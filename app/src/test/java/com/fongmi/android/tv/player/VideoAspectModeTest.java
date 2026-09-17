package com.fongmi.android.tv.player;

import androidx.media3.ui.AspectRatioFrameLayout;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoAspectModeTest {

    @Test
    public void legacyModeIdsRemainStable() {
        assertEquals(0, VideoAspectMode.ORIGINAL);
        assertEquals(1, VideoAspectMode.RATIO_16_9);
        assertEquals(2, VideoAspectMode.RATIO_4_3);
        assertEquals(3, VideoAspectMode.FILL);
        assertEquals(4, VideoAspectMode.ZOOM);
        assertEquals(5, VideoAspectMode.RATIO_21_9);
        assertEquals(6, VideoAspectMode.ADAPTIVE);
        assertEquals(7, VideoAspectMode.CUSTOM);
    }

    @Test
    public void displayOrderKeepsFixedRatiosTogetherWithoutChangingIds() {
        assertArrayEquals(new int[]{
                VideoAspectMode.ORIGINAL,
                VideoAspectMode.RATIO_16_9,
                VideoAspectMode.RATIO_4_3,
                VideoAspectMode.RATIO_21_9,
                VideoAspectMode.FILL,
                VideoAspectMode.ZOOM,
                VideoAspectMode.ADAPTIVE,
                VideoAspectMode.CUSTOM
        }, VideoAspectMode.displayOrder());
        assertEquals(3, VideoAspectMode.displayIndex(VideoAspectMode.RATIO_21_9));
        assertEquals(VideoAspectMode.FILL, VideoAspectMode.modeAtDisplayIndex(4));
    }

    @Test
    public void sanitizePreservesKnownModesAndRejectsUnknownValues() {
        for (int mode = VideoAspectMode.ORIGINAL; mode <= VideoAspectMode.CUSTOM; mode++) {
            assertEquals(mode, VideoAspectMode.sanitize(mode));
        }
        assertEquals(VideoAspectMode.ORIGINAL, VideoAspectMode.sanitize(-1));
        assertEquals(VideoAspectMode.ORIGINAL, VideoAspectMode.sanitize(8));
    }

    @Test
    public void customDimensionsRejectExtremeRatios() {
        assertFalse(VideoAspectMode.isValidDimensions(100f, 1f));
        assertTrue(VideoAspectMode.isValidDimensions(2560f, 1080f));
    }

    @Test
    public void fixedAndDynamicModesResolveTargetRatios() {
        assertEquals(16f / 9f, VideoAspectMode.resolve(VideoAspectMode.RATIO_16_9, 0f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(4f / 3f, VideoAspectMode.resolve(VideoAspectMode.RATIO_4_3, 0f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(21f / 9f, VideoAspectMode.resolve(VideoAspectMode.RATIO_21_9, 0f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(64f / 27f, VideoAspectMode.resolve(VideoAspectMode.ADAPTIVE, 64f / 27f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(32f / 9f, VideoAspectMode.resolve(VideoAspectMode.CUSTOM, 0f, 32f / 9f).targetAspectRatio(), 0.0001f);
    }

    @Test
    public void invalidDynamicRatiosUseSafeFallbacks() {
        assertEquals(16f / 9f, VideoAspectMode.resolve(VideoAspectMode.ADAPTIVE, 0f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(16f / 9f, VideoAspectMode.resolve(VideoAspectMode.ADAPTIVE, 100f, 0f).targetAspectRatio(), 0.0001f);
        assertEquals(21f / 9f, VideoAspectMode.resolve(VideoAspectMode.CUSTOM, 0f, Float.NaN).targetAspectRatio(), 0.0001f);
        assertEquals(21f / 9f, VideoAspectMode.resolve(VideoAspectMode.CUSTOM, 0f, 100f).targetAspectRatio(), 0.0001f);
    }

    @Test
    public void addedAspectModesUseFitLayoutAndNativeOverride() {
        for (int mode : new int[]{VideoAspectMode.RATIO_21_9, VideoAspectMode.ADAPTIVE, VideoAspectMode.CUSTOM}) {
            VideoAspectMode.Spec spec = VideoAspectMode.resolve(mode, 64f / 27f, 64f / 27f);
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, spec.resizeMode());
            assertTrue(spec.hasTargetAspectRatio());
            assertFalse(spec.stretch());
        }
    }

    @Test
    public void fillIsTheOnlyStretchMode() {
        assertTrue(VideoAspectMode.resolve(VideoAspectMode.FILL, 0f, 0f).stretch());
        assertFalse(VideoAspectMode.resolve(VideoAspectMode.ORIGINAL, 0f, 0f).stretch());
        assertFalse(VideoAspectMode.resolve(VideoAspectMode.ZOOM, 0f, 0f).stretch());
    }
}
