package com.fongmi.android.tv.player.audio;

import java.util.OptionalLong;

public final class PlaybackMediaClock {

    private static final long UNSET_TIME_MS = Long.MIN_VALUE;

    public record Snapshot(long generation, long mediaAnchorMs,
                           long capturedUntilMs, long presentedCaptureMs,
                           boolean playing, boolean fresh) {

        public OptionalLong mapCaptureToMediaMs(long captureTimeMs) {
            if (!fresh || captureTimeMs < 0L) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(saturatedAdd(mediaAnchorMs, captureTimeMs));
        }

        public OptionalLong presentedMediaPositionMs() {
            return mapCaptureToMediaMs(presentedCaptureMs);
        }
    }

    private final long maxSampleAgeMs;
    private long generation;
    private long mediaAnchorMs;
    private long capturedUntilMs;
    private long writtenUs;
    private long outputPositionUs;
    private long sampleElapsedMs = UNSET_TIME_MS;
    private boolean playing;

    public PlaybackMediaClock(long maxSampleAgeMs) {
        if (maxSampleAgeMs < 0L) throw new IllegalArgumentException("maxSampleAgeMs must not be negative");
        this.maxSampleAgeMs = maxSampleAgeMs;
    }

    public synchronized void reset(long generation, long mediaAnchorMs) {
        this.generation = generation;
        this.mediaAnchorMs = Math.max(0L, mediaAnchorMs);
        capturedUntilMs = 0L;
        writtenUs = 0L;
        outputPositionUs = 0L;
        sampleElapsedMs = UNSET_TIME_MS;
        playing = false;
    }

    public synchronized void onCaptured(long generation, long capturedUntilMs) {
        if (generation != this.generation) return;
        this.capturedUntilMs = Math.max(this.capturedUntilMs, Math.max(0L, capturedUntilMs));
    }

    public synchronized void onOutputSample(long generation, long writtenUs,
                                            long outputPositionUs, long sampledElapsedMs,
                                            boolean playing) {
        if (generation != this.generation) return;
        this.writtenUs = Math.max(0L, writtenUs);
        this.outputPositionUs = Math.max(0L, outputPositionUs);
        this.sampleElapsedMs = sampledElapsedMs;
        this.playing = playing;
    }

    public synchronized void onOutputReleased(long generation) {
        if (generation != this.generation) return;
        writtenUs = 0L;
        outputPositionUs = 0L;
        sampleElapsedMs = UNSET_TIME_MS;
        playing = false;
    }

    public synchronized Snapshot snapshot(long elapsedNowMs) {
        long ageMs = sampleElapsedMs == UNSET_TIME_MS ? Long.MAX_VALUE : elapsedNowMs - sampleElapsedMs;
        boolean fresh = ageMs >= 0L && ageMs <= maxSampleAgeMs;
        long currentPositionUs = outputPositionUs;
        if (fresh && playing && ageMs > 0L) currentPositionUs = saturatedAdd(currentPositionUs, ageMs * 1_000L);
        currentPositionUs = Math.min(writtenUs, Math.max(0L, currentPositionUs));
        long presentedCaptureMs = Math.min(capturedUntilMs, currentPositionUs / 1_000L);
        return new Snapshot(generation, mediaAnchorMs, capturedUntilMs,
                presentedCaptureMs, playing, fresh);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }
}
