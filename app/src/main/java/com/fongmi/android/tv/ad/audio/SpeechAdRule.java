package com.fongmi.android.tv.ad.audio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One bounded speech-ad rule. A rule is intentionally not a regular expression:
 * each segment contains ordered literal parts and optional {@code *} gaps.
 */
public final class SpeechAdRule {

    static final int MAX_SEGMENTS = 8;
    static final int MAX_SEGMENT_CODE_POINTS = 128;
    static final long MAX_ROLL_MS = 120_000L;

    private final String id;
    private final List<Segment> segments;
    private final long preRollMs;
    private final long postRollMs;
    private final String canonical;

    public SpeechAdRule(String id, List<Segment> segments,
                        long preRollMs, long postRollMs) {
        this.id = requireId(id);
        if (segments == null || segments.isEmpty() || segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("speech rule segment count is invalid");
        }
        this.segments = List.copyOf(segments);
        if (preRollMs < 0L || postRollMs < 0L
                || preRollMs > MAX_ROLL_MS || postRollMs > MAX_ROLL_MS
                || preRollMs % 1_000L != 0L || postRollMs % 1_000L != 0L) {
            throw new IllegalArgumentException("speech rule roll window is invalid");
        }
        this.preRollMs = preRollMs;
        this.postRollMs = postRollMs;
        this.canonical = canonicalText(this.segments, preRollMs, postRollMs);
    }

    public String id() {
        return id;
    }

    public List<Segment> segments() {
        return segments;
    }

    public long preRollMs() {
        return preRollMs;
    }

    public long postRollMs() {
        return postRollMs;
    }

    /** Stable normalized text representation used for deduplication and ID generation. */
    public String canonical() {
        return canonical;
    }

    static String normalizeText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < normalized.length(); ) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                if (!previousWhitespace) result.append(' ');
                previousWhitespace = true;
            } else if (!Character.isISOControl(codePoint)
                    && Character.getType(codePoint) != Character.FORMAT) {
                result.appendCodePoint(codePoint);
                previousWhitespace = false;
            }
        }
        return result.toString().trim();
    }

    static String canonicalText(List<Segment> segments, long preRollMs, long postRollMs) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) value.append('>');
            value.append(segments.get(i).canonical());
        }
        if (preRollMs == 0L) {
            value.append(',').append(postRollMs / 1_000L);
        } else {
            value.append(",[").append(preRollMs / 1_000L)
                    .append(',').append(postRollMs / 1_000L).append(']');
        }
        return value.toString();
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        if (value.isEmpty() || value.length() > 64
                || !Character.isLowerCase(value.charAt(0))
                && !Character.isDigit(value.charAt(0))) {
            throw new IllegalArgumentException("speech rule id is invalid");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')
                    && c != '.' && c != '_' && c != '-') {
                throw new IllegalArgumentException("speech rule id is invalid");
            }
        }
        return value;
    }

    public static final class Segment {
        private final List<String> literals;
        private final List<Boolean> wildcardBetween;
        private final boolean leadingWildcard;
        private final boolean trailingWildcard;
        private final String canonical;

        public Segment(List<String> literals, List<Boolean> wildcardBetween,
                       boolean leadingWildcard, boolean trailingWildcard) {
            if (literals == null || literals.isEmpty()) {
                throw new IllegalArgumentException("speech segment literals are required");
            }
            if (wildcardBetween == null || wildcardBetween.size() != literals.size() - 1) {
                throw new IllegalArgumentException("speech segment wildcard layout is invalid");
            }
            List<String> normalized = new ArrayList<>(literals.size());
            for (int i = 0; i < literals.size(); i++) {
                String value = normalizeText(literals.get(i));
                if (value.isEmpty() || value.contains("//")
                        || value.chars().anyMatch(c -> "*>,[]#".indexOf(c) >= 0)) {
                    throw new IllegalArgumentException("speech segment literal is invalid");
                }
                // Adjacent literal parts are one search term; otherwise the first
                // occurrence of a prefix could hide a later complete match.
                if (i > 0 && !wildcardBetween.get(i - 1)) {
                    int last = normalized.size() - 1;
                    normalized.set(last, normalized.get(last) + value);
                } else {
                    normalized.add(value);
                }
            }
            this.literals = List.copyOf(normalized);
            this.wildcardBetween = java.util.Collections.nCopies(normalized.size() - 1, true);
            this.leadingWildcard = leadingWildcard;
            this.trailingWildcard = trailingWildcard;
            this.canonical = buildCanonical(this.literals, this.wildcardBetween,
                    leadingWildcard, trailingWildcard);
            if (canonical.codePointCount(0, canonical.length()) > MAX_SEGMENT_CODE_POINTS
                    || canonical.codePoints().noneMatch(Character::isLetterOrDigit)) {
                throw new IllegalArgumentException("speech segment length or text is invalid");
            }
        }

        public List<String> literals() {
            return literals;
        }

        public boolean leadingWildcard() {
            return leadingWildcard;
        }

        public boolean trailingWildcard() {
            return trailingWildcard;
        }

        public String canonical() {
            return canonical;
        }

        /** Finds the first ordered match at or after {@code fromIndex}. */
        Match find(String text, int fromIndex) {
            int cursor = Math.max(0, fromIndex);
            int start = -1;
            for (int i = 0; i < literals.size(); i++) {
                String literal = literals.get(i);
                int found = i == 0
                        ? text.indexOf(literal, cursor)
                        : wildcardBetween.get(i - 1)
                        ? text.indexOf(literal, cursor)
                        : text.startsWith(literal, cursor) ? cursor : -1;
                if (found < 0) return null;
                if (start < 0) start = found;
                cursor = found + literal.length();
            }
            return new Match(start, cursor);
        }

        private static String buildCanonical(List<String> literals,
                                             List<Boolean> wildcardBetween,
                                             boolean leadingWildcard,
                                             boolean trailingWildcard) {
            StringBuilder result = new StringBuilder();
            if (leadingWildcard) result.append('*');
            for (int i = 0; i < literals.size(); i++) {
                if (i > 0 && wildcardBetween.get(i - 1)) result.append('*');
                result.append(literals.get(i));
            }
            if (trailingWildcard) result.append('*');
            return result.toString();
        }

        record Match(int start, int end) {
        }
    }
}
