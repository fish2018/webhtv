package com.fongmi.android.tv.following;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "following",
        indices = {
                @Index(value = {"series_key"}),
                @Index(value = {"enabled", "next_check_at"}),
                @Index(value = {"has_update", "updated_at"}),
                @Index(value = {"cid", "site_key", "vod_id"}),
                @Index(value = {"tmdb_id", "media_type", "tracked_season"})
        })
public class Following {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "identity_key")
    public String identityKey = "";

    @NonNull
    @ColumnInfo(name = "series_key")
    public String seriesKey = "";

    public int cid;

    @NonNull
    @ColumnInfo(name = "site_key")
    public String siteKey = "";

    @NonNull
    @ColumnInfo(name = "vod_id")
    public String vodId = "";

    @NonNull
    @ColumnInfo(name = "vod_name")
    public String vodName = "";

    @NonNull
    @ColumnInfo(name = "vod_pic")
    public String vodPic = "";

    @NonNull
    @ColumnInfo(name = "media_type")
    public String mediaType = "tv";

    @ColumnInfo(name = "tmdb_id")
    public int tmdbId;

    @ColumnInfo(name = "tracked_season")
    public int trackedSeason;

    @ColumnInfo(name = "tracked_episode")
    public int trackedEpisode;

    @ColumnInfo(name = "watched_season")
    public int watchedSeason;

    @ColumnInfo(name = "watched_episode")
    public int watchedEpisode;

    public long position;
    public long duration;

    @NonNull
    @ColumnInfo(name = "official_status")
    public String officialStatus = FollowingMetadataSnapshot.UNKNOWN;

    @ColumnInfo(name = "latest_released_season")
    public int latestReleasedSeason;

    @ColumnInfo(name = "latest_released_episode")
    public int latestReleasedEpisode;

    @ColumnInfo(name = "season_total_episodes")
    public int seasonTotalEpisodes;

    @ColumnInfo(name = "season_released_episodes")
    public int seasonReleasedEpisodes;

    @ColumnInfo(name = "series_total_episodes")
    public int seriesTotalEpisodes;

    @ColumnInfo(name = "next_air_season")
    public int nextAirSeason;

    @ColumnInfo(name = "next_air_episode")
    public int nextAirEpisode;

    @ColumnInfo(name = "next_air_at")
    public long nextAirAt;

    @ColumnInfo(name = "last_observed_episode")
    public int lastObservedEpisode;

    @ColumnInfo(name = "read_watermark_episode")
    public int readWatermarkEpisode;

    @ColumnInfo(name = "last_notified_episode")
    public int lastNotifiedEpisode;

    @ColumnInfo(name = "last_notified_at")
    public long lastNotifiedAt;

    @ColumnInfo(name = "has_update")
    public boolean hasUpdate;

    @ColumnInfo(name = "unwatched_count")
    public int unwatchedCount;

    @ColumnInfo(name = "notify_enabled")
    public boolean notifyEnabled;

    public boolean enabled = true;

    @ColumnInfo(name = "metadata_updated_at")
    public long metadataUpdatedAt;

    @ColumnInfo(name = "last_checked_at")
    public long lastCheckedAt;

    @ColumnInfo(name = "next_check_at")
    public long nextCheckAt;

    @ColumnInfo(name = "failure_count")
    public int failureCount;

    @NonNull
    @ColumnInfo(name = "last_error")
    public String lastError = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public Following copy() {
        Following item = new Following();
        item.identityKey = identityKey;
        item.seriesKey = seriesKey;
        item.cid = cid;
        item.siteKey = siteKey;
        item.vodId = vodId;
        item.vodName = vodName;
        item.vodPic = vodPic;
        item.mediaType = mediaType;
        item.tmdbId = tmdbId;
        item.trackedSeason = trackedSeason;
        item.trackedEpisode = trackedEpisode;
        item.watchedSeason = watchedSeason;
        item.watchedEpisode = watchedEpisode;
        item.position = position;
        item.duration = duration;
        item.officialStatus = officialStatus;
        item.latestReleasedSeason = latestReleasedSeason;
        item.latestReleasedEpisode = latestReleasedEpisode;
        item.seasonTotalEpisodes = seasonTotalEpisodes;
        item.seasonReleasedEpisodes = seasonReleasedEpisodes;
        item.seriesTotalEpisodes = seriesTotalEpisodes;
        item.nextAirSeason = nextAirSeason;
        item.nextAirEpisode = nextAirEpisode;
        item.nextAirAt = nextAirAt;
        item.lastObservedEpisode = lastObservedEpisode;
        item.readWatermarkEpisode = readWatermarkEpisode;
        item.lastNotifiedEpisode = lastNotifiedEpisode;
        item.lastNotifiedAt = lastNotifiedAt;
        item.hasUpdate = hasUpdate;
        item.unwatchedCount = unwatchedCount;
        item.notifyEnabled = notifyEnabled;
        item.enabled = enabled;
        item.metadataUpdatedAt = metadataUpdatedAt;
        item.lastCheckedAt = lastCheckedAt;
        item.nextCheckAt = nextCheckAt;
        item.failureCount = failureCount;
        item.lastError = lastError;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        return item;
    }
}
