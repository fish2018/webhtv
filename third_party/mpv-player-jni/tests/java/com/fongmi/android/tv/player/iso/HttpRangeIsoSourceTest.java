package com.fongmi.android.tv.player.iso;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

import static org.junit.Assert.*;

public class HttpRangeIsoSourceTest {
    private final MockWebServer server = new MockWebServer();
    private final ExecutorService readers = Executors.newFixedThreadPool(2);
    private HttpRangeIsoSource source;

    @After
    public void tearDown() {
        if (source != null) source.close();
        readers.shutdownNow();
        server.close();
    }

    @Test
    public void closeCancelsTwoConcurrentResponseBodiesAfterHeaders() throws Exception {
        CountDownLatch headers = new CountDownLatch(2);
        OkHttpClient client = new OkHttpClient.Builder().eventListener(new EventListener() {
            @Override public void responseHeadersEnd(Call call, Response response) {
                if (!call.request().header("Range").equals("bytes=0-0")) headers.countDown();
            }
        }).build();
        server.enqueue(range("bytes 0-0/16", "a", "one").build());
        // Identical ranges deliberately exercise two active Calls, not the page cache's dedup.
        server.enqueue(range("bytes 1-1/16", "b", "one").bodyDelay(30, TimeUnit.SECONDS).build());
        server.enqueue(range("bytes 1-1/16", "b", "one").bodyDelay(30, TimeUnit.SECONDS).build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(), Map.of(), client);
        assertEquals(16, source.length());
        Future<?> first = readers.submit(() -> assertThrows(IOException.class, () -> source.readAt(1, new byte[1], 0, 1)));
        Future<?> second = readers.submit(() -> assertThrows(IOException.class, () -> source.readAt(1, new byte[1], 0, 1)));
        assertTrue(headers.await(3, TimeUnit.SECONDS));
        source.close();
        first.get(3, TimeUnit.SECONDS);
        second.get(3, TimeUnit.SECONDS);
    }

    @Test
    public void validatorChangeIsRejectedAndCallerRangeHeadersAreOverridden() throws Exception {
        server.enqueue(range("bytes 0-0/16", "a", "one").build());
        server.enqueue(range("bytes 2-2/16", "c", "two").build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(),
                Map.of("Range", "bytes=500-600", "Accept-Encoding", "gzip"), new OkHttpClient());
        assertEquals(16, source.length());
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED,
                assertThrows(IsoSourceException.class, () -> source.readAt(2, new byte[1], 0, 1)).reason());
        assertEquals("bytes=0-0", server.takeRequest(3, TimeUnit.SECONDS).getHeaders().get("Range"));
        assertEquals("identity", server.takeRequest(3, TimeUnit.SECONDS).getHeaders().get("Accept-Encoding"));
    }

    @Test
    public void ignoredRangeStillFailsWithoutReadingWholeIso() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("not a range").build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(), Map.of(), new OkHttpClient());
        assertEquals(IsoSourceException.Reason.RANGE_UNSUPPORTED,
                assertThrows(IsoSourceException.class, () -> source.length()).reason());
    }

    @Test
    public void progressiveBodyPublishesBeforeCompletionAndCancelsOnlyItsOwnCall() throws Exception {
        server.enqueue(range("bytes 0-0/32768", "a", "one").build());
        server.enqueue(range("bytes 0-32767/32768", "x".repeat(32768), "one")
                .throttleBody(8192, 1, TimeUnit.SECONDS).build());
        server.enqueue(range("bytes 8-8/32768", "y", "one").build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(), Map.of(), new OkHttpClient());
        assertEquals(32768, source.length());
        CountDownLatch prefix = new CountDownLatch(1);
        byte[] data = new byte[32768];
        RemoteIsoSource.ReadRequest request = new RemoteIsoSource.ReadRequest(count -> {
            if (count >= 8192) prefix.countDown();
        });
        Future<?> result = readers.submit(() -> assertThrows(IOException.class,
                () -> source.readAt(0, data, 0, data.length, request)));
        assertTrue(prefix.await(2, TimeUnit.SECONDS));
        assertFalse(result.isDone());
        assertEquals('x', data[0]);
        request.cancel();
        result.get(2, TimeUnit.SECONDS);
        assertEquals(1, source.readAt(8, new byte[1], 0, 1));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void progressiveRetryResumesAfterPublishedPrefixWithoutOverwritingIt() throws Exception {
        List<String> ranges = new ArrayList<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        byte[] data = new byte[12];
        Arrays.fill(data, (byte) 77);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            String range = chain.request().header("Range");
            ranges.add(range);
            String contentRange;
            ResponseBody body;
            if (range.equals("bytes=0-0")) {
                contentRange = "bytes 0-0/16";
                body = ResponseBody.create("a", (MediaType) null);
            } else if (!interrupted.getAndSet(true)) {
                contentRange = "bytes 0-7/16";
                BufferedSource stream = Okio.buffer(new Source() {
                    boolean sent;
                    public long read(Buffer sink, long count) throws IOException {
                        if (sent) throw new IOException("connection reset after prefix");
                        sent = true;
                        sink.writeUtf8("abcd");
                        return 4;
                    }
                    public Timeout timeout() { return Timeout.NONE; }
                    public void close() {}
                });
                body = new ResponseBody() {
                    public MediaType contentType() { return null; }
                    public long contentLength() { return 8; }
                    public BufferedSource source() { return stream; }
                };
            } else {
                assertEquals("bytes=4-7", range);
                assertArrayEquals("abcd".getBytes(), Arrays.copyOfRange(data, 2, 6));
                contentRange = "bytes 4-7/16";
                body = ResponseBody.create("efgh", (MediaType) null);
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(206).message("Partial Content").header("Content-Range", contentRange)
                    .header("ETag", "one").body(body).build();
        }).build();
        source = new HttpRangeIsoSource("http://test.invalid/disc.iso", Map.of(), client);
        List<Integer> progress = new ArrayList<>();
        assertEquals(8, source.readAt(0, data, 2, 8, new RemoteIsoSource.ReadRequest(progress::add)));
        assertEquals(List.of("bytes=0-0", "bytes=0-7", "bytes=4-7"), ranges);
        assertEquals(List.of(4, 8), progress);
        assertArrayEquals("abcdefgh".getBytes(), Arrays.copyOfRange(data, 2, 10));
        assertEquals(77, data[1]);
        assertEquals(77, data[10]);
    }

    @Test
    public void progressiveValidatorChangePublishesNoUnvalidatedBytes() throws Exception {
        server.enqueue(range("bytes 0-0/16", "a", "one").build());
        server.enqueue(range("bytes 2-2/16", "c", "two").build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(), Map.of(), new OkHttpClient());
        byte[] data = new byte[]{77};
        AtomicInteger progress = new AtomicInteger();
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED, assertThrows(IsoSourceException.class,
                () -> source.readAt(2, data, 0, 1, new RemoteIsoSource.ReadRequest(progress::set))).reason());
        assertEquals(0, progress.get());
        assertEquals(77, data[0]);
    }

    @Test
    public void cancellationBeforeCallRegistrationStillCancelsNewlyAttachedCall() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        RemoteIsoSource.ReadRequest request = new RemoteIsoSource.ReadRequest(count -> fail("cancelled read published"));
        request.cancel();
        request.attach(cancellations::incrementAndGet);
        assertEquals(1, cancellations.get());
        assertThrows(IOException.class, () -> request.publish(1));
    }

    @Test
    public void contentRangeBeyondSourceLengthIsRejectedBeforePublishing() throws Exception {
        server.enqueue(range("bytes 0-0/16", "a", "one").build());
        server.enqueue(range("bytes 15-16/16", "xx", "one").build());
        server.start();
        source = new HttpRangeIsoSource(server.url("/disc.iso").toString(), Map.of(), new OkHttpClient());
        assertEquals(IsoSourceException.Reason.RANGE_INVALID, assertThrows(IsoSourceException.class,
                () -> source.readAt(15, new byte[2], 0, 2, new RemoteIsoSource.ReadRequest(count -> fail("invalid range")))).reason());
    }

    private static MockResponse.Builder range(String range, String body, String validator) {
        return new MockResponse.Builder().code(206).addHeader("Content-Range", range)
                .addHeader("ETag", validator).body(body);
    }
}
