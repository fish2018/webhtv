package com.fongmi.android.tv.following;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowingDeleteDeviceTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private static final String IDENTITY = "tmdb:tv:993344:s1";

    @Before
    public void setUp() {
        FollowingStore.replaceAll(List.of(), List.of());
        Following item = new Following();
        item.identityKey = IDENTITY;
        item.seriesKey = "tmdb:tv:993344";
        item.vodName = "取消追更测试";
        item.trackedSeason = 1;
        item.enabled = true;
        FollowingSource source = new FollowingSource();
        source.followingKey = IDENTITY;
        source.siteKey = "site";
        source.vodId = "vod";
        FollowingStore.saveNew(item, source);
    }

    @After
    public void tearDown() {
        FollowingStore.replaceAll(List.of(), List.of());
    }

    @Test
    public void deleteStartedOnMainThreadCompletesWithoutRoomMainThreadCrash() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                FollowingPlaybackBridge.deleteAsync(IDENTITY, error -> {
                    failure.set(error);
                    latch.countDown();
                }));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertNull(FollowingStore.find(IDENTITY));
        assertTrue(FollowingStore.sources(IDENTITY).isEmpty());
    }
}
