package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DetailThemeVisibilityTest {

    @Test
    public void mobileThemeButton_hidesDuringFullscreenAndPipOverlays() {
        assertTrue(DetailThemeVisibility.showMobileThemeButton(true, false, false, false));
        assertFalse(DetailThemeVisibility.showMobileThemeButton(true, true, false, false));
        assertFalse(DetailThemeVisibility.showMobileThemeButton(true, false, true, false));
        assertFalse(DetailThemeVisibility.showMobileThemeButton(true, false, false, true));
    }

    @Test
    public void largeScreenThemeButton_usesLargeScreenOnlyAndHidesDuringPlaybackOverlays() {
        assertTrue(DetailThemeVisibility.showLargeScreenThemeButton(false, false, false, false));
        assertFalse(DetailThemeVisibility.showLargeScreenThemeButton(true, false, false, false));
        assertFalse(DetailThemeVisibility.showLargeScreenThemeButton(false, true, false, false));
        assertFalse(DetailThemeVisibility.showLargeScreenThemeButton(false, false, true, false));
        assertFalse(DetailThemeVisibility.showLargeScreenThemeButton(false, false, false, true));
    }

    @Test
    public void fusionThemeButton_hidesWhenFullscreenOrPictureInPicture() {
        assertTrue(DetailThemeVisibility.showFusionThemeButton(true, false, false));
        assertFalse(DetailThemeVisibility.showFusionThemeButton(false, false, false));
        assertFalse(DetailThemeVisibility.showFusionThemeButton(true, true, false));
        assertFalse(DetailThemeVisibility.showFusionThemeButton(true, false, true));
    }
}
