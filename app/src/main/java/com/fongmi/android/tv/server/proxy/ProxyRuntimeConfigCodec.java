package com.fongmi.android.tv.server.proxy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ProxyRuntimeConfigCodec {

    private static final String PREFIX = "multi_thread_proxy_";

    public static final String KEY_SCHEMA_VERSION = PREFIX + "schema_version";
    public static final String KEY_ENABLED = PREFIX + "enabled";
    public static final String KEY_PORT_MODE = PREFIX + "port_mode";
    public static final String KEY_CONFIGURED_PORT = PREFIX + "port";
    public static final String KEY_SERVER_WORKERS = PREFIX + "server_workers";
    public static final String KEY_CONNECTION_QUEUE_CAPACITY = PREFIX + "connection_queue_capacity";
    public static final String KEY_RANGE_WORKERS = PREFIX + "range_workers";
    public static final String KEY_RANGE_CONCURRENCY = PREFIX + "range_concurrency";
    public static final String KEY_SHARD_MODE = PREFIX + "shard_mode";
    public static final String KEY_SHARD_COUNT = PREFIX + "shard_count";
    public static final String KEY_CHUNK_SIZE_BYTES = PREFIX + "chunk_size_bytes";
    public static final String KEY_MAX_SESSIONS = PREFIX + "max_sessions";
    public static final String KEY_REORDER_WINDOW_BLOCKS = PREFIX + "reorder_window_blocks";
    public static final String KEY_BUFFER_BUDGET_BYTES = PREFIX + "buffer_budget_bytes";
    public static final String KEY_RETRY_COUNT = PREFIX + "retry_count";
    public static final String KEY_CONNECT_TIMEOUT_MILLIS = PREFIX + "connect_timeout_millis";
    public static final String KEY_READ_TIMEOUT_MILLIS = PREFIX + "read_timeout_millis";
    public static final String KEY_FILE_THRESHOLD_BYTES = PREFIX + "file_threshold_bytes";

    private static final Set<String> KEYS;

    static {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(KEY_SCHEMA_VERSION);
        keys.add(KEY_ENABLED);
        keys.add(KEY_PORT_MODE);
        keys.add(KEY_CONFIGURED_PORT);
        keys.add(KEY_SERVER_WORKERS);
        keys.add(KEY_CONNECTION_QUEUE_CAPACITY);
        keys.add(KEY_RANGE_WORKERS);
        keys.add(KEY_RANGE_CONCURRENCY);
        keys.add(KEY_SHARD_MODE);
        keys.add(KEY_SHARD_COUNT);
        keys.add(KEY_CHUNK_SIZE_BYTES);
        keys.add(KEY_MAX_SESSIONS);
        keys.add(KEY_REORDER_WINDOW_BLOCKS);
        keys.add(KEY_BUFFER_BUDGET_BYTES);
        keys.add(KEY_RETRY_COUNT);
        keys.add(KEY_CONNECT_TIMEOUT_MILLIS);
        keys.add(KEY_READ_TIMEOUT_MILLIS);
        keys.add(KEY_FILE_THRESHOLD_BYTES);
        KEYS = Collections.unmodifiableSet(keys);
    }

    private ProxyRuntimeConfigCodec() {
    }

    public static Set<String> keys() {
        return KEYS;
    }

    public static Map<String, Object> encode(ProxyRuntimeConfig config) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_SCHEMA_VERSION, config.schemaVersion());
        values.put(KEY_ENABLED, config.enabled());
        values.put(KEY_PORT_MODE, config.portMode().value());
        values.put(KEY_CONFIGURED_PORT, config.configuredPort());
        values.put(KEY_SERVER_WORKERS, config.serverWorkers());
        values.put(KEY_CONNECTION_QUEUE_CAPACITY, config.connectionQueueCapacity());
        values.put(KEY_RANGE_WORKERS, config.rangeWorkers());
        values.put(KEY_RANGE_CONCURRENCY, config.rangeConcurrency());
        values.put(KEY_SHARD_MODE, config.shardMode().value());
        values.put(KEY_SHARD_COUNT, config.shardCount());
        values.put(KEY_CHUNK_SIZE_BYTES, config.chunkSizeBytes());
        values.put(KEY_MAX_SESSIONS, config.maxSessions());
        values.put(KEY_REORDER_WINDOW_BLOCKS, config.reorderWindowBlocks());
        values.put(KEY_BUFFER_BUDGET_BYTES, config.bufferBudgetBytes());
        values.put(KEY_RETRY_COUNT, config.retryCount());
        values.put(KEY_CONNECT_TIMEOUT_MILLIS, config.connectTimeoutMillis());
        values.put(KEY_READ_TIMEOUT_MILLIS, config.readTimeoutMillis());
        values.put(KEY_FILE_THRESHOLD_BYTES, config.fileThresholdBytes());
        return Collections.unmodifiableMap(values);
    }

    public static ProxyRuntimeConfig decode(Map<String, ?> values) {
        Map<String, ?> source = values == null ? Map.of() : values;
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        ProxyRuntimeConfig.PortMode portMode = ProxyRuntimeConfig.PortMode.from(
                stringValue(source.get(KEY_PORT_MODE)), defaults.portMode());
        ProxyRuntimeConfig.ShardMode shardMode = ProxyRuntimeConfig.ShardMode.from(
                stringValue(source.get(KEY_SHARD_MODE)), defaults.shardMode());

        return new ProxyRuntimeConfig(
                intValue(source.get(KEY_SCHEMA_VERSION), defaults.schemaVersion()),
                booleanValue(source.get(KEY_ENABLED), defaults.enabled()),
                portMode,
                intValue(source.get(KEY_CONFIGURED_PORT), defaults.configuredPort()),
                intValue(source.get(KEY_SERVER_WORKERS), defaults.serverWorkers()),
                intValue(source.get(KEY_CONNECTION_QUEUE_CAPACITY), defaults.connectionQueueCapacity()),
                intValue(source.get(KEY_RANGE_WORKERS), defaults.rangeWorkers()),
                intValue(source.get(KEY_RANGE_CONCURRENCY), defaults.rangeConcurrency()),
                shardMode,
                intValue(source.get(KEY_SHARD_COUNT), defaults.shardCount()),
                longValue(source.get(KEY_CHUNK_SIZE_BYTES), defaults.chunkSizeBytes()),
                intValue(source.get(KEY_MAX_SESSIONS), defaults.maxSessions()),
                intValue(source.get(KEY_REORDER_WINDOW_BLOCKS), defaults.reorderWindowBlocks()),
                longValue(source.get(KEY_BUFFER_BUDGET_BYTES), defaults.bufferBudgetBytes()),
                intValue(source.get(KEY_RETRY_COUNT), defaults.retryCount()),
                longValue(source.get(KEY_CONNECT_TIMEOUT_MILLIS), defaults.connectTimeoutMillis()),
                longValue(source.get(KEY_READ_TIMEOUT_MILLIS), defaults.readTimeoutMillis()),
                longValue(source.get(KEY_FILE_THRESHOLD_BYTES), defaults.fileThresholdBytes()));
    }

    private static String stringValue(Object value) {
        if (value instanceof String text) return text;
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        return null;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
        }
        return fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE ? fallback : (int) parsed;
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}