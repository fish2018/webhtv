package com.fongmi.android.tv.utils;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

import java.util.List;

public final class PreviousProcessExitLogger {

    private static final String KEY_LAST_EXIT_TIMESTAMP = "debug_last_process_exit_timestamp";

    private PreviousProcessExitLogger() {
    }

    public static void log(Context context) {
        if (!SpiderDebug.isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            logApi30(context.getApplicationContext());
        } catch (Throwable error) {
            SpiderDebug.log("process-exit", "query failed error=%s", error.getClass().getSimpleName());
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void logApi30(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return;
        List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 1);
        if (exits.isEmpty()) return;
        ApplicationExitInfo exit = exits.get(0);
        long timestamp = exit.getTimestamp();
        if (timestamp <= Prefers.getLong(KEY_LAST_EXIT_TIMESTAMP, 0)) return;
        Prefers.put(KEY_LAST_EXIT_TIMESTAMP, timestamp);
        SpiderDebug.log("process-exit",
                "previous reason=%s(%d) status=%d importance=%d timestamp=%d pss=%d rss=%d description=%s",
                reasonName(exit.getReason()), exit.getReason(), exit.getStatus(), exit.getImportance(),
                timestamp, exit.getPss(), exit.getRss(), safe(exit.getDescription()));
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String reasonName(int reason) {
        return switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH -> "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR -> "ANR";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_OTHER -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
