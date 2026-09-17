package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public final class HostExceptionInterceptor implements Interceptor {

    private static final String UNEXPECTED_HOST_PREFIX = "unexpected host:";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        try {
            return chain.proceed(chain.request());
        } catch (IllegalArgumentException error) {
            if (!isUnexpectedHost(error)) throw error;
            throw new IOException("Invalid URL host", error);
        }
    }

    private static boolean isUnexpectedHost(IllegalArgumentException error) {
        return error.getMessage() != null && error.getMessage().startsWith(UNEXPECTED_HOST_PREFIX);
    }
}
