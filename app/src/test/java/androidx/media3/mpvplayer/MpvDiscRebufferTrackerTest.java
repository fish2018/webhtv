package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.*;

public class MpvDiscRebufferTrackerTest {
    @Test
    public void prolongedDemandStarvationCountsOnceAndIncludesActiveDuration() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(1000, true, 4000, 0, 0);
        tracker.update(1200, true, 4000, 0, 200);
        assertEquals(0, tracker.count());
        tracker.update(1300, true, 4000, 0, 300);
        tracker.update(1600, true, 4000, 0, 600);
        assertEquals(1, tracker.count());
        assertEquals(600, tracker.totalMs(1600));
        tracker.update(1700, true, 4050, 150, 0);
        assertFalse(tracker.active());
        assertEquals(700, tracker.totalMs(3000));
    }

    @Test
    public void stillMenusWithoutPendingDemandAreNotRebuffers() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(0, true, 1000, 0, 0);
        tracker.update(60_000, true, 1000, 0, 0);
        assertEquals(0, tracker.count());
    }

    @Test
    public void LowBufferAndBackgroundPrefetchWhilePlayingAreNotStalls() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        for (int time = 0; time < 10_000; time += 100) {
            tracker.update(time, true, time, 0, 1500);
        }
        assertEquals(0, tracker.count());
    }

    @Test
    public void bufferedFramesPreventCountingSourceWaitAsRebuffer() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(0, true, 100, 1000, 0);
        tracker.update(1000, true, 100, 1000, 1000);
        assertEquals(0, tracker.count());
    }

    @Test
    public void startupPauseSeekAndInactiveSourcesAreExcluded() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(0, false, 0, 0, 0);
        tracker.update(4000, false, 0, 0, 4000);
        tracker.update(5000, true, 0, 0, 5000);
        assertEquals(0, tracker.count());
        tracker.update(5100, true, 20, 0, 0);
        assertEquals(0, tracker.count());
    }

    @Test
    public void recoveryAndAnotherStallCountSeparatelyAndResetClearsSession() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(0, true, 10, 0, 0);
        tracker.update(300, true, 10, 0, 300);
        tracker.update(400, true, 10, 0, 0);
        tracker.update(500, true, 20, 0, 0);
        tracker.update(800, true, 20, 0, 300);
        assertEquals(2, tracker.count());
        tracker.interrupt(900);
        assertEquals(800, tracker.totalMs(2000));
        tracker.reset();
        assertEquals(0, tracker.count());
        assertEquals(0, tracker.totalMs(3000));
    }

    @Test
    public void playlistPositionDiscontinuityDoesNotAccumulateOldStallTime() {
        MpvDiscRebufferTracker tracker = new MpvDiscRebufferTracker();
        tracker.update(0, true, 20_000, 0, 0);
        tracker.update(5000, true, 0, 0, 5000);
        assertEquals(0, tracker.count());
        tracker.update(5100, true, 100, 0, 0);
        assertEquals(0, tracker.count());
    }
}
