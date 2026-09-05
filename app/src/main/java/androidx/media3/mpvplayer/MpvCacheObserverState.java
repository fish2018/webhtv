package androidx.media3.mpvplayer;

import java.util.Arrays;

final class MpvCacheObserverState {

    static final long INITIAL_OBSERVER_GRACE_MS = 2_000;
    static final long FALLBACK_INTERVAL_MS = 5_000;
    static final long PAUSED_TIMELINE_QUERY_INTERVAL_MS = 1_000;
    static final long DYNAMIC_OBSERVER_STALE_MS = 15_000;

    enum Metric {
        DURATION,
        END,
        READER_POSITION,
        SPEED,
        BUFFERING_STATE,
        FORWARD_BYTES,
        TOTAL_BYTES,
        FILE_BYTES,
        IDLE,
        UNDERRUN,
        BOF,
        EOF
    }

    private static final int ALL_OBSERVED_MASK = (1 << Metric.values().length) - 1;
    private int observedMask;
    private final long[] lastObserverAtMs = new long[Metric.values().length];
    private long fileLoadedAtMs = -1;
    private long lastFallbackQueryAtMs = -1;
    private long lastPausedTimelineQueryAtMs = -1;

    MpvCacheObserverState() {
        Arrays.fill(lastObserverAtMs, -1);
    }

    boolean record(String property, Object value) {
        return record(property, value, -1);
    }

    boolean record(String property, Object value, long nowMs) {
        Metric metric = metricForProperty(property);
        if (metric == null || value == null) return false;
        int bit = bit(metric);
        boolean firstValue = (observedMask & bit) == 0;
        observedMask |= bit;
        if (nowMs >= 0 && isDynamic(metric)) lastObserverAtMs[metric.ordinal()] = Math.max(0, nowMs);
        return firstValue;
    }

    void onFileLoaded(long nowMs) {
        if (fileLoadedAtMs < 0) fileLoadedAtMs = Math.max(0, nowMs);
    }

    void onPlaybackDiscontinuity(long nowMs) {
        long timeMs = Math.max(0, nowMs);
        fileLoadedAtMs = timeMs;
        lastFallbackQueryAtMs = -1;
        lastPausedTimelineQueryAtMs = -1;
        for (Metric metric : Metric.values()) {
            if (isDynamic(metric) && (observedMask & bit(metric)) != 0) {
                lastObserverAtMs[metric.ordinal()] = timeMs;
            }
        }
    }

    boolean shouldQueryFallback(
            boolean fileLoaded,
            boolean cacheActive,
            boolean playbackActive,
            long nowMs) {
        if (!fileLoaded || playbackActive) return false;
        if (fileLoadedAtMs < 0) {
            onFileLoaded(nowMs);
            return false;
        }
        if (!elapsed(nowMs, fileLoadedAtMs, INITIAL_OBSERVER_GRACE_MS)) return false;
        if (lastFallbackQueryAtMs >= 0 && !elapsed(nowMs, lastFallbackQueryAtMs, FALLBACK_INTERVAL_MS)) return false;
        return observedMask != ALL_OBSERVED_MASK
                || cacheActive && hasStaleDynamicObserver(nowMs);
    }

    boolean needsFallback(Metric metric, boolean cacheActive, long nowMs) {
        if ((observedMask & bit(metric)) == 0) return true;
        return cacheActive && isDynamic(metric) && observerStale(metric, nowMs);
    }

    boolean shouldQueryPausedTimeline(
            boolean fileLoaded,
            boolean paused,
            boolean cacheEnabled,
            long nowMs) {
        if (!fileLoaded || !paused || !cacheEnabled) return false;
        return lastPausedTimelineQueryAtMs < 0
                || elapsed(nowMs, lastPausedTimelineQueryAtMs,
                PAUSED_TIMELINE_QUERY_INTERVAL_MS);
    }

    void onFallbackQuery(long nowMs) {
        lastFallbackQueryAtMs = Math.max(0, nowMs);
    }

    boolean hasObservedValues() {
        return observedMask != 0;
    }

    void onPausedTimelineQuery(long nowMs) {
        lastPausedTimelineQueryAtMs = Math.max(0, nowMs);
    }

    int observedCount() {
        return Integer.bitCount(observedMask);
    }

    void reset() {
        observedMask = 0;
        Arrays.fill(lastObserverAtMs, -1);
        fileLoadedAtMs = -1;
        lastFallbackQueryAtMs = -1;
        lastPausedTimelineQueryAtMs = -1;
    }

    private static int bit(Metric metric) {
        return 1 << metric.ordinal();
    }

    private boolean hasStaleDynamicObserver(long nowMs) {
        for (Metric metric : Metric.values()) {
            if (isDynamic(metric)
                    && (observedMask & bit(metric)) != 0
                    && observerStale(metric, nowMs)) return true;
        }
        return false;
    }

    private boolean observerStale(Metric metric, long nowMs) {
        long observedAtMs = lastObserverAtMs[metric.ordinal()];
        return observedAtMs >= 0
                && elapsed(nowMs, observedAtMs, DYNAMIC_OBSERVER_STALE_MS);
    }

    private static boolean elapsed(long nowMs, long sinceMs, long thresholdMs) {
        return nowMs >= sinceMs && nowMs - sinceMs >= thresholdMs;
    }

    private static boolean isDynamic(Metric metric) {
        return switch (metric) {
            case DURATION, END, READER_POSITION, SPEED, BUFFERING_STATE, FORWARD_BYTES, TOTAL_BYTES, FILE_BYTES -> true;
            case IDLE, UNDERRUN, BOF, EOF -> false;
        };
    }

    private static Metric metricForProperty(String property) {
        if (property == null) return null;
        return switch (property) {
            case "demuxer-cache-duration", "demuxer-cache-state/cache-duration" -> Metric.DURATION;
            case "demuxer-cache-time", "demuxer-cache-state/cache-end" -> Metric.END;
            case "demuxer-cache-state/reader-pts" -> Metric.READER_POSITION;
            case "cache-speed", "demuxer-cache-state/raw-input-rate" -> Metric.SPEED;
            case "cache-buffering-state" -> Metric.BUFFERING_STATE;
            case "demuxer-cache-state/fw-bytes" -> Metric.FORWARD_BYTES;
            case "demuxer-cache-state/total-bytes" -> Metric.TOTAL_BYTES;
            case "demuxer-cache-state/file-cache-bytes" -> Metric.FILE_BYTES;
            case "demuxer-cache-idle", "demuxer-cache-state/idle" -> Metric.IDLE;
            case "demuxer-cache-state/underrun" -> Metric.UNDERRUN;
            case "demuxer-cache-state/bof-cached" -> Metric.BOF;
            case "demuxer-cache-state/eof-cached" -> Metric.EOF;
            default -> null;
        };
    }
}
