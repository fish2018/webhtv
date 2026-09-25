package com.fongmi.android.tv.server.proxy;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class SegmentedRangeEngine implements AutoCloseable {

    private static final long RESUBMIT_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final ProxyRuntimeConfig config;
    private final UpstreamRangeClient upstream;
    private final ProxySessionRegistry sessions;
    private final ThreadPoolExecutor workers;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SegmentedRangeEngine(
            ProxyRuntimeConfig config,
            UpstreamRangeClient upstream,
            ProxySessionRegistry sessions) {
        ProxyRuntimeConfigValidator.requireValid(config);
        this.config = config;
        this.upstream = Objects.requireNonNull(upstream, "upstream");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.workers = new ThreadPoolExecutor(
                0,
                config.rangeWorkers(),
                30,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new RangeThreadFactory());
        this.workers.allowCoreThreadTimeOut(true);
    }

    public TransferResult transfer(
            String url,
            Map<String, String> requestHeaders,
            String rangeHeader,
            OutputStream output) throws IOException {
        return transfer(url, requestHeaders, rangeHeader, output, config, ignored -> {});
    }

    TransferResult transfer(
            String url,
            Map<String, String> requestHeaders,
            String rangeHeader,
            OutputStream output,
            PreparationListener listener) throws IOException {
        return transfer(url, requestHeaders, rangeHeader, output, config, listener);
    }

    TransferResult transfer(
            String url,
            Map<String, String> requestHeaders,
            String rangeHeader,
            OutputStream output,
            ProxyRuntimeConfig sessionConfig,
            PreparationListener listener) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(listener, "listener");
        ProxyRuntimeConfigValidator.requireValid(sessionConfig);
        if (closed.get()) throw new IOException("segmented range engine is closed");
        TransferState state = new TransferState();
        Optional<ProxySessionRegistry.Lease> acquired = sessions.tryAcquire(state::cancel);
        if (acquired.isEmpty()) return prepared(listener, TransferResult.sessionLimit());
        ProxySessionRegistry.Lease lease = acquired.orElseThrow();
        try {
            return transferAcquired(url, requestHeaders, rangeHeader, output, sessionConfig, state, listener);
        } finally {
            state.cancel();
            lease.close();
        }
    }

    private TransferResult transferAcquired(
            String url,
            Map<String, String> requestHeaders,
            String rangeHeader,
            OutputStream output,
            ProxyRuntimeConfig sessionConfig,
            TransferState state,
            PreparationListener listener) throws IOException {
        UpstreamRangeClient.ProbeResult probe = upstream.probe(url, requestHeaders);
        if (!probe.canParallel()) return prepared(listener, TransferResult.fromProbe(probe));

        HttpByteRange.ParseResult parsedRange = HttpByteRange.parse(rangeHeader, probe.totalLength());
        if (parsedRange.status() == HttpByteRange.ParseStatus.UNSATISFIABLE) {
            return prepared(listener, TransferResult.of(Decision.UNSATISFIABLE, probe, null, 0));
        }
        if (!parsedRange.isValid()) {
            Decision decision = parsedRange.status() == HttpByteRange.ParseStatus.ABSENT
                    ? Decision.FALLBACK_SINGLE_CONNECTION
                    : Decision.INVALID_RANGE;
            return prepared(listener, TransferResult.of(decision, probe, null, 0));
        }

        HttpByteRange requested = parsedRange.range();
        RangePlanner.Plan plan = RangePlanner.planTransferBlocks(requested, sessionConfig);
        long perSessionBudget = sessionConfig.bufferBudgetBytes() / sessionConfig.maxSessions();
        long largestShard = plan.maxShardLength();
        if (perSessionBudget <= 0
                || largestShard > perSessionBudget
                || largestShard > Integer.MAX_VALUE) {
            return prepared(listener, TransferResult.of(Decision.RESOURCE_LIMIT, probe, requested, 0));
        }

        long budgetConcurrency = perSessionBudget / largestShard;
        long concurrency = Math.min(plan.shardCount(), sessionConfig.rangeConcurrency());
        concurrency = Math.min(concurrency, sessionConfig.nominalReorderWindowBlocks());
        concurrency = Math.min(concurrency, workers.getMaximumPoolSize());
        concurrency = Math.min(concurrency, budgetConcurrency);
        if (concurrency <= 0) {
            return prepared(listener, TransferResult.of(Decision.RESOURCE_LIMIT, probe, requested, 0));
        }

        OrderedChunkBuffer buffer = new OrderedChunkBuffer(perSessionBudget);
        state.attach(buffer);
        schedule(state, buffer, probe, plan.shard(0), sessionConfig);

        long written = 0;
        try {
            byte[] first = buffer.takeNext();
            if (first == null) throw new IOException("segmented transfer cancelled");
            listener.onPrepared(TransferResult.of(Decision.PARALLEL, probe, requested, 0));
            output.write(first);
            written = Math.addExact(written, first.length);
            state.remove(0);

            long nextToSchedule = 1;
            long bootstrapEnd = Math.min(plan.shardCount(), concurrency + 1);
            while (nextToSchedule < bootstrapEnd) {
                schedule(state, buffer, probe, plan.shard(nextToSchedule++), sessionConfig);
            }
            for (long expected = 1; expected < plan.shardCount(); expected++) {
                byte[] bytes = buffer.takeNext();
                if (bytes == null) throw new IOException("segmented transfer cancelled");
                output.write(bytes);
                written = Math.addExact(written, bytes.length);
                state.remove(expected);
                if (nextToSchedule < plan.shardCount()) {
                    schedule(state, buffer, probe, plan.shard(nextToSchedule++), sessionConfig);
                }
            }
            output.flush();
            return TransferResult.of(Decision.PARALLEL, probe, requested, written);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("segmented transfer interrupted", e);
        } catch (ArithmeticException e) {
            throw new IOException("segmented transfer byte count overflow", e);
        }
    }

    private static TransferResult prepared(PreparationListener listener, TransferResult result) {
        listener.onPrepared(result);
        return result;
    }

    private void schedule(
            TransferState state,
            OrderedChunkBuffer buffer,
            UpstreamRangeClient.ProbeResult probe,
            RangePlanner.Shard shard,
            ProxyRuntimeConfig sessionConfig) throws IOException {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                byte[] bytes = fetchWithRetry(state, probe, shard, sessionConfig);
                if (!state.isCancelled()) buffer.put(shard.index(), bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                state.fail(e);
            } catch (Throwable failure) {
                state.fail(failure);
            }
            return null;
        });
        state.track(shard.index(), task);
        while (true) {
            if (state.isCancelled() || closed.get() || workers.isShutdown()) {
                state.remove(shard.index());
                task.cancel(true);
                throw new IOException("segmented range engine stopped");
            }
            try {
                workers.execute(task);
                return;
            } catch (RejectedExecutionException e) {
                if (workers.isShutdown()) {
                    state.remove(shard.index());
                    task.cancel(true);
                    throw new IOException("segmented range worker pool rejected task", e);
                }
                LockSupport.parkNanos(RESUBMIT_DELAY_NANOS);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    state.remove(shard.index());
                    task.cancel(true);
                    throw new IOException("interrupted while waiting for a range worker", e);
                }
            }
        }
    }

    private byte[] fetchWithRetry(
            TransferState state,
            UpstreamRangeClient.ProbeResult probe,
            RangePlanner.Shard shard,
            ProxyRuntimeConfig sessionConfig) throws IOException {
        IOException lastFailure = null;
        for (long attempt = 0; attempt <= (long) sessionConfig.retryCount(); attempt++) {
            if (state.isCancelled() || closed.get()) throw new IOException("segmented transfer cancelled");
            try {
                return upstream.fetchShard(
                        probe.finalUrl(),
                        probe.forwardHeaders(),
                        shard,
                        probe.totalLength(),
                        probe.validator());
            } catch (UpstreamRangeClient.RangeFetchException semanticFailure) {
                if (!isRetryable(semanticFailure.responseCode())) throw semanticFailure;
                lastFailure = semanticFailure;
            } catch (IOException networkFailure) {
                lastFailure = networkFailure;
            }
        }
        throw lastFailure == null ? new IOException("upstream range fetch failed") : lastFailure;
    }

    private static boolean isRetryable(int responseCode) {
        return responseCode == 408 || responseCode == 429 || responseCode >= 500 && responseCode <= 599;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) workers.shutdownNow();
    }

    private final class TransferState {

        private final Map<Long, Future<?>> futures = new ConcurrentHashMap<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile OrderedChunkBuffer buffer;

        private void attach(OrderedChunkBuffer buffer) {
            this.buffer = buffer;
            if (cancelled.get()) buffer.close();
        }

        private void track(long index, Future<?> future) {
            futures.put(index, future);
            if (cancelled.get()) future.cancel(true);
        }

        private void remove(long index) {
            futures.remove(index);
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private void fail(Throwable failure) {
            OrderedChunkBuffer current = buffer;
            if (current != null) current.fail(failure);
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            OrderedChunkBuffer current = buffer;
            if (current != null) current.close();
            for (Future<?> future : futures.values()) future.cancel(true);
            futures.clear();
        }
    }

    private static final class RangeThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MultiThreadProxy-Range-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    @FunctionalInterface
    interface PreparationListener {
        void onPrepared(TransferResult result);
    }

    public enum Decision {
        PARALLEL,
        FALLBACK_SINGLE_CONNECTION,
        UNSATISFIABLE,
        INVALID_RANGE,
        INVALID_UPSTREAM,
        SESSION_LIMIT,
        RESOURCE_LIMIT
    }

    public record TransferResult(
            Decision decision,
            long totalLength,
            long startInclusive,
            long endInclusive,
            long bytesWritten,
            UpstreamRangeClient.ProbeResult probe) {

        public boolean isParallel() {
            return decision == Decision.PARALLEL;
        }

        private static TransferResult sessionLimit() {
            return new TransferResult(Decision.SESSION_LIMIT, -1, -1, -1, 0, null);
        }

        private static TransferResult fromProbe(UpstreamRangeClient.ProbeResult probe) {
            Decision decision = switch (probe.decision()) {
                case FALLBACK_SINGLE_CONNECTION, BELOW_THRESHOLD, MISSING_VALIDATOR ->
                        Decision.FALLBACK_SINGLE_CONNECTION;
                case UNSATISFIABLE -> Decision.UNSATISFIABLE;
                case INVALID_RESPONSE -> Decision.INVALID_UPSTREAM;
                case PARALLEL -> throw new IllegalArgumentException("parallel probe requires a client range");
            };
            return of(decision, probe, null, 0);
        }

        private static TransferResult of(
                Decision decision,
                UpstreamRangeClient.ProbeResult probe,
                HttpByteRange range,
                long bytesWritten) {
            long start = range == null ? -1 : range.startInclusive();
            long end = range == null ? -1 : range.endInclusive();
            return new TransferResult(
                    decision,
                    probe.totalLength(),
                    start,
                    end,
                    bytesWritten,
                    probe);
        }
    }
}
