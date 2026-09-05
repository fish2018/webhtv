package com.fongmi.android.tv.bean;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.history.HistoryDisplayPolicy;
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.playback.PlaybackProgressWriter;
import com.fongmi.android.tv.playback.PlaybackDeleteTombstoneStore;
import com.fongmi.android.tv.playback.TmdbSeasonProgressStore;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Entity
public class History implements Diffable<History> {

    private static final long HISTORY_REFRESH_DEBOUNCE = 500;
    private static final Runnable HISTORY_REFRESH = RefreshEvent::history;
    private static final long NEAR_END_MIN_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long NEAR_END_MAX_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long ENDING_MIN_PLAYED_MS = TimeUnit.MINUTES.toMillis(1);

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("wallPic")
    private String wallPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodFlag")
    private String vodFlag;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("revSort")
    private boolean revSort;
    @SerializedName("revPlay")
    private boolean revPlay;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("opening")
    private long opening;
    @SerializedName("ending")
    private long ending;
    @SerializedName("position")
    private long position;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private float speed;
    @SerializedName("speedOverride")
    @ColumnInfo(defaultValue = "0")
    private boolean speedOverride;
    @SerializedName("scale")
    private int scale;
    @SerializedName("cid")
    private int cid;
    @SerializedName("typeName")
    private String typeName;
    @SerializedName("area")
    private String area;
    @SerializedName("actor")
    private String actor;
    @SerializedName("director")
    private String director;
    @SerializedName("year")
    private String year;
    @SerializedName("tmdbId")
    @ColumnInfo(defaultValue = "0")
    private int tmdbId;
    @SerializedName("mediaType")
    @ColumnInfo(defaultValue = "")
    private String mediaType;
    @SerializedName("legacyKey")
    @ColumnInfo(defaultValue = "")
    private String legacyKey;
    @SerializedName("tmdbSeasonNumber")
    @ColumnInfo(defaultValue = "0")
    private int tmdbSeasonNumber;
    @SerializedName("tmdbEpisodeNumber")
    @ColumnInfo(defaultValue = "0")
    private int tmdbEpisodeNumber;

    @SerializedName("player")
    @ColumnInfo(defaultValue = "-1")
    private int player = PlayerSetting.NONE;
    private transient long updateTime;
    private transient String playbackSourceKey;
    @Ignore
    private transient String sourceBindingKey;
    @Ignore
    private transient String displayIdentity;

    public History() {
        this.speed = 1;
        this.scale = -1;
        this.ending = C.TIME_UNSET;
        this.opening = C.TIME_UNSET;
        this.position = C.TIME_UNSET;
        this.duration = C.TIME_UNSET;
    }

    public History copy() {
        History item = new History();
        item.key = key;
        item.vodPic = vodPic;
        item.wallPic = wallPic;
        item.vodName = vodName;
        item.vodFlag = vodFlag;
        item.vodRemarks = vodRemarks;
        item.episodeUrl = episodeUrl;
        item.revSort = revSort;
        item.revPlay = revPlay;
        item.createTime = createTime;
        item.opening = opening;
        item.ending = ending;
        item.position = position;
        item.duration = duration;
        item.speed = speed;
        item.speedOverride = speedOverride;
        item.scale = scale;
        item.cid = cid;
        item.typeName = typeName;
        item.area = area;
        item.actor = actor;
        item.director = director;
        item.year = year;
        item.tmdbId = tmdbId;
        item.mediaType = mediaType;
        item.legacyKey = legacyKey;
        item.tmdbSeasonNumber = tmdbSeasonNumber;
        item.tmdbEpisodeNumber = tmdbEpisodeNumber;
        item.player = player;
        item.updateTime = updateTime;
        item.playbackSourceKey = playbackSourceKey;
        item.sourceBindingKey = sourceBindingKey;
        item.displayIdentity = displayIdentity;
        return item;
    }

    public static History objectFrom(String str) {
        return App.gson().fromJson(str, History.class);
    }

    public static List<History> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, History.class).getType();
        List<History> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static List<History> get() {
        return get(VodConfig.getCid());
    }

    public static List<History> getForDisplay() {
        if (!Setting.isGlobalHistoryEnabled()) return get();
        boolean aggregate = Setting.isHistoryAggregationEffective();
        return HistoryDisplayPolicy.project(AppDatabase.get().getHistoryDao().findAll(),
                aggregate ? AppDatabase.get().getTmdbSeasonProgressDao().findAll() : Collections.emptyList(), aggregate);
    }

    public static List<History> getAll() {
        boolean aggregate = Setting.isHistoryAggregationEffective();
        return HistoryDisplayPolicy.project(AppDatabase.get().getHistoryDao().findAll(),
                aggregate ? AppDatabase.get().getTmdbSeasonProgressDao().findAll() : Collections.emptyList(), aggregate);
    }

    public static List<History> get(int cid) {
        List<History> items = AppDatabase.get().getHistoryDao().find(cid);
        if (!Setting.isHistoryAggregationEffective()) return items;
        return HistoryDisplayPolicy.project(items, AppDatabase.get().getTmdbSeasonProgressDao().findAll(cid), true);
    }

    public static History find(String key) {
        return AppDatabase.get().getHistoryDao().find(VodConfig.getCid(), key);
    }

    public static History find(int cid, String key) {
        return AppDatabase.get().getHistoryDao().find(cid, key);
    }

    public static History findPlayback(String key, String vodName, List<Flag> flags) {
        return findPlayback(key, Collections.singletonList(vodName), flags);
    }

    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags) {
        return findPlayback(key, vodNames, flags, 0, "", -1);
    }

    /**
     * @param explicitTmdbItem 调用方（如从 TMDB 详情进入播放）已知的完整 TMDB 身份，优先用它跨源查找，
     *                         避免依赖尚未完成的异步匹配缓存，也避免电影与剧集数字 ID 相同时串记录。
     */
    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags, TmdbItem explicitTmdbItem) {
        return findPlayback(key, vodNames, flags, explicitTmdbItem, -1);
    }

    /**
     * Season-aware playback lookup. A non-negative expectedSeason prevents a progress record from another
     * season (or an unknown-season legacy record from another source key) from being reused.
     */
    public static History findPlayback(String key, List<String> vodNames, List<Flag> flags, TmdbItem explicitTmdbItem, int expectedSeason) {
        int tmdbId = explicitTmdbItem == null ? 0 : explicitTmdbItem.getTmdbId();
        String mediaType = explicitTmdbItem == null ? "" : explicitTmdbItem.getMediaType();
        return findPlayback(key, vodNames, flags, tmdbId, mediaType, expectedSeason);
    }

    private static History findPlayback(String key, List<String> vodNames, List<Flag> flags, int explicitTmdbId, String explicitMediaType, int expectedSeason) {
        // 聚合模式：优先按完整 TMDB 身份跨源查找最新历史，命中则复用该记录并切换到当前 key。
        History aggregated = findPlaybackByTmdb(key, vodNames, flags, explicitTmdbId, explicitMediaType, expectedSeason);
        if (aggregated != null) return aggregated;
        History history = find(key);
        if (isSeasonEligible(history, key, expectedSeason)) return copyForPlaybackKey(history, key, flags, history);
        if (vodNames != null) {
            for (String vodName : vodNames) {
                if (vodName == null || vodName.isEmpty()) continue;
                history = findPlaybackCandidate(key, findByName(vodName), flags, expectedSeason);
                if (history != null) return history.cid(VodConfig.getCid());
            }
        }
        return null;
    }

    /**
     * 聚合模式下按 (mediaType, tmdbId) 跨源查找历史。
     * 优先用调用方传入的完整身份，其次从 key/剧名解析。
     * 命中当前 key 自身记录时直接返回；否则走 findPlaybackCandidate 做跨源剧集匹配
     * （按剧集名/URL 对齐源 B 的线路与集数），仅复用可续播的进度。
     */
    private static History findPlaybackByTmdb(String key, List<String> vodNames, List<Flag> flags, int explicitTmdbId, String explicitMediaType, int expectedSeason) {
        if (!Setting.isHistoryAggregationEffective()) return null;
        TmdbIdentity identity = explicitTmdbId > 0 && !normalizeMediaType(explicitMediaType).isEmpty()
                ? new TmdbIdentity(explicitTmdbId, normalizeMediaType(explicitMediaType))
                : resolveTmdbIdentity(key, vodNames);
        if (identity == null) return null;
        List<History> list = AppDatabase.get().getHistoryDao().findByTmdbIdentity(VodConfig.getCid(), identity.mediaType(), identity.tmdbId());
        if (list.isEmpty()) return null;
        if ("tv".equals(identity.mediaType())) {
            if (expectedSeason >= 0) {
                TmdbSeasonProgress progress = TmdbSeasonProgressStore.find(
                        VodConfig.getCid(), identity.mediaType(), identity.tmdbId(), expectedSeason);
                list = overlaySeasonProgress(list, progress);
            } else {
                int localSeason = knownSeasonForRoute(key, list);
                if (localSeason >= 0) {
                    TmdbSeasonProgress progress = TmdbSeasonProgressStore.find(
                            VodConfig.getCid(), identity.mediaType(), identity.tmdbId(), localSeason);
                    list = overlayLocalSeasonProgress(key, list, progress);
                }
            }
        }
        // 整剧级统一进度：不再因当前 key 命中自身记录就直接返回该源旧进度，
        // 统一交给 findPlaybackCandidate 在全部同剧记录中选「最近可续播」的那条
        // （list 已按 createTime DESC 排序），使回到任一源都续到全剧最新进度。
        return findPlaybackCandidate(key, list, flags, expectedSeason);
    }

    static List<History> overlaySeasonProgress(List<History> histories, TmdbSeasonProgress progress) {
        if (histories == null || histories.isEmpty() || progress == null
                || progress.episodeNumber <= 0 || progress.seasonNumber < 0
                || TextUtils.isEmpty(progress.sourceHistoryKey)) return histories;
        History source = null;
        for (History history : histories) {
            if (history != null && TextUtils.equals(history.getKey(), progress.sourceHistoryKey)) {
                source = history;
                break;
            }
        }
        if (source == null || source.getCid() != progress.cid || source.getTmdbId() != progress.tmdbId
                || !normalizeMediaType(source.getMediaType()).equals(normalizeMediaType(progress.mediaType))) return histories;
        History snapshot = source.copy();
        TmdbSeasonProgressStore.apply(snapshot, progress);
        List<History> result = new ArrayList<>(histories.size() + 1);
        result.add(snapshot);
        result.addAll(histories);
        return result;
    }

    static List<History> overlayLocalSeasonProgress(
            String key, List<History> histories, TmdbSeasonProgress progress) {
        if (histories == null || histories.isEmpty() || progress == null
                || !TextUtils.equals(key, progress.sourceHistoryKey)) return histories;
        for (History history : histories) {
            if (history == null || !TextUtils.equals(key, history.getKey())) continue;
            if (history.getTmdbEpisodeNumber() <= 0
                    || history.getTmdbSeasonNumber() != progress.seasonNumber) return histories;
            return overlaySeasonProgress(histories, progress);
        }
        return histories;
    }

    private static int knownSeasonForRoute(String key, List<History> histories) {
        if (TextUtils.isEmpty(key) || histories == null) return -1;
        for (History history : histories) {
            if (history != null && TextUtils.equals(key, history.getKey())
                    && history.getTmdbEpisodeNumber() > 0
                    && history.getTmdbSeasonNumber() >= 0) return history.getTmdbSeasonNumber();
        }
        return -1;
    }

    /**
     * 从 key（siteKey@@@vodId）与候选剧名解析完整 TMDB 身份。
     * 优先读当前 key 已存历史记录里的 tmdbId/mediaType（来自 DB，可靠），
     * 未命中再回退到 TmdbMatchCache 按 siteKey/vodId/名称查询（异步匹配可能尚未完成）。
     */
    private static TmdbIdentity resolveTmdbIdentity(String key, List<String> vodNames) {
        if (TextUtils.isEmpty(key)) return null;
        History existing = find(key);
        if (existing != null && existing.getTmdbId() > 0 && !normalizeMediaType(existing.getMediaType()).isEmpty()) {
            return new TmdbIdentity(existing.getTmdbId(), normalizeMediaType(existing.getMediaType()));
        }
        String[] parts = key.split(AppDatabase.SYMBOL);
        if (parts.length < 2) return null;
        String siteKey = parts[0];
        String vodId = parts[1];
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        if (vodNames != null) {
            for (String vodName : vodNames) {
                TmdbItem item = cache.find(siteKey, vodId, vodName);
                TmdbIdentity identity = tmdbIdentity(item);
                if (identity != null) return identity;
            }
        }
        TmdbItem item = cache.find(siteKey, vodId);
        return tmdbIdentity(item);
    }

    private static TmdbIdentity tmdbIdentity(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return null;
        String mediaType = normalizeMediaType(item.getMediaType());
        return mediaType.isEmpty() ? null : new TmdbIdentity(item.getTmdbId(), mediaType);
    }

    private static String normalizeMediaType(String value) {
        if (value == null) return "";
        String mediaType = value.trim().toLowerCase(Locale.ROOT);
        return "movie".equals(mediaType) || "tv".equals(mediaType) ? mediaType : "";
    }

    private record TmdbIdentity(int tmdbId, String mediaType) {
    }

    public static List<History> findByName(String name) {
        try {
            return AppDatabase.get().getHistoryDao().findByName(VodConfig.getCid(), name);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void delete(int cid) {
        int deleted = AppDatabase.get().getHistoryDao().delete(cid);
        AppDatabase.get().getTmdbSeasonProgressDao().deleteByCid(cid);
        if (deleted > 0) notifyChanged();
    }

    public static void deleteForDisplay() {
        if (Setting.isGlobalHistoryEnabled()) {
            List<History> items = AppDatabase.get().getHistoryDao().findAll();
            Set<Integer> cids = new HashSet<>();
            for (History item : items) cids.add(item.getCid());
            for (TmdbSeasonProgress progress : AppDatabase.get().getTmdbSeasonProgressDao().findAll()) {
                cids.add(progress.cid);
            }
            int deleted = 0;
            for (int cid : cids) deleted += PlaybackProgressWriter.deleteAllFromUser(cid).affected;
            if (deleted > 0) notifyChanged();
        } else {
            PlaybackProgressWriter.deleteAllFromUser(VodConfig.getCid());
        }
    }

    public static void deleteAndSync(int cid) {
        PlaybackProgressWriter.deleteAllFromUser(cid);
    }

    public static void sync(List<History> targets) {
        targets.forEach(target -> {
            if (!target.canMergeByName()) {
                target.cid(VodConfig.getCid()).save();
                return;
            }
            List<History> items = findByName(target.getVodName());
            if (items.isEmpty()) target.cid(VodConfig.getCid()).save();
            else {
                long latestTime = items.stream().mapToLong(History::getCreateTime).max().orElse(0L);
                if (target.getCreateTime() > latestTime) target.cid(VodConfig.getCid()).merge(items, true).save();
            }
        });
    }

    static History findPlaybackCandidate(String key, List<History> items, List<Flag> flags) {
        return findPlaybackCandidate(key, items, flags, -1);
    }

    static History findPlaybackCandidate(String key, List<History> items, List<Flag> flags, int expectedSeason) {
        if (items == null || items.isEmpty()) return null;
        // 集数和进度按整剧聚合，线路仍优先使用当前片源自己的历史偏好。
        History local = findLocalPlaybackPreference(key, items, expectedSeason);
        History selected = null;
        for (History item : items) {
            if (isSeasonEligible(item, key, expectedSeason) && canResume(item) && matchesAnyEpisode(item, flags)) {
                selected = item;
                break;
            }
        }
        if (selected == null) {
            for (History item : items) {
                if (isSeasonEligible(item, key, expectedSeason) && canResume(item)) {
                    selected = item;
                    break;
                }
            }
        }
        if (selected == null) {
            for (History item : items) {
                if (isSeasonEligible(item, key, expectedSeason)) {
                    selected = item;
                    break;
                }
            }
        }
        return selected == null ? null : copyForPlaybackKey(selected, key, flags, local);
    }

    private static History findLocalPlaybackPreference(String key, List<History> items, int expectedSeason) {
        if (TextUtils.isEmpty(key)) return null;
        for (History item : items) {
            if (item != null && TextUtils.equals(key, item.getKey()) && isSeasonEligible(item, key, expectedSeason)) return item;
        }
        return null;
    }

    private static boolean isSeasonEligible(History item, String requestedKey, int expectedSeason) {
        if (item == null) return false;
        if (expectedSeason < 0) {
            boolean tmdbTv = item.getTmdbId() > 0 && "tv".equalsIgnoreCase(item.getMediaType());
            return !tmdbTv || TextUtils.equals(requestedKey, item.getKey());
        }
        int savedSeason = item.getTmdbSeasonNumber();
        boolean hasKnownSeason = savedSeason > 0 || (savedSeason == 0 && item.getTmdbEpisodeNumber() > 0);
        if (hasKnownSeason) return savedSeason == expectedSeason;
        return TextUtils.equals(requestedKey, item.getKey());
    }

    private static boolean canResume(History item) {
        return item != null && item.getPosition() > 0;
    }

    private static History copyForPlaybackKey(History item, String key, List<Flag> flags, History local) {
        History copy = item.copy();
        boolean sameRoute = TextUtils.equals(key, item.getKey());
        if (key != null && !key.isEmpty() && !TextUtils.equals(key, item.getKey())) {
            copy.playbackSourceKey = item.getKey();
            copy.setKey(key);
        }
        rebindPlaybackRoute(copy, local, flags, sameRoute);
        return copy;
    }

    private static void rebindPlaybackRoute(
            History playback, History local, List<Flag> flags, boolean sameRoute) {
        if (playback == null || flags == null || flags.isEmpty()) return;
        Flag preferred = sameRoute
                ? findStableFlag(flags, playback.getSourceBindingKey(), playback.getEpisodeUrl()) : null;
        if (preferred == null) preferred = findFlag(flags, local == null ? "" : local.getVodFlag());
        if (preferred == null) preferred = findFlag(flags, playback.getVodFlag());
        Episode episode = findMatchingEpisode(playback, preferred);
        Flag resolved = episode == null ? null : preferred;
        if (episode == null) {
            for (Flag flag : flags) {
                if (flag == null || flag == preferred) continue;
                episode = findMatchingEpisode(playback, flag);
                if (episode != null) {
                    resolved = flag;
                    break;
                }
            }
        }
        if (resolved == null) resolved = preferred != null ? preferred : firstFlag(flags);
        if (resolved != null) playback.setVodFlag(resolved.getFlag());
        playback.setSourceBindingKey(stableFlagKey(flags, resolved));
        if (episode != null) playback.setEpisodeUrl(episode.getUrl());
    }

    private static String stableFlagKey(List<Flag> flags, Flag target) {
        if (flags == null || target == null) return "";
        for (int i = 0; i < flags.size(); i++) {
            if (flags.get(i) == target) return Flag.stableKey(target, i);
        }
        return "";
    }

    private static Flag findStableFlag(List<Flag> flags, String key, String episodeUrl) {
        if (flags == null || TextUtils.isEmpty(key)) return null;
        Flag keyed = null;
        for (int i = 0; i < flags.size(); i++) {
            Flag flag = flags.get(i);
            if (flag != null && TextUtils.equals(key, stableFlagKey(flags, flag))) {
                keyed = flag;
                break;
            }
        }
        if (keyed != null && (TextUtils.isEmpty(episodeUrl)
                || containsEpisodeUrl(keyed, episodeUrl))) return keyed;
        Flag unique = null;
        for (Flag flag : flags) {
            if (!containsEpisodeUrl(flag, episodeUrl)) continue;
            if (unique != null) return null;
            unique = flag;
        }
        return unique;
    }

    private static boolean containsEpisodeUrl(Flag flag, String episodeUrl) {
        if (flag == null || TextUtils.isEmpty(episodeUrl) || flag.getEpisodes() == null) return false;
        for (Episode episode : flag.getEpisodes()) {
            if (episode != null && TextUtils.equals(episodeUrl, episode.getUrl())) return true;
        }
        return false;
    }

    private static Flag findFlag(List<Flag> flags, String name) {
        if (TextUtils.isEmpty(name)) return null;
        for (Flag flag : flags) if (flag != null && TextUtils.equals(name, flag.getFlag())) return flag;
        return null;
    }

    private static Flag firstFlag(List<Flag> flags) {
        for (Flag flag : flags) if (flag != null) return flag;
        return null;
    }

    private static Episode findMatchingEpisode(History history, Flag flag) {
        if (history == null || flag == null || flag.getEpisodes() == null) return null;
        for (Episode episode : flag.getEpisodes()) if (matchesEpisode(history, episode)) return episode;
        return null;
    }

    private static boolean matchesAnyEpisode(History item, List<Flag> flags) {
        if (item == null || flags == null || flags.isEmpty()) return false;
        for (Flag flag : flags) {
            if (flag == null || flag.getEpisodes() == null) continue;
            for (Episode episode : flag.getEpisodes()) if (matchesEpisode(item, episode)) return true;
        }
        return false;
    }

    private static boolean matchesEpisode(History item, Episode episode) {
        if (episode == null) return false;
        Episode saved = item.getEpisode();
        if (item.getTmdbEpisodeNumber() > 0 && episode.getTmdbEpisode() != null && episode.getTmdbEpisode().getNumber() > 0) {
            return episode.matchesNumber(saved);
        }
        String episodeUrl = item.getEpisodeUrl();
        if (!episodeUrl.isEmpty() && episodeUrl.equals(episode.getUrl())) return true;
        String remarks = item.getVodRemarks();
        boolean match = !remarks.isEmpty() && (remarks.equalsIgnoreCase(episode.getName()) || remarks.equals(episode.getDisplayName()));
        if (!match) {
            // 跨源同剧集名格式常不同（如「第2集」与「2. 众人在燕歌坊遇刺客」），严格比对会失败，
            // 退化到源自身旧记录。此处所有候选同属一个 tmdbId（同一部剧），集号即可靠同集判据，
            // 故统一交给 Episode.matchesNumber：优先使用已绑定的 TMDB 标准集号，旧记录再回退源集名提取。
            match = episode.matchesNumber(saved);
        }
        return match;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getWallPic() {
        return wallPic == null ? "" : wallPic;
    }

    public void setWallPic(String wallPic) {
        this.wallPic = wallPic;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodFlag() {
        return vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getSourceBindingKey() {
        return sourceBindingKey == null ? "" : sourceBindingKey;
    }

    public void setSourceBindingKey(String sourceBindingKey) {
        this.sourceBindingKey = sourceBindingKey;
    }

    public String getVodRemarks() {
        return vodRemarks == null ? "" : vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public boolean isRevSort() {
        return revSort;
    }

    public void setRevSort(boolean revSort) {
        this.revSort = revSort;
    }

    public boolean isRevPlay() {
        return revPlay;
    }

    public void setRevPlay(boolean revPlay) {
        this.revPlay = revPlay;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public long getOpening() {
        return opening;
    }

    public void setOpening(long opening) {
        this.opening = opening;
    }

    public long getEnding() {
        return ending;
    }

    public void setEnding(long ending) {
        this.ending = ending;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean getSpeedOverride() {
        return speedOverride;
    }

    public void setSpeedOverride(boolean speedOverride) {
        this.speedOverride = speedOverride;
    }

    public boolean hasUserSpeed() {
        return speedOverride || (speed > 0 && Math.abs(speed - 1.0f) > 0.001f);
    }

    public float getPlaybackSpeed(float defaultSpeed) {
        return hasUserSpeed() && speed > 0 ? speed : defaultSpeed;
    }

    public void setUserSpeed(float speed) {
        this.speed = speed;
        this.speedOverride = true;
    }

    public int getScale() {
        return scale == -1 ? -1 : VideoAspectMode.sanitize(scale);
    }

    public void setScale(int scale) {
        this.scale = VideoAspectMode.sanitize(scale);
    }

    public int getPlayer() {
        return player;
    }

    public int getPlayerOrDefault() {
        return PlayerSetting.resolvePlayer(player);
    }

    public boolean hasPlayer() {
        return PlayerSetting.isPlayer(player);
    }

    public void setPlayer(int player) {
        this.player = PlayerSetting.sanitizePlayer(player);
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public History cid(int cid) {
        setCid(cid);
        return this;
    }

    public String getTypeName() {
        return typeName == null ? "" : typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getArea() {
        return area == null ? "" : area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getActor() {
        return actor == null ? "" : actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getDirector() {
        return director == null ? "" : director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getYear() {
        return year == null ? "" : year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(int tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getMediaType() {
        return mediaType == null ? "" : mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getLegacyKey() {
        return legacyKey == null ? "" : legacyKey;
    }

    public void setLegacyKey(String legacyKey) {
        this.legacyKey = legacyKey;
    }

    public int getTmdbSeasonNumber() {
        return tmdbSeasonNumber;
    }

    public void setTmdbSeasonNumber(int tmdbSeasonNumber) {
        this.tmdbSeasonNumber = Math.max(-1, tmdbSeasonNumber);
    }

    public int getTmdbEpisodeNumber() {
        return tmdbEpisodeNumber;
    }

    public void setTmdbEpisodeNumber(int tmdbEpisodeNumber) {
        this.tmdbEpisodeNumber = Math.max(0, tmdbEpisodeNumber);
        if (this.tmdbEpisodeNumber == 0) this.tmdbSeasonNumber = 0;
    }

    public boolean setTmdbEpisodePosition(Episode episode) {
        TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
        int episodeNumber = tmdbEpisode == null ? 0 : tmdbEpisode.getNumber();
        return setTmdbEpisodePosition(tmdbEpisode, episodeNumber);
    }

    public boolean setTmdbEpisodePositionWithUnknownSeason(int episodeNumber) {
        return setTmdbEpisodePosition(null, Math.max(0, episodeNumber));
    }

    private boolean setTmdbEpisodePosition(TmdbEpisode tmdbEpisode, int episodeNumber) {
        int seasonNumber = tmdbEpisode == null ? -1 : tmdbEpisode.getSeasonNumber();
        return setTmdbEpisodePosition(seasonNumber, episodeNumber);
    }

    public boolean setTmdbEpisodePosition(int seasonNumber, int episodeNumber) {
        episodeNumber = Math.max(0, episodeNumber);
        seasonNumber = episodeNumber == 0 ? 0 : Math.max(-1, seasonNumber);
        if (tmdbSeasonNumber == seasonNumber && tmdbEpisodeNumber == episodeNumber) return false;
        tmdbSeasonNumber = seasonNumber;
        tmdbEpisodeNumber = episodeNumber;
        return true;
    }

    /**
     * 仅在本记录对应字段为空时补齐富集元数据（题材/地区/演员/主创/年份）。
     * 用于老记录重新播放时逐步补全，供观影报告统计使用。避免用空值覆盖已有数据。
     *
     * @return 是否有任一字段被补齐
     */
    public boolean enrichMeta(String typeName, String area, String actor, String director, String year) {
        boolean changed = false;
        if (getTypeName().isEmpty() && !android.text.TextUtils.isEmpty(typeName)) { this.typeName = typeName; changed = true; }
        if (getArea().isEmpty() && !android.text.TextUtils.isEmpty(area)) { this.area = area; changed = true; }
        if (getActor().isEmpty() && !android.text.TextUtils.isEmpty(actor)) { this.actor = actor; changed = true; }
        if (getDirector().isEmpty() && !android.text.TextUtils.isEmpty(director)) { this.director = director; changed = true; }
        if (getYear().isEmpty() && !android.text.TextUtils.isEmpty(year)) { this.year = year; changed = true; }
        return changed;
    }

    public String getSiteName() {
        if (getCid() != VodConfig.getCid()) return ResUtil.getString(R.string.history_other_config);
        if (SiteApi.PUSH.equals(getSiteKey())) return "";
        if (VodConfig.get().getSites().isEmpty()) return "";
        Site site = VodConfig.get().getSite(getSiteKey());
        return site.isEmpty() ? ResUtil.getString(R.string.history_source_unavailable) : site.getDisplayName();
    }

    public boolean isCurrentSourceAvailable() {
        if (getCid() != VodConfig.getCid()) return false;
        return SiteApi.PUSH.equals(getSiteKey()) || !VodConfig.get().getSite(getSiteKey()).isEmpty();
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        String[] parts = Objects.toString(getKey(), "").split(AppDatabase.SYMBOL);
        return parts.length > 1 ? parts[1] : "";
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        Episode episode = Episode.create(getVodRemarks(), getEpisodeUrl());
        if (getTmdbEpisodeNumber() > 0) {
            episode.setTmdbEpisode(new TmdbEpisode(getTmdbEpisodeNumber(), "", "", "", "", 0, 0, 0, getTmdbSeasonNumber()));
        }
        return episode;
    }

    /**
     * 是否为跨源聚合复制出的播放记录（key 已切到当前源，但进度沿用自 playbackSourceKey 指向的源）。
     * 跨源时线路名与剧集 URL 必然不同，判断"是否同一集"应改用集名而非线路/URL。
     */
    public History forPlaybackKey(String key, int cid) {
        History copy = copy();
        if (getCid() != cid || !TextUtils.equals(getKey(), key)) copy.playbackSourceKey = getKey();
        copy.setKey(key);
        copy.setCid(cid);
        return copy;
    }

    public boolean isCrossSourcePlayback() {
        return !TextUtils.isEmpty(playbackSourceKey);
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
    }

    public boolean hasPlaybackTime() {
        return getPosition() >= 0 && getDuration() > 0;
    }

    public String getPlaybackTimeText() {
        if (!hasPlaybackTime()) return "";
        long duration = Math.max(0, getDuration());
        long position = Math.max(0, Math.min(getPosition(), duration));
        return Util.timeMs(position) + " / " + Util.timeMs(duration);
    }

    public int getRevPlayText() {
        return isRevPlay() ? R.string.play_backward : R.string.play_forward;
    }

    public int getRevPlayHint() {
        return isRevPlay() ? R.string.play_backward_hint : R.string.play_forward_hint;
    }

    private boolean isPushHistory() {
        return key != null && key.startsWith(SiteApi.PUSH + AppDatabase.SYMBOL);
    }

    private boolean canMergeByName() {
        return !isPushHistory();
    }

    boolean shouldMerge(History item, boolean force) {
        if (!canMergeByName() || !item.canMergeByName()) return false;
        // 聚合模式下跨源合并由 tmdbId 统一接管：列表按 TMDB 身份折叠（HistoryDisplayPolicy），
        // 续播按 tmdbId 跨源找最新进度（findPlaybackByTmdb）。二者都要求各源记录保留在库中。
        // 而遗留的 name-merge 会 copyTo(this).delete() 物理删掉同名记录，且 delete() 在聚合模式下
        // 会按 tmdbId 级联删除（见 delete()），导致同剧的 TMDB 记录与原生记录互相覆盖、进度丢失。
        // 因此聚合生效时禁用 name-merge，改由 tmdbId 聚合处理，避免破坏跨源续播所需的多源记录。
        if (Setting.isHistoryAggregationEffective()) return false;
        if (!force && TextUtils.equals(getKey(), item.getKey())) return false;
        if (!force && TextUtils.equals(getKey(), item.playbackSourceKey)) return false;
        if (force) return true;
        if (getDuration() <= 0 || item.getDuration() <= 0) return false;
        return Math.abs(getDuration() - item.getDuration()) <= TimeUnit.MINUTES.toMillis(10);
    }

    private History copyTo(History item) {
        if (getOpening() > 0) item.setOpening(getOpening());
        if (getEnding() > 0) item.setEnding(getEnding());
        if (hasUserSpeed()) item.setUserSpeed(getSpeed());
        return this;
    }

    public boolean canSave() {
        return getPosition() > 0;
    }

    public boolean isNearEnding() {
        if (getPosition() <= 0 || getDuration() <= 0) return false;
        long threshold = Math.min(NEAR_END_MAX_MS, Math.max(NEAR_END_MIN_MS, getDuration() / 100));
        long remaining = getDuration() - getPosition();
        return remaining >= 0 && remaining <= threshold;
    }

    /**
     * 片尾时间是否已到，可判定本集播完。
     * ending 存在 History 上，主键不含集数（siteKey##vodId），因此整剧共享同一个值；
     * 而 duration 来自当前集。若某集明显更短（预告/彩蛋/番外，或源站给了错误时长），
     * 裸用 ending + position >= duration 会在刚开播、position 还很小时就成立，
     * 把用户从未看过的一集直接判为播完并跳走。这里要求：
     * 1) ending 不得超过本集时长所允许的片尾上限——用与设置端相同的
     *    Constant.getOpEdLimit(duration)，凡是本集根本设不出来的值即为跨集错配；
     * 2) 至少已播过一小段，排除开播瞬间误判。短视频按时长比例缩放该门槛，
     *    避免总时长很短时片尾永不触发。
     */
    public boolean isEndingReached(long position, long duration) {
        if (getEnding() <= 0 || duration <= 0 || position < 0) return false;
        if (getEnding() > Constant.getOpEdLimit(duration)) return false;
        if (position < Math.min(ENDING_MIN_PLAYED_MS, duration / 4)) return false;
        return getEnding() + position >= duration;
    }

    public void resetPlaybackPosition() {
        setPosition(C.TIME_UNSET);
        setDuration(C.TIME_UNSET);
    }

    public boolean canSync() {
        return System.currentTimeMillis() - getUpdateTime() > 5000;
    }

    public History merge() {
        merge(false);
        return this;
    }

    private History merge(boolean force) {
        if (!canMergeByName()) return this;
        return merge(findByName(getVodName()), force);
    }

    private History merge(List<History> items, boolean force) {
        for (History item : items) if (item.shouldMerge(this, force)) item.copyTo(this).delete();
        return this;
    }

    /**
     * 将历史记录的 key 迁移到新值。
     * 仅在 key 实际变化时删除旧 key，避免同 key 先删后写失败导致历史消失。
     * 迁移后立即 save 新 key，缩短「旧已删、新未写」窗口。
     *
     * <p>push（网盘/推送/本地文件）记录不迁移：它的 key 第二段就是那条播放地址本身
     * （见 {@link #getVodId()} 与 SiteApi#pushDetail），而从历史进入播放正是靠这一段反解
     * 出 vodId。普通站点的 vodId 是稳定的条目 id，迁移无害；push 迁移则会把记录改写成详情
     * 阶段解析出的另一条地址，那条地址往往带时效，下次从历史打开必然失败，用户只能回网盘
     * 重新找。按名合并（{@link #canMergeByName()}）已对 push 豁免，这里补齐同样的豁免。
     */
    public void replace(String key) {
        if (TextUtils.isEmpty(key) || TextUtils.equals(getKey(), key)) return;
        if (isPushHistory()) return;
        String previous = getKey();
        enrichTmdbId();
        updateTime = System.currentTimeMillis();
        setKey(key);
        History[] before = {null};
        boolean[] saved = {false};
        TmdbSeasonProgressStore.runInTransaction(() -> {
            List<PlaybackDeleteTombstone> tombstones = PlaybackDeleteTombstoneStore.snapshot();
            long deletedAt = Math.max(latestDeletion(tombstones, previous), latestDeletion(tombstones, key));
            if (!writeSurvivesDeletion(getCreateTime(), deletedAt)) return null;
            before[0] = find(getCid(), previous);
            if (!TextUtils.isEmpty(previous)) {
                AppDatabase.get().getTmdbSeasonProgressDao().replaceSourceHistoryKey(getCid(), previous, key);
                AppDatabase.get().getHistoryDao().delete(getCid(), previous);
                AppDatabase.get().getTrackDao().delete(previous);
            }
            AppDatabase.get().getHistoryDao().insertOrUpdate(this);
            TmdbSeasonProgressStore.write(this);
            saved[0] = true;
            return null;
        });
        if (saved[0] && recommendationSignalsChanged(before[0], this)) notifyChanged();
    }

    public History save(int cid) {
        return cid(cid).merge(true).save();
    }

    private void enrichTmdbId() {
        if (tmdbId > 0 || !Setting.isHistoryAggregationEffective()) return;
        String siteKey = getSiteKey();
        String vodId = getVodId();
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(vodId)) return;
        TmdbItem item = Setting.getTmdbMatchCache().find(siteKey, vodId, getVodName());
        if (item != null && item.getTmdbId() > 0) {
            this.tmdbId = item.getTmdbId();
            this.mediaType = item.getMediaType();
        }
    }

    public History save() {
        enrichTmdbId();
        updateTime = System.currentTimeMillis();
        History[] before = {null};
        boolean[] saved = {false};
        TmdbSeasonProgressStore.runInTransaction(() -> {
            long deletedAt = latestDeletion(PlaybackDeleteTombstoneStore.snapshot(), getKey());
            if (!writeSurvivesDeletion(getCreateTime(), deletedAt)) return null;
            before[0] = find(getCid(), getKey());
            if (mediaIdentityChanged(before[0], this)) {
                AppDatabase.get().getTmdbSeasonProgressDao().deleteBySource(getCid(), getKey());
            }
            AppDatabase.get().getHistoryDao().insertOrUpdate(this);
            TmdbSeasonProgressStore.write(this);
            saved[0] = true;
            return null;
        });
        if (saved[0] && recommendationSignalsChanged(before[0], this)) notifyChanged();
        return this;
    }

    public History delete() {
        return deleteRelated(false);
    }

    public History deleteDisplayItem() {
        return deleteRelated(Setting.isGlobalHistoryEnabled());
    }

    private History deleteRelated(boolean global) {
        boolean deleted;
        String identity = HistoryDisplayPolicy.tmdbIdentity(this);
        if (global && identity.contains(":season:") && Setting.isHistoryAggregationEffective()) {
            String mediaType = identity.substring(0, identity.indexOf(':'));
            int seasonNumber = getTmdbSeasonNumber();
            Set<Integer> cids = seasonDeleteCids(
                    AppDatabase.get().getHistoryDao().findByTmdbIdentity(mediaType, getTmdbId()),
                    AppDatabase.get().getTmdbSeasonProgressDao().findAll(),
                    mediaType, getTmdbId(), seasonNumber, getCid());
            deleted = false;
            for (Integer cid : cids) {
                History target = copy();
                target.setCid(cid);
                deleted |= PlaybackProgressWriter.deleteFromUser(target).affected > 0;
            }
            if (deleted) notifyChanged();
            return this;
        }
        List<History> relatedItems = Collections.emptyList();
        if (!identity.isEmpty() && !identity.startsWith("source:") && Setting.isHistoryAggregationEffective()) {
            String mediaType = identity.substring(0, identity.indexOf(':'));
            relatedItems = global
                    ? AppDatabase.get().getHistoryDao().findByTmdbIdentity(mediaType, getTmdbId())
                    : AppDatabase.get().getHistoryDao().findByTmdbIdentity(getCid(), mediaType, getTmdbId());
            if (!relatedItems.isEmpty() && identity.contains(":season:")) {
                List<History> seasonItems = new ArrayList<>();
                for (History item : relatedItems) {
                    if (identity.equals(HistoryDisplayPolicy.tmdbIdentity(item))) seasonItems.add(item);
                }
                relatedItems = seasonItems;
            }
        }
        if (!relatedItems.isEmpty()) {
            deleted = false;
            for (History item : relatedItems) {
                deleted |= PlaybackProgressWriter.deleteFromUser(item).affected > 0;
            }
        } else {
            deleted = PlaybackProgressWriter.deleteFromUser(this).affected > 0;
        }
        if (deleted) notifyChanged();
        return this;
    }

    private static boolean mediaIdentityChanged(History before, History after) {
        if (before == null || after == null) return false;
        return before.getTmdbId() != after.getTmdbId()
                || !TmdbSeasonProgress.normalizeMediaType(before.getMediaType()).equals(
                TmdbSeasonProgress.normalizeMediaType(after.getMediaType()));
    }

    static boolean writeSurvivesDeletion(long updatedAt, long deletedAt) {
        return deletedAt <= 0 || updatedAt > deletedAt;
    }

    private long latestDeletion(List<PlaybackDeleteTombstone> tombstones, String historyKey) {
        String value = Objects.toString(historyKey, "");
        String[] parts = value.split(AppDatabase.SYMBOL);
        String siteKey = parts.length > 0 ? parts[0] : "";
        String vodId = parts.length > 1 ? parts[1] : "";
        return PlaybackDeleteTombstoneStore.latest(tombstones, "", getCid(), value, siteKey, vodId,
                getMediaType(), getTmdbId(), getTmdbSeasonNumber());
    }

    static Set<Integer> seasonDeleteCids(
            List<History> histories,
            List<TmdbSeasonProgress> progress,
            String mediaType,
            int tmdbId,
            int seasonNumber,
            int currentCid) {
        Set<Integer> result = new HashSet<>();
        if (currentCid >= 0) result.add(currentCid);
        String normalizedMediaType = TmdbSeasonProgress.normalizeMediaType(mediaType);
        if (histories != null) {
            for (History history : histories) {
                if (history != null && history.getTmdbId() == tmdbId
                        && normalizedMediaType.equals(TmdbSeasonProgress.normalizeMediaType(history.getMediaType()))) {
                    result.add(history.getCid());
                }
            }
        }
        if (progress != null) {
            for (TmdbSeasonProgress snapshot : progress) {
                if (snapshot != null && snapshot.tmdbId == tmdbId
                        && snapshot.seasonNumber == seasonNumber
                        && normalizedMediaType.equals(TmdbSeasonProgress.normalizeMediaType(snapshot.mediaType))) {
                    result.add(snapshot.cid);
                }
            }
        }
        return result;
    }

    private static void notifyChanged() {
        App.post(HISTORY_REFRESH, HISTORY_REFRESH_DEBOUNCE);
    }

    static boolean recommendationSignalsChanged(History before, History after) {
        if (after == null || TextUtils.isEmpty(after.getVodName())) return false;
        if (before == null) return true;
        return before.getCid() != after.getCid()
                || !Objects.equals(before.getKey(), after.getKey())
                || !Objects.equals(before.getVodName(), after.getVodName())
                || !Objects.equals(before.getTypeName(), after.getTypeName())
                || !Objects.equals(before.getArea(), after.getArea())
                || !Objects.equals(before.getActor(), after.getActor())
                || !Objects.equals(before.getDirector(), after.getDirector())
                || !Objects.equals(before.getYear(), after.getYear());
    }

    public History deleteAndSync() {
        return deleteDisplayItem();
    }

    public void findEpisode(List<Flag> flags) {
        if (flags.isEmpty()) return;
        setVodFlag(flags.get(0).getFlag());
        if (!flags.get(0).getEpisodes().isEmpty()) {
            Episode episode = flags.get(0).getEpisodes().get(0);
            setVodRemarks(episode.getName());
            setEpisodeUrl(episode.getUrl());
            setTmdbEpisodePosition(episode);
        }
        if (!canMergeByName()) return;
        for (History item : findByName(getVodName())) {
            if (getPosition() > 0) break;
            for (Flag flag : flags) {
                Episode episode = flag.find(item.getEpisode(), true);
                if (episode == null) continue;
                item.copyTo(this);
                setVodFlag(flag.getFlag());
                setPosition(item.getPosition());
                setVodRemarks(episode.getName());
                setEpisodeUrl(episode.getUrl());
                if (episode.getTmdbEpisode() != null) setTmdbEpisodePosition(episode);
                else {
                    setTmdbSeasonNumber(item.getTmdbSeasonNumber());
                    setTmdbEpisodeNumber(item.getTmdbEpisodeNumber());
                }
                break;
            }
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof History it)) return false;
        return Objects.equals(getDisplayIdentity(), it.getDisplayIdentity());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDisplayIdentity());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean isSameItem(History other) {
        return equals(other);
    }

    public String getDisplayIdentity() {
        return TextUtils.isEmpty(displayIdentity) ? getKey() : displayIdentity;
    }

    public void setDisplayIdentity(String displayIdentity) {
        this.displayIdentity = displayIdentity;
    }

    @Override
    public boolean isSameContent(History other) {
        return other != null
                && Objects.equals(getVodName(), other.getVodName())
                && Objects.equals(getVodPic(), other.getVodPic())
                && Objects.equals(getWallPic(), other.getWallPic())
                && Objects.equals(getVodFlag(), other.getVodFlag())
                && Objects.equals(getVodRemarks(), other.getVodRemarks())
                && Objects.equals(getEpisodeUrl(), other.getEpisodeUrl())
                && getPosition() == other.getPosition()
                && getDuration() == other.getDuration()
                && getCreateTime() == other.getCreateTime();
    }
}
