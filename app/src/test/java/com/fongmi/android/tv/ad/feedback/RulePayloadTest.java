package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class RulePayloadTest {

    @Test
    public void emptyPayloadIsNotActionable() {
        AdAttribution plan = new AdAttribution("hls", AdCategory.DISCONTINUITY_BLOCK, 0.9f,
                RiskLevel.MEDIUM, List.of("证据"), RemediationKind.HLS_STRUCTURED_RULE,
                RulePayload.empty());

        // 机制可执行但没有数据可写，必须视为不可落地
        assertFalse(plan.actionable());
    }

    @Test
    public void diagnosticConstructorDefaultsToEmptyPayload() {
        AdAttribution plan = new AdAttribution("domain", AdCategory.ALREADY_HANDLED, 0.95f,
                RiskLevel.LOW, List.of("已在黑名单"), RemediationKind.NONE);

        assertTrue(plan.payload().isEmpty());
        assertFalse(plan.actionable());
    }

    @Test
    public void hostPayloadMakesPlanActionable() {
        AdAttribution plan = new AdAttribution("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.6f,
                RiskLevel.LOW, List.of("证据"), RemediationKind.HOST_BLACKLIST,
                RulePayload.ofHosts(List.of("ad.example.com")));

        assertTrue(plan.actionable());
        assertEquals(List.of("ad.example.com"), plan.payload().hosts());
    }

    @Test
    public void ruleKeyPayloadIsNotEmpty() {
        RulePayload payload = RulePayload.ofRuleKey("builtin|pkg|baofeng");

        assertFalse(payload.isEmpty());
        assertEquals("builtin|pkg|baofeng", payload.ruleKey());
    }

    @Test
    public void durationRangePresenceIsExplicit() {
        assertFalse(RulePayload.ofHosts(List.of("a.com")).hasDurationRange());
        assertTrue(RulePayload.ofHlsRule(List.of("v.a.com"), List.of("ad.a.com"),
                6.3, 6.5, true, true, 2).hasDurationRange());
    }

    @Test
    public void nullFieldsBecomeEmptyCollections() {
        RulePayload payload = new RulePayload(null, null, null, null, null,
                Double.NaN, Double.NaN, false, false, -5);

        assertTrue(payload.playlistHostSuffixes().isEmpty());
        assertTrue(payload.hosts().isEmpty());
        assertTrue(payload.regex().isEmpty());
        assertTrue(payload.exclude().isEmpty());
        assertEquals("", payload.ruleKey());
        assertEquals(0, payload.minimumSignals());
        assertTrue(payload.isEmpty());
    }

    /**
     * 区间外对照切片。数量必须足够 —— cleaner 有 35% 删除比例上限，
     * 区间外只放两三片时任何规则都会因超限而回退，规则自检会正确地拒掉它。
     */
    private static List<SegmentFact> healthyOutside(int insideFrom, int insideCount) {
        List<SegmentFact> facts = new java.util.ArrayList<>();
        for (int i = 0; i < insideFrom; i++) {
            facts.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = insideFrom + insideCount; i < 30; i++) {
            facts.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0,
                    i == insideFrom + insideCount));
        }
        return facts;
    }

    @Test
    public void hlsClassifierScopesRuleToPlaylistHostAndKeepsMinimumTwoSignals() {
        List<SegmentFact> inside = List.of(
                new SegmentFact(10, "ad-cdn.other.com", "/seg/10.ts", 6.4, true),
                new SegmentFact(11, "ad-cdn.other.com", "/seg/11.ts", 6.4, false));
        List<SegmentFact> outside = healthyOutside(10, 2);
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 92_800, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, true, true, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution plan = HlsSegmentClassifier.classify(evidence);
        RulePayload payload = plan.payload();

        // 作用域收窄到本站，避免规则污染其他站点
        assertEquals(List.of("v.example.com"), payload.playlistHostSuffixes());
        assertEquals(List.of("ad-cdn.other.com"), payload.hosts());
        // requireDiscontinuity 只对广告块首片成立，编码进规则会导致只删首片、
        // 留下其余广告，因此刻意不编码 —— 断点只在归因阶段用于定位区间
        assertFalse(payload.requireDiscontinuity());
        assertTrue(payload.requireCrossDomain());
        // 跨域 + 域名 + 时长离群三个信号，门限取 2
        assertEquals(2, payload.minimumSignals());
        assertTrue(plan.actionable());
    }

    @Test
    public void durationRangeOnlyAppearsWhenOutlierSignalHolds() {
        // 区间内外时长一致：不给时长条件，避免只靠固定时长删片
        List<SegmentFact> inside = List.of(
                new SegmentFact(10, "ad-cdn.other.com", "/ads/10.ts", 8.0, true),
                new SegmentFact(11, "ad-cdn.other.com", "/ads/11.ts", 8.0, false));
        List<SegmentFact> outside = healthyOutside(10, 2);
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 96_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, true, true, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        RulePayload payload = HlsSegmentClassifier.classify(evidence).payload();

        assertFalse(payload.hasDurationRange());
    }

    @Test
    public void pathHintPresentOutsideTheIntervalIsNotADistinguishingCondition() {
        // 站内正片路径也含 /ads/ 时，该特征无区分度：规则按切片独立匹配，
        // 没有「只在这段区间内生效」的概念，编码它会连正片一起删。
        List<SegmentFact> inside = List.of(
                new SegmentFact(10, "v.example.com", "/ads/10.ts", 8.0, true),
                new SegmentFact(11, "v.example.com", "/ads/11.ts", 8.0, false));
        List<SegmentFact> outside = List.of(
                new SegmentFact(0, "v.example.com", "/ads/0.ts", 8.0, false),
                new SegmentFact(12, "v.example.com", "/seg/12.ts", 8.0, false));
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 96_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        // 同域 + 时长一致 + 路径无区分度 → 无任何可编码条件，必须弃权
        assertNull(HlsSegmentClassifier.classify(evidence));
    }

    @Test
    public void pathHintAppearingInLaterOutsideSegmentIsRejected() {
        List<SegmentFact> inside = List.of(
                new SegmentFact(10, "v.example.com", "/ads/10.ts", 8.0, false),
                new SegmentFact(11, "v.example.com", "/ads/11.ts", 8.0, false));
        List<SegmentFact> outside = List.of(
                new SegmentFact(0, "v.example.com", "/seg/0.ts", 8.0, false),
                new SegmentFact(12, "v.example.com", "/ads/12.ts", 8.0, false));
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 96_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, false, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        // 后半段的正常切片含 hint，完整 outside 检查后不能生成路径规则
        assertFalse(HlsSegmentClassifier.detect(evidence).pathHint());
        assertFalse(HlsSegmentClassifier.classify(evidence) != null
                && !HlsSegmentClassifier.classify(evidence).payload().regex().isEmpty());
    }

    @Test
    public void existingRuleAttributionCarriesRuleKey() {
        ExistingRuleClassifier.RuleState disabled = new ExistingRuleClassifier.RuleState(
                "builtin|pkg|baofeng", "baofeng", "暴风片头", false, true,
                List.of("ad-cdn.other.com"),
                ExistingRuleClassifierTest.compiledRule("ad-cdn.other.com"));
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                10_000, 40_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                List.of(new SegmentFact(3, "ad-cdn.other.com", "/seg/3.ts", 6.4, true)),
                healthyOutside(3, 1),
                false, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution plan = ExistingRuleClassifier.classify(evidence,
                new ExistingRuleClassifier.Input(List.of(disabled), false, List.of()));

        assertEquals("builtin|pkg|baofeng", plan.payload().ruleKey());
        assertTrue(plan.actionable());
    }

    @Test
    public void mergedAttributionKeepsPayloadOfChosenMechanism() {
        AdAttribution hls = new AdAttribution("hls", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.6f,
                RiskLevel.MEDIUM, List.of("hls 证据"), RemediationKind.HLS_STRUCTURED_RULE,
                RulePayload.ofHls(List.of("v.example.com"), List.of("hls-host.com"), 2));
        AdAttribution domain = new AdAttribution("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.5f,
                RiskLevel.LOW, List.of("domain 证据"), RemediationKind.HOST_BLACKLIST,
                RulePayload.ofHosts(List.of("domain-host.com")));

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(hls, domain));
        AdAttribution preferred = verdict.preferred();

        // 合并后取成本更低的 HOST_BLACKLIST，载荷必须同步取它的，不能错配
        assertEquals(RemediationKind.HOST_BLACKLIST, preferred.remediation());
        assertEquals(List.of("domain-host.com"), preferred.payload().hosts());
    }
}
