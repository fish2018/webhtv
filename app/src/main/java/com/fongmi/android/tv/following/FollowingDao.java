package com.fongmi.android.tv.following;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public abstract class FollowingDao {

    @Upsert
    public abstract void insertOrUpdate(Following item);

    @Upsert
    public abstract void insertOrUpdate(List<Following> items);

    @Query("SELECT * FROM following ORDER BY has_update DESC, updated_at DESC")
    public abstract List<Following> findAll();

    @Query("SELECT * FROM following WHERE enabled = 1 AND next_check_at <= :now ORDER BY next_check_at ASC LIMIT :limit")
    public abstract List<Following> findDue(long now, int limit);

    @Query("SELECT * FROM following WHERE identity_key = :identityKey LIMIT 1")
    public abstract Following find(String identityKey);

    @Query("SELECT * FROM following WHERE tmdb_id = :tmdbId AND media_type = :mediaType AND tracked_season = :season LIMIT 1")
    public abstract Following findByTmdb(int tmdbId, String mediaType, int season);

    @Query("SELECT * FROM following WHERE cid = :cid AND site_key = :siteKey AND vod_id = :vodId AND tracked_season = :season LIMIT 1")
    public abstract Following findBySource(int cid, String siteKey, String vodId, int season);

    @Query("SELECT * FROM following WHERE cid = :cid AND site_key = :siteKey AND vod_id = :vodId AND tmdb_id = 0 ORDER BY tracked_season ASC")
    public abstract List<Following> findUnmatchedBySource(int cid, String siteKey, String vodId);

    @Query("SELECT COUNT(*) FROM following WHERE enabled = 1 AND has_update = 1")
    public abstract int unreadCount();

    @Query("UPDATE following SET read_watermark_episode = MAX(read_watermark_episode, :episode), has_update = CASE WHEN latest_released_episode > MAX(read_watermark_episode, :episode) THEN 1 ELSE 0 END, updated_at = :now WHERE identity_key = :identityKey")
    public abstract int markRead(String identityKey, int episode, long now);

    @Query("UPDATE following SET notify_enabled = :enabled, updated_at = :now WHERE identity_key = :identityKey")
    public abstract int setNotifyEnabled(String identityKey, boolean enabled, long now);

    @Query("UPDATE following SET enabled = :enabled, updated_at = :now WHERE identity_key = :identityKey")
    public abstract int setEnabled(String identityKey, boolean enabled, long now);

    @Query("DELETE FROM following WHERE identity_key = :identityKey")
    public abstract int delete(String identityKey);

    @Query("DELETE FROM following")
    public abstract int deleteAll();
}
