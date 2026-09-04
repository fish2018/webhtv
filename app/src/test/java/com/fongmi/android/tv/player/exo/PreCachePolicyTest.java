package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreCachePolicyTest {

    @Test
    public void initialAndRecoveryUseIndependentWatermarks() {
        assertEquals(5_000, target(false, -1, 0, 128));
        assertEquals(8_000, target(true, -1, 0, 128));
    }

    @Test
    public void highBitrateTargetFitsInsideEffectiveCapacity() {
        assertEquals(3_435, target(false, -1, 100, 51.2));
        assertEquals(3_435, target(true, -1, 100, 51.2));
    }

    @Test
    public void targetNeverExtendsPastMediaEnd() {
        assertEquals(1_500, target(false, 1_500, 0, 128));
        assertEquals(2_500, target(true, 2_500, 0, 128));
    }

    @Test
    public void loadingRequiresFullWatermark() {
        assertFalse(PreCachePolicy.hasSafeBuffer(4_999, true, 5_000, false));
        assertTrue(PreCachePolicy.hasSafeBuffer(5_000, true, 5_000, false));
        assertFalse(PreCachePolicy.hasSafeBuffer(7_999, true, 8_000, true));
    }

    @Test
    public void seekSuppressionRequiresReadyIdleAndSafeBuffer() {
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_BUFFERING, true, false, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, false, false, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, true, true));
        assertFalse(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, false, false));
        assertTrue(PreCache.shouldReleaseSeekPreloadSuppression(Player.STATE_READY, true, false, true));
    }

    @Test
    public void repeatedPreloadFailuresOpenCircuitAtThreshold() {
        assertFalse(PreCache.shouldOpenPreloadFailureCircuit(1));
        assertTrue(PreCache.shouldOpenPreloadFailureCircuit(2));
    }

    @Test
    public void loadControlIdleFallbackStillKeepsAReserve() {
        assertFalse(PreCachePolicy.hasSafeBuffer(1_999, false, 5_000, false));
        assertTrue(PreCachePolicy.hasSafeBuffer(2_000, false, 5_000, false));
        assertFalse(PreCachePolicy.hasSafeBuffer(2_999, false, 8_000, true));
        assertTrue(PreCachePolicy.hasSafeBuffer(3_000, false, 8_000, true));
    }

    @Test
    public void preloadRangeUsesAtMostHalfOfDiskCache() {
        assertEquals(20_000, preloadLength(20_000, -1, 50, 512));
        assertEquals(10_737, preloadLength(20_000, -1, 100, 256));
        assertEquals(5_368, preloadLength(120_000, -1, 100, 128));
    }

    @Test
    public void unknownBitrateUsesConservativeRangeEstimate() {
        assertEquals(10_737, preloadLength(20_000, -1, 0, 512));
        assertEquals(3_000, preloadLength(20_000, 3_000, 0, 512));
    }

    @Test
    public void aheadTargetUsesMostOfDiskBudgetWithoutCrossingIt() {
        assertEquals(300_000, aheadTarget(300_000, -1, 8, 512));
        assertEquals(34_359, aheadTarget(300_000, -1, 100, 512));
        assertEquals(68_719, aheadTarget(300_000, -1, 0, 512));
    }

    @Test
    public void wholeMediaTargetStillHonorsRemainingDurationAndCapacity() {
        assertEquals(120_000, aheadTarget(Long.MAX_VALUE, 120_000, 8, 512));
        assertEquals(429_496, aheadTarget(Long.MAX_VALUE, -1, 8, 512));
    }

    @Test
    public void refillWatermarkCreatesAStableHysteresisBand() {
        assertEquals(240_000, PreCachePolicy.preloadResumeWatermarkMs(300_000, 20_000));
        assertEquals(40_000, PreCachePolicy.preloadResumeWatermarkMs(60_000, 20_000));
        assertEquals(0, PreCachePolicy.preloadResumeWatermarkMs(10_000, 20_000));
    }

    private static long target(boolean recovery, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        int capacity = (int) Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.safeBufferTargetMs(recovery, remainingMs, bitrate, capacity);
    }

    private static long preloadLength(long configuredMs, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        long capacity = Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.preloadLengthMs(configuredMs, remainingMs, bitrate, capacity);
    }

    private static long aheadTarget(long configuredMs, long remainingMs, double bitrateMbps, double capacityMib) {
        long bitrate = Math.round(bitrateMbps * 1_000_000);
        long capacity = Math.round(capacityMib * 1024 * 1024);
        return PreCachePolicy.preloadAheadTargetMs(configuredMs, remainingMs, bitrate, capacity);
    }
}
