package com.fongmi.android.tv.ad.feedback;

import java.util.List;

/**
 * 一次区间反馈的完整状态，驱动对话框两屏展示。
 *
 * @param feedbackId  去重用的稳定标识
 * @param startMs     区间起点
 * @param endMs       区间终点
 * @param startOrigin 起点来源，第一屏需明示
 * @param skipApplied 本次播放是否已跳过
 * @param verdict     归因仲裁结果，第二屏内容；尚未完成时为 null
 */
public record AdFeedbackSession(String feedbackId, long startMs, long endMs,
                                StartOrigin startOrigin, boolean skipApplied,
                                AdAttributionArbiter.Verdict verdict) {

    public AdFeedbackSession {
        if (feedbackId == null || feedbackId.isBlank()) {
            throw new IllegalArgumentException("feedbackId is required");
        }
        startOrigin = startOrigin == null ? StartOrigin.FALLBACK_WINDOW : startOrigin;
    }

    /** 第一屏：区间已确定，归因未开始。 */
    public static AdFeedbackSession pending(String feedbackId, long startMs, long endMs,
                                           StartOrigin startOrigin, boolean skipApplied) {
        return new AdFeedbackSession(feedbackId, startMs, endMs, startOrigin, skipApplied, null);
    }

    /** 第二屏：附加归因结果。 */
    public AdFeedbackSession withVerdict(AdAttributionArbiter.Verdict verdict) {
        return new AdFeedbackSession(feedbackId, startMs, endMs, startOrigin, skipApplied, verdict);
    }

    public long durationMs() {
        return endMs - startMs;
    }

    public boolean analysisComplete() {
        return verdict != null;
    }

    /** 有可落地的规则方案。 */
    public boolean hasActionablePlan() {
        return verdict != null && verdict.hasActionablePlan();
    }

    /** 首选方案的证据，无结论时为空。 */
    public List<String> preferredEvidence() {
        if (verdict == null || verdict.preferred() == null) return List.of();
        return verdict.preferred().evidence();
    }
}
