package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvAutoOutputPolicyTest {

    @Test
    public void failedDirectStaysOnGpuAcrossSameItemEvaluations() {
        MpvAutoOutputPolicy.Decision tv = MpvAutoOutputPolicy.evaluate(1920, 1080, true, true, false, false);
        for (int rebuild = 0; rebuild < 3; rebuild++) {
            MpvAutoOutputPolicy.Decision safe = MpvAutoOutputPolicy.afterSurfaceFailure(tv, true);
            assertFalse(safe.eligible());
            assertEquals("surface-direct-failed-for-item", safe.reason());
            assertEquals(MpvAutoOutputPolicy.Transition.KEEP_GPU,
                    MpvAutoOutputPolicy.transition(safe.eligible(), false));
            assertEquals(MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT,
                    MpvAutoOutputPolicy.transition(safe.eligible(), true));
        }
        // New items/explicit settings changes clear the failure in PlayerManager.
        assertTrue(MpvAutoOutputPolicy.afterSurfaceFailure(tv, false).eligible());
        MpvAutoOutputPolicy.Decision mobile = MpvAutoOutputPolicy.evaluate(1920, 1080, true, false, false, false);
        assertEquals(mobile, MpvAutoOutputPolicy.afterSurfaceFailure(mobile, false));
    }

    @Test
    public void acceptsTvHardwareDecodeAtAnyResolution() {
        assertTrue(MpvAutoOutputPolicy.evaluate(3840, 1632, true, true, false, false).eligible());
        assertTrue(MpvAutoOutputPolicy.evaluate(1920, 1080, true, true, false, false).eligible());
    }

    @Test
    public void canStartDirectBeforeVideoSizeIsKnown() {
        assertTrue(MpvAutoOutputPolicy.canStartSurfaceDirect(true, true, false, false));
        assertFalse(MpvAutoOutputPolicy.canStartSurfaceDirect(true, true, true, false));
    }

    @Test
    public void rejectsVideoFeaturesThatNeedGpuComposition() {
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, true, false).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, true).eligible());
    }

    @Test
    public void usesDirectOutputOnlyWhenDolbyVisionProfileIsSupported() {
        assertTrue(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED, 5).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 5).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN, 5).eligible());
    }

    @Test
    public void keepsSurfaceDirectForUnsupportedDv7WhenHdr10FallbackIsEnabled() {
        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 7, true);
        assertTrue(decision.eligible());
        assertEquals("dv7-hdr10-base-layer", decision.reason());
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_SURFACE_DIRECT,
                MpvAutoOutputPolicy.transition(decision.eligible(), true));
    }

    @Test
    public void doesNotPinUnsupportedDv7WithoutHdr10Fallback() {
        assertFalse(MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 7, false).eligible());
    }

    @Test
    public void usesHevcHdr10ForUnsupportedProfile8Only() {
        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 8, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED);
        assertTrue(decision.eligible());
        assertEquals("dv8-hdr10-base-layer", decision.reason());
    }

    @Test
    public void keepsProfile8SoftwareWhenHevcCapabilityIsUnknownOrUnsupported() {
        assertFalse(MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 8, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNKNOWN).eligible());
        assertFalse(MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED, 8, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.UNSUPPORTED).eligible());
    }

    @Test
    public void nativeProfile8AlwaysWinsOverHdr10Fallback() {
        MpvAutoOutputPolicy.Decision decision = MpvAutoOutputPolicy.evaluate(
                3840, 2160, true, true, false, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED, 8, false,
                MpvAutoOutputPolicy.DolbyVisionSupport.SUPPORTED);
        assertTrue(decision.eligible());
        assertEquals("dolby-vision-hw-supported", decision.reason());
    }

    @Test
    public void evaluatesFourKBeforeTracksAreComplete() {
        assertTrue(MpvAutoOutputPolicy.canEvaluateWithoutTracks(3840, 1606));
    }

    @Test
    public void evaluatesKnownSizeBeforeTracksAreComplete() {
        assertTrue(MpvAutoOutputPolicy.canEvaluateWithoutTracks(1920, 1080));
        assertTrue(MpvAutoOutputPolicy.canEvaluateWithoutTracks(3840, 2160));
    }

    @Test
    public void revealsSuccessfulDirectFrameBeforeTrackMetadataCompletes() {
        assertTrue(MpvAutoOutputPolicy.canRevealDirectFrame(
                true, false, true, true, 3840, 2160));
    }

    @Test
    public void keepsShutterForUnprovenOrNonDirectOutput() {
        assertFalse(MpvAutoOutputPolicy.canRevealDirectFrame(
                true, false, false, true, 3840, 2160));
        assertFalse(MpvAutoOutputPolicy.canRevealDirectFrame(
                true, false, true, false, 3840, 2160));
        assertFalse(MpvAutoOutputPolicy.canRevealDirectFrame(
                true, false, true, true, 0, 2160));
        assertFalse(MpvAutoOutputPolicy.canRevealDirectFrame(
                true, true, true, true, 3840, 2160));
        assertFalse(MpvAutoOutputPolicy.canRevealDirectFrame(
                false, false, true, true, 3840, 2160));
    }

    @Test
    public void subtitlesUseDirectOutputOverlay() {
        assertTrue(MpvAutoOutputPolicy.evaluate(3840, 2160, true, true, false, false).eligible());
    }

    @Test
    public void keepsDirectOutputWhenNextItemRemainsEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(true, true));
    }

    @Test
    public void entersDirectOutputWhenGpuItemIsEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.ENTER_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(true, false));
    }

    @Test
    public void leavesDirectOutputWhenNextItemIsNotEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.LEAVE_SURFACE_DIRECT, MpvAutoOutputPolicy.transition(false, true));
    }

    @Test
    public void keepsGpuOutputWhenNextItemIsNotEligible() {
        assertEquals(MpvAutoOutputPolicy.Transition.KEEP_GPU, MpvAutoOutputPolicy.transition(false, false));
    }
}
