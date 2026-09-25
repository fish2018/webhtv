package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FollowingContinueDeviceTest {

    private static final String HISTORY_KEY = "source-a@@@vod-a";

    @After
    public void tearDown() {
        AppDatabase.get().getHistoryDao().delete(0, HISTORY_KEY);
    }

    @Test
    public void historyForFallsBackToTmdbProgressFromAnotherSource() {
        AppDatabase.get().getHistoryDao().delete(0, HISTORY_KEY);
        History history = new History();
        history.setKey(HISTORY_KEY);
        history.setCid(0);
        history.setVodName("跨源续播验证");
        history.setMediaType("tv");
        history.setTmdbId(910003);
        history.setTmdbEpisodePosition(1, 4);
        history.setPosition(8_000);
        history.setDuration(24_000);
        history.setCreateTime(100);
        AppDatabase.get().getHistoryDao().insertOrUpdate(history);

        Following item = new Following();
        item.cid = 0;
        item.mediaType = "tv";
        item.tmdbId = 910003;
        item.trackedSeason = 1;
        FollowingSource source = new FollowingSource();
        source.siteKey = "source-b";
        source.vodId = "vod-b";

        History restored = FollowingStore.historyFor(item, source);

        assertNotNull(restored);
        assertEquals(HISTORY_KEY, restored.getKey());
        assertEquals(4, restored.getTmdbEpisodeNumber());
        assertEquals(8_000, restored.getPosition());
    }
}
