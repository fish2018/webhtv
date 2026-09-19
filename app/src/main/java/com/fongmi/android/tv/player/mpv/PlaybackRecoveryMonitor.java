package com.fongmi.android.tv.player.mpv;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.ui.activity.PlaybackRecoveryActivity;
import com.github.catvod.crawler.SpiderDebug;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Called only from mpv's watchdog thread. No JNI, player locks or UI-thread I/O. */
public final class PlaybackRecoveryMonitor {
    public static final String PROCESS_SUFFIX = ":playback_recovery";
    public static final String EXTRA_TOKEN = "recovery_token";
    private static final int FILE_VERSION = 1;
    private final Context context;
    private String token;
    private boolean prompted;

    public record Request(String token, PlaybackRecoveryPolicy.Target target,
                          long heartbeatMs, boolean armed, String status, String trace) {}

    public PlaybackRecoveryMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    public static String mainProcessName(Context context) {
        return context.getApplicationInfo().processName;
    }

    public static boolean isRecoveryProcess(Context context) {
        String name = Build.VERSION.SDK_INT >= 28 ? Application.getProcessName()
                : readSmallFile("/proc/self/cmdline");
        int nul = name == null ? -1 : name.indexOf('\0');
        if (nul >= 0) name = name.substring(0, nul);
        return (mainProcessName(context) + PROCESS_SUFFIX).equals(name);
    }

    public void onStall(long heartbeatPostedMs, long nowMs, String trace) {
        if (prompted || heartbeatPostedMs <= 0
                || nowMs - heartbeatPostedMs < PlaybackRecoveryPolicy.STALL_MS) return;
        Activity foreground = App.activity();
        boolean visible = foreground != null && !foreground.isFinishing() && !foreground.isDestroyed();
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) visible = false;
        if (!PlaybackRecoveryPolicy.shouldPrompt(nowMs, heartbeatPostedMs, visible, prompted)) return;
        prompted = true; // At most one prompt for this uninterrupted stall.
        PlaybackRecoveryPolicy.Target target = readTarget(Process.myPid());
        if (target == null || target.uid() != Process.myUid()
                || !mainProcessName(context).equals(target.processName())) {
            PlaybackTrace.log("mpv-recovery", trace, "not armed: process identity unavailable");
            return;
        }
        token = UUID.randomUUID().toString(); // No random/IO work on normal player startup.
        Request request = new Request(token, target, heartbeatPostedMs, true, "stalled", trace);
        if (!writeRequest(context, request, false)) {
            PlaybackTrace.log("mpv-recovery", trace, "not armed: recovery state could not be persisted");
            return;
        }
        StringBuilder stack = new StringBuilder();
        StackTraceElement[] frames = Looper.getMainLooper().getThread().getStackTrace();
        for (int i = 0; i < Math.min(frames.length, 20); i++) {
            if (i > 0) stack.append(" <- ");
            stack.append(frames[i]);
        }
        PlaybackTrace.log("mpv-recovery", trace,
                "main heartbeat stalled=%dms pid=%d start=%d launching separate recovery process; stack=%s",
                nowMs - heartbeatPostedMs, target.pid(), target.startTicks(), stack);
        try {
            context.startActivity(new Intent(context, PlaybackRecoveryActivity.class)
                    .putExtra(EXTRA_TOKEN, token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            PlaybackTrace.log("mpv-recovery", trace, "recovery launch rejected: %s", error.getClass().getSimpleName());
            // Background-activity restrictions are not permission to kill the process.
            disarm("launch-rejected");
        }
    }

    public void onHeartbeat() {
        if (!prompted) return;
        disarm("responsive-again");
        prompted = false;
    }

    public void close() {
        if (prompted) disarm("watchdog-stopped");
    }

    private void disarm(String status) {
        if (token == null) return;
        // Only watchdog threads write the request. Serialize old/new watchdogs
        // and cross-process readers, including AtomicFile's legacy .bak path.
        try (RandomAccessFile lockFile = lockFile(context);
             FileLock ignored = lockFile.getChannel().lock()) {
            Request current = readUnlocked(stateFile(context, false));
            if (current != null && token.equals(current.token()) && current.armed()) {
                writeUnlocked(stateFile(context, false), new Request(token, current.target(),
                        current.heartbeatMs(), false, status, current.trace()));
                PlaybackTrace.log("mpv-recovery", current.trace(), "recovery disarmed: %s", status);
            }
        } catch (IOException | RuntimeException error) {
            Log.w("MpvRecovery", "cannot disarm recovery request", error);
        }
    }

    public static Request requestForToken(Context context, String token) {
        Request request = readRequest(context, false);
        return token != null && request != null && token.equals(request.token()) ? request : null;
    }

    public static boolean mayEndProcess(Context context, Request request) {
        Request current = request == null ? null : requestForToken(context, request.token());
        return current != null && current.armed() && request.equals(current)
                && SystemClock.elapsedRealtime() - current.heartbeatMs() >= PlaybackRecoveryPolicy.STALL_MS
                && PlaybackRecoveryPolicy.mayTerminate(current.target(), readTarget(current.target().pid()),
                    Process.myPid(), Process.myUid(), mainProcessName(context));
    }

    /** Persist the user's action before termination; the next main process logs it. */
    public static boolean recordUserExit(Context context, Request request) {
        return mayEndProcess(context, request) && writeRequest(context, new Request(request.token(),
                request.target(), request.heartbeatMs(), false, "user-confirmed-exit", request.trace()), true);
    }

    public static void logPreviousResult(Context context) {
        Request request = readRequest(context, true);
        if (request == null) request = readRequest(context, false);
        if (request == null) return;
        SpiderDebug.log("mpv-recovery", "previous pid=%d start=%d status=%s trace=%s; no automatic playback",
                request.target().pid(), request.target().startTicks(), request.status(), request.trace());
    }

    public static PlaybackRecoveryPolicy.Target readTarget(int pid) {
        if (pid <= 0) return null;
        try {
            String prefix = "/proc/" + pid;
            long ticks = PlaybackRecoveryPolicy.processStartTicks(readSmallFile(prefix + "/stat"));
            String name = readSmallFile(prefix + "/cmdline");
            if (name == null || ticks <= 0) return null;
            int nul = name.indexOf('\0');
            if (nul >= 0) name = name.substring(0, nul);
            int uid = PlaybackRecoveryPolicy.processUid(readSmallFile(prefix + "/status"));
            if (uid < 0) return null;
            if (ticks != PlaybackRecoveryPolicy.processStartTicks(readSmallFile(prefix + "/stat"))) return null;
            return new PlaybackRecoveryPolicy.Target(pid, uid, ticks, name);
        } catch (Exception error) {
            return null;
        }
    }

    private static String readSmallFile(String path) {
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[4096];
            int size = input.read(buffer);
            return size < 0 ? "" : new String(buffer, 0, size, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    private static AtomicFile stateFile(Context context, boolean result) {
        return new AtomicFile(new File(context.getCacheDir(),
                result ? "mpv-playback-recovery.result" : "mpv-playback-recovery.state"));
    }

    private static RandomAccessFile lockFile(Context context) throws IOException {
        return new RandomAccessFile(new File(context.getCacheDir(), "mpv-playback-recovery.lock"), "rw");
    }

    private static Request readRequest(Context context, boolean result) {
        AtomicFile file = stateFile(context, result);
        if (!file.getBaseFile().isFile()
                && !new File(file.getBaseFile().getPath() + ".bak").isFile()) return null;
        // Recovery UI never waits behind a disk writer. Unknown/busy means no
        // authorization to terminate, not permission to use a stale request.
        try (RandomAccessFile lockFile = lockFile(context);
             FileLock lock = lockFile.getChannel().tryLock()) {
            return lock == null ? null : readUnlocked(file);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static Request readUnlocked(AtomicFile file) {
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            if (input.readInt() != FILE_VERSION) return null;
            String token = input.readUTF();
            PlaybackRecoveryPolicy.Target target = new PlaybackRecoveryPolicy.Target(
                    input.readInt(), input.readInt(), input.readLong(), input.readUTF());
            return new Request(token, target, input.readLong(), input.readBoolean(),
                    input.readUTF(), input.readUTF());
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static boolean writeRequest(Context context, Request request, boolean result) {
        try (RandomAccessFile lockFile = lockFile(context);
             FileLock ignored = lockFile.getChannel().lock()) {
            return writeUnlocked(stateFile(context, result), request);
        } catch (IOException | RuntimeException error) {
            Log.w("MpvRecovery", "cannot lock recovery state", error);
            return false;
        }
    }

    private static boolean writeUnlocked(AtomicFile file, Request request) {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream output = new DataOutputStream(stream);
            output.writeInt(FILE_VERSION);
            output.writeUTF(request.token());
            output.writeInt(request.target().pid());
            output.writeInt(request.target().uid());
            output.writeLong(request.target().startTicks());
            output.writeUTF(request.target().processName());
            output.writeLong(request.heartbeatMs());
            output.writeBoolean(request.armed());
            output.writeUTF(request.status());
            output.writeUTF(request.trace() == null ? "" : request.trace());
            output.flush();
            file.finishWrite(stream);
            return true;
        } catch (IOException | RuntimeException error) {
            if (stream != null) file.failWrite(stream);
            Log.w("MpvRecovery", "cannot save recovery state", error);
            return false;
        }
    }
}
