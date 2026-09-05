package com.fongmi.android.tv.server.proxy;

import java.util.ArrayList;
import java.util.List;

public final class ProxyRuntimeConfigValidator {

    static final int MAX_SERVER_WORKERS = 128;
    static final int MAX_CONNECTION_QUEUE_CAPACITY = 4096;
    static final int MAX_RANGE_WORKERS = 128;
    static final int MAX_RANGE_CONCURRENCY = 128;
    static final int MAX_SHARD_COUNT = 65_536;
    static final long MAX_CHUNK_SIZE_BYTES = 64L * 1024L * 1024L;
    static final int MAX_SESSIONS = 64;
    static final int MAX_REORDER_WINDOW_BLOCKS = 4096;
    static final long MAX_BUFFER_BUDGET_BYTES = 512L * 1024L * 1024L;
    static final int MAX_RETRY_COUNT = 20;
    static final long MAX_CONNECT_TIMEOUT_MILLIS = 2L * 60L * 1000L;
    static final long MAX_READ_TIMEOUT_MILLIS = 10L * 60L * 1000L;

    private static final int RECOMMENDED_SERVER_WORKERS_MIN = 4;
    private static final int RECOMMENDED_SERVER_WORKERS_MAX = 32;
    private static final int RECOMMENDED_RANGE_WORKERS_MIN = 4;
    private static final int RECOMMENDED_RANGE_WORKERS_MAX = 32;
    private static final int RECOMMENDED_RANGE_CONCURRENCY_MIN = 2;
    private static final int RECOMMENDED_RANGE_CONCURRENCY_MAX = 16;
    private static final long RECOMMENDED_CHUNK_SIZE_MIN = 256L * 1024L;
    private static final long RECOMMENDED_CHUNK_SIZE_MAX = 16L * 1024L * 1024L;

    private ProxyRuntimeConfigValidator() {
    }

    public static Result validate(ProxyRuntimeConfig config) {
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (config == null) {
            errors.add(Issue.CONFIG_REQUIRED);
            return new Result(errors, warnings);
        }

        if (config.schemaVersion() != ProxyRuntimeConfig.SCHEMA_VERSION) {
            errors.add(Issue.UNSUPPORTED_SCHEMA_VERSION);
        }
        validatePort(config, errors);
        positive(config.serverWorkers(), Issue.SERVER_WORKERS_NOT_POSITIVE, errors);
        maximum(config.serverWorkers(), MAX_SERVER_WORKERS, Issue.SERVER_WORKERS_TOO_LARGE, errors);
        positive(config.connectionQueueCapacity(), Issue.CONNECTION_QUEUE_CAPACITY_NOT_POSITIVE, errors);
        maximum(config.connectionQueueCapacity(), MAX_CONNECTION_QUEUE_CAPACITY,
                Issue.CONNECTION_QUEUE_CAPACITY_TOO_LARGE, errors);
        positive(config.rangeWorkers(), Issue.RANGE_WORKERS_NOT_POSITIVE, errors);
        maximum(config.rangeWorkers(), MAX_RANGE_WORKERS, Issue.RANGE_WORKERS_TOO_LARGE, errors);
        positive(config.rangeConcurrency(), Issue.RANGE_CONCURRENCY_NOT_POSITIVE, errors);
        maximum(config.rangeConcurrency(), MAX_RANGE_CONCURRENCY, Issue.RANGE_CONCURRENCY_TOO_LARGE, errors);
        validateShards(config, errors);
        positive(config.chunkSizeBytes(), Issue.CHUNK_SIZE_NOT_POSITIVE, errors);
        maximum(config.chunkSizeBytes(), MAX_CHUNK_SIZE_BYTES, Issue.CHUNK_SIZE_TOO_LARGE, errors);
        positive(config.maxSessions(), Issue.MAX_SESSIONS_NOT_POSITIVE, errors);
        maximum(config.maxSessions(), MAX_SESSIONS, Issue.MAX_SESSIONS_TOO_LARGE, errors);
        if (config.reorderWindowBlocks() < 0) errors.add(Issue.REORDER_WINDOW_NEGATIVE);
        maximum(config.reorderWindowBlocks(), MAX_REORDER_WINDOW_BLOCKS, Issue.REORDER_WINDOW_TOO_LARGE, errors);
        positive(config.bufferBudgetBytes(), Issue.BUFFER_BUDGET_NOT_POSITIVE, errors);
        maximum(config.bufferBudgetBytes(), MAX_BUFFER_BUDGET_BYTES, Issue.BUFFER_BUDGET_TOO_LARGE, errors);
        if (config.retryCount() < 0) errors.add(Issue.RETRY_COUNT_NEGATIVE);
        maximum(config.retryCount(), MAX_RETRY_COUNT, Issue.RETRY_COUNT_TOO_LARGE, errors);
        positive(config.connectTimeoutMillis(), Issue.CONNECT_TIMEOUT_NOT_POSITIVE, errors);
        maximum(config.connectTimeoutMillis(), MAX_CONNECT_TIMEOUT_MILLIS, Issue.CONNECT_TIMEOUT_TOO_LARGE, errors);
        positive(config.readTimeoutMillis(), Issue.READ_TIMEOUT_NOT_POSITIVE, errors);
        maximum(config.readTimeoutMillis(), MAX_READ_TIMEOUT_MILLIS, Issue.READ_TIMEOUT_TOO_LARGE, errors);
        if (config.fileThresholdBytes() < 0) errors.add(Issue.FILE_THRESHOLD_NEGATIVE);

        recommended(config.serverWorkers(), RECOMMENDED_SERVER_WORKERS_MIN, RECOMMENDED_SERVER_WORKERS_MAX,
                Issue.SERVER_WORKERS_OUTSIDE_RECOMMENDED, warnings);
        recommended(config.rangeWorkers(), RECOMMENDED_RANGE_WORKERS_MIN, RECOMMENDED_RANGE_WORKERS_MAX,
                Issue.RANGE_WORKERS_OUTSIDE_RECOMMENDED, warnings);
        recommended(config.rangeConcurrency(), RECOMMENDED_RANGE_CONCURRENCY_MIN, RECOMMENDED_RANGE_CONCURRENCY_MAX,
                Issue.RANGE_CONCURRENCY_OUTSIDE_RECOMMENDED, warnings);
        recommended(config.chunkSizeBytes(), RECOMMENDED_CHUNK_SIZE_MIN, RECOMMENDED_CHUNK_SIZE_MAX,
                Issue.CHUNK_SIZE_OUTSIDE_RECOMMENDED, warnings);
        validateBufferWarnings(config, warnings);

        return new Result(errors, warnings);
    }

    public static void requireValid(ProxyRuntimeConfig config) {
        Result result = validate(config);
        if (!result.isValid()) throw new IllegalArgumentException("Invalid multi-thread proxy config: " + result.errors());
    }

    private static void validatePort(ProxyRuntimeConfig config, List<Issue> errors) {
        if (config.portMode() == null) {
            errors.add(Issue.PORT_MODE_REQUIRED);
        } else if (config.portMode() == ProxyRuntimeConfig.PortMode.AUTO) {
            if (config.configuredPort() != ProxyRuntimeConfig.AUTO_PORT) {
                errors.add(Issue.AUTO_PORT_MUST_BE_ZERO);
            }
        } else if (config.configuredPort() < 1024 || config.configuredPort() > 65535) {
            errors.add(Issue.FIXED_PORT_OUT_OF_RANGE);
        }
    }

    private static void validateShards(ProxyRuntimeConfig config, List<Issue> errors) {
        if (config.shardMode() == null) {
            errors.add(Issue.SHARD_MODE_REQUIRED);
            return;
        }
        if (config.shardCount() < 0) {
            errors.add(Issue.SHARD_COUNT_NEGATIVE);
        } else if (config.shardMode() == ProxyRuntimeConfig.ShardMode.COUNT && config.shardCount() == 0) {
            errors.add(Issue.SHARD_COUNT_NOT_POSITIVE);
        } else if (config.shardMode() == ProxyRuntimeConfig.ShardMode.COUNT
                && config.shardCount() > MAX_SHARD_COUNT) {
            errors.add(Issue.SHARD_COUNT_TOO_LARGE);
        }
    }

    private static void validateBufferWarnings(ProxyRuntimeConfig config, List<Issue> warnings) {
        if (config.chunkSizeBytes() <= 0 || config.bufferBudgetBytes() <= 0) return;
        if (config.bufferBudgetBytes() < config.chunkSizeBytes()) {
            warnings.add(Issue.BUFFER_BUDGET_BELOW_CHUNK);
        }
        long budgetBlocks = config.bufferBudgetBytes() / config.chunkSizeBytes();
        if (budgetBlocks < config.nominalReorderWindowBlocks()) {
            warnings.add(Issue.BUFFER_BUDGET_BELOW_NOMINAL_WINDOW);
        }
    }

    private static void positive(int value, Issue issue, List<Issue> errors) {
        if (value <= 0) errors.add(issue);
    }

    private static void positive(long value, Issue issue, List<Issue> errors) {
        if (value <= 0) errors.add(issue);
    }

    private static void maximum(long value, long maximum, Issue issue, List<Issue> errors) {
        if (value > maximum) errors.add(issue);
    }

    private static void recommended(long value, long min, long max, Issue issue, List<Issue> warnings) {
        if (value > 0 && (value < min || value > max)) warnings.add(issue);
    }

    public enum Issue {
        CONFIG_REQUIRED,
        UNSUPPORTED_SCHEMA_VERSION,
        PORT_MODE_REQUIRED,
        FIXED_PORT_OUT_OF_RANGE,
        AUTO_PORT_MUST_BE_ZERO,
        SERVER_WORKERS_NOT_POSITIVE,
        SERVER_WORKERS_TOO_LARGE,
        CONNECTION_QUEUE_CAPACITY_NOT_POSITIVE,
        CONNECTION_QUEUE_CAPACITY_TOO_LARGE,
        RANGE_WORKERS_NOT_POSITIVE,
        RANGE_WORKERS_TOO_LARGE,
        RANGE_CONCURRENCY_NOT_POSITIVE,
        RANGE_CONCURRENCY_TOO_LARGE,
        SHARD_MODE_REQUIRED,
        SHARD_COUNT_NEGATIVE,
        SHARD_COUNT_NOT_POSITIVE,
        SHARD_COUNT_TOO_LARGE,
        CHUNK_SIZE_NOT_POSITIVE,
        CHUNK_SIZE_TOO_LARGE,
        MAX_SESSIONS_NOT_POSITIVE,
        MAX_SESSIONS_TOO_LARGE,
        REORDER_WINDOW_NEGATIVE,
        REORDER_WINDOW_TOO_LARGE,
        BUFFER_BUDGET_NOT_POSITIVE,
        BUFFER_BUDGET_TOO_LARGE,
        RETRY_COUNT_NEGATIVE,
        RETRY_COUNT_TOO_LARGE,
        CONNECT_TIMEOUT_NOT_POSITIVE,
        CONNECT_TIMEOUT_TOO_LARGE,
        READ_TIMEOUT_NOT_POSITIVE,
        READ_TIMEOUT_TOO_LARGE,
        FILE_THRESHOLD_NEGATIVE,
        SERVER_WORKERS_OUTSIDE_RECOMMENDED,
        RANGE_WORKERS_OUTSIDE_RECOMMENDED,
        RANGE_CONCURRENCY_OUTSIDE_RECOMMENDED,
        CHUNK_SIZE_OUTSIDE_RECOMMENDED,
        BUFFER_BUDGET_BELOW_CHUNK,
        BUFFER_BUDGET_BELOW_NOMINAL_WINDOW
    }

    public record Result(List<Issue> errors, List<Issue> warnings) {

        public Result {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasError(Issue issue) {
            return errors.contains(issue);
        }

        public boolean hasWarning(Issue issue) {
            return warnings.contains(issue);
        }
    }
}