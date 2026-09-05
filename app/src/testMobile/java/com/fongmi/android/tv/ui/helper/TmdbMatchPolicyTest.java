package com.fongmi.android.tv.ui.helper;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbMatchPolicyTest {

    @Test
    public void plainTitlePrefersRootSeriesOverSplitSeasonVariant() {
        JsonObject root = detail("凡人修仙传", "凡人修仙传");
        JsonObject split = detail("凡人修仙传", "凡人修仙传（分季）");

        int rootScore = TmdbMatchPolicy.splitSeasonDetailScore("凡人修仙传", root);
        int splitScore = TmdbMatchPolicy.splitSeasonDetailScore("凡人修仙传", split);

        assertTrue("plain source title should boost the non-split TMDB entry", rootScore > 0);
        assertTrue("plain source title should penalize TMDB split-season duplicates", splitScore < 0);
        assertTrue("root series must outrank split-season duplicates", rootScore - splitScore >= 300);
        assertTrue(TmdbMatchPolicy.isUnwantedSplitSeasonVariant("凡人修仙传", split));
    }

    @Test
    public void explicitSeasonSourceDoesNotPenalizeSplitSeasonVariant() {
        JsonObject split = detail("凡人修仙传", "凡人修仙传（分季）");

        assertFalse(TmdbMatchPolicy.isUnwantedSplitSeasonVariant("凡人修仙传 第二季", split));
        assertTrue(TmdbMatchPolicy.splitSeasonDetailScore("凡人修仙传 第二季", split) >= 0);
    }

    @Test
    public void pushAutoMatchRejectsGenericOrUrlTitles() {
        assertFalse(TmdbMatchPolicy.shouldAutoMatchPushTitle("\u5728\u7ebf\u89c6\u9891"));
        assertFalse(TmdbMatchPolicy.shouldAutoMatchPushTitle("\u7f51\u7edc\u89c6\u9891 01"));
        assertFalse(TmdbMatchPolicy.shouldAutoMatchPushTitle("https://example.com/live/3"));
        assertFalse(TmdbMatchPolicy.shouldAutoMatchPushTitle("01"));
    }

    @Test
    public void pushAutoMatchKeepsMeaningfulMediaTitles() {
        assertTrue(TmdbMatchPolicy.shouldAutoMatchPushTitle("\u79c1\u5bb6\u4fa6\u63a2 \u7b2c3\u5b63"));
        assertTrue(TmdbMatchPolicy.shouldAutoMatchPushTitle("Clarkson's Farm S03"));
    }

    private JsonObject detail(String name, String originalName) {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.addProperty("original_name", originalName);
        return object;
    }
}
