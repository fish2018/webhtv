package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

import java.util.Locale;

@Entity(
        primaryKeys = {"cid", "mediaType", "tmdbId", "seasonNumber"},
        indices = {
                @Index(value = {"cid", "mediaType", "tmdbId", "sourceHistoryKey", "updatedAt"}),
                @Index(value = {"cid", "sourceHistoryKey"})
        })
public class TmdbSeasonProgress {

    public int cid;
    @NonNull
    public String mediaType = "tv";
    public int tmdbId;
    public int seasonNumber;
    public int episodeNumber;
    public long position;
    public long duration;
    @NonNull
    public String sourceFlag = "";
    @NonNull
    public String sourceEpisodeName = "";
    @NonNull
    public String sourceEpisodeUrl = "";
    @NonNull
    public String sourceHistoryKey = "";
    @NonNull
    public String sourceBindingKey = "";
    public long updatedAt;

    public TmdbSeasonProgress() {
    }

    public static TmdbSeasonProgress of(int cid, String mediaType, int tmdbId,
                                        int seasonNumber, int episodeNumber,
                                        long position, long duration, String sourceHistoryKey) {
        TmdbSeasonProgress progress = new TmdbSeasonProgress();
        progress.cid = cid;
        progress.mediaType = normalizeMediaType(mediaType);
        progress.tmdbId = tmdbId;
        progress.seasonNumber = seasonNumber;
        progress.episodeNumber = episodeNumber;
        progress.position = position;
        progress.duration = duration;
        progress.sourceHistoryKey = sourceHistoryKey == null ? "" : sourceHistoryKey;
        progress.updatedAt = System.currentTimeMillis();
        return progress;
    }

    public String identityKey() {
        return cid + ":" + mediaType + ":" + tmdbId + ":season:" + seasonNumber;
    }

    public static String normalizeMediaType(String mediaType) {
        return mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
    }
}
