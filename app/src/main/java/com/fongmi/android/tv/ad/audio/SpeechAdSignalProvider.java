package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaAudioProcessor;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class SpeechAdSignalProvider implements AdAudioSignalProvider {

    public static final String ID = "speech";
    public static final String RULE_ID = "speech-keyword";
    static final long MATCH_COOLDOWN_MS = 30_000L;

    private static final String HUB_CONSUMER_ID = "speech-ad";
    private static final int TARGET_SAMPLE_RATE = 16_000;
    // Matches RealtimeSubtitleController.AUDIO_QUEUE_CAPACITY. Eight slots overflowed within
    // a second on a 48 kHz stream, where every frame needs downsampling before it can be fed
    // to the recognizer.
    private static final int DEFAULT_MAILBOX_CAPACITY = 16;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    public interface ConfigSource {
        SpeechAdConfig snapshot();
    }

    private enum ModelStatus {
        UNKNOWN,
        READY,
        UNAVAILABLE,
        FAILED
    }

    private final PlaybackMediaSignalHub hub;
    private final SpeechRecognitionFactory recognizerFactory;
    private final ConfigSource configSource;
    private final int mailboxCapacity;
    private final Executor worker;
    private final AdAudioDiagnostics diagnostics;
    private final ArrayDeque<PcmEnvelope> mailbox = new ArrayDeque<>();

    private ProviderState state = ProviderState.DISABLED;
    private SessionContext context;
    private String ruleVersion = "";
    private Listener listener;
    private SpeechAdConfig config;
    private HostPosition hostPosition;
    private SpeechRecognitionFactory.Session recognitionSession;
    private PlaybackMediaSignalHub.Registration registration;
    private PlaybackMediaSignalHub.CaptureLease captureLease;
    private ModelStatus modelStatus = ModelStatus.UNKNOWN;
    private long instanceToken;
    private int timelineToken;
    private long lastMatchPositionMs = Long.MIN_VALUE;
    private long pcmFrameCount;
    private long droppedPcmCount;
    private long fedFrameCount;
    private long recognitionCount;
    private boolean drainScheduled;
    private boolean enabled;
    private boolean closed;

    public SpeechAdSignalProvider(PlaybackMediaSignalHub hub,
                                  SpeechRecognitionFactory recognizerFactory,
                                  ConfigSource configSource,
                                  Executor worker,
                                  AdAudioDiagnostics diagnostics) {
        this(hub, recognizerFactory, configSource, DEFAULT_MAILBOX_CAPACITY,
                worker, diagnostics);
    }

    SpeechAdSignalProvider(PlaybackMediaSignalHub hub,
                           SpeechRecognitionFactory recognizerFactory,
                           ConfigSource configSource,
                           int mailboxCapacity,
                           Executor worker,
                           AdAudioDiagnostics diagnostics) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.recognizerFactory = Objects.requireNonNull(recognizerFactory,
                "recognizerFactory");
        this.configSource = Objects.requireNonNull(configSource, "configSource");
        if (mailboxCapacity <= 0) {
            throw new IllegalArgumentException("mailboxCapacity must be positive");
        }
        this.mailboxCapacity = mailboxCapacity;
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
        ProviderError error;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(listener, "listener");
            deactivateResourcesLocked(false, false);
            this.context = context;
            this.ruleVersion = rules.version();
            this.listener = listener;
            this.hostPosition = null;
            this.lastMatchPositionMs = Long.MIN_VALUE;
            this.timelineToken = nextTimelineToken(this.timelineToken);
            this.modelStatus = ModelStatus.UNKNOWN;
            error = loadConfigAndReconcileLocked();
        }
        notifyError(error);
    }

    @Override
    public void onHostPosition(HostPosition position) {
        ProviderError error = null;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(position, "position");
            if (!matchesContext(position.sessionId(), position.generation())) {
                diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
                return;
            }
            hostPosition = position;
            error = reconcileLocked();
        }
        notifyError(error);
    }

    @Override
    public void onTimelineReset(TimelineReset reset) {
        Listener currentListener;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(reset, "reset");
            if (!acceptsResetLocked(reset.sessionId(), reset.generation())) return;
            currentListener = listener;
            applyTimelineResetLocked(reset);
        }
        notifyTimelineReset(currentListener, reset);
    }

    @Override
    public void setEnabled(boolean enabled) {
        ProviderError error = null;
        synchronized (this) {
            if (closed || this.enabled == enabled) return;
            this.enabled = enabled;
            if (!enabled) {
                deactivateResourcesLocked(false, false);
                state = ProviderState.DISABLED;
            } else if (context == null || config == null || listener == null) {
                state = ProviderState.IDLE;
            } else {
                error = reconcileLocked();
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
        instanceToken = nextInstanceToken(instanceToken);
        closeRegistrationLocked();
        closeCaptureLeaseLocked();
        mailbox.clear();
        drainScheduled = false;
        closeRecognitionSessionLocked(false);
        context = null;
        config = null;
        hostPosition = null;
        listener = null;
        ruleVersion = "";
        state = ProviderState.CLOSED;
    }

    private ProviderError loadConfigAndReconcileLocked() {
        try {
            config = Objects.requireNonNull(configSource.snapshot(),
                    "speech config");
        } catch (RuntimeException error) {
            config = null;
            state = ProviderState.DEGRADED;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
            return providerError(ErrorCode.START_FAILED,
                    "Speech configuration is unavailable");
        }
        return reconcileLocked();
    }

    private ProviderError reconcileLocked() {
        if (!enabled || config == null || !config.enabled()) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.DISABLED;
            return null;
        }
        if (config.keywords().isEmpty()) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.IDLE;
            return null;
        }
        ProviderError modelError = ensureModelReadyLocked();
        if (modelStatus != ModelStatus.READY) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.DEGRADED;
            return modelError;
        }
        if (context == null || listener == null || ruleVersion.isEmpty()) {
            deactivateResourcesLocked(false, false);
            state = ruleVersion.isEmpty() && context != null
                    ? ProviderState.DEGRADED : ProviderState.IDLE;
            return ruleVersion.isEmpty() && context != null
                    ? providerError(ErrorCode.RULES_UNAVAILABLE,
                    "Speech routing version is unavailable") : null;
        }
        if (!isEligible(hostPosition)) {
            // Park without releasing resources. Buffering makes the position temporarily
            // ineligible many times per session; tearing the recognizer and the capture
            // lease down here would rebuild sherpa each time, lose the in-flight
            // utterance, and drop the PCM tap the audio pipeline was rebuilt to attach.
            state = ProviderState.IDLE;
            return null;
        }
        if (recognitionSession != null && registration != null
                && captureLease != null) {
            state = ProviderState.RUNNING;
            return null;
        }
        return activateLocked();
    }

    private ProviderError ensureModelReadyLocked() {
        if (modelStatus == ModelStatus.READY) return null;
        if (modelStatus == ModelStatus.UNAVAILABLE) return null;
        if (modelStatus == ModelStatus.FAILED) return null;
        try {
            if (recognizerFactory.isReady()) {
                modelStatus = ModelStatus.READY;
                return null;
            }
            modelStatus = ModelStatus.UNAVAILABLE;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_MODEL_UNAVAILABLE);
            return providerError(ErrorCode.START_FAILED,
                    "Speech model is unavailable");
        } catch (RuntimeException error) {
            modelStatus = ModelStatus.FAILED;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
            return providerError(ErrorCode.START_FAILED,
                    "Speech model check failed");
        }
    }

    private ProviderError activateLocked() {
        deactivateResourcesLocked(false, false);
        PlaybackMediaSignalHub.Session hubSession = hub.session();
        if (context == null || hubSession.id() != context.sessionId()
                || hubSession.generation() != context.generation()) {
            state = ProviderState.DEGRADED;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
            return providerError(ErrorCode.START_FAILED,
                    "Speech session is stale");
        }

        long token = nextInstanceToken(instanceToken);
        instanceToken = token;
        long sessionId = context.sessionId();
        long generation = context.generation();
        SpeechRecognitionFactory.Session nextSession = null;
        PlaybackMediaSignalHub.Registration nextRegistration = null;
        PlaybackMediaSignalHub.CaptureLease nextCaptureLease = null;
        try {
            nextSession = recognizerFactory.create(
                    recognitionListener(token));
            nextRegistration = hub.register(
                    HUB_CONSUMER_ID, DIRECT_EXECUTOR, 1,
                    hubConsumer(token));
            nextCaptureLease = hub.requestCapture(
                    PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO);
        } catch (RuntimeException error) {
            safeClose(nextRegistration);
            safeClose(nextCaptureLease);
            safeClose(nextSession);
            state = ProviderState.DEGRADED;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
            return providerError(ErrorCode.START_FAILED,
                    "Speech recognizer could not start");
        }
        recognitionSession = nextSession;
        registration = nextRegistration;
        captureLease = nextCaptureLease;
        state = ProviderState.RUNNING;
        return null;
    }

    private SpeechRecognitionFactory.Listener recognitionListener(long token) {
        return new SpeechRecognitionFactory.Listener() {
            @Override
            public void onResult(String text, long startUs, long endUs,
                                 int callbackTimelineToken) {
                onRecognitionResult(token, text, startUs, callbackTimelineToken);
            }

            @Override
            public void onError(Throwable error) {
                onRecognitionError(token);
            }
        };
    }

    private PlaybackMediaSignalHub.Consumer hubConsumer(long token) {
        return new PlaybackMediaSignalHub.Consumer() {
            @Override
            public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                enqueuePcm(token, frame);
            }

            @Override
            public void onLifecycle(PlaybackMediaSignalHub.Lifecycle event) {
                onHubLifecycle(token, event);
            }

            @Override
            public void onFailure(RuntimeException error) {
                synchronized (SpeechAdSignalProvider.this) {
                    if (closed || token != instanceToken) return;
                    diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }
        };
    }

    private void enqueuePcm(long token, PlaybackMediaSignalHub.PcmFrame frame) {
        synchronized (this) {
            if (closed || token != instanceToken) return;
            if (state != ProviderState.RUNNING) {
                // Frames are dropped whenever the provider is parked. Count them, otherwise
                // "audio arrives but nothing is recognized" looks identical to "no audio".
                if (droppedPcmCount++ % 50L == 0L) {
                    AdAudioDiagnostics.log("speech pcm dropped state=%s n=%d",
                            state, droppedPcmCount);
                }
                return;
            }
            if (!matchesContext(frame.sessionId(), frame.generation())) {
                diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
                return;
            }
            float[] samples;
            try {
                samples = Arrays.copyOf(frame.monoSamples(), frame.monoSamples().length);
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                return;
            }
            if (mailbox.size() >= mailboxCapacity) {
                mailbox.removeFirst();
                diagnostics.record(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
            }
            if (pcmFrameCount++ % 200L == 0L) {
                AdAudioDiagnostics.log("speech pcm n=%d rate=%d captureMs=%d",
                        pcmFrameCount, frame.sampleRate(), frame.captureStartTimeMs());
            }
            mailbox.addLast(new PcmEnvelope(
                    token, frame.sessionId(), frame.generation(), timelineToken,
                    samples, frame.sampleRate(), frame.captureStartTimeMs()));
            scheduleDrainLocked(token, timelineToken);
        }
    }

    private void scheduleDrainLocked(long token, int scheduledTimelineToken) {
        if (drainScheduled) return;
        drainScheduled = true;
        try {
            worker.execute(() -> drainMailbox(token, scheduledTimelineToken));
        } catch (RuntimeException error) {
            drainScheduled = false;
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void drainMailbox(long token, int scheduledTimelineToken) {
        while (true) {
            PcmEnvelope envelope;
            synchronized (this) {
                if (closed || token != instanceToken
                        || scheduledTimelineToken != timelineToken) return;
                envelope = mailbox.pollFirst();
                if (envelope == null) {
                    drainScheduled = false;
                    return;
                }
                if (!isCurrentEnvelopeLocked(envelope)) {
                    diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
                    continue;
                }
            }

            float[] samples;
            try {
                samples = PlaybackMediaAudioProcessor.resample(
                        envelope.samples(), envelope.sampleRate(), TARGET_SAMPLE_RATE);
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                continue;
            }
            if (samples.length == 0) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                continue;
            }
            long startUs = millisecondsToMicroseconds(envelope.captureStartTimeMs());
            long durationUs = ((long) samples.length * 1_000_000L
                    + TARGET_SAMPLE_RATE - 1L) / TARGET_SAMPLE_RATE;
            long endUs = saturatedAdd(startUs, durationUs);

            synchronized (this) {
                if (!isCurrentEnvelopeLocked(envelope)
                        || recognitionSession == null) {
                    diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
                    continue;
                }
                try {
                    recognitionSession.accept(
                            samples, startUs, endUs, envelope.timelineToken());
                    if (fedFrameCount++ % 200L == 0L) {
                        AdAudioDiagnostics.log("speech fed n=%d samples=%d startMs=%d",
                                fedFrameCount, samples.length,
                                microsecondsToMilliseconds(startUs));
                    }
                } catch (RuntimeException error) {
                    diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }
        }
    }

    private void onRecognitionResult(long token, String text, long startUs,
                                     int callbackTimelineToken) {
        Listener currentListener;
        AdAudioCandidate candidate;
        synchronized (this) {
            if (!isCurrentCallbackLocked(token, callbackTimelineToken)) {
                diagnostics.record(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            String normalized = SpeechAdKeywordSet.normalize(text);
            if (normalized.isEmpty()) {
                diagnostics.record(AdAudioDiagnostics.Code.SPEECH_TEXT_EMPTY);
                return;
            }
            boolean matched;
            try {
                matched = config != null
                        && config.keywords().firstMatch(text).isPresent();
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                return;
            }
            // Log only the transcript LENGTH and the match verdict. The recognized text and
            // the matched keyword must never reach the log.
            AdAudioDiagnostics.log("speech text n=%d chars=%d matched=%b startMs=%d",
                    ++recognitionCount, normalized.length(),
                    matched, microsecondsToMilliseconds(startUs));
            if (!matched) return;
            HostPosition position = hostPosition;
            if (!isEligible(position)
                    || !matchesContext(position.sessionId(), position.generation())) {
                diagnostics.record(AdAudioDiagnostics.Code.CLOCK_UNAVAILABLE);
                return;
            }
            // Anchor on the capture timestamp the recognizer reported for this utterance.
            // hostPosition is only republished on bind/refresh/state change, so during
            // steady playback it is stale and would place the candidate at the position
            // playback had when it was last sampled instead of where the keyword was said.
            long startMs = microsecondsToMilliseconds(startUs);
            if (startMs < 0L) {
                diagnostics.record(AdAudioDiagnostics.Code.CLOCK_UNAVAILABLE);
                return;
            }
            if (lastMatchPositionMs != Long.MIN_VALUE
                    && startMs >= lastMatchPositionMs
                    && startMs - lastMatchPositionMs < MATCH_COOLDOWN_MS) {
                diagnostics.record(AdAudioDiagnostics.Code.SPEECH_COOLDOWN);
                return;
            }
            long endMs = saturatedAdd(startMs, (long) config.skipSeconds() * 1_000L);
            try {
                candidate = new AdAudioCandidate(
                        context.sessionId(), context.generation(), RULE_ID, ruleVersion,
                        startMs, endMs, true, 1.0d, ID);
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                return;
            }
            lastMatchPositionMs = startMs;
            diagnostics.record(AdAudioDiagnostics.Code.SPEECH_MATCHED);
            currentListener = listener;
        }
        notifyCandidate(currentListener, candidate);
    }

    private void onRecognitionError(long token) {
        Listener currentListener;
        synchronized (this) {
            if (closed || token != instanceToken || context == null) {
                diagnostics.record(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            currentListener = listener;
        }
        notifyError(currentListener, providerError(
                ErrorCode.ANALYSIS_FAILED, "Speech recognizer failed"));
    }

    private void onHubLifecycle(long token, PlaybackMediaSignalHub.Lifecycle event) {
        Listener currentListener;
        TimelineReset reset;
        synchronized (this) {
            if (closed || token != instanceToken) return;
            if (!acceptsResetLocked(event.sessionId(), event.generation())) return;
            reset = new TimelineReset(
                    event.sessionId(), event.generation(),
                    ResetReason.valueOf(event.reason().name()),
                    event.mediaAnchorMs());
            currentListener = listener;
            applyTimelineResetLocked(reset);
        }
        notifyTimelineReset(currentListener, reset);
    }

    private void applyTimelineResetLocked(TimelineReset reset) {
        context = new SessionContext(
                reset.sessionId(), reset.generation(),
                context.mediaId(), context.mediaUrl(), context.headers());
        hostPosition = null;
        lastMatchPositionMs = Long.MIN_VALUE;
        timelineToken = nextTimelineToken(timelineToken);
        if (!mailbox.isEmpty()) {
            diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
        }
        mailbox.clear();
        drainScheduled = false;
        if (recognitionSession != null) {
            try {
                recognitionSession.reset();
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            }
        }
        state = !enabled || config == null || !config.enabled()
                ? ProviderState.DISABLED
                : modelStatus == ModelStatus.READY
                ? ProviderState.IDLE : ProviderState.DEGRADED;
    }

    private boolean acceptsResetLocked(long sessionId, long generation) {
        if (context == null) return false;
        if (sessionId < context.sessionId()) return false;
        return sessionId != context.sessionId()
                || generation > context.generation();
    }

    private boolean isCurrentEnvelopeLocked(PcmEnvelope envelope) {
        return !closed && envelope.instanceToken() == instanceToken
                && envelope.timelineToken() == timelineToken
                && matchesContext(envelope.sessionId(), envelope.generation());
    }

    private boolean isCurrentCallbackLocked(long token,
                                            int callbackTimelineToken) {
        return !closed && state == ProviderState.RUNNING
                && token == instanceToken
                && callbackTimelineToken == timelineToken
                && context != null;
    }

    private boolean matchesContext(long sessionId, long generation) {
        return context != null && context.sessionId() == sessionId
                && context.generation() == generation;
    }

    private static boolean isEligible(HostPosition position) {
        return position != null && position.seekable() && !position.live()
                && position.durationMs() >= 0L
                && position.positionMs() < position.durationMs();
    }

    private void deactivateResourcesLocked(boolean resetSession,
                                           boolean staleQueuedPcm) {
        instanceToken = nextInstanceToken(instanceToken);
        closeRegistrationLocked();
        closeCaptureLeaseLocked();
        if (staleQueuedPcm && !mailbox.isEmpty()) {
            diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
        }
        mailbox.clear();
        drainScheduled = false;
        closeRecognitionSessionLocked(resetSession);
    }

    private void closeRegistrationLocked() {
        if (registration == null) return;
        try {
            registration.close();
        } catch (RuntimeException error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
        registration = null;
    }

    private void closeCaptureLeaseLocked() {
        if (captureLease == null) return;
        try {
            captureLease.close();
        } catch (RuntimeException error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
        captureLease = null;
    }

    private void closeRecognitionSessionLocked(boolean resetFirst) {
        if (recognitionSession == null) return;
        if (resetFirst) {
            try {
                recognitionSession.reset();
            } catch (RuntimeException error) {
                diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            }
        }
        try {
            recognitionSession.close();
        } catch (RuntimeException error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
        recognitionSession = null;
    }

    private ProviderError providerError(ErrorCode code, String detail) {
        return new ProviderError(ID, code, detail);
    }

    private void notifyCandidate(Listener target, AdAudioCandidate candidate) {
        if (target == null) return;
        try {
            target.onCandidate(candidate);
        } catch (RuntimeException error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void notifyError(ProviderError error) {
        Listener target;
        synchronized (this) {
            if (closed || error == null) return;
            target = listener;
        }
        notifyError(target, error);
    }

    private void notifyError(Listener target, ProviderError error) {
        if (target == null || error == null) return;
        try {
            target.onProviderError(error);
        } catch (RuntimeException listenerError) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void notifyTimelineReset(Listener target, TimelineReset reset) {
        if (target == null) return;
        try {
            target.onTimelineReset(reset);
        } catch (RuntimeException error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void safeClose(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception error) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private static long millisecondsToMicroseconds(long milliseconds) {
        if (milliseconds <= 0L) return 0L;
        if (milliseconds > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE;
        return milliseconds * 1_000L;
    }

    private static long microsecondsToMilliseconds(long microseconds) {
        return microseconds < 0L ? -1L : microseconds / 1_000L;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long nextInstanceToken(long current) {
        return current == Long.MAX_VALUE ? 0L : current + 1L;
    }

    private static int nextTimelineToken(int current) {
        return current == Integer.MAX_VALUE ? 0 : current + 1;
    }

    private record PcmEnvelope(long instanceToken, long sessionId, long generation,
                               int timelineToken, float[] samples, int sampleRate,
                               long captureStartTimeMs) {
    }
}