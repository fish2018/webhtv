package com.fongmi.android.tv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class RecommendationEnrichmentExecutor {

    static final int MAX_CONCURRENCY = 4;

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(MAX_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "recommendation-enrichment-" + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private RecommendationEnrichmentExecutor() {
    }

    static <T, R> List<R> map(List<T> values, Function<? super T, ? extends R> mapper, Function<? super T, ? extends R> fallback) {
        List<R> results = new ArrayList<>();
        if (values == null || values.isEmpty()) return results;

        List<Future<R>> futures = new ArrayList<>(values.size());
        for (T value : values) {
            futures.add(EXECUTOR.submit(() -> mapper.apply(value)));
        }

        for (int index = 0; index < futures.size(); index++) {
            try {
                results.add(futures.get(index).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel(futures);
                for (int remaining = index; remaining < values.size(); remaining++) {
                    results.add(fallback.apply(values.get(remaining)));
                }
                break;
            } catch (ExecutionException e) {
                results.add(fallback.apply(values.get(index)));
            }
        }
        return results;
    }

    static <T, R> void mapAsync(List<T> values, Function<? super T, ? extends R> mapper, Function<? super T, ? extends R> fallback, Consumer<List<R>> callback) {
        List<T> input = values == null ? new ArrayList<>() : new ArrayList<>(values);
        if (input.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
        }

        List<R> results = new ArrayList<>(java.util.Collections.nCopies(input.size(), null));
        AtomicInteger remaining = new AtomicInteger(input.size());
        for (int index = 0; index < input.size(); index++) {
            int resultIndex = index;
            T value = input.get(index);
            EXECUTOR.execute(() -> {
                R result;
                try {
                    result = mapper.apply(value);
                } catch (Throwable ignored) {
                    result = fallback.apply(value);
                }
                List<R> completed = null;
                synchronized (results) {
                    results.set(resultIndex, result);
                    if (remaining.decrementAndGet() == 0) completed = new ArrayList<>(results);
                }
                if (completed != null) callback.accept(completed);
            });
        }
    }

    private static void cancel(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) future.cancel(true);
    }
}
