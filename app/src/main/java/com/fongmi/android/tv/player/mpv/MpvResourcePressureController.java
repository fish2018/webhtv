package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/** Session-isolated resource pressure ceilings shared by MPV cache and preload control. */
public final class MpvResourcePressureController {

    public static final long RECOVERY_STABLE_MS = 30_000L;
    public static final long RECOVERY_STEP_INTERVAL_MS = 15_000L;
    public static final long CONSTRAINED_COOLDOWN_MS = 30_000L;
    public static final long HARD_COOLDOWN_MS = 60_000L;
    public static final int RECOVERY_MIN_NORMAL_SAMPLES = 2;

    private PlaybackAutoContext.SessionToken session = PlaybackAutoContext.SessionToken.none();
    private State state = State.IDLE;
    private RecoveryClass recoveryClass = RecoveryClass.NONE;
    private long forwardCeilingBytes = MpvForwardCachePolicy.MAX_FORWARD_BYTES;
    private long backCeilingBytes = MpvBackCachePolicy.MAX_BACK_BYTES;
    private boolean ceilingsInitialized;
    private boolean expansionAllowed = true;
    private boolean preloadAllowed = true;
    private long lastAcceptedRevision = -1;
    private long lastAcceptedSampleAtElapsedMs = -1;
    private MpvResourcePressurePolicy.Level lastInputLevel =
            MpvResourcePressurePolicy.Level.INACTIVE;
    private MpvResourcePressurePolicy.Reason lastPolicyReason =
            MpvResourcePressurePolicy.Reason.NOT_AUTOMATIC_MPV;
    private long lastPressureAtElapsedMs = -1;
    private long normalSinceElapsedMs = -1;
    private long lastNormalSampleAtElapsedMs = -1;
    private int normalSamples;
    private long lastRecoveryStepAtElapsedMs = -1;
    private int evaluations;
    private int cancellations;
    private Decision lastDecision = Decision.unrestricted();

    public synchronized void beginSession(PlaybackAutoContext.SessionToken token) {
        reset(token);
    }

    public synchronized void endSession(PlaybackAutoContext.SessionToken token) {
        if (!isCurrent(token)) return;
        reset(PlaybackAutoContext.SessionToken.none());
    }

    public synchronized Decision evaluate(
            PlaybackAutoContext.SessionToken token,
            PlaybackAutoContext.SessionToken factsSession,
            MpvResourcePressurePolicy.Assessment assessment,
            Trigger trigger,
            long currentForwardBytes,
            long currentBackBytes,
            long nowElapsedMs) {
        Trigger source = trigger == null ? Trigger.RUNTIME : trigger;
        long now = Math.max(0, nowElapsedMs);
        if (!isCurrent(token) || factsSession == null || !token.equals(factsSession)) {
            return currentDecision(source, Action.HOLD, Reason.STALE_SESSION,
                    false, false, now);
        }
        MpvResourcePressurePolicy.Assessment current = assessment == null
                ? MpvResourcePressurePolicy.Assessment.inactive() : assessment;
        if (current.contextRevision() < lastAcceptedRevision) {
            return currentDecision(source, Action.HOLD, Reason.STALE_REVISION,
                    false, false, now);
        }

        boolean revisionAdvanced = current.contextRevision() > lastAcceptedRevision;
        boolean inputChanged = current.level() != lastInputLevel
                || current.reason() != lastPolicyReason;
        if (revisionAdvanced
                && current.sampledAtElapsedMs() >= 0
                && lastAcceptedSampleAtElapsedMs >= 0
                && current.sampledAtElapsedMs() < lastAcceptedSampleAtElapsedMs) {
            return currentDecision(source, Action.HOLD, Reason.OUT_OF_ORDER_SAMPLE,
                    false, false, now);
        }

        long oldForward = forwardCeilingBytes;
        long oldBack = backCeilingBytes;
        boolean oldExpansionAllowed = expansionAllowed;
        boolean oldPreloadAllowed = preloadAllowed;
        RecoveryClass oldRecoveryClass = recoveryClass;
        ensureCeilings(currentForwardBytes, currentBackBytes, current.level());
        if (evaluations < Integer.MAX_VALUE) evaluations++;

        boolean newEvidence = revisionAdvanced || inputChanged;
        if (revisionAdvanced) {
            lastAcceptedRevision = current.contextRevision();
            if (current.sampledAtElapsedMs() >= 0) {
                lastAcceptedSampleAtElapsedMs = Math.max(
                        lastAcceptedSampleAtElapsedMs,
                        current.sampledAtElapsedMs());
            }
        }
        lastInputLevel = current.level();
        lastPolicyReason = current.reason();

        if (!current.active()) {
            state = current.reason() == MpvResourcePressurePolicy.Reason.CONFIG_PRIORITY
                    ? State.SUPPRESSED : State.INACTIVE;
            recoveryClass = RecoveryClass.NONE;
            forwardCeilingBytes = MpvForwardCachePolicy.MAX_FORWARD_BYTES;
            backCeilingBytes = MpvBackCachePolicy.MAX_BACK_BYTES;
            expansionAllowed = true;
            preloadAllowed = true;
            clearRecoverySamples();
            return remember(source, Action.INACTIVE, Reason.POLICY_INACTIVE,
                    changed(oldForward, oldBack, oldExpansionAllowed,
                            oldPreloadAllowed, oldRecoveryClass),
                    oldPreloadAllowed && !preloadAllowed, now);
        }

        if (current.level() == MpvResourcePressurePolicy.Level.HARD) {
            if (newEvidence) {
                long pressureAt = current.sampledAtElapsedMs() >= 0
                        ? current.sampledAtElapsedMs() : now;
                lastPressureAtElapsedMs = Math.max(lastPressureAtElapsedMs, pressureAt);
            }
            recoveryClass = RecoveryClass.HARD;
            forwardCeilingBytes = MpvForwardCachePolicy.MIN_FORWARD_BYTES;
            backCeilingBytes = 0;
            expansionAllowed = false;
            preloadAllowed = false;
            clearRecoverySamples();
            state = State.RESTRICTED;
            boolean changed = changed(oldForward, oldBack, oldExpansionAllowed,
                    oldPreloadAllowed, oldRecoveryClass);
            return remember(source, changed ? Action.TIGHTENED : Action.HELD,
                    Reason.HARD_PRESSURE, changed,
                    oldPreloadAllowed && !preloadAllowed, now);
        }

        if (current.level() == MpvResourcePressurePolicy.Level.CONSTRAINED
                || current.level() == MpvResourcePressurePolicy.Level.UNKNOWN) {
            if (recoveryClass == RecoveryClass.NONE) {
                recoveryClass = RecoveryClass.CONSTRAINED;
                forwardCeilingBytes = Math.min(forwardCeilingBytes,
                        MpvForwardCachePolicy.normalizeTier(currentForwardBytes));
                backCeilingBytes = Math.min(backCeilingBytes,
                        MpvBackCachePolicy.normalizeTier(currentBackBytes));
            }
            if (newEvidence && recoveryClass != RecoveryClass.HARD) {
                long pressureAt = current.sampledAtElapsedMs() >= 0
                        ? current.sampledAtElapsedMs() : now;
                lastPressureAtElapsedMs = Math.max(lastPressureAtElapsedMs, pressureAt);
            }
            expansionAllowed = false;
            // Metered/roaming links are cost constraints, not device resource
            // pressure. Keep one preload worker running so pause-preload can
            // continue; only cache expansion remains restricted. Unknown
            // state and actual memory/thermal/power pressure still stop it.
            preloadAllowed = recoveryClass != RecoveryClass.HARD
                    && current.level() == MpvResourcePressurePolicy.Level.CONSTRAINED
                    && allowsCostConstrainedPreload(current.reason());
            clearRecoverySamples();
            state = State.RESTRICTED;
            boolean changed = changed(oldForward, oldBack, oldExpansionAllowed,
                    oldPreloadAllowed, oldRecoveryClass);
            return remember(source, changed ? Action.TIGHTENED : Action.HELD,
                    current.level() == MpvResourcePressurePolicy.Level.UNKNOWN
                            ? Reason.UNKNOWN_HOLD : Reason.CONSTRAINED_HOLD,
                    changed, oldPreloadAllowed && !preloadAllowed, now);
        }

        if (recoveryClass == RecoveryClass.NONE
                && forwardCeilingBytes == MpvForwardCachePolicy.MAX_FORWARD_BYTES
                && backCeilingBytes == MpvBackCachePolicy.MAX_BACK_BYTES
                && expansionAllowed
                && preloadAllowed) {
            state = State.ACTIVE;
            return remember(source, Action.STABLE, Reason.NORMAL_STABLE,
                    false, false, now);
        }

        if (recoveryClass == RecoveryClass.NONE) {
            recoveryClass = RecoveryClass.CONSTRAINED;
            forwardCeilingBytes = Math.min(forwardCeilingBytes,
                    MpvForwardCachePolicy.normalizeTier(currentForwardBytes));
            backCeilingBytes = Math.min(backCeilingBytes,
                    MpvBackCachePolicy.normalizeTier(currentBackBytes));
            expansionAllowed = false;
            preloadAllowed = false;
            if (lastPressureAtElapsedMs < 0) lastPressureAtElapsedMs = now;
        }
        observeNormal(current, revisionAdvanced);
        boolean stable = normalSamples >= RECOVERY_MIN_NORMAL_SAMPLES
                && elapsed(now, normalSinceElapsedMs) >= RECOVERY_STABLE_MS;
        boolean cooledDown = cooldownRemainingMs(now) == 0;
        boolean stepReady = lastRecoveryStepAtElapsedMs < 0
                || elapsed(now, lastRecoveryStepAtElapsedMs)
                >= RECOVERY_STEP_INTERVAL_MS;
        if (!stable || !cooledDown || !stepReady) {
            state = State.RECOVERY_WAIT;
            return remember(source, Action.RECOVERY_WAIT, Reason.RECOVERY_WAIT,
                    changed(oldForward, oldBack, oldExpansionAllowed,
                            oldPreloadAllowed, oldRecoveryClass),
                    oldPreloadAllowed && !preloadAllowed, now);
        }

        forwardCeilingBytes = MpvForwardCachePolicy.nextTier(
                forwardCeilingBytes, MpvForwardCachePolicy.MAX_FORWARD_BYTES);
        backCeilingBytes = MpvBackCachePolicy.nextTier(
                backCeilingBytes, MpvBackCachePolicy.MAX_BACK_BYTES);
        expansionAllowed = true;
        preloadAllowed = true;
        lastRecoveryStepAtElapsedMs = now;
        if (forwardCeilingBytes >= MpvForwardCachePolicy.MAX_FORWARD_BYTES
                && backCeilingBytes >= MpvBackCachePolicy.MAX_BACK_BYTES) {
            recoveryClass = RecoveryClass.NONE;
            state = State.ACTIVE;
        } else {
            state = State.RECOVERING;
        }
        return remember(source, Action.RECOVERY_STEP, Reason.RECOVERY_STEP,
                true, false, now);
    }

    private static boolean allowsCostConstrainedPreload(
            MpvResourcePressurePolicy.Reason reason) {
        return reason == MpvResourcePressurePolicy.Reason.METERED
                || reason == MpvResourcePressurePolicy.Reason.ROAMING
                || reason == MpvResourcePressurePolicy.Reason.DATA_SAVER_WHITELISTED;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                session,
                state,
                recoveryClass,
                forwardCeilingBytes,
                backCeilingBytes,
                expansionAllowed,
                preloadAllowed,
                lastAcceptedRevision,
                lastAcceptedSampleAtElapsedMs,
                lastInputLevel,
                lastPolicyReason,
                lastPressureAtElapsedMs,
                normalSinceElapsedMs,
                normalSamples,
                lastRecoveryStepAtElapsedMs,
                evaluations,
                cancellations,
                lastDecision);
    }

    private void observeNormal(
            MpvResourcePressurePolicy.Assessment assessment,
            boolean revisionAdvanced) {
        if (!revisionAdvanced || assessment.sampledAtElapsedMs() < 0
                || assessment.sampledAtElapsedMs() <= lastNormalSampleAtElapsedMs) {
            return;
        }
        lastNormalSampleAtElapsedMs = assessment.sampledAtElapsedMs();
        if (normalSinceElapsedMs < 0) {
            normalSinceElapsedMs = assessment.sampledAtElapsedMs();
        }
        if (normalSamples < Integer.MAX_VALUE) normalSamples++;
    }

    private void ensureCeilings(
            long currentForwardBytes,
            long currentBackBytes,
            MpvResourcePressurePolicy.Level level) {
        if (ceilingsInitialized) return;
        if (level == MpvResourcePressurePolicy.Level.NORMAL
                || level == MpvResourcePressurePolicy.Level.INACTIVE) {
            forwardCeilingBytes = MpvForwardCachePolicy.MAX_FORWARD_BYTES;
            backCeilingBytes = MpvBackCachePolicy.MAX_BACK_BYTES;
        } else {
            forwardCeilingBytes = MpvForwardCachePolicy.normalizeTier(currentForwardBytes);
            backCeilingBytes = MpvBackCachePolicy.normalizeTier(currentBackBytes);
        }
        ceilingsInitialized = true;
    }

    private Decision remember(
            Trigger trigger,
            Action action,
            Reason reason,
            boolean changed,
            boolean cancellationRequested,
            long nowElapsedMs) {
        if (cancellationRequested && cancellations < Integer.MAX_VALUE) cancellations++;
        lastDecision = currentDecision(trigger, action, reason, changed,
                cancellationRequested, nowElapsedMs);
        return lastDecision;
    }

    private Decision currentDecision(
            Trigger trigger,
            Action action,
            Reason reason,
            boolean changed,
            boolean cancellationRequested,
            long nowElapsedMs) {
        return new Decision(
                action,
                trigger,
                reason,
                lastPolicyReason,
                lastInputLevel,
                recoveryClass,
                forwardCeilingBytes,
                backCeilingBytes,
                expansionAllowed,
                preloadAllowed,
                changed,
                cancellationRequested,
                normalSamples,
                cooldownRemainingMs(nowElapsedMs),
                lastAcceptedRevision);
    }

    private boolean changed(
            long oldForward,
            long oldBack,
            boolean oldExpansionAllowed,
            boolean oldPreloadAllowed,
            RecoveryClass oldRecoveryClass) {
        return oldForward != forwardCeilingBytes
                || oldBack != backCeilingBytes
                || oldExpansionAllowed != expansionAllowed
                || oldPreloadAllowed != preloadAllowed
                || oldRecoveryClass != recoveryClass;
    }

    private long cooldownRemainingMs(long nowElapsedMs) {
        if (recoveryClass == RecoveryClass.NONE || lastPressureAtElapsedMs < 0) return 0;
        long cooldown = recoveryClass == RecoveryClass.HARD
                ? HARD_COOLDOWN_MS : CONSTRAINED_COOLDOWN_MS;
        return Math.max(0, cooldown - elapsed(nowElapsedMs, lastPressureAtElapsedMs));
    }

    private void clearRecoverySamples() {
        normalSinceElapsedMs = -1;
        lastNormalSampleAtElapsedMs = -1;
        normalSamples = 0;
        lastRecoveryStepAtElapsedMs = -1;
    }

    private boolean isCurrent(PlaybackAutoContext.SessionToken token) {
        return token != null && token.active() && token.equals(session);
    }

    private void reset(PlaybackAutoContext.SessionToken token) {
        session = token == null ? PlaybackAutoContext.SessionToken.none() : token;
        state = State.IDLE;
        recoveryClass = RecoveryClass.NONE;
        forwardCeilingBytes = MpvForwardCachePolicy.MAX_FORWARD_BYTES;
        backCeilingBytes = MpvBackCachePolicy.MAX_BACK_BYTES;
        ceilingsInitialized = false;
        expansionAllowed = true;
        preloadAllowed = true;
        lastAcceptedRevision = -1;
        lastAcceptedSampleAtElapsedMs = -1;
        lastInputLevel = MpvResourcePressurePolicy.Level.INACTIVE;
        lastPolicyReason = MpvResourcePressurePolicy.Reason.NOT_AUTOMATIC_MPV;
        lastPressureAtElapsedMs = -1;
        clearRecoverySamples();
        evaluations = 0;
        cancellations = 0;
        lastDecision = Decision.unrestricted();
    }

    private static long elapsed(long nowElapsedMs, long thenElapsedMs) {
        if (nowElapsedMs < 0 || thenElapsedMs < 0) return 0;
        return Math.max(0, nowElapsedMs - thenElapsedMs);
    }

    public enum Trigger {
        BASELINE("baseline"),
        RUNTIME("runtime"),
        MEMORY("memory"),
        SYSTEM("system"),
        REBUILD("rebuild");

        private final String label;

        Trigger(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Action {
        INACTIVE("inactive"),
        HOLD("hold"),
        TIGHTENED("tightened"),
        HELD("held"),
        RECOVERY_WAIT("recovery-wait"),
        RECOVERY_STEP("recovery-step"),
        STABLE("stable");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        STALE_SESSION("stale-session"),
        STALE_REVISION("stale-revision"),
        OUT_OF_ORDER_SAMPLE("out-of-order-sample"),
        POLICY_INACTIVE("policy-inactive"),
        HARD_PRESSURE("hard-pressure"),
        CONSTRAINED_HOLD("constrained-hold"),
        UNKNOWN_HOLD("unknown-hold"),
        RECOVERY_WAIT("recovery-wait"),
        RECOVERY_STEP("recovery-step"),
        NORMAL_STABLE("normal-stable");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum State {
        IDLE("idle"),
        INACTIVE("inactive"),
        SUPPRESSED("suppressed"),
        RESTRICTED("restricted"),
        RECOVERY_WAIT("recovery-wait"),
        RECOVERING("recovering"),
        ACTIVE("active");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum RecoveryClass {
        NONE("none"),
        CONSTRAINED("constrained"),
        HARD("hard");

        private final String label;

        RecoveryClass(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Decision(
            Action action,
            Trigger trigger,
            Reason reason,
            MpvResourcePressurePolicy.Reason policyReason,
            MpvResourcePressurePolicy.Level inputLevel,
            RecoveryClass recoveryClass,
            long forwardCeilingBytes,
            long backCeilingBytes,
            boolean expansionAllowed,
            boolean preloadAllowed,
            boolean changed,
            boolean preloadCancellationRequested,
            int normalSamples,
            long cooldownRemainingMs,
            long contextRevision) {

        public Decision {
            action = action == null ? Action.HOLD : action;
            trigger = trigger == null ? Trigger.RUNTIME : trigger;
            reason = reason == null ? Reason.NORMAL_STABLE : reason;
            policyReason = policyReason == null
                    ? MpvResourcePressurePolicy.Reason.NETWORK_UNKNOWN : policyReason;
            inputLevel = inputLevel == null
                    ? MpvResourcePressurePolicy.Level.UNKNOWN : inputLevel;
            recoveryClass = recoveryClass == null ? RecoveryClass.NONE : recoveryClass;
            forwardCeilingBytes = MpvForwardCachePolicy.normalizeTier(forwardCeilingBytes);
            backCeilingBytes = MpvBackCachePolicy.normalizeTier(backCeilingBytes);
            normalSamples = Math.max(0, normalSamples);
            cooldownRemainingMs = Math.max(0, cooldownRemainingMs);
            contextRevision = Math.max(-1, contextRevision);
        }

        public static Decision unrestricted() {
            return new Decision(
                    Action.INACTIVE,
                    Trigger.RUNTIME,
                    Reason.POLICY_INACTIVE,
                    MpvResourcePressurePolicy.Reason.NOT_AUTOMATIC_MPV,
                    MpvResourcePressurePolicy.Level.INACTIVE,
                    RecoveryClass.NONE,
                    MpvForwardCachePolicy.MAX_FORWARD_BYTES,
                    MpvBackCachePolicy.MAX_BACK_BYTES,
                    true,
                    true,
                    false,
                    false,
                    0,
                    0,
                    -1);
        }

        public boolean active() {
            return inputLevel != MpvResourcePressurePolicy.Level.INACTIVE;
        }

        public boolean recovering() {
            return recoveryClass != RecoveryClass.NONE
                    && inputLevel == MpvResourcePressurePolicy.Level.NORMAL;
        }

        public String targetLabel() {
            return "forward-" + forwardCeilingBytes
                    + "-back-" + backCeilingBytes
                    + "-preload-" + preloadAllowed;
        }
    }

    public record Snapshot(
            PlaybackAutoContext.SessionToken session,
            State state,
            RecoveryClass recoveryClass,
            long forwardCeilingBytes,
            long backCeilingBytes,
            boolean expansionAllowed,
            boolean preloadAllowed,
            long lastAcceptedRevision,
            long lastAcceptedSampleAtElapsedMs,
            MpvResourcePressurePolicy.Level lastInputLevel,
            MpvResourcePressurePolicy.Reason lastPolicyReason,
            long lastPressureAtElapsedMs,
            long normalSinceElapsedMs,
            int normalSamples,
            long lastRecoveryStepAtElapsedMs,
            int evaluations,
            int cancellations,
            Decision lastDecision) {
    }
}
