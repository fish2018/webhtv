package com.fongmi.android.tv.ui.helper;

import android.app.Activity;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonSegment;
import com.fongmi.android.tv.bean.TmdbSeasonScope;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.AiRecommendationService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleLearningStore;
import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolution;
import com.fongmi.android.tv.title.MediaTitleResolver;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbDetailCache;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TMDB 数据适配器
 *
 * 负责在 VideoActivity 的 TMDB 模式下：
 * 1. 根据视频名称自动搜索匹配 TMDB 条目
 * 2. 加载详情、演员、简介等元数据
 * 3. 把元数据写回 {@link Vod} 并通过 {@link RefreshEvent#vod(Vod)} 推送到 UI
 *
 * 该类位于 src/main，被 mobile / leanback 两个 flavor 共享，因此只依赖
 * 两端都存在的事件机制，不直接操作各自布局生成的 binding 字段。
 */
public class TmdbUIAdapter {

    private static final long VOD_REFRESH_COALESCE_MS = 240;
    private static final long TMDB_STARTUP_BACKGROUND_DELAY_MS = 1200;
    // 选集是首屏内容，不能和推荐/个性化一起排在 1200ms 之后：用户盯着的就是它。
    // 集数元数据立刻发起（磁盘缓存命中时几乎瞬时），推荐等仍延后给首帧让路。
    private static final long TMDB_STARTUP_EPISODE_DELAY_MS = 0;
    private static final int MAX_CACHED_SEASONS = 24;

    private final Activity activity;
    private final TmdbService tmdbService;
    private final TmdbMatcher tmdbMatcher;
    private final TmdbConfig tmdbConfig;
    private final Runnable pendingVodRefresh = this::dispatchPendingVodRefresh;
    private final TmdbDetailPrefetch detailPrefetch;
    private final Task.Scope backgroundTasks;
    private final Task.Scope episodeTasks;
    private ListenableFuture<TmdbDetailPrefetch.Result> activePrefetch;

    private TmdbItem tmdbItem;
    private JsonObject tmdbDetail;
    private List<TmdbPerson> tmdbCast;
    private List<TmdbItem> recommendations;
    private List<TmdbVideo> relatedVideos;
    private List<TmdbItem> personalTmdbRecommendations;
    private List<TmdbItem> personalDoubanRecommendations;
    private List<TmdbItem> personalAiRecommendations;
    private PersonalRecommendationService.RecommendationPage personalTmdbPage;
    private PersonalRecommendationService.RecommendationPage personalDoubanPage;
    private PersonalRecommendationService.RecommendationPage personalAiPage;
    private Vod vod;
    private Flag activeFlag;
    private volatile int sourceSeasonNumber = -1;
    private volatile TmdbSeasonResolver.Resolution seasonResolution;
    private List<SeasonOption> seasonOptions = List.of();
    private List<Integer> explicitSourceSeasons = List.of();
    private int requestSeasonNumber = -1;
    private int titleSeasonNumber = -1;
    private String sourceCacheTitle = "";
    private String activeFlagKey = "";
    private TmdbEpisodeInfo episodeInfo;
    private int recommendationPage;
    private boolean recommendationHasMore;
    private boolean recommendationLoading;
    private boolean personalTmdbLoading;
    private boolean personalDoubanLoading;
    private boolean personalRefreshLoading;
    private boolean personalAiLoading;
    private boolean relatedVideoLoading;
    private String relatedVideoContextKey = "";
    private boolean loaded;
    private volatile boolean episodeMetadataLoaded;

    private volatile int loadGeneration;
    private volatile int episodeMetadataGeneration;
    private volatile int relatedVideoGeneration;
    private final Object episodeMetadataLock = new Object();
    // 季级内存缓存：切季 / 切线路会反复要同一季的集数，磁盘缓存虽然命中但仍要读文件 +
    // 解析整季 JSON。缓存解析结果让重复访问零 IO。键为 tmdbId|mediaType|season。
    private final Map<String, List<TmdbEpisode>> seasonEpisodeCache = new ConcurrentHashMap<>();
    private volatile int pendingVodRefreshGeneration;
    private volatile Vod pendingVodRefreshVod;
    private final java.util.EnumSet<RefreshEvent.Type> pendingVodRefreshTypes = java.util.EnumSet.noneOf(RefreshEvent.Type.class);
    private Runnable pendingStartupBackgroundLoads;
    private Runnable pendingStartupEpisodeLoad;
    private PersonalAiUpdateListener personalAiUpdateListener;

    public interface LoadMoreCallback {
        void onLoaded(boolean changed);
    }

    public interface PersonalAiUpdateListener {
        void onPersonalAiRecommendationsUpdated();
    }

    public TmdbUIAdapter(Activity activity) {
        this.activity = activity;
        this.tmdbService = new TmdbService();
        this.tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        this.tmdbMatcher = new TmdbMatcher(tmdbService, tmdbConfig);
        this.backgroundTasks = new Task.Scope(Task.recommendationExecutor());
        // 选集元数据独立线程池：recommendationExecutor 只有 3 条线程，推荐 / 个性化 / AI 推荐
        // 都挤在里面。原先 1200ms 延迟天然把选集和它们错开了，现在选集不再延迟，必须换到
        // largeExecutor(20)，否则选集会排在推荐后面，反而更慢。
        this.episodeTasks = new Task.Scope(Task.largeExecutor());
        this.detailPrefetch = new TmdbDetailPrefetch(Task.recommendationExecutor());
    }

    public boolean isReady() {
        return tmdbConfig.isReady();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isEpisodeMetadataLoaded() {
        return episodeMetadataLoaded;
    }

    public void setPersonalAiUpdateListener(PersonalAiUpdateListener listener) {
        this.personalAiUpdateListener = listener;
    }

    public void setActiveFlag(Flag flag) {
        activeFlag = flag;
        int index = flagIndex(vod == null ? null : vod.getFlags(), flag);
        String nextKey = flagKey(flag, index);
        if (vod != null) captureActiveFlagSeasonEvidence(vod);
        if (!nextKey.equals(activeFlagKey)) {
            activeFlagKey = nextKey;
            if (loaded && vod != null && tmdbItem != null && tmdbDetail != null) refreshEpisodeMetadataAfterBinding();
        }
    }

    public static String flagKey(Flag flag, int index) {
        return Flag.stableKey(flag, index);
    }

    public static boolean isFlagKey(String value) {
        if (TextUtils.isEmpty(value)) return false;
        int separator = value.lastIndexOf('#');
        if (separator <= 0 || separator == value.length() - 1) return false;
        try {
            return Integer.parseInt(value.substring(separator + 1)) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String activeFlagKey(Flag flag) {
        return flag != null && flag == activeFlag ? activeFlagKey : "";
    }

    public static int flagIndex(List<Flag> flags, Flag target) {
        if (flags == null || target == null) return -1;
        for (int i = 0; i < flags.size(); i++) if (flags.get(i) == target) return i;
        return flags.indexOf(target);
    }

    public static Flag selectPlaybackFlag(List<Flag> flags, String requestedFlagKey,
                                          String requestedEpisodeUrl, String requestedFlagName) {
        if (flags == null || flags.isEmpty()) return null;
        if (isFlagKey(requestedFlagKey)) {
            Flag keyed = null;
            for (int i = 0; i < flags.size(); i++) {
                if (TextUtils.equals(requestedFlagKey, flagKey(flags.get(i), i))) {
                    keyed = flags.get(i);
                    break;
                }
            }
            if (keyed != null && (TextUtils.isEmpty(requestedEpisodeUrl)
                    || containsEpisodeUrl(keyed, requestedEpisodeUrl))) return keyed;
            return uniqueFlagForEpisodeUrl(flags, requestedEpisodeUrl);
        }
        if (!TextUtils.isEmpty(requestedEpisodeUrl)) {
            for (Flag flag : flags) {
                for (Episode episode : flag.getEpisodes()) {
                    if (TextUtils.equals(requestedEpisodeUrl, episode.getUrl())) return flag;
                }
            }
        }
        for (Flag flag : flags) {
            if (!TextUtils.isEmpty(requestedFlagName)
                    && TextUtils.equals(requestedFlagName, flag.getFlag())) return flag;
        }
        return null;
    }

    private static Flag uniqueFlagForEpisodeUrl(List<Flag> flags, String episodeUrl) {
        if (TextUtils.isEmpty(episodeUrl)) return null;
        Flag match = null;
        for (Flag flag : flags) {
            if (!containsEpisodeUrl(flag, episodeUrl)) continue;
            if (match != null) return null;
            match = flag;
        }
        return match;
    }

    private static boolean containsEpisodeUrl(Flag flag, String episodeUrl) {
        if (flag == null || TextUtils.isEmpty(episodeUrl)) return false;
        for (Episode episode : flag.getEpisodes()) {
            if (TextUtils.equals(episodeUrl, episode.getUrl())) return true;
        }
        return false;
    }

    public record FlagSeasonBinding(String flagKey, TmdbSeasonScope scope) {
    }

    public static Map<Integer, List<String>> projectSourceFlags(List<FlagSeasonBinding> bindings) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        if (bindings == null) return result;
        for (FlagSeasonBinding binding : bindings) {
            if (binding == null || TextUtils.isEmpty(binding.flagKey()) || binding.scope() == null
                    || !binding.scope().isKnown()) continue;
            for (Integer season : binding.scope().getSeasons()) {
                if (season == null || season < 0) continue;
                result.computeIfAbsent(season, ignored -> new ArrayList<>()).add(binding.flagKey());
            }
        }
        return result;
    }

    public TmdbItem getTmdbItem() {
        return tmdbItem;
    }

    public JsonObject getTmdbDetail() {
        return tmdbDetail;
    }

    public int getSourceSeasonNumber() {
        return sourceSeasonNumber;
    }

    public TmdbSeasonResolver.Resolution getSeasonResolution() {
        return seasonResolution;
    }

    public List<SeasonOption> getSeasonOptions() {
        return seasonOptions == null ? List.of() : List.copyOf(seasonOptions);
    }

    public boolean hasSeasonOptions() {
        return tmdbItem != null && tmdbItem.isTv() && seasonOptions != null && !seasonOptions.isEmpty();
    }

    public String getSourceTitleForAiAnalysis() {
        if (!TextUtils.isEmpty(sourceCacheTitle)) return sourceCacheTitle;
        return vod == null ? "" : vod.getName();
    }

    public Map<Integer, Integer> getSeasonEpisodeCounts() {
        Map<Integer, Integer> counts = new java.util.LinkedHashMap<>();
        for (SeasonOption option : getSeasonOptions()) counts.put(option.getSeasonNumber(), option.getEpisodeCount());
        return counts;
    }

    public boolean applyValidatedFlatSeasonMapping() {
        if (vod == null || tmdbItem == null || !tmdbItem.isTv() || seasonOptions == null || seasonOptions.size() <= 1 || activeFlag == null) return false;
        List<Integer> seasons = new ArrayList<>();
        Map<Integer, Integer> counts = new java.util.LinkedHashMap<>();
        for (SeasonOption option : seasonOptions) {
            seasons.add(option.getSeasonNumber());
            counts.put(option.getSeasonNumber(), option.getEpisodeCount());
        }
        boolean applicable = EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(
                sourceEpisodeNumbers(activeFlag), seasons, counts).size() > 1;
        return applicable && updateSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE);
    }
    public String getPosterUrl() {
        return TmdbImageSelector.poster(tmdbDetail, tmdbConfig.getImageBase(), tmdbItem == null ? "" : tmdbItem.getPosterUrl());
    }

    public List<TmdbPerson> getCast() {
        return tmdbCast == null ? new ArrayList<>() : tmdbCast;
    }

    public List<TmdbItem> getPersonalTmdbRecommendations() {
        return getPersonalRecommendations(personalTmdbRecommendations);
    }

    public void removeRecommendation(TmdbItem item) {
        removeRecommendationFrom(recommendations, item);
        removeRecommendationFrom(personalTmdbRecommendations, item);
        removeRecommendationFrom(personalDoubanRecommendations, item);
        removeRecommendationFrom(personalAiRecommendations, item);
    }

    public List<TmdbItem> getPersonalDoubanRecommendations() {
        return getPersonalRecommendations(personalDoubanRecommendations);
    }

    public List<TmdbItem> getPersonalAiRecommendations() {
        return getPersonalRecommendations(personalAiRecommendations);
    }

    private List<TmdbItem> getPersonalRecommendations(List<TmdbItem> personalRecommendations) {
        if (personalRecommendations == null || personalRecommendations.isEmpty()) return new ArrayList<>();
        List<TmdbItem> items = new ArrayList<>();
        for (TmdbItem item : personalRecommendations) {
            if (containsRecommendation(items, item)) continue;
            items.add(item);
        }
        return items;
    }

    /**
     * Invalidate callbacks from the previous detail page before starting a new crawler request.
     */
    public void beginDetailRequest() {
        resetLoadState();
        backgroundTasks.cancelAll();
        episodeTasks.cancelAll();
        // 季缓存在这里清而不是在 resetLoadState 里：本方法由 getDetail() 在 prefetch 之前调用，
        // 清完紧接着的预热会重新填。放在 resetLoadState 会被 load() 冲掉预热结果；不清则
        // getDetail(refresh=true) 这类强制刷新会被内存缓存挡住，拿到陈旧集数。
        seasonEpisodeCache.clear();
        cancelActivePrefetch();
        detailPrefetch.cancel();
    }

    public void release() {
        resetLoadState();
        backgroundTasks.close();
        episodeTasks.close();
        cancelActivePrefetch();
        detailPrefetch.cancel();
    }

    /**
     * Prefetch core TMDB detail for a known item without mutating or publishing a Vod.
     */
    public void prefetch(TmdbItem item) {
        if (item == null || !isReady()) return;
        long start = System.currentTimeMillis();
        // Intent 在主线程读：本方法由 prefetchDirectTmdbDetail 在主线程调用，而 Intent 内部是
        // 非线程安全的 Bundle，主线程还会 putExtras/removeExtra 改它。算好季号再传进后台任务。
        int intentSeason = intentSeasonNumber();
        TmdbDetailPrefetch.StartResult started = detailPrefetch.start(item, () -> {
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            TmdbItem loadedItem = normalizeLoadedItem(item, detail);
            List<TmdbPerson> cast = tmdbService.cast(detail, tmdbConfig);
            SpiderDebug.log("tmdb-prefetch", "finish cost=%dms media=%s id=%d", System.currentTimeMillis() - start, loadedItem.getMediaType(), loadedItem.getTmdbId());
            // 选集卡片要的是 season/episodes，detail 里没有，所以顺手预热最可能的那一季。
            // 必须另起任务：这个 future 完成才会触发 loadDetailSync，串在里面等于用选集
            // 预热延后核心详情上屏。
            warmLikelySeasonAsync(loadedItem, detail, intentSeason);
            return new TmdbDetailPrefetch.Result(loadedItem, detail, cast);
        });
        if (started == null) {
            SpiderDebug.log("tmdb-prefetch", "skip invalid media=%s id=%d", item.getMediaType(), item.getTmdbId());
            return;
        }
        SpiderDebug.log("tmdb-prefetch", "%s media=%s id=%d", prefetchStateText(started.getState()), item.getMediaType(), item.getTmdbId());
        if (started.getState() != TmdbDetailPrefetch.StartState.REUSED) logPrefetchFailure(started.getFuture(), item, start);
    }

    /**
     * 预热最可能用到的那一季，跑在独立任务里，不阻塞 prefetch future。
     *
     * 预热可能与随后的 loadEpisodeTitlesAsync 并发请求同一季，两者都 miss 时会重复读一次
     * 磁盘/网络。这是有意接受的：预热猜错季本来也会浪费一次，为去重引入 per-key future
     * 不划算，且 TmdbEpisode 全是 final 字段、缓存值用 List.copyOf，重复写入不会产生脏数据。
     */
    private void warmLikelySeasonAsync(TmdbItem item, JsonObject detail, int intentSeason) {
        if (item == null || !item.isTv() || detail == null) return;
        int target = likelySeasonNumber(detail, intentSeason);
        if (target < 0) return;
        // 走 episodeTasks 而非裸 Task.submitLarge：换源 / 销毁时能随 generation 一起取消，
        // 不会在后台继续跑并往缓存里写已经没人要的数据。
        episodeTasks.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                List<TmdbEpisode> episodes = seasonEpisodes(item, target);
                SpiderDebug.log("tmdb-prefetch", "season warm season=%d count=%d cost=%dms", target, episodes.size(), System.currentTimeMillis() - start);
            } catch (CancellationException ignored) {
                // 预热是纯优化，取消无需处理。
            } catch (Throwable e) {
                SpiderDebug.log("tmdb-prefetch", "season warm failed error=%s", e.getMessage());
            }
        });
    }

    /**
     * 猜测预热哪一季。prefetch 早于 captureSourceSeason，季解析还没跑，所以只能用 Intent
     * 指定的季号，没有时退化为唯一正片季（单季剧最常见）。返回 < 0 表示证据不足，不猜。
     */
    static int likelySeasonNumber(JsonObject detail, int intentSeason) {
        List<SeasonOption> options = parseSeasonOptions(detail);
        if (options.isEmpty()) return -1;
        int target = intentSeason;
        if (target < 0) {
            List<Integer> ordinary = new ArrayList<>();
            for (SeasonOption option : options) if (option.getSeasonNumber() > 0) ordinary.add(option.getSeasonNumber());
            if (ordinary.size() != 1) return -1;
            target = ordinary.get(0);
        }
        for (SeasonOption option : options) if (option.getSeasonNumber() == target) return target;
        return -1;
    }

    private int intentSeasonNumber() {
        if (activity == null || activity.getIntent() == null) return -1;
        int requested = activity.getIntent().getIntExtra("tmdb_play_season_number", -1);
        if (requested >= 0) return requested;
        return EpisodeSeasonPolicy.resolveSourceSeason(activityIntentTitle());
    }

    private String prefetchStateText(TmdbDetailPrefetch.StartState state) {
        if (state == TmdbDetailPrefetch.StartState.REUSED) return "reuse";
        if (state == TmdbDetailPrefetch.StartState.REPLACED) return "replace";
        return "start";
    }

    private void logPrefetchFailure(ListenableFuture<TmdbDetailPrefetch.Result> future, TmdbItem item, long start) {
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(TmdbDetailPrefetch.Result result) {
            }

            @Override
            public void onFailure(Throwable error) {
                String state = error instanceof CancellationException ? "cancelled" : "failed";
                SpiderDebug.log("tmdb-prefetch", "%s cost=%dms media=%s id=%d error=%s", state, System.currentTimeMillis() - start, item.getMediaType(), item.getTmdbId(), error == null ? "unknown" : error.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * 直接指定 TMDB 条目并加载详情。
     */
    public void load(TmdbItem item, Vod vod) {
        if (item == null) return;
        // resetLoadState 会清掉 sourceCacheTitle，而手动换条目时 vod.getName() 已被
        // enrichVod 改写成上一个 TMDB 标题。先留住站源标题，季度绑定的键才能跨会话一致。
        String sourceTitle = sourceCacheTitle;
        int generation = resetLoadState();
        captureSourceSeason(vod, sourceTitle);
        cancelActivePrefetch();
        this.tmdbItem = item;
        saveMatch(vod, item);
        TmdbDetailCache.Entry cached = takeTmdbDetailCache(item);
        if (cached != null) {
            detailPrefetch.cancel();
            backgroundTasks.submit(() -> loadDetailSync(vod, cached.getItem(), cached.getDetail(), cached.getCast(), generation));
            return;
        }
        ListenableFuture<TmdbDetailPrefetch.Result> prefetched = detailPrefetch.take(item);
        if (prefetched != null) {
            attachPrefetch(prefetched, vod, generation);
            return;
        }
        loadDetail(vod, item, generation);
    }

    private void attachPrefetch(ListenableFuture<TmdbDetailPrefetch.Result> prefetched, Vod vod, int generation) {
        setActivePrefetch(prefetched);
        SpiderDebug.log("tmdb-prefetch", "attach state=%s", prefetched.isDone() ? "done" : "running");
        Futures.addCallback(prefetched, new FutureCallback<>() {
            @Override
            public void onSuccess(TmdbDetailPrefetch.Result result) {
                clearActivePrefetch(prefetched);
                if (result == null || !isCurrentGeneration(generation)) return;
                backgroundTasks.submit(() -> loadDetailSync(vod, result.getItem(), result.getDetail(), result.getCast(), generation));
            }

            @Override
            public void onFailure(Throwable error) {
                clearActivePrefetch(prefetched);
                if (!isCurrentGeneration(generation)) return;
                notifyLoadComplete(vod, generation);
            }
        }, MoreExecutors.directExecutor());
    }

    private synchronized void setActivePrefetch(ListenableFuture<TmdbDetailPrefetch.Result> future) {
        activePrefetch = future;
    }

    private synchronized void clearActivePrefetch(ListenableFuture<TmdbDetailPrefetch.Result> expected) {
        if (activePrefetch == expected) activePrefetch = null;
    }

    private void cancelActivePrefetch() {
        ListenableFuture<TmdbDetailPrefetch.Result> future;
        synchronized (this) {
            future = activePrefetch;
            activePrefetch = null;
        }
        if (future != null) future.cancel(true);
    }

    /**
     * Load TMDB data from the detail payload already consumed by fast playback.
     */
    public void load(TmdbItem item, Vod vod, TmdbDetailCache.Entry cached) {
        if (item == null) return;
        if (cached == null) {
            load(item, vod);
            return;
        }
        String sourceTitle = sourceCacheTitle;
        int generation = resetLoadState();
        captureSourceSeason(vod, sourceTitle);
        cancelActivePrefetch();
        detailPrefetch.cancel();
        this.tmdbItem = item;
        saveMatch(vod, item);
        backgroundTasks.submit(() -> loadDetailSync(vod, cached.getItem(), cached.getDetail(), cached.getCast(), generation));
    }

    public void rememberManualMatch(Vod vod, TmdbItem item) {
        saveTitleLearning(vod, item);
        saveManualMatch(vod, item);
    }

    /**
     * 根据视频名称自动搜索匹配并加载详情。
     *
     * @param videoName 视频标题（通常取详情页解析出的名称）
     * @param vod       待增强的 Vod；增强后通过事件推回 UI
     */
    public void autoMatch(String videoName, Vod vod) {
        autoMatch(videoName, vod, "");
    }

    public void autoMatch(String videoName, Vod vod, String searchKeyword) {
        int generation = resetLoadState();
        captureSourceSeason(vod, videoName);
        cancelActivePrefetch();
        detailPrefetch.cancel();
        if (SiteApi.PUSH.equals(cacheSiteKey(vod)) && !TmdbMatchPolicy.shouldAutoMatchPushTitle(videoName)) {
            SpiderDebug.log("tmdb", "skip push auto match: generic title=%s", videoName);
            notifyLoadComplete(vod, generation);
            return;
        }
        if (!isReady()) {
            SpiderDebug.log("tmdb", "skip auto match: config not ready");
            notifyLoadComplete(vod, generation);
            return;
        }
        if (TextUtils.isEmpty(videoName)) {
            notifyLoadComplete(vod, generation);
            return;
        }
        backgroundTasks.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                if (!isCurrentGeneration(generation)) return;
                TmdbItem matched = getCachedMatch(vod);
                if (matched != null) {
                    SpiderDebug.log("tmdb", "auto match cache hit title=%s cost=%dms", matched.getTitle(), System.currentTimeMillis() - start);
                    // 手动选择由用户拍板，分季变体过滤只针对自动匹配的误命中。
                    if (!isManualMatch(vod) && isCachedSplitSeasonMismatch(videoName, vod, matched)) {
                        SpiderDebug.log("tmdb", "auto match cache skipped split-season variant title=%s id=%d name=%s", matched.getTitle(), matched.getTmdbId(), videoName);
                        matched = null;
                    }
                }
                if (matched == null) {
                    long searchStart = System.currentTimeMillis();
                    matched = searchResolvedMatch(videoName, vod, searchKeyword);
                    SpiderDebug.log("tmdb", "auto match search cost=%dms hit=%s name=%s", System.currentTimeMillis() - searchStart, matched != null, videoName);
                }
                if (!isCurrentGeneration(generation)) return;
                if (matched == null) {
                    SpiderDebug.log("tmdb", "auto match miss name=%s total=%dms", videoName, System.currentTimeMillis() - start);
                    tmdbItem = null;
                    notifyLoadComplete(vod, generation);
                    return;
                }
                saveMatch(vod, matched);
                tmdbItem = matched;
                SpiderDebug.log("tmdb", "auto match ready title=%s total=%dms", matched.getTitle(), System.currentTimeMillis() - start);
                loadDetailSync(vod, matched, generation);
            } catch (Exception e) {
                SpiderDebug.log("tmdb", "auto match failed name=%s total=%dms error=%s", videoName, System.currentTimeMillis() - start, e.getMessage());
                if (!isCurrentGeneration(generation)) return;
                tmdbItem = null;
                notifyLoadComplete(vod, generation);
            }
        });
    }

    private TmdbItem searchResolvedMatch(String videoName, Vod vod, String searchKeyword) {
        MediaTitleRequest request = buildTitleRequest(videoName, vod, searchKeyword, false);
        MediaTitleResolver resolver = new MediaTitleResolver();
        MediaTitleResolution resolution = resolver.resolve(request);
        List<String> attempted = new ArrayList<>();
        int attempts = 0;
        for (String title : resolution.queryTitles()) {
            if (!addAttemptedTmdbQuery(attempted, title)) continue;
            TmdbItem item = tmdbMatcher.searchAndMatch(title, vod);
            if (item != null) return item;
            if (++attempts >= 4) break;
        }
        TmdbItem keywordMatch = searchKeywordMatch(searchKeyword, vod, attempted);
        if (keywordMatch != null) return keywordMatch;

        List<String> cleanedTitles = resolver.queryCleanedTitles(request, 4);
        SpiderDebug.log("tmdb", "auto match cleaned fallback raw=%s titles=%s", videoName, cleanedTitles);
        for (String title : cleanedTitles) {
            if (!addAttemptedTmdbQuery(attempted, title)) continue;
            TmdbItem item = tmdbMatcher.searchAndMatch(title, vod);
            if (item != null) return item;
        }
        MediaTitleRequest aiRequest = buildTitleRequest(videoName, vod, searchKeyword, true);
        MediaTitleResolution fallback = resolver.resolveWithAiFallback(aiRequest);
        SpiderDebug.log("tmdb", "auto match ai fallback source=%s raw=%s titles=%s", fallback.getSource(), videoName, fallback.queryTitles());
        for (String title : fallback.queryTitles()) {
            if (!addAttemptedTmdbQuery(attempted, title)) continue;
            TmdbItem item = tmdbMatcher.searchAndMatch(title, vod);
            if (item != null) return item;
        }
        return null;
    }

    private MediaTitleRequest buildTitleRequest(String videoName, Vod vod, String searchKeyword, boolean allowAi) {
        return MediaTitleRequest.builder()
                .siteKey(cacheSiteKey(vod))
                .vodId(cacheVodId(vod))
                .rawTitle(videoName)
                .rawRemarks(vod == null ? "" : vod.getRemarks())
                .searchKeyword(searchKeyword)
                .vodYear(vod == null ? "" : vod.getYear())
                .source(MediaTitleLearningExample.SOURCE_TMDB_AUTO)
                .allowAi(allowAi)
                .build();
    }

    private TmdbItem searchKeywordMatch(String searchKeyword, Vod vod, List<String> attempted) {
        if (TextUtils.isEmpty(searchKeyword) || !addAttemptedTmdbQuery(attempted, searchKeyword)) return null;
        TmdbItem item = tmdbMatcher.searchAndMatch(searchKeyword, vod);
        SpiderDebug.log("tmdb", "auto match search keyword=%s hit=%s", searchKeyword, item != null);
        return item;
    }

    private boolean addAttemptedTmdbQuery(List<String> attempted, String title) {
        String query = tmdbMatcher.cleanVideoName(title);
        if (TextUtils.isEmpty(query)) return false;
        for (String item : attempted) if (item.equalsIgnoreCase(query)) return false;
        attempted.add(query);
        return true;
    }

    public List<TmdbItem> search(String keyword) throws Exception {
        return search(keyword, null);
    }

    public List<TmdbItem> search(String keyword, Vod vod) throws Exception {
        return tmdbMatcher.search(keyword, vod);
    }

    public String cleanSearchQuery(String keyword) {
        return tmdbMatcher.cleanVideoName(keyword);
    }

    private void loadDetail(Vod vod, TmdbItem item, int generation) {
        if (item == null || !isReady()) {
            notifyLoadComplete(vod, generation);
            return;
        }
        backgroundTasks.submit(() -> loadDetailSync(vod, item, generation));
    }

    private TmdbDetailCache.Entry takeTmdbDetailCache(TmdbItem item) {
        if (activity == null || activity.getIntent() == null || item == null) return null;
        TmdbDetailCache.Entry cached = TmdbDetailCache.take(activity.getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), item);
        if (cached != null) SpiderDebug.log("tmdb", "detail core memory-cache hit title=%s media=%s id=%d", cached.getItem().getTitle(), cached.getItem().getMediaType(), cached.getItem().getTmdbId());
        return cached;
    }

    private int resetLoadState() {
        int generation = ++loadGeneration;
        episodeMetadataGeneration++;
        tmdbItem = null;
        tmdbDetail = null;
        tmdbCast = null;
        recommendations = null;
        relatedVideos = null;
        personalTmdbRecommendations = null;
        personalDoubanRecommendations = null;
        personalAiRecommendations = null;
        personalTmdbPage = null;
        personalDoubanPage = null;
        personalAiPage = null;
        vod = null;
        sourceSeasonNumber = -1;
        seasonResolution = null;
        seasonOptions = List.of();
        explicitSourceSeasons = List.of();
        requestSeasonNumber = -1;
        titleSeasonNumber = -1;
        sourceCacheTitle = "";
        episodeInfo = null;
        recommendationPage = 1;
        recommendationHasMore = false;
        recommendationLoading = false;
        personalTmdbLoading = false;
        personalDoubanLoading = false;
        personalRefreshLoading = false;
        personalAiLoading = false;
        relatedVideoLoading = false;
        relatedVideoContextKey = "";
        relatedVideoGeneration++;
        loaded = false;
        episodeMetadataLoaded = false;
        pendingVodRefreshVod = null;
        pendingVodRefreshGeneration = generation;
        App.removeCallbacks(pendingVodRefresh);
        if (pendingStartupBackgroundLoads != null) App.removeCallbacks(pendingStartupBackgroundLoads);
        pendingStartupBackgroundLoads = null;
        if (pendingStartupEpisodeLoad != null) App.removeCallbacks(pendingStartupEpisodeLoad);
        pendingStartupEpisodeLoad = null;
        return generation;
    }

    private void captureSourceSeason(Vod sourceVod, String sourceTitle) {
        requestSeasonNumber = activity == null || activity.getIntent() == null
                ? -1 : activity.getIntent().getIntExtra("tmdb_play_season_number", -1);
        String vodTitle = sourceVod == null ? "" : sourceVod.getName();
        sourceCacheTitle = selectSourceCacheTitle(sourceTitle, activityIntentTitle(), vodTitle);
        boolean pushSource = SiteApi.PUSH.equals(cacheSiteKey(sourceVod));
        titleSeasonNumber = pushSource
                ? EpisodeSeasonPolicy.resolveExplicitSourceSeason(
                sourceTitle,
                activityIntentTitle(),
                vodTitle,
                sourceVod == null ? "" : sourceVod.getRemarks())
                : EpisodeSeasonPolicy.resolveSourceSeason(
                sourceTitle,
                activityIntentTitle(),
                vodTitle,
                sourceVod == null ? "" : sourceVod.getRemarks());

        captureActiveFlagSeasonEvidence(sourceVod);
    }

    private void captureActiveFlagSeasonEvidence(Vod sourceVod) {
        Flag sourceFlag = activeFlagFor(sourceVod);
        List<Integer> explicit = explicitSourceSeasons(sourceFlag);
        List<Integer> metadata = new ArrayList<>();
        if (sourceFlag != null && sourceFlag.getEpisodes() != null) {
            for (Episode episode : sourceFlag.getEpisodes()) {
                TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
                if (tmdbEpisode != null && tmdbEpisode.getNumber() > 0) {
                    addExplicitSeason(metadata, tmdbEpisode.getSeasonNumber());
                }
            }
        }
        explicitSourceSeasons = List.copyOf(explicit);
        sourceSeasonNumber = requestSeasonNumber >= 0
                ? requestSeasonNumber
                : titleSeasonNumber >= 0
                ? titleSeasonNumber
                : explicit.size() == 1
                ? explicit.get(0)
                : explicit.isEmpty() && metadata.size() == 1 ? metadata.get(0) : -1;
    }

    private Flag activeFlagFor(Vod sourceVod) {
        if (sourceVod == null || sourceVod.getFlags() == null || sourceVod.getFlags().isEmpty()) return null;
        return resolveActiveFlag(sourceVod.getFlags(), activeFlag, activeFlagKey);
    }

    static Flag resolveActiveFlag(List<Flag> flags, Flag activeFlag, String activeFlagKey) {
        if (flags == null || flags.isEmpty()) return null;
        if (activeFlag != null) for (Flag flag : flags) if (flag == activeFlag) return flag;
        if (!TextUtils.isEmpty(activeFlagKey)) {
            for (int i = 0; i < flags.size(); i++) {
                if (TextUtils.equals(activeFlagKey, flagKey(flags.get(i), i))) return flags.get(i);
            }
        }
        return flags.size() == 1 ? flags.get(0) : null;
    }

    static List<Integer> explicitSourceSeasons(Flag flag) {
        List<Integer> seasons = new ArrayList<>();
        if (flag == null) return seasons;
        addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(flag.getFlag()));
        addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(flag.getShow()));
        if (flag.getEpisodes() != null) {
            for (Episode episode : flag.getEpisodes()) {
                addExplicitSeason(seasons, EpisodeSeasonPolicy.resolveExplicitSourceSeason(
                        episode == null ? "" : episode.getName()));
            }
        }
        return seasons;
    }

    static String selectSourceCacheTitle(String sourceTitle, String intentTitle, String vodTitle) {
        if (!TextUtils.isEmpty(sourceTitle)) return sourceTitle;
        if (!TextUtils.isEmpty(intentTitle)) return intentTitle;
        return vodTitle == null ? "" : vodTitle;
    }

    static boolean removeStaleSeasonBinding(
            TmdbSeasonMatchCache cache,
            String siteKey,
            String vodId,
            String sourceTitle,
            TmdbItem item) {
        return removeStaleSeasonBinding(cache, siteKey, vodId, sourceTitle, "", item);
    }

    static boolean removeStaleSeasonBinding(
            TmdbSeasonMatchCache cache,
            String siteKey,
            String vodId,
            String sourceTitle,
            String flagKey,
            TmdbItem item) {
        return cache != null
                && item != null
                && cache.removeIfMediaChanged(siteKey, vodId, sourceTitle, flagKey, item.getTmdbId(), item.getMediaType());
    }

    private static void addExplicitSeason(List<Integer> seasons, int season) {
        if (season >= 0 && !seasons.contains(season)) seasons.add(season);
    }

    private void resolveSeason(Vod sourceVod, TmdbItem item, JsonObject detail) {
        synchronized (Setting.class) {
        seasonOptions = parseSeasonOptions(detail);
        List<Integer> tmdbSeasons = new ArrayList<>();
        Map<Integer, Integer> seasonCounts = new HashMap<>();
        for (SeasonOption option : seasonOptions) {
            tmdbSeasons.add(option.getSeasonNumber());
            seasonCounts.put(option.getSeasonNumber(), option.getEpisodeCount());
        }
        TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
        Map<String, String> routeFingerprints = new LinkedHashMap<>();
        if (sourceVod != null && sourceVod.getFlags() != null) {
            for (int i = 0; i < sourceVod.getFlags().size(); i++) {
                Flag flag = sourceVod.getFlags().get(i);
                String key = flagKey(flag, i);
                routeFingerprints.put(key, sourceFingerprint(flag, key, seasonCounts));
            }
        }
        if (cache.pruneRouteBindings(cacheSiteKey(sourceVod), cacheVodId(sourceVod), routeFingerprints)) {
            Setting.putTmdbSeasonMatchCache(cache);
        }
        TmdbSeasonMatchCache.Entry manual = cache.find(
                cacheSiteKey(sourceVod),
                cacheVodId(sourceVod),
                sourceCacheTitle,
                activeFlagKey,
                item == null ? -1 : item.getTmdbId(),
                allowLegacyVodBinding(sourceVod));
        int manualTmdbEpisodeCount = manual == null || manual.getSeasonNumber() == null
                ? 0 : seasonCounts.getOrDefault(manual.getSeasonNumber(), 0);
        if (manual != null && !manual.isFresh(sourceFingerprint(sourceVod),
                sourceEpisodeCount(activeFlagFor(sourceVod)), manualTmdbEpisodeCount)) {
            cache.remove(cacheSiteKey(sourceVod), cacheVodId(sourceVod), sourceCacheTitle, activeFlagKey);
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb", "discard stale source shape source=%s tmdb=%d",
                    sourceCacheTitle, item == null ? -1 : item.getTmdbId());
            manual = null;
        }
        if (manual != null
                && manual.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_SEASON
                && (manual.getSeasonNumber() == null || !containsSeasonOption(manual.getSeasonNumber()))) {
            cache.remove(cacheSiteKey(sourceVod), cacheVodId(sourceVod), sourceCacheTitle, activeFlagKey);
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb", "discard removed season source=%s tmdb=%d season=%s",
                    sourceCacheTitle, item == null ? -1 : item.getTmdbId(), manual.getSeasonNumber());
            manual = null;
        }
        boolean validManualMulti = manual == null || manual.getMode() != TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE
                || manual.getSegments().isEmpty()
                ? hasSafeFlatSeasonMapping(activeFlagFor(sourceVod), tmdbSeasons, seasonCounts)
                : TmdbSeasonResolver.hasValidPersistedSegments(manual, tmdbSeasons, seasonCounts,
                sourceEpisodeCount(activeFlagFor(sourceVod)));
        if (manual != null && manual.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE
                && !validManualMulti) {
            cache.remove(cacheSiteKey(sourceVod), cacheVodId(sourceVod), sourceCacheTitle, activeFlagKey);
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb", "discard unsafe multi-slice source=%s tmdb=%d",
                    sourceCacheTitle, item == null ? -1 : item.getTmdbId());
            manual = null;
        }
        seasonResolution = TmdbSeasonResolver.resolve(
                requestSeasonNumber,
                manual,
                explicitSourceSeasons,
                titleSeasonNumber,
                tmdbSeasons,
                seasonCounts,
                sourceEpisodeCount(activeFlagFor(sourceVod)),
                sourceEpisodeNumbers(activeFlagFor(sourceVod)),
                explicitEpisodeSeasons(activeFlagFor(sourceVod)),
                !SiteApi.PUSH.equals(cacheSiteKey(sourceVod)));
        TmdbSeasonScope routeScope = seasonResolution.toScope();
        if (seasonResolution.getSource() == TmdbSeasonResolver.Source.REQUEST) {
            // intent 带来的 season 只是"进场时选中的那一季"，不该锁死整条线路的季度解析。
            // TmdbSeasonResolver.resolve 的第一个判断就用 requestSeason 短路返回 RESOLVED，
            // 于是分集富化走单季路径，只能按扁平集号在该季 1..N 里查；源站不分季的长番
            // （如航海王第 62 集）超出该季集数后一律匹配不到 TMDB。这里重算一次不带
            // requestSeason 的解析：本就该按季切片的源，改用切片结果，让 mapFlatEpisodeNumber
            // 把扁平集号映射到正确的季。
            TmdbSeasonResolver.Resolution unrequested = TmdbSeasonResolver.resolve(
                    -1, manual, explicitSourceSeasons, titleSeasonNumber, tmdbSeasons, seasonCounts,
                    sourceEpisodeCount(activeFlagFor(sourceVod)), sourceEpisodeNumbers(activeFlagFor(sourceVod)),
                    explicitEpisodeSeasons(activeFlagFor(sourceVod)),
                    !SiteApi.PUSH.equals(cacheSiteKey(sourceVod)));
            routeScope = unrequested.toScope();
            if (unrequested.getStatus() == TmdbSeasonResolver.Status.MULTI_SLICE) {
                seasonResolution = unrequested;
                SpiderDebug.log("tmdb", "adopt multi-slice over request season=%d source=%s",
                        requestSeasonNumber, sourceCacheTitle);
            }
        }
        Integer selected = seasonResolution.getSelectedSeason();
        sourceSeasonNumber = selected == null ? -1 : selected;
        Flag resolvedFlag = activeFlagFor(sourceVod);
        if (cache.recordRouteBinding(cacheSiteKey(sourceVod), cacheVodId(sourceVod), activeFlagKey,
                resolvedFlag == null ? "" : resolvedFlag.getFlag(),
                sourceFingerprint(resolvedFlag, activeFlagKey, seasonCounts), item == null ? -1 : item.getTmdbId(),
                item == null ? "" : item.getMediaType(), routeScope)) {
            Setting.putTmdbSeasonMatchCache(cache);
        }
        SpiderDebug.log("tmdb", "season resolve status=%s source=%s season=%d reason=%s",
                seasonResolution.getStatus(), seasonResolution.getSource(), sourceSeasonNumber, seasonResolution.getReason());
        }
    }

    private static boolean hasSafeFlatSeasonMapping(Flag flag, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        return flag != null && EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(
                sourceEpisodeNumbers(flag), seasons, seasonCounts).size() > 1;
    }

    public boolean applyManualSeason(int seasonNumber) {
        if (!containsSeasonOption(seasonNumber)) return false;
        return updateSeasonBinding(seasonNumber, TmdbSeasonMatchCache.Mode.MANUAL_SEASON);
    }

    public boolean keepOriginalEpisodeList() {
        return updateSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_FLAT);
    }

    public boolean clearManualSeasonBinding() {
        if (vod == null || tmdbItem == null) return false;
        synchronized (Setting.class) {
            TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
            cache.remove(cacheSiteKey(vod), cacheVodId(vod), sourceCacheTitle, activeFlagKey);
            Setting.putTmdbSeasonMatchCache(cache);
        }
        refreshEpisodeMetadataAfterBinding();
        return true;
    }

    private boolean updateSeasonBinding(Integer seasonNumber, TmdbSeasonMatchCache.Mode mode) {
        if (vod == null || tmdbItem == null || !tmdbItem.isTv() || TextUtils.isEmpty(sourceCacheTitle)) return false;
        if (mode == TmdbSeasonMatchCache.Mode.MANUAL_SEASON && (seasonNumber == null || !containsSeasonOption(seasonNumber))) return false;
        synchronized (Setting.class) {
            TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
            cache.put(
                cacheSiteKey(vod),
                cacheVodId(vod),
                sourceCacheTitle,
                activeFlagKey,
                tmdbItem.getTmdbId(),
                tmdbItem.getMediaType(),
                seasonNumber,
                mode,
                sourceFingerprint(vod),
                sourceEpisodeCount(activeFlagFor(vod)),
                seasonEpisodeCount(seasonNumber),
                    mode == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE ? multiSliceSegments() : List.of());
            Setting.putTmdbSeasonMatchCache(cache);
        }
        refreshEpisodeMetadataAfterBinding();
        return true;
    }

    private void refreshEpisodeMetadataAfterBinding() {
        if (vod == null || tmdbItem == null || tmdbDetail == null) return;
        int generation = loadGeneration;
        int metadataGeneration = ++episodeMetadataGeneration;
        Integer selectedSeason;
        synchronized (episodeMetadataLock) {
            clearEpisodeMetadata(activeFlagFor(vod));
            resolveSeason(vod, tmdbItem, tmdbDetail);
            episodeInfo = TmdbEpisodeInfo.from(tmdbItem.getMediaType(), tmdbDetail, sourceSeasonNumber);
            selectedSeason = currentEpisodeMetadataSeason();
        }
        episodeMetadataLoaded = false;
        if (selectedSeason != null || isMultiSliceResolution()) {
            loadEpisodeTitlesAsync(vod, tmdbItem, generation, metadataGeneration, selectedSeason);
        } else {
            finishEpisodeMetadataLoad(vod, generation, metadataGeneration, null);
        }
    }

    private boolean containsSeasonOption(int seasonNumber) {
        if (seasonOptions == null) return false;
        for (SeasonOption option : seasonOptions) if (option.getSeasonNumber() == seasonNumber) return true;
        return false;
    }

    private int seasonEpisodeCount(Integer seasonNumber) {
        if (seasonNumber == null || seasonOptions == null) return 0;
        for (SeasonOption option : seasonOptions) {
            if (option.getSeasonNumber() == seasonNumber) return option.getEpisodeCount();
        }
        return 0;
    }

    private List<TmdbSeasonSegment> multiSliceSegments() {
        if (seasonOptions == null || seasonOptions.isEmpty()) return List.of();
        List<Integer> seasons = new ArrayList<>();
        Map<Integer, Integer> counts = new java.util.LinkedHashMap<>();
        for (SeasonOption option : seasonOptions) seasons.add(option.getSeasonNumber());
        for (SeasonOption option : seasonOptions) counts.put(option.getSeasonNumber(), option.getEpisodeCount());
        return TmdbSeasonResolver.flatSeasonSegments(sourceEpisodeNumbers(activeFlagFor(vod)), seasons, counts);
    }

    private static boolean allowLegacyVodBinding(Vod sourceVod) {
        return sourceVod != null && sourceVod.getFlags() != null && sourceVod.getFlags().size() <= 1;
    }

    static int sourceEpisodeCount(Flag flag) {
        return flag == null || flag.getEpisodes() == null ? 0 : flag.getEpisodes().size();
    }

    static List<Integer> sourceEpisodeNumbers(Flag flag) {
        List<Integer> numbers = new ArrayList<>();
        if (flag == null || flag.getEpisodes() == null) return numbers;
        for (Episode episode : flag.getEpisodes()) numbers.add(episode == null ? -1 : episode.getNumber());
        return numbers;
    }

    static List<Integer> explicitEpisodeSeasons(Flag flag) {
        List<Integer> seasons = new ArrayList<>();
        if (flag == null || flag.getEpisodes() == null) return seasons;
        for (Episode episode : flag.getEpisodes()) {
            seasons.add(EpisodeSeasonPolicy.resolveExplicitSourceSeason(
                    episode == null ? "" : episode.getName()));
        }
        return seasons;
    }

    private String sourceFingerprint(Vod sourceVod) {
        if (sourceVod == null) return "";
        String title = TextUtils.isEmpty(sourceCacheTitle) ? sourceVod.getName() : sourceCacheTitle;
        return manualBindingFingerprint(title, activeFlagFor(sourceVod), activeFlagKey);
    }

    public static String manualBindingFingerprint(String sourceTitle, Flag flag, String flagKey) {
        return (sourceTitle == null ? "" : sourceTitle) + "|"
                + (flagKey == null ? "" : flagKey) + "|"
                + EpisodeSeasonSnapshot.stableStructureFingerprint(flag == null ? null : flag.getEpisodes());
    }

    public static String sourceFingerprint(Flag flag, String flagKey, Map<Integer, Integer> seasonCounts) {
        return (flagKey == null ? "" : flagKey) + "|"
                + EpisodeSeasonSnapshot.structureFingerprint(flag == null ? null : flag.getEpisodes(), seasonCounts);
    }

    private Map<Integer, Integer> currentSeasonCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (SeasonOption option : seasonOptions) counts.put(option.getSeasonNumber(), option.getEpisodeCount());
        return counts;
    }

    static void clearEpisodeMetadata(Flag flag) {
        if (flag == null || flag.getEpisodes() == null) return;
        for (Episode episode : flag.getEpisodes()) {
            if (episode == null) continue;
            episode.setTmdbEpisode(null);
            episode.setDisplayName("");
        }
    }

    static List<SeasonOption> parseSeasonOptions(JsonObject detail) {
        List<SeasonOption> result = new ArrayList<>();
        if (detail == null || !detail.has("seasons") || !detail.get("seasons").isJsonArray()) return result;
        for (JsonElement element : detail.getAsJsonArray("seasons")) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject season = element.getAsJsonObject();
            int number = jsonInt(season, "season_number", -1);
            if (number < 0) continue;
            result.add(new SeasonOption(
                    number,
                    jsonString(season, "name"),
                    jsonString(season, "air_date"),
                    Math.max(0, jsonInt(season, "episode_count", 0))));
        }
        return List.copyOf(result);
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static final class SeasonOption {

        private final int seasonNumber;
        private final String name;
        private final String airDate;
        private final int episodeCount;

        private SeasonOption(int seasonNumber, String name, String airDate, int episodeCount) {
            this.seasonNumber = seasonNumber;
            this.name = name == null ? "" : name;
            this.airDate = airDate == null ? "" : airDate;
            this.episodeCount = Math.max(0, episodeCount);
        }

        public int getSeasonNumber() {
            return seasonNumber;
        }

        public String getName() {
            return name;
        }

        public String getAirDate() {
            return airDate;
        }

        public int getEpisodeCount() {
            return episodeCount;
        }
    }


    private String activityIntentTitle() {
        return activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("name");
    }
    private boolean isCurrentGeneration(int generation) {
        return generation == loadGeneration;
    }

    private void loadDetailSync(Vod vod, TmdbItem item, int generation) {
        loadDetailSync(vod, item, null, null, generation);
    }

    private void loadDetailSync(Vod vod, TmdbItem item, JsonObject cachedDetail, List<TmdbPerson> cachedCast, int generation) {
        long start = System.currentTimeMillis();
        try {
            SpiderDebug.log("tmdb", "detail core start title=%s media=%s id=%d", item.getTitle(), item.getMediaType(), item.getTmdbId());
            long detailStart = System.currentTimeMillis();
            JsonObject detail = cachedDetail;
            if (detail == null) detail = tmdbService.detail(item, tmdbConfig, false);
            item = normalizeLoadedItem(item, detail);
            SpiderDebug.log("tmdb", "detail core tmdbDetail source=%s cost=%dms title=%s", cachedDetail == null ? "service" : "memory-cache", System.currentTimeMillis() - detailStart, item.getTitle());
            long castStart = System.currentTimeMillis();
            List<TmdbPerson> cast = cachedCast == null ? new ArrayList<>() : new ArrayList<>(cachedCast);
            if (cast.isEmpty()) cast = tmdbService.cast(detail, tmdbConfig);
            SpiderDebug.log("tmdb", "detail core castParse source=%s cost=%dms count=%d title=%s", cachedCast == null || cachedCast.isEmpty() ? "service" : "memory-cache", System.currentTimeMillis() - castStart, cast.size(), item.getTitle());
            if (!isCurrentGeneration(generation)) return;
            this.vod = vod;
            if (activeFlag != null) {
                int activeIndex = flagIndex(vod == null ? null : vod.getFlags(), activeFlag);
                activeFlagKey = flagKey(activeFlag, activeIndex);
            }
            captureActiveFlagSeasonEvidence(vod);
            tmdbItem = item;
            tmdbDetail = detail;
            synchronized (Setting.class) {
                TmdbSeasonMatchCache seasonCache = Setting.getTmdbSeasonMatchCache();
                if (seasonCache.removeIfMediaChanged(cacheSiteKey(vod), cacheVodId(vod), sourceCacheTitle,
                        activeFlagKey, item.getTmdbId(), item.getMediaType(), allowLegacyVodBinding(vod))) {
                    Setting.putTmdbSeasonMatchCache(seasonCache);
                }
            }
            if (item.isTv()) {
                resolveSeason(vod, item, detail);
            } else {
                sourceSeasonNumber = -1;
                seasonResolution = null;
                seasonOptions = List.of();
            }
            episodeInfo = TmdbEpisodeInfo.from(item.getMediaType(), detail, sourceSeasonNumber);
            tmdbCast = cast;
            recommendations = new ArrayList<>();
            relatedVideos = null;
            recommendationPage = 1;
            recommendationHasMore = false;
            PersonalRecommendationService.RecommendationPages personalPages = PersonalRecommendationService.RecommendationPages.empty();
            personalTmdbPage = personalPages.getTmdb();
            personalDoubanPage = personalPages.getDouban();
            personalAiPage = personalPages.getAi();
            personalTmdbRecommendations = personalTmdbPage.getItems();
            personalDoubanRecommendations = personalDoubanPage.getItems();
            personalAiRecommendations = personalAiPage.getItems();
            loaded = true;
            episodeMetadataLoaded = vod == null || item == null || !item.isTv();
            if (vod != null) {
                saveMatch(vod, item);
                long enrichStart = System.currentTimeMillis();
                enrichVod(vod, item, detail);
                SpiderDebug.log("tmdb", "detail core enrichVod cost=%dms title=%s", System.currentTimeMillis() - enrichStart, item.getTitle());
                if (!isCurrentGeneration(generation)) return;
                notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_CORE);
                SpiderDebug.log("tmdb", "detail core first refresh queued cost=%dms title=%s", System.currentTimeMillis() - start, item.getTitle());
            }
            scheduleStartupBackgroundLoads(vod, item, detail, generation);
            SpiderDebug.log("tmdb", "detail core loaded title=%s cast=%d total=%dms", item.getTitle(), getCast().size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "detail load failed cost=%dms error=%s", System.currentTimeMillis() - start, e.getMessage());
            notifyLoadComplete(vod, generation);
        }
    }

    private TmdbItem normalizeLoadedItem(TmdbItem item, JsonObject detail) {
        String title = detailTitle(item, detail);
        if (TextUtils.isEmpty(title) || title.equals(item.getTitle())) return item;
        SpiderDebug.log("tmdb", "detail title normalized source=%s tmdb=%s", item.getTitle(), title);
        return new TmdbItem(
                item.getTmdbId(),
                item.getMediaType(),
                title,
                item.getSubtitle(),
                item.getOverview(),
                item.getPosterUrl(),
                item.getBackdropUrl(),
                item.getCredit(),
                item.getRating(),
                item.getOriginalLanguage(),
                item.getOriginCountry(),
                item.getGenreIds(),
                item.getDepartment(),
                item.getTmdbRating(),
                item.getDoubanRating(),
                item.getRecommendationReason());
    }

    private String detailTitle(TmdbItem item, JsonObject detail) {
        if (detail == null) return "";
        String primary = "movie".equalsIgnoreCase(item.getMediaType()) ? jsonString(detail, "title") : jsonString(detail, "name");
        if (!TextUtils.isEmpty(primary)) return primary;
        return "movie".equalsIgnoreCase(item.getMediaType()) ? jsonString(detail, "name") : jsonString(detail, "title");
    }

    private static String jsonString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private Vod alignCachedVodIdentity(Vod vod) {
        if (activity == null || activity.getIntent() == null) return vod;
        return VodEventGuard.alignCachedIdentity(
                vod,
                activity.getIntent().getStringExtra("key"),
                activity.getIntent().getStringExtra("id"));
    }


    private void notifyVodChanged(Vod vod, int generation, RefreshEvent.Type type) {
        if (vod == null || !isCurrentGeneration(generation)) return;
        pendingVodRefreshVod = alignCachedVodIdentity(vod);
        pendingVodRefreshGeneration = generation;
        // 累积待发送类型：240ms 合并窗口内若多种异步数据先后到达（如推荐与个性化），
        // 逐一派发各自的细粒度事件，避免后到者覆盖先到者的类型而丢刷新。
        // notifyVodChanged 会从后台线程（detail core / 集数标题）与主线程（推荐 / 个性）
        // 同时调用，EnumSet 非线程安全，故对其复合读写加锁串行化，避免并发修改异常与丢事件。
        synchronized (pendingVodRefreshTypes) {
            pendingVodRefreshTypes.add(type);
        }
        App.post(pendingVodRefresh, VOD_REFRESH_COALESCE_MS);
    }

    private void dispatchPendingVodRefresh() {
        Vod vod = pendingVodRefreshVod;
        int generation = pendingVodRefreshGeneration;
        // add（后台线程）与 drain+clear（此处，主线程）跨线程访问同一 EnumSet，
        // 复合操作须同锁串行化，避免 ConcurrentModificationException 或漏掉类型。
        java.util.EnumSet<RefreshEvent.Type> types;
        synchronized (pendingVodRefreshTypes) {
            types = pendingVodRefreshTypes.isEmpty()
                    ? java.util.EnumSet.noneOf(RefreshEvent.Type.class)
                    : java.util.EnumSet.copyOf(pendingVodRefreshTypes);
            pendingVodRefreshTypes.clear();
        }
        pendingVodRefreshVod = null;
        if (vod == null || !isCurrentGeneration(generation)) return;
        SpiderDebug.log("tmdb", "vod refresh coalesced dispatch title=%s types=%s", vod.getName(), types);
        for (RefreshEvent.Type type : types) {
            switch (type) {
                case VOD_CORE -> RefreshEvent.vodCore(vod);
                case VOD_RECOMMENDATIONS -> RefreshEvent.vodRecommendations(vod);
                case VOD_PERSONAL -> RefreshEvent.vodPersonal(vod);
                case VOD_EPISODE_TITLES -> RefreshEvent.vodEpisodeTitles(vod);
                case VOD_RELATED_VIDEOS -> RefreshEvent.vodRelatedVideos(vod);
                default -> RefreshEvent.vod(vod);
            }
        }
    }

    private void scheduleStartupBackgroundLoads(Vod vod, TmdbItem item, JsonObject detail, int generation) {
        scheduleStartupEpisodeLoad(vod, item, generation);
        if (pendingStartupBackgroundLoads != null) App.removeCallbacks(pendingStartupBackgroundLoads);
        pendingStartupBackgroundLoads = () -> {
            if (!isCurrentGeneration(generation)) return;
            SpiderDebug.log("tmdb", "startup background loads begin title=%s delay=%dms", item == null ? "" : item.getTitle(), TMDB_STARTUP_BACKGROUND_DELAY_MS);
            loadRelatedVideosAsync(sourceSeasonNumber, -1);
            loadRelatedRecommendationsAsync(vod, item, detail, generation);
            loadPersonalRecommendationsAsync(vod, item, detail, generation);
        };
        App.post(pendingStartupBackgroundLoads, TMDB_STARTUP_BACKGROUND_DELAY_MS);
    }

    // 选集元数据单独排程且不延迟：它决定「正在加载剧集信息...」占位符何时消失。
    private void scheduleStartupEpisodeLoad(Vod vod, TmdbItem item, int generation) {
        if (pendingStartupEpisodeLoad != null) App.removeCallbacks(pendingStartupEpisodeLoad);
        pendingStartupEpisodeLoad = () -> {
            if (!isCurrentGeneration(generation)) return;
            SpiderDebug.log("tmdb", "startup episode load begin title=%s delay=%dms", item == null ? "" : item.getTitle(), TMDB_STARTUP_EPISODE_DELAY_MS);
            if (vod == null || item == null || !item.isTv()) return;
            int metadataGeneration = ++episodeMetadataGeneration;
            Integer selectedSeason = currentEpisodeMetadataSeason();
            episodeMetadataLoaded = false;
            if (selectedSeason == null && !isMultiSliceResolution()) {
                finishEpisodeMetadataLoad(vod, generation, metadataGeneration, null);
            } else {
                loadEpisodeTitlesAsync(vod, item, generation, metadataGeneration, selectedSeason);
            }
        };
        App.post(pendingStartupEpisodeLoad, TMDB_STARTUP_EPISODE_DELAY_MS);
    }

    private void loadEpisodeTitlesAsync(Vod vod, TmdbItem item, int generation, int metadataGeneration, Integer selectedSeason) {
        if (vod == null || item == null || !item.isTv()) return;
        episodeTasks.submit(() -> {
            if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)) return;
            long start = System.currentTimeMillis();
            boolean changed = selectedSeason != null
                    ? applyEpisodeTitles(vod, item, selectedSeason, generation, metadataGeneration)
                    : applyEpisodeTitlesForSlices(vod, item, generation, metadataGeneration);
            if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)) return;
            SpiderDebug.log("tmdb", "episode titles async cost=%dms changed=%s season=%s title=%s", System.currentTimeMillis() - start, changed, selectedSeason, item.getTitle());
            finishEpisodeMetadataLoad(vod, generation, metadataGeneration, selectedSeason);
        });
    }

    private void loadRelatedRecommendationsAsync(Vod vod, TmdbItem item, JsonObject detail, int generation) {
        if (item == null || detail == null) return;
        recommendationLoading = true;
        backgroundTasks.submit(() -> {
            long start = System.currentTimeMillis();
            List<TmdbItem> ranked = new ArrayList<>();
            boolean more = false;
            int recommendationCount = 0;
            int similarCount = 0;
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                long recommendationsStart = System.currentTimeMillis();
                List<TmdbItem> pageRecommendations = tmdbService.recommendations(item, tmdbConfig, 1);
                SpiderDebug.log("tmdb", "related recommendations request cost=%dms count=%d title=%s", System.currentTimeMillis() - recommendationsStart, pageRecommendations.size(), item.getTitle());
                long similarStart = System.currentTimeMillis();
                List<TmdbItem> pageSimilar = tmdbService.similar(item, tmdbConfig, 1);
                SpiderDebug.log("tmdb", "related similar request cost=%dms count=%d title=%s", System.currentTimeMillis() - similarStart, pageSimilar.size(), item.getTitle());
                recommendationCount = pageRecommendations.size();
                similarCount = pageSimilar.size();
                ranked = PersonalRecommendationService.rankTmdbItemsForContext(detail, pageRecommendations, pageSimilar, Integer.MAX_VALUE);
                more = !pageRecommendations.isEmpty() || !pageSimilar.isEmpty();
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial recommendations failed error=%s", e.getMessage());
            }
            List<TmdbItem> loadedItems = ranked;
            boolean hasMore = more;
            SpiderDebug.log("tmdb", "related recommendations async cost=%dms recommendations=%d similar=%d ranked=%d title=%s", System.currentTimeMillis() - start, recommendationCount, similarCount, loadedItems.size(), item.getTitle());
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                recommendationLoading = false;
                recommendations = loadedItems;
                recommendationPage = 1;
                recommendationHasMore = hasMore;
                if (vod != null && !loadedItems.isEmpty()) notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_RECOMMENDATIONS);
            });
            service.enrichTmdbRatingsAsync(loadedItems, enriched -> applyRelatedRatingEnrichment(enriched, generation, vod));
        });
    }

    private void loadPersonalRecommendationsAsync(Vod vod, TmdbItem item, JsonObject detail, int generation) {
        if (vod == null || !Setting.isPersonalRecommendation()) return;
        personalRefreshLoading = true;
        backgroundTasks.submit(() -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService.RecommendationPages pages = PersonalRecommendationService.RecommendationPages.empty();
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                pages = service.loadPage(vod, item, detail, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial personal recommendations failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPages loadedPages = pages;
            SpiderDebug.log("tmdb", "personal recommendations async cost=%dms tmdb=%d douban=%d title=%s", System.currentTimeMillis() - start, loadedPages.getTmdb().getItems().size(), loadedPages.getDouban().getItems().size(), item == null ? "" : item.getTitle());
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                personalRefreshLoading = false;
                personalTmdbPage = loadedPages.getTmdb();
                personalDoubanPage = loadedPages.getDouban();
                personalTmdbRecommendations = personalTmdbPage.getItems();
                personalDoubanRecommendations = personalDoubanPage.getItems();
                if (vod != null && (!personalTmdbRecommendations.isEmpty() || !personalDoubanRecommendations.isEmpty())) notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_PERSONAL);
            });
            service.enrichTmdbPageRatingsAsync(loadedPages.getTmdb(), enriched -> applyPersonalTmdbRatingEnrichment(enriched, generation, vod));
        });
        loadPersonalAiRecommendationsAsync(vod, item, generation);
    }

    private void loadPersonalAiRecommendationsAsync(Vod vod, TmdbItem item, int generation) {
        if (vod == null || !Setting.isPersonalRecommendation() || personalAiLoading) return;
        personalAiLoading = true;
        backgroundTasks.submit(() -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            AiRecommendationService.CachedPage cached = service.loadCachedAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            if (cached.hasItems()) {
                SpiderDebug.log("tmdb", "personal ai cache hit exact=%s resolved=%s count=%d title=%s", cached.isExact(), cached.isResolved(), cached.getPage().getItems().size(), item == null ? "" : item.getTitle());
                applyPersonalAiPage(cached.getPage(), generation, false, false);
            }
            PersonalRecommendationService.RecommendationPage page = cached.getPage();
            String mode = "cache";
            try {
                if (!cached.hasItems() || !cached.isExact() || !cached.isResolved()) {
                    mode = cached.isExact() ? "resolve-cache" : "refresh";
                    page = cached.isExact()
                            ? service.resolveCachedAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                            : service.refreshAiPage(vod, item, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                }
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "initial personal ai recommendations failed error=%s", e.getMessage());
                page = PersonalRecommendationService.RecommendationPage.empty("");
            }
            PersonalRecommendationService.RecommendationPage loadedPage = page;
            SpiderDebug.log("tmdb", "personal ai recommendations async mode=%s cost=%dms count=%d title=%s", mode, System.currentTimeMillis() - start, loadedPage.getItems().size(), item == null ? "" : item.getTitle());
            applyPersonalAiPage(loadedPage, generation, !cached.hasItems(), true);
            service.enrichTmdbPageRatingsAsync(loadedPage, enriched -> applyPersonalAiRatingEnrichment(enriched, generation));
        });
    }

    private void applyPersonalAiRatingEnrichment(PersonalRecommendationService.RecommendationPage enriched, int generation) {
        activity.runOnUiThread(() -> {
            if (!isCurrentGeneration(generation) || enriched == null || !mergeRecommendationRatings(personalAiRecommendations, enriched.getItems())) return;
            if (personalAiPage != null) personalAiPage = personalAiPage.withItems(personalAiRecommendations);
            notifyPersonalAiRecommendationsUpdated();
        });
    }

    private void applyPersonalAiPage(PersonalRecommendationService.RecommendationPage page, int generation, boolean allowEmpty, boolean finishLoading) {
        activity.runOnUiThread(() -> {
            if (!isCurrentGeneration(generation)) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            if (page == null) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            List<TmdbItem> items = page.getItems();
            if (!allowEmpty && items.isEmpty()) {
                if (finishLoading) personalAiLoading = false;
                return;
            }
            boolean changed = !TmdbRecommendationRows.sameDisplayList(personalAiRecommendations, items);
            personalAiPage = page;
            personalAiRecommendations = items;
            if (changed) notifyPersonalAiRecommendationsUpdated();
            if (finishLoading) personalAiLoading = false;
        });
    }

    private void notifyPersonalAiRecommendationsUpdated() {
        if (personalAiUpdateListener != null) personalAiUpdateListener.onPersonalAiRecommendationsUpdated();
    }

    private void finishEpisodeMetadataLoad(Vod vod, int generation, int metadataGeneration, Integer selectedSeason) {
        if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)) return;
        episodeMetadataLoaded = true;
        notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_EPISODE_TITLES);
    }

    /**
     * 取某季集数，优先命中进程内缓存。缓存未命中才走 TmdbService（磁盘缓存 / 网络）。
     * 空结果不写缓存，避免把一次失败固化成整个会话的空列表。
     *
     * 缓存生命周期绑定一次详情请求：由 beginDetailRequest() 清空（在 prefetch 预热之前），
     * 所以既不会挡住强制刷新，也不会被 load() 冲掉预热结果。MAX_CACHED_SEASONS 只是
     * 防御多季剧反复切季时的无界增长。
     */
    private List<TmdbEpisode> seasonEpisodes(TmdbItem item, int seasonNumber) throws Exception {
        if (item == null || seasonNumber < 0) return List.of();
        String key = item.getTmdbId() + "|" + item.getMediaType() + "|" + seasonNumber;
        List<TmdbEpisode> cached = seasonEpisodeCache.get(key);
        if (cached != null) {
            SpiderDebug.log("tmdb", "season episodes source=memory season=%d count=%d", seasonNumber, cached.size());
            return cached;
        }
        JsonObject season = tmdbService.season(item, seasonNumber, tmdbConfig);
        if (season == null) return List.of();
        List<TmdbEpisode> episodes = tmdbService.episodes(season, tmdbConfig, item.getTmdbId(), seasonNumber);
        if (!episodes.isEmpty()) {
            if (seasonEpisodeCache.size() >= MAX_CACHED_SEASONS) seasonEpisodeCache.clear();
            seasonEpisodeCache.put(key, List.copyOf(episodes));
        }
        return episodes;
    }

    private Integer currentEpisodeMetadataSeason() {
        if (seasonResolution == null || seasonResolution.getStatus() != TmdbSeasonResolver.Status.RESOLVED) return null;
        Integer selectedSeason = seasonResolution.getSelectedSeason();
        return selectedSeason != null && selectedSeason >= 0 ? selectedSeason : null;
    }

    private boolean isMultiSliceResolution() {
        return seasonResolution != null && seasonResolution.getStatus() == TmdbSeasonResolver.Status.MULTI_SLICE;
    }

    private boolean isCurrentEpisodeMetadataRequest(int generation, int metadataGeneration, Integer selectedSeason) {
        return isCurrentGeneration(generation)
                && metadataGeneration == episodeMetadataGeneration
                && Objects.equals(currentEpisodeMetadataSeason(), selectedSeason);
    }

    private void notifyLoadComplete(Vod vod, int generation) {
        // TMDB 加载失败或跳过时，仍然发送 RefreshEvent 让 UI 继续
        if (!isCurrentGeneration(generation)) return;
        episodeMetadataLoaded = true;
        if (vod != null) notifyVodChanged(vod, generation, RefreshEvent.Type.VOD_CORE);
    }

    private TmdbItem getCachedMatch(Vod vod) {
        if (vod == null) return null;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        TmdbItem manual = cache.findManual(cacheSiteKey(vod), cacheVodId(vod), vod.getName());
        return manual != null ? manual : cache.find(cacheSiteKey(vod), cacheVodId(vod), vod.getName());
    }

    private boolean isManualMatch(Vod vod) {
        return vod != null && Setting.getTmdbMatchCache().isManual(cacheSiteKey(vod), cacheVodId(vod), vod.getName());
    }

    private boolean isCachedSplitSeasonMismatch(String videoName, Vod vod, TmdbItem item) {
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            return TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(videoName, vod), detail);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String matchSourceText(String videoName, Vod vod) {
        StringBuilder builder = new StringBuilder(videoName == null ? "" : videoName);
        if (vod != null) {
            builder.append(' ').append(vod.getName());
            builder.append(' ').append(vod.getRemarks());
        }
        return builder.toString();
    }

    private void saveMatch(Vod vod, TmdbItem item) {
        if (vod == null || item == null || item.getTmdbId() <= 0) return;
        // 读-改-写要整体互斥：本方法在后台线程被调用，手动选择在主线程写，
        // 不加锁会让后到的自动结果基于旧快照覆盖掉刚落盘的手动选择。
        synchronized (Setting.class) {
            TmdbMatchCache cache = Setting.getTmdbMatchCache();
            cache.put(cacheSiteKey(vod), cacheVodId(vod), vod.getName(), item);
            Setting.putTmdbMatchCache(cache);
        }
    }

    /**
     * 记录手动选择。必须在 load() 之前调用：load() 会重置 sourceCacheTitle，
     * 而 enrichVod 之后 vod.getName() 已被改写成 TMDB 标题，不能作为唯一的键。
     */
    private void saveManualMatch(Vod vod, TmdbItem item) {
        if (vod == null || item == null || item.getTmdbId() <= 0) return;
        List<String> aliases = manualMatchTitleAliases(vod);
        synchronized (Setting.class) {
            TmdbMatchCache cache = Setting.getTmdbMatchCache();
            cache.putManual(cacheSiteKey(vod), cacheVodId(vod), aliases, item);
            Setting.putTmdbMatchCache(cache);
        }
    }

    /**
     * 别名只取站源侧信号。vod.getName() 在 enrichVod 之后已是"上一次"的 TMDB 标题，
     * 无条件写进去会留下一条指向旧条目的精确键（A→B→C 连续切换后 key(标题A) 仍指向 B），
     * 而历史记录里存的正是那个旧标题，反查就会读回旧选择。但它未被富集时又正是
     * getCachedMatch 的读取键，不能一概丢弃——用当前已加载条目的标题判断它是否已被改写。
     */
    private List<String> manualMatchTitleAliases(Vod vod) {
        List<String> aliases = new ArrayList<>();
        addTitleAlias(aliases, sourceCacheTitle);
        addTitleAlias(aliases, activityIntentTitle());
        String vodTitle = vod == null ? "" : vod.getName();
        if (!isEnrichedVodTitle(vodTitle)) addTitleAlias(aliases, vodTitle);
        return aliases;
    }

    private boolean isEnrichedVodTitle(String vodTitle) {
        return tmdbItem != null && !TextUtils.isEmpty(vodTitle) && vodTitle.equals(tmdbItem.getTitle());
    }

    private static void addTitleAlias(List<String> aliases, String title) {
        if (TextUtils.isEmpty(title) || aliases.contains(title)) return;
        aliases.add(title);
    }

    private void saveTitleLearning(Vod vod, TmdbItem item) {
        if (vod == null || item == null || item.getTitle().isEmpty()) return;
        String rawTitle = vod.getName();
        MediaTitleParser parser = new MediaTitleParser();
        MediaTitleLearningStore.load().putManual(
                cacheSiteKey(vod),
                cacheVodId(vod),
                rawTitle,
                parser.cleanTitle(rawTitle),
                item.getTitle(),
                item.getMediaType(),
                parser.firstYear(vod.getYear()),
                parser.seasonNumber(rawTitle),
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);
    }

    private String cacheSiteKey(Vod vod) {
        String siteKey = vod == null ? "" : vod.getSiteKey();
        if (!TextUtils.isEmpty(siteKey)) return siteKey;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    private String cacheVodId(Vod vod) {
        String vodId = vod == null ? "" : vod.getId();
        if (!TextUtils.isEmpty(vodId)) return vodId;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("id");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    /**
     * 把 TMDB 详情写回 Vod。
     */
    public void enrichVod(Vod vod) {
        enrichVod(vod, tmdbItem, tmdbDetail);
    }

    private void enrichVod(Vod vod, TmdbItem item, JsonObject detail) {
        if (vod == null || item == null || detail == null) return;

        applyTmdbTitle(vod, item);

        // 简介：优先使用 TMDB 翻译后的简介
        String overview = tmdbService.translatedOverview(detail, tmdbConfig);
        if (!TextUtils.isEmpty(overview) && overview.length() > vod.getContent().length()) {
            vod.setContent(overview);
        }

        // 海报：源站缺失时使用 TMDB 海报
        if (TextUtils.isEmpty(vod.getPic()) && !TextUtils.isEmpty(item.getPosterUrl())) {
            vod.setPic(item.getPosterUrl());
        }

        // 年份：源站缺失时使用 TMDB 年份
        if (TextUtils.isEmpty(vod.getYear())) {
            String year = getYear();
            if (!TextUtils.isEmpty(year)) vod.setYear(year);
        }

        // 地区：源站缺失时使用 TMDB 制片国家
        if (TextUtils.isEmpty(vod.getArea())) {
            String area = getArea();
            if (!TextUtils.isEmpty(area)) vod.setArea(area);
        }

        // 类型 / 题材：源站缺失时使用 TMDB 类型
        if (TextUtils.isEmpty(vod.getTypeName())) {
            String genres = getGenresText();
            if (!TextUtils.isEmpty(genres)) vod.setTypeName(genres);
        }

        // 演员：源站缺失时使用 TMDB 演员表
        if (TextUtils.isEmpty(vod.getActor())) {
            List<String> names = new ArrayList<>();
            for (TmdbPerson person : getCast()) {
                if (!TextUtils.isEmpty(person.getName())) names.add(person.getName());
                if (names.size() >= 5) break;
            }
            if (!names.isEmpty()) vod.setActor(TextUtils.join(" / ", names));
        }

        // 导演 / 主创：源站缺失时使用 TMDB 主创
        if (TextUtils.isEmpty(vod.getDirector())) {
            List<TmdbPerson> creators = tmdbService.creators(detail, tmdbConfig);
            List<String> names = new ArrayList<>();
            for (TmdbPerson person : creators) {
                if (!TextUtils.isEmpty(person.getName())) names.add(person.getName());
                if (names.size() >= 5) break;
            }
            if (!names.isEmpty()) vod.setDirector(TextUtils.join(" / ", names));
        }
    }

    static boolean applyTmdbTitle(Vod vod, TmdbItem item) {
        if (vod == null || item == null) return false;
        String title = item.getTitle();
        if (title == null || title.length() == 0) return false;
        vod.setName(sourceAwareTitle(vod.getName(), item, title));
        return true;
    }

    public static String sourceAwareTitle(String sourceTitle, TmdbItem item, String tmdbTitle) {
        if (item != null && item.isTv() && EpisodeSeasonPolicy.resolveSourceSeason(sourceTitle) >= 0) return sourceTitle;
        return tmdbTitle;
    }

    /**
     * 获取并应用 TMDB 集数标题到 Vod（仅针对电视剧）。
     */
    private boolean applyEpisodeTitles(Vod vod, TmdbItem item, int selectedSeason, int generation, int metadataGeneration) {
        if (vod == null || item == null || vod.getFlags() == null) return false;
        if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)) return false;
        try {
            List<TmdbEpisode> episodes = seasonEpisodes(item, selectedSeason);
            if (episodes.isEmpty()) return false;
            Map<Integer, TmdbEpisode> episodesByNumber = indexEpisodesByNumber(episodes);

            // 线路集号可靠时按显式集号匹配；出现重复、缺失或越界时按原始顺序匹配。
            synchronized (episodeMetadataLock) {
                if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, selectedSeason)) return false;
                Flag target = activeFlagFor(vod);
                boolean changed = applyEpisodeMetadata(target == null ? null : target.getEpisodes(), episodesByNumber, selectedSeason);
                com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(target);
                SpiderDebug.log("tmdb", "应用第 %d 季集数标题: %d 集", selectedSeason, episodes.size());
                return changed;
            }
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "获取第 %d 季集数信息失败: %s", selectedSeason, e.getMessage());
            return false;
        }
    }

    private boolean applyEpisodeTitlesForSlices(Vod vod, TmdbItem item, int generation, int metadataGeneration) {
        if (vod == null || item == null || vod.getFlags() == null || !isMultiSliceResolution()) return false;
        Map<Integer, Integer> seasonCounts = new HashMap<>();
        for (SeasonOption option : seasonOptions) seasonCounts.put(option.getSeasonNumber(), option.getEpisodeCount());
        List<TmdbSeasonSegment> persistedSegments = seasonResolution.getSegments();
        // 只拉 availableSeasons 会漏季：手动分段绑定时它仅含 segments 涉及的季，
        // 其余季（如航海王第 7 季）的集永远拿不到刮削数据。但也不能拉全部可切分季——
        // 23 季串行阻塞 HTTP 首次冷缓存要 7-18 秒，期间选集界面无标题无图。
        // 只拉源集数实际覆盖到的那几季（这正是 fallback 唯一会用到的集合）。
        List<Integer> seasons = seasonsCoveringSource(seasonCounts, sourceEpisodeCount(activeFlagFor(vod)), persistedSegments);
        Map<Integer, Map<Integer, TmdbEpisode>> episodesBySeason = new HashMap<>();
        try {
            for (Integer season : seasons) {
                if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, null)) return false;
                // 采用远端带内存缓存的 seasonEpisodes；单季拿不到时跳过而不是整体放弃，
                // 否则 23 季里任何一季失败就会让所有季的刮削一起丢掉。
                List<TmdbEpisode> seasonItems = seasonEpisodes(item, season);
                if (seasonItems.isEmpty()) continue;
                episodesBySeason.put(season, indexEpisodesByNumber(seasonItems));
            }
            if (episodesBySeason.isEmpty()) return false;
            synchronized (episodeMetadataLock) {
                if (!isCurrentEpisodeMetadataRequest(generation, metadataGeneration, null)) return false;
                boolean changed = false;
                Flag target = activeFlagFor(vod);
                List<Episode> sourceEpisodes = target == null ? null : target.getEpisodes();
                if (sourceEpisodes != null) {
                    List<Integer> sourceNumbers = new ArrayList<>();
                    for (Episode episode : sourceEpisodes) sourceNumbers.add(episode == null ? -1 : episode.getNumber());
                    if (!persistedSegments.isEmpty()) {
                        changed |= applySegmentedEpisodeMetadata(sourceEpisodes, episodesBySeason, persistedSegments);
                    } else if (EpisodeSeasonPolicy.canMapFlatEpisodeKeys(sourceNumbers, seasons, seasonCounts)) {
                        changed |= applyMappedEpisodeMetadata(sourceEpisodes, episodesBySeason, seasons, seasonCounts);
                    }
                    // 上面两条路径都可能留下大量未挂数据的集：分段只覆盖部分季，
                    // canMapFlatEpisodeKeys 又要求各季集数精确覆盖源集数（航海王 1114 vs ~1120 不满足）。
                    // 用尽力切分补齐剩下的集，与选集界面的季度按钮同一套分段口径。
                    changed |= applySegmentFallbackMetadata(sourceEpisodes, episodesBySeason, seasons, seasonCounts);
                }
                com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(target);
                SpiderDebug.log("tmdb", "应用多季切片元数据: %s", seasons);
                return changed;
            }
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "获取多季切片信息失败: %s", e.getMessage());
            return false;
        }
    }

    private boolean applyEpisodeMetadata(List<Episode> sourceEpisodes, Map<Integer, TmdbEpisode> episodesByNumber, int selectedSeason) {
        if (sourceEpisodes == null || episodesByNumber == null || episodesByNumber.isEmpty()) return false;
        boolean changed = false;
        boolean usePosition = shouldUseEpisodePosition(sourceEpisodes, episodesByNumber);
        for (int index = 0; index < sourceEpisodes.size(); index++) {
            Episode episode = sourceEpisodes.get(index);
            int resolvedNumber = resolveEpisodeNumber(episode, index, usePosition);
            TmdbEpisode tmdbEp = episodesByNumber.get(resolvedNumber);
            if (tmdbEp == null || !TmdbEpisodeMatcher.shouldApply(episode, tmdbEp, resolvedNumber)) continue;
            if (hasEpisodeMetadataChanged(episode.getTmdbEpisode(), tmdbEp)) changed = true;
            episode.setTmdbEpisode(tmdbEp);
            if (!tmdbEp.getTitle().isEmpty()) {
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(tmdbEp.getNumber(), tmdbEp.getTitle()), Setting.isTmdbEpisodeFileSize());
                if (TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                episode.setDisplayName(displayName);
                changed = true;
            }
        }
        return changed;
    }

    private List<Integer> allSliceableSeasons(Map<Integer, Integer> seasonCounts) {
        List<Integer> ordered = new ArrayList<>();
        for (SeasonOption option : seasonOptions) ordered.add(option.getSeasonNumber());
        return EpisodeSeasonPolicy.sliceableSeasons(ordered);
    }

    /**
     * 拉取范围：源集数按各季集数累加能覆盖到的季，外加已持久化分段涉及的季。
     * 源只有 300 集时前 5-6 季就够了，不必为 1000+ 集的番拉满 23 季。
     */
    private List<Integer> seasonsCoveringSource(Map<Integer, Integer> seasonCounts, int sourceEpisodeCount, List<TmdbSeasonSegment> persistedSegments) {
        List<Integer> sliceable = allSliceableSeasons(seasonCounts);
        if (sourceEpisodeCount <= 0) return sliceable;
        Set<Integer> needed = new LinkedHashSet<>();
        int covered = 0;
        for (Integer season : sliceable) {
            if (covered >= sourceEpisodeCount) break;
            needed.add(season);
            covered += Math.max(0, seasonCounts.getOrDefault(season, 0));
        }
        // 分段绑定可能指向累加范围之外的季，缺了它 applySegmentedEpisodeMetadata 会拿不到数据
        for (TmdbSeasonSegment segment : persistedSegments) needed.add(segment.getSeasonNumber());
        List<Integer> ordered = new ArrayList<>();
        for (Integer season : sliceable) if (needed.contains(season)) ordered.add(season);
        return ordered.isEmpty() ? sliceable : ordered;
    }

    /**
     * 排序后的集号是否为无跳号的连续序列，且长度至少覆盖 width。
     * 只要前 width 个连续即可——末季分段被裁短时取前缀是安全的。
     */
    static boolean isContiguousFrom(List<Integer> orderedNumbers, int width) {
        if (width <= 0 || orderedNumbers.size() < width) return false;
        for (int i = 1; i < width; i++) {
            // 必须 intValue 比较：集号常大于 127，超出 Integer 缓存后 != 会比对象引用
            if (orderedNumbers.get(i).intValue() != orderedNumbers.get(i - 1) + 1) return false;
        }
        return true;
    }

    /**
     * 按 {@link EpisodeSeasonSegments} 的尽力切分给尚未挂上 TMDB 数据的集补齐元数据。
     * 只填空缺，不覆盖已匹配的集——分段绑定和精确映射的结果比这里的推算更可信。
     */
    private boolean applySegmentFallbackMetadata(
            List<Episode> sourceEpisodes,
            Map<Integer, Map<Integer, TmdbEpisode>> episodesBySeason,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(sourceEpisodes.size(), seasons, seasonCounts);
        if (segments.isEmpty()) return false;
        boolean changed = false;
        for (EpisodeSeasonSegments.Segment segment : segments) {
            if (EpisodeSeasonSegments.isOther(segment.season())) continue;
            Map<Integer, TmdbEpisode> episodesByNumber = episodesBySeason.get(segment.season());
            if (episodesByNumber == null) continue;
            // 不能假设每季集号都从 1 开始：航海王这类长番在 TMDB 里是跨季连续编号的
            // （第 7 季 = 196-228 而非 1-33），按季内序号取键会全部落空。
            // 改为把该季集号排序后按位置取，既支持连续编号也支持每季重新编号。
            List<Integer> orderedNumbers = new ArrayList<>(episodesByNumber.keySet());
            Collections.sort(orderedNumbers);
            int width = segment.end() - segment.start();
            // 按位置贴的前提：该季集号必须是无跳号的连续序列，且至少覆盖分段宽度。
            // indexEpisodesByNumber 会滤掉集号 <= 0 并对重号去重，中间缺一集就会让后面每集
            // 都挂上下一集的标题和剧照——这条路径没有 TmdbEpisodeMatcher 兜底，只能整季放弃。
            // 不能简单要求"集数 == 分段宽度"：末季的分段会被 build 裁到源集数
            // （航海王 S22 宽 26 而该季 67 集），那种前缀对齐的情况是安全的。
            if (!isContiguousFrom(orderedNumbers, width)) continue;
            for (int index = segment.start(); index < segment.end() && index < sourceEpisodes.size(); index++) {
                Episode episode = sourceEpisodes.get(index);
                if (episode == null || episode.getTmdbEpisode() != null) continue;
                int offset = index - segment.start();
                if (offset >= orderedNumbers.size()) continue;
                int mappedNumber = orderedNumbers.get(offset);
                TmdbEpisode tmdbEpisode = episodesByNumber.get(mappedNumber);
                if (tmdbEpisode == null) continue;
                // 源集号与本季集号一致时是普通匹配；不一致才是跨季映射，必须带 mapped 标记
                // 才能通过 TmdbEpisodeMatcher 的校验。
                if (episode.getNumber() == tmdbEpisode.getNumber()) episode.setTmdbEpisode(tmdbEpisode);
                else episode.setMappedTmdbEpisode(tmdbEpisode);
                changed = true;
                if (tmdbEpisode.getTitle().isEmpty()) continue;
                String displayName = EpisodeTitleFormatter.withSourceFileSize(
                        episode.getName(),
                        EpisodeTitleFormatter.formatTmdbTitle(mappedNumber, tmdbEpisode.getTitle()),
                        Setting.isTmdbEpisodeFileSize());
                if (!TextUtils.equals(episode.getDisplayName(), displayName)) episode.setDisplayName(displayName);
            }
        }
        return changed;
    }

    private boolean applyMappedEpisodeMetadata(
            List<Episode> sourceEpisodes,
            Map<Integer, Map<Integer, TmdbEpisode>> episodesBySeason,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodes == null || episodesBySeason == null || episodesBySeason.isEmpty()) return false;
        boolean changed = false;
        for (Episode episode : sourceEpisodes) {
            EpisodeSeasonPolicy.SeasonEpisode mapped = EpisodeSeasonPolicy.mapFlatEpisodeNumber(
                    episode == null ? -1 : episode.getNumber(), seasons, seasonCounts);
            if (mapped == null) continue;
            Map<Integer, TmdbEpisode> episodesByNumber = episodesBySeason.get(mapped.seasonNumber());
            if (episodesByNumber == null) continue;
            int mappedNumber = mapped.episodeNumber();
            TmdbEpisode tmdbEpisode = episodesByNumber.get(mappedNumber);
            if (!TmdbEpisodeMatcher.shouldApplyMapped(episode, tmdbEpisode, mapped.seasonNumber(), mappedNumber)) continue;
            if (hasEpisodeMetadataChanged(episode.getTmdbEpisode(), tmdbEpisode)) changed = true;
            // 同上：已通过跨季映射校验，必须标记 mapped，否则源集号≠本季集号的条目会被严格匹配丢掉
            episode.setMappedTmdbEpisode(tmdbEpisode);
            if (!tmdbEpisode.getTitle().isEmpty()) {
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(mappedNumber, tmdbEpisode.getTitle()), Setting.isTmdbEpisodeFileSize());
                if (!TextUtils.equals(episode.getDisplayName(), displayName)) {
                    episode.setDisplayName(displayName);
                    changed = true;
                }
            }
        }
        return changed;
    }

    static boolean applySegmentedEpisodeMetadata(
            List<Episode> sourceEpisodes,
            Map<Integer, Map<Integer, TmdbEpisode>> episodesBySeason,
            List<TmdbSeasonSegment> segments) {
        for (TmdbSeasonSegment segment : segments) {
            Map<Integer, TmdbEpisode> seasonEpisodes = episodesBySeason.get(segment.getSeasonNumber());
            int length = segment.getSourceEpisodeEndIndex() - segment.getSourceEpisodeStartIndex() + 1;
            if (segment.getSourceEpisodeStartIndex() < 0
                    || segment.getSourceEpisodeEndIndex() >= sourceEpisodes.size()
                    || length <= 0 || seasonEpisodes == null
                    || segment.getTmdbEpisodeStartNumber() + length - 1 > seasonEpisodes.size()) return false;
        }
        boolean changed = false;
        for (TmdbSeasonSegment segment : segments) {
            Map<Integer, TmdbEpisode> seasonEpisodes = episodesBySeason.get(segment.getSeasonNumber());
            if (seasonEpisodes == null) continue;
            int end = segment.getSourceEpisodeEndIndex();
            for (int index = segment.getSourceEpisodeStartIndex(); index <= end; index++) {
                Episode episode = sourceEpisodes.get(index);
                int tmdbNumber = segment.getTmdbEpisodeStartNumber()
                        + index - segment.getSourceEpisodeStartIndex();
                TmdbEpisode tmdbEpisode = seasonEpisodes.get(tmdbNumber);
                if (!TmdbEpisodeMatcher.shouldApplyMapped(
                        episode, tmdbEpisode, segment.getSeasonNumber(), tmdbNumber)) continue;
                if (hasEpisodeMetadataChanged(episode.getTmdbEpisode(), tmdbEpisode)) changed = true;
                // 已通过 shouldApplyMapped 的分段校验，属于明确的跨季安全映射；
                // 必须标记 mapped，否则源集号与本季集号不同的分段会被严格匹配判为无效。
                episode.setMappedTmdbEpisode(tmdbEpisode);
                if (!tmdbEpisode.getTitle().isEmpty()) {
                    String displayName = EpisodeTitleFormatter.withSourceFileSize(
                            episode.getName(),
                            EpisodeTitleFormatter.formatTmdbTitle(tmdbEpisode.getNumber(), tmdbEpisode.getTitle()),
                            Setting.isTmdbEpisodeFileSize());
                    if (!TextUtils.equals(episode.getDisplayName(), displayName)) {
                        episode.setDisplayName(displayName);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
    private static Map<Integer, TmdbEpisode> indexEpisodesByNumber(List<TmdbEpisode> episodes) {
        Map<Integer, TmdbEpisode> indexed = new HashMap<>();
        if (episodes == null) return indexed;
        for (TmdbEpisode episode : episodes) {
            if (episode != null && episode.getNumber() > 0) indexed.putIfAbsent(episode.getNumber(), episode);
        }
        return indexed;
    }

    static boolean shouldUseEpisodePosition(List<Episode> sourceEpisodes, List<TmdbEpisode> tmdbEpisodes) {
        if (tmdbEpisodes == null || tmdbEpisodes.isEmpty()) return false;
        return shouldUseEpisodePosition(sourceEpisodes, indexEpisodesByNumber(tmdbEpisodes));
    }

    private static boolean shouldUseEpisodePosition(List<Episode> sourceEpisodes, Map<Integer, TmdbEpisode> tmdbEpisodes) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty() || tmdbEpisodes == null || tmdbEpisodes.isEmpty()) return false;
        Set<Integer> numbers = new HashSet<>();
        for (Episode episode : sourceEpisodes) {
            int number = episode == null ? -1 : episode.getNumber();
            if (number <= 0 || !numbers.add(number) || !tmdbEpisodes.containsKey(number)) return true;
        }
        return false;
    }

    static int resolveEpisodeNumber(Episode episode, int position, boolean usePosition) {
        if (episode == null) return -1;
        return usePosition ? position + 1 : episode.getNumber();
    }

    static boolean hasEpisodeMetadataChanged(TmdbEpisode current, TmdbEpisode updated) {
        if (current == updated) return false;
        if (current == null || updated == null) return true;
        return current.getNumber() != updated.getNumber()
                || !TextUtils.equals(current.getTitle(), updated.getTitle())
                || !TextUtils.equals(current.getDate(), updated.getDate())
                || !TextUtils.equals(current.getOverview(), updated.getOverview())
                || !TextUtils.equals(current.getStillUrl(), updated.getStillUrl())
                || Double.compare(current.getVoteAverage(), updated.getVoteAverage()) != 0
                || current.getRuntime() != updated.getRuntime()
                || current.getTmdbId() != updated.getTmdbId()
                || current.getSeasonNumber() != updated.getSeasonNumber();
    }

    /**
     * 评分文本，形如 "8.6"，无评分返回空串。
     */
    public String getRatingText() {
        if (tmdbDetail == null) return "";
        if (!tmdbDetail.has("vote_average") || tmdbDetail.get("vote_average").isJsonNull()) return "";
        double vote = tmdbDetail.get("vote_average").getAsDouble();
        return vote <= 0 ? "" : String.format(Locale.US, "%.1f", vote);
    }

    /**
     * 当前 TMDB 剧集的规范化集数信息。
     */
    public TmdbEpisodeInfo getEpisodeInfo() {
        if (episodeInfo == null) {
            episodeInfo = TmdbEpisodeInfo.from(tmdbItem == null ? "" : tmdbItem.getMediaType(), tmdbDetail, sourceSeasonNumber);
        }
        return episodeInfo;
    }

    public String getEpisodeDetailText() {
        return getEpisodeInfo().detailText(activity);
    }

    public String getEpisodeCompactText() {
        return getEpisodeInfo().compactText(activity);
    }

    /**
     * 类型文本，形如 "剧情 / 动作"，无类型返回空串。
     */
    public String getGenresText() {
        if (tmdbDetail == null || !tmdbDetail.has("genres")) return "";
        JsonElement element = tmdbDetail.get("genres");
        if (!element.isJsonArray()) return "";
        JsonArray genres = element.getAsJsonArray();
        List<String> names = new ArrayList<>();
        for (JsonElement g : genres) {
            if (!g.isJsonObject()) continue;
            JsonObject obj = g.getAsJsonObject();
            if (obj.has("name") && !obj.get("name").isJsonNull()) names.add(obj.get("name").getAsString());
        }
        return TextUtils.join(" / ", names);
    }

    /**
     * 年份文本，取首播/上映日期的年份，无则返回空串。
     */
    public String getYear() {
        if (tmdbDetail == null) return "";
        String date = readString(tmdbDetail, "first_air_date");
        if (TextUtils.isEmpty(date)) date = readString(tmdbDetail, "release_date");
        if (TextUtils.isEmpty(date) || date.length() < 4) return "";
        return date.substring(0, 4);
    }

    /**
     * 地区文本，优先取制片国家名称，其次原产国代码，无则返回空串。
     */
    public String getArea() {
        if (tmdbDetail == null) return "";
        if (tmdbDetail.has("production_countries") && tmdbDetail.get("production_countries").isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("production_countries")) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                String name = readString(obj, "name");
                if (!TextUtils.isEmpty(name)) names.add(name);
                if (names.size() >= 2) break;
            }
            if (!names.isEmpty()) return TextUtils.join(" / ", names);
        }
        if (tmdbDetail.has("origin_country") && tmdbDetail.get("origin_country").isJsonArray()) {
            List<String> codes = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("origin_country")) {
                if (e.isJsonNull()) continue;
                String code = e.getAsString();
                if (!TextUtils.isEmpty(code)) codes.add(code);
                if (codes.size() >= 2) break;
            }
            if (!codes.isEmpty()) return TextUtils.join(" / ", codes);
        }
        return "";
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    /**
     * 获取剧照列表（backdrops）。
     */
    public List<String> getPhotos() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.backdrops(tmdbDetail, tmdbConfig);
    }


    public List<String> getPosters() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.posters(tmdbDetail, tmdbConfig);
    }

    public List<String> getBackgroundPhotos() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.photos(tmdbDetail, tmdbConfig, preferLandscapeBackground());
    }

    private boolean preferLandscapeBackground() {
        return ResUtil.getScreenWidth(activity) >= ResUtil.getScreenHeight(activity);
    }

    /**
     * 获取主创团队（导演、编剧、制片）。
     */
    public List<TmdbPerson> getCreators() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.creators(tmdbDetail, tmdbConfig);
    }

    /**
     * 获取推荐影片（recommendations + similar 合并去重）。
     */
    public List<TmdbItem> getRecommendations() {
        return recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
    }

    public List<TmdbVideo> getRelatedVideos() {
        return relatedVideos == null ? new ArrayList<>() : new ArrayList<>(relatedVideos);
    }

    public void loadRelatedVideosAsync(int seasonNumber, int episodeNumber) {
        TmdbItem item = tmdbItem;
        Vod currentVod = vod;
        if (!loaded || item == null || !isReady()) {
            relatedVideos = new ArrayList<>();
            return;
        }
        int generation = loadGeneration;
        String contextKey = item.getMediaType() + ":" + item.getTmdbId() + ":" + seasonNumber + ":" + episodeNumber + ":" + tmdbConfig.getLanguage();
        if (contextKey.equals(relatedVideoContextKey) && (relatedVideoLoading || relatedVideos != null)) return;
        int videoGeneration = ++relatedVideoGeneration;
        relatedVideoContextKey = contextKey;
        relatedVideoLoading = true;
        relatedVideos = new ArrayList<>();
        backgroundTasks.submit(() -> {
            List<TmdbVideo> loadedVideos = new ArrayList<>();
            try {
                loadedVideos = tmdbService.relatedVideos(item, seasonNumber, episodeNumber, tmdbConfig);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb-video", "related async failed media=%s id=%d season=%d episode=%d error=%s", item.getMediaType(), item.getTmdbId(), seasonNumber, episodeNumber, e.getMessage());
            }
            List<TmdbVideo> result = loadedVideos;
            activity.runOnUiThread(() -> {
                boolean sameItem = tmdbItem != null && tmdbItem.getTmdbId() == item.getTmdbId() && Objects.equals(tmdbItem.getMediaType(), item.getMediaType());
                if (!isCurrentGeneration(generation) || relatedVideoGeneration != videoGeneration || !contextKey.equals(relatedVideoContextKey) || !sameItem) return;
                relatedVideoLoading = false;
                relatedVideos = new ArrayList<>(result);
                SpiderDebug.log("tmdb-video", "related async loaded media=%s id=%d season=%d episode=%d count=%d", item.getMediaType(), item.getTmdbId(), seasonNumber, episodeNumber, relatedVideos.size());
                notifyVodChanged(currentVod, generation, RefreshEvent.Type.VOD_RELATED_VIDEOS);
            });
        });
    }

    public boolean hasMoreRecommendations() {
        return recommendationHasMore;
    }

    public boolean hasMorePersonalTmdbRecommendations() {
        return personalTmdbPage != null && personalTmdbPage.hasMore();
    }

    public boolean hasMorePersonalDoubanRecommendations() {
        return personalDoubanPage != null && personalDoubanPage.hasMore();
    }

    public boolean hasMorePersonalAiRecommendations() {
        return personalAiPage != null && personalAiPage.hasMore();
    }

    public void loadMoreRecommendations(LoadMoreCallback callback) {
        if (recommendationLoading || !recommendationHasMore || tmdbItem == null || tmdbDetail == null) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        int nextPage = recommendationPage + 1;
        recommendationLoading = true;
        backgroundTasks.submit(() -> {
            List<TmdbItem> next = new ArrayList<>();
            boolean more = false;
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                List<TmdbItem> pageRecommendations = tmdbService.recommendations(tmdbItem, tmdbConfig, nextPage);
                List<TmdbItem> pageSimilar = tmdbService.similar(tmdbItem, tmdbConfig, nextPage);
                next = PersonalRecommendationService.rankTmdbItemsForContext(tmdbDetail, pageRecommendations, pageSimilar, Integer.MAX_VALUE);
                more = !pageRecommendations.isEmpty() || !pageSimilar.isEmpty();
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "load more recommendations failed page=%d error=%s", nextPage, e.getMessage());
            }
            List<TmdbItem> loadedItems = next;
            boolean hasMore = more;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                recommendationLoading = false;
                recommendationPage = nextPage;
                recommendationHasMore = hasMore;
                boolean changed = appendUnique(recommendations, loadedItems);
                if (callback != null) callback.onLoaded(changed);
            });
            service.enrichTmdbRatingsAsync(loadedItems, enriched -> applyRelatedRatingEnrichment(enriched, generation, vod));
        });
    }

    public void loadMorePersonalTmdbRecommendations(LoadMoreCallback callback) {
        loadMorePersonalRecommendations(true, callback);
    }

    public void loadMorePersonalDoubanRecommendations(LoadMoreCallback callback) {
        loadMorePersonalRecommendations(false, callback);
    }

    public void loadMorePersonalAiRecommendations(LoadMoreCallback callback) {
        if (callback != null) callback.onLoaded(false);
    }

    public void refreshPersonalRecommendations(LoadMoreCallback callback) {
        if (personalRefreshLoading || tmdbDetail == null) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        personalRefreshLoading = true;
        backgroundTasks.submit(() -> {
            boolean changed = false;
            PersonalRecommendationService.RecommendationPages pages = PersonalRecommendationService.RecommendationPages.empty();
            boolean aiChanged = false;
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                String tmdbFingerprint = service.historyFingerprint(vod, true);
                String doubanFingerprint = service.historyFingerprint(vod, false);
                String aiFingerprint = service.aiFingerprint(vod, tmdbItem);
                boolean sameTmdb = personalTmdbPage != null && personalTmdbPage.getHistoryFingerprint().equals(tmdbFingerprint);
                boolean sameDouban = personalDoubanPage != null && personalDoubanPage.getHistoryFingerprint().equals(doubanFingerprint);
                boolean sameAi = personalAiPage != null && personalAiPage.getHistoryFingerprint().equals(aiFingerprint);
                if (!sameTmdb || !sameDouban) {
                    pages = service.loadPage(vod, tmdbItem, tmdbDetail, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                    changed = true;
                }
                aiChanged = !sameAi;
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "refresh personal recommendations failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPages loadedPages = pages;
            boolean hasChanged = changed;
            boolean hasAiChanged = aiChanged;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                personalRefreshLoading = false;
                if (hasChanged) {
                    personalTmdbPage = loadedPages.getTmdb();
                    personalDoubanPage = loadedPages.getDouban();
                    personalTmdbRecommendations = personalTmdbPage.getItems();
                    personalDoubanRecommendations = personalDoubanPage.getItems();
                }
                if (callback != null) callback.onLoaded(hasChanged);
                if (hasAiChanged) loadPersonalAiRecommendationsAsync(vod, tmdbItem, generation);
            });
            if (hasChanged) service.enrichTmdbPageRatingsAsync(loadedPages.getTmdb(), enriched -> applyPersonalTmdbRatingEnrichment(enriched, generation, vod));
        });
    }

    private void loadMorePersonalRecommendations(boolean tmdb, LoadMoreCallback callback) {
        PersonalRecommendationService.RecommendationPage page = tmdb ? personalTmdbPage : personalDoubanPage;
        if (page == null || !page.hasMore() || (tmdb ? personalTmdbLoading : personalDoubanLoading)) {
            if (callback != null) callback.onLoaded(false);
            return;
        }
        int generation = loadGeneration;
        if (tmdb) personalTmdbLoading = true;
        else personalDoubanLoading = true;
        backgroundTasks.submit(() -> {
            PersonalRecommendationService.RecommendationPage nextPage;
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                nextPage = tmdb
                        ? service.loadTmdbPage(vod, tmdbItem, tmdbDetail, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                        : service.loadDoubanPage(vod, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "load more personal recommendations failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            activity.runOnUiThread(() -> {
                if (!isCurrentGeneration(generation)) return;
                if (tmdb) {
                    personalTmdbLoading = false;
                    personalTmdbPage = loadedPage;
                    boolean changed = appendUnique(personalTmdbRecommendations, loadedPage.getItems());
                    if (callback != null) callback.onLoaded(changed);
                } else {
                    personalDoubanLoading = false;
                    personalDoubanPage = loadedPage;
                    boolean changed = appendUnique(personalDoubanRecommendations, loadedPage.getItems());
                    if (callback != null) callback.onLoaded(changed);
                }
            });
            if (tmdb) service.enrichTmdbPageRatingsAsync(loadedPage, enriched -> applyPersonalTmdbRatingEnrichment(enriched, generation, vod));
        });
    }

    private void applyRelatedRatingEnrichment(List<TmdbItem> enriched, int generation, Vod eventVod) {
        activity.runOnUiThread(() -> {
            if (!isCurrentGeneration(generation) || !mergeRecommendationRatings(recommendations, enriched)) return;
            if (eventVod != null) notifyVodChanged(eventVod, generation, RefreshEvent.Type.VOD_RECOMMENDATIONS);
        });
    }

    private void applyPersonalTmdbRatingEnrichment(PersonalRecommendationService.RecommendationPage enriched, int generation, Vod eventVod) {
        activity.runOnUiThread(() -> {
            if (!isCurrentGeneration(generation) || enriched == null || !mergeRecommendationRatings(personalTmdbRecommendations, enriched.getItems())) return;
            if (personalTmdbPage != null) personalTmdbPage = personalTmdbPage.withItems(personalTmdbRecommendations);
            if (eventVod != null) notifyVodChanged(eventVod, generation, RefreshEvent.Type.VOD_PERSONAL);
        });
    }

    private boolean hasMoreTmdbRelatedPages(JsonObject detail, String key) {
        JsonObject object = detail != null && detail.has(key) && detail.get(key).isJsonObject() ? detail.getAsJsonObject(key) : null;
        if (object == null || !object.has("total_pages") || object.get("total_pages").isJsonNull()) return false;
        try {
            return object.get("total_pages").getAsInt() > 1;
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean appendUnique(List<TmdbItem> target, List<TmdbItem> source) {
        if (target == null || source == null || source.isEmpty()) return false;
        boolean changed = false;
        for (TmdbItem item : source) {
            if (item == null || containsRecommendation(target, item)) continue;
            target.add(item);
            changed = true;
        }
        return changed;
    }

    private boolean containsRecommendation(List<TmdbItem> items, TmdbItem target) {
        if (items == null || target == null) return false;
        for (TmdbItem item : items) if (TmdbRecommendationRows.sameIdentity(item, target)) return true;
        return false;
    }

    static boolean removeRecommendationFrom(List<TmdbItem> items, TmdbItem target) {
        return items != null && target != null && items.removeIf(item -> TmdbRecommendationRows.sameIdentity(item, target));
    }

    public static boolean mergeRecommendationRatings(List<TmdbItem> current, List<TmdbItem> enriched) {
        if (current == null || enriched == null || current.isEmpty() || enriched.isEmpty()) return false;
        boolean changed = false;
        for (TmdbItem candidate : enriched) {
            if (candidate == null || candidate.getDoubanRating() <= 0) continue;
            for (int index = 0; index < current.size(); index++) {
                TmdbItem existing = current.get(index);
                if (!TmdbRecommendationRows.sameIdentity(existing, candidate)) continue;
                if (Double.compare(existing.getDoubanRating(), candidate.getDoubanRating()) != 0) {
                    current.set(index, candidate);
                    changed = true;
                }
                break;
            }
        }
        return changed;
    }

}
