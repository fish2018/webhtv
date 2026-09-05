package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AdAttributionArbiterTest {

    private static AdAttribution of(String channel, AdCategory category, float confidence,
                                    RemediationKind kind) {
        return new AdAttribution(channel, category, confidence, RiskLevel.MEDIUM,
                List.of(channel + " 证据"), kind, payloadFor(kind));
    }

    /**
     * 可落地的机制必须带非空载荷，否则 {@link AdAttribution#actionable()} 为 false
     * 并被仲裁器降级成诊断。诊断类机制本身就没有载荷。
     */
    private static RulePayload payloadFor(RemediationKind kind) {
        return switch (kind) {
            case ENABLE_EXISTING_RULE -> RulePayload.ofRuleKey("builtin|pkg|rule");
            case HOST_BLACKLIST -> RulePayload.ofHosts(List.of("ad.example.com"));
            case HLS_STRUCTURED_RULE -> RulePayload.ofHls(
                    List.of("v.example.com"), List.of("ad.example.com"), 2);
            case URL_REGEX_RULE -> RulePayload.ofRegex(List.of(".*/ad/.*"));
            default -> RulePayload.empty();
        };
    }

    @Test
    public void prefersCheaperMechanismAtComparableConfidence() {
        // 域名黑名单置信度略低，但成本与风险都更低
        AdAttribution hls = of("hls", AdCategory.DISCONTINUITY_BLOCK, 0.80f,
                RemediationKind.HLS_STRUCTURED_RULE);
        AdAttribution domain = of("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.78f,
                RemediationKind.HOST_BLACKLIST);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(hls, domain));

        assertEquals(RemediationKind.HOST_BLACKLIST, verdict.preferred().remediation());
        assertTrue(verdict.hasActionablePlan());
        assertEquals(1, verdict.alternatives().size());
    }

    @Test
    public void enablingExistingRuleOutranksNewRuleEvenAtLowerConfidence() {
        AdAttribution hls = of("hls", AdCategory.DISCONTINUITY_BLOCK, 0.85f,
                RemediationKind.HLS_STRUCTURED_RULE);
        AdAttribution existing = of("existing-rule", AdCategory.FIXED_DURATION_BLOCK, 0.75f,
                RemediationKind.ENABLE_EXISTING_RULE);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(hls, existing));

        assertEquals(RemediationKind.ENABLE_EXISTING_RULE, verdict.preferred().remediation());
    }

    @Test
    public void muchHigherConfidenceStillWinsOverCheaperMechanism() {
        // 置信度占 0.7 权重，差距足够大时仍可翻盘
        AdAttribution hls = of("hls", AdCategory.DISCONTINUITY_BLOCK, 1.0f,
                RemediationKind.HLS_STRUCTURED_RULE);
        AdAttribution domain = of("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.30f,
                RemediationKind.HOST_BLACKLIST);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(hls, domain));

        assertEquals(RemediationKind.HLS_STRUCTURED_RULE, verdict.preferred().remediation());
    }

    @Test
    public void mergesSameCategoryWithProbabilisticOr() {
        AdAttribution first = of("hls", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.6f,
                RemediationKind.HLS_STRUCTURED_RULE);
        AdAttribution second = of("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.5f,
                RemediationKind.HOST_BLACKLIST);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(first, second));

        // 1 - (1-0.6)(1-0.5) = 0.8
        assertEquals(0.8f, verdict.preferred().confidence(), 0.0001f);
        // 合并后取成本更低的机制
        assertEquals(RemediationKind.HOST_BLACKLIST, verdict.preferred().remediation());
        // 证据合并，通道名拼接
        assertEquals(2, verdict.preferred().evidence().size());
        assertTrue(verdict.preferred().channelId().contains("hls"));
        assertTrue(verdict.preferred().channelId().contains("domain"));
        assertTrue(verdict.alternatives().isEmpty());
    }

    @Test
    public void alreadyHandledIsDemotedToDiagnostics() {
        AdAttribution diagnosis = new AdAttribution("domain", AdCategory.ALREADY_HANDLED, 0.95f,
                RiskLevel.LOW, List.of("已在黑名单中"), RemediationKind.NONE);
        AdAttribution hls = of("hls", AdCategory.DISCONTINUITY_BLOCK, 0.4f,
                RemediationKind.HLS_STRUCTURED_RULE);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(diagnosis, hls));

        // 置信度 0.95 远高于 0.4，但不可落地，只能进诊断
        assertEquals(RemediationKind.HLS_STRUCTURED_RULE, verdict.preferred().remediation());
        assertEquals(1, verdict.diagnostics().size());
        assertEquals(AdCategory.ALREADY_HANDLED, verdict.diagnostics().get(0).category());
    }

    @Test
    public void diagnosticsOnlyFallsBackToSessionSkip() {
        AdAttribution diagnosis = new AdAttribution("existing-rule", AdCategory.ALREADY_HANDLED, 0.5f,
                RiskLevel.LOW, List.of("旧启发式引擎生效"), RemediationKind.NONE);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(diagnosis));

        assertEquals(RemediationKind.SESSION_SKIP_ONLY, verdict.preferred().remediation());
        assertEquals(AdCategory.IN_STREAM_BURNED_IN, verdict.preferred().category());
        assertFalse(verdict.hasActionablePlan());
        // 诊断证据被并入兜底结论，便于 UI 一次展示
        assertTrue(verdict.preferred().evidence().stream()
                .anyMatch(line -> line.contains("旧启发式引擎生效")));
    }

    @Test
    public void allChannelsAbstainYieldsEmptyVerdict() {
        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(
                Arrays.asList(null, null));

        assertNull(verdict.preferred());
        assertTrue(verdict.empty());
        assertFalse(verdict.hasActionablePlan());
    }

    @Test
    public void handlesNullListAndSkipsNullEntries() {
        assertTrue(AdAttributionArbiter.arbitrate(null).empty());

        AdAttribution hls = of("hls", AdCategory.DISCONTINUITY_BLOCK, 0.6f,
                RemediationKind.HLS_STRUCTURED_RULE);
        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(
                Arrays.asList(null, hls, null));

        assertEquals(RemediationKind.HLS_STRUCTURED_RULE, verdict.preferred().remediation());
    }

    @Test
    public void mergedRiskTakesLowestOfGroup() {
        AdAttribution medium = new AdAttribution("hls", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.5f,
                RiskLevel.MEDIUM, List.of("a"), RemediationKind.HLS_STRUCTURED_RULE);
        AdAttribution low = new AdAttribution("domain", AdCategory.THIRD_PARTY_CDN_SEGMENT, 0.5f,
                RiskLevel.LOW, List.of("b"), RemediationKind.HOST_BLACKLIST);

        AdAttributionArbiter.Verdict verdict = AdAttributionArbiter.arbitrate(List.of(medium, low));

        assertEquals(RiskLevel.LOW, verdict.preferred().risk());
    }
}
