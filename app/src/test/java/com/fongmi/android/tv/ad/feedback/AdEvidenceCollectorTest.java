package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.M3u8Evidence;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AdEvidenceCollectorTest {

    private static final AdEvidenceCollector.Context CONTEXT = new AdEvidenceCollector.Context(
            "site", "站点", "剧名", "线路", "第 1 集",
            "https://v.example.com/play/index.m3u8?token=secret", true);

    /** 10 个切片，每个 10s；下标 3-5 来自广告域名。 */
    private static M3u8Evidence tenSegments(List<Integer> discontinuities) {
        List<String> segments = new ArrayList<>();
        List<Float> durations = new ArrayList<>();
        List<Boolean> switches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean ad = i >= 3 && i <= 5;
            segments.add(ad
                    ? "https://ad-cdn.other.com/creative/" + i + ".ts?sid=x"
                    : "https://v.example.com/seg/" + i + ".ts");
            durations.add(10f);
            switches.add(ad);
        }
        return M3u8Evidence.create(segments, discontinuities, durations, switches);
    }

    @Test
    public void splitsSegmentsIntoInsideAndOutside() {
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of()), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of(), false);

        assertEquals(List.of(3, 4, 5), evidence.inside().stream().map(SegmentFact::index).toList());
        assertEquals(7, evidence.outside().size());
        assertTrue(evidence.hasSegmentEvidence());
    }

    @Test
    public void stripsQueryFromPlaylistAndSegmentUrls() {
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of()), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of(), false);

        // 播放地址的 token 不得进入证据
        assertEquals("/play/index.m3u8", evidence.urlPath());
        assertEquals("v.example.com", evidence.playlistHost());
        assertEquals("/creative/3.ts", evidence.inside().get(0).path());
        assertTrue(evidence.inside().stream().noneMatch(fact -> fact.path().contains("sid=")));
    }

    @Test
    public void detectsCrossDomainWhenAllInsideSegmentsAreForeign() {
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of()), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of(), false);

        assertTrue(evidence.crossDomain());
    }

    @Test
    public void noCrossDomainWhenIntervalMixesHosts() {
        // 区间跨到正片切片上，不能整体判为跨域
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of()), 30_000, 80_000,
                StartOrigin.USER_MARKED, List.of(), false);

        assertFalse(evidence.crossDomain());
    }

    @Test
    public void boundedByDiscontinuityRequiresBothEnds() {
        // 断点在 3（广告块开始）与 6（正片恢复）
        AdIntervalEvidence both = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of(3, 6)), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of(), false);
        assertTrue(both.boundedByDiscontinuity());

        // 只有首端有断点
        AdIntervalEvidence headOnly = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of(3)), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of(), false);
        assertFalse(headOnly.boundedByDiscontinuity());
    }

    @Test
    public void intervalReachingPlaylistEndCountsTailAsBounded() {
        // 区间到最后一个切片，没有「之后的切片」可带断点
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of(7)), 70_000, 100_000,
                StartOrigin.USER_MARKED, List.of(), false);

        assertTrue(evidence.boundedByDiscontinuity());
    }

    @Test
    public void recordsBlacklistMatches() {
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, tenSegments(List.of()), 30_000, 60_000,
                StartOrigin.USER_MARKED, List.of("ad-cdn.other.com"), false);

        assertEquals(List.of("ad-cdn.other.com"), evidence.matchedExistingHosts());
    }

    @Test
    public void keepsEveryOutsideSegmentForLongPlaylists() {
        // 区间外必须完整保留：分类器用它做「区间外不含某特征」的全局否定判断，
        // 截断样本会让 playlist 后半段的反例看不见，从而生成命中全站的规则。
        List<String> segments = new ArrayList<>();
        List<Float> durations = new ArrayList<>();
        List<Boolean> switches = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            segments.add("https://v.example.com/seg/" + i + ".ts");
            durations.add(10f);
            switches.add(false);
        }
        M3u8Evidence long_ = M3u8Evidence.create(segments, List.of(), durations, switches);

        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, long_, 30_000, 60_000, StartOrigin.USER_MARKED, List.of(), false);

        assertEquals(3, evidence.inside().size());
        assertEquals(497, evidence.outside().size());
    }

    @Test
    public void handlesMissingPlaylistEvidence() {
        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, null, 30_000, 60_000, StartOrigin.FALLBACK_WINDOW, List.of(), true);

        assertTrue(evidence.inside().isEmpty());
        assertFalse(evidence.hasSegmentEvidence());
        assertFalse(evidence.crossDomain());
        assertTrue(evidence.legacyHeuristicActive());
        // 上下文仍然保留
        assertEquals("v.example.com", evidence.playlistHost());
    }

    @Test
    public void handlesEmptyEvidenceAndNonHlsContext() {
        AdEvidenceCollector.Context nonHls = new AdEvidenceCollector.Context(
                "site", "站点", "剧名", "线路", "第 1 集",
                "https://v.example.com/play/video.mp4", false);

        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                nonHls, M3u8Evidence.empty(), 30_000, 60_000,
                StartOrigin.FALLBACK_WINDOW, List.of(), false);

        assertFalse(evidence.hls());
        assertFalse(evidence.hasSegmentEvidence());
    }

    @Test
    public void relativeSegmentUriHasNoHostButKeepsPath() {
        M3u8Evidence relative = M3u8Evidence.create(
                List.of("seg/0.ts", "seg/1.ts", "seg/2.ts"),
                List.of(), List.of(10f, 10f, 10f), List.of(false, false, false));

        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, relative, 0, 10_000, StartOrigin.USER_MARKED, List.of(), false);

        assertEquals("", evidence.inside().get(0).host());
        assertEquals("seg/0.ts", evidence.inside().get(0).path());
        // 无 host 时不能判为跨域
        assertFalse(evidence.crossDomain());
    }

    @Test
    public void toleratesDurationsShorterThanSegments() {
        // durations 缺项时按较短的长度处理，不抛异常
        M3u8Evidence mismatched = M3u8Evidence.create(
                List.of("a.ts", "b.ts", "c.ts"),
                List.of(), List.of(10f, 10f), List.of(false, false));

        AdIntervalEvidence evidence = AdEvidenceCollector.collect(
                CONTEXT, mismatched, 0, 10_000, StartOrigin.USER_MARKED, List.of(), false);

        assertEquals(1, evidence.inside().size());
        assertEquals(1, evidence.outside().size());
    }
}
