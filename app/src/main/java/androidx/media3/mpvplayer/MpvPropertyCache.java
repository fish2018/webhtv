package androidx.media3.mpvplayer;

import java.util.HashMap;
import java.util.Map;

final class MpvPropertyCache {

    private final Map<String, Object> values = new HashMap<>();

    void put(String property, Object value) {
        if (property == null || property.isEmpty()) return;
        if (value == null) values.remove(property);
        else values.put(property, value);
    }

    boolean contains(String property) {
        return values.containsKey(property);
    }

    int getInt(String property, int fallback) {
        Object value = values.get(property);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    long getLong(String property, long fallback) {
        Object value = values.get(property);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    double getDouble(String property, double fallback) {
        Object value = values.get(property);
        if (!(value instanceof Number number)) return fallback;
        double result = number.doubleValue();
        return Double.isFinite(result) ? result : fallback;
    }

    boolean getBoolean(String property, boolean fallback) {
        Object value = values.get(property);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.longValue() != 0;
        return fallback;
    }

    String getString(String property, String fallback) {
        Object value = values.get(property);
        return value instanceof String text ? text : fallback;
    }

    void clear() {
        values.clear();
    }
}