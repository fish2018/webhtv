package com.fongmi.android.tv.utils;

import java.util.Locale;

public final class HistoryProgressFormatter {

    private HistoryProgressFormatter() {
    }

    public static String format(long positionMs, long durationMs) {
        if (positionMs <= 0) return "";
        long displayMs = durationMs > 0 ? Math.min(positionMs, durationMs) : positionMs;
        long seconds = displayMs / 1000;
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60);
    }
}
