package com.fongmi.android.tv.title;

import com.fongmi.android.tv.bean.AiConfig;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;

public class MediaTitleCacheTest {

    @Test
    public void key_changesWhenTitleExtractionPromptChanges() {
        MediaTitleRequest request = MediaTitleRequest.builder()
                .siteKey("site")
                .vodId("vod")
                .rawTitle("F 凡人#修仙传 动漫 B")
                .build();
        AiConfig first = AiConfig.objectFrom("{}");
        AiConfig second = AiConfig.objectFrom("{}");
        second.setTitleExtractionPrompt("优先把 # 分隔的中文片名合并");

        MediaTitleCache cache = new MediaTitleCache();

        assertNotEquals(cache.key(request, first), cache.key(request, second));
    }

    @Test
    public void key_changesWhenSearchKeywordChanges() {
        MediaTitleRequest firstRequest = MediaTitleRequest.builder()
                .siteKey("site")
                .vodId("vod")
                .rawTitle("同一卡片标题 4K")
                .searchKeyword("第一个搜索词")
                .build();
        MediaTitleRequest secondRequest = MediaTitleRequest.builder()
                .siteKey("site")
                .vodId("vod")
                .rawTitle("同一卡片标题 4K")
                .searchKeyword("第二个搜索词")
                .build();
        AiConfig config = AiConfig.objectFrom("{}");

        MediaTitleCache cache = new MediaTitleCache();

        assertNotEquals(cache.key(firstRequest, config), cache.key(secondRequest, config));
    }

    @Test
    public void key_changesWhenRecognitionContextChanges() {
        MediaTitleRequest firstRequest = MediaTitleRequest.builder()
                .rawTitle("qyn 第二季")
                .folderName("/media/庆余年")
                .contextTitles(java.util.List.of("庆余年 第01集"))
                .build();
        MediaTitleRequest secondRequest = MediaTitleRequest.builder()
                .rawTitle("qyn 第二季")
                .folderName("/media/另一部剧")
                .contextTitles(java.util.List.of("另一部剧 第01集"))
                .build();

        MediaTitleCache cache = new MediaTitleCache();
        AiConfig config = AiConfig.objectFrom("{}");

        assertNotEquals(cache.key(firstRequest, config), cache.key(secondRequest, config));
    }

    @Test
    public void request_contextIsBoundedAndFolderIsReducedToBasename() {
        MediaTitleRequest request = MediaTitleRequest.builder()
                .folderName("/private/path/庆余年/")
                .contextTitles(List.of("relative/path/title-0.mkv", "/private/path/title-1.mkv"))
                .build();

        assertEquals("庆余年", request.getFolderName());
        assertEquals(2, request.getContextTitles().size());
        assertEquals("title-0.mkv", request.getContextTitles().get(0));
    }

    @Test
    public void request_contextTitlesAreLimitedToSixteenEntries() {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) values.add("/private/path/title-" + i + ".mkv");

        MediaTitleRequest request = MediaTitleRequest.builder().contextTitles(values).build();

        assertEquals(16, request.getContextTitles().size());
        assertEquals("title-0.mkv", request.getContextTitles().get(0));
    }

    @Test
    public void key_changesWhenPromptRecognitionInputsChange() {
        MediaTitleRequest base = MediaTitleRequest.builder()
                .rawTitle("庆余年 S02E05")
                .build();
        MediaTitleRequest remarks = MediaTitleRequest.builder()
                .rawTitle("庆余年 S02E05")
                .rawRemarks("更新至05集")
                .build();
        MediaTitleRequest year = MediaTitleRequest.builder()
                .rawTitle("庆余年 S02E05")
                .vodYear("2024")
                .build();
        MediaTitleRequest learning = MediaTitleRequest.builder()
                .rawTitle("庆余年 S02E05")
                .learningExamples(List.of(MediaTitleLearningExample.manual(
                        "qyn", "qyn", "庆余年", "tv", 0, 2,
                        MediaTitleLearningExample.SOURCE_TMDB_MANUAL)))
                .build();

        MediaTitleCache cache = new MediaTitleCache();
        AiConfig config = AiConfig.objectFrom("{}");

        assertNotEquals(cache.key(base, config), cache.key(remarks, config));
        assertNotEquals(cache.key(base, config), cache.key(year, config));
        assertNotEquals(cache.key(base, config), cache.key(learning, config));
    }
}
