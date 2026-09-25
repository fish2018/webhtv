package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JarLoaderTest {

    @Test
    public void clearContinuesAfterSpiderDestroyFailure() throws Exception {
        JarLoader loader = new JarLoader();
        ConcurrentHashMap<String, Spider> spiders = map(loader, "spiders");
        ConcurrentHashMap<String, Object> locks = map(loader, "locks");
        AtomicInteger destroyed = new AtomicInteger();

        spiders.put("broken", new Spider() {
            @Override
            public void destroy() {
                destroyed.incrementAndGet();
                throw new NullPointerException("broken spider");
            }
        });
        spiders.put("healthy", new Spider() {
            @Override
            public void destroy() {
                destroyed.incrementAndGet();
            }
        });
        locks.put("lock", new Object());
        field("recent").set(loader, "recent");

        loader.clear();

        assertEquals(2, destroyed.get());
        assertTrue(spiders.isEmpty());
        assertTrue(locks.isEmpty());
        assertNull(field("recent").get(loader));
    }

    @Test
    public void spiderInitRetriesTransientFailureAndCachesRecovery() throws Exception {
        JarLoader loader = new JarLoader();
        AtomicInteger attempts = new AtomicInteger();
        Spider expected = new Spider() {};

        Spider result = loader.getOrCreateSpider("site", () -> attempts.incrementAndGet() == 1 ? new SpiderNull() : expected);

        assertSame(expected, result);
        assertEquals(2, attempts.get());
        assertSame(expected, map(loader, "spiders").get("site"));
    }

    @Test
    public void spiderInitPersistentFailureIsNotCached() throws Exception {
        JarLoader loader = new JarLoader();
        AtomicInteger attempts = new AtomicInteger();
        Spider expected = new Spider() {};

        Spider failed = loader.getOrCreateSpider("site", () -> {
            attempts.incrementAndGet();
            return new SpiderNull();
        });
        assertNull(map(loader, "spiders").get("site"));
        Spider recovered = loader.getOrCreateSpider("site", () -> {
            attempts.incrementAndGet();
            return expected;
        });

        assertTrue(failed instanceof SpiderNull);
        assertSame(expected, recovered);
        assertEquals(3, attempts.get());
        assertSame(expected, map(loader, "spiders").get("site"));
    }

    @Test
    public void interruptedSpiderInitDoesNotRetryOrCache() throws Exception {
        JarLoader loader = new JarLoader();
        AtomicInteger attempts = new AtomicInteger();

        Thread.currentThread().interrupt();
        try {
            Spider result = loader.getOrCreateSpider("site", () -> {
                attempts.incrementAndGet();
                return new SpiderNull();
            });

            assertTrue(result instanceof SpiderNull);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, attempts.get());
            assertNull(map(loader, "spiders").get("site"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptedSpiderLookupDoesNotUseCachedInstance() throws Exception {
        JarLoader loader = new JarLoader();
        Spider cached = new Spider() {};
        map(loader, "spiders").put("site", cached);

        Thread.currentThread().interrupt();
        try {
            Spider result = loader.getOrCreateSpider("site", SpiderNull::new);

            assertTrue(result instanceof SpiderNull);
            assertTrue(Thread.currentThread().isInterrupted());
            assertSame(cached, map(loader, "spiders").get("site"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void completedInitAfterCancellationIsCachedButNotReturned() throws Exception {
        JarLoader loader = new JarLoader();
        Spider expected = new Spider() {};

        try {
            Spider result = loader.getOrCreateSpider("site", () -> {
                Thread.currentThread().interrupt();
                return expected;
            });

            assertTrue(result instanceof SpiderNull);
            assertTrue(Thread.currentThread().isInterrupted());
            assertSame(expected, map(loader, "spiders").get("site"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void callerInterruptedWhileWaitingDoesNotUseNewlyCachedSpider() throws Exception {
        JarLoader loader = new JarLoader();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger secondAttempts = new AtomicInteger();
        AtomicReference<Spider> firstResult = new AtomicReference<>();
        AtomicReference<Spider> secondResult = new AtomicReference<>();
        Spider expected = new Spider() {};

        Thread first = new Thread(() -> firstResult.set(loader.getOrCreateSpider("site", () -> {
            firstStarted.countDown();
            try {
                releaseFirst.await();
                return expected;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SpiderNull();
            }
        })));
        Thread second = new Thread(() -> secondResult.set(loader.getOrCreateSpider("site", () -> {
            secondAttempts.incrementAndGet();
            return new SpiderNull();
        })));

        first.start();
        try {
            assertTrue(firstStarted.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));
            second.start();
            long deadline = System.currentTimeMillis() + 2000;
            while (second.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals(Thread.State.BLOCKED, second.getState());
            second.interrupt();
            releaseFirst.countDown();
            first.join(2000);
            second.join(2000);

            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertSame(expected, firstResult.get());
            assertTrue(secondResult.get() instanceof SpiderNull);
            assertEquals(0, secondAttempts.get());
            assertSame(expected, map(loader, "spiders").get("site"));
        } finally {
            releaseFirst.countDown();
            if (first.isAlive()) first.interrupt();
            if (second.isAlive()) second.interrupt();
            first.join(2000);
            second.join(2000);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ConcurrentHashMap<String, T> map(JarLoader loader, String name) throws Exception {
        return (ConcurrentHashMap<String, T>) field(name).get(loader);
    }

    private static Field field(String name) throws Exception {
        Field field = JarLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
