package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChoiceDialogTest {

    @Test
    public void keepsFocusedItemInsideViewportWhenMovingDown() {
        assertEquals(84, ChoiceDialog.calculateItemScrollY(0, 240, 540, 270, 324));
        assertEquals(262, ChoiceDialog.calculateItemScrollY(84, 240, 540, 448, 502));
    }

    @Test
    public void scrollsBackWhenMovingUpAndClampsToContent() {
        assertEquals(120, ChoiceDialog.calculateItemScrollY(180, 240, 540, 120, 174));
        assertEquals(0, ChoiceDialog.calculateItemScrollY(20, 240, 200, 0, 54));
        assertEquals(300, ChoiceDialog.calculateItemScrollY(999, 240, 540, 300, 354));
    }

    @Test
    public void doesNotScrollAlreadyVisibleItems() {
        assertEquals(40, ChoiceDialog.calculateItemScrollY(40, 240, 540, 80, 134));
    }
}
