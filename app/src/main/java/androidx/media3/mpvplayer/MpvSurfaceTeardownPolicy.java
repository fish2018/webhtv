package androidx.media3.mpvplayer;

final class MpvSurfaceTeardownPolicy {

    private volatile boolean terminalReleaseRequested;

    boolean requestTerminalRelease() {
        if (terminalReleaseRequested) return false;
        terminalReleaseRequested = true;
        return true;
    }

    boolean shouldBindSurface() {
        return !terminalReleaseRequested;
    }

    boolean shouldDetachSurface() {
        return !terminalReleaseRequested;
    }
}
