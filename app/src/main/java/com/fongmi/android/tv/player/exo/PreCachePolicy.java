package com.fongmi.android.tv.player.exo;

final class PreCachePolicy {

    static final long INITIAL_SAFE_BUFFER_MS = 5_000;
    static final long RECOVERY_SAFE_BUFFER_MS = 8_000;
    static final long PLAYBACK_STABILITY_GRACE_MS = 5_000;
    static final long NEXT_RANGE_DELAY_MS = 5_000;
    private static final long INITIAL_IDLE_FLOOR_MS = 2_000;
    private static final long RECOVERY_IDLE_FLOOR_MS = 3_000;
    private static final int CAPACITY_HEADROOM_PERCENT = 80;
    private static final int PRELOAD_CACHE_PERCENT = 50;
    private static final int PRELOAD_AHEAD_CACHE_PERCENT = 80;
    private static final long MIN_CAPACITY_TARGET_MS = 1_000;
    private static final long UNKNOWN_BITRATE_BITS_PER_SECOND = 200_000_000L;
    private static final long UNKNOWN_AHEAD_BITRATE_BITS_PER_SECOND = 50_000_000L;

    private PreCachePolicy() {
    }

    static long safeBufferTargetMs(boolean recovery, long remainingMs, long bitrateBitsPerSecond, int capacityBytes) {
        long targetMs = recovery ? RECOVERY_SAFE_BUFFER_MS : INITIAL_SAFE_BUFFER_MS;
        if (remainingMs >= 0) targetMs = Math.min(targetMs, remainingMs);
        long capacityMs = capacityDurationMs(bitrateBitsPerSecond, capacityBytes);
        if (capacityMs > 0) {
            long usableCapacityMs = Math.max(MIN_CAPACITY_TARGET_MS, capacityMs * CAPACITY_HEADROOM_PERCENT / 100);
            targetMs = Math.min(targetMs, usableCapacityMs);
        }
        return Math.max(0, targetMs);
    }

    static boolean hasSafeBuffer(long bufferedDurationMs, boolean loading, long targetMs, boolean recovery) {
        if (bufferedDurationMs >= targetMs) return true;
        long idleFloorMs = recovery ? RECOVERY_IDLE_FLOOR_MS : INITIAL_IDLE_FLOOR_MS;
        return !loading && bufferedDurationMs >= Math.min(targetMs, idleFloorMs);
    }

    static boolean isPlaybackStableForPreload(
            long nowMs,
            long preloadNotBeforeMs,
            boolean playing,
            boolean loading) {
        return playing && !loading && nowMs >= 0 && preloadNotBeforeMs >= 0
                && nowMs >= preloadNotBeforeMs;
    }

    static long nextRangeDelayMs(boolean completed) {
        return completed ? NEXT_RANGE_DELAY_MS : 0;
    }

    static long preloadLengthMs(long configuredLengthMs, long remainingMs, long bitrateBitsPerSecond, long cacheCapacityBytes) {
        long lengthMs = Math.max(0, configuredLengthMs);
        if (remainingMs >= 0) lengthMs = Math.min(lengthMs, remainingMs);
        if (cacheCapacityBytes <= 0) return 0;
        long byteBudget = cacheCapacityBytes / 100 * PRELOAD_CACHE_PERCENT;
        long estimatedBitrate = bitrateBitsPerSecond > 0 ? bitrateBitsPerSecond : UNKNOWN_BITRATE_BITS_PER_SECOND;
        long capacityMs = durationForBytesMs(byteBudget, estimatedBitrate);
        return Math.min(lengthMs, capacityMs);
    }

    static long preloadAheadTargetMs(long configuredAheadMs, long remainingMs,
                                     long bitrateBitsPerSecond, long cacheCapacityBytes) {
        long targetMs = Math.max(0, configuredAheadMs);
        if (remainingMs >= 0) targetMs = Math.min(targetMs, remainingMs);
        if (cacheCapacityBytes <= 0) return 0;
        long byteBudget = cacheCapacityBytes / 100 * PRELOAD_AHEAD_CACHE_PERCENT;
        long estimatedBitrate = bitrateBitsPerSecond > 0
                ? bitrateBitsPerSecond : UNKNOWN_AHEAD_BITRATE_BITS_PER_SECOND;
        return Math.min(targetMs, durationForBytesMs(byteBudget, estimatedBitrate));
    }

    static long preloadResumeWatermarkMs(long targetMs, long chunkMs) {
        long refillBandMs = Math.max(Math.max(0, chunkMs), Math.max(0, targetMs) / 5);
        return Math.max(0, targetMs - refillBandMs);
    }

    private static long capacityDurationMs(long bitrateBitsPerSecond, int capacityBytes) {
        if (bitrateBitsPerSecond <= 0 || capacityBytes <= 0) return 0;
        return durationForBytesMs(capacityBytes, bitrateBitsPerSecond);
    }

    private static long durationForBytesMs(long bytes, long bitrateBitsPerSecond) {
        if (bytes <= 0 || bitrateBitsPerSecond <= 0) return 0;
        long capacityBits = bytes > Long.MAX_VALUE / 8L ? Long.MAX_VALUE : bytes * 8L;
        if (capacityBits > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        return capacityBits * 1_000L / bitrateBitsPerSecond;
    }
}
