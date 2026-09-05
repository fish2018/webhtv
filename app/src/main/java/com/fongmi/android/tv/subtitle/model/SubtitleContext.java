package com.fongmi.android.tv.subtitle.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SubtitleContext {

    private final String playbackKey;
    private final String siteKey;
    private final String vodId;
    private final String mediaType;
    private final String mediaPath;
    private final String canonicalTitle;
    private final String originalTitle;
    private final List<String> aliases;
    private final int year;
    private final int seasonNumber;
    private final int episodeNumber;
    private final String episodeTitle;
    private final String preferredLanguage;
    private final String originalLanguage;
    private final String originCountry;
    private final boolean networkStream;
    private final ResolvedMediaIdentity identity;

    private SubtitleContext(Builder builder) {
        this.playbackKey = builder.playbackKey;
        this.siteKey = builder.siteKey;
        this.vodId = builder.vodId;
        this.mediaType = builder.mediaType;
        this.mediaPath = builder.mediaPath;
        this.canonicalTitle = builder.canonicalTitle;
        this.originalTitle = builder.originalTitle;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(builder.aliases));
        this.year = builder.year;
        this.seasonNumber = builder.seasonNumber;
        this.episodeNumber = builder.episodeNumber;
        this.episodeTitle = builder.episodeTitle;
        this.preferredLanguage = builder.preferredLanguage;
        this.originalLanguage = builder.originalLanguage;
        this.originCountry = builder.originCountry;
        this.networkStream = builder.networkStream;
        this.identity = builder.identity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPlaybackKey() {
        return playbackKey;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getVodId() {
        return vodId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public List<String> getAliases() {
        return aliases;
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

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public boolean isNetworkStream() {
        return networkStream;
    }

    public ResolvedMediaIdentity getIdentity() {
        return identity;
    }

    public boolean hasTmdbIdentity() {
        return identity != null && identity.hasTmdbIdentity();
    }

    public static final class Builder {

        private String playbackKey = "";
        private String siteKey = "";
        private String vodId = "";
        private String mediaType = "";
        private String mediaPath = "";
        private String canonicalTitle = "";
        private String originalTitle = "";
        private final List<String> aliases = new ArrayList<>();
        private int year;
        private int seasonNumber = -1;
        private int episodeNumber = -1;
        private String episodeTitle = "";
        private String preferredLanguage = "zh";
        private String originalLanguage = "";
        private String originCountry = "";
        private boolean networkStream = true;
        private ResolvedMediaIdentity identity;

        public Builder playbackKey(String playbackKey) {
            this.playbackKey = playbackKey == null ? "" : playbackKey;
            return this;
        }

        public Builder siteKey(String siteKey) {
            this.siteKey = siteKey == null ? "" : siteKey;
            return this;
        }

        public Builder vodId(String vodId) {
            this.vodId = vodId == null ? "" : vodId;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType == null ? "" : mediaType;
            return this;
        }

        public Builder mediaPath(String mediaPath) {
            this.mediaPath = mediaPath == null ? "" : mediaPath;
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

        public Builder addAlias(String alias) {
            if (alias != null && !alias.isEmpty() && !aliases.contains(alias)) aliases.add(alias);
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases.clear();
            if (aliases != null) for (String alias : aliases) addAlias(alias);
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

        public Builder preferredLanguage(String preferredLanguage) {
            this.preferredLanguage = preferredLanguage == null ? "zh" : preferredLanguage;
            return this;
        }

        public Builder originalLanguage(String originalLanguage) {
            this.originalLanguage = originalLanguage == null ? "" : originalLanguage;
            return this;
        }

        public Builder originCountry(String originCountry) {
            this.originCountry = originCountry == null ? "" : originCountry;
            return this;
        }

        public Builder networkStream(boolean networkStream) {
            this.networkStream = networkStream;
            return this;
        }

        public Builder identity(ResolvedMediaIdentity identity) {
            this.identity = identity;
            return this;
        }

        public SubtitleContext build() {
            return new SubtitleContext(this);
        }
    }
}
