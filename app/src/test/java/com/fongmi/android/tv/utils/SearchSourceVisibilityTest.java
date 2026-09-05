package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchSourceVisibilityTest {

    @Test
    public void sourceWithVisibleResultsIsAlwaysShown() {
        assertTrue(SearchSourceVisibility.shouldShow(true));
    }

    @Test
    public void emptySourceIsHiddenIncludingUnlimitedFixedOrderMode() {
        assertFalse(SearchSourceVisibility.shouldShow(false));
    }

    @Test
    public void everyFilteredModeHidesSourcesWithoutMatches() {
        assertFalse(SearchSourceVisibility.shouldShow(false));
    }

    @Test
    public void hiddenSourceAppearsAsSoonAsItGetsAMatch() {
        assertFalse(SearchSourceVisibility.shouldShow(false));
        assertTrue(SearchSourceVisibility.shouldShow(true));
    }
}
