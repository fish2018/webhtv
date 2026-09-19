package com.fongmi.android.tv.player.exo;

import android.media.AudioDeviceInfo;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.Build;
import android.os.SystemClock;

import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput;
import androidx.media3.exoplayer.audio.ForwardingAudioOutput;
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Observes the real provider result, including vendor-direct, without replacing its implementation. */
final class ExoDiagnosticAudioOutput extends ForwardingAudioOutput {
    private final PlaybackDiagnosticCollector log;
    private final Context owner;
    private final AudioTrack track;
    private final String outputId = PlaybackDiagnosticCollector.id("audio-output");
    private long epoch, calls, requested, accepted, shorts, zeros, errors, maxWriteUs, lastSummaryMs, lastWriteMs;
    private long rawHead = -1, extendedHead, lastTimestampMs;
    private long firstPts = Long.MIN_VALUE, lastPts = Long.MIN_VALUE;
    private final com.fongmi.android.tv.player.PcmDiagnosticProbe pcmProbe = new com.fongmi.android.tv.player.PcmDiagnosticProbe();
    private final int pcmEncoding, pcmChannels;
    private final com.fongmi.android.tv.player.OutputProgressDiagnostic audioProgress = new com.fongmi.android.tv.player.OutputProgressDiagnostic(false);

    static AudioOutput wrap(AudioOutput output, AudioOutputProvider.OutputConfig config, ExoDiagnosticCollector collector) {
        return collector == null ? output : new ExoDiagnosticAudioOutput(output, config, collector);
    }

    static AudioOutputProvider provider(AudioOutputProvider delegate, ExoDiagnosticCollector collector) {
        if (collector == null) return delegate;
        return new ForwardingAudioOutputProvider(delegate) {
            @Override public FormatSupport getFormatSupport(FormatConfig config) {
                FormatSupport result = super.getFormatSupport(config);
                collector.log.emit("audio.candidates", "audio-output-provider", e -> {
                    ExoDiagnosticCollector.format(e, config.format);
                    e.observed("support", result.supportLevel).observed("offload", result.isFormatSupportedForOffload);
                });
                return result;
            }
            @Override public OutputConfig getOutputConfig(FormatConfig config) throws ConfigurationException {
                try { return super.getOutputConfig(config); }
                catch (ConfigurationException | RuntimeException error) {
                    collector.log.error(collector.log.context(), "audio-output", "configuration", error); throw error;
                }
            }
            @Override public AudioOutput getAudioOutput(OutputConfig config) throws InitializationException {
                long start = SystemClock.elapsedRealtime();
                collector.log.emit("audio.output.lifecycle", "audio-output-provider", e -> e.observed("operation", "initialize").observed("phase", "begin"));
                try {
                    AudioOutput result = super.getAudioOutput(config);
                    collector.log.emit("audio.output.lifecycle", "audio-output-provider", e -> e.observed("operation", "initialize")
                            .observed("phase", "end").observed("result", "success").observed("durationMs", SystemClock.elapsedRealtime() - start));
                    return result;
                } catch (InitializationException | RuntimeException error) {
                    collector.log.error(collector.log.context(), "audio-output", "initialize", error); throw error;
                }
            }
        };
    }

    static AudioSink sink(AudioSink delegate, ExoDiagnosticCollector collector) {
        if (collector == null) return delegate;
        Media3DiagnosticBridge.bind(delegate, collector.log, null, PlaybackDiagnosticCollector.id("audio-sink"));
        return new ForwardingAudioSink(delegate) {
            private long lastMs, calls, bytes;
            private androidx.media3.common.Format inputFormat;
            private final com.fongmi.android.tv.player.PcmDiagnosticProbe inputProbe = new com.fongmi.android.tv.player.PcmDiagnosticProbe();
            @Override public void configure(AudioSinkConfig config) throws ConfigurationException {
                inputFormat = config.format;
                collector.log.emit("audio.processing", "audio-sink-config", e -> {
                    ExoDiagnosticCollector.format(e, config.format);
                    e.observed("mapping", config.outputChannelMapping == null ? null : config.outputChannelMapping.toString())
                            .observed("stage", "sink-input; before processors");
                });
                super.configure(config);
            }
            @Override public boolean handleBuffer(ByteBuffer buffer, long pts, int count) throws InitializationException, WriteException {
                if (!DebugLogStore.acceptsEvent("audio.processing")) return super.handleBuffer(buffer, pts, count);
                int position = buffer.position();
                boolean result = super.handleBuffer(buffer, pts, count);
                if (inputFormat != null && "audio/raw".equals(inputFormat.sampleMimeType)) inputProbe.sample(collector.log, collector.log.context(),
                        buffer, position, buffer.position(), inputFormat.pcmEncoding, inputFormat.channelCount, "sink-before-processors", collector.log.protectedMedia());
                calls++; bytes += buffer.position() - position;
                long now = SystemClock.elapsedRealtime();
                if (now - lastMs >= 5000) {
                    lastMs = now;
                    collector.log.emit("audio.processing", "audio-sink", e -> e.observed("writeCalls", calls).observed("acceptedBytes", bytes)
                            .observed("lastPtsUs", pts).observed("metricScope", "sink acceptance; not AudioTrack.write"));
                }
                return result;
            }
            @Override public void setSkipSilenceEnabled(boolean value) {
                super.setSkipSilenceEnabled(value);
                collector.log.emit("audio.processing", "audio-sink", e -> e.observed("skipSilence", value));
            }
            @Override public void setPlaybackParameters(androidx.media3.common.PlaybackParameters value) {
                super.setPlaybackParameters(value);
                collector.log.emit("audio.processing", "audio-sink", e -> e.observed("speed", value.speed).observed("stage", "requested-parameters"));
            }
        };
    }

    private ExoDiagnosticAudioOutput(AudioOutput output, AudioOutputProvider.OutputConfig config, ExoDiagnosticCollector collector) {
        super(output); log = collector.log; owner = log.context();
        track = output instanceof AudioTrackAudioOutput audioTrack ? audioTrack.getAudioTrack() : null;
        Media3DiagnosticBridge.bind(track, log, owner, outputId);
        pcmEncoding = config.encoding; pcmChannels = track == null ? Integer.bitCount(config.channelMask) : track.getChannelCount();
        event("audio.output.configure", e -> e.observed("encoding", config.encoding).observed("sampleRate", config.sampleRate)
                .observed("channelMask", config.channelMask).observed("offload", config.isOffload).observed("tunneling", config.isTunneling)
                .observed("sessionId", output.getAudioSessionId()).observed("bufferSize", config.bufferSize).observed("bufferUnit", "bytes")
                .observed("usage", config.audioAttributes.usage).observed("contentType", config.audioAttributes.contentType)
                .observed("flags", config.audioAttributes.flags).observed("source", output.getClass().getSimpleName())
                .unknown("audibility", NOT_OBSERVABLE).pin(outputId + "-config"));
    }

    private void event(String name, Consumer<DiagnosticEvent> facts) {
        log.emit(owner, name, "audio-output", "output-lifetime; may span gapless media", e -> {
            e.observed("audioOutputId", outputId).observed("epoch", epoch); facts.accept(e);
        });
    }

    @Override public boolean write(ByteBuffer buffer, int units, long pts) throws WriteException {
        if (!DebugLogStore.acceptsEvent("audio.output.write")) return super.write(buffer, units, pts);
        long generation = DebugLogStore.captureGeneration();
        if (generation != diagnosticGeneration) { diagnosticGeneration = generation; resetEpoch(); lastSummaryMs = 0; }
        int position = buffer.position(), remaining = buffer.remaining();
        long start = SystemClock.elapsedRealtimeNanos();
        try {
            boolean result = super.write(buffer, units, pts);
            int consumed = buffer.position() - position;
            pcmProbe.sample(log, owner, buffer, position, buffer.position(), pcmEncoding, pcmChannels, "audio-output-after-processors", log.protectedMedia());
            calls++; requested += remaining; accepted += consumed;
            if (consumed == 0) zeros++; else if (consumed < remaining) shorts++;
            lastWriteMs = SystemClock.elapsedRealtime(); lastPts = pts;
            maxWriteUs = Math.max(maxWriteUs, (SystemClock.elapsedRealtimeNanos() - start) / 1000);
            if (consumed > 0 && firstPts == Long.MIN_VALUE) {
                firstPts = pts;
                event("audio.output.write", e -> e.observed("stage", "first-payload-accepted").observed("acceptedBytes", consumed).observed("firstPtsUs", pts));
                log.evidence(owner, false, 3);
            }
            sample(false);
            return result;
        } catch (WriteException error) {
            errors++;
            event("audio.error", e -> e.severity("error").observed("stage", "write").observed("errorCode", error.errorCode)
                    .observed("recoverable", error.isRecoverable).pin(outputId + "-error"));
            log.error(owner, "audio-output", "write", error); sample(true); throw error;
        }
    }

    private void sample(boolean force) {
        if (!DebugLogStore.acceptsEvent("audio.output.write")) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastSummaryMs < 5000) return;
        lastSummaryMs = now;
        event("audio.output.write", e -> e.observed("writeCalls", calls).observed("requestedBytes", requested)
                .observed("acceptedBytes", accepted).observed("shortWrites", shorts).observed("zeroWrites", zeros)
                .observed("writeErrors", errors).observed("maxWriteUs", maxWriteUs)
                .observed("lastPtsUs", lastPts == Long.MIN_VALUE ? null : lastPts)
                .observed("lastAgeMs", lastWriteMs == 0 ? null : now - lastWriteMs)
                .observed("metricScope", "AudioOutput payload consumption; retried requests included, internal headers excluded"));
        if (track == null) {
            event("audio.output.playhead", e -> e.unknown("headRaw", NOT_OBSERVABLE).observed("nativeHook", "non-AudioTrack backend"));
            return;
        }
        try {
            long raw = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
            long delta = rawHead < 0 ? 0 : (raw - rawHead) & 0xffffffffL;
            // A large backward jump can be a backend reset, not a four-billion-frame advance.
            if (delta > 0x7fffffffL) { epoch++; extendedHead = 0; delta = 0; }
            else extendedHead += delta;
            boolean first = rawHead < 0; rawHead = raw;
            audioProgress.sample(log, owner, epoch, now, extendedHead,
                    track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING ? "audio-paused-or-stopped"
                            : accepted == 0 ? "no-audio-payload-observed" : null,
                    "actual AudioTrack playhead during writes; not acoustic audibility");
            long change = delta;
            event("audio.output.playhead", e -> {
                e.observed("headRaw", raw).observed("headFrames", extendedHead)
                        .observed("headDeltaFrames", first ? null : change).observed("state", track.getState())
                        .observed("playState", track.getPlayState()).observed("metricScope", "uint32 AudioTrack frames; reset/flush epoch")
                        .unknown("audibility", NOT_OBSERVABLE);
                if (Build.VERSION.SDK_INT >= 24) e.observed("underruns", track.getUnderrunCount());
                else e.unknown("underruns", NOT_SUPPORTED);
            });
            if (delta > 0) log.evidence(owner, false, 4);
            if (Build.VERSION.SDK_INT >= 23) {
                AudioDeviceInfo route = track.getRoutedDevice();
                event("audio.route", e -> e.observed("routeRole", "actual").observed("routeId", route == null ? null : "device-" + route.getId())
                        .observed("routeType", route == null ? null : route.getType()).observed("source", "AudioTrack.getRoutedDevice"));
            } else event("audio.route", e -> e.unknown("routeId", NOT_SUPPORTED));
            if (now - lastTimestampMs >= 10000) {
                lastTimestampMs = now;
                AudioTimestamp timestamp = new AudioTimestamp();
                boolean valid = track.getTimestamp(timestamp);
                event("audio.output.playhead", e -> {
                    e.observed("timestampValid", valid);
                    if (valid) e.observed("timestampFrames", timestamp.framePosition).observed("timestampNs", timestamp.nanoTime)
                            .observed("timestampAgeMs", Math.max(0, (System.nanoTime() - timestamp.nanoTime) / 1_000_000));
                    else e.unknown("timestampFrames", UNAVAILABLE).unknown("timestampNs", UNAVAILABLE);
                });
            }
        } catch (RuntimeException error) {
            event("audio.output.playhead", e -> e.unknown("headRaw", READ_ERROR));
        }
    }

    @Override public void play() { super.play(); lifecycle("play"); }
    @Override public void pause() { sample(true); super.pause(); lifecycle("pause"); }
    @Override public void flush() { sample(true); super.flush(); resetEpoch(); lifecycle("flush"); }
    @Override public void stop() { sample(true); super.stop(); resetEpoch(); lifecycle("stop"); }
    @Override public void release() { sample(true); super.release(); lifecycle("release"); }
    @Override public void setVolume(float volume) { super.setVolume(volume); event("audio.volume", e -> e.observed("volume", volume).observed("stage", "AudioOutput.setVolume")); }
    @Override public void setPreferredDevice(AudioDeviceInfo device) {
        super.setPreferredDevice(device);
        event("audio.route", e -> e.observed("routeRole", "preferred").observed("routeId", device == null ? null : "device-" + device.getId()));
    }
    private void lifecycle(String operation) { event("audio.output.lifecycle", e -> e.observed("operation", operation).observed("phase", "end").observed("result", "success")); }
    private void resetEpoch() {
        epoch++; rawHead = -1; extendedHead = 0; calls = requested = accepted = shorts = zeros = errors = maxWriteUs = 0;
        firstPts = lastPts = Long.MIN_VALUE; lastWriteMs = 0; lastTimestampMs = 0;
    }
    private long diagnosticGeneration = -1;
}
