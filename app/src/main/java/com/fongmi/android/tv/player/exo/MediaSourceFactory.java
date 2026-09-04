package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.cache.DiskCacheCapacityPolicy;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    private static final PriorityTaskManager PLAYBACK_PRIORITY_MANAGER = new PriorityTaskManager();
    private static final CacheCapacityState CACHE_CAPACITY_STATE = new CacheCapacityState();

    private static StandaloneDatabaseProvider databaseProvider;
    private static Cache cache;

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private OkHttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    @Nullable private final ExoDolbyVisionPlaybackState dolbyVisionPlaybackState;

    public MediaSourceFactory() {
        this(null);
    }

    MediaSourceFactory(
            @Nullable ExoDolbyVisionPlaybackState dolbyVisionPlaybackState) {
        this.dolbyVisionPlaybackState = dolbyVisionPlaybackState;
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory()).setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
    }

    static DataSource.Factory createUpstreamDataSourceFactory(Map<String, String> headers) {
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(OkHttp.player());
        applyHeaders(factory, headers);
        DataSource.Factory upstream = new DefaultDataSource.Factory(App.get(), factory);
        DataSource.Factory recovered = new HttpEofRecoveryDataSource.Factory(upstream);
        return new PriorityTaskDataSource.Factory(recovered, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK_PRELOAD, true);
    }

    static synchronized Cache getCache() {
        if (cache != null) return cache;
        File dir = Path.exoCache();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, FileUtil.getDirectorySize(dir));
        long capacityBytes = initialCapacityBytes(decision);
        Cache created = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(capacityBytes), getDatabaseProvider());
        cache = created;
        CACHE_CAPACITY_STATE.recordCreated(capacityBytes);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "created capacityBytes=%d policy=%s existingBytes=%d availableBytes=%d reserveBytes=%d", capacityBytes, decision.state(), decision.existingCacheBytes(), decision.availableStorageBytes(), decision.reserveBytes());
        return created;
    }

    public static synchronized void acquireCacheSession() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("next-player-session");
        CACHE_CAPACITY_STATE.acquireSession();
    }

    public static synchronized void releaseCacheSession() {
        CACHE_CAPACITY_STATE.releaseSession();
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        if (isReliable(decision) && CACHE_CAPACITY_STATE.canReleasePending()) rebuildCacheLocked("last-player-release");
    }

    private static StandaloneDatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    private static DiskCacheCapacityPolicy.Decision resolveCapacity(File dir, long existingCacheBytes) {
        FileUtil.StorageSpace storage = FileUtil.getStorageSpace(dir);
        return DiskCacheCapacityPolicy.resolve(storage.available(), PreloadSetting.getPreloadSizeBytes(PlayerSetting.EXO), existingCacheBytes, storage.availableBytes(), storage.totalBytes());
    }

    private static long initialCapacityBytes(DiskCacheCapacityPolicy.Decision decision) {
        if (!isReliable(decision)) return decision.existingCacheBytes();
        return decision.effectiveCapacityBytes();
    }

    static synchronized long getCacheCapacityBytes() {
        DiskCacheCapacityPolicy.Decision decision = refreshPendingCacheCapacity();
        return cache == null ? initialCapacityBytes(decision) : CACHE_CAPACITY_STATE.actualCapacityBytes();
    }

    static synchronized long getPendingCacheCapacityBytes() {
        refreshPendingCacheCapacity();
        return CACHE_CAPACITY_STATE.pendingCapacityBytes();
    }

    static synchronized ExoCacheWritePolicy.Decision getCacheWriteDecision() {
        DiskCacheCapacityPolicy.Decision capacity = refreshPendingCacheCapacity();
        long actualCapacityBytes = cache == null ? 0 : CACHE_CAPACITY_STATE.actualCapacityBytes();
        return ExoCacheWritePolicy.resolve(capacity, actualCapacityBytes);
    }

    private static DiskCacheCapacityPolicy.Decision refreshPendingCacheCapacity() {
        File dir = Path.exoCache();
        long existingCacheBytes = cache == null ? FileUtil.getDirectorySize(dir) : cache.getCacheSpace();
        DiskCacheCapacityPolicy.Decision decision = resolveCapacity(dir, existingCacheBytes);
        if (isReliable(decision)) CACHE_CAPACITY_STATE.report(decision.effectiveCapacityBytes());
        return decision;
    }

    private static boolean isReliable(DiskCacheCapacityPolicy.Decision decision) {
        return decision.state() != DiskCacheCapacityPolicy.State.UNAVAILABLE;
    }

    private static void rebuildCacheLocked(String reason) {
        if (!releaseCacheLocked(reason)) return;
        try {
            getCache();
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "rebuild-failed reason=%s error=%s", reason, e.getClass().getSimpleName());
        }
    }

    private static boolean releaseCacheLocked(String reason) {
        if (cache == null) return false;
        Cache releasing = cache;
        long actual = CACHE_CAPACITY_STATE.actualCapacityBytes();
        long pending = CACHE_CAPACITY_STATE.pendingCapacityBytes();
        try {
            releasing.release();
            cache = null;
            CACHE_CAPACITY_STATE.recordReleased();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "released reason=%s actualCapacityBytes=%d pendingCapacityBytes=%d activeSessions=%d", reason, actual, pending, CACHE_CAPACITY_STATE.activeSessions());
            return true;
        } catch (RuntimeException e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-cache", "release-failed reason=%s error=%s activeSessions=%d", reason, e.getClass().getSimpleName(), CACHE_CAPACITY_STATE.activeSessions());
            return false;
        }
    }

    static boolean isConcatenatingUrl(String url) {
        return url != null && url.contains(CONCAT_SOURCE_SEPARATOR) && url.contains(CONCAT_DURATION_SEPARATOR);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        applyHeaders(getHttpDataSourceFactory(), ExoUtil.extractHeaders(mediaItem));
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        if (isConcatenatingUrl(url)) return createConcatenatingMediaSource(mediaItem, url);
        else return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, String url) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length >= 2) builder.add(defaultMediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(UrlUtil.uri(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) {
            ExtractorsFactory defaults = new DefaultExtractorsFactory()
                    .setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(
                            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
            extractorsFactory = new DolbyVisionP81ExtractorsFactory(
                    defaults, dolbyVisionPlaybackState);
        }
        return extractorsFactory;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) {
            DataSource.Factory cacheDataSource = getCacheDataSource(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()));
            DataSource.Factory trackedDataSource = new PlaybackBytePositionDataSource.Factory(cacheDataSource);
            dataSourceFactory = new PriorityTaskDataSource.Factory(trackedDataSource, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
        }
        return dataSourceFactory;
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        return new CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(new HttpEofRecoveryDataSource.Factory(upstreamFactory))
                .setCacheWriteDataSinkFactory(null)
                .setEventListener(PlaybackCacheMetrics.listener())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private OkHttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }

    private static void applyHeaders(OkHttpDataSource.Factory factory, Map<String, String> headers) {
        Map<String, String> sanitized = sanitizeHeaders(headers);
        String userAgent = removeUserAgentHeader(sanitized);
        factory.setUserAgent(userAgent).setDefaultRequestProperties(sanitized);
    }

    static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return sanitized;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            sanitized.put(key, entry.getValue().trim());
        }
        return sanitized;
    }

    static String removeUserAgentHeader(Map<String, String> headers) {
        String userAgent = null;
        Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!"User-Agent".equalsIgnoreCase(entry.getKey())) continue;
            String value = entry.getValue().trim();
            if (!value.isEmpty()) userAgent = value;
            iterator.remove();
        }
        return userAgent;
    }
}
