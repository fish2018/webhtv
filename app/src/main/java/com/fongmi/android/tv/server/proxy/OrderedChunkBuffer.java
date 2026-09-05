package com.fongmi.android.tv.server.proxy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, cancellation-aware buffer that releases chunks in ascending index order.
 * A byte array passed to {@link #put(int, byte[])} is owned by this buffer afterwards.
 */
public final class OrderedChunkBuffer implements AutoCloseable {

    private final long maxBytes;
    private final Map<Long, byte[]> chunks = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private long bufferedBytes;
    private long nextIndex;
    private boolean completed;
    private boolean closed;
    private Throwable failure;

    public OrderedChunkBuffer(long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    public void put(long index, byte[] data) throws InterruptedException {
        Objects.requireNonNull(data, "data");
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        if (data.length == 0) throw new IllegalArgumentException("chunk must not be empty");
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("chunk exceeds buffer budget");
        }

        lock.lockInterruptibly();
        try {
            ensureWritable();
            if (index < nextIndex || chunks.containsKey(index)) {
                throw new IllegalArgumentException("chunk index is duplicate or already consumed");
            }
            while (bufferedBytes > maxBytes - data.length) {
                ensureWritable();
                changed.await();
            }
            ensureWritable();
            chunks.put(index, data);
            bufferedBytes += data.length;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until the next ordered chunk is available, the producer completes, or the buffer fails.
     * Returns {@code null} only after {@link #complete()} or {@link #close()} when no chunk remains.
     */
    public byte[] takeNext() throws InterruptedException, IOException {
        lock.lockInterruptibly();
        try {
            while (true) {
                if (failure != null) throw failureException(failure);
                byte[] data = chunks.remove(nextIndex);
                if (data != null) {
                    bufferedBytes -= data.length;
                    nextIndex++;
                    changed.signalAll();
                    return data;
                }
                if (completed || closed) {
                    chunks.clear();
                    bufferedBytes = 0;
                    return null;
                }
                changed.await();
            }
        } finally {
            lock.unlock();
        }
    }

    public void complete() {
        lock.lock();
        try {
            if (!closed && failure == null) {
                completed = true;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void fail(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        lock.lock();
        try {
            if (!closed && failure == null) {
                failure = cause;
                chunks.clear();
                bufferedBytes = 0;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public long bufferedBytes() {
        lock.lock();
        try {
            return bufferedBytes;
        } finally {
            lock.unlock();
        }
    }

    public long nextIndex() {
        lock.lock();
        try {
            return nextIndex;
        } finally {
            lock.unlock();
        }
    }

    public boolean isCompleted() {
        lock.lock();
        try {
            return completed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (!closed) {
                closed = true;
                chunks.clear();
                bufferedBytes = 0;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    private void ensureWritable() {
        if (failure != null) throw new IllegalStateException("buffer has failed", failure);
        if (closed) throw new IllegalStateException("buffer is closed");
        if (completed) throw new IllegalStateException("buffer is complete");
    }

    private static IOException failureException(Throwable cause) {
        return new IOException("ordered chunk buffer failed", cause);
    }
}