package com.fongmi.android.tv.server.proxy;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class RangePlanner {

    static final long BOOTSTRAP_BLOCK_BYTES = 100L;

    private RangePlanner() {
    }

    public static Plan plan(HttpByteRange range, ProxyRuntimeConfig config) {
        Objects.requireNonNull(range, "range");
        ProxyRuntimeConfigValidator.requireValid(config);
        if (config.shardMode() == ProxyRuntimeConfig.ShardMode.COUNT) {
            long count = Math.min((long) config.shardCount(), range.length());
            return Plan.byCount(range, count);
        }
        return Plan.bySize(range, config.chunkSizeBytes());
    }

    /**
     * Refines user-visible logical shards into bounded transport blocks. The first block is
     * intentionally small so a slow upstream can deliver startup metadata without waiting for a
     * multi-megabyte logical shard to finish.
     */
    static Plan planTransferBlocks(HttpByteRange range, ProxyRuntimeConfig config) {
        Plan logicalPlan = plan(range, config);
        long regularBlockSize = Math.min(logicalPlan.maxShardLength(), config.chunkSizeBytes());
        long bootstrapBlockSize = Math.min(regularBlockSize, BOOTSTRAP_BLOCK_BYTES);
        return Plan.byBootstrapAndSize(range, bootstrapBlockSize, regularBlockSize);
    }

    public static final class Plan implements Iterable<Shard> {

        private final HttpByteRange range;
        private final ProxyRuntimeConfig.ShardMode mode;
        private final long shardCount;
        private final long firstChunkSize;
        private final long chunkSize;

        private Plan(
                HttpByteRange range,
                ProxyRuntimeConfig.ShardMode mode,
                long shardCount,
                long firstChunkSize,
                long chunkSize) {
            this.range = range;
            this.mode = mode;
            this.shardCount = shardCount;
            this.firstChunkSize = firstChunkSize;
            this.chunkSize = chunkSize;
        }

        private static Plan byCount(HttpByteRange range, long count) {
            return new Plan(range, ProxyRuntimeConfig.ShardMode.COUNT, count, 0, 0);
        }

        private static Plan bySize(HttpByteRange range, long chunkSize) {
            return byBootstrapAndSize(range, chunkSize, chunkSize);
        }

        private static Plan byBootstrapAndSize(
                HttpByteRange range,
                long firstChunkSize,
                long chunkSize) {
            long firstLength = Math.min(range.length(), firstChunkSize);
            long remaining = range.length() - firstLength;
            long count = 1 + (remaining == 0 ? 0 : 1 + (remaining - 1) / chunkSize);
            return new Plan(
                    range,
                    ProxyRuntimeConfig.ShardMode.SIZE,
                    count,
                    firstChunkSize,
                    chunkSize);
        }

        public HttpByteRange range() {
            return range;
        }

        public long shardCount() {
            return shardCount;
        }

        long maxShardLength() {
            if (mode == ProxyRuntimeConfig.ShardMode.COUNT) return shard(0).length();
            return Math.min(chunkSize, range.length());
        }

        public Shard shard(long index) {
            if (index < 0 || index >= shardCount) throw new IndexOutOfBoundsException("shard index: " + index);
            long relativeStart;
            long length;
            if (mode == ProxyRuntimeConfig.ShardMode.COUNT) {
                long baseSize = range.length() / shardCount;
                long remainder = range.length() % shardCount;
                relativeStart = Math.addExact(
                        Math.multiplyExact(index, baseSize),
                        Math.min(index, remainder));
                length = baseSize + (index < remainder ? 1 : 0);
            } else if (index == 0) {
                relativeStart = 0;
                length = Math.min(firstChunkSize, range.length());
            } else {
                relativeStart = Math.addExact(
                        firstChunkSize,
                        Math.multiplyExact(index - 1, chunkSize));
                length = Math.min(chunkSize, range.length() - relativeStart);
            }
            long start = Math.addExact(range.startInclusive(), relativeStart);
            long end = Math.addExact(start, length - 1);
            return new Shard(index, start, end);
        }

        @Override
        public Iterator<Shard> iterator() {
            return iterator(0);
        }

        public Iterator<Shard> iterator(long firstIndex) {
            if (firstIndex < 0 || firstIndex > shardCount) {
                throw new IndexOutOfBoundsException("first shard index: " + firstIndex);
            }
            return new Iterator<>() {

                private long nextIndex = firstIndex;

                @Override
                public boolean hasNext() {
                    return nextIndex < shardCount;
                }

                @Override
                public Shard next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return shard(nextIndex++);
                }
            };
        }
    }

    public record Shard(long index, long startInclusive, long endInclusive) {

        public Shard {
            if (index < 0) throw new IllegalArgumentException("index must not be negative");
            if (startInclusive < 0 || endInclusive < startInclusive) {
                throw new IllegalArgumentException("invalid shard range");
            }
        }

        public long length() {
            return endInclusive - startInclusive + 1;
        }

        public String rangeHeader() {
            return "bytes=" + startInclusive + "-" + endInclusive;
        }
    }
}