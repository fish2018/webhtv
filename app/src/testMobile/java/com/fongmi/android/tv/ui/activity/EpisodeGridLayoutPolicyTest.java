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

    @Test
    public void originalEnhancedFallbackUsesOneColumnForSingleEpisode() {
        assertEquals(1, EpisodeGridLayoutPolicy.getOriginalEnhancedFallbackSpan(1, 40, 2));
    }

    @Test
    public void originalEnhancedFallbackUsesThreeColumnsForShortTitles() {
        assertEquals(3, EpisodeGridLayoutPolicy.getOriginalEnhancedFallbackSpan(12, 11, 2));
    }

    @Test
    public void originalEnhancedFallbackHonoursTheUserEpisodeColumnForLongTitles() {
        assertEquals(2, EpisodeGridLayoutPolicy.getOriginalEnhancedFallbackSpan(12, 12, 2));
        assertEquals(1, EpisodeGridLayoutPolicy.getOriginalEnhancedFallbackSpan(12, 48, 1));
    }
}
