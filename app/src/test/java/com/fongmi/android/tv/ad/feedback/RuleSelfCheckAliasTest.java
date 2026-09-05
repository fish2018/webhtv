package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 第六轮评审用随机对抗找出的假阳性：{@code cleaned.contains(uri)} 是子串查找，
 * 被删片的 URI 若是某个保留片 URI 的子串，判据就恒为真。
 *
 * <p>配合 {@code removedSegments() == inside.size()} 只比总数，
 * 「漏删一片区间内 + 误删一片区间外」会互相抵消而放行。
 */
public class RuleSelfCheckAliasTest {

    private static final String PLAYLIST_HOST = "v.example.com";

    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside) {
        return new AdIntervalEvidence(
                160_000, 190_000, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/index.m3u8", true,
                inside, outside, true, false, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    /**
     * 无扩展名的递增序号切片：删掉 {@code /s/1} 后，保留的 {@code /s/12}
     * 让子串判据看不见这次误删。载荷同时命中区间外那片（duration + crossDomain）。
     */
    @Test
    public void rejectsWhenRemovedOutsideUriIsSubstringOfKeptOne() {
        List<SegmentFact> inside = new ArrayList<>();
        for (int i = 20; i <= 22; i++) {
            inside.add(new SegmentFact(i, "ad.other.com", "/ads/" + (i - 19), 6.4, i == 20));
        }
        // 用户多框了一片同域正片，制造「漏删」以抵消下面的「误删」
        inside.add(new SegmentFact(23, PLAYLIST_HOST, "/s/23", 8.0, false));

        List<SegmentFact> outside = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/s/" + (100 + i), 8.0, false));
        }
        // 会被 duration+crossDomain 命中的正片，其 URI 是下一片的前缀
        outside.add(new SegmentFact(24, "cdn2.net", "/s/1", 6.4, false));
        outside.add(new SegmentFact(25, "cdn2.net", "/s/12", 8.0, false));

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad.other.com"), List.of(),
                6.3, 6.5, false, true, 2);

        assertFalse("区间外正片被误删时必须拒绝，不能因子串遮蔽而放行",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }

    /**
     * 同一 URI 同时出现在区间内与区间外时，两道判据会互相抵消。
     *
     * <p>规则漏删区间内那片、却删掉区间外那片：URI 次数比对因两者同名而仍然相等，
     * {@code removedSegments() == inside.size()} 也仍然成立，于是放行 —— 真实运行
     * 删正片且 {@code fallback=false}，错误不被回退兜住。真实 URL 靠 query 区分
     * （{@code /hls/seg.ts?i=10} 与 {@code ?i=25}），而证据按去敏要求丢掉 query，
     * 合成 manifest 无法区分，只能弃权。
     */
    @Test
    public void abstainsWhenTheSameUriAppearsInsideAndOutside() {
        // 区间内 1 片广告，URI 为 X，时长 8.0（只满足 host 一个信号，会被漏删）
        List<SegmentFact> inside = List.of(
                new SegmentFact(12, "ad.other.com", "/ads/x.ts", 8.0, true));

        List<SegmentFact> outside = new ArrayList<>();
        // 区间外同一个 URI X，时长 6.4（host + duration 两个信号，会被误删）
        outside.add(new SegmentFact(5, "ad.other.com", "/ads/x.ts", 6.4, false));
        for (int i = 0; i < 10; i++) {
            outside.add(new SegmentFact(20 + i, PLAYLIST_HOST, "/seg/" + i + ".ts", 8.0, false));
        }

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad.other.com"), List.of(),
                6.3, 6.5, false, false, 2);

        assertFalse("删反了（留区间内、删区间外）却因 URI 重名使判据抵消，必须拒绝",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }

    /** 代理式内嵌 URL 的 path 会原样保留，同样能遮蔽真实删除。 */
    @Test
    public void rejectsWhenProxyStylePathShadowsRemovedSegment() {
        List<SegmentFact> inside = List.of(
                new SegmentFact(2, "ad.other.com", "/seg/1.ts", 8.0, true),
                new SegmentFact(3, "edge7." + PLAYLIST_HOST, "/ads/1.ts", 8.0, false));
        List<SegmentFact> outside = List.of(
                new SegmentFact(0, "ad.other.com", "/a.ts", 8.0, false),
                new SegmentFact(1, "edge7." + PLAYLIST_HOST, "/x/ads/1.ts", 6.4, false),
                // 内嵌完整 URL：pathOf 会原样返回，成为上面 index=0 的遮蔽者
                new SegmentFact(4, "edge7." + PLAYLIST_HOST,
                        "/p/https://ad.other.com/a.ts", 10.0, false),
                new SegmentFact(5, "shared-cdn.net", "/seg/1.ts", 10.0, false));

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad.other.com"), List.of(),
                Double.NaN, Double.NaN, false, true, 2);

        assertFalse("内嵌 URL 的 path 不得遮蔽区间外的误删",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }

    /**
     * 同一 URI 在 playlist 中复用（片头片尾同一 promo）不得造成误判。
     *
     * <p>现在由 {@code hasUriOverlap} 前置拦下，不再走到 URI 次数比对。保留这条
     * 测试是因为它钉的是**结果**（必须拒绝），而拒绝的理由换成更早的判据仍然正确；
     * 次数比对本身由 {@link #rejectsWhenRemovedOutsideUriIsSubstringOfKeptOne}
     * 等区间外不重名的用例继续覆盖。
     */
    @Test
    public void rejectsWhenDuplicateUriAppearsInsideAndOutside() {
        List<SegmentFact> inside = List.of(
                new SegmentFact(10, "ad.other.com", "/promo.ts", 6.4, true),
                new SegmentFact(11, "ad.other.com", "/promo.ts", 6.4, false));
        List<SegmentFact> outside = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/s/" + i + ".ts", 8.0, false));
        }
        // 片尾复用同一个 promo 切片：它会被同一条规则删掉
        outside.add(new SegmentFact(29, "ad.other.com", "/promo.ts", 6.4, false));
        for (int i = 12; i < 29; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/s/" + i + ".ts", 8.0, i == 12));
        }

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad.other.com"), List.of(),
                Double.NaN, Double.NaN, false, true, 2);

        assertFalse("区间外同 URI 的切片会被一并删除，必须拒绝",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }

    /** 规模预判：真实 manifest 行密度更高，接近上限时必须提前拒绝。 */
    @Test
    public void rejectsWhenRealManifestWouldExceedLineLimit()
    {
        List<SegmentFact> inside = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            inside.add(new SegmentFact(i, "ad.other.com", "/ads/" + i + ".ts", 6.4, i == 0));
        }
        List<SegmentFact> outside = new ArrayList<>();
        // 合成 2 行/片时不超 20000，真实 3 行/片时会超
        for (int i = 3; i < 7_000; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/s/" + i + ".ts", 8.0, i == 3));
        }

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad.other.com"), List.of(),
                Double.NaN, Double.NaN, false, true, 2);

        assertFalse("真实 manifest 会因行数超限回退，规则不该保存",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }

    /** 健康场景仍须放行，否则自检等于把功能关掉。 */
    @Test
    public void stillAcceptsCleanlySeparableAdBlock() {
        List<SegmentFact> inside = new ArrayList<>();
        for (int i = 10; i < 13; i++) {
            inside.add(new SegmentFact(i, "ad-cdn.other.com", "/ads/" + i + ".ts", 6.4, i == 10));
        }
        List<SegmentFact> outside = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/seg/" + i + ".ts", 8.0, false));
        }
        for (int i = 13; i < 30; i++) {
            outside.add(new SegmentFact(i, PLAYLIST_HOST, "/seg/" + i + ".ts", 8.0, i == 13));
        }

        RulePayload payload = RulePayload.ofHlsRule(
                List.of(PLAYLIST_HOST), List.of("ad-cdn.other.com"), List.of(),
                Double.NaN, Double.NaN, false, true, 2);

        assertTrue("干净可分的广告块必须放行",
                RuleSelfCheck.isSafe(evidence(inside, outside), payload));
    }
}
