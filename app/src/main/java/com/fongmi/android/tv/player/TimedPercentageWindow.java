package com.fongmi.android.tv.player;

import java.util.ArrayDeque;
import java.util.Deque;

/** Time-weighted percentage statistics over a bounded elapsed-realtime window. */
final class TimedPercentageWindow {

    private final long windowMs;
    private final Deque<Sample> samples;
    private double current;
    private long lastSampleAtMs;

    TimedPercentageWindow(long windowMs) {
        this.windowMs = Math.max(1, windowMs);
        this.samples = new ArrayDeque<>();
        reset();
    }

    void add(long endAtMs, long durationMs, double percent) {
        if (!Double.isFinite(percent)) return;
        long end = Math.max(0, endAtMs);
        long duration = Math.max(0, durationMs);
        long start = duration > end ? 0 : end - duration;
        current = Math.max(0, percent);
        lastSampleAtMs = end;
        samples.addLast(new Sample(start, end, current));
        prune(end);
    }

    Stats snapshot(long nowMs) {
        long now = Math.max(0, nowMs);
        prune(now);
        if (!Double.isFinite(current) || samples.isEmpty()) return Stats.unavailable();
        long cutoff = Math.max(0, now - windowMs);
        double weightedSum = 0;
        long weightedDuration = 0;
        double peak = current;
        int count = 0;
        for (Sample sample : samples) {
            if (sample.endAtMs() < cutoff) continue;
            peak = Math.max(peak, sample.percent());
            count++;
            long overlapStart = Math.max(cutoff, sample.startAtMs());
            long overlapEnd = Math.min(now, sample.endAtMs());
            long overlap = Math.max(0, overlapEnd - overlapStart);
            if (overlap <= 0) continue;
            weightedSum += sample.percent() * overlap;
            weightedDuration += overlap;
        }
        double average = weightedDuration > 0
                ? weightedSum / weightedDuration : current;
        return new Stats(true, current, average, peak, count, lastSampleAtMs);
    }

    void reset() {
        samples.clear();
        current = Double.NaN;
        lastSampleAtMs = -1;
    }

    private void prune(long nowMs) {
        long cutoff = Math.max(0, nowMs - windowMs);
        while (!samples.isEmpty() && samples.peekFirst().endAtMs() <= cutoff) {
            samples.removeFirst();
        }
        if (lastSampleAtMs >= 0 && lastSampleAtMs <= cutoff && samples.isEmpty()) {
            current = Double.NaN;
            lastSampleAtMs = -1;
        }
    }

    record Stats(boolean available, double current, double average, double peak,
                 int sampleCount, long sampledAtMs) {

        static Stats unavailable() {
            return new Stats(false, 0, 0, 0, 0, -1);
        }
    }

    private record Sample(long startAtMs, long endAtMs, double percent) {
    }
}
