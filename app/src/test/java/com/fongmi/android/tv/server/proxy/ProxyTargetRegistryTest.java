package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ProxyTargetRegistryTest {

    @Test
    public void registrationStoresSanitizedTargetBehindOpaqueReusableId() {
        AtomicLong now = new AtomicLong(1_000);
        ProxyTargetRegistry registry = new ProxyTargetRegistry(100, now::get);
        try {
            ProxyTargetRegistry.Registration registration = registry.register(
                    "https://cdn.example.test/private/movie.mp4?auth=secret",
                    Map.of(
                            "Cookie", "sid=secret",
                            "Host", "evil.test",
                            "Range", "bytes=9-",
                            "User-Agent", "WebHtv-Test"));
            try {
                assertFalse(registration.path().contains("cdn.example.test"));
                assertFalse(registration.path().contains("auth=secret"));
                assertFalse(registration.path().contains("sid=secret"));

                ProxyTargetRegistry.Target first = registry.resolve(registration.id());
                ProxyTargetRegistry.Target second = registry.resolve(registration.id());
                assertNotNull(first);
                assertEquals(first, second);
                assertEquals("https://cdn.example.test/private/movie.mp4?auth=secret", first.url());
                assertEquals(Map.of("Cookie", "sid=secret", "User-Agent", "WebHtv-Test"), first.headers());
                assertFalse(first.toString().contains("cdn.example.test"));
                assertFalse(first.toString().contains("sid=secret"));
            } finally {
                registration.close();
            }
            assertNull(registry.resolve(registration.id()));
        } finally {
            registry.close();
        }
    }

    @Test
    public void resolveRefreshesIdleExpiryAndExpiredIdsStayRevoked() {
        AtomicLong now = new AtomicLong(10_000);
        ProxyTargetRegistry registry = new ProxyTargetRegistry(100, now::get);
        try {
            ProxyTargetRegistry.Registration registration = registry.register(
                    "https://cdn.example.test/movie.mp4",
                    Map.of());
            now.addAndGet(90);
            assertNotNull(registry.resolve(registration.id()));
            now.addAndGet(90);
            assertNotNull(registry.resolve(registration.id()));
            now.addAndGet(101);
            assertNull(registry.resolve(registration.id()));
            assertNull(registry.resolve(registration.id()));
        } finally {
            registry.close();
        }
    }

    @Test
    public void expiredTargetsArePurgedWithoutAnotherLookup() throws Exception {
        ProxyTargetRegistry registry = new ProxyTargetRegistry(20, System::currentTimeMillis);
        try {
            registry.register("https://cdn.example.test/movie.mp4", Map.of());
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (registry.size() != 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(0, registry.size());
        } finally {
            registry.close();
        }
    }

    @Test
    public void closeRevokesAllTargetsRejectsNewOnesAndIsIdempotent() {
        ProxyTargetRegistry registry = new ProxyTargetRegistry(100, System::currentTimeMillis);
        ProxyTargetRegistry.Registration registration = registry.register(
                "https://cdn.example.test/movie.mp4",
                Map.of());

        registry.close();
        registry.close();

        assertNull(registry.resolve(registration.id()));
        assertThrows(IllegalStateException.class, () -> registry.register(
                "https://cdn.example.test/other.mp4",
                Map.of()));
    }

    @Test
    public void rejectsNonHttpTargetsBeforePublishingAnId() {
        ProxyTargetRegistry registry = new ProxyTargetRegistry(100, System::currentTimeMillis);
        try {
            assertThrows(IllegalArgumentException.class, () -> registry.register(
                    "file:///private/movie.mp4",
                    Map.of()));
        } finally {
            registry.close();
        }
    }
}
