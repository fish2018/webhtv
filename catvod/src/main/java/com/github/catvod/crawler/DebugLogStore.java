package com.github.catvod.crawler;

import android.text.TextUtils;

import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DebugLogStore {

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final ThreadLocal<SimpleDateFormat> FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));
    private static final String FILE_NAME = "webhtv-debug-log.txt";
    private static final String PREF_ENABLED = "debug_log";
    private static volatile boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        DebugLogStore.enabled = enabled;
        Prefers.put(PREF_ENABLED, enabled);
        if (enabled) add("debug", "调试日志已开启");
        else clear();
    }

    public static void restoreEnabled() {
        enabled = Prefers.getBoolean(PREF_ENABLED);
        if (enabled) add("debug", "调试日志已恢复");
    }

    public static void add(String tag, String msg) {
        if (!isEnabled()) return;
        if (TextUtils.isEmpty(msg)) return;
        String line = FORMAT.get().format(new Date()) + " [" + Thread.currentThread().getName() + "] " + safe(tag) + ": " + msg;
        synchronized (LOCK) {
            LINES.addLast(line);
            write(line);
        }
    }

    public static String text() {
        if (!isEnabled()) return "调试日志未开启";
        String file = read();
        if (!TextUtils.isEmpty(file)) return file;
        List<String> copy = snapshot();
        if (copy.isEmpty()) return "暂无调试日志";
        StringBuilder builder = new StringBuilder();
        for (String line : copy) builder.append(line).append('\n');
        return builder.toString();
    }

    public static List<String> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(LINES);
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return LINES.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            LINES.clear();
            delete();
        }
    }

    private static String safe(String tag) {
        return TextUtils.isEmpty(tag) ? "Debug" : tag;
    }

    private static File file() {
        try {
            return new File(Init.context().getCacheDir(), FILE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void write(String line) {
        try {
            File file = file();
            if (file == null) return;
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String read() {
        try {
            File file = file();
            if (file == null || !file.exists()) return "";
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream stream = new FileInputStream(file)) {
                int read = stream.read(data);
                return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Throwable e) {
            return "";
        }
    }

    private static void delete() {
        try {
            File file = file();
            if (file != null && file.exists()) file.delete();
        } catch (Throwable ignored) {
        }
    }
}
