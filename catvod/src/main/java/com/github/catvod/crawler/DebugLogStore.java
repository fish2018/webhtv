package com.github.catvod.crawler;

import android.text.TextUtils;

import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class DebugLogStore {

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final ThreadLocal<SimpleDateFormat> FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));
    private static final String FILE_NAME = "webhtv-debug-log.txt";
    private static final String PREF_ENABLED = "debug_log";
    private static final int MAX_MESSAGE_CHARS = 12000;
    private static final int MAX_LINES = 2000;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static long version;
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
        if (!enabled) return;
        synchronized (LOCK) {
            loadLocked();
        }
        add("debug", "调试日志已恢复");
    }

    public static void add(String tag, String msg) {
        if (!isEnabled()) return;
        if (TextUtils.isEmpty(msg)) return;
        String line = FORMAT.get().format(new Date()) + " [" + Thread.currentThread().getName() + "] " + safe(tag) + ": " + limit(msg);
        synchronized (LOCK) {
            LINES.addLast(line);
            trimLines(LINES, MAX_LINES);
            version++;
            writeLocked(line);
        }
    }

    public static String text() {
        if (!isEnabled()) return "调试日志未开启";
        List<String> copy;
        synchronized (LOCK) {
            if (LINES.isEmpty()) loadLocked();
            copy = new ArrayList<>(LINES);
        }
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
            if (enabled && LINES.isEmpty()) loadLocked();
            return LINES.size();
        }
    }

    public static long bytes() {
        try {
            File file = file();
            return file != null && file.exists() ? file.length() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    public static long version() {
        return version;
    }

    public static void clear() {
        synchronized (LOCK) {
            LINES.clear();
            version++;
            delete();
        }
    }

    private static String safe(String tag) {
        return TextUtils.isEmpty(tag) ? "Debug" : tag;
    }

    private static String limit(String msg) {
        if (msg.length() <= MAX_MESSAGE_CHARS) return msg;
        return msg.substring(0, MAX_MESSAGE_CHARS) + " ...(truncated " + (msg.length() - MAX_MESSAGE_CHARS) + " chars)";
    }

    private static File file() {
        try {
            return new File(Init.context().getCacheDir(), FILE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeLocked(String line) {
        try {
            File file = file();
            if (file == null) return;
            byte[] data = (line + "\n").getBytes(StandardCharsets.UTF_8);
            if (file.length() + data.length > MAX_FILE_BYTES) {
                rewriteLocked(file);
                return;
            }
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write(data);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void loadLocked() {
        try {
            File file = file();
            if (file == null || !file.exists()) return;
            String text = readTail(file, MAX_FILE_BYTES);
            if (TextUtils.isEmpty(text)) return;
            LINES.clear();
            for (String line : text.split("\\r?\\n")) {
                if (!TextUtils.isEmpty(line)) LINES.addLast(line);
            }
            trimLines(LINES, MAX_LINES);
            if (file.length() > MAX_FILE_BYTES) rewriteLocked(file);
        } catch (Throwable e) {
        }
    }

    static void trimLines(ArrayDeque<String> lines, int maxLines) {
        while (lines.size() > maxLines) lines.removeFirst();
    }

    static String readTail(File file, int maxBytes) throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            long start = Math.max(0, length - maxBytes);
            boolean startsOnLine = start == 0;
            if (start > 0) {
                input.seek(start - 1);
                startsOnLine = input.read() == '\n';
            }
            input.seek(start);
            byte[] data = new byte[(int) (length - start)];
            input.readFully(data);
            int offset = 0;
            if (!startsOnLine) {
                while (offset < data.length && data[offset] != '\n') offset++;
                if (offset < data.length) offset++;
            }
            return new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
        }
    }

    private static void rewriteLocked(File file) throws Exception {
        ArrayDeque<String> retained = new ArrayDeque<>();
        int bytes = 0;
        Iterator<String> iterator = LINES.descendingIterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            int size = line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes + size > MAX_FILE_BYTES) break;
            retained.addFirst(line);
            bytes += size;
        }
        LINES.clear();
        LINES.addAll(retained);
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            for (String line : LINES) stream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
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
