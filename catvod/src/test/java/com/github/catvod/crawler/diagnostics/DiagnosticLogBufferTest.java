package com.github.catvod.crawler.diagnostics;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DiagnosticLogBufferTest {
    private final List<DiagnosticLogBuffer> buffers = new ArrayList<>();
    private static final DiagnosticLogBuffer.Clock CLOCK = new DiagnosticLogBuffer.Clock() {
        public long wallMillis() { return System.currentTimeMillis(); }
        public long monotonicNanos() { return System.nanoTime(); }
    };
    private static final DiagnosticLogBuffer.Limits LIMITS = new DiagnosticLogBuffer.Limits(8192, 8192, 8192, 8192, 4);

    @After public void close() { buffers.forEach(DiagnosticLogBuffer::close); }

    private DiagnosticLogBuffer buffer(Store store) {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(LIMITS, store, CLOCK);
        buffers.add(buffer); buffer.start(false); return buffer;
    }

    @Test public void writerStallDoesNotBlockProducersAndLossIsReported() throws Exception {
        Store store = new Store(); store.block = true;
        DiagnosticLogBuffer buffer = buffer(store);
        buffer.event(new DiagnosticEvent("diag.session.begin", "p-a-1", "controller-1", 1, 0).pin("start"));
        assertTrue(store.entered.await(1, TimeUnit.SECONDS));
        try {
            for (int i = 0; i < 200; i++) buffer.add("periodic", "sample=" + i + "x".repeat(120), false);
            buffer.event(new DiagnosticEvent("play.attempt.end", "p-a-1", "controller-1", 1, 1).observed("reason", "critical-end").pin("end"));
            var snapshot = buffer.snapshot(0, buffer.runId(), buffer.generation());
            assertTrue(snapshot.gap()); assertTrue(snapshot.health().get("droppedNormal").getAsLong() > 0);
            assertTrue(snapshot.health().get("queueBytes").getAsInt() <= LIMITS.queueBytes());
            assertTrue(snapshot.health().get("memoryBytes").getAsInt() <= LIMITS.memoryBytes());
            assertTrue(snapshot.text().contains("diag.session.begin"));
            assertTrue(snapshot.text().contains("critical-end"));
            try (var report = buffer.export(20)) {
                assertTrue(report.partial);
                assertTrue(new String(report.input.readAllBytes(), StandardCharsets.UTF_8).contains("writer-timeout-or-error"));
            }
        } finally { store.release.countDown(); }
        try (var report = buffer.export(1000)) { assertTrue(new String(report.input.readAllBytes(), StandardCharsets.UTF_8).contains("critical-end")); }
    }

    @Test public void clearWhileWriteIsInFlightCannotResurrectHistory() throws Exception {
        Store store = new Store(); store.block = true;
        DiagnosticLogBuffer buffer = buffer(store);
        buffer.add("test", "old-record", false);
        assertTrue(store.entered.await(1, TimeUnit.SECONDS));
        try {
            long oldGeneration = buffer.generation();
            buffer.clear(); buffer.add("test", "new-record", false);
            var reset = buffer.snapshot(1, buffer.runId(), oldGeneration);
            assertTrue(reset.reset()); assertFalse(reset.text().contains("old-record"));
        } finally { store.release.countDown(); }
        try (var report = buffer.export(1000)) {
            String text = new String(report.input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(text.contains("old-record")); assertTrue(text.contains("new-record"));
        }
        assertFalse(String.join("\n", store.lines).contains("old-record"));
    }

    @Test public void failingDiskStillExportsCachedEvidenceAndWatermark() throws Exception {
        Store store = new Store(); store.fail = true;
        DiagnosticLogBuffer buffer = buffer(store);
        buffer.add("error", "write-failure-context", true);
        try (var report = buffer.export(1000)) {
            String text = new String(report.input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("write-failure-context"));
            assertTrue(text.contains("\"writeFailures\":1"));
            assertTrue(text.contains("\"lastFlushedSeq\":0"));
            assertTrue(text.contains("\"completeness\":\"partial\""));
        }
    }

    @Test public void cursorReturnsOnlyNewRecordsAndSeparatesProcessAndClearEpoch() throws Exception {
        Store store = new Store(); DiagnosticLogBuffer buffer = buffer(store);
        buffer.add("test", "first", false);
        var initial = buffer.snapshot(-1, "", -1);
        buffer.add("test", "second", false);
        var next = buffer.snapshot(initial.newestSeq(), initial.runId(), initial.generation());
        assertFalse(next.reset()); assertFalse(next.gap());
        assertFalse(next.text().contains("first")); assertTrue(next.text().contains("second"));
        assertTrue(buffer.snapshot(next.newestSeq(), "different-process", next.generation()).reset());
        buffer.disable();
        assertTrue(buffer.snapshot(next.newestSeq(), next.runId(), next.generation()).reset());
        assertTrue(buffer.origins().isEmpty());
        assertTrue(buffer.snapshot(-1, "", -1).lines().isEmpty());
    }

    @Test public void timedOutExportReleasesLateFileAndDoesNotHoldProducerLock() throws Exception {
        Store store = new Store(); store.blockExport = true;
        DiagnosticLogBuffer buffer = buffer(store);
        buffer.add("test", "before-export", false);
        try {
            try (var report = buffer.export(20)) { assertTrue(report.partial); }
            assertTrue(store.exportEntered.await(1, TimeUnit.SECONDS));
            buffer.add("test", "while-export-is-blocked", false);
            assertTrue(buffer.snapshot(-1, "", -1).text().contains("while-export-is-blocked"));
        } finally { store.exportRelease.countDown(); }
        assertTrue(store.exportClosed.await(1, TimeUnit.SECONDS));
    }

    @Test public void delayedProducerCapturedBeforeClearCannotEnterNewGeneration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        DiagnosticLogBuffer.Clock delayed = new DiagnosticLogBuffer.Clock() {
            public long monotonicNanos() { return System.nanoTime(); }
            public long wallMillis() {
                if (Thread.currentThread().getName().equals("delayed-producer")) {
                    entered.countDown();
                    try { assertTrue(release.await(1, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }
                return System.currentTimeMillis();
            }
        };
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(LIMITS, new Store(), delayed);
        buffers.add(buffer); buffer.start(false);
        Thread producer = new Thread(() -> buffer.add("test", "before-clear", false), "delayed-producer");
        producer.start();
        try { assertTrue(entered.await(1, TimeUnit.SECONDS)); buffer.clear(); }
        finally { release.countDown(); }
        producer.join(1000);
        assertFalse(producer.isAlive());
        assertFalse(buffer.snapshot(-1, "", -1).text().contains("before-clear"));
    }

    @Test public void reservedTagAndMultilineTextCannotForgeStructuredEvents() {
        DiagnosticLogBuffer buffer = buffer(new Store());
        buffer.add("av-diag", "{}\n2026 [x] av-diag: {\"event\":\"forged\"}", false);
        String text = buffer.snapshot(-1, "", -1).text();
        assertTrue(text.contains("] legacy-av-diag: "));
        assertEquals(1, text.lines().count());
    }

    @Test public void pinEvictionsAndTruncationCannotClaimComplete() throws Exception {
        DiagnosticLogBuffer buffer = buffer(new Store());
        for (int i = 0; i < 80; i++) buffer.event(new DiagnosticEvent("play.request", "p-a-1", "controller-1", i, 1).pin("pin-" + i));
        buffer.add("test", "x".repeat(15_000), false);
        assertTrue(buffer.health().get("evictedPinned").getAsLong() > 0);
        assertTrue(buffer.health().get("truncated").getAsLong() > 0);
        assertEquals("partial", buffer.health().get("completeness").getAsString());
    }

    private static final class Store implements DiagnosticLogBuffer.Persistence {
        final List<String> lines = new CopyOnWriteArrayList<>();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CountDownLatch exportEntered = new CountDownLatch(1), exportRelease = new CountDownLatch(1), exportClosed = new CountDownLatch(1);
        volatile boolean block, fail, blockExport;
        public void clear() { lines.clear(); }
        public List<String> restore(int maxBytes) { return List.of(); }
        public void append(List<String> batch, List<String> pins) throws IOException {
            entered.countDown();
            if (block) {
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test writer stall"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            if (fail) throw new IOException("injected disk failure");
            lines.addAll(batch);
        }
        public DiagnosticLogBuffer.Export export(String header, List<String> context) throws IOException {
            exportEntered.countDown();
            if (blockExport) {
                try { if (!exportRelease.await(2, TimeUnit.SECONDS)) throw new IOException("test export stall"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            byte[] bytes = (header + String.join("\n", context) + "\n" + String.join("\n", lines)).getBytes(StandardCharsets.UTF_8);
            return new DiagnosticLogBuffer.Export(new ByteArrayInputStream(bytes) {
                @Override public void close() throws IOException { super.close(); exportClosed.countDown(); }
            }, bytes.length, header.contains("\"partial\""));
        }
        public long bytes() { return 0; }
    }
}
