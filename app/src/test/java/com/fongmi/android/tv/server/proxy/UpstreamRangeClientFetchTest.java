package com.fongmi.android.tv.server.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class UpstreamRangeClientFetchTest {

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
    public void fetchesExactShardWithIdentityAndIfRange() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 100-199/1000")
                .setHeader("ETag", "\"asset-v1\"")
                .body("x".repeat(100))
                .build());
        UpstreamRangeClient client = client();
        UpstreamResourceValidator validator = UpstreamResourceValidator.select("\"asset-v1\"", null);

        byte[] bytes = client.fetchShard(
                server.url("/asset.mp4").toString(),
                Map.of("User-Agent", "WebHtv-Shard"),
                new RangePlanner.Shard(7, 100, 199),
                1_000,
                validator);
        RecordedRequest request = server.takeRequest();

        assertEquals(100, bytes.length);
        assertEquals("bytes=100-199", request.getHeaders().get("Range"));
        assertEquals("identity", request.getHeaders().get("Accept-Encoding"));
        assertEquals("\"asset-v1\"", request.getHeaders().get("If-Range"));
        assertEquals("WebHtv-Shard", request.getHeaders().get("User-Agent"));
    }

    @Test
    public void detectsChangedResourceAndIfRangeFallbackStatuses() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-9/100")
                .setHeader("ETag", "\"asset-v2\"")
                .body("0123456789")
                .build());
        server.enqueue(new MockResponse.Builder().code(200).body("whole").build());
        server.enqueue(new MockResponse.Builder().code(412).build());
        UpstreamRangeClient client = client();
        UpstreamResourceValidator validator = UpstreamResourceValidator.select("\"asset-v1\"", null);
        RangePlanner.Shard shard = new RangePlanner.Shard(0, 0, 9);

        assertStatus(RangeResponseValidator.Status.RESOURCE_CHANGED,
                assertThrows(UpstreamRangeClient.RangeFetchException.class,
                        () -> client.fetchShard(server.url("/changed").toString(), Map.of(), shard, 100, validator)));
        assertStatus(RangeResponseValidator.Status.FALLBACK_SINGLE_CONNECTION,
                assertThrows(UpstreamRangeClient.RangeFetchException.class,
                        () -> client.fetchShard(server.url("/fallback").toString(), Map.of(), shard, 100, validator)));
        assertStatus(RangeResponseValidator.Status.PRECONDITION_FAILED,
                assertThrows(UpstreamRangeClient.RangeFetchException.class,
                        () -> client.fetchShard(server.url("/precondition").toString(), Map.of(), shard, 100, validator)));
    }

    @Test
    public void rejectsTruncatedAndOversizedShardBodies() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-9/100")
                .setHeader("ETag", "\"asset-v1\"")
                .body("short")
                .build());
        server.enqueue(new MockResponse.Builder()
                .code(206)
                .setHeader("Content-Range", "bytes 0-9/100")
                .setHeader("ETag", "\"asset-v1\"")
                .body("0123456789extra")
                .build());
        UpstreamRangeClient client = client();
        UpstreamResourceValidator validator = UpstreamResourceValidator.select("\"asset-v1\"", null);
        RangePlanner.Shard shard = new RangePlanner.Shard(0, 0, 9);

        assertStatus(RangeResponseValidator.Status.BODY_LENGTH_MISMATCH,
                assertThrows(UpstreamRangeClient.RangeFetchException.class,
                        () -> client.fetchShard(server.url("/short").toString(), Map.of(), shard, 100, validator)));
        assertStatus(RangeResponseValidator.Status.BODY_LENGTH_MISMATCH,
                assertThrows(UpstreamRangeClient.RangeFetchException.class,
                        () -> client.fetchShard(server.url("/long").toString(), Map.of(), shard, 100, validator)));
    }

    private static void assertStatus(
            RangeResponseValidator.Status expected,
            UpstreamRangeClient.RangeFetchException error) {
        assertEquals(expected, error.status());
    }

    private static UpstreamRangeClient client() {
        return new UpstreamRangeClient(new OkHttpClient.Builder().build(), config());
    }

    private static ProxyRuntimeConfig config() {
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
                0);
    }
}