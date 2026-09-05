package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppCacheStorageTest {

    @Test
    public void tmdbMatchAndTitleLearningUseClearableAppCache() throws Exception {
        String setting = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));
        String learning = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "title", "MediaTitleLearningStore.java")));

        assertTrue(setting.contains("AppCache.get(AppCache.KEY_TMDB_MATCH)"));
        assertTrue(setting.contains("AppCache.put(AppCache.KEY_TMDB_MATCH"));
        assertTrue(setting.contains("AppCache.get(AppCache.KEY_TMDB_SEASON_MATCH)"));
        assertTrue(setting.contains("AppCache.put(AppCache.KEY_TMDB_SEASON_MATCH"));
        assertFalse(setting.contains("Prefers.getString(\"tmdb_match_cache\")"));
        assertFalse(setting.contains("Prefers.put(\"tmdb_match_cache\""));

        assertTrue(learning.contains("AppCache.KEY_MEDIA_TITLE_LEARNING"));
        assertTrue(learning.contains("AppCache.get(KEY)"));
        assertTrue(learning.contains("AppCache.put(KEY"));
        assertFalse(learning.contains("Prefers.getString(KEY)"));
        assertFalse(learning.contains("Prefers.put(KEY"));
    }

    @Test
    public void exposedCacheApisUseAppCacheFiles() throws Exception {
        String bridge = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "web", "HomeWebBridge.java")));
        String process = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "server", "process", "Cache.java")));

        assertTrue(bridge.contains("case \"cache.get\" -> quote(AppCache.get(cacheKey(payload)))"));
        assertTrue(bridge.contains("AppCache.put(cacheKey(payload), Json.safeString(payload, \"value\"))"));
        assertTrue(bridge.contains("AppCache.remove(cacheKey(payload))"));
        assertFalse(bridge.contains("Prefers.getString(cacheKey(payload))"));
        assertFalse(bridge.contains("Prefers.put(cacheKey(payload)"));
        assertFalse(bridge.contains("Prefers.remove(cacheKey(payload))"));

        assertTrue(process.contains("Nano.ok(AppCache.get(getKey(rule, key)))"));
        assertTrue(process.contains("AppCache.put(getKey(rule, key), params.get(\"value\"))"));
        assertTrue(process.contains("AppCache.remove(getKey(rule, key))"));
        assertFalse(process.contains("Prefers.getString(getKey(rule, key))"));
        assertFalse(process.contains("Prefers.put(getKey(rule, key)"));
        assertFalse(process.contains("Prefers.remove(getKey(rule, key))"));
    }

    @Test
    public void clearCacheRemovesLegacyPreferenceCacheKeys() throws Exception {
        String fileUtil = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "utils", "FileUtil.java")));
        String appCache = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "utils", "AppCache.java")));

        assertTrue(fileUtil.contains("Path.clear(Path.cache())"));
        assertTrue(fileUtil.contains("AppCache.clearLegacyPreferences()"));
        assertTrue(appCache.contains(".remove(KEY_TMDB_MATCH)"));
        assertTrue(appCache.contains(".remove(KEY_TMDB_SEASON_MATCH)"));
        assertTrue(appCache.contains(".remove(KEY_MEDIA_TITLE_LEARNING)"));
        assertTrue(appCache.contains("entry.getKey().startsWith(WEB_CACHE_PREFIX)"));
    }

    @Test
    public void backupDoesNotTreatCacheAsSettings() throws Exception {
        String backup = read(mainJava().resolve(Path.of("com", "fongmi", "android", "tv", "bean", "Backup.java")));

        assertFalse(backup.contains("\"tmdb_match_cache\""));
        assertFalse(backup.contains("\"tmdb_season_match_cache\""));
        assertFalse(backup.contains("\"media_title_learning\""));
        assertTrue(backup.contains("if (key.startsWith(\"cache_\")) return false;"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
