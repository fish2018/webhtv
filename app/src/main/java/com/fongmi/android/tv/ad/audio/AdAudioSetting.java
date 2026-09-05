package com.fongmi.android.tv.ad.audio;

import com.github.catvod.utils.Prefers;

public final class AdAudioSetting {

    /** 社区规则源，按上游 m3u8-ad-audio-rules 的公开地址；用户可改。 */
    public static final String DEFAULT_PROBE_RULE_URL =
            "https://m3u8-ad-audio-rules-sync.ccfork.workers.dev/rules.json";

    private static final String KEY_ENABLED = "ad_audio_fingerprint_enabled";
    private static final String KEY_AUTO_SKIP = "ad_audio_auto_skip_enabled";
    private static final String KEY_PROBE_RULE_URL = "ad_audio_probe_rule_url";
    private static final String KEY_PROBE_CHECKED_AT = "ad_audio_probe_rule_checked_at";
    private static final long PROBE_REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000;

    private AdAudioSetting() {
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        Prefers.put(KEY_ENABLED, enabled);
    }

    public static boolean isAutoSkipEnabled() {
        return Prefers.getBoolean(KEY_AUTO_SKIP, false);
    }

    public static void setAutoSkipEnabled(boolean enabled) {
        Prefers.put(KEY_AUTO_SKIP, enabled);
    }

    public static String getProbeRuleUrl() {
        return Prefers.getString(KEY_PROBE_RULE_URL, DEFAULT_PROBE_RULE_URL).trim();
    }

    public static void setProbeRuleUrl(String url) {
        Prefers.put(KEY_PROBE_RULE_URL, url == null ? "" : url.trim());
        Prefers.put(KEY_PROBE_CHECKED_AT, 0L);
    }

    /** 距上次检查超过刷新间隔才允许再次联网，避免每次建播放器都打一次请求。 */
    public static boolean isProbeRefreshDue(long nowMs) {
        long checkedAt = Prefers.getLong(KEY_PROBE_CHECKED_AT, 0L);
        return checkedAt <= 0L || nowMs - checkedAt >= PROBE_REFRESH_INTERVAL_MS
                || nowMs < checkedAt;
    }

    public static void markProbeRefreshed(long nowMs) {
        Prefers.put(KEY_PROBE_CHECKED_AT, nowMs);
    }
}
