package com.fongmi.android.tv.following;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FollowingMergePolicy {

    private FollowingMergePolicy() {
    }

    public static List<Following> mergeFollowing(List<Following> local, List<Following> remote) {
        Map<String, Following> merged = new LinkedHashMap<>();
        for (Following item : safe(local)) if (valid(item)) merged.put(item.identityKey, item.copy());
        for (Following item : safe(remote)) {
            if (!valid(item)) continue;
            Following current = merged.get(item.identityKey);
            merged.put(item.identityKey, current == null ? item.copy() : mergeOne(current, item));
        }
        return new ArrayList<>(merged.values());
    }

    public static List<FollowingSource> mergeSources(List<FollowingSource> local, List<FollowingSource> remote) {
        Map<String, FollowingSource> merged = new LinkedHashMap<>();
        for (FollowingSource item : safe(local)) if (valid(item)) merged.put(key(item), item.copy());
        for (FollowingSource item : safe(remote)) {
            if (!valid(item)) continue;
            FollowingSource current = merged.get(key(item));
            merged.put(key(item), current == null ? item.copy() : mergeSource(current, item));
        }
        return new ArrayList<>(merged.values());
    }

    static Following mergeOne(Following local, Following remote) {
        Following result = local.copy();
        if (remote.metadataUpdatedAt > local.metadataUpdatedAt) {
            result.vodName = prefer(remote.vodName, local.vodName);
            result.vodPic = prefer(remote.vodPic, local.vodPic);
            result.mediaType = prefer(remote.mediaType, local.mediaType);
            result.tmdbId = remote.tmdbId > 0 ? remote.tmdbId : local.tmdbId;
            result.trackedSeason = Math.max(local.trackedSeason, remote.trackedSeason);
            result.trackedEpisode = Math.max(local.trackedEpisode, remote.trackedEpisode);
            result.officialStatus = prefer(remote.officialStatus, local.officialStatus);
            result.latestReleasedSeason = Math.max(local.latestReleasedSeason, remote.latestReleasedSeason);
            result.latestReleasedEpisode = Math.max(local.latestReleasedEpisode, remote.latestReleasedEpisode);
            result.seasonTotalEpisodes = Math.max(local.seasonTotalEpisodes, remote.seasonTotalEpisodes);
            result.seasonReleasedEpisodes = Math.max(local.seasonReleasedEpisodes, remote.seasonReleasedEpisodes);
            result.seriesTotalEpisodes = Math.max(local.seriesTotalEpisodes, remote.seriesTotalEpisodes);
            result.nextAirSeason = remote.nextAirAt >= local.nextAirAt ? remote.nextAirSeason : local.nextAirSeason;
            result.nextAirEpisode = remote.nextAirAt >= local.nextAirAt ? remote.nextAirEpisode : local.nextAirEpisode;
            result.nextAirAt = Math.max(local.nextAirAt, remote.nextAirAt);
            result.metadataUpdatedAt = remote.metadataUpdatedAt;
        }
        if (remote.updatedAt >= local.updatedAt) {
            result.watchedSeason = remote.watchedSeason;
            result.watchedEpisode = remote.watchedEpisode;
            result.position = remote.position;
            result.duration = remote.duration;
        } else {
            result.watchedEpisode = Math.max(local.watchedEpisode, remote.watchedEpisode);
            if (remote.watchedEpisode > local.watchedEpisode) {
                result.watchedSeason = remote.watchedSeason;
                result.position = remote.position;
                result.duration = remote.duration;
            }
        }
        result.readWatermarkEpisode = Math.max(local.readWatermarkEpisode, remote.readWatermarkEpisode);
        result.lastNotifiedEpisode = Math.max(local.lastNotifiedEpisode, remote.lastNotifiedEpisode);
        result.lastNotifiedAt = Math.max(local.lastNotifiedAt, remote.lastNotifiedAt);
        result.lastObservedEpisode = Math.max(local.lastObservedEpisode, remote.lastObservedEpisode);
        result.enabled = local.enabled && remote.enabled;
        result.notifyEnabled = local.notifyEnabled && remote.notifyEnabled;
        result.createdAt = local.createdAt > 0 ? Math.min(local.createdAt, positive(remote.createdAt)) : remote.createdAt;
        result.updatedAt = Math.max(local.updatedAt, remote.updatedAt);
        FollowingUpdatePolicy.refreshDerived(result, 0);
        return result;
    }

    private static FollowingSource mergeSource(FollowingSource local, FollowingSource remote) {
        FollowingSource result = local.copy();
        result.vodName = prefer(remote.vodName, local.vodName);
        result.vodPic = prefer(remote.vodPic, local.vodPic);
        result.vodFlag = prefer(remote.vodFlag, local.vodFlag);
        result.playableSeason = Math.max(local.playableSeason, remote.playableSeason);
        result.playableEpisode = Math.max(local.playableEpisode, remote.playableEpisode);
        result.playableCount = Math.max(local.playableCount, remote.playableCount);
        if (remote.lastProbeAt >= local.lastProbeAt) result.preferred = remote.preferred;
        result.lastProbeAt = Math.max(local.lastProbeAt, remote.lastProbeAt);
        result.lastError = remote.lastProbeAt >= local.lastProbeAt ? remote.lastError : local.lastError;
        return result;
    }

    private static long positive(long value) {
        return value > 0 ? value : Long.MAX_VALUE;
    }

    private static String prefer(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static boolean valid(Following item) {
        return item != null && item.identityKey != null && !item.identityKey.isBlank();
    }

    private static boolean valid(FollowingSource item) {
        return item != null && item.followingKey != null && !item.followingKey.isBlank();
    }

    private static String key(FollowingSource item) {
        return item.followingKey + "\u0000" + item.cid + "\u0000" + item.siteKey + "\u0000" + item.vodId;
    }

    private static <T> List<T> safe(List<T> items) {
        return items == null ? List.of() : items;
    }
}
