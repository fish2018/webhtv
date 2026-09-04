package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoLoadControlTest {

    @Test
    public void seekRecoveryUsesMedia3UserActionThresholdWithoutWeakeningRebuffer() {
        assertEquals(ExoPlaybackThresholdCoordinator.Episode.REBUFFER,
                AutoLoadControl.playbackEpisode(true, true));
        assertEquals(ExoPlaybackThresholdCoordinator.Episode.SEEK,
                AutoLoadControl.playbackEpisode(false, true));
        assertEquals(ExoPlaybackThresholdCoordinator.Episode.STARTUP,
                AutoLoadControl.playbackEpisode(false, false));

        ExoPlaybackThresholdPolicy.Decision policy = ExoPlaybackThresholdPolicy.resolve(
                ExoPlaybackThresholdPolicy.Inputs.unknown());
        ExoPlaybackThresholdCoordinator.Selection criticalSeek =
                new ExoPlaybackThresholdCoordinator.Selection(
                        com.fongmi.android.tv.player.PlaybackAutoContext.SessionToken.none(),
                        ExoPlaybackThresholdCoordinator.Episode.SEEK,
                        8_000,
                        15_000,
                        policy,
                        true,
                        ExoPlaybackThresholdCoordinator.Action.LOCK);
        ExoPlaybackThresholdCoordinator.Selection conservativeRebuffer =
                new ExoPlaybackThresholdCoordinator.Selection(
                        com.fongmi.android.tv.player.PlaybackAutoContext.SessionToken.none(),
                        ExoPlaybackThresholdCoordinator.Episode.REBUFFER,
                        8_000,
                        15_000,
                        policy,
                        true,
                        ExoPlaybackThresholdCoordinator.Action.LOCK);

        assertEquals(1_000, criticalSeek.thresholdMs());
        assertEquals(15_000, conservativeRebuffer.thresholdMs());
    }

    @Test
    public void seekMarkerIsSessionBoundAndExpires() {
        ExoPlaybackThresholdCoordinator coordinator =
                new ExoPlaybackThresholdCoordinator();
        com.fongmi.android.tv.player.PlaybackAutoContext.SessionToken session =
                new com.fongmi.android.tv.player.PlaybackAutoContext.SessionToken(
                        "p-seek-1", 1);

        coordinator.markSeek(session, 10_000);

        assertTrue(coordinator.isSeekPending(session, 10_001));
        assertFalse(coordinator.isSeekPending(
                com.fongmi.android.tv.player.PlaybackAutoContext.SessionToken.none(),
                10_001));
        assertFalse(coordinator.isSeekPending(
                session,
                10_000 + ExoPlaybackThresholdCoordinator.SEEK_RECOVERY_TIMEOUT_MS + 1));
    }

    @Test
    public void usesAdaptiveVodRebufferThreshold() {
        assertFalse(AutoLoadControl.reachedAdaptiveThreshold(2_999_000, 1f, C.TIME_UNSET, 3_000));
        assertTrue(AutoLoadControl.reachedAdaptiveThreshold(3_000_000, 1f, C.TIME_UNSET, 3_000));
        assertFalse(AutoLoadControl.reachedAdaptiveThreshold(7_999_000, 1f, C.TIME_UNSET, 8_000));
        assertTrue(AutoLoadControl.reachedAdaptiveThreshold(8_000_000, 1f, C.TIME_UNSET, 8_000));
        assertFalse(AutoLoadControl.reachedAdaptiveThreshold(14_999_000, 1f, C.TIME_UNSET, 15_000));
        assertTrue(AutoLoadControl.reachedAdaptiveThreshold(15_000_000, 1f, C.TIME_UNSET, 15_000));
    }

    @Test
    public void playbackSpeedUsesPlayoutDuration() {
        assertFalse(AutoLoadControl.reachedAdaptiveThreshold(3_999_000, 2f, C.TIME_UNSET, 2_000));
        assertTrue(AutoLoadControl.reachedAdaptiveThreshold(4_000_000, 2f, C.TIME_UNSET, 2_000));
    }

    @Test
    public void liveOffsetKeepsMedia3HalfOffsetLimit() {
        assertFalse(AutoLoadControl.reachedAdaptiveThreshold(1_999_000, 1f, 4_000_000, 8_000));
        assertTrue(AutoLoadControl.reachedAdaptiveThreshold(2_000_000, 1f, 4_000_000, 8_000));
    }

    @Test
    public void controlledSingleTrackThresholdNeverExceedsThreeSeconds() {
        assertEquals(0, AutoLoadControl.controlledTimeThresholdMs(-1));
        assertEquals(1_500, AutoLoadControl.controlledTimeThresholdMs(1_500));
        assertEquals(3_000, AutoLoadControl.controlledTimeThresholdMs(3_000));
        assertEquals(3_000, AutoLoadControl.controlledTimeThresholdMs(15_000));
    }

    @Test
    public void controlledStartFallsBackWhenRescueCannotContinue() {
        assertFalse(AutoLoadControl.shouldStartControlledPlayback(
                false, true, true, false));
        assertTrue(AutoLoadControl.shouldStartControlledPlayback(
                false, false, true, false));
        assertTrue(AutoLoadControl.shouldStartControlledPlayback(
                false, false, false, true));
        assertTrue(AutoLoadControl.shouldStartControlledPlayback(
                true, true, false, false));
    }

    @Test
    public void dynamicThresholdPreservesTargetBytesEscape() {
        assertTrue(AutoLoadControl.shouldStartDynamicPlayback(true, false));
        assertTrue(AutoLoadControl.shouldStartDynamicPlayback(false, true));
        assertFalse(AutoLoadControl.shouldStartDynamicPlayback(false, false));
        assertTrue(AutoLoadControl.shouldStartDynamicPlayback(
                false, true, true, false));
        assertFalse(AutoLoadControl.shouldStartDynamicPlayback(
                true, true, true, false));
        assertTrue(AutoLoadControl.shouldStartDynamicPlayback(
                true, true, true, true));
    }

    @Test
    public void proxyVodKeepsStartupFastAndOnlyUsesLongRecoveryAtProtectionFloor() {
        assertEquals(1_500, AutoLoadControl.effectiveDynamicThresholdMs(
                1_500,
                false,
                true,
                1f,
                ExoPlaybackThresholdPolicy.RiskLevel.NONE));
        assertEquals(3_000, AutoLoadControl.effectiveDynamicThresholdMs(
                15_000,
                true,
                true,
                0.90f,
                ExoPlaybackThresholdPolicy.RiskLevel.CRITICAL));
        assertEquals(15_000, AutoLoadControl.effectiveDynamicThresholdMs(
                15_000,
                true,
                true,
                0.85f,
                ExoPlaybackThresholdPolicy.RiskLevel.CRITICAL));
        assertEquals(3_000, AutoLoadControl.effectiveDynamicThresholdMs(
                15_000,
                true,
                true,
                0.75f,
                ExoPlaybackThresholdPolicy.RiskLevel.CRITICAL));
        assertEquals(15_000, AutoLoadControl.effectiveDynamicThresholdMs(
                15_000,
                true,
                false,
                1f,
                ExoPlaybackThresholdPolicy.RiskLevel.CRITICAL));
    }

    @Test
    public void memoryPressureSuppressesBackBufferAndPreloading() {
        assertEquals(15_000_000L, AutoLoadControl.effectiveBackBufferDurationUs(15_000_000L, false));
        assertEquals(0L, AutoLoadControl.effectiveBackBufferDurationUs(15_000_000L, true));
        assertEquals(0L, AutoLoadControl.effectiveBackBufferDurationUs(-1L, false));
        assertTrue(AutoLoadControl.shouldContinuePreloading(true, false));
        assertFalse(AutoLoadControl.shouldContinuePreloading(true, true));
        assertFalse(AutoLoadControl.shouldContinuePreloading(false, false));
    }
}
