package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure language policy for TMDB display fields. */
public final class TmdbLanguagePolicy {

    public static final String DEFAULT_LANGUAGE = "zh-CN";

    public record TranslatedValue(String value, String language, boolean exact) {
        public static final TranslatedValue EMPTY = new TranslatedValue("", "", false);

        public boolean isEmpty() {
            return TextUtils.isEmpty(value);
        }
    }

    private TmdbLanguagePolicy() {
    }

    public static String requestLanguage(@Nullable TmdbConfig config) {
        return normalize(config == null ? null : config.getLanguage());
    }

    public static String normalize(@Nullable String language) {
        String value = language == null ? "" : language.trim();
        if (TextUtils.isEmpty(value)) return DEFAULT_LANGUAGE;
        String lower = value.toLowerCase(Locale.ROOT);
        if ("zh-hans".equals(lower) || "zh-hans-cn".equals(lower) || "zh-cn".equals(lower) || "zh-sg".equals(lower) || "zh-my".equals(lower)) return "zh-CN";
        if ("zh-hant".equals(lower) || "zh-hant-tw".equals(lower) || "zh-tw".equals(lower) || "zh-hk".equals(lower) || "zh-mo".equals(lower)) return "zh-TW";
        String[] parts = lower.split("[-_]", 2);
        if (parts.length == 2) return parts[0] + "-" + parts[1].toUpperCase(Locale.ROOT);
        return lower.toUpperCase(Locale.ROOT);
    }

    public static String root(@Nullable String language) {
        String normalized = normalize(language);
        int separator = normalized.indexOf('-');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    public static boolean isChinese(@Nullable String language) {
        return "ZH".equals(root(language));
    }

    public static boolean matchesTarget(@Nullable String target, @Nullable String candidateLanguage) {
        if (TextUtils.isEmpty(target)) return TextUtils.isEmpty(candidateLanguage);
        String expected = normalize(target);
        String candidate = normalize(candidateLanguage);
        if (TextUtils.isEmpty(candidate)) return false;
        // zh and en are root-only languages and may satisfy zh-CN/en-US snapshots.
        // Regional variants such as zh-TW must not satisfy zh-CN; they remain a
        // lower-priority fallback in bestValue rather than a compatible identity.
        return expected.equalsIgnoreCase(candidate) || candidate.equalsIgnoreCase(root(expected));
    }

    public static boolean isDisplayLanguageCompatible(@Nullable String target, @Nullable String payloadLanguage) {
        return matchesTarget(target, payloadLanguage);
    }

    public static boolean looksTargetLanguage(@Nullable String target, @Nullable String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return false;
        if (!isChinese(target)) return true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x3400 && ch <= 0x9FFF || ch >= 0xF900 && ch <= 0xFAFF || ch >= 0x20000 && ch <= 0x3FFFF) return true;
        }
        return false;
    }

    public static TranslatedValue bestValue(@Nullable String primary, @Nullable JsonArray translations,
                                            @NonNull String valueKey, @Nullable String targetLanguage) {
        String target = normalize(targetLanguage);
        Map<Integer, TranslatedValue> ranked = new LinkedHashMap<>();
        for (Candidate candidate : translationCandidates(translations, valueKey)) {
            int score = score(target, candidate.language());
            if (score >= 0) ranked.putIfAbsent(score, new TranslatedValue(candidate.value(), candidate.language(), score <= 2));
        }
        if (!TextUtils.isEmpty(primary)) {
            boolean primaryMatches = !isChinese(target) || looksTargetLanguage(target, primary);
            int score = primaryMatches ? 0 : 4;
            ranked.putIfAbsent(score, new TranslatedValue(primary.trim(), target, primaryMatches));
        }
        for (int score = 0; score <= 4; score++) {
            TranslatedValue value = ranked.get(score);
            if (value != null && !value.isEmpty()) return value;
        }
        return TranslatedValue.EMPTY;
    }

    public static String bestDisplayValue(@Nullable String primary, @Nullable JsonArray translations,
                                          @NonNull String valueKey, @Nullable String targetLanguage) {
        return bestValue(primary, translations, valueKey, targetLanguage).value();
    }

    private static List<Candidate> translationCandidates(@Nullable JsonArray translations, String valueKey) {
        List<Candidate> result = new ArrayList<>();
        if (translations == null) return result;
        for (JsonElement element : translations) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject translation = element.getAsJsonObject();
            JsonObject data = translation.has("data") && translation.get("data").isJsonObject() ? translation.getAsJsonObject("data") : null;
            String value = string(data, valueKey);
            if (TextUtils.isEmpty(value)) continue;
            String language = firstNonEmpty(string(translation, "iso_639_1"), string(translation, "language"));
            if (TextUtils.isEmpty(language)) continue;
            String country = string(translation, "iso_3166_1");
            String full = TextUtils.isEmpty(country) ? language : language + "-" + country;
            result.add(new Candidate(value.trim(), full));
        }
        return result;
    }

    private static int score(String target, String candidate) {
        String expected = normalize(target);
        String actual = normalize(candidate);
        if (expected.equalsIgnoreCase(actual)) return 0;
        String expectedRoot = root(expected);
        String actualRoot = root(actual);
        if (expectedRoot.equalsIgnoreCase(actualRoot)) return 1;
        if (isChinese(expected) && isChinese(actual)) return 2;
        if ("EN".equalsIgnoreCase(actualRoot)) return 4;
        if (!TextUtils.isEmpty(actualRoot)) return 3;
        return -1;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString()) return "";
        String value = object.get(key).getAsString();
        return value == null ? "" : value.trim();
    }

    private record Candidate(String value, String language) {
    }
}
