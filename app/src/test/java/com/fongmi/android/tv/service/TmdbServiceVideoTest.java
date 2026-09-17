package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbVideo;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TmdbServiceVideoTest {

    @Test
    public void relatedVideosLoadsEpisodeSeasonAndSeriesThenDeduplicates() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            server.enqueue(response("{\"results\":[]}"));
            server.enqueue(response("{\"results\":[" + video("same_key", "Trailer", true, "zh-CN") + "]}"));
            server.enqueue(response("{\"results\":[" + video("same_key", "Clip", false, "en") + "," + video("other_key", "Teaser", false, "en") + "]}"));

            int tmdbId = 100000000 + (int) Math.abs(System.nanoTime() % 800000000L);
            TmdbItem item = new TmdbItem(tmdbId, "tv", "Example", "", "", "", "");
            TmdbConfig config = config(server.url("/3").toString(), "test-key", "zh-CN");

            List<TmdbVideo> videos = new TmdbService().relatedVideos(item, 2, 3, config);

            assertEquals(3, server.getRequestCount());
            assertEquals(2, videos.size());
            assertEquals("same_key", videos.get(0).getKey());
            assertEquals(TmdbVideo.Scope.SEASON, videos.get(0).getScope());
            assertEquals("other_key", videos.get(1).getKey());
        } finally {
            server.close();
        }
    }

    @Test
    public void movieRelatedVideosUsesOnlyTitleEndpoint() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            server.enqueue(response("{\"results\":[" + video("movie_key", "Trailer", true, "en") + "]}"));
            int tmdbId = 100000000 + (int) Math.abs(System.nanoTime() % 800000000L);
            TmdbItem item = new TmdbItem(tmdbId, "movie", "Example", "", "", "", "");
            TmdbConfig config = config(server.url("/3").toString(), "test-key", "en-US");

            List<TmdbVideo> videos = new TmdbService().relatedVideos(item, -1, -1, config);

            assertEquals(1, server.getRequestCount());
            assertEquals(1, videos.size());
            assertEquals(TmdbVideo.Scope.TITLE, videos.get(0).getScope());
        } finally {
            server.close();
        }
    }

    @Test
    public void videoCacheKeySeparatesScopeEpisodeAndLanguage() {
        TmdbService service = new TmdbService();
        TmdbItem item = new TmdbItem(123, "tv", "Example", "", "", "", "");
        TmdbConfig chinese = config("https://api.tmdb.org/3", "first-key", "zh-CN");
        TmdbConfig english = config("https://mirror.example.com/3", "second-key", "en-US");

        String season = service.videoCacheKey(item, TmdbVideo.Scope.SEASON, 2, -1, chinese);
        String episode = service.videoCacheKey(item, TmdbVideo.Scope.EPISODE, 2, 3, chinese);

        assertNotEquals(season, episode);
        assertNotEquals(episode, service.videoCacheKey(item, TmdbVideo.Scope.EPISODE, 2, 3, english));
        assertTrue(episode.contains("episode"));
    }

    private static MockResponse response(String body) {
        return new MockResponse.Builder().code(200).body(body).build();
    }

    private static String video(String key, String type, boolean official, String language) {
        return "{\"id\":\"id-" + key + "\",\"key\":\"" + key + "\",\"site\":\"YouTube\",\"name\":\"Example\",\"type\":\"" + type + "\",\"official\":" + official + ",\"iso_639_1\":\"" + language + "\",\"published_at\":\"2026-01-01T00:00:00.000Z\"}";
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