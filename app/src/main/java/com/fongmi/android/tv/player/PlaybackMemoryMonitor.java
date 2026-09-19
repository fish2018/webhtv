package com.fongmi.android.tv.player;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Android adapter that samples low-frequency process memory facts for the active playback session. */
public final class PlaybackMemoryMonitor {

    static final long PERIODIC_SAMPLE_INTERVAL_MS = 30_000;
    static final long PSS_SAMPLE_INTERVAL_MS = 5 * 60_000;

    private static final PlaybackMemoryMonitor PROCESS = new PlaybackMemoryMonitor(PlaybackMemoryCoordinator.process());

    private final Object sessionLock;
    private final PlaybackMemoryCoordinator coordinator;
    private final ScheduledExecutorService executor;

    private volatile Context applicationContext;
    private PlaybackAutoContext.SessionToken scheduledSession;
    private ScheduledFuture<?> periodicFuture;
    private long lastPssSampleAtElapsedMs;

    private PlaybackMemoryMonitor(PlaybackMemoryCoordinator coordinator) {
        this.sessionLock = new Object();
        this.coordinator = coordinator;
        this.scheduledSession = PlaybackAutoContext.SessionToken.none();
        this.lastPssSampleAtElapsedMs = -1;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "playback-memory");
            thread.setDaemon(true);
            return thread;
        });
        coordinator.addListener(update -> PlaybackTrace.log(
                "playback-memory", update.session().traceId(), "%s", update.logSummary()));
    }

    public static PlaybackMemoryMonitor process() {
        return PROCESS;
    }

    public void initialize(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        applicationContext = appContext == null ? context : appContext;
    }

    public boolean beginSession(PlaybackAutoContext.SessionToken session) {
        synchronized (sessionLock) {
            if (!coordinator.beginSession(session)) return false;
            cancelPeriodicLocked();
            scheduledSession = session;
            executor.execute(() -> sampleSafely(session, PlaybackAutoContext.MemoryTrigger.SESSION_START, null));
            periodicFuture = executor.scheduleWithFixedDelay(
                    () -> sampleSafely(session, PlaybackAutoContext.MemoryTrigger.PERIODIC, null),
                    PERIODIC_SAMPLE_INTERVAL_MS,
                    PERIODIC_SAMPLE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
            return true;
        }
    }

    public boolean endSession(PlaybackAutoContext.SessionToken session) {
        synchronized (sessionLock) {
            if (!coordinator.endSession(session)) return false;
            if (session.equals(scheduledSession)) {
                scheduledSession = PlaybackAutoContext.SessionToken.none();
                cancelPeriodicLocked();
            }
            return true;
        }
    }

    public void onTrimMemory(int level) {
        PlaybackAutoContext.SessionToken session = coordinator.activeSession();
        if (!session.active()) return;
        executor.execute(() -> sampleSafely(session, PlaybackAutoContext.MemoryTrigger.TRIM_MEMORY, level));
    }

    public void onLowMemory() {
        PlaybackAutoContext.SessionToken session = coordinator.activeSession();
        if (!session.active()) return;
        executor.execute(() -> sampleSafely(session, PlaybackAutoContext.MemoryTrigger.LOW_MEMORY, null));
    }

    private void sampleSafely(PlaybackAutoContext.SessionToken session,
                              PlaybackAutoContext.MemoryTrigger trigger,
                              Integer trimLevel) {
        try {
            sample(session, trigger, trimLevel);
        } catch (Throwable error) {
            logFailure(session, "sample", error);
        }
    }

    private void sample(PlaybackAutoContext.SessionToken session,
                        PlaybackAutoContext.MemoryTrigger trigger,
                        Integer trimLevel) {
        if (!session.equals(coordinator.activeSession())) return;
        long sampledAt = SystemClock.elapsedRealtime();
        JavaHeapMetrics javaHeap = readJavaHeap(session);
        SystemMemoryMetrics system = readSystemMemory(session, applicationContext);
        Long nativeHeapBytes = readNativeHeap(session);
        Long pssBytes = shouldSamplePss(sampledAt) ? readPss(session) : null;
        PlaybackAutoContext.MemorySnapshot snapshot = new PlaybackAutoContext.MemorySnapshot(
                trigger,
                javaHeap.usedBytes(),
                javaHeap.limitBytes(),
                javaHeap.headroomBytes(),
                system.lowRamDevice(),
                system.totalBytes(),
                system.availableBytes(),
                system.thresholdBytes(),
                system.lowMemory(),
                trimLevel,
                system.processImportance(),
                nativeHeapBytes);
        coordinator.publish(session, snapshot, pssBytes, Build.VERSION.SDK_INT, sampledAt);
        DiagnosticControls.forTrace(session.traceId(), "play.resources", "existing-memory-monitor", e -> e
                .observed("javaBytes", javaHeap.usedBytes()).observed("nativeBytes", nativeHeapBytes)
                .observed("lowRam", system.lowRamDevice()).observed("trimLevel", trimLevel).observed("reason", trigger.name())
                .observed("metricScope", "existing monitor; no additional proc/PSS polling"));
    }

    private boolean shouldSamplePss(long nowElapsedMs) {
        if (lastPssSampleAtElapsedMs >= 0
                && nowElapsedMs - lastPssSampleAtElapsedMs < PSS_SAMPLE_INTERVAL_MS) {
            return false;
        }
        lastPssSampleAtElapsedMs = nowElapsedMs;
        return true;
    }

    private static JavaHeapMetrics readJavaHeap(PlaybackAutoContext.SessionToken session) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long limit = runtime.maxMemory();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            long used = Math.max(0, total - Math.min(total, Math.max(0, free)));
            long headroom = Math.max(0, limit - Math.min(limit, used));
            return new JavaHeapMetrics(used, Math.max(0, limit), headroom);
        } catch (Throwable error) {
            logFailure(session, "java-heap", error);
            return JavaHeapMetrics.unknown();
        }
    }

    private static SystemMemoryMetrics readSystemMemory(
            PlaybackAutoContext.SessionToken session, Context context) {
        if (context == null) return SystemMemoryMetrics.unknown();
        ActivityManager manager;
        try {
            manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        } catch (Throwable error) {
            logFailure(session, "activity-manager", error);
            return SystemMemoryMetrics.unknown();
        }
        if (manager == null) return SystemMemoryMetrics.unknown();

        Long totalBytes = null;
        Long availableBytes = null;
        Long thresholdBytes = null;
        Boolean lowMemory = null;
        Boolean lowRamDevice = null;
        Integer processImportance = null;
        try {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memoryInfo);
            totalBytes = memoryInfo.totalMem;
            availableBytes = memoryInfo.availMem;
            thresholdBytes = memoryInfo.threshold;
            lowMemory = memoryInfo.lowMemory;
        } catch (Throwable error) {
            logFailure(session, "memory-info", error);
        }
        try {
            lowRamDevice = manager.isLowRamDevice();
        } catch (Throwable error) {
            logFailure(session, "low-ram", error);
        }
        try {
            ActivityManager.RunningAppProcessInfo processInfo = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(processInfo);
            processImportance = processInfo.importance;
        } catch (Throwable error) {
            logFailure(session, "process-memory-state", error);
        }
        return new SystemMemoryMetrics(totalBytes, availableBytes, thresholdBytes, lowMemory,
                lowRamDevice, processImportance);
    }

    private static Long readNativeHeap(PlaybackAutoContext.SessionToken session) {
        try {
            return Math.max(0, Debug.getNativeHeapAllocatedSize());
        } catch (Throwable error) {
            logFailure(session, "native-heap", error);
            return null;
        }
    }

    private static Long readPss(PlaybackAutoContext.SessionToken session) {
        try {
            long pssKilobytes = Math.max(0, Debug.getPss());
            return pssKilobytes > Long.MAX_VALUE / 1024 ? Long.MAX_VALUE : pssKilobytes * 1024;
        } catch (Throwable error) {
            logFailure(session, "pss", error);
            return null;
        }
    }

    private void cancelPeriodicLocked() {
        if (periodicFuture == null) return;
        periodicFuture.cancel(false);
        periodicFuture = null;
    }

    private static void logFailure(
            PlaybackAutoContext.SessionToken session, String action, Throwable error) {
        PlaybackTrace.log("playback-memory", session == null ? PlaybackTrace.NONE : session.traceId(),
                "action=%s result=failed exception=%s", action,
                error == null ? "unknown" : error.getClass().getSimpleName());
    }

    private record JavaHeapMetrics(Long usedBytes, Long limitBytes, Long headroomBytes) {

        private static JavaHeapMetrics unknown() {
            return new JavaHeapMetrics(null, null, null);
        }
    }

    private record SystemMemoryMetrics(
            Long totalBytes,
            Long availableBytes,
            Long thresholdBytes,
            Boolean lowMemory,
            Boolean lowRamDevice,
            Integer processImportance) {

        private static SystemMemoryMetrics unknown() {
            return new SystemMemoryMetrics(null, null, null, null, null, null);
        }
    }
}
