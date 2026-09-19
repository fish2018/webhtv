package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Writer-owned, bounded recovery facts outside cacheDir. No I/O or callbacks on player/HTTP threads. */
public final class JournaledDiagnosticFile implements DiagnosticLogBuffer.Persistence {
    private static final int MAX_BYTES = 64 * 1024;
    private static final Set<String> EVENTS = Set.of("diag.session.begin", "play.attempt.end", "diag.error", "mpv.output.failure",
            "video.first-output", "video.output.summary", "audio.output.playhead", "play.clock", "process.recovery", "ijk.event");
    private static final Set<String> FACTS = Set.of("videoEvidenceLevel", "audioEvidenceLevel", "headFrames", "headDeltaFrames",
            "submittedBuffers", "outputBuffers", "positionMs", "state", "videoPartialFailure", "firstError", "lastError",
            "errorId", "domain", "stage", "javaClass", "errorCode", "reason", "missingEvidence");
    private final DiagnosticLogBuffer.Persistence logs;
    private final File journal;
    private final JsonObject sessions = new JsonObject();
    private String run = "none", recovery = "";
    private long lastWriteNs, evicted;
    private long journalBytes;
    private boolean dirty;

    public JournaledDiagnosticFile(DiagnosticLogBuffer.Persistence logs, File filesDirectory) {
        this.logs = logs; journal = new File(filesDirectory, "webhtv-diagnostics.journal");
    }

    @Override public void clear() throws IOException {
        IOException failure = null;
        try { logs.clear(); } catch (IOException error) { failure = error; }
        sessions.entrySet().clear(); recovery = ""; dirty = false; evicted = 0; journalBytes = 0;
        if (journal.exists() && !journal.delete()) failure = new IOException("Diagnostic journal clear failed");
        File temporary = new File(journal.getPath() + ".new");
        if (temporary.exists() && !temporary.delete()) failure = new IOException("Diagnostic journal temporary clear failed");
        if (failure != null) throw failure;
    }

    @Override public List<String> restore(int maxBytes) throws IOException {
        List<String> result = new ArrayList<>();
        try { result.addAll(logs.restore(maxBytes)); }
        catch (IOException error) { result.add("# process.recovery cached timeline read-error; private journal recovery follows"); }
        if (!journal.isFile()) return result;
        try (FileInputStream input = new FileInputStream(journal)) {
            if (journal.length() > MAX_BYTES) throw new IOException("Oversized journal");
            byte[] bytes = new byte[(int) journal.length()];
            journalBytes = bytes.length;
            int length = 0, read;
            while (length < bytes.length && (read = input.read(bytes, length, bytes.length - length)) > 0) length += read;
            JsonObject previous = JsonParser.parseString(new String(bytes, 0, length, StandardCharsets.UTF_8)).getAsJsonObject();
            previous.addProperty("event", "process.recovery");
            previous.addProperty("priorProcessCompleteness", "unknown; last durable facts only");
            sanitize(previous, 0);
            recovery = "# recovered-journal " + previous + "\n";
        } catch (IOException | RuntimeException error) {
            recovery = "# recovered-journal {\"event\":\"process.recovery\",\"status\":\"read-error\",\"completeness\":\"partial\"}\n";
        }
        result.add(recovery.stripTrailing());
        return result;
    }

    @Override public void append(List<String> lines, List<String> pinned) throws IOException {
        boolean urgent = false;
        for (String line : lines) urgent |= capture(line);
        IOException failure = null;
        try { logs.append(lines, pinned); } catch (IOException error) { failure = error; }
        // At most once a second for ordinary progress. Start/end/crash boundaries are durable promptly.
        if (dirty && (urgent || System.nanoTime() - lastWriteNs >= 1_000_000_000L)) {
            try { save(); } catch (IOException error) { failure = error; }
        }
        if (failure != null) throw failure;
    }

    private boolean capture(String line) {
        int start = line.indexOf("av-diag: {");
        if (start < 0) return false;
        int eventStart = line.indexOf("\"event\":\"", start);
        if (eventStart < 0) return false;
        eventStart += 9;
        int eventEnd = line.indexOf('"', eventStart);
        if (eventEnd < eventStart || !EVENTS.contains(line.substring(eventStart, eventEnd))) return false;
        try {
            JsonObject event = JsonParser.parseString(line.substring(start + "av-diag: ".length())).getAsJsonObject();
            String name = string(event, "event");
            if (!EVENTS.contains(name)) return false;
            String process = string(event, "processRunId");
            if (!process.equals(run)) { run = process; sessions.entrySet().clear(); }
            String instance = string(event, "playerInstanceId");
            String trace = string(event, "trace");
            if (("none".equals(trace) || "process".equals(instance)) && !"process.recovery".equals(name)) return false;
            String key = instance;
            JsonObject state = sessions.has(key) ? sessions.getAsJsonObject(key) : new JsonObject();
            String attempt = string(event, "attemptId");
            if (!attempt.equals(string(state, "attemptId"))) state = new JsonObject();
            for (String field : List.of("trace", "playerInstanceId", "engine", "attemptId", "mediaGeneration", "eventMediaId", "seq", "wallTime")) {
                if (event.has(field)) state.add(field, event.get(field).deepCopy());
            }
            state.addProperty("lastEvent", name);
            if (!state.has("unfinished") || "diag.session.begin".equals(name)) state.addProperty("unfinished", true);
            if ("play.attempt.end".equals(name)) state.addProperty("unfinished", false);
            if (event.has("observed")) {
                JsonObject observed = event.getAsJsonObject("observed");
                for (String field : FACTS) if (observed.has(field)) state.add(field, observed.get(field).deepCopy());
            }
            sessions.add(key, state);
            while (sessions.size() > 16) { sessions.remove(sessions.keySet().iterator().next()); evicted++; }
            dirty = true;
            return "diag.session.begin".equals(name) || "play.attempt.end".equals(name) || "process.recovery".equals(name);
        } catch (RuntimeException ignored) { return false; }
    }

    private void save() throws IOException {
        File directory = journal.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Diagnostic journal directory unavailable");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1); root.addProperty("previousRun", run); root.addProperty("evictedSessions", evicted);
        root.addProperty("scope", "last writer-processed facts; unknown unflushed tail"); root.add("sessions", sessions.deepCopy());
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        while (bytes.length > MAX_BYTES && sessions.size() > 0) {
            sessions.remove(sessions.keySet().iterator().next()); evicted++;
            root.add("sessions", sessions.deepCopy()); root.addProperty("evictedSessions", evicted);
            bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        }
        File temporary = new File(journal.getPath() + ".new");
        try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(bytes); }
        if (!temporary.renameTo(journal)) throw new IOException("Diagnostic journal replacement failed");
        journalBytes = bytes.length;
        dirty = false; lastWriteNs = System.nanoTime();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field); return value != null && value.isJsonPrimitive() ? value.getAsString() : "none";
    }

    private static void sanitize(JsonObject object, int depth) {
        for (String key : new ArrayList<>(object.keySet())) {
            JsonElement value = object.get(key);
            if (value.isJsonObject() && depth < 8) sanitize(value.getAsJsonObject(), depth + 1);
            else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String safe = DiagnosticText.clean(value.getAsString()).text();
                object.addProperty(key, safe.substring(0, Math.min(256, safe.length())));
            } else if (!value.isJsonPrimitive() && !value.isJsonNull()) object.remove(key);
        }
    }

    @Override public DiagnosticLogBuffer.Export export(String header, List<String> pinned) throws IOException {
        if (dirty) save();
        return logs.export(header + recovery, pinned);
    }
    @Override public long bytes() { return logs.bytes() + journalBytes; }
    @Override public long rotations() { return logs.rotations(); }
    @Override public long extraDiskBudgetBytes() { return logs.extraDiskBudgetBytes() + 2L * MAX_BYTES; }
}
