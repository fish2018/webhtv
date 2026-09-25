package com.fongmi.android.tv.utils;

import com.github.catvod.utils.Prefers;

public final class CrashRestartMode {

    private static final String KEY = "crash_restart_skip_config_once";

    private CrashRestartMode() {
    }

    public static void arm() {
        // The crash library kills this process immediately after starting the restart activity.
        // Persist synchronously so the new process cannot miss the one-shot marker.
        Prefers.getPrefers().edit().putBoolean(KEY, true).commit();
    }

    public static boolean consume() {
        if (!Prefers.getBoolean(KEY)) return false;
        Prefers.getPrefers().edit().remove(KEY).commit();
        return true;
    }
}
