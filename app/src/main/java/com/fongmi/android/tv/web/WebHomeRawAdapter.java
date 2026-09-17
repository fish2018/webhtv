package com.fongmi.android.tv.web;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class WebHomeRawAdapter {

    private static final long CACHE_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_TEXT_BYTES = 10L * 1024L * 1024L;
    private static final long RELOAD_WINDOW_MS = TimeUnit.SECONDS.toMillis(15);

    private static volatile OkHttpClient client;

    private final Map<String, String> siteHeaders;
    private final RawUrl home;
    private final boolean cookiesAllowed;
    private volatile long noCacheUntil;

    private WebHomeRawAdapter(RawUrl home, Map<String, String> siteHeaders, boolean cookiesAllowed) {
        this.home = home;
        this.siteHeaders = siteHeaders == null ? new HashMap<>() : new HashMap<>(siteHeaders);
        this.cookiesAllowed = cookiesAllowed;
    }

    public static WebHomeRawAdapter create(String homePage, Map<String, String> siteHeaders) {
        return create(homePage, siteHeaders, true);
    }

    public static WebHomeRawAdapter create(String homePage, Map<String, String> siteHeaders, boolean cookiesAllowed) {
        RawUrl home = RawUrl.parse(homePage);
        if (home == null) return null;
        SpiderDebug.log("webhome-raw", "enabled scope=%s url=%s upstream=%s", home.scope, home.original, home.upstream);
        return new WebHomeRawAdapter(home, siteHeaders, cookiesAllowed);
    }

    public WebResourceResponse intercept(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) return null;
        RawUrl target = RawUrl.parse(request.getUrl().toString());
        if (target == null || !home.sameScope(target)) return null;
        long now = System.currentTimeMillis();
        boolean explicitReload = hasInternalReload(request.getUrl());
        if (request.isForMainFrame()) noCacheUntil = explicitReload ? now + RELOAD_WINDOW_MS : 0;
        boolean noCache = explicitReload || now < noCacheUntil;
        return fetch(request, target, noCache);
    }

    private WebResourceResponse fetch(WebResourceRequest webRequest, RawUrl target, boolean noCache) {
        Response response = null;
        try {
            Request request = buildRequest(webRequest, target, noCache);
            long start = System.currentTimeMillis();
            SpiderDebug.log("webhome-raw", "%s %s upstream=%s noCache=%s", request.method(), webRequest.getUrl(), target.upstream, noCache);
            response = client().newCall(request).execute();
            if (cookiesAllowed) CookieBridge.set(target.upstream, response.headers());
            WebResourceResponse result = toWebResponse(webRequest, target, response);
            SpiderDebug.log("webhome-raw", "%s -> %s in %sms cache=%s", target.upstream, response.code(), System.currentTimeMillis() - start, response.cacheResponse() != null);
            return result;
        } catch (Throwable e) {
            if (response != null) response.close();
            SpiderDebug.log("webhome-raw", e);
            return error("Raw WebHome load failed: " + e.getMessage());
        }
    }

    private Request buildRequest(WebResourceRequest webRequest, RawUrl target, boolean noCache) {
        Map<String, String> headers = buildHeaders(webRequest.getRequestHeaders());
        Request.Builder builder = new Request.Builder().url(target.upstream).headers(HeaderPolicy.of(headers));
        HttpUrl url = HttpUrl.parse(target.upstream);
        if (cookiesAllowed && url != null) CookieBridge.apply(url, builder, true, HeaderPolicy.hasCookie(headers));
        if (noCache) {
            builder.cacheControl(CacheControl.FORCE_NETWORK);
            builder.header(HttpHeaders.CACHE_CONTROL, "no-cache");
            builder.header(HttpHeaders.PRAGMA, "no-cache");
        }
        if ("HEAD".equalsIgnoreCase(webRequest.getMethod())) builder.head();
        else builder.get();
        return builder.build();
    }

    private Map<String, String> buildHeaders(Map<String, String> requestHeaders) {
        Map<String, String> headers = new HashMap<>();
        if (requestHeaders != null) {
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                if (copyRequestHeader(entry.getKey(), entry.getValue())) headers.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : siteHeaders.entrySet()) {
            if (copyRequestHeader(entry.getKey(), entry.getValue())) headers.put(entry.getKey(), entry.getValue());
        }
        return HeaderPolicy.withDefaultUa(headers);
    }

    private boolean copyRequestHeader(String name, String value) {
        if (TextUtils.isEmpty(name) || value == null) return false;
        if (!cookiesAllowed && (HttpHeaders.COOKIE.equalsIgnoreCase(name) || HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name))) return false;
        return !"Host".equalsIgnoreCase(name)
                && !"Connection".equalsIgnoreCase(name)
                && !"Content-Length".equalsIgnoreCase(name)
                && !"Content-Encoding".equalsIgnoreCase(name)
                && !"Accept-Encoding".equalsIgnoreCase(name)
                && !"If-None-Match".equalsIgnoreCase(name)
                && !"If-Modified-Since".equalsIgnoreCase(name);
    }

    private WebResourceResponse toWebResponse(WebResourceRequest request, RawUrl target, Response response) {
        ResponseBody body = response.body();
        ContentType contentType = getContentType(target, body, request.isForMainFrame());
        if (body != null && contentType.text && body.contentLength() > MAX_TEXT_BYTES) {
            closeQuietly(response);
            return error(413, "Payload Too Large", "Raw WebHome text resource is too large");
        }
        int rawCode = response.code();
        int code = statusCode(rawCode);
        String reason = reasonPhrase(code, rawCode == code ? response.message() : "");
        Map<String, String> headers = responseHeaders(response, contentType);
        InputStream stream = body == null ? emptyStream() : body.byteStream();
        return new WebResourceResponse(contentType.mime, contentType.encoding, code, reason, headers, stream);
    }

    private ContentType getContentType(RawUrl target, ResponseBody body, boolean mainFrame) {
        ContentType type = byExtension(target.path, mainFrame);
        if (type != null) return type;
        MediaType mediaType = body == null ? null : body.contentType();
        if (mediaType == null) return mainFrame ? ContentType.html() : ContentType.binary("application/octet-stream");
        String mime = (mediaType.type() + "/" + mediaType.subtype()).toLowerCase(Locale.ROOT);
        String encoding = mediaType.charset() == null ? null : mediaType.charset().name();
        if (mainFrame && ("text/plain".equals(mime) || "application/octet-stream".equals(mime))) return ContentType.html();
        return new ContentType(mime, encoding, isTextMime(mime));
    }

    private ContentType byExtension(String path, boolean mainFrame) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        if (mainFrame || lower.endsWith(".html") || lower.endsWith(".htm")) return ContentType.html();
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return ContentType.text("application/javascript");
        if (lower.endsWith(".css")) return ContentType.text("text/css");
        if (lower.endsWith(".json") || lower.endsWith(".map")) return ContentType.text("application/json");
        if (lower.endsWith(".xml")) return ContentType.text("application/xml");
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return ContentType.text("text/plain");
        if (lower.endsWith(".svg")) return ContentType.text("image/svg+xml");
        if (lower.endsWith(".wasm")) return ContentType.binary("application/wasm");
        if (lower.endsWith(".png")) return ContentType.binary("image/png");
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ContentType.binary("image/jpeg");
        if (lower.endsWith(".gif")) return ContentType.binary("image/gif");
        if (lower.endsWith(".webp")) return ContentType.binary("image/webp");
        if (lower.endsWith(".avif")) return ContentType.binary("image/avif");
        if (lower.endsWith(".ico")) return ContentType.binary("image/x-icon");
        if (lower.endsWith(".woff")) return ContentType.binary("font/woff");
        if (lower.endsWith(".woff2")) return ContentType.binary("font/woff2");
        if (lower.endsWith(".ttf")) return ContentType.binary("font/ttf");
        if (lower.endsWith(".otf")) return ContentType.binary("font/otf");
        if (lower.endsWith(".eot")) return ContentType.binary("application/vnd.ms-fontobject");
        return null;
    }

    private boolean isTextMime(String mime) {
        return mime.startsWith("text/")
                || "application/javascript".equals(mime)
                || "application/json".equals(mime)
                || "application/xml".equals(mime)
                || "image/svg+xml".equals(mime);
    }

    private Map<String, String> responseHeaders(Response response, ContentType contentType) {
        Map<String, String> headers = new HashMap<>();
        for (String name : response.headers().names()) {
            if (!copyResponseHeader(name)) continue;
            List<String> values = response.headers(name);
            if (!values.isEmpty()) headers.put(name, TextUtils.join(", ", values));
        }
        headers.put(HttpHeaders.CONTENT_TYPE, contentType.header());
        return headers;
    }

    private boolean copyResponseHeader(String name) {
        return !"Connection".equalsIgnoreCase(name)
                && !"Transfer-Encoding".equalsIgnoreCase(name)
                && !"Keep-Alive".equalsIgnoreCase(name)
                && !"Content-Length".equalsIgnoreCase(name)
                && !"Content-Encoding".equalsIgnoreCase(name)
                && !"Content-Type".equalsIgnoreCase(name)
                && !"Content-Disposition".equalsIgnoreCase(name)
                && !"Set-Cookie".equalsIgnoreCase(name)
                && !"Set-Cookie2".equalsIgnoreCase(name)
                && !"Content-Security-Policy".equalsIgnoreCase(name)
                && !"Content-Security-Policy-Report-Only".equalsIgnoreCase(name)
                && !"X-Content-Type-Options".equalsIgnoreCase(name)
                && !"X-Frame-Options".equalsIgnoreCase(name)
                && !"Cross-Origin-Embedder-Policy".equalsIgnoreCase(name)
                && !"Cross-Origin-Opener-Policy".equalsIgnoreCase(name)
                && !"Cross-Origin-Resource-Policy".equalsIgnoreCase(name);
    }

    private int statusCode(int code) {
        if (code < 100 || code > 599) return 502;
        return code >= 300 && code < 400 ? 200 : code;
    }

    private String reasonPhrase(int code, String message) {
        if (!TextUtils.isEmpty(message)) return message;
        return switch (code) {
            case 200 -> "OK";
            case 206 -> "Partial Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 413 -> "Payload Too Large";
            case 502 -> "Bad Gateway";
            default -> code >= 500 ? "Server Error" : "HTTP " + code;
        };
    }

    private WebResourceResponse error(String message) {
        return error(502, "Bad Gateway", message);
    }

    private WebResourceResponse error(int code, String reason, String message) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.CACHE_CONTROL, "no-store");
        byte[] bytes = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse("text/plain", "utf-8", code, reason, headers, new ByteArrayInputStream(bytes));
    }

    private static OkHttpClient client() {
        if (client != null) return client;
        synchronized (WebHomeRawAdapter.class) {
            if (client != null) return client;
            OkHttpClient.Builder builder = OkHttp.client().newBuilder();
            try {
                File dir = Path.cache("webhome_raw");
                if (dir.exists() || dir.mkdirs()) builder.cache(new Cache(dir, CACHE_BYTES));
            } catch (Throwable e) {
                SpiderDebug.log("webhome-raw", "cache init failed error=%s", e.getMessage());
            }
            return client = builder.build();
        }
    }

    private static boolean hasInternalReload(Uri uri) {
        try {
            return !TextUtils.isEmpty(uri.getQueryParameter("_fm_reload"));
        } catch (Throwable e) {
            return uri.toString().contains("_fm_reload=");
        }
    }

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static void closeQuietly(Response response) {
        try {
            response.close();
        } catch (Throwable ignored) {
        }
    }

    private static class ContentType {
        final String mime;
        final String encoding;
        final boolean text;

        ContentType(String mime, String encoding, boolean text) {
            this.mime = mime;
            this.encoding = encoding;
            this.text = text;
        }

        static ContentType html() {
            return text("text/html");
        }

        static ContentType text(String mime) {
            return new ContentType(mime, "utf-8", true);
        }

        static ContentType binary(String mime) {
            return new ContentType(mime, null, false);
        }

        String header() {
            return TextUtils.isEmpty(encoding) ? mime : mime + "; charset=" + encoding;
        }
    }

    private static class RawUrl {
        final String original;
        final String upstream;
        final String scope;
        final String path;

        RawUrl(String original, String upstream, String scope, String path) {
            this.original = original;
            this.upstream = upstream;
            this.scope = scope;
            this.path = path;
        }

        static RawUrl parse(String url) {
            GitRawUrlResolver.RawUrl result = GitRawUrlResolver.resolve(url);
            return result == null ? null : new RawUrl(result.original, result.upstream, result.scope, result.path);
        }

        boolean sameScope(RawUrl target) {
            return target != null && scope.equals(target.scope);
        }

        private static RawUrl githubRaw(Uri uri, String host, String path) {
            if (!"raw.githubusercontent.com".equals(host)) return null;
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 4) return null;
            String scope = "github:" + segments.get(0) + "/" + segments.get(1);
            return new RawUrl(uri.toString(), uri.toString(), scope, path);
        }

        private static RawUrl github(Uri uri, String host, String path) {
            if (!"github.com".equals(host)) return null;
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 5) return null;
            String mode = segments.get(2);
            if (!"blob".equals(mode) && !"raw".equals(mode)) return null;
            String repoPath = "/" + segments.get(0) + "/" + segments.get(1) + "/" + join(segments, 3);
            String upstream = uri.buildUpon().scheme("https").authority("raw.githubusercontent.com").encodedPath(repoPath).build().toString();
            String scope = "github:" + segments.get(0) + "/" + segments.get(1);
            return new RawUrl(uri.toString(), upstream, scope, repoPath);
        }

        private static RawUrl gist(Uri uri, String host, String path) {
            if (!"gist.githubusercontent.com".equals(host)) return null;
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 3 || !"raw".equals(segments.get(2))) return null;
            String scope = "gist:" + segments.get(0) + "/" + segments.get(1);
            return new RawUrl(uri.toString(), uri.toString(), scope, path);
        }

        private static RawUrl cnb(Uri uri, String host, String path) {
            RawUrl result = marker(uri, host, path, "/-/git/raw/", "/-/git/raw/", "cnb:");
            if (result != null) return result;
            return marker(uri, host, path, "/-/git/blob/", "/-/git/raw/", "cnb:");
        }

        private static RawUrl dashRaw(Uri uri, String host, String path) {
            String prefix = isKnownGitLab(host) ? "gitlab:" : "git-dash:";
            RawUrl result = marker(uri, host, path, "/-/raw/", "/-/raw/", prefix);
            if (result != null) return result;
            return marker(uri, host, path, "/-/blob/", "/-/raw/", prefix);
        }

        private static RawUrl marker(Uri uri, String host, String path, String marker, String replacement, String prefix) {
            int index = path.indexOf(marker);
            if (index <= 1) return null;
            String project = path.substring(0, index);
            if (segmentCount(project) < 2) return null;
            String upstreamPath = path.substring(0, index) + replacement + path.substring(index + marker.length());
            String upstream = uri.buildUpon().encodedPath(upstreamPath).build().toString();
            return new RawUrl(uri.toString(), upstream, prefix + host + project, upstreamPath);
        }

        private static RawUrl knownSimple(Uri uri, String host, String path) {
            if ("gitee.com".equals(host)) return simple(uri, host, path, "gitee:", "raw", "blob", "raw");
            if ("bitbucket.org".equals(host)) return simple(uri, host, path, "bitbucket:", "raw", "src", "raw");
            if (isKnownGitLab(host)) return variableSimple(uri, host, path, "gitlab:", "raw", "blob", "raw");
            return null;
        }

        private static RawUrl simple(Uri uri, String host, String path, String prefix, String rawMode, String viewMode, String upstreamMode) {
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 5) return null;
            String mode = segments.get(2);
            if (!rawMode.equals(mode) && !viewMode.equals(mode)) return null;
            String upstreamPath = "/" + segments.get(0) + "/" + segments.get(1) + "/" + upstreamMode + "/" + join(segments, 3);
            String upstream = uri.buildUpon().encodedPath(upstreamPath).build().toString();
            String scope = prefix + host + "/" + segments.get(0) + "/" + segments.get(1);
            return new RawUrl(uri.toString(), upstream, scope, upstreamPath);
        }

        private static RawUrl variableSimple(Uri uri, String host, String path, String prefix, String rawMode, String viewMode, String upstreamMode) {
            List<String> segments = uri.getPathSegments();
            for (int i = 2; i < segments.size() - 2; i++) {
                String mode = segments.get(i);
                if (!rawMode.equals(mode) && !viewMode.equals(mode)) continue;
                String project = "/" + join(segments, 0, i);
                String upstreamPath = project + "/" + upstreamMode + "/" + join(segments, i + 1);
                String upstream = uri.buildUpon().encodedPath(upstreamPath).build().toString();
                return new RawUrl(uri.toString(), upstream, prefix + host + project, upstreamPath);
            }
            return null;
        }

        private static RawUrl giteaLike(Uri uri, String host, String path) {
            RawUrl result = giteaMarker(uri, host, path, "/raw/branch/", "/raw/branch/");
            if (result != null) return result;
            result = giteaMarker(uri, host, path, "/raw/tag/", "/raw/tag/");
            if (result != null) return result;
            result = giteaMarker(uri, host, path, "/raw/commit/", "/raw/commit/");
            if (result != null) return result;
            result = giteaMarker(uri, host, path, "/src/branch/", "/raw/branch/");
            if (result != null) return result;
            result = giteaMarker(uri, host, path, "/src/tag/", "/raw/tag/");
            if (result != null) return result;
            return giteaMarker(uri, host, path, "/src/commit/", "/raw/commit/");
        }

        private static RawUrl giteaMarker(Uri uri, String host, String path, String marker, String replacement) {
            int index = path.indexOf(marker);
            if (index <= 1) return null;
            String project = path.substring(0, index);
            if (segmentCount(project) != 2) return null;
            String upstreamPath = path.substring(0, index) + replacement + path.substring(index + marker.length());
            String upstream = uri.buildUpon().encodedPath(upstreamPath).build().toString();
            return new RawUrl(uri.toString(), upstream, "gitea:" + host + project, upstreamPath);
        }

        private static boolean isKnownGitLab(String host) {
            return "gitlab.com".equals(host) || host.startsWith("gitlab.") || host.contains(".gitlab.");
        }

        private static Uri clean(Uri uri) {
            Uri.Builder builder = uri.buildUpon().clearQuery().fragment(null);
            try {
                Set<String> names = uri.getQueryParameterNames();
                for (String name : names) {
                    if ("_fm_reload".equals(name) || "_fm_restore".equals(name)) continue;
                    for (String value : uri.getQueryParameters(name)) builder.appendQueryParameter(name, value);
                }
            } catch (Throwable ignored) {
                builder.clearQuery();
            }
            return builder.build();
        }

        private static String path(Uri uri) {
            String path = uri.getEncodedPath();
            return TextUtils.isEmpty(path) ? "/" : path;
        }

        private static int segmentCount(String path) {
            if (TextUtils.isEmpty(path) || "/".equals(path)) return 0;
            int count = 0;
            for (String part : path.split("/")) if (!TextUtils.isEmpty(part)) count++;
            return count;
        }

        private static String join(List<String> segments, int start) {
            return join(segments, start, segments.size());
        }

        private static String join(List<String> segments, int start, int end) {
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (builder.length() > 0) builder.append('/');
                builder.append(Uri.encode(segments.get(i), null));
            }
            return builder.toString();
        }
    }
}
