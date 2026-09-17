package com.fongmi.android.tv.ad.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class SignedRulePackageCodec {

    public static final int PACKAGE_SCHEMA_VERSION = 1;
    public static final int MAX_PACKAGE_BYTES = AdAudioRuleStore.MAX_IMPORT_BYTES + 64 * 1024;

    static final int MAX_JSON_TOKENS = 300_000;

    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_OBJECT_MEMBERS = 64;
    private static final int ED25519_SIGNATURE_BYTES = 64;
    private static final byte[] SIGNING_DOMAIN =
            "webhtv.ad-audio.package/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]+");
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern ALGORITHM_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private SignedRulePackageCodec() {
    }

    public static Parsed parse(byte[] bytes) throws SignedRulePackageException {
        if (bytes == null || bytes.length == 0) throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        if (bytes.length > MAX_PACKAGE_BYTES) throw error(SignedRulePackageException.Code.PACKAGE_TOO_LARGE);

        String json = decodeUtf8(bytes);
        validateNesting(json);
        preflightJson(json);

        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            root = requireObject(element);
        } catch (RuntimeException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }

        rejectUnknown(root, "packageSchemaVersion", "packageId", "revision",
                "createdAtEpochMs", "expiresAtEpochMs", "payloadSha256", "payload", "signature");
        int schemaVersion = requiredInt(root, "packageSchemaVersion");
        if (schemaVersion != PACKAGE_SCHEMA_VERSION) {
            throw error(SignedRulePackageException.Code.UNSUPPORTED_PACKAGE_SCHEMA);
        }

        String packageId = requiredId(root, "packageId");
        long revision = requiredLong(root, "revision");
        long createdAt = requiredLong(root, "createdAtEpochMs");
        long expiresAt = requiredLong(root, "expiresAtEpochMs");
        if (revision <= 0 || createdAt < 0 || expiresAt <= createdAt) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }

        String digestHex = requiredString(root, "payloadSha256");
        if (!DIGEST_PATTERN.matcher(digestHex).matches()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        byte[] payloadDigest = decodeHex(digestHex);

        JsonObject payload = requireObject(root.get("payload"));
        String payloadJson = payload.toString();
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > AdAudioRuleStore.MAX_IMPORT_BYTES) {
            throw error(SignedRulePackageException.Code.PACKAGE_TOO_LARGE);
        }

        AudioFingerprintRuleSet ruleSet;
        try {
            ruleSet = AudioFingerprintRuleCodec.fromJson(payloadJson);
        } catch (IllegalArgumentException e) {
            throw error(SignedRulePackageException.Code.PAYLOAD_INVALID);
        }

        String canonicalPayload;
        try {
            canonicalPayload = AudioFingerprintRuleCodec.toJson(ruleSet);
        } catch (IllegalArgumentException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_TOO_LARGE);
        }
        if (canonicalPayload.getBytes(StandardCharsets.UTF_8).length > AdAudioRuleStore.MAX_IMPORT_BYTES) {
            throw error(SignedRulePackageException.Code.PACKAGE_TOO_LARGE);
        }

        JsonObject signatureObject = requireObject(root.get("signature"));
        rejectUnknown(signatureObject, "keyId", "algorithm", "value");
        String keyId = requiredId(signatureObject, "keyId");
        String algorithm = requiredString(signatureObject, "algorithm");
        if (!ALGORITHM_PATTERN.matcher(algorithm).matches()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        byte[] signature = decodeSignature(requiredString(signatureObject, "value"));
        byte[] signingInput = signingInput(packageId, revision, createdAt, expiresAt,
                keyId, algorithm, payloadDigest);

        return new Parsed(schemaVersion, packageId, revision, createdAt, expiresAt,
                digestHex, payloadDigest, canonicalPayload, ruleSet, keyId, algorithm,
                signature, signingInput);
    }

    public static byte[] signingInput(String packageId, long revision, long createdAt,
                                      long expiresAt, String keyId, String algorithm,
                                      byte[] payloadDigest) {
        if (!matches(ID_PATTERN, packageId) || revision <= 0 || createdAt < 0
                || expiresAt <= createdAt || !matches(ID_PATTERN, keyId)
                || !matches(ALGORITHM_PATTERN, algorithm)
                || payloadDigest == null || payloadDigest.length != 32) {
            throw new IllegalArgumentException("invalid signing input");
        }
        byte[] packageBytes = packageId.getBytes(StandardCharsets.US_ASCII);
        byte[] keyBytes = keyId.getBytes(StandardCharsets.US_ASCII);
        byte[] algorithmBytes = algorithm.getBytes(StandardCharsets.US_ASCII);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(128);
            DataOutputStream writer = new DataOutputStream(output);
            writer.write(SIGNING_DOMAIN);
            writeLengthPrefixed(writer, packageBytes);
            writer.writeLong(revision);
            writer.writeLong(createdAt);
            writer.writeLong(expiresAt);
            writeLengthPrefixed(writer, keyBytes);
            writeLengthPrefixed(writer, algorithmBytes);
            writer.write(payloadDigest);
            writer.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String decodeUtf8(byte[] bytes) throws SignedRulePackageException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void validateNesting(String json) throws SignedRulePackageException {
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
                if (++depth > MAX_JSON_DEPTH) {
                    throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
                }
            } else if (value == '}' || value == ']') {
                if (--depth < 0) throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
            }
        }
        if (quoted || depth != 0) throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
    }

    private static void preflightJson(String json) throws SignedRulePackageException {
        TokenBudget budget = new TokenBudget();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            scanValue(reader, budget);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
            }
        } catch (SignedRulePackageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void scanValue(JsonReader reader, TokenBudget budget)
            throws IOException, SignedRulePackageException {
        budget.tick();
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                String firstName = null;
                Set<String> names = null;
                int memberCount = 0;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    budget.tick();
                    if (++memberCount > MAX_OBJECT_MEMBERS) {
                        throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
                    }
                    if (firstName == null) {
                        firstName = name;
                    } else {
                        if (names == null) {
                            names = new HashSet<>();
                            names.add(firstName);
                        }
                        if (!names.add(name)) {
                            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
                        }
                    }
                    scanValue(reader, budget);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) scanValue(reader, budget);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static JsonObject requireObject(JsonElement element) throws SignedRulePackageException {
        if (element == null || !element.isJsonObject()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        return element.getAsJsonObject();
    }

    private static String requiredId(JsonObject object, String key)
            throws SignedRulePackageException {
        String value = requiredString(object, key);
        if (!ID_PATTERN.matcher(value).matches()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        return value;
    }

    private static String requiredString(JsonObject object, String key)
            throws SignedRulePackageException {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        return primitive.getAsString();
    }

    private static int requiredInt(JsonObject object, String key)
            throws SignedRulePackageException {
        long value = requiredLong(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        return (int) value;
    }

    private static long requiredLong(JsonObject object, String key)
            throws SignedRulePackageException {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        String value = element.getAsString();
        if (!INTEGER_PATTERN.matcher(value).matches()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void rejectUnknown(JsonObject object, String... allowed)
            throws SignedRulePackageException {
        for (String name : object.keySet()) {
            boolean known = false;
            for (String candidate : allowed) {
                if (candidate.equals(name)) {
                    known = true;
                    break;
                }
            }
            if (!known) throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static byte[] decodeSignature(String value) throws SignedRulePackageException {
        if (!BASE64_URL_PATTERN.matcher(value).matches()) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != ED25519_SIGNATURE_BYTES
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
                throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void writeLengthPrefixed(DataOutputStream writer, byte[] value) throws IOException {
        writer.writeShort(value.length);
        writer.write(value);
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static SignedRulePackageException error(SignedRulePackageException.Code code) {
        return new SignedRulePackageException(code);
    }

    private static final class TokenBudget {
        private int count;

        void tick() throws SignedRulePackageException {
            if (++count > MAX_JSON_TOKENS) {
                throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
            }
        }
    }

    public record Parsed(int packageSchemaVersion, String packageId, long revision,
                         long createdAtEpochMs, long expiresAtEpochMs,
                         String payloadSha256, byte[] payloadDigest,
                         String canonicalPayload, AudioFingerprintRuleSet ruleSet,
                         String keyId, String algorithm, byte[] signature,
                         byte[] signingInput) {

        public Parsed {
            if (packageId == null || payloadSha256 == null || payloadDigest == null
                    || canonicalPayload == null || ruleSet == null || keyId == null
                    || algorithm == null || signature == null || signingInput == null) {
                throw new IllegalArgumentException("parsed package fields are required");
            }
            payloadDigest = payloadDigest.clone();
            signature = signature.clone();
            signingInput = signingInput.clone();
        }

        @Override
        public byte[] payloadDigest() {
            return payloadDigest.clone();
        }

        @Override
        public byte[] signature() {
            return signature.clone();
        }

        @Override
        public byte[] signingInput() {
            return signingInput.clone();
        }
    }
}
