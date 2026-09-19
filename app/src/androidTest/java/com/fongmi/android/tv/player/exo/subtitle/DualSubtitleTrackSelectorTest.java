package com.fongmi.android.tv.player.exo.subtitle;

import android.content.Context;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.util.Arrays;

/** Runs the shipped Media3 selector, including its parameter overrides and tunneling decisions. */
public class DualSubtitleTrackSelectorTest extends TestCase {
    private DefaultTrackSelector delegate;
    private DualSubtitleTrackSelector selector;
    private TrackGroup video, audio, primary, secondary;
    private TrackGroupArray groups;
    private RendererCapabilities[] capabilities;
    private int invalidations;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        video = new TrackGroup("video", new Format.Builder().setId("v").setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1280).setHeight(720).setFrameRate(24).build());
        audio = new TrackGroup("audio", new Format.Builder().setId("a").setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setChannelCount(2).setSampleRate(48000).build());
        primary = new TrackGroup("english", text("en", "en"));
        secondary = new TrackGroup("chinese", text("zh", "zh"));
        groups = new TrackGroupArray(video, audio, primary, secondary);
        capabilities = new RendererCapabilities[]{new Capabilities(C.TRACK_TYPE_VIDEO),
                new Capabilities(C.TRACK_TYPE_AUDIO), new Capabilities(C.TRACK_TYPE_TEXT),
                new Capabilities(C.TRACK_TYPE_TEXT)};
        delegate = new DefaultTrackSelector(context);
        delegate.setParameters(delegate.buildUponParameters()
                .setConstrainAudioChannelCountToDeviceCapabilities(false).setTunnelingEnabled(true)
                .setOverrideForType(new TrackSelectionOverride(primary, 0)).build());
        selector = new DualSubtitleTrackSelector(delegate);
        selector.setSecondaryRendererIndex(3);
        selector.init(() -> invalidations++, DefaultBandwidthMeter.getSingletonInstance(context));
    }

    @Override
    protected void tearDown() throws Exception {
        if (selector != null) selector.release();
        super.tearDown();
    }

    private static Format text(String id, String language) {
        return new Format.Builder().setId(id).setLanguage(language).setSampleMimeType(MimeTypes.TEXT_VTT).build();
    }

    private TrackSelectorResult select() throws Exception {
        return selector.selectTracks(capabilities, groups, new MediaPeriodId("period"), Timeline.EMPTY);
    }

    private TrackSelectorResult activate() throws Exception {
        TrackSelectorResult result = select();
        selector.onSelectionActivated(result.info);
        return result;
    }

    public void testDefaultOffPreservesOriginalSelectionsAndTunneling() throws Exception {
        TrackSelectorResult original = delegate.selectTracks(Arrays.copyOf(capabilities, 3), groups,
                new MediaPeriodId("period"), Timeline.EMPTY);
        TrackSelectorResult result = activate();
        for (int i = 0; i < 3; i++) assertTrue("Original renderer " + i, result.isEquivalent(original, i));
        assertNull(result.selections[3]);
        assertNull(result.rendererConfigurations[3]);
        assertEquals(4, result.tracks.getGroups().size());
        assertTrue(selector.isPrimarySelected(primary.getFormat(0)));
        assertFalse(selector.isSecondarySelected(secondary.getFormat(0)));
        assertSame(delegate.getParameters(), selector.getParameters());
        assertSame(delegate.getRendererCapabilitiesListener(), selector.getRendererCapabilitiesListener());
    }

    public void testSecondaryHasIndependentSelectionAndOnlyPublishesWhenActivated() throws Exception {
        TrackSelectorResult before = activate();
        assertTrue(selector.setSecondaryOverride(new TrackSelectionOverride(secondary, 0)));
        assertEquals(1, invalidations);
        TrackSelectorResult result = select();
        assertFalse("Read-ahead is not active playback", selector.isSecondarySelected(secondary.getFormat(0)));
        for (int i = 0; i < 3; i++) assertTrue(result.isEquivalent(before, i));
        assertEquals(secondary, result.selections[3].getTrackGroup());
        assertEquals(1, result.selections[3].length());
        assertEquals(2, result.tracks.getGroups().stream()
                .filter(group -> group.getType() == C.TRACK_TYPE_TEXT && group.isSelected()).count());
        selector.onSelectionActivated(result.info);
        assertTrue(selector.isPrimarySelected(primary.getFormat(0)));
        assertFalse(selector.isPrimarySelected(secondary.getFormat(0)));
        assertTrue(selector.isSecondarySelected(secondary.getFormat(0)));
    }

    public void testPrimaryAndSecondaryDisableIndependently() throws Exception {
        TrackSelectorResult before = activate();
        selector.setSecondaryOverride(new TrackSelectionOverride(secondary, 0));
        activate();
        selector.setParameters(delegate.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
        TrackSelectorResult primaryOff = activate();
        assertNull(primaryOff.selections[2]);
        assertEquals(secondary, primaryOff.selections[3].getTrackGroup());
        assertFalse(selector.isPrimarySelected(primary.getFormat(0)));
        assertTrue(selector.isSecondarySelected(secondary.getFormat(0)));
        assertTrue(primaryOff.isEquivalent(before, 0));
        assertTrue(primaryOff.isEquivalent(before, 1));
        selector.setParameters(delegate.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
        selector.setSecondaryOverride(null);
        TrackSelectorResult secondaryOff = activate();
        assertEquals(primary, secondaryOff.selections[2].getTrackGroup());
        assertNull(secondaryOff.selections[3]);
        assertTrue(selector.isPrimarySelected(primary.getFormat(0)));
    }

    public void testDuplicateRoleRequestDoesNotDisturbEitherSelection() throws Exception {
        activate();
        selector.setSecondaryOverride(new TrackSelectionOverride(secondary, 0));
        TrackSelectorResult before = activate();
        assertFalse(selector.setSecondaryOverride(new TrackSelectionOverride(primary, 0)));
        assertTrue(activate().isEquivalent(before));
    }

    public void testTwoVariantsOfOneGroupCannotBindTheSameSampleQueueTwice() throws Exception {
        TrackGroup variants = new TrackGroup("variants", text("en-1", "en"), text("en-2", "en"));
        groups = new TrackGroupArray(video, audio, variants, secondary);
        delegate.setParameters(delegate.buildUponParameters().setOverrideForType(new TrackSelectionOverride(variants, 0)).build());
        activate();
        assertFalse(selector.setSecondaryOverride(new TrackSelectionOverride(variants, 1)));
        assertNull(activate().selections[3]);
    }

    public void testResetRejectsReadAheadActivationAndOldOutputTokens() throws Exception {
        activate();
        selector.setSecondaryOverride(new TrackSelectionOverride(secondary, 0));
        TrackSelectorResult old = select();
        DualSubtitleTrackSelector.Request token = selector.request();
        assertTrue(token.accepts(secondary.getFormat(0)));
        selector.reset();
        selector.onSelectionActivated(old.info);
        assertNotSame(token, selector.request());
        assertFalse(selector.isPrimarySelected(primary.getFormat(0)));
        assertFalse(selector.isSecondarySelected(secondary.getFormat(0)));
        assertNull(select().selections[3]);
    }

    public void testUnavailableOrDrmSubtitleDoesNotAffectAudioVideo() throws Exception {
        TrackSelectorResult before = activate();
        TrackGroup missing = new TrackGroup("missing", text("missing", "fr"));
        selector.setSecondaryOverride(new TrackSelectionOverride(missing, 0));
        assertNull(select().selections[3]);
        TrackGroup encrypted = new TrackGroup("encrypted", text("drm", "fr").buildUpon()
                .setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build());
        groups = new TrackGroupArray(video, audio, primary, encrypted);
        selector.setSecondaryOverride(new TrackSelectionOverride(encrypted, 0));
        TrackSelectorResult result = activate();
        assertNull(result.selections[3]);
        assertTrue(result.isEquivalent(before, 0));
        assertTrue(result.isEquivalent(before, 1));
    }

    private static final class Capabilities implements RendererCapabilities {
        private final int type;
        Capabilities(int type) { this.type = type; }
        @Override public String getName() { return "fixture-" + type; }
        @Override public int getTrackType() { return type; }
        @Override public int supportsMixedMimeTypeAdaptation() { return ADAPTIVE_NOT_SUPPORTED; }
        @Override public int supportsFormat(Format format) {
            if (MimeTypes.getTrackType(format.sampleMimeType) != type) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
            return RendererCapabilities.create(format.cryptoType == C.CRYPTO_TYPE_NONE ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_DRM,
                    ADAPTIVE_NOT_SUPPORTED, type == C.TRACK_TYPE_TEXT ? TUNNELING_NOT_SUPPORTED : TUNNELING_SUPPORTED);
        }
    }
}
