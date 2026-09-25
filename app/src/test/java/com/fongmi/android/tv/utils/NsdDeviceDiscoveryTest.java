package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NsdDeviceDiscoveryTest {

    @Test
    public void capturesLocalNetworkSecurityExceptionWithoutThrowing() {
        SecurityException error = new SecurityException("Missing local network permission");

        RuntimeException captured = NsdDeviceDiscovery.captureFailure(() -> {
            throw error;
        });

        assertSame(error, captured);
    }

    @Test
    public void successfulOperationHasNoFailure() {
        assertNull(NsdDeviceDiscovery.captureFailure(() -> {
        }));
    }
}
