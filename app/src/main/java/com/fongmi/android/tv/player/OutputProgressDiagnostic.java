package com.fongmi.android.tv.player;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import java.util.Objects;

/** Conservative observation over existing samples. Never requests recovery or changes playback. */
public final class OutputProgressDiagnostic {
    private final boolean video;
    private PlaybackDiagnosticCollector.Context owner;
    private long epoch, capture = -1, unchangedSince;
    private double previous = Double.NaN;
    private int unchangedSamples;
    private boolean reported;
    private String lastGate;

    public OutputProgressDiagnostic(boolean video) { this.video = video; }

    public void sample(PlaybackDiagnosticCollector log, PlaybackDiagnosticCollector.Context context,
            long currentEpoch, long nowMs, double progress, String gate, String metric) {
        if (!DebugLogStore.acceptsEvent(video ? "video.output.summary" : "audio.output.playhead") || context == null) {
            previous = Double.NaN; lastGate = null; return;
        }
        long generation = DebugLogStore.captureGeneration();
        if (owner != context || epoch != currentEpoch || capture != generation) {
            owner = context; epoch = currentEpoch; capture = generation;
            previous = Double.NaN; lastGate = null; reported = false;
        }
        if (gate == null && !Double.isFinite(progress)) gate = "output-metric-unavailable";
        if (gate != null) {
            previous = Double.NaN; unchangedSamples = 0; reported = false;
            if (!Objects.equals(lastGate, gate)) {
                String reason = gate;
                log.emit(context, "diag.observation", "output-progress-gate", "sample-owner", e -> e
                        .observed("video", video).observed("phase", "gated").observed("reason", reason).observed("metricScope", metric));
            }
            lastGate = gate; return;
        }
        lastGate = null;
        if (!Double.isFinite(previous) || Double.compare(progress, previous) != 0) {
            previous = progress; unchangedSince = nowMs; unchangedSamples = 0; reported = false; return;
        }
        unchangedSamples++;
        long duration = nowMs - unchangedSince;
        if (reported || unchangedSamples < 3 || duration < 15_000) return;
        reported = true;
        log.emit(context, "diag.observation", "output-progress-samples", "sample-owner", e -> e.inferred()
                .observed("video", video).observed("phase", "observation").observed("reason", "no-output-progress-observed")
                .observed("windowMs", duration).observed("count", unchangedSamples).observed("value", progress)
                .observed("epoch", currentEpoch).observed("metricScope", metric)
                .unknown(video ? "physicalVideo" : "audibility", DiagnosticEvent.Status.NOT_OBSERVABLE)
                .observed("nextStep", "correlate input, buffering, surface/route and user symptom; this is not a decoder failure")
                .pin(log.instanceId() + (video ? "-video-observation" : "-audio-observation")));
    }
}
