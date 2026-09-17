package com.fongmi.android.tv.ad.audio;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.subtitle.SpeechRecognitionFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdAudioRuntimeController implements AutoCloseable {

    public interface PlaybackPort extends AdSkipCoordinator.PlaybackPort {
        boolean isEligible(long sessionId, long generation);

        default AdAudioSignalProvider.SessionContext sessionContext(
                long sessionId, long generation) {
            return new AdAudioSignalProvider.SessionContext(
                    sessionId, generation, "session-" + sessionId, "", Map.of());
        }
    }

    @FunctionalInterface
    public interface ProbeProviderFactory {
        AdAudioSignalProvider create(ProbeRuleSidecar sidecar);
    }

    @FunctionalInterface
    public interface SpeechProviderFactory {
        AdAudioSignalProvider create();
    }

    private static final int RUNTIME_CANDIDATE_CAPACITY = 1_024;

    private final PlaybackMediaSignalHub hub;
    private final PlaybackMediaClock clock;
    private final AdAudioRuleSource ruleSource;
    private final PlaybackPort playback;
    private final Executor worker;
    private final Runnable workerShutdown;
    private final Executor speechWorker;
    private final Runnable speechWorkerShutdown;
    private final ProbeProviderFactory probeProviderFactory;
    private final SpeechProviderFactory speechProviderFactory;
    private final SpeechRecognitionFactory recognitionFactory;
    private final AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();

    private AdAudioRuleSnapshot snapshot = new AdAudioRuleSnapshot(
            "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "");
    private AdSkipCoordinator.UiPort ui;
    private AdSkipCoordinator coordinator;
    private PcmAdAudioSignalProvider pcmProvider;
    private AdAudioSignalProvider probeProvider;
    private AdAudioSignalProvider speechProvider;
    private AdAudioDetectionMultiplexer multiplexer;
    private AdSkipPolicyController policy;
    private AdSkipPolicyController.Mode skipMode = AdSkipPolicyController.Mode.PROMPT;
    private SpeechAdConfig speechConfig = SpeechAdConfig.defaults();
    private boolean enabled;
    private boolean speechSuppressed;
    private String lastRefreshLog = "";
    private long activeSessionId = Long.MIN_VALUE;
    private boolean closed;

    public AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                    AdAudioRuleSource ruleSource, PlaybackPort playback) {
        this(hub, clock, ruleSource, playback, createWorkers(), false);
    }

    public AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                    AdAudioRuleSource ruleSource, PlaybackPort playback,
                                    SpeechRecognitionFactory recognitionFactory) {
        this(hub, clock, ruleSource, playback, createWorkers(), recognitionFactory);
    }

    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Workers workers,
                                     SpeechRecognitionFactory recognitionFactory) {
        this(hub, clock, ruleSource, playback, workers.analysis,
                workers.analysis::shutdownNow, workers.speech,
                workers.speech::shutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"),
                null, Objects.requireNonNull(recognitionFactory, "recognitionFactory"));
    }
    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Workers workers, boolean useNoopSpeechProvider) {
        this(hub, clock, ruleSource, playback, workers.analysis,
                workers.analysis::shutdownNow, workers.speech,
                workers.speech::shutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"),
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                ignored -> new NoopAdAudioSignalProvider("probe"),
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             Executor speechWorker, Runnable speechWorkerShutdown,
                             ProbeProviderFactory probeProviderFactory,
                             SpeechProviderFactory speechProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                speechWorker, speechWorkerShutdown,
                probeProviderFactory, speechProviderFactory, null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             SpeechRecognitionFactory recognitionFactory,
                             Executor speechWorker, Runnable speechWorkerShutdown) {
        this(hub, clock, ruleSource, playback, Runnable::run, () -> { },
                speechWorker, speechWorkerShutdown,
                ignored -> new NoopAdAudioSignalProvider("probe"), null,
                Objects.requireNonNull(recognitionFactory, "recognitionFactory"));
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             ProbeProviderFactory probeProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                probeProviderFactory,
                () -> new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID), null);
    }

    AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                             AdAudioRuleSource ruleSource, PlaybackPort playback,
                             Executor worker, Runnable workerShutdown,
                             ProbeProviderFactory probeProviderFactory,
                             SpeechProviderFactory speechProviderFactory) {
        this(hub, clock, ruleSource, playback, worker, workerShutdown,
                worker, () -> { },
                probeProviderFactory, speechProviderFactory, null);
    }

    private AdAudioRuntimeController(PlaybackMediaSignalHub hub, PlaybackMediaClock clock,
                                     AdAudioRuleSource ruleSource, PlaybackPort playback,
                                     Executor worker, Runnable workerShutdown,
                                     Executor speechWorker, Runnable speechWorkerShutdown,
                                     ProbeProviderFactory probeProviderFactory,
                                     SpeechProviderFactory speechProviderFactory,
                                     SpeechRecognitionFactory recognitionFactory) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ruleSource = Objects.requireNonNull(ruleSource, "ruleSource");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.workerShutdown = workerShutdown;
        this.speechWorker = Objects.requireNonNull(speechWorker, "speechWorker");
        this.speechWorkerShutdown = speechWorkerShutdown;
        this.probeProviderFactory = Objects.requireNonNull(
                probeProviderFactory, "probeProviderFactory");
        if (speechProviderFactory == null && recognitionFactory == null) {
            throw new NullPointerException("speech provider source");
        }
        this.speechProviderFactory = speechProviderFactory;
        this.recognitionFactory = recognitionFactory;
    }

    public synchronized void start(boolean enabled) {
        if (closed) return;
        this.enabled = enabled;
        reconfigureLocked();
    }

    public synchronized void reloadRules() {
        if (closed) return;
        reconfigureLocked();
    }

    private void reconfigureLocked() {
        loadRulesLocked();
        deactivateLocked();
        PlaybackMediaSignalHub.Session session = hub.session();
        speechSuppressed = false;
        if (coordinator != null) coordinator.reset(session.id());
        refreshLocked();
    }

    public synchronized void bindUi(AdSkipCoordinator.UiPort ui) {
        if (closed) return;
        Objects.requireNonNull(ui, "ui");
        // PlaybackActivity rebinds whenever it regains ownership, which can happen on every
        // playback state change. Re-creating the coordinator would orphan the already
        // running providers: they keep a reference to the old one and the output listener
        // then drops their candidates, so the prompt silently disappears.
        if (this.ui == ui && coordinator != null) {
            refreshLocked();
            return;
        }
        if (coordinator != null) coordinator.close();
        this.ui = ui;
        this.coordinator = new AdSkipCoordinator(playback, ui, 5_000L, diagnostics);
        refreshLocked();
    }

    public synchronized void unbindUi() {
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
        deactivateLocked();
    }

    public synchronized void refresh() {
        if (closed) return;
        refreshLocked();
    }

    public synchronized void suspend() {
        if (closed) return;
        // suspend() is called before a new media item starts. Suppression belongs to the
        // previous playback session and must not silently carry into the next item.
        speechSuppressed = false;
        deactivateLocked();
        PlaybackMediaSignalHub.Session session = hub.session();
        if (coordinator != null) coordinator.reset(session.id());
    }

    public synchronized boolean needsPipelineRebuild() {
        return hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO)
                && !hub.isPipelineAttached();
    }

    public synchronized boolean isActive() {
        return isActiveLocked();
    }

    public synchronized AdAudioRuleSnapshot snapshot() {
        return snapshot;
    }

    public synchronized AdSkipPolicyController.Mode skipMode() {
        return skipMode;
    }

    public synchronized void setSkipMode(AdSkipPolicyController.Mode mode) {
        if (closed) return;
        skipMode = Objects.requireNonNull(mode, "mode");
        if (policy != null) installModeResolver(policy);
    }

    public synchronized void setSpeechConfig(SpeechAdConfig config) {
        if (closed) return;
        SpeechAdConfig next = Objects.requireNonNull(config, "config");
        boolean rebuild = !next.equals(speechConfig);
        speechConfig = next;
        if (policy != null) installModeResolver(policy);
        if (rebuild) reconfigureLocked();
    }

    /** Suppresses only speech analysis until the next media session. */
    public synchronized void suppressSpeechForCurrentSession() {
        if (closed || speechSuppressed) return;
        speechSuppressed = true;
        diagnostics.record(AdAudioDiagnostics.Code.SPEECH_RUNTIME_SUPPRESSED);
        deactivateSpeechLocked();
        refreshLocked();
    }

    public synchronized boolean isSpeechSuppressed() {
        return speechSuppressed;
    }

    public synchronized boolean isSpeechConfigured() {
        return speechConfig.enabled() && speechConfig.hasSpeechRules();
    }

    public AdAudioDiagnostics.Snapshot diagnostics() {
        return diagnostics.snapshot();
    }

    public synchronized void stop() {
        if (closed) return;
        enabled = false;
        speechSuppressed = false;
        deactivateLocked();
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        deactivateLocked();
        if (coordinator != null) coordinator.close();
        coordinator = null;
        ui = null;
        if (workerShutdown != null) workerShutdown.run();
        if (speechWorkerShutdown != null) speechWorkerShutdown.run();
    }

    private void loadRulesLocked() {
        try {
            AdAudioRuleSnapshot loaded = ruleSource.load();
            if (loaded == null) {
                diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
                snapshot = new AdAudioRuleSnapshot(
                        "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "RULE_LOAD_FAILED");
            } else {
                snapshot = loaded;
                if (loaded.hasError()) diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
            }
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.RULE_LOAD_FAILED);
            snapshot = new AdAudioRuleSnapshot(
                    "local", "", AudioFingerprintRuleSet.empty(), java.util.List.of(), "RULE_LOAD_FAILED");
        }
    }

    private void refreshLocked() {
        boolean fingerprintReady = enabled && !snapshot.hasError() && snapshot.hasRules();
        boolean speechReady = !speechSuppressed
                && speechConfig.enabled() && speechConfig.hasSpeechRules();
        if (ui == null || (!fingerprintReady && !speechReady)) {
            // Transition-only: refreshLocked runs every 5s from the host position pump, and
            // an unsampled line here would churn the bounded debug-log ring.
            logTransition("refresh skipped ui=" + (ui != null)
                    + " fingerprint=" + fingerprintReady + " speech=" + speechReady);
            deactivateLocked();
            return;
        }
        PlaybackMediaSignalHub.Session session = hub.session();
        if (!playback.isEligible(session.id(), session.generation())) {
            logTransition("refresh ineligible session=" + session.id()
                    + " gen=" + session.generation());
            deactivateLocked();
            return;
        }
        if (multiplexer != null) {
            // Re-validate the hub SESSION: an active-but-parked provider still holds a
            // capture lease bound to the session it was created for. Generation is
            // deliberately not checked -- it bumps on every seek/flush and the providers
            // already self-heal through onTimelineReset, so comparing it here would rebuild
            // the recognizer on each seek.
            if (isActiveLocked() && activeSessionId == session.id()) {
                publishHostPositionLocked(session);
                return;
            }
            deactivateLocked();
        }
        activateLocked(session, fingerprintReady, speechReady);
        publishHostPositionLocked(session);
        // Logged after the host position is published: the speech provider only leaves IDLE
        // once it has an eligible position, so reading state before this is misleading.
        logTransition("activated"
                + " speech=" + (speechProvider == null ? "none" : speechProvider.state())
                + " pcm=" + (pcmProvider == null ? "none" : pcmProvider.state())
                + " capture=" + hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO)
                + " pipeline=" + hub.isPipelineAttached());
    }

    /** Emits {@code message} only when it differs from the previous refresh outcome. */
    private void logTransition(String message) {
        if (message.equals(lastRefreshLog)) return;
        lastRefreshLog = message;
        AdAudioDiagnostics.log("%s", message);
    }

    private void activateLocked(PlaybackMediaSignalHub.Session session,
                                boolean fingerprintReady, boolean speechReady) {
        AdSkipCoordinator currentCoordinator = coordinator;
        if (currentCoordinator == null) return;
        AdAudioSignalProvider.SessionContext context;
        try {
            context = playback.sessionContext(session.id(), session.generation());
        } catch (RuntimeException e) {
            context = null;
        }
        if (context == null || context.sessionId() != session.id()
                || context.generation() != session.generation()) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return;
        }

        AdAudioRuleSnapshot routingSnapshot = routingSnapshotLocked();
        AdSkipPolicyController nextPolicy = new AdSkipPolicyController(
                context, routingSnapshot.version(), RUNTIME_CANDIDATE_CAPACITY,
                currentCoordinator::onCandidate,
                currentCoordinator::onAutoCandidate);
        installModeResolver(nextPolicy);
        AdAudioDetectionMultiplexer[] muxHolder = new AdAudioDetectionMultiplexer[1];
        AdAudioSignalProvider.Listener output = new AdAudioSignalProvider.Listener() {
            @Override
            public void onCandidate(AdAudioSignalProvider.AdAudioCandidate candidate) {
                synchronized (AdAudioRuntimeController.this) {
                    if (multiplexer != muxHolder[0] || policy != nextPolicy
                            || coordinator != currentCoordinator) return;
                }
                nextPolicy.onCandidate(candidate);
            }

            @Override
            public void onProviderError(AdAudioSignalProvider.ProviderError error) {
                if (error != null && !PcmAdAudioSignalProvider.ID.equals(error.providerId())) {
                    diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
                }
            }

            @Override
            public void onTimelineReset(AdAudioSignalProvider.TimelineReset reset) {
                PcmAdAudioSignalProvider currentPcm;
                AdAudioSignalProvider currentProbe;
                AdAudioSignalProvider currentSpeech;
                synchronized (AdAudioRuntimeController.this) {
                    if (multiplexer != muxHolder[0] || policy != nextPolicy
                            || coordinator != currentCoordinator) return;
                    currentPcm = pcmProvider;
                    currentProbe = probeProvider;
                    currentSpeech = speechProvider;
                }
                nextPolicy.onTimelineReset(reset);
                currentCoordinator.onTimelineReset(new PlaybackMediaSignalHub.Lifecycle(
                        reset.sessionId(), reset.generation(),
                        PlaybackMediaSignalHub.ResetReason.valueOf(reset.reason().name()),
                        reset.mediaAnchorMs()));
                notifyTimelineReset(currentPcm, reset);
                notifyTimelineReset(currentProbe, reset);
                notifyTimelineReset(currentSpeech, reset);
            }
        };
        Set<String> allowedRuleIds = new HashSet<>();
        if (fingerprintReady) {
            snapshot.ruleSet().rules().stream()
                    .map(AudioFingerprintRule::id)
                    .forEach(allowedRuleIds::add);
        }
        if (speechReady) {
            if (!speechConfig.keywords().isEmpty()) {
                allowedRuleIds.add(SpeechAdSignalProvider.RULE_ID);
            }
            speechConfig.rules().rules().stream()
                    .map(SpeechAdRule::id)
                    .forEach(allowedRuleIds::add);
        }
        AdAudioDetectionMultiplexer nextMux = new AdAudioDetectionMultiplexer(
                context, routingSnapshot.version(), Set.copyOf(allowedRuleIds),
                RUNTIME_CANDIDATE_CAPACITY, output);
        muxHolder[0] = nextMux;
        PcmAdAudioSignalProvider nextPcm = fingerprintReady
                ? new PcmAdAudioSignalProvider(hub, worker, diagnostics) : null;
        AdAudioSignalProvider nextProbe = fingerprintReady
                ? createProbeProviderLocked() : null;
        AdAudioSignalProvider nextSpeech = speechReady
                ? createSpeechProviderLocked() : null;

        policy = nextPolicy;
        multiplexer = nextMux;
        pcmProvider = nextPcm;
        probeProvider = nextProbe;
        speechProvider = nextSpeech;
        activeSessionId = session.id();

        startProvider(nextPcm, true, context, routingSnapshot, nextMux);
        startProvider(nextProbe, snapshot.probeAvailable(), context, routingSnapshot, nextMux);
        startProvider(nextSpeech, true, context, routingSnapshot, nextMux);
        if (nextPcm != null && !isRunning(nextPcm)) {
            closeProvider(nextPcm);
            if (pcmProvider == nextPcm) pcmProvider = null;
        }
        if (nextProbe != null && !isRunning(nextProbe)) {
            closeProvider(nextProbe);
            if (probeProvider == nextProbe) probeProvider = null;
        }
        if (nextSpeech != null && !isReadyForHostPosition(nextSpeech)) {
            closeProvider(nextSpeech);
            if (speechProvider == nextSpeech) speechProvider = null;
        }
    }
    private void deactivateLocked() {
        PcmAdAudioSignalProvider oldPcm = pcmProvider;
        AdAudioSignalProvider oldProbe = probeProvider;
        AdAudioSignalProvider oldSpeech = speechProvider;
        AdAudioDetectionMultiplexer oldMux = multiplexer;
        AdSkipPolicyController oldPolicy = policy;
        pcmProvider = null;
        probeProvider = null;
        speechProvider = null;
        multiplexer = null;
        policy = null;
        activeSessionId = Long.MIN_VALUE;
        closeProvider(oldSpeech);
        closeProvider(oldProbe);
        closeProvider(oldPcm);
        if (oldMux != null) oldMux.close();
        if (oldPolicy != null) oldPolicy.close();
    }

    private void deactivateSpeechLocked() {
        AdAudioSignalProvider oldSpeech = speechProvider;
        speechProvider = null;
        closeProvider(oldSpeech);
    }

    private boolean isActiveLocked() {
        // IDLE counts as active for the speech provider: it parks there while the position
        // is not yet eligible (buffering, duration unknown) but keeps its recognizer and
        // capture lease. Treating it as inactive would make every refresh tear it down.
        return isRunning(pcmProvider) || isRunning(probeProvider)
                || isReadyForHostPosition(speechProvider);
    }

    private void publishHostPositionLocked(PlaybackMediaSignalHub.Session session) {
        if (multiplexer == null) return;
        AdSkipCoordinator.PlaybackSnapshot playbackSnapshot;
        try {
            playbackSnapshot = playback.snapshot(session.id(), session.generation());
        } catch (RuntimeException e) {
            return;
        }
        if (playbackSnapshot == null
                || playbackSnapshot.sessionId() != session.id()
                || playbackSnapshot.generation() != session.generation()) return;
        AdAudioSignalProvider.HostPosition position =
                new AdAudioSignalProvider.HostPosition(
                        session.id(), session.generation(),
                        Math.max(0L, playbackSnapshot.positionMs()),
                        Math.max(-1L, playbackSnapshot.durationMs()),
                        playbackSnapshot.seekable(), playbackSnapshot.live());
        multiplexer.onHostPosition(position);
        notifyHostPosition(pcmProvider, position);
        notifyHostPosition(probeProvider, position);
        notifyHostPosition(speechProvider, position);
    }

    private AdAudioSignalProvider createProbeProviderLocked() {
        if (!snapshot.probeAvailable()) return new NoopAdAudioSignalProvider("probe");
        try {
            AdAudioSignalProvider provider =
                    probeProviderFactory.create(snapshot.probeSidecar());
            return provider == null ? new NoopAdAudioSignalProvider("probe") : provider;
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return new NoopAdAudioSignalProvider("probe");
        }
    }

    private void startProvider(AdAudioSignalProvider provider, boolean providerEnabled,
                               AdAudioSignalProvider.SessionContext context,
                               AdAudioRuleSnapshot routingSnapshot,
                               AdAudioSignalProvider.Listener listener) {
        if (provider == null) return;
        try {
            provider.setEnabled(providerEnabled);
            provider.start(context, routingSnapshot, listener);
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            closeProvider(provider);
        }
    }

    private AdAudioSignalProvider createSpeechProviderLocked() {
        try {
            if (recognitionFactory != null) {
                return new SpeechAdSignalProvider(
                        hub, recognitionFactory, () -> speechConfig,
                        speechWorker, diagnostics);
            }
            AdAudioSignalProvider provider = speechProviderFactory.create();
            return provider == null
                    ? new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID)
                    : provider;
        } catch (RuntimeException e) {
            diagnostics.record(AdAudioDiagnostics.Code.MATCHER_ERROR);
            return new NoopAdAudioSignalProvider(SpeechAdSignalProvider.ID);
        }
    }

    private AdAudioRuleSnapshot routingSnapshotLocked() {
        String version = snapshot.version().isEmpty()
                ? "speech-runtime-v1" : snapshot.version();
        if (!speechConfig.rules().isEmpty()) {
            version = withSpeechRulesVersion(version, speechConfig.rulesVersion());
        }
        if (version.equals(snapshot.version())) return snapshot;
        return new AdAudioRuleSnapshot(
                snapshot.sourceId(), version, snapshot.ruleSet(),
                snapshot.warnings(), snapshot.lastError(), snapshot.probeSidecar());
    }

    private static String withSpeechRulesVersion(String baseVersion, String rulesVersion) {
        String suffix = ":speech-v2-" + rulesVersion;
        if (baseVersion.length() + suffix.length() <= 128) {
            return baseVersion + suffix;
        }
        // AdAudioDetectionMultiplexer and AdSkipPolicyController intentionally bound
        // routing versions. Preserve a deterministic identity without allowing an
        // unusually long external source version to break speech activation.
        return "speech-base-" + sha256Prefix(baseVersion) + suffix;
    }

    private static String sha256Prefix(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                result.append(String.format("%02x", digest[i] & 0xff));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private void installModeResolver(AdSkipPolicyController target) {
        target.setMode(skipMode);
        target.setPromptOnlyRuleIds(speechConfig.rules().rules().stream()
                .map(SpeechAdRule::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        target.setModeResolver(providerId -> SpeechAdSignalProvider.ID.equals(providerId)
                ? speechConfig.mode() : skipMode);
    }
    private static boolean isReadyForHostPosition(AdAudioSignalProvider provider) {
        if (provider == null) return false;
        AdAudioSignalProvider.ProviderState state = provider.state();
        return state == AdAudioSignalProvider.ProviderState.IDLE
                || state == AdAudioSignalProvider.ProviderState.RUNNING;
    }
    private static boolean isRunning(AdAudioSignalProvider provider) {
        return provider != null
                && provider.state() == AdAudioSignalProvider.ProviderState.RUNNING;
    }

    private static void notifyTimelineReset(
            AdAudioSignalProvider provider,
            AdAudioSignalProvider.TimelineReset reset) {
        if (provider == null) return;
        try {
            provider.onTimelineReset(reset);
        } catch (RuntimeException ignored) {
        }
    }

    private static void notifyHostPosition(
            AdAudioSignalProvider provider,
            AdAudioSignalProvider.HostPosition position) {
        if (provider == null) return;
        try {
            provider.onHostPosition(position);
        } catch (RuntimeException ignored) {
        }
    }

    private static void closeProvider(AdAudioSignalProvider provider) {
        if (provider == null) return;
        try {
            provider.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static Workers createWorkers() {
        ExecutorService analysis = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ad-audio-matcher");
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService speech = Executors.newSingleThreadExecutor(r -> {
            Runnable speechTask = () -> {
                try {
                    android.os.Process.setThreadPriority(
                            android.os.Process.THREAD_PRIORITY_BACKGROUND);
                } catch (RuntimeException ignored) {
                }
                r.run();
            };
            Thread thread = new Thread(speechTask, "ad-audio-speech-owner");
            thread.setDaemon(true);
            try {
                thread.setPriority(Thread.NORM_PRIORITY - 1);
            } catch (RuntimeException ignored) {
            }
            return thread;
        });
        return new Workers(analysis, speech);
    }

    /** Pure-Java playback health gate used to disable speech work after confirmed degradation. */
    public static final class SpeechAdPlaybackHealth {

        public static final long SAMPLE_INTERVAL_MS = 5_000L;
        public static final long MAX_SAMPLE_GAP_MS = 10_000L;
        public static final int REQUIRED_DEGRADED_SAMPLES = 2;
        public static final long DROPPED_FRAMES_PER_SECOND_THRESHOLD = 4L;

        private long lastSampleAtMs = -1L;
        private long lastDroppedFrames = -1L;
        private long lastAudioUnderruns = -1L;
        private long lastRebufferCount = -1L;
        private int degradedSamples;
        private boolean suppressed;

        public synchronized Decision observe(long nowMs, long droppedFrames,
                                             long audioUnderruns, long rebufferCount) {
            long now = Math.max(0L, nowMs);
            long dropped = Math.max(0L, droppedFrames);
            long underruns = Math.max(0L, audioUnderruns);
            long rebuffers = Math.max(0L, rebufferCount);
            if (suppressed) return Decision.SUPPRESSED;
            if (lastSampleAtMs >= 0L && now <= lastSampleAtMs) return Decision.HELD;
            if (lastSampleAtMs >= 0L && now - lastSampleAtMs < SAMPLE_INTERVAL_MS) {
                return Decision.HELD;
            }
            boolean gapTooLarge = lastSampleAtMs >= 0L
                    && now - lastSampleAtMs > MAX_SAMPLE_GAP_MS;
            long intervalMs = lastSampleAtMs < 0L ? 0L : now - lastSampleAtMs;
            long droppedDelta = positiveDelta(dropped, lastDroppedFrames);
            long underrunDelta = positiveDelta(underruns, lastAudioUnderruns);
            long rebufferDelta = positiveDelta(rebuffers, lastRebufferCount);
            lastSampleAtMs = now;
            lastDroppedFrames = dropped;
            lastAudioUnderruns = underruns;
            lastRebufferCount = rebuffers;
            if (gapTooLarge) {
                degradedSamples = 0;
                return Decision.OBSERVED;
            }
            long minimumDropped = intervalMs <= 0L
                    || intervalMs > (Long.MAX_VALUE - 999L)
                    / DROPPED_FRAMES_PER_SECOND_THRESHOLD
                    ? Long.MAX_VALUE
                    : (intervalMs * DROPPED_FRAMES_PER_SECOND_THRESHOLD + 999L) / 1_000L;
            boolean droppedRate = droppedDelta >= minimumDropped;
            boolean degraded = underrunDelta > 0L || rebufferDelta > 0L || droppedRate;
            degradedSamples = degraded
                    ? Math.min(REQUIRED_DEGRADED_SAMPLES, degradedSamples + 1) : 0;
            if (degradedSamples >= REQUIRED_DEGRADED_SAMPLES) {
                suppressed = true;
                return Decision.SUPPRESS;
            }
            return degraded ? Decision.DEGRADED : Decision.OBSERVED;
        }

        public synchronized boolean isSuppressed() {
            return suppressed;
        }

        public synchronized void reset() {
            lastSampleAtMs = -1L;
            lastDroppedFrames = -1L;
            lastAudioUnderruns = -1L;
            lastRebufferCount = -1L;
            degradedSamples = 0;
            suppressed = false;
        }

        private static long positiveDelta(long current, long previous) {
            if (previous < 0L || current <= previous) return 0L;
            return current - previous;
        }

        public enum Decision {
            HELD,
            OBSERVED,
            DEGRADED,
            SUPPRESS,
            SUPPRESSED
        }
    }

    private record Workers(ExecutorService analysis, ExecutorService speech) {
    }
}
