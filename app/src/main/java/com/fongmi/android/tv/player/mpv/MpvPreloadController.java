package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/** Session-isolated hysteresis controller for MPV automatic proxy preloading. */
public final class MpvPreloadController {

    public static final long RECOVERY_STABLE_MS = 15_000L;
    public static final int RECOVERY_MIN_SAMPLES = 2;
    public static final long BUFFER_DECLINE_MIN_MS = 2_000L;
    public static final long MAX_RUNTIME_SAMPLE_GAP_MS = 10_000L;

    private PlaybackAutoContext.SessionToken session = PlaybackAutoContext.SessionToken.none();
    private State state = State.IDLE;
    private boolean ready;
    private long lastAcceptedRevision = -1;
    private long lastAcceptedThroughputSampleAtMs = -1;
    private long lastAcceptedRuntimeSampleAtMs = -1;
    private long previousBufferedMs = -1;
    private int previousRebufferCount = -1;
    private long recoverySinceMs = -1;
    private long lastRecoveryThroughputSampleAtMs = -1;
    private long lastRecoveryRuntimeSampleAtMs = -1;
    private int recoverySamples;
    private int evaluations;
    private int cancellations;
    private MpvPreloadPolicy.Reason lastPolicyReason =
            MpvPreloadPolicy.Reason.NOT_AUTOMATIC;
    private Decision lastDecision = Decision.idle();

    public synchronized void beginSession(PlaybackAutoContext.SessionToken token) {
        reset(token);
    }

    public synchronized void endSession(PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return;
        reset(PlaybackAutoContext.SessionToken.none());
    }

    public synchronized boolean disrupt(PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return false;
        boolean changed = ready || state != State.BLOCKED;
        boolean cancellation = lastDecision.concurrency() > 0;
        ready = false;
        clearRecovery();
        state = State.BLOCKED;
        if (cancellation) cancellations = increment(cancellations);
        lastDecision = new Decision(
                Action.BLOCK,
                Reason.DISCONTINUITY,
                lastPolicyReason,
                state,
                0,
                false,
                changed,
                cancellation,
                0,
                RECOVERY_STABLE_MS,
                lastDecision.ratioPermille(),
                lastAcceptedRevision);
        return changed;
    }

    public synchronized Decision evaluate(
            PlaybackAutoContext.SessionToken token,
            PlaybackAutoContext.SessionToken factsSession,
            MpvPreloadPolicy.Request request,
            long nowElapsedMs) {
        long now = Math.max(0, nowElapsedMs);
        if (!isCurrent(token) || factsSession == null || !token.equals(factsSession)) {
            return currentDecision(Action.HOLD, Reason.STALE_SESSION, false, false, now);
        }
        MpvPreloadPolicy.Request current = request == null
                ? MpvPreloadPolicy.Request.inactive() : request;
        if (current.contextRevision() < lastAcceptedRevision) {
            return currentDecision(Action.HOLD, Reason.STALE_REVISION, false, false, now);
        }
        if (current.contextRevision() == lastAcceptedRevision) {
            return currentDecision(Action.HOLD, Reason.DUPLICATE_REVISION, false, false, now);
        }
        if (outOfOrder(current.throughputSampleAtElapsedMs(),
                lastAcceptedThroughputSampleAtMs)
                || outOfOrder(current.runtimeSampleAtElapsedMs(),
                lastAcceptedRuntimeSampleAtMs)) {
            return currentDecision(Action.HOLD, Reason.OUT_OF_ORDER_SAMPLE, false, false, now);
        }

        boolean oldReady = ready;
        int oldConcurrency = lastDecision.concurrency();
        State oldState = state;
        RuntimeRisk risk = observeRuntimeRisk(current);
        MpvPreloadPolicy.Assessment assessment = MpvPreloadPolicy.assess(
                current.withRuntimeRisk(risk.bufferDeclining(), risk.rebufferRisk()));
        acceptEvidence(current);
        evaluations = increment(evaluations);
        lastPolicyReason = assessment.reason();

        if (!assessment.active()) {
            ready = false;
            clearRecovery();
            state = assessment.reason() == MpvPreloadPolicy.Reason.CONFIG_PRIORITY
                    ? State.SUPPRESSED : State.INACTIVE;
            return remember(Action.INACTIVE, Reason.POLICY_INACTIVE,
                    changed(oldReady, oldState), oldConcurrency > 0, assessment, now);
        }

        if (assessment.signal() == MpvPreloadPolicy.Signal.BLOCK) {
            ready = false;
            clearRecovery();
            state = State.BLOCKED;
            return remember(Action.BLOCK, Reason.POLICY_BLOCK,
                    changed(oldReady, oldState), oldConcurrency > 0, assessment, now);
        }

        if (assessment.signal() == MpvPreloadPolicy.Signal.SUSPEND) {
            state = State.SUSPENDED;
            return remember(Action.SUSPEND, Reason.FOREGROUND_SUSPEND,
                    oldState != state || oldConcurrency > 0,
                    oldConcurrency > 0, assessment, now);
        }

        if (assessment.signal() == MpvPreloadPolicy.Signal.BOOTSTRAP) {
            ready = true;
            clearRecovery();
            state = State.BOOTSTRAP;
            return remember(Action.BOOTSTRAP, Reason.THROUGHPUT_BOOTSTRAP,
                    changed(oldReady, oldState), false, assessment, now);
        }

        if (assessment.signal() == MpvPreloadPolicy.Signal.HOLD) {
            if (!ready) clearRecovery();
            state = ready ? State.ALLOWED : State.BLOCKED;
            return remember(Action.HOLD, Reason.HYSTERESIS_HOLD,
                    oldState != state, false, assessment, now);
        }

        if (ready) {
            state = State.ALLOWED;
            return remember(Action.HOLD, Reason.STABLE,
                    oldState != state, false, assessment, now);
        }

        observeRecoveryEvidence(assessment.request(), now);
        boolean stable = recoverySamples >= RECOVERY_MIN_SAMPLES
                && recoverySinceMs >= 0
                && now - recoverySinceMs >= RECOVERY_STABLE_MS;
        if (!stable) {
            state = State.RECOVERY_WAIT;
            return remember(Action.RECOVERY_WAIT, Reason.RECOVERY_WAIT,
                    oldState != state, false, assessment, now);
        }

        ready = true;
        state = State.ALLOWED;
        return remember(Action.ALLOW, Reason.RECOVERY_COMPLETE,
                true, false, assessment, now);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                session, state, ready, lastAcceptedRevision,
                lastAcceptedThroughputSampleAtMs,
                lastAcceptedRuntimeSampleAtMs,
                recoverySinceMs, recoverySamples, evaluations, cancellations,
                lastPolicyReason, lastDecision);
    }

    private RuntimeRisk observeRuntimeRisk(MpvPreloadPolicy.Request request) {
        long sampledAt = request.runtimeSampleAtElapsedMs();
        if (sampledAt < 0 || sampledAt <= lastAcceptedRuntimeSampleAtMs) {
            return RuntimeRisk.NONE;
        }
        boolean gapTooLarge = lastAcceptedRuntimeSampleAtMs >= 0
                && sampledAt - lastAcceptedRuntimeSampleAtMs
                > MAX_RUNTIME_SAMPLE_GAP_MS;
        boolean declining = request.bufferUsable()
                && !gapTooLarge
                && previousBufferedMs >= 0
                && previousBufferedMs - request.bufferedDurationMs()
                >= BUFFER_DECLINE_MIN_MS;
        boolean rebuffer = previousRebufferCount >= 0
                && request.rebufferCount() > previousRebufferCount;
        previousBufferedMs = request.bufferUsable()
                ? request.bufferedDurationMs() : -1;
        previousRebufferCount = request.rebufferCount();
        return new RuntimeRisk(declining, rebuffer);
    }

    private void acceptEvidence(MpvPreloadPolicy.Request request) {
        lastAcceptedRevision = request.contextRevision();
        if (request.throughputSampleAtElapsedMs() >= 0) {
            lastAcceptedThroughputSampleAtMs = Math.max(
                    lastAcceptedThroughputSampleAtMs,
                    request.throughputSampleAtElapsedMs());
        }
        if (request.runtimeSampleAtElapsedMs() >= 0) {
            lastAcceptedRuntimeSampleAtMs = Math.max(
                    lastAcceptedRuntimeSampleAtMs,
                    request.runtimeSampleAtElapsedMs());
        }
    }

    private void observeRecoveryEvidence(
            MpvPreloadPolicy.Request request,
            long nowElapsedMs) {
        long throughputSample = request.throughputSampleAtElapsedMs();
        long runtimeSample = request.runtimeSampleAtElapsedMs();
        if (throughputSample < 0 || runtimeSample < 0
                || throughputSample <= lastRecoveryThroughputSampleAtMs
                || runtimeSample <= lastRecoveryRuntimeSampleAtMs) {
            return;
        }
        if (recoverySinceMs < 0) recoverySinceMs = nowElapsedMs;
        lastRecoveryThroughputSampleAtMs = throughputSample;
        lastRecoveryRuntimeSampleAtMs = runtimeSample;
        recoverySamples = increment(recoverySamples);
    }

    private Decision remember(
            Action action,
            Reason reason,
            boolean changed,
            boolean cancellationRequested,
            MpvPreloadPolicy.Assessment assessment,
            long nowElapsedMs) {
        if (cancellationRequested) cancellations = increment(cancellations);
        int concurrency = ready
                && assessment.signal() != MpvPreloadPolicy.Signal.SUSPEND ? 1 : 0;
        lastDecision = new Decision(
                action, reason, assessment.reason(), state, concurrency, ready,
                changed, cancellationRequested, recoverySamples,
                recoveryRemainingMs(nowElapsedMs), assessment.ratioPermille(),
                lastAcceptedRevision);
        return lastDecision;
    }

    private Decision currentDecision(
            Action action,
            Reason reason,
            boolean changed,
            boolean cancellationRequested,
            long nowElapsedMs) {
        return new Decision(
                action, reason, lastPolicyReason, state,
                ready && state != State.SUSPENDED ? 1 : 0,
                ready, changed, cancellationRequested, recoverySamples,
                recoveryRemainingMs(nowElapsedMs),
                lastDecision.ratioPermille(), lastAcceptedRevision);
    }

    private long recoveryRemainingMs(long nowElapsedMs) {
        if (ready || recoverySinceMs < 0) return ready ? 0 : RECOVERY_STABLE_MS;
        return Math.max(0, RECOVERY_STABLE_MS
                - Math.max(0, nowElapsedMs - recoverySinceMs));
    }

    private boolean changed(boolean oldReady, State oldState) {
        return oldReady != ready || oldState != state;
    }

    private void clearRecovery() {
        recoverySinceMs = -1;
        lastRecoveryThroughputSampleAtMs = -1;
        lastRecoveryRuntimeSampleAtMs = -1;
        recoverySamples = 0;
    }

    private boolean isCurrent(PlaybackAutoContext.SessionToken token) {
        return token != null && token.active() && token.equals(session);
    }

    private void reset(PlaybackAutoContext.SessionToken token) {
        session = token == null ? PlaybackAutoContext.SessionToken.none() : token;
        state = State.IDLE;
        ready = false;
        lastAcceptedRevision = -1;
        lastAcceptedThroughputSampleAtMs = -1;
        lastAcceptedRuntimeSampleAtMs = -1;
        previousBufferedMs = -1;
        previousRebufferCount = -1;
        clearRecovery();
        evaluations = 0;
        cancellations = 0;
        lastPolicyReason = MpvPreloadPolicy.Reason.NOT_AUTOMATIC;
        lastDecision = Decision.idle();
    }

    private static boolean outOfOrder(long current, long previous) {
        return current >= 0 && previous >= 0 && current < previous;
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private record RuntimeRisk(boolean bufferDeclining, boolean rebufferRisk) {
        private static final RuntimeRisk NONE = new RuntimeRisk(false, false);
    }

    public record Decision(
            Action action,
            Reason reason,
            MpvPreloadPolicy.Reason policyReason,
            State state,
            int concurrency,
            boolean ready,
            boolean changed,
            boolean cancellationRequested,
            int recoverySamples,
            long recoveryRemainingMs,
            long ratioPermille,
            long acceptedRevision) {

        public Decision {
            action = action == null ? Action.HOLD : action;
            reason = reason == null ? Reason.POLICY_BLOCK : reason;
            policyReason = policyReason == null
                    ? MpvPreloadPolicy.Reason.THROUGHPUT_UNKNOWN : policyReason;
            state = state == null ? State.IDLE : state;
            concurrency = Math.clamp(concurrency, 0, 1);
            recoverySamples = Math.max(0, recoverySamples);
            recoveryRemainingMs = Math.max(0, recoveryRemainingMs);
            ratioPermille = Math.max(0, ratioPermille);
        }

        static Decision idle() {
            return new Decision(Action.HOLD, Reason.POLICY_INACTIVE,
                    MpvPreloadPolicy.Reason.NOT_AUTOMATIC,
                    State.IDLE, 0, false, false, false,
                    0, RECOVERY_STABLE_MS, 0, -1);
        }

        public boolean preloadAllowed() {
            return concurrency == 1;
        }

        public String targetLabel() {
            return "threads-" + concurrency + "-state-" + state.label();
        }
    }

    public record Snapshot(
            PlaybackAutoContext.SessionToken session,
            State state,
            boolean ready,
            long lastAcceptedRevision,
            long lastAcceptedThroughputSampleAtMs,
            long lastAcceptedRuntimeSampleAtMs,
            long recoverySinceMs,
            int recoverySamples,
            int evaluations,
            int cancellations,
            MpvPreloadPolicy.Reason lastPolicyReason,
            Decision lastDecision) {
    }

    public enum State {
        IDLE("idle"),
        INACTIVE("inactive"),
        SUPPRESSED("suppressed"),
        BLOCKED("blocked"),
        SUSPENDED("suspended"),
        BOOTSTRAP("bootstrap"),
        RECOVERY_WAIT("recovery-wait"),
        ALLOWED("allowed");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Action {
        INACTIVE("inactive"),
        BLOCK("block"),
        SUSPEND("suspend"),
        BOOTSTRAP("bootstrap"),
        RECOVERY_WAIT("recovery-wait"),
        ALLOW("allow"),
        HOLD("hold");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        POLICY_INACTIVE("policy-inactive"),
        POLICY_BLOCK("policy-block"),
        FOREGROUND_SUSPEND("foreground-suspend"),
        THROUGHPUT_BOOTSTRAP("throughput-bootstrap"),
        RECOVERY_WAIT("recovery-wait"),
        RECOVERY_COMPLETE("recovery-complete"),
        STABLE("stable"),
        HYSTERESIS_HOLD("hysteresis-hold"),
        DISCONTINUITY("discontinuity"),
        STALE_SESSION("stale-session"),
        STALE_REVISION("stale-revision"),
        DUPLICATE_REVISION("duplicate-revision"),
        OUT_OF_ORDER_SAMPLE("out-of-order-sample");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
