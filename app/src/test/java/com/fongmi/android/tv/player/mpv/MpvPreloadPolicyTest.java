package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvPreloadPolicyTest {

    @Test
    public void automaticConcurrencyIsZeroOrOneAndManualKeepsConfiguredValue() {
        assertEquals(1, MpvPreloadPolicy.resolveExecutorThreads(true, 4));
        assertEquals(1, MpvPreloadPolicy.resolveExecutorThreads(true, 1));
        assertEquals(4, MpvPreloadPolicy.resolveExecutorThreads(false, 4));
        assertEquals(1, MpvPreloadPolicy.resolveExecutorThreads(false, 0));
    }

    @Test
    public void lowRatioPausesAndHealthyRatioBecomesRecoveryEvidence() {
        MpvPreloadPolicy.Assessment low = MpvPreloadPolicy.assess(
                withThroughput(eligible(), 11_490_000L, true, true));
        MpvPreloadPolicy.Assessment middle = MpvPreloadPolicy.assess(
                withThroughput(eligible(), 11_500_000L, true, true));
        MpvPreloadPolicy.Assessment healthy = MpvPreloadPolicy.assess(
                withThroughput(eligible(), 14_000_000L, true, true));

        assertEquals(MpvPreloadPolicy.Signal.BOOTSTRAP, low.signal());
        assertEquals(MpvPreloadPolicy.Reason.RATIO_LOW, low.reason());
        assertEquals(1_149, low.ratioPermille());
        assertEquals(MpvPreloadPolicy.Signal.HOLD, middle.signal());
        assertEquals(MpvPreloadPolicy.Signal.RECOVER, healthy.signal());
        assertEquals(1_400, healthy.ratioPermille());
    }

    @Test
    public void unknownAndStaleProxyThroughputUseBootstrapAdmission() {
        MpvPreloadPolicy.Assessment unknown = MpvPreloadPolicy.assess(
                withThroughput(eligible(), 0, false, false));
        MpvPreloadPolicy.Assessment stale = MpvPreloadPolicy.assess(
                withThroughput(eligible(), 20_000_000L, true, false));

        assertEquals(MpvPreloadPolicy.Signal.BOOTSTRAP, unknown.signal());
        assertEquals(MpvPreloadPolicy.Reason.THROUGHPUT_BOOTSTRAP, unknown.reason());
        assertEquals(MpvPreloadPolicy.Signal.BOOTSTRAP, stale.signal());
        assertEquals(MpvPreloadPolicy.Reason.THROUGHPUT_REFRESH, stale.reason());
    }

    @Test
    public void bufferAndRebufferRiskPauseImmediately() {
        assertEquals(MpvPreloadPolicy.Reason.LOW_BUFFER,
                MpvPreloadPolicy.assess(withBuffer(eligible(), 7_999, false,
                        false, false)).reason());
        assertEquals(MpvPreloadPolicy.Reason.BUFFER_DECLINING,
                MpvPreloadPolicy.assess(withBuffer(eligible(), 20_000, true,
                        false, false)).reason());
        assertEquals(MpvPreloadPolicy.Reason.REBUFFER,
                MpvPreloadPolicy.assess(withBuffer(eligible(), 20_000, false,
                        true, false)).reason());
        assertEquals(MpvPreloadPolicy.Reason.BUFFERING,
                MpvPreloadPolicy.assess(withBuffer(eligible(), 20_000, false,
                        false, true)).reason());
    }

    @Test
    public void foregroundRequestSuspendsWithoutBecomingRiskEvidence() {
        MpvPreloadPolicy.Assessment assessment = MpvPreloadPolicy.assess(
                withForeground(eligible(), 2));

        assertEquals(MpvPreloadPolicy.Signal.SUSPEND, assessment.signal());
        assertEquals(MpvPreloadPolicy.Reason.FOREGROUND_ACTIVE, assessment.reason());
    }

    @Test
    public void externalAndOpaqueRoutesDefaultClosed() {
        MpvPreloadPolicy.Assessment external = MpvPreloadPolicy.assess(
                withRoute(eligible(), PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE,
                        true, PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK, true,
                        PlaybackAutoContext.UpstreamState.OPAQUE, true));
        MpvPreloadPolicy.Assessment opaque = MpvPreloadPolicy.assess(
                withRoute(eligible(), PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE,
                        true, PlaybackAutoContext.PathKind.REMOTE, true,
                        PlaybackAutoContext.UpstreamState.OPAQUE, true));
        MpvPreloadPolicy.Assessment unknown = MpvPreloadPolicy.assess(
                withRoute(eligible(), PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE,
                        true, PlaybackAutoContext.PathKind.UNKNOWN, false,
                        PlaybackAutoContext.UpstreamState.UNKNOWN, false));

        assertEquals(MpvPreloadPolicy.Reason.EXTERNAL_LOOPBACK, external.reason());
        assertEquals(MpvPreloadPolicy.Reason.UPSTREAM_OPAQUE, opaque.reason());
        assertEquals(MpvPreloadPolicy.Reason.PATH_UNKNOWN, unknown.reason());
    }

    @Test
    public void onlyKnownHlsVodIsEligible() {
        MpvPreloadPolicy.Assessment live = MpvPreloadPolicy.assess(
                withStream(eligible(), PlaybackAutoContext.Protocol.HLS, true,
                        PlaybackAutoContext.StreamKind.LIVE, true));
        MpvPreloadPolicy.Assessment dash = MpvPreloadPolicy.assess(
                withStream(eligible(), PlaybackAutoContext.Protocol.DASH, true,
                        PlaybackAutoContext.StreamKind.VOD, true));
        MpvPreloadPolicy.Assessment unknown = MpvPreloadPolicy.assess(
                withStream(eligible(), PlaybackAutoContext.Protocol.HLS, true,
                        PlaybackAutoContext.StreamKind.UNKNOWN, false));

        assertEquals(MpvPreloadPolicy.Reason.NOT_VOD, live.reason());
        assertEquals(MpvPreloadPolicy.Reason.NOT_HLS, dash.reason());
        assertEquals(MpvPreloadPolicy.Reason.STREAM_UNKNOWN, unknown.reason());
    }

    @Test
    public void resourceAndSharedCacheBudgetAreHardGates() {
        assertEquals(MpvPreloadPolicy.Reason.RESOURCE_GATE,
                MpvPreloadPolicy.assess(withResource(eligible(), false)).reason());
        assertEquals(MpvPreloadPolicy.Reason.CACHE_STORAGE_UNKNOWN,
                MpvPreloadPolicy.assess(withCache(eligible(), true,
                        false, false, false)).reason());
        assertEquals(MpvPreloadPolicy.Reason.CACHE_CIRCUIT_OPEN,
                MpvPreloadPolicy.assess(withCache(eligible(), true,
                        true, true, true)).reason());
        assertEquals(MpvPreloadPolicy.Reason.CACHE_BUDGET_EXHAUSTED,
                MpvPreloadPolicy.assess(withCache(eligible(), true,
                        true, false, false)).reason());
    }

    @Test
    public void manualAndConfigPriorityDoNotOwnTheUserSetting() {
        MpvPreloadPolicy.Assessment manual = MpvPreloadPolicy.assess(
                withMode(eligible(), false, true));
        MpvPreloadPolicy.Assessment config = MpvPreloadPolicy.assess(
                withMode(eligible(), true, false));

        assertTrue(MpvPreloadPolicy.ownsProxyControl(true, true));
        assertFalse(MpvPreloadPolicy.ownsProxyControl(false, true));
        assertFalse(MpvPreloadPolicy.ownsProxyControl(true, false));
        assertFalse(manual.active());
        assertEquals(MpvPreloadPolicy.Reason.NOT_AUTOMATIC, manual.reason());
        assertFalse(config.active());
        assertEquals(MpvPreloadPolicy.Reason.CONFIG_PRIORITY, config.reason());
    }

    private static MpvPreloadPolicy.Request eligible() {
        return new MpvPreloadPolicy.Request(
                true, true, true, true,
                PlaybackAutoContext.Protocol.HLS, true,
                PlaybackAutoContext.StreamKind.VOD, true,
                PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE, true,
                PlaybackAutoContext.PathKind.REMOTE, true,
                PlaybackAutoContext.UpstreamState.VISIBLE, true,
                true, true, true, true, false,
                20_000_000L, true, true, 1_000,
                10_000_000L, true, 20_000, 1_000,
                0, false, false, false, 0, 1);
    }

    private static MpvPreloadPolicy.Request withMode(
            MpvPreloadPolicy.Request r, boolean automatic, boolean priority) {
        return copy(r, automatic, priority, r.protocol(), r.protocolUsable(),
                r.streamKind(), r.streamKindUsable(), r.playerPath(),
                r.playerPathUsable(), r.upstreamPath(), r.upstreamPathUsable(),
                r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), r.cacheEnabled(),
                r.cacheStorageKnown(), r.cacheBudgetAvailable(),
                r.cacheCircuitOpen(), r.upstreamBitsPerSecond(),
                r.throughputKnown(), r.throughputFresh(),
                r.bufferedDurationMs(), r.bufferDeclining(), r.rebufferRisk(),
                r.buffering(), r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withThroughput(
            MpvPreloadPolicy.Request r, long bits, boolean known, boolean fresh) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                r.playerPath(), r.playerPathUsable(), r.upstreamPath(),
                r.upstreamPathUsable(), r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), r.cacheEnabled(), r.cacheStorageKnown(),
                r.cacheBudgetAvailable(), r.cacheCircuitOpen(), bits, known, fresh,
                r.bufferedDurationMs(), r.bufferDeclining(), r.rebufferRisk(),
                r.buffering(), r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withBuffer(
            MpvPreloadPolicy.Request r, long buffer, boolean declining,
            boolean rebuffer, boolean buffering) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                r.playerPath(), r.playerPathUsable(), r.upstreamPath(),
                r.upstreamPathUsable(), r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), r.cacheEnabled(), r.cacheStorageKnown(),
                r.cacheBudgetAvailable(), r.cacheCircuitOpen(),
                r.upstreamBitsPerSecond(), r.throughputKnown(), r.throughputFresh(),
                buffer, declining, rebuffer, buffering, r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withForeground(
            MpvPreloadPolicy.Request r, int foreground) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                r.playerPath(), r.playerPathUsable(), r.upstreamPath(),
                r.upstreamPathUsable(), r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), r.cacheEnabled(), r.cacheStorageKnown(),
                r.cacheBudgetAvailable(), r.cacheCircuitOpen(),
                r.upstreamBitsPerSecond(), r.throughputKnown(), r.throughputFresh(),
                r.bufferedDurationMs(), r.bufferDeclining(), r.rebufferRisk(),
                r.buffering(), foreground);
    }

    private static MpvPreloadPolicy.Request withRoute(
            MpvPreloadPolicy.Request r,
            PlaybackAutoContext.PathKind playerPath, boolean playerUsable,
            PlaybackAutoContext.PathKind upstreamPath, boolean upstreamUsable,
            PlaybackAutoContext.UpstreamState upstreamState, boolean stateUsable) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                playerPath, playerUsable, upstreamPath, upstreamUsable,
                upstreamState, stateUsable, r.resourcePreloadAllowed(),
                r.cacheEnabled(), r.cacheStorageKnown(), r.cacheBudgetAvailable(),
                r.cacheCircuitOpen(), r.upstreamBitsPerSecond(),
                r.throughputKnown(), r.throughputFresh(), r.bufferedDurationMs(),
                r.bufferDeclining(), r.rebufferRisk(), r.buffering(),
                r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withStream(
            MpvPreloadPolicy.Request r,
            PlaybackAutoContext.Protocol protocol, boolean protocolUsable,
            PlaybackAutoContext.StreamKind stream, boolean streamUsable) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), protocol,
                protocolUsable, stream, streamUsable, r.playerPath(),
                r.playerPathUsable(), r.upstreamPath(), r.upstreamPathUsable(),
                r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), r.cacheEnabled(),
                r.cacheStorageKnown(), r.cacheBudgetAvailable(),
                r.cacheCircuitOpen(), r.upstreamBitsPerSecond(),
                r.throughputKnown(), r.throughputFresh(), r.bufferedDurationMs(),
                r.bufferDeclining(), r.rebufferRisk(), r.buffering(),
                r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withResource(
            MpvPreloadPolicy.Request r, boolean allowed) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                r.playerPath(), r.playerPathUsable(), r.upstreamPath(),
                r.upstreamPathUsable(), r.upstreamState(), r.upstreamStateUsable(),
                allowed, r.cacheEnabled(), r.cacheStorageKnown(),
                r.cacheBudgetAvailable(), r.cacheCircuitOpen(),
                r.upstreamBitsPerSecond(), r.throughputKnown(),
                r.throughputFresh(), r.bufferedDurationMs(), r.bufferDeclining(),
                r.rebufferRisk(), r.buffering(), r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request withCache(
            MpvPreloadPolicy.Request r, boolean enabled, boolean storage,
            boolean budget, boolean circuit) {
        return copy(r, r.automatic(), r.performanceOptionsPriority(), r.protocol(),
                r.protocolUsable(), r.streamKind(), r.streamKindUsable(),
                r.playerPath(), r.playerPathUsable(), r.upstreamPath(),
                r.upstreamPathUsable(), r.upstreamState(), r.upstreamStateUsable(),
                r.resourcePreloadAllowed(), enabled, storage, budget, circuit,
                r.upstreamBitsPerSecond(), r.throughputKnown(),
                r.throughputFresh(), r.bufferedDurationMs(), r.bufferDeclining(),
                r.rebufferRisk(), r.buffering(), r.foregroundRequests());
    }

    private static MpvPreloadPolicy.Request copy(
            MpvPreloadPolicy.Request r,
            boolean automatic,
            boolean priority,
            PlaybackAutoContext.Protocol protocol,
            boolean protocolUsable,
            PlaybackAutoContext.StreamKind stream,
            boolean streamUsable,
            PlaybackAutoContext.PathKind playerPath,
            boolean playerPathUsable,
            PlaybackAutoContext.PathKind upstreamPath,
            boolean upstreamPathUsable,
            PlaybackAutoContext.UpstreamState upstreamState,
            boolean upstreamStateUsable,
            boolean resourceAllowed,
            boolean cacheEnabled,
            boolean storageKnown,
            boolean budgetAvailable,
            boolean circuitOpen,
            long throughput,
            boolean throughputKnown,
            boolean throughputFresh,
            long buffer,
            boolean declining,
            boolean rebuffer,
            boolean buffering,
            int foreground) {
        return new MpvPreloadPolicy.Request(
                automatic, r.mpvKernel(), priority, r.preloadConfigured(),
                protocol, protocolUsable, stream, streamUsable,
                playerPath, playerPathUsable, upstreamPath, upstreamPathUsable,
                upstreamState, upstreamStateUsable, resourceAllowed,
                cacheEnabled, storageKnown, budgetAvailable, circuitOpen,
                throughput, throughputKnown, throughputFresh,
                r.throughputSampleAtElapsedMs(), r.selectedBitsPerSecond(),
                r.bufferUsable(), buffer, r.runtimeSampleAtElapsedMs(),
                r.rebufferCount(), buffering, declining, rebuffer,
                foreground, r.contextRevision());
    }
}
