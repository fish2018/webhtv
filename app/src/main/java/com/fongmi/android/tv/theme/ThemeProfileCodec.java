package com.fongmi.android.tv.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** JSON codec with bounded input and no executable/ambiguous fields. */
public final class ThemeProfileCodec {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ThemeProfileCodec() {
    }

    public static ThemeProfile parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("theme JSON is empty");
        validateJsonBounds(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("theme JSON is invalid", e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("theme JSON must be an object");
        inspect(root, 0);
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(GSON.fromJson(root, ThemeProfile.class));
        if (!result.valid()) throw new IllegalArgumentException(result.message());
        return result.profile();
    }

    public static String encode(ThemeProfile profile) {
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        if (!result.valid()) throw new IllegalArgumentException(result.message());
        return GSON.toJson(result.profile());
    }

    /**
     * Bounds raw imported JSON before Gson builds a recursive tree. This keeps the
     * nesting limit effective for pasted/downloaded input instead of discovering it
     * only after a deeply nested payload has already been parsed.
     */
    static void validateJsonBounds(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > ThemeProfileValidator.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("theme JSON is too large");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{' || current == '[') {
                if (++depth > ThemeProfileValidator.MAX_NESTING_DEPTH) {
                    throw new IllegalArgumentException("theme JSON is too deeply nested");
                }
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth < 0) throw new IllegalArgumentException("theme JSON is invalid");
            }
        }
    }

    private static void inspect(JsonElement element, int depth) {
        if (depth > ThemeProfileValidator.MAX_NESTING_DEPTH) throw new IllegalArgumentException("theme JSON is too deeply nested");
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) inspect(child, depth + 1);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!ThemeProfileValidator.isSafeJsonKey(entry.getKey())) {
                throw new IllegalArgumentException("unsupported executable theme field: " + entry.getKey());
            }
            inspect(entry.getValue(), depth + 1);
        }
    }
}
