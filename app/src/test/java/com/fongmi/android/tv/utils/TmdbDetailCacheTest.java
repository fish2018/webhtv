package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TmdbDetailCacheTest {

    @Test
    public void putReturnsEmptyKeyForMissingItemOrDetail() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();

        assertTrue(TmdbDetailCache.put(null, detail, List.of()).isEmpty());
        assertTrue(TmdbDetailCache.put(item, null, List.of()).isEmpty());
    }

    @Test
    public void takeReturnsCachedDetailOnceForExpectedTmdbItem() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        JsonObject detail = new JsonObject();
        detail.addProperty("name", "庆余年");
        List<TmdbPerson> cast = new ArrayList<>();
        cast.add(new TmdbPerson(1, "张若昀", "范闲", "", "Acting", ""));

        String key = TmdbDetailCache.put(item, detail, cast);
        cast.clear();

        TmdbDetailCache.Entry entry = TmdbDetailCache.take(key, item);

        assertTrue(!key.isEmpty());
        assertSame(item, entry.getItem());
        assertSame(detail, entry.getDetail());
        assertEquals(1, entry.getCast().size());
        assertEquals("张若昀", entry.getCast().get(0).getName());
        assertNull(TmdbDetailCache.take(key, item));
    }

    @Test
    public void takeReturnsNullForMismatchedTmdbItem() {
        TmdbItem item = new TmdbItem(123, "tv", "庆余年", "", "", "", "");
        TmdbItem other = new TmdbItem(456, "tv", "庆余年 第二季", "", "", "", "");
        JsonObject detail = new JsonObject();

        String key = TmdbDetailCache.put(item, detail, List.of());

        assertNull(TmdbDetailCache.take(key, other));
    }
}
