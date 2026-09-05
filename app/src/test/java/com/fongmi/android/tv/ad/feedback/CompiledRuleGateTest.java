package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 「启用已有规则」通道的守卫：{@code compiledOf} 必须拒掉那些自检会放行、
 * 但真实运行会误删正片的既有规则。
 *
 * <p>第八轮评审复现的链路：{@code RuleSelfCheck} 的合成 manifest 用
 * {@code SegmentFact.path()} 拼 URL，而那是 {@code URI.getPath()} 的产物，
 * query 与 fragment 都被去敏丢弃。于是一条 {@code segmentUrlRegex=["/ads/"]}
 * 的既有规则在自检里只匹配到广告片，真实运行却会命中所有
 * {@code ?ref=/ads/} 的正片 —— 超删比例低于 35% 时 {@code fallback=false}，
 * 错误不被回退兜住。
 */
public class CompiledRuleGateTest {

    @Test
    public void rejectsRuleWithUnanchoredSegmentRegex() {
        String detail = "{\"id\":\"x\",\"playlistHostSuffixes\":[\"v.example.com\"],"
                + "\"segmentUrlRegex\":[\"/ads/\"],\"minimumSignals\":1}";

        assertTrue("未锚定的切片正则必须被识别",
                AdFeedbackDataSource.hasUnanchoredSegmentRegex(detail));
        assertNull("不得为这类规则背书", AdFeedbackDataSource.compiledOf(detail));
    }

    @Test
    public void acceptsRuleWithAnchoredSegmentRegex() {
        String detail = "{\"id\":\"x\",\"playlistHostSuffixes\":[\"v.example.com\"],"
                + "\"segmentUrlRegex\":[\"^[^?#]*\\\\Q/ads/\\\\E\"],\"minimumSignals\":1}";

        assertFalse(AdFeedbackDataSource.hasUnanchoredSegmentRegex(detail));
        assertNotNull("锚定过的正则语义与自检一致，可以放行",
                AdFeedbackDataSource.compiledOf(detail));
    }

    @Test
    public void acceptsRuleWithoutSegmentRegexAtAll() {
        // 只靠 host + 跨域的规则不涉及 query/fragment 语义差异
        String detail = "{\"id\":\"x\",\"playlistHostSuffixes\":[\"v.example.com\"],"
                + "\"hostSuffixes\":[\"ad.other.com\"],\"requireCrossDomain\":true,"
                + "\"minimumSignals\":2}";

        assertFalse(AdFeedbackDataSource.hasUnanchoredSegmentRegex(detail));
        assertNotNull(AdFeedbackDataSource.compiledOf(detail));
    }

    @Test
    public void treatsUnparsableDetailAsUnanchored() {
        assertTrue("解析不出来时保守判为未锚定",
                AdFeedbackDataSource.hasUnanchoredSegmentRegex("{\"segmentUrlRegex\":["));
        assertNull(AdFeedbackDataSource.compiledOf("not json"));
        assertNull(AdFeedbackDataSource.compiledOf(null));
        assertNull(AdFeedbackDataSource.compiledOf("   "));
    }

    /** compiledOf 用本地 Gson，不依赖 Application —— 纯 JVM 下必须可用。 */
    @Test
    public void roundTripsWithoutApplicationContext() {
        String detail = "{\"id\":\"baofeng\",\"name\":\"暴风\",\"version\":1,"
                + "\"playlistHostSuffixes\":[\"v.example.com\"],"
                + "\"hostSuffixes\":[\"ad.other.com\"],"
                + "\"minDuration\":6.3,\"maxDuration\":6.5,"
                + "\"requireCrossDomain\":true,\"minimumSignals\":2}";

        HlsManifestCleaner.Rule rule = AdFeedbackDataSource.compiledOf(detail);

        assertNotNull("本地 Gson 必须能在纯 JVM 下还原规则", rule);
        // 还原出的规则行为与直接构造的一致
        String manifest = manifest();
        HlsManifestCleaner.Result viaJson = HlsManifestCleaner.clean(BASE, manifest, List.of(rule));
        HlsManifestCleaner.Result direct = HlsManifestCleaner.clean(BASE, manifest,
                List.of(HlsAdRule.createUserRule("baofeng", "暴风",
                        List.of("v.example.com"), List.of("ad.other.com"), List.of(),
                        6.3, 6.5, false, true, 2).compile()));

        assertEquals(direct.removedSegments(), viaJson.removedSegments());
        assertEquals(direct.fallback(), viaJson.fallback());
        assertEquals(direct.manifest(), viaJson.manifest());
    }

    private static final String BASE = "https://v.example.com/index.m3u8";

    private static String manifest() {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        for (int i = 0; i < 30; i++) {
            boolean ad = i >= 10 && i < 13;
            if (i == 10 || i == 13) text.append("#EXT-X-DISCONTINUITY\n");
            text.append("#EXTINF:").append(ad ? "6.400" : "8.000").append(",\n");
            text.append(ad ? "https://ad.other.com/ads/" + i + ".ts"
                    : "https://v.example.com/seg/" + i + ".ts").append('\n');
        }
        return text.append("#EXT-X-ENDLIST\n").toString();
    }

    /**
     * 端到端：带未锚定正则的既有规则不得被建议启用。
     *
     * <p>这条规则在自检里看起来只删广告，真实运行会连 {@code ?ref=/ads/} 的正片
     * 一起删，且超删比例低于 35% 故不触发回退。
     */
    @Test
    public void doesNotSuggestEnablingRuleThatOverDeletesViaQuery() {
        String detail = "{\"id\":\"loose\",\"playlistHostSuffixes\":[\"v.example.com\"],"
                + "\"hostSuffixes\":[\"ad.other.com\"],"
                + "\"segmentUrlRegex\":[\"/ads/\"],\"minimumSignals\":1}";
        ExistingRuleClassifier.RuleState loose = new ExistingRuleClassifier.RuleState(
                "vod:src:loose", "loose", "宽松规则", false, true,
                List.of("ad.other.com"), AdFeedbackDataSource.compiledOf(detail));

        List<SegmentFact> inside = new ArrayList<>();
        for (int i = 10; i < 13; i++) {
            inside.add(new SegmentFact(i, "ad.other.com", "/ads/" + i + ".ts", 6.4, i == 10));
        }
        List<SegmentFact> outside = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = 13; i < 40; i++) {
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, i == 13));
        }
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 99_200, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                "v.example.com", "/play/index.m3u8", true,
                inside, outside, true, true, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution attribution = ExistingRuleClassifier.classify(evidence,
                new ExistingRuleClassifier.Input(List.of(loose), false, List.of()));

        assertTrue("带未锚定正则的规则不得被建议启用",
                attribution == null
                        || attribution.remediation() != RemediationKind.ENABLE_EXISTING_RULE);
    }
}
