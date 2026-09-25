package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FollowingBackupDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private String identityKey;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        identityKey = "tmdb:tv:992234:s1";
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @After
    public void tearDown() {
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @Test
    public void realDatabaseRestoreAndMergeKeepIdentityAndWatermarks() {
        Following local = following();
        local.readWatermarkEpisode = 3;
        local.lastNotifiedEpisode = 3;
        local.watchedEpisode = 2;
        local.nextCheckAt = 999;
        local.lastError = "local only";
        FollowingSource source = source();
        FollowingStore.saveNew(local, source);

        FollowingBackupCodec.Payload payload = FollowingBackupCodec.capture();
        assertEquals(1, payload.following().size());
        assertEquals(1, payload.sources().size());

        Following remote = local.copy();
        remote.readWatermarkEpisode = 7;
        remote.lastNotifiedEpisode = 6;
        remote.watchedEpisode = 5;
        remote.updatedAt = local.updatedAt + 1;
        FollowingSource remoteSource = source.copy();
        remoteSource.playableEpisode = 8;
        remoteSource.playableCount = 8;

        FollowingBackupCodec.merge(List.of(remote), List.of(remoteSource));

        Following merged = FollowingStore.find(identityKey);
        FollowingSource mergedSource = FollowingStore.preferredSource(identityKey);
        assertNotNull(merged);
        assertEquals(7, merged.readWatermarkEpisode);
        assertEquals(6, merged.lastNotifiedEpisode);
        assertEquals(5, merged.watchedEpisode);
        assertEquals(999, merged.nextCheckAt);
        assertEquals("local only", merged.lastError);
        assertEquals(8, mergedSource.playableEpisode);

        FollowingBackupCodec.restoreFull(payload.following(), payload.sources());
        assertEquals(1, FollowingStore.list().size());
        assertEquals(3, FollowingStore.find(identityKey).readWatermarkEpisode);
        assertEquals(1, FollowingStore.sources(identityKey).size());
    }

    private Following following() {
        Following item = new Following();
        item.identityKey = identityKey;
        item.seriesKey = "tmdb:tv:992234";
        item.cid = 1;
        item.siteKey = "site";
        item.vodId = "vod";
        item.vodName = "备份测试";
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 8;
        item.enabled = true;
        item.createdAt = 100;
        item.updatedAt = 100;
        return item;
    }

    private FollowingSource source() {
        FollowingSource item = new FollowingSource();
        item.followingKey = identityKey;
        item.cid = 1;
        item.siteKey = "site";
        item.vodId = "vod";
        item.vodName = "备份测试";
        item.preferred = true;
        item.playableEpisode = 5;
        item.playableCount = 5;
        return item;
    }
}
