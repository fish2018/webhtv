package com.fongmi.android.tv.server.proxy;

import java.net.URI;
import java.util.Locale;

public final class ProxyPlaybackPolicy {

    private ProxyPlaybackPolicy() {
    }

    public static boolean shouldProxy(boolean enabled, String url, String format) {
        if (!enabled || url == null || url.isBlank()) return false;
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return false;
        if (host == null || isLoopback(host)) return false;

        String formatToken = format == null ? "" : format.toLowerCase(Locale.US);
        String urlToken = url.toLowerCase(Locale.US);
        return !isAdaptiveManifest(formatToken) && !isAdaptiveManifest(urlToken);
    }

    private static boolean isAdaptiveManifest(String value) {
        return value.equals("hls")
                || value.contains(".m3u")
                || value.contains("m3u8")
                || value.contains("mpegurl")
                || value.contains(".mpd")
                || value.equals("mpd")
                || value.equals("dash")
                || value.contains("dash+xml")
                || value.contains("format=hls")
                || value.contains("type=m3u8")
                || value.contains("format=dash")
                || value.contains("type=mpd");
    }

    private static boolean isLoopback(String host) {
        String normalized = host.trim().toLowerCase(Locale.US);
        if (normalized.startsWith("[") && normalized.endsWith("]")) normalized = normalized.substring(1, normalized.length() - 1);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }
}