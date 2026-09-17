package com.fongmi.android.tv.subtitle.translate;

import java.util.List;
import java.util.Locale;

public final class SrtSubtitleCueWriter {

    public String write(List<SubtitleCue> cues) {
        StringBuilder builder = new StringBuilder();
        if (cues == null) return "";
        int number = 1;
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            if (cue == null) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(number++).append('\n');
            builder.append(formatTimestamp(cue.getStartMs())).append(" --> ").append(formatTimestamp(cue.getEndMs())).append('\n');
            for (String line : cue.getTextLines()) builder.append(line == null ? "" : line).append('\n');
        }
        return builder.toString();
    }

    private static String formatTimestamp(long millis) {
        long value = Math.max(0L, millis);
        long hours = value / 3_600_000L;
        value %= 3_600_000L;
        long minutes = value / 60_000L;
        value %= 60_000L;
        long seconds = value / 1_000L;
        value %= 1_000L;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, value);
    }
}
