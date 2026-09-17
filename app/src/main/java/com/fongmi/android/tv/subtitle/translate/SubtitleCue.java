package com.fongmi.android.tv.subtitle.translate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SubtitleCue {

    private final int index;
    private final long startMs;
    private final long endMs;
    private final List<String> textLines;

    public SubtitleCue(int index, long startMs, long endMs, List<String> textLines) {
        this.index = index;
        this.startMs = Math.max(0L, startMs);
        this.endMs = Math.max(this.startMs, endMs);
        this.textLines = Collections.unmodifiableList(copyTextLines(textLines));
    }

    public int getIndex() {
        return index;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public List<String> getTextLines() {
        return textLines;
    }

    private static List<String> copyTextLines(List<String> textLines) {
        List<String> values = new ArrayList<>();
        if (textLines == null) return values;
        for (String line : textLines) values.add(line == null ? "" : line);
        return values;
    }
}
