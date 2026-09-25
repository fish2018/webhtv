package com.fongmi.android.tv.ad.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded literal matcher over timestamped speech results. Confined to one worker;
 * callers must supply finalized, incremental, non-overlapping recognition chunks,
 * filter stale session/generation callbacks, and reset on every discontinuity.
 * No user-supplied regular expression is executed.
 */
public final class SpeechAdMatcher {

    public static final long MAX_SEQUENCE_WINDOW_US = 30_000_000L;
    public static final long MATCH_COOLDOWN_US = 30_000_000L;
    public static final int MAX_RECOGNIZED_TEXT_CODE_POINTS = 4_096;
    private static final int MAX_WINDOW_CHARS = 8_192;
    private static final int MAX_WINDOW_RESULTS = 128;

    private final List<State> states;
    private final ArrayDeque<Recognition> window = new ArrayDeque<>();
    private int windowChars;
    private int timelineToken = Integer.MIN_VALUE;
    private boolean hasTimeline;
    private long lastEndUs = -1L;

    public SpeechAdMatcher(SpeechAdRuleSet rules) {
        Objects.requireNonNull(rules, "rules");
        states = new ArrayList<>(rules.rules().size());
        for (SpeechAdRule rule : rules.rules()) states.add(new State(rule));
    }

    public List<Match> accept(String text, long startUs, long endUs, int timelineToken) {
        if (!hasTimeline || this.timelineToken != timelineToken) reset(timelineToken);
        if (startUs < 0L || endUs <= startUs) {
            clearWindow();
            return List.of();
        }
        // A revised partial or a delayed duplicate must not be concatenated as new speech.
        if (startUs < lastEndUs) return List.of();
        lastEndUs = endUs;
        if (endUs - startUs > MAX_SEQUENCE_WINDOW_US || text == null
                || text.length() > 2 * MAX_RECOGNIZED_TEXT_CODE_POINTS
                || text.codePointCount(0, text.length()) > MAX_RECOGNIZED_TEXT_CODE_POINTS) {
            clearWindow();
            return List.of();
        }
        String normalized = SpeechAdRule.normalizeText(text);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length())
                > MAX_RECOGNIZED_TEXT_CODE_POINTS) {
            clearWindow();
            return List.of();
        }
        if (states.isEmpty()) return List.of();
        while (!window.isEmpty() && (endUs - window.peekFirst().startUs() > MAX_SEQUENCE_WINDOW_US
                || window.size() >= MAX_WINDOW_RESULTS
                || windowChars + normalized.length() > MAX_WINDOW_CHARS)) {
            windowChars -= window.removeFirst().text().length();
        }
        window.addLast(new Recognition(normalized, startUs, endUs, timelineToken));
        windowChars += normalized.length();
        // One shared window retains provenance even when the first literal is split
        // across results. Whole-result eviction avoids broken Unicode boundaries.
        StringBuilder buffer = new StringBuilder(windowChars);
        for (Recognition result : window) buffer.append(result.text());
        String transcript = buffer.toString();
        List<Match> matches = new ArrayList<>();
        for (State state : states) {
            int fromIndex = 0;
            for (Recognition result : window) {
                if (state.lastMatchEndUs < 0L || result.startUs() >= state.lastMatchEndUs
                        && result.startUs() - state.lastMatchEndUs >= MATCH_COOLDOWN_US) break;
                fromIndex += result.text().length();
            }
            int firstIndex = -1;
            int endIndex = fromIndex;
            boolean complete = true;
            for (SpeechAdRule.Segment segment : state.rule.segments()) {
                SpeechAdRule.Segment.Match found = segment.find(transcript, endIndex);
                if (found == null) {
                    complete = false;
                    break;
                }
                if (firstIndex < 0) firstIndex = found.start();
                endIndex = found.end();
            }
            if (!complete) continue;
            long firstStartUs = resultAt(firstIndex).startUs();
            long matchEndUs = resultAt(endIndex - 1).endUs();
            matches.add(new Match(state.rule.id(), firstStartUs, matchEndUs,
                    state.rule.preRollMs(), state.rule.postRollMs(), timelineToken));
            state.lastMatchEndUs = matchEndUs;
        }
        return List.copyOf(matches);
    }

    public List<Match> accept(Recognition recognition) {
        Objects.requireNonNull(recognition, "recognition");
        return accept(recognition.text(), recognition.startUs(), recognition.endUs(),
                recognition.timelineToken());
    }

    public void reset() {
        reset(Integer.MIN_VALUE);
        hasTimeline = false;
    }

    public void reset(int timelineToken) {
        this.timelineToken = timelineToken;
        hasTimeline = true;
        lastEndUs = -1L;
        clearWindow();
        for (State state : states) state.lastMatchEndUs = -1L;
    }

    public int timelineToken() {
        return timelineToken;
    }

    private void clearWindow() {
        window.clear();
        windowChars = 0;
    }

    private Recognition resultAt(int index) {
        for (Recognition result : window) {
            if (index < result.text().length()) return result;
            index -= result.text().length();
        }
        throw new IllegalStateException("speech match is outside the retained window");
    }

    public record Recognition(String text, long startUs, long endUs, int timelineToken) {
        public Recognition {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Times conservatively bound whole recognized chunks, not aligned individual words. */
    public record Match(String ruleId, long firstStartUs, long lastEndUs,
                        long preRollMs, long postRollMs, int timelineToken) {
        public Match {
            Objects.requireNonNull(ruleId, "ruleId");
            if (firstStartUs < 0L || lastEndUs <= firstStartUs
                    || preRollMs < 0L || postRollMs < 0L
                    || preRollMs > SpeechAdRule.MAX_ROLL_MS
                    || postRollMs > SpeechAdRule.MAX_ROLL_MS) {
                throw new IllegalArgumentException("speech match interval is invalid");
            }
        }

        public long candidateStartMs() {
            return candidateStartMs(-1L);
        }

        public long candidateStartMs(long durationMs) {
            long startMs = Math.max(0L, firstStartUs / 1_000L - preRollMs);
            return durationMs < 0L ? startMs : Math.min(durationMs, startMs);
        }

        /** A clamped empty interval is not actionable; the downstream policy must reject it. */
        public long candidateEndMs(long durationMs) {
            long endMs = lastEndUs / 1_000L + postRollMs;
            return durationMs < 0L ? endMs : Math.min(durationMs, endMs);
        }
    }

    private static final class State {
        private final SpeechAdRule rule;
        private long lastMatchEndUs = -1L;

        private State(SpeechAdRule rule) {
            this.rule = rule;
        }
    }
}
