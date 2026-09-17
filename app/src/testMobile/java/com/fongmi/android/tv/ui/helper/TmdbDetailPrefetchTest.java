package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TmdbDetailPrefetchTest {

    @Test
    public void start_reusesSameTmdbIdentity() throws Exception {
        TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.newDirectExecutorService());
        TmdbItem item = item(100, "tv");
        AtomicInteger calls = new AtomicInteger();

        TmdbDetailPrefetch.StartResult first = prefetch.start(item, () -> result(item, calls.incrementAndGet()));
        TmdbDetailPrefetch.StartResult second = prefetch.start(item(100, "tv"), () -> result(item, calls.incrementAndGet()));

        assertEquals(TmdbDetailPrefetch.StartState.STARTED, first.getState());
        assertEquals(TmdbDetailPrefetch.StartState.REUSED, second.getState());
        assertSame(first.getFuture(), second.getFuture());
        assertEquals(1, calls.get());
        assertEquals(1, second.getFuture().get().getDetail().get("call").getAsInt());
    }

    @Test
    public void start_normalizesMediaTypeIdentity() {
        TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.newDirectExecutorService());
        TmdbItem item = item(100, "TV");

        TmdbDetailPrefetch.StartResult first = prefetch.start(item, () -> result(item, 1));
        TmdbDetailPrefetch.StartResult second = prefetch.start(item(100, " tv "), () -> result(item, 2));

        assertEquals(TmdbDetailPrefetch.StartState.REUSED, second.getState());
        assertSame(first.getFuture(), second.getFuture());
    }

    @Test
    public void take_consumesMatchingResultOnce() {
        TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.newDirectExecutorService());
        TmdbItem item = item(101, "movie");
        prefetch.start(item, () -> result(item, 1));

        assertNotNull(prefetch.take(item(101, "movie")));
        assertNull(prefetch.take(item(101, "movie")));
    }

    @Test
    public void take_doesNotConsumeDifferentMediaOrId() {
        TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.newDirectExecutorService());
        TmdbItem item = item(102, "tv");
        prefetch.start(item, () -> result(item, 1));

        assertNull(prefetch.take(item(102, "movie")));
        assertNull(prefetch.take(item(999, "tv")));
        assertNotNull(prefetch.take(item(102, "tv")));
    }

    @Test
    public void start_cancelsPreviousIdentityAndReportsReplacement() throws Exception {
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.listeningDecorator(worker));
            java.util.concurrent.CountDownLatch blocker = new java.util.concurrent.CountDownLatch(1);
            TmdbItem oldItem = item(103, "tv");
            TmdbDetailPrefetch.StartResult old = prefetch.start(oldItem, () -> {
                blocker.await();
                return result(oldItem, 1);
            });

            TmdbItem replacement = item(104, "tv");
            TmdbDetailPrefetch.StartResult next = prefetch.start(replacement, () -> result(replacement, 2));
            blocker.countDown();

            assertEquals(TmdbDetailPrefetch.StartState.REPLACED, next.getState());
            assertTrue(old.getFuture().isCancelled());
            assertEquals(2, next.getFuture().get().getDetail().get("call").getAsInt());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void takenFutureRemainsCancellableByConsumer() throws Exception {
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            TmdbDetailPrefetch prefetch = new TmdbDetailPrefetch(MoreExecutors.listeningDecorator(worker));
            java.util.concurrent.CountDownLatch blocker = new java.util.concurrent.CountDownLatch(1);
            TmdbItem item = item(105, "movie");
            prefetch.start(item, () -> {
                blocker.await();
                return result(item, 1);
            });

            ListenableFuture<TmdbDetailPrefetch.Result> taken = prefetch.take(item);
            assertNotNull(taken);
            assertTrue(taken.cancel(true));
            assertTrue(taken.isCancelled());
            blocker.countDown();
        } finally {
            worker.shutdownNow();
        }
    }

    private static TmdbItem item(int id, String mediaType) {
        return new TmdbItem(id, mediaType, "Title", "", "", "", "");
    }

    private static TmdbDetailPrefetch.Result result(TmdbItem item, int call) {
        JsonObject detail = new JsonObject();
        detail.addProperty("call", call);
        return new TmdbDetailPrefetch.Result(item, detail, Collections.emptyList());
    }
}
