package com.fongmi.android.tv.bean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EpisodePositionCacheTest {

    private File directory;
    private File cacheFile;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("episode-position-cache").toFile();
        cacheFile = new File(directory, "positions.json");
    }

    @After
    public void tearDown() {
        if (cacheFile.exists()) cacheFile.delete();
        directory.delete();
    }

    @Test
    public void enabledCacheRestoresPersistedEpisodePosition() {
        EpisodePositionCache cache = new EpisodePositionCache(() -> true, cacheFile);
        cache.put("site", "vod", "line", "episode-1", 90_000, 300_000);
        cache.save();

        EpisodePositionCache restored = new EpisodePositionCache(() -> true, cacheFile);
        EpisodePositionCache.EpisodePosition position = restored.get("site", "vod", "line", "episode-1");

        assertNotNull(position);
        assertEquals(90_000, position.position);
        assertEquals(300_000, position.duration);
    }

    @Test
    public void disabledCacheIgnoresExistingPositionsAndRejectsNewWrites() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        EpisodePositionCache cache = new EpisodePositionCache(enabled::get, cacheFile);
        cache.put("site", "vod", "line", "episode-1", 90_000, 300_000);

        enabled.set(false);
        assertNull(cache.get("site", "vod", "line", "episode-1"));
        cache.put("site", "vod", "line", "episode-2", 45_000, 300_000);

        enabled.set(true);
        assertNotNull(cache.get("site", "vod", "line", "episode-1"));
        assertNull(cache.get("site", "vod", "line", "episode-2"));
    }
}
