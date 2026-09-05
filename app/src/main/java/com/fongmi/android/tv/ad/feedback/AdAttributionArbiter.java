package com.fongmi.android.tv.ad.feedback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨通道仲裁：合并同类结论、按成本-风险排序、选出最优方案。
 *
 * <p>核心取舍见设计文档第 8 节：「最好的去广告方式」不等于「置信度最高的方式」。
 * 同等置信度下优先选成本更低、风险更小的机制。
 */
public final class AdAttributionArbiter {

    private AdAttributionArbiter() {
    }

    /**
     * 仲裁结果。
     *
     * @param preferred    首选方案，全部弃权时为 null
     * @param alternatives 备选方案，按得分降序
     * @param diagnostics  纯诊断结论，不产出规则但需展示
     */
    public record Verdict(AdAttribution preferred, List<AdAttribution> alternatives,
                          List<AdAttribution> diagnostics) {

        public Verdict {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        /** 是否有可落地的规则方案。 */
        public boolean hasActionablePlan() {
            return preferred != null && preferred.actionable();
        }

        /** 全部通道都没给出结论。 */
        public boolean empty() {
            return preferred == null && diagnostics.isEmpty();
        }
    }

    /**
     * 仲裁若干通道结论。入参可含 null（通道弃权），会被忽略。
     */
    public static Verdict arbitrate(List<AdAttribution> attributions) {
        List<AdAttribution> valid = new ArrayList<>();
        if (attributions != null) {
            for (AdAttribution attribution : attributions) {
                if (attribution != null) valid.add(attribution);
            }
        }
        if (valid.isEmpty()) return new Verdict(null, List.of(), List.of());

        List<AdAttribution> diagnostics = new ArrayList<>();
        List<AdAttribution> actionable = new ArrayList<>();
        for (AdAttribution attribution : valid) {
            if (attribution.actionable()) actionable.add(attribution);
            else diagnostics.add(attribution);
        }

        List<AdAttribution> merged = mergeSameCategory(actionable);
        merged.sort(Comparator.comparingDouble(AdAttribution::score).reversed()
                .thenComparingInt(a -> a.remediation().ordinal()));

        if (merged.isEmpty()) {
            // 只有诊断结论：能做的只有本次跳过
            return new Verdict(sessionSkipFallback(diagnostics), List.of(), diagnostics);
        }
        return new Verdict(merged.get(0), merged.subList(1, merged.size()), diagnostics);
    }

    /**
     * 同一 {@link AdCategory} 的多通道结论合并为一条：置信度按概率或
     * {@code 1 - Π(1 - cᵢ)}，机制取成本最低的那个，证据全量保留。
     */
    private static List<AdAttribution> mergeSameCategory(List<AdAttribution> attributions) {
        Map<AdCategory, List<AdAttribution>> grouped = new EnumMap<>(AdCategory.class);
        for (AdAttribution attribution : attributions) {
            grouped.computeIfAbsent(attribution.category(), key -> new ArrayList<>()).add(attribution);
        }

        List<AdAttribution> merged = new ArrayList<>();
        for (Map.Entry<AdCategory, List<AdAttribution>> entry : grouped.entrySet()) {
            List<AdAttribution> group = entry.getValue();
            if (group.size() == 1) {
                merged.add(group.get(0));
                continue;
            }
            float inverse = 1f;
            for (AdAttribution attribution : group) inverse *= (1f - attribution.confidence());
            float confidence = 1f - inverse;

            AdAttribution cheapest = group.stream()
                    .min(Comparator.comparingInt(a -> a.remediation().ordinal()))
                    .orElse(group.get(0));
            RiskLevel risk = group.stream()
                    .map(AdAttribution::risk)
                    .min(Comparator.comparingInt(Enum::ordinal))
                    .orElse(cheapest.risk());

            Set<String> lines = new LinkedHashSet<>();
            List<String> channels = new ArrayList<>();
            for (AdAttribution attribution : group) {
                lines.addAll(attribution.evidence());
                channels.add(attribution.channelId());
            }
            merged.add(new AdAttribution(String.join("+", channels), entry.getKey(),
                    confidence, risk, List.copyOf(lines), cheapest.remediation(),
                    // 载荷必须跟随被选中的机制，否则会拿 A 的数据去执行 B 的动作
                    cheapest.payload()));
        }
        return merged;
    }

    /** 只有诊断结论时的兜底：本次跳过。 */
    private static AdAttribution sessionSkipFallback(List<AdAttribution> diagnostics) {
        List<String> lines = new ArrayList<>();
        lines.add("未能归纳出可复用的去广规则");
        for (AdAttribution diagnostic : diagnostics) lines.addAll(diagnostic.evidence());
        return new AdAttribution("arbiter", AdCategory.IN_STREAM_BURNED_IN,
                0.3f, RiskLevel.LOW, lines, RemediationKind.SESSION_SKIP_ONLY);
    }
}
