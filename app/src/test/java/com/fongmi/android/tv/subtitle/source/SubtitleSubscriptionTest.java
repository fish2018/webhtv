package com.fongmi.android.tv.subtitle.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SubtitleSubscriptionTest {

    @Test
    public void removesInvalidAndDuplicateSourcesAndSortsPriority() {
        SubtitleSubscription value = SubtitleSubscription.parse("{\"version\":1,\"subtitles\":[" +
                "{\"key\":\"low\",\"name\":\"Low\",\"type\":3,\"api\":\"py\",\"ext\":\"low.py\",\"priority\":1}," +
                "{\"key\":\"high\",\"name\":\"High\",\"type\":3,\"api\":\"py\",\"ext\":\"high.py\",\"priority\":9}," +
                "{\"key\":\"low\",\"name\":\"Duplicate Low\",\"type\":3,\"api\":\"py\",\"ext\":\"duplicate.py\"}," +
                "{\"key\":\"broken\",\"name\":\"Broken\",\"type\":3,\"api\":\"py\"}]}", "https://example.com/sub/");
        assertEquals(2, value.getSubtitles().size());
        assertEquals("high", value.getSubtitles().get(0).getKey());
        assertEquals("low", value.getSubtitles().get(1).getKey());
    }

    @Test
    public void preservesAssetUrisWhenResolvingScriptAddresses() {
        assertEquals("assets://subtitle_sources/builtin/xunlei.py",
                SubtitleSubscription.resolve("assets://subtitle_sources/builtin/manifest.json", "assets://subtitle_sources/builtin/xunlei.py"));
    }

    @Test
    public void rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> SubtitleSubscription.parse("{\"version\":2,\"subtitles\":[]}", "https://example.com/"));
    }
}
