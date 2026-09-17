package com.fongmi.android.tv.player.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PlaybackMediaAudioPipelineTest {

    @Test
    public void clockSinkIgnoresOlderOutputsAndReleaseInvalidatesClock() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        PlaybackMediaSignalHub.Session session = hub.beginSession(30_000L);
        clock.reset(session.generation(), session.mediaAnchorMs());
        PlaybackMediaAudioPipeline pipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        activate(pipeline);
        clock.onCaptured(hub.session().generation(), 2_000L);

        pipeline.clockSink().onSample(2L, 2_000_000L, 1_500_000L, 10_000L, false);
        pipeline.clockSink().onSample(1L, 2_000_000L, 500_000L, 10_000L, false);
        assertEquals(31_500L, clock.snapshot(10_000L).presentedMediaPositionMs().orElseThrow());

        pipeline.clockSink().onReleased(1L);
        assertEquals(31_500L, clock.snapshot(10_000L).presentedMediaPositionMs().orElseThrow());
        pipeline.clockSink().onReleased(2L);
        assertTrue(clock.snapshot(10_000L).presentedMediaPositionMs().isEmpty());
    }

    @Test
    public void supersededClockSinkCannotReleaseTheCurrentClock() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        PlaybackMediaSignalHub.Session session = hub.beginSession(30_000L);
        clock.reset(session.generation(), session.mediaAnchorMs());
        PlaybackMediaAudioPipeline oldPipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        activate(oldPipeline);
        clock.onCaptured(hub.session().generation(), 3_000L);
        oldPipeline.clockSink().onSample(1L, 3_000_000L, 1_000_000L, 10_000L, false);

        PlaybackMediaAudioPipeline currentPipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        activate(currentPipeline);
        clock.onCaptured(hub.session().generation(), 3_000L);
        currentPipeline.clockSink().onSample(1L, 3_000_000L, 1_500_000L, 10_000L, false);
        oldPipeline.clockSink().onReleased(1L);

        assertEquals(31_500L,
                clock.snapshot(10_000L).presentedMediaPositionMs().orElseThrow());
    }

    @Test
    public void supersededProcessorCannotResetCurrentTimeline() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        hub.beginSession(0L);
        PlaybackMediaAudioPipeline oldPipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        oldPipeline.audioProcessor().configure(
                new AudioProcessor.AudioFormat(16_000, 1, C.ENCODING_PCM_16BIT));
        oldPipeline.audioProcessor().flush(AudioProcessor.StreamMetadata.DEFAULT);
        PlaybackMediaAudioPipeline.create(hub, clock);
        long currentGeneration = hub.session().generation();

        oldPipeline.audioProcessor().flush(AudioProcessor.StreamMetadata.DEFAULT);

        assertEquals(currentGeneration, hub.session().generation());
    }

    @Test
    public void supersededProcessorCannotPublishPcm() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        List<PlaybackMediaSignalHub.PcmFrame> received = new ArrayList<>();
        hub.register("test", Runnable::run, 4, new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                received.add(frame);
            }
        });
        hub.requestCapture(PlaybackMediaSignalHub.ConsumerKind.TEST);
        hub.beginSession(0L);
        PlaybackMediaAudioPipeline oldPipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        oldPipeline.audioProcessor().configure(
                new AudioProcessor.AudioFormat(16_000, 1, C.ENCODING_PCM_16BIT));
        oldPipeline.audioProcessor().flush(AudioProcessor.StreamMetadata.DEFAULT);
        PlaybackMediaAudioPipeline.create(hub, clock);
        ByteBuffer input = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder());
        input.putShort((short) 1_000).flip();

        oldPipeline.audioProcessor().queueInput(input);

        assertTrue(received.isEmpty());
    }

    @Test
    public void timelineResetRejectsOldClockSamplesUntilAudioFlush() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        PlaybackMediaSignalHub.Session session = hub.beginSession(0L);
        clock.reset(session.generation(), session.mediaAnchorMs());
        PlaybackMediaAudioPipeline pipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        pipeline.audioProcessor().configure(
                new AudioProcessor.AudioFormat(16_000, 1, C.ENCODING_PCM_16BIT));
        pipeline.audioProcessor().flush(AudioProcessor.StreamMetadata.DEFAULT);

        PlaybackMediaSignalHub.Session reset = hub.resetTimeline(
                50_000L, PlaybackMediaSignalHub.ResetReason.SEEK);
        clock.reset(reset.generation(), reset.mediaAnchorMs());
        pipeline.clockSink().onSample(1L, 3_000_000L, 2_000_000L, 10_000L, false);

        assertTrue(clock.snapshot(10_000L).presentedMediaPositionMs().isEmpty());
    }

    @Test
    public void processorResetKeepsTheOwningPipelineReusable() throws Exception {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        hub.beginSession(0L);
        PlaybackMediaAudioPipeline pipeline = PlaybackMediaAudioPipeline.create(hub, clock);
        activate(pipeline);

        pipeline.audioProcessor().reset();
        activate(pipeline);

        assertTrue(hub.isPipelineAttached());
    }

    private static void activate(PlaybackMediaAudioPipeline pipeline) throws Exception {
        pipeline.audioProcessor().configure(
                new AudioProcessor.AudioFormat(16_000, 1, C.ENCODING_PCM_16BIT));
        pipeline.audioProcessor().flush(AudioProcessor.StreamMetadata.DEFAULT);
    }
}
