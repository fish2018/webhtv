package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PanEndpointParserTest {

    @Test
    public void parsesQuarkPvideoEndpointWithoutExposingSecrets() {
        String upstream = "https://dl-pc-zb.drive.quark.cn/file?token=secret";
        String headers = "{\"Cookie\":\"private-cookie\",\"Referer\":\"https://pan.quark.cn/\",\"User-Agent\":\"UA\"}";
        PanEndpoint endpoint = PanEndpointParser.parse(local(upstream, headers, 16), Map.of());

        assertEquals(PanProvider.QUARK, endpoint.provider());
        assertEquals(16, endpoint.configuredThreads());
        assertEquals(upstream, endpoint.upstreamUrl());
        assertEquals("private-cookie", endpoint.upstreamHeaders().get("Cookie"));
        assertFalse(endpoint.toString().contains("secret"));
        assertFalse(endpoint.toString().contains("private-cookie"));
    }

    @Test
    public void classifiesBaiduCdnAndAllowsMaximumConfiguredThreads() {
        String upstream = "https://d.pcs.baidu.com/file?sign=private";
        PanEndpoint endpoint = PanEndpointParser.parse(local(upstream, "{\"User-Agent\":\"netdisk\"}", 256), Map.of());

        assertEquals(PanProvider.BAIDU, endpoint.provider());
        assertEquals(256, endpoint.configuredThreads());
        assertTrue(endpoint.hasDirectUpstream());
    }

    @Test
    public void missingDirectUrlKeepsProxyOnlyEvidence() {
        PanEndpoint endpoint = PanEndpointParser.parse("http://127.0.0.1:9978/proxy?id=opaque", Map.of("User-Agent", "UA"));

        assertFalse(endpoint.hasDirectUpstream());
        assertEquals(PanProvider.GENERIC, endpoint.provider());
        assertEquals("UA", endpoint.upstreamHeaders().get("User-Agent"));
    }

    @Test
    public void stopsDecodingAfterHeaderJsonIsValidSoCookieEscapesStayIntact() {
        String upstream = "https://dl-pc-zb.drive.quark.cn/file?token=abc%2Fdef";
        String headers = "{\"Cookie\":\"session=abc%2Fdef%3D\",\"Referer\":\"https://pan.quark.cn\"}";
        PanEndpoint endpoint = PanEndpointParser.parse(local(upstream, headers, 8), Map.of());

        assertEquals(upstream, endpoint.upstreamUrl());
        assertEquals("session=abc%2Fdef%3D", endpoint.upstreamHeaders().get("Cookie"));
    }


    @Test
    public void rejectsPrivateAndNonCanonicalLoopbackUpstreamTargets() {
        for (String upstream : new String[]{
                "http://127.0.0.2/admin",
                "http://0.0.0.0/admin",
                "http://10.0.0.1/admin",
                "http://172.16.0.1/admin",
                "http://192.168.1.1/admin",
                "http://169.254.169.254/latest/meta-data",
                "http://[fc00::1]/admin",
                "http://[fe80::1]/admin"}) {
            try {
                PanEndpointParser.parse(local(upstream, "{}", 8), Map.of());
                fail("Should reject private target: " + upstream);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Private upstream target"));
            }
        }
    }

    @Test
    public void keepsPublicLiteralUpstreamTarget() {
        PanEndpoint endpoint = PanEndpointParser.parse(local("https://8.8.8.8/file", "{}", 8), Map.of());
        assertTrue(endpoint.hasDirectUpstream());
    }

    private static String local(String upstream, String headers, int threads) {
        return "http://127.0.0.1:1314/?url=" + encode(upstream) + "&header=" + encode(headers) + "&thread=" + threads;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
