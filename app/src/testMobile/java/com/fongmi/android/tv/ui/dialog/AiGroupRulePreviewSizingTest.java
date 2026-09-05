package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AiGroupRulePreviewSizingTest {

    @Test
    public void landscapePreviewUsesMostOfAvailableWidthAndHeight() {
        AiGroupRulePreviewSizing.Size size = AiGroupRulePreviewSizing.calculate(1920, 1080, 36, 255);

        assertTrue(size.width() >= 1760);
        assertTrue(size.width() <= 1920 - 36 * 2);
        assertTrue(size.dialogHeight() >= 960);
        assertTrue(size.dialogHeight() <= 1080 - 36 * 2);
        assertTrue(size.contentHeight() >= 700);
        assertTrue(size.contentHeight() <= 1080 - 255);
    }

    @Test
    public void portraitPreviewKeepsMarginsAndFitsReservedTitleAndActions() {
        AiGroupRulePreviewSizing.Size size = AiGroupRulePreviewSizing.calculate(1080, 2400, 36, 570);

        assertTrue(size.width() >= 990);
        assertTrue(size.width() <= 1080 - 36 * 2);
        assertTrue(size.dialogHeight() >= 2100);
        assertTrue(size.dialogHeight() <= 2400 - 36 * 2);
        assertTrue(size.contentHeight() >= 1500);
        assertTrue(size.contentHeight() <= 2400 - 570);
    }
}
