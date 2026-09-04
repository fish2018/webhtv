package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvResourcePressurePolicyTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    public void criticalMemoryAndSevereThermalAreHardPressure() {
        MpvResourcePressurePolicy.Assessment memory = assess(context(
                PlaybackAutoContext.MemoryPressure.CRITICAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 1, 10), 10);
        MpvResourcePressurePolicy.Assessment thermal = assess(context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.SEVERE,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 2, 20), 20);

        assertEquals(MpvResourcePressurePolicy.Level.HARD, memory.level());
        assertEquals(MpvResourcePressurePolicy.Reason.CRITICAL_MEMORY, memory.reason());
        assertEquals(MpvResourcePressurePolicy.Level.HARD, thermal.level());
        assertEquals(MpvResourcePressurePolicy.Reason.THERMAL_SEVERE, thermal.reason());
    }

    @Test
    public void criticalThermalIsHardPressure() {
        MpvResourcePressurePolicy.Assessment result = assess(context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.CRITICAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 13, 130), 130);

        assertEquals(MpvResourcePressurePolicy.Level.HARD, result.level());
        assertEquals(MpvResourcePressurePolicy.Reason.THERMAL_CRITICAL,
                result.reason());
    }

    @Test
    public void criticalSnapshotIsHardEvenWhenPressureFactIsNormal() {
        PlaybackAutoContext.MemorySnapshot low = new PlaybackAutoContext.MemorySnapshot(
                PlaybackAutoContext.MemoryTrigger.PERIODIC,
                128 * MIB, 512 * MIB, 384 * MIB, false,
                4L * 1024 * MIB, 128 * MIB, 256 * MIB,
                true, null, 100, 64 * MIB);

        MpvResourcePressurePolicy.Assessment result = assess(context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                low,
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 3, 30), 30);

        assertEquals(MpvResourcePressurePolicy.Level.HARD, result.level());
        assertEquals(MpvResourcePressurePolicy.Reason.CRITICAL_MEMORY_SNAPSHOT,
                result.reason());
    }

    @Test
    public void moderateMemoryThermalAndPowerSaveOnlyConstrain() {
        assertConstrained(
                context(PlaybackAutoContext.MemoryPressure.MODERATE,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        normalNetwork(), 4, 40),
                MpvResourcePressurePolicy.Reason.MODERATE_MEMORY, 40);
        assertConstrained(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.MODERATE,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        normalNetwork(), 5, 50),
                MpvResourcePressurePolicy.Reason.THERMAL_MODERATE, 50);
        assertConstrained(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.POWER_SAVE,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        normalNetwork(), 6, 60),
                MpvResourcePressurePolicy.Reason.POWER_SAVE, 60);
    }

    @Test
    public void networkCostDoesNotDisableDiskPreload() {
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.METERED,
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, true, true, false,
                                PlaybackAutoContext.NetworkTransport.CELLULAR,
                                PlaybackAutoContext.DataSaverState.DISABLED),
                        7, 70),
                70);
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.ROAMING,
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, true, true, true,
                                PlaybackAutoContext.NetworkTransport.CELLULAR,
                                PlaybackAutoContext.DataSaverState.DISABLED),
                        8, 80),
                80);
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, true, false, false,
                                PlaybackAutoContext.NetworkTransport.WIFI,
                                PlaybackAutoContext.DataSaverState.ENABLED),
                        9, 90),
                90);
    }

    @Test
    public void networkValidationDoesNotDisableDiskPreload() {
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        new PlaybackAutoContext.NetworkSnapshot(
                                false, false, false, false,
                                PlaybackAutoContext.NetworkTransport.WIFI,
                                PlaybackAutoContext.DataSaverState.DISABLED),
                        14, 140),
                140);
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, false, false, false,
                                PlaybackAutoContext.NetworkTransport.WIFI,
                                PlaybackAutoContext.DataSaverState.DISABLED),
                        15, 150),
                150);
        assertNormal(
                context(PlaybackAutoContext.MemoryPressure.NORMAL,
                        normalMemory(), PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, true, false, false,
                                PlaybackAutoContext.NetworkTransport.WIFI,
                                PlaybackAutoContext.DataSaverState.WHITELISTED),
                        16, 160),
                160);
    }

    @Test
    public void staleOrMissingFactsAreUnknownAndNeverHard() {
        PlaybackAutoContext current = context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 10, 0, 100);

        MpvResourcePressurePolicy.Assessment expired = assess(current, 100);

        assertEquals(MpvResourcePressurePolicy.Level.UNKNOWN, expired.level());
        assertFalse(expired.level() == MpvResourcePressurePolicy.Level.HARD);
        assertEquals(0, expired.sampledAtElapsedMs());
    }

    @Test
    public void fullyFreshSafeFactsAreNormal() {
        MpvResourcePressurePolicy.Assessment result = assess(context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 11, 110), 110);

        assertTrue(result.active());
        assertEquals(MpvResourcePressurePolicy.Level.NORMAL, result.level());
        assertEquals(MpvResourcePressurePolicy.Reason.NORMAL, result.reason());
    }

    @Test
    public void contextRepublishDoesNotInventNewResourceSampleTime() {
        PlaybackAutoContext current = context(
                PlaybackAutoContext.MemoryPressure.NORMAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.UNMETERED,
                normalNetwork(), 17, 170, 10_000, 120_000);

        MpvResourcePressurePolicy.Assessment result = assess(current, 10_000);

        assertEquals(MpvResourcePressurePolicy.Level.NORMAL, result.level());
        assertEquals(170, result.sampledAtElapsedMs());
        assertEquals(10_000, result.contextPublishedAtElapsedMs());
    }

    @Test
    public void nonAutomaticAndConfigPriorityAreInactive() {
        PlaybackAutoContext current = context(
                PlaybackAutoContext.MemoryPressure.CRITICAL,
                normalMemory(),
                PlaybackAutoContext.ThermalState.CRITICAL,
                PlaybackAutoContext.PowerState.POWER_SAVE,
                PlaybackAutoContext.NetworkCost.ROAMING,
                normalNetwork(), 12, 120);

        MpvResourcePressurePolicy.Assessment manual =
                MpvResourcePressurePolicy.assess(current, false, true, true, 120);
        MpvResourcePressurePolicy.Assessment config =
                MpvResourcePressurePolicy.assess(current, true, true, false, 120);

        assertFalse(manual.active());
        assertEquals(MpvResourcePressurePolicy.Reason.NOT_AUTOMATIC_MPV,
                manual.reason());
        assertFalse(config.active());
        assertEquals(MpvResourcePressurePolicy.Reason.CONFIG_PRIORITY,
                config.reason());
    }

    private static void assertConstrained(
            PlaybackAutoContext context,
            MpvResourcePressurePolicy.Reason reason,
            long now) {
        MpvResourcePressurePolicy.Assessment result = assess(context, now);
        assertEquals(MpvResourcePressurePolicy.Level.CONSTRAINED, result.level());
        assertEquals(reason, result.reason());
    }

    private static void assertNormal(PlaybackAutoContext context, long now) {
        MpvResourcePressurePolicy.Assessment result = assess(context, now);
        assertEquals(MpvResourcePressurePolicy.Level.NORMAL, result.level());
        assertEquals(MpvResourcePressurePolicy.Reason.NORMAL, result.reason());
    }

    private static MpvResourcePressurePolicy.Assessment assess(
            PlaybackAutoContext context,
            long now) {
        return MpvResourcePressurePolicy.assess(context, true, true, true, now);
    }

    private static PlaybackAutoContext context(
            PlaybackAutoContext.MemoryPressure memory,
            PlaybackAutoContext.MemorySnapshot memorySnapshot,
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.NetworkCost networkCost,
            PlaybackAutoContext.NetworkSnapshot network,
            long revision,
            long sampledAt) {
        return context(memory, memorySnapshot, thermal, power, networkCost,
                network, revision, sampledAt, 120_000);
    }

    private static PlaybackAutoContext context(
            PlaybackAutoContext.MemoryPressure memory,
            PlaybackAutoContext.MemorySnapshot memorySnapshot,
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.NetworkCost networkCost,
            PlaybackAutoContext.NetworkSnapshot network,
            long revision,
            long sampledAt,
            long ttlMs) {
        return context(memory, memorySnapshot, thermal, power, networkCost,
                network, revision, sampledAt, sampledAt, ttlMs);
    }

    private static PlaybackAutoContext context(
            PlaybackAutoContext.MemoryPressure memory,
            PlaybackAutoContext.MemorySnapshot memorySnapshot,
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.NetworkCost networkCost,
            PlaybackAutoContext.NetworkSnapshot network,
            long revision,
            long sampledAt,
            long publishedAt,
            long ttlMs) {
        PlaybackAutoContext.DeviceFacts device = new PlaybackAutoContext.DeviceFacts(
                PlaybackAutoContext.Fact.withTtl(
                        memory, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs),
                PlaybackAutoContext.Fact.withTtl(
                        memorySnapshot, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs),
                PlaybackAutoContext.Fact.unknown(-1L),
                PlaybackAutoContext.Fact.withTtl(
                        thermal, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs),
                PlaybackAutoContext.Fact.withTtl(
                        power, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs),
                PlaybackAutoContext.Fact.withTtl(
                        networkCost, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs),
                PlaybackAutoContext.Fact.withTtl(
                        network, PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, sampledAt, ttlMs));
        return new PlaybackAutoContext(
                token(revision),
                0,
                revision,
                publishedAt,
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.Kernel.UNKNOWN),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.DecodeMode.UNKNOWN),
                device,
                PlaybackAutoContext.ResourceFacts.unknown(),
                PlaybackAutoContext.PathFacts.unknown(),
                PlaybackAutoContext.RuntimeFacts.unknown(),
                PlaybackAutoContext.MediaFacts.unknown());
    }

    private static PlaybackAutoContext.MemorySnapshot normalMemory() {
        return new PlaybackAutoContext.MemorySnapshot(
                PlaybackAutoContext.MemoryTrigger.PERIODIC,
                128 * MIB, 512 * MIB, 384 * MIB, false,
                4L * 1024 * MIB, 2L * 1024 * MIB, 256 * MIB,
                false, null, 100, 64 * MIB);
    }

    private static PlaybackAutoContext.NetworkSnapshot normalNetwork() {
        return new PlaybackAutoContext.NetworkSnapshot(
                true, true, false, false,
                PlaybackAutoContext.NetworkTransport.WIFI,
                PlaybackAutoContext.DataSaverState.DISABLED);
    }

    private static PlaybackAutoContext.SessionToken token(long generation) {
        return new PlaybackAutoContext.SessionToken(
                "p-policy-" + Long.toString(generation + 1, 36),
                Math.max(1, generation));
    }
}
