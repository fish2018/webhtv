package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.utils.HlsManifestCleaner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于域名信誉的归因通道，实现「非当前适配的主流域名」这条判据。
 *
 * <p>三层比对见设计文档第 7.2 节。所有外部数据由调用方以 {@link Input} 传入，
 * 类本身不触碰 SharedPreferences，因此可在纯 JVM 下单测。
 */
public final class DomainReputationClassifier {

    public static final String CHANNEL_ID = "domain";

    /** 命中已知黑名单时的置信度。 */
    static final float CONFIDENCE_KNOWN_HOST = 0.95f;
    /** 仅「不在本站基线内」时的置信度。 */
    static final float CONFIDENCE_UNKNOWN_HOST = 0.55f;
    /** 接口候选也认为是广告域名时的加成。 */
    static final float BONUS_INTERFACE_CANDIDATE = 0.15f;

    private DomainReputationClassifier() {
    }

    /**
     * 分类输入。
     *
     * @param blacklistedHosts    现有黑名单，来自 {@code RuleConfig.get().getAds()}
     * @param siteBaselineHosts   本站历史正常域名，来自 SitePlaylistHostBaseline
     * @param interfaceCandidateHosts 接口学习的待审候选域名
     * @param interfaceSourceName 命中候选时展示的来源接口名
     */
    public record Input(List<String> blacklistedHosts, List<String> siteBaselineHosts,
                        List<String> interfaceCandidateHosts, String interfaceSourceName) {

        public Input {
            blacklistedHosts = blacklistedHosts == null ? List.of() : List.copyOf(blacklistedHosts);
            siteBaselineHosts = siteBaselineHosts == null ? List.of() : List.copyOf(siteBaselineHosts);
            interfaceCandidateHosts = interfaceCandidateHosts == null ? List.of() : List.copyOf(interfaceCandidateHosts);
            interfaceSourceName = interfaceSourceName == null ? "" : interfaceSourceName;
        }

        public static Input empty() {
            return new Input(List.of(), List.of(), List.of(), "");
        }
    }

    /** 无结论时返回 null 表示弃权。不含叠加校验，仅供单测。 */
    public static AdAttribution classify(AdIntervalEvidence evidence, Input input) {
        return classify(evidence, input, List.of());
    }

    /**
     * 带叠加校验的版本：新规则要与 {@code activeRules} 合并后仍然安全。
     *
     * @param activeRules 当前已启用并生效的 HLS 规则
     */
    public static AdAttribution classify(AdIntervalEvidence evidence, Input input,
                                         List<HlsManifestCleaner.Rule> activeRules) {
        if (evidence == null || evidence.inside().isEmpty()) return null;
        Input safe = input == null ? Input.empty() : input;

        Set<String> foreignHosts = foreignHosts(evidence);
        if (foreignHosts.isEmpty()) return null;

        List<String> matchedBlacklist = matching(foreignHosts, safe.blacklistedHosts());
        List<String> matchedCandidates = matching(foreignHosts, safe.interfaceCandidateHosts());

        if (!matchedBlacklist.isEmpty()) {
            // 已在黑名单里却仍被用户看到：拦截路径没覆盖播放器直连的切片请求。
            // 结论是诊断（NONE + 空载荷，actionable() 为 false），不会生成规则，
            // 因此不受下面那道「需要同域对照」的约束 —— 整站切片都在独立 CDN
            // 恰恰是这条诊断最该出现的场景。
            List<String> evidenceLines = new ArrayList<>();
            evidenceLines.add("切片域名已在广告黑名单中：" + String.join("、", matchedBlacklist));
            evidenceLines.add("黑名单主要在 WebView 请求拦截生效，不拦播放器直连的切片请求");
            evidenceLines.add("建议改用 HLS 结构化规则删除这些切片");
            return new AdAttribution(CHANNEL_ID, AdCategory.ALREADY_HANDLED,
                    CONFIDENCE_KNOWN_HOST, RiskLevel.LOW, evidenceLines, RemediationKind.NONE);
        }

        // 以下分支会生成规则，必须有对照组：区间外存在属于 playlist 域名的切片，
        // 才能说明这些外域切片是异常的。否则整站切片都在独立 CDN
        // （api.site.com + cdn.site-x.com 这种常见拆分）会被整体判为广告，
        // 生成的规则命中全部切片 → HlsManifestCleaner 因
        // removedCount == segmentCount 回退原文，而 HlsAdblockPipeline 的
        // fallback 会连带停掉 legacy 启发式，比不加规则更差。
        // 与 HlsSegmentClassifier.detectCrossDomain 同一判据。
        if (!hasSameDomainOutside(evidence)) return null;

        // 无基线数据时不能断言「非本站域名」，避免首次播放即误判
        if (safe.siteBaselineHosts().isEmpty() && matchedCandidates.isEmpty()) return null;

        boolean outsideBaseline = !safe.siteBaselineHosts().isEmpty()
                && foreignHosts.stream().noneMatch(host -> endsWithAny(host, safe.siteBaselineHosts()));
        if (!outsideBaseline && matchedCandidates.isEmpty()) return null;

        float confidence = outsideBaseline ? CONFIDENCE_UNKNOWN_HOST : 0f;
        List<String> evidenceLines = new ArrayList<>();
        if (outsideBaseline) {
            evidenceLines.add("切片域名 " + String.join("、", foreignHosts) + " 不在本站常用域名中");
        }
        if (!matchedCandidates.isEmpty()) {
            confidence += BONUS_INTERFACE_CANDIDATE;
            String source = safe.interfaceSourceName().isEmpty() ? "接口规则" : safe.interfaceSourceName();
            evidenceLines.add(source + "也将其列为广告域名候选");
        }
        evidenceLines.add("起点来源：" + evidence.startOrigin());

        // 落地方式是 HLS 结构化规则而不是域名黑名单：黑名单只在 WebView
        // 请求拦截生效（RuleConfig.getAds() 的唯一消费者是 CustomWebView），
        // 拦不住播放器直连的切片请求。跨域按 foreignHosts 的定义天然成立，
        // 与 hostSuffixes 合起来正好满足 minimumSignals=2。
        // playlistHost 必然非空 —— hasSameDomainOutside 在它为空时已经弃权，
        // 所以这里不再有「无作用域」的退路。
        List<String> scope = List.of(evidence.playlistHost());
        RulePayload payload = RulePayload.ofHlsRule(scope, List.copyOf(foreignHosts),
                Double.NaN, Double.NaN, false, true, 2);
        // 最终守门：用真实 cleaner 验证只删区间内。域名条件同样挡不住
        // 「广告与正片共用第三方 CDN、靠路径区分」这类场景。
        if (!RuleSelfCheck.isSafe(evidence, payload, activeRules)) return null;
        return new AdAttribution(CHANNEL_ID, AdCategory.THIRD_PARTY_CDN_SEGMENT,
                confidence, RiskLevel.LOW, evidenceLines,
                RemediationKind.HLS_STRUCTURED_RULE, payload);
    }

    /**
     * 区间外是否存在属于 playlist 域名的切片。这是「外域切片异常」的对照前提：
     * 没有同域切片作对照，说明该站本来就把切片放在独立 CDN 上。
     */
    private static boolean hasSameDomainOutside(AdIntervalEvidence evidence) {
        return SegmentContrast.hasSameDomainOutside(evidence);
    }

    /** 区间内不属于 playlist 域名的切片 host，去重保序。 */
    private static Set<String> foreignHosts(AdIntervalEvidence evidence) {
        Set<String> hosts = new LinkedHashSet<>();
        String playlistHost = evidence.playlistHost();
        for (SegmentFact fact : evidence.inside()) {
            if (fact.host().isEmpty()) continue;
            if (!playlistHost.isEmpty() && fact.hostEndsWith(playlistHost)) continue;
            hosts.add(fact.host().toLowerCase(Locale.US));
        }
        return hosts;
    }

    private static List<String> matching(Set<String> hosts, List<String> patterns) {
        List<String> matched = new ArrayList<>();
        for (String host : hosts) {
            if (endsWithAny(host, patterns)) matched.add(host);
        }
        return matched;
    }

    /**
     * host 是否命中任一片段。黑名单条目多为域名片段（如 {@code doubleclick.net}），
     * 沿用现有拦截语义：后缀匹配或包含匹配。
     */
    private static boolean endsWithAny(String host, List<String> patterns) {
        String lower = host.toLowerCase(Locale.US);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            String target = pattern.toLowerCase(Locale.US).trim();
            if (lower.equals(target) || lower.endsWith("." + target) || lower.contains(target)) return true;
        }
        return false;
    }
}
