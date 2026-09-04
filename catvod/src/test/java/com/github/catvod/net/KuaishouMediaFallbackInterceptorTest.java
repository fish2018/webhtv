package com.github.catvod.net;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import okio.Buffer;
import okio.BufferedSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KuaishouMediaFallbackInterceptorTest {

    @Test
    public void onlyKuaishouPngSegmentsAreEligible() {
        assertTrue(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://static.yximgs.com/udata/pkg/66f23107afd545efbfe0112c86d8c72e.png")));
        assertTrue(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://p1.a.kwimgs.com/udata/pkg/66f23107afd545efbfe0112c86d8c72e.png")));
        assertFalse(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://static.yximgs.com/udata/pkg/segment.png")));
        assertFalse(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://static.yximgs.com/udata/pkg/66f23107afd545efbfe0112c86d8c72e.ts")));
        assertFalse(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://static.yximgs.com/video/66f23107afd545efbfe0112c86d8c72e.png")));
        assertFalse(KuaishouMediaFallbackInterceptor.isEligible(get(
                "https://example.com/udata/pkg/66f23107afd545efbfe0112c86d8c72e.png")));
        assertFalse(KuaishouMediaFallbackInterceptor.isEligible(new Request.Builder()
                .url(HttpUrl.get("https://static.yximgs.com/udata/pkg/66f23107afd545efbfe0112c86d8c72e.png"))
                .post(RequestBody.create(new byte[0]))
                .build()));
    }

    @Test
    public void closesOriginal404BeforeRetryingFallbackHost() throws Exception {
        Request request = request("static.yximgs.com")
                .header("Authorization", "secret")
                .header("Cookie", "session=secret")
                .build();
        RecordingChain recording = new RecordingChain(request, true);

        try (Response response = new KuaishouMediaFallbackInterceptor()
                .intercept(recording.chain())) {
            assertEquals(200, response.code());
            assertEquals("segment", response.body().string());
        }

        assertEquals(2, recording.requests.size());
        assertEquals("static.yximgs.com", recording.requests.get(0).url().host());
        assertEquals("p1.a.kwimgs.com", recording.requests.get(1).url().host());
        assertNull(recording.requests.get(1).header("Authorization"));
        assertNull(recording.requests.get(1).header("Cookie"));
    }

    @Test
    public void returnsOriginal404WhenAllFallbackHostsFail() throws Exception {
        Request request = request("static.yximgs.com").build();
        RecordingChain recording = new RecordingChain(request, false);

        try (Response response = new KuaishouMediaFallbackInterceptor()
                .intercept(recording.chain())) {
            assertEquals(404, response.code());
            assertEquals("", response.body().string());
        }

        assertEquals(5, recording.requests.size());
    }

    private Request.Builder request(String host) {
        return new Request.Builder().url("http://" + host
                + "/udata/pkg/66f23107afd545efbfe0112c86d8c72e.png");
    }

    private static final class RecordingChain implements InvocationHandler {

        private final Request initial;
        private final boolean fallbackSucceeds;
        private final List<Request> requests = new ArrayList<>();
        private final List<TrackingResponseBody> bodies = new ArrayList<>();

        private RecordingChain(Request initial, boolean fallbackSucceeds) {
            this.initial = initial;
            this.fallbackSucceeds = fallbackSucceeds;
        }

        private okhttp3.Interceptor.Chain chain() {
            return (okhttp3.Interceptor.Chain) Proxy.newProxyInstance(
                    okhttp3.Interceptor.Chain.class.getClassLoader(),
                    new Class<?>[]{okhttp3.Interceptor.Chain.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("request")) return initial;
            if (!method.getName().equals("proceed")) {
                throw new UnsupportedOperationException(method.getName());
            }
            if (!bodies.isEmpty() && !bodies.get(bodies.size() - 1).closed) {
                throw new IllegalStateException("previous response is still open");
            }
            Request request = (Request) args[0];
            requests.add(request);
            boolean success = fallbackSucceeds && requests.size() > 1;
            TrackingResponseBody body = new TrackingResponseBody(
                    success ? "segment" : "missing");
            bodies.add(body);
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(success ? 200 : 404)
                    .message(success ? "OK" : "Not Found")
                    .body(body)
                    .build();
        }
    }

    private static final class TrackingResponseBody extends ResponseBody {

        private final Buffer source;
        private boolean closed;

        private TrackingResponseBody(String value) {
            source = new Buffer().writeUtf8(value);
        }

        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public long contentLength() {
            return source.size();
        }

        @Override
        public BufferedSource source() {
            return source;
        }

        @Override
        public void close() {
            closed = true;
            source.close();
        }
    }

    private static Request get(String url) {
        return new Request.Builder().url(HttpUrl.get(url)).build();
    }
}
