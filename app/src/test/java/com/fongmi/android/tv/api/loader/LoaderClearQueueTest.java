package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoaderClearQueueTest {

    @Test
    public void cleanupTasksRunInSubmissionOrder() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LoaderClearQueue queue = new LoaderClearQueue(executor);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        try {
            LoaderClearQueue.Entry first = queue.submit("first", id -> {
                order.add(id);
                firstStarted.countDown();
                await(releaseFirst);
            });
            LoaderClearQueue.Entry second = queue.submit("second", order::add);

            assertEquals(1, first.id());
            assertEquals(2, second.id());
            assertEquals("first", first.reason());
            assertEquals("second", second.reason());
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            assertFalse(second.future().isDone());
            assertEquals(Arrays.asList(1), order);

            releaseFirst.countDown();
            second.future().get(5, TimeUnit.SECONDS);

            assertEquals(Arrays.asList(1, 2), order);
            assertEquals(second, queue.latest());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
