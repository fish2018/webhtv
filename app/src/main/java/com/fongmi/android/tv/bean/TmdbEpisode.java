package com.fongmi.android.tv.bean;

import android.text.TextUtils;

public class TmdbEpisode {

    private final int number;
    private final String title;
    private final String date;
    private final String overview;
    private final String stillUrl;
    private final double voteAverage;
    private final int runtime;
    private final int tmdbId;       // TMDB 剧集 ID
    private final int seasonNumber; // 季数，0 为特别篇，负数表示未知

    public TmdbEpisode(int number, String title, String date, String overview, String stillUrl, double voteAverage, int runtime) {
        this(number, title, date, overview, stillUrl, voteAverage, runtime, 0, 1);
    }

    public TmdbEpisode(int number, String title, String date, String overview, String stillUrl, double voteAverage, int runtime, int tmdbId, int seasonNumber) {
        this.number = number;
        this.title = title;
        this.date = date;
        this.overview = overview;
        this.stillUrl = stillUrl;
        this.voteAverage = voteAverage;
        this.runtime = runtime;
        this.tmdbId = tmdbId;
        this.seasonNumber = seasonNumber;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return TextUtils.isEmpty(title) ? "" : title;
    }

    public String getDate() {
        return TextUtils.isEmpty(date) ? "" : date;
    }

    public String getOverview() {
        return TextUtils.isEmpty(overview) ? "" : overview;
    }

    public String getStillUrl() {
        return TextUtils.isEmpty(stillUrl) ? "" : stillUrl;
    }

    public double getVoteAverage() {
        return voteAverage;
    }

    public int getRuntime() {
        return runtime;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public String getDisplayTitle() {
        return String.format("第%d集%s", number, TextUtils.isEmpty(title) ? "" : " - " + title);
    }
}
