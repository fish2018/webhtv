package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdAudioDiagnosticsTest {

    @Test
    public void snapshotContainsOnlyFixedCodeCounters() {
        AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        diagnostics.record(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
        diagnostics.record(AdAudioDiagnostics.Code.QUEUE_OVERFLOW);
        diagnostics.record(AdAudioDiagnostics.Code.SEEK_REJECTED);

        AdAudioDiagnostics.Snapshot snapshot = diagnostics.snapshot();

        assertEquals(2L, snapshot.count(AdAudioDiagnostics.Code.QUEUE_OVERFLOW));
        assertEquals(1L, snapshot.count(AdAudioDiagnostics.Code.SEEK_REJECTED));
        assertEquals(AdAudioDiagnostics.Code.SEEK_REJECTED, snapshot.lastCode());
    }

    @Test
    public void repeatedCodesAreSampledSoTheAudioThreadNeverLogsPerFrame() {
        // record() is reachable once per PCM frame (STALE_GENERATION after a flush,
        // QUEUE_OVERFLOW while the recognizer lags) and each log line is a synchronous
        // file write, so only the first few plus a decayed tail may be emitted.
        assertTrue(AdAudioDiagnostics.shouldLogCount(1L));
        assertTrue(AdAudioDiagnostics.shouldLogCount(3L));
        assertFalse(AdAudioDiagnostics.shouldLogCount(4L));
        assertFalse(AdAudioDiagnostics.shouldLogCount(99L));
        assertTrue(AdAudioDiagnostics.shouldLogCount(100L));
        assertFalse(AdAudioDiagnostics.shouldLogCount(101L));

        long logged = 0;
        for (long i = 1; i <= 5_000L; i++) if (AdAudioDiagnostics.shouldLogCount(i)) logged++;
        assertEquals(53L, logged);
    }

    @Test
    public void countersStayAccurateEvenWhenLoggingIsSampled() {
        AdAudioDiagnostics diagnostics = new AdAudioDiagnostics();
        for (int i = 0; i < 250; i++) {
            diagnostics.record(AdAudioDiagnostics.Code.STALE_GENERATION);
        }
        assertEquals(250L, diagnostics.count(AdAudioDiagnostics.Code.STALE_GENERATION));
    }
}
