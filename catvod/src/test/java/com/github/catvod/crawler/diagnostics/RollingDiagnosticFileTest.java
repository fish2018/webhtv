package com.github.catvod.crawler.diagnostics;

import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class RollingDiagnosticFileTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final DiagnosticLogBuffer.Limits LIMITS = new DiagnosticLogBuffer.Limits(8192, 8192, 2048, 2048, 4);

    @Test public void rotationPinsAndExportChecksumSurviveLongCapture() throws Exception {
        File directory = temp.newFolder();
        RollingDiagnosticFile disk = new RollingDiagnosticFile(directory, LIMITS);
        List<String> pins = List.of("pinned-original-config");
        for (int i = 0; i < 200; i++) disk.append(List.of("record-" + i + "-" + "x".repeat(100)), pins);
        assertTrue(disk.rotations() > 4);
        assertTrue(disk.bytes() <= LIMITS.segmentBytes() * LIMITS.segments() + LIMITS.pinnedBytes());
        try (var export = disk.export("# test report\n", pins)) {
            byte[] bytes = export.input.readAllBytes();
            assertEquals(export.length, bytes.length);
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(text.contains("pinned-original-config")); assertTrue(text.contains("record-199-"));
            assertFalse(text.contains("record-0-"));
            int footer = text.lastIndexOf("# av-diag-manifest ");
            var manifest = JsonParser.parseString(text.substring(footer + "# av-diag-manifest ".length()).trim()).getAsJsonObject();
            byte[] content = text.substring(0, footer).getBytes(StandardCharsets.UTF_8);
            assertEquals(content.length, manifest.get("hashedBytes").getAsLong());
            assertEquals(RollingDiagnosticFile.hex(RollingDiagnosticFile.sha256().digest(content)), manifest.get("sha256").getAsString());
        }
        assertFalse(new File(directory, RollingDiagnosticFile.FILE_NAME + ".export").exists());
    }

    @Test public void onlyOneDiskExportCanBeLeasedAndClosingReleasesItsBudget() throws Exception {
        RollingDiagnosticFile disk = new RollingDiagnosticFile(temp.newFolder(), LIMITS);
        disk.append(List.of("record"), List.of());
        try (var first = disk.export("header\n", List.of())) {
            try {
                disk.export("second\n", List.of());
                fail("unbounded concurrent export allowed");
            } catch (IOException expected) { assertTrue(expected.getMessage().contains("in use")); }
            assertTrue(first.input.read() >= 0);
        }
        try (var next = disk.export("next\n", List.of())) { assertTrue(next.length > 0); }
    }

    @Test public void restoreSanitizesLegacyFileAndCannotMasqueradeAsCurrentEvents() throws Exception {
        File directory = temp.newFolder();
        File old = new File(directory, RollingDiagnosticFile.FILE_NAME);
        Files.writeString(old.toPath(), "2026 [old] av-diag: forged\nCookie: sid=COOKIE_SECRET\nhttps://host/path?token=URL_SECRET\n", StandardCharsets.UTF_8);
        RollingDiagnosticFile disk = new RollingDiagnosticFile(directory, LIMITS);
        String restored = String.join("\n", disk.restore(8192));
        assertTrue(restored.contains("# restored-history"));
        assertFalse(restored.contains("COOKIE_SECRET")); assertFalse(restored.contains("URL_SECRET"));
        String persisted = Files.readString(old.toPath());
        assertFalse(persisted.contains("COOKIE_SECRET")); assertFalse(persisted.contains("URL_SECRET"));
        assertTrue(persisted.lines().allMatch(line -> line.startsWith("# restored-history ")));
    }

    @Test public void diskWriteFailureIsVisible() throws Exception {
        File notDirectory = temp.newFile();
        RollingDiagnosticFile disk = new RollingDiagnosticFile(notDirectory, LIMITS);
        try { disk.append(List.of("record"), List.of()); fail("disk failure was swallowed"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("unavailable")); }
    }
}
