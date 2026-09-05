package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybackProgressWriter {

    enum RemoteSeasonUpsertMode {
        SKIP,
        SNAPSHOT_ONLY,
        ROUTE_AND_SNAPSHOT
    }

    private PlaybackProgressWriter() {
    }

    // Temporary or unsaved VOD configs legitimately persist local History rows with cid 0.
    // Only a deliberate user deletion may target those rows; API and sync deletes still
    // require a stable, positive config mapping.
    static boolean canDeleteCid(int cid, boolean userInitiated) {
        return cid > 0 || (userInitiated && cid == 0);
    }

    static boolean requiresSourceIdentity(PlaybackProgressDeleteInput input) {
        return input != null && !input.isAllScope() && !input.isSiteScope() && !input.isSeasonScope();
    }

    public static PlaybackProgressApplyResult applyFromLocalApi(PlaybackProgressInput input) {
        if (!ViewingRecordSyncStore.isEnabled()) return PlaybackProgressApplyResult.failed(input, "观影记录同步未开启");
        if (!ViewingRecordSyncStore.isLocalWriteEnabled()) return PlaybackProgressApplyResult.failed(input, "本机 API 修改未开启");
        return applyInternal(input, PlaybackDeleteTombstoneStore.snapshot(), false);
    }

    public static PlaybackProgressBatchResult applyFromLocalApi(List<PlaybackProgressInput> inputs) {
        PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
        if (!ViewingRecordSyncStore.isEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressInput) null, "观影记录同步未开启"));
            return batch;
        }
        if (!ViewingRecordSyncStore.isLocalWriteEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressInput) null, "本机 API 修改未开启"));
            return batch;
        }
        return applyInternal(inputs, PlaybackDeleteTombstoneStore.snapshot(), false);
    }

    public static PlaybackProgressBatchResult deleteFromLocalApi(List<PlaybackProgressDeleteInput> inputs) {
        PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
        if (!ViewingRecordSyncStore.isEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "观影记录同步未开启"));
            return batch;
        }
        if (!ViewingRecordSyncStore.isLocalWriteEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "本机 API 修改未开启"));
            return batch;
        }
        if (inputs == null || inputs.isEmpty()) return batch;
        for (PlaybackProgressDeleteInput input : inputs) batch.add(deleteInternal(input, null, false, false));
        return batch;
    }

    /** Called by the history UI for a deliberate user deletion. */
    public static PlaybackProgressApplyResult deleteFromUser(History history) {
        if (history == null) return PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "记录不存在");
        return deleteInternal(createDeleteInput(history), null, false, true);
    }

    static PlaybackProgressDeleteInput createDeleteInput(History history) {
        PlaybackProgressDeleteInput input = new PlaybackProgressDeleteInput();
        if (history == null) return input;
        input.cid = history.getCid();
        input.historyKey = history.getKey();
        input.siteKey = history.getSiteKey();
        input.vodId = history.getVodId();
        input.episodeName = history.getVodRemarks();
        if (TmdbSeasonProgressStore.isEligible(history)) {
            input.scope = "season";
            input.mediaType = history.getMediaType();
            input.tmdbId = history.getTmdbId();
            input.seasonNumber = history.getTmdbSeasonNumber();
        }
        input.deletedAt = System.currentTimeMillis();
        return input.normalize();
    }

    /** Called by the history UI for a deliberate clear-all operation. */
    public static PlaybackProgressApplyResult deleteAllFromUser(int cid) {
        PlaybackProgressDeleteInput input = new PlaybackProgressDeleteInput();
        input.cid = cid;
        input.scope = "all";
        input.confirm = true;
        input.deletedAt = System.currentTimeMillis();
        return deleteInternal(input, null, false, true);
    }

    public static PlaybackProgressBatchResult applyFromRemoteSync(List<PlaybackProgressInput> inputs, RemoteSyncConfig config) {
        PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
        if (!ViewingRecordSyncStore.isEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressInput) null, "观影记录同步未开启"));
            return batch;
        }
        List<PlaybackDeleteTombstone> tombstones = PlaybackDeleteTombstoneStore.snapshot();
        if (inputs != null) for (PlaybackProgressInput input : inputs) {
            input = input == null ? null : input.normalize();
            if (input == null) {
                batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressInput) null, "请求体不能为空"));
            } else if (config != null && !config.matchesSite(input.siteKey)) {
                batch.add(PlaybackProgressApplyResult.skipped(input, input.targetHistoryKey(targetCid(input)), "站点不匹配", 0));
            } else if (!TextUtils.isEmpty(input.configKey) && targetCid(input) <= 0) {
                batch.add(PlaybackProgressApplyResult.skipped(input, input.historyKey, "接口不匹配", 0));
            } else {
                batch.add(applyInternal(input, tombstones, true));
            }
        }
        return batch;
    }

    /** Applies delete operations from a delete-aware remote response. */
    public static PlaybackProgressBatchResult deleteFromRemoteSync(List<PlaybackProgressDeleteInput> inputs, RemoteSyncConfig config) {
        PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
        if (!ViewingRecordSyncStore.isEnabled()) {
            batch.add(PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "观影记录同步未开启"));
            return batch;
        }
        if (inputs != null) for (PlaybackProgressDeleteInput input : inputs) {
            batch.add(deleteInternal(input, config, true, false));
        }
        return batch;
    }

    private static PlaybackProgressBatchResult applyInternal(List<PlaybackProgressInput> inputs,
                                                              List<PlaybackDeleteTombstone> tombstones,
                                                              boolean remote) {
        PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
        if (inputs == null || inputs.isEmpty()) return batch;
        for (PlaybackProgressInput input : inputs) batch.add(applyInternal(input, tombstones, remote));
        return batch;
    }

    private static synchronized PlaybackProgressApplyResult applyInternal(PlaybackProgressInput input,
                                                                           List<PlaybackDeleteTombstone> tombstones,
                                                                           boolean remote) {
        if (Setting.isIncognito()) return PlaybackProgressApplyResult.failed(input, "隐身模式不允许写入");
        if (input == null) return PlaybackProgressApplyResult.failed((PlaybackProgressInput) null, "请求体不能为空");
        input.normalize();
        int cid = targetCid(input);
        if (cid <= 0) return PlaybackProgressApplyResult.skipped(input, input.historyKey, "接口不匹配", 0);
        long remoteUpdatedAt = input.updatedAt;
        long deletedAt = PlaybackDeleteTombstoneStore.latest(tombstones, input.configKey, cid,
                input.historyKey, input.siteKey, input.vodId,
                input.mediaType, input.tmdbId, input.seasonNumber);
        if (remote) {
            // The caller's snapshot may have been taken just before a local delete.
            // Re-read while holding the writer lock so an in-flight tombstone wins.
            deletedAt = Math.max(deletedAt, PlaybackDeleteTombstoneStore.latest(
                    PlaybackDeleteTombstoneStore.snapshot(), input.configKey, cid,
                    input.historyKey, input.siteKey, input.vodId,
                    input.mediaType, input.tmdbId, input.seasonNumber));
        }
        if (remote && remoteUpdatedAt <= 0 && deletedAt > 0) {
            return PlaybackProgressApplyResult.skipped(input, input.historyKey, "记录已删除且远端缺少更新时间", deletedAt);
        }
        String error = input.validate(!remote || deletedAt <= 0);
        if (!TextUtils.isEmpty(error)) return PlaybackProgressApplyResult.failed(input, error);
        if (input.updatedAt <= 0) input.updatedAt = System.currentTimeMillis();
        if (deletedAt > 0 && input.updatedAt <= deletedAt) {
            return PlaybackProgressApplyResult.skipped(input, input.historyKey, "记录已被删除", deletedAt);
        }
        String requestedKey = input.targetHistoryKey(cid);
        synchronized (TmdbSeasonProgressStore.class) {
        History local = findLocal(cid, input, requestedKey);
        TmdbSeasonProgress targetSnapshot = remote && input.hasTmdbEpisodeIdentity()
                ? TmdbSeasonProgressStore.find(cid, input.mediaType, input.tmdbId, input.seasonNumber)
                : null;
        RemoteSeasonUpsertMode seasonMode = remote && input.hasTmdbEpisodeIdentity()
                ? planRemoteSeasonUpsert(input, local, targetSnapshot)
                : null;
        if (seasonMode == RemoteSeasonUpsertMode.SKIP) {
            long freshAt = targetSnapshot == null
                    ? local == null ? 0 : local.getCreateTime()
                    : targetSnapshot.updatedAt;
            return PlaybackProgressApplyResult.skipped(input,
                    local == null ? requestedKey : local.getKey(), "stale season progress", freshAt);
        }
        if (seasonMode == null && local != null && input.updatedAt <= local.getCreateTime()) {
            return PlaybackProgressApplyResult.skipped(input, local.getKey(), "远端记录不新于本地", local.getCreateTime());
        }
        // Keep an existing local key when matching a legacy/base history key. This
        // avoids creating a duplicate `site@@@vod@@@cid` row solely because the
        // remote payload carries a portable cid-qualified key.
        String key = local == null ? requestedKey : local.getKey();
        History history = local == null ? new History() : local.copy();
        boolean sameEpisode = isSameEpisode(local, input);
        history.setKey(key);
        history.setCid(cid);
        history.setVodName(input.vodName);
        history.setVodPic(input.vodPic);
        history.setVodFlag(input.flag);
        applySourceBindingKey(history, input);
        history.setVodRemarks(input.episodeName);
        history.setEpisodeUrl(input.episodeUrl);
        if (input.hasTmdbEpisodeIdentity()) {
            history.setTmdbId(input.tmdbId);
            history.setMediaType(input.mediaType);
            history.setTmdbEpisodePosition(input.seasonNumber, input.episodeNumber);
        } else if (!sameEpisode) {
            history.setTmdbEpisodePosition(null);
        }
        history.setPosition(input.positionMs);
        history.setDuration(input.durationMs);
        applySpeed(history, input.speed, input.speedOverride);
        history.setCreateTime(input.updatedAt);
        boolean snapshotOnly = seasonMode == RemoteSeasonUpsertMode.SNAPSHOT_ONLY;
        TmdbSeasonProgressStore.runInTransaction(() -> {
            if (!snapshotOnly) {
                if (hasMediaIdentityChanged(local, history)) {
                    AppDatabase.get().getTmdbSeasonProgressDao().deleteBySource(cid, history.getKey());
                }
                AppDatabase.get().getHistoryDao().insertOrUpdate(history);
            }
            TmdbSeasonProgressStore.write(history);
            return null;
        });
        RefreshEvent.history();
        return local == null ? PlaybackProgressApplyResult.created(input, history.getKey()) : PlaybackProgressApplyResult.updated(input, history.getKey());
        }
    }

    static boolean hasMediaIdentityChanged(History before, History after) {
        if (before == null || after == null) return false;
        return before.getTmdbId() != after.getTmdbId()
                || !TmdbSeasonProgress.normalizeMediaType(before.getMediaType()).equals(
                TmdbSeasonProgress.normalizeMediaType(after.getMediaType()));
    }

    static RemoteSeasonUpsertMode planRemoteSeasonUpsert(
            PlaybackProgressInput input, History local, TmdbSeasonProgress targetSnapshot) {
        if (input == null || !input.hasTmdbEpisodeIdentity()) return RemoteSeasonUpsertMode.ROUTE_AND_SNAPSHOT;
        if (targetSnapshot != null && input.updatedAt <= targetSnapshot.updatedAt) {
            return RemoteSeasonUpsertMode.SKIP;
        }
        boolean localIsSameMedia = local != null
                && local.getTmdbId() == input.tmdbId
                && TmdbSeasonProgress.normalizeMediaType(local.getMediaType()).equals(
                TmdbSeasonProgress.normalizeMediaType(input.mediaType));
        boolean localIsTarget = localIsSameMedia && local.getTmdbSeasonNumber() == input.seasonNumber;
        if (localIsTarget && input.updatedAt <= local.getCreateTime()) return RemoteSeasonUpsertMode.SKIP;
        if (local != null && !localIsSameMedia && input.updatedAt <= local.getCreateTime()) {
            return RemoteSeasonUpsertMode.SKIP;
        }
        if (localIsSameMedia && !localIsTarget && input.updatedAt <= local.getCreateTime()) {
            return RemoteSeasonUpsertMode.SNAPSHOT_ONLY;
        }
        return RemoteSeasonUpsertMode.ROUTE_AND_SNAPSHOT;
    }

    static boolean isSameEpisode(History local, PlaybackProgressInput input) {
        if (local == null || input == null) return false;
        return Episode.create(input.episodeName, input.episodeUrl).matchesPlayback(local.getEpisode());
    }

    static void applySpeed(History history, float speed, Boolean speedOverride) {
        if (history == null) return;
        float value = speed <= 0 ? 1f : speed;
        if (speedOverride == null) {
            history.setSpeed(value);
        } else if (speedOverride) {
            history.setUserSpeed(value);
        } else {
            history.setSpeed(1f);
            history.setSpeedOverride(false);
        }
    }

    static void applySourceBindingKey(History history, PlaybackProgressInput input) {
        if (history == null || input == null) return;
        history.setSourceBindingKey(input.sourceBindingKey);
    }

    static synchronized PlaybackProgressApplyResult deleteInternal(
            PlaybackProgressDeleteInput input,
            RemoteSyncConfig filter,
            boolean remote,
            boolean userInitiated) {
        if (!userInitiated && Setting.isIncognito()) {
            return PlaybackProgressApplyResult.failed(input, "隐身模式不允许清理");
        }
        if (input == null) return PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "请求体不能为空");
        input.normalize();
        if (input.hasMalformedSeasonScope()) {
            return PlaybackProgressApplyResult.failed(input, "季度删除缺少有效的 TMDB 电视剧季度身份");
        }
        if (!matchesFilter(input, filter)) return PlaybackProgressApplyResult.skipped(input, input.historyKey, "站点不匹配");
        int cid = targetCid(input, userInitiated);
        if (!canDeleteCid(cid, userInitiated)) return PlaybackProgressApplyResult.skipped(input, input.historyKey, "接口不匹配");
        if (remote && input.deletedAt <= 0) return PlaybackProgressApplyResult.failed(input, "远端删除记录缺少deletedAt");
        if (!remote && input.isAllScope() && !input.confirm) return PlaybackProgressApplyResult.failed(input, "全量清理需要confirm=true");
        if (input.isSiteScope() && TextUtils.isEmpty(input.siteKey)) return PlaybackProgressApplyResult.failed(input, "按站点清理需要siteKey");
        if (requiresSourceIdentity(input) && TextUtils.isEmpty(input.historyKey)
                && (TextUtils.isEmpty(input.siteKey) || TextUtils.isEmpty(input.vodId))) {
            return PlaybackProgressApplyResult.failed(input, "historyKey、siteKey+vodId或siteKey不能为空");
        }
        return TmdbSeasonProgressStore.runInTransaction(
                () -> deleteInternalTransaction(input, filter, remote, userInitiated));
    }

    private static PlaybackProgressApplyResult deleteInternalTransaction(
            PlaybackProgressDeleteInput input,
            RemoteSyncConfig filter,
            boolean remote,
            boolean userInitiated) {
        if (!userInitiated && Setting.isIncognito()) return PlaybackProgressApplyResult.failed(input, "隐身模式不允许清理");
        if (input == null) return PlaybackProgressApplyResult.failed((PlaybackProgressDeleteInput) null, "请求体不能为空");
        input.normalize();
        if (input.hasMalformedSeasonScope()) {
            return PlaybackProgressApplyResult.failed(input, "季度删除缺少有效的 TMDB 电视剧季度身份");
        }
        if (!matchesFilter(input, filter)) return PlaybackProgressApplyResult.skipped(input, input.historyKey, "站点不匹配");
        int cid = targetCid(input, userInitiated);
        if (!canDeleteCid(cid, userInitiated)) return PlaybackProgressApplyResult.skipped(input, input.historyKey, "接口不匹配");
        if (remote && input.deletedAt <= 0) return PlaybackProgressApplyResult.failed(input, "远端删除记录缺少deletedAt");
        if (!remote && input.isAllScope() && !input.confirm) return PlaybackProgressApplyResult.failed(input, "全量清理需要confirm=true");
        if (input.isSiteScope() && TextUtils.isEmpty(input.siteKey)) return PlaybackProgressApplyResult.failed(input, "按站点清理需要siteKey");
        if (requiresSourceIdentity(input) && TextUtils.isEmpty(input.historyKey)
                && (TextUtils.isEmpty(input.siteKey) || TextUtils.isEmpty(input.vodId))) {
            return PlaybackProgressApplyResult.failed(input, "historyKey、siteKey+vodId或siteKey不能为空");
        }
        if (input.deletedAt <= 0) input.deletedAt = System.currentTimeMillis();

        // Persist the marker before touching History. If a playback write races this
        // deletion, the marker makes the stale write lose deterministically.
        PlaybackDeleteTombstoneStore.record(input, cid, filter);
        if (input.isSeasonScope()) {
            if (remote) {
                long cutoff = Math.max(input.deletedAt, PlaybackDeleteTombstoneStore.latest(
                        PlaybackDeleteTombstoneStore.snapshot(), input.configKey, cid,
                        input.historyKey, input.siteKey, input.vodId,
                        input.mediaType, input.tmdbId, input.seasonNumber));
                List<History> histories = AppDatabase.get().getHistoryDao()
                        .findByTmdbIdentity(cid, input.mediaType, input.tmdbId);
                List<TmdbSeasonProgress> snapshots = AppDatabase.get().getTmdbSeasonProgressDao()
                        .findByMedia(cid, input.mediaType, input.tmdbId);
                if (filter != null) {
                    snapshots = snapshotsForRoutes(snapshots, histories, filter);
                    histories.removeIf(item -> !filter.matchesSite(item.getSiteKey()));
                }
                if (hasNewerSeasonState(histories, snapshots, input.seasonNumber, cutoff)) {
                    return PlaybackProgressApplyResult.skipped(
                            input, resultKey(input), "本地季度进度更新于删除事件");
                }
            }
            int affected = deleteSeason(cid, input, filter);
            if (affected > 0 && !userInitiated) RefreshEvent.history();
            if (userInitiated) PlaybackEventCollector.get().onHistoryDeleted(input, cid);
            return affected > 0
                    ? PlaybackProgressApplyResult.deleted(input, resultKey(input), affected)
                    : PlaybackProgressApplyResult.skipped(input, resultKey(input), "本地记录不存在");
        }
        List<PlaybackDeleteTombstone> tombstones = PlaybackDeleteTombstoneStore.snapshot();
        HistoryDao dao = AppDatabase.get().getHistoryDao();
        List<History> candidates = candidates(dao, cid, input);
        Map<String, History> progressToReconcile = new LinkedHashMap<>();
        int affected = 0;
        int newer = 0;
        long deletedAt = input.deletedAt;
        for (History history : candidates) {
            if (filter != null && !filter.matchesSite(history.getSiteKey())) continue;
            long cutoff = Math.max(deletedAt, PlaybackDeleteTombstoneStore.latest(tombstones, input.configKey, cid,
                    history.getKey(), history.getSiteKey(), history.getVodId()));
            if (remote && history.getCreateTime() > cutoff) {
                newer++;
                continue;
            }
            List<TmdbSeasonProgress> sourceSnapshots = AppDatabase.get().getTmdbSeasonProgressDao()
                    .findBySource(cid, history.getMediaType(), history.getTmdbId(), history.getKey());
            if (remote && sourceSnapshots.stream().anyMatch(snapshot -> snapshot.updatedAt > cutoff)) {
                newer++;
                continue;
            }
            AppDatabase.get().getTmdbSeasonProgressDao().deleteBySource(cid, history.getKey());
            int count = dao.delete(cid, history.getKey());
            if (count <= 0) continue;
            AppDatabase.get().getTrackDao().delete(history.getKey());
            if (TmdbSeasonProgressStore.isEligible(history)) {
                String identity = history.getCid() + ":" + history.getMediaType() + ":"
                        + history.getTmdbId() + ":" + history.getTmdbSeasonNumber();
                progressToReconcile.put(identity, history);
            }
            affected += count;
        }
        for (History history : progressToReconcile.values()) {
            TmdbSeasonProgressStore.reconcile(history.getCid(), history.getMediaType(),
                    history.getTmdbId(), history.getTmdbSeasonNumber());
        }
        if (input.isAllScope() && filter == null) {
            affected += remote
                    ? AppDatabase.get().getTmdbSeasonProgressDao().deleteByCidBeforeOrAt(cid, input.deletedAt)
                    : AppDatabase.get().getTmdbSeasonProgressDao().deleteByCid(cid);
        }

        // The history screens update their adapters themselves; avoid a second refresh
        // event while a user deletion is being animated. API/remote callers still need
        // to notify other history consumers.
        if (affected > 0 && !userInitiated) RefreshEvent.history();
        if (userInitiated) PlaybackEventCollector.get().onHistoryDeleted(input, cid);
        if (affected > 0) return PlaybackProgressApplyResult.deleted(input, resultKey(input), affected);
        if (newer > 0) return PlaybackProgressApplyResult.skipped(input, resultKey(input), "本地记录更新于删除事件");
        return PlaybackProgressApplyResult.skipped(input, resultKey(input), "本地记录不存在");
    }

    static boolean shouldRestoreAnotherSeason(PlaybackProgressDeleteInput input) {
        return input != null && input.isSeasonScope();
    }

    static boolean hasNewerSeasonState(List<History> histories,
                                       List<TmdbSeasonProgress> snapshots,
                                       int seasonNumber,
                                       long cutoff) {
        if (snapshots != null) {
            for (TmdbSeasonProgress snapshot : snapshots) {
                if (snapshot != null && snapshot.seasonNumber == seasonNumber
                        && snapshot.updatedAt > cutoff) return true;
            }
        }
        if (histories != null) {
            for (History history : histories) {
                if (history != null && history.getTmdbSeasonNumber() == seasonNumber
                        && history.getTmdbEpisodeNumber() > 0
                        && history.getCreateTime() > cutoff) return true;
            }
        }
        return false;
    }

    static List<TmdbSeasonProgress> snapshotsForRoutes(
            List<TmdbSeasonProgress> snapshots, List<History> histories, RemoteSyncConfig filter) {
        if (snapshots == null || snapshots.isEmpty() || filter == null) return snapshots == null ? List.of() : snapshots;
        Map<String, History> routes = new LinkedHashMap<>();
        if (histories != null) for (History history : histories) {
            if (history != null) routes.put(history.getKey(), history);
        }
        List<TmdbSeasonProgress> result = new ArrayList<>();
        for (TmdbSeasonProgress snapshot : snapshots) {
            History route = snapshot == null ? null : routes.get(snapshot.sourceHistoryKey);
            if (route != null && filter.matchesSite(route.getSiteKey())) result.add(snapshot);
        }
        return result;
    }

    private static int deleteSeason(int cid, PlaybackProgressDeleteInput input, RemoteSyncConfig filter) {
        return TmdbSeasonProgressStore.runInTransaction(() -> {
            String mediaType = input.mediaType;
            List<History> histories = AppDatabase.get().getHistoryDao()
                    .findByTmdbIdentity(cid, mediaType, input.tmdbId);
            List<com.fongmi.android.tv.bean.TmdbSeasonProgress> snapshots = AppDatabase.get()
                    .getTmdbSeasonProgressDao().findByMedia(cid, mediaType, input.tmdbId);
            if (filter != null) {
                snapshots = snapshotsForRoutes(snapshots, histories, filter);
                histories.removeIf(item -> !filter.matchesSite(item.getSiteKey()));
            }
            TmdbSeasonDeletePlanner.Plan plan = TmdbSeasonDeletePlanner.plan(
                    histories, snapshots, cid, mediaType, input.tmdbId, input.seasonNumber);
            boolean deleteSnapshot = filter == null;
            if (!deleteSnapshot) for (TmdbSeasonProgress snapshot : snapshots) {
                if (snapshot.seasonNumber == input.seasonNumber) {
                    deleteSnapshot = true;
                    break;
                }
            }
            int affected = deleteSnapshot ? AppDatabase.get().getTmdbSeasonProgressDao().delete(
                    cid, mediaType, input.tmdbId, input.seasonNumber) : 0;
            for (Map.Entry<String, com.fongmi.android.tv.bean.TmdbSeasonProgress> entry
                    : plan.restoreRoutes().entrySet()) {
                History route = AppDatabase.get().getHistoryDao().find(cid, entry.getKey());
                if (route == null) continue;
                TmdbSeasonProgressStore.apply(route, entry.getValue());
                AppDatabase.get().getHistoryDao().insertOrUpdate(route);
                affected++;
            }
            for (String key : plan.deleteRouteKeys()) {
                int count = AppDatabase.get().getHistoryDao().delete(cid, key);
                if (count > 0) AppDatabase.get().getTrackDao().delete(key);
                affected += count;
            }
            return affected;
        });
    }

    private static List<History> candidates(HistoryDao dao, int cid, PlaybackProgressDeleteInput input) {
        Map<String, History> result = new LinkedHashMap<>();
        if (!TextUtils.isEmpty(input.historyKey)) {
            History exact = dao.find(cid, input.historyKey);
            if (exact != null) result.put(exact.getKey(), exact);
            // A history key from another device normally contains a different cid suffix.
            if (result.isEmpty() && !TextUtils.isEmpty(input.siteKey) && !TextUtils.isEmpty(input.vodId)) addByItem(dao, cid, input, result);
        } else if (!TextUtils.isEmpty(input.siteKey) && !TextUtils.isEmpty(input.vodId)) {
            addByItem(dao, cid, input, result);
        } else {
            for (History history : dao.findAll(cid)) result.put(history.getKey(), history);
        }
        if (input.isSiteScope() && !TextUtils.isEmpty(input.siteKey)) {
            result.entrySet().removeIf(entry -> !TextUtils.equals(RemoteSyncConfig.normalize(entry.getValue().getSiteKey()), RemoteSyncConfig.normalize(input.siteKey)));
        }
        if (!input.isAllScope() && !input.isSiteScope() && TextUtils.isEmpty(input.historyKey)
                && (TextUtils.isEmpty(input.siteKey) || TextUtils.isEmpty(input.vodId))) result.clear();
        return new ArrayList<>(result.values());
    }

    private static void addByItem(HistoryDao dao, int cid, PlaybackProgressDeleteInput input, Map<String, History> result) {
        String baseKey = input.siteKey + AppDatabase.SYMBOL + input.vodId;
        History base = dao.find(cid, baseKey);
        if (base != null) result.put(base.getKey(), base);
        for (History item : dao.findByKeyPrefix(cid, baseKey + AppDatabase.SYMBOL)) result.put(item.getKey(), item);
    }

    private static boolean matchesFilter(PlaybackProgressDeleteInput input, RemoteSyncConfig filter) {
        if (filter == null || filter.siteKeys == null || filter.siteKeys.isEmpty()) return true;
        if (input.isAllScope() || input.isSeasonScope()) return true;
        return !TextUtils.isEmpty(input.siteKey) && filter.matchesSite(input.siteKey);
    }

    private static String resultKey(PlaybackProgressDeleteInput input) {
        if (!TextUtils.isEmpty(input.historyKey)) return input.historyKey;
        if (!TextUtils.isEmpty(input.siteKey) && !TextUtils.isEmpty(input.vodId)) return input.siteKey + AppDatabase.SYMBOL + input.vodId;
        return "";
    }

    private static History findLocal(int cid, PlaybackProgressInput input, String key) {
        History exact = AppDatabase.get().getHistoryDao().find(cid, key);
        if (exact != null) return exact;
        String baseKey = input.siteKey + AppDatabase.SYMBOL + input.vodId;
        History base = AppDatabase.get().getHistoryDao().find(cid, baseKey);
        if (base != null) return base;
        List<History> items = AppDatabase.get().getHistoryDao().findByKeyPrefix(cid, baseKey + AppDatabase.SYMBOL);
        if (items.isEmpty()) return null;
        return bestEpisodeMatch(items, input);
    }

    private static History bestEpisodeMatch(List<History> items, PlaybackProgressInput input) {
        for (History item : items) if (!TextUtils.isEmpty(input.episodeUrl) && TextUtils.equals(input.episodeUrl, item.getEpisodeUrl())) return item;
        for (History item : items) if (!TextUtils.isEmpty(input.flag) && TextUtils.equals(input.flag, item.getVodFlag()) && TextUtils.equals(input.episodeName, item.getVodRemarks())) return item;
        for (History item : items) if (TextUtils.equals(input.episodeName, item.getVodRemarks())) return item;
        return items.get(0);
    }

    private static int targetCid(PlaybackProgressInput input) {
        int cid = PlaybackConfigIdentity.cidForKey(input.configKey);
        if (cid > 0) return cid;
        if (!TextUtils.isEmpty(input.configKey)) return 0;
        return input.cid > 0 ? input.cid : VodConfig.getCid();
    }

    static int targetCid(PlaybackProgressDeleteInput input, boolean userInitiated) {
        return userInitiated ? input.cid : targetCid(input);
    }

    private static int targetCid(PlaybackProgressDeleteInput input) {
        int cid = PlaybackConfigIdentity.cidForKey(input.configKey);
        if (cid > 0) return cid;
        if (!TextUtils.isEmpty(input.configKey)) return 0;
        if (input.cid > 0) return input.cid;
        try {
            int index = input.historyKey.lastIndexOf(AppDatabase.SYMBOL);
            if (index >= 0) return Integer.parseInt(input.historyKey.substring(index + AppDatabase.SYMBOL.length()));
        } catch (Exception ignored) {
        }
        return VodConfig.getCid();
    }
}
