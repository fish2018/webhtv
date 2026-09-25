package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyCapabilitiesTest {

    @Test
    public void capabilitiesRequireProcessTokenAndNeverEchoIt() throws Exception {
        ProxyCapabilityToken token = ProxyCapabilityToken.generate();
        MultiThreadProxyServer server = new MultiThreadProxyServer(autoConfig(), token, 7);
        try {
            server.startServer();

            assertEquals(401, request(server.actualPort(), null).code());
            assertEquals(401, request(server.actualPort(), "wrong").code());

            HttpResult authorized = request(server.actualPort(), token.value());
            assertEquals(200, authorized.code());
            assertTrue(authorized.body().contains("\"schemaVersion\":1"));
            assertTrue(authorized.body().contains("\"configRevision\":7"));
            assertTrue(authorized.body().contains("\"auth\":\"capability-token\""));
            assertTrue(authorized.body().contains("\"portMode\":\"auto\""));
            assertTrue(authorized.body().contains("\"configuredPort\":0"));
            assertTrue(authorized.body().contains("\"port\":" + server.actualPort()));
            assertTrue(authorized.body().contains("\"serverWorkers\":8"));
            assertFalse(authorized.body().contains(token.value()));
        } finally {
            server.close();
        }
    }

    @Test
    public void capabilitiesPublishValidationWarnings() throws Exception {
        ProxyCapabilityToken token = ProxyCapabilityToken.generate();
        MultiThreadProxyServer server = new MultiThreadProxyServer(autoConfig(1), token, 8);
        try {
            server.startServer();

            HttpResult authorized = request(server.actualPort(), token.value());

            assertEquals(200, authorized.code());
            assertTrue(authorized.body().contains("\"warnings\""));
            assertTrue(authorized.body().contains("\"SERVER_WORKERS_OUTSIDE_RECOMMENDED\""));
        } finally {
            server.close();
        }
    }

    private static HttpResult request(int port, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + MultiThreadProxyServer.CAPABILITIES_PATH).openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        if (token != null) connection.setRequestProperty(MultiThreadProxyServer.TOKEN_HEADER, token);
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            return new HttpResult(code, body);
        } finally {
            connection.disconnect();
        }
    }

    private static ProxyRuntimeConfig autoConfig() {
        return autoConfig(ProxyRuntimeConfig.defaults().serverWorkers());
    }

    private static ProxyRuntimeConfig autoConfig(int serverWorkers) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                true,
                ProxyRuntimeConfig.PortMode.AUTO,
                ProxyRuntimeConfig.AUTO_PORT,
                serverWorkers,
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

    private record HttpResult(int code, String body) {
    }
}