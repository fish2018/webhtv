package androidx.media3.mpvplayer;

/** Side-effect-free decision for the initial MPV surface gate. */
final class MpvSurfaceLoadPolicy {

    private MpvSurfaceLoadPolicy() {
    }

    static boolean shouldWaitForVideoSurface(
            boolean audioOnly, boolean osdSurfaceRequired,
            boolean surfaceViewShown, boolean videoSurfaceReady) {
        if (audioOnly && !osdSurfaceRequired) return false;
        return surfaceViewShown && !videoSurfaceReady;
    }
}
