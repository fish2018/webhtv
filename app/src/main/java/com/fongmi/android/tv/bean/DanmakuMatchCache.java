package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DanmakuMatchCache {

    private static final Gson GSON = new Gson();
    private Map<String, Entry> items;

    public static DanmakuMatchCache objectFrom(String json) {
        try {
            DanmakuMatchCache cache = GSON.fromJson(json, DanmakuMatchCache.class);
            return cache == null ? new DanmakuMatchCache() : cache;
        } catch (Throwable e) {
            return new DanmakuMatchCache();
        }
    }

    public DanmakuMatchCache() {
        this.items = new HashMap<>();
    }

    public Danmaku find(String siteKey, String vodId, String episodeName) {
        Entry entry = getItems().get(key(siteKey, vodId, episodeName));
        return entry == null ? null : entry.toDanmaku();
    }

    public MediaTitleLearningExample put(String siteKey, String vodId, String episodeName, String searchTitle, String rawTitle, Danmaku item) {
        if (isBlank(siteKey) || isBlank(vodId) || item == null || item.isEmpty()) return null;
        Entry entry = Entry.from(searchTitle, rawTitle, item);
        if (entry == null) return null;
        getItems().put(key(siteKey, vodId, episodeName), entry);
        return entry.toLearningExample().identity(siteKey, vodId);
    }

    public List<MediaTitleLearningExample> learningExamples(String ruleTitle) {
        List<MediaTitleLearningExample> result = new ArrayList<>();
        String normalized = normalize(ruleTitle);
        for (Entry entry : getItems().values()) {
            if (entry == null || isBlank(entry.expectedTitle)) continue;
            if (!normalized.isEmpty() && !normalize(entry.ruleTitle).equals(normalized) && !normalize(entry.rawTitle).contains(normalized)) continue;
            result.add(entry.toLearningExample());
        }
        return result;
    }

    public Map<String, Entry> getItems() {
        if (items == null) items = new HashMap<>();
        return items;
    }

    private String key(String siteKey, String vodId, String episodeName) {
        return clean(siteKey) + "@@@" + clean(vodId) + "@@@" + normalizedEpisodeKey(episodeName);
    }

    private static String normalizedEpisodeKey(String episodeName) {
        int number = new MediaTitleParser().episodeNumber(episodeName);
        return number > 0 ? "e" + number : normalize(episodeName);
    }

    private static String normalize(String text) {
        return clean(text).replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Entry {

        private String name;
        private String url;
        private String sourceName;
        private String searchTitle;
        private String rawTitle;
        private String ruleTitle;
        private String expectedTitle;
        private long updatedAt;

        static Entry from(String searchTitle, String rawTitle, Danmaku item) {
            String url = item.getUrl();
            if (isBlank(url)) return null;
            MediaTitleParser parser = new MediaTitleParser();
            Entry entry = new Entry();
            entry.name = item.getName();
            entry.url = url;
            entry.sourceName = safeSourceName(item);
            entry.searchTitle = clean(searchTitle);
            entry.rawTitle = clean(rawTitle);
            entry.ruleTitle = parser.cleanTitle(first(searchTitle, rawTitle));
            entry.expectedTitle = parser.cleanTitle(displayTitle(item.getName()));
            entry.updatedAt = System.currentTimeMillis();
            return entry;
        }

        Danmaku toDanmaku() {
            Danmaku item = Danmaku.from(url);
            item.setName(name);
            return item;
        }

        MediaTitleLearningExample toLearningExample() {
            return MediaTitleLearningExample.manual(rawTitle, ruleTitle, expectedTitle, "tv", 0, -1, MediaTitleLearningExample.SOURCE_DANMAKU_MANUAL);
        }

        private static String first(String... values) {
            for (String value : values) if (!isBlank(value)) return value;
            return "";
        }

        private static String safeSourceName(Danmaku item) {
            try {
                return item.getSourceName();
            } catch (Throwable e) {
                return "";
            }
        }

        private static String displayTitle(String name) {
            String value = clean(name).replaceAll("(?i)https?://\\S+", " ");
            int from = value.toLowerCase(Locale.ROOT).indexOf(" from ");
            int dash = value.indexOf(" - ");
            int cut = from >= 0 && dash >= 0 ? Math.min(from, dash) : Math.max(from, dash);
            if (cut >= 0) value = value.substring(0, cut);
            value = value.replaceAll("[【\\[]\\s*(?:19|20)\\d{2}\\s*[】\\]]", " ");
            value = value.replaceAll("[（(]\\s*(?:19|20)\\d{2}\\s*[)）]", " ");
            value = value.replaceAll("[【\\[]?(?:B站|哔哩|bilibli|bilibili|腾讯|爱奇艺|优酷|芒果|弹幕|来源)[】\\]]?", " ");
            value = value.replaceAll("\\s+", " ").trim();
            return value.isEmpty() ? clean(name) : value;
        }
    }
}
