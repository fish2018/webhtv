package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpisodeRangePolicyTest {

    @Test
    public void build_usesNativeGroupSizeBreakpoints() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(501, 0, false);

        assertEquals(6, ranges.size());
        assertEquals("1-100", ranges.get(0).label());
        assertEquals(0, ranges.get(0).start());
        assertEquals(100, ranges.get(0).end());
        assertEquals("501", ranges.get(5).label());
    }

    @Test
    public void build_selectsRangeContainingSelectedIndex() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(120, 72, false);

        assertEquals("41-80", ranges.get(1).label());
        assertTrue(ranges.get(1).selected());
    }

    @Test
    public void build_formatsReverseLabelsAgainstOriginalEpisodeNumbers() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(120, 0, true);

        assertEquals("120-81", ranges.get(0).label());
        assertEquals("40-1", ranges.get(2).label());
    }

    @Test
    public void build_preservesGroupBoundariesInReverseMode() {
        // 38 episodes: forward groups are "1-20" and "21-38" with boundary at 20|21.
        // Reverse should maintain the same episode-number boundary: "38-21" and "20-1".
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(38, 0, true);

        assertEquals(2, ranges.size());
        assertEquals("38-21", ranges.get(0).label());
        assertEquals(0, ranges.get(0).start());
        assertEquals(18, ranges.get(0).end());
        assertEquals("20-1", ranges.get(1).label());
        assertEquals(18, ranges.get(1).start());
        assertEquals(38, ranges.get(1).end());
    }

    @Test
    public void build_capsCardPagesAtFiftyEpisodes() {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(501, 0, false, 50);

        assertEquals(11, ranges.size());
        assertEquals("1-50", ranges.get(0).label());
        assertEquals("451-500", ranges.get(9).label());
        assertEquals("501", ranges.get(10).label());
    }

    @Test
    public void gridFallbackImagesRemainAvailableForCardPagesWithoutEpisodeArtwork() {
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 50, false));
        assertTrue(TmdbEpisodeGridPolicy.shouldUseFallbackImage(true, 50, true));
    }

    @Test
    public void segmentIndex_selectsThePageContainingCurrentEpisode() {
        assertEquals(80, EpisodeRangePolicy.segmentStart(131, 82, 40));
        assertEquals(2, EpisodeRangePolicy.segmentIndex(131, 82, 40));
    }

    @Test
    public void segmentIndex_clampsMissingOrOutOfRangeSelection() {
        assertEquals(0, EpisodeRangePolicy.segmentIndex(0, -1, 40));
        assertEquals(0, EpisodeRangePolicy.segmentIndex(131, -1, 40));
        assertEquals(3, EpisodeRangePolicy.segmentIndex(131, 999, 40));
    }
    @Test
    public void slice_clampsRangeToItems() {
        List<Integer> items = List.of(1, 2, 3, 4);

        assertEquals(List.of(2, 3), EpisodeRangePolicy.slice(items, new EpisodeRangePolicy.Range("2-3", 1, 3, true)));
        assertEquals(List.of(), EpisodeRangePolicy.slice(items, null));
    }
}
