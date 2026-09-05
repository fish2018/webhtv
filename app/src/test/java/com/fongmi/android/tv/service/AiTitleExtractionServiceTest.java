package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolution;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiTitleExtractionServiceTest {

    @Test
    public void buildPrompt_includesRelevantLearningExamples() {
        MediaTitleLearningExample example = MediaTitleLearningExample.manual(
                "qyn 第二季",
                "qyn",
                "庆余年",
                "tv",
                0,
                2,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);
        MediaTitleRequest request = MediaTitleRequest.builder()
                .rawTitle("qyn 第二季 4K 更新至18集")
                .learningExamples(List.of(example))
                .build();
        MediaTitleResolution rule = new MediaTitleParser().parse(request);

        String prompt = AiTitleExtractionService.buildPrompt(request, rule);

        assertTrue(prompt.contains("\"expectedTitle\":\"庆余年\""));
        assertTrue(prompt.contains("\"rawTitle\":\"qyn 第二季 4K 更新至18集\""));
    }

    @Test
    public void buildPrompt_usesConfiguredTitleExtractionPromptAndAppendsInputJson() {
        AiConfig config = AiConfig.objectFrom("{}");
        config.setTitleExtractionPrompt("自定义名称提取提示词：把 F 凡人#修仙传 动漫 B 提取为凡人修仙传");
        MediaTitleRequest request = MediaTitleRequest.builder()
                .rawTitle("F 凡人#修仙传 动漫 B")
                .searchKeyword("凡人修仙传")
                .build();
        MediaTitleResolution rule = new MediaTitleParser().parse(request);

        String prompt = AiTitleExtractionService.buildPrompt(config, request, rule);

        assertTrue(prompt.startsWith("自定义名称提取提示词"));
        assertTrue(prompt.contains("输入 JSON:"));
        assertTrue(prompt.contains("\"rawTitle\":\"F 凡人#修仙传 动漫 B\""));
        assertTrue(prompt.contains("\"searchKeyword\":\"凡人修仙传\""));
    }

    @Test
    public void parseResponse_acceptsStrictJsonAndAddsAliasCandidates() {
        MediaTitleResolution rule = new MediaTitleParser().parse(MediaTitleRequest.builder()
                .rawTitle("qyn 第二季 4K")
                .build());

        MediaTitleResolution result = AiTitleExtractionService.parseResponse("{\"canonicalTitle\":\"庆余年\",\"mediaType\":\"tv\",\"seasonNumber\":2,\"aliases\":[\"庆余年 第二季\"],\"confidence\":0.91}", rule);

        assertNotNull(result);
        assertEquals("庆余年", result.getCanonicalTitle());
        assertEquals("tv", result.getMediaType());
        assertEquals(2, result.getSeasonNumber());
        assertTrue(result.getAliases().contains("庆余年 第二季"));
    }

    @Test
    public void parseResponse_rejectsNoisyTitle() {
        MediaTitleResolution rule = new MediaTitleParser().parse(MediaTitleRequest.builder()
                .rawTitle("庆余年2 S02E05 4K")
                .build());

        MediaTitleResolution result = AiTitleExtractionService.parseResponse("{\"canonicalTitle\":\"庆余年 4K 第5集\",\"confidence\":0.95}", rule);

        assertNull(result);
    }
}
