package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbItem;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class FollowingMigrationDeviceTest {

    private static final String SOURCE_KEY = "source:9:migrate:930001:s0";
    private static final String TARGET_KEY = "tmdb:tv:930001:s1";

    @After
    public void tearDown() {
        FollowingStore.delete(SOURCE_KEY);
        FollowingStore.delete(TARGET_KEY);
    }

    @Test
    public void sourceFallbackMigratesToMatchedTmdbIdentity() {
        FollowingStore.delete(SOURCE_KEY);
        FollowingStore.delete(TARGET_KEY);
        Following source = new Following();
        source.identityKey = SOURCE_KEY;
        source.seriesKey = "source:9:migrate:930001";
        source.cid = 9;
        source.siteKey = "migrate";
        source.vodId = "930001";
        source.vodName = "迁移验证剧集";
        source.mediaType = "tv";
        source.trackedSeason = 0;
        source.watchedEpisode = 1;
        source.readWatermarkEpisode = 1;
        source.enabled = true;
        source.nextCheckAt = Long.MAX_VALUE;
        FollowingSource binding = new FollowingSource();
        binding.followingKey = SOURCE_KEY;
        binding.cid = source.cid;
        binding.siteKey = source.siteKey;
        binding.vodId = source.vodId;
        binding.vodName = source.vodName;
        binding.playableEpisode = 16;
        binding.playableCount = 16;
        binding.preferred = true;
        FollowingStore.saveNew(source, binding);

        FollowingMetadataSnapshot snapshot = new FollowingMetadataSnapshot();
        snapshot.source = "tmdb";
        snapshot.status = FollowingMetadataSnapshot.RETURNING;
        snapshot.latestReleasedSeason = 1;
        snapshot.latestReleasedEpisode = 12;
        snapshot.seasonTotalEpisodes = 18;
        snapshot.seasonReleasedEpisodes = 12;
        snapshot.seriesTotalEpisodes = 18;
        snapshot.fetchedAt = System.currentTimeMillis();

        Following migrated = FollowingStore.resolveTmdb(
                new TmdbItem(930001, "tv", source.vodName, "", "", "", ""), 1,
                source.cid, source.siteKey, source.vodId, snapshot);

        assertNotNull(migrated);
        assertNull(FollowingStore.find(SOURCE_KEY));
        Following target = FollowingStore.find(TARGET_KEY);
        assertNotNull(target);
        assertEquals(930001, target.tmdbId);
        assertEquals(1, target.trackedSeason);
        assertEquals(12, target.latestReleasedEpisode);
        assertEquals(FollowingMetadataSnapshot.RETURNING, target.officialStatus);
        FollowingSource moved = FollowingStore.preferredSource(TARGET_KEY);
        assertNotNull(moved);
        assertEquals(1, moved.playableSeason);
        assertEquals(16, moved.playableEpisode);
    }
}
