package com.fongmi.android.tv.following;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.utils.Task;

import java.util.function.Consumer;
import java.util.Collection;

/** Main-thread-safe bridge between playback state and the independent following database. */
public final class FollowingPlaybackBridge {

    public interface SaveCallback {
        void onResult(Following item, Throwable error);
    }

    private static volatile int cachedUnreadCount;

    private FollowingPlaybackBridge() {
    }

    public static int cachedUnreadCount() {
        return cachedUnreadCount;
    }

    public static void refreshUnreadCountAsync(Consumer<Integer> callback) {
        Task.execute(() -> {
            int unread = 0;
            try {
                unread = FollowingStore.unreadCount();
            } catch (Throwable ignored) {
            }
            cachedUnreadCount = unread;
            final int result = unread;
            if (callback != null) App.post(() -> callback.accept(result));
        });
    }

    public static boolean isEligible(History history) {
        if (history == null) return false;
        String mediaType = FollowingIdentity.normalizeMediaType(history.getMediaType());
        if ("movie".equals(mediaType)) return false;
        if (history.getTmdbId() > 0) return "tv".equals(mediaType);
        return !TextUtils.isEmpty(siteKey(history)) && !TextUtils.isEmpty(vodId(history));
    }

    public static int trackedSeason(History history, int fallbackSeason) {
        if (history == null) return Math.max(0, fallbackSeason);
        int season = history.getTmdbEpisodeNumber() > 0 ? history.getTmdbSeasonNumber() : fallbackSeason;
        return Math.max(0, season);
    }

    public static String identityKey(History history, int fallbackSeason) {
        if (history == null) return "";
        int season = trackedSeason(history, fallbackSeason);
        if (history.getTmdbId() > 0) {
            String mediaType = FollowingIdentity.normalizeMediaType(history.getMediaType());
            return FollowingIdentity.identityKey(new TmdbItem(history.getTmdbId(), mediaType,
                    safe(history.getVodName()), "", "", safe(history.getVodPic()), ""), season);
        }
        return FollowingIdentity.identityKey(history.getCid(), siteKey(history), vodId(history), season);
    }

    public static Following build(History history, int fallbackSeason) {
        if (history == null) return null;
        String identityKey = identityKey(history, fallbackSeason);
        if (TextUtils.isEmpty(identityKey)) return null;
        int season = trackedSeason(history, fallbackSeason);
        String mediaType = FollowingIdentity.normalizeMediaType(history.getMediaType());
        long now = System.currentTimeMillis();
        Following item = new Following();
        item.identityKey = identityKey;
        item.seriesKey = history.getTmdbId() > 0
                ? FollowingIdentity.seriesKey(new TmdbItem(history.getTmdbId(), mediaType,
                        safe(history.getVodName()), "", "", safe(history.getVodPic()), ""))
                : FollowingIdentity.seriesKey(history.getCid(), siteKey(history), vodId(history));
        item.cid = history.getCid();
        item.siteKey = FollowingIdentity.normalize(siteKey(history));
        item.vodId = FollowingIdentity.normalize(vodId(history));
        item.vodName = safe(history.getVodName());
        item.vodPic = safe(history.getVodPic());
        item.mediaType = mediaType;
        item.tmdbId = history.getTmdbId();
        item.trackedSeason = season;
        item.trackedEpisode = Math.max(0, history.getTmdbEpisodeNumber());
        item.watchedSeason = Math.max(0, history.getTmdbSeasonNumber());
        item.watchedEpisode = Math.max(0, history.getTmdbEpisodeNumber());
        item.position = Math.max(0, history.getPosition());
        item.duration = Math.max(0, history.getDuration());
        item.notifyEnabled = FollowingSettings.isNotificationsEnabled();
        item.enabled = true;
        item.createdAt = now;
        item.updatedAt = now;
        item.nextCheckAt = now;
        FollowingUpdatePolicy.initializeNew(item, 0, now);
        item.nextCheckAt = now;
        return item;
    }

    public static FollowingSource source(Following item, History history) {
        if (item == null || history == null) return null;
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.cid = item.cid;
        source.siteKey = item.siteKey;
        source.vodId = item.vodId;
        source.vodName = item.vodName;
        source.vodPic = item.vodPic;
        source.vodFlag = safe(history.getVodFlag());
        source.playableSeason = item.trackedSeason;
        source.preferred = true;
        return source;
    }

    public static void findAsync(String identityKey, Consumer<Following> callback) {
        if (TextUtils.isEmpty(identityKey)) {
            if (callback != null) App.post(() -> callback.accept(null));
            return;
        }
        Task.execute(() -> {
            Following item = null;
            try {
                item = FollowingStore.find(identityKey);
            } catch (Throwable ignored) {
            }
            final Following result = item;
            if (callback != null) App.post(() -> callback.accept(result));
        });
    }

    public static void resolveTmdbAsync(TmdbItem tmdb, int season, int cid, String siteKey, String vodId,
                                        FollowingMetadataSnapshot snapshot, Consumer<Following> callback) {
        Task.execute(() -> {
            Following item = null;
            try {
                item = FollowingStore.resolveTmdb(tmdb, season, cid, siteKey, vodId, snapshot);
            } catch (Throwable ignored) {
            }
            final Following result = item;
            if (callback != null) App.post(() -> callback.accept(result));
        });
    }

    public static void addAsync(Following item, FollowingSource source, SaveCallback callback) {
        if (item == null || TextUtils.isEmpty(item.identityKey)) {
            if (callback != null) App.post(() -> callback.onResult(null, new IllegalArgumentException("following identity is empty")));
            return;
        }
        Task.execute(() -> {
            try {
                Following existing = FollowingStore.find(item.identityKey);
                if (existing == null) {
                    FollowingStore.saveNew(item, source);
                    existing = item;
                }
                final Following result = existing;
                if (callback != null) App.post(() -> callback.onResult(result, null));
            } catch (Throwable error) {
                if (callback != null) App.post(() -> callback.onResult(null, error));
            }
        });
    }

    public static void deleteAsync(String identityKey, Consumer<Throwable> callback) {
        if (TextUtils.isEmpty(identityKey)) {
            if (callback != null) App.post(() -> callback.accept(null));
            return;
        }
        Task.execute(() -> {
            Throwable error = null;
            try {
                FollowingStore.delete(identityKey);
            } catch (Throwable throwable) {
                error = throwable;
            }
            final Throwable result = error;
            if (callback != null) App.post(() -> callback.accept(result));
        });
    }

    public static void markReadAsync(String identityKey, Consumer<Throwable> callback) {
        writeAsync(() -> FollowingStore.markRead(identityKey), callback);
    }

    public static void markReadAllAsync(Collection<String> identityKeys, Consumer<Throwable> callback) {
        if (identityKeys == null || identityKeys.isEmpty()) {
            if (callback != null) App.post(() -> callback.accept(null));
            return;
        }
        writeAsync(() -> FollowingStore.markReadAll(identityKeys), callback);
    }

    public static void setNotifyEnabledAsync(String identityKey, boolean enabled, Consumer<Throwable> callback) {
        writeAsync(() -> FollowingStore.setNotifyEnabled(identityKey, enabled), callback);
    }

    private static void writeAsync(Runnable action, Consumer<Throwable> callback) {
        if (action == null) {
            if (callback != null) App.post(() -> callback.accept(null));
            return;
        }
        Task.execute(() -> {
            Throwable error = null;
            try {
                action.run();
            } catch (Throwable throwable) {
                error = throwable;
            }
            final Throwable result = error;
            if (callback != null) App.post(() -> callback.accept(result));
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String siteKey(History history) {
        try {
            return safe(history.getSiteKey());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String vodId(History history) {
        try {
            return safe(history.getVodId());
        } catch (Throwable ignored) {
            return "";
        }
    }
}
