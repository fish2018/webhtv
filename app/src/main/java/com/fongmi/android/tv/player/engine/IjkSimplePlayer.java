package com.fongmi.android.tv.player.engine;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.mpvplayer.MpvHlsProxy;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.cache.PlaybackDiskBufferStore;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.PlaybackResourceClassifier;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.ijk.IjkBufferPolicy;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.ijk.IjkRealtimeRecoveryPolicy;
import com.fongmi.android.tv.setting.IjkPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

@UnstableApi
class IjkSimplePlayer extends SimpleBasePlayer implements IMediaPlayer.Listener {

    private static final long STATE_REFRESH_INTERVAL_MS = 1000;

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
            .add(COMMAND_SET_VIDEO_SURFACE)
            .add(COMMAND_GET_TRACKS)
            .build();

    private final IjkMediaPlayer ijk;
    private final MpvHlsProxy hlsProxy;
    private final Runnable stateRefreshRunnable;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private Object videoOutput;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private Tracks currentTracks;
    private Format selectedVideoFormat;
    private Format selectedAudioFormat;
    private VideoSize videoSize;
    private int playbackState;
    private int bufferingPercent;
    private long bufferingPositionMs;
    private int decode;
    private long pendingSeekPositionMs;
    private long pendingSeekRequestedAtMs;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private boolean currentDash;
    private volatile boolean resourceObservationActive;
    private volatile PlaybackResourceClassifier.Classification resourceClassification;
    private volatile String currentPlayableUrl;
    private volatile IjkBufferPolicy.Config automaticInputBufferConfig;
    private volatile IjkBufferPolicy.Config appliedInputBufferConfig;
    private volatile IjkDecodePressurePolicy.Config automaticDecodeControlConfig;
    private volatile IjkDecodePressurePolicy.Config appliedDecodeControlConfig;
    private IjkPlayerEngine.ErrorSnapshot lastErrorSnapshot;
    private volatile IjkPlayerEngine.OpenStage openStage;
    private volatile int lastHttpStatus;
    private volatile long lastNativeOffset;
    private volatile boolean longUrlProxied;
    private boolean prepared;
    private boolean newlyRenderedFirstFrame;
    private boolean renderedFirstFrameSeen;
    private float volume;

    IjkSimplePlayer(int decode) {
        super(Looper.getMainLooper());
        this.decode = decode;
        ijk = new IjkMediaPlayer();
        ijk.setListener(this);
        ijk.setOnNativeInvokeListener(this::onNativeInvoke);
        hlsProxy = new MpvHlsProxy(PlayerSetting.IJK);
        stateRefreshRunnable = this::refreshPlaybackState;
        playbackParameters = PlaybackParameters.DEFAULT;
        currentTracks = Tracks.EMPTY;
        videoSize = VideoSize.UNKNOWN;
        playbackState = Player.STATE_IDLE;
        pendingSeekPositionMs = C.TIME_UNSET;
        pendingSeekRequestedAtMs = C.TIME_UNSET;
        playWhenReady = true;
        volume = 1f;
        automaticInputBufferConfig = IjkBufferPolicy.safeInitialConfig();
        appliedInputBufferConfig = IjkBufferPolicy.safeInitialConfig();
        automaticDecodeControlConfig = IjkDecodePressurePolicy.automaticInitialConfig();
        appliedDecodeControlConfig = IjkDecodePressurePolicy.automaticInitialConfig();
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        resetOpenDiagnostics();
    }

    @Override
    protected State getState() {
        int state = playbackState;
        boolean isLoading = loading && state != Player.STATE_IDLE && state != Player.STATE_ENDED;
        boolean firstFrameEvent = newlyRenderedFirstFrame;
        newlyRenderedFirstFrame = false;
        State.Builder builder = new State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(state)
                .setIsLoading(isLoading)
                .setPlayerError(playerError)
                .setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(playbackParameters)
                .setVideoSize(videoSize)
                .setNewlyRenderedFirstFrame(firstFrameEvent)
                .setVolume(volume)
                .setPlaylist(mediaItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData()))
                .setCurrentMediaItemIndex(mediaItem == null ? C.INDEX_UNSET : 0);
        if (mediaItem != null) {
            long duration = duration();
            long position = position();
            long buffered = bufferedPosition(position, duration);
            builder.setContentPositionMs(isPlayingInternal() ? PositionSupplier.getExtrapolating(position, playbackParameters.speed) : PositionSupplier.getConstant(position));
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(buffered));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, buffered - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData() {
        long duration = duration();
        IjkStreamScenePolicy.Decision scene = streamSceneDecision(duration);
        MediaItemData.Builder builder = new MediaItemData.Builder(mediaItem.mediaId)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(scene.seekable())
                .setIsDynamic(scene.dynamic())
                .setManifest(scene.timelineSnapshot())
                .setTracks(currentTracks);
        if (scene.authoritative() && scene.live()) {
            MediaItem.LiveConfiguration.Builder live =
                    new MediaItem.LiveConfiguration.Builder();
            if (scene.targetOffsetMs() != C.TIME_UNSET) {
                live.setTargetOffsetMs(scene.targetOffsetMs());
            }
            builder.setLiveConfiguration(live.build());
        }
        return builder.build();
    }

    Tracks getCurrentTracksSnapshot() {
        return currentTracks;
    }

    @Nullable
    Format getSelectedVideoFormatSnapshot() {
        return selectedVideoFormat;
    }

    @Nullable
    Format getSelectedAudioFormatSnapshot() {
        return selectedAudioFormat;
    }

    String getVideoCodecInfoSnapshot() {
        return ijk.getVideoCodecInfo();
    }

    String getAudioCodecInfoSnapshot() {
        return ijk.getAudioCodecInfo();
    }

    int getVideoDecoderSnapshot() {
        try {
            return ijk.getVideoDecoder();
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "decoder fact unavailable type=%s", error.getClass().getSimpleName());
            return IjkMediaPlayer.FFP_PROPV_DECODER_UNKNOWN;
        }
    }

    IjkPlayerEngine.ErrorSnapshot getLastErrorSnapshot() {
        return lastErrorSnapshot;
    }

    IjkPlayerEngine.DropRateSnapshot getDropRateSnapshot() {
        try {
            float rate = ijk.getDropFrameRate();
            float decodeFps = ijk.getVideoDecodeFramesPerSecond();
            boolean available = prepared
                    && renderedFirstFrameSeen
                    && Float.isFinite(rate)
                    && rate >= 0f
                    && Float.isFinite(decodeFps)
                    && decodeFps > 0f;
            int permille = available
                    ? (int) Math.max(0,
                    Math.min(10_000, Math.round(rate * 1_000f)))
                    : 0;
            return new IjkPlayerEngine.DropRateSnapshot(
                    available, permille);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "ijk-runtime-profile",
                        "drop-rate unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkPlayerEngine.DropRateSnapshot.unknown();
        }
    }

    long getTcpSpeedSnapshot() {
        try {
            return Math.max(0, ijk.getTcpSpeed());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "tcp speed fact unavailable type=%s", error.getClass().getSimpleName());
            return 0;
        }
    }

    long getBitrateSnapshot() {
        try {
            return Math.max(0, ijk.getBitRate());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "bitrate fact unavailable type=%s", error.getClass().getSimpleName());
            return 0;
        }
    }

    Long getLiveLagLowerBoundSnapshot() {
        MpvHlsProxy.LiveLagSnapshot snapshot = hlsProxy.liveLagSnapshot(
                getNativeBufferedDurationSnapshot());
        return snapshot.known() ? snapshot.lowerBoundMs() : null;
    }

    IjkRealtimeRecoveryPolicy.QueueSnapshot getRealtimeQueueSnapshot() {
        try {
            Tracks tracks = currentTracks;
            return new IjkRealtimeRecoveryPolicy.QueueSnapshot(
                    true,
                    tracks.containsType(C.TRACK_TYPE_AUDIO),
                    tracks.containsType(C.TRACK_TYPE_VIDEO),
                    ijk.getAudioCachedDuration(),
                    ijk.getVideoCachedDuration(),
                    ijk.getAudioCachedBytes(),
                    ijk.getVideoCachedBytes(),
                    ijk.getAudioCachedPackets(),
                    ijk.getVideoCachedPackets());
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-realtime",
                        "queue facts unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkRealtimeRecoveryPolicy.QueueSnapshot.unknown();
        }
    }

    IjkDecodePressurePolicy.DecodeSnapshot getDecodePressureSnapshot() {
        try {
            float decodeFps = ijk.getVideoDecodeFramesPerSecond();
            float outputFps = ijk.getVideoOutputFramesPerSecond();
            return new IjkDecodePressurePolicy.DecodeSnapshot(
                    true, decodeFps, outputFps);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-decode",
                        "fps facts unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return IjkDecodePressurePolicy.DecodeSnapshot.unknown();
        }
    }

    private long getNativeBufferedDurationSnapshot() {
        try {
            long audio = Math.max(0, ijk.getAudioCachedDuration());
            long video = Math.max(0, ijk.getVideoCachedDuration());
            Tracks tracks = currentTracks;
            return IjkBufferedDurationPolicy.resolve(
                    tracks.containsType(C.TRACK_TYPE_AUDIO),
                    tracks.containsType(C.TRACK_TYPE_VIDEO),
                    audio,
                    video);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("ijk-buffer",
                        "buffer-duration unavailable errorType=%s action=keep-unknown",
                        error.getClass().getSimpleName());
            }
            return 0;
        }
    }

    void setDecode(int decode) {
        this.decode = decode;
    }

    void stageAutomaticInputBufferConfig(IjkBufferPolicy.Config config) {
        automaticInputBufferConfig = config == null
                ? IjkBufferPolicy.safeInitialConfig() : config;
    }

    IjkBufferPolicy.Config getAppliedInputBufferConfig() {
        return appliedInputBufferConfig;
    }

    void stageAutomaticDecodeControlConfig(
            IjkDecodePressurePolicy.Config config) {
        automaticDecodeControlConfig = config == null
                ? IjkDecodePressurePolicy.automaticInitialConfig() : config;
    }

    IjkDecodePressurePolicy.Config getAppliedDecodeControlConfig() {
        return appliedDecodeControlConfig;
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        setPendingSeek(mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET);
        playbackState = mediaItem == null ? Player.STATE_IDLE : Player.STATE_IDLE;
        loading = false;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        playerError = null;
        prepared = false;
        bufferingPercent = 0;
        bufferingPositionMs = 0;
        newlyRenderedFirstFrame = false;
        renderedFirstFrameSeen = false;
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (mediaItems.isEmpty()) invalidateResourceObservation();
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        invalidateResourceObservation();
        mediaItem = null;
        playbackState = Player.STATE_IDLE;
        loading = false;
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        openCurrent();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        hlsProxy.setPlaybackPaused(!playWhenReady);
        if (playbackState == Player.STATE_READY) {
            if (playWhenReady) ijk.start();
            else ijk.pause();
        }
        if (!playWhenReady) requestPreload(Math.max(0, position()));
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        stopInternal(true);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        if (currentDash) {
            releaseDashSafely();
            return Futures.immediateVoidFuture();
        }
        stopInternal(false);
        hlsProxy.release();
        ijk.release();
        return Futures.immediateVoidFuture();
    }

    private void releaseDashSafely() {
        stopStateRefresh();
        loading = false;
        playWhenReady = false;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        videoSize = VideoSize.UNKNOWN;
        try {
            ijk.resetListeners();
            clearVideoOutput();
            ijk.stop();
        } catch (Throwable e) {
            SpiderDebug.log("ijk",
                    "dash switch stop failed errorType=%s action=continue-release",
                    e.getClass().getSimpleName());
        }
        SpiderDebug.log("ijk", "dash switch deferred release scheduled");
        Task.schedule(() -> {
            try {
                ijk.release();
                SpiderDebug.log("ijk", "dash switch deferred release complete");
            } catch (Throwable e) {
                SpiderDebug.log("ijk",
                        "dash switch deferred release failed errorType=%s action=release-proxy",
                        e.getClass().getSimpleName());
            } finally {
                hlsProxy.release();
            }
        }, 800, TimeUnit.MILLISECONDS);
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE;
        ijk.setLooping(repeatOne);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (positionMs == C.TIME_UNSET) positionMs = 0;
        bufferingPercent = 0;
        bufferingPositionMs = Math.max(0, positionMs);
        setPendingSeek(positionMs > 0 ? positionMs : C.TIME_UNSET);
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            ijk.seekTo(positionMs);
        }
        requestPreload(positionMs);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        ijk.setSpeed(playbackParameters.speed);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume, int volumeOperationType) {
        this.volume = volume;
        ijk.setVolume(volume, volume);
        return Futures.immediateVoidFuture();
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
    public void onPrepared(IMediaPlayer mp) {
        prepared = true;
        advanceOpenStage(IjkPlayerEngine.OpenStage.PREPARED);
        playbackState = Player.STATE_READY;
        loading = false;
        playerError = null;
        refreshTracks();
        if (pendingSeekPositionMs != C.TIME_UNSET) {
            ijk.seekTo(pendingSeekPositionMs);
        }
        requestPreload(Math.max(0, position()));
        if (playWhenReady) ijk.start();
        invalidateState();
        startStateRefresh();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_ENDED;
        loading = false;
        stopStateRefresh();
        invalidateState();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        lastErrorSnapshot = new IjkPlayerEngine.ErrorSnapshot(
                what,
                extra,
                prepared,
                openStage,
                lastHttpStatus,
                lastNativeOffset,
                longUrlProxied);
        setPendingSeek(C.TIME_UNSET);
        playbackState = Player.STATE_IDLE;
        loading = false;
        stopStateRefresh();
        playerError = new PlaybackException(
                "IJK playback failed stage=" + openStage.label(),
                null,
                IjkErrorMappingPolicy.resolve(
                        what, extra, prepared, openStage, lastHttpStatus));
        SpiderDebug.log("ijk",
                "error what=%d extra=%d mapped=%d decode=%d prepared=%s stage=%s http=%d offset=%d longProxy=%s action=notify",
                what,
                extra,
                playerError.errorCode,
                decode,
                prepared,
                openStage.label(),
                lastHttpStatus,
                lastNativeOffset,
                longUrlProxied);
        prepared = false;
        invalidateState();
        return true;
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        if (what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.INPUT_OPENED);
        } else if (what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.STREAM_INFO);
        } else if (what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.COMPONENT_OPENED);
        }
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
            startStateRefresh();
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END
                || what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            loading = false;
            playbackState = Player.STATE_READY;
            startStateRefresh();
        }
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                && !renderedFirstFrameSeen) {
            renderedFirstFrameSeen = true;
            newlyRenderedFirstFrame = true;
            advanceOpenStage(IjkPlayerEngine.OpenStage.FIRST_FRAME);
        }
        if (SpiderDebug.isEnabled()
                && (what == IMediaPlayer.MEDIA_INFO_OPEN_INPUT
                || what == IMediaPlayer.MEDIA_INFO_FIND_STREAM_INFO
                || what == IMediaPlayer.MEDIA_INFO_COMPONENT_OPEN)) {
            SpiderDebug.log("ijk",
                    "open stage=%s info=%d extra=%d http=%d longProxy=%s",
                    openStage.label(), what, extra, lastHttpStatus,
                    longUrlProxied);
        }
        invalidateState();
    }

    private boolean onNativeInvoke(int event, Bundle bundle) {
        int httpStatus = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_HTTP_CODE, 0);
        int nativeError = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_ERROR, 0);
        int retry = bundleNumber(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_RETRY_COUNTER, 0);
        long offset = bundleLong(bundle,
                IjkMediaPlayer.OnNativeInvokeListener.ARG_OFFSET, -1);
        if (event == IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_OPEN) {
            advanceOpenStage(IjkPlayerEngine.OpenStage.HTTP_OPENING);
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_OPEN) {
            if (httpStatus > 0) lastHttpStatus = httpStatus;
            advanceOpenStage(IjkPlayerEngine.OpenStage.HTTP_OPENED);
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_SEEK) {
            if (httpStatus > 0) lastHttpStatus = httpStatus;
            if (offset >= 0) lastNativeOffset = offset;
        } else if (event
                == IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_SEEK
                && offset >= 0) {
            lastNativeOffset = offset;
        }
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("ijk",
                    "native event=%d stage=%s http=%d error=%d offset=%d retry=%d",
                    event,
                    openStage.label(),
                    httpStatus,
                    nativeError,
                    offset,
                    retry);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        bufferingPercent = percent;
        invalidateState();
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, long positionMs) {
        bufferingPositionMs = Math.max(0, positionMs);
        invalidateState();
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
        videoSize = new VideoSize(width, height);
        refreshTracks();
        invalidateState();
    }

    @Override
    public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
    }

    private void openCurrent() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return;
        try {
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            prepared = false;
            newlyRenderedFirstFrame = false;
            renderedFirstFrameSeen = false;
            lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
            resetOpenDiagnostics();
            ijk.reset();
            hlsProxy.clear();
            invalidateResourceObservation();
            ijk.setWakeMode(App.get(), PowerManager.PARTIAL_WAKE_LOCK);
            Uri sourceUri = mediaItem.localConfiguration.uri;
            Map<String, String> headers = ExoUtil.extractHeaders(mediaItem);
            String playableUrl = sourceUri.toString();
            resourceClassification = PlaybackResourceClassifier.classifyRequest(playableUrl, mediaItem.localConfiguration.mimeType, mediaItem.localConfiguration.mimeType);
            resourceObservationActive = true;
            boolean dash = isLikelyDash(mediaItem, playableUrl);
            currentDash = dash;
            if (dash) {
                playableUrl = hlsProxy.proxyDash(
                        playableUrl, headers,
                        PlaybackDiskBufferStore.mediaKey(mediaItem));
                SpiderDebug.log("ijk", "proxy action=enabled mode=dash");
            } else if (shouldProxyHls(mediaItem, playableUrl)) {
                playableUrl = hlsProxy.proxy(
                        playableUrl, headers,
                        PlaybackDiskBufferStore.mediaKey(mediaItem));
                SpiderDebug.log("ijk", "proxy action=enabled mode=hls");
            } else {
                IjkLongUrlPolicy.Decision longUrl =
                        IjkLongUrlPolicy.evaluate(playableUrl);
                if (longUrl.proxyRequired()) {
                    playableUrl = hlsProxy.proxyFile(
                            playableUrl,
                            headers,
                            PlaybackDiskBufferStore.mediaKey(mediaItem));
                    longUrlProxied = true;
                    SpiderDebug.log("ijk",
                            "proxy action=enabled mode=file-long-url nativeBytes=%d",
                            longUrl.nativeBytes());
                }
            }
            SpiderDebug.log("ijk",
                    "open dash=%s decode=%d mime=%s headers=%d",
                    dash,
                    decode,
                    mediaItem.localConfiguration.mimeType,
                    headers.size());
            currentPlayableUrl = playableUrl;
            configureOptions(sourceUri, dash);
            bindVideoOutput();
            ijk.setDataSource(App.get(), Uri.parse(playableUrl), headers);
            advanceOpenStage(IjkPlayerEngine.OpenStage.SOURCE_SET);
            ijk.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijk.setScreenOnWhilePlaying(true);
            ijk.setLooping(repeatOne);
            ijk.setSpeed(playbackParameters.speed);
            ijk.prepareAsync();
            invalidateState();
            startStateRefresh();
        } catch (Throwable e) {
            playerError = new PlaybackException(
                    "IJK open failed",
                    e,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            SpiderDebug.log("ijk",
                    "open failed errorType=%s stage=%s http=%d longProxy=%s action=notify",
                    e.getClass().getSimpleName(),
                    openStage.label(),
                    lastHttpStatus,
                    longUrlProxied);
            lastErrorSnapshot = new IjkPlayerEngine.ErrorSnapshot(
                    IMediaPlayer.MEDIA_ERROR_IO,
                    0,
                    prepared,
                    openStage,
                    lastHttpStatus,
                    lastNativeOffset,
                    longUrlProxied);
            playbackState = Player.STATE_IDLE;
            loading = false;
            prepared = false;
            stopStateRefresh();
            invalidateState();
        }
    }

    private void stopInternal(boolean resetState) {
        try {
            if (playbackState != Player.STATE_IDLE) ijk.stop();
        } catch (Throwable ignored) {
        }
        ijk.reset();
        hlsProxy.clear();
        invalidateResourceObservation();
        currentDash = false;
        loading = false;
        bufferingPercent = 0;
        bufferingPositionMs = 0;
        currentTracks = Tracks.EMPTY;
        selectedVideoFormat = null;
        selectedAudioFormat = null;
        videoSize = VideoSize.UNKNOWN;
        prepared = false;
        newlyRenderedFirstFrame = false;
        renderedFirstFrameSeen = false;
        lastErrorSnapshot = IjkPlayerEngine.ErrorSnapshot.none();
        resetOpenDiagnostics();
        if (resetState) playbackState = Player.STATE_IDLE;
        stopStateRefresh();
    }

    private void startStateRefresh() {
        App.post(stateRefreshRunnable, STATE_REFRESH_INTERVAL_MS);
    }

    private void invalidateResourceObservation() {
        resourceObservationActive = false;
        resourceClassification = null;
        currentPlayableUrl = null;
    }

    private void stopStateRefresh() {
        App.removeCallbacks(stateRefreshRunnable);
    }

    private void refreshPlaybackState() {
        if (mediaItem == null || playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null) return;
        requestPreload(Math.max(0, position()));
        invalidateState();
        startStateRefresh();
    }

    private void requestPreload(long positionMs) {
        if (playWhenReady) hlsProxy.preloadAround(positionMs);
        else hlsProxy.preloadWhilePaused(positionMs);
    }

    private void setVideoOutput(Object output) {
        detachSurfaceHolder();
        if (output instanceof SurfaceView view) {
            setSurfaceHolder(view.getHolder());
        } else if (output instanceof TextureView view && view.getSurfaceTexture() != null) {
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
        bindVideoOutput();
    }

    private void setSurfaceHolder(SurfaceHolder holder) {
        surfaceHolder = holder;
        surfaceHolder.addCallback(surfaceCallback);
        surface = surfaceHolder.getSurface();
        ownsSurface = false;
    }

    private void bindVideoOutput() {
        try {
            if (surfaceHolder != null) {
                surface = surfaceHolder.getSurface();
                if (surface != null && surface.isValid()) {
                    SpiderDebug.log("ijk", "bind display holder=%s surface=%s", surfaceHolder, surface);
                    ijk.setDisplay(surfaceHolder);
                }
            } else if (surface != null && surface.isValid()) {
                SpiderDebug.log("ijk", "bind surface surface=%s", surface);
                ijk.setSurface(surface);
            }
        } catch (Throwable e) {
            SpiderDebug.log("ijk",
                    "bind surface failed errorType=%s action=keep-current",
                    e.getClass().getSimpleName());
        }
    }

    private void clearVideoOutput() {
        detachSurfaceHolder();
        releaseOwnedSurface();
        surface = null;
        try {
            ijk.setDisplay(null);
        } catch (Throwable ignored) {
        }
        try {
            ijk.setSurface(null);
        } catch (Throwable ignored) {
        }
    }

    private void detachSurfaceHolder() {
        if (surfaceHolder == null) return;
        try {
            surfaceHolder.removeCallback(surfaceCallback);
        } catch (Throwable ignored) {
        }
        surfaceHolder = null;
    }

    private void releaseOwnedSurface() {
        if (ownsSurface && surface != null) surface.release();
        ownsSurface = false;
    }

    private void configureOptions(Uri uri, boolean dash) {
        String url = uri.toString();
        boolean automaticBuffer = PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER,
                PlaybackPerformanceCatalog.IJK_WATER);
        IjkBufferOptionPolicy.Decision inputBuffer =
                IjkBufferOptionPolicy.resolve(
                        automaticBuffer,
                        automaticInputBufferConfig,
                        url,
                        IjkPerformanceSetting.getScene(),
                        IjkPerformanceSetting.getBufferMb(),
                        PlayerSetting.getBufferBytes(PlayerSetting.IJK),
                        IjkPerformanceSetting.getFirstWaterMs(),
                        IjkPerformanceSetting.getNextWaterMs(),
                        IjkPerformanceSetting.getLastWaterMs());
        appliedInputBufferConfig = inputBuffer.config();
        if (PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER)) {
            appliedInputBufferConfig = new IjkBufferPolicy.Config(
                    IjkPerformanceSetting.getBufferMb(),
                    appliedInputBufferConfig.firstWaterMs(),
                    appliedInputBufferConfig.nextWaterMs(),
                    appliedInputBufferConfig.lastWaterMs());
            inputBuffer = IjkBufferOptionPolicy.withConfig(
                    inputBuffer,
                    appliedInputBufferConfig,
                    PlayerSetting.getBufferBytes(PlayerSetting.IJK));
        }
        if (PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_WATER)) {
            appliedInputBufferConfig = new IjkBufferPolicy.Config(
                    appliedInputBufferConfig.bufferMb(),
                    IjkPerformanceSetting.getFirstWaterMs(),
                    IjkPerformanceSetting.getNextWaterMs(),
                    IjkPerformanceSetting.getLastWaterMs());
            inputBuffer = IjkBufferOptionPolicy.withConfig(
                    inputBuffer,
                    appliedInputBufferConfig,
                    PlayerSetting.getBufferBytes(PlayerSetting.IJK));
        }
        boolean automaticPictureQueue = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE);
        boolean automaticSoftTune = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE);
        appliedDecodeControlConfig = IjkDecodePressurePolicy.prepareConfig(
                automaticPictureQueue || automaticSoftTune,
                automaticDecodeControlConfig,
                decode == PlayerEngine.SOFT,
                IjkPerformanceSetting.getPictureQueue(),
                configuredSoftTuneMode());
        appliedDecodeControlConfig = new IjkDecodePressurePolicy.Config(
                automaticPictureQueue
                        ? appliedDecodeControlConfig.pictureQueue()
                        : IjkPerformanceSetting.getPictureQueue(),
                automaticSoftTune
                        ? appliedDecodeControlConfig.tuneMode()
                        : decode == PlayerEngine.SOFT
                        ? configuredSoftTuneMode()
                        : IjkDecodePressurePolicy.TuneMode.OFF);
        SpiderDebug.log("ijk-buffer",
                "action=prepare mode=%s bufferMb=%d maxBufferBytes=%d firstMs=%d nextMs=%d lastMs=%d realtime=%s infbuf=%s",
                automaticBuffer ? "automatic" : "fixed",
                appliedInputBufferConfig.bufferMb(),
                inputBuffer.maxBufferBytes(),
                appliedInputBufferConfig.firstWaterMs(),
                appliedInputBufferConfig.nextWaterMs(),
                appliedInputBufferConfig.lastWaterMs(),
                inputBuffer.realtime(), inputBuffer.infiniteBuffer());
        if (dash) ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "iformat", "dash");
        configureSoftDecodeOptions(appliedDecodeControlConfig.tuneMode());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
        // SegmentBase MP4 relies on HTTP byte-range seeks for sidx/moof access.
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", dash ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", IjkPerformanceSetting.isAccurateSeek() ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", IjkPerformanceSetting.getFrameDropValue());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", inputBuffer.maxBufferBytes());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", dash ? 0 : (IjkPerformanceSetting.isPacketBuffering() ? 1 : 0));
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-high-water-mark-ms", appliedInputBufferConfig.firstWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", appliedInputBufferConfig.nextWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", appliedInputBufferConfig.lastWaterMs());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", decode);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", IjkPerformanceSetting.isReconnect() ? 1 : 0);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", appliedDecodeControlConfig.pictureQueue());
        SpiderDebug.log("ijk-decode",
                "action=prepare mode=%s requestedDecode=%s pictureQueue=%d tune=%s",
                automaticPictureQueue || automaticSoftTune
                        ? "automatic" : "fixed",
                decode == PlayerEngine.SOFT ? "software" : "hardware",
                appliedDecodeControlConfig.pictureQueue(),
                appliedDecodeControlConfig.tuneMode().label());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "async,cache,crypto,file,http,https,pipe,rtmp,rtp,tcp,tls,udp,data,ijkinject,ijklongurl,ijksegment,ijkhttphook,ijklivehook,ijktcphook,ijkurlhook,ijkmediadatasource");
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", inputBuffer.infiniteBuffer() ? 1 : 0);
        applyProbeOptions();
        applyRtspOptions(url);
    }

    private void configureSoftDecodeOptions(
            IjkDecodePressurePolicy.TuneMode tuneMode) {
        IjkDecodePressurePolicy.TuneMode mode = tuneMode == null
                ? IjkDecodePressurePolicy.TuneMode.OFF : tuneMode;
        if (mode == IjkDecodePressurePolicy.TuneMode.OFF) return;
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", mode.fast());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC,
                "skip_loop_filter", mode.skipLoopFilter());
        ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC,
                "skip_frame", mode.skipFrame());
    }

    private IjkDecodePressurePolicy.TuneMode configuredSoftTuneMode() {
        return switch (IjkPerformanceSetting.getSoftTuneMode()) {
            case IjkPerformanceSetting.SOFT_TUNE_AGGRESSIVE ->
                    IjkDecodePressurePolicy.TuneMode.AGGRESSIVE;
            case IjkPerformanceSetting.SOFT_TUNE_MILD ->
                    IjkDecodePressurePolicy.TuneMode.MILD;
            default -> IjkDecodePressurePolicy.TuneMode.OFF;
        };
    }

    private void applyProbeOptions() {
        if (IjkPerformanceSetting.getProbeMode() == IjkPerformanceSetting.PROBE_FAST) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512_000);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2_000_000);
        } else if (IjkPerformanceSetting.getProbeMode() == IjkPerformanceSetting.PROBE_FULL) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 5_000_000);
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 10_000_000);
        }
    }

    private void applyRtspOptions(String url) {
        if (!url.toLowerCase(Locale.US).startsWith("rtsp")) return;
        if (IjkPerformanceSetting.getRtspTransport() == IjkPerformanceSetting.RTSP_TCP) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_flags", "prefer_tcp");
        } else if (IjkPerformanceSetting.getRtspTransport() == IjkPerformanceSetting.RTSP_UDP) {
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "udp");
        }
    }

    private boolean shouldProxyHls(MediaItem item, String uri) {
        if (!isLikelyHls(item, uri) || TextUtils.isEmpty(uri)) return false;
        Uri parsed = Uri.parse(uri);
        String scheme = parsed.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return !"/mpv/index.m3u8".equals(parsed.getPath()) && !"/mpv/item".equals(parsed.getPath());
    }

    private boolean isLikelyHls(MediaItem item, String uri) {
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

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surface = holder.getSurface();
            bindVideoOutput();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surface = holder.getSurface();
            bindVideoOutput();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surface = null;
            try {
                ijk.setDisplay(null);
            } catch (Throwable ignored) {
            }
        }
    };

    private long duration() {
        long duration = safeDuration();
        return duration > 0 ? duration : C.TIME_UNSET;
    }

    PlaybackResourceClassifier.Classification getResourceClassification() {
        PlaybackResourceClassifier.Classification current = resourceClassification;
        PlaybackResourceClassifier.Classification proxy = resourceObservationActive
                ? hlsProxy.resourceClassification() : null;
        if (proxy != null) current = PlaybackResourceClassifier.merge(current, proxy);
        return current;
    }

    IjkStreamScenePolicy.Decision getStreamSceneDecision() {
        return streamSceneDecision(duration());
    }

    private IjkStreamScenePolicy.Decision streamSceneDecision(long durationMs) {
        return IjkStreamScenePolicy.resolve(
                getResourceClassification(), durationMs, SystemClock.elapsedRealtime());
    }

    PlaybackRoute.Resolution getPlaybackRouteResolution() {
        return PlaybackRoute.resolve(currentPlayableUrl);
    }

    private long safeDuration() {
        try {
            return ijk.getDuration();
        } catch (Throwable ignored) {
            return C.TIME_UNSET;
        }
    }

    private long position() {
        try {
            long actual = Math.max(0, ijk.getCurrentPosition());
            if (pendingSeekPositionMs == C.TIME_UNSET) return actual;
            boolean reached = Math.abs(actual - pendingSeekPositionMs) <= 1500;
            boolean expired = pendingSeekRequestedAtMs != C.TIME_UNSET && SystemClock.elapsedRealtime() - pendingSeekRequestedAtMs >= 15_000;
            if (reached || expired) {
                setPendingSeek(C.TIME_UNSET);
                return actual;
            }
            return pendingSeekPositionMs;
        } catch (Throwable ignored) {
            return pendingSeekPositionMs == C.TIME_UNSET ? 0 : pendingSeekPositionMs;
        }
    }

    private void setPendingSeek(long positionMs) {
        pendingSeekPositionMs = positionMs;
        pendingSeekRequestedAtMs = positionMs == C.TIME_UNSET ? C.TIME_UNSET : SystemClock.elapsedRealtime();
    }

    private long bufferedPosition(long position, long duration) {
        return IjkBufferedDurationPolicy.bufferedPosition(
                position, duration, bufferingPercent,
                getNativeBufferedDurationSnapshot(), bufferingPositionMs);
    }

    private boolean isPlayingInternal() {
        try {
            return playbackState == Player.STATE_READY && ijk.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void refreshTracks() {
        try {
            List<ITrackInfo> infos = ijk.getTrackInfo();
            if (infos == null || infos.isEmpty()) {
                currentTracks = Tracks.EMPTY;
                selectedVideoFormat = null;
                selectedAudioFormat = null;
                return;
            }
            List<Tracks.Group> groups = new java.util.ArrayList<>();
            int selectedVideoStream = selectedStream(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
            int selectedAudioStream = selectedStream(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
            Format actualVideo = null;
            Format actualAudio = null;
            boolean selectedVideo = false;
            boolean selectedAudio = false;
            boolean selectedText = false;
            int index = 0;
            for (ITrackInfo info : infos) {
                int type = mediaTrackType(info.getTrackType());
                if (type == C.TRACK_TYPE_UNKNOWN) continue;
                boolean selected = switch (type) {
                    case C.TRACK_TYPE_VIDEO -> !selectedVideo;
                    case C.TRACK_TYPE_AUDIO -> !selectedAudio;
                    case C.TRACK_TYPE_TEXT -> !selectedText;
                    default -> false;
                };
                if (selected) {
                    if (type == C.TRACK_TYPE_VIDEO) selectedVideo = true;
                    else if (type == C.TRACK_TYPE_AUDIO) selectedAudio = true;
                    else if (type == C.TRACK_TYPE_TEXT) selectedText = true;
                }
                Format format = buildFormat(info, type, ++index);
                if (type == C.TRACK_TYPE_VIDEO && info.getStreamIndex() == selectedVideoStream) actualVideo = format;
                if (type == C.TRACK_TYPE_AUDIO && info.getStreamIndex() == selectedAudioStream) actualAudio = format;
                TrackGroup group = new TrackGroup("ijk:" + type + ":" + index, format);
                groups.add(new Tracks.Group(group, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
            }
            currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
            selectedVideoFormat = actualVideo;
            selectedAudioFormat = actualAudio;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "tracks refreshed count=%d groups=%d", infos.size(), groups.size());
        } catch (Throwable e) {
            currentTracks = Tracks.EMPTY;
            selectedVideoFormat = null;
            selectedAudioFormat = null;
            SpiderDebug.log("ijk", "tracks refresh failed type=%s", e.getClass().getSimpleName());
        }
    }

    private int selectedStream(int type) {
        try {
            return ijk.getSelectedTrack(type);
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("ijk", "selected track fact unavailable type=%s", error.getClass().getSimpleName());
            return -1;
        }
    }

    private int mediaTrackType(int ijkType) {
        return switch (ijkType) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> C.TRACK_TYPE_VIDEO;
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> C.TRACK_TYPE_AUDIO;
            case ITrackInfo.MEDIA_TRACK_TYPE_TEXT -> C.TRACK_TYPE_TEXT;
            default -> C.TRACK_TYPE_UNKNOWN;
        };
    }

    private Format buildFormat(ITrackInfo info, int type, int index) {
        String codec = info.getMimeType();
        Format.Builder builder = new Format.Builder()
                .setId(type + ":" + index)
                .setLabel(trackLabel(type, index))
                .setCodecs(TextUtils.isEmpty(codec) ? null : codec)
                .setLanguage(TextUtils.isEmpty(info.getLanguage()) ? null : info.getLanguage())
                .setSampleMimeType(sampleMimeType(type, codec));
        if (type == C.TRACK_TYPE_VIDEO) {
            int width = info.getWidth() > 0 ? info.getWidth() : videoSize.width;
            int height = info.getHeight() > 0 ? info.getHeight() : videoSize.height;
            if (width > 0) builder.setWidth(width);
            if (height > 0) builder.setHeight(height);
            if (info.getFps() > 0) builder.setFrameRate(info.getFps());
            ColorInfo colorInfo = colorInfo(info);
            if (colorInfo != null) builder.setColorInfo(colorInfo);
        } else if (type == C.TRACK_TYPE_AUDIO) {
            if (info.getChannelCount() > 0) builder.setChannelCount(info.getChannelCount());
        }
        int bitrate = info.getBitrate();
        if (bitrate <= 0 && type == C.TRACK_TYPE_VIDEO) bitrate = safeIntBitrate(ijk.getBitRate());
        if (bitrate > 0) builder.setAverageBitrate(bitrate);
        return builder.build();
    }

    @Nullable
    private ColorInfo colorInfo(ITrackInfo info) {
        int colorSpace = colorSpace(info);
        int colorRange = colorRange(info);
        int colorTransfer = colorTransfer(info);
        if (colorSpace == C.LENGTH_UNSET && colorRange == C.LENGTH_UNSET && colorTransfer == C.LENGTH_UNSET) return null;
        ColorInfo.Builder builder = new ColorInfo.Builder();
        if (colorSpace != C.LENGTH_UNSET) builder.setColorSpace(colorSpace);
        if (colorRange != C.LENGTH_UNSET) builder.setColorRange(colorRange);
        if (colorTransfer != C.LENGTH_UNSET) builder.setColorTransfer(colorTransfer);
        return builder.build();
    }

    private int colorSpace(ITrackInfo info) {
        String value = lower(joinColor(info.getColorPrimaries(), info.getColorSpace()));
        if (value.contains("bt2020") || value.contains("bt.2020") || value.contains("2020")) return C.COLOR_SPACE_BT2020;
        if (value.contains("bt709") || value.contains("bt.709") || value.contains("709")) return C.COLOR_SPACE_BT709;
        if (value.contains("bt601") || value.contains("bt.601") || value.contains("601") || value.contains("smpte170m") || value.contains("smpte-170m")) return C.COLOR_SPACE_BT601;
        return C.LENGTH_UNSET;
    }

    private int colorRange(ITrackInfo info) {
        String value = lower(info.getColorRange());
        if (value.contains("jpeg") || value.contains("pc") || value.contains("full")) return C.COLOR_RANGE_FULL;
        if (value.contains("mpeg") || value.contains("tv") || value.contains("limited")) return C.COLOR_RANGE_LIMITED;
        return C.LENGTH_UNSET;
    }

    private int colorTransfer(ITrackInfo info) {
        String value = lower(info.getColorTransfer());
        if (value.contains("smpte2084") || value.contains("st2084") || value.contains("pq")) return C.COLOR_TRANSFER_ST2084;
        if (value.contains("arib-std-b67") || value.contains("hlg")) return C.COLOR_TRANSFER_HLG;
        if (value.contains("iec61966") || value.contains("srgb")) return C.COLOR_TRANSFER_SRGB;
        if (value.contains("linear")) return C.COLOR_TRANSFER_LINEAR;
        if (value.contains("bt709") || value.contains("bt.709") || value.contains("bt601") || value.contains("bt.601") || value.contains("smpte170m") || value.contains("smpte-170m")) return C.COLOR_TRANSFER_SDR;
        return C.LENGTH_UNSET;
    }

    private String joinColor(String first, String second) {
        return (first == null ? "" : first) + " " + (second == null ? "" : second);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private int safeIntBitrate(long bitrate) {
        return bitrate > 0 && bitrate <= Integer.MAX_VALUE ? (int) bitrate : 0;
    }

    private String trackLabel(int type, int index) {
        return switch (type) {
            case C.TRACK_TYPE_VIDEO -> "Video " + index;
            case C.TRACK_TYPE_AUDIO -> "Audio " + index;
            case C.TRACK_TYPE_TEXT -> "Subtitle " + index;
            default -> "Track " + index;
        };
    }

    private String sampleMimeType(int type, String codec) {
        String value = codec == null ? "" : codec.toLowerCase(Locale.US);
        if (type == C.TRACK_TYPE_TEXT) {
            if (value.contains("pgs") || value.contains("hdmv")) return MimeTypes.APPLICATION_PGS;
            if (value.contains("dvd") || value.contains("vobsub")) return MimeTypes.APPLICATION_VOBSUB;
            if (value.contains("dvb")) return MimeTypes.APPLICATION_DVBSUBS;
            if (value.contains("ass") || value.contains("ssa")) return MimeTypes.TEXT_SSA;
            if (value.contains("webvtt") || value.contains("vtt")) return MimeTypes.TEXT_VTT;
            if (value.contains("srt") || value.contains("subrip")) return MimeTypes.APPLICATION_SUBRIP;
            if (value.contains("ttml")) return MimeTypes.APPLICATION_TTML;
            return TextUtils.isEmpty(value) ? MimeTypes.TEXT_UNKNOWN : MimeTypes.BASE_TYPE_TEXT + "/" + value;
        }
        if (type == C.TRACK_TYPE_AUDIO) {
            if (value.contains("aac")) return MimeTypes.AUDIO_AAC;
            if (value.contains("ac3")) return MimeTypes.AUDIO_AC3;
            if (value.contains("eac3") || value.contains("e-ac-3")) return MimeTypes.AUDIO_E_AC3;
            if (value.contains("opus")) return MimeTypes.AUDIO_OPUS;
            if (value.contains("vorbis")) return MimeTypes.AUDIO_VORBIS;
            if (value.contains("flac")) return MimeTypes.AUDIO_FLAC;
            if (value.contains("mp3") || value.contains("mpeg")) return MimeTypes.AUDIO_MPEG;
            return TextUtils.isEmpty(value) ? null : MimeTypes.BASE_TYPE_AUDIO + "/" + value;
        }
        if (value.contains("hevc") || value.contains("h265")) return MimeTypes.VIDEO_H265;
        if (value.contains("h264") || value.contains("avc")) return MimeTypes.VIDEO_H264;
        if (value.contains("av1")) return MimeTypes.VIDEO_AV1;
        if (value.contains("vp9")) return MimeTypes.VIDEO_VP9;
        if (value.contains("vp8")) return MimeTypes.VIDEO_VP8;
        if (value.contains("mpeg2")) return MimeTypes.VIDEO_MPEG2;
        return TextUtils.isEmpty(value) ? null : MimeTypes.BASE_TYPE_VIDEO + "/" + value;
    }

    private synchronized void resetOpenDiagnostics() {
        openStage = IjkPlayerEngine.OpenStage.NONE;
        lastHttpStatus = 0;
        lastNativeOffset = -1;
        longUrlProxied = false;
    }

    private synchronized void advanceOpenStage(
            IjkPlayerEngine.OpenStage stage) {
        if (stage != null && stage.ordinal() > openStage.ordinal()) {
            openStage = stage;
        }
    }

    private static int bundleNumber(Bundle bundle, String key, int fallback) {
        Object value = bundle == null ? null : bundle.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long bundleLong(Bundle bundle, String key, long fallback) {
        Object value = bundle == null ? null : bundle.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

}
