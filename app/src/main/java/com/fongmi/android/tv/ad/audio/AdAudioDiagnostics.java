package com.fongmi.android.tv.ad.audio;

import com.github.catvod.crawler.SpiderDebug;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class AdAudioDiagnostics {

    static final String LOG_TAG = "ad-audio";

    public enum Code {
        QUEUE_OVERFLOW,
        STALE_GENERATION,
        CLOCK_UNAVAILABLE,
        RULE_LOAD_FAILED,
        MATCHER_ERROR,
        SEEK_REJECTED,
        SPEECH_MODEL_UNAVAILABLE,
        SPEECH_START_FAILED,
        SPEECH_TEXT_EMPTY,
        SPEECH_MATCHED,
        SPEECH_COOLDOWN,
        SPEECH_STALE_CALLBACK
    }

    private final EnumMap<Code, Long> counts = new EnumMap<>(Code.class);
    private Code lastCode;

    public void record(Code code) {
        if (code == null) return;
        long total;
        synchronized (this) {
            total = counts.getOrDefault(code, 0L) + 1L;
            counts.put(code, total);
            lastCode = code;
        }
        // Logged outside the monitor and heavily sampled. SpiderDebug.log writes to disk
        // synchronously under a process-wide lock, and codes such as STALE_GENERATION or
        // QUEUE_OVERFLOW can fire once per PCM frame on the ExoPlayer audio thread.
        if (shouldLogCount(total)) log("%s n=%d", code, total);
    }

    /** Logs the first few occurrences, then decays to one line per 100. */
    static boolean shouldLogCount(long total) {
        if (total <= 3L) return true;
        return total % 100L == 0L;
    }

    /**
     * Emits a debug-log line when the user has enabled 调试日志. Callers must never pass
     * recognized text, keywords, media URLs or headers; only low-cardinality state.
     * Never call this per audio frame without sampling: it performs blocking file I/O.
     */
    static void log(String message, Object... args) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(LOG_TAG, message, args);
    }

    public synchronized long count(Code code) {
        return counts.getOrDefault(code, 0L);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Collections.unmodifiableMap(new EnumMap<>(counts)), lastCode);
    }

    public record Snapshot(Map<Code, Long> counts, Code lastCode) {
        public Snapshot {
            counts = Map.copyOf(counts);
        }

        public long count(Code code) {
            return counts.getOrDefault(code, 0L);
        }
    }
}
