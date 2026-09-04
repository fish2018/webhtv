package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EpisodeGridLayoutPolicyTest {

    @Test
    public void portraitLayoutKeepsPhoneSpanLimitWhileConfigurationIsLandscape() {
        assertEquals(4, EpisodeGridLayoutPolicy.getMaxSpan(false, false));
    }

    @Test
    public void portraitLayoutIgnoresFullscreenLandscapeMeasurement() {
        int width = EpisodeGridLayoutPolicy.getAvailableWidth(
                2400, 2400, 1080, 112, false, true);

        assertEquals(968, width);
    }

    @Test
    public void matchingOrientationUsesMeasuredRecyclerWidth() {
        int width = EpisodeGridLayoutPolicy.getAvailableWidth(
                1024, 1080, 2400, 112, false, false);

        assertEquals(1024, width);
    }

    @Test
    public void landscapeLayoutRetainsSixColumnLimit() {
        assertEquals(6, EpisodeGridLayoutPolicy.getMaxSpan(true, false));
        assertEquals(6, EpisodeGridLayoutPolicy.getMaxSpan(false, true));
    }
}
