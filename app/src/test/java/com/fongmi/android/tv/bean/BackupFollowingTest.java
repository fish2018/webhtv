package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingSource;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupFollowingTest {

    @Test
    public void oldBackupWithoutFollowingPayloadPreservesLocalData() {
        Backup backup = Backup.objectFrom("{\"config\":[]}");

        assertFalse(backup.hasFollowingPayload());
    }

    @Test
    public void followingPayloadRoundTripsWithSchemaVersion() {
        Backup backup = new Backup();
        Following item = new Following();
        item.identityKey = "tmdb:tv:1:s1";
        item.vodName = "Example";
        FollowingSource source = new FollowingSource();
        source.followingKey = item.identityKey;
        source.siteKey = "site";
        source.vodId = "vod";
        backup.setFollowing(java.util.List.of(item));
        backup.setFollowingSource(java.util.List.of(source));
        backup.setFollowingSchemaVersion(1);

        Backup parsed = Backup.objectFrom(new Gson().toJson(backup));

        assertTrue(parsed.hasFollowingPayload());
        assertEquals(1, parsed.getFollowing().size());
        assertEquals("Example", parsed.getFollowing().get(0).vodName);
        assertEquals("vod", parsed.getFollowingSource().get(0).vodId);
    }
}
