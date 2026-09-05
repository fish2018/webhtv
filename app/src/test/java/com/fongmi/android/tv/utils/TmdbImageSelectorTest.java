package com.fongmi.android.tv.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TmdbImageSelectorTest {

    @Test
    public void backgrounds_prefersLargestLandscapeOriginal() {
        JsonObject detail = detail("""
                {
                  "images": {
                    "backdrops": [
                      {"file_path": "/small.jpg", "width": 1280, "height": 720, "vote_average": 9.0, "vote_count": 50},
                      {"file_path": "/large.jpg", "width": 3840, "height": 2160, "vote_average": 6.0, "vote_count": 5}
                    ],
                    "posters": [
                      {"file_path": "/poster.jpg", "width": 2000, "height": 3000, "vote_average": 10.0, "vote_count": 100}
                    ]
                  },
                  "backdrop_path": "/fallback_backdrop.jpg",
                  "poster_path": "/fallback_poster.jpg"
                }
                """);

        List<String> urls = TmdbImageSelector.backgrounds(detail, "https://image.tmdb.org/t/p/w500", "https://image.tmdb.org/t/p/w780", true, 2);

        assertEquals("https://image.tmdb.org/t/p/original/large.jpg", urls.get(0));
        assertEquals("https://image.tmdb.org/t/p/original/small.jpg", urls.get(1));
    }

    @Test
    public void backgrounds_prefersPortraitWhenRequested() {
        JsonObject detail = detail("""
                {
                  "images": {
                    "backdrops": [
                      {"file_path": "/wide.jpg", "width": 3840, "height": 2160}
                    ],
                    "posters": [
                      {"file_path": "/poster.jpg", "width": 2000, "height": 3000}
                    ]
                  }
                }
                """);

        List<String> urls = TmdbImageSelector.backgrounds(detail, "https://image.tmdb.org/t/p/w500", "https://image.tmdb.org/t/p/w780", false, 1);

        assertEquals(List.of("https://image.tmdb.org/t/p/original/poster.jpg"), urls);
    }

    @Test
    public void backgrounds_fallsBackWhenPreferredOrientationIsMissing() {
        JsonObject detail = detail("""
                {
                  "images": {
                    "backdrops": [
                      {"file_path": "/wide.jpg", "width": 1920, "height": 1080}
                    ],
                    "posters": []
                  }
                }
                """);

        List<String> urls = TmdbImageSelector.backgrounds(detail, "https://image.tmdb.org/t/p/w500", "https://image.tmdb.org/t/p/w780", false, 1);

        assertEquals(List.of("https://image.tmdb.org/t/p/original/wide.jpg"), urls);
    }

    @Test
    public void backdrops_excludePostersAndKeepResolutionOrdering() {
        JsonObject detail = detail("""
                {
                  "images": {
                    "backdrops": [
                      {"file_path": "/small_backdrop.jpg", "width": 1280, "height": 720},
                      {"file_path": "/large_backdrop.jpg", "width": 3840, "height": 2160}
                    ],
                    "posters": [
                      {"file_path": "/poster.jpg", "width": 2000, "height": 3000}
                    ]
                  }
                }
                """);

        List<String> urls = TmdbImageSelector.backdrops(detail, "https://image.tmdb.org/t/p/w780", 10);

        assertEquals(List.of(
                "https://image.tmdb.org/t/p/original/large_backdrop.jpg",
                "https://image.tmdb.org/t/p/original/small_backdrop.jpg"), urls);
    }

    @Test
    public void posters_excludeBackdropsAndKeepResolutionOrdering() {
        JsonObject detail = detail("""
                {
                  "images": {
                    "backdrops": [
                      {"file_path": "/backdrop.jpg", "width": 3840, "height": 2160}
                    ],
                    "posters": [
                      {"file_path": "/small_poster.jpg", "width": 1000, "height": 1500},
                      {"file_path": "/large_poster.jpg", "width": 2000, "height": 3000}
                    ]
                  }
                }
                """);

        List<String> urls = TmdbImageSelector.posters(detail, "https://image.tmdb.org/t/p/w500", 10);

        assertEquals(List.of(
                "https://image.tmdb.org/t/p/original/large_poster.jpg",
                "https://image.tmdb.org/t/p/original/small_poster.jpg"), urls);
    }
    @Test
    public void originalUrl_replacesTmdbSizeSegment() {
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", TmdbImageSelector.originalUrl("https://image.tmdb.org/t/p/w780/abc.jpg"));
    }

    @Test
    public void originalUrl_handlesHeightAndAlreadyOriginalSegments() {
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", TmdbImageSelector.originalUrl("https://image.tmdb.org/t/p/h632/abc.jpg"));
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", TmdbImageSelector.originalUrl("https://image.tmdb.org/t/p/original/abc.jpg"));
    }

    @Test
    public void cardImage_usesRequestedOrientationWithOppositeFallback() {
        com.fongmi.android.tv.bean.TmdbItem item = new com.fongmi.android.tv.bean.TmdbItem(
                1, "movie", "测试影片", "2024", "", "poster.jpg", "backdrop.jpg");

        assertEquals("poster.jpg", TmdbImageSelector.cardImage(item, false));
        assertEquals("backdrop.jpg", TmdbImageSelector.cardImage(item, true));
        assertEquals("backdrop.jpg", TmdbImageSelector.cardImage(item, !false));
        assertEquals("poster.jpg", TmdbImageSelector.cardImage(item, !true));
    }


    private JsonObject detail(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
