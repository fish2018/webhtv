package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeSeasonPolicyTest {

    @Test
    public void sliceBySeasonCounts_slicesOnlyWhenCountsExactlyCoverSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertTrue(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(List.of(4, 5, 6), EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenTmdbCountsWouldDropSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6, 7);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenSeasonCountIsMissing() {
        List<Integer> episodes = List.of(1, 2, 3, 4);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 2);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_ignoresSpecialsWhenOrdinarySeasonsCoverSourceEpisodes() {
        List<Integer> episodes = java.util.stream.IntStream.rangeClosed(1, 18).boxed().toList();
        List<Integer> seasons = List.of(0, 1, 2);
        Map<Integer, Integer> counts = Map.of(0, 3, 1, 10, 2, 8);

        assertEquals(List.of(1, 2), EpisodeSeasonPolicy.sliceableSeasons(seasons));
        assertTrue(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(List.of(11, 12, 13, 14, 15, 16, 17, 18),
                EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void flatEpisodeMapping_ignoresSpecialsWhenMappingAbsoluteNumbers() {
        List<Integer> seasons = List.of(0, 1, 2);
        Map<Integer, Integer> counts = Map.of(0, 3, 1, 10, 2, 8);
        List<Integer> sourceNumbers = java.util.stream.IntStream.rangeClosed(1, 18).boxed().toList();

        assertTrue(EpisodeSeasonPolicy.canMapFlatEpisodeNumbers(sourceNumbers, seasons, counts));
        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(2, 1),
                EpisodeSeasonPolicy.mapFlatEpisodeNumber(11, seasons, counts));
    }
    @Test
    public void shouldUseSingleSeasonEpisodeData_whenFirstTmdbSeasonCoversSourceEpisodes() {
        List<Integer> seasons = List.of(1, 2, 3);
        Map<Integer, Integer> counts = Map.of(1, 190, 2, 8, 3, 8);

        assertTrue(EpisodeSeasonPolicy.shouldUseSingleSeasonEpisodeData(161, 1, seasons, counts));
    }

    @Test
    public void shouldUseSingleSeasonEpisodeData_keepsExactMultiSeasonMapping() {
        List<Integer> seasons = List.of(1, 2, 3);
        Map<Integer, Integer> counts = Map.of(1, 40, 2, 40, 3, 40);

        assertFalse(EpisodeSeasonPolicy.shouldUseSingleSeasonEpisodeData(120, 1, seasons, counts));
    }

    @Test
    public void flatEpisodeMapping_requiresCompleteContinuousSequence() {
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 10, 2, 12);

        assertTrue(EpisodeSeasonPolicy.canMapFlatEpisodeNumbers(
                java.util.stream.IntStream.rangeClosed(1, 22).boxed().toList(), seasons, counts));
        assertFalse(EpisodeSeasonPolicy.canMapFlatEpisodeNumbers(
                List.of(1, 2, 3, 5), seasons, Map.of(1, 2, 2, 2)));
        assertFalse(EpisodeSeasonPolicy.canMapFlatEpisodeNumbers(
                List.of(1, 2, 2, 4), seasons, Map.of(1, 2, 2, 2)));
    }

    @Test
    public void flatEpisodeKeyMapping_acceptsGapsDuplicatesAndOutOfOrderNumbers() {
        List<Integer> seasons = List.of(0, 1, 2);
        Map<Integer, Integer> counts = Map.of(0, 3, 1, 2, 2, 2);
        List<Integer> sourceNumbers = List.of(4, 1, 2, 2, 0, -1, 5);

        assertTrue(EpisodeSeasonPolicy.canMapFlatEpisodeKeys(sourceNumbers, seasons, counts));
        assertEquals(List.of(1, 2),
                EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(sourceNumbers, seasons, counts));
        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(2, 2),
                EpisodeSeasonPolicy.mapFlatEpisodeNumber(4, seasons, counts));
    }

    @Test
    public void flatEpisodeKeyMapping_skipsUnmappableNumbersWithoutGuessingByPosition() {
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 2, 2, 2);

        assertFalse(EpisodeSeasonPolicy.canMapFlatEpisodeKeys(List.of(0, -1, 5), seasons, counts));
        assertEquals(List.of(),
                EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(List.of(0, -1, 5), seasons, counts));
    }

    @Test
    public void flatEpisodeMapping_mapsAbsoluteNumberToSeasonLocalNumber() {
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 10, 2, 12);

        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(1, 1), EpisodeSeasonPolicy.mapFlatEpisodeNumber(1, seasons, counts));
        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(1, 10), EpisodeSeasonPolicy.mapFlatEpisodeNumber(10, seasons, counts));
        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(2, 1), EpisodeSeasonPolicy.mapFlatEpisodeNumber(11, seasons, counts));
        assertEquals(new EpisodeSeasonPolicy.SeasonEpisode(2, 12), EpisodeSeasonPolicy.mapFlatEpisodeNumber(22, seasons, counts));
        assertEquals(null, EpisodeSeasonPolicy.mapFlatEpisodeNumber(23, seasons, counts));
    }

    @Test
    public void linearEpisodeNumber_trustsSourceNumberRegardlessOfPosition() {
        // 新逻辑：文件名有集号时，直接使用它（真实场景：S01E01 在 index=17）
        assertEquals(1, EpisodeSeasonPolicy.linearEpisodeNumber(1, 17));
    }

    @Test
    public void linearEpisodeNumber_keepsAbsoluteSourceNumberForPagedRanges() {
        assertEquals(41, EpisodeSeasonPolicy.linearEpisodeNumber(41, 0));
    }

    @Test
    public void resolveAvailableSeasons_singleTmdbSeasonUsesOnlyThatSeason() {
        assertEquals(List.of(4), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1), -1, 4, List.of(4), Map.of(4, 8)));
    }

    @Test
    public void resolveAvailableSeasons_explicitSingleSeasonHidesUnrelatedTmdbSeasons() {
        assertEquals(List.of(3), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(3), -1, 1, List.of(1, 2, 3), Map.of(1, 8, 2, 8, 3, 8)));
    }

    @Test
    public void resolveAvailableSeasons_explicitMultipleSeasonsReturnsOnlySourceSeasons() {
        assertEquals(List.of(1, 3), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(3, 1, 3, 1), -1, 1, List.of(1, 2, 3), Map.of(1, 2, 2, 2, 3, 2)));
    }

    @Test
    public void resolveAvailableSeasons_explicitSpecialSeasonStaysOnSeasonZero() {
        assertEquals(List.of(0), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(0, 0), -1, 1, List.of(0, 1), Map.of(0, 2, 1, 2)));
    }

    @Test
    public void resolveAvailableSeasons_partialExplicitSingleSeasonKeepsKnownSeason() {
        assertEquals(List.of(1), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(1, 1, -1, 1), -1, 1, List.of(1), Map.of(1, 206)));
    }

    @Test
    public void resolveAvailableSeasons_partialExplicitSingleSeasonHidesUnrelatedTmdbSeasons() {
        assertEquals(List.of(2), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(2, -1), -1, 1, List.of(1, 2, 3), Map.of(1, 2, 2, 2, 3, 2)));
    }

    @Test
    public void resolveAvailableSeasons_partialExplicitSpecialSeasonStaysOnSeasonZero() {
        assertEquals(List.of(0), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(0, -1, 0), -1, 1, List.of(0, 1), Map.of(0, 2, 1, 2)));
    }

    @Test
    public void resolveAvailableSeasons_partialExplicitMultipleSeasonsStaysUngrouped() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(1, -1, 2), -1, 1, List.of(1, 2), Map.of(1, 2, 2, 2)));
    }

    @Test
    public void resolveAvailableSeasons_partialExplicitUnknownTmdbSeasonStaysUngrouped() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(2, -1), -1, 1, List.of(1), Map.of(1, 2)));
    }

    @Test
    public void resolveAvailableSeasons_unknownTmdbSeasonFallsBackToUngrouped() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(4, 4), -1, 1, List.of(1, 2, 3), Map.of(1, 2, 2, 2, 3, 2)));
    }

    @Test
    public void resolveAvailableSeasons_explicitSeasonMismatchBeatsSingleTmdbSeasonFallback() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(3), -1, 1, List.of(1), Map.of(1, 8)));
    }

    @Test
    public void resolveAvailableSeasons_titleSeasonMismatchBeatsSingleTmdbSeasonFallback() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1), 3, 1, List.of(1), Map.of(1, 8)));
    }

    @Test
    public void resolveAvailableSeasons_titleSeasonMapsWholeSourceToThatSeason() {
        assertEquals(List.of(2), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1, -1), 2, 1, List.of(1, 2, 3), Map.of(1, 3, 2, 3, 3, 3)));
    }

    @Test
    public void resolveAvailableSeasons_exactFullSeriesCountAllowsAllSeasons() {
        assertEquals(List.of(1, 2), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1, -1, -1, -1, -1), -1, 1, List.of(1, 2), Map.of(1, 3, 2, 3)));
    }

    @Test
    public void resolveAvailableSeasons_automaticKeyMappingUsesEpisodeNumbersWhenCountsAreIncomplete() {
        assertEquals(List.of(1, 2), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1, -1, -1, -1, -1, -1), -1, 1,
                List.of(1, 2), Map.of(1, 2, 2, 2),
                List.of(4, 1, 2, 2, 0, -1, 5)));
    }

    @Test
    public void resolveAvailableSeasons_doesNotExposeSingleTouchedSeasonFromKeys() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1, -1, -1, -1), -1, 1,
                List.of(1, 2), Map.of(1, 2, 2, 2),
                List.of(1, 2, 0, -1, 5)));
    }

    @Test
    public void resolveAvailableSeasons_ambiguousPartialLineDoesNotExposeAllTmdbSeasons() {
        assertEquals(List.of(), EpisodeSeasonPolicy.resolveAvailableSeasons(
                List.of(-1, -1, -1, -1, -1), -1, 1, List.of(1, 2), Map.of(1, 3, 2, 3)));
    }

    @Test
    public void resolveAvailableSeasons_keepsExistingSingleSeasonCompatibilityFallback() {
        assertEquals(List.of(1), EpisodeSeasonPolicy.resolveAvailableSeasons(
                java.util.Collections.nCopies(161, -1), -1, 1, List.of(1, 2, 3), Map.of(1, 190, 2, 8, 3, 8)));
    }

    @Test
    public void resolveSourceSeason_readsExplicitAndTrailingSeasonBeforeScrapedTitleReplacement() {
        assertEquals(2, EpisodeSeasonPolicy.resolveSourceSeason("克拉克森的农场 第2季"));
        assertEquals(3, EpisodeSeasonPolicy.resolveSourceSeason("Clarkson's Farm S03"));
        assertEquals(2, EpisodeSeasonPolicy.resolveSourceSeason("Clarksons.Farm.S02E05.2160p"));
        assertEquals(4, EpisodeSeasonPolicy.resolveSourceSeason("克拉克森的农场4"));
        assertEquals(0, EpisodeSeasonPolicy.resolveSourceSeason("示例剧 S00E05"));
        assertEquals(0, EpisodeSeasonPolicy.resolveSourceSeason("示例剧 Season 0"));
        assertEquals(0, EpisodeSeasonPolicy.resolveSourceSeason("示例剧 特别篇"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("克拉克森的农场"));
    }

    @Test
    public void resolveExplicitSourceSeason_ignoresLineOrdinalsAndPlainEpisodeNumbers() {
        assertEquals(-1, EpisodeSeasonPolicy.resolveExplicitSourceSeason("摆渡普画#02"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveExplicitSourceSeason("UC原画#03"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveExplicitSourceSeason("线路3"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveExplicitSourceSeason("109"));
    }

    @Test
    public void resolveExplicitSourceSeason_keepsActualSeasonMarkersAndSpecials() {
        assertEquals(2, EpisodeSeasonPolicy.resolveExplicitSourceSeason("摆渡普画 第2季"));
        assertEquals(3, EpisodeSeasonPolicy.resolveExplicitSourceSeason("UC原画 Season 3"));
        assertEquals(3, EpisodeSeasonPolicy.resolveExplicitSourceSeason("线路 S03"));
        assertEquals(0, EpisodeSeasonPolicy.resolveExplicitSourceSeason("特别篇"));
    }

    @Test
    public void resolveSourceSeason_ignoresReleaseDateLikeEpisodeNames() {
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("2026-07-02"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("2026-07-29"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("2026-08-05"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("20260702"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("2026-07-02.mp4"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("李熊猫 2026-07-02"));
        assertEquals(-1, EpisodeSeasonPolicy.resolveSourceSeason("李熊猫 20260702.H264"));
        assertEquals(2, EpisodeSeasonPolicy.resolveSourceSeason("庆余年2 2024-05-16"));
        assertEquals(2, EpisodeSeasonPolicy.resolveSourceSeason("庆余年2 20240516"));
    }

    @Test
    public void episodeMetadataSeasonCandidates_neverFallsBackToSeasonOneWhenSourceSeasonIsKnown() {
        assertEquals(List.of(2), EpisodeSeasonPolicy.episodeMetadataSeasonCandidates(2));
        assertEquals(List.of(0), EpisodeSeasonPolicy.episodeMetadataSeasonCandidates(0));
        assertEquals(List.of(), EpisodeSeasonPolicy.episodeMetadataSeasonCandidates(-1));
    }

    @Test
    public void episodePositionCacheKey_isolatesSameEpisodeLabelAcrossSeasons() {
        assertEquals("S2|第5集", EpisodeSeasonPolicy.episodePositionCacheKey(2, "第5集"));
        assertEquals("S2|第5集", EpisodeSeasonPolicy.episodePositionCacheKey(2, "S2|第5集"));
        assertEquals("S0|特别篇第5集", EpisodeSeasonPolicy.episodePositionCacheKey(0, "特别篇第5集"));
        assertEquals("第5集", EpisodeSeasonPolicy.episodePositionCacheKey(-1, "第5集"));
    }


    @Test
    public void hasCompleteExplicitSeasonMapping_requiresEveryEpisodeToMatchTmdb() {
        assertTrue(EpisodeSeasonPolicy.hasCompleteExplicitSeasonMapping(List.of(1, 3, 1), List.of(1, 2, 3)));
        assertTrue(EpisodeSeasonPolicy.hasCompleteExplicitSeasonMapping(List.of(0, 0), List.of(0, 1)));
        assertFalse(EpisodeSeasonPolicy.hasCompleteExplicitSeasonMapping(List.of(1, -1), List.of(1, 2, 3)));
        assertFalse(EpisodeSeasonPolicy.hasCompleteExplicitSeasonMapping(List.of(4), List.of(1, 2, 3)));
    }
}
