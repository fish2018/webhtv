package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import java.util.List;

@Dao
public abstract class TmdbSeasonProgressDao extends BaseDao<TmdbSeasonProgress> {

    @Query("SELECT * FROM TmdbSeasonProgress ORDER BY updatedAt DESC")
    public abstract List<TmdbSeasonProgress> findAll();

    @Query("SELECT * FROM TmdbSeasonProgress WHERE cid = :cid ORDER BY updatedAt DESC")
    public abstract List<TmdbSeasonProgress> findAll(int cid);

    @Query("SELECT * FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId AND seasonNumber = :seasonNumber LIMIT 1")
    public abstract TmdbSeasonProgress find(int cid, String mediaType, int tmdbId, int seasonNumber);

    @Query("SELECT * FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId ORDER BY updatedAt DESC")
    public abstract List<TmdbSeasonProgress> findByMedia(int cid, String mediaType, int tmdbId);

    @Query("SELECT * FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId AND sourceHistoryKey = :sourceHistoryKey ORDER BY updatedAt DESC")
    public abstract List<TmdbSeasonProgress> findBySource(int cid, String mediaType, int tmdbId, String sourceHistoryKey);

    @Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId AND seasonNumber = :seasonNumber")
    public abstract int delete(int cid, String mediaType, int tmdbId, int seasonNumber);

    @Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId")
    public abstract int deleteMedia(int cid, String mediaType, int tmdbId);

    @Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid")
    public abstract int deleteByCid(int cid);

    @Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND updatedAt <= :cutoff")
    public abstract int deleteByCidBeforeOrAt(int cid, long cutoff);

    @Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND sourceHistoryKey = :sourceHistoryKey")
    public abstract int deleteBySource(int cid, String sourceHistoryKey);

    @Query("UPDATE TmdbSeasonProgress SET sourceHistoryKey = :newKey WHERE cid = :cid AND sourceHistoryKey = :oldKey")
    public abstract int replaceSourceHistoryKey(int cid, String oldKey, String newKey);

    @Query("DELETE FROM TmdbSeasonProgress")
    public abstract int deleteAll();
}
