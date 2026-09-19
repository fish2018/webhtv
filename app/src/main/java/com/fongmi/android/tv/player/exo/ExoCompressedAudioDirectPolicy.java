package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput;
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioOutput;
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider;

import com.github.catvod.crawler.SpiderDebug;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ExoCompressedAudioDirectPolicy
        implements DefaultAudioSink.AudioOffloadSupportProvider {

    private static final int VENDOR_DIRECT_BUFFER_SIZE = 256 * 1024;

    interface DirectPlaybackSupport {
        boolean isSupported(Format format, AudioAttributes audioAttributes);
    }

    private final DefaultAudioSink.AudioOffloadSupportProvider standardProvider;
    private final DirectPlaybackSupport directPlaybackSupport;
    private final Set<OutputKey> vendorDirectConfigs;
    private final Set<OutputKey> failedVendorDirectConfigs;
    private final AtomicReference<OutputKey> pendingPcmFallback = new AtomicReference<>();
    private final ExoAudioOutputState audioOutputState = new ExoAudioOutputState();

    public ExoCompressedAudioDirectPolicy(Context context) {
        this(new DefaultAudioOffloadSupportProvider(context.getApplicationContext()),
                ExoCompressedAudioDirectPolicy::platformSupportsDirectPlayback);
    }

    ExoCompressedAudioDirectPolicy(
            DefaultAudioSink.AudioOffloadSupportProvider standardProvider,
            DirectPlaybackSupport directPlaybackSupport) {
        this.standardProvider = standardProvider;
        this.directPlaybackSupport = directPlaybackSupport;
        this.vendorDirectConfigs = ConcurrentHashMap.newKeySet();
        this.failedVendorDirectConfigs = ConcurrentHashMap.newKeySet();
    }

    @Override
    public AudioOffloadSupport getAudioOffloadSupport(
            Format format, AudioAttributes audioAttributes) {
        AudioOffloadSupport standard = standardProvider.getAudioOffloadSupport(
                format, audioAttributes);
        if (standard.isFormatSupported) {
            OutputKey key = OutputKey.from(format);
            if (key != null) vendorDirectConfigs.remove(key);
            logDecision(format, key, "standard-offload");
        }
        return standard;
    }

    AudioOutputProvider wrapOutputProvider(AudioOutputProvider delegate) {
        return wrapOutputProvider(delegate, null);
    }

    AudioOutputProvider wrapOutputProvider(AudioOutputProvider delegate, ExoDiagnosticCollector diagnostics) {
        return new ForwardingAudioOutputProvider(delegate) {
            @Override
            public AudioOutputProvider.FormatSupport getFormatSupport(
                    AudioOutputProvider.FormatConfig config) {
                AudioOutputProvider.FormatSupport standard =
                        super.getFormatSupport(config);
                OutputKey key = OutputKey.from(config.format);
                // Tunneling is a shared audio/video contract. This vendor-only output cannot
                // supply HW_AV_SYNC timestamps, so let Media3 choose a standard output/decoder.
                if (config.enableTunneling || key == null || !supportsEncodedFrames(key.encoding())) {
                    if (key != null) vendorDirectConfigs.remove(key);
                    return standard;
                }

                // Do not let a failed vendor-direct configuration fall through to
                // the delegate's generic passthrough/direct claim. Media3 must see
                // it as unsupported on the next selection and choose decoder + PCM.
                if (failedVendorDirectConfigs.contains(key)) {
                    vendorDirectConfigs.remove(key);
                    logDecision(config.format, key, "vendor-direct-failed-force-pcm");
                    return standard.isFormatSupportedForOffload
                            ? standard : AudioOutputProvider.FormatSupport.UNSUPPORTED;
                }

                // A passthrough support level only means that the default provider can
                // create an encoded AudioTrack. It does not prove that the device's
                // non-standard direct path will initialize successfully. Preserve the
                // standard path only for real Media3 offload support; otherwise probe
                // and prefer the vendor-direct output when the platform advertises it.
                if (standard.isFormatSupportedForOffload) {
                    vendorDirectConfigs.remove(key);
                    return standard;
                }
                OutputKey directKey = resolveVendorDirect(config.format,
                        config.audioAttributes);
                if (directKey == null) return standard;
                return standard.buildUpon()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .build();
            }

            @Override
            public AudioOutputProvider.OutputConfig getOutputConfig(
                    AudioOutputProvider.FormatConfig config)
                    throws AudioOutputProvider.ConfigurationException {
                OutputKey key = OutputKey.from(config.format);
                if (config.enableTunneling || key == null || !vendorDirectConfigs.contains(key)) {
                    try {
                        return super.getOutputConfig(config);
                    } catch (RuntimeException error) {
                        // AudioTrackAudioOutputProvider currently lets an invalid
                        // channel mask escape as IllegalStateException from
                        // getMinBufferSize(). Convert it to the provider contract
                        // so Media3 reports a recoverable audio-track init error
                        // instead of ERROR_CODE_FAILED_RUNTIME_CHECK.
                        throw new AudioOutputProvider.ConfigurationException(
                                "AudioTrack output configuration failed: "
                                        + error.getMessage());
                    }
                }
                if (SpiderDebug.isEnabled()) {
                    SpiderDebug.log("exo-audio-direct",
                            "config encoding=%d sampleRate=%d channelMask=0x%X session=%d tunneling=%s virtualDevice=%d attrs=%d/%d/%d",
                            key.encoding(), key.sampleRate(), key.channelMask(),
                            config.audioSessionId, config.enableTunneling,
                            config.virtualDeviceId, config.audioAttributes.contentType,
                            config.audioAttributes.usage, config.audioAttributes.flags);
                }
                return new AudioOutputProvider.OutputConfig.Builder()
                        .setEncoding(key.encoding())
                        .setSampleRate(key.sampleRate())
                        .setChannelMask(key.channelMask())
                        .setBufferSize(VENDOR_DIRECT_BUFFER_SIZE)
                        // vivo's compressed output rejects non-zero effect sessions (status -38).
                        .setAudioSessionId(0)
                        .setAudioAttributes(config.audioAttributes)
                        .setIsOffload(false)
                        // Compressed direct tracks cannot use the HW_AV_SYNC tunneling attributes
                        // on the target HAL; keep tunneling for the normal PCM/offload path.
                        .setIsTunneling(false)
                        .setUsePlaybackParameters(
                                config.enablePlaybackParameters)
                        .setUseOffloadGapless(false)
                        // The target HAL rejects compressed tracks when Media3 attaches a
                        // virtual-device Context, including the default device id 0.
                        .setVirtualDeviceId(C.INDEX_UNSET)
                        .build();
            }

            @Override
            public AudioOutput getAudioOutput(AudioOutputProvider.OutputConfig config)
                    throws AudioOutputProvider.InitializationException {
                boolean vendorDirect = usesVendorDirect(config);
                try {
                    AudioOutput output = vendorDirect
                            ? createVendorDirectAudioOutput(config)
                            : super.getAudioOutput(config);
                    output = ExoDiagnosticAudioOutput.wrap(output, config, diagnostics);
                    if (vendorDirect) output = new ForwardingAudioOutput(output) {
                        @Override
                        public boolean write(ByteBuffer buffer, int accessUnitCount,
                                             long presentationTimeUs)
                                throws AudioOutput.WriteException {
                            try {
                                return super.write(buffer, accessUnitCount,
                                        presentationTimeUs);
                            } catch (AudioOutput.WriteException error) {
                                disableVendorDirect(config, "write-" + error.errorCode);
                                throw new AudioOutput.WriteException(
                                        error.errorCode, true);
                            }
                        }
                    };
                    return audioOutputState.track(output, config);
                } catch (AudioOutputProvider.InitializationException error) {
                    if (vendorDirect) {
                        disableVendorDirect(config, "initialization");
                    }
                    throw error;
                }
            }
        };
    }

    private static AudioOutput createVendorDirectAudioOutput(
            AudioOutputProvider.OutputConfig config)
            throws AudioOutputProvider.InitializationException {
        AudioTrack audioTrack = null;
        try {
            int contentType = config.audioAttributes.contentType;
            // vivo's compressed direct profile rejects CONTENT_TYPE_UNKNOWN even though
            // the same encoding is advertised by getDirectPlaybackSupport(). Treat media
            // playback as music for this vendor-only bitstream path; PCM/offload keeps
            // the caller's original Media3 attributes.
            if (contentType == android.media.AudioAttributes.CONTENT_TYPE_UNKNOWN) {
                contentType = android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
            }
            android.media.AudioAttributes.Builder attributesBuilder =
                    new android.media.AudioAttributes.Builder()
                            .setContentType(contentType)
                            .setFlags(config.audioAttributes.flags)
                            .setUsage(config.audioAttributes.usage);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                attributesBuilder
                        .setAllowedCapturePolicy(
                                config.audioAttributes.allowedCapturePolicy)
                        .setHapticChannelsMuted(
                                config.audioAttributes.hapticChannelsMuted);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                attributesBuilder
                        .setSpatializationBehavior(
                                config.audioAttributes.spatializationBehavior)
                        .setIsContentSpatialized(
                                config.audioAttributes.isContentSpatialized);
            }
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(config.encoding)
                    .setSampleRate(config.sampleRate)
                    .setChannelMask(config.channelMask)
                    .build();
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(attributesBuilder.build())
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(config.bufferSize)
                    .setSessionId(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setOffloadedPlayback(false);
            }
            audioTrack = builder.build();
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                audioTrack.release();
                throw new AudioOutputProvider.InitializationException();
            }
            return new AudioTrackAudioOutput(
                    audioTrack, config, null, Clock.DEFAULT);
        } catch (AudioOutputProvider.InitializationException error) {
            throw error;
        } catch (RuntimeException error) {
            if (audioTrack != null) audioTrack.release();
            throw new AudioOutputProvider.InitializationException(error);
        }
    }

    void modifyAudioTrackBuilder(
            AudioTrack.Builder builder, AudioOutputProvider.OutputConfig config) {
        if (!usesVendorDirect(config)) return;
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("exo-audio-direct",
                    "builder encoding=%d sampleRate=%d channelMask=0x%X directSession=0",
                    config.encoding, config.sampleRate, config.channelMask);
        }
        builder.setBufferSizeInBytes(VENDOR_DIRECT_BUFFER_SIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setOffloadedPlayback(false);
        }
    }

    boolean usesVendorDirect(int encoding, int sampleRate, int channelMask) {
        return vendorDirectConfigs.contains(new OutputKey(encoding, sampleRate,
                channelMask));
    }

    private boolean usesVendorDirect(AudioOutputProvider.OutputConfig config) {
        // A capability query can cache this encoding while another standard output is being
        // configured. The final output mode, not that cache, owns tunneling and offload.
        return !config.isTunneling && !config.isOffload
                && usesVendorDirect(config.encoding, config.sampleRate, config.channelMask);
    }

    public ExoAudioOutputState.Snapshot getAudioOutputSnapshot() {
        return audioOutputState.snapshot();
    }

    public boolean consumePcmFallbackRequest() {
        return pendingPcmFallback.getAndSet(null) != null;
    }

    void disableVendorDirect(int encoding, int sampleRate, int channelMask) {
        disableVendorDirect(new OutputKey(encoding, sampleRate, channelMask),
                "test");
    }

    static boolean supportsEncodedFrames(int encoding) {
        return switch (encoding) {
            case C.ENCODING_MP3,
                    C.ENCODING_AAC_LC,
                    C.ENCODING_AAC_HE_V1,
                    C.ENCODING_AAC_HE_V2,
                    C.ENCODING_AAC_XHE,
                    C.ENCODING_AAC_ELD -> true;
            default -> false;
        };
    }

    private OutputKey resolveVendorDirect(
            Format format, AudioAttributes audioAttributes) {
        OutputKey key = OutputKey.from(format);
        if (key == null || !supportsEncodedFrames(key.encoding())) {
            logDecision(format, key, "unsupported-encoding");
            return null;
        }
        if (failedVendorDirectConfigs.contains(key)) {
            logDecision(format, key, "vendor-direct-failed");
            return null;
        }
        if (!directPlaybackSupport.isSupported(format, audioAttributes)) {
            vendorDirectConfigs.remove(key);
            logDecision(format, key, "no-direct-support");
            return null;
        }
        vendorDirectConfigs.add(key);
        logDecision(format, key, "vendor-direct");
        return key;
    }

    private static boolean platformSupportsDirectPlayback(
            Format format, AudioAttributes audioAttributes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        OutputKey key = OutputKey.from(format);
        if (key == null) return false;
        try {
            AudioFormat platformFormat = new AudioFormat.Builder()
                    .setEncoding(key.encoding())
                    .setSampleRate(key.sampleRate())
                    .setChannelMask(key.channelMask())
                    .build();
            android.media.AudioAttributes platformAttributes =
                    audioAttributes.getPlatformAudioAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return AudioManager.getDirectPlaybackSupport(
                        platformFormat, platformAttributes) != 0;
            }
            return AudioTrack.isDirectPlaybackSupported(
                    platformFormat, platformAttributes);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logDecision(Format format, OutputKey key, String reason) {
        if (!SpiderDebug.isEnabled()) return;
        SpiderDebug.log("exo-audio-direct",
                "mime=%s codecs=%s encoding=%d sampleRate=%d channels=%d reason=%s",
                format == null ? "" : format.sampleMimeType,
                format == null ? "" : format.codecs,
                key == null ? C.ENCODING_INVALID : key.encoding(),
                key == null ? 0 : key.sampleRate(),
                format == null ? 0 : Math.max(0, format.channelCount), reason);
    }

    private void disableVendorDirect(
            AudioOutputProvider.OutputConfig config, String reason) {
        disableVendorDirect(new OutputKey(config.encoding, config.sampleRate,
                config.channelMask), reason);
    }

    private void disableVendorDirect(OutputKey key, String reason) {
        vendorDirectConfigs.remove(key);
        failedVendorDirectConfigs.add(key);
        pendingPcmFallback.set(key);
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("exo-audio-direct",
                    "disable encoding=%d sampleRate=%d channelMask=0x%X reason=%s",
                    key.encoding(), key.sampleRate(), key.channelMask(), reason);
        }
    }

    private record OutputKey(int encoding, int sampleRate, int channelMask) {

        static OutputKey from(Format format) {
            if (format == null || format.sampleMimeType == null
                    || format.sampleRate <= 0 || format.channelCount <= 0) {
                return null;
            }
            int encoding = MimeTypes.getEncoding(format.sampleMimeType,
                    format.codecs);
            int channelMask = Util.getAudioTrackChannelConfig(format);
            if (encoding == C.ENCODING_INVALID || channelMask == 0) return null;
            return new OutputKey(encoding, format.sampleRate, channelMask);
        }
    }
}
