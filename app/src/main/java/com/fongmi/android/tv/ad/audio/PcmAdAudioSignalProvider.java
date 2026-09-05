package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class PcmAdAudioSignalProvider implements AdAudioSignalProvider {

    public static final String ID = "pcm";

    private static final int DEFAULT_QUEUE_CAPACITY = 8;

    private final PlaybackMediaSignalHub hub;
    private final AdAudioConsumer.MatcherFactory matcherFactory;
    private final int queueCapacity;
    private final Executor worker;
    private final AdAudioDiagnostics diagnostics;

    private ProviderState state = ProviderState.DISABLED;
    private SessionContext context;
    private AdAudioRuleSnapshot rules;
    private Listener listener;
    private AdAudioConsumer consumer;
    private PlaybackMediaSignalHub.Registration registration;
    private PlaybackMediaSignalHub.CaptureLease captureLease;
    private long observedMatcherErrors;
    private boolean enabled;
    private boolean closed;

    public PcmAdAudioSignalProvider(PlaybackMediaSignalHub hub, Executor worker,
                                    AdAudioDiagnostics diagnostics) {
        this(hub, PcmAdAudioSignalProvider::createMatcher,
                DEFAULT_QUEUE_CAPACITY, worker, diagnostics);
    }

    PcmAdAudioSignalProvider(PlaybackMediaSignalHub hub,
                             AdAudioConsumer.MatcherFactory matcherFactory,
                             int queueCapacity, Executor worker,
                             AdAudioDiagnostics diagnostics) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.matcherFactory = Objects.requireNonNull(matcherFactory, "matcherFactory");
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
        this.worker = Objects.requireNonNull(worker, "worker");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void start(SessionContext context, AdAudioRuleSnapshot rules,
                      Listener listener) {
        ProviderError error = null;
        synchronized (this) {
            if (closed) return;
            this.context = Objects.requireNonNull(context, "context");
            this.rules = Objects.requireNonNull(rules, "rules");
            this.listener = Objects.requireNonNull(listener, "listener");
            deactivateLocked();
            if (!enabled) {
                state = ProviderState.DISABLED;
                return;
            }
            error = activateLocked();
        }
        notifyError(error);
    }

    @Override
    public void onHostPosition(HostPosition position) {
        ProviderError error;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(position, "position");
            if (!matches(position.sessionId(), position.generation())) return;
            error = observeMatcherErrorsLocked(consumer);
        }
        notifyError(error);
    }

    @Override
    public void onTimelineReset(TimelineReset reset) {
        ResetOutcome outcome;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(reset, "reset");
            outcome = acceptTimelineResetLocked(reset.sessionId(), reset.generation(),
                    reset.reason(), reset.mediaAnchorMs(), consumer);
        }
        if (outcome == null) return;
        notifyTimelineReset(outcome.reset());
        notifyError(outcome.error());
    }

    @Override
    public void setEnabled(boolean enabled) {
        ProviderError error = null;
        synchronized (this) {
            if (closed || this.enabled == enabled) return;
            this.enabled = enabled;
            if (!enabled) {
                deactivateLocked();
                state = ProviderState.DISABLED;
            } else if (context == null || rules == null || listener == null) {
                state = ProviderState.IDLE;
            } else {
                error = activateLocked();
            }
        }
        notifyError(error);
    }

    @Override
    public synchronized ProviderState state() {
        return state;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        deactivateLocked();
        context = null;
        rules = null;
        listener = null;
        state = ProviderState.CLOSED;
    }

    private ProviderError activateLocked() {
        deactivateLocked();
        if (!enabled) {
            state = ProviderState.DISABLED;
            return null;
        }
        if (!rules.hasRules()) {
            state = ProviderState.IDLE;
            return null;
        }
        if (rules.hasError() || rules.version().isEmpty()) {
            state = ProviderState.DEGRADED;
            return error(ErrorCode.RULES_UNAVAILABLE, "PCM rules are unavailable");
        }
        PlaybackMediaSignalHub.Session session = hub.session();
        if (session.id() != context.sessionId()
                || session.generation() != context.generation()) {
            state = ProviderState.DEGRADED;
            return error(ErrorCode.START_FAILED, "PCM session is stale");
        }

        AdAudioConsumer nextConsumer = new AdAudioConsumer(
                matcherFactory,
                candidate -> onConsumerCandidate(candidate),
                queueCapacity,
                worker,
                diagnostics);
        nextConsumer.start(context.sessionId(), context.generation(), rules.ruleSet());
        observedMatcherErrors = nextConsumer.matcherErrorCount();
        if (observedMatcherErrors > 0L) {
            nextConsumer.close();
            state = ProviderState.DEGRADED;
            return error(ErrorCode.START_FAILED, "PCM matcher could not start");
        }

        PlaybackMediaSignalHub.Registration nextRegistration = null;
        PlaybackMediaSignalHub.CaptureLease nextCaptureLease = null;
        try {
            nextRegistration = hub.register(
                    "ad-audio-" + ID, worker, queueCapacity,
                    bridge(nextConsumer));
            nextCaptureLease = hub.requestCapture(
                    PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO);
        } catch (RuntimeException e) {
            if (nextCaptureLease != null) nextCaptureLease.close();
            if (nextRegistration != null) nextRegistration.close();
            nextConsumer.close();
            state = ProviderState.DEGRADED;
            return error(ErrorCode.START_FAILED, "PCM capture could not start");
        }
        consumer = nextConsumer;
        registration = nextRegistration;
        captureLease = nextCaptureLease;
        state = ProviderState.RUNNING;
        return null;
    }

    private PlaybackMediaSignalHub.Consumer bridge(AdAudioConsumer source) {
        return new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                source.onPcm(frame);
                reportNewMatcherErrors(source);
            }

            @Override
            public void onLifecycle(PlaybackMediaSignalHub.Lifecycle event) {
                ResetOutcome outcome;
                synchronized (PcmAdAudioSignalProvider.this) {
                    outcome = acceptTimelineResetLocked(event.sessionId(), event.generation(),
                            mapReason(event.reason()), event.mediaAnchorMs(), source);
                }
                if (outcome == null) return;
                notifyTimelineReset(outcome.reset());
                notifyError(outcome.error());
            }

            @Override
            public void onFailure(RuntimeException error) {
                source.onFailure(error);
                reportNewMatcherErrors(source);
            }
        };
    }

    private ResetOutcome acceptTimelineResetLocked(long sessionId, long generation,
                                                   ResetReason reason, long mediaAnchorMs,
                                                   AdAudioConsumer source) {
        if (source == null || source != consumer || context == null) return null;
        if (sessionId == context.sessionId() && generation <= context.generation()) return null;
        context = new SessionContext(sessionId, generation,
                context.mediaId(), context.mediaUrl(), context.headers());
        source.onLifecycle(new PlaybackMediaSignalHub.Lifecycle(
                sessionId, generation, mapReason(reason), mediaAnchorMs));
        ProviderError matcherError = observeMatcherErrorsLocked(source);
        if (reason == ResetReason.RELEASE) {
            deactivateLocked();
            state = enabled ? ProviderState.IDLE : ProviderState.DISABLED;
        }
        return new ResetOutcome(
                new TimelineReset(sessionId, generation, reason, mediaAnchorMs),
                matcherError);
    }

    private void onConsumerCandidate(AdAudioConsumer.Candidate candidate) {
        Listener currentListener;
        AdAudioCandidate mapped;
        ProviderError mappingError = null;
        synchronized (this) {
            if (closed || state == ProviderState.DISABLED || consumer == null
                    || rules == null || !matches(candidate.sessionId(), candidate.generation())) {
                return;
            }
            AudioFingerprintMatcher.MatchEvent event = candidate.event();
            try {
                mapped = new AdAudioCandidate(
                        candidate.sessionId(), candidate.generation(),
                        event.ruleId(), rules.version(), event.startTimeMs(), event.endTimeMs(),
                        event.type() == AudioFingerprintMatcher.Type.FULL_MATCHED,
                        event.confidence(), ID);
                state = ProviderState.RUNNING;
            } catch (RuntimeException e) {
                mapped = null;
                state = ProviderState.DEGRADED;
                mappingError = error(ErrorCode.ANALYSIS_FAILED,
                        "PCM matcher returned an invalid candidate");
            }
            currentListener = listener;
        }
        if (mapped != null) notifyCandidate(currentListener, mapped);
        notifyError(mappingError);
    }

    private void reportNewMatcherErrors(AdAudioConsumer source) {
        ProviderError error;
        synchronized (this) {
            error = observeMatcherErrorsLocked(source);
        }
        notifyError(error);
    }

    private ProviderError observeMatcherErrorsLocked(AdAudioConsumer source) {
        if (source == null || source != consumer) return null;
        long current = source.matcherErrorCount();
        if (current <= observedMatcherErrors) return null;
        observedMatcherErrors = current;
        state = ProviderState.DEGRADED;
        return error(ErrorCode.ANALYSIS_FAILED, "PCM matcher failed");
    }

    private boolean matches(long sessionId, long generation) {
        return context != null && context.sessionId() == sessionId
                && context.generation() == generation;
    }

    private ProviderError error(ErrorCode code, String detail) {
        return new ProviderError(ID, code, detail);
    }

    private void notifyError(ProviderError error) {
        if (error == null) return;
        Listener current;
        synchronized (this) {
            if (closed) return;
            current = listener;
        }
        if (current == null) return;
        try {
            current.onProviderError(error);
        } catch (RuntimeException ignored) {
        }
    }

    private void notifyTimelineReset(TimelineReset reset) {
        if (reset == null) return;
        Listener current;
        synchronized (this) {
            if (closed) return;
            current = listener;
        }
        if (current == null) return;
        try {
            current.onTimelineReset(reset);
        } catch (RuntimeException ignored) {
        }
    }

    private static void notifyCandidate(Listener listener, AdAudioCandidate candidate) {
        if (listener == null) return;
        try {
            listener.onCandidate(candidate);
        } catch (RuntimeException ignored) {
        }
    }

    private void deactivateLocked() {
        if (captureLease != null) {
            captureLease.close();
            captureLease = null;
        }
        if (registration != null) {
            registration.close();
            registration = null;
        }
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
        observedMatcherErrors = 0L;
    }

    private static AdAudioConsumer.Matcher createMatcher(AudioFingerprintRuleSet ruleSet) {
        AudioFingerprintMatcher matcher = new AudioFingerprintMatcher(
                ruleSet, AudioFingerprintMatcher.Config.conservative());
        return new AdAudioConsumer.Matcher() {
            @Override
            public List<AudioFingerprintMatcher.MatchEvent> feed(
                    short[] samples, int sampleRate, long captureStartMs) {
                return matcher.feed(new AudioFingerprintMatcher.PcmChunk(
                        samples, sampleRate, 1, captureStartMs)).events();
            }

            @Override
            public List<AudioFingerprintMatcher.MatchEvent> finish() {
                return matcher.finish().events();
            }
        };
    }

    private static ResetReason mapReason(PlaybackMediaSignalHub.ResetReason reason) {
        return ResetReason.valueOf(reason.name());
    }

    private static PlaybackMediaSignalHub.ResetReason mapReason(ResetReason reason) {
        return PlaybackMediaSignalHub.ResetReason.valueOf(reason.name());
    }

    private record ResetOutcome(TimelineReset reset, ProviderError error) {
    }
}
