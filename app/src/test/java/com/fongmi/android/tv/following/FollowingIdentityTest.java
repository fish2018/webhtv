package com.fongmi.android.tv.following;

import com.fongmi.android.tv.bean.TmdbItem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FollowingIdentityTest {

    @Test
    public void tmdbIdentityIsStableAndSeasonScoped() {
        TmdbItem item = new TmdbItem(1399, "TV", "Title", "", "", "", "");

        assertEquals("tmdb:tv:1399:s2", FollowingIdentity.identityKey(item, 2));
        assertEquals("tmdb:tv:1399", FollowingIdentity.seriesKey(item));
    }

    @Test
    public void sourceIdentityNormalizesSiteAndVodId() {
        assertEquals("source:7:site:vod:s1", FollowingIdentity.identityKey(7, " Site ", " VOD ", 1));
    }
}
