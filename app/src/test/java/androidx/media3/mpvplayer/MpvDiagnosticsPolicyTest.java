package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvDiagnosticsPolicyTest {

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
