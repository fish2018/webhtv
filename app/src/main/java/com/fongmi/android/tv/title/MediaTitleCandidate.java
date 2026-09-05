package com.fongmi.android.tv.title;

import java.util.Objects;
import java.util.Locale;

public final class MediaTitleCandidate {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_TMDB = "tmdb";
    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_RULE = "rule";
    public static final String SOURCE_ALIAS = "alias";
    public static final String SOURCE_RAW = "raw";

    private String title;
    private String source;
    private float confidence;

    public MediaTitleCandidate() {
    }

    public MediaTitleCandidate(String title, String source, float confidence) {
        this.title = clean(title);
        this.source = clean(source);
        this.confidence = clamp(confidence);
    }

    public static MediaTitleCandidate of(String title, String source, float confidence) {
        return new MediaTitleCandidate(title, source, confidence);
    }

    public String getTitle() {
        return clean(title);
    }

    public String getSource() {
        return clean(source);
    }

    public float getConfidence() {
        return clamp(confidence);
    }

    public boolean isEmpty() {
        return getTitle().isEmpty();
    }

    private static float clamp(float value) {
        if (Float.isNaN(value) || value < 0f) return 0f;
        return Math.min(1f, value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaTitleCandidate other)) return false;
        return getTitle().equalsIgnoreCase(other.getTitle());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTitle().toLowerCase(Locale.ROOT));
    }
}
