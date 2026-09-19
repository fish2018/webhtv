package com.fongmi.android.tv.player;

import android.media.MediaCodec;
import android.os.Build;
import android.os.SystemClock;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.github.catvod.crawler.diagnostics.DiagnosticText;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Per-engine evidence. Contexts are immutable and may safely outlive the current media. */
public final class PlaybackDiagnosticCollector {
    public record Context(String trace, long generation, long attempt, String mediaId, String role) {}
    private static final AtomicLong IDS = new AtomicLong();
    private static final WeakHashMap<Object, String> OBJECT_IDS = new WeakHashMap<>();
    private final String instance = id("player");
    private final String engine, version;
    private final AtomicLong sequence = new AtomicLong();
    private volatile Context current = new Context("none", 0, 0, null, "unresolved");
    private volatile int videoLevel = -1, audioLevel = -1;
    private volatile String firstError, lastError;
    private volatile boolean protectedMedia;
    private long captureGeneration = -1;
    private volatile boolean ended;

    public PlaybackDiagnosticCollector(String engine, String version) { this.engine = engine; this.version = version; }
    public static boolean enabled() { return DebugLogStore.isEnabled(); }
    public static String id(String kind) { return kind + "-" + IDS.incrementAndGet(); }
    public static synchronized String objectId(Object object, String kind) {
        return object == null ? "none" : OBJECT_IDS.computeIfAbsent(object, key -> id(kind));
    }
    public Context context() { return current; }
    public String instanceId() { return instance; }
    public String engine() { return engine; }
    public boolean ended() { return ended; }
    public boolean protectedMedia() { return protectedMedia; }
    public void protectedMedia(boolean value) { protectedMedia = value; }

    public synchronized Context begin(String trace, String role) {
        if (current.attempt() > 0) end("reprepare");
        boolean same = current.trace().equals(trace);
        current = new Context(PlaybackTrace.normalize(trace), current.generation() + (same ? 0 : 1),
                current.attempt() + 1, id("media"), role);
        videoLevel = audioLevel = -1; firstError = lastError = null; captureGeneration = -1; ended = false;
        if ("foreground".equals(role)) DiagnosticControls.playback(this);
        baseline(false);
        return current;
    }

    public synchronized void baseline() {
        baseline(true);
    }

    private void baseline(boolean late) {
        if (!enabled() || captureGeneration == DebugLogStore.captureGeneration()) return;
        if (captureGeneration != -1) { videoLevel = audioLevel = -1; firstError = lastError = null; }
        captureGeneration = DebugLogStore.captureGeneration();
        NativeLibraryDiagnostics.request();
        emit(current, "diag.session.begin", "engine-collector", "engine-context", e -> e
                .observed("mode", "standard").observed("captureStartedLate", late)
                .observed("engineInstance", instance).observed("captureGeneration", captureGeneration)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE)
                .pin(instance + "-baseline"));
    }

    public void emit(Context owner, String name, String source, String association, Consumer<DiagnosticEvent> facts) {
        emit(owner, name, source, association, null, facts);
    }

    public void emitNative(Context owner, String name, String source, String association, String prefix, Consumer<DiagnosticEvent> facts) {
        emit(owner, name, source, association, prefix == null ? "" : prefix, facts);
    }

    private void emit(Context owner, String name, String source, String association, String nativePrefix, Consumer<DiagnosticEvent> facts) {
        if (nativePrefix == null ? !DebugLogStore.acceptsEvent(name) : !DebugLogStore.isEnabled()
                || !DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.nativePrefix(nativePrefix))) return;
        try {
            Context ctx = owner == null ? new Context("none", 0, 0, null, "unresolved") : owner;
            DiagnosticEvent event = new DiagnosticEvent(name, ctx.trace(), instance, ctx.generation(), ctx.attempt())
                    .source(engine, version, ctx.role(), source, association, ctx.mediaId(), current.mediaId(),
                            sequence.incrementAndGet(), SystemClock.elapsedRealtimeNanos());
            facts.accept(event);
            DebugLogStore.event(event);
        } catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }
    }

    public void emit(String name, String source, Consumer<DiagnosticEvent> facts) {
        emit(current, name, source, "engine-context", facts);
    }

    public void evidence(Context owner, boolean video, int level) {
        if (owner != current || !enabled()) return;
        if (video) videoLevel = Math.max(videoLevel, level); else audioLevel = Math.max(audioLevel, level);
    }

    public void error(Context owner, String domain, String stage, Throwable error) {
        if (!enabled() || error == null) return;
        String errorId = id("error");
        if (owner == current) { if (firstError == null) firstError = errorId; lastError = errorId; }
        if (domain.startsWith("video") || domain.startsWith("audio"))
            emit(owner, domain.startsWith("video") ? "video.error" : "audio.error", domain, "exception-chain", e -> e
                    .severity("error").observed("errorId", errorId).observed("stage", stage)
                    .observed("javaClass", error.getClass().getName()).message(error.getMessage()));
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8 && visited.add(cause); depth++, cause = cause.getCause()) {
            Throwable item = cause;
            int index = depth;
            emit(owner, "diag.error", domain, "exception-chain", e -> {
                e.severity("error").observed("errorId", errorId).observed("domain", domain).observed("stage", stage)
                        .observed("chunk", index).observed("javaClass", item.getClass().getName())
                        .observed("message", String.valueOf(item.getMessage())).pin(instance + "-error");
                if (Build.VERSION.SDK_INT >= 21 && item instanceof MediaCodec.CodecException codec) {
                    e.observed("diagnosticInfo", codec.getDiagnosticInfo()).observed("recoverable", codec.isRecoverable())
                            .observed("transient", codec.isTransient());
                    if (Build.VERSION.SDK_INT >= 23) e.observed("errorCode", codec.getErrorCode());
                }
            });
        }
        String detail = DiagnosticText.throwable(error);
        int limit = Math.min(detail.length(), 2640), chunks = (limit + 219) / 220;
        for (int i = 0; i < chunks; i++) {
            int chunk = i;
            String part = detail.substring(i * 220, Math.min(limit, (i + 1) * 220));
            emit(owner, "diag.error", domain, "exception-detail", e -> e.severity("error")
                    .observed("errorId", errorId).observed("chunk", chunk).observed("chunkCount", chunks)
                    .observed("message", part).observed("truncated", detail.length() > limit));
        }
    }

    public synchronized void end(String reason) {
        if (ended) return;
        ended = true;
        Context owner = current;
        com.github.catvod.crawler.diagnostics.DiagnosticCapture.Session depth = com.github.catvod.crawler.diagnostics.DiagnosticCapture.current(owner.trace(), owner.generation(), owner.attempt());
        if (depth != null && depth.instance().equals(instance)) com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("playback-ended");
        emit(owner, "play.attempt.end", "engine-collector", "engine-context", e -> e
                .observed("reason", reason).observed("closed", true)
                .observed("videoEvidenceLevel", videoLevel < 0 ? null : videoLevel).observed("audioEvidenceLevel", audioLevel < 0 ? null : audioLevel)
                .observed("firstError", firstError).observed("lastError", lastError)
                .unknown("physicalVideo", NOT_OBSERVABLE).unknown("audibility", NOT_OBSERVABLE)
                .observed("missingEvidence", "physical presentation; per-collector unavailable fields")
                .pin(instance + "-end"));
    }
}
