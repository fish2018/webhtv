package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HlsAdblockNoticeTest {

    @Test
    public void debouncesRepeatedPlaylistRefreshesWithinWindow() {
        String url = "https://video.example.com/live/notice-test.m3u8";

        assertTrue(HlsAdblockNotice.shouldNotify(url, 100_000L));
        assertFalse(HlsAdblockNotice.shouldNotify(url, 129_999L));
        assertTrue(HlsAdblockNotice.shouldNotify(url, 130_000L));
    }

    @Test
    public void tracksDifferentPlaylistsIndependently() {
        long now = 200_000L;

        assertTrue(HlsAdblockNotice.shouldNotify("https://a.example.com/live.m3u8", now));
        assertTrue(HlsAdblockNotice.shouldNotify("https://b.example.com/live.m3u8", now));
    }
}
