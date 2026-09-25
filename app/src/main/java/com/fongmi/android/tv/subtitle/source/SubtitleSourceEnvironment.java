package com.fongmi.android.tv.subtitle.source;

import com.fongmi.android.tv.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

public final class SubtitleSourceEnvironment {

    private SubtitleSourceEnvironment() {
    }

    public static JsonObject resolve(String sourceKey, JsonObject params) {
        return resolve(params, load(sourceKey));
    }

    static JsonObject resolve(JsonObject params, JsonObject environment) {
        JsonObject result = params == null ? new JsonObject() : params.deepCopy();
        environment = environment == null ? new JsonObject() : environment;
        for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) continue;
            String name = placeholderName(entry.getValue().getAsString());
            if (name == null) continue;
            JsonElement value = environment.get(name);
            entry.setValue(value == null || value.isJsonNull() ? new com.google.gson.JsonPrimitive("") : value.deepCopy());
        }
        return result;
    }

    private static String placeholderName(String value) {
        if (value == null || value.length() < 4 || !value.startsWith("${") || !value.endsWith("}")) return null;
        String name = value.substring(2, value.length() - 1);
        if (name.isEmpty() || name.charAt(0) < 'A' || name.charAt(0) > 'Z') return null;
        for (int i = 1; i < name.length(); i++) {
            char valueChar = name.charAt(i);
            if ((valueChar < 'A' || valueChar > 'Z') && (valueChar < '0' || valueChar > '9') && valueChar != '_') return null;
        }
        return name;
    }

    public static String resolveToken(String sourceKey, String name) {
        JsonElement value = load(sourceKey).get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static JsonObject load(String sourceKey) {
        String json = Setting.getSubtitleSourceEnvironment(sourceKey);
        if (json.isEmpty()) return new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }
}
