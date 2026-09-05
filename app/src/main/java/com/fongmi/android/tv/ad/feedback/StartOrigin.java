package com.fongmi.android.tv.ad.feedback;

/**
 * 区间起点的来源。短按只提供一个时间点，起点按可靠性逐级推断。
 *
 * <p>顺序即可靠性：{@link #USER_MARKED} 最强，{@link #FALLBACK_WINDOW} 最弱。
 * 仲裁时用 {@link #weight()} 折算证据权重。
 */
public enum StartOrigin {
    /** 用户在标记模式下显式框选。 */
    USER_MARKED(1.0f),
    /** 落在最近一个 #EXT-X-DISCONTINUITY 边界上。 */
    DISCONTINUITY(0.95f),
    /** 落在最近一次切片跨域切换点上。 */
    CROSS_DOMAIN(0.85f),
    /** 复用音频/语音通道已有候选的起点。 */
    AUDIO_CANDIDATE(0.80f),
    /** 无任何信号，按固定窗口回溯。 */
    FALLBACK_WINDOW(0.55f);

    private final float weight;

    StartOrigin(float weight) {
        this.weight = weight;
    }

    /** 证据权重，0..1。 */
    public float weight() {
        return weight;
    }
}
