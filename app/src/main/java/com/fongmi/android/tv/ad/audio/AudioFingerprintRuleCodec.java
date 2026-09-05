package com.fongmi.android.tv.ad.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AudioFingerprintRuleCodec {

    private static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_JSON_TOKENS = 300_000;
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{8}");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]+");

    private AudioFingerprintRuleCodec() {
    }

    public static AudioFingerprintRuleSet fromJson(String json) {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("rule JSON is empty");
        if (json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("rule JSON is too large");
        try {
            validateJsonEnvelope(json);
            preflightJson(json);
            JsonElement rootElement = JsonParser.parseString(json);
            JsonObject root = requireObject(rootElement, "root");
            rejectUnknown(root, "schemaVersion", "algorithm", "rules");
            if (requiredInt(root, "schemaVersion") != AudioFingerprintRuleSet.SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported schema version");
            }
            JsonObject algorithm = requireObject(root.get("algorithm"), "algorithm");
            rejectUnknown(algorithm, "id", "sampleRate", "windowMs", "hopMs", "bandCount");
            if (!AudioFingerprintRuleSet.ALGORITHM_ID.equals(requiredString(algorithm, "id"))) {
                throw new IllegalArgumentException("unsupported fingerprint algorithm");
            }
            AudioFingerprintConfig config = new AudioFingerprintConfig(
                    requiredInt(algorithm, "sampleRate"),
                    requiredInt(algorithm, "windowMs"),
                    requiredInt(algorithm, "hopMs"),
                    requiredInt(algorithm, "bandCount"));
            JsonArray rulesArray = requireArray(root.get("rules"), "rules");
            if (rulesArray.size() > AudioFingerprintRuleSet.MAX_RULES) {
                throw new IllegalArgumentException("too many rules");
            }
            List<AudioFingerprintRule> rules = new ArrayList<>(rulesArray.size());
            for (JsonElement element : rulesArray) rules.add(parseRule(requireObject(element, "rule")));
            return new AudioFingerprintRuleSet(config, rules);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid rule JSON", e);
        }
    }

    public static String toJson(AudioFingerprintRuleSet ruleSet) {
        if (ruleSet == null) throw new IllegalArgumentException("rule set is required");
        BoundedStringWriter output = new BoundedStringWriter(MAX_JSON_CHARS);
        try (JsonWriter writer = new JsonWriter(output)) {
            writer.setHtmlSafe(false);
            writer.beginObject();
            writer.name("schemaVersion").value(AudioFingerprintRuleSet.SCHEMA_VERSION);
            AudioFingerprintConfig config = ruleSet.config();
            writer.name("algorithm").beginObject();
            writer.name("id").value(AudioFingerprintRuleSet.ALGORITHM_ID);
            writer.name("sampleRate").value(config.sampleRate());
            writer.name("windowMs").value(config.windowMs());
            writer.name("hopMs").value(config.hopMs());
            writer.name("bandCount").value(config.bandCount());
            writer.endObject();
            writer.name("rules").beginArray();
            for (AudioFingerprintRule rule : ruleSet.rules()) writeRule(writer, rule);
            writer.endArray();
            writer.endObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("serialized rule JSON is too large", e);
        }
        return output.value();
    }

    private static AudioFingerprintRule parseRule(JsonObject object) {
        rejectUnknown(object, "id", "durationMs", "anchorOffsetMs", "anchorDurationMs", "fingerprint", "variants");
        List<int[]> variants = new ArrayList<>();
        JsonElement variantsElement = object.get("variants");
        if (variantsElement != null) {
            JsonArray variantArray = requireArray(variantsElement, "variants");
            if (variantArray.size() > AudioFingerprintRule.MAX_VARIANTS) {
                throw new IllegalArgumentException("too many variants");
            }
            for (JsonElement variant : variantArray) variants.add(parseSequence(variant, "variant"));
        }
        return new AudioFingerprintRule(
                requiredString(object, "id"),
                requiredLong(object, "durationMs"),
                requiredLong(object, "anchorOffsetMs"),
                requiredLong(object, "anchorDurationMs"),
                parseSequence(object.get("fingerprint"), "fingerprint"),
                variants);
    }

    private static int[] parseSequence(JsonElement element, String name) {
        JsonArray array = requireArray(element, name);
        if (array.size() < AudioFingerprintRule.MIN_SEQUENCE_FRAMES
                || array.size() > AudioFingerprintRule.MAX_SEQUENCE_FRAMES) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            String hash = requiredString(array.get(i), name + " hash");
            if (!HASH_PATTERN.matcher(hash).matches()) throw new IllegalArgumentException("invalid hash");
            result[i] = (int) Long.parseLong(hash, 16);
        }
        return result;
    }

    private static void writeRule(JsonWriter writer, AudioFingerprintRule rule) throws IOException {
        writer.beginObject();
        writer.name("id").value(rule.id());
        writer.name("durationMs").value(rule.durationMs());
        writer.name("anchorOffsetMs").value(rule.anchorOffsetMs());
        writer.name("anchorDurationMs").value(rule.anchorDurationMs());
        writer.name("fingerprint");
        writeHashArray(writer, rule.fingerprint());
        List<int[]> variants = rule.variants();
        if (!variants.isEmpty()) {
            writer.name("variants").beginArray();
            for (int[] sequence : variants) writeHashArray(writer, sequence);
            writer.endArray();
        }
        writer.endObject();
    }

    private static void writeHashArray(JsonWriter writer, int[] hashes) throws IOException {
        writer.beginArray();
        for (int hash : hashes) writer.value(toHash(hash));
        writer.endArray();
    }

    private static String toHash(int hash) {
        String value = Integer.toHexString(hash);
        if (value.length() == 8) return value;
        StringBuilder padded = new StringBuilder(8);
        for (int i = value.length(); i < 8; i++) padded.append('0');
        return padded.append(value).toString();
    }

    private static JsonObject requireObject(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String name) {
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return element.getAsJsonArray();
    }

    private static String requiredString(JsonObject object, String key) {
        return requiredString(object.get(key), key);
    }

    private static String requiredString(JsonElement element, String name) {
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(name + " must be a string");
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) throw new IllegalArgumentException(name + " must be a string");
        return primitive.getAsString();
    }

    private static int requiredInt(JsonObject object, String key) {
        long value = requiredLong(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is out of range");
        }
        return (int) value;
    }

    private static long requiredLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        String value = element.getAsString();
        if (!INTEGER_PATTERN.matcher(value).matches()) throw new IllegalArgumentException(key + " must be an integer");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " is out of range", e);
        }
    }

    private static void rejectUnknown(JsonObject object, String... allowed) {
        for (String name : object.keySet()) {
            boolean known = false;
            for (String candidate : allowed) {
                if (candidate.equals(name)) {
                    known = true;
                    break;
                }
            }
            if (!known) throw new IllegalArgumentException("unknown field: " + name);
        }
    }

    private static void validateJsonEnvelope(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
                continue;
            }
            if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > MAX_JSON_DEPTH) throw new IllegalArgumentException("rule JSON is too deeply nested");
            } else if (value == '}' || value == ']') {
                if (--depth < 0) throw new IllegalArgumentException("rule JSON has invalid nesting");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("rule JSON is incomplete");
    }

    private static void preflightJson(String json) {
        TokenBudget budget = new TokenBudget();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            scanValue(reader, budget, true);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("rule JSON has trailing data");
            }
        } catch (IOException | IllegalStateException e) {
            throw new IllegalArgumentException("invalid rule JSON", e);
        }
    }

    private static void scanValue(JsonReader reader, TokenBudget budget, boolean root) throws IOException {
        budget.tick();
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    budget.tick();
                    if (root && "rules".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        scanRules(reader, budget);
                    } else {
                        scanValue(reader, budget, false);
                    }
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) scanValue(reader, budget, false);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("invalid rule JSON token");
        }
    }

    private static void scanRules(JsonReader reader, TokenBudget budget) throws IOException {
        reader.beginArray();
        int count = 0;
        while (reader.hasNext()) {
            if (++count > AudioFingerprintRuleSet.MAX_RULES) {
                throw new IllegalArgumentException("too many rules");
            }
            scanValue(reader, budget, false);
        }
        reader.endArray();
    }

    private static final class TokenBudget {
        private int count;

        void tick() {
            if (++count > MAX_JSON_TOKENS) throw new IllegalArgumentException("rule JSON has too many tokens");
        }
    }

    private static final class BoundedStringWriter extends Writer {
        private final int maxChars;
        private final StringBuilder value = new StringBuilder(16_384);

        BoundedStringWriter(int maxChars) {
            this.maxChars = maxChars;
        }

        String value() {
            return value.toString();
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            ensureCapacity(length);
            value.append(chars, offset, length);
        }

        @Override
        public void write(String string, int offset, int length) throws IOException {
            ensureCapacity(length);
            value.append(string, offset, offset + length);
        }

        @Override
        public void write(int character) throws IOException {
            ensureCapacity(1);
            value.append((char) character);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private void ensureCapacity(int additionalChars) throws IOException {
            if (additionalChars < 0 || value.length() > maxChars - additionalChars) {
                throw new IOException("JSON size limit exceeded");
            }
        }
    }
}
