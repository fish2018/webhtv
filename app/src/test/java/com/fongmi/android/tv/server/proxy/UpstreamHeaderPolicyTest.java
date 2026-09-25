package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpstreamHeaderPolicyTest {

    @Test
    public void keepsOnlyExplicitEndToEndAllowlistWithCanonicalNames() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("user-agent", "WebHtv-Test");
        input.put("COOKIE", "sid=1");
        input.put("Referer", "https://example.test/watch");
        input.put("Origin", "https://example.test");
        input.put("Authorization", "Bearer test");
        input.put("Accept", "video/mp4");
        input.put("Accept-Language", "zh-CN");
        input.put("Range", "bytes=5-");
        input.put("Accept-Encoding", "gzip");
        input.put("Host", "evil.test");
        input.put("Connection", "close");
        input.put("Content-Length", "999");
        input.put("X-Untrusted", "discarded");

        Map<String, String> sanitized = UpstreamHeaderPolicy.sanitize(input);

        assertEquals(7, sanitized.size());
        assertEquals("WebHtv-Test", sanitized.get("User-Agent"));
        assertEquals("sid=1", sanitized.get("Cookie"));
        assertEquals("Bearer test", sanitized.get("Authorization"));
        assertFalse(sanitized.containsKey("Range"));
        assertFalse(sanitized.containsKey("Accept-Encoding"));
        assertFalse(sanitized.containsKey("Host"));
        assertThrows(UnsupportedOperationException.class,
                () -> sanitized.put("User-Agent", "mutated"));
    }

    @Test
    public void rejectsBlankOrInjectionShapedValues() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("User-Agent", "  ");
        input.put("Cookie", "a=1\r\nX-Injected: yes");
        input.put("Referer", "https://safe.test/path");
        input.put("Authorization", null);

        Map<String, String> sanitized = UpstreamHeaderPolicy.sanitize(input);

        assertEquals(1, sanitized.size());
        assertTrue(sanitized.containsKey("Referer"));
    }
}