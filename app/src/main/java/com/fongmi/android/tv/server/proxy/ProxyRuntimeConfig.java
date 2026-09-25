package com.fongmi.android.tv.server.proxy;

public record ProxyRuntimeConfig(
        int schemaVersion,
        boolean enabled,
        PortMode portMode,
        int configuredPort,
        int serverWorkers,
        int connectionQueueCapacity,
        int rangeWorkers,
        int rangeConcurrency,
        ShardMode shardMode,
        int shardCount,
        long chunkSizeBytes,
        int maxSessions,
        int reorderWindowBlocks,
        long bufferBudgetBytes,
        int retryCount,
        long connectTimeoutMillis,
        long readTimeoutMillis,
        long fileThresholdBytes) {

    public static final int SCHEMA_VERSION = 1;
    public static final boolean DEFAULT_ENABLED = false;
    public static final int AUTO_PORT = 0;
    public static final int DEFAULT_PORT = 17575;
    public static final int DEFAULT_SERVER_WORKERS = 8;
    public static final int DEFAULT_CONNECTION_QUEUE_CAPACITY = 64;
    public static final int DEFAULT_RANGE_WORKERS = 10;
    public static final int DEFAULT_RANGE_CONCURRENCY = 10;
    public static final int AUTO_SHARD_COUNT = 0;
    public static final int DEFAULT_SHARD_COUNT = 256;
    public static final long DEFAULT_CHUNK_SIZE_BYTES = 1024L * 1024L;
    public static final int DEFAULT_MAX_SESSIONS = 2;
    public static final int AUTO_REORDER_WINDOW_BLOCKS = 0;
    public static final long DEFAULT_BUFFER_BUDGET_BYTES = 20L * 1024L * 1024L;
    public static final int DEFAULT_RETRY_COUNT = 2;
    public static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L;
    public static final long DEFAULT_READ_TIMEOUT_MILLIS = 30_000L;
    public static final long DEFAULT_FILE_THRESHOLD_BYTES = 32L * 1024L * 1024L;

    public static ProxyRuntimeConfig defaults() {
        return new ProxyRuntimeConfig(
                SCHEMA_VERSION,
                DEFAULT_ENABLED,
                PortMode.FIXED,
                DEFAULT_PORT,
                DEFAULT_SERVER_WORKERS,
                DEFAULT_CONNECTION_QUEUE_CAPACITY,
                DEFAULT_RANGE_WORKERS,
                DEFAULT_RANGE_CONCURRENCY,
                ShardMode.COUNT,
                DEFAULT_SHARD_COUNT,
                DEFAULT_CHUNK_SIZE_BYTES,
                DEFAULT_MAX_SESSIONS,
                AUTO_REORDER_WINDOW_BLOCKS,
                DEFAULT_BUFFER_BUDGET_BYTES,
                DEFAULT_RETRY_COUNT,
                DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DEFAULT_READ_TIMEOUT_MILLIS,
                DEFAULT_FILE_THRESHOLD_BYTES);
    }

    public int nominalReorderWindowBlocks() {
        if (reorderWindowBlocks > 0) return reorderWindowBlocks;
        if (rangeConcurrency <= 0) return 0;
        return rangeConcurrency > Integer.MAX_VALUE / 2
                ? Integer.MAX_VALUE
                : rangeConcurrency * 2;
    }

    public int effectiveReorderWindowBlocks(long availableBufferBytes, int remainingShards) {
        if (availableBufferBytes <= 0 || remainingShards <= 0 || chunkSizeBytes <= 0) return 0;
        long budgetBlocks = availableBufferBytes / chunkSizeBytes;
        long effective = Math.min((long) nominalReorderWindowBlocks(), remainingShards);
        effective = Math.min(effective, budgetBlocks);
        return effective <= 0 ? 0 : (int) Math.min(effective, Integer.MAX_VALUE);
    }

    public enum PortMode {
        FIXED("fixed"),
        AUTO("auto");

        private final String value;

        PortMode(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static PortMode from(String value, PortMode fallback) {
            if (value == null) return fallback;
            for (PortMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) return mode;
            }
            return fallback;
        }
    }

    public enum ShardMode {
        AUTO("auto"),
        COUNT("count"),
        SIZE("size");

        private final String value;

        ShardMode(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static ShardMode from(String value, ShardMode fallback) {
            if (value == null) return fallback;
            for (ShardMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) return mode;
            }
            return fallback;
        }
    }
}