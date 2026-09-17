package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;
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
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.ContentMetadataMutations;
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
import com.google.common.base.Ascii;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import okhttp3.Call;

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
        OkHttpDataSource.Factory factory = createHttpDataSourceFactory(OkHttp.player(), headers);
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

    public static boolean isConcatenatingUrl(String url) {
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
        Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
        DefaultMediaSourceFactory itemFactory = createItemMediaSourceFactory(headers);
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        if (isConcatenatingUrl(url)) return createConcatenatingMediaSource(mediaItem, url, itemFactory);
        else return itemFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(
            MediaItem mediaItem, String url, DefaultMediaSourceFactory itemFactory) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : url.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX);
            if (info.length >= 2) {
                builder.add(itemFactory.createMediaSource(
                        mediaItem.buildUpon().setUri(UrlUtil.uri(info[0])).build()),
                        Long.parseLong(info[1]));
            }
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) {
            ExtractorsFactory defaults = new DefaultExtractorsFactory()
                    .setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(
                            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
            ExtractorsFactory withApe = new ExtractorsFactory() {
                @Override
                public androidx.media3.extractor.Extractor[] createExtractors() {
                    return prependApe(defaults.createExtractors());
                }

                @Override
                public androidx.media3.extractor.Extractor[] createExtractors(
                        Uri uri, Map<String, List<String>> responseHeaders) {
                    return prependApe(defaults.createExtractors(uri, responseHeaders));
                }
            };
            extractorsFactory = new DolbyVisionP81ExtractorsFactory(
                    withApe, dolbyVisionPlaybackState);
        }
        return extractorsFactory;
    }

    private static androidx.media3.extractor.Extractor[] prependApe(
            androidx.media3.extractor.Extractor[] defaults) {
        androidx.media3.extractor.Extractor[] extractors =
                new androidx.media3.extractor.Extractor[defaults.length + 1];
        extractors[0] = new ApeExtractor();
        System.arraycopy(defaults, 0, extractors, 1, defaults.length);
        return extractors;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) {
            DataSource.Factory cacheDataSource = getCacheDataSource(
                    new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()),
                    Map.of());
            DataSource.Factory adblockDataSource = new ExoHlsAdblockDataSource.Factory(cacheDataSource);
            DataSource.Factory trackedDataSource = new PlaybackBytePositionDataSource.Factory(adblockDataSource);
            dataSourceFactory = new PriorityTaskDataSource.Factory(trackedDataSource, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
        }
        return dataSourceFactory;
    }

    private DataSource.Factory getDataSourceFactory(Map<String, String> headers) {
        OkHttpDataSource.Factory httpFactory = createHttpDataSourceFactory(OkHttp.player(), headers);
        DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(App.get(), httpFactory);
        DataSource.Factory cacheDataSource = getCacheDataSource(upstreamFactory, headers);
        DataSource.Factory adblockDataSource = new ExoHlsAdblockDataSource.Factory(cacheDataSource);
        DataSource.Factory trackedDataSource = new PlaybackBytePositionDataSource.Factory(adblockDataSource);
        return new PriorityTaskDataSource.Factory(
                trackedDataSource, PLAYBACK_PRIORITY_MANAGER, C.PRIORITY_PLAYBACK, false);
    }

    private DefaultMediaSourceFactory createItemMediaSourceFactory(Map<String, String> headers) {
        return new DefaultMediaSourceFactory(getDataSourceFactory(headers), getExtractorsFactory())
                .setLoadOnlySelectedTracks(PlaybackPerformanceSetting.isLoadOnlySelectedTracksEnabled());
    }

    private CacheDataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        return getCacheDataSource(upstreamFactory, Map.of());
    }

    private CacheDataSource.Factory getCacheDataSource(
            DataSource.Factory upstreamFactory, Map<String, String> headers) {
        return new CacheDataSource.Factory()
                .setCache(getCache())
                .setUpstreamDataSourceFactory(new HttpEofRecoveryDataSource.Factory(upstreamFactory))
                .setCacheWriteDataSinkFactory(null)
                .setCacheKeyFactory(cacheKeyFactory(headers))
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

    static OkHttpDataSource.Factory createHttpDataSourceFactory(
            Call.Factory client, Map<String, String> headers) {
        // Keep the player's CookieJar, redirect policy, proxy and connection pool unchanged.
        OkHttpDataSource.Factory factory = new OkHttpDataSource.Factory(client);
        applyHeaders(factory, headers);
        return factory;
    }

    /**
     * Builds a cache namespace from request headers without putting credentials into the cache
     * key. The namespace is shared by the foreground MediaSource and any future item-scoped
     * preload for the same immutable request snapshot.
     */
    static String cacheNamespace(Map<String, String> headers) {
        Map<String, String> sanitized = sanitizeHeaders(headers);
        String userAgent = removeUserAgentHeader(sanitized);
        if (userAgent != null) sanitized.put("User-Agent", userAgent);
        // No request identity was added: retain the existing URL/custom-key cache hits.
        if (sanitized.isEmpty()) return "";
        TreeMap<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        normalized.putAll(sanitized);
        boolean hasCaseVariants = normalized.size() != sanitized.size();
        // Media3 retains case variants until OkHttp builds the request. Do not guess which
        // credential wins or silently discard one when computing its cache identity.
        if (hasCaseVariants) normalized = new TreeMap<>(sanitized);
        StringBuilder value = new StringBuilder();
        value.append(hasCaseVariants ? 'D' : 'S');
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String name = hasCaseVariants ? entry.getKey() : Ascii.toLowerCase(entry.getKey());
            // Length-prefix fields: cache lookup happens before HTTP validation, so newline
            // delimiters would allow even a rejected header to alias valid cached credentials.
            value.append(name.length()).append(':').append(name)
                    .append(entry.getValue().length()).append(':').append(entry.getValue());
        }
        return sha256(value.toString());
    }

    static CacheKeyFactory cacheKeyFactory(Map<String, String> headers) {
        String namespace = cacheNamespace(headers);
        return dataSpec -> cacheKey(namespace, dataSpec.key == null ? dataSpec.uri.toString() : dataSpec.key);
    }

    static String cacheKey(Map<String, String> headers, String resourceKey) {
        return cacheKey(cacheNamespace(headers), resourceKey);
    }

    private static String cacheKey(String namespace, String resourceKey) {
        return namespace.isEmpty() ? resourceKey : namespace + "|" + resourceKey;
    }

    /**
     * Gives Media3's PreCacheHelper an item-scoped view of the shared cache.  PreCacheHelper does
     * not expose CacheKeyFactory, so the scope has to be applied at the Cache boundary.  The
     * foreground CacheDataSource uses the same {@link #cacheKey(Map, String)} contract.
     */
    static Cache scopedCache(Map<String, String> headers) {
        return scopedCache(getCache(), headers);
    }

    static Cache scopedCache(Cache delegate, Map<String, String> headers) {
        return new ScopedCache(delegate, cacheNamespace(headers));
    }

    private static final class ScopedCache implements Cache {

        private final Cache delegate;
        private final String namespace;
        private final String prefix;

        private ScopedCache(Cache delegate, String namespace) {
            this.delegate = delegate;
            this.namespace = namespace;
            this.prefix = namespace.isEmpty() ? "" : namespace + "|";
        }

        private String key(String rawKey) {
            return cacheKey(namespace, rawKey);
        }

        @Override
        public long getUid() {
            return delegate.getUid();
        }

        @Override
        public void release() {
            // The process cache is shared by foreground playback and other sessions.
        }

        @Override
        public NavigableSet<CacheSpan> addListener(String rawKey, Listener listener) {
            return delegate.addListener(key(rawKey), listener);
        }

        @Override
        public void removeListener(String rawKey, Listener listener) {
            delegate.removeListener(key(rawKey), listener);
        }

        @Override
        public NavigableSet<CacheSpan> getCachedSpans(String rawKey) {
            return delegate.getCachedSpans(key(rawKey));
        }

        @Override
        public Set<String> getKeys() {
            Set<String> keys = new TreeSet<>();
            for (String key : delegate.getKeys()) {
                if (key.startsWith(prefix)) keys.add(key.substring(prefix.length()));
            }
            return keys;
        }

        @Override
        public long getCacheSpace() {
            return delegate.getCacheSpace();
        }

        @Override
        public CacheSpan startReadWrite(String rawKey, long position, long length)
                throws InterruptedException, CacheException {
            return delegate.startReadWrite(key(rawKey), position, length);
        }

        @Override
        public CacheSpan startReadWriteNonBlocking(String rawKey, long position, long length)
                throws CacheException {
            return delegate.startReadWriteNonBlocking(key(rawKey), position, length);
        }

        @Override
        public File startFile(String rawKey, long position, long length) throws CacheException {
            return delegate.startFile(key(rawKey), position, length);
        }

        @Override
        public void commitFile(File file, long length) throws CacheException {
            delegate.commitFile(file, length);
        }

        @Override
        public void releaseHoleSpan(CacheSpan holeSpan) {
            delegate.releaseHoleSpan(holeSpan);
        }

        @Override
        public void removeResource(String rawKey) {
            delegate.removeResource(key(rawKey));
        }

        @Override
        public void removeSpan(CacheSpan span) {
            delegate.removeSpan(span);
        }

        @Override
        public boolean isCached(String rawKey, long position, long length) {
            return delegate.isCached(key(rawKey), position, length);
        }

        @Override
        public long getCachedLength(String rawKey, long position, long length) {
            return delegate.getCachedLength(key(rawKey), position, length);
        }

        @Override
        public long getCachedBytes(String rawKey, long position, long length) {
            return delegate.getCachedBytes(key(rawKey), position, length);
        }

        @Override
        public void applyContentMetadataMutations(String rawKey, ContentMetadataMutations mutations)
                throws CacheException {
            delegate.applyContentMetadataMutations(key(rawKey), mutations);
        }

        @Override
        public ContentMetadata getContentMetadata(String rawKey) {
            return delegate.getContentMetadata(key(rawKey));
        }
    }

    private static String sha256(String value) {
        try {
            // Preserve every code unit, including malformed headers that HTTP will reject.
            // UTF-8's replacement of unpaired surrogates would alias a literal '?' credential.
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < value.length(); i++) {
                hash.update((byte) (value.charAt(i) >>> 8));
                hash.update((byte) value.charAt(i));
            }
            byte[] digest = hash.digest();
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by the Android runtime", e);
        }
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
