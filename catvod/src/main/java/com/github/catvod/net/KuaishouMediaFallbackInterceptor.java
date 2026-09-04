package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.crawler.SpiderDebug;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Retries Kuaishou PNG-wrapped HLS segments when the manifest's CDN hostname
 * returns a false 404.  The same object is available from the other Kuaishou
 * CDN aliases, while retrying arbitrary media URLs would be unsafe.
 */
final class KuaishouMediaFallbackInterceptor implements Interceptor {

    private static final List<String> FALLBACK_HOSTS = List.of(
            "p1.a.kwimgs.com",
            "p2.a.kwimgs.com",
            "bd.a.yximgs.com",
            "ali2.a.kwimgs.com");
    private static final Set<String> KUAISHOU_CDN_HOSTS = Set.of(
            "static.yximgs.com",
            "p1.a.kwimgs.com",
            "p2.a.kwimgs.com",
            "bd.a.yximgs.com",
            "hw.a.yximgs.com",
            "ali2.a.kwimgs.com",
            "js2.a.yximgs.com");
    private static final Pattern SEGMENT_PATH = Pattern.compile(
            "(?i)^/udata/pkg/[0-9a-f]{32}\\.png$");

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Response original = chain.proceed(request);
        if (original.code() != 404 || !isEligible(request)) return original;

        String originalHost = request.url().host();
        Response fallback = copyResponseForFallback(original);
        original.close();

        for (String host : FALLBACK_HOSTS) {
            if (host.equalsIgnoreCase(originalHost)) continue;
            Request retry = request.newBuilder()
                    .url(request.url().newBuilder().host(host).build())
                    .removeHeader("Authorization")
                    .removeHeader("Proxy-Authorization")
                    .removeHeader("Cookie")
                    .build();
            try {
                Response candidate = chain.proceed(retry);
                if (candidate.isSuccessful()) {
                    debug(
                            "media-404-fallback from=%s to=%s path=%s result=%d",
                            originalHost, host, request.url().encodedPath(), candidate.code());
                    return candidate;
                }
                candidate.close();
            } catch (IOException error) {
                debug(
                        "media-404-fallback failed host=%s path=%s error=%s",
                        host, request.url().encodedPath(), error.getClass().getSimpleName());
            }
        }

        debug(
                "media-404-fallback exhausted host=%s path=%s", originalHost,
                request.url().encodedPath());
        return fallback;
    }

    private static void debug(String message, Object... args) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("okhttp-player", message, args);
    }

    /**
     * OkHttp requires a response to be closed before an interceptor calls
     * {@code chain.proceed()} again. Keep a small, reusable 404 response for
     * the exhausted-fallback case after closing the network response.
     */
    private static Response copyResponseForFallback(Response response) {
        return response.newBuilder()
                .removeHeader("Content-Length")
                .body(ResponseBody.EMPTY)
                .build();
    }

    static boolean isEligible(Request request) {
        if (request == null || !(request.method().equals("GET") || request.method().equals("HEAD"))) {
            return false;
        }
        HttpUrl url = request.url();
        return KUAISHOU_CDN_HOSTS.contains(url.host().toLowerCase())
                && SEGMENT_PATH.matcher(url.encodedPath()).matches();
    }
}
