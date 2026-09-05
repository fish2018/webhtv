package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;

import java.util.Locale;

public final class TmdbRatingFormatter {

    private TmdbRatingFormatter() {
    }

    public static String format(TmdbItem item) {
        return format(item, 0.0);
    }

    public static String format(TmdbItem item, double loadedDoubanRating) {
        Ratings ratings = ratings(item, loadedDoubanRating);
        if (ratings.getTmdb().isEmpty()) return ratings.getDouban();
        if (ratings.getDouban().isEmpty()) return ratings.getTmdb();
        return ratings.getTmdb() + " · " + ratings.getDouban();
    }

    public static Ratings ratings(TmdbItem item) {
        return ratings(item, 0.0);
    }

    public static Ratings ratings(TmdbItem item, double loadedDoubanRating) {
        if (item == null) return Ratings.EMPTY;
        double tmdbRating = item.getTmdbRating();
        double doubanRating = loadedDoubanRating > 0 ? loadedDoubanRating : item.getDoubanRating();
        if (item.getRating() > 0) {
            if (tmdbRating <= 0 && item.getTmdbId() > 0) tmdbRating = item.getRating();
            if (doubanRating <= 0 && item.getTmdbId() <= 0) doubanRating = item.getRating();
        }
        return new Ratings(
                label("TMDB", tmdbRating),
                label("豆瓣", doubanRating),
                tmdbRating > 0,
                doubanRating > 0);
    }

    public static Ratings completeRatings(TmdbItem item) {
        return completeRatings(item, 0.0, "");
    }

    public static Ratings completeRatings(TmdbItem item, double loadedDoubanRating) {
        return completeRatings(item, loadedDoubanRating, "");
    }

    public static Ratings completeRatings(TmdbItem item, String legacyRating) {
        return completeRatings(item, 0.0, legacyRating);
    }

    private static Ratings completeRatings(TmdbItem item, double loadedDoubanRating, String legacyRating) {
        Ratings ratings = ratings(item, loadedDoubanRating);
        String fallback = legacyRating == null ? "" : legacyRating.trim();
        if (ratings.isEmpty() && !fallback.isEmpty()) {
            boolean tmdb = item != null && item.getTmdbId() > 0;
            ratings = new Ratings(
                    tmdb ? "TMDB " + fallback : "",
                    tmdb ? "" : "豆瓣 " + fallback,
                    tmdb,
                    !tmdb);
        }
        return new Ratings(
                ratings.hasTmdbRating() ? ratings.getTmdb() : "TMDB —",
                ratings.hasDoubanRating() ? ratings.getDouban() : "豆瓣 —",
                ratings.hasTmdbRating(),
                ratings.hasDoubanRating());
    }

    private static String label(String source, double rating) {
        return rating > 0 ? String.format(Locale.US, "%s %.1f", source, rating) : "";
    }

    public static final class Ratings {

        private static final Ratings EMPTY = new Ratings("", "", false, false);

        private final String tmdb;
        private final String douban;
        private final boolean tmdbRating;
        private final boolean doubanRating;

        private Ratings(String tmdb, String douban, boolean tmdbRating, boolean doubanRating) {
            this.tmdb = tmdb;
            this.douban = douban;
            this.tmdbRating = tmdbRating;
            this.doubanRating = doubanRating;
        }

        public String getTmdb() {
            return tmdb;
        }

        public String getDouban() {
            return douban;
        }

        public boolean hasTmdbRating() {
            return tmdbRating;
        }

        public boolean hasDoubanRating() {
            return doubanRating;
        }

        public boolean isEmpty() {
            return !tmdbRating && !doubanRating;
        }
    }
}
