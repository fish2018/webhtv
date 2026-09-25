package com.fongmi.android.tv.following;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
        tableName = "following_source",
        primaryKeys = {"following_key", "cid", "site_key", "vod_id"},
        indices = {
                @Index(value = {"following_key", "preferred"}),
                @Index(value = {"cid", "site_key", "vod_id"})
        })
public class FollowingSource {

    @NonNull
    @ColumnInfo(name = "following_key")
    public String followingKey = "";

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
    @ColumnInfo(name = "vod_flag")
    public String vodFlag = "";

    @ColumnInfo(name = "playable_season")
    public int playableSeason;

    @ColumnInfo(name = "playable_episode")
    public int playableEpisode;

    @ColumnInfo(name = "playable_count")
    public int playableCount;

    public boolean preferred;

    @ColumnInfo(name = "last_probe_at")
    public long lastProbeAt;

    @NonNull
    @ColumnInfo(name = "last_error")
    public String lastError = "";

    public FollowingSource copy() {
        FollowingSource item = new FollowingSource();
        item.followingKey = followingKey;
        item.cid = cid;
        item.siteKey = siteKey;
        item.vodId = vodId;
        item.vodName = vodName;
        item.vodPic = vodPic;
        item.vodFlag = vodFlag;
        item.playableSeason = playableSeason;
        item.playableEpisode = playableEpisode;
        item.playableCount = playableCount;
        item.preferred = preferred;
        item.lastProbeAt = lastProbeAt;
        item.lastError = lastError;
        return item;
    }
}
