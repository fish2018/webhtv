package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.lang.reflect.Field;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TmdbServiceCacheKeyTest {

    @Test
    public void detailCacheKeyIgnoresAuthAndApiBaseButKeepsLanguageAndProfile() {
        TmdbService service = new TmdbService();
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");

        TmdbConfig keyConfig = config("https://api.tmdb.org/3", "first-key", "zh-CN");
        TmdbConfig tokenConfig = config("https://mirror.example.com/tmdb/3", "second-key", "zh-CN");
        TmdbConfig otherLanguage = config("https://api.tmdb.org/3", "first-key", "en-US");

        assertEquals(service.detailCacheKey(item, keyConfig, true), service.detailCacheKey(item, tokenConfig, true));
        assertNotEquals(service.detailCacheKey(item, keyConfig, true), service.detailCacheKey(item, otherLanguage, true));
        assertNotEquals(service.detailCacheKey(item, keyConfig, true), service.detailCacheKey(item, keyConfig, false));
    }

    @Test
    public void playbackDetailCacheKeysFallbackToFullDetailCache() {
        TmdbService service = new TmdbService();
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        TmdbConfig config = config("https://api.tmdb.org/3", "test-key", "zh-CN");

        assertEquals(service.detailCacheKey(item, config, false), service.detailCacheKeys(item, config, false).get(0));
        assertTrue(service.detailCacheKeys(item, config, false).contains(service.detailCacheKey(item, config, true)));
        assertEquals(1, service.detailCacheKeys(item, config, true).size());
    }

    @Test
    public void seasonAndEpisodeCacheKeysIgnoreAuthAndApiBase() {
        TmdbService service = new TmdbService();
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");

        TmdbConfig first = config("https://api.tmdb.org/3", "first-key", "zh-CN");
        TmdbConfig second = config("https://mirror.example.com/tmdb/3", "second-key", "zh-CN");

        assertEquals(service.seasonCacheKey(item, 2, first), service.seasonCacheKey(item, 2, second));
        assertEquals(service.episodeCacheKey(item, 2, 3, first), service.episodeCacheKey(item, 2, 3, second));
    }

    @Test
    public void searchCacheKeyIgnoresAuthAndApiBaseButKeepsKeywordAndLanguage() {
        TmdbService service = new TmdbService();

        TmdbConfig first = config("https://api.tmdb.org/3", "first-key", "zh-CN");
        TmdbConfig second = config("https://mirror.example.com/tmdb/3", "second-key", "zh-CN");
        TmdbConfig english = config("https://api.tmdb.org/3", "first-key", "en-US");

        assertEquals(service.searchCacheKey(" 庆余年 ", first), service.searchCacheKey("庆余年", second));
        assertNotEquals(service.searchCacheKey("庆余年", first), service.searchCacheKey("庆余年 第二季", first));
        assertNotEquals(service.searchCacheKey("庆余年", first), service.searchCacheKey("庆余年", english));
    }

    @Test
    public void onAirTvDetailsUseOneDayCache() {
        TmdbService service = new TmdbService();

        assertEquals(24L * 60L * 60L * 1000L, service.detailCacheTtl(JsonParser.parseString(
                "{\"status\":\"Returning Series\",\"next_episode_to_air\":{\"season_number\":1,\"episode_number\":2}}")
                .getAsJsonObject()));
        assertEquals(24L * 60L * 60L * 1000L, service.detailCacheTtl(JsonParser.parseString(
                "{\"in_production\":true}")
                .getAsJsonObject()));
    }

    @Test
    public void completedAndMovieDetailsKeepSevenDayCache() {
        TmdbService service = new TmdbService();

        assertEquals(7L * 24L * 60L * 60L * 1000L, service.detailCacheTtl(JsonParser.parseString(
                "{\"status\":\"Ended\",\"number_of_episodes\":36}")
                .getAsJsonObject()));
        assertEquals(7L * 24L * 60L * 60L * 1000L, service.detailCacheTtl(JsonParser.parseString(
                "{\"status\":\"Released\",\"runtime\":120}")
                .getAsJsonObject()));
    }
    @Test
    public void authenticationFailuresOpenCredentialScopedCircuit() {
        TmdbService service = new TmdbService();
        TmdbConfig blocked = config("https://api.tmdb.org/3", "invalid-key", "zh-CN");
        TmdbConfig different = config("https://api.tmdb.org/3", "different-key", "zh-CN");
        TmdbService.clearAuthFailuresForTest();
        try {
            RuntimeException failure = service.httpFailure(blocked, 401, "TMDB search failed: HTTP 401");

            assertTrue(failure instanceof TmdbService.AuthException);
            assertEquals(401, ((TmdbService.AuthException) failure).getStatusCode());
            assertThrows(TmdbService.AuthException.class, () -> service.throwIfAuthBlocked(blocked));
            service.throwIfAuthBlocked(different);
        } finally {
            TmdbService.clearAuthFailuresForTest();
        }
    }

    @Test
    public void omdbRequestsReuseSharedClientAndRecoverAfterHttpFailure() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            server.enqueue(new MockResponse.Builder().code(503).body("temporarily unavailable").build());
            server.enqueue(new MockResponse.Builder().code(200).body("{\"Response\":\"True\",\"imdbRating\":\"8.7\"}").build());

            assertSame(OmdbService.clientForTest(), OmdbService.clientForTest());
            assertNull(OmdbService.fetch(server.url("/").toString(), "tt123", "test-key"));
            assertEquals("8.7", OmdbService.fetch(server.url("/").toString(), "tt123", "test-key").get("imdbRating").getAsString());
            assertEquals(2, server.getRequestCount());
        } finally {
            server.close();
        }
    }
    private static TmdbConfig config(String apiBase, String apiKey, String language) {
        try {
            TmdbConfig config = new TmdbConfig();
            set(config, "apiBase", apiBase);
            set(config, "apiKey", apiKey);
            set(config, "language", language);
            return config.sanitize();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void set(TmdbConfig config, String name, String value) throws Exception {
        Field field = TmdbConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(config, value);
    }
}
