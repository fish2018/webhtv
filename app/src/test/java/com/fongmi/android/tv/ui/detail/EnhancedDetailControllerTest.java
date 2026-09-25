package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class EnhancedDetailControllerTest {

    @Test
    public void enhancedMode_hasNoInlinePlayerOrAutoPlay() {
        TmdbDetailModeController controller = new EnhancedDetailController(null);

        assertFalse(controller.shouldShowInlinePlayer());
        assertFalse(controller.shouldAutoPlay());
    }
}
