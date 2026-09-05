package com.fongmi.android.tv.server.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SegmentedRangeEngineTest {

    private static final String PAYLOAD = "ABCDEFGHIJKL";

    private MockWebServer server;
    private AtomicInteger requests;
    private AtomicBoolean failFirstShard;
    private AtomicInteger shardFailures;

    @Before
    public void setUp() throws IOException {
        requests = new AtomicInteger();
        failFirstShard = new AtomicBoolean();
        shardFailures = new AtomicInteger();
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                requests.incrementAndGet();
                String range = request.getHeaders().get("Range");
                if (failFirstShard.get()
                        && "bytes=4-7".equals(range)
                        && shardFailures.getAndIncrement() == 0) {
                    return new MockResponse.Builder().code(500).body("retry").build();
                }
                if ("bytes=0-3".equals(range)) Thread.sleep(100);
                long[] bounds = parseRange(range);
                int start = Math.toIntExact(bounds[0]);
                int end = Math.toIntExact(bounds[1]);
                return new MockResponse.Builder()
                        .code(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + PAYLOAD.length())
                        .setHeader("ETag", "\"asset-v1\"")
                        .body(PAYLOAD.substring(start, end + 1))
                        .build();
            }
        });
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) server.close();
    }

    @Test
    public void downloadsShardsConcurrentlyButWritesInRangeOrder() throws Exception {
        ProxyRuntimeConfig config = config();
        ProxySessionRegistry sessions = new ProxySessionRegistry(config.maxSessions());
        UpstreamRangeClient upstream = new UpstreamRangeClient(new OkHttpClient.Builder().build(), config);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (SegmentedRangeEngine engine = new SegmentedRangeEngine(config, upstream, sessions)) {
            SegmentedRangeEngine.TransferResult result = engine.transfer(
                    server.url("/asset.bin").toString(),
                    Map.of("User-Agent", "WebHtv-Engine"),
                    "bytes=0-11",
                    output);

            assertEquals(SegmentedRangeEngine.Decision.PARALLEL, result.decision());
            assertEquals(PAYLOAD.length(), result.bytesWritten());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), output.toByteArray());
            assertEquals(4, requests.get());
            assertEquals(0, sessions.activeCount());
        }
    }

    @Test
    public void retriesTransientShardFailureWithinConfiguredBudget() throws Exception {
        failFirstShard.set(true);
        ProxyRuntimeConfig config = config(1);
        ProxySessionRegistry sessions = new ProxySessionRegistry(config.maxSessions());
        UpstreamRangeClient upstream = new UpstreamRangeClient(new OkHttpClient.Builder().build(), config);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (SegmentedRangeEngine engine = new SegmentedRangeEngine(config, upstream, sessions)) {
            SegmentedRangeEngine.TransferResult result = engine.transfer(
                    server.url("/asset.bin").toString(),
                    Map.of(),
                    "bytes=0-11",
                    output);

            assertEquals(SegmentedRangeEngine.Decision.PARALLEL, result.decision());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), output.toByteArray());
            assertEquals(5, requests.get());
        }
    }

    @Test
    public void countModeStreamsBlocksWithinBudgetBeforeLogicalShardFinishes() throws Exception {
        String payload = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        CountDownLatch secondBlockRequested = new CountDownLatch(1);
        CountDownLatch releaseSecondBlock = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                requests.incrementAndGet();
                String range = request.getHeaders().get("Range");
                long[] bounds = parseRange(range);
                int start = Math.toIntExact(bounds[0]);
                int end = Math.toIntExact(bounds[1]);
                if ("bytes=8-15".equals(range)) {
                    secondBlockRequested.countDown();
                    releaseSecondBlock.await(2, TimeUnit.SECONDS);
                }
                return new MockResponse.Builder()
                        .code(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + payload.length())
                        .setHeader("ETag", "\"asset-v1\"")
                        .body(payload.substring(start, end + 1))
                        .build();
            }
        });
        ProxyRuntimeConfig config = countModeConfig();
        ProxySessionRegistry sessions = new ProxySessionRegistry(config.maxSessions());
        UpstreamRangeClient upstream = new UpstreamRangeClient(new OkHttpClient.Builder().build(), config);
        CountDownLatch firstWrite = new CountDownLatch(1);
        FirstWriteOutput output = new FirstWriteOutput(firstWrite);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (SegmentedRangeEngine engine = new SegmentedRangeEngine(config, upstream, sessions)) {
            Future<SegmentedRangeEngine.TransferResult> transfer = executor.submit(() -> engine.transfer(
                    server.url("/asset.bin").toString(), Map.of(), "bytes=0-63", output));

            assertTrue(secondBlockRequested.await(2, TimeUnit.SECONDS));
            assertTrue(firstWrite.await(2, TimeUnit.SECONDS));
            assertFalse(transfer.isDone());
            releaseSecondBlock.countDown();

            SegmentedRangeEngine.TransferResult result = transfer.get(5, TimeUnit.SECONDS);
            assertEquals(SegmentedRangeEngine.Decision.PARALLEL, result.decision());
            assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), output.toByteArray());
        } finally {
            releaseSecondBlock.countDown();
            executor.shutdownNow();
            sessions.close();
        }
    }

    @Test
    public void configuredReorderWindowCapsScheduledShardLookahead() throws Exception {
        String payload = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345";
        CountDownLatch secondBlockRequested = new CountDownLatch(1);
        CountDownLatch thirdBlockRequested = new CountDownLatch(1);
        CountDownLatch releaseSecondBlock = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                requests.incrementAndGet();
                String range = request.getHeaders().get("Range");
                long[] bounds = parseRange(range);
                int start = Math.toIntExact(bounds[0]);
                int end = Math.toIntExact(bounds[1]);
                if ("bytes=4-7".equals(range)) {
                    secondBlockRequested.countDown();
                    releaseSecondBlock.await(2, TimeUnit.SECONDS);
                } else if ("bytes=8-11".equals(range)) {
                    thirdBlockRequested.countDown();
                }
                return new MockResponse.Builder()
                        .code(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + payload.length())
                        .setHeader("ETag", "\"asset-v1\"")
                        .body(payload.substring(start, end + 1))
                        .build();
            }
        });
        ProxyRuntimeConfig config = config(0, 1);
        ProxySessionRegistry sessions = new ProxySessionRegistry(config.maxSessions());
        UpstreamRangeClient upstream = new UpstreamRangeClient(new OkHttpClient.Builder().build(), config);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (SegmentedRangeEngine engine = new SegmentedRangeEngine(config, upstream, sessions)) {
            Future<SegmentedRangeEngine.TransferResult> transfer = executor.submit(() -> engine.transfer(
                    server.url("/asset.bin").toString(), Map.of(), "bytes=0-31", new ByteArrayOutputStream()));

            assertTrue(secondBlockRequested.await(2, TimeUnit.SECONDS));
            assertFalse(thirdBlockRequested.await(200, TimeUnit.MILLISECONDS));
            releaseSecondBlock.countDown();
            assertTrue(thirdBlockRequested.await(2, TimeUnit.SECONDS));
            assertEquals(SegmentedRangeEngine.Decision.PARALLEL, transfer.get(5, TimeUnit.SECONDS).decision());
        } finally {
            releaseSecondBlock.countDown();
            executor.shutdownNow();
            sessions.close();
        }
    }
    @Test
    public void refusesNewTransferWhenGlobalSessionBudgetIsExhausted() throws Exception {
        ProxyRuntimeConfig config = config();
        ProxySessionRegistry sessions = new ProxySessionRegistry(1);
        ProxySessionRegistry.Lease occupied = sessions.tryAcquire(() -> {}).orElseThrow();
        UpstreamRangeClient upstream = new UpstreamRangeClient(new OkHttpClient.Builder().build(), config);

        try (SegmentedRangeEngine engine = new SegmentedRangeEngine(config, upstream, sessions)) {
            SegmentedRangeEngine.TransferResult result = engine.transfer(
                    server.url("/asset.bin").toString(), Map.of(), "bytes=0-11", new ByteArrayOutputStream());

            assertEquals(SegmentedRangeEngine.Decision.SESSION_LIMIT, result.decision());
            assertEquals(0, requests.get());
            assertFalse(result.isParallel());
        } finally {
            occupied.close();
            sessions.close();
        }
    }

    private static long[] parseRange(String value) {
        String interval = value.substring("bytes=".length());
        int dash = interval.indexOf("-");
        return new long[]{
                Long.parseLong(interval.substring(0, dash)),
                Long.parseLong(interval.substring(dash + 1))};
    }

    private static ProxyRuntimeConfig countModeConfig() {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(), true, ProxyRuntimeConfig.PortMode.AUTO, ProxyRuntimeConfig.AUTO_PORT,
                defaults.serverWorkers(), defaults.connectionQueueCapacity(), 2, 2,
                ProxyRuntimeConfig.ShardMode.COUNT, 2, 8, 1, 2, 16, 0,
                defaults.connectTimeoutMillis(), defaults.readTimeoutMillis(), 0);
    }

    private static final class FirstWriteOutput extends ByteArrayOutputStream {

        private final CountDownLatch firstWrite;

        private FirstWriteOutput(CountDownLatch firstWrite) {
            this.firstWrite = firstWrite;
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            super.write(bytes, offset, length);
            if (length > 0) firstWrite.countDown();
        }
    }

    private static ProxyRuntimeConfig config() {
        return config(0);
    }

    private static ProxyRuntimeConfig config(int retryCount) {
        return config(retryCount, 3);
    }

    private static ProxyRuntimeConfig config(int retryCount, int reorderWindowBlocks) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(), true, ProxyRuntimeConfig.PortMode.AUTO, ProxyRuntimeConfig.AUTO_PORT,
                defaults.serverWorkers(), defaults.connectionQueueCapacity(), 3, 3,
                ProxyRuntimeConfig.ShardMode.SIZE, ProxyRuntimeConfig.AUTO_SHARD_COUNT, 4, 1, reorderWindowBlocks, 12, retryCount,
                defaults.connectTimeoutMillis(), defaults.readTimeoutMillis(), 0);
    }
}
