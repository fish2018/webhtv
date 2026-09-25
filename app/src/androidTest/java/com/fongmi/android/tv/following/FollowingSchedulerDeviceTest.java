package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingSchedulerDeviceTest {

    private Context context;
    private boolean originalEnabled;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        originalEnabled = FollowingSettings.isEnabled();
        FollowingSettings.setEnabled(true);
        FollowingScheduler.cancelAll(context);
    }

    @After
    public void tearDown() {
        FollowingScheduler.cancelAll(context);
        FollowingSettings.setEnabled(originalEnabled);
    }

    @Test
    public void uniquePeriodicAndOneShotWorkAreEnqueuedAndCanceled() throws Exception {
        FollowingScheduler.ensurePeriodic(context);
        FollowingScheduler.enqueueDueNow(context);

        assertTrue(awaitState(FollowingScheduler.PERIODIC_WORK, false));
        assertTrue(awaitState(FollowingScheduler.ONE_SHOT_WORK, false));

        FollowingSettings.setEnabled(false);
        FollowingScheduler.cancelAll(context);

        assertTrue(awaitState(FollowingScheduler.PERIODIC_WORK, true));
        assertTrue(awaitState(FollowingScheduler.ONE_SHOT_WORK, true));
    }

    private boolean awaitState(String uniqueName, boolean canceled) throws Exception {
        for (int i = 0; i < 30; i++) {
            List<WorkInfo> infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(uniqueName)
                    .get(5, TimeUnit.SECONDS);
            if (canceled && infos.isEmpty()) return true;
            if (!infos.isEmpty() && infos.stream().allMatch(info -> canceled
                    ? info.getState() == WorkInfo.State.CANCELLED
                    : info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.RUNNING)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
