package com.fongmi.android.tv.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Playback-session debounce for repeated live-playlist refreshes. */
public final class HlsAdblockNotice {

    private static final long WINDOW_MS = 30_000L;
    private static final int MAX_ENTRIES = 64;
    private static final Map<String, Long> RECENT = new LinkedHashMap<>();

    private HlsAdblockNotice() {}

    public static synchronized boolean shouldNotify(String playlistUrl, long nowMs) {
        String key = playlistUrl == null ? "" : playlistUrl;
        Long previous = RECENT.get(key);
        if (previous != null && nowMs - previous < WINDOW_MS) return false;
        RECENT.put(key, nowMs);
        while (RECENT.size() > MAX_ENTRIES) RECENT.remove(RECENT.keySet().iterator().next());
        return true;
    }
}
