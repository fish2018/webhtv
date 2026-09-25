package com.fongmi.android.tv.utils;

import java.util.Locale;

/** Formats HLS segment positions using the same clock style as the player timeline. */
public final class AdBlockTimeFormatter {

    private static final long TENTHS_PER_SECOND = 10L;
    private static final long TENTHS_PER_MINUTE = 60L * TENTHS_PER_SECOND;
    private static final long TENTHS_PER_HOUR = 60L * TENTHS_PER_MINUTE;

    private AdBlockTimeFormatter() {
    }

    /**
     * Formats a media position or duration as {@code HH:MM:SS.t}.
     *
     * <p>The stored HLS timings contain milliseconds, so retaining one decimal place avoids
     * hiding the difference between adjacent short segments while still looking like a player
     * timeline. Rounding is done before splitting the fields so values such as 59.96 seconds
     * correctly carry into the next minute.</p>
     */
    public static String formatSeconds(double seconds) {
        double safeSeconds = Double.isFinite(seconds) ? Math.max(0d, seconds) : 0d;
        long tenths = Math.round(safeSeconds * TENTHS_PER_SECOND);
        long hours = tenths / TENTHS_PER_HOUR;
        long minutes = (tenths % TENTHS_PER_HOUR) / TENTHS_PER_MINUTE;
        long wholeSeconds = (tenths % TENTHS_PER_MINUTE) / TENTHS_PER_SECOND;
        long fractionalSecond = tenths % TENTHS_PER_SECOND;
        return String.format(Locale.US, "%02d:%02d:%02d.%d",
                hours, minutes, wholeSeconds, fractionalSecond);
    }
}
