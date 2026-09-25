package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class EpisodeSeasonPolicy {

    private static final Pattern SPECIAL_SEASON = Pattern.compile("(?i)(?:第\\s*0\\s*[季部]|season\\s*0\\b|s0{1,2}(?:[-._\\s]*e[0-9]{1,3})?\\b|特别篇|特別篇|\\bspecials\\b)");

    private EpisodeSeasonPolicy() {
    }

    /** Season 0 contains specials and is not part of a flat ordinary-season sequence. */
    public static List<Integer> sliceableSeasons(List<Integer> seasons) {
        if (seasons == null || seasons.isEmpty()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Integer season : seasons) {
            if (season != null && season > 0) result.add(season);
        }
        return List.copyOf(result);
    }

    public static boolean canSliceBySeasonCounts(int episodeCount, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        List<Integer> countedSeasons = sliceableSeasons(seasons);
        if (episodeCount <= 0 || countedSeasons.size() <= 1 || seasonCounts == null || seasonCounts.isEmpty()) return false;
        int total = 0;
        for (Integer season : countedSeasons) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            if (count <= 0) return false;
            total += count;
            if (total > episodeCount) return false;
        }
        return total == episodeCount;
    }

    public static <T> List<T> sliceBySeasonCounts(List<T> episodes, List<Integer> seasons, Map<Integer, Integer> seasonCounts, int selectedSeason) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        List<Integer> countedSeasons = sliceableSeasons(seasons);
        if (!canSliceBySeasonCounts(episodes.size(), countedSeasons, seasonCounts)) return episodes;
        int start = 0;
        for (Integer season : countedSeasons) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            int end = Math.min(episodes.size(), start + count);
            if (season == selectedSeason) return start < end ? episodes.subList(start, end) : List.of();
            start = end;
        }
        return episodes;
    }

    public static boolean shouldUseSingleSeasonEpisodeData(int sourceEpisodeCount, int firstSeason, List<Integer> seasons, Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeCount <= 0 || firstSeason < 0 || seasons == null || seasons.size() <= 1 || seasonCounts == null) return false;
        int firstSeasonCount = Math.max(0, seasonCounts.getOrDefault(firstSeason, 0));
        return firstSeasonCount >= sourceEpisodeCount && !canSliceBySeasonCounts(sourceEpisodeCount, seasons, seasonCounts);
    }

    /**
     * Resolves only seasons that can be safely mapped to the current source line.
     * An empty result means the UI must keep the source episodes as one flat list.
     */
    public static List<Integer> resolveAvailableSeasons(
            List<Integer> sourceSeasonNumbers,
            int titleSeason,
            int firstSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts) {
        return resolveAvailableSeasons(sourceSeasonNumbers, titleSeason, firstSeason, tmdbSeasons, seasonCounts, null);
    }

    public static List<Integer> resolveAvailableSeasons(
            List<Integer> sourceSeasonNumbers,
            int titleSeason,
            int firstSeason,
            List<Integer> tmdbSeasons,
            Map<Integer, Integer> seasonCounts,
            List<Integer> sourceEpisodeNumbers) {
        if (sourceSeasonNumbers == null || sourceSeasonNumbers.isEmpty() || tmdbSeasons == null || tmdbSeasons.isEmpty()) return List.of();
        boolean hasAnyExplicitSeason = hasAnyExplicitSeason(sourceSeasonNumbers);
        if (hasAnyExplicitSeason) {
            if (!hasCompleteExplicitSeasonMapping(sourceSeasonNumbers, tmdbSeasons)) {
                // Unclassified extras are safe only when every known episode agrees on one TMDB season.
                int onlyMappedSeason = -1;
                for (Integer season : sourceSeasonNumbers) {
                    if (season == null || season < 0) continue;
                    if (!tmdbSeasons.contains(season)) return List.of();
                    if (onlyMappedSeason >= 0 && onlyMappedSeason != season) return List.of();
                    onlyMappedSeason = season;
                }
                return onlyMappedSeason >= 0 ? List.of(onlyMappedSeason) : List.of();
            }
            List<Integer> available = new ArrayList<>();
            for (Integer season : tmdbSeasons) {
                if (sourceSeasonNumbers.contains(season)) available.add(season);
            }
            return List.copyOf(available);
        }
        if (titleSeason >= 0) return tmdbSeasons.contains(titleSeason) ? List.of(titleSeason) : List.of();
        if (tmdbSeasons.size() == 1) return List.of(tmdbSeasons.get(0));
        int sourceEpisodeCount = sourceSeasonNumbers.size();
        if (canSliceBySeasonCounts(sourceEpisodeCount, tmdbSeasons, seasonCounts)) return sliceableSeasons(tmdbSeasons);
        List<Integer> keyedSeasons = mappedSeasonsByEpisodeNumbers(sourceEpisodeNumbers, tmdbSeasons, seasonCounts);
        if (keyedSeasons.size() > 1) return keyedSeasons;
        if (tmdbSeasons.contains(firstSeason) && shouldUseSingleSeasonEpisodeData(sourceEpisodeCount, firstSeason, tmdbSeasons, seasonCounts)) return List.of(firstSeason);
        return List.of();
    }

    public static int resolveSourceSeason(String... candidates) {
        return resolveSourceSeason(true, candidates);
    }

    /** Use for source-line labels and episode names, where trailing digits are ordinals rather than seasons. */
    public static int resolveExplicitSourceSeason(String... candidates) {
        return resolveSourceSeason(false, candidates);
    }

    private static int resolveSourceSeason(boolean allowTrailingSeason, String... candidates) {
        if (candidates == null || candidates.length == 0) return -1;
        MediaTitleParser parser = new MediaTitleParser();
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            if (SPECIAL_SEASON.matcher(candidate).find()) return 0;
            int season = allowTrailingSeason
                    ? parser.parse(MediaTitleRequest.builder().rawTitle(candidate).build()).getSeasonNumber()
                    : parser.seasonNumber(candidate);
            if (season > 0) return season;
        }
        return -1;
    }

    public static List<Integer> episodeMetadataSeasonCandidates(int sourceSeason) {
        return sourceSeason >= 0 ? List.of(sourceSeason) : List.of();
    }

    public static String episodePositionCacheKey(int season, String episodeName) {
        String name = episodeName == null ? "" : episodeName;
        if (season < 0 || name.isEmpty()) return name;
        String prefix = "S" + season + "|";
        return name.startsWith(prefix) ? name : prefix + name;
    }

    private static boolean hasAnyExplicitSeason(List<Integer> sourceSeasonNumbers) {
        for (Integer season : sourceSeasonNumbers) {
            if (season != null && season >= 0) return true;
        }
        return false;
    }

    /** Returns true only when every source episode declares a season present in TMDB metadata. */
    public static boolean hasCompleteExplicitSeasonMapping(List<Integer> sourceSeasonNumbers, List<Integer> tmdbSeasons) {
        if (sourceSeasonNumbers == null || sourceSeasonNumbers.isEmpty() || tmdbSeasons == null || tmdbSeasons.isEmpty()) return false;
        for (Integer season : sourceSeasonNumbers) {
            if (season == null || season < 0 || !tmdbSeasons.contains(season)) return false;
        }
        return true;
    }

    /**
     * Returns true only when a flat source line is a complete 1..N sequence and
     * TMDB season counts cover it exactly. This prevents gap/duplicate lists
     * from being silently remapped by position.
     */
    public static boolean canMapFlatEpisodeNumbers(
            List<Integer> sourceEpisodeNumbers,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeNumbers == null || sourceEpisodeNumbers.isEmpty()) return false;
        if (!canSliceBySeasonCounts(sourceEpisodeNumbers.size(), seasons, seasonCounts)) return false;
        for (int index = 0; index < sourceEpisodeNumbers.size(); index++) {
            Integer number = sourceEpisodeNumbers.get(index);
            if (number == null || number != index + 1) return false;
        }
        return true;
    }

    /**
     * Returns true when at least one extracted source episode number can be
     * mapped to an ordinary TMDB season. Missing, duplicate, unordered and
     * out-of-range numbers do not shift other episodes and are ignored.
     */
    public static boolean canMapFlatEpisodeKeys(
            List<Integer> sourceEpisodeNumbers,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        return !mappedSeasonsByEpisodeNumbers(sourceEpisodeNumbers, seasons, seasonCounts).isEmpty();
    }

    /** Returns ordinary TMDB seasons referenced by extracted episode-number keys. */
    public static List<Integer> mappedSeasonsByEpisodeNumbers(
            List<Integer> sourceEpisodeNumbers,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        List<Integer> ordinarySeasons = sliceableSeasons(seasons);
        if (sourceEpisodeNumbers == null || sourceEpisodeNumbers.isEmpty()
                || ordinarySeasons.isEmpty() || seasonCounts == null || seasonCounts.isEmpty()) {
            return List.of();
        }
        List<Integer> touched = new ArrayList<>();
        for (Integer number : sourceEpisodeNumbers) {
            SeasonEpisode mapped = mapFlatEpisodeNumber(number == null ? -1 : number, ordinarySeasons, seasonCounts);
            if (mapped != null && !touched.contains(mapped.seasonNumber())) touched.add(mapped.seasonNumber());
        }
        if (touched.isEmpty()) return List.of();
        List<Integer> ordered = new ArrayList<>();
        for (Integer season : ordinarySeasons) if (touched.contains(season)) ordered.add(season);
        return List.copyOf(ordered);
    }

    /** Returns ordinary TMDB seasons touched by a 1..N source prefix. */
    public static List<Integer> coveredSeasonsByEpisodeCount(
            int episodeCount,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        List<Integer> ordinarySeasons = sliceableSeasons(seasons);
        if (episodeCount <= 0 || ordinarySeasons.isEmpty() || seasonCounts == null || seasonCounts.isEmpty()) return List.of();
        int remaining = episodeCount;
        List<Integer> covered = new ArrayList<>();
        for (Integer season : ordinarySeasons) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            if (count <= 0) return List.of();
            covered.add(season);
            if (remaining <= count) return List.copyOf(covered);
            remaining -= count;
        }
        return List.of();
    }

    public static SeasonEpisode mapFlatEpisodeNumber(
            int sourceEpisodeNumber,
            List<Integer> seasons,
            Map<Integer, Integer> seasonCounts) {
        if (sourceEpisodeNumber <= 0 || seasons == null || seasonCounts == null) return null;
        int offset = sourceEpisodeNumber - 1;
        for (Integer season : sliceableSeasons(seasons)) {
            int count = Math.max(0, seasonCounts.getOrDefault(season, 0));
            if (count <= 0) return null;
            if (offset < count) return new SeasonEpisode(season, offset + 1);
            offset -= count;
        }
        return null;
    }

    public record SeasonEpisode(int seasonNumber, int episodeNumber) {
    }

    public static int linearEpisodeNumber(int sourceEpisodeNumber, int zeroBasedIndex) {
        // 文件名有明确集号时，直接使用它，不要被列表位置覆盖
        if (sourceEpisodeNumber > 0) return sourceEpisodeNumber;
        // 文件名无集号时，用列表位置推断（index + 1）
        return zeroBasedIndex >= 0 ? zeroBasedIndex + 1 : -1;
    }
}
