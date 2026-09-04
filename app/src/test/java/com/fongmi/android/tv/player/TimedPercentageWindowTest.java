package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimedPercentageWindowTest {

    @Test
    public void computesTimeWeightedAverageAndPrunesOldSamples() {
        TimedPercentageWindow window = new TimedPercentageWindow(10_000);
        window.add(1_000, 0, 10);
        window.add(5_000, 4_000, 20);
        window.add(12_000, 7_000, 5);

        TimedPercentageWindow.Stats stats = window.snapshot(12_000);

        assertTrue(stats.available());
        assertEquals(5, stats.current(), 0.001);
        assertEquals(9.5, stats.average(), 0.001);
        assertEquals(20, stats.peak(), 0.001);
        assertEquals(2, stats.sampleCount());
    }

    @Test
    public void resetClearsAllSamples() {
        TimedPercentageWindow window = new TimedPercentageWindow(10_000);
        window.add(1_000, 1_000, 50);
        window.reset();

        assertFalse(window.snapshot(1_000).available());
    }
}
