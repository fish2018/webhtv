package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 叠加校验：一条单独安全的规则，与站内已启用规则合并后不得越过 cleaner 的
 * 回退闸门。
 *
 * <p>{@code HlsManifestCleaner.matches} 是「任一规则命中即删」，而三道闸门
 * （全删 / 删除比例 &gt; 35% / 删除时长 &gt; 90s）作用于合并后的删除总量。越过闸门
 * 会让整份 manifest 回退 —— 不只新规则无效，连原有的结构化净化也一起丢掉。
 */
public class RuleStackingTest {

    private static final String PLAYLIST_HOST = "v.example.com";

    private static List<SegmentFact> seg(int from, int count, String host, String path,
                                       double duration, boolean discFirst) {
        List<SegmentFact> facts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            facts.add(new SegmentFact(from + i, host, path + (from + i) + ".ts",
                    duration, discFirst && i == 0));
        }
        return facts;
    }

    private static AdIntervalEvidence evidence(List<SegmentFact> inside, List<SegmentFact> outside) {
        return new AdIntervalEvidence(
                80_000, 99_200, StartOrigin.USER_MARKED,
                "site", "站点", "剧名", "线路", "第 1 集",
                PLAYLIST_HOST, "/play/index.m3u8", true,
                inside, outside, true, true, List.of(), false, List.of(),
                AudioIntervalFact.unavailable(), SpeechIntervalFact.unavailable());
    }

    private static HlsManifestCleaner.Rule rule(String hostSuffix, int minimumSignals) {
        return HlsAdRule.createUserRule("id-" + hostSuffix, "n",
                List.of(PLAYLIST_HOST), List.of(hostSuffix), List.of(),
                null, null, false, true, minimumSignals).compile();
    }

    /**
     * 30 片：0-9 正片、10-12 广告 A、13-21 由已启用规则负责的广告 B、22-29 正片。
     *
     * <p>广告 B 的时长刻意不同于广告 A：分类器生成的规则会带上
     * {@code duration 6.3–6.5} 条件，若两者时长相同，「时长 + 跨域」就凑够
     * minimumSignals=2，新规则**自己**就删掉了区间外的广告 B —— 单独校验先失败，
     * 测不到叠加逻辑。这个混淆由 {@link #abstainsWhenDurationWindowAlsoMatchesOutside}
     * 单独覆盖。
     */
    private static AdIntervalEvidence stackedEvidence() {
        List<SegmentFact> inside = seg(10, 3, "ad-a.other.com", "/ads/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        // 已启用规则会删掉这 9 片
        outside.addAll(seg(13, 9, "ad-b.other.com", "/adb/", 4.0, true));
        outside.addAll(seg(22, 8, PLAYLIST_HOST, "/seg/", 8.0, true));
        return evidence(inside, outside);
    }

    @Test
    public void rejectsNewRuleWhenCombinedRemovalCrossesRatioGate() {
        // 单独删 3/30 = 10%，与已启用规则的 9 片合并后 12/30 = 40% > 35%
        AdIntervalEvidence evidence = stackedEvidence();
        HlsManifestCleaner.Rule active = rule("ad-b.other.com", 2);

        assertTrue("新规则单独是安全的",
                RuleSelfCheck.isSafe(evidence, rule("ad-a.other.com", 2), List.of()));
        assertFalse("与已启用规则叠加后越过 35% 闸门，必须拒绝",
                RuleSelfCheck.isSafe(evidence, rule("ad-a.other.com", 2), List.of(active)));
    }

    @Test
    public void acceptsNewRuleWhenCombinedRemovalStaysUnderGate() {
        // 已启用规则只负责 2 片，合并后 5/30 = 16.7%
        List<SegmentFact> inside = seg(10, 3, "ad-a.other.com", "/ads/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        outside.addAll(seg(13, 2, "ad-b.other.com", "/adb/", 6.4, true));
        outside.addAll(seg(15, 15, PLAYLIST_HOST, "/seg/", 8.0, true));
        AdIntervalEvidence evidence = evidence(inside, outside);

        assertTrue("合并后仍在闸门内，应放行",
                RuleSelfCheck.isSafe(evidence, rule("ad-a.other.com", 2),
                        List.of(rule("ad-b.other.com", 2))));
    }

    /**
     * 已启用规则**自己**就让 manifest 回退时，依然拒绝。
     *
     * <p>曾经在这里放行，理由是「那时生产上已整体不净化、没有原有净化需要保护」。
     * 那是错的：判据只对证据里这一份 manifest 成立，而规则一旦保存就对该站所有
     * 剧集长期生效 —— 同一站点另一集里已启用规则可能并未越界，叠加新规则后越界
     * 回退，原本正常工作的删除全丢。更根本的是，合并后回退时新规则在这份 manifest
     * 上删 0 片，对它是否有效没有任何证据，谈不上背书。
     */
    @Test
    public void rejectsEvenWhenActiveRulesThemselvesAlreadyCauseFallback() {
        AdIntervalEvidence evidence = stackedEvidence();
        // 一条命中全站同域切片的已启用规则：18/30 越过 35% 闸门，单独就会回退
        HlsManifestCleaner.Rule overbroad = HlsAdRule.createUserRule(
                "wide", "wide", List.of(PLAYLIST_HOST), List.of(PLAYLIST_HOST),
                List.of(), null, null, false, false, 1).compile();

        assertFalse("合并后回退即拒绝，不追究是谁造成的",
                RuleSelfCheck.isSafe(evidence, rule("ad-a.other.com", 2), List.of(overbroad)));
    }

    /**
     * {@code activeRules} 为 {@code null} 表示「无法确定站内有哪些规则生效」，
     * 必须拒绝 —— 与空列表（确无规则生效）语义相反。
     *
     * <p>数据源在遇到模拟不了的已启用规则、或读配置失败时返回 null。若把它当成
     * 空列表处理，叠加校验就被整体跳过，是 fail-open。
     */
    @Test
    public void rejectsWhenActiveRulesAreUnknown() {
        assertFalse("规则集未知时不能为新规则背书",
                RuleSelfCheck.isSafe(stackedEvidence(), rule("ad-a.other.com", 2), null));
    }

    /**
     * 分类器生成的时长窗口会把区间外同时长的跨域切片一起删掉时，必须弃权。
     *
     * <p>这不是叠加问题、也不钉本次改动 —— 单独一条规则就已经越界：
     * {@code duration 6.3–6.5} 与 {@code requireCrossDomain} 凑够 minimumSignals=2，
     * 另一段广告（本应交给别的规则或保持原样）落进同一窗口就被误删。刻意传
     * {@code List.of()} 并让删除比例停在闸门内（5/30），从而钉住判据来自
     * {@code isSafeAlone} 的区间外 URI 次数比对，而不是回退闸门。
     *
     * <p>留在这个文件里是因为它解释了 {@link #stackedEvidence} 为何要把广告 B 的
     * 时长与广告 A 错开 —— 否则叠加用例会先被这条判据拦掉，测不到闸门。
     */
    @Test
    public void abstainsWhenDurationWindowAlsoMatchesOutside() {
        List<SegmentFact> inside = seg(10, 3, "ad-a.other.com", "/ads/", 6.4, true);
        List<SegmentFact> outside = new ArrayList<>(
                seg(0, 10, PLAYLIST_HOST, "/seg/", 8.0, false));
        // 与区间内同时长、同为跨域，但域名与路径都不同
        outside.addAll(seg(13, 2, "ad-b.other.com", "/adb/", 6.4, true));
        outside.addAll(seg(15, 15, PLAYLIST_HOST, "/seg/", 8.0, true));

        assertNull("时长窗口会连带删除区间外切片，必须弃权",
                HlsSegmentClassifier.classify(evidence(inside, outside), List.of()));
    }

    /** 端到端：分类器在叠加会越界时整体弃权。 */
    @Test
    public void classifierAbstainsWhenStackingCrossesGate() {
        AdIntervalEvidence evidence = stackedEvidence();
        List<HlsManifestCleaner.Rule> active = List.of(rule("ad-b.other.com", 2));

        AdAttribution alone = HlsSegmentClassifier.classify(evidence, List.of());
        AdAttribution stacked = HlsSegmentClassifier.classify(evidence, active);

        assertTrue("无叠加时应能产出方案", alone != null && alone.actionable());
        assertTrue("叠加越界时必须弃权或不可落地",
                stacked == null || !stacked.actionable());
    }
}
