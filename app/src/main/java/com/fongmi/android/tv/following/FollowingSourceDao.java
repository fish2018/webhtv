package com.fongmi.android.tv.following;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public abstract class FollowingSourceDao {

    @Upsert
    public abstract void insertOrUpdate(FollowingSource item);

    @Upsert
    public abstract void insertOrUpdate(List<FollowingSource> items);

    @Query("SELECT * FROM following_source ORDER BY preferred DESC, last_probe_at DESC")
    public abstract List<FollowingSource> findAll();

    @Query("SELECT * FROM following_source WHERE following_key = :followingKey ORDER BY preferred DESC, last_probe_at DESC")
    public abstract List<FollowingSource> findForFollowing(String followingKey);

    @Query("SELECT * FROM following_source WHERE following_key = :followingKey ORDER BY preferred DESC, last_probe_at DESC LIMIT 1")
    public abstract FollowingSource findPreferred(String followingKey);

    @Query("DELETE FROM following_source WHERE following_key = :followingKey")
    public abstract int deleteForFollowing(String followingKey);

    @Query("DELETE FROM following_source")
    public abstract int deleteAll();
}
