package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExoBufferingStallWatchdogTest {

    private static final long TIMEOUT = ExoBufferingStallWatchdog.STALL_TIMEOUT_MS;
    private static final long STALL = ExoBufferingStallWatchdog.STALL_TIMEOUT_MS;
    private static final long SEGMENT_PROGRESS_MARGIN_MS =
            ExoBufferingStallWatchdog.SEGMENT_PROGRESS_MARGIN_MS;
    private static final long LOADING_TIMEOUT = ExoBufferingStallWatchdog.LOADING_STALL_TIMEOUT_MS;

    @Test
    public void staysQuietUntilArmed() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        assertFalse(watchdog.isArmed());
        assertFalse(watchdog.shouldTimeout(LOADING_TIMEOUT * 10, 0, 0, false));
    }

    @Test
    public void timesOutOnlyWhenNeitherPositionNorBufferAdvances() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        assertFalse(watchdog.shouldTimeout(TIMEOUT - 1, 5_000, 9_000, false));
        assertTrue(watchdog.shouldTimeout(TIMEOUT, 5_000, 9_000, false));
    }

    @Test
    public void growingBufferIsProgressEvenWhilePositionStandsStill() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // A normal rebuffer: position frozen, buffered end still climbing.
        watchdog.observe(TIMEOUT - 1, 5_000, 12_000);
        assertFalse(watchdog.shouldTimeout(TIMEOUT, 5_000, 12_000, false));
        assertTrue(watchdog.shouldTimeout(TIMEOUT * 2 - 1, 5_000, 12_000, false));
    }

    @Test
    public void advancingPositionIsProgressEvenWhileBufferStandsStill() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        watchdog.observe(TIMEOUT - 1, 7_000, 9_000);
        assertFalse(watchdog.shouldTimeout(TIMEOUT, 7_000, 9_000, false));
    }

    @Test
    public void loadingSourceGetsTheLongerCeiling() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // A remote Matroska Cues fetch produces no samples yet is not stalled.
        assertFalse(watchdog.shouldTimeout(TIMEOUT, 5_000, 9_000, true));
        assertFalse(watchdog.shouldTimeout(LOADING_TIMEOUT - 1, 5_000, 9_000, true));
        assertTrue(watchdog.shouldTimeout(LOADING_TIMEOUT, 5_000, 9_000, true));
    }

    @Test
    public void loadingCeilingStaysBounded() {
        // A hung socket read keeps loading true forever; it must still trip.
        assertTrue(LOADING_TIMEOUT > TIMEOUT);
        assertTrue(LOADING_TIMEOUT < Long.MAX_VALUE);
    }

    @Test
    public void resetDisarms() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        watchdog.reset();
        assertFalse(watchdog.isArmed());
        assertFalse(watchdog.shouldTimeout(LOADING_TIMEOUT * 10, 5_000, 9_000, false));
    }

    @Test
    public void observeArmsWhenNotYetArmed() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.observe(1_000, 5_000, 9_000);
        assertTrue(watchdog.isArmed());
        assertFalse(watchdog.shouldTimeout(1_000 + TIMEOUT - 1, 5_000, 9_000, false));
        assertTrue(watchdog.shouldTimeout(1_000 + TIMEOUT, 5_000, 9_000, false));
    }

    @Test
    public void timeoutMustOutlastMaxRebufferThreshold() {
        // A LoadControl still filling its rebuffer threshold must never be killed.
        assertTrue(ExoBufferingStallWatchdog.STALL_TIMEOUT_MS
                > ExoPlaybackThresholdPolicy.MAX_STREAMING_REBUFFER_MS);
    }

    @Test
    public void regressedSampleRearmsBecauseItIsADiscontinuity() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 300_000, 320_000);
        // A backward seek drops position and buffered end well below the baseline. Treating
        // that as "no progress" would make every later sample satisfy the criterion and time
        // out a session that merely jumped, so the clock must restart on the lower baseline.
        watchdog.observe(TIMEOUT / 2, 60_000, 62_000);
        assertFalse(watchdog.shouldTimeout(TIMEOUT, 60_000, 62_000, false));
        // Still stalled from the new baseline onward, so it must fire eventually.
        assertTrue(watchdog.shouldTimeout(TIMEOUT / 2 + TIMEOUT, 60_000, 62_000, false));
    }

    @Test
    public void repeatingLargeRegressionCannotDeferTheTimeoutForever() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // Alternating growth and a >tolerance regression rebaselines the clock every tick.
        // Without an episode ceiling this defers the timeout indefinitely while the position
        // stays frozen, which is exactly the stall this class exists to catch.
        long now = 0;
        boolean fired = false;
        for (int i = 0; i < 300 && !fired; i++) {
            now += 1_000;
            long buffered = i % 2 == 0 ? 12_000 : 10_000;
            if (watchdog.shouldTimeout(now, 5_000, buffered, false)) {
                fired = true;
                break;
            }
            watchdog.observe(now, 5_000, buffered);
        }
        assertTrue("episode ceiling must bound a repeating regression cycle", fired);
        assertTrue(now <= ExoBufferingStallWatchdog.EPISODE_CEILING_MS + 2_000);
    }

    @Test
    public void ceilingSparesAGenuinelyProgressingEpisode() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // A large file on a slow link can spend well past the ceiling steadily filling its
        // rebuffer threshold. The ceiling must not kill it just because time elapsed.
        long now = 0;
        long buffered = 9_000;
        while (now < ExoBufferingStallWatchdog.EPISODE_CEILING_MS + 2_000) {
            now += 1_000;
            buffered += 200;
            assertFalse("fired at " + now + "ms despite progress",
                    watchdog.shouldTimeout(now, 5_000, buffered, true));
            watchdog.observe(now, 5_000, buffered);
        }
    }

    @Test
    public void ceilingSparesProgressAfterABackwardSeek() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        // Start high, then seek far back. If the progress watermarks stayed at the pre-seek
        // values, every later sample would read as zero progress (clamped) and the ceiling
        // would kill a session that is actually advancing from the new position.
        watchdog.arm(0, 600_000, 620_000);
        watchdog.observe(1_000, 30_000, 32_000);
        long position = 30_000;
        long buffered = 32_000;
        for (long now = 2_000; now < ExoBufferingStallWatchdog.EPISODE_CEILING_MS + 5_000;
                now += 1_000) {
            position += 1_000;
            buffered += 1_000;
            assertFalse("fired at " + now + "ms after a backward seek despite progress",
                    watchdog.shouldTimeout(now, position, buffered, false));
            watchdog.observe(now, position, buffered);
        }
    }

    @Test
    public void stallAfterABackwardSeekStillFires() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 600_000, 620_000);
        watchdog.observe(1_000, 30_000, 32_000);
        // Frozen from the new position onward, so the normal criterion must still report it.
        assertTrue(watchdog.shouldTimeout(1_000 + STALL, 30_000, 32_000, false));
    }

    @Test
    public void ceilingStillFiresWhenNetProgressIsBelowTheMargin() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // Movement exists but is far below the margin, so this is not a working session.
        long now = ExoBufferingStallWatchdog.EPISODE_CEILING_MS;
        long buffered = 9_000 + ExoBufferingStallWatchdog.SEGMENT_PROGRESS_MARGIN_MS - 1_000;
        assertTrue(watchdog.shouldTimeout(now, 5_000, buffered, true));
    }

    @Test
    public void oscillationWhollyAboveTheArmWatermarkStillTerminates() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // The severity case: a mid-playback rebuffer arms with buffered already high, and the
        // oscillation sits entirely above arm + margin. If the progress watermarks stayed pinned
        // to arm(), net progress would read above the margin on every sample and the ceiling
        // would be deferred forever — the permanent spinner this class exists to catch.
        long trough = 9_000 + SEGMENT_PROGRESS_MARGIN_MS + 30_000;
        long peak = trough + 20_000;
        long now = 0;
        boolean fired = false;
        for (int i = 0; i < 400 && !fired; i++) {
            now += 1_000;
            long buffered = i % 2 == 0 ? peak : trough;
            if (watchdog.shouldTimeout(now, 5_000, buffered, false)) {
                fired = true;
                break;
            }
            watchdog.observe(now, 5_000, buffered);
        }
        assertTrue("a bounded oscillation above the arm watermark must still terminate", fired);
    }

    @Test
    public void episodeCeilingDoesNotCountPausedTime() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        // A paused session re-arms every tick, so the ceiling never accumulates.
        long now = 0;
        for (int i = 0; i < 300; i++) {
            now += 1_000;
            watchdog.arm(now, 5_000, 9_000);
            assertFalse(watchdog.shouldTimeout(now, 5_000, 9_000, false));
        }
        // Resuming gets a full window rather than an immediately-expired one.
        assertFalse(watchdog.shouldTimeout(now + STALL - 1, 5_000, 9_000, false));
        assertTrue(watchdog.shouldTimeout(now + STALL, 5_000, 9_000, false));
    }

    @Test
    public void toleranceDipWithGrowthKeepsTheHigherBaseline() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_300);
        // Position grows while buffered dips within tolerance: the growth branch runs, and it
        // must not lower the buffered baseline, or the later rebound would count as progress
        // and reset the clock on every sawtooth.
        watchdog.observe(1_000, 6_000, 9_000);
        watchdog.observe(2_000, 6_000, 9_300);
        // The rebound was not progress, so the deadline still derives from the 1s sample.
        assertTrue(watchdog.shouldTimeout(1_000 + STALL, 6_000, 9_300, false));
    }

    @Test
    public void oscillatingBufferedEndStillTimesOut() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        // Buffered end dithers within the jitter tolerance while position stays frozen.
        // Re-arming on each dip would reset the clock forever and never report the stall.
        long now = 0;
        for (int i = 0; i < 40; i++) {
            now += 1_000;
            watchdog.observe(now, 5_000, i % 2 == 0 ? 9_300 : 9_000);
        }
        assertTrue(watchdog.shouldTimeout(now, 5_000, 9_000, false));
    }

    @Test
    public void jitterBelowToleranceDoesNotRearm() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 5_000, 9_000);
        watchdog.observe(1_000, 5_000, 9_000 - (ExoBufferingStallWatchdog.DISCONTINUITY_TOLERANCE_MS - 1));
        // Clock kept running from the original arm, so the original deadline still applies.
        assertTrue(watchdog.shouldTimeout(TIMEOUT, 5_000, 9_000, false));
    }

    @Test
    public void aBackwardSeekDoesNotInheritTheOldBaseline() {
        ExoBufferingStallWatchdog watchdog = new ExoBufferingStallWatchdog();
        watchdog.arm(0, 600_000, 620_000);
        // Seek from 10:00 back to 00:30 while buffering.
        watchdog.observe(1_000, 30_000, 32_000);
        assertFalse(watchdog.shouldTimeout(1_000 + TIMEOUT - 1, 30_000, 32_000, false));
        // Progress from the new baseline keeps it quiet.
        watchdog.observe(1_000 + TIMEOUT - 1, 31_000, 45_000);
        assertFalse(watchdog.shouldTimeout(1_000 + TIMEOUT, 31_000, 45_000, false));
    }
}
