package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackPanelResourceMonitorTest {

    @Test
    public void samplesOnlyWhileActiveAndStopsImmediately() {
        FakeSampler sampler = new FakeSampler();
        PlaybackPanelResourceMonitor monitor =
                new PlaybackPanelResourceMonitor(sampler, Runnable::run);

        monitor.requestSample();
        assertEquals(0, sampler.cpuReads);
        assertEquals(0, sampler.memoryReads);

        monitor.start();
        monitor.requestSample();
        assertEquals(1, sampler.cpuReads);
        assertEquals(1, sampler.memoryReads);
        assertTrue(sampler.lastIncludedPss);
        assertFalse(monitor.snapshot().cpuAvailable());
        assertTrue(monitor.snapshot().memoryAvailable());
        assertEquals(100, monitor.snapshot().pssBytes());
        assertEquals(150, monitor.snapshot().graphicsPssBytes());

        sampler.advance(1_000, 300);
        monitor.requestSample();
        PlaybackPanelResourceMonitor.Snapshot active = monitor.snapshot();
        assertEquals(2, sampler.cpuReads);
        assertEquals(1, sampler.memoryReads);
        assertTrue(active.cpuAvailable());
        assertEquals(30, active.cpuPercent(), 0.001);

        monitor.stop();
        sampler.advance(31_000, 500);
        monitor.requestSample();
        assertEquals(2, sampler.cpuReads);
        assertEquals(1, sampler.memoryReads);
        assertFalse(monitor.snapshot().active());
    }

    @Test
    public void memoryAndPssUseSeparateCadences() {
        FakeSampler sampler = new FakeSampler();
        PlaybackPanelResourceMonitor monitor =
                new PlaybackPanelResourceMonitor(sampler, Runnable::run);
        monitor.start();
        monitor.requestSample();

        sampler.advance(5_000, 100);
        monitor.requestSample();
        assertEquals(2, sampler.memoryReads);
        assertFalse(sampler.lastIncludedPss);
        assertEquals(100, monitor.snapshot().pssBytes());
        assertEquals(150, monitor.snapshot().graphicsPssBytes());

        sampler.advance(25_000, 100);
        monitor.requestSample();
        assertEquals(3, sampler.memoryReads);
        assertTrue(sampler.lastIncludedPss);
    }

    @Test
    public void queuedSampleIsDiscardedWhenPanelCloses() {
        FakeSampler sampler = new FakeSampler();
        QueuedExecutor executor = new QueuedExecutor();
        PlaybackPanelResourceMonitor monitor =
                new PlaybackPanelResourceMonitor(sampler, executor);
        monitor.start();
        monitor.requestSample();

        monitor.stop();
        executor.runPending();

        assertEquals(0, sampler.cpuReads);
        assertEquals(0, sampler.memoryReads);
        assertFalse(monitor.snapshot().active());
    }

    private static final class FakeSampler
            implements PlaybackPanelResourceMonitor.Sampler {

        private long now;
        private long cpuTime;
        private int cpuReads;
        private int memoryReads;
        private boolean lastIncludedPss;

        @Override
        public long elapsedRealtimeMs() {
            return now;
        }

        @Override
        public long processCpuTimeMs() {
            cpuReads++;
            return cpuTime;
        }

        @Override
        public PlaybackPanelResourceMonitor.MemoryMetrics readMemory(
                boolean includePss) {
            memoryReads++;
            lastIncludedPss = includePss;
            return new PlaybackPanelResourceMonitor.MemoryMetrics(
                    includePss ? 100 : -1, includePss ? 150 : -1,
                    200, 300, 400, 500, 600);
        }

        void advance(long elapsedMs, long cpuMs) {
            now += elapsedMs;
            cpuTime += cpuMs;
        }
    }

    private static final class QueuedExecutor implements java.util.concurrent.Executor {

        private Runnable pending;

        @Override
        public void execute(Runnable command) {
            pending = command;
        }

        void runPending() {
            if (pending == null) return;
            Runnable command = pending;
            pending = null;
            command.run();
        }
    }
}
