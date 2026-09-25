package com.fongmi.android.tv.following;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.FollowingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingCheckDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    @Before
    public void setUp() {
        FollowingSettings.setEnabled(true);
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @After
    public void tearDown() {
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @Test
    public void checkAllStartedOnMainThreadDoesNotAccessRoomOnMainThread() throws Exception {
        try (ActivityScenario<FollowingActivity> scenario = ActivityScenario.launch(FollowingActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.check).performClick());
            assertTrue(await(scenario, activity -> {
                View check = activity.findViewById(R.id.check);
                return check != null && check.isEnabled();
            }));
        }
    }

    private boolean await(ActivityScenario<FollowingActivity> scenario, Check check) throws Exception {
        for (int i = 0; i < 50; i++) {
            AtomicBoolean result = new AtomicBoolean();
            scenario.onActivity(activity -> result.set(check.value(activity)));
            if (result.get()) return true;
            Thread.sleep(100);
        }
        return false;
    }

    private interface Check {
        boolean value(FollowingActivity activity);
    }
}
