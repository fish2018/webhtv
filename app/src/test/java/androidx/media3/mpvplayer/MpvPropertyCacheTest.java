package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvPropertyCacheTest {

    @Test
    public void storesObservedValuesWithTypedFallbacks() {
        MpvPropertyCache cache = new MpvPropertyCache();
        cache.put("width", 3840L);
        cache.put("fps", 59.94);
        cache.put("selected", true);
        cache.put("codec", "hevc");

        assertEquals(3840, cache.getInt("width", 0));
        assertEquals(59.94, cache.getDouble("fps", 0), 0.001);
        assertTrue(cache.getBoolean("selected", false));
        assertEquals("hevc", cache.getString("codec", ""));
        assertEquals(7, cache.getInt("missing", 7));
    }

    @Test
    public void unavailableValuesRemoveStaleEntries() {
        MpvPropertyCache cache = new MpvPropertyCache();
        cache.put("track-list/0/title", "old");
        cache.put("track-list/0/title", null);

        assertFalse(cache.contains("track-list/0/title"));
        assertEquals("fallback", cache.getString("track-list/0/title", "fallback"));
    }

    @Test
    public void clearDropsValuesFromPreviousMedia() {
        MpvPropertyCache cache = new MpvPropertyCache();
        cache.put("duration", 10.0);
        cache.clear();

        assertFalse(cache.contains("duration"));
    }
}