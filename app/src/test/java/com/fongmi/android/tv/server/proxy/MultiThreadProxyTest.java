package com.fongmi.android.tv.server.proxy;

import com.fongmi.android.tv.player.PlaybackRoute;

import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyTest {

    @After
    public void tearDown() {
        MultiThreadProxy.stop();
    }

    @Test
    public void processFacadeAppliesConfigPublishesSnapshotAndStops() throws Exception {
        MultiThreadProxy.stop();
        ProxyPortController.Snapshot running = MultiThreadProxy.apply(enabledAutoConfig());

        assertTrue(running.ready());
        assertTrue(running.actualPort() > 0);
        assertEquals(running, MultiThreadProxy.snapshot());

        MultiThreadProxy.stop();
        ProxyPortController.Snapshot stopped = MultiThreadProxy.snapshot();
        assertFalse(stopped.ready());
        assertEquals(MultiThreadProxyServer.UNBOUND_PORT, stopped.actualPort());
        assertEquals(running.config(), stopped.config());
        assertEquals(running.configRevision(), stopped.configRevision());
    }

    @Test
    public void processFacadeRegistersOpaqueStreamUrlWithoutLeakingTargetOrToken() throws Exception {
        MultiThreadProxy.stop();
        ProxyPortController.Snapshot running = MultiThreadProxy.apply(enabledAutoConfig());
        String target = "https://cdn.example.test/private/movie.mp4?auth=secret";

        try (ProxyStreamRegistration registration = MultiThreadProxy.register(
                target,
                Map.of("Cookie", "sid=secret"))) {
            String streamUrl = registration.url();
            assertTrue(streamUrl.startsWith("http://127.0.0.1:" + running.actualPort() + "/v1/stream/"));
            assertFalse(streamUrl.contains("cdn.example.test"));
            assertFalse(streamUrl.contains("auth=secret"));
            assertFalse(streamUrl.contains("sid=secret"));
            assertFalse(streamUrl.contains(MultiThreadProxy.capabilityToken()));
            assertEquals(PlaybackRoute.Owner.APP_MULTI_THREAD_PROXY, PlaybackRoute.resolve(streamUrl).owner());
        }
    }

    private static ProxyRuntimeConfig enabledAutoConfig() {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                true,
                ProxyRuntimeConfig.PortMode.AUTO,
                0,
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
}
