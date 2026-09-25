package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowingPlaybackBridgeTest {

    @Test
    public void movieIsNotEligibleButTvAndSourceAre() {
        History movie = history("movie", 88, 2, 5);
        History tv = history("tv", 88, 2, 5);
        History source = history("", 0, -1, 0);
        source.setKey("site@@@vod@@@7");
        source.setCid(7);

        assertFalse(FollowingPlaybackBridge.isEligible(movie));
        assertTrue(FollowingPlaybackBridge.isEligible(tv));
        assertTrue(FollowingPlaybackBridge.isEligible(source));
    }

    @Test
    public void buildUsesStableTmdbSeasonProgress() {
        History history = history("tv", 88, 2, 5);
        history.setCid(7);
        history.setVodName("测试剧");
        history.setVodPic("poster");
        history.setPosition(1200);
        history.setDuration(2400);

        Following item = FollowingPlaybackBridge.build(history, 1);

        assertEquals("tmdb:tv:88:s2", item.identityKey);
        assertEquals("tmdb:tv:88", item.seriesKey);
        assertEquals(2, item.trackedSeason);
        assertEquals(5, item.watchedEpisode);
        assertEquals(1200, item.position);
        assertEquals(0, item.readWatermarkEpisode);
        assertEquals(0, item.lastNotifiedEpisode);
    }

    @Test
    public void sourceFallbackIdentityUsesConfigSiteAndVod() {
        History history = history("", 0, -1, 0);
        history.setCid(7);
        history.setKey("Site@@@VOD@@@7");

        assertEquals("source:7:site:vod:s0", FollowingPlaybackBridge.identityKey(history, 0));
    }

    private static History history(String mediaType, int tmdbId, int season, int episode) {
        History history = new History();
        history.setMediaType(mediaType);
        history.setTmdbId(tmdbId);
        history.setTmdbEpisodePosition(season, episode);
        return history;
    }
}
