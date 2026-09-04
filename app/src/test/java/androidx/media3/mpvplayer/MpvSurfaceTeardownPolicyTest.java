package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvSurfaceTeardownPolicyTest {

    @Test
    public void transientSurfaceLossKeepsAttachAndDetachEnabled() {
        MpvSurfaceTeardownPolicy policy = new MpvSurfaceTeardownPolicy();

        assertTrue(policy.shouldBindSurface());
        assertTrue(policy.shouldDetachSurface());
    }

    @Test
    public void terminalReleaseBlocksAttachAndDetach() {
        MpvSurfaceTeardownPolicy policy = new MpvSurfaceTeardownPolicy();

        assertTrue(policy.requestTerminalRelease());
        assertFalse(policy.shouldBindSurface());
        assertFalse(policy.shouldDetachSurface());
    }

    @Test
    public void repeatedTerminalReleaseIsIdempotent() {
        MpvSurfaceTeardownPolicy policy = new MpvSurfaceTeardownPolicy();

        assertTrue(policy.requestTerminalRelease());
        assertFalse(policy.requestTerminalRelease());
        assertFalse(policy.shouldBindSurface());
        assertFalse(policy.shouldDetachSurface());
    }
}
