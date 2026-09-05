package com.fongmi.android.tv.ui.helper;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class TmdbMatchPolicy {

    private static final int SPLIT_SEASON_PENALTY = -240;
    private static final int NON_SPLIT_BONUS = 140;
    private static final int EXPLICIT_SPLIT_BONUS = 160;
    private static final Pattern PUSH_URL = Pattern.compile("(?i)^(?:https?|rtsp|rtmp|mms|magnet|ed2k|thunder|video|file):\\S+$");
    private static final Pattern PUSH_GENERIC_TITLE = Pattern.compile("(?i)^(?:online\\s*video|network\\s*video|web\\s*video|video|push|cast|\u5728\u7ebf\u89c6\u9891|\u7f51\u7edc\u89c6\u9891|\u7f51\u9875\u89c6\u9891|\u63a8\u9001|\u6295\u5c4f|\u89c6\u9891)(?:\\s*(?:[-_#.]?\\s*\\d{1,4}))?$");

    private TmdbMatchPolicy() {
    }

    public static int splitSeasonDetailScore(String sourceText, JsonObject detail) {
        boolean split = isSplitSeasonVariant(detailTitle(detail));
        if (!split) return allowsSplitSeasonVariant(sourceText) ? 0 : NON_SPLIT_BONUS;
        if (mentionsSplitSeason(sourceText)) return EXPLICIT_SPLIT_BONUS;
        return allowsSplitSeasonVariant(sourceText) ? 0 : SPLIT_SEASON_PENALTY;
    }

    public static boolean shouldAutoMatchPushTitle(String title) {
        String value = Objects.toString(title, "").trim();
        if (value.isEmpty() || PUSH_URL.matcher(value).matches() || PUSH_GENERIC_TITLE.matcher(value).matches()) return false;
        if (!value.matches(".*[\u4e00-\u9fffA-Za-z].*")) return false;
        String normalized = normalize(value);
        return normalized.length() >= 2 && !normalized.matches("(?:0*\\d{1,4})");
    }

    public static boolean isUnwantedSplitSeasonVariant(String sourceText, JsonObject detail) {
        return isSplitSeasonVariant(detailTitle(detail)) && !allowsSplitSeasonVariant(sourceText);
    }

    static boolean isSplitSeasonVariant(String text) {
        String value = normalize(text);
        return value.contains("分季");
    }

    static boolean allowsSplitSeasonVariant(String sourceText) {
        return mentionsSplitSeason(sourceText) || mentionsExplicitSeason(sourceText);
    }

    private static boolean mentionsSplitSeason(String sourceText) {
        return normalize(sourceText).contains("分季");
    }

    private static boolean mentionsExplicitSeason(String sourceText) {
        String value = Objects.toString(sourceText, "");
        return value.matches("(?is).*(第\\s*[零〇一二三四五六七八九十两0-9]+\\s*[季部]|season\\s*[0-9]{1,2}|s[0-9]{1,2}(?:[-._\\s]*e[0-9]{1,3})?).*");
    }

    private static String detailTitle(JsonObject detail) {
        return string(detail, "name") + " " + string(detail, "original_name") + " " + string(detail, "title") + " " + string(detail, "original_title");
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    private static String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }
}
