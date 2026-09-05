package com.fongmi.android.tv.ad.feedback;

import com.fongmi.android.tv.bean.M3u8Evidence;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放时间 ↔ HLS 切片下标的映射，以及短按场景的起点推断。
 *
 * <p>纯计算，不做网络请求：{@code M3u8Parser.parse()} 已经产出全部所需输入。
 * 见设计文档第 4.2、7.1 节。
 */
public final class AdIntervalMapper {

    /** 短按回溯窗口默认值，见设计文档待确认决策 1。 */
    public static final long DEFAULT_FALLBACK_WINDOW_MS = 90_000L;

    private AdIntervalMapper() {
    }

    /**
     * 区间起点推断结果。
     *
     * @param startMs 推断出的起点
     * @param origin  起点来源
     */
    public record InferredStart(long startMs, StartOrigin origin) {
    }

    /**
     * 找出与 {@code [startMs, endMs)} 有交集的切片下标。
     *
     * <p>切片 {@code i} 覆盖 {@code [cum[i], cum[i+1])}，判据是半开区间相交：
     * {@code cum[i+1] > startSec && cum[i] < endSec}。恰好贴边不算相交。
     */
    public static List<Integer> insideIndices(List<Float> durations, long startMs, long endMs) {
        List<Integer> indices = new ArrayList<>();
        if (durations == null || durations.isEmpty() || endMs <= startMs) return indices;
        double startSec = startMs / 1000d;
        double endSec = endMs / 1000d;
        double cum = 0;
        for (int i = 0; i < durations.size(); i++) {
            Float value = durations.get(i);
            double duration = value == null ? 0 : value;
            double next = cum + duration;
            if (next > startSec && cum < endSec) indices.add(i);
            cum = next;
        }
        return indices;
    }

    /** 切片 {@code index} 的起始播放位置，毫秒。 */
    public static long segmentStartMs(List<Float> durations, int index) {
        if (durations == null || index <= 0) return 0L;
        double cum = 0;
        for (int i = 0; i < index && i < durations.size(); i++) {
            Float value = durations.get(i);
            if (value != null) cum += value;
        }
        return Math.round(cum * 1000d);
    }

    /**
     * 短按场景下由终点回溯推断起点。按可靠性逐级尝试：
     * DISCONTINUITY 边界 → 跨域切换点 → 音频候选 → 固定窗口。
     *
     * @param evidence        playlist 证据，可为 null
     * @param endMs           用户点击时的播放位置
     * @param audioCandidateStartMs 音频/语音通道已有候选的起点，无则传负数
     * @param fallbackWindowMs 兜底回溯窗口
     */
    public static InferredStart inferStart(M3u8Evidence evidence, long endMs,
                                          long audioCandidateStartMs, long fallbackWindowMs) {
        if (endMs <= 0) return new InferredStart(0L, StartOrigin.FALLBACK_WINDOW);

        if (evidence != null && !evidence.isEmpty()) {
            List<Float> durations = evidence.getDurations();

            long discontinuity = latestBoundaryBefore(durations, evidence.getDiscontinuities(), endMs);
            if (discontinuity >= 0) return new InferredStart(discontinuity, StartOrigin.DISCONTINUITY);

            long crossDomain = latestSwitchBefore(durations, evidence.getDomainSwitches(), endMs);
            if (crossDomain >= 0) return new InferredStart(crossDomain, StartOrigin.CROSS_DOMAIN);
        }

        if (audioCandidateStartMs >= 0 && audioCandidateStartMs < endMs) {
            return new InferredStart(audioCandidateStartMs, StartOrigin.AUDIO_CANDIDATE);
        }

        long window = fallbackWindowMs > 0 ? fallbackWindowMs : DEFAULT_FALLBACK_WINDOW_MS;
        return new InferredStart(Math.max(0L, endMs - window), StartOrigin.FALLBACK_WINDOW);
    }

    /** 最近一个位于 {@code endMs} 之前的断点边界起始位置，没有则返回 -1。 */
    private static long latestBoundaryBefore(List<Float> durations, List<Integer> discontinuities, long endMs) {
        if (durations == null || discontinuities == null || discontinuities.isEmpty()) return -1L;
        long best = -1L;
        for (Integer index : discontinuities) {
            if (index == null || index < 0 || index >= durations.size()) continue;
            long position = segmentStartMs(durations, index);
            if (position < endMs && position > best) best = position;
        }
        return best;
    }

    /** 最近一个位于 {@code endMs} 之前的跨域切换点起始位置，没有则返回 -1。 */
    private static long latestSwitchBefore(List<Float> durations, List<Boolean> domainSwitches, long endMs) {
        if (durations == null || domainSwitches == null || domainSwitches.isEmpty()) return -1L;
        long best = -1L;
        for (int i = 0; i < domainSwitches.size() && i < durations.size(); i++) {
            Boolean switched = domainSwitches.get(i);
            if (switched == null || !switched) continue;
            long position = segmentStartMs(durations, i);
            if (position < endMs && position > best) best = position;
        }
        return best;
    }
}
