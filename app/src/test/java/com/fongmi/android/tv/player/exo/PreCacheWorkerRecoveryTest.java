package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreCacheWorkerRecoveryTest {

    @Test
    public void queuedFailureDoesNotPreventReleaseAndExecutorShutdown() {
        RuntimeException queuedFailure = new IllegalStateException("stale worker message");
        AtomicBoolean released = new AtomicBoolean();
        FakeQueue queue = new FakeQueue(released::set);
        queue.enqueue(() -> {
            throw queuedFailure;
        });
        ThreadPoolExecutor executor = newExecutor();
        List<DrainFailure> failures = new ArrayList<>();

        PreCacheWorkerRecovery.Result result = PreCache.recoverWorkerResources(queue, executor, (error, releasePhase) -> failures.add(new DrainFailure(error, releasePhase)));

        assertEquals(PreCacheWorkerRecovery.Result.RELEASED, result);
        assertTrue(released.get());
        assertTrue(executor.isShutdown());
        assertEquals(2, queue.drainCalls);
        assertEquals(List.of(new DrainFailure(queuedFailure, false)), failures);
    }

    @Test
    public void releaseFailureIsReportedAfterQueueIsDrained() {
        RuntimeException releaseFailure = new IllegalStateException("release failed");
        FakeQueue queue = new FakeQueue(value -> {
            throw releaseFailure;
        });
        ThreadPoolExecutor executor = newExecutor();
        List<DrainFailure> failures = new ArrayList<>();

        PreCacheWorkerRecovery.Result result = PreCache.recoverWorkerResources(queue, executor, (error, releasePhase) -> failures.add(new DrainFailure(error, releasePhase)));

        assertEquals(PreCacheWorkerRecovery.Result.RELEASE_FAILED, result);
        assertTrue(executor.isShutdown());
        assertEquals(2, queue.drainCalls);
        assertEquals(List.of(new DrainFailure(releaseFailure, true)), failures);
    }

    @Test
    public void rejectedQueueDrainsReleaseQueuedByConcurrentStop() {
        AtomicBoolean released = new AtomicBoolean();
        FakeQueue queue = new FakeQueue(released::set);
        queue.enqueueRelease();
        queue.accepting = false;
        queue.quit = true;
        ThreadPoolExecutor executor = newExecutor();

        PreCacheWorkerRecovery.Result result = PreCache.recoverWorkerResources(queue, executor, (error, releasePhase) -> {});

        assertEquals(PreCacheWorkerRecovery.Result.QUEUE_REJECTED, result);
        assertTrue(executor.isShutdown());
        assertTrue(released.get());
        assertTrue(queue.quit);
        assertEquals(1, queue.drainCalls);
    }

    @Test
    public void synchronousReleaseEnqueueFailureStillQuitsAndShutsDown() {
        RuntimeException releaseFailure = new IllegalStateException("release enqueue failed");
        FakeQueue queue = new FakeQueue(value -> {});
        queue.enqueueFailure = releaseFailure;
        ThreadPoolExecutor executor = newExecutor();
        List<DrainFailure> failures = new ArrayList<>();

        PreCacheWorkerRecovery.Result result = PreCache.recoverWorkerResources(queue, executor, (error, releasePhase) -> failures.add(new DrainFailure(error, releasePhase)));

        assertEquals(PreCacheWorkerRecovery.Result.RELEASE_FAILED, result);
        assertTrue(queue.quit);
        assertEquals(0, queue.drainCalls);
        assertTrue(executor.isShutdown());
        assertEquals(List.of(new DrainFailure(releaseFailure, true)), failures);
    }

    @Test
    public void missingCompletionMarkerCannotReportReleaseSuccess() {
        FakeQueue queue = new FakeQueue(value -> {});
        queue.dropActionsOnDrain = true;
        ThreadPoolExecutor executor = newExecutor();

        PreCacheWorkerRecovery.Result result = PreCache.recoverWorkerResources(queue, executor, (error, releasePhase) -> {});

        assertEquals(PreCacheWorkerRecovery.Result.RELEASE_FAILED, result);
        assertTrue(executor.isShutdown());
    }

    private static ThreadPoolExecutor newExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private record DrainFailure(RuntimeException error, boolean releasePhase) {
    }

    private interface ReleaseAction {

        void run(boolean value);
    }

    private static final class FakeQueue implements PreCacheWorkerRecovery.Queue {

        private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        private final ReleaseAction releaseAction;
        private boolean accepting = true;
        private boolean dropActionsOnDrain;
        private boolean quit;
        private int drainCalls;
        private RuntimeException enqueueFailure;

        private FakeQueue(ReleaseAction releaseAction) {
            this.releaseAction = releaseAction;
        }

        private void enqueue(Runnable action) {
            actions.add(action);
        }

        @Override
        public boolean post(Runnable action) {
            if (!accepting) return false;
            actions.add(action);
            return true;
        }

        @Override
        public void enqueueRelease() {
            if (enqueueFailure != null) throw enqueueFailure;
            if (accepting) actions.add(() -> releaseAction.run(true));
        }

        @Override
        public void quitSafely() {
            quit = true;
            accepting = false;
        }

        @Override
        public void drain() {
            drainCalls++;
            if (dropActionsOnDrain) {
                actions.clear();
                return;
            }
            while (!actions.isEmpty()) actions.removeFirst().run();
        }
    }
}
