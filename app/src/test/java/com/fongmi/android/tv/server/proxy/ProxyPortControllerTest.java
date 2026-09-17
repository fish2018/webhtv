package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProxyPortControllerTest {

    @Test
    public void sameConfigIsIdempotentAndSuccessfulChangeReplacesServer() throws Exception {
        ProxyPortController controller = new ProxyPortController();
        try {
            ProxyRuntimeConfig firstConfig = config(true, ProxyRuntimeConfig.PortMode.AUTO, 0, 1);
            ProxyPortController.Snapshot first = controller.apply(firstConfig);
            assertTrue(first.ready());
            assertEquals(1L, first.configRevision());
            assertEquals(200, responseCode(first.actualPort()));

            ProxyPortController.Snapshot same = controller.apply(firstConfig);
            assertEquals(first.configRevision(), same.configRevision());
            assertEquals(first.actualPort(), same.actualPort());

            ProxyRuntimeConfig changedConfig = config(true, ProxyRuntimeConfig.PortMode.AUTO, 0, 2);
            ProxyPortController.Snapshot changed = controller.apply(changedConfig);
            assertEquals(2L, changed.configRevision());
            assertTrue(changed.ready());
            assertNotEquals(first.actualPort(), changed.actualPort());
            assertEquals(200, responseCode(changed.actualPort()));
        } finally {
            controller.close();
        }
    }

    @Test
    public void fixedPortCanRebuildListenerSettingsOnTheSamePort() throws Exception {
        int port;
        try (ServerSocket available = new ServerSocket()) {
            available.bind(new InetSocketAddress(InetAddress.getByName(MultiThreadProxyServer.LOOPBACK_HOST), 0));
            port = available.getLocalPort();
        }

        ProxyPortController controller = new ProxyPortController();
        try {
            ProxyPortController.Snapshot first = controller.apply(
                    config(true, ProxyRuntimeConfig.PortMode.FIXED, port, 1));
            ProxyPortController.Snapshot changed = controller.apply(
                    config(true, ProxyRuntimeConfig.PortMode.FIXED, port, 2));

            assertEquals(first.configRevision() + 1, changed.configRevision());
            assertEquals(port, changed.actualPort());
            assertTrue(changed.ready());
            assertEquals(200, responseCode(port));
        } finally {
            controller.close();
        }
    }

    @Test
    public void failedReconfigurationKeepsPreviousServiceAndRevision() throws Exception {
        ProxyPortController controller = new ProxyPortController();
        try {
            ProxyPortController.Snapshot before = controller.apply(
                    config(true, ProxyRuntimeConfig.PortMode.AUTO, 0, 1));

            try (ServerSocket occupied = new ServerSocket()) {
                occupied.bind(new InetSocketAddress(InetAddress.getByName(MultiThreadProxyServer.LOOPBACK_HOST), 0));
                ProxyRuntimeConfig conflicting = config(
                        true,
                        ProxyRuntimeConfig.PortMode.FIXED,
                        occupied.getLocalPort(),
                        2);

                assertThrows(IOException.class, () -> controller.apply(conflicting));
            }

            ProxyPortController.Snapshot after = controller.snapshot();
            assertEquals(before.configRevision(), after.configRevision());
            assertEquals(before.config(), after.config());
            assertEquals(before.actualPort(), after.actualPort());
            assertTrue(after.ready());
            assertEquals(200, responseCode(after.actualPort()));
        } finally {
            controller.close();
        }
    }

    @Test
    public void disablingStopsServiceAndPublishesUnboundSnapshot() throws Exception {
        ProxyPortController controller = new ProxyPortController();
        try {
            ProxyPortController.Snapshot running = controller.apply(
                    config(true, ProxyRuntimeConfig.PortMode.AUTO, 0, 1));
            ProxyPortController.Snapshot stopped = controller.apply(
                    config(false, ProxyRuntimeConfig.PortMode.AUTO, 0, 1));

            assertEquals(running.configRevision() + 1, stopped.configRevision());
            assertFalse(stopped.ready());
            assertEquals(MultiThreadProxyServer.UNBOUND_PORT, stopped.actualPort());
        } finally {
            controller.close();
        }
    }

    private static int responseCode(int port) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + MultiThreadProxyServer.HEALTH_PATH).openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static ProxyRuntimeConfig config(
            boolean enabled,
            ProxyRuntimeConfig.PortMode portMode,
            int port,
            int serverWorkers) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                enabled,
                portMode,
                port,
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
}
