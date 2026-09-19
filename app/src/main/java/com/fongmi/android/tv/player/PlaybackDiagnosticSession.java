package com.fongmi.android.tv.player;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Controller evidence only. Decoder, output and physical presentation are separate collectors. */
public final class PlaybackDiagnosticSession {
    interface Sink {
        boolean enabled();
        long generation();
        void emit(DiagnosticEvent event);
    }
    private static final AtomicLong INSTANCES = new AtomicLong();
    private static final List<WeakReference<PlaybackDiagnosticSession>> ACTIVE = new ArrayList<>();
    private static final Pattern DETAIL = Pattern.compile("(?:^|\\s)(player|decode|headers|source)=([^\\s]+)");
    private static final Sink LIVE = new Sink() {
        @Override public boolean enabled() { return DebugLogStore.isEnabled(); }
        @Override public long generation() { return DebugLogStore.captureGeneration(); }
        @Override public void emit(DiagnosticEvent event) { DebugLogStore.event(event); }
    };

    private final Sink sink;
    private final String instance = "controller-" + INSTANCES.incrementAndGet();
    private String trace = "none", lastStage = "none";
    private long mediaGeneration, attempt, capturedGeneration = -1, startedAtMs;
    private Integer playerType, decode;
    private boolean active;

    public PlaybackDiagnosticSession() {
        this(LIVE);
        synchronized (ACTIVE) {
            ACTIVE.removeIf(reference -> reference.get() == null);
            if (ACTIVE.size() >= 64) ACTIVE.remove(0);
            ACTIVE.add(new WeakReference<>(this));
        }
    }

    PlaybackDiagnosticSession(Sink sink) { this.sink = sink; }

    public static void captureActive(long nowMs) {
        List<PlaybackDiagnosticSession> sessions = new ArrayList<>();
        synchronized (ACTIVE) {
            for (WeakReference<PlaybackDiagnosticSession> reference : ACTIVE) {
                PlaybackDiagnosticSession session = reference.get();
                if (session != null) sessions.add(session);
            }
        }
        for (PlaybackDiagnosticSession session : sessions) session.captureLate(nowMs);
    }

    public synchronized void begin(String trace, long nowMs) {
        end("replaced");
        this.trace = trace; mediaGeneration++; attempt = 0;
        startedAtMs = nowMs; active = true; lastStage = "request-pending";
        playerType = decode = null; capturedGeneration = -1;
        if (sink.enabled()) capture(false, nowMs);
    }

    private synchronized void captureLate(long nowMs) {
        if (active && sink.enabled() && capturedGeneration != sink.generation()) capture(true, nowMs);
    }

    private void capture(boolean late, long nowMs) {
        capturedGeneration = sink.generation();
        DiagnosticEvent event = event("diag.session.begin")
                .observed("mode", "standard").observed("captureStartedLate", late)
                .observed("association", "controller-current; source callback media is not collected")
                .unknown("engineInstance", NOT_COLLECTED).unknown("role", UNKNOWN)
                .coverage("session", KNOWN).coverage("deviceInfo", KNOWN).coverage("config", KNOWN)
                .coverage("request", KNOWN).coverage("lifecycle", KNOWN).coverage("attemptEnd", KNOWN).coverage("health", KNOWN);
        for (String missing : List.of("nativeLibraries", "display", "configChanges", "input", "inputRoute", "container", "tracks",
                "drm", "clock", "resources", "decoder", "surface", "audioOutput")) event.coverage(missing, NOT_COLLECTED);
        sink.emit(event.pin(trace + "-session"));
        if (late) sink.emit(event("play.lifecycle").observed("reason", "capture-started-late")
                .unknown("controllerStage", NOT_COLLECTED).unknown("video", NOT_COLLECTED).unknown("audio", NOT_COLLECTED)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE).pin(trace + "-late"));
    }

    public synchronized void stage(String stage, String detail, long nowMs) {
        if (!active || !sink.enabled()) return;
        if (capturedGeneration != sink.generation()) capture(true, nowMs);
        Integer oldPlayer = playerType, oldDecode = decode;
        String signalSource = null;
        Integer headers = null;
        Matcher matcher = DETAIL.matcher(detail == null ? "" : detail);
        while (matcher.find()) {
            String name = matcher.group(1), value = matcher.group(2);
            if ("source".equals(name)) signalSource = value;
            else {
                try {
                    int number = Integer.parseInt(value);
                    if (number < 0) continue;
                    if ("player".equals(name)) playerType = number;
                    else if ("decode".equals(name)) decode = number;
                    else headers = number;
                } catch (NumberFormatException ignored) {}
            }
        }
        if ("prepare".equals(stage)) {
            if (attempt > 0) endAttempt("controller-reprepare");
            attempt++;
            sink.emit(event("config.snapshot").requested("playerType", playerType).requested("decode", decode)
                    .unknown("decoder", NOT_COLLECTED).unknown("audioOutput", NOT_COLLECTED).pin(trace + "-config-" + attempt));
        }
        if (!java.util.Objects.equals(oldPlayer, playerType) || !java.util.Objects.equals(oldDecode, decode)) {
            sink.emit(event("config.change").observed("oldValue", "player=" + oldPlayer + ",decode=" + oldDecode)
                    .requested("newValue", "player=" + playerType + ",decode=" + decode)
                    .observed("reason", stage).observed("phase", "controller-request")
                    .unknown("result", PENDING_CALLBACK));
        }
        lastStage = stage;
        String name = switch (stage) {
            case "request" -> "play.request";
            case "parse-complete" -> "resolve.result";
            case "tracks" -> "media.tracks";
            default -> "play.lifecycle";
        };
        DiagnosticEvent event = event(name).observed("controllerStage", stage).observed("elapsedMs", Math.max(0, nowMs - startedAtMs));
        if (playerType != null) event.requested("playerType", playerType);
        if (headers != null) event.observed("headersCount", headers);
        if (signalSource != null) event.observed("signalSource", signalSource);
        if ("tracks".equals(stage)) event.observed("tracksSummary", detail == null ? "" : detail);
        event.unknown("video", NOT_COLLECTED).unknown("audio", NOT_COLLECTED)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE);
        sink.emit(event.pin(trace + "-" + stage));
    }

    public synchronized void end(String reason) {
        if (!active) return;
        if (sink.enabled()) {
            if (capturedGeneration != sink.generation()) capture(true, startedAtMs);
            endAttempt(reason);
        }
        active = false;
    }

    private void endAttempt(String reason) {
        sink.emit(event("play.attempt.end").observed("closed", true).observed("reason", reason)
                .observed("lastNormalLayer", "controller-signal:" + lastStage)
                .unknown("outcome", UNKNOWN).unknown("video", NOT_COLLECTED).unknown("audio", NOT_COLLECTED)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE)
                .observed("missingEvidence", "decoder / video submission / audio output / user observation")
                .observed("nextStep", "Collect the missing output layer during one reproduction (D1/D2/D3 collectors).")
                .pin(trace + "-end-" + attempt));
    }

    private DiagnosticEvent event(String event) { return new DiagnosticEvent(event, trace, instance, mediaGeneration, attempt); }
}
