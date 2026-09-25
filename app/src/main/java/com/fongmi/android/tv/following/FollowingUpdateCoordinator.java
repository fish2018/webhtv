package com.fongmi.android.tv.following;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.following.FollowingSourceProbe.FollowingSourceSnapshot;

import java.io.IOException;
import java.util.List;

public class FollowingUpdateCoordinator {

    private final FollowingMetadataClient metadataClient;
    private final FollowingSourceProbe sourceProbe;

    public FollowingUpdateCoordinator() {
        this(new FollowingMetadataClient(), new FollowingSourceProbe());
    }

    FollowingUpdateCoordinator(FollowingMetadataClient metadataClient, FollowingSourceProbe sourceProbe) {
        this.metadataClient = metadataClient;
        this.sourceProbe = sourceProbe;
    }

    public int checkDue(long now) throws IOException {
        if (!FollowingSettings.isEnabled()) return 0;
        boolean foreground = App.isForeground();
        int limit = foreground ? FollowingSchedulePolicy.FOREGROUND_BATCH_SIZE : FollowingSchedulePolicy.BACKGROUND_BATCH_SIZE;
        List<Following> due = FollowingStore.database().getFollowingDao().findDue(now, limit);
        int checked = 0;
        for (Following item : due) {
            if (check(item, false, foreground)) checked++;
        }
        return checked;
    }

    public boolean checkNow(String identityKey, boolean manual) {
        Following item = FollowingStore.find(identityKey);
        return item != null && check(item, manual);
    }

    boolean check(Following item, boolean manual) {
        return check(item, manual, App.isForeground());
    }

    boolean check(Following item, boolean manual, boolean foreground) {
        long now = System.currentTimeMillis();
        try {
            FollowingSource source = FollowingStore.preferredSource(item.identityKey);
            FollowingMetadataSnapshot metadata = null;
            boolean metadataChanged = false;
            boolean sourceAlreadyProbed = false;
            Throwable metadataError = null;
            if (item.tmdbId > 0) {
                try {
                    metadata = metadataClient.fetch(item, manual);
                } catch (Throwable error) {
                    // TMDB is the preferred official provider, but an unavailable/rate-limited
                    // provider must not make the whole record fail when the bound source is
                    // still usable. Preserve the last official snapshot and probe the source.
                    metadataError = error;
                }
            }
            if (metadata != null) {
                metadataChanged = FollowingUpdatePolicy.applyMetadata(item, metadata, now);
            } else {
                FollowingSourceSnapshot sourceSnapshot = probeBestEffort(item, source, manual, false);
                sourceAlreadyProbed = sourceSnapshot != null;
                if (sourceSnapshot == null || sourceSnapshot.source == null) {
                    Throwable failure = metadataError != null
                            ? metadataError : new IllegalStateException("来源检查失败");
                    FollowingUpdatePolicy.markFailure(item, failure, now);
                    FollowingStore.update(item);
                    FollowingScheduler.scheduleNext(App.get(), item);
                    return false;
                }
                source = sourceSnapshot.source;
            }
            FollowingStore.reconcile(item);
            FollowingUpdatePolicy.refreshDerived(item, now);
            if (!sourceAlreadyProbed && FollowingSettings.shouldProbe(manual, metadataChanged, source)) {
                FollowingSourceSnapshot probed = probeBestEffort(item, source, manual, false);
                if (probed != null) source = probed.source;
            }
            item.lastCheckedAt = now;
            item.failureCount = 0;
            item.lastError = metadataError == null ? "" : "TMDB不可用，已回退原站";
            item.nextCheckAt = FollowingSchedulePolicy.nextCheckAt(now, item.officialStatus, item.nextAirAt, foreground);
            item.updatedAt = now;
            FollowingStore.update(item);
            if (source != null) FollowingStore.updateSource(source);
            if (FollowingNotifier.notifyUpdate(item, source)) FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return true;
        } catch (Throwable error) {
            FollowingUpdatePolicy.markFailure(item, error, now);
            FollowingStore.update(item);
            FollowingScheduler.scheduleNext(App.get(), item);
            return false;
        }
    }

    private FollowingSourceSnapshot probeBestEffort(Following item, FollowingSource source, boolean manual, boolean required) {
        if (source == null) source = fallbackSource(item);
        if (source == null || !FollowingSettings.shouldProbe(manual, false, source)) return null;
        try {
            FollowingSourceSnapshot result = sourceProbe.probe(item, source);
            FollowingStore.updateSource(source);
            return result;
        } catch (Throwable error) {
            source.lastError = error.getMessage() == null ? "来源检查失败" : error.getMessage();
            source.lastProbeAt = System.currentTimeMillis();
            FollowingStore.updateSource(source);
            if (required) throw error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error);
            return null;
        }
    }

    private static FollowingSource fallbackSource(Following item) {
        if (item == null || item.siteKey == null || item.siteKey.isBlank() || item.vodId == null || item.vodId.isBlank()) return null;
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.cid = item.cid;
        source.siteKey = item.siteKey;
        source.vodId = item.vodId;
        source.vodName = item.vodName;
        source.vodPic = item.vodPic;
        source.preferred = true;
        return source;
    }
}
