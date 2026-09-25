package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PanBenchmarkPlanTest {

    @Test
    public void defaultsToEightButCanIncludeCurrentHigherValue() {
        assertEquals(List.of(1, 2, 4, 8), PanBenchmarkPlan.defaultSweep(32));
        assertEquals(List.of(1, 2, 4, 8, 32), PanBenchmarkPlan.sweep(8, 32, true));
    }

    @Test
    public void acceptsArbitraryUserValueUpTo256() {
        assertEquals(List.of(1, 7, 57, 256), PanBenchmarkPlan.sanitizeThreads(List.of(57, 7, 999, 1)));
        assertEquals(256, PanBenchmarkPlan.normalizeThreads(999));
    }

    @Test
    public void highConcurrencyReceivesEnoughPerWorkerBudget() {
        long budget = PanBenchmarkPlan.roundBudgetBytes(100_000_000L, 256, PanBenchmarkPlan.Mode.STANDARD);
        assertTrue(budget >= 256L * 4L * 1024L * 1024L);
    }

    @Test
    public void standardBudgetTracksRequiredBitrate() {
        long budget = PanBenchmarkPlan.roundBudgetBytes(100_000_000L, 8, PanBenchmarkPlan.Mode.STANDARD);
        assertEquals(125_000_000L, budget);
    }

    @Test
    public void plannedTrafficIncludesProbeWarmupRetriesDirectComparisonsAndAppPhase() {
        List<Integer> threads = List.of(1, 2, 4, 8, 16, 32, 64, 128, 256);
        int appThreads = 256;
        long expected = PanBenchmarkPlan.PROBE_BYTES + PanBenchmarkPlan.PROXY_WARMUP_BYTES * 2;
        for (int repeat = 0; repeat < PanBenchmarkPlan.repeats(PanBenchmarkPlan.Mode.DEEP); repeat++) {
            expected += PanBenchmarkPlan.roundBudgetBytes(0, 1, PanBenchmarkPlan.Mode.DEEP);
            for (int thread : threads) {
                expected += PanBenchmarkPlan.roundBudgetBytes(0, thread, PanBenchmarkPlan.Mode.DEEP) * 2;
                if (thread > 1) expected += PanBenchmarkPlan.roundBudgetBytes(0, PanBenchmarkPlan.directConcurrency(thread), PanBenchmarkPlan.Mode.DEEP);
            }
            expected += PanBenchmarkPlan.roundBudgetBytes(0, appThreads, PanBenchmarkPlan.Mode.DEEP) * 4;
        }

        assertEquals(expected, PanBenchmarkPlan.estimatePlannedBytes(0, threads, PanBenchmarkPlan.Mode.DEEP, true, appThreads));
    }

    @Test
    public void directPlaybackPlanUsesConfiguredThreadsForAppPhaseOnly() {
        int configuredThreads = 64;
        long perRepeat = PanBenchmarkPlan.roundBudgetBytes(0, 1, PanBenchmarkPlan.Mode.STANDARD)
                + PanBenchmarkPlan.roundBudgetBytes(0, configuredThreads, PanBenchmarkPlan.Mode.STANDARD) * 2;
        long expected = PanBenchmarkPlan.PROBE_BYTES + perRepeat * PanBenchmarkPlan.repeats(PanBenchmarkPlan.Mode.STANDARD);

        assertEquals(expected, PanBenchmarkPlan.estimatePlannedBytes(0, List.of(1, 8, 64), PanBenchmarkPlan.Mode.STANDARD, false, configuredThreads));
    }

}
