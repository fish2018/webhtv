package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class TmdbRatingFormatterTest {

    @Test
    public void format_showsTmdbAndDoubanRatingsTogether() {
        TmdbItem item = new TmdbItem(
                123, "movie", "测试电影", "", "", "", "", "", 8.2,
                "zh", "CN", Collections.emptyList(), "", 8.2, 8.7);

        assertEquals("TMDB 8.2 · 豆瓣 8.7", TmdbRatingFormatter.format(item));
    }


    @Test
    public void ratings_exposesSeparateSingleLineChipLabels() {
        TmdbItem item = new TmdbItem(
                123, "movie", "测试电影", "", "", "", "", "", 8.2,
                "zh", "CN", Collections.emptyList(), "", 8.2, 8.7);

        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.ratings(item);

        assertEquals("TMDB 8.2", ratings.getTmdb());
        assertEquals("豆瓣 8.7", ratings.getDouban());
    }

    @Test
    public void ratings_keepsMissingSourceEmptyInsteadOfRenderingPlaceholder() {
        TmdbItem item = new TmdbItem(123, "movie", "测试电影", "", "", "", "", "", 8.2);

        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.ratings(item);

        assertEquals("TMDB 8.2", ratings.getTmdb());
        assertEquals("", ratings.getDouban());
    }

    @Test
    public void ratings_recoversTmdbScoreFromLegacyCacheAfterDoubanWasAdded() {
        TmdbItem item = new Gson().fromJson(
                "{\"tmdbId\":123,\"mediaType\":\"movie\",\"title\":\"旧缓存\",\"rating\":8.1,\"tmdbRating\":0,\"doubanRating\":8.7,\"genreIds\":[]}",
                TmdbItem.class);

        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.completeRatings(item);

        assertEquals("TMDB 8.1", ratings.getTmdb());
        assertEquals("豆瓣 8.7", ratings.getDouban());
    }

    @Test
    public void completeRatings_keepsBothSourceSlotsVisible() {
        TmdbItem tmdbOnly = new TmdbItem(123, "movie", "测试电影", "", "", "", "", "", 8.2);
        TmdbItem doubanOnly = new TmdbItem(-123, "movie", "测试电影", "", "", "", "", "", 9.1);

        TmdbRatingFormatter.Ratings tmdbRatings = TmdbRatingFormatter.completeRatings(tmdbOnly);
        TmdbRatingFormatter.Ratings doubanRatings = TmdbRatingFormatter.completeRatings(doubanOnly);

        assertEquals("TMDB 8.2", tmdbRatings.getTmdb());
        assertEquals("豆瓣 —", tmdbRatings.getDouban());
        assertEquals("TMDB —", doubanRatings.getTmdb());
        assertEquals("豆瓣 9.1", doubanRatings.getDouban());
    }

    @Test
    public void format_labelsLegacyNegativeIdRatingAsDouban() {
        TmdbItem item = new TmdbItem(-123, "movie", "测试电影", "", "", "", "", "", 9.1);

        assertEquals("豆瓣 9.1", TmdbRatingFormatter.format(item));
    }
}
