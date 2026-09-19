package com.fongmi.android.tv.player.iso;

import com.github.catvod.crawler.SpiderDebug;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Raw-byte read-ahead for interactive discs. Never reads or advances the disc VM. */
final class ProgressiveIsoPageCache implements RemoteIsoSource {

    private static final int MAX_READERS = 3; // Two read-ahead requests plus foreground capacity.
    private final RemoteIsoSource source;
    private final IsoDiskPageStore disk;
    private final int pageSize;
    private final int maxPages;
    private final int readAlignment;
    private final int prefetchDistance;
    private final LinkedHashMap<Long, Page> pages = new LinkedHashMap<>(8, 0.75f, true);
    private final LinkedHashMap<Long, Load> loads = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_READERS,
            runnable -> new Thread(runnable, "iso-range"));
    private boolean closed;
    private IOException sourceFailure;
    private int running;
    private long lastReadEnd = -1;
    private long hits;
    private long misses;
    private long partialHits;
    private long diskHits;
    private long networkPages;
    private long cancelledReads;

    ProgressiveIsoPageCache(RemoteIsoSource source, IsoDiskPageStore disk) {
        this(source, IsoPageCache.DEFAULT_PAGE_SIZE, IsoPageCache.DEFAULT_MAX_PAGES, disk);
    }

    ProgressiveIsoPageCache(RemoteIsoSource source, int pageSize, int maxPages, IsoDiskPageStore disk) {
        if (pageSize <= 0 || maxPages <= 0) throw new IllegalArgumentException("Invalid ISO cache size");
        this.source = source;
        this.disk = disk;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        readAlignment = Math.min(64 * 1024, Math.max(1, pageSize / 16));
        prefetchDistance = Math.min(4, Math.max(0, maxPages - 1));
    }

    @Override
    public long length() throws IOException {
        synchronized (this) {
            ensureOpen();
        }
        return source.length();
    }

    @Override
    public int readAt(long offset, byte[] buffer, int bufferOffset, int length) throws IOException {
        if (offset < 0 || bufferOffset < 0 || length < 0 || bufferOffset > buffer.length - length) throw new IndexOutOfBoundsException();
        long total = length();
        if (offset >= total || length == 0) return 0;
        int wanted = (int) Math.min(length, total - offset);
        int written = 0;
        boolean sequential;
        synchronized (this) {
            ensureOpen();
            sequential = offset == lastReadEnd;
            if (!sequential) cancelOutside(offset / pageSize, offset / pageSize);
        }
        while (written < wanted) {
            long index = offset / pageSize;
            int position = (int) (offset % pageSize);
            int count = Math.min(wanted - written, (int) Math.min(pageSize - position, total - offset));
            long started = System.nanoTime();
            synchronized (this) {
                ensureOpen();
                Page page = page(index, total);
                page.waiters++;
                page.failure = null; // A new demand may retry only the still-missing bytes.
                try {
                    if (page.available(position) >= position + count) {
                        hits++;
                        if (!page.complete()) partialHits++;
                    } else {
                        misses++;
                    }
                    while (page.available(position) < position + count) {
                        ensureOpen();
                        if (page.failure != null) throw page.failure;
                        schedule(page, position, false);
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("ISO range wait interrupted", e);
                        }
                    }
                    ensureOpen();
                    System.arraycopy(page.data, position, buffer, bufferOffset + written, count);
                    long waitMs = (System.nanoTime() - started) / 1_000_000;
                    if (waitMs >= 100 && SpiderDebug.isEnabled()) {
                        SpiderDebug.log("iso-cache", "demand page=%d offset=%d length=%d waitMs=%d partial=%s progressive=true",
                                index, position, count, waitMs, !page.complete());
                    }
                } finally {
                    page.waiters--;
                    trim();
                }
                if ((sequential && position + count >= readAlignment) || position + count == page.size) {
                    prefetch(index, total);
                }
                offset += count;
                written += count;
                lastReadEnd = offset;
            }
            sequential = true;
        }
        return written;
    }

    private Page page(long index, long total) {
        Page page = pages.get(index);
        if (page == null) {
            Load existing = loads.get(index);
            page = existing == null ? new Page(index, (int) Math.min(pageSize, total - index * pageSize)) : existing.page;
            pages.put(index, page);
        }
        return page;
    }

    private void schedule(Page page, int preferred, boolean speculative) {
        if (page.complete() || closed) return;
        Load existing = loads.get(page.index);
        if (existing != null) {
            if (!speculative) {
                existing.speculative = false;
                if (!existing.started) existing.preferred = preferred;
            }
        } else {
            loads.put(page.index, new Load(page, preferred, speculative));
        }
        dispatch();
    }

    /** The executor only contains running jobs; the bounded page/horizon map owns queued work. */
    private void dispatch() {
        while (!closed && running < MAX_READERS) {
            Load next = null;
            for (Load load : loads.values()) {
                if (load.started || load.cancelled) continue;
                if (next == null || (next.speculative && !load.speculative)) next = load;
                if (!load.speculative) break;
            }
            if (next == null) return;
            next.started = true;
            running++;
            workers.execute(next);
        }
    }

    private void prefetch(long current, long total) {
        long last = Math.min((total - 1) / pageSize, current + prefetchDistance);
        cancelOutside(current, last);
        for (long index = current + 1; index <= last; index++) {
            Page page = page(index, total);
            if (page.failure == null) schedule(page, 0, true);
        }
        trim();
    }

    private void cancelOutside(long first, long last) {
        Iterator<Load> iterator = loads.values().iterator();
        while (iterator.hasNext()) {
            Load load = iterator.next();
            if (load.page.waiters != 0 || load.page.complete()
                    || (load.page.index >= first && load.page.index <= last)) continue;
            cancel(load);
            if (!load.started) iterator.remove();
        }
    }

    private void cancel(Load load) {
        if (load.cancelled) return;
        load.cancelled = true;
        cancelledReads++;
        if (load.request != null) load.request.cancel();
    }

    private void trim() {
        Iterator<Page> iterator = pages.values().iterator();
        while (pages.size() > maxPages && iterator.hasNext()) {
            Page evicted = iterator.next();
            iterator.remove();
            Load load = loads.get(evicted.index);
            if (load != null && evicted.waiters == 0 && !evicted.complete()) {
                cancel(load);
                if (!load.started) loads.remove(evicted.index);
            }
        }
    }

    private final class Load implements Runnable {
        final Page page;
        int preferred;
        boolean speculative;
        boolean started;
        boolean cancelled;
        ReadRequest request;

        Load(Page page, int preferred, boolean speculative) {
            this.page = page;
            this.preferred = preferred;
            this.speculative = speculative;
        }

        @Override
        public void run() {
            IOException failure = null;
            try {
                load();
            } catch (IOException e) {
                failure = e;
                if (e instanceof IsoSourceException iso && iso.reason() == IsoSourceException.Reason.SOURCE_CHANGED) {
                    synchronized (ProgressiveIsoPageCache.this) {
                        sourceFailure = iso;
                    }
                    close();
                }
            } catch (RuntimeException e) {
                failure = new IOException("ISO page load failed", e);
            } finally {
                synchronized (ProgressiveIsoPageCache.this) {
                    if (!cancelled && failure != null) page.failure = failure;
                    loads.remove(page.index, this);
                    running--;
                    ProgressiveIsoPageCache.this.notifyAll();
                    dispatch();
                }
            }
        }

        private void load() throws IOException {
            boolean checkDisk;
            synchronized (ProgressiveIsoPageCache.this) {
                ensureActive();
                checkDisk = !page.diskChecked;
                page.diskChecked = true;
            }
            if (checkDisk && disk != null) {
                byte[] cached = disk.read(page.index, page.size);
                if (cached != null) {
                    synchronized (ProgressiveIsoPageCache.this) {
                        ensureActive();
                        page.data = cached;
                        page.publish(0, cached.length);
                        diskHits++;
                        ProgressiveIsoPageCache.this.notifyAll();
                        logStats();
                    }
                    return;
                }
            }
            while (true) {
                final int begin;
                final int end;
                synchronized (ProgressiveIsoPageCache.this) {
                    ensureActive();
                    if (page.complete()) break;
                    if (page.data == null) page.data = new byte[page.size];
                    int aligned = preferred / readAlignment * readAlignment;
                    int available = page.available(aligned);
                    begin = available == page.size ? page.available(0) : available;
                    Integer next = page.ready.ceilingKey(begin);
                    end = next == null ? page.size : next;
                    request = new ReadRequest(count -> {
                        synchronized (ProgressiveIsoPageCache.this) {
                            ensureActive();
                            if (count <= 0 || count > end - begin) throw new IOException("Invalid ISO read progress");
                            page.publish(begin, begin + count);
                            ProgressiveIsoPageCache.this.notifyAll();
                        }
                    });
                }
                int count = source.readAt(page.index * pageSize + begin, page.data, begin, end - begin, request);
                if (count <= 0) throw new EOFException("Incomplete ISO page");
            }
            // Readers can already consume the completed bytes while the existing
            // coordinator reserves, writes and atomically commits the disk page.
            if (disk != null) disk.write(page.index, page.data, speculative);
            synchronized (ProgressiveIsoPageCache.this) {
                ensureOpen();
                networkPages++;
                logStats();
            }
        }

        private void ensureActive() throws IOException {
            ensureOpen();
            if (cancelled) throw new IOException("ISO page superseded");
        }
    }

    private static final class Page {
        final long index;
        final int size;
        final TreeMap<Integer, Integer> ready = new TreeMap<>();
        byte[] data;
        int waiters;
        boolean diskChecked;
        IOException failure;

        Page(long index, int size) {
            this.index = index;
            this.size = size;
        }

        int available(int position) {
            Map.Entry<Integer, Integer> entry = ready.floorEntry(position);
            return entry == null ? position : Math.max(position, entry.getValue());
        }

        boolean complete() {
            return available(0) == size;
        }

        void publish(int start, int end) {
            Map.Entry<Integer, Integer> previous = ready.floorEntry(start);
            if (previous != null && previous.getValue() >= start) {
                start = previous.getKey();
                end = Math.max(end, previous.getValue());
                ready.remove(previous.getKey());
            }
            Map.Entry<Integer, Integer> next;
            while ((next = ready.ceilingEntry(start)) != null && next.getKey() <= end) {
                end = Math.max(end, next.getValue());
                ready.remove(next.getKey());
            }
            ready.put(start, end);
        }
    }

    private void ensureOpen() throws IOException {
        if (sourceFailure != null) throw sourceFailure;
        if (closed) throw new IsoSourceException(IsoSourceException.Reason.CLOSED, "ISO cache closed");
    }

    private void logStats() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("iso-cache",
                "pages=%d pending=%d hits=%d misses=%d partialHits=%d diskHits=%d networkPages=%d cancelled=%d progressive=true",
                pages.size(), loads.size(), hits, misses, partialHits, diskHits, networkPages, cancelledReads);
    }

    @Override
    public String validator() {
        return source.validator();
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            for (Load load : new ArrayList<>(loads.values())) cancel(load);
            pages.clear();
            loads.clear();
            notifyAll();
        }
        workers.shutdownNow();
        source.close();
        if (disk != null) disk.close();
    }
}
