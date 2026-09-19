package com.fongmi.android.tv.player;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticAccess;
import com.github.catvod.crawler.diagnostics.DiagnosticCapture;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.google.gson.JsonObject;
import java.lang.ref.WeakReference;
import java.util.Set;

/** Product controls publish requests only; exporters never invoke a player. */
public final class DiagnosticControls {
    public static final DiagnosticAccess ACCESS = new DiagnosticAccess();
    public static final String[] SYMPTOMS = {"黑屏", "画面不动", "无声", "断音", "音画不同步", "其他"};
    private static volatile WeakReference<PlaybackDiagnosticCollector> latest = new WeakReference<>(null);
    private static volatile WeakReference<FelBindProbeControl> felProbe = new WeakReference<>(null);
    private DiagnosticControls() {}

    static void playback(PlaybackDiagnosticCollector collector) {
        FelBindProbeControl probe = felProbe.get();
        if (probe != null && !probe.owner().equals(collector.instanceId())) probe.cancel();
        latest = new WeakReference<>(collector);
        DiagnosticCapture.Session active = DiagnosticCapture.current();
        PlaybackDiagnosticCollector.Context owner = collector.context();
        if (active != null && (!active.instance().equals(collector.instanceId()) || active.attempt() != owner.attempt())) DiagnosticCapture.stop("playback-changed");
    }

    public static void mark(String symptom) {
        if (!Set.of(SYMPTOMS).contains(symptom)) throw new IllegalArgumentException("请选择一种故障现象");
        if (!DebugLogStore.isEnabled()) throw new IllegalStateException("请先开启调试日志");
        publish("diag.user-mark", e -> e.userReported().observed("symptom", symptom)
                .observed("captureId", PlaybackDiagnosticCollector.id("incident"))
                .requested("requestedBeforeMs", 30_000).requested("requestedAfterMs", 15_000).pin("last-user-mark"));
    }

    public static synchronized DiagnosticCapture.Session startDepth(int seconds) {
        FelBindProbeControl probe = felProbe.get();
        if (probe != null && probe.active()) throw new IllegalStateException("请先结束 FEL 绑定对照");
        PlaybackDiagnosticCollector collector = latest.get();
        if (collector == null || collector.ended()) throw new IllegalStateException("请先开始播放");
        PlaybackDiagnosticCollector.Context owner = collector.context();
        return DiagnosticCapture.start(owner.trace(), collector.instanceId(), owner.generation(), owner.attempt(), seconds, collector.protectedMedia());
    }

    public static void registerFelBindProbe(FelBindProbeControl probe) {
        felProbe = new WeakReference<>(probe);
    }

    public static synchronized void startFelBindProbe() {
        if (!DebugLogStore.isEnabled()) throw new IllegalStateException("请先开启调试日志");
        if (!DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories
                .nativePrefix("vo/gpu-next/aimagereader"))) throw new IllegalStateException("请先开启视频诊断日志分类");
        if (DiagnosticCapture.current() != null) throw new IllegalStateException("请先停止深度统计");
        PlaybackDiagnosticCollector collector = latest.get();
        FelBindProbeControl probe = felProbe.get();
        if (collector == null || collector.ended() || collector.protectedMedia()
                || !"mpv".equals(collector.engine()) || probe == null
                || !probe.owner().equals(collector.instanceId()))
            throw new IllegalStateException("当前播放不支持 FEL 绑定对照");
        probe.start();
        publish("diag.capture", e -> e.userReported().requested("operation", "fel-bind-start")
                .requested("durationMs", 30_000).observed("metricScope", "record-only; no pixel access"));
    }

    public static void stopFelBindProbe() {
        FelBindProbeControl probe = felProbe.get();
        if (probe != null) probe.cancel();
    }

    public static void forTrace(String trace, String name, String source, java.util.function.Consumer<DiagnosticEvent> facts) {
        if (!DebugLogStore.acceptsEvent(name)) return;
        PlaybackDiagnosticCollector collector = latest.get();
        if (collector != null && collector.context().trace().equals(trace)) collector.emit(collector.context(), name, source, "monitor-trace-context", facts);
        else {
            DiagnosticEvent event = new DiagnosticEvent(name, trace, "monitor", 0, 0);
            facts.accept(event); DebugLogStore.event(event);
        }
    }

    private static void publish(String event, java.util.function.Consumer<DiagnosticEvent> facts) {
        PlaybackDiagnosticCollector collector = latest.get();
        if (collector != null) collector.emit(collector.context(), event, "user-control", "selected-playback-context", facts);
        else {
            DiagnosticEvent value = new DiagnosticEvent(event, "none", "user", 0, 0); facts.accept(value); DebugLogStore.event(value);
        }
    }

    public static JsonObject status() {
        JsonObject result = new JsonObject();
        PlaybackDiagnosticCollector collector = latest.get();
        result.addProperty("playingContext", collector != null && !collector.ended());
        if (collector != null) {
            PlaybackDiagnosticCollector.Context owner = collector.context();
            result.addProperty("trace", owner.trace()); result.addProperty("attempt", owner.attempt());
            result.addProperty("engine", collector.engine()); result.addProperty("protectedMedia", collector.protectedMedia());
        }
        DiagnosticCapture.Session capture = DiagnosticCapture.current();
        result.addProperty("deepRemainingMs", capture == null ? 0 : capture.remainingMs());
        FelBindProbeControl probe = felProbe.get();
        boolean ownsProbe = collector != null && probe != null && !collector.ended()
                && probe.owner().equals(collector.instanceId());
        result.addProperty("felBindProbeState", ownsProbe ? probe.state() : "unavailable");
        result.addProperty("felBindProbeActive", ownsProbe && probe.active());
        result.addProperty("categories", com.github.catvod.crawler.diagnostics.DiagnosticCategories.summary(DebugLogStore.categories()));
        return result;
    }
}
