package com.fongmi.android.tv.server.proxy;

import com.fongmi.android.tv.player.PlaybackRouteRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

public final class MultiThreadProxyServer extends NanoHTTPD implements AutoCloseable {

    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final String HEALTH_PATH = "/v1/health";
    public static final String CAPABILITIES_PATH = "/v1/capabilities";
    public static final String PROXY_PATH = "/v1/proxy";
    public static final String STREAM_PATH_PREFIX = ProxyTargetRegistry.STREAM_PATH_PREFIX;
    public static final String TOKEN_HEADER = "X-WebHtv-Proxy-Token";
    public static final int UNBOUND_PORT = -1;

    private static final String MIME_JSON = "application/json; charset=utf-8";
    private static final int START_TIMEOUT_MILLIS = 500;
    private static final int PIPE_BUFFER_BYTES = 32 * 1024;

    private final ProxyRuntimeConfig config;
    private final ProxyDomainRuleSet domainRules;
    private final ProxyCapabilityToken capabilityToken;
    private final ProxySessionRegistry sessions;
    private final ProxyTargetRegistry targets;
    private final UpstreamRangeClient upstream;
    private final SegmentedRangeEngine rangeEngine;
    private final ThreadPoolExecutor streamWorkers;
    private final long configRevision;
    private PlaybackRouteRegistry.Registration routeRegistration;
    private volatile int actualPort;

    public MultiThreadProxyServer(ProxyRuntimeConfig config) {
        this(config, ProxyCapabilityToken.generate(), 0, ProxyDomainRuleSet.EMPTY);
    }

    MultiThreadProxyServer(
            ProxyRuntimeConfig config,
            ProxyCapabilityToken capabilityToken,
            long configRevision) {
        this(config, capabilityToken, configRevision, ProxyDomainRuleSet.EMPTY);
    }

    MultiThreadProxyServer(
            ProxyRuntimeConfig config,
            ProxyCapabilityToken capabilityToken,
            long configRevision,
            ProxyDomainRuleSet domainRules) {
        this(config, capabilityToken, configRevision, new UpstreamRangeClient(config), domainRules);
    }

    MultiThreadProxyServer(
            ProxyRuntimeConfig config,
            ProxyCapabilityToken capabilityToken,
            long configRevision,
            UpstreamRangeClient upstream) {
        this(config, capabilityToken, configRevision, upstream, ProxyDomainRuleSet.EMPTY);
    }

    MultiThreadProxyServer(
            ProxyRuntimeConfig config,
            ProxyCapabilityToken capabilityToken,
            long configRevision,
            UpstreamRangeClient upstream,
            ProxyDomainRuleSet domainRules) {
        super(LOOPBACK_HOST, listeningPort(config));
        this.config = config;
        this.domainRules = domainRules == null ? ProxyDomainRuleSet.EMPTY : domainRules;
        this.capabilityToken = capabilityToken;
        this.sessions = new ProxySessionRegistry(config.maxSessions());
        this.targets = new ProxyTargetRegistry();
        this.upstream = upstream;
        this.rangeEngine = new SegmentedRangeEngine(config, upstream, sessions);
        this.streamWorkers = new ThreadPoolExecutor(
                0,
                config.maxSessions(),
                30,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new StreamThreadFactory());
        this.streamWorkers.allowCoreThreadTimeOut(true);
        this.configRevision = configRevision;
        this.actualPort = UNBOUND_PORT;
        setAsyncRunner(new BoundedAsyncRunner(
                config.serverWorkers(),
                config.connectionQueueCapacity()));
    }

    public synchronized void startServer() throws IOException {
        if (isReady()) return;
        try {
            start(START_TIMEOUT_MILLIS, true);
            int port = getListeningPort();
            if (port <= 0) throw new IOException("Multi-thread proxy did not publish a listening port");
            routeRegistration = PlaybackRouteRegistry.registerAppService(
                    port,
                    PlaybackRouteRegistry.AppOwner.MULTI_THREAD_PROXY);
            actualPort = port;
        } catch (IOException | RuntimeException e) {
            actualPort = UNBOUND_PORT;
            stop();
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        actualPort = UNBOUND_PORT;
        PlaybackRouteRegistry.Registration registration = routeRegistration;
        routeRegistration = null;
        if (registration != null) registration.close();
        targets.close();
        sessions.close();
        rangeEngine.close();
        upstream.close();
        streamWorkers.shutdownNow();
        stop();
    }

    public ProxyRuntimeConfig config() {
        return config;
    }

    public int actualPort() {
        return actualPort;
    }

    public boolean isReady() {
        return actualPort > 0 && isAlive();
    }

    ProxySessionRegistry sessions() {
        return sessions;
    }

    synchronized ProxyStreamRegistration registerTarget(
            String url,
            Map<String, String> headers) throws IOException {
        if (!isReady()) throw new IOException("multi-thread proxy is not ready");
        ProxyTargetRegistry.Registration registration = targets.register(url, headers);
        String streamUrl = "http://" + LOOPBACK_HOST + ":" + actualPort + registration.path();
        return new ProxyStreamRegistration(streamUrl, registration::close);
    }

    @Override
    protected ClientHandler createClientHandler(Socket socket, InputStream inputStream) {
        return new LoopbackClientHandler(socket, inputStream);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (HEALTH_PATH.equals(uri)) return serveHealth(session);
        if (CAPABILITIES_PATH.equals(uri)) return serveCapabilities(session);
        if (PROXY_PATH.equals(uri)) return serveProxy(session);
        if (uri != null && uri.startsWith(STREAM_PATH_PREFIX)) return serveStream(session, uri);
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private Response serveProxy(IHTTPSession session) {
        if (!capabilityToken.matches(header(session, TOKEN_HEADER))) return unauthorized();
        if (!supportsReadMethod(session)) return methodNotAllowed();
        String target = session.getParms().get("url");
        if (target == null || target.isBlank()) return error(Response.Status.BAD_REQUEST, "Missing upstream URL");
        return serveTarget(session, target.trim(), session.getHeaders());
    }

    private Response serveStream(IHTTPSession session, String uri) {
        if (!supportsReadMethod(session)) return methodNotAllowed();
        String id = uri.substring(STREAM_PATH_PREFIX.length());
        ProxyTargetRegistry.Target target = targets.resolve(id);
        if (target == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        }
        return serveTarget(session, target.url(), target.headers());
    }

    private Response serveTarget(
            IHTTPSession session,
            String target,
            Map<String, String> requestHeaders) {
        if (!supportsReadMethod(session)) return methodNotAllowed();
        String range = header(session, "Range");
        if (session.getMethod() == Method.HEAD || range == null || range.isBlank()) {
            return serveSingleConnection(session, target, requestHeaders, range);
        }

        final StreamingTransfer transfer;
        final SegmentedRangeEngine.TransferResult prepared;
        try {
            ProxyRuntimeConfig sessionConfig = domainRules.resolve(target, config);
            transfer = new StreamingTransfer(target, requestHeaders, range, sessionConfig);
            prepared = transfer.awaitPrepared();
        } catch (RejectedExecutionException e) {
            return serviceUnavailable();
        } catch (IllegalArgumentException e) {
            return error(Response.Status.BAD_REQUEST, "Invalid upstream request");
        } catch (IOException e) {
            return error(ProxyStatus.BAD_GATEWAY, "Upstream request failed");
        }

        if (prepared.decision() == SegmentedRangeEngine.Decision.PARALLEL) {
            return parallelResponse(transfer, prepared);
        }
        transfer.close();
        if (prepared.decision() == SegmentedRangeEngine.Decision.FALLBACK_SINGLE_CONNECTION) {
            return serveSingleConnection(session, target, requestHeaders, range);
        }
        return switch (prepared.decision()) {
            case UNSATISFIABLE -> rangeNotSatisfiable(prepared.totalLength());
            case INVALID_RANGE -> error(Response.Status.BAD_REQUEST, "Invalid Range");
            case INVALID_UPSTREAM -> error(ProxyStatus.BAD_GATEWAY, "Invalid upstream range response");
            case SESSION_LIMIT, RESOURCE_LIMIT -> serviceUnavailable();
            case FALLBACK_SINGLE_CONNECTION, PARALLEL ->
                    throw new IllegalStateException("proxy decision was already handled");
        };
    }

    private Response serveSingleConnection(
            IHTTPSession session,
            String target,
            Map<String, String> requestHeaders,
            String range) {
        final UpstreamRangeClient.SingleRequest request;
        try {
            request = upstream.singleRequest(
                    target,
                    requestHeaders,
                    range,
                    session.getMethod() == Method.HEAD);
        } catch (IllegalArgumentException e) {
            return error(Response.Status.BAD_REQUEST, "Invalid upstream request");
        }

        Optional<ProxySessionRegistry.Lease> acquired = sessions.tryAcquire(request::cancel);
        if (acquired.isEmpty()) {
            request.cancel();
            return serviceUnavailable();
        }
        ProxySessionRegistry.Lease lease = acquired.orElseThrow();
        try {
            UpstreamRangeClient.SingleResponse upstreamResponse = request.execute();
            Response.IStatus status = Response.Status.lookup(upstreamResponse.statusCode());
            if (status == null) status = ProxyStatus.BAD_GATEWAY;
            String mime = upstreamResponse.contentType();
            if (mime == null || mime.isBlank()) mime = "application/octet-stream";
            SingleConnectionInputStream stream = new SingleConnectionInputStream(
                    upstreamResponse.bodyStream(),
                    upstreamResponse,
                    lease);
            Response response = upstreamResponse.contentLength() >= 0
                    ? newFixedLengthResponse(status, mime, stream, upstreamResponse.contentLength())
                    : newChunkedResponse(status, mime, stream);
            copyRelayHeader(response, upstreamResponse, "Content-Range");
            copyRelayHeader(response, upstreamResponse, "Accept-Ranges");
            copyRelayHeader(response, upstreamResponse, "ETag");
            copyRelayHeader(response, upstreamResponse, "Last-Modified");
            copyRelayHeader(response, upstreamResponse, "Content-Encoding");
            response.addHeader("Cache-Control", "no-store");
            response.addHeader("Connection", "close");
            return response;
        } catch (IOException e) {
            lease.close();
            return error(ProxyStatus.BAD_GATEWAY, "Upstream request failed");
        }
    }

    private static void copyRelayHeader(
            Response response,
            UpstreamRangeClient.SingleResponse upstreamResponse,
            String name) {
        String value = upstreamResponse.header(name);
        if (value != null && !value.isBlank()) response.addHeader(name, value);
    }

    private static Response parallelResponse(
            StreamingTransfer transfer,
            SegmentedRangeEngine.TransferResult prepared) {
        long length = prepared.endInclusive() - prepared.startInclusive() + 1;
        UpstreamRangeClient.ProbeResult probe = prepared.probe();
        String mime = probe.contentType() == null || probe.contentType().isBlank()
                ? "application/octet-stream"
                : probe.contentType();
        Response response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mime,
                transfer,
                length);
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Content-Range", "bytes " + prepared.startInclusive() + "-"
                + prepared.endInclusive() + "/" + prepared.totalLength());
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Connection", "close");
        UpstreamResourceValidator validator = probe.validator();
        if (validator.type() == UpstreamResourceValidator.Type.STRONG_ETAG) {
            response.addHeader("ETag", validator.value());
        } else if (validator.type() == UpstreamResourceValidator.Type.LAST_MODIFIED) {
            response.addHeader("Last-Modified", validator.value());
        }
        return response;
    }

    private static Response rangeNotSatisfiable(long totalLength) {
        Response response = error(Response.Status.RANGE_NOT_SATISFIABLE, "Range Not Satisfiable");
        if (totalLength >= 0) response.addHeader("Content-Range", "bytes */" + totalLength);
        return response;
    }

    private static Response serviceUnavailable() {
        Response response = error(Response.Status.SERVICE_UNAVAILABLE, "Service Unavailable");
        response.addHeader("Retry-After", "1");
        return response;
    }

    private static Response error(Response.IStatus status, String message) {
        Response response = newFixedLengthResponse(status, MIME_PLAINTEXT, message);
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Connection", "close");
        return response;
    }

    private Response serveHealth(IHTTPSession session) {
        if (!supportsReadMethod(session)) return methodNotAllowed();
        Response response = newFixedLengthResponse(Response.Status.OK, MIME_JSON, healthJson());
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    private Response serveCapabilities(IHTTPSession session) {
        if (!capabilityToken.matches(header(session, TOKEN_HEADER))) return unauthorized();
        if (!supportsReadMethod(session)) return methodNotAllowed();
        Response response = newFixedLengthResponse(Response.Status.OK, MIME_JSON, capabilitiesJson());
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    private String healthJson() {
        return "{\"schemaVersion\":" + ProxyRuntimeConfig.SCHEMA_VERSION
                + ",\"ready\":" + isReady()
                + ",\"port\":" + actualPort + "}";
    }

    private String capabilitiesJson() {
        String shardCount = config.shardCount() == ProxyRuntimeConfig.AUTO_SHARD_COUNT
                ? "\"auto\""
                : Integer.toString(config.shardCount());
        return "{\"schemaVersion\":" + ProxyRuntimeConfig.SCHEMA_VERSION
                + ",\"configRevision\":" + configRevision
                + ",\"auth\":\"capability-token\""
                + ",\"portMode\":\"" + config.portMode().value() + "\""
                + ",\"configuredPort\":" + config.configuredPort()
                + ",\"port\":" + actualPort
                + ",\"activeSessions\":" + sessions.activeCount()
                + ",\"config\":{\"serverWorkers\":" + config.serverWorkers()
                + ",\"connectionQueueCapacity\":" + config.connectionQueueCapacity()
                + ",\"rangeWorkers\":" + config.rangeWorkers()
                + ",\"rangeConcurrency\":" + config.rangeConcurrency()
                + ",\"shardMode\":\"" + config.shardMode().value() + "\""
                + ",\"shardCount\":" + shardCount
                + ",\"chunkSizeBytes\":" + config.chunkSizeBytes()
                + ",\"maxSessions\":" + config.maxSessions()
                + ",\"reorderWindowBlocks\":" + config.reorderWindowBlocks()
                + ",\"bufferBudgetBytes\":" + config.bufferBudgetBytes()
                + ",\"retryCount\":" + config.retryCount()
                + ",\"connectTimeoutMillis\":" + config.connectTimeoutMillis()
                + ",\"readTimeoutMillis\":" + config.readTimeoutMillis()
                + ",\"fileThresholdBytes\":" + config.fileThresholdBytes()
                + "},\"warnings\":" + warningsJson() + "}";
    }

    private String warningsJson() {
        StringBuilder json = new StringBuilder("[");
        for (ProxyRuntimeConfigValidator.Issue warning
                : ProxyRuntimeConfigValidator.validate(config).warnings()) {
            if (json.length() > 1) json.append(',');
            json.append('"').append(warning.name()).append('"');
        }
        return json.append(']').toString();
    }

    private static boolean supportsReadMethod(IHTTPSession session) {
        return session.getMethod() == Method.GET || session.getMethod() == Method.HEAD;
    }

    private static Response methodNotAllowed() {
        Response response = newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "Method Not Allowed");
        response.addHeader("Allow", "GET, HEAD");
        return response;
    }

    private static Response unauthorized() {
        Response response = newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized");
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    private static String header(IHTTPSession session, String expectedName) {
        for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
            if (expectedName.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private static int listeningPort(ProxyRuntimeConfig config) {
        ProxyRuntimeConfigValidator.requireValid(config);
        return config.portMode() == ProxyRuntimeConfig.PortMode.AUTO
                ? ProxyRuntimeConfig.AUTO_PORT
                : config.configuredPort();
    }

    private static final class SingleConnectionInputStream extends InputStream {

        private final InputStream delegate;
        private final UpstreamRangeClient.SingleResponse response;
        private final ProxySessionRegistry.Lease lease;
        private final AtomicBoolean closed = new AtomicBoolean();

        private SingleConnectionInputStream(
                InputStream delegate,
                UpstreamRangeClient.SingleResponse response,
                ProxySessionRegistry.Lease lease) {
            this.delegate = delegate;
            this.response = response;
            this.lease = lease;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                delegate.close();
            } catch (IOException ignored) {
            } finally {
                response.close();
                lease.close();
            }
        }
    }

    private final class StreamingTransfer extends InputStream {

        private final PipedInputStream input;
        private final PipedOutputStream output;
        private final CompletableFuture<SegmentedRangeEngine.TransferResult> prepared = new CompletableFuture<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Future<?> task;

        private StreamingTransfer(
                String target,
                Map<String, String> headers,
                String range,
                ProxyRuntimeConfig sessionConfig) throws IOException {
            input = new PipedInputStream(PIPE_BUFFER_BYTES);
            output = new PipedOutputStream(input);
            try {
                task = streamWorkers.submit(() -> runTransfer(target, headers, range, sessionConfig));
            } catch (RejectedExecutionException e) {
                close();
                throw e;
            }
        }

        private void runTransfer(
                String target,
                Map<String, String> headers,
                String range,
                ProxyRuntimeConfig sessionConfig) {
            try (PipedOutputStream sink = output) {
                SegmentedRangeEngine.TransferResult result = rangeEngine.transfer(
                        target,
                        headers,
                        range,
                        sink,
                        sessionConfig,
                        prepared::complete);
                prepared.complete(result);
            } catch (Throwable failure) {
                prepared.completeExceptionally(failure);
                if (failure instanceof Error error) throw error;
            }
        }

        private SegmentedRangeEngine.TransferResult awaitPrepared() throws IOException {
            try {
                return prepared.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                close();
                throw new IOException("interrupted while preparing proxy response", e);
            } catch (ExecutionException e) {
                close();
                Throwable cause = e.getCause();
                if (cause instanceof IllegalArgumentException invalid) throw invalid;
                if (cause instanceof IOException io) throw io;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IOException("proxy response preparation failed", cause);
            }
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return input.read(bytes, offset, length);
        }

        @Override
        public int available() throws IOException {
            return input.available();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                input.close();
            } catch (IOException ignored) {
            }
            try {
                output.close();
            } catch (IOException ignored) {
            }
            Future<?> current = task;
            if (current != null) current.cancel(true);
        }
    }

    private static final class StreamThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MultiThreadProxy-Stream-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private enum ProxyStatus implements Response.IStatus {
        BAD_GATEWAY(502, "Bad Gateway");

        private final int code;
        private final String description;

        ProxyStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }

        @Override
        public String getDescription() {
            return code + " " + description;
        }

        @Override
        public int getRequestStatus() {
            return code;
        }
    }

    private final class LoopbackClientHandler extends ClientHandler implements BoundedAsyncRunner.OverloadHandler {

        private final Socket socket;

        private LoopbackClientHandler(Socket socket, InputStream inputStream) {
            super(inputStream, socket);
            this.socket = socket;
        }

        @Override
        public void rejectOverload() {
            byte[] body = "Service Unavailable".getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "Retry-After: 1\r\n\r\n";
            try {
                OutputStream output = socket.getOutputStream();
                output.write(headers.getBytes(StandardCharsets.US_ASCII));
                output.write(body);
                output.flush();
            } catch (IOException ignored) {
                // The peer may have disconnected before overload could be reported.
            } finally {
                close();
            }
        }
    }
}
