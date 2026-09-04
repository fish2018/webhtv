package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static androidx.media3.exoplayer.audio.AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY;

import android.content.Context;
import android.os.Handler;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import com.github.catvod.crawler.SpiderDebug;

public final class CompatFfmpegAudioRenderer extends DecoderAudioRenderer<FfmpegAudioDecoder> {

    private static final int NUM_BUFFERS = 16;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 5760;
    private static final int STEREO_CHANNEL_COUNT = 2;
    private final Context context;
    private final boolean systemDecoderFallbackOnly;

    public CompatFfmpegAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink) {
        this(null, eventHandler, eventListener, audioSink, false);
    }

    public CompatFfmpegAudioRenderer(Context context, Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink, boolean systemDecoderFallbackOnly) {
        super(eventHandler, eventListener, audioSink);
        this.context = context;
        this.systemDecoderFallbackOnly = systemDecoderFallbackOnly;
    }

    @Override
    public String getName() {
        return "CompatFfmpegAudioRenderer";
    }

    @Override
    protected int supportsFormatInternal(Format format) {
        Format decodeFormat = normalizeDecodeFormat(format);
        String sampleMimeType = decodeFormat.sampleMimeType;
        if (sampleMimeType == null) return C.FORMAT_UNSUPPORTED_TYPE;
        if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(sampleMimeType)) return C.FORMAT_UNSUPPORTED_TYPE;
        if (systemDecoderFallbackOnly && isSystemDecoderSupported(decodeFormat)) return C.FORMAT_UNSUPPORTED_SUBTYPE;
        if (!FfmpegLibrary.supportsFormat(sampleMimeType)) return C.FORMAT_UNSUPPORTED_SUBTYPE;
        if (getOutputChannelCount(decodeFormat) == Format.NO_VALUE) return C.FORMAT_UNSUPPORTED_SUBTYPE;
        return decodeFormat.cryptoType == C.CRYPTO_TYPE_NONE ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_DRM;
    }

    @Override
    protected FfmpegAudioDecoder createDecoder(Format format, CryptoConfig cryptoConfig) throws FfmpegDecoderException {
        TraceUtil.beginSection("createCompatFfmpegAudioDecoder");
        Format decodeFormat = normalizeDecodeFormat(format);
        int initialInputBufferSize = decodeFormat.maxInputSize != Format.NO_VALUE ? decodeFormat.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
        int outputChannelCount = getOutputChannelCount(decodeFormat);
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("exo-audio", "ffmpeg output mime=%s sourceChannels=%d outputChannels=%d downmix=%s",
                    decodeFormat.sampleMimeType, decodeFormat.channelCount, outputChannelCount,
                    outputChannelCount == STEREO_CHANNEL_COUNT && decodeFormat.channelCount > STEREO_CHANNEL_COUNT);
        }
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(decodeFormat, NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, shouldOutputFloat(decodeFormat, outputChannelCount), outputChannelCount);
        TraceUtil.endSection();
        return decoder;
    }

    @Override
    protected Format getOutputFormat(FfmpegAudioDecoder decoder) {
        Assertions.checkNotNull(decoder);
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(decoder.getChannelCount())
                .setSampleRate(decoder.getSampleRate())
                .setPcmEncoding(decoder.getEncoding())
                .build();
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() {
        return RendererCapabilities.ADAPTIVE_NOT_SEAMLESS;
    }

    private static Format normalizeDecodeFormat(Format format) {
        String sampleMimeType = format.sampleMimeType;
        if (sampleMimeType == null) return format;
        if (sampleMimeType.startsWith(MimeTypes.AUDIO_DTS_HD + ";") || MimeTypes.AUDIO_MEDIA3_DTS_HD_MA_CORELESS.equals(sampleMimeType)) return format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_DTS_HD).build();
        if (MimeTypes.AUDIO_AMR.equals(sampleMimeType)) return format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AMR_NB).build();
        return format;
    }

    private boolean isSystemDecoderSupported(Format format) {
        if (context == null || format.sampleMimeType == null) return false;
        try {
            for (androidx.media3.exoplayer.mediacodec.MediaCodecInfo info : MediaCodecSelector.DEFAULT.getDecoderInfos(format.sampleMimeType, false, false)) {
                if (info.isFormatSupported(context, format)) return true;
            }
            return false;
        } catch (MediaCodecUtil.DecoderQueryException e) {
            return false;
        }
    }

    private boolean sinkSupportsFormat(Format format, int pcmEncoding, int channelCount) {
        return sinkSupportsFormat(Util.getPcmFormat(pcmEncoding, channelCount, format.sampleRate));
    }

    private int getOutputChannelCount(Format format) {
        boolean sourceSupported = sinkSupportsFormat(format, C.ENCODING_PCM_16BIT, format.channelCount)
                || sinkSupportsFormat(format, C.ENCODING_PCM_FLOAT, format.channelCount);
        boolean stereoSupported = sinkSupportsFormat(format, C.ENCODING_PCM_16BIT, STEREO_CHANNEL_COUNT)
                || sinkSupportsFormat(format, C.ENCODING_PCM_FLOAT, STEREO_CHANNEL_COUNT);
        return resolveOutputChannelCount(format.channelCount, sourceSupported, stereoSupported);
    }

    static int resolveOutputChannelCount(int sourceChannelCount, boolean sourceSupported, boolean stereoSupported) {
        if (sourceSupported) return sourceChannelCount;
        if (sourceChannelCount > STEREO_CHANNEL_COUNT && stereoSupported) return STEREO_CHANNEL_COUNT;
        return Format.NO_VALUE;
    }

    private boolean shouldOutputFloat(Format format, int outputChannelCount) {
        if (!sinkSupportsFormat(format, C.ENCODING_PCM_16BIT, outputChannelCount)) return true;
        int floatSupport = getSinkFormatSupport(Util.getPcmFormat(C.ENCODING_PCM_FLOAT, outputChannelCount, format.sampleRate));
        return floatSupport == SINK_FORMAT_SUPPORTED_DIRECTLY && !MimeTypes.AUDIO_AC3.equals(format.sampleMimeType);
    }
}
