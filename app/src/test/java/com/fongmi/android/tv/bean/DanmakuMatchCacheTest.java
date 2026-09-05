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
}
