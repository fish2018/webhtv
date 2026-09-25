package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput;
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioOutput;
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider;
import androidx.media3.extractor.AacUtil;
import androidx.media3.extractor.MpegAudioUtil;

import com.github.catvod.crawler.SpiderDebug;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ExoCompressedAudioDirectPolicy
        implements DefaultAudioSink.AudioOffloadSupportProvider {

    private static final int VENDOR_DIRECT_BUFFER_SIZE = 256 * 1024;
    private static final long OUTPUT_STALL_CONFIRMATION_MS = 2_000;
    private static final long PCM_CONFIRMATION_MS = 2_000;
    static final long STARTUP_STALL_MS = 800;
    private static final long STARTUP_AUDIO_US = 200_000;
    private static AudioDeviceCallback processDeviceCallback;

    interface DirectPlaybackSupport {
        boolean isSupported(Format format, AudioAttributes audioAttributes);
    }

    interface VendorDirectOutputFactory {
        AudioOutput create(AudioOutputProvider.OutputConfig config)
                throws AudioOutputProvider.InitializationException;
    }

    interface OutputEnvironment {
        ExoAudioDirectFailureMemory.Route expectedRoute(AudioAttributes attributes);
        ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output);

        default StartupBuffer startupBuffer(AudioOutput output) {
            return StartupBuffer.UNKNOWN;
        }

        default StartupBuffer setStartThresholdBytes(AudioOutput output, int bytes) {
            return StartupBuffer.UNKNOWN;
        }

        default long rawPlaybackHead(AudioOutput output) {
            return C.LENGTH_UNSET;
        }
    }

    record StartupBuffer(int capacityBytes, int sizeBytes, int thresholdBytes) {
        static final StartupBuffer UNKNOWN = new StartupBuffer(0, 0, 0);

        int effectiveThresholdBytes() {
            return capacityBytes > 0 && sizeBytes > 0 && sizeBytes <= capacityBytes
                    && thresholdBytes > 0 && thresholdBytes <= capacityBytes
                    ? Math.min(sizeBytes, thresholdBytes) : 0;
        }
    }

    private static final OutputEnvironment UNKNOWN_ENVIRONMENT = new OutputEnvironment() {
        @Override public ExoAudioDirectFailureMemory.Route expectedRoute(AudioAttributes attributes) {
            return null;
        }
        @Override public ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output) {
            return null;
        }
    };

    private final DefaultAudioSink.AudioOffloadSupportProvider standardProvider;
    private final DirectPlaybackSupport directPlaybackSupport;
    private final Clock clock;
    private final VendorDirectOutputFactory vendorDirectOutputFactory;
    private final Map<DirectKey, Format> vendorDirectConfigs;
    private final Set<DirectKey> failedVendorDirectConfigs;
    private final ExoAudioDirectFailureMemory failureMemory;
    private final OutputEnvironment environment;
    private volatile boolean audioPassthroughEnabled = true;
    private final AtomicReference<OutputKey> pendingPcmFallback = new AtomicReference<>();
    private final AtomicBoolean initializationFailureNotified = new AtomicBoolean();
    private final AtomicReference<Runnable> initializationFailureListener =
            new AtomicReference<>();
    private final ExoAudioOutputState audioOutputState = new ExoAudioOutputState();
    private final AtomicReference<OutputAttempt> outputAttempt =
            new AtomicReference<>(new OutputAttempt());

    public ExoCompressedAudioDirectPolicy(Context context) {
        this(new DefaultAudioOffloadSupportProvider(context.getApplicationContext()),
                ExoCompressedAudioDirectPolicy::platformSupportsDirectPlayback,
                Clock.DEFAULT, ExoCompressedAudioDirectPolicy::createVendorDirectAudioOutput,
                ExoAudioDirectFailureMemory.process(), platformEnvironment(context));
    }

    ExoCompressedAudioDirectPolicy(
            DefaultAudioSink.AudioOffloadSupportProvider standardProvider,
            DirectPlaybackSupport directPlaybackSupport) {
        this(standardProvider, directPlaybackSupport, Clock.DEFAULT,
                ExoCompressedAudioDirectPolicy::createVendorDirectAudioOutput);
    }

    ExoCompressedAudioDirectPolicy(
            DefaultAudioSink.AudioOffloadSupportProvider standardProvider,
            DirectPlaybackSupport directPlaybackSupport,
            Clock clock,
            VendorDirectOutputFactory vendorDirectOutputFactory) {
        this(standardProvider, directPlaybackSupport, clock, vendorDirectOutputFactory,
                new ExoAudioDirectFailureMemory(), UNKNOWN_ENVIRONMENT);
    }

    ExoCompressedAudioDirectPolicy(
            DefaultAudioSink.AudioOffloadSupportProvider standardProvider,
            DirectPlaybackSupport directPlaybackSupport,
            Clock clock,
            VendorDirectOutputFactory vendorDirectOutputFactory,
            ExoAudioDirectFailureMemory failureMemory,
            OutputEnvironment environment) {
        this.standardProvider = standardProvider;
        this.directPlaybackSupport = directPlaybackSupport;
        this.clock = clock;
        this.vendorDirectOutputFactory = vendorDirectOutputFactory;
        this.vendorDirectConfigs = new ConcurrentHashMap<>();
        this.failedVendorDirectConfigs = ConcurrentHashMap.newKeySet();
        this.failureMemory = failureMemory;
        this.environment = environment;
    }

    /** Configure before exposing the renderer's output provider to the playback thread. */
    void setAudioPassthroughEnabled(boolean enabled) {
        audioPassthroughEnabled = enabled;
        if (!enabled) vendorDirectConfigs.clear();
    }

    @Override
    public AudioOffloadSupport getAudioOffloadSupport(
            Format format, AudioAttributes audioAttributes) {
        if (!audioPassthroughEnabled) return AudioOffloadSupport.DEFAULT_UNSUPPORTED;
        AudioOffloadSupport standard = standardProvider.getAudioOffloadSupport(
                format, audioAttributes);
        if (standard.isFormatSupported) {
            OutputKey key = OutputKey.from(format);
            if (key != null) vendorDirectConfigs.remove(new DirectKey(key,
                    effectiveAttributes(audioAttributes)));
            logDecision(format, key, "standard-offload");
        }
        return standard;
    }

    AudioOutputProvider wrapOutputProvider(AudioOutputProvider delegate) {
        return wrapOutputProvider(delegate, null);
    }

    AudioOutputProvider wrapOutputProvider(AudioOutputProvider delegate, ExoDiagnosticCollector diagnostics) {
        return new ForwardingAudioOutputProvider(delegate) {
            private boolean observingCapabilities;
            private final Listener capabilityListener = () -> {
                failureMemory.invalidate();
                vendorDirectConfigs.clear();
                OutputAttempt attempt = outputAttempt.get();
                attempt.cancelRecovery();
            };

            @Override
            public void addListener(Listener listener) {
                if (!observingCapabilities) {
                    super.addListener(capabilityListener);
                    observingCapabilities = true;
                }
                super.addListener(listener);
            }

            @Override
            public void release() {
                if (observingCapabilities) super.removeListener(capabilityListener);
                super.release();
            }

            @Override
            public AudioOutputProvider.FormatSupport getFormatSupport(
                    AudioOutputProvider.FormatConfig config) {
                if (!audioPassthroughEnabled && !MimeTypes.AUDIO_RAW.equals(config.format.sampleMimeType)) {
                    return AudioOutputProvider.FormatSupport.UNSUPPORTED;
                }
                AudioOutputProvider.FormatSupport standard =
                        super.getFormatSupport(config);
                OutputKey key = OutputKey.from(config.format);
                AudioAttributes attributes = effectiveAttributes(config.audioAttributes);
                DirectKey directKey = key == null ? null : new DirectKey(key, attributes);
                // Tunneling is a shared audio/video contract. This vendor-only output cannot
                // supply HW_AV_SYNC timestamps, so let Media3 choose a standard output/decoder.
                if (config.enableTunneling || key == null || !supportsEncodedFrames(key.encoding())) {
                    if (directKey != null) vendorDirectConfigs.remove(directKey);
                    return standard;
                }

                if (standard.isFormatSupportedForOffload) {
                    vendorDirectConfigs.remove(directKey);
                    return standard;
                }

                // Do not let a failed vendor-direct configuration fall through to
                // the delegate's generic passthrough/direct claim. Media3 must see
                // it as unsupported on the next selection and choose decoder + PCM.
                if (failedVendorDirectConfigs.contains(directKey)
                        || remembersFailure(config, attributes)) {
                    vendorDirectConfigs.remove(directKey);
                    logDecision(config.format, key, "vendor-direct-failed-force-pcm");
                    return standard.isFormatSupportedForOffload
                            ? standard : AudioOutputProvider.FormatSupport.UNSUPPORTED;
                }

                // A passthrough support level only means that the default provider can
                // create an encoded AudioTrack. It does not prove that the device's
                // non-standard direct path will initialize successfully. Preserve the
                // standard path only for real Media3 offload support; otherwise probe
                // and prefer the vendor-direct output when the platform advertises it.
                OutputKey resolved = resolveVendorDirect(config.format, attributes);
                if (resolved == null) return standard;
                return standard.buildUpon()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .build();
            }

            @Override
            public AudioOutputProvider.OutputConfig getOutputConfig(
                    AudioOutputProvider.FormatConfig config)
                    throws AudioOutputProvider.ConfigurationException {
                if (!audioPassthroughEnabled && !MimeTypes.AUDIO_RAW.equals(config.format.sampleMimeType)) {
                    throw new AudioOutputProvider.ConfigurationException("Audio passthrough is disabled");
                }
                OutputKey key = OutputKey.from(config.format);
                AudioAttributes attributes = effectiveAttributes(config.audioAttributes);
                if (config.enableTunneling || key == null
                        || !vendorDirectConfigs.containsKey(new DirectKey(key, attributes))) {
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
                            config.virtualDeviceId, attributes.contentType,
                            attributes.usage, attributes.flags);
                }
                return new AudioOutputProvider.OutputConfig.Builder()
                        .setEncoding(key.encoding())
                        .setSampleRate(key.sampleRate())
                        .setChannelMask(key.channelMask())
                        .setBufferSize(VENDOR_DIRECT_BUFFER_SIZE)
                        // vivo's compressed output rejects non-zero effect sessions (status -38).
                        .setAudioSessionId(0)
                        .setAudioAttributes(attributes)
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
                // Recheck the final config as well: a prior encoded capability/config must not
                // bypass a disabled setting. PCM still uses the normal output ownership wrapper.
                if (!audioPassthroughEnabled && !Util.isEncodingLinearPcm(config.encoding)) {
                    throw new AudioOutputProvider.InitializationException();
                }
                OutputAttempt attempt = outputAttempt.get();
                boolean vendorDirect = usesVendorDirect(config);
                try {
                    AudioOutput raw = vendorDirect
                            ? vendorDirectOutputFactory.create(config)
                            : super.getAudioOutput(config);
                    AudioOutput output = ExoDiagnosticAudioOutput.wrap(raw, config, diagnostics);
                    VendorDirectAudioOutput directOutput = vendorDirect
                            ? new VendorDirectAudioOutput(output, raw, config, attempt) : null;
                    attempt.output.set(directOutput);
                    if (directOutput != null) {
                        output = directOutput;
                    } else if ((attempt.recovery != null || attempt.startupRecoveryUsed)
                            && Util.isEncodingLinearPcm(config.encoding)
                            && !config.isTunneling && !config.isOffload) {
                        output = new PcmRecoveryAudioOutput(output, raw, attempt);
                    }
                    attempt.currentOutput.set(output);
                    return audioOutputState.track(output, config);
                } catch (AudioOutputProvider.InitializationException error) {
                    if (vendorDirect && outputAttempt.get() == attempt) {
                        synchronized (outputAttempt) {
                            if (outputAttempt.get() != attempt) throw error;
                            disableVendorDirect(config, "initialization");
                        }
                        notifyInitializationFailure();
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
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(config.encoding)
                    .setSampleRate(config.sampleRate)
                    .setChannelMask(config.channelMask)
                    .build();
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(config.audioAttributes.getPlatformAudioAttributes())
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
        if (!audioPassthroughEnabled) return false;
        OutputKey output = new OutputKey(encoding, sampleRate, channelMask);
        return vendorDirectConfigs.keySet().stream().anyMatch(key -> key.output.equals(output));
    }

    private boolean usesVendorDirect(AudioOutputProvider.OutputConfig config) {
        // A capability query can cache this encoding while another standard output is being
        // configured. The final output mode, not that cache, owns tunneling and offload.
        return audioPassthroughEnabled && !config.isTunneling && !config.isOffload
                && vendorDirectConfigs.containsKey(DirectKey.from(config));
    }

    public ExoAudioOutputState.Snapshot getAudioOutputSnapshot() {
        return audioOutputState.snapshot();
    }

    public boolean consumePcmFallbackRequest() {
        return pendingPcmFallback.getAndSet(null) != null;
    }

    /**
     * Registers a listener that can begin the PCM fallback without waiting for Media3's
     * delayed audio initialization error.
     */
    public void setInitializationFailureListener(Runnable listener) {
        initializationFailureListener.set(listener);
    }

    /** Forget retired output evidence without retrying a failed configuration. */
    public void resetOutputProgress() {
        synchronized (outputAttempt) {
            initializationFailureNotified.set(false);
            outputAttempt.get().cancelRecovery();
            outputAttempt.set(new OutputAttempt());
            pendingPcmFallback.set(null);
        }
    }

    /** Only the explicit audio retry can carry failure evidence into the next preparation. */
    public void prepareForPlayback(String url, boolean pcmRetry) {
        String media = ExoAudioDirectFailureMemory.mediaId(url);
        synchronized (outputAttempt) {
            initializationFailureNotified.set(false);
            OutputAttempt previous = outputAttempt.get();
            Recovery recovery = pcmRetry && media != null && media.equals(previous.media)
                    ? previous.failure : null;
            if (previous.recovery != null) previous.recovery.cancelled = true;
            if (previous.failure != null && previous.failure != recovery) previous.failure.cancelled = true;
            outputAttempt.set(new OutputAttempt(media, recovery));
            pendingPcmFallback.set(null);
        }
    }

    public void setSelectedAudioFormat(Format format) {
        synchronized (outputAttempt) {
            OutputAttempt attempt = outputAttempt.get();
            if (format == null) return;
            attempt.selectedFormat = ExoAudioDirectFailureMemory.formatId(format);
            if (attempt.recovery != null
                    && !attempt.recovery.key.format().equals(attempt.selectedFormat)) {
                attempt.recovery.cancelled = true;
            }
        }
    }

    /** Called only by the active audio renderer on its existing playback thread. */
    Format maybeRequestStartupPcmFallback(boolean rendererPlaying) {
        OutputAttempt attempt = outputAttempt.get();
        VendorDirectAudioOutput output = attempt.output.get();
        if (output == null || attempt.startupRecoveryUsed) return null;
        if (!rendererPlaying) {
            output.startupWindowAtMs = C.TIME_UNSET;
            return null;
        }
        if (!output.isStartupStalled()) return null;
        synchronized (outputAttempt) {
            if (outputAttempt.get() != attempt || attempt.currentOutput.get() != output
                    || attempt.startupRecoveryUsed || !output.playing || output.startupComplete
                    || !attempt.output.compareAndSet(output, null)) return null;
            attempt.startupRecoveryUsed = true;
            attempt.fallbackAtMs = clock.elapsedRealtime();
            rememberPendingFailure(output);
            if (outputAttempt.get() != attempt || attempt.currentOutput.get() != output) return null;
            attempt.recovery = attempt.failure;
            disableVendorDirect(output.config, "startup-no-progress");
            output.logProgress("startup-fallback", attempt.fallbackAtMs);
            return output.format;
        }
    }

    /** Keep dynamic scheduling awake only while a vendor output still needs startup validation. */
    long startupProgressIntervalUs() {
        VendorDirectAudioOutput output = outputAttempt.get().output.get();
        return output != null && output.playing && !output.startupComplete
                ? 50_000 : Long.MAX_VALUE;
    }

    public boolean requestPcmFallbackForStuckPlayback(PlaybackException error) {
        if (error == null || error.errorCode != PlaybackException.ERROR_CODE_TIMEOUT) {
            return false;
        }
        boolean stuckPlaying = false;
        Throwable cause = error.getCause();
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof StuckPlayerException stuck) {
                stuckPlaying = stuck.stuckType == StuckPlayerException.STUCK_PLAYING_NO_PROGRESS;
                break;
            }
        }
        if (!stuckPlaying) return false;
        synchronized (outputAttempt) {
            OutputAttempt attempt = outputAttempt.get();
            VendorDirectAudioOutput output = attempt.output.get();
            if (output == null || !output.stalled
                    || !attempt.output.compareAndSet(output, null)) return false;
            rememberPendingFailure(output);
            if (outputAttempt.get() != attempt) return false;
            disableVendorDirect(output.config, "playing-no-progress");
            return true;
        }
    }

    void disableVendorDirect(int encoding, int sampleRate, int channelMask) {
        disableVendorDirect(new DirectKey(new OutputKey(encoding, sampleRate, channelMask),
                        effectiveAttributes(AudioAttributes.DEFAULT)),
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
        DirectKey directKey = new DirectKey(key, audioAttributes);
        if (failedVendorDirectConfigs.contains(directKey)) {
            logDecision(format, key, "vendor-direct-failed");
            return null;
        }
        if (!directPlaybackSupport.isSupported(format, audioAttributes)) {
            vendorDirectConfigs.remove(directKey);
            logDecision(format, key, "no-direct-support");
            return null;
        }
        vendorDirectConfigs.put(directKey, format);
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
                return supportsBitstream(AudioManager.getDirectPlaybackSupport(
                        platformFormat, platformAttributes));
            }
            return AudioTrack.isDirectPlaybackSupported(
                    platformFormat, platformAttributes);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean supportsBitstream(int support) {
        return (support & AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED) != 0;
    }

    static AudioAttributes effectiveAttributes(AudioAttributes attributes) {
        // Preserve the existing vivo compatibility adjustment, but apply it before
        // querying capability and keying the decision as well as during creation.
        return attributes.contentType == C.AUDIO_CONTENT_TYPE_UNKNOWN
                ? attributes.buildUpon().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
                : attributes;
    }

    private boolean remembersFailure(AudioOutputProvider.FormatConfig config,
                                     AudioAttributes attributes) {
        OutputAttempt attempt = outputAttempt.get();
        if (config.preferredDevice != null || attempt.media == null) return false;
        long nowMs = clock.elapsedRealtime();
        if (!failureMemory.hasMedia(attempt.media, nowMs)) return false;
        ExoAudioDirectFailureMemory.Route route = environment.expectedRoute(attributes);
        if (route == null) return false;
        return failureMemory.contains(new ExoAudioDirectFailureMemory.Key(attempt.media,
                ExoAudioDirectFailureMemory.formatId(config.format), attributes, route), nowMs);
    }

    private void rememberPendingFailure(VendorDirectAudioOutput output) {
        OutputAttempt attempt = output.attempt;
        if (outputAttempt.get() != attempt || attempt.media == null || output.format == null
                || output.routeAtStall == null) return;
        ExoAudioDirectFailureMemory.Route expected =
                environment.expectedRoute(output.config.audioAttributes);
        if (!output.routeAtStall.equals(expected)) return;
        attempt.failure = new Recovery(new ExoAudioDirectFailureMemory.Key(attempt.media,
                ExoAudioDirectFailureMemory.formatId(output.format),
                output.config.audioAttributes, expected), failureMemory.generation());
    }

    private static OutputEnvironment platformEnvironment(Context context) {
        AudioManager manager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        return new OutputEnvironment() {
            @Override
            public ExoAudioDirectFailureMemory.Route expectedRoute(AudioAttributes attributes) {
                if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
                try {
                    if (manager.getMode() != AudioManager.MODE_NORMAL) return null;
                    if (!observeDeviceChanges(manager)) return null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        List<AudioDeviceInfo> devices = manager.getAudioDevicesForAttributes(
                                attributes.getPlatformAudioAttributes());
                        return devices.size() == 1 ? route(devices.get(0)) : null;
                    }
                    // Older APIs cannot predict the attribute-specific route. Only an
                    // unambiguous sole output is safe; never guess among HDMI/BT/speakers.
                    AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    return devices.length == 1 ? route(devices[0]) : null;
                } catch (RuntimeException unavailable) {
                    return null;
                }
            }

            @Override
            public ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output) {
                if (!(output instanceof AudioTrackAudioOutput track)) return null;
                try {
                    return route(track.getAudioTrack().getRoutedDevice());
                } catch (RuntimeException unavailable) {
                    return null;
                }
            }

            @Override
            public StartupBuffer startupBuffer(AudioOutput output) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || !(output instanceof AudioTrackAudioOutput track)) return StartupBuffer.UNKNOWN;
                try {
                    AudioTrack audioTrack = track.getAudioTrack();
                    return new StartupBuffer(audioTrack.getBufferCapacityInFrames(),
                            audioTrack.getBufferSizeInFrames(), audioTrack.getStartThresholdInFrames());
                } catch (RuntimeException unavailable) {
                    return StartupBuffer.UNKNOWN;
                }
            }

            @Override
            public StartupBuffer setStartThresholdBytes(AudioOutput output, int bytes) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || !(output instanceof AudioTrackAudioOutput track)) return StartupBuffer.UNKNOWN;
                try {
                    // Compressed AudioTrack's "frames" are bytes. Do not shrink the steady buffer:
                    // setBufferSizeInFrames is PCM-only, whereas this API controls startup fill.
                    track.getAudioTrack().setStartThresholdInFrames(bytes);
                    return startupBuffer(output);
                } catch (RuntimeException unavailable) {
                    return StartupBuffer.UNKNOWN;
                }
            }

            @Override
            public long rawPlaybackHead(AudioOutput output) {
                if (!(output instanceof AudioTrackAudioOutput track)) return C.LENGTH_UNSET;
                try {
                    return track.getAudioTrack().getPlaybackHeadPosition() & 0xffffffffL;
                } catch (RuntimeException unavailable) {
                    return C.LENGTH_UNSET;
                }
            }
        };
    }

    private static ExoAudioDirectFailureMemory.Route route(AudioDeviceInfo device) {
        if (device == null || !device.isSink()) return null;
        return new ExoAudioDirectFailureMemory.Route(device.getId(), device.getType(),
                Arrays.hashCode(device.getEncodings()), Arrays.hashCode(device.getSampleRates()),
                Arrays.hashCode(device.getChannelMasks()));
    }

    private static synchronized boolean observeDeviceChanges(AudioManager manager) {
        if (processDeviceCallback != null) return true;
        // Register lazily on the first failure/lookup, on the existing main Looper.
        // The callback retains only port IDs and the process memory, never an engine.
        int[] initialIds = outputDeviceIds(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        AudioDeviceCallback callback = new AudioDeviceCallback() {
            private boolean initial = true;

            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) {
                int[] ids = outputDeviceIds(devices);
                if (initial) {
                    initial = false;
                    // Registration reports already connected devices, not a route change.
                    if (Arrays.equals(initialIds, ids)) return;
                }
                if (ids.length != 0) ExoAudioDirectFailureMemory.process().invalidate();
            }

            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
                initial = false;
                if (outputDeviceIds(devices).length != 0) ExoAudioDirectFailureMemory.process().invalidate();
            }
        };
        manager.registerAudioDeviceCallback(callback, null);
        processDeviceCallback = callback;
        return true;
    }

    private static int[] outputDeviceIds(AudioDeviceInfo[] devices) {
        return Arrays.stream(devices).filter(AudioDeviceInfo::isSink)
                .mapToInt(AudioDeviceInfo::getId).sorted().toArray();
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
        disableVendorDirect(DirectKey.from(config), reason);
    }

    private void disableVendorDirect(DirectKey key, String reason) {
        vendorDirectConfigs.remove(key);
        failedVendorDirectConfigs.add(key);
        pendingPcmFallback.set(key.output);
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("exo-audio-direct",
                    "disable encoding=%d sampleRate=%d channelMask=0x%X reason=%s",
                    key.output.encoding(), key.output.sampleRate(), key.output.channelMask(), reason);
        }
    }

    private void notifyInitializationFailure() {
        if (!initializationFailureNotified.compareAndSet(false, true)) return;
        Runnable listener = initializationFailureListener.get();
        if (listener != null) listener.run();
    }

    private static final class OutputAttempt {
        final AtomicReference<VendorDirectAudioOutput> output = new AtomicReference<>();
        final AtomicReference<AudioOutput> currentOutput = new AtomicReference<>();
        final String media;
        volatile Recovery recovery;
        volatile Recovery failure;
        volatile String selectedFormat;
        boolean startupRecoveryUsed;
        long fallbackAtMs = C.TIME_UNSET;

        OutputAttempt() {
            this(null, null);
        }

        OutputAttempt(String media, Recovery recovery) {
            this.media = media;
            this.recovery = recovery;
        }

        void cancelRecovery() {
            if (recovery != null) recovery.cancelled = true;
            if (failure != null) failure.cancelled = true;
        }
    }

    private static final class Recovery {
        final ExoAudioDirectFailureMemory.Key key;
        final long generation;
        volatile boolean cancelled;

        Recovery(ExoAudioDirectFailureMemory.Key key, long generation) {
            this.key = key;
            this.generation = generation;
        }
    }

    private record DirectKey(OutputKey output, AudioAttributes attributes) {
        static DirectKey from(AudioOutputProvider.OutputConfig config) {
            return new DirectKey(new OutputKey(config.encoding, config.sampleRate, config.channelMask),
                    config.audioAttributes);
        }
    }

    private final class VendorDirectAudioOutput extends ForwardingAudioOutput {
        private final AudioOutputProvider.OutputConfig config;
        private final AudioOutput raw;
        private final OutputAttempt attempt;
        private final Format format;
        private StartupBuffer startupBuffer;
        private boolean thresholdAdjusted;
        private boolean startupComplete;
        private int framesPerAccessUnit;
        private long acceptedFrames;
        private long startupWindowAtMs = C.TIME_UNSET;
        private long startupGeneration;
        private ExoAudioDirectFailureMemory.Route startupRoute;
        private boolean playing;
        private boolean acceptedData;
        private long acceptedBytes;
        private long playAtMs = C.TIME_UNSET;
        private long firstWriteAtMs = C.TIME_UNSET;
        private long firstProgressAtMs = C.TIME_UNSET;
        private long lastPositionUs = C.TIME_UNSET;
        private long unchangedSinceMs = C.TIME_UNSET;
        private volatile boolean stalled;
        private ExoAudioDirectFailureMemory.Route routeAtStall;

        VendorDirectAudioOutput(AudioOutput output, AudioOutput raw,
                                AudioOutputProvider.OutputConfig config, OutputAttempt attempt) {
            super(output);
            this.config = config;
            this.raw = raw;
            this.attempt = attempt;
            this.format = vendorDirectConfigs.get(DirectKey.from(config));
            this.startupBuffer = environment.startupBuffer(raw);
            if (SpiderDebug.isEnabled()) {
                SpiderDebug.log("exo-audio-direct",
                        "event=created encoding=%d capacityBytes=%d sizeBytes=%d startThresholdBytes=%d effectiveThresholdBytes=%d",
                        config.encoding, startupBuffer.capacityBytes(), startupBuffer.sizeBytes(),
                        startupBuffer.thresholdBytes(), startupBuffer.effectiveThresholdBytes());
            }
        }

        @Override
        public boolean write(ByteBuffer buffer, int accessUnitCount, long presentationTimeUs)
                throws AudioOutput.WriteException {
            int position = buffer.position();
            if (!startupComplete && framesPerAccessUnit == 0 && buffer.hasRemaining()) {
                framesPerAccessUnit = startupFramesPerAccessUnit(config.encoding, buffer);
            }
            try {
                boolean handled = super.write(buffer, accessUnitCount, presentationTimeUs);
                if (buffer.position() > position) {
                    acceptedData = true;
                    acceptedBytes += buffer.position() - position;
                    if (firstWriteAtMs == C.TIME_UNSET) {
                        firstWriteAtMs = clock.elapsedRealtime();
                        logProgress("first-write", firstWriteAtMs);
                    }
                    if (!startupComplete && handled && accessUnitCount > 0) {
                        acceptedFrames += (long) framesPerAccessUnit * accessUnitCount;
                        maybeLowerStartThreshold();
                    }
                }
                return handled;
            } catch (AudioOutput.WriteException error) {
                synchronized (outputAttempt) {
                    if (outputAttempt.get() == attempt && attempt.currentOutput.get() == this) {
                        routeAtStall = environment.actualRoute(raw);
                        rememberPendingFailure(this);
                        if (outputAttempt.get() == attempt) {
                            disableVendorDirect(config, "write-" + error.errorCode);
                        }
                    }
                }
                throw new AudioOutput.WriteException(error.errorCode, true);
            }
        }

        @Override
        public long getPositionUs() {
            long positionUs = super.getPositionUs();
            if (playing && acceptedData) {
                if (positionUs < 0) {
                    resetObservation();
                } else {
                    long nowMs = clock.elapsedRealtime();
                    if (positionUs > 0 && firstProgressAtMs == C.TIME_UNSET) {
                        firstProgressAtMs = nowMs;
                        logProgress("first-progress", nowMs);
                    }
                    if (positionUs > 0) startupComplete = true;
                    if (positionUs != lastPositionUs) {
                        lastPositionUs = positionUs;
                        unchangedSinceMs = nowMs;
                        stalled = false;
                        routeAtStall = null;
                    } else {
                        boolean observedStall = nowMs - unchangedSinceMs >= OUTPUT_STALL_CONFIRMATION_MS;
                        if (observedStall && !stalled && outputAttempt.get() == attempt) {
                            routeAtStall = environment.actualRoute(raw);
                        }
                        stalled = observedStall;
                    }
                }
            }
            return positionUs;
        }

        @Override
        public void play() {
            super.play();
            if (!playing) resetObservation();
            playing = true;
            if (playAtMs == C.TIME_UNSET) playAtMs = clock.elapsedRealtime();
        }

        @Override
        public void pause() {
            playing = false;
            startupWindowAtMs = C.TIME_UNSET;
            super.pause();
        }

        @Override
        public void flush() {
            acceptedData = false;
            acceptedBytes = 0;
            acceptedFrames = 0;
            // A seek/flush is not a fresh first-playback probe. Keep the established generic
            // stall recovery for these epochs instead of replaying stale startup evidence.
            startupComplete = true;
            resetObservation();
            super.flush();
        }

        @Override
        public void stop() {
            playing = false;
            acceptedData = false;
            acceptedBytes = 0;
            acceptedFrames = 0;
            startupComplete = true;
            resetObservation();
            super.stop();
        }

        @Override
        public void release() {
            playing = false;
            startupWindowAtMs = C.TIME_UNSET;
            // Media3 posts stop to the playback thread before notifying the App of a timeout.
            // Keep already observed evidence for that error; the next output/attempt replaces it.
            super.release();
        }

        private void resetObservation() {
            lastPositionUs = C.TIME_UNSET;
            unchangedSinceMs = C.TIME_UNSET;
            stalled = false;
            routeAtStall = null;
            startupWindowAtMs = C.TIME_UNSET;
        }

        private void maybeLowerStartThreshold() {
            if (thresholdAdjusted || config.sampleRate <= 0
                    || acceptedFrames * 1_000_000L < STARTUP_AUDIO_US * config.sampleRate) return;
            thresholdAdjusted = true;
            int threshold = startupBuffer.effectiveThresholdBytes();
            if (threshold == 0 || acceptedBytes <= 0 || acceptedBytes >= threshold) return;
            StartupBuffer adjusted = environment.setStartThresholdBytes(raw, (int) acceptedBytes);
            // Read-back is the only authority, including a vendor's clamping of the request.
            if (adjusted.effectiveThresholdBytes() > 0) startupBuffer = adjusted;
        }

        private boolean isStartupStalled() {
            if (!playing || startupComplete || format == null || lastPositionUs != 0
                    || !acceptedData || startupBuffer.effectiveThresholdBytes() == 0
                    || acceptedBytes < startupBuffer.effectiveThresholdBytes()) {
                startupWindowAtMs = C.TIME_UNSET;
                return false;
            }
            long nowMs = clock.elapsedRealtime();
            if (startupWindowAtMs == C.TIME_UNSET) {
                startupWindowAtMs = nowMs;
                startupGeneration = failureMemory.generation();
                startupRoute = environment.actualRoute(raw);
                return false;
            }
            if (nowMs - startupWindowAtMs < STARTUP_STALL_MS) return false;
            // Query the raw head and route only at the decision boundary, not every render.
            // A working head with stale timestamp feedback must not be replayed as silent audio.
            long head = environment.rawPlaybackHead(raw);
            if (head > 0) {
                startupComplete = true;
                return false;
            }
            ExoAudioDirectFailureMemory.Route route = environment.actualRoute(raw);
            if (head < 0 || startupRoute == null || !startupRoute.equals(route)
                    || startupGeneration != failureMemory.generation()) {
                startupComplete = true;
                return false;
            }
            // Re-read the real threshold: routing/HAL changes can alter it after construction.
            StartupBuffer current = environment.startupBuffer(raw);
            if (current.effectiveThresholdBytes() == 0
                    || acceptedBytes < current.effectiveThresholdBytes()) {
                startupBuffer = current;
                startupWindowAtMs = C.TIME_UNSET;
                return false;
            }
            routeAtStall = route;
            return true;
        }

        private void logProgress(String event, long nowMs) {
            if (!SpiderDebug.isEnabled()) return;
            SpiderDebug.log("exo-audio-direct",
                    "event=%s encoding=%d sincePlayMs=%d sinceFirstWriteMs=%d acceptedBytes=%d",
                    event, config.encoding, playAtMs == C.TIME_UNSET ? -1 : nowMs - playAtMs,
                    firstWriteAtMs == C.TIME_UNSET ? -1 : nowMs - firstWriteAtMs, acceptedBytes);
        }
    }

    // Same frame counts as the shipped DefaultAudioSink; inspect only the first complete header.
    private static int startupFramesPerAccessUnit(int encoding, ByteBuffer buffer) {
        return switch (encoding) {
            case C.ENCODING_AAC_LC -> AacUtil.AAC_LC_AUDIO_SAMPLE_COUNT;
            case C.ENCODING_AAC_HE_V1, C.ENCODING_AAC_HE_V2 -> AacUtil.AAC_HE_AUDIO_SAMPLE_COUNT;
            case C.ENCODING_AAC_XHE -> AacUtil.AAC_XHE_AUDIO_SAMPLE_COUNT;
            case C.ENCODING_AAC_ELD -> AacUtil.AAC_LD_AUDIO_SAMPLE_COUNT;
            case C.ENCODING_MP3 -> buffer.remaining() < 4 ? 0
                    : Math.max(0, MpegAudioUtil.parseMpegAudioFrameSampleCount(
                            Util.getBigEndianInt(buffer, buffer.position())));
            default -> 0;
        };
    }

    /** Installed only for PCM recovery, never on healthy PCM playback. */
    private final class PcmRecoveryAudioOutput extends ForwardingAudioOutput {
        private final AudioOutput raw;
        private final OutputAttempt attempt;
        private final Recovery recovery;
        private boolean playing;
        private boolean acceptedData;
        private boolean finished;
        private boolean reportedProgress;
        private long windowAtMs = C.TIME_UNSET;
        private long windowPositionUs;
        private long lastPositionUs;
        private long lastProgressAtMs;

        PcmRecoveryAudioOutput(AudioOutput output, AudioOutput raw, OutputAttempt attempt) {
            super(output);
            this.raw = raw;
            this.attempt = attempt;
            this.recovery = attempt.recovery;
        }

        @Override
        public boolean write(ByteBuffer buffer, int accessUnitCount, long presentationTimeUs)
                throws AudioOutput.WriteException {
            int position = buffer.position();
            boolean result = super.write(buffer, accessUnitCount, presentationTimeUs);
            acceptedData |= buffer.position() > position;
            return result;
        }

        @Override
        public long getPositionUs() {
            long positionUs = super.getPositionUs();
            if (finished || !playing || !acceptedData || (recovery != null && recovery.cancelled)
                    || outputAttempt.get() != attempt || attempt.currentOutput.get() != this) {
                return positionUs;
            }
            long nowMs = clock.elapsedRealtime();
            if (!reportedProgress && positionUs > 0) {
                reportedProgress = true;
                synchronized (outputAttempt) {
                    if (outputAttempt.get() == attempt && attempt.currentOutput.get() == this) {
                        pendingPcmFallback.set(null);
                    }
                }
                if (attempt.fallbackAtMs != C.TIME_UNSET && SpiderDebug.isEnabled()) {
                    SpiderDebug.log("exo-audio-direct", "event=pcm-first-progress sinceFallbackMs=%d",
                            nowMs - attempt.fallbackAtMs);
                }
            }
            if (recovery == null) {
                finished = reportedProgress;
                return positionUs;
            }
            if (positionUs < 0 || (windowAtMs != C.TIME_UNSET && positionUs < lastPositionUs)) {
                recovery.cancelled = true;
                return positionUs;
            }
            if (windowAtMs == C.TIME_UNSET || nowMs - lastProgressAtMs >= PCM_CONFIRMATION_MS) {
                windowAtMs = nowMs;
                windowPositionUs = positionUs;
                lastProgressAtMs = nowMs;
            }
            if (positionUs > lastPositionUs) lastProgressAtMs = nowMs;
            lastPositionUs = positionUs;
            if (nowMs - windowAtMs >= PCM_CONFIRMATION_MS
                    && positionUs - windowPositionUs >= 1_000_000
                    && nowMs == lastProgressAtMs
                    && recovery.key.format().equals(attempt.selectedFormat)) {
                finished = true;
                ExoAudioDirectFailureMemory.Route actual = environment.actualRoute(raw);
                ExoAudioDirectFailureMemory.Route expected =
                        environment.expectedRoute(recovery.key.attributes());
                if (Objects.equals(recovery.key.route(), actual)
                        && Objects.equals(actual, expected)
                        && confirmRecovery(nowMs)
                        && SpiderDebug.isEnabled()) {
                    SpiderDebug.log("exo-audio-direct",
                            "event=pcm-recovery-confirmed stableMs=%d progressUs=%d ttlMs=%d",
                            nowMs - windowAtMs, positionUs - windowPositionUs,
                            ExoAudioDirectFailureMemory.TTL_MS);
                }
            }
            return positionUs;
        }

        private boolean confirmRecovery(long nowMs) {
            synchronized (outputAttempt) {
                return outputAttempt.get() == attempt && attempt.currentOutput.get() == this
                        && !recovery.cancelled && recovery.key.format().equals(attempt.selectedFormat)
                        && failureMemory.confirm(recovery.key, recovery.generation, nowMs);
            }
        }

        @Override public void play() {
            super.play();
            if (!playing) windowAtMs = C.TIME_UNSET;
            playing = true;
        }

        @Override public void pause() {
            playing = false;
            windowAtMs = C.TIME_UNSET;
            super.pause();
        }

        @Override public void flush() {
            if (recovery != null) recovery.cancelled = true;
            super.flush();
        }

        @Override public void stop() {
            if (recovery != null) recovery.cancelled = true;
            super.stop();
        }

        @Override public void release() {
            if (recovery != null) recovery.cancelled = true;
            super.release();
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
