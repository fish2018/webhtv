package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class HistorySeasonDeleteScopeTest {

    @Test
    public void globalSeasonDeleteIncludesCidsWhoseCurrentRouteMovedToAnotherSeason() {
        History currentSeasonTwo = history(7, 88, 2);
        History anotherConfigSeasonOne = history(8, 88, 1);
        TmdbSeasonProgress oldSeasonOne = TmdbSeasonProgress.of(
                7, "tv", 88, 1, 5, 1_000, 2_000, "site@@@vod");

        Set<Integer> result = History.seasonDeleteCids(
                List.of(currentSeasonTwo, anotherConfigSeasonOne),
                List.of(oldSeasonOne), "tv", 88, 1, 9);

        assertEquals(Set.of(7, 8, 9), result);
    }

    private static History history(int cid, int tmdbId, int season) {
        History history = new History();
        history.setCid(cid);
        history.setMediaType("tv");
        history.setTmdbId(tmdbId);
        history.setTmdbEpisodePosition(season, 1);
        return history;
    }
}
