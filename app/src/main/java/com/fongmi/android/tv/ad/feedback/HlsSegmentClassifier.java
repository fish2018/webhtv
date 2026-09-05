package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.utils.HlsManifestCleaner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于 HLS 切片结构的归因通道。零新增运行时成本：全部输入来自
 * {@code M3u8Parser.parse()} 已产出的证据，不发起新的网络请求。
 *
 * <p>五种信号的权重见设计文档第 7.1 节。生成的条件必须落在
 * {@code HlsManifestCleaner.Rule} 已支持的范围内，否则规则无法被现有引擎执行。
 */
public final class HlsSegmentClassifier {

    public static final String CHANNEL_ID = "hls";

    /** 置信度低于此值时弃权。 */
    static final float MIN_CONFIDENCE = 0.30f;

    static final float WEIGHT_CROSS_DOMAIN = 0.35f;
    static final float WEIGHT_DISCONTINUITY = 0.25f;
    static final float WEIGHT_DURATION_OUTLIER = 0.20f;
    static final float WEIGHT_PATH_HINT = 0.15f;
    static final float WEIGHT_HEAD_POSITION = 0.05f;

    /** 区间内切片时长视为「整齐」的标准差上限，秒。 */
    private static final double UNIFORM_STDDEV_MAX = 0.05d;
    /** 与区间外众数时长的差异下限，秒。 */
    private static final double DURATION_GAP_MIN = 0.5d;
    /** 落在 playlist 头部多少比例内算「片头」。 */
    private static final double HEAD_RATIO = 0.15d;

    /** 保守的广告路径特征，只匹配明确的目录段。 */
    private static final List<String> PATH_HINTS =
            List.of("/ad/", "/ads/", "/adv/", "/preroll/", "/midroll/", "/creative/");

    private HlsSegmentClassifier() {
    }

    /** 命中的信号，用于生成规则时决定 minimumSignals。 */
    public record Signals(boolean crossDomain, boolean discontinuity, boolean durationOutlier,
                          boolean pathHint, boolean headPosition) {

        /**
         * 观察到的信号数，仅供诊断展示。
         *
         * <p>刻意不用于判断能否落地 —— 信号命中不等于条件有区分度，
         * 那件事由 {@code payloadOf} 的实际载荷决定。此前用它做守卫时，
         * 「同域 + 路径特征区间外也有」的场景会产出一条空载荷的归因。
         */
        public int signalCount() {
            int count = 0;
            if (crossDomain) count++;
            if (discontinuity) count++;
            if (durationOutlier) count++;
            if (pathHint) count++;
            if (headPosition) count++;
            return count;
        }

        public float weightSum() {
            float sum = 0f;
            if (crossDomain) sum += WEIGHT_CROSS_DOMAIN;
            if (discontinuity) sum += WEIGHT_DISCONTINUITY;
            if (durationOutlier) sum += WEIGHT_DURATION_OUTLIER;
            if (pathHint) sum += WEIGHT_PATH_HINT;
            if (headPosition) sum += WEIGHT_HEAD_POSITION;
            return sum;
        }
    }

    /**
     * 对区间做结构归因，不含叠加校验。无法判断时返回 null 表示弃权。
     *
     * <p>仅供单测；生产路径走带 {@code activeRules} 的版本。
     */
    public static AdAttribution classify(AdIntervalEvidence evidence) {
        return classify(evidence, List.of());
    }

    /**
     * 带叠加校验的版本：新规则要与 {@code activeRules} 合并后仍然安全。
     *
     * @param activeRules 当前已启用并生效的 HLS 规则
     */
    public static AdAttribution classify(AdIntervalEvidence evidence,
                                         List<HlsManifestCleaner.Rule> activeRules) {
        if (evidence == null || !evidence.hasSegmentEvidence()) return null;
        // 区间已被结构化规则整体处理，用户看到的广告来自别处，不应再加 HLS 规则
        if (evidence.handledByStructuredRule()) return null;

        Signals signals = detect(evidence);
        float confidence = signals.weightSum();
        if (confidence < MIN_CONFIDENCE) return null;

        // 以实际载荷为准判断能否落地：信号命中不等于条件有区分度
        // （如站内正片路径也含 /ads/ 时，路径特征无法编码进规则）
        RulePayload payload = payloadOf(evidence, signals);
        if (payload.isEmpty()) return null;
        // 最终守门：用真实 cleaner 跑一遍，确认只删区间内、不碰区间外、不触发回退。
        // 逐条件的对照校验挡不住条件间的组合效应（如 duration + crossDomain 在
        // 区间外同时成立），也预判不到删除比例与时长闸门。
        if (!RuleSelfCheck.isSafe(evidence, payload, activeRules)) return null;

        return new AdAttribution(CHANNEL_ID, categoryOf(signals), confidence,
                RiskLevel.MEDIUM, describe(evidence, signals, payload),
                RemediationKind.HLS_STRUCTURED_RULE, payload);
    }

    /**
     * 生成可被 {@code HlsManifestCleaner.Rule} 执行的条件。
     *
     * <p>关键约束：{@code HlsManifestCleaner} 对每个切片独立累加信号，
     * 达到 minimumSignals 即删除。因此只能编码**有区分度**的条件 ——
     * 与 playlist 同域的 hostSuffixes 对每个切片都成立，requireDiscontinuity
     * 对每个断点后的切片都成立，两者凑够 2 个信号会连正片一起删。
     *
     * <p>有区分度的条件只有两类：非本站域名、广告路径特征。至少要有一类，
     * 否则返回空载荷让通道弃权。
     */
    private static RulePayload payloadOf(AdIntervalEvidence evidence, Signals signals) {
        String playlistHost = evidence.playlistHost();
        // 域名只在跨域信号成立时才有区分度。crossDomain 自带对照校验
        // （detectCrossDomain 要求区间外存在同域切片），少了这道门槛就会在
        // 「整站切片都在独立 CDN」的架构上把全站域名写进规则 —— 规则命中所有
        // 切片 → cleaner 因 removedCount == segmentCount 回退 → 连带停掉
        // legacy 启发式，比不加规则更差。
        List<String> hosts = signals.crossDomain()
                ? evidence.inside().stream()
                        .map(SegmentFact::host)
                        .filter(host -> !host.isEmpty())
                        .filter(host -> playlistHost.isEmpty() || !hostMatches(host, playlistHost))
                        .distinct()
                        .toList()
                : List.of();
        List<String> pathPatterns = signals.pathHint()
                ? pathPatternsOf(evidence) : List.of();
        // 没有任何区分条件时不得生成规则
        if (hosts.isEmpty() && pathPatterns.isEmpty()) return RulePayload.empty();

        double min = Double.NaN;
        double max = Double.NaN;
        if (signals.durationOutlier()) {
            double mean = mean(evidence.inside());
            min = Math.max(0d, mean - 0.1d);
            max = mean + 0.1d;
        }
        // 作用域收窄到当前站点的 playlist 域名，否则规则会污染其他站点
        List<String> scope = playlistHost.isEmpty() ? List.of() : List.of(playlistHost);

        // requireDiscontinuity 只对广告块首片成立，块内后续切片拿不到这个信号。
        // 把它计入门限会导致只删掉首片、留下其余广告，因此不编码进规则 ——
        // 断点已经在归因阶段用于定位区间，规则层不再需要它。
        // requireCrossDomain 对块内每一片都成立，可以安全编码。
        boolean requireCrossDomain = signals.crossDomain();

        // 逐项统计真正对块内每一片都成立的信号，minimumSignals 不能超过它，
        // 否则 HlsAdRule.compile() 会拒绝整条规则。
        int encoded = 0;
        if (!hosts.isEmpty()) encoded++;
        if (!pathPatterns.isEmpty()) encoded++;
        if (!Double.isNaN(min)) encoded++;
        if (requireCrossDomain) encoded++;

        // 门限优先取 2 以避免宽泛删片，但不得超过实际可编码的信号数
        // （超过会让 compile() 拒绝整条规则）。也不取满全部信号 —— 要求
        // 三个条件同时成立时，广告块里稍有出入的一片就会漏删。
        int minimumSignals = Math.min(2, encoded);

        return RulePayload.ofHlsRule(scope, hosts, pathPatterns, min, max,
                false, requireCrossDomain, minimumSignals);
    }

    /**
     * 命中的广告路径目录段转成正则，用 quote 避免元字符注入。
     *
     * <p>双向校验：区间内每片都含该目录段，且区间外**没有**任何切片含它。
     * 只查 inside 的话，站内正片路径恰好含 hint 时会被一起删掉 —— 规则是按
     * 切片独立匹配的，没有「只在这段区间内生效」的概念。
     *
     * <p>区间外为空时无法完成对照（用户框选覆盖了整个 playlist），一律弃权：
     * 此时任何条件都命中全部切片，生成的规则会触发 cleaner 整体回退。
     */
    private static List<String> pathPatternsOf(AdIntervalEvidence evidence) {
        List<String> patterns = new ArrayList<>();
        for (String hint : PATH_HINTS) {
            boolean insideAll = SegmentContrast.pathPresentInside(evidence, hint);
            if (!insideAll) continue;
            boolean outsideNone = SegmentContrast.pathAbsentOutside(evidence, hint);
            if (outsideNone) patterns.add(pathOnlyPattern(hint));
        }
        return patterns;
    }

    /**
     * 把目录段包成「只匹配 path 部分」的正则。
     *
     * <p>必须锚定：本类的双向校验读 {@code SegmentFact.path()}（已去 query，
     * 见去敏要求），而 {@code HlsManifestCleaner.matchesPattern} 匹配的是
     * 含 query 的完整 URL。正片带 {@code ?ref=/ads/} 这类参数时，校验认为
     * 区间外干净而放行，运行时却命中全部切片 → cleaner 回退 → 连带停掉
     * legacy 启发式。{@code [^?]*} 无法跨过 {@code ?}，从而把匹配限制在 path 内。
     */
    static String pathOnlyPattern(String hint) {
        // 同时排除 query 与 fragment：证据侧用 URI.getPath()，query 和 fragment
        // 都被丢弃，所以 pathAbsentOutside 与 RuleSelfCheck 都看不见它们。
        // 只挡 ? 的话，正片 URL 形如 /seg/99.ts#/ads/x 会被跨 # 命中 —— 实测
        // 这种形状下真实 cleaner 会多删一片正片且 fallback=false，错误不被兜住。
        return "^[^?#]*" + Pattern.quote(hint);
    }

    /** host 是否等于给定域名或为其子域。 */
    private static boolean hostMatches(String host, String domain) {
        String lower = host.toLowerCase(Locale.US);
        String target = domain.toLowerCase(Locale.US);
        return lower.equals(target) || lower.endsWith("." + target);
    }

    /** 逐项检测五种信号。 */
    public static Signals detect(AdIntervalEvidence evidence) {
        List<SegmentFact> inside = evidence.inside();
        List<SegmentFact> outside = evidence.outside();
        return new Signals(
                detectCrossDomain(evidence),
                evidence.boundedByDiscontinuity() || inside.get(0).discontinuityBefore(),
                detectDurationOutlier(inside, outside),
                detectPathHint(evidence),
                detectHeadPosition(inside, outside));
    }

    /**
     * 跨域：区间内切片整体不属于 playlist 域名，且区间外存在足够的同域对照。
     *
     * <p>对照由 {@link SegmentContrast#hasSameDomainOutside} 统一判定 ——
     * 只看「存在一个同域切片」不够，playlist 开头一片同域、其余全在独立 CDN
     * 是真实架构，那种情况下生成的规则会命中全站。
     */
    private static boolean detectCrossDomain(AdIntervalEvidence evidence) {
        String playlistHost = evidence.playlistHost();
        if (playlistHost.isEmpty()) return false;
        boolean insideForeign = evidence.inside().stream()
                .allMatch(fact -> !fact.host().isEmpty() && !fact.hostEndsWith(playlistHost));
        if (!insideForeign) return false;
        return SegmentContrast.hasSameDomainOutside(evidence);
    }

    /**
     * 时长离群：区间内时长高度一致，且与区间外众数明显不同。
     * 单独出现时权重不足以触发规则，必须与其他信号叠加。
     */
    private static boolean detectDurationOutlier(List<SegmentFact> inside, List<SegmentFact> outside) {
        if (inside.isEmpty() || outside.isEmpty()) return false;
        if (stdDev(inside) > UNIFORM_STDDEV_MAX) return false;
        double insideMean = mean(inside);
        Double outsideMode = mode(outside);
        if (outsideMode == null) return false;
        return Math.abs(insideMean - outsideMode) > DURATION_GAP_MIN;
    }

    /**
     * 路径特征：存在某个目录段在区间内每片都出现、且区间外一片都没有。
     *
     * <p>信号本身就带对照，与 {@link #pathPatternsOf} 用同一判据 —— 否则会出现
     * 「信号成立但无法编码进规则」的空载荷归因。
     */
    private static boolean detectPathHint(AdIntervalEvidence evidence) {
        return PATH_HINTS.stream().anyMatch(hint ->
                SegmentContrast.pathPresentInside(evidence, hint)
                        && SegmentContrast.pathAbsentOutside(evidence, hint));
    }

    /** 位置：区间落在 playlist 头部 15% 以内。 */
    private static boolean detectHeadPosition(List<SegmentFact> inside, List<SegmentFact> outside) {
        int total = inside.size() + outside.size();
        if (total == 0) return false;
        int firstIndex = inside.stream().mapToInt(SegmentFact::index).min().orElse(Integer.MAX_VALUE);
        return firstIndex <= Math.max(0, (int) Math.floor(total * HEAD_RATIO));
    }

    private static AdCategory categoryOf(Signals signals) {
        if (signals.crossDomain()) return AdCategory.THIRD_PARTY_CDN_SEGMENT;
        if (signals.discontinuity()) return AdCategory.DISCONTINUITY_BLOCK;
        if (signals.durationOutlier()) return AdCategory.FIXED_DURATION_BLOCK;
        return AdCategory.UNKNOWN;
    }

    /**
     * 生成给用户看的证据。
     *
     * <p>分两段：先说观察到的现象（按 signals），再明确列出**实际写进规则**的
     * 条件（按 payload）。两者不是一回事 —— 例如路径特征因区间外也含而未被编码时，
     * 现象成立但规则里没有它。不列清规则条件，用户无法判断这条规则的杀伤范围。
     */
    private static List<String> describe(AdIntervalEvidence evidence, Signals signals,
                                         RulePayload payload) {
        List<SegmentFact> inside = evidence.inside();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.US, "区间内 %d 个切片，时长合计 %.1fs",
                inside.size(), inside.stream().mapToDouble(SegmentFact::durationSec).sum()));
        if (signals.crossDomain()) {
            lines.add(String.format(Locale.US, "切片域名 %s 与 playlist 域名 %s 不一致",
                    inside.get(0).host(), evidence.playlistHost()));
        }
        if (signals.discontinuity()) lines.add("区间边界存在 #EXT-X-DISCONTINUITY");
        if (signals.durationOutlier()) {
            lines.add(String.format(Locale.US, "区间内切片时长一致（%.2fs），与正片切片时长差异明显",
                    mean(inside)));
        }
        if (signals.pathHint()) lines.add("切片路径包含广告目录特征");
        if (signals.headPosition()) lines.add("区间位于播放列表头部");
        lines.add("起点来源：" + evidence.startOrigin());
        lines.addAll(describeRule(payload));
        return lines;
    }

    /** 逐项列出规则实际生效的条件，让用户能判断杀伤范围。 */
    private static List<String> describeRule(RulePayload payload) {
        List<String> lines = new ArrayList<>();
        if (payload.isEmpty()) return lines;
        lines.add("规则生效范围：" + String.join("、", payload.playlistHostSuffixes()));
        if (!payload.hosts().isEmpty()) {
            lines.add("删除来自这些域名的切片：" + String.join("、", payload.hosts()));
        }
        if (!payload.regex().isEmpty()) {
            lines.add("删除路径匹配的切片：" + String.join("、", payload.regex()));
        }
        if (payload.hasDurationRange()) {
            lines.add(String.format(Locale.US, "限定切片时长 %.2f–%.2fs",
                    payload.durationMin(), payload.durationMax()));
        }
        if (payload.requireCrossDomain()) lines.add("仅当切片域名与 playlist 不同时生效");
        lines.add(String.format(Locale.US, "需同时满足其中 %d 个条件", payload.minimumSignals()));
        return lines;
    }

    private static double mean(List<SegmentFact> facts) {
        return facts.stream().mapToDouble(SegmentFact::durationSec).average().orElse(0d);
    }

    private static double stdDev(List<SegmentFact> facts) {
        if (facts.size() < 2) return 0d;
        double mean = mean(facts);
        double variance = facts.stream()
                .mapToDouble(fact -> Math.pow(fact.durationSec() - mean, 2))
                .sum() / facts.size();
        return Math.sqrt(variance);
    }

    /** 区间外切片的众数时长，按 0.1s 粒度归桶。 */
    private static Double mode(List<SegmentFact> facts) {
        Map<Long, Integer> buckets = new HashMap<>();
        for (SegmentFact fact : facts) {
            long bucket = Math.round(fact.durationSec() * 10);
            buckets.merge(bucket, 1, Integer::sum);
        }
        return buckets.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() / 10d)
                .orElse(null);
    }
}
