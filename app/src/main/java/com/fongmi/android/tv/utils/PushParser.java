package com.fongmi.android.tv.utils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PushParser {

    private static final Pattern PUSH_URL = Pattern.compile("(https?|thunder|magnet|ed2k|video):\\S+");
    private static final Pattern COMMON_HEADER = Pattern.compile("(?i)^(user-agent|referer|referrer|cookie|origin|authorization|range|accept|content-type)\\s*[:=].+");
    private PushParser() {
    }

    public static Parsed fromText(String text) {
        Parsed parsed = splitTitle(text);
        return new Parsed(sniffUrl(stripPushScheme(parsed.getUrl())), parsed.getTitle());
    }

    public static Parsed fromId(String id) {
        Parsed parsed = splitTitle(id);
        return new Parsed(stripPushScheme(parsed.getUrl()), parsed.getTitle());
    }

    public static Parsed of(String url, String title) {
        return new Parsed(stripPushScheme(clean(url)), clean(title));
    }

    private static Parsed splitTitle(String text) {
        String value = clean(text);
        if (isStructured(value)) return new Parsed(value, "");
        int index = value.lastIndexOf('|');
        if (index <= 0 || index >= value.length() - 1) return new Parsed(value, "");
        String url = clean(value.substring(0, index));
        String title = clean(value.substring(index + 1));
        if (url.isEmpty() || !isTitleSuffix(title)) return new Parsed(value, "");
        return new Parsed(url, title);
    }

    private static boolean isTitleSuffix(String value) {
        if (value.isEmpty()) return false;
        if (PUSH_URL.matcher(value).find()) return false;
        if (COMMON_HEADER.matcher(value).matches()) return false;
        return hasTitleText(value);
    }

    private static boolean hasTitleText(String value) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static String sniffUrl(String value) {
        if (isStructured(value)) return value;
        Matcher matcher = PUSH_URL.matcher(value);
        return matcher.find() ? matcher.group(0) : value;
    }

    private static boolean isStructured(String value) {
        return (value.startsWith("{") && value.endsWith("}")) || value.contains("$");
    }

    private static String stripPushScheme(String value) {
        return value.regionMatches(true, 0, "push://", 0, 7) ? value.substring(7) : value;
    }

    private static String clean(String value) {
        return Objects.toString(value, "").trim();
    }

    public static final class Parsed {

        private final String url;
        private final String title;

        private Parsed(String url, String title) {
            this.url = clean(url);
            this.title = clean(title);
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getId() {
            return title.isEmpty() ? url : url + "|" + title;
        }

        public String getName() {
            return title.isEmpty() ? url : title;
        }
    }
}
