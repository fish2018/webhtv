package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MpvSurfaceSizePolicyTest {

    @Test
    public void directMediaCodecDoesNotUseGpuSurfaceSizeProperty() {
        assertFalse(MpvSurfaceSizePolicy.usesAndroidSurfaceSize("mediacodec_embed"));
        assertTrue(MpvSurfaceSizePolicy.usesAndroidSurfaceSize("gpu"));
        assertTrue(MpvSurfaceSizePolicy.usesAndroidSurfaceSize("gpu-next"));
    }

    @Test
    public void osdSizeRequiresRequestedValidSurfaceAndDimensions() {
        assertFalse(MpvSurfaceSizePolicy.shouldApplyOsdSize(false, true, 1920, 1080));
        assertFalse(MpvSurfaceSizePolicy.shouldApplyOsdSize(true, false, 1920, 1080));
        assertFalse(MpvSurfaceSizePolicy.shouldApplyOsdSize(true, true, 0, 1080));
        assertTrue(MpvSurfaceSizePolicy.shouldApplyOsdSize(true, true, 1920, 1080));
    }

    @Test
    public void sizeValueRejectsIncompleteDimensions() {
        assertEquals("1920x1080", MpvSurfaceSizePolicy.sizeValue(1920, 1080));
        assertNull(MpvSurfaceSizePolicy.sizeValue(0, 1080));
        assertNull(MpvSurfaceSizePolicy.sizeValue(1920, 0));
    }
}
