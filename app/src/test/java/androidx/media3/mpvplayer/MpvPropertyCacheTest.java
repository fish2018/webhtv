package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.List;
import java.util.Map;

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
        assertEquals(7, cache.getInt(null, 7));
        assertFalse(cache.contains(null));
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
    public void derivesTrackListChildrenFromNodeSnapshot() {
        MpvPropertyCache cache = new MpvPropertyCache();
        cache.put("track-list", new MpvPropertySnapshot.TrackList(List.of(
                Map.of("id", 2, "type", "audio", "codec", "aac"),
                Map.of("id", 3, "type", "sub", "codec", "subrip")), true));

        assertEquals(2, cache.getInt("track-list/count", 0));
        assertEquals("sub", cache.getString("track-list/1/type", ""));
        assertEquals(3, cache.getInt("track-list/1/id", 0));
        assertTrue(cache.contains("track-list/1/codec"));
        assertFalse(cache.contains("track-list/2/type"));

        cache.put("track-list", null);
        assertEquals(0, cache.getInt("track-list/count", 0));
    }

    @Test
    public void clearDropsValuesFromPreviousMedia() {
        MpvPropertyCache cache = new MpvPropertyCache();
        cache.put("duration", 10.0);
        cache.clear();

        assertFalse(cache.contains("duration"));
    }
}
