package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.setting.Setting;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TMDB video metadata restricted to safe, directly playable providers.
 */
public final class TmdbVideo {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 240;
    private static final int MAX_TYPE_LENGTH = 64;
    private static final int MAX_LANGUAGE_LENGTH = 16;
    private static final int MAX_COUNTRY_LENGTH = 16;

    public enum Scope {
        TITLE,
        SEASON,
        EPISODE
    }

    private final String id;
    private final String key;
    private final String site;
    private final String name;
    private final String type;
    private final boolean official;
    private final int size;
    private final String iso6391;
    private final String iso31661;
    private final String publishedAt;
    private final Scope scope;
    private final int seasonNumber;
    private final int episodeNumber;

    private TmdbVideo(String id, String key, String site, String name, String type, boolean official, int size,
                      String iso6391, String iso31661, String publishedAt, Scope scope, int seasonNumber, int episodeNumber) {
        this.id = id;
        this.key = key;
        this.site = site;
        this.name = name;
        this.type = type;
        this.official = official;
        this.size = Math.max(0, size);
        this.iso6391 = iso6391;
        this.iso31661 = iso31661;
        this.publishedAt = publishedAt;
        this.scope = scope == null ? Scope.TITLE : scope;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
    }

    public static TmdbVideo from(JsonObject object, Scope scope, int seasonNumber, int episodeNumber) {
        if (object == null) return null;
        String site = text(object, "site");
        if (!"youtube".equalsIgnoreCase(site)) return null;
        String key = text(object, "key");
        if (!SAFE_KEY.matcher(key).matches()) return null;
        String id = limit(text(object, "id"), MAX_ID_LENGTH);
        String name = limit(text(object, "name"), MAX_NAME_LENGTH);
        String type = limit(text(object, "type"), MAX_TYPE_LENGTH);
        String language = limit(text(object, "iso_639_1"), MAX_LANGUAGE_LENGTH);
        String country = limit(text(object, "iso_3166_1"), MAX_COUNTRY_LENGTH);
        String publishedAt = limit(text(object, "published_at"), MAX_ID_LENGTH);
        int size = integer(object, "size");
        boolean official = bool(object, "official");
        return new TmdbVideo(id, key, "YouTube", name, type, official, size, language, country, publishedAt,
                scope, seasonNumber, episodeNumber);
    }

    public static List<TmdbVideo> mergeAndRank(List<TmdbVideo> values, String preferredLanguage, int limit) {
        Map<String, TmdbVideo> unique = new LinkedHashMap<>();
        if (values != null) {
            for (TmdbVideo value : values) {
                if (value == null) continue;
                String identity = value.getIdentity();
                TmdbVideo previous = unique.get(identity);
                if (previous == null || compare(value, previous, preferredLanguage) < 0) unique.put(identity, value);
            }
        }
        List<TmdbVideo> result = new ArrayList<>(unique.values());
        result.sort((first, second) -> compare(first, second, preferredLanguage));
        if (limit > 0 && result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    private static int compare(TmdbVideo first, TmdbVideo second, String preferredLanguage) {
        int result = Integer.compare(scopeRank(second.scope), scopeRank(first.scope));
        if (result != 0) return result;
        result = Boolean.compare(second.official, first.official);
        if (result != 0) return result;
        result = Integer.compare(languageRank(first.iso6391, preferredLanguage), languageRank(second.iso6391, preferredLanguage));
        if (result != 0) return result;
        result = Integer.compare(typeRank(first.type), typeRank(second.type));
        if (result != 0) return result;
        result = second.publishedAt.compareTo(first.publishedAt);
        if (result != 0) return result;
        return first.id.compareTo(second.id);
    }

    private static int scopeRank(Scope value) {
        if (value == Scope.EPISODE) return 3;
        if (value == Scope.SEASON) return 2;
        return 1;
    }

    private static int languageRank(String language, String preferredLanguage) {
        String value = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String preferred = preferredLanguage == null ? "" : preferredLanguage.trim().toLowerCase(Locale.ROOT);
        if (!preferred.isEmpty() && value.equals(preferred)) return 0;
        int separator = preferred.indexOf('-');
        String preferredRoot = separator > 0 ? preferred.substring(0, separator) : preferred;
        if (!preferredRoot.isEmpty() && value.equals(preferredRoot)) return 1;
        if ("en".equals(value)) return 2;
        return 3;
    }

    private static int typeRank(String type) {
        if (type == null) return 6;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "trailer": return 0;
            case "teaser": return 1;
            case "clip": return 2;
            case "featurette": return 3;
            case "behind the scenes": return 4;
            case "recap": return 5;
            default: return 6;
        }
    }

    private static String text(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public String getId() { return id; }
    public String getKey() { return key; }
    public String getSite() { return site; }
    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isOfficial() { return official; }
    public int getSize() { return size; }
    public String getIso6391() { return iso6391; }
    public String getIso31661() { return iso31661; }
    public String getPublishedAt() { return publishedAt; }
    public Scope getScope() { return scope; }
    public int getSeasonNumber() { return seasonNumber; }
    public int getEpisodeNumber() { return episodeNumber; }
    public String getIdentity() { return site + "|" + key; }
    public String getWatchUrl() { return "https://www.youtube.com/watch?v=" + key; }
    public String getThumbnailUrl() { return "https://i.ytimg.com/vi/" + key + "/hqdefault.jpg"; }

    public String getScopeLabel() {
        if (scope == Scope.EPISODE) return "当前集";
        if (scope == Scope.SEASON) return "当前季";
        return "全剧";
    }

    public String getDisplayType() {
        return getDisplayType(Setting.getLanguage(), Locale.getDefault());
    }

    String getDisplayType(int language, Locale systemLocale) {
        Locale locale = systemLocale == null ? Locale.getDefault() : systemLocale;
        if (language == Setting.LANGUAGE_SIMPLIFIED) locale = Locale.SIMPLIFIED_CHINESE;
        else if (language == Setting.LANGUAGE_TRADITIONAL) locale = Locale.TRADITIONAL_CHINESE;
        if (!Locale.CHINESE.getLanguage().equals(locale.getLanguage())) return type.isEmpty() ? "Video" : type;
        boolean traditional = "Hant".equalsIgnoreCase(locale.getScript())
                || "TW".equalsIgnoreCase(locale.getCountry())
                || "HK".equalsIgnoreCase(locale.getCountry())
                || "MO".equalsIgnoreCase(locale.getCountry());
        switch (type.toLowerCase(Locale.ROOT)) {
            case "trailer": return traditional ? "預告片" : "预告片";
            case "teaser": return traditional ? "前導預告" : "先导预告";
            case "clip": return "片段";
            case "featurette": return traditional ? "製作特輯" : "制作特辑";
            case "behind the scenes": return traditional ? "幕後花絮" : "幕后花絮";
            case "recap": return traditional ? "劇情回顧" : "剧情回顾";
            case "bloopers": return "NG 花絮";
            case "opening credits": return traditional ? "片頭" : "片头";
            default: return traditional ? "視頻" : "视频";
        }
    }
}
