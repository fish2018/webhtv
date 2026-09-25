package com.fongmi.android.tv.subtitle.model;

public final class ResolvedMediaIdentity {

    private final int tmdbId;
    private final int tmdbEpisodeId;
    private final String mediaType;
    private final String canonicalTitle;
    private final String originalTitle;
    private final int year;
    private final int seasonNumber;
    private final int episodeNumber;
    private final String episodeTitle;
    private final boolean fromCache;
    private final boolean fromBoundEpisode;
    private final boolean fromRemoteResolve;

    private ResolvedMediaIdentity(Builder builder) {
        this.tmdbId = builder.tmdbId;
        this.tmdbEpisodeId = builder.tmdbEpisodeId;
        this.mediaType = builder.mediaType;
        this.canonicalTitle = builder.canonicalTitle;
        this.originalTitle = builder.originalTitle;
        this.year = builder.year;
        this.seasonNumber = builder.seasonNumber;
        this.episodeNumber = builder.episodeNumber;
        this.episodeTitle = builder.episodeTitle;
        this.fromCache = builder.fromCache;
        this.fromBoundEpisode = builder.fromBoundEpisode;
        this.fromRemoteResolve = builder.fromRemoteResolve;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public int getTmdbEpisodeId() {
        return tmdbEpisodeId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public String getOriginalTitle() {
        return originalTitle;
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

    public String getEpisodeTitle() {
        return episodeTitle;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public boolean isFromBoundEpisode() {
        return fromBoundEpisode;
    }

    public boolean isFromRemoteResolve() {
        return fromRemoteResolve;
    }

    public boolean hasTmdbIdentity() {
        return tmdbId > 0 || tmdbEpisodeId > 0;
    }

    public static final class Builder {

        private int tmdbId;
        private int tmdbEpisodeId;
        private String mediaType = "";
        private String canonicalTitle = "";
        private String originalTitle = "";
        private int year;
        private int seasonNumber = -1;
        private int episodeNumber = -1;
        private String episodeTitle = "";
        private boolean fromCache;
        private boolean fromBoundEpisode;
        private boolean fromRemoteResolve;

        public Builder tmdbId(int tmdbId) {
            this.tmdbId = tmdbId;
            return this;
        }

        public Builder tmdbEpisodeId(int tmdbEpisodeId) {
            this.tmdbEpisodeId = tmdbEpisodeId;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType == null ? "" : mediaType;
            return this;
        }

        public Builder canonicalTitle(String canonicalTitle) {
            this.canonicalTitle = canonicalTitle == null ? "" : canonicalTitle;
            return this;
        }

        public Builder originalTitle(String originalTitle) {
            this.originalTitle = originalTitle == null ? "" : originalTitle;
            return this;
        }

        public Builder year(int year) {
            this.year = year;
            return this;
        }

        public Builder seasonNumber(int seasonNumber) {
            this.seasonNumber = seasonNumber;
            return this;
        }

        public Builder episodeNumber(int episodeNumber) {
            this.episodeNumber = episodeNumber;
            return this;
        }

        public Builder episodeTitle(String episodeTitle) {
            this.episodeTitle = episodeTitle == null ? "" : episodeTitle;
            return this;
        }

        public Builder fromCache(boolean fromCache) {
            this.fromCache = fromCache;
            return this;
        }

        public Builder fromBoundEpisode(boolean fromBoundEpisode) {
            this.fromBoundEpisode = fromBoundEpisode;
            return this;
        }

        public Builder fromRemoteResolve(boolean fromRemoteResolve) {
            this.fromRemoteResolve = fromRemoteResolve;
            return this;
        }

        public ResolvedMediaIdentity build() {
            return new ResolvedMediaIdentity(this);
        }
    }
}
