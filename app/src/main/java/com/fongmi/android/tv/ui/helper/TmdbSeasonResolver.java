package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonSegment;
import com.fongmi.android.tv.bean.TmdbSeasonScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure policy for resolving source episodes to TMDB seasons without guessing season one. */
public final class TmdbSeasonResolver {

    public enum Status {
        RESOLVED,
        MULTI_SLICE,
        FLAT,
        AMBIGUOUS
    }

    public enum Source {
        REQUEST,
        MANUAL,
        MANUAL_FLAT,
        MANUAL_MULTI_SLICE,
        EXPLICIT,
        EXPLICIT_MULTI,
        EXPLICIT_CONFLICT,
        TITLE,
        SINGLE_SEASON,
        EPISODE_COUNT,
        FLAT_EPISODE_KEYS,
        ALL_SEASON_COUNTS,
        NONE
    }

    private TmdbSeasonResolver() {
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount) {
        return resolve(requestSeason, manualBinding, explicitSourceSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount, null, null, true);
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount,
            boolean allowHeuristicGuessing) {
        return resolve(requestSeason, manualBinding, explicitSourceSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount, null, null, allowHeuristicGuessing);
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount,
            List<Integer> sourceEpisodeNumbers) {
        return resolve(requestSeason, manualBinding, explicitSourceSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount, sourceEpisodeNumbers, null, true);
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount,
            List<Integer> sourceEpisodeNumbers,
            boolean allowHeuristicGuessing) {
        return resolve(requestSeason, manualBinding, explicitSourceSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount, sourceEpisodeNumbers, null,
                allowHeuristicGuessing);
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount,
            List<Integer> sourceEpisodeNumbers,
            List<Integer> explicitEpisodeSeasons) {
        return resolve(requestSeason, manualBinding, explicitSourceSeasons, titleSeason,
                tmdbSeasons, seasonCounts, sourceEpisodeCount, sourceEpisodeNumbers,
                explicitEpisodeSeasons, true);
    }

    public static Resolution resolve(
            int requestSeason,
            TmdbSeasonMatchCache.Entry manualBinding,
            List<Integer> explicitSourceSeasons,
            int titleSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount,
            List<Integer> sourceEpisodeNumbers,
            List<Integer> explicitEpisodeSeasons,
            boolean allowHeuristicGuessing) {
        List<Integer> seasons = distinctNonNegative(tmdbSeasons);
        List<Integer> sliceableSeasons = EpisodeSeasonPolicy.sliceableSeasons(seasons);
        if (requestSeason >= 0) {
            if (contains(seasons, requestSeason)) return Resolution.resolved(requestSeason, Source.REQUEST, "request_season");
            return Resolution.ambiguous(Source.REQUEST, "requested_season_missing_from_tmdb");
        }

        if (manualBinding != null && manualBinding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_FLAT) {
            return Resolution.flat(Source.MANUAL_FLAT, "manual_flat");
        }
        if (seasons.isEmpty()) return Resolution.ambiguous(Source.NONE, "tmdb_seasons_empty");
        if (manualBinding != null && manualBinding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE) {
            List<Integer> persistedSeasons = persistedSegmentSeasons(
                    manualBinding, seasons, seasonCounts, sourceEpisodeCount);
            if (persistedSeasons.size() > 1) {
                return Resolution.multiSlice(persistedSeasons, Source.MANUAL_MULTI_SLICE,
                        "manual_multi_slice_segments", manualBinding.getSegments());
            }
            List<Integer> coveredSeasons = sourceEpisodeNumbers == null
                    ? EpisodeSeasonPolicy.coveredSeasonsByEpisodeCount(sourceEpisodeCount, sliceableSeasons, seasonCounts)
                    : EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(sourceEpisodeNumbers, sliceableSeasons, seasonCounts);
            return !coveredSeasons.isEmpty()
                    ? Resolution.multiSlice(coveredSeasons, Source.MANUAL_MULTI_SLICE, "manual_multi_slice")
                    : Resolution.ambiguous(Source.MANUAL_MULTI_SLICE, "manual_multi_slice_stale");
        }

        Resolution manual = resolveManual(manualBinding, seasons);
        if (manual != null) return manual;

        List<Integer> explicit = distinctNonNegative(explicitSourceSeasons);
        if (explicit.size() > 1) {
            if (titleSeason >= 0 || !seasons.containsAll(explicit)) {
                return Resolution.ambiguous(Source.EXPLICIT_CONFLICT, "multiple_explicit_seasons");
            }
            List<Integer> orderedExplicit = new ArrayList<>();
            for (Integer season : sliceableSeasons) if (explicit.contains(season)) orderedExplicit.add(season);
            if (matchesSeasonNumberRuns(sourceEpisodeNumbers, explicitEpisodeSeasons,
                    orderedExplicit, seasonCounts)) {
                return Resolution.multiSlice(orderedExplicit, Source.EXPLICIT_MULTI,
                        "multiple_explicit_seasons", completeSeasonSegments(orderedExplicit, seasonCounts));
            }
            return Resolution.ambiguous(Source.EXPLICIT_CONFLICT, "multiple_explicit_seasons");
        }
        if (explicit.size() == 1) {
            int season = explicit.get(0);
            if (titleSeason >= 0 && titleSeason != season) {
                return Resolution.ambiguous(Source.EXPLICIT_CONFLICT, "title_and_source_season_conflict");
            }
            return contains(seasons, season)
                    ? Resolution.resolved(season, Source.EXPLICIT, "explicit_source_season")
                    : Resolution.ambiguous(Source.EXPLICIT, "explicit_season_missing_from_tmdb");
        }

        if (titleSeason >= 0) {
            return contains(seasons, titleSeason)
                    ? Resolution.resolved(titleSeason, Source.TITLE, "title_season")
                    : Resolution.ambiguous(Source.TITLE, "title_season_missing_from_tmdb");
        }

        List<Integer> ordinarySeasons = new ArrayList<>();
        for (Integer season : seasons) if (season != null && season > 0) ordinarySeasons.add(season);
        if (allowHeuristicGuessing && ordinarySeasons.size() == 1) return Resolution.resolved(ordinarySeasons.get(0), Source.SINGLE_SEASON, "single_ordinary_season");
        if (!allowHeuristicGuessing) return Resolution.ambiguous(Source.NONE, "heuristic_guessing_disabled");
        if (ordinarySeasons.isEmpty() && seasons.size() == 1 && seasons.get(0) == 0) {
            return Resolution.resolved(0, Source.SINGLE_SEASON, "specials_only");
        }

        List<Integer> exactCountMatches = exactCountMatches(sliceableSeasons.isEmpty() ? seasons : sliceableSeasons, seasonCounts, sourceEpisodeCount);
        if (exactCountMatches.size() == 1) {
            return Resolution.resolved(exactCountMatches.get(0), Source.EPISODE_COUNT, "unique_episode_count");
        }
        if (EpisodeSeasonPolicy.canSliceBySeasonCounts(sourceEpisodeCount, sliceableSeasons, seasonCounts)) {
            return Resolution.multiSlice(sliceableSeasons, Source.ALL_SEASON_COUNTS, "all_season_counts");
        }

        List<Integer> keyedSeasons = EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(
                sourceEpisodeNumbers, sliceableSeasons, seasonCounts);
        if (keyedSeasons.size() > 1) {
            return Resolution.multiSlice(keyedSeasons, Source.FLAT_EPISODE_KEYS, "flat_episode_keys");
        }

        if (exactCountMatches.size() > 1) {
            return Resolution.ambiguous(Source.EPISODE_COUNT, "duplicate_episode_counts");
        }

        return Resolution.ambiguous(Source.NONE, "insufficient_season_evidence");
    }

    private static Resolution resolveManual(TmdbSeasonMatchCache.Entry binding, List<Integer> tmdbSeasons) {
        if (binding == null) return null;
        if (binding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_FLAT) {
            return Resolution.flat(Source.MANUAL_FLAT, "manual_flat");
        }
        Integer season = binding.getSeasonNumber();
        if (binding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_SEASON && season != null && contains(tmdbSeasons, season)) {
            return Resolution.resolved(season, Source.MANUAL, "manual_season");
        }
        return null;
    }

    private static List<Integer> persistedSegmentSeasons(
            TmdbSeasonMatchCache.Entry binding,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount) {
        if (!hasValidPersistedSegments(binding, tmdbSeasons, seasonCounts, sourceEpisodeCount)) return List.of();
        List<Integer> result = new ArrayList<>();
        for (TmdbSeasonSegment segment : binding.getSegments()) {
            if (!result.contains(segment.getSeasonNumber())) result.add(segment.getSeasonNumber());
        }
        return result;
    }

    public static boolean hasValidPersistedSegments(
            TmdbSeasonMatchCache.Entry binding,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            int sourceEpisodeCount) {
        if (binding == null || binding.getSegments().size() < 2 || sourceEpisodeCount <= 0) return false;
        int expectedStart = 0;
        for (TmdbSeasonSegment segment : binding.getSegments()) {
            if (segment == null || !tmdbSeasons.contains(segment.getSeasonNumber())
                    || segment.getSourceEpisodeStartIndex() != expectedStart
                    || segment.getSourceEpisodeEndIndex() < segment.getSourceEpisodeStartIndex()
                    || segment.getSourceEpisodeEndIndex() >= sourceEpisodeCount
                    || segment.getTmdbEpisodeStartNumber() <= 0) return false;
            int length = segment.getSourceEpisodeEndIndex() - segment.getSourceEpisodeStartIndex() + 1;
            int tmdbCount = seasonCounts == null ? 0 : seasonCounts.getOrDefault(segment.getSeasonNumber(), 0);
            if (tmdbCount <= 0 || segment.getTmdbEpisodeStartNumber() + length - 1 > tmdbCount) return false;
            expectedStart = segment.getSourceEpisodeEndIndex() + 1;
        }
        return expectedStart == sourceEpisodeCount;
    }

    public static List<TmdbSeasonSegment> flatSeasonSegments(
            List<Integer> sourceEpisodeNumbers,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeNumbers == null || sourceEpisodeNumbers.isEmpty()) return List.of();
        List<TmdbSeasonSegment> segments = new ArrayList<>();
        Set<Integer> completed = new LinkedHashSet<>();
        int currentSeason = -1;
        int currentStart = 0;
        int currentTmdbStart = -1;
        int previousTmdbEpisode = -1;
        for (int index = 0; index < sourceEpisodeNumbers.size(); index++) {
            EpisodeSeasonPolicy.SeasonEpisode mapped = EpisodeSeasonPolicy.mapFlatEpisodeNumber(
                    sourceEpisodeNumbers.get(index) == null ? -1 : sourceEpisodeNumbers.get(index),
                    tmdbSeasons, seasonCounts);
            if (mapped == null) return List.of();
            if (mapped.seasonNumber() != currentSeason) {
                if (currentSeason >= 0) {
                    completed.add(currentSeason);
                    segments.add(new TmdbSeasonSegment(currentSeason, currentStart, index - 1, currentTmdbStart));
                }
                if (completed.contains(mapped.seasonNumber())) return List.of();
                currentSeason = mapped.seasonNumber();
                currentStart = index;
                currentTmdbStart = mapped.episodeNumber();
            } else if (mapped.episodeNumber() != previousTmdbEpisode + 1) {
                return List.of();
            }
            previousTmdbEpisode = mapped.episodeNumber();
        }
        segments.add(new TmdbSeasonSegment(currentSeason, currentStart,
                sourceEpisodeNumbers.size() - 1, currentTmdbStart));
        return segments.size() > 1 ? List.copyOf(segments) : List.of();
    }

    private static List<Integer> exactCountMatches(List<Integer> seasons, Map<Integer, Integer> seasonCounts, int sourceEpisodeCount) {
        List<Integer> matches = new ArrayList<>();
        if (sourceEpisodeCount <= 0 || seasonCounts == null || seasonCounts.isEmpty()) return matches;
        for (Integer season : seasons) {
            if (seasonCounts.getOrDefault(season, 0) == sourceEpisodeCount) matches.add(season);
        }
        return matches;
    }

    private static boolean matchesSeasonNumberRuns(
            List<Integer> sourceEpisodeNumbers,
            List<Integer> explicitEpisodeSeasons,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeNumbers == null || sourceEpisodeNumbers.isEmpty() || seasons.size() < 2) return false;
        if (explicitEpisodeSeasons == null || explicitEpisodeSeasons.size() != sourceEpisodeNumbers.size()) return false;
        int index = 0;
        for (Integer season : seasons) {
            int count = seasonCounts.getOrDefault(season, 0);
            if (count <= 0 || index + count > sourceEpisodeNumbers.size()) return false;
            boolean explicitEvidence = false;
            for (int number = 1; number <= count; number++) {
                if (!Integer.valueOf(number).equals(sourceEpisodeNumbers.get(index++))) return false;
                Integer explicitSeason = explicitEpisodeSeasons.get(index - 1);
                if (explicitSeason != null && explicitSeason >= 0) {
                    explicitEvidence = true;
                    if (!season.equals(explicitSeason)) return false;
                }
            }
            if (!explicitEvidence) return false;
        }
        return index == sourceEpisodeNumbers.size();
    }

    private static List<TmdbSeasonSegment> completeSeasonSegments(
            List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        List<TmdbSeasonSegment> segments = new ArrayList<>();
        int sourceStart = 0;
        for (Integer season : seasons) {
            int count = seasonCounts.getOrDefault(season, 0);
            if (count <= 0) return List.of();
            segments.add(new TmdbSeasonSegment(season, sourceStart, sourceStart + count - 1, 1));
            sourceStart += count;
        }
        return List.copyOf(segments);
    }

    private static List<Integer> distinctNonNegative(List<Integer> values) {
        Set<Integer> distinct = new LinkedHashSet<>();
        if (values != null) for (Integer value : values) if (value != null && value >= 0) distinct.add(value);
        return List.copyOf(distinct);
    }

    private static boolean contains(List<Integer> seasons, int season) {
        return season >= 0 && seasons.contains(season);
    }

    public static final class Resolution {

        private final Status status;
        private final Integer selectedSeason;
        private final List<Integer> availableSeasons;
        private final Source source;
        private final String reason;
        private final List<TmdbSeasonSegment> segments;

        private Resolution(Status status, Integer selectedSeason, List<Integer> availableSeasons, Source source,
                           String reason, List<TmdbSeasonSegment> segments) {
            this.status = status;
            this.selectedSeason = selectedSeason;
            this.availableSeasons = availableSeasons == null ? List.of() : List.copyOf(availableSeasons);
            this.source = source;
            this.reason = reason == null ? "" : reason;
            this.segments = segments == null ? List.of() : List.copyOf(segments);
        }

        private static Resolution resolved(int season, Source source, String reason) {
            return new Resolution(Status.RESOLVED, season, List.of(season), source, reason, List.of());
        }

        private static Resolution multiSlice(List<Integer> seasons, Source source, String reason) {
            return multiSlice(seasons, source, reason, List.of());
        }

        private static Resolution multiSlice(List<Integer> seasons, Source source, String reason,
                                             List<TmdbSeasonSegment> segments) {
            return new Resolution(Status.MULTI_SLICE, null, seasons, source, reason, segments);
        }

        private static Resolution flat(Source source, String reason) {
            return new Resolution(Status.FLAT, null, List.of(), source, reason, List.of());
        }

        private static Resolution ambiguous(Source source, String reason) {
            return new Resolution(Status.AMBIGUOUS, null, List.of(), source, reason, List.of());
        }

        public Status getStatus() {
            return status;
        }

        public Integer getSelectedSeason() {
            return selectedSeason;
        }

        public List<Integer> getAvailableSeasons() {
            return availableSeasons;
        }

        public Source getSource() {
            return source;
        }

        public String getReason() {
            return reason;
        }

        public List<TmdbSeasonSegment> getSegments() {
            return segments;
        }

        public TmdbSeasonScope toScope() {
            if (status == Status.RESOLVED && selectedSeason != null) {
                return TmdbSeasonScope.known(selectedSeason);
            }
            if (status == Status.MULTI_SLICE) {
                TmdbSeasonScope segmented = TmdbSeasonScope.multiSegments(segments);
                return segmented.isKnown() ? segmented : TmdbSeasonScope.multi(availableSeasons);
            }
            return TmdbSeasonScope.unknown();
        }
    }
}
