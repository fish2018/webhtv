package com.fongmi.android.tv.web;

import com.github.catvod.crawler.SpiderDebug;

import java.net.URI;
import java.util.Locale;

/** Emits low-cardinality, secret-safe lifecycle events for the WebTheme runtime. */
final class WebThemeRuntimeDiagnostics {

    private static final String TAG = "webtheme-runtime";
    private static final int MAX_URL_LENGTH = 2048;

    enum Event {
        MANIFEST_LOAD_STARTED("manifest_load_started"),
        MANIFEST_LOAD_RESOLVED("manifest_load_resolved"),
        MANIFEST_LOAD_IGNORED("manifest_load_ignored"),
        MANIFEST_LOAD_FAILED("manifest_load_failed"),
        MANIFEST_CACHE_FALLBACK("manifest_cache_fallback"),
        MANIFEST_ROLLBACK("manifest_rollback"),
        DOCUMENT_LOAD_STARTED("document_load_started"),
        DOCUMENT_READY("document_ready"),
        DOCUMENT_RECOVERY("document_recovery"),
        FALLBACK("fallback");

        private final String key;

        Event(String key) {
            this.key = key;
        }
    }

    enum Reason {
        NONE("none"),
        STALE_OPERATION("stale_operation"),
        MANIFEST_IO("manifest_io"),
        MANIFEST_INVALID("manifest_invalid"),
        LAST_KNOWN_GOOD("last_known_good"),
        ROLLBACK("rollback"),
        PAGE_UNAVAILABLE("page_unavailable"),
        BRIDGE_UNAVAILABLE("bridge_unavailable"),
        DATA_ISOLATION_UNAVAILABLE("data_isolation_unavailable"),
        LOAD_TIMEOUT("load_timeout"),
        EMPTY_DOCUMENT("empty_document"),
        WEB_RESOURCE_ERROR("web_resource_error"),
        HTTP_ERROR("http_error"),
        RENDER_PROCESS_GONE("render_process_gone");

        private final String key;

        Reason(String key) {
            this.key = key;
        }
    }

    private WebThemeRuntimeDiagnostics() {
    }

    static void log(Event event, int operation, int generation, WebThemePage page,
            WebHomeTarget target, String url, Reason reason, int code) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log(TAG, "%s", format(event, operation, generation, page, target, url, reason, code));
    }

    static void logConsole(Object level, int line, Object source, String message) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("webhome-console", "%s", formatConsole(level, line, source, message));
    }

    static String format(Event event, int operation, int generation, WebThemePage page,
            WebHomeTarget target, String url, Reason reason, int code) {
        WebThemePage effectivePage = page != null ? page : target == null ? null : target.getPage();
        return String.format(Locale.US,
                "event=%s operation=%d generation=%d page=%s mode=%s reason=%s code=%d url=%s",
                event == null ? "unknown" : event.key,
                operation,
                generation,
                effectivePage == null ? "none" : effectivePage.getKey(),
                mode(target),
                reason == null ? Reason.NONE.key : reason.key,
                code,
                safeUrl(url));
    }

    static String formatConsole(Object level, int line, Object source, String message) {
        return String.format(Locale.US, "level=%s line=%d message_length=%d url=%s",
                consoleLevel(level), Math.max(0, line), message == null ? 0 : message.length(), safeUrl(source));
    }

    static Reason manifestFailure(Throwable failure) {
        if (failure instanceof IllegalArgumentException) return Reason.PAGE_UNAVAILABLE;
        Throwable cause = failure == null ? null : failure.getCause();
        while (cause != null) {
            if (cause instanceof IllegalArgumentException) return Reason.MANIFEST_INVALID;
            cause = cause.getCause();
        }
        return Reason.MANIFEST_IO;
    }

    static String mode(WebHomeTarget target) {
        if (target == null) return "none";
        if (!target.isGlobal()) return "site";
        String type = target.isV2() ? "v2" : target.isManifest() ? "manifest" : "v1";
        return type + (target.isRemoteGlobal() ? "_remote" : "_local");
    }

    static String safeUrl(Object value) {
        return safeUrl(value == null ? "" : value.toString());
    }

    static String safeUrl(String value) {
        String raw = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        if (raw.isEmpty()) return "";
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme();
            if (scheme != null && uri.isOpaque()) return limit(scheme, 32) + ":<redacted>";
            String authority = uri.getRawAuthority();
            if (authority != null) {
                int userInfo = authority.lastIndexOf('@');
                if (userInfo >= 0) authority = authority.substring(userInfo + 1);
            }
            StringBuilder safe = new StringBuilder();
            if (scheme != null) safe.append(scheme).append(':');
            if (authority != null) safe.append("//").append(authority);
            if (uri.getRawPath() != null) safe.append(uri.getRawPath());
            return limit(safe.toString(), MAX_URL_LENGTH);
        } catch (RuntimeException ignored) {
            int query = raw.indexOf('?');
            int fragment = raw.indexOf('#');
            int end = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
            String safe = end < 0 ? raw : raw.substring(0, end);
            int authority = safe.indexOf("://");
            if (authority >= 0) {
                int path = safe.indexOf('/', authority + 3);
                int userInfo = safe.lastIndexOf('@', path < 0 ? safe.length() - 1 : path);
                if (userInfo >= authority + 3) {
                    safe = safe.substring(0, authority + 3) + safe.substring(userInfo + 1);
                }
            }
            return limit(safe, MAX_URL_LENGTH);
        }
    }

    private static String consoleLevel(Object value) {
        String level = value == null ? "" : value.toString().toLowerCase(Locale.US);
        switch (level) {
            case "debug":
            case "error":
            case "log":
            case "tip":
            case "warning":
                return level;
            default:
                return "unknown";
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
