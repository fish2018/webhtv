package com.fongmi.android.tv.following;

public final class FollowingUpdatePolicy {

    private FollowingUpdatePolicy() {
    }

    public static int releasedEpisode(Following item) {
        if (item == null) return 0;
        int released = item.latestReleasedSeason == item.trackedSeason
                ? item.latestReleasedEpisode : item.seasonReleasedEpisodes;
        return Math.max(0, released);
    }

    public static void initializeNew(Following item, int releasedEpisode, long now) {
        if (item == null) return;
        item.readWatermarkEpisode = Math.max(0, releasedEpisode);
        item.lastNotifiedEpisode = Math.max(0, releasedEpisode);
        item.lastNotifiedAt = now;
        item.lastObservedEpisode = Math.max(0, releasedEpisode);
        item.createdAt = item.createdAt > 0 ? item.createdAt : now;
        item.updatedAt = now;
        refreshDerived(item, now);
    }

    public static boolean applyMetadata(Following item, FollowingMetadataSnapshot snapshot, long now) {
        if (item == null || snapshot == null) return false;
        boolean firstMetadata = item.metadataUpdatedAt <= 0
                && item.readWatermarkEpisode <= 0
                && item.lastNotifiedEpisode <= 0;
        int before = releasedEpisode(item);
        int previousLatestSeason = Math.max(0, item.latestReleasedSeason);
        int previousLatestEpisode = Math.max(0, item.latestReleasedEpisode);
        item.officialStatus = FollowingMetadataSnapshot.normalizeStatus(snapshot.status);
        int latestSeason = Math.max(0, snapshot.latestReleasedSeason);
        int latestEpisode = Math.max(0, snapshot.latestReleasedEpisode);
        if (latestSeason > previousLatestSeason) {
            item.latestReleasedSeason = latestSeason;
            item.latestReleasedEpisode = latestEpisode;
        } else if (latestSeason == previousLatestSeason) {
            item.latestReleasedSeason = previousLatestSeason;
            item.latestReleasedEpisode = Math.max(previousLatestEpisode, latestEpisode);
        } else {
            item.latestReleasedSeason = previousLatestSeason;
            item.latestReleasedEpisode = previousLatestEpisode;
        }
        item.seasonTotalEpisodes = Math.max(0, snapshot.seasonTotalEpisodes);
        item.seasonReleasedEpisodes = Math.max(Math.max(0, item.seasonReleasedEpisodes), Math.max(0, snapshot.seasonReleasedEpisodes));
        item.seriesTotalEpisodes = Math.max(0, snapshot.seriesTotalEpisodes);
        item.nextAirSeason = Math.max(0, snapshot.nextAirSeason);
        item.nextAirEpisode = Math.max(0, snapshot.nextAirEpisode);
        item.nextAirAt = Math.max(0, snapshot.nextAirAt);
        item.metadataUpdatedAt = snapshot.fetchedAt > 0 ? snapshot.fetchedAt : now;
        item.lastObservedEpisode = Math.max(item.lastObservedEpisode, releasedEpisode(item));
        item.failureCount = 0;
        item.lastError = "";
        item.lastCheckedAt = now;
        if (firstMetadata) {
            item.readWatermarkEpisode = Math.max(item.readWatermarkEpisode, releasedEpisode(item));
            item.lastNotifiedEpisode = Math.max(item.lastNotifiedEpisode, releasedEpisode(item));
            item.lastNotifiedAt = now;
        }
        item.updatedAt = now;
        refreshDerived(item, now);
        return releasedEpisode(item) > before;
    }

    public static void refreshDerived(Following item, long now) {
        if (item == null) return;
        int released = releasedEpisode(item);
        int watched = Math.max(0, item.watchedEpisode);
        int read = Math.max(0, item.readWatermarkEpisode);
        item.unwatchedCount = Math.max(0, released - watched);
        item.hasUpdate = released > Math.max(watched, read);
        if (now > 0) item.updatedAt = now;
    }

    public static boolean shouldNotify(Following item) {
        if (item == null || !item.enabled || !item.notifyEnabled) return false;
        int released = releasedEpisode(item);
        return released > Math.max(Math.max(0, item.readWatermarkEpisode), Math.max(0, item.lastNotifiedEpisode));
    }

    public static void markNotified(Following item, long now) {
        if (item == null) return;
        item.lastNotifiedEpisode = releasedEpisode(item);
        item.lastNotifiedAt = now;
        item.updatedAt = now;
    }

    public static void markRead(Following item, long now) {
        if (item == null) return;
        item.readWatermarkEpisode = Math.max(item.readWatermarkEpisode, releasedEpisode(item));
        refreshDerived(item, now);
    }

    public static void markFailure(Following item, Throwable error, long now) {
        if (item == null) return;
        item.failureCount = Math.max(0, item.failureCount) + 1;
        item.lastError = shortError(error);
        item.lastCheckedAt = now;
        item.nextCheckAt = FollowingSchedulePolicy.backoffAt(now, item.failureCount);
        item.updatedAt = now;
    }

    private static String shortError(Throwable error) {
        String message = error == null || error.getMessage() == null ? "检查失败" : error.getMessage().trim();
        return message.length() <= 160 ? message : message.substring(0, 160);
    }
}
