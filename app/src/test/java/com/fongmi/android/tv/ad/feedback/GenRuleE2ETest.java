package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端：分类器产出的载荷 → HlsAdRule → HlsManifestCleaner，
 * 验证生成的规则在真实 manifest 上删对了切片、且不误删正片。
 */
public class GenRuleE2ETest {

    private static final String BASE = "https://v.example.com/play/index.m3u8";
    private static final int TOTAL = 30;
    private static final int AD_FROM = 10;
    private static final int AD_COUNT = 3;

    private static List<SegmentFact> seg(int from, int n, String host, String path,
                                        double dur, boolean discFirst) {
        List<SegmentFact> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new SegmentFact(from + i, host, path + (from + i) + ".ts", dur, discFirst && i == 0));
        }
        return l;
    }

    private static List<SegmentFact> outsideAround(String host, double dur) {
        List<SegmentFact> l = new ArrayList<>(seg(0, AD_FROM, host, "/seg/", dur, false));
        l.addAll(seg(AD_FROM + AD_COUNT, TOTAL - AD_FROM - AD_COUNT, host, "/seg/", dur, true));
        return l;
    }

    private static AdIntervalEvidence ev(List<SegmentFact> in, List<SegmentFact> out,
                                        boolean bounded, boolean cross) {
        return new AdIntervalEvidence(80_000, 100_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧", "线", "1", "v.example.com", "/play/index.m3u8", true,
                in, out, bounded, cross, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    /** 按 AdRulePlanApplier 的方式组装规则。 */
    private static HlsManifestCleaner.Rule toRule(RulePayload p) {
        HlsAdRule r = HlsAdRule.createUserRule("id", "n",
                p.playlistHostSuffixes(), p.hosts(), p.regex(),
                p.hasDurationRange() ? p.durationMin() : null,
                p.hasDurationRange() ? p.durationMax() : null,
                p.requireDiscontinuity(), p.requireCrossDomain(),
                p.minimumSignals());
        return r.compile();
    }

    /** 30 片，AD_FROM 起 3 片是广告，广告块前后各有断点。 */
    private static String manifest(String adHost, String adPath, double adDur) {
        StringBuilder b = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < TOTAL; i++) {
            boolean ad = i >= AD_FROM && i < AD_FROM + AD_COUNT;
            if (i == AD_FROM || i == AD_FROM + AD_COUNT) b.append("#EXT-X-DISCONTINUITY\n");
            b.append("#EXTINF:").append(ad ? adDur : 8.0).append(",\n");
            b.append(ad ? adHost + adPath + i + ".ts" : "https://v.example.com/seg/" + i + ".ts")
                    .append('\n');
        }
        return b.append("#EXT-X-ENDLIST\n").toString();
    }

    @Test
    public void crossDomainRuleRemovesExactlyTheAdBlock() {
        AdAttribution plan = HlsSegmentClassifier.classify(
                ev(seg(AD_FROM, AD_COUNT, "ad-cdn.other.com", "/seg/", 6.4, true),
                        outsideAround("v.example.com", 8.0), true, true));
        assertNotNull(plan);

        HlsManifestCleaner.Result res = HlsManifestCleaner.clean(BASE,
                manifest("https://ad-cdn.other.com", "/seg/", 6.4),
                List.of(toRule(plan.payload())));

        assertEquals("只删广告块", AD_COUNT, res.removedSegments());
    }

    @Test
    public void domainChannelRuleRemovesExactlyTheAdBlock() {
        AdAttribution plan = DomainReputationClassifier.classify(
                ev(seg(AD_FROM, AD_COUNT, "ad-cdn.other.com", "/seg/", 6.4, false),
                        outsideAround("v.example.com", 8.0), false, true),
                new DomainReputationClassifier.Input(
                        List.of(), List.of("v.example.com"), List.of(), ""));
        assertNotNull(plan);

        HlsManifestCleaner.Result res = HlsManifestCleaner.clean(BASE,
                manifest("https://ad-cdn.other.com", "/seg/", 6.4),
                List.of(toRule(plan.payload())));

        assertEquals("只删广告块", AD_COUNT, res.removedSegments());
    }

    /**
     * 同域广告块：广告与正片同域名、同时长，只有路径特征和断点可用。
     * 这是最危险的场景 —— 若规则把 playlist 域名当作切片域名条件，
     * 会连带删掉每一个紧跟断点的正片切片。
     */
    @Test
    public void sameDomainRuleMustNotDeleteLegitSegmentsAfterDiscontinuity() {
        AdAttribution plan = HlsSegmentClassifier.classify(
                ev(seg(AD_FROM, AD_COUNT, "v.example.com", "/ads/", 8.0, true),
                        outsideAround("v.example.com", 8.0), true, false));
        if (plan == null) return; // 通道弃权也是可接受的安全结果

        HlsManifestCleaner.Result res = HlsManifestCleaner.clean(BASE,
                manifest("https://v.example.com", "/ads/", 8.0),
                List.of(toRule(plan.payload())));

        assertEquals("只能删广告块，不得误删断点后的正片", AD_COUNT, res.removedSegments());
    }

    /**
     * 整站切片都在独立 CDN（api.site.com + cdn.site-x.com 这种常见拆分）。
     * 没有同域切片作对照时，任何域名条件都命中全站 → cleaner 因
     * removedCount == segmentCount 回退，而 HlsAdblockPipeline 的 fallback
     * 会连带停掉 legacy 启发式，比不加规则更差。两个通道都必须弃权。
     */
    @Test
    public void siteWideCdnMustNotProduceAnyRule() {
        List<SegmentFact> inside = seg(AD_FROM, AD_COUNT, "cdn.site-x.com", "/seg/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, AD_FROM, "cdn.site-x.com", "/seg/", 8.0, false));
        outside.addAll(seg(AD_FROM + AD_COUNT, TOTAL - AD_FROM - AD_COUNT,
                "cdn.site-x.com", "/seg/", 8.0, true));
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                80_000, 100_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧", "线", "1", "api.site.com", "/play/index.m3u8", true,
                inside, outside, true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution hls = HlsSegmentClassifier.classify(evidence);
        if (hls != null) {
            assertFalse("HLS 通道不得为全站 CDN 生成可落地规则", hls.actionable());
        }
        assertNull("域名通道必须弃权", DomainReputationClassifier.classify(evidence,
                new DomainReputationClassifier.Input(
                        List.of(), List.of("api.site.com"), List.of(), "")));
    }

    /** 用户框选覆盖整个 playlist 时无对照可用，同样不得生成规则。 */
    @Test
    public void intervalCoveringWholePlaylistMustNotProduceAnyRule() {
        AdIntervalEvidence evidence = new AdIntervalEvidence(
                0, 240_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧", "线", "1", "v.example.com", "/play/index.m3u8", true,
                seg(0, TOTAL, "v.example.com", "/ads/", 8.0, true), List.of(),
                true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution hls = HlsSegmentClassifier.classify(evidence);
        if (hls != null) {
            assertFalse("无对照组时不得生成可落地规则", hls.actionable());
        }
    }
}
