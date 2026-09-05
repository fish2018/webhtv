package com.fongmi.android.tv.ad.audio;

import com.github.catvod.utils.Prefers;

import java.util.Objects;

public final class SpeechAdSetting {
    private static final String KEY_ENABLED = "speech_ad_enabled";
    private static final String KEY_KEYWORDS = "speech_ad_keywords";
    private static final String KEY_SKIP_SECONDS = "speech_ad_skip_seconds";
    private static final String KEY_SKIP_MODE = "speech_ad_skip_mode";

    public static SpeechAdConfig snapshot() {
        return SpeechAdConfig.create(
                Prefers.getBoolean(KEY_ENABLED, false),
                Prefers.getString(KEY_KEYWORDS, SpeechAdConfig.DEFAULT_KEYWORDS),
                Prefers.getInt(KEY_SKIP_SECONDS, 15),
                Prefers.getString(KEY_SKIP_MODE, AdSkipPolicyController.Mode.PROMPT.name()));
    }

    public static void setEnabled(boolean value) {
        Prefers.put(KEY_ENABLED, value);
    }

    public static void setKeywords(String value) {
        Prefers.put(KEY_KEYWORDS, String.join(",", SpeechAdKeywordSet.parse(value).values()));
    }

    public static void setSkipSeconds(int value) {
        Prefers.put(KEY_SKIP_SECONDS, Math.max(1, Math.min(120, value)));
    }

    public static void setMode(AdSkipPolicyController.Mode value) {
        Prefers.put(KEY_SKIP_MODE, Objects.requireNonNull(value, "mode").name());
    }

    private SpeechAdSetting() {
    }
}