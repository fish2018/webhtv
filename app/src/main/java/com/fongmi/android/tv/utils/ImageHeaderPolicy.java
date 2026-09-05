package com.fongmi.android.tv.utils;

import com.google.common.net.HttpHeaders;

import java.util.Locale;

import okhttp3.HttpUrl;

public class ImageHeaderPolicy {

    static final String DOUBAN_IMAGE_REFERER = "https://movie.douban.com";

    private ImageHeaderPolicy() {
    }

    public static String doubanImageReferer(String url, boolean hasReferer) {
        if (hasReferer) return "";
        return isDoubanImageHost(host(url)) ? DOUBAN_IMAGE_REFERER : "";
    }

    public static boolean isReferer(String name) {
        return name != null && HttpHeaders.REFERER.equalsIgnoreCase(name.trim());
    }

    private static boolean isDoubanImageHost(String host) {
        return host != null && host.matches("img\\d+\\.doubanio\\.com");
    }

    private static String host(String url) {
        String value = stripDirectives(url);
        if (value.isEmpty()) return "";
        HttpUrl parsed = HttpUrl.parse(value);
        if (parsed == null && value.startsWith("//")) parsed = HttpUrl.parse("https:" + value);
        return parsed == null ? "" : parsed.host().toLowerCase(Locale.ROOT);
    }

    private static String stripDirectives(String url) {
        if (url == null) return "";
        String value = url.trim();
        int marker = firstDirective(value);
        return marker < 0 ? value : value.substring(0, marker);
    }

    private static int firstDirective(String value) {
        int marker = -1;
        marker = min(marker, value.indexOf("@Headers="));
        marker = min(marker, value.indexOf("@Cookie="));
        marker = min(marker, value.indexOf("@Referer="));
        marker = min(marker, value.indexOf("@User-Agent="));
        return marker;
    }

    private static int min(int current, int candidate) {
        if (candidate < 0) return current;
        return current < 0 ? candidate : Math.min(current, candidate);
    }
}
