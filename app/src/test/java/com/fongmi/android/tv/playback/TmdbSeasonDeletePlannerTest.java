package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbSeasonDeletePlannerTest {

    @Test
    public void deletingCurrentSeasonRestoresNewestOtherSeasonOnSharedRoute() {
        History route = history("site@@@vod", 2, 300);
        TmdbSeasonProgress first = progress(route, 1, 100);
        TmdbSeasonProgress second = progress(route, 2, 300);

        TmdbSeasonDeletePlanner.Plan plan = TmdbSeasonDeletePlanner.plan(
                List.of(route), List.of(second, first), 7, "tv", 88, 2);

        assertTrue(plan.deleteRouteKeys().isEmpty());
        assertEquals(1, plan.restoreRoutes().size());
        assertEquals(1, plan.restoreRoutes().get(route.getKey()).seasonNumber);
    }

    @Test
    public void deletingOlderSnapshotDoesNotRewriteRouteAlreadyShowingAnotherSeason() {
        History route = history("site@@@vod", 3, 400);
        TmdbSeasonProgress second = progress(route, 2, 200);
        TmdbSeasonProgress third = progress(route, 3, 400);

        TmdbSeasonDeletePlanner.Plan plan = TmdbSeasonDeletePlanner.plan(
                List.of(route), List.of(third, second), 7, "tv", 88, 2);

        assertTrue(plan.deleteRouteKeys().isEmpty());
        assertTrue(plan.restoreRoutes().isEmpty());
    }

    @Test
    public void deletingOnlySeasonRemovesRouteWhenNoOtherSnapshotReferencesIt() {
        History route = history("site@@@vod", 2, 300);

        TmdbSeasonDeletePlanner.Plan plan = TmdbSeasonDeletePlanner.plan(
                List.of(route), List.of(progress(route, 2, 300)), 7, "tv", 88, 2);

        assertEquals(List.of(route.getKey()), plan.deleteRouteKeys());
        assertTrue(plan.restoreRoutes().isEmpty());
    }

    private static History history(String key, int season, long updatedAt) {
        History history = new History();
        history.setKey(key);
        history.setCid(7);
        history.setMediaType("tv");
        history.setTmdbId(88);
        history.setTmdbEpisodePosition(season, 5);
        history.setCreateTime(updatedAt);
        return history;
    }

    private static TmdbSeasonProgress progress(History history, int season, long updatedAt) {
        TmdbSeasonProgress progress = TmdbSeasonProgress.of(
                history.getCid(), history.getMediaType(), history.getTmdbId(), season, 5,
                1_000, 2_000, history.getKey());
        progress.updatedAt = updatedAt;
        return progress;
    }
}
