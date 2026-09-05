package com.fongmi.android.tv.server.proxy;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProxySessionRegistry implements AutoCloseable {

    private final int maxSessions;
    private final Set<Lease> active = Collections.newSetFromMap(new IdentityHashMap<>());

    private long nextId = 1;
    private boolean closed;

    public ProxySessionRegistry(int maxSessions) {
        if (maxSessions <= 0) throw new IllegalArgumentException("maxSessions must be positive");
        this.maxSessions = maxSessions;
    }

    public synchronized Optional<Lease> tryAcquire(Runnable cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (closed || active.size() >= maxSessions) return Optional.empty();
        Lease lease = new Lease(this, nextId++, cancellation);
        active.add(lease);
        return Optional.of(lease);
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public int maxSessions() {
        return maxSessions;
    }

    @Override
    public void close() {
        Lease[] sessions;
        synchronized (this) {
            if (closed) return;
            closed = true;
            sessions = active.toArray(new Lease[0]);
            active.clear();
        }
        for (Lease session : sessions) session.cancelFromRegistry();
    }

    private void release(Lease lease) {
        synchronized (this) {
            active.remove(lease);
        }
        lease.runCancellation();
    }

    public static final class Lease implements AutoCloseable {

        private final ProxySessionRegistry owner;
        private final long id;
        private final Runnable cancellation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(ProxySessionRegistry owner, long id, Runnable cancellation) {
            this.owner = owner;
            this.id = id;
            this.cancellation = cancellation;
        }

        public long id() {
            return id;
        }

        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) owner.release(this);
        }

        private void cancelFromRegistry() {
            if (closed.compareAndSet(false, true)) runCancellation();
        }

        private void runCancellation() {
            try {
                cancellation.run();
            } catch (RuntimeException ignored) {
                // Cancellation is best-effort and must not block other session cleanup.
            }
        }
    }
}