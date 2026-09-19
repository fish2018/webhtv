package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Same log writer owns the bounded pre/post window and three local incident files. */
public final class IncidentDiagnosticFile implements DiagnosticLogBuffer.Persistence {
    private static final int RECENT_LIMIT = 256 << 10, INCIDENT_LIMIT = 768 << 10;
    private record Line(String text, long timeMs, int bytes) {}
    private final DiagnosticLogBuffer.Persistence delegate;
    private final File directory;
    private final ArrayDeque<Line> recent = new ArrayDeque<>();
    private final ArrayList<String> pending = new ArrayList<>();
    private int recentBytes, pendingBytes;
    private long firstMs, markMs, deadlineMs, diskBytes;
    private boolean partial;
    private volatile boolean active;
    private String trigger;
    private long checkpointMs;
    private final java.util.function.LongSupplier clock;

    public IncidentDiagnosticFile(DiagnosticLogBuffer.Persistence delegate, File directory) { this(delegate, directory, () -> System.nanoTime() / 1_000_000); }
    public IncidentDiagnosticFile(DiagnosticLogBuffer.Persistence delegate, File directory, java.util.function.LongSupplier clock) {
        this.delegate = delegate; this.directory = directory; this.clock = clock;
    }
    private File file(int i) { return new File(directory, "webhtv-diagnostic-incident-" + i + ".txt"); }
    private File pendingFile() { return new File(file(0).getPath() + ".new"); }

    @Override public void append(List<String> lines, List<String> pinned) throws IOException {
        delegate.append(lines, pinned);
        for (String text : lines) {
            long now = clock.getAsLong();
            JsonObject event = DiagnosticReport.parseEvent(text);
            boolean user = event != null && "diag.user-mark".equals(event.get("event").getAsString());
            boolean error = event != null && ("error".equals(event.get("level").getAsString()) || "fatal".equals(event.get("level").getAsString()));
            if (user || error && !active && (markMs == 0 || now - markMs >= 30_000)) {
                if (active) finish(now, true);
                active = true; partial = false; markMs = now; deadlineMs = now + 15_000; checkpointMs = -1; trigger = user ? "user-mark" : "error";
                pending.clear(); pendingBytes = 0;
                firstMs = recent.isEmpty() ? now : recent.getFirst().timeMs;
                for (String pin : pinned) add(pin);
                for (Line line : recent) add(line.text);
                partial |= now - firstMs < 30_000;
            }
            if (active) add(text);
            int cost = text.getBytes(StandardCharsets.UTF_8).length + 64;
            recent.addLast(new Line(text, now, cost)); recentBytes += cost;
            while (!recent.isEmpty() && (recentBytes > RECENT_LIMIT || now - recent.getFirst().timeMs > 30_000)) recentBytes -= recent.removeFirst().bytes;
        }
        tick();
    }

    private void add(String line) {
        int cost = line.getBytes(StandardCharsets.UTF_8).length + 1;
        if (pendingBytes + cost > INCIDENT_LIMIT) { partial = true; return; }
        pending.add(line); pendingBytes += cost;
    }

    @Override public boolean needsTick() { return active || delegate.needsTick(); }
    @Override public void tick() throws IOException {
        delegate.tick(); long now = clock.getAsLong();
        if (active && now >= deadlineMs) finish(now, false);
        else if (active && (checkpointMs < 0 || now - checkpointMs >= 1000)) writePending(now, true);
    }

    private void writePending(long now, boolean recording) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Incident directory unavailable");
        File temp = pendingFile();
        JsonObject meta = new JsonObject(); meta.addProperty("trigger", trigger); meta.addProperty("requestedBeforeMs", 30_000);
        meta.addProperty("requestedAfterMs", 15_000); meta.addProperty("retainedBeforeMs", Math.max(0, markMs - firstMs));
        meta.addProperty("retainedAfterMs", Math.max(0, Math.min(now, deadlineMs) - markMs));
        meta.addProperty("completeness", partial || recording ? "partial" : "declared-window");
        meta.addProperty("postWindowPending", recording);
        meta.addProperty("windowClock", "writer-arrival-monotonic-ms; original source/enqueue times retained per event");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp))) {
            writer.write("# incident-window " + meta + "\n");
            for (String line : pending) { writer.write(line); writer.write('\n'); }
        }
        checkpointMs = now; updateBytes();
    }

    private void finish(long now, boolean interrupted) throws IOException {
        partial |= interrupted;
        writePending(now, false);
        rotatePending(); active = false;
        pending.clear(); pendingBytes = 0; updateBytes();
    }

    private void rotatePending() throws IOException {
        delete(file(2));
        for (int i = 1; i >= 0; i--) if (file(i).exists() && !file(i).renameTo(file(i + 1))) throw new IOException("Incident rotation failed");
        if (!pendingFile().renameTo(file(0))) throw new IOException("Incident replacement failed");
    }

    @Override public DiagnosticLogBuffer.Export export(String header, List<String> pinned) throws IOException {
        ArrayList<String> context = new ArrayList<>(pinned); int retained = 0;
        for (int i = 0; i < 3; i++) if (file(i).isFile()) try (BufferedReader reader = new BufferedReader(new FileReader(file(i)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                retained += line.getBytes(StandardCharsets.UTF_8).length + 1;
                if (retained > (512 << 10)) { context.add("# incident-export {\"completeness\":\"partial\",\"reason\":\"incident-export-byte-budget\"}"); break; }
                context.add(line);
            }
            if (retained > (512 << 10)) break;
        }
        if (active) context.add("# incident-export {\"completeness\":\"partial\",\"reason\":\"post-window-still-recording\"}");
        return delegate.export(header, context);
    }

    @Override public List<String> restore(int maxBytes) throws IOException {
        // An interrupted post-window already has a partial header and its last checkpoint.
        if (pendingFile().isFile()) rotatePending();
        updateBytes(); return delegate.restore(maxBytes);
    }
    @Override public void clear() throws IOException {
        delegate.clear(); active = false; recent.clear(); pending.clear(); recentBytes = pendingBytes = 0;
        for (int i = 0; i < 3; i++) delete(file(i)); delete(new File(file(0).getPath() + ".new")); diskBytes = 0;
    }
    private void updateBytes() { diskBytes = pendingFile().length(); for (int i = 0; i < 3; i++) diskBytes += file(i).length(); }
    @Override public long bytes() { return delegate.bytes() + diskBytes; }
    @Override public long rotations() { return delegate.rotations(); }
    @Override public long extraDiskBudgetBytes() { return delegate.extraDiskBudgetBytes() + (4L << 20); }
    private static void delete(File file) throws IOException { if (file.exists() && !file.delete()) throw new IOException("Incident deletion failed"); }
}
