package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则自检的边界回归：第五轮评审实测出的五类有害规则，必须全部被拒。
 *
 * <p>这些场景的共同点是「逐条件的对照校验都通过，但规则的实际效果有害」——
 * 正是 {@link RuleSelfCheck} 存在的理由。
 */
public class RuleSelfCheckTest {

    private static final String PLAYLIST_HOST = "v.example.com";

    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside) {
        long start = inside.isEmpty() ? 0 : Math.round(inside.get(0).index() * 8_000d);
        double span = inside.stream().mapToDouble(SegmentFact::durationSec).sum();
        return new AdIntervalEvidence(
                start, start + Math.round(span * 1000d), StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/index.m3u8", true,
                inside, outside, true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    private static List<SegmentFact> seg(int from, int count, String host, String path,
                                       double duration, boolean discFirst) {
        List<SegmentFact> facts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            facts.add(new SegmentFact(from + i, host, path + (from + i) + ".ts",
                    duration, discFirst && i == 0));
        }
        return facts;
    }

    /** 正片切片同域、时长与广告一致：duration + crossDomain 组合会误删它们。 */
    @Test
    public void rejectsWhenOutsideSegmentsFallInsideTheDurationWindow() {
        List<SegmentFact> inside = seg(10, 3, "ad-cdn.other.com", "/seg/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        // 边缘节点上的正片，时长恰好落在广告的 ±0.1s 窗口内
        outside.addAll(seg(13, 5, "legit-edge.fastly-cdn.net", "/seg/", 6.4, false));
        outside.addAll(seg(18, 12, PLAYLIST_HOST, "/seg/", 8.0, false));

        AdAttribution plan = HlsSegmentClassifier.classify(evidence(inside, outside));
        if (plan != null) assertFalse(plan.actionable());
    }

    /** 广告与正片共用同一第三方 CDN，只有路径不同：host 条件会误删正片。 */
    @Test
    public void rejectsWhenAdAndContentShareTheSameCdn() {
        List<SegmentFact> inside = seg(10, 3, "shared-cdn.net", "/ads/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        outside.addAll(seg(13, 5, "shared-cdn.net", "/seg/", 8.0, false));
        outside.addAll(seg(18, 12, PLAYLIST_HOST, "/seg/", 8.0, false));

        AdIntervalEvidence ev = evidence(inside, outside);
        AdAttribution hls = HlsSegmentClassifier.classify(ev);
        if (hls != null) assertFalse(hls.actionable());
        assertNull(DomainReputationClassifier.classify(ev,
                new DomainReputationClassifier.Input(
                        List.of(), List.of(PLAYLIST_HOST), List.of(), "")));
    }

    /** 删除比例超过 cleaner 的 35% 上限：规则注定失效，不该保存。 */
    @Test
    public void rejectsWhenRemovalRatioExceedsCleanerLimit() {
        List<SegmentFact> inside = seg(0, 19, "ad-cdn.other.com", "/ads/", 6.4, true);
        List<SegmentFact> outside = seg(19, 1, PLAYLIST_HOST, "/seg/", 8.0, false);

        AdAttribution plan = HlsSegmentClassifier.classify(evidence(inside, outside));
        if (plan != null) assertFalse(plan.actionable());
    }

    /** 删除总时长超过 90s：即使比例只有 20%，cleaner 仍会整体回退。 */
    @Test
    public void rejectsWhenRemovedDurationExceedsCleanerLimit() {
        List<SegmentFact> inside = seg(10, 20, "ad-cdn.other.com", "/ads/", 6.0, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        outside.addAll(seg(30, 70, PLAYLIST_HOST, "/seg/", 8.0, false));

        AdAttribution plan = HlsSegmentClassifier.classify(evidence(inside, outside));
        if (plan != null) assertFalse(plan.actionable());
    }

    /**
     * 正片在 playlist 的子域上，且时长落入广告的 duration 窗口。
     *
     * <p>分类器的 {@code hostEndsWith} 视子域为同域（计入对照分母帮助放行），
     * 而 cleaner 的 {@code requireCrossDomain} 用 {@code !equals} 判定，视其为跨域
     * 并给 1 分；再叠加 duration 的 1 分就够 2 分被删。两侧对「同域」的定义相反。
     *
     * <p>区间外多数为同域 8.0s 正片，使 duration 众数为 8.0 从而让时长离群成立、
     * 时长条件被编码进规则 —— 这是该场景成立的前提。
     */
    @Test
    public void rejectsWhenContentSitsOnPlaylistSubdomainInsideDurationWindow() {
        List<SegmentFact> inside = seg(10, 3, "ad-cdn.other.com", "/seg/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        outside.addAll(seg(13, 12, PLAYLIST_HOST, "/seg/", 8.0, false));
        // 子域上的正片，时长与广告相同
        outside.addAll(seg(25, 5, "edge7." + PLAYLIST_HOST, "/seg/", 6.4, false));

        AdAttribution plan = HlsSegmentClassifier.classify(evidence(inside, outside));
        if (plan != null) assertFalse(plan.actionable());
    }

    /** 健康场景必须放行，否则自检等于把功能关掉。 */
    @Test
    public void acceptsCleanlySeparableAdBlock() {
        List<SegmentFact> inside = seg(10, 3, "ad-cdn.other.com", "/ads/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        outside.addAll(seg(13, 17, PLAYLIST_HOST, "/seg/", 8.0, true));

        AdAttribution plan = HlsSegmentClassifier.classify(evidence(inside, outside));
        assertTrue("干净可分的广告块必须能产出可落地规则",
                plan != null && plan.actionable());
    }
}
