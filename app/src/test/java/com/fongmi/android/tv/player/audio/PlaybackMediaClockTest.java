package com.fongmi.android.tv.player.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackMediaClockTest {

    @Test
    public void mapsCaptureTimeOnlyForFreshCurrentGeneration() {
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        clock.reset(7L, 30_000L);
        clock.onCaptured(7L, 2_000L);
        clock.onOutputSample(7L, 2_000_000L, 1_500_000L, 10_000L, true);
        PlaybackMediaClock.Snapshot snapshot = clock.snapshot(10_100L);
        assertEquals(31_600L, snapshot.presentedMediaPositionMs().orElseThrow());
        assertEquals(32_000L, snapshot.mapCaptureToMediaMs(2_000L).orElseThrow());
        assertEquals(45_000L, snapshot.mapCaptureToMediaMs(15_000L).orElseThrow());
        assertTrue(clock.snapshot(10_700L).presentedMediaPositionMs().isEmpty());
        clock.reset(8L, 90_000L);
        assertTrue(clock.snapshot(10_700L).mapCaptureToMediaMs(2_000L).isEmpty());
    }

    @Test
    public void clampsOutputPositionToWrittenFrames() {
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        clock.reset(1L, 0L);
        clock.onCaptured(1L, 1_000L);
        clock.onOutputSample(1L, 1_000_000L, 2_000_000L, 100L, true);
        assertEquals(1_000L, clock.snapshot(100L).presentedMediaPositionMs().orElseThrow());
    }
}
