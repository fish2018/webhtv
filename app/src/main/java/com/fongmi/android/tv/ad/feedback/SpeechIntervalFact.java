package com.fongmi.android.tv.ad.feedback;

/**
 * 语音关键词通道在区间内的既有命中情况。
 *
 * <p>刻意不记录命中的具体关键词：{@code SpeechAdSignalProvider} 的日志也遵守同一约定，
 * 关键词可能包含用户自定义的敏感内容。
 *
 * @param enabled  语音去广是否已开启
 * @param hitCount 区间内命中次数
 */
public record SpeechIntervalFact(boolean enabled, int hitCount) {

    public SpeechIntervalFact {
        if (hitCount < 0) hitCount = 0;
    }

    public static SpeechIntervalFact unavailable() {
        return new SpeechIntervalFact(false, 0);
    }

    public boolean hasMatch() {
        return hitCount > 0;
    }
}
