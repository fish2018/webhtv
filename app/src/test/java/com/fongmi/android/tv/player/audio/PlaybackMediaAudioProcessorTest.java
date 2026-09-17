package com.fongmi.android.tv.player.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

public class PlaybackMediaAudioProcessorTest {

    @Test
    public void publishesOneMonoBufferToTwoConsumersAndPreservesOutput() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        ManualExecutor firstExecutor = new ManualExecutor();
        ManualExecutor secondExecutor = new ManualExecutor();
        RecordingConsumer first = new RecordingConsumer();
        RecordingConsumer second = new RecordingConsumer();
        hub.register("first", firstExecutor, 4, first);
        hub.register("second", secondExecutor, 4, second);
        PlaybackMediaSignalHub.CaptureLease capture = hub.requestCapture(
                PlaybackMediaSignalHub.ConsumerKind.TEST);
        PlaybackMediaSignalHub.Session session = hub.beginSession(5_000L);
        clock.reset(session.generation(), session.mediaAnchorMs());
        PlaybackMediaAudioProcessor processor = new PlaybackMediaAudioProcessor(hub, clock);
        processor.configure(new AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT));
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        ByteBuffer input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
        input.putShort(Short.MAX_VALUE).putShort(Short.MIN_VALUE);
        input.putShort((short) 16_384).putShort((short) 16_384).flip();

        processor.queueInput(input);
        ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        firstExecutor.runAll();
        secondExecutor.runAll();

        assertSame(first.frame.monoSamples(), second.frame.monoSamples());
        assertArrayEquals(new float[]{-1f / 65_536f, 0.5f}, first.frame.monoSamples(), 0.0001f);
        assertEquals(48_000, first.frame.sampleRate());
        assertEquals(0L, first.frame.captureStartTimeMs());
        assertEquals(Short.MAX_VALUE, output.getShort());
        assertEquals(Short.MIN_VALUE, output.getShort());
        assertEquals(16_384, output.getShort());
        assertEquals(16_384, output.getShort());
        assertEquals(1L, clock.snapshot(0L).capturedUntilMs());
        capture.close();
    }

    @Test
    public void inactiveCapturePassesThroughWithoutPublishing() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(2);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        ManualExecutor executor = new ManualExecutor();
        RecordingConsumer consumer = new RecordingConsumer();
        hub.register("inactive", executor, 2, consumer);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        clock.reset(session.generation(), 0L);
        PlaybackMediaAudioProcessor processor = new PlaybackMediaAudioProcessor(hub, clock);
        processor.configure(new AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT));
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        ByteBuffer input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        input.putInt(0x12345678).flip();

        processor.queueInput(input);
        ByteBuffer output = processor.getOutput().order(ByteOrder.nativeOrder());
        executor.runAll();

        assertEquals(null, consumer.frame);
        assertEquals(0x12345678, output.getInt());
    }

    @Test
    public void timelineResetDropsOldStreamUntilAudioFlush() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(2);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        ManualExecutor executor = new ManualExecutor();
        RecordingConsumer consumer = new RecordingConsumer();
        hub.register("reset", executor, 2, consumer);
        hub.requestCapture(PlaybackMediaSignalHub.ConsumerKind.TEST);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        clock.reset(session.generation(), 0L);
        PlaybackMediaAudioProcessor processor = new PlaybackMediaAudioProcessor(hub, clock);
        processor.configure(new AudioProcessor.AudioFormat(16_000, 1, C.ENCODING_PCM_16BIT));
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        PlaybackMediaSignalHub.Session reset = hub.resetTimeline(
                50_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        clock.reset(reset.generation(), reset.mediaAnchorMs());
        ByteBuffer input = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder());
        input.putShort((short) 1_000).flip();

        processor.queueInput(input);
        executor.runAll();

        assertNull(consumer.frame);
        assertEquals(0L, clock.snapshot(0L).capturedUntilMs());
    }

    private static final class RecordingConsumer implements PlaybackMediaSignalHub.Consumer {
        private PlaybackMediaSignalHub.PcmFrame frame;

        @Override
        public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
            this.frame = frame;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) tasks.removeFirst().run();
        }
    }
}
