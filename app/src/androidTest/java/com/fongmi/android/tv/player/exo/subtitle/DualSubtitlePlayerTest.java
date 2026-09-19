package com.fongmi.android.tv.player.exo.subtitle;

import android.content.Context;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.SingleSampleMediaSource;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Track;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real ExoPlayer/MediaPeriod integration, without relying on a device audio/video codec. */
@SuppressWarnings("deprecation")
public class DualSubtitlePlayerTest extends TestCase {
    private ExoPlayer player;
    private ExoSubtitleSession session;
    private PlayerView view;
    private CueGroup primaryCues = CueGroup.EMPTY_TIME_ZERO;

    private static void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    protected void pumpMainLoop() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            pumpMainLoop();
            main(() -> {
                assertNull("Player error", player.getPlayerError());
                try { ready.set(condition.call()); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            if (ready.get()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        main(() -> fail(message + "; state=" + player.getPlaybackState() + " position=" + player.getCurrentPosition()
                + " tracks=" + player.getCurrentTracks().getGroups().size() + " primary=" + text(primaryCues.cues)
                + " secondary=" + text(secondaryCues())));
    }

    private MediaSource source(String id, String language, String text) {
        byte[] bytes = ("1\n00:00:00,000 --> 00:00:04,000\n" + text + " ONE\n\n"
                + "2\n00:00:04,000 --> 00:00:10,000\n" + text + " TWO\n").getBytes(StandardCharsets.UTF_8);
        return new SingleSampleMediaSource.Factory(() -> new ByteArrayDataSource(bytes))
                .setTreatLoadErrorsAsEndOfStream(false).createMediaSource(
                        new MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse("memory:///" + id + ".srt"))
                                .setId(id).setLabel(id).setLanguage(language).setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                .build(), 10_000_000);
    }

    @Override protected void tearDown() throws Exception {
        main(() -> {
            if (view != null) view.setPlayer(null);
            if (session != null) session.release();
            if (player != null) player.release();
        });
        super.tearDown();
    }

    public void testTwoRealMediaPeriodsSelectionsAndSiblingOverlayLifecycle() throws Exception {
        main(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            session = new ExoSubtitleSession();
            DefaultTrackSelector original = new DefaultTrackSelector(context);
            original.setParameters(original.buildUponParameters().setPreferredTextLanguage("en"));
            player = new ExoPlayer.Builder(context)
                    .setTrackSelector(session.wrapTrackSelector(original))
                    .setRenderersFactory(session.wrapRenderersFactory((handler, video, audio, output, metadata) ->
                            new Renderer[]{new TextRenderer(group -> {
                                primaryCues = group;
                                output.onCues(group);
                            }, handler.getLooper())})).build();
            view = new PlayerView(context);
            view.setPlayer(player);
            session.attach(view);
            assertNull(secondaryView());
            player.setMediaSource(new MergingMediaSource(source("main", "en", "MAIN"),
                    source("second", "zh", "SECOND")), 1500);
            player.prepare();
            player.play();
        });
        await(() -> text(primaryCues.cues).equals("MAIN ONE") && find("second") != null, "primary playback started");
        main(() -> {
            assertEquals(Renderer.STATE_DISABLED, player.getRenderer(1).getState());
            assertTrue(session.isPrimarySelected(find("main")));
            Track track = new Track(C.TRACK_TYPE_TEXT, "second", null).playerId(find("second").id);
            assertTrue(session.selectSecondary(player, track));
        });
        await(() -> text(secondaryCues()).equals("SECOND ONE"), "secondary selected through real merged media periods");
        main(() -> {
            player.pause();
            assertTrue(session.isPrimarySelected(find("main")));
            assertTrue(session.isSecondarySelected(find("second")));
            assertEquals("MAIN ONE", text(primaryCues.cues));
            // This is the exact visibility operation performed when the existing ASS surface takes over.
            view.getSubtitleView().setVisibility(View.INVISIBLE);
            assertEquals(View.VISIBLE, secondaryView().getVisibility());
            assertSame(view.getSubtitleView().getParent(), secondaryView().getParent());
            assertFalse(secondaryView().isFocusable());
            SubtitleView old = secondaryView();
            session.detach();
            assertNull(old.getParent());
            session.attach(view);
            assertNotSame(old, secondaryView());
            assertEquals("SECOND ONE", text(secondaryCues()));
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
        });
        await(() -> player.getRenderer(0).getState() == Renderer.STATE_DISABLED, "primary independently disabled");
        main(() -> {
            assertEquals("SECOND ONE", text(secondaryCues()));
            assertTrue(session.isSecondarySelected(find("second")));
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
            player.seekTo(5500);
            player.play();
        });
        await(() -> text(primaryCues.cues).equals("MAIN TWO") && text(secondaryCues()).equals("SECOND TWO"),
                "both real renderers follow the same forward seek");
        main(() -> { player.seekTo(1500); player.play(); });
        await(() -> text(primaryCues.cues).equals("MAIN ONE") && text(secondaryCues()).equals("SECOND ONE"),
                "both real renderers follow the same backward seek");
        main(() -> assertTrue(session.selectSecondary(player, Track.disabled(C.TRACK_TYPE_TEXT, "off"))));
        await(() -> player.getRenderer(1).getState() == Renderer.STATE_DISABLED && secondaryCues().isEmpty(),
                "secondary independently disabled");
        main(() -> {
            assertEquals("MAIN ONE", text(primaryCues.cues));
            session.reset();
            player.stop();
            assertTrue(secondaryCues().isEmpty());
        });
    }

    private Format find(String id) {
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            // MergingMediaPeriod prefixes runtime ids with the child source index.
            for (int i = 0; i < group.length; i++) if (id.equals(group.getTrackFormat(i).label)) return group.getTrackFormat(i);
        }
        return null;
    }

    private Object field(String name) {
        try {
            java.lang.reflect.Field field = ExoSubtitleSession.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(session);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SuppressWarnings("unchecked") private List<Cue> secondaryCues() { return (List<Cue>) field("cues"); }
    private SubtitleView secondaryView() { return (SubtitleView) field("view"); }
    private static String text(List<Cue> cues) { return cues.isEmpty() ? "" : cues.get(0).text.toString(); }
}
