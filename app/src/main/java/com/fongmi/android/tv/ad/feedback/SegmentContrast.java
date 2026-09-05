package com.fongmi.android.tv.ad.feedback;

import java.util.List;
import java.util.Locale;

/**
 * 生成 HLS 规则前必须成立的对照判据，两个分类器共用的单一真源。
 *
 * <p>核心不变量：<b>任何写进规则的条件，必须对广告块内每片成立、且对块外每片不成立。</b>
 * 违反它的规则会命中全站切片 → {@code HlsManifestCleaner} 因
 * {@code removedCount == segmentCount} 或超过删除比例阈值而回退原文 →
 * {@code HlsAdblockPipeline} 在回退时连带跳过 legacy {@code HlsAdsParser}，
 * 结果该站去广能力整体倒退，比不加规则更差。
 *
 * <p>此前这条判据被拆在两个分类器里各写一份，四轮评审里换了四个入口反复出问题
 * （同域 hosts、域名通道缺对照、HLS 通道缺 crossDomain 门槛、路径正则跨 query），
 * 因此收敛到本类统一表达。
 */
final class SegmentContrast {

    /**
     * 区间外同域切片至少要占的比例。单一同域切片不足以证明「区间外整体不是同一 CDN」：
     * playlist 开头一片同域、其余三百片都在独立 CDN 是真实存在的架构。
     */
    private static final double MIN_SAME_DOMAIN_RATIO = 0.5d;

    private SegmentContrast() {
    }

    /**
     * 区间外是否存在足以作为对照的同域切片。
     *
     * <p>要求同域切片占区间外多数，而不只是「存在一个」。
     */
    static boolean hasSameDomainOutside(AdIntervalEvidence evidence) {
        String playlistHost = evidence.playlistHost();
        if (playlistHost.isEmpty()) return false;
        List<SegmentFact> outside = evidence.outside();
        if (outside.isEmpty()) return false;
        long sameDomain = outside.stream()
                .filter(fact -> fact.hostEndsWith(playlistHost))
                .count();
        return sameDomain >= Math.max(1L, (long) Math.ceil(outside.size() * MIN_SAME_DOMAIN_RATIO));
    }

    /**
     * 区间外是否**完全没有**切片路径含该目录段。
     *
     * <p>用 {@code path} 而非完整 URL 比较，与 {@code HlsSegmentClassifier.pathOnlyPattern}
     * 生成的锚定正则语义一致 —— 那条正则同样只匹配 path。
     */
    static boolean pathAbsentOutside(AdIntervalEvidence evidence, String hint) {
        List<SegmentFact> outside = evidence.outside();
        // 没有对照数据时无法证明「区间外不含」，一律视为不成立
        if (outside.isEmpty()) return false;
        return outside.stream()
                .noneMatch(fact -> fact.path().toLowerCase(Locale.US).contains(hint));
    }

    /** 区间内每片路径都含该目录段。 */
    static boolean pathPresentInside(AdIntervalEvidence evidence, String hint) {
        List<SegmentFact> inside = evidence.inside();
        if (inside.isEmpty()) return false;
        return inside.stream()
                .allMatch(fact -> fact.path().toLowerCase(Locale.US).contains(hint));
    }
}
