package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class WebThemeManifestRollbackTest {

    private static final String CACHE_URL = "https://rollback.example/theme.json";
    private static final String CACHE_KEY = CACHE_URL + "\nmobile";
    private static final String ETAG_V1 = "\"theme-v1\"";
    private static final String ETAG_V2 = "\"theme-v2\"";
    private static final String ETAG_V3 = "\"theme-v3\"";
    private static final long NOW = 2_000_000L;

    @Before
    @After
    public void clearManifestCache() {
        WebThemeManifestLoader.clearCache();
    }

    @Test
    public void validatedUpdateRetainsOneStableRollbackVersionUntilDocumentAcceptance() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        WebThemeManifestLoader.LoadResult first = refresh(
                manifest("1", "home-v1.html"), ETAG_V1, persistent, NOW);

        assertTrue(first.activationPending());
        assertFalse(first.rollbackAvailable());
        assertTrue(WebThemeManifestLoader.accept(
                CACHE_URL, "mobile", first.revision(), persistent));

        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);

        assertTrue(second.activationPending());
        assertTrue(second.rollbackAvailable());
        assertEquals("2", second.manifest().getVersion());
        assertEquals(manifest("1", "home-v1.html"), persistent.single().previous().json());
        assertEquals(WebThemeManifestLoader.RollbackAction.ROLLBACK,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));
    }

    @Test
    public void pendingUpdateCanAutomaticallyRollbackExactlyTheExpectedRevision() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);

        WebThemeManifestLoader.LoadResult rolledBack = WebThemeManifestLoader.rollbackPending(
                CACHE_URL, "mobile", second.revision(), persistent, NOW + 2);

        assertEquals(WebThemeManifestLoader.CacheState.ROLLED_BACK, rolledBack.state());
        assertEquals("1", rolledBack.manifest().getVersion());
        assertFalse(rolledBack.activationPending());
        assertFalse(rolledBack.rollbackAvailable());
        assertEquals(WebThemeManifestLoader.RollbackAction.RETRY,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));

        IOException stale = assertThrows(IOException.class, () -> WebThemeManifestLoader.rollbackPending(
                CACHE_URL, "mobile", second.revision(), persistent, NOW + 3));
        assertEquals("Theme manifest rollback is no longer available", stale.getMessage());
        assertEquals("1", persistent.single().current().json().contains("\"version\":\"1\"") ? "1" : "unexpected");
    }

    @Test
    public void blockedVersionValidatorPreventsTheSameFailedReleaseFromReactivating() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);
        WebThemeManifestLoader.rollbackPending(
                CACHE_URL, "mobile", second.revision(), persistent, NOW + 2);
        WebThemeManifestLoader.clearCache();
        AtomicReference<String> sentEtag = new AtomicReference<>();
        long expiredAt = NOW + 2 + WebThemeManifestLoader.CACHE_TTL_MILLIS;

        WebThemeManifestLoader.LoadResult result = load(false, etag -> {
            sentEtag.set(etag);
            return WebThemeManifestLoader.FetchResult.notModified(ETAG_V2);
        }, persistent, expiredAt);

        assertEquals(ETAG_V2, sentEtag.get());
        assertEquals(WebThemeManifestLoader.CacheState.ROLLED_BACK, result.state());
        assertEquals("1", result.manifest().getVersion());
        assertEquals(expiredAt, persistent.single().current().validatedAt());
        assertEquals(ETAG_V2, persistent.single().previous().etag());
    }

    @Test
    public void aDifferentServerReleaseCanReplaceTheBlockedVersion() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);
        WebThemeManifestLoader.rollbackPending(
                CACHE_URL, "mobile", second.revision(), persistent, NOW + 2);
        WebThemeManifestLoader.clearCache();
        long expiredAt = NOW + 2 + WebThemeManifestLoader.CACHE_TTL_MILLIS;

        WebThemeManifestLoader.LoadResult third = load(false, etag -> {
            assertEquals(ETAG_V2, etag);
            return WebThemeManifestLoader.FetchResult.modified(
                    manifest("3", "home-v3.html"), ETAG_V3);
        }, persistent, expiredAt);

        assertEquals(WebThemeManifestLoader.CacheState.REFRESHED, third.state());
        assertEquals("3", third.manifest().getVersion());
        assertTrue(third.activationPending());
        assertTrue(third.rollbackAvailable());
        assertEquals("1", WebThemeManifest.parse(CACHE_URL,
                persistent.single().previous().json(), "mobile").getVersion());
        assertEquals("", persistent.single().blockedRevision());
    }

    @Test
    public void serverRevertingToKnownStableVersionClearsPendingActivation() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        refresh(manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);

        WebThemeManifestLoader.LoadResult reverted = refresh(
                manifest("1", "home-v1.html"), ETAG_V1, persistent, NOW + 2);

        assertEquals("1", reverted.manifest().getVersion());
        assertFalse(reverted.activationPending());
        assertFalse(reverted.rollbackAvailable());
        assertNull(persistent.single().previous());
        assertEquals(WebThemeManifestLoader.RollbackAction.NONE,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));
    }

    @Test
    public void manualRollbackAndRetrySwapOnlyTheTwoRetainedVersions() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);
        assertTrue(WebThemeManifestLoader.accept(
                CACHE_URL, "mobile", second.revision(), persistent));

        WebThemeManifestLoader.LoadResult rolledBack = WebThemeManifestLoader.applyRollbackAction(
                CACHE_URL, "mobile", WebThemeManifestLoader.RollbackAction.ROLLBACK,
                persistent, NOW + 2);

        assertEquals("1", rolledBack.manifest().getVersion());
        assertEquals(WebThemeManifestLoader.CacheState.ROLLED_BACK, rolledBack.state());
        assertEquals(WebThemeManifestLoader.RollbackAction.RETRY,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));

        WebThemeManifestLoader.LoadResult retried = WebThemeManifestLoader.applyRollbackAction(
                CACHE_URL, "mobile", WebThemeManifestLoader.RollbackAction.RETRY,
                persistent, NOW + 3);

        assertEquals("2", retried.manifest().getVersion());
        assertTrue(retried.activationPending());
        assertTrue(retried.rollbackAvailable());
        assertEquals(WebThemeManifestLoader.RollbackAction.ROLLBACK,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));
    }

    @Test
    public void failedRefreshOfAPendingCandidateUsesTheStablePreviousVersion() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        refresh(manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);
        WebThemeManifestLoader.clearCache();
        IOException offline = new IOException("offline");

        WebThemeManifestLoader.LoadResult fallback = load(true, etag -> {
            assertEquals(ETAG_V2, etag);
            throw offline;
        }, persistent, NOW + 2);

        assertEquals(WebThemeManifestLoader.CacheState.ROLLED_BACK, fallback.state());
        assertEquals("1", fallback.manifest().getVersion());
        assertEquals(offline, fallback.refreshFailure());
        assertEquals(WebThemeManifestLoader.RollbackAction.RETRY,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));
    }

    @Test
    public void blockedRejectedVersionIsNeverPromotedWhenTheStablePayloadIsCorrupt() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        String rejectedJson = manifest("2", "home-v2.html");
        WebThemeManifestLoader.StoredManifest rejected = new WebThemeManifestLoader.StoredManifest(
                rejectedJson, ETAG_V2, NOW);
        persistent.write(CACHE_KEY, new WebThemeManifestLoader.StoredCache(
                new WebThemeManifestLoader.StoredManifest("{\"schemaVersion\":2}", ETAG_V1, NOW),
                rejected, false, WebThemeManifestLoader.revision(rejectedJson)));
        IOException offline = new IOException("offline");

        IOException thrown = assertThrows(IOException.class, () -> load(false, etag -> {
            throw offline;
        }, persistent, NOW + 1));

        assertEquals(offline, thrown);
        assertEquals(WebThemeManifestLoader.RollbackAction.NONE,
                WebThemeManifestLoader.rollbackAction(CACHE_URL, "mobile", persistent));
    }

    @Test
    public void staleRollbackRevisionCannotReplaceANewerPendingCandidate() throws Exception {
        MemoryPersistentCache persistent = stableV1();
        WebThemeManifestLoader.LoadResult second = refresh(
                manifest("2", "home-v2.html"), ETAG_V2, persistent, NOW + 1);
        WebThemeManifestLoader.LoadResult third = refresh(
                manifest("3", "home-v3.html"), ETAG_V3, persistent, NOW + 2);

        assertThrows(IOException.class, () -> WebThemeManifestLoader.rollbackPending(
                CACHE_URL, "mobile", second.revision(), persistent, NOW + 3));

        assertEquals("3", third.manifest().getVersion());
        assertEquals("3", WebThemeManifest.parse(CACHE_URL,
                persistent.single().current().json(), "mobile").getVersion());
        assertEquals("1", WebThemeManifest.parse(CACHE_URL,
                persistent.single().previous().json(), "mobile").getVersion());
    }

    private static MemoryPersistentCache stableV1() throws Exception {
        MemoryPersistentCache persistent = new MemoryPersistentCache();
        WebThemeManifestLoader.LoadResult first = refresh(
                manifest("1", "home-v1.html"), ETAG_V1, persistent, NOW);
        assertTrue(WebThemeManifestLoader.accept(
                CACHE_URL, "mobile", first.revision(), persistent));
        return persistent;
    }

    private static WebThemeManifestLoader.LoadResult refresh(String json, String etag,
            MemoryPersistentCache persistent, long now) throws IOException {
        return load(true, ignored -> WebThemeManifestLoader.FetchResult.modified(json, etag),
                persistent, now);
    }

    private static WebThemeManifestLoader.LoadResult load(boolean force,
            WebThemeManifestLoader.ConditionalSource source,
            WebThemeManifestLoader.PersistentCache persistent, long now) throws IOException {
        return WebThemeManifestLoader.load(CACHE_URL, "mobile", force, source, persistent, now);
    }

    private static String manifest(String version, String entry) {
        return "{\"schemaVersion\":2,\"id\":\"rollback.theme\",\"version\":\"" + version
                + "\",\"minHostApi\":2,\"pages\":{\"home\":{\"entry\":\"" + entry
                + "\",\"contract\":\"vod.home@1\"}},\"permissions\":{\"home\":[\"vod.home\"]}}";
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

        private WebThemeManifestLoader.StoredCache single() {
            assertEquals(1, entries.size());
            return entries.get(CACHE_KEY);
        }
    }
}
