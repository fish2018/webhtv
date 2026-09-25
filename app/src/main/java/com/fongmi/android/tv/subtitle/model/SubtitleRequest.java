package com.fongmi.android.tv.subtitle.model;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SubtitleRequest {

    private final String playbackKey;
    private final String siteKey;
    private final String siteName;
    private final String vodId;
    private final String vodName;
    private final String vodRemarks;
    private final String vodYear;
    private final String episodeName;
    private final String playUrl;
    private final Map<String, String> playHeaders;
    private final String preferredLanguage;
    private final SubtitleTrigger trigger;
    private final boolean allowTmdbLookup;
    private final boolean manualOnly;
    private final TmdbItem tmdbItem;
    private final TmdbEpisode tmdbEpisode;
    private final int seasonNumber;
    private final int episodeNumber;

    private SubtitleRequest(Builder builder) {
        this.playbackKey = builder.playbackKey;
        this.siteKey = builder.siteKey;
        this.siteName = builder.siteName;
        this.vodId = builder.vodId;
        this.vodName = builder.vodName;
        this.vodRemarks = builder.vodRemarks;
        this.vodYear = builder.vodYear;
        this.episodeName = builder.episodeName;
        this.playUrl = builder.playUrl;
        this.playHeaders = Collections.unmodifiableMap(new HashMap<>(builder.playHeaders));
        this.preferredLanguage = builder.preferredLanguage;
        this.trigger = builder.trigger;
        this.allowTmdbLookup = builder.allowTmdbLookup;
        this.manualOnly = builder.manualOnly;
        this.tmdbItem = builder.tmdbItem;
        this.tmdbEpisode = builder.tmdbEpisode;
        this.seasonNumber = builder.seasonNumber;
        this.episodeNumber = builder.episodeNumber;
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

    public String getSiteName() {
        return siteName;
    }

    public String getVodId() {
        return vodId;
    }

    public String getVodName() {
        return vodName;
    }

    public String getVodRemarks() {
        return vodRemarks;
    }

    public String getVodYear() {
        return vodYear;
    }

    public String getEpisodeName() {
        return episodeName;
    }

    public String getPlayUrl() {
        return playUrl;
    }

    public Map<String, String> getPlayHeaders() {
        return playHeaders;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public SubtitleTrigger getTrigger() {
        return trigger;
    }

    public boolean isAllowTmdbLookup() {
        return allowTmdbLookup;
    }

    public boolean isManualOnly() {
        return manualOnly;
    }

    @Nullable
    public TmdbItem getTmdbItem() {
        return tmdbItem;
    }

    @Nullable
    public TmdbEpisode getTmdbEpisode() {
        return tmdbEpisode;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public static final class Builder {

        private String playbackKey = "";
        private String siteKey = "";
        private String siteName = "";
        private String vodId = "";
        private String vodName = "";
        private String vodRemarks = "";
        private String vodYear = "";
        private String episodeName = "";
        private String playUrl = "";
        private Map<String, String> playHeaders = new HashMap<>();
        private String preferredLanguage = "zh";
        private SubtitleTrigger trigger = SubtitleTrigger.AUTO_PLAY;
        private boolean allowTmdbLookup = true;
        private boolean manualOnly = false;
        private TmdbItem tmdbItem;
        private TmdbEpisode tmdbEpisode;
        private int seasonNumber = -1;
        private int episodeNumber = -1;

        public Builder playbackKey(String playbackKey) {
            this.playbackKey = playbackKey == null ? "" : playbackKey;
            return this;
        }

        public Builder siteKey(String siteKey) {
            this.siteKey = siteKey == null ? "" : siteKey;
            return this;
        }

        public Builder siteName(String siteName) {
            this.siteName = siteName == null ? "" : siteName;
            return this;
        }

        public Builder vodId(String vodId) {
            this.vodId = vodId == null ? "" : vodId;
            return this;
        }

        public Builder vodName(String vodName) {
            this.vodName = vodName == null ? "" : vodName;
            return this;
        }

        public Builder vodRemarks(String vodRemarks) {
            this.vodRemarks = vodRemarks == null ? "" : vodRemarks;
            return this;
        }

        public Builder vodYear(String vodYear) {
            this.vodYear = vodYear == null ? "" : vodYear;
            return this;
        }

        public Builder episodeName(String episodeName) {
            this.episodeName = episodeName == null ? "" : episodeName;
            return this;
        }

        public Builder playUrl(String playUrl) {
            this.playUrl = playUrl == null ? "" : playUrl;
            return this;
        }

        public Builder playHeaders(Map<String, String> playHeaders) {
            this.playHeaders = playHeaders == null ? new HashMap<>() : new HashMap<>(playHeaders);
            return this;
        }

        public Builder preferredLanguage(String preferredLanguage) {
            this.preferredLanguage = preferredLanguage == null ? "zh" : preferredLanguage;
            return this;
        }

        public Builder trigger(SubtitleTrigger trigger) {
            this.trigger = trigger == null ? SubtitleTrigger.AUTO_PLAY : trigger;
            return this;
        }

        public Builder allowTmdbLookup(boolean allowTmdbLookup) {
            this.allowTmdbLookup = allowTmdbLookup;
            return this;
        }

        public Builder manualOnly(boolean manualOnly) {
            this.manualOnly = manualOnly;
            return this;
        }

        public Builder tmdbItem(TmdbItem tmdbItem) {
            this.tmdbItem = tmdbItem;
            return this;
        }

        public Builder tmdbEpisode(TmdbEpisode tmdbEpisode) {
            this.tmdbEpisode = tmdbEpisode;
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

        public SubtitleRequest build() {
            return new SubtitleRequest(this);
        }
    }
}
