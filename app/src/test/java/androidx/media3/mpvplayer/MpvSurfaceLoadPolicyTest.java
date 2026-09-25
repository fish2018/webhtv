package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MpvSurfaceLoadPolicyTest {

    @Test
    public void audioOnly_doesNotWaitForVideoSurface() {
        assertFalse(MpvSurfaceLoadPolicy.shouldWaitForVideoSurface(
                true, false, true, false));
    }

    @Test
    public void audioOnlyStillWaitsWhenOsdSurfaceIsRequired() {
        assertTrue(MpvSurfaceLoadPolicy.shouldWaitForVideoSurface(
                true, true, true, false));
    }

    @Test
    public void videoStillWaitsForVisibleSurfaceView() {
        assertTrue(MpvSurfaceLoadPolicy.shouldWaitForVideoSurface(
                false, false, true, false));
        assertFalse(MpvSurfaceLoadPolicy.shouldWaitForVideoSurface(
                false, false, true, true));
    }
}
