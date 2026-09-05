package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.history.HistoryDisplayPolicy;

import org.junit.Test;

import java.util.List;

public class HistorySeasonSnapshotProjectionTest {

    @Test
    public void ownSeasonSnapshotSupersedesOlderUnknownHistoryRoute() {
        History unknown = history("tv", 88, 100, "site-a@@@vod-a");
        unknown.setCid(7);
        unknown.setTmdbEpisodePositionWithUnknownSeason(1);
        unknown.setPosition(296);
        unknown.setDuration(2_276_000);
        TmdbSeasonProgress snapshot = TmdbSeasonProgress.of(
                7, "tv", 88, 6, 1, 1_080_193, 2_276_000, unknown.getKey());
        snapshot.updatedAt = 300;

        List<History> result = HistoryDisplayPolicy.project(List.of(unknown), List.of(snapshot), true);

        assertEquals(1, result.size());
        assertEquals(6, result.get(0).getTmdbSeasonNumber());
        assertEquals(1_080_193, result.get(0).getPosition());
    }

    @Test
    public void movieRouteDoesNotConsumeTvSeasonSnapshotWithCollidingIdentity() {
        History movie = history("movie", 88, 100, "site-a@@@vod-a");
        movie.setCid(7);
        movie.setPosition(120);
        TmdbSeasonProgress tvSnapshot = TmdbSeasonProgress.of(
                7, "tv", 88, 6, 1, 1_080_193, 2_276_000, movie.getKey());
        tvSnapshot.updatedAt = 300;

        List<History> result = HistoryDisplayPolicy.project(List.of(movie), List.of(tvSnapshot), true);

        assertEquals(1, result.size());
        assertEquals(120, result.get(0).getPosition());
        assertEquals(0, result.get(0).getTmdbSeasonNumber());
    }

    @Test
    public void newerUnknownHistoryRouteStaysBesideOldSnapshot() {
        History unknown = history("tv", 88, 300, "site-a@@@vod-a");
        unknown.setCid(7);
        unknown.setTmdbEpisodePositionWithUnknownSeason(1);
        TmdbSeasonProgress snapshot = TmdbSeasonProgress.of(
                7, "tv", 88, 6, 1, 1_080_193, 2_276_000, unknown.getKey());
        snapshot.updatedAt = 100;

        List<History> result = HistoryDisplayPolicy.project(List.of(unknown), List.of(snapshot), true);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> item.getTmdbSeasonNumber() == -1));
        assertTrue(result.stream().anyMatch(item -> item.getTmdbSeasonNumber() == 6));
    }

    @Test
    public void explicitUnknownSeasonPreservedWhenCanonicalEpisodeNumberIsKnown() {
        History history = new History();
        history.setTmdbEpisodePosition(6, 12);

        assertTrue(history.setTmdbEpisodePositionWithUnknownSeason(13));

        assertEquals(-1, history.getTmdbSeasonNumber());
        assertEquals(13, history.getTmdbEpisodeNumber());
    }

    @Test
    public void nullEpisodeStillClearsCanonicalPosition() {
        History history = new History();
        history.setTmdbEpisodePosition(6, 12);

        history.setTmdbEpisodePosition(null);

        assertEquals(0, history.getTmdbSeasonNumber());
        assertEquals(0, history.getTmdbEpisodeNumber());
    }

    private static History history(String mediaType, int tmdbId, long createTime, String key) {
        History history = new History();
        history.setKey(key);
        history.setMediaType(mediaType);
        history.setTmdbId(tmdbId);
        history.setCreateTime(createTime);
        return history;
    }
}
