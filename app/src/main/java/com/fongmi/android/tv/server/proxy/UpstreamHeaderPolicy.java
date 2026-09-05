package com.fongmi.android.tv.server.proxy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UpstreamHeaderPolicy {

    private static final Map<String, String> ALLOWED_HEADERS = Map.of(
            "user-agent", "User-Agent",
            "cookie", "Cookie",
            "referer", "Referer",
            "origin", "Origin",
            "authorization", "Authorization",
            "accept", "Accept",
            "accept-language", "Accept-Language");

    private static final Set<String> CROSS_ORIGIN_SENSITIVE_HEADERS = Set.of(
            "Cookie",
            "Authorization");

    private UpstreamHeaderPolicy() {
    }

    public static Map<String, String> sanitize(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String canonicalName = ALLOWED_HEADERS.get(entry.getKey().trim().toLowerCase(Locale.ROOT));
            if (canonicalName == null) continue;
            String value = entry.getValue().trim();
            if (value.isEmpty() || containsForbiddenControl(value)) continue;
            result.put(canonicalName, value);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, String> sanitizeForRedirect(
            Map<String, String> input,
            boolean crossOrigin) {
        Map<String, String> sanitized = sanitize(input);
        if (!crossOrigin || sanitized.isEmpty()) return sanitized;
        Map<String, String> result = new LinkedHashMap<>(sanitized);
        for (String name : CROSS_ORIGIN_SENSITIVE_HEADERS) result.remove(name);
        return Collections.unmodifiableMap(result);
    }

    private static boolean containsForbiddenControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == 0 || character == 10 || character == 13) return true;
        }
        return false;
    }
}