package com.fongmi.android.tv.utils;

import java.net.URI;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure manifest-text cleaner. Network fetching and URI proxy rewriting stay outside this class. */
public final class HlsManifestCleaner {

    private static final double MAX_REMOVAL_RATIO = 0.35;
    private static final double MAX_REMOVED_DURATION_SEC = 90;
    private static final int MAX_MANIFEST_CHARS = 2 * 1024 * 1024;
    private static final int MAX_MANIFEST_LINES = 20_000;
    private static final int MAX_REGEX_COUNT = 32;
    private static final int MAX_REGEX_LENGTH = 512;

    private HlsManifestCleaner() {}

    public static Result clean(String baseUrl, String manifest, List<Rule> rules) {
        if (manifest == null || rules == null || rules.isEmpty() || !manifest.trim().startsWith("#EXTM3U")) {
            return Result.unchanged(manifest);
        }
        if (manifest.length() > MAX_MANIFEST_CHARS || lineCount(manifest) > MAX_MANIFEST_LINES) return Result.fallback(manifest);
        if (manifest.contains("#EXT-X-BYTERANGE")) return Result.fallback(manifest);
        if (manifest.contains("#EXT-X-SKIP:") || manifest.contains("#EXT-X-PART:")
                || manifest.contains("#EXT-X-PRELOAD-HINT:")) return Result.fallback(manifest);
        try {
            List<Node> nodes = parse(manifest);
            int segmentCount = 0;
            int removedCount = 0;
            double removedDurationSec = 0;
            for (Node node : nodes) {
                if (!(node instanceof Segment segment)) continue;
                segmentCount++;
                if (matches(baseUrl, segment, rules)) {
                    segment.removed = true;
                    removedCount++;
                    removedDurationSec += segment.durationSec;
                }
            }
            if (removedCount == 0) return Result.unchanged(manifest);
            if (segmentCount == 0 || removedCount == segmentCount || (double) removedCount / segmentCount > MAX_REMOVAL_RATIO
                    || removedDurationSec > MAX_REMOVED_DURATION_SEC) {
                return Result.fallback(manifest);
            }
            int mediaSequenceIncrement = 0;
            int discontinuitySequenceIncrement = 0;
            if (!manifest.contains("#EXT-X-ENDLIST")) {
                boolean retainedSeen = false;
                for (Node node : nodes) {
                    if (!(node instanceof Segment segment)) continue;
                    if (segment.removed) {
                        if (retainedSeen) return Result.fallback(manifest);
                        mediaSequenceIncrement++;
                        if (segment.discontinuityBefore) discontinuitySequenceIncrement++;
                    } else {
                        retainedSeen = true;
                    }
                }
                if (mediaSequenceIncrement > 0 && !manifest.contains("#EXT-X-MEDIA-SEQUENCE:")) return Result.fallback(manifest);
                if (discontinuitySequenceIncrement > 0 && !manifest.contains("#EXT-X-DISCONTINUITY-SEQUENCE:")) return Result.fallback(manifest);
            }
            return new Result(render(nodes, manifest.endsWith("\n"), mediaSequenceIncrement, discontinuitySequenceIncrement),
                    true, false, removedCount, removedDurationSec);
        } catch (RuntimeException e) {
            return Result.fallback(manifest);
        }
    }

    private static int lineCount(String value) {
        int count = 1;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') count++;
        return count;
    }

    private static List<Node> parse(String manifest) {
        String normalized = manifest.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<Node> nodes = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        boolean discontinuityBefore = false;
        for (String line : lines) {
            if (line.isEmpty() && pending.isEmpty()) continue;
            String trimmed = line.trim();
            if (trimmed.equals("#EXT-X-DISCONTINUITY")) discontinuityBefore = true;
            if (trimmed.startsWith("#EXTINF:")) {
                flushPending(nodes, pending);
                pending.add(line);
                continue;
            }
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                if (hasExtInf(pending)) {
                    nodes.add(new Segment(new ArrayList<>(pending), line, duration(pending), discontinuityBefore));
                    pending.clear();
                    discontinuityBefore = false;
                } else {
                    flushPending(nodes, pending);
                    nodes.add(new Line(line));
                }
            } else {
                pending.add(line);
            }
        }
        flushPending(nodes, pending);
        return nodes;
    }

    private static double duration(List<String> lines) {
        for (String line : lines) {
            String value = line.trim();
            if (!value.startsWith("#EXTINF:")) continue;
            int comma = value.indexOf(',');
            String number = value.substring(8, comma < 0 ? value.length() : comma);
            return Double.parseDouble(number);
        }
        return 0;
    }

    private static boolean hasExtInf(List<String> lines) {
        for (String line : lines) if (line.trim().startsWith("#EXTINF:")) return true;
        return false;
    }

    private static void flushPending(List<Node> nodes, List<String> pending) {
        for (String line : pending) nodes.add(new Line(line));
        pending.clear();
    }

    private static boolean matches(String baseUrl, Segment segment, List<Rule> rules) {
        URI base = URI.create(baseUrl);
        URI resolved = base.resolve(segment.uri);
        String url = resolved.toString();
        String host = resolved.getHost() == null ? "" : resolved.getHost().toLowerCase(Locale.US);
        String baseHost = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.US);
        for (Rule rule : rules) {
            boolean scopedBySuffix = !rule.playlistHostSuffixes.isEmpty() && matchesHost(baseHost, rule.playlistHostSuffixes);
            boolean scopedByPattern = !rule.playlistHostPatterns.isEmpty() && matchesPattern(baseHost, rule.playlistHostPatterns);
            if ((!rule.playlistHostSuffixes.isEmpty() || !rule.playlistHostPatterns.isEmpty()) && !scopedBySuffix && !scopedByPattern) continue;
            int signals = 0;
            if (matchesHost(host, rule.hostSuffixes)) signals++;
            if (matchesPattern(url, rule.segmentUrlPatterns)) signals++;
            if (rule.hasDurationRange() && segment.durationSec >= rule.minDuration && segment.durationSec <= rule.maxDuration) signals++;
            if (rule.requireDiscontinuity && segment.discontinuityBefore) signals++;
            if (rule.requireCrossDomain && !host.isEmpty() && !baseHost.isEmpty() && !host.equals(baseHost)) signals++;
            if (signals >= rule.minimumSignals) return true;
        }
        return false;
    }

    private static boolean matchesHost(String host, List<String> suffixes) {
        for (String suffix : suffixes) {
            String value = suffix.toLowerCase(Locale.US);
            if (host.equals(value) || host.endsWith("." + value)) return true;
        }
        return false;
    }

    private static boolean matchesPattern(String url, List<Pattern> patterns) {
        for (Pattern pattern : patterns) if (pattern.matcher(url).find()) return true;
        return false;
    }

    private static String render(List<Node> nodes, boolean trailingNewline, int mediaSequenceIncrement, int discontinuitySequenceIncrement) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node instanceof Segment segment && segment.removed) continue;
            if (node instanceof Line line && isSegmentPrefix(line.value) && prefixesRemovedSegment(nodes, i)) continue;
            for (String line : node.lines()) {
                output.append(rewriteSequence(line, mediaSequenceIncrement, discontinuitySequenceIncrement)).append('\n');
            }
        }
        if (!trailingNewline && output.length() > 0) output.setLength(output.length() - 1);
        return output.toString();
    }

    private static String rewriteSequence(String line, int mediaIncrement, int discontinuityIncrement) {
        if (mediaIncrement > 0 && line.trim().startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
            return incrementTag(line, "#EXT-X-MEDIA-SEQUENCE:", mediaIncrement);
        }
        if (discontinuityIncrement > 0 && line.trim().startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:")) {
            return incrementTag(line, "#EXT-X-DISCONTINUITY-SEQUENCE:", discontinuityIncrement);
        }
        return line;
    }

    private static String incrementTag(String line, String tag, int increment) {
        String trimmed = line.trim();
        BigInteger value = new BigInteger(trimmed.substring(tag.length()).trim());
        if (value.signum() < 0 || value.bitLength() > 64) throw new IllegalArgumentException("Invalid HLS sequence");
        BigInteger updated = value.add(BigInteger.valueOf(increment));
        if (updated.bitLength() > 64) throw new IllegalArgumentException("HLS sequence overflow");
        return tag + updated;
    }

    private static boolean isSegmentPrefix(String line) {
        String value = line.trim();
        return value.equals("#EXT-X-DISCONTINUITY") || value.startsWith("#EXT-X-PROGRAM-DATE-TIME:");
    }

    private static boolean prefixesRemovedSegment(List<Node> nodes, int index) {
        for (int i = index + 1; i < nodes.size(); i++) {
            Node next = nodes.get(i);
            if (next instanceof Segment segment) return segment.removed;
            if (!(next instanceof Line line) || (!isSegmentPrefix(line.value) && !isTransparentStateTag(line.value))) return false;
        }
        return false;
    }

    private static boolean isTransparentStateTag(String line) {
        String value = line.trim();
        return value.startsWith("#EXT-X-KEY:") || value.startsWith("#EXT-X-MAP:");
    }

    private interface Node {
        List<String> lines();
    }

    private record Line(String value) implements Node {
        @Override public List<String> lines() { return List.of(value); }
    }

    private static final class Segment implements Node {
        private final List<String> leadingLines;
        private final String uri;
        private final double durationSec;
        private final boolean discontinuityBefore;
        private boolean removed;

        private Segment(List<String> leadingLines, String uri, double durationSec, boolean discontinuityBefore) {
            this.leadingLines = leadingLines;
            this.uri = uri;
            this.durationSec = durationSec;
            this.discontinuityBefore = discontinuityBefore;
        }

        @Override public List<String> lines() {
            List<String> lines = new ArrayList<>(leadingLines);
            lines.add(uri);
            return lines;
        }
    }

    public record Result(String manifest, boolean changed, boolean fallback, int removedSegments, double removedDurationSec) {
        private static Result unchanged(String manifest) { return new Result(manifest, false, false, 0, 0); }
        private static Result fallback(String manifest) { return new Result(manifest, false, true, 0, 0); }
    }

    public static final class Rule {
        private final List<String> hostSuffixes;
        private final List<String> playlistHostSuffixes;
        private final List<Pattern> playlistHostPatterns;
        private final List<Pattern> segmentUrlPatterns;
        private final int minimumSignals;
        private final double minDuration;
        private final double maxDuration;
        private final boolean requireDiscontinuity;
        private final boolean requireCrossDomain;

        private Rule(Builder builder) {
            this.hostSuffixes = List.copyOf(builder.hostSuffixes);
            this.playlistHostSuffixes = List.copyOf(builder.playlistHostSuffixes);
            this.playlistHostPatterns = compilePatterns(builder.playlistHostPatterns);
            this.segmentUrlPatterns = compilePatterns(builder.segmentUrlPatterns);
            this.minimumSignals = Math.max(1, builder.minimumSignals);
            this.minDuration = builder.minDuration;
            this.maxDuration = builder.maxDuration;
            this.requireDiscontinuity = builder.requireDiscontinuity;
            this.requireCrossDomain = builder.requireCrossDomain;
        }

        private boolean hasDurationRange() { return !Double.isNaN(minDuration) && !Double.isNaN(maxDuration); }

        private static boolean looksDangerous(String value) {
            return value.contains(".*.*") || value.contains(".+.+") || value.matches(".*\\([^)]*[+*][^)]*\\)[+*].*");
        }

        private static List<Pattern> compilePatterns(List<String> values) {
            if (values.size() > MAX_REGEX_COUNT) throw new IllegalArgumentException("Too many HLS regex patterns");
            List<Pattern> patterns = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.length() > MAX_REGEX_LENGTH || looksDangerous(value)) {
                    throw new IllegalArgumentException("Unsafe HLS regex pattern");
                }
                patterns.add(Pattern.compile(value));
            }
            return Collections.unmodifiableList(patterns);
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private List<String> hostSuffixes = List.of();
            private List<String> playlistHostSuffixes = List.of();
            private List<String> playlistHostPatterns = List.of();
            private List<String> segmentUrlPatterns = List.of();
            private int minimumSignals = 1;
            private double minDuration = Double.NaN;
            private double maxDuration = Double.NaN;
            private boolean requireDiscontinuity;
            private boolean requireCrossDomain;

            public Builder hostSuffixes(List<String> values) { hostSuffixes = values == null ? List.of() : values; return this; }
            public Builder playlistHostSuffixes(List<String> values) { playlistHostSuffixes = values == null ? List.of() : values; return this; }
            public Builder playlistHostPatterns(List<String> values) { playlistHostPatterns = values == null ? List.of() : values; return this; }
            public Builder segmentUrlPatterns(List<String> values) { segmentUrlPatterns = values == null ? List.of() : values; return this; }
            public Builder durationRange(double min, double max) { minDuration = min; maxDuration = max; return this; }
            public Builder requireDiscontinuity(boolean value) { requireDiscontinuity = value; return this; }
            public Builder requireCrossDomain(boolean value) { requireCrossDomain = value; return this; }
            public Builder minimumSignals(int value) { minimumSignals = value; return this; }
            public Rule build() { return new Rule(this); }
        }
    }
}
