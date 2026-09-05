package com.fongmi.android.tv.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecommendationEnrichmentExecutorTest {

    @Test
    public void map_runsWithBoundedParallelismAndPreservesOrder() throws Exception {
        List<Integer> input = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int value = 1; value <= RecommendationEnrichmentExecutor.MAX_CONCURRENCY * 2; value++) {
            input.add(value);
            expected.add(value * 10);
        }
        CountDownLatch started = new CountDownLatch(RecommendationEnrichmentExecutor.MAX_CONCURRENCY);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            Future<List<Integer>> result = caller.submit(() -> RecommendationEnrichmentExecutor.map(
                    input,
                    value -> {
                        int current = active.incrementAndGet();
                        maxActive.accumulateAndGet(current, Math::max);
                        started.countDown();
                        try {
                            await(release);
                            return value * 10;
                        } finally {
                            active.decrementAndGet();
                        }
                    },
                    value -> -value));

            assertTrue("parallel workers did not start", started.await(5, TimeUnit.SECONDS));
            assertEquals(RecommendationEnrichmentExecutor.MAX_CONCURRENCY, maxActive.get());
            release.countDown();
            assertEquals(expected, result.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for release");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    public void map_usesPerItemFallbackWhenOneTaskFails() {
        List<Integer> result = RecommendationEnrichmentExecutor.map(
                List.of(1, 2, 3),
                value -> {
                    if (value == 2) throw new IllegalStateException("boom");
                    return value * 10;
                },
                value -> -value);

        assertEquals(List.of(10, -2, 30), result);
    }

    @Test
    public void mapAsync_returnsBeforeWorkersFinishAndPreservesOrder() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<List<Integer>> result = new AtomicReference<>();
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            Future<?> returned = caller.submit(() -> RecommendationEnrichmentExecutor.mapAsync(
                    List.of(1, 2),
                    value -> {
                        started.countDown();
                        await(release);
                        return value * 10;
                    },
                    value -> -value,
                    values -> {
                        result.set(values);
                        completed.countDown();
                    }));

            returned.get(1, TimeUnit.SECONDS);
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(completed.await(50, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(10, 20), result.get());
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }
}
