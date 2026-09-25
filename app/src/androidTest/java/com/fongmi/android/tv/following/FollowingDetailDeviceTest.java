package com.fongmi.android.tv.following;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingDetailDeviceTest {

    @Test
    public void databaseLookupCanStartOnMainThreadWithoutMainThreadRoomAccess() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean mainThread = new AtomicBoolean();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                FollowingPlaybackBridge.findAsync("missing:following:key", item -> {
                    mainThread.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper());
                    latch.countDown();
                }));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue("callback should return to main thread", mainThread.get());
        assertFalse(FollowingStore.database().getFollowingDao().findAll().stream()
                .anyMatch(item -> "missing:following:key".equals(item.identityKey)));
    }
}
