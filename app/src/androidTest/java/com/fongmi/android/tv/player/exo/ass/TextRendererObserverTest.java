package com.fongmi.android.tv.player.exo.ass;

import android.os.SystemClock;
import junit.framework.TestCase;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.RendererConfiguration;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Exercises the real shipped TextRenderer, decoder, Cue output and final-stream handling. */
@SuppressWarnings("deprecation")
public class TextRendererObserverTest extends TestCase {
    private static final long OFFSET = 1_000_000_000L;
    private static final byte[] SCRIPT = ("[Script Info]\nPlayResX: 640\nPlayResY: 360\n"
            + "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
            + "Style: Default,Arial,30,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1\n"
            + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            + "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,kept,comma\n").getBytes(StandardCharsets.UTF_8);
    private static final Format FORMAT = new Format.Builder().setSampleMimeType(MimeTypes.TEXT_SSA)
            .setId(AssInput.EXTERNAL_ID_PREFIX + "test").build();

    private static final class Output implements TextOutput {
        CueGroup cues = CueGroup.EMPTY_TIME_ZERO;
        @Override public void onCues(CueGroup cues) { this.cues = cues; }
    }

    private static class Observer implements TextRenderer.Observer {
        long generation, resets, sampleTime, position, textOffset;
        int samples, disabled;
        boolean ended, throwOnSample;
        byte[] bytes;
        TextRenderer.Stream reading, displaying;
        @Override public void onStream(TextRenderer.Stream stream, long generation) { reading = stream; this.generation = generation; }
        @Override public void onReset(TextRenderer.Stream stream, long generation, long position) { resets++; this.generation = generation; }
        @Override public void onSample(TextRenderer.Stream stream, long generation, Format format, ByteBuffer data, long timeUs) {
            samples++; bytes = AssInput.copy(data); sampleTime = timeUs;
            if (throwOnSample) throw new IllegalStateException("fixture");
        }
        @Override public void onClock(TextRenderer.Stream reading, TextRenderer.Stream displaying, long generation,
                                      long position, long offset, boolean started, boolean ended) {
            this.reading = reading; this.displaying = displaying; this.position = position;
            textOffset = offset; this.ended = ended;
        }
        @Override public void onCues(TextRenderer.Stream stream, long generation, CueGroup group) { }
        @Override public void onDisabled(long generation) { disabled++; }
    }

    private static final class Input implements SampleStream {
        int phase, actualReads, peeks;
        @Override public boolean isReady() { return true; }
        @Override public void maybeThrowError() { }
        @Override public int skipData(long positionUs) { return 0; }
        @Override public int readData(FormatHolder holder, DecoderInputBuffer buffer, int flags) {
            if ((flags & FLAG_PEEK) != 0) peeks++;
            if (phase == 0 || (flags & FLAG_REQUIRE_FORMAT) != 0) {
                holder.format = FORMAT; phase = 1; return C.RESULT_FORMAT_READ;
            }
            buffer.clear();
            if (phase >= 2) {
                buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM); return C.RESULT_BUFFER_READ;
            }
            buffer.timeUs = 0;
            buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
            if ((flags & FLAG_OMIT_SAMPLE_DATA) == 0) {
                buffer.ensureSpaceForWrite(SCRIPT.length); buffer.data.put(SCRIPT);
            }
            if ((flags & FLAG_PEEK) == 0) { phase = 2; actualReads++; }
            return C.RESULT_BUFFER_READ;
        }
    }

    private TextRenderer renderer(Output output, Input input, Observer observer) throws Exception {
        TextRenderer renderer = new TextRenderer(output, null);
        if (observer != null) renderer.setObserver(observer);
        renderer.init(0, PlayerId.UNSET, Clock.DEFAULT);
        renderer.enable(RendererConfiguration.DEFAULT, new Format[]{FORMAT}, input,
                OFFSET, false, true, OFFSET, OFFSET, new MediaSource.MediaPeriodId("period-a"));
        renderer.start();
        return renderer;
    }

    private void awaitCue(TextRenderer renderer, Output output, long position) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 2000;
        do {
            renderer.render(position, SystemClock.elapsedRealtime() * 1000);
            if (!output.cues.cues.isEmpty()) return;
            SystemClock.sleep(5);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Legacy Cue output did not arrive");
    }

    private void release(TextRenderer renderer) {
        renderer.stop(); renderer.disable(); renderer.reset(); renderer.release();
    }

    public void testDefaultObserverOffPreservesCueAndSingleConsumption() throws Exception {
        Output output = new Output(); Input input = new Input();
        TextRenderer renderer = renderer(output, input, null);
        try {
            awaitCue(renderer, output, OFFSET + 1_500_000);
            assertEquals("kept,comma", output.cues.cues.get(0).text.toString());
            assertEquals(1, input.actualReads);
            assertTrue(input.peeks > 0);
            assertEquals(TextRenderer.class, renderer.getClass());
        } finally { release(renderer); }
    }

    public void testPausedObserverDelayRedeliversCompatibleCue() throws Exception {
        Output output = new Output(); Input input = new Input();
        TextRenderer renderer = renderer(output, input, new Observer());
        try {
            awaitCue(renderer, output, OFFSET + 2_000_000);
            renderer.stop();
            renderer.setTextOffsetMs(500);
            assertEquals("kept,comma", output.cues.cues.get(0).text.toString());
            renderer.setTextOffsetMs(2500);
            assertTrue("Moving before the cue's start must clear it", output.cues.cues.isEmpty());
            renderer.setTextOffsetMs(0);
            assertEquals("Moving time backward must also reset the event index", "kept,comma",
                    output.cues.cues.get(0).text.toString());
        } finally {
            if (renderer.getState() != androidx.media3.exoplayer.Renderer.STATE_STARTED) renderer.start();
            release(renderer);
        }
    }

    public void testObserverUsesSelectedBytesOffsetDelaySeekAndFinalEnd() throws Exception {
        Output output = new Output(); Input input = new Input(); Observer observer = new Observer();
        TextRenderer renderer = renderer(output, input, observer);
        try {
            awaitCue(renderer, output, OFFSET + 1_500_000);
            assertEquals(1, observer.samples);
            assertTrue(Arrays.equals(SCRIPT, observer.bytes));
            assertEquals(OFFSET, observer.sampleTime);
            assertEquals(OFFSET, observer.reading.offsetUs);
            assertEquals("period-a", observer.reading.mediaPeriodId.periodUid);
            renderer.setTextOffsetMs(500);
            renderer.render(OFFSET + 2_000_000, 0);
            assertEquals(1500, AssInput.timeMs(observer.position, observer.reading.offsetUs, observer.textOffset));
            assertEquals("kept,comma", output.cues.cues.get(0).text.toString());
            long generation = observer.generation;
            input.phase = 1;
            renderer.resetPosition(OFFSET + 1_500_000, false);
            awaitCue(renderer, output, OFFSET + 1_500_000);
            assertTrue(observer.generation > generation);
            assertEquals(2, observer.samples);
            renderer.setCurrentStreamFinal();
            renderer.setFinalStreamEndPositionUs(OFFSET + 4_000_000);
            renderer.render(OFFSET + 4_000_000, 0);
            assertTrue(renderer.isEnded());
            assertTrue(observer.ended);
        } finally { release(renderer); }
        assertEquals(1, observer.disabled);
    }

    public void testThrowingObserverCannotBreakCompatibleDecoder() throws Exception {
        Output output = new Output(); Input input = new Input(); Observer observer = new Observer();
        observer.throwOnSample = true;
        TextRenderer renderer = renderer(output, input, observer);
        try {
            awaitCue(renderer, output, OFFSET + 1_500_000);
            assertEquals("kept,comma", output.cues.cues.get(0).text.toString());
            assertEquals(1, observer.disabled);
        } finally { release(renderer); }
    }

    public void testReadAheadPeriodDoesNotRelabelDisplayedSubtitle() throws Exception {
        Output output = new Output(); Input input = new Input(); Observer observer = new Observer();
        TextRenderer renderer = renderer(output, input, observer);
        try {
            awaitCue(renderer, output, OFFSET + 1_500_000);
            long displaying = observer.displaying.sequence;
            renderer.replaceStream(new Format[]{FORMAT}, new Input(), OFFSET + 5_000_000,
                    OFFSET + 5_000_000, new MediaSource.MediaPeriodId("period-b"));
            renderer.render(OFFSET + 1_600_000, 0);
            assertEquals("period-b", observer.reading.mediaPeriodId.periodUid);
            assertEquals(displaying, observer.displaying.sequence);
            assertEquals("kept,comma", output.cues.cues.get(0).text.toString());
        } finally { release(renderer); }
    }
}
