package com.fongmi.android.tv.web;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebCall {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).dns(OkHttp.dns()).proxySelector(OkHttp.selector()).proxyAuthenticator(OkHttp.authenticator()).build();

    public static String request(JsonObject payload) {
        Response response = null;
        try {
            String url = Json.safeString(payload, "url");
            String method = Json.safeString(payload, "method");
            String body = Json.safeString(payload, "body");
            String responseType = Json.safeString(payload, "responseType");
            boolean include = "include".equals(Json.safeString(payload, "credentials"));
            int timeout = getTimeout(payload);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(payload.get("headers")));
            setDefaultEncoding(headers);
            Request.Builder builder = new Request.Builder().url(url).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, include, HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(method), getBody(getMethod(method), body, builder.build().headers()));
            Request request = builder.build();
            long start = System.currentTimeMillis();
            SpiderDebug.log("webhome-net", "%s %s include=%s timeout=%ss headers=%s", request.method(), request.url(), include, timeout, request.headers().names());
            response = client(timeout).newCall(request).execute();
            SpiderDebug.log("webhome-net", "%s %s -> %s in %sms", request.method(), request.url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(url, response.headers());
            return toJson(response, responseType);
        } catch (Throwable e) {
            SpiderDebug.log("webhome-net", e);
            return error(e);
        } finally {
            if (response != null) response.close();
        }
    }

    private static int getTimeout(JsonObject payload) {
        try {
            return Math.max(payload.get("timeout").getAsInt(), 1);
        } catch (Exception e) {
            return 30;
        }
    }

    private static OkHttpClient client(int timeout) {
        long millis = TimeUnit.SECONDS.toMillis(timeout);
        return CLIENT.newBuilder().connectTimeout(millis, TimeUnit.MILLISECONDS).readTimeout(millis, TimeUnit.MILLISECONDS).writeTimeout(millis, TimeUnit.MILLISECONDS).build();
    }

    private static String getMethod(String method) {
        return TextUtils.isEmpty(method) ? "GET" : method.toUpperCase();
    }

    private static void setDefaultEncoding(Map<String, String> headers) {
        boolean hasEncoding = headers.keySet().stream().anyMatch("Accept-Encoding"::equalsIgnoreCase);
        if (!hasEncoding) headers.put("Accept-Encoding", "gzip");
    }

    private static RequestBody getBody(String method, String body, Headers headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String type = headers.get("Content-Type");
        MediaType mediaType = MediaType.parse(TextUtils.isEmpty(type) ? "text/plain; charset=utf-8" : type);
        return RequestBody.create(body == null ? "" : body, mediaType);
    }

    private static String toJson(Response response, String responseType) throws Exception {
        byte[] raw = response.body() == null ? new byte[0] : readAll(response.body().byteStream());
        byte[] bytes = decode(response.header("Content-Encoding"), raw);
        JsonObject object = new JsonObject();
        object.addProperty("ok", response.isSuccessful());
        object.addProperty("status", response.code());
        object.addProperty("url", response.request().url().toString());
        object.add("headers", headers(response.headers()));
        object.add("cookies", App.gson().toJsonTree(response.headers().values("Set-Cookie")));
        String body = new String(bytes, StandardCharsets.UTF_8);
        if ("base64".equals(responseType)) object.addProperty("body", Util.base64(bytes));
        else if ("json".equals(responseType)) object.add("body", Json.parse(body));
        else object.addProperty("body", body);
        return object.toString();
    }

    private static byte[] decode(String encoding, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return bytes;
        if (hasEncoding(encoding, "br")) throw new IOException("Unsupported Content-Encoding: br");
        if (hasEncoding(encoding, "gzip") || isGzip(bytes)) return gunzip(bytes);
        if (hasEncoding(encoding, "deflate")) return inflate(bytes);
        return bytes;
    }

    private static boolean hasEncoding(String encoding, String value) {
        if (TextUtils.isEmpty(encoding)) return false;
        for (String item : encoding.split(",")) if (value.equalsIgnoreCase(item.trim())) return true;
        return false;
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private static byte[] gunzip(byte[] bytes) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return readAll(gzip);
        }
    }

    private static byte[] inflate(byte[] bytes) throws Exception {
        try {
            return inflate(bytes, true);
        } catch (Throwable e) {
            return inflate(bytes, false);
        }
    }

    private static byte[] inflate(byte[] bytes, boolean nowrap) throws Exception {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(nowrap))) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream stream = input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static JsonObject headers(Headers headers) {
        JsonObject object = new JsonObject();
        for (String name : headers.names()) {
            if (headers.values(name).size() == 1) object.addProperty(name, headers.get(name));
            else object.add(name, App.gson().toJsonTree(headers.values(name)));
        }
        return object;
    }

    private static String error(Throwable e) {
        JsonObject object = new JsonObject();
        object.addProperty("ok", false);
        object.addProperty("status", 500);
        object.addProperty("body", "");
        object.addProperty("error", e.getMessage());
        object.add("headers", new JsonObject());
        return object.toString();
    }

    public static JsonObject object(String json) {
        JsonElement element = Json.parse(json);
        return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
