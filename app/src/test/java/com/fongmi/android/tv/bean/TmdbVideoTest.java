package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.setting.Setting;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TmdbVideoTest {

    @Test
    public void fromJsonAcceptsOnlySafeYoutubeVideoKeys() {
        TmdbVideo video = TmdbVideo.from(videoJson("abc_DEF-1", "YouTube", "Trailer", true, "zh-CN", "2026-01-02T03:04:05.000Z"), TmdbVideo.Scope.TITLE, -1, -1);

        assertNotNull(video);
        assertEquals("YouTube", video.getSite());
        assertEquals("abc_DEF-1", video.getKey());
        assertEquals("https://www.youtube.com/watch?v=abc_DEF-1", video.getWatchUrl());
        assertEquals("https://i.ytimg.com/vi/abc_DEF-1/hqdefault.jpg", video.getThumbnailUrl());
        assertEquals(TmdbVideo.Scope.TITLE, video.getScope());
        assertTrue(video.isOfficial());

        assertNull(TmdbVideo.from(videoJson("https://evil.example/file", "YouTube", "Trailer", false, "en", ""), TmdbVideo.Scope.TITLE, -1, -1));
        assertNull(TmdbVideo.from(videoJson("valid-key", "Vimeo", "Trailer", false, "en", ""), TmdbVideo.Scope.TITLE, -1, -1));
    }

    @Test
    public void mergeAndRankKeepsMostSpecificScopeAndStablePriority() {
        TmdbVideo titleDuplicate = TmdbVideo.from(videoJson("same_key", "youtube", "Clip", false, "en", "2025-01-01T00:00:00.000Z"), TmdbVideo.Scope.TITLE, -1, -1);
        TmdbVideo seasonDuplicate = TmdbVideo.from(videoJson("same_key", "YouTube", "Trailer", true, "zh-CN", "2024-01-01T00:00:00.000Z"), TmdbVideo.Scope.SEASON, 2, -1);
        TmdbVideo episodeDuplicate = TmdbVideo.from(videoJson("same_key", "YouTube", "Trailer", true, "zh-CN", "2023-01-01T00:00:00.000Z"), TmdbVideo.Scope.EPISODE, 2, 3);
        TmdbVideo other = TmdbVideo.from(videoJson("other_key", "YouTube", "Teaser", false, "en", "2026-01-01T00:00:00.000Z"), TmdbVideo.Scope.TITLE, -1, -1);

        List<TmdbVideo> result = TmdbVideo.mergeAndRank(List.of(titleDuplicate, seasonDuplicate, episodeDuplicate, other), "zh-CN", 12);

        assertEquals(2, result.size());
        assertEquals("same_key", result.get(0).getKey());
        assertEquals(TmdbVideo.Scope.EPISODE, result.get(0).getScope());
        assertEquals("other_key", result.get(1).getKey());
    }

    @Test
    public void displayTypeUsesChineseCategoryNames() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
            assertEquals("预告片", videoType("Trailer").getDisplayType());
            assertEquals("先导预告", videoType("Teaser").getDisplayType());
            assertEquals("片段", videoType("Clip").getDisplayType());
            assertEquals("制作特辑", videoType("Featurette").getDisplayType());
            assertEquals("幕后花絮", videoType("Behind the Scenes").getDisplayType());
            assertEquals("剧情回顾", videoType("Recap").getDisplayType());
            assertEquals("NG 花絮", videoType("Bloopers").getDisplayType());
            assertEquals("片头", videoType("Opening Credits").getDisplayType());
            assertEquals("视频", videoType("Unknown").getDisplayType());
            assertEquals("视频", videoType("").getDisplayType());

            Locale.setDefault(Locale.TRADITIONAL_CHINESE);
            assertEquals("預告片", videoType("Trailer").getDisplayType());
            assertEquals("前導預告", videoType("Teaser").getDisplayType());
            assertEquals("製作特輯", videoType("Featurette").getDisplayType());
            assertEquals("幕後花絮", videoType("Behind the Scenes").getDisplayType());
            assertEquals("劇情回顧", videoType("Recap").getDisplayType());
            assertEquals("片頭", videoType("Opening Credits").getDisplayType());
            assertEquals("視頻", videoType("Unknown").getDisplayType());

            Locale.setDefault(Locale.ENGLISH);
            assertEquals("Trailer", videoType("Trailer").getDisplayType());
            assertEquals("Unknown", videoType("Unknown").getDisplayType());
            assertEquals("Video", videoType("").getDisplayType());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void displayTypeUsesApplicationLanguageBeforeSystemLocale() {
        assertEquals("预告片", videoType("Trailer").getDisplayType(Setting.LANGUAGE_SIMPLIFIED, Locale.ENGLISH));
        assertEquals("預告片", videoType("Trailer").getDisplayType(Setting.LANGUAGE_TRADITIONAL, Locale.SIMPLIFIED_CHINESE));
        assertEquals("Trailer", videoType("Trailer").getDisplayType(Setting.LANGUAGE_FOLLOW_SYSTEM, Locale.ENGLISH));
    }

    private static TmdbVideo videoType(String type) {
        return TmdbVideo.from(videoJson("type_key", "YouTube", type, false, "en", ""), TmdbVideo.Scope.TITLE, -1, -1);
    }

    private static JsonObject videoJson(String key, String site, String type, boolean official, String language, String publishedAt) {
        JsonObject object = new JsonObject();
        object.addProperty("id", "video-" + key);
        object.addProperty("key", key);
        object.addProperty("site", site);
        object.addProperty("name", "Example video");
        object.addProperty("type", type);
        object.addProperty("official", official);
        object.addProperty("size", 1080);
        object.addProperty("iso_639_1", language);
        object.addProperty("iso_3166_1", "US");
        object.addProperty("published_at", publishedAt);
        return object;
    }
}
