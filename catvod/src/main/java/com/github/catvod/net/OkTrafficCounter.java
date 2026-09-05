package com.github.catvod.net;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Process-wide count of response bytes read through {@link OkHttp}.
 *
 * <p>Exists because per-UID {@link android.net.TrafficStats} needs kernel support that
 * some vendor ROMs never compiled in, leaving speed readouts with no byte source at
 * all.  Counting in the HTTP layer is independent of that kernel support, so it works
 * everywhere the traffic passes through Java — which includes the scraping and config
 * requests that run before a playback kernel exists to report its own throughput.
 *
 * <p>Bytes are counted as they are consumed rather than when a response completes, so a
 * long-lived video stream reports progress continuously instead of in one lump.
 */
public final class OkTrafficCounter {

    private static final AtomicLong TOTAL_BYTES = new AtomicLong();
    private static final int HTTP_SWITCHING_PROTOCOLS = 101;

    private OkTrafficCounter() {
    }

    /** Monotonic count of response body bytes read since process start. */
    public static long totalBytes() {
        return TOTAL_BYTES.get();
    }

    static Interceptor interceptor() {
        return chain -> count(chain.proceed(chain.request()));
    }

    private static Response count(Response response) {
        // A 101 hands the socket to the WebSocket reader; rebuilding its body would
        // break the upgrade, and its frames are not what a speed readout measures.
        if (response.code() == HTTP_SWITCHING_PROTOCOLS) return response;
        ResponseBody body = response.body();
        if (body == null) return response;
        BufferedSource counted = Okio.buffer(new CountingSource(body.source()));
        return response.newBuilder()
                .body(ResponseBody.create(counted, body.contentType(), body.contentLength()))
                .build();
    }

    private static final class CountingSource extends ForwardingSource {

        private CountingSource(Source delegate) {
            super(delegate);
        }

        @Override
        public long read(@NonNull Buffer sink, long byteCount) throws IOException {
            long read = super.read(sink, byteCount);
            if (read > 0) TOTAL_BYTES.addAndGet(read);
            return read;
        }
    }
}
