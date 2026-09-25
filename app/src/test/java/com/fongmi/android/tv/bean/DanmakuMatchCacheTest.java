package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.title.MediaTitleLearningExample;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DanmakuMatchCacheTest {

    @Test
    public void cache_roundTripsSelectedDanmakuForSameEpisode() {
        Danmaku item = Danmaku.from("https://example.test/danmaku.xml");
        item.setName("庆余年(2024) - 第5集 from B站");

        DanmakuMatchCache cache = new DanmakuMatchCache();
        cache.put("site", "vod-1", "第05集", "qyn", "qyn 第二季", item);

        Danmaku cached = cache.find("site", "vod-1", "第5集");

        assertNotNull(cached);
        assertEquals("https://example.test/danmaku.xml", cached.getUrl());
        assertEquals("庆余年(2024) - 第5集 from B站", cached.getName());
    }

    @Test
    public void cacheEntry_exportsLearningExampleWithoutUrl() {
        Danmaku item = Danmaku.from("https://example.test/danmaku.xml");
        item.setName("庆余年(2024) - 第5集 from B站");

        DanmakuMatchCache cache = new DanmakuMatchCache();
        cache.put("site", "vod-1", "第05集", "qyn", "qyn 第二季", item);

        MediaTitleLearningExample example = cache.learningExamples("qyn").get(0);

        assertEquals("qyn", example.getRuleTitle());
        assertEquals("庆余年", example.getExpectedTitle());
        assertEquals(MediaTitleLearningExample.SOURCE_DANMAKU_MANUAL, example.getSource());
    }

    @Test
    public void seriesMemory_keepsManualKeywordForLaterEpisodes() {
        Danmaku item = danmaku("https://example.test/current.xml");

        DanmakuMatchCache cache = new DanmakuMatchCache();
        cache.putSeries("site", "vod-1", "qyn 第二季", "庆余年", item);

        assertEquals("qyn 第二季", cache.findSeriesSearchTitle("site", "vod-1"));
        assertEquals("", cache.findSeriesSearchTitle("site", "vod-2"));
        assertEquals("", cache.findSeriesSearchTitle("other", "vod-1"));
    }

    @Test
    public void seriesMemory_overwritesPreviousManualKeyword() {
        DanmakuMatchCache cache = new DanmakuMatchCache();
        cache.putSeries("site", "vod-1", "旧关键词", "旧标题", danmaku("https://example.test/old.xml"));
        cache.putSeries("site", "vod-1", "新关键词", "新标题", danmaku("https://example.test/new.xml"));

        assertEquals("新关键词", cache.findSeriesSearchTitle("site", "vod-1"));
    }

    @Test
    public void tmdbSeasonMemory_reusesKeywordAndIsolatedFromSeriesMemory() {
        DanmakuMatchCache cache = new DanmakuMatchCache();
        cache.putTmdbSeason(123, 2, "qyn 第二季", "庆余年", danmaku("https://example.test/current.xml"));

        assertEquals("qyn 第二季", cache.findTmdbSeasonSearchTitle(123, 2));
        assertEquals("", cache.findTmdbSeasonSearchTitle(123, 3));
        assertEquals("", cache.findTmdbSeasonSearchTitle(456, 2));
        assertEquals("", cache.findSeriesSearchTitle("site", "vod-1"));
    }

    @Test
    public void manualMemory_rejectsInvalidIdentityAndBlankKeyword() {
        DanmakuMatchCache cache = new DanmakuMatchCache();
        Danmaku item = danmaku("https://example.test/current.xml");

        cache.putSeries("", "vod-1", "keyword", "title", item);
        cache.putSeries("site", "", "keyword", "title", item);
        cache.putSeries("site", "vod-1", "", "title", null);
        cache.putTmdbSeason(0, 1, "keyword", "title", item);
        cache.putTmdbSeason(123, 0, "keyword", "title", null);

        assertEquals(0, cache.getSeriesItems().size());
        assertEquals(0, cache.getTmdbSeasonItems().size());
    }

    @Test
    public void manualMemory_fallsBackToRawTitleWhenSearchTitleIsBlank() {
        DanmakuMatchCache cache = new DanmakuMatchCache();

        cache.putSeries("site", "vod-1", "", "庆余年 第二季", danmaku("https://example.test/current.xml"));

        assertEquals("庆余年 第二季", cache.findSeriesSearchTitle("site", "vod-1"));
    }

    @Test
    public void objectFrom_acceptsLegacyJsonWithoutSeriesOrTmdbMaps() {
        DanmakuMatchCache cache = DanmakuMatchCache.objectFrom("{\"items\":{}}");

        assertEquals(0, cache.getSeriesItems().size());
        assertEquals(0, cache.getTmdbSeasonItems().size());
    }

    private static Danmaku danmaku(String url) {
        Danmaku item = Danmaku.from(url);
        item.setName("庆余年(2024) - 第5集 from B站");
        return item;
    }
}
