package com.fongmi.android.tv.player.audio;

import androidx.media3.common.audio.AudioProcessor;

import java.util.Objects;

public record PlaybackMediaAudioPipeline(
        AudioProcessor audioProcessor,
        PlaybackMediaAudioOutputProvider.ClockSink clockSink) {

    public PlaybackMediaAudioPipeline {
        Objects.requireNonNull(audioProcessor, "audioProcessor");
        Objects.requireNonNull(clockSink, "clockSink");
    }

    public static PlaybackMediaAudioPipeline create(PlaybackMediaSignalHub hub,
                                                    PlaybackMediaClock clock) {
        Objects.requireNonNull(hub, "hub");
        Objects.requireNonNull(clock, "clock");
        PipelineGate gate = attachGate(hub);
        return new PlaybackMediaAudioPipeline(
                new PlaybackMediaAudioProcessor(hub, clock, gate),
                new HubClockSink(hub, clock, gate));
    }

    static PipelineGate attachGate(PlaybackMediaSignalHub hub) {
        Objects.requireNonNull(hub, "hub");
        return new PipelineGate(hub, hub.attachPipeline());
    }

    private static final class HubClockSink implements PlaybackMediaAudioOutputProvider.ClockSink {
        private final PlaybackMediaSignalHub hub;
        private final PlaybackMediaClock clock;
        private final PipelineGate pipelineGate;
        private long latestOutputId;

        private HubClockSink(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             PipelineGate pipelineGate) {
            this.hub = hub;
            this.clock = clock;
            this.pipelineGate = pipelineGate;
        }

        @Override
        public synchronized void onSample(long outputId, long writtenUs, long positionUs,
                                          long sampledElapsedMs, boolean playing) {
            if (!pipelineGate.isBoundToCurrentSession()) return;
            if (outputId < latestOutputId) return;
            latestOutputId = outputId;
            PlaybackMediaSignalHub.Session session = hub.session();
            clock.onOutputSample(session.generation(), writtenUs, positionUs,
                    sampledElapsedMs, playing);
        }

        @Override
        public synchronized void onReleased(long outputId) {
            if (!pipelineGate.isBoundToCurrentSession()) return;
            if (outputId != latestOutputId) return;
            PlaybackMediaSignalHub.Session session = hub.session();
            clock.onOutputReleased(session.generation());
        }
    }

    static final class PipelineGate {
        private final PlaybackMediaSignalHub hub;
        private final PlaybackMediaSignalHub.PipelineLease lease;
        private long sessionId = Long.MIN_VALUE;
        private long generation = Long.MIN_VALUE;

        private PipelineGate(PlaybackMediaSignalHub hub,
                             PlaybackMediaSignalHub.PipelineLease lease) {
            this.hub = hub;
            this.lease = lease;
        }

        synchronized void bind(PlaybackMediaSignalHub.Session session) {
            if (!lease.isCurrent()) return;
            sessionId = session.id();
            generation = session.generation();
        }

        boolean isCurrent() {
            return lease.isCurrent();
        }

        synchronized boolean isBoundToCurrentSession() {
            if (!lease.isCurrent()) return false;
            PlaybackMediaSignalHub.Session current = hub.session();
            return current.id() == sessionId && current.generation() == generation;
        }

    }
}
