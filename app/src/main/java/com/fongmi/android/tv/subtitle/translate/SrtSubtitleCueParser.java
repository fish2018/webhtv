package com.fongmi.android.tv.subtitle.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SrtSubtitleCueParser {

    private static final Pattern TIMING = Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2},\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2},\\d{3}).*");

    public List<SubtitleCue> parse(String text) {
        List<SubtitleCue> cues = new ArrayList<>();
        String[] blocks = normalize(text).split("\\n\\s*\\n");
        for (String block : blocks) {
            SubtitleCue cue = parseBlock(block);
            if (cue != null) cues.add(cue);
        }
        return cues;
    }

    private SubtitleCue parseBlock(String block) {
        String[] lines = block.split("\\n", -1);
        int offset = 0;
        while (offset < lines.length && lines[offset].trim().isEmpty()) offset++;
        if (offset >= lines.length) return null;

        int index = -1;
        if (isNumeric(lines[offset])) {
            index = parseIndex(lines[offset]);
            offset++;
        }
        if (offset >= lines.length) return null;

        Matcher matcher = TIMING.matcher(lines[offset].trim());
        if (!matcher.matches()) return null;
        long startMs = parseTimestamp(matcher.group(1));
        long endMs = parseTimestamp(matcher.group(2));
        offset++;

        List<String> textLines = new ArrayList<>();
        while (offset < lines.length) {
            String line = lines[offset++];
            if (!line.trim().isEmpty() || !textLines.isEmpty()) textLines.add(line);
        }
        while (!textLines.isEmpty() && textLines.get(textLines.size() - 1).trim().isEmpty()) {
            textLines.remove(textLines.size() - 1);
        }
        return new SubtitleCue(index, startMs, endMs, textLines);
    }

    private static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        String value = text;
        if (value.charAt(0) == '\uFEFF') value = value.substring(1);
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static boolean isNumeric(String line) {
        return line != null && line.trim().matches("\\d+");
    }

    private static int parseIndex(String line) {
        try {
            return Integer.parseInt(line.trim());
        } catch (Throwable e) {
            return -1;
        }
    }

    private static long parseTimestamp(String value) {
        String[] parts = value.split("[:,]");
        long hours = Long.parseLong(parts[0]);
        long minutes = Long.parseLong(parts[1]);
        long seconds = Long.parseLong(parts[2]);
        long millis = Long.parseLong(parts[3]);
        return (((hours * 60L) + minutes) * 60L + seconds) * 1000L + millis;
    }
}
