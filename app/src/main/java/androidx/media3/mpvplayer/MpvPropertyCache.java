package androidx.media3.mpvplayer;

import java.util.HashMap;
import java.util.Map;

final class MpvPropertyCache {

    private final Map<String, Object> values = new HashMap<>();
    private MpvPropertySnapshot.TrackList tracks = MpvPropertySnapshot.TrackList.empty();

    void put(String property, Object value) {
        if (property == null || property.isEmpty()) return;
        if ("track-list".equals(property)) {
            tracks = value instanceof MpvPropertySnapshot.TrackList list
                    ? list : MpvPropertySnapshot.TrackList.empty();
        }
        if (value == null) values.remove(property);
        else values.put(property, value);
    }

    boolean contains(String property) {
        return value(property) != null;
    }

    int getInt(String property, int fallback) {
        Object value = value(property);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    long getLong(String property, long fallback) {
        Object value = value(property);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    double getDouble(String property, double fallback) {
        Object value = value(property);
        if (!(value instanceof Number number)) return fallback;
        double result = number.doubleValue();
        return Double.isFinite(result) ? result : fallback;
    }

    boolean getBoolean(String property, boolean fallback) {
        Object value = value(property);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.longValue() != 0;
        return fallback;
    }

    String getString(String property, String fallback) {
        Object value = value(property);
        return value instanceof String text ? text : fallback;
    }

    void clear() {
        values.clear();
        tracks = MpvPropertySnapshot.TrackList.empty();
    }

    private Object value(String property) {
        if (property == null) return null;
        if ("track-list/count".equals(property)) return tracks.entries().size();
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
}
