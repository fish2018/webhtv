package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackAutoContextStore;
import com.fongmi.android.tv.player.PlaybackRoute;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoPreloadPolicyTest {

    private static final PlaybackAutoContext.SessionToken SESSION =
            new PlaybackAutoContext.SessionToken("p-preload-1", 1);
    private static final long MEDIA_BITRATE = 10_000_000L;

    @Test
    public void unknownEvidenceUsesConservativeSingleThreadRange() {
        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(
                inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        AutoPreloadPolicy.ThroughputEvidence.unknown(),
                        AutoPreloadPolicy.SystemEvidence.unknown(),
                        ForwardBufferTrend.Snapshot.unknown(), false, false));

        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
        assertEquals("degraded", decision.mode());
        assertTrue(decision.enabled());
    }

    @Test
    public void staleSessionImmediatelyPauses() {
        AutoPreloadPolicy.Inputs input = inputs(
                0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(0, 30, 30, 30, 200, false),
                safeSystem(), stableTrend(0, 20_000), false, false);
        input = new AutoPreloadPolicy.Inputs(
                input.session(), false, input.route(), input.bufferedMs(),
                input.mediaBitrateBitsPerSecond(), input.rebufferCount(), input.loading(),
                input.trend(), input.throughput(), input.system(), false, false, 0);

        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(input);

        assertFalse(decision.enabled());
        assertEquals("session-mismatch", decision.reason());
    }

    @Test
    public void trustedStableEvidencePromotesOneThenTwoThreadsSlowly() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();

        assertEquals(1, policy.evaluate(safeInputs(0)).threads());
        AutoPreloadPolicy.Decision normal = policy.evaluate(safeInputs(20_000));
        AutoPreloadPolicy.Decision fast = policy.evaluate(safeInputs(30_000));

        assertEquals(1, normal.threads());
        assertEquals(20_000, normal.durationMs());
        assertEquals(2, fast.threads());
        assertEquals(30_000, fast.durationMs());
    }

    @Test
    public void weakEffectiveThroughputImmediatelyPauses() {
        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(
                inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        trustedThroughput(0, 11, 11, 15, 200, false),
                        safeSystem(), stableTrend(0, 20_000), false, false));

        assertFalse(decision.enabled());
        assertEquals("throughput-deficit", decision.reason());
    }

    @Test
    public void shortWindowCollapseImmediatelyPauses() {
        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(
                inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        trustedThroughput(0, 25, 9, 30, 200, false),
                        safeSystem(), stableTrend(0, 20_000), false, false));

        assertFalse(decision.enabled());
        assertEquals("short-window-deficit", decision.reason());
    }

    @Test
    public void shortWindowDeclineCancelsFastBudgetBeforePlaybackRisk() {
        AutoPreloadPolicy policy = fastPolicy();

        AutoPreloadPolicy.Decision decision = policy.evaluate(
                inputs(35_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        trustedThroughput(35_000, 30, 20, 30, 200, false),
                        safeSystem(), stableTrend(35_000, 20_000), false, true));

        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
        assertEquals("short-window-decline", decision.reason());
    }

    @Test
    public void dangerousTimeToEmptyImmediatelyPauses() {
        ForwardBufferTrend.Snapshot draining = trend(0, -1_000, 10_000,
                ForwardBufferTrend.Confidence.MEDIUM);

        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(
                inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 10_000,
                        trustedThroughput(0, 30, 30, 30, 200, false),
                        safeSystem(), draining, false, false));

        assertFalse(decision.enabled());
        assertEquals("time-to-empty", decision.reason());
    }

    @Test
    public void unavailableAndUnvalidatedNetworksPause() {
        AutoPreloadPolicy.Decision unavailable = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(false, true, false, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.WIFI)));
        AutoPreloadPolicy.Decision unvalidated = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, false, false, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.WIFI)));

        assertEquals("network-unavailable", unavailable.reason());
        assertEquals("network-unvalidated", unvalidated.reason());
        assertFalse(unavailable.enabled());
        assertFalse(unvalidated.enabled());
    }

    @Test
    public void meteredAndRoamingDegradeWhileDataSaverPauses() {
        AutoPreloadPolicy.Decision metered = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, true, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.CELLULAR)));
        AutoPreloadPolicy.Decision roaming = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, false, true,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.CELLULAR)));
        AutoPreloadPolicy.Decision dataSaver = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, false, false,
                        PlaybackAutoContext.DataSaverState.ENABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.WIFI)));

        assertEquals("metered", metered.reason());
        assertEquals("roaming", roaming.reason());
        assertEquals("data-saver", dataSaver.reason());
        assertTrue(metered.enabled());
        assertTrue(roaming.enabled());
        assertEquals(1, metered.threads());
        assertEquals(1, roaming.threads());
        assertEquals(10_000, metered.durationMs());
        assertEquals(10_000, roaming.durationMs());
        assertFalse(dataSaver.enabled());
    }

    @Test
    public void powerSaverAndSevereThermalPause() {
        AutoPreloadPolicy.Decision power = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, false, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.POWER_SAVE,
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.NetworkTransport.WIFI)));
        AutoPreloadPolicy.Decision thermal = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, false, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.SEVERE,
                        PlaybackAutoContext.NetworkTransport.WIFI)));

        assertEquals("power-save", power.reason());
        assertEquals("thermal-pressure", thermal.reason());
        assertFalse(power.enabled());
        assertFalse(thermal.enabled());
    }

    @Test
    public void moderateThermalUsesShortSingleThreadRange() {
        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(
                withSystem(0, system(true, true, false, false,
                        PlaybackAutoContext.DataSaverState.DISABLED,
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ThermalState.MODERATE,
                        PlaybackAutoContext.NetworkTransport.WIFI)));

        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
        assertEquals("thermal-moderate", decision.reason());
    }

    @Test
    public void memoryPressureImmediatelyPauses() {
        AutoPreloadPolicy.Inputs safe = safeInputs(0);
        AutoPreloadPolicy.Inputs pressured = new AutoPreloadPolicy.Inputs(
                safe.session(), safe.sessionMatches(), safe.route(), safe.bufferedMs(),
                safe.mediaBitrateBitsPerSecond(), safe.rebufferCount(), safe.loading(),
                safe.trend(), safe.throughput(), safe.system(), true, false, 0);

        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(pressured);

        assertFalse(decision.enabled());
        assertEquals("memory-pressure", decision.reason());
    }

    @Test
    public void vpnAndLimitedAppServicePathNeverReachFastMode() {
        AutoPreloadPolicy vpn = new AutoPreloadPolicy();
        AutoPreloadPolicy.SystemEvidence vpnSystem = system(
                true, true, false, false,
                PlaybackAutoContext.DataSaverState.DISABLED,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.NetworkTransport.VPN);
        vpn.evaluate(inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(0, 40, 40, 40, 100, false),
                vpnSystem, stableTrend(0, 20_000), false, false));
        AutoPreloadPolicy.Decision vpnLater = vpn.evaluate(inputs(
                60_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(60_000, 40, 40, 40, 100, false),
                vpnSystem, stableTrend(60_000, 20_000), false, false));

        AutoPreloadPolicy app = new AutoPreloadPolicy();
        AutoPreloadPolicy.ThroughputEvidence limited = throughput(
                0, 50, 50, 50, 100, false,
                ExoThroughputPathPolicy.Trust.LIMITED);
        app.evaluate(inputs(0, PlaybackRoute.APP_LOCAL_SERVICE, 20_000,
                limited, safeSystem(), stableTrend(0, 20_000), false, false));
        AutoPreloadPolicy.Decision appLater = app.evaluate(inputs(
                60_000, PlaybackRoute.APP_LOCAL_SERVICE, 20_000,
                throughput(60_000, 50, 50, 50, 100, false,
                        ExoThroughputPathPolicy.Trust.LIMITED),
                safeSystem(), stableTrend(60_000, 20_000), false, false));

        assertEquals(1, vpnLater.threads());
        assertEquals(10_000, vpnLater.durationMs());
        assertEquals(1, appLater.threads());
        assertEquals(10_000, appLater.durationMs());
    }

    @Test
    public void staleThroughputAndHighPredictionErrorStayConservative() {
        AutoPreloadPolicy.ThroughputEvidence stale = trustedThroughput(
                0, 30, 30, 30, 200, false);
        AutoPreloadPolicy.Decision staleDecision = new AutoPreloadPolicy().evaluate(
                inputs(65_001, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        new AutoPreloadPolicy.ThroughputEvidence(
                                false,
                                stale.effectiveBitsPerSecond(),
                                stale.shortBitsPerSecond(),
                                stale.longBitsPerSecond(),
                                stale.longSampleCount(),
                                stale.longWindowMs(),
                                stale.predictionErrorPermille(),
                                stale.confidence(),
                                stale.pathTrust(),
                                stale.pathConfidence(),
                                false,
                                0),
                        safeSystem(), stableTrend(65_001, 20_000), false, false));
        AutoPreloadPolicy.Decision inaccurate = new AutoPreloadPolicy().evaluate(
                inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                        trustedThroughput(0, 30, 30, 30, 1_000, false),
                        safeSystem(), stableTrend(0, 20_000), false, false));

        assertEquals("throughput-evidence-unknown", staleDecision.reason());
        assertEquals("prediction-error", inaccurate.reason());
        assertEquals(10_000, staleDecision.durationMs());
        assertEquals(10_000, inaccurate.durationMs());
    }

    @Test
    public void contentionBlocksPromotionButOwnFastTaskDoesNotOscillate() {
        AutoPreloadPolicy blocked = new AutoPreloadPolicy();
        blocked.evaluate(inputs(0, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(0, 30, 30, 30, 200, true),
                safeSystem(), stableTrend(0, 20_000), false, false));
        AutoPreloadPolicy.Decision blockedLater = blocked.evaluate(inputs(
                60_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(60_000, 30, 30, 30, 200, true),
                safeSystem(), stableTrend(60_000, 20_000), false, false));

        AutoPreloadPolicy fast = fastPolicy();
        AutoPreloadPolicy.Decision held = fast.evaluate(inputs(
                35_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 20_000,
                trustedThroughput(35_000, 30, 30, 30, 200, true),
                safeSystem(), stableTrend(35_000, 20_000), false, true));

        assertEquals(1, blockedLater.threads());
        assertEquals(10_000, blockedLater.durationMs());
        assertEquals(2, held.threads());
        assertEquals("fast", held.mode());
    }

    @Test
    public void disruptionPausesThenRecoversWithFastCooldown() {
        AutoPreloadPolicy policy = fastPolicy();
        policy.disrupt(31_000, AutoPreloadPolicy.Reason.NETWORK_CHANGED);

        assertFalse(policy.evaluate(safeInputs(40_999)).enabled());
        assertEquals(1, policy.evaluate(safeInputs(41_000)).threads());
        assertEquals(1, policy.evaluate(safeInputs(71_000)).threads());
        assertEquals(2, policy.evaluate(safeInputs(91_000)).threads());
    }

    @Test
    public void rebufferIncreaseImmediatelyPauses() {
        AutoPreloadPolicy.Inputs safe = safeInputs(0);
        AutoPreloadPolicy.Inputs rebuffered = new AutoPreloadPolicy.Inputs(
                safe.session(), safe.sessionMatches(), safe.route(), safe.bufferedMs(),
                safe.mediaBitrateBitsPerSecond(), 1, safe.loading(), safe.trend(),
                safe.throughput(), safe.system(), false, false, 0);

        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(rebuffered);

        assertFalse(decision.enabled());
        assertEquals("rebuffer", decision.reason());
    }

    @Test
    public void unknownAppProxyMediaKeepsPreloadPausedUntilForegroundReserveRecovers() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        AutoPreloadPolicy.Inputs low = new AutoPreloadPolicy.Inputs(
                SESSION,
                true,
                PlaybackRoute.APP_LOCAL_SERVICE,
                ExoNetworkGuardBufferPolicy.LOOPBACK_FLOOR_MS - 1,
                0,
                1,
                true,
                stableTrend(0, ExoNetworkGuardBufferPolicy.LOOPBACK_FLOOR_MS - 1),
                AutoPreloadPolicy.ThroughputEvidence.unknown(),
                safeSystem(),
                false,
                false,
                0);
        AutoPreloadPolicy.Inputs recovered = new AutoPreloadPolicy.Inputs(
                SESSION,
                true,
                PlaybackRoute.APP_LOCAL_SERVICE,
                ExoNetworkGuardBufferPolicy.LOOPBACK_FLOOR_MS,
                0,
                1,
                true,
                stableTrend(15_000, ExoNetworkGuardBufferPolicy.LOOPBACK_FLOOR_MS),
                AutoPreloadPolicy.ThroughputEvidence.unknown(),
                safeSystem(),
                false,
                false,
                15_000);

        AutoPreloadPolicy.Decision paused = policy.evaluate(low);
        AutoPreloadPolicy.Decision resumed = policy.evaluate(recovered);

        assertFalse(paused.enabled());
        assertEquals("foreground-recovery", paused.reason());
        assertTrue(resumed.enabled());
        assertEquals(1, resumed.threads());
    }

    @Test
    public void fastModeFallsBackWhenFrontBufferMarginShrinks() {
        AutoPreloadPolicy policy = fastPolicy();

        AutoPreloadPolicy.Decision decision = policy.evaluate(inputs(
                35_000, PlaybackRoute.DIRECT_REMOTE_HTTP, 11_000,
                trustedThroughput(35_000, 30, 30, 30, 200, false),
                safeSystem(), stableTrend(35_000, 11_000), false, true));

        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
        assertEquals("front-buffer-margin", decision.reason());
    }

    @Test
    public void externalLoopbackNeverExceedsOneThreadAndNeedsStableBufferForLongRange() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        AutoPreloadPolicy.Inputs initial = inputs(
                0, PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, 20_000,
                AutoPreloadPolicy.ThroughputEvidence.unknown(), safeSystem(),
                stableTrend(0, 20_000), false, false);
        AutoPreloadPolicy.Inputs stable = inputs(
                20_000, PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, 20_000,
                AutoPreloadPolicy.ThroughputEvidence.unknown(), safeSystem(),
                stableTrend(20_000, 20_000), false, false);

        AutoPreloadPolicy.Decision first = policy.evaluate(initial);
        AutoPreloadPolicy.Decision later = policy.evaluate(stable);

        assertEquals(1, first.threads());
        assertEquals(10_000, first.durationMs());
        assertEquals(1, later.threads());
        assertEquals(40_000, later.durationMs());
    }

    @Test
    public void expiredSystemFactsCannotPromoteFastMode() {
        PlaybackAutoContextStore store = new PlaybackAutoContextStore();
        PlaybackAutoContext.SessionToken session = store.beginSession("p-expired-1", 0);
        PlaybackAutoContext.DeviceFacts device = new PlaybackAutoContext.DeviceFacts(
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.MemoryPressure.UNKNOWN),
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.MemorySnapshot.unknown()),
                PlaybackAutoContext.Fact.unknown(-1L),
                PlaybackAutoContext.Fact.withTtl(
                        PlaybackAutoContext.ThermalState.NOMINAL,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, 0, 10),
                PlaybackAutoContext.Fact.withTtl(
                        PlaybackAutoContext.PowerState.NORMAL,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, 0, 10),
                PlaybackAutoContext.Fact.withTtl(
                        PlaybackAutoContext.NetworkCost.UNMETERED,
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, 0, 10),
                PlaybackAutoContext.Fact.withTtl(
                        new PlaybackAutoContext.NetworkSnapshot(
                                true, true, false, false,
                                PlaybackAutoContext.NetworkTransport.WIFI,
                                PlaybackAutoContext.DataSaverState.DISABLED),
                        PlaybackAutoContext.ValueSource.SYSTEM_API,
                        PlaybackAutoContext.Confidence.HIGH, 0, 10));
        assertTrue(store.publishDeviceFacts(session, device, 0));
        ExoThroughputEstimator.Snapshot throughput = snapshot(
                session, 20, 30, 30, 30, 200, false);

        AutoPreloadPolicy.Inputs input = AutoPreloadPolicy.Inputs.capture(
                20,
                session,
                PlaybackRoute.DIRECT_REMOTE_HTTP,
                20_000,
                MEDIA_BITRATE,
                0,
                false,
                stableTrend(20, 20_000),
                throughput,
                store.snapshot(),
                false,
                false);
        AutoPreloadPolicy.Decision decision = new AutoPreloadPolicy().evaluate(input);

        assertEquals(1, decision.threads());
        assertEquals(10_000, decision.durationMs());
        assertEquals("system-evidence-unknown", decision.reason());
    }

    @Test
    public void restrictiveDecisionComparisonUsesThreadsThenRange() {
        AutoPreloadPolicy.Decision fast = new AutoPreloadPolicy.Decision(
                2, 30_000, "fast", "stable-fast");
        AutoPreloadPolicy.Decision normal = new AutoPreloadPolicy.Decision(
                1, 20_000, "normal", "stable-normal");
        AutoPreloadPolicy.Decision shortRange = new AutoPreloadPolicy.Decision(
                1, 10_000, "degraded", "buffer-declining");
        AutoPreloadPolicy.Decision paused = new AutoPreloadPolicy.Decision(
                0, 0, "paused", "metered");

        assertTrue(normal.moreRestrictiveThan(fast));
        assertTrue(shortRange.moreRestrictiveThan(normal));
        assertTrue(paused.moreRestrictiveThan(shortRange));
        assertFalse(fast.moreRestrictiveThan(normal));
        assertFalse(normal.moreRestrictiveThan(null));
    }

    private static AutoPreloadPolicy fastPolicy() {
        AutoPreloadPolicy policy = new AutoPreloadPolicy();
        policy.evaluate(safeInputs(0));
        assertEquals(2, policy.evaluate(safeInputs(30_000)).threads());
        return policy;
    }

    private static AutoPreloadPolicy.Inputs safeInputs(long nowMs) {
        return inputs(
                nowMs,
                PlaybackRoute.DIRECT_REMOTE_HTTP,
                20_000,
                trustedThroughput(nowMs, 30, 30, 30, 200, false),
                safeSystem(),
                stableTrend(nowMs, 20_000),
                false,
                false);
    }

    private static AutoPreloadPolicy.Inputs withSystem(
            long nowMs,
            AutoPreloadPolicy.SystemEvidence system) {
        return inputs(
                nowMs,
                PlaybackRoute.DIRECT_REMOTE_HTTP,
                20_000,
                trustedThroughput(nowMs, 30, 30, 30, 200, false),
                system,
                stableTrend(nowMs, 20_000),
                false,
                false);
    }

    private static AutoPreloadPolicy.Inputs inputs(
            long nowMs,
            PlaybackRoute route,
            long bufferedMs,
            AutoPreloadPolicy.ThroughputEvidence throughput,
            AutoPreloadPolicy.SystemEvidence system,
            ForwardBufferTrend.Snapshot trend,
            boolean memoryPaused,
            boolean preloadActive) {
        return new AutoPreloadPolicy.Inputs(
                SESSION,
                true,
                route,
                bufferedMs,
                MEDIA_BITRATE,
                0,
                false,
                trend,
                throughput,
                system,
                memoryPaused,
                preloadActive,
                nowMs);
    }

    private static AutoPreloadPolicy.ThroughputEvidence trustedThroughput(
            long observedAtMs,
            long effectiveMbps,
            long shortMbps,
            long longMbps,
            int predictionErrorPermille,
            boolean contended) {
        return throughput(
                observedAtMs,
                effectiveMbps,
                shortMbps,
                longMbps,
                predictionErrorPermille,
                contended,
                ExoThroughputPathPolicy.Trust.TRUSTED);
    }

    private static AutoPreloadPolicy.ThroughputEvidence throughput(
            long observedAtMs,
            long effectiveMbps,
            long shortMbps,
            long longMbps,
            int predictionErrorPermille,
            boolean contended,
            ExoThroughputPathPolicy.Trust trust) {
        return new AutoPreloadPolicy.ThroughputEvidence(
                true,
                effectiveMbps * 1_000_000L,
                shortMbps * 1_000_000L,
                longMbps * 1_000_000L,
                4,
                15_000,
                predictionErrorPermille,
                PlaybackAutoContext.Confidence.MEDIUM,
                trust,
                trust == ExoThroughputPathPolicy.Trust.TRUSTED
                        ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.LOW,
                contended,
                observedAtMs);
    }

    private static AutoPreloadPolicy.SystemEvidence safeSystem() {
        return system(
                true,
                true,
                false,
                false,
                PlaybackAutoContext.DataSaverState.DISABLED,
                PlaybackAutoContext.PowerState.NORMAL,
                PlaybackAutoContext.ThermalState.NOMINAL,
                PlaybackAutoContext.NetworkTransport.WIFI);
    }

    private static AutoPreloadPolicy.SystemEvidence system(
            boolean available,
            boolean validated,
            boolean metered,
            boolean roaming,
            PlaybackAutoContext.DataSaverState dataSaver,
            PlaybackAutoContext.PowerState power,
            PlaybackAutoContext.ThermalState thermal,
            PlaybackAutoContext.NetworkTransport transport) {
        PlaybackAutoContext.NetworkCost cost = roaming
                ? PlaybackAutoContext.NetworkCost.ROAMING
                : metered
                ? PlaybackAutoContext.NetworkCost.METERED
                : PlaybackAutoContext.NetworkCost.UNMETERED;
        return new AutoPreloadPolicy.SystemEvidence(
                true,
                available,
                validated,
                metered,
                roaming,
                transport,
                dataSaver,
                true,
                cost,
                true,
                power,
                true,
                thermal);
    }

    private static ForwardBufferTrend.Snapshot stableTrend(
            long sampledAtMs,
            long bufferedMs) {
        return trend(
                sampledAtMs,
                0,
                bufferedMs,
                ForwardBufferTrend.Confidence.MEDIUM);
    }

    private static ForwardBufferTrend.Snapshot trend(
            long sampledAtMs,
            long slopeMsPerSecond,
            long bufferedMs,
            ForwardBufferTrend.Confidence confidence) {
        return new ForwardBufferTrend.Snapshot(
                slopeMsPerSecond,
                slopeMsPerSecond,
                slopeMsPerSecond,
                20_000,
                5,
                confidence,
                bufferedMs,
                sampledAtMs);
    }

    private static ExoThroughputEstimator.Snapshot snapshot(
            PlaybackAutoContext.SessionToken session,
            long observedAtMs,
            long effectiveMbps,
            long shortMbps,
            long longMbps,
            int predictionErrorPermille,
            boolean contended) {
        return new ExoThroughputEstimator.Snapshot(
                session,
                effectiveMbps * 1_000_000L,
                shortMbps * 1_000_000L,
                longMbps * 1_000_000L,
                effectiveMbps * 1_000_000L,
                4,
                4,
                4,
                12_000,
                15_000,
                predictionErrorPermille,
                PlaybackAutoContext.Confidence.MEDIUM,
                ExoThroughputPathPolicy.Trust.TRUSTED,
                PlaybackAutoContext.Confidence.MEDIUM,
                contended,
                effectiveMbps * 1_000_000L,
                ExoThroughputEstimator.Action.HOLD,
                ExoThroughputEstimator.Reason.STABLE_HOLD,
                observedAtMs);
    }
}
