package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MediaSourceFactoryTest {

    @Test
    public void sanitizeHeaders_trimsKeysAndValues() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(" Referer ", " https://movie.douban.com ");
        headers.put(" ", "ignored");
        headers.put("Accept", null);

        Map<String, String> sanitized = MediaSourceFactory.sanitizeHeaders(headers);

        assertEquals(1, sanitized.size());
        assertEquals("https://movie.douban.com", sanitized.get("Referer"));
    }

    @Test
    public void removeUserAgentHeader_extractsCaseInsensitively() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("referer", "https://example.test");
        headers.put("user-agent", " Custom UA ");

        String userAgent = MediaSourceFactory.removeUserAgentHeader(headers);

        assertEquals("Custom UA", userAgent);
        assertEquals(1, headers.size());
        assertNull(headers.get("user-agent"));
        assertEquals("https://example.test", headers.get("referer"));
    }

    @Test
    public void isHlsUrl_recognizesLiveAliases() {
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/play?type=hls&id=1"));
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/live/stream?id=1"));
        assertTrue(MediaSourceFactory.isHlsUrl("https://example.test/tv/live.php?id=1"));
        assertFalse(MediaSourceFactory.isHlsUrl("https://example.test/video.mp4"));
    }

}
