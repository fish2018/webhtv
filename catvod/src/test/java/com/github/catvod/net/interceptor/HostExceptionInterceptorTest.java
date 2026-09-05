package com.github.catvod.net.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HostExceptionInterceptorTest {

    @Test
    public void unexpectedHostFromDownstreamBecomesIOException() {
        IllegalArgumentException failure = new IllegalArgumentException("unexpected host: xn--invalid");
        OkHttpClient client = clientThatFailsWith(failure);

        try {
            client.newCall(request()).execute();
            fail("Expected an IOException");
        } catch (IOException error) {
            assertEquals("Invalid URL host", error.getMessage());
            assertSame(failure, error.getCause());
        }
    }

    @Test
    public void unrelatedIllegalArgumentExceptionIsNotMasked() throws IOException {
        IllegalArgumentException failure = new IllegalArgumentException("programming error");
        OkHttpClient client = clientThatFailsWith(failure);

        try {
            client.newCall(request()).execute();
            fail("Expected the original IllegalArgumentException");
        } catch (IllegalArgumentException error) {
            assertSame(failure, error);
        }
    }

    private static OkHttpClient clientThatFailsWith(IllegalArgumentException failure) {
        return new OkHttpClient.Builder()
                .addInterceptor(new HostExceptionInterceptor())
                .addInterceptor(chain -> {
                    throw failure;
                })
                .build();
    }

    private static Request request() {
        return new Request.Builder().url("https://example.com/video").build();
    }
}
