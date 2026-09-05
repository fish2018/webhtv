package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * pathPatternsOf 的双向校验用 fact.path()（已去 query），而
 * HlsManifestCleaner.matchesPattern 匹配 resolved.toString()（含 query）。
 * 正片 query 里含 hint 时，校验放行但运行时会被删。
 */
public class QueryHintMismatchTest {

    private static final String BASE = "https://v.example.com/play/index.m3u8";

    @Test public void legitSegmentWithHintInQueryMustNotBeDeleted() {
        // 区间内 3 片是真广告（路径含 /ads/），区间外正片路径干净但 query 含 /ads/
        List<SegmentFact> inside = new ArrayList<>();
        for (int i = 10; i < 13; i++) {
            inside.add(new SegmentFact(i, "v.example.com", "/ads/" + i + ".ts", 8.0, i == 10));
        }
        List<SegmentFact> outside = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // path 不含 hint —— 双向校验会认为 outside 干净
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = 13; i < 30; i++) {
            outside.add(new SegmentFact(i, "v.example.com", "/seg/" + i + ".ts", 8.0, i == 13));
        }
        AdIntervalEvidence ev = new AdIntervalEvidence(
                80_000, 104_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧", "线", "1", "v.example.com", "/play/index.m3u8", true,
                inside, outside, true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());

        AdAttribution plan = HlsSegmentClassifier.classify(ev);
        assertNotNull("同域 + 路径特征应产出方案", plan);
        System.out.println("payload.regex=" + plan.payload().regex());

        // 真实 manifest：正片带 ?ref=/ads/ 的 query
        StringBuilder m = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 30; i++) {
            boolean ad = i >= 10 && i < 13;
            if (i == 10 || i == 13) m.append("#EXT-X-DISCONTINUITY\n");
            m.append("#EXTINF:8.0,\n");
            m.append(ad ? "https://v.example.com/ads/" + i + ".ts\n"
                        : "https://v.example.com/seg/" + i + ".ts?ref=/ads/\n");
        }
        m.append("#EXT-X-ENDLIST\n");

        RulePayload p = plan.payload();
        HlsAdRule rule = HlsAdRule.createUserRule("id", "n",
                p.playlistHostSuffixes(), p.hosts(), p.regex(),
                p.hasDurationRange() ? p.durationMin() : null,
                p.hasDurationRange() ? p.durationMax() : null,
                p.requireDiscontinuity(), p.requireCrossDomain(), p.minimumSignals());
        HlsManifestCleaner.Result res = HlsManifestCleaner.clean(BASE, m.toString(), List.of(rule.compile()));
        System.out.println("removed=" + res.removedSegments() + " fallback=" + res.fallback());
        assertEquals("只能删 3 片广告，不得误删 query 含 hint 的正片", 3, res.removedSegments());
    }
}
