package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

final class ExoLoadControlPolicy {

    private static final int MIN_BUFFER_MS = 15_000;
    private static final int MAX_BUFFER_MS = 30_000;
    private static final int AUTO_LOCAL_MIN_BUFFER_MS = 1_000;
    private static final int AUTO_LOCAL_MAX_BUFFER_MS = 15_000;
    private static final int AUTO_LOCAL_START_BUFFER_MS = 500;
    private static final int AUTO_LOCAL_REBUFFER_MS = 1_000;
    private static final int AUTO_STREAMING_MAX_REBUFFER_MS = 15_000;

    private ExoLoadControlPolicy() {
    }

    static BufferDurations resolve(int profile, int bufferLevel) {
        return switch (profile) {
            case PlaybackPerformanceSetting.PROFILE_RECOMMENDED, PlaybackPerformanceSetting.PROFILE_AUTO -> new BufferDurations(30_000, 60_000);
            case PlaybackPerformanceSetting.PROFILE_COMPATIBLE,
                 PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT -> new BufferDurations(15_000, 30_000);
            default -> custom(bufferLevel);
        };
    }

    static boolean prioritizeTime(boolean configured) {
        return configured;
    }

    static AutomaticConfiguration automatic(int streamingStartBufferMs) {
        BufferDurations streaming = resolve(PlaybackPerformanceSetting.PROFILE_AUTO, 1);
        BufferDurations local = new BufferDurations(
                AUTO_LOCAL_MIN_BUFFER_MS,
                AUTO_LOCAL_MAX_BUFFER_MS);
        return new AutomaticConfiguration(
                streaming,
                local,
                Math.clamp(streamingStartBufferMs, 0, streaming.minBufferMs()),
                AUTO_STREAMING_MAX_REBUFFER_MS,
                AUTO_LOCAL_START_BUFFER_MS,
                AUTO_LOCAL_REBUFFER_MS,
                false,
                true);
    }

    private static BufferDurations custom(int bufferLevel) {
        int level = Math.clamp(bufferLevel, 1, 15);
        int minBufferMs = MIN_BUFFER_MS + (level - 1) * (MAX_BUFFER_MS - MIN_BUFFER_MS) / 14;
        return new BufferDurations(minBufferMs, minBufferMs * 2);
    }

    record BufferDurations(int minBufferMs, int maxBufferMs) {
    }

    record AutomaticConfiguration(
            BufferDurations streaming,
            BufferDurations local,
            int streamingStartBufferMs,
            int streamingRebufferMs,
            int localStartBufferMs,
            int localRebufferMs,
            boolean streamingPrioritizeTime,
            boolean localPrioritizeTime) {

        AutomaticConfiguration {
            if (streaming == null || local == null) throw new IllegalArgumentException("buffer durations required");
            streamingStartBufferMs = Math.clamp(streamingStartBufferMs, 0, streaming.minBufferMs());
            streamingRebufferMs = Math.clamp(streamingRebufferMs, 0, streaming.minBufferMs());
            localStartBufferMs = Math.clamp(localStartBufferMs, 0, local.minBufferMs());
            localRebufferMs = Math.clamp(localRebufferMs, 0, local.minBufferMs());
        }
    }
}
