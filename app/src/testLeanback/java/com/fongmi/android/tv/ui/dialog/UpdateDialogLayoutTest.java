package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateDialogLayoutTest {

    private static final int SCREEN_HEIGHT = 1080;
    private static final int NORMAL_MIN_HEIGHT = 220;
    private static final int NORMAL_MAX_HEIGHT = 320;

    @Test
    public void normalStateKeepsOriginalScrollHeight() {
        int height = UpdateDialogLayout.calculatePreferredScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT);

        assertEquals(NORMAL_MAX_HEIGHT, height);
    }

    @Test
    public void scrollHeightLeavesRoomForDialogChromeAndBottomAction() {
        int height = UpdateDialogLayout.fitScrollHeight(742, 1080, 325, 324);

        assertEquals(431, height);
    }

    @Test
    public void scrollHeightKeepsPreferredSizeWhenThereIsEnoughRoom() {
        int height = UpdateDialogLayout.fitScrollHeight(320, 1080, 300, 96);

        assertEquals(320, height);
    }

    @Test
    public void downloadStateKeepsPreferredHeightWhenWindowHasRoom() {
        int preferredHeight = UpdateDialogLayout.calculatePreferredScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT);
        int fittedHeight = UpdateDialogLayout.fitScrollHeight(preferredHeight, 900, 480, 90);

        assertEquals(NORMAL_MAX_HEIGHT, fittedHeight);
    }

    @Test
    public void smallWindowStillShrinksTheListForProgressAndActions() {
        int preferredHeight = UpdateDialogLayout.calculatePreferredScrollHeight(
                SCREEN_HEIGHT,
                NORMAL_MIN_HEIGHT,
                NORMAL_MAX_HEIGHT);
        int fittedHeight = UpdateDialogLayout.fitScrollHeight(preferredHeight, 600, 400, 50);

        assertEquals(150, fittedHeight);
    }

    @Test
    public void unchangedProgressPanelHeightSkipsRelayout() {
        assertFalse(UpdateDialogLayout.hasProgressPanelHeightChanged(80, 80));
    }

    @Test
    public void changedProgressPanelHeightTriggersRelayout() {
        assertTrue(UpdateDialogLayout.hasProgressPanelHeightChanged(-1, 80));
        assertTrue(UpdateDialogLayout.hasProgressPanelHeightChanged(80, 112));
    }

    @Test
    public void smallScreenUsesMoreWidthForUpdateContent() {
        assertEquals(604, UpdateDialogLayout.calculateDialogWidth(720, 960, 96));
    }

    @Test
    public void smallScreenUsesAProportionalVerticalMargin() {
        assertEquals(48, UpdateDialogLayout.calculateVerticalMargin(480, 96));
        assertEquals(96, UpdateDialogLayout.calculateVerticalMargin(1080, 96));
    }

    @Test
    public void availableHeightUsesTheTightestKnownWindowBounds() {
        assertEquals(900, UpdateDialogLayout.resolveAvailableHeight(1080, 900, 960));
        assertEquals(720, UpdateDialogLayout.resolveAvailableHeight(1080, 0, 720));
    }

}
