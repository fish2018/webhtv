package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import org.junit.Test;

import java.util.List;

public class ExoVideoConstraintApplicabilityTest {
    private static final TrackSelectionParameters AUTO = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
    private static final Format UHD = video("uhd", 3840, 2160, 60, 24_000_000);
    private static final Format HD = video("hd", 1280, 720, 30, 4_000_000);

    @Test public void singleNativeDvTrackNeverNeedsRuntimeLimits() {
        Format dv = UHD.buildUpon().setSampleMimeType("video/dolby-vision").setCodecs("dvhe.05.09").build();
        assertFalse(canAdjust(false, dv));
        assertFalse(canAdjust(true, dv));
    }

    @Test public void noTrackInformationCannotJustifyReselection() {
        assertFalse(ExoVideoConstraintApplicability.canAdjust(Tracks.EMPTY, AUTO));
        assertFalse(ExoVideoConstraintApplicability.canAdjust(null, AUTO));
    }

    @Test public void alternativesRemainUsableAfterLowestTrackWasSelected() {
        // The available group remains adaptive even when just its lowest track is selected.
        assertTrue(canAdjust(true, HD, UHD));
    }

    @Test public void unsupportedOrNonAdaptiveAlternativesAreNotUseful() {
        assertFalse(canAdjust(false, UHD, HD));
        // Only formats that Media3 reports as supported enter the policy.
        assertFalse(canAdjust(true, UHD));
    }

    @Test public void manualOverrideAndDisabledVideoArePreserved() {
        assertFalse(ExoVideoConstraintApplicability.canAdjust(true, true, true, false, List.of(UHD, HD)));
        assertFalse(ExoVideoConstraintApplicability.canAdjust(true, true, false, true, List.of(UHD, HD)));
    }

    @Test public void duplicateFormatsDoNotOfferDifferentBudgets() {
        assertFalse(canAdjust(true, UHD, UHD.buildUpon().setId("another-id").build()));
        assertTrue(canAdjust(true, UHD, UHD.buildUpon().setAverageBitrate(12_000_000).build()));
    }

    @Test public void unknownFormatBetweenKnownAlternativesDoesNotHideThem() {
        Format unknown = video("unknown", -1, -1, -1, -1);
        assertTrue(canAdjust(true, UHD, unknown, HD));
        assertFalse(canAdjust(true, UHD, unknown));
    }

    @Test public void unselectedAlternativeCameraDoesNotEnableSwitching() {
        assertFalse(ExoVideoConstraintApplicability.canAdjust(false, true, false, false, List.of(UHD, HD)));
    }

    private static Format video(String id, int width, int height, float fps, int bitrate) {
        return new Format.Builder().setId(id).setSampleMimeType("video/hevc")
                .setWidth(width).setHeight(height).setFrameRate(fps).setAverageBitrate(bitrate).build();
    }

    private static boolean canAdjust(boolean adaptive, Format... formats) {
        return ExoVideoConstraintApplicability.canAdjust(true, adaptive, false, false, List.of(formats));
    }
}
