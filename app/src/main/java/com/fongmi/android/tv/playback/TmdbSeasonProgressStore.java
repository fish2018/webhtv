package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.List;
import java.util.concurrent.Callable;

public final class TmdbSeasonProgressStore {

    private TmdbSeasonProgressStore() {
    }

    public static boolean isEligible(History history) {
        return history != null
                && history.getTmdbId() > 0
                && "tv".equals(TmdbSeasonProgress.normalizeMediaType(history.getMediaType()))
                && history.getTmdbSeasonNumber() >= 0
                && history.getTmdbEpisodeNumber() > 0;
    }

    public static TmdbSeasonProgress fromHistory(History history) {
        if (!isEligible(history)) return null;
        TmdbSeasonProgress progress = TmdbSeasonProgress.of(
                history.getCid(), history.getMediaType(), history.getTmdbId(),
                history.getTmdbSeasonNumber(), history.getTmdbEpisodeNumber(),
                history.getPosition(), history.getDuration(), history.getKey());
        progress.sourceFlag = history.getVodFlag() == null ? "" : history.getVodFlag();
        progress.sourceEpisodeName = history.getVodRemarks();
        progress.sourceEpisodeUrl = history.getEpisodeUrl();
        progress.sourceBindingKey = TextUtils.isEmpty(history.getSourceBindingKey())
                ? sourceBindingKey(history) : history.getSourceBindingKey();
        progress.updatedAt = history.getCreateTime() > 0 ? history.getCreateTime() : System.currentTimeMillis();
        return progress;
    }

    public static boolean write(History history) {
        TmdbSeasonProgress progress = fromHistory(history);
        if (progress == null) return false;
        TmdbSeasonProgress existing = find(progress.cid, progress.mediaType, progress.tmdbId, progress.seasonNumber);
        if (!shouldWrite(existing, progress)) return false;
        AppDatabase.get().getTmdbSeasonProgressDao().insertOrUpdate(progress);
        return true;
    }

    static boolean shouldWrite(TmdbSeasonProgress existing, TmdbSeasonProgress incoming) {
        return incoming != null && (existing == null || incoming.updatedAt >= existing.updatedAt);
    }

    public static TmdbSeasonProgress find(int cid, String mediaType, int tmdbId, int seasonNumber) {
        return AppDatabase.get().getTmdbSeasonProgressDao().find(
                cid, TmdbSeasonProgress.normalizeMediaType(mediaType), tmdbId, seasonNumber);
    }

    public static void reconcile(int cid, String mediaType, int tmdbId, int seasonNumber) {
        String normalizedType = TmdbSeasonProgress.normalizeMediaType(mediaType);
        List<History> histories = AppDatabase.get().getHistoryDao().findByTmdbIdentity(cid, normalizedType, tmdbId);
        History latest = null;
        for (History history : histories) {
            if (!isEligible(history) || history.getTmdbSeasonNumber() != seasonNumber) continue;
            if (latest == null || history.getCreateTime() > latest.getCreateTime()) latest = history;
        }
        if (latest == null) {
            AppDatabase.get().getTmdbSeasonProgressDao().delete(cid, normalizedType, tmdbId, seasonNumber);
        } else {
            write(latest);
        }
    }

    public static boolean restoreAnotherSeason(History history) {
        if (!isEligible(history)) return false;
        String mediaType = TmdbSeasonProgress.normalizeMediaType(history.getMediaType());
        List<TmdbSeasonProgress> snapshots = AppDatabase.get().getTmdbSeasonProgressDao().findBySource(
                history.getCid(), mediaType, history.getTmdbId(), history.getKey());
        for (TmdbSeasonProgress snapshot : snapshots) {
            if (snapshot.seasonNumber == history.getTmdbSeasonNumber() || snapshot.episodeNumber <= 0) continue;
            History restored = history.copy();
            apply(restored, snapshot);
            AppDatabase.get().getHistoryDao().insertOrUpdate(restored);
            return true;
        }
        return false;
    }

    public static void apply(History history, TmdbSeasonProgress progress) {
        if (history == null || progress == null) return;
        history.setTmdbEpisodePosition(progress.seasonNumber, progress.episodeNumber);
        history.setPosition(progress.position);
        history.setDuration(progress.duration);
        history.setVodFlag(progress.sourceFlag);
        history.setVodRemarks(progress.sourceEpisodeName);
        history.setEpisodeUrl(progress.sourceEpisodeUrl);
        history.setSourceBindingKey(progress.sourceBindingKey);
        history.setCreateTime(progress.updatedAt);
    }

    public static void deleteMedia(int cid, String mediaType, int tmdbId) {
        AppDatabase.get().getTmdbSeasonProgressDao().deleteMedia(
                cid, TmdbSeasonProgress.normalizeMediaType(mediaType), tmdbId);
    }

    public static synchronized <T> T runInTransaction(Callable<T> action) {
        return AppDatabase.get().runInTransaction(action);
    }

    private static String sourceBindingKey(History history) {
        String siteKey = history.getSiteKey();
        String vodId = history.getVodId();
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return history.getKey();
        String key = siteKey + AppDatabase.SYMBOL + vodId;
        return TextUtils.isEmpty(history.getVodFlag()) ? key : key + AppDatabase.SYMBOL + history.getVodFlag();
    }
}
