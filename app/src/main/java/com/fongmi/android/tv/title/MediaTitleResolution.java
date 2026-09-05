package com.fongmi.android.tv.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MediaTitleResolution {

    public static final String SOURCE_TMDB_CACHE = "TMDB_CACHE";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_RULE = "RULE";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_RULE_AI_MERGED = "RULE_AI_MERGED";
    public static final String SOURCE_CLEANED = "CLEANED";

    private String rawTitle;
    private String ruleTitle;
    private String canonicalTitle;
    private String originalTitle;
    private String mediaType;
    private int year;
    private int seasonNumber = -1;
    private int episodeNumber = -1;
    private String episodeTitle;
    private List<String> aliases = new ArrayList<>();
    private List<MediaTitleCandidate> candidates = new ArrayList<>();
    private float confidence;
    private String source;
    private boolean needsVerification;

    public String getRawTitle() {
        return clean(rawTitle);
    }

    public void setRawTitle(String rawTitle) {
        this.rawTitle = clean(rawTitle);
    }

    public String getRuleTitle() {
        return clean(ruleTitle);
    }

    public void setRuleTitle(String ruleTitle) {
        this.ruleTitle = clean(ruleTitle);
    }

    public String getCanonicalTitle() {
        return clean(canonicalTitle);
    }

    public void setCanonicalTitle(String canonicalTitle) {
        this.canonicalTitle = clean(canonicalTitle);
    }

    public String getOriginalTitle() {
        return clean(originalTitle);
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = clean(originalTitle);
    }

    public String getMediaType() {
        return MediaTitleLearningExample.normalizeMediaType(mediaType);
    }

    public void setMediaType(String mediaType) {
        this.mediaType = MediaTitleLearningExample.normalizeMediaType(mediaType);
    }

    public int getYear() {
        return year >= 1900 && year <= 2099 ? year : 0;
    }

    public void setYear(int year) {
        this.year = year >= 1900 && year <= 2099 ? year : 0;
    }

    public int getSeasonNumber() {
        return seasonNumber > 0 ? seasonNumber : -1;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber > 0 ? seasonNumber : -1;
    }

    public int getEpisodeNumber() {
        return episodeNumber > 0 ? episodeNumber : -1;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber > 0 ? episodeNumber : -1;
    }

    public String getEpisodeTitle() {
        return clean(episodeTitle);
    }

    public void setEpisodeTitle(String episodeTitle) {
        this.episodeTitle = clean(episodeTitle);
    }

    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    public void addAlias(String alias) {
        String value = clean(alias);
        if (value.isEmpty()) return;
        for (String item : aliases) if (item.equalsIgnoreCase(value)) return;
        aliases.add(value);
    }

    public List<MediaTitleCandidate> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public void addCandidate(MediaTitleCandidate candidate) {
        if (candidate == null || candidate.isEmpty()) return;
        for (MediaTitleCandidate item : candidates) if (item.equals(candidate)) return;
        candidates.add(candidate);
    }

    public float getConfidence() {
        if (Float.isNaN(confidence) || confidence < 0f) return 0f;
        return Math.min(1f, confidence);
    }

    public void setConfidence(float confidence) {
        this.confidence = Float.isNaN(confidence) || confidence < 0f ? 0f : Math.min(1f, confidence);
    }

    public String getSource() {
        return clean(source);
    }

    public void setSource(String source) {
        this.source = clean(source);
    }

    public boolean isNeedsVerification() {
        return needsVerification;
    }

    public void setNeedsVerification(boolean needsVerification) {
        this.needsVerification = needsVerification;
    }

    public List<String> queryTitles() {
        List<String> values = new ArrayList<>();
        for (MediaTitleCandidate candidate : candidates) addUnique(values, candidate.getTitle());
        addUnique(values, canonicalTitle);
        addUnique(values, ruleTitle);
        addUnique(values, rawTitle);
        return values;
    }

    private static void addUnique(List<String> values, String value) {
        String text = clean(value);
        if (text.isEmpty()) return;
        for (String item : values) if (item.equalsIgnoreCase(text)) return;
        values.add(text);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
