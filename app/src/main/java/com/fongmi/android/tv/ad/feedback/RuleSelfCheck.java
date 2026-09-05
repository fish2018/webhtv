package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.bean.HlsAdRule;
import com.fongmi.android.tv.utils.HlsManifestCleaner;

import java.util.List;
import java.util.Locale;

/**
 * 规则自检：把候选载荷交给**真实**的 {@link HlsManifestCleaner} 跑一遍合成 manifest，
 * 只有「区间内全删、区间外一片不删、且未触发回退」才放行。
 *
 * <p>这是核心不变量的唯一守门人。前五轮评审反复出现同一类缺陷 —— 每轮为某一类条件
 * 补上对照校验，下一轮就暴露另一类没校验的条件（同域 hosts、缺 crossDomain 门槛、
 * 路径正则跨 query、采样截断、duration+crossDomain 组合、共用 CDN 靠路径区分）。
 * 根因是那些修法都在验证「条件」，而不变量说的是「规则的实际效果」。
 *
 * <p>用执行引擎自身做判据，好处是对未来新增的任何条件类型自动生效，并且顺带覆盖了
 * cleaner 的三道回退闸门（全删、删除比例 &gt; 35%、删除时长 &gt; 90s）—— 那些闸门即使
 * 条件完全有区分度也会让规则整体失效。
 */
final class RuleSelfCheck {

    /** 合成 manifest 的基准地址，只需 host 与 playlist 一致即可。 */
    private static final String SYNTHETIC_BASE_SCHEME = "https://";

    private RuleSelfCheck() {
    }

    /**
     * 真实 manifest 每片的行数上界。除 EXTINF + URI 外，常见还带
     * {@code #EXT-X-KEY}、{@code #EXT-X-PROGRAM-DATE-TIME} 与
     * {@code #EXT-X-DISCONTINUITY}，实测这种 5 行/片的 playlist 能正常净化。
     *
     * <p>取上界而非典型值：低估会让规则在真实 manifest 上因行数超限而回退 ——
     * 规则存下来却永久无效，用户看到「已保存」但广告依旧。高估只是让超长
     * playlist 弃权，而按 4s/片算 4000 片已是 4.4 小时，正片剧集触达不到。
     *
     * <p>{@code #EXT-X-BYTERANGE} 不计入：{@code HlsManifestCleaner} 对含该标签的
     * manifest 无条件回退，这类 playlist 从不参与净化。
     */
    private static final int LINES_PER_SEGMENT_REAL = 5;
    /** 与 {@code HlsManifestCleaner.MAX_MANIFEST_LINES} 一致。 */
    private static final int MAX_MANIFEST_LINES = 20_000;
    /** 为真实 manifest 的头部标签预留的行数余量。 */
    private static final int MANIFEST_HEADER_LINES = 16;

    /**
     * 只做单独校验，不含叠加。仅供单测直接验证 {@link #isSafeAlone} 的判据 ——
     * 生产路径必须走三参版本，把已启用规则一起算进去。
     *
     * @return 载荷是否安全可落地
     */
    static boolean isSafe(AdIntervalEvidence evidence, RulePayload payload) {
        return isSafe(evidence, payload, List.of());
    }

    /** 带叠加校验的载荷版本。 */
    static boolean isSafe(AdIntervalEvidence evidence, RulePayload payload,
                          List<HlsManifestCleaner.Rule> activeRules) {
        if (payload.isEmpty()) return false;
        HlsManifestCleaner.Rule rule = compile(payload);
        if (rule == null) return false;
        return isSafe(evidence, rule, activeRules);
    }

    /**
     * 校验规则单独生效、以及与站内已启用规则叠加后是否都安全。
     *
     * <p>也是「启用已有规则」路径的守门人：那条路径此前只判断规则的 hostSuffixes
     * 与区间内某片同域，不看其余条件。实测一条 {@code minimumSignals=1} 的既有规则
     * 在「广告与正片共用同一 CDN」时会命中全部切片。
     *
     * <p>{@code HlsManifestCleaner.matches} 是「任一规则命中即删」，而三道回退闸门
     * （全删 / 删除比例 &gt; 35% / 删除时长 &gt; 90s）作用于**合并后**的删除总量。
     * 一条单独安全的规则叠加到已生效规则上可能越过闸门，导致整份 manifest 回退 ——
     * 不只新规则无效，连原有的结构化净化也一起丢掉。
     *
     * <p>刻意不做「区间外切片逐一比对」：合并后的删除集恒等于两条规则各自删除集的
     * 并集（{@code matches} 逐片独立求值），而 {@link #isSafeAlone} 已经钉住「新规则
     * 单独只删区间内」。合并后多删的区间外切片全部来自已启用规则 —— 那是它们本来
     * 就会删的，与新规则无关。于是新规则对区间外的影响恒为零，只有闸门会因叠加而
     * 翻转，所以只查闸门。
     *
     * <p>合并后回退即拒绝，**不追究是谁造成的**。曾经尝试过「已启用规则自己就回退
     * 时放行」，理由是那时生产上已整体不净化、没有原有净化需要保护 —— 那是错的：
     * 判据只对证据里这一份 manifest 成立，而规则一旦保存就对该站所有剧集长期生效。
     * 实测同一站点另一集里已启用规则并未越界（删 10/30），叠加新规则后越界回退，
     * 原本正常工作的 10 片删除全丢。更根本的是，合并后回退时新规则在这份 manifest
     * 上删 0 片，我们对它是否有效**没有任何证据**，谈不上背书。
     *
     * <p>代价是：站内只要有一条过宽的内置或接口规则处于启用，该站点的反馈功能就
     * 一直拿不到方案。这是真实的体验问题，但解法是把肇事规则指出来让用户禁用，
     * 不是保存一条未经验证的规则。
     *
     * @param activeRules 当前已启用且能被忠实模拟的规则。空表示站内无规则生效；
     *                    {@code null} 表示无法确定（存在模拟不了的已启用规则，或读取
     *                    配置失败），此时一律拒绝 —— 预测不了闸门就不能为规则背书。
     */
    static boolean isSafe(AdIntervalEvidence evidence, HlsManifestCleaner.Rule rule,
                          List<HlsManifestCleaner.Rule> activeRules) {
        if (!isSafeAlone(evidence, rule)) return false;
        if (activeRules == null) return false;
        if (activeRules.isEmpty()) return true;

        List<SegmentFact> ordered = ordered(evidence);
        List<HlsManifestCleaner.Rule> merged = new java.util.ArrayList<>(activeRules);
        merged.add(rule);
        return !fallsBack(evidence, synthesize(evidence, ordered), merged);
    }

    /**
     * 给定规则集在合成 manifest 上是否触发回退。判不出来时按「回退」处理，
     * 让调用方走拒绝路径。
     *
     * <p>这是对**真实运行**的预测，而合成 manifest 按去敏要求丢掉了 query 与
     * fragment：靠 query 区分广告的已启用规则在这里命中不到切片，闸门预测会偏乐观。
     * {@code AdFeedbackDataSource.activeHlsRules} 因此在遇到这类规则时整体返回
     * {@code null} 而不是把它悄悄剔除 —— 剔除会让「合并后不回退」这个结论建立在
     * 比生产更小的规则集上，而那个方向不是安全的：子集不回退推不出全集不回退。
     */
    private static boolean fallsBack(AdIntervalEvidence evidence, String manifest,
                                     List<HlsManifestCleaner.Rule> rules) {
        String base = SYNTHETIC_BASE_SCHEME + evidence.playlistHost() + "/index.m3u8";
        try {
            HlsManifestCleaner.Result result = HlsManifestCleaner.clean(base, manifest, rules);
            return result == null || result.fallback();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** 规则单独生效时的校验。 */
    private static boolean isSafeAlone(AdIntervalEvidence evidence, HlsManifestCleaner.Rule rule) {
        if (rule == null) return false;
        if (evidence.playlistHost().isEmpty()) return false;
        if (evidence.inside().isEmpty() || evidence.outside().isEmpty()) return false;
        // 区间内外存在同一个 URI 时，读回判据无法区分对错，只能弃权：
        // 「删了区间内的 U、留了区间外的 U」（正确）与「留了区间内的 U、删了区间外的
        // U」（删反）两种结果的 URI 次数完全相同，removedSegments 也都等于 inside 的
        // 片数 —— 两道判据同时被抵消，放行后真实运行删正片且 fallback=false，
        // 用户直接看到跳帧。真实 URL 靠 query 区分（如 /hls/seg.ts?i=10 与 ?i=25），
        // 而证据按去敏要求丢掉了 query，合成 manifest 里两者塌成一个。
        //
        // 代价是也拒掉一部分本来安全的重名场景（如内外同 URI 但时长不同、规则确实
        // 只命中区间内那片）。接受这个代价：判据无法自证对错时弃权是唯一安全方向，
        // 而真实 playlist 的切片 URI 通常按位置唯一，内外重名基本只来自 query 区分
        // —— 误拒几乎全部落在危险场景上。若将来要救回这部分，得让合成 URI 带上
        // 位置标记（放在 query 里，锚定正则的 ^[^?#]* 跨不过 ?），不是补判据能解决的。
        if (hasUriOverlap(evidence)) return false;

        // 真实 manifest 的行密度高于合成，按上界预判：若真实会因规模回退，
        // 这条规则在播放时不会生效，不该保存。除切片行外还要留出 header
        // （#EXTM3U / #EXT-X-VERSION / #EXT-X-TARGETDURATION /
        // #EXT-X-MEDIA-SEQUENCE / #EXT-X-ENDLIST 等）的余量，否则恰好卡在
        // 边界的 playlist 会被放行而真实仍然回退。
        int total = evidence.inside().size() + evidence.outside().size();
        if ((long) total * LINES_PER_SEGMENT_REAL + MANIFEST_HEADER_LINES
                > MAX_MANIFEST_LINES) return false;

        List<SegmentFact> ordered = ordered(evidence);
        String base = SYNTHETIC_BASE_SCHEME + evidence.playlistHost() + "/index.m3u8";
        HlsManifestCleaner.Result result;
        try {
            result = HlsManifestCleaner.clean(base, synthesize(evidence, ordered), List.of(rule));
        } catch (RuntimeException e) {
            return false;
        }
        // 回退意味着这条规则在真实播放里同样会被拒（全删 / 比例超限 / 时长超限）
        if (result == null || result.fallback() || !result.changed()) return false;

        // 按 URI 出现次数比对，而不是 contains 子串查找 —— 后者会被别名遮蔽：
        // 删掉 /s/1 后保留的 /s/12 让判断恒为真。代理式内嵌 URL 的 path、
        // 无扩展名序号切片、同一 URI 在 playlist 中复用都会触发这种遮蔽。
        java.util.Map<String, Integer> keptCounts = segmentUriCounts(result.manifest());
        java.util.Map<String, Integer> expected = new java.util.HashMap<>();
        for (SegmentFact fact : evidence.outside()) {
            expected.merge(uriOf(fact, evidence.playlistHost()), 1, Integer::sum);
        }
        // 区间外每片都必须保留，且次数完全一致：少一次就是误删。
        if (!expected.equals(keptCounts)) return false;
        // 区间内每片都必须被删：与区间外同 URI 的切片会被一并删除，
        // 此时上面的次数比对已经失败，这里再兜一次总数。
        return result.removedSegments() == evidence.inside().size();
    }

    /** 区间内外是否有切片在合成 manifest 里塌成同一个 URI。 */
    private static boolean hasUriOverlap(AdIntervalEvidence evidence) {
        java.util.Set<String> inside = new java.util.HashSet<>();
        for (SegmentFact fact : evidence.inside()) {
            inside.add(uriOf(fact, evidence.playlistHost()));
        }
        for (SegmentFact fact : evidence.outside()) {
            if (inside.contains(uriOf(fact, evidence.playlistHost()))) return true;
        }
        return false;
    }

    /** 统计净化后 manifest 里每个切片 URI 的出现次数。 */
    private static java.util.Map<String, Integer> segmentUriCounts(String cleaned) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        if (cleaned == null) return counts;
        for (String line : cleaned.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            counts.merge(trimmed, 1, Integer::sum);
        }
        return counts;
    }

    /** 切片按原下标排序，作为合成与回读的共同顺序基准。 */
    private static List<SegmentFact> ordered(AdIntervalEvidence evidence) {
        List<SegmentFact> all = new java.util.ArrayList<>(evidence.inside());
        all.addAll(evidence.outside());
        all.sort(java.util.Comparator.comparingInt(SegmentFact::index));
        return all;
    }

    /**
     * 用证据合成一份与真实 playlist 等价的 media playlist。
     *
     * <p>切片按原下标排序，保留 host、path、时长与断点标记 —— 这些正是规则可能
     * 用到的全部条件维度。
     *
     * <p>刻意不注入自定义标签做标记：{@code HlsManifestCleaner} 对未知 tag
     * 会整份回退（实测 {@code fallback=true}），注入哨兵反而让自检永远拒绝。
     */
    private static String synthesize(AdIntervalEvidence evidence, List<SegmentFact> ordered) {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:10\n");
        for (SegmentFact fact : ordered) {
            if (fact.discontinuityBefore()) text.append("#EXT-X-DISCONTINUITY\n");
            text.append(String.format(Locale.US, "#EXTINF:%.3f,%n", fact.durationSec()));
            text.append(uriOf(fact, evidence.playlistHost())).append('\n');
        }
        return text.append("#EXT-X-ENDLIST\n").toString();
    }

    /** 还原切片的绝对地址。host 为空时按同域相对路径处理。 */
    private static String uriOf(SegmentFact fact, String playlistHost) {
        String host = fact.host().isEmpty() ? playlistHost : fact.host();
        String path = fact.path().startsWith("/") ? fact.path() : "/" + fact.path();
        return SYNTHETIC_BASE_SCHEME + host + path;
    }

    /** 载荷编译失败说明条件本身非法，直接判为不安全。 */
    private static HlsManifestCleaner.Rule compile(RulePayload payload) {
        try {
            return HlsAdRule.createUserRule(
                    "self-check", "self-check",
                    payload.playlistHostSuffixes(), payload.hosts(), payload.regex(),
                    payload.hasDurationRange() ? payload.durationMin() : null,
                    payload.hasDurationRange() ? payload.durationMax() : null,
                    payload.requireDiscontinuity(), payload.requireCrossDomain(),
                    payload.minimumSignals()).compile();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
