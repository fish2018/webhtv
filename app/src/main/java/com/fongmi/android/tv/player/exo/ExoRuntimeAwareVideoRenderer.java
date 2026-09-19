package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.os.Handler;

import androidx.media3.common.Format;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

import java.util.ArrayList;
import java.util.List;

/** MediaCodec renderer that removes only exact, observed runtime-blacklisted combinations. */
final class ExoRuntimeAwareVideoRenderer extends MediaCodecVideoRenderer {

    private final ExoDecoderRuntimeSession runtimeSession;
    private final ExoDecoderRuntimeSession.OutputConfig output;
    private final ExoDiagnosticCollector diagnostics;

    ExoRuntimeAwareVideoRenderer(
            Context context,
            MediaCodecAdapter.Factory codecAdapterFactory,
            MediaCodecSelector mediaCodecSelector,
            long allowedJoiningTimeMs,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            ExoDecoderRuntimeSession runtimeSession,
            ExoDecoderRuntimeSession.OutputConfig output,
            ExoFrameSchedulingExperimentPolicy.Decision
                    frameSchedulingDecision, ExoDiagnosticCollector diagnostics) {
        super(builder(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                allowedJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                frameSchedulingDecision));
        this.runtimeSession = runtimeSession;
        this.output = output;
        this.diagnostics = diagnostics;
    }

    @Override
    public String getName() {
        return "MediaCodecVideoRenderer-RuntimeProfile";
    }

    @Override
    protected List<MediaCodecInfo> getDecoderInfos(
            MediaCodecSelector selector,
            Format format,
            boolean secure) throws MediaCodecUtil.DecoderQueryException {
        return filter(super.getDecoderInfos(selector, format, secure), format, secure);
    }

    private List<MediaCodecInfo> filter(
            List<MediaCodecInfo> infos,
            Format format,
            boolean secure) {
        if (infos == null || infos.isEmpty()) return List.of();
        List<MediaCodecInfo> allowed = new ArrayList<>(infos.size());
        long nowEpochMs = System.currentTimeMillis();
        for (MediaCodecInfo info : infos) {
            boolean excluded = info == null || runtimeSession.shouldExclude(
                    info.name, format, secure, output, nowEpochMs);
            if (info != null && diagnostics != null) diagnostics.log.emit("video.candidates", "runtime-profile-selector", e -> e
                    .observed("decoderName", info.name).observed("accepted", !excluded)
                    .observed("reason", excluded ? "existing-runtime-profile-exclusion" : "runtime-profile-allowed"));
            if (excluded) {
                continue;
            }
            allowed.add(info);
        }
        ExoDiagnosticCodecAdapter.candidates(diagnostics, allowed, format.sampleMimeType, secure, output.tunneling(), "runtime-renderer-final-order");
        return allowed;
    }

    private static Builder builder(
            Context context,
            MediaCodecAdapter.Factory codecAdapterFactory,
            MediaCodecSelector mediaCodecSelector,
            long allowedJoiningTimeMs,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            ExoFrameSchedulingExperimentPolicy.Decision
                    frameSchedulingDecision) {
        Builder builder = new Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(
                        DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
        ExoFrameSchedulingRendererSettings.from(frameSchedulingDecision)
                .apply(builder);
        if (PlaybackPerformanceSetting.isLateDropInputEnabled()) {
            builder.experimentalSetLateThresholdToDropDecoderInputUs(
                    ExoUtil.ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US);
        }
        return builder;
    }
}
