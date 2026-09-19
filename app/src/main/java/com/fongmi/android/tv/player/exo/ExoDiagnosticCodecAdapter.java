package com.fongmi.android.tv.player.exo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import androidx.media3.decoder.CryptoInfo;
import androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Counts only existing calls. Does not dequeue, inspect samples, or install a new frame listener. */
final class ExoDiagnosticCodecAdapter extends ForwardingMediaCodecAdapter {
    private final PlaybackDiagnosticCollector log;
    private final Context owner;
    private final String decoderId, name;
    private final boolean video;
    private String surfaceId;
    private long epoch, inputs, bytes, outputs, submitted, discarded, encrypted, eos, regressions;
    private volatile long callbacks;
    private boolean hasFrameListener;
    private long firstPts = Long.MIN_VALUE, lastPts = Long.MIN_VALUE, lastInputMs, lastOutputMs, lastSummaryMs;
    private long lastDecodedPts = Long.MIN_VALUE;

    static MediaCodecAdapter.Factory factory(MediaCodecAdapter.Factory delegate, ExoDiagnosticCollector collector) {
        return config -> {
            PlaybackDiagnosticCollector log = collector.log;
            Context owner = log.context();
            String decoderId = PlaybackDiagnosticCollector.id("decoder");
            boolean video = config.format.sampleMimeType != null && config.format.sampleMimeType.startsWith("video/");
            String event = video ? "video.decoder.attempt" : "audio.decoder.attempt";
            long start = SystemClock.elapsedRealtime();
            log.emit(owner, event, "codec-adapter-factory", "engine-at-create; media-unconfirmed", e -> e
                    .observed("decoderId", decoderId).observed("decoderName", config.codecInfo.name)
                    .observed("stage", "create-configure-start aggregate").observed("phase", "begin")
                    .unknown("operation", NOT_OBSERVABLE).pin(decoderId + "-attempt"));
            mediaFormat(log, owner, config.mediaFormat, decoderId, video ? "video.configure" : "audio.decoder.output");
            Object previousDiagnosticOwner = Media3DiagnosticBridge.enter(log, owner, decoderId, config.codecInfo.name, video);
            try {
                MediaCodecAdapter adapter = delegate.createAdapter(config);
                log.emit(owner, event, "codec-adapter-factory", "engine-at-create; media-unconfirmed", e -> e
                        .observed("decoderId", decoderId).observed("decoderName", config.codecInfo.name)
                        .observed("stage", "create-configure-start aggregate").observed("phase", "end")
                        .observed("result", "success").observed("durationMs", SystemClock.elapsedRealtime() - start));
                return new ExoDiagnosticCodecAdapter(adapter, log, owner, decoderId, config, video);
            } catch (IOException | RuntimeException error) {
                log.emit(owner, event, "codec-adapter-factory", "engine-at-create; media-unconfirmed", e -> e.severity("error")
                        .observed("decoderId", decoderId).observed("decoderName", config.codecInfo.name)
                        .observed("stage", "create-configure-start aggregate").observed("phase", "end")
                        .observed("result", "failed").observed("durationMs", SystemClock.elapsedRealtime() - start)
                        .unknown("operation", NOT_OBSERVABLE).pin(decoderId + "-failure"));
                log.error(owner, video ? "video-codec" : "audio-codec", "create-configure-start aggregate", error);
                throw error;
            } finally {
                androidx.media3.common.util.PlaybackDiagnostics.leave(previousDiagnosticOwner);
            }
        };
    }

    static MediaCodecSelector selector(MediaCodecSelector delegate, ExoDiagnosticCollector collector, String policy) {
        return (mime, secure, tunnel) -> {
            List<MediaCodecInfo> infos;
            try { infos = delegate.getDecoderInfos(mime, secure, tunnel); }
            catch (androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException error) {
                collector.log.error(collector.log.context(), "codec-query", policy, error); throw error;
            }
            candidates(collector, infos, mime, secure, tunnel, policy);
            return infos;
        };
    }

    static void candidates(ExoDiagnosticCollector collector, List<MediaCodecInfo> infos, String mime, boolean secure, boolean tunnel, String policy) {
        if (collector == null) return;
        boolean video = mime != null && mime.startsWith("video/");
        String event = video ? "video.candidates" : "audio.candidates";
        if (!DebugLogStore.acceptsEvent(event)) return;
        Context owner = collector.log.context();
        List<ExoDiagnosticCodecSnapshotCache.Candidate> candidates = new ArrayList<>(infos.size());
        for (MediaCodecInfo info : infos) {
            List<ExoDiagnosticCodecSnapshotCache.Profile> levels = new ArrayList<>();
            int profileCount = 0;
            boolean readable = true;
            try {
                android.media.MediaCodecInfo.CodecProfileLevel[] profiles = info.getProfileLevels();
                profileCount = profiles.length;
                for (int p = 0; p < Math.min(64, profiles.length); p++) {
                    levels.add(new ExoDiagnosticCodecSnapshotCache.Profile(profiles[p].profile, profiles[p].level));
                }
            } catch (RuntimeException ignored) {
                readable = false;
                DebugLogStore.collectorFailure();
            }
            candidates.add(new ExoDiagnosticCodecSnapshotCache.Candidate(info.name,
                    info.hardwareAccelerated, info.softwareOnly, info.vendor, profileCount, levels, readable));
        }
        ExoDiagnosticCodecSnapshotCache.Observation observation = collector.codecSnapshots.observe(
                owner, DebugLogStore.captureGeneration(),
                new ExoDiagnosticCodecSnapshotCache.Query(mime, secure, tunnel, policy), candidates);
        String snapshotId = collector.log.instanceId() + "-codecs-" + observation.snapshotId();
        if (!observation.fullSnapshot()) {
            collector.log.emit(owner, event, "codec-selector", "engine-context", e -> e
                    .observed("phase", "snapshot-reuse").observed("snapshotId", snapshotId)
                    .observed("queryCount", observation.queryCount()).observed("candidateCount", candidates.size())
                    .observed("mime", mime).observed("secure", secure).observed("tunneling", tunnel)
                    .observed("reason", policy));
            return;
        }
        if (candidates.isEmpty()) collector.log.emit(owner, event, "codec-selector", "engine-context", e -> e
                .observed("snapshotId", snapshotId).observed("mime", mime).observed("candidateCount", 0)
                .observed("reason", policy).observed("secure", secure).observed("tunneling", tunnel));
        for (int i = 0; i < candidates.size(); i++) {
            ExoDiagnosticCodecSnapshotCache.Candidate info = candidates.get(i);
            int index = i;
            collector.log.emit(owner, event, "codec-selector", "engine-context", e -> e
                    .observed("snapshotId", snapshotId).observed("mime", mime).observed("candidateIndex", index)
                    .observed("candidateCount", candidates.size()).observed("decoderName", info.name())
                    .observed("hardwareAccelerated", info.hardwareAccelerated()).observed("softwareOnly", info.softwareOnly())
                    .observed("vendor", info.vendor()).observed("secure", secure).observed("tunneling", tunnel)
                    .observed("reason", policy).observed("metricScope", "selector result; renderer may further filter/order"));
            for (ExoDiagnosticCodecSnapshotCache.Profile profile : info.profiles()) {
                collector.log.emit(owner, event, "codec-capabilities", "engine-context", e -> e
                        .observed("snapshotId", snapshotId).observed("candidateIndex", index).observed("decoderName", info.name())
                        .observed("profile", profile.profile()).observed("level", profile.level())
                        .observed("truncated", info.profileCount() > 64).observed("metricScope", "Android capability declaration"));
            }
            if (!info.readable()) collector.log.emit(owner, event, "codec-capabilities", "engine-context", e -> e
                    .observed("snapshotId", snapshotId).observed("decoderName", info.name()).unknown("profiles", READ_ERROR));
        }
    }

    private ExoDiagnosticCodecAdapter(MediaCodecAdapter delegate, PlaybackDiagnosticCollector log, Context owner,
                                      String decoderId, Configuration config, boolean video) {
        super(delegate);
        this.log = log; this.owner = owner; this.decoderId = decoderId; this.video = video; name = config.codecInfo.name;
        surfaceId = PlaybackDiagnosticCollector.objectId(config.surface, "surface");
        emit(video ? "video.configure" : "audio.decoder.output", e -> {
            ExoDiagnosticCollector.format(e, config.format);
            e.observed("surfaceId", surfaceId).unknown("renderCallbacks", NOT_COLLECTED);
        });
    }

    private void emit(String event, java.util.function.Consumer<DiagnosticEvent> facts) {
        log.emit(owner, event, "codec-adapter", "adapter-lifetime; media-at-create-unconfirmed", e -> {
            e.observed("decoderId", decoderId).observed("decoderName", name).observed("epoch", epoch);
            facts.accept(e);
        });
    }

    static void mediaFormat(PlaybackDiagnosticCollector log, Context owner, MediaFormat format, String id, String event) {
        if (!DebugLogStore.acceptsEvent(event)) return;
        String[] keys = {"mime", "width", "height", "max-width", "max-height", "profile", "level", "color-standard", "color-range",
                "color-transfer", "rotation-degrees", "max-input-size", "operating-rate", "priority", "low-latency", "crop-left",
                "crop-right", "crop-top", "crop-bottom", "stride", "slice-height", "sample-rate", "channel-count", "pcm-encoding"};
        for (String key : keys) {
            if (!format.containsKey(key)) continue;
            try {
                Object value = "mime".equals(key) ? format.getString(key)
                        : "operating-rate".equals(key) ? format.getFloat(key) : format.getInteger(key);
                log.emit(owner, event, "codec-mediaformat", "adapter-configuration", e -> e.observed("decoderId", id)
                        .observed("property", key).observed("value", value));
            } catch (RuntimeException ignored) {
                log.emit(owner, event, "codec-mediaformat", "adapter-configuration", e -> e.observed("decoderId", id)
                        .observed("property", key).unknown("value", READ_ERROR));
            }
        }
        int count = 0, bytes = 0;
        boolean protectedCsd = log.protectedMedia();
        boolean digestComplete = !protectedCsd && owner == log.context();
        java.security.MessageDigest digest = null;
        if (digestComplete) try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException ignored) { digestComplete = false; }
        for (int i = 0; i < 8; i++) {
            try {
                java.nio.ByteBuffer csd = format.getByteBuffer("csd-" + i);
                if (csd != null) {
                    count++; int length = csd.remaining();
                    bytes = (int) Math.min(Integer.MAX_VALUE, (long) bytes + length);
                    if (bytes > 65536) digestComplete = false;
                    if (digestComplete) {
                        digest.update((byte) i);
                        digest.update(new byte[]{(byte)(length >>> 24), (byte)(length >>> 16), (byte)(length >>> 8), (byte)length});
                        digest.update(csd.duplicate()); // Never consume the codec's original buffer.
                    }
                }
            } catch (RuntimeException ignored) { digestComplete = false; DebugLogStore.collectorFailure(); }
        }
        String digestValue = null;
        if (digestComplete && count > 0) {
            StringBuilder text = new StringBuilder(64);
            for (byte value : digest.digest()) text.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
            digestValue = text.toString();
        }
        int csdCount = count, csdBytes = bytes;
        String csdDigest = digestValue;
        log.emit(owner, event, "codec-mediaformat", "adapter-configuration", e -> {
            e.observed("decoderId", id).observed("csdCount", csdCount).observed("csdBytes", csdBytes);
            if (csdDigest != null) e.observed("csdDigest", csdDigest);
            else e.unknown("csdDigest", protectedCsd ? PERMISSION_DENIED : csdCount == 0 ? NOT_APPLICABLE : NOT_COLLECTED);
        });
    }

    @Override public void queueInputBuffer(int index, int offset, int size, long pts, int flags) {
        super.queueInputBuffer(index, offset, size, pts, flags);
        input(size, pts, flags, false);
    }
    @Override public void queueSecureInputBuffer(int index, int offset, CryptoInfo crypto, long pts, int flags) {
        super.queueSecureInputBuffer(index, offset, crypto, pts, flags);
        input(0, pts, flags, true);
    }
    private void input(int size, long pts, int flags, boolean secure) {
        if (!DebugLogStore.acceptsEvent(video ? "video.sample.summary" : "audio.decoder.output")) return;
        captureWindow();
        inputs++; bytes += size; if (secure) encrypted++;
        if ((flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos++;
        if (lastPts != Long.MIN_VALUE && pts < lastPts) regressions++;
        lastPts = pts; lastInputMs = SystemClock.elapsedRealtime();
        if (firstPts == Long.MIN_VALUE) {
            firstPts = pts;
            emit(video ? "video.first-output" : "audio.decoder.output", e -> e.observed("stage", "first-input-queued").observed("firstPtsUs", pts));
        }
        summary(false);
    }
    @Override public int dequeueOutputBufferIndex(MediaCodec.BufferInfo info) {
        int result = super.dequeueOutputBufferIndex(info);
        if (result >= 0 && DebugLogStore.acceptsEvent(video ? "video.output.summary" : "audio.decoder.output")) {
            captureWindow();
            outputs++; lastOutputMs = SystemClock.elapsedRealtime();
            if ((info.flags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG | MediaCodec.BUFFER_FLAG_END_OF_STREAM)) == 0) lastDecodedPts = info.presentationTimeUs;
            if (outputs == 1) emit(video ? "video.first-output" : "audio.decoder.output", e -> e
                    .observed("stage", "first-output-buffer").observed("firstPtsUs", info.presentationTimeUs));
            summary(false);
        }
        return result;
    }
    @Override public MediaFormat getOutputFormat() {
        MediaFormat result = super.getOutputFormat();
        mediaFormat(log, owner, result, decoderId, video ? "video.output.format" : "audio.decoder.output");
        return result;
    }
    @Override public void releaseOutputBuffer(int index, boolean render) { super.releaseOutputBuffer(index, render); released(render); }
    @Override public void releaseOutputBuffer(int index, long time) { super.releaseOutputBuffer(index, time); released(true); }
    private void released(boolean render) {
        if (!DebugLogStore.acceptsEvent(video ? "video.output.summary" : "audio.decoder.output")) return;
        captureWindow();
        if (render) {
            submitted++;
            if (submitted == 1) emit("video.first-output", e -> e.observed("stage", "first-release-to-surface").observed("surfaceId", surfaceId));
        } else discarded++;
    }
    @Override public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
        // Only observe the listener already requested by Media3 (notably tunneling).
        hasFrameListener = true;
        super.setOnFrameRenderedListener((codec, pts, time) -> {
            if (PlaybackDiagnosticCollector.enabled()) callbacks++;
            listener.onFrameRendered(this, pts, time);
        }, handler);
    }
    @Override public void setOutputSurface(Surface surface) {
        super.setOutputSurface(surface);
        String old = surfaceId; surfaceId = PlaybackDiagnosticCollector.objectId(surface, "surface");
        emit("surface.bind", e -> e.observed("oldSurfaceId", old).observed("surfaceId", surfaceId).observed("result", "success"));
    }
    @Override public void detachOutputSurface() {
        super.detachOutputSurface(); String old = surfaceId; surfaceId = "none";
        emit("surface.bind", e -> e.observed("oldSurfaceId", old).observed("surfaceId", surfaceId).observed("phase", "detached"));
    }
    @Override public void flush() {
        summary(true);
        try { super.flush(); lifecycle("flush", "success"); }
        catch (RuntimeException error) { log.error(owner, "codec", "flush", error); throw error; }
        epoch++; inputs = bytes = outputs = submitted = discarded = encrypted = eos = regressions = callbacks = 0;
        firstPts = lastPts = lastDecodedPts = Long.MIN_VALUE; lastInputMs = lastOutputMs = 0;
    }
    @Override public void release() {
        summary(true);
        try { super.release(); lifecycle("release", "success"); }
        catch (RuntimeException error) { log.error(owner, "codec", "release", error); throw error; }
    }
    private void lifecycle(String operation, String result) {
        emit(video ? "video.flush-reuse-release" : "audio.decoder.attempt", e -> e.observed("operation", operation).observed("result", result));
    }
    private void summary(boolean force) {
        if (!DebugLogStore.acceptsEvent(video ? "video.output.summary" : "audio.decoder.output")) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastSummaryMs < 5000) return;
        lastSummaryMs = now;
        emit("audio.sync", e -> e.observed("video", video).observed("lastPtsUs", lastDecodedPts == Long.MIN_VALUE ? null : lastDecodedPts)
                .observed("lastAgeMs", lastOutputMs == 0 ? null : now - lastOutputMs)
                .observed("metricScope", "decoder output PTS; correlate by owner/epoch; not physical presentation time"));
        emit(video ? "video.sample.summary" : "audio.decoder.output", e -> e.observed("inputBuffers", inputs)
                .observed("bytes", bytes).observed("encryptedBuffers", encrypted).observed("eosBuffers", eos)
                .observed("ptsRegressions", regressions).observed("firstPtsUs", firstPts == Long.MIN_VALUE ? null : firstPts)
                .observed("lastPtsUs", lastPts == Long.MIN_VALUE ? null : lastPts)
                .observed("lastAgeMs", lastInputMs == 0 ? null : now - lastInputMs));
        emit(video ? "video.output.summary" : "audio.decoder.output", e -> e.observed("outputBuffers", outputs)
                .observed("submittedBuffers", submitted).observed("discardedBuffers", discarded)
                .observed("surfaceId", surfaceId).observed("lastAgeMs", lastOutputMs == 0 ? null : now - lastOutputMs)
                .observed("metricScope", "adapter calls since capture/flush; secure/tunneling may bypass dequeue")
                .unknown("physicalDisplay", NOT_OBSERVABLE));
        if (hasFrameListener) emit("video.surface-render-callback", e -> e.observed("renderCallbacks", callbacks)
                .observed("surfaceId", surfaceId).observed("metricScope", "existing Media3 listener; may batch/delay"));
    }

    private long diagnosticGeneration = -1;
    private void captureWindow() {
        long generation = DebugLogStore.captureGeneration();
        if (generation == diagnosticGeneration) return;
        diagnosticGeneration = generation; epoch++;
        inputs = bytes = outputs = submitted = discarded = encrypted = eos = regressions = callbacks = 0;
        firstPts = lastPts = lastDecodedPts = Long.MIN_VALUE; lastInputMs = lastOutputMs = lastSummaryMs = 0;
    }
}
