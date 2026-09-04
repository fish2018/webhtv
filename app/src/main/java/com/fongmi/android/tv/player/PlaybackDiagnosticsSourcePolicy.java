package com.fongmi.android.tv.player;

/** Source-aware policy for diagnostics that must not treat local I/O as network traffic. */
public final class PlaybackDiagnosticsSourcePolicy {

    private PlaybackDiagnosticsSourcePolicy() {
    }

    public static boolean isLocal(String source) {
        if (source == null || source.isBlank()) return false;
        String value = source.trim();
        if (value.startsWith("/")) return true;
        return PlaybackRoute.resolve(value).location() == PlaybackRoute.Location.LOCAL;
    }
}
