package com.fongmi.android.tv.ad.feedback;

import java.util.List;

/**
 * 用户反馈区间的证据快照。一次采集，之后所有分类通道只读。
 *
 * <p>去敏原则沿用 {@code AdDetectionRequest}：只保留 host 与去参数 path，
 * 不含完整 URL、query、token、Cookie。见设计文档第 6.1、10 节。
 *
 * @param startMs                          区间起点，毫秒
 * @param endMs                            区间终点，毫秒
 * @param startOrigin                      起点来源
 * @param siteKey                          站点标识
 * @param siteName                         站点名称
 * @param vodName                          剧名
 * @param flagName                         线路名
 * @param episodeName                      集名
 * @param playlistHost                     主 playlist 的 host
 * @param urlPath                          播放地址去参数 path
 * @param hls                              是否 HLS
 * @param inside                           区间内切片
 * @param outside                          区间外切片，作为对照组
 * @param boundedByDiscontinuity           区间首尾是否都贴着 DISCONTINUITY 边界
 * @param crossDomain                      区间内切片是否整体跨域
 * @param alreadyRemovedByStructuredRuleIds 已被结构化规则删除的规则 id
 * @param legacyHeuristicActive            旧启发式引擎当次是否生效
 * @param matchedExistingHosts             命中现有黑名单的 host
 * @param audio                            音频指纹通道事实
 * @param speech                           语音通道事实
 */
public record AdIntervalEvidence(
        long startMs, long endMs, StartOrigin startOrigin,
        String siteKey, String siteName, String vodName,
        String flagName, String episodeName,
        String playlistHost, String urlPath, boolean hls,
        List<SegmentFact> inside, List<SegmentFact> outside,
        boolean boundedByDiscontinuity, boolean crossDomain,
        List<String> alreadyRemovedByStructuredRuleIds,
        boolean legacyHeuristicActive,
        List<String> matchedExistingHosts,
        AudioIntervalFact audio, SpeechIntervalFact speech) {

    public AdIntervalEvidence {
        if (startMs < 0) throw new IllegalArgumentException("startMs must not be negative");
        if (endMs <= startMs) throw new IllegalArgumentException("endMs must be greater than startMs");
        startOrigin = startOrigin == null ? StartOrigin.FALLBACK_WINDOW : startOrigin;
        siteKey = siteKey == null ? "" : siteKey;
        siteName = siteName == null ? "" : siteName;
        vodName = vodName == null ? "" : vodName;
        flagName = flagName == null ? "" : flagName;
        episodeName = episodeName == null ? "" : episodeName;
        playlistHost = playlistHost == null ? "" : playlistHost;
        urlPath = urlPath == null ? "" : urlPath;
        inside = inside == null ? List.of() : List.copyOf(inside);
        outside = outside == null ? List.of() : List.copyOf(outside);
        alreadyRemovedByStructuredRuleIds = alreadyRemovedByStructuredRuleIds == null
                ? List.of() : List.copyOf(alreadyRemovedByStructuredRuleIds);
        matchedExistingHosts = matchedExistingHosts == null ? List.of() : List.copyOf(matchedExistingHosts);
        audio = audio == null ? AudioIntervalFact.unavailable() : audio;
        speech = speech == null ? SpeechIntervalFact.unavailable() : speech;
    }

    /** 区间时长，毫秒。 */
    public long durationMs() {
        return endMs - startMs;
    }

    /** 区间内是否有可用的切片证据。无切片时 HLS 通道必须弃权。 */
    public boolean hasSegmentEvidence() {
        return hls && !inside.isEmpty();
    }

    /**
     * 用户报告的区间是否已被结构化规则整体处理掉。
     * 为真时说明用户看到的广告来自别处，不应再新增 HLS 规则。
     */
    public boolean handledByStructuredRule() {
        return !alreadyRemovedByStructuredRuleIds.isEmpty();
    }
}
