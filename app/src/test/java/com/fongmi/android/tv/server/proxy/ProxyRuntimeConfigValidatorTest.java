package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProxyRuntimeConfigValidatorTest {

    @Test
    public void defaultsAreValidWithoutWarnings() {
        ProxyRuntimeConfigValidator.Result result = ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfig.defaults());

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void fixedPortUsesProtocolBoundaryAndAutoRequiresZero() {
        assertTrue(validate(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 1024).isValid());
        assertTrue(validate(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 65535).isValid());
        assertFalse(validate(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 1023).isValid());
        assertFalse(validate(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 65536).isValid());

        Map<String, Object> auto = values();
        auto.put(ProxyRuntimeConfigCodec.KEY_PORT_MODE, "auto");
        auto.put(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 0);
        assertTrue(ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfigCodec.decode(auto)).isValid());

        auto.put(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, 17575);
        assertTrue(ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfigCodec.decode(auto))
                .hasError(ProxyRuntimeConfigValidator.Issue.AUTO_PORT_MUST_BE_ZERO));
    }

    @Test
    public void resourceCountsRejectOnlyNonPositiveHardInvalidValues() {
        String[] positiveKeys = {
                ProxyRuntimeConfigCodec.KEY_SERVER_WORKERS,
                ProxyRuntimeConfigCodec.KEY_CONNECTION_QUEUE_CAPACITY,
                ProxyRuntimeConfigCodec.KEY_RANGE_WORKERS,
                ProxyRuntimeConfigCodec.KEY_RANGE_CONCURRENCY,
                ProxyRuntimeConfigCodec.KEY_MAX_SESSIONS,
                ProxyRuntimeConfigCodec.KEY_CHUNK_SIZE_BYTES,
                ProxyRuntimeConfigCodec.KEY_BUFFER_BUDGET_BYTES,
                ProxyRuntimeConfigCodec.KEY_CONNECT_TIMEOUT_MILLIS,
                ProxyRuntimeConfigCodec.KEY_READ_TIMEOUT_MILLIS
        };

        for (String key : positiveKeys) {
            assertFalse(key, validate(key, 0).isValid());
        }
    }

    @Test
    public void shardCountIsRequiredOnlyForCountMode() {
        Map<String, Object> countMode = values();
        countMode.put(ProxyRuntimeConfigCodec.KEY_SHARD_MODE, "count");
        countMode.put(ProxyRuntimeConfigCodec.KEY_SHARD_COUNT, 0);

        ProxyRuntimeConfigValidator.Result invalid =
                ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfigCodec.decode(countMode));
        assertTrue(invalid.hasError(ProxyRuntimeConfigValidator.Issue.SHARD_COUNT_NOT_POSITIVE));

        countMode.put(ProxyRuntimeConfigCodec.KEY_SHARD_COUNT, 1);
        assertTrue(ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfigCodec.decode(countMode)).isValid());
    }

    @Test
    public void dangerousResourceValuesAreRejectedAtAbsoluteSafetyLimits() {
        assertLimit(ProxyRuntimeConfigCodec.KEY_SERVER_WORKERS,
                ProxyRuntimeConfigValidator.MAX_SERVER_WORKERS,
                ProxyRuntimeConfigValidator.Issue.SERVER_WORKERS_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_CONNECTION_QUEUE_CAPACITY,
                ProxyRuntimeConfigValidator.MAX_CONNECTION_QUEUE_CAPACITY,
                ProxyRuntimeConfigValidator.Issue.CONNECTION_QUEUE_CAPACITY_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_RANGE_WORKERS,
                ProxyRuntimeConfigValidator.MAX_RANGE_WORKERS,
                ProxyRuntimeConfigValidator.Issue.RANGE_WORKERS_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_RANGE_CONCURRENCY,
                ProxyRuntimeConfigValidator.MAX_RANGE_CONCURRENCY,
                ProxyRuntimeConfigValidator.Issue.RANGE_CONCURRENCY_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_SHARD_COUNT,
                ProxyRuntimeConfigValidator.MAX_SHARD_COUNT,
                ProxyRuntimeConfigValidator.Issue.SHARD_COUNT_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_CHUNK_SIZE_BYTES,
                ProxyRuntimeConfigValidator.MAX_CHUNK_SIZE_BYTES,
                ProxyRuntimeConfigValidator.Issue.CHUNK_SIZE_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_MAX_SESSIONS,
                ProxyRuntimeConfigValidator.MAX_SESSIONS,
                ProxyRuntimeConfigValidator.Issue.MAX_SESSIONS_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_REORDER_WINDOW_BLOCKS,
                ProxyRuntimeConfigValidator.MAX_REORDER_WINDOW_BLOCKS,
                ProxyRuntimeConfigValidator.Issue.REORDER_WINDOW_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_BUFFER_BUDGET_BYTES,
                ProxyRuntimeConfigValidator.MAX_BUFFER_BUDGET_BYTES,
                ProxyRuntimeConfigValidator.Issue.BUFFER_BUDGET_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_RETRY_COUNT,
                ProxyRuntimeConfigValidator.MAX_RETRY_COUNT,
                ProxyRuntimeConfigValidator.Issue.RETRY_COUNT_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_CONNECT_TIMEOUT_MILLIS,
                ProxyRuntimeConfigValidator.MAX_CONNECT_TIMEOUT_MILLIS,
                ProxyRuntimeConfigValidator.Issue.CONNECT_TIMEOUT_TOO_LARGE);
        assertLimit(ProxyRuntimeConfigCodec.KEY_READ_TIMEOUT_MILLIS,
                ProxyRuntimeConfigValidator.MAX_READ_TIMEOUT_MILLIS,
                ProxyRuntimeConfigValidator.Issue.READ_TIMEOUT_TOO_LARGE);
    }

    private static void assertLimit(
            String key,
            int maximum,
            ProxyRuntimeConfigValidator.Issue tooLargeIssue) {
        assertTrue(key, validate(key, maximum).isValid());
        assertTrue(key, validate(key, maximum + 1).hasError(tooLargeIssue));
    }

    private static void assertLimit(
            String key,
            long maximum,
            ProxyRuntimeConfigValidator.Issue tooLargeIssue) {
        assertTrue(key, validate(key, maximum).isValid());
        assertTrue(key, validate(key, maximum + 1L).hasError(tooLargeIssue));
    }

    private static ProxyRuntimeConfigValidator.Result validate(String key, Object value) {
        Map<String, Object> values = values();
        values.put(key, value);
        return ProxyRuntimeConfigValidator.validate(ProxyRuntimeConfigCodec.decode(values));
    }

    private static Map<String, Object> values() {
        return new HashMap<>(ProxyRuntimeConfigCodec.encode(ProxyRuntimeConfig.defaults()));
    }
}