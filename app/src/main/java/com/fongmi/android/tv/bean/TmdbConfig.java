package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TmdbConfig {

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_API_BASE = "https://api.tmdb.org/3";
    private static final String DEFAULT_IMAGE_HOST = "https://images.tmdb.org";
    private static final String DEFAULT_IMAGE_BASE = "https://images.tmdb.org/t/p/w342";
    private static final String DEFAULT_BACKDROP_BASE = "https://images.tmdb.org/t/p/w780";
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    /**
     * 默认不富集 TMDB 的站点：非影视内容（音频/有声/小说/漫画/短剧/画）加上配置类站点。
     *
     * <p>统一写半角括号即可——{@link #normalizeBrackets} 会让同一条规则一并命中
     * {@code 「设」配置}、{@code 【设】配置} 这些写法，猫源用的正是全角角括号。
     *
     * <p>注意 {@code [书]} 与 {@code [小说]}、{@code [漫]} 与 {@code [漫画]} 不可互替：
     * 匹配是子串包含，{@code [书]} 命中 {@code [书]xxx} 但不命中 {@code [小说]xxx}，
     * 因为 {@code [小说]} 里 {@code [书} 后面跟的是 {@code 说} 而非 {@code ]}。
     * {@code 配置} 不带括号，纯文本子串匹配，能命中 {@code 「设」配置}、{@code [配置]xxx}。
     */
    private static final List<String> DEFAULT_DISABLED_RULES = List.of("[音]", "[听]", "[书]", "[漫]", "[短]", "[设]", "[画]", "[漫画]", "[小说]", "配置", "[配]");
    private static final Pattern TMDB_SIZE = Pattern.compile("/(?:w\\d+|h\\d+|original)$");

    @SerializedName("apiBase")
    private String apiBase;
    @SerializedName("apiKey")
    private String apiKey;
    @SerializedName(value = "apikey", alternate = {"api_key", "tmdbApiKey", "key"})
    private String apiKeyCompat;
    @SerializedName(value = "accessToken", alternate = {"token", "readAccessToken", "bearerToken"})
    private String accessToken;
    @SerializedName(value = "omdbApiKey", alternate = {"omdbKey", "imdbApiKey"})
    private String omdbApiKey;
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
    @SerializedName("disabledSites")
    private List<String> disabledSites;
    @SerializedName(value = "allowedSites", alternate = {"includeSites", "whitelistSites"})
    private List<String> allowedSites;

    public static TmdbConfig objectFrom(String json) {
        try {
            TmdbConfig config = GSON.fromJson(json, TmdbConfig.class);
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
        omdbApiKey = trimOr(omdbApiKey, "");
        language = trimOr(language, DEFAULT_LANGUAGE);
        imageBase = normalizeImageInput(trimOr(imageBase, DEFAULT_IMAGE_BASE));
        backdropBase = normalizeImageInput(trimOr(backdropBase, ""));
        if (TextUtils.isEmpty(backdropBase) && isImageHost(imageBase)) backdropBase = imageBase(imageBase, "w780");
        if (isImageHost(imageBase)) imageBase = imageBase(imageBase, "w342");
        backdropBase = trimOr(backdropBase, DEFAULT_BACKDROP_BASE);
        if (isImageHost(backdropBase) && !backdropBase.contains("/t/p/")) backdropBase = imageBase(backdropBase, "w780");
        enabledSites = cleanList(enabledSites);
        disabledSites = mergeList(cleanList(excludeKeywords), cleanList(disabledSites));
        excludeKeywords = null;
        allowedSites = cleanList(allowedSites);
        if (excludeKeywordsConfigured == null) excludeKeywordsConfigured = !disabledSites.isEmpty();
        if (!excludeKeywordsConfigured && disabledSites.isEmpty()) disabledSites = getDefaultDisabledRules();
        return this;
    }

    public static List<String> getDefaultDisabledRules() {
        return new ArrayList<>(DEFAULT_DISABLED_RULES);
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getApiHost() {
        String api = TextUtils.isEmpty(apiBase) ? DEFAULT_API_BASE : apiBase;
        api = trimTrailingSlash(api);
        if (api.endsWith("/3")) api = api.substring(0, api.length() - 2);
        return trimTrailingSlash(api);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getOmdbApiKey() {
        return omdbApiKey;
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
        if (base.endsWith("/t/p")) base = base.substring(0, base.length() - 4);
        base = trimTrailingSlash(base);
        if (isHttpUrl(base)) return base;
        String withScheme = ensureHttpScheme(base);
        return isHttpUrl(withScheme) ? withScheme : DEFAULT_IMAGE_HOST;
    }

    public List<String> getEnabledSites() {
        return enabledSites == null ? new ArrayList<>() : enabledSites;
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords == null ? new ArrayList<>() : excludeKeywords;
    }

    public List<String> getDisabledSites() {
        return disabledSites == null ? new ArrayList<>() : disabledSites;
    }

    public List<String> getAllowedSites() {
        return allowedSites == null ? new ArrayList<>() : allowedSites;
    }

    public boolean isExcludeKeywordsConfigured() {
        return Boolean.TRUE.equals(excludeKeywordsConfigured);
    }

    public boolean isReady() {
        return !TextUtils.isEmpty(getAccessToken()) || !TextUtils.isEmpty(getApiKey());
    }

    public boolean hasSiteRules() {
        return !getEnabledSites().isEmpty() || !getAllowedSites().isEmpty() || !getDisabledSites().isEmpty();
    }

    public boolean isSiteEnabled(String key, String name) {
        sanitize();
        List<String> sites = getEnabledSites();
        if (matchesExact(getDisabledSites(), key) || matchesExact(getDisabledSites(), name)) return false;
        if (matchesExact(getAllowedSites(), key) || matchesExact(getAllowedSites(), name)) return true;
        if (matchesExact(sites, key) || matchesExact(sites, name)) return true;
        if (matches(getDisabledSites(), key) || matches(getDisabledSites(), name)) return false;
        return sites.isEmpty() || matches(sites, key) || matches(sites, name);
    }

    public String toJson() {
        return GSON.toJson(sanitize());
    }

    private static String trimOr(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private static String normalizeApiBase(String value) {
        String api = ensureHttpScheme(trimTrailingSlash(value));
        if (api.endsWith("/3")) return api;
        return joinUrl(api, "3");
    }

    private static String normalizeImageInput(String value) {
        if (TextUtils.isEmpty(value)) return value;
        return ensureHttpScheme(value.trim());
    }

    private static String ensureHttpScheme(String value) {
        if (TextUtils.isEmpty(value) || isHttpUrl(value)) return value;
        if (looksLikeHost(value)) return "https://" + value.trim();
        return value;
    }

    private static boolean looksLikeHost(String value) {
        String text = trimTrailingSlash(value);
        if (TextUtils.isEmpty(text) || text.contains("://") || text.contains(" ") || text.startsWith("/")) return false;
        int slash = text.indexOf('/');
        String host = slash < 0 ? text : text.substring(0, slash);
        return host.contains(".") || host.equalsIgnoreCase("localhost") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+(:\\d+)?");
    }

    private static boolean isImageHost(String value) {
        String image = trimTrailingSlash(value);
        return image.endsWith("/t/p") || image.equals(DEFAULT_IMAGE_HOST) || image.endsWith(".tmdb.org") || isHttpUrl(image) || looksLikeHost(image);
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
        while (TMDB_SIZE.matcher(image).find()) {
            image = image.substring(0, image.lastIndexOf('/'));
            image = trimTrailingSlash(image);
        }
        return image;
    }

    private static String trimTrailingSlash(String value) {
        String text = TextUtils.isEmpty(value) ? "" : value.trim();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
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

    private static List<String> mergeList(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>();
        addAllUnique(result, first);
        addAllUnique(result, second);
        return result;
    }

    private static void addAllUnique(List<String> result, List<String> values) {
        if (values == null) return;
        for (String value : values) if (!TextUtils.isEmpty(value) && !result.contains(value)) result.add(value);
    }

    /**
     * 括号写法归一。同一个分类标记在不同源里写作 {@code [音]}、{@code 「音」}、{@code 【音】}——
     * 猫源全用全角角括号，TVBox 配置多用半角。一条规则该把这些写法都覆盖住，
     * 否则默认规则对猫源的 57 个站点一条也匹配不上。
     */
    private static String normalizeBrackets(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value
                .replace('「', '[').replace('」', ']')
                .replace('【', '[').replace('】', ']')
                .replace('〔', '[').replace('〕', ']')
                .replace('［', '[').replace('］', ']');
    }

    private static boolean matches(List<String> rules, String value) {
        if (rules == null || TextUtils.isEmpty(value)) return false;
        String target = normalizeBrackets(value).toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            if (target.contains(normalizeBrackets(rule.trim()).toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean matchesExact(List<String> rules, String value) {
        if (rules == null || TextUtils.isEmpty(value)) return false;
        String target = normalizeBrackets(value).trim();
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            if (target.equalsIgnoreCase(normalizeBrackets(rule.trim()))) return true;
        }
        return false;
    }

    private static boolean isAccessToken(String value) {
        return value != null && value.trim().split("\\.").length >= 3;
    }
}
