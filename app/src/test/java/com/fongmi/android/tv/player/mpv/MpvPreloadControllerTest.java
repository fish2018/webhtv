package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvPreloadControllerTest {

    @Test
    public void recoveryRequiresTwoNewSamplesAndFifteenStableSeconds() {
        MpvPreloadController controller = controller("recover", 1);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        MpvPreloadController.Decision first = controller.evaluate(
                token, token, request(1, 0, 0, 20_000_000L,
                        20_000, 0, 0), 0);
        MpvPreloadController.Decision repeatedEvidence = controller.evaluate(
                token, token, request(2, 0, 5_000, 20_000_000L,
                        20_000, 0, 0), 5_000);
        MpvPreloadController.Decision second = controller.evaluate(
                token, token, request(3, 10_000, 10_000, 20_000_000L,
                        20_000, 0, 0), 10_000);
        MpvPreloadController.Decision allowed = controller.evaluate(
                token, token, request(4, 15_000, 15_000, 20_000_000L,
                        20_000, 0, 0), 15_000);

        assertEquals(MpvPreloadController.Action.RECOVERY_WAIT, first.action());
        assertEquals(1, repeatedEvidence.recoverySamples());
        assertEquals(2, second.recoverySamples());
        assertFalse(second.preloadAllowed());
        assertTrue(allowed.preloadAllowed());
        assertEquals(1, allowed.concurrency());
    }

    @Test
    public void unknownThroughputAllowsSingleBootstrapWorker() {
        MpvPreloadController controller = controller("bootstrap", 9);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        MpvPreloadController.Decision decision = controller.evaluate(
                token, token,
                requestUnknownThroughput(1, 0, 0, 20_000), 0);
        assertEquals(MpvPreloadController.Action.BOOTSTRAP, decision.action());
        assertEquals(MpvPreloadController.State.BOOTSTRAP, decision.state());
        assertEquals(1, decision.concurrency());
    }

    @Test
    public void lowRatioImmediatelyCancelsAllowedPreload() {
        MpvPreloadController controller = allowedController("ratio", 2);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        MpvPreloadController.Decision blocked = controller.evaluate(
                token, token, request(4, 20_000, 20_000, 11_000_000L,
                        20_000, 0, 0), 20_000);

        assertFalse(blocked.preloadAllowed());
        assertTrue(blocked.cancellationRequested());
        assertEquals(MpvPreloadPolicy.Reason.RATIO_LOW, blocked.policyReason());
    }

    @Test
    public void foregroundSuspensionPreservesAdmissionAcrossTheRequest() {
        MpvPreloadController controller = allowedController("foreground", 3);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        MpvPreloadController.Decision suspended = controller.evaluate(
                token, token, request(4, 20_000, 20_000, 20_000_000L,
                        20_000, 0, 1), 20_000);
        MpvPreloadController.Decision resumed = controller.evaluate(
                token, token, request(5, 25_000, 25_000, 12_000_000L,
                        20_000, 0, 0), 25_000);

        assertEquals(MpvPreloadController.State.SUSPENDED, suspended.state());
        assertFalse(suspended.preloadAllowed());
        assertTrue(suspended.ready());
        assertTrue(resumed.preloadAllowed());
        assertEquals(MpvPreloadController.Reason.HYSTERESIS_HOLD,
                resumed.reason());
    }

    @Test
    public void derivedBufferDeclineAndRebufferPauseWithoutCallerGuessing() {
        MpvPreloadController declining = allowedController("decline", 4);
        PlaybackAutoContext.SessionToken declineToken = declining.snapshot().session();
        MpvPreloadController.Decision decline = declining.evaluate(
                declineToken, declineToken,
                request(4, 20_000, 20_000, 20_000_000L,
                        17_000, 0, 0), 20_000);

        MpvPreloadController rebuffering = allowedController("rebuffer", 5);
        PlaybackAutoContext.SessionToken rebufferToken = rebuffering.snapshot().session();
        MpvPreloadController.Decision rebuffer = rebuffering.evaluate(
                rebufferToken, rebufferToken,
                request(4, 20_000, 20_000, 20_000_000L,
                        20_000, 1, 0), 20_000);

        assertEquals(MpvPreloadPolicy.Reason.BUFFER_DECLINING,
                decline.policyReason());
        assertEquals(MpvPreloadPolicy.Reason.REBUFFER,
                rebuffer.policyReason());
    }

    @Test
    public void staleRevisionSessionAndOutOfOrderSamplesCannotReopen() {
        MpvPreloadController controller = controller("order", 6);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token,
                request(2, 10_000, 10_000, 11_000_000L,
                        20_000, 0, 0), 10_000);

        MpvPreloadController.Decision staleRevision = controller.evaluate(
                token, token, request(1, 15_000, 15_000, 20_000_000L,
                        20_000, 0, 0), 15_000);
        MpvPreloadController.Decision outOfOrder = controller.evaluate(
                token, token, request(3, 5_000, 20_000, 20_000_000L,
                        20_000, 0, 0), 20_000);
        MpvPreloadController.Decision staleSession = controller.evaluate(
                token, token("other", 1),
                request(4, 25_000, 25_000, 20_000_000L,
                        20_000, 0, 0), 25_000);

        assertEquals(MpvPreloadController.Reason.STALE_REVISION,
                staleRevision.reason());
        assertEquals(MpvPreloadController.Reason.OUT_OF_ORDER_SAMPLE,
                outOfOrder.reason());
        assertEquals(MpvPreloadController.Reason.STALE_SESSION,
                staleSession.reason());
        assertFalse(controller.snapshot().ready());
    }

    @Test
    public void duplicateRevisionDoesNotAdvanceRecovery() {
        MpvPreloadController controller = controller("duplicate", 7);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token,
                request(1, 0, 0, 20_000_000L, 20_000, 0, 0), 0);

        MpvPreloadController.Decision duplicate = controller.evaluate(
                token, token,
                request(1, 10_000, 10_000, 20_000_000L,
                        20_000, 0, 0), 10_000);

        assertEquals(MpvPreloadController.Reason.DUPLICATE_REVISION,
                duplicate.reason());
        assertEquals(1, controller.snapshot().recoverySamples());
    }

    @Test
    public void seekDisruptionAndSessionEndReturnToClosedState() {
        MpvPreloadController controller = allowedController("seek", 8);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();

        assertTrue(controller.disrupt(token));
        assertFalse(controller.snapshot().ready());
        assertEquals(MpvPreloadController.Reason.DISCONTINUITY,
                controller.snapshot().lastDecision().reason());

        controller.endSession(token);
        assertFalse(controller.snapshot().session().active());
        assertEquals(MpvPreloadController.State.IDLE, controller.snapshot().state());
    }

    private static MpvPreloadController allowedController(String trace, long generation) {
        MpvPreloadController controller = controller(trace, generation);
        PlaybackAutoContext.SessionToken token = controller.snapshot().session();
        controller.evaluate(token, token,
                request(1, 0, 0, 20_000_000L, 20_000, 0, 0), 0);
        controller.evaluate(token, token,
                request(2, 10_000, 10_000, 20_000_000L, 20_000, 0, 0),
                10_000);
        MpvPreloadController.Decision allowed = controller.evaluate(token, token,
                request(3, 15_000, 15_000, 20_000_000L, 20_000, 0, 0),
                15_000);
        assertTrue(allowed.preloadAllowed());
        return controller;
    }

    private static MpvPreloadController controller(String trace, long generation) {
        MpvPreloadController controller = new MpvPreloadController();
        controller.beginSession(token(trace, generation));
        return controller;
    }

    private static MpvPreloadPolicy.Request request(
            long revision,
            long throughputSample,
            long runtimeSample,
            long throughput,
            long buffer,
            int rebuffer,
            int foreground) {
        return new MpvPreloadPolicy.Request(
                true, true, true, true,
                PlaybackAutoContext.Protocol.HLS, true,
                PlaybackAutoContext.StreamKind.VOD, true,
                PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, true,
                PlaybackAutoContext.PathKind.REMOTE, true,
                PlaybackAutoContext.UpstreamState.VISIBLE, true,
                true, true, true, true, false,
                throughput, true, true, throughputSample,
                10_000_000L, true, buffer, runtimeSample,
                rebuffer, false, false, false, foreground, revision);
    }

    private static PlaybackAutoContext.SessionToken token(
            String trace, long generation) {
        long seed = Math.abs((trace == null ? 0 : trace.hashCode()) * 31L + generation);
        return new PlaybackAutoContext.SessionToken(
                "p-" + Long.toString(seed + 1, 36)
                        + "-" + Long.toString(generation + 1, 36),
                generation);
    }

    private static MpvPreloadPolicy.Request requestUnknownThroughput(
            long revision, long throughputSample, long runtimeSample, long buffer) {
        MpvPreloadPolicy.Request base = request(
                revision, throughputSample, runtimeSample, 0, buffer, 0, 0);
        return new MpvPreloadPolicy.Request(
                base.automatic(), base.mpvKernel(), base.performanceOptionsPriority(),
                base.preloadConfigured(), base.protocol(), base.protocolUsable(),
                base.streamKind(), base.streamKindUsable(), base.playerPath(),
                base.playerPathUsable(), base.upstreamPath(), base.upstreamPathUsable(),
                base.upstreamState(), base.upstreamStateUsable(),
                base.resourcePreloadAllowed(), base.cacheEnabled(),
                base.cacheStorageKnown(), base.cacheBudgetAvailable(),
                base.cacheCircuitOpen(), 0, false, false,
                base.throughputSampleAtElapsedMs(), base.selectedBitsPerSecond(),
                base.bufferUsable(), base.bufferedDurationMs(),
                base.runtimeSampleAtElapsedMs(), base.rebufferCount(),
                base.buffering(), base.bufferDeclining(), base.rebufferRisk(),
                base.foregroundRequests(), base.contextRevision());
    }
}
