package com.fongmi.android.tv.server.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class UpstreamRangeClientTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
    }

    @Test
    public void probeSendsIdentitySingleByteRangeAndSanitizedHeaders() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .setHeader("Content-Type", "video/mp4")
                .setHeader("ETag", "\"asset-v1\"")
                .body("x")
                .build());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "WebHtv-Probe");
        headers.put("Cookie", "sid=1");
        headers.put("Range", "bytes=5-");
        headers.put("Accept-Encoding", "gzip");
        headers.put("Host", "evil.test");

        UpstreamRangeClient.ProbeResult result = client(0).probe(
                server.url("/video.mp4").toString(), headers);
        RecordedRequest recorded = server.takeRequest();

        assertTrue(result.canParallel());
        assertEquals(UpstreamRangeClient.Decision.PARALLEL, result.decision());
        assertEquals(1_000, result.totalLength());
        assertEquals(UpstreamResourceValidator.Type.STRONG_ETAG, result.validator().type());
        assertEquals("bytes=0-0", recorded.getHeaders().get("Range"));
        assertEquals("identity", recorded.getHeaders().get("Accept-Encoding"));
        assertEquals("WebHtv-Probe", recorded.getHeaders().get("User-Agent"));
        assertEquals("sid=1", recorded.getHeaders().get("Cookie"));
        assertFalse("evil.test".equals(recorded.getHeaders().get("Host")));
    }

    @Test
    public void mapsIgnoredRangeToSingleConnectionFallbackWithoutRequiringValidator() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "video/mp4")
                .body("whole response")
                .build());

        UpstreamRangeClient.ProbeResult result = client(0).probe(
                server.url("/ignored-range.mp4").toString(), Map.of());

        assertEquals(UpstreamRangeClient.Decision.FALLBACK_SINGLE_CONNECTION, result.decision());
        assertEquals(RangeResponseValidator.Status.FALLBACK_SINGLE_CONNECTION, result.validationStatus());
        assertFalse(result.canParallel());
    }

    @Test
    public void rejectsEncodedAndTruncatedPartialResponses() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .setHeader("Content-Encoding", "gzip")
                .setHeader("ETag", "\"asset-v1\"")
                .body("x")
                .build());
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .setHeader("ETag", "\"asset-v1\"")
                .body("")
                .build());

        UpstreamRangeClient.ProbeResult encoded = client(0).probe(
                server.url("/encoded.mp4").toString(), Map.of());
        UpstreamRangeClient.ProbeResult truncated = client(0).probe(
                server.url("/truncated.mp4").toString(), Map.of());

        assertEquals(UpstreamRangeClient.Decision.INVALID_RESPONSE, encoded.decision());
        assertEquals(RangeResponseValidator.Status.ENCODED_RESPONSE, encoded.validationStatus());
        assertEquals(UpstreamRangeClient.Decision.INVALID_RESPONSE, truncated.decision());
        assertEquals(RangeResponseValidator.Status.BODY_LENGTH_MISMATCH, truncated.validationStatus());
    }

    @Test
    public void reportsThresholdAndMissingValidatorFallbackReasons() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/100")
                .setHeader("ETag", "\"small\"")
                .body("x")
                .build());
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .body("x")
                .build());

        UpstreamRangeClient.ProbeResult small = client(100).probe(
                server.url("/small.mp4").toString(), Map.of());
        UpstreamRangeClient.ProbeResult unverified = client(0).probe(
                server.url("/unverified.mp4").toString(), Map.of());

        assertEquals(UpstreamRangeClient.Decision.BELOW_THRESHOLD, small.decision());
        assertEquals(UpstreamRangeClient.Decision.MISSING_VALIDATOR, unverified.decision());
        assertNull(unverified.validator().ifRangeValue());
    }

    @Test
    public void probeFollowsSameOriginRedirectAndKeepsAllowedHeaders() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(302)
                .setHeader("Location", "/final.mp4")
                .build());
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .setHeader("ETag", "\"asset-v1\"")
                .body("x")
                .build());
        Map<String, String> headers = Map.of(
                "User-Agent", "WebHtv-Redirect",
                "Cookie", "sid=1",
                "Authorization", "Bearer token");

        UpstreamRangeClient.ProbeResult result = client(0).probe(
                server.url("/redirect.mp4").toString(), headers);
        server.takeRequest();
        RecordedRequest redirected = server.takeRequest();

        assertTrue(result.canParallel());
        assertEquals(server.url("/final.mp4").toString(), result.finalUrl());
        assertEquals("sid=1", redirected.getHeaders().get("Cookie"));
        assertEquals("Bearer token", redirected.getHeaders().get("Authorization"));
        assertEquals("sid=1", result.forwardHeaders().get("Cookie"));
    }

    @Test
    public void crossOriginRedirectDropsSensitiveHeadersForProbeAndShards() throws Exception {
        MockWebServer redirectedServer = new MockWebServer();
        redirectedServer.start();
        try {
            server.enqueue(new MockResponse.Builder()
                    .code(302)
                    .setHeader("Location", redirectedServer.url("/final.mp4").toString())
                    .build());
            redirectedServer.enqueue(new MockResponse.Builder()
                    .code(206)
                    .setHeader("Content-Range", "bytes 0-0/1000")
                    .setHeader("ETag", "\"asset-v1\"")
                    .body("x")
                    .build());
            redirectedServer.enqueue(new MockResponse.Builder()
                    .code(206)
                    .setHeader("Content-Range", "bytes 0-9/1000")
                    .setHeader("ETag", "\"asset-v1\"")
                    .body("0123456789")
                    .build());
            Map<String, String> headers = Map.of(
                    "User-Agent", "WebHtv-Redirect",
                    "Cookie", "sid=1",
                    "Authorization", "Bearer token",
                    "Origin", "https://source.example",
                    "Referer", "https://source.example/page");
            UpstreamRangeClient client = client(0);

            UpstreamRangeClient.ProbeResult result = client.probe(
                    server.url("/redirect.mp4").toString(), headers);
            RecordedRequest probe = redirectedServer.takeRequest();
            byte[] shard = client.fetchShard(
                    result.finalUrl(),
                    result.forwardHeaders(),
                    new RangePlanner.Shard(0, 0, 9),
                    result.totalLength(),
                    result.validator());
            RecordedRequest fetch = redirectedServer.takeRequest();

            assertEquals("0123456789", new String(shard));
            assertEquals("WebHtv-Redirect", probe.getHeaders().get("User-Agent"));
            assertNull(probe.getHeaders().get("Cookie"));
            assertNull(probe.getHeaders().get("Authorization"));
            assertEquals("https://source.example", probe.getHeaders().get("Origin"));
            assertEquals("https://source.example/page", probe.getHeaders().get("Referer"));
            assertNull(fetch.getHeaders().get("Cookie"));
            assertNull(fetch.getHeaders().get("Authorization"));
        } finally {
            redirectedServer.close();
        }
    }

    @Test
    public void singleConnectionFollowsCrossOriginRedirectWithoutCredentials() throws Exception {
        MockWebServer redirectedServer = new MockWebServer();
        redirectedServer.start();
        try {
            server.enqueue(new MockResponse.Builder()
                    .code(302)
                    .setHeader("Location", redirectedServer.url("/whole.mp4").toString())
                    .build());
            redirectedServer.enqueue(new MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "video/mp4")
                    .body("whole")
                    .build());
            UpstreamRangeClient client = client(0);

            try (UpstreamRangeClient.SingleResponse response = client.singleRequest(
                    server.url("/redirect.mp4").toString(),
                    Map.of("Cookie", "sid=1", "Authorization", "Bearer token"),
                    null,
                    false).execute()) {
                assertEquals(200, response.statusCode());
            }
            RecordedRequest redirected = redirectedServer.takeRequest();
            assertNull(redirected.getHeaders().get("Cookie"));
            assertNull(redirected.getHeaders().get("Authorization"));
        } finally {
            redirectedServer.close();
        }
    }
    @Test
    public void singleResponseCloseIsIdempotent() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("whole").build());
        UpstreamRangeClient client = client(0);
        UpstreamRangeClient.SingleResponse response = client.singleRequest(
                server.url("/whole.mp4").toString(), Map.of(), null, false).execute();

        response.close();
        response.close();

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void cancellingSingleRequestBeforeExecutePreventsNetworkCall() {
        UpstreamRangeClient.SingleRequest request = client(0).singleRequest(
                server.url("/cancelled.mp4").toString(),
                Map.of(),
                null,
                false);

        request.cancel();

        assertThrows(IOException.class, request::execute);
        assertEquals(0, server.getRequestCount());
    }
    private static UpstreamRangeClient client(long thresholdBytes) {
        return new UpstreamRangeClient(new OkHttpClient.Builder().build(), config(thresholdBytes));
    }

    private static ProxyRuntimeConfig config(long thresholdBytes) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                true,
                ProxyRuntimeConfig.PortMode.AUTO,
                ProxyRuntimeConfig.AUTO_PORT,
                defaults.serverWorkers(),
                defaults.connectionQueueCapacity(),
                defaults.rangeWorkers(),
                defaults.rangeConcurrency(),
                defaults.shardMode(),
                defaults.shardCount(),
                defaults.chunkSizeBytes(),
                defaults.maxSessions(),
                defaults.reorderWindowBlocks(),
                defaults.bufferBudgetBytes(),
                defaults.retryCount(),
                defaults.connectTimeoutMillis(),
                defaults.readTimeoutMillis(),
                thresholdBytes);
    }
}