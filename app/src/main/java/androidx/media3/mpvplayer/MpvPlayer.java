package androidx.media3.mpvplayer;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Build;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.player.PreloadPausePolicy;
import com.fongmi.android.tv.player.cache.DiskCacheCapacityPolicy;
import com.fongmi.android.tv.player.cache.PlaybackDiskBufferStore;
import com.fongmi.android.tv.player.engine.PlayerCacheState;
import com.fongmi.android.tv.player.iso.IsoSessionManager;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.player.mpv.MpvDirectAudioPolicy;
import com.fongmi.android.tv.player.mpv.MpvNetworkRecoveryPolicy;
import com.fongmi.android.tv.player.mpv.MpvSubtitleStylePolicy;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import is.xyz.mpv.MPVLib;

@UnstableApi
public final class MpvPlayer extends SimpleBasePlayer implements MPVLib.EventObserver, MPVLib.LogObserver {

    private static final String TAG = "TV-mpv";
    private static final String SIZE_TAG = "MPV_SIZE";
    private static final long STATE_REFRESH_INTERVAL_MS = 1000;
    private static final long PROPERTY_EVENT_COALESCE_MS = 50;
    private static final long MAIN_THREAD_WATCHDOG_INTERVAL_MS = 500;
    private static final long MAIN_THREAD_STALL_THRESHOLD_MS = 1500;
    private static final long MAIN_THREAD_STALL_LOG_INTERVAL_MS = 5000;
    // A 60 Hz display has only 16.67 ms per frame. Record shorter calls too;
    // a 100 ms threshold hides the single-frame misses that accumulate into bursts.
    private static final long SLOW_MPV_NATIVE_CALL_THRESHOLD_MS = 16;
    private static final long END_FILE_VALIDATION_DELAY_MS = 800;
    private static final long LOAD_START_RETRY_DELAY_MS = 1000;
    private static final long POST_RESTART_TRACK_REFRESH_DELAY_MS = 120;
    private static final long INITIAL_TRACK_SELECTION_LOAD_TIMEOUT_MS = 15000;
    private static final long INITIAL_TRACK_SELECTION_RESTORE_TIMEOUT_MS = 1500;
    private static final long MEDIA_REPLACEMENT_STOP_TIMEOUT_MS = 1200;
    private static final long TRACK_REFRESH_DEBOUNCE_MS = 80;
    // Last resort out of the seek buffering window. PLAYBACK_RESTART and the
    // paused-for-cache observer are the normal exits; this only covers a seek mpv
    // never answers, so it must outlast a slow-but-working seek. Matches the seek
    // latch timeout in MpvSeekPositionState so both give up on the same evidence.
    private static final long SEEK_BUFFERING_TIMEOUT_MS = MpvSeekPositionState.TARGET_TIMEOUT_MS;

    private static final int MAX_OBSERVED_TRACKS = 64;
    private static final int MAX_OBSERVED_CHAPTERS = 512;
    private static final float FRAME_RATE_REQUEST_EPSILON = 0.001f;
    private static final int MAX_LOAD_START_RETRIES = 2;
    private static final double SECONDS_TO_MS = 1000.0;
    private static final double DEFAULT_SUBTITLE_TEXT_SIZE_FRACTION = 0.0533;
    private static final double MICROSECONDS_TO_SECONDS = 1_000_000.0;
    private static final String CONCAT_SOURCE_SEPARATOR = "***";
    private static final String CONCAT_SOURCE_SEPARATOR_REGEX = "\\*\\*\\*";
    private static final String CONCAT_DURATION_SEPARATOR = "|||";
    private static final String CONCAT_DURATION_SEPARATOR_REGEX = "\\|\\|\\|";
    private static final String HLS_LOAD_OPTIONS = "demuxer=lavf,demuxer-lavf-format=hls,demuxer-lavf-probesize=10485760,demuxer-lavf-analyzeduration=5";
    private static final String DASH_LOAD_OPTIONS = "demuxer=lavf,demuxer-lavf-format=dash,demuxer-lavf-probesize=10485760,demuxer-lavf-analyzeduration=5";
    private static final int RECENT_LOG_LIMIT = 32;
    private static final Object NATIVE_CONTEXT_LOCK = new Object();
    private static final AtomicLong NATIVE_REQUEST_IDS = new AtomicLong(1);
    @Nullable
    private static MpvPlayer nativeContextOwner;

    public static final String ERROR_HLS_PLAYBACK_FAILED = "MPV_HLS_PLAYBACK_FAILED";
    public static final String ERROR_LOAD_FAILED = "MPV_LOAD_FAILED";
    public static final String ERROR_NETWORK_FAILED = "MPV_NETWORK_FAILED";
    public static final String ERROR_DRM_UNSUPPORTED = "MPV_DRM_UNSUPPORTED";
    public static final String ERROR_UNEXPECTED_IMAGE = "MPV_UNEXPECTED_IMAGE";
    public static final String ERROR_NO_AV_DATA = "MPV_NO_AV_DATA";
    public static final String ERROR_INVALID_MEDIA_DATA = "MPV_INVALID_MEDIA_DATA";
    public static final String ERROR_DECODE_FAILED = "MPV_DECODE_FAILED";
    public static final String ERROR_VIDEO_OUTPUT_FAILED = "MPV_VIDEO_OUTPUT_FAILED";

    private static final Commands COMMANDS = new Commands.Builder()
            .add(COMMAND_PLAY_PAUSE)
            .add(COMMAND_PREPARE)
            .add(COMMAND_STOP)
            .add(COMMAND_RELEASE)
            .add(COMMAND_SET_REPEAT_MODE)
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_TIMELINE)
            .add(COMMAND_GET_METADATA)
            .add(COMMAND_SET_MEDIA_ITEM)
            .add(COMMAND_CHANGE_MEDIA_ITEMS)
            .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(COMMAND_GET_VOLUME)
            .add(COMMAND_SET_VOLUME)
            .add(COMMAND_SET_SPEED_AND_PITCH)
            .add(COMMAND_GET_TEXT_OFFSET)
            .add(COMMAND_SET_TEXT_OFFSET)
            .add(COMMAND_GET_AUDIO_OFFSET)
            .add(COMMAND_SET_AUDIO_OFFSET)
            .add(COMMAND_SET_VIDEO_SURFACE)
            .add(COMMAND_GET_TRACKS)
            .build();

    private final Context context;
    private final MpvPlayerConfig config;
    private final Handler mainHandler;
    private final Object propertyEventLock;
    private final Map<String, Object> coalescedPropertyEvents;
    private final Map<String, Integer> cachedVideoIntProperties;
    private final Runnable coalescedPropertyDrainRunnable;
    private final Runnable stateRefreshRunnable;
    private final Runnable mainThreadHeartbeatRunnable;
    private final Runnable mainThreadWatchdogRunnable;
    private final AtomicBoolean mainThreadHeartbeatPending;
    private final Runnable endFileValidationRunnable;
    private final Runnable loadStartRetryRunnable;
    private final Runnable seekBufferingTimeoutRunnable;
    private final Runnable mediaReplacementStopTimeoutRunnable;
    private final Runnable trackRefreshRunnable;
    private final Runnable chapterRefreshRunnable;
    private final Runnable initialTrackSelectionGateTimeoutRunnable;
    private final Runnable isoTrackMetadataReadyListener;
    private final MpvHlsProxy hlsProxy;
    private final MpvAutoCacheBaselineState autoCacheBaselineState;
    private final MpvAutoHlsBitrateState autoHlsBitrateState;
    private final MpvCacheObserverState cacheObserverState;
    private final MpvPropertyCache propertyCache;
    private final Set<String> observedPropertyNames;
    private final MpvCacheTimeState cacheTimeState;
    private final MpvSeekPositionState seekPositionState;
    private final MpvMediaReplacementCoordinator mediaReplacementCoordinator;
    private final List<String> recentLogs;
    private final List<ParcelFileDescriptor> contentFds;
    @Nullable
    private final File subtitleDiagnosticFile;
    private final File preloadCacheDir;
    private final long preloadCacheCapacityBytes;
    private final MpvSurfaceTeardownPolicy surfaceTeardownPolicy;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private SurfaceHolder osdSurfaceHolder;
    private SurfaceView osdSurfaceView;
    private Surface surface;
    private Surface osdSurface;
    private Surface attachedSurface;
    private Surface attachedOsdSurface;
    private Surface lastFrameRateSurface;
    private Object videoOutput;
    private MpvLutShader lutShader;
    private String currentPlayableUri;
    private String playbackTraceId = PlaybackTrace.NONE;
    private volatile PlaybackResourceClassifier.Classification resourceClassification;
    private String currentIsoUri;
    private boolean isoTrackListDumped;
    private long isoMetadataListenerSessionId = -1;
    private String appliedLutShaderPath;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private Tracks currentTracks;
    private VideoTrackDiagnostics selectedVideoTrackDiagnostics;
    private VideoTrackDiagnostics availableVideoTrackDiagnostics;
    private List<MediaEdition> currentChapters;
    private VideoSize videoSize;
    private String lastVideoSizeCandidateLog;
    private int playbackState;
    private long initialSeekPositionMs;
    private long loadStartPositionMs;
    private long cachedPositionMs;
    private long cachedDurationMs;
    private long cachedCacheDurationMs;
    private long cachedCacheEndMs;
    private long cachedCacheReaderPositionMs;
    private long cachedCacheForwardBytes;
    private long cachedCacheTotalBytes;
    private long cachedCacheFileBytes;
    private long cachedCacheSpeedBytesPerSecond;
    private long cachedCacheSpeedSampleAtMs = -1;
    private long cachedCacheUnderrunCount;
    private long cachedSelectedHlsBitrate;
    private long effectiveDemuxerMaxBytes;
    private long preloadCacheBaselineBytes;
    private long preloadCacheTargetBytes;
    private int preloadCacheBaselineSeconds;
    private int preloadCacheTargetSeconds;
    private long textOffsetMs;
    private long audioOffsetMs;
    private float subtitleTextSize;
    private float subtitlePosition;
    private float videoAspectRatio;
    private boolean stretchVideo;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private boolean initialized;
    private boolean released;
    private boolean surfaceAttached;
    private boolean osdSurfaceAttached;
    private boolean osdSurfaceRequested;
    private boolean initialOsdSurfaceRequested;
    private boolean osdSurfaceUsedForCurrentMedia;
    private boolean initialTrackSelectionGateRequested;
    private boolean initialTrackSelectionGateActive;
    private String initialSubtitleTrackId;
    private boolean pendingOsdSurfaceAttach;
    private volatile long pendingOsdSurfaceRequestId;
    private long pendingOsdLoadGeneration = C.INDEX_UNSET;
    private Surface pendingOsdSurface;
    private boolean fileLoaded;
    private boolean loadStarted;
    private boolean playbackRestarted;
    private boolean stopping;
    // True between a seek request and the discontinuity that resolves it. Lets the
    // rebuffer accounting in PlayerManager tell a user-initiated seek apart from a
    // network stall, since both surface as STATE_BUFFERING.
    private boolean seekBufferingActive;
    private boolean eofReached;
    private boolean idleActive;
    private boolean currentLikelyHls;
    private boolean currentLikelyDash;
    private boolean preloadCacheOverlayApplied;
    private boolean sawNoAvData;
    private boolean sawInvalidData;
    private boolean sawPngVideo;
    private boolean sawNetworkError;
    private boolean sawDecodeError;
    private boolean sawVideoOutputError;
    private boolean sawDrmError;
    private boolean cachedCacheIdle;
    private boolean cachedCacheUnderrun;
    private boolean cachedCacheBof;
    private boolean cachedCacheEof;
    private boolean observedCurrentVo;
    private boolean observedHwdecCurrent;
    private boolean preferAacApplied;
    private boolean directAudioApplied;
    private boolean audioTrackManuallySelected;
    private BiConsumer<Integer, Integer> videoSizeProbeListener;
    private boolean trackRefreshScheduled;
    private boolean chapterRefreshScheduled;
    private boolean trackRefreshPrioritized;
    private int trackRefreshCoalescedEvents;
    private long trackRefreshFirstScheduledAtMs;
    private String trackRefreshLastReason;
    private long fileLoadedAtElapsedRealtimeMs;
    private boolean coalescedPropertyDrainScheduled;
    private int loadStartRetryCount;
    private int videoReconfigCount;
    private int currentChapter;
    private int cachedCacheBufferingState;
    private int surfaceWidth;
    private int surfaceHeight;
    private int osdSurfaceWidth;
    private int osdSurfaceHeight;
    private String appliedAndroidSurfaceSize;
    private String appliedAndroidOsdSurfaceSize;
    private String attachedVo;
    private String effectiveVo;
    private String lastFailureLog;
    private int lastEndFileReason;
    private int lastEndFileError;
    private String lastEndFileErrorText;
    private String cachedCurrentVo;
    private String cachedCurrentGpuContext;
    private String cachedGpuApi;
    private String cachedCurrentAo;
    private String cachedAudioDevice;
    private String cachedHwdecCurrent;
    private double cachedAvSyncSeconds;
    private double cachedDisplayFps;
    private double cachedEstimatedDisplayFps;
    private double cachedContainerFps;
    private double cachedEstimatedVfFps;
    private float cachedContentFrameRate;
    private float lastRequestedFrameRate = Float.NaN;
    private int lastFrameRateCompatibility = -1;
    private int lastFrameRateStrategy = -1;
    private long cachedDecoderDroppedFrames;
    private long cachedOutputDroppedFrames;
    private boolean observedDroppedFrames;
    private long cachedMistimedFrames;
    private long cachedDelayedFrames;
    private boolean cachedDisplaySyncActive;
    private float volume;
    private HandlerThread mainThreadWatchdogThread;
    private Handler mainThreadWatchdogHandler;
    private volatile boolean mainThreadWatchdogRunning;
    private volatile long mainThreadHeartbeatPostedAtMs;
    private volatile long lastMainThreadStallLogAtMs;
    private volatile long activeMpvNativeCallStartedAtMs;
    private volatile String activeMpvNativeCallKind = "";
    private volatile String activeMpvNativeCallTarget = "";

    public MpvPlayer(Context context, MpvPlayerConfig config) {
        super(Looper.getMainLooper());
        this.context = context.getApplicationContext();
        this.config = config;
        mainHandler = new Handler(Looper.getMainLooper());
        propertyEventLock = new Object();
        coalescedPropertyEvents = new LinkedHashMap<>();
        cachedVideoIntProperties = new LinkedHashMap<>();
        coalescedPropertyDrainRunnable = this::drainCoalescedPropertyEvents;
        cacheObserverState = new MpvCacheObserverState();
        propertyCache = new MpvPropertyCache();
        observedPropertyNames = new HashSet<>();
        MpvCacheTimePolicy.Decision initialCacheTimeDecision = MpvCacheTimePolicy.resolve(
                config.performanceOptionsPriority(),
                config.automaticCacheTime(),
                config.cache(),
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds(),
                config.rebufferMs(),
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.PathKind.UNKNOWN);
        cacheTimeState = new MpvCacheTimeState(
                initialCacheTimeDecision,
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds(),
                config.demuxerHysteresisSeconds());
        seekPositionState = new MpvSeekPositionState();
        mediaReplacementCoordinator = new MpvMediaReplacementCoordinator();
        surfaceTeardownPolicy = new MpvSurfaceTeardownPolicy();
        stateRefreshRunnable = this::refreshPlaybackState;
        mainThreadHeartbeatPending = new AtomicBoolean();
        mainThreadHeartbeatRunnable = () -> {
            mainThreadHeartbeatPending.set(false);
            mainThreadHeartbeatPostedAtMs = 0;
        };
        mainThreadWatchdogRunnable = this::runMainThreadWatchdog;
        endFileValidationRunnable = this::validateEarlyEndFile;
        loadStartRetryRunnable = this::retryLoadIfNotStarted;
        seekBufferingTimeoutRunnable = this::timeOutSeekBuffering;
        mediaReplacementStopTimeoutRunnable = this::resumeMediaReplacementAfterStopTimeout;
        trackRefreshRunnable = this::runScheduledTrackRefresh;
        chapterRefreshRunnable = this::runScheduledChapterRefresh;
        initialTrackSelectionGateTimeoutRunnable =
                () -> releaseInitialTrackSelectionGate("timeout");
        isoTrackMetadataReadyListener = this::onIsoTrackMetadataReady;
        hlsProxy = new MpvHlsProxy();
        autoCacheBaselineState = new MpvAutoCacheBaselineState();
        autoHlsBitrateState = new MpvAutoHlsBitrateState();
        recentLogs = new ArrayList<>();
        contentFds = new ArrayList<>();
        preloadCacheDir = new File(config.cacheDir(), "mpv-demuxer-cache");
        preloadCacheCapacityBytes = resolvePreloadCacheCapacity(preloadCacheDir);
        effectiveDemuxerMaxBytes = config.demuxerMaxBytes();
        File externalFiles = this.context.getExternalFilesDir(null);
        subtitleDiagnosticFile = externalFiles == null ? null : new File(externalFiles, "mpv-subtitle-debug.log");
        if (subtitleDiagnosticFile != null && subtitleDiagnosticFile.length() > 2 * 1024 * 1024) subtitleDiagnosticFile.delete();
        playbackParameters = PlaybackParameters.DEFAULT;
        currentTracks = Tracks.EMPTY;
        selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        currentChapters = List.of();
        videoSize = VideoSize.UNKNOWN;
        playbackState = Player.STATE_IDLE;
        initialSeekPositionMs = C.TIME_UNSET;
        loadStartPositionMs = C.TIME_UNSET;
        cachedDurationMs = C.TIME_UNSET;
        currentChapter = C.INDEX_UNSET;
        lastEndFileReason = MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN;
        textOffsetMs = 0;
        audioOffsetMs = 0;
        subtitleTextSize = 0f;
        subtitlePosition = 0f;
        playWhenReady = true;
        volume = 1f;
    }

    @Override
    protected State getState() {
        int state = playbackState;
        MediaItem currentItem = mediaItem;
        if (currentItem == null && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            Log.w(TAG, "Coerce empty playlist state=" + state + " loading=" + loading + " fileLoaded=" + fileLoaded + " playbackRestarted=" + playbackRestarted);
            state = Player.STATE_IDLE;
        }
        State.Builder builder = new State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(state)
                .setIsLoading(currentItem != null && loading && state != Player.STATE_IDLE && state != Player.STATE_ENDED)
                .setPlayerError(playerError)
                .setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(playbackParameters)
                .setTextOffsetMs(textOffsetMs)
                .setAudioOffsetMs(audioOffsetMs)
                .setVideoSize(videoSize)
                .setVolume(volume)
                .setCurrentMediaEditions(currentChapters)
                .setPlaylist(currentItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData(currentItem)))
                .setCurrentMediaItemIndex(currentItem == null ? C.INDEX_UNSET : 0);
        if (currentItem != null) {
            long duration = cachedDurationMs > 0 ? cachedDurationMs : C.TIME_UNSET;
            long position = Math.max(0, cachedPositionMs);
            PositionSupplier positionSupplier = isPlayingInternal()
                    ? PositionSupplier.getExtrapolating(position, playbackParameters.speed)
                    : PositionSupplier.getConstant(position);
            builder.setContentPositionMs(positionSupplier);
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(bufferedPositionMs(position, duration)));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, bufferedPositionMs(position, duration) - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData(MediaItem item) {
        long duration = cachedDurationMs > 0 ? cachedDurationMs : C.TIME_UNSET;
        return new MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(duration > 0)
                .setIsDynamic(duration == C.TIME_UNSET)
                .setTracks(currentTracks)
                .build();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        restorePreloadCacheOverlay();
        clearCoalescedPropertyEvents();
        boolean reusingContext = canReuseContextForMediaReplacement();
        boolean hadActiveMedia = mediaItem != null && (fileLoaded || loadStarted || playbackState != Player.STATE_IDLE);
        cancelScheduledTrackRefresh();
        cancelScheduledChapterRefresh();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        initialSeekPositionMs = mediaItem != null && startPositionMs > 0
                ? startPositionMs : C.TIME_UNSET;
        loadStartPositionMs = C.TIME_UNSET;
        seekPositionState.clear();
        endSeekBuffering("set-media-items");
        cachedPositionMs = Math.max(0, startPositionMs == C.TIME_UNSET ? 0 : startPositionMs);
        cachedDurationMs = C.TIME_UNSET;
        resetVideoMetadataCache();
        resetCacheState();
        propertyCache.clear();
        currentTracks = Tracks.EMPTY;
        selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        pendingOsdLoadGeneration = C.INDEX_UNSET;
        osdSurfaceUsedForCurrentMedia = initialOsdSurfaceRequested;
        setOsdSurfaceRequested(initialOsdSurfaceRequested);
        mainHandler.removeCallbacks(initialTrackSelectionGateTimeoutRunnable);
        initialTrackSelectionGateActive = mediaItem != null
                && initialTrackSelectionGateRequested;
        currentChapters = List.of();
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
        fileLoaded = false;
        fileLoadedAtElapsedRealtimeMs = 0;
        playbackRestarted = false;
        loadStarted = false;
        loadStartRetryCount = 0;
        eofReached = false;
        idleActive = false;
        preferAacApplied = false;
        directAudioApplied = false;
        audioTrackManuallySelected = false;
        currentPlayableUri = null;
        closeIsoSession();
        currentLikelyHls = false;
        currentLikelyDash = false;
        currentChapter = C.INDEX_UNSET;
        resetFailureSignals();
        recentLogs.clear();
        playerError = null;
        mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
        if (mediaItem == null) {
            mediaReplacementCoordinator.reset();
        } else {
            if (initialized && !reusingContext) releaseNativeContext("new media");
            long generation = mediaReplacementCoordinator.begin(reusingContext, hadActiveMedia, stopping);
            if (reusingContext) {
                SpiderDebug.log("mpv", "context reused reason=new-media generation=%d stopPending=%s active=%s player=%s", generation, stopping, hadActiveMedia, identity(this));
            }
        }
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        closeContentFds();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        if (mediaItem == null && !mediaItems.isEmpty()) mediaItem = mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (mediaItems.isEmpty()) {
            stopInternal(true);
            stopMainThreadWatchdog();
            mediaItem = null;
            invalidateState();
        } else {
            mediaItem = mediaItems.get(0);
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        stopInternal(true);
        stopMainThreadWatchdog();
        mediaItem = null;
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        long generation = mediaReplacementCoordinator.generation();
        boolean reusingContext = canReuseContextForMediaReplacement();
        if (mediaReplacementCoordinator.deferPrepare(reusingContext, stopping)) {
            SpiderDebug.log("mpv", "media replace deferred generation=%d reason=stop-pending player=%s", generation, identity(this));
            mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
            mainHandler.postDelayed(mediaReplacementStopTimeoutRunnable, MEDIA_REPLACEMENT_STOP_TIMEOUT_MS);
        } else {
            openCurrent(generation);
        }
        return Futures.immediateVoidFuture();
    }

    private boolean canReuseContextForMediaReplacement() {
        return initialized && !released && nativeContextOwner == this && "mediacodec_embed".equals(config.vo());
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        hlsProxy.setPlaybackPaused(!playWhenReady);
        if (initialized && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            safeSetPropertyBoolean("pause", shouldPauseNativePlayback());
        }
        updatePreloadCacheOverlay();
        if (!playWhenReady) requestHlsPreload(cachedPositionMs);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        stopInternal(true);
        stopMainThreadWatchdog();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        prepareTerminalRelease();
        released = true;
        startMainThreadWatchdog();
        videoSizeProbeListener = null;
        cancelScheduledTrackRefresh();
        cancelScheduledChapterRefresh();
        mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
        mediaReplacementCoordinator.reset();
        try {
            stopInternal(false);
            hlsProxy.release();
            clearVideoOutput();
            mainHandler.removeCallbacks(stateRefreshRunnable);
            mainHandler.removeCallbacks(endFileValidationRunnable);
            mainHandler.removeCallbacks(seekBufferingTimeoutRunnable);
            releaseNativeContext("release");
        } finally {
            stopMainThreadWatchdog();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE;
        if (initialized) safeSetPropertyString("loop-file", repeatOne ? "inf" : "no");
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (positionMs == C.TIME_UNSET) positionMs = 0;
        cachedPositionMs = Math.max(0, positionMs);
        resetCacheTimelineForSeek(cachedPositionMs);
        if (!fileLoaded) initialSeekPositionMs = cachedPositionMs;
        if (initialized && playbackState != Player.STATE_IDLE) {
            long nowMs = SystemClock.elapsedRealtime();
            if (fileLoaded) {
                initialSeekPositionMs = C.TIME_UNSET;
                seekPositionState.begin(cachedPositionMs, nowMs);
                cacheObserverState.onPlaybackDiscontinuity(nowMs);
            }
            if (currentLikelyHls && playbackRestarted) {
                hlsProxy.cancelAutomaticPreloadForDiscontinuity();
            }
            seekMpv(cachedPositionMs);
            if (currentLikelyHls && playbackRestarted) requestHlsPreload(cachedPositionMs);
            int nextState = MpvPlaybackState.resolveAfterSeekRequest(playbackState, fileLoaded, stopping);
            // Re-arm for a seek that lands inside an open seek window too, so scrubbing
            // does not keep running against the first seek's deadline. A BUFFERING that
            // came from a stall is deliberately not adopted: its rebuffer is already
            // counted, and arming the timeout there could publish READY over a session
            // that really is stuck.
            if (nextState == Player.STATE_BUFFERING
                    && (playbackState != Player.STATE_BUFFERING || seekBufferingActive)) {
                beginSeekBuffering("request");
            }
            playbackState = nextState;
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        if (initialized) safeSetPropertyDouble("speed", playbackParameters.speed);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume, int volumeOperationType) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (initialized) safeSetPropertyDouble("volume", this.volume * 100.0);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetTextOffsetMs(long offsetMs) {
        textOffsetMs = offsetMs;
        applyTextOffset();
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetAudioOffsetMs(long offsetMs) {
        audioOffsetMs = offsetMs;
        applyAudioOffset();
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    public void setSubtitleStyle(float textSize, float position) {
        subtitleTextSize = textSize;
        subtitlePosition = position;
        applySubtitleStyle();
        invalidateState();
    }

    public void setVideoAspect(float aspectRatio, boolean stretch) {
        float normalized = Float.isFinite(aspectRatio) && aspectRatio > 0f ? aspectRatio : 0f;
        if (Float.compare(videoAspectRatio, normalized) == 0 && stretchVideo == stretch) return;
        videoAspectRatio = normalized;
        stretchVideo = stretch;
        applyVideoAspect();
    }

    public void prepareTerminalRelease() {
        if (surfaceTeardownPolicy.requestTerminalRelease()) {
            SpiderDebug.log("mpv", "terminal Surface release requested player=%s", identity(this));
        }
    }

    public String getAudioSpdifCodecs() {
        return config.audioSpdif();
    }

    public void setPlaybackTraceId(String playbackTraceId) {
        this.playbackTraceId = PlaybackTrace.normalize(playbackTraceId);
    }

    public String getPlaybackTraceId() {
        return playbackTraceId;
    }

    public PlaybackRoute.Resolution getPlaybackRouteResolution() {
        return PlaybackRoute.resolve(currentPlayableUri);
    }

    @Nullable
    public PlaybackResourceClassifier.Classification getResourceClassification() {
        PlaybackResourceClassifier.Classification current = resourceClassification;
        PlaybackResourceClassifier.Classification proxy = hlsProxy.resourceClassification();
        if (proxy != null) current = PlaybackResourceClassifier.merge(current, proxy);
        if (current == null) return null;
        try {
            return PlaybackResourceClassifier.observePlayer(current, isCurrentMediaItemLive(), getDuration());
        } catch (Throwable ignored) {
            return current;
        }
    }

    public void setLutShader(@Nullable MpvLutShader shader) {
        lutShader = shader;
        applyShaderPipeline(false);
    }

    public void setLutPreviewProgress(float progress) {
        if (!initialized || lutShader == null || !lutShader.isPreview()) return;
        float value = Math.max(0f, Math.min(1f, progress));
        safeCommand(new String[]{
                "change-list", "glsl-shader-opts", "append",
                lutShader.getPreviewOptionKey() + "=" + String.format(Locale.US, "%.5f", value)});
    }

    public AutoCacheBaselineResult applyAutoCacheBaseline(long forwardBytes, long backBytes) {
        if (!autoCacheBaselineState.stage(
                config.performanceOptionsPriority(), forwardBytes, backBytes)) {
            return AutoCacheBaselineResult.REJECTED;
        }
        if (!initialized) {
            PlaybackTrace.log("mpv-auto", playbackTraceId,
                    "action=initial-cache result=staged forwardBytes=%d backBytes=%d",
                    forwardBytes, backBytes);
            return AutoCacheBaselineResult.STAGED;
        }
        boolean applied = applyAutoCacheBaselineToNative();
        PlaybackTrace.log("mpv-auto", playbackTraceId,
                "action=initial-cache result=%s forwardBytes=%d backBytes=%d",
                applied ? "applied" : "failed", forwardBytes, backBytes);
        return applied ? AutoCacheBaselineResult.APPLIED : AutoCacheBaselineResult.FAILED;
    }

    public void clearAutoCacheBaseline() {
        autoCacheBaselineState.clear();
    }

    public boolean updateAutomaticPreloadControl(
            boolean automatic,
            boolean resourceAllowed,
            boolean trafficAllowed) {
        return hlsProxy.updateAutomaticPreloadControl(
                automatic, resourceAllowed, trafficAllowed);
    }

    public void requestAutomaticHlsPreload(long positionMs) {
        hlsProxy.requestAutomaticPreload(
                positionMs,
                selectedHlsBitsPerSecond());
    }

    private void requestHlsPreload(long positionMs) {
        if (!currentLikelyHls) return;
        if (playWhenReady) {
            hlsProxy.preloadAround(positionMs);
        } else {
            hlsProxy.preloadWhilePaused(positionMs, selectedHlsBitsPerSecond());
        }
    }

    private long selectedHlsBitsPerSecond() {
        MpvHlsProxy.HlsVariant selected = MpvHlsProxy.resolveSelectedVariant(
                hlsProxy.variantSnapshot().variants(), cachedSelectedHlsBitrate);
        return selected == null ? 0 : selected.selectionBitsPerSecond();
    }

    public AutoHlsBitrateResult applyAutoHlsBitrate(String option) {
        if (!autoHlsBitrateState.stage(
                config.performanceOptionsPriority(),
                config.automaticHlsVariant(),
                option)) {
            return AutoHlsBitrateResult.REJECTED;
        }
        if (!initialized) {
            PlaybackTrace.log("mpv-hls-variant", playbackTraceId,
                    "action=apply-option result=staged target=%s",
                    autoHlsBitrateState.snapshot().stagedOption());
            return AutoHlsBitrateResult.STAGED;
        }
        boolean applied = applyAutoHlsBitrateToNative();
        PlaybackTrace.log("mpv-hls-variant", playbackTraceId,
                "action=apply-option result=%s target=%s",
                applied ? "applied" : "failed",
                autoHlsBitrateState.snapshot().stagedOption());
        return applied ? AutoHlsBitrateResult.APPLIED
                : AutoHlsBitrateResult.FAILED;
    }

    public void clearAutoHlsBitrate() {
        autoHlsBitrateState.clear();
    }

    /** Cached track/proxy HLS state; this method never performs a native query. */
    public AutoHlsRuntimeSnapshot getAutoHlsRuntimeSnapshot() {
        MpvHlsProxy.HlsVariantSnapshot proxy = hlsProxy.variantSnapshot();
        List<HlsVariant> variants = new ArrayList<>(proxy.variants().size());
        for (MpvHlsProxy.HlsVariant variant : proxy.variants()) {
            variants.add(toPublicVariant(variant));
        }
        MpvHlsProxy.HlsVariant selected = MpvHlsProxy.resolveSelectedVariant(
                proxy.variants(), cachedSelectedHlsBitrate);
        MpvAutoHlsBitrateState.Snapshot option = autoHlsBitrateState.snapshot();
        return new AutoHlsRuntimeSnapshot(
                currentLikelyHls,
                variants,
                selected == null ? null : toPublicVariant(selected),
                saturatingMultiply(cachedCacheSpeedBytesPerSecond, 8L),
                isFreshCacheSpeedSample(SystemClock.elapsedRealtime()),
                cachedCacheUnderrun,
                cachedCacheUnderrunCount,
                option.stagedOption(),
                option.acceptedOption(),
                option.observedBitsPerSecond(),
                option.observedCount());
    }

    /** Proxy-only upstream and disk facts; this never trusts MPV loopback speed. */
    public AutoHlsPreloadRuntimeSnapshot getAutoHlsPreloadRuntimeSnapshot() {
        MpvHlsProxy.PreloadRuntimeSnapshot proxy =
                hlsProxy.preloadRuntimeSnapshot(SystemClock.elapsedRealtime());
        return new AutoHlsPreloadRuntimeSnapshot(
                proxy.preloadConfigured(),
                proxy.vod(),
                proxy.upstreamBitsPerSecond(),
                proxy.throughputKnown(),
                proxy.throughputFresh(),
                proxy.throughputSampleAtElapsedMs(),
                proxy.throughputAgeMs(),
                proxy.acceptedThroughputSamples(),
                proxy.rejectedThroughputSamples(),
                proxy.lastThroughputRejectReason(),
                proxy.foregroundRequests(),
                proxy.cacheEnabled(),
                proxy.cacheStorageKnown(),
                proxy.cacheBudgetAvailable(),
                proxy.cacheCircuitOpen(),
                proxy.cachePhysicalBytes(),
                proxy.cacheReservedBytes(),
                proxy.cacheNewWriteBudgetBytes(),
                proxy.cacheEffectiveCapacityBytes(),
                proxy.preloadTasks());
    }

    public enum AutoCacheBaselineResult {
        REJECTED("rejected", false, false),
        STAGED("staged", true, true),
        APPLIED("applied", true, false),
        FAILED("failed", false, false);

        private final String label;
        private final boolean accepted;
        private final boolean staged;

        AutoCacheBaselineResult(String label, boolean accepted, boolean staged) {
            this.label = label;
            this.accepted = accepted;
            this.staged = staged;
        }

        public String label() {
            return label;
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean staged() {
            return staged;
        }
    }

    public enum AutoHlsBitrateResult {
        REJECTED("rejected", false, false),
        STAGED("staged", true, true),
        APPLIED("applied", true, false),
        FAILED("failed", false, false);

        private final String label;
        private final boolean accepted;
        private final boolean staged;

        AutoHlsBitrateResult(String label, boolean accepted, boolean staged) {
            this.label = label;
            this.accepted = accepted;
            this.staged = staged;
        }

        public String label() {
            return label;
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean staged() {
            return staged;
        }
    }

    public record HlsVariant(
            long bandwidthBitsPerSecond,
            long averageBandwidthBitsPerSecond,
            int width,
            int height) {

        public HlsVariant {
            bandwidthBitsPerSecond = Math.max(0, bandwidthBitsPerSecond);
            averageBandwidthBitsPerSecond = Math.max(0,
                    averageBandwidthBitsPerSecond);
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public long selectionBitsPerSecond() {
            return bandwidthBitsPerSecond > 0
                    ? bandwidthBitsPerSecond : averageBandwidthBitsPerSecond;
        }
    }

    public record AutoHlsRuntimeSnapshot(
            boolean likelyHls,
            List<HlsVariant> variants,
            @Nullable HlsVariant selectedVariant,
            long rawInputBitsPerSecond,
            boolean rawInputRateUsable,
            boolean underrun,
            long underrunCount,
            String stagedOption,
            String acceptedOption,
            long observedBitsPerSecond,
            int observedCount) {

        public AutoHlsRuntimeSnapshot {
            variants = variants == null ? List.of() : List.copyOf(variants);
            rawInputBitsPerSecond = Math.max(0, rawInputBitsPerSecond);
            underrunCount = Math.max(0, underrunCount);
            stagedOption = stagedOption == null ? "" : stagedOption;
            acceptedOption = acceptedOption == null ? "" : acceptedOption;
            observedCount = Math.max(0, observedCount);
        }
    }

    public record AutoHlsPreloadRuntimeSnapshot(
            boolean preloadConfigured,
            boolean vod,
            long upstreamBitsPerSecond,
            boolean throughputKnown,
            boolean throughputFresh,
            long throughputSampleAtElapsedMs,
            long throughputAgeMs,
            int acceptedThroughputSamples,
            int rejectedThroughputSamples,
            String lastThroughputRejectReason,
            int foregroundRequests,
            boolean cacheEnabled,
            boolean cacheStorageKnown,
            boolean cacheBudgetAvailable,
            boolean cacheCircuitOpen,
            long cachePhysicalBytes,
            long cacheReservedBytes,
            long cacheNewWriteBudgetBytes,
            long cacheEffectiveCapacityBytes,
            int preloadTasks) {

        public AutoHlsPreloadRuntimeSnapshot {
            upstreamBitsPerSecond = Math.max(0, upstreamBitsPerSecond);
            acceptedThroughputSamples = Math.max(0, acceptedThroughputSamples);
            rejectedThroughputSamples = Math.max(0, rejectedThroughputSamples);
            lastThroughputRejectReason = lastThroughputRejectReason == null
                    ? "none" : lastThroughputRejectReason;
            foregroundRequests = Math.max(0, foregroundRequests);
            cachePhysicalBytes = Math.max(0, cachePhysicalBytes);
            cacheReservedBytes = Math.max(0, cacheReservedBytes);
            cacheNewWriteBudgetBytes = Math.max(0, cacheNewWriteBudgetBytes);
            cacheEffectiveCapacityBytes = Math.max(0, cacheEffectiveCapacityBytes);
            preloadTasks = Math.max(0, preloadTasks);
        }
    }

    /** Observer-only cache snapshot for UI diagnostics and periodic telemetry. */
    public PlayerCacheState getCacheState() {
        return buildCacheState();
    }

    /** Observer-only cache snapshot for periodic telemetry; never queries native properties. */
    public PlayerCacheState getCachedCacheState() {
        return buildCacheState();
    }

    private PlayerCacheState buildCacheState() {
        MpvCacheTimeState.Snapshot cacheTime = cacheTimeState.snapshot();
        return new PlayerCacheState(
                cacheObserverState.hasObservedValues(),
                config.cache(),
                cachedCacheIdle,
                cachedCacheUnderrun,
                cachedCacheBof,
                cachedCacheEof,
                cachedCacheBufferingState,
                cachedCacheDurationMs,
                cachedCacheEndMs,
                cachedCacheReaderPositionMs,
                cachedCacheForwardBytes,
                cachedCacheTotalBytes,
                cachedCacheFileBytes,
                cachedCacheSpeedBytesPerSecond,
                effectiveDemuxerMaxBytes,
                config.demuxerMaxBackBytes(),
                cacheTime.master().label(),
                cacheTime.reason().label(),
                cacheTime.cacheSeconds(),
                cacheTime.readaheadSeconds(),
                cacheTime.hysteresisSeconds(),
                cacheTime.observedOptions());
    }

    public String getRenderDiagnostics() {
        refreshRenderState(MpvDiagnosticsPolicy.Request.PANEL);
        String requested = isConfiguredVulkan() ? "vulkan" : "opengl";
        String currentVo = cachedCurrentVo;
        String currentGpuContext = cachedCurrentGpuContext;
        String gpuApi = cachedGpuApi;
        boolean runtimeReported = !TextUtils.isEmpty(currentVo)
                || !TextUtils.isEmpty(currentGpuContext)
                || !TextUtils.isEmpty(gpuApi);
        String actual = runtimeReported
                ? (isRuntimeVulkan(currentVo, currentGpuContext, gpuApi) ? "vulkan" : "opengl")
                : "等待上报";
        return "请求 " + requested
                + " / 实际 " + actual
                + " / vo " + emptyDash(currentVo)
                + " / context " + emptyDash(currentGpuContext)
                + " / api " + emptyDash(gpuApi);
    }

    public String getRuntimeDiagnostics() {
        refreshRuntimeDiagnostics(MpvDiagnosticsPolicy.Request.PANEL);
        String hwdec = TextUtils.isEmpty(cachedHwdecCurrent) ? "等待上报" : cachedHwdecCurrent;
        String ao = TextUtils.isEmpty(cachedCurrentAo) ? "等待上报" : cachedCurrentAo;
        String audioDevice = cachedAudioDevice;
        return joinParts(
                "hwdec " + hwdec,
                "ao " + ao,
                TextUtils.isEmpty(audioDevice) ? "" : "device " + shortText(audioDevice, 32),
                formatAvSync(),
                formatDisplayFps(),
                formatDisplaySync(),
                formatShader());
    }

    /** Whole-device GPU sampling is owned by the panel's background resource monitor. */
    public String getGpuLoadDiagnostics() {
        return "";
    }

    public void setGpuLoadDiagnosticsEnabled(boolean enabled) {
    }

    /** Cached values from mpv runtime property observers; never falls back to requested config. */
    public String getObservedHwdecCurrent() {
        return !observedHwdecCurrent || cachedHwdecCurrent == null ? "" : cachedHwdecCurrent;
    }

    public boolean hasObservedHwdecCurrent() {
        return observedHwdecCurrent;
    }

    /** Cached values from mpv runtime property observers; never falls back to requested config. */
    public String getObservedCurrentVideoOutput() {
        return !observedCurrentVo || cachedCurrentVo == null ? "" : cachedCurrentVo;
    }

    public boolean hasObservedCurrentVideoOutput() {
        return observedCurrentVo;
    }

    public long getDroppedFrames() {
        return Math.max(0, cachedDecoderDroppedFrames) + Math.max(0, cachedOutputDroppedFrames);
    }

    public boolean hasObservedDroppedFrames() {
        return observedDroppedFrames;
    }

    public long getObservedDroppedFrames() {
        return Math.max(0, cachedDecoderDroppedFrames) + Math.max(0, cachedOutputDroppedFrames);
    }

    /**
     * True while the BUFFERING currently published exists because of a seek.
     *
     * <p>Rebuffer statistics drive the network guard and the HLS variant policy, and a seek
     * is a user action rather than evidence that the source cannot keep up. Callers use this
     * to keep the seek window out of those counters.
     */
    public boolean isSeekBuffering() {
        return seekBufferingActive;
    }

    /** Cached observer values only; this method never queries MPV synchronously. */
    public FrameTimingSnapshot getFrameTimingSnapshot() {
        double displayFps = cachedEstimatedDisplayFps > 0
                ? cachedEstimatedDisplayFps : cachedDisplayFps;
        return new FrameTimingSnapshot(
                Math.max(0, cachedDecoderDroppedFrames),
                Math.max(0, cachedOutputDroppedFrames),
                Math.max(0, cachedMistimedFrames),
                Math.max(0, cachedDelayedFrames),
                cachedAvSyncSeconds,
                getObservedContentFrameRate(),
                displayFps > 0 && Double.isFinite(displayFps) ? displayFps : 0,
                observedDroppedFrames);
    }

    public record FrameTimingSnapshot(
            long decoderDroppedFrames,
            long outputDroppedFrames,
            long mistimedFrames,
            long delayedFrames,
            double avSyncSeconds,
            float contentFrameRate,
            double displayFrameRate,
            boolean observed) {
    }

    public float getObservedContentFrameRate() {
        return cachedContentFrameRate > 0 && Float.isFinite(cachedContentFrameRate) ? cachedContentFrameRate : 0f;
    }

    public float getObservedDisplayFrameRate() {
        double value = cachedEstimatedDisplayFps > 0 ? cachedEstimatedDisplayFps : cachedDisplayFps;
        return value > 0 && Double.isFinite(value) && value <= Float.MAX_VALUE ? (float) value : 0f;
    }

    @Override
    protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
        this.videoOutput = videoOutput;
        setVideoOutput(videoOutput);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
        if (videoOutput == null || videoOutput == this.videoOutput) {
            this.videoOutput = null;
            clearVideoOutput();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    public void eventProperty(String property) {
        dispatchProperty(property, null);
    }

    @Override
    public void eventProperty(String property, long value) {
        dispatchProperty(property, value);
    }

    @Override
    public void eventProperty(String property, boolean value) {
        dispatchProperty(property, value);
    }

    @Override
    public void eventProperty(String property, String value) {
        dispatchProperty(property, value);
    }

    @Override
    public void eventProperty(String property, double value) {
        dispatchProperty(property, value);
    }

    @Override
    public void event(int eventId) {
        postToMain(() -> handleEvent(eventId));
    }

    @Override
    public void eventCommandReply(long requestId, int error) {
        if (requestId == pendingOsdSurfaceRequestId) {
            postToMain(() -> handleOsdSurfaceReply(requestId, error));
            return;
        }
        if (error >= MPVLib.MpvError.MPV_ERROR_SUCCESS) return;
        postToMain(() -> {
            String message = "asynchronous command failed request=" + requestId + " error=" + error;
            Log.e(TAG, message);
            rememberLog(message);
            markFailureSignal(message);
        });
    }

    @Override
    public void endFile(int reason, int error, String errorText) {
        postToMain(() -> handleEndFile(reason, error, errorText));
    }

    @Override
    public void logMessage(String prefix, int level, String text) {
        postToMain(() -> {
            if (released) return;
            String line = MpvDiagnosticsPolicy.redactSensitive(prefix + ": " + text);
            rememberLog(line);
            markFailureSignal(line);
            String lower = line.toLowerCase(Locale.US);
            if (shouldDebugLogMpvLine(line)) PlaybackTrace.log("mpv", playbackTraceId, "%s", line);
        });
    }

    private void openCurrent(long generation) {
        if (!mediaReplacementCoordinator.isCurrent(generation) || mediaItem == null || mediaItem.localConfiguration == null) return;
        startMainThreadWatchdog();
        try {
            ensureInitialized();
            if (!mediaReplacementCoordinator.isCurrent(generation)) return;
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            fileLoaded = false;
            fileLoadedAtElapsedRealtimeMs = 0;
            loadStarted = false;
            playbackRestarted = false;
            loadStartPositionMs = C.TIME_UNSET;
            loadStartRetryCount = 0;
            videoReconfigCount = 0;
            lastVideoSizeCandidateLog = null;
            eofReached = false;
            idleActive = false;
            cachedDurationMs = C.TIME_UNSET;
            cachedCacheDurationMs = 0;
            resetRuntimeDiagnostics();
            resetFailureSignals();
            recentLogs.clear();
            mainHandler.removeCallbacks(endFileValidationRunnable);
            closeContentFds();
            if (hasDrmConfiguration(mediaItem)) {
                fail(mpvError(ERROR_DRM_UNSUPPORTED, "MediaItem DRM configuration is not supported by libmpv"), PlaybackException.ERROR_CODE_DRM_UNSPECIFIED);
                return;
            }
            Map<String, String> headers = applyMediaOptions(mediaItem);
            bindVideoOutput();
            safeSetPropertyBoolean("pause", shouldPauseNativePlayback());
            safeSetPropertyString("loop-file", repeatOne ? "inf" : "no");
            safeSetPropertyDouble("speed", playbackParameters.speed);
            safeSetPropertyDouble("volume", volume * 100.0);
            applyTextOffset();
            applyAudioOffset();
            applySubtitleStyle();
            applyVideoAspect();
            currentPlayableUri = playableUri(mediaItem);
            logSourceDiagnostics(mediaItem, currentPlayableUri, headers);
            boolean declaredIso = isLikelyIso(mediaItem, currentPlayableUri);
            if (!declaredIso && shouldProbeOpaqueIso(mediaItem, currentPlayableUri)) {
                String probingUri = currentPlayableUri;
                IsoSessionManager.probeAndCreateAsync(probingUri, headers, isoUri -> mainHandler.post(() -> {
                    if (released || stopping || !mediaReplacementCoordinator.isCurrent(generation) || !TextUtils.equals(currentPlayableUri, probingUri)) {
                        IsoSessionManager.closeUri(isoUri);
                        return;
                    }
                    currentIsoUri = isoUri;
                    attachIsoTrackMetadataListener();
                    if (currentIsoUri != null) currentPlayableUri = currentIsoUri;
                    continueOpenCurrent(headers, generation);
                }));
                return;
            }
            if (declaredIso) {
                currentIsoUri = IsoSessionManager.create(currentPlayableUri, headers);
                attachIsoTrackMetadataListener();
            }
            if (currentIsoUri != null) currentPlayableUri = currentIsoUri;
            continueOpenCurrent(headers, generation);
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void continueOpenCurrent(Map<String, String> headers, long generation) {
        if (!mediaReplacementCoordinator.isCurrent(generation)) return;
        try {
            String sourceMime = mediaItem == null || mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.mimeType;
            resourceClassification = PlaybackResourceClassifier.classifyRequest(currentPlayableUri, sourceMime, sourceMime);
            if (currentIsoUri != null) {
                currentLikelyHls = false;
                currentLikelyDash = false;
                hlsProxy.clear();
                Log.i(TAG, "load remote optical-disc ISO session");
            } else {
                currentLikelyHls = isLikelyHls(mediaItem, currentPlayableUri);
                currentLikelyDash = isLikelyDash(mediaItem, currentPlayableUri);
            }
            applyPreloadDiskCacheMode();
            applyCacheTimePolicy();
            if (currentIsoUri == null && shouldProxyHls(currentPlayableUri, currentLikelyHls)) {
                String originalUri = currentPlayableUri;
                currentPlayableUri = hlsProxy.proxy(
                        originalUri, headers,
                        PlaybackDiskBufferStore.mediaKey(mediaItem));
                if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "hls proxy enabled original=%s proxy=%s", MpvDiagnosticsPolicy.sourceSummary(originalUri), MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri));
            } else {
                hlsProxy.clear();
            }
            MpvNetworkRecoveryPolicy.Decision recovery = MpvNetworkRecoveryPolicy.resolve(currentPlayableUri);
            PlaybackTrace.log("mpv", playbackTraceId, "network recovery route=%s routeOwner=%s evidence=%s confidence=%s observedLeg=%s upstreamVisibility=%s controlScope=%s recoveryBoundary=%s policyKnown=%s nativeRemote=%s appOverlay=%s", recovery.route(), recovery.routeOwner(), recovery.routeEvidence(), recovery.routeConfidence(), recovery.observedLeg(), recovery.upstreamVisibility(), recovery.controlScope(), recovery.recoveryBoundary(), recovery.upstreamRecoveryPolicyKnown(), recovery.nativeRemoteRecovery(), recovery.appReconnectOverlay());
            if (!mediaReplacementCoordinator.isCurrent(generation)) return;
            applyShaderPipeline(true);
            Log.d(TAG, "load scheme=" + safeScheme(currentPlayableUri) + " urlLen=" + (currentPlayableUri == null ? 0 : currentPlayableUri.length()) + " hls=" + currentLikelyHls + " dash=" + currentLikelyDash);
            PlaybackTrace.log("mpv", playbackTraceId, "load scheme=%s urlLen=%d hls=%s dash=%s surface=%s attached=%s hwdec=%s vo=%s gpuContext=%s gpuApi=%s", safeScheme(currentPlayableUri), currentPlayableUri == null ? 0 : currentPlayableUri.length(), currentLikelyHls, currentLikelyDash, surface != null && surface.isValid(), surfaceAttached, config.hwdec(), config.vo(), config.gpuContext(), config.gpuApi());
            if (deferLoadUntilOsdSurfaceReady(generation)) return;
            startPreparedMedia(generation);
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private boolean deferLoadUntilOsdSurfaceReady(long generation) {
        if (!requiresOsdSurface() || !(videoOutput instanceof SurfaceView)) return false;
        boolean settled = pendingOsdSurfaceRequestId == 0
                && osdSurfaceAttached == osdSurfaceRequested;
        if (settled) return false;
        pendingOsdLoadGeneration = generation;
        SpiderDebug.log("mpv", "initial OSD gate waiting before load generation=%d requested=%s attached=%s pending=%d valid=%s",
                generation, osdSurfaceRequested, osdSurfaceAttached,
                pendingOsdSurfaceRequestId,
                osdSurface != null && osdSurface.isValid());
        return true;
    }

    private void resumePendingOsdLoad() {
        long generation = pendingOsdLoadGeneration;
        if (generation == C.INDEX_UNSET || pendingOsdSurfaceRequestId != 0
                || osdSurfaceAttached != osdSurfaceRequested) return;
        pendingOsdLoadGeneration = C.INDEX_UNSET;
        SpiderDebug.log("mpv", "initial OSD gate ready before load generation=%d", generation);
        startPreparedMedia(generation);
    }

    private void startPreparedMedia(long generation) {
        if (!mediaReplacementCoordinator.isCurrent(generation) || loadStarted) return;
        try {
            safeSetPropertyBoolean("pause", true);
            if (!TextUtils.isEmpty(initialSubtitleTrackId)) {
                safeSetPropertyString("sid", initialSubtitleTrackId);
                PlaybackTrace.log("mpv", playbackTraceId,
                        "initial subtitle preselect sid=%s phase=before-load",
                        initialSubtitleTrackId);
            }
            loadCurrentUri();
            if (initialTrackSelectionGateActive) {
                mainHandler.removeCallbacks(initialTrackSelectionGateTimeoutRunnable);
                mainHandler.postDelayed(initialTrackSelectionGateTimeoutRunnable,
                        INITIAL_TRACK_SELECTION_LOAD_TIMEOUT_MS);
                PlaybackTrace.log("mpv", playbackTraceId,
                        "initial track gate waiting phase=load timeoutMs=%d",
                        INITIAL_TRACK_SELECTION_LOAD_TIMEOUT_MS);
            }
            scheduleLoadStartRetry();
            invalidateState();
            startStateRefresh();
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void ensureInitialized() throws IOException {
        synchronized (NATIVE_CONTEXT_LOCK) {
            if (initialized && nativeContextOwner == this) return;
            if (nativeContextOwner != null && nativeContextOwner != this) {
                MpvPlayer previousOwner = nativeContextOwner;
                SpiderDebug.log("mpv", "native context takeover old=%s new=%s", identity(previousOwner), identity(this));
                previousOwner.released = true;
                previousOwner.releaseNativeContextLocked("takeover");
                previousOwner.stopMainThreadWatchdog();
            }
            if (!MPVLib.ensureLoaded(context)) {
                Throwable e = MPVLib.getLoadError();
                if (e instanceof IOException io) throw io;
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IOException(e == null ? "MPV native libraries are unavailable" : e.getMessage(), e);
            }
            copySupportAssets();
            // Claim ownership only once the native context exists. Assigning before the
            // attempt leaves a failed create owning the process-wide slot: this method's
            // takeover branch above has already released the previous owner, so nothing
            // else would ever reset it, and every later playback would take the takeover
            // path against an instance whose initialized flag is still false.
            if (!mpvTryCreate(context)) {
                nativeContextOwner = null;
                throw new IOException("MPV native context creation is already in progress");
            }
            nativeContextOwner = this;
            applyPreInitOptions();
            mpvInit();
            initialized = true;
            MPVLib.addObserver(this);
            MPVLib.addLogObserver(this);
            applyPostInitOptions();
            applyVideoAspect();
            applyShaderPipeline(true);
            observeProperties();
        }
    }

    private void applyPreInitOptions() {
        MpvCacheTimePolicy.Decision initialCacheTimeDecision = MpvCacheTimePolicy.resolve(
                config.performanceOptionsPriority(),
                config.automaticCacheTime(),
                config.cache(),
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds(),
                config.rebufferMs(),
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.PathKind.UNKNOWN);
        cacheTimeState.reset(
                initialCacheTimeDecision,
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds(),
                config.demuxerHysteresisSeconds());
        setOption("config", "yes");
        setOption("config-dir", config.configDir().getAbsolutePath());
        setOption("gpu-shader-cache-dir", config.cacheDir().getAbsolutePath());
        setOption("icc-cache-dir", config.cacheDir().getAbsolutePath());
        setOption("vo", config.vo());
        setOption("gpu-context", config.gpuContext());
        if (!TextUtils.isEmpty(config.gpuApi())) setOption("gpu-api", config.gpuApi());
        if (config.openglEs()) setOption("opengl-es", "yes");
        setOption("hwdec", config.hwdec());
        setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
        setOption("ao", config.ao());
        if (!TextUtils.isEmpty(config.audioSpdif())) setOption("audio-spdif", config.audioSpdif());
        setOption("audio-set-media-role", "yes");
        setOption("tls-verify", config.tlsVerify() ? "yes" : "no");
        if (config.caFile().isFile()) setOption("tls-ca-file", config.caFile().getAbsolutePath());
        setOption("input-default-bindings", "yes");
        setOption("cache", config.cache() ? "yes" : "no");
        setOption("cache-on-disk", "no");
        if (preloadCacheCapacityBytes > 0) {
            setOption("demuxer-cache-dir", preloadCacheDir.getAbsolutePath());
        }
        setOption("cache-secs", String.valueOf(config.cacheSeconds()));
        setOption("cache-pause", MpvStartupBufferPolicy.CACHE_PAUSE);
        // Baseline for fast startup. mpv.conf can still replace it during init
        // when the user explicitly selects config priority.
        setOption("cache-pause-initial", MpvStartupBufferPolicy.CACHE_PAUSE_INITIAL);
        setOption("cache-pause-wait", String.format(Locale.US, "%.3f", config.rebufferMs() / SECONDS_TO_MS));
        setOption("demuxer-thread", "yes");
        setOption("demuxer-seekable-cache", "auto");
        setOption("demuxer-max-bytes", String.valueOf(config.demuxerMaxBytes()));
        setOption("demuxer-max-back-bytes", String.valueOf(config.demuxerMaxBackBytes()));
        setOption("demuxer-readahead-secs", String.valueOf(config.demuxerReadaheadSeconds()));
        setOption("demuxer-hysteresis-secs", String.valueOf(config.demuxerHysteresisSeconds()));
        // Keep these in native initialization because user configs may replace the
        // bundled defaults. Fontconfig indexes readable Android system font directories
        // and lets libass fall back per glyph without bundling a font in the APK.
        setOption("sub-ass", "yes");
        setOption("sub-ass-override", MpvSubtitleStylePolicy.ASS_OVERRIDE);
        setOption("embeddedfonts", "yes");
        setOption("sub-fix-timing", "yes");
        setOption("sub-use-margins", "yes");
        setOption("sub-font-provider", "fontconfig");
        setOption("msg-level", config.logLevel());
        for (Map.Entry<String, String> entry : config.extraOptions().entrySet()) setOption(entry.getKey(), entry.getValue());
    }

    private void applyPostInitOptions() {
        setRuntimeString("save-position-on-quit", "no");
        setRuntimeString("force-window", "no");
        setRuntimeString("idle", "yes");
        int overlayCount = applyPerformanceOptionOverlay();
        effectiveVo = MpvOptionPriorityPolicy.resolveVideoOutput(
                config.performanceOptionsPriority(),
                config.vo(),
                stringProperty("vo", ""));
        boolean autoCacheApplied = applyAutoCacheBaselineToNative();
        boolean autoHlsApplied = applyAutoHlsBitrateToNative();
        applyHardwareSafetyOptions();
        if (!autoCacheBaselineState.snapshot().isEmpty()) {
            PlaybackTrace.log("mpv-auto", playbackTraceId,
                    "action=initial-cache result=%s phase=post-init",
                    autoCacheApplied ? "applied" : "failed");
        }
        if (autoHlsBitrateState.snapshot().staged()) {
            PlaybackTrace.log("mpv-hls-variant", playbackTraceId,
                    "action=apply-option result=%s phase=post-init target=%s",
                    autoHlsApplied ? "applied" : "failed",
                    autoHlsBitrateState.snapshot().stagedOption());
        }
        if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "option priority=%s overlayCount=%d effective cache maxBytes=%s backBytes=%s timeMaster=%s cacheSecs=%s readaheadSecs=%s hysteresisSecs=%s initial=%s rebufferWait=%s", MpvOptionPriorityPolicy.priorityName(config.performanceOptionsPriority()), overlayCount, stringProperty("demuxer-max-bytes", "?"), stringProperty("demuxer-max-back-bytes", "?"), cacheTimeState.snapshot().master().label(), stringProperty("cache-secs", "?"), stringProperty("demuxer-readahead-secs", "?"), stringProperty("demuxer-hysteresis-secs", "?"), stringProperty("cache-pause-initial", "?"), stringProperty("cache-pause-wait", "?"));
    }

    private void applyHardwareSafetyOptions() {
        if (!MpvPerformanceSetting.isZeroCopyBlocked()) return;
        String effectiveHwdec = "no".equals(config.hwdec()) ? "no" : "mediacodec-copy";
        if (!"no".equals(config.hwdec())) setRuntimeString("hwdec", "mediacodec-copy");
        setRuntimeString("vo", config.vo());
        Log.i(TAG, "hardware safety override applied hwdec=" + effectiveHwdec + " vo=" + config.vo());
        SpiderDebug.log("mpv", "hardware safety override hwdec=%s vo=%s", effectiveHwdec, config.vo());
    }

    private boolean applyAutoCacheBaselineToNative() {
        Map<String, String> options = autoCacheBaselineState.snapshot();
        if (options.isEmpty() || !initialized) return false;
        boolean applied = true;
        for (Map.Entry<String, String> entry : options.entrySet()) {
            boolean accepted = setRuntimeStringChecked(entry.getKey(), entry.getValue());
            if (accepted) recordEffectiveCacheOption(entry.getKey(), entry.getValue());
            applied &= accepted;
        }
        return applied;
    }

    private boolean applyAutoHlsBitrateToNative() {
        MpvAutoHlsBitrateState.Snapshot snapshot = autoHlsBitrateState.snapshot();
        if (!snapshot.staged() || !initialized) return false;
        boolean applied = setRuntimeStringChecked(
                "hls-bitrate", snapshot.stagedOption());
        if (applied) autoHlsBitrateState.recordAccepted(snapshot.stagedOption());
        return applied;
    }

    private int applyPerformanceOptionOverlay() {
        Map<String, String> overlay = MpvOptionPriorityPolicy.resolvePerformanceOverlay(config);
        for (Map.Entry<String, String> entry : overlay.entrySet()) {
            if (setRuntimeStringChecked(entry.getKey(), entry.getValue())) {
                cacheTimeState.recordAccepted(entry.getKey(), entry.getValue());
                recordEffectiveCacheOption(entry.getKey(), entry.getValue());
            }
        }
        return overlay.size();
    }

    private void recordEffectiveCacheOption(String name, String value) {
        if (!"demuxer-max-bytes".equals(name)) return;
        try {
            effectiveDemuxerMaxBytes = Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void observeProperties() {
        observedPropertyNames.clear();
        propertyCache.clear();
        observe("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("time-pos/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("duration/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-idle", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("cache-speed", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("cache-buffering-state", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/cache-end", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/reader-pts", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-cache-state/fw-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/total-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/file-cache-bytes", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/raw-input-rate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/bof-cached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("demuxer-cache-state/eof-cached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("cache-secs", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-readahead-secs", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("demuxer-hysteresis-secs", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("hls-bitrate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-state/idle", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("demuxer-cache-state/underrun", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("idle-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("sub-visibility", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("path", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("file-format", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("audio-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("vo-configured", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("width", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("height", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/dw", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/dh", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/dw", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-out-params/dh", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("container-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("estimated-vf-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("video-bitrate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("audio-bitrate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("video-params/primaries", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/gamma", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/colorlevels", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("video-params/colormatrix", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-vo", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-gpu-context", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("gpu-api", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-ao", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("audio-device", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("avsync", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("display-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("estimated-display-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
        observe("decoder-frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("frame-drop-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("mistimed-frame-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("vo-delayed-frame-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("display-sync-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("track-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("vid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("secondary-sid", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("sub-visibility", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("current-tracks/video/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/video/demux-w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("current-tracks/video/demux-h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("current-tracks/audio/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/sub/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("current-tracks/sub2/id", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("chapter", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("chapter-list", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("chapter-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
    }


    private void observeTrackProperties(int count) {
        int limit = Math.min(Math.max(0, count), MAX_OBSERVED_TRACKS);
        for (int i = 0; i < limit; i++) {
            String prefix = "track-list/" + i + "/";
            observe(prefix + "type", MPVLib.MpvFormat.MPV_FORMAT_STRING);
            observe(prefix + "albumart", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            observe(prefix + "id", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "title", MPVLib.MpvFormat.MPV_FORMAT_STRING);
            observe(prefix + "lang", MPVLib.MpvFormat.MPV_FORMAT_STRING);
            observe(prefix + "demux-id", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "src-id", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "codec", MPVLib.MpvFormat.MPV_FORMAT_STRING);
            observe(prefix + "selected", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            observe(prefix + "demux-w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "demux-h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "demux-samplerate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "demux-channel-count", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            observe(prefix + "demux-bitrate", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        }
    }

    private void observeChapterProperties(int count) {
        int limit = Math.min(Math.max(0, count), MAX_OBSERVED_CHAPTERS);
        for (int i = 0; i < limit; i++) {
            String prefix = "chapter-list/" + i + "/";
            observe(prefix + "time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
            observe(prefix + "title", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        }
    }

    private void dispatchProperty(String property, @Nullable Object value) {
        if (!isCoalescedProperty(property)) {
            postToMain(() -> handleProperty(property, value));
            return;
        }
        boolean scheduleDrain = false;
        synchronized (propertyEventLock) {
            coalescedPropertyEvents.put(property, value);
            if (!coalescedPropertyDrainScheduled) {
                coalescedPropertyDrainScheduled = true;
                scheduleDrain = true;
            }
        }
        if (scheduleDrain) mainHandler.postDelayed(
                coalescedPropertyDrainRunnable, PROPERTY_EVENT_COALESCE_MS);
    }

    private void drainCoalescedPropertyEvents() {
        Map<String, Object> snapshot;
        synchronized (propertyEventLock) {
            snapshot = new LinkedHashMap<>(coalescedPropertyEvents);
            coalescedPropertyEvents.clear();
        }
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            handleProperty(entry.getKey(), entry.getValue());
        }
        synchronized (propertyEventLock) {
            if (coalescedPropertyEvents.isEmpty()) {
                coalescedPropertyDrainScheduled = false;
            } else {
                mainHandler.postDelayed(
                        coalescedPropertyDrainRunnable,
                        PROPERTY_EVENT_COALESCE_MS);
            }
        }
    }

    private boolean isCoalescedProperty(String property) {
        return switch (property) {
            case "time-pos", "time-pos/full",
                    "demuxer-cache-duration", "demuxer-cache-state/cache-duration",
                    "demuxer-cache-time", "demuxer-cache-state/cache-end",
                    "demuxer-cache-state/reader-pts",
                    "cache-speed", "demuxer-cache-state/raw-input-rate",
                    "cache-buffering-state", "demuxer-cache-state/fw-bytes",
                    "demuxer-cache-state/total-bytes", "demuxer-cache-state/file-cache-bytes",
                    "avsync", "display-fps", "estimated-display-fps",
                    "decoder-frame-drop-count", "frame-drop-count",
                    "mistimed-frame-count", "vo-delayed-frame-count",
                    "width", "height", "video-params/w", "video-params/h",
                    "video-params/dw", "video-params/dh", "video-out-params/w",
                    "video-out-params/h", "video-out-params/dw", "video-out-params/dh",
                    "current-tracks/video/demux-w", "current-tracks/video/demux-h",
                    "container-fps", "estimated-vf-fps" -> true;
            default -> false;
        };
    }

    private void clearCoalescedPropertyEvents() {
        mainHandler.removeCallbacks(coalescedPropertyDrainRunnable);
        synchronized (propertyEventLock) {
            coalescedPropertyEvents.clear();
            coalescedPropertyDrainScheduled = false;
        }
    }

    private void handleProperty(String property, @Nullable Object value) {
        if (released) return;
        boolean firstCacheTimeReadback = cacheTimeState.recordObserved(property, value);
        if (firstCacheTimeReadback) {
            MpvCacheTimeState.Snapshot cacheTime = cacheTimeState.snapshot();
            if (cacheTime.observedOptions() == 3) {
                PlaybackTrace.log("mpv-cache-time", playbackTraceId,
                        "master=%s reason=%s cache=%d readahead=%d hysteresis=%d observed=%d result=native-readback",
                        cacheTime.master().label(), cacheTime.reason().label(),
                        cacheTime.cacheSeconds(), cacheTime.readaheadSeconds(),
                        cacheTime.hysteresisSeconds(), cacheTime.observedOptions());
            }
        }
        boolean firstHlsReadback = autoHlsBitrateState.recordObserved(
                property, value);
        if (firstHlsReadback) {
            MpvAutoHlsBitrateState.Snapshot hls = autoHlsBitrateState.snapshot();
            PlaybackTrace.log("mpv-hls-variant", playbackTraceId,
                    "action=native-readback observed=%d count=%d",
                    hls.observedBitsPerSecond(), hls.observedCount());
        }
        if (mediaItem == null) {
            playbackState = Player.STATE_IDLE;
            loading = false;
            return;
        }
        propertyCache.put(property, value);
        boolean firstObservedCacheMetric = cacheObserverState.record(property, value, SystemClock.elapsedRealtime()) && cacheObserverState.observedCount() == 1;
        if (firstObservedCacheMetric) PlaybackTrace.log("mpv", playbackTraceId, "cache source=observer-first property=%s", property);
        cacheObservedVideoProperty(property, value);
        boolean stateChanged = false;
        switch (property) {
            case "time-pos", "time-pos/full" -> cachedPositionMs =
                    stabilizedPositionMs(doubleSecondsToMs(value, cachedPositionMs));
            case "duration", "duration/full" -> {
                long durationMs = doubleSecondsToMs(value, cachedDurationMs);
                if (durationMs != cachedDurationMs) {
                    cachedDurationMs = durationMs;
                    stateChanged = true;
                }
            }
            case "demuxer-cache-duration", "demuxer-cache-state/cache-duration" -> cachedCacheDurationMs = Math.max(0, doubleSecondsToMs(value, cachedCacheDurationMs));
            case "demuxer-cache-time", "demuxer-cache-state/cache-end" -> cachedCacheEndMs = Math.max(0, doubleSecondsToMs(value, cachedCacheEndMs));
            case "demuxer-cache-state/reader-pts" -> cachedCacheReaderPositionMs = Math.max(0, doubleSecondsToMs(value, cachedCacheReaderPositionMs));
            case "cache-speed", "demuxer-cache-state/raw-input-rate" -> {
                cachedCacheSpeedBytesPerSecond = Math.max(0,
                        longValue(value, cachedCacheSpeedBytesPerSecond));
                if (value instanceof Number) {
                    cachedCacheSpeedSampleAtMs = SystemClock.elapsedRealtime();
                }
            }
            case "cache-buffering-state" -> cachedCacheBufferingState = Math.max(0, (int) Math.min(100, longValue(value, cachedCacheBufferingState)));
            case "demuxer-cache-state/fw-bytes" -> cachedCacheForwardBytes = Math.max(0, longValue(value, cachedCacheForwardBytes));
            case "demuxer-cache-state/total-bytes" -> cachedCacheTotalBytes = Math.max(0, longValue(value, cachedCacheTotalBytes));
            case "demuxer-cache-state/file-cache-bytes" -> cachedCacheFileBytes = Math.max(0, longValue(value, cachedCacheFileBytes));
            case "demuxer-cache-idle", "demuxer-cache-state/idle" -> cachedCacheIdle = Boolean.TRUE.equals(value);
            case "demuxer-cache-state/underrun" -> recordCacheUnderrun(
                    Boolean.TRUE.equals(value));
            case "demuxer-cache-state/bof-cached" -> cachedCacheBof = Boolean.TRUE.equals(value);
            case "demuxer-cache-state/eof-cached" -> cachedCacheEof = Boolean.TRUE.equals(value);
            case "pause" -> {
                if (value instanceof Boolean paused) {
                    boolean activeMedia = fileLoaded
                            && !stopping
                            && !eofReached
                            && playbackState != Player.STATE_IDLE
                            && playbackState != Player.STATE_ENDED;
                    boolean effectivePlayWhenReady = playWhenReady
                            && !initialTrackSelectionGateActive;
                    MpvPauseIntentPolicy.Action action = MpvPauseIntentPolicy.resolve(
                            effectivePlayWhenReady, paused, activeMedia);
                    if (action == MpvPauseIntentPolicy.Action.REASSERT_REQUESTED_STATE) {
                        boolean requestedPaused = shouldPauseNativePlayback();
                        PlaybackTrace.log("mpv", playbackTraceId,
                                "pause intent reconcile observed=%s requested=%s state=%d",
                                paused, requestedPaused, playbackState);
                        safeSetPropertyBoolean("pause", requestedPaused);
                    }
                }
            }
            case "paused-for-cache" -> {
                boolean nextLoading = Boolean.TRUE.equals(value);
                int nextPlaybackState = playbackState;
                if (nextLoading) nextPlaybackState = Player.STATE_BUFFERING;
                else if (playbackState == Player.STATE_BUFFERING && fileLoaded && playbackRestarted) nextPlaybackState = Player.STATE_READY;
                stateChanged = loading != nextLoading || playbackState != nextPlaybackState;
                loading = nextLoading;
                playbackState = nextPlaybackState;
                if (nextPlaybackState == Player.STATE_READY) endSeekBuffering("paused-for-cache");
            }
            case "eof-reached" -> {
                boolean wasEnded = playbackState == Player.STATE_ENDED;
                eofReached = Boolean.TRUE.equals(value);
                if (eofReached) markPlaybackEnded("property:eof-reached");
                stateChanged = !wasEnded && playbackState == Player.STATE_ENDED;
            }
            case "idle-active" -> {
                boolean wasEnded = playbackState == Player.STATE_ENDED;
                idleActive = Boolean.TRUE.equals(value);
                if (idleActive && fileLoaded && !stopping) markPlaybackEnded("property:idle-active");
                stateChanged = !wasEnded && playbackState == Player.STATE_ENDED;
            }
            case "width", "height", "video-params/w", "video-params/h", "video-params/dw", "video-params/dh", "video-out-params/w", "video-out-params/h", "video-out-params/dw", "video-out-params/dh", "current-tracks/video/demux-w", "current-tracks/video/demux-h" -> {
                int previousWidth = videoSize.width;
                int previousHeight = videoSize.height;
                updateVideoSize("property:" + property);
                stateChanged = previousWidth != videoSize.width || previousHeight != videoSize.height;
                scheduleTrackRefresh(property);
            }
            case "container-fps", "estimated-vf-fps" -> {
                cachedContentFrameRate = videoFrameRate();
                applySurfaceFrameRate();
                scheduleTrackRefresh(property);
            }
            case "video-params/primaries", "video-params/gamma", "video-params/colorlevels", "video-params/colormatrix" -> scheduleTrackRefresh(property);
            case "current-vo" -> {
                observedCurrentVo = value instanceof String;
                cachedCurrentVo = value instanceof String text ? text : cachedCurrentVo;
            }
            case "current-gpu-context" -> cachedCurrentGpuContext = stringValue(value, cachedCurrentGpuContext);
            case "gpu-api" -> cachedGpuApi = stringValue(value, cachedGpuApi);
            case "current-ao" -> cachedCurrentAo = stringValue(value, cachedCurrentAo);
            case "audio-device" -> cachedAudioDevice = stringValue(value, cachedAudioDevice);
            case "hwdec-current" -> {
                observedHwdecCurrent = value instanceof String;
                cachedHwdecCurrent = value instanceof String text ? text : cachedHwdecCurrent;
            }
            case "avsync" -> cachedAvSyncSeconds = doubleValue(value, cachedAvSyncSeconds);
            case "display-fps" -> cachedDisplayFps = doubleValue(value, cachedDisplayFps);
            case "estimated-display-fps" -> cachedEstimatedDisplayFps = doubleValue(value, cachedEstimatedDisplayFps);
            case "decoder-frame-drop-count" -> {
                observedDroppedFrames = value instanceof Number;
                cachedDecoderDroppedFrames = Math.max(0, longValue(value, cachedDecoderDroppedFrames));
            }
            case "frame-drop-count" -> {
                observedDroppedFrames = value instanceof Number;
                cachedOutputDroppedFrames = Math.max(0, longValue(value, cachedOutputDroppedFrames));
            }
            case "mistimed-frame-count" -> cachedMistimedFrames = Math.max(0, longValue(value, cachedMistimedFrames));
            case "vo-delayed-frame-count" -> cachedDelayedFrames = Math.max(0, longValue(value, cachedDelayedFrames));
            case "display-sync-active" -> cachedDisplaySyncActive = Boolean.TRUE.equals(value);
            case "track-list/count" -> {
                observeTrackProperties((int) Math.max(0, longValue(value, 0)));
                int previousWidth = videoSize.width;
                int previousHeight = videoSize.height;
                updateVideoSize("property:" + property);
                stateChanged = previousWidth != videoSize.width || previousHeight != videoSize.height;
                scheduleTrackRefresh(property);
            }
            case "vid", "aid", "sid", "secondary-sid", "sub-visibility", "current-tracks/video/id", "current-tracks/audio/id", "current-tracks/sub/id", "current-tracks/sub2/id" -> scheduleTrackRefresh(property);
            case "chapter" -> {
                if (value instanceof Number number) currentChapter = number.intValue();
                if (!shouldDeferStartupMetadataRefresh()) scheduleChapterRefresh();
            }
            case "chapter-list" -> {
                if (!shouldDeferStartupMetadataRefresh()) handleChapterListProperty(value);
            }
            case "chapter-list/count" -> {
                observeChapterProperties((int) Math.max(0, longValue(value, 0)));
                if (!shouldDeferStartupMetadataRefresh()) scheduleChapterRefresh();
            }
            default -> {
                if (property.startsWith("track-list/")) scheduleTrackRefresh(property);
                else if (property.startsWith("chapter-list/")) scheduleChapterRefresh();
            }
        }
        if (stateChanged) invalidateState();
    }

    public Tracks getCurrentTracksSnapshot() {
        return currentTracks;
    }

    public VideoTrackDiagnostics getSelectedVideoTrackDiagnostics() {
        return selectedVideoTrackDiagnostics;
    }

    /** Metadata for the first video track, including when mpv temporarily reports vid=no. */
    public VideoTrackDiagnostics getAvailableVideoTrackDiagnostics() {
        return availableVideoTrackDiagnostics;
    }

    public VideoSize getVideoSizeSnapshot() {
        return videoSize;
    }

    public void setVideoSizeProbeListener(@Nullable BiConsumer<Integer, Integer> listener) {
        videoSizeProbeListener = listener;
    }

    public void setInitialOsdSurfaceRequested(boolean requested) {
        initialOsdSurfaceRequested = requested;
        if (mediaItem != null) return;
        osdSurfaceUsedForCurrentMedia = requested;
        setOsdSurfaceRequested(requested);
    }

    public void setInitialTrackSelectionGateRequested(boolean requested) {
        initialTrackSelectionGateRequested = requested;
    }

    public void setInitialSubtitleTrackId(@Nullable String trackId) {
        initialSubtitleTrackId = trackId;
    }

    public void releaseInitialTrackSelectionGate() {
        releaseInitialTrackSelectionGate("tracks-restored");
    }

    private void releaseInitialTrackSelectionGate(String reason) {
        if (!initialTrackSelectionGateActive) return;
        initialTrackSelectionGateActive = false;
        mainHandler.removeCallbacks(initialTrackSelectionGateTimeoutRunnable);
        PlaybackTrace.log("mpv", playbackTraceId,
                "initial track gate released reason=%s play=%s fileLoaded=%s restart=%s",
                reason, playWhenReady, fileLoaded, playbackRestarted);
        if (initialized && fileLoaded && !stopping && !eofReached) {
            safeSetPropertyBoolean("pause", shouldPauseNativePlayback());
            invalidateState();
        }
    }

    private boolean shouldPauseNativePlayback() {
        return MpvInitialTrackSelectionPolicy.shouldPauseNativePlayback(
                playWhenReady, initialTrackSelectionGateActive);
    }

    public void resetTrackSelection() {
        audioTrackManuallySelected = true;
        preferAacApplied = true;
        setMpvTrack(C.TRACK_TYPE_VIDEO, "auto");
        setMpvTrack(C.TRACK_TYPE_AUDIO, "auto");
        setMpvTrack(C.TRACK_TYPE_TEXT, "auto");
        setSecondarySubtitleTrackSelection("no");
        refreshTracks();
        invalidateState();
    }

    public void setTrackSelection(int type, String mpvId) {
        if (TextUtils.isEmpty(mpvId)) return;
        if (type == C.TRACK_TYPE_AUDIO) {
            audioTrackManuallySelected = true;
            preferAacApplied = true;
        }
        setMpvTrack(type, mpvId);
        refreshTracks();
        invalidateState();
    }

    public void restoreVideoTrackSelection() {
        if (!initialized || config.deferStartupTrackRefresh() && !playbackRestarted) return;
        int count = Math.max(0, intProperty("track-list/count", 0));
        for (int index = 0; index < count; index++) {
            TrackInfo info = readTrackInfo(index, C.INDEX_UNSET);
            if (info == null || info.type != C.TRACK_TYPE_VIDEO) continue;
            setMpvTrack(C.TRACK_TYPE_VIDEO, info.id);
            SpiderDebug.log("mpv", "restore video track id=%s codec=%s", info.id, info.codec);
            refreshTracks();
            invalidateState();
            return;
        }
        SpiderDebug.log("mpv", "restore video track skipped no video track");
    }

    public void setSecondarySubtitleTrackSelection(String mpvId) {
        if (TextUtils.isEmpty(mpvId) || !initialized) return;
        safeSetPropertyString("secondary-sid", mpvId);
        syncOsdSurfaceRequirementFromMpv();
        SpiderDebug.log("mpv", "select secondary subtitle id=%s", mpvId);
        refreshTracks();
        invalidateState();
    }

    public boolean isSecondarySubtitleSelected(String mpvId) {
        if (TextUtils.isEmpty(mpvId)) return false;
        String selected = secondarySubtitleTrackId();
        if (TextUtils.isEmpty(selected) || isAutoTrackChoice(selected) || isDisabledTrackChoice(selected)) return false;
        return selected.equals(mpvId) || normalizeTrackId(selected).equals(normalizeTrackId(mpvId));
    }

    public boolean selectEdition(MediaEdition edition) {
        if (edition == null || edition.index < 0 || edition.index >= currentChapters.size()) return false;
        currentChapter = edition.index;
        if (initialized) safeSetPropertyInt("chapter", edition.index);
        refreshChapters();
        invalidateState();
        return true;
    }

    private void setMpvTrack(int type, String mpvId) {
        if (!initialized) return;
        String property = mpvTrackProperty(type);
        if (property == null) return;
        String current = propertyStringOrInt(property);
        if (MpvInitialTrackSelectionPolicy.isSameTrackSelection(
                current, mpvId)) {
            SpiderDebug.log("mpv",
                    "select track skipped type=%d property=%s id=%s reason=already-selected",
                    type, property, mpvId);
            return;
        }
        try {
            mpvSetPropertyString(property, mpvId);
            if (type == C.TRACK_TYPE_TEXT) syncOsdSurfaceRequirementFromMpv();
            Log.d(TAG, "set track property=" + property + " requested=" + mpvId + " actual=" + propertyStringOrInt(property));
        } catch (Throwable e) {
            Log.e(TAG, "set track failed property=" + property + " requested=" + mpvId, e);
        }
        SpiderDebug.log("mpv", "select track type=%d property=%s id=%s", type, property, mpvId);
    }

    @Nullable
    private String mpvTrackProperty(int type) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "vid";
            case C.TRACK_TYPE_AUDIO -> "aid";
            case C.TRACK_TYPE_TEXT -> "sid";
            default -> null;
        };
    }

    private void handleEvent(int eventId) {
        if (released) return;
        if (mediaItem == null && eventId != MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) {
            playbackState = Player.STATE_IDLE;
            loading = false;
            Log.d(TAG, "Ignore stale mpv event without media item event=" + eventId);
            invalidateState();
            return;
        }
        switch (eventId) {
            case MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                mediaReplacementCoordinator.onStartFile();
                mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
                loadStarted = true;
                playbackState = Player.STATE_BUFFERING;
                loading = true;
                fileLoaded = false;
                fileLoadedAtElapsedRealtimeMs = 0;
                playbackRestarted = false;
                stopping = false;
                eofReached = false;
                idleActive = false;
                // A new load supersedes any open seek window. Leaving it armed lets its
                // timeout fire once this load reaches FILE_LOADED and publish READY over
                // a file that has not restarted playback yet.
                endSeekBuffering("start-file");
                resetFailureSignals();
                if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "event=start-file source=%s", MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri));
                mainHandler.removeCallbacks(endFileValidationRunnable);
                mainHandler.removeCallbacks(loadStartRetryRunnable);
                startStateRefresh();
            }
            case MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                if (loadedUnexpectedImage()) {
                    fail(mpvError(ERROR_UNEXPECTED_IMAGE, "path=" + firstNonEmpty(stringProperty("path", ""), currentPlayableUri)), PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED);
                    return;
                }
                fileLoaded = true;
                fileLoadedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
                cacheObserverState.onFileLoaded(SystemClock.elapsedRealtime());
                mainHandler.removeCallbacks(endFileValidationRunnable);
                playbackState = Player.STATE_BUFFERING;
                loading = true;
                updateVideoSize("event=file-loaded");
                applyVideoAspect();
                if (config.deferStartupTrackRefresh()) {
                    scheduleTrackRefresh("event=file-loaded");
                } else {
                    refreshTracks();
                    refreshChapters();
                }
                if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "event=file-loaded duration=%d size=%dx%d path=%s", cachedDurationMs, videoSize.width, videoSize.height, MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri));
                addSubtitleConfigurations();
                if (initialSeekPositionMs != C.TIME_UNSET) {
                    long targetPositionMs = initialSeekPositionMs;
                    initialSeekPositionMs = C.TIME_UNSET;
                    resetCacheTimelineForSeek(targetPositionMs);
                    seekPositionState.begin(
                            targetPositionMs, SystemClock.elapsedRealtime());
                    boolean loadedAtTarget = loadStartPositionMs == targetPositionMs;
                    if (!loadedAtTarget) {
                        seekMpv(targetPositionMs);
                    } else if (shouldCollectDebugDetails()) {
                        PlaybackTrace.log("mpv", playbackTraceId,
                                "initial seek embedded target=%d observed=%d",
                                targetPositionMs, cachedPositionMs);
                    }
                }
                loadStartPositionMs = C.TIME_UNSET;
                safeSetPropertyBoolean("pause", shouldPauseNativePlayback());
                updatePreloadCacheOverlay();
                startStateRefresh();
            }
            case MPVLib.MpvEvent.MPV_EVENT_SEEK -> {
                // mpv stops playback here and only resumes at PLAYBACK_RESTART. Seeks that
                // mpv starts on its own (chapter jumps, its own EDL/loop handling) never
                // pass through handleSeek, so this is the second entrance to the same
                // window; handleSeek covers the ones the app requests.
                boolean reseekingInWindow = playbackState == Player.STATE_BUFFERING && seekBufferingActive;
                if (fileLoaded && !stopping && playbackState != Player.STATE_IDLE
                        && playbackState != Player.STATE_ENDED
                        && (playbackState != Player.STATE_BUFFERING || reseekingInWindow)) {
                    // A BUFFERING that a cache stall opened is left alone: it is not a seek,
                    // and claiming it would drop a real rebuffer from the statistics. An open
                    // seek window does restart its deadline, so a chain of chapter jumps
                    // cannot inherit the remaining time of the first one.
                    playbackState = Player.STATE_BUFFERING;
                    beginSeekBuffering("event");
                }
            }
            case MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                playbackRestarted = true;
                endSeekBuffering("playback-restart");
                if (config.deferStartupTrackRefresh()) {
                    scheduleTrackRefresh("event=playback-restart");
                }
                if (initialTrackSelectionGateActive) {
                    mainHandler.removeCallbacks(initialTrackSelectionGateTimeoutRunnable);
                    mainHandler.postDelayed(initialTrackSelectionGateTimeoutRunnable,
                            INITIAL_TRACK_SELECTION_RESTORE_TIMEOUT_MS);
                }
                if (currentLikelyHls) requestHlsPreload(cachedPositionMs);
                updateVideoSize("event=playback-restart");
                if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "event=playback-restart position=%d duration=%d size=%dx%d", cachedPositionMs, cachedDurationMs, videoSize.width, videoSize.height);
                if (playbackState != Player.STATE_ENDED) {
                    playbackState = Player.STATE_READY;
                    loading = false;
                    startStateRefresh();
                }
            }
            case MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                videoReconfigCount++;
                updateVideoSize("event=video-reconfig#" + videoReconfigCount);
            }
            case MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                handleEndFile(MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN, MPVLib.MpvError.MPV_ERROR_SUCCESS, null);
                return;
            }
            case MPVLib.MpvEvent.MPV_EVENT_IDLE -> {
                if (fileLoaded && !stopping) {
                    markPlaybackEnded("event:idle");
                } else if (loading && !stopping) {
                    playbackState = Player.STATE_BUFFERING;
                    mainHandler.removeCallbacks(endFileValidationRunnable);
                    mainHandler.postDelayed(endFileValidationRunnable, END_FILE_VALIDATION_DELAY_MS);
                    startStateRefresh();
                }
            }
            case MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> {
                if (fileLoaded && !stopping) {
                    markPlaybackEnded("event:shutdown");
                } else {
                    playbackState = Player.STATE_IDLE;
                    loading = false;
                    stopStateRefresh();
                }
            }
            default -> {
            }
        }
        invalidateState();
    }

    private void handleEndFile(int reason, int error, @Nullable String errorText) {
        if (released) return;
        if (mediaReplacementCoordinator.shouldIgnoreEndFile(stopping, loadStarted)) {
            SpiderDebug.log("mpv", "ignore replaced-media end-file generation=%d reason=%s(%d) error=%s(%d)", mediaReplacementCoordinator.generation(), endFileReasonName(reason), reason, mpvErrorName(error), error);
            return;
        }
        // The file is over however this ends, so no seek inside it can still resolve.
        // Clearing here rather than per-branch covers the natural-EOF path, which sets
        // ENDED directly instead of going through markPlaybackEnded().
        endSeekBuffering("end-file");
        lastEndFileReason = reason;
        lastEndFileError = error;
        lastEndFileErrorText = errorText;
        if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "event=end-file reason=%s(%d) error=%s(%d) text=%s loaded=%s restart=%s eof=%s stopping=%s source=%s",
                endFileReasonName(reason), reason, mpvErrorName(error), error, TextUtils.isEmpty(errorText) ? "-" : MpvDiagnosticsPolicy.redactSensitive(errorText),
                fileLoaded, playbackRestarted, eofReached, stopping, MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri));
        stopStateRefresh();
        loading = false;
        boolean resumeReplacement = false;
        if (stopping) {
            stopping = false;
            resumeReplacement = mediaReplacementCoordinator.resumeAfterStopAcknowledged();
        } else if (reason == MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR) {
            fail(nativeEndFileError(reason, error, errorText), nativeEndFilePlaybackExceptionCode(error));
            return;
        } else if (isSuccessfulNaturalEof(reason, error)) {
            playbackState = Player.STATE_ENDED;
        } else if (isFailedLoadedMedia()) {
            fail(new IOException(failedLoadedMediaMessage()), PlaybackException.ERROR_CODE_DECODING_FAILED);
            return;
        } else if (fileLoaded || eofReached) {
            markPlaybackEnded("event:end-file");
        } else {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
            mainHandler.removeCallbacks(endFileValidationRunnable);
            mainHandler.postDelayed(endFileValidationRunnable, END_FILE_VALIDATION_DELAY_MS);
            startStateRefresh();
        }
        invalidateState();
        if (resumeReplacement) resumeMediaReplacement("stop-ack");
    }

    private void markPlaybackEnded(String reason) {
        if (playbackState == Player.STATE_ENDED) return;
        endSeekBuffering("ended");
        eofReached = true;
        loading = false;
        playbackState = Player.STATE_ENDED;
        stopStateRefresh();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        SpiderDebug.log("mpv", "playback ended reason=%s position=%d duration=%d", reason, cachedPositionMs, cachedDurationMs);
        stopMainThreadWatchdog();
    }

    private boolean isSuccessfulNaturalEof(int reason, int error) {
        if (reason != MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF || error != MPVLib.MpvError.MPV_ERROR_SUCCESS || !fileLoaded) return false;
        if (eofReached) return true;
        long duration = cachedDurationMs;
        long position = cachedPositionMs;
        if (duration == C.TIME_UNSET || duration <= 0) return playbackRestarted && position > 0 && !sawNetworkError && !sawDecodeError && !sawDrmError;
        long tolerance = Math.max(3000L, Math.min(15000L, duration / 100L));
        return position >= Math.max(0, duration - tolerance);
    }

    private boolean isLikelyHls(MediaItem item, String uri) {
        if (uri != null && uri.startsWith("edl://")) return false;
        if (item.localConfiguration != null) {
            String mimeType = item.localConfiguration.mimeType;
            if (MimeTypes.APPLICATION_M3U8.equals(mimeType)
                    || "application/vnd.apple.mpegurl".equalsIgnoreCase(mimeType)
                    || "application/x-mpegurl".equalsIgnoreCase(mimeType)
                    || "hls".equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        return lower.contains("m3u8");
    }

    private boolean isLikelyDash(MediaItem item, String uri) {
        if (uri != null && uri.startsWith("edl://")) return false;
        if (item.localConfiguration != null) {
            String mimeType = item.localConfiguration.mimeType;
            if (MimeTypes.APPLICATION_MPD.equals(mimeType)
                    || "application/dash+xml".equalsIgnoreCase(mimeType)
                    || "dash".equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        return lower.contains(".mpd") || lower.contains("type=mpd") || lower.contains("format=mpd");
    }

    private boolean loadedUnexpectedImage() {
        String path = firstNonEmpty(stringProperty("path", ""), currentPlayableUri);
        if (!isImageUri(path)) return false;
        if (!TextUtils.isEmpty(currentPlayableUri) && sameUri(path, currentPlayableUri)) return false;
        Log.w(TAG, "unexpected image pathScheme=" + safeScheme(path)
                + " requestedScheme=" + safeScheme(currentPlayableUri)
                + " requestedLen=" + (currentPlayableUri == null ? 0 : currentPlayableUri.length()));
        return true;
    }

    private String safeScheme(String value) {
        try {
            return String.valueOf(Uri.parse(value).getScheme());
        } catch (Throwable ignored) {
            return "invalid";
        }
    }

    private boolean isImageUri(String uri) {
        if (TextUtils.isEmpty(uri)) return false;
        String lower = uri.toLowerCase(Locale.US);
        int end = lower.length();
        int query = lower.indexOf('?');
        int fragment = lower.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        lower = lower.substring(0, end);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp")
                || lower.endsWith(".avif");
    }

    private boolean sameUri(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean shouldProxyHls(String uri, boolean likelyHls) {
        if (!likelyHls || TextUtils.isEmpty(uri)) return false;
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return !"/mpv/index.m3u8".equals(parsed.getPath()) && !"/mpv/item".equals(parsed.getPath());
    }

    private boolean hasDrmConfiguration(MediaItem item) {
        return item != null && item.localConfiguration != null && item.localConfiguration.drmConfiguration != null;
    }

    private Map<String, String> applyMediaOptions(MediaItem item) {
        MpvRequestHeaderPolicy.Resolved resolved = MpvRequestHeaderPolicy.resolve(extractHeaders(item), config.userAgent());
        Map<String, String> headers = resolved.headers();
        String userAgent = resolved.userAgent();
        String referer = resolved.referer();
        String origin = resolved.origin();
        String headerFields = buildHeaderFields(headers);
        setRuntimeString("user-agent", userAgent == null ? "" : userAgent);
        setRuntimeString("referrer", referer == null ? "" : referer);
        setRuntimeString("http-header-fields", headerFields);
        if (item.mediaMetadata.title != null) setRuntimeString("force-media-title", item.mediaMetadata.title.toString());
        SpiderDebug.log("mpv", "media options sourceHeadersOnly=true uaEmpty=%s refererEmpty=%s originEmpty=%s headerNames=%s headerFields=%s",
                TextUtils.isEmpty(userAgent), TextUtils.isEmpty(referer), TextUtils.isEmpty(origin), headerNames(headers), !TextUtils.isEmpty(headerFields));
        return headers;
    }

    private Map<String, String> extractHeaders(MediaItem item) {
        if (item.requestMetadata.extras == null) return Map.of();
        android.os.Bundle extras = item.requestMetadata.extras;
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        for (String key : extras.keySet()) {
            String value = extras.getString(key);
            if (value != null) headers.put(key, value);
        }
        return headers;
    }

    private String buildHeaderFields(Map<String, String> headers) {
        if (headers.isEmpty()) return "";
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (equalsHeader(key, HttpHeaders.USER_AGENT) || equalsHeader(key, HttpHeaders.REFERER) || equalsHeader(key, HttpHeaders.RANGE)) continue;
            fields.add(key + ": " + escapeListValue(entry.getValue()));
        }
        return String.join(",", fields);
    }

    private List<String> headerNames(Map<String, String> headers) {
        if (headers.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (String key : headers.keySet()) names.add(key);
        return names;
    }

    private String escapeListValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }

    private boolean equalsHeader(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private String playableUri(Uri uri) throws IOException {
        String value = uri.toString();
        if (isConcatenatingUri(value)) return edlUri(value);
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (fd == null) throw new IOException("Unable to open content uri: " + uri);
            contentFds.add(fd);
            return "fd://" + fd.getFd();
        }
        return value;
    }

    private String playableUri(MediaItem item) throws IOException {
        Uri requestUri = item.requestMetadata.mediaUri;
        if (requestUri != null && isConcatenatingUri(requestUri.toString())) return edlUri(requestUri.toString());
        return playableUri(item.localConfiguration.uri);
    }

    private boolean isConcatenatingUri(String uri) {
        return uri != null && uri.contains(CONCAT_SOURCE_SEPARATOR) && uri.contains(CONCAT_DURATION_SEPARATOR);
    }

    private String edlUri(String uri) throws IOException {
        StringBuilder builder = new StringBuilder("edl://");
        int count = 0;
        for (String split : uri.split(CONCAT_SOURCE_SEPARATOR_REGEX)) {
            String[] info = split.split(CONCAT_DURATION_SEPARATOR_REGEX, 2);
            if (info.length < 2 || TextUtils.isEmpty(info[0])) continue;
            if (count++ > 0) builder.append(';');
            builder.append("file=").append(edlValue(info[0]));
            long durationUs = parseLong(info[1], C.TIME_UNSET);
            if (durationUs > 0) builder.append(",length=").append(String.format(Locale.US, "%.3f", durationUs / MICROSECONDS_TO_SECONDS));
        }
        if (count == 0) throw new IOException("Invalid concatenating media uri");
        SpiderDebug.log("mpv", "concat uri converted to EDL segments=%d", count);
        return builder.toString();
    }

    private String edlValue(String value) {
        return "%" + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "%" + value;
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void addSubtitleConfigurations() {
        if (mediaItem == null || mediaItem.localConfiguration == null || mediaItem.localConfiguration.subtitleConfigurations.isEmpty()) return;
        for (MediaItem.SubtitleConfiguration sub : mediaItem.localConfiguration.subtitleConfigurations) {
            Uri uri = sub.uri;
            String mode = MpvSubtitleSourcePolicy.addMode(sub.selectionFlags);
            String format = null;
            try {
                String source = playableSubtitleUri(uri);
                if (source.startsWith("fd://")) {
                    format = MpvSubtitleSourcePolicy.lavfFormat(uri == null ? null : uri.toString(), sub.mimeType);
                    if (!TextUtils.isEmpty(format)) configureSubtitleDemuxer(format);
                }
                MPVLib.command(subtitleAddCommand(source, mode, sub.label, sub.language));
                if ("select".equals(mode) && booleanProperty("sub-visibility", true)) safeSetPropertyBoolean("sub-visibility", true);
            } catch (Throwable e) {
                Log.e(TAG, "add external subtitle failed uri=" + uri, e);
            } finally {
                if (!TextUtils.isEmpty(format)) resetSubtitleDemuxer();
            }
        }
    }

    private void configureSubtitleDemuxer(String format) {
        MPVLib.command(new String[]{"set", "sub-demuxer", "lavf"});
        MPVLib.command(new String[]{"set", "demuxer-lavf-format", format});
    }

    private void resetSubtitleDemuxer() {
        try {
            MPVLib.command(new String[]{"set", "demuxer-lavf-format", ""});
        } finally {
            MPVLib.command(new String[]{"set", "sub-demuxer", ""});
        }
    }

    private String playableSubtitleUri(Uri uri) throws IOException {
        String path = uri == null ? null : uri.getPath();
        String scheme = uri == null ? null : uri.getScheme();
        if (!MpvSubtitleSourcePolicy.requiresFileDescriptor(scheme, path)) return playableUri(uri);
        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            contentFds.add(fd);
            return "fd://" + fd.getFd();
        } catch (Exception e) {
            if (fd != null) try { fd.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    private String[] subtitleAddCommand(String source, String mode, @Nullable String title, @Nullable String language) {
        if (!TextUtils.isEmpty(language)) return new String[]{"sub-add", source, mode, TextUtils.isEmpty(title) ? "" : title, language};
        if (!TextUtils.isEmpty(title)) return new String[]{"sub-add", source, mode, title};
        return new String[]{"sub-add", source, mode};
    }

    private void setVideoOutput(Object output) {
        clearSurfaceFrameRate();
        resetSurfaceFrameRateRequest();
        detachMpvSurface();
        detachSurfaceHolder();
        removeOsdSurfaceView();
        surfaceWidth = 0;
        surfaceHeight = 0;
        Log.d(SIZE_TAG, "mpv setVideoOutput output=" + surfaceOutputName(output));
        if (output instanceof SurfaceView view) {
            updateSurfaceSize(view);
            setSurfaceHolder(view.getHolder());
        } else if (output instanceof TextureView view && view.getSurfaceTexture() != null) {
            updateSurfaceSize(view);
            releaseOwnedSurface();
            surface = new Surface(view.getSurfaceTexture());
            ownsSurface = true;
        } else if (output instanceof SurfaceHolder holder) {
            setSurfaceHolder(holder);
        } else if (output instanceof Surface s) {
            releaseOwnedSurface();
            surface = s;
            ownsSurface = false;
        }
        reconcileOsdSurface();
        bindVideoOutput();
    }

    private void setSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
        updateSurfaceSize(holder);
        Log.d(SIZE_TAG, "mpv setSurfaceHolder frame=" + surfaceFrame(holder) + " cached=" + surfaceWidth + "x" + surfaceHeight);
        surfaceHolder.addCallback(surfaceCallback);
        surface = surfaceHolder.getSurface();
        ownsSurface = false;
    }

    private boolean requiresOsdSurface() {
        return "mediacodec_embed".equals(videoOutputVo());
    }

    private String videoOutputVo() {
        return TextUtils.isEmpty(effectiveVo) ? config.vo() : effectiveVo;
    }

    private void createOsdSurfaceView() {
        if (!requiresOsdSurface() || osdSurfaceView != null
                || !(videoOutput instanceof SurfaceView videoView)) return;
        if (!(videoView.getParent() instanceof ViewGroup parent)) {
            Log.e(TAG, "Unable to create direct-output OSD surface without a ViewGroup parent");
            return;
        }

        SurfaceView overlay = new SurfaceView(videoView.getContext());
        overlay.setZOrderMediaOverlay(true);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        int videoIndex = parent.indexOfChild(videoView);
        int overlayIndex = videoIndex < 0 ? parent.getChildCount() : Math.min(parent.getChildCount(), videoIndex + 1);
        osdSurfaceView = overlay;
        osdSurfaceHolder = overlay.getHolder();
        osdSurfaceHolder.addCallback(osdSurfaceCallback);
        parent.addView(overlay, overlayIndex);
        osdSurface = osdSurfaceHolder.getSurface();
        SpiderDebug.log("mpv", "created transparent OSD SurfaceView parent=%s index=%d", parent.getClass().getSimpleName(), overlayIndex);
    }

    private void removeOsdSurfaceView() {
        if (osdSurfaceHolder != null) {
            try {
                osdSurfaceHolder.removeCallback(osdSurfaceCallback);
            } catch (Throwable ignored) {
            }
        }
        if (osdSurfaceView != null && osdSurfaceView.getParent() instanceof ViewGroup parent) {
            try {
                parent.removeView(osdSurfaceView);
            } catch (Throwable ignored) {
            }
        }
        osdSurfaceHolder = null;
        osdSurfaceView = null;
        osdSurface = null;
        osdSurfaceWidth = 0;
        osdSurfaceHeight = 0;
        appliedAndroidOsdSurfaceSize = null;
    }

    private void syncOsdSurfaceRequirementFromMpv() {
        if (!initialized) return;
        boolean subtitlesVisible = booleanProperty("sub-visibility", true);
        String primarySelection = propertyStringOrInt("sid");
        String secondarySelection = propertyStringOrInt("secondary-sid");
        if (!MpvOsdSurfacePolicy.needsCurrentTrackQuery(
                subtitlesVisible, primarySelection, secondarySelection)) {
            setOsdSurfaceRequested(false);
            return;
        }
        String primaryCurrent = isDisabledTrackChoice(primarySelection)
                ? "" : currentTrackId(C.TRACK_TYPE_TEXT);
        String secondaryCurrent = isDisabledTrackChoice(secondarySelection)
                ? "" : propertyStringOrInt("current-tracks/sub2/id");
        boolean requested = requiresOsdSurface()
                && MpvOsdSurfacePolicy.requiresSurface(
                subtitlesVisible, primaryCurrent, primarySelection,
                secondaryCurrent, secondarySelection);
        setOsdSurfaceRequested(requested);
    }

    private void setOsdSurfaceRequested(boolean requested) {
        if (requested) osdSurfaceUsedForCurrentMedia = true;
        requested = MpvOsdSurfacePolicy.shouldKeepSurface(
                requested, osdSurfaceUsedForCurrentMedia);
        requested = requested && requiresOsdSurface();
        if (osdSurfaceRequested != requested) {
            osdSurfaceRequested = requested;
            String primary = initialized ? propertyStringOrInt("sid") : "pending";
            String secondary = initialized ? propertyStringOrInt("secondary-sid") : "pending";
            SpiderDebug.log("mpv", "OSD surface requested=%s sid=%s secondarySid=%s visible=%s",
                    requested, primary, secondary,
                    initialized && booleanProperty("sub-visibility", true));
        }
        reconcileOsdSurface();
    }

    private void reconcileOsdSurface() {
        if (osdSurfaceRequested) createOsdSurfaceView();
        if (!initialized || !surfaceTeardownPolicy.shouldBindSurface()) return;
        if (pendingOsdSurfaceRequestId != 0) return;

        Surface target = osdSurface;
        boolean targetValid = target != null && target.isValid();
        if (osdSurfaceRequested) {
            if (!surfaceAttached) return;
            if (!targetValid) {
                if (osdSurfaceAttached) enqueueOsdSurfaceUpdate(false, null);
                return;
            }
            if (osdSurfaceAttached && attachedOsdSurface == target) return;
            if (osdSurfaceAttached) {
                enqueueOsdSurfaceUpdate(false, null);
            } else {
                enqueueOsdSurfaceUpdate(true, target);
            }
            return;
        }

        if (osdSurfaceAttached) {
            enqueueOsdSurfaceUpdate(false, null);
        } else {
            removeOsdSurfaceView();
        }
    }

    private void enqueueOsdSurfaceUpdate(boolean attach, @Nullable Surface target) {
        if (pendingOsdSurfaceRequestId != 0) return;
        if (attach && (target == null || !target.isValid())) return;
        long requestId = NATIVE_REQUEST_IDS.getAndIncrement();
        pendingOsdSurfaceRequestId = requestId;
        pendingOsdSurfaceAttach = attach;
        pendingOsdSurface = target;
        try {
            int result = mpvEnqueueOsdSurface(requestId, attach ? target : null);
            if (result < MPVLib.MpvError.MPV_ERROR_SUCCESS) {
                pendingOsdSurfaceRequestId = 0;
                pendingOsdSurfaceAttach = false;
                pendingOsdSurface = null;
                SpiderDebug.log("mpv", "OSD surface queue failed attach=%s error=%d", attach, result);
                return;
            }
            SpiderDebug.log("mpv", "OSD surface update queued request=%d attach=%s surface=%s",
                    requestId, attach, target);
        } catch (Throwable e) {
            pendingOsdSurfaceRequestId = 0;
            pendingOsdSurfaceAttach = false;
            pendingOsdSurface = null;
            SpiderDebug.log("mpv", "OSD surface queue failed attach=%s error=%s", attach, e.getMessage());
        }
    }

    private void handleOsdSurfaceReply(long requestId, int error) {
        if (requestId != pendingOsdSurfaceRequestId) return;
        boolean attach = pendingOsdSurfaceAttach;
        Surface requestedSurface = pendingOsdSurface;
        pendingOsdSurfaceRequestId = 0;
        pendingOsdSurfaceAttach = false;
        pendingOsdSurface = null;
        if (error >= MPVLib.MpvError.MPV_ERROR_SUCCESS) {
            osdSurfaceAttached = attach;
            attachedOsdSurface = attach ? requestedSurface : null;
            SpiderDebug.log("mpv", "OSD surface update applied request=%d attach=%s surface=%s",
                    requestId, attach, requestedSurface);
        } else {
            String message = "OSD surface update failed request=" + requestId + " attach=" + attach + " error=" + error;
            Log.e(TAG, message);
            rememberLog(message);
            markFailureSignal(message);
        }
        reconcileOsdSurface();
        if (error >= MPVLib.MpvError.MPV_ERROR_SUCCESS) {
            resumePendingOsdLoad();
        }
    }

    private void bindVideoOutput() {
        if (!initialized || !surfaceTeardownPolicy.shouldBindSurface()
                || surface == null || !surface.isValid()) return;
        try {
            boolean sameVideoSurface = surfaceAttached && attachedSurface == surface;
            String targetVo = videoOutputVo();
            if (sameVideoSurface) {
                // Re-read holder size on fast-path resize — surfaceChanged may not fire after fullscreen exit.
                if (surfaceHolder != null) updateSurfaceSize(surfaceHolder);
                applyAndroidSurfaceSize();
                applyAndroidOsdSurfaceSize();
                applySurfaceFrameRate();
                if (!TextUtils.equals(attachedVo, targetVo)) {
                    if (enqueueMpvCommand("set", "vo", targetVo)) attachedVo = targetVo;
                }
                Log.d(SIZE_TAG, "mpv resize attached surface cached=" + surfaceWidth + "x" + surfaceHeight + " vo=" + targetVo);
                SpiderDebug.log("mpv", "surface resized surface=%s size=%dx%d vo=%s", surface, surfaceWidth, surfaceHeight, targetVo);
                reconcileOsdSurface();
                return;
            }
            if (surfaceAttached || osdSurfaceAttached) detachMpvSurface();
            mpvAttachSurface(surface);
            surfaceAttached = true;
            attachedSurface = surface;
            applyAndroidSurfaceSize();
            applyAndroidOsdSurfaceSize();
            applySurfaceFrameRate();
            // Opening force-window while idle creates a disposable 960x540 VO
            // before the real MediaCodec format is known, causing an extra reconfig.
            if (enqueueMpvCommand("set", "vo", targetVo)) attachedVo = targetVo;
            Log.d(SIZE_TAG, "mpv bind surface valid=" + surface.isValid() + " cached=" + surfaceWidth + "x" + surfaceHeight + " vo=" + targetVo);
            SpiderDebug.log("mpv", "surface attached video=%s osd=%s size=%dx%d vo=%s", surface, osdSurface, surfaceWidth, surfaceHeight, targetVo);
            reconcileOsdSurface();
        } catch (Throwable e) {
            fail(mpvError(ERROR_VIDEO_OUTPUT_FAILED, e.getMessage(), e), PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED);
        }
    }

    private void clearVideoOutput() {
        clearSurfaceFrameRate();
        resetSurfaceFrameRateRequest();
        detachSurfaceHolder();
        detachMpvSurface();
        removeOsdSurfaceView();
        releaseOwnedSurface();
        surface = null;
        surfaceWidth = 0;
        surfaceHeight = 0;
        osdSurfaceWidth = 0;
        osdSurfaceHeight = 0;
        resetAppliedSurfaceSizes();
    }

    private void detachMpvSurface() {
        detachMpvSurface(true);
    }

    private void detachMpvSurface(boolean resetVideoOutput) {
        if (!initialized) return;
        if (!surfaceAttached && !osdSurfaceAttached
                && pendingOsdSurfaceRequestId == 0) return;
        if (!surfaceTeardownPolicy.shouldDetachSurface()) {
            clearMpvSurfaceAttachmentState();
            return;
        }
        try {
            boolean detachDirectVideoFirst = surfaceAttached
                    && "mediacodec_embed".equals(videoOutputVo());
            // Clearing wid first lets direct output tear down MediaCodec before
            // Android releases the Surface. Resetting vo first can reconfigure
            // the decoder against an already released Surface.
            if (detachDirectVideoFirst) mpvDetachSurface();
            if (resetVideoOutput) {
                enqueueMpvCommand("set", "vo", "null");
                enqueueMpvCommand("set", "force-window", "no");
            }
            if (osdSurfaceAttached || pendingOsdSurfaceRequestId != 0) {
                mpvDetachOsdSurface();
            }
            if (surfaceAttached && !detachDirectVideoFirst) mpvDetachSurface();
        } catch (Throwable ignored) {
        }
        clearMpvSurfaceAttachmentState();
    }

    private void clearMpvSurfaceAttachmentState() {
        surfaceAttached = false;
        osdSurfaceAttached = false;
        pendingOsdSurfaceRequestId = 0;
        pendingOsdSurfaceAttach = false;
        pendingOsdSurface = null;
        attachedSurface = null;
        attachedOsdSurface = null;
        attachedVo = null;
    }

    private void detachSurfaceHolder() {
        if (surfaceHolder == null) return;
        try {
            surfaceHolder.removeCallback(surfaceCallback);
        } catch (Throwable ignored) {
        }
        surfaceHolder = null;
    }

    private void updateSurfaceSize(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        surfaceWidth = view.getWidth();
        surfaceHeight = view.getHeight();
        Log.d(SIZE_TAG, "mpv updateSurfaceSize view=" + surfaceOutputName(view) + " size=" + surfaceWidth + "x" + surfaceHeight);
    }

    private void updateSurfaceSize(SurfaceHolder holder) {
        if (holder == null) return;
        Rect frame = holder.getSurfaceFrame();
        if (frame == null || frame.width() <= 0 || frame.height() <= 0) return;
        surfaceWidth = frame.width();
        surfaceHeight = frame.height();
        Log.d(SIZE_TAG, "mpv updateSurfaceSize holder frame=" + frame.width() + "x" + frame.height());
    }

    private void updateSurfaceSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        surfaceWidth = width;
        surfaceHeight = height;
        Log.d(SIZE_TAG, "mpv updateSurfaceSize changed=" + surfaceWidth + "x" + surfaceHeight);
    }

    private void updateOsdSurfaceSize(SurfaceHolder holder) {
        if (holder == null) return;
        Rect frame = holder.getSurfaceFrame();
        if (frame == null) return;
        updateOsdSurfaceSize(frame.width(), frame.height());
    }

    private void updateOsdSurfaceSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        osdSurfaceWidth = width;
        osdSurfaceHeight = height;
        Log.d(SIZE_TAG, "mpv updateOsdSurfaceSize changed="
                + osdSurfaceWidth + "x" + osdSurfaceHeight);
    }

    private void applyAndroidSurfaceSize() {
        String targetVo = videoOutputVo();
        if (!MpvSurfaceSizePolicy.usesAndroidSurfaceSize(targetVo)) return;
        String value = MpvSurfaceSizePolicy.sizeValue(surfaceWidth, surfaceHeight);
        if (value == null || TextUtils.equals(appliedAndroidSurfaceSize, value)) return;
        if (!enqueueMpvCommand("set", "android-surface-size", value)) return;
        appliedAndroidSurfaceSize = value;
        Log.d(SIZE_TAG, "mpv android-surface-size queued=" + value + " vo=" + targetVo);
    }

    private void applyAndroidOsdSurfaceSize() {
        boolean validSurface = osdSurface != null && osdSurface.isValid();
        if (!MpvSurfaceSizePolicy.shouldApplyOsdSize(
                osdSurfaceRequested, validSurface, osdSurfaceWidth, osdSurfaceHeight)) return;
        String value = MpvSurfaceSizePolicy.sizeValue(osdSurfaceWidth, osdSurfaceHeight);
        if (TextUtils.equals(appliedAndroidOsdSurfaceSize, value)) return;
        if (!enqueueMpvCommand("set", "android-osd-surface-size", value)) return;
        appliedAndroidOsdSurfaceSize = value;
        Log.d(SIZE_TAG, "mpv android-osd-surface-size queued=" + value);
    }

    private void resetAppliedSurfaceSizes() {
        appliedAndroidSurfaceSize = null;
        appliedAndroidOsdSurfaceSize = null;
    }

    private void applySurfaceFrameRate() {
        Surface target = surface;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || target == null || !target.isValid()) return;
        float rate = MpvPerformanceSetting.getFrameRateMode() == MpvPerformanceSetting.FRAME_RATE_SEAMLESS ? cachedContentFrameRate : 0f;
        if (rate < 0) rate = 0f;
        int compatibility = rate > 0f ? Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE : Surface.FRAME_RATE_COMPATIBILITY_DEFAULT;
        requestSurfaceFrameRate(target, rate, compatibility, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
    }

    private void requestSurfaceFrameRate(Surface target, float rate, int compatibility, int strategy) {
        if (target == lastFrameRateSurface
                && Math.abs(rate - lastRequestedFrameRate) <= FRAME_RATE_REQUEST_EPSILON
                && compatibility == lastFrameRateCompatibility
                && strategy == lastFrameRateStrategy) return;
        try {
            target.setFrameRate(rate, compatibility, strategy);
            lastFrameRateSurface = target;
            lastRequestedFrameRate = rate;
            lastFrameRateCompatibility = compatibility;
            lastFrameRateStrategy = strategy;
            SpiderDebug.log("mpv", "surface frame rate request=%.3f mode=%s", rate, MpvPerformanceSetting.getFrameRateText());
        } catch (Throwable e) {
            SpiderDebug.log("mpv", "surface frame rate request failed rate=%.3f error=%s", rate, e.getMessage());
        }
    }

    private void clearSurfaceFrameRate() {
        Surface target = surface;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && target != null && target.isValid() && target == lastFrameRateSurface) {
            requestSurfaceFrameRate(target, 0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        }
    }

    private void resetSurfaceFrameRateRequest() {
        lastFrameRateSurface = null;
        lastRequestedFrameRate = Float.NaN;
        lastFrameRateCompatibility = -1;
        lastFrameRateStrategy = -1;
    }

    private String surfaceOutputName(Object output) {
        if (output == null) return "null";
        return output.getClass().getSimpleName();
    }

    private String surfaceFrame(SurfaceHolder holder) {
        if (holder == null || holder.getSurfaceFrame() == null) return "null";
        Rect frame = holder.getSurfaceFrame();
        return frame.width() + "x" + frame.height();
    }

    private void releaseOwnedSurface() {
        if (ownsSurface && surface != null) surface.release();
        ownsSurface = false;
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surface = holder.getSurface();
            updateSurfaceSize(holder);
            Log.d(SIZE_TAG, "mpv surfaceCreated frame=" + surfaceFrame(holder) + " valid=" + (surface != null && surface.isValid()));
            bindVideoOutput();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surface = holder.getSurface();
            updateSurfaceSize(width, height);
            Log.d(SIZE_TAG, "mpv surfaceChanged format=" + format + " size=" + width + "x" + height + " frame=" + surfaceFrame(holder));
            bindVideoOutput();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(SIZE_TAG, "mpv surfaceDestroyed frame=" + surfaceFrame(holder));
            clearSurfaceFrameRate();
            resetSurfaceFrameRateRequest();
            surface = null;
            // Android normally destroys the OSD Surface first. Detach video
            // before OSD and keep the selected VO for transient window loss,
            // otherwise MPV may reopen MediaCodec on the released Surface.
            detachMpvSurface(false);
        }
    };

    private final SurfaceHolder.Callback osdSurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            osdSurface = holder.getSurface();
            updateOsdSurfaceSize(holder);
            Log.d(SIZE_TAG, "mpv OSD surfaceCreated frame=" + surfaceFrame(holder)
                    + " valid=" + (osdSurface != null && osdSurface.isValid()));
            applyAndroidOsdSurfaceSize();
            reconcileOsdSurface();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            osdSurface = holder.getSurface();
            updateOsdSurfaceSize(width, height);
            applyAndroidOsdSurfaceSize();
            Log.d(SIZE_TAG, "mpv OSD surfaceChanged format=" + format + " size=" + width + "x" + height);
            reconcileOsdSurface();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(SIZE_TAG, "mpv OSD surfaceDestroyed frame=" + surfaceFrame(holder));
            osdSurface = null;
            osdSurfaceWidth = 0;
            osdSurfaceHeight = 0;
            appliedAndroidOsdSurfaceSize = null;
            if (MpvOsdSurfacePolicy.shouldDeferDestroyedSurfaceDetach(
                    osdSurfaceRequested, surfaceAttached)) {
                SpiderDebug.log("mpv", "defer OSD detach until video Surface loss");
                return;
            }
            reconcileOsdSurface();
        }
    };

    private void stopInternal(boolean resetState) {
        pendingOsdLoadGeneration = C.INDEX_UNSET;
        initialTrackSelectionGateActive = false;
        mainHandler.removeCallbacks(initialTrackSelectionGateTimeoutRunnable);
        restorePreloadCacheOverlay();
        clearCoalescedPropertyEvents();
        cancelScheduledTrackRefresh();
        cancelScheduledChapterRefresh();
        mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
        mediaReplacementCoordinator.cancelDeferredPrepare();
        stopMpv(true);
        clearSurfaceFrameRate();
        closeContentFds();
        endSeekBuffering("stop");
        loading = false;
        fileLoaded = false;
        fileLoadedAtElapsedRealtimeMs = 0;
        loadStarted = false;
        playbackRestarted = false;
        loadStartRetryCount = 0;
        eofReached = false;
        preferAacApplied = false;
        directAudioApplied = false;
        audioTrackManuallySelected = false;
        cachedPositionMs = 0;
        cachedDurationMs = C.TIME_UNSET;
        resetVideoMetadataCache();
        resetCacheState();
        propertyCache.clear();
        currentTracks = Tracks.EMPTY;
        selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
        cachedSelectedHlsBitrate = 0;
        currentChapters = List.of();
        videoSize = VideoSize.UNKNOWN;
        playerError = null;
        initialSeekPositionMs = C.TIME_UNSET;
        loadStartPositionMs = C.TIME_UNSET;
        seekPositionState.clear();
        idleActive = false;
        currentPlayableUri = null;
        resourceClassification = null;
        closeIsoSession();
        currentLikelyHls = false;
        currentLikelyDash = false;
        currentChapter = C.INDEX_UNSET;
        resetFailureSignals();
        hlsProxy.clear();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        if (resetState) playbackState = Player.STATE_IDLE;
        stopStateRefresh();
        invalidateState();
    }

    private void stopMpv(boolean markStopping) {
        if (!initialized) return;
        boolean previousStopping = stopping;
        if (markStopping) stopping = true;
        if (!enqueueMpvCommand("stop")) {
            stopping = previousStopping;
        }
    }

    private void resumeMediaReplacementAfterStopTimeout() {
        if (released || mediaItem == null || !mediaReplacementCoordinator.resumeAfterTimeout()) return;
        stopping = false;
        SpiderDebug.log("mpv", "media replace resume generation=%d reason=stop-timeout player=%s", mediaReplacementCoordinator.generation(), identity(this));
        openCurrent(mediaReplacementCoordinator.generation());
    }

    private void resumeMediaReplacement(String reason) {
        mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
        long generation = mediaReplacementCoordinator.generation();
        SpiderDebug.log("mpv", "media replace resume generation=%d reason=%s player=%s", generation, reason, identity(this));
        mainHandler.post(() -> openCurrent(generation));
    }

    private void releaseNativeContext(String reason) {
        synchronized (NATIVE_CONTEXT_LOCK) {
            releaseNativeContextLocked(reason);
        }
    }

    private void releaseNativeContextLocked(String reason) {
        if (!initialized) return;
        boolean ownsNativeContext = nativeContextOwner == this;
        try {
            if (ownsNativeContext && (surfaceAttached || osdSurfaceAttached)) detachMpvSurface();
        } catch (Throwable ignored) {
        }
        try {
            MPVLib.removeObserver(this);
            MPVLib.removeLogObserver(this);
            if (ownsNativeContext) mpvDestroyCreatedContext();
        } catch (Throwable ignored) {
        } finally {
            if (ownsNativeContext) nativeContextOwner = null;
            mainHandler.removeCallbacks(mediaReplacementStopTimeoutRunnable);
            mediaReplacementCoordinator.reset();
            initialized = false;
            observedPropertyNames.clear();
            propertyCache.clear();
            autoHlsBitrateState.onNativeContextReleased();
            surfaceAttached = false;
            osdSurfaceAttached = false;
            attachedSurface = null;
            attachedOsdSurface = null;
            attachedVo = null;
            effectiveVo = null;
            osdSurfaceWidth = 0;
            osdSurfaceHeight = 0;
            resetAppliedSurfaceSizes();
            stopping = false;
            loadStarted = false;
            loadStartRetryCount = 0;
        }
        SpiderDebug.log("mpv", "context released reason=%s owner=%s player=%s", reason, ownsNativeContext, identity(this));
    }

    private static String identity(MpvPlayer player) {
        return Integer.toHexString(System.identityHashCode(player));
    }

    private void seekMpv(long positionMs) {
        try {
            mpvCommand(new String[]{"seek", String.format(Locale.US, "%.3f", positionMs / SECONDS_TO_MS), "absolute+exact"});
        } catch (Throwable e) {
            fail(e, PlaybackException.ERROR_CODE_UNSPECIFIED);
        }
    }

    /**
     * Opens the buffering window that spans a seek.
     *
     * <p>The window closes on an mpv-side signal: the MPV_EVENT_PLAYBACK_RESTART event or the
     * {@code paused-for-cache} observer reaching READY. If a seek is swallowed natively neither
     * arrives, so the window also carries a deadline — one that closes the window but only
     * overrides the state once it has confirmed mpv is not still waiting on its cache, since a
     * genuine stall must keep reporting BUFFERING. The latch in {@link MpvSeekPositionState}
     * cannot serve as that guard: it only clamps the reported position, never the state.
     */
    private void beginSeekBuffering(String source) {
        seekBufferingActive = true;
        loading = true;
        mainHandler.removeCallbacks(seekBufferingTimeoutRunnable);
        mainHandler.postDelayed(seekBufferingTimeoutRunnable, SEEK_BUFFERING_TIMEOUT_MS);
        startStateRefresh();
        if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "seek-buffering action=enter source=%s targetMs=%d", source, cachedPositionMs);
    }

    private void endSeekBuffering(String reason) {
        if (!seekBufferingActive) return;
        seekBufferingActive = false;
        mainHandler.removeCallbacks(seekBufferingTimeoutRunnable);
        if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "seek-buffering action=exit reason=%s positionMs=%d", reason, cachedPositionMs);
    }

    private void timeOutSeekBuffering() {
        if (released || !seekBufferingActive) return;
        endSeekBuffering("timeout");
        if (stopping || playbackState != Player.STATE_BUFFERING || !fileLoaded) return;
        // Neither exit signal arrived, so decide from mpv rather than from the clock. Ask for
        // paused-for-cache directly: the observed copy cannot be trusted here, since the very
        // situation this covers is a seek whose observer callbacks never came. This is a rare
        // fallback path, so one synchronous read is affordable.
        //
        // Still waiting on the cache means the BUFFERING is honest. Publishing READY would hide
        // the progress indicator over a frozen frame — the exact bug the seek window fixes, just
        // 15 s later — and would also cancel the stall watchdog, since checkBufferingStall()
        // disarms on READY. Leave the state alone and let that watchdog own the stall.
        boolean pausedForCache = nativeBooleanProperty("paused-for-cache", true);
        if (pausedForCache) {
            PlaybackTrace.log("mpv", playbackTraceId, "seek-buffering action=hold reason=paused-for-cache positionMs=%d", cachedPositionMs);
            return;
        }
        playbackState = Player.STATE_READY;
        loading = false;
        PlaybackTrace.log("mpv", playbackTraceId, "seek-buffering action=release reason=timeout positionMs=%d", cachedPositionMs);
        invalidateState();
        startStateRefresh();
    }

    private void loadCurrentUri() {
        String startOption = "";
        if (initialSeekPositionMs != C.TIME_UNSET && initialSeekPositionMs > 0) {
            loadStartPositionMs = initialSeekPositionMs;
            startOption = "start=" + String.format(Locale.US, "%.3f",
                    initialSeekPositionMs / SECONDS_TO_MS);
        }
        if (currentLikelyHls) {
            mpvCommand(new String[]{"loadfile", currentPlayableUri, "replace", "-1",
                    appendLoadOption(HLS_LOAD_OPTIONS, startOption)});
        } else if (currentLikelyDash) {
            mpvCommand(new String[]{"loadfile", currentPlayableUri, "replace", "-1",
                    appendLoadOption(DASH_LOAD_OPTIONS, startOption)});
        } else if (!startOption.isEmpty()) {
            mpvCommand(new String[]{"loadfile", currentPlayableUri, "replace", "-1", startOption});
        } else {
            mpvCommand(new String[]{"loadfile", currentPlayableUri, "replace"});
        }
        if (shouldCollectDebugDetails() && !startOption.isEmpty()) {
            PlaybackTrace.log("mpv", playbackTraceId,
                    "load initial position=%d option=%s", loadStartPositionMs, startOption);
        }
    }

    private String appendLoadOption(String options, String option) {
        return TextUtils.isEmpty(option) ? options : options + "," + option;
    }

    private void scheduleLoadStartRetry() {
        mainHandler.removeCallbacks(loadStartRetryRunnable);
        mainHandler.postDelayed(loadStartRetryRunnable, LOAD_START_RETRY_DELAY_MS);
    }

    private void retryLoadIfNotStarted() {
        if (released || loadStarted || fileLoaded || playerError != null) return;
        if (playbackState != Player.STATE_BUFFERING || TextUtils.isEmpty(currentPlayableUri)) return;
        if (loadStartRetryCount >= MAX_LOAD_START_RETRIES) return;
        loadStartRetryCount++;
        if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId, "load retry attempt=%d source=%s idle=%s", loadStartRetryCount, MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri), booleanProperty("idle-active", idleActive));
        try {
            loadCurrentUri();
            scheduleLoadStartRetry();
        } catch (Throwable e) {
            fail(classifyLoadError(e, e.getMessage()), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
    }

    private void updateVideoSize(String reason) {
        SizeCandidate candidate = videoSizeCandidate();
        logVideoSizeCandidates(reason, candidate);
        if (candidate == null || candidate.width <= 0 || candidate.height <= 0) return;
        if (videoSize.width == candidate.width && videoSize.height == candidate.height) return;
        videoSize = new VideoSize(candidate.width, candidate.height);
        BiConsumer<Integer, Integer> listener = videoSizeProbeListener;
        if (listener != null) {
            int width = candidate.width;
            int height = candidate.height;
            mainHandler.postAtFrontOfQueue(() -> {
                if (!released && listener == videoSizeProbeListener) listener.accept(width, height);
            });
        }
        Log.d(SIZE_TAG, "mpv videoSize=" + candidate.width + "x" + candidate.height + " source=" + candidate.source + " reason=" + reason + " surface=" + surfaceWidth + "x" + surfaceHeight);
    }

    @Nullable
    private SizeCandidate videoSizeCandidate() {
        SizeCandidate candidate = candidateFromProperties("current-tracks/video/demux-w", "current-tracks/video/demux-h", "current-track");
        if (candidate != null) return candidate;
        candidate = candidateFromSelectedVideoTrack();
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("video-params/dw", "video-params/dh", "video-params-display");
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("video-params/w", "video-params/h", "video-params");
        if (candidate != null) return candidate;
        candidate = candidateFromProperties("width", "height", "width-height");
        if (candidate != null) return candidate;
        if (!canUseVideoOutSizeFallback()) return null;
        candidate = candidateFromProperties("video-out-params/dw", "video-out-params/dh", "video-out-display");
        if (candidate != null) return candidate;
        return candidateFromProperties("video-out-params/w", "video-out-params/h", "video-out");
    }

    private boolean canUseVideoOutSizeFallback() {
        return videoSize.width <= 0 && videoSize.height <= 0 && (fileLoaded || playbackRestarted);
    }

    @Nullable
    private SizeCandidate candidateFromProperties(String widthProperty, String heightProperty, String source) {
        int width = cachedVideoIntProperty(widthProperty, 0);
        int height = cachedVideoIntProperty(heightProperty, 0);
        return width > 0 && height > 0 ? new SizeCandidate(width, height, source) : null;
    }

    @Nullable
    private SizeCandidate candidateFromSelectedVideoTrack() {
        SizeCandidate firstVideo = null;
        for (Tracks.Group group : currentTracks.getGroups()) {
            if (group.length <= 0) continue;
            Format format = group.getTrackFormat(0);
            if (!MimeTypes.isVideo(format.sampleMimeType)) continue;
            int width = format.width;
            int height = format.height;
            if (width <= 0 || height <= 0) continue;
            SizeCandidate candidate = new SizeCandidate(width, height, "tracks-cache");
            if (firstVideo == null) firstVideo = candidate;
            if (group.isTrackSelected(0)) return candidate;
        }
        return firstVideo;
    }

    private void logVideoSizeCandidates(String reason, @Nullable SizeCandidate candidate) {
        if (!shouldCollectDebugDetails()) return;
        String text = "mpv size candidates reason=" + reason
                + " selected=" + candidateText(candidate)
                + " stable=" + size("current", "current-tracks/video/demux-w", "current-tracks/video/demux-h")
                + " track=" + selectedTrackSizeText()
                + " paramsDisplay=" + size("vp-d", "video-params/dw", "video-params/dh")
                + " params=" + size("vp", "video-params/w", "video-params/h")
                + " legacy=" + size("wh", "width", "height")
                + " outDisplay=" + size("vo-d", "video-out-params/dw", "video-out-params/dh")
                + " out=" + size("vo", "video-out-params/w", "video-out-params/h")
                + " fileLoaded=" + fileLoaded
                + " restarted=" + playbackRestarted
                + " reconfig=" + videoReconfigCount
                + " surface=" + surfaceWidth + "x" + surfaceHeight;
        if (text.equals(lastVideoSizeCandidateLog)) return;
        lastVideoSizeCandidateLog = text;
        Log.d(SIZE_TAG, text);
        PlaybackTrace.log("mpv", playbackTraceId, "%s", text);
    }

    private String candidateText(@Nullable SizeCandidate candidate) {
        return candidate == null ? "none" : candidate.source + ":" + candidate.width + "x" + candidate.height;
    }

    private String size(String label, String widthProperty, String heightProperty) {
        return label + ":" + cachedVideoIntProperty(widthProperty, 0) + "x"
                + cachedVideoIntProperty(heightProperty, 0);
    }

    private String selectedTrackSizeText() {
        return candidateText(candidateFromSelectedVideoTrack());
    }

    private record SizeCandidate(int width, int height, String source) {
    }

    private void startStateRefresh() {
        mainHandler.removeCallbacks(stateRefreshRunnable);
        mainHandler.postDelayed(stateRefreshRunnable, STATE_REFRESH_INTERVAL_MS);
    }

    private void stopStateRefresh() {
        mainHandler.removeCallbacks(stateRefreshRunnable);
    }

    private void startMainThreadWatchdog() {
        if (!SpiderDebug.isEnabled() || mainThreadWatchdogRunning) return;
        HandlerThread thread = new HandlerThread("mpv-main-watchdog", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        mainThreadWatchdogThread = thread;
        mainThreadWatchdogHandler = handler;
        mainThreadWatchdogRunning = true;
        mainThreadHeartbeatPending.set(false);
        mainThreadHeartbeatPostedAtMs = 0;
        lastMainThreadStallLogAtMs = 0;
        handler.post(mainThreadWatchdogRunnable);
        SpiderDebug.log("mpv-anr", "watchdog start player=%s", identity(this));
    }

    private void stopMainThreadWatchdog() {
        mainThreadWatchdogRunning = false;
        Handler handler = mainThreadWatchdogHandler;
        if (handler != null) handler.removeCallbacks(mainThreadWatchdogRunnable);
        mainHandler.removeCallbacks(mainThreadHeartbeatRunnable);
        mainThreadHeartbeatPending.set(false);
        mainThreadHeartbeatPostedAtMs = 0;
        activeMpvNativeCallStartedAtMs = 0;
        activeMpvNativeCallKind = "";
        activeMpvNativeCallTarget = "";
        HandlerThread thread = mainThreadWatchdogThread;
        mainThreadWatchdogHandler = null;
        mainThreadWatchdogThread = null;
        if (thread != null) thread.quitSafely();
    }

    private void runMainThreadWatchdog() {
        if (!mainThreadWatchdogRunning) return;
        if (!SpiderDebug.isEnabled()) {
            stopMainThreadWatchdog();
            return;
        }
        long nowMs = SystemClock.elapsedRealtime();
        long postedAtMs = mainThreadHeartbeatPostedAtMs;
        if (mainThreadHeartbeatPending.get() && postedAtMs > 0) {
            long stalledMs = nowMs - postedAtMs;
            if (stalledMs >= MAIN_THREAD_STALL_THRESHOLD_MS
                    && (lastMainThreadStallLogAtMs == 0
                    || nowMs - lastMainThreadStallLogAtMs >= MAIN_THREAD_STALL_LOG_INTERVAL_MS)) {
                lastMainThreadStallLogAtMs = nowMs;
                logMainThreadStall(stalledMs);
            }
        } else if (mainThreadHeartbeatPending.compareAndSet(false, true)) {
            mainThreadHeartbeatPostedAtMs = nowMs;
            if (!mainHandler.post(mainThreadHeartbeatRunnable)) {
                mainThreadHeartbeatPending.set(false);
                mainThreadHeartbeatPostedAtMs = 0;
            }
        }
        Handler handler = mainThreadWatchdogHandler;
        if (mainThreadWatchdogRunning && handler != null) {
            handler.postDelayed(mainThreadWatchdogRunnable, MAIN_THREAD_WATCHDOG_INTERVAL_MS);
        }
    }

    private void logMainThreadStall(long stalledMs) {
        long nativeStartedAtMs = activeMpvNativeCallStartedAtMs;
        String nativeCall = TextUtils.isEmpty(activeMpvNativeCallKind)
                ? "none"
                : activeMpvNativeCallKind + (TextUtils.isEmpty(activeMpvNativeCallTarget)
                ? "" : ":" + activeMpvNativeCallTarget);
        long nativeElapsedMs = nativeStartedAtMs > 0
                ? Math.max(0, SystemClock.elapsedRealtime() - nativeStartedAtMs) : 0;
        String stack = formatMainThreadStack(Looper.getMainLooper().getThread().getStackTrace());
        String message = "main stalled=" + stalledMs + "ms native=" + nativeCall
                + " nativeElapsed=" + nativeElapsedMs + "ms stack=" + stack;
        Log.w(TAG, "ANR_DIAGNOSTIC " + message);
        SpiderDebug.log("mpv-anr", "%s", message);
    }

    private String formatMainThreadStack(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) return "empty";
        StringBuilder builder = new StringBuilder();
        int count = Math.min(stack.length, 32);
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(" <- ");
            builder.append(stack[i].toString());
        }
        return builder.toString();
    }

    private long beginMpvNativeCall(String kind, String target) {
        if (!mainThreadWatchdogRunning || Looper.myLooper() != Looper.getMainLooper()) return -1;
        long startedAtMs = SystemClock.elapsedRealtime();
        activeMpvNativeCallKind = kind;
        activeMpvNativeCallTarget = target == null ? "" : target;
        activeMpvNativeCallStartedAtMs = startedAtMs;
        return startedAtMs;
    }

    private void endMpvNativeCall(long startedAtMs, String kind, String target) {
        if (startedAtMs < 0) return;
        long elapsedMs = Math.max(0, SystemClock.elapsedRealtime() - startedAtMs);
        if (activeMpvNativeCallStartedAtMs == startedAtMs) {
            activeMpvNativeCallStartedAtMs = 0;
            activeMpvNativeCallKind = "";
            activeMpvNativeCallTarget = "";
        }
        if (elapsedMs >= SLOW_MPV_NATIVE_CALL_THRESHOLD_MS) {
            String operation = kind + (TextUtils.isEmpty(target) ? "" : ":" + target);
            Log.w(TAG, "SLOW_MPV_CALL operation=" + operation + " elapsed=" + elapsedMs + "ms");
            SpiderDebug.log("mpv-anr", "slow-native operation=%s elapsed=%dms", operation, elapsedMs);
        }
    }

    private void refreshPlaybackState() {
        if (released || mediaItem == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null) return;
        updatePreloadCacheOverlay();
        if (currentLikelyHls) requestHlsPreload(cachedPositionMs);
        invalidateState();
        startStateRefresh();
    }

    private void applyPausedForCache(boolean pausedForCache) {
        loading = pausedForCache;
        playbackState = MpvPlaybackState.resolveAfterCachePoll(playbackState, fileLoaded, playbackRestarted, stopping, pausedForCache);
    }

    private void refreshCacheState() {
        if (!initialized) return;
        long nowMs = SystemClock.elapsedRealtime();
        boolean speedActive = cachedCacheSpeedBytesPerSecond > 0
                && isFreshCacheSpeedSample(nowMs);
        boolean cacheActive = loading || !cachedCacheIdle || speedActive;
        boolean timelineQueried = cacheObserverState.shouldQueryPausedTimeline(
                fileLoaded, !playWhenReady, config.cache(), nowMs);
        if (timelineQueried) {
            refreshCacheTimeline();
            cacheObserverState.onPausedTimelineQuery(nowMs);
        }
        if (!cacheObserverState.shouldQueryFallback(
                fileLoaded, cacheActive, isPlayingInternal(), nowMs)) return;
        if (!timelineQueried && (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.DURATION, cacheActive, nowMs)
                || cacheObserverState.needsFallback(MpvCacheObserverState.Metric.END, cacheActive, nowMs))) {
            refreshCacheTimeline();
        }
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.READER_POSITION, cacheActive, nowMs)) cachedCacheReaderPositionMs = Math.max(0, doublePropertyMs("demuxer-cache-state/reader-pts", cachedCacheReaderPositionMs));
        if (cacheObserverState.needsFallback(
                MpvCacheObserverState.Metric.SPEED, cacheActive, nowMs)) {
            Long speed = nullableLongProperty(
                    "demuxer-cache-state/raw-input-rate");
            if (speed == null) speed = nullableLongProperty("cache-speed");
            if (speed != null) {
                cachedCacheSpeedBytesPerSecond = Math.max(0, speed);
                cachedCacheSpeedSampleAtMs = nowMs;
            }
        }
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.FORWARD_BYTES, cacheActive, nowMs)) cachedCacheForwardBytes = Math.max(0, nativeLongProperty("demuxer-cache-state/fw-bytes", cachedCacheForwardBytes));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.TOTAL_BYTES, cacheActive, nowMs)) cachedCacheTotalBytes = Math.max(0, nativeLongProperty("demuxer-cache-state/total-bytes", cachedCacheTotalBytes));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.FILE_BYTES, cacheActive, nowMs)) cachedCacheFileBytes = Math.max(0, nativeLongProperty("demuxer-cache-state/file-cache-bytes", cachedCacheFileBytes));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.BUFFERING_STATE, cacheActive, nowMs)) cachedCacheBufferingState = Math.max(0, Math.min(100, (int) nativeLongProperty("cache-buffering-state", cachedCacheBufferingState)));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.IDLE, cacheActive, nowMs)) cachedCacheIdle = nativeBooleanProperty("demuxer-cache-state/idle", nativeBooleanProperty("demuxer-cache-idle", cachedCacheIdle));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.UNDERRUN, cacheActive, nowMs)) recordCacheUnderrun(nativeBooleanProperty("demuxer-cache-state/underrun", cachedCacheUnderrun));
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.BOF, cacheActive, nowMs)) cachedCacheBof = nativeBooleanProperty("demuxer-cache-state/bof-cached", cachedCacheBof);
        if (cacheObserverState.needsFallback(MpvCacheObserverState.Metric.EOF, cacheActive, nowMs)) cachedCacheEof = nativeBooleanProperty("demuxer-cache-state/eof-cached", cachedCacheEof);
        cacheObserverState.onFallbackQuery(nowMs);
    }

    private void refreshCacheTimeline() {
        cachedCacheDurationMs = Math.max(0, doublePropertyMs(
                "demuxer-cache-state/cache-duration",
                doublePropertyMs("demuxer-cache-duration", cachedCacheDurationMs)));
        cachedCacheEndMs = Math.max(0, doublePropertyMs(
                "demuxer-cache-state/cache-end",
                doublePropertyMs("demuxer-cache-time", cachedCacheEndMs)));
    }

    private void validateEarlyEndFile() {
        if (released || stopping || fileLoaded || eofReached || playerError != null || playbackState != Player.STATE_BUFFERING) return;
        if (idleActive) {
            fail(classifyLoadError(null, "idle-active=true"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        } else {
            startStateRefresh();
        }
    }

    private boolean isFailedLoadedMedia() {
        if (!fileLoaded) return false;
        if (sawNoAvData || sawInvalidData || sawPngVideo || sawNetworkError || sawDecodeError || sawVideoOutputError || sawDrmError) return true;
        if (recentLogsContain("no audio or video data played", "invalid data found when processing input", "video: png", "could not open codec", "failed to initialize decoder", "video output failed")) return true;
        return playbackRestarted && videoSize.width <= 0 && videoSize.height <= 0 && positionMs() <= 0 && durationMs() == C.TIME_UNSET;
    }

    private String failedLoadedMediaMessage() {
        if (currentLikelyHls && (sawNoAvData
                || sawInvalidData
                || sawPngVideo
                || recentLogsContain("no audio or video data played", "invalid data found when processing input", "video: png")
                || playbackRestarted && videoSize.width <= 0 && videoSize.height <= 0 && positionMs() <= 0)) {
            return ERROR_HLS_PLAYBACK_FAILED + detailSuffix("hls input failed");
        }
        if (sawDrmError) return ERROR_DRM_UNSUPPORTED + detailSuffix("drm/encrypted media");
        if (sawVideoOutputError) return ERROR_VIDEO_OUTPUT_FAILED + detailSuffix("video output failed");
        if (sawNetworkError) return ERROR_NETWORK_FAILED + detailSuffix("network/io failure");
        if (sawNoAvData || recentLogsContain("no audio or video data played")) return ERROR_NO_AV_DATA + detailSuffix("no audio or video data played");
        if (sawInvalidData || sawPngVideo || recentLogsContain("invalid data found when processing input", "video: png")) return ERROR_INVALID_MEDIA_DATA + detailSuffix("invalid media data");
        return ERROR_DECODE_FAILED + detailSuffix("no playable audio/video output");
    }

    private IOException classifyLoadError(@Nullable Throwable cause, @Nullable String detail) {
        String code;
        if (sawDrmError) code = ERROR_DRM_UNSUPPORTED;
        else if (sawVideoOutputError) code = ERROR_VIDEO_OUTPUT_FAILED;
        else if (sawNetworkError) code = ERROR_NETWORK_FAILED;
        else if (sawInvalidData || sawPngVideo) code = ERROR_INVALID_MEDIA_DATA;
        else if (sawNoAvData) code = ERROR_NO_AV_DATA;
        else code = ERROR_LOAD_FAILED;
        return cause == null ? mpvError(code, detail) : mpvError(code, detail, cause);
    }

    private IOException nativeEndFileError(int reason, int error, @Nullable String errorText) {
        return mpvError(nativeEndFileErrorCode(error), nativeEndFileDetail(reason, error, errorText));
    }

    private String nativeEndFileErrorCode(int error) {
        if (sawDrmError) return ERROR_DRM_UNSUPPORTED;
        if (sawVideoOutputError || error == MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED) return ERROR_VIDEO_OUTPUT_FAILED;
        if (sawNetworkError) return ERROR_NETWORK_FAILED;
        if (currentLikelyHls && (sawNoAvData
                || sawInvalidData
                || sawPngVideo
                || error == MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY
                || error == MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT
                || error == MPVLib.MpvError.MPV_ERROR_UNSUPPORTED)) {
            return ERROR_HLS_PLAYBACK_FAILED;
        }
        if (sawNoAvData || error == MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY) return ERROR_NO_AV_DATA;
        if (sawInvalidData
                || sawPngVideo
                || error == MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT
                || error == MPVLib.MpvError.MPV_ERROR_UNSUPPORTED) {
            return ERROR_INVALID_MEDIA_DATA;
        }
        if (sawDecodeError) return ERROR_DECODE_FAILED;
        return error == MPVLib.MpvError.MPV_ERROR_LOADING_FAILED ? ERROR_LOAD_FAILED : ERROR_DECODE_FAILED;
    }

    private int nativeEndFilePlaybackExceptionCode(int error) {
        if (error == MPVLib.MpvError.MPV_ERROR_LOADING_FAILED) return PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
        if (error == MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED) return PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED;
        return PlaybackException.ERROR_CODE_DECODING_FAILED;
    }

    private String nativeEndFileDetail(int reason, int error, @Nullable String errorText) {
        StringBuilder builder = new StringBuilder();
        builder.append("native end-file reason=").append(endFileReasonName(reason)).append('(').append(reason).append(')');
        builder.append(" error=").append(mpvErrorName(error)).append('(').append(error).append(')');
        if (!TextUtils.isEmpty(errorText)) builder.append(' ').append(errorText);
        return builder.toString();
    }

    private String endFileReasonName(int reason) {
        return switch (reason) {
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF -> "eof";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_STOP -> "stop";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_QUIT -> "quit";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR -> "error";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_REDIRECT -> "redirect";
            case MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN -> "unknown";
            default -> "unknown";
        };
    }

    private String mpvErrorName(int error) {
        return switch (error) {
            case MPVLib.MpvError.MPV_ERROR_SUCCESS -> "success";
            case MPVLib.MpvError.MPV_ERROR_LOADING_FAILED -> "loading_failed";
            case MPVLib.MpvError.MPV_ERROR_AO_INIT_FAILED -> "ao_init_failed";
            case MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED -> "vo_init_failed";
            case MPVLib.MpvError.MPV_ERROR_NOTHING_TO_PLAY -> "nothing_to_play";
            case MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT -> "unknown_format";
            case MPVLib.MpvError.MPV_ERROR_UNSUPPORTED -> "unsupported";
            case MPVLib.MpvError.MPV_ERROR_GENERIC -> "generic";
            default -> "unknown";
        };
    }

    private IOException mpvError(String code, @Nullable String detail) {
        return new IOException(code + detailSuffix(detail));
    }

    private IOException mpvError(String code, @Nullable String detail, Throwable cause) {
        return new IOException(code + detailSuffix(detail), cause);
    }

    private String detailSuffix(@Nullable String detail) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(detail)) builder.append(": ").append(detail);
        String recent = recentLogSuffix();
        if (!TextUtils.isEmpty(recent)) builder.append(recent);
        return builder.toString();
    }

    private void rememberLog(String line) {
        if (recentLogs.size() >= RECENT_LOG_LIMIT) recentLogs.remove(0);
        recentLogs.add(line);
    }

    private void markFailureSignal(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.US);
        if (lower.contains("no audio or video data played")) sawNoAvData = true;
        if (lower.contains("invalid data found when processing input")) sawInvalidData = true;
        if (lower.contains("video: png")) sawPngVideo = true;
        if (isNetworkFailureLog(lower)) sawNetworkError = true;
        if (isDecodeFailureLog(lower)) sawDecodeError = true;
        if (isVideoOutputFailureLog(lower)) sawVideoOutputError = true;
        if (isDrmFailureLog(lower)) sawDrmError = true;
        if (sawNoAvData || sawInvalidData || sawPngVideo || sawNetworkError || sawDecodeError || sawVideoOutputError || sawDrmError || lower.contains("failed") || lower.contains("error")) lastFailureLog = line;
    }

    private void resetCacheState() {
        cacheObserverState.reset();
        cachedCacheDurationMs = 0;
        cachedCacheEndMs = 0;
        cachedCacheReaderPositionMs = 0;
        cachedCacheForwardBytes = 0;
        cachedCacheTotalBytes = 0;
        cachedCacheFileBytes = 0;
        cachedCacheSpeedBytesPerSecond = 0;
        cachedCacheSpeedSampleAtMs = -1;
        cachedCacheBufferingState = 0;
        cachedCacheIdle = false;
        cachedCacheUnderrun = false;
        cachedCacheUnderrunCount = 0;
        cachedCacheBof = false;
        cachedCacheEof = false;
    }

    private void resetCacheTimelineForSeek(long targetPositionMs) {
        long target = Math.max(0, targetPositionMs);
        cachedCacheDurationMs = 0;
        cachedCacheEndMs = target;
        cachedCacheReaderPositionMs = target;
        cachedCacheForwardBytes = 0;
        cachedCacheTotalBytes = 0;
        cachedCacheFileBytes = 0;
        cachedCacheBufferingState = 0;
        cachedCacheBof = false;
        cachedCacheEof = false;
    }

    private long resolvePreloadCacheCapacity(File directory) {
        if (!PreloadSetting.isPreload(PlayerSetting.MPV)) return 0;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return 0;
            long existingBytes = FileUtil.getDirectorySize(directory);
            FileUtil.StorageSpace storage = FileUtil.getStorageSpace(directory);
            DiskCacheCapacityPolicy.Decision decision =
                    DiskCacheCapacityPolicy.resolve(
                            storage.available(),
                            PreloadSetting.getPreloadSizeBytes(PlayerSetting.MPV),
                            existingBytes,
                            storage.availableBytes(),
                            storage.totalBytes());
            long capacityBytes = decision.state()
                    == DiskCacheCapacityPolicy.State.UNAVAILABLE
                    ? 0 : decision.newWriteBudgetBytes();
            SpiderDebug.log("mpv-preload-cache",
                    "action=capacity state=%s configuredBytes=%d existingBytes=%d availableBytes=%d reserveBytes=%d capacityBytes=%d",
                    decision.state(), decision.configuredCapacityBytes(),
                    decision.existingCacheBytes(), decision.availableStorageBytes(),
                    decision.reserveBytes(), capacityBytes);
            return Math.max(0, capacityBytes);
        } catch (Throwable error) {
            SpiderDebug.log("mpv-preload-cache",
                    "action=capacity state=unavailable error=%s",
                    error.getClass().getSimpleName());
            return 0;
        }
    }

    private void applyPreloadDiskCacheMode() {
        PlaybackResourceClassifier.Classification classification = resourceClassification;
        PlaybackAutoContext.Protocol protocol = classification == null
                ? PlaybackAutoContext.Protocol.UNKNOWN : classification.protocol();
        PlaybackAutoContext.PathKind playerPath = classification == null
                ? PlaybackAutoContext.PathKind.UNKNOWN : classification.playerPath();
        boolean progressive = !currentLikelyHls && !currentLikelyDash
                && MpvPreloadCachePolicy.supportsForwardPreload(
                protocol, playerPath);
        boolean enable = config.performanceOptionsPriority()
                && PreloadSetting.isPreload(PlayerSetting.MPV)
                && preloadCacheCapacityBytes > 0
                && progressive;
        boolean directoryApplied = !enable || setRuntimeStringChecked(
                "demuxer-cache-dir", preloadCacheDir.getAbsolutePath());
        boolean applied = setRuntimeStringChecked(
                "cache-on-disk", enable && directoryApplied ? "yes" : "no");
        PlaybackTrace.log("mpv-preload-cache", playbackTraceId,
                "action=disk-mode requested=%s result=%s capacityBytes=%d protocol=%s path=%s hls=%s dash=%s",
                enable, applied ? "applied" : "failed",
                preloadCacheCapacityBytes, protocol.label(), playerPath.label(),
                currentLikelyHls, currentLikelyDash);
    }

    private void recordCacheUnderrun(boolean underrun) {
        if (underrun && !cachedCacheUnderrun
                && cachedCacheUnderrunCount < Long.MAX_VALUE) {
            cachedCacheUnderrunCount++;
        }
        cachedCacheUnderrun = underrun;
    }

    private void applyCacheTimePolicy() {
        PlaybackResourceClassifier.Classification classification = resourceClassification;
        PlaybackAutoContext.Protocol protocol = currentLikelyHls
                ? PlaybackAutoContext.Protocol.HLS
                : currentLikelyDash
                ? PlaybackAutoContext.Protocol.DASH
                : classification == null
                ? PlaybackAutoContext.Protocol.UNKNOWN
                : classification.protocol();
        PlaybackAutoContext.PathKind playerPath = classification == null
                ? PlaybackAutoContext.PathKind.UNKNOWN : classification.playerPath();
        MpvCacheTimePolicy.Decision decision = MpvCacheTimePolicy.resolve(
                config.performanceOptionsPriority(),
                config.automaticCacheTime(),
                config.cache(),
                config.cacheSeconds(),
                config.demuxerReadaheadSeconds(),
                config.rebufferMs(),
                protocol,
                playerPath);
        cacheTimeState.select(decision);
        boolean applied = decision.runtimeManaged();
        for (Map.Entry<String, String> entry : decision.runtimeOptions().entrySet()) {
            boolean accepted = setRuntimeStringChecked(entry.getKey(), entry.getValue());
            if (accepted) cacheTimeState.recordAccepted(entry.getKey(), entry.getValue());
            applied &= accepted;
        }
        MpvCacheTimeState.Snapshot snapshot = cacheTimeState.snapshot();
        PlaybackTrace.log("mpv-cache-time", playbackTraceId,
                "master=%s reason=%s protocol=%s path=%s cache=%d readahead=%d hysteresis=%d rebufferWait=%d observed=%d result=%s",
                decision.master().label(), decision.reason().label(),
                decision.protocol().label(), decision.playerPath().label(),
                snapshot.cacheSeconds(), snapshot.readaheadSeconds(),
                snapshot.hysteresisSeconds(), decision.rebufferWaitSeconds(),
                snapshot.observedOptions(),
                !decision.runtimeManaged() ? "mpv-conf" : applied ? "applied" : "failed");
    }

    private void updatePreloadCacheOverlay() {
        int baselineSeconds = preloadCacheOverlayApplied
                ? preloadCacheBaselineSeconds : cacheTimeState.snapshot().cacheSeconds();
        long baselineBytes = preloadCacheOverlayApplied
                ? preloadCacheBaselineBytes : effectiveDemuxerMaxBytes;
        PlaybackResourceClassifier.Classification classification = resourceClassification;
        PlaybackAutoContext.Protocol protocol = classification == null
                ? PlaybackAutoContext.Protocol.UNKNOWN : classification.protocol();
        PlaybackAutoContext.StreamKind streamKind = classification == null
                ? PlaybackAutoContext.StreamKind.UNKNOWN : classification.streamKind();
        PlaybackAutoContext.PathKind playerPath = classification == null
                ? PlaybackAutoContext.PathKind.UNKNOWN : classification.playerPath();
        boolean pauseAllowed = PreloadPausePolicy.evaluate(
                playWhenReady,
                PreloadSetting.getPausePreloadPolicy(PlayerSetting.MPV),
                PlaybackSystemConditionMonitor.process().currentNetworkSnapshot()).allowed();
        long capacityBytes = config.automaticCacheTime()
                ? baselineBytes : preloadCacheCapacityBytes;
        MpvPreloadCachePolicy.Decision decision =
                MpvPreloadCachePolicy.resolve(
                        new MpvPreloadCachePolicy.Request(
                                !playWhenReady,
                                PreloadSetting.isPreload(PlayerSetting.MPV),
                                pauseAllowed,
                                config.performanceOptionsPriority(),
                                config.cache(),
                                protocol,
                                streamKind,
                                playerPath,
                                baselineSeconds,
                                baselineBytes,
                                PreloadSetting.getPreloadAheadSeconds(PlayerSetting.MPV),
                                capacityBytes,
                                cachedPositionMs,
                                cachedDurationMs));
        if (!decision.apply()) {
            restorePreloadCacheOverlay();
            return;
        }
        if (!initialized || !fileLoaded) return;
        if (!preloadCacheOverlayApplied) {
            preloadCacheBaselineSeconds = baselineSeconds;
            preloadCacheBaselineBytes = baselineBytes;
            preloadCacheOverlayApplied = true;
        }
        int targetSeconds = Math.max(
                preloadCacheTargetSeconds, decision.targetSeconds());
        long targetBytes = Math.max(
                preloadCacheTargetBytes, decision.targetBytes());
        boolean secondsCurrent = cacheTimeState.snapshot().cacheSeconds() == targetSeconds;
        boolean bytesCurrent = effectiveDemuxerMaxBytes == targetBytes;
        if (secondsCurrent && bytesCurrent) {
            preloadCacheTargetSeconds = targetSeconds;
            preloadCacheTargetBytes = targetBytes;
            return;
        }
        boolean bytesAccepted = bytesCurrent
                || setRuntimeStringChecked("demuxer-max-bytes", String.valueOf(targetBytes));
        if (bytesAccepted) effectiveDemuxerMaxBytes = targetBytes;
        boolean secondsAccepted = secondsCurrent
                || setRuntimeStringChecked("cache-secs", String.valueOf(targetSeconds));
        if (secondsAccepted) {
            cacheTimeState.recordAccepted("cache-secs", String.valueOf(targetSeconds));
        }
        if (!bytesAccepted || !secondsAccepted) {
            rollbackPreloadCacheOverlay(secondsAccepted && !secondsCurrent,
                    bytesAccepted && !bytesCurrent);
            PlaybackTrace.log("mpv-preload-cache", playbackTraceId,
                    "action=extend result=failed mode=%s protocol=%s stream=%s path=%s",
                    playWhenReady ? "playing" : "paused",
                    protocol.label(), streamKind.label(), playerPath.label());
            return;
        }
        preloadCacheTargetSeconds = targetSeconds;
        preloadCacheTargetBytes = targetBytes;
        PlaybackTrace.log("mpv-preload-cache", playbackTraceId,
                "action=extend result=applied mode=%s baselineSeconds=%d targetSeconds=%d baselineBytes=%d targetBytes=%d disk=%s protocol=%s stream=%s path=%s",
                playWhenReady ? "playing" : "paused",
                preloadCacheBaselineSeconds, targetSeconds,
                preloadCacheBaselineBytes, targetBytes,
                preloadCacheCapacityBytes > 0,
                protocol.label(), streamKind.label(), playerPath.label());
    }

    private void rollbackPreloadCacheOverlay(
            boolean restoreSeconds,
            boolean restoreBytes) {
        boolean secondsRestored = !restoreSeconds
                || setRuntimeStringChecked("cache-secs",
                String.valueOf(preloadCacheBaselineSeconds));
        if (secondsRestored && restoreSeconds) {
            cacheTimeState.recordAccepted("cache-secs",
                    String.valueOf(preloadCacheBaselineSeconds));
        }
        boolean bytesRestored = !restoreBytes
                || setRuntimeStringChecked("demuxer-max-bytes",
                String.valueOf(preloadCacheBaselineBytes));
        if (bytesRestored && restoreBytes) {
            effectiveDemuxerMaxBytes = preloadCacheBaselineBytes;
        }
        if (secondsRestored && bytesRestored) clearPreloadCacheOverlay();
    }

    private void restorePreloadCacheOverlay() {
        if (!preloadCacheOverlayApplied) return;
        if (!initialized) {
            clearPreloadCacheOverlay();
            return;
        }
        boolean secondsRestored = setRuntimeStringChecked(
                "cache-secs", String.valueOf(preloadCacheBaselineSeconds));
        if (secondsRestored) {
            cacheTimeState.recordAccepted("cache-secs",
                    String.valueOf(preloadCacheBaselineSeconds));
        }
        boolean bytesRestored = setRuntimeStringChecked(
                "demuxer-max-bytes", String.valueOf(preloadCacheBaselineBytes));
        if (bytesRestored) effectiveDemuxerMaxBytes = preloadCacheBaselineBytes;
        if (!secondsRestored || !bytesRestored) return;
        PlaybackTrace.log("mpv-preload-cache", playbackTraceId,
                "action=restore baselineSeconds=%d baselineBytes=%d previousTargetSeconds=%d previousTargetBytes=%d",
                preloadCacheBaselineSeconds, preloadCacheBaselineBytes,
                preloadCacheTargetSeconds, preloadCacheTargetBytes);
        clearPreloadCacheOverlay();
    }

    private void clearPreloadCacheOverlay() {
        preloadCacheOverlayApplied = false;
        preloadCacheBaselineSeconds = 0;
        preloadCacheBaselineBytes = 0;
        preloadCacheTargetSeconds = 0;
        preloadCacheTargetBytes = 0;
    }

    private boolean isNetworkFailureLog(String lower) {
        if (MpvRenderLogPolicy.isRenderPipelineTimeout(lower)) return false;
        return lower.contains("http error")
                || lower.contains("server returned")
                || lower.contains("connection timed out")
                || lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("network is unreachable")
                || lower.contains("name resolution")
                || lower.contains("cannot resolve")
                || lower.contains("timed out")
                || ((lower.contains("tls") || lower.contains("ssl")) && (lower.contains("failed") || lower.contains("error") || lower.contains("certificate")))
                || lower.contains("error reading")
                || lower.contains("failed to open http")
                || lower.contains("failed to open https");
    }

    private boolean isDecodeFailureLog(String lower) {
        return lower.contains("could not open codec")
                || lower.contains("failed to initialize decoder")
                || lower.contains("failed to init decoder")
                || lower.contains("decoder init failed")
                || lower.contains("error while decoding")
                || lower.contains("error decoding")
                || lower.contains("decoding failed")
                || lower.contains("no decoders available")
                || lower.contains("hardware decoding failed");
    }

    private boolean isVideoOutputFailureLog(String lower) {
        if (MpvRenderLogPolicy.isRecoveredRenderFallback(lower)) return false;
        return lower.contains("video output failed")
                || lower.contains("failed to create android surface")
                || lower.contains("could not create egl")
                || (lower.contains("egl") && lower.contains("failed"))
                || (lower.contains("vo/") && lower.contains("failed"));
    }

    private boolean isDrmFailureLog(String lower) {
        return (lower.contains("widevine") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed") || lower.contains("error")))
                || (lower.contains("encrypted") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed")))
                || (lower.contains("drm") && (lower.contains("unsupported") || lower.contains("not supported") || lower.contains("failed") || lower.contains("error")));
    }

    private boolean shouldDebugLogMpvLine(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.US);
        return lower.contains("error")
                || lower.contains("failed")
                || lower.contains("invalid")
                || lower.contains("no audio")
                || lower.contains("video:")
                || lower.contains("audio:")
                || lower.contains("found 'hls'")
                || lower.contains("opening")
                || lower.contains("lavf")
                || lower.contains("demux")
                || lower.contains("codec")
                || lower.contains("track")
                || lower.contains("aimagereader")
                || lower.contains("vulkan")
                || lower.contains("fence")
                || lower.contains("bufferqueue")
                || lower.contains("surface pool")
                || lower.contains("conversion pool")
                || lower.contains("mediacodec output");
    }

    private void resetFailureSignals() {
        sawNoAvData = false;
        sawInvalidData = false;
        sawPngVideo = false;
        sawNetworkError = false;
        sawDecodeError = false;
        sawVideoOutputError = false;
        sawDrmError = false;
        lastFailureLog = null;
        lastEndFileReason = MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_UNKNOWN;
        lastEndFileError = MPVLib.MpvError.MPV_ERROR_SUCCESS;
        lastEndFileErrorText = null;
    }

    private boolean recentLogsContain(String... needles) {
        for (String log : recentLogs) {
            String lower = log == null ? "" : log.toLowerCase(Locale.US);
            for (String needle : needles) {
                if (lower.contains(needle)) return true;
            }
        }
        return false;
    }

    private long positionMs() {
        return cachedPositionMs;
    }

    private long durationMs() {
        return cachedDurationMs > 0 ? cachedDurationMs : C.TIME_UNSET;
    }

    private long stabilizedPositionMs(long observedPositionMs) {
        long targetPositionMs = seekPositionState.targetPositionMs();
        long resolvedPositionMs = seekPositionState.resolve(
                observedPositionMs, SystemClock.elapsedRealtime());
        if (targetPositionMs != MpvSeekPositionState.NO_TARGET
                && !seekPositionState.hasTarget()) {
            PlaybackTrace.log("mpv", playbackTraceId,
                    "seek-latch action=release targetMs=%d observedMs=%d",
                    targetPositionMs, observedPositionMs);
        }
        return resolvedPositionMs;
    }

    private long bufferedPositionMs(long position, long duration) {
        PlaybackResourceClassifier.Classification classification = resourceClassification;
        boolean local = classification != null
                && classification.playerPath() == PlaybackAutoContext.PathKind.LOCAL;
        return MpvBufferedPositionPolicy.resolve(
                position,
                duration,
                cachedCacheDurationMs,
                cachedCacheEndMs,
                local,
                !TextUtils.isEmpty(currentIsoUri));
    }

    private boolean isPlayingInternal() {
        return playbackState == Player.STATE_READY && playWhenReady && !loading;
    }

    private void scheduleTrackRefresh(String reason) {
        if (released) return;
        long nowMs = SystemClock.elapsedRealtime();
        if (!trackRefreshScheduled) {
            trackRefreshFirstScheduledAtMs = nowMs;
            trackRefreshCoalescedEvents = 0;
        }
        trackRefreshScheduled = true;
        trackRefreshCoalescedEvents++;
        trackRefreshLastReason = reason;
        boolean prioritizePlaybackRestart = config.deferStartupTrackRefresh()
                && "event=playback-restart".equals(reason);
        if (trackRefreshPrioritized && !prioritizePlaybackRestart) return;
        if (prioritizePlaybackRestart) trackRefreshPrioritized = true;
        mainHandler.removeCallbacks(trackRefreshRunnable);
        long delayMs = prioritizePlaybackRestart
                ? POST_RESTART_TRACK_REFRESH_DELAY_MS
                : MpvTrackRefreshPolicy.delayMs(
                fileLoaded, fileLoadedAtElapsedRealtimeMs, nowMs);
        mainHandler.postDelayed(trackRefreshRunnable, delayMs);
    }

    private void runScheduledTrackRefresh() {
        int coalescedEvents = trackRefreshCoalescedEvents;
        long spanMs = Math.max(0, SystemClock.elapsedRealtime()
                - trackRefreshFirstScheduledAtMs);
        String lastReason = trackRefreshLastReason;
        trackRefreshScheduled = false;
        resetTrackRefreshDiagnostics();
        if (released) return;
        if (shouldCollectDebugDetails()) {
            PlaybackTrace.log("mpv", playbackTraceId,
                    "track refresh run coalesced=%d span=%dms last=%s startup=%s",
                    coalescedEvents, spanMs, lastReason,
                    MpvTrackRefreshPolicy.isStartupWindow(fileLoaded,
                            fileLoadedAtElapsedRealtimeMs,
                            SystemClock.elapsedRealtime()));
        }
        if (config.deferStartupTrackRefresh() && !playbackRestarted) {
            if (shouldCollectDebugDetails()) PlaybackTrace.log("mpv", playbackTraceId,
                    "track refresh deferred until playback restart");
            return;
        }
        refreshTracks();
        if (config.deferStartupTrackRefresh()) refreshChapters();
        invalidateState();
    }

    private void cancelScheduledTrackRefresh() {
        trackRefreshScheduled = false;
        mainHandler.removeCallbacks(trackRefreshRunnable);
        resetTrackRefreshDiagnostics();
    }

    private void resetTrackRefreshDiagnostics() {
        trackRefreshPrioritized = false;
        trackRefreshCoalescedEvents = 0;
        trackRefreshFirstScheduledAtMs = 0;
        trackRefreshLastReason = null;
    }

    private void scheduleChapterRefresh() {
        if (released || chapterRefreshScheduled) return;
        chapterRefreshScheduled = true;
        mainHandler.postDelayed(chapterRefreshRunnable, TRACK_REFRESH_DEBOUNCE_MS);
    }

    private void runScheduledChapterRefresh() {
        chapterRefreshScheduled = false;
        if (!released) refreshChapters();
    }

    private void cancelScheduledChapterRefresh() {
        chapterRefreshScheduled = false;
        mainHandler.removeCallbacks(chapterRefreshRunnable);
    }

    private void refreshTracks() {
        if (trackRefreshScheduled) cancelScheduledTrackRefresh();
        if (config.deferStartupTrackRefresh() && !playbackRestarted) return;
        if (!initialized) {
            currentTracks = Tracks.EMPTY;
            selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            cachedSelectedHlsBitrate = 0;
            return;
        }
        syncOsdSurfaceRequirementFromMpv();
        int count = Math.max(0, intProperty("track-list/count", 0));
        if (count <= 0) {
            currentTracks = Tracks.EMPTY;
            selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            cachedSelectedHlsBitrate = 0;
            return;
        }
        if (!isoTrackListDumped && !TextUtils.isEmpty(currentIsoUri)) {
            isoTrackListDumped = true;
            MPVLib.dumpTrackList();
        }
        List<TrackInfo> infos = new ArrayList<>();
        int audioIndex = 0;
        int subtitleIndex = 0;
        for (int i = 0; i < count; i++) {
            String mpvType = stringProperty("track-list/" + i + "/type", "");
            int typeIndex = "audio".equals(mpvType) ? audioIndex++ : "sub".equals(mpvType) ? subtitleIndex++ : C.INDEX_UNSET;
            TrackInfo info = readTrackInfo(i, typeIndex);
            if (info == null) continue;
            infos.add(info);
        }
        if (infos.isEmpty()) {
            currentTracks = Tracks.EMPTY;
            selectedVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            availableVideoTrackDiagnostics = VideoTrackDiagnostics.empty();
            cachedSelectedHlsBitrate = 0;
            return;
        }
        String selectedVideo = selectedTrackId(C.TRACK_TYPE_VIDEO);
        String selectedAudio = selectedTrackId(C.TRACK_TYPE_AUDIO);
        String selectedText = selectedTrackId(C.TRACK_TYPE_TEXT);
        TrackInfo selectedVideoInfo = findTrack(
                infos, C.TRACK_TYPE_VIDEO, selectedVideo);
        if (selectedVideoInfo == null && isAutoOrUnknownTrackChoice(selectedVideo)) {
            selectedVideoInfo = firstTrack(infos, C.TRACK_TYPE_VIDEO);
        }
        TrackInfo selectedAudioInfo = findTrack(
                infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        TrackInfo firstVideoInfo = firstTrack(infos, C.TRACK_TYPE_VIDEO);
        availableVideoTrackDiagnostics = firstVideoInfo == null
                ? VideoTrackDiagnostics.empty()
                : firstVideoInfo.toVideoTrackDiagnostics();
        selectedVideoTrackDiagnostics = selectedVideoInfo == null
                ? VideoTrackDiagnostics.empty()
                : selectedVideoInfo.toVideoTrackDiagnostics();
        cachedSelectedHlsBitrate = selectedVideoInfo != null
                && selectedVideoInfo.hlsBitrate > 0
                ? selectedVideoInfo.hlsBitrate
                : selectedAudioInfo != null && selectedAudioInfo.hlsBitrate > 0
                ? selectedAudioInfo.hlsBitrate : 0;
        boolean hasSelectedVideo = hasSelectedTrack(infos, C.TRACK_TYPE_VIDEO, selectedVideo);
        boolean hasSelectedAudio = hasSelectedTrack(infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        boolean hasSelectedText = hasSelectedTrack(infos, C.TRACK_TYPE_TEXT, selectedText);
        boolean autoVideoFallbackUsed = false;
        boolean autoAudioFallbackUsed = false;
        boolean autoTextFallbackUsed = false;
        List<Tracks.Group> groups = new ArrayList<>();
        for (TrackInfo info : infos) {
            boolean selected = isTrackSelected(info, trackIdForType(info.type, selectedVideo, selectedAudio, selectedText));
            if (!selected && info.type == C.TRACK_TYPE_VIDEO && !hasSelectedVideo && isAutoOrUnknownTrackChoice(selectedVideo) && !autoVideoFallbackUsed) {
                selected = true;
                autoVideoFallbackUsed = true;
            } else if (!selected && info.type == C.TRACK_TYPE_AUDIO && !hasSelectedAudio && isAutoOrUnknownTrackChoice(selectedAudio) && !autoAudioFallbackUsed) {
                selected = true;
                autoAudioFallbackUsed = true;
            } else if (!selected && info.type == C.TRACK_TYPE_TEXT && !hasSelectedText && isAutoTrackChoice(selectedText) && !autoTextFallbackUsed) {
                selected = true;
                autoTextFallbackUsed = true;
            }
            Format format = info.toFormat();
            TrackGroup mediaGroup = new TrackGroup("mpv:" + info.type + ":" + info.id, format);
            groups.add(new Tracks.Group(mediaGroup, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
        }
        currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
        if (!maybeSelectPreferredDirectAudio(infos, selectedAudio)) {
            maybeSelectPreferredAac(infos, selectedAudio);
        }
        logTrackSnapshot(infos, selectedVideo, selectedAudio, selectedText, currentTracks);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv", "tracks refreshed count=%d groups=%d", count, groups.size());
    }

    private boolean maybeSelectPreferredDirectAudio(List<TrackInfo> infos, String selectedAudio) {
        if (directAudioApplied || audioTrackManuallySelected || !initialized
                || !"mediacodec_embed".equals(videoOutputVo())) return false;
        TrackInfo selected = findTrack(infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        if (selected == null) return false;
        List<MpvDirectAudioPolicy.Candidate> candidates = new ArrayList<>();
        for (TrackInfo info : infos) {
            if (info.type != C.TRACK_TYPE_AUDIO) continue;
            candidates.add(new MpvDirectAudioPolicy.Candidate(
                    info.id, info.lang, info.codec, info.title, info.channels));
        }
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(
                candidates, selected.id, config.audioSpdif());
        directAudioApplied = true;
        preferAacApplied = true;
        TrackInfo target = selected;
        for (TrackInfo info : infos) {
            if (info.type == C.TRACK_TYPE_AUDIO
                    && TextUtils.equals(info.id, selection.id())) {
                target = info;
                break;
            }
        }
        if (selection.changed()) setMpvTrack(C.TRACK_TYPE_AUDIO, selection.id());
        SpiderDebug.log("mpv", "direct automatic audio selection changed=%s reason=%s previous=%s/%s/%dch/%s selected=%s/%s/%dch/%s",
                selection.changed(), selection.reason(), selected.id, selected.codec,
                selected.channels, selected.lang, target.id, target.codec,
                target.channels, target.lang);
        return true;
    }

    private void maybeSelectPreferredAac(List<TrackInfo> infos, String selectedAudio) {
        if (!PlayerSetting.isPreferAAC(PlayerSetting.MPV) || preferAacApplied
                || directAudioApplied || audioTrackManuallySelected || !initialized) return;
        TrackInfo selected = findTrack(infos, C.TRACK_TYPE_AUDIO, selectedAudio);
        if (selected != null && isAacTrack(selected)) {
            preferAacApplied = true;
            return;
        }
        TrackInfo preferred = findPreferredAacTrack(infos, selected);
        if (preferred == null || TextUtils.equals(preferred.id, selected == null ? "" : selected.id)) return;
        preferAacApplied = true;
        setMpvTrack(C.TRACK_TYPE_AUDIO, preferred.id);
        SpiderDebug.log("mpv", "prefer AAC audio track selected id=%s codec=%s previous=%s", preferred.id, preferred.codec, selected == null ? selectedAudio : selected.id);
    }

    @Nullable
    private TrackInfo findTrack(List<TrackInfo> infos, int type, String selectedId) {
        for (TrackInfo info : infos) if (info.type == type && isTrackSelected(info, selectedId)) return info;
        return null;
    }

    @Nullable
    private TrackInfo firstTrack(List<TrackInfo> infos, int type) {
        for (TrackInfo info : infos) if (info.type == type) return info;
        return null;
    }

    @Nullable
    private TrackInfo findPreferredAacTrack(List<TrackInfo> infos, @Nullable TrackInfo selected) {
        TrackInfo first = null;
        for (TrackInfo info : infos) {
            if (info.type != C.TRACK_TYPE_AUDIO || !isAacTrack(info)) continue;
            if (first == null) first = info;
            if (selected != null && !TextUtils.isEmpty(selected.lang) && TextUtils.equals(selected.lang, info.lang)) return info;
        }
        return first;
    }

    private boolean isAacTrack(TrackInfo info) {
        return info != null && sampleMimeType(info).equals(MimeTypes.AUDIO_AAC);
    }

    private void refreshChapters() {
        if (released || !initialized || shouldDeferStartupMetadataRefresh()) return;
        currentChapter = intProperty("chapter", currentChapter);
        List<MediaEdition> chapters = parseChapters(stringProperty("chapter-list", ""));
        if (chapters.isEmpty()) chapters = readChaptersFromProperties();
        updateCurrentChapters(chapters);
    }

    private void handleChapterListProperty(@Nullable Object value) {
        if (shouldDeferStartupMetadataRefresh()) return;
        List<MediaEdition> chapters = value instanceof String string ? parseChapters(string) : List.of();
        if (chapters.isEmpty()) {
            scheduleChapterRefresh();
            return;
        }
        updateCurrentChapters(chapters);
    }

    private boolean shouldDeferStartupMetadataRefresh() {
        return config.deferStartupTrackRefresh() && !playbackRestarted;
    }

    private void updateCurrentChapters(List<MediaEdition> chapters) {
        if (chapters == null) chapters = List.of();
        if (chapters.equals(currentChapters)) return;
        currentChapters = chapters;
        SpiderDebug.log("mpv", "chapters refreshed count=%d selected=%d", chapters.size(), currentChapter);
        invalidateState();
    }

    private List<MediaEdition> parseChapters(String json) {
        if (TextUtils.isEmpty(json)) return List.of();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) return List.of();
        try {
            JSONArray array = new JSONArray(trimmed);
            List<MediaEdition> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                items.add(MediaEdition.edition(i, secondsToUs(item.optDouble("time", 0)), chapterLabel(i, item.optString("title", null)), i == currentChapter));
            }
            return items;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private List<MediaEdition> readChaptersFromProperties() {
        int count = Math.max(0, intProperty("chapter-list/count", 0));
        if (count <= 0) return List.of();
        List<MediaEdition> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "chapter-list/" + i + "/";
            items.add(MediaEdition.edition(i, secondsToUs(doubleProperty(prefix + "time", 0)), chapterLabel(i, stringProperty(prefix + "title", null)), i == currentChapter));
        }
        return items;
    }

    private String chapterLabel(int index, @Nullable String title) {
        title = emptyToNull(title);
        return title == null ? "Chapter " + (index + 1) : title;
    }

    private long secondsToUs(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) return 0;
        return Math.round(seconds * 1_000_000.0);
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        if (TextUtils.isEmpty(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void logTrackSnapshot(List<TrackInfo> infos, String selectedVideo, String selectedAudio, String selectedText, Tracks tracks) {
        if (!shouldCollectDebugDetails()) return;
        String rawVid = propertyStringOrInt("vid");
        String rawAid = propertyStringOrInt("aid");
        String rawSid = propertyStringOrInt("sid");
        String rawSecondarySid = propertyStringOrInt("secondary-sid");
        StringBuilder builder = new StringBuilder();
        builder.append("tracks snapshot ");
        builder.append("vid=").append(selectedVideo).append(" aid=").append(selectedAudio).append(" sid=").append(selectedText);
        builder.append(" rawVid=").append(rawVid);
        builder.append(" rawAid=").append(rawAid);
        builder.append(" rawSid=").append(rawSid);
        builder.append(" secondarySid=").append(rawSecondarySid);
        builder.append(" currentVideo=").append(propertyStringOrInt("current-tracks/video/id"));
        builder.append(" currentAudio=").append(propertyStringOrInt("current-tracks/audio/id"));
        builder.append(" currentSub=").append(isDisabledTrackChoice(rawSid)
                ? rawSid : propertyStringOrInt("current-tracks/sub/id"));
        builder.append(" currentSub2=").append(isDisabledTrackChoice(rawSecondarySid)
                ? rawSecondarySid : propertyStringOrInt("current-tracks/sub2/id"));
        builder.append(" size=").append(videoSize.width).append("x").append(videoSize.height);
        builder.append(" width=").append(intProperty("width", C.LENGTH_UNSET));
        builder.append(" height=").append(intProperty("height", C.LENGTH_UNSET));
        builder.append(" fps=").append(videoFrameRate());
        builder.append(" color primaries=").append(stringProperty("video-params/primaries", ""));
        builder.append(" gamma=").append(stringProperty("video-params/gamma", ""));
        builder.append(" levels=").append(stringProperty("video-params/colorlevels", ""));
        builder.append(" matrix=").append(stringProperty("video-params/colormatrix", ""));
        for (int i = 0; i < infos.size(); i++) {
            TrackInfo info = infos.get(i);
            builder.append(" | track[").append(i).append("]");
            builder.append(" type=").append(trackTypeName(info.type));
            builder.append(" id=").append(info.id);
            builder.append(" demuxId=").append(info.demuxId);
            builder.append(" srcId=").append(info.srcId);
            builder.append(" rawSelected=").append(info.selected);
            builder.append(" finalSelected=").append(isTrackSelectedInSnapshot(tracks, info));
            builder.append(" title=").append(info.title);
            builder.append(" lang=").append(info.lang);
            builder.append(" codec=").append(info.codec);
            builder.append(" decoder=").append(info.decoder);
            builder.append(" doviProfile=").append(info.dolbyVisionProfile);
            builder.append(" doviLevel=").append(info.dolbyVisionLevel);
            Format format = info.toFormat();
            builder.append(" label=").append(format.label);
            builder.append(" formatLang=").append(format.language);
            builder.append(" size=").append(info.width).append("x").append(info.height);
            builder.append(" fps=").append(info.frameRate);
            builder.append(" sr=").append(info.sampleRate);
            builder.append(" ch=").append(info.channels);
            builder.append(" br=").append(info.bitrate);
            builder.append(" color=").append(info.colorInfo == null ? "" : info.colorInfo.toLogString());
        }
        String text = builder.toString();
        Log.d(TAG, text);
        PlaybackTrace.log("mpv", playbackTraceId, "%s", text);
    }

    private boolean isTrackSelectedInSnapshot(Tracks tracks, TrackInfo info) {
        if (tracks == null || tracks.isEmpty()) return false;
        String groupId = "mpv:" + info.type + ":" + info.id;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.length <= 0 || !group.getMediaTrackGroup().id.equals(groupId)) continue;
            return group.isTrackSelected(0);
        }
        return false;
    }

    private String trackTypeName(int type) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "video";
            case C.TRACK_TYPE_AUDIO -> "audio";
            case C.TRACK_TYPE_TEXT -> "text";
            default -> String.valueOf(type);
        };
    }

    private boolean hasSelectedTrack(List<TrackInfo> infos, int type, String selectedId) {
        for (TrackInfo info : infos) {
            if (info.type == type && isTrackSelected(info, selectedId)) return true;
        }
        return false;
    }

    private boolean isTrackSelected(TrackInfo info, String selectedId) {
        if (info.selected) return true;
        if (TextUtils.isEmpty(selectedId) || isAutoTrackChoice(selectedId) || isDisabledTrackChoice(selectedId)) return false;
        return selectedId.equals(info.id) || normalizeTrackId(selectedId).equals(normalizeTrackId(info.id));
    }

    private String selectedTrackId(int type) {
        String property = mpvTrackProperty(type);
        if (property == null) return "";
        String selected = propertyStringOrInt(property);
        if (isDisabledTrackChoice(selected)) return selected;
        String current = currentTrackId(type);
        return !TextUtils.isEmpty(current) ? current : selected;
    }

    private String currentTrackId(int type) {
        String property = switch (type) {
            case C.TRACK_TYPE_VIDEO -> "current-tracks/video/id";
            case C.TRACK_TYPE_AUDIO -> "current-tracks/audio/id";
            case C.TRACK_TYPE_TEXT -> "current-tracks/sub/id";
            default -> null;
        };
        return property == null ? "" : propertyStringOrInt(property);
    }

    private String secondarySubtitleTrackId() {
        String selected = propertyStringOrInt("secondary-sid");
        if (isDisabledTrackChoice(selected)) return selected;
        String current = propertyStringOrInt("current-tracks/sub2/id");
        if (!TextUtils.isEmpty(current)) return current;
        return selected;
    }

    private String propertyStringOrInt(String property) {
        String value = stringProperty(property, "");
        if (!TextUtils.isEmpty(value)) return value;
        int intValue = intProperty(property, Integer.MIN_VALUE);
        return intValue == Integer.MIN_VALUE ? "" : String.valueOf(intValue);
    }

    private String trackIdForType(int type, String selectedVideo, String selectedAudio, String selectedText) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> selectedVideo;
            case C.TRACK_TYPE_AUDIO -> selectedAudio;
            case C.TRACK_TYPE_TEXT -> selectedText;
            default -> "";
        };
    }

    private boolean isAutoTrackChoice(String value) {
        return "auto".equalsIgnoreCase(value);
    }

    private boolean isAutoOrUnknownTrackChoice(String value) {
        return TextUtils.isEmpty(value) || isAutoTrackChoice(value);
    }

    private boolean isDisabledTrackChoice(String value) {
        return "no".equalsIgnoreCase(value);
    }

    private String normalizeTrackId(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        while (normalized.startsWith("0") && normalized.length() > 1) normalized = normalized.substring(1);
        return normalized;
    }

    @Nullable
    private TrackInfo readTrackInfo(int index, int typeIndex) {
        String prefix = "track-list/" + index + "/";
        String mpvType = stringProperty(prefix + "type", "");
        int type = mediaTrackType(mpvType);
        if (type == C.TRACK_TYPE_UNKNOWN) return null;
        if (type == C.TRACK_TYPE_VIDEO && booleanProperty(prefix + "albumart", false)) return null;
        String id = stringProperty(prefix + "id", "");
        if (TextUtils.isEmpty(id)) id = String.valueOf(intProperty(prefix + "id", index + 1));
        String title = stringProperty(prefix + "title", "");
        String lang = stringProperty(prefix + "lang", "");
        int demuxId = intProperty(prefix + "demux-id", C.INDEX_UNSET);
        int srcId = intProperty(prefix + "src-id", C.INDEX_UNSET);
        if (TextUtils.isEmpty(lang) && typeIndex >= 0 && !TextUtils.isEmpty(currentIsoUri)) {
            int isoTrackType = type == C.TRACK_TYPE_AUDIO ? 1 : type == C.TRACK_TYPE_TEXT ? 2 : 0;
            if (isoTrackType != 0) {
                int isoTrackId = demuxId > 0 ? demuxId : srcId;
                lang = IsoSessionManager.getTrackLanguage(IsoSessionManager.parseId(currentIsoUri), isoTrackType, isoTrackId, typeIndex);
            }
        }
        String codec = stringProperty(prefix + "codec", "");
        String decoder = stringProperty(prefix + "decoder", "");
        int dolbyVisionProfile = intProperty(prefix + "dolby-vision-profile", C.INDEX_UNSET);
        int dolbyVisionLevel = intProperty(prefix + "dolby-vision-level", C.INDEX_UNSET);
        int sourceDolbyVisionProfile = intProperty(prefix + "source-dolby-vision-profile", C.INDEX_UNSET);
        int sourceDolbyVisionLevel = intProperty(prefix + "source-dolby-vision-level", C.INDEX_UNSET);
        boolean selected = booleanProperty(prefix + "selected", false);
        int width = intProperty(prefix + "demux-w", C.LENGTH_UNSET);
        int height = intProperty(prefix + "demux-h", C.LENGTH_UNSET);
        float frameRate = type == C.TRACK_TYPE_VIDEO ? videoFrameRate() : C.RATE_UNSET;
        if (type == C.TRACK_TYPE_VIDEO) {
            if (width <= 0) width = videoSize.width > 0 ? videoSize.width : intProperty("width", C.LENGTH_UNSET);
            if (height <= 0) height = videoSize.height > 0 ? videoSize.height : intProperty("height", C.LENGTH_UNSET);
        }
        int sampleRate = intProperty(prefix + "demux-samplerate", C.RATE_UNSET_INT);
        int channels = intProperty(prefix + "demux-channel-count", C.LENGTH_UNSET);
        int bitrate = intProperty(prefix + "demux-bitrate", C.LENGTH_UNSET);
        if (bitrate <= 0 && type == C.TRACK_TYPE_VIDEO) bitrate = intProperty("video-bitrate", C.LENGTH_UNSET);
        if (bitrate <= 0 && type == C.TRACK_TYPE_AUDIO) bitrate = intProperty("audio-bitrate", C.LENGTH_UNSET);
        int hlsBitrate = intProperty(prefix + "hls-bitrate", C.LENGTH_UNSET);
        ColorInfo colorInfo = type == C.TRACK_TYPE_VIDEO ? videoColorInfo() : null;
        return new TrackInfo(type, id, demuxId, srcId, title, lang, codec,
                decoder, dolbyVisionProfile, dolbyVisionLevel,
                sourceDolbyVisionProfile, sourceDolbyVisionLevel,
                selected, width, height, frameRate, sampleRate, channels,
                bitrate, hlsBitrate, colorInfo);
    }

    private float videoFrameRate() {
        double fps = cachedContainerFps > 0 ? cachedContainerFps : cachedEstimatedVfFps;
        return fps > 0 ? (float) fps : C.RATE_UNSET;
    }

    @Nullable
    private ColorInfo videoColorInfo() {
        int colorSpace = mpvColorSpace();
        int colorRange = mpvColorRange();
        int colorTransfer = mpvColorTransfer();
        if (colorSpace == C.LENGTH_UNSET && colorRange == C.LENGTH_UNSET && colorTransfer == C.LENGTH_UNSET) return null;
        ColorInfo.Builder builder = new ColorInfo.Builder();
        if (colorSpace != C.LENGTH_UNSET) builder.setColorSpace(colorSpace);
        if (colorRange != C.LENGTH_UNSET) builder.setColorRange(colorRange);
        if (colorTransfer != C.LENGTH_UNSET) builder.setColorTransfer(colorTransfer);
        return builder.build();
    }

    private int mpvColorSpace() {
        String primaries = lowerProperty("video-params/primaries");
        String matrix = lowerProperty("video-params/colormatrix");
        String value = !TextUtils.isEmpty(primaries) ? primaries : matrix;
        if (value.contains("bt.2020") || value.contains("bt2020") || value.contains("2020")) return C.COLOR_SPACE_BT2020;
        if (value.contains("bt.709") || value.contains("bt709") || value.contains("709")) return C.COLOR_SPACE_BT709;
        if (value.contains("bt.601") || value.contains("bt601") || value.contains("601") || value.contains("smpte-170m") || value.contains("smpte170m")) return C.COLOR_SPACE_BT601;
        return C.LENGTH_UNSET;
    }

    private int mpvColorRange() {
        String value = lowerProperty("video-params/colorlevels");
        if (value.contains("full") || value.contains("pc")) return C.COLOR_RANGE_FULL;
        if (value.contains("limited") || value.contains("tv")) return C.COLOR_RANGE_LIMITED;
        return C.LENGTH_UNSET;
    }

    private int mpvColorTransfer() {
        String value = lowerProperty("video-params/gamma");
        if (value.contains("pq") || value.contains("st.2084") || value.contains("st2084")) return C.COLOR_TRANSFER_ST2084;
        if (value.contains("hlg")) return C.COLOR_TRANSFER_HLG;
        if (value.contains("srgb")) return C.COLOR_TRANSFER_SRGB;
        if (value.contains("linear")) return C.COLOR_TRANSFER_LINEAR;
        if (value.contains("gamma2.2") || value.contains("bt.470m") || value.contains("bt470m")) return C.COLOR_TRANSFER_GAMMA_2_2;
        if (value.contains("bt.1886") || value.contains("bt1886") || value.contains("709") || value.contains("601")) return C.COLOR_TRANSFER_SDR;
        return C.LENGTH_UNSET;
    }

    private String lowerProperty(String property) {
        return stringProperty(property, "").toLowerCase(Locale.US);
    }

    private int mediaTrackType(String mpvType) {
        if ("video".equals(mpvType)) return C.TRACK_TYPE_VIDEO;
        if ("audio".equals(mpvType)) return C.TRACK_TYPE_AUDIO;
        if ("sub".equals(mpvType)) return C.TRACK_TYPE_TEXT;
        return C.TRACK_TYPE_UNKNOWN;
    }

    private String sampleMimeType(TrackInfo info) {
        String codec = info.codec == null ? "" : info.codec.toLowerCase(Locale.US);
        if (info.type == C.TRACK_TYPE_TEXT) {
            if (codec.contains("pgs") || codec.contains("hdmv")) return MimeTypes.APPLICATION_PGS;
            if (codec.contains("dvd") || codec.contains("vobsub")) return MimeTypes.APPLICATION_VOBSUB;
            if (codec.contains("dvb")) return MimeTypes.APPLICATION_DVBSUBS;
            if (codec.contains("ass") || codec.contains("ssa")) return MimeTypes.TEXT_SSA;
            if (codec.contains("webvtt") || codec.contains("vtt")) return MimeTypes.TEXT_VTT;
            if (codec.contains("srt") || codec.contains("subrip")) return MimeTypes.APPLICATION_SUBRIP;
            if (codec.contains("ttml")) return MimeTypes.APPLICATION_TTML;
            return TextUtils.isEmpty(codec) ? MimeTypes.TEXT_UNKNOWN : MimeTypes.BASE_TYPE_TEXT + "/" + codec;
        }
        if (info.type == C.TRACK_TYPE_AUDIO) {
            if (codec.contains("aac")) return MimeTypes.AUDIO_AAC;
            if (codec.contains("ac3")) return MimeTypes.AUDIO_AC3;
            if (codec.contains("eac3") || codec.contains("e-ac-3")) return MimeTypes.AUDIO_E_AC3;
            if (codec.contains("opus")) return MimeTypes.AUDIO_OPUS;
            if (codec.contains("vorbis")) return MimeTypes.AUDIO_VORBIS;
            if (codec.contains("flac")) return MimeTypes.AUDIO_FLAC;
            if (codec.contains("mp3")) return MimeTypes.AUDIO_MPEG;
            return MimeTypes.BASE_TYPE_AUDIO + "/" + (TextUtils.isEmpty(codec) ? "unknown" : codec);
        }
        if (codec.contains("hevc") || codec.contains("h265")) return MimeTypes.VIDEO_H265;
        if (codec.contains("h264") || codec.contains("avc")) return MimeTypes.VIDEO_H264;
        if (codec.contains("av1")) return MimeTypes.VIDEO_AV1;
        if (codec.contains("vp9")) return MimeTypes.VIDEO_VP9;
        if (codec.contains("vp8")) return MimeTypes.VIDEO_VP8;
        if (codec.contains("mpeg2")) return MimeTypes.VIDEO_MPEG2;
        return MimeTypes.BASE_TYPE_VIDEO + "/" + (TextUtils.isEmpty(codec) ? "unknown" : codec);
    }

    private double doubleProperty(String property, double fallback) {
        return propertyCache.getDouble(property, fallback);
    }

    private long doublePropertyMs(String property, long fallback) {
        try {
            Double value = mpvGetPropertyDouble(property);
            if (value == null || value.isNaN() || value.isInfinite()) return fallback;
            return Math.max(0, Math.round(value * SECONDS_TO_MS));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private double nativeDoubleProperty(String property, double fallback) {
        try {
            Double value = mpvGetPropertyDouble(property);
            if (value == null || value.isNaN() || value.isInfinite()) return fallback;
            return value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }


    private double doubleValue(@Nullable Object value, double fallback) {
        if (!(value instanceof Number number)) return fallback;
        double result = number.doubleValue();
        return Double.isNaN(result) || Double.isInfinite(result) ? fallback : result;
    }

    private long doubleSecondsToMs(@Nullable Object value, long fallback) {
        if (value instanceof Number number) return Math.max(0, Math.round(number.doubleValue() * SECONDS_TO_MS));
        return fallback;
    }

    private long longValue(@Nullable Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        return fallback;
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value <= 0 || multiplier <= 0) return 0;
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    private boolean isFreshCacheSpeedSample(long nowMs) {
        long now = Math.max(0, nowMs);
        return cachedCacheSpeedSampleAtMs >= 0
                && now >= cachedCacheSpeedSampleAtMs
                && now - cachedCacheSpeedSampleAtMs
                <= MpvCacheObserverState.DYNAMIC_OBSERVER_STALE_MS;
    }

    private static HlsVariant toPublicVariant(MpvHlsProxy.HlsVariant variant) {
        return new HlsVariant(
                variant.bandwidthBitsPerSecond(),
                variant.averageBandwidthBitsPerSecond(),
                variant.width(),
                variant.height());
    }

    private long longProperty(String property, long fallback) {
        return propertyCache.getLong(property, fallback);
    }

    private long nativeLongProperty(String property, long fallback) {
        try {
            Integer value = mpvGetPropertyInt(property);
            return value == null ? fallback : value.longValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @Nullable
    private Long nullableLongProperty(String property) {
        try {
            Integer value = mpvGetPropertyInt(property);
            return value == null ? null : value.longValue();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int intProperty(String property, int fallback) {
        return propertyCache.getInt(property, fallback);
    }

    private boolean booleanProperty(String property, boolean fallback) {
        return propertyCache.getBoolean(property, fallback);
    }

    private boolean nativeBooleanProperty(String property, boolean fallback) {
        try {
            Boolean value = mpvGetPropertyBoolean(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String stringProperty(String property, String fallback) {
        return propertyCache.getString(property, fallback);
    }

    private String nativeStringProperty(String property, String fallback) {
        try {
            String value = mpvGetPropertyString(property);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void refreshRenderState(MpvDiagnosticsPolicy.Request request) {
        if (!initialized || !MpvDiagnosticsPolicy.allowsSynchronousProperties(request, SpiderDebug.isEnabled())) return;
        cachedCurrentVo = firstNonEmpty(nativeStringProperty("current-vo", cachedCurrentVo), cachedCurrentVo);
        cachedCurrentGpuContext = firstNonEmpty(nativeStringProperty("current-gpu-context", cachedCurrentGpuContext), cachedCurrentGpuContext);
        cachedGpuApi = firstNonEmpty(nativeStringProperty("gpu-api", cachedGpuApi), cachedGpuApi);
    }

    private void refreshRuntimeDiagnostics(MpvDiagnosticsPolicy.Request request) {
        if (!initialized || !MpvDiagnosticsPolicy.allowsSynchronousProperties(request, SpiderDebug.isEnabled())) return;
        cachedCurrentAo = firstNonEmpty(nativeStringProperty("current-ao", cachedCurrentAo), cachedCurrentAo);
        cachedAudioDevice = firstNonEmpty(nativeStringProperty("audio-device", cachedAudioDevice), cachedAudioDevice);
        cachedHwdecCurrent = firstNonEmpty(nativeStringProperty("hwdec-current", cachedHwdecCurrent), cachedHwdecCurrent);
        cachedAvSyncSeconds = nativeDoubleProperty("avsync", cachedAvSyncSeconds);
        cachedDisplayFps = nativeDoubleProperty("display-fps", cachedDisplayFps);
        cachedEstimatedDisplayFps = nativeDoubleProperty("estimated-display-fps", cachedEstimatedDisplayFps);
        cachedDecoderDroppedFrames = Math.max(0, nativeLongProperty("decoder-frame-drop-count", cachedDecoderDroppedFrames));
        cachedOutputDroppedFrames = Math.max(0, nativeLongProperty("frame-drop-count", cachedOutputDroppedFrames));
        cachedMistimedFrames = Math.max(0, nativeLongProperty("mistimed-frame-count", cachedMistimedFrames));
        cachedDelayedFrames = Math.max(0, nativeLongProperty("vo-delayed-frame-count", cachedDelayedFrames));
        cachedDisplaySyncActive = nativeBooleanProperty("display-sync-active", cachedDisplaySyncActive);
    }

    private void resetRuntimeDiagnostics() {
        observedCurrentVo = false;
        observedHwdecCurrent = false;
        cachedCurrentVo = null;
        cachedCurrentGpuContext = null;
        cachedGpuApi = null;
        cachedCurrentAo = null;
        cachedAudioDevice = null;
        cachedHwdecCurrent = null;
        cachedAvSyncSeconds = 0;
        cachedDisplayFps = 0;
        cachedEstimatedDisplayFps = 0;
        cachedContainerFps = 0;
        cachedEstimatedVfFps = 0;
        cachedContentFrameRate = 0;
        cachedDecoderDroppedFrames = 0;
        cachedOutputDroppedFrames = 0;
        observedDroppedFrames = false;
        cachedMistimedFrames = 0;
        cachedDelayedFrames = 0;
        cachedDisplaySyncActive = false;
    }

    private void resetVideoMetadataCache() {
        cachedVideoIntProperties.clear();
        cachedContainerFps = 0;
        cachedEstimatedVfFps = 0;
        cachedContentFrameRate = 0;
    }

    private void cacheObservedVideoProperty(String property, @Nullable Object value) {
        switch (property) {
            case "width", "height", "video-params/w", "video-params/h",
                    "video-params/dw", "video-params/dh", "video-out-params/w",
                    "video-out-params/h", "video-out-params/dw", "video-out-params/dh",
                    "current-tracks/video/demux-w", "current-tracks/video/demux-h" -> {
                if (value instanceof Number number) {
                    cachedVideoIntProperties.put(property, number.intValue());
                }
            }
            case "container-fps" -> cachedContainerFps = doubleValue(value, cachedContainerFps);
            case "estimated-vf-fps" -> cachedEstimatedVfFps = doubleValue(value, cachedEstimatedVfFps);
            default -> {
            }
        }
    }

    private int cachedVideoIntProperty(String property, int fallback) {
        Integer value = cachedVideoIntProperties.get(property);
        return value == null ? fallback : value;
    }

    private String stringValue(@Nullable Object value, String fallback) {
        return value instanceof String text && !TextUtils.isEmpty(text) ? text : fallback;
    }

    private String joinParts(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values == null) return "";
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(value);
        }
        return builder.toString();
    }

    private String shortText(String value, int maxLength) {
        if (TextUtils.isEmpty(value) || maxLength <= 0 || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1) + "...";
    }

    private String formatAvSync() {
        long ms = Math.round(cachedAvSyncSeconds * 1000.0);
        return "A-V " + ms + "ms";
    }

    private String formatDisplayFps() {
        if (cachedDisplayFps <= 0 && cachedEstimatedDisplayFps <= 0) return "";
        if (cachedDisplayFps > 0 && cachedEstimatedDisplayFps > 0 && Math.abs(cachedDisplayFps - cachedEstimatedDisplayFps) >= 0.01) {
            return "刷新 " + formatHz(cachedDisplayFps) + "/估" + formatHz(cachedEstimatedDisplayFps);
        }
        return "刷新 " + formatHz(cachedDisplayFps > 0 ? cachedDisplayFps : cachedEstimatedDisplayFps);
    }

    private String formatDroppedFrames() {
        long total = cachedDecoderDroppedFrames + cachedOutputDroppedFrames;
        if (total <= 0 && cachedMistimedFrames <= 0 && cachedDelayedFrames <= 0) return "";
        return joinParts(
                total > 0 ? "掉帧 dec " + cachedDecoderDroppedFrames + "/out " + cachedOutputDroppedFrames : "",
                cachedMistimedFrames > 0 ? "mistimed " + cachedMistimedFrames : "",
                cachedDelayedFrames > 0 ? "delayed " + cachedDelayedFrames : "");
    }

    private String formatDisplaySync() {
        return cachedDisplaySyncActive ? "display-sync 开" : "";
    }

    private String formatShader() {
        return lutShader == null ? "" : "shader 开";
    }

    private String formatHz(double value) {
        if (value <= 0) return "-";
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.01) return String.valueOf((long) rounded) + "Hz";
        return String.format(Locale.US, "%.2fHz", value);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private boolean isConfiguredVulkan() {
        return TextUtils.equals(config.vo(), "gpu-next")
                && TextUtils.equals(config.gpuContext(), "androidvk")
                && TextUtils.equals(config.gpuApi(), "vulkan");
    }

    private boolean isRuntimeVulkan(String currentVo, String currentGpuContext, String gpuApi) {
        return containsIgnoreCase(currentGpuContext, "vk")
                || containsIgnoreCase(currentGpuContext, "vulkan")
                || containsIgnoreCase(gpuApi, "vulkan")
                || containsIgnoreCase(currentVo, "gpu-next") && isConfiguredVulkan();
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }

    private void fail(Throwable e, int errorCode) {
        playerError = new PlaybackException(e.getMessage(), e, errorCode);
        endSeekBuffering("fail");
        playbackState = Player.STATE_IDLE;
        loading = false;
        fileLoaded = false;
        closeContentFds();
        mainHandler.removeCallbacks(endFileValidationRunnable);
        stopStateRefresh();
        if (MpvDiagnosticsPolicy.allowsDetailedDiagnostics(MpvDiagnosticsPolicy.Request.ERROR_DETAILED, SpiderDebug.isEnabled())) PlaybackTrace.log("mpv", playbackTraceId, "fail code=%d message=%s diagnostics=%s", errorCode, MpvDiagnosticsPolicy.redactSensitive(e.getMessage()), diagnosticSummary());
        invalidateState();
        stopMainThreadWatchdog();
    }

    private String diagnosticSummary() {
        List<String> parts = new ArrayList<>();
        parts.add("trace=" + playbackTraceId);
        parts.add("source=" + MpvDiagnosticsPolicy.sourceSummary(currentPlayableUri));
        parts.add("hls=" + currentLikelyHls);
        parts.add("dash=" + currentLikelyDash);
        parts.add("loaded=" + fileLoaded);
        parts.add("started=" + loadStarted);
        parts.add("restart=" + playbackRestarted);
        parts.add("size=" + videoSize.width + "x" + videoSize.height);
        parts.add("position=" + cachedPositionMs);
        parts.add("duration=" + cachedDurationMs);
        parts.add("tracks=" + currentTracks.getGroups().size());
        parts.add("path=" + MpvDiagnosticsPolicy.sourceSummary(firstNonEmpty(stringProperty("path", ""), currentPlayableUri)));
        parts.add("file-format=" + stringProperty("file-format", ""));
        parts.add("video-codec=" + stringProperty("video-codec", ""));
        parts.add("audio-codec=" + stringProperty("audio-codec", ""));
        parts.add("hwdec=" + stringProperty("hwdec-current", ""));
        parts.add("vo=" + stringProperty("current-vo", stringProperty("vo-configured", "")));
        parts.add("shader=" + (lutShader == null ? "-" : lutShader.diagnostics()));
        parts.add("end-file=" + endFileReasonName(lastEndFileReason) + "/" + mpvErrorName(lastEndFileError) + "(" + lastEndFileError + ")");
        if (!TextUtils.isEmpty(lastEndFileErrorText)) parts.add("end-file-text=" + MpvDiagnosticsPolicy.redactSensitive(lastEndFileErrorText));
        if (currentLikelyHls) parts.add("hls-proxy=" + hlsProxy.diagnostics());
        return String.join(" ", parts);
    }

    private String recentLogSuffix() {
        if (!TextUtils.isEmpty(lastFailureLog)) return ": " + lastFailureLog;
        if (recentLogs.isEmpty()) return "";
        return ": " + recentLogs.get(recentLogs.size() - 1);
    }

    private void applyShaderPipeline(boolean force) {
        if (!initialized) return;
        String target = lutShader == null ? "" : lutShader.getPath();
        if (!force && TextUtils.equals(appliedLutShaderPath, target)) return;
        if (!TextUtils.isEmpty(appliedLutShaderPath)) {
            String optionKey = MpvLutShader.getPreviewOptionKey(appliedLutShaderPath);
            if (!optionKey.isEmpty()) safeCommand(new String[]{"change-list", "glsl-shader-opts", "del", optionKey});
            safeCommand(new String[]{"change-list", "glsl-shaders", "remove", appliedLutShaderPath});
            SpiderDebug.log("mpv", "shader remove lut=%s", appliedLutShaderPath);
        }
        if (!TextUtils.isEmpty(target)) {
            if (lutShader != null && lutShader.isPreview()) setLutPreviewProgress(0f);
            safeCommand(new String[]{"change-list", "glsl-shaders", "append", target});
            SpiderDebug.log("mpv", "shader append lut=%s", target);
        }
        appliedLutShaderPath = target;
    }

    private void postToMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else mainHandler.post(runnable);
    }

    private boolean shouldCollectDebugDetails() {
        return MpvDiagnosticsPolicy.allowsDetailedDiagnostics(MpvDiagnosticsPolicy.Request.DEBUG_LOG, SpiderDebug.isEnabled());
    }

    private boolean mpvTryCreate(Context appContext) {
        long startedAtMs = beginMpvNativeCall("lifecycle", "create");
        try {
            return MPVLib.tryCreate(appContext);
        } finally {
            endMpvNativeCall(startedAtMs, "lifecycle", "create");
        }
    }

    private void mpvInit() {
        long startedAtMs = beginMpvNativeCall("lifecycle", "init");
        try {
            MPVLib.initializeCreatedContext();
        } finally {
            endMpvNativeCall(startedAtMs, "lifecycle", "init");
        }
    }

    private void mpvDestroyCreatedContext() {
        long startedAtMs = beginMpvNativeCall("lifecycle", "destroy");
        try {
            MPVLib.destroyCreatedContext();
        } finally {
            endMpvNativeCall(startedAtMs, "lifecycle", "destroy");
        }
    }

    private int mpvSetOptionString(String property, String value) {
        long startedAtMs = beginMpvNativeCall("set-option", property);
        try {
            return MPVLib.setOptionString(property, value);
        } finally {
            endMpvNativeCall(startedAtMs, "set-option", property);
        }
    }

    private Integer mpvGetPropertyInt(String property) {
        long startedAtMs = beginMpvNativeCall("get-int", property);
        try {
            return MPVLib.getPropertyInt(property);
        } finally {
            endMpvNativeCall(startedAtMs, "get-int", property);
        }
    }

    private Double mpvGetPropertyDouble(String property) {
        long startedAtMs = beginMpvNativeCall("get-double", property);
        try {
            return MPVLib.getPropertyDouble(property);
        } finally {
            endMpvNativeCall(startedAtMs, "get-double", property);
        }
    }

    private Boolean mpvGetPropertyBoolean(String property) {
        long startedAtMs = beginMpvNativeCall("get-boolean", property);
        try {
            return MPVLib.getPropertyBoolean(property);
        } finally {
            endMpvNativeCall(startedAtMs, "get-boolean", property);
        }
    }

    private String mpvGetPropertyString(String property) {
        long startedAtMs = beginMpvNativeCall("get-string", property);
        try {
            return MPVLib.getPropertyString(property);
        } finally {
            endMpvNativeCall(startedAtMs, "get-string", property);
        }
    }

    private int mpvSetPropertyInt(String property, int value) {
        long startedAtMs = beginMpvNativeCall("set-int", property);
        try {
            return MPVLib.setPropertyInt(property, value);
        } finally {
            endMpvNativeCall(startedAtMs, "set-int", property);
        }
    }

    private int mpvSetPropertyDouble(String property, double value) {
        long startedAtMs = beginMpvNativeCall("set-double", property);
        try {
            return MPVLib.setPropertyDouble(property, value);
        } finally {
            endMpvNativeCall(startedAtMs, "set-double", property);
        }
    }

    private int mpvSetPropertyBoolean(String property, boolean value) {
        long startedAtMs = beginMpvNativeCall("set-boolean", property);
        try {
            return MPVLib.setPropertyBoolean(property, value);
        } finally {
            endMpvNativeCall(startedAtMs, "set-boolean", property);
        }
    }

    private int mpvSetPropertyString(String property, String value) {
        long startedAtMs = beginMpvNativeCall("set-string", property);
        try {
            return MPVLib.setPropertyString(property, value);
        } finally {
            endMpvNativeCall(startedAtMs, "set-string", property);
        }
    }

    private int mpvCommand(String[] command) {
        String target = command == null || command.length == 0 ? "unknown" : command[0];
        long startedAtMs = beginMpvNativeCall("command", target);
        try {
            return MPVLib.command(command);
        } finally {
            endMpvNativeCall(startedAtMs, "command", target);
        }
    }

    private int mpvEnqueueCommand(long requestId, String[] command) {
        String target = command == null || command.length == 0 ? "unknown" : command[0];
        long startedAtMs = beginMpvNativeCall("enqueue-command", target);
        try {
            return MPVLib.enqueueCommand(requestId, command);
        } finally {
            endMpvNativeCall(startedAtMs, "enqueue-command", target);
        }
    }

    private int mpvObserveProperty(String property, int format) {
        long startedAtMs = beginMpvNativeCall("observe", property);
        try {
            return MPVLib.observeProperty(property, format);
        } finally {
            endMpvNativeCall(startedAtMs, "observe", property);
        }
    }

    private void mpvAttachSurface(Surface target) {
        long startedAtMs = beginMpvNativeCall("surface", "attach-video");
        try {
            MPVLib.attachSurface(target);
        } finally {
            endMpvNativeCall(startedAtMs, "surface", "attach-video");
        }
    }

    private void mpvDetachSurface() {
        long startedAtMs = beginMpvNativeCall("surface", "detach-video");
        try {
            MPVLib.detachSurface();
        } finally {
            endMpvNativeCall(startedAtMs, "surface", "detach-video");
        }
    }

    private void mpvDetachOsdSurface() {
        long startedAtMs = beginMpvNativeCall("surface", "detach-osd");
        try {
            MPVLib.detachOsdSurface();
        } finally {
            endMpvNativeCall(startedAtMs, "surface", "detach-osd");
        }
    }

    private int mpvEnqueueOsdSurface(long requestId, @Nullable Surface target) {
        String operation = target == null ? "detach-osd-async" : "attach-osd-async";
        long startedAtMs = beginMpvNativeCall("surface", operation);
        try {
            return MPVLib.enqueueOsdSurface(requestId, target);
        } finally {
            endMpvNativeCall(startedAtMs, "surface", operation);
        }
    }

    private void setOption(String name, String value) {
        if (value == null) value = "";
        try {
            mpvSetOptionString(name, value);
        } catch (Throwable ignored) {
        }
    }

    private void setRuntimeString(String name, String value) {
        setRuntimeStringChecked(name, value);
    }

    private boolean setRuntimeStringChecked(String name, String value) {
        if (value == null) value = "";
        if (!initialized) return false;
        try {
            mpvSetPropertyString(name, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void observe(String property, int format) {
        if (!observedPropertyNames.add(property)) return;
        try {
            mpvObserveProperty(property, format);
        } catch (Throwable ignored) {
            observedPropertyNames.remove(property);
        }
    }

    private void safeSetPropertyBoolean(String property, boolean value) {
        try {
            mpvSetPropertyBoolean(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void applyTextOffset() {
        if (initialized) safeSetPropertyDouble("sub-delay", textOffsetMs / SECONDS_TO_MS);
    }

    private void applyAudioOffset() {
        if (initialized) safeSetPropertyDouble("audio-delay", audioOffsetMs / SECONDS_TO_MS);
    }

    private void applyVideoAspect() {
        if (!initialized) return;
        safeSetPropertyString("video-aspect-override", videoAspectRatio > 0f && !stretchVideo ? String.format(Locale.US, "%.6f", videoAspectRatio) : "no");
        safeSetPropertyString("keepaspect", stretchVideo ? "no" : "yes");
    }

    private void applySubtitleStyle() {
        if (!initialized) return;
        CaptionStyle style = captionStyle();
        safeSetPropertyDouble("sub-scale", subtitleScale());
        safeSetPropertyDouble("sub-pos", subtitlePosition());
        safeSetPropertyString("sub-font", style.font);
        safeSetPropertyString("sub-bold", style.bold ? "yes" : "no");
        safeSetPropertyString("sub-italic", style.italic ? "yes" : "no");
        safeSetPropertyString("sub-color", mpvColor(style.foreground));
        safeSetPropertyString("sub-border-color", mpvColor(style.edge));
        safeSetPropertyString("sub-shadow-color", mpvColor(style.back));
        safeSetPropertyString("sub-back-color", mpvColor(style.back));
        safeSetPropertyString("sub-border-style", style.borderStyle);
        safeSetPropertyDouble("sub-border-size", style.borderSize);
        safeSetPropertyDouble("sub-shadow-offset", style.shadowOffset);
        safeSetPropertyString("sub-ass-style-overrides", assStyleOverrides(style));
        // Force MPV to re-render ASS/SSA subtitles with the new sub-scale value.
        // Toggling sub-visibility triggers layout recalculation. Save and restore
        // the original state so we don't accidentally enable subtitles if user disabled them.
        boolean wasVisible = booleanProperty("sub-visibility", true);
        safeSetPropertyBoolean("sub-visibility", false);
        safeSetPropertyBoolean("sub-visibility", wasVisible);
    }

    private double subtitleScale() {
        if (subtitleTextSize <= 0) return 1.0;
        return Math.max(0.5, Math.min(2.5, subtitleTextSize / DEFAULT_SUBTITLE_TEXT_SIZE_FRACTION));
    }

    private double subtitlePosition() {
        return Math.max(0, Math.min(150, 100.0 - subtitlePosition * 100.0));
    }

    private CaptionStyle captionStyle() {
        if (!PlayerSetting.isCaption()) return defaultCaptionStyle();
        try {
            CaptioningManager manager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
            CaptioningManager.CaptionStyle style = manager == null ? null : manager.getUserStyle();
            if (style == null) return defaultCaptionStyle();
            int foreground = style.hasForegroundColor() ? style.foregroundColor : Color.WHITE;
            int background = style.hasBackgroundColor() ? style.backgroundColor : Color.TRANSPARENT;
            int edge = style.hasEdgeColor() ? style.edgeColor : Color.BLACK;
            int edgeType = style.hasEdgeType() ? style.edgeType : CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE;
            Typeface typeface = style.getTypeface();
            String font = captionFont(typeface);
            boolean bold = typeface != null && typeface.isBold();
            boolean italic = typeface != null && typeface.isItalic();
            return switch (edgeType) {
                case CaptioningManager.CaptionStyle.EDGE_TYPE_NONE -> captionStyle(font, bold, italic, foreground, edge, background, Color.TRANSPARENT, 0.0, 0.0);
                case CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW -> captionStyle(font, bold, italic, foreground, edge, background, edge, 0.0, 2.0);
                case CaptioningManager.CaptionStyle.EDGE_TYPE_RAISED, CaptioningManager.CaptionStyle.EDGE_TYPE_DEPRESSED -> captionStyle(font, bold, italic, foreground, edge, background, edge, 1.0, 1.0);
                default -> captionStyle(font, bold, italic, foreground, edge, background, Color.TRANSPARENT, 3.0, 0.0);
            };
        } catch (Throwable ignored) {
            return defaultCaptionStyle();
        }
    }

    private CaptionStyle captionStyle(String font, boolean bold, boolean italic, int foreground, int edge, int background, int shadow, double borderSize, double shadowOffset) {
        boolean hasBackground = Color.alpha(background) > 0;
        return new CaptionStyle(font, bold, italic, foreground, edge, hasBackground ? background : shadow, hasBackground ? "background-box" : "outline-and-shadow", borderSize, hasBackground ? 2.0 : shadowOffset);
    }

    private CaptionStyle defaultCaptionStyle() {
        return new CaptionStyle("sans-serif", false, false, Color.WHITE, Color.BLACK, Color.TRANSPARENT, "outline-and-shadow", 3.0, 0.0);
    }

    private String captionFont(@Nullable Typeface typeface) {
        if (typeface == null || Typeface.DEFAULT.equals(typeface) || Typeface.SANS_SERIF.equals(typeface)) return "sans-serif";
        if (Typeface.SERIF.equals(typeface)) return "serif";
        if (Typeface.MONOSPACE.equals(typeface)) return "monospace";
        String value = typeface.toString().toLowerCase(Locale.US);
        if (value.contains("mono")) return "monospace";
        if (value.contains("serif")) return "serif";
        return "sans-serif";
    }

    private String mpvColor(int color) {
        return String.format(Locale.US, "%.4f/%.4f/%.4f/%.4f", Color.red(color) / 255.0, Color.green(color) / 255.0, Color.blue(color) / 255.0, Color.alpha(color) / 255.0);
    }

    private String assStyleOverrides(CaptionStyle style) {
        return "FontName=" + style.font
                + ",Bold=" + (style.bold ? "1" : "0")
                + ",Italic=" + (style.italic ? "1" : "0")
                + ",PrimaryColour=" + assColor(style.foreground)
                + ",OutlineColour=" + assColor(style.edge)
                + ",BackColour=" + assColor(style.back)
                + ",BorderStyle=" + ("background-box".equals(style.borderStyle) ? "4" : "1")
                + ",Outline=" + String.format(Locale.US, "%.1f", style.borderSize)
                + ",Shadow=" + String.format(Locale.US, "%.1f", style.shadowOffset);
    }

    private String assColor(int color) {
        int alpha = 255 - Color.alpha(color);
        return String.format(Locale.US, "&H%02X%02X%02X%02X", alpha, Color.blue(color), Color.green(color), Color.red(color));
    }

    private void safeSetPropertyDouble(String property, double value) {
        try {
            mpvSetPropertyDouble(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeSetPropertyInt(String property, int value) {
        try {
            mpvSetPropertyInt(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeSetPropertyString(String property, String value) {
        try {
            mpvSetPropertyString(property, value);
        } catch (Throwable ignored) {
        }
    }

    private void safeCommand(String[] command) {
        try {
            mpvCommand(command);
        } catch (Throwable ignored) {
        }
    }

    private boolean enqueueMpvCommand(String... command) {
        if (!initialized) return false;
        long requestId = NATIVE_REQUEST_IDS.getAndIncrement();
        if (requestId <= 0) {
            NATIVE_REQUEST_IDS.compareAndSet(requestId + 1, 2);
            requestId = 1;
        }
        try {
            int result = mpvEnqueueCommand(requestId, command);
            if (result >= MPVLib.MpvError.MPV_ERROR_SUCCESS) return true;
            Log.e(TAG, "Unable to enqueue mpv command request=" + requestId + " error=" + result);
        } catch (Throwable e) {
            Log.e(TAG, "Unable to enqueue mpv command request=" + requestId, e);
        }
        return false;
    }

    private void closeContentFds() {
        if (contentFds.isEmpty()) return;
        for (ParcelFileDescriptor fd : contentFds) {
            try {
                fd.close();
            } catch (IOException ignored) {
            }
        }
        contentFds.clear();
    }

    private void closeIsoSession() {
        if (TextUtils.isEmpty(currentIsoUri)) return;
        if (isoMetadataListenerSessionId > 0) {
            IsoSessionManager.removeTrackMetadataListener(isoMetadataListenerSessionId, isoTrackMetadataReadyListener);
            isoMetadataListenerSessionId = -1;
        }
        IsoSessionManager.closeUri(currentIsoUri);
        currentIsoUri = null;
        isoTrackListDumped = false;
    }

    private void attachIsoTrackMetadataListener() {
        long sessionId = IsoSessionManager.parseId(currentIsoUri);
        if (sessionId <= 0) return;
        if (isoMetadataListenerSessionId > 0) {
            IsoSessionManager.removeTrackMetadataListener(isoMetadataListenerSessionId, isoTrackMetadataReadyListener);
        }
        isoMetadataListenerSessionId = sessionId;
        IsoSessionManager.addTrackMetadataListener(sessionId, isoTrackMetadataReadyListener);
    }

    private void onIsoTrackMetadataReady() {
        long sessionId = isoMetadataListenerSessionId;
        mainHandler.post(() -> {
            if (released || sessionId <= 0 || sessionId != IsoSessionManager.parseId(currentIsoUri)) return;
            SpiderDebug.log("mpv", "iso track metadata ready session=%d", sessionId);
            refreshTracks();
            invalidateState();
        });
    }

    private boolean isLikelyIso(MediaItem item, String uri) {
        if (item != null && item.localConfiguration != null) {
            String mime = item.localConfiguration.mimeType;
            if ("video/x-iso".equalsIgnoreCase(mime)
                    || "application/x-iso9660-image".equalsIgnoreCase(mime)
                    || "application/x-iso9660".equalsIgnoreCase(mime)) return true;
        }
        String lower = uri == null ? "" : uri.toLowerCase(Locale.US);
        try {
            String path = Uri.parse(uri).getPath();
            if (path != null && path.toLowerCase(Locale.US).endsWith(".iso")) return true;
        } catch (Throwable ignored) {
        }
        if (lower.contains(".iso?") || lower.contains("%2eiso")) return true;
        CharSequence title = item == null ? null : item.mediaMetadata.title;
        return title != null && title.toString().trim().toLowerCase(Locale.US).endsWith(".iso");
    }

    private void logSourceDiagnostics(MediaItem item, String uri, Map<String, String> headers) {
        if (!shouldCollectDebugDetails()) return;
        String scheme = "";
        boolean loopback = false;
        boolean pathIso = false;
        try {
            Uri parsed = Uri.parse(uri);
            scheme = String.valueOf(parsed.getScheme());
            String host = String.valueOf(parsed.getHost());
            loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            String path = parsed.getPath();
            pathIso = path != null && path.toLowerCase(Locale.US).endsWith(".iso");
        } catch (Throwable ignored) {
        }
        String mime = item != null && item.localConfiguration != null ? item.localConfiguration.mimeType : null;
        String mediaId = item == null ? null : item.mediaId;
        CharSequence title = item == null ? null : item.mediaMetadata.title;
        PlaybackTrace.log("mpv", playbackTraceId, "source diagnostic scheme=" + scheme
                + " loopback=" + loopback
                + " urlLen=" + (uri == null ? 0 : uri.length())
                + " pathIso=" + pathIso
                + " mime=" + mime
                + " mediaIdLen=" + (mediaId == null ? 0 : mediaId.length())
                + " mediaIdIso=" + containsIso(mediaId)
                + " titleIso=" + containsIso(title == null ? null : title.toString())
                + " headers=" + (headers == null ? 0 : headers.size()));
    }

    private boolean containsIso(String value) {
        return value != null && value.toLowerCase(Locale.US).contains(".iso");
    }

    private boolean isOpaqueLocalProxy(String uri) {
        try {
            Uri parsed = Uri.parse(uri);
            String host = parsed.getHost();
            return ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldProbeOpaqueIso(MediaItem item, String uri) {
        if (!isOpaqueLocalProxy(uri)) return false;
        if (containsIso(uri)) return true;
        if (item == null) return false;
        if (containsIso(item.mediaId)) return true;
        CharSequence title = item.mediaMetadata.title;
        return title != null && containsIso(title.toString());
    }

    private void copySupportAssets() throws IOException {
        copyAsset("cacert.pem", config.caFile());
        MpvFontConfig.write(config.configDir(), config.cacheDir());
    }

    private void copyAsset(String name, File outFile) throws IOException {
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open(name, AssetManager.ACCESS_STREAMING)) {
            long size = in.available();
            if (outFile.length() == size && size > 0) return;
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Unable to create " + parent);
            try (OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        }
    }

    private final class TrackInfo {
        final int type;
        final String id;
        final int demuxId;
        final int srcId;
        final String title;
        final String lang;
        final String codec;
        final String decoder;
        final int dolbyVisionProfile;
        final int dolbyVisionLevel;
        final int sourceDolbyVisionProfile;
        final int sourceDolbyVisionLevel;
        final boolean selected;
        final int width;
        final int height;
        final float frameRate;
        final int sampleRate;
        final int channels;
        final int bitrate;
        final int hlsBitrate;
        final ColorInfo colorInfo;

        TrackInfo(int type, String id, int demuxId, int srcId, String title,
                  String lang, String codec, String decoder,
                  int dolbyVisionProfile, int dolbyVisionLevel,
                  int sourceDolbyVisionProfile, int sourceDolbyVisionLevel,
                  boolean selected, int width, int height, float frameRate,
                  int sampleRate, int channels, int bitrate, int hlsBitrate,
                  @Nullable ColorInfo colorInfo) {
            this.type = type;
            this.id = id;
            this.demuxId = demuxId;
            this.srcId = srcId;
            this.title = title;
            this.lang = lang;
            this.codec = codec;
            this.decoder = decoder;
            this.dolbyVisionProfile = dolbyVisionProfile;
            this.dolbyVisionLevel = dolbyVisionLevel;
            this.sourceDolbyVisionProfile = sourceDolbyVisionProfile;
            this.sourceDolbyVisionLevel = sourceDolbyVisionLevel;
            this.selected = selected;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitrate = bitrate;
            this.hlsBitrate = hlsBitrate;
            this.colorInfo = colorInfo;
        }

        Format toFormat() {
            String label = TextUtils.isEmpty(title) ? (TextUtils.isEmpty(lang) ? trackLabel() : null) : title;
            Format.Builder builder = new Format.Builder()
                    .setId(type + ":" + id)
                    .setLabel(label)
                    .setCodecs(TextUtils.isEmpty(codec) ? null : codec)
                    .setLanguage(TextUtils.isEmpty(lang) ? null : lang)
                    .setSampleMimeType(sampleMimeType(this));
            if (width > 0) builder.setWidth(width);
            if (height > 0) builder.setHeight(height);
            if (frameRate > 0) builder.setFrameRate(frameRate);
            if (colorInfo != null) builder.setColorInfo(colorInfo);
            if (sampleRate > 0) builder.setSampleRate(sampleRate);
            if (channels > 0) builder.setChannelCount(channels);
            if (bitrate > 0) builder.setAverageBitrate(bitrate);
            return builder.build();
        }

        VideoTrackDiagnostics toVideoTrackDiagnostics() {
            int sourceProfile = sourceDolbyVisionProfile > 0
                    ? sourceDolbyVisionProfile : dolbyVisionProfile;
            int sourceLevel = sourceDolbyVisionProfile > 0
                    ? sourceDolbyVisionLevel : dolbyVisionLevel;
            return new VideoTrackDiagnostics(
                    sourceCodecs(sourceProfile, sourceLevel),
                    dolbyVisionProfile, dolbyVisionLevel,
                    sourceProfile, sourceLevel,
                    codec, decoder, colorInfo);
        }

        private String sourceCodecs(int profile, int level) {
            if (profile <= 0) return codec;
            String value = String.format(Locale.US, "dvhe.%02d", profile);
            return level >= 0
                    ? value + String.format(Locale.US, ".%02d", level)
                    : value;
        }

        private String trackLabel() {
            String prefix = switch (type) {
                case C.TRACK_TYPE_VIDEO -> "Video";
                case C.TRACK_TYPE_AUDIO -> "Audio";
                case C.TRACK_TYPE_TEXT -> "Subtitle";
                default -> "Track";
            };
            return prefix + " " + id;
        }
    }

    public record VideoTrackDiagnostics(
            String sourceCodecs,
            int dolbyVisionProfile,
            int dolbyVisionLevel,
            int sourceDolbyVisionProfile,
            int sourceDolbyVisionLevel,
            String decodedCodec,
            String decoderName,
            @Nullable ColorInfo outputColorInfo) {

        public VideoTrackDiagnostics {
            sourceCodecs = sourceCodecs == null ? "" : sourceCodecs;
            decodedCodec = decodedCodec == null ? "" : decodedCodec;
            decoderName = decoderName == null ? "" : decoderName;
        }

        public VideoTrackDiagnostics(
                String sourceCodecs,
                int dolbyVisionProfile,
                int dolbyVisionLevel,
                String decodedCodec,
                String decoderName,
                @Nullable ColorInfo outputColorInfo) {
            this(sourceCodecs, dolbyVisionProfile, dolbyVisionLevel,
                    dolbyVisionProfile, dolbyVisionLevel,
                    decodedCodec, decoderName, outputColorInfo);
        }

        public boolean hasDolbyVisionSource() {
            return sourceDolbyVisionProfile > 0;
        }

        public static VideoTrackDiagnostics empty() {
            return new VideoTrackDiagnostics("", C.INDEX_UNSET, C.INDEX_UNSET,
                    C.INDEX_UNSET, C.INDEX_UNSET, "", "", null);
        }
    }

    private record CaptionStyle(String font, boolean bold, boolean italic, int foreground, int edge, int back, String borderStyle, double borderSize, double shadowOffset) {
    }
}
