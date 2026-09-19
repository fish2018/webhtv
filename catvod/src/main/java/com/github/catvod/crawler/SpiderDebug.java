package com.github.catvod.crawler;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;
import com.github.catvod.crawler.diagnostics.DiagnosticText;

import java.util.Locale;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static boolean isEnabled() {
        return DebugLogStore.isEnabled();
    }

    /** Capture only: the runtime remains the sole owner of its Logcat/stdout output. */
    public static void console(String tag, String level, String message) {
        if (!DebugLogStore.acceptsTag(tag) || message == null || message.isEmpty()) return;
        String severity = switch (level == null ? "" : level.toUpperCase(Locale.ROOT)) {
            case "DEBUG", "LOG" -> "DEBUG";
            case "WARN", "WARNING" -> "WARN";
            case "ERROR", "FATAL" -> "ERROR";
            case "STDERR" -> "STDERR"; // A stream is not a severity (logging.info can use stderr).
            default -> "INFO";
        };
        try {
            DebugLogStore.add(tag, "[" + severity + "] " + message, severity.equals("ERROR"));
        } catch (RuntimeException ignored) {
            DebugLogStore.collectorFailure();
        }
    }

    public static void log(Throwable th) {
        log(TAG, th);
    }

    public static void log(String tag, Throwable th) {
        if (th == null) return;
        if (!DebugLogStore.acceptsTag(tag)) return;
        String message = DiagnosticText.throwable(th);
        DebugLogStore.add(tag, message, true);
        Logger.t(safeTag(tag)).e(message);
    }

    public static void log(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        if (!DebugLogStore.acceptsTag(TAG)) return;
        DebugLogStore.add(TAG, msg);
        Logger.t(TAG).d(DiagnosticText.clean(msg).text());
    }

    public static void log(String tag, String msg, Object... args) {
        if (TextUtils.isEmpty(msg)) return;
        if (!DebugLogStore.acceptsTag(tag)) return;
        String message = format(msg, args);
        DebugLogStore.add(tag, message);
        Logger.t(safeTag(tag)).d(DiagnosticText.clean(message).text());
    }

    private static String safeTag(String tag) {
        return DiagnosticText.clean(TextUtils.isEmpty(tag) ? TAG : tag).text();
    }

    private static String format(String msg, Object... args) {
        try {
            return args == null || args.length == 0 ? msg : String.format(Locale.US, msg, args);
        } catch (Throwable e) {
            return msg;
        }
    }
}
