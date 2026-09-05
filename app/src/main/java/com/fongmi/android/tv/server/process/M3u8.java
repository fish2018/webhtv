package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.api.config.HlsRuleConfig;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.fongmi.android.tv.utils.HlsAdblockPipeline;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;

/** Compatibility proxy for Python live sources which emit 127.0.0.1:9978/m3u8 links. */
public class M3u8 implements Process {

    private static final String TAG = "live-m3u8";
    private static final String MIME_M3U8 = "application/vnd.apple.mpegurl; charset=utf-8";
    private static final String DEFAULT_UA = "okhttp/4.9.2";
    private static final String DEFAULT_REFERER = "https://www.4gtv.tv/";
    private static final String DEFAULT_ORIGIN = "https://www.4gtv.tv";
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return "/m3u8".equals(url);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        String target = session.getParms().get("url");
        if (!isHttp(target)) return Nano.error(Response.Status.BAD_REQUEST, "Missing or invalid url");
        okhttp3.Response upstream = null;
        try {
            Request request = request(session, target);
            long start = System.currentTimeMillis();
            upstream = OkHttp.player().newCall(request).execute();
            SpiderDebug.log(TAG, "%s %s -> %s in %sms", request.method(), shortUrl(target), upstream.code(), System.currentTimeMillis() - start);
            ResponseBody body = upstream.body();
            if (body == null) {
                upstream.close();
                return Nano.error(Response.Status.INTERNAL_ERROR, "Empty upstream body");
            }
            if (isPlaylist(upstream.request().url().toString(), body.contentType())) return playlist(upstream, body);
            return stream(upstream, body);
        } catch (Throwable e) {
            if (upstream != null) upstream.close();
            SpiderDebug.log(TAG, e);
            return Nano.error(Response.Status.INTERNAL_ERROR, TextUtils.isEmpty(e.getMessage()) ? e.toString() : e.getMessage());
        }
    }

    private Request request(IHTTPSession session, String target) {
        Request.Builder builder = new Request.Builder()
                .url(target)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", DEFAULT_REFERER)
                .header("Origin", DEFAULT_ORIGIN);
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) builder.header("Range", range);
        if (session.getMethod() == NanoHTTPD.Method.HEAD) builder.head();
        return builder.build();
    }

    private Response playlist(okhttp3.Response upstream, ResponseBody body) throws IOException {
        try (upstream; body) {
            if (!upstream.isSuccessful()) return error(status(upstream.code()), "Playlist HTTP " + upstream.code());
            String text = body.string();
            if (!looksLikePlaylist(text)) return Nano.error(Response.Status.BAD_REQUEST, "Invalid playlist");
            HlsAdblockPipeline.Outcome clean = Setting.isAdblock()
                    ? HlsAdblockPipeline.apply(upstream.request().url().toString(), text, hlsRules(), true)
                    : new HlsAdblockPipeline.Outcome(text, false, false, 0, 0);
            String rewritten = rewrite(upstream.request().url(), clean.manifest());
            byte[] bytes = rewritten.getBytes(StandardCharsets.UTF_8);
            SpiderDebug.log(TAG, "playlist bytes=%s rewritten=%s removed=%s structured=%s legacy=%s url=%s",
                    text.length(), bytes.length, clean.removedSegments(), clean.structured(), clean.legacy(), shortUrl(upstream.request().url().toString()));
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_M3U8, new ByteArrayInputStream(bytes), bytes.length));
        }
    }

    private List<HlsManifestCleaner.Rule> hlsRules() {
        return HlsRuleConfig.getRules();
    }

    private Response stream(okhttp3.Response upstream, ResponseBody body) {
        long length = body.contentLength();
        String mime = body.contentType() == null ? "application/octet-stream" : body.contentType().toString();
        InputStream input = new CloseResponseInputStream(body.byteStream(), upstream);
        Response response = length >= 0
                ? NanoHTTPD.newFixedLengthResponse(status(upstream.code()), mime, input, length)
                : NanoHTTPD.newChunkedResponse(status(upstream.code()), mime, input);
        copyHeader(upstream, response, "Accept-Ranges");
        copyHeader(upstream, response, "Content-Range");
        copyHeader(upstream, response, "Cache-Control");
        return response;
    }

    private String rewrite(HttpUrl base, String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder output = new StringBuilder(text.length() + 512);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                line = proxy(resolve(base, trimmed));
            } else if (trimmed.startsWith("#") && trimmed.contains("URI=\"")) {
                line = rewriteUriAttributes(base, line);
            }
            output.append(line);
            if (i + 1 < lines.length) output.append('\n');
        }
        return output.toString();
    }

    private String rewriteUriAttributes(HttpUrl base, String line) {
        Matcher matcher = URI_ATTR.matcher(line);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = "URI=\"" + proxy(resolve(base, matcher.group(1))) + "\"";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String resolve(HttpUrl base, String value) {
        HttpUrl resolved = base.resolve(value);
        return resolved == null ? value : resolved.toString();
    }

    private String proxy(String target) {
        return "http://127.0.0.1:9978/m3u8?url=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
    }

    private boolean isPlaylist(String url, MediaType type) {
        String mime = type == null ? "" : type.toString().toLowerCase(Locale.US);
        if (mime.contains("mpegurl") || mime.contains("m3u8")) return true;
        String lower = url.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u");
    }

    private boolean looksLikePlaylist(String text) {
        return !TextUtils.isEmpty(text) && text.trim().startsWith("#EXTM3U");
    }

    private boolean isHttp(String url) {
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private Response noCache(Response response) {
        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.addHeader("Pragma", "no-cache");
        return response;
    }

    private Response error(Response.IStatus status, String message) {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, message);
    }

    private void copyHeader(okhttp3.Response source, Response target, String name) {
        String value = source.header(name);
        if (!TextUtils.isEmpty(value)) target.addHeader(name, value);
    }

    private Response.IStatus status(int code) {
        Response.Status status = Response.Status.lookup(code);
        return status == null ? Response.Status.INTERNAL_ERROR : status;
    }

    private String shortUrl(String url) {
        return url.length() <= 180 ? url : url.substring(0, 180) + "...";
    }

    private static class CloseResponseInputStream extends FilterInputStream {

        private final okhttp3.Response response;

        private CloseResponseInputStream(InputStream input, okhttp3.Response response) {
            super(input);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }
}
