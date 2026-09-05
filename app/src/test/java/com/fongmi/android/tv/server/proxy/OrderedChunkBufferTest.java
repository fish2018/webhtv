package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OrderedChunkBufferTest {

    @Test
    public void waitsForTheNextIndexAndReleasesBytesAfterConsumption() throws Exception {
        OrderedChunkBuffer buffer = new OrderedChunkBuffer(8);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> next = executor.submit(buffer::takeNext);
            assertFalse(next.isDone());

            buffer.put(1, new byte[]{2});
            assertFalse(next.isDone());
            buffer.put(0, new byte[]{1});

            assertArrayEquals(new byte[]{1}, next.get(1, TimeUnit.SECONDS));
            assertEquals(1, buffer.bufferedBytes());
            assertArrayEquals(new byte[]{2}, buffer.takeNext());
            assertEquals(0, buffer.bufferedBytes());
        } finally {
            buffer.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void blocksAProducerWhenBudgetIsFullUntilConsumerDrainsIt() throws Exception {
        OrderedChunkBuffer buffer = new OrderedChunkBuffer(3);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            buffer.put(0, new byte[]{1, 2, 3});
            Future<?> producer = executor.submit(() -> {
                buffer.put(1, new byte[]{4, 5});
                return null;
            });

            try {
                producer.get(100, TimeUnit.MILLISECONDS);
                fail("producer should wait for buffer capacity");
            } catch (TimeoutException expected) {
                // Expected backpressure.
            }

            assertArrayEquals(new byte[]{1, 2, 3}, buffer.takeNext());
            producer.get(1, TimeUnit.SECONDS);
            assertArrayEquals(new byte[]{4, 5}, buffer.takeNext());
        } finally {
            buffer.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void closeWakesWaitersAndFailureIsPropagated() throws Exception {
        OrderedChunkBuffer buffer = new OrderedChunkBuffer(8);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> next = executor.submit(buffer::takeNext);
            buffer.close();
            assertNull(next.get(1, TimeUnit.SECONDS));

            try {
                buffer.put(0, new byte[]{1});
                fail("closed buffer should reject writes");
            } catch (IllegalStateException expected) {
                // Expected cancellation behavior.
            }

            OrderedChunkBuffer failed = new OrderedChunkBuffer(8);
            failed.fail(new IOException("upstream failed"));
            try {
                failed.takeNext();
                fail("failed buffer should reject reads");
            } catch (IOException expected) {
                assertEquals("upstream failed", expected.getCause().getMessage());
            } finally {
                failed.close();
            }
        } finally {
            buffer.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void completeDrainsBufferedChunksThenReturnsNull() throws Exception {
        OrderedChunkBuffer buffer = new OrderedChunkBuffer(8);
        buffer.put(0, new byte[]{1});
        buffer.complete();

        assertTrue(buffer.isCompleted());
        assertArrayEquals(new byte[]{1}, buffer.takeNext());
        assertNull(buffer.takeNext());
    }

    @Test
    public void rejectsOversizedChunksDuplicateIndexesAndPastIndexes() throws Exception {
        OrderedChunkBuffer buffer = new OrderedChunkBuffer(2);
        try {
            try {
                buffer.put(0, new byte[]{1, 2, 3});
                fail("chunk larger than budget should be rejected");
            } catch (IllegalArgumentException expected) {
                // Expected budget validation.
            }
            buffer.put(0, new byte[]{1});
            try {
                buffer.put(0, new byte[]{2});
                fail("duplicate index should be rejected");
            } catch (IllegalArgumentException expected) {
                // Expected ordering validation.
            }
            assertArrayEquals(new byte[]{1}, buffer.takeNext());
            try {
                buffer.put(-1, new byte[]{2});
                fail("negative index should be rejected");
            } catch (IllegalArgumentException expected) {
                // Expected index validation.
            }
        } finally {
            buffer.close();
        }
    }
}