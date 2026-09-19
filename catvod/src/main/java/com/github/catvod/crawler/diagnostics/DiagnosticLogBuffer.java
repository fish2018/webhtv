package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** One disk owner. The producer lock never covers I/O, flush, export or user callbacks. */
public final class DiagnosticLogBuffer implements AutoCloseable {
    public record Limits(int queueBytes, int memoryBytes, int pinnedBytes, int segmentBytes, int segments) {
        public static Limits standard() { return new Limits(1 << 20, 1 << 20, 256 << 10, 4 << 20, 4); }
        public Limits {
            if (queueBytes < 1024 || memoryBytes < 1024 || pinnedBytes < 1024 || segmentBytes < 1024 || segments < 1 || segments > 4)
                throw new IllegalArgumentException("Invalid diagnostic budget");
        }
    }

    public interface Clock {
        long wallMillis();
        long monotonicNanos();
        default int processId() { return -1; }
    }

    public interface Persistence {
        void clear() throws IOException;
        List<String> restore(int maxBytes) throws IOException;
        void append(List<String> lines, List<String> pinned) throws IOException;
        Export export(String header, List<String> pinned) throws IOException;
        long bytes();
        default long rotations() { return 0; }
        default long extraDiskBudgetBytes() { return 0; }
        /** Constant-time cached flag, no I/O; allows a pending incident to finish in a silent tail. */
        default boolean needsTick() { return false; }
        default void tick() throws IOException {}
    }

    public static final class Export implements AutoCloseable {
        public interface Opener { InputStream open() throws IOException; }
        public final InputStream input;
        public final long length;
        public final boolean partial;
        private final Opener opener;
        public Export(InputStream input, long length, boolean partial) {
            this(input, length, partial, null);
        }
        public Export(InputStream input, long length, boolean partial, Opener opener) {
            this.input = input; this.length = length; this.partial = partial; this.opener = opener;
        }
        public InputStream openAgain() throws IOException { if (opener == null) throw new IOException("Snapshot cannot be reopened"); return opener.open(); }
        @Override public void close() throws IOException { input.close(); }
    }

    public record Snapshot(String runId, long generation, long version, long oldestSeq, long newestSeq,
                           boolean reset, boolean gap, List<String> lines, JsonObject health) {
        public String text() { return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n"); }
    }

    private record Entry(long seq, long generation, String line, int cost, boolean critical) {}
    private record ExportRequest(long generation, long target, CompletableFuture<Export> future) {}
    private static final ThreadLocal<SimpleDateFormat> FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));
    private final Object lock = new Object();
    private final Limits limits;
    private final Persistence persistence;
    private final Clock clock;
    private final String runId = UUID.randomUUID().toString();
    private final AtomicLong sourceSequence = new AtomicLong();
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private final ArrayDeque<Entry> memory = new ArrayDeque<>();
    private final LinkedHashMap<String, Entry> pinned = new LinkedHashMap<>();
    private final LinkedHashSet<String> origins = new LinkedHashSet<>();
    private final LinkedHashMap<String, JsonObject> collectorHealth = new LinkedHashMap<>();
    private final LinkedHashSet<String> partialCollectors = new LinkedHashSet<>();
    private Thread writer;
    private volatile boolean enabled;
    private boolean stopped, clearPending, restorePending, restoredHistory;
    private int queueBytes, memoryBytes, pinnedBytes, highWatermark;
    private volatile long generation = 1;
    private long sequence, version, droppedNormal, droppedCritical, evictedMemory, evictedPinned;
    private long truncated, writeFailures, lastFlushedSeq, lastProcessedSeq, persistedBytes, collectorFailures;
    private long capturedAtNanos, lastWriterProgressNanos, rotatedSegments, exportFailures;
    private String lastWriteError = "none";
    private ExportRequest exportRequest;

    public DiagnosticLogBuffer(Limits limits, Persistence persistence, Clock clock) {
        this.limits = limits; this.persistence = persistence; this.clock = clock;
    }

    public boolean isEnabled() { return enabled; }
    public String runId() { return runId; }
    public long version() { synchronized (lock) { return version; } }
    public long generation() { return generation; }

    public void start(boolean restore) {
        synchronized (lock) {
            if (stopped) return;
            if (!enabled) {
                enabled = true;
                capturedAtNanos = clock.monotonicNanos();
                if (restore && writer == null) restorePending = true;
                else clearLocked();
                if (writer == null) {
                    writer = new Thread(this::runWriter, "webhtv-diagnostic-writer");
                    writer.setDaemon(true);
                    writer.start();
                }
            }
            lock.notifyAll();
        }
    }

    public void disable() { synchronized (lock) { enabled = false; clearLocked(); } }
    public void clear() { synchronized (lock) { clearLocked(); } }

    private void clearLocked() {
        generation++;
        queue.clear(); memory.clear(); pinned.clear(); origins.clear(); collectorHealth.clear(); partialCollectors.clear();
        queueBytes = memoryBytes = pinnedBytes = highWatermark = 0;
        droppedNormal = droppedCritical = evictedMemory = evictedPinned = truncated = writeFailures = collectorFailures = exportFailures = 0;
        lastFlushedSeq = lastProcessedSeq = 0;
        capturedAtNanos = clock.monotonicNanos();
        restoredHistory = restorePending = false;
        lastWriteError = "none";
        clearPending = true;
        if (exportRequest != null) { exportRequest.future.cancel(false); exportRequest = null; }
        version++;
        lock.notifyAll();
    }

    public void add(String tag, String message, boolean critical) {
        if (!enabled || message == null || message.isEmpty()) return;
        long captured = clock.monotonicNanos();
        long epoch = generation();
        long sourceSeq = sourceSequence.incrementAndGet();
        DiagnosticText.Clean clean = DiagnosticText.clean(message);
        String safeTag = DiagnosticText.clean(tag == null ? "Debug" : tag).text().replace(':', '_').replace('[', '_').replace(']', '_');
        if (safeTag.length() > 64) safeTag = safeTag.substring(0, 64);
        // Reserve the structured tag; arbitrary Spider/native text cannot impersonate events.
        if ("av-diag".equals(safeTag)) safeTag = "legacy-av-diag";
        offer(safeTag, clean.text(), null, critical, false, captured, sourceSeq, clean.truncated(), DiagnosticText.origins(message), epoch);
    }

    public void event(DiagnosticEvent event) {
        if (!enabled || event == null) return;
        long captured = clock.monotonicNanos();
        long epoch = generation();
        long sourceSeq = sourceSequence.incrementAndGet();
        try {
            offer("av-diag", event.json(), event.pinKey(), event.critical(), true, captured, sourceSeq, event.truncated(), List.of(), epoch);
        } catch (RuntimeException error) {
            collectorFailure(); // Diagnostics cannot throw into a player callback.
        }
    }

    public void collectorFailure() { synchronized (lock) { collectorFailures++; version++; } }

    /** Crash path only. Releases the producer lock while waiting and never does caller-thread I/O. */
    public void flushBestEffort(long timeoutMs) {
        if (Thread.currentThread() == writer) return;
        long deadline = System.nanoTime() + Math.min(150, Math.max(0, timeoutMs)) * 1_000_000;
        synchronized (lock) {
            long target = sequence;
            lock.notifyAll();
            while (enabled && lastProcessedSeq < target) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { lock.wait(Math.max(1, remaining / 1_000_000)); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    public void collectorHealth(String id, DiagnosticEvent event, boolean partial, long captureGeneration) {
        if (!enabled) return;
        try {
            JsonObject value = com.google.gson.JsonParser.parseString(event.json()).getAsJsonObject();
            synchronized (lock) {
                if (!enabled || generation != captureGeneration) return;
                collectorHealth.put(id, value);
                if (partial) partialCollectors.add(id); else partialCollectors.remove(id);
                while (collectorHealth.size() > 16) {
                    String oldest = collectorHealth.keySet().iterator().next();
                    collectorHealth.remove(oldest); partialCollectors.remove(oldest);
                }
                version++;
            }
        } catch (RuntimeException ignored) { collectorFailure(); }
    }

    private void offer(String tag, String text, String pinKey, boolean critical, boolean structured,
                       long captured, long sourceSeq, boolean wasTruncated, List<String> foundOrigins, long epoch) {
        long wallTime = clock.wallMillis();
        String thread = DiagnosticText.clean(Thread.currentThread().getName()).text();
        if (thread.length() > 64) thread = thread.substring(0, 64);
        String jsonThread = new JsonPrimitive(thread).toString();
        String timezone = new JsonPrimitive(TimeZone.getDefault().getID()).toString();
        int pid = clock.processId();
        String prefix = FORMAT.get().format(new Date(wallTime)) + " [" + thread.replace('[', '_').replace(']', '_') + "] " + tag + ": ";
        synchronized (lock) {
            if (!enabled || stopped || epoch != generation) return;
            long seq = ++sequence;
            if (structured) {
                text = text.substring(0, text.length() - 1) + ",\"processRunId\":\"" + runId + "\",\"processRole\":\"app\",\"seq\":" + seq
                        + ",\"sourceSeq\":" + sourceSeq + ",\"wallTime\":" + wallTime + ",\"timezone\":" + timezone + ",\"thread\":" + jsonThread
                        + ",\"elapsedRealtimeNs\":" + captured + ",\"capturedAtNs\":" + captured + ",\"enqueuedAtNs\":" + clock.monotonicNanos()
                        + ",\"pid\":" + (pid < 0 ? "null" : pid) + ",\"pidStatus\":\"" + (pid < 0 ? "not-collected" : "known")
                        + "\",\"captureGeneration\":" + generation + "}";
            }
            else text += " [logSeq=" + seq + "]";
            String line = prefix + text;
            Entry entry = new Entry(seq, generation, line, line.getBytes(StandardCharsets.UTF_8).length + 192, critical);
            if (wasTruncated || text.contains("[truncated")) truncated++;
            for (String origin : foundOrigins) {
                origins.add(origin);
                if (origins.size() > 200) origins.remove(origins.iterator().next());
            }
            memory.addLast(entry); memoryBytes += entry.cost;
            while (memoryBytes > limits.memoryBytes && !memory.isEmpty()) { memoryBytes -= memory.removeFirst().cost; evictedMemory++; }
            if (pinKey != null) {
                Entry old = pinned.remove(pinKey);
                if (old != null) pinnedBytes -= old.cost;
                pinned.put(pinKey, entry); pinnedBytes += entry.cost;
                while (pinnedBytes > limits.pinnedBytes && !pinned.isEmpty()) {
                    String first = pinned.keySet().iterator().next();
                    pinnedBytes -= pinned.remove(first).cost; evictedPinned++;
                }
            }
            if (critical && queueBytes + entry.cost > limits.queueBytes) {
                for (Iterator<Entry> it = queue.iterator(); it.hasNext() && queueBytes + entry.cost > limits.queueBytes;) {
                    Entry candidate = it.next();
                    if (!candidate.critical) { it.remove(); queueBytes -= candidate.cost; droppedNormal++; }
                }
            }
            if (queueBytes + entry.cost <= limits.queueBytes) {
                queue.addLast(entry); queueBytes += entry.cost;
                highWatermark = Math.max(highWatermark, queueBytes);
            } else if (critical) droppedCritical++;
            else droppedNormal++;
            version++;
            lock.notifyAll();
        }
    }

    public List<String> origins() { synchronized (lock) { return new ArrayList<>(origins); } }

    public Snapshot snapshot(long afterSeq, String expectedRun, long expectedGeneration) {
        synchronized (lock) {
            long oldest = memory.isEmpty() ? sequence + 1 : memory.peekFirst().seq;
            boolean reset = !runId.equals(expectedRun) || expectedGeneration != generation || afterSeq > sequence;
            boolean gap = !reset && afterSeq >= 0 && afterSeq < oldest - 1;
            List<String> lines = new ArrayList<>();
            long start = reset || gap ? -1 : afterSeq;
            if (reset || gap) {
                for (Entry entry : pinned.values()) if (entry.seq < oldest) lines.add(entry.line);
            }
            for (Entry entry : memory) if (entry.seq > start) lines.add(entry.line);
            return new Snapshot(runId, generation, version, oldest, sequence, reset, gap, List.copyOf(lines), healthLocked("memory-window"));
        }
    }

    public JsonObject health() { synchronized (lock) { return healthLocked("memory-window"); } }

    private JsonObject healthLocked(String scope) {
        JsonObject health = new JsonObject();
        health.addProperty("schemaVersion", 1); health.addProperty("event", "diag.health");
        health.addProperty("processRunId", runId); health.addProperty("captureGeneration", generation);
        health.addProperty("scope", scope); health.addProperty("enabled", enabled);
        health.addProperty("lastCapturedSeq", sequence); health.addProperty("lastFlushedSeq", lastFlushedSeq);
        health.addProperty("lastProcessedSeq", lastProcessedSeq); health.addProperty("queueBytes", queueBytes);
        health.addProperty("queueLimitBytes", limits.queueBytes); health.addProperty("queueHighWatermark", highWatermark);
        health.addProperty("memoryBytes", memoryBytes); health.addProperty("memoryLimitBytes", limits.memoryBytes);
        health.addProperty("pinnedBytes", pinnedBytes); health.addProperty("pinnedLimitBytes", limits.pinnedBytes);
        health.addProperty("diskBytes", persistedBytes); health.addProperty("diskLimitBytes", (long) limits.segmentBytes * limits.segments + limits.pinnedBytes + persistence.extraDiskBudgetBytes());
        health.addProperty("droppedNormal", droppedNormal); health.addProperty("droppedCritical", droppedCritical);
        health.addProperty("evictedMemory", evictedMemory); health.addProperty("evictedPinned", evictedPinned);
        health.addProperty("truncated", truncated); health.addProperty("writeFailures", writeFailures);
        health.addProperty("rotatedSegments", rotatedSegments); health.addProperty("exportFailures", exportFailures);
        health.addProperty("lastWriteError", lastWriteError); health.addProperty("collectorFailures", collectorFailures);
        health.addProperty("clearPending", clearPending); health.addProperty("restorePending", restorePending);
        health.addProperty("restoredHistory", restoredHistory); health.addProperty("priorProcessCompleteness", "unknown");
        health.addProperty("captureStartedAtNs", capturedAtNanos);
        health.addProperty("writerLastProgressAtNs", lastWriterProgressNanos == 0 ? null : lastWriterProgressNanos);
        health.addProperty("writerProgressStatus", lastWriterProgressNanos == 0 ? "not-collected" : "known");
        health.addProperty("nativeOverflow", "not-collected");
        JsonObject collectors = new JsonObject();
        for (java.util.Map.Entry<String, JsonObject> entry : collectorHealth.entrySet()) collectors.add(entry.getKey(), entry.getValue().deepCopy());
        health.add("collectorHealth", collectors);
        boolean partial = droppedNormal + droppedCritical + evictedPinned + truncated + writeFailures + collectorFailures > 0
                || sequence > lastFlushedSeq || clearPending || restorePending || restoredHistory || rotatedSegments > 0
                || ("memory-window".equals(scope) && evictedMemory > 0) || !partialCollectors.isEmpty();
        health.addProperty("completeness", partial ? "partial" : "complete-within-declared-window");
        return health;
    }

    public Export export(long timeoutMillis) {
        CompletableFuture<Export> future = new CompletableFuture<>();
        long epoch;
        boolean accepted;
        synchronized (lock) {
            epoch = generation;
            accepted = enabled && writer != null && !stopped && exportRequest == null;
            if (accepted) {
                exportRequest = new ExportRequest(generation, sequence, future);
                lock.notifyAll();
            }
        }
        if (!accepted) return memoryExport(snapshot(-1, "", -1), "disabled-or-export-busy");
        try {
            Export export = future.get(Math.max(1, Math.min(timeoutMillis, 1000)), TimeUnit.MILLISECONDS);
            boolean current;
            synchronized (lock) { current = epoch == generation && enabled; }
            if (current) return export;
            export.close();
            return memoryExport(snapshot(-1, "", -1), "capture-reset-during-export");
        } catch (Exception error) {
            if (!future.cancel(false)) {
                // Completion can win the timeout race. The caller still owns cleanup in that case.
                try {
                    Export abandoned = future.getNow(null);
                    if (abandoned != null) abandoned.close();
                } catch (Exception ignored) { /* The fallback health already marks this export partial. */ }
            }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            // Use a fresh generation after clear; never return a pre-clear fallback.
            return memoryExport(snapshot(-1, "", -1), "writer-timeout-or-error");
        }
    }

    private Export memoryExport(Snapshot snapshot, String reason) {
        JsonObject health = snapshot.health.deepCopy();
        health.addProperty("completeness", "partial"); health.addProperty("exportFallback", reason);
        byte[] content = (header(health) + snapshot.text()).getBytes(StandardCharsets.UTF_8);
        String manifest = "# av-diag-manifest {\"schemaVersion\":1,\"hashedBytes\":" + content.length + ",\"sha256\":\""
                + RollingDiagnosticFile.hex(RollingDiagnosticFile.sha256().digest(content)) + "\",\"hashScope\":\"all-preceding-bytes\"}\n";
        byte[] bytes = (new String(content, StandardCharsets.UTF_8) + manifest).getBytes(StandardCharsets.UTF_8);
        return new Export(new ByteArrayInputStream(bytes), bytes.length, true, () -> new ByteArrayInputStream(bytes));
    }

    public static String header(JsonObject health) {
        return "# WebHTV AV diagnostics schema=1\n# READY / first-frame callback / accepted audio writes do not prove visible video or audible sound.\n"
                + "# Completeness covers only the declared retained window and available collectors.\n# av-diag-health " + health + "\n";
    }

    private void runWriter() {
        while (true) {
            List<Entry> batch = new ArrayList<>();
            List<String> pins;
            boolean clear, restore;
            long epoch;
            ExportRequest report = null;
            synchronized (lock) {
                while (!stopped && !clearPending && !restorePending && queue.isEmpty() && exportRequest == null) {
                    boolean timed = persistence.needsTick();
                    try { lock.wait(timed ? 1000 : 0); } catch (InterruptedException ignored) { if (stopped) return; }
                    if (timed) break;
                }
                if (stopped) return;
                epoch = generation; clear = clearPending; restore = restorePending;
                clearPending = restorePending = false;
                if (exportRequest != null && exportRequest.future.isCancelled()) exportRequest = null;
                if (!clear && !restore && exportRequest != null && (lastProcessedSeq >= exportRequest.target || queue.isEmpty())) {
                    report = exportRequest; exportRequest = null;
                }
                int batchBytes = 0;
                while (report == null && !queue.isEmpty() && batch.size() < 64 && batchBytes < 128 * 1024) {
                    Entry entry = queue.removeFirst(); queueBytes -= entry.cost;
                    batch.add(entry); batchBytes += entry.cost;
                }
                pins = pinnedLinesLocked();
            }
            try {
                if (clear) persistence.clear();
                if (restore) {
                    List<String> history = persistence.restore(limits.memoryBytes);
                    synchronized (lock) {
                        if (epoch == generation && enabled) {
                            restoredHistory = !history.isEmpty();
                            // Prior-process records stay explicitly separate from new observations.
                            ArrayDeque<Entry> recovered = new ArrayDeque<>();
                            int size = 0;
                            for (String line : history) {
                                Entry entry = new Entry(0, epoch, line, line.getBytes(StandardCharsets.UTF_8).length + 192, false);
                                recovered.addLast(entry); size += entry.cost;
                            }
                            for (Entry entry : memory) { recovered.addLast(entry); size += entry.cost; }
                            memory.clear(); memory.addAll(recovered); memoryBytes = size;
                            while (memoryBytes > limits.memoryBytes && !memory.isEmpty()) memoryBytes -= memory.removeFirst().cost;
                            version++;
                        }
                    }
                }
                boolean current;
                synchronized (lock) { current = epoch == generation; }
                if (current && !batch.isEmpty()) {
                    persistence.append(batch.stream().map(Entry::line).toList(), pins);
                    synchronized (lock) { if (epoch == generation) lastFlushedSeq = batch.get(batch.size() - 1).seq; }
                }
                if (report != null && !report.future.isCancelled()) completeExport(report);
                if (current) persistence.tick();
                synchronized (lock) {
                    if (epoch == generation) {
                        if (!batch.isEmpty()) lastProcessedSeq = batch.get(batch.size() - 1).seq;
                        persistedBytes = persistence.bytes();
                        rotatedSegments = persistence.rotations();
                        lastWriterProgressNanos = clock.monotonicNanos(); version++;
                        lock.notifyAll();
                    }
                }
            } catch (Exception error) {
                synchronized (lock) {
                    if (epoch == generation) {
                        if (report == null) { writeFailures++; lastWriteError = error.getClass().getSimpleName(); }
                        else exportFailures++;
                        if (!batch.isEmpty()) lastProcessedSeq = batch.get(batch.size() - 1).seq;
                        version++;
                    }
                    lock.notifyAll();
                }
                if (report != null) report.future.completeExceptionally(error);
            }
        }
    }

    private void completeExport(ExportRequest request) throws IOException {
        JsonObject health;
        List<String> pins;
        synchronized (lock) {
            if (request.generation != generation || !enabled) { request.future.cancel(false); return; }
            health = healthLocked("retained-disk-segments");
            LinkedHashSet<String> context = new LinkedHashSet<>(pinnedLinesLocked());
            for (Entry entry : memory) context.add(entry.line);
            pins = new ArrayList<>(context);
        }
        Export export = persistence.export(header(health), pins);
        boolean discard;
        synchronized (lock) {
            if (request.generation != generation || !enabled) request.future.cancel(false);
            discard = !request.future.complete(export);
        }
        if (discard) export.close();
    }

    private List<String> pinnedLinesLocked() { return pinned.values().stream().map(Entry::line).toList(); }

    @Override public void close() {
        synchronized (lock) {
            stopped = true; enabled = false;
            if (exportRequest != null) exportRequest.future.cancel(false);
            lock.notifyAll();
        }
    }
}
