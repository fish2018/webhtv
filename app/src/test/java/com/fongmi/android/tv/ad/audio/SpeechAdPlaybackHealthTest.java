package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeechAdPlaybackHealthTest {

    @Test
    public void stableSamplesKeepSpeechEnabled() {
        AdAudioRuntimeController.SpeechAdPlaybackHealth gate =
                new AdAudioRuntimeController.SpeechAdPlaybackHealth();

        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.OBSERVED,
                gate.observe(0, 0, 0, 0));
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.OBSERVED,
                gate.observe(5_000, 1, 0, 0));
        assertFalse(gate.isSuppressed());
    }

    @Test
    public void confirmedUnderrunsSuppressSpeech() {
        AdAudioRuntimeController.SpeechAdPlaybackHealth gate =
                new AdAudioRuntimeController.SpeechAdPlaybackHealth();

        gate.observe(0, 0, 0, 0);
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.DEGRADED,
                gate.observe(5_000, 0, 1, 0));
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.SUPPRESS,
                gate.observe(10_000, 0, 2, 0));
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.SUPPRESSED,
                gate.observe(15_000, 0, 3, 0));
        assertTrue(gate.isSuppressed());
    }

    @Test
    public void rebufferAndDroppedFrameDeltasAreCountedOnlyOncePerSample() {
        AdAudioRuntimeController.SpeechAdPlaybackHealth gate =
                new AdAudioRuntimeController.SpeechAdPlaybackHealth();

        gate.observe(0, 0, 0, 0);
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.HELD,
                gate.observe(1_000, 100, 0, 1));
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.DEGRADED,
                gate.observe(5_000, 100, 0, 1));
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.SUPPRESS,
                gate.observe(10_000, 120, 0, 1));
        assertTrue(gate.isSuppressed());
    }

    @Test
    public void resetStartsAFreshPlaybackSession() {
        AdAudioRuntimeController.SpeechAdPlaybackHealth gate =
                new AdAudioRuntimeController.SpeechAdPlaybackHealth();
        gate.observe(0, 0, 0, 0);
        gate.observe(5_000, 0, 1, 0);
        gate.observe(10_000, 0, 2, 0);
        assertTrue(gate.isSuppressed());

        gate.reset();
        assertFalse(gate.isSuppressed());
        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.OBSERVED,
                gate.observe(0, 0, 0, 0));
    }

    @Test
    public void longTelemetryGapDiscardsOldEvidence() {
        AdAudioRuntimeController.SpeechAdPlaybackHealth gate =
                new AdAudioRuntimeController.SpeechAdPlaybackHealth();
        gate.observe(0, 0, 0, 0);
        gate.observe(5_000, 0, 1, 0);

        assertEquals(AdAudioRuntimeController.SpeechAdPlaybackHealth.Decision.OBSERVED,
                gate.observe(20_001, 0, 2, 0));
        assertFalse(gate.isSuppressed());
    }
}
