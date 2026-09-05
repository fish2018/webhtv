package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fongmi.android.tv.bean.HlsAdRule;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 契约测试：分类器产出的 {@link RulePayload} 必须能组装成一条
 * {@code HlsAdRule.compile()} 接受的规则。
 *
 * <p>这是整条链路最容易断的地方 —— 规则写进 UserHlsRuleStore 却编译失败时，
 * 用户看到「已保存」但广告依旧存在，且只会在 HlsRuleConfig 里留下一条报错条目。
 * compile() 的硬约束是 minimumSignals 不得超过实际信号数。
 */
public class RulePayloadCompileContractTest {

    /** 把载荷按 AdRulePlanApplier 的方式组装成规则并编译。 */
    private static void assertCompiles(String label, RulePayload payload) {
        assertTrue(label + ": 载荷必须带作用域", !payload.playlistHostSuffixes().isEmpty());
        HlsAdRule rule = HlsAdRule.createUserRule(
                "test-id", "test", payload.playlistHostSuffixes(),
                payload.hosts(), payload.regex(),
                payload.hasDurationRange() ? payload.durationMin() : null,
                payload.hasDurationRange() ? payload.durationMax() : null,
                payload.requireDiscontinuity(), payload.requireCrossDomain(),
                payload.minimumSignals());
        try {
            assertNotNull(label, rule.compile());
        } catch (RuntimeException e) {
            fail(label + ": 规则无法编译 -> " + e.getMessage());
        }
    }

    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside,
                                               boolean bounded, boolean crossDomain) {
        return new AdIntervalEvidence(
                80_000, 100_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, bounded, crossDomain, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    private static List<SegmentFact> segments(int from, int count, String host,
                                             String pathPrefix, double duration,
                                             boolean discontinuityOnFirst) {
        List<SegmentFact> facts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            facts.add(new SegmentFact(from + i, host, pathPrefix + (from + i) + ".ts",
                    duration, discontinuityOnFirst && i == 0));
        }
        return facts;
    }

    @Test
    public void crossDomainWithDiscontinuityAndDurationOutlierCompiles() {
        AdIntervalEvidence evidence = evidence(
                segments(10, 3, "ad-cdn.other.com", "/seg/", 6.4, true),
                segments(0, 10, "v.example.com", "/seg/", 8.0, false),
                true, true);

        AdAttribution plan = HlsSegmentClassifier.classify(evidence);

        assertNotNull("三信号命中应产出方案", plan);
        assertCompiles("跨域+断点+时长离群", plan.payload());
    }

    @Test
    public void pathHintOnlyIntervalStillCompiles() {
        // 同域、时长一致，只有路径特征命中：可编码信号数为 1，
        // 而落地时 minimumSignals 被抬到 2 —— compile() 会因此拒绝。
        AdIntervalEvidence evidence = evidence(
                segments(10, 3, "v.example.com", "/ads/", 8.0, false),
                segments(0, 10, "v.example.com", "/seg/", 8.0, false),
                false, false);

        AdAttribution plan = HlsSegmentClassifier.classify(evidence);

        // 置信度 0.15 低于门槛，通道应弃权而不是产出无法编译的规则
        assertTrue("单一弱信号必须弃权", plan == null);
    }

    @Test
    public void discontinuityPlusPathHintCompiles() {
        AdIntervalEvidence evidence = evidence(
                segments(10, 3, "v.example.com", "/ads/", 8.0, true),
                segments(0, 10, "v.example.com", "/seg/", 8.0, false),
                true, false);

        AdAttribution plan = HlsSegmentClassifier.classify(evidence);

        assertNotNull("断点+路径特征应产出方案", plan);
        assertCompiles("断点+路径特征", plan.payload());
    }

    @Test
    public void domainChannelPayloadCompiles() {
        AdIntervalEvidence evidence = evidence(
                segments(10, 3, "ad-cdn.other.com", "/seg/", 6.4, false),
                segments(0, 10, "v.example.com", "/seg/", 8.0, false),
                false, true);

        AdAttribution plan = DomainReputationClassifier.classify(evidence,
                new DomainReputationClassifier.Input(
                        List.of(), List.of("v.example.com"), List.of(), ""));

        assertNotNull("非本站域名应产出方案", plan);
        assertCompiles("域名通道", plan.payload());
    }

    @Test
    public void durationOutlierWithCrossDomainCompiles() {
        AdIntervalEvidence evidence = evidence(
                segments(10, 4, "ad-cdn.other.com", "/seg/", 6.4, false),
                segments(0, 10, "v.example.com", "/seg/", 8.0, false),
                false, true);

        AdAttribution plan = HlsSegmentClassifier.classify(evidence);

        assertNotNull("跨域+时长离群应产出方案", plan);
        assertCompiles("跨域+时长离群", plan.payload());
    }
}
