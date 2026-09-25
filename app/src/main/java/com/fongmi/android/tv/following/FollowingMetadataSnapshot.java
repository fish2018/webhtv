package com.fongmi.android.tv.following;

import java.util.Locale;

public final class FollowingMetadataSnapshot {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String RETURNING = "RETURNING";
    public static final String PLANNED = "PLANNED";
    public static final String ENDED = "ENDED";
    public static final String CANCELED = "CANCELED";

    public String source = "";
    public String status = UNKNOWN;
    public int latestReleasedSeason;
    public int latestReleasedEpisode;
    public int seasonTotalEpisodes;
    public int seasonReleasedEpisodes;
    public int seriesTotalEpisodes;
    public int nextAirSeason;
    public int nextAirEpisode;
    public long nextAirAt;
    public long fetchedAt;

    public static String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (status.contains("RETURNING") || status.contains("PRODUCTION") || status.contains("PILOT")) return RETURNING;
        if (status.contains("PLANNED")) return PLANNED;
        if (status.contains("ENDED")) return ENDED;
        if (status.contains("CANCEL")) return CANCELED;
        return UNKNOWN;
    }

    public FollowingMetadataSnapshot copy() {
        FollowingMetadataSnapshot item = new FollowingMetadataSnapshot();
        item.source = source;
        item.status = status;
        item.latestReleasedSeason = latestReleasedSeason;
        item.latestReleasedEpisode = latestReleasedEpisode;
        item.seasonTotalEpisodes = seasonTotalEpisodes;
        item.seasonReleasedEpisodes = seasonReleasedEpisodes;
        item.seriesTotalEpisodes = seriesTotalEpisodes;
        item.nextAirSeason = nextAirSeason;
        item.nextAirEpisode = nextAirEpisode;
        item.nextAirAt = nextAirAt;
        item.fetchedAt = fetchedAt;
        return item;
    }
}
