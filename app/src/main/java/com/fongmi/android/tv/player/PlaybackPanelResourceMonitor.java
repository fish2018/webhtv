package com.fongmi.android.tv.player;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;

import com.fongmi.android.tv.utils.Task;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** On-demand app CPU and memory sampler owned by the visible playback diagnostics panel. */
public final class PlaybackPanelResourceMonitor {

    static final long CPU_SAMPLE_INTERVAL_MS = 900;
    static final long MEMORY_SAMPLE_INTERVAL_MS = 5_000;
    static final long PSS_SAMPLE_INTERVAL_MS = 30_000;
    static final long CPU_WINDOW_MS = 10_000;

    private final Object lock;
    private final Sampler sampler;
    private final Executor executor;
    private final AtomicBoolean taskRunning;
    private final TimedPercentageWindow cpuWindow;

    private volatile Snapshot snapshot;
    private boolean active;
    private long generation;
    private long lastCpuRequestAtMs;
    private long lastMemoryRequestAtMs;
    private long lastPssRequestAtMs;
    private long previousCpuWallAtMs;
    private long previousCpuTimeMs;
    private MemoryMetrics memoryMetrics;

    public PlaybackPanelResourceMonitor(Context context) {
        this(new AndroidSampler(context), Task.executor());
    }

    PlaybackPanelResourceMonitor(Sampler sampler, Executor executor) {
        this.lock = new Object();
        this.sampler = sampler;
        this.executor = executor;
        this.taskRunning = new AtomicBoolean();
        this.cpuWindow = new TimedPercentageWindow(CPU_WINDOW_MS);
        this.snapshot = Snapshot.empty();
        resetLocked();
    }

    public void start() {
        synchronized (lock) {
            if (active) return;
            active = true;
            generation++;
            resetLocked();
        }
    }

    public void stop() {
        synchronized (lock) {
            if (!active && !snapshot.active()) return;
            active = false;
            generation++;
            resetLocked();
        }
    }

    public boolean isActive() {
        synchronized (lock) {
            return active;
        }
    }

    /** Requests only samples whose cadence is due. This method has no recurring scheduler. */
    public void requestSample() {
        long token;
        boolean sampleCpu;
        boolean sampleMemory;
        boolean samplePss;
        synchronized (lock) {
            if (!active || taskRunning.get()) return;
            long now = sampler.elapsedRealtimeMs();
            sampleCpu = due(lastCpuRequestAtMs, now, CPU_SAMPLE_INTERVAL_MS);
            sampleMemory = due(lastMemoryRequestAtMs, now, MEMORY_SAMPLE_INTERVAL_MS);
            samplePss = due(lastPssRequestAtMs, now, PSS_SAMPLE_INTERVAL_MS);
            if (!sampleCpu && !sampleMemory && !samplePss) return;
            if (!taskRunning.compareAndSet(false, true)) return;
            if (sampleCpu) lastCpuRequestAtMs = now;
            if (sampleMemory || samplePss) lastMemoryRequestAtMs = now;
            if (samplePss) lastPssRequestAtMs = now;
            token = generation;
        }
        try {
            executor.execute(() -> sample(token, sampleCpu,
                    sampleMemory || samplePss, samplePss));
        } catch (RuntimeException error) {
            taskRunning.set(false);
        }
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private void sample(long token, boolean sampleCpu,
                        boolean sampleMemory, boolean samplePss) {
        try {
            if (!isActive(token)) return;
            long wallAtMs = sampleCpu ? sampler.elapsedRealtimeMs() : -1;
            long cpuTimeMs = sampleCpu ? sampler.processCpuTimeMs() : -1;
            MemoryMetrics measured = sampleMemory
                    ? sampler.readMemory(samplePss) : null;
            synchronized (lock) {
                if (!active || token != generation) return;
                if (sampleCpu) publishCpuLocked(wallAtMs, cpuTimeMs);
                if (measured != null) {
                    memoryMetrics = memoryMetrics.merge(measured, samplePss);
                }
                publishSnapshotLocked(sampleCpu ? wallAtMs
                        : sampler.elapsedRealtimeMs());
            }
        } finally {
            taskRunning.set(false);
        }
    }

    private void publishCpuLocked(long wallAtMs, long cpuTimeMs) {
        if (wallAtMs < 0 || cpuTimeMs < 0) return;
        if (previousCpuWallAtMs >= 0 && previousCpuTimeMs >= 0) {
            long wallDelta = wallAtMs - previousCpuWallAtMs;
            long cpuDelta = cpuTimeMs - previousCpuTimeMs;
            if (wallDelta > 0 && cpuDelta >= 0) {
                cpuWindow.add(wallAtMs, wallDelta,
                        cpuDelta * 100.0 / wallDelta);
            }
        }
        previousCpuWallAtMs = wallAtMs;
        previousCpuTimeMs = cpuTimeMs;
    }

    private void publishSnapshotLocked(long sampledAtMs) {
        TimedPercentageWindow.Stats cpu = cpuWindow.snapshot(sampledAtMs);
        snapshot = new Snapshot(true, cpu.available(), cpu.current(),
                cpu.average(), cpu.peak(), memoryMetrics.pssBytes(),
                memoryMetrics.graphicsPssBytes(),
                memoryMetrics.javaHeapUsedBytes(), memoryMetrics.javaHeapLimitBytes(),
                memoryMetrics.nativeHeapBytes(), memoryMetrics.systemAvailableBytes(),
                memoryMetrics.systemTotalBytes(), Math.max(0, sampledAtMs));
    }

    private boolean isActive(long token) {
        synchronized (lock) {
            return active && token == generation;
        }
    }

    private void resetLocked() {
        lastCpuRequestAtMs = -1;
        lastMemoryRequestAtMs = -1;
        lastPssRequestAtMs = -1;
        previousCpuWallAtMs = -1;
        previousCpuTimeMs = -1;
        memoryMetrics = MemoryMetrics.empty();
        cpuWindow.reset();
        snapshot = active ? Snapshot.pending() : Snapshot.empty();
    }

    private static boolean due(long lastAtMs, long nowMs, long intervalMs) {
        return lastAtMs < 0 || nowMs - lastAtMs >= intervalMs;
    }

    interface Sampler {

        long elapsedRealtimeMs();

        long processCpuTimeMs();

        MemoryMetrics readMemory(boolean includePss);
    }

    record MemoryMetrics(long pssBytes, long graphicsPssBytes,
                         long javaHeapUsedBytes,
                         long javaHeapLimitBytes, long nativeHeapBytes,
                         long systemAvailableBytes, long systemTotalBytes) {

        static MemoryMetrics empty() {
            return new MemoryMetrics(-1, -1, -1, -1, -1, -1, -1);
        }

        MemoryMetrics merge(MemoryMetrics measured, boolean replacePss) {
            if (measured == null) return this;
            long pss = replacePss && measured.pssBytes() >= 0
                    ? measured.pssBytes() : pssBytes;
            long graphicsPss = replacePss && measured.graphicsPssBytes() >= 0
                    ? measured.graphicsPssBytes() : graphicsPssBytes;
            return new MemoryMetrics(pss, graphicsPss,
                    prefer(measured.javaHeapUsedBytes(), javaHeapUsedBytes),
                    prefer(measured.javaHeapLimitBytes(), javaHeapLimitBytes),
                    prefer(measured.nativeHeapBytes(), nativeHeapBytes),
                    prefer(measured.systemAvailableBytes(), systemAvailableBytes),
                    prefer(measured.systemTotalBytes(), systemTotalBytes));
        }

        private static long prefer(long current, long fallback) {
            return current >= 0 ? current : fallback;
        }
    }

    public record Snapshot(boolean active, boolean cpuAvailable,
                           double cpuPercent, double cpuAveragePercent,
                           double cpuPeakPercent, long pssBytes,
                           long graphicsPssBytes,
                           long javaHeapUsedBytes, long javaHeapLimitBytes,
                           long nativeHeapBytes, long systemAvailableBytes,
                           long systemTotalBytes, long sampledAtElapsedMs) {

        static Snapshot empty() {
            return new Snapshot(false, false, 0, 0, 0,
                    -1, -1, -1, -1, -1, -1, -1, -1);
        }

        static Snapshot pending() {
            return new Snapshot(true, false, 0, 0, 0,
                    -1, -1, -1, -1, -1, -1, -1, -1);
        }

        public boolean memoryAvailable() {
            return pssBytes >= 0 || graphicsPssBytes >= 0
                    || javaHeapUsedBytes >= 0
                    || nativeHeapBytes >= 0 || systemAvailableBytes >= 0;
        }
    }

    private static final class AndroidSampler implements Sampler {

        private final Context context;

        private AndroidSampler(Context context) {
            if (context == null) {
                this.context = null;
            } else {
                Context application = context.getApplicationContext();
                this.context = application == null ? context : application;
            }
        }

        @Override
        public long elapsedRealtimeMs() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public long processCpuTimeMs() {
            try {
                return Math.max(0, Process.getElapsedCpuTime());
            } catch (Throwable ignored) {
                return -1;
            }
        }

        @Override
        public MemoryMetrics readMemory(boolean includePss) {
            long javaUsed = -1;
            long javaLimit = -1;
            long nativeHeap = -1;
            long systemAvailable = -1;
            long systemTotal = -1;
            long pss = -1;
            long graphicsPss = -1;
            try {
                Runtime runtime = Runtime.getRuntime();
                javaLimit = Math.max(0, runtime.maxMemory());
                long total = Math.max(0, runtime.totalMemory());
                long free = Math.max(0, runtime.freeMemory());
                javaUsed = Math.max(0, total - Math.min(total, free));
            } catch (Throwable ignored) {
            }
            try {
                nativeHeap = Math.max(0, Debug.getNativeHeapAllocatedSize());
            } catch (Throwable ignored) {
            }
            if (context != null) {
                try {
                    ActivityManager manager = (ActivityManager) context.getSystemService(
                            Context.ACTIVITY_SERVICE);
                    if (manager != null) {
                        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                        manager.getMemoryInfo(info);
                        systemAvailable = Math.max(0, info.availMem);
                        systemTotal = Math.max(0, info.totalMem);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (includePss) {
                try {
                    Debug.MemoryInfo info = new Debug.MemoryInfo();
                    Debug.getMemoryInfo(info);
                    pss = kilobytesToBytes(info.getTotalPss());
                    graphicsPss = memoryStatBytes(info, "summary.graphics");
                } catch (Throwable ignored) {
                }
            }
            return new MemoryMetrics(pss, graphicsPss, javaUsed, javaLimit, nativeHeap,
                    systemAvailable, systemTotal);
        }

        private static long memoryStatBytes(Debug.MemoryInfo info, String name) {
            if (info == null || name == null) return -1;
            try {
                String value = info.getMemoryStat(name);
                if (value == null || value.trim().isEmpty()) return -1;
                return kilobytesToBytes(Long.parseLong(value.trim()));
            } catch (Throwable ignored) {
                return -1;
            }
        }

        private static long kilobytesToBytes(long kilobytes) {
            if (kilobytes < 0) return -1;
            return kilobytes > Long.MAX_VALUE / 1024
                    ? Long.MAX_VALUE : kilobytes * 1024;
        }
    }
}
