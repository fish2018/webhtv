package com.fongmi.android.tv.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Issues short-lived opaque references for provider-controlled identifiers. */
final class WebThemeAccessSession {

    private static final int MAX_VOD_REFERENCES = 10_000;
    private static final int MAX_TYPE_REFERENCES = 512;
    private static final int MAX_FILTER_REFERENCES = 8_192;

    private final Map<String, String> vodByRef = new HashMap<>();
    private final Map<String, String> vodRefByValue = new HashMap<>();
    private final Map<String, String> typeByRef = new HashMap<>();
    private final Map<String, String> typeRefByValue = new HashMap<>();
    private final Map<String, FilterKey> filterKeyByRef = new HashMap<>();
    private final Map<String, String> filterKeyRefByValue = new HashMap<>();
    private final Map<String, FilterValue> filterValueByRef = new HashMap<>();
    private final Map<String, String> filterValueRefByValue = new HashMap<>();

    synchronized JsonObject protectHome(JsonObject root) {
        if (root == null) return new JsonObject();
        JsonArray classes = array(root, "classes");
        for (JsonElement element : classes) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            item.addProperty("typeId", issueType(string(item, "typeId")));
        }
        root.add("filters", protectFilters(object(root, "filters")));
        protectItems(array(root, "items"));
        return root;
    }

    synchronized JsonObject protectCategory(JsonObject root, String publicTypeRef) {
        if (root == null) return new JsonObject();
        object(root, "query").addProperty("typeId", value(publicTypeRef));
        protectItems(array(root, "items"));
        return root;
    }

    synchronized JsonObject protectDetail(JsonObject root) {
        if (root == null) return new JsonObject();
        JsonObject item = object(root, "item");
        item.addProperty("vodId", issueVod(string(item, "vodId")));
        return root;
    }

    synchronized String issueRoute(String vodId) {
        return issueVod(vodId);
    }

    synchronized String resolveVod(String ref) {
        return vodByRef.get(value(ref));
    }

    synchronized String resolveType(String ref) {
        return typeByRef.get(value(ref));
    }

    synchronized HashMap<String, String> resolveExtend(Map<String, String> values) {
        HashMap<String, String> result = new HashMap<>();
        if (values == null) return result;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            FilterKey key = filterKeyByRef.get(value(entry.getKey()));
            FilterValue filterValue = filterValueByRef.get(value(entry.getValue()));
            if (key == null || filterValue == null || !key.ref.equals(filterValue.keyRef)) {
                throw new SecurityException("Unknown filter reference");
            }
            result.put(key.value, filterValue.value);
        }
        return result;
    }

    private JsonObject protectFilters(JsonObject filters) {
        JsonObject protectedFilters = new JsonObject();
        for (Map.Entry<String, JsonElement> group : filters.entrySet()) {
            if (!group.getValue().isJsonArray()) continue;
            String typeRef = issueType(group.getKey());
            JsonArray mappedGroup = group.getValue().getAsJsonArray();
            for (JsonElement element : mappedGroup) {
                if (!element.isJsonObject()) continue;
                JsonObject filter = element.getAsJsonObject();
                String keyRef = issueFilterKey(typeRef, string(filter, "key"));
                filter.addProperty("key", keyRef);
                for (JsonElement value : array(filter, "values")) {
                    if (!value.isJsonObject()) continue;
                    JsonObject option = value.getAsJsonObject();
                    option.addProperty("value", issueFilterValue(keyRef, string(option, "value")));
                }
            }
            protectedFilters.add(typeRef, mappedGroup);
        }
        return protectedFilters;
    }

    private void protectItems(JsonArray items) {
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            item.addProperty("vodId", issueVod(string(item, "vodId")));
            item.remove("action");
        }
    }

    private String issueVod(String value) {
        return issue("vod", value, vodByRef, vodRefByValue, MAX_VOD_REFERENCES);
    }

    private String issueType(String value) {
        return issue("type", value, typeByRef, typeRefByValue, MAX_TYPE_REFERENCES);
    }

    private String issueFilterKey(String typeRef, String value) {
        String identity = value(typeRef) + '\u0000' + value(value);
        String existing = filterKeyRefByValue.get(identity);
        if (existing != null) return existing;
        if (filterKeyByRef.size() >= MAX_FILTER_REFERENCES) throw new IllegalStateException("Too many filter references");
        String ref = reference("filter");
        filterKeyByRef.put(ref, new FilterKey(ref, value(value)));
        filterKeyRefByValue.put(identity, ref);
        return ref;
    }

    private String issueFilterValue(String keyRef, String value) {
        String identity = value(keyRef) + '\u0000' + value(value);
        String existing = filterValueRefByValue.get(identity);
        if (existing != null) return existing;
        if (filterValueByRef.size() >= MAX_FILTER_REFERENCES) throw new IllegalStateException("Too many filter references");
        String ref = reference("value");
        filterValueByRef.put(ref, new FilterValue(keyRef, value(value)));
        filterValueRefByValue.put(identity, ref);
        return ref;
    }

    private static String issue(String prefix, String raw, Map<String, String> byRef,
            Map<String, String> refByValue, int limit) {
        String safe = value(raw);
        if (safe.isEmpty()) return "";
        String existing = refByValue.get(safe);
        if (existing != null) return existing;
        if (byRef.size() >= limit) throw new IllegalStateException("Too many theme references");
        String ref = reference(prefix);
        byRef.put(ref, safe);
        refByValue.put(safe, ref);
        return ref;
    }

    private static String reference(String prefix) {
        return prefix + '_' + UUID.randomUUID();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonObject()) return object.getAsJsonObject(key);
        JsonObject value = new JsonObject();
        object.add(key, value);
        return value;
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private record FilterKey(String ref, String value) {
    }

    private record FilterValue(String keyRef, String value) {
    }
}
