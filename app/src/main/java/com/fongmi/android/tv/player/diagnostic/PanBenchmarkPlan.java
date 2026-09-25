package com.fongmi.android.tv.player.diagnostic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PanBenchmarkPlan {

    public static final int DEFAULT_MAX_THREADS = 8;
    public static final int MAX_THREADS = 256;
    public static final int MAX_DIRECT_CONCURRENCY = 32;
    public static final long PROBE_BYTES = 64L * 1024L;
    public static final long PROXY_WARMUP_BYTES = 512L * 1024L;
    private static final long MIB = 1024L * 1024L;
    private static final long MIN_BYTES_PER_WORKER = 4L * MIB;
    private static final int[] STANDARD_STEPS = {1, 2, 4, 8, 16, 32, 64, 128, 256};

    private PanBenchmarkPlan() {
    }

    public static List<Integer> defaultSweep(int configuredThreads) {
        return sweep(DEFAULT_MAX_THREADS, configuredThreads, false);
    }

    public static List<Integer> sweep(int maxThreads, int configuredThreads, boolean includeConfiguredAboveMax) {
        int max = normalizeThreads(maxThreads);
        Set<Integer> values = new LinkedHashSet<>();
        for (int step : STANDARD_STEPS) if (step <= max) values.add(step);
        int configured = configuredThreads <= 0 ? 0 : normalizeThreads(configuredThreads);
        if (configured > 0 && (configured <= max || includeConfiguredAboveMax)) values.add(configured);
        return new ArrayList<>(values);
    }

    public static List<Integer> sanitizeThreads(Collection<Integer> requested) {
        Set<Integer> values = new LinkedHashSet<>();
        if (requested != null) for (Integer value : requested) if (value != null) values.add(normalizeThreads(value));
        if (values.isEmpty()) values.add(1);
        List<Integer> result = new ArrayList<>(values);
        result.sort(Integer::compareTo);
        return result;
    }

    public static long roundBudgetBytes(long requiredBitsPerSecond, int threads, Mode mode) {
        int concurrency = normalizeThreads(threads);
        Mode actualMode = mode == null ? Mode.STANDARD : mode;
        long requiredBytesPerSecond = requiredBitsPerSecond <= 0 ? 0 : divideRoundUp(requiredBitsPerSecond, 8);
        long target = requiredBytesPerSecond <= 0 ? actualMode.defaultBytes : multiplySaturated(requiredBytesPerSecond, actualMode.targetSeconds) * 5 / 4;
        long workerFloor = multiplySaturated(concurrency, MIN_BYTES_PER_WORKER);
        long normalBudget = Math.min(Math.max(target, actualMode.minBytes), actualMode.maxBytes);
        return Math.max(normalBudget, workerFloor);
    }

    public static long roundTimeLimitMs(Mode mode) {
        Mode actualMode = mode == null ? Mode.STANDARD : mode;
        return actualMode.maxDurationSeconds * 1000L;
    }

    public static int repeats(Mode mode) {
        return (mode == null ? Mode.STANDARD : mode).repeats;
    }

    public static int directConcurrency(int requestedThreads) {
        return Math.min(normalizeThreads(requestedThreads), MAX_DIRECT_CONCURRENCY);
    }

    public static long estimateTotalBytes(long requiredBitsPerSecond, Collection<Integer> threads, Mode mode, int measuredRepeats) {
        long total = 0;
        for (int value : sanitizeThreads(threads)) total = addSaturated(total, roundBudgetBytes(requiredBitsPerSecond, value, mode));
        int repeats = Math.max(0, Math.min(measuredRepeats, 3));
        if (repeats > 0) {
            List<Integer> values = sanitizeThreads(threads);
            int bestCandidate = values.get(values.size() - 1);
            total = addSaturated(total, multiplySaturated(roundBudgetBytes(requiredBitsPerSecond, bestCandidate, mode), repeats));
        }
        return total;
    }

    public static long estimatePlannedBytes(long requiredBitsPerSecond, Collection<Integer> threads, Mode mode, boolean proxied) {
        List<Integer> values = sanitizeThreads(threads);
        int maxThreads = values.get(values.size() - 1);
        int appThreads = proxied ? maxThreads : 1;
        return estimatePlannedBytes(requiredBitsPerSecond, values, mode, proxied, appThreads);
    }

    public static long estimatePlannedBytes(long requiredBitsPerSecond, Collection<Integer> threads, Mode mode, boolean proxied, int appThreads) {
        List<Integer> values = sanitizeThreads(threads);
        int repeats = repeats(mode);
        int normalizedAppThreads = normalizeThreads(appThreads);
        long total = PROBE_BYTES;
        if (proxied) total = addSaturated(total, multiplySaturated(PROXY_WARMUP_BYTES, 2));
        for (int repeat = 0; repeat < repeats; repeat++) {
            total = addSaturated(total, roundBudgetBytes(requiredBitsPerSecond, 1, mode));
            if (proxied) {
                for (int value : values) {
                    total = addSaturated(total, retryableBudgetBytes(requiredBitsPerSecond, value, mode));
                    if (value > 1) total = addSaturated(total, roundBudgetBytes(requiredBitsPerSecond, directConcurrency(value), mode));
                }
            }
            total = addSaturated(total, retryableBudgetBytes(requiredBitsPerSecond, normalizedAppThreads, mode));
            if (proxied && repeats > 1) total = addSaturated(total, retryableBudgetBytes(requiredBitsPerSecond, normalizedAppThreads, mode));
        }
        return total;
    }

    public static int normalizeThreads(int value) {
        return Math.max(1, Math.min(value, MAX_THREADS));
    }

    private static long divideRoundUp(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static long multiplySaturated(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long retryableBudgetBytes(long requiredBitsPerSecond, int threads, Mode mode) {
        return multiplySaturated(roundBudgetBytes(requiredBitsPerSecond, threads, mode), 2);
    }

    private static long addSaturated(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public enum Mode {
        QUICK(6, 8, 1, 32 * MIB, 128 * MIB, 64 * MIB),
        STANDARD(8, 10, 2, 64 * MIB, 512 * MIB, 128 * MIB),
        DEEP(10, 12, 3, 128 * MIB, 2L * 1024L * MIB, 256 * MIB);

        private final long targetSeconds;
        private final long maxDurationSeconds;
        private final int repeats;
        private final long minBytes;
        private final long maxBytes;
        private final long defaultBytes;

        Mode(long targetSeconds, long maxDurationSeconds, int repeats, long minBytes, long maxBytes, long defaultBytes) {
            this.targetSeconds = targetSeconds;
            this.maxDurationSeconds = maxDurationSeconds;
            this.repeats = repeats;
            this.minBytes = minBytes;
            this.maxBytes = maxBytes;
            this.defaultBytes = defaultBytes;
        }
    }
}
