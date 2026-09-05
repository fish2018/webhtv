package com.fongmi.android.tv.subtitle.translate;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;

public final class SubtitleTranslationRequest {

    public static final String MODE_TRANSLATED = "translated";
    public static final String MODE_BILINGUAL = "bilingual";
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_AUTO_FALLBACK = "auto_fallback";
    public static final String TRIGGER_CURRENT_SUBTITLE = "current_subtitle";

    private final String playbackKey;
    private final SubtitleRequest subtitleRequest;
    private final SubtitleAsset sourceAsset;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final String mode;
    private final String trigger;
    private final boolean useCache;

    private SubtitleTranslationRequest(Builder builder) {
        this.playbackKey = trimOr(builder.playbackKey, "");
        this.subtitleRequest = builder.subtitleRequest;
        this.sourceAsset = builder.sourceAsset;
        this.sourceLanguage = trimOr(builder.sourceLanguage, "auto");
        this.targetLanguage = trimOr(builder.targetLanguage, "zh-Hans");
        this.mode = trimOr(builder.mode, MODE_TRANSLATED);
        this.trigger = trimOr(builder.trigger, TRIGGER_MANUAL);
        this.useCache = builder.useCache;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPlaybackKey() {
        return playbackKey;
    }

    public SubtitleRequest getSubtitleRequest() {
        return subtitleRequest;
    }

    public SubtitleAsset getSourceAsset() {
        return sourceAsset;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public String getMode() {
        return mode;
    }

    public String getTrigger() {
        return trigger;
    }

    public boolean isUseCache() {
        return useCache;
    }

    private static String trimOr(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    public static final class Builder {

        private String playbackKey = "";
        private SubtitleRequest subtitleRequest;
        private SubtitleAsset sourceAsset;
        private String sourceLanguage = "auto";
        private String targetLanguage = "zh-Hans";
        private String mode = MODE_TRANSLATED;
        private String trigger = TRIGGER_MANUAL;
        private boolean useCache = true;

        public Builder playbackKey(String playbackKey) {
            this.playbackKey = playbackKey;
            return this;
        }

        public Builder subtitleRequest(SubtitleRequest subtitleRequest) {
            this.subtitleRequest = subtitleRequest;
            return this;
        }

        public Builder sourceAsset(SubtitleAsset sourceAsset) {
            this.sourceAsset = sourceAsset;
            return this;
        }

        public Builder sourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
            return this;
        }

        public Builder targetLanguage(String targetLanguage) {
            this.targetLanguage = targetLanguage;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder trigger(String trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public SubtitleTranslationRequest build() {
            return new SubtitleTranslationRequest(this);
        }
    }
}
