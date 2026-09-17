package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Tests unsaved TMDB API and image-host settings without changing persisted configuration. */
public final class TmdbConfigTestService {

    private static final OkHttpClient CLIENT = com.github.catvod.net.OkHttp.client().newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private TmdbConfigTestService() {
    }

    public static Result test(String credential, String apiHost, String imageHost, String omdbApiKey) {
        return test(CLIENT, credential, apiHost, imageHost, omdbApiKey, "https://www.omdbapi.com/");
    }

    static Result test(OkHttpClient client, String credential, String apiHost, String imageHost, String omdbApiKey, String omdbBaseUrl) {
        return new Result(
                timed(() -> testApi(client, credential, apiHost)),
                timed(() -> testImage(client, imageHost)),
                timed(() -> testOmdb(client, omdbApiKey, omdbBaseUrl)));
    }

    static Check testApi(OkHttpClient client, String credential, String apiHost) {
        if (credential == null || credential.trim().isEmpty()) return Check.failed("API Key / Access Token is empty");
        TmdbConfig config = config(credential, apiHost, null, null);
        try {
            HttpUrl base = HttpUrl.parse(config.getApiBase() + "/configuration");
            if (base == null) return Check.failed("invalid URL");
            HttpUrl.Builder url = base.newBuilder();
            Request.Builder request = new Request.Builder().get();
            if (config.getAccessToken().isEmpty()) {
                request.url(url.addQueryParameter("api_key", config.getApiKey()).build());
            } else {
                request.url(url.build()).header("Authorization", "Bearer " + config.getAccessToken());
            }
            try (Response response = client.newCall(request.build()).execute()) {
                if (!response.isSuccessful()) return Check.failed("HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) return Check.failed("empty response");
                JsonObject json = JsonParser.parseString(body.string()).getAsJsonObject();
                if (!json.has("images") || !json.get("images").isJsonObject()) {
                    return Check.failed("response is not TMDB configuration data");
                }
                return Check.success();
            }
        } catch (Exception e) {
            return Check.failed(message(e));
        }
    }

    static Check testImage(OkHttpClient client, String imageHost) {
        TmdbConfig config = config(null, null, imageHost, null);
        try {
            HttpUrl url = HttpUrl.parse(config.getImageBase() + "/wwemzKWzjKYJFfCeiB57q3r4Bcm.png");
            if (url == null) return Check.failed("invalid URL");
            try (Response response = client.newCall(new Request.Builder().url(url).get().build()).execute()) {
                if (!response.isSuccessful()) return Check.failed("HTTP " + response.code());
                ResponseBody body = response.body();
                String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
                if (!type.startsWith("image/")) return Check.failed("response is not an image");
                if (body == null || body.contentLength() == 0) return Check.failed("empty image");
                byte[] prefix = body.source().peek().readByteArray(16);
                return hasImageSignature(prefix) ? Check.success() : Check.failed("invalid image data");
            }
        } catch (Exception e) {
            return Check.failed(message(e));
        }
    }

    static Check testOmdb(OkHttpClient client, String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.trim().isEmpty()) return Check.failed("OMDb API Key is empty");
        try {
            HttpUrl base = parseHost(baseUrl);
            if (base == null) return Check.failed("invalid OMDb URL");
            HttpUrl url = base.newBuilder()
                    .addQueryParameter("i", "tt0111161")
                    .addQueryParameter("apikey", apiKey.trim())
                    .build();
            try (Response response = client.newCall(new Request.Builder().url(url).get().build()).execute()) {
                if (!response.isSuccessful()) return Check.failed("HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) return Check.failed("empty response");
                JsonObject json = JsonParser.parseString(body.string()).getAsJsonObject();
                if (json.has("Response") && "False".equalsIgnoreCase(json.get("Response").getAsString())) {
                    return Check.failed(json.has("Error") ? json.get("Error").getAsString() : "OMDb rejected the request");
                }
                if (!json.has("imdbID") || !json.get("imdbID").getAsString().startsWith("tt")) {
                    return Check.failed("response is not valid OMDb data");
                }
                return Check.success();
            }
        } catch (Exception e) {
            return Check.failed(message(e));
        }
    }

    private static TmdbConfig config(String credential, String apiHost, String imageHost, String omdbApiKey) {
        JsonObject json = new JsonObject();
        String value = trim(credential);
        if (value.split("\\.").length >= 3) json.addProperty("accessToken", value);
        else json.addProperty("apiKey", value);
        if (apiHost != null) json.addProperty("apiBase", trim(apiHost));
        if (imageHost != null) json.addProperty("imageBase", trim(imageHost));
        if (omdbApiKey != null) json.addProperty("omdbApiKey", trim(omdbApiKey));
        return TmdbConfig.objectFrom(json.toString());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static Check timed(CheckSupplier supplier) {
        long started = System.nanoTime();
        Check result;
        try {
            result = supplier.get();
        } catch (RuntimeException e) {
            result = Check.failed(message(e));
        }
        return result.withLatency(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private interface CheckSupplier {
        Check get();
    }

    private static HttpUrl parseHost(String host) {
        if (host == null || host.trim().isEmpty()) return null;
        String value = host.trim();
        if (!value.endsWith("/")) value += "/";
        try {
            HttpUrl url = HttpUrl.get(value);
            return "http".equals(url.scheme()) || "https".equals(url.scheme()) ? url : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean hasImageSignature(byte[] value) {
        if (value.length < 3) return false;
        boolean jpeg = (value[0] & 0xff) == 0xff && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
        boolean png = value.length >= 8 && (value[0] & 0xff) == 0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47;
        boolean gif = value.length >= 6 && value[0] == 'G' && value[1] == 'I' && value[2] == 'F';
        boolean webp = value.length >= 12 && value[0] == 'R' && value[1] == 'I' && value[2] == 'F' && value[3] == 'F'
                && value[8] == 'W' && value[9] == 'E' && value[10] == 'B' && value[11] == 'P';
        return jpeg || png || gif || webp;
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }

    public static final class Result {
        public final Check api;
        public final Check image;
        public final Check omdb;

        Result(Check api, Check image, Check omdb) {
            this.api = api;
            this.image = image;
            this.omdb = omdb;
        }
    }

    public static final class Check {
        public final boolean success;
        public final String message;
        public final long latencyMillis;

        private Check(boolean success, String message, long latencyMillis) {
            this.success = success;
            this.message = message;
            this.latencyMillis = latencyMillis;
        }

        static Check success() {
            return new Check(true, "", 0);
        }

        static Check failed(String message) {
            return new Check(false, message, 0);
        }

        Check withLatency(long latencyMillis) {
            return new Check(success, message, Math.max(0, latencyMillis));
        }
    }
}
