package com.fongmi.android.tv.player;

import android.os.SystemClock;

import com.github.catvod.crawler.SpiderDebug;

import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class PlaybackTrace {

    public static final String NONE = "none";

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /**
     * Chronological order for reporting, which is not the enum declaration order:
     * a first frame normally arrives before {@code STATE_READY}, so iterating an
     * {@link EnumMap} directly would print the timeline out of sequence.
     */
    private static final Stage[] STAGE_ORDER = {
            Stage.REQUEST,
            Stage.PARSE_COMPLETE,
            Stage.PREPARE,
            Stage.TRACKS,
            Stage.FIRST_FRAME,
            Stage.AUDIO_PLAYABLE,
            Stage.READY,
    };

    private final EnumMap<Stage, Long> stageTimes = new EnumMap<>(Stage.class);
    private String traceId = "";
    private long startedAtMs = -1;
    private long lastStageAtMs = -1;

    public enum Stage {
        REQUEST("request"),
        PARSE_COMPLETE("parse-complete"),
        PREPARE("prepare"),
        TRACKS("tracks"),
        READY("ready"),
        FIRST_FRAME("first-frame"),
        AUDIO_PLAYABLE("audio-playable");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public synchronized String begin() {
        return begin(SystemClock.elapsedRealtime());
    }

    synchronized String begin(long elapsedRealtimeMs) {
        traceId = createId(elapsedRealtimeMs, SEQUENCE.incrementAndGet());
        startedAtMs = Math.max(0, elapsedRealtimeMs);
        lastStageAtMs = startedAtMs;
        stageTimes.clear();
        return traceId;
    }

    public synchronized String ensure() {
        return traceId.isEmpty() ? begin() : traceId;
    }

    synchronized String ensure(long elapsedRealtimeMs) {
        return traceId.isEmpty() ? begin(elapsedRealtimeMs) : traceId;
    }

    public synchronized String current() {
        return normalize(traceId);
    }

    public synchronized void clear() {
        traceId = "";
        startedAtMs = -1;
        lastStageAtMs = -1;
        stageTimes.clear();
    }

    public boolean mark(Stage stage, String detail) {
        return mark(stage, SystemClock.elapsedRealtime(), detail);
    }

    synchronized boolean mark(Stage stage, long elapsedRealtimeMs, String detail) {
        if (traceId.isEmpty() || stage == null || stageTimes.containsKey(stage)) return false;
        long now = Math.max(startedAtMs, elapsedRealtimeMs);
        long elapsedMs = Math.max(0, now - startedAtMs);
        long deltaMs = Math.max(0, now - lastStageAtMs);
        stageTimes.put(stage, now);
        lastStageAtMs = Math.max(lastStageAtMs, now);
        String suffix = detail == null || detail.isBlank() ? "" : " " + detail;
        log("playback-stage", traceId, "stage=%s elapsed=%dms delta=%dms%s", stage.label(), elapsedMs, deltaMs, suffix);
        return true;
    }

    synchronized long stageElapsedMs(Stage stage) {
        Long time = stageTimes.get(stage);
        return time == null || startedAtMs < 0 ? -1 : Math.max(0, time - startedAtMs);
    }

    synchronized boolean hasStage(Stage stage) {
        return stageTimes.containsKey(stage);
    }

    /**
     * Renders the startup timeline for on-screen diagnostics, so a slow start can be
     * localized to a stage without pulling a debug log. Stages appear in the order
     * they were reached; a stage not yet reached is omitted rather than shown as zero.
     */
    public synchronized String startupSummary() {
        if (startedAtMs < 0 || stageTimes.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        long previous = 0;
        for (Stage stage : STAGE_ORDER) {
            Long time = stageTimes.get(stage);
            if (time == null) continue;
            long elapsed = Math.max(0, time - startedAtMs);
            if (builder.length() > 0) builder.append("  ");
            builder.append(stage.label()).append(' ').append(elapsed).append("ms");
            long delta = elapsed - previous;
            if (delta > 0 && previous > 0) builder.append("(+").append(delta).append(')');
            previous = elapsed;
        }
        return builder.toString();
    }

    /**
     * Returns the stage that consumed the most wall time, as {@code label:deltaMs},
     * or an empty string when fewer than two stages have been reached.
     */
    public synchronized String slowestStage() {
        if (startedAtMs < 0 || stageTimes.size() < 2) return "";
        Stage worst = null;
        long worstDelta = -1;
        long previous = 0;
        for (Stage stage : STAGE_ORDER) {
            Long time = stageTimes.get(stage);
            if (time == null) continue;
            long elapsed = Math.max(0, time - startedAtMs);
            long delta = elapsed - previous;
            if (delta > worstDelta) {
                worstDelta = delta;
                worst = stage;
            }
            previous = elapsed;
        }
        return worst == null || worstDelta <= 0 ? "" : worst.label() + ":" + worstDelta + "ms";
    }

    public static String normalize(String traceId) {
        return isValid(traceId) ? traceId : NONE;
    }

    public static void log(String tag, String traceId, String format, Object... args) {
        if (!SpiderDebug.isEnabled()) return;
        Object[] values = new Object[(args == null ? 0 : args.length) + 1];
        values[0] = normalize(traceId);
        if (args != null && args.length > 0) System.arraycopy(args, 0, values, 1, args.length);
        SpiderDebug.log(tag, "trace=%s " + format, values);
    }

    static String createId(long elapsedRealtimeMs, long sequence) {
        long time = Math.max(0, elapsedRealtimeMs);
        long count = Math.max(1, sequence);
        return "p-" + Long.toString(time, 36).toLowerCase(Locale.US) + "-" + Long.toString(count, 36).toLowerCase(Locale.US);
    }

    private static boolean isValid(String value) {
        if (value == null || !value.startsWith("p-") || value.length() < 5) return false;
        int separator = value.indexOf('-', 2);
        if (separator <= 2 || separator >= value.length() - 1) return false;
        return isBase36(value, 2, separator) && isBase36(value, separator + 1, value.length());
    }

    private static boolean isBase36(String value, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') continue;
            if (c >= 'a' && c <= 'z') continue;
            return false;
        }
        return true;
    }
}
