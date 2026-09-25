package com.fongmi.android.tv.following;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.FollowingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class FollowingActivityDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private List<String> identityKeys;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        FollowingSettings.setEnabled(true);
        String updatedIdentity = "tmdb:tv:900001:s1";
        String quietIdentity = "tmdb:tv:900002:s1";
        identityKeys = List.of(updatedIdentity, quietIdentity);
        FollowingDatabase database = FollowingDatabase.get();
        database.getFollowingSourceDao().deleteAll();
        database.getFollowingDao().deleteAll();
        Following item = new Following();
        item.identityKey = updatedIdentity;
        item.seriesKey = "tmdb:tv:900001";
        item.cid = 0;
        item.siteKey = "test";
        item.vodId = "vod";
        item.vodName = "设备验证剧集";
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 5;
        item.seasonReleasedEpisodes = 5;
        item.readWatermarkEpisode = 1;
        item.watchedEpisode = 2;
        item.unwatchedCount = 3;
        item.hasUpdate = true;
        item.enabled = true;
        item.nextCheckAt = Long.MAX_VALUE;
        item.updatedAt = System.currentTimeMillis();
        FollowingSource source = new FollowingSource();
        source.followingKey = updatedIdentity;
        source.siteKey = "test";
        source.vodId = "vod";
        source.preferred = true;
        source.playableSeason = 1;
        source.playableEpisode = 4;
        source.playableCount = 4;
        FollowingStore.saveNew(item, source);

        Following quiet = item.copy();
        quiet.identityKey = quietIdentity;
        quiet.seriesKey = "tmdb:tv:900002";
        quiet.vodId = "vod2";
        quiet.vodName = "无更新剧集";
        quiet.watchedEpisode = 5;
        quiet.readWatermarkEpisode = 5;
        quiet.unwatchedCount = 0;
        quiet.hasUpdate = false;
        FollowingSource quietSource = source.copy();
        quietSource.followingKey = quietIdentity;
        quietSource.vodId = "vod2";
        FollowingStore.saveNew(quiet, quietSource);
    }

    @After
    public void tearDown() {
        for (String identityKey : identityKeys) FollowingStore.delete(identityKey);
    }

    @Test
    public void activityRendersStoredFollowingAndFiltersUpdates() {
        try (ActivityScenario<FollowingActivity> scenario = ActivityScenario.launch(FollowingActivity.class)) {
            assertTrue(await(scenario, activity -> {
                RecyclerView recycler = activity.findViewById(R.id.recycler);
                return recycler != null && recycler.getAdapter() != null && recycler.getAdapter().getItemCount() == 2;
            }));

            assertTrue(await(scenario, activity -> {
                TextView summary = activity.findViewById(R.id.summary);
                return summary != null && "追更 2 部 · 未读 0 部".contentEquals(summary.getText());
            }));
            scenario.onActivity(activity -> {
                TextView summary = activity.findViewById(R.id.summary);
                assertEquals("追更 2 部 · 未读 0 部", summary.getText().toString());
                RecyclerView recycler = activity.findViewById(R.id.recycler);
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
                assertNotNull(holder);
                assertEquals(View.GONE, holder.itemView.findViewById(R.id.read).getVisibility());
                assertEquals(View.GONE, holder.itemView.findViewById(R.id.badge).getVisibility());
                activity.findViewById(R.id.filter).performClick();
            });

            assertTrue(await(scenario, activity -> {
                TextView filter = activity.findViewById(R.id.filter);
                RecyclerView recycler = activity.findViewById(R.id.recycler);
                return filter != null && "只看更新".contentEquals(filter.getText())
                        && recycler != null && recycler.getAdapter() != null
                        && recycler.getAdapter().getItemCount() == 1;
            }));
        }
        assertFalse(FollowingStore.find(identityKeys.get(0)).hasUpdate);
    }

    private boolean await(ActivityScenario<FollowingActivity> scenario, Check check) {
        boolean[] result = new boolean[1];
        for (int i = 0; i < 30; i++) {
            result[0] = false;
            scenario.onActivity(activity -> result[0] = check.value(activity));
            if (result[0]) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for FollowingActivity");
            }
        }
        return false;
    }

    private interface Check {
        boolean value(FollowingActivity activity);
    }
}
