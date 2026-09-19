package androidx.media3.mpvplayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Maps an existing detector's omissions to source time without rewriting HLS media. */
final class HlsAdTimeline {

    static final HlsAdTimeline NONE = new HlsAdTimeline(List.of(), 0, "no-ads");
    private static final int MAX_SEGMENTS = 100_000;
    // The detector is heuristic. A multi-minute candidate can be programme
    // content, so preserve it instead of making that whole interval unseekable.
    private static final long MAX_AUTO_SKIP_BLOCK_US = 120_000_000;

    private final List<Range> ranges;
    private final long durationUs;
    private final String reason;

    private HlsAdTimeline(List<Range> ranges, long durationUs, String reason) {
        this.ranges = List.copyOf(ranges);
        this.durationUs = durationUs;
        this.reason = reason;
    }

    static HlsAdTimeline from(String original, String filtered) {
        if (original == null || original.equals(filtered)) return NONE;
        Playlist source = parse(original);
        Playlist kept = parse(filtered);
        if (source == null || kept == null || kept.segments().isEmpty()
                || kept.segments().size() >= source.segments().size()) {
            return empty("invalid-or-unchanged-playlist");
        }
        int count = kept.segments().size();
        int[] first = new int[count];
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            Segment segment = kept.segments().get(i);
            while (cursor < source.segments().size()
                    && !segment.equals(source.segments().get(cursor))) cursor++;
            if (cursor == source.segments().size()) return empty("not-a-subsequence");
            first[i] = cursor++;
        }
        // Repeated URIs/durations/ranges must not silently identify the wrong occurrence.
        cursor = source.segments().size() - 1;
        for (int i = count - 1; i >= 0; i--) {
            Segment segment = kept.segments().get(i);
            while (cursor >= 0 && !segment.equals(source.segments().get(cursor))) cursor--;
            if (cursor != first[i]) return empty("ambiguous-segments");
            cursor--;
        }
        List<Range> ranges = new ArrayList<>();
        long positionUs = 0;
        long adStartUs = -1;
        int keptIndex = 0;
        int preservedBlocks = 0;
        for (int i = 0; i < source.segments().size(); i++) {
            boolean retained = keptIndex < count && first[keptIndex] == i;
            // Validate separate source blocks before merging. A false positive
            // programme block must not swallow the short ad next to it.
            if (adStartUs >= 0 && (retained || source.discontinuities().contains(i))) {
                if (!addRange(ranges, adStartUs, positionUs)) preservedBlocks++;
                adStartUs = -1;
            }
            if (retained) {
                keptIndex++;
            } else if (adStartUs < 0) {
                adStartUs = positionUs;
            }
            positionUs += source.segments().get(i).durationUs();
        }
        if (adStartUs >= 0 && !addRange(ranges, adStartUs, positionUs)) preservedBlocks++;
        return new HlsAdTimeline(ranges, positionUs, preservedBlocks == 0
                ? "exo-hls-detector" : "exo-hls-detector-long-blocks-preserved");
    }

    private static boolean addRange(List<Range> ranges, long startUs, long endUs) {
        if (endUs - startUs > MAX_AUTO_SKIP_BLOCK_US) return false;
        // Round inward: never skip extra programme content at sub-millisecond boundaries.
        long startMs = startUs / 1000 + (startUs % 1000 == 0 ? 0 : 1);
        long endMs = endUs / 1000;
        if (endMs > startMs) {
            if (!ranges.isEmpty() && ranges.get(ranges.size() - 1).endMs() == startMs) {
                startMs = ranges.remove(ranges.size() - 1).startMs();
            }
            ranges.add(new Range(startMs, endMs));
        }
        return true;
    }

    private static HlsAdTimeline empty(String reason) {
        return new HlsAdTimeline(List.of(), 0, reason);
    }

    private static Playlist parse(String text) {
        if (text == null) return null;
        String content = text.strip();
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        if (!content.startsWith("#EXTM3U")) return null;
        List<Segment> segments = new ArrayList<>();
        Set<Integer> discontinuities = new HashSet<>();
        long durationUs = -1;
        long totalUs = 0;
        String byteRange = "";
        boolean ended = false;
        try {
            for (String raw : content.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.startsWith("#EXT-X-STREAM-INF:")
                        || line.startsWith("#EXT-X-PART:")
                        || line.startsWith("#EXT-X-SKIP:")
                        || line.equals("#EXT-X-I-FRAMES-ONLY")) return null;
                if (line.equals("#EXT-X-ENDLIST")) {
                    ended = true;
                } else if (line.equals("#EXT-X-DISCONTINUITY")) {
                    discontinuities.add(segments.size());
                } else if (line.startsWith("#EXTINF:")) {
                    if (ended || durationUs >= 0) return null;
                    int comma = line.indexOf(',');
                    String value = line.substring(8, comma < 0 ? line.length() : comma).trim();
                    if (value.length() > 48) return null;
                    BigDecimal seconds = new BigDecimal(value);
                    if (seconds.scale() < -12 || seconds.scale() > 18) return null;
                    durationUs = seconds.movePointRight(6)
                            .setScale(0, RoundingMode.HALF_UP).longValueExact();
                    if (durationUs <= 0) return null;
                } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                    byteRange = line.substring(17).trim();
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    if (ended || durationUs <= 0 || segments.size() >= MAX_SEGMENTS) return null;
                    totalUs = Math.addExact(totalUs, durationUs);
                    segments.add(new Segment(line, durationUs, byteRange));
                    durationUs = -1;
                    byteRange = "";
                }
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            return null;
        }
        return ended && durationUs < 0 && !segments.isEmpty()
                ? new Playlist(segments, discontinuities) : null;
    }

    List<Range> ranges() {
        return ranges;
    }

    String reason() {
        return reason;
    }

    boolean sameCuts(HlsAdTimeline other) {
        return other != null && durationUs == other.durationUs && ranges.equals(other.ranges);
    }

    long skipTargetMs(long positionMs) {
        Range range = rangeAt(positionMs);
        return range == null ? positionMs : range.endMs();
    }

    Range nextRange(long positionMs) {
        int low = 0;
        int high = ranges.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (ranges.get(mid).endMs() <= positionMs) low = mid + 1;
            else high = mid;
        }
        return low == ranges.size() ? null : ranges.get(low);
    }

    private Range rangeAt(long positionMs) {
        int low = 0;
        int high = ranges.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Range range = ranges.get(mid);
            if (positionMs < range.startMs()) high = mid - 1;
            else if (positionMs >= range.endMs()) low = mid + 1;
            else return range;
        }
        return null;
    }

    record Range(long startMs, long endMs) {}

    private record Segment(String uri, long durationUs, String byteRange) {}

    private record Playlist(List<Segment> segments, Set<Integer> discontinuities) {}

    /** Keeps stale position notifications from repeatedly seeking to the same cut end. */
    static final class SkipState {
        private final Set<Range> requested = new HashSet<>();

        long nextTargetMs(HlsAdTimeline timeline, long positionMs) {
            Range range = timeline.rangeAt(positionMs);
            return range != null && requested.add(range) ? range.endMs() : -1;
        }

        void clear() {
            requested.clear();
        }
    }
}
