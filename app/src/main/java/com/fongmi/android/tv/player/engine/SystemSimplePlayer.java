package com.fongmi.android.tv.player.engine;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
class SystemSimplePlayer extends SimpleBasePlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnVideoSizeChangedListener {

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

    private final MediaPlayer mediaPlayer;
    private MediaItem mediaItem;
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private Object videoOutput;
    private PlaybackParameters playbackParameters;
    private PlaybackException playerError;
    private VideoSize videoSize;
    private int playbackState;
    private int bufferingPercent;
    private long pendingSeekPositionMs;
    private boolean playWhenReady;
    private boolean loading;
    private boolean repeatOne;
    private boolean ownsSurface;
    private float volume;

    SystemSimplePlayer() {
        super(Looper.getMainLooper());
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnVideoSizeChangedListener(this);
        playbackParameters = PlaybackParameters.DEFAULT;
        videoSize = VideoSize.UNKNOWN;
        playbackState = Player.STATE_IDLE;
        pendingSeekPositionMs = C.TIME_UNSET;
        playWhenReady = true;
        volume = 1f;
    }

    @Override
    protected State getState() {
        State.Builder builder = new State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(playbackState)
                .setIsLoading(SystemPlayerState.loadingFor(playbackState, loading))
                .setPlayerError(playerError)
                .setRepeatMode(repeatOne ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF)
                .setPlaybackParameters(playbackParameters)
                .setVideoSize(videoSize)
                .setVolume(volume)
                .setPlaylist(mediaItem == null ? ImmutableList.of() : ImmutableList.of(mediaItemData()))
                .setCurrentMediaItemIndex(mediaItem == null ? C.INDEX_UNSET : 0);
        if (mediaItem != null) {
            long duration = duration();
            long position = position();
            builder.setContentPositionMs(isPlayingInternal() ? PositionSupplier.getExtrapolating(position, playbackParameters.speed) : PositionSupplier.getConstant(position));
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(bufferedPosition(duration)));
            builder.setTotalBufferedDurationMs(PositionSupplier.getConstant(Math.max(0, bufferedPosition(duration) - position)));
        }
        return builder.build();
    }

    private MediaItemData mediaItemData() {
        long duration = duration();
        return new MediaItemData.Builder(mediaItem.mediaId)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .setDurationUs(duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000)
                .setIsSeekable(duration > 0)
                .setIsDynamic(duration == C.TIME_UNSET)
                .setTracks(Tracks.EMPTY)
                .build();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        pendingSeekPositionMs = mediaItem != null && startPositionMs > 0 ? startPositionMs : C.TIME_UNSET;
        playbackState = Player.STATE_IDLE;
        loading = false;
        playerError = null;
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleReplaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        mediaItem = mediaItems.isEmpty() ? null : mediaItems.get(0);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
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
        if (playbackState == Player.STATE_READY) {
            if (playWhenReady) mediaPlayer.start();
            else mediaPlayer.pause();
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        stopInternal(true);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        stopInternal(false);
        mediaPlayer.release();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
        repeatOne = repeatMode == Player.REPEAT_MODE_ONE;
        mediaPlayer.setLooping(repeatOne);
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (positionMs == C.TIME_UNSET) positionMs = 0;
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            mediaPlayer.seekTo((int) Math.min(positionMs, Integer.MAX_VALUE));
        } else {
            pendingSeekPositionMs = positionMs;
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        applyPlaybackParameters();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume, int volumeOperationType) {
        this.volume = volume;
        mediaPlayer.setVolume(volume, volume);
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
    public void onPrepared(MediaPlayer mp) {
        playbackState = Player.STATE_READY;
        loading = false;
        playerError = null;
        applyPlaybackParameters();
        if (pendingSeekPositionMs != C.TIME_UNSET) {
            mediaPlayer.seekTo((int) Math.min(pendingSeekPositionMs, Integer.MAX_VALUE));
            pendingSeekPositionMs = C.TIME_UNSET;
        }
        if (playWhenReady) mediaPlayer.start();
        invalidateState();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playbackState = Player.STATE_ENDED;
        loading = false;
        invalidateState();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        playbackState = Player.STATE_IDLE;
        loading = false;
        playerError = new PlaybackException("System player error: " + what + ", " + extra, null, errorCode(what));
        invalidateState();
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            loading = true;
            playbackState = Player.STATE_BUFFERING;
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            loading = false;
            playbackState = Player.STATE_READY;
        }
        invalidateState();
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferingPercent = percent;
        invalidateState();
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        videoSize = new VideoSize(width, height);
        invalidateState();
    }

    private void openCurrent() {
        if (mediaItem == null || mediaItem.localConfiguration == null) return;
        try {
            playbackState = Player.STATE_BUFFERING;
            loading = true;
            playerError = null;
            mediaPlayer.reset();
            bindVideoOutput();
            mediaPlayer.setDataSource(App.get(), mediaItem.localConfiguration.uri, headersWithRange());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setScreenOnWhilePlaying(true);
            mediaPlayer.setLooping(repeatOne);
            mediaPlayer.setVolume(volume, volume);
            mediaPlayer.prepareAsync();
            invalidateState();
        } catch (Throwable e) {
            playerError = new PlaybackException(e.getMessage(), e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            playbackState = Player.STATE_IDLE;
            loading = false;
            invalidateState();
        }
    }

    private Map<String, String> headersWithRange() {
        Map<String, String> headers = new HashMap<>(ExoUtil.extractHeaders(mediaItem));
        headers.putIfAbsent(HttpHeaders.RANGE, "bytes=0-");
        return headers;
    }

    private void stopInternal(boolean resetState) {
        try {
            if (playbackState != Player.STATE_IDLE) mediaPlayer.stop();
        } catch (Throwable ignored) {
        }
        mediaPlayer.reset();
        loading = false;
        bufferingPercent = 0;
        videoSize = VideoSize.UNKNOWN;
        if (resetState) playbackState = Player.STATE_IDLE;
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
                if (surface != null && surface.isValid()) mediaPlayer.setDisplay(surfaceHolder);
            } else if (surface != null && surface.isValid()) {
                mediaPlayer.setSurface(surface);
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearVideoOutput() {
        detachSurfaceHolder();
        releaseOwnedSurface();
        surface = null;
        mediaPlayer.setSurface(null);
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

    private void applyPlaybackParameters() {
        if (playbackState != Player.STATE_READY) return;
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.setSpeed(playbackParameters.speed);
            params.setPitch(playbackParameters.pitch);
            mediaPlayer.setPlaybackParams(params);
        } catch (Throwable ignored) {
        }
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
                mediaPlayer.setDisplay(null);
            } catch (Throwable ignored) {
            }
        }
    };

    private long duration() {
        long duration = safeDuration();
        return duration > 0 ? duration : C.TIME_UNSET;
    }

    private long safeDuration() {
        if (!canQueryPlaybackPosition()) return C.TIME_UNSET;
        try {
            return mediaPlayer.getDuration();
        } catch (Throwable ignored) {
            return C.TIME_UNSET;
        }
    }

    private long position() {
        if (!canQueryPlaybackPosition()) return pendingSeekPositionMs == C.TIME_UNSET ? 0 : Math.max(0, pendingSeekPositionMs);
        try {
            return Math.max(0, mediaPlayer.getCurrentPosition());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private long bufferedPosition(long duration) {
        if (duration == C.TIME_UNSET || duration <= 0) return position();
        return Math.min(duration, duration * bufferingPercent / 100);
    }

    private boolean isPlayingInternal() {
        try {
            return playbackState == Player.STATE_READY && mediaPlayer.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean canQueryPlaybackPosition() {
        return playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED;
    }

    private int errorCode(int what) {
        return switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
            case MediaPlayer.MEDIA_ERROR_MALFORMED -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT -> PlaybackException.ERROR_CODE_TIMEOUT;
            default -> PlaybackException.ERROR_CODE_UNSPECIFIED;
        };
    }
}
