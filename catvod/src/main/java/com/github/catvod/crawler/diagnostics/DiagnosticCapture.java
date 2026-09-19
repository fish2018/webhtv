package com.github.catvod.crawler.diagnostics;

import com.github.catvod.crawler.DebugLogStore;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Explicit, bounded opt-in. No media data, player callback or disk I/O is owned here. */
public final class DiagnosticCapture {
    public record Session(String id, String trace, String instance, long generation, long attempt,
                          long captureGeneration, long deadlineNs) {
        public long remainingMs() { return Math.max(0, (deadlineNs - System.nanoTime()) / 1_000_000); }
    }
    private static volatile Session session;
    private static final java.util.concurrent.atomic.AtomicInteger pixels = new java.util.concurrent.atomic.AtomicInteger();
    private static ScheduledExecutorService timer;
    private static ScheduledFuture<?> expiry;
    private DiagnosticCapture() {}

    public static synchronized Session start(String trace, String instance, long generation, long attempt,
                                             int seconds, boolean protectedMedia) {
        if (!DebugLogStore.isEnabled()) throw new IllegalStateException("请先开启调试日志");
        if (protectedMedia) throw new IllegalStateException("受保护内容不能进行深度内容统计");
        if (trace == null || "none".equals(trace) || attempt < 1) throw new IllegalStateException("当前没有可关联的播放，请先播放再操作");
        stop("replaced");
        int duration = Math.min(120, Math.max(1, seconds));
        Session value = new Session(UUID.randomUUID().toString(), trace, instance, generation, attempt,
                DebugLogStore.captureGeneration(), System.nanoTime() + duration * 1_000_000_000L);
        pixels.set(0); session = value;
        if (timer == null) timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "diagnostic-expiry"); thread.setDaemon(true); return thread;
        });
        expiry = timer.schedule(() -> expire(value), duration, TimeUnit.SECONDS);
        event(value, "started", duration * 1000L);
        return value;
    }

    private static synchronized void expire(Session expected) { if (session == expected) stop("expired"); }

    public static Session current() {
        Session value = session;
        return value != null && DebugLogStore.isEnabled() && value.captureGeneration == DebugLogStore.captureGeneration()
                && System.nanoTime() < value.deadlineNs ? value : null;
    }

    public static Session current(String trace, long generation, long attempt) {
        Session value = current();
        return value != null && value.trace.equals(trace) && value.generation == generation && value.attempt == attempt ? value : null;
    }

    public static int claimPixel(Session expected) {
        if (current() != expected) return -1;
        int index = pixels.getAndIncrement();
        return index < 3 ? index : -1;
    }

    public static synchronized void stop(String reason) {
        Session value = session; session = null;
        if (expiry != null) { expiry.cancel(false); expiry = null; }
        if (value != null) event(value, reason, 0);
    }

    private static void event(Session value, String reason, long duration) {
        DebugLogStore.event(new DiagnosticEvent("diag.capture", value.trace, value.instance, value.generation, value.attempt)
                .observed("captureId", value.id).observed("reason", reason).observed("expiresMs", duration)
                .observed("mode", "bounded-depth").observed("protectedMedia", false).pin("depth-capture"));
    }
}
