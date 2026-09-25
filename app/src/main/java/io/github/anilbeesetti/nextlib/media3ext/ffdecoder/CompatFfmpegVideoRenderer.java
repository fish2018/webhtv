package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import android.os.Handler;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.decoder.Decoder;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.VideoDecoderOutputBuffer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.video.DecoderVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

/**
 * Mirrors {@link FfmpegVideoRenderer} but can yield the track to the platform decoders, the way
 * {@link CompatFfmpegAudioRenderer} already does for audio. {@link FfmpegVideoRenderer} is final and
 * reports {@code FORMAT_HANDLED} unconditionally, so on high-spec video that MediaCodec can only
 * report as {@code FORMAT_EXCEEDS_CAPABILITIES} the track selector prefers FFmpeg software decoding
 * over hardware decoding.
 */
public final class CompatFfmpegVideoRenderer extends DecoderVideoRenderer {

    private static final int DEFAULT_NUM_OF_INPUT_BUFFERS = 4;
    private static final int DEFAULT_NUM_OF_OUTPUT_BUFFERS = 4;
    /* Default size based on 720p resolution video compressed by a factor of two. */
    private static final int DEFAULT_INPUT_BUFFER_SIZE = Util.ceilDivide(1280, 64) * Util.ceilDivide(720, 64) * (64 * 64 * 3 / 2) / 2;

    private final int numInputBuffers;
    private final int numOutputBuffers;
    private final int threads;
    private final int skipFrame;
    private final int skipLoopFilter;
    private final int lowres;
    private final boolean systemDecoderFallbackOnly;
    @Nullable private final MediaCodecSelector platformDecoderSelector;

    @Nullable private FfmpegVideoDecoder decoder;

    public CompatFfmpegVideoRenderer(long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, boolean systemDecoderFallbackOnly, @Nullable MediaCodecSelector platformDecoderSelector) {
        this(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify, Runtime.getRuntime().availableProcessors(), DEFAULT_NUM_OF_INPUT_BUFFERS, DEFAULT_NUM_OF_OUTPUT_BUFFERS, 0, 0, 0, systemDecoderFallbackOnly, platformDecoderSelector);
    }

    public CompatFfmpegVideoRenderer(long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify, int threads, int numInputBuffers, int numOutputBuffers, int skipFrame, int skipLoopFilter, int lowres, boolean systemDecoderFallbackOnly, @Nullable MediaCodecSelector platformDecoderSelector) {
        super(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
        this.threads = threads;
        this.numInputBuffers = numInputBuffers;
        this.numOutputBuffers = numOutputBuffers;
        this.skipFrame = skipFrame;
        this.skipLoopFilter = skipLoopFilter;
        this.lowres = lowres;
        this.systemDecoderFallbackOnly = systemDecoderFallbackOnly;
        this.platformDecoderSelector = platformDecoderSelector;
    }

    @Override
    public String getName() {
        return "CompatFfmpegVideoRenderer";
    }

    @Override
    public int supportsFormat(Format format) {
        String mimeType = format.sampleMimeType;
        if (mimeType == null) return C.FORMAT_UNSUPPORTED_TYPE;
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isVideo(mimeType)) return C.FORMAT_UNSUPPORTED_TYPE;
        if (systemDecoderFallbackOnly && platformHandlesMimeType(mimeType)) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
        if (!FfmpegLibrary.supportsFormat(mimeType)) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
        if (format.drmInitData != null) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
        return RendererCapabilities.create(C.FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    }

    /**
     * Whether the platform owns this codec, in which case FFmpeg stays out of the way. Must be asked
     * with the very same {@link MediaCodecSelector} the MediaCodec renderers were built with: when
     * that selector is narrowed to hardware-accelerated decoders, a codec whose only platform decoder
     * is software has no MediaCodec renderer to fall back to and FFmpeg has to claim the track.
     * Deliberately never asks whether the platform supports the exact {@link Format}: a 4K HEVC track
     * that exceeds the decoder's level still decodes far better on MediaCodec than in FFmpeg software.
     */
    private boolean platformHandlesMimeType(String mimeType) {
        MediaCodecSelector selector = platformDecoderSelector == null ? MediaCodecSelector.DEFAULT : platformDecoderSelector;
        try {
            return !selector.getDecoderInfos(mimeType, false, false).isEmpty();
        } catch (MediaCodecUtil.DecoderQueryException e) {
            return false;
        }
    }

    @Override
    protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface) throws FfmpegDecoderException {
        if (decoder == null) throw new FfmpegDecoderException("Failed to render output buffer to surface: decoder is not initialized.");
        decoder.renderToSurface(outputBuffer, surface);
        outputBuffer.release();
    }

    @Override
    protected Decoder<DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException> createDecoder(Format format, @Nullable CryptoConfig cryptoConfig) throws DecoderException {
        int initialInputBufferSize = format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
        FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(numInputBuffers, numOutputBuffers, initialInputBufferSize, threads, format, skipFrame, skipLoopFilter, lowres);
        this.decoder = decoder;
        return decoder;
    }

    @Override
    protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
        if (decoder != null) decoder.setOutputMode(outputMode);
    }
}
