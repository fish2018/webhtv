package com.fongmi.android.tv.subtitle.model;

public final class SubtitleCandidate {

    private final String provider;
    private final String candidateId;
    private final String displayName;
    private final String language;
    private final String format;
    private final String releaseInfo;
    private final int score;
    private final int year;
    private final int seasonNumber;
    private final int episodeNumber;
    private final SubtitleMatchType matchType;
    private final String queryKey;
    private final boolean requiresResolve;
    private final String providerPayload;

    public SubtitleCandidate(String provider, String candidateId, String displayName, String language, String format, String releaseInfo, int score, int year, int seasonNumber, int episodeNumber, SubtitleMatchType matchType, String queryKey, boolean requiresResolve, String providerPayload) {
        this.provider = provider == null ? "" : provider;
        this.candidateId = candidateId == null ? "" : candidateId;
        this.displayName = displayName == null ? "" : displayName;
        this.language = language == null ? "" : language;
        this.format = format == null ? "" : format;
        this.releaseInfo = releaseInfo == null ? "" : releaseInfo;
        this.score = score;
        this.year = year;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
        this.matchType = matchType == null ? SubtitleMatchType.METADATA_FUZZY : matchType;
        this.queryKey = queryKey == null ? "" : queryKey;
        this.requiresResolve = requiresResolve;
        this.providerPayload = providerPayload == null ? "" : providerPayload;
    }

    public String getProvider() {
        return provider;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguage() {
        return language;
    }

    public String getFormat() {
        return format;
    }

    public String getReleaseInfo() {
        return releaseInfo;
    }

    public int getScore() {
        return score;
    }

    public int getYear() {
        return year;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public SubtitleMatchType getMatchType() {
        return matchType;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public boolean isRequiresResolve() {
        return requiresResolve;
    }

    public String getProviderPayload() {
        return providerPayload;
    }

    public SubtitleCandidate withScore(int score, SubtitleMatchType matchType) {
        return new SubtitleCandidate(provider, candidateId, displayName, language, format, releaseInfo, score, year, seasonNumber, episodeNumber, matchType, queryKey, requiresResolve, providerPayload);
    }
}
