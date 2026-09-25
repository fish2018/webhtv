package androidx.media3.mpvplayer;

import com.fongmi.android.tv.player.PlaybackAutoContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvPreloadCachePolicyTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    public void playingProgressiveVodExtendsTimeAndCapacity() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                false, true, 30, 64 * MIB, 300, 256 * MIB,
                60_000, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE);

        assertTrue(decision.apply());
        assertEquals(300, decision.targetSeconds());
        assertEquals(256 * MIB, decision.targetBytes());
    }

    @Test
    public void pausedProgressiveVodUsesTheSameForwardTargetWhenAllowed() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                true, true, 30, 64 * MIB, 300, 128 * MIB,
                60_000, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE);

        assertTrue(decision.apply());
        assertEquals(300, decision.targetSeconds());
        assertEquals(128 * MIB, decision.targetBytes());
    }

    @Test
    public void pausePolicyOnlyBlocksPausedPreload() {
        assertFalse(resolve(
                true, false, 30, 64 * MIB, 300, 128 * MIB,
                0, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE).apply());
        assertTrue(resolve(
                false, false, 30, 64 * MIB, 300, 128 * MIB,
                0, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE).apply());
    }

    @Test
    public void knownDurationExternalLoopbackIsEligibleVod() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                false, true, 30, 64 * MIB, 600, 256 * MIB,
                0, 1_440_000,
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.StreamKind.UNKNOWN,
                PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK);

        assertTrue(decision.apply());
        assertEquals(600, decision.targetSeconds());
    }

    @Test
    public void knownDurationOpaqueRemoteHttpIsEligibleVod() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                true, true, 30, 64 * MIB, 600, 256 * MIB,
                0, 1_440_000,
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.StreamKind.UNKNOWN,
                PlaybackAutoContext.PathKind.REMOTE);

        assertTrue(decision.apply());
        assertEquals(600, decision.targetSeconds());
        assertEquals(256 * MIB, decision.targetBytes());
    }

    @Test
    public void knownDurationOpaqueLanHttpIsEligibleVod() {
        assertTrue(resolve(
                false, true, 30, 64 * MIB, 600, 256 * MIB,
                0, 1_440_000,
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.StreamKind.UNKNOWN,
                PlaybackAutoContext.PathKind.LAN_PRIVATE).apply());
    }

    @Test
    public void unknownPathResourceRemainsConservative() {
        assertFalse(resolve(
                false, true, 30, 64 * MIB, 600, 256 * MIB,
                0, 1_440_000,
                PlaybackAutoContext.Protocol.UNKNOWN,
                PlaybackAutoContext.StreamKind.UNKNOWN,
                PlaybackAutoContext.PathKind.UNKNOWN).apply());
    }

    @Test
    public void wholeMediaUsesRemainingDuration() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                false, true, 30, 64 * MIB, 0, 128 * MIB,
                600_000, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE);

        assertTrue(decision.apply());
        assertEquals(3_000, decision.targetSeconds());
    }

    @Test
    public void knownDurationAtEndDoesNotExtendCache() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                false, true, 30, 64 * MIB, 60, 128 * MIB,
                3_600_000, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE);

        assertFalse(decision.apply());
        assertEquals(MpvPreloadCachePolicy.Reason.ALREADY_AHEAD,
                decision.reason());
    }

    @Test
    public void segmentedAndLiveResourcesKeepTheirNormalCacheTarget() {
        assertFalse(resolve(
                false, true, 30, 64 * MIB, 300, 128 * MIB,
                0, 3_600_000,
                PlaybackAutoContext.Protocol.HLS,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE).apply());
        assertFalse(resolve(
                false, true, 30, 64 * MIB, 300, 128 * MIB,
                0, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.LIVE,
                PlaybackAutoContext.PathKind.REMOTE).apply());
    }

    @Test
    public void targetNeverShrinksTheNormalCache() {
        MpvPreloadCachePolicy.Decision decision = resolve(
                false, true, 300, 256 * MIB, 60, 128 * MIB,
                0, 3_600_000,
                PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP,
                PlaybackAutoContext.StreamKind.VOD,
                PlaybackAutoContext.PathKind.REMOTE);

        assertFalse(decision.apply());
        assertEquals(300, decision.targetSeconds());
        assertEquals(256 * MIB, decision.targetBytes());
    }

    private static MpvPreloadCachePolicy.Decision resolve(
            boolean paused,
            boolean pauseAllowed,
            int baselineSeconds,
            long baselineBytes,
            int aheadSeconds,
            long preloadCapacityBytes,
            long positionMs,
            long durationMs,
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.StreamKind streamKind,
            PlaybackAutoContext.PathKind playerPath) {
        return MpvPreloadCachePolicy.resolve(
                new MpvPreloadCachePolicy.Request(
                        paused, true, pauseAllowed, true, true,
                        protocol, streamKind, playerPath,
                        baselineSeconds, baselineBytes,
                        aheadSeconds, preloadCapacityBytes,
                        positionMs, durationMs));
    }
}
