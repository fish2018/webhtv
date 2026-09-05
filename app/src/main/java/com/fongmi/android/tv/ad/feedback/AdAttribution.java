package com.fongmi.android.tv.ad.feedback;

import java.util.List;

/**
 * 单个通道对区间的归因结论。
 *
 * @param channelId   通道标识，"hls" / "domain" / "existing-rule" / "audio" / "speech" / "ai"
 * @param category    归因类别
 * @param confidence  通道内自评置信度，0..1
 * @param risk        采纳该结论的风险等级
 * @param evidence    人类可读的证据，直接进 UI
 * @param remediation 建议的去广机制
 * @param payload     落地规则所需的机器可读数据
 */
public record AdAttribution(
        String channelId, AdCategory category, float confidence,
        RiskLevel risk, List<String> evidence, RemediationKind remediation,
        RulePayload payload) {

    public AdAttribution {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId is required");
        }
        category = category == null ? AdCategory.UNKNOWN : category;
        confidence = clamp(confidence);
        risk = risk == null ? RiskLevel.MEDIUM : risk;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        remediation = remediation == null ? RemediationKind.NONE : remediation;
        payload = payload == null ? RulePayload.empty() : payload;
    }

    /** 纯诊断结论没有可落地的数据。 */
    public AdAttribution(String channelId, AdCategory category, float confidence,
                         RiskLevel risk, List<String> evidence, RemediationKind remediation) {
        this(channelId, category, confidence, risk, evidence, remediation, RulePayload.empty());
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * 仲裁得分：置信度占 0.7，机制的成本-风险优先级占 0.3。
     * {@link AdCategory#ALREADY_HANDLED} 强制降到最低，见设计文档第 8.2 节。
     */
    public float score() {
        if (category == AdCategory.ALREADY_HANDLED) return 0f;
        return confidence * 0.7f + remediation.priorityWeight() * 0.3f;
    }

    /**
     * 是否能产出可落地的规则。除了机制本身要可执行，载荷也必须非空 ——
     * 否则「保存规则」会写出一条空规则。
     */
    public boolean actionable() {
        return remediation != RemediationKind.NONE
                && remediation != RemediationKind.SESSION_SKIP_ONLY
                && !payload.isEmpty();
    }
}
