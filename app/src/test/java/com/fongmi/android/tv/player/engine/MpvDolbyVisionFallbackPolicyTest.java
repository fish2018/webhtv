package com.fongmi.android.tv.player.engine;

import androidx.media3.mpvplayer.MpvPlayer;

import com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicy;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvDolbyVisionFallbackPolicyTest {

    @Test
    public void gpuOutputMeansDv7BaseLayerFallback() {
        MpvPlayer.VideoTrackDiagnostics details = details(7);

        assertTrue(MpvPlayerEngine.isDolbyVisionHdr10Fallback(
                details, "gpu-next"));
        assertTrue(MpvPlayerEngine.isDolbyVisionHdr10Fallback(
                details, "gpu"));
    }

    @Test
    public void configuredFallbackAlsoCoversSurfaceDirect() {
        assertTrue(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(
                details(7), true, true));
        assertFalse(MpvPlayerEngine.isDolbyVisionHdr10Fallback(
                details(7), "mediacodec_embed"));
        assertFalse(MpvPlayerEngine.isDolbyVisionHdr10Fallback(
                details(5), "gpu-next"));
    }

    @Test
    public void configuredFallbackMarksDv7WithoutWaitingForVo() {
        assertTrue(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(
                details(7), true, true));
        assertFalse(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(
                details(7), false, true));
        assertFalse(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(
                details(5), true, true));
        assertFalse(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(
                details(7), true, false));
    }

    @Test
    public void nativeDv7AlwaysWinsOverFallbackPreference() {
        assertEquals(MpvPlayerEngine.DV7_PRESERVE,
                MpvPlayerEngine.selectDv7Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED,
                        PlaybackPerformanceSetting.DV7_HANDLING_HDR10));
    }

    @Test
    public void supportedP81UsesP81Preference() {
        assertEquals(MpvPlayerEngine.DV7_P81,
                MpvPlayerEngine.selectDv7Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED,
                        PlaybackPerformanceSetting.DV7_HANDLING_P81));
    }

    @Test
    public void unsupportedP81FallsBackToHdr10() {
        assertEquals(MpvPlayerEngine.DV7_HDR10,
                MpvPlayerEngine.selectDv7Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                        PlaybackPerformanceSetting.DV7_HANDLING_P81));
    }

    @Test
    public void explicitFelWinsEvenWhenNativeDv7IsSupported() {
        for (MpvAutoOutputPolicy.DolbyVisionSupport nativeSupport
                : MpvAutoOutputPolicy.DolbyVisionSupport.values()) {
            for (MpvAutoOutputPolicy.DolbyVisionSupport p81Support
                    : MpvAutoOutputPolicy.DolbyVisionSupport.values()) {
                assertEquals(MpvPlayerEngine.DV7_FEL,
                        MpvPlayerEngine.selectDv7Handling(nativeSupport, p81Support,
                                PlaybackPerformanceSetting.DV7_HANDLING_FEL));
            }
        }
    }

    @Test
    public void felPreservesOriginalLayersAndRpu() {
        assertEquals("preserve", MpvPlayerEngine.dv7DemuxerOption(MpvPlayerEngine.DV7_FEL));
        assertEquals("p81", MpvPlayerEngine.dv7DemuxerOption(MpvPlayerEngine.DV7_P81));
        assertEquals("hdr10", MpvPlayerEngine.dv7DemuxerOption(MpvPlayerEngine.DV7_HDR10));
        assertEquals("preserve", MpvPlayerEngine.dv7DemuxerOption(MpvPlayerEngine.DV7_PRESERVE));
    }

    @Test
    public void profile8SelectsHdr10OnlyWithRegularHevcSupport() {
        assertEquals(MpvPlayerEngine.DV8_HDR10,
                MpvPlayerEngine.selectDv8Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED));
        assertEquals(MpvPlayerEngine.DV8_PRESERVE,
                MpvPlayerEngine.selectDv8Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN));
        assertEquals(MpvPlayerEngine.DV8_PRESERVE,
                MpvPlayerEngine.selectDv8Handling(
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED,
                        MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED));
    }

    @Test
    public void configuredProfile8FallbackIsReportedSeparatelyFromDv7() {
        MpvPlayer.VideoTrackDiagnostics details = details(8);
        assertTrue(MpvPlayerEngine.isConfiguredDv8Hdr10Fallback(details, true, true));
        assertFalse(MpvPlayerEngine.isConfiguredDv7Hdr10Fallback(details, true, true));
    }

    private static MpvPlayer.VideoTrackDiagnostics details(int profile) {
        return new MpvPlayer.VideoTrackDiagnostics(
                "dvhe.0" + profile + ".06", profile, 6,
                "hevc", "c2.qti.hevc.decoder", null);
    }
}
