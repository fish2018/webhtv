package com.fongmi.android.tv.server.proxy;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class ProxyTargetRegistry implements AutoCloseable {

    static final String STREAM_PATH_PREFIX = "/v1/stream/";
    static final long DEFAULT_IDLE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static final int ID_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Object lock = new Object();
    private final Map<String, Entry> targets = new HashMap<>();
    private final long idleTtlMillis;
    private final LongSupplier nowMillis;
    private final ScheduledThreadPoolExecutor expiryExecutor;
    private boolean closed;

    ProxyTargetRegistry() {
        this(DEFAULT_IDLE_TTL_MILLIS, System::currentTimeMillis);
    }

    ProxyTargetRegistry(long idleTtlMillis, LongSupplier nowMillis) {
        if (idleTtlMillis <= 0) throw new IllegalArgumentException("idle TTL must be positive");
        this.idleTtlMillis = idleTtlMillis;
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
        this.expiryExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "MultiThreadProxy-TargetExpiry");
            thread.setDaemon(true);
            return thread;
        });
        this.expiryExecutor.setRemoveOnCancelPolicy(true);
        this.expiryExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    Registration register(String url, Map<String, String> headers) {
        String targetUrl = Objects.requireNonNull(url, "url").trim();
        UpstreamRangeClient.requireHttpUrl(targetUrl);
        Target target = new Target(targetUrl, UpstreamHeaderPolicy.sanitize(headers));
        synchronized (lock) {
            if (closed) throw new IllegalStateException("proxy target registry is closed");
            String id;
            do {
                id = randomId();
            } while (targets.containsKey(id));
            Entry entry = new Entry(target, deadline(nowMillis.getAsLong()));
            targets.put(id, entry);
            scheduleExpiry(id, entry, idleTtlMillis);
            return new Registration(this, id, entry);
        }
    }

    Target resolve(String id) {
        if (id == null || id.isBlank()) return null;
        synchronized (lock) {
            if (closed) return null;
            Entry entry = targets.get(id);
            if (entry == null) return null;
            long now = nowMillis.getAsLong();
            if (entry.expiresAtMillis <= now) {
                remove(id, entry);
                return null;
            }
            entry.expiresAtMillis = deadline(now);
            return entry.target;
        }
    }

    int size() {
        synchronized (lock) {
            return targets.size();
        }
    }

    private long deadline(long now) {
        return now > Long.MAX_VALUE - idleTtlMillis ? Long.MAX_VALUE : now + idleTtlMillis;
    }

    private void scheduleExpiry(String id, Entry entry, long delayMillis) {
        entry.expiry = expiryExecutor.schedule(
                () -> expire(id, entry),
                Math.max(1, delayMillis),
                TimeUnit.MILLISECONDS);
    }

    private void expire(String id, Entry entry) {
        synchronized (lock) {
            if (closed || targets.get(id) != entry) return;
            long remaining = entry.expiresAtMillis - nowMillis.getAsLong();
            if (remaining <= 0) {
                targets.remove(id, entry);
                entry.expiry = null;
                return;
            }
            scheduleExpiry(id, entry, remaining);
        }
    }

    private void remove(String id, Entry entry) {
        synchronized (lock) {
            if (!targets.remove(id, entry)) return;
            ScheduledFuture<?> expiry = entry.expiry;
            entry.expiry = null;
            if (expiry != null) expiry.cancel(false);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            for (Entry entry : targets.values()) {
                if (entry.expiry != null) entry.expiry.cancel(false);
                entry.expiry = null;
            }
            targets.clear();
            expiryExecutor.shutdownNow();
        }
    }

    private static String randomId() {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    record Target(String url, Map<String, String> headers) {

        @Override
        public String toString() {
            return "Target{redacted}";
        }
    }

    static final class Registration implements AutoCloseable {

        private final ProxyTargetRegistry owner;
        private final String id;
        private final Entry entry;

        private Registration(ProxyTargetRegistry owner, String id, Entry entry) {
            this.owner = owner;
            this.id = id;
            this.entry = entry;
        }

        String id() {
            return id;
        }

        String path() {
            return STREAM_PATH_PREFIX + id;
        }

        @Override
        public void close() {
            owner.remove(id, entry);
        }
    }

    private static final class Entry {

        private final Target target;
        private long expiresAtMillis;
        private ScheduledFuture<?> expiry;

        private Entry(Target target, long expiresAtMillis) {
            this.target = target;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
