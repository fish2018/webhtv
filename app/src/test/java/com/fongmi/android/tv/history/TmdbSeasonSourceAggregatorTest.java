package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.bean.TmdbSeasonScope;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbSeasonSourceAggregatorTest {

    @Test
    public void collectsOnlySameSeasonAndDeduplicatesSourceRoutes() {
        History olderLine = history("site-a@@@vod-a@@@line-1", 1, 10, 1, 2, 100);
        History newerLine = history("site-a@@@vod-a@@@line-2", 1, 10, 1, 3, 300);
        History otherSource = history("site-b@@@vod-b", 1, 10, 1, 4, 200);
        History otherSeason = history("site-c@@@vod-c", 1, 10, 2, 1, 400);
        History otherConfig = history("site-d@@@vod-d", 2, 10, 1, 5, 500);

        List<History> result = TmdbSeasonSourceAggregator.collect(
                List.of(olderLine, newerLine, otherSource, otherSeason, otherConfig),
                1, "tv", 10, 1, "site-current@@@vod-current");

        assertEquals(2, result.size());
        assertEquals(newerLine, result.get(0));
        assertEquals(otherSource, result.get(1));
    }

    @Test
    public void excludesCurrentRouteAndRejectsUnknownSeason() {
        History current = history("site-a@@@vod-a@@@line", 1, 10, 1, 2, 100);

        assertTrue(TmdbSeasonSourceAggregator.collect(
                List.of(current), 1, "tv", 10, 1, "site-a@@@vod-a").isEmpty());
        assertTrue(TmdbSeasonSourceAggregator.collect(
                List.of(current), 1, "tv", 10, -1, "").isEmpty());
    }

    @Test
    public void restoresSharedRouteFromRequestedSeasonSnapshot() {
        History sharedRoute = history("site-a@@@vod-a@@@line", 1, 10, 3, 2, 300);
        TmdbSeasonProgress seasonOne = TmdbSeasonProgress.of(
                1, "tv", 10, 1, 4, 120, 1200, sharedRoute.getKey());
        seasonOne.sourceFlag = "line";
        seasonOne.sourceEpisodeName = "Episode 4";
        seasonOne.sourceEpisodeUrl = "s1e4";
        seasonOne.updatedAt = 200;

        List<History> result = TmdbSeasonSourceAggregator.collect(
                List.of(sharedRoute), List.of(seasonOne),
                1, "tv", 10, 1, "site-current@@@vod-current");

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getTmdbSeasonNumber());
        assertEquals(4, result.get(0).getTmdbEpisodeNumber());
        assertEquals("s1e4", result.get(0).getEpisodeUrl());
    }

    @Test
    public void routeBindingsKeepAllMultiSeasonSourcesAfterProgressMovesToAnotherSeason() {
        History routeA = history("site-a@@@vod-a", 1, 10, 2, 3, 300);
        History routeB = history("site-b@@@vod-b", 1, 10, 2, 4, 400);
        TmdbSeasonProgress seasonOne = TmdbSeasonProgress.of(
                1, "tv", 10, 1, 5, 120, 1200, routeB.getKey());
        seasonOne.updatedAt = 200;
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.recordRouteBinding("site-a", "vod-a", "line#0", "line", 10, "tv",
                TmdbSeasonScope.multi(List.of(1, 2)));
        cache.recordRouteBinding("site-b", "vod-b", "line#0", "line", 10, "tv",
                TmdbSeasonScope.multi(List.of(1, 2)));

        List<History> result = TmdbSeasonSourceAggregator.collect(
                List.of(routeA, routeB), List.of(seasonOne), cache,
                1, "tv", 10, 1, "site-current@@@vod-current");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> item.getSiteKey().equals("site-a")));
        assertTrue(result.stream().anyMatch(item -> item.getSiteKey().equals("site-b")));
    }

    @Test
    public void snapshotKeepsExactDuplicateFlagKeyAcrossRestart() {
        History route = history("site-a@@@vod-a", 1, 10, 1, 3, 300);
        TmdbSeasonProgress seasonOne = TmdbSeasonProgress.of(
                1, "tv", 10, 1, 5, 120, 1200, route.getKey());
        seasonOne.sourceFlag = "duplicate";
        seasonOne.sourceBindingKey = "duplicate#1";
        seasonOne.updatedAt = 300;
        TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
        cache.recordRouteBinding("site-a", "vod-a", "duplicate#0", "duplicate", 10, "tv",
                TmdbSeasonScope.known(1));
        cache.recordRouteBinding("site-a", "vod-a", "duplicate#1", "duplicate", 10, "tv",
                TmdbSeasonScope.known(1));

        List<History> result = TmdbSeasonSourceAggregator.collect(
                List.of(route), List.of(seasonOne), cache,
                1, "tv", 10, 1, "site-current@@@vod-current");

        assertEquals(1, result.size());
        assertEquals("duplicate#1", result.get(0).getSourceBindingKey());
    }

    private static History history(String key, int cid, int tmdbId, int season, int episode, long time) {
        History history = new History();
        history.setKey(key);
        history.setCid(cid);
        history.setMediaType("tv");
        history.setTmdbId(tmdbId);
        history.setTmdbEpisodePosition(season, episode);
        history.setCreateTime(time);
        return history;
    }
}
