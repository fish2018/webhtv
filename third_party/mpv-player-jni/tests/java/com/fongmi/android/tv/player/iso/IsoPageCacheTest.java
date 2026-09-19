package com.fongmi.android.tv.player.iso;

import androidx.media3.mpvplayer.IsoCacheTestSupport;
import androidx.media3.mpvplayer.MpvHlsCacheCoordinator;

import org.junit.After;
import org.junit.Test;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class IsoPageCacheTest {
    private final List<IsoPageCache> caches = new ArrayList<>();
    private final List<IsoDiskPageStore> stores = new ArrayList<>();
    private final List<File> directories = new ArrayList<>();
    private final ExecutorService readers = Executors.newFixedThreadPool(3);

    @After
    public void tearDown() {
        for (IsoPageCache cache : caches) cache.close();
        for (IsoDiskPageStore store : stores) store.close();
        readers.shutdownNow();
        for (File directory : directories) {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    @Test
    public void thirtyRealSizePagesLoopWithoutNetworkAfterWarmupAndKeepEightInMemory() throws Exception {
        int pageSize = IsoPageCache.DEFAULT_PAGE_SIZE;
        FakeSource source = new FakeSource(pageSize, 40);
        IsoDiskPageStore disk = disk(128L << 20, true);
        IsoPageCache cache = cache(source, pageSize, 8, disk);
        for (int loop = 0; loop < 4; loop++) {
            for (int page = 0; page < 30; page++) {
                byte[] result = new byte[1];
                assertEquals(1, cache.readAt((long) page * pageSize + 100, result, 0, 1));
                assertEquals((byte) page, result[0]);
            }
        }
        for (int page = 0; page < 30; page++) assertEquals("page " + page, 1, source.count(page));
        Field pages = IsoPageCache.class.getDeclaredField("pages");
        pages.setAccessible(true);
        assertEquals(8, ((Map<?, ?>) pages.get(cache)).size());
    }

    @Test
    public void sequentialPartialReadsStartNextPageBeforeCurrentPageEnds() throws Exception {
        CountDownLatch nextStarted = new CountDownLatch(1);
        FakeSource source = new FakeSource(1024, 8);
        source.hook = page -> { if (page == 1) nextStarted.countDown(); };
        IsoPageCache cache = cache(source, 1024, 8, null);
        cache.readAt(0, new byte[128], 0, 128);
        cache.readAt(128, new byte[128], 0, 128);
        assertTrue(nextStarted.await(3, TimeUnit.SECONDS));
        assertEquals(1, source.count(1));
    }

    @Test
    public void seekDiscardsQueuedHintsAndDoesNotWaitForOldPrefetch() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch latest = new CountDownLatch(1);
        FakeSource source = new FakeSource(1024, 30);
        source.hook = page -> {
            if (page == 1) { started.countDown(); await(release); }
            if (page == 21) latest.countDown();
        };
        IsoPageCache cache = cache(source, 1024, 8, null);
        cache.readAt(0, new byte[1024], 0, 1024);
        assertTrue(started.await(3, TimeUnit.SECONDS));
        Future<Integer> seek = readers.submit(() -> cache.readAt(10 * 1024L, new byte[1024], 0, 1024));
        assertEquals(1024, (int) seek.get(3, TimeUnit.SECONDS));
        cache.readAt(20 * 1024L, new byte[1024], 0, 1024);
        release.countDown();
        assertTrue(latest.await(3, TimeUnit.SECONDS));
        assertEquals(0, source.count(11));
    }

    @Test
    public void concurrentSamePageSharesOneSourceRead() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeSource source = new FakeSource(1024, 4);
        source.hook = page -> { started.countDown(); await(release); };
        IsoPageCache cache = cache(source, 1024, 8, null);
        Future<Integer> first = readers.submit(() -> cache.readAt(0, new byte[1], 0, 1));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        Future<Integer> second = readers.submit(() -> cache.readAt(0, new byte[1], 0, 1));
        release.countDown();
        assertEquals(1, (int) first.get(3, TimeUnit.SECONDS));
        assertEquals(1, (int) second.get(3, TimeUnit.SECONDS));
        assertEquals(1, source.count(0));
    }

    @Test
    public void navigationPrefetchHasTwoWorkersAndFourPageHorizon() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch horizon = new CountDownLatch(4);
        FakeSource source = new FakeSource(1024, 30);
        source.hook = page -> {
            if (page == 1 || page == 2) { started.countDown(); await(release); }
            if (page >= 1 && page <= 4) horizon.countDown();
        };
        IsoPageCache cache = cache(source, 1024, 8, disk(32 * 1024, true));
        cache.readAt(0, new byte[1024], 0, 1024);
        assertTrue("both Range reads must start without waiting for the other", started.await(3, TimeUnit.SECONDS));
        assertEquals(0, source.count(3));
        release.countDown();
        assertTrue(horizon.await(3, TimeUnit.SECONDS));
        cache.close();
        assertEquals(0, source.count(5));
        for (int page = 0; page <= 4; page++) assertEquals(1, source.count(page));
    }

    @Test
    public void navigationSeekReplacesBothWorkersQueuedHorizon() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch latest = new CountDownLatch(2);
        FakeSource source = new FakeSource(1024, 40);
        source.hook = page -> {
            if (page == 1 || page == 2) { started.countDown(); await(release); }
            if (page == 21 || page == 22) latest.countDown();
        };
        IsoPageCache cache = cache(source, 1024, 8, disk(64 * 1024, true));
        cache.readAt(0, new byte[1024], 0, 1024);
        assertTrue(started.await(3, TimeUnit.SECONDS));
        cache.readAt(20 * 1024L, new byte[1024], 0, 1024);
        release.countDown();
        assertTrue(latest.await(3, TimeUnit.SECONDS));
        cache.close();
        assertEquals(0, source.count(3));
        assertEquals(0, source.count(4));
    }

    @Test
    public void closeUnblocksOwnersAndWaitersAndRejectsCacheHits() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeSource source = new FakeSource(1024, 4);
        source.hook = page -> { started.countDown(); await(release); };
        source.onClose = release::countDown;
        IsoPageCache cache = cache(source, 1024, 8, null);
        Future<?> first = readers.submit(() -> assertThrows(IOException.class, () -> cache.readAt(0, new byte[1], 0, 1)));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        Future<?> second = readers.submit(() -> assertThrows(IOException.class, () -> cache.readAt(0, new byte[1], 0, 1)));
        cache.close();
        first.get(3, TimeUnit.SECONDS);
        second.get(3, TimeUnit.SECONDS);
        assertThrows(IOException.class, () -> cache.readAt(0, new byte[1], 0, 1));
    }

    @Test
    public void shortReadsAreCompletedAndTruncatedPagesAreNeverCached() throws Exception {
        FakeSource source = new FakeSource(1024, 4);
        source.readLimit = 100;
        IsoPageCache cache = cache(source, 1024, 8, null);
        assertEquals(1, cache.readAt(7, new byte[1], 0, 1));
        assertEquals(11, source.count(0));
        source.readLimit = 0;
        assertThrows(EOFException.class, () -> cache.readAt(1024, new byte[1], 0, 1));
        source.readLimit = 1024;
        assertEquals(1, cache.readAt(1024, new byte[1], 0, 1));
        assertEquals(2, source.count(1));
    }

    @Test
    public void changedSourceInvalidatesMemoryAndDisk() throws Exception {
        FakeSource source = new FakeSource(1024, 4);
        IsoDiskPageStore disk = disk(16 * 1024, true);
        IsoPageCache cache = cache(source, 1024, 8, disk);
        cache.readAt(0, new byte[1], 0, 1);
        source.hook = page -> { throw new IsoSourceException(IsoSourceException.Reason.SOURCE_CHANGED, "changed"); };
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED,
                assertThrows(IsoSourceException.class, () -> cache.readAt(1024, new byte[1], 0, 1)).reason());
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED,
                assertThrows(IsoSourceException.class, () -> cache.readAt(0, new byte[1], 0, 1)).reason());
        assertNull(disk.read(0, 1024));
    }

    @Test
    public void diskBudgetIsSharedAndCloseDoesNotDeleteOtherSessions() throws Exception {
        File directory = directory();
        long budget = 3 * (4096 + 256);
        MpvHlsCacheCoordinator coordinator = IsoCacheTestSupport.coordinator(directory, true);
        IsoDiskPageStore first = new IsoDiskPageStore(directory, budget, coordinator);
        IsoDiskPageStore second = new IsoDiskPageStore(directory, budget, coordinator);
        stores.add(first);
        stores.add(second);
        for (int page = 0; page < 6; page++) first.write(page, new byte[4096], false);
        byte[] different = new byte[4096];
        Arrays.fill(different, (byte) 9);
        second.write(5, different, false);
        assertTrue(coordinator.cacheBytes() <= budget);
        assertArrayEquals(different, second.read(5, 4096));
        first.close();
        assertArrayEquals(different, second.read(5, 4096));
        second.close();
        assertEquals(0, coordinator.cacheBytes());
    }

    @Test
    public void missingOrTruncatedDiskPageFallsBackToSource() throws Exception {
        FakeSource source = new FakeSource(1024, 8);
        IsoDiskPageStore disk = disk(32 * 1024, true);
        IsoPageCache cache = cache(source, 1024, 1, disk);
        cache.readAt(0, new byte[1], 0, 1);
        cache.readAt(1024, new byte[1], 0, 1);
        File page = Arrays.stream(directories.get(0).listFiles())
                .filter(file -> file.getName().endsWith("-0.bin")).findFirst().orElseThrow();
        Files.write(page.toPath(), new byte[3]);
        cache.readAt(0, new byte[1], 0, 1);
        assertEquals(2, source.count(0));
        cache.readAt(1024, new byte[1], 0, 1);
        assertTrue(page.delete());
        cache.readAt(0, new byte[1], 0, 1);
        assertEquals(3, source.count(0));
    }

    @Test
    public void unavailableStorageFallsBackToBoundedMemoryAndNetwork() throws Exception {
        FakeSource source = new FakeSource(1024, 8);
        IsoPageCache cache = cache(source, 1024, 1, disk(32 * 1024, false));
        cache.readAt(0, new byte[1], 0, 1);
        cache.readAt(1024, new byte[1], 0, 1);
        cache.readAt(0, new byte[1], 0, 1);
        assertEquals(2, source.count(0));
        assertEquals(0, directories.get(0).listFiles().length);
    }

    @Test
    public void eofAndInvalidBoundsDoNotIssueRequests() throws Exception {
        FakeSource source = new FakeSource(1024, 2);
        IsoPageCache cache = cache(source, 1024, 8, null);
        assertEquals(0, cache.readAt(2048, new byte[1], 0, 1));
        assertEquals(0, cache.readAt(0, new byte[1], 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> cache.readAt(-1, new byte[1], 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> cache.readAt(0, new byte[1], Integer.MAX_VALUE, 1));
        assertTrue(source.reads.isEmpty());
    }

    private File directory() throws IOException {
        File directory = Files.createTempDirectory("p9-iso-cache-").toFile();
        directories.add(directory);
        return directory;
    }

    private IsoDiskPageStore disk(long capacity, boolean available) throws IOException {
        File directory = directory();
        IsoDiskPageStore store = new IsoDiskPageStore(directory, capacity, IsoCacheTestSupport.coordinator(directory, available));
        stores.add(store);
        return store;
    }

    private IsoPageCache cache(FakeSource source, int pageSize, int maxPages, IsoDiskPageStore disk) {
        IsoPageCache cache = new IsoPageCache(source, pageSize, maxPages, disk);
        caches.add(cache);
        return cache;
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Test read not released");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(error);
        }
    }

    private interface Hook { void read(long page) throws IOException; }

    private static final class FakeSource implements RemoteIsoSource {
        final int pageSize;
        final long total;
        final Map<Long, AtomicInteger> reads = new ConcurrentHashMap<>();
        volatile Hook hook = page -> {};
        volatile Runnable onClose = () -> {};
        volatile int readLimit = Integer.MAX_VALUE;
        volatile boolean closed;

        FakeSource(int pageSize, int pages) { this.pageSize = pageSize; total = (long) pageSize * pages; }
        int count(long page) { return reads.getOrDefault(page, new AtomicInteger()).get(); }
        public long length() { return total; }
        public String validator() { return "test"; }
        public void close() { closed = true; onClose.run(); }
        public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
            long page = offset / pageSize;
            reads.computeIfAbsent(page, ignored -> new AtomicInteger()).incrementAndGet();
            hook.read(page);
            if (closed) throw new IOException("closed");
            int count = (int) Math.min(Math.min(length, readLimit), total - offset);
            Arrays.fill(buffer, bufferOffset, bufferOffset + count, (byte) page);
            return count;
        }
    }
}
