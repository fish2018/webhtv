package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaAudioProcessor;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private static final int RECOGNITION_RESULT_CAPACITY = 8;
    private static final int MAX_RESULT_CHARS = 8_192;
    private static final int MAX_PCM_SAMPLES = 192_000;
    // Hub delivery only validates/copies into the bounded mailbox and schedules owner work;
    // it must not run native recognition or matching on the audio publisher thread.
    private static final Executor HUB_EXECUTOR = Runnable::run;

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
    private final ArrayDeque<OwnerCommand> ownerCommands = new ArrayDeque<>();
    private final Object submissionLock = new Object();

    // The monitor protects only Java state/mailboxes. External code, Hub cleanup, file
    // logging, resampling, matching and native calls must run outside it.
    private ProviderState state = ProviderState.DISABLED;
    private SessionContext context;
    private String ruleVersion = "";
    private Listener listener;
    private SpeechAdConfig config;
    // SpeechAdMatcher is confined to the speech owner. The reference is swapped
    // under the short Java-state lock when a config is installed; matching and
    // timeline resets never run in that lock.
    private SpeechAdMatcher speechMatcher;
    private HostPosition hostPosition;
    private SpeechRecognitionFactory.Session recognitionSession;
    private PlaybackMediaSignalHub.Registration registration;
    private PlaybackMediaSignalHub.CaptureLease captureLease;
    private ModelStatus modelStatus = ModelStatus.UNKNOWN;
    private long instanceToken;
    private int timelineToken;
    private long lastMatchPositionMs = Long.MIN_VALUE;
    private boolean ownerTaskScheduled;
    private boolean acceptScheduled;
    private boolean modelCheckScheduled;
    private int pendingRecognitionResults;
    private boolean matcherResetScheduled;
    private long pendingActivationToken = -1L;
    private boolean enabled;
    private boolean closed;
    private boolean ownerRejected;

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
        dispatchOwner();
        notifyError(error);
    }

    @Override
    public void onHostPosition(HostPosition position) {
        ProviderError error = null;
        synchronized (this) {
            if (closed) return;
            Objects.requireNonNull(position, "position");
            if (!matchesContext(position.sessionId(), position.generation())) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.STALE_GENERATION);
                return;
            }
            hostPosition = position;
            error = reconcileLocked();
        }
        dispatchOwner();
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
        dispatchOwner();
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
        dispatchOwner();
        notifyError(error);
    }

    @Override
    public synchronized ProviderState state() {
        return state;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (!closed) {
                closed = true;
                instanceToken = nextInstanceToken(instanceToken);
                pendingActivationToken = -1L;
                discardObsoleteCommandsLocked();
                retireCaptureLocked();
                mailbox.clear();
                retireRecognitionSessionLocked(false);
                context = null;
                config = null;
                hostPosition = null;
                listener = null;
                ruleVersion = "";
                speechMatcher = null;
                state = ProviderState.CLOSED;
            }
        }
        // Even a repeated close must finish an in-progress executor handoff before its
        // caller shuts down the owner or creates a replacement provider.
        dispatchOwner();
    }

    private ProviderError loadConfigAndReconcileLocked() {
        try {
            config = Objects.requireNonNull(configSource.snapshot(),
                    "speech config");
            speechMatcher = config.rules().isEmpty()
                    ? null : new SpeechAdMatcher(config.rules());
        } catch (RuntimeException error) {
            config = null;
            speechMatcher = null;
            state = ProviderState.DEGRADED;
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
            return providerError(ErrorCode.START_FAILED,
                    "Speech configuration is unavailable");
        }
        return reconcileLocked();
    }

    private ProviderError reconcileLocked() {
        if (ownerRejected) {
            state = ProviderState.DEGRADED;
            return null;
        }
        if (!enabled || config == null || !config.enabled()) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.DISABLED;
            return null;
        }
        if (!config.hasSpeechRules()) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.IDLE;
            return null;
        }
        if (modelStatus == ModelStatus.UNKNOWN) {
            state = ProviderState.IDLE;
            if (!modelCheckScheduled) {
                modelCheckScheduled = true;
                enqueueOwnerCommandLocked(new CheckModelCommand(instanceToken), false);
            }
            return null;
        }
        if (modelStatus != ModelStatus.READY) {
            deactivateResourcesLocked(false, false);
            state = ProviderState.DEGRADED;
            return null;
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
        if (pendingActivationToken == instanceToken) {
            // Creation is deliberately owned by the speech worker. Do not enqueue a
            // second create while the first one is still pending; doing so would create
            // duplicate native sessions when refresh() races model startup.
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

    private ProviderError modelErrorLocked() {
        return modelStatus == ModelStatus.UNAVAILABLE
                ? providerError(ErrorCode.START_FAILED, "Speech model is unavailable")
                : providerError(ErrorCode.START_FAILED, "Speech model check failed");
    }

    private ProviderError activateLocked() {
        deactivateResourcesLocked(false, false);
        pendingActivationToken = instanceToken;
        state = ProviderState.IDLE;
        enqueueOwnerCommandLocked(new CreateSessionCommand(
                instanceToken, context.sessionId(), context.generation()), false);
        return null;
    }

    private SpeechRecognitionFactory.Listener recognitionListener(long token) {
        return new SpeechRecognitionFactory.Listener() {
            @Override
            public void onResult(String text, long startUs, long endUs,
                                 int callbackTimelineToken) {
                onRecognitionResult(token, text, startUs, endUs, callbackTimelineToken);
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
                    diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }
        };
    }

    private void enqueuePcm(long token, PlaybackMediaSignalHub.PcmFrame frame) {
        synchronized (this) {
            if (closed || token != instanceToken) return;
            if (!matchesContext(frame.sessionId(), frame.generation())) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.STALE_GENERATION);
                return;
            }
            if (state != ProviderState.RUNNING) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_PCM_DROPPED);
                // Buffering without input is harmless. Dropped PCM is not: invalidate the
                // old segment as soon as actual input is discarded, but retain the model.
                if (recognitionSession != null && !hasResetCommandLocked()) {
                    invalidatePcmGapLocked();
                }
            } else if (frame.sampleRate() < 8_000 || frame.sampleRate() > 192_000
                    || frame.captureStartTimeMs() < 0L || frame.monoSamples().length == 0
                    || frame.monoSamples().length > MAX_PCM_SAMPLES) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_PCM_DROPPED);
                invalidatePcmGapLocked();
            } else {
                if (mailbox.size() >= mailboxCapacity) {
                    mailbox.removeFirst();
                    diagnostics.recordQuietly(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
                    invalidatePcmGapLocked();
                }
                mailbox.addLast(new PcmEnvelope(
                        token, frame.sessionId(), frame.generation(), timelineToken,
                        Arrays.copyOf(frame.monoSamples(), frame.monoSamples().length),
                        frame.sampleRate(), frame.captureStartTimeMs()));
                diagnostics.recordSpeechQueueDepth(mailbox.size());
                if (!acceptScheduled) {
                    acceptScheduled = true;
                    enqueueOwnerCommandLocked(new AcceptNextPcmCommand(), false);
                }
            }
        }
        dispatchOwner();
    }

    private boolean hasResetCommandLocked() {
        return ownerCommands.stream().anyMatch(command -> command instanceof ResetSessionCommand);
    }

    private void invalidatePcmGapLocked() {
        timelineToken = nextTimelineToken(timelineToken);
        // Retain the newest bounded PCM tail, all on the fresh post-gap timeline. A result
        // from an in-flight accept has the old token and cannot undo this invalidation.
        int size = mailbox.size();
        for (int i = 0; i < size; i++) {
            PcmEnvelope frame = mailbox.removeFirst();
            mailbox.addLast(new PcmEnvelope(frame.instanceToken(), frame.sessionId(),
                    frame.generation(), timelineToken, frame.samples(), frame.sampleRate(),
                    frame.captureStartTimeMs()));
        }
        ownerCommands.removeIf(command -> command instanceof ResetSessionCommand
                || command instanceof RecognitionResultCommand || command instanceof RecognitionErrorCommand);
        pendingRecognitionResults = 0;
        matcherResetScheduled = false;
        if (recognitionSession != null) {
            enqueueOwnerCommandLocked(new ResetSessionCommand(instanceToken, timelineToken), true);
        }
    }

    private void scheduleMatcherResetLocked() {
        if (speechMatcher == null || matcherResetScheduled) return;
        matcherResetScheduled = true;
        enqueueOwnerCommandLocked(new ResetMatcherCommand(instanceToken, timelineToken), true);
    }

    private void dispatchOwner() {
        // Serialize the handoff, not native work. Without this separate lock, close could
        // see scheduled=true before execute accepted the wakeup and shut down the executor.
        // The production executor enqueues asynchronously; never hold the state monitor
        // across execute (injected test executors may run inline).
        synchronized (submissionLock) {
            synchronized (this) {
                if (ownerRejected || ownerTaskScheduled || ownerCommands.isEmpty()) return;
                ownerTaskScheduled = true;
            }
            try {
                worker.execute(this::runOwnerCommand);
            } catch (RuntimeException error) {
                synchronized (this) {
                    ownerTaskScheduled = false;
                    ownerRejected = true;
                    instanceToken = nextInstanceToken(instanceToken);
                    mailbox.clear();
                    if (!closed) state = ProviderState.DEGRADED;
                    // Preserve cleanup commands/references; rejection must not masquerade as
                    // a successful native close or fall back to freeing on the caller thread.
                }
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_OWNER_REJECTED);
            }
        }
    }

    private void runOwnerCommand() {
        // One merged wakeup owns this provider until idle. Check lifecycle priority between
        // each bounded PCM/result command, including after a slow native call. No recursive
        // resubmission after Runtime.shutdown(), and no later provider can overtake close.
        while (true) {
            OwnerCommand command;
            synchronized (this) {
                command = ownerCommands.pollFirst();
                if (command == null) {
                    ownerTaskScheduled = false;
                    return;
                }
                if (command instanceof RecognitionResultCommand
                        || command instanceof RecognitionErrorCommand) pendingRecognitionResults--;
            }
            try {
                command.run();
            } catch (RuntimeException error) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            }
        }
    }

    private void enqueueOwnerCommandLocked(OwnerCommand command, boolean priority) {
        if (priority) ownerCommands.addFirst(command);
        else ownerCommands.addLast(command);
    }

    private void acceptNextPcm() {
        PcmEnvelope envelope;
        SpeechRecognitionFactory.Session session;
        synchronized (this) {
            if (closed || state != ProviderState.RUNNING
                    || recognitionSession == null) {
                mailbox.clear();
                acceptScheduled = false;
                return;
            }
            envelope = mailbox.pollFirst();
            if (envelope == null) {
                acceptScheduled = false;
                return;
            }
            if (!isCurrentEnvelopeLocked(envelope)) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.STALE_GENERATION);
                enqueueOwnerCommandLocked(new AcceptNextPcmCommand(), false);
                return;
            }
            session = recognitionSession;
        }

        float[] samples;
        try {
            samples = PlaybackMediaAudioProcessor.resample(
                    envelope.samples(), envelope.sampleRate(), TARGET_SAMPLE_RATE);
        } catch (RuntimeException error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            finishAcceptCommand();
            return;
        }
        if (samples.length == 0) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            finishAcceptCommand();
            return;
        }
        long startUs = millisecondsToMicroseconds(envelope.captureStartTimeMs());
        long durationUs = ((long) samples.length * 1_000_000L
                + TARGET_SAMPLE_RATE - 1L) / TARGET_SAMPLE_RATE;
        long endUs = saturatedAdd(startUs, durationUs);
        synchronized (this) {
            if (!isCurrentEnvelopeLocked(envelope) || session != recognitionSession
                    || state != ProviderState.RUNNING) {
                finishAcceptCommand();
                return;
            }
        }
        long beginNanos = System.nanoTime();
        try {
            session.accept(samples, startUs, endUs, envelope.timelineToken());
        } catch (RuntimeException error) {
            synchronized (this) {
                if (isCurrentEnvelopeLocked(envelope)) invalidatePcmGapLocked();
            }
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
        } finally {
            diagnostics.recordSpeechAcceptNanos(System.nanoTime() - beginNanos);
        }
        finishAcceptCommand();
    }

    private void finishAcceptCommand() {
        synchronized (this) {
            if (closed || state != ProviderState.RUNNING
                    || recognitionSession == null || mailbox.isEmpty()) {
                acceptScheduled = false;
                return;
            }
            if (ownerCommands.stream().noneMatch(command -> command instanceof AcceptNextPcmCommand)) {
                enqueueOwnerCommandLocked(new AcceptNextPcmCommand(), false);
            }
        }
    }

    private interface OwnerCommand {
        void run();
    }

    private final class CheckModelCommand implements OwnerCommand {
        private final long token;

        private CheckModelCommand(long token) {
            this.token = token;
        }

        @Override
        public void run() {
            synchronized (SpeechAdSignalProvider.this) {
                if (closed || token != instanceToken) return;
            }
            ModelStatus result;
            try {
                result = recognizerFactory.isReady()
                        ? ModelStatus.READY : ModelStatus.UNAVAILABLE;
            } catch (RuntimeException error) {
                result = ModelStatus.FAILED;
            }
            ProviderError error = null;
            Listener errorListener;
            synchronized (SpeechAdSignalProvider.this) {
                if (closed || token != instanceToken) return;
                modelCheckScheduled = false;
                modelStatus = result;
                if (result == ModelStatus.UNAVAILABLE) {
                    diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_MODEL_UNAVAILABLE);
                } else if (result == ModelStatus.FAILED) {
                    diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
                }
                error = reconcileLocked();
                if (result != ModelStatus.READY) error = modelErrorLocked();
                errorListener = listener;
            }
            dispatchOwner();
            notifyError(errorListener, error);
        }
    }

    private final class CreateSessionCommand implements OwnerCommand {
        private final long token;
        private final long sessionId;
        private final long generation;

        private CreateSessionCommand(long token, long sessionId, long generation) {
            this.token = token;
            this.sessionId = sessionId;
            this.generation = generation;
        }

        @Override
        public void run() {
            synchronized (SpeechAdSignalProvider.this) {
                if (closed || token != instanceToken || pendingActivationToken != token) return;
            }
            SpeechRecognitionFactory.Session created = null;
            PlaybackMediaSignalHub.Registration nextRegistration = null;
            PlaybackMediaSignalHub.CaptureLease nextLease = null;
            boolean installedCapture = false;
            ProviderError error = null;
            Listener errorListener = null;
            try {
                PlaybackMediaSignalHub.Session current = hub.session();
                if (current.id() != sessionId || current.generation() != generation) {
                    throw new IllegalStateException("stale speech activation");
                }
                nextRegistration = hub.register(HUB_CONSUMER_ID, HUB_EXECUTOR, 1, hubConsumer(token));
                nextLease = hub.requestCapture(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO);
                synchronized (SpeechAdSignalProvider.this) {
                    if (!closed && token == instanceToken) {
                        registration = nextRegistration;
                        captureLease = nextLease;
                        installedCapture = true;
                    }
                }
                if (!installedCapture) return;
                created = Objects.requireNonNull(recognizerFactory.create(
                        recognitionListener(token), SpeechRecognitionFactory.ExecutionProfile.AD_AUDIO),
                        "speech recognition session");
            } catch (RuntimeException failure) {
                error = providerError(ErrorCode.START_FAILED, "Speech recognizer could not start");
            } finally {
                if (!installedCapture) {
                    safeClose(nextRegistration);
                    safeClose(nextLease);
                }
            }
            boolean install;
            synchronized (SpeechAdSignalProvider.this) {
                // A seek during a slow create is safe: this new session has consumed no PCM.
                // A changed media session or disabled provider must never install it.
                install = error == null && !closed && token == instanceToken
                        && pendingActivationToken == token && context != null
                        && context.sessionId() == sessionId && enabled
                        && config != null && config.enabled();
                if (install) {
                    recognitionSession = created;
                    pendingActivationToken = -1L;
                    state = isEligible(hostPosition) ? ProviderState.RUNNING : ProviderState.IDLE;
                } else if (!closed && token == instanceToken) {
                    errorListener = listener;
                    retireCaptureLocked();
                    pendingActivationToken = -1L;
                    modelStatus = error == null ? ModelStatus.READY : ModelStatus.FAILED;
                    state = error == null ? ProviderState.IDLE : ProviderState.DEGRADED;
                    if (error != null) diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_START_FAILED);
                }
            }
            if (!install) safeClose(created);
            notifyError(errorListener, error);
        }
    }

    private final class AcceptNextPcmCommand implements OwnerCommand {
        @Override
        public void run() {
            acceptNextPcm();
        }
    }

    private final class RecognitionResultCommand implements OwnerCommand {
        private final long token;
        private final String text;
        private final long startUs;
        private final long endUs;
        private final int timeline;

        private RecognitionResultCommand(long token, String text, long startUs,
                                         long endUs, int timeline) {
            this.token = token;
            this.text = text;
            this.startUs = startUs;
            this.endUs = endUs;
            this.timeline = timeline;
        }

        @Override
        public void run() {
            processRecognitionResult(token, text, startUs, endUs, timeline);
        }
    }

    private final class RecognitionErrorCommand implements OwnerCommand {
        private final long token;

        private RecognitionErrorCommand(long token) {
            this.token = token;
        }

        @Override
        public void run() {
            processRecognitionError(token);
        }
    }

    private final class ResetSessionCommand implements OwnerCommand {
        private final long token;
        private final int timeline;

        private ResetSessionCommand(long token, int timeline) {
            this.token = token;
            this.timeline = timeline;
        }

        @Override
        public void run() {
            SpeechRecognitionFactory.Session session;
            SpeechAdMatcher matcher;
            synchronized (SpeechAdSignalProvider.this) {
                if (closed || token != instanceToken || timeline != timelineToken) return;
                session = recognitionSession;
                matcher = speechMatcher;
            }
            if (matcher != null) matcher.reset(timeline);
            if (session == null) return;
            try {
                session.reset();
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_RESET);
            } catch (RuntimeException error) {
                synchronized (SpeechAdSignalProvider.this) {
                    if (token == instanceToken) {
                        deactivateResourcesLocked(false, false);
                        modelStatus = ModelStatus.FAILED;
                        state = ProviderState.DEGRADED;
                    }
                }
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            }
        }
    }

    private final class ResetMatcherCommand implements OwnerCommand {
        private final long token;
        private final int timeline;

        private ResetMatcherCommand(long token, int timeline) {
            this.token = token;
            this.timeline = timeline;
        }

        @Override
        public void run() {
            SpeechAdMatcher matcher;
            synchronized (SpeechAdSignalProvider.this) {
                if (closed || token != instanceToken || timeline != timelineToken) return;
                ownerCommands.removeIf(command -> command instanceof RecognitionResultCommand
                        || command instanceof RecognitionErrorCommand);
                pendingRecognitionResults = 0;
                matcherResetScheduled = false;
                matcher = speechMatcher;
            }
            if (matcher != null) matcher.reset(timeline);
        }
    }

    private final class CloseCaptureCommand implements OwnerCommand {
        private final PlaybackMediaSignalHub.Registration retiredRegistration;
        private final PlaybackMediaSignalHub.CaptureLease retiredLease;

        private CloseCaptureCommand(PlaybackMediaSignalHub.Registration registration,
                                    PlaybackMediaSignalHub.CaptureLease lease) {
            retiredRegistration = registration;
            retiredLease = lease;
        }

        @Override
        public void run() {
            safeClose(retiredRegistration);
            safeClose(retiredLease);
        }
    }

    private final class CloseSessionCommand implements OwnerCommand {
        private final SpeechRecognitionFactory.Session session;
        private final boolean resetFirst;

        private CloseSessionCommand(SpeechRecognitionFactory.Session session,
                                    boolean resetFirst) {
            this.session = session;
            this.resetFirst = resetFirst;
        }

        @Override
        public void run() {
            if (resetFirst) {
                try {
                    session.reset();
                } catch (RuntimeException error) {
                    diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }
            try {
                session.close();
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_CLOSED);
            } catch (RuntimeException error) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            }
        }
    }

    private void onRecognitionResult(long token, String text, long startUs,
                                     long endUs, int callbackTimelineToken) {
        synchronized (this) {
            if (!isCurrentCallbackLocked(token, callbackTimelineToken)) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            if (pendingRecognitionResults >= RECOGNITION_RESULT_CAPACITY) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
                scheduleMatcherResetLocked();
                return;
            }
            if (text != null && text.length() > MAX_RESULT_CHARS) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_PCM_DROPPED);
                scheduleMatcherResetLocked();
                return;
            }
            pendingRecognitionResults++;
            enqueueOwnerCommandLocked(new RecognitionResultCommand(
                    token, text, startUs, endUs, callbackTimelineToken), false);
        }
        dispatchOwner();
    }

    private void onRecognitionError(long token) {
        synchronized (this) {
            if (closed || token != instanceToken || context == null) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            if (pendingRecognitionResults >= RECOGNITION_RESULT_CAPACITY) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
                return;
            }
            pendingRecognitionResults++;
            enqueueOwnerCommandLocked(new RecognitionErrorCommand(token), false);
        }
        dispatchOwner();
    }

    private void processRecognitionResult(long token, String text, long startUs,
                                          long endUs, int callbackTimelineToken) {
        SpeechAdConfig currentConfig;
        SpeechAdMatcher currentMatcher;
        SessionContext currentContext;
        String currentRuleVersion;
        Listener currentListener;
        synchronized (this) {
            if (!isCurrentCallbackLocked(token, callbackTimelineToken)) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            currentConfig = config;
            currentMatcher = speechMatcher;
            currentContext = context;
            currentRuleVersion = ruleVersion;
            currentListener = listener;
        }
        if (text == null || SpeechAdKeywordSet.normalize(text).isEmpty()) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_TEXT_EMPTY);
        }

        List<AdAudioCandidate> candidates = new ArrayList<>();
        if (currentMatcher != null) {
            for (SpeechAdMatcher.Match match : currentMatcher.accept(
                    text, startUs, endUs, callbackTimelineToken)) {
                AdAudioCandidate candidate = compoundCandidate(
                        currentContext, currentRuleVersion, match);
                if (candidate != null) candidates.add(candidate);
            }
        }
        if (currentConfig != null && !currentConfig.keywords().isEmpty()
                && currentConfig.keywords().firstMatch(text).isPresent()) {
            long startMs = microsecondsToMilliseconds(startUs);
            long endMs = saturatedAdd(startMs,
                    (long) currentConfig.skipSeconds() * 1_000L);
            if (startMs >= 0L && endMs > startMs) {
                AdAudioCandidate candidate = keywordCandidate(
                        currentContext, currentRuleVersion, startMs, endMs);
                if (candidate != null) candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return;

        synchronized (this) {
            if (!isCurrentCallbackLocked(token, callbackTimelineToken)
                    || currentConfig != config || currentContext != context
                    || !currentRuleVersion.equals(ruleVersion)) return;
            HostPosition position = hostPosition;
            if (!isEligible(position)
                    || !matchesContext(position.sessionId(), position.generation())) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.CLOCK_UNAVAILABLE);
                return;
            }
            // Legacy keywords retain their provider-level cooldown. V2 rules have
            // independent cooldown state inside SpeechAdMatcher.
            candidates.removeIf(candidate -> RULE_ID.equals(candidate.ruleId())
                    && !acceptLegacyMatchLocked(candidate.startMs()));
            if (candidates.isEmpty()) return;
            for (AdAudioCandidate ignored : candidates) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_MATCHED);
            }
        }
        for (AdAudioCandidate candidate : candidates) {
            notifyCandidate(currentListener, candidate);
        }
    }

    private boolean acceptLegacyMatchLocked(long startMs) {
        if (lastMatchPositionMs != Long.MIN_VALUE
                && startMs >= lastMatchPositionMs
                && startMs - lastMatchPositionMs < MATCH_COOLDOWN_MS) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_COOLDOWN);
            return false;
        }
        lastMatchPositionMs = startMs;
        return true;
    }

    private AdAudioCandidate keywordCandidate(SessionContext currentContext,
                                              String currentRuleVersion,
                                              long startMs, long endMs) {
        if (currentContext == null) return null;
        try {
            return new AdAudioCandidate(
                    currentContext.sessionId(), currentContext.generation(), RULE_ID,
                    currentRuleVersion, startMs, endMs, true, 1.0d, ID);
        } catch (RuntimeException error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return null;
        }
    }

    private AdAudioCandidate compoundCandidate(SessionContext currentContext,
                                               String currentRuleVersion,
                                               SpeechAdMatcher.Match match) {
        if (currentContext == null) return null;
        long startMs = Math.max(0L, match.firstStartUs() / 1_000L);
        startMs = startMs > match.preRollMs()
                ? startMs - match.preRollMs() : 0L;
        long endMs = saturatedAdd(match.lastEndUs() / 1_000L, match.postRollMs());
        if (endMs <= startMs) return null;
        try {
            return new AdAudioCandidate(
                    currentContext.sessionId(), currentContext.generation(), match.ruleId(),
                    currentRuleVersion, startMs, endMs, false, 1.0d, ID);
        } catch (RuntimeException error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return null;
        }
    }

    private void processRecognitionError(long token) {
        Listener currentListener;
        synchronized (this) {
            if (closed || token != instanceToken || context == null) {
                diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_STALE_CALLBACK);
                return;
            }
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
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
        dispatchOwner();
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
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.STALE_GENERATION);
        }
        mailbox.clear();
        ownerCommands.removeIf(command -> command instanceof ResetSessionCommand
                || command instanceof AcceptNextPcmCommand || command instanceof RecognitionResultCommand
                || command instanceof RecognitionErrorCommand
                || command instanceof ResetMatcherCommand);
        pendingRecognitionResults = 0;
        matcherResetScheduled = false;
        acceptScheduled = false;
        if (recognitionSession != null) {
            enqueueOwnerCommandLocked(new ResetSessionCommand(
                    instanceToken, timelineToken), true);
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
        pendingActivationToken = -1L;
        discardObsoleteCommandsLocked();
        retireCaptureLocked();
        if (staleQueuedPcm && !mailbox.isEmpty()) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.STALE_GENERATION);
        }
        mailbox.clear();
        acceptScheduled = false;
        retireRecognitionSessionLocked(resetSession);
    }

    private void discardObsoleteCommandsLocked() {
        ownerCommands.removeIf(command -> !(command instanceof CloseSessionCommand)
                && !(command instanceof CloseCaptureCommand));
        pendingRecognitionResults = 0;
        matcherResetScheduled = false;
        modelCheckScheduled = false;
        acceptScheduled = false;
    }

    private void retireCaptureLocked() {
        if (registration == null && captureLease == null) return;
        enqueueOwnerCommandLocked(new CloseCaptureCommand(registration, captureLease), true);
        registration = null;
        captureLease = null;
    }

    private void retireRecognitionSessionLocked(boolean resetFirst) {
        if (recognitionSession == null) return;
        SpeechRecognitionFactory.Session retired = recognitionSession;
        recognitionSession = null;
        diagnostics.recordQuietly(AdAudioDiagnostics.Code.SPEECH_CLOSE_PENDING);
        // Unregister first (also lock-free), then physically close native, before any new create.
        enqueueOwnerCommandLocked(new CloseSessionCommand(retired, resetFirst), false);
    }

    private ProviderError providerError(ErrorCode code, String detail) {
        return new ProviderError(ID, code, detail);
    }

    private void notifyCandidate(Listener target, AdAudioCandidate candidate) {
        if (target == null) return;
        try {
            target.onCandidate(candidate);
        } catch (RuntimeException error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
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
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void notifyTimelineReset(Listener target, TimelineReset reset) {
        if (target == null) return;
        try {
            target.onTimelineReset(reset);
        } catch (RuntimeException error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
        }
    }

    private void safeClose(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception error) {
            diagnostics.recordQuietly(AdAudioDiagnostics.Code.MATCHER_ERROR);
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
