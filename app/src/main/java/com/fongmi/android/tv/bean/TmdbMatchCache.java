package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.HashMap;
import java.util.Map;

public class TmdbMatchCache {

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
        Entry entry = getItems().get(key(siteKey, vodId));
        return entry == null ? null : entry.toItem();
    }

    public void put(String siteKey, String vodId, TmdbItem item) {
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId) || item == null || item.getTmdbId() <= 0) return;
        getItems().put(key(siteKey, vodId), Entry.from(item));
    }

    public Map<String, Entry> getItems() {
        if (items == null) items = new HashMap<>();
        return items;
    }

    private String key(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
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
            return entry;
        }

        public TmdbItem toItem() {
            return new TmdbItem(tmdbId, mediaType, title, subtitle, overview, posterUrl, backdropUrl, credit);
        }
    }
}
