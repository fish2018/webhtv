package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SignedRulePackageVerifierTest {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;
    private static final long NOW = 1_786_900_000_000L;
    private static final long CREATED_AT = NOW - MINUTE_MS;
    private static final long EXPIRES_AT = NOW + DAY_MS;
    private static final String PACKAGE_ID = "official.ad-audio";
    private static final String KEY_ID = "release-1";
    private static final String ALGORITHM = "ED25519";
    private static final String EMPTY_PAYLOAD =
            "{\"schemaVersion\":2,\"algorithm\":{"
                    + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                    + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},\"rules\":[]}";

    @Test
    public void activeKeyVerifiesInstallAndCache() throws Exception {
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                NOW - DAY_MS, NOW + DAY_MS);
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        VerifiedRulePackage installed = verifier.verify(envelope, VerificationMode.INSTALL);
        VerifiedRulePackage cached = verifier.verify(envelope, VerificationMode.CACHE);

        assertEquals(PACKAGE_ID, installed.packageId());
        assertEquals(42L, installed.revision());
        assertEquals(CREATED_AT, installed.createdAtEpochMs());
        assertEquals(EXPIRES_AT, installed.expiresAtEpochMs());
        assertEquals(KEY_ID, installed.keyId());
        assertEquals(ALGORITHM, installed.signatureAlgorithm());
        assertEquals(EMPTY_PAYLOAD, installed.canonicalPayload());
        assertEquals(0, installed.ruleSet().rules().size());
        assertArrayEquals(installed.payloadDigest(), cached.payloadDigest());
    }

    @Test
    public void tamperingEverySignedFieldIsRejected() throws Exception {
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                NOW - DAY_MS, NOW + DAY_MS);
        String valid = text(signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD));
        String digest = digestHex(EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.PACKAGE_ID_MISMATCH,
                () -> verifier.verify(bytes(valid.replace(PACKAGE_ID, "other.ad-audio")),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifier.verify(bytes(valid.replace("\"revision\":42", "\"revision\":43")),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifier.verify(bytes(valid.replace(Long.toString(CREATED_AT),
                                Long.toString(CREATED_AT + 1))), VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifier.verify(bytes(valid.replace(Long.toString(EXPIRES_AT),
                                Long.toString(EXPIRES_AT + 1))), VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.UNKNOWN_KEY,
                () -> verifier.verify(bytes(valid.replace(KEY_ID, "release-2")),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.UNSUPPORTED_SIGNATURE_ALGORITHM,
                () -> verifier.verify(bytes(valid.replace(ALGORITHM, "ED25518")),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH,
                () -> verifier.verify(bytes(valid.replace("\"rules\":[]", "\"rules\":[{"
                                + "\"id\":\"ad-1\",\"durationMs\":15000,"
                                + "\"anchorOffsetMs\":0,\"anchorDurationMs\":3000,"
                                + "\"fingerprint\":[\"00000000\",\"00000001\","
                                + "\"00000002\",\"00000003\"]}]")),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH,
                () -> verifier.verify(bytes(valid.replace(digest,
                                (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1))),
                        VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifier.verify(bytes(tamperSignature(valid)), VerificationMode.INSTALL));
    }

    @Test
    public void correctlySignedUnknownKeyIsRejected() throws Exception {
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                "unknown-1", ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.UNKNOWN_KEY,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                        NOW - DAY_MS, NOW + DAY_MS).verify(envelope, VerificationMode.INSTALL));
    }

    @Test
    public void correctlySignedUnsupportedAlgorithmIsRejected() throws Exception {
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, "ECDSA", EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.UNSUPPORTED_SIGNATURE_ALGORITHM,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                        NOW - DAY_MS, NOW + DAY_MS).verify(envelope, VerificationMode.INSTALL));
    }

    @Test
    public void retiredKeyIsCacheOnly() throws Exception {
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.RETIRED,
                NOW - DAY_MS, NOW + DAY_MS);
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier.verify(envelope, VerificationMode.INSTALL));
        assertEquals(42L, verifier.verify(envelope, VerificationMode.CACHE).revision());
    }

    @Test
    public void revokedKeyIsAlwaysRejected() throws Exception {
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.REVOKED,
                NOW - DAY_MS, NOW + DAY_MS);
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier.verify(envelope, VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier.verify(envelope, VerificationMode.CACHE));
    }

    @Test
    public void packageCreationMustFallWithinKeyValidityBounds() throws Exception {
        byte[] before = signedEnvelope(PACKAGE_ID, 42, NOW - 2 * DAY_MS, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        byte[] after = signedEnvelope(PACKAGE_ID, 42, NOW, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                        NOW - DAY_MS, NOW + DAY_MS).verify(before, VerificationMode.CACHE));
        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                        NOW - DAY_MS, NOW - 1).verify(after, VerificationMode.CACHE));
    }

    @Test
    public void keyValidityBoundsAreInclusive() throws Exception {
        byte[] atNotBefore = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        byte[] atNotAfter = signedEnvelope(PACKAGE_ID, 43, NOW, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertEquals(42L, verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                CREATED_AT, NOW + DAY_MS).verify(atNotBefore, VerificationMode.CACHE).revision());
        assertEquals(43L, verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                CREATED_AT, NOW).verify(atNotAfter, VerificationMode.CACHE).revision());
    }

    @Test
    public void expiredActiveKeyCannotInstallButCanValidateExistingCache() throws Exception {
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                CREATED_AT - DAY_MS, NOW - 1);
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier.verify(envelope, VerificationMode.INSTALL));
        assertEquals(42L, verifier.verify(envelope, VerificationMode.CACHE).revision());
    }

    @Test
    public void packageFutureSkewIsLimitedToFiveMinutes() throws Exception {
        byte[] boundary = signedEnvelope(PACKAGE_ID, 42,
                NOW + 5 * MINUTE_MS, NOW + DAY_MS, KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        byte[] tooFar = signedEnvelope(PACKAGE_ID, 43,
                NOW + 5 * MINUTE_MS + 1, NOW + DAY_MS, KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                NOW - DAY_MS, NOW + 2 * DAY_MS);

        assertEquals(42L, verifier.verify(boundary, VerificationMode.INSTALL).revision());
        assertCode(SignedRulePackageException.Code.PACKAGE_NOT_YET_VALID,
                () -> verifier.verify(tooFar, VerificationMode.INSTALL));
    }

    @Test
    public void packageExpiryBoundaryIsExclusive() throws Exception {
        byte[] expired = signedEnvelope(PACKAGE_ID, 42, CREATED_AT, NOW,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        byte[] valid = signedEnvelope(PACKAGE_ID, 43, CREATED_AT, NOW + 1,
                KEY_ID, ALGORITHM, EMPTY_PAYLOAD);
        SignedRulePackageVerifier verifier = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                NOW - DAY_MS, NOW + DAY_MS);

        assertCode(SignedRulePackageException.Code.PACKAGE_EXPIRED,
                () -> verifier.verify(expired, VerificationMode.INSTALL));
        assertEquals(43L, verifier.verify(valid, VerificationMode.INSTALL).revision());
    }

    @Test
    public void packageLifetimeCannotExceedNinetyDays() throws Exception {
        long created = NOW - 1;
        byte[] envelope = signedEnvelope(PACKAGE_ID, 42, created,
                created + 90 * DAY_MS + 1, KEY_ID, ALGORITHM, EMPTY_PAYLOAD);

        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                        NOW - DAY_MS, NOW + 100 * DAY_MS)
                        .verify(envelope, VerificationMode.INSTALL));
    }

    @Test
    public void verifiedDigestIsDefensivelyCopied() throws Exception {
        VerifiedRulePackage verified = verifier(TrustedRuleKeyRegistry.Status.ACTIVE,
                NOW - DAY_MS, NOW + DAY_MS).verify(
                signedEnvelope(PACKAGE_ID, 42, CREATED_AT, EXPIRES_AT,
                        KEY_ID, ALGORITHM, EMPTY_PAYLOAD), VerificationMode.INSTALL);
        byte[] expected = verified.payloadDigest();

        verified.payloadDigest()[0] ^= 1;

        assertArrayEquals(expected, verified.payloadDigest());
    }

    private static SignedRulePackageVerifier verifier(TrustedRuleKeyRegistry.Status status,
                                                       long notBefore, long notAfter) {
        TrustedRuleKeyRegistry.Entry entry = new TrustedRuleKeyRegistry.Entry(
                KEY_ID, ALGORITHM, notBefore, notAfter, status,
                SignedRulePackageTestKeys.verifier());
        TrustedRuleKeyRegistry registry = keyId -> KEY_ID.equals(keyId)
                ? Optional.of(entry)
                : Optional.empty();
        return new SignedRulePackageVerifier(PACKAGE_ID, registry, () -> NOW);
    }

    private static byte[] signedEnvelope(String packageId, long revision,
                                         long createdAt, long expiresAt,
                                         String keyId, String algorithm,
                                         String payload) throws Exception {
        AudioFingerprintRuleSet ruleSet = AudioFingerprintRuleCodec.fromJson(payload);
        String canonicalPayload = AudioFingerprintRuleCodec.toJson(ruleSet);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        byte[] signature = SignedRulePackageTestKeys.signer().sign(
                SignedRulePackageCodec.signingInput(packageId, revision, createdAt,
                        expiresAt, keyId, algorithm, digest));
        String json = "{"
                + "\"packageSchemaVersion\":1,"
                + "\"packageId\":\"" + packageId + "\","
                + "\"revision\":" + revision + ','
                + "\"createdAtEpochMs\":" + createdAt + ','
                + "\"expiresAtEpochMs\":" + expiresAt + ','
                + "\"payloadSha256\":\"" + hex(digest) + "\","
                + "\"payload\":" + canonicalPayload + ','
                + "\"signature\":{"
                + "\"keyId\":\"" + keyId + "\","
                + "\"algorithm\":\"" + algorithm + "\","
                + "\"value\":\"" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature) + "\"}}";
        return bytes(json);
    }

    private static String tamperSignature(String json) {
        int start = json.indexOf("\"value\":\"") + "\"value\":\"".length();
        char replacement = json.charAt(start) == 'A' ? 'B' : 'A';
        return json.substring(0, start) + replacement + json.substring(start + 1);
    }

    private static String digestHex(String payload) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertCode(SignedRulePackageException.Code expected, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected " + expected);
        } catch (SignedRulePackageException e) {
            assertEquals(expected, e.code());
            assertEquals(expected.name(), e.getMessage());
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
