package com.fongmi.android.tv.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.effect.ColorLut;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;
import androidx.media3.mpvplayer.MpvPlayer;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.codec.CodecCapabilityInspector;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.MpvPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerCacheState;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.cache.PlaybackDiskBufferStore;
import com.fongmi.android.tv.player.exo.ExoDecoderResourceRecoveryLimiter;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardBufferPolicy;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardController;
import com.fongmi.android.tv.player.exo.ExoNetworkGuardEligibility;
import com.fongmi.android.tv.player.exo.ExoRtspLiveLagController;
import com.fongmi.android.tv.player.exo.ExoRtspLiveLagPolicy;
import com.fongmi.android.tv.player.exo.ForwardBufferTrend;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.player.ijk.IjkBufferController;
import com.fongmi.android.tv.player.ijk.IjkBufferPolicy;
import com.fongmi.android.tv.player.ijk.IjkDecodePressureController;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.ijk.IjkRealtimeRecoveryController;
import com.fongmi.android.tv.player.ijk.IjkRealtimeRecoveryPolicy;
import com.fongmi.android.tv.player.ijk.IjkRuntimeProfileController;
import com.fongmi.android.tv.player.ijk.IjkRuntimeProfilePolicy;
import com.fongmi.android.tv.player.ijk.IjkRuntimeProfiles;
import com.fongmi.android.tv.player.danmaku.DanmakuUrlPolicy;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuBatcher;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuBuffer;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMessage;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuMetrics;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuParser;
import com.fongmi.android.tv.player.danmaku.LiveDanmakuWebSocketSession;
import com.fongmi.android.tv.player.lut.DynamicLutEffect;
import com.fongmi.android.tv.player.lut.LutEffectFactory;
import com.fongmi.android.tv.player.lut.LutEligibility;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.lut.MpvLutShader;
import com.fongmi.android.tv.player.lut.MpvLutShaderFactory;
import com.fongmi.android.tv.player.mpv.MpvAutoController;
import com.fongmi.android.tv.player.mpv.MpvAutoControlPolicy;
import com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicy;
import com.fongmi.android.tv.player.mpv.MpvAutoRenderPolicy;
import com.fongmi.android.tv.player.mpv.MpvBackCacheController;
import com.fongmi.android.tv.player.mpv.MpvBackCachePolicy;
import com.fongmi.android.tv.player.mpv.MpvCacheTargetCoordinator;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.player.mpv.MpvForwardCacheController;
import com.fongmi.android.tv.player.mpv.MpvForwardCachePolicy;
import com.fongmi.android.tv.player.mpv.MpvHlsVariantController;
import com.fongmi.android.tv.player.mpv.MpvHlsVariantPolicy;
import com.fongmi.android.tv.player.mpv.MpvPreloadController;
import com.fongmi.android.tv.player.mpv.MpvPreloadPolicy;
import com.fongmi.android.tv.player.mpv.MpvResourcePressureController;
import com.fongmi.android.tv.player.mpv.MpvVulkanBackendPolicy;
import com.fongmi.android.tv.player.mpv.MpvResourcePressurePolicy;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.setting.IjkPerformanceSetting;
import com.fongmi.android.tv.setting.MpvPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackExperimentSetting;
import com.fongmi.android.tv.setting.PlaybackLightweightAssessmentSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackProfileAbSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.LocalProxyDebug;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import is.xyz.mpv.MPVLib;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    public static final String RELOAD_LUT_WARMUP = "__webhtv_lut_warmup_reload__";
    private static final String NETWORK_GUARD_DEBUG = "EXO_NETWORK_GUARD";

    private static void logNetworkGuard(String message) {
        if (SpiderDebug.isEnabled()) Log.d(NETWORK_GUARD_DEBUG, message);
    }

    private static final long LOCAL_PROXY_READY_TIMEOUT_MS = 5000;
    private static final long LOCAL_PROXY_RETRY_DELAY_MS = 1000;
    private static final long HARD_DECODE_SWITCH_RETRY_DELAY_MS = 1200;
    private static final long EXO_TUNNELING_RETRY_DELAY_MS = 250;
    private static final long EXO_DECODER_RUNTIME_RETRY_DELAY_MS = 1200;
    private static final long EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS = 500;
    private static final long EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS = 1200;
    private static final long MPV_AUTO_OUTPUT_PROBE_INTERVAL_MS = 250;
    private static final int LOCAL_PROXY_MAX_RETRY = 2;
    private static final int MPV_AUTO_OUTPUT_PROBE_MAX_ATTEMPTS = 20;
    private static final int LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD = 3;
    private static final long DANMAKU_FORCE_RELOAD_DEBOUNCE_MS = 10000;
    private static final long LIVE_DANMAKU_METRICS_INTERVAL_MS = 15000L;
    private static final long PLAYBACK_TELEMETRY_INTERVAL_MS = 5000L;
    private static final long MPV_FRAME_TIMING_LOG_INTERVAL_MS = 5000L;
    private static final long DISK_RANGE_GAP_TOLERANCE_MS = 2000L;
    private static final long LUT_PREVIEW_FRAME_INTERVAL_MS = 16L;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 5f};
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.##x");

    private final Runnable runnable;
    private final Runnable liveDanmakuMetricsRunnable;
    private final Runnable networkProtectionRunnable;
    private final Runnable playbackTelemetryRunnable;
    private final Callback callback;
    private final DynamicLutEffect dynamicLutEffect;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final BroadcastReceiver noisyReceiver;
    private final PlaybackBufferingTracker playbackBufferingTracker;
    private final PlaybackTrace playbackTrace;
    private final PlaybackAutoContextStore playbackAutoContextStore;
    private final PlaybackTelemetryCoordinator playbackTelemetryCoordinator;
    private final PlaybackProfileAbCoordinator playbackProfileAbCoordinator;
    private final PlaybackProfileAbCoordinator
            playbackLightweightAssessmentCoordinator;
    private final PlaybackMediaFactsCoordinator playbackMediaFactsCoordinator;
    private final ExoDecoderResourceRecoveryLimiter
            exoDecoderResourceRecoveryLimiter;
    private final ExoNetworkGuardController networkProtectionController;
    private final ExoRtspLiveLagController rtspLiveLagController;
    private final MpvAutoController mpvAutoController;
    private final MpvForwardCacheController mpvForwardCacheController;
    private final MpvBackCacheController mpvBackCacheController;
    private final MpvCacheTargetCoordinator mpvCacheTargetCoordinator;
    private final MpvHlsVariantController mpvHlsVariantController;
    private final MpvResourcePressureController mpvResourcePressureController;
    private final MpvPreloadController mpvPreloadController;
    private final PlaybackMemoryCoordinator.Registration mpvResourceMemoryRegistration;
    private final PlaybackMemoryCoordinator.Registration ijkBufferMemoryRegistration;
    private final PlaybackSystemConditionCoordinator.Registration mpvResourceSystemRegistration;
    private final PlaybackExperimentCoordinator playbackExperimentCoordinator;
    private final PlaybackExperimentCoordinator.Registration playbackExperimentRegistration;
    private final IjkBufferController ijkBufferController;
    private final IjkDecodePressureController ijkDecodePressureController;
    private final IjkRealtimeRecoveryController ijkRealtimeRecoveryController;
    private final IjkRuntimeProfileController ijkRuntimeProfileController;
    private final ForwardBufferTrend networkProtectionTrend;
    private final LiveDanmakuBatcher liveDanmakuBatcher;
    private final LiveDanmakuBuffer liveDanmakuBuffer;
    private final LiveDanmakuMetrics liveDanmakuMetrics;
    private DanmakuController danmakuController;
    private LiveDanmakuWebSocketSession liveDanmakuSession;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private String currentDanmakuUrl;
    private String currentDanmakuKey;
    private String loadingDanmakuKey;
    private String lastLoggedRouteTraceId = PlaybackTrace.NONE;
    private IjkTimelinePublicationKey lastIjkTimelinePublicationKey;
    private IjkBufferController.Decision pendingIjkBufferDecision;
    private IjkDecodePressureController.Decision pendingIjkDecodePressureDecision;
    private IjkRealtimeRecoveryPolicy.Decision pendingIjkRealtimeRecoveryDecision;
    private ExoDecoderResourceRecovery pendingExoDecoderResourceRecovery;
    private PlaybackAutoContext.SessionToken playbackAutoSession = PlaybackAutoContext.SessionToken.none();
    private long playbackTrackSequence;
    private long danmakuLoadStartedAtMs;
    private volatile long liveDanmakuGeneration;
    private volatile boolean liveDanmakuPlaybackActive;
    private long pendingSwitchPositionMs = C.TIME_UNSET;
    private long pendingInitialStartPositionMs = C.TIME_UNSET;
    private float pendingSwitchSpeed = 1f;
    private boolean danmakuLoadInProgress;
    private boolean danmakuForeground = true;
    private boolean pendingSwitchRepeat;
    private boolean pendingSwitchRestore;
    private boolean audioFocusHeld;
    private boolean noisyReceiverRegistered;
    private boolean resumeOnAudioFocusGain;
    private Object audioFocusRequest;

    private boolean initTrack;
    private boolean videoEffectsActive;
    private boolean videoEffectsDirty;
    private boolean lutAppliedForItem;
    private boolean lutApplyInProgress;
    private boolean lutPipelineReadyForItem;
    private boolean lutPipelinePrepareInProgress;
    private boolean pendingLutPreview;
    private boolean waitingLutBeforePlay;
    private boolean playWhenReady = true;
    private boolean lutWarmupRecoveryActive;
    private boolean lutWarmupRefreshRequested;
    private boolean lutWarmupReloadPreviewPending;
    private boolean hardDecodeSwitchRetryArmed;
    private boolean lutAllowed = true;
    private boolean mpvAutoOutputEvaluated;
    private boolean mpvAutoOutputFrameReady;
    private boolean mpvAutoOutputEvaluationScheduled;
    private boolean mpvExplicitSubtitlePreference;
    private boolean mpvAutoGpuPinnedForSession;
    private boolean mpvAutoVulkanPinnedForItem;
    private boolean mpvAutoVulkanDisabledForItem;
    private boolean mpvSurfaceFallbackTried;
    private boolean mpvVulkanFallbackTried;
    private boolean mpvHlsManagedReload;
    private boolean ijkBufferManagedReload;
    private boolean ijkRuntimeTemporaryFallback;
    private boolean ijkRuntimeManualOverride;
    private boolean pendingIjkRuntimeFallbackReparse;
    private boolean playbackForeground;
    private boolean exoDecoderResourceRecoveryInProgress;
    private int playerType;
    private int retry;
    private int localProxyRetry;
    private int prepareSeq;
    private int lutApplySeq;
    private int lutWarmupRecoveredErrors;
    private int mpvOutputEvaluationSeq;
    private int mpvAutoOutputProbeAttempts;
    private float userPlaybackSpeed = 1f;
    private float networkProtectionSpeed = 1f;
    private float networkProtectionSupportedSpeed = 1f;
    private long networkProtectionMediaBitrate;
    private long lastMpvFrameTimingLogMs;
    private ExoNetworkGuardController.State networkProtectionState = ExoNetworkGuardController.State.NORMAL;
    private ExoNetworkGuardController.ProtectionTier networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
    private String networkProtectionReason = "waiting";
    private PlaybackExperimentCoordinator.Token networkProtectionExperimentToken;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        PlaybackExperimentSetting.ensureInitialized();
        this.playbackExperimentCoordinator =
                PlaybackExperimentCoordinator.process();
        this.playbackExperimentRegistration =
                playbackExperimentCoordinator.addListener(update ->
                        App.post(() -> onPlaybackExperimentPolicyChanged(update)));
        this.runnable = this::onPlaybackTimeout;
        this.liveDanmakuMetricsRunnable = () -> logLiveDanmakuMetrics("periodic", true);
        this.networkProtectionRunnable = this::evaluateNetworkProtection;
        this.playbackTelemetryRunnable = this::publishPlaybackTelemetryTick;
        this.playbackBufferingTracker = new PlaybackBufferingTracker();
        this.playbackTrace = new PlaybackTrace();
        this.playbackAutoContextStore = PlaybackAutoContextStore.process();
        this.playbackTelemetryCoordinator = PlaybackTelemetryCoordinator.process();
        this.playbackProfileAbCoordinator = PlaybackProfileAbCoordinator.process();
        this.playbackLightweightAssessmentCoordinator =
                PlaybackLightweightAssessmentSetting.coordinator();
        this.playbackMediaFactsCoordinator = new PlaybackMediaFactsCoordinator(playbackAutoContextStore);
        this.exoDecoderResourceRecoveryLimiter =
                new ExoDecoderResourceRecoveryLimiter();
        this.networkProtectionController = new ExoNetworkGuardController();
        this.rtspLiveLagController = new ExoRtspLiveLagController();
        this.mpvAutoController = new MpvAutoController();
        this.mpvForwardCacheController = new MpvForwardCacheController();
        this.mpvBackCacheController = new MpvBackCacheController();
        this.mpvCacheTargetCoordinator = new MpvCacheTargetCoordinator();
        this.mpvHlsVariantController = new MpvHlsVariantController();
        this.mpvResourcePressureController = new MpvResourcePressureController();
        this.mpvPreloadController = new MpvPreloadController();
        this.ijkBufferController = new IjkBufferController();
        this.ijkDecodePressureController =
                new IjkDecodePressureController();
        this.ijkRealtimeRecoveryController =
                new IjkRealtimeRecoveryController();
        this.ijkRuntimeProfileController =
                IjkRuntimeProfiles.process().newController();
        this.mpvResourceMemoryRegistration = PlaybackMemoryCoordinator.process().addListener(update ->
                App.post(() -> onMpvResourceMemoryUpdate(update)));
        this.ijkBufferMemoryRegistration = PlaybackMemoryCoordinator.process().addListener(update ->
                App.post(() -> onIjkBufferMemoryUpdate(update)));
        this.mpvResourceSystemRegistration = PlaybackSystemConditionCoordinator.process().addListener(update ->
                App.post(() -> onMpvResourceSystemUpdate(update)));
        this.networkProtectionTrend = new ForwardBufferTrend();
        this.liveDanmakuBuffer = new LiveDanmakuBuffer();
        this.liveDanmakuMetrics = new LiveDanmakuMetrics();
        this.liveDanmakuBatcher = new LiveDanmakuBatcher(liveDanmakuBuffer, this::onLiveDanmakuBatch);
        this.dynamicLutEffect = new DynamicLutEffect();
        this.audioFocusChangeListener = this::onNativeAudioFocusChanged;
        this.noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
                onNativeAudioBecomingNoisy();
            }
        };
        this.playerType = PlayerSetting.getPlayer();
        this.engine = buildEngine(playerType, PlayerEngine.HARD);
        this.player = engine.getPlayer();
    }

    public void release() {
        prepareSeq++;
        lutApplySeq++;
        prepareTerminalRelease();
        resetNetworkProtectionSession("release");
        clearExoDecoderResourceRecovery(true);
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        App.removeCallbacks(playbackTelemetryRunnable);
        mpvResourceMemoryRegistration.close();
        ijkBufferMemoryRegistration.close();
        mpvResourceSystemRegistration.close();
        playbackExperimentRegistration.close();
        stopNativeAudioSession();
        clearDanmaku("release");
        releaseLiveDanmakuSession();
        liveDanmakuBatcher.release();
        App.removeCallbacks(liveDanmakuMetricsRunnable);
        if (danmakuController != null) danmakuController.setListener(null);
        danmakuController = null;
        endPlaybackTelemetrySession("release");
        clearPlaybackAutoContext();
        ijkRuntimeTemporaryFallback = false;
        ijkRuntimeManualOverride = false;
        pendingIjkRuntimeFallbackReparse = false;
        mpvAutoGpuPinnedForSession = false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        if (engine == null) return;
        engine.release();
        engine = null;
        player = null;
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
        playbackBufferingTracker.reset();
        playbackTrace.clear();
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
    }

    public void prepareTerminalRelease() {
        if (engine instanceof MpvPlayerEngine mpv) mpv.prepareTerminalRelease();
    }

    private boolean experimentAllowed(PlaybackExperimentPolicy.Action action) {
        return PlaybackExperimentSetting.isAllowed(action);
    }

    private void onPlaybackExperimentPolicyChanged(
            PlaybackExperimentCoordinator.Update update) {
        if (update == null) return;
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason
                        .GENERATION_CHANGED);
        PlaybackExperimentPolicy.State policy =
                PlaybackExperimentSetting.getState();
        PlaybackTrace.log(
                "playback-experiment",
                playbackTrace.current(),
                "generation=%d change=%s strategy=%s frameAb=%s profileAb=%s action=invalidate-internal-experiments",
                update.generation(),
                update.change(),
                policy.strategyId(),
                policy.allows(PlaybackExperimentPolicy.Action
                        .EXO_FRAME_SCHEDULING_AB),
                policy.allows(PlaybackExperimentPolicy.Action
                        .SHARED_PROFILE_AB_VALIDATION));
    }

    private void onPlaybackTimeout() {
        completeIjkBufferManagedReload(
                false, "timeout", SystemClock.elapsedRealtime(), true);
        if (retryLutWarmupByRefresh("timeout")) return;
        if (retryMpvDv7P81FirstFrameTimeout()) return;
        if (retryMpvVulkanBackendTimeout()) return;
        if (retryMpvAutoVulkanToOpenGl("auto-vulkan-first-frame-timeout")) return;
        if (retryExoDv7FirstFrameTimeout()) return;
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void resetLutRuntimeState(String reason, boolean clearEngineEffects) {
        lutApplySeq++;
        if (clearEngineEffects && engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason + "_reset");
        } else if (clearEngineEffects && engine != null && videoEffectsActive) {
            try {
                engine.setVideoEffects(Collections.emptyList());
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset reason=%s", reason);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset failed reason=%s error=%s", reason, causeChain(e));
            }
        }
        dynamicLutEffect.clear();
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return engine.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public String getPlaybackTraceId() {
        return playbackTrace.current();
    }

    public PlaybackAutoContext getPlaybackAutoContext() {
        return playbackAutoContextStore.snapshot();
    }

    public void publishPlaybackRenderTarget(PlaybackAutoContext.RenderTarget renderTarget) {
        playbackMediaFactsCoordinator.publishRenderTarget(
                playbackAutoSession, renderTarget, SystemClock.elapsedRealtime());
    }

    public void publishPlaybackDisplayFacts(
            PlaybackAutoContext.DisplayMode currentMode,
            PlaybackAutoContext.DisplayMode requestedMode) {
        playbackMediaFactsCoordinator.publishDisplayFacts(
                playbackAutoSession, currentMode, requestedMode, SystemClock.elapsedRealtime());
    }

    public void publishPlaybackDecision(PlaybackTelemetry.DecisionEvent event) {
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession, event, SystemClock.elapsedRealtime());
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return userPlaybackSpeed;
    }

    public float getEffectiveSpeed() {
        return player == null ? userPlaybackSpeed : player.getPlaybackParameters().speed;
    }

    public String getNetworkProtectionText() {
        if (!isExo() || !ExoPerformanceSetting.isNetworkProtectionEnabled()) return "";
        if (Math.abs(userPlaybackSpeed - 1f) > 0.001f) return "手动倍速时停用";
        if (!isVod()) return "仅支持点播";
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        if (!eligibility.eligible()) return networkProtectionEligibilityText(eligibility.reason());
        return switch (networkProtectionState) {
            case NORMAL -> "正常";
            case WARNING -> "评估中";
            case PROTECT -> "降速中";
            case RECOVERY -> "恢复中";
            case UNSUSTAINABLE -> "网络不足";
        };
    }

    private String networkProtectionEligibilityText(String reason) {
        return switch (reason == null ? "" : reason) {
            case "preserve-passthrough" -> "音频直通时停用";
            case "preserve-tunneling" -> "隧道模式时停用";
            case "speed-unsupported" -> "播放器不支持调速";
            case "user-speed" -> "手动倍速时停用";
            case "vod-only" -> "仅支持点播";
            default -> "未启用";
        };
    }

    public long getNetworkProtectionMediaBitrate() {
        return networkProtectionMediaBitrate;
    }

    public long getNetworkProtectionStableThroughput() {
        return networkProtectionMediaBitrate <= 0 ? 0 : Math.max(0, Math.round(networkProtectionMediaBitrate * networkProtectionSupportedSpeed));
    }

    public long getNetworkProtectionConsumption() {
        return networkProtectionMediaBitrate <= 0 ? 0 : Math.max(0, Math.round(networkProtectionMediaBitrate * getEffectiveSpeed()));
    }

    public float getNetworkProtectionSupportedSpeed() {
        return networkProtectionSupportedSpeed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedDuration() {
        return Math.max(0, getEffectiveBufferedPosition() - getPosition());
    }

    public int getBufferedPercentage() {
        if (!isExo()) return player.getBufferedPercentage();
        long duration = player.getDuration();
        if (duration == 0) return 100;
        if (duration < 0) return 0;
        return Math.max(0, Math.min(100, androidx.media3.common.util.Util.percentInt(
                getEffectiveBufferedPosition(), duration)));
    }

    private long getEffectiveBufferedPosition() {
        long nativeBuffered = Math.max(0, player.getBufferedPosition());
        if (!isExo()) return nativeBuffered;
        String mediaKey = PlaybackDiskBufferStore.mediaKey(player.getCurrentMediaItem());
        return PlaybackDiskBufferStore.process().effectiveEnd(
                mediaKey, nativeBuffered, player.getDuration(), DISK_RANGE_GAP_TOLERANCE_MS);
    }

    public boolean isLoading() {
        return player.isLoading();
    }

    public String getSizeText() {
        int width = getVideoWidth();
        int height = getVideoHeight();
        if (width <= 0 || height <= 0) {
            Format format = getVideoFormat();
            if (format != null) {
                if (width <= 0) width = format.width;
                if (height <= 0) height = format.height;
            }
        }
        return width <= 0 || height <= 0 ? "" : width + " x " + height;
    }

    public Format getVideoFormat() {
        return engine.getVideoFormat();
    }

    public PlayerCacheState getCacheState() {
        return engine == null ? PlayerCacheState.empty() : engine.getCacheState();
    }

    public String getRenderDiagnostics() {
        return engine == null ? "" : engine.getRenderDiagnostics();
    }

    public String getRuntimeDiagnostics() {
        return engine == null ? "" : engine.getRuntimeDiagnostics();
    }

    public String getGpuLoadDiagnostics() {
        return engine == null ? "" : engine.getGpuLoadDiagnostics();
    }

    public void setGpuLoadDiagnosticsEnabled(boolean enabled) {
        if (engine != null) engine.setGpuLoadDiagnosticsEnabled(enabled);
    }

    public PlayerEngine.VideoPlaybackDetails getVideoPlaybackDetails() {
        return engine == null
                ? PlayerEngine.VideoPlaybackDetails.empty()
                : engine.getVideoPlaybackDetails();
    }

    public AudioPlaybackDiagnostics.Snapshot getAudioPlaybackDiagnostics() {
        return engine == null
                ? AudioPlaybackDiagnostics.Snapshot.empty()
                : engine.getAudioPlaybackDiagnostics();
    }

    public long getDroppedFrames() {
        return engine == null ? 0 : engine.getDroppedFrames();
    }

    public int getRebufferCount() {
        return playbackBufferingTracker.getRebufferCount();
    }

    public long getRebufferTotalMs() {
        return playbackBufferingTracker.getRebufferTotalMs();
    }

    public boolean supportsSubtitleStyle() {
        return engine != null && engine.supportsSubtitleStyle();
    }

    public boolean supportsSecondarySubtitle() {
        return engine != null && engine.supportsSecondarySubtitle();
    }

    public boolean isSecondarySubtitleSelected(Format format) {
        return engine != null && engine.isSecondarySubtitleSelected(format);
    }

    public String getAudioPassThroughText() {
        if (!PlayerSetting.isAudioPassThrough()) return "关";
        if (!isMpv()) return "开";
        String codecs = engine == null ? "" : engine.getAudioSpdifCodecs();
        return TextUtils.isEmpty(codecs) ? "开/PCM" : "开/" + codecs;
    }

    public void setSubtitleStyle(float textSize, float position) {
        if (engine != null) engine.setSubtitleStyle(textSize, position);
    }

    public String getSpeedText() {
        return SPEED_FORMAT.format(getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public boolean isHardDecode() {
        return engine.isHard();
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player_kernel)[playerType];
    }

    public int getPlayerType() {
        return playerType;
    }

    public String getLutText() {
        return LutSetting.getButtonText();
    }

    public void setLutAllowed(boolean allowed) {
        if (lutAllowed == allowed) return;
        lutAllowed = allowed;
        if (!allowed) resetLutRuntimeState("lut_disallowed", true);
    }

    public String getLutUnavailableReason() {
        return LutEligibility.getUnavailableReason(engine, spec);
    }

    public boolean selectLut(@Nullable LutPreset preset, boolean preview) {
        boolean autoSwitchMpvToGpu = preset != null
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO
                && engine instanceof MpvPlayerEngine mpv
                && mpv.isSurfaceDirect();
        if (preset != null) {
            String reason = autoSwitchMpvToGpu
                    ? LutEligibility.getUnavailableReason(engine, spec, true)
                    : getLutUnavailableReason();
            if (!TextUtils.isEmpty(reason)) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "reject preset=%s reason=%s", preset.getId(), reason);
                Notify.show(reason);
                return false;
            }
        }
        LutSetting.select(preset);
        callback.onPlayerRenderRequired();
        if (autoSwitchMpvToGpu) {
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("lut-mpv", "auto output switch surface-direct -> gpu preset=%s preview=%s",
                        preset.getId(), preview);
            }
            return rebuildAndRestartMpv(false, "auto-lut-selected");
        }
        if (preset != null && preview) applyLutPreview(true);
        else applyLut(true);
        return true;
    }

    public boolean isIjk() {
        return playerType == PlayerSetting.IJK;
    }

    public boolean isMpv() {
        return playerType == PlayerSetting.MPV;
    }

    public boolean sendMpvCustomButton(String id, boolean longPress) {
        if (!isMpv() || TextUtils.isEmpty(id) || !(engine instanceof MpvPlayerEngine mpv)) return false;
        return mpv.sendScriptMessage(MpvConfigStore.CUSTOM_BUTTON_MESSAGE, id, longPress ? "long" : "short");
    }

    public boolean isMpvSurfaceDirect() {
        return engine instanceof MpvPlayerEngine mpv && mpv.isSurfaceDirect();
    }

    /**
     * Keep the native player's shutter visible while automatic MPV output is
     * still being selected. This prevents a failed direct DV probe from
     * exposing a stale poster or the last frame before the GPU rebuild.
     */
    public boolean shouldKeepVideoShutterClosed() {
        return isMpv()
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO
                && !mpvAutoOutputEvaluated
                && !mpvAutoOutputFrameReady;
    }

    public boolean isExo() {
        return playerType == PlayerSetting.EXO;
    }

    public boolean requiresTextureRenderForLut() {
        return isExo()
                && LutSetting.isEnabled()
                && canWarmLutPipeline()
                && TextUtils.isEmpty(getLutUnavailableReason());
    }

    public boolean isNativePlayer() {
        return !isExo();
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        setMediaItem();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setTitle(MediaEdition edition) {
        if (edition == null) return;
        if (isMpv() && engine.selectEdition(edition)) return;
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("edition=" + edition.index).build().toString());
        if (engine.selectEdition(edition)) return;
        setMediaItem();
        seekTo(0);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        if (danmakuController == controller) {
            configureDanmakuController(controller);
            return;
        }
        if (danmakuController != null) {
            danmakuController.setListener(null);
            danmakuController.clearItems();
        }
        danmakuController = controller;
        if (danmakuController == null) return;
        configureDanmakuController(danmakuController);
        restoreDanmakuDataSource();
    }

    private void configureDanmakuController(DanmakuController controller) {
        if (controller == null) return;
        controller.setOkHttpClient(OkHttp.player());
        controller.setConfig(DanmakuSetting.getConfig());
        controller.setEnabled(DanmakuSetting.isShow());
        controller.setListener(new DanmakuController.Listener() {
            @Override
            public void onLoadCompleted(Uri uri, int count) {
                logDanmakuLoad("completed", uri, count, null);
                finishDanmakuLoad(uri);
            }

            @Override
            public void onLoadError(Uri uri, IOException error) {
                logDanmakuLoad("error", uri, -1, error);
                finishDanmakuLoad(uri);
            }
        });
    }

    private void restoreDanmakuDataSource() {
        if (danmakuController == null || TextUtils.isEmpty(currentDanmakuUrl)) return;
        if (!DanmakuUrlPolicy.classify(currentDanmakuUrl).isStatic()) return;
        loadingDanmakuKey = currentDanmakuKey;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "restore controller %s key=%s", DanmakuUrlPolicy.logSummary(currentDanmakuUrl), summarizeUrl(currentDanmakuKey));
        danmakuController.setDataSource(Uri.parse(currentDanmakuUrl));
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        if (danmakuController != null) danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuController != null) danmakuController.setEnabled(enabled);
        if (!enabled) {
            stopLiveDanmakuSession("hidden");
        } else if (danmakuForeground && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        }
    }

    public void setDanmakuForeground(boolean foreground) {
        if (danmakuForeground == foreground) return;
        danmakuForeground = foreground;
        if (!foreground) {
            stopLiveDanmakuSession("background");
            discardLiveDanmakuPending();
        } else if (DanmakuSetting.isShow() && DanmakuUrlPolicy.classify(currentDanmakuUrl).isLive()) {
            connectLiveDanmakuSession(currentDanmakuUrl);
        }
    }

    public void setPlaybackForeground(boolean foreground) {
        playbackForeground = foreground;
        if (!foreground || pendingExoDecoderResourceRecovery == null) return;
        ExoDecoderResourceRecovery recovery = pendingExoDecoderResourceRecovery;
        pendingExoDecoderResourceRecovery = null;
        scheduleExoDecoderResourceRecovery(recovery, "foreground");
    }

    public void sendDanmaku(String text) {
        if (danmakuController != null) danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        if (Math.abs(speed - userPlaybackSpeed) >= 0.001f) {
            invalidatePlaybackProfileAssessments(
                    PlaybackProfileAbCoordinator.InvalidationReason.USER_SPEED);
        }
        userPlaybackSpeed = speed;
        resetNetworkProtectionSession("user-speed");
        if (Math.abs(speed - 1f) < 0.001f) scheduleNetworkProtection(0);
        return getSpeedText();
    }

    private void applyEffectiveSpeed(float speed, String reason) {
        if (player == null || !player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            logNetworkGuard("apply skipped reason=" + reason + " player=" + (player != null)
                    + " command=" + (player != null && player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)));
            return;
        }
        float current = player.getPlaybackParameters().speed;
        if (Math.abs(speed - userPlaybackSpeed) >= 0.001f) {
            invalidatePlaybackProfileAssessments(
                    PlaybackProfileAbCoordinator.InvalidationReason
                            .SPEED_RESCUE_CONFOUND);
        }
        logNetworkGuard(String.format(java.util.Locale.US,
                "apply request reason=%s requested=%.3f current=%.3f user=%.3f state=%d playing=%s loading=%s",
                reason, speed, current, userPlaybackSpeed, player.getPlaybackState(), player.isPlaying(), player.isLoading()));
        if (Math.abs(current - speed) < 0.001f) return;
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        logNetworkGuard(String.format(java.util.Locale.US,
                "apply result reason=%s requested=%.3f actual=%.3f", reason, speed, player.getPlaybackParameters().speed));
        PlaybackTrace.log("exo-network-protection", playbackTrace.current(), "speed %.3f->%.3f reason=%s user=%.2f", current, speed, reason, userPlaybackSpeed);
    }

    private void resetNetworkProtectionSession(String reason) {
        App.removeCallbacks(networkProtectionRunnable);
        networkProtectionController.reset();
        networkProtectionTrend.reset();
        networkProtectionState = ExoNetworkGuardController.State.NORMAL;
        networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
        networkProtectionReason = reason;
        networkProtectionSpeed = 1f;
        networkProtectionSupportedSpeed = 1f;
        networkProtectionMediaBitrate = 0;
        networkProtectionExperimentToken = null;
        applyEffectiveSpeed(userPlaybackSpeed, reason);
    }

    private ExoNetworkGuardEligibility.Decision getNetworkProtectionEligibility() {
        AudioPlaybackDiagnostics.OutputMode audioOutputMode = getAudioPlaybackDiagnostics()
                .outputMode();
        return ExoNetworkGuardEligibility.resolve(new ExoNetworkGuardEligibility.Request(
                ExoPerformanceSetting.isNetworkProtectionEnabled()
                        && experimentAllowed(
                        PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED),
                player != null && isExo(),
                isVod(),
                Math.abs(userPlaybackSpeed - 1f) < 0.001f,
                player != null && player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH),
                PlayerSetting.isTunnel(),
                audioOutputMode));
    }

    private void scheduleNetworkProtection(long delayMs) {
        App.removeCallbacks(networkProtectionRunnable);
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        AudioPlaybackDiagnostics.OutputMode audioOutputMode = getAudioPlaybackDiagnostics()
                .outputMode();
        logNetworkGuard("schedule delay=" + delayMs + " eligible=" + eligibility.eligible()
                + " reason=" + eligibility.reason() + " exo=" + isExo() + " vod=" + isVod()
                + " userSpeed=" + userPlaybackSpeed + " tunnel=" + PlayerSetting.isTunnel()
                + " configuredPassthrough=" + PlayerSetting.isAudioPassThrough(PlayerSetting.EXO)
                + " audioOutput=" + audioOutputMode
                + " state=" + (player == null ? -1 : player.getPlaybackState())
                + " playing=" + (player != null && player.isPlaying()));
        if (!eligibility.eligible()) {
            if (networkProtectionSpeed < 0.999f) resetNetworkProtectionSession(eligibility.reason());
            else {
                networkProtectionState = ExoNetworkGuardController.State.NORMAL;
                networkProtectionTier = ExoNetworkGuardController.ProtectionTier.NONE;
                networkProtectionReason = eligibility.reason();
            }
            return;
        }
        if (player.getPlaybackState() != Player.STATE_READY || !player.isPlaying()) return;
        networkProtectionExperimentToken = playbackExperimentCoordinator.capture(
                PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED);
        App.post(networkProtectionRunnable, delayMs);
    }

    private void evaluateNetworkProtection() {
        if (player == null) return;
        if (!playbackExperimentCoordinator.isCurrent(
                networkProtectionExperimentToken)
                || !experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_NETWORK_SPEED)) {
            resetNetworkProtectionSession("experiment-disabled");
            return;
        }
        ExoNetworkGuardEligibility.Decision eligibility = getNetworkProtectionEligibility();
        boolean eligible = eligibility.eligible();
        long nowMs = SystemClock.elapsedRealtime();
        boolean ready = player.getPlaybackState() == Player.STATE_READY;
        boolean playing = player.isPlaying();
        boolean loading = player.isLoading();
        long bufferedMs = Math.max(0, player.getTotalBufferedDuration());
        networkProtectionTrend.observe(
                nowMs,
                bufferedMs,
                eligible && ready && playing,
                loading);
        ForwardBufferTrend.Snapshot trend = networkProtectionTrend.snapshot();
        PlaybackAnalyticsListener.Snapshot analytics = PlaybackAnalyticsListener.getSnapshot();
        PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media = PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(getVideoFormat());
        boolean networkEstimateKnown = isTrustedNetworkEstimate(analytics, media);
        float networkSupportedSpeed = networkEstimateKnown ? Math.min(2f, analytics.bandwidthEstimate() * 0.90f / media.bitrateBitsPerSecond()) : 1f;
        long safeBufferMs = getNetworkProtectionSafeBufferMs();
        float previousEffectiveSpeed = getEffectiveSpeed();
        ExoNetworkGuardController.State previousState = networkProtectionState;
        ExoNetworkGuardController.ProtectionTier previousTier = networkProtectionTier;
        ExoNetworkGuardController.Decision decision = networkProtectionController.evaluate(new ExoNetworkGuardController.Input(
                nowMs,
                eligible,
                ready,
                playing,
                loading,
                bufferedMs,
                trend.known(),
                trend.slopeMsPerSecond(),
                trend.fastSlopeMsPerSecond(),
                trend.slowSlopeMsPerSecond(),
                trend.windowMs(),
                analytics.rebufferCount(),
                previousEffectiveSpeed,
                ExoPerformanceSetting.getNetworkProtectionMinimumSpeed(),
                safeBufferMs,
                networkEstimateKnown,
                networkSupportedSpeed));
        logNetworkGuard(String.format(java.util.Locale.US,
                "evaluate eligible=%s ready=%s playing=%s loading=%s buffered=%d safe=%d trendKnown=%s slope=%d fast=%d slow=%d window=%d rebuffer=%d current=%.3f networkKnown=%s networkSupported=%.3f decision=%s tier=%s reason=%s changed=%s target=%.3f supported=%.3f raw=%.3f calculated=%.3f tte=%d ttr=%d requiredSlew=%.4f appliedSlew=%.4f feasible=%s",
                eligible, ready, playing, loading, bufferedMs, safeBufferMs, trend.known(), trend.slopeMsPerSecond(),
                trend.fastSlopeMsPerSecond(), trend.slowSlopeMsPerSecond(), trend.windowMs(), analytics.rebufferCount(),
                getEffectiveSpeed(), networkEstimateKnown, networkSupportedSpeed, decision.state(), decision.tier(), decision.reason(),
                decision.changed(), decision.targetSpeed(), decision.supportedSpeed(), decision.rawTargetSpeed(),
                decision.calculatedTargetSpeed(), decision.timeToEmptyMs(), decision.timeToReserveMs(),
                decision.requiredSlewPerSecond(), decision.appliedSlewPerSecond(), decision.rampFeasible()));
        networkProtectionState = decision.state();
        networkProtectionTier = decision.tier();
        networkProtectionReason = decision.reason();
        networkProtectionSpeed = decision.targetSpeed();
        networkProtectionSupportedSpeed = decision.supportedSpeed();
        networkProtectionMediaBitrate = media.bitrateBitsPerSecond();
        if (decision.changed()) applyEffectiveSpeed(networkProtectionSpeed, "guard-" + decision.reason());
        PlaybackTelemetry.DecisionOutcome telemetryOutcome = decision.changed()
                ? PlaybackTelemetry.DecisionOutcome.APPLIED
                : !eligible || !ready || !playing
                ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        playbackTelemetryCoordinator.publishDecision(playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.NETWORK_PROTECTION,
                        telemetryOutcome,
                        previousState.name().toLowerCase(java.util.Locale.US),
                        decision.state().name().toLowerCase(java.util.Locale.US),
                        networkProtectionState.name().toLowerCase(java.util.Locale.US),
                        decision.reason(),
                        telemetryOutcome == PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                                ? eligibility.reason() : telemetryOutcome == PlaybackTelemetry.DecisionOutcome.HELD ? "no-change" : "none",
                        List.of(
                                PlaybackTelemetry.DecisionInput.bool("eligible", eligible, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("ready", ready, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("playing", playing, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("loading", loading, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("buffered_ms", bufferedMs, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("safe_buffer_ms", safeBufferMs, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                networkEstimateKnown ? PlaybackTelemetry.DecisionInput.number("bandwidth_bps", analytics.bandwidthEstimate(), PlaybackAutoContext.ValueSource.ESTIMATOR, PlaybackAutoContext.Confidence.MEDIUM) : PlaybackTelemetry.DecisionInput.unknown("bandwidth_bps"),
                                media.bitrateBitsPerSecond() > 0 ? PlaybackTelemetry.DecisionInput.number("media_bitrate_bps", media.bitrateBitsPerSecond(), PlaybackAutoContext.ValueSource.ESTIMATOR, telemetryConfidence(media.confidence())) : PlaybackTelemetry.DecisionInput.unknown("media_bitrate_bps"),
                                PlaybackTelemetry.DecisionInput.number("rebuffer_count", analytics.rebufferCount(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                trend.known() ? PlaybackTelemetry.DecisionInput.number("buffer_slope_msps", trend.slopeMsPerSecond(), PlaybackAutoContext.ValueSource.ESTIMATOR, PlaybackAutoContext.Confidence.MEDIUM) : PlaybackTelemetry.DecisionInput.unknown("buffer_slope_msps"),
                                PlaybackTelemetry.DecisionInput.decimal("current_speed", previousEffectiveSpeed, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.decimal("target_speed", decision.targetSpeed(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH))),
                nowMs);
        if (decision.changed() || previousState != networkProtectionState || previousTier != networkProtectionTier) {
            PlaybackTrace.log("exo-network-protection", playbackTrace.current(), "state=%s tier=%s reason=%s speed=%.3f supported=%.3f rawTarget=%.3f target=%.3f floor=%.2f buffered=%d safe=%d tte=%d ttr=%d requiredSlew=%.4f appliedSlew=%.4f feasible=%s loading=%s slope=%d fast=%d slow=%d window=%d rebuffer=%d networkKnown=%s networkSupported=%.3f route=%s",
                    networkProtectionState, networkProtectionTier, networkProtectionReason, networkProtectionSpeed, decision.supportedSpeed(), decision.rawTargetSpeed(), decision.calculatedTargetSpeed(), ExoPerformanceSetting.getNetworkProtectionMinimumSpeed(),
                    player.getTotalBufferedDuration(), decision.safeBufferMs(), decision.timeToEmptyMs(), decision.timeToReserveMs(), decision.requiredSlewPerSecond(), decision.appliedSlewPerSecond(), decision.rampFeasible(),
                    player.isLoading(), trend.slopeMsPerSecond(), trend.fastSlopeMsPerSecond(), trend.slowSlopeMsPerSecond(), trend.windowMs(), analytics.rebufferCount(), networkEstimateKnown, networkSupportedSpeed, getEffectivePlaybackRoute().route());
        }
        if (eligible && player.getPlaybackState() == Player.STATE_READY && player.isPlaying()) scheduleNetworkProtection(getNetworkProtectionEvaluationDelayMs());
    }

    private long getNetworkProtectionEvaluationDelayMs() {
        return switch (networkProtectionState) {
            case WARNING, PROTECT, RECOVERY -> ExoNetworkGuardController.CONTROL_INTERVAL_MS;
            case NORMAL, UNSUSTAINABLE -> ExoNetworkGuardController.OBSERVE_INTERVAL_MS;
        };
    }

    private long getNetworkProtectionSafeBufferMs() {
        PlaybackRoute.Resolution route = getEffectivePlaybackRoute();
        return ExoNetworkGuardBufferPolicy.resolve(route.loopback(), ExoPerformanceSetting.getRebufferMs());
    }

    private boolean isTrustedNetworkEstimate(PlaybackAnalyticsListener.Snapshot analytics, PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media) {
        if (analytics.bandwidthEstimate() <= 0 || media.bitrateBitsPerSecond() <= 0) return false;
        if ("unknown".equals(media.source()) || "low".equals(media.confidence()) || "unknown".equals(media.confidence())) return false;
        return getEffectivePlaybackRoute().route() == PlaybackRoute.DIRECT_REMOTE_HTTP;
    }

    public String addSpeed() {
        return setSpeed(nextPresetSpeed());
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    private float nextPresetSpeed() {
        float speed = getSpeed();
        for (float preset : SPEED_PRESETS) if (speed < preset - 0.01f) return preset;
        return SPEED_PRESETS[0];
    }

    public void setTrack(List<Track> tracks) {
        mpvExplicitSubtitlePreference = hasRequestedSubtitle(tracks);
        if (mpvExplicitSubtitlePreference && engine instanceof MpvPlayerEngine mpv) {
            mpv.retainSubtitleSurfaceForCurrentItem();
        }
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void setSecondarySubtitleTrack(Track track) {
        if (track != null && !track.isDisabled()
                && engine instanceof MpvPlayerEngine mpv) {
            mpv.retainSubtitleSurfaceForCurrentItem();
        }
        if (engine != null) engine.setSecondarySubtitleTrack(track);
    }

    public void play() {
        startNativeAudioSession(true);
        player.play();
    }

    public void pause() {
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason.PAUSED);
        player.pause();
        stopNativeAudioSession();
    }

    public void stop() {
        stopNativeAudioSession();
        clearDanmaku("stop");
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        stopNativeAudioSession();
        clearDanmaku("clear_media_items");
        mpvAutoGpuPinnedForSession = false;
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        long now = SystemClock.elapsedRealtime();
        invalidatePlaybackProfileAssessments(
                PlaybackProfileAbCoordinator.InvalidationReason.USER_SEEK);
        rtspLiveLagController.onUserSeek(playbackAutoSession, now);
        ijkRealtimeRecoveryController.onUserSeek(playbackAutoSession, now);
        ijkDecodePressureController.onUserSeek(playbackAutoSession, now);
        resetNetworkProtectionSession("user-seek");
        if (isExo()) {
            PlaybackAnalyticsListener.onUserSeekRequested(
                    player.getCurrentPosition(),
                    time,
                    player.getPlaybackState(),
                    player.getBufferedPosition(),
                    player.getTotalBufferedDuration(),
                    player.isLoading(),
                    player.isPlaying());
        }
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET)) return player.getTextOffsetMs();
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET)) return player.getAudioOffsetMs();
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        boolean activePlayback = player != null
                && player.getPlaybackState() == Player.STATE_READY
                && player.getPlayWhenReady();
        if (activePlayback) scheduleNetworkProtection(0);
        else resetNetworkProtectionSession("reset");
        retry = 0;
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        clearPendingSwitchRestore();
    }

    public void clear() {
        prepareSeq++;
        lutApplySeq++;
        clearExoDecoderResourceRecovery(true);
        resetNetworkProtectionSession("clear");
        resetMpvOutputRuntime();
        spec = null;
        clearPendingSwitchRestore();
        clearDanmaku("clear");
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        clearLutWarmupRecovery();
        endPlaybackTelemetrySession("clear");
        playbackBufferingTracker.reset();
        clearPlaybackAutoContext();
        playbackTrace.clear();
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
    }

    public void resetTrack() {
        engine.resetTrack();
    }

    public void restoreVideoTrack() {
        if (engine != null) engine.restoreVideoTrack();
    }

    public void toggleDecode() {
        beginIjkRuntimeManualOverride();
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        beginPlaybackTrace("switch-decode");
        engine.setDecode(next);
        if (engine instanceof ExoPlayerEngine exo) {
            exo.prepareFrameSchedulingForNextPlayback();
        }
        rebuildPlayer(resetVideoSurface);
        setMediaItem();
    }

    public void switchDecode(PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        beginIjkRuntimeManualOverride();
        beginPlaybackTrace("switch-decode-fresh");
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetLutRuntimeState("switch_decode_fresh", true);
        stopNativeAudioSession();
        engine.release();
        spec = freshSpec;
        bindPlaybackTrace();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        engine = buildEngine(playerType, next);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh decode=%d position=%d spec=%s", next, position, debugSpec());
        callback.onPlayerRebuild(player, resetVideoSurface);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchDecode(Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        beginIjkRuntimeManualOverride();
        beginPlaybackTrace("switch-decode-result");
        int next = engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean resetVideoSurface = playerType == PlayerSetting.EXO && next == PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetLutRuntimeState("switch_decode_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        hardDecodeSwitchRetryArmed = next == PlayerEngine.HARD;
        engine = buildEngine(playerType, next);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, resetVideoSurface);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh parse decode=%d position=%d useParse=%s spec=%s", next, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = PlaySpec.from(result, key, metadata);
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch decode fresh result decode=%d position=%d spec=%s", next, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    public void togglePlayer() {
        switchPlayer(PlayerSetting.nextPlayer(playerType));
    }

    public void switchPlayer(int type) {
        switchPlayer(type, true);
    }

    public void switchPlayer(int type, PlaySpec freshSpec, long position, float speed, boolean repeat) {
        if (engine == null || player == null || freshSpec == null) return;
        beginIjkRuntimeManualOverride();
        beginPlaybackTrace("switch-player-fresh");
        type = PlayerSetting.sanitizePlayer(type);
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player_fresh", true);
        stopNativeAudioSession();
        engine.release();
        playerType = type;
        PlayerSetting.putPlayer(type);
        spec = freshSpec;
        bindPlaybackTrace();
        playWhenReady = wasPlayWhenReady;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh type=%d position=%d spec=%s", type, position, debugSpec());
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player, false);
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    public void switchPlayer(int type, Result result, String key, MediaMetadata metadata, boolean useParse, long position, float speed, boolean repeat) {
        if (engine == null || player == null || result == null || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        beginIjkRuntimeManualOverride();
        beginPlaybackTrace("switch-player-result");
        type = PlayerSetting.sanitizePlayer(type);
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player_result", true);
        stopNativeAudioSession();
        stopParse();
        engine.release();
        playerType = type;
        PlayerSetting.putPlayer(type);
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        playWhenReady = wasPlayWhenReady;
        callback.onPlayerRebuild(player, false);
        if (result.needParse() || useParse) {
            pendingSwitchRestore = true;
            pendingSwitchPositionMs = position;
            pendingSwitchSpeed = speed;
            pendingSwitchRepeat = repeat;
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh parse type=%d position=%d useParse=%s spec=%s", type, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            spec = PlaySpec.from(result, key, metadata);
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player fresh result type=%d position=%d spec=%s", type, position, debugSpec());
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        }
    }

    private void switchPlayer(int type, boolean persist) {
        if (engine == null || player == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        if (type == playerType) return;
        beginIjkRuntimeManualOverride();
        beginPlaybackTrace("switch-player");
        long position = getPosition();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player", true);
        stopNativeAudioSession();
        engine.release();
        playerType = type;
        if (persist) {
            PlayerSetting.putPlayer(type);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player type=%d persist=%s position=%d spec=%s", type, persist, position, debugSpec());
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player, false);
        if (spec == null || spec.getUrl() == null) return;
        this.playWhenReady = wasPlayWhenReady;
        if (reparseForPlayerSwitch(position, speed, repeat)) return;
        setMediaItem(Constant.TIMEOUT_PLAY);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void rebuildPlayer() {
        rebuildPlayer(false);
    }

    private void rebuildPlayer(boolean resetVideoSurface) {
        stopNativeAudioSession();
        player = engine.rebuild(listener);
        restoreIjkStagedBufferConfig();
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        callback.onPlayerRebuild(player, resetVideoSurface);
    }

    private void applyMpvHlsInitialControl(
            MpvPlayerEngine mpv,
            PlaybackAutoContext context,
            boolean automatic,
            long now) {
        String networkIdentity = PlaybackSystemConditionMonitor.process()
                .currentNetworkIdentityDigest();
        PlaybackThroughputHistory.Match history = PlaybackThroughputHistory
                .process().lookup(context, networkIdentity, now);
        MpvHlsVariantPolicy.InitialAssessment assessment =
                MpvHlsVariantPolicy.resolveInitial(
                        automatic,
                        isMpv(),
                        MpvPerformanceSetting.isPerformancePriority(),
                        history);
        MpvHlsVariantController.Snapshot before =
                mpvHlsVariantController.snapshot();
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.evaluateInitial(
                        playbackAutoSession,
                        context == null ? PlaybackAutoContext.SessionToken.none()
                                : context.session(),
                        assessment);
        boolean pendingContextRestore = !decision.requestsApply()
                && decision.reason()
                == MpvHlsVariantController.Reason.ACTION_PENDING
                && before.state() != MpvHlsVariantController.State.APPLYING
                && assessment.active()
                && !TextUtils.isEmpty(before.targetOption());
        String requestedOption = pendingContextRestore
                ? before.targetOption() : decision.targetOption();
        boolean started = false;
        MpvPlayer.AutoHlsBitrateResult result =
                MpvPlayer.AutoHlsBitrateResult.REJECTED;
        if (pendingContextRestore) {
            result = mpv.applyAutoHlsBitrate(
                    playbackTrace.current(), requestedOption);
        } else if (decision.requestsApply()) {
            started = mpvHlsVariantController.beginApply(
                    playbackAutoSession, decision, now);
            if (started) {
                result = mpv.applyAutoHlsBitrate(
                        playbackTrace.current(), decision.targetOption());
                mpvHlsVariantController.completeApply(
                        playbackAutoSession,
                        decision,
                        result.accepted(),
                        result.staged(),
                        now);
            }
        } else if (decision.reason()
                != MpvHlsVariantController.Reason.ACTION_PENDING) {
            mpv.clearAutoHlsBitrate();
            if (!automatic
                    || assessment.reason()
                    == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY) {
                mpvHlsVariantController.suppress(playbackAutoSession);
            }
        }
        PlaybackTelemetry.DecisionOutcome outcome =
                !automatic
                        || assessment.reason()
                        == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : pendingContextRestore && !result.accepted()
                        ? PlaybackTelemetry.DecisionOutcome.FAILED
                        : pendingContextRestore && result.staged()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : pendingContextRestore
                        ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : !started || !result.accepted()
                        ? PlaybackTelemetry.DecisionOutcome.FAILED
                        : result.staged()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : PlaybackTelemetry.DecisionOutcome.APPLIED;
        String suppression = !automatic
                ? "not-automatic"
                : assessment.reason() == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : pendingContextRestore && !result.accepted()
                ? "native-restore-failed"
                : pendingContextRestore
                ? "none"
                : decision.requestsApply() && !started
                ? "action-rejected"
                : decision.requestsApply() && !result.accepted()
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "ceiling_bps", assessment.ceilingBitsPerSecond(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.trustedThroughputBitsPerSecond() > 0
                ? PlaybackTelemetry.DecisionInput.number(
                "trusted_throughput_bps",
                assessment.trustedThroughputBitsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                assessment.confidence())
                : PlaybackTelemetry.DecisionInput.unknown(
                "trusted_throughput_bps"));
        inputs.add(history.usable()
                ? PlaybackTelemetry.DecisionInput.number(
                "evidence_age_ms", history.ageMs(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                history.confidence())
                : PlaybackTelemetry.DecisionInput.unknown("evidence_age_ms"));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "history_reason", history.reason().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.pathKind() == PlaybackAutoContext.PathKind.UNKNOWN
                ? PlaybackTelemetry.DecisionInput.unknown("path")
                : PlaybackTelemetry.DecisionInput.text(
                "path", assessment.pathKind().label(),
                PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER,
                assessment.confidence()));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "native_readbacks",
                mpv.getAutoHlsRuntimeSnapshot().observedCount(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(before.targetOption()),
                        hlsOptionLabel(requestedOption),
                        result.accepted()
                                ? hlsOptionLabel(requestedOption)
                                : hlsOptionLabel(before.targetOption()),
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
    }

    private void applyMpvAutoInitialControl() {
        if (!(engine instanceof MpvPlayerEngine mpv) || !playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        boolean automaticForward = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BUFFER_BYTES);
        boolean automaticBack = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BACK_BUFFER);
        boolean automatic = automaticForward || automaticBack;
        applyMpvHlsInitialControl(
                mpv,
                context,
                PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV,
                        PlaybackPerformanceCatalog.MPV_HLS_BITRATE),
                now);
        MpvAutoControlPolicy.Request request = MpvAutoControlPolicy.requestFrom(
                context,
                automatic,
                isMpv(),
                MpvPerformanceSetting.isPerformancePriority(),
                now);
        MpvAutoControlPolicy.Decision decision = mpvAutoController.evaluate(
                playbackAutoSession,
                context.session(),
                request);
        MpvAutoController.Snapshot before = mpvAutoController.snapshot();
        if (!automatic) {
            mpv.clearAutoCacheBaseline();
            mpvForwardCacheController.suppress(playbackAutoSession);
            mpvBackCacheController.suppress(playbackAutoSession);
            mpvCacheTargetCoordinator.suppress(playbackAutoSession);
            evaluateMpvCaches(
                    null,
                    null,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
            return;
        }

        boolean started = false;
        boolean applied = false;
        boolean staged = false;
        String applyResult = "not-requested";
        MpvForwardCacheController.Trigger forwardTrigger = null;
        MpvBackCacheController.Trigger backTrigger = null;
        long targetForwardBytes = automaticForward
                ? decision.forwardBytes() : mpv.getConfiguredForwardCacheBytes();
        long targetBackBytes = automaticBack
                ? decision.backBytes() : mpv.getConfiguredBackCacheBytes();
        if (targetBackBytes > targetForwardBytes) {
            if (automaticForward && !automaticBack) {
                targetForwardBytes = targetBackBytes;
            } else if (!automaticForward && automaticBack) {
                targetBackBytes = targetForwardBytes;
            }
        }
        if (decision.requestsApply()) {
            started = mpvAutoController.beginApply(playbackAutoSession, decision);
            MpvPlayer.AutoCacheBaselineResult result = started
                    ? mpv.applyAutoCacheBaseline(
                    playbackTrace.current(), targetForwardBytes, targetBackBytes)
                    : MpvPlayer.AutoCacheBaselineResult.REJECTED;
            applied = result.accepted();
            staged = result.staged();
            applyResult = result.label();
            if (started) {
                mpvAutoController.completeApply(
                        playbackAutoSession, decision, applied, staged);
            }
        } else {
            mpv.clearAutoCacheBaseline();
            mpvForwardCacheController.suppress(playbackAutoSession);
            mpvBackCacheController.suppress(playbackAutoSession);
            mpvCacheTargetCoordinator.suppress(playbackAutoSession);
        }
        if (applied) {
            MpvForwardCacheController.Snapshot forwardBefore = mpvForwardCacheController.snapshot();
            MpvBackCacheController.Snapshot backBefore = mpvBackCacheController.snapshot();
            boolean preserveForwardTarget = forwardBefore.baselineInitialized();
            boolean preserveBackTarget = backBefore.baselineInitialized();
            mpvForwardCacheController.recordBaseline(
                    playbackAutoSession, targetForwardBytes, preserveForwardTarget);
            mpvBackCacheController.recordBaseline(
                    playbackAutoSession, targetBackBytes, preserveBackTarget);
            mpvCacheTargetCoordinator.recordBaseline(
                    playbackAutoSession, targetForwardBytes, targetBackBytes);
            forwardTrigger = !automaticForward ? null : preserveForwardTarget
                    ? MpvForwardCacheController.Trigger.REBUILD
                    : MpvForwardCacheController.Trigger.BASELINE;
            backTrigger = !automaticBack ? null : preserveBackTarget
                    ? MpvBackCacheController.Trigger.REBUILD
                    : MpvBackCacheController.Trigger.BASELINE;
        }
        MpvAutoController.Snapshot after = mpvAutoController.snapshot();
        PlaybackTelemetry.DecisionOutcome outcome =
                decision.reason() == MpvAutoControlPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : staged
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : applied
                        ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : PlaybackTelemetry.DecisionOutcome.FAILED;
        String oldValue = before.appliedForwardBytes() >= 0
                ? "forward-" + before.appliedForwardBytes()
                + "-back-" + before.appliedBackBytes()
                : "startup-baseline";
        String resultValue = applied ? decision.targetLabel() : oldValue;
        String suppression = decision.reason() == MpvAutoControlPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : decision.requestsApply() && !started
                ? "action-rejected"
                : decision.requestsApply() && !applied
                ? "native-apply-failed"
                : "none";

        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(decision.requestsApply()
                ? PlaybackTelemetry.DecisionInput.number(
                "forward_bytes", decision.forwardBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("forward_bytes"));
        inputs.add(decision.requestsApply()
                ? PlaybackTelemetry.DecisionInput.number(
                "back_bytes", decision.backBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("back_bytes"));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "performance_priority", request.performancePriority(),
                PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(request.pressureUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "memory_pressure", request.memoryPressure().label(),
                context.device().memoryPressure().source(),
                context.device().memoryPressure().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "memory_snapshot_usable", request.snapshotUsable(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(request.snapshotUsable()
                && request.memorySnapshot().lowRamDevice() != null
                ? PlaybackTelemetry.DecisionInput.bool(
                "low_ram", request.memorySnapshot().lowRamDevice(),
                context.device().memorySnapshot().source(),
                context.device().memorySnapshot().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("low_ram"));
        inputs.add(request.protocolUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "protocol", request.protocol().label(),
                context.resource().protocol().source(),
                context.resource().protocol().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("protocol"));
        inputs.add(request.streamKindUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "stream", request.streamKind().label(),
                context.resource().streamKind().source(),
                context.resource().streamKind().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("stream"));
        inputs.add(request.playerPathUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "player_path", request.playerPath().label(),
                context.path().playerPath().source(),
                context.path().playerPath().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("player_path"));
        inputs.add(request.upstreamPathUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "upstream_path", request.upstreamPath().label(),
                context.path().upstreamPath().source(),
                context.path().upstreamPath().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("upstream_path"));
        inputs.add(request.upstreamStateUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "upstream_state", request.upstreamState().label(),
                context.path().upstreamState().source(),
                context.path().upstreamState().confidence())
                : PlaybackTelemetry.DecisionInput.unknown("upstream_state"));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "apply_attempts", after.applyAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));

        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_CACHE,
                        outcome,
                        oldValue,
                        decision.targetLabel(),
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-auto", playbackTrace.current(),
                "state=%s action=%s reason=%s forwardBytes=%d backBytes=%d capped=%s attempts=%d result=%s",
                after.state().label(),
                decision.action().label(),
                decision.reason().label(),
                decision.forwardBytes(),
                decision.backBytes(),
                decision.capped(),
                after.applyAttempts(),
                decision.requestsApply() ? applyResult : outcome.label());
        if (forwardTrigger != null || backTrigger != null) {
            evaluateMpvCaches(forwardTrigger, backTrigger,
                    MpvBackCachePolicy.SeekObservation.none(), now);
        } else {
            evaluateMpvCaches(
                    null,
                    null,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
        }
    }

    private void applyIjkAutoInitialControl() {
        if (!(engine instanceof IjkPlayerEngine ijk)
                || !playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        IjkBufferPolicy.Request request = buildIjkBufferRequest(
                context, now, false);
        IjkBufferPolicy.Decision policy = mergeIjkBufferDecision(
                IjkBufferPolicy.resolve(request));
        IjkBufferController.Decision decision =
                ijkBufferController.stageInitial(
                        playbackAutoSession, context.session(), policy);
        if (policy.managed()) {
            ijk.stageAutomaticInputBufferConfig(decision.targetConfig());
        }
        IjkDecodePressureController.Decision decodeDecision =
                ijkDecodePressureController.stageInitial(
                        playbackAutoSession,
                        context.session(),
                        hasAutomaticIjkDecodeOptions());
        if (hasAutomaticIjkDecodeOptions()) {
            ijk.stageAutomaticDecodeControlConfig(
                    mergeIjkDecodeConfig(decodeDecision.targetConfig()));
        }
        publishIjkBufferDecision(
                decision, request, IjkBufferController.Trigger.INITIAL,
                false, true, now);
        publishIjkDecodePressureDecision(
                decodeDecision,
                null,
                false,
                false,
                now);
    }

    private void restoreIjkStagedBufferConfig() {
        if (!(engine instanceof IjkPlayerEngine ijk)
                || !PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER,
                PlaybackPerformanceCatalog.IJK_WATER,
                PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE)
                || !playbackAutoSession.active()) return;
        IjkBufferController.Snapshot snapshot = ijkBufferController.snapshot();
        if (!playbackAutoSession.equals(snapshot.session())) return;
        ijk.stageAutomaticInputBufferConfig(
                mergeIjkBufferConfig(snapshot.stagedConfig()));
        IjkDecodePressureController.Snapshot decode =
                ijkDecodePressureController.snapshot();
        if (playbackAutoSession.equals(decode.session())) {
            ijk.stageAutomaticDecodeControlConfig(
                    mergeIjkDecodeConfig(decode.stagedConfig()));
        }
    }

    private void onIjkBufferMemoryUpdate(
            PlaybackMemoryCoordinator.Update update) {
        if (update == null || !playbackAutoSession.active()
                || !playbackAutoSession.equals(update.session())) return;
        evaluateIjkBuffer(IjkBufferController.Trigger.MEMORY,
                SystemClock.elapsedRealtime());
    }

    private void evaluateIjkBuffer(
            IjkBufferController.Trigger trigger,
            long nowElapsedMs) {
        if (!(engine instanceof IjkPlayerEngine ijk)
                || !playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        IjkBufferPolicy.Request request = buildIjkBufferRequest(
                context, now, true);
        IjkBufferPolicy.Decision policy = mergeIjkBufferDecision(
                IjkBufferPolicy.resolve(request));
        IjkBufferController.Decision decision = ijkBufferController.evaluate(
                playbackAutoSession,
                context.session(),
                policy,
                ijk.getAppliedInputBufferConfig(),
                trigger,
                player != null && player.getPlaybackState()
                        == Player.STATE_BUFFERING,
                playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                        || playbackTrace.hasStage(
                        PlaybackTrace.Stage.AUDIO_PLAYABLE),
                request.rebufferUsable() ? request.rebufferCount() : 0,
                now);

        boolean applyStarted = false;
        boolean applySucceeded = decision.action()
                != IjkBufferController.Action.RELOAD;
        if (decision.requestsReload()) {
            boolean safetyReload = decision.reason()
                    == IjkBufferController.Reason.SAFETY_RELOAD;
            PlaybackExperimentPolicy.Action reloadAction = safetyReload
                    ? PlaybackExperimentPolicy.Action.IJK_BUFFER_SAFETY_RELOAD
                    : PlaybackExperimentPolicy.Action.IJK_BUFFER_RELOAD;
            if (!experimentAllowed(reloadAction)) {
                decision = ijkBufferController.deferExperimentalReload(
                        playbackAutoSession, decision);
                applySucceeded = false;
            } else {
                applyStarted = ijkBufferController.beginApply(
                        playbackAutoSession, decision);
                if (applyStarted) {
                    ijk.stageAutomaticInputBufferConfig(
                            mergeIjkBufferConfig(
                                    ijkBufferController.snapshot().stagedConfig()));
                    pendingIjkBufferDecision = decision;
                    boolean restartStarted = restartIjkBuffer(ijk, decision);
                    applySucceeded = restartStarted
                            && decision.targetConfig().equals(
                            ijk.getAppliedInputBufferConfig());
                    if (!applySucceeded) {
                        completeIjkBufferManagedReload(
                                false, "start-failed", now, false);
                        if (!restartStarted) ijkBufferManagedReload = false;
                    }
                }
            }
        }
        if (policy.managed()) {
            ijk.stageAutomaticInputBufferConfig(
                    mergeIjkBufferConfig(
                            ijkBufferController.snapshot().stagedConfig()));
        }
        publishIjkBufferDecision(
                decision, request, trigger, applyStarted,
                applySucceeded, now);
    }

    private IjkBufferPolicy.Request buildIjkBufferRequest(
            PlaybackAutoContext context,
            long nowElapsedMs,
            boolean allowEngineScene) {
        PlaybackAutoContext current = context == null
                ? PlaybackAutoContext.empty() : context;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                current.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> streamFact =
                current.resource().streamKind();
        PlaybackAutoContext.Fact<PlaybackAutoContext.ManifestFacts> manifestFact =
                current.resource().manifest();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                current.device().memoryPressure();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> snapshotFact =
                current.device().memorySnapshot();
        PlaybackAutoContext.Fact<Long> bitrateFact =
                current.runtime().mediaBitrateBitsPerSecond();
        PlaybackAutoContext.Fact<Integer> rebufferFact =
                current.runtime().rebufferCount();
        PlaybackAutoContext.Fact<Long> liveLagFact =
                current.runtime().liveLagMs();
        boolean protocolUsable = protocolFact.isUsable(now);
        PlaybackAutoContext.Protocol protocol = protocolUsable
                ? protocolFact.value() : PlaybackAutoContext.Protocol.UNKNOWN;
        boolean streamUsable = streamFact.isUsable(now);
        PlaybackAutoContext.StreamKind stream = streamUsable
                ? streamFact.value() : PlaybackAutoContext.StreamKind.UNKNOWN;
        int configuredScene = PlaybackPerformanceSetting.isOverridden(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_SCENE)
                ? IjkPerformanceSetting.getScene()
                : IjkPerformanceSetting.SCENE_AUTO;
        if (configuredScene != IjkPerformanceSetting.SCENE_AUTO) {
            stream = switch (configuredScene) {
                case IjkPerformanceSetting.SCENE_VOD ->
                        PlaybackAutoContext.StreamKind.VOD;
                case IjkPerformanceSetting.SCENE_LIVE_LOW_LATENCY ->
                        PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE;
                default -> PlaybackAutoContext.StreamKind.LIVE;
            };
            streamUsable = true;
        }
        boolean segmented = protocol == PlaybackAutoContext.Protocol.HLS
                || protocol == PlaybackAutoContext.Protocol.DASH;
        if (!streamUsable && allowEngineScene && !segmented
                && engine instanceof IjkPlayerEngine ijk) {
            if (ijk.isVod()) {
                stream = PlaybackAutoContext.StreamKind.VOD;
                streamUsable = true;
            } else if (ijk.isLive()) {
                stream = PlaybackAutoContext.StreamKind.LIVE;
                streamUsable = true;
            }
        }
        boolean snapshotUsable = snapshotFact.isUsable(now)
                && snapshotFact.value().hasEvidence();
        return new IjkBufferPolicy.Request(
                PlaybackPerformanceSetting.hasAutomaticOptions(
                        PlayerSetting.IJK,
                        PlaybackPerformanceCatalog.IJK_BUFFER,
                        PlaybackPerformanceCatalog.IJK_WATER),
                isIjk(),
                protocolUsable,
                protocol,
                streamUsable,
                stream,
                manifestFact.isUsable(now),
                manifestFact.value(),
                pressureFact.isUsable(now),
                pressureFact.value(),
                snapshotUsable,
                snapshotFact.value(),
                bitrateFact.isUsable(now) && bitrateFact.value() > 0,
                bitrateFact.isUsable(now) ? bitrateFact.value() : 0,
                rebufferFact.isUsable(now),
                rebufferFact.isUsable(now) ? rebufferFact.value() : 0,
                liveLagFact.isUsable(now) && liveLagFact.value() >= 0,
                liveLagFact.isUsable(now) ? liveLagFact.value() : -1);
    }

    private IjkBufferPolicy.Config mergeIjkBufferConfig(
            IjkBufferPolicy.Config automatic) {
        IjkBufferPolicy.Config safe = automatic == null
                ? IjkBufferPolicy.safeInitialConfig() : automatic;
        int bufferMb = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER)
                ? safe.bufferMb() : IjkPerformanceSetting.getBufferMb();
        if (PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_WATER)) {
            return new IjkBufferPolicy.Config(
                    bufferMb,
                    safe.firstWaterMs(),
                    safe.nextWaterMs(),
                    safe.lastWaterMs());
        }
        return new IjkBufferPolicy.Config(
                bufferMb,
                IjkPerformanceSetting.getFirstWaterMs(),
                IjkPerformanceSetting.getNextWaterMs(),
                IjkPerformanceSetting.getLastWaterMs());
    }

    private IjkBufferPolicy.Decision mergeIjkBufferDecision(
            IjkBufferPolicy.Decision decision) {
        IjkBufferPolicy.Decision safe = decision == null
                ? IjkBufferPolicy.resolve(null) : decision;
        return new IjkBufferPolicy.Decision(
                safe.managed(),
                mergeIjkBufferConfig(safe.target()),
                safe.reason(),
                safe.memoryCeilingMb(),
                safe.liveLagHigh(),
                safe.targetOffsetMs(),
                safe.mediaDemandBytes());
    }

    private void evaluateIjkRealtimeRecovery(long nowElapsedMs) {
        if (!(engine instanceof IjkPlayerEngine ijk)
                || !playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                context.resource().protocol();
        boolean protocolUsable = protocolFact.isUsable(now);
        PlaybackAutoContext.Protocol protocol = protocolUsable
                ? protocolFact.value() : PlaybackAutoContext.Protocol.UNKNOWN;
        boolean automatic = PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_BUFFER,
                PlaybackPerformanceCatalog.IJK_WATER) && experimentAllowed(
                PlaybackExperimentPolicy.Action.IJK_REALTIME_REBUILD);
        boolean realtime = protocol == PlaybackAutoContext.Protocol.RTSP
                || protocol == PlaybackAutoContext.Protocol.RTMP;
        if (!automatic || !protocolUsable || !realtime) {
            ijkRealtimeRecoveryController.onPositionDiscontinuity(
                    playbackAutoSession);
            return;
        }
        IjkBufferController.Snapshot reloadState =
                ijkBufferController.snapshot();
        IjkRealtimeRecoveryController.Input input =
                new IjkRealtimeRecoveryController.Input(
                        playbackAutoSession,
                        automatic,
                        isIjk(),
                        protocolUsable,
                        protocol,
                        isIjkPlaybackActive(),
                        playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                                || playbackTrace.hasStage(
                                PlaybackTrace.Stage.AUDIO_PLAYABLE),
                        Math.abs(getSpeed() - 1f) < 0.01f,
                        false,
                        reloadState.applyInProgress(),
                        ijk.getRealtimeQueueSnapshot(),
                        ijk.getAppliedInputBufferConfig(),
                        now);
        IjkRealtimeRecoveryPolicy.Decision decision =
                ijkRealtimeRecoveryController.evaluate(input);
        IjkRealtimeRecoveryController.Snapshot stateAtDecision =
                ijkRealtimeRecoveryController.snapshot();
        IjkBufferController.Decision reloadGate = null;
        boolean actionStarted = false;
        boolean restartStarted = false;
        if (decision.requestsRecovery()) {
            reloadGate = ijkBufferController.requestRealtimeRecovery(
                    playbackAutoSession,
                    context.session(),
                    ijk.getAppliedInputBufferConfig(),
                    now);
            if (reloadGate.requestsReload()) {
                boolean reloadReserved = ijkBufferController.beginApply(
                        playbackAutoSession, reloadGate);
                boolean recoveryReserved = reloadReserved
                        && ijkRealtimeRecoveryController.beginAction(
                        playbackAutoSession, decision, now);
                actionStarted = reloadReserved && recoveryReserved;
                if (actionStarted) {
                    pendingIjkBufferDecision = reloadGate;
                    pendingIjkRealtimeRecoveryDecision = decision;
                    ijk.stageAutomaticInputBufferConfig(
                            mergeIjkBufferConfig(
                                    ijkBufferController.snapshot().stagedConfig()));
                    restartStarted = restartIjkRealtimeRecovery(
                            ijk, reloadGate, decision);
                    if (!restartStarted) {
                        completeIjkBufferManagedReload(
                                false, "start-failed", now, false);
                    }
                } else if (reloadReserved) {
                    ijkBufferController.completeApply(
                            playbackAutoSession, reloadGate, false, now);
                }
            }
        }
        publishIjkRealtimeRecoveryDecision(
                decision,
                protocol,
                reloadGate,
                stateAtDecision,
                actionStarted,
                restartStarted,
                now);
    }

    private void evaluateIjkDecodePressure(long nowElapsedMs) {
        if (!(engine instanceof IjkPlayerEngine ijk)
                || !playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        PlaybackAutoContext.DecoderFacts decoder = context.media().decoder();
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decodeFact =
                decoder.videoDecodeMode();
        PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermalFact =
                context.device().thermalState();
        PlaybackAutoContext.Fact<Float> frameRateFact =
                context.media().videoTrack().frameRate();
        boolean decoderUsable = decoder.trackSequence()
                == context.media().trackSequence()
                && decodeFact.isUsable(now);
        IjkDecodePressurePolicy.Input policyInput =
                new IjkDecodePressurePolicy.Input(
                        isIjkDecodePressureAutomatic()
                                && experimentAllowed(
                                PlaybackExperimentPolicy.Action.IJK_DECODE_REBUILD),
                        isIjk(),
                        isIjkPlaybackActive(),
                        playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                                || playbackTrace.hasStage(
                                PlaybackTrace.Stage.AUDIO_PLAYABLE),
                        Math.abs(getSpeed() - 1f) < 0.01f,
                        false,
                        false,
                        decoderUsable,
                        decoderUsable ? decodeFact.value()
                                : PlaybackAutoContext.DecodeMode.UNKNOWN,
                        thermalFact.isUsable(now),
                        thermalFact.isUsable(now) ? thermalFact.value()
                                : PlaybackAutoContext.ThermalState.UNKNOWN,
                        frameRateFact.isUsable(now)
                                && frameRateFact.value() > 0,
                        frameRateFact.isUsable(now)
                                ? frameRateFact.value() : -1f,
                        ijk.getDecodePressureSnapshot());
        IjkBufferController.Snapshot reloadState =
                ijkBufferController.snapshot();
        IjkDecodePressureController.Decision decision =
                ijkDecodePressureController.evaluate(
                        new IjkDecodePressureController.Input(
                                playbackAutoSession,
                                context.session(),
                                policyInput,
                                automaticIjkDecodeView(
                                        ijk.getAppliedDecodeControlConfig()),
                                reloadState.applyInProgress(),
                                now));

        IjkBufferController.Decision reloadGate = null;
        boolean actionStarted = false;
        boolean restartStarted = false;
        if (decision.requestsReload()) {
            reloadGate = ijkBufferController.requestDecodePressureReload(
                    playbackAutoSession,
                    context.session(),
                    ijk.getAppliedInputBufferConfig(),
                    now);
            if (reloadGate.requestsReload()) {
                boolean reloadReserved = ijkBufferController.beginApply(
                        playbackAutoSession, reloadGate);
                boolean decodeReserved = reloadReserved
                        && ijkDecodePressureController.beginAction(
                        playbackAutoSession, decision);
                actionStarted = reloadReserved && decodeReserved;
                if (actionStarted) {
                    pendingIjkBufferDecision = reloadGate;
                    pendingIjkDecodePressureDecision = decision;
                    ijk.stageAutomaticInputBufferConfig(
                            mergeIjkBufferConfig(
                                    ijkBufferController.snapshot().stagedConfig()));
                    ijk.stageAutomaticDecodeControlConfig(
                            mergeIjkDecodeConfig(
                                    ijkDecodePressureController.snapshot()
                                            .stagedConfig()));
                    restartStarted = restartIjkDecodePressure(
                            ijk, reloadGate, decision);
                    boolean applied = restartStarted
                            && mergeIjkDecodeConfig(
                            decision.targetConfig()).equals(
                            ijk.getAppliedDecodeControlConfig());
                    if (!applied) {
                        completeIjkBufferManagedReload(
                                false, "start-failed", now, false);
                        if (!restartStarted) ijkBufferManagedReload = false;
                    }
                } else if (reloadReserved) {
                    ijkBufferController.completeApply(
                            playbackAutoSession, reloadGate, false, now);
                }
            }
        }
        if (hasAutomaticIjkDecodeOptions()) {
            ijk.stageAutomaticDecodeControlConfig(
                    mergeIjkDecodeConfig(
                            ijkDecodePressureController.snapshot().stagedConfig()));
        }
        publishIjkDecodePressureDecision(
                decision,
                reloadGate,
                actionStarted,
                restartStarted,
                now);
    }

    private boolean hasAutomaticIjkDecodeOptions() {
        return PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE);
    }

    private boolean isIjkDecodePressureAutomatic() {
        return PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE);
    }

    private IjkDecodePressurePolicy.Config automaticIjkDecodeView(
            IjkDecodePressurePolicy.Config applied) {
        IjkDecodePressurePolicy.Config safe = applied == null
                ? IjkDecodePressurePolicy.automaticInitialConfig()
                : applied;
        return new IjkDecodePressurePolicy.Config(
                IjkDecodePressurePolicy.AUTOMATIC_PICTURE_QUEUE,
                safe.tuneMode());
    }

    private IjkDecodePressurePolicy.Config mergeIjkDecodeConfig(
            IjkDecodePressurePolicy.Config automatic) {
        IjkDecodePressurePolicy.Config safe = automatic == null
                ? IjkDecodePressurePolicy.automaticInitialConfig()
                : automatic;
        int pictureQueue = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE)
                ? safe.pictureQueue() : IjkPerformanceSetting.getPictureQueue();
        IjkDecodePressurePolicy.TuneMode tune = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.IJK,
                PlaybackPerformanceCatalog.IJK_SOFT_TUNE)
                ? safe.tuneMode() : switch (IjkPerformanceSetting.getSoftTuneMode()) {
            case IjkPerformanceSetting.SOFT_TUNE_AGGRESSIVE ->
                    IjkDecodePressurePolicy.TuneMode.AGGRESSIVE;
            case IjkPerformanceSetting.SOFT_TUNE_MILD ->
                    IjkDecodePressurePolicy.TuneMode.MILD;
            default -> IjkDecodePressurePolicy.TuneMode.OFF;
        };
        return new IjkDecodePressurePolicy.Config(pictureQueue, tune);
    }

    private boolean isIjkPlaybackActive() {
        if (player == null || !player.getPlayWhenReady()
                || player.getPlaybackState() != Player.STATE_READY) {
            return false;
        }
        try {
            return player.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean restartIjkBuffer(
            IjkPlayerEngine ijk,
            IjkBufferController.Decision decision) {
        if (spec == null || TextUtils.isEmpty(spec.getUrl())
                || player == null) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long position = ijk.isVod()
                ? Math.max(0, player.getCurrentPosition()) : C.TIME_UNSET;
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            initTrack = false;
            playWhenReady = wasPlayWhenReady;
            ijkBufferManagedReload = true;
            PlaybackTrace.log("ijk-buffer", playbackTrace.current(),
                    "action=reload old=%s target=%s resume=%d play=%s reason=%s",
                    decision.appliedConfig().label(),
                    decision.targetConfig().label(),
                    position == C.TIME_UNSET ? 0 : position,
                    wasPlayWhenReady,
                    decision.reason().label());
            ijk.restart(spec.checkUa(), position, wasPlayWhenReady);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-buffer", playbackTrace.current(),
                    "action=reload result=failed errorType=%s",
                    error.getClass().getSimpleName());
            return false;
        }
        try {
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-buffer", playbackTrace.current(),
                    "action=restore-state result=partial errorType=%s",
                    error.getClass().getSimpleName());
        }
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private boolean restartIjkRealtimeRecovery(
            IjkPlayerEngine ijk,
            IjkBufferController.Decision reloadGate,
            IjkRealtimeRecoveryPolicy.Decision recovery) {
        if (spec == null || TextUtils.isEmpty(spec.getUrl())
                || player == null) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            initTrack = false;
            playWhenReady = wasPlayWhenReady;
            ijkBufferManagedReload = true;
            PlaybackTrace.log("ijk-realtime", playbackTrace.current(),
                    "action=rebuild-session trigger=%s bufferedMs=%d bytes=%d packets=%d play=%s reloadReason=%s",
                    recovery.trigger().label(),
                    recovery.queue().playableDurationMs(),
                    recovery.queue().totalBytes(),
                    recovery.queue().totalPackets(),
                    wasPlayWhenReady,
                    reloadGate.reason().label());
            ijk.restart(spec.checkUa(), C.TIME_UNSET, wasPlayWhenReady);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-realtime", playbackTrace.current(),
                    "action=rebuild-session result=failed errorType=%s",
                    error.getClass().getSimpleName());
            return false;
        }
        try {
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-realtime", playbackTrace.current(),
                    "action=restore-state result=partial errorType=%s",
                    error.getClass().getSimpleName());
        }
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private boolean restartIjkDecodePressure(
            IjkPlayerEngine ijk,
            IjkBufferController.Decision reloadGate,
            IjkDecodePressureController.Decision decision) {
        if (spec == null || TextUtils.isEmpty(spec.getUrl())
                || player == null) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long position = ijk.isVod()
                ? Math.max(0, player.getCurrentPosition()) : C.TIME_UNSET;
        IjkDecodePressurePolicy.Metrics metrics =
                decision.assessment().metrics();
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            initTrack = false;
            playWhenReady = wasPlayWhenReady;
            ijkBufferManagedReload = true;
            PlaybackTrace.log("ijk-decode", playbackTrace.current(),
                    "action=reload old=%s target=%s pressure=%s thermalReason=%s targetFps=%d decodeFps=%d outputFps=%d play=%s reloadReason=%s",
                    decision.appliedConfig().label(),
                    decision.targetConfig().label(),
                    decision.assessment().pressure().label(),
                    decision.assessment().reason().label(),
                    Math.round(metrics.targetFps() * 1_000f),
                    Math.round(metrics.decodeFps() * 1_000f),
                    Math.round(metrics.outputFps() * 1_000f),
                    wasPlayWhenReady,
                    reloadGate.reason().label());
            ijk.restart(spec.checkUa(), position, wasPlayWhenReady);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-decode", playbackTrace.current(),
                    "action=reload result=failed errorType=%s",
                    error.getClass().getSimpleName());
            return false;
        }
        try {
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
        } catch (Throwable error) {
            PlaybackTrace.log("ijk-decode", playbackTrace.current(),
                    "action=restore-state result=partial errorType=%s",
                    error.getClass().getSimpleName());
        }
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private void completeIjkBufferManagedReload(
            boolean succeeded,
            String completionReason,
            long nowElapsedMs,
            boolean publishCompletion) {
        IjkBufferController.Decision pending = pendingIjkBufferDecision;
        IjkDecodePressureController.Decision decode =
                pendingIjkDecodePressureDecision;
        IjkRealtimeRecoveryPolicy.Decision recovery =
                pendingIjkRealtimeRecoveryDecision;
        pendingIjkBufferDecision = null;
        pendingIjkDecodePressureDecision = null;
        pendingIjkRealtimeRecoveryDecision = null;
        if (pending != null || decode != null || recovery != null) {
            long now = Math.max(0, nowElapsedMs);
            if (pending != null) {
                ijkBufferController.completeApply(
                        playbackAutoSession, pending, succeeded, now);
            }
            if (recovery != null) {
                ijkRealtimeRecoveryController.completeAction(
                        playbackAutoSession, succeeded);
            }
            if (decode != null) {
                ijkDecodePressureController.completeAction(
                        playbackAutoSession, succeeded);
            }
            if (engine instanceof IjkPlayerEngine ijk
                    && PlaybackPerformanceSetting.hasAutomaticOptions(
                    PlayerSetting.IJK,
                    PlaybackPerformanceCatalog.IJK_BUFFER,
                    PlaybackPerformanceCatalog.IJK_WATER,
                    PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE,
                    PlaybackPerformanceCatalog.IJK_SOFT_TUNE)
                    && playbackAutoSession.active()) {
                ijk.stageAutomaticInputBufferConfig(
                        mergeIjkBufferConfig(
                                ijkBufferController.snapshot().stagedConfig()));
                ijk.stageAutomaticDecodeControlConfig(
                        mergeIjkDecodeConfig(
                                ijkDecodePressureController.snapshot().stagedConfig()));
            }
            String domain = recovery != null ? "ijk-realtime"
                    : decode != null ? "ijk-decode" : "ijk-buffer";
            String target = decode != null
                    ? decode.targetConfig().label()
                    : pending == null ? "unknown"
                    : pending.targetConfig().label();
            PlaybackTrace.log(domain, playbackTrace.current(),
                    "action=reload-complete result=%s reason=%s target=%s",
                    succeeded ? "ready" : "failed",
                    PlaybackTelemetry.safeLabel(completionReason),
                    target);
            if (publishCompletion) {
                if (recovery != null) {
                    publishIjkRealtimeRecoveryCompletion(
                            recovery, succeeded, completionReason, now);
                } else if (decode != null) {
                    publishIjkDecodePressureCompletion(
                            decode, succeeded, completionReason, now);
                } else if (pending != null) {
                    publishIjkBufferCompletion(
                            pending, succeeded, completionReason, now);
                }
            }
        }
        ijkBufferManagedReload = false;
    }

    private void publishIjkDecodePressureCompletion(
            IjkDecodePressureController.Decision decision,
            boolean succeeded,
            String completionReason,
            long nowElapsedMs) {
        IjkDecodePressureController.Snapshot decode =
                ijkDecodePressureController.snapshot();
        IjkBufferController.Snapshot reload =
                ijkBufferController.snapshot();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "action_attempts", decode.actionAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "successful_actions", decode.successfulActions(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "failed_actions", decode.failedActions(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "shared_reload_attempts", reload.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_DECODE_PRESSURE,
                        succeeded ? PlaybackTelemetry.DecisionOutcome.APPLIED
                                : PlaybackTelemetry.DecisionOutcome.FAILED,
                        decision.appliedConfig().label(),
                        decision.targetConfig().label(),
                        succeeded ? decision.targetConfig().label()
                                : decision.appliedConfig().label(),
                        succeeded ? "reload-ready" : "reload-failed",
                        succeeded ? "none"
                                : PlaybackTelemetry.safeLabel(
                                completionReason),
                        inputs),
                nowElapsedMs);
    }

    private void publishIjkBufferCompletion(
            IjkBufferController.Decision decision,
            boolean succeeded,
            String completionReason,
            long nowElapsedMs) {
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        IjkBufferController.Snapshot snapshot = ijkBufferController.snapshot();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", snapshot.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "successful_reloads", snapshot.successfulReloads(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_BUFFER,
                        succeeded ? PlaybackTelemetry.DecisionOutcome.APPLIED
                                : PlaybackTelemetry.DecisionOutcome.FAILED,
                        decision.appliedConfig().label(),
                        decision.targetConfig().label(),
                        succeeded ? decision.targetConfig().label()
                                : decision.appliedConfig().label(),
                        succeeded ? "reload-ready" : "reload-failed",
                        succeeded ? "none"
                                : PlaybackTelemetry.safeLabel(completionReason),
                        inputs),
                nowElapsedMs);
    }

    private void publishIjkBufferDecision(
            IjkBufferController.Decision decision,
            IjkBufferPolicy.Request request,
            IjkBufferController.Trigger trigger,
            boolean applyStarted,
            boolean applySucceeded,
            long nowElapsedMs) {
        if (decision == null || request == null) return;
        PlaybackTelemetry.DecisionOutcome outcome = !decision.policy().managed()
                ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : decision.action() == IjkBufferController.Action.RELOAD
                ? applyStarted && applySucceeded
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : PlaybackTelemetry.DecisionOutcome.FAILED
                : decision.action() == IjkBufferController.Action.STAGE
                ? PlaybackTelemetry.DecisionOutcome.SELECTED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        String result = applyStarted && applySucceeded
                || decision.action() == IjkBufferController.Action.STAGE
                ? decision.targetConfig().label()
                : decision.appliedConfig().label();
        PlaybackAutoContext.Fact<Long> liveLagFact =
                playbackAutoContextStore.snapshot().runtime().liveLagMs();
        boolean liveLagFactUsable = liveLagFact.isUsable(nowElapsedMs);
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "trigger", trigger.name().toLowerCase(java.util.Locale.US),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "memory_ceiling_mb", decision.policy().memoryCeilingMb(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "target_first_ms", decision.targetConfig().firstWaterMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "target_next_ms", decision.targetConfig().nextWaterMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "target_last_ms", decision.targetConfig().lastWaterMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(request.mediaBitrateUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "media_bps", request.mediaBitrateBitsPerSecond(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown("media_bps"));
        inputs.add(request.liveLagUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "live_lag_ms", request.liveLagMs(),
                liveLagFactUsable ? liveLagFact.source()
                        : PlaybackAutoContext.ValueSource.UNKNOWN,
                liveLagFactUsable ? liveLagFact.confidence()
                        : PlaybackAutoContext.Confidence.UNKNOWN)
                : PlaybackTelemetry.DecisionInput.unknown("live_lag_ms"));
        inputs.add(request.rebufferUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "rebuffer_count", request.rebufferCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("rebuffer_count"));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts",
                ijkBufferController.snapshot().reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_BUFFER,
                        outcome,
                        decision.appliedConfig().label(),
                        decision.targetConfig().label(),
                        result,
                        decision.reason().label(),
                        decision.policy().reason().label(),
                        inputs),
                nowElapsedMs);
    }

    private void publishIjkDecodePressureDecision(
            IjkDecodePressureController.Decision decision,
            IjkBufferController.Decision reloadGate,
            boolean actionStarted,
            boolean restartStarted,
            long nowElapsedMs) {
        if (decision == null) return;
        boolean suppressed = switch (decision.reason()) {
            case STALE_SESSION,
                 STALE_SAMPLE,
                 NOT_MANAGED,
                 INELIGIBLE,
                 ACTION_PENDING -> true;
            default -> false;
        };
        PlaybackTelemetry.DecisionOutcome outcome;
        if (decision.action() == IjkDecodePressureController.Action.STAGE) {
            outcome = PlaybackTelemetry.DecisionOutcome.SELECTED;
        } else if (!decision.requestsReload()) {
            outcome = suppressed
                    ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                    : PlaybackTelemetry.DecisionOutcome.HELD;
        } else if (reloadGate != null && !reloadGate.requestsReload()) {
            outcome = PlaybackTelemetry.DecisionOutcome.HELD;
        } else {
            outcome = actionStarted && restartStarted
                    ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                    : PlaybackTelemetry.DecisionOutcome.FAILED;
        }
        String suppression = reloadGate != null
                && !reloadGate.requestsReload()
                ? reloadGate.reason().label()
                : suppressed ? decision.assessment().reason().label()
                : "none";
        String result = decision.action()
                == IjkDecodePressureController.Action.STAGE
                || actionStarted && restartStarted
                ? decision.targetConfig().label()
                : decision.appliedConfig().label();
        IjkDecodePressureController.Snapshot state =
                ijkDecodePressureController.snapshot();
        IjkBufferController.Snapshot reload =
                ijkBufferController.snapshot();
        IjkDecodePressurePolicy.Metrics metrics =
                decision.assessment().metrics();
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decoder =
                context.media().decoder().videoDecodeMode();
        PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermal =
                context.device().thermalState();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "pressure", decision.assessment().pressure().label(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(decoder.isUsable(now)
                ? PlaybackTelemetry.DecisionInput.text(
                "actual_decode", decoder.value().label(),
                decoder.source(), decoder.confidence())
                : PlaybackTelemetry.DecisionInput.unknown("actual_decode"));
        inputs.add(thermal.isUsable(now)
                ? PlaybackTelemetry.DecisionInput.text(
                "thermal", thermal.value().label(),
                thermal.source(), thermal.confidence())
                : PlaybackTelemetry.DecisionInput.unknown("thermal"));
        inputs.add(metrics.targetFps() > 0
                ? PlaybackTelemetry.DecisionInput.number(
                "target_fps_milli",
                Math.round(metrics.targetFps() * 1_000f),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown(
                "target_fps_milli"));
        inputs.add(metrics.fpsUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "decode_fps_milli",
                Math.round(metrics.decodeFps() * 1_000f),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown(
                "decode_fps_milli"));
        inputs.add(metrics.fpsUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "output_fps_milli",
                Math.round(metrics.outputFps() * 1_000f),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown(
                "output_fps_milli"));
        inputs.add(metrics.outputRatioPermille() >= 0
                ? PlaybackTelemetry.DecisionInput.number(
                "output_ratio_permille",
                metrics.outputRatioPermille(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown(
                "output_ratio_permille"));
        inputs.add(metrics.outputToDecodePermille() >= 0
                ? PlaybackTelemetry.DecisionInput.number(
                "output_decode_permille",
                metrics.outputToDecodePermille(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown(
                "output_decode_permille"));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", state.consecutiveRiskSamples(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "recovery_samples", state.consecutiveRecoverySamples(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", reload.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", reloadGate == null
                        ? 0 : reloadGate.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_DECODE_PRESSURE,
                        outcome,
                        decision.appliedConfig().label(),
                        decision.targetConfig().label(),
                        result,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        if (decision.action() != IjkDecodePressureController.Action.HOLD
                || state.consecutiveRiskSamples() > 0
                || state.consecutiveRecoverySamples() > 0
                || reloadGate != null) {
            PlaybackTrace.log("ijk-decode", playbackTrace.current(),
                    "action=%s reason=%s pressure=%s targetFps=%d decodeFps=%d outputFps=%d outputRatio=%d outputDecodeRatio=%d riskSamples=%d recoverySamples=%d reloadGate=%s result=%s",
                    decision.action().label(),
                    decision.reason().label(),
                    decision.assessment().pressure().label(),
                    Math.round(metrics.targetFps() * 1_000f),
                    Math.round(metrics.decodeFps() * 1_000f),
                    Math.round(metrics.outputFps() * 1_000f),
                    metrics.outputRatioPermille(),
                    metrics.outputToDecodePermille(),
                    state.consecutiveRiskSamples(),
                    state.consecutiveRecoverySamples(),
                    reloadGate == null ? "none"
                            : reloadGate.reason().label(),
                    outcome.label());
        }
    }

    private void publishIjkRealtimeRecoveryDecision(
            IjkRealtimeRecoveryPolicy.Decision decision,
            PlaybackAutoContext.Protocol protocol,
            IjkBufferController.Decision reloadGate,
            IjkRealtimeRecoveryController.Snapshot stateAtDecision,
            boolean actionStarted,
            boolean restartStarted,
            long nowElapsedMs) {
        if (decision == null) return;
        boolean suppressed = switch (decision.reason()) {
            case NOT_AUTOMATIC_IJK,
                 NOT_REALTIME_PROTOCOL,
                 INACTIVE,
                 STARTUP,
                 NON_UNIT_SPEED,
                 USER_SEEK,
                 ACTION_PENDING,
                 EVIDENCE_UNKNOWN,
                 STALE_SESSION,
                 STALE_SAMPLE -> true;
            default -> false;
        };
        PlaybackTelemetry.DecisionOutcome outcome;
        if (!decision.requestsRecovery()) {
            outcome = suppressed
                    ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                    : PlaybackTelemetry.DecisionOutcome.HELD;
        } else if (reloadGate != null && !reloadGate.requestsReload()) {
            outcome = PlaybackTelemetry.DecisionOutcome.HELD;
        } else {
            outcome = actionStarted && restartStarted
                    ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                    : PlaybackTelemetry.DecisionOutcome.FAILED;
        }
        String suppression = reloadGate != null
                && !reloadGate.requestsReload()
                ? reloadGate.reason().label()
                : suppressed ? decision.reason().label() : "none";
        IjkRealtimeRecoveryPolicy.QueueSnapshot queue = decision.queue();
        IjkBufferController.Snapshot reloadState =
                ijkBufferController.snapshot();
        IjkRealtimeRecoveryController.Snapshot recoveryState =
                stateAtDecision == null
                        ? ijkRealtimeRecoveryController.snapshot()
                        : stateAtDecision;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "protocol", protocol == null
                        ? PlaybackAutoContext.Protocol.UNKNOWN.label()
                        : protocol.label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "trigger", decision.trigger().label(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(queue.durationUsable()
                ? PlaybackTelemetry.DecisionInput.number(
                "buffered_ms", queue.playableDurationMs(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM)
                : PlaybackTelemetry.DecisionInput.unknown("buffered_ms"));
        inputs.add(decision.durationGrowthMsPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown(
                "duration_growth_msps")
                : PlaybackTelemetry.DecisionInput.number(
                "duration_growth_msps",
                decision.durationGrowthMsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cached_bytes", queue.totalBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(decision.bytesGrowthPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown("bytes_growth_ps")
                : PlaybackTelemetry.DecisionInput.number(
                "bytes_growth_ps", decision.bytesGrowthPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cached_packets", queue.totalPackets(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(decision.packetsGrowthPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown(
                "packets_growth_ps")
                : PlaybackTelemetry.DecisionInput.number(
                "packets_growth_ps", decision.packetsGrowthPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "occupancy_permille",
                queue.occupancyPermille(
                        decision.thresholds().maxBufferBytes()),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", recoveryState.consecutiveRiskSamples(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", reloadState.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", reloadGate == null
                        ? 0 : reloadGate.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_REALTIME_RECOVERY,
                        outcome,
                        "monitoring",
                        decision.action().label(),
                        actionStarted && restartStarted
                                ? "rebuild-pending" : "hold",
                        decision.reason().label(),
                        suppression,
                        inputs),
                nowElapsedMs);
        if (decision.trigger() != IjkRealtimeRecoveryPolicy.Trigger.NONE
                || reloadGate != null) {
            PlaybackTrace.log("ijk-realtime", playbackTrace.current(),
                    "action=%s reason=%s trigger=%s bufferedMs=%d bytes=%d packets=%d durationGrowth=%d byteGrowth=%d packetGrowth=%d samples=%d reloadGate=%s result=%s",
                    decision.action().label(),
                    decision.reason().label(),
                    decision.trigger().label(),
                    queue.playableDurationMs(),
                    queue.totalBytes(),
                    queue.totalPackets(),
                    decision.durationGrowthMsPerSecond(),
                    decision.bytesGrowthPerSecond(),
                    decision.packetsGrowthPerSecond(),
                    recoveryState.consecutiveRiskSamples(),
                    reloadGate == null ? "none"
                            : reloadGate.reason().label(),
                    outcome.label());
        }
    }

    private void publishIjkRealtimeRecoveryCompletion(
            IjkRealtimeRecoveryPolicy.Decision decision,
            boolean succeeded,
            String completionReason,
            long nowElapsedMs) {
        IjkRealtimeRecoveryController.Snapshot recovery =
                ijkRealtimeRecoveryController.snapshot();
        IjkBufferController.Snapshot reload =
                ijkBufferController.snapshot();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "recovery_attempts", recovery.recoveryAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "successful_recoveries", recovery.successfulRecoveries(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "failed_recoveries", recovery.failedRecoveries(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "shared_reload_attempts", reload.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_REALTIME_RECOVERY,
                        succeeded ? PlaybackTelemetry.DecisionOutcome.APPLIED
                                : PlaybackTelemetry.DecisionOutcome.FAILED,
                        "rebuild-pending",
                        "ready",
                        succeeded ? "ready" : "failed",
                        succeeded ? "rebuild-ready" : "rebuild-failed",
                        succeeded ? "none"
                                : PlaybackTelemetry.safeLabel(
                                completionReason),
                        inputs),
                nowElapsedMs);
        PlaybackTrace.log("ijk-realtime", playbackTrace.current(),
                "action=rebuild-complete result=%s trigger=%s attempts=%d successes=%d failures=%d reason=%s",
                succeeded ? "ready" : "failed",
                decision.trigger().label(),
                recovery.recoveryAttempts(),
                recovery.successfulRecoveries(),
                recovery.failedRecoveries(),
                PlaybackTelemetry.safeLabel(completionReason));
    }

    private void activateIjkRuntimeProfileIfEligible(long nowElapsedMs) {
        if (ijkRuntimeManualOverride
                || !playbackAutoSession.active()
                || playerType != PlayerSetting.IJK
                || !PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK)
                || playerType != PlayerSetting.IJK) return;
        long now = Math.max(0, nowElapsedMs);
        IjkRuntimeProfileController.Facts facts = currentIjkRuntimeFacts(now);
        IjkRuntimeProfileController.RuntimeSample sample =
                currentIjkRuntimeSample(null, now);
        IjkRuntimeProfilePolicy.Path path = engine != null && engine.isHard()
                ? IjkRuntimeProfilePolicy.Path.IJK_HARD
                : IjkRuntimeProfilePolicy.Path.IJK_SOFT;
        if (!ijkRuntimeProfileController.activate(
                playbackAutoSession, path, facts, sample, now)) return;
        PlaybackTrace.log(
                "ijk-runtime-profile",
                playbackTrace.current(),
                "action=activate path=%s profile=%s",
                path.label(),
                ijkRuntimeProfileController.snapshot().profileId());
    }

    private IjkRuntimeProfileController.Facts currentIjkRuntimeFacts(
            long nowElapsedMs) {
        boolean automatic = PlayerSetting.getPlayer() == PlayerSetting.IJK
                && PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK)
                && !ijkRuntimeManualOverride;
        return IjkRuntimeProfileController.Facts.fromContext(
                playbackAutoContextStore.snapshot(),
                automatic,
                Math.max(0, nowElapsedMs));
    }

    private IjkRuntimeProfileController.RuntimeSample currentIjkRuntimeSample(
            PlaybackTelemetry.RuntimeObservation observation,
            long nowElapsedMs) {
        boolean active = false;
        if (player != null) {
            try {
                active = player.getPlaybackState() == Player.STATE_READY
                        && player.getPlayWhenReady()
                        && player.isPlaying();
            } catch (Throwable ignored) {
            }
        }
        boolean decodeFpsUsable = false;
        float decodeFps = 0f;
        boolean outputFpsUsable = false;
        float outputFps = 0f;
        boolean dropRateUsable = false;
        int dropRatePermille = 0;
        if (engine instanceof IjkPlayerEngine ijk) {
            IjkDecodePressurePolicy.DecodeSnapshot decode =
                    ijk.getDecodePressureSnapshot();
            decodeFpsUsable = decode.available()
                    && decode.decodeFps() > 0;
            decodeFps = decodeFpsUsable ? decode.decodeFps() : 0f;
            outputFpsUsable = decode.available()
                    && decode.outputFps() > 0;
            outputFps = outputFpsUsable ? decode.outputFps() : 0f;
            IjkPlayerEngine.DropRateSnapshot drop =
                    ijk.getDropRateSnapshot();
            dropRateUsable = drop.available();
            dropRatePermille = drop.permille();
        } else if (observation != null
                && observation.renderedFrameRate().known()
                && observation.renderedFrameRate().value() > 0) {
            outputFpsUsable = true;
            outputFps = observation.renderedFrameRate().value();
        }
        int rebufferCount = observation != null
                && observation.rebufferCount().known()
                ? Math.max(0, observation.rebufferCount().value())
                : playbackBufferingTracker.getRebufferCount();
        boolean droppedFramesUsable = observation != null
                && observation.droppedFrames().known();
        long droppedFrames = droppedFramesUsable
                ? Math.max(0, observation.droppedFrames().value()) : 0;
        long nativeHeapBytes = -1;
        long pssBytes = -1;
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        long now = Math.max(0, nowElapsedMs);
        if (playbackAutoSession.equals(context.session())) {
            PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot>
                    memory = context.device().memorySnapshot();
            if (memory.isUsable(now)
                    && memory.value().nativeHeapAllocatedBytes() != null) {
                nativeHeapBytes = memory.value().nativeHeapAllocatedBytes();
            }
            PlaybackAutoContext.Fact<Long> pss =
                    context.device().diagnosticPssBytes();
            if (pss.isUsable(now) && pss.value() >= 0) {
                pssBytes = pss.value();
            }
        }
        return new IjkRuntimeProfileController.RuntimeSample(
                active,
                rebufferCount,
                decodeFpsUsable,
                decodeFps,
                outputFpsUsable,
                outputFps,
                dropRateUsable,
                dropRatePermille,
                droppedFramesUsable,
                droppedFrames,
                nativeHeapBytes,
                pssBytes);
    }

    private void onIjkRuntimeFirstFrame(long nowElapsedMs) {
        if (!playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        IjkRuntimeProfileController.Observation observation =
                ijkRuntimeProfileController.onFirstFrame(
                        playbackAutoSession,
                        currentIjkRuntimeFacts(now),
                        currentIjkRuntimeSample(null, now),
                        now,
                        System.currentTimeMillis());
        publishIjkRuntimeObservation(observation, now);
    }

    private void evaluateIjkRuntimeProfile(
            PlaybackTelemetry.RuntimeObservation runtime,
            long nowElapsedMs) {
        if (!playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        IjkRuntimeProfileController.Observation observation =
                ijkRuntimeProfileController.observe(
                        playbackAutoSession,
                        currentIjkRuntimeFacts(now),
                        currentIjkRuntimeSample(runtime, now),
                        now,
                        System.currentTimeMillis());
        publishIjkRuntimeObservation(observation, now);
    }

    private void finishIjkRuntimeProfileSession(
            PlaybackTelemetry.RuntimeObservation runtime,
            long nowElapsedMs) {
        if (!playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        ijkRuntimeProfileController.finishSession(
                playbackAutoSession,
                currentIjkRuntimeFacts(now),
                currentIjkRuntimeSample(runtime, now),
                System.currentTimeMillis());
    }

    private boolean retryIjkRuntimeProfileFallback(
            PlaybackException error,
            PlaybackErrorClassifier.Failure failure,
            PlayerEngine.ErrorAction engineAction) {
        if (engineAction == PlayerEngine.ErrorAction.RECOVERED
                || error == null
                || failure == null
                || !experimentAllowed(
                PlaybackExperimentPolicy.Action.IJK_RUNTIME_KERNEL_FALLBACK)
                || !playbackAutoSession.active()) return false;
        long now = SystemClock.elapsedRealtime();
        IjkPlayerEngine.ErrorSnapshot ijkError =
                engine instanceof IjkPlayerEngine ijk
                        ? ijk.getLastErrorSnapshot()
                        : IjkPlayerEngine.ErrorSnapshot.none();
        IjkRuntimeProfileController.Decision decision =
                ijkRuntimeProfileController.handleFailure(
                        playbackAutoSession,
                        currentIjkRuntimeFacts(now),
                        currentIjkRuntimeSample(
                                collectPlaybackTelemetry(
                                        PlaybackAutoContext.PlaybackPhase.ERROR,
                                        now),
                                now),
                        new IjkRuntimeProfileController.FailureEvent(
                                failure.stage(),
                                error.errorCode,
                                ijkError.what(),
                                ijkError.extra(),
                                ijkError.prepared()),
                        now,
                        System.currentTimeMillis());
        publishIjkRuntimeFailureDecision(decision, now);
        if (!decision.requestsSwitch()) return false;
        boolean switched = switchIjkRuntimeFallback(decision);
        if (!switched) {
            ijkRuntimeProfileController.onSwitchStartFailed(
                    playbackAutoSession, System.currentTimeMillis());
            publishIjkRuntimeSwitchStartFailure("switch-start-failed");
        }
        return switched;
    }

    private boolean switchIjkRuntimeFallback(
            IjkRuntimeProfileController.Decision decision) {
        if (decision == null
                || !decision.requestsSwitch()
                || engine == null
                || player == null
                || spec == null
                || TextUtils.isEmpty(spec.getUrl())) return false;
        IjkRuntimeProfilePolicy.Path targetPath = decision.targetPath();
        int targetPlayer = playerTypeForIjkRuntimePath(targetPath);
        int targetDecode = targetPath == IjkRuntimeProfilePolicy.Path.IJK_SOFT
                ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long now = SystemClock.elapsedRealtime();
        long position = ijkRuntimeVodResumePosition(now);
        PlayerEngine replacement;
        try {
            replacement = buildEngine(targetPlayer, targetDecode);
        } catch (Throwable error) {
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=build-fallback target=%s result=failed errorType=%s",
                    targetPath.label(),
                    error.getClass().getSimpleName());
            return false;
        }
        Player replacementPlayer = replacement.getPlayer();
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            App.removeCallbacks(networkProtectionRunnable);
            resetNetworkProtectionSession("ijk-runtime-fallback");
            resetLutRuntimeState("ijk_runtime_fallback", true);
            stopNativeAudioSession();
            stopParse();
            engine.release();
            engine = replacement;
            player = replacementPlayer;
            playerType = targetPlayer;
            playWhenReady = wasPlayWhenReady;
            hardDecodeSwitchRetryArmed = false;
            initTrack = false;
            ijkRuntimeTemporaryFallback = true;
            pendingIjkRuntimeFallbackReparse = false;
            callback.onPlayerRebuild(player, false);
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=switch from=%s target=%s count=%d resume=%d play=%s",
                    decision.fromPath().label(),
                    targetPath.label(),
                    decision.fallbackCount(),
                    position == C.TIME_UNSET ? 0 : position,
                    wasPlayWhenReady);
            pendingIjkRuntimeFallbackReparse = true;
            if (reparseForPlayerSwitch(position, speed, repeat)) {
                return true;
            }
            pendingIjkRuntimeFallbackReparse = false;
            setMediaItem(Constant.TIMEOUT_PLAY);
            if (position > 0) seekTo(position);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            return true;
        } catch (Throwable error) {
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=switch target=%s result=failed errorType=%s",
                    targetPath.label(),
                    error.getClass().getSimpleName());
            try {
                if (engine != replacement) replacement.release();
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private long ijkRuntimeVodResumePosition(long nowElapsedMs) {
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> stream =
                context.resource().streamKind();
        if (!playbackAutoSession.equals(context.session())
                || !stream.isUsable(Math.max(0, nowElapsedMs))
                || stream.value() != PlaybackAutoContext.StreamKind.VOD) {
            return C.TIME_UNSET;
        }
        return Math.max(0, getPosition());
    }

    private static int playerTypeForIjkRuntimePath(
            IjkRuntimeProfilePolicy.Path path) {
        if (path == IjkRuntimeProfilePolicy.Path.MPV) {
            return PlayerSetting.MPV;
        }
        if (path == IjkRuntimeProfilePolicy.Path.EXO) {
            return PlayerSetting.EXO;
        }
        return PlayerSetting.IJK;
    }

    private void prepareIjkRuntimeForUserPlayback() {
        ijkRuntimeManualOverride = false;
        pendingIjkRuntimeFallbackReparse = false;
        if (!ijkRuntimeTemporaryFallback) return;
        if (PlayerSetting.getPlayer() != PlayerSetting.IJK
                || !PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK)) {
            ijkRuntimeTemporaryFallback = false;
            return;
        }
        if (engine instanceof IjkPlayerEngine && engine.isHard()) {
            playerType = PlayerSetting.IJK;
            ijkRuntimeTemporaryFallback = false;
            return;
        }
        PlayerEngine replacement;
        try {
            replacement = buildEngine(PlayerSetting.IJK, PlayerEngine.HARD);
        } catch (Throwable error) {
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=restore-default result=failed errorType=%s",
                    error.getClass().getSimpleName());
            return;
        }
        try {
            prepareSeq++;
            stopNativeAudioSession();
            if (engine != null) engine.release();
            engine = replacement;
            player = replacement.getPlayer();
            playerType = PlayerSetting.IJK;
            callback.onPlayerRebuild(player, false);
            ijkRuntimeTemporaryFallback = false;
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=restore-default target=ijk-hard result=applied");
        } catch (Throwable error) {
            PlaybackTrace.log(
                    "ijk-runtime-profile",
                    playbackTrace.current(),
                    "action=restore-default result=failed errorType=%s",
                    error.getClass().getSimpleName());
        }
    }

    private void beginIjkRuntimeManualOverride() {
        ijkRuntimeManualOverride = true;
        ijkRuntimeTemporaryFallback = false;
        pendingIjkRuntimeFallbackReparse = false;
        ijkRuntimeProfileController.cancel(playbackAutoSession);
    }

    private void publishIjkRuntimeObservation(
            IjkRuntimeProfileController.Observation observation,
            long nowElapsedMs) {
        if (observation == null || !observation.material()) return;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "profile", observation.profileId(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "rebuffer_count", observation.rebufferCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        addNumberInput(inputs, "drop_rate_permille",
                observation.dropRatePermille(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "rendered_ratio_permille",
                observation.renderedRatioPermille(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "native_growth_bytes",
                observation.nativeHeapGrowthBytes(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "pss_growth_bytes",
                observation.pssGrowthBytes(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.LOW);
        PlaybackTelemetry.DecisionOutcome outcome =
                observation.fallbackSucceeded()
                        ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : observation.action()
                        == IjkRuntimeProfileController.ObservationAction.STABLE
                        ? PlaybackTelemetry.DecisionOutcome.SELECTED
                        : PlaybackTelemetry.DecisionOutcome.OBSERVED;
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_RUNTIME_PROFILE,
                        outcome,
                        observation.path().label(),
                        observation.action().label(),
                        observation.path().label(),
                        observation.reason().label(),
                        "none",
                        inputs),
                nowElapsedMs);
        PlaybackTrace.log(
                "ijk-runtime-profile",
                playbackTrace.current(),
                "action=%s reason=%s key=%s path=%s stable=%s fallbackSuccess=%s rebuffers=%d dropPermille=%d renderedPermille=%d nativeGrowth=%d pssGrowth=%d",
                observation.action().label(),
                observation.reason().label(),
                observation.profileId(),
                observation.path().label(),
                observation.health().stable(),
                observation.fallbackSucceeded(),
                observation.rebufferCount(),
                observation.dropRatePermille(),
                observation.renderedRatioPermille(),
                observation.nativeHeapGrowthBytes(),
                observation.pssGrowthBytes());
    }

    private void publishIjkRuntimeFailureDecision(
            IjkRuntimeProfileController.Decision decision,
            long nowElapsedMs) {
        if (decision == null
                || decision.reason()
                == IjkRuntimeProfileController.Reason.NOT_MANAGED) return;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "profile", decision.profileId(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "failure_kind", decision.assessment().kind().label(),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "fallback_count", decision.fallbackCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "failure_persisted", decision.failurePersisted(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "fallback_failure_recorded",
                decision.fallbackFailureRecorded(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_RUNTIME_PROFILE,
                        decision.requestsSwitch()
                                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                                : PlaybackTelemetry.DecisionOutcome.HELD,
                        decision.fromPath().label(),
                        decision.targetPath() == null
                                ? "hold" : decision.targetPath().label(),
                        decision.requestsSwitch()
                                ? "switch-pending" : decision.fromPath().label(),
                        decision.reason().label(),
                        decision.requestsSwitch()
                                ? "none" : decision.reason().label(),
                        inputs),
                nowElapsedMs);
        PlaybackTrace.log(
                "ijk-runtime-profile",
                playbackTrace.current(),
                "action=%s reason=%s key=%s from=%s target=%s kind=%s persist=%s fallbackCount=%d",
                decision.action().label(),
                decision.reason().label(),
                decision.profileId(),
                decision.fromPath().label(),
                decision.targetPath() == null
                        ? "none" : decision.targetPath().label(),
                decision.assessment().kind().label(),
                decision.failurePersisted(),
                decision.fallbackCount());
    }

    private void publishIjkRuntimeSwitchStartFailure(String reason) {
        long now = SystemClock.elapsedRealtime();
        IjkRuntimeProfileController.Snapshot snapshot =
                ijkRuntimeProfileController.snapshot();
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.IJK_RUNTIME_PROFILE,
                        PlaybackTelemetry.DecisionOutcome.FAILED,
                        snapshot.currentPath().label(),
                        snapshot.currentPath().label(),
                        "failed",
                        IjkRuntimeProfileController.Reason
                                .SWITCH_START_FAILED.label(),
                        PlaybackTelemetry.safeLabel(reason),
                        List.of(
                                PlaybackTelemetry.DecisionInput.number(
                                        "fallback_count",
                                        snapshot.fallbackCount(),
                                        PlaybackAutoContext.ValueSource
                                                .PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.HIGH))),
                now);
    }

    private void onMpvResourceMemoryUpdate(PlaybackMemoryCoordinator.Update update) {
        if (update == null || !playbackAutoSession.active()
                || !playbackAutoSession.equals(update.session())) return;
        evaluateMpvCaches(
                MpvForwardCacheController.Trigger.MEMORY,
                MpvBackCacheController.Trigger.MEMORY,
                MpvBackCachePolicy.SeekObservation.none(),
                SystemClock.elapsedRealtime());
    }

    private void onMpvResourceSystemUpdate(
            PlaybackSystemConditionCoordinator.Update update) {
        if (update == null || !playbackAutoSession.active()
                || !playbackAutoSession.equals(update.session())) return;
        evaluateMpvCaches(
                MpvForwardCacheController.Trigger.RESOURCE,
                MpvBackCacheController.Trigger.RESOURCE,
                MpvBackCachePolicy.SeekObservation.none(),
                SystemClock.elapsedRealtime());
    }

    private void evaluateMpvCaches(
            MpvForwardCacheController.Trigger forwardTrigger,
            MpvBackCacheController.Trigger backTrigger,
            MpvBackCachePolicy.SeekObservation seekObservation,
            long nowElapsedMs) {
        if (!(engine instanceof MpvPlayerEngine mpv) || !playbackAutoSession.active()) return;
        long now = Math.max(0, nowElapsedMs);
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        boolean automaticForward = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BUFFER_BYTES);
        boolean automaticBack = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.BACK_BUFFER);
        boolean automaticPreload = PlaybackPerformanceSetting.isAuto(
                PlayerSetting.MPV,
                PlaybackPerformanceCatalog.PRELOAD);
        boolean automatic = automaticForward || automaticBack || automaticPreload;
        boolean performancePriority = MpvPerformanceSetting.isPerformancePriority();
        MpvForwardCacheController.Snapshot forwardBefore = mpvForwardCacheController.snapshot();
        MpvBackCacheController.Snapshot backBefore = mpvBackCacheController.snapshot();
        long baseline = forwardBefore.initialBaselineBytes() > 0
                ? forwardBefore.initialBaselineBytes() : MpvForwardCachePolicy.MIN_FORWARD_BYTES;
        long resourceForward = forwardBefore.controlledTargetBytes() > 0
                ? forwardBefore.controlledTargetBytes() : baseline;
        long resourceBack = backBefore.controlledTargetBytes() >= 0
                ? backBefore.controlledTargetBytes() : 0;
        MpvResourcePressurePolicy.Assessment resourceAssessment =
                MpvResourcePressurePolicy.assess(
                        context,
                        automatic,
                        isMpv(),
                        performancePriority,
                        now);
        MpvResourcePressureController.Snapshot resourceBefore =
                mpvResourcePressureController.snapshot();
        MpvResourcePressureController.Trigger resourceTrigger =
                mpvResourceTrigger(forwardTrigger, backTrigger);
        MpvResourcePressureController.Decision resourceDecision =
                mpvResourcePressureController.evaluate(
                        playbackAutoSession,
                        context.session(),
                        resourceAssessment,
                        resourceTrigger,
                        resourceForward,
                        resourceBack,
                        now);
        boolean resourcePreloadChanged = resourceBefore.preloadAllowed()
                != resourceDecision.preloadAllowed();
        publishMpvResourcePressureDecision(
                resourceBefore,
                resourceAssessment,
                resourceDecision,
                resourcePreloadChanged,
                now);
        evaluateMpvPreload(mpv, context, resourceDecision, automaticPreload,
                performancePriority, now);
        MpvForwardCachePolicy.Assessment forwardAssessment = MpvForwardCachePolicy.assess(
                context,
                automaticForward,
                isMpv(),
                performancePriority,
                baseline,
                now);
        MpvForwardCacheController.Decision forwardDecision = !automaticForward
                || forwardTrigger == null
                ? null : mpvForwardCacheController.evaluate(
                playbackAutoSession, context.session(), forwardAssessment, resourceDecision,
                forwardTrigger, now);
        MpvForwardCacheController.Snapshot forwardEvaluated =
                mpvForwardCacheController.snapshot();
        long currentForward = !automaticForward
                ? mpv.getConfiguredForwardCacheBytes()
                : forwardEvaluated.controlledTargetBytes() > 0
                ? forwardEvaluated.controlledTargetBytes() : baseline;
        long targetForward = forwardDecision != null && forwardDecision.requestsApply()
                ? forwardDecision.targetBytes() : currentForward;

        boolean seekableForBack = isCurrentMpvMediaSeekable()
                || backTrigger == MpvBackCacheController.Trigger.REBUILD
                && backBefore.controlledTargetBytes() > 0;
        MpvBackCachePolicy.Request backRequest = MpvBackCachePolicy.requestFrom(
                context,
                automaticBack,
                isMpv(),
                performancePriority,
                seekableForBack,
                isCurrentMpvMediaLive(),
                targetForward,
                forwardAssessment,
                now);
        MpvBackCachePolicy.Assessment backAssessment =
                MpvBackCachePolicy.resolve(backRequest);
        MpvBackCacheController.Decision backDecision = !automaticBack
                || backTrigger == null
                ? null : mpvBackCacheController.evaluate(
                playbackAutoSession, context.session(), backAssessment, resourceDecision,
                backTrigger, seekObservation, now);
        MpvBackCacheController.Snapshot backEvaluated = mpvBackCacheController.snapshot();
        long currentBack = !automaticBack
                ? mpv.getConfiguredBackCacheBytes()
                : backEvaluated.controlledTargetBytes() >= 0
                ? backEvaluated.controlledTargetBytes() : 0;
        long targetBack = backDecision != null && backDecision.requestsApply()
                ? backDecision.targetBytes() : currentBack;
        if (automaticForward && !automaticBack && targetBack > targetForward) {
            targetForward = targetBack;
        } else if (!automaticForward && automaticBack
                && targetBack > targetForward) {
            targetBack = targetForward;
        }

        boolean cacheExpansionAllowed = experimentAllowed(
                PlaybackExperimentPolicy.Action.MPV_CACHE_EXPANSION);
        boolean forwardPolicyRequested = forwardDecision != null
                && forwardDecision.requestsApply();
        boolean backPolicyRequested = backDecision != null
                && backDecision.requestsApply();
        boolean forwardRequested = forwardPolicyRequested
                && (targetForward <= currentForward || cacheExpansionAllowed);
        boolean backRequested = backPolicyRequested
                && (targetBack <= currentBack || cacheExpansionAllowed);
        boolean expansionSuppressed = forwardPolicyRequested && !forwardRequested
                || backPolicyRequested && !backRequested;
        if (forwardPolicyRequested && !forwardRequested) {
            targetForward = currentForward;
        }
        if (backPolicyRequested && !backRequested) {
            targetBack = currentBack;
        }
        boolean applyRequested = forwardRequested || backRequested;
        boolean forwardStarted = !forwardRequested;
        boolean backStarted = !backRequested;
        boolean coordinatorStarted = false;
        boolean adopted = false;
        boolean accepted = false;
        boolean staged = false;
        String applyResult = expansionSuppressed
                ? "experiment-disabled" : "not-requested";
        MpvCacheTargetCoordinator.Decision combinedDecision = null;
        if (applyRequested) {
            combinedDecision = mpvCacheTargetCoordinator.evaluate(
                    playbackAutoSession, targetForward, targetBack);
            if (forwardRequested) {
                forwardStarted = mpvForwardCacheController.beginApply(
                        playbackAutoSession, forwardDecision);
            }
            if (backRequested) {
                backStarted = mpvBackCacheController.beginApply(
                        playbackAutoSession, backDecision);
            }
            boolean controllersStarted = forwardStarted && backStarted;
            if (controllersStarted && combinedDecision.requestsApply()) {
                coordinatorStarted = mpvCacheTargetCoordinator.beginApply(
                        playbackAutoSession, combinedDecision);
                MpvPlayer.AutoCacheBaselineResult result = coordinatorStarted
                        ? mpv.applyAutoCacheBaseline(
                        playbackTrace.current(), targetForward, targetBack)
                        : MpvPlayer.AutoCacheBaselineResult.REJECTED;
                accepted = result.accepted();
                staged = result.staged();
                applyResult = result.label();
                if (coordinatorStarted) {
                    mpvCacheTargetCoordinator.completeApply(
                            playbackAutoSession, combinedDecision, accepted, staged);
                }
            } else if (controllersStarted
                    && combinedDecision.reason()
                    == MpvCacheTargetCoordinator.Reason.TARGET_STABLE) {
                adopted = true;
                accepted = true;
                applyResult = "already-applied";
            } else if (!controllersStarted) {
                applyResult = "controller-rejected";
            } else {
                applyResult = combinedDecision.reason().label();
            }
            boolean controllerAccepted = accepted && (coordinatorStarted || adopted);
            if (forwardRequested && forwardStarted) {
                mpvForwardCacheController.completeApply(
                        playbackAutoSession, forwardDecision,
                        controllerAccepted, staged, now);
            }
            if (backRequested && backStarted) {
                mpvBackCacheController.completeApply(
                        playbackAutoSession, backDecision,
                        controllerAccepted, staged, now);
            }
            if (controllerAccepted) {
                mpvForwardCacheController.syncNativeTarget(
                        playbackAutoSession, targetForward);
                mpvBackCacheController.syncNativeTarget(
                        playbackAutoSession, targetBack);
            }
        }

        MpvForwardCacheController.Snapshot forwardAfter = mpvForwardCacheController.snapshot();
        MpvBackCacheController.Snapshot backAfter = mpvBackCacheController.snapshot();
        PlayerCacheState cache = mpv.getAutoCacheSnapshot();
        boolean commitStarted = coordinatorStarted || adopted;
        String forwardApplyResult = forwardPolicyRequested && !forwardRequested
                ? "experiment-disabled" : applyResult;
        String backApplyResult = backPolicyRequested && !backRequested
                ? "experiment-disabled" : applyResult;
        if (forwardDecision != null) {
            publishMpvForwardCacheDecision(
                    forwardTrigger,
                    forwardAssessment,
                    forwardDecision,
                    forwardAfter,
                    cache,
                    context,
                    now,
                    forwardRequested && forwardStarted && commitStarted,
                    accepted,
                    staged,
                    forwardApplyResult);
        }
        if (backDecision != null) {
            publishMpvBackCacheDecision(
                    backTrigger,
                    backAssessment,
                    backDecision,
                    backAfter,
                    cache,
                    context,
                    now,
                    backRequested && backStarted && commitStarted,
                    accepted,
                    staged,
                    backApplyResult);
        }
        if (applyRequested || expansionSuppressed) {
            PlaybackTrace.log("mpv-cache-target", playbackTrace.current(),
                    "forward=%d back=%d totalBudget=%d forwardChanged=%s backChanged=%s coordinator=%s result=%s",
                    targetForward, targetBack, backAssessment.totalBudgetBytes(),
                    forwardRequested, backRequested,
                    combinedDecision == null ? "none" : combinedDecision.reason().label(),
                    applyResult);
        }
    }

    private void evaluateMpvPreload(
            MpvPlayerEngine mpv,
            PlaybackAutoContext context,
            MpvResourcePressureController.Decision resourceDecision,
            boolean automatic,
            boolean performancePriority,
            long now) {
        boolean experimentalAutomatic = automatic && experimentAllowed(
                PlaybackExperimentPolicy.Action.MPV_AUTO_PRELOAD);
        MpvPlayer.AutoHlsRuntimeSnapshot hls = mpv.getAutoHlsRuntimeSnapshot();
        MpvPlayer.AutoHlsPreloadRuntimeSnapshot proxy =
                mpv.getAutoHlsPreloadRuntimeSnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                context.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> streamFact =
                context.resource().streamKind();
        PlaybackAutoContext.Fact<PlaybackAutoContext.PathKind> playerPathFact =
                context.path().playerPath();
        PlaybackAutoContext.Fact<PlaybackAutoContext.PathKind> upstreamPathFact =
                context.path().upstreamPath();
        PlaybackAutoContext.Fact<PlaybackAutoContext.UpstreamState> upstreamStateFact =
                context.path().upstreamState();
        PlaybackAutoContext.Fact<Long> bufferFact =
                context.runtime().bufferedDurationMs();
        PlaybackAutoContext.Fact<Integer> rebufferFact =
                context.runtime().rebufferCount();
        PlaybackAutoContext.Fact<Long> positionFact =
                context.runtime().positionMs();
        boolean protocolUsable = protocolFact.isUsable(now);
        boolean streamUsable = streamFact.isUsable(now);
        PlaybackAutoContext.StreamKind streamKind = streamUsable
                ? streamFact.value() : PlaybackAutoContext.StreamKind.UNKNOWN;
        if (streamKind == PlaybackAutoContext.StreamKind.VOD && !proxy.vod()) {
            streamUsable = false;
            streamKind = PlaybackAutoContext.StreamKind.UNKNOWN;
        }
        boolean bufferUsable = bufferFact.isUsable(now);
        long selectedBits = hls.selectedVariant() == null
                ? 0 : hls.selectedVariant().selectionBitsPerSecond();
        if (selectedBits <= 0 && hls.variants().size() == 1) {
            selectedBits = hls.variants().get(0).selectionBitsPerSecond();
        }
        if (selectedBits <= 0) {
            PlaybackAutoContext.Fact<Long> mediaBitrateFact =
                    context.runtime().mediaBitrateBitsPerSecond();
            if (mediaBitrateFact.isUsable(now) && mediaBitrateFact.value() > 0) {
                selectedBits = mediaBitrateFact.value();
            }
        }
        boolean buffering = player != null
                && player.getPlaybackState() == Player.STATE_BUFFERING;
        MpvPreloadPolicy.Request request = new MpvPreloadPolicy.Request(
                experimentalAutomatic,
                isMpv(),
                performancePriority,
                proxy.preloadConfigured(),
                protocolUsable ? protocolFact.value()
                        : PlaybackAutoContext.Protocol.UNKNOWN,
                protocolUsable,
                streamKind,
                streamUsable,
                playerPathFact.isUsable(now) ? playerPathFact.value()
                        : PlaybackAutoContext.PathKind.UNKNOWN,
                playerPathFact.isUsable(now),
                upstreamPathFact.isUsable(now) ? upstreamPathFact.value()
                        : PlaybackAutoContext.PathKind.UNKNOWN,
                upstreamPathFact.isUsable(now),
                upstreamStateFact.isUsable(now) ? upstreamStateFact.value()
                        : PlaybackAutoContext.UpstreamState.UNKNOWN,
                upstreamStateFact.isUsable(now),
                resourceDecision.preloadAllowed(),
                proxy.cacheEnabled(),
                proxy.cacheStorageKnown(),
                proxy.cacheBudgetAvailable(),
                proxy.cacheCircuitOpen(),
                proxy.upstreamBitsPerSecond(),
                proxy.throughputKnown(),
                proxy.throughputFresh(),
                proxy.throughputSampleAtElapsedMs(),
                selectedBits,
                bufferUsable,
                bufferUsable ? bufferFact.value() : 0,
                bufferUsable ? bufferFact.sampledAtElapsedMs() : -1,
                rebufferFact.hasValue() ? rebufferFact.value() : 0,
                buffering,
                false,
                false,
                proxy.foregroundRequests(),
                context.revision());
        MpvPreloadController.Snapshot before = mpvPreloadController.snapshot();
        MpvPreloadController.Decision decision = mpvPreloadController.evaluate(
                playbackAutoSession, context.session(), request, now);
        boolean automaticPreloadManaged = MpvPreloadPolicy.ownsProxyControl(
                automatic, performancePriority);
        boolean gateChanged = mpv.updateAutomaticPreloadControl(
                automaticPreloadManaged,
                experimentalAutomatic && resourceDecision.preloadAllowed(),
                experimentalAutomatic && decision.preloadAllowed());
        boolean scheduled = false;
        if (experimentalAutomatic && automaticPreloadManaged
                && decision.preloadAllowed()
                && positionFact.isUsable(now)) {
            mpv.requestAutomaticHlsPreload(Math.max(0, positionFact.value()));
            scheduled = true;
        }
        publishMpvPreloadDecision(
                before, decision, request, proxy, gateChanged, scheduled, now);
    }

    private void publishMpvPreloadDecision(
            MpvPreloadController.Snapshot before,
            MpvPreloadController.Decision decision,
            MpvPreloadPolicy.Request request,
            MpvPlayer.AutoHlsPreloadRuntimeSnapshot proxy,
            boolean gateChanged,
            boolean scheduled,
            long now) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (decision.policyReason() == MpvPreloadPolicy.Reason.CONFIG_PRIORITY
                || decision.policyReason() == MpvPreloadPolicy.Reason.NOT_AUTOMATIC) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (gateChanged || decision.cancellationRequested()
                || decision.action() == MpvPreloadController.Action.ALLOW) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else if (decision.changed()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.HELD;
        }
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "upstream_bps",
                request.throughputKnown() ? request.upstreamBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.PROXY,
                request.throughputKnown()
                        ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "throughput_age_ms",
                request.throughputKnown() ? proxy.throughputAgeMs() : -1,
                PlaybackAutoContext.ValueSource.PROXY,
                request.throughputKnown()
                        ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "selected_bps",
                request.selectedBitsPerSecond() > 0
                        ? request.selectedBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                request.selectedBitsPerSecond() > 0
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "ratio_permille", decision.ratioPermille(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "buffer_ms",
                request.bufferUsable() ? request.bufferedDurationMs() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                request.bufferUsable()
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "foreground", request.foregroundRequests(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_physical", proxy.cachePhysicalBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_reserved", proxy.cacheReservedBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_write_budget",
                proxy.cacheNewWriteBudgetBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cache_effective",
                proxy.cacheEffectiveCapacityBytes(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "recovery_samples", decision.recoverySamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "recovery_ms", decision.recoveryRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "throughput_filter",
                proxy.lastThroughputRejectReason(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "resource_allowed", request.resourcePreloadAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "cache_budget", request.cacheBudgetAvailable(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "cache_circuit", request.cacheCircuitOpen(),
                PlaybackAutoContext.ValueSource.PROXY,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_PRELOAD,
                        outcome,
                        before.lastDecision().targetLabel(),
                        decision.targetLabel(),
                        scheduled ? "scheduled" : gateChanged
                                ? "gate-updated" : "held",
                        decision.policyReason().label(),
                        decision.reason().label(),
                        inputs),
                now);
        PlaybackTrace.log("mpv-preload", playbackTrace.current(),
                "state=%s action=%s reason=%s policy=%s threads=%d ratio=%d upstream=%d selected=%d buffer=%d foreground=%d cachePhysical=%d cacheReserved=%d cacheBudget=%d cacheEffective=%d circuit=%s recoverySamples=%d recoveryMs=%d gateChanged=%s scheduled=%s",
                decision.state().label(), decision.action().label(),
                decision.reason().label(), decision.policyReason().label(),
                decision.concurrency(), decision.ratioPermille(),
                request.upstreamBitsPerSecond(), request.selectedBitsPerSecond(),
                request.bufferedDurationMs(), request.foregroundRequests(),
                proxy.cachePhysicalBytes(), proxy.cacheReservedBytes(),
                proxy.cacheNewWriteBudgetBytes(),
                proxy.cacheEffectiveCapacityBytes(), proxy.cacheCircuitOpen(),
                decision.recoverySamples(), decision.recoveryRemainingMs(),
                gateChanged, scheduled);
    }

    private static MpvResourcePressureController.Trigger mpvResourceTrigger(
            MpvForwardCacheController.Trigger forwardTrigger,
            MpvBackCacheController.Trigger backTrigger) {
        if (forwardTrigger == MpvForwardCacheController.Trigger.RESOURCE
                || backTrigger == MpvBackCacheController.Trigger.RESOURCE) {
            return MpvResourcePressureController.Trigger.SYSTEM;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.MEMORY
                || backTrigger == MpvBackCacheController.Trigger.MEMORY) {
            return MpvResourcePressureController.Trigger.MEMORY;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.REBUILD
                || backTrigger == MpvBackCacheController.Trigger.REBUILD) {
            return MpvResourcePressureController.Trigger.REBUILD;
        }
        if (forwardTrigger == MpvForwardCacheController.Trigger.BASELINE
                || backTrigger == MpvBackCacheController.Trigger.BASELINE) {
            return MpvResourcePressureController.Trigger.BASELINE;
        }
        return MpvResourcePressureController.Trigger.RUNTIME;
    }

    private void publishMpvResourcePressureDecision(
            MpvResourcePressureController.Snapshot before,
            MpvResourcePressurePolicy.Assessment assessment,
            MpvResourcePressureController.Decision decision,
            boolean preloadGateChanged,
            long now) {
        MpvResourcePressureController.Snapshot previous = before == null
                ? mpvResourcePressureController.snapshot() : before;
        PlaybackTelemetry.DecisionOutcome outcome = !assessment.active()
                && assessment.reason() == MpvResourcePressurePolicy.Reason.CONFIG_PRIORITY
                ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                : decision.changed() || preloadGateChanged
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : decision.action() == MpvResourcePressureController.Action.STABLE
                ? PlaybackTelemetry.DecisionOutcome.OBSERVED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        String oldValue = mpvResourceTargetLabel(
                previous.forwardCeilingBytes(),
                previous.backCeilingBytes(),
                previous.preloadAllowed());
        String targetValue = decision.targetLabel();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "level",
                assessment.level().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(assessment.memoryUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "memory_pressure",
                assessment.memoryPressure().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        inputs.add(assessment.thermalUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "thermal",
                assessment.thermal().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("thermal"));
        inputs.add(assessment.powerUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "power",
                assessment.power().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("power"));
        inputs.add(assessment.networkCostUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "network_cost",
                assessment.networkCost().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("network_cost"));
        inputs.add(assessment.networkSnapshotUsable()
                ? PlaybackTelemetry.DecisionInput.text(
                "data_saver",
                assessment.networkSnapshot().dataSaverState().label(),
                PlaybackAutoContext.ValueSource.SYSTEM_API,
                PlaybackAutoContext.Confidence.HIGH)
                : PlaybackTelemetry.DecisionInput.unknown("data_saver"));
        addNumberInput(inputs, "forward_ceiling", decision.forwardCeilingBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "back_ceiling", decision.backCeilingBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "expansion_allowed",
                decision.expansionAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "preload_allowed",
                decision.preloadAllowed(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "preload_gate_changed",
                preloadGateChanged,
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        addNumberInput(inputs, "normal_samples", decision.normalSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_RESOURCE_PRESSURE,
                        outcome,
                        oldValue,
                        targetValue,
                        targetValue,
                        decision.policyReason().label(),
                        decision.reason().label(),
                        inputs),
                now);
        PlaybackTrace.log("mpv-resource-pressure", playbackTrace.current(),
                "state=%s trigger=%s input=%s action=%s reason=%s policy=%s forwardCeiling=%d backCeiling=%d expansion=%s preload=%s gateChanged=%s normalSamples=%d cooldown=%d",
                mpvResourcePressureController.snapshot().state().label(),
                decision.trigger().label(),
                decision.inputLevel().label(),
                decision.action().label(),
                decision.reason().label(),
                decision.policyReason().label(),
                decision.forwardCeilingBytes(),
                decision.backCeilingBytes(),
                decision.expansionAllowed(),
                decision.preloadAllowed(),
                preloadGateChanged,
                decision.normalSamples(),
                decision.cooldownRemainingMs());
    }

    private static String mpvResourceTargetLabel(
            long forwardBytes,
            long backBytes,
            boolean preloadAllowed) {
        return "forward-" + Math.max(0, forwardBytes)
                + "-back-" + Math.max(0, backBytes)
                + "-preload-" + preloadAllowed;
    }

    private void publishMpvForwardCacheDecision(
            MpvForwardCacheController.Trigger trigger,
            MpvForwardCachePolicy.Assessment assessment,
            MpvForwardCacheController.Decision decision,
            MpvForwardCacheController.Snapshot after,
            PlayerCacheState cache,
            PlaybackAutoContext context,
            long now,
            boolean started,
            boolean accepted,
            boolean staged,
            String applyResult) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (!assessment.active()
                && assessment.inactiveReason() == MpvForwardCachePolicy.Reason.CONFIG_PRIORITY) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if ("experiment-disabled".equals(applyResult)) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (!decision.requestsApply()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else if (!started || !accepted) {
            outcome = PlaybackTelemetry.DecisionOutcome.FAILED;
        } else if (staged) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.APPLIED;
        }
        String oldValue = forwardTargetLabel(decision.oldNativeTargetBytes());
        String targetValue = forwardTargetLabel(decision.targetBytes());
        String resultValue = forwardTargetLabel(after.nativeTargetBytes());
        String suppression = "experiment-disabled".equals(applyResult)
                ? "experiment-disabled"
                : !decision.requestsApply()
                ? decision.reason().label()
                : !started
                ? "action-rejected"
                : !accepted
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addBitrateInput(inputs, "average_bps", assessment.averageBitrate());
        addBitrateInput(inputs, "peak_bps", assessment.peakBitrate());
        addNumberInput(inputs, "media_target", assessment.mediaReliable()
                ? assessment.mediaTargetBytes() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "safe_capacity", assessment.safeTargetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "initial_baseline", after.initialBaselineBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "actual_fw", cache.forwardBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "actual_total", cache.totalBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> memoryFact =
                context.device().memorySnapshot();
        addNumberInput(inputs, "native_heap",
                memoryFact.isUsable(now) && memoryFact.value().nativeHeapAllocatedBytes() != null
                        ? memoryFact.value().nativeHeapAllocatedBytes() : -1,
                memoryFact.isUsable(now) ? memoryFact.source() : PlaybackAutoContext.ValueSource.UNKNOWN,
                memoryFact.isUsable(now) ? memoryFact.confidence() : PlaybackAutoContext.Confidence.UNKNOWN);
        PlaybackAutoContext.Fact<Long> pssFact = context.device().diagnosticPssBytes();
        addNumberInput(inputs, "diagnostic_pss",
                pssFact.isUsable(now) ? pssFact.value() : -1,
                pssFact.isUsable(now) ? pssFact.source() : PlaybackAutoContext.ValueSource.UNKNOWN,
                pssFact.isUsable(now) ? pssFact.confidence() : PlaybackAutoContext.Confidence.UNKNOWN);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                context.device().memoryPressure();
        if (pressureFact.isUsable(now)) {
            inputs.add(PlaybackTelemetry.DecisionInput.text(
                    "memory_pressure", pressureFact.value().label(),
                    pressureFact.source(), pressureFact.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        }
        addNumberInput(inputs, "stable_samples", after.demandSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_FORWARD_CACHE,
                        outcome,
                        oldValue,
                        targetValue,
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-forward-cache", playbackTrace.current(),
                "state=%s trigger=%s action=%s reason=%s old=%s target=%s result=%s fw=%d total=%d stable=%d cooldown=%d apply=%s",
                after.state().label(), trigger == null ? "runtime" : trigger.label(),
                decision.action().label(), decision.reason().label(), oldValue,
                targetValue, resultValue, cache.forwardBytes(), cache.totalBytes(),
                after.demandSamples(), decision.cooldownRemainingMs(), applyResult);
    }

    private void publishMpvBackCacheDecision(
            MpvBackCacheController.Trigger trigger,
            MpvBackCachePolicy.Assessment assessment,
            MpvBackCacheController.Decision decision,
            MpvBackCacheController.Snapshot after,
            PlayerCacheState cache,
            PlaybackAutoContext context,
            long now,
            boolean started,
            boolean accepted,
            boolean staged,
            String applyResult) {
        PlaybackTelemetry.DecisionOutcome outcome;
        if (!assessment.active()
                && assessment.reason() == MpvBackCachePolicy.Reason.CONFIG_PRIORITY) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if ("experiment-disabled".equals(applyResult)) {
            outcome = PlaybackTelemetry.DecisionOutcome.SUPPRESSED;
        } else if (!decision.requestsApply()) {
            outcome = PlaybackTelemetry.DecisionOutcome.OBSERVED;
        } else if (!started || !accepted) {
            outcome = PlaybackTelemetry.DecisionOutcome.FAILED;
        } else if (staged) {
            outcome = PlaybackTelemetry.DecisionOutcome.REQUESTED;
        } else {
            outcome = PlaybackTelemetry.DecisionOutcome.APPLIED;
        }
        String oldValue = backTargetLabel(decision.oldNativeTargetBytes());
        String targetValue = backTargetLabel(decision.targetBytes());
        String resultValue = backTargetLabel(after.nativeTargetBytes());
        String suppression = "experiment-disabled".equals(applyResult)
                ? "experiment-disabled"
                : !decision.requestsApply()
                ? decision.reason().label()
                : !started
                ? "action-rejected"
                : !accepted
                ? "native-apply-failed"
                : "none";
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "seek_distance_ms",
                decision.lastBackwardSeekDistanceMs() > 0
                        ? decision.lastBackwardSeekDistanceMs() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                decision.lastBackwardSeekDistanceMs() > 0
                        ? PlaybackAutoContext.Confidence.HIGH
                        : PlaybackAutoContext.Confidence.UNKNOWN);
        addNumberInput(inputs, "seek_evidence", decision.backwardSeekEvidence(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "learned_target", decision.learnedTargetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "safe_back", assessment.safeBackBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "total_budget", assessment.totalBudgetBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "forward_target", assessment.forwardBytes(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "actual_fw", cache.forwardBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "actual_total", cache.totalBytes(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> pressureFact =
                context.device().memoryPressure();
        if (pressureFact.isUsable(now)) {
            inputs.add(PlaybackTelemetry.DecisionInput.text(
                    "memory_pressure", pressureFact.value().label(),
                    pressureFact.source(), pressureFact.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown("memory_pressure"));
        }
        addNumberInput(inputs, "normal_samples", after.normalSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "apply_attempts", after.applyAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_BACK_CACHE,
                        outcome,
                        oldValue,
                        targetValue,
                        resultValue,
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
        PlaybackTrace.log("mpv-back-cache", playbackTrace.current(),
                "state=%s trigger=%s action=%s reason=%s old=%s target=%s result=%s seekDistance=%d seekEvidence=%d learned=%d safe=%d forward=%d totalBudget=%d fw=%d total=%d cooldown=%d apply=%s",
                after.state().label(), trigger == null ? "runtime" : trigger.label(),
                decision.action().label(), decision.reason().label(), oldValue,
                targetValue, resultValue, decision.lastBackwardSeekDistanceMs(),
                decision.backwardSeekEvidence(), decision.learnedTargetBytes(),
                assessment.safeBackBytes(), assessment.forwardBytes(),
                assessment.totalBudgetBytes(), cache.forwardBytes(), cache.totalBytes(),
                decision.cooldownRemainingMs(), applyResult);
    }

    private boolean isCurrentMpvMediaSeekable() {
        if (player == null
                || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return false;
        }
        try {
            Timeline timeline = player.getCurrentTimeline();
            int index = player.getCurrentMediaItemIndex();
            if (timeline.isEmpty() || index < 0 || index >= timeline.getWindowCount()) return false;
            Timeline.Window window = new Timeline.Window();
            timeline.getWindow(index, window);
            return window.isSeekable;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isCurrentMpvMediaLive() {
        if (player == null) return false;
        try {
            return player.isCurrentMediaItemLive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String forwardTargetLabel(long bytes) {
        return bytes < 0 ? "unknown" : "forward-" + bytes;
    }

    private static String backTargetLabel(long bytes) {
        return bytes < 0 ? "unknown" : "back-" + bytes;
    }

    private static void addBitrateInput(
            List<PlaybackTelemetry.DecisionInput> inputs,
            String name,
            MpvForwardCachePolicy.BitrateEvidence evidence) {
        if (evidence != null && evidence.reliable()) {
            inputs.add(PlaybackTelemetry.DecisionInput.number(
                    name, evidence.bitsPerSecond(), evidence.valueSource(), evidence.confidence()));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown(name));
        }
    }

    private static void addNumberInput(
            List<PlaybackTelemetry.DecisionInput> inputs,
            String name,
            long value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence) {
        if (value < 0 || source == PlaybackAutoContext.ValueSource.UNKNOWN
                || confidence == PlaybackAutoContext.Confidence.UNKNOWN) {
            inputs.add(PlaybackTelemetry.DecisionInput.unknown(name));
        } else {
            inputs.add(PlaybackTelemetry.DecisionInput.number(name, value, source, confidence));
        }
    }

    private static List<MpvHlsVariantPolicy.Variant> toPolicyVariants(
            List<MpvPlayer.HlsVariant> variants) {
        if (variants == null || variants.isEmpty()) return List.of();
        List<MpvHlsVariantPolicy.Variant> result =
                new ArrayList<>(variants.size());
        for (MpvPlayer.HlsVariant variant : variants) {
            MpvHlsVariantPolicy.Variant mapped = toPolicyVariant(variant);
            if (mapped != null) result.add(mapped);
        }
        return List.copyOf(result);
    }

    @Nullable
    private static MpvHlsVariantPolicy.Variant toPolicyVariant(
            @Nullable MpvPlayer.HlsVariant variant) {
        if (variant == null) return null;
        return new MpvHlsVariantPolicy.Variant(
                variant.bandwidthBitsPerSecond(),
                variant.averageBandwidthBitsPerSecond(),
                variant.width(),
                variant.height());
    }

    private static String hlsOptionLabel(String option) {
        if (TextUtils.isEmpty(option)) return "unset";
        String value = option.trim();
        if ("min".equals(value) || "max".equals(value)
                || "no".equals(value)) return value;
        long bits = optionBits(value);
        return bits > 0 ? "bps-" + bits : "invalid";
    }

    private static long optionBits(String option) {
        if (TextUtils.isEmpty(option)) return 0;
        try {
            return Math.max(0, Long.parseLong(option.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record MpvHlsApplyResult(
            boolean started,
            boolean optionAccepted,
            boolean reloadStarted,
            boolean succeeded,
            String optionResult) {

        private MpvHlsApplyResult {
            optionResult = optionResult == null ? "unknown" : optionResult;
        }

        private static MpvHlsApplyResult notRequested() {
            return new MpvHlsApplyResult(
                    false, false, false, false, "not-requested");
        }

        private static MpvHlsApplyResult rejected() {
            return new MpvHlsApplyResult(
                    false, false, false, false, "rejected");
        }
    }

    public void applyPerformanceSettings() {
        if (isExo()) {
            resetNetworkProtectionSession("performance-settings-changed");
            scheduleNetworkProtection(0);
            if (engine instanceof ExoPlayerEngine exo
                    && exo.requiresDv7Hdr10FallbackRebuild()) {
                rebuildAndRestartExo("dv7-fallback-setting-changed");
            }
            return;
        }
        if (!isMpv() || spec == null || TextUtils.isEmpty(spec.getUrl()) || !(engine instanceof MpvPlayerEngine mpv)) return;
        resetMpvOutputEvaluationState();
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        mpv.setSurfaceDirectOverride(null);
        mpv.setVulkanRenderOverride(null);
        mpv.resetDv7HandlingForNewItem();
        mpv.resetDv8HandlingForNewItem();
        rebuildAndRestartMpv(null, "performance-settings-changed");
    }

    private boolean rebuildAndRestartExo(String reason) {
        if (!isExo() || spec == null || TextUtils.isEmpty(spec.getUrl())) {
            return false;
        }
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        App.removeCallbacks(runnable);
        videoSize = null;
        initTrack = false;
        rebuildPlayer();
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE,
                "player=" + playerType + " decode=" + engine.getDecode()
                        + " exo=" + reason);
        engine.start(spec.checkUa(), position, wasPlayWhenReady);
        startNativeAudioSession(wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private boolean rebuildAndRestartMpv(Boolean surfaceDirectOverride, String reason) {
        if (!isMpv() || spec == null || TextUtils.isEmpty(spec.getUrl()) || !(engine instanceof MpvPlayerEngine mpv)) return false;
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        prepareSeq++;
        resetMpvOutputEvaluationState();
        App.removeCallbacks(runnable);
        Boolean effectiveSurfaceDirectOverride = surfaceDirectOverride;
        if (effectiveSurfaceDirectOverride == null
                && mpvAutoGpuPinnedForSession
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO) {
            effectiveSurfaceDirectOverride = false;
        }
        mpv.setSurfaceDirectOverride(effectiveSurfaceDirectOverride);
        videoSize = null;
        initTrack = false;
        rebuildPlayer();
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        applyMpvAutoInitialControl();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE, "player=" + playerType + " decode=" + engine.getDecode() + " mpv-output=" + reason);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "rebuild reason=%s directOverride=%s position=%d play=%s speed=%s repeat=%s spec=%s", reason, effectiveSurfaceDirectOverride, position, wasPlayWhenReady, speed, repeat, debugSpec());
        engine.start(spec.checkUa(), position, wasPlayWhenReady);
        scheduleMpvAutoOutputEvaluation();
        startNativeAudioSession(wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        return true;
    }

    private void prepareMpvOutputForNewItem() {
        resetMpvOutputEvaluationState();
        List<Track> persistedTracks = Track.find(getKey());
        Track persistedSubtitle = findRequestedSubtitle(persistedTracks);
        mpvExplicitSubtitlePreference = persistedSubtitle != null;
        if (!(engine instanceof MpvPlayerEngine mpv)) return;
        mpv.prepareSubtitleForNewItem(persistedSubtitle);
        boolean dv7HandlingChanged = mpv.resetDv7HandlingForNewItem();
        boolean dv8HandlingChanged = mpv.resetDv8HandlingForNewItem();
        boolean clearAutoVulkanRenderer = mpvAutoVulkanPinnedForItem;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        mpv.setVulkanRenderOverride(null);
        if (MpvPerformanceSetting.getOutputMode()
                == MpvPerformanceSetting.OUTPUT_AUTO) {
            callback.onPlayerOutputPending();
        }
        mpv.setSurfaceDirectOverride(null);
        boolean autoDirectEligible = MpvAutoOutputPolicy.canStartSurfaceDirect(
                engine.isHard(),
                Util.isLeanback(),
                videoEffectsActive || videoEffectsDirty || MpvPerformanceSetting.isInterpolation()
                        || lutAllowed && LutSetting.isEnabled(),
                MpvConfigStore.hasGpuVideoProcessing());
        if (mpvAutoGpuPinnedForSession
                && MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO) {
            autoDirectEligible = false;
        }
        boolean shouldStartDirect = MpvPerformanceSetting.shouldUseSurfaceDirect(
                autoDirectEligible, Util.isLeanback(), engine.isHard());
        if (mpv.isSurfaceDirect() == shouldStartDirect
                && !clearAutoVulkanRenderer && !dv7HandlingChanged && !dv8HandlingChanged) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "prepare new item rebuild currentDirect=%s desiredDirect=%s clearAutoVulkan=%s mode=%s", mpv.isSurfaceDirect(), shouldStartDirect, clearAutoVulkanRenderer, MpvPerformanceSetting.getOutputModeText());
        mpv.setSurfaceDirectOverride(shouldStartDirect);
        rebuildPlayer();
    }

    private void resetMpvOutputRuntime() {
        resetMpvOutputEvaluationState();
        mpvAutoGpuPinnedForSession = false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = false;
        lastMpvFrameTimingLogMs = 0;
        if (engine instanceof MpvPlayerEngine mpv) {
            mpv.setSurfaceDirectOverride(null);
            mpv.setVulkanRenderOverride(null);
        }
    }

    private void resetMpvOutputEvaluationState() {
        mpvAutoOutputEvaluated = false;
        mpvAutoOutputFrameReady = false;
        mpvAutoOutputEvaluationScheduled = false;
        mpvAutoOutputProbeAttempts = 0;
        mpvSurfaceFallbackTried = false;
        mpvVulkanFallbackTried = false;
        mpvOutputEvaluationSeq++;
    }

    private void scheduleMpvAutoOutputEvaluation() {
        if (!isMpv()
                || MpvPerformanceSetting.getOutputMode()
                != MpvPerformanceSetting.OUTPUT_AUTO) return;
        if (mpvAutoOutputEvaluated || mpvAutoOutputEvaluationScheduled) return;
        mpvAutoOutputEvaluationScheduled = true;
        int seq = ++mpvOutputEvaluationSeq;
        App.post(() -> {
            if (seq != mpvOutputEvaluationSeq) return;
            mpvAutoOutputEvaluationScheduled = false;
            if (mpvHlsManagedReload) {
                scheduleMpvAutoOutputEvaluation();
                return;
            }
            mpvAutoOutputProbeAttempts++;
            boolean evaluated = evaluateMpvAutoOutput();
            if (!evaluated && !mpvAutoOutputEvaluated && mpvAutoOutputProbeAttempts < MPV_AUTO_OUTPUT_PROBE_MAX_ATTEMPTS) {
                scheduleMpvAutoOutputEvaluation();
            } else if (!evaluated && SpiderDebug.isEnabled()) {
                SpiderDebug.log("mpv-output", "auto probe exhausted attempts=%d size=%dx%d tracksEmpty=%s", mpvAutoOutputProbeAttempts, getVideoWidth(), getVideoHeight(), engine == null || engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty());
            }
        }, MPV_AUTO_OUTPUT_PROBE_INTERVAL_MS);
    }

    private boolean evaluateMpvAutoOutput() {
        if (!isMpv() || mpvAutoOutputEvaluated
                || !(engine instanceof MpvPlayerEngine mpv)) return true;
        if (mpvHlsManagedReload) return false;
        Tracks tracks = engine.getCurrentTracks();
        boolean tracksReady = tracks != null && !tracks.isEmpty();
        if (!tracksReady) return false;
        Format format = tracksReady ? engine.getVideoFormat() : null;
        PlayerEngine.VideoPlaybackDetails videoDetails = engine.getVideoPlaybackDetails();
        boolean dolbyVision = videoDetails != null
                && videoDetails.hasDolbyVisionSource();
        if (!dolbyVision && (format == null || !tracks.containsType(C.TRACK_TYPE_VIDEO))) {
            return false;
        }
        VideoSize probedSize = mpv.getVideoSizeSnapshot();
        int width = format != null && format.width > 0 ? format.width : probedSize.width > 0 ? probedSize.width : getVideoWidth();
        int height = format != null && format.height > 0 ? format.height : probedSize.height > 0 ? probedSize.height : getVideoHeight();
        if (width <= 0 || height <= 0) return false;
        boolean externalSubtitleActive = spec != null && spec.getSubs() != null && !spec.getSubs().isEmpty();
        boolean earlyEvaluation = false;
        boolean subtitleActive = externalSubtitleActive || mpvExplicitSubtitlePreference;
        boolean lutOrFilterActive = videoEffectsActive || videoEffectsDirty || lutAllowed && LutSetting.isEnabled() || MpvPerformanceSetting.isInterpolation();
        boolean customGpuProcessing = MpvConfigStore.hasGpuVideoProcessing();
        boolean dv7Hdr10FallbackEnabled = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.isDv7Hdr10Active();
        MpvAutoOutputPolicy.DolbyVisionSupport dolbyVisionSupport = dolbyVision
                ? CodecCapabilityInspector.dolbyVisionSupport(
                App.get(), videoDetails, format, width, height)
                : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        MpvAutoOutputPolicy.DolbyVisionSupport profile81Support =
                dolbyVision && videoDetails.dolbyVisionProfile() == 7
                        && PlaybackPerformanceSetting.getMpvDv7HandlingMode()
                        == PlaybackPerformanceSetting.DV7_HANDLING_P81
                        ? CodecCapabilityInspector.dolbyVisionProfileSupport(
                        App.get(), 8, videoDetails.dolbyVisionLevel(),
                        videoDetails.sourceCodecs(), format, width, height)
                        : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        boolean dv7HandlingChanged = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.updateDv7Handling(dolbyVisionSupport, profile81Support);
        MpvAutoOutputPolicy.DolbyVisionSupport hevcHdr10Support =
                dolbyVision && videoDetails.dolbyVisionProfile() == 8
                        ? CodecCapabilityInspector.hevcHdr10Support(
                        App.get(), format, width, height)
                        : MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN;
        boolean dv8HandlingChanged = dolbyVision
                && videoDetails.dolbyVisionProfile() == 8
                && mpv.updateDv8Handling(dolbyVisionSupport, hevcHdr10Support);
        dv7Hdr10FallbackEnabled = dolbyVision
                && videoDetails.dolbyVisionProfile() == 7
                && mpv.isDv7Hdr10Active();
        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                width, height, engine.isHard(),
                Util.isLeanback(), lutOrFilterActive, customGpuProcessing,
                dolbyVisionSupport,
                dolbyVision ? videoDetails.dolbyVisionProfile() : C.INDEX_UNSET,
                dv7Hdr10FallbackEnabled,
                hevcHdr10Support);
        int dolbyVisionProfile = dolbyVision
                ? videoDetails.dolbyVisionProfile() : C.INDEX_UNSET;
        boolean currentlyVulkan = mpv.isVulkanRenderer();
        MpvAutoRenderPolicy.Decision renderDecision = MpvAutoRenderPolicy.evaluate(
                PlaybackPerformanceSetting.isAuto(
                        PlayerSetting.MPV, PlaybackPerformanceCatalog.MPV_RENDER),
                engine.isHard(), dolbyVisionProfile, dolbyVisionSupport,
                MPVLib.isBundledVulkanEnabled(App.get()),
                MPVLib.isDeviceVulkan13Capable(App.get()),
                currentlyVulkan, mpvAutoVulkanDisabledForItem);
        boolean enableAutoVulkan = renderDecision.action()
                == MpvAutoRenderPolicy.Action.ENABLE_VULKAN;
        if (enableAutoVulkan) {
            mpvAutoVulkanPinnedForItem = true;
            mpv.setVulkanRenderOverride(true);
        }
        if (dolbyVision && decision.reason().startsWith("dolby-vision-hw-")) {
            mpvAutoGpuPinnedForSession = true;
        }
        mpvAutoOutputEvaluated = true;
        boolean currentlyDirect = isMpvSurfaceDirect();
        MpvAutoOutputPolicy.Transition transition = MpvAutoOutputPolicy.transition(decision.eligible(), currentlyDirect);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "auto decision eligible=%s transition=%s reason=%s renderAction=%s renderReason=%s size=%dx%d tracksReady=%s early=%s subtitle=%s lutOrFilter=%s customGpu=%s dvProfile=%d dvSupport=%s direct=%s gpuPinned=%s autoVulkan=%s attempts=%d", decision.eligible(), transition, decision.reason(), renderDecision.action(), renderDecision.reason(), width, height, tracksReady, earlyEvaluation, subtitleActive, lutOrFilterActive, customGpuProcessing, dolbyVisionProfile, dolbyVisionSupport, currentlyDirect, mpvAutoGpuPinnedForSession, mpvAutoVulkanPinnedForItem, mpvAutoOutputProbeAttempts);
        boolean transitionRequested = dv7HandlingChanged || dv8HandlingChanged || enableAutoVulkan
                || transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT
                || transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT;
        boolean requestAccepted = true;
        if (dv7HandlingChanged || dv8HandlingChanged) {
            Boolean outputOverride = enableAutoVulkan
                    || transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT
                    ? Boolean.FALSE
                    : transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT
                    ? Boolean.TRUE : null;
            String reason = dv7HandlingChanged
                    ? "auto-dv7-" + mpv.getDv7HandlingOption()
                    : "auto-dv8-" + mpv.getDv8HandlingOption();
            requestAccepted = rebuildAndRestartMpv(outputOverride, reason);
        } else if (enableAutoVulkan) {
            requestAccepted = rebuildAndRestartMpv(false,
                    "auto-" + renderDecision.reason());
        } else if (transition == MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT) {
            requestAccepted = rebuildAndRestartMpv(true, "auto-" + decision.reason());
        } else if (transition == MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT) {
            requestAccepted = rebuildAndRestartMpv(false, "auto-" + decision.reason());
        }
        String oldOutput = currentlyDirect ? "surface-direct"
                : currentlyVulkan ? "gpu-vulkan" : "gpu-opengl";
        String targetOutput = enableAutoVulkan ? "gpu-vulkan"
                : decision.eligible() ? "surface-direct" : "gpu";
        PlaybackTelemetry.DecisionOutcome telemetryOutcome = transitionRequested
                ? requestAccepted ? PlaybackTelemetry.DecisionOutcome.REQUESTED : PlaybackTelemetry.DecisionOutcome.FAILED
                : PlaybackTelemetry.DecisionOutcome.HELD;
        playbackTelemetryCoordinator.publishDecision(playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_OUTPUT,
                        telemetryOutcome,
                        oldOutput,
                        targetOutput,
                        transitionRequested && requestAccepted ? targetOutput : oldOutput,
                        enableAutoVulkan ? renderDecision.reason() : decision.reason(),
                        transitionRequested ? requestAccepted ? "none" : "rebuild-rejected" : "no-transition",
                        List.of(
                                PlaybackTelemetry.DecisionInput.number("width", width, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("height", height, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("hard_decode", engine.isHard(), PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("leanback", Util.isLeanback(), PlaybackAutoContext.ValueSource.SYSTEM_API, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("tracks_ready", tracksReady, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("early_evaluation", earlyEvaluation, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("subtitle_active", subtitleActive, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("lut_or_filter", lutOrFilterActive, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("custom_gpu", customGpuProcessing, PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.bool("currently_direct", currentlyDirect, PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.MEDIUM),
                                PlaybackTelemetry.DecisionInput.bool("eligible", decision.eligible(), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number("probe_attempts", mpvAutoOutputProbeAttempts, PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH))),
                SystemClock.elapsedRealtime());
        if (!transitionRequested || !requestAccepted) {
            callback.onPlayerOutputReady();
        }
        return true;
    }

    private boolean hasRequestedSubtitle(List<Track> tracks) {
        return findRequestedSubtitle(tracks) != null;
    }

    private Track findRequestedSubtitle(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (Track track : tracks) {
            if (track.getType() == C.TRACK_TYPE_TEXT
                    && track.isSelected() && !track.isDisabled()) return track;
        }
        return null;
    }

    private void restoreTrackSelection(List<Track> tracks) {
        if (tracks != null && !tracks.isEmpty()) engine.setTrack(tracks);
    }

    private void onMpvVideoSizeProbed(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) return;
        if (!isMpv()
                || MpvPerformanceSetting.getOutputMode()
                != MpvPerformanceSetting.OUTPUT_AUTO
                || mpvAutoOutputEvaluated) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("mpv-output", "auto size probe size=%dx%d attempts=%d", width, height, mpvAutoOutputProbeAttempts);
        mpvAutoOutputEvaluationScheduled = false;
        mpvOutputEvaluationSeq++;
        if (!evaluateMpvAutoOutput()) scheduleMpvAutoOutputEvaluation();
    }

    private boolean retryMpvDv7P81Failure(PlaybackException error) {
        if (error == null || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isDv7P81Active()) return false;
        String message = error.getMessage();
        boolean conversionOrDecodeFailure = error.errorCode
                == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || message != null && (message.startsWith(MpvPlayer.ERROR_DECODE_FAILED)
                || message.startsWith(MpvPlayer.ERROR_INVALID_MEDIA_DATA));
        if (!conversionOrDecodeFailure || !mpv.prepareDv7P81Hdr10Fallback()) {
            return false;
        }
        return rebuildAndRestartMpv(null, "dv7-p81-hdr10-fallback");
    }

    private boolean retryMpvDv7P81FirstFrameTimeout() {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || player == null
                || !mpv.isDv7P81Active()
                || playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                || !mpv.prepareDv7P81Hdr10Fallback()) {
            return false;
        }
        long position = Math.max(0, player.getCurrentPosition());
        PlaybackTrace.log("mpv-dv", playbackTrace.current(),
                "P8.1 produced no first frame before startup timeout; retry HDR10 position=%d",
                position);
        return rebuildAndRestartMpv(null, "dv7-p81-first-frame-timeout");
    }

    private boolean retryMpvSurfaceDirectFailure(PlaybackException error) {
        if (!isMpvSurfaceDirect() || mpvSurfaceFallbackTried || error == null) return false;
        if (isDv7NativeAttemptRequested()) return false;
        String message = error.getMessage();
        boolean outputFailure = error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || message != null && (message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED) || message.startsWith(MpvPlayer.ERROR_DECODE_FAILED));
        if (!outputFailure) return false;
        mpvSurfaceFallbackTried = true;
        mpvAutoOutputEvaluated = true;
        mpvOutputEvaluationSeq++;
        PlaybackTrace.log("mpv-output", playbackTrace.current(), "surface direct failed; fallback gpu once code=%d message=%s", error.errorCode, message);
        return rebuildAndRestartMpv(false, "surface-direct-failure");
    }

    private boolean retryMpvVulkanBackendFailure(PlaybackException error) {
        if (mpvVulkanFallbackTried || error == null
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.shouldFallbackVulkanToStable()) return false;
        String message = error.getMessage();
        boolean outputFailure = error.errorCode
                == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || message != null
                && message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED);
        if (!outputFailure) return false;
        return retryMpvVulkanBackendToStable(mpv, "direct-output-failure");
    }

    private boolean retryMpvVulkanBackendTimeout() {
        if (mpvVulkanFallbackTried || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.shouldFallbackVulkanToStable()) return false;
        VideoSize size = mpv.getVideoSizeSnapshot();
        if ((size == null || size.width <= 0 || size.height <= 0)
                && getVideoWidth() <= 0 && getVideoHeight() <= 0) return false;
        return retryMpvVulkanBackendToStable(mpv, "direct-first-frame-timeout");
    }

    private boolean retryMpvVulkanBackendToStable(MpvPlayerEngine mpv,
                                                   String reason) {
        mpvVulkanFallbackTried = true;
        MpvVulkanBackendPolicy.rememberDirectFailure();
        mpv.setVulkanBackendOverride(MpvVulkanBackendPolicy.STABLE);
        PlaybackTrace.log("mpv-vulkan", playbackTrace.current(),
                "backend direct failed; fallback stable once reason=%s", reason);
        return rebuildAndRestartMpv(false, reason);
    }

    private boolean retryMpvAutoVulkanFailure(PlaybackException error) {
        if (error == null || !mpvAutoVulkanPinnedForItem
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isVulkanRenderer()) return false;
        String message = error.getMessage();
        boolean outputFailure = error.errorCode
                == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                || message != null
                && message.startsWith(MpvPlayer.ERROR_VIDEO_OUTPUT_FAILED);
        return outputFailure
                && retryMpvAutoVulkanToOpenGl("auto-vulkan-output-failure");
    }

    private boolean retryMpvAutoVulkanToOpenGl(String reason) {
        if (!mpvAutoVulkanPinnedForItem
                || !(engine instanceof MpvPlayerEngine mpv)
                || !mpv.isVulkanRenderer()) return false;
        mpvAutoVulkanPinnedForItem = false;
        mpvAutoVulkanDisabledForItem = true;
        mpv.setVulkanRenderOverride(false);
        mpv.setVulkanBackendOverride(null);
        PlaybackTrace.log("mpv-vulkan", playbackTrace.current(),
                "automatic DV5 Vulkan failed; fallback OpenGL once reason=%s",
                reason);
        return rebuildAndRestartMpv(false, reason);
    }

    private boolean isDv7NativeAttemptRequested() {
        if (!isMpv() || !(engine instanceof MpvPlayerEngine mpv)
                || !engine.isHard() || !mpv.isDv7NativeActive()) return false;
        PlayerEngine.VideoPlaybackDetails details =
                engine.getVideoPlaybackDetails();
        return details != null && details.dolbyVisionProfile() == 7;
    }

    private PlayerEngine buildEngine(int type, int decode) {
        return switch (type) {
            case PlayerSetting.IJK -> new IjkPlayerEngine(decode, listener);
            case PlayerSetting.MPV -> new MpvPlayerEngine(decode, listener, this::onMpvVideoSizeProbed);
            default -> new ExoPlayerEngine(decode, listener);
        };
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, true);
    }

    public void start(PlaySpec spec, long timeout, boolean playWhenReady) {
        start(spec, timeout, playWhenReady, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, boolean playWhenReady, long positionMs) {
        Integer forced = spec.getForcedPlayer();
        endPlaybackTelemetrySession("replace-start");
        prepareIjkRuntimeForUserPlayback();
        clearPendingSwitchRestore();
        clearDanmaku("start");
        this.spec = spec;
        pendingInitialStartPositionMs = positionMs > 0 ? positionMs : C.TIME_UNSET;
        prepareMpvOutputForNewItem();
        beginPlaybackTrace("start", false);
        this.playWhenReady = playWhenReady;
        retry = 0;
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        setMediaItem(timeout);
        int desiredKernel;
        if (forced != null) {
            desiredKernel = forced;
        } else {
            desiredKernel = PlayerSetting.getPlayer();
        }
        if (desiredKernel != playerType) {
            final Integer target = desiredKernel;
            App.post(() -> switchPlayer(target, false), 200);
        }
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, true);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, boolean playWhenReady) {
        parse(key, result, useParse, metadata, playWhenReady, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata,
                      boolean playWhenReady, long positionMs) {
        endPlaybackTelemetrySession("replace-parse");
        prepareIjkRuntimeForUserPlayback();
        stopParse();
        clearPendingSwitchRestore();
        clearDanmaku("parse");
        spec = PlaySpec.fromParse(result, key, metadata, useParse);
        pendingInitialStartPositionMs = positionMs > 0 ? positionMs : C.TIME_UNSET;
        prepareMpvOutputForNewItem();
        beginPlaybackTrace("parse", false);
        this.playWhenReady = playWhenReady;
        retry = 0;
        localProxyRetry = 0;
        hardDecodeSwitchRetryArmed = false;
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private boolean reparseForPlayerSwitch(long position, float speed, boolean repeat) {
        if (spec == null || !spec.canReparse() || !spec.isParseSource()) return false;
        Result result = spec.getParseResult();
        boolean useParse = spec.isParseUseParse();
        MediaMetadata metadata = spec.getMetadata();
        String key = spec.getKey();
        pendingSwitchRestore = true;
        pendingSwitchPositionMs = position;
        pendingSwitchSpeed = speed;
        pendingSwitchRepeat = repeat;
        stopParse();
        if (spec.isParseSource()) {
            spec = PlaySpec.fromParse(result, key, metadata, useParse);
            bindPlaybackTrace();
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player reparse type=%d position=%d useParse=%s spec=%s", playerType, position, useParse, debugSpec());
            parseJob = ParseJob.create(this).start(result, useParse);
        } else {
            refreshDirectForPlayerSwitch(result, key, metadata);
        }
        return true;
    }

    private void refreshDirectForPlayerSwitch(Result result, String key, MediaMetadata metadata) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refresh direct type=%d keyLen=%d flag=%s url=%s", playerType, safeLength(key), result.getFlag(), summarizeUrl(result.getUrl().v()));
        Task.execute(() -> {
            try {
                Result refreshed = SiteApi.playerContent(key, result.getFlag(), result.getUrl().v(), playerType);
                App.post(() -> startRefreshedSwitchResult(refreshed, key, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    clearPendingSwitchRestore();
                    callback.onError(e.getMessage());
                });
            }
        });
    }

    private void startRefreshedSwitchResult(Result result, String key, MediaMetadata metadata) {
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            clearPendingSwitchRestore();
            callback.onError(result == null ? ResUtil.getString(R.string.error_play_url) : result.hasMsg() ? result.getMsg() : ResUtil.getString(R.string.error_play_url));
            return;
        }
        if (result.needParse()) {
            spec = PlaySpec.fromParse(result, key, metadata, false);
            bindPlaybackTrace();
            parseJob = ParseJob.create(this).start(result, false);
            return;
        }
        spec = PlaySpec.from(result, key, metadata);
        bindPlaybackTrace();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player refreshed direct spec=%s", debugSpec());
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    private void restoreAfterSwitchReparse() {
        if (!pendingSwitchRestore) return;
        long position = pendingSwitchPositionMs;
        float speed = pendingSwitchSpeed;
        boolean repeat = pendingSwitchRepeat;
        clearPendingSwitchRestore();
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void clearPendingSwitchRestore() {
        pendingSwitchRestore = false;
        pendingIjkRuntimeFallbackReparse = false;
        pendingSwitchPositionMs = C.TIME_UNSET;
        pendingSwitchSpeed = 1f;
        pendingSwitchRepeat = false;
    }

    public void setMediaItem() {
        playWhenReady = player == null || player.getPlayWhenReady();
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        int seq = ++prepareSeq;
        if (rejectMpvDrmMedia()) return;
        if (LocalProxyDebug.shouldAwaitReady(spec.getUrl())) {
            awaitLocalProxyAndSetMediaItem(seq, timeout);
            return;
        }
        setMediaItemNow(timeout, true);
    }

    private void awaitLocalProxyAndSetMediaItem(int seq, long timeout) {
        PlaySpec target = spec;
        String url = target.getUrl();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await start seq=%d timeout=%d spec=%s", seq, timeout, debugSpec());
        Task.execute(() -> {
            boolean ready = LocalProxyDebug.awaitReady(url, LOCAL_PROXY_READY_TIMEOUT_MS);
            App.post(() -> {
                if (seq != prepareSeq || spec != target || engine == null) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await skip seq=%d current=%d ready=%s", seq, prepareSeq, ready);
                    return;
                }
                if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await done seq=%d ready=%s spec=%s", seq, ready, debugSpec());
                setMediaItemNow(timeout, true);
            });
        });
    }

    private void setMediaItemNow(long timeout, boolean notifyPrepare) {
        if (spec == null || spec.getUrl() == null || engine == null) return;
        prepareExoFrameSchedulingForNewPlayback();
        spec.setPlaybackTraceId(playbackTrace.ensure());
        spec.refreshPlaybackRoute();
        publishPlaybackAutoContext(false);
        activateIjkRuntimeProfileIfEligible(SystemClock.elapsedRealtime());
        applyIjkAutoInitialControl();
        applyMpvAutoInitialControl();
        logPlaybackRoute();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "setMediaItem timeout=%d notify=%s spec=%s", timeout, notifyPrepare, debugSpec());
        resetNetworkProtectionSession("new-media");
        App.removeCallbacks(runnable);
        setDanmakus(spec.getDanmakus());
        prepareLutPipeline();
        initTrack = false;
        waitingLutBeforePlay = false;
        applySubtitleStyle();
        playbackTrace.mark(PlaybackTrace.Stage.PREPARE, "player=" + playerType + " decode=" + engine.getDecode());
        long initialPositionMs = pendingInitialStartPositionMs;
        pendingInitialStartPositionMs = C.TIME_UNSET;
        engine.start(spec.checkUa(), initialPositionMs, playWhenReady);
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
        scheduleMpvAutoOutputEvaluation();
        startNativeAudioSession(playWhenReady);
        App.post(runnable, timeout);
        if (notifyPrepare) callback.onPrepare();
    }

    private void prepareExoFrameSchedulingForNewPlayback() {
        if (!(engine instanceof ExoPlayerEngine exo)) return;
        if (!exo.prepareFrameSchedulingForNextPlayback()) return;
        rebuildPlayer(false);
    }

    private void applySubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle(PlayerSetting.getSubtitleTextSize(), PlayerSetting.getSubtitlePosition());
    }

    private void startNativeAudioSession(boolean shouldPlay) {
        if (!shouldPlay || !isNativePlayer()) return;
        requestNativeAudioFocus();
        registerNoisyReceiver();
    }

    private void stopNativeAudioSession() {
        unregisterNoisyReceiver();
        abandonNativeAudioFocus();
        resumeOnAudioFocusGain = false;
    }

    private void requestNativeAudioFocus() {
        AudioManager manager = audioManager();
        if (manager == null || audioFocusHeld) return;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusApi26.request(manager, audioFocusChangeListener);
            result = audioFocusRequest == null ? AudioManager.AUDIOFOCUS_REQUEST_FAILED : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            result = manager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!audioFocusHeld && SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus request denied type=%d", playerType);
    }

    private void abandonNativeAudioFocus() {
        if (!audioFocusHeld) return;
        AudioManager manager = audioManager();
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioFocusApi26.abandon(manager, audioFocusRequest);
            else manager.abandonAudioFocus(audioFocusChangeListener);
        }
        audioFocusRequest = null;
        audioFocusHeld = false;
    }

    private void registerNoisyReceiver() {
        if (noisyReceiverRegistered) return;
        try {
            App.get().registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyReceiverRegistered = true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "register noisy receiver failed error=%s", causeChain(e));
        }
    }

    private void unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return;
        try {
            App.get().unregisterReceiver(noisyReceiver);
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "unregister noisy receiver failed error=%s", causeChain(e));
        }
        noisyReceiverRegistered = false;
    }

    private void onNativeAudioBecomingNoisy() {
        if (!isNativePlayer() || player == null) return;
        boolean wasPlaying = player.isPlaying();
        player.pause();
        stopNativeAudioSession();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio noisy pause type=%d wasPlaying=%s", playerType, wasPlaying);
    }

    private void onNativeAudioFocusChanged(int focusChange) {
        if (!isNativePlayer() || player == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false;
                    startNativeAudioSession(true);
                    player.play();
                }
            }
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnAudioFocusGain = player.isPlaying();
                player.pause();
            }
            case AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnAudioFocusGain = false;
                player.pause();
                stopNativeAudioSession();
            }
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "native audio focus changed type=%d change=%d resume=%s", playerType, focusChange, resumeOnAudioFocusGain);
    }

    private AudioManager audioManager() {
        return (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
    }

    private static final class AudioFocusApi26 {

        private static Object request(AudioManager manager, AudioManager.OnAudioFocusChangeListener listener) {
            android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            android.media.AudioFocusRequest request = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .build();
            return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? request : null;
        }

        private static void abandon(AudioManager manager, Object request) {
            if (request instanceof android.media.AudioFocusRequest) manager.abandonAudioFocusRequest((android.media.AudioFocusRequest) request);
        }
    }

    private boolean rejectMpvDrmMedia() {
        if (!isMpv() || spec == null || spec.getDrm() == null) return false;
        App.removeCallbacks(runnable);
        clearPendingSwitchRestore();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "reject drm for mpv spec=%s drm=%s", debugSpec(), spec.getDrm().getType());
        callback.onError(ResUtil.getString(R.string.error_play_mpv_drm_unsupported));
        return true;
    }

    private void prepareLutPipeline() {
        lutApplySeq++;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        dynamicLutEffect.clear();
        clearLutWarmupRecovery();
        if (videoEffectsDirty) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "rebuild clean player before media item reason=prepare spec=%s", debugSpec());
            rebuildPlayer();
        }
        if (shouldPreinstallDynamicLutPipeline()
                && safeSetVideoEffects(dynamicLutEffect.effects(), "prepare_dynamic_passthrough")) {
            lutPipelineReadyForItem = true;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "preinstalled dynamic pipeline before playback spec=%s", debugSpec());
            return;
        }
        clearVideoEffects("prepare");
    }

    private boolean shouldPreinstallDynamicLutPipeline() {
        return LutSetting.isEnabled()
                && engine != null
                && !engine.supportsNativeLut()
                && canWarmLutPipeline()
                && TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean shouldPrepareLutBeforePlay() {
        return false;
    }

    public void applyLut(boolean notify) {
        applyLut(notify, false);
    }

    public void applyLutPreview(boolean notify) {
        applyLut(notify, true);
    }

    private void applyLut(boolean notify, boolean preview) {
        if (engine == null) return;
        int seq = ++lutApplySeq;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request seq=%d notify=%s preview=%s enabled=%s preset=%s state=%s videoFormat=%s tracksEmpty=%s active=%s dirty=%s applied=%s applying=%s pendingPreview=%s", seq, notify, preview, LutSetting.isEnabled(), LutSetting.getPresetId(), stateName(player.getPlaybackState()), engine.getVideoFormat(), engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty(), videoEffectsActive, videoEffectsDirty, lutAppliedForItem, lutApplyInProgress, pendingLutPreview);
        if (!lutAllowed) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("disallowed");
            completeLutBeforePlay("disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=off state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("off");
            completeLutBeforePlay("off");
            return;
        }
        LutPreset preset = LutStore.find(LutSetting.getPresetId());
        if (preset == null) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=missing state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("missing");
            completeLutBeforePlay("missing");
            if (notify) Notify.show(R.string.lut_missing);
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format notify=%s preview=%s state=%s spec=%s", notify, preview, stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (notify || preview) pendingLutPreview = preview;
        if (engine.supportsNativeLut()) {
            applyNativeLut(seq, preset, notify, preview);
            return;
        }
        if (!ensureLutPipelineReadyForCurrentItem("request")) {
            return;
        }
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                ColorLut colorLut = LutEffectFactory.createColorLut(preset, strength);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create dynamic preset=%s format=%s strength=%d preview=%s seconds=%d cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyLutColor(seq, colorLut, notify, preview, previewSeconds));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("error");
                    completeLutBeforePlay("error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLut(int seq, LutPreset preset, boolean notify, boolean preview) {
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                MpvLutShader shader = MpvLutShaderFactory.create(preset, strength, preview);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader preset=%s format=%s strength=%d preview=%s seconds=%d clock=monotonic cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyNativeLutShader(seq, shader, notify, preview));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "create shader failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("native_error");
                    completeLutBeforePlay("native_error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyNativeLutShader(int seq, MpvLutShader shader, boolean notify, boolean preview) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (safeSetNativeLut(shader, preview ? "preview_native" : "apply_native")) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
            if (preview) scheduleNativeLutPreviewCommit(seq);
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview_native" : "apply_native");
    }

    private void scheduleNativeLutPreviewCommit(int seq) {
        int holdMs = Math.max(1, LutSetting.getPreviewSeconds()) * 1000;
        long slideStartMs = SystemClock.elapsedRealtime() + holdMs;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "preview scheduled seq=%d hold=%d slide=%d", seq, holdMs, MpvLutShaderFactory.PREVIEW_SLIDE_MS);
        App.post(() -> updateNativeLutPreview(seq, slideStartMs), holdMs);
    }

    private void updateNativeLutPreview(int seq, long slideStartMs) {
        if (seq != lutApplySeq || engine == null || !engine.supportsNativeLut()) return;
        if (!lutAllowed || !LutSetting.isEnabled()) return;
        long elapsedMs = Math.max(0, SystemClock.elapsedRealtime() - slideStartMs);
        float progress = Math.min(1f, elapsedMs / (float) MpvLutShaderFactory.PREVIEW_SLIDE_MS);
        engine.setNativeLutPreviewProgress(progress);
        if (progress < 1f) {
            App.post(() -> updateNativeLutPreview(seq, slideStartMs), LUT_PREVIEW_FRAME_INTERVAL_MS);
            return;
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "preview completed seq=%d elapsed=%d", seq, elapsedMs);
    }

    private void applyLutColor(int seq, ColorLut colorLut, boolean notify, boolean preview, int previewSeconds) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            dynamicLutEffect.clear();
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before set effects preview=%s spec=%s", preview, debugSpec());
            return;
        }
        dynamicLutEffect.set(colorLut, preview, previewSeconds);
        boolean applied = videoEffectsActive;
        if (applied) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "updated dynamic pipeline in place reason=%s preview=%s", preview ? "preview_dynamic" : "apply_dynamic", preview);
        } else {
            applied = safeSetVideoEffects(dynamicLutEffect.effects(), preview ? "preview_dynamic" : "apply_dynamic");
        }
        if (applied) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview" : "apply");
    }

    private void applyLutForCurrentItem() {
        if (engine == null) return;
        if (!lutAllowed) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_disallowed");
            completeLutBeforePlay("auto_disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_off");
            completeLutBeforePlay("auto_off");
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before auto apply state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (lutApplyInProgress) return;
        if (lutAppliedForItem) return;
        if (!ensureLutPipelineReadyForCurrentItem("auto")) return;
        boolean preview = pendingLutPreview || lutWarmupReloadPreviewPending;
        lutWarmupReloadPreviewPending = false;
        applyLut(false, preview);
    }

    private void completeLutBeforePlay(String reason) {
        if (!waitingLutBeforePlay || player == null) return;
        waitingLutBeforePlay = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "start playback after lut decision reason=%s active=%s dirty=%s", reason, videoEffectsActive, videoEffectsDirty);
        player.play();
    }

    private boolean ensureLutPipelineReadyForCurrentItem(String reason) {
        if (lutPipelineReadyForItem) return true;
        if (lutPipelinePrepareInProgress) return false;
        if (!canWarmLutPipeline()) return true;
        if (shouldWaitForVideoFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before pipeline warmup reason=%s state=%s spec=%s", reason, stateName(player.getPlaybackState()), debugSpec());
            return false;
        }
        String unavailable = getLutUnavailableReason();
        if (!TextUtils.isEmpty(unavailable)) return true;
        return prepareLutPipelineForCurrentItem(reason);
    }

    private boolean prepareLutPipelineForCurrentItem(String reason) {
        if (spec == null || engine == null || player == null) return true;
        lutPipelinePrepareInProgress = true;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        dynamicLutEffect.clear();
        long position = Math.max(0, getPosition());
        boolean playWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        if (!safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_prepare_dynamic_passthrough")) {
            lutPipelinePrepareInProgress = false;
            return true;
        }
        lutPipelineReadyForItem = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "prepare current item with effects reason=%s position=%d play=%s spec=%s", reason, position, playWhenReady, debugSpec());
        startLutWarmupRecovery();
        engine.restart(spec.checkUa(), position, playWhenReady);
        if (speed != 1f) setSpeed(speed);
        lutPipelinePrepareInProgress = false;
        return false;
    }

    private void startLutWarmupRecovery() {
        lutWarmupRecoveryActive = true;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private void clearLutWarmupRecovery() {
        lutWarmupRecoveryActive = false;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private boolean retryLutWarmupByRefresh(String reason) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        lutWarmupRefreshRequested = true;
        lutWarmupRecoveryActive = false;
        lutWarmupReloadPreviewPending = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request playback refresh after warmup failure reason=%s errors=%d spec=%s", reason, lutWarmupRecoveredErrors, debugSpec());
        callback.onReload(RELOAD_LUT_WARMUP);
        return true;
    }

    private boolean retryLutWarmupByRefresh(PlayerEngine.ErrorAction action, PlaybackException e) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        if (action == PlayerEngine.ErrorAction.RECOVERED && ++lutWarmupRecoveredErrors < LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD) return false;
        if (action != PlayerEngine.ErrorAction.RECOVERED && action != PlayerEngine.ErrorAction.FATAL && action != PlayerEngine.ErrorAction.RELOAD) return false;
        return retryLutWarmupByRefresh("error_" + e.errorCode + "_" + action);
    }

    private boolean shouldWaitForLutFormat() {
        if (engine == null || !LutSetting.isEnabled()) return false;
        return shouldWaitForVideoFormat();
    }

    private boolean shouldWaitForVideoFormat() {
        if (engine == null) return false;
        Format currentFormat = engine.getVideoFormat();
        if (isUsableVideoFormat(currentFormat)) return false;
        Tracks tracks = engine.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return true;
        boolean hasVideo = false;
        boolean hasUsableFormat = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            hasVideo = true;
            for (int i = 0; i < group.length; i++) {
                if (isUsableVideoFormat(group.getTrackFormat(i))) {
                    hasUsableFormat = true;
                    break;
                }
            }
            if (hasUsableFormat) break;
        }
        return hasVideo && !hasUsableFormat;
    }

    private boolean isUsableVideoFormat(Format format) {
        if (format == null) return false;
        return !TextUtils.isEmpty(format.sampleMimeType) || !TextUtils.isEmpty(format.codecs) || format.colorInfo != null || format.width > 0 || format.height > 0;
    }

    private void clearVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        safeSetVideoEffects(Collections.emptyList(), reason);
    }

    private void setNeutralVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (engine != null && engine.supportsNativeLut()) {
            safeSetNativeLut(null, reason);
            return;
        }
        if (canKeepWarmNeutralEffects()) safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_dynamic_passthrough");
        else clearVideoEffects(reason);
    }

    private boolean canKeepWarmNeutralEffects() {
        if (!LutSetting.isEnabled()) return false;
        if (!canWarmLutPipeline()) return false;
        if (shouldWaitForVideoFormat()) return false;
        return TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean canWarmLutPipeline() {
        if (!lutAllowed) return false;
        if (engine == null || !engine.supportsVideoEffects()) return false;
        if (spec != null && spec.getDrm() != null) return false;
        if (PlayerSetting.isTunnel()) return false;
        if (engine.getDecode() == PlayerEngine.SOFT) return false;
        if (PlayerSetting.isVideoPrefer()) return false;
        return true;
    }

    private boolean safeSetVideoEffects(List<Effect> effects, String reason) {
        if (engine == null) return false;
        boolean empty = effects == null || effects.isEmpty();
        if (empty && !videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "skip clear effects reason=%s", reason);
            return false;
        }
        if (!empty && videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "reuse active effects reason=%s", reason);
            return true;
        }
        try {
            engine.setVideoEffects(empty ? Collections.emptyList() : effects);
            if (empty) {
                videoEffectsActive = false;
            } else {
                videoEffectsActive = true;
                videoEffectsDirty = true;
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects=%d reason=%s active=%s dirty=%s", empty ? 0 : effects.size(), reason, videoEffectsActive, videoEffectsDirty);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private boolean safeSetNativeLut(MpvLutShader shader, String reason) {
        if (engine == null || !engine.supportsNativeLut()) return false;
        try {
            engine.setNativeLutShader(shader);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader=%s reason=%s", shader == null ? "none" : shader.diagnostics(), reason);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-mpv", "set shader failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        setDanmaku(item, false);
    }

    public void reloadDanmaku(Danmaku item) {
        setDanmaku(item, true);
    }

    private void setDanmaku(Danmaku item, boolean force) {
        if (item.isEmpty()) {
            if (spec != null) spec.setDanmaku(item);
            clearDanmaku("empty_source");
            return;
        }
        if (danmakuController == null) return;
        String url = DanmakuUrlPolicy.normalize(DanmakuSetting.getValidApiUrl(), item.getRealUrl());
        DanmakuUrlPolicy.SourceType sourceType = DanmakuUrlPolicy.classify(url);
        if (!sourceType.isSupported()) {
            if (spec != null) spec.setDanmaku(item);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "reject %s", DanmakuUrlPolicy.logSummary(url));
            clearDanmaku("unsupported_source");
            return;
        }
        String key = normalizeDanmakuKey(url);
        if (!force && TextUtils.equals(currentDanmakuUrl, url)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip same %s", DanmakuUrlPolicy.logSummary(url));
            return;
        }
        if (force && shouldSkipForcedDanmakuReload(key)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip duplicate reload key=%s %s", summarizeUrl(key), DanmakuUrlPolicy.logSummary(url));
            return;
        }
        if (spec != null) spec.setDanmaku(item);
        if (force && currentDanmakuUrl != null) danmakuController.clearItems();
        currentDanmakuUrl = url;
        currentDanmakuKey = key;
        if (sourceType.isLive()) {
            danmakuController.clearItems();
            loadingDanmakuKey = null;
            danmakuLoadStartedAtMs = 0;
            danmakuLoadInProgress = false;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "select live source pending websocket connection %s", DanmakuUrlPolicy.logSummary(url));
            if (danmakuForeground && DanmakuSetting.isShow()) connectLiveDanmakuSession(url);
            return;
        }
        stopLiveDanmakuSession("static_source");
        loadingDanmakuKey = key;
        danmakuLoadStartedAtMs = SystemClock.elapsedRealtime();
        danmakuLoadInProgress = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "%s name=%s %s key=%s", force ? "reload" : "load", item.getName(), DanmakuUrlPolicy.logSummary(url), summarizeUrl(key));
        danmakuController.setDataSource(Uri.parse(url));
    }

    private boolean shouldSkipForcedDanmakuReload(String key) {
        if (TextUtils.isEmpty(key) || !TextUtils.equals(currentDanmakuKey, key) || danmakuLoadStartedAtMs <= 0) return false;
        if (danmakuLoadInProgress && (TextUtils.isEmpty(loadingDanmakuKey) || TextUtils.equals(loadingDanmakuKey, key))) return true;
        long elapsed = SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        return elapsed >= 0 && elapsed < DANMAKU_FORCE_RELOAD_DEBOUNCE_MS;
    }

    private void finishDanmakuLoad(Uri uri) {
        String key = normalizeDanmakuKey(uri == null ? "" : uri.toString());
        if (!TextUtils.isEmpty(loadingDanmakuKey) && !TextUtils.equals(loadingDanmakuKey, key)) return;
        danmakuLoadInProgress = false;
        loadingDanmakuKey = null;
    }

    private void clearDanmakuState() {
        currentDanmakuUrl = null;
        currentDanmakuKey = null;
        loadingDanmakuKey = null;
        danmakuLoadStartedAtMs = 0;
        danmakuLoadInProgress = false;
    }

    private void clearDanmaku(String reason) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "clear reason=%s current=%s", reason, DanmakuUrlPolicy.logSummary(currentDanmakuUrl));
        stopLiveDanmakuSession(reason);
        liveDanmakuBatcher.clear();
        liveDanmakuBuffer.clear();
        if (danmakuController != null) danmakuController.clearItems();
        clearDanmakuState();
    }

    private void connectLiveDanmakuSession(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (liveDanmakuSession == null) {
            liveDanmakuSession = new LiveDanmakuWebSocketSession(App.get(), new LiveDanmakuWebSocketSession.Listener() {
                @Override
                public void onStateChanged(LiveDanmakuWebSocketSession.State state, long generation, String sourceUrl, int code, String detail) {
                    liveDanmakuGeneration = generation;
                    liveDanmakuMetrics.onState(state, code, SystemClock.elapsedRealtime());
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING) {
                        liveDanmakuBuffer.reset(generation);
                        liveDanmakuBatcher.reset(generation);
                    } else if (state == LiveDanmakuWebSocketSession.State.RETRY_WAIT || state == LiveDanmakuWebSocketSession.State.STOPPED || state == LiveDanmakuWebSocketSession.State.RELEASED) {
                        liveDanmakuBatcher.clear();
                        liveDanmakuBuffer.clear();
                    }
                    if (state != LiveDanmakuWebSocketSession.State.OPEN) {
                        App.post(() -> logLiveDanmakuMetrics("state_" + state, false));
                        clearLiveDanmakuRenderer(generation);
                    }
                    if (state == LiveDanmakuWebSocketSession.State.CONNECTING || state == LiveDanmakuWebSocketSession.State.OPEN || state == LiveDanmakuWebSocketSession.State.RETRY_WAIT) scheduleLiveDanmakuMetrics();
                    else App.removeCallbacks(liveDanmakuMetricsRunnable);
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku-ws", "state=%s generation=%d code=%d detail=%s %s", state, generation, code, detail, DanmakuUrlPolicy.logSummary(sourceUrl));
                }

                @Override
                public void onMessage(long generation, String text) {
                    if (generation != liveDanmakuGeneration) return;
                    liveDanmakuMetrics.onFrame();
                    long parseStartedNs = SystemClock.elapsedRealtimeNanos();
                    LiveDanmakuParser.Result result = LiveDanmakuParser.parse(text, generation, SystemClock.elapsedRealtime());
                    liveDanmakuMetrics.onParse(result, SystemClock.elapsedRealtimeNanos() - parseStartedNs);
                    if (!result.isAccepted()) return;
                    LiveDanmakuWebSocketSession session = liveDanmakuSession;
                    if (session != null) session.markMessageAccepted(generation);
                    if (result.kind() == LiveDanmakuParser.Kind.ONLINE) {
                        liveDanmakuBuffer.updateOnline(generation, result.online());
                    } else {
                        if (!liveDanmakuPlaybackActive) return;
                        LiveDanmakuMessage message = result.message();
                        LiveDanmakuBuffer.OfferResult offer = liveDanmakuBuffer.offer(message);
                        liveDanmakuMetrics.onOffer(offer);
                        if (offer != LiveDanmakuBuffer.OfferResult.STALE) liveDanmakuBatcher.requestDrain(generation);
                    }
                }
            });
        }
        liveDanmakuGeneration = liveDanmakuSession.connect(url);
    }

    private void stopLiveDanmakuSession(String reason) {
        if (liveDanmakuSession != null) liveDanmakuGeneration = liveDanmakuSession.stop(reason);
    }

    private void releaseLiveDanmakuSession() {
        if (liveDanmakuSession == null) return;
        liveDanmakuSession.release();
        liveDanmakuSession = null;
    }

    private void onLiveDanmakuBatch(long generation, List<LiveDanmakuMessage> messages) {
        long scheduledAtMs = SystemClock.elapsedRealtime();
        App.post(() -> {
            if (generation != liveDanmakuGeneration || messages.isEmpty() || danmakuController == null || !DanmakuSetting.isShow()) return;
            liveDanmakuMetrics.onBatch(messages.size(), SystemClock.elapsedRealtime() - scheduledAtMs);
            List<androidx.media3.ui.danmaku.Danmaku> batch = new ArrayList<>(messages.size());
            for (LiveDanmakuMessage message : messages) {
                int pool = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? androidx.media3.ui.danmaku.Danmaku.POOL_SPECIAL : androidx.media3.ui.danmaku.Danmaku.POOL_NORMAL;
                long ttlMs = message.type() == LiveDanmakuMessage.Type.SUPER_CHAT ? LiveDanmakuBuffer.DEFAULT_PRIORITY_TTL_MS : LiveDanmakuBuffer.DEFAULT_NORMAL_TTL_MS;
                batch.add(new androidx.media3.ui.danmaku.Danmaku(message.text(), 0L, androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL, message.colorArgb(), 0f, pool, "", 0L)
                        .setLiveExpiryElapsedRealtimeMs(message.receivedAtMs() + ttlMs));
            }
            danmakuController.offerLiveBatch(batch);
        });
    }

    private void clearLiveDanmakuRenderer(long generation) {
        App.post(() -> {
            if (generation != liveDanmakuGeneration || danmakuController == null) return;
            danmakuController.clearLiveItems();
        });
    }

    private void discardLiveDanmakuPending() {
        liveDanmakuBuffer.discardPending();
        liveDanmakuBatcher.reset(liveDanmakuGeneration);
        clearLiveDanmakuRenderer(liveDanmakuGeneration);
    }

    private void scheduleLiveDanmakuMetrics() {
        if (!SpiderDebug.isEnabled()) return;
        App.post(liveDanmakuMetricsRunnable, LIVE_DANMAKU_METRICS_INTERVAL_MS);
    }

    private void logLiveDanmakuMetrics(String reason, boolean reschedule) {
        if (!SpiderDebug.isEnabled()) return;
        long nowMs = SystemClock.elapsedRealtime();
        LiveDanmakuMetrics.Snapshot metrics = liveDanmakuMetrics.snapshotAndReset(nowMs);
        LiveDanmakuBuffer.Snapshot buffer = liveDanmakuBuffer.snapshot();
        androidx.media3.ui.danmaku.DanmakuView.LiveStats render = danmakuController == null ? androidx.media3.ui.danmaku.DanmakuView.LiveStats.EMPTY : danmakuController.getLiveStats();
        SpiderDebug.log("danmaku-ws-metrics", "reason=%s state=%s stateMs=%d openMs=%d code=%d received=%d parsed=%d invalid=%d normal=%d super=%d online=%d queued=%d overflow=%d stale=%d batches=%d batchMessages=%d parseAvgUs=%d parseMaxUs=%d mainAvgMs=%d mainMaxMs=%d retries=%d appPending=%d/%d appExpired=%d appHigh=%d renderOffered=%d renderAdmitted=%d renderOverflow=%d renderExpired=%d trackWaits=%d renderPending=%d renderHigh=%d active=%d", reason, metrics.state(), metrics.stateDurationMs(), metrics.openDurationMs(), metrics.lastCode(), metrics.received(), metrics.parsed(), metrics.invalid(), metrics.normal(), metrics.superChat(), metrics.online(), metrics.queued(), metrics.overflow(), metrics.stale(), metrics.batches(), metrics.batchedMessages(), metrics.averageParseNanos() / 1000L, metrics.maxParseNanos() / 1000L, metrics.averageMainDelayMs(), metrics.maxMainDelayMs(), metrics.retryWaits(), buffer.normalPending(), buffer.priorityPending(), buffer.droppedExpired(), buffer.highWaterMark(), render.offered, render.admitted, render.droppedOverflow, render.droppedExpired, render.trackWaits, render.pending, render.highWaterMark, render.active);
        if (reschedule && liveDanmakuSession != null && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.STOPPED && liveDanmakuSession.state() != LiveDanmakuWebSocketSession.State.RELEASED) scheduleLiveDanmakuMetrics();
    }

    private void logDanmakuLoad(String event, Uri uri, int count, IOException error) {
        if (!SpiderDebug.isEnabled()) return;
        long elapsed = danmakuLoadStartedAtMs <= 0 ? -1 : SystemClock.elapsedRealtime() - danmakuLoadStartedAtMs;
        if (error == null) {
            SpiderDebug.log("danmaku", "load %s count=%d elapsed=%dms %s", event, count, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()));
        } else {
            SpiderDebug.log("danmaku", "load %s elapsed=%dms %s error=%s", event, elapsed, DanmakuUrlPolicy.logSummary(uri == null ? "" : uri.toString()), error.getMessage());
        }
    }

    private static String normalizeDanmakuKey(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        try {
            Uri uri = Uri.parse(value);
            String nested = getNestedDanmakuUrl(uri);
            return TextUtils.isEmpty(nested) ? value : normalizeDanmakuKey(nested);
        } catch (Throwable e) {
            return value;
        }
    }

    private static String getNestedDanmakuUrl(Uri uri) {
        if (uri == null) return "";
        String path = uri.getPath();
        if (TextUtils.isEmpty(path) || !path.endsWith("/danmaku")) return "";
        return uri.getQueryParameter("url");
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        playbackTrace.mark(PlaybackTrace.Stage.PARSE_COMPLETE, "headers=" + (headers == null ? 0 : headers.size()));
        PlaybackTrace.log("player", playbackTrace.current(), "parseSuccess from=%s url=%s headers=%s", from, summarizeUrl(url), headers == null ? 0 : headers.size());
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem(Constant.TIMEOUT_PLAY);
        restoreAfterSwitchReparse();
    }

    @Override
    public void onParseError() {
        if (pendingIjkRuntimeFallbackReparse) {
            ijkRuntimeProfileController.onSwitchStartFailed(
                    playbackAutoSession, System.currentTimeMillis());
            publishIjkRuntimeSwitchStartFailure("reparse-failed");
        }
        clearPendingSwitchRestore();
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private String debugSpec() {
        if (spec == null) return "null";
        return "trace=" + playbackTrace.current() +
                ", keyLen=" + safeLength(spec.getKey()) +
                ", url=" + summarizeUrl(spec.getUrl()) +
                ", format=" + spec.getFormat() +
                ", headers=" + (spec.getHeaders() == null ? 0 : spec.getHeaders().size()) +
                ", subs=" + (spec.getSubs() == null ? 0 : spec.getSubs().size()) +
                ", danmakus=" + (spec.getDanmakus() == null ? 0 : spec.getDanmakus().size());
    }

    private void beginPlaybackTrace(String reason) {
        beginPlaybackTrace(reason, true);
    }

    private void beginPlaybackTrace(String reason, boolean finishCurrentSession) {
        if (finishCurrentSession) {
            endPlaybackTelemetrySession("replace-" + reason);
        }
        playbackBufferingTracker.reset();
        clearExoDecoderResourceRecovery(true);
        lastIjkTimelinePublicationKey = null;
        playbackTrace.begin();
        long now = SystemClock.elapsedRealtime();
        playbackAutoSession = playbackAutoContextStore.beginSession(playbackTrace.current(), now);
        rtspLiveLagController.beginSession(playbackAutoSession);
        mpvAutoController.beginSession(playbackAutoSession);
        mpvForwardCacheController.beginSession(playbackAutoSession);
        mpvBackCacheController.beginSession(playbackAutoSession);
        mpvCacheTargetCoordinator.beginSession(playbackAutoSession);
        mpvHlsVariantController.beginSession(playbackAutoSession);
        mpvResourcePressureController.beginSession(playbackAutoSession);
        mpvPreloadController.beginSession(playbackAutoSession);
        mpvHlsManagedReload = false;
        ijkBufferController.beginSession(playbackAutoSession, now);
        ijkDecodePressureController.beginSession(playbackAutoSession);
        ijkRealtimeRecoveryController.beginSession(playbackAutoSession);
        ijkRuntimeProfileController.beginSession(playbackAutoSession);
        ijkBufferManagedReload = false;
        pendingIjkBufferDecision = null;
        pendingIjkDecodePressureDecision = null;
        pendingIjkRealtimeRecoveryDecision = null;
        playbackTrackSequence = 1;
        playbackMediaFactsCoordinator.beginSession(playbackAutoSession);
        PlaybackMemoryMonitor.process().beginSession(playbackAutoSession);
        PlaybackSystemConditionMonitor.process().beginSession(playbackAutoSession);
        playbackTelemetryCoordinator.beginSession(playbackAutoSession, now);
        beginPlaybackProfileAbSession(now);
        lastLoggedRouteTraceId = PlaybackTrace.NONE;
        bindPlaybackTrace();
        playbackTrace.mark(PlaybackTrace.Stage.REQUEST, "reason=" + reason + " player=" + playerType + " decode=" + (engine == null ? -1 : engine.getDecode()));
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
    }

    private void bindPlaybackTrace() {
        if (spec != null) spec.setPlaybackTraceId(playbackTrace.current());
    }

    private void beginPlaybackProfileAbSession(long nowElapsedMs) {
        PlaybackProfileAbPolicy.EnrollmentResolution enrollment =
                PlaybackProfileAbSetting.getEnrollmentResolution();
        PlaybackProfileAbPolicy.Arm arm = currentPlaybackProfileAbArm();
        playbackProfileAbCoordinator.beginSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.StartConfig(
                        enrollment.active()
                                && playbackProfileAbGateOpen()
                                && arm != null,
                        arm,
                        enrollment.enrollment().deviceDigest(),
                        playbackExperimentCoordinator.generation(),
                        Math.abs(userPlaybackSpeed - 1f) < 0.001f),
                nowElapsedMs);
        PlaybackProfileAbPolicy.EnrollmentResolution lightweightEnrollment =
                PlaybackLightweightAssessmentSetting
                        .getEnrollmentResolution();
        PlaybackProfileAbPolicy.Arm lightweightArm =
                currentPlaybackLightweightAssessmentArm();
        playbackLightweightAssessmentCoordinator.beginSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.StartConfig(
                        lightweightEnrollment.active()
                                && playbackProfileAbGateOpen()
                                && lightweightArm != null,
                        lightweightArm,
                        lightweightEnrollment.enrollment().deviceDigest(),
                        playbackExperimentCoordinator.generation(),
                        Math.abs(userPlaybackSpeed - 1f) < 0.001f),
                nowElapsedMs);
    }

    private void observePlaybackProfileAb(
            PlaybackTelemetry.RuntimeObservation observation,
            long nowElapsedMs) {
        if (!playbackAutoSession.active()) return;
        boolean playbackIntended = false;
        if (player != null) {
            try {
                playbackIntended = player.getPlayWhenReady()
                        && (player.isPlaying()
                        || player.getPlaybackState()
                        == Player.STATE_BUFFERING);
            } catch (Throwable ignored) {
            }
        }
        boolean frameSchedulingExperimentActive = false;
        if (isExo()) {
            try {
                var frameScheduling = PlaybackAnalyticsListener
                        .getFrameSchedulingExperimentSnapshot();
                frameSchedulingExperimentActive = frameScheduling.active()
                        && playbackTrace.current().equals(
                        frameScheduling.traceId());
            } catch (Throwable ignored) {
            }
        }
        playbackProfileAbCoordinator.observe(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.RuntimeInput(
                        PlaybackProfileAbSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackProfileAbArm(),
                        playbackExperimentCoordinator.generation(),
                        playbackAutoContextStore.snapshot(),
                        observation,
                        playbackIntended,
                        frameSchedulingExperimentActive,
                        false),
                nowElapsedMs);
        playbackLightweightAssessmentCoordinator.observe(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.RuntimeInput(
                        PlaybackLightweightAssessmentSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackLightweightAssessmentArm(),
                        playbackExperimentCoordinator.generation(),
                        playbackAutoContextStore.snapshot(),
                        observation,
                        playbackIntended,
                        frameSchedulingExperimentActive,
                        false),
                nowElapsedMs);
    }

    private void finishPlaybackProfileAbSession(
            String reason,
            long nowElapsedMs) {
        playbackProfileAbCoordinator.endSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.EndConfig(
                        PlaybackProfileAbSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackProfileAbArm(),
                        playbackExperimentCoordinator.generation(),
                        reason),
                nowElapsedMs,
                System.currentTimeMillis());
        playbackLightweightAssessmentCoordinator.endSession(
                playbackAutoSession,
                new PlaybackProfileAbCoordinator.EndConfig(
                        PlaybackLightweightAssessmentSetting.isEnrolled()
                                && playbackProfileAbGateOpen(),
                        currentPlaybackLightweightAssessmentArm(),
                        playbackExperimentCoordinator.generation(),
                        reason),
                nowElapsedMs,
                System.currentTimeMillis());
    }

    private PlaybackProfileAbPolicy.Arm currentPlaybackProfileAbArm() {
        return PlaybackProfileAbPolicy.armForProfile(
                PlaybackPerformanceSetting.getProfile(playerType));
    }

    private PlaybackProfileAbPolicy.Arm
    currentPlaybackLightweightAssessmentArm() {
        return PlaybackLightweightAssessmentPolicy.armForProfile(
                PlaybackPerformanceSetting.getProfile(playerType));
    }

    private void invalidatePlaybackProfileAssessments(
            PlaybackProfileAbCoordinator.InvalidationReason reason) {
        playbackProfileAbCoordinator.invalidate(
                playbackAutoSession, reason);
        playbackLightweightAssessmentCoordinator.invalidate(
                playbackAutoSession, reason);
    }

    private boolean playbackProfileAbGateOpen() {
        return PlaybackProfileAbPolicy.gateAllows(
                PlaybackExperimentSetting.getState(),
                playbackAutoKernel(playerType));
    }

    private PlaybackResourceClassifier.Classification currentResourceClassification() {
        PlaybackResourceClassifier.Classification request = PlaybackResourceClassifier.classifyRequest(
                spec == null ? null : spec.getUrl(),
                spec == null ? null : spec.getFormat(),
                spec == null ? null : spec.getFormat());
        PlaybackResourceClassifier.Classification observed =
                engine == null ? null : engine.getResourceClassification();
        return PlaybackResourceClassifier.merge(request, observed);
    }

    private void publishPlaybackAutoContext(boolean acceptDecoder) {
        if (spec == null || engine == null || !playbackAutoSession.active()) return;
        PlaybackResourceClassifier.Classification classification = currentResourceClassification();
        PlaybackRoute.Resolution observedRoute = engine.getEffectivePlaybackRoute();
        if (observedRoute == null || observedRoute.route() == PlaybackRoute.OTHER) observedRoute = spec.getPlaybackRoute();
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Kernel> kernel = PlaybackAutoContext.Fact.forSession(
                playbackAutoKernel(playerType), PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH, now);
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decode = PlaybackAutoContext.Fact.forSession(
                engine.isHard() ? PlaybackAutoContext.DecodeMode.HARDWARE : PlaybackAutoContext.DecodeMode.SOFTWARE,
                PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST, PlaybackAutoContext.Confidence.HIGH, now);
        PlaybackAutoContext.PathFacts path = classification.toPathFacts(observedRoute, now);
        PlaybackAutoContext.ResourceFacts resource = classification.toResourceFacts(now);
        if (!playbackAutoContextStore.publishPlaybackFacts(playbackAutoSession, kernel, decode, resource, path, now)) return;
        try {
            playbackMediaFactsCoordinator.publishEngineFacts(
                    playbackAutoSession,
                    playbackTrackSequence,
                    engine.getPlaybackFactsSnapshot(),
                    acceptDecoder,
                    now);
        } catch (Throwable error) {
            PlaybackTrace.log("playback-auto-context", playbackTrace.current(),
                    "media facts unavailable type=%s action=keep-partial", error.getClass().getSimpleName());
        }
        PlaybackAutoContext snapshot = playbackAutoContextStore.snapshot();
        if (playbackAutoSession.equals(snapshot.session())) {
            PlaybackTrace.log("playback-auto-context", playbackTrace.current(), "%s", snapshot.logSummary());
            PlaybackTrace.log("playback-auto-resource", playbackTrace.current(), "%s", classification.logSummary());
        }
    }

    private void publishPlaybackTelemetryTick() {
        if (!playbackAutoSession.active()) return;
        publishPlaybackTelemetry();
        schedulePlaybackTelemetry();
    }

    private void publishPlaybackTelemetry() {
        publishPlaybackTelemetry(null);
    }

    private void publishPlaybackTelemetry(PlaybackAutoContext.PlaybackPhase phaseOverride) {
        publishPlaybackTelemetry(phaseOverride, true);
    }

    private void publishPlaybackTelemetry(
            PlaybackAutoContext.PlaybackPhase phaseOverride,
            boolean evaluateMpvHlsVariant) {
        if (!playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackTelemetry.RuntimeObservation observation =
                collectPlaybackTelemetry(phaseOverride, now);
        playbackTelemetryCoordinator.publishRuntime(
                playbackAutoSession, observation, now);
        observePlaybackProfileAb(observation, now);
        evaluateIjkRuntimeProfile(observation, now);
        evaluateExoRtspLiveLag(observation, now);
        if (phaseOverride != PlaybackAutoContext.PlaybackPhase.ERROR) {
            evaluateIjkBuffer(IjkBufferController.Trigger.RUNTIME, now);
            evaluateIjkRealtimeRecovery(now);
            evaluateIjkDecodePressure(now);
        }
        if (isMpv()) {
            if (evaluateMpvHlsVariant) {
                evaluateMpvHlsVariant(observation, now);
            }
            evaluateMpvCaches(
                    MpvForwardCacheController.Trigger.RUNTIME,
                    MpvBackCacheController.Trigger.RUNTIME,
                    MpvBackCachePolicy.SeekObservation.none(),
                    now);
        }
    }

    private void evaluateMpvHlsVariant(
            PlaybackTelemetry.RuntimeObservation telemetry,
            long now) {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || !playbackAutoSession.active()) return;
        PlaybackAutoContext context = playbackAutoContextStore.snapshot();
        MpvPlayer.AutoHlsRuntimeSnapshot runtime =
                mpv.getAutoHlsRuntimeSnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.Protocol> protocolFact =
                context.resource().protocol();
        PlaybackAutoContext.Fact<PlaybackAutoContext.StreamKind> streamFact =
                context.resource().streamKind();
        boolean protocolUsable = protocolFact.isUsable(now);
        boolean streamUsable = streamFact.isUsable(now);
        PlaybackTelemetry.Metric<Long> bufferedMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown()
                : telemetry.bufferedDurationMs();
        PlaybackTelemetry.Metric<Long> positionMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown() : telemetry.positionMs();
        PlaybackTelemetry.Metric<Integer> rebufferMetric = telemetry == null
                ? PlaybackTelemetry.Metric.unknown() : telemetry.rebufferCount();
        boolean buffering = player != null
                && player.getPlaybackState() == Player.STATE_BUFFERING;
        MpvHlsVariantController.RuntimeObservation observation =
                new MpvHlsVariantController.RuntimeObservation(
                        PlaybackPerformanceSetting.isAuto(
                                PlayerSetting.MPV,
                                PlaybackPerformanceCatalog.MPV_HLS_BITRATE)
                                && experimentAllowed(
                                PlaybackExperimentPolicy.Action.MPV_HLS_RUNTIME_RELOAD),
                        isMpv(),
                        MpvPerformanceSetting.isPerformancePriority(),
                        protocolUsable ? protocolFact.value()
                                : PlaybackAutoContext.Protocol.UNKNOWN,
                        protocolUsable,
                        streamUsable ? streamFact.value()
                                : PlaybackAutoContext.StreamKind.UNKNOWN,
                        streamUsable,
                        toPolicyVariants(runtime.variants()),
                        toPolicyVariant(runtime.selectedVariant()),
                        runtime.underrun(),
                        runtime.underrunCount(),
                        rebufferMetric.known()
                                ? Math.max(0, rebufferMetric.value())
                                : playbackBufferingTracker.getRebufferCount(),
                        buffering,
                        bufferedMetric.known(),
                        bufferedMetric.known()
                                ? Math.max(0, bufferedMetric.value()) : 0,
                        runtime.rawInputBitsPerSecond(),
                        runtime.rawInputRateUsable(),
                        positionMetric.known()
                                ? Math.max(0, positionMetric.value()) : 0);
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.evaluateRuntime(
                        playbackAutoSession, context.session(), observation, now);
        MpvHlsApplyResult apply = decision.requestsApply()
                ? executeMpvHlsVariantDecision(mpv, decision, now)
                : MpvHlsApplyResult.notRequested();
        MpvHlsVariantController.Snapshot snapshot =
                mpvHlsVariantController.snapshot();
        if (decision.reason() == MpvHlsVariantController.Reason.ROLLBACK_TIMEOUT
                || decision.requestsApply() && !apply.succeeded()) {
            mpvHlsManagedReload = false;
        }
        PlaybackTelemetry.DecisionOutcome outcome =
                decision.policyReason() == MpvHlsVariantPolicy.Reason.NOT_AUTOMATIC
                        || decision.policyReason()
                        == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                        ? PlaybackTelemetry.DecisionOutcome.SUPPRESSED
                        : !decision.requestsApply()
                        ? PlaybackTelemetry.DecisionOutcome.HELD
                        : apply.succeeded()
                        ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                        : PlaybackTelemetry.DecisionOutcome.FAILED;
        String suppression = decision.policyReason()
                == MpvHlsVariantPolicy.Reason.NOT_AUTOMATIC
                ? "not-automatic"
                : decision.policyReason()
                == MpvHlsVariantPolicy.Reason.CONFIG_PRIORITY
                ? "mpv-conf-priority"
                : decision.requestsApply() && !apply.started()
                ? "action-rejected"
                : decision.requestsApply() && !apply.optionAccepted()
                ? "native-apply-failed"
                : decision.requestsApply() && !apply.reloadStarted()
                ? "reload-start-failed"
                : decision.reason().label();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        addNumberInput(inputs, "selected_bps",
                decision.targetBitsPerSecond() > 0
                        ? optionBits(decision.oldOption())
                        : runtime.selectedVariant() == null
                        ? -1 : runtime.selectedVariant().selectionBitsPerSecond(),
                PlaybackAutoContext.ValueSource.MANIFEST,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "target_bps", decision.targetBitsPerSecond(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH);
        addNumberInput(inputs, "raw_input_bps",
                runtime.rawInputRateUsable()
                        ? runtime.rawInputBitsPerSecond() : -1,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.MEDIUM);
        addNumberInput(inputs, "buffered_ms",
                bufferedMetric.known() ? bufferedMetric.value() : -1,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "underrun_count", runtime.underrunCount(),
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "rebuffer_count", observation.rebufferCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", decision.riskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "hard_risk_samples", decision.hardRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "throughput_risk_samples", decision.throughputRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "buffer_risk_samples", decision.bufferRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", snapshot.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(decision.oldOption()),
                        hlsOptionLabel(decision.targetOption()),
                        apply.succeeded()
                                ? hlsOptionLabel(decision.targetOption())
                                : hlsOptionLabel(decision.oldOption()),
                        decision.reason().label(),
                        suppression,
                        inputs),
                now);
    }

    private MpvHlsApplyResult executeMpvHlsVariantDecision(
            MpvPlayerEngine mpv,
            MpvHlsVariantController.Decision decision,
            long now) {
        boolean started = mpvHlsVariantController.beginApply(
                playbackAutoSession, decision, now);
        if (!started) return MpvHlsApplyResult.rejected();
        MpvPlayer.AutoHlsBitrateResult option = mpv.applyAutoHlsBitrate(
                playbackTrace.current(), decision.targetOption());
        boolean reloadStarted = option.accepted()
                && restartMpvHlsVariant(decision);
        boolean succeeded = option.accepted() && reloadStarted;
        if (!succeeded && !TextUtils.isEmpty(decision.oldOption())) {
            mpv.applyAutoHlsBitrate(
                    playbackTrace.current(), decision.oldOption());
        }
        mpvHlsVariantController.completeApply(
                playbackAutoSession,
                decision,
                succeeded,
                option.staged(),
                SystemClock.elapsedRealtime());
        return new MpvHlsApplyResult(
                true, option.accepted(), reloadStarted, succeeded,
                option.label());
    }

    private boolean restartMpvHlsVariant(
            MpvHlsVariantController.Decision decision) {
        if (!decision.reloadsMedia()
                || spec == null
                || TextUtils.isEmpty(spec.getUrl())
                || engine == null
                || player == null) return false;
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        long position = decision.preservesVodPosition()
                ? decision.resumePositionMs() : C.TIME_UNSET;
        try {
            prepareSeq++;
            App.removeCallbacks(runnable);
            mpvHlsManagedReload = true;
            initTrack = false;
            playWhenReady = wasPlayWhenReady;
            PlaybackTrace.log("mpv-hls-variant", playbackTrace.current(),
                    "action=%s stream=%s target=%d resume=%d play=%s",
                    decision.action().label(), decision.streamKind().label(),
                    decision.targetBitsPerSecond(),
                    position == C.TIME_UNSET ? 0 : position,
                    wasPlayWhenReady);
            engine.restart(spec.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            return true;
        } catch (Throwable error) {
            mpvHlsManagedReload = false;
            PlaybackTrace.log("mpv-hls-variant", playbackTrace.current(),
                    "action=%s result=failed errorType=%s",
                    decision.action().label(),
                    error.getClass().getSimpleName());
            return false;
        }
    }

    private void schedulePlaybackTelemetry() {
        App.removeCallbacks(playbackTelemetryRunnable);
        if (!playbackAutoSession.active() || player == null || player.getPlaybackState() == Player.STATE_ENDED) return;
        App.post(playbackTelemetryRunnable, PLAYBACK_TELEMETRY_INTERVAL_MS);
    }

    private void endPlaybackTelemetrySession(String reason) {
        App.removeCallbacks(playbackTelemetryRunnable);
        if (!playbackAutoSession.active()) return;
        long now = SystemClock.elapsedRealtime();
        PlaybackTelemetry.RuntimeObservation observation =
                collectPlaybackTelemetry(null, now);
        observePlaybackProfileAb(observation, now);
        finishPlaybackProfileAbSession(reason, now);
        finishIjkRuntimeProfileSession(observation, now);
        playbackTelemetryCoordinator.endSession(
                playbackAutoSession, reason, observation, now);
        rtspLiveLagController.endSession(playbackAutoSession);
    }

    private PlaybackTelemetry.RuntimeObservation collectPlaybackTelemetry(
            PlaybackAutoContext.PlaybackPhase phaseOverride,
            long now) {
        PlaybackAutoContext.PlaybackPhase phaseValue = phaseOverride == null ? playbackPhaseSnapshot() : phaseOverride;
        PlaybackTelemetry.Metric<PlaybackAutoContext.PlaybackPhase> phase = phaseValue == PlaybackAutoContext.PlaybackPhase.UNKNOWN
                ? PlaybackTelemetry.Metric.unknown()
                : PlaybackTelemetry.Metric.of(phaseValue, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        PlaybackTelemetry.Metric<Boolean> loading = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> position = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> duration = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> buffered = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> bandwidth = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> mediaBitrate = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Float> renderedFrameRate = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> droppedFrames = PlaybackTelemetry.Metric.unknown();
        PlaybackTelemetry.Metric<Long> liveLag = PlaybackTelemetry.Metric.unknown();
        if (player != null) {
            try {
                loading = PlaybackTelemetry.Metric.of(player.isLoading(), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getCurrentPosition();
                if (value >= 0) position = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getDuration();
                if (value >= 0 && value != C.TIME_UNSET) duration = PlaybackTelemetry.Metric.of(value,
                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
            try {
                long value = player.getTotalBufferedDuration();
                if (value >= 0) buffered = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable ignored) {
            }
        }
        if (isExo() && playbackTrace.current().equals(PlaybackAnalyticsListener.getPlaybackTraceId())) {
            PlaybackAnalyticsListener.Snapshot analytics = PlaybackAnalyticsListener.getSnapshot();
            if (analytics.bandwidthEstimate() > 0) {
                bandwidth = PlaybackTelemetry.Metric.of(analytics.bandwidthEstimate(), PlaybackAutoContext.ValueSource.ESTIMATOR,
                        PlaybackAutoContext.Confidence.MEDIUM);
            }
            PlaybackAnalyticsListener.DisplayMediaBitrateEstimate media =
                    PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(getVideoFormat());
            if (media.bitrateBitsPerSecond() > 0) {
                PlaybackAutoContext.ValueSource source = "format".equals(media.source())
                        ? PlaybackAutoContext.ValueSource.PLAYER_CALLBACK : PlaybackAutoContext.ValueSource.ESTIMATOR;
                mediaBitrate = PlaybackTelemetry.Metric.of(media.bitrateBitsPerSecond(), source,
                        telemetryConfidence(media.confidence()));
            }
            PlaybackAnalyticsListener.DisplayFrameRateEstimate frameRate = PlaybackAnalyticsListener.getDisplayFrameRateEstimate();
            if (frameRate.frameRate() > 0 && frameRate.sampleCount() > 0) {
                renderedFrameRate = PlaybackTelemetry.Metric.of(frameRate.frameRate(), PlaybackAutoContext.ValueSource.ESTIMATOR,
                        frameRate.sampleCount() >= 12 ? PlaybackAutoContext.Confidence.HIGH : PlaybackAutoContext.Confidence.MEDIUM);
            }
            if (analytics.everReady() || analytics.videoFormat() != null) {
                droppedFrames = PlaybackTelemetry.Metric.of(Math.max(0, analytics.droppedFrames()),
                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
            }
            try {
                long value = player.getCurrentLiveOffset();
                if (player.isCurrentMediaItemLive() && value >= 0 && value != C.TIME_UNSET) {
                    liveLag = PlaybackTelemetry.Metric.of(value, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                            PlaybackAutoContext.Confidence.HIGH);
                }
            } catch (Throwable ignored) {
            }
        } else if (engine != null) {
            try {
                PlayerEngine.RuntimeMetrics metrics = engine.getRuntimeMetrics();
                if (metrics.bandwidthBitsPerSecond() != null) bandwidth = PlaybackTelemetry.Metric.of(
                        metrics.bandwidthBitsPerSecond(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.mediaBitrateBitsPerSecond() != null) mediaBitrate = PlaybackTelemetry.Metric.of(
                        metrics.mediaBitrateBitsPerSecond(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.renderedFrameRate() != null) renderedFrameRate = PlaybackTelemetry.Metric.of(
                        metrics.renderedFrameRate(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.MEDIUM);
                if (metrics.droppedFrames() != null) droppedFrames = PlaybackTelemetry.Metric.of(
                        metrics.droppedFrames(), PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.HIGH);
            } catch (Throwable error) {
                PlaybackTrace.log("playback-telemetry", playbackTrace.current(),
                        "native metrics unavailable type=%s action=keep-unknown", error.getClass().getSimpleName());
            }
        }
        if (player != null && isIjk()) {
            try {
                long value = player.getCurrentLiveOffset();
                if (player.isCurrentMediaItemLive()
                        && value >= 0 && value != C.TIME_UNSET) {
                    liveLag = PlaybackTelemetry.Metric.of(
                            value,
                            PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                            PlaybackAutoContext.Confidence.HIGH);
                }
            } catch (Throwable ignored) {
            }
            if (!liveLag.known() && engine instanceof IjkPlayerEngine ijk) {
                try {
                    Long value = ijk.getLiveLagLowerBoundMs();
                    if (value != null && value >= 0) {
                        liveLag = PlaybackTelemetry.Metric.of(
                                value,
                                PlaybackAutoContext.ValueSource.PROXY,
                                PlaybackAutoContext.Confidence.MEDIUM);
                    }
                } catch (Throwable error) {
                    PlaybackTrace.log("ijk-buffer", playbackTrace.current(),
                            "live-lag unavailable errorType=%s action=keep-unknown",
                            error.getClass().getSimpleName());
                }
            }
        }
        long firstFrameMs = playbackTrace.stageElapsedMs(PlaybackTrace.Stage.FIRST_FRAME);
        PlaybackTelemetry.Metric<Long> firstFrame = firstFrameMs < 0 ? PlaybackTelemetry.Metric.unknown()
                : PlaybackTelemetry.Metric.of(firstFrameMs, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH);
        logMpvFrameTiming(now);
        return new PlaybackTelemetry.RuntimeObservation(
                phase,
                loading,
                position,
                duration,
                buffered,
                bandwidth,
                mediaBitrate,
                renderedFrameRate,
                droppedFrames,
                PlaybackTelemetry.Metric.of(playbackBufferingTracker.getRebufferCount(),
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                PlaybackTelemetry.Metric.of(playbackBufferingTracker.getRebufferTotalMs(now),
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER, PlaybackAutoContext.Confidence.HIGH),
                firstFrame,
                liveLag);
    }

    private void logMpvFrameTiming(long now) {
        if (!SpiderDebug.isEnabled() || !(engine instanceof MpvPlayerEngine mpv)) return;
        if (now - lastMpvFrameTimingLogMs < MPV_FRAME_TIMING_LOG_INTERVAL_MS) return;
        lastMpvFrameTimingLogMs = now;
        MpvPlayer.FrameTimingSnapshot timing = mpv.getFrameTimingSnapshot();
        PlaybackTrace.log("mpv-frame-timing", playbackTrace.current(),
                "dec=%d out=%d mistimed=%d delayed=%d avSyncMs=%d contentFps=%.3f displayFps=%.3f direct=%s observed=%s",
                timing.decoderDroppedFrames(),
                timing.outputDroppedFrames(),
                timing.mistimedFrames(),
                timing.delayedFrames(),
                Math.round(timing.avSyncSeconds() * 1000.0),
                timing.contentFrameRate(),
                timing.displayFrameRate(),
                mpv.isSurfaceDirect(),
                timing.observed());
    }

    private void evaluateExoRtspLiveLag(
            PlaybackTelemetry.RuntimeObservation observation,
            long nowMs) {
        if (!playbackAutoSession.active()) return;
        boolean automatic = PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO)
                && experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_RTSP_RECOVERY);
        boolean exo = isExo() && engine instanceof ExoPlayerEngine;
        PlaybackResourceClassifier.Classification classification =
                currentResourceClassification();
        boolean rtsp = classification.protocol() == PlaybackAutoContext.Protocol.RTSP;
        boolean classifiedLive = classification.streamKind() == PlaybackAutoContext.StreamKind.LIVE
                || classification.streamKind() == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE;
        boolean mediaItemLive = false;
        boolean active = false;
        boolean loading = false;
        boolean seekAvailable = false;
        if (player != null) {
            try {
                mediaItemLive = player.isCurrentMediaItemLive();
            } catch (Throwable ignored) {
            }
            try {
                int state = player.getPlaybackState();
                active = player.getPlayWhenReady()
                        && (player.isPlaying()
                        || state == Player.STATE_READY
                        || state == Player.STATE_BUFFERING);
            } catch (Throwable ignored) {
            }
            try {
                loading = player.isLoading();
            } catch (Throwable ignored) {
            }
            try {
                seekAvailable = player.isCommandAvailable(
                        Player.COMMAND_SEEK_TO_DEFAULT_POSITION);
            } catch (Throwable ignored) {
            }
        }
        boolean live = classifiedLive && mediaItemLive;
        boolean startupComplete = playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)
                || playbackTrace.hasStage(PlaybackTrace.Stage.AUDIO_PLAYABLE);
        long liveLagMs = metricLong(observation == null ? null : observation.liveLagMs());
        long bufferedMs = metricLong(observation == null ? null : observation.bufferedDurationMs());
        boolean liveEdgeReliable = isReliableDynamicLiveEdge(
                live, seekAvailable, liveLagMs);
        ExoRtspLiveLagPolicy.Decision decision = rtspLiveLagController.evaluate(
                new ExoRtspLiveLagController.Input(
                        playbackAutoSession,
                        automatic,
                        exo,
                        rtsp,
                        live,
                        active,
                        startupComplete,
                        false,
                        loading,
                        liveEdgeReliable,
                        seekAvailable,
                        liveLagMs,
                        bufferedMs,
                        nowMs));
        ExoRtspLiveLagController.Snapshot stateAtDecision =
                rtspLiveLagController.snapshot();

        // Do not fill the shared decision log with inapplicable HLS/DASH/native observations.
        if (!automatic || !exo || !rtsp) return;
        if (!decision.requestsRecovery()) {
            publishExoRtspLiveLagDecision(
                    decision,
                    PlaybackTelemetry.DecisionOutcome.HELD,
                    false,
                    active,
                    loading,
                    seekAvailable,
                    liveEdgeReliable,
                    stateAtDecision,
                    nowMs);
            return;
        }

        boolean started = rtspLiveLagController.beginAction(
                playbackAutoSession, decision.action(), nowMs);
        boolean succeeded = started && executeExoRtspLiveLagRecovery(decision.action());
        if (started) {
            rtspLiveLagController.completeAction(
                    playbackAutoSession, decision.action(), succeeded);
        }
        publishExoRtspLiveLagDecision(
                decision,
                succeeded ? PlaybackTelemetry.DecisionOutcome.APPLIED
                        : PlaybackTelemetry.DecisionOutcome.FAILED,
                succeeded,
                active,
                loading,
                seekAvailable,
                liveEdgeReliable,
                stateAtDecision,
                nowMs);
    }

    private boolean executeExoRtspLiveLagRecovery(
            ExoRtspLiveLagPolicy.Action action) {
        try {
            return switch (action) {
                case SEEK_LIVE_EDGE -> engine instanceof ExoPlayerEngine exo
                        && exo.recoverRtspLiveEdge();
                case REBUILD_SESSION -> restartExoRtspLiveSession();
                case HOLD -> false;
            };
        } catch (Throwable error) {
            PlaybackTrace.log("exo-rtsp-live", playbackTrace.current(),
                    "action=%s result=failed errorType=%s",
                    action.label(), error.getClass().getSimpleName());
            return false;
        }
    }

    private boolean restartExoRtspLiveSession() {
        if (!(engine instanceof ExoPlayerEngine)
                || player == null
                || spec == null
                || TextUtils.isEmpty(spec.getUrl())) {
            return false;
        }
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        prepareSeq++;
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        resetNetworkProtectionSession("rtsp-live-rebuild");
        setDanmakus(spec.getDanmakus());
        initTrack = false;
        waitingLutBeforePlay = false;
        playWhenReady = wasPlayWhenReady;
        applySubtitleStyle();
        PlaybackTrace.log("exo-rtsp-live", playbackTrace.current(),
                "action=rebuild-session play=%s speed=%.3f repeat=%s",
                wasPlayWhenReady, speed, repeat);
        engine.restart(spec.checkUa(), C.TIME_UNSET, wasPlayWhenReady);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
        App.post(runnable, Constant.TIMEOUT_PLAY);
        callback.onPrepare();
        return true;
    }

    private boolean isReliableDynamicLiveEdge(
            boolean live,
            boolean seekAvailable,
            long liveLagMs) {
        if (!live || !seekAvailable || liveLagMs < 0 || player == null) return false;
        try {
            Timeline timeline = player.getCurrentTimeline();
            int index = player.getCurrentMediaItemIndex();
            if (timeline == null || timeline.isEmpty()
                    || index < 0 || index >= timeline.getWindowCount()) {
                return false;
            }
            Timeline.Window window = timeline.getWindow(index, new Timeline.Window());
            return window.isLive() && window.isDynamic;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void publishExoRtspLiveLagDecision(
            ExoRtspLiveLagPolicy.Decision decision,
            PlaybackTelemetry.DecisionOutcome outcome,
            boolean succeeded,
            boolean active,
            boolean loading,
            boolean seekAvailable,
            boolean liveEdgeReliable,
            ExoRtspLiveLagController.Snapshot stateAtDecision,
            long nowMs) {
        ExoRtspLiveLagController.Snapshot snapshot =
                rtspLiveLagController.snapshot();
        ExoRtspLiveLagController.Snapshot decisionState = stateAtDecision == null
                ? snapshot : stateAtDecision;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "trigger", decision.trigger().label(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.liveLagMs() < 0
                ? PlaybackTelemetry.DecisionInput.unknown("live_lag_ms")
                : PlaybackTelemetry.DecisionInput.number(
                "live_lag_ms", decision.liveLagMs(),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.lagGrowthMsPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown("lag_growth_msps")
                : PlaybackTelemetry.DecisionInput.number(
                "lag_growth_msps", decision.lagGrowthMsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(decision.bufferedMs() < 0
                ? PlaybackTelemetry.DecisionInput.unknown("buffered_ms")
                : PlaybackTelemetry.DecisionInput.number(
                "buffered_ms", decision.bufferedMs(),
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(decision.bufferGrowthMsPerSecond() == Long.MIN_VALUE
                ? PlaybackTelemetry.DecisionInput.unknown("buffer_growth_msps")
                : PlaybackTelemetry.DecisionInput.number(
                "buffer_growth_msps", decision.bufferGrowthMsPerSecond(),
                PlaybackAutoContext.ValueSource.ESTIMATOR,
                PlaybackAutoContext.Confidence.MEDIUM));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "active", active,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "loading", loading,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "edge_reliable", liveEdgeReliable,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.bool(
                "seek_available", seekAvailable,
                PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "risk_samples", decisionState.consecutiveRiskSamples(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "recoveries", snapshot.recoveryAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "cooldown_ms", decision.cooldownRemainingMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.RTSP_LIVE_RECOVERY,
                        outcome,
                        decisionState.lastAction().label(),
                        decision.action().label(),
                        succeeded ? decision.action().label() : "hold",
                        decision.reason().label(),
                        decision.requestsRecovery() ? succeeded ? "none" : "action-failed"
                                : decision.reason().label(),
                        inputs),
                nowMs);
    }

    private static long metricLong(PlaybackTelemetry.Metric<Long> metric) {
        return metric == null || !metric.known() || metric.value() < 0
                ? -1 : metric.value();
    }

    private PlaybackAutoContext.PlaybackPhase playbackPhaseSnapshot() {
        if (player == null) return PlaybackAutoContext.PlaybackPhase.IDLE;
        return switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING -> PlaybackAutoContext.PlaybackPhase.BUFFERING;
            case Player.STATE_READY -> PlaybackAutoContext.PlaybackPhase.READY;
            case Player.STATE_ENDED -> PlaybackAutoContext.PlaybackPhase.ENDED;
            case Player.STATE_IDLE -> spec == null ? PlaybackAutoContext.PlaybackPhase.IDLE : PlaybackAutoContext.PlaybackPhase.PREPARING;
            default -> PlaybackAutoContext.PlaybackPhase.UNKNOWN;
        };
    }

    private static PlaybackAutoContext.Confidence telemetryConfidence(String value) {
        if (value == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (value) {
            case "high" -> PlaybackAutoContext.Confidence.HIGH;
            case "medium" -> PlaybackAutoContext.Confidence.MEDIUM;
            case "low" -> PlaybackAutoContext.Confidence.LOW;
            default -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private void clearPlaybackAutoContext() {
        mpvForwardCacheController.endSession(playbackAutoSession);
        mpvBackCacheController.endSession(playbackAutoSession);
        mpvCacheTargetCoordinator.endSession(playbackAutoSession);
        mpvHlsVariantController.endSession(playbackAutoSession);
        mpvResourcePressureController.endSession(playbackAutoSession);
        mpvPreloadController.endSession(playbackAutoSession);
        ijkBufferController.endSession(playbackAutoSession);
        ijkDecodePressureController.endSession(playbackAutoSession);
        ijkRealtimeRecoveryController.endSession(playbackAutoSession);
        ijkRuntimeProfileController.endSession(playbackAutoSession);
        ijkBufferManagedReload = false;
        pendingIjkBufferDecision = null;
        pendingIjkDecodePressureDecision = null;
        pendingIjkRealtimeRecoveryDecision = null;
        mpvHlsManagedReload = false;
        mpvAutoController.endSession(playbackAutoSession);
        PlaybackSystemConditionMonitor.process().endSession(playbackAutoSession);
        PlaybackMemoryMonitor.process().endSession(playbackAutoSession);
        playbackMediaFactsCoordinator.endSession(playbackAutoSession);
        playbackAutoContextStore.clear(playbackAutoSession);
        playbackAutoSession = PlaybackAutoContext.SessionToken.none();
        playbackTrackSequence = 0;
    }

    private static PlaybackAutoContext.Kernel playbackAutoKernel(int playerType) {
        return switch (PlayerSetting.sanitizePlayer(playerType)) {
            case PlayerSetting.IJK -> PlaybackAutoContext.Kernel.IJK;
            case PlayerSetting.MPV -> PlaybackAutoContext.Kernel.MPV;
            default -> PlaybackAutoContext.Kernel.EXO;
        };
    }

    private void logPlaybackRoute() {
        if (spec == null) return;
        String traceId = playbackTrace.current();
        if (traceId.equals(lastLoggedRouteTraceId)) return;
        PlaybackRoute.Resolution resolution = spec.getPlaybackRoute();
        PlaybackTrace.log("playback-route", traceId, "%s", resolution.logSummary());
        lastLoggedRouteTraceId = traceId;
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        return "scheme=" + (TextUtils.isEmpty(scheme) ? "unknown" : scheme)
                + " len=" + url.length();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private void markStartupCompletion(boolean ready, Tracks tracks) {
        if (tracks == null) return;
        boolean hasVideo = tracks.containsType(C.TRACK_TYPE_VIDEO);
        boolean hasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO);
        PlaybackStartupPolicy.Completion completion = PlaybackStartupPolicy.resolve(ready, playerType == PlayerSetting.MPV, hasVideo, hasAudio);
        if (completion == PlaybackStartupPolicy.Completion.FIRST_FRAME) {
            if (playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)) return;
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME, "source=mpv-playback-restart player=" + playerType);
            onIjkRuntimeFirstFrame(SystemClock.elapsedRealtime());
        } else if (completion == PlaybackStartupPolicy.Completion.AUDIO_PLAYABLE) {
            playbackTrace.mark(PlaybackTrace.Stage.AUDIO_PLAYABLE, "source=ready player=" + playerType);
        }
    }

    private void completeMpvDirectFirstFrame(int state) {
        if (!MpvAutoOutputPolicy.canRevealDirectFrame(
                MpvPerformanceSetting.getOutputMode() == MpvPerformanceSetting.OUTPUT_AUTO,
                mpvAutoOutputEvaluated,
                state == Player.STATE_READY,
                isMpvSurfaceDirect(),
                getVideoWidth(),
                getVideoHeight())) return;
        mpvAutoOutputFrameReady = true;
        callback.onPlayerOutputReady();
        if (!playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME)) {
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME,
                    "source=mpv-playback-restart-direct player=" + playerType);
            onIjkRuntimeFirstFrame(SystemClock.elapsedRealtime());
        }
        PlaybackTrace.log("mpv-output", playbackTrace.current(),
                "auto shutter release reason=direct-playback-restart size=%dx%d evaluated=%s",
                getVideoWidth(), getVideoHeight(), mpvAutoOutputEvaluated);
    }

    private void recordBufferingState(int state) {
        if (player == null) return;
        if (isExo()
                && state == Player.STATE_BUFFERING
                && !playbackBufferingTracker.isBuffering()
                && PlaybackAnalyticsListener.isSeekRecoveryActive()) {
            PlaybackTrace.log("playback-buffer", playbackTrace.current(),
                    "event=excluded phase=seek outcome=user-action");
            return;
        }
        if ((mpvHlsManagedReload || ijkBufferManagedReload)
                && state == Player.STATE_BUFFERING
                && !playbackBufferingTracker.isBuffering()) {
            String domain = mpvHlsManagedReload
                    ? "mpv-hls-variant"
                    : pendingIjkDecodePressureDecision != null
                    ? "ijk-decode"
                    : pendingIjkRealtimeRecoveryDecision == null
                    ? "ijk-buffer" : "ijk-realtime";
            PlaybackTrace.log(domain,
                    playbackTrace.current(),
                    "action=managed-reload-buffering result=excluded-from-rebuffer");
            return;
        }
        boolean startupComplete = playbackTrace.hasStage(PlaybackTrace.Stage.FIRST_FRAME) || playbackTrace.hasStage(PlaybackTrace.Stage.AUDIO_PLAYABLE);
        PlaybackBufferingTracker.Event event = playbackBufferingTracker.update(
                state == Player.STATE_BUFFERING,
                startupComplete,
                SystemClock.elapsedRealtime(),
                state,
                currentPositionSnapshot(),
                forwardBufferedSnapshot(),
                loadingSnapshot());
        if (event == null) return;
        PlaybackTrace.log("playback-buffer", playbackTrace.current(),
                "event=%s phase=%s outcome=%s duration=%dms count=%d total=%dms position=%d forward=%d state=%s loading=%s",
                event.type().label(), event.phase().label(), bufferingOutcome(event), event.durationMs(), event.rebufferCount(), event.rebufferTotalMs(),
                event.positionMs(), event.forwardBufferedMs(), stateName(event.playbackState()), event.loading());
    }

    private long currentPositionSnapshot() {
        try {
            return Math.max(0, player.getCurrentPosition());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private long forwardBufferedSnapshot() {
        try {
            return Math.max(0, player.getTotalBufferedDuration());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean loadingSnapshot() {
        try {
            return player.isLoading();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String bufferingOutcome(PlaybackBufferingTracker.Event event) {
        if (event.type() == PlaybackBufferingTracker.Type.START) return "-";
        return switch (event.playbackState()) {
            case Player.STATE_READY -> "ready";
            case Player.STATE_ENDED -> "ended";
            case Player.STATE_IDLE -> "idle";
            default -> "left-buffering";
        };
    }

    private void onMpvHlsPlaybackReady(long now) {
        if (!isMpv() || !playbackAutoSession.active()) return;
        MpvHlsVariantController.Completion completion =
                mpvHlsVariantController.onPlaybackReady(
                        playbackAutoSession, now);
        MpvHlsVariantController.Snapshot snapshot =
                mpvHlsVariantController.snapshot();
        if (snapshot.pendingMode()
                == MpvHlsVariantController.PendingMode.NONE) {
            mpvHlsManagedReload = false;
        }
        if (!completion.changed()) return;
        MpvHlsVariantController.Decision action = snapshot.lastDecision();
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "reload_attempts", snapshot.reloadAttempts(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "successful_downgrades", snapshot.successfulDowngrades(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "rollback_count", snapshot.rollbackCount(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "failed_actions", snapshot.failedActions(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        if (engine instanceof MpvPlayerEngine mpv) {
            MpvPlayer.AutoHlsRuntimeSnapshot runtime =
                    mpv.getAutoHlsRuntimeSnapshot();
            inputs.add(PlaybackTelemetry.DecisionInput.number(
                    "native_readbacks", runtime.observedCount(),
                    PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                    PlaybackAutoContext.Confidence.HIGH));
        }
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        PlaybackTelemetry.DecisionOutcome.APPLIED,
                        hlsOptionLabel(action.oldOption()),
                        hlsOptionLabel(action.targetOption()),
                        hlsOptionLabel(completion.option()),
                        completion.reason().label(),
                        "none",
                        inputs),
                now);
    }

    private boolean recoverMpvHlsVariantError() {
        if (!(engine instanceof MpvPlayerEngine mpv)
                || !playbackAutoSession.active()) return false;
        MpvHlsVariantController.Decision decision =
                mpvHlsVariantController.requestRollbackOnError(
                        playbackAutoSession);
        if (!decision.requestsApply()) {
            if (mpvHlsVariantController.snapshot().pendingMode()
                    == MpvHlsVariantController.PendingMode.ROLLBACK) {
                mpvHlsManagedReload = false;
            }
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        MpvHlsApplyResult apply = executeMpvHlsVariantDecision(
                mpv, decision, now);
        PlaybackTelemetry.DecisionOutcome outcome = apply.succeeded()
                ? PlaybackTelemetry.DecisionOutcome.REQUESTED
                : PlaybackTelemetry.DecisionOutcome.FAILED;
        List<PlaybackTelemetry.DecisionInput> inputs = new ArrayList<>();
        inputs.add(PlaybackTelemetry.DecisionInput.number(
                "resume_position_ms", decision.resumePositionMs(),
                PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                PlaybackAutoContext.Confidence.HIGH));
        inputs.add(PlaybackTelemetry.DecisionInput.text(
                "stream", decision.streamKind().label(),
                PlaybackAutoContext.ValueSource.MANIFEST,
                PlaybackAutoContext.Confidence.HIGH));
        playbackTelemetryCoordinator.publishDecision(
                playbackAutoSession,
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.MPV_HLS_VARIANT,
                        outcome,
                        hlsOptionLabel(decision.oldOption()),
                        hlsOptionLabel(decision.targetOption()),
                        apply.succeeded()
                                ? hlsOptionLabel(decision.targetOption())
                                : hlsOptionLabel(decision.oldOption()),
                        decision.reason().label(),
                        apply.succeeded() ? "none" : "rollback-start-failed",
                        inputs),
                now);
        if (!apply.succeeded()) mpvHlsManagedReload = false;
        return apply.succeeded();
    }

    private static String trackSummary(Tracks tracks) {
        return "video=" + tracks.containsType(C.TRACK_TYPE_VIDEO) +
                " audio=" + tracks.containsType(C.TRACK_TYPE_AUDIO) +
                " text=" + tracks.containsType(C.TRACK_TYPE_TEXT) +
                " groups=" + tracks.getGroups().size();
    }

    private static String causeChain(Throwable error) {
        if (error == null) return "null";
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getName());
            current = current.getCause();
        }
        return builder.toString();
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onReload(String msg);

        default void onPlayerRenderRequired() {
        }

        default void onPlayerOutputPending() {
        }

        default void onPlayerOutputReady() {
        }

        default void onExoFirstFrame() {
        }

        void onPlayerRebuild(Player newPlayer, boolean resetVideoSurface);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            liveDanmakuPlaybackActive = isPlaying;
            if (!isPlaying) discardLiveDanmakuPending();
            if (isPlaying) scheduleNetworkProtection(0);
            else if (player.getPlaybackState() == Player.STATE_READY && !player.getPlayWhenReady()) {
                invalidatePlaybackProfileAssessments(
                        PlaybackProfileAbCoordinator.InvalidationReason.PAUSED);
                resetNetworkProtectionSession("paused");
            } else if (player.getPlaybackState() != Player.STATE_BUFFERING) {
                App.removeCallbacks(networkProtectionRunnable);
                networkProtectionController.disrupt(networkProtectionSpeed);
                networkProtectionTrend.reset();
                networkProtectionState = networkProtectionController.getState();
                networkProtectionTier = networkProtectionController.getTier();
            } else {
                // A rebuffer is exactly the evidence the guard needs after playback resumes.
                // Keep the current protection speed, controller history, and forward-buffer
                // trend instead of forcing another warm-up window on every stall.
                App.removeCallbacks(networkProtectionRunnable);
                networkProtectionReason = "buffering-hold";
            }
            publishPlaybackTelemetry();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "state=%s spec=%s", stateName(state), debugSpec());
            publishPlaybackAutoContext(state != Player.STATE_IDLE);
            if (state == Player.STATE_READY) {
                completeMpvDirectFirstFrame(state);
                ijkRuntimeProfileController.onPrepared(playbackAutoSession);
                onMpvHlsPlaybackReady(SystemClock.elapsedRealtime());
                if (isIjk()) {
                    completeIjkBufferManagedReload(
                            true, "ready", SystemClock.elapsedRealtime(), true);
                }
                playbackTrace.mark(PlaybackTrace.Stage.READY, "player=" + playerType);
                markStartupCompletion(true, getCurrentTracks());
                hardDecodeSwitchRetryArmed = false;
                clearLutWarmupRecovery();
                applyLutForCurrentItem();
                scheduleNetworkProtection(0);
            } else if (state == Player.STATE_BUFFERING) {
                App.removeCallbacks(networkProtectionRunnable);
                // Do not reset/disrupt the network guard here. BUFFERING is transient and
                // clearing its trend makes repeated stalls permanently outrun the 10 s
                // confirmation window. READY schedules an immediate evaluation using the
                // retained pre-stall trend plus the newly recorded rebuffer count.
                networkProtectionReason = "buffering-hold";
            } else {
                resetNetworkProtectionSession(state == Player.STATE_ENDED ? "ended" : "inactive");
            }
            recordBufferingState(state);
            publishPlaybackTelemetry();
            if (state == Player.STATE_ENDED) {
                App.removeCallbacks(playbackTelemetryRunnable);
                finishPlaybackProfileAbSession(
                        "ended", SystemClock.elapsedRealtime());
            } else {
                schedulePlaybackTelemetry();
            }
        }

        @Override
        public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
            if (isExo()) scheduleNetworkProtection(0);
            if (!(engine instanceof IjkPlayerEngine)) return;
            int index = player == null ? C.INDEX_UNSET : player.getCurrentMediaItemIndex();
            if (timeline.isEmpty() || index < 0 || index >= timeline.getWindowCount()) return;
            Timeline.Window window = timeline.getWindow(index, new Timeline.Window());
            if (window.manifest == null) return;
            IjkTimelinePublicationKey key = new IjkTimelinePublicationKey(
                    window.manifest,
                    window.liveConfiguration,
                    window.isDynamic);
            if (key.equals(lastIjkTimelinePublicationKey)) return;
            lastIjkTimelinePublicationKey = key;
            publishPlaybackAutoContext(false);
            evaluateIjkBuffer(IjkBufferController.Trigger.MANIFEST,
                    SystemClock.elapsedRealtime());
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            rtspLiveLagController.onPositionDiscontinuity(playbackAutoSession);
            ijkRealtimeRecoveryController.onPositionDiscontinuity(
                    playbackAutoSession);
            ijkDecodePressureController.onPositionDiscontinuity(
                    playbackAutoSession);
            resetNetworkProtectionSession("discontinuity-" + reason);
            scheduleNetworkProtection(ExoNetworkGuardController.OBSERVE_INTERVAL_MS);
            if (isMpv()) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    mpvPreloadController.disrupt(playbackAutoSession);
                }
                MpvBackCachePolicy.SeekObservation seek = MpvBackCachePolicy.observeSeek(
                        reason == Player.DISCONTINUITY_REASON_SEEK,
                        oldPosition.mediaItemIndex == newPosition.mediaItemIndex,
                        oldPosition.positionMs,
                        newPosition.positionMs);
                evaluateMpvCaches(
                        null,
                        MpvBackCacheController.Trigger.SEEK,
                        seek,
                        SystemClock.elapsedRealtime());
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
            publishPlaybackAutoContext(true);
            publishPlaybackTelemetry();
            applyLutForCurrentItem();
            scheduleMpvAutoOutputEvaluation();
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (playbackTrackSequence < Long.MAX_VALUE) playbackTrackSequence++;
            publishPlaybackAutoContext(false);
            if (isExo()) scheduleNetworkProtection(0);
            if (!tracks.isEmpty() && !initTrack) {
                playbackTrace.mark(PlaybackTrace.Stage.TRACKS, trackSummary(tracks));
                restoreTrackSelection(Track.find(getKey()));
                if (engine instanceof MpvPlayerEngine mpv) {
                    mpv.completeInitialSubtitleTrackRestore();
                }
                callback.onTracksChanged();
                initTrack = true;
            }
            markStartupCompletion(player != null && player.getPlaybackState() == Player.STATE_READY, tracks);
            publishPlaybackTelemetry();
            applyLutForCurrentItem();
            scheduleMpvAutoOutputEvaluation();
        }

        @Override
        public void onRenderedFirstFrame() {
            // A rendered video frame proves that the media and video decoder are
            // working even if a slow audio track keeps Exo in BUFFERING briefly.
            // Do not let the generic startup timer turn that valid playback into
            // a false connection-timeout error.
            if (isExo()) App.removeCallbacks(runnable);
            playbackTrace.mark(PlaybackTrace.Stage.FIRST_FRAME, "source=media3 player=" + playerType);
            publishPlaybackAutoContext(true);
            onIjkRuntimeFirstFrame(SystemClock.elapsedRealtime());
            publishPlaybackTelemetry();
            if (isExo()) callback.onExoFirstFrame();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            App.removeCallbacks(networkProtectionRunnable);
            if (handleExoDecoderResourcesReclaimed(e)) return;
            completeIjkBufferManagedReload(
                    false, "playback-error",
                    SystemClock.elapsedRealtime(), true);
            rtspLiveLagController.onPlaybackError(playbackAutoSession);
            ijkRealtimeRecoveryController.onPlaybackError(
                    playbackAutoSession);
            ijkDecodePressureController.onPlaybackError(
                    playbackAutoSession);
            // Publish the failing runtime snapshot without letting the periodic
            // HLS timeout path start a rollback before this concrete error is
            // classified. A downgrade error gets exactly one rollback attempt;
            // an error from that rollback continues through the normal handler.
            publishPlaybackTelemetry(
                    PlaybackAutoContext.PlaybackPhase.ERROR, false);
            if (recoverMpvHlsVariantError()) return;
            if (retryMpvDv7P81Failure(e)) return;
            if (retryMpvSurfaceDirectFailure(e)) return;
            if (retryMpvVulkanBackendFailure(e)) return;
            if (retryMpvAutoVulkanFailure(e)) return;
            PlaybackErrorClassifier.Failure failure = PlaybackErrorClassifier.classify(e, getEffectivePlaybackRoute());
            PlayerEngine.ErrorAction action = engine.handleError(e);
            PlaybackTrace.log("playback-error", playbackTrace.current(), "%s action=%s player=%d decode=%d", failure.logSummary(), action, playerType, engine.getDecode());
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "error code=%d errorType=%s action=%s retry=%d causeTypes=%s", e.errorCode, e.getClass().getSimpleName(), action, retry, causeChain(e));
            LocalProxyDebug.dumpIfLocalFailure(spec == null ? null : spec.getUrl(), e);
            if (retryLutFailure(e)) return;
            if (retryLutWarmupByRefresh(action, e)) return;
            boolean decoderRuntimeObserved = action == PlayerEngine.ErrorAction.DECODE
                    && engine instanceof ExoPlayerEngine exo
                    && exo.observeDecoderRuntimeFailure(e);
            if (action == PlayerEngine.ErrorAction.DECODE && retryExoTunnelingFailure(e)) return;
            if (decoderRuntimeObserved && retryExoDecoderRuntimeFailure(e)) return;
            if (action == PlayerEngine.ErrorAction.DECODE && retryHardDecodeSwitch(e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryLocalProxy(e)) return;
            if (retryIjkRuntimeProfileFallback(e, failure, action)) return;
            if (action == PlayerEngine.ErrorAction.RELOAD) {
                finishPlaybackProfileAbSession(
                        "player-error", SystemClock.elapsedRealtime());
                callback.onReload(getPlaybackErrorMessage(failure));
                return;
            }
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                if (spec != null) setDanmakus(spec.getDanmakus());
                return;
            }
            finishPlaybackProfileAbSession(
                    "player-error", SystemClock.elapsedRealtime());
            callback.onError(getPlaybackErrorMessage(failure));
        }
    };

    private record IjkTimelinePublicationKey(
            Object manifest,
            MediaItem.LiveConfiguration liveConfiguration,
            boolean dynamic) {
    }

    private record ExoDecoderResourceRecovery(
            PlaySpec target,
            long positionMs,
            float speed,
            boolean repeat,
            boolean playWhenReady,
            long textOffsetMs,
            long audioOffsetMs) {
    }

    private PlaybackRoute.Resolution getEffectivePlaybackRoute() {
        PlaybackRoute.Resolution route = engine == null ? null : engine.getEffectivePlaybackRoute();
        if (route != null && route.route() != PlaybackRoute.OTHER) return route;
        return spec == null ? PlaybackRoute.resolve(null) : spec.getPlaybackRoute();
    }

    private String getPlaybackErrorMessage(PlaybackErrorClassifier.Failure failure) {
        return switch (failure.stage()) {
            case LOCAL_ENDPOINT -> switch (failure.route().owner()) {
                case APP_MAIN_SERVER, APP_HLS_PROXY -> ResUtil.getString(R.string.error_play_stage_app_local);
                default -> ResUtil.getString(R.string.error_play_stage_external_local);
            };
            case NETWORK_IO -> PlaybackRouteCapabilities.resolve(failure.route()).externalUpstreamOpaque()
                    ? ResUtil.getString(R.string.error_play_stage_external_supply)
                    : ResUtil.getString(R.string.error_play_stage_network);
            case MEDIA_PARSING -> ResUtil.getString(R.string.error_play_stage_media);
            case DECODER -> ResUtil.getString(R.string.error_play_stage_decoder);
            case OUTPUT -> isAudioOutputFailure(failure)
                    ? ResUtil.getString(R.string.error_play_stage_audio_output)
                    : isVideoOutputFailure(failure)
                    ? ResUtil.getString(R.string.error_play_stage_video_output)
                    : ResUtil.getString(R.string.error_play_stage_output);
            case DRM -> ResUtil.getString(R.string.error_play_stage_drm);
            case UNKNOWN -> ResUtil.getString(R.string.error_play_stage_unknown);
        };
    }

    private boolean isAudioOutputFailure(PlaybackErrorClassifier.Failure failure) {
        return hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED);
    }

    private boolean isVideoOutputFailure(PlaybackErrorClassifier.Failure failure) {
        return hasErrorCode(failure, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED)
                || hasErrorCode(failure, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
                || "mpv-video-output-marker".equals(failure.evidence());
    }

    private boolean hasErrorCode(
            PlaybackErrorClassifier.Failure failure, int errorCode) {
        return PlaybackException.getErrorCodeName(errorCode).equals(failure.errorCode());
    }

    private boolean handleExoDecoderResourcesReclaimed(
            PlaybackException error) {
        if (error.errorCode
                != PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED) {
            return false;
        }
        if (pendingExoDecoderResourceRecovery != null
                || exoDecoderResourceRecoveryInProgress) {
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=suppress-duplicate pending=%s inProgress=%s",
                    pendingExoDecoderResourceRecovery != null,
                    exoDecoderResourceRecoveryInProgress);
            return true;
        }
        ExoDecoderResourceRecovery recovery =
                captureExoDecoderResourceRecovery();
        ExoDecoderResourceRecoveryLimiter.Action action =
                exoDecoderResourceRecoveryLimiter.request(
                        error.errorCode,
                        engine instanceof ExoPlayerEngine,
                        engine != null && player != null,
                        recovery != null,
                        playbackForeground);
        if (action
                == ExoDecoderResourceRecoveryLimiter.Action.DEFER_UNTIL_FOREGROUND) {
            hardDecodeSwitchRetryArmed = false;
            pendingExoDecoderResourceRecovery = recovery;
            App.removeCallbacks(playbackTelemetryRunnable);
            resetNetworkProtectionSession("exo-resource-reclaimed-deferred");
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=defer position=%d play=%s",
                    recovery.positionMs(),
                    recovery.playWhenReady());
            return true;
        }
        if (action
                != ExoDecoderResourceRecoveryLimiter.Action.RECOVER_NOW) {
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=pass-through reason=%s",
                    action);
            return false;
        }
        hardDecodeSwitchRetryArmed = false;
        scheduleExoDecoderResourceRecovery(recovery, "foreground-error");
        return true;
    }

    @Nullable
    private ExoDecoderResourceRecovery captureExoDecoderResourceRecovery() {
        if (!(engine instanceof ExoPlayerEngine)
                || player == null
                || spec == null
                || spec.getUrl() == null) {
            return null;
        }
        return new ExoDecoderResourceRecovery(
                spec,
                Math.max(0, getPosition()),
                getSpeed(),
                isRepeatOne(),
                player.getPlayWhenReady(),
                getTextOffsetMs(),
                getAudioOffsetMs());
    }

    private void scheduleExoDecoderResourceRecovery(
            ExoDecoderResourceRecovery recovery,
            String reason) {
        if (recovery == null) return;
        if (!playbackForeground) {
            pendingExoDecoderResourceRecovery = recovery;
            PlaybackTrace.log(
                    "exo-decoder-resource",
                    playbackTrace.current(),
                    "action=defer-before-rebuild reason=%s",
                    reason);
            return;
        }
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec != recovery.target()) {
            return;
        }
        exoDecoderResourceRecoveryInProgress = true;
        int seq = ++prepareSeq;
        App.removeCallbacks(runnable);
        App.removeCallbacks(networkProtectionRunnable);
        App.removeCallbacks(playbackTelemetryRunnable);
        resetNetworkProtectionSession("exo-resource-reclaimed");
        rebuildPlayer(true);
        this.playWhenReady = recovery.playWhenReady();
        initTrack = false;
        PlaybackTrace.log(
                "exo-decoder-resource",
                playbackTrace.current(),
                "action=rebuild delay=%d position=%d play=%s reason=%s",
                EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS,
                recovery.positionMs(),
                recovery.playWhenReady(),
                reason);
        App.post(() -> {
            if (seq != prepareSeq
                    || spec != recovery.target()
                    || engine != exo
                    || player == null) {
                exoDecoderResourceRecoveryInProgress = false;
                return;
            }
            if (!playbackForeground) {
                pendingExoDecoderResourceRecovery = recovery;
                exoDecoderResourceRecoveryInProgress = false;
                PlaybackTrace.log(
                        "exo-decoder-resource",
                        playbackTrace.current(),
                        "action=defer-before-prepare");
                return;
            }
            try {
                setDanmakus(recovery.target().getDanmakus());
                waitingLutBeforePlay = false;
                applySubtitleStyle();
                engine.start(
                        recovery.target().checkUa(),
                        recovery.positionMs(),
                        recovery.playWhenReady());
                setSpeed(recovery.speed());
                setRepeatOne(recovery.repeat());
                setTextOffsetMs(recovery.textOffsetMs());
                setAudioOffsetMs(recovery.audioOffsetMs());
                App.post(runnable, Constant.TIMEOUT_PLAY);
                callback.onPrepare();
                PlaybackTrace.log(
                        "exo-decoder-resource",
                        playbackTrace.current(),
                        "action=prepare position=%d play=%s",
                        recovery.positionMs(),
                        recovery.playWhenReady());
            } finally {
                exoDecoderResourceRecoveryInProgress = false;
            }
        }, EXO_DECODER_RESOURCE_RECOVERY_DELAY_MS);
    }

    private void clearExoDecoderResourceRecovery(boolean resetBudget) {
        pendingExoDecoderResourceRecovery = null;
        exoDecoderResourceRecoveryInProgress = false;
        if (resetBudget) exoDecoderResourceRecoveryLimiter.reset();
    }

    private boolean retryHardDecodeSwitch(PlaybackException e) {
        if (!hardDecodeSwitchRetryArmed || engine == null || player == null || spec == null || !engine.isHard()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) return false;
        hardDecodeSwitchRetryArmed = false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        App.removeCallbacks(runnable);
        resetLutRuntimeState("hard_decode_switch_retry", true);
        engine.release();
        engine = buildEngine(playerType, PlayerEngine.HARD);
        player = engine.getPlayer();
        restoreIjkStagedBufferConfig();
        callback.onPlayerRebuild(player, true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry delay=%d position=%d errorType=%s", HARD_DECODE_SWITCH_RETRY_DELAY_MS, position, e.getClass().getSimpleName());
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine == null || player == null || !engine.isHard()) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "hard decode switch retry start position=%d", position);
            setDanmakus(target.getDanmakus());
            initTrack = false;
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            scheduleMpvAutoOutputEvaluation();
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, HARD_DECODE_SWITCH_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoTunnelingFailure(PlaybackException e) {
        if (!(engine instanceof ExoPlayerEngine exo) || player == null || spec == null) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED && e.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) return false;
        if (!exo.disableTunnelingForSession()) return false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-tunnel", "fallback scheduled delay=%d position=%d errorType=%s", EXO_TUNNELING_RETRY_DELAY_MS, position, e.getClass().getSimpleName());
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("exo-tunnel", "fallback start position=%d", position);
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, EXO_TUNNELING_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoDecoderRuntimeFailure(PlaybackException e) {
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec == null) {
            return false;
        }
        boolean dolbyVisionFallback =
                exo.isDolbyVisionP81RuntimeFailurePending();
        if (!dolbyVisionFallback
                && !experimentAllowed(
                PlaybackExperimentPolicy.Action.EXO_DECODER_RUNTIME_REBUILD)) {
            return false;
        }
        if (!exo.prepareDecoderRuntimeFallback()) {
            return false;
        }
        hardDecodeSwitchRetryArmed = false;
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, getPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log(
                    "exo-decoder-profile",
                    "action=retry-scheduled delay=%d position=%d errorType=%s",
                    EXO_DECODER_RUNTIME_RETRY_DELAY_MS,
                    position,
                    e.getClass().getSimpleName());
        }
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "exo-decoder-profile",
                        "action=retry-start position=%d",
                        position);
            }
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
        }, EXO_DECODER_RUNTIME_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryExoDv7FirstFrameTimeout() {
        if (!(engine instanceof ExoPlayerEngine exo)
                || player == null
                || spec == null
                || !exo.prepareDv7Hdr10FallbackForFirstFrameTimeout()) {
            return false;
        }
        int seq = ++prepareSeq;
        PlaySpec target = spec;
        long position = Math.max(0, player.getCurrentPosition());
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        boolean wasPlayWhenReady = player.getPlayWhenReady();
        App.removeCallbacks(runnable);
        rebuildPlayer(true);
        this.playWhenReady = wasPlayWhenReady;
        initTrack = false;
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log(
                    "exo-dv",
                    "action=first-frame-timeout-fallback-scheduled delay=%d position=%d",
                    EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS,
                    position);
        }
        App.post(() -> {
            if (seq != prepareSeq || spec != target || engine != exo || player == null) return;
            setDanmakus(target.getDanmakus());
            waitingLutBeforePlay = false;
            applySubtitleStyle();
            engine.start(target.checkUa(), position, wasPlayWhenReady);
            if (speed != 1f) setSpeed(speed);
            setRepeatOne(repeat);
            App.post(runnable, Constant.TIMEOUT_PLAY);
            callback.onPrepare();
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log(
                        "exo-dv",
                        "action=first-frame-timeout-fallback-start position=%d",
                        position);
            }
        }, EXO_DV7_FIRST_FRAME_FALLBACK_DELAY_MS);
        return true;
    }

    private boolean retryLutFailure(PlaybackException e) {
        if (!LutSetting.isEnabled()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED && e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED) return false;
        App.removeCallbacks(runnable);
        LutSetting.select(null);
        lutAppliedForItem = true;
        lutApplyInProgress = false;
        pendingLutPreview = false;
        lutWarmupReloadPreviewPending = false;
        clearVideoEffects("lut_error_retry");
        Notify.show(R.string.lut_apply_failed);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "disable and retry after frame processing error code=%d spec=%s cause=%s", e.errorCode, debugSpec(), causeChain(e));
        if (spec != null) setMediaItem();
        return true;
    }

    private boolean retryLocalProxy(PlaybackException e) {
        if (spec == null || !LocalProxyDebug.isLocalProxyUrl(spec.getUrl())) return false;
        if (!LocalProxyDebug.isConnectionRefused(e)) return false;
        if (++localProxyRetry > LOCAL_PROXY_MAX_RETRY) return false;
        int attempt = localProxyRetry;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry schedule attempt=%d delay=%d spec=%s", attempt, LOCAL_PROXY_RETRY_DELAY_MS, debugSpec());
        App.removeCallbacks(runnable);
        App.post(() -> {
            if (spec == null || attempt != localProxyRetry) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry start attempt=%d spec=%s", attempt, debugSpec());
            setMediaItem();
        }, LOCAL_PROXY_RETRY_DELAY_MS);
        return true;
    }

}
