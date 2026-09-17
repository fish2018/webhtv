package com.fongmi.android.tv.server.proxy;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD.AsyncRunner;
import fi.iki.elonen.NanoHTTPD.ClientHandler;

public final class BoundedAsyncRunner implements AsyncRunner {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final Set<ClientHandler> running;
    private final ThreadPoolExecutor executor;

    public BoundedAsyncRunner(int workerCount, int queueCapacity) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be positive");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.running = java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                daemonThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void exec(ClientHandler clientHandler) {
        if (clientHandler == null) return;
        running.add(clientHandler);
        try {
            executor.execute(() -> {
                try {
                    clientHandler.run();
                } finally {
                    closed(clientHandler);
                }
            });
        } catch (RejectedExecutionException e) {
            running.remove(clientHandler);
            if (clientHandler instanceof OverloadHandler overloadHandler) {
                overloadHandler.rejectOverload();
            } else {
                clientHandler.close();
            }
        }
    }

    @Override
    public void closed(ClientHandler clientHandler) {
        running.remove(clientHandler);
    }

    @Override
    public void closeAll() {
        for (ClientHandler clientHandler : new ArrayList<>(running)) clientHandler.close();
        executor.shutdownNow();
        running.clear();
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "MultiThreadProxy-Request-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public interface OverloadHandler {

        void rejectOverload();
    }
}
