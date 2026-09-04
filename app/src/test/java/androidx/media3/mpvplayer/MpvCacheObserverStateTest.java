package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvCacheObserverStateTest {

    @Test
    public void firstObserverValueDisablesFallbackForThatMetric() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.DURATION, false, 0));
        assertTrue(state.record("demuxer-cache-state/cache-duration", 4.5, 100));
        assertFalse(state.needsFallback(MpvCacheObserverState.Metric.DURATION, false, 100));
        assertEquals(1, state.observedCount());
    }

    @Test
    public void aliasesShareOneLogicalMetric() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertTrue(state.record("cache-speed", 1024L, 100));
        assertFalse(state.record("demuxer-cache-state/raw-input-rate", 2048L, 200));
        assertFalse(state.needsFallback(MpvCacheObserverState.Metric.SPEED, false, 200));
        assertEquals(1, state.observedCount());
    }

    @Test
    public void unavailableAndUnrelatedPropertiesKeepFallbackEnabled() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        assertFalse(state.record("demuxer-cache-state/fw-bytes", null, 100));
        assertFalse(state.record("time-pos", 1.0, 100));
        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.FORWARD_BYTES, false, 100));
        assertEquals(0, state.observedCount());
    }

    @Test
    public void fallbackQueriesWaitUntilFileLoaded() {
        MpvCacheObserverState state = new MpvCacheObserverState();

        state.onFileLoaded(1_000);
        assertFalse(state.shouldQueryFallback(false, true, false, 20_000));
        assertFalse(state.shouldQueryFallback(true, true, false, 2_999));
        assertTrue(state.shouldQueryFallback(true, true, false, 3_000));

        state.onFallbackQuery(3_000);
        assertFalse(state.shouldQueryFallback(true, true, false, 7_999));
        assertTrue(state.shouldQueryFallback(true, true, false, 8_000));
    }

    @Test
    public void fullyObservedCacheNeedsNoSynchronousFallback() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        recordAllMetrics(state, 1_000);
        state.onFileLoaded(1_000);

        assertEquals(12, state.observedCount());
        assertFalse(state.shouldQueryFallback(true, false, false, 20_000));
    }

    @Test
    public void activeCacheUsesLowFrequencyFallbackAfterDynamicObserversGoSilent() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        recordAllMetrics(state, 1_000);
        state.onFileLoaded(1_000);

        assertFalse(state.shouldQueryFallback(true, true, false, 15_999));
        assertTrue(state.shouldQueryFallback(true, true, false, 16_000));
        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.DURATION, true, 16_000));
        assertFalse(state.needsFallback(MpvCacheObserverState.Metric.IDLE, true, 16_000));

        state.onFallbackQuery(16_000);
        assertFalse(state.shouldQueryFallback(true, true, false, 20_999));
        assertTrue(state.shouldQueryFallback(true, true, false, 21_000));
    }

    @Test
    public void freshSpeedObserverDoesNotHideStaleTimelineMetrics() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        recordAllMetrics(state, 1_000);
        state.onFileLoaded(1_000);
        assertTrue(state.shouldQueryFallback(true, true, false, 16_000));

        state.record("cache-speed", 4096L, 16_000);

        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.DURATION, true, 16_000));
        assertFalse(state.needsFallback(MpvCacheObserverState.Metric.SPEED, true, 16_000));
        assertTrue(state.shouldQueryFallback(true, true, false, 16_000));
    }

    @Test
    public void activePlaybackNeverUsesSynchronousFallback() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        state.onFileLoaded(1_000);

        assertFalse(state.shouldQueryFallback(true, true, true, 20_000));
        assertTrue(state.shouldQueryFallback(true, true, false, 20_000));
    }

    @Test
    public void pausedEnabledCacheQueriesTimelineOncePerSecondEvenWhenIdle() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        state.onFileLoaded(1_000);

        assertTrue(state.shouldQueryPausedTimeline(true, true, true, 1_000));
        state.onPausedTimelineQuery(1_000);
        assertFalse(state.shouldQueryPausedTimeline(true, true, true, 1_999));
        assertTrue(state.shouldQueryPausedTimeline(true, true, true, 2_000));
        assertFalse(state.shouldQueryPausedTimeline(true, false, true, 3_000));
        assertFalse(state.shouldQueryPausedTimeline(true, true, false, 3_000));
    }

    @Test
    public void seekRestartsGraceAndDynamicSilenceWindows() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        recordAllMetrics(state, 1_000);
        state.onFileLoaded(1_000);
        assertTrue(state.shouldQueryFallback(true, true, false, 16_000));

        state.onPlaybackDiscontinuity(16_000);

        assertFalse(state.shouldQueryFallback(true, true, false, 17_999));
        assertFalse(state.shouldQueryFallback(true, true, false, 30_999));
        assertTrue(state.shouldQueryFallback(true, true, false, 31_000));
    }

    @Test
    public void resetRequiresFreshObserverValuesForNewMedia() {
        MpvCacheObserverState state = new MpvCacheObserverState();
        state.record("demuxer-cache-state/idle", false, 100);
        state.record("demuxer-cache-state/eof-cached", true, 100);
        state.onFileLoaded(100);
        state.onFallbackQuery(3_000);

        assertEquals(2, state.observedCount());
        state.reset();

        assertEquals(0, state.observedCount());
        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.IDLE, false, 0));
        assertTrue(state.needsFallback(MpvCacheObserverState.Metric.EOF, false, 0));
        assertFalse(state.shouldQueryFallback(true, true, false, 20_000));
    }

    private static void recordAllMetrics(MpvCacheObserverState state, long nowMs) {
        state.record("demuxer-cache-duration", 1.0, nowMs);
        state.record("demuxer-cache-time", 2.0, nowMs);
        state.record("demuxer-cache-state/reader-pts", 1.0, nowMs);
        state.record("cache-speed", 1024L, nowMs);
        state.record("cache-buffering-state", 100L, nowMs);
        state.record("demuxer-cache-state/fw-bytes", 1024L, nowMs);
        state.record("demuxer-cache-state/total-bytes", 2048L, nowMs);
        state.record("demuxer-cache-state/file-cache-bytes", 0L, nowMs);
        state.record("demuxer-cache-idle", false, nowMs);
        state.record("demuxer-cache-state/underrun", false, nowMs);
        state.record("demuxer-cache-state/bof-cached", true, nowMs);
        state.record("demuxer-cache-state/eof-cached", false, nowMs);
    }
}
