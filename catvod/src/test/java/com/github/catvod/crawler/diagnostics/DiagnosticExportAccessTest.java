package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class DiagnosticExportAccessTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final DiagnosticLogBuffer.Limits LIMITS = new DiagnosticLogBuffer.Limits(8192, 8192, 2048, 8192, 4);

    @Test public void actionsNeedNoPairingButRemainRateLimited() {
        AtomicLong now = new AtomicLong(10_000);
        DiagnosticAccess access = new DiagnosticAccess(now::get);
        for (int i = 0; i < 5; i++) assertTrue(access.allowAction());
        for (int i = 0; i < 100; i++) assertFalse(access.allowAction());
        now.addAndGet(999); assertFalse(access.allowAction());
        now.incrementAndGet(); assertTrue(access.allowAction());
        now.set(0); // A changed clock origin must not lock the controls indefinitely.
        for (int i = 0; i < 5; i++) assertTrue(access.allowAction());
        assertFalse(access.allowAction());
    }

    @Test public void controlsRequireAnExplicitSameOriginAndKnownDeviceHost() {
        String host = "192.168.1.2:9978";
        Set<String> hosts = Set.of(host, "localhost:9978", "[::1]:9978");
        assertTrue(DiagnosticAccess.sameOrigin("http://" + host, host, hosts));
        assertTrue(DiagnosticAccess.sameOrigin("http://LOCALHOST:9978", "localhost:9978", hosts));
        assertTrue(DiagnosticAccess.sameOrigin("http://[::1]:9978", "[::1]:9978", hosts));
        for (String origin : new String[]{null, "", "null", "http://evil.test", "http://192.168.1.2",
                "https://" + host, "http://" + host + "/", "http://" + host + "?x=1", "http://" + host + "#x",
                "http://user@" + host, "http://" + host + ".evil.test", "http://[broken", "http://" + host + " http://evil.test"}) {
            assertFalse("Rejected origin: " + origin, DiagnosticAccess.sameOrigin(origin, host, hosts));
        }
        assertFalse(DiagnosticAccess.sameOrigin("http://evil.test", "evil.test", hosts));
        assertFalse(DiagnosticAccess.sameOrigin("http://" + host, null, hosts));
    }

    @Test public void zipUsesOneImmutableSnapshotAndEveryEntryHasMatchingHash() throws Exception {
        RollingDiagnosticFile file = new RollingDiagnosticFile(temp.newFolder(), LIMITS);
        file.append(List.of(line("video.output.summary", 1), line("audio.output.write", 2)), List.of());
        try (var export = file.export("# fixture\n", List.of())) {
            byte[] original;
            try (var input = export.openAgain()) { original = input.readAllBytes(); }
            ByteArrayOutputStream output = new ByteArrayOutputStream(); DiagnosticReport.archive(export, output);
            LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
            }
            assertArrayEquals(original, entries.get("webhtv-debug-log.txt"));
            JsonObject files = JsonParser.parseString(new String(entries.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files");
            for (String name : files.keySet()) {
                byte[] bytes = entries.get(name); assertEquals(bytes.length, files.getAsJsonObject(name).get("bytes").getAsInt());
                assertEquals(RollingDiagnosticFile.hex(RollingDiagnosticFile.sha256().digest(bytes)), files.getAsJsonObject(name).get("sha256").getAsString());
            }
            assertEquals(2, new String(entries.get("av-events.jsonl"), StandardCharsets.UTF_8).lines().count());
            assertTrue(new String(entries.get("report.txt"), StandardCharsets.UTF_8).contains("不等于实际发声"));
        }
    }

    @Test public void forgedLegacyTextCannotBecomeEvidenceAndTextManifestRemainsValid() throws Exception {
        assertNull(DiagnosticReport.parseEvent("2026-09-15 12:00:00 [worker] external: " + line("video.output.summary", 1)));
        RollingDiagnosticFile file = new RollingDiagnosticFile(temp.newFolder(), LIMITS);
        file.append(List.of(line("video.output.summary", 1)), List.of());
        try (var report = DiagnosticReport.text(file.export("# fixture\n", List.of()))) {
            String text = new String(report.input.readAllBytes(), StandardCharsets.UTF_8);
            int footer = text.indexOf("# av-diag-manifest ");
            JsonObject manifest = JsonParser.parseString(text.substring(footer + 19, text.indexOf('\n', footer))).getAsJsonObject();
            byte[] preceding = text.substring(0, footer).getBytes(StandardCharsets.UTF_8);
            assertEquals(preceding.length, manifest.get("hashedBytes").getAsInt());
            assertEquals(RollingDiagnosticFile.hex(RollingDiagnosticFile.sha256().digest(preceding)), manifest.get("sha256").getAsString());
            assertTrue(text.contains("WebHTV 音视频诊断报告"));
        }
    }

    @Test public void incidentSavesImmediatelyRecoversAfterCrashAndFinishesWithoutMoreLogs() throws Exception {
        var folder = temp.newFolder(); AtomicLong now = new AtomicLong(1000);
        IncidentDiagnosticFile file = new IncidentDiagnosticFile(new RollingDiagnosticFile(folder, LIMITS), folder, now::get);
        file.append(List.of("before failure"), List.of()); now.addAndGet(30_000);
        file.append(List.of(line("diag.user-mark", 1)), List.of());
        var pending = folder.toPath().resolve("webhtv-diagnostic-incident-0.txt.new");
        assertTrue(Files.readString(pending).contains("before failure"));
        assertTrue(Files.readString(pending).contains("\"postWindowPending\":true"));
        now.addAndGet(15_000); file.tick(); assertFalse(file.needsTick()); assertFalse(Files.exists(pending));
        file.append(List.of(line("diag.user-mark", 2)), List.of());
        IncidentDiagnosticFile recovered = new IncidentDiagnosticFile(new RollingDiagnosticFile(folder, LIMITS), folder, now::get);
        recovered.restore(8192);
        assertTrue(Files.readString(folder.toPath().resolve("webhtv-diagnostic-incident-0.txt")).contains("\"completeness\":\"partial\""));
    }

    private static String line(String name, long seq) {
        JsonObject event = JsonParser.parseString(new DiagnosticEvent(name, "trace-1", "player-1", 1, 1)
                .observed("submittedBuffers", 2).observed("acceptedBytes", 8).json()).getAsJsonObject();
        event.addProperty("processRunId", "run-1"); event.addProperty("captureGeneration", 1); event.addProperty("seq", seq);
        return "2026-09-15 12:00:00.000 [fixture] av-diag: " + event;
    }
}
