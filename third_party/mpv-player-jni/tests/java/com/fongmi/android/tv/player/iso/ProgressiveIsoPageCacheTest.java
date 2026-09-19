package com.fongmi.android.tv.player.iso;

import androidx.media3.mpvplayer.IsoCacheTestSupport;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class ProgressiveIsoPageCacheTest {
    private final List<ProgressiveIsoPageCache> caches = new ArrayList<>();
    private final List<File> directories = new ArrayList<>();
    private final ExecutorService readers = Executors.newFixedThreadPool(5);

    @After
    public void tearDown() throws Exception {
        for (ProgressiveIsoPageCache cache : caches) cache.close();
        readers.shutdownNow();
        assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS));
        for (File directory : directories) {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    @Test
    public void smallDemandReturnsBeforeRestOfFourMiBAndOnlyCompletePageReachesDisk() throws Exception {
        int size = IsoPageCache.DEFAULT_PAGE_SIZE;
        FakeSource source = new FakeSource(size, 2);
        CountDownLatch prefix = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        source.hook = read -> {
            read.request.attach(release::countDown);
            read.publish(64 * 1024);
            prefix.countDown();
            await(release);
            read.request.checkCancelled();
            read.publish(read.length);
        };
        IsoDiskPageStore disk = disk(16L << 20, true);
        ProgressiveIsoPageCache cache = cache(source, size, 8, disk);
        byte[] first = new byte[2048];
        Future<Integer> result = readers.submit(() -> cache.readAt(0, first, 0, first.length));
        assertTrue(prefix.await(3, TimeUnit.SECONDS));
        assertEquals(first.length, (int) result.get(1, TimeUnit.SECONDS));
        assertBytes(0, first);
        assertNull("partial pages must never become disk cache hits", disk.read(0, size));
        byte[] second = new byte[1024];
        assertEquals(second.length, cache.readAt(4096, second, 0, second.length));
        assertBytes(4096, second);
        assertEquals(1, source.reads.size());
        release.countDown();
        awaitIdle(cache);
        assertNotNull(disk.read(0, size));
        assertEquals(size, source.bytes.get());
    }

    @Test
    public void coldPageTailStartsNearDemandAndDoesNotWaitForEarlierMegabytes() throws Exception {
        int size = IsoPageCache.DEFAULT_PAGE_SIZE;
        FakeSource source = new FakeSource(size, 1);
        CountDownLatch earlierStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        source.hook = read -> {
            if (read.offset == 0) {
                read.request.attach(release::countDown);
                earlierStarted.countDown();
                await(release);
                read.request.checkCancelled();
            }
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, size, 1, null);
        byte[] data = new byte[2048];
        Future<Integer> result = readers.submit(() -> cache.readAt(size - 4096L, data, 0, data.length));
        assertEquals(data.length, (int) result.get(1, TimeUnit.SECONDS));
        assertTrue(earlierStarted.await(3, TimeUnit.SECONDS));
        assertEquals(size - 64 * 1024L, source.reads.get(0).offset);
        assertBytes(size - 4096L, data);
        release.countDown();
        awaitIdle(cache);
        assertEquals("no duplicate bytes when filling the earlier gap", size, source.bytes.get());
    }

    @Test
    public void concurrentSamePageReadersShareProgressAndOneRequest() throws Exception {
        FakeSource source = new FakeSource(1024, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        source.hook = read -> {
            read.request.attach(release::countDown);
            read.publish(512);
            started.countDown();
            await(release);
            read.request.checkCancelled();
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        Future<Integer> first = readers.submit(() -> cache.readAt(0, new byte[32], 0, 32));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        Future<Integer> second = readers.submit(() -> cache.readAt(128, new byte[32], 0, 32));
        assertEquals(32, (int) first.get(1, TimeUnit.SECONDS));
        assertEquals(32, (int) second.get(1, TimeUnit.SECONDS));
        assertEquals(1, source.reads.size());
        release.countDown();
    }

    @Test
    public void demandCanConsumeAnInFlightPrefetchBeforeItsPageEnds() throws Exception {
        FakeSource source = new FakeSource(1024, 8);
        CountDownLatch prefix = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        source.hook = read -> {
            if (read.offset == 1024) {
                read.request.attach(release::countDown);
                read.publish(128);
                prefix.countDown();
                await(release);
                read.request.checkCancelled();
            }
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        cache.readAt(0, new byte[1024], 0, 1024);
        assertTrue(prefix.await(3, TimeUnit.SECONDS));
        Future<Integer> result = readers.submit(() -> cache.readAt(1024, new byte[32], 0, 32));
        assertEquals(32, (int) result.get(1, TimeUnit.SECONDS));
        assertEquals(1, source.count(1));
        assertTrue(source.peak.get() <= 3);
        release.countDown();
    }

    @Test
    public void seekCancelsObsoleteBodiesAndQueuedHorizonWithoutClosingSource() throws Exception {
        FakeSource source = new FakeSource(1024, 40);
        CountDownLatch started = new CountDownLatch(3);
        AtomicInteger cancelled = new AtomicInteger();
        source.hook = read -> {
            long page = read.offset / 1024;
            if (page >= 1 && page <= 3) {
                CountDownLatch release = new CountDownLatch(1);
                read.request.attach(() -> { cancelled.incrementAndGet(); release.countDown(); });
                started.countDown();
                await(release);
                read.request.checkCancelled();
            }
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        cache.readAt(0, new byte[1024], 0, 1024);
        assertTrue(started.await(3, TimeUnit.SECONDS));
        byte[] data = new byte[32];
        Future<Integer> seek = readers.submit(() -> cache.readAt(20 * 1024L, data, 0, data.length));
        assertEquals(data.length, (int) seek.get(2, TimeUnit.SECONDS));
        assertBytes(20 * 1024L, data);
        assertEquals(3, cancelled.get());
        assertEquals(0, source.count(4));
        assertFalse(source.closed);
        assertTrue(source.peak.get() <= 3);
    }

    @Test
    public void seekDoesNotCancelAnotherActiveDemandReader() throws Exception {
        FakeSource source = new FakeSource(1024, 20);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        source.hook = read -> {
            if (read.offset == 0) {
                read.request.attach(() -> { cancelled.incrementAndGet(); release.countDown(); });
                started.countDown();
                await(release);
                read.request.checkCancelled();
            }
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        Future<Integer> first = readers.submit(() -> cache.readAt(0, new byte[16], 0, 16));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertEquals(16, cache.readAt(10 * 1024L, new byte[16], 0, 16));
        assertEquals(0, cancelled.get());
        release.countDown();
        assertEquals(16, (int) first.get(2, TimeUnit.SECONDS));
    }

    @Test
    public void cancelledPartialPageRetainsValidatedBytesAndResumesOnlyTheGap() throws Exception {
        FakeSource source = new FakeSource(1024, 10);
        CountDownLatch prefix = new CountDownLatch(1);
        source.hook = read -> {
            if (read.offset == 0) {
                CountDownLatch release = new CountDownLatch(1);
                read.request.attach(release::countDown);
                read.publish(128);
                prefix.countDown();
                await(release);
                read.request.checkCancelled();
            }
            read.publish(read.length);
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        assertEquals(32, cache.readAt(0, new byte[32], 0, 32));
        assertTrue(prefix.await(3, TimeUnit.SECONDS));
        cache.readAt(5 * 1024L, new byte[32], 0, 32);
        source.hook = read -> read.publish(read.length);
        byte[] all = new byte[1024];
        assertEquals(1024, cache.readAt(0, all, 0, all.length));
        assertBytes(0, all);
        assertEquals(2, source.count(0));
        assertTrue(source.reads.stream().anyMatch(read -> read.offset == 128));
    }

    @Test
    public void closeUnblocksAllLoadsAndSamePageWaitersAndRejectsHits() throws Exception {
        FakeSource source = new FakeSource(1024, 40);
        CountDownLatch started = new CountDownLatch(3);
        source.hook = read -> {
            CountDownLatch release = new CountDownLatch(1);
            read.request.attach(release::countDown);
            started.countDown();
            await(release);
            read.request.checkCancelled();
        };
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, null);
        List<Future<?>> results = new ArrayList<>();
        for (int page : new int[]{0, 10, 20, 0}) {
            results.add(readers.submit(() -> assertThrows(IOException.class,
                    () -> cache.readAt(page * 1024L, new byte[32], 0, 32))));
        }
        assertTrue(started.await(3, TimeUnit.SECONDS));
        cache.close();
        for (Future<?> result : results) result.get(2, TimeUnit.SECONDS);
        assertThrows(IOException.class, () -> cache.readAt(0, new byte[1], 0, 1));
        assertTrue(source.closed);
    }

    @Test
    public void shortAndFailedReadsNeverPublishAnIncompleteDiskPage() throws Exception {
        FakeSource source = new FakeSource(1024, 1);
        source.hook = read -> { read.publish(32); throw new EOFException("truncated"); };
        IsoDiskPageStore disk = disk(16 * 1024, true);
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, disk);
        assertThrows(EOFException.class, () -> cache.readAt(0, new byte[1024], 0, 1024));
        assertNull(disk.read(0, 1024));
        source.hook = read -> read.publish(Math.min(100, read.length));
        byte[] data = new byte[1024];
        assertEquals(data.length, cache.readAt(0, data, 0, data.length));
        assertBytes(0, data);
        awaitIdle(cache);
        assertArrayEquals(data, disk.read(0, 1024));
        assertEquals(1024, source.bytes.get());
    }

    @Test
    public void sourceChangeClosesSessionAndInvalidatesMemoryAndDisk() throws Exception {
        FakeSource source = new FakeSource(1024, 4);
        IsoDiskPageStore disk = disk(16 * 1024, true);
        ProgressiveIsoPageCache cache = cache(source, 1024, 8, disk);
        cache.readAt(0, new byte[16], 0, 16);
        awaitIdle(cache);
        source.hook = read -> { throw new IsoSourceException(IsoSourceException.Reason.SOURCE_CHANGED, "changed"); };
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED,
                assertThrows(IsoSourceException.class, () -> cache.readAt(2048, new byte[1], 0, 1)).reason());
        assertEquals(IsoSourceException.Reason.SOURCE_CHANGED,
                assertThrows(IsoSourceException.class, () -> cache.readAt(0, new byte[1], 0, 1)).reason());
        assertNull(disk.read(0, 1024));
    }

    @Test
    public void thirtyRealPagesLoopFromDiskWithoutMoreNetworkAndKeepEightInMemory() throws Exception {
        int size = IsoPageCache.DEFAULT_PAGE_SIZE;
        FakeSource source = new FakeSource(size, 30);
        ProgressiveIsoPageCache cache = cache(source, size, 8, disk(128L << 20, true));
        byte[] data = new byte[size];
        for (int loop = 0; loop < 3; loop++) {
            for (int page = 0; page < 30; page++) {
                assertEquals(size, cache.readAt((long) page * size, data, 0, size));
                assertEquals(value((long) page * size), data[0]);
                assertEquals(value((long) (page + 1) * size - 1), data[size - 1]);
            }
            awaitIdle(cache);
        }
        for (int page = 0; page < 30; page++) assertEquals("page " + page, 1, source.count(page));
        assertEquals(30L * size, source.bytes.get());
        assertTrue(source.peak.get() <= 3);
        assertEquals(8, map(cache, "pages").size());
    }

    @Test
    public void missingDiskOrStorageFallsBackWithoutChangingByteContents() throws Exception {
        FakeSource source = new FakeSource(1024, 4);
        ProgressiveIsoPageCache cache = cache(source, 1024, 1, disk(16 * 1024, false));
        byte[] data = new byte[1024];
        cache.readAt(0, data, 0, data.length);
        awaitIdle(cache);
        cache.readAt(1024, data, 0, data.length);
        awaitIdle(cache);
        cache.readAt(0, data, 0, data.length);
        awaitIdle(cache);
        assertBytes(0, data);
        assertEquals(2, source.count(0));
        assertEquals(1, map(cache, "pages").size());
    }

    @Test
    public void eofBoundsAndFinalShortPageDoNotExposeUnwrittenBytes() throws Exception {
        FakeSource source = new FakeSource(1024, 2);
        source.total = 1300;
        ProgressiveIsoPageCache cache = cache(source, 1024, 1, null);
        assertEquals(0, cache.readAt(1300, new byte[1], 0, 1));
        assertEquals(0, cache.readAt(0, new byte[1], 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> cache.readAt(-1, new byte[1], 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> cache.readAt(0, new byte[1], Integer.MAX_VALUE, 1));
        assertTrue(source.reads.isEmpty());
        byte[] data = new byte[300];
        Arrays.fill(data, (byte) 77);
        assertEquals(276, cache.readAt(1024, data, 0, 300));
        assertBytes(1024, Arrays.copyOf(data, 276));
        assertEquals(77, data[276]);
    }

    private ProgressiveIsoPageCache cache(FakeSource source, int size, int count, IsoDiskPageStore disk) {
        ProgressiveIsoPageCache cache = new ProgressiveIsoPageCache(source, size, count, disk);
        caches.add(cache);
        return cache;
    }

    private IsoDiskPageStore disk(long capacity, boolean available) throws IOException {
        File directory = Files.createTempDirectory("p9-progressive-test-").toFile();
        directories.add(directory);
        return new IsoDiskPageStore(directory, capacity, IsoCacheTestSupport.coordinator(directory, available));
    }

    private static Map<?, ?> map(ProgressiveIsoPageCache cache, String name) throws Exception {
        Field field = ProgressiveIsoPageCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(cache);
    }

    private static void awaitIdle(ProgressiveIsoPageCache cache) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        synchronized (cache) {
            while (!map(cache, "loads").isEmpty()) {
                long left = deadline - System.nanoTime();
                assertTrue("cache load did not finish", left > 0);
                TimeUnit.NANOSECONDS.timedWait(cache, left);
            }
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("test source timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static byte value(long offset) { return (byte) (offset * 31 + offset / 1024); }

    private static void assertBytes(long offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) assertEquals("byte " + i, value(offset + i), bytes[i]);
    }

    private interface Hook { void read(Read read) throws IOException; }

    private static final class Read {
        final long offset;
        final int length;
        final byte[] buffer;
        final int bufferOffset;
        final RemoteIsoSource.ReadRequest request;
        final AtomicLong bytes;
        int written;

        Read(long offset, byte[] buffer, int bufferOffset, int length,
             RemoteIsoSource.ReadRequest request, AtomicLong bytes) {
            this.offset = offset;
            this.buffer = buffer;
            this.bufferOffset = bufferOffset;
            this.length = length;
            this.request = request;
            this.bytes = bytes;
        }

        void publish(int count) throws IOException {
            request.checkCancelled();
            for (int i = written; i < count; i++) buffer[bufferOffset + i] = value(offset + i);
            bytes.addAndGet(count - written);
            written = count;
            request.publish(count);
        }
    }

    private static final class FakeSource implements RemoteIsoSource {
        final int pageSize;
        final List<Read> reads = new CopyOnWriteArrayList<>();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicLong bytes = new AtomicLong();
        volatile long total;
        volatile boolean closed;
        volatile Hook hook = read -> read.publish(read.length);

        FakeSource(int size, int pages) { pageSize = size; total = (long) size * pages; }
        long count(int page) { return reads.stream().filter(read -> read.offset / pageSize == page).count(); }
        public long length() { return total; }
        public String validator() { return "test"; }
        public void close() { closed = true; for (Read read : reads) read.request.cancel(); }
        public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
            return readAt(offset, buffer, bufferOffset, length, new ReadRequest(count -> {}));
        }
        public int readAt(long offset, byte[] buffer, int bufferOffset, int length, ReadRequest request) throws IOException {
            if (closed) throw new IOException("closed");
            Read read = new Read(offset, buffer, bufferOffset, length, request, bytes);
            reads.add(read);
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                hook.read(read);
                request.checkCancelled();
                return read.written;
            } finally {
                request.detach();
                active.decrementAndGet();
            }
        }
    }
}
