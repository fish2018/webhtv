package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HlsSegmentClassifierTest {

    private static final String PLAYLIST_HOST = "v.example.com";

    /** 构造区间内外切片：inside 用广告域名，outside 用 playlist 域名。 */
    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside) {
        return evidence(inside, outside, false, List.of());
    }

    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside,
                                               boolean bounded, List<String> removedRuleIds) {
        return new AdIntervalEvidence(
                10_000, 40_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/index.m3u8", true,
                inside, outside, bounded, false,
                removedRuleIds, false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    private static List<SegmentFact> segments(int from, int count, String host, String pathPrefix,
                                             double duration, boolean discontinuityOnFirst) {
        List<SegmentFact> facts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            facts.add(new SegmentFact(from + i, host, pathPrefix + (from + i) + ".ts",
                    duration, discontinuityOnFirst && i == 0));
        }
        return facts;
    }

    /** 区间外切片分成前后两段，避免与区间内下标重叠。 */
    private static List<SegmentFact> outsideAround(int insideFrom, int insideCount, int tailCount,
                                                   String host, double duration) {
        List<SegmentFact> facts = new ArrayList<>(segments(0, insideFrom, host, "/seg/", duration, false));
        facts.addAll(segments(insideFrom + insideCount, tailCount, host, "/seg/", duration, false));
        return facts;
    }

    @Test
    public void crossDomainWithDiscontinuityIsHighConfidence() {
        // 区间在中段（下标 10-14，总 25 片），位置信号不参与
        AdIntervalEvidence evidence = evidence(
                segments(10, 5, "ad-cdn.other.com", "/seg/", 6.4, true),
                outsideAround(10, 5, 10, PLAYLIST_HOST, 8.0));

        AdAttribution attribution = HlsSegmentClassifier.classify(evidence);

        assertNotNull(attribution);
        assertEquals(AdCategory.THIRD_PARTY_CDN_SEGMENT, attribution.category());
        assertEquals(RemediationKind.HLS_STRUCTURED_RULE, attribution.remediation());
        // 跨域 0.35 + 断点 0.25 + 时长离群 0.20 = 0.80
        assertEquals(0.80f, attribution.confidence(), 0.001f);
        assertTrue(attribution.actionable());
        assertFalse(HlsSegmentClassifier.detect(evidence).headPosition());
    }

    @Test
    public void headPositionAddsSmallWeightOnTopOfStructuralSignals() {
        // 同样三个结构信号，但区间在头部：额外 +0.05
        AdIntervalEvidence evidence = evidence(
                segments(3, 5, "ad-cdn.other.com", "/seg/", 6.4, true),
                outsideAround(3, 5, 17, PLAYLIST_HOST, 8.0));

        AdAttribution attribution = HlsSegmentClassifier.classify(evidence);

        assertNotNull(attribution);
        assertEquals(0.85f, attribution.confidence(), 0.001f);
    }

    @Test
    public void sameDomainThroughoutIsNotCrossDomainEvidence() {
        // 整条 playlist 同域：这是该站正常结构，跨域信号必须不成立
        AdIntervalEvidence evidence = evidence(
                segments(3, 5, PLAYLIST_HOST, "/seg/", 8.0, false),
                segments(0, 20, PLAYLIST_HOST, "/seg/", 8.0, false));

        HlsSegmentClassifier.Signals signals = HlsSegmentClassifier.detect(evidence);

        assertFalse(signals.crossDomain());
        // 无任何信号，整体弃权
        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void crossDomainRequiresOutsideContrast() {
        // 区间外为空，无法形成对照
        AdIntervalEvidence evidence = evidence(
                segments(0, 5, "ad-cdn.other.com", "/seg/", 6.4, false),
                List.of());

        assertFalse(HlsSegmentClassifier.detect(evidence).crossDomain());
    }

    @Test
    public void subdomainOfPlaylistHostCountsAsSameDomain() {
        AdIntervalEvidence evidence = evidence(
                segments(3, 3, "cdn." + PLAYLIST_HOST, "/seg/", 8.0, false),
                segments(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));

        assertFalse(HlsSegmentClassifier.detect(evidence).crossDomain());
    }

    @Test
    public void durationOutlierNeedsUniformInsideAndDifferentOutsideMode() {
        // 区间内时长不齐（6.4 与 9.0），不算离群证据
        List<SegmentFact> inside = new ArrayList<>(segments(3, 2, "ad.other.com", "/seg/", 6.4, false));
        inside.add(new SegmentFact(5, "ad.other.com", "/seg/5.ts", 9.0, false));

        AdIntervalEvidence evidence = evidence(inside, segments(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));

        assertFalse(HlsSegmentClassifier.detect(evidence).durationOutlier());
    }

    @Test
    public void durationCloseToOutsideModeIsNotOutlier() {
        // 6.4 与众数 6.7 只差 0.3s，低于 0.5s 门槛
        AdIntervalEvidence evidence = evidence(
                segments(3, 4, "ad.other.com", "/seg/", 6.4, false),
                segments(0, 10, PLAYLIST_HOST, "/seg/", 6.7, false));

        assertFalse(HlsSegmentClassifier.detect(evidence).durationOutlier());
    }

    @Test
    public void pathHintMatchesOnlyExplicitAdDirectories() {
        AdIntervalEvidence hit = evidence(
                segments(3, 2, PLAYLIST_HOST, "/ads/spot", 8.0, false),
                segments(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        assertTrue(HlsSegmentClassifier.detect(hit).pathHint());

        // "upload/" 含 ad 子串但不是目录段，不应命中
        AdIntervalEvidence miss = evidence(
                segments(3, 2, PLAYLIST_HOST, "/upload/download", 8.0, false),
                segments(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        assertFalse(HlsSegmentClassifier.detect(miss).pathHint());
    }

    @Test
    public void headPositionOnlyForLeadingSegments() {
        AdIntervalEvidence head = evidence(
                segments(0, 3, "ad.other.com", "/seg/", 6.4, false),
                segments(3, 20, PLAYLIST_HOST, "/seg/", 8.0, false));
        assertTrue(HlsSegmentClassifier.detect(head).headPosition());

        AdIntervalEvidence middle = evidence(
                segments(12, 3, "ad.other.com", "/seg/", 6.4, false),
                segments(0, 20, PLAYLIST_HOST, "/seg/", 8.0, false));
        assertFalse(HlsSegmentClassifier.detect(middle).headPosition());
    }

    @Test
    public void abstainsWhenAlreadyHandledByStructuredRule() {
        AdIntervalEvidence evidence = evidence(
                segments(3, 5, "ad-cdn.other.com", "/ads/", 6.4, true),
                segments(0, 20, PLAYLIST_HOST, "/seg/", 8.0, false),
                true, List.of("baofeng-preroll"));

        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void abstainsWithoutSegmentEvidence() {
        assertNull(HlsSegmentClassifier.classify(null));

        AdIntervalEvidence empty = evidence(List.of(), segments(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        assertNull(HlsSegmentClassifier.classify(empty));
    }

    @Test
    public void abstainsForNonHlsPlayback() {
        AdIntervalEvidence nonHls = new AdIntervalEvidence(
                10_000, 40_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/video.mp4", false,
                segments(3, 5, "ad.other.com", "/ads/", 6.4, true),
                segments(0, 20, PLAYLIST_HOST, "/seg/", 8.0, false),
                true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        assertNull(HlsSegmentClassifier.classify(nonHls));
    }

    @Test
    public void headPositionAloneIsBelowConfidenceFloor() {
        // 只有位置信号 0.05，低于 0.30 门槛
        AdIntervalEvidence evidence = evidence(
                segments(0, 3, PLAYLIST_HOST, "/seg/", 8.0, false),
                segments(3, 20, PLAYLIST_HOST, "/seg/", 8.0, false));

        HlsSegmentClassifier.Signals signals = HlsSegmentClassifier.detect(evidence);
        assertTrue(signals.headPosition());
        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void positionSignalAloneCannotProduceARule() {
        // 位置信号无法编码进 HlsAdRule，也没有区分度：单它成立时必须弃权
        AdIntervalEvidence evidence = evidence(
                segments(0, 3, PLAYLIST_HOST, "/seg/", 8.0, false),
                outsideAround(0, 3, 20, PLAYLIST_HOST, 8.0));

        HlsSegmentClassifier.Signals signals = HlsSegmentClassifier.detect(evidence);
        assertTrue(signals.headPosition());
        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void rejectsCrossDomainWhenSameHostIsOnlySparseOutsideSample() {
        List<SegmentFact> outside = new ArrayList<>();
        outside.add(new SegmentFact(0, PLAYLIST_HOST, "/seg/0.ts", 8.0, false));
        for (int i = 1; i < 300; i++) {
            outside.add(new SegmentFact(i, "cdn.site-x.com", "/seg/" + i + ".ts", 8.0, false));
        }
        AdIntervalEvidence evidence = evidence(
                segments(100, 3, "cdn.site-x.com", "/seg/", 6.4, false), outside);

        assertFalse(HlsSegmentClassifier.detect(evidence).crossDomain());
        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void discontinuityOnFirstInsideSegmentCountsAsBoundary() {
        AdIntervalEvidence evidence = evidence(
                segments(3, 4, "ad.other.com", "/seg/", 6.4, true),
                segments(0, 20, PLAYLIST_HOST, "/seg/", 8.0, false));

        assertTrue(HlsSegmentClassifier.detect(evidence).discontinuity());
    }
}
