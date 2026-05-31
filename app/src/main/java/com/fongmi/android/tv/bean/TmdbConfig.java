package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TmdbConfig {

    private static final String DEFAULT_API_BASE = "https://api.tmdb.org/3";
    private static final String DEFAULT_IMAGE_HOST = "https://image.tmdb.org";
    private static final String DEFAULT_IMAGE_BASE = "https://image.tmdb.org/t/p/w342";
    private static final String DEFAULT_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780";
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    private static final List<String> DEFAULT_EXCLUDE_KEYWORDS = List.of("[\u97f3]", "[\u542c]", "[\u4e66]", "[\u6f2b]", "[\u77ed]");

    @SerializedName("apiBase")
    private String apiBase;
    @SerializedName("apiKey")
    private String apiKey;
    @SerializedName(value = "apikey", alternate = {"api_key", "tmdbApiKey", "key"})
    private String apiKeyCompat;
    @SerializedName(value = "accessToken", alternate = {"token", "readAccessToken", "bearerToken"})
    private String accessToken;
    @SerializedName("language")
    private String language;
    @SerializedName("imageBase")
    private String imageBase;
    @SerializedName("backdropBase")
    private String backdropBase;
    @SerializedName(value = "enabledSites", alternate = {"siteKeys", "sites", "matchSites"})
    private List<String> enabledSites;
    @SerializedName(value = "excludeKeywords", alternate = {"exclude", "blockedKeywords", "skipKeywords"})
    private List<String> excludeKeywords;
    @SerializedName("excludeKeywordsConfigured")
    private Boolean excludeKeywordsConfigured;

    public static TmdbConfig objectFrom(String json) {
        try {
            TmdbConfig config = App.gson().fromJson(json, TmdbConfig.class);
            return config == null ? new TmdbConfig().sanitize() : config.sanitize();
        } catch (Throwable e) {
            return new TmdbConfig().sanitize();
        }
    }

    public TmdbConfig sanitize() {
        apiBase = normalizeApiBase(trimOr(apiBase, DEFAULT_API_BASE));
        apiKey = trimOr(apiKey, trimOr(apiKeyCompat, ""));
        apiKeyCompat = apiKey;
        accessToken = trimOr(accessToken, "");
        if (!TextUtils.isEmpty(accessToken) && accessToken.equals(apiKey) && !isAccessToken(accessToken)) accessToken = "";
        language = trimOr(language, DEFAULT_LANGUAGE);
        imageBase = trimOr(imageBase, DEFAULT_IMAGE_BASE);
        backdropBase = trimOr(backdropBase, "");
        if (TextUtils.isEmpty(backdropBase) && isImageHost(imageBase)) backdropBase = imageBase(imageBase, "w780");
        if (isImageHost(imageBase)) imageBase = imageBase(imageBase, "w342");
        backdropBase = trimOr(backdropBase, DEFAULT_BACKDROP_BASE);
        enabledSites = cleanList(enabledSites);
        excludeKeywords = cleanList(excludeKeywords);
        if (excludeKeywordsConfigured == null) excludeKeywordsConfigured = !excludeKeywords.isEmpty();
        if (!excludeKeywordsConfigured && excludeKeywords.isEmpty()) excludeKeywords = new ArrayList<>(DEFAULT_EXCLUDE_KEYWORDS);
        return this;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getLanguage() {
        return language;
    }

    public String getImageBase() {
        return imageBase;
    }

    public String getBackdropBase() {
        return backdropBase;
    }

    public String getImageHost() {
        String base = TextUtils.isEmpty(imageBase) ? DEFAULT_IMAGE_BASE : imageBase;
        base = stripImageSize(base);
        if (base.endsWith("/t/p")) return base.substring(0, base.length() - 4);
        return DEFAULT_IMAGE_HOST;
    }

    public List<String> getEnabledSites() {
        return enabledSites == null ? new ArrayList<>() : enabledSites;
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords == null ? new ArrayList<>() : excludeKeywords;
    }

    public boolean isExcludeKeywordsConfigured() {
        return Boolean.TRUE.equals(excludeKeywordsConfigured);
    }

    public boolean isReady() {
        return !TextUtils.isEmpty(getAccessToken()) || !TextUtils.isEmpty(getApiKey());
    }

    public boolean hasSiteRules() {
        return !getEnabledSites().isEmpty() || !getExcludeKeywords().isEmpty();
    }

    public boolean isSiteEnabled(String key, String name) {
        sanitize();
        if (matches(getExcludeKeywords(), key) || matches(getExcludeKeywords(), name)) return false;
        List<String> sites = getEnabledSites();
        if (sites.isEmpty()) return true;
        return matches(sites, key) || matches(sites, name);
    }

    public String toJson() {
        return App.gson().toJson(sanitize());
    }

    private static String trimOr(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private static String normalizeApiBase(String value) {
        String api = trimTrailingSlash(value);
        if (api.endsWith("/3")) return api;
        return joinUrl(api, "3");
    }

    private static boolean isImageHost(String value) {
        String image = trimTrailingSlash(value);
        return image.endsWith("/t/p") || image.equals(DEFAULT_IMAGE_HOST) || image.endsWith(".tmdb.org");
    }

    private static String imageBase(String value, String size) {
        String image = stripImageSize(value);
        if (image.endsWith("/t/p")) return joinUrl(image, size);
        return joinUrl(joinUrl(image, "t/p"), size);
    }

    private static String joinUrl(String base, String path) {
        return trimTrailingSlash(base) + "/" + path;
    }

    private static String stripImageSize(String value) {
        String image = trimTrailingSlash(value);
        if (image.endsWith("/w342") || image.endsWith("/w500") || image.endsWith("/w780")) return image.substring(0, image.lastIndexOf('/'));
        if (image.endsWith("/original")) return image.substring(0, image.length() - 9);
        return image;
    }

    private static String trimTrailingSlash(String value) {
        String text = TextUtils.isEmpty(value) ? "" : value.trim();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static List<String> cleanList(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            String item = value.trim();
            if (!item.isEmpty() && !result.contains(item)) result.add(item);
        }
        return result;
    }

    private static boolean matches(List<String> rules, String value) {
        if (rules == null || TextUtils.isEmpty(value)) return false;
        String target = value.toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            if (target.contains(rule.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean isAccessToken(String value) {
        return value != null && value.trim().split("\\.").length >= 3;
    }
}
