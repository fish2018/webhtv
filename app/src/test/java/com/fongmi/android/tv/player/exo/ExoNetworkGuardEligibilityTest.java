package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.AudioPlaybackDiagnostics;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExoNetworkGuardEligibilityTest {

    @Test
    public void allowsExoVodWithoutResourceTypeGate() {
        assertTrue(ExoNetworkGuardEligibility.resolve(
                request(false, AudioPlaybackDiagnostics.OutputMode.PCM)).eligible());
    }

    @Test
    public void unknownOutput_defersSpeedChangesUntilInitialized() {
        assertFalse(ExoNetworkGuardEligibility.resolve(
                request(false, AudioPlaybackDiagnostics.OutputMode.UNKNOWN)).eligible());
        assertFalse(ExoNetworkGuardEligibility.resolve(request(false, null)).eligible());
    }

    @Test
    public void preservesTunnelingAndPassthroughInsteadOfDisablingThem() {
        assertFalse(ExoNetworkGuardEligibility.resolve(
                request(true, AudioPlaybackDiagnostics.OutputMode.UNKNOWN)).eligible());
        assertFalse(ExoNetworkGuardEligibility.resolve(
                request(false, AudioPlaybackDiagnostics.OutputMode.PASSTHROUGH)).eligible());
    }

    @Test
    public void configuredPassthroughDoesNotBlockRuntimePcmOrOffload() {
        assertTrue(ExoNetworkGuardEligibility.resolve(
                request(false, AudioPlaybackDiagnostics.OutputMode.PCM)).eligible());
        assertTrue(ExoNetworkGuardEligibility.resolve(
                request(false, AudioPlaybackDiagnostics.OutputMode.OFFLOAD)).eligible());
    }

    @Test
    public void userSpeedAndUnsupportedSpeedCommandRemainUntouched() {
        assertFalse(ExoNetworkGuardEligibility.resolve(new ExoNetworkGuardEligibility.Request(
                true, true, true, false, true, false,
                AudioPlaybackDiagnostics.OutputMode.UNKNOWN)).eligible());
        assertFalse(ExoNetworkGuardEligibility.resolve(new ExoNetworkGuardEligibility.Request(
                true, true, true, true, false, false,
                AudioPlaybackDiagnostics.OutputMode.UNKNOWN)).eligible());
    }

    private static ExoNetworkGuardEligibility.Request request(
            boolean tunneling, AudioPlaybackDiagnostics.OutputMode audioOutputMode) {
        return new ExoNetworkGuardEligibility.Request(
                true, true, true, true, true, tunneling, audioOutputMode);
    }
}
