package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

/** Pure classification of memory, thermal, power and network-cost pressure for MPV auto mode. */
public final class MpvResourcePressurePolicy {

    private MpvResourcePressurePolicy() {
    }

    public static Assessment assess(
            PlaybackAutoContext context,
            boolean automatic,
            boolean mpv,
            boolean performancePriority,
            long nowElapsedMs) {
        PlaybackAutoContext current = context == null
                ? PlaybackAutoContext.empty() : context;
        PlaybackAutoContext.DeviceFacts device = current.device();
        long now = Math.max(0, nowElapsedMs);

        PlaybackAutoContext.Fact<PlaybackAutoContext.MemoryPressure> memoryFact =
                device.memoryPressure();
        PlaybackAutoContext.Fact<PlaybackAutoContext.MemorySnapshot> memorySnapshotFact =
                device.memorySnapshot();
        PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermalFact =
                device.thermalState();
        PlaybackAutoContext.Fact<PlaybackAutoContext.PowerState> powerFact =
                device.powerState();
        PlaybackAutoContext.Fact<PlaybackAutoContext.NetworkCost> networkCostFact =
                device.networkCost();
        PlaybackAutoContext.Fact<PlaybackAutoContext.NetworkSnapshot> networkSnapshotFact =
                device.networkSnapshot();

        boolean memoryUsable = usable(memoryFact, now);
        boolean memorySnapshotUsable = usable(memorySnapshotFact, now)
                && memorySnapshotFact.value().hasEvidence();
        boolean thermalUsable = usable(thermalFact, now);
        boolean powerUsable = usable(powerFact, now);
        boolean networkCostUsable = usable(networkCostFact, now);
        boolean networkSnapshotUsable = usable(networkSnapshotFact, now);

        PlaybackAutoContext.MemoryPressure memory = memoryUsable
                ? memoryFact.value() : PlaybackAutoContext.MemoryPressure.UNKNOWN;
        PlaybackAutoContext.MemorySnapshot memorySnapshot = memorySnapshotUsable
                ? memorySnapshotFact.value() : PlaybackAutoContext.MemorySnapshot.unknown();
        PlaybackAutoContext.ThermalState thermal = thermalUsable
                ? thermalFact.value() : PlaybackAutoContext.ThermalState.UNKNOWN;
        PlaybackAutoContext.PowerState power = powerUsable
                ? powerFact.value() : PlaybackAutoContext.PowerState.UNKNOWN;
        PlaybackAutoContext.NetworkCost networkCost = networkCostUsable
                ? networkCostFact.value() : PlaybackAutoContext.NetworkCost.UNKNOWN;
        PlaybackAutoContext.NetworkSnapshot network = networkSnapshotUsable
                ? networkSnapshotFact.value() : PlaybackAutoContext.NetworkSnapshot.unknown();
        long latestResourceSampleAtElapsedMs = latestSample(
                memoryFact,
                memorySnapshotFact,
                thermalFact,
                powerFact,
                networkCostFact,
                networkSnapshotFact);

        Base base = new Base(
                current.revision(),
                current.publishedAtElapsedMs(),
                memoryUsable,
                memory,
                memorySnapshotUsable,
                thermalUsable,
                thermal,
                powerUsable,
                power,
                networkCostUsable,
                networkCost,
                networkSnapshotUsable,
                network);
        if (!automatic || !mpv) {
            return base.result(false, Level.INACTIVE, Reason.NOT_AUTOMATIC_MPV,
                    current.publishedAtElapsedMs());
        }
        if (!performancePriority) {
            return base.result(false, Level.INACTIVE, Reason.CONFIG_PRIORITY,
                    current.publishedAtElapsedMs());
        }

        if (memoryUsable && memory == PlaybackAutoContext.MemoryPressure.CRITICAL) {
            return base.result(true, Level.HARD, Reason.CRITICAL_MEMORY,
                    memoryFact.sampledAtElapsedMs());
        }
        if (memorySnapshotUsable && criticalMemory(memorySnapshot)) {
            return base.result(true, Level.HARD, Reason.CRITICAL_MEMORY_SNAPSHOT,
                    memorySnapshotFact.sampledAtElapsedMs());
        }
        if (thermalUsable && thermal == PlaybackAutoContext.ThermalState.CRITICAL) {
            return base.result(true, Level.HARD, Reason.THERMAL_CRITICAL,
                    thermalFact.sampledAtElapsedMs());
        }
        if (thermalUsable && thermal == PlaybackAutoContext.ThermalState.SEVERE) {
            return base.result(true, Level.HARD, Reason.THERMAL_SEVERE,
                    thermalFact.sampledAtElapsedMs());
        }

        if (memoryUsable && memory == PlaybackAutoContext.MemoryPressure.MODERATE) {
            return base.result(true, Level.CONSTRAINED, Reason.MODERATE_MEMORY,
                    memoryFact.sampledAtElapsedMs());
        }
        if (thermalUsable && thermal == PlaybackAutoContext.ThermalState.MODERATE) {
            return base.result(true, Level.CONSTRAINED, Reason.THERMAL_MODERATE,
                    thermalFact.sampledAtElapsedMs());
        }
        if (powerUsable && power == PlaybackAutoContext.PowerState.POWER_SAVE) {
            return base.result(true, Level.CONSTRAINED, Reason.POWER_SAVE,
                    powerFact.sampledAtElapsedMs());
        }
        if (!memoryUsable || memory != PlaybackAutoContext.MemoryPressure.NORMAL
                || !memorySnapshotUsable) {
            return base.result(true, Level.UNKNOWN, Reason.MEMORY_UNKNOWN,
                    latestResourceSampleAtElapsedMs);
        }
        if (!thermalUsable || thermal != PlaybackAutoContext.ThermalState.NOMINAL) {
            return base.result(true, Level.UNKNOWN, Reason.THERMAL_UNKNOWN,
                    latestResourceSampleAtElapsedMs);
        }
        if (!powerUsable || power != PlaybackAutoContext.PowerState.NORMAL) {
            return base.result(true, Level.UNKNOWN, Reason.POWER_UNKNOWN,
                    latestResourceSampleAtElapsedMs);
        }
        // Network type/cost is handled exclusively by the pause preload setting.
        // It is not memory/thermal/power pressure and must not stop disk preload.
        return base.result(true, Level.NORMAL, Reason.NORMAL,
                latestResourceSampleAtElapsedMs);
    }

    private static boolean criticalMemory(PlaybackAutoContext.MemorySnapshot snapshot) {
        if (snapshot == null) return false;
        if (Boolean.TRUE.equals(snapshot.systemLowMemory())) return true;
        Long available = snapshot.systemAvailableBytes();
        Long threshold = snapshot.systemThresholdBytes();
        if (available != null && threshold != null && available <= threshold) return true;
        return Long.valueOf(0).equals(snapshot.javaHeapHeadroomBytes());
    }

    private static boolean usable(PlaybackAutoContext.Fact<?> fact, long nowElapsedMs) {
        return fact != null && fact.isUsable(nowElapsedMs);
    }

    private static long latestSample(
            PlaybackAutoContext.Fact<?> first,
            boolean firstUsable,
            PlaybackAutoContext.Fact<?> second,
            boolean secondUsable) {
        return Math.max(firstUsable ? first.sampledAtElapsedMs() : -1,
                secondUsable ? second.sampledAtElapsedMs() : -1);
    }

    private static long latestSample(PlaybackAutoContext.Fact<?>... facts) {
        long latest = -1;
        if (facts == null) return latest;
        for (PlaybackAutoContext.Fact<?> fact : facts) {
            if (fact != null) latest = Math.max(latest, fact.sampledAtElapsedMs());
        }
        return latest;
    }

    public enum Level {
        INACTIVE("inactive"),
        UNKNOWN("unknown"),
        NORMAL("normal"),
        CONSTRAINED("constrained"),
        HARD("hard");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Reason {
        NOT_AUTOMATIC_MPV("not-automatic-mpv"),
        CONFIG_PRIORITY("mpv-conf-priority"),
        CRITICAL_MEMORY("critical-memory"),
        CRITICAL_MEMORY_SNAPSHOT("critical-memory-snapshot"),
        THERMAL_SEVERE("thermal-severe"),
        THERMAL_CRITICAL("thermal-critical"),
        MODERATE_MEMORY("moderate-memory"),
        THERMAL_MODERATE("thermal-moderate"),
        POWER_SAVE("power-save"),
        NETWORK_UNAVAILABLE("network-unavailable"),
        NETWORK_UNVALIDATED("network-unvalidated"),
        METERED("metered"),
        ROAMING("roaming"),
        DATA_SAVER("data-saver"),
        DATA_SAVER_WHITELISTED("data-saver-whitelisted"),
        MEMORY_UNKNOWN("memory-unknown"),
        THERMAL_UNKNOWN("thermal-unknown"),
        POWER_UNKNOWN("power-unknown"),
        NETWORK_UNKNOWN("network-unknown"),
        NORMAL("normal");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Assessment(
            boolean active,
            Level level,
            Reason reason,
            long contextRevision,
            long contextPublishedAtElapsedMs,
            long sampledAtElapsedMs,
            boolean memoryUsable,
            PlaybackAutoContext.MemoryPressure memoryPressure,
            boolean memorySnapshotUsable,
            boolean thermalUsable,
            PlaybackAutoContext.ThermalState thermal,
            boolean powerUsable,
            PlaybackAutoContext.PowerState power,
            boolean networkCostUsable,
            PlaybackAutoContext.NetworkCost networkCost,
            boolean networkSnapshotUsable,
            PlaybackAutoContext.NetworkSnapshot networkSnapshot) {

        public Assessment {
            level = level == null ? Level.UNKNOWN : level;
            reason = reason == null ? Reason.NETWORK_UNKNOWN : reason;
            contextRevision = Math.max(0, contextRevision);
            contextPublishedAtElapsedMs = contextPublishedAtElapsedMs < 0
                    ? -1 : contextPublishedAtElapsedMs;
            sampledAtElapsedMs = sampledAtElapsedMs < 0 ? -1 : sampledAtElapsedMs;
            memoryPressure = memoryPressure == null
                    ? PlaybackAutoContext.MemoryPressure.UNKNOWN : memoryPressure;
            thermal = thermal == null
                    ? PlaybackAutoContext.ThermalState.UNKNOWN : thermal;
            power = power == null
                    ? PlaybackAutoContext.PowerState.UNKNOWN : power;
            networkCost = networkCost == null
                    ? PlaybackAutoContext.NetworkCost.UNKNOWN : networkCost;
            networkSnapshot = networkSnapshot == null
                    ? PlaybackAutoContext.NetworkSnapshot.unknown() : networkSnapshot;
        }

        public static Assessment inactive() {
            return new Assessment(false, Level.INACTIVE, Reason.NOT_AUTOMATIC_MPV,
                    0, -1, -1,
                    false, PlaybackAutoContext.MemoryPressure.UNKNOWN, false,
                    false, PlaybackAutoContext.ThermalState.UNKNOWN,
                    false, PlaybackAutoContext.PowerState.UNKNOWN,
                    false, PlaybackAutoContext.NetworkCost.UNKNOWN,
                    false, PlaybackAutoContext.NetworkSnapshot.unknown());
        }
    }

    private record Base(
            long contextRevision,
            long publishedAtElapsedMs,
            boolean memoryUsable,
            PlaybackAutoContext.MemoryPressure memoryPressure,
            boolean memorySnapshotUsable,
            boolean thermalUsable,
            PlaybackAutoContext.ThermalState thermal,
            boolean powerUsable,
            PlaybackAutoContext.PowerState power,
            boolean networkCostUsable,
            PlaybackAutoContext.NetworkCost networkCost,
            boolean networkSnapshotUsable,
            PlaybackAutoContext.NetworkSnapshot networkSnapshot) {

        private Assessment result(
                boolean active,
                Level level,
                Reason reason,
                long sampledAtElapsedMs) {
            return new Assessment(
                    active,
                    level,
                    reason,
                    contextRevision,
                    publishedAtElapsedMs,
                    sampledAtElapsedMs,
                    memoryUsable,
                    memoryPressure,
                    memorySnapshotUsable,
                    thermalUsable,
                    thermal,
                    powerUsable,
                    power,
                    networkCostUsable,
                    networkCost,
                    networkSnapshotUsable,
                    networkSnapshot);
        }
    }
}
