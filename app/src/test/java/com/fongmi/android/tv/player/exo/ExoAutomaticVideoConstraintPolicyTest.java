package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.PlaybackRouteCapabilities;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoAutomaticVideoConstraintPolicyTest {

    private static final ExoAutomaticVideoConstraintPolicy.Limit BASELINE =
            new ExoAutomaticVideoConstraintPolicy.Limit(3840, 2160, 60, 20_000_000);
    private static final List<ExoAutomaticVideoConstraintPolicy.Limit> TIERS = List.of(
            BASELINE,
            new ExoAutomaticVideoConstraintPolicy.Limit(2560, 1440, 60, 12_000_000),
            new ExoAutomaticVideoConstraintPolicy.Limit(1920, 1080, 60, 8_000_000),
            new ExoAutomaticVideoConstraintPolicy.Limit(1280, 720, 30, 4_000_000),
            new ExoAutomaticVideoConstraintPolicy.Limit(854, 480, 30, 1_500_000));

    @Test
    public void environmentCapsDoNotUseBandwidthOrRebufferSignals() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision first = evaluate(
                state,
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision later = evaluate(
                first.state(),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.PROGRESSIVE_SINGLE,
                31_000);

        assertEquals(BASELINE, first.state().effective());
        assertEquals(BASELINE, later.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.UNCHANGED, first.action());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.UNCHANGED, later.action());
    }

    @Test
    public void severeThermalTightensImmediatelyAndNominalRecoversWithHysteresis() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision severe = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision pending = evaluate(
                severe.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        0,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);
        ExoAutomaticVideoConstraintPolicy.Decision recovered = evaluate(
                pending.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        0,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                32_001);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                severe.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_PENDING, pending.action());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERED, recovered.action());
        assertEquals(BASELINE, recovered.state().effective());
    }

    @Test
    public void unknownThermalFactsCannotReleaseAnExistingThermalLimit() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision severe = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision unknown = evaluate(
                severe.state(),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                40_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                unknown.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_BLOCKED, unknown.action());
    }

    @Test
    public void moderateThermalBlocksRecoveryButDoesNotInventANewDowngrade() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision severe = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision moderate = evaluate(
                severe.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        0,
                        true),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                moderate.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_BLOCKED, moderate.action());
    }

    @Test
    public void expiredModerateThermalCannotReleaseTheEarlierSevereLimit() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision severe = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision moderate = evaluate(
                severe.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        0,
                        true),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);
        ExoAutomaticVideoConstraintPolicy.Decision expired = evaluate(
                moderate.state(),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                40_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                expired.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_BLOCKED,
                expired.action());
    }

    @Test
    public void recoveryEvidenceMustRemainFreshForTheWholeStableWindow() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision severe = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision pending = evaluate(
                severe.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL,
                        0,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);
        ExoAutomaticVideoConstraintPolicy.Decision expired = evaluate(
                pending.state(),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                32_001);

        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_PENDING,
                pending.action());
        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT,
                expired.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_BLOCKED,
                expired.action());
    }

    @Test
    public void anExpiredRedundantRestrictionCannotBeForgotten() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision combined = evaluate(
                state,
                environment(
                        ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT.intersect(
                                ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL
                                | ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL
                                | ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision dataOnly = evaluate(
                combined.state(),
                environment(ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);
        ExoAutomaticVideoConstraintPolicy.Decision expired = evaluate(
                dataOnly.state(),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                40_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_SEVERE_LIMIT.intersect(
                        ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT),
                expired.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_BLOCKED,
                expired.action());
    }

    @Test
    public void meteredRemoteIsCappedButLocalPathBypassesCarrierCost() {
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Decision remote = evaluate(
                state,
                environment(ExoAutomaticVideoConstraintPolicy.METERED_REMOTE_LIMIT,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);
        ExoAutomaticVideoConstraintPolicy.Decision local = evaluate(
                remote.state(),
                environment(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                        0,
                        false),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                2_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.METERED_REMOTE_LIMIT,
                remote.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_PENDING, local.action());
        assertEquals(ExoAutomaticVideoConstraintPolicy.METERED_REMOTE_LIMIT,
                local.state().effective());
    }

    @Test
    public void softwareDecoderAndDataSaverCapsIntersect() {
        ExoAutomaticVideoConstraintPolicy.Environment environment = environment(
                ExoAutomaticVideoConstraintPolicy.SOFTWARE_DECODER_LIMIT.intersect(
                        ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT),
                ExoAutomaticVideoConstraintPolicy.SOURCE_DECODER
                        | ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                ExoAutomaticVideoConstraintPolicy.SOURCE_DECODER
                        | ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST,
                false);
        ExoAutomaticVideoConstraintPolicy.Decision decision = evaluate(
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE),
                environment,
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                1_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.SOFTWARE_DECODER_LIMIT
                        .intersect(ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT),
                decision.state().effective());
    }

    @Test
    public void decodeFaultStepsDownAndDoesNotReactToRepeatedEventsDuringCooldown() {
        ExoAutomaticVideoConstraintPolicy.SelectedTrack selected =
                new ExoAutomaticVideoConstraintPolicy.SelectedTrack(3840, 2160, 60, 18_000_000);
        ExoAutomaticVideoConstraintPolicy.Input input = input(
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(), true,
                selected, ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS, 1_000);
        ExoAutomaticVideoConstraintPolicy.Decision first =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        ExoAutomaticVideoConstraintPolicy.initial(BASELINE), input,
                        ExoAutomaticVideoConstraintPolicy.Fault.CODEC_ERROR);
        ExoAutomaticVideoConstraintPolicy.Decision repeated =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        first.state(), input(first.state(), selected, 5_000),
                        ExoAutomaticVideoConstraintPolicy.Fault.CODEC_ERROR);

        assertEquals(TIERS.get(1), first.state().effective());
        assertEquals(ExoAutomaticVideoConstraintPolicy.FAULT_HOLD_MS,
                first.reevaluateAfterMs());
        assertEquals(TIERS.get(1), repeated.state().effective());
        assertFalse(repeated.faultStepped());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.FAULT_COOLDOWN,
                repeated.action());
    }

    @Test
    public void faultLimitRecoversOnlyAfterHoldAndStableWindow() {
        ExoAutomaticVideoConstraintPolicy.SelectedTrack selected =
                new ExoAutomaticVideoConstraintPolicy.SelectedTrack(3840, 2160, 60, 18_000_000);
        ExoAutomaticVideoConstraintPolicy.Decision fault =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        ExoAutomaticVideoConstraintPolicy.initial(BASELINE),
                        input(ExoAutomaticVideoConstraintPolicy.Environment.unknown(), true,
                                selected, ExoAutomaticVideoConstraintPolicy.ResourceMode.PROGRESSIVE_SINGLE, 1_000),
                        ExoAutomaticVideoConstraintPolicy.Fault.DROPPED_FRAMES);
        ExoAutomaticVideoConstraintPolicy.Decision pending =
                ExoAutomaticVideoConstraintPolicy.evaluate(
                        fault.state(),
                        input(fault.state(), selected, 32_000));
        ExoAutomaticVideoConstraintPolicy.Decision recovered =
                ExoAutomaticVideoConstraintPolicy.evaluate(
                        pending.state(),
                        input(pending.state(), selected, 62_001));

        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERY_PENDING, pending.action());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.RECOVERED, recovered.action());
        assertEquals(BASELINE, recovered.state().effective());
    }

    @Test
    public void throughputFaultStepsDownTheLadderAndRecoversLikeOtherFaults() {
        // 起播固定选最高画质后原生 ABR 不再兜底吞吐，带宽不足/重缓冲要靠 THROUGHPUT 证据降档。
        ExoAutomaticVideoConstraintPolicy.SelectedTrack selected =
                new ExoAutomaticVideoConstraintPolicy.SelectedTrack(3840, 2160, 60, 18_000_000);
        ExoAutomaticVideoConstraintPolicy.Input input = input(
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(), true,
                selected, ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS, 1_000);

        ExoAutomaticVideoConstraintPolicy.Decision first =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        ExoAutomaticVideoConstraintPolicy.initial(BASELINE), input,
                        ExoAutomaticVideoConstraintPolicy.Fault.THROUGHPUT);
        // 带宽回调很频繁，冷却期内的重复证据不能继续往下踩。
        ExoAutomaticVideoConstraintPolicy.Decision repeated =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        first.state(), input(first.state(), selected, 5_000),
                        ExoAutomaticVideoConstraintPolicy.Fault.THROUGHPUT);
        // 冷却结束后仍然吞吐不足则继续降一档。
        ExoAutomaticVideoConstraintPolicy.Decision stepped =
                ExoAutomaticVideoConstraintPolicy.onFault(
                        repeated.state(),
                        input(repeated.state(),
                                new ExoAutomaticVideoConstraintPolicy.SelectedTrack(2560, 1440, 60, 11_000_000),
                                17_000),
                        ExoAutomaticVideoConstraintPolicy.Fault.THROUGHPUT);

        assertEquals(TIERS.get(1), first.state().effective());
        assertTrue(first.faultStepped());
        assertTrue(first.faultActive());
        assertEquals(TIERS.get(1), repeated.state().effective());
        assertFalse(repeated.faultStepped());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.FAULT_COOLDOWN, repeated.action());
        assertEquals(TIERS.get(2), stepped.state().effective());
        assertTrue(stepped.faultStepped());
    }

    @Test
    public void throughputFaultNeverFallsBelowTheLowestTier() {
        // 反复吞吐不足时必须停在最低档，不能越降越低把画面降没了。
        ExoAutomaticVideoConstraintPolicy.State state =
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE);
        ExoAutomaticVideoConstraintPolicy.Limit lowest = TIERS.get(TIERS.size() - 1);
        long now = 1_000;
        for (int i = 0; i < TIERS.size() + 3; i++) {
            ExoAutomaticVideoConstraintPolicy.Limit effective = state.effective();
            ExoAutomaticVideoConstraintPolicy.Decision decision =
                    ExoAutomaticVideoConstraintPolicy.onFault(
                            state,
                            input(state,
                                    new ExoAutomaticVideoConstraintPolicy.SelectedTrack(
                                            effective.width(), effective.height(),
                                            effective.frameRate(), effective.maxVideoBitrate()),
                                    now),
                            ExoAutomaticVideoConstraintPolicy.Fault.THROUGHPUT);
            state = decision.state();
            now += ExoAutomaticVideoConstraintPolicy.FAULT_STEP_COOLDOWN_MS + 1;
        }

        assertEquals(lowest, state.effective());
    }

    @Test
    public void progressiveModeNeverReportsABandwidthDowngradeAction() {
        ExoAutomaticVideoConstraintPolicy.Decision decision = evaluate(
                ExoAutomaticVideoConstraintPolicy.initial(BASELINE),
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.PROGRESSIVE_SINGLE,
                1_000);

        assertEquals(ExoAutomaticVideoConstraintPolicy.ResourceMode.PROGRESSIVE_SINGLE,
                decision.resourceMode());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Action.UNCHANGED, decision.action());
    }

    @Test
    public void contextMappingAppliesMeteredCapOnlyToConfirmedRemotePath() {
        long now = 1_000;
        PlaybackAutoContext.DeviceFacts device = deviceFacts(
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.NetworkCost.METERED,
                new PlaybackAutoContext.NetworkSnapshot(
                        true, true, true, false,
                        PlaybackAutoContext.NetworkTransport.CELLULAR,
                        PlaybackAutoContext.DataSaverState.DISABLED),
                now);
        ExoAutomaticVideoConstraintPolicy.Environment remote =
                ExoAutomaticVideoConstraintPolicy.environment(
                        context(device, path(PlaybackAutoContext.PathKind.REMOTE,
                                PlaybackAutoContext.PathKind.REMOTE, now),
                                PlaybackAutoContext.DecodeMode.HARDWARE, now),
                        now);
        ExoAutomaticVideoConstraintPolicy.Environment local =
                ExoAutomaticVideoConstraintPolicy.environment(
                        context(device, path(PlaybackAutoContext.PathKind.LOCAL,
                                PlaybackAutoContext.PathKind.LOCAL, now),
                                PlaybackAutoContext.DecodeMode.HARDWARE, now),
                        now);

        assertEquals(ExoAutomaticVideoConstraintPolicy.METERED_REMOTE_LIMIT, remote.cap());
        assertEquals(ExoAutomaticVideoConstraintPolicy.CostScope.REMOTE, remote.costScope());
        assertEquals(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(), local.cap());
        assertEquals(ExoAutomaticVideoConstraintPolicy.CostScope.LOCAL, local.costScope());
    }

    @Test
    public void contextMappingCombinesCriticalThermalDataSaverAndSoftwareDecode() {
        long now = 2_000;
        PlaybackAutoContext.DeviceFacts device = deviceFacts(
                PlaybackAutoContext.ThermalState.CRITICAL,
                PlaybackAutoContext.PowerState.POWER_SAVE,
                PlaybackAutoContext.NetworkCost.METERED,
                new PlaybackAutoContext.NetworkSnapshot(
                        true, true, true, false,
                        PlaybackAutoContext.NetworkTransport.CELLULAR,
                        PlaybackAutoContext.DataSaverState.ENABLED),
                now);
        ExoAutomaticVideoConstraintPolicy.Environment environment =
                ExoAutomaticVideoConstraintPolicy.environment(
                        context(device, path(PlaybackAutoContext.PathKind.REMOTE,
                                PlaybackAutoContext.PathKind.REMOTE, now),
                                PlaybackAutoContext.DecodeMode.SOFTWARE, now),
                        now);

        assertEquals(ExoAutomaticVideoConstraintPolicy.THERMAL_CRITICAL_LIMIT
                        .intersect(ExoAutomaticVideoConstraintPolicy.SOFTWARE_DECODER_LIMIT)
                        .intersect(ExoAutomaticVideoConstraintPolicy.EXPENSIVE_NETWORK_LIMIT),
                environment.cap());
        assertTrue(environment.recoveryBlocked());
        assertEquals(ExoAutomaticVideoConstraintPolicy.SOURCE_THERMAL
                        | ExoAutomaticVideoConstraintPolicy.SOURCE_DATA_COST
                        | ExoAutomaticVideoConstraintPolicy.SOURCE_DECODER,
                environment.restrictiveSources());
    }

    @Test
    public void expiredSystemFactsDoNotInventSafeOrRestrictiveCaps() {
        long sampledAt = 1_000;
        PlaybackAutoContext.DeviceFacts device = deviceFacts(
                PlaybackAutoContext.ThermalState.CRITICAL,
                PlaybackAutoContext.PowerState.POWER_SAVE,
                PlaybackAutoContext.NetworkCost.ROAMING,
                new PlaybackAutoContext.NetworkSnapshot(
                        true, true, true, true,
                        PlaybackAutoContext.NetworkTransport.CELLULAR,
                        PlaybackAutoContext.DataSaverState.ENABLED),
                sampledAt);
        long expiredAt = sampledAt + 121_000;
        ExoAutomaticVideoConstraintPolicy.Environment environment =
                ExoAutomaticVideoConstraintPolicy.environment(
                        context(device, path(PlaybackAutoContext.PathKind.REMOTE,
                                PlaybackAutoContext.PathKind.REMOTE, sampledAt),
                                PlaybackAutoContext.DecodeMode.HARDWARE, sampledAt),
                        expiredAt);

        assertEquals(ExoAutomaticVideoConstraintPolicy.Limit.unbounded(), environment.cap());
        assertEquals(ExoAutomaticVideoConstraintPolicy.SOURCE_DECODER,
                environment.observedSources());
        assertEquals(0, environment.restrictiveSources());
        assertEquals(ExoAutomaticVideoConstraintPolicy.SOURCE_DECODER,
                environment.releasableSources());
        assertFalse(environment.recoveryBlocked());
    }

    @Test
    public void environmentReasonLabelJoinsMultipleReasons() {
        ExoAutomaticVideoConstraintPolicy.Environment environment =
                new ExoAutomaticVideoConstraintPolicy.Environment(
                        ExoAutomaticVideoConstraintPolicy.Limit.unbounded(),
                        0,
                        0,
                        0,
                        false,
                        PlaybackAutoContext.ThermalState.UNKNOWN,
                        PlaybackAutoContext.PowerState.UNKNOWN,
                        PlaybackAutoContext.NetworkCost.UNKNOWN,
                        PlaybackAutoContext.DataSaverState.UNKNOWN,
                        PlaybackAutoContext.DecodeMode.UNKNOWN,
                        ExoAutomaticVideoConstraintPolicy.CostScope.UNKNOWN,
                        List.of(
                                ExoAutomaticVideoConstraintPolicy.Reason.THERMAL_CRITICAL,
                                ExoAutomaticVideoConstraintPolicy.Reason.SOFTWARE_DECODER));

        assertEquals("thermal-critical,software-decoder", environment.reasonLabel());
    }

    private static ExoAutomaticVideoConstraintPolicy.Decision evaluate(
            ExoAutomaticVideoConstraintPolicy.State state,
            ExoAutomaticVideoConstraintPolicy.Environment environment,
            boolean stable,
            ExoAutomaticVideoConstraintPolicy.ResourceMode mode,
            long now) {
        return ExoAutomaticVideoConstraintPolicy.evaluate(
                state,
                input(environment, stable,
                        ExoAutomaticVideoConstraintPolicy.SelectedTrack.unknown(), mode, now));
    }

    private static ExoAutomaticVideoConstraintPolicy.Input input(
            ExoAutomaticVideoConstraintPolicy.Environment environment,
            boolean stable,
            ExoAutomaticVideoConstraintPolicy.SelectedTrack selected,
            ExoAutomaticVideoConstraintPolicy.ResourceMode mode,
            long now) {
        return new ExoAutomaticVideoConstraintPolicy.Input(
                BASELINE, TIERS, environment, stable, selected, mode, now);
    }

    private static ExoAutomaticVideoConstraintPolicy.Input input(
            ExoAutomaticVideoConstraintPolicy.State state,
            ExoAutomaticVideoConstraintPolicy.SelectedTrack selected,
            long now) {
        return new ExoAutomaticVideoConstraintPolicy.Input(
                BASELINE,
                TIERS,
                ExoAutomaticVideoConstraintPolicy.Environment.unknown(),
                true,
                selected,
                ExoAutomaticVideoConstraintPolicy.ResourceMode.ADAPTIVE_VARIANTS,
                now);
    }

    private static ExoAutomaticVideoConstraintPolicy.Environment environment(
            ExoAutomaticVideoConstraintPolicy.Limit cap,
            int observedSources,
            int restrictiveSources,
            boolean recoveryBlocked) {
        return new ExoAutomaticVideoConstraintPolicy.Environment(
                cap,
                observedSources,
                restrictiveSources,
                recoveryBlocked ? 0 : observedSources & ~restrictiveSources,
                recoveryBlocked,
                PlaybackAutoContext.ThermalState.UNKNOWN,
                PlaybackAutoContext.PowerState.UNKNOWN,
                PlaybackAutoContext.NetworkCost.UNKNOWN,
                PlaybackAutoContext.DataSaverState.UNKNOWN,
                PlaybackAutoContext.DecodeMode.UNKNOWN,
                ExoAutomaticVideoConstraintPolicy.CostScope.UNKNOWN,
                List.of());
    }

    private static PlaybackAutoContext context(
            PlaybackAutoContext.DeviceFacts device,
            PlaybackAutoContext.PathFacts path,
            PlaybackAutoContext.DecodeMode decodeMode,
            long now) {
        return new PlaybackAutoContext(
                new PlaybackAutoContext.SessionToken("e04-test", 1),
                0,
                0,
                now,
                PlaybackAutoContext.Fact.forSession(
                        PlaybackAutoContext.Kernel.EXO,
                        PlaybackAutoContext.ValueSource.PLAYER_MANAGER,
                        PlaybackAutoContext.Confidence.HIGH,
                        now),
                PlaybackAutoContext.Fact.forSession(
                        decodeMode,
                        PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST,
                        PlaybackAutoContext.Confidence.HIGH,
                        now),
                device,
                PlaybackAutoContext.ResourceFacts.unknown(),
                path,
                PlaybackAutoContext.RuntimeFacts.unknown(),
                PlaybackAutoContext.MediaFacts.unknown());
    }

    private static PlaybackAutoContext.DeviceFacts deviceFacts(
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.NetworkCost cost,
            PlaybackAutoContext.NetworkSnapshot network,
            long now) {
        long ttl = 120_000;
        return new PlaybackAutoContext.DeviceFacts(
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.MemoryPressure.UNKNOWN),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.MemorySnapshot.unknown()),
                PlaybackAutoContext.Fact.unknown(-1L),
                PlaybackAutoContext.Fact.withTtl(
                        thermal,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH,
                        now,
                        ttl),
                PlaybackAutoContext.Fact.withTtl(
                        power,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH,
                        now,
                        ttl),
                PlaybackAutoContext.Fact.withTtl(
                        cost,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH,
                        now,
                        ttl),
                PlaybackAutoContext.Fact.withTtl(
                        network,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH,
                        now,
                        ttl));
    }

    private static PlaybackAutoContext.PathFacts path(
            PlaybackAutoContext.PathKind playerPath,
            PlaybackAutoContext.PathKind upstreamPath,
            long now) {
        return new PlaybackAutoContext.PathFacts(
                PlaybackAutoContext.Fact.unknown(PlaybackRoute.OTHER),
                PlaybackAutoContext.Fact.unknown(PlaybackRoute.Owner.UNKNOWN),
                PlaybackAutoContext.Fact.unknown(false),
                PlaybackAutoContext.Fact.unknown(
                        PlaybackRouteCapabilities.ObservedLeg.SOURCE_SPECIFIC),
                PlaybackAutoContext.Fact.unknown(
                        PlaybackRouteCapabilities.UpstreamVisibility.UNKNOWN),
                PlaybackAutoContext.Fact.unknown(
                        PlaybackRouteCapabilities.ControlScope.NONE),
                PlaybackAutoContext.Fact.forSession(
                        playerPath,
                        PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER,
                        PlaybackAutoContext.Confidence.HIGH,
                        now),
                PlaybackAutoContext.Fact.forSession(
                        upstreamPath,
                        PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER,
                        PlaybackAutoContext.Confidence.HIGH,
                        now),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.UpstreamState.UNKNOWN));
    }
}
