package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PreCacheThreadExceptionHandlerTest {

    @Test
    public void runtimeExceptionIsContainedAndReported() throws Exception {
        IndexOutOfBoundsException failure = new IndexOutOfBoundsException("empty HLS group");
        AtomicReference<Thread> failedThread = new AtomicReference<>();
        AtomicReference<RuntimeException> reported = new AtomicReference<>();
        AtomicReference<Throwable> delegated = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            throw failure;
        });
        thread.setUncaughtExceptionHandler(new PreCacheThreadExceptionHandler((source, error) -> {
            failedThread.set(source);
            reported.set(error);
        }, (source, error) -> delegated.set(error)));

        thread.start();
        thread.join();

        assertSame(thread, failedThread.get());
        assertSame(failure, reported.get());
        assertNull(delegated.get());
    }

    @Test
    public void fatalErrorIsDelegated() throws Exception {
        AssertionError failure = new AssertionError("fatal worker failure");
        AtomicReference<RuntimeException> reported = new AtomicReference<>();
        AtomicReference<Thread> delegatedThread = new AtomicReference<>();
        AtomicReference<Throwable> delegated = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            throw failure;
        });
        thread.setUncaughtExceptionHandler(new PreCacheThreadExceptionHandler((source, error) -> reported.set(error), (source, error) -> {
            delegatedThread.set(source);
            delegated.set(error);
        }));

        thread.start();
        thread.join();

        assertNull(reported.get());
        assertSame(thread, delegatedThread.get());
        assertSame(failure, delegated.get());
    }

    @Test
    public void recoveryFailureIsDelegatedWithOriginalFailure() throws Exception {
        IndexOutOfBoundsException original = new IndexOutOfBoundsException("empty HLS group");
        IllegalStateException recoveryFailure = new IllegalStateException("recovery failed");
        AtomicReference<Throwable> delegated = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            throw original;
        });
        thread.setUncaughtExceptionHandler(new PreCacheThreadExceptionHandler((source, error) -> {
            throw recoveryFailure;
        }, (source, error) -> delegated.set(error)));

        thread.start();
        thread.join();

        assertSame(recoveryFailure, delegated.get());
        assertSame(original, recoveryFailure.getSuppressed()[0]);
    }
}
