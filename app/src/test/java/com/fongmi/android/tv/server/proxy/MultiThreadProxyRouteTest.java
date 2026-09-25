package com.fongmi.android.tv.server.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyRouteTest {

    private static final String PAYLOAD = "ABCDEFGHIJKL";

    private MockWebServer upstream;
    private AtomicInteger upstreamRequests;
    private AtomicBoolean ignoreRanges;
    private AtomicBoolean unsatisfiable;
    private AtomicBoolean slowProbe;
    private AtomicBoolean failBootstrap;
    private AtomicInteger activeCalls;
    private AtomicReference<String> lastMethod;
    private AtomicReference<String> lastCookie;
    private ProxyCapabilityToken capabilityToken;
    private MultiThreadProxyServer proxy;

    @Before
    public void setUp() throws Exception {
        upstreamRequests = new AtomicInteger();
        ignoreRanges = new AtomicBoolean();
        unsatisfiable = new AtomicBoolean();
        slowProbe = new AtomicBoolean();
        failBootstrap = new AtomicBoolean();
        activeCalls = new AtomicInteger();
        lastMethod = new AtomicReference<>();
        lastCookie = new AtomicReference<>();
        upstream = new MockWebServer();
        upstream.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                upstreamRequests.incrementAndGet();
                lastMethod.set(request.getMethod());
                lastCookie.set(request.getHeaders().get("Cookie"));
                if ("HEAD".equals(request.getMethod())) {
                    return new MockResponse.Builder()
                            .code(200)
                            .setHeader("Content-Type", "video/mp4")
                            .setHeader("Content-Length", PAYLOAD.length())
                            .setHeader("ETag", "\"asset-v1\"")
                            .build();
                }
                if (unsatisfiable.get()) {
                    return new MockResponse.Builder()
                            .code(416)
                            .setHeader("Content-Range", "bytes */" + PAYLOAD.length())
                            .build();
                }
                if (slowProbe.get()) {
                    return new MockResponse.Builder()
                            .code(206)
                            .setHeader("Content-Range", "bytes 0-0/" + PAYLOAD.length())
                            .setHeader("ETag", "\"asset-v1\"")
                            .body("A")
                            .bodyDelay(30, TimeUnit.SECONDS)
                            .build();
                }
                if (ignoreRanges.get()) {
                    return new MockResponse.Builder()
                            .code(200)
                            .setHeader("Content-Type", "video/mp4")
                            .setHeader("ETag", "\"asset-v1\"")
                            .body(PAYLOAD)
                            .build();
                }
                String range = request.getHeaders().get("Range");
                if (failBootstrap.get() && "bytes=0-3".equals(range)) {
                    return new MockResponse.Builder().code(500).body("bootstrap failed").build();
                }
                long[] bounds = parseRange(range);
                int start = Math.toIntExact(bounds[0]);
                int end = Math.toIntExact(bounds[1]);
                if ("bytes=0-3".equals(range)) Thread.sleep(100);
                return new MockResponse.Builder()
                        .code(206)
                        .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + PAYLOAD.length())
                        .setHeader("Content-Type", "video/mp4")
                        .setHeader("ETag", "\"asset-v1\"")
                        .body(PAYLOAD.substring(start, end + 1))
                        .build();
            }
        });
        upstream.start();
        capabilityToken = ProxyCapabilityToken.generate();
        ProxyRuntimeConfig config = config();
        OkHttpClient okHttp = new OkHttpClient.Builder()
                .eventListener(new EventListener() {
                    @Override
                    public void callStart(Call call) {
                        activeCalls.incrementAndGet();
                    }

                    @Override
                    public void callEnd(Call call) {
                        activeCalls.decrementAndGet();
                    }

                    @Override
                    public void callFailed(Call call, IOException failure) {
                        activeCalls.decrementAndGet();
                    }
                })
                .build();
        UpstreamRangeClient client = new UpstreamRangeClient(okHttp, config);
        proxy = new MultiThreadProxyServer(config, capabilityToken, 7, client);
        proxy.startServer();
    }

    @After
    public void tearDown() throws Exception {
        if (proxy != null) proxy.close();
        if (upstream != null) upstream.close();
    }

    @Test
    public void proxyRejectsMissingCapabilityBeforeContactingUpstream() throws Exception {
        HttpResult result = request(false, "bytes=0-11");

        assertEquals(401, result.code());
        assertEquals(0, upstreamRequests.get());
    }

    @Test
    public void proxyStreamsParallelShardsAsSingleOrderedPartialResponse() throws Exception {
        HttpResult result = request(true, "bytes=0-11");

        assertEquals(206, result.code());
        assertEquals("bytes 0-11/12", result.contentRange());
        assertEquals("bytes", result.acceptRanges());
        assertEquals("video/mp4", result.contentType());
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        assertEquals(4, upstreamRequests.get());
        assertTrue(proxy.sessions().activeCount() == 0);
    }

    @Test
    public void bootstrapFailureReturnsBadGatewayBeforePartialHeadersArePublished() throws Exception {
        failBootstrap.set(true);

        HttpResult result = request(true, "bytes=0-11");

        assertEquals(502, result.code());
        assertNull(result.contentRange());
    }

    @Test
    public void proxyFallsBackToSingleConnectionWhenUpstreamIgnoresRange() throws Exception {
        ignoreRanges.set(true);

        HttpResult result = request(true, "bytes=4-7");

        assertEquals(200, result.code());
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        assertEquals(2, upstreamRequests.get());
    }

    @Test
    public void proxyWithoutRangeUsesOneDirectUpstreamRequest() throws Exception {
        ignoreRanges.set(true);

        HttpResult result = request(true, null);

        assertEquals(200, result.code());
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
        assertEquals(1, upstreamRequests.get());
    }

    @Test
    public void proxyHeadUsesOneUpstreamHeadWithoutCreatingShards() throws Exception {
        HttpResult result = request(
                true,
                null,
                "HEAD",
                upstream.url("/asset.bin").toString());

        assertEquals(200, result.code());
        assertEquals(0, result.body().length);
        assertEquals(1, upstreamRequests.get());
        assertEquals("HEAD", lastMethod.get());
    }

    @Test
    public void proxyPreservesUnsatisfiedTotalLength() throws Exception {
        unsatisfiable.set(true);

        HttpResult result = request(true, "bytes=99-100");

        assertEquals(416, result.code());
        assertEquals("bytes */12", result.contentRange());
        assertEquals(1, upstreamRequests.get());
    }

    @Test
    public void proxyRejectsNonNetworkUrlBeforeContactingUpstream() throws Exception {
        HttpResult result = request(true, null, "GET", "file:///private/video.mp4");

        assertEquals(400, result.code());
        assertEquals(0, upstreamRequests.get());
    }

    @Test
    public void opaqueStreamRelaysRegisteredTargetWithoutExposingSecretsOrCapabilityHeader() throws Exception {
        String target = upstream.url("/asset.bin").toString();
        String streamUrl;
        try (ProxyStreamRegistration registration = proxy.registerTarget(
                target,
                Map.of("Cookie", "sid=secret", "Host", "evil.test"))) {
            streamUrl = registration.url();
            assertTrue(streamUrl.startsWith("http://127.0.0.1:" + proxy.actualPort() + "/v1/stream/"));
            assertFalse(streamUrl.contains(target));
            assertFalse(streamUrl.contains("sid=secret"));
            assertFalse(streamUrl.contains(capabilityToken.value()));

            HttpResult result = requestUrl(streamUrl, "bytes=0-11", "GET", false);

            assertEquals(206, result.code());
            assertEquals("bytes 0-11/12", result.contentRange());
            assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), result.body());
            assertEquals("sid=secret", lastCookie.get());
        }

        assertEquals(404, requestUrl(streamUrl, "bytes=0-11", "GET", false).code());
    }

    @Test
    public void closingProxyCancelsBlockedUpstreamProbeWithinOneSecond() throws Exception {
        slowProbe.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> pending = executor.submit(() -> {
            try {
                request(true, "bytes=0-11");
            } catch (IOException ignored) {
            }
        });
        try {
            awaitActiveCalls(1, 2_000);
            proxy.close();
            awaitActiveCalls(0, 1_000);
            pending.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitActiveCalls(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (activeCalls.get() != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(expected, activeCalls.get());
    }

    private HttpResult request(boolean authenticated, String range) throws IOException {
        return request(authenticated, range, "GET", upstream.url("/asset.bin").toString());
    }

    private HttpResult request(
            boolean authenticated,
            String range,
            String method,
            String targetUrl) throws IOException {
        String target = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
        return requestUrl(
                "http://127.0.0.1:" + proxy.actualPort() + "/v1/proxy?url=" + target,
                range,
                method,
                authenticated);
    }

    private HttpResult requestUrl(
            String url,
            String range,
            String method,
            boolean authenticated) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(5_000);
        connection.setRequestMethod(method);
        if (authenticated) connection.setRequestProperty(MultiThreadProxyServer.TOKEN_HEADER, capabilityToken.value());
        if (range != null) connection.setRequestProperty("Range", range);
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = stream == null ? new byte[0] : readAll(stream);
            return new HttpResult(
                    code,
                    connection.getHeaderField("Content-Range"),
                    connection.getHeaderField("Accept-Ranges"),
                    connection.getHeaderField("Content-Type"),
                    body);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[128];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static long[] parseRange(String value) {
        String interval = value.substring("bytes=".length());
        int dash = interval.indexOf("-");
        return new long[]{
                Long.parseLong(interval.substring(0, dash)),
                Long.parseLong(interval.substring(dash + 1))};
    }

    private static ProxyRuntimeConfig config() {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(), true, ProxyRuntimeConfig.PortMode.AUTO, ProxyRuntimeConfig.AUTO_PORT,
                defaults.serverWorkers(), defaults.connectionQueueCapacity(), 3, 3,
                ProxyRuntimeConfig.ShardMode.SIZE, ProxyRuntimeConfig.AUTO_SHARD_COUNT, 4, 1, 3, 12, 0,
                defaults.connectTimeoutMillis(), defaults.readTimeoutMillis(), 0);
    }

    private record HttpResult(
            int code,
            String contentRange,
            String acceptRanges,
            String contentType,
            byte[] body) {
    }
}
