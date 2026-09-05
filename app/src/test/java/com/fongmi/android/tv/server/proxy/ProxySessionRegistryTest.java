package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ProxySessionRegistryTest {

    @Test
    public void enforcesCapacityAndReleasesSlotExactlyOnce() {
        ProxySessionRegistry registry = new ProxySessionRegistry(2);
        AtomicInteger cancellations = new AtomicInteger();
        try {
            ProxySessionRegistry.Lease first = registry.tryAcquire(cancellations::incrementAndGet).orElseThrow();
            ProxySessionRegistry.Lease second = registry.tryAcquire(cancellations::incrementAndGet).orElseThrow();

            assertNotEquals(first.id(), second.id());
            assertEquals(2, registry.activeCount());
            assertTrue(registry.tryAcquire(cancellations::incrementAndGet).isEmpty());

            first.close();
            first.close();
            assertEquals(1, cancellations.get());
            assertEquals(1, registry.activeCount());

            Optional<ProxySessionRegistry.Lease> replacement =
                    registry.tryAcquire(cancellations::incrementAndGet);
            assertTrue(replacement.isPresent());
            replacement.orElseThrow().close();
            second.close();
            assertEquals(3, cancellations.get());
            assertEquals(0, registry.activeCount());
        } finally {
            registry.close();
        }
    }

    @Test
    public void closingRegistryCancelsAllActiveSessionsAndRejectsNewOnes() {
        ProxySessionRegistry registry = new ProxySessionRegistry(3);
        AtomicInteger cancellations = new AtomicInteger();
        registry.tryAcquire(cancellations::incrementAndGet).orElseThrow();
        registry.tryAcquire(cancellations::incrementAndGet).orElseThrow();

        registry.close();
        registry.close();

        assertEquals(2, cancellations.get());
        assertEquals(0, registry.activeCount());
        assertFalse(registry.tryAcquire(cancellations::incrementAndGet).isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveCapacity() {
        new ProxySessionRegistry(0);
    }
}