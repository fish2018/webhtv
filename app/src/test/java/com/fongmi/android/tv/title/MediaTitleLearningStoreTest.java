package com.fongmi.android.tv.title;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MediaTitleLearningStoreTest {

    @Test
    public void find_prioritizesExactIdentityAndSimilarRuleTitle() {
        MediaTitleLearningStore store = new MediaTitleLearningStore();
        store.getItems().put("low", MediaTitleLearningExample.manual(
                "abc",
                "abc",
                "错误标题",
                "tv",
                0,
                -1,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL));
        store.getItems().put("exact", MediaTitleLearningExample.manual(
                "qyn 第二季",
                "qyn",
                "庆余年",
                "tv",
                0,
                2,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL).identity("site-a", "vod-1"));

        List<MediaTitleLearningExample> examples = store.find(MediaTitleRequest.builder()
                .siteKey("site-a")
                .vodId("vod-1")
                .rawTitle("qyn 第二季 防和谐版")
                .build(), 2);

        assertEquals("庆余年", examples.get(0).getExpectedTitle());
    }
    @Test
    public void objectFrom_restoresObfuscatedReleaseCacheAsExamples() {
        MediaTitleLearningStore store = MediaTitleLearningStore.objectFrom("{\"a\":{\"key\":{\"a\":\"raw\",\"b\":\"rule\",\"c\":\"expected\",\"d\":\"tv\",\"e\":\"site\",\"f\":\"vod\",\"i\":\"TMDB_MANUAL\",\"j\":1,\"k\":1,\"l\":true}}}");

        assertEquals("expected", store.find(MediaTitleRequest.builder().siteKey("site").vodId("vod").rawTitle("raw").build(), 1).get(0).getExpectedTitle());
    }
}
