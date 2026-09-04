package com.fongmi.android.tv.player.exo;

import android.media.MediaFormat;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;

import com.fongmi.android.tv.setting.ExoPerformanceSetting;
import com.fongmi.android.tv.setting.PlaybackPerformanceCatalog;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.github.catvod.crawler.DebugEventLimiter;
import com.github.catvod.crawler.SpiderDebug;

public class PlaybackAnalyticsListener implements AnalyticsListener, VideoFrameMetadataListener {

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile String playbackTraceId = PlaybackTrace.NONE;
    private static volatile long totalDroppedFrames;
    private static volatile long lastBandwidthLogMs;
    private static volatile long lastMediaEstimateLogMs;
    private static volatile boolean loading;
    private static volatile boolean frameSchedulingExperimentActive;
    private static volatile long seekSequence;
    private static volatile long activeSeekSequence;
    private static volatile long seekRequestedAtMs;
    private static volatile long seekBufferingAtMs;
    private static volatile long seekReadyAtMs;
    private static volatile long seekTargetPositionMs;
    private static volatile boolean seekFirstFrameLogged;
    private static volatile long seekFirstVideoFrameAtMs;
    private static volatile long seekFirstAudioAdvanceAtMs;
    private static volatile long seekPlayingAtMs;
    private static volatile long lastSeekVideoFrameAtMs;
    private static volatile long lastSeekVideoPtsUs;
    private static volatile int seekVideoFrameCount;
    private static volatile ForwardBufferTrend.Snapshot lastStableBufferTrend =
            ForwardBufferTrend.Snapshot.unknown();
    private static final long BANDWIDTH_LOG_INTERVAL_MS = 5_000;
    private static final long MEDIA_ESTIMATE_LOG_INTERVAL_MS = 10_000;
    private static final long LOADING_LOG_INTERVAL_MS = 5_000;
    private static final long LOW_BUFFER_LOADING_LOG_INTERVAL_MS = 1_000;
    private static final long LOW_BUFFER_LOG_THRESHOLD_MS = 8_000;
    private static final long SEEK_TRACE_TIMEOUT_MS = 30_000;
    private static final ObservedMediaBitrateEstimator BITRATE_ESTIMATOR = new ObservedMediaBitrateEstimator();
    private static final ObservedVideoFrameRateEstimator FRAME_RATE_ESTIMATOR = new ObservedVideoFrameRateEstimator();
    private static final ExoFrameTimingMetrics FRAME_TIMING_METRICS = new ExoFrameTimingMetrics();
    private static final ExoFrameSchedulingExperimentMetrics FRAME_SCHEDULING_METRICS =
            new ExoFrameSchedulingExperimentMetrics();
    private static final ForwardBufferTrend BUFFER_TREND = new ForwardBufferTrend();
    private static final DebugEventLimiter LOADING_LOG_LIMITER = new DebugEventLimiter(1);

    public static Snapshot getSnapshot() {
        return snapshot;
    }

    public static void beginSession(String traceId) {
        beginSession(
                traceId,
                ExoFrameSchedulingExperimentPolicy.stableDecision(
                        false, false, false),
                ExoDecoderRuntimeSession.OutputConfig.unknown(),
                "unknown");
    }

    public static void beginSession(
            String traceId,
            ExoFrameSchedulingExperimentPolicy.Decision schedulingDecision,
            ExoDecoderRuntimeSession.OutputConfig output,
            String codecQueueMode) {
        reset();
        playbackTraceId = PlaybackTrace.normalize(traceId);
        frameSchedulingExperimentActive = FRAME_SCHEDULING_METRICS.begin(
                playbackTraceId,
                schedulingDecision,
                output,
                codecQueueMode,
                schedulingDecision == null
                        ? "" : schedulingDecision.deviceDigest(),
                safeElapsedRealtime());
        if (frameSchedulingExperimentActive) {
            ExoFrameSchedulingPerfettoTrace.begin(
                    playbackTraceId, schedulingDecision);
        }
    }

    public static String getPlaybackTraceId() {
        return playbackTraceId;
    }

    public static ObservedMediaBitrateEstimator.Estimate getMediaBitrateEstimate() {
        return BITRATE_ESTIMATOR.estimate();
    }

    public static DisplayMediaBitrateEstimate getDisplayMediaBitrateEstimate() {
        ObservedMediaBitrateEstimator.Estimate estimate = BITRATE_ESTIMATOR.estimate();
        return toDisplayMediaBitrateEstimate(estimate, estimate.source() != ObservedMediaBitrateEstimator.Source.FORMAT && estimate.source() != ObservedMediaBitrateEstimator.Source.UNKNOWN);
    }

    public static DisplayMediaBitrateEstimate getDisplayMediaBitrateEstimate(@Nullable Format videoFormat) {
        boolean videoBitrateKnown = ExoPlaybackDiagnostics.formatBitrate(videoFormat) > 0;
        ObservedMediaBitrateEstimator.Estimate estimate = videoBitrateKnown ? BITRATE_ESTIMATOR.estimate() : BITRATE_ESTIMATOR.estimateWithoutFormat();
        return toDisplayMediaBitrateEstimate(estimate, estimate.source() != ObservedMediaBitrateEstimator.Source.UNKNOWN);
    }

    private static DisplayMediaBitrateEstimate toDisplayMediaBitrateEstimate(ObservedMediaBitrateEstimator.Estimate estimate, boolean estimated) {
        return new DisplayMediaBitrateEstimate(
                estimate.bitrateBitsPerSecond(),
                estimate.source().label(),
                estimate.confidence().label(),
                estimated,
                estimate.averageBitrateBitsPerSecond(),
                estimate.averageSource().label(),
                estimate.averageConfidence().label(),
                estimate.burstBitrateBitsPerSecond(),
                estimate.burstSource().label(),
                estimate.burstConfidence().label());
    }

    public static DisplayFrameRateEstimate getDisplayFrameRateEstimate() {
        ObservedVideoFrameRateEstimator.Estimate estimate = FRAME_RATE_ESTIMATOR.estimate();
        return new DisplayFrameRateEstimate(estimate.frameRate(), estimate.sampleCount());
    }

    public static ExoFrameTimingMetrics.Snapshot getFrameTimingSnapshot() {
        return FRAME_TIMING_METRICS.snapshot();
    }

    public static ExoFrameSchedulingExperimentMetrics.Snapshot
    getFrameSchedulingExperimentSnapshot() {
        Snapshot current = snapshot;
        return FRAME_SCHEDULING_METRICS.snapshot(
                FRAME_TIMING_METRICS.snapshot(),
                current.droppedFrames(),
                current.rebufferCount());
    }

    public static DecoderFailureEvidence getDecoderFailureEvidence(
            PlaybackException error) {
        ErrorDetails details = ErrorDetails.from(error);
        Snapshot current = snapshot;
        Format format = details.format() != null
                ? details.format() : current.videoFormat();
        String decoderName = details.decoderName() == null
                || details.decoderName().isBlank()
                ? current.videoDecoderName() : details.decoderName();
        boolean secure = details.secureDecoderRequired();
        return new DecoderFailureEvidence(format, decoderName, secure);
    }

    public static ForwardBufferTrend.Snapshot getBufferTrend() {
        return BUFFER_TREND.snapshot();
    }

    public static boolean isSeekRecoveryActive() {
        return ExoPlaybackThresholdCoordinator.process().isSeekPending(
                ExoPlaybackThresholdCoordinator.currentSession(),
                SystemClock.elapsedRealtime());
    }

    public static void onUserSeekRequested(
            long originPositionMs,
            long targetPositionMs,
            @Player.State int state,
            long bufferedPositionMs,
            long totalBufferedDurationMs,
                boolean isLoading,
                boolean isPlaying) {
        long now = SystemClock.elapsedRealtime();
        ExoPlaybackThresholdCoordinator.process().markSeek(
                ExoPlaybackThresholdCoordinator.currentSession(), now);
        long sequence = seekSequence == Long.MAX_VALUE ? 1 : seekSequence + 1;
        seekSequence = sequence;
        activeSeekSequence = sequence;
        seekRequestedAtMs = now;
        seekBufferingAtMs = 0;
        seekReadyAtMs = 0;
        seekTargetPositionMs = Math.max(0, targetPositionMs);
        seekFirstFrameLogged = false;
        seekFirstVideoFrameAtMs = 0;
        seekFirstAudioAdvanceAtMs = 0;
        seekPlayingAtMs = 0;
        lastSeekVideoFrameAtMs = 0;
        lastSeekVideoPtsUs = 0;
        seekVideoFrameCount = 0;
        seekTrace(
                "phase=request seq=%d origin=%d target=%d delta=%d state=%s bufferedPosition=%d totalBuffered=%d loading=%s playing=%s",
                sequence,
                Math.max(0, originPositionMs),
                seekTargetPositionMs,
                targetPositionMs - originPositionMs,
                stateName(state),
                Math.max(0, bufferedPositionMs),
                Math.max(0, totalBufferedDurationMs),
                isLoading,
                isPlaying);
    }

    static ForwardBufferTrend.Snapshot getLastStableBufferTrend() {
        return lastStableBufferTrend;
    }

    static ExoThroughputEstimator.Snapshot getThroughputSnapshot() {
        return ExoThroughputCoordinator.process().snapshot();
    }

    public static void reset() {
        ExoPerformanceSetting.discardAutoSession(playbackTraceId);
        ExoPlaybackThresholdCoordinator.process().disrupt(
                ExoPlaybackThresholdCoordinator.currentSession());
        snapshot = Snapshot.empty();
        totalDroppedFrames = 0;
        lastBandwidthLogMs = 0;
        lastMediaEstimateLogMs = 0;
        loading = false;
        frameSchedulingExperimentActive = false;
        activeSeekSequence = 0;
        seekRequestedAtMs = 0;
        seekBufferingAtMs = 0;
        seekReadyAtMs = 0;
        seekTargetPositionMs = 0;
        seekFirstFrameLogged = false;
        seekFirstVideoFrameAtMs = 0;
        seekFirstAudioAdvanceAtMs = 0;
        seekPlayingAtMs = 0;
        lastSeekVideoFrameAtMs = 0;
        lastSeekVideoPtsUs = 0;
        seekVideoFrameCount = 0;
        playbackTraceId = PlaybackTrace.NONE;
        BITRATE_ESTIMATOR.reset();
        FRAME_RATE_ESTIMATOR.reset();
        FRAME_TIMING_METRICS.reset();
        FRAME_SCHEDULING_METRICS.reset();
        ExoFrameSchedulingPerfettoTrace.reset();
        BUFFER_TREND.reset();
        lastStableBufferTrend = ForwardBufferTrend.Snapshot.unknown();
        LOADING_LOG_LIMITER.clear();
        PlaybackCacheMetrics.reset();
        PlaybackBytePositionDataSource.resetSession();
    }

    public static void finishSession(long finalPositionMs) {
        Snapshot finished = snapshot;
        String finishedTraceId = playbackTraceId;
        if (finished.everReady()) {
            long rebufferTotalMs = finished.rebufferTotalMs();
            if (finished.rebufferStartMs() > 0) rebufferTotalMs += Math.max(0, SystemClock.elapsedRealtime() - finished.rebufferStartMs());
            ObservedMediaBitrateEstimator.Estimate media = getMediaBitrateEstimate();
            long mediaBitrate = media.reliable() ? media.bitrateBitsPerSecond() : ExoPlaybackDiagnostics.combinedBitrate(finished.videoFormat(), finished.audioFormat());
            ExoPerformanceSetting.recordAutoSession(
                    finishedTraceId,
                    finished.rebufferCount(),
                    rebufferTotalMs,
                    Math.max(finished.positionMs(), finalPositionMs),
                    mediaBitrate,
                    finished.bandwidthEstimate());
        } else {
            ExoPerformanceSetting.discardAutoSession(finishedTraceId);
        }
        ExoFrameSchedulingExperimentMetrics.Snapshot frameScheduling =
                FRAME_SCHEDULING_METRICS.snapshot(
                        FRAME_TIMING_METRICS.snapshot(),
                        finished.droppedFrames(),
                        finished.rebufferCount());
        if (frameScheduling.active()) {
            PlaybackTrace.log(
                    "exo-frame-ab",
                    finishedTraceId,
                    "%s",
                    frameScheduling.logSummary());
            ExoFrameSchedulingPerfettoTrace.finish(frameScheduling);
        }
        reset();
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
        long now = SystemClock.elapsedRealtime();
        PlaybackAutoContext.SessionToken thresholdSession =
                ExoPlaybackThresholdCoordinator.currentSession();
        boolean seekRecovery = ExoPlaybackThresholdCoordinator.process()
                .isSeekPending(thresholdSession, now);
        Snapshot previous = snapshot;
        Snapshot next = snapshot.withState(stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs);
        if (state == Player.STATE_BUFFERING) {
            rememberStableBufferTrend(BUFFER_TREND.snapshot());
            BITRATE_ESTIMATOR.disrupt();
            BUFFER_TREND.reset();
            FRAME_TIMING_METRICS.resetReleaseContinuity();
            if (next.everReady() && next.rebufferStartMs() <= 0 && !seekRecovery) {
                next = next.withRebufferStart(now);
            }
        }
        if (state != Player.STATE_BUFFERING && next.rebufferStartMs() > 0) next = next.withRebufferEnd(now);
        if (state == Player.STATE_READY) next = next.withEverReady();
        boolean rebufferStarted = previous.rebufferStartMs() <= 0 && next.rebufferStartMs() > 0;
        boolean rebufferEnded = previous.rebufferStartMs() > 0 && next.rebufferStartMs() <= 0;
        snapshot = next;
        logSeekState(eventTime, state, now);
        if (rebufferStarted) {
            FRAME_SCHEDULING_METRICS.observeBoundary(
                    ExoFrameSchedulingExperimentMetrics.Boundary.REBUFFER);
        }
        if (rebufferStarted || rebufferEnded) updateAutoRecovery(next, now);
        observeAutoThresholds(
                eventTime.totalBufferedDurationMs,
                next.rebufferStartMs() > 0,
                now);
        if (state == Player.STATE_READY
                || state == Player.STATE_IDLE
                || state == Player.STATE_ENDED) {
            ExoPlaybackThresholdCoordinator.process().endEpisode(
                    thresholdSession);
        }
        if (!SpiderDebug.isEnabled()) return;
        if (rebufferStarted) {
            traceLog("rebuffer start count=%d position=%d buffered=%d loading=%s", next.rebufferCount(), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        } else if (rebufferEnded) {
            traceLog("rebuffer end duration=%dms total=%dms count=%d position=%d buffered=%d loading=%s", Math.max(0, now - previous.rebufferStartMs()), next.rebufferTotalMs(), next.rebufferCount(), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        } else {
            traceLog("state=%s position=%d buffered=%d loading=%s", stateName(state), eventTime.currentPlaybackPositionMs, eventTime.totalBufferedDurationMs, loading);
        }
    }

    @Override
    public void onPlayWhenReadyChanged(
            EventTime eventTime,
            boolean playWhenReady,
            @Player.PlayWhenReadyChangeReason int reason) {
        if (playWhenReady) return;
        FRAME_TIMING_METRICS.resetReleaseContinuity();
        FRAME_SCHEDULING_METRICS.observeBoundary(
                ExoFrameSchedulingExperimentMetrics.Boundary.PAUSE);
    }

    private static void updateAutoRecovery(Snapshot current, long now) {
        long totalMs = current.rebufferTotalMs();
        if (current.rebufferStartMs() > 0) totalMs += Math.max(0, now - current.rebufferStartMs());
        ObservedMediaBitrateEstimator.Estimate media = getMediaBitrateEstimate();
        long mediaBitrate = media.reliable() ? media.bitrateBitsPerSecond() : ExoPlaybackDiagnostics.combinedBitrate(current.videoFormat(), current.audioFormat());
        int previousMs = ExoPerformanceSetting.getAutoSessionRebufferMs();
        int updatedMs = ExoPerformanceSetting.updateAutoSession(
                playbackTraceId,
                current.rebufferCount(),
                totalMs,
                current.positionMs(),
                mediaBitrate,
                current.bandwidthEstimate());
        if (updatedMs != previousMs && SpiderDebug.isEnabled()) {
            traceLog("auto recovery threshold=%dms previous=%dms count=%d total=%dms mediaBitrate=%d bandwidth=%d", updatedMs, previousMs, current.rebufferCount(), totalMs, mediaBitrate, current.bandwidthEstimate());
        }
    }

    @Override
    public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
        if (loading == isLoading) return;
        loading = isLoading;
        if (!SpiderDebug.isEnabled()) return;
        long bufferedMs = Math.max(0, eventTime.totalBufferedDurationMs);
        long intervalMs = bufferedMs < LOW_BUFFER_LOG_THRESHOLD_MS ? LOW_BUFFER_LOADING_LOG_INTERVAL_MS : LOADING_LOG_INTERVAL_MS;
        if (!"READY".equals(snapshot.state())) intervalMs = 0;
        DebugEventLimiter.Decision decision = LOADING_LOG_LIMITER.acquire("loading", SystemClock.elapsedRealtime(), intervalMs);
        if (!decision.allowed()) return;
        traceLog("loading=%s state=%s position=%d buffered=%d suppressed=%d", isLoading, snapshot.state(), eventTime.currentPlaybackPositionMs, bufferedMs, decision.suppressedCount());
    }

    @Override
    public void onVideoDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withVideoDecoder(decoderName);
        FRAME_SCHEDULING_METRICS.observeDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onVideoInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        Format previousFormat = snapshot.videoFormat();
        boolean changed = previousFormat != null
                && !previousFormat.equals(format);
        snapshot = snapshot.withVideoFormat(format);
        FRAME_RATE_ESTIMATOR.reset();
        FRAME_TIMING_METRICS.resetReleaseContinuity();
        if (changed) {
            FRAME_SCHEDULING_METRICS.observeBoundary(
                    ExoFrameSchedulingExperimentMetrics.Boundary.FORMAT_CHANGE);
        }
        FRAME_SCHEDULING_METRICS.observeFormat(format);
        BITRATE_ESTIMATOR.updateFormats(snapshot.videoFormat(), snapshot.audioFormat());
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video format mime=%s codecs=%s size=%dx%d fps=%.3f bitrate=%d bitrateSource=%s color=%s", format.sampleMimeType, format.codecs, format.width, format.height, format.frameRate, ExoPlaybackDiagnostics.formatBitrate(format), ExoPlaybackDiagnostics.bitrateSource(format), format.colorInfo);
        ExoPlaybackDiagnostics.logTrackFormats(snapshot.videoFormat(), snapshot.audioFormat(), ExoUtil.getEffectiveTargetBufferBytes());
    }

    @Override
    public void onAudioDecoderInitialized(EventTime eventTime, String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        snapshot = snapshot.withAudioDecoder(decoderName);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio decoder=%s init=%dms", decoderName, initializationDurationMs);
    }

    @Override
    public void onAudioInputFormatChanged(EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        snapshot = snapshot.withAudioFormat(format);
        BITRATE_ESTIMATOR.updateFormats(snapshot.videoFormat(), snapshot.audioFormat());
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio format mime=%s codecs=%s channels=%d sampleRate=%d bitrate=%d bitrateSource=%s language=%s", format.sampleMimeType, format.codecs, format.channelCount, format.sampleRate, ExoPlaybackDiagnostics.formatBitrate(format), ExoPlaybackDiagnostics.bitrateSource(format), format.language);
        ExoPlaybackDiagnostics.logTrackFormats(snapshot.videoFormat(), snapshot.audioFormat(), ExoUtil.getEffectiveTargetBufferBytes());
    }

    @Override
    public void onAudioTrackInitialized(EventTime eventTime, AudioSink.AudioTrackConfig config) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio track initialized encoding=%s(%d) sampleRate=%d channelMask=0x%X channels=%d tunneling=%s offload=%s buffer=%d",
                audioEncodingName(config.encoding), config.encoding, config.sampleRate,
                config.channelConfig, Integer.bitCount(config.channelConfig), config.tunneling,
                config.offload, config.bufferSize);
    }

    @Override
    public void onAudioTrackReleased(EventTime eventTime, AudioSink.AudioTrackConfig config) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio track released encoding=%s(%d) sampleRate=%d channelMask=0x%X channels=%d tunneling=%s offload=%s buffer=%d",
                audioEncodingName(config.encoding), config.encoding, config.sampleRate,
                config.channelConfig, Integer.bitCount(config.channelConfig), config.tunneling,
                config.offload, config.bufferSize);
    }

    @Override
    public void onAudioSinkError(EventTime eventTime, Exception error) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio sink error type=%s message=%s", error == null ? "unknown" : error.getClass().getSimpleName(),
                error == null || error.getMessage() == null ? "" : error.getMessage());
        long now = nowElapsed();
        if (activeSeek(now)) {
            seekTrace(
                    "phase=audio-sink-error seq=%d elapsed=%d type=%s message=%s position=%d buffered=%d",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    error == null ? "unknown" : error.getClass().getSimpleName(),
                    error == null || error.getMessage() == null ? "" : error.getMessage(),
                    Math.max(0, eventTime.currentPlaybackPositionMs),
                    Math.max(0, eventTime.totalBufferedDurationMs));
        }
    }

    @Override
    public void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("audio underrun buffer=%d bufferMs=%d elapsedSinceFeedMs=%d", bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        long now = nowElapsed();
        if (activeSeek(now)) {
            seekTrace(
                    "phase=audio-underrun seq=%d elapsed=%d buffer=%d bufferMs=%d elapsedSinceFeedMs=%d position=%d buffered=%d",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    bufferSize,
                    bufferSizeMs,
                    elapsedSinceLastFeedMs,
                    Math.max(0, eventTime.currentPlaybackPositionMs),
                    Math.max(0, eventTime.totalBufferedDurationMs));
        }
    }

    @Override
    public void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {
        long now = nowElapsed();
        if (!activeSeek(now) || seekFirstAudioAdvanceAtMs > 0) return;
        seekFirstAudioAdvanceAtMs = now;
        seekTrace(
                "phase=audio-advancing seq=%d elapsed=%d position=%d buffered=%d playoutStart=%d",
                activeSeekSequence,
                elapsedSinceSeek(now),
                Math.max(0, eventTime.currentPlaybackPositionMs),
                Math.max(0, eventTime.totalBufferedDurationMs),
                playoutStartSystemTimeMs);
    }

    @Override
    public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
        if (!SpiderDebug.isEnabled()) return;
        traceLog("video size=%dx%d unappliedRotation=%d ratio=%.3f", videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
    }

    @Override
    public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        long observed = Math.max(0, droppedFrames);
        totalDroppedFrames = totalDroppedFrames > Long.MAX_VALUE - observed
                ? Long.MAX_VALUE : totalDroppedFrames + observed;
        snapshot = snapshot.withDroppedFrames(totalDroppedFrames);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("droppedFrames=%d total=%d elapsed=%dms position=%d", droppedFrames, totalDroppedFrames, elapsedMs, eventTime.currentPlaybackPositionMs);
    }

    @Override
    public void onVideoFrameProcessingOffset(EventTime eventTime, long totalProcessingOffsetUs, int frameCount) {
        FRAME_TIMING_METRICS.observeProcessingOffset(
                totalProcessingOffsetUs,
                frameCount,
                frameSchedulingExperimentActive);
    }

    @Override
    public void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {
        FRAME_TIMING_METRICS.observeCodecError(videoCodecError);
        if (SpiderDebug.isEnabled()) traceLog(
                "video codec recoverable errorType=%s",
                videoCodecError == null ? "unknown" : videoCodecError.getClass().getSimpleName());
    }

    @Override
    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        snapshot = snapshot.withBandwidth(totalLoadTimeMs, totalBytesLoaded, bitrateEstimate);
        if (!SpiderDebug.isEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBandwidthLogMs < BANDWIDTH_LOG_INTERVAL_MS) return;
        lastBandwidthLogMs = now;
        ObservedMediaBitrateEstimator.Estimate media = getMediaBitrateEstimate();
        ForwardBufferTrend.Snapshot trend = getBufferTrend();
        ExoThroughputEstimator.Snapshot throughput = getThroughputSnapshot();
        traceLog("bandwidth=%d raw=%d short=%d long=%d throughputConfidence=%s predictionErrorPermille=%d pathTrust=%s preloadContended=%s loadTime=%dms bytes=%d mediaBitrate=%d mediaSource=%s mediaConfidence=%s mediaAverage=%d averageSource=%s averageConfidence=%s mediaBurst=%d burstSource=%s burstConfidence=%s bufferSlope=%d slopeWindowMs=%d",
                bitrateEstimate,
                throughput.rawEstimateBitsPerSecond(),
                throughput.shortEstimateBitsPerSecond(),
                throughput.longEstimateBitsPerSecond(),
                throughput.confidence().label(),
                throughput.predictionErrorPermille(),
                throughput.pathTrust().label(),
                throughput.preloadContended(),
                totalLoadTimeMs, totalBytesLoaded,
                media.bitrateBitsPerSecond(), media.source().label(), media.confidence().label(),
                media.averageBitrateBitsPerSecond(), media.averageSource().label(), media.averageConfidence().label(),
                media.burstBitrateBitsPerSecond(), media.burstSource().label(), media.burstConfidence().label(),
                trend.slopeMsPerSecond(), trend.windowMs());
    }

    @Override
    public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        BITRATE_ESTIMATOR.observeLoad(loadEventInfo.bytesLoaded, mediaLoadData.mediaStartTimeMs, mediaLoadData.mediaEndTimeMs);
        long contentLength = PlaybackBytePositionDataSource.parseContentRangeTotal(loadEventInfo.responseHeaders);
        if (contentLength <= 0 && loadEventInfo.dataSpec.position == 0 && loadEventInfo.dataSpec.length != C.LENGTH_UNSET) contentLength = loadEventInfo.dataSpec.length;
        BITRATE_ESTIMATOR.updateContent(contentLength, C.TIME_UNSET);
    }

    @Override
    public void onPositionDiscontinuity(EventTime eventTime, Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        boolean seek = reason == Player.DISCONTINUITY_REASON_SEEK
                || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
        long now = nowElapsed();
        BITRATE_ESTIMATOR.disrupt();
        FRAME_RATE_ESTIMATOR.reset();
        FRAME_TIMING_METRICS.resetReleaseContinuity();
        if (seek) {
            FRAME_SCHEDULING_METRICS.observeBoundary(
                    ExoFrameSchedulingExperimentMetrics.Boundary.SEEK);
        }
        BUFFER_TREND.reset();
        lastStableBufferTrend = ForwardBufferTrend.Snapshot.unknown();
        PlaybackAutoContext.SessionToken thresholdSession =
                ExoPlaybackThresholdCoordinator.currentSession();
        ExoPlaybackThresholdCoordinator.process().disrupt(thresholdSession);
        if (seek) {
            ExoPlaybackThresholdCoordinator.process().markSeek(
                    thresholdSession, now);
            if (!activeSeek(now) && SpiderDebug.isEnabled()) {
                long sequence = seekSequence == Long.MAX_VALUE ? 1 : seekSequence + 1;
                seekSequence = sequence;
                activeSeekSequence = sequence;
                seekRequestedAtMs = now;
                seekBufferingAtMs = 0;
                seekReadyAtMs = 0;
                seekTargetPositionMs = Math.max(0, newPosition.positionMs);
                seekFirstFrameLogged = false;
                seekFirstVideoFrameAtMs = 0;
                seekFirstAudioAdvanceAtMs = 0;
                seekPlayingAtMs = 0;
                lastSeekVideoFrameAtMs = 0;
                lastSeekVideoPtsUs = 0;
                seekVideoFrameCount = 0;
                seekTrace(
                        "phase=request-observed seq=%d origin=%d target=%d state=%s buffered=%d loading=%s",
                        sequence,
                        Math.max(0, oldPosition.positionMs),
                        seekTargetPositionMs,
                        snapshot.state(),
                        Math.max(0, eventTime.totalBufferedDurationMs),
                        loading);
            }
            if (!activeSeek(now)) return;
            seekTrace(
                    "phase=discontinuity seq=%d elapsed=%d reason=%d old=%d requested=%d actual=%d adjustment=%d buffered=%d loading=%s",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    reason,
                    Math.max(0, oldPosition.positionMs),
                    seekTargetPositionMs,
                    Math.max(0, newPosition.positionMs),
                    newPosition.positionMs - seekTargetPositionMs,
                    Math.max(0, eventTime.totalBufferedDurationMs),
                    loading);
        }
    }

    @Override
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format, @Nullable MediaFormat mediaFormat) {
        FRAME_RATE_ESTIMATOR.observe(presentationTimeUs);
        if (frameSchedulingExperimentActive) {
            FRAME_TIMING_METRICS.observeFrameRelease(
                    presentationTimeUs, releaseTimeNs, System.nanoTime());
        }
        long now = nowElapsed();
        PlaybackAutoContext.SessionToken thresholdSession =
                ExoPlaybackThresholdCoordinator.currentSession();
        if ("READY".equals(snapshot.state())
                && ExoPlaybackThresholdCoordinator.process()
                .isSeekPending(thresholdSession, now)) {
            ExoPlaybackThresholdCoordinator.process().endEpisode(thresholdSession);
        }
        if (!activeSeek(now)) return;
        seekVideoFrameCount++;
        if (seekFirstVideoFrameAtMs <= 0) {
            seekFirstVideoFrameAtMs = now;
            lastSeekVideoFrameAtMs = now;
            lastSeekVideoPtsUs = presentationTimeUs;
            seekTrace(
                    "phase=video-frame-first seq=%d elapsed=%d frame=%d ptsUs=%d position=%d buffered=%d",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    seekVideoFrameCount,
                    presentationTimeUs,
                    Math.max(0, snapshot.positionMs()),
                    Math.max(0, snapshot.bufferedMs()));
            return;
        }
        long gapMs = Math.max(0, now - lastSeekVideoFrameAtMs);
        if (gapMs >= 120) {
            seekTrace(
                    "phase=video-frame-gap seq=%d elapsed=%d gap=%d frame=%d ptsDeltaUs=%d position=%d buffered=%d state=%s",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    gapMs,
                    seekVideoFrameCount,
                    presentationTimeUs - lastSeekVideoPtsUs,
                    Math.max(0, snapshot.positionMs()),
                    Math.max(0, snapshot.bufferedMs()),
                    snapshot.state());
        }
        lastSeekVideoFrameAtMs = now;
        lastSeekVideoPtsUs = presentationTimeUs;
    }

    @Override
    public void onRenderedFirstFrame(
            EventTime eventTime,
            Object output,
            long renderTimeMs) {
        FRAME_SCHEDULING_METRICS.observeFirstFrame(renderTimeMs);
        long now = nowElapsed();
        if (activeSeek(now) && !seekFirstFrameLogged) {
            seekFirstFrameLogged = true;
            seekTrace(
                    "phase=first-frame seq=%d elapsed=%d afterReady=%d position=%d buffered=%d loading=%s",
                    activeSeekSequence,
                    elapsedSinceSeek(now),
                    seekReadyAtMs <= 0 ? -1 : Math.max(0, now - seekReadyAtMs),
                    Math.max(0, eventTime.currentPlaybackPositionMs),
                    Math.max(0, eventTime.totalBufferedDurationMs),
                    loading);
        }
    }

    @Override
    public void onIsPlayingChanged(EventTime eventTime, boolean isPlaying) {
        if (!isPlaying) return;
        long now = nowElapsed();
        if (!activeSeek(now)) return;
        if (seekPlayingAtMs > 0) return;
        seekPlayingAtMs = now;
        seekTrace(
                "phase=playing seq=%d elapsed=%d afterReady=%d position=%d buffered=%d loading=%s",
                activeSeekSequence,
                elapsedSinceSeek(now),
                seekReadyAtMs <= 0 ? -1 : Math.max(0, now - seekReadyAtMs),
                Math.max(0, eventTime.currentPlaybackPositionMs),
                Math.max(0, eventTime.totalBufferedDurationMs),
                loading);
    }

    private static void logSeekState(
            EventTime eventTime,
            @Player.State int state,
            long now) {
        if (!activeSeek(now)) return;
        if (state == Player.STATE_BUFFERING && seekBufferingAtMs <= 0) {
            seekBufferingAtMs = now;
        } else if (state == Player.STATE_READY && seekReadyAtMs <= 0) {
            seekReadyAtMs = now;
        }
        long bufferingMs = seekBufferingAtMs <= 0 || state != Player.STATE_READY
                ? -1 : Math.max(0, now - seekBufferingAtMs);
        seekTrace(
                "phase=state seq=%d elapsed=%d state=%s bufferingDuration=%d position=%d buffered=%d loading=%s",
                activeSeekSequence,
                elapsedSinceSeek(now),
                stateName(state),
                bufferingMs,
                Math.max(0, eventTime.currentPlaybackPositionMs),
                Math.max(0, eventTime.totalBufferedDurationMs),
                loading);
    }

    private static boolean activeSeek(long now) {
        if (activeSeekSequence <= 0 || seekRequestedAtMs <= 0) return false;
        if (now - seekRequestedAtMs <= SEEK_TRACE_TIMEOUT_MS) return true;
        activeSeekSequence = 0;
        return false;
    }

    private static long elapsedSinceSeek(long now) {
        return Math.max(0, now - seekRequestedAtMs);
    }

    private static long nowElapsed() {
        return SystemClock.elapsedRealtime();
    }

    private static void seekTrace(String format, Object... args) {
        PlaybackTrace.log("exo-seek", playbackTraceId, format, args);
    }

    @Override
    public void onEvents(Player player, AnalyticsListener.Events events) {
        long now = SystemClock.elapsedRealtime();
        PlaybackBytePositionDataSource.Snapshot bytes = PlaybackBytePositionDataSource.snapshot();
        BITRATE_ESTIMATOR.updateContent(bytes.contentLengthBytes(), player.getDuration());
        boolean stablePlayback = player.getPlaybackState() == Player.STATE_READY && player.isPlaying();
        BITRATE_ESTIMATOR.observeBytePosition(now, player.getBufferedPosition(), bytes, stablePlayback);
        BUFFER_TREND.observe(
                now,
                player.getTotalBufferedDuration(),
                stablePlayback,
                player.isLoading());
        ForwardBufferTrend.Snapshot trend = BUFFER_TREND.snapshot();
        rememberStableBufferTrend(trend);
        observeAutoThresholds(
                player.getTotalBufferedDuration(),
                snapshot.rebufferStartMs() > 0,
                now);
        if (!SpiderDebug.isEnabled() || now - lastMediaEstimateLogMs < MEDIA_ESTIMATE_LOG_INTERVAL_MS) return;
        lastMediaEstimateLogMs = now;
        ObservedMediaBitrateEstimator.Estimate media = getMediaBitrateEstimate();
        traceLog("media-estimate bitrate=%d source=%s confidence=%s average=%d averageSource=%s averageConfidence=%s burst=%d burstSource=%s burstConfidence=%s p50=%d p90=%d windows=%d windowMs=%d observedMs=%d contentLength=%d duration=%d bufferSlope=%d slopeConfidence=%s slopeWindowMs=%d slopeSamples=%d",
                media.bitrateBitsPerSecond(), media.source().label(), media.confidence().label(),
                media.averageBitrateBitsPerSecond(), media.averageSource().label(), media.averageConfidence().label(),
                media.burstBitrateBitsPerSecond(), media.burstSource().label(), media.burstConfidence().label(),
                media.p50BitsPerSecond(), media.p90BitsPerSecond(), media.windowCount(), media.windowDurationMs(), media.observedDurationMs(), media.contentLengthBytes(), media.durationMs(),
                trend.slopeMsPerSecond(), trend.confidence().label(), trend.windowMs(), trend.sampleCount());
    }

    private static void rememberStableBufferTrend(
            ForwardBufferTrend.Snapshot trend) {
        if (trend == null || !trend.known()) return;
        ForwardBufferTrend.Snapshot previous = lastStableBufferTrend;
        if (!previous.known()
                || trend.sampledAtElapsedMs() >= previous.sampledAtElapsedMs()) {
            lastStableBufferTrend = trend;
        }
    }

    private static void observeAutoThresholds(
            long bufferedDurationMs,
            boolean rebuffering,
            long nowElapsedMs) {
        if (!PlaybackPerformanceSetting.hasAutomaticOptions(
                PlayerSetting.EXO,
                PlaybackPerformanceCatalog.EXO_START_BUFFER,
                PlaybackPerformanceCatalog.EXO_REBUFFER)) return;
        ExoPerformanceSetting.refreshAutoSession(playbackTraceId);
        ExoPlaybackThresholdCoordinator.process().observe(
                ExoPlaybackThresholdCoordinator.captureInputs(
                        ExoPerformanceSetting.getEffectiveStartBufferMs(),
                        ExoPerformanceSetting.getEffectiveRebufferMs(),
                        Math.max(0, bufferedDurationMs),
                        -1,
                        rebuffering,
                        nowElapsedMs));
    }

    @Override
    public void onPlayerError(EventTime eventTime, PlaybackException error) {
        String code = PlaybackException.getErrorCodeName(error.errorCode);
        ErrorDetails details = ErrorDetails.from(error);
        snapshot = snapshot.withError(code, error.getClass().getSimpleName(), details);
        if (!SpiderDebug.isEnabled()) return;
        traceLog("error code=%s errorType=%s details=%s", code, error.getClass().getSimpleName(), details.summary());
    }

    private static void traceLog(String format, Object... args) {
        PlaybackTrace.log("playback-metrics", playbackTraceId, format, args);
    }

    private static long safeElapsedRealtime() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
            return 0;
        }
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

    private static String audioEncodingName(int encoding) {
        return switch (encoding) {
            case C.ENCODING_PCM_16BIT -> "pcm16";
            case C.ENCODING_PCM_FLOAT -> "pcmfloat";
            case C.ENCODING_AC3 -> "ac3";
            case C.ENCODING_E_AC3 -> "eac3";
            case C.ENCODING_E_AC3_JOC -> "eac3-joc";
            case C.ENCODING_DTS -> "dts";
            case C.ENCODING_DTS_HD -> "dts-hd";
            case C.ENCODING_DTS_HD_MA -> "dts-hd-ma";
            case C.ENCODING_DOLBY_TRUEHD -> "truehd";
            default -> "unknown";
        };
    }

    public record DisplayMediaBitrateEstimate(
            long bitrateBitsPerSecond,
            String source,
            String confidence,
            boolean estimated,
            long averageBitrateBitsPerSecond,
            String averageSource,
            String averageConfidence,
            long burstBitrateBitsPerSecond,
            String burstSource,
            String burstConfidence) {
    }

    public record DisplayFrameRateEstimate(float frameRate, int sampleCount) {
    }

    public record DecoderFailureEvidence(
            @Nullable Format format,
            String decoderName,
            boolean secureDecoderRequired) {

        public DecoderFailureEvidence {
            decoderName = decoderName == null ? "" : decoderName;
        }
    }

    public record Snapshot(String state, String videoDecoderName, Format videoFormat, String audioDecoderName, Format audioFormat, long droppedFrames, long positionMs, long bufferedMs, long bandwidthEstimate, int lastLoadTimeMs, long lastLoadBytes, int rebufferCount, long rebufferTotalMs, long rebufferStartMs, boolean everReady, String errorCode, String errorMessage, Format errorFormat, String errorDecoderName, String errorDiagnosticInfo, boolean errorSecureDecoderRequired, String errorCause) {

        public static Snapshot empty() {
            return new Snapshot("", "", null, "", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, "", "", null, "", "", false, "");
        }

        private Snapshot withState(String state, long positionMs, long bufferedMs) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, Math.max(0, bufferedMs), bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withVideoDecoder(String decoderName) {
            return new Snapshot(state, decoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withVideoFormat(Format format) {
            return new Snapshot(state, videoDecoderName, format, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withAudioDecoder(String decoderName) {
            return new Snapshot(state, videoDecoderName, videoFormat, decoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withAudioFormat(Format format) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, format, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withDroppedFrames(long droppedFrames) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withBandwidth(int loadTimeMs, long bytesLoaded, long bitrateEstimate) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, Math.max(0, bitrateEstimate), Math.max(0, loadTimeMs), Math.max(0, bytesLoaded), rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withRebufferStart(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount + 1, rebufferTotalMs, now, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withRebufferEnd(long now) {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs + Math.max(0, now - rebufferStartMs), 0, everReady, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withEverReady() {
            return new Snapshot(state, videoDecoderName, videoFormat, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, true, errorCode, errorMessage, errorFormat, errorDecoderName, errorDiagnosticInfo, errorSecureDecoderRequired, errorCause);
        }

        private Snapshot withError(String code, String message, ErrorDetails details) {
            Format format = details.format() != null ? details.format() : videoFormat;
            return new Snapshot(state, videoDecoderName, format, audioDecoderName, audioFormat, droppedFrames, positionMs, bufferedMs, bandwidthEstimate, lastLoadTimeMs, lastLoadBytes, rebufferCount, rebufferTotalMs, rebufferStartMs, everReady, code, message, details.format(), details.decoderName(), details.diagnosticInfo(), details.secureDecoderRequired(), details.cause());
        }
    }

    private record ErrorDetails(Format format, String decoderName, String diagnosticInfo, boolean secureDecoderRequired, String cause) {

        static ErrorDetails from(PlaybackException error) {
            if (error == null) return new ErrorDetails(null, "", "", false, "");
            Format format = null;
            String decoderName = "";
            String diagnosticInfo = "";
            boolean secure = false;
            if (error instanceof ExoPlaybackException exo) format = exo.rendererFormat;
            MediaCodecRenderer.DecoderInitializationException init = findDecoderInitException(error);
            if (init != null) {
                decoderName = init.codecInfo == null ? "" : init.codecInfo.name;
                diagnosticInfo = init.diagnosticInfo == null ? "" : init.diagnosticInfo;
                secure = init.secureDecoderRequired;
            }
            return new ErrorDetails(format, decoderName, diagnosticInfo, secure, causeTypes(error));
        }

        private String summary() {
            return "decoder=" + decoderName + " diagnostic=" + diagnosticInfo + " secure=" + secureDecoderRequired + " cause=" + cause;
        }
    }

    private static MediaCodecRenderer.DecoderInitializationException findDecoderInitException(Throwable error) {
        // Attribute only the primary initialization exception. A single terminal
        // error may contain several fallback decoders; persisting all of them at
        // once would turn one playback failure into multiple blacklist samples.
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MediaCodecRenderer.DecoderInitializationException init) return init;
        }
        return null;
    }

    private static String causeTypes(Throwable error) {
        if (error == null) return "";
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (result.length() > 0) result.append(" <- ");
            result.append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return result.toString();
    }
}
