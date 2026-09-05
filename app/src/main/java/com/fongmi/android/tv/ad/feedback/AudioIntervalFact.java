package com.fongmi.android.tv.ad.feedback;

import java.util.List;

/**
 * 音频指纹通道在区间内的既有匹配结果。Phase 1 只读，不采集新指纹。
 *
 * @param enabled      音频指纹功能是否已开启
 * @param matchedRuleIds 区间内命中的规则 id
 * @param captureReady 本次播放是否具备采集条件（Exo、非直播、可 seek、时长已知）
 */
public record AudioIntervalFact(boolean enabled, List<String> matchedRuleIds, boolean captureReady) {

    public AudioIntervalFact {
        matchedRuleIds = matchedRuleIds == null ? List.of() : List.copyOf(matchedRuleIds);
    }

    public static AudioIntervalFact unavailable() {
        return new AudioIntervalFact(false, List.of(), false);
    }

    public boolean hasMatch() {
        return !matchedRuleIds.isEmpty();
    }
}
