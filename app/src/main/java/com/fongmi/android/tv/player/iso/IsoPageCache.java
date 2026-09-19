package com.fongmi.android.tv.player.iso;

import com.github.catvod.crawler.SpiderDebug;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class IsoPageCache implements RemoteIsoSource {

    public static final int DEFAULT_PAGE_SIZE = 4 * 1024 * 1024;
    public static final int DEFAULT_MAX_PAGES = 8;

    private final Map<Long, CompletableFuture<byte[]>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<Long, byte[]> pages;
    private final ExecutorService prefetch;
    private final RemoteIsoSource source;
    private final IsoDiskPageStore disk;
    private final int maxPages;
    private final int pageSize;
    private final int prefetchParallelism;
    private final int prefetchDistance;
    private volatile boolean closed;
    private volatile IOException sourceFailure;
    private long lastReadEnd = -1;
    private long nextPrefetch = -1;
    private long lastPrefetch = -1;
    private int prefetchWorkers;
    private long hits;
    private long misses;
    private long diskHits;
    private long networkPages;
    private long prefetchedPages;

    public IsoPageCache(RemoteIsoSource source) {
        this(source, DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGES);
    }

    IsoPageCache(RemoteIsoSource source, IsoDiskPageStore disk) {
        this(source, DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGES, disk);
    }

    public IsoPageCache(RemoteIsoSource source, int pageSize, int maxPages) {
        this(source, pageSize, maxPages, null);
    }

    IsoPageCache(RemoteIsoSource source, int pageSize, int maxPages, IsoDiskPageStore disk) {
        if (pageSize <= 0 || maxPages <= 0) throw new IllegalArgumentException("Invalid ISO cache size");
        this.source = source;
        this.disk = disk;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        // Interactive ISO playback cannot use demuxer read-ahead. Keep a small
        // raw-byte horizon instead, without advancing libbluray's VM.
        this.prefetchParallelism = disk == null ? 1 : 2;
        this.prefetchDistance = disk == null ? 1 : Math.min(4, Math.max(1, maxPages - 1));
        this.prefetch = Executors.newFixedThreadPool(prefetchParallelism,
                r -> new Thread(r, "iso-prefetch"));
        this.pages = new LinkedHashMap<>(maxPages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > IsoPageCache.this.maxPages;
            }
        };
    }

    @Override
    public long length() throws IOException {
        ensureOpen();
        return source.length();
    }

    @Override
    public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
        ensureOpen();
        if (offset < 0 || bufferOffset < 0 || length < 0 || bufferOffset > buffer.length - length) {
            throw new IndexOutOfBoundsException();
        }
        long total = length();
        if (offset >= total || length == 0) return 0;
        boolean sequential;
        synchronized (this) {
            sequential = offset == lastReadEnd;
            // A seek replaces the queued hint. An already running raw-page read may finish,
            // but never queues more work or advances the disc VM.
            if (!sequential) nextPrefetch = lastPrefetch = -1;
        }
        int remaining = (int) Math.min(length, total - offset);
        int written = 0;
        while (remaining > 0) {
            long pageIndex = offset / pageSize;
            int pageOffset = (int) (offset % pageSize);
            byte[] page = page(pageIndex, false);
            ensureOpen();
            if (pageOffset >= page.length) break;
            int count = Math.min(remaining, page.length - pageOffset);
            System.arraycopy(page, pageOffset, buffer, bufferOffset + written, count);
            offset += count;
            written += count;
            remaining -= count;
            if ((sequential && pageOffset + count >= Math.min(64 * 1024, Math.max(1, pageSize / 4)))
                    || pageOffset + count == page.length) {
                prefetch(pageIndex + 1, total);
            }
            sequential = true;
        }
        synchronized (this) {
            lastReadEnd = offset;
        }
        return written;
    }

    private byte[] page(long index, boolean speculative) throws IOException {
        CompletableFuture<byte[]> future;
        boolean owner = false;
        synchronized (this) {
            ensureOpen();
            byte[] cached = pages.get(index);
            if (cached != null) {
                hits++;
                return cached;
            }
            misses++;
            future = pending.get(index);
            if (speculative && future != null) return null;
            if (future == null) {
                future = new CompletableFuture<>();
                pending.put(index, future);
                owner = true;
            }
        }
        if (owner) {
            try {
                byte[] loaded = load(index, speculative);
                synchronized (this) {
                    ensureOpen();
                    pages.put(index, loaded);
                    pending.remove(index, future);
                }
                future.complete(loaded);
                logStats();
                return loaded;
            } catch (Throwable e) {
                synchronized (this) {
                    pending.remove(index, future);
                }
                if (e instanceof IsoSourceException iso && iso.reason() == IsoSourceException.Reason.SOURCE_CHANGED) {
                    sourceFailure = iso;
                    close();
                }
                future.completeExceptionally(e);
                if (e instanceof IOException io) throw io;
                throw new IOException(e);
            }
        }
        try {
            byte[] loaded = future.get();
            ensureOpen();
            return loaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ISO page wait interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException(cause);
        }
    }

    private byte[] load(long index, boolean speculative) throws IOException {
        long offset = index * pageSize;
        long total = source.length();
        if (offset >= total) return new byte[0];
        int expected = (int) Math.min(pageSize, total - offset);
        if (disk != null) {
            byte[] cached = disk.read(index, expected);
            if (cached != null) {
                synchronized (this) {
                    diskHits++;
                }
                return cached;
            }
        }
        byte[] data = new byte[expected];
        int read = 0;
        while (read < expected) {
            ensureOpen();
            int count = source.readAt(offset + read, data, read, expected - read);
            if (count <= 0) throw new EOFException("Incomplete ISO page");
            read += count;
        }
        ensureOpen();
        if (disk != null) disk.write(index, data, speculative);
        synchronized (this) {
            networkPages++;
            if (speculative) prefetchedPages++;
        }
        return data;
    }

    private synchronized void prefetch(long index, long total) {
        if (closed || index > (total - 1) / pageSize) return;
        nextPrefetch = index;
        lastPrefetch = Math.min((total - 1) / pageSize, index + prefetchDistance - 1);
        while (nextPrefetch <= lastPrefetch
                && (pages.containsKey(nextPrefetch) || pending.containsKey(nextPrefetch))) nextPrefetch++;
        if (nextPrefetch > lastPrefetch) return;
        while (prefetchWorkers < prefetchParallelism) {
            prefetchWorkers++;
            try {
                prefetch.execute(this::runPrefetch);
            } catch (RejectedExecutionException ignored) {
                prefetchWorkers--;
                break;
            }
        }
    }

    private void runPrefetch() {
        while (true) {
            long index;
            synchronized (IsoPageCache.this) {
                while (nextPrefetch >= 0 && nextPrefetch <= lastPrefetch
                        && (pages.containsKey(nextPrefetch) || pending.containsKey(nextPrefetch))) {
                    nextPrefetch++;
                }
                if (closed || nextPrefetch < 0 || nextPrefetch > lastPrefetch) {
                    prefetchWorkers--;
                    return;
                }
                index = nextPrefetch++;
            }
            try {
                page(index, true);
            } catch (IOException ignored) {
                // Failed speculation is not cached; a later demand can retry the page.
            }
        }
    }

    private synchronized void logStats() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("iso-cache",
                "pages=%d pending=%d hits=%d misses=%d diskHits=%d networkPages=%d prefetched=%d disk=%s",
                pages.size(), pending.size(), hits, misses, diskHits, networkPages, prefetchedPages, disk != null);
    }

    private void ensureOpen() throws IOException {
        if (sourceFailure != null) throw sourceFailure;
        if (closed) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO cache closed");
    }

    @Override
    public String validator() {
        return source.validator();
    }

    @Override
    public void close() {
        ArrayList<CompletableFuture<byte[]>> waiters;
        synchronized (this) {
            if (closed) return;
            closed = true;
            nextPrefetch = lastPrefetch = -1;
            waiters = new ArrayList<>(pending.values());
            pages.clear();
            pending.clear();
        }
        IOException error = sourceFailure != null ? sourceFailure
                : new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO cache closed");
        for (CompletableFuture<byte[]> waiter : waiters) waiter.completeExceptionally(error);
        prefetch.shutdownNow();
        source.close();
        if (disk != null) disk.close();
    }
}
