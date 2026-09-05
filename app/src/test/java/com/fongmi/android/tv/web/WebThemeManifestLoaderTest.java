package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WebThemeManifestLoaderTest {

    private static final String CACHE_URL = "https://cache.example/theme.json";

    @Before
    @After
    public void clearManifestCache() {
        WebThemeManifestLoader.clearCache();
    }

    @Test
    public void cacheMatrixUsesHitRefreshAndLastKnownGood() throws Exception {
        AtomicInteger reads = new AtomicInteger();

        WebThemeManifestLoader.LoadResult first = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    reads.incrementAndGet();
                    return manifest("1", "home-v1.html");
                });
        WebThemeManifestLoader.LoadResult hit = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    throw new AssertionError("cache hit must not read source");
                });
        WebThemeManifestLoader.LoadResult refreshed = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> {
                    reads.incrementAndGet();
                    return manifest("2", "home-v2.html");
                });
        IOException refreshFailure = new IOException("private upstream details");
        WebThemeManifestLoader.LoadResult fallback = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> {
                    reads.incrementAndGet();
                    throw refreshFailure;
                });

        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, first.state());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, hit.state());
        assertSame(first.manifest(), hit.manifest());
        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, refreshed.state());
        assertEquals("https://cache.example/home-v2.html",
                refreshed.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertSame(refreshed.manifest(), fallback.manifest());
        assertSame(refreshFailure, fallback.refreshFailure());
        assertEquals(3, reads.get());
    }


    @Test
    public void cacheEntriesAreIsolatedByPlatformTarget() throws Exception {
        WebThemeManifestLoader.LoadResult mobile = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> manifest("1", "mobile.html"));
        WebThemeManifestLoader.LoadResult leanback = WebThemeManifestLoader.load(
                CACHE_URL, "leanback", false, () -> manifest("1", "leanback.html"));
        WebThemeManifestLoader.LoadResult mobileHit = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    throw new AssertionError("mobile cache hit must not read source");
                });

        assertEquals("https://cache.example/mobile.html",
                mobile.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals("https://cache.example/leanback.html",
                leanback.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, mobileHit.state());
        assertSame(mobile.manifest(), mobileHit.manifest());
    }

    @Test
    public void invalidRefreshKeepsThePreviousValidatedManifest() throws Exception {
        WebThemeManifestLoader.LoadResult first = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> manifest("1", "home-v1.html"));

        WebThemeManifestLoader.LoadResult fallback = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> "{\"schemaVersion\":2}");

        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertSame(first.manifest(), fallback.manifest());
        assertNotNull(fallback.refreshFailure());
        assertTrue(fallback.refreshFailure().getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void failedRefreshDoesNotOverwriteANewerConcurrentSuccess() throws Exception {
        WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> manifest("1", "home-v1.html"));
        IOException refreshFailure = new IOException("offline");

        WebThemeManifestLoader.LoadResult fallback = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> {
                    WebThemeManifestLoader.load(
                            CACHE_URL, "mobile", true,
                            () -> manifest("2", "home-v2.html"));
                    throw refreshFailure;
                });

        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertEquals("https://cache.example/home-v2.html",
                fallback.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertSame(refreshFailure, fallback.refreshFailure());
    }

    @Test
    public void coldFailureIsNotSilentlyRecovered() {
        assertThrows(IOException.class, () -> WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> {
                    throw new IOException("offline");
                }));
    }

    @Test
    public void persistentLastKnownGoodSurvivesMemoryCacheReset() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        String original = manifest("1", "home-v1.html");

        WebThemeManifestLoader.LoadResult refreshed = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> original, persistent);
        WebThemeManifestLoader.clearCache();
        IOException refreshFailure = new IOException("offline");
        WebThemeManifestLoader.LoadResult fallback = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", true, () -> {
                    throw refreshFailure;
                }, persistent);
        WebThemeManifestLoader.LoadResult memoryHit = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    throw new AssertionError("restored manifest must be cached in memory");
                }, persistent);

        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, refreshed.state());
        assertEquals(1, persistent.size());
        assertEquals(WebThemeManifestLoader.CacheState.LAST_KNOWN_GOOD, fallback.state());
        assertSame(refreshFailure, fallback.refreshFailure());
        assertEquals("https://cache.example/home-v1.html",
                fallback.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals(WebThemeManifestLoader.CacheState.CACHE_HIT, memoryHit.state());
        assertSame(fallback.manifest(), memoryHit.manifest());
    }

    @Test
    public void persistentCacheRemainsIsolatedByPlatformTarget() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        WebThemeManifestLoader.load(CACHE_URL, "mobile", false,
                () -> manifest("1", "mobile.html"), persistent);
        WebThemeManifestLoader.clearCache();
        IOException offline = new IOException("offline");

        IOException thrown = assertThrows(IOException.class, () -> WebThemeManifestLoader.load(
                CACHE_URL, "leanback", false, () -> {
                    throw offline;
                }, persistent));

        assertSame(offline, thrown);
        assertEquals(1, persistent.size());
    }

    @Test
    public void corruptPersistentEntryDoesNotMaskColdFailure() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        WebThemeManifestLoader.load(CACHE_URL, "mobile", false,
                () -> manifest("1", "home-v1.html"), persistent);
        persistent.corruptAll("{\"schemaVersion\":2}");
        WebThemeManifestLoader.clearCache();
        IOException offline = new IOException("offline");

        IOException thrown = assertThrows(IOException.class, () -> WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    throw offline;
                }, persistent));

        assertSame(offline, thrown);
        assertEquals(0, persistent.size());
    }

    @Test
    public void emptyPersistentEntryIsDiscardedWithoutMaskingColdFailure() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        persistent.write(CACHE_URL + "\nmobile",
                stable(new WebThemeManifestLoader.StoredManifest("", "", 0)));
        IOException offline = new IOException("offline");

        IOException thrown = assertThrows(IOException.class, () -> WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> {
                    throw offline;
                }, persistent));

        assertSame(offline, thrown);
        assertEquals(0, persistent.size());
    }

    @Test
    public void persistentWriteFailureDoesNotRejectFreshManifest() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        persistent.failWrites = true;

        WebThemeManifestLoader.LoadResult result = WebThemeManifestLoader.load(
                CACHE_URL, "mobile", false, () -> manifest("1", "home-v1.html"), persistent);

        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, result.state());
        assertEquals("https://cache.example/home-v1.html",
                result.manifest().getPage(WebThemePage.HOME).getEntryUrl());
        assertEquals(0, persistent.size());
    }

    @Test
    public void boundedReaderAcceptsLimitAndRejectsOneExtraByte() throws Exception {
        assertEquals("1234", WebThemeManifestLoader.read(stream("1234"), 4));
        assertThrows(IOException.class, () -> WebThemeManifestLoader.read(stream("12345"), 4));
    }

    @Test
    public void boundedReaderRejectsMalformedUtf8() {
        byte[] malformed = {(byte) 0xC3, (byte) 0x28};

        assertThrows(IOException.class,
                () -> WebThemeManifestLoader.read(new ByteArrayInputStream(malformed), malformed.length));
    }

    @Test
    public void boundedReaderUsesAndroidCompatibleStrictUtf8Decoding() throws Exception {
        String source = source();

        assertTrue(source.contains("StandardCharsets.UTF_8.newDecoder()"));
        assertTrue(source.contains("CodingErrorAction.REPORT"));
        assertFalse(source.contains("output.toString(StandardCharsets.UTF_8)"));
    }

    @Test
    public void remoteManifestUsesIsolatedPlatformTlsClient() throws Exception {
        String source = source();

        assertTrue(source.contains("new OkHttpClient.Builder()"));
        assertTrue(source.contains("Dns.SYSTEM.lookup(hostname)"));
        assertFalse(source.contains("OkHttp.client().newBuilder()"));
        assertFalse(source.contains("com.github.catvod.net.OkHttp"));
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
        private boolean failWrites;

        @Override
        public WebThemeManifestLoader.StoredCache read(String cacheKey) {
            return entries.get(cacheKey);
        }

        @Override
        public void write(String cacheKey, WebThemeManifestLoader.StoredCache stored) throws IOException {
            if (failWrites) throw new IOException("disk full");
            entries.put(cacheKey, stored);
        }

        @Override
        public void remove(String cacheKey) {
            entries.remove(cacheKey);
        }

        private int size() {
            return entries.size();
        }

        private void corruptAll(String json) {
            entries.replaceAll((key, value) -> new WebThemeManifestLoader.StoredCache(
                    new WebThemeManifestLoader.StoredManifest(
                            json, value.current().etag(), value.current().validatedAt()),
                    value.previous(), value.activationPending(), value.blockedRevision()));
        }
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String source() throws Exception {
        Path root = Files.exists(Path.of("src")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(
                "src/main/java/com/fongmi/android/tv/web/WebThemeManifestLoader.java"), StandardCharsets.UTF_8);
    }
}
