package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ExistingRuleClassifierTest {

    private static final String PLAYLIST_HOST = "v.example.com";

    /**
     * 区间外放足量同域正片：cleaner 有 35% 删除比例上限，只给一两片对照时
     * 任何规则都会因超限回退，「启用已有规则」的自检会正确拒掉。
     */
    private static AdIntervalEvidence evidence(String insideHost, List<String> removedRuleIds) {
        List<SegmentFact> outside = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = 4; i < 30; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/seg/" + i + ".ts", 8.0, i == 4));
        }
        return new AdIntervalEvidence(
                10_000, 40_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/index.m3u8", true,
                List.of(new SegmentFact(3, insideHost, "/seg/3.ts", 6.4, true)),
                outside,
                false, false, removedRuleIds, false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    /** 一条只命中给定切片域名、带作用域的可编译规则。 */
    static com.fongmi.android.tv.utils.HlsManifestCleaner.Rule compiledRule(String hostSuffix) {
        return com.fongmi.android.tv.bean.HlsAdRule.createUserRule(
                "id", "n", List.of(PLAYLIST_HOST), List.of(hostSuffix), List.of(),
                null, null, false, true, 2).compile();
    }

    @Test
    public void suggestsEnablingDisabledRuleInsteadOfCreatingNew() {
        ExistingRuleClassifier.RuleState disabled = new ExistingRuleClassifier.RuleState(
                "builtin:baofeng", "baofeng-preroll", "暴风片头", false, true,
                List.of("ad-cdn.other.com"), compiledRule("ad-cdn.other.com"));

        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(disabled), false, List.of()));

        assertNotNull(attribution);
        assertEquals(RemediationKind.ENABLE_EXISTING_RULE, attribution.remediation());
        assertEquals(ExistingRuleClassifier.CONFIDENCE_DISABLED_RULE,
                attribution.confidence(), 0.0001f);
        assertTrue(attribution.actionable());
        assertTrue(attribution.evidence().stream().anyMatch(line -> line.contains("暴风片头")));
    }

    @Test
    public void ignoresAlreadyEnabledRule() {
        ExistingRuleClassifier.RuleState enabled = new ExistingRuleClassifier.RuleState(
                "builtin:baofeng", "baofeng-preroll", "暴风片头", true, true,
                List.of("ad-cdn.other.com"));

        assertNull(ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(enabled), false, List.of())));
    }

    @Test
    public void ignoresDisabledRuleThatDoesNotCoverInterval() {
        ExistingRuleClassifier.RuleState unrelated = new ExistingRuleClassifier.RuleState(
                "builtin:other", "other-rule", "其他规则", false, true,
                List.of("unrelated-cdn.com"));

        assertNull(ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(unrelated), false, List.of())));
    }

    @Test
    public void reportsInvalidRuleAsDiagnosis() {
        ExistingRuleClassifier.RuleState invalid = new ExistingRuleClassifier.RuleState(
                "builtin:broken", "broken-rule", "坏规则", false, false, List.of("ad-cdn.other.com"));

        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(invalid), false, List.of()));

        assertNotNull(attribution);
        // 编译失败的规则不能建议启用，只能报诊断
        assertEquals(RemediationKind.NONE, attribution.remediation());
        assertEquals(AdCategory.ALREADY_HANDLED, attribution.category());
        assertTrue(attribution.evidence().stream().anyMatch(line -> line.contains("编译失败")));
    }

    @Test
    public void reportsLegacyHeuristicAndProtectingExcludes() {
        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(), true, List.of(".*/main/.*")));

        assertNotNull(attribution);
        assertEquals(RemediationKind.NONE, attribution.remediation());
        assertTrue(attribution.evidence().stream().anyMatch(line -> line.contains("旧启发式引擎")));
        assertTrue(attribution.evidence().stream().anyMatch(line -> line.contains("正片保护")));
    }

    @Test
    public void reportsIntervalAlreadyRemovedByStructuredRule() {
        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of("quantum-block")),
                ExistingRuleClassifier.Input.empty());

        assertNotNull(attribution);
        assertTrue(attribution.evidence().stream().anyMatch(line -> line.contains("quantum-block")));
    }

    @Test
    public void abstainsWhenNothingToReport() {
        assertNull(ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                ExistingRuleClassifier.Input.empty()));
        assertNull(ExistingRuleClassifier.classify(null, ExistingRuleClassifier.Input.empty()));
    }

    /**
     * 既有规则过宽时不得建议启用。
     *
     * <p>此前这条路径完全没有守卫 —— 只判断 hostSuffixes 与区间内某片同域，
     * 不看规则的其余条件。一条 {@code minimumSignals=1} 且只限定 playlist 作用域的
     * 规则会命中该站每一片切片，启用后 cleaner 因全删而回退，并连带停掉 legacy。
     */
    @Test
    public void doesNotSuggestEnablingAnOverbroadRule() {
        com.fongmi.android.tv.utils.HlsManifestCleaner.Rule overbroad =
                com.fongmi.android.tv.bean.HlsAdRule.createUserRule(
                        "wide", "wide", List.of(PLAYLIST_HOST), List.of("ad-cdn.other.com"),
                        List.of(), 0.0, 3600.0, false, false, 1).compile();
        ExistingRuleClassifier.RuleState wide = new ExistingRuleClassifier.RuleState(
                "builtin:wide", "wide", "过宽规则", false, true,
                List.of("ad-cdn.other.com"), overbroad);

        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(wide), false, List.of()));

        // 要么整体弃权，要么只报诊断，绝不能给出 ENABLE_EXISTING_RULE
        assertTrue(attribution == null
                || attribution.remediation() != RemediationKind.ENABLE_EXISTING_RULE);
    }

    /** 拿不到编译产物时无法验证，宁可不建议启用。 */
    @Test
    public void doesNotSuggestEnablingWhenCompiledRuleIsUnavailable() {
        ExistingRuleClassifier.RuleState noCompiled = new ExistingRuleClassifier.RuleState(
                "builtin:x", "x", "无编译产物", false, true, List.of("ad-cdn.other.com"));

        AdAttribution attribution = ExistingRuleClassifier.classify(
                evidence("ad-cdn.other.com", List.of()),
                new ExistingRuleClassifier.Input(List.of(noCompiled), false, List.of()));

        assertTrue(attribution == null
                || attribution.remediation() != RemediationKind.ENABLE_EXISTING_RULE);
    }
}
