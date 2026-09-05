package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProxyRuntimeConfigCodecTest {

    @Test
    public void defaultsRoundTripWithoutLosingTypes() {
        ProxyRuntimeConfig defaults = ProxyRuntimeConfig.defaults();
        Map<String, Object> encoded = ProxyRuntimeConfigCodec.encode(defaults);

        assertEquals(defaults, ProxyRuntimeConfigCodec.decode(encoded));
        assertEquals(ProxyRuntimeConfigCodec.keys(), encoded.keySet());
        assertFalse(encoded.containsValue(null));
    }

    @Test
    public void missingOrUnknownValuesFallBackToDefaults() {
        Map<String, Object> values = new HashMap<>();
        values.put(ProxyRuntimeConfigCodec.KEY_PORT_MODE, "unknown");
        values.put(ProxyRuntimeConfigCodec.KEY_SHARD_MODE, "unknown");
        values.put(ProxyRuntimeConfigCodec.KEY_SERVER_WORKERS, new Object());

        assertEquals(ProxyRuntimeConfig.defaults(), ProxyRuntimeConfigCodec.decode(values));
    }

    @Test
    public void backupStyleStringsAndBroadNumbersAreDecoded() {
        Map<String, Object> values = new HashMap<>();
        values.put(ProxyRuntimeConfigCodec.KEY_ENABLED, "true");
        values.put(ProxyRuntimeConfigCodec.KEY_CONFIGURED_PORT, "23456");
        values.put(ProxyRuntimeConfigCodec.KEY_SERVER_WORKERS, String.valueOf(Integer.MAX_VALUE));
        values.put(ProxyRuntimeConfigCodec.KEY_CHUNK_SIZE_BYTES, String.valueOf(Long.MAX_VALUE));

        ProxyRuntimeConfig decoded = ProxyRuntimeConfigCodec.decode(values);

        assertTrue(decoded.enabled());
        assertEquals(23456, decoded.configuredPort());
        assertEquals(Integer.MAX_VALUE, decoded.serverWorkers());
        assertEquals(Long.MAX_VALUE, decoded.chunkSizeBytes());
    }
}