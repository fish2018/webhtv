package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class AdAudioConsumer implements PlaybackMediaSignalHub.Consumer, AutoCloseable {

    public interface Matcher {
        List<AudioFingerprintMatcher.MatchEvent> feed(short[] samples, int sampleRate, long captureStartMs);

        List<AudioFingerprintMatcher.MatchEvent> finish();
    }

    public interface MatcherFactory {
        Matcher create(AudioFingerprintRuleSet ruleSet);
    }

    public interface CandidateSink {
        void onCandidate(Candidate candidate);
    }

    public record Candidate(long sessionId, long generation, AudioFingerprintMatcher.MatchEvent event) {
        public Candidate {
            Objects.requireNonNull(event, "event");
        }
    }

    private final Object lock = new Object();
    private final MatcherFactory matcherFactory;
    private final CandidateSink candidateSink;
    private final AdAudioDiagnostics diagnostics;
    private final int queueCapacity;
    private final Executor worker;
    private final ArrayDeque<PlaybackMediaSignalHub.PcmFrame> queue = new ArrayDeque<>();

    private Matcher matcher;
    private AudioFingerprintRuleSet ruleSet;
    private long sessionId = Long.MIN_VALUE;
    private long generation = Long.MIN_VALUE;
    private long queueOverflowCount;
    private long matcherErrorCount;
    private boolean drainScheduled;
    private boolean closed;

    public AdAudioConsumer(MatcherFactory matcherFactory, CandidateSink candidateSink, int queueCapacity) {
        this(matcherFactory, candidateSink, queueCapacity, null);
    }

    public AdAudioConsumer(MatcherFactory matcherFactory, CandidateSink candidateSink,
                           int queueCapacity, Executor worker) {
        this(matcherFactory, candidateSink, queueCapacity, worker, new AdAudioDiagnostics());
    }

    public AdAudioConsumer(MatcherFactory matcherFactory, CandidateSink candidateSink,
                           int queueCapacity, Executor worker, AdAudioDiagnostics diagnostics) {
        this.matcherFactory = Objects.requireNonNull(matcherFactory, "matcherFactory");
        this.candidateSink = Objects.requireNonNull(candidateSink, "candidateSink");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.queueCapacity = queueCapacity;
        this.worker = worker;
    }

    public AdAudioConsumer(CandidateSink candidateSink, int queueCapacity) {
        this(ruleSet -> new AudioFingerprintMatcherAdapter(
                        new AudioFingerprintMatcher(ruleSet, AudioFingerprintMatcher.Config.conservative())),
                candidateSink, queueCapacity);
    }

    public AdAudioConsumer(CandidateSink candidateSink, int queueCapacity, Executor worker) {
        this(ruleSet -> new AudioFingerprintMatcherAdapter(
                        new AudioFingerprintMatcher(ruleSet, AudioFingerprintMatcher.Config.conservative())),
                candidateSink, queueCapacity, worker);
    }

    public AdAudioConsumer(CandidateSink candidateSink, int queueCapacity, Executor worker,
                           AdAudioDiagnostics diagnostics) {
        this(ruleSet -> new AudioFingerprintMatcherAdapter(
                        new AudioFingerprintMatcher(ruleSet, AudioFingerprintMatcher.Config.conservative())),
                candidateSink, queueCapacity, worker, diagnostics);
    }

    public void start(long sessionId, long generation, AudioFingerprintRuleSet ruleSet) {
        Objects.requireNonNull(ruleSet, "ruleSet");
        synchronized (lock) {
            if (closed) return;
            finishMatcherLocked();
            queue.clear();
            this.sessionId = sessionId;
            this.generation = generation;
            this.ruleSet = ruleSet;
            this.matcher = createMatcherLocked();
        }
    }

    @Override
    public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
        Objects.requireNonNull(frame, "frame");
        synchronized (lock) {
            if (closed || matcher == null) return;
            if (frame.sessionId() != sessionId || frame.generation() != generation) {
                diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
                return;
            }
            while (queue.size() >= queueCapacity) {
                queue.removeFirst();
                queueOverflowCount++;
                diagnostics.record(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
            }
            queue.addLast(frame);
            scheduleDrainLocked();
        }
    }

    @Override
    public void onLifecycle(PlaybackMediaSignalHub.Lifecycle event) {
        Objects.requireNonNull(event, "event");
        synchronized (lock) {
            if (closed || ruleSet == null) return;
            finishMatcherLocked();
            queue.clear();
            sessionId = event.sessionId();
            generation = event.generation();
            matcher = event.reason() == PlaybackMediaSignalHub.ResetReason.RELEASE
                    ? null
                    : createMatcherLocked();
        }
    }

    @Override
    public void onFailure(RuntimeException error) {
        synchronized (lock) {
            matcherErrorCount++;
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    public long sessionId() {
        synchronized (lock) {
            return sessionId;
        }
    }

    public long generation() {
        synchronized (lock) {
            return generation;
        }
    }

    public long queueOverflowCount() {
        synchronized (lock) {
            return queueOverflowCount;
        }
    }

    public long matcherErrorCount() {
        synchronized (lock) {
            return matcherErrorCount;
        }
    }

    public void drainForTest() {
        drainLoop();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            finishMatcherLocked();
            queue.clear();
        }
    }

    private void scheduleDrainLocked() {
        if (worker == null || drainScheduled) return;
        drainScheduled = true;
        try {
            worker.execute(this::drainLoop);
        } catch (RuntimeException e) {
            drainScheduled = false;
            matcherErrorCount++;
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void drainLoop() {
        while (true) {
            PlaybackMediaSignalHub.PcmFrame frame;
            List<AudioFingerprintMatcher.MatchEvent> events;
            long candidateSession;
            long candidateGeneration;
            synchronized (lock) {
                frame = queue.pollFirst();
                if (frame == null) {
                    drainScheduled = false;
                    return;
                }
                if (closed || matcher == null || frame.sessionId() != sessionId || frame.generation() != generation) continue;
                candidateSession = sessionId;
                candidateGeneration = generation;
                try {
                    events = matcher.feed(toPcm16(frame.monoSamples()), frame.sampleRate(), frame.captureStartTimeMs());
                } catch (RuntimeException e) {
                    matcherErrorCount++;
                    diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                    finishMatcherLocked();
                    matcher = createMatcherLocked();
                    continue;
                }
            }
            emit(candidateSession, candidateGeneration, events);
        }
    }

    private void emit(long sessionId, long generation, List<AudioFingerprintMatcher.MatchEvent> events) {
        if (events == null || events.isEmpty()) return;
        List<Candidate> candidates = new ArrayList<>(events.size());
        for (AudioFingerprintMatcher.MatchEvent event : events) {
            if (event != null) candidates.add(new Candidate(sessionId, generation, event));
        }
        for (Candidate candidate : candidates) {
            try {
                candidateSink.onCandidate(candidate);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private Matcher createMatcherLocked() {
        try {
            return matcherFactory.create(ruleSet);
        } catch (RuntimeException e) {
            matcherErrorCount++;
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return null;
        }
    }

    private void finishMatcherLocked() {
        if (matcher == null) return;
        try {
            matcher.finish();
        } catch (RuntimeException e) {
            matcherErrorCount++;
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
        matcher = null;
    }

    private static short[] toPcm16(float[] samples) {
        short[] result = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float value = samples[i];
            if (!Float.isFinite(value)) value = 0f;
            int sample = Math.round(value * 32768f);
            result[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
        }
        return result;
    }

    private static final class AudioFingerprintMatcherAdapter implements Matcher {
        private final AudioFingerprintMatcher matcher;

        private AudioFingerprintMatcherAdapter(AudioFingerprintMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public List<AudioFingerprintMatcher.MatchEvent> feed(short[] samples, int sampleRate, long captureStartMs) {
            AudioFingerprintMatcher.FeedResult result = matcher.feed(
                    new AudioFingerprintMatcher.PcmChunk(samples, sampleRate, 1, captureStartMs));
            return result.events();
        }

        @Override
        public List<AudioFingerprintMatcher.MatchEvent> finish() {
            return matcher.finish().events();
        }
    }
}
