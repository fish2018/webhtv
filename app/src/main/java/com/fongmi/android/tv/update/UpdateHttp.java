package com.fongmi.android.tv.update;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class UpdateHttp {

    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build();
    private static final OkHttpClient OCI_CLIENT = CLIENT.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private UpdateHttp() {
    }

    public static OkHttpClient client() {
        return CLIENT;
    }

    public static OkHttpClient ociClient() {
        return OCI_CLIENT;
    }

    public static String string(String url, Map<String, String> headers, long timeoutMs) throws IOException {
        Request.Builder builder = new Request.Builder().url(UpdateUrl.requireHttpsUrl(url)).header("Accept-Encoding", "identity");
        if (headers != null) headers.forEach(builder::header);
        OkHttpClient client = CLIENT.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build();
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response");
            long length = body.contentLength();
            if (length > MAX_TEXT_BYTES) throw new IOException("Response is too large");
            byte[] bytes = body.bytes();
            if (bytes.length > MAX_TEXT_BYTES) throw new IOException("Response is too large");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
