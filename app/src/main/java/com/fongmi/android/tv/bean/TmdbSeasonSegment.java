package com.fongmi.android.tv.bean;

import java.util.Objects;

/** A verified slice of a flat source line belonging to one TMDB season. */
public final class TmdbSeasonSegment {

    private final int seasonNumber;
    private final int sourceEpisodeStartIndex;
    private final int sourceEpisodeEndIndex;
    private final int tmdbEpisodeStartNumber;

    public TmdbSeasonSegment(int seasonNumber, int sourceEpisodeStartIndex,
                             int sourceEpisodeEndIndex, int tmdbEpisodeStartNumber) {
        this.seasonNumber = seasonNumber;
        this.sourceEpisodeStartIndex = sourceEpisodeStartIndex;
        this.sourceEpisodeEndIndex = sourceEpisodeEndIndex;
        this.tmdbEpisodeStartNumber = tmdbEpisodeStartNumber;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getSourceEpisodeStartIndex() {
        return sourceEpisodeStartIndex;
    }

    public int getSourceEpisodeEndIndex() {
        return sourceEpisodeEndIndex;
    }

    public int getTmdbEpisodeStartNumber() {
        return tmdbEpisodeStartNumber;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof TmdbSeasonSegment other)) return false;
        return seasonNumber == other.seasonNumber
                && sourceEpisodeStartIndex == other.sourceEpisodeStartIndex
                && sourceEpisodeEndIndex == other.sourceEpisodeEndIndex
                && tmdbEpisodeStartNumber == other.tmdbEpisodeStartNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seasonNumber, sourceEpisodeStartIndex, sourceEpisodeEndIndex, tmdbEpisodeStartNumber);
    }
}
