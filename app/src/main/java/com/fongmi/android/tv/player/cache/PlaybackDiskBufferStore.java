package com.fongmi.android.tv.player.cache;

import androidx.media3.common.MediaItem;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks completed time ranges written to disk during the current process. */
public final class PlaybackDiskBufferStore {

    private static final int MAX_MEDIA_ENTRIES = 16;
    private static final int MAX_RANGES_PER_MEDIA = 128;
    private static final long QUERY_LOG_INTERVAL_MS = 5000;
    private static final PlaybackDiskBufferStore PROCESS = new PlaybackDiskBufferStore();

    private final Map<String, RangeSet> mediaRanges = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, QueryLogState> queryLogs = new LinkedHashMap<>();

    public static PlaybackDiskBufferStore process() {
        return PROCESS;
    }

    public static String mediaKey(MediaItem mediaItem) {
        if (mediaItem == null) return "";
        if (mediaItem.mediaId != null && !mediaItem.mediaId.isBlank()) return mediaItem.mediaId;
        if (mediaItem.localConfiguration == null || mediaItem.localConfiguration.uri == null) return "";
        return mediaItem.localConfiguration.uri.toString();
    }

    public synchronized void reset(String mediaKey) {
        if (mediaKey == null || mediaKey.isBlank()) return;
        mediaRanges.put(mediaKey, new RangeSet());
        trimMediaEntries();
        log("reset", mediaKey, 0, 0, 0);
    }

    public synchronized void recordCompleted(String mediaKey, long startMs, long endMs) {
        if (mediaKey == null || mediaKey.isBlank() || startMs < 0 || endMs <= startMs) return;
        RangeSet ranges = mediaRanges.computeIfAbsent(mediaKey, ignored -> new RangeSet());
        ranges.add(startMs, endMs);
        trimMediaEntries();
        log("record", mediaKey, startMs, endMs, endMs);
    }

    public synchronized long contiguousEnd(String mediaKey, long fromMs, long gapToleranceMs) {
        if (mediaKey == null || mediaKey.isBlank()) return Math.max(0, fromMs);
        RangeSet ranges = mediaRanges.get(mediaKey);
        long result = ranges == null
                ? Math.max(0, fromMs)
                : ranges.contiguousEnd(Math.max(0, fromMs), Math.max(0, gapToleranceMs));
        logQuery(mediaKey, fromMs, gapToleranceMs, result);
        return result;
    }

    /** Returns the native or disk-backed end, bounded to the known media duration. */
    public synchronized long effectiveEnd(
            String mediaKey, long nativeBufferedEndMs, long durationMs, long gapToleranceMs) {
        long nativeEnd = Math.max(0, nativeBufferedEndMs);
        long diskEnd = contiguousEnd(mediaKey, nativeEnd, gapToleranceMs);
        long effective = Math.max(nativeEnd, diskEnd);
        return durationMs > 0 ? Math.min(effective, durationMs) : effective;
    }

    public synchronized void clear() {
        mediaRanges.clear();
        queryLogs.clear();
    }

    private static void log(String action, String mediaKey, long startMs, long endMs, long resultMs) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("playback-disk-buffer",
                "action=%s key=%s startMs=%d endMs=%d resultMs=%d",
                action, Integer.toHexString(mediaKey == null ? 0 : mediaKey.hashCode()),
                startMs, endMs, resultMs);
    }

    private void logQuery(String mediaKey, long fromMs, long gapToleranceMs, long resultMs) {
        if (!SpiderDebug.isEnabled()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        QueryLogState previous = queryLogs.get(mediaKey);
        if (previous != null && previous.resultMs() == resultMs
                && now - previous.loggedAtMs() < QUERY_LOG_INTERVAL_MS) return;
        queryLogs.put(mediaKey, new QueryLogState(resultMs, now));
        log("query", mediaKey, fromMs, gapToleranceMs, resultMs);
    }

    private void trimMediaEntries() {
        while (mediaRanges.size() > MAX_MEDIA_ENTRIES) {
            String eldest = mediaRanges.keySet().iterator().next();
            mediaRanges.remove(eldest);
        }
    }

    private static final class RangeSet {

        private final List<Range> ranges = new ArrayList<>();

        private void add(long startMs, long endMs) {
            Range incoming = new Range(startMs, endMs);
            List<Range> merged = new ArrayList<>(ranges.size() + 1);
            boolean inserted = false;
            for (Range current : ranges) {
                if (current.endMs < incoming.startMs) {
                    merged.add(current);
                } else if (incoming.endMs < current.startMs) {
                    if (!inserted) {
                        merged.add(incoming);
                        inserted = true;
                    }
                    merged.add(current);
                } else {
                    incoming = new Range(
                            Math.min(incoming.startMs, current.startMs),
                            Math.max(incoming.endMs, current.endMs));
                }
            }
            if (!inserted) merged.add(incoming);
            ranges.clear();
            int first = Math.max(0, merged.size() - MAX_RANGES_PER_MEDIA);
            ranges.addAll(merged.subList(first, merged.size()));
        }

        private long contiguousEnd(long fromMs, long gapToleranceMs) {
            long endMs = fromMs;
            for (Range range : ranges) {
                if (range.endMs < fromMs) continue;
                if (range.startMs > saturatedAdd(endMs, gapToleranceMs)) break;
                endMs = Math.max(endMs, range.endMs);
            }
            return endMs;
        }

        private static long saturatedAdd(long value, long increment) {
            return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
        }
    }

    private record Range(long startMs, long endMs) {
    }

    private record QueryLogState(long resultMs, long loggedAtMs) {
    }
}
