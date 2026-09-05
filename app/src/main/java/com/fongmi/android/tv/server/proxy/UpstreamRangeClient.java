package com.fongmi.android.tv.server.proxy;

import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class UpstreamRangeClient implements AutoCloseable {

    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    private static final String HEADER_CONTENT_RANGE = "Content-Range";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ETAG = "ETag";
    private static final String HEADER_IF_RANGE = "If-Range";
    private static final String HEADER_LAST_MODIFIED = "Last-Modified";
    private static final String HEADER_RANGE = "Range";
    private static final int PROBE_BODY_LIMIT = 2;
    private static final int MAX_REDIRECTS = 10;

    private final OkHttpClient client;
    private final ProxyRuntimeConfig config;
    private final Object lifecycleLock = new Object();
    private final Set<Call> activeCalls = new HashSet<>();
    private boolean closed;

    public UpstreamRangeClient(ProxyRuntimeConfig config) {
        this(OkHttp.player(), config);
    }

    UpstreamRangeClient(OkHttpClient baseClient, ProxyRuntimeConfig config) {
        Objects.requireNonNull(baseClient, "baseClient");
        ProxyRuntimeConfigValidator.requireValid(config);
        this.config = config;
        this.client = baseClient.newBuilder()
                .connectTimeout(config.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMillis(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public ProbeResult probe(String url, Map<String, String> requestHeaders) throws IOException {
        Request request = buildProbeRequest(url, requestHeaders);
        try (RedirectResponse redirect = executeRedirecting(request, requestHeaders, null)) {
            Response response = redirect.response();
            String finalUrl = response.request().url().toString();
            UpstreamResourceValidator validator = UpstreamResourceValidator.select(
                    response.header(HEADER_ETAG),
                    response.header(HEADER_LAST_MODIFIED));
            int statusCode = response.code();
            if (statusCode == 200) {
                long bodyLength = response.body() == null ? -1 : response.body().contentLength();
                return new ProbeResult(
                        Decision.FALLBACK_SINGLE_CONNECTION,
                        bodyLength,
                        validator,
                        finalUrl,
                        response.header(HEADER_CONTENT_TYPE),
                        RangeResponseValidator.Status.FALLBACK_SINGLE_CONNECTION,
                        redirect.forwardHeaders());
            }
            if (statusCode == 416) {
                HttpContentRange.ParseResult parsed = HttpContentRange.parse(response.header(HEADER_CONTENT_RANGE));
                RangeResponseValidator.Status status = parsed.status() == HttpContentRange.ParseStatus.UNSATISFIED
                        ? RangeResponseValidator.Status.UNSATISFIABLE
                        : RangeResponseValidator.Status.INVALID_CONTENT_RANGE;
                Decision decision = status == RangeResponseValidator.Status.UNSATISFIABLE
                        ? Decision.UNSATISFIABLE
                        : Decision.INVALID_RESPONSE;
                return result(decision, parsed.totalLength(), validator, finalUrl, response, status,
                        redirect.forwardHeaders());
            }
            if (statusCode != 206) {
                return result(
                        Decision.INVALID_RESPONSE,
                        -1,
                        validator,
                        finalUrl,
                        response,
                        RangeResponseValidator.Status.UNEXPECTED_STATUS,
                        redirect.forwardHeaders());
            }

            HttpContentRange.ParseResult parsed = HttpContentRange.parse(response.header(HEADER_CONTENT_RANGE));
            if (!parsed.isValid()) {
                RangeResponseValidator.Status status = parsed.status() == HttpContentRange.ParseStatus.MISSING
                        ? RangeResponseValidator.Status.MISSING_CONTENT_RANGE
                        : RangeResponseValidator.Status.INVALID_CONTENT_RANGE;
                return result(Decision.INVALID_RESPONSE, -1, validator, finalUrl, response, status,
                        redirect.forwardHeaders());
            }

            HttpContentRange contentRange = parsed.range();
            HttpByteRange requested = new HttpByteRange(0, 0, contentRange.totalLength());
            long actualBodyLength = isIdentity(response.header(HEADER_CONTENT_ENCODING))
                    ? readProbeBody(response.body())
                    : -1;
            RangeResponseValidator.Result validation = RangeResponseValidator.validate(
                    requested,
                    statusCode,
                    response.header(HEADER_CONTENT_RANGE),
                    actualBodyLength,
                    response.header(HEADER_CONTENT_ENCODING));
            if (!validation.isAccepted()) {
                return result(
                        Decision.INVALID_RESPONSE,
                        contentRange.totalLength(),
                        validator,
                        finalUrl,
                        response,
                        validation.status(),
                        redirect.forwardHeaders());
            }
            if (contentRange.totalLength() <= config.fileThresholdBytes()) {
                return result(
                        Decision.BELOW_THRESHOLD,
                        contentRange.totalLength(),
                        validator,
                        finalUrl,
                        response,
                        validation.status(),
                        redirect.forwardHeaders());
            }
            if (!validator.isUsable()) {
                return result(
                        Decision.MISSING_VALIDATOR,
                        contentRange.totalLength(),
                        validator,
                        finalUrl,
                        response,
                        validation.status(),
                        redirect.forwardHeaders());
            }
            return result(
                    Decision.PARALLEL,
                    contentRange.totalLength(),
                    validator,
                    finalUrl,
                    response,
                    validation.status(),
                    redirect.forwardHeaders());
        }
    }

    public byte[] fetchShard(
            String url,
            Map<String, String> requestHeaders,
            RangePlanner.Shard shard,
            long totalLength,
            UpstreamResourceValidator validator) throws IOException {
        Objects.requireNonNull(shard, "shard");
        Objects.requireNonNull(validator, "validator");
        HttpByteRange requested = new HttpByteRange(
                shard.startInclusive(),
                shard.endInclusive(),
                totalLength);
        if (requested.length() > Integer.MAX_VALUE
                || requested.length() > config.bufferBudgetBytes()) {
            throw new IllegalArgumentException("shard exceeds effective in-memory buffer capacity");
        }

        Request request = buildShardRequest(url, requestHeaders, requested, validator);
        try (RedirectResponse redirect = executeRedirecting(request, requestHeaders, null)) {
            Response response = redirect.response();
            RangeResponseValidator.Result metadata = RangeResponseValidator.validate(
                    requested,
                    response.code(),
                    response.header(HEADER_CONTENT_RANGE),
                    -1,
                    response.header(HEADER_CONTENT_ENCODING),
                    validator,
                    response.header(HEADER_ETAG),
                    response.header(HEADER_LAST_MODIFIED));
            if (!metadata.isAccepted()) throw fetchFailure(metadata.status(), response.code());

            BodyRead body = readShardBody(response.body(), Math.toIntExact(requested.length()));
            RangeResponseValidator.Result complete = RangeResponseValidator.validate(
                    requested,
                    response.code(),
                    response.header(HEADER_CONTENT_RANGE),
                    body.actualLength(),
                    response.header(HEADER_CONTENT_ENCODING),
                    validator,
                    response.header(HEADER_ETAG),
                    response.header(HEADER_LAST_MODIFIED));
            if (!complete.isAccepted()) throw fetchFailure(complete.status(), response.code());
            return body.bytes();
        }
    }

    public SingleRequest singleRequest(
            String url,
            Map<String, String> requestHeaders,
            String rangeHeader,
            boolean head) {
        requireHttpUrl(url);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .method(head ? "HEAD" : "GET", null)
                .header(HEADER_ACCEPT_ENCODING, "identity");
        copyAllowedHeaders(builder, requestHeaders);
        boolean ranged = rangeHeader != null && !rangeHeader.isBlank();
        if (ranged) builder.header(HEADER_RANGE, rangeHeader);
        return new SingleRequest(this, builder.build(), requestHeaders, ranged);
    }

    private RedirectResponse executeRedirecting(
            Request initialRequest,
            Map<String, String> requestHeaders,
            Consumer<Call> callObserver) throws IOException {
        Map<String, String> originalHeaders = UpstreamHeaderPolicy.sanitize(requestHeaders);
        Map<String, String> forwardHeaders = originalHeaders;
        Request request = initialRequest;
        for (int redirects = 0; ; redirects++) {
            Call call = track(client.newCall(request));
            if (callObserver != null) callObserver.accept(call);
            Response response = null;
            try {
                response = call.execute();
                if (!response.isRedirect()) {
                    return new RedirectResponse(this, call, response, forwardHeaders);
                }
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("too many upstream redirects");
                }
                String location = response.header("Location");
                HttpUrl nextUrl = location == null ? null : response.request().url().resolve(location);
                if (nextUrl == null) throw new IOException("invalid upstream redirect location");
                boolean crossOrigin = !sameOrigin(response.request().url(), nextUrl);
                forwardHeaders = UpstreamHeaderPolicy.sanitizeForRedirect(forwardHeaders, crossOrigin);
                Request.Builder next = response.request().newBuilder().url(nextUrl);
                for (String name : originalHeaders.keySet()) {
                    next.removeHeader(name);
                }
                copyAllowedHeaders(next, forwardHeaders);
                request = next.build();
            } finally {
                if (response != null && response.isRedirect()) response.close();
                if (response == null || response.isRedirect()) release(call);
            }
        }
    }

    private static boolean sameOrigin(HttpUrl left, HttpUrl right) {
        return left.scheme().equalsIgnoreCase(right.scheme())
                && left.host().equalsIgnoreCase(right.host())
                && left.port() == right.port();
    }
    private Call track(Call call) throws IOException {
        synchronized (lifecycleLock) {
            if (closed) {
                call.cancel();
                throw new IOException("upstream range client is closed");
            }
            activeCalls.add(call);
            return call;
        }
    }

    private void release(Call call) {
        synchronized (lifecycleLock) {
            activeCalls.remove(call);
        }
    }

    @Override
    public void close() {
        final Set<Call> calls;
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true;
            calls = new HashSet<>(activeCalls);
            activeCalls.clear();
        }
        for (Call call : calls) call.cancel();
    }

    private Request buildProbeRequest(String url, Map<String, String> requestHeaders) {
        requireHttpUrl(url);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get()
                .header(HEADER_RANGE, "bytes=0-0")
                .header(HEADER_ACCEPT_ENCODING, "identity");
        copyAllowedHeaders(builder, requestHeaders);
        return builder.build();
    }

    private Request buildShardRequest(
            String url,
            Map<String, String> requestHeaders,
            HttpByteRange requested,
            UpstreamResourceValidator validator) {
        requireHttpUrl(url);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get()
                .header(HEADER_RANGE, requested.requestHeader())
                .header(HEADER_ACCEPT_ENCODING, "identity");
        copyAllowedHeaders(builder, requestHeaders);
        if (validator.isUsable()) builder.header(HEADER_IF_RANGE, validator.ifRangeValue());
        return builder.build();
    }

    private static void copyAllowedHeaders(Request.Builder builder, Map<String, String> requestHeaders) {
        for (Map.Entry<String, String> entry : UpstreamHeaderPolicy.sanitize(requestHeaders).entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
    }

    static void requireHttpUrl(String value) {
        Objects.requireNonNull(value, "url");
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid upstream URL", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("upstream URL must be an absolute HTTP(S) URL without credentials or fragment");
        }
    }

    private static BodyRead readShardBody(ResponseBody body, int expectedLength) throws IOException {
        if (body == null) return new BodyRead(new byte[0], 0);
        try (InputStream input = body.byteStream()) {
            byte[] bytes = new byte[expectedLength];
            int total = 0;
            while (total < expectedLength) {
                int count = input.read(bytes, total, expectedLength - total);
                if (count < 0) break;
                if (count == 0) continue;
                total += count;
            }
            if (total < expectedLength) {
                return new BodyRead(Arrays.copyOf(bytes, total), total);
            }
            int extra = input.read();
            return new BodyRead(bytes, extra < 0 ? total : (long) total + 1);
        }
    }

    private static long readProbeBody(ResponseBody body) throws IOException {
        if (body == null) return 0;
        try (InputStream input = body.byteStream()) {
            byte[] bytes = new byte[PROBE_BODY_LIMIT];
            int total = 0;
            while (total < bytes.length) {
                int count = input.read(bytes, total, bytes.length - total);
                if (count < 0) break;
                if (count == 0) continue;
                total += count;
            }
            return total;
        }
    }

    private static boolean isIdentity(String contentEncoding) {
        return contentEncoding == null
                || contentEncoding.trim().isEmpty()
                || "identity".equalsIgnoreCase(contentEncoding.trim());
    }

    private static RangeFetchException fetchFailure(
            RangeResponseValidator.Status status,
            int responseCode) {
        return new RangeFetchException(status, responseCode);
    }

    private static ProbeResult result(
            Decision decision,
            long totalLength,
            UpstreamResourceValidator validator,
            String finalUrl,
            Response response,
            RangeResponseValidator.Status validationStatus,
            Map<String, String> forwardHeaders) {
        return new ProbeResult(
                decision,
                totalLength,
                validator,
                finalUrl,
                response.header(HEADER_CONTENT_TYPE),
                validationStatus,
                forwardHeaders);
    }

    private static final class RedirectResponse implements AutoCloseable {

        private final UpstreamRangeClient owner;
        private final Call call;
        private final Response response;
        private final Map<String, String> forwardHeaders;
        private boolean handedOff;
        private boolean closed;

        private RedirectResponse(
                UpstreamRangeClient owner,
                Call call,
                Response response,
                Map<String, String> forwardHeaders) {
            this.owner = owner;
            this.call = call;
            this.response = response;
            this.forwardHeaders = Map.copyOf(forwardHeaders);
        }

        private Response response() {
            return response;
        }

        private Map<String, String> forwardHeaders() {
            return forwardHeaders;
        }

        private Response handOff() {
            handedOff = true;
            closed = true;
            return response;
        }

        @Override
        public void close() {
            if (closed || handedOff) return;
            closed = true;
            try {
                response.close();
            } finally {
                owner.release(call);
            }
        }
    }
    public static final class SingleRequest {

        private final UpstreamRangeClient owner;
        private final Request request;
        private final Map<String, String> requestHeaders;
        private final boolean ranged;
        private final AtomicBoolean executed = new AtomicBoolean();
        private volatile Call activeCall;
        private volatile boolean cancelled;

        private SingleRequest(
                UpstreamRangeClient owner,
                Request request,
                Map<String, String> requestHeaders,
                boolean ranged) {
            this.owner = owner;
            this.request = request;
            this.requestHeaders = UpstreamHeaderPolicy.sanitize(requestHeaders);
            this.ranged = ranged;
        }

        public SingleResponse execute() throws IOException {
            if (!executed.compareAndSet(false, true)) throw new IOException("upstream request already executed");
            if (cancelled) throw new IOException("upstream request cancelled");
            try (RedirectResponse redirect = owner.executeRedirecting(
                    request,
                    requestHeaders,
                    call -> {
                        activeCall = call;
                        if (cancelled) call.cancel();
                    })) {
                Response response = redirect.response();
                if (ranged && !isIdentity(response.header(HEADER_CONTENT_ENCODING))) {
                    throw new IOException("encoded upstream range response is not safe to relay");
                }
                return new SingleResponse(owner, activeCall, redirect.handOff());
            }
        }

        public void cancel() {
            cancelled = true;
            Call call = activeCall;
            if (call != null) {
                call.cancel();
                owner.release(call);
            }
        }
    }

    public static final class SingleResponse implements AutoCloseable {

        private final UpstreamRangeClient owner;
        private final Call call;
        private final Response response;
        private final ResponseBody body;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SingleResponse(UpstreamRangeClient owner, Call call, Response response) {
            this.owner = owner;
            this.call = call;
            this.response = response;
            this.body = response.body();
        }

        public int statusCode() {
            return response.code();
        }

        public String header(String name) {
            return response.header(name);
        }

        public String contentType() {
            return response.header(HEADER_CONTENT_TYPE);
        }

        public long contentLength() {
            String declared = response.header("Content-Length");
            if (declared != null) {
                try {
                    long value = Long.parseLong(declared);
                    if (value >= 0) return value;
                } catch (NumberFormatException ignored) {
                }
            }
            return body == null ? 0 : body.contentLength();
        }

        public InputStream bodyStream() {
            return body == null ? new ByteArrayInputStream(new byte[0]) : body.byteStream();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                response.close();
            } finally {
                owner.release(call);
            }
        }
    }
    private record BodyRead(byte[] bytes, long actualLength) {
    }

    public static final class RangeFetchException extends IOException {

        private final RangeResponseValidator.Status status;
        private final int responseCode;

        private RangeFetchException(RangeResponseValidator.Status status, int responseCode) {
            super("upstream range fetch rejected: " + status + " (HTTP " + responseCode + ")");
            this.status = status;
            this.responseCode = responseCode;
        }

        public RangeResponseValidator.Status status() {
            return status;
        }

        public int responseCode() {
            return responseCode;
        }
    }

    public enum Decision {
        PARALLEL,
        FALLBACK_SINGLE_CONNECTION,
        BELOW_THRESHOLD,
        MISSING_VALIDATOR,
        UNSATISFIABLE,
        INVALID_RESPONSE
    }

    public record ProbeResult(
            Decision decision,
            long totalLength,
            UpstreamResourceValidator validator,
            String finalUrl,
            String contentType,
            RangeResponseValidator.Status validationStatus,
            Map<String, String> forwardHeaders) {

        public ProbeResult {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(finalUrl, "finalUrl");
            Objects.requireNonNull(validationStatus, "validationStatus");
            forwardHeaders = Map.copyOf(forwardHeaders);
        }

        public boolean canParallel() {
            return decision == Decision.PARALLEL;
        }
    }
}
