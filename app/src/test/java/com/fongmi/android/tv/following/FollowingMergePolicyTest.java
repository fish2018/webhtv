package com.fongmi.android.tv.following;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FollowingMergePolicyTest {

    @Test
    public void mergeKeepsNewerProgressAndMaximumWatermarks() {
        Following local = following();
        local.watchedEpisode = 4;
        local.updatedAt = 200;
        local.readWatermarkEpisode = 3;
        local.nextCheckAt = 999;

        Following remote = following();
        remote.watchedEpisode = 7;
        remote.updatedAt = 100;
        remote.readWatermarkEpisode = 6;
        remote.lastNotifiedEpisode = 5;
        remote.nextCheckAt = 111;

        Following merged = FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0);
        assertEquals(7, merged.watchedEpisode);
        assertEquals(6, merged.readWatermarkEpisode);
        assertEquals(5, merged.lastNotifiedEpisode);
        assertEquals(999, merged.nextCheckAt);
    }

    @Test
    public void explicitDisableIntentWinsOnEitherDevice() {
        Following local = following();
        local.enabled = true;
        Following remote = following();
        remote.enabled = false;

        assertFalse(FollowingMergePolicy.mergeFollowing(List.of(local), List.of(remote)).get(0).enabled);
    }

    private static Following following() {
        Following item = new Following();
        item.identityKey = "tmdb:tv:1:s1";
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 8;
        item.seasonReleasedEpisodes = 8;
        return item;
    }
}
