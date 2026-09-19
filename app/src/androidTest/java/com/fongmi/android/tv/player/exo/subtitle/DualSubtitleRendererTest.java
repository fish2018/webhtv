package com.fongmi.android.tv.player.exo.subtitle;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Real shipped renderers/decoders with independent streams, one renderer clock and queued UI output. */
@SuppressWarnings("deprecation")
public class DualSubtitleRendererTest extends TestCase {
    private static final long OFFSET = 1_000_000_000L;
    private static final Format PRIMARY = new Format.Builder().setId("primary")
            .setSampleMimeType(MimeTypes.TEXT_SSA).build();
    private static final Format SECONDARY = new Format.Builder().setId("secondary")
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build();
    private static final TrackGroup SECONDARY_GROUP = new TrackGroup("secondary", SECONDARY);
    private static final String ASS = "[Script Info]\nPlayResX: 640\nPlayResY: 360\n"
            + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,PRIMARY ONE\n"
            + "Dialogue: 0,0:00:05.00,0:00:10.00,Default,,0,0,0,,PRIMARY TWO\n";
    private static final String SRT = "1\n00:00:01,000 --> 00:00:03,000\nSECONDARY ONE\n\n"
            + "2\n00:00:05,000 --> 00:00:10,000\nSECONDARY TWO\n";

    private static final class Input implements SampleStream {
        final Format format;
        final byte[] data;
        int phase, reads;

        Input(Format format, String text) {
            this.format = format;
            data = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override public boolean isReady() { return true; }
        @Override public void maybeThrowError() { }
        @Override public int skipData(long positionUs) { return 0; }
        @Override
        public int readData(FormatHolder holder, DecoderInputBuffer buffer, int flags) {
            if (phase == 0 || (flags & FLAG_REQUIRE_FORMAT) != 0) {
                holder.format = format;
                phase = 1;
                return C.RESULT_FORMAT_READ;
            }
            buffer.clear();
            if (phase > 1) {
                buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
                return C.RESULT_BUFFER_READ;
            }
            buffer.timeUs = 0;
            buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
            if ((flags & FLAG_OMIT_SAMPLE_DATA) == 0) {
                buffer.ensureSpaceForWrite(data.length);
                buffer.data.put(data);
            }
            if ((flags & FLAG_PEEK) == 0) { phase = 2; reads++; }
            return C.RESULT_BUFFER_READ;
        }
    }

    private static final class Output implements TextOutput, TextRenderer.Observer {
        CueGroup cues = CueGroup.EMPTY_TIME_ZERO;
        int samples;
        @Override public void onCues(CueGroup cues) { this.cues = cues; }
        @Override public void onStream(TextRenderer.Stream stream, long generation) { }
        @Override public void onReset(TextRenderer.Stream stream, long generation, long position) { }
        @Override public void onSample(TextRenderer.Stream stream, long generation, Format format, ByteBuffer data, long timeUs) {
            assertEquals(PRIMARY, format);
            samples++;
        }
        @Override public void onClock(TextRenderer.Stream reading, TextRenderer.Stream displaying, long generation,
                                      long position, long offset, boolean started, boolean ended) { }
        @Override public void onCues(TextRenderer.Stream stream, long generation, CueGroup cues) { }
        @Override public void onDisabled(long generation) { }
    }

    private final class Rig implements AutoCloseable {
        final ExoSubtitleSession session = new ExoSubtitleSession();
        final DualSubtitleTrackSelector selector = (DualSubtitleTrackSelector) session.wrapTrackSelector(
                new DefaultTrackSelector(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        final Output output = new Output();
        final TextRenderer primary = new TextRenderer(output, null);
        final TextRenderer secondary;
        final Input primaryInput = new Input(PRIMARY, ASS);
        final Input secondaryInput = new Input(SECONDARY, SRT);

        Rig() {
            primary.setObserver(output);
            Renderer[] renderers = session.wrapRenderersFactory((handler, video, audio, text, metadata) ->
                    new Renderer[]{primary}).createRenderers(new Handler(Looper.getMainLooper()), null, null, output, null);
            assertSame(primary, renderers[0]);
            secondary = (TextRenderer) renderers[1];
            primary.init(0, PlayerId.UNSET, Clock.DEFAULT);
            secondary.init(1, PlayerId.UNSET, Clock.DEFAULT);
        }

        void start() throws Exception {
            selector.setSecondaryOverride(new TrackSelectionOverride(SECONDARY_GROUP, 0));
            enable(primary, primaryInput, "a");
            enable(secondary, secondaryInput, "a");
        }

        void seek(long positionUs) throws Exception {
            primaryInput.phase = secondaryInput.phase = 1;
            primary.resetPosition(OFFSET + positionUs, false);
            secondary.resetPosition(OFFSET + positionUs, false);
        }

        @Override public void close() {
            release(primary);
            release(secondary);
            main(session::release);
            selector.release();
        }
    }

    private static void enable(TextRenderer renderer, Input input, String period) throws Exception {
        renderer.enable(RendererConfiguration.DEFAULT, new Format[]{input.format}, input,
                OFFSET, false, true, OFFSET, OFFSET, new MediaPeriodId(period));
        renderer.start();
    }

    private static void release(TextRenderer renderer) {
        if (renderer.getState() == Renderer.STATE_STARTED) renderer.stop();
        if (renderer.getState() == Renderer.STATE_ENABLED) renderer.disable();
        renderer.reset();
        renderer.release();
    }

    private static void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    @SuppressWarnings("unchecked")
    private static List<Cue> cues(ExoSubtitleSession session) {
        AtomicReference<List<Cue>> result = new AtomicReference<>();
        main(() -> {
            try {
                java.lang.reflect.Field field = ExoSubtitleSession.class.getDeclaredField("cues");
                field.setAccessible(true);
                result.set((List<Cue>) field.get(session));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        return result.get();
    }

    private static String text(List<Cue> cues) {
        return cues.isEmpty() ? "" : cues.get(0).text.toString();
    }

    private static void await(Rig rig, long positionUs, String expectedPrimary, String expectedSecondary) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        do {
            rig.primary.render(OFFSET + positionUs, 0);
            rig.secondary.render(OFFSET + positionUs, 0);
            if (expectedPrimary.equals(text(rig.output.cues.cues))
                    && expectedSecondary.equals(text(cues(rig.session)))) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("Expected " + expectedPrimary + " / " + expectedSecondary + ", got "
                + text(rig.output.cues.cues) + " / " + text(cues(rig.session)));
    }

    public void testRealRenderersKeepOutputsIndependentAcrossPauseOffsetAndSeek() throws Exception {
        try (Rig rig = new Rig()) {
            assertEquals(Renderer.STATE_DISABLED, rig.secondary.getState());
            assertEquals(0, rig.secondaryInput.reads);
            rig.start();
            await(rig, 1_500_000, "PRIMARY ONE", "SECONDARY ONE");
            assertEquals(1, rig.output.samples);
            assertEquals(1, rig.secondaryInput.reads);
            assertEquals(Cue.ANCHOR_TYPE_START, cues(rig.session).get(0).lineAnchor);
            rig.primary.stop();
            rig.secondary.stop();
            rig.primary.setTextOffsetMs(2000);
            rig.secondary.setTextOffsetMs(2000);
            assertTrue(rig.output.cues.cues.isEmpty());
            assertTrue(cues(rig.session).isEmpty());
            rig.primary.setTextOffsetMs(0);
            rig.secondary.setTextOffsetMs(0);
            assertEquals("PRIMARY ONE", text(rig.output.cues.cues));
            assertEquals("SECONDARY ONE", text(cues(rig.session)));
            rig.primary.start();
            rig.secondary.start();
            rig.seek(5_500_000);
            await(rig, 5_500_000, "PRIMARY TWO", "SECONDARY TWO");
            rig.seek(1_500_000);
            await(rig, 1_500_000, "PRIMARY ONE", "SECONDARY ONE");
            rig.secondary.stop();
            rig.secondary.disable();
            assertTrue(cues(rig.session).isEmpty());
            assertEquals("PRIMARY ONE", text(rig.output.cues.cues));
        }
    }

    public void testSourceResetRejectsOldStreamEvenWhenTheNewFormatIsIdentical() throws Exception {
        try (Rig rig = new Rig()) {
            rig.start();
            await(rig, 1_500_000, "PRIMARY ONE", "SECONDARY ONE");
            main(() -> {
                rig.session.reset();
                rig.selector.setSecondaryOverride(new TrackSelectionOverride(SECONDARY_GROUP, 0));
            });
            rig.secondary.setTextOffsetMs(100);
            assertTrue("An old source must not repopulate the new session", cues(rig.session).isEmpty());
            rig.secondary.stop();
            rig.secondary.disable();
            enable(rig.secondary, new Input(SECONDARY, SRT), "b");
            await(rig, 1_500_000, "PRIMARY ONE", "SECONDARY ONE");
            main(rig.session::release);
            rig.secondary.setTextOffsetMs(200);
            assertTrue("Released sessions must ignore renderer output", cues(rig.session).isEmpty());
        }
    }
}
