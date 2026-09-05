package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreCacheTest {

    @Test
    public void canPreCache_allowsRegularHttpMedia() {
        assertTrue(PreCache.canPreCache("https", "https://cdn.example.test/movie.mkv"));
        assertTrue(PreCache.canPreCache("http", "http://cdn.example.test/movie.mp4"));
    }

    @Test
    public void canPreCache_allowsLocalProxyMediaLikeUpstream() {
        // Local proxy URLs are passed through to ExoPlayer for jar-based multi-threading.
        assertTrue(PreCache.canPreCache("http", "http://127.0.0.1:9978/proxy?do=js"));
        assertTrue(PreCache.canPreCache("http", "http://localhost:9978/proxy?siteKey=drive"));
        assertTrue(PreCache.canPreCache("http", "http://[::1]:9978/proxy?do=py"));
        assertTrue(PreCache.canPreCache("http", "http://127.0.0.1:5000/proxy/1_4213_0_0"));
    }

    @Test
    public void canPreCache_skipsNonHttpAndConcatenatingMedia() {
        assertFalse(PreCache.canPreCache("file", "file:///sdcard/movie.mkv"));
        assertFalse(PreCache.canPreCache("https", "https://a.test/1.mp4|||1000***https://b.test/2.mp4|||1000"));
    }

    @Test
    public void workerUsabilityRejectsFailedAndStoppedThreads() throws Exception {
        Thread running = Thread.currentThread();
        Thread stopped = new Thread(() -> {});
        stopped.start();
        stopped.join();

        assertTrue(PreCache.isWorkerUsable(running, null));
        assertFalse(PreCache.isWorkerUsable(running, running));
        assertFalse(PreCache.isWorkerUsable(stopped, null));
        assertFalse(PreCache.isWorkerUsable(null, null));
    }
}
