package com.fongmi.android.tv.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;

/** Removes the optional subscription-level TMDB key before config/detail logging or parsing. */
public final class TmdbSourceCredentialIngress {

    public static final String ROOT_FIELD = "tmdb_api_key";
    private static final int MIN_KEY_LENGTH = 16;
    private static final int MAX_KEY_LENGTH = 256;
    private static final Gson GSON = new Gson();

    private TmdbSourceCredentialIngress() {
    }

    public static Ingress extractRootAndStrip(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) return new Ingress(rawJson, "");
        try {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (parsed == null || !parsed.isJsonObject()) return new Ingress(rawJson, "");
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has(ROOT_FIELD)) return new Ingress(rawJson, "");

            JsonElement value = root.get(ROOT_FIELD);
            root.remove(ROOT_FIELD);
            String candidate = value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? normalizeApiKey(value.getAsString())
                    : "";
            if (countRootFieldOccurrences(rawJson) != 1) candidate = "";
            return new Ingress(GSON.toJson(root), candidate);
        } catch (Exception ignored) {
            return new Ingress(sanitizeMalformedJson(rawJson), "");
        }
    }

    public static String normalizeApiKey(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.length() < MIN_KEY_LENGTH || normalized.length() > MAX_KEY_LENGTH) return "";
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < 0x21 || c > 0x7e) return "";
        }
        return normalized;
    }

    private static int countRootFieldOccurrences(String rawJson) {
        try (JsonReader reader = new JsonReader(new StringReader(rawJson))) {
            int count = 0;
            reader.beginObject();
            while (reader.hasNext()) {
                if (ROOT_FIELD.equals(reader.nextName())) count++;
                reader.skipValue();
            }
            reader.endObject();
            return count;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String sanitizeMalformedJson(String rawJson) {
        if (rawJson == null || !rawJson.contains("\"" + ROOT_FIELD + "\"")) return rawJson;
        // The malformed document is already unusable. Returning an empty object prevents callers from
        // logging, caching, or parsing the raw credential-bearing text when structural parsing failed.
        return "{}";
    }

    public static final class Ingress {

        private final String sanitizedJson;
        private final String candidateKey;

        private Ingress(String sanitizedJson, String candidateKey) {
            this.sanitizedJson = sanitizedJson == null ? "" : sanitizedJson;
            this.candidateKey = candidateKey == null ? "" : candidateKey;
        }

        public String getSanitizedJson() {
            return sanitizedJson;
        }

        public String getCandidateKey() {
            return candidateKey;
        }

        @Override
        public String toString() {
            return "Ingress{key=" + !candidateKey.isEmpty() + ",jsonLength=" + sanitizedJson.length() + "}";
        }
    }
}
