package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

/** Creates the DV5 fallback renderer when the native GPU backend is available. */
final class ExoDv5GpuRendererFactory {

    private ExoDv5GpuRendererFactory() {
    }

    static boolean shouldCreate(ExoDv5Native.Probe probe) {
        return probe != null && probe.available();
    }

    @Nullable
    static ExoDv5GpuRenderer create(
            Context context,
            MediaCodecAdapter.Factory codecAdapterFactory,
            MediaCodecSelector mediaCodecSelector,
            long allowedJoiningTimeMs,
            boolean enableDecoderFallback,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            ExoFrameSchedulingExperimentPolicy.Decision frameSchedulingDecision) {
        ExoDv5Native.Probe probe = ExoDv5Native.probe();
        if (!shouldCreate(probe)) return null;
        return new ExoDv5GpuRenderer(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                allowedJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                frameSchedulingDecision,
                new ExoDv5VideoSink());
    }
}
