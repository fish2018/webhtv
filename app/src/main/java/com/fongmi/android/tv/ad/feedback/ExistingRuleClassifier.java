package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.utils.HlsManifestCleaner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 诊断通道：回答「为什么现有机制没拦住」。
 *
 * <p>本通道不产出新规则，但会给出成本最低的修复建议 —— 启用一条已存在
 * 但默认关闭的规则，永远优于新建规则。见设计文档第 7.3、8.1 节。
 */
public final class ExistingRuleClassifier {

    public static final String CHANNEL_ID = "existing-rule";

    /** 命中一条已存在但未启用的规则时的置信度。 */
    static final float CONFIDENCE_DISABLED_RULE = 0.75f;

    private ExistingRuleClassifier() {
    }

    /**
     * 一条内置/外部 HLS 规则的当前状态，对应 {@code HlsRuleConfig.Entry} 的子集。
     *
     * @param key         复合状态键
     * @param id          规则 id
     * @param name        规则名
     * @param enabled     当前是否启用
     * @param valid       编译是否通过
     * @param hostSuffixes 该规则针对的切片域名后缀
     * @param compiled    已编译的规则，供自检实跑；取不到时为 null（则不建议启用）
     */
    public record RuleState(String key, String id, String name, boolean enabled,
                            boolean valid, List<String> hostSuffixes,
                            HlsManifestCleaner.Rule compiled) {

        public RuleState {
            key = key == null ? "" : key;
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            hostSuffixes = hostSuffixes == null ? List.of() : List.copyOf(hostSuffixes);
        }

        /**
         * 无编译产物的简化构造，仅用于诊断路径与测试。
         *
         * <p>刻意保持包级可见：生产代码必须走 7 参构造并提供编译产物，
         * 否则 {@code findDisabledMatch} 会因无法自检而跳过该规则 ——
         * 用它会静默关掉「启用已有规则」整条通道。
         */
        RuleState(String key, String id, String name, boolean enabled,
                  boolean valid, List<String> hostSuffixes) {
            this(key, id, name, enabled, valid, hostSuffixes, null);
        }
    }

    /**
     * 分类输入。
     *
     * @param hlsRules            已知的 HLS 规则状态
     * @param legacyHeuristicActive 旧启发式引擎当次是否生效
     * @param protectingExcludes  可能误保护广告切片的正片保护正则
     */
    public record Input(List<RuleState> hlsRules, boolean legacyHeuristicActive,
                        List<String> protectingExcludes) {

        public Input {
            hlsRules = hlsRules == null ? List.of() : List.copyOf(hlsRules);
            protectingExcludes = protectingExcludes == null ? List.of() : List.copyOf(protectingExcludes);
        }

        public static Input empty() {
            return new Input(List.of(), false, List.of());
        }
    }

    /** 无诊断结论时返回 null。不含叠加校验，仅供单测。 */
    public static AdAttribution classify(AdIntervalEvidence evidence, Input input) {
        return classify(evidence, input, List.of());
    }

    /**
     * 带叠加校验的版本：待启用规则要与 {@code activeRules} 合并后仍然安全。
     *
     * @param activeRules 当前已启用并生效的 HLS 规则
     */
    public static AdAttribution classify(AdIntervalEvidence evidence, Input input,
                                         List<HlsManifestCleaner.Rule> activeRules) {
        if (evidence == null || evidence.inside().isEmpty()) return null;
        Input safe = input == null ? Input.empty() : input;

        RuleState disabled = findDisabledMatch(evidence, safe.hlsRules(), activeRules);
        if (disabled != null) {
            List<String> lines = new ArrayList<>();
            lines.add(String.format(Locale.US, "已有规则「%s」覆盖该域名但当前未启用",
                    disabled.name().isEmpty() ? disabled.id() : disabled.name()));
            lines.add("启用它即可拦掉这段广告，无需新建规则");
            return new AdAttribution(CHANNEL_ID, AdCategory.DISCONTINUITY_BLOCK,
                    CONFIDENCE_DISABLED_RULE, RiskLevel.LOW, lines,
                    RemediationKind.ENABLE_EXISTING_RULE,
                    RulePayload.ofRuleKey(disabled.key()));
        }

        List<String> lines = new ArrayList<>();
        if (evidence.handledByStructuredRule()) {
            lines.add("该区间已被规则 " + String.join("、", evidence.alreadyRemovedByStructuredRuleIds())
                    + " 处理，用户看到的广告来自其他来源");
        }
        if (safe.legacyHeuristicActive()) {
            lines.add("旧启发式引擎当次生效，其判定不依赖规则，可能与结构化结论冲突");
        }
        if (!safe.protectingExcludes().isEmpty()) {
            lines.add("存在 " + safe.protectingExcludes().size()
                    + " 条正片保护规则，可能阻止了这些切片被删除");
        }
        RuleState invalid = findInvalid(safe.hlsRules());
        if (invalid != null) {
            lines.add("规则「" + (invalid.name().isEmpty() ? invalid.id() : invalid.name())
                    + "」编译失败，未参与本次净化");
        }
        if (lines.isEmpty()) return null;

        return new AdAttribution(CHANNEL_ID, AdCategory.ALREADY_HANDLED,
                0.5f, RiskLevel.LOW, lines, RemediationKind.NONE);
    }

    /**
     * 找出一条覆盖本区间域名、有效但未启用的规则。
     *
     * <p>必须过 {@link RuleSelfCheck}：仅凭「hostSuffixes 与区间内某片同域」建议启用，
     * 不看该规则的其余条件 —— 实测一条 {@code minimumSignals=1} 的既有规则在
     * 「广告与正片共用同一 CDN」时会命中全部切片，回退并连带停掉 legacy 启发式。
     * 这条路径此前完全没有守卫。
     */
    private static RuleState findDisabledMatch(AdIntervalEvidence evidence, List<RuleState> rules,
                                               List<HlsManifestCleaner.Rule> activeRules) {
        for (RuleState rule : rules) {
            if (rule.enabled() || !rule.valid() || rule.hostSuffixes().isEmpty()) continue;
            boolean covers = evidence.inside().stream()
                    .anyMatch(fact -> rule.hostSuffixes().stream().anyMatch(fact::hostEndsWith));
            if (!covers) continue;
            // 拿不到编译产物时无法验证，宁可不建议
            if (rule.compiled() == null) continue;
            if (!RuleSelfCheck.isSafe(evidence, rule.compiled(), activeRules)) continue;
            return rule;
        }
        return null;
    }

    private static RuleState findInvalid(List<RuleState> rules) {
        return rules.stream().filter(rule -> !rule.valid()).findFirst().orElse(null);
    }
}
