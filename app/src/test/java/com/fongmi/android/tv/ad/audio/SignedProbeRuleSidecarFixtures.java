package com.fongmi.android.tv.ad.audio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

final class SignedProbeRuleSidecarFixtures {

    static final long NOW = 1_786_900_000_000L;
    static final String PACKAGE_ID = "official.ad-audio.probe-v1";
    static final String SOURCE_PACKAGE_ID = "official.ad-audio";
    static final String KEY_ID = "release-1";
    static final String SIGNATURE_ALGORITHM = "ED25519";

    private SignedProbeRuleSidecarFixtures() {
    }

    static byte[] signedEnvelope(long artifactRevision, String converterVersion)
            throws Exception {
        String unsigned = envelope(artifactRevision, converterVersion,
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        byte[] input = SignedProbeRuleSidecarCodec.parse(bytes(unsigned)).signingInput();
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                SignedRulePackageTestKeys.signer().sign(input));
        return bytes(envelope(artifactRevision, converterVersion, signature));
    }

    static SignedProbeRuleSidecarVerifier verifier() {
        TrustedRuleKeyRegistry keys = keyId -> KEY_ID.equals(keyId)
                ? Optional.of(new TrustedRuleKeyRegistry.Entry(KEY_ID, SIGNATURE_ALGORITHM,
                NOW - 86_400_000L, NOW + 86_400_000L,
                TrustedRuleKeyRegistry.Status.ACTIVE, SignedRulePackageTestKeys.verifier()))
                : Optional.empty();
        return new SignedProbeRuleSidecarVerifier(PACKAGE_ID, keys, () -> NOW);
    }

    static byte[] rules() {
        return ("{\"format\":\"ad-audio-probe-rules\",\"schemaVersion\":1,"
                + "\"revision\":42,\"algorithm\":\"spectral-sequence-v1\","
                + "\"rules\":[]}").getBytes(StandardCharsets.UTF_8);
    }

    private static String envelope(long artifactRevision, String converterVersion,
                                   String signature) throws Exception {
        byte[] rules = rules();
        return "{"
                + "\"artifactSchemaVersion\":1,"
                + "\"artifactType\":\"ad-audio-probe-sidecar\","
                + "\"packageId\":\"" + PACKAGE_ID + "\","
                + "\"revision\":" + artifactRevision + ","
                + "\"createdAtEpochMs\":" + (NOW - 60_000L) + ","
                + "\"expiresAtEpochMs\":" + (NOW + 86_400_000L) + ","
                + "\"sourcePackageId\":\"" + SOURCE_PACKAGE_ID + "\","
                + "\"sourceRevision\":42,"
                + "\"sourcePayloadSha256\":\"" + hex(digest("signed-v2-payload")) + "\","
                + "\"algorithm\":\"spectral-sequence-v1\","
                + "\"converterVersion\":\"" + converterVersion + "\","
                + "\"rulesSha256\":\"" + hex(digest(rules)) + "\","
                + "\"rulesBase64Url\":\""
                + Base64.getUrlEncoder().withoutPadding().encodeToString(rules) + "\","
                + "\"signature\":{\"keyId\":\"" + KEY_ID
                + "\",\"algorithm\":\"" + SIGNATURE_ALGORITHM
                + "\",\"value\":\"" + signature + "\"}}";
    }

    private static byte[] digest(String value) throws Exception {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
