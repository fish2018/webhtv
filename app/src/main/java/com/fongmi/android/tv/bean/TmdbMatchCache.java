package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.title.MediaTitleParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TmdbMatchCache {

    private static final String TITLE_SCOPE = "__title__";

    private Map<String, Entry> items;

    public static TmdbMatchCache objectFrom(String str) {
        try {
            TmdbMatchCache cache = App.gson().fromJson(str, TmdbMatchCache.class);
            return cache == null ? new TmdbMatchCache() : cache;
        } catch (Exception e) {
            return new TmdbMatchCache();
        }
    }

    public TmdbMatchCache() {
        this.items = new HashMap<>();
    }

    public TmdbItem find(String siteKey, String vodId) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        Entry entry = getItems().get(key(siteKey, vodId));
        return entry == null ? null : entry.toItem();
    }

    public TmdbItem find(String siteKey, String vodId, String sourceTitle) {
        Entry entry = findEntry(siteKey, vodId, sourceTitle);
        return entry == null ? null : entry.toItem();
    }

    /**
     * 用户手动选定的条目。手动选择的存在本身就意味着"标题解析结果和用户意图不一致"，
     * 所以它既不受标题兼容性校验约束，也不该被后续自动匹配覆盖。
     */
    public TmdbItem findManual(String siteKey, String vodId, String sourceTitle) {
        Entry entry = findManualEntry(siteKey, vodId, sourceTitle);
        return entry == null ? null : entry.toItem();
    }

    public boolean isManual(String siteKey, String vodId, String sourceTitle) {
        return findManualEntry(siteKey, vodId, sourceTitle) != null;
    }

    private Entry findEntry(String siteKey, String vodId, String sourceTitle) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        if (TextUtils.isEmpty(sourceTitle)) return getItems().get(key(siteKey, vodId));
        Entry manual = findManualEntry(siteKey, vodId, sourceTitle);
        if (manual != null) return manual;
        Entry scoped = getItems().get(key(siteKey, vodId, sourceTitle));
        if (scoped != null) return scoped;
        Entry legacy = getItems().get(key(siteKey, vodId));
        if (isCompatible(legacy, sourceTitle)) return legacy;
        Entry title = getItems().get(titleKey(sourceTitle));
        return isCompatible(title, sourceTitle) ? title : null;
    }

    private Entry findManualEntry(String siteKey, String vodId, String sourceTitle) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return null;
        Entry anchor = getItems().get(key(siteKey, vodId));
        // 没有标题可比时（如 Intent 未带 name）无从区分同一 vodId 下的多部作品，
        // 只能沿用条目级语义，与两参 find(siteKey, vodId) 的既有行为保持一致。
        if (TextUtils.isEmpty(sourceTitle)) return isManual(anchor) ? anchor : null;
        Entry scoped = getItems().get(key(siteKey, vodId, sourceTitle));
        if (isManual(scoped)) return scoped;
        // 条目级锚点：站源标题会被 TMDB 富集改写成 TMDB 标题，手动选择必须能在标题变化后仍被读回。
        // 但同一 vodId 下可能挂着多个不同作品（见 TmdbMatchCacheTest 的共享 vodId 用例），
        // 所以锚点只在标题确实指向同一作品时才生效。
        return isManual(anchor) && anchor.matchesManualTitle(matchTitle(sourceTitle)) ? anchor : null;
    }

    private boolean isManual(Entry entry) {
        return entry != null && entry.manual && entry.tmdbId > 0;
    }

    public void put(String siteKey, String vodId, TmdbItem item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        if (isManual(getItems().get(key(siteKey, vodId)))) return;
        getItems().put(key(siteKey, vodId), Entry.from(item));
    }

    public void put(String siteKey, String vodId, String sourceTitle, TmdbItem item) {
        if (TextUtils.isEmpty(sourceTitle)) {
            put(siteKey, vodId, item);
            return;
        }
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        // 自动匹配不得覆盖用户的手动选择，否则下次进场读回的是自动猜测。
        if (findManualEntry(siteKey, vodId, sourceTitle) != null) return;
        Entry entry = Entry.from(item);
        getItems().put(key(siteKey, vodId, sourceTitle), entry);
        putTitle(sourceTitle, entry);
    }

    /**
     * 记录手动选择。sourceTitles 传入所有已知的站源标题别名（详情名、Intent 名、当前 Vod 名），
     * 任一别名都能读回同一条目；同时写入条目级锚点，标题被改写后依然命中。
     * 仍照旧维护全局标题域：手动纠正过的结果不能把被否掉的旧猜测留在那里给别的站源读；
     * 但 findManualEntry 不看标题域，所以"手动"的排他性只作用于当前条目。
     */
    public void putManual(String siteKey, String vodId, List<String> sourceTitles, TmdbItem item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        Entry entry = Entry.manual(item);
        if (sourceTitles != null) {
            for (String sourceTitle : sourceTitles) entry.addManualTitle(matchTitle(sourceTitle));
        }
        // TMDB 标题本身也是别名：富集会把 vod.getName() 改写成它，下次进场用它当键来读。
        entry.addManualTitle(matchTitle(item.getTitle()));
        getItems().put(key(siteKey, vodId), entry);
        if (sourceTitles == null) return;
        for (String sourceTitle : sourceTitles) {
            if (TextUtils.isEmpty(sourceTitle)) continue;
            getItems().put(key(siteKey, vodId, sourceTitle), entry);
            putTitle(sourceTitle, entry);
        }
    }

    public Map<String, Entry> getItems() {
        if (items == null) items = new HashMap<>();
        return items;
    }

    private String key(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    private String key(String siteKey, String vodId, String sourceTitle) {
        return key(siteKey, vodId) + AppDatabase.SYMBOL + sourceKey(sourceTitle);
    }

    private String titleKey(String sourceTitle) {
        String title = matchTitle(sourceTitle);
        return TextUtils.isEmpty(title) ? "" : TITLE_SCOPE + AppDatabase.SYMBOL + title;
    }

    /**
     * 维护全局标题域（同名作品跨站沿用）。手动结论优先于自动猜测：
     * 用户纠正过的标题不能再把被否掉的旧猜测留给别的站源，也不该因为与旧猜测不一致
     * 而降级成冲突标记（那会让所有站源都读不到）。只有两个都是手动且指向不同条目时，
     * 才是真正的同名歧义，此时才标冲突。
     */
    private void putTitle(String sourceTitle, Entry entry) {
        String key = titleKey(sourceTitle);
        if (TextUtils.isEmpty(key) || entry == null) return;
        Entry cached = getItems().get(key);
        if (cached == null || sameTmdb(cached, entry)) {
            getItems().put(key, entry);
        } else if (isManual(entry) && !isManual(cached)) {
            getItems().put(key, entry);
        } else if (isManual(cached) && !isManual(entry)) {
            // 保留手动结论，别让某个站源的自动猜测把它冲掉。
        } else {
            getItems().put(key, Entry.conflict(sourceTitle));
        }
    }

    private boolean sameTmdb(Entry first, Entry second) {
        if (first == null || second == null) return false;
        return first.tmdbId > 0 && first.tmdbId == second.tmdbId && normalize(first.mediaType).equals(normalize(second.mediaType));
    }

    private boolean isCompatible(Entry entry, String sourceTitle) {
        if (entry == null || entry.tmdbId <= 0 || TextUtils.isEmpty(sourceTitle)) return false;
        String source = matchTitle(sourceTitle);
        String cached = matchTitle(entry.title);
        return TextUtils.isEmpty(source) || (!TextUtils.isEmpty(cached) && cached.equals(source));
    }

    private String sourceKey(String sourceTitle) {
        return normalize(sourceTitle).replace(AppDatabase.SYMBOL, " ");
    }

    private String matchTitle(String text) {
        return normalize(new MediaTitleParser().cleanTitle(text));
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    public static class Entry {

        private int tmdbId;
        private String mediaType;
        private String title;
        private String subtitle;
        private String overview;
        private String posterUrl;
        private String backdropUrl;
        private String credit;
        private double rating;
        private String originalLanguage;
        private String originCountry;
        private String department;
        private boolean manual;
        private List<String> manualTitles;

        public static Entry conflict(String title) {
            Entry entry = new Entry();
            entry.tmdbId = -1;
            entry.mediaType = "";
            entry.title = title;
            return entry;
        }

        public static Entry manual(TmdbItem item) {
            Entry entry = from(item);
            entry.manual = true;
            entry.manualTitles = new ArrayList<>();
            return entry;
        }

        void addManualTitle(String normalizedTitle) {
            if (TextUtils.isEmpty(normalizedTitle)) return;
            if (manualTitles == null) manualTitles = new ArrayList<>();
            if (!manualTitles.contains(normalizedTitle)) manualTitles.add(normalizedTitle);
        }

        /**
         * 别名为 null 只可能来自旧版本写入的数据（当时还没有别名字段），放行以免升级后失效。
         * 由 putManual 写入的条目一定带列表：即使所有标题都被 cleanTitle 清洗成空串（例如站源
         * 标题只有年份），也不能放行——同一 vodId 下可能挂着多部作品，放行会让锚点变成通配符，
         * 把别的作品也匹配成这次手动选择。这种情况下仍可通过站源标题的精确键读回。
         */
        boolean matchesManualTitle(String normalizedTitle) {
            if (manualTitles == null) return true;
            return !TextUtils.isEmpty(normalizedTitle) && manualTitles.contains(normalizedTitle);
        }

        public static Entry from(TmdbItem item) {
            Entry entry = new Entry();
            entry.tmdbId = item.getTmdbId();
            entry.mediaType = item.getMediaType();
            entry.title = item.getTitle();
            entry.subtitle = item.getSubtitle();
            entry.overview = item.getOverview();
            entry.posterUrl = item.getPosterUrl();
            entry.backdropUrl = item.getBackdropUrl();
            entry.credit = item.getCredit();
            entry.rating = item.getRating();
            entry.originalLanguage = item.getOriginalLanguage();
            entry.originCountry = item.getOriginCountry();
            entry.department = item.getDepartment();
            return entry;
        }

        public TmdbItem toItem() {
            return new TmdbItem(tmdbId, mediaType, title, subtitle, overview, posterUrl, backdropUrl, credit, rating, originalLanguage, originCountry, null, department);
        }
    }
}
