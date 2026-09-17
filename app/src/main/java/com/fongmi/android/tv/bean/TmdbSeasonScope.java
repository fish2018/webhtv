package com.fongmi.android.tv.bean;

import java.util.LinkedHashSet;
import java.util.List;

public final class TmdbSeasonScope {

    public enum Kind {
        KNOWN,
        MULTI,
        UNKNOWN
    }

    private static final TmdbSeasonScope UNKNOWN = new TmdbSeasonScope(Kind.UNKNOWN, null, List.of(), List.of());

    private final Kind kind;
    private final Integer seasonNumber;
    private final List<Integer> seasons;
    private final List<TmdbSeasonSegment> segments;

    private TmdbSeasonScope(Kind kind, Integer seasonNumber, List<Integer> seasons,
                            List<TmdbSeasonSegment> segments) {
        this.kind = kind;
        this.seasonNumber = seasonNumber;
        this.seasons = seasons;
        this.segments = segments;
    }

    public static TmdbSeasonScope known(int seasonNumber) {
        if (seasonNumber < 0) return unknown();
        return new TmdbSeasonScope(Kind.KNOWN, seasonNumber, List.of(seasonNumber),
                List.of(new TmdbSeasonSegment(seasonNumber, -1, -1, 1)));
    }

    public static TmdbSeasonScope multi(List<Integer> seasons) {
        LinkedHashSet<Integer> distinct = new LinkedHashSet<>();
        if (seasons != null) {
            for (Integer season : seasons) {
                if (season != null && season >= 0) distinct.add(season);
            }
        }
        if (distinct.size() < 2) return unknown();
        List<TmdbSeasonSegment> segments = new java.util.ArrayList<>();
        for (Integer season : distinct) segments.add(new TmdbSeasonSegment(season, -1, -1, 1));
        return new TmdbSeasonScope(Kind.MULTI, null, List.copyOf(distinct), List.copyOf(segments));
    }

    public static TmdbSeasonScope multiSegments(List<TmdbSeasonSegment> segments) {
        if (segments == null || segments.size() < 2) return unknown();
        LinkedHashSet<Integer> distinct = new LinkedHashSet<>();
        for (TmdbSeasonSegment segment : segments) {
            if (segment == null || segment.getSeasonNumber() < 0
                    || segment.getSourceEpisodeStartIndex() < 0
                    || segment.getSourceEpisodeEndIndex() < segment.getSourceEpisodeStartIndex()
                    || segment.getTmdbEpisodeStartNumber() <= 0) return unknown();
            distinct.add(segment.getSeasonNumber());
        }
        if (distinct.size() < 2) return unknown();
        return new TmdbSeasonScope(Kind.MULTI, null, List.copyOf(distinct), List.copyOf(segments));
    }

    public static TmdbSeasonScope unknown() {
        return UNKNOWN;
    }

    public Kind getKind() {
        return kind;
    }

    public Integer getSeasonNumber() {
        return seasonNumber;
    }

    public List<Integer> getSeasons() {
        return seasons;
    }

    public List<TmdbSeasonSegment> getSegments() {
        return segments;
    }

    public TmdbSeasonSegment segmentFor(int seasonNumber) {
        for (TmdbSeasonSegment segment : segments) {
            if (segment.getSeasonNumber() == seasonNumber) return segment;
        }
        return null;
    }

    public boolean accepts(int seasonNumber) {
        return seasons.contains(seasonNumber);
    }

    public boolean isKnown() {
        return kind != Kind.UNKNOWN;
    }
}
