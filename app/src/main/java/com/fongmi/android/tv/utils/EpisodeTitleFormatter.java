package com.fongmi.android.tv.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeTitleFormatter {

    private static final Pattern FILE_SIZE = Pattern.compile("(?i)([\\[\\(（【]\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s*(B|KB|MB|M|GB|G|TB|T|PB)(?:\\s*([\\]\\)）】])|(?=$|[\\s._-]))");

    private EpisodeTitleFormatter() {
    }

    public static String formatTmdbTitle(int number, String tmdbTitle) {
        if (!isEmpty(tmdbTitle)) return number > 0 ? number + ". " + tmdbTitle : tmdbTitle;
        if (number > 0) return "第" + number + "集";
        return "";
    }

    public static String formatTmdbTitle(String label, String sourceName, String tmdbTitle) {
        String title = isEmpty(label) ? "" : label;
        if (!isEmpty(tmdbTitle) && !equals(label, tmdbTitle) && !equals(sourceName, tmdbTitle)) {
            title = isEmpty(title) ? tmdbTitle : title + ". " + tmdbTitle;
        }
        return title;
    }

    public static String formatTmdbTitle(String label, String sourceName, String tmdbTitle, boolean showScraped) {
        if (showScraped) return formatTmdbTitle(label, sourceName, tmdbTitle);
        // 原文件名模式：优先展示源文件名，缺失时回退到 label
        return isEmpty(sourceName) ? (isEmpty(label) ? "" : label) : sourceName;
    }

    public static String withSourceFileSize(String sourceName, String title, boolean includeFileSize) {
        if (isEmpty(title) || !includeFileSize) return isEmpty(title) ? "" : title;
        String fileSize = extractFileSize(sourceName);
        if (isEmpty(fileSize) || containsFileSize(title)) return title;
        return fileSize + " " + title;
    }

    public static String buildPlaybackTitle(String name, String episode) {
        name = safe(name);
        episode = safe(episode);
        if (isEmpty(episode) || equals(name, episode)) return name;
        return isEmpty(name) ? episode : name + " " + episode;
    }

    public static String extractFileSize(String value) {
        if (isEmpty(value)) return "";
        Matcher matcher = FILE_SIZE.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (isEmpty(token)) continue;
            boolean hasOpen = !isEmpty(matcher.group(1));
            boolean hasClose = !isEmpty(matcher.group(4));
            if (hasOpen != hasClose) continue;
            return token.trim().replaceAll("\\s+", "");
        }
        return "";
    }

    public static boolean containsFileSize(String value) {
        return !isEmpty(extractFileSize(value));
    }

    public static String removeFileSizes(String value) {
        if (isEmpty(value)) return "";
        Matcher matcher = FILE_SIZE.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        int last = 0;
        boolean removed = false;
        while (matcher.find()) {
            boolean hasOpen = !isEmpty(matcher.group(1));
            boolean hasClose = !isEmpty(matcher.group(4));
            if (hasOpen != hasClose) continue;
            result.append(value, last, matcher.start());
            last = matcher.end();
            removed = true;
        }
        if (!removed) return value;
        result.append(value, last, value.length());
        return result.toString().replaceAll("\\s+", " ").replaceAll("\\s+(?=\\.[A-Za-z0-9]{1,8}(?:$|[\\s?#]))", "").trim();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean equals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
