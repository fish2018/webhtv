package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Task {

    private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
    private static final ListeningExecutorService largeExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(20));
    private static final ListeningExecutorService loaderExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    private static final ListeningExecutorService recommendationExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3));
    private static final ThreadPoolExecutor searchPool = createSearchPool();
    private static final ListeningExecutorService searchExecutor = MoreExecutors.listeningDecorator(searchPool);
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static ThreadPoolExecutor createSearchPool() {
        return createSearchPool(20);
    }

    private static ThreadPoolExecutor createSearchPool(int threads) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public static ListeningExecutorService executor() {
        return executor;
    }

    public static ListeningExecutorService largeExecutor() {
        return largeExecutor;
    }

    public static ListeningExecutorService loaderExecutor() {
        return loaderExecutor;
    }

    public static ListeningExecutorService recommendationExecutor() {
        return recommendationExecutor;
    }

    public static ListeningExecutorService searchExecutor() {
        return largeExecutor;
    }

    public static ListeningExecutorService searchPoolExecutor() {
        return searchExecutor;
    }

    public static ListeningExecutorService newSearchExecutor(int threads) {
        return MoreExecutors.listeningDecorator(createSearchPool(Math.max(1, threads)));
    }

    public static void applySearchThread(int threads) {
        int n = Math.max(1, threads);
        if (n >= searchPool.getCorePoolSize()) {
            searchPool.setMaximumPoolSize(n);
            searchPool.setCorePoolSize(n);
        } else {
            searchPool.setCorePoolSize(n);
            searchPool.setMaximumPoolSize(n);
        }
    }

    public static ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public static Future<?> submitLarge(Runnable task) {
        return largeExecutor.submit(task);
    }

    public static Future<?> submitRecommendation(Runnable task) {
        return recommendationExecutor.submit(task);
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess) {
        return callback(onSuccess, null);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess, @Nullable Consumer<Throwable> onFailure) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(@NonNull Throwable error) {
                if (onFailure != null) onFailure.accept(error);
            }
        };
    }

    public static final class Scope {

        private final Executor executor;
        private final Object lock = new Object();
        private final Set<ScopedFuture<?>> futures = new HashSet<>();
        private final ThreadLocal<Long> runningGeneration = new ThreadLocal<>();
        private long generation;
        private boolean closed;

        public Scope(Executor executor) {
            if (executor == null) throw new IllegalArgumentException("executor == null");
            this.executor = executor;
        }

        public Future<?> submit(Runnable runnable) {
            return submit(executor, runnable);
        }

        public Future<?> submit(Executor target, Runnable runnable) {
            if (runnable == null) throw new IllegalArgumentException("runnable == null");
            return submitInternal(target, Executors.callable(runnable, null));
        }

        public <T> Future<T> submitCallable(Callable<T> callable) {
            return submitCallable(executor, callable);
        }

        public <T> Future<T> submitCallable(Executor target, Callable<T> callable) {
            if (callable == null) throw new IllegalArgumentException("callable == null");
            return submitInternal(target, callable);
        }

        private <T> Future<T> submitInternal(Executor target, Callable<T> callable) {
            if (target == null) throw new IllegalArgumentException("executor == null");
            ScopedFuture<T> future;
            synchronized (lock) {
                Long inheritedGeneration = runningGeneration.get();
                long taskGeneration = inheritedGeneration == null ? generation : inheritedGeneration;
                if (closed || taskGeneration != generation) return cancelledFuture();
                future = new ScopedFuture<>(callable, taskGeneration);
                futures.add(future);
            }
            try {
                target.execute(future);
            } catch (RuntimeException e) {
                future.cancel(false);
                throw e;
            }
            return future;
        }

        public void cancelAll() {
            cancel(false);
        }

        public void close() {
            cancel(true);
        }

        private void cancel(boolean close) {
            List<ScopedFuture<?>> pending;
            synchronized (lock) {
                generation++;
                if (close) closed = true;
                pending = new ArrayList<>(futures);
                futures.clear();
            }
            for (Future<?> future : pending) future.cancel(true);
        }

        private boolean isCurrent(long taskGeneration) {
            synchronized (lock) {
                return !closed && taskGeneration == generation;
            }
        }

        private <T> Future<T> cancelledFuture() {
            FutureTask<T> future = new FutureTask<>(() -> null);
            future.cancel(false);
            return future;
        }

        private final class ScopedFuture<T> extends FutureTask<T> {

            private final long taskGeneration;

            private ScopedFuture(Callable<T> callable, long taskGeneration) {
                super(callable);
                this.taskGeneration = taskGeneration;
            }

            @Override
            public void run() {
                if (!isCurrent(taskGeneration)) {
                    cancel(false);
                    return;
                }
                runningGeneration.set(taskGeneration);
                try {
                    super.run();
                } finally {
                    runningGeneration.remove();
                }
            }

            @Override
            protected void done() {
                synchronized (lock) {
                    futures.remove(this);
                }
            }
        }
    }
}
