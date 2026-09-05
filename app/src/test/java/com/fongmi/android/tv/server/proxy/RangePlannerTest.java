package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RangePlannerTest {

    @Test
    public void countModeProducesExactlyRequestedBalancedShards() {
        HttpByteRange range = new HttpByteRange(100, 109, 1_000);
        RangePlanner.Plan plan = RangePlanner.plan(range, config(ProxyRuntimeConfig.ShardMode.COUNT, 3, 4));

        assertEquals(3, plan.shardCount());
        assertShard(plan.shard(0), 0, 100, 103);
        assertShard(plan.shard(1), 1, 104, 106);
        assertShard(plan.shard(2), 2, 107, 109);
    }

    @Test
    public void sizeAndAutoModesUseConfiguredChunkSize() {
        HttpByteRange range = new HttpByteRange(50, 59, 1_000);

        RangePlanner.Plan size = RangePlanner.plan(
                range,
                config(ProxyRuntimeConfig.ShardMode.SIZE, 0, 4));
        RangePlanner.Plan auto = RangePlanner.plan(
                range,
                config(ProxyRuntimeConfig.ShardMode.AUTO, 0, 4));

        for (RangePlanner.Plan plan : new RangePlanner.Plan[]{size, auto}) {
            assertEquals(3, plan.shardCount());
            assertShard(plan.shard(0), 0, 50, 53);
            assertShard(plan.shard(1), 1, 54, 57);
            assertShard(plan.shard(2), 2, 58, 59);
        }
    }

    @Test
    public void transferBlocksBoundCountModeAndUseSmallBootstrap() {
        long mib = 1024L * 1024L;
        HttpByteRange range = new HttpByteRange(0, 20 * mib - 1, 20 * mib);
        RangePlanner.Plan plan = RangePlanner.planTransferBlocks(
                range,
                config(ProxyRuntimeConfig.ShardMode.COUNT, 2, mib));

        assertEquals(100L, RangePlanner.BOOTSTRAP_BLOCK_BYTES);
        assertEquals(21, plan.shardCount());
        assertShard(plan.shard(0), 0, 0, RangePlanner.BOOTSTRAP_BLOCK_BYTES - 1);
        assertEquals(mib, plan.shard(1).length());
        assertEquals(range.endInclusive(), plan.shard(plan.shardCount() - 1).endInclusive());
    }

    @Test
    public void countLargerThanPayloadDoesNotCreateEmptyShards() {
        HttpByteRange range = new HttpByteRange(10, 11, 100);
        RangePlanner.Plan plan = RangePlanner.plan(
                range,
                config(ProxyRuntimeConfig.ShardMode.COUNT, 10, 4));

        assertEquals(2, plan.shardCount());
        assertShard(plan.shard(0), 0, 10, 10);
        assertShard(plan.shard(1), 1, 11, 11);
    }

    @Test
    public void hugePlansRemainLazyAndSupportIndexedIteration() {
        HttpByteRange range = new HttpByteRange(0, Long.MAX_VALUE - 1, Long.MAX_VALUE);
        RangePlanner.Plan plan = RangePlanner.plan(
                range,
                config(ProxyRuntimeConfig.ShardMode.COUNT, ProxyRuntimeConfigValidator.MAX_SHARD_COUNT, 1));

        assertEquals(ProxyRuntimeConfigValidator.MAX_SHARD_COUNT, plan.shardCount());
        RangePlanner.Shard first = plan.shard(0);
        RangePlanner.Shard last = plan.shard(plan.shardCount() - 1);
        assertEquals(0, first.startInclusive());
        assertEquals(Long.MAX_VALUE - 1, last.endInclusive());

        Iterator<RangePlanner.Shard> iterator = plan.iterator(plan.shardCount() - 1);
        assertTrue(iterator.hasNext());
        assertEquals(last, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(IndexOutOfBoundsException.class, () -> plan.shard(plan.shardCount()));
    }

    private static void assertShard(RangePlanner.Shard shard, long index, long start, long end) {
        assertEquals(index, shard.index());
        assertEquals(start, shard.startInclusive());
        assertEquals(end, shard.endInclusive());
        assertEquals(end - start + 1, shard.length());
    }

    private static ProxyRuntimeConfig config(
            ProxyRuntimeConfig.ShardMode shardMode,
            int shardCount,
            long chunkSizeBytes) {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        return new ProxyRuntimeConfig(
                defaults.schemaVersion(),
                defaults.enabled(),
                defaults.portMode(),
                defaults.configuredPort(),
                defaults.serverWorkers(),
                defaults.connectionQueueCapacity(),
                defaults.rangeWorkers(),
                defaults.rangeConcurrency(),
                shardMode,
                shardCount,
                chunkSizeBytes,
                defaults.maxSessions(),
                defaults.reorderWindowBlocks(),
                Math.max(defaults.bufferBudgetBytes(), chunkSizeBytes),
                defaults.retryCount(),
                defaults.connectTimeoutMillis(),
                defaults.readTimeoutMillis(),
                defaults.fileThresholdBytes());
    }
}
