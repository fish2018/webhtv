package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure planning for a single-season history deletion. */
public final class TmdbSeasonDeletePlanner {

    private TmdbSeasonDeletePlanner() {
    }

    public static Plan plan(List<History> histories, List<TmdbSeasonProgress> snapshots,
                            int cid, String mediaType, int tmdbId, int seasonNumber) {
        List<String> deleteRoutes = new ArrayList<>();
        Map<String, TmdbSeasonProgress> restoreRoutes = new LinkedHashMap<>();
        String normalizedType = TmdbSeasonProgress.normalizeMediaType(mediaType);
        for (History history : histories == null ? List.<History>of() : histories) {
            if (history == null || history.getCid() != cid || history.getTmdbId() != tmdbId
                    || !normalizedType.equals(TmdbSeasonProgress.normalizeMediaType(history.getMediaType()))
                    || history.getTmdbSeasonNumber() != seasonNumber
                    || history.getTmdbEpisodeNumber() <= 0 || TextUtils.isEmpty(history.getKey())) continue;
            TmdbSeasonProgress replacement = newestOtherSeason(snapshots, history, cid, normalizedType, tmdbId, seasonNumber);
            if (replacement == null) deleteRoutes.add(history.getKey());
            else restoreRoutes.put(history.getKey(), replacement);
        }
        return new Plan(Collections.unmodifiableList(deleteRoutes),
                Collections.unmodifiableMap(restoreRoutes));
    }

    private static TmdbSeasonProgress newestOtherSeason(List<TmdbSeasonProgress> snapshots,
                                                        History history, int cid, String mediaType,
                                                        int tmdbId, int deletedSeason) {
        TmdbSeasonProgress result = null;
        for (TmdbSeasonProgress snapshot : snapshots == null ? List.<TmdbSeasonProgress>of() : snapshots) {
            if (snapshot == null || snapshot.cid != cid || snapshot.tmdbId != tmdbId
                    || !mediaType.equals(TmdbSeasonProgress.normalizeMediaType(snapshot.mediaType))
                    || snapshot.seasonNumber == deletedSeason || snapshot.episodeNumber <= 0
                    || !TextUtils.equals(snapshot.sourceHistoryKey, history.getKey())) continue;
            if (result == null || snapshot.updatedAt > result.updatedAt) result = snapshot;
        }
        return result;
    }

    public record Plan(List<String> deleteRouteKeys, Map<String, TmdbSeasonProgress> restoreRoutes) {
    }
}
