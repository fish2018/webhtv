package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonObject;

import java.util.Set;

/** Versioned, allowlisted facts. No arbitrary object serialization or media content. */
public final class DiagnosticEvent {
    public enum Status {
        KNOWN("known"), UNKNOWN("unknown"), NOT_COLLECTED("not-collected"), NOT_SUPPORTED("not-supported"),
        NOT_APPLICABLE("not-applicable"), PERMISSION_DENIED("permission-denied"), STALE("stale"),
        READ_ERROR("read-error"), TIMED_OUT("timed-out"), PENDING_CALLBACK("pending-callback"),
        UNAVAILABLE("unavailable"), NOT_OBSERVABLE("not-observable");
        public final String value;
        Status(String value) { this.value = value; }
    }

    private static final Set<String> EVENTS = Set.of("diag.session.begin", "env.device", "config.snapshot", "play.request",
            "resolve.result", "media.tracks", "play.lifecycle", "play.attempt.end", "diag.health", "diag.error",
            "input.open", "input.read.summary", "drm.state", "play.clock", "env.display", "display.change",
            "video.candidates", "video.decoder.attempt", "video.configure", "video.output.format", "video.sample.summary",
            "video.output.summary", "video.first-output", "video.surface-render-callback", "video.flush-reuse-release", "video.error",
            "surface.created", "surface.changed", "surface.destroyed", "surface.bind", "surface.resize", "surface.visibility",
            "video.presentation.source", "video.effects", "audio.track", "audio.candidates", "audio.decoder.attempt",
            "audio.decoder.output", "audio.processing", "audio.output.configure", "audio.output.write", "audio.output.playhead",
            "audio.route", "audio.focus", "audio.volume", "audio.output.lifecycle", "audio.error", "audio.sync",
            "mpv.init", "mpv.option", "mpv.event", "mpv.tracks", "mpv.video.path", "mpv.audio.path", "mpv.runtime",
            "mpv.decoder.attempt", "mpv.output.failure", "mpv.command.result", "mpv.collector.health", "mpv.native.output",
            "ijk.event", "ijk.runtime", "process.recovery", "env.native", "config.change", "input.route", "media.container",
            "play.resources", "diag.user-mark", "diag.comparison", "diag.capture", "diag.export", "diag.observation",
            "video.pixel-probe", "audio.pcm-probe");
    private static final Set<String> FIELDS = Set.of("mode", "captureStartedLate", "reason", "controllerStage", "elapsedMs",
            "playerType", "decode", "headersCount", "tracksSummary", "signalSource", "video", "audio", "physicalVideo", "audibility",
            "appVersion", "versionCode", "buildTime", "buildTag", "gitRevision", "media3Version", "flavor", "abi", "process64Bit",
            "android", "api", "targetSdk", "manufacturer", "model", "device", "hardwareAccelerated", "source", "role",
            "session", "deviceInfo", "nativeLibraries", "display", "config", "configChanges", "request", "input", "inputRoute",
            "container", "tracks", "drm", "lifecycle", "clock", "resources", "attemptEnd", "health", "decoder", "surface", "audioOutput",
            "closed", "outcome", "lastNormalLayer", "missingEvidence", "nextStep", "association", "engineInstance",
            "property", "value", "operation", "operationId", "phase", "result", "domain", "stage", "errorId", "errorCode",
            "javaClass", "message", "diagnosticInfo", "recoverable", "transient", "chunk", "chunkCount", "truncated",
            "decoderId", "decoderName", "surfaceId", "oldSurfaceId", "audioOutputId", "seekEpoch", "epoch", "format",
            "mime", "codecs", "profile", "level", "width", "height", "frameRate", "bitrate", "sampleRate", "channels",
            "channelMask", "channelIndexMask", "mapping", "encoding", "pcm", "secure", "tunneling", "offload", "softwareOnly",
            "vendor", "candidateIndex", "candidateCount", "accepted", "fallbackAllowed", "reuseResult", "discardReasons",
            "csdCount", "csdBytes", "csdDigest", "selected", "available", "trackId", "trackType", "support", "flags", "language",
            "videoAvailable", "videoSelected", "audioAvailable", "audioSelected", "videoEvidenceLevel", "audioEvidenceLevel",
            "videoPartialFailure", "videoOutputObserved", "audioOutputObserved", "failedBoundary", "firstError", "lastError",
            "durationMs", "positionMs", "bufferedMs", "windowMs", "count", "bytes", "firstPtsUs", "lastPtsUs", "lastAgeMs",
            "inputBuffers", "outputBuffers", "submittedBuffers", "discardedBuffers", "encryptedBuffers", "eosBuffers",
            "ptsRegressions", "droppedBuffers", "skippedBuffers", "renderCallbacks", "metricScope", "physicalDisplay",
            "writeCalls", "requestedBytes", "acceptedBytes", "shortWrites", "zeroWrites", "writeErrors", "maxWriteUs",
            "sessionId", "bufferSize", "bufferUnit", "playState", "state", "headRaw", "headFrames", "headDeltaFrames",
            "timestampValid", "timestampFrames", "timestampNs", "timestampAgeMs", "underruns", "routeId", "routeType",
            "routeRole", "usage", "contentType", "volume", "systemVolume", "systemMaxVolume", "systemMinVolume",
            "muted", "fixedVolume", "focusOwner", "focusRequest", "suppressionReason", "playWhenReady", "isPlaying",
            "speed", "skipSilence", "attached", "shown", "visibility", "windowVisibility", "alpha", "ancestorAlpha",
            "holderWidth", "holderHeight", "valid", "surfaceType", "presentation", "artworkVisible", "shutterVisible",
            "rotation", "displayId", "modeId", "refreshHz", "resizeMode", "requestedWidth", "requestedHeight",
            "loadTaskId", "rangeStart", "rangeLength", "contentTypeHeader", "contentLength", "contentRange", "httpCode",
            "nativeLevel", "nativePrefix", "sourceFiltered", "nativeOverflow", "javaDropped", "lateEvents", "nodeErrors",
            "subscriptionLevel", "msgLevel", "captureGeneration", "sampleSpanMs", "registrationResult", "heartbeatAgeMs",
            "nativeHook", "unfinished", "previousRun", "exitReason", "exitStatus", "traceAvailable", "writerFailure",
            "firstSeenMs", "lastSeenMs", "sourceStage", "propertyGeneration", "ageMs", "symptom", "captureId", "expiresMs",
            "parameter", "oldValue", "newValue", "note", "userReported", "probeIndex", "channel", "frames", "rms", "peak",
            "zeroRatio", "clipped", "nonFinite", "luminance", "blackRatio", "changeRatio", "pixelCount", "protectedMedia",
            "processorIndex", "processorName", "active", "inputFormat", "outputFormat", "focusChange", "focusGain",
            "focusResult", "focusAction", "effectiveGain", "javaBytes", "nativeBytes", "lowRam", "trimLevel", "powerSave",
            "thermalStatus", "batteryLevel", "fingerprintDigest", "library", "buildId", "loadResult", "expectedDigest",
            "actualDigest", "manifestMatch", "backend", "seekable", "live", "retainedBeforeMs", "retainedAfterMs",
            "requestedBeforeMs", "requestedAfterMs", "complete", "operationCount", "keyframes", "firstInputMs", "firstOutputMs");

    private final JsonObject root = new JsonObject();
    private final JsonObject observed = new JsonObject();
    private final JsonObject requested = new JsonObject();
    private final JsonObject coverage = new JsonObject();
    private String pinKey;
    private boolean critical;
    private boolean truncated;

    public DiagnosticEvent(String event, String trace, String instance, long generation, long attempt) {
        if (!EVENTS.contains(event)) throw new IllegalArgumentException("Unknown diagnostic event");
        root.addProperty("schemaVersion", 1);
        root.addProperty("event", event);
        root.addProperty("level", "info");
        root.addProperty("trace", label(trace));
        root.addProperty("playerInstanceId", label(instance));
        root.addProperty("association", "controller-context-only");
        root.addProperty("eventMediaIdStatus", Status.NOT_COLLECTED.value);
        root.addProperty("engineStatus", Status.NOT_COLLECTED.value);
        root.addProperty("mediaGeneration", Math.max(0, generation));
        root.addProperty("attemptId", Math.max(0, attempt));
        root.addProperty("source", "app-controller");
        root.addProperty("evidenceClass", "observed");
        root.addProperty("status", Status.KNOWN.value);
        root.addProperty("ageMs", 0);
    }

    public DiagnosticEvent observed(String name, Object value) { put(observed, name, value, Status.KNOWN); return this; }
    public DiagnosticEvent requested(String name, Object value) { put(requested, name, value, Status.KNOWN); return this; }
    public DiagnosticEvent unknown(String name, Status status) { put(observed, name, null, status); return this; }
    public DiagnosticEvent coverage(String name, Status status) { put(coverage, name, status == Status.KNOWN ? true : null, status); return this; }
    public DiagnosticEvent pin(String key) { pinKey = label(key); return this; }
    public String pinKey() { return pinKey; }
    public String name() { return root.get("event").getAsString(); }
    public DiagnosticCategories.Category category() {
        if (observed.has("nativePrefix")) {
            JsonObject prefix = observed.getAsJsonObject("nativePrefix");
            if (prefix.has("value")) return DiagnosticCategories.nativePrefix(prefix.get("value").getAsString());
        }
        return DiagnosticCategories.event(name());
    }
    public boolean critical() { return critical || pinKey != null; }
    public boolean truncated() { return truncated; }

    /** A bounded native message, still sanitized, with explicit truncation rather than a silent label cut. */
    public DiagnosticEvent message(String text) {
        String safe = DiagnosticText.clean(text).text();
        JsonObject fact = new JsonObject();
        fact.addProperty("status", Status.KNOWN.value);
        fact.addProperty("value", safe.substring(0, Math.min(1800, safe.length())));
        observed.add("message", fact);
        truncated = safe.length() > 1800;
        observed("truncated", truncated);
        return this;
    }

    public DiagnosticEvent source(String engine, String version, String role, String source, String association,
                                  String eventMediaId, String currentMediaId, long sourceSeq, long capturedAtNs) {
        root.addProperty("engine", label(engine));
        root.addProperty("engineVersion", label(version));
        root.addProperty("engineStatus", Status.KNOWN.value);
        root.addProperty("role", label(role));
        root.addProperty("source", label(source));
        root.addProperty("association", label(association));
        root.addProperty("eventMediaIdStatus", eventMediaId == null ? Status.UNKNOWN.value : Status.KNOWN.value);
        if (eventMediaId != null) root.addProperty("eventMediaId", label(eventMediaId));
        if (currentMediaId != null) root.addProperty("currentMediaId", label(currentMediaId));
        root.addProperty("collectorSourceSeq", sourceSeq);
        root.addProperty("sourceCapturedAtNs", capturedAtNs);
        return this;
    }

    public DiagnosticEvent severity(String level) {
        if (!Set.of("fatal", "error", "warn", "info", "debug", "trace").contains(level))
            throw new IllegalArgumentException("Unknown diagnostic severity");
        root.addProperty("level", level);
        critical = "fatal".equals(level) || "error".equals(level) || "warn".equals(level);
        return this;
    }

    public DiagnosticEvent inferred() { root.addProperty("evidenceClass", "inferred"); return this; }
    public DiagnosticEvent userReported() { root.addProperty("evidenceClass", "user-reported"); return this; }

    public String json() {
        JsonObject result = root.deepCopy();
        result.addProperty("priority", critical() ? "critical" : "normal");
        if (observed.size() > 0) result.add("observed", observed.deepCopy());
        if (requested.size() > 0) result.add("requested", requested.deepCopy());
        if (coverage.size() > 0) result.add("collectors", coverage.deepCopy());
        String json = result.toString();
        if (json.length() > 4_000) throw new IllegalArgumentException("Diagnostic event exceeds schema budget");
        return json;
    }

    private static void put(JsonObject target, String name, Object value, Status status) {
        if (!FIELDS.contains(name) || status == null) throw new IllegalArgumentException("Unknown diagnostic field/status");
        JsonObject fact = new JsonObject();
        if (status == Status.KNOWN && value == null) status = Status.UNKNOWN;
        if (value instanceof Number number && !Double.isFinite(number.doubleValue())) status = Status.UNKNOWN;
        fact.addProperty("status", status.value);
        if (status == Status.KNOWN) {
            if (value instanceof Boolean bool) fact.addProperty("value", bool);
            else if (value instanceof Number number) fact.addProperty("value", number);
            else if (value instanceof String string) fact.addProperty("value", label(string));
            else throw new IllegalArgumentException("Only scalar diagnostic fields are allowed");
        }
        target.add(name, fact);
    }

    private static String label(String value) {
        if (value == null) return "none";
        String safe = DiagnosticText.clean(value).text();
        return safe.length() > 256 ? safe.substring(0, 240) + " [truncated]" : safe;
    }
}
