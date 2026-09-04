package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.media.MediaCrypto;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Experimental MediaCodec renderer targeting {@link ExoDv5VideoSink}. */
final class ExoDv5GpuRenderer extends MediaCodecVideoRenderer {

    private final Context context;
    private final ExoDv5VideoSink sink;

    ExoDv5GpuRenderer(
            Context context,
            MediaCodecAdapter.Factory codecAdapterFactory,
            MediaCodecSelector mediaCodecSelector,
            long allowedJoiningTimeMs,
            boolean enableDecoderFallback,
            @Nullable Handler eventHandler,
            @Nullable VideoRendererEventListener eventListener,
            ExoFrameSchedulingExperimentPolicy.Decision frameSchedulingDecision,
            ExoDv5VideoSink sink) {
        super(ExoFrameSchedulingRendererSettings.from(frameSchedulingDecision)
                .apply(new Builder(context)
                        .setCodecAdapterFactory(codecAdapterFactory)
                        .setMediaCodecSelector(mediaCodecSelector)
                        .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
                        .setEnableDecoderFallback(enableDecoderFallback)
                        .setEventHandler(eventHandler)
                        .setEventListener(eventListener)
                        .setVideoSink(sink)));
        this.context = context;
        this.sink = sink;
        sink.setDiagnosticContext(context);
    }

    @Override
    public String getName() {
        return "MediaCodecVideoRenderer-DV5-Vulkan";
    }

    @Override
    protected int supportsFormat(MediaCodecSelector selector, Format format)
            throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException {
        if (!ExoDv5GpuMappingPolicy.isProfile5(
                format.sampleMimeType, format.codecs)
                || format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (supportsNativeDolbyVision(selector, format)) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        if (getDecoderInfos(selector, format, false).isEmpty()) {
            return C.FORMAT_UNSUPPORTED_TYPE;
        }
        return super.supportsFormat(selector, asHevc(format))
                & ~RendererCapabilities.TUNNELING_SUPPORT_MASK;
    }

    @Override
    protected List<MediaCodecInfo> getDecoderInfos(
            MediaCodecSelector selector, Format format, boolean secure)
            throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException {
        if (secure || !ExoDv5GpuMappingPolicy.isProfile5(
                format.sampleMimeType, format.codecs)) {
            return List.of();
        }
        if (supportsNativeDolbyVision(selector, format)) return List.of();
        List<MediaCodecInfo> infos = super.getDecoderInfos(
                selector, asHevc(format), false);
        List<MediaCodecInfo> hardwareInfos = new ArrayList<>(infos.size());
        for (MediaCodecInfo info : infos) {
            if (info != null && info.hardwareAccelerated) hardwareInfos.add(info);
        }
        return hardwareInfos;
    }

    @Override
    protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(
            MediaCodecInfo info, Format format, MediaCrypto crypto, float rate) {
        return super.getMediaCodecConfiguration(info, asHevc(format), crypto, rate);
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs)
            throws ExoPlaybackException {
        try {
            super.render(positionUs, elapsedRealtimeUs);
        } catch (ExoPlaybackException error) {
            sink.diagnosticLog("renderer render failed code=" + error.errorCode
                    + " message=" + error.getMessage()
                    + " cause=" + error.getCause());
            throw error;
        } catch (RuntimeException error) {
            sink.diagnosticLog("renderer render runtime failure=" + error);
            throw error;
        }
    }

    @Override
    protected void onCodecInitialized(
            String name,
            MediaCodecAdapter.Configuration configuration,
            long initializedTimestampMs,
            long initializationDurationMs) {
        sink.diagnosticLog("renderer codec initialized name=" + name
                + " durationMs=" + initializationDurationMs);
        super.onCodecInitialized(
                name, configuration, initializedTimestampMs, initializationDurationMs);
    }

    @Override
    protected void onCodecReleased(String name) {
        sink.diagnosticLog("renderer codec released name=" + name);
        super.onCodecReleased(name);
    }

    @Override
    protected void onCodecError(Exception codecError) {
        sink.diagnosticLog("renderer codec error=" + codecError);
        super.onCodecError(codecError);
    }

    @Override
    protected void onStreamChanged(
            Format[] formats,
            long startPositionUs,
            long offsetUs,
            MediaSource.MediaPeriodId mediaPeriodId) throws ExoPlaybackException {
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
        Format format = formats == null || formats.length == 0 ? null : formats[0];
        sink.diagnosticLog("renderer stream startUs=" + startPositionUs
                + " offsetUs=" + offsetUs
                + " mime=" + (format == null ? null : format.sampleMimeType)
                + " codecs=" + (format == null ? null : format.codecs));
    }

    @Override
    protected void onDisabled() {
        sink.diagnosticLog("renderer disabled stats=" + sink.stats());
        try {
            super.onDisabled();
        } finally {
            // Release the codec input Surface before disconnecting the output swapchain.
            sink.disable();
        }
    }

    @Override
    protected void onStopped() {
        sink.diagnosticLog("renderer stopped stats=" + sink.stats());
        super.onStopped();
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer)
            throws ExoPlaybackException {
        ByteBuffer data = buffer.data;
        if (data == null || buffer.isEncrypted()) return;
        for (byte[] rpu : findRpuNalus(data)) {
            sink.queueRpu(buffer.timeUs, rpu);
        }
    }

    ExoDv5Native.Stats diagnosticStats() {
        return sink.stats();
    }

    static Format asHevc(Format format) {
        return format.buildUpon()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs(null)
                .setInitializationData(
                        DolbyVisionP81ExtractorsFactory.removeDolbyVisionCsd(
                                format.initializationData))
                .build();
    }

    private boolean supportsNativeDolbyVision(
            MediaCodecSelector selector, Format format)
            throws androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException {
        if (android.os.Build.VERSION.SDK_INT < 26) return false;
        android.hardware.display.DisplayManager displayManager =
                (android.hardware.display.DisplayManager) context.getSystemService(
                        Context.DISPLAY_SERVICE);
        android.view.Display display = displayManager == null
                ? null : displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY);
        if (display == null || !display.isHdr()
                || display.getHdrCapabilities() == null) return false;
        boolean dolbyDisplay = false;
        for (int hdrType : display.getHdrCapabilities().getSupportedHdrTypes()) {
            if (hdrType == android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                dolbyDisplay = true;
                break;
            }
        }
        if (!dolbyDisplay) return false;
        for (MediaCodecInfo info : selector.getDecoderInfos(
                format.sampleMimeType, false, false)) {
            if (info != null && info.hardwareAccelerated
                    && info.isFormatSupported(context, format)) return true;
        }
        return false;
    }

    static List<byte[]> findRpuNalus(ByteBuffer source) {
        ByteBuffer data = source.duplicate();
        int start = data.position();
        int limit = data.limit();
        List<byte[]> result = new ArrayList<>();
        if (findStartCode(data, start, limit) >= 0) {
            int cursor = start;
            while (true) {
                int prefix = findStartCode(data, cursor, limit);
                if (prefix < 0) break;
                int prefixSize = data.get(prefix + 2) == 1 ? 3 : 4;
                int nalStart = prefix + prefixSize;
                int next = findStartCode(data, nalStart, limit);
                int nalEnd = next < 0 ? limit : next;
                addRpu(data, nalStart, nalEnd, result);
                if (next < 0) break;
                cursor = next;
            }
            return result;
        }
        for (int lengthSize : new int[] {4, 2, 1}) {
            List<byte[]> candidate = parseLengthPrefixed(
                    data, start, limit, lengthSize);
            if (candidate != null) return candidate;
        }
        return result;
    }

    private static List<byte[]> parseLengthPrefixed(
            ByteBuffer data, int start, int limit, int lengthSize) {
        List<byte[]> result = new ArrayList<>();
        int cursor = start;
        boolean sawNal = false;
        while (cursor < limit) {
            if (limit - cursor < lengthSize) return null;
            long length = 0;
            for (int i = 0; i < lengthSize; i++) {
                length = (length << 8) | (data.get(cursor + i) & 0xffL);
            }
            cursor += lengthSize;
            if (length < 2 || length > limit - cursor) return null;
            int nalEnd = cursor + (int) length;
            addRpu(data, cursor, nalEnd, result);
            sawNal = true;
            cursor = nalEnd;
        }
        return sawNal ? result : null;
    }

    private static void addRpu(
            ByteBuffer data, int start, int end, List<byte[]> output) {
        if (end - start < 2 || ((data.get(start) & 0x7e) >> 1) != 62) return;
        byte[] nal = new byte[end - start];
        ByteBuffer copy = data.duplicate();
        copy.position(start);
        copy.limit(end);
        copy.get(nal);
        output.add(nal);
    }

    private static int findStartCode(ByteBuffer data, int start, int limit) {
        for (int i = start; i + 3 <= limit; i++) {
            if (data.get(i) != 0 || data.get(i + 1) != 0) continue;
            if (data.get(i + 2) == 1) return i;
            if (i + 4 <= limit && data.get(i + 2) == 0
                    && data.get(i + 3) == 1) return i;
        }
        return -1;
    }
}
