package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingProjectionDeviceTest {

    private static final String IDENTITY_KEY = "tmdb:tv:910001:s1";
    private static final String HISTORY_KEY = "projection@@@item";

    @After
    public void tearDown() {
        FollowingStore.delete(IDENTITY_KEY);
        AppDatabase.get().getHistoryDao().delete(0, HISTORY_KEY);
    }

    @Test
    public void mainThreadProjectionQueuesWithoutMainThreadRoomAccess() throws Exception {
        FollowingStore.delete(IDENTITY_KEY);
        AppDatabase.get().getHistoryDao().delete(0, HISTORY_KEY);

        Following item = new Following();
        item.identityKey = IDENTITY_KEY;
        item.seriesKey = "tmdb:tv:910001";
        item.siteKey = "projection";
        item.vodId = "item";
        item.mediaType = "tv";
        item.tmdbId = 910001;
        item.trackedSeason = 1;
        item.enabled = true;
        item.nextCheckAt = Long.MAX_VALUE;
        FollowingStore.saveNew(item, null);

        History history = new History();
        history.setKey(HISTORY_KEY);
        history.setCid(0);
        history.setVodName("投影验证剧集");
        history.setMediaType("tv");
        history.setTmdbId(910001);
        history.setTmdbEpisodePosition(1, 3);
        history.setPosition(12_000);
        history.setDuration(24_000);
        history.setCreateTime(System.currentTimeMillis());
        AppDatabase.get().getHistoryDao().insertOrUpdate(history);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> FollowingStore.project(history));

        Following projected = awaitProjected();
        assertEquals(3, projected.watchedEpisode);
        assertEquals(12_000, projected.position);
    }

    private Following awaitProjected() throws Exception {
        for (int i = 0; i < 50; i++) {
            Following item = FollowingStore.find(IDENTITY_KEY);
            if (item != null && item.watchedEpisode == 3) return item;
            Thread.sleep(100);
        }
        throw new AssertionError("following projection did not complete");
    }
}
