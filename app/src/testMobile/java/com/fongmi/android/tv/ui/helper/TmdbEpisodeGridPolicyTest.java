package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeGridPolicyTest {

    @Test
    public void layout_wrapsWhenListModeOrShortGrid() {
        assertEquals(TmdbEpisodeGridPolicy.WRAP_CONTENT, TmdbEpisodeGridPolicy.layout(false, 120, 2, 128).heightPx());
        assertEquals(TmdbEpisodeGridPolicy.WRAP_CONTENT, TmdbEpisodeGridPolicy.layout(true, 6, 2, 128).heightPx());
        assertFalse(TmdbEpisodeGridPolicy.layout(true, 6, 2, 128).nestedScrolling());
    }

    @Test
    public void layout_limitsLongGridToThreeRows() {
        TmdbEpisodeGridPolicy.Layout layout = TmdbEpisodeGridPolicy.layout(true, 80, 2, 128);

        assertEquals(384, layout.heightPx());
        assertTrue(layout.nestedScrolling());
    }

    @Test
    public void tvAdaptiveSpanCountUsesFourColumnsOn1080pTvDensity() {
        assertEquals(3, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(599));
        assertEquals(4, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(686));
        assertEquals(5, TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(1100));
    }

    @Test
    public void verticalFocusTargetMovesBySpanInsideGrid() {
        assertEquals(4, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 4, 20, true));
        assertEquals(8, TmdbEpisodeGridPolicy.verticalFocusTarget(4, 4, 20, true));
        assertEquals(0, TmdbEpisodeGridPolicy.verticalFocusTarget(4, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 4, 20, false));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(16, 4, 20, true));
        assertEquals(5, TmdbEpisodeGridPolicy.verticalFocusTarget(3, 4, 6, true));
    }

    @Test
    public void verticalFocusTargetReachesShortLastRowFromEveryColumn() {
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(0, 3, 4, true));
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(1, 3, 4, true));
        assertEquals(3, TmdbEpisodeGridPolicy.verticalFocusTarget(2, 3, 4, true));
        assertEquals(TmdbEpisodeGridPolicy.NO_FOCUS_TARGET, TmdbEpisodeGridPolicy.verticalFocusTarget(3, 3, 4, true));
    }

    @Test
    public void shouldUseFallbackImageOnlyForLegacySmallGridPolicy() {
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(false, 120));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 24));
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 25));
    }

    @Test
    public void shouldUseFallbackImageForAnyEpisodeInGridMode() {
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 100, false));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 100, true));
        assertFalse(TmdbEpisodeGridPolicy.shouldUseFallbackImage(false, 100, false));
    }
}
