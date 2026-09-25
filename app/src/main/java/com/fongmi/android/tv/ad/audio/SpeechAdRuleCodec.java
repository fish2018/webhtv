package com.fongmi.android.tv.ad.audio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Strict parser for the human-readable speech-ad rules v1 format. */
public final class SpeechAdRuleCodec {

    public static final String FORMAT = "speech-ad-rules-v1";
    public static final int MAX_INPUT_BYTES = 64 * 1024;
    public static final int MAX_RULES = SpeechAdRuleSet.MAX_RULES;
    public static final long MAX_ROLL_SECONDS = SpeechAdRule.MAX_ROLL_MS / 1_000L;

    private static final char BOM = '\uFEFF';
    private static final String WILDCARD = "*";

    private SpeechAdRuleCodec() {
    }

    public static SpeechAdRuleSet parse(String text) {
        if (text == null || text.isEmpty()) return SpeechAdRuleSet.empty();
        if (text.length() > MAX_INPUT_BYTES
                || text.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw invalid(0, "document is too large");
        }
        String source = text.charAt(0) == BOM ? text.substring(1) : text;
        LinkedHashMap<String, SpeechAdRule> rules = new LinkedHashMap<>();
        String[] lines = source.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            SpeechAdRule rule = parseLine(line, i + 1);
            SpeechAdRule previous = rules.putIfAbsent(rule.id(), rule);
            if (previous != null && !previous.canonical().equals(rule.canonical())) {
                throw invalid(i + 1, "duplicate rule id");
            }
            if (rules.size() > MAX_RULES) throw invalid(i + 1, "too many rules");
        }
        return new SpeechAdRuleSet(new ArrayList<>(rules.values()));
    }

    public static SpeechAdRuleSet fromText(String text) {
        return parse(text);
    }

    public static String serialize(SpeechAdRuleSet rules) {
        if (rules == null) throw new NullPointerException("rules");
        return rules.toText();
    }

    private static SpeechAdRule parseLine(String line, int lineNumber) {
        Action action = parseAction(line, lineNumber);
        String body = action.body();
        if (body.isEmpty()) throw invalid(lineNumber, "rule body is empty");
        String[] rawSegments = body.split(">", -1);
        if (rawSegments.length == 0 || rawSegments.length > SpeechAdRule.MAX_SEGMENTS) {
            throw invalid(lineNumber, "invalid segment count");
        }
        List<SpeechAdRule.Segment> segments = new ArrayList<>(rawSegments.length);
        for (String rawSegment : rawSegments) {
            segments.add(parseSegment(rawSegment, lineNumber));
        }
        String canonical = SpeechAdRule.canonicalText(segments,
                action.preRollMs(), action.postRollMs());
        String id = "speech-" + digest(canonical).substring(0, 16);
        return new SpeechAdRule(id, segments, action.preRollMs(), action.postRollMs());
    }

    private static SpeechAdRule.Segment parseSegment(String raw, int lineNumber) {
        String source = SpeechAdRule.normalizeText(raw);
        if (source.isEmpty()) throw invalid(lineNumber, "empty segment");
        List<String> literals = new ArrayList<>();
        List<Boolean> wildcardBetween = new ArrayList<>();
        boolean leadingWildcard = source.startsWith(WILDCARD);
        boolean trailingWildcard = source.endsWith(WILDCARD);
        String[] pieces = source.split("\\*", -1);
        boolean foundLiteral = false;
        for (String piece : pieces) {
            String literal = SpeechAdRule.normalizeText(piece);
            if (literal.isEmpty()) continue;
            if (foundLiteral) wildcardBetween.add(true);
            literals.add(literal);
            foundLiteral = true;
        }
        if (literals.isEmpty()) throw invalid(lineNumber, "segment must contain text");
        try {
            return new SpeechAdRule.Segment(literals, wildcardBetween,
                    leadingWildcard, trailingWildcard);
        } catch (IllegalArgumentException error) {
            throw invalid(lineNumber, error.getMessage());
        }
    }

    private static Action parseAction(String line, int lineNumber) {
        if (line.endsWith("]")) {
            int open = line.lastIndexOf('[');
            if (open <= 0) throw invalid(lineNumber, "invalid range action");
            String inner = line.substring(open + 1, line.length() - 1);
            String[] values = inner.replace('，', ',').split(",", -1);
            if (values.length != 2) throw invalid(lineNumber, "range action needs two values");
            long pre = parseSeconds(values[0], lineNumber);
            long post = parseSeconds(values[1], lineNumber);
            String body = line.substring(0, open).trim();
            if (body.endsWith(",") || body.endsWith("，")) {
                body = body.substring(0, body.length() - 1).trim();
            }
            return new Action(body, pre * 1_000L, post * 1_000L);
        }
        int comma = Math.max(line.lastIndexOf(','), line.lastIndexOf('，'));
        if (comma <= 0 || comma == line.length() - 1) {
            throw invalid(lineNumber, "missing action suffix");
        }
        long post = parseSeconds(line.substring(comma + 1), lineNumber);
        return new Action(line.substring(0, comma).trim(), 0L, post * 1_000L);
    }

    private static long parseSeconds(String value, int lineNumber) {
        String source = value.trim();
        if (source.isEmpty()) throw invalid(lineNumber, "empty action value");
        long seconds;
        try {
            if (source.startsWith("+") || source.startsWith("-")) {
                throw new NumberFormatException();
            }
            seconds = Long.parseLong(source);
        } catch (NumberFormatException e) {
            throw invalid(lineNumber, "action value is not an integer");
        }
        if (seconds < 0L || seconds > MAX_ROLL_SECONDS) {
            throw invalid(lineNumber, "action value is out of range");
        }
        return seconds;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static IllegalArgumentException invalid(int line, String message) {
        return new IllegalArgumentException((line <= 0 ? "speech rules" : "line " + line) + ": " + message);
    }

    private record Action(String body, long preRollMs, long postRollMs) {
    }
}
