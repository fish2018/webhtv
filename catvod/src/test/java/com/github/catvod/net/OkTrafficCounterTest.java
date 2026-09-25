package com.github.catvod.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises the counter against a real server, because it rewrites every response body
 * that flows through {@link OkHttp} and a mistake there would corrupt playback and
 * scraping rather than merely misreport a speed.
 */
public class OkTrafficCounterTest {

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .addNetworkInterceptor(OkTrafficCounter.interceptor())
                .build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void bodyContentSurvivesWrapping() throws IOException {
        server.enqueue(new MockResponse().setBody("hello world"));

        try (Response response = get()) {
            assertEquals("hello world", response.body().string());
        }
    }

    @Test
    public void byteStreamConsumerSeesIntactBody() throws IOException {
        // Glide loads every poster through OkHttpUrlLoader, which consumes the body as an
        // InputStream rather than via string()/bytes(); a wrapper that only works for the
        // latter would break image loading across the whole UI.
        byte[] payload = new byte[32 * 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 253);
        server.enqueue(new MockResponse().setBody(new Buffer().write(payload)));
        long before = OkTrafficCounter.totalBytes();

        byte[] received = new byte[payload.length];
        try (Response response = get(); java.io.InputStream stream = response.body().byteStream()) {
            int offset = 0;
            while (offset < received.length) {
                int read = stream.read(received, offset, received.length - offset);
                if (read < 0) break;
                offset += read;
            }
            assertEquals(payload.length, offset);
            assertEquals(-1, stream.read());
        }

        assertArrayEquals(payload, received);
        assertEquals(payload.length, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void countsBytesActuallyRead() throws IOException {
        server.enqueue(new MockResponse().setBody("0123456789"));
        long before = OkTrafficCounter.totalBytes();

        try (Response response = get()) {
            response.body().string();
        }

        assertEquals(10, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void contentLengthAndTypeArePreserved() throws IOException {
        server.enqueue(new MockResponse()
                .setBody("abcd")
                .setHeader("Content-Type", "application/json"));

        try (Response response = get()) {
            ResponseBody body = response.body();
            assertEquals(4, body.contentLength());
            assertEquals("application/json", body.contentType().toString());
            assertEquals("abcd", body.string());
        }
    }

    @Test
    public void binaryBodyIsNotCorrupted() throws IOException {
        byte[] payload = new byte[8192];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        server.enqueue(new MockResponse().setBody(new Buffer().write(payload)));

        try (Response response = get()) {
            assertArrayEquals(payload, response.body().bytes());
        }
    }

    @Test
    public void emptyBodyIsCountedAsZero() throws IOException {
        server.enqueue(new MockResponse().setBody(""));
        long before = OkTrafficCounter.totalBytes();

        try (Response response = get()) {
            assertEquals("", response.body().string());
        }

        assertEquals(0, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void noContentResponseIsSafe() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(204));
        long before = OkTrafficCounter.totalBytes();

        try (Response response = get()) {
            assertEquals(204, response.code());
            assertEquals("", response.body().string());
        }

        assertEquals(0, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void headResponseIsSafe() throws IOException {
        server.enqueue(new MockResponse().setHeader("Content-Length", "1234"));

        Request request = new Request.Builder().url(server.url("/")).head().build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            assertEquals("", response.body().string());
        }
    }

    @Test
    public void partialRangeBodyIsCountedAndIntact() throws IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-3/100")
                .setBody("abcd"));
        long before = OkTrafficCounter.totalBytes();

        Request request = new Request.Builder()
                .url(server.url("/"))
                .header("Range", "bytes=0-3")
                .build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals(206, response.code());
            assertEquals("abcd", response.body().string());
        }

        assertEquals(4, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void gzipCountsCompressedWireBytesAndStillDecodes() throws IOException {
        // A speed readout should reflect what crossed the wire, not the inflated size.
        Buffer gzipped = new Buffer();
        try (okio.BufferedSink sink = okio.Okio.buffer(new okio.GzipSink(gzipped))) {
            sink.writeUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }
        long wireBytes = gzipped.size();
        server.enqueue(new MockResponse()
                .setHeader("Content-Encoding", "gzip")
                .setBody(gzipped));
        long before = OkTrafficCounter.totalBytes();

        try (Response response = get()) {
            // OkHttp inflates transparently above the network interceptor.
            assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", response.body().string());
        }

        long counted = OkTrafficCounter.totalBytes() - before;
        assertEquals(wireBytes, counted);
        assertTrue("compressed count should be below the inflated size", counted < 40);
    }

    @Test
    public void partiallyReadBodyCountsOnlyWhatWasConsumed() throws IOException {
        byte[] payload = new byte[64 * 1024];
        server.enqueue(new MockResponse().setBody(new Buffer().write(payload)));
        long before = OkTrafficCounter.totalBytes();

        try (Response response = get()) {
            // Read a little, then abandon the rest as a cancelled transfer would.
            response.body().source().readByteString(16);
        }

        long counted = OkTrafficCounter.totalBytes() - before;
        assertTrue("should count at least what was read", counted >= 16);
        assertTrue("should not bill the unread remainder", counted < payload.length);
    }

    @Test
    public void concurrentTransfersCountExactlyOnce() throws Exception {
        // Real playback runs many transfers at once (segments, posters, subtitles), so a
        // lost or double-counted update would skew every readout.
        int requests = 24;
        int bodySize = 4096;
        for (int i = 0; i < requests; i++) {
            server.enqueue(new MockResponse().setBody(new Buffer().write(new byte[bodySize])));
        }
        long before = OkTrafficCounter.totalBytes();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < requests; i++) {
            futures.add(pool.submit(() -> {
                try (Response response = get()) {
                    response.body().bytes();
                }
                return null;
            }));
        }
        for (java.util.concurrent.Future<?> future : futures) future.get();
        pool.shutdown();

        assertEquals((long) requests * bodySize, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void stacksWithResponseInterceptorDeflateBranch() throws IOException {
        // Both interceptors rebuild the body. ResponseInterceptor registers first so it
        // sits outside and inflates *through* this counter, which must therefore still
        // see the raw compressed bytes and must not break its read.
        String plain = "deflate payload deflate payload deflate payload";
        Buffer deflated = new Buffer();
        try (okio.BufferedSink sink = okio.Okio.buffer(
                new okio.DeflaterSink(deflated, new java.util.zip.Deflater(-1, true)))) {
            sink.writeUtf8(plain);
        }
        long wireBytes = deflated.size();
        server.enqueue(new MockResponse()
                .setHeader("Content-Encoding", "deflate")
                .setBody(deflated));

        OkHttpClient stacked = new OkHttpClient.Builder()
                .addNetworkInterceptor(new com.github.catvod.net.interceptor.ResponseInterceptor())
                .addNetworkInterceptor(OkTrafficCounter.interceptor())
                .build();
        long before = OkTrafficCounter.totalBytes();

        Request request = new Request.Builder().url(server.url("/")).build();
        try (Response response = stacked.newCall(request).execute()) {
            assertEquals(plain, response.body().string());
        }

        assertEquals(wireBytes, OkTrafficCounter.totalBytes() - before);
    }

    @Test
    public void totalIsMonotonicAcrossRequests() throws IOException {
        server.enqueue(new MockResponse().setBody("abc"));
        server.enqueue(new MockResponse().setBody("defgh"));
        long before = OkTrafficCounter.totalBytes();

        try (Response first = get()) {
            first.body().string();
        }
        long afterFirst = OkTrafficCounter.totalBytes();
        try (Response second = get()) {
            second.body().string();
        }

        assertEquals(3, afterFirst - before);
        assertEquals(8, OkTrafficCounter.totalBytes() - before);
    }

    private Response get() throws IOException {
        return client.newCall(new Request.Builder().url(server.url("/")).build()).execute();
    }
}
