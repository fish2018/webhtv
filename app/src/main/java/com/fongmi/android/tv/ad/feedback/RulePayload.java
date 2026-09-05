package com.fongmi.android.tv.ad.feedback;

import java.util.List;

/**
 * 归因结论的机器可读载荷：落地规则所需的实际数据。
 *
 * <p>与 {@link AdAttribution#evidence()} 分开是刻意的 —— 证据是给人看的、
 * 会随文案和语言变化，不能用来提取域名或规则键。
 *
 * @param playlistHostSuffixes 规则作用域，限定到当前站点的 playlist 域名；
 *                             HLS 规则必须携带，否则会污染其他站点（设计文档第 10 节）
 * @param hosts                要拉黑或匹配的切片域名
 * @param regex                广告 URL 正则
 * @param exclude              正片保护正则
 * @param ruleKey              要启用的已有规则的复合状态键
 * @param durationMin          切片时长下界，NaN 表示不限定
 * @param durationMax          切片时长上界，NaN 表示不限定
 * @param requireDiscontinuity 是否要求断点边界
 * @param requireCrossDomain   是否要求跨域
 * @param minimumSignals       生成 HLS 规则时的多信号门限
 */
public record RulePayload(List<String> playlistHostSuffixes, List<String> hosts,
                          List<String> regex, List<String> exclude,
                          String ruleKey, double durationMin, double durationMax,
                          boolean requireDiscontinuity, boolean requireCrossDomain,
                          int minimumSignals) {

    public RulePayload {
        playlistHostSuffixes = playlistHostSuffixes == null ? List.of() : List.copyOf(playlistHostSuffixes);
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
        regex = regex == null ? List.of() : List.copyOf(regex);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        ruleKey = ruleKey == null ? "" : ruleKey;
        if (minimumSignals < 0) minimumSignals = 0;
    }

    public static RulePayload empty() {
        return new RulePayload(List.of(), List.of(), List.of(), List.of(), "",
                Double.NaN, Double.NaN, false, false, 0);
    }

    /** 仅域名黑名单。 */
    public static RulePayload ofHosts(List<String> hosts) {
        return new RulePayload(List.of(), hosts, List.of(), List.of(), "",
                Double.NaN, Double.NaN, false, false, 0);
    }

    /** 仅 URL 正则。 */
    public static RulePayload ofRegex(List<String> regex) {
        return new RulePayload(List.of(), List.of(), regex, List.of(), "",
                Double.NaN, Double.NaN, false, false, 0);
    }

    /** 启用一条已有规则。 */
    public static RulePayload ofRuleKey(String ruleKey) {
        return new RulePayload(List.of(), List.of(), List.of(), List.of(), ruleKey,
                Double.NaN, Double.NaN, false, false, 0);
    }

    /** HLS 结构化条件，不带时长范围。 */
    public static RulePayload ofHls(List<String> playlistHostSuffixes, List<String> hosts,
                                    int minimumSignals) {
        return new RulePayload(playlistHostSuffixes, hosts, List.of(), List.of(), "",
                Double.NaN, Double.NaN, false, false, minimumSignals);
    }

    /** HLS 结构化条件全量。 */
    public static RulePayload ofHlsRule(List<String> playlistHostSuffixes, List<String> hosts,
                                        List<String> regex,
                                        double durationMin, double durationMax,
                                        boolean requireDiscontinuity, boolean requireCrossDomain,
                                        int minimumSignals) {
        return new RulePayload(playlistHostSuffixes, hosts, regex, List.of(), "",
                durationMin, durationMax, requireDiscontinuity, requireCrossDomain, minimumSignals);
    }

    /** HLS 结构化条件，无 URL 正则。 */
    public static RulePayload ofHlsRule(List<String> playlistHostSuffixes, List<String> hosts,
                                        double durationMin, double durationMax,
                                        boolean requireDiscontinuity, boolean requireCrossDomain,
                                        int minimumSignals) {
        return ofHlsRule(playlistHostSuffixes, hosts, List.of(), durationMin, durationMax,
                requireDiscontinuity, requireCrossDomain, minimumSignals);
    }

    public boolean hasDurationRange() {
        return !Double.isNaN(durationMin) && !Double.isNaN(durationMax);
    }

    public boolean isEmpty() {
        return hosts.isEmpty() && regex.isEmpty() && exclude.isEmpty() && ruleKey.isEmpty();
    }
}
