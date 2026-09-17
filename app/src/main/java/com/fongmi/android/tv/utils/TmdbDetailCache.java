package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TmdbDetailCache {

    public static final String EXTRA_KEY = "tmdb_detail_cache_key";

    private static final int MAX_PENDING = 8;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();

    public static String put(TmdbItem item, JsonObject detail, List<TmdbPerson> cast) {
        if (item == null || item.getTmdbId() <= 0 || detail == null) return "";
        if (CACHE.size() >= MAX_PENDING) CACHE.clear();
        String key = "tmdb_detail_" + NEXT_ID.incrementAndGet();
        CACHE.put(key, new Entry(item, detail, cast));
        return key;
    }

    public static Entry take(String key, TmdbItem expected) {
        if (TextUtils.isEmpty(key)) return null;
        Entry entry = CACHE.remove(key);
        if (entry == null || !entry.matches(expected)) return null;
        return entry;
    }

    private TmdbDetailCache() {
    }

    public static class Entry {

        private final TmdbItem item;
        private final JsonObject detail;
        private final List<TmdbPerson> cast;

        private Entry(TmdbItem item, JsonObject detail, List<TmdbPerson> cast) {
            this.item = item;
            this.detail = detail;
            this.cast = cast == null ? new ArrayList<>() : new ArrayList<>(cast);
        }

        public TmdbItem getItem() {
            return item;
        }

        public JsonObject getDetail() {
            return detail;
        }

        public List<TmdbPerson> getCast() {
            return new ArrayList<>(cast);
        }

        private boolean matches(TmdbItem expected) {
            if (expected == null || item == null) return false;
            return item.getTmdbId() == expected.getTmdbId() && normalize(item.getMediaType()).equals(normalize(expected.getMediaType()));
        }

        private String normalize(String value) {
            return TextUtils.isEmpty(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
