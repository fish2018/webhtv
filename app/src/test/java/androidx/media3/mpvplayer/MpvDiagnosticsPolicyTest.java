package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MpvDiagnosticsPolicyTest {
    @Test
    public void onlyNativeInfoMeasurementsBypassPlaybackProcessing() {
        String[][] measurements = {
                {"vo/gpu-next/aimagereader", "WebHTV FEL perf:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL perf stages:"},
                {"vo/gpu-next", "WebHTV FEL render perf:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL GPU init:"},
                {"vd", "WebHTV FEL decoder threads:"},
                {"vd", "WebHTV FEL decoder cost:"},
                {"vd", "WebHTV FEL producer handoff:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL map cost:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL GPU pool:"},
                {"enhancement_pair", "WebHTV FEL stats:"},
                {"vd", "WebHTV FEL decoder queue:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL reuse:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL api perf:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL frame order:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL api slow:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL descriptors:"},
                {"vo/gpu-next", "WebHTV FEL renderer init:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL wait sample:"},
                {"vo/gpu-next/aimagereader", "WebHTV FEL bind probe:"},
        };
        assertEquals(MpvDiagnosticsPolicy.FEL_PERFORMANCE_KINDS, measurements.length);
        for (int kind = 0; kind < measurements.length; kind++) {
            String prefix = measurements[kind][0], text = measurements[kind][1] + " count=12\n";
            assertEquals(kind, MpvDiagnosticsPolicy.felPerformanceKind(prefix, 40, text));
            for (int severity : new int[]{0, 10, 20, 30, 50, 60, 70})
                assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind(prefix, severity, text));
            assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind("metadata", 40, text));
            assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind(prefix, 40, "title=" + text));
            assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind(prefix, 40,
                    text + "WebHTV FEL fatal: driver failure"));
        }
        assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind("vd", 20, "WebHTV FEL fatal: no progress"));
        assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind("vd", 40, "WebHTV FEL unknown: failed"));
        assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind("vo/gpu-next", 40, "WebHTV FEL GPU input: NLQ active"));
        assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind(null, 40, "WebHTV FEL perf:"));
        assertEquals(-1, MpvDiagnosticsPolicy.felPerformanceKind("vd", 40, null));
    }

    @Test
    public void warningFloodCannotHidePerformanceOrDecoderThreadEvidence() {
        MpvDiagnosticsPolicy.NativeLogWindow window = new MpvDiagnosticsPolicy.NativeLogWindow();
        for (int i = 0; i < 100; i++) window.allow(100, "warning");
        for (int i = 0; i < 8; i++) assertTrue(window.allowPerformance(101, 0));
        assertFalse(window.allowPerformance(102, 0));
        assertTrue(window.allowPerformance(103, 3)); // GPU init has its own budget.
        assertTrue(window.allowPerformance(103, 4)); // Actual decoder threads too.
        assertTrue(window.allowPerformance(103, 11)); // Cache evidence has its own budget.
        for (int i = 0; i < 8; i++) assertTrue(window.allowPerformance(103, 12));
        assertFalse(window.allowPerformance(104, 12));
        assertTrue(window.allowPerformance(104, 13)); // Frame identity survives API statistics.
        assertTrue(window.allowPerformance(104, 14)); // Slow-call evidence has its own budget.
        assertTrue(window.allowPerformance(104, 17)); // Wait samples survive the same flood.
        assertTrue(window.allowPerformance(104, 18)); // Explicit bind groups have a separate budget.
        assertTrue(window.allow(104, "vd: WebHTV FEL fatal: no progress"));
        assertEquals(2, window.takePerformanceSuppressed());
        assertEquals(0, window.takePerformanceSuppressed());
        assertTrue(window.allowPerformance(5101, 0));
        assertTrue(window.allowPerformance(50, 0)); // Clock reset starts a new window.
        assertFalse(window.allowPerformance(50, -1));
        assertFalse(window.allowPerformance(50, MpvDiagnosticsPolicy.FEL_PERFORMANCE_KINDS));
    }

    @Test
    public void fatalFelRequiresActualErrorRecordNotQuotedMetadata() {
        assertTrue(MpvDiagnosticsPolicy.isFatalFelLog(20, "WebHTV FEL fatal: decoder stalled\n"));
        assertFalse(MpvDiagnosticsPolicy.isFatalFelLog(40, "WebHTV FEL fatal: a file title"));
        assertFalse(MpvDiagnosticsPolicy.isFatalFelLog(20, "title=WebHTV FEL fatal: movie"));
        assertFalse(MpvDiagnosticsPolicy.isFatalFelLog(20, null));
    }

    @Test
    public void warningSeverityPreservesStarvationAndUnknownWarnings() {
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(30,
                "MediaCodec input and output ports remained unavailable; failing hardware decode"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(20, "opaque driver diagnostic"));
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately(60, "ordinary per-frame detail"));
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately(30, null));
    }

    @Test
    public void repeatedWarningsAreBoundedButFatalDiagnosisSurvives() {
        MpvDiagnosticsPolicy.NativeLogWindow window = new MpvDiagnosticsPolicy.NativeLogWindow();
        for (int i = 0; i < 32; i++) assertTrue(window.allow(100, "warning"));
        assertFalse(window.allow(101, "warning"));
        assertFalse(window.allow(102, "warning"));
        assertTrue(window.allow(103, "vd: WebHTV FEL fatal: no progress"));
        assertEquals(2, window.takeSuppressed());
        assertEquals(0, window.takeSuppressed());
        assertTrue(window.allow(5100, "warning"));
    }

    @Test
    public void felReconstructionEvidenceIsPersistedBeforeMainQueue() {
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(
                "enhancement_pair: WebHTV Android FEL: software enhancement-layer decoder enabled"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(
                "enhancement_pair: WebHTV FEL pair: BL=1.0 EL=1.0 NLQ=1 software-EL=1"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(
                "vo/gpu-next: WebHTV FEL GPU input: matched EL uploaded with active NLQ."));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(
                "ffmpeg/video: Native Dolby Vision output is unavailable, using the base-layer decoder for profile 7"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately(
                "ffmpeg/video: MediaCodec started successfully: codec = c2.mtk.hevc.decoder, ret = 0"));
    }

    @Test
    public void failureEvidenceDoesNotWaitForTheUi() {
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately("vd: Decoder init failed for hevc"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately("vo/gpu-next: Vulkan error"));
        assertTrue(MpvDiagnosticsPolicy.shouldLogNativeImmediately("lavf: Invalid data found when processing input"));
    }

    @Test
    public void ordinaryPerFrameLogsStayOutOfImmediateDiagnostics() {
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately("cplayer: playing frame pts=12.34"));
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately("vd: sending packet pts=12.34"));
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately(null));
        assertFalse(MpvDiagnosticsPolicy.shouldLogNativeImmediately(""));
    }

    @Test
    public void normalPlaybackAndMinimalErrorsNeverQueryDetailedProperties() {
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.PLAYBACK, false));
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.PLAYBACK, true));
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.ERROR_MINIMAL, false));
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.ERROR_MINIMAL, true));
    }

    @Test
    public void visiblePanelUsesObservedPropertiesOnly() {
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.PANEL, false));
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.PANEL, true));
    }

    @Test
    public void detailedLogsAndErrorsRequireDebugSwitch() {
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.DEBUG_LOG, false));
        assertTrue(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.DEBUG_LOG, true));
        assertFalse(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.ERROR_DETAILED, false));
        assertTrue(MpvDiagnosticsPolicy.allowsSynchronousProperties(MpvDiagnosticsPolicy.Request.ERROR_DETAILED, true));
    }

    @Test
    public void sourceSummaryDoesNotExposePathQueryOrToken() {
        String source = "https://cdn.example.com/private/movie.mkv?token=secret";
        String summary = MpvDiagnosticsPolicy.sourceSummary(source);

        assertTrue(summary.contains("scheme=https"));
        assertTrue(summary.contains("urlLen=" + source.length()));
        assertFalse(summary.contains("cdn.example.com"));
        assertFalse(summary.contains("private"));
        assertFalse(summary.contains("secret"));
    }

    @Test
    public void nativeLogRedactionRemovesUrlsAndSensitiveHeaders() {
        String raw = "opening https://cdn.example.com/a.m3u8?token=secret Authorization: Bearer abc Cookie=session=xyz codec=h264";
        String safe = MpvDiagnosticsPolicy.redactSensitive(raw);

        assertTrue(safe.contains("<url>"));
        assertTrue(safe.toLowerCase().contains("authorization=<redacted>"));
        assertTrue(safe.toLowerCase().contains("cookie=<redacted>"));
        assertTrue(safe.contains("codec=h264"));
        assertFalse(safe.contains("secret"));
        assertFalse(safe.contains("Bearer abc"));
        assertFalse(safe.contains("session=xyz"));
    }
}
