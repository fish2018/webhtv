package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * {@link AdFeedbackDataSource} 的纯解析部分。
 *
 * <p>读取静态单例的方法依赖 Android 运行时，无法在此覆盖；这里只锁定
 * {@code hostSuffixesOf} 的解析行为，它是规则状态能否被正确识别的关键。
 */
public class AdFeedbackDataSourceTest {

    @Test
    public void parsesHostSuffixesFromRuleDetail() {
        String detail = "{\"id\":\"baofeng\",\"hostSuffixes\":[\"ad-cdn.a.com\",\"ads.b.com\"],"
                + "\"minimumSignals\":2}";

        assertEquals(List.of("ad-cdn.a.com", "ads.b.com"),
                AdFeedbackDataSource.hostSuffixesOf(detail));
    }

    @Test
    public void trimsAndSkipsBlankEntries() {
        String detail = "{\"hostSuffixes\":[\"  ad.a.com  \",\"\",\"   \",null,\"ad.b.com\"]}";

        assertEquals(List.of("ad.a.com", "ad.b.com"),
                AdFeedbackDataSource.hostSuffixesOf(detail));
    }

    @Test
    public void returnsEmptyWhenFieldMissingOrWrongType() {
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("{\"id\":\"x\"}").isEmpty());
        // 规则只用 playlistHostSuffixes 限定作用域，没有切片域名条件
        assertTrue(AdFeedbackDataSource.hostSuffixesOf(
                "{\"playlistHostSuffixes\":[\"v.a.com\"]}").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("{\"hostSuffixes\":\"ad.a.com\"}").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("{\"hostSuffixes\":null}").isEmpty());
    }

    @Test
    public void toleratesMalformedInput() {
        assertTrue(AdFeedbackDataSource.hostSuffixesOf(null).isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("   ").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("not json").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("[1,2,3]").isEmpty());
        assertTrue(AdFeedbackDataSource.hostSuffixesOf("{\"hostSuffixes\":[").isEmpty());
    }

    @Test
    public void parsedSuffixesFeedRuleStateMatching() {
        // 端到端：解析出的后缀能让 ExistingRuleClassifier 命中区间切片
        List<String> suffixes = AdFeedbackDataSource.hostSuffixesOf(
                "{\"hostSuffixes\":[\"ad-cdn.other.com\"]}");
        ExistingRuleClassifier.RuleState disabled = new ExistingRuleClassifier.RuleState(
                "builtin:x", "x", "实验规则", false, true, suffixes,
                ExistingRuleClassifierTest.compiledRule("ad-cdn.other.com"));

        List<SegmentFact> outside = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = 4; i < 30; i++) {
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, i == 4));
        }
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                10_000, 40_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                List.of(new SegmentFact(3, "ad-cdn.other.com", "/seg/3.ts", 6.4, true)),
                outside,
                false, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution attribution = ExistingRuleClassifier.classify(evidence,
                new ExistingRuleClassifier.Input(List.of(disabled), false, List.of()));

        assertEquals(RemediationKind.ENABLE_EXISTING_RULE, attribution.remediation());
    }
}
