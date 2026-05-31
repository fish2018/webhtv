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

    public TmdbEpisode(int number, String title, String date, String overview, String stillUrl, double voteAverage, int runtime) {
        this.number = number;
        this.title = title;
        this.date = date;
        this.overview = overview;
        this.stillUrl = stillUrl;
        this.voteAverage = voteAverage;
        this.runtime = runtime;
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
}
