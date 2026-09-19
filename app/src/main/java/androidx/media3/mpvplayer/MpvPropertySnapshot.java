package androidx.media3.mpvplayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Event-owned metadata. Missing metadata must never trigger a synchronous JNI read. */
final class MpvPropertySnapshot {
    static final int MAX_JSON_CHARS = 512 * 1024;
    private final Set<String> observed = new HashSet<>();
    private final Map<String, Object> values = new HashMap<>();
    private TrackList tracks = TrackList.empty();
    private long generation;
    record DiagnosticValue(Object value, long generation, long updatedAtMs, String status) {}
    record DiagnosticSnapshot(long generation, Map<String, DiagnosticValue> values, Map<String, Integer> registrations,
                              TrackList tracks, boolean tracksObserved, long lateEvents, long nodeErrors) {}
    private final Map<String, DiagnosticValue> diagnosticValues = new HashMap<>();
    private final Map<String, Integer> registrations = new HashMap<>();
    private TrackList diagnosticTracks = TrackList.empty();
    private boolean diagnosticTracksObserved;
    private long diagnosticGeneration, diagnosticCapture = -1, lateEvents, nodeErrors;

    synchronized void diagnosticBegin(long nextGeneration) {
        diagnosticGeneration = nextGeneration;
        diagnosticValues.entrySet().removeIf(entry -> !Set.of("mpv-version", "ffmpeg-version", "options/msg-level", "hwdec",
                "gpu-api", "volume", "mute", "speed", "audio-device", "audio-delay", "audio-spdif").contains(entry.getKey()));
        diagnosticValues.replaceAll((name, value) -> new DiagnosticValue(value.value(), nextGeneration, value.updatedAtMs(), value.status()));
        diagnosticTracks = TrackList.empty(); diagnosticTracksObserved = false;
    }

    synchronized void diagnosticRegister(String property, int result) { registrations.put(property, result); }

    synchronized void diagnosticUpdate(long eventGeneration, String property, Object value, long nowMs, long capture) {
        if (diagnosticCapture != capture) {
            diagnosticCapture = capture; diagnosticValues.clear(); diagnosticTracks = TrackList.empty();
            diagnosticTracksObserved = false; lateEvents = nodeErrors = 0;
        }
        if (eventGeneration != diagnosticGeneration) { lateEvents++; return; }
        if ("track-list".equals(property)) {
            diagnosticTracks = value instanceof TrackList list ? list : TrackList.empty();
            diagnosticTracksObserved = value instanceof TrackList;
            if (!diagnosticTracks.valid()) nodeErrors++;
        } else if (MpvDiagnosticCollector.propertyAllowed(property)) {
            Object bounded = value instanceof String text && text.length() > 2048 ? text.substring(0, 2048) : value;
            diagnosticValues.put(property, new DiagnosticValue(bounded, eventGeneration, nowMs,
                    value == null ? "unavailable" : "known"));
        }
    }

    synchronized DiagnosticSnapshot diagnosticSnapshot(long capture) {
        boolean current = diagnosticCapture == capture;
        return new DiagnosticSnapshot(diagnosticGeneration, current ? Map.copyOf(diagnosticValues) : Map.of(), Map.copyOf(registrations),
                current ? diagnosticTracks : TrackList.empty(), current && diagnosticTracksObserved,
                current ? lateEvents : 0, current ? nodeErrors : 0);
    }

    record TrackList(List<Map<String, Object>> entries, boolean valid) {
        static TrackList empty() {
            return new TrackList(Collections.emptyList(), true);
        }

        static TrackList parse(String json) {
            if (json == null) return empty();
            if (json.length() > MAX_JSON_CHARS || !boundedDepth(json)) return invalid();
            try {
                JsonElement root = JsonParser.parseString(json);
                if (root.isJsonNull()) return empty();
                if (!root.isJsonArray() || root.getAsJsonArray().size() > 1024) return invalid();
                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject() || element.getAsJsonObject().size() > 128) return invalid();
                    Map<String, Object> fields = new HashMap<>();
                    for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                        if (!field.getValue().isJsonPrimitive()) continue;
                        JsonPrimitive value = field.getValue().getAsJsonPrimitive();
                        Object scalar = value.isBoolean() ? value.getAsBoolean()
                                : value.isNumber() ? value.getAsNumber() : value.getAsString();
                        fields.put(field.getKey(), scalar);
                    }
                    result.add(Collections.unmodifiableMap(fields));
                }
                return new TrackList(Collections.unmodifiableList(result), true);
            } catch (RuntimeException error) {
                return invalid();
            }
        }

        private static TrackList invalid() {
            return new TrackList(Collections.emptyList(), false);
        }

        private static boolean boundedDepth(String text) {
            int depth = 0;
            boolean quoted = false;
            boolean escaped = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') quoted = false;
                } else if (c == '"') quoted = true;
                else if (c == '[' || c == '{') {
                    if (++depth > 12) return false;
                } else if ((c == ']' || c == '}') && --depth < 0) return false;
            }
            return depth == 0 && !quoted;
        }
    }

    synchronized void register(String property) {
        observed.add(property);
    }

    synchronized boolean contains(String property) {
        return property.startsWith("track-list/")
                || observed.contains(property) && isMetadataRead(property);
    }

    private static boolean isMetadataRead(String property) {
        if (property.startsWith("video-params/") || property.startsWith("video-out-params/")) return true;
        return switch (property) {
            case "track-list", "chapter", "chapter-list", "width", "height",
                    "video-bitrate", "audio-bitrate", "container-fps", "estimated-vf-fps",
                    "vid", "aid", "sid", "secondary-sid", "sub-visibility",
                    "current-tracks/video/id", "current-tracks/audio/id",
                    "current-tracks/sub/id", "current-tracks/sub2/id",
                    "current-tracks/video/demux-w", "current-tracks/video/demux-h" -> true;
            // Preserve existing stale-observer cache probes and the one-shot
            // DTS-HD fallback query. This is not a global getter replacement.
            default -> false;
        };
    }

    synchronized void beginFile(long nextGeneration) {
        generation = nextGeneration;
        tracks = TrackList.empty();
        // Option observations can stay unchanged across a new file. File data cannot.
        values.keySet().removeIf(key -> key.startsWith("current-tracks/")
                || key.startsWith("video-")
                || key.startsWith("audio-") && !key.equals("audio-device")
                || key.startsWith("demuxer-cache-") || key.startsWith("time-pos")
                || key.startsWith("duration") || key.startsWith("track-list")
                || key.equals("width") || key.equals("height")
                || key.equals("container-fps") || key.equals("estimated-vf-fps")
                || key.equals("chapter") || key.equals("chapter-list"));
    }

    synchronized boolean update(long eventGeneration, String property, Object value) {
        if (eventGeneration != generation) return false;
        if (property.equals("track-list")) {
            tracks = value instanceof TrackList list ? list : TrackList.empty();
        } else if (value == null) {
            values.remove(property);
        } else {
            values.put(property, value);
        }
        return true;
    }

    synchronized void acceptedWrite(String property, Object value) {
        if (observed.contains(property)) values.put(property, value);
    }

    private synchronized Object value(String property) {
        if (property.equals("track-list/count")) return tracks.entries().size();
        if (!property.startsWith("track-list/")) return values.get(property);
        String[] parts = property.split("/", 3);
        if (parts.length != 3) return null;
        try {
            int index = Integer.parseInt(parts[1]);
            return index < 0 || index >= tracks.entries().size()
                    ? null : tracks.entries().get(index).get(parts[2]);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    String string(String property) {
        Object value = value(property);
        return value instanceof Boolean flag ? flag ? "yes" : "no"
                : value == null ? null : value.toString();
    }

    Integer integer(String property) {
        try {
            Object value = value(property);
            if (value == null) return null;
            long number = value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
            return number < Integer.MIN_VALUE || number > Integer.MAX_VALUE ? null : (int) number;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    Double decimal(String property) {
        try {
            Object value = value(property);
            if (value == null) return null;
            double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    Boolean flag(String property) {
        Object value = value(property);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value == null) return null;
        return switch (value.toString()) {
            case "yes", "true", "1" -> true;
            case "no", "false", "0" -> false;
            default -> null;
        };
    }
}
