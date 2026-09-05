package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TmdbSeasonSourceAggregator {

    private TmdbSeasonSourceAggregator() {
    }

    public static List<History> collect(List<History> histories, int cid, String mediaType,
                                        int tmdbId, int seasonNumber, String currentRoute) {
        if (histories == null || histories.isEmpty() || tmdbId <= 0 || seasonNumber < 0) return List.of();
        String normalizedType = TmdbSeasonProgress.normalizeMediaType(mediaType);
        if (!"tv".equals(normalizedType)) return List.of();
        String excludedRoute = routeKey(currentRoute);
        Map<String, History> routes = new LinkedHashMap<>();
        for (History history : histories) {
            if (history == null || history.getCid() != cid || history.getTmdbId() != tmdbId) continue;
            if (!normalizedType.equals(TmdbSeasonProgress.normalizeMediaType(history.getMediaType()))) continue;
            if (history.getTmdbSeasonNumber() != seasonNumber || history.getTmdbEpisodeNumber() <= 0) continue;
            String route = routeKey(history);
            if (route.isEmpty() || route.equals(excludedRoute)) continue;
            History previous = routes.get(route);
            if (previous == null || history.getCreateTime() >= previous.getCreateTime()) routes.put(route, history);
        }
        List<History> result = new ArrayList<>(routes.values());
        result.sort((first, second) -> Long.compare(second.getCreateTime(), first.getCreateTime()));
        return result;
    }

    public static List<History> collect(List<History> histories, List<TmdbSeasonProgress> snapshots,
                                        int cid, String mediaType, int tmdbId, int seasonNumber,
                                        String currentRoute) {
        return collect(histories, snapshots, null, cid, mediaType, tmdbId, seasonNumber, currentRoute);
    }

    public static List<History> collect(List<History> histories, List<TmdbSeasonProgress> snapshots,
                                        TmdbSeasonMatchCache seasonCache, int cid, String mediaType,
                                        int tmdbId, int seasonNumber, String currentRoute) {
        List<History> projected = histories == null ? new ArrayList<>() : new ArrayList<>(histories);
        Set<String> snapshotRoutes = new HashSet<>();
        if (snapshots != null && histories != null) {
            Map<String, History> byKey = new LinkedHashMap<>();
            for (History history : histories) if (history != null) byKey.put(history.getKey(), history);
            for (TmdbSeasonProgress snapshot : snapshots) {
                if (snapshot == null || snapshot.cid != cid || snapshot.tmdbId != tmdbId
                        || snapshot.seasonNumber != seasonNumber || snapshot.episodeNumber <= 0
                        || !TmdbSeasonProgress.normalizeMediaType(mediaType).equals(
                        TmdbSeasonProgress.normalizeMediaType(snapshot.mediaType))) continue;
                History source = byKey.get(snapshot.sourceHistoryKey);
                if (source == null) continue;
                History seasonRoute = source.copy();
                seasonRoute.setTmdbEpisodePosition(snapshot.seasonNumber, snapshot.episodeNumber);
                seasonRoute.setPosition(snapshot.position);
                seasonRoute.setDuration(snapshot.duration);
                seasonRoute.setVodFlag(snapshot.sourceFlag);
                seasonRoute.setVodRemarks(snapshot.sourceEpisodeName);
                seasonRoute.setEpisodeUrl(snapshot.sourceEpisodeUrl);
                seasonRoute.setSourceBindingKey(snapshot.sourceBindingKey);
                seasonRoute.setCreateTime(snapshot.updatedAt);
                projected.add(seasonRoute);
                snapshotRoutes.add(snapshot.sourceHistoryKey);
            }
        }
        if (seasonCache != null && histories != null) {
            Map<String, List<TmdbSeasonMatchCache.RouteBinding>> bindingsByRoute =
                    seasonCache.indexRouteBindings(tmdbId, mediaType, seasonNumber);
            for (History history : histories) {
                if (history == null || history.getCid() != cid || history.getTmdbId() != tmdbId
                        || snapshotRoutes.contains(history.getKey())
                        || !TmdbSeasonProgress.normalizeMediaType(mediaType).equals(
                        TmdbSeasonProgress.normalizeMediaType(history.getMediaType()))) continue;
                List<TmdbSeasonMatchCache.RouteBinding> bindings = bindingsByRoute.getOrDefault(
                        TmdbSeasonMatchCache.routeIdentity(history.getSiteKey(), history.getVodId()), List.of());
                for (TmdbSeasonMatchCache.RouteBinding binding : bindings) {
                    History seasonRoute = history.copy();
                    seasonRoute.setTmdbEpisodePosition(seasonNumber, 1);
                    seasonRoute.setVodFlag(binding.getSourceFlag());
                    seasonRoute.setVodRemarks("");
                    seasonRoute.setEpisodeUrl("");
                    seasonRoute.setSourceBindingKey(binding.getFlagKey());
                    seasonRoute.setCreateTime(Math.max(history.getCreateTime(), binding.getUpdatedAt()));
                    projected.add(seasonRoute);
                }
            }
        }
        return collect(projected, cid, mediaType, tmdbId, seasonNumber, currentRoute);
    }

    private static String routeKey(History history) {
        if (history == null || history.getSiteKey().isEmpty() || history.getVodId().isEmpty()) return "";
        return history.getSiteKey() + AppDatabase.SYMBOL + history.getVodId();
    }

    private static String routeKey(String key) {
        if (key == null || key.isEmpty()) return "";
        int first = key.indexOf(AppDatabase.SYMBOL);
        if (first < 0) return key;
        int second = key.indexOf(AppDatabase.SYMBOL, first + AppDatabase.SYMBOL.length());
        return second < 0 ? key : key.substring(0, second);
    }
}
