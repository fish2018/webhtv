package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class WebViewDataDirectoryGuard {

    private static final String TAG = "WebViewDataGuard";
    private static final int MAX_PROCESS_FILE_BYTES = 4096;

    private WebViewDataDirectoryGuard() {
    }

    public static void clearStaleLock(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            File dataDir = new File(context.getApplicationInfo().dataDir);
            if (clearStaleLock(dataDir, new File("/proc"), Process.myPid())) {
                Log.w(TAG, "Removed stale WebView data lock");
            }
        } catch (Throwable e) {
            Log.w(TAG, "Unable to inspect WebView data lock", e);
        }
    }

    static boolean clearStaleLock(File dataDir, File procRoot, int currentPid) {
        File lock = new File(new File(dataDir, "app_webview"), "webview_data.lock");
        LockOwner owner = readLockOwner(lock);
        if (owner == null || owner.pid == currentPid || !isStale(owner, procRoot)) return false;
        LockOwner latest = readLockOwner(lock);
        if (latest == null || latest.pid != owner.pid || !latest.processName.equals(owner.processName)) return false;
        return lock.delete();
    }

    private static LockOwner readLockOwner(File lock) {
        if (!lock.isFile()) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(lock))) {
            int pid = input.readInt();
            String processName = input.readUTF();
            if (pid <= 0 || processName.isEmpty()) return null;
            return new LockOwner(pid, processName);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean isStale(LockOwner owner, File procRoot) {
        File processDir = new File(procRoot, String.valueOf(owner.pid));
        if (!processDir.isDirectory()) return true;
        String status = readText(new File(processDir, "status"));
        if (isDeadState(status)) return true;
        String processName = readProcessName(new File(processDir, "cmdline"));
        return !processName.isEmpty() && !owner.processName.equals(processName);
    }

    private static boolean isDeadState(String status) {
        for (String line : status.split("\\R")) {
            if (!line.startsWith("State:")) continue;
            String state = line.substring("State:".length()).trim();
            return state.startsWith("Z") || state.startsWith("X");
        }
        return false;
    }

    private static String readProcessName(File file) {
        String value = readText(file);
        int end = value.indexOf('\0');
        return (end >= 0 ? value.substring(0, end) : value).trim();
    }

    private static String readText(File file) {
        if (!file.isFile()) return "";
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int total = 0;
            int count;
            while (total < MAX_PROCESS_FILE_BYTES && (count = input.read(buffer, 0, Math.min(buffer.length, MAX_PROCESS_FILE_BYTES - total))) != -1) {
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ignored) {
            return "";
        }
    }

    private static final class LockOwner {
        final int pid;
        final String processName;

        LockOwner(int pid, String processName) {
            this.pid = pid;
            this.processName = processName;
        }
    }
}
