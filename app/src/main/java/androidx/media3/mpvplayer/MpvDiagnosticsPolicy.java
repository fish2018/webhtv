package androidx.media3.mpvplayer;

import java.util.Locale;
import java.util.Arrays;
import java.util.regex.Pattern;

final class MpvDiagnosticsPolicy {

    enum Request {
        PLAYBACK,
        PANEL,
        DEBUG_LOG,
        ERROR_MINIMAL,
        ERROR_DETAILED
    }

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp)://[^\\s\\]\\[\\\"'<>]+");
    private static final Pattern SENSITIVE_HEADER = Pattern.compile("(?i)\\b(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api-key)\\s*[:=]\\s*(?:bearer\\s+)?[^\\s,;]+");

    private MpvDiagnosticsPolicy() {
    }

    static boolean includedByLogLevel(String prefix, int level, String settings) {
        int threshold = 30, specificity = -1;
        if (settings != null) for (String entry : settings.split(",")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2) continue;
            String component = pair[0].trim();
            int rank = "all".equals(component) ? 0 : prefix != null
                    && (prefix.equals(component) || prefix.startsWith(component + "/")) ? component.length() : -1;
            if (rank < 0 || rank < specificity) continue;
            threshold = switch (pair[1].trim()) {
                case "no" -> 0; case "fatal" -> 10; case "error" -> 20; case "warn" -> 30;
                case "info", "status" -> 40; case "v" -> 50; case "debug" -> 60; case "trace" -> 70;
                default -> threshold;
            };
            specificity = rank;
        }
        return level <= threshold;
    }

    static String diagnosticLogLevel(String base) {
        return diagnosticLogLevel(base, com.github.catvod.crawler.diagnostics.DiagnosticCategories.ALL, false);
    }

    static String diagnosticLogLevel(String base, int categories, boolean deep) {
        StringBuilder result = new StringBuilder(base == null ? "all=warn" : base);
        for (String component : new String[]{"vd", "ffmpeg/video", "ffmpeg/audio", "vo", "ao", "cplayer", "demux", "sub"}) {
            com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category category = switch (component) {
                case "vd", "ffmpeg/video", "vo" -> com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.VIDEO;
                case "ffmpeg/audio", "ao" -> com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.AUDIO;
                case "demux" -> com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.NETWORK;
                case "sub" -> com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.SUBTITLE;
                default -> com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.SYSTEM;
            };
            if (com.github.catvod.crawler.diagnostics.DiagnosticCategories.accepts(categories, category)
                    && !includedByLogLevel(component, deep ? 60 : 40, base)) result.append(',').append(component).append(deep ? "=debug" : "=info");
        }
        return result.toString();
    }

    static boolean allowsSynchronousProperties(Request request, boolean debugLogEnabled) {
        if (request == null) return false;
        return switch (request) {
            case PANEL, PLAYBACK, ERROR_MINIMAL -> false;
            case DEBUG_LOG, ERROR_DETAILED -> debugLogEnabled;
        };
    }

    static boolean allowsDetailedDiagnostics(Request request, boolean debugLogEnabled) {
        if (request == null) return false;
        return switch (request) {
            case PANEL, PLAYBACK, ERROR_MINIMAL -> false;
            case DEBUG_LOG, ERROR_DETAILED -> debugLogEnabled;
        };
    }

    static String sourceSummary(String source) {
        String value = source == null ? "" : source.trim();
        String scheme = scheme(value);
        return "scheme=" + (scheme.isEmpty() ? "-" : scheme) + " urlLen=" + value.length();
    }

    static String redactSensitive(String text) {
        if (text == null || text.isEmpty()) return "";
        String safe = URL.matcher(text).replaceAll("<url>");
        return SENSITIVE_HEADER.matcher(safe).replaceAll("$1=<redacted>");
    }

    /** Persist startup/failure evidence without waiting for the Android main queue. */
    static boolean isFatalFelLog(int level, String text) {
        return level > 0 && level <= 20 && text != null
                && text.trim().startsWith("WebHTV FEL fatal:");
    }

    static boolean shouldLogNativeImmediately(int level, String line) {
        // mpv: fatal=10, error=20, warn=30. In particular "failing hardware
        // decode" must not disappear just because it doesn't contain "failed".
        return line != null && !line.isEmpty()
                && (level > 0 && level <= 30 || shouldLogNativeImmediately(line));
    }

    static final int FEL_PERFORMANCE_KINDS = 19;

    /** Only native single-line measurements may bypass playback-state processing. */
    static int felPerformanceKind(String prefix, int level, String text) {
        if (level != 40 || prefix == null || text == null) return -1;
        String value = text.trim();
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return -1;
        if (prefix.equals("vo/gpu-next/aimagereader")) {
            if (value.startsWith("WebHTV FEL perf:")) return 0;
            if (value.startsWith("WebHTV FEL perf stages:")) return 1;
            if (value.startsWith("WebHTV FEL GPU init:")) return 3;
            if (value.startsWith("WebHTV FEL map cost:")) return 7;
            if (value.startsWith("WebHTV FEL GPU pool:")) return 8;
            if (value.startsWith("WebHTV FEL reuse:")) return 11;
            if (value.startsWith("WebHTV FEL api perf:")) return 12;
            if (value.startsWith("WebHTV FEL frame order:")) return 13;
            if (value.startsWith("WebHTV FEL api slow:")) return 14;
            if (value.startsWith("WebHTV FEL descriptors:")) return 15;
            if (value.startsWith("WebHTV FEL wait sample:")) return 17;
            if (value.startsWith("WebHTV FEL bind probe:")) return 18;
        } else if (prefix.equals("vo/gpu-next")) {
            if (value.startsWith("WebHTV FEL render perf:")) return 2;
            if (value.startsWith("WebHTV FEL renderer init:")) return 16;
        } else if (prefix.equals("vd")) {
            if (value.startsWith("WebHTV FEL decoder threads:")) return 4;
            if (value.startsWith("WebHTV FEL decoder cost:")) return 5;
            if (value.startsWith("WebHTV FEL producer handoff:")) return 6;
            if (value.startsWith("WebHTV FEL decoder queue:")) return 10;
        } else if (prefix.equals("enhancement_pair")) {
            if (value.startsWith("WebHTV FEL stats:")) return 9;
        }
        return -1;
    }

    static final class NativeLogWindow {
        private long startMs = -1;
        private int count;
        private int suppressed;
        private final long[] performanceStartMs = new long[FEL_PERFORMANCE_KINDS];
        private final int[] performanceCount = new int[FEL_PERFORMANCE_KINDS];
        private int performanceSuppressed;

        NativeLogWindow() {
            Arrays.fill(performanceStartMs, -1);
        }

        boolean allowPerformance(long nowMs, int kind) {
            if (kind < 0 || kind >= FEL_PERFORMANCE_KINDS) return false;
            if (performanceStartMs[kind] < 0 || nowMs - performanceStartMs[kind] >= 5000
                    || nowMs < performanceStartMs[kind]) {
                performanceStartMs[kind] = nowMs;
                performanceCount[kind] = 0;
            }
            if (performanceCount[kind] < 8) {
                performanceCount[kind]++;
                return true;
            }
            performanceSuppressed++;
            return false;
        }

        int takePerformanceSuppressed() {
            int result = performanceSuppressed;
            performanceSuppressed = 0;
            return result;
        }

        boolean allow(long nowMs, String line) {
            if (startMs < 0 || nowMs - startMs >= 5000 || nowMs < startMs) {
                startMs = nowMs;
                count = 0;
            }
            if (++count <= 32 || line.contains("WebHTV FEL fatal:")) return true;
            suppressed++;
            return false;
        }

        int takeSuppressed() {
            int result = suppressed;
            suppressed = 0;
            return result;
        }
    }

    static boolean shouldLogNativeImmediately(String line) {
        if (line == null || line.isEmpty()) return false;
        String lower = line.toLowerCase(Locale.US);
        return lower.contains("webhtv android fel:")
                || lower.contains("webhtv fel ")
                || lower.contains("dolby vision")
                || lower.contains("dovi")
                || lower.contains("nlq")
                || lower.contains("mediacodec started successfully")
                || lower.contains("using hardware decoding")
                || lower.contains("using software decoding")
                || lower.contains("decoder format:")
                || lower.contains("device name:")
                || lower.contains("vo: [gpu-next]")
                || lower.contains("error")
                || lower.contains("failed")
                || lower.contains("invalid");
    }

    private static String scheme(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0) return "";
        for (int i = 0; i < colon; i++) {
            char c = value.charAt(i);
            if (i == 0 && !Character.isLetter(c)) return "";
            if (i > 0 && !Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.') return "";
        }
        return value.substring(0, colon).toLowerCase(Locale.US);
    }
}
