package com.github.catvod.crawler.diagnostics;

import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Called exclusively by the log writer, except for closing an immutable export stream. */
public final class RollingDiagnosticFile implements DiagnosticLogBuffer.Persistence {
    public static final String FILE_NAME = "webhtv-debug-log.txt";
    private final File directory;
    private final DiagnosticLogBuffer.Limits limits;
    private final AtomicBoolean exportLeased = new AtomicBoolean();
    private List<String> savedPins = List.of();
    private long bytes, rotations;
    private volatile long exportBytes;

    public RollingDiagnosticFile(File directory, DiagnosticLogBuffer.Limits limits) { this.directory = directory; this.limits = limits; }
    private File segment(int index) { return new File(directory, FILE_NAME + (index == 0 ? "" : "." + index)); }
    private File pins() { return new File(directory, FILE_NAME + ".pinned"); }
    private File exportFile() { return new File(directory, FILE_NAME + ".export"); }

    private void ensureDirectory() throws IOException {
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) throw new IOException("Diagnostic directory unavailable");
    }

    @Override public void clear() throws IOException {
        ensureDirectory();
        for (int i = 0; i < limits.segments(); i++) { delete(segment(i)); delete(new File(segment(i).getPath() + ".new")); }
        delete(pins()); delete(new File(pins().getPath() + ".new"));
        if (!exportLeased.get()) delete(exportFile());
        savedPins = List.of(); bytes = rotations = 0;
    }

    @Override public List<String> restore(int maxBytes) throws IOException {
        ensureDirectory();
        if (!exportLeased.get()) delete(exportFile());
        ArrayDeque<String> recent = new ArrayDeque<>();
        int[] cost = {0};
        // Rewrite legacy files before exposing them: old builds may have persisted credentials.
        for (int i = limits.segments() - 1; i >= 0; i--) {
            List<String> safe = readSanitizedTail(segment(i), limits.segmentBytes());
            if (segment(i).exists()) replace(segment(i), safe);
            for (String line : safe) retain(recent, line, maxBytes, cost);
        }
        List<String> oldPins = readSanitizedTail(pins(), limits.pinnedBytes());
        if (pins().exists()) replace(pins(), oldPins);
        for (String line : oldPins) retain(recent, line, maxBytes, cost);
        updateBytes();
        return new ArrayList<>(recent);
    }

    private List<String> readSanitizedTail(File file, int budget) throws IOException {
        if (!file.isFile()) return List.of();
        ArrayDeque<String> result = new ArrayDeque<>();
        int[] cost = {0};
        try (FileInputStream raw = new FileInputStream(file)) {
            long offset = Math.max(0, raw.getChannel().size() - budget);
            raw.getChannel().position(offset);
            InputStream input = new BufferedInputStream(raw);
            if (offset > 0) { int c; while ((c = input.read()) != -1 && c != '\n') {} }
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            boolean longLine = false;
            int c;
            while ((c = input.read()) != -1) {
                if (c == '\n') {
                    restoreLine(result, line, longLine, budget, cost);
                    line.reset(); longLine = false;
                } else if (line.size() < 48_000) line.write(c);
                else longLine = true;
            }
            if (line.size() > 0 || longLine) restoreLine(result, line, longLine, budget, cost);
        }
        return new ArrayList<>(result);
    }

    private void restoreLine(ArrayDeque<String> lines, ByteArrayOutputStream bytes, boolean truncated, int budget, int[] cost) {
        String line = DiagnosticText.clean(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).text();
        if (line.isEmpty()) return;
        if (!line.startsWith("# restored-history ")) line = "# restored-history " + line;
        if (truncated) line += " [truncated history]";
        retain(lines, line, budget, cost);
    }

    private static void retain(ArrayDeque<String> lines, String line, int budget, int[] cost) {
        lines.addLast(line); cost[0] += line.getBytes(StandardCharsets.UTF_8).length + 192;
        while (cost[0] > budget && !lines.isEmpty()) cost[0] -= lines.removeFirst().getBytes(StandardCharsets.UTF_8).length + 192;
    }

    @Override public void append(List<String> lines, List<String> pinned) throws IOException {
        ensureDirectory();
        OutputStream output = null;
        try {
            long length = segment(0).length();
            for (String line : lines) {
                byte[] data = (line + "\n").getBytes(StandardCharsets.UTF_8);
                if (data.length > limits.segmentBytes()) throw new IOException("Diagnostic entry exceeds segment budget");
                if (length + data.length > limits.segmentBytes()) {
                    if (output != null) { output.close(); output = null; }
                    rotate(); length = 0;
                }
                if (output == null) output = new BufferedOutputStream(new FileOutputStream(segment(0), true));
                output.write(data); length += data.length;
            }
        } finally { if (output != null) output.close(); }
        if (!savedPins.equals(pinned)) { replace(pins(), pinned); savedPins = List.copyOf(pinned); }
        updateBytes();
    }

    private void rotate() throws IOException {
        rotations++;
        delete(segment(limits.segments() - 1));
        for (int i = limits.segments() - 2; i >= 0; i--) {
            File from = segment(i);
            if (from.exists() && !from.renameTo(segment(i + 1))) throw new IOException("Diagnostic rotation failed");
        }
    }

    private void replace(File target, List<String> lines) throws IOException {
        File temp = new File(target.getPath() + ".new");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
            for (String line : lines) output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (!temp.renameTo(target)) throw new IOException("Diagnostic snapshot replacement failed");
    }

    @Override public DiagnosticLogBuffer.Export export(String header, List<String> pinned) throws IOException {
        ensureDirectory();
        if (!exportLeased.compareAndSet(false, true)) throw new IOException("Diagnostic export already in use");
        File report = exportFile();
        try {
            MessageDigest digest = sha256();
            long count = 0;
            try (OutputStream file = new BufferedOutputStream(new FileOutputStream(report))) {
                DigestOutputStream hashed = new DigestOutputStream(file, digest);
                byte[] preamble = (header + "# Pinned snapshots and cached capture window (may also occur in timeline)\n" + String.join("\n", pinned) + "\n# Retained timeline\n").getBytes(StandardCharsets.UTF_8);
                hashed.write(preamble); count += preamble.length;
                byte[] buffer = new byte[8192];
                for (int i = limits.segments() - 1; i >= 0; i--) {
                    if (!segment(i).isFile()) continue;
                    try (InputStream input = new FileInputStream(segment(i))) {
                        int read;
                        long copied = 0;
                        while ((read = input.read(buffer, 0, (int) Math.min(buffer.length, limits.segmentBytes() - copied))) > 0) {
                            hashed.write(buffer, 0, read); count += read; copied += read;
                        }
                    }
                }
                hashed.flush();
                file.write(("# av-diag-manifest {\"schemaVersion\":1,\"hashedBytes\":" + count + ",\"sha256\":\"" + hex(digest.digest())
                        + "\",\"hashScope\":\"all-preceding-bytes\"}\n").getBytes(StandardCharsets.UTF_8));
            }
            InputStream stream = new FilterInputStream(new FileInputStream(report)) {
                private boolean closed;
                @Override public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    try { super.close(); } finally {
                        try { delete(report); } finally { exportBytes = 0; exportLeased.set(false); }
                    }
                }
            };
            exportBytes = report.length();
            return new DiagnosticLogBuffer.Export(stream, exportBytes, header.contains("\"completeness\":\"partial\""), () -> new FileInputStream(report));
        } catch (IOException | RuntimeException error) {
            try { delete(report); } catch (IOException ignored) {}
            exportLeased.set(false);
            throw error;
        }
    }

    private void updateBytes() {
        bytes = pins().length();
        for (int i = 0; i < limits.segments(); i++) bytes += segment(i).length();
    }
    @Override public long bytes() { return bytes + exportBytes; }
    @Override public long extraDiskBudgetBytes() { return (long) limits.segmentBytes() * limits.segments() + limits.memoryBytes() + limits.pinnedBytes() + (640L << 10); }
    @Override public long rotations() { return rotations; }

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return text.toString();
    }

    private static void delete(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Diagnostic file deletion failed");
    }
}
