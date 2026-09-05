package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BoundedAsyncRunnerTest {

    private static final NanoHTTPD DUMMY_SERVER = new NanoHTTPD(0) {
        @Override
        public Response serve(IHTTPSession session) {
            return newFixedLengthResponse("");
        }
    };

    @Test
    public void rejectsNonPositiveBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedAsyncRunner(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BoundedAsyncRunner(1, 0));
    }

    @Test
    public void closesConnectionWhenWorkerAndQueueAreFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        TrackingHandler active = new TrackingHandler(release);
        TrackingHandler queued = new TrackingHandler(release);
        TrackingHandler rejected = new TrackingHandler(release);
        BoundedAsyncRunner runner = new BoundedAsyncRunner(1, 1);
        try {
            runner.exec(active);
            assertTrue(active.awaitStarted());

            runner.exec(queued);
            runner.exec(rejected);

            assertTrue(rejected.awaitClosed());
            assertTrue(rejected.rejected.get());
            assertFalse(queued.started.get());
        } finally {
            release.countDown();
            runner.closeAll();
        }
    }

    private static final class TrackingHandler extends NanoHTTPD.ClientHandler implements BoundedAsyncRunner.OverloadHandler {

        private final CountDownLatch release;
        private final CountDownLatch startedLatch;
        private final CountDownLatch closedLatch;
        private final AtomicBoolean started;
        private final AtomicBoolean rejected;

        private TrackingHandler(CountDownLatch release) {
            DUMMY_SERVER.super(new ByteArrayInputStream(new byte[0]), new Socket());
            this.release = release;
            this.startedLatch = new CountDownLatch(1);
            this.closedLatch = new CountDownLatch(1);
            this.started = new AtomicBoolean();
            this.rejected = new AtomicBoolean();
        }

        @Override
        public void run() {
            started.set(true);
            startedLatch.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void rejectOverload() {
            rejected.set(true);
            close();
        }

        @Override
        public void close() {
            closedLatch.countDown();
        }

        private boolean awaitStarted() throws InterruptedException {
            return startedLatch.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitClosed() throws InterruptedException {
            return closedLatch.await(2, TimeUnit.SECONDS);
        }
    }
}
