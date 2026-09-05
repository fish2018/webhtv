package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProxyPlaybackPolicyTest {

    @Test
    public void onlyEnabledRemoteProgressiveHttpPlaybackUsesProxy() {
        assertTrue(ProxyPlaybackPolicy.shouldProxy(true, "https://media.example.com/movie.mp4", "mp4"));
        assertTrue(ProxyPlaybackPolicy.shouldProxy(true, "http://media.example.com/play?id=1", ""));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(false, "https://media.example.com/movie.mp4", "mp4"));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(true, "https://media.example.com/live.m3u8", "m3u8"));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(true, "https://media.example.com/live", "hls"));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(true, "https://media.example.com/manifest.mpd", "dash"));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(true, "http://127.0.0.1:17575/v1/stream/id", "mp4"));
        assertFalse(ProxyPlaybackPolicy.shouldProxy(true, "file:///sdcard/movie.mp4", "mp4"));
    }
}