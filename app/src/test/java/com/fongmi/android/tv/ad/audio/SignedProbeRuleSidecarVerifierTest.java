package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

public class SignedProbeRuleSidecarVerifierTest {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;
    private static final long NOW = 1_786_900_000_000L;
    private static final long CREATED_AT = NOW - MINUTE_MS;
    private static final long EXPIRES_AT = NOW + DAY_MS;
    private static final String PACKAGE_ID = "official.ad-audio.probe-v1";
    private static final String SOURCE_PACKAGE_ID = "official.ad-audio";
    private static final long ARTIFACT_REVISION = 5L;
    private static final long SOURCE_REVISION = 42L;
    private static final String KEY_ID = "release-1";
    private static final String SIGNATURE_ALGORITHM = "ED25519";
    private static final String CONVERTER_VERSION = "probe-oracle-1";
    private static final String ZERO_SIGNATURE =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    public void activeKeyVerifiesIndependentSidecarArtifact() throws Exception {
        byte[] envelope = signedEnvelope();

        VerifiedProbeRuleSidecarPackage verified = verifier(
                TrustedRuleKeyRegistry.Status.ACTIVE).verify(envelope, VerificationMode.INSTALL);

        assertEquals(PACKAGE_ID, verified.packageId());
        assertEquals(ARTIFACT_REVISION, verified.revision());
        assertEquals(CREATED_AT, verified.createdAtEpochMs());
        assertEquals(EXPIRES_AT, verified.expiresAtEpochMs());
        assertEquals(KEY_ID, verified.keyId());
        assertEquals(SIGNATURE_ALGORITHM, verified.signatureAlgorithm());
        assertEquals(SOURCE_PACKAGE_ID, verified.sidecar().sourcePackageId());
        assertEquals(SOURCE_REVISION, verified.sidecar().sourceRevision());
        assertEquals(CONVERTER_VERSION, verified.sidecar().converterVersion());
        assertArrayEquals(rules(), verified.sidecar().canonicalRules());
        verified.sidecar().requireBoundTo(SOURCE_PACKAGE_ID, SOURCE_REVISION, sourceDigest());
        assertEquals(64, verified.artifactSha256().length());
    }

    @Test
    public void everySecurityFieldIsCoveredBySignatureOrDigest() throws Exception {
        String valid = text(signedEnvelope());
        String sourceDigest = hex(sourceDigest());
        String rulesDigest = hex(digest(rules()));

        assertCode(SignedRulePackageException.Code.PACKAGE_ID_MISMATCH,
                () -> verifier(TrustedRuleKeyRegistry.Status.ACTIVE).verify(
                        bytes(valid.replace("\"packageId\":\"" + PACKAGE_ID + "\"",
                                "\"packageId\":\"other.probe-v1\"")), VerificationMode.INSTALL));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(valid.replace("\"revision\":5", "\"revision\":6")));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(valid.replace("\"sourcePackageId\":\"" + SOURCE_PACKAGE_ID + "\"",
                        "\"sourcePackageId\":\"other.ad-audio\"")));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(valid.replace("\"sourceRevision\":42",
                        "\"sourceRevision\":43")));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(valid.replace(sourceDigest, flipHex(sourceDigest))));
        assertCode(SignedRulePackageException.Code.PAYLOAD_INVALID,
                () -> verifyTampered(valid.replace(ProbeRuleSidecar.ALGORITHM_ID,
                        "spectral-sequence-v2")));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(valid.replace(CONVERTER_VERSION, "probe-oracle-2")));
        assertCode(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH,
                () -> verifyTampered(valid.replace(rulesDigest, flipHex(rulesDigest))));

        byte[] changedRules = "{\"format\":\"changed\"}".getBytes(StandardCharsets.UTF_8);
        assertCode(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH,
                () -> verifyTampered(valid.replace(base64(rules()), base64(changedRules))));
        assertCode(SignedRulePackageException.Code.SIGNATURE_INVALID,
                () -> verifyTampered(tamperSignature(valid)));
    }

    @Test
    public void malformedJsonAndEncodingAreRejectedBeforeVerification() throws Exception {
        String valid = text(signedEnvelope());

        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid.replace(
                        "\"artifactType\"", "\"unknown\":0,\"artifactType\""))));
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid.replace(
                        "\"revision\":5", "\"revision\":5,\"revision\":6"))));
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid + "{}")));
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid.replace(
                        "\"revision\":5", "\"revision\":5.0"))));
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid.replace(
                        base64(rules()), "not+base64"))));
        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(new byte[]{(byte) 0xC3, 0x28}));
    }

    @Test
    public void nonCanonicalSignatureBase64UrlIsRejected() throws Exception {
        String valid = text(signedEnvelope());
        String signature = signatureValue(valid);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int canonicalIndex = alphabet.indexOf(signature.charAt(signature.length() - 1));
        String nonCanonical = signature.substring(0, signature.length() - 1)
                + alphabet.charAt((canonicalIndex & 0x30) + 1);
        assertArrayEquals(Base64.getUrlDecoder().decode(signature),
                Base64.getUrlDecoder().decode(nonCanonical));

        assertCode(SignedRulePackageException.Code.PACKAGE_MALFORMED,
                () -> SignedProbeRuleSidecarCodec.parse(bytes(valid.replace(
                        "\"value\":\"" + signature + "\"",
                        "\"value\":\"" + nonCanonical + "\""))));
    }

    @Test
    public void retiredKeyIsCacheOnlyAndRevokedKeyIsAlwaysRejected() throws Exception {
        byte[] envelope = signedEnvelope();

        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier(TrustedRuleKeyRegistry.Status.RETIRED)
                        .verify(envelope, VerificationMode.INSTALL));
        assertEquals(ARTIFACT_REVISION,
                verifier(TrustedRuleKeyRegistry.Status.RETIRED)
                        .verify(envelope, VerificationMode.CACHE).revision());
        assertCode(SignedRulePackageException.Code.KEY_NOT_VALID,
                () -> verifier(TrustedRuleKeyRegistry.Status.REVOKED)
                        .verify(envelope, VerificationMode.CACHE));
    }

    @Test
    public void artifactArraysAreDefensivelyCopied() throws Exception {
        VerifiedProbeRuleSidecarPackage verified = verifier(
                TrustedRuleKeyRegistry.Status.ACTIVE).verify(
                signedEnvelope(), VerificationMode.INSTALL);

        byte[] first = verified.artifactDigest();
        first[0] ^= 1;

        assertArrayEquals(digest(verified.signingInput()), verified.artifactDigest());
    }

    private static byte[] signedEnvelope() throws Exception {
        String unsigned = envelope(ZERO_SIGNATURE);
        byte[] input = SignedProbeRuleSidecarCodec.parse(bytes(unsigned)).signingInput();
        return bytes(envelope(base64(SignedRulePackageTestKeys.signer().sign(input))));
    }

    private static String envelope(String signature) throws Exception {
        return "{"
                + "\"artifactSchemaVersion\":1,"
                + "\"artifactType\":\"ad-audio-probe-sidecar\","
                + "\"packageId\":\"" + PACKAGE_ID + "\","
                + "\"revision\":" + ARTIFACT_REVISION + ","
                + "\"createdAtEpochMs\":" + CREATED_AT + ","
                + "\"expiresAtEpochMs\":" + EXPIRES_AT + ","
                + "\"sourcePackageId\":\"" + SOURCE_PACKAGE_ID + "\","
                + "\"sourceRevision\":" + SOURCE_REVISION + ","
                + "\"sourcePayloadSha256\":\"" + hex(sourceDigest()) + "\","
                + "\"algorithm\":\"" + ProbeRuleSidecar.ALGORITHM_ID + "\","
                + "\"converterVersion\":\"" + CONVERTER_VERSION + "\","
                + "\"rulesSha256\":\"" + hex(digest(rules())) + "\","
                + "\"rulesBase64Url\":\"" + base64(rules()) + "\","
                + "\"signature\":{"
                + "\"keyId\":\"" + KEY_ID + "\","
                + "\"algorithm\":\"" + SIGNATURE_ALGORITHM + "\","
                + "\"value\":\"" + signature + "\"}}";
    }

    private static SignedProbeRuleSidecarVerifier verifier(TrustedRuleKeyRegistry.Status status) {
        TrustedRuleKeyRegistry keys = keyId -> KEY_ID.equals(keyId)
                ? Optional.of(new TrustedRuleKeyRegistry.Entry(KEY_ID, SIGNATURE_ALGORITHM,
                NOW - DAY_MS, NOW + DAY_MS, status, SignedRulePackageTestKeys.verifier()))
                : Optional.empty();
        return new SignedProbeRuleSidecarVerifier(PACKAGE_ID, keys, () -> NOW);
    }

    private static VerifiedProbeRuleSidecarPackage verifyTampered(String value) throws Exception {
        return verifier(TrustedRuleKeyRegistry.Status.ACTIVE)
                .verify(bytes(value), VerificationMode.INSTALL);
    }

    private static void assertCode(SignedRulePackageException.Code expected,
                                   ThrowingRunnable action) {
        try {
            action.run();
            fail("expected " + expected);
        } catch (SignedRulePackageException e) {
            assertEquals(expected, e.code());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String tamperSignature(String envelope) {
        int index = envelope.lastIndexOf("\"value\":\"") + "\"value\":\"".length();
        char current = envelope.charAt(index);
        char replacement = current == 'A' ? 'B' : 'A';
        return envelope.substring(0, index) + replacement + envelope.substring(index + 1);
    }

    private static String signatureValue(String envelope) {
        int start = envelope.lastIndexOf("\"value\":\"") + "\"value\":\"".length();
        return envelope.substring(start, envelope.indexOf('"', start));
    }

    private static byte[] rules() {
        return ("{\"format\":\"ad-audio-probe-rules\",\"schemaVersion\":1,"
                + "\"revision\":42,\"algorithm\":\"spectral-sequence-v1\","
                + "\"rules\":[]}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sourceDigest() throws Exception {
        return digest("signed-v2-payload".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static String flipHex(String value) {
        return (value.charAt(0) == '0' ? "1" : "0") + value.substring(1);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
