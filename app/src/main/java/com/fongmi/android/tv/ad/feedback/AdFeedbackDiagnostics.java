package com.fongmi.android.tv.ad.feedback;

import com.github.catvod.crawler.SpiderDebug;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 区间反馈的本地诊断计数。只暴露固定枚举计数，不记录媒体 URL、规则正文、
 * Cookie、Authorization 或用户标识 —— 与 {@code AdAudioDiagnostics} 同一约定。
 */
public final class AdFeedbackDiagnostics {

    static final String LOG_TAG = "ad-feedback";

    public enum Code {
        INTERVAL_SUBMITTED,
        START_INFERRED_FROM_DISCONTINUITY,
        START_INFERRED_FROM_FALLBACK,
        IMMEDIATE_SKIP_APPLIED,
        IMMEDIATE_SKIP_REJECTED,
        EVIDENCE_COLLECT_FAILED,
        CHANNEL_ABSTAINED,
        ARBITER_NO_PLAN,
        PLAN_ACCEPTED,
        PLAN_DISCARDED,
        DUPLICATE_SUBMISSION,
        ALREADY_HANDLED_DETECTED
    }

    private final EnumMap<Code, Long> counts = new EnumMap<>(Code.class);
    private Code lastCode;

    public void record(Code code) {
        if (code == null) return;
        synchronized (this) {
            counts.put(code, counts.getOrDefault(code, 0L) + 1L);
            lastCode = code;
        }
    }

    /** 把起点来源折算成对应的诊断码。 */
    public void recordStartOrigin(StartOrigin origin) {
        if (origin == StartOrigin.DISCONTINUITY) {
            record(Code.START_INFERRED_FROM_DISCONTINUITY);
        } else if (origin == StartOrigin.FALLBACK_WINDOW) {
            record(Code.START_INFERRED_FROM_FALLBACK);
        }
    }

    public synchronized long count(Code code) {
        return counts.getOrDefault(code, 0L);
    }

    public synchronized Code lastCode() {
        return lastCode;
    }

    public synchronized Map<Code, Long> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(counts));
    }

    static void log(String message, Object... args) {
        SpiderDebug.log(LOG_TAG + " " + String.format(message, args));
    }
}
