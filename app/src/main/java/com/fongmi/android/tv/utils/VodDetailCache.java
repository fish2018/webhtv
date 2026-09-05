package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class VodDetailCache {

    static final long CONTENT_TTL_MS = 5 * 60 * 1000L;

    private static final int MAX_PENDING = 8;
    private static final int MAX_CONTENT = 8;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final ConcurrentHashMap<String, Vod> CACHE = new ConcurrentHashMap<>();
    private static final LinkedHashMap<String, ContentEntry> CONTENT_CACHE = new LinkedHashMap<>(16, 0.75f, true);

    public static String put(Vod vod) {
        if (vod == null) return "";
        if (CACHE.size() >= MAX_PENDING) CACHE.clear();
        String key = "vod_detail_" + NEXT_ID.incrementAndGet();
        CACHE.put(key, vod);
        return key;
    }

    public static Vod take(String key) {
        if (key == null || key.isEmpty()) return null;
        return CACHE.remove(key);
    }

    public static void putContent(String source, String id, String content) {
        putContent(source, id, content, System.currentTimeMillis());
    }

    static synchronized void putContent(String source, String id, String content, long now) {
        String key = contentKey(source, id);
        if (key.isEmpty() || content == null || content.isEmpty()) return;
        removeExpiredContent(now);
        CONTENT_CACHE.put(key, new ContentEntry(content, now));
        trimContentCache();
    }

    public static String getContent(String source, String id) {
        return getContent(source, id, System.currentTimeMillis());
    }

    static synchronized String getContent(String source, String id, long now) {
        String key = contentKey(source, id);
        if (key.isEmpty()) return null;
        ContentEntry entry = CONTENT_CACHE.get(key);
        if (entry == null) return null;
        if (entry.expired(now)) {
            CONTENT_CACHE.remove(key);
            return null;
        }
        return entry.content;
    }

    public static synchronized void invalidateContent(String source, String id) {
        String key = contentKey(source, id);
        if (!key.isEmpty()) CONTENT_CACHE.remove(key);
    }

    private static String contentKey(String source, String id) {
        if (source == null || source.isEmpty() || id == null || id.isEmpty()) return "";
        return source + '\u0000' + id;
    }

    private static void removeExpiredContent(long now) {
        Iterator<Map.Entry<String, ContentEntry>> iterator = CONTENT_CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expired(now)) iterator.remove();
        }
    }

    private static void trimContentCache() {
        Iterator<String> iterator = CONTENT_CACHE.keySet().iterator();
        while (CONTENT_CACHE.size() > MAX_CONTENT && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private VodDetailCache() {
    }

    private static class ContentEntry {

        private final String content;
        private final long cachedAt;

        private ContentEntry(String content, long cachedAt) {
            this.content = content;
            this.cachedAt = cachedAt;
        }

        private boolean expired(long now) {
            return now - cachedAt > CONTENT_TTL_MS;
        }
    }
}
