package androidx.media3.mpvplayer;

import android.os.SystemClock;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector;
import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.github.catvod.crawler.diagnostics.DiagnosticText;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import is.xyz.mpv.MPVLib;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Native callbacks and watchdog only publish cached facts. Never calls MPV or waits for the UI. */
final class MpvDiagnosticCollector {
    private static final Context UNRESOLVED = new Context("none", 0, 0, null, "unresolved");
    private static final String HOOK_MARKER = "WebHTV AV-DIAG ";
    private static final java.util.regex.Pattern HOOK_FIELDS = java.util.regex.Pattern.compile("([A-Za-z][A-Za-z0-9]*)=([^\\s]+)");
    static final String STANDARD_COMPONENTS = ",vd=info,ffmpeg/video=info,ffmpeg/audio=info,vo=info,ao=info,cplayer=info";
    private static final Set<String> PROPERTIES = Set.of("time-pos", "time-pos/full", "duration", "duration/full", "pause", "paused-for-cache",
            "idle-active", "eof-reached", "seeking", "file-format", "seekable", "current-edition", "vid", "aid", "sid", "hwdec", "hwdec-current", "hwdec-interop", "video-codec", "audio-codec",
            "current-vo", "current-gpu-context", "gpu-api", "current-ao", "audio-device", "volume", "mute", "speed", "audio-delay", "avsync",
            "decoder-frame-drop-count", "frame-drop-count", "mistimed-frame-count", "vo-delayed-frame-count", "display-fps", "estimated-display-fps",
            "container-fps", "estimated-vf-fps", "video-pts", "audio-pts", "mpv-version", "ffmpeg-version", "options/msg-level",
            "options/af", "demuxer-cache-duration", "cache-buffering-state", "audio-spdif");
    private final PlaybackDiagnosticCollector log = new PlaybackDiagnosticCollector("mpv", "runtime-properties");
    private final com.fongmi.android.tv.player.FelBindProbeControl felBindProbe =
            new com.fongmi.android.tv.player.FelBindProbeControl(log.instanceId());
    private final MpvPropertySnapshot snapshot;
    private final AtomicLong nativeSeq = new AtomicLong();
    private final String cacheId = PlaybackDiagnosticCollector.id("mpv-health");
    private volatile long nativeOverflow, lastNativeMs, firstNativeMs, capture = -1, lastTickMs;
    private volatile String lastVideoFailure;
    private volatile String requestedMsgLevel;
    private volatile boolean closed;
    // Controller requests may advance before the native thread finishes the previous file.
    private volatile Context nativeOwner = UNRESOLVED;
    private Context pendingLoadOwner;
    private long nativeGeneration = -1, loadSequence, pendingLoadId;
    private final Map<String, MpvPropertySnapshot.DiagnosticValue> emitted = new java.util.HashMap<>();
    private final Set<String> emittedMissing = new java.util.HashSet<>();
    private MpvPropertySnapshot.TrackList emittedTracks;
    private int categories = -1;
    private final com.fongmi.android.tv.player.OutputProgressDiagnostic videoProgress = new com.fongmi.android.tv.player.OutputProgressDiagnostic(true);

    MpvDiagnosticCollector(MpvPropertySnapshot snapshot) { this.snapshot = snapshot; }
    com.fongmi.android.tv.player.FelBindProbeControl felBindProbe() { return felBindProbe; }
    boolean depthActive() {
        Context owner = nativeOwner;
        com.github.catvod.crawler.diagnostics.DiagnosticCapture.Session capture = com.github.catvod.crawler.diagnostics.DiagnosticCapture.current(owner.trace(), owner.generation(), owner.attempt());
        return capture != null && capture.instance().equals(log.instanceId());
    }

    static boolean propertyAllowed(String name) {
        return PROPERTIES.contains(name) || name.startsWith("video-params/") || name.startsWith("video-out-params/")
                || name.startsWith("video-dec-params/") || name.startsWith("audio-params/") || name.startsWith("audio-out-params/")
                || name.startsWith("current-tracks/video/") || name.startsWith("current-tracks/audio/");
    }

    private synchronized void capture() {
        long generation = DebugLogStore.captureGeneration();
        if (capture == generation) return;
        capture = generation; nativeOverflow = lastNativeMs = firstNativeMs = lastTickMs = 0; lastVideoFailure = null;
        emitted.clear(); emittedMissing.clear(); emittedTracks = null;
        log.baseline();
    }

    synchronized void begin(String trace) {
        closed = false; log.begin(trace, "foreground");
        felBindProbe.begin();
        com.fongmi.android.tv.player.DiagnosticControls.registerFelBindProbe(felBindProbe);
        emitted.clear(); emittedMissing.clear(); emittedTracks = null;
        if (PlaybackDiagnosticCollector.enabled()) { capture(); health(); }
    }

    synchronized long loadRequested() {
        Context owner = log.context();
        // The public START_FILE callback has no playlist entry ID. Never guess across
        // overlapping attempts, including load commands whose return has not arrived.
        pendingLoadOwner = pendingLoadOwner == null || pendingLoadOwner == owner ? owner : UNRESOLVED;
        return pendingLoadId = ++loadSequence;
    }

    synchronized void loadReturned(long requestId, int result) {
        if (result < 0 && pendingLoadId == requestId) pendingLoadOwner = null;
    }

    synchronized void nativeLog(String prefix, int level, String text) {
        if (!PlaybackDiagnosticCollector.enabled() || text == null
                || !DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.nativePrefix(prefix))) return;
        capture();
        long now = SystemClock.elapsedRealtime();
        if (firstNativeMs == 0) firstNativeMs = now;
        lastNativeMs = now;
        long sourceSeq = nativeSeq.incrementAndGet();
        String bounded = text.substring(0, Math.min(16384, text.length()));
        if (isHook(bounded)) {
            hook(prefix, level, bounded, sourceSeq);
            return;
        }
        String lower = bounded.toLowerCase(java.util.Locale.ROOT);
        boolean lookup = lower.contains("failed to getcodecnamebytype");
        boolean videoFailure = lookup || lower.contains("software decoding fallback is disabled")
                || (prefix != null && (prefix.equals("vd") || prefix.startsWith("ffmpeg/video"))
                    && (lower.contains("failed") || lower.contains("error while opening decoder") || lower.contains("could not open codec")));
        boolean boundary = videoFailure || lower.contains("using hardware decoding") || lower.contains("using software decoding")
                || lower.contains("mediacodec started successfully");
        String severity = level <= 10 ? "fatal" : level <= 20 ? "error" : level <= 30 ? "warn" : level <= 40 ? "info" : level <= 50 ? "debug" : "trace";
        String stage = lookup ? "codec-name-lookup-result" : videoFailure ? "decoder-initialization-unresolved" : "native-message";
        if (videoFailure) {
            String safe = DiagnosticText.clean(bounded).text();
            lastVideoFailure = safe.substring(0, Math.min(240, safe.length()));
        }
        log.emitNative(nativeOwner, boundary ? "mpv.decoder.attempt" : "mpv.native.output", "mpv-log-callback", "native-load-start-context; log-media-unconfirmed", prefix, e -> {
            e.severity(severity).observed("nativePrefix", prefix).observed("nativeLevel", level).observed("count", sourceSeq)
                    .observed("stage", stage).message(bounded);
            if (boundary) e.inferred().pin(cacheId + "-last-boundary");
            if (lookup || videoFailure) e.unknown("operation", UNKNOWN).observed("nativeHook", "legacy text; correlate with packaged AV-DIAG-01-v1 operation events");
        });
        // Publish after the last error itself: a silent native tail needs no later log or UI callback.
        if (videoFailure || level <= 30) { partialFailure(); health(); }
    }

    static boolean isHook(String text) {
        if (text == null) return false;
        int marker = text.indexOf(HOOK_MARKER);
        // libav prepends its AVClass item name (e.g. hevc_mediacodec:).
        return marker == 0 || marker > 0 && marker < 256 && text.charAt(marker - 1) == ' ';
    }

    private void hook(String prefix, int level, String text, long sourceSeq) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher matcher = HOOK_FIELDS.matcher(text.substring(text.indexOf(HOOK_MARKER)));
        while (matcher.find() && values.size() < 32) values.put(matcher.group(1), matcher.group(2));
        boolean audio = prefix != null && (prefix.startsWith("ao") || prefix.startsWith("ffmpeg/audio"));
        String event = text.contains("audio-write ") ? "audio.output.write" : text.contains("audio-route ") ? "audio.route"
                : text.contains("audio-config ") ? "audio.output.configure" : text.contains("audio-lifecycle ") ? "audio.output.lifecycle"
                : text.contains("codec-candidate ") || text.contains("codec-profile ") ? audio ? "audio.candidates" : "video.candidates"
                : "mpv.decoder.attempt";
        Long code = integer(values.get("code"));
        boolean failed = code != null && code < 0 || Set.of("query-failed", "jvm-unavailable", "no-eligible-candidate", "null").contains(values.getOrDefault("outcome", ""));
        if (!audio && failed) lastVideoFailure = text.substring(0, Math.min(240, text.length()));
        log.emitNative(nativeOwner, event, "packaged-native-hook-v1", "native-load-start-context; log-media-unconfirmed", prefix, e -> {
            e.observed("nativePrefix", prefix).observed("nativeLevel", level).observed("count", sourceSeq)
                    .observed("nativeHook", "AV-DIAG-01-v1").observed("operation", values.get("operation"))
                    .observed("phase", values.get("phase")).observed("result", values.get("outcome")).message(text);
            if (failed) e.severity("error").pin(cacheId + "-native-hook-failure");
            if (code != null) e.observed("errorCode", code);
            if (values.containsKey("name")) e.observed("decoderName", values.get("name"));
            if (values.containsKey("reason")) e.observed("reason", values.get("reason"));
            if (values.containsKey("stage")) e.observed("stage", values.get("stage"));
            if (values.containsKey("backend")) e.observed("backend", values.get("backend"));
            if (values.containsKey("mime")) e.observed("mime", values.get("mime"));
            if (values.containsKey("accepted") && !text.contains("audio-write ")) e.observed("accepted", "1".equals(values.get("accepted")));
            if (values.containsKey("encoding")) e.observed("pcm", Set.of("2", "3", "4", "21", "22").contains(values.get("encoding")));
            if (values.containsKey("offload")) e.observed("offload", "1".equals(values.get("offload")));
            if (values.containsKey("timestampValid")) e.observed("timestampValid", "1".equals(values.get("timestampValid")));
            if (values.containsKey("timestampAgeMs")) e.observed("timestampAgeMs", integer(values.get("timestampAgeMs")));
            if (values.containsKey("output")) e.observed("audioOutputId", cacheId + ":" + values.get("output"));
            String[][] mapping = {{"index", "candidateIndex"}, {"profile", "profile"}, {"rate", "sampleRate"}, {"channels", "channels"},
                    {"mask", "channelMask"}, {"encoding", "encoding"}, {"bufferBytes", "bufferSize"}, {"calls", "writeCalls"},
                    {"requested", "requestedBytes"}, {"accepted", "acceptedBytes"}, {"short", "shortWrites"}, {"zero", "zeroWrites"},
                    {"errors", "writeErrors"}, {"raw", "errorCode"}, {"maxUs", "maxWriteUs"}, {"headRaw", "headRaw"}, {"id", "routeId"}, {"type", "routeType"},
                    {"candidate", "candidateIndex"}, {"count", "candidateCount"}};
            for (String[] field : mapping) if (values.containsKey(field[0])) e.observed(field[1], integer(values.get(field[0])));
            if (values.containsKey("role")) e.observed("routeRole", values.get("role"));
            if (text.contains("audio-write ")) e.observed("metricScope", "AudioTrack owner: accepted bytes, raw return unit in message; head is existing backend cache");
        });
        if (failed) { partialFailure(); health(); }
    }

    private static Long integer(String value) { try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException ignored) { return null; } }

    synchronized void event(int event, long propertyGeneration) {
        if (event == MPVLib.MpvEvent.MPV_EVENT_START_FILE) {
            nativeOwner = pendingLoadOwner == null ? UNRESOLVED : pendingLoadOwner;
            pendingLoadOwner = null; nativeGeneration = propertyGeneration; lastVideoFailure = null;
            emitted.clear(); emittedMissing.clear(); emittedTracks = null;
        } else if (event == MPVLib.MpvEvent.MPV_EVENT_QUEUE_OVERFLOW) {
            // A dropped start/end boundary invalidates media association until a new load.
            nativeOwner = UNRESOLVED; pendingLoadOwner = null; lastVideoFailure = null;
        }
        if (!PlaybackDiagnosticCollector.enabled()) return;
        capture();
        if (event == MPVLib.MpvEvent.MPV_EVENT_QUEUE_OVERFLOW) nativeOverflow++;
        log.emit(nativeOwner, "mpv.event", "mpv-event-callback", "native-load-start-context", e -> e.observed("value", event).observed("propertyGeneration", propertyGeneration)
                .observed("stage", event == MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART ? "playback-restart; not frame-present" : "native-event"));
        health();
    }

    void option(String property, String value, int result, boolean runtime) {
        if ("msg-level".equals(property)) requestedMsgLevel = value;
        if (!propertyAllowed(property) && !Set.of("vo", "ao", "ad", "gpu-context", "audio-spdif", "msg-level", "hwdec-codecs").contains(property)) return;
        log.emit("mpv.option", "mpv-option-api", e -> e.observed("property", property).requested("value", value)
                .observed("errorCode", result).observed("phase", runtime ? "runtime-set-return" : "pre-init-set-return")
                .unknown("result", PENDING_CALLBACK));
    }

    synchronized void propertyChanged(String property) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        if (property.equals("vid") || property.equals("aid") || property.equals("track-list")) { capture(); partialFailure(); health(); }
    }

    void registration(String property, int format, int result) {
        log.emit("mpv.collector.health", "mpv-observe-property", e -> e.observed("property", property)
                .observed("format", format).observed("registrationResult", result)
                .observed("metricScope", "registration result; property value availability reported separately"));
    }

    void command(String operation, long id, int result, String phase, long elapsedMs) {
        boolean reply = "completed".equals(phase);
        log.emit(reply ? UNRESOLVED : log.context(), "mpv.command.result", "mpv-command-api",
                reply ? "operation-id-only; media-unconfirmed" : "controller-request", e -> e.observed("operation", operation).observed("operationId", id)
                .observed("errorCode", result).observed("phase", phase).observed("durationMs", elapsedMs));
    }

    synchronized void end(int reason, int error) {
        felBindProbe.end();
        if (closed) return;
        log.emit("mpv.event", "mpv-controller-end", e -> e.observed("reason", reason).observed("errorCode", error));
        if (nativeOwner == log.context()) tick(true);
        log.end("controller-end:" + reason); closed = true;
    }

    synchronized void endFile(int reason, int error) {
        log.emit(nativeOwner, "mpv.event", "mpv-end-file", "native-load-start-context", e -> e
                .observed("reason", reason).observed("errorCode", error).observed("propertyGeneration", nativeGeneration));
        tick(true);
        // begin() already closed an older controller attempt. Its late native end
        // remains evidence for that owner and must never close the new attempt.
        if (nativeOwner == log.context() && !closed) {
            felBindProbe.end();
            log.end("end-file:" + reason); closed = true;
        }
    }

    synchronized void tick(boolean force) {
        if (!PlaybackDiagnosticCollector.enabled()) { felBindProbe.cancel(); return; }
        capture();
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastTickMs < 5000) return;
        if (categories != DebugLogStore.categories()) {
            categories = DebugLogStore.categories(); emitted.clear(); emittedMissing.clear(); emittedTracks = null;
        }
        lastTickMs = now;
        MpvPropertySnapshot.DiagnosticSnapshot state = snapshot.diagnosticSnapshot(capture);
        Context owner = state.generation() == nativeGeneration ? nativeOwner : UNRESOLVED;
        for (Map.Entry<String, Integer> registration : state.registrations().entrySet()) {
            if (!propertyAllowed(registration.getKey()) || state.values().containsKey(registration.getKey())) continue;
            if (!force && !emittedMissing.add(registration.getKey())) continue;
            log.emit(owner, "mpv.runtime", "mpv-property-observer-cache", "native-load-start-context", e -> e.observed("property", registration.getKey())
                    .observed("registrationResult", registration.getValue())
                    .unknown("value", registration.getValue() < 0 ? NOT_SUPPORTED : NOT_COLLECTED));
        }
        for (Map.Entry<String, MpvPropertySnapshot.DiagnosticValue> entry : state.values().entrySet()) {
            String property = entry.getKey(); MpvPropertySnapshot.DiagnosticValue value = entry.getValue();
            MpvPropertySnapshot.DiagnosticValue prior = emitted.put(property, value);
            emittedMissing.remove(property);
            boolean clock = property.startsWith("time-pos") || property.equals("avsync") || property.endsWith("frame-count");
            if (!force && !clock && prior != null && prior.generation() == value.generation()
                    && java.util.Objects.equals(prior.value(), value.value())) continue;
            long age = Math.max(0, now - value.updatedAtMs());
            String event = property.equals("file-format") || property.equals("seekable") || property.equals("current-edition") ? "media.container"
                    : property.equals("avsync") || property.equals("audio-delay") ? "audio.sync"
                    : property.equals("mpv-version") || property.equals("ffmpeg-version") ? "mpv.init"
                    : property.startsWith("audio-") || property.equals("current-ao") || property.equals("volume") || property.equals("mute")
                    ? "mpv.audio.path" : property.startsWith("video-") || property.startsWith("hwdec") || property.equals("current-vo")
                    ? "mpv.video.path" : "mpv.runtime";
            log.emit(owner, event, "mpv-property-observer-cache", "native-load-start-context", e -> {
                e.observed("property", property).observed("ageMs", age).observed("propertyGeneration", value.generation());
                if (value.value() == null) e.unknown("value", UNAVAILABLE);
                else if ((property.startsWith("time-pos") || property.equals("avsync")) && age > 15000) e.unknown("value", STALE);
                else e.observed("value", value.value());
            });
        }
        if (state.tracksObserved() && state.tracks().valid() && (force || emittedTracks != state.tracks())) for (Map<String, Object> track : state.tracks().entries()) {
            log.emit(owner, "mpv.tracks", "track-list-observer", "native-load-start-context", e -> e.observed("trackId", scalar(track.get("id")))
                    .observed("trackType", scalar(track.get("type"))).observed("selected", scalar(track.get("selected")))
                    .observed("flags", "default=" + scalar(track.get("default")) + ",forced=" + scalar(track.get("forced")) + ",albumart=" + scalar(track.get("albumart")))
                    .observed("codecs", scalar(track.get("codec"))));
        }
        emittedTracks = state.tracks();
        observeVideoProgress(state, owner, now);
        partialFailure(); health();
    }

    private void observeVideoProgress(MpvPropertySnapshot.DiagnosticSnapshot state, Context owner, long now) {
        Object fps = value(state, "container-fps"), pts = value(state, "video-pts");
        String gate = owner == UNRESOLVED || closed ? "inactive-or-unresolved-media" : null;
        if (gate == null) for (String property : new String[]{"pause", "paused-for-cache", "seeking", "idle-active", "eof-reached"}) {
            if (!Boolean.FALSE.equals(value(state, property))) { gate = property + "-active-or-unavailable"; break; }
        }
        boolean motion = state.tracksObserved() && state.tracks().valid() && state.tracks().entries().stream().anyMatch(track ->
                "video".equals(track.get("type")) && Boolean.TRUE.equals(track.get("selected")) && !Boolean.TRUE.equals(track.get("albumart")));
        if (gate == null && (!motion || !(fps instanceof Number number) || number.doubleValue() < 1)) gate = "no-motion-video-or-unknown-cadence";
        videoProgress.sample(log, owner, state.generation(), now, pts instanceof Number number ? number.doubleValue() : Double.NaN, gate,
                "cached MPV video-pts; not VO present or physical display; three 5-second samples");
    }

    private static Object scalar(Object value) { return value instanceof String || value instanceof Number || value instanceof Boolean ? value : null; }

    private void partialFailure() {
        if (lastVideoFailure == null) return;
        MpvPropertySnapshot.DiagnosticSnapshot state = snapshot.diagnosticSnapshot(capture);
        if (nativeOwner == UNRESOLVED || state.generation() != nativeGeneration) return;
        Object vid = value(state, "vid"), aid = value(state, "aid");
        boolean videoAvailable = state.tracksObserved() && state.tracks().valid()
                && state.tracks().entries().stream().anyMatch(track -> "video".equals(track.get("type")) && !Boolean.TRUE.equals(track.get("albumart")));
        boolean partial = videoAvailable && "no".equals(String.valueOf(vid)) && selected(aid);
        log.emit(nativeOwner, "mpv.output.failure", "native-error-and-observer-cache", "native-load-start-context; log-media-unconfirmed", e -> e.inferred()
                .observed("videoAvailable", state.tracksObserved() ? videoAvailable : null)
                .observed("videoSelected", vid == null ? null : selected(vid)).observed("audioSelected", aid == null ? null : selected(aid))
                .observed("videoPartialFailure", partial ? true : null).observed("lastError", lastVideoFailure)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE).pin(cacheId + "-failure"));
    }
    private static Object value(MpvPropertySnapshot.DiagnosticSnapshot state, String key) {
        MpvPropertySnapshot.DiagnosticValue item = state.values().get(key); return item == null ? null : item.value();
    }
    private static boolean selected(Object value) {
        if (value instanceof Number n) return n.longValue() > 0;
        try { return value != null && Long.parseLong(value.toString()) > 0; } catch (NumberFormatException ignored) { return false; }
    }

    private void health() {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        MpvPropertySnapshot.DiagnosticSnapshot state = snapshot.diagnosticSnapshot(capture);
        Context owner = state.generation() == nativeGeneration ? nativeOwner : UNRESOLVED;
        DiagnosticEvent event = new DiagnosticEvent("mpv.collector.health", owner.trace(), cacheId, owner.generation(), owner.attempt())
                .source("mpv", "runtime-properties", owner.role(), "cached-collector", "native-load-start-context", owner.mediaId(), log.context().mediaId(), nativeSeq.get(), SystemClock.elapsedRealtimeNanos())
                .observed("captureGeneration", capture).observed("nativeOverflow", nativeOverflow).observed("javaDropped", 0)
                .observed("lateEvents", state.lateEvents()).observed("nodeErrors", state.nodeErrors())
                .observed("firstSeenMs", firstNativeMs == 0 ? null : firstNativeMs).observed("lastSeenMs", lastNativeMs == 0 ? null : lastNativeMs)
                .observed("subscriptionLevel", "terminal-default (packaged JNI source)").requested("msgLevel", requestedMsgLevel)
                .observed("msgLevel", value(state, "options/msg-level")).unknown("sourceFiltered", NOT_OBSERVABLE)
                .observed("lastError", lastVideoFailure).observed("nativeHook", "AV-DIAG-01-v1: selector operations and AudioTrack; physical output not observable")
                .observed("registrationResult", "registered=" + state.registrations().size() + ",failed=" + state.registrations().values().stream().filter(code -> code < 0).count());
        DebugLogStore.collectorHealth(cacheId, event, nativeOverflow > 0 || state.lateEvents() > 0 || state.nodeErrors() > 0, capture);
    }
}
