package com.fongmi.android.tv.ad.audio;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class SignedProbeRuleSidecarVerifier {

    private static final Pattern PACKAGE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String expectedPackageId;
    private final TrustedRuleKeyRegistry keyRegistry;
    private final LongSupplier clock;

    public SignedProbeRuleSidecarVerifier(String expectedPackageId,
                                          TrustedRuleKeyRegistry keyRegistry,
                                          LongSupplier clock) {
        if (expectedPackageId == null || !PACKAGE_ID.matcher(expectedPackageId).matches()) {
            throw new IllegalArgumentException("expected packageId is invalid");
        }
        if (keyRegistry == null || clock == null) {
            throw new IllegalArgumentException("key registry and clock are required");
        }
        this.expectedPackageId = expectedPackageId;
        this.keyRegistry = keyRegistry;
        this.clock = clock;
    }

    public VerifiedProbeRuleSidecarPackage verify(byte[] signedEnvelope,
                                                   VerificationMode mode)
            throws SignedRulePackageException {
        if (mode == null) throw new IllegalArgumentException("verification mode is required");
        SignedProbeRuleSidecarCodec.Parsed parsed =
                SignedProbeRuleSidecarCodec.parse(signedEnvelope);
        if (!expectedPackageId.equals(parsed.packageId())) {
            throw error(SignedRulePackageException.Code.PACKAGE_ID_MISMATCH);
        }
        long now = clock.getAsLong();
        if (now < 0L) throw new IllegalStateException("clock returned a negative time");
        validateTime(parsed, now);
        if (!SignedRulePackageVerifier.ED25519.equals(parsed.signatureAlgorithm())) {
            throw error(SignedRulePackageException.Code.UNSUPPORTED_SIGNATURE_ALGORITHM);
        }
        TrustedRuleKeyRegistry.Entry key = findKey(parsed.keyId());
        validateKey(key, parsed, mode, now);
        try {
            key.verifier().verify(parsed.signature(), parsed.signingInput());
        } catch (GeneralSecurityException e) {
            throw error(SignedRulePackageException.Code.SIGNATURE_INVALID);
        }
        ProbeRuleSidecar sidecar = ProbeRuleSidecar.verified(
                parsed.sourcePackageId(), parsed.sourceRevision(),
                parsed.sourcePayloadDigest(), parsed.probeAlgorithm(),
                parsed.converterVersion(), parsed.canonicalRules(), parsed.rulesDigest());
        byte[] artifactDigest = sha256(parsed.signingInput());
        return new VerifiedProbeRuleSidecarPackage(parsed.packageId(), parsed.revision(),
                parsed.createdAtEpochMs(), parsed.expiresAtEpochMs(), sidecar,
                hex(artifactDigest), artifactDigest, parsed.keyId(),
                parsed.signatureAlgorithm(), parsed.signingInput());
    }

    String expectedPackageId() {
        return expectedPackageId;
    }

    private TrustedRuleKeyRegistry.Entry findKey(String keyId)
            throws SignedRulePackageException {
        Optional<TrustedRuleKeyRegistry.Entry> entry = keyRegistry.find(keyId);
        if (entry == null || entry.isEmpty()) {
            throw error(SignedRulePackageException.Code.UNKNOWN_KEY);
        }
        return entry.get();
    }

    private static void validateTime(SignedProbeRuleSidecarCodec.Parsed parsed, long now)
            throws SignedRulePackageException {
        long createdAt = parsed.createdAtEpochMs();
        if (createdAt > now
                && createdAt - now > SignedRulePackageVerifier.MAX_FUTURE_SKEW_MS) {
            throw error(SignedRulePackageException.Code.PACKAGE_NOT_YET_VALID);
        }
        if (parsed.expiresAtEpochMs() <= now) {
            throw error(SignedRulePackageException.Code.PACKAGE_EXPIRED);
        }
        if (parsed.expiresAtEpochMs() - createdAt
                > SignedRulePackageVerifier.MAX_PACKAGE_LIFETIME_MS) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void validateKey(TrustedRuleKeyRegistry.Entry key,
                                    SignedProbeRuleSidecarCodec.Parsed parsed,
                                    VerificationMode mode, long now)
            throws SignedRulePackageException {
        if (!parsed.keyId().equals(key.keyId())
                || !parsed.signatureAlgorithm().equals(key.algorithm())
                || key.status() == TrustedRuleKeyRegistry.Status.REVOKED) {
            throw error(SignedRulePackageException.Code.KEY_NOT_VALID);
        }
        long createdAt = parsed.createdAtEpochMs();
        if (createdAt < key.notBeforeEpochMs() || createdAt > key.notAfterEpochMs()) {
            throw error(SignedRulePackageException.Code.KEY_NOT_VALID);
        }
        if (mode == VerificationMode.INSTALL
                && (key.status() != TrustedRuleKeyRegistry.Status.ACTIVE
                || now < key.notBeforeEpochMs() || now > key.notAfterEpochMs())) {
            throw error(SignedRulePackageException.Code.KEY_NOT_VALID);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(byte[] value) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            int current = value[i] & 0xff;
            result[i * 2] = digits[current >>> 4];
            result[i * 2 + 1] = digits[current & 0x0f];
        }
        return new String(result);
    }

    private static SignedRulePackageException error(SignedRulePackageException.Code code) {
        return new SignedRulePackageException(code);
    }
}
