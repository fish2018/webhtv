package com.github.catvod.crawler.diagnostics;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The common boundary for untrusted legacy/native text. Structured fields also use it. */
public final class DiagnosticText {
    public static final int MAX_CHARS = 12_000;
    private static final String REDACTED = "[redacted]";
    private static final Pattern URI_TEXT = Pattern.compile("(?i)(?:[a-z][a-z0-9+.-]{1,24}:)?//[^\\s<>\"'\\\\]+|(?:file|content|magnet):[^\\s<>\"']+");
    private static final Pattern HEADER = Pattern.compile("(?im)(?:[\"']?)(?:authorization|proxy-authorization|cookie|set-cookie|referer)(?:[\"']?)\\s*[:=]\\s*[^\\r\\n]*");
    private static final Pattern SECRET = Pattern.compile("(?i)(?<![a-z0-9_.-])([\"']?(?:[a-z0-9_.-]{0,48}(?:token|password|passwd|secret|credential|cookie|authorization|license|sessionkey|api[_-]?key|access[_-]?key|share[_-]?key|username|account|email|phone|serial|url|uri|payload|body)[a-z0-9_.-]{0,48})[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;&}\\]]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[a-z0-9+/_.=~-]+");
    private static final Pattern LOCAL_PATH = Pattern.compile("/(?:storage|sdcard|data/user|data/data|mnt/media_rw)/[^\\s\"'<>]*");
    private static final Pattern ADDRESS = Pattern.compile("(?i)\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b|\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b");

    private DiagnosticText() {}

    public record Clean(String text, boolean truncated) {}

    public static Clean clean(String input) {
        if (input == null) return new Clean("", false);
        boolean truncated = input.length() > MAX_CHARS;
        String value = input.substring(0, Math.min(MAX_CHARS, input.length())).replace("\\/", "/");
        value = HEADER.matcher(value).replaceAll("sensitive-header=" + REDACTED);
        value = SECRET.matcher(value).replaceAll("$1" + REDACTED);
        value = BEARER.matcher(value).replaceAll(REDACTED);
        value = URI_TEXT.matcher(value).replaceAll("uri:" + REDACTED);
        value = LOCAL_PATH.matcher(value).replaceAll("path:" + REDACTED);
        value = ADDRESS.matcher(value).replaceAll("address:" + REDACTED);
        StringBuilder result = new StringBuilder(Math.min(MAX_CHARS, value.length()));
        for (int i = 0; i < value.length() && result.length() < MAX_CHARS; i++) {
            char c = value.charAt(i);
            if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029' || (c >= '\u202a' && c <= '\u202e')) result.append('?');
            else result.append(c);
        }
        truncated |= result.length() >= MAX_CHARS;
        if (truncated) result.append(" [truncated]");
        return new Clean(result.toString(), truncated);
    }

    /** Domain suggestions are local memory only, never part of an exported event. */
    public static List<String> origins(String input) {
        if (input == null) return List.of();
        String value = input.substring(0, Math.min(MAX_CHARS, input.length())).replace("\\/", "/");
        Matcher matcher = URI_TEXT.matcher(value);
        List<String> result = new ArrayList<>();
        while (matcher.find() && result.size() < 16) {
            try {
                String candidate = matcher.group();
                URI uri = URI.create(candidate.startsWith("//") ? "https:" + candidate : candidate);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (host != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                    result.add(scheme.toLowerCase(java.util.Locale.ROOT) + "://" + host + (uri.getPort() < 0 ? "" : ":" + uri.getPort()));
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed input is neither a diagnostic failure nor a proxy rule.
            }
        }
        return result;
    }

    public static String throwable(Throwable error) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendThrowable(error, "", 0, visited, result);
        return clean(result.toString()).text();
    }

    private static void appendThrowable(Throwable error, String relation, int depth, Set<Throwable> visited, StringBuilder out) {
        if (error == null) return;
        if (depth >= 8 || visited.size() >= 16 || out.length() >= MAX_CHARS || !visited.add(error)) {
            out.append("\n[truncated exception chain/cycle]");
            return;
        }
        out.append(relation).append(error.getClass().getName()).append(": ").append(clean(error.getMessage()).text());
        StackTraceElement[] stack = error.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 48) && out.length() < MAX_CHARS; i++) out.append("\n at ").append(stack[i]);
        if (stack.length > 48) out.append("\n[truncated stack]");
        Throwable[] suppressed = error.getSuppressed();
        for (int i = 0; i < Math.min(suppressed.length, 4); i++) appendThrowable(suppressed[i], "\nSuppressed: ", depth + 1, visited, out);
        if (suppressed.length > 4) out.append("\n[truncated suppressed]");
        appendThrowable(error.getCause(), "\nCaused by: ", depth + 1, visited, out);
    }
}
