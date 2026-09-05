package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeSeasonSegmentsTest {

    /** 航海王实测数据：源站 1114 集，TMDB 24 季（含特别篇 season 0 = 39 集）。 */
    private static Map<Integer, Integer> onePieceCounts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        int[] values = {39, 61, 16, 14, 39, 13, 52, 33, 35, 73, 45, 26, 14, 101, 58, 62, 50, 56, 55, 74, 14, 197, 67, 26};
        for (int season = 0; season < values.length; season++) counts.put(season, values[season]);
        return counts;
    }

    private static List<Integer> onePieceSeasons() {
        return List.copyOf(onePieceCounts().keySet());
    }

    @Test
    public void splitsEvenlyWhenSeasonCountsExactlyCoverSource() {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(6, List.of(1, 2), Map.of(1, 3, 2, 3));

        assertEquals(2, segments.size());
        assertEquals(new EpisodeSeasonSegments.Segment(1, 0, 3), segments.get(0));
        assertEquals(new EpisodeSeasonSegments.Segment(2, 3, 6), segments.get(1));
    }

    /**
     * 源集数多于各季之和时，多出来的集必须单独成段。EpisodeSeasonPolicy.mapFlatEpisodeNumber
     * 在这种情况下会对超出部分返回 null，那些集就会没有任何归属。
     */
    @Test
    public void surplusSourceEpisodesFallIntoOtherSegment() {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(8, List.of(1, 2), Map.of(1, 3, 2, 3));

        assertEquals(3, segments.size());
        EpisodeSeasonSegments.Segment other = segments.get(2);
        assertTrue(EpisodeSeasonSegments.isOther(other.season()));
        assertEquals(6, other.start());
        assertEquals(8, other.end());
    }

    /** season 0（特别篇）不参与切分，否则会把正片集号整体前移一季。 */
    @Test
    public void specialsSeasonIsExcludedFromSlicing() {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(6, List.of(0, 1, 2), Map.of(0, 5, 1, 3, 2, 3));

        assertEquals(2, segments.size());
        assertEquals(1, segments.get(0).season());
        assertEquals(2, segments.get(1).season());
    }

    @Test
    public void lastSegmentIsClampedToSourceEpisodeCount() {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(4, List.of(1, 2), Map.of(1, 3, 2, 3));

        assertEquals(2, segments.size());
        assertEquals(new EpisodeSeasonSegments.Segment(2, 3, 4), segments.get(1));
        assertFalse("末段不能越过源集数",
                segments.get(segments.size() - 1).end() > 4);
    }

    @Test
    public void singleSeasonYieldsNoSegmentsSoUiHidesSeasonChips() {
        assertTrue(EpisodeSeasonSegments.build(10, List.of(1), Map.of(1, 10)).isEmpty());
        assertTrue(EpisodeSeasonSegments.build(0, List.of(1, 2), Map.of(1, 3, 2, 3)).isEmpty());
        assertTrue(EpisodeSeasonSegments.build(10, null, null).isEmpty());
    }

    @Test
    public void onePieceRealDataCoversEveryEpisodeExactlyOnce() {
        int episodeCount = 1114;
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(episodeCount, onePieceSeasons(), onePieceCounts());

        assertFalse(segments.isEmpty());
        int cursor = 0;
        for (EpisodeSeasonSegments.Segment segment : segments) {
            assertEquals("分段必须首尾相接，不留空洞也不重叠", cursor, segment.start());
            assertTrue(segment.end() > segment.start());
            cursor = segment.end();
        }
        assertEquals("分段必须完整覆盖所有源集", episodeCount, cursor);

        for (int position = 0; position < episodeCount; position++) {
            assertTrue("第 " + position + " 集必须归属某个分段",
                    EpisodeSeasonSegments.indexOf(segments, position) >= 0);
        }
    }

    @Test
    public void sliceReturnsEpisodesOfRequestedSeason() {
        List<String> items = List.of("a", "b", "c", "d", "e", "f");
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(items.size(), List.of(1, 2), Map.of(1, 2, 2, 4));

        assertEquals(List.of("a", "b"), EpisodeSeasonSegments.slice(items, segments, 1));
        assertEquals(List.of("c", "d", "e", "f"), EpisodeSeasonSegments.slice(items, segments, 2));
        assertEquals(List.of(), EpisodeSeasonSegments.slice(items, segments, 99));
    }

    @Test
    public void sizesKeepSegmentOrder() {
        List<EpisodeSeasonSegments.Segment> segments =
                EpisodeSeasonSegments.build(8, List.of(1, 2), Map.of(1, 3, 2, 3));
        Map<Integer, Integer> sizes = EpisodeSeasonSegments.sizes(segments);

        assertEquals(List.of(1, 2, EpisodeSeasonSegments.OTHER_SEASON), List.copyOf(sizes.keySet()));
        assertEquals(Integer.valueOf(2), sizes.get(EpisodeSeasonSegments.OTHER_SEASON));
    }
}
