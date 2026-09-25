package com.fongmi.android.tv.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class OmdbService {

    private static final String BASE_URL = "https://www.omdbapi.com/";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    private OmdbService() {
    }

    public static JsonObject fetch(String imdbId, String apiKey) throws IOException {
        return fetch(BASE_URL, imdbId, apiKey);
    }

    static JsonObject fetch(String baseUrl, String imdbId, String apiKey) throws IOException {
        if (isBlank(baseUrl) || isBlank(imdbId) || isBlank(apiKey)) return null;
        throwIfInterrupted();
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) throw new IllegalArgumentException("Invalid OMDb base URL");
        HttpUrl url = parsed.newBuilder()
                .addQueryParameter("i", imdbId)
                .addQueryParameter("apikey", apiKey)
                .build();
        Request request = new Request.Builder().url(url).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            throwIfInterrupted();
            if (!response.isSuccessful() || response.body() == null) return null;
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }

    static OkHttpClient clientForTest() {
        return CLIENT;
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("OMDb request cancelled");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
