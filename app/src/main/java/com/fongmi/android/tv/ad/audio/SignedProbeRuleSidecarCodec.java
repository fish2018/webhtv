package com.fongmi.android.tv.ad.audio;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class SignedProbeRuleSidecarCodec {

    public static final int ARTIFACT_SCHEMA_VERSION = 1;
    public static final String ARTIFACT_TYPE = "ad-audio-probe-sidecar";
    public static final int MAX_PACKAGE_BYTES = 3 * 1024 * 1024;

    private static final int SIGNATURE_BYTES = 64;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_RULES_BASE64_CHARS =
            ((AdAudioRuleStore.MAX_IMPORT_BYTES + 2) / 3) * 4;
    private static final byte[] SIGNING_DOMAIN =
            "webhtv.ad-audio.probe-sidecar/v1".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SIGNATURE_ALGORITHM = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final Pattern INTEGER = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern BASE64_URL = Pattern.compile("[A-Za-z0-9_-]+");

    private SignedProbeRuleSidecarCodec() {
    }

    public static Parsed parse(byte[] bytes) throws SignedRulePackageException {
        if (bytes == null || bytes.length == 0) throw malformed();
        if (bytes.length > MAX_PACKAGE_BYTES) {
            throw error(SignedRulePackageException.Code.PACKAGE_TOO_LARGE);
        }
        String json = decodeUtf8(bytes);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            require(reader.peek(), JsonToken.BEGIN_OBJECT);
            reader.beginObject();
            Set<String> seen = new HashSet<>();
            Integer artifactSchemaVersion = null;
            String artifactType = null;
            String packageId = null;
            Long revision = null;
            Long createdAtEpochMs = null;
            Long expiresAtEpochMs = null;
            String sourcePackageId = null;
            Long sourceRevision = null;
            String sourcePayloadSha256 = null;
            String probeAlgorithm = null;
            String converterVersion = null;
            String rulesSha256 = null;
            String rulesBase64Url = null;
            ParsedSignature signature = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!seen.add(name)) throw malformed();
                switch (name) {
                    case "artifactSchemaVersion" -> artifactSchemaVersion = readInt(reader);
                    case "artifactType" -> artifactType = readString(reader);
                    case "packageId" -> packageId = readString(reader);
                    case "revision" -> revision = readLong(reader);
                    case "createdAtEpochMs" -> createdAtEpochMs = readLong(reader);
                    case "expiresAtEpochMs" -> expiresAtEpochMs = readLong(reader);
                    case "sourcePackageId" -> sourcePackageId = readString(reader);
                    case "sourceRevision" -> sourceRevision = readLong(reader);
                    case "sourcePayloadSha256" -> sourcePayloadSha256 = readString(reader);
                    case "algorithm" -> probeAlgorithm = readString(reader);
                    case "converterVersion" -> converterVersion = readString(reader);
                    case "rulesSha256" -> rulesSha256 = readString(reader);
                    case "rulesBase64Url" -> rulesBase64Url = readString(reader);
                    case "signature" -> signature = readSignature(reader);
                    default -> throw malformed();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw malformed();
            return validate(artifactSchemaVersion, artifactType, packageId, revision,
                    createdAtEpochMs, expiresAtEpochMs, sourcePackageId, sourceRevision,
                    sourcePayloadSha256, probeAlgorithm, converterVersion, rulesSha256,
                    rulesBase64Url, signature);
        } catch (SignedRulePackageException e) {
            throw e;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw malformed();
        }
    }

    private static Parsed validate(Integer artifactSchemaVersion, String artifactType,
                                   String packageId, Long revision, Long createdAtEpochMs,
                                   Long expiresAtEpochMs, String sourcePackageId,
                                   Long sourceRevision, String sourcePayloadSha256,
                                   String probeAlgorithm, String converterVersion,
                                   String rulesSha256, String rulesBase64Url,
                                   ParsedSignature signature)
            throws SignedRulePackageException {
        if (artifactSchemaVersion == null || artifactSchemaVersion != ARTIFACT_SCHEMA_VERSION) {
            throw error(SignedRulePackageException.Code.UNSUPPORTED_PACKAGE_SCHEMA);
        }
        if (!ARTIFACT_TYPE.equals(artifactType)
                || !validId(packageId) || revision == null || revision <= 0L
                || createdAtEpochMs == null || createdAtEpochMs < 0L
                || expiresAtEpochMs == null || expiresAtEpochMs <= createdAtEpochMs
                || !validId(sourcePackageId) || sourceRevision == null || sourceRevision <= 0L
                || sourcePayloadSha256 == null || !DIGEST.matcher(sourcePayloadSha256).matches()
                || converterVersion == null || !ID.matcher(converterVersion).matches()
                || rulesSha256 == null || !DIGEST.matcher(rulesSha256).matches()
                || signature == null) {
            throw malformed();
        }
        if (!ProbeRuleSidecar.ALGORITHM_ID.equals(probeAlgorithm)) {
            throw error(SignedRulePackageException.Code.PAYLOAD_INVALID);
        }
        if (rulesBase64Url == null || rulesBase64Url.length() > MAX_RULES_BASE64_CHARS
                || !BASE64_URL.matcher(rulesBase64Url).matches()) {
            throw malformed();
        }
        byte[] rules = decodeBase64Url(rulesBase64Url, -1);
        if (rules.length == 0 || rules.length > AdAudioRuleStore.MAX_IMPORT_BYTES
                || !rulesBase64Url.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(rules))) {
            throw malformed();
        }
        byte[] sourceDigest = decodeHex(sourcePayloadSha256);
        byte[] rulesDigest = decodeHex(rulesSha256);
        if (!MessageDigest.isEqual(sha256(rules), rulesDigest)) {
            throw error(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH);
        }
        byte[] signingInput = signingInput(packageId, revision, createdAtEpochMs,
                expiresAtEpochMs, sourcePackageId, sourceRevision, sourceDigest,
                probeAlgorithm, converterVersion, rulesDigest, signature.keyId,
                signature.algorithm);
        return new Parsed(artifactSchemaVersion, artifactType, packageId, revision,
                createdAtEpochMs, expiresAtEpochMs, sourcePackageId, sourceRevision,
                sourcePayloadSha256, sourceDigest, probeAlgorithm, converterVersion,
                rulesSha256, rulesDigest, rules, signature.keyId, signature.algorithm,
                signature.value, signingInput);
    }

    private static ParsedSignature readSignature(JsonReader reader)
            throws IOException, SignedRulePackageException {
        require(reader.peek(), JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        Set<String> seen = new HashSet<>();
        String keyId = null;
        String algorithm = null;
        String value = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!seen.add(name)) throw malformed();
            switch (name) {
                case "keyId" -> keyId = readString(reader);
                case "algorithm" -> algorithm = readString(reader);
                case "value" -> value = readString(reader);
                default -> throw malformed();
            }
        }
        reader.endObject();
        if (!validId(keyId) || algorithm == null
                || !SIGNATURE_ALGORITHM.matcher(algorithm).matches()
                || value == null || !BASE64_URL.matcher(value).matches()) {
            throw malformed();
        }
        byte[] decoded = decodeBase64Url(value, SIGNATURE_BYTES);
        if (!value.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))) {
            throw malformed();
        }
        return new ParsedSignature(keyId, algorithm, decoded);
    }

    private static byte[] signingInput(String packageId, long revision,
                                       long createdAtEpochMs, long expiresAtEpochMs,
                                       String sourcePackageId, long sourceRevision,
                                       byte[] sourceDigest, String probeAlgorithm,
                                       String converterVersion, byte[] rulesDigest,
                                       String keyId, String signatureAlgorithm) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(SIGNING_DOMAIN);
            output.writeByte(0);
            writeLengthPrefixed(output, packageId);
            output.writeLong(revision);
            output.writeLong(createdAtEpochMs);
            output.writeLong(expiresAtEpochMs);
            writeLengthPrefixed(output, sourcePackageId);
            output.writeLong(sourceRevision);
            output.write(sourceDigest);
            writeLengthPrefixed(output, probeAlgorithm);
            writeLengthPrefixed(output, converterVersion);
            output.write(rulesDigest);
            writeLengthPrefixed(output, keyId);
            writeLengthPrefixed(output, signatureAlgorithm);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeLengthPrefixed(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) throw new IllegalArgumentException("field is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static int readInt(JsonReader reader) throws IOException, SignedRulePackageException {
        long value = readLong(reader);
        if (value > Integer.MAX_VALUE) throw malformed();
        return (int) value;
    }

    private static long readLong(JsonReader reader) throws IOException, SignedRulePackageException {
        require(reader.peek(), JsonToken.NUMBER);
        String raw = reader.nextString();
        if (!INTEGER.matcher(raw).matches()) throw malformed();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw malformed();
        }
    }

    private static String readString(JsonReader reader)
            throws IOException, SignedRulePackageException {
        require(reader.peek(), JsonToken.STRING);
        return reader.nextString();
    }

    private static void require(JsonToken actual, JsonToken expected)
            throws SignedRulePackageException {
        if (actual != expected) throw malformed();
    }

    private static boolean validId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    private static byte[] decodeBase64Url(String value, int expectedLength)
            throws SignedRulePackageException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (expectedLength >= 0 && decoded.length != expectedLength) throw malformed();
            return decoded;
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    private static byte[] decodeHex(String value) throws SignedRulePackageException {
        if (value == null || !DIGEST.matcher(value).matches()) throw malformed();
        byte[] result = new byte[DIGEST_BYTES];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String decodeUtf8(byte[] bytes) throws SignedRulePackageException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw malformed();
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static SignedRulePackageException malformed() {
        return error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
    }

    private static SignedRulePackageException error(SignedRulePackageException.Code code) {
        return new SignedRulePackageException(code);
    }

    private record ParsedSignature(String keyId, String algorithm, byte[] value) {
        private ParsedSignature {
            value = value.clone();
        }
    }

    public record Parsed(int artifactSchemaVersion, String artifactType, String packageId,
                         long revision, long createdAtEpochMs, long expiresAtEpochMs,
                         String sourcePackageId, long sourceRevision,
                         String sourcePayloadSha256, byte[] sourcePayloadDigest,
                         String probeAlgorithm, String converterVersion,
                         String rulesSha256, byte[] rulesDigest, byte[] canonicalRules,
                         String keyId, String signatureAlgorithm, byte[] signature,
                         byte[] signingInput) {
        public Parsed {
            if (artifactType == null || packageId == null || sourcePackageId == null
                    || sourcePayloadSha256 == null || sourcePayloadDigest == null
                    || probeAlgorithm == null || converterVersion == null
                    || rulesSha256 == null || rulesDigest == null || canonicalRules == null
                    || keyId == null || signatureAlgorithm == null || signature == null
                    || signingInput == null) {
                throw new IllegalArgumentException("parsed sidecar fields are required");
            }
            sourcePayloadDigest = sourcePayloadDigest.clone();
            rulesDigest = rulesDigest.clone();
            canonicalRules = canonicalRules.clone();
            signature = signature.clone();
            signingInput = signingInput.clone();
        }

        @Override
        public byte[] sourcePayloadDigest() {
            return sourcePayloadDigest.clone();
        }

        @Override
        public byte[] rulesDigest() {
            return rulesDigest.clone();
        }

        @Override
        public byte[] canonicalRules() {
            return canonicalRules.clone();
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
