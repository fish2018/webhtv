package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.History;

import java.util.List;

@Dao
public abstract class HistoryDao extends BaseDao<History> {

    @Query("SELECT * FROM History")
    public abstract List<History> findAll();

    @Query("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC")
    public abstract List<History> find(int cid);

    @Query("SELECT * FROM History WHERE cid = :cid")
    public abstract List<History> findAll(int cid);

    @Query("SELECT * FROM History WHERE cid = :cid AND `key` = :key")
    public abstract History find(int cid, String key);

    @Query("SELECT * FROM History WHERE cid = :cid AND vodName = :vodName ORDER BY createTime DESC")
    public abstract List<History> findByName(int cid, String vodName);

    @Query("SELECT * FROM History WHERE cid = :cid AND `key` LIKE :keyPrefix || '%' ORDER BY createTime DESC")
    public abstract List<History> findByKeyPrefix(int cid, String keyPrefix);

    @Query("SELECT * FROM History WHERE cid = :cid AND tmdbId = :tmdbId AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbId(int cid, int tmdbId);

    @Query("SELECT * FROM History WHERE cid = :cid AND tmdbId = :tmdbId AND LOWER(TRIM(mediaType)) = :mediaType AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbIdentity(int cid, String mediaType, int tmdbId);

    @Query("SELECT * FROM History WHERE tmdbId = :tmdbId AND LOWER(TRIM(mediaType)) = :mediaType AND tmdbId > 0 ORDER BY createTime DESC")
    public abstract List<History> findByTmdbIdentity(String mediaType, int tmdbId);

    @Query("DELETE FROM History WHERE cid = :cid AND `key` = :key")
    public abstract int delete(int cid, String key);

    @Query("DELETE FROM History WHERE cid = :cid AND `key` LIKE :keyPrefix || '%'")
    public abstract int deleteByKeyPrefix(int cid, String keyPrefix);

    @Query("DELETE FROM History WHERE cid = :cid")
    public abstract int delete(int cid);

    @Query("DELETE FROM History")
    public abstract int delete();
}
