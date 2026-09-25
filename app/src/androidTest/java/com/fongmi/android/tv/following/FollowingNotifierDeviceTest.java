package com.fongmi.android.tv.following;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.ui.activity.FollowingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class FollowingNotifierDeviceTest {

    private boolean originalNotificationsEnabled;

    @Before
    public void setUp() {
        originalNotificationsEnabled = FollowingSettings.isNotificationsEnabled();
        FollowingSettings.setEnabled(true);
        FollowingSettings.setNotificationsEnabled(true);
    }

    @After
    public void tearDown() {
        FollowingSettings.setNotificationsEnabled(originalNotificationsEnabled);
    }

    @Test
    public void foregroundSuppressesNotificationWithoutAdvancingWatermark() {
        Following item = new Following();
        item.identityKey = "tmdb:tv:910002:s1";
        item.vodName = "前台通知验证";
        item.trackedSeason = 1;
        item.latestReleasedSeason = 1;
        item.latestReleasedEpisode = 5;
        item.readWatermarkEpisode = 4;
        item.lastNotifiedEpisode = 2;
        item.notifyEnabled = true;
        item.enabled = true;

        try (ActivityScenario<FollowingActivity> scenario = ActivityScenario.launch(FollowingActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(FollowingNotifier.notifyUpdate(item, null));
                assertEquals(2, item.lastNotifiedEpisode);
            });
        }
    }
}
