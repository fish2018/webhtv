package com.fongmi.android.tv.api.loader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

final class LoaderClearQueue {

    private final ExecutorService executor;
    private final ThreadLocal<Boolean> running;
    private Entry latest;
    private int nextId;

    LoaderClearQueue(ExecutorService executor) {
        this.executor = executor;
        this.running = new ThreadLocal<>();
    }

    synchronized Entry submit(String reason, IntConsumer action) {
        int id = ++nextId;
        Future<?> future = executor.submit(() -> {
            running.set(true);
            try {
                action.accept(id);
            } finally {
                running.remove();
            }
        });
        return latest = new Entry(id, reason, future);
    }

    synchronized Entry latest() {
        return latest;
    }

    boolean isRunning() {
        return Boolean.TRUE.equals(running.get());
    }

    static final class Entry {

        private final int id;
        private final String reason;
        private final Future<?> future;

        Entry(int id, String reason, Future<?> future) {
            this.id = id;
            this.reason = reason;
            this.future = future;
        }

        int id() {
            return id;
        }

        String reason() {
            return reason;
        }

        Future<?> future() {
            return future;
        }
    }
}
