package com.fongmi.android.tv.ad.feedback;

/**
 * 归因结论对应的去广机制。
 *
 * <p>{@link #ordinal()} 即成本-风险优先级：越靠前越优先。见设计文档第 8.1 节。
 * 仲裁器依赖这个顺序，重排枚举会改变仲裁结果。
 */
public enum RemediationKind {
    /** 启用一条已存在但默认关闭的规则，运行时零新增成本。 */
    ENABLE_EXISTING_RULE,
    /** 写入 {@code UserAdRule.hosts}。 */
    HOST_BLACKLIST,
    /** 写入 {@code HlsAdRule}，由 HlsManifestCleaner 执行。 */
    HLS_STRUCTURED_RULE,
    /** 写入 {@code UserAdRule.regex}。 */
    URL_REGEX_RULE,
    /** 写入 {@code AudioFingerprintRule}（Phase 2）。 */
    AUDIO_FINGERPRINT_RULE,
    /** 追加语音关键词（Phase 2，仅建议）。 */
    SPEECH_KEYWORD,
    /** 无法泛化，只能本次跳过。 */
    SESSION_SKIP_ONLY,
    /** 纯诊断结论，不产出规则。 */
    NONE;

    /** 成本-风险排序权重，0..1，越大越优先。 */
    public float priorityWeight() {
        int span = values().length - 1;
        return span == 0 ? 1f : 1f - (float) ordinal() / span;
    }
}
