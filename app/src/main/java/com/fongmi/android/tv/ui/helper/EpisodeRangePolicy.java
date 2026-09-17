package com.fongmi.android.tv.ui.helper;

import java.util.ArrayList;
import java.util.List;

public final class EpisodeRangePolicy {

    public static final int CARD_PAGE_MAX_SIZE = 50;

    private EpisodeRangePolicy() {
    }

    public static int segmentIndex(int size, int selectedIndex, int segmentSize) {
        if (size <= 0) return 0;
        int safeSegmentSize = Math.max(1, segmentSize);
        int safeIndex = Math.max(0, Math.min(selectedIndex, size - 1));
        return safeIndex / safeSegmentSize;
    }

    public static int segmentStart(int size, int selectedIndex, int segmentSize) {
        return segmentIndex(size, selectedIndex, segmentSize) * Math.max(1, segmentSize);
    }

    public static List<Range> build(int size, int selectedIndex, boolean reverse) {
        return build(size, selectedIndex, reverse, 0);
    }

    public static List<Range> build(int size, int selectedIndex, boolean reverse, int maxGroupSize) {
        List<Range> ranges = new ArrayList<>();
        if (size <= 0) return ranges;
        int groupSize = groupSize(size, maxGroupSize);
        int count = (int) Math.ceil(size / (float) groupSize);
        int remainder = size % groupSize;
        for (int i = 0; i < count; i++) {
            int start;
            int end;
            if (reverse && remainder != 0) {
                // Reverse keeps the same episode-number group boundaries as forward order,
                // so the partial group lands first (chunks align to the end of the list).
                start = i == 0 ? 0 : remainder + (i - 1) * groupSize;
                end = i == 0 ? remainder : Math.min(start + groupSize, size);
            } else {
                start = i * groupSize;
                end = Math.min(start + groupSize, size);
            }
            int labelStart = reverse ? size - start : start + 1;
            int labelEnd = reverse ? size - end + 1 : end;
            boolean single = labelStart == labelEnd;
            boolean selected = selectedIndex >= start && selectedIndex < end;
            ranges.add(new Range(single ? String.valueOf(labelStart) : labelStart + "-" + labelEnd, start, end, selected));
        }
        if (ranges.stream().noneMatch(Range::selected)) {
            Range first = ranges.get(0);
            ranges.set(0, first.withSelected(true));
        }
        return ranges;
    }

    public static <T> List<T> slice(List<T> items, Range range) {
        if (items == null || items.isEmpty() || range == null) return List.of();
        int start = Math.max(0, Math.min(range.start(), items.size()));
        int end = Math.max(start, Math.min(range.end(), items.size()));
        return items.subList(start, end);
    }

    public static int selectedPosition(List<Range> ranges) {
        if (ranges == null || ranges.isEmpty()) return 0;
        for (int i = 0; i < ranges.size(); i++) if (ranges.get(i).selected()) return i;
        return 0;
    }

    static int groupSize(int size) {
        return groupSize(size, 0);
    }

    static int groupSize(int size, int maxGroupSize) {
        int groupSize = defaultGroupSize(size);
        if (maxGroupSize > 0) groupSize = Math.min(groupSize, Math.max(1, maxGroupSize));
        return groupSize;
    }

    private static int defaultGroupSize(int size) {
        if (size <= 60) return 20;
        if (size > 2500) return 300;
        if (size > 1500) return 200;
        if (size > 1000) return 150;
        if (size > 500) return 100;
        if (size > 300) return 50;
        return 40;
    }

    public record Range(String label, int start, int end, boolean selected) {

        Range withSelected(boolean selected) {
            return new Range(label, start, end, selected);
        }
    }
}
