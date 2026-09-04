package androidx.media3.mpvplayer;

import java.util.Locale;
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

    static boolean allowsSynchronousProperties(Request request, boolean debugLogEnabled) {
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
