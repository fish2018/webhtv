package com.fongmi.android.tv.player.exo;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackTelemetry;
import com.fongmi.android.tv.player.PlaybackTelemetryCoordinator;
import com.fongmi.android.tv.setting.ExoPerformanceSetting;

import java.util.List;

final class AutoLoadControl implements LoadControl {

    private final AutoTargetLoadControl delegate;
    private final int fallbackStreamingStartBufferMs;
    private final ExoPlaybackThresholdCoordinator thresholdCoordinator;
    private final boolean automaticStartBuffer;
    private final boolean automaticRebuffer;

    AutoLoadControl(
            AutoTargetLoadControl delegate,
            ExoLoadControlPolicy.AutomaticConfiguration configuration) {
        this(
                delegate,
                configuration,
                ExoPlaybackThresholdCoordinator.process(),
                true,
                true);
    }

    AutoLoadControl(
            AutoTargetLoadControl delegate,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            boolean automaticStartBuffer,
            boolean automaticRebuffer) {
        this(
                delegate,
                configuration,
                ExoPlaybackThresholdCoordinator.process(),
                automaticStartBuffer,
                automaticRebuffer);
    }

    AutoLoadControl(
            AutoTargetLoadControl delegate,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            ExoPlaybackThresholdCoordinator thresholdCoordinator) {
        this(delegate, configuration, thresholdCoordinator, true, true);
    }

    AutoLoadControl(
            AutoTargetLoadControl delegate,
            ExoLoadControlPolicy.AutomaticConfiguration configuration,
            ExoPlaybackThresholdCoordinator thresholdCoordinator,
            boolean automaticStartBuffer,
            boolean automaticRebuffer) {
        this.delegate = delegate;
        this.fallbackStreamingStartBufferMs = configuration.streamingStartBufferMs();
        this.thresholdCoordinator = thresholdCoordinator;
        this.automaticStartBuffer = automaticStartBuffer;
        this.automaticRebuffer = automaticRebuffer;
    }

    @Override
    public void onPrepared(PlayerId playerId) {
        delegate.onPrepared(playerId);
    }

    @Override
    public void onTracksSelected(Parameters parameters, TrackGroupArray trackGroups, ExoTrackSelection[] trackSelections) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections);
    }

    @Override
    public void onStopped(PlayerId playerId) {
        delegate.onStopped(playerId);
    }

    @Override
    public void onReleased(PlayerId playerId) {
        delegate.onReleased(playerId);
    }

    @Override
    public Allocator getAllocator(PlayerId playerId) {
        return delegate.getAllocator(playerId);
    }

    @Override
    public long getBackBufferDurationUs(PlayerId playerId) {
        return effectiveBackBufferDurationUs(
                delegate.getBackBufferDurationUs(playerId),
                delegate.isBackBufferSuppressed(playerId));
    }

    @Override
    public boolean retainBackBufferFromKeyframe(PlayerId playerId) {
        return !delegate.isBackBufferSuppressed(playerId)
                && delegate.retainBackBufferFromKeyframe(playerId);
    }

    @Override
    public boolean shouldContinueLoading(Parameters parameters) {
        return delegate.shouldContinueLoading(parameters);
    }

    @Override
    public boolean shouldStartPlayback(Parameters parameters) {
        boolean delegateReady = delegate.shouldStartPlayback(parameters);
        if (PlayerId.PRELOAD.equals(parameters.playerId)) return delegateReady;
        if (parameters.rebuffering ? !automaticRebuffer : !automaticStartBuffer) {
            return delegateReady;
        }
        ExoLoadControlModePolicy.Decision mode = delegate.currentModeDecision(parameters.playerId);
        long now = SystemClock.elapsedRealtime();
        ExoPlaybackThresholdPolicy.Inputs inputs =
                ExoPlaybackThresholdCoordinator.captureInputs(
                        ExoPerformanceSetting.getEffectiveStartBufferMs(),
                        ExoPerformanceSetting.getEffectiveRebufferMs(),
                        mediaDurationMs(parameters.bufferedDurationUs),
                        mediaDurationMs(parameters.targetLiveOffsetUs),
                        parameters.rebuffering,
                        now);
        ExoPlaybackThresholdCoordinator.Episode episode = playbackEpisode(
                parameters.rebuffering,
                thresholdCoordinator.isSeekPending(inputs.session(), now));
        ExoPlaybackThresholdCoordinator.Selection selection =
                thresholdCoordinator.lockEpisode(episode, inputs);
        if (!selection.session().active()) {
            return legacyShouldStartPlayback(delegateReady, parameters, mode);
        }
        if (selection.newlyLocked()) {
            ExoPlaybackDiagnostics.logPlaybackThreshold(selection);
            publishThresholdTelemetry(selection, inputs, now);
        }
        if (mode.mode() == ExoLoadControlModePolicy.Mode.LOCAL_TIME
                || selection.policy().reason()
                == ExoPlaybackThresholdPolicy.Reason.LOCAL_RESOURCE) {
            return delegateReady;
        }

        int thresholdMs = effectiveDynamicThresholdMs(
                selection.thresholdMs(),
                parameters.rebuffering,
                mode.appProxyVodFallback(),
                parameters.playbackSpeed,
                selection.policy().riskLevel());
        boolean adaptiveReady = reachedAdaptiveThreshold(
                parameters.bufferedDurationUs,
                parameters.playbackSpeed,
                parameters.targetLiveOffsetUs,
                thresholdMs);
        boolean targetSizeReady = delegate.isTargetBufferSizeReached(
                parameters.playerId);
        if (mode.mode().controlledTimePriority()) {
            int controlledThresholdMs = controlledTimeThresholdMs(thresholdMs);
            boolean controlledReady = reachedAdaptiveThreshold(
                    parameters.bufferedDurationUs,
                    parameters.playbackSpeed,
                    parameters.targetLiveOffsetUs,
                    controlledThresholdMs);
            return shouldStartControlledPlayback(
                    controlledReady,
                    delegate.canContinueControlledRescue(parameters.playerId),
                    targetSizeReady,
                    adaptiveReady);
        }
        return shouldStartDynamicPlayback(
                parameters.rebuffering,
                mode.appProxyVodFallback(),
                targetSizeReady,
                adaptiveReady);
    }

    @Override
    public boolean shouldContinuePreloading(PlayerId playerId, Timeline timeline, MediaSource.MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
        return shouldContinuePreloading(
                delegate.shouldContinuePreloading(
                        playerId, timeline, mediaPeriodId, bufferedDurationUs),
                delegate.isPreloadPaused());
    }

    static boolean reachedAdaptiveThreshold(long bufferedDurationUs, float playbackSpeed, long targetLiveOffsetUs, int rebufferMs) {
        long requiredUs = rebufferMs * 1_000L;
        if (targetLiveOffsetUs != C.TIME_UNSET) requiredUs = Math.min(requiredUs, targetLiveOffsetUs / 2);
        long playoutBufferedUs = Util.getPlayoutDurationForMediaDuration(bufferedDurationUs, playbackSpeed);
        return playoutBufferedUs >= requiredUs;
    }

    static ExoPlaybackThresholdCoordinator.Episode playbackEpisode(
            boolean rebuffering,
            boolean seekPending) {
        if (rebuffering) return ExoPlaybackThresholdCoordinator.Episode.REBUFFER;
        if (seekPending) return ExoPlaybackThresholdCoordinator.Episode.SEEK;
        return ExoPlaybackThresholdCoordinator.Episode.STARTUP;
    }

    static int controlledTimeThresholdMs(int configuredThresholdMs) {
        return Math.min(
                Math.max(0, configuredThresholdMs),
                ExoLoadControlModePolicy.SINGLE_TRACK_RESCUE_BUFFER_MS);
    }

    static boolean shouldStartControlledPlayback(
            boolean controlledReady,
            boolean rescueCanContinue,
            boolean targetSizeReady,
            boolean adaptiveReady) {
        if (rescueCanContinue) return controlledReady;
        return shouldStartDynamicPlayback(targetSizeReady, adaptiveReady);
    }

    static boolean shouldStartDynamicPlayback(
            boolean targetSizeReady,
            boolean adaptiveReady) {
        return targetSizeReady || adaptiveReady;
    }

    static boolean shouldStartDynamicPlayback(
            boolean rebuffering,
            boolean appProxyVodFallback,
            boolean targetSizeReady,
            boolean adaptiveReady) {
        if (rebuffering && appProxyVodFallback) return adaptiveReady;
        return shouldStartDynamicPlayback(targetSizeReady, adaptiveReady);
    }

    static int effectiveDynamicThresholdMs(
            int selectedThresholdMs,
            boolean rebuffering,
            boolean appProxyVodFallback,
            float playbackSpeed,
            ExoPlaybackThresholdPolicy.RiskLevel riskLevel) {
        int selected = Math.max(0, selectedThresholdMs);
        if (!rebuffering || !appProxyVodFallback) return selected;
        ExoPlaybackThresholdPolicy.RiskLevel risk = riskLevel == null
                ? ExoPlaybackThresholdPolicy.RiskLevel.NONE : riskLevel;
        boolean minimumProtectionStillFailing =
                Math.abs(playbackSpeed - ExoNetworkProtectionPolicy.AUTO_MIN_SPEED) <= 0.005f
                        && risk == ExoPlaybackThresholdPolicy.RiskLevel.CRITICAL;
        return minimumProtectionStillFailing
                ? selected
                : Math.min(selected, ExoLoadControlModePolicy.SINGLE_TRACK_RESCUE_BUFFER_MS);
    }

    static long effectiveBackBufferDurationUs(
            long configuredDurationUs,
            boolean suppressed) {
        return suppressed ? 0 : Math.max(0, configuredDurationUs);
    }

    static boolean shouldContinuePreloading(
            boolean delegateAllowed,
            boolean memoryPaused) {
        return delegateAllowed && !memoryPaused;
    }

    private boolean legacyShouldStartPlayback(
            boolean delegateReady,
            Parameters parameters,
            ExoLoadControlModePolicy.Decision mode) {
        if (mode.mode().controlledTimePriority()) {
            int configuredThresholdMs = parameters.rebuffering
                    ? ExoPerformanceSetting.getEffectiveRebufferMs()
                    : fallbackStreamingStartBufferMs;
            int controlledThresholdMs = controlledTimeThresholdMs(
                    configuredThresholdMs);
            boolean controlledReady = reachedAdaptiveThreshold(
                    parameters.bufferedDurationUs,
                    parameters.playbackSpeed,
                    parameters.targetLiveOffsetUs,
                    controlledThresholdMs);
            boolean adaptiveReady = parameters.rebuffering
                    && reachedAdaptiveThreshold(
                    parameters.bufferedDurationUs,
                    parameters.playbackSpeed,
                    parameters.targetLiveOffsetUs,
                    ExoPerformanceSetting.getEffectiveRebufferMs());
            if (controlledReady
                    || delegate.canContinueControlledRescue(parameters.playerId)) {
                return controlledReady;
            }
            if (!parameters.rebuffering || delegateReady) return delegateReady;
            return adaptiveReady;
        }
        if (!parameters.rebuffering || delegateReady) return delegateReady;
        return reachedAdaptiveThreshold(
                parameters.bufferedDurationUs,
                parameters.playbackSpeed,
                parameters.targetLiveOffsetUs,
                ExoPerformanceSetting.getEffectiveRebufferMs());
    }

    private static long mediaDurationMs(long durationUs) {
        return durationUs == C.TIME_UNSET
                ? -1 : Math.max(0, durationUs / 1_000L);
    }

    private static void publishThresholdTelemetry(
            ExoPlaybackThresholdCoordinator.Selection selection,
            ExoPlaybackThresholdPolicy.Inputs inputs,
            long now) {
        ExoPlaybackThresholdPolicy.Decision policy = selection.policy();
        PlaybackTelemetryCoordinator.process().publishDecision(
                selection.session(),
                new PlaybackTelemetry.DecisionEvent(
                        PlaybackTelemetry.DecisionDomain.LOAD_CONTROL,
                        PlaybackTelemetry.DecisionOutcome.APPLIED,
                        selection.episode() == ExoPlaybackThresholdCoordinator.Episode.REBUFFER
                                ? Integer.toString(inputs.configuredRebufferMs())
                                : selection.episode() == ExoPlaybackThresholdCoordinator.Episode.SEEK
                                ? Integer.toString(ExoPlaybackThresholdCoordinator.SEEK_START_BUFFER_MS)
                                : Integer.toString(inputs.configuredStartBufferMs()),
                        Integer.toString(selection.thresholdMs()),
                        Integer.toString(selection.thresholdMs()),
                        policy.reason().label(),
                        "none",
                        List.of(
                                PlaybackTelemetry.DecisionInput.text(
                                        "episode",
                                        selection.episode().label(),
                                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.text(
                                        "protocol",
                                        policy.protocol().label(),
                                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.MEDIUM),
                                PlaybackTelemetry.DecisionInput.text(
                                        "stream_kind",
                                        policy.streamKind().label(),
                                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.MEDIUM),
                                PlaybackTelemetry.DecisionInput.number(
                                        "start_ms",
                                        selection.startBufferMs(),
                                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number(
                                        "rebuffer_ms",
                                        selection.rebufferMs(),
                                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                                        PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number(
                                        "boundary_ms",
                                        policy.boundaryMs(),
                                        PlaybackAutoContext.ValueSource.MANIFEST,
                                        policy.boundaryMs() > 0
                                                ? PlaybackAutoContext.Confidence.MEDIUM
                                                : PlaybackAutoContext.Confidence.UNKNOWN),
                                PlaybackTelemetry.DecisionInput.number(
                                        "throughput_ratio_permille",
                                        policy.throughputRatioPermille(),
                                        PlaybackAutoContext.ValueSource.ESTIMATOR,
                                        policy.throughputUsable()
                                                ? PlaybackAutoContext.Confidence.MEDIUM
                                                : PlaybackAutoContext.Confidence.UNKNOWN),
                                PlaybackTelemetry.DecisionInput.number(
                                        "prediction_error_permille",
                                        policy.predictionErrorPermille(),
                                        PlaybackAutoContext.ValueSource.ESTIMATOR,
                                        policy.throughputUsable()
                                                ? PlaybackAutoContext.Confidence.MEDIUM
                                                : PlaybackAutoContext.Confidence.UNKNOWN),
                                PlaybackTelemetry.DecisionInput.number(
                                        "buffer_slope_msps",
                                        policy.bufferSlopeMsPerSecond(),
                                        PlaybackAutoContext.ValueSource.ESTIMATOR,
                                        policy.trendUsable()
                                                ? PlaybackAutoContext.Confidence.MEDIUM
                                                : PlaybackAutoContext.Confidence.UNKNOWN),
                                PlaybackTelemetry.DecisionInput.number(
                                        "time_to_empty_ms",
                                        policy.timeToEmptyMs(),
                                        PlaybackAutoContext.ValueSource.ESTIMATOR,
                                        policy.timeToEmptyMs() >= 0
                                                ? PlaybackAutoContext.Confidence.MEDIUM
                                                : PlaybackAutoContext.Confidence.UNKNOWN),
                                PlaybackTelemetry.DecisionInput.number(
                                        "rebuffer_count",
                                        inputs.rebufferCount(),
                                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                                        PlaybackAutoContext.Confidence.HIGH),
                                PlaybackTelemetry.DecisionInput.number(
                                        "rebuffer_total_ms",
                                        inputs.rebufferTotalMs(),
                                        PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                                        PlaybackAutoContext.Confidence.HIGH))),
                now);
    }
}
