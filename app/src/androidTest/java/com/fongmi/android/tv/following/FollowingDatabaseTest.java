package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FollowingDatabaseTest {

    @Rule
    public final FollowingDeviceDataRule followingData = new FollowingDeviceDataRule();

    private Context context;
    private FollowingDatabase database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, FollowingDatabase.class).build();
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
    }

    @Test
    public void createsIndependentDatabaseAndStoresFollowAndSource() {
        Following item = new Following();
        item.identityKey = "tmdb:tv:1:s1";
        item.seriesKey = "tmdb:tv:1";
        item.vodName = "Example";
        item.trackedSeason = 1;
        item.enabled = true;
        item.nextCheckAt = 10;
        database.getFollowingDao().insertOrUpdate(item);

        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.siteKey = "site";
        source.vodId = "vod";
        source.preferred = true;
        database.getFollowingSourceDao().insertOrUpdate(source);

        assertNotNull(database.getFollowingDao().find(item.identityKey));
        assertEquals(item.identityKey, database.getFollowingDao().findDue(20, 5).get(0).identityKey);
        assertEquals("vod", database.getFollowingSourceDao().findPreferred(item.identityKey).vodId);
    }
}
