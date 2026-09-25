package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WebThemeManifestConditionalCacheTest {

    private static final String CACHE_URL = "https://cache.example/theme.json";
    private static final String CACHE_KEY = CACHE_URL + "\nmobile";
    private static final String ETAG_V1 = "\"theme-v1\"";
    private static final String ETAG_V2 = "\"theme-v2\"";
    private static final long NOW = 1_000_000L;

    @Before
    @After
    public void clearManifestCache() {
        WebThemeManifestLoader.clearCache();
    }

    @Test
    public void freshPersistentEntrySkipsNetworkAcrossProcessRestart() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        WebThemeManifestLoader.LoadResult first = load(false,
                etag -> WebThemeManifestLoader.FetchResult.modified(manifest("1", "home-v1.html"), ETAG_V1),
                persistent, NOW);
        WebThemeManifestLoader.clearCache();
        AtomicInteger requests = new AtomicInteger();

        WebThemeManifestLoader.LoadResult hit = load(false, etag -> {
            requests.incrementAndGet();
            throw new AssertionError("fresh persistent cache must not hit the network");
        }, persistent, NOW + WebThemeManifestLoader.CACHE_TTL_MILLIS - 1);

        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, first.state());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, hit.state());
        assertEquals(0, requests.get());
        assertEquals("https://cache.example/home-v1.html",
                hit.manifest().getPage(WebThemePage.HOME).getEntryUrl());
    }

    @Test
    public void expiredEntryUsesEtagAnd304ExtendsTheFreshWindow() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        load(false, etag -> WebThemeManifestLoader.FetchResult.modified(
                manifest("1", "home-v1.html"), ETAG_V1), persistent, NOW);
        WebThemeManifestLoader.clearCache();
        AtomicReference<String> sentEtag = new AtomicReference<>();
        long revalidatedAt = NOW + WebThemeManifestLoader.CACHE_TTL_MILLIS;

        WebThemeManifestLoader.LoadResult revalidated = load(false, etag -> {
            sentEtag.set(etag);
            return WebThemeManifestLoader.FetchResult.notModified("");
        }, persistent, revalidatedAt);
        WebThemeManifestLoader.clearCache();
        WebThemeManifestLoader.LoadResult hit = load(false, etag -> {
            throw new AssertionError("304 must extend the persistent fresh window");
        }, persistent, revalidatedAt + 1);

        assertEquals(ETAG_V1, sentEtag.get());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, revalidated.state());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, hit.state());
        assertEquals(ETAG_V1, persistent.single().etag());
        assertEquals(revalidatedAt, persistent.single().validatedAt());
    }

    @Test
    public void forcedRefreshUsesEtagInsideTheFreshWindow() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        load(false, etag -> WebThemeManifestLoader.FetchResult.modified(
                manifest("1", "home-v1.html"), ETAG_V1), persistent, NOW);
        AtomicReference<String> sentEtag = new AtomicReference<>();

        WebThemeManifestLoader.LoadResult refreshed = load(true, etag -> {
            sentEtag.set(etag);
            return WebThemeManifestLoader.FetchResult.modified(
                    manifest("2", "home-v2.html"), ETAG_V2);
        }, persistent, NOW + 1);

        assertEquals(ETAG_V1, sentEtag.get());
        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, refreshed.state());
        assertEquals("https://cache.example/home-v2.html",
                refreshed.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals(ETAG_V2, persistent.single().etag());
        assertEquals(NOW + 1, persistent.single().validatedAt());
    }

    @Test
    public void expiredEntryStillFallsBackToLastKnownGood() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        load(false, etag -> WebThemeManifestLoader.FetchResult.modified(
                manifest("1", "home-v1.html"), ETAG_V1), persistent, NOW);
        WebThemeManifestLoader.clearCache();
        IOException offline = new IOException("offline");

        WebThemeManifestLoader.LoadResult fallback = load(false, etag -> {
            assertEquals(ETAG_V1, etag);
            throw offline;
        }, persistent, NOW + WebThemeManifestLoader.CACHE_TTL_MILLIS);

        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertSame(offline, fallback.refreshFailure());
        assertEquals(NOW, persistent.single().validatedAt());
    }

    @Test
    public void futureValidationTimeIsTreatedAsExpired() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        persistent.write(CACHE_KEY, stable(new WebThemeManifestLoader.StoredManifest(
                manifest("1", "home-v1.html"), ETAG_V1,
                NOW + WebThemeManifestLoader.CACHE_TTL_MILLIS)));
        AtomicInteger requests = new AtomicInteger();

        WebThemeManifestLoader.LoadResult refreshed = load(false, etag -> {
            requests.incrementAndGet();
            assertEquals(ETAG_V1, etag);
            return WebThemeManifestLoader.FetchResult.modified(
                    manifest("2", "home-v2.html"), ETAG_V2);
        }, persistent, NOW);

        assertEquals(1, requests.get());
        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, refreshed.state());
        assertEquals(NOW, persistent.single().validatedAt());
    }

    @Test
    public void notModifiedWithoutASentValidatorKeepsLegacyCacheExpired() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        persistent.write(CACHE_KEY, stable(new WebThemeManifestLoader.StoredManifest(
                manifest("1", "home-v1.html"), "", 0)));

        WebThemeManifestLoader.LoadResult fallback = load(false, etag -> {
            assertEquals("", etag);
            return WebThemeManifestLoader.FetchResult.notModified(ETAG_V1);
        }, persistent, NOW);

        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertEquals(0, persistent.single().validatedAt());
    }

    @Test
    public void notModifiedWithoutCachedManifestIsAColdFailure() {
        MemoryPersistentCache persistent = new MemoryPersistentCache();

        assertThrows(IOException.class, () -> load(false,
                etag -> WebThemeManifestLoader.FetchResult.notModified(ETAG_V1), persistent, NOW));
    }

    private static WebThemeManifestLoader.LoadResult load(boolean force,
            WebThemeManifestLoader.ConditionalSource source,
            WebThemeManifestLoader.PersistentCache persistent, long now) throws IOException {
        return WebThemeManifestLoader.load(CACHE_URL, "mobile", force, source, persistent, now);
    }

    private static String manifest(String version, String entry) {
        return "{\"schemaVersion\":2,\"id\":\"cache.theme\",\"version\":\"" + version
                + "\",\"minHostApi\":2,\"pages\":{\"home\":{\"entry\":\"" + entry
                + "\",\"contract\":\"vod.home@1\"}},\"permissions\":{\"home\":[\"vod.home\"]}}";
    }

    private static WebThemeManifestLoader.StoredCache stable(
            WebThemeManifestLoader.StoredManifest current) {
        return new WebThemeManifestLoader.StoredCache(current, null, false, "");
    }

    private static final class MemoryPersistentCache implements WebThemeManifestLoader.PersistentCache {

        private final Map<String, WebThemeManifestLoader.StoredCache> entries = new HashMap<>();

        @Override
        public WebThemeManifestLoader.StoredCache read(String cacheKey) {
            return entries.get(cacheKey);
        }

        @Override
        public void write(String cacheKey, WebThemeManifestLoader.StoredCache stored) {
            entries.put(cacheKey, stored);
        }

        @Override
        public void remove(String cacheKey) {
            entries.remove(cacheKey);
        }

        private WebThemeManifestLoader.StoredManifest single() {
            assertEquals(1, entries.size());
            return entries.values().iterator().next().current();
        }
    }
}
