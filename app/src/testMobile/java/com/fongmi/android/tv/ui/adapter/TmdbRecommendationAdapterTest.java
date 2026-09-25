package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbRecommendationAdapterTest {

    @Test
    public void contentComparison_detectsRatingOnlyChanges() {
        TmdbItem initial = item(8.2, 0.0);
        TmdbItem enriched = item(8.2, 9.1);

        assertFalse(TmdbRecommendationAdapter.sameContent(initial, enriched));
        assertFalse(TmdbRailAdapter.sameContent(initial, enriched));
    }

    @Test
    public void contentComparison_acceptsEquivalentCards() {
        TmdbItem first = item(8.2, 9.1);
        TmdbItem second = item(8.2, 9.1);

        assertTrue(TmdbRecommendationAdapter.sameContent(first, second));
        assertTrue(TmdbRailAdapter.sameContent(first, second));
    }

    private static TmdbItem item(double tmdbRating, double doubanRating) {
        return new TmdbItem(
                1,
                "movie",
                "测试影片",
                "2024",
                "",
                "poster",
                "backdrop",
                "",
                tmdbRating,
                "zh",
                "CN",
                new ArrayList<>(),
                "",
                tmdbRating,
                doubanRating);
    }
}
