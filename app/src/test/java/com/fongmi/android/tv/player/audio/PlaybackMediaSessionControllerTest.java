package com.fongmi.android.tv.player.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackMediaSessionControllerTest {

    @Test
    public void seekInvalidatesSignalsBeforePlayerCallback() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(4);
        PlaybackMediaClock clock = new PlaybackMediaClock(500L);
        PlaybackMediaSessionController controller = new PlaybackMediaSessionController(hub, clock);
        PlaybackMediaSignalHub.Session before = controller.begin(10_000L);

        PlaybackMediaSignalHub.Session after = controller.beforeSeek(80_000L);

        assertNotEquals(before.generation(), after.generation());
        assertEquals(80_000L, after.mediaAnchorMs());
        assertEquals(after.generation(), clock.snapshot(0L).generation());
    }

    @Test
    public void releaseClosesHub() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(2);
        PlaybackMediaSessionController controller = new PlaybackMediaSessionController(
                hub, new PlaybackMediaClock(500L));
        controller.begin(0L);
        controller.release();
        assertTrue(!hub.isPipelineAttached());
        assertThrowsIllegalState(() -> hub.beginSession(0L));
    }

    @Test
    public void beforeReleaseInvalidatesPipelineBeforeEngineTeardown() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(2);
        PlaybackMediaSessionController controller = new PlaybackMediaSessionController(
                hub, new PlaybackMediaClock(500L));
        controller.begin(0L);
        hub.attachPipeline();

        controller.beforeRelease();

        assertTrue(!hub.isPipelineAttached());
    }

    private static void assertThrowsIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalStateException");
    }
}
