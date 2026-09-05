package com.fongmi.android.tv.history;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HistoryDisplayPolicyTest {

    @Test
    public void aggregationUsesMediaTypeAndTmdbId() {
        History movie = history("movie", 42, 100, "movie-key");
        History tv = history("tv", 42, 200, "tv-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(movie, tv), true);

        assertEquals(2, result.size());
        assertEquals("tv-key", result.get(0).getKey());
        assertEquals("movie-key", result.get(1).getKey());
    }

    @Test
    public void aggregationKeepsMostRecentlyPlayedMember() {
        History older = history("tv", 88, 100, "old-key");
        older.setTmdbEpisodePosition(1, 1);
        History newer = history("tv", 88, 300, "new-key");
        newer.setTmdbEpisodePosition(1, 2);

        List<History> result = HistoryDisplayPolicy.project(List.of(older, newer), true);

        assertEquals(1, result.size());
        assertEquals("new-key", result.get(0).getKey());
    }

    @Test
    public void aggregationKeepsDifferentTvSeasonsSeparate() {
        History seasonOne = history("tv", 88, 100, "season-one");
        seasonOne.setTmdbEpisodePosition(1, 5);
        History seasonThree = history("tv", 88, 300, "season-three");
        seasonThree.setTmdbEpisodePosition(3, 1);

        List<History> result = HistoryDisplayPolicy.project(List.of(seasonOne, seasonThree), true);

        assertEquals(2, result.size());
        assertEquals("season-three", result.get(0).getKey());
        assertEquals("season-one", result.get(1).getKey());
    }

    @Test
    public void knownSeasonsWithoutSnapshotsHaveDistinctDisplayNames() {
        History seasonOne = history("tv", 88, 100, "season-one");
        seasonOne.setVodName("乐高幻影忍者：神龙崛起");
        seasonOne.setTmdbEpisodePosition(1, 5);
        History seasonThree = history("tv", 88, 300, "season-three");
        seasonThree.setVodName("乐高幻影忍者：神龙崛起");
        seasonThree.setTmdbEpisodePosition(3, 1);

        List<History> result = HistoryDisplayPolicy.project(List.of(seasonOne, seasonThree), true);

        assertEquals("乐高幻影忍者：神龙崛起 第3季", result.get(0).getVodName());
        assertEquals("乐高幻影忍者：神龙崛起 第1季", result.get(1).getVodName());
    }

    @Test
    public void chineseNumberSeasonTitleIsNotDecoratedTwice() {
        History seasonTwo = history("tv", 88, 200, "season-two");
        seasonTwo.setCid(7);
        seasonTwo.setVodName("乐高幻影忍者：神龙崛起第二季");
        seasonTwo.setTmdbEpisodePosition(2, 5);
        TmdbSeasonProgress snapshot = TmdbSeasonProgress.of(
                7, "tv", 88, 2, 5, 1_000, 2_000, seasonTwo.getKey());

        List<History> result = HistoryDisplayPolicy.project(
                List.of(seasonTwo), List.of(snapshot), true);

        assertEquals(1, result.size());
        assertEquals("乐高幻影忍者：神龙崛起第二季", result.get(0).getVodName());
    }

    @Test
    public void mismatchedSourceSeasonTitleIsReplacedByProjectedSeason() {
        History sharedRoute = history("tv", 88, 200, "shared-route");
        sharedRoute.setCid(7);
        sharedRoute.setVodName("乐高幻影忍者：神龙崛起第二季");
        sharedRoute.setTmdbEpisodePosition(2, 5);
        TmdbSeasonProgress seasonOne = TmdbSeasonProgress.of(
                7, "tv", 88, 1, 3, 1_000, 2_000, sharedRoute.getKey());

        List<History> result = HistoryDisplayPolicy.project(
                List.of(sharedRoute), List.of(seasonOne), true);
        History projected = result.stream()
                .filter(item -> item.getTmdbSeasonNumber() == 1)
                .findFirst()
                .orElseThrow();

        assertEquals("乐高幻影忍者：神龙崛起 第1季", projected.getVodName());
    }

    @Test
    public void aggregationProjectsEveryStoredSeasonFromSharedSourceHistory() {
        History sharedRoute = history("tv", 88, 300, "site-a@@@vod-a");
        sharedRoute.setCid(7);
        sharedRoute.setVodName("乐高幻影忍者：神龙崛起");
        sharedRoute.setTmdbEpisodePosition(3, 2);

        TmdbSeasonProgress first = TmdbSeasonProgress.of(7, "tv", 88, 1, 5,
                1_000, 2_000, sharedRoute.getKey());
        first.updatedAt = 100;
        TmdbSeasonProgress second = TmdbSeasonProgress.of(7, "tv", 88, 2, 5,
                3_000, 4_000, sharedRoute.getKey());
        second.updatedAt = 200;
        TmdbSeasonProgress third = TmdbSeasonProgress.of(7, "tv", 88, 3, 2,
                5_000, 6_000, sharedRoute.getKey());
        third.updatedAt = 300;

        List<History> result = HistoryDisplayPolicy.project(
                List.of(sharedRoute), List.of(first, second, third), true);

        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getTmdbSeasonNumber());
        assertEquals(2, result.get(1).getTmdbSeasonNumber());
        assertEquals(1, result.get(2).getTmdbSeasonNumber());
        assertEquals(5_000, result.get(0).getPosition());
        assertEquals(3_000, result.get(1).getPosition());
        assertEquals(1_000, result.get(2).getPosition());
        assertFalse(result.get(0).isSameItem(result.get(1)));
        assertFalse(result.get(1).isSameItem(result.get(2)));
    }

    @Test
    public void oldKnownSnapshotDoesNotHideCurrentUnknownHistory() {
        History currentUnknown = history("tv", 88, 300, "site-a@@@vod-a");
        currentUnknown.setCid(7);
        TmdbSeasonProgress oldSeason = TmdbSeasonProgress.of(
                7, "tv", 88, 1, 5, 1_000, 2_000, currentUnknown.getKey());
        oldSeason.updatedAt = 100;

        List<History> result = HistoryDisplayPolicy.project(
                List.of(currentUnknown), List.of(oldSeason), true);

        assertEquals(2, result.size());
        assertEquals("source:" + currentUnknown.getKey(), HistoryDisplayPolicy.tmdbIdentity(result.get(0)));
        assertEquals(1, result.get(1).getTmdbSeasonNumber());
    }

    @Test
    public void unknownTvHistoriesRemainSourceIsolated() {
        History first = history("tv", 88, 100, "site-a@@@vod-a");
        History second = history("tv", 88, 200, "site-b@@@vod-b");

        List<History> result = HistoryDisplayPolicy.project(List.of(first, second), true);

        assertEquals(2, result.size());
        assertEquals("site-b@@@vod-b", result.get(0).getKey());
        assertEquals("site-a@@@vod-a", result.get(1).getKey());
    }

    @Test
    public void recordsWithoutStableTmdbIdentityStaySeparate() {
        History first = history("", 88, 100, "first-key");
        History second = history("", 88, 200, "second-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(first, second), true);

        assertEquals(2, result.size());
        assertEquals("second-key", result.get(0).getKey());
    }

    @Test
    public void aggregationDisabledOnlySortsByCreateTime() {
        History older = history("tv", 88, 100, "old-key");
        History newer = history("tv", 88, 300, "new-key");

        List<History> result = HistoryDisplayPolicy.project(List.of(older, newer), false);

        assertEquals(2, result.size());
        assertEquals("new-key", result.get(0).getKey());
        assertEquals("old-key", result.get(1).getKey());
    }

    @Test
    public void playbackCopyPreservesProgressAndMarksCrossSource() {
        History source = history("tv", 88, 100, "old-site@@@old-vod@@@2");
        source.setCid(2);
        source.setPosition(120_000);
        source.setDuration(300_000);

        History result = source.forPlaybackKey("new-site@@@new-vod@@@1", 1);

        assertEquals("new-site@@@new-vod@@@1", result.getKey());
        assertEquals(1, result.getCid());
        assertEquals(120_000, result.getPosition());
        assertEquals(300_000, result.getDuration());
        org.junit.Assert.assertTrue(result.isCrossSourcePlayback());
    }

    @Test
    public void playbackCopyKeepsCurrentSourceIdentity() {
        History source = history("tv", 88, 100, "site@@@vod@@@1");
        source.setCid(1);

        History result = source.forPlaybackKey("site@@@vod@@@1", 1);

        assertEquals("site@@@vod@@@1", result.getKey());
        assertEquals(1, result.getCid());
        assertFalse(result.isCrossSourcePlayback());
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
