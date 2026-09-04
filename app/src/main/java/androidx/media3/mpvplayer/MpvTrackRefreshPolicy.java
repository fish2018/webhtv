package androidx.media3.mpvplayer;

final class MpvTrackRefreshPolicy {

    private static final long NORMAL_DEBOUNCE_MS = 120;
    private static final long STARTUP_QUIET_MS = 2000;
    private static final long STARTUP_WINDOW_MS = 8000;

    private MpvTrackRefreshPolicy() {
    }

    static long delayMs(boolean fileLoaded, long fileLoadedAtMs, long nowMs) {
        return isStartupWindow(fileLoaded, fileLoadedAtMs, nowMs)
                ? STARTUP_QUIET_MS : NORMAL_DEBOUNCE_MS;
    }

    static boolean isStartupWindow(boolean fileLoaded, long fileLoadedAtMs,
                                   long nowMs) {
        if (!fileLoaded) return true;
        if (fileLoadedAtMs <= 0 || nowMs < fileLoadedAtMs) return false;
        return nowMs - fileLoadedAtMs < STARTUP_WINDOW_MS;
    }
}
