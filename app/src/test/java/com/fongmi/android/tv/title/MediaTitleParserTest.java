package com.fongmi.android.tv.title;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediaTitleParserTest {

    @Test
    public void parse_removesNetworkDriveNoiseAndExtractsEpisodeSignals() {
        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("庆余年2 S02E05 4K 高码 国语中字 更新至18集")
                        .rawRemarks("更新至18集")
                        .episodeName("第05集")
                        .build());

        assertEquals("庆余年", resolution.getCanonicalTitle());
        assertEquals(2, resolution.getSeasonNumber());
        assertEquals(5, resolution.getEpisodeNumber());
        assertTrue(resolution.getConfidence() >= 0.7f);
    }

    @Test
    public void candidates_prioritizeLearningExampleOverRuleTitle() {
        MediaTitleLearningExample example = MediaTitleLearningExample.manual(
                "qyn 第二季",
                "qyn",
                "庆余年",
                "tv",
                0,
                2,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);

        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("qyn 第二季 防和谐版")
                        .learningExamples(List.of(example))
                        .build());

        assertEquals("庆余年", resolution.getCanonicalTitle());
        assertEquals("庆余年", resolution.getCandidates().get(0).getTitle());
        assertTrue(resolution.getConfidence() >= 0.9f);
    }

    @Test
    public void candidates_chooseMostSimilarLearningExampleWhenSeveralExist() {
        MediaTitleLearningExample unrelated = MediaTitleLearningExample.manual(
                "abc",
                "abc",
                "错误标题",
                "tv",
                0,
                -1,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);
        MediaTitleLearningExample related = MediaTitleLearningExample.manual(
                "qyn 第二季",
                "qyn",
                "庆余年",
                "tv",
                0,
                2,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);

        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("qyn 第二季 防和谐版")
                        .learningExamples(List.of(unrelated, related))
                        .build());

        assertEquals("庆余年", resolution.getCanonicalTitle());
        assertEquals("庆余年", resolution.getCandidates().get(0).getTitle());
    }

    @Test
    public void parse_marksHarmonySeparatedPushTitleAsLowConfidence() {
        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("F 凡人#修仙传 动漫 A")
                        .build());

        assertEquals("凡人修仙传", resolution.getCanonicalTitle());
        assertTrue(resolution.getConfidence() < 0.75f);
    }

    @Test
    public void parse_removesDifferentTrailingPushMarker() {
        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("F 凡人#修仙传 动漫 B")
                        .build());

        assertEquals("凡人修仙传", resolution.getCanonicalTitle());
    }

    @Test
    public void cleanTitle_removesTrueColorSourceTag() {
        MediaTitleParser parser = new MediaTitleParser();

        assertEquals("云秀行", parser.cleanTitle("云秀行（真彩）"));
        assertEquals("云秀行", parser.normalizeSearchText("云秀行（真彩）"));
    }

    @Test
    public void cleanSearchTitles_extractsEffectiveTitleFromSourceAndQualityNoise() {
        List<String> titles = new MediaTitleParser().cleanSearchTitles(
                MediaTitleRequest.builder()
                        .rawTitle("玩偶 | 虎斑2 云秀行（真彩） 4K HDR 60帧 更新至第20集")
                        .build());

        assertEquals(List.of("云秀行"), titles);
    }

    @Test
    public void cleanSearchTitles_prefersTitleBracketOverReleaseGroupBracket() {
        List<String> titles = new MediaTitleParser().cleanSearchTitles(
                MediaTitleRequest.builder()
                        .rawTitle("[玩偶][云秀行][01][4K].mkv")
                        .build());

        assertEquals(List.of("云秀行"), titles);
    }

    @Test
    public void candidates_doNotReuseLearningOnlyBecauseBothTitlesHaveTrueColorTag() {
        MediaTitleLearningExample unrelated = MediaTitleLearningExample.manual(
                "千香（真彩）",
                "千香",
                "千香",
                "tv",
                0,
                -1,
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);

        MediaTitleResolution resolution = new MediaTitleParser().parse(
                MediaTitleRequest.builder()
                        .rawTitle("云秀行（真彩）")
                        .learningExamples(List.of(unrelated))
                        .build());

        assertEquals("云秀行", resolution.getCanonicalTitle());
    }
}
