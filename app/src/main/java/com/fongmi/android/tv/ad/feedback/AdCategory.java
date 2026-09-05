package com.fongmi.android.tv.ad.feedback;

/**
 * 用户反馈区间的归因类别。
 *
 * <p>见 {@code docs/ad-feedback-timerange-design.md} 第 6.2 节。
 */
public enum AdCategory {
    /** 切片来自非本站适配域名。 */
    THIRD_PARTY_CDN_SEGMENT,
    /** 固定时长硬插块。 */
    FIXED_DURATION_BLOCK,
    /** 被 DISCONTINUITY 包裹的独立块。 */
    DISCONTINUITY_BLOCK,
    /** 命中已知广告域名。 */
    KNOWN_AD_HOST,
    /** 音频频谱指纹匹配。 */
    AUDIO_FINGERPRINT,
    /** 语音关键词命中。 */
    SPEECH_KEYWORD,
    /** 压制进正片，无结构特征可用。 */
    IN_STREAM_BURNED_IN,
    /** 已被现有规则处理，用户看到的广告来自别处。 */
    ALREADY_HANDLED,
    UNKNOWN
}
