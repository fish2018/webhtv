package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonSegment;
import com.fongmi.android.tv.bean.TmdbSeasonScope;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TmdbSeasonResolverTest {

    @Test
    public void requestSeasonWinsOverManualBinding() {
        TmdbSeasonMatchCache.Entry manual = binding(2);

        TmdbSeasonResolver.Resolution result = resolve(3, manual, List.of(), -1,
                List.of(1, 2, 3), Map.of(1, 10, 2, 10, 3, 8), 8);

        assertResolved(result, 3, TmdbSeasonResolver.Source.REQUEST);
    }

    @Test
    public void missingRequestedSeasonDoesNotSilentlyFallBack() {
        TmdbSeasonResolver.Resolution result = resolve(3, binding(2), List.of(), -1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 8);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.REQUEST, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void manualBindingWinsOverParsedSeason() {
        TmdbSeasonResolver.Resolution result = resolve(-1, binding(2), List.of(), 1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 8);

        assertResolved(result, 2, TmdbSeasonResolver.Source.MANUAL);
    }

    @Test
    public void manualFlatPreventsAutomaticGuessing() {
        TmdbSeasonMatchCache.Entry manual = flatBinding();

        TmdbSeasonResolver.Resolution result = resolve(-1, manual, List.of(), 1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 10);

        assertEquals(TmdbSeasonResolver.Status.FLAT, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_FLAT, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void manualFlatRemainsEffectiveWhenTmdbSeasonListIsEmpty() {
        TmdbSeasonResolver.Resolution result = resolve(-1, flatBinding(), List.of(), -1,
                List.of(), Map.of(), 12);

        assertEquals(TmdbSeasonResolver.Status.FLAT, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_FLAT, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void requestSeasonStillWinsOverManualFlatWhenSeasonListIsEmpty() {
        TmdbSeasonResolver.Resolution result = resolve(2, flatBinding(), List.of(), -1,
                List.of(), Map.of(), 12);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.REQUEST, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void unknownMultiSeasonDoesNotDefaultToFirstSeason() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 7);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void explicitSourceSeasonResolvesWhenPresentInTmdb() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(2), -1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 8);

        assertResolved(result, 2, TmdbSeasonResolver.Source.EXPLICIT);
    }

    @Test
    public void explicitEpisodesAcrossCompleteSeasonsResolveMultiSlice() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(2, 1), -1,
                List.of(1, 2), Map.of(1, 3, 2, 2), 5,
                List.of(1, 2, 3, 1, 2),
                List.of(1, 1, 1, 2, 2));

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.EXPLICIT_MULTI, result.getSource());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
        assertEquals(List.of(
                new TmdbSeasonSegment(1, 0, 2, 1),
                new TmdbSeasonSegment(2, 3, 4, 1)), result.getSegments());
    }

    @Test
    public void reverseExplicitEpisodeSeasonRunsStayAmbiguous() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(2, 1), -1,
                List.of(1, 2), Map.of(1, 2, 2, 2), 4,
                List.of(1, 2, 1, 2),
                List.of(2, 2, 1, 1));

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.EXPLICIT_CONFLICT, result.getSource());
    }

    @Test
    public void titleAndLineSeasonConflictStaysAmbiguous() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(1), 2,
                List.of(1, 2), Map.of(1, 10, 2, 8), 8);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.EXPLICIT_CONFLICT, result.getSource());
    }

    @Test
    public void onlyOrdinarySeasonIgnoresSpecials() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(0, 2), Map.of(0, 3, 2, 8), 7);

        assertResolved(result, 2, TmdbSeasonResolver.Source.SINGLE_SEASON);
    }

    @Test
    public void exactAllOrdinarySeasonCountsIgnoreSpecialSeasonZero() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(0, 1, 2), Map.of(0, 3, 1, 10, 2, 8), 18);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
        assertEquals(TmdbSeasonResolver.Source.ALL_SEASON_COUNTS, result.getSource());
    }

    @Test
    public void manualMultiSliceIgnoresSpecialSeasonZero() {
        TmdbSeasonResolver.Resolution result = resolve(-1, multiSliceBinding(), List.of(), 6,
                List.of(0, 1, 2), Map.of(0, 3, 1, 10, 2, 8), 18);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
    }
    @Test
    public void specialEpisodeCountDoesNotAutoSelectSeasonZeroWhenOrdinarySeasonsExist() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(0, 1, 2), Map.of(0, 3, 1, 10, 2, 8), 3);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.NONE, result.getSource());
    }
    @Test
    public void uniqueExactEpisodeCountResolvesSeason() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(1, 2, 3), Map.of(1, 10, 2, 8, 3, 12), 8);

        assertResolved(result, 2, TmdbSeasonResolver.Source.EPISODE_COUNT);
    }

    @Test
    public void untrustedSourceDoesNotGuessOnlyOrdinarySeason() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(-1, null, List.of(), -1,
                List.of(0, 3), Map.of(0, 2, 3, 1), 1, false);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.NONE, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void untrustedSourceDoesNotGuessSeasonFromOneEpisodeCount() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(-1, null, List.of(), -1,
                List.of(1, 2, 3), Map.of(1, 10, 2, 8, 3, 1), 1, false);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.NONE, result.getSource());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void untrustedSourceStillHonorsExplicitSeasonEvidence() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(-1, null, List.of(3), -1,
                List.of(1, 2, 3), Map.of(1, 10, 2, 8, 3, 1), 1, false);

        assertResolved(result, 3, TmdbSeasonResolver.Source.EXPLICIT);
    }


    @Test
    public void duplicateExactEpisodeCountsStayAmbiguous() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 8, 2, 8), 8);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertNull(result.getSelectedSeason());
    }

    @Test
    public void exactAllSeasonCountReturnsMultiSlice() {
        TmdbSeasonResolver.Resolution result = resolve(-1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 18);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
        assertEquals(TmdbSeasonResolver.Source.ALL_SEASON_COUNTS, result.getSource());
    }

    @Test
    public void automaticKeyMappingFallsBackWhenCountsAreIncomplete() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 2, 2, 2), 7,
                List.of(4, 1, 2, 2, 0, -1, 5));

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.FLAT_EPISODE_KEYS, result.getSource());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
    }

    @Test
    public void automaticKeyMappingDoesNotGuessWhenOnlyOneSeasonIsTouched() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 2, 2, 2), 5,
                List.of(1, 2, 0, -1, 5));

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.NONE, result.getSource());
    }

    @Test
    public void staleManualSeasonFallsBackToExplicitSignal() {
        TmdbSeasonResolver.Resolution result = resolve(-1, binding(3), List.of(2), -1,
                List.of(1, 2), Map.of(1, 10, 2, 8), 8);

        assertResolved(result, 2, TmdbSeasonResolver.Source.EXPLICIT);
    }

    @Test
    public void manualMultiSliceOverridesTitleSeasonWhenCountsStillMatch() {
        TmdbSeasonResolver.Resolution result = resolve(-1, multiSliceBinding(), List.of(), 6,
                List.of(1, 2), Map.of(1, 10, 2, 8), 18);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
    }

    @Test
    public void manualMultiSliceAllowsPartialLatestSeason() {
        TmdbSeasonResolver.Resolution result = resolve(-1, multiSliceBinding(), List.of(), 6,
                List.of(1, 2), Map.of(1, 10, 2, 8), 17);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
    }

    @Test
    public void persistedMultiSliceSegmentsSurviveLaterTmdbCountChanges() {
        TmdbSeasonMatchCache.Entry binding = TmdbSeasonMatchCache.Entry.create(
                100, "tv", null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE,
                "fingerprint", 18, 0,
                List.of(new TmdbSeasonSegment(1, 0, 9, 1),
                        new TmdbSeasonSegment(2, 10, 17, 1)));

        TmdbSeasonResolver.Resolution result = resolve(-1, binding, List.of(), -1,
                List.of(1, 2), Map.of(1, 11, 2, 9), 18);

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
    }

    @Test
    public void persistedSegmentsRejectOutOfBoundsAndOverlap() {
        TmdbSeasonMatchCache.Entry outOfBounds = TmdbSeasonMatchCache.Entry.create(
                100, "tv", null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE,
                "fingerprint", 4, 0,
                List.of(new TmdbSeasonSegment(1, 0, 1, 1),
                        new TmdbSeasonSegment(2, 2, 4, 1)));
        TmdbSeasonMatchCache.Entry overlap = TmdbSeasonMatchCache.Entry.create(
                100, "tv", null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE,
                "fingerprint", 4, 0,
                List.of(new TmdbSeasonSegment(1, 0, 2, 1),
                        new TmdbSeasonSegment(2, 2, 3, 1)));

        assertEquals(false, TmdbSeasonResolver.hasValidPersistedSegments(
                outOfBounds, List.of(1, 2), Map.of(1, 2, 2, 2), 4));
        assertEquals(false, TmdbSeasonResolver.hasValidPersistedSegments(
                overlap, List.of(1, 2), Map.of(1, 3, 2, 2), 4));
    }

    @Test
    public void flatSeasonSegmentsTruncatePartialLatestSeasonToSourceLength() {
        List<TmdbSeasonSegment> segments = TmdbSeasonResolver.flatSeasonSegments(
                java.util.stream.IntStream.rangeClosed(1, 15).boxed().toList(),
                List.of(1, 2), Map.of(1, 10, 2, 10));

        assertEquals(2, segments.size());
        assertEquals(9, segments.get(0).getSourceEpisodeEndIndex());
        assertEquals(10, segments.get(1).getSourceEpisodeStartIndex());
        assertEquals(14, segments.get(1).getSourceEpisodeEndIndex());
        assertEquals(true, TmdbSeasonResolver.hasValidPersistedSegments(
                TmdbSeasonMatchCache.Entry.create(100, "tv", null,
                        TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE,
                        "fingerprint", 15, 0, segments),
                List.of(1, 2), Map.of(1, 10, 2, 10), 15));
    }

    @Test
    public void flatSeasonSegmentsDoNotPretendIrregularKeysAreContiguous() {
        assertEquals(List.of(), TmdbSeasonResolver.flatSeasonSegments(
                List.of(1, 2, 4, 3), List.of(1, 2), Map.of(1, 2, 2, 2)));
    }

    @Test
    public void manualMultiSliceUsesExtractedEpisodeNumbersAsKeys() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, multiSliceBinding(), List.of(), 6,
                List.of(1, 2), Map.of(1, 2, 2, 2), 6,
                List.of(4, 1, 2, 2, -1, 5));

        assertEquals(TmdbSeasonResolver.Status.MULTI_SLICE, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
        assertEquals(List.of(1, 2), result.getAvailableSeasons());
    }

    @Test
    public void staleManualMultiSliceDoesNotApplyWhenSourceExceedsTmdbCounts() {
        TmdbSeasonResolver.Resolution result = resolve(-1, multiSliceBinding(), List.of(), 6,
                List.of(1, 2), Map.of(1, 10, 2, 8), 19);

        assertEquals(TmdbSeasonResolver.Status.AMBIGUOUS, result.getStatus());
        assertEquals(TmdbSeasonResolver.Source.MANUAL_MULTI_SLICE, result.getSource());
    }
    @Test
    public void multiSliceConvertsToCoveredSeasonScope() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 2, 2, 2), 4);

        TmdbSeasonScope scope = result.toScope();

        assertEquals(TmdbSeasonScope.Kind.MULTI, scope.getKind());
        assertEquals(List.of(1, 2), scope.getSeasons());
    }

    @Test
    public void ambiguousResolutionConvertsToUnknownScope() {
        TmdbSeasonResolver.Resolution result = TmdbSeasonResolver.resolve(
                -1, null, List.of(), -1,
                List.of(1, 2), Map.of(1, 2, 2, 2), 3);

        assertEquals(TmdbSeasonScope.Kind.UNKNOWN, result.toScope().getKind());
    }

    private static TmdbSeasonResolver.Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manual,
            List<Integer> explicitSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount) {
        return TmdbSeasonResolver.resolve(requestSeason, manual, explicitSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount);
    }

    private static TmdbSeasonMatchCache.Entry binding(int season) {
        return TmdbSeasonMatchCache.Entry.create(100, "tv", season,
                TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fingerprint", 8, 8);
    }

    private static TmdbSeasonMatchCache.Entry flatBinding() {
        return TmdbSeasonMatchCache.Entry.create(100, "tv", null,
                TmdbSeasonMatchCache.Mode.MANUAL_FLAT, "fingerprint", 18, 0);
    }

    private static TmdbSeasonMatchCache.Entry multiSliceBinding() {
        return TmdbSeasonMatchCache.Entry.create(100, "tv", null,
                TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE, "fingerprint", 18, 0);
    }
    private static void assertResolved(TmdbSeasonResolver.Resolution result, int season, TmdbSeasonResolver.Source source) {
        assertEquals(TmdbSeasonResolver.Status.RESOLVED, result.getStatus());
        assertEquals(Integer.valueOf(season), result.getSelectedSeason());
        assertEquals(source, result.getSource());
    }
}
