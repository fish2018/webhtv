package com.fongmi.android.tv.player.exo;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

final class PreCacheWorkerRecovery {

    interface Queue {

        boolean post(Runnable action);

        void enqueueRelease();

        void quitSafely();

        void drain();
    }

    interface FailureListener {

        void onFailure(RuntimeException error, boolean releasePhase);
    }

    enum Result {
        RELEASED,
        RELEASE_FAILED,
        QUEUE_REJECTED
    }

    static Result recover(Queue queue, ThreadPoolExecutor executor, FailureListener failureListener) {
        AtomicBoolean releaseStarted = new AtomicBoolean();
        AtomicBoolean releaseFinished = new AtomicBoolean();
        try {
            if (!queue.post(() -> releaseStarted.set(true))) {
                drainQuittingQueue(queue, failureListener);
                return Result.QUEUE_REJECTED;
            }
            try {
                queue.enqueueRelease();
            } catch (RuntimeException error) {
                reportFailure(failureListener, error, true);
                quitAfterEnqueueFailure(queue, failureListener);
                return Result.RELEASE_FAILED;
            }
            boolean markerAccepted = queue.post(() -> releaseFinished.set(true));
            queue.quitSafely();
            boolean releaseFailed = drain(queue, releaseStarted, releaseFinished, failureListener);
            if (!markerAccepted) return Result.QUEUE_REJECTED;
            if (!releaseFinished.get()) return Result.RELEASE_FAILED;
            return releaseFailed ? Result.RELEASE_FAILED : Result.RELEASED;
        } finally {
            if (executor != null) executor.shutdownNow();
        }
    }

    private static void drainQuittingQueue(Queue queue, FailureListener failureListener) {
        // Handler.post() rejects work once its MessageQueue is quitting, but due messages remain drainable.
        while (true) {
            try {
                queue.drain();
                return;
            } catch (RuntimeException error) {
                reportFailure(failureListener, error, false);
            }
        }
    }

    private static boolean drain(Queue queue, AtomicBoolean releaseStarted, AtomicBoolean releaseFinished, FailureListener failureListener) {
        boolean releaseFailed = false;
        while (true) {
            try {
                queue.drain();
                return releaseFailed;
            } catch (RuntimeException error) {
                boolean releasePhase = releaseStarted.get() && !releaseFinished.get();
                releaseFailed |= releasePhase;
                reportFailure(failureListener, error, releasePhase);
                if (releaseFinished.get()) return releaseFailed;
            }
        }
    }

    private static void quitAfterEnqueueFailure(Queue queue, FailureListener failureListener) {
        try {
            queue.quitSafely();
        } catch (RuntimeException error) {
            reportFailure(failureListener, error, false);
        }
    }

    private static void reportFailure(FailureListener listener, RuntimeException error, boolean releasePhase) {
        if (listener == null) return;
        try {
            listener.onFailure(error, releasePhase);
        } catch (RuntimeException ignored) {
            // Diagnostics must not interrupt resource cleanup.
        }
    }

    private PreCacheWorkerRecovery() {
    }
}
