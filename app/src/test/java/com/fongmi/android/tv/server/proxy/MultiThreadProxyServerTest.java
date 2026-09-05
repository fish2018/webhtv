package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyServerTest {

    @Test
    public void autoPortBindsLoopbackAndServesMinimalHealth() throws Exception {
        MultiThreadProxyServer server = new MultiThreadProxyServer(config(ProxyRuntimeConfig.PortMode.AUTO, 0));
        try {
            assertEquals(MultiThreadProxyServer.LOOPBACK_HOST, server.getHostname());

            server.startServer();

            int port = server.actualPort();
            assertTrue(port > 0);
            assertTrue(server.isReady());
            HttpResult health = request(port, MultiThreadProxyServer.HEALTH_PATH);
            assertEquals(200, health.code());
            assertEquals("{\"schemaVersion\":1,\"ready\":true,\"port\":" + port + "}", health.body());

            HttpResult missing = request(port, "/not-exposed");
            assertEquals(404, missing.code());
        } finally {
            server.close();
        }

        assertFalse(server.isReady());
        assertEquals(MultiThreadProxyServer.UNBOUND_PORT, server.actualPort());
    }

    @Test
    public void fixedPortConflictFailsWithoutSilentFallback() throws Exception {
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress(InetAddress.getByName(MultiThreadProxyServer.LOOPBACK_HOST), 0));
            int port = occupied.getLocalPort();
            MultiThreadProxyServer server = new MultiThreadProxyServer(config(ProxyRuntimeConfig.PortMode.FIXED, port));
            try {
                assertThrows(IOException.class, server::startServer);
                assertFalse(server.isReady());
                assertEquals(MultiThreadProxyServer.UNBOUND_PORT, server.actualPort());
            } finally {
                server.close();
            }
        }
    }

    private static ProxyRuntimeConfig config(ProxyRuntimeConfig.PortMode portMode, int port) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                true,
                portMode,
                port,
                defaults.serverWorkers(),
                defaults.connectionQueueCapacity(),
                defaults.rangeWorkers(),
                defaults.rangeConcurrency(),
                defaults.shardMode(),
                defaults.shardCount(),
                defaults.chunkSizeBytes(),
                defaults.maxSessions(),
                defaults.reorderWindowBlocks(),
                defaults.bufferBudgetBytes(),
                defaults.retryCount(),
                defaults.connectTimeoutMillis(),
                defaults.readTimeoutMillis(),
                defaults.fileThresholdBytes());
    }

    private static HttpResult request(int port, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        connection.setRequestMethod("GET");
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body;
            if (stream == null) {
                body = "";
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    body = reader.lines().collect(Collectors.joining("\n"));
                }
            }
            return new HttpResult(code, body);
        } finally {
            connection.disconnect();
        }
    }

    private record HttpResult(int code, String body) {
    }
}
