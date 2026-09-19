package com.fongmi.android.tv.player.exo;

import androidx.media3.common.util.PlaybackDiagnostics;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Maps library-owned events to their original player/output. Never invokes playback APIs. */
final class Media3DiagnosticBridge implements PlaybackDiagnostics.Listener {
    private static final WeakHashMap<Object, Binding> OWNERS = new WeakHashMap<>();
    private static final Media3DiagnosticBridge INSTANCE = new Media3DiagnosticBridge();
    private static final ThreadLocal<WriteCache> WRITES = ThreadLocal.withInitial(WriteCache::new);
    private static final class WriteCache { WeakReference<Object> key = new WeakReference<>(null); Binding binding; }
    static final class Binding {
        final PlaybackDiagnosticCollector log;
        final Context owner;
        final String id, decoderName;
        final boolean video;
        long capture = -1, calls, bytes, requested, shortWrites, zeros, errors, headerBytes, maxNs, lastMs;
        Binding(PlaybackDiagnosticCollector log, Context owner, String id, String decoderName, boolean video) {
            this.log = log; this.owner = owner; this.id = id; this.decoderName = decoderName; this.video = video;
        }
        Context context() { return owner == null ? log.context() : owner; }
    }
    static void bind(Object key, PlaybackDiagnosticCollector log, Context owner, String id) {
        if (key == null) return;
        PlaybackDiagnostics.setListener(INSTANCE);
        synchronized (OWNERS) { OWNERS.put(key, new Binding(log, owner, id, null, false)); }
    }
    static Object enter(PlaybackDiagnosticCollector log, Context owner, String decoderId, String name, boolean video) {
        PlaybackDiagnostics.setListener(INSTANCE);
        return PlaybackDiagnostics.enter(new Binding(log, owner, decoderId, name, video));
    }
    private static Binding binding(Object key) {
        if (key instanceof Binding binding) return binding;
        synchronized (OWNERS) { return OWNERS.get(key); }
    }

    static androidx.media3.extractor.ExtractorsFactory extractors(androidx.media3.extractor.ExtractorsFactory delegate,
            androidx.media3.common.MediaItem item) {
        if (item.localConfiguration == null || !(item.localConfiguration.tag instanceof Context owner)) return delegate;
        PlaybackDiagnosticCollector log = ExoDiagnosticCollector.mediaLog(owner);
        if (log == null) return delegate;
        return new androidx.media3.extractor.ForwardingExtractorsFactory(delegate) {
            private androidx.media3.extractor.Extractor[] bindOutputs(androidx.media3.extractor.Extractor[] extractors) {
                for (int i = 0; i < extractors.length; i++) {
                    extractors[i] = new androidx.media3.extractor.ForwardingExtractor(extractors[i]) {
                        @Override public void init(androidx.media3.extractor.ExtractorOutput output) {
                            // Bind on the original loader's init, never to the globally current media.
                            bind(output, log, owner, owner.mediaId());
                            super.init(output);
                        }
                    };
                }
                return extractors;
            }
            @Override public androidx.media3.extractor.Extractor[] createExtractors() { return bindOutputs(delegate.createExtractors()); }
            @Override public androidx.media3.extractor.Extractor[] createExtractors(android.net.Uri uri, java.util.Map<String, java.util.List<String>> headers) {
                return bindOutputs(delegate.createExtractors(uri, headers));
            }
        };
    }
    @Override public boolean enabled(String event) {
        return "codec".equals(event) ? DebugLogStore.acceptsEvent("video.decoder.attempt") || DebugLogStore.acceptsEvent("audio.decoder.attempt") : DebugLogStore.acceptsEvent(event);
    }
    @Override public void event(Object key, String event, String operation, String phase, long durationNs, long value, String detail, Throwable error) {
        Binding binding = binding(key);
        if (binding == null) {
            if ("codec".equals(event)) return;
            DebugLogStore.event(new DiagnosticEvent(event, "none", PlaybackDiagnosticCollector.objectId(key, "media3-owner"), 0, 0)
                    .observed("operation", operation).observed("phase", phase).observed("value", value).message(detail)
                    .observed("association", "library-owner; media-unresolved"));
            return;
        }
        String name = "codec".equals(event) ? binding.video ? "video.decoder.attempt" : "audio.decoder.attempt" : event;
        binding.log.emit(binding.context(), name, "media3-owner-hook", binding.owner == null ? "engine-owner-current-context" : "fixed-operation-owner", e -> {
            e.observed("operation", operation).observed("phase", phase).observed("durationMs", durationNs < 0 ? null : durationNs / 1_000_000.0)
                    .observed("result", error == null ? phase : "failed");
            if ("codec".equals(event)) e.observed("decoderId", binding.id).observed("decoderName", binding.decoderName).observed("stage", operation);
            else if ("audio.focus".equals(event)) {
                e.observed("focusOwner", "Media3");
                if ("callback".equals(operation)) e.observed("focusChange", value);
                else if ("duck-gain".equals(operation)) e.observed("effectiveGain", Float.parseFloat(detail));
                else if ("player-command".equals(operation)) e.observed("focusAction", value);
                else e.observed("focusResult", value).observed("focusRequest", detail);
            } else if ("audio.processing".equals(event)) {
                e.observed("processorIndex", value).observed("active", "active".equals(operation) ? true : null).message(detail);
                int input = detail.indexOf(" in="), output = detail.indexOf(" out=");
                e.observed("processorName", input < 0 ? detail : detail.substring(0, input));
                if (input >= 0 && output > input) e.observed("inputFormat", detail.substring(input + 4, output)).observed("outputFormat", detail.substring(output + 5));
            } else e.message(detail).observed("value", value);
            if (error != null) e.severity("error").observed("javaClass", error.getClass().getName());
        });
        if (error != null) binding.log.error(binding.context(), binding.video ? "video-codec" : "audio-codec", operation, error);
    }
    @Override public void write(Object key, int requested, int result, long pts, boolean header, long durationNs) {
        WriteCache cache = WRITES.get();
        if (cache.key.get() != key) { cache.key = new WeakReference<>(key); cache.binding = binding(key); }
        Binding binding = cache.binding;
        if (binding == null) return;
        long capture = DebugLogStore.captureGeneration();
        if (binding.capture != capture) {
            binding.capture = capture; binding.calls = binding.bytes = binding.requested = binding.shortWrites = binding.zeros = binding.errors = binding.headerBytes = binding.maxNs = binding.lastMs = 0;
        }
        binding.calls++; binding.requested += requested;
        if (result < 0) binding.errors++; else if (result == 0) binding.zeros++; else {
            if (header) binding.headerBytes += result; else binding.bytes += result;
            if (result < requested) binding.shortWrites++;
        }
        binding.maxNs = Math.max(binding.maxNs, durationNs);
        long now = android.os.SystemClock.elapsedRealtime();
        if (result >= 0 && binding.calls != 1 && now - binding.lastMs < 5000) return;
        binding.lastMs = now;
        binding.log.emit(binding.context(), "audio.output.write", "AudioTrack.write-owner", "fixed-output-lifetime", e -> {
            e.observed("audioOutputId", binding.id).observed("writeCalls", binding.calls).observed("requestedBytes", binding.requested)
                    .observed("acceptedBytes", binding.bytes).observed("shortWrites", binding.shortWrites).observed("zeroWrites", binding.zeros)
                    .observed("writeErrors", binding.errors).observed("maxWriteUs", binding.maxNs / 1000).observed("lastPtsUs", pts)
                    .observed("errorCode", result).observed("bytes", binding.headerBytes)
                    .observed("metricScope", "raw AudioTrack API; acceptedBytes=payload; bytes=AV-sync headers; request includes retries");
            if (result < 0) e.severity("error").observed("stage", header ? "av-sync-header-write" : "payload-write");
        });
    }
}
