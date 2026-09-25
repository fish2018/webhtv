package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TmdbDetailModeControllerTest {

    @Test
    public void concreteControllers_implementModeControllerContract() {
        assertTrue(TmdbDetailModeController.class.isAssignableFrom(FusionDetailController.class));
        assertTrue(TmdbDetailModeController.class.isAssignableFrom(EnhancedDetailController.class));
        assertTrue(TmdbDetailModeController.class.isAssignableFrom(PlayerDetailController.class));
    }
}
