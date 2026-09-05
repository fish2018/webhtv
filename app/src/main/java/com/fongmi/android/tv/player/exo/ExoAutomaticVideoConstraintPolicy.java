package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Pure automatic-mode policy that keeps project constraints separate from Media3 ABR. */
final class ExoAutomaticVideoConstraintPolicy {

    static final long FAULT_STEP_COOLDOWN_MS = 15_000L;
    static final long FAULT_HOLD_MS = 30_000L;
    static final long RECOVERY_STABLE_MS = 30_000L;

    static final int SOURCE_THERMAL = 1;
    static final int SOURCE_DATA_COST = 1 << 1;
    static final int SOURCE_DECODER = 1 << 2;
    static final int SOURCE_FAULT = 1 << 3;

    static final Limit SOFTWARE_DECODER_LIMIT = new Limit(1920, 1080, 30, 6_000_000);
    static final Limit THERMAL_SEVERE_LIMIT = new Limit(1920, 1080, 30, 8_000_000);
    static final Limit THERMAL_CRITICAL_LIMIT = new Limit(1280, 720, 30, 4_000_000);
    static final Limit METERED_REMOTE_LIMIT = new Limit(1920, 1080, 60, 8_000_000);
    static final Limit EXPENSIVE_NETWORK_LIMIT = new Limit(1280, 720, 30, 4_000_000);

    private ExoAutomaticVideoConstraintPolicy() {
    }

    static State initial(Limit baseline) {
        Limit safeBaseline = baseline == null ? Limit.minimum() : baseline;
        return new State(safeBaseline, null, -1, -1, -1, 0, Fault.NONE);
    }

    static Decision evaluate(State previous, Input input) {
        return resolve(previous, input, Fault.NONE);
    }

    static Decision onFault(State previous, Input input, Fault fault) {
        return resolve(previous, input, fault == null ? Fault.NONE : fault);
    }

    static Environment environment(PlaybackAutoContext context, long elapsedRealtimeMs) {
        PlaybackAutoContext snapshot = context == null ? PlaybackAutoContext.empty() : context;
        PlaybackAutoContext.DeviceFacts device = snapshot.device();
        long now = Math.max(0, elapsedRealtimeMs);
        Limit cap = Limit.unbounded();
        int observedSources = 0;
        int restrictiveSources = 0;
        int releasableSources = 0;
        boolean recoveryBlocked = false;
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);

        PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermalFact = device.thermalState();
        PlaybackAutoContext.ThermalState thermal = PlaybackAutoContext.ThermalState.UNKNOWN;
        if (thermalFact.isUsable(now)) {
            observedSources |= SOURCE_THERMAL;
            thermal = thermalFact.value();
            switch (thermal) {
                case NOMINAL -> releasableSources |= SOURCE_THERMAL;
                case MODERATE -> {
                    recoveryBlocked = true;
                    reasons.add(Reason.THERMAL_MODERATE_HOLD);
                }
                case SEVERE -> {
                    cap = cap.intersect(THERMAL_SEVERE_LIMIT);
                    restrictiveSources |= SOURCE_THERMAL;
                    reasons.add(Reason.THERMAL_SEVERE);
                }
                case CRITICAL -> {
                    cap = cap.intersect(THERMAL_CRITICAL_LIMIT);
                    restrictiveSources |= SOURCE_THERMAL;
                    reasons.add(Reason.THERMAL_CRITICAL);
                }
                default -> {
                }
            }
        }

        PlaybackAutoContext.Fact<PlaybackAutoContext.PowerState> powerFact = device.powerState();
        PlaybackAutoContext.PowerState power = PlaybackAutoContext.PowerState.UNKNOWN;
        if (powerFact.isUsable(now)) {
            power = powerFact.value();
            if (power == PlaybackAutoContext.PowerState.POWER_SAVE) {
                recoveryBlocked = true;
                reasons.add(Reason.POWER_SAVE_HOLD);
            }
        }

        PlaybackAutoContext.DecodeMode decodeMode = actualDecodeMode(snapshot, now);
        if (decodeMode != PlaybackAutoContext.DecodeMode.UNKNOWN) {
            observedSources |= SOURCE_DECODER;
            if (decodeMode == PlaybackAutoContext.DecodeMode.SOFTWARE) {
                cap = cap.intersect(SOFTWARE_DECODER_LIMIT);
                restrictiveSources |= SOURCE_DECODER;
                reasons.add(Reason.SOFTWARE_DECODER);
            } else {
                releasableSources |= SOURCE_DECODER;
            }
        }

        CostScope costScope = costScope(snapshot.path(), now);
        PlaybackAutoContext.NetworkCost networkCost = usableValue(
                device.networkCost(), PlaybackAutoContext.NetworkCost.UNKNOWN, now);
        PlaybackAutoContext.NetworkSnapshot network = usableValue(
                device.networkSnapshot(), PlaybackAutoContext.NetworkSnapshot.unknown(), now);
        PlaybackAutoContext.DataSaverState dataSaver = network.dataSaverState();
        if (costScope == CostScope.LOCAL) {
            observedSources |= SOURCE_DATA_COST;
            releasableSources |= SOURCE_DATA_COST;
        } else if (dataSaver == PlaybackAutoContext.DataSaverState.ENABLED) {
            observedSources |= SOURCE_DATA_COST;
            restrictiveSources |= SOURCE_DATA_COST;
            cap = cap.intersect(EXPENSIVE_NETWORK_LIMIT);
            reasons.add(Reason.DATA_SAVER);
        } else if (networkCost == PlaybackAutoContext.NetworkCost.ROAMING) {
            observedSources |= SOURCE_DATA_COST;
            restrictiveSources |= SOURCE_DATA_COST;
            cap = cap.intersect(EXPENSIVE_NETWORK_LIMIT);
            reasons.add(Reason.ROAMING);
        } else if (networkCost == PlaybackAutoContext.NetworkCost.METERED
                && costScope == CostScope.REMOTE) {
            observedSources |= SOURCE_DATA_COST;
            restrictiveSources |= SOURCE_DATA_COST;
            cap = cap.intersect(METERED_REMOTE_LIMIT);
            reasons.add(Reason.METERED_REMOTE);
        } else if (networkCost == PlaybackAutoContext.NetworkCost.UNMETERED) {
            observedSources |= SOURCE_DATA_COST;
            releasableSources |= SOURCE_DATA_COST;
        }

        return new Environment(
                cap,
                observedSources,
                restrictiveSources,
                releasableSources,
                recoveryBlocked,
                thermal,
                power,
                networkCost,
                dataSaver,
                decodeMode,
                costScope,
                new ArrayList<>(reasons));
    }

    private static Decision resolve(State previous, Input input, Fault faultSignal) {
        Input currentInput = input == null ? Input.minimum() : input;
        State current = previous == null ? initial(currentInput.baseline()) : previous;
        long now = currentInput.elapsedRealtimeMs();
        Limit faultLimit = current.faultLimit();
        long lastFaultAt = current.lastFaultAtMs();
        long lastFaultStepAt = current.lastFaultStepAtMs();
        Fault lastFault = current.lastFault();
        boolean faultStepped = false;

        if (faultSignal != Fault.NONE) {
            lastFaultAt = now;
            lastFault = faultSignal;
            if (lastFaultStepAt < 0 || now - lastFaultStepAt >= FAULT_STEP_COOLDOWN_MS) {
                faultLimit = nextFaultLimit(
                        currentInput.tiers(),
                        current.effective(),
                        currentInput.selectedTrack());
                lastFaultStepAt = now;
                faultStepped = true;
            }
        }

        Environment environment = currentInput.environment();
        Limit rawTarget = currentInput.baseline().intersect(environment.cap());
        int restrictiveSources = environment.restrictiveSources();
        int releasableSources = environment.releasableSources();
        boolean faultActive = faultLimit != null && lastFaultAt >= 0
                && now - lastFaultAt < FAULT_HOLD_MS;
        if (faultActive) {
            rawTarget = rawTarget.intersect(faultLimit);
            restrictiveSources |= SOURCE_FAULT;
        } else if (faultLimit != null) {
            releasableSources |= SOURCE_FAULT;
        }

        Limit effective = current.effective();
        long recoverySince = current.recoverySinceMs();
        int holdSources = current.holdSources();
        Limit immediate = effective.intersect(rawTarget);
        Action action = Action.UNCHANGED;

        if (!immediate.equals(effective)) {
            effective = immediate;
            holdSources |= restrictiveSources;
            recoverySince = -1;
            action = faultSignal != Fault.NONE ? Action.FAULT_TIGHTENED : Action.TIGHTENED;
        } else if (rawTarget.equals(effective)) {
            holdSources |= restrictiveSources;
            recoverySince = -1;
            if (faultSignal != Fault.NONE) {
                action = faultStepped ? Action.FAULT_CONSTRAINT_HELD : Action.FAULT_COOLDOWN;
            }
        } else {
            int currentEvidenceSources = restrictiveSources | releasableSources;
            int unresolvedSources = holdSources & ~currentEvidenceSources;
            boolean recoveryEligible = unresolvedSources == 0
                    && currentInput.playbackStable()
                    && !environment.recoveryBlocked();
            if (!recoveryEligible) {
                recoverySince = -1;
                action = Action.RECOVERY_BLOCKED;
            } else if (recoverySince < 0) {
                recoverySince = now;
                action = Action.RECOVERY_PENDING;
            } else if (now - recoverySince >= RECOVERY_STABLE_MS) {
                effective = rawTarget;
                holdSources = restrictiveSources;
                recoverySince = -1;
                action = Action.RECOVERED;
            } else {
                action = Action.RECOVERY_PENDING;
            }
        }

        if (!faultActive && faultLimit != null
                && (action == Action.RECOVERED || rawTarget.equals(effective))) {
            faultLimit = null;
            holdSources &= ~SOURCE_FAULT;
            if (lastFaultAt >= 0 && now - lastFaultAt >= FAULT_HOLD_MS) lastFault = Fault.NONE;
        }

        State next = new State(
                effective,
                faultLimit,
                lastFaultAt,
                lastFaultStepAt,
                recoverySince,
                holdSources,
                lastFault);
        long recoveryRemainingMs = action == Action.RECOVERY_PENDING
                ? Math.max(0, RECOVERY_STABLE_MS - Math.max(0, now - recoverySince)) : 0;
        long faultRemainingMs = faultActive
                ? Math.max(1, FAULT_HOLD_MS - Math.max(0, now - lastFaultAt)) : 0;
        long reevaluateAfterMs = recoveryRemainingMs > 0
                ? recoveryRemainingMs : faultRemainingMs;
        return new Decision(
                next,
                rawTarget,
                action,
                faultSignal,
                faultStepped,
                !effective.equals(current.effective()),
                faultActive,
                recoveryRemainingMs,
                reevaluateAfterMs,
                environment,
                currentInput.resourceMode());
    }

    private static Limit nextFaultLimit(
            List<Limit> tiers,
            Limit effective,
            SelectedTrack selectedTrack) {
        List<Limit> safeTiers = tiers == null || tiers.isEmpty()
                ? List.of(effective, Limit.minimum()) : tiers;
        Limit reference = effective;
        SelectedTrack selected = selectedTrack == null ? SelectedTrack.unknown() : selectedTrack;
        if (selected.hasSize()
                && (selected.width() < effective.width() || selected.height() < effective.height())) {
            reference = new Limit(
                    selected.width(),
                    selected.height(),
                    selected.frameRate() > 0 ? selected.frameRate() : effective.frameRate(),
                    selected.bitrate() > 0 ? selected.bitrate() : effective.maxVideoBitrate());
        }
        int index = safeTiers.size() - 1;
        for (int i = 0; i < safeTiers.size(); i++) {
            Limit tier = safeTiers.get(i);
            if (reference.width() >= tier.width() && reference.height() >= tier.height()) {
                index = i;
                break;
            }
        }
        Limit next = safeTiers.get(Math.min(safeTiers.size() - 1, index + 1));
        return effective.intersect(next);
    }

    private static PlaybackAutoContext.DecodeMode actualDecodeMode(
            PlaybackAutoContext context,
            long now) {
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> actual =
                context.media().decoder().videoDecodeMode();
        if (actual.isUsable(now)) return actual.value();
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> requested = context.decodeMode();
        return requested.isUsable(now) ? requested.value() : PlaybackAutoContext.DecodeMode.UNKNOWN;
    }

    private static CostScope costScope(PlaybackAutoContext.PathFacts pathFacts, long now) {
        PlaybackAutoContext.PathFacts path = pathFacts == null
                ? PlaybackAutoContext.PathFacts.unknown() : pathFacts;
        PlaybackAutoContext.PathKind player = usableValue(
                path.playerPath(), PlaybackAutoContext.PathKind.UNKNOWN, now);
        PlaybackAutoContext.PathKind upstream = usableValue(
                path.upstreamPath(), PlaybackAutoContext.PathKind.UNKNOWN, now);
        if (player == PlaybackAutoContext.PathKind.REMOTE
                || player == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK
                || upstream == PlaybackAutoContext.PathKind.REMOTE
                || upstream == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK) {
            return CostScope.REMOTE;
        }
        if (player == PlaybackAutoContext.PathKind.LOCAL
                || player == PlaybackAutoContext.PathKind.LAN_PRIVATE) {
            return CostScope.LOCAL;
        }
        return CostScope.UNKNOWN;
    }

    private static <T> T usableValue(
            PlaybackAutoContext.Fact<T> fact,
            T fallback,
            long now) {
        return fact != null && fact.isUsable(now) ? fact.value() : fallback;
    }

    enum Fault {
        NONE("none"),
        DROPPED_FRAMES("dropped-frames"),
        CODEC_ERROR("codec-error"),
        // 起播固定选约束内最高画质后，原生 ABR 不再提供吞吐保护，带宽不足只能表现为持续重缓冲。
        // 解码器本身没有压力，掉帧和编解码错误都不会触发，因此需要独立的吞吐不足证据来降档。
        THROUGHPUT("throughput");

        private final String label;

        Fault(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum Action {
        UNCHANGED("unchanged"),
        TIGHTENED("tightened"),
        FAULT_TIGHTENED("fault-tightened"),
        FAULT_CONSTRAINT_HELD("fault-constraint-held"),
        FAULT_COOLDOWN("fault-cooldown"),
        RECOVERY_BLOCKED("recovery-blocked"),
        RECOVERY_PENDING("recovery-pending"),
        RECOVERED("recovered");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum Reason {
        SOFTWARE_DECODER("software-decoder"),
        THERMAL_MODERATE_HOLD("thermal-moderate-hold"),
        THERMAL_SEVERE("thermal-severe"),
        THERMAL_CRITICAL("thermal-critical"),
        POWER_SAVE_HOLD("power-save-hold"),
        METERED_REMOTE("metered-remote"),
        ROAMING("roaming"),
        DATA_SAVER("data-saver");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum ResourceMode {
        ADAPTIVE_VARIANTS("adaptive-variants"),
        SEGMENTED_SINGLE("segmented-single"),
        PROGRESSIVE_SINGLE("progressive-single"),
        OTHER_SINGLE("other-single"),
        UNKNOWN("unknown");

        private final String label;

        ResourceMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    enum CostScope {
        LOCAL("local"),
        REMOTE("remote"),
        UNKNOWN("unknown");

        private final String label;

        CostScope(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Limit(int width, int height, int frameRate, int maxVideoBitrate) {

        Limit {
            width = Math.max(1, width);
            height = Math.max(1, height);
            frameRate = Math.max(1, frameRate);
            maxVideoBitrate = Math.max(1, maxVideoBitrate);
        }

        static Limit unbounded() {
            return new Limit(Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        static Limit minimum() {
            return new Limit(854, 480, 30, 1_500_000);
        }

        Limit intersect(Limit other) {
            Limit value = other == null ? unbounded() : other;
            return new Limit(
                    Math.min(width, value.width),
                    Math.min(height, value.height),
                    Math.min(frameRate, value.frameRate),
                    Math.min(maxVideoBitrate, value.maxVideoBitrate));
        }

        String label() {
            return width + "x" + height + "@" + frameRate + "/" + maxVideoBitrate;
        }
    }

    record SelectedTrack(int width, int height, int frameRate, int bitrate) {

        SelectedTrack {
            width = Math.max(0, width);
            height = Math.max(0, height);
            frameRate = Math.max(0, frameRate);
            bitrate = Math.max(0, bitrate);
        }

        static SelectedTrack unknown() {
            return new SelectedTrack(0, 0, 0, 0);
        }

        boolean hasSize() {
            return width > 0 && height > 0;
        }
    }

    record Environment(
            Limit cap,
            int observedSources,
            int restrictiveSources,
            int releasableSources,
            boolean recoveryBlocked,
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.NetworkCost networkCost,
            PlaybackAutoContext.DataSaverState dataSaver,
            PlaybackAutoContext.DecodeMode decodeMode,
            CostScope costScope,
            List<Reason> reasons) {

        Environment {
            cap = cap == null ? Limit.unbounded() : cap;
            restrictiveSources &= observedSources;
            releasableSources &= observedSources;
            releasableSources &= ~restrictiveSources;
            thermal = thermal == null ? PlaybackAutoContext.ThermalState.UNKNOWN : thermal;
            power = power == null ? PlaybackAutoContext.PowerState.UNKNOWN : power;
            networkCost = networkCost == null ? PlaybackAutoContext.NetworkCost.UNKNOWN : networkCost;
            dataSaver = dataSaver == null ? PlaybackAutoContext.DataSaverState.UNKNOWN : dataSaver;
            decodeMode = decodeMode == null ? PlaybackAutoContext.DecodeMode.UNKNOWN : decodeMode;
            costScope = costScope == null ? CostScope.UNKNOWN : costScope;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        static Environment unknown() {
            return new Environment(
                    Limit.unbounded(),
                    0,
                    0,
                    0,
                    false,
                    PlaybackAutoContext.ThermalState.UNKNOWN,
                    PlaybackAutoContext.PowerState.UNKNOWN,
                    PlaybackAutoContext.NetworkCost.UNKNOWN,
                    PlaybackAutoContext.DataSaverState.UNKNOWN,
                    PlaybackAutoContext.DecodeMode.UNKNOWN,
                    CostScope.UNKNOWN,
                    List.of());
        }

        String reasonLabel() {
            if (reasons.isEmpty()) return "baseline";
            StringBuilder builder = new StringBuilder();
            for (Reason reason : reasons) {
                if (builder.length() > 0) builder.append(',');
                builder.append(reason.label());
            }
            return builder.toString();
        }
    }

    record Input(
            Limit baseline,
            List<Limit> tiers,
            Environment environment,
            boolean playbackStable,
            SelectedTrack selectedTrack,
            ResourceMode resourceMode,
            long elapsedRealtimeMs) {

        Input {
            baseline = baseline == null ? Limit.minimum() : baseline;
            tiers = tiers == null || tiers.isEmpty() ? List.of(baseline, Limit.minimum())
                    : List.copyOf(tiers);
            environment = environment == null ? Environment.unknown() : environment;
            selectedTrack = selectedTrack == null ? SelectedTrack.unknown() : selectedTrack;
            resourceMode = resourceMode == null ? ResourceMode.UNKNOWN : resourceMode;
            elapsedRealtimeMs = Math.max(0, elapsedRealtimeMs);
        }

        static Input minimum() {
            Limit minimum = Limit.minimum();
            return new Input(minimum, List.of(minimum), Environment.unknown(),
                    false, SelectedTrack.unknown(), ResourceMode.UNKNOWN, 0);
        }
    }

    record State(
            Limit effective,
            Limit faultLimit,
            long lastFaultAtMs,
            long lastFaultStepAtMs,
            long recoverySinceMs,
            int holdSources,
            Fault lastFault) {

        State {
            effective = effective == null ? Limit.minimum() : effective;
            lastFault = lastFault == null ? Fault.NONE : lastFault;
        }
    }

    record Decision(
            State state,
            Limit rawTarget,
            Action action,
            Fault faultSignal,
            boolean faultStepped,
            boolean changed,
            boolean faultActive,
            long recoveryRemainingMs,
            long reevaluateAfterMs,
            Environment environment,
            ResourceMode resourceMode) {
    }
}
