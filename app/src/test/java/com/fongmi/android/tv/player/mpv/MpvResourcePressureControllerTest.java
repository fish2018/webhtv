package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvResourcePressureControllerTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    public void hardPressureImmediatelyCapsCachesAndCancelsPreloadOnce() {
        MpvResourcePressureController controller = controller("hard", 1);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        MpvResourcePressureController.Decision first = controller.evaluate(
                token, token, hard(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 0);
        MpvResourcePressureController.Decision duplicate = controller.evaluate(
                token, token, hard(1, 0),
                MpvResourcePressureController.Trigger.RUNTIME,
                96 * MIB, 32 * MIB, 5_000);

        assertEquals(24 * MIB, first.forwardCeilingBytes());
        assertEquals(0, first.backCeilingBytes());
        assertFalse(first.expansionAllowed());
        assertFalse(first.preloadAllowed());
        assertTrue(first.preloadCancellationRequested());
        assertFalse(duplicate.preloadCancellationRequested());
        assertEquals(1, controller.snapshot().cancellations());
    }

    @Test
    public void constrainedAndUnknownFreezeCurrentTargetsWithoutShrinking() {
        MpvResourcePressureController constrained = controller("cost", 2);
        PlaybackAutoContext.SessionToken constrainedToken =
                constrained.snapshot().session();
        MpvResourcePressureController.Decision cost = constrained.evaluate(
                constrainedToken, constrainedToken, constrained(1, 10),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 10);

        MpvResourcePressureController unknown = controller("unknown", 3);
        PlaybackAutoContext.SessionToken unknownToken = unknown.snapshot().session();
        MpvResourcePressureController.Decision missing = unknown.evaluate(
                unknownToken, unknownToken, unknown(1, 20),
                MpvResourcePressureController.Trigger.RUNTIME,
                64 * MIB, 16 * MIB, 20);

        assertEquals(96 * MIB, cost.forwardCeilingBytes());
        assertEquals(32 * MIB, cost.backCeilingBytes());
        assertFalse(cost.expansionAllowed());
        assertTrue(cost.preloadAllowed());
        assertEquals(64 * MIB, missing.forwardCeilingBytes());
        assertEquals(16 * MIB, missing.backCeilingBytes());
        assertFalse(missing.expansionAllowed());
        assertFalse(missing.preloadAllowed());
        assertEquals(MpvResourcePressureController.Reason.UNKNOWN_HOLD,
                missing.reason());
    }

    @Test
    public void costConstraintDoesNotBypassHardPressureRecovery() {
        MpvResourcePressureController controller = controller("hard-cost", 11);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, hard(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 0);

        MpvResourcePressureController.Decision metered = controller.evaluate(
                token, token, constrained(2, 10),
                MpvResourcePressureController.Trigger.SYSTEM,
                24 * MIB, 0, 10);

        assertFalse(metered.preloadAllowed());
        assertEquals(MpvResourcePressureController.RecoveryClass.HARD,
                metered.recoveryClass());
    }

    @Test
    public void hardRecoveryNeedsFreshNormalSamplesCooldownAndMovesOneTier() {
        MpvResourcePressureController controller = controller("recover", 4);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, hard(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 0);

        MpvResourcePressureController.Decision firstNormal = controller.evaluate(
                token, token, normal(2, 30_000),
                MpvResourcePressureController.Trigger.MEMORY,
                24 * MIB, 0, 30_000);
        MpvResourcePressureController.Decision secondNormal = controller.evaluate(
                token, token, normal(3, 60_000),
                MpvResourcePressureController.Trigger.SYSTEM,
                24 * MIB, 0, 60_000);

        assertFalse(firstNormal.expansionAllowed());
        assertEquals(1, firstNormal.normalSamples());
        assertEquals(MpvResourcePressureController.Action.RECOVERY_STEP,
                secondNormal.action());
        assertEquals(48 * MIB, secondNormal.forwardCeilingBytes());
        assertEquals(16 * MIB, secondNormal.backCeilingBytes());
        assertTrue(secondNormal.expansionAllowed());
        assertTrue(secondNormal.preloadAllowed());

        MpvResourcePressureController.Decision early = controller.evaluate(
                token, token, normal(3, 60_000),
                MpvResourcePressureController.Trigger.RUNTIME,
                48 * MIB, 16 * MIB, 74_999);
        MpvResourcePressureController.Decision next = controller.evaluate(
                token, token, normal(3, 60_000),
                MpvResourcePressureController.Trigger.RUNTIME,
                48 * MIB, 16 * MIB, 75_000);

        assertEquals(48 * MIB, early.forwardCeilingBytes());
        assertEquals(64 * MIB, next.forwardCeilingBytes());
        assertEquals(32 * MIB, next.backCeilingBytes());
    }

    @Test
    public void duplicateNormalRevisionDoesNotCountAsFreshSample() {
        MpvResourcePressureController controller = controller("duplicate", 5);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, constrained(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                64 * MIB, 16 * MIB, 0);

        controller.evaluate(token, token, normal(2, 30_000),
                MpvResourcePressureController.Trigger.SYSTEM,
                64 * MIB, 16 * MIB, 30_000);
        controller.evaluate(token, token, normal(2, 30_000),
                MpvResourcePressureController.Trigger.RUNTIME,
                64 * MIB, 16 * MIB, 60_000);

        assertEquals(1, controller.snapshot().normalSamples());
        assertFalse(controller.snapshot().expansionAllowed());
    }

    @Test
    public void newerContextRevisionWithoutNewResourceSampleDoesNotCount() {
        MpvResourcePressureController controller = controller("republish", 9);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, constrained(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                64 * MIB, 16 * MIB, 0);

        controller.evaluate(token, token, normal(2, 30_000),
                MpvResourcePressureController.Trigger.SYSTEM,
                64 * MIB, 16 * MIB, 30_000);
        controller.evaluate(token, token, normal(3, 30_000),
                MpvResourcePressureController.Trigger.RUNTIME,
                64 * MIB, 16 * MIB, 60_000);

        assertEquals(1, controller.snapshot().normalSamples());
        assertFalse(controller.snapshot().expansionAllowed());
    }

    @Test
    public void initiallyNormalFactsAreImmediatelyUnrestricted() {
        MpvResourcePressureController controller = controller("normal", 10);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        MpvResourcePressureController.Decision decision = controller.evaluate(
                token, token, normal(1, 10),
                MpvResourcePressureController.Trigger.BASELINE,
                48 * MIB, 0, 10);

        assertEquals(MpvResourcePressureController.Action.STABLE, decision.action());
        assertEquals(MpvForwardCachePolicy.MAX_FORWARD_BYTES,
                decision.forwardCeilingBytes());
        assertEquals(MpvBackCachePolicy.MAX_BACK_BYTES,
                decision.backCeilingBytes());
        assertTrue(decision.expansionAllowed());
        assertTrue(decision.preloadAllowed());
        assertEquals(0, decision.normalSamples());
    }

    @Test
    public void staleRevisionAndOutOfOrderSampleCannotReleaseRestriction() {
        MpvResourcePressureController controller = controller("order", 6);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, hard(2, 20),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 20);

        MpvResourcePressureController.Decision stale = controller.evaluate(
                token, token, normal(1, 30),
                MpvResourcePressureController.Trigger.SYSTEM,
                24 * MIB, 0, 30);
        MpvResourcePressureController.Decision outOfOrder = controller.evaluate(
                token, token, normal(3, 10),
                MpvResourcePressureController.Trigger.SYSTEM,
                24 * MIB, 0, 40);

        assertEquals(MpvResourcePressureController.Reason.STALE_REVISION,
                stale.reason());
        assertEquals(MpvResourcePressureController.Reason.OUT_OF_ORDER_SAMPLE,
                outOfOrder.reason());
        assertEquals(24 * MIB, controller.snapshot().forwardCeilingBytes());
        assertFalse(controller.snapshot().preloadAllowed());
    }

    @Test
    public void inactivePolicyReleasesOnlyAutoOwnedRestrictions() {
        MpvResourcePressureController controller = controller("inactive", 7);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token, hard(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 0);

        MpvResourcePressureController.Decision inactive = controller.evaluate(
                token, token, inactive(2, 10),
                MpvResourcePressureController.Trigger.BASELINE,
                24 * MIB, 0, 10);

        assertFalse(inactive.active());
        assertEquals(MpvForwardCachePolicy.MAX_FORWARD_BYTES,
                inactive.forwardCeilingBytes());
        assertEquals(MpvBackCachePolicy.MAX_BACK_BYTES,
                inactive.backCeilingBytes());
        assertTrue(inactive.expansionAllowed());
        assertTrue(inactive.preloadAllowed());
    }

    @Test
    public void staleSessionCannotMutateCurrentController() {
        MpvResourcePressureController controller = controller("session", 8);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        PlaybackAutoContext.SessionToken stale = token("stale", 1);

        MpvResourcePressureController.Decision decision = controller.evaluate(
                token, stale, hard(1, 0),
                MpvResourcePressureController.Trigger.SYSTEM,
                96 * MIB, 32 * MIB, 0);

        assertEquals(MpvResourcePressureController.Reason.STALE_SESSION,
                decision.reason());
        assertEquals(0, controller.snapshot().evaluations());
        assertTrue(controller.snapshot().preloadAllowed());
    }

    private static MpvResourcePressureController controller(
            String trace,
            long generation) {
        MpvResourcePressureController controller =
                new MpvResourcePressureController();
        controller.beginSession(token(trace, generation));
        return controller;
    }

    private static MpvResourcePressurePolicy.Assessment hard(long revision, long sample) {
        return assessment(true, MpvResourcePressurePolicy.Level.HARD,
                MpvResourcePressurePolicy.Reason.THERMAL_SEVERE,
                revision, sample);
    }

    private static MpvResourcePressurePolicy.Assessment constrained(
            long revision,
            long sample) {
        return assessment(true, MpvResourcePressurePolicy.Level.CONSTRAINED,
                MpvResourcePressurePolicy.Reason.METERED,
                revision, sample);
    }

    private static MpvResourcePressurePolicy.Assessment unknown(
            long revision,
            long sample) {
        return assessment(true, MpvResourcePressurePolicy.Level.UNKNOWN,
                MpvResourcePressurePolicy.Reason.NETWORK_UNKNOWN,
                revision, sample);
    }

    private static MpvResourcePressurePolicy.Assessment normal(
            long revision,
            long sample) {
        return assessment(true, MpvResourcePressurePolicy.Level.NORMAL,
                MpvResourcePressurePolicy.Reason.NORMAL,
                revision, sample);
    }

    private static MpvResourcePressurePolicy.Assessment inactive(
            long revision,
            long sample) {
        return assessment(false, MpvResourcePressurePolicy.Level.INACTIVE,
                MpvResourcePressurePolicy.Reason.CONFIG_PRIORITY,
                revision, sample);
    }

    private static MpvResourcePressurePolicy.Assessment assessment(
            boolean active,
            MpvResourcePressurePolicy.Level level,
            MpvResourcePressurePolicy.Reason reason,
            long revision,
            long sample) {
        return new MpvResourcePressurePolicy.Assessment(
                active,
                level,
                reason,
                revision,
                sample,
                sample,
                level == MpvResourcePressurePolicy.Level.NORMAL,
                level == MpvResourcePressurePolicy.Level.NORMAL
                        ? PlaybackAutoContext.MemoryPressure.NORMAL
                        : PlaybackAutoContext.MemoryPressure.UNKNOWN,
                level == MpvResourcePressurePolicy.Level.NORMAL,
                level != MpvResourcePressurePolicy.Level.UNKNOWN,
                level == MpvResourcePressurePolicy.Level.NORMAL
                        ? PlaybackAutoContext.ThermalState.NOMINAL
                        : PlaybackAutoContext.ThermalState.UNKNOWN,
                level != MpvResourcePressurePolicy.Level.UNKNOWN,
                level == MpvResourcePressurePolicy.Level.NORMAL
                        ? PlaybackAutoContext.PowerState.NORMAL
                        : PlaybackAutoContext.PowerState.UNKNOWN,
                level != MpvResourcePressurePolicy.Level.UNKNOWN,
                level == MpvResourcePressurePolicy.Level.NORMAL
                        ? PlaybackAutoContext.NetworkCost.UNMETERED
                        : PlaybackAutoContext.NetworkCost.UNKNOWN,
                level != MpvResourcePressurePolicy.Level.UNKNOWN,
                PlaybackAutoContext.NetworkSnapshot.unknown());
    }

    private static PlaybackAutoContext.SessionToken token(
            String trace,
            long generation) {
        long seed = Math.abs((trace == null ? 0 : trace.hashCode()) * 31L + generation);
        return new PlaybackAutoContext.SessionToken(
                "p-" + Long.toString(seed + 1, 36)
                        + "-" + Long.toString(generation + 1, 36),
                generation);
    }
}
