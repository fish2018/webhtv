package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackTelemetry;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.exo.ExoOutputModeManager;
import com.fongmi.android.tv.player.exo.ExoOutputModePolicy;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private static final String SIZE_TAG = "MPV_SIZE";

    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean audioOnly;
    private boolean redirect;
    private boolean playbackExiting;
    private boolean nativeOutputPending;
    private boolean bound;
    private boolean stop;
    private boolean lock;
    private int render = -1;
    private int requestedResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private ExoOutputModeManager exoOutputModeManager;

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    protected PlayerManager player() {
        return mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isPlaybackExiting() {
        return playbackExiting;
    }

    protected void markPlaybackExiting() {
        this.playbackExiting = true;
    }

    protected void finishPlayback() {
        markPlaybackExiting();
        stopPlayback();
        finish();
    }

    protected void stopPlayback() {
        if (mService != null && isOwner()) {
            mService.shutdown();
        } else if (mController != null) {
            mController.stop();
        }
    }

    protected void updateNavigationKey() {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract CustomSeekView getSeekView();

    protected abstract PlayerView getExoView();

    protected abstract String getPlaybackKey();

    protected boolean deferPlaybackServiceBinding() {
        return false;
    }

    protected boolean isOwner() {
        String key = getPlaybackKey();
        return key == null || (mService != null && key.equals(player().getKey()));
    }

    protected boolean isIdle() {
        return mController.getPlaybackState() == Player.STATE_IDLE;
    }

    protected boolean isEnded() {
        return mController.getPlaybackState() == Player.STATE_ENDED;
    }

    protected boolean isBuffering() {
        return mController.getPlaybackState() == Player.STATE_BUFFERING;
    }

    protected boolean isPaused() {
        return !isBuffering() && !isIdle();
    }

    protected void onServiceConnected() {
    }

    protected boolean isLutAllowed() {
        return true;
    }

    protected void onPrepare() {
    }

    protected void onExoFirstFrame() {
        View progress = getExoView().getRootView().findViewById(R.id.progress);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    protected void onTracksChanged() {
    }

    protected void onTitlesChanged() {
    }

    protected void onPlayerRebuilt() {
    }

    protected void onControllerReady(Player controller) {
    }

    protected void onPlayerPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
    }

    protected void onError(String msg) {
    }

    protected void onReload(String msg) {
        onError(msg);
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onSurfaceAttached() {
    }

    protected void applyResizeMode(int resizeMode) {
        requestedResizeMode = resizeMode;
        int effectiveResizeMode = effectiveResizeMode(resizeMode);
        logSurfaceState("applyResizeMode before mode=" + resizeMode + " effective=" + effectiveResizeMode);
        PlayerView view = getExoView();
        view.setResizeMode(effectiveResizeMode);
        view.requestLayout();
        View surface = view.getVideoSurfaceView();
        if (surface != null) surface.requestLayout();
        logSurfaceState("applyResizeMode after mode=" + resizeMode + " effective=" + effectiveResizeMode);
    }

    private int effectiveResizeMode(int resizeMode) {
        if (mService != null && player().isMpv() && !player().isMpvSurfaceDirect() && resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            return AspectRatioFrameLayout.RESIZE_MODE_FILL;
        }
        return resizeMode;
    }

    protected void onReclaim() {
    }

    protected boolean seekTo(long deltaMs) {
        long targetMs = Math.max(0, player().getPosition() + deltaMs);
        long durationMs = player().getDuration();
        boolean seekToEnd = durationMs > 0 && targetMs >= durationMs;
        mController.seekTo(seekToEnd ? durationMs : targetMs);
        if (!seekToEnd) mController.play();
        return seekToEnd;
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        startPlayer(key, result, useParse, timeout, metadata, C.TIME_UNSET);
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout,
                               MediaMetadata metadata, long startPositionMs) {
        if (rejectUnsupportedDrm(key, result)) {
            return;
        } else if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            onError(ResUtil.getString(R.string.error_play_drm));
        } else if (result.hasMsg()) {
            onError(result.getMsg());
        } else if (result.getRealUrl().isEmpty()) {
            onError(ResUtil.getString(R.string.error_play_url));
        } else if (result.needParse() || useParse) {
            attachSurface();
            player().parse(key, result, useParse, metadata, PlayerSetting.isAutoPlay(), startPositionMs);
        } else {
            attachSurface();
            player().start(PlaySpec.from(result, key, metadata, PlayerSetting.getPlayer()), timeout, PlayerSetting.isAutoPlay(), startPositionMs);
        }
    }

    private boolean rejectUnsupportedDrm(String key, Result result) {
        if (result == null || result.getDrm() == null || !isSelectedMpvPlayer()) return false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "reject drm for mpv key=%s drm=%s", key, result.getDrm().getType());
        onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
        return true;
    }

    private boolean isSelectedMpvPlayer() {
        return mService != null ? player().isMpv() : PlayerSetting.getPlayer() == PlayerSetting.MPV;
    }

    private void bindPlaybackService() {
        if (bound) return;
        long start = System.currentTimeMillis();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service start key=%s", getPlaybackKey());
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "bind service requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private void bindPlaybackServiceAfterFirstFrame() {
        View root = getExoView().getRootView();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(() -> {
                    if (!isFinishing() && !isDestroyed()) bindPlaybackService();
                });
                return true;
            }
        });
    }

    private void buildControllerAsync() {
        long start = System.currentTimeMillis();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected, ContextCompat.getMainExecutor(this));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller build requested cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private void onControllerConnected() {
        long start = System.currentTimeMillis();
        try {
            mController = mControllerFuture.get();
            getSeekView().setPlayer(mController);
            onControllerReady(mController);
            mController.addListener(this);
        } catch (Exception ignored) {
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "controller connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Bundle extras = getIntent().getExtras();
        if (extras != null) intent.putExtras(extras);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    private void attachSurface() {
        if (mService == null) return;
        int targetRender = getRender();
        logSurfaceState("attach start target=" + targetRender);
        syncShutter(true);
        if (render != targetRender) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "switch render from=%d to=%d", render, targetRender);
            if (getExoView().getPlayer() != null) getExoView().setPlayer(null);
            getExoView().setRender(targetRender);
            render = targetRender;
            syncShutter(true);
            logSurfaceState("attach after setRender target=" + targetRender);
        }
        if (getExoView().getPlayer() == null) {
            getExoView().setPlayer(player().getPlayer());
            logSurfaceState("attach after setPlayer");
            syncVideoSurfaceSize(null);
            syncShutter();
            if (player().isNativePlayer()) getExoView().post(this::syncShutter);
        }
        publishRenderTarget(getExoView().getVideoSurfaceView());
        onSurfaceAttached();
        logSurfaceState("attach done");
    }

    private void syncVideoSurfaceSize(VideoSize size) {
        if (mService == null) return;
        View surface = getExoView().getVideoSurfaceView();
        if (!(surface instanceof SurfaceView surfaceView)) return;
        if (!PlaybackPerformanceSetting.isSurfaceFixedSizeEnabled() || getRender() != PlayerSetting.RENDER_SURFACE || player().isNativePlayer()) {
            surfaceView.getHolder().setSizeFromLayout();
            logSurfaceState("syncVideoSurfaceSize layout size=" + (size == null ? "null" : size.width + "x" + size.height));
            return;
        }
        int width = size != null && size.width > 0 ? size.width : player().getVideoWidth();
        int height = size != null && size.height > 0 ? size.height : player().getVideoHeight();
        if (width <= 0 || height <= 0) return;
        ExoUtil.EnhancedVideoProfile profile = ExoUtil.getEnhancedVideoProfile();
        float scale = Math.min((float) profile.width() / width, (float) profile.height() / height);
        if (scale < 1f) {
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        surfaceView.getHolder().setFixedSize(width, height);
        logSurfaceState("syncVideoSurfaceSize fixed=" + width + "x" + height);
    }

    private void logSurfaceState(String step) {
        PlayerView view = getExoView();
        if (view == null) return;
        View surface = view.getVideoSurfaceView();
        View content = view.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        String playerText = mService == null ? "none" : player().getPlayerText();
        boolean nativePlayer = mService != null && player().isNativePlayer();
        int targetRender = mService == null ? -1 : getRender();
        String message = "playback " + step
                + " key=" + getPlaybackKey()
                + " player=" + playerText
                + " native=" + nativePlayer
                + " render=" + render
                + " target=" + targetRender
                + " resize=" + view.getResizeMode()
                + " playerView=" + viewSize(view)
                + " content=" + viewSize(content)
                + " surface=" + surfaceName(surface) + ":" + viewSize(surface)
                + " holder=" + surfaceHolderSize(surface)
                + " rotation=" + (getDisplay() == null ? -1 : getDisplay().getRotation())
                + " orientation=" + getResources().getConfiguration().orientation;
        Log.d(SIZE_TAG, message);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("surface-size", "%s", message);
    }

    private static String viewSize(View view) {
        if (view == null) return "null";
        return view.getWidth() + "x" + view.getHeight();
    }

    private static String surfaceName(View view) {
        return view == null ? "null" : view.getClass().getSimpleName();
    }

    private static String surfaceHolderSize(View view) {
        if (!(view instanceof SurfaceView surfaceView)) return "n/a";
        android.graphics.Rect frame = surfaceView.getHolder().getSurfaceFrame();
        return frame.width() + "x" + frame.height() + "/valid=" + surfaceView.getHolder().getSurface().isValid();
    }

    private void syncShutter() {
        syncShutter(false);
    }

    private void syncShutter(boolean restoreExo) {
        if (mService == null) return;
        boolean nativePlayer = player().isNativePlayer();
        View shutter = getExoView().findViewById(androidx.media3.ui.R.id.exo_shutter);
        if (nativePlayer) {
            boolean keepClosed = nativeOutputPending
                    || player().shouldKeepVideoShutterClosed();
            View videoSurface = getExoView().getVideoSurfaceView();
            // Native MPV uses SurfaceView, which is composed above the normal
            // PlayerView shutter. Alpha hides its buffer without replacing or
            // detaching the Surface while automatic output is being decided.
            if (videoSurface != null) videoSurface.setAlpha(keepClosed ? 0f : 1f);
            getExoView().setShutterBackgroundColor(keepClosed ? Color.BLACK : Color.TRANSPARENT);
            if (shutter != null) shutter.setVisibility(keepClosed ? View.VISIBLE : View.GONE);
        } else if (restoreExo) {
            View videoSurface = getExoView().getVideoSurfaceView();
            if (videoSurface != null) videoSurface.setAlpha(1f);
            getExoView().setShutterBackgroundColor(Color.BLACK);
            if (shutter != null) shutter.setVisibility(View.VISIBLE);
        }
    }

    private void detachSurface() {
        getExoView().setPlayer(null);
        if (mService != null) player().publishPlaybackRenderTarget(PlaybackAutoContext.RenderTarget.DETACHED);
    }

    private void resetVideoSurfaceForDecoderSwitch() {
        int targetRender = getRender();
        int temporaryRender = targetRender == PlayerSetting.RENDER_TEXTURE ? PlayerSetting.RENDER_SURFACE : PlayerSetting.RENDER_TEXTURE;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "reset video surface for decoder switch temp=%d target=%d", temporaryRender, targetRender);
        getExoView().setPlayer(null);
        getExoView().setRender(temporaryRender);
        getExoView().setRender(targetRender);
        render = -1;
    }

    protected void setRender() {
        render = -1;
        detachSurface();
        attachSurface();
    }

    private int getRender() {
        if (mService != null && player().isNativePlayer()) return 0;
        if (mService != null && player().requiresTextureRenderForLut()) return PlayerSetting.RENDER_TEXTURE;
        return PlayerSetting.getRender();
    }

    private void releasePlaybackService() {
        if (mService != null) releaseService(isOwner());
        detach();
    }

    private void releaseService(boolean owner) {
        mService.removePlayerCallback(mPlayerCallback);
        if (owner) mService.setNavigationCallback(null, null);
        if (mService.hasExternalClient() || mService.hasPlayerCallback()) {
            if (owner) mService.suspend();
            mService.resetSessionActivity();
        } else if (owner) {
            mService.shutdown();
        }
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        getSeekView().setProgressPlayer(null);
        unbindService(this);
        mService = null;
    }

    private String lifecycleState() {
        String playerKey = null;
        boolean released = true;
        if (mService != null && mService.player() != null) {
            released = mService.player().isReleased();
            if (!released) playerKey = mService.player().getKey();
        }
        return "activity=" + getClass().getSimpleName() +
                " key=" + getPlaybackKey() +
                " playerKey=" + playerKey +
                " owner=" + isOwner() +
                " bound=" + bound +
                " service=" + (mService != null) +
                " controller=" + (mController != null) +
                " released=" + released +
                " redirect=" + redirect +
                " stop=" + stop +
                " finishing=" + isFinishing() +
                " destroyed=" + isDestroyed() +
                " keepScreen=" + ((getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
    }

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (isOwner()) PlaybackActivity.this.onTracksChanged();
        }

        @Override
        public void onTitlesChanged() {
            if (isOwner()) PlaybackActivity.this.onTitlesChanged();
        }

        @Override
        public void onError(String msg) {
            if (isOwner()) PlaybackActivity.this.onError(msg);
        }

        @Override
        public void onReload(String msg) {
            if (isOwner()) PlaybackActivity.this.onReload(msg);
        }

        @Override
        public void onPlayerRenderRequired() {
            if (!isOwner()) return;
            int targetRender = getRender();
            if (render == targetRender) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "LUT switch render from=%d to=%d", render, targetRender);
            setRender();
            applyResizeMode(requestedResizeMode);
        }

        @Override
        public void onPlayerOutputPending() {
            if (!isOwner()) return;
            nativeOutputPending = true;
            syncShutter();
        }

        @Override
        public void onPlayerOutputReady() {
            if (!isOwner()) return;
            nativeOutputPending = false;
            syncShutter();
        }

        @Override
        public void onExoFirstFrame() {
            if (!isOwner() || !player().isExo()) return;
            View shutter = getExoView().findViewById(androidx.media3.ui.R.id.exo_shutter);
            if (shutter != null) shutter.setVisibility(View.INVISIBLE);
            getExoView().setShutterBackgroundColor(Color.TRANSPARENT);
            PlaybackActivity.this.onExoFirstFrame();
        }

        @Override
        public void onPlayerRebuild(Player player, boolean resetVideoSurface) {
            if (isOwner()) {
                nativeOutputPending = player().shouldKeepVideoShutterClosed();
                getSeekView().setProgressPlayer(player);
                if (resetVideoSurface) resetVideoSurfaceForDecoderSwitch();
                setRender();
                applyResizeMode(requestedResizeMode);
                PlaybackActivity.this.onPlayerRebuilt();
            }
        }
    };

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        super.initView(savedInstanceState);
        ExoUtil.setPlayerView(getExoView());
        exoOutputModeManager = new ExoOutputModeManager(getWindow());
        if (deferPlaybackServiceBinding()) bindPlaybackServiceAfterFirstFrame();
        else bindPlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "initView cost=%dms key=%s deferred=%s", System.currentTimeMillis() - start, getPlaybackKey(), deferPlaybackServiceBinding());
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isOwner()) return;
        if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (!isBuffering()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "playing changed isPlaying=%s state=%d %s", isPlaying, mController == null ? -1 : mController.getPlaybackState(), lifecycleState());
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (isOwner()) onPlayerPositionDiscontinuity(oldPosition, newPosition, reason);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "state changed state=%d %s", state, lifecycleState());
        if (isOwner()) onStateChanged(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (!isOwner()) return;
        logSurfaceState("onVideoSizeChanged size=" + size.width + "x" + size.height + " ratio=" + size.pixelWidthHeightRatio);
        publishRenderTarget(getExoView().getVideoSurfaceView());
        syncVideoSurfaceSize(size);
        applyExoOutputMode();
        onSizeChanged(size);
    }

    private void applyExoOutputMode() {
        if (mService == null || exoOutputModeManager == null) return;
        publishRenderTarget(getExoView().getVideoSurfaceView());
        ExoOutputModeManager.Result result;
        if (player().isExo() && getRender() == PlayerSetting.RENDER_SURFACE) {
            Format format = player().getVideoFormat();
            result = exoOutputModeManager.apply(format);
        } else {
            result = exoOutputModeManager.observe("observation-only");
        }
        publishDisplayFacts(result);
        if (SpiderDebug.isEnabled() && result.decision() != null && result.decision().mode() != null) {
            ExoOutputModePolicy.Mode mode = result.decision().mode();
            SpiderDebug.log("playback-flow", "exo output mode reason=%s applied=%s target=%dx%d@%.3fHz", result.reason(), result.applied(), mode.width(), mode.height(), mode.refreshRateMilliHz() / 1000f);
        }
    }

    private void restoreExoOutputMode() {
        if (exoOutputModeManager == null) return;
        ExoOutputModeManager.Result result = exoOutputModeManager.restore();
        if (mService != null) publishDisplayFacts(result);
    }

    private void publishDisplayFacts(ExoOutputModeManager.Result result) {
        if (mService == null || result == null) return;
        player().publishPlaybackDisplayFacts(
                toDisplayMode(result.currentMode()),
                toDisplayMode(result.requestedMode()));
        ExoOutputModePolicy.Decision decision = result.decision();
        boolean hasTarget = decision != null && decision.mode() != null;
        PlaybackTelemetry.DecisionOutcome outcome = result.applied()
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : !hasTarget ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : decision.changeRequired() ? PlaybackTelemetry.DecisionOutcome.SELECTED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        player().publishPlaybackDecision(new PlaybackTelemetry.DecisionEvent(
                PlaybackTelemetry.DecisionDomain.DISPLAY_MODE,
                outcome,
                displayModeLabel(result.currentMode()),
                displayModeLabel(result.requestedMode()),
                result.applied() ? "window-requested" : displayModeLabel(result.currentMode()),
                result.reason(),
                result.applied() ? "none" : result.reason(),
                java.util.List.of(
                        displayModeInput("current_mode_id", result.currentMode() == null ? null : result.currentMode().id(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_width", result.currentMode() == null ? null : result.currentMode().width(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_height", result.currentMode() == null ? null : result.currentMode().height(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("current_refresh_millihz", result.currentMode() == null ? null : result.currentMode().refreshRateMilliHz(), PlaybackAutoContext.ValueSource.SYSTEM_API),
                        displayModeInput("target_mode_id", result.requestedMode() == null ? null : result.requestedMode().id(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_width", result.requestedMode() == null ? null : result.requestedMode().width(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_height", result.requestedMode() == null ? null : result.requestedMode().height(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        displayModeInput("target_refresh_millihz", result.requestedMode() == null ? null : result.requestedMode().refreshRateMilliHz(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER),
                        PlaybackTelemetry.DecisionInput.bool("change_required", decision != null && decision.changeRequired(),
                                PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                        PlaybackTelemetry.DecisionInput.bool("window_request", result.applied(),
                                PlaybackAutoContext.ValueSource.SYSTEM_API, PlaybackAutoContext.Confidence.HIGH))));
    }

    private static PlaybackTelemetry.DecisionInput displayModeInput(
            String name, Integer value, PlaybackAutoContext.ValueSource source) {
        return value == null
                ? PlaybackTelemetry.DecisionInput.unknown(name)
                : PlaybackTelemetry.DecisionInput.number(name, value,
                source, PlaybackAutoContext.Confidence.HIGH);
    }

    private static String displayModeLabel(ExoOutputModePolicy.Mode mode) {
        return mode == null ? "unknown" : "mode-" + mode.id();
    }

    private void publishRenderTarget(View surface) {
        if (mService == null) return;
        PlaybackAutoContext.RenderTarget target = surface instanceof SurfaceView
                ? PlaybackAutoContext.RenderTarget.SURFACE_VIEW
                : surface instanceof TextureView
                ? PlaybackAutoContext.RenderTarget.TEXTURE_VIEW
                : PlaybackAutoContext.RenderTarget.UNKNOWN;
        player().publishPlaybackRenderTarget(target);
    }

    private static PlaybackAutoContext.DisplayMode toDisplayMode(ExoOutputModePolicy.Mode mode) {
        return mode == null ? PlaybackAutoContext.DisplayMode.unknown()
                : new PlaybackAutoContext.DisplayMode(mode.id(), mode.width(), mode.height(), mode.refreshRateMilliHz());
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        long start = System.currentTimeMillis();
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.replaceBinding(this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
        mService.setPlaybackForeground(true);
        mService.setNavigationCallback(getNavigationCallback(), getPlaybackKey());
        mService.addPlayerCallback(mPlayerCallback);
        getSeekView().setProgressPlayer(player().getPlayer());
        player().setLutAllowed(isLutAllowed());
        player().setDanmakuForeground(true);
        publishRenderTarget(getExoView().getVideoSurfaceView());
        applyExoOutputMode();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-flow", "service connected cost=%dms key=%s", System.currentTimeMillis() - start, getPlaybackKey());
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service connected %s", lifecycleState());
        onServiceConnected();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "service disconnected name=%s %s", name, lifecycleState());
        getSeekView().setProgressPlayer(null);
        mService = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mService != null) {
            mService.setPlaybackForeground(true);
            if (isOwner()) player().setDanmakuForeground(true);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity resume %s", lifecycleState());
        playbackExiting = false;
        setRedirect(false);
        applyExoOutputMode();
        if (shouldReclaim()) {
            detachSurface();
            onReclaim();
        }
    }

    @Override
    protected void onPause() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity pause %s", lifecycleState());
        super.onPause();
        if (isRedirect() && mController != null) mController.pause();
    }

    @Override
    protected void onStop() {
        if (mService != null) {
            mService.setPlaybackForeground(false);
            if (isOwner()) player().setDanmakuForeground(false);
        }
        restoreExoOutputMode();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity stop backgroundOff=%s %s", PlayerSetting.isBackgroundOff(), lifecycleState());
        super.onStop();
        if (isOwner() && !isAudioOnly() && PlayerSetting.isBackgroundOff() && mController != null) mController.pause();
    }

    @Override
    public void onTrimMemory(int level) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity trimMemory level=%d %s", level, lifecycleState());
        super.onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy beforeRelease %s", lifecycleState());
        restoreExoOutputMode();
        super.onDestroy();
        if (isChangingConfigurations()) {
            if (mService != null) mService.removePlayerCallback(mPlayerCallback);
            detach();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy configuration change preserved service key=%s", getPlaybackKey());
            return;
        }
        releasePlaybackService();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("playback-lifecycle", "activity destroy afterRelease activity=%s key=%s", getClass().getSimpleName(), getPlaybackKey());
    }
}
