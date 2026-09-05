package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ProxyRuntimeConfigTest {

    @Test
    public void defaultsMatchFirstReleaseContract() {
        ProxyRuntimeConfig config = ProxyRuntimeConfig.defaults();

        assertEquals(1, config.schemaVersion());
        assertFalse(config.enabled());
        assertEquals(ProxyRuntimeConfig.PortMode.FIXED, config.portMode());
        assertEquals(17575, config.configuredPort());
        assertEquals(8, config.serverWorkers());
        assertEquals(64, config.connectionQueueCapacity());
        assertEquals(10, config.rangeWorkers());
        assertEquals(10, config.rangeConcurrency());
        assertEquals(ProxyRuntimeConfig.ShardMode.COUNT, config.shardMode());
        assertEquals(256, config.shardCount());
        assertEquals(1024L * 1024L, config.chunkSizeBytes());
        assertEquals(2, config.maxSessions());
        assertEquals(0, config.reorderWindowBlocks());
        assertEquals(20L * 1024L * 1024L, config.bufferBudgetBytes());
        assertEquals(2, config.retryCount());
        assertEquals(10_000L, config.connectTimeoutMillis());
        assertEquals(30_000L, config.readTimeoutMillis());
        assertEquals(32L * 1024L * 1024L, config.fileThresholdBytes());
    }

    @Test
    public void effectiveReorderWindowRespectsConfiguredBudgetAndRemainingShards() {
        ProxyRuntimeConfig config = ProxyRuntimeConfig.defaults();
        long chunk = config.chunkSizeBytes();

        assertEquals(20, config.effectiveReorderWindowBlocks(config.bufferBudgetBytes(), 100));
        assertEquals(3, config.effectiveReorderWindowBlocks(3 * chunk, 100));
        assertEquals(2, config.effectiveReorderWindowBlocks(config.bufferBudgetBytes(), 2));
        assertEquals(0, config.effectiveReorderWindowBlocks(chunk - 1, 100));
        assertEquals(0, config.effectiveReorderWindowBlocks(config.bufferBudgetBytes(), 0));
    }
}