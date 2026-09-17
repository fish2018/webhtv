package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VodDetailCacheTest {

    @Test
    public void putReturnsEmptyKeyForNullVod() {
        assertTrue(VodDetailCache.put(null).isEmpty());
    }

    @Test
    public void takeReturnsCachedVodOnce() {
        Vod vod = new Vod();
        vod.setId("movie-1");
        vod.setName("Movie One");

        String key = VodDetailCache.put(vod);

        assertTrue(!key.isEmpty());
        assertSame(vod, VodDetailCache.take(key));
        assertNull(VodDetailCache.take(key));
    }

    @Test
    public void takeReturnsNullForBlankOrUnknownKey() {
        assertNull(VodDetailCache.take(""));
        assertNull(VodDetailCache.take("missing"));
    }

    @Test
    public void reusableContentSurvivesRepeatedReadsAndInvalidation() {
        String source = "site-" + System.nanoTime();
        String id = "movie-1";
        String json = "{\"list\":[{\"vod_id\":\"movie-1\"}]}";

        VodDetailCache.putContent(source, id, json, 1_000L);

        assertEquals(json, VodDetailCache.getContent(source, id, 1_001L));
        assertEquals(json, VodDetailCache.getContent(source, id, 1_002L));
        VodDetailCache.invalidateContent(source, id);
        assertNull(VodDetailCache.getContent(source, id, 1_003L));
    }

    @Test
    public void reusableContentExpiresAfterTtl() {
        String source = "site-expiring-" + System.nanoTime();
        String id = "movie-2";

        VodDetailCache.putContent(source, id, "cached", 2_000L);

        assertNull(VodDetailCache.getContent(source, id, 2_000L + VodDetailCache.CONTENT_TTL_MS + 1));
    }
}
