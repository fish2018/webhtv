package com.fongmi.android.tv.ad.audio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class SignedRulePackageVerifier {

    public static final String ED25519 = "ED25519";
    public static final long MAX_FUTURE_SKEW_MS = 5 * 60_000L;
    public static final long MAX_PACKAGE_LIFETIME_MS = 90L * 24 * 60 * 60_000L;

    private static final Pattern PACKAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String expectedPackageId;
    private final TrustedRuleKeyRegistry keyRegistry;
    private final LongSupplier clock;

    public SignedRulePackageVerifier(String expectedPackageId,
                                     TrustedRuleKeyRegistry keyRegistry,
                                     LongSupplier clock) {
        if (expectedPackageId == null || !PACKAGE_ID_PATTERN.matcher(expectedPackageId).matches()) {
            throw new IllegalArgumentException("expected packageId is invalid");
        }
        if (keyRegistry == null || clock == null) {
            throw new IllegalArgumentException("key registry and clock are required");
        }
        this.expectedPackageId = expectedPackageId;
        this.keyRegistry = keyRegistry;
        this.clock = clock;
    }

    public VerifiedRulePackage verify(byte[] signedEnvelope, VerificationMode mode)
            throws SignedRulePackageException {
        if (mode == null) throw new IllegalArgumentException("verification mode is required");

        SignedRulePackageCodec.Parsed parsed = SignedRulePackageCodec.parse(signedEnvelope);
        byte[] actualDigest = sha256(parsed.canonicalPayload());
        if (!MessageDigest.isEqual(actualDigest, parsed.payloadDigest())) {
            throw error(SignedRulePackageException.Code.PAYLOAD_DIGEST_MISMATCH);
        }
        if (!expectedPackageId.equals(parsed.packageId())) {
            throw error(SignedRulePackageException.Code.PACKAGE_ID_MISMATCH);
        }

        long now = clock.getAsLong();
        if (now < 0) throw new IllegalStateException("clock returned a negative time");
        validatePackageTime(parsed, now);

        if (!ED25519.equals(parsed.algorithm())) {
            throw error(SignedRulePackageException.Code.UNSUPPORTED_SIGNATURE_ALGORITHM);
        }
        TrustedRuleKeyRegistry.Entry key = findKey(parsed.keyId());
        validateKey(key, parsed, mode, now);

        try {
            key.verifier().verify(parsed.signature(), parsed.signingInput());
        } catch (GeneralSecurityException e) {
            throw error(SignedRulePackageException.Code.SIGNATURE_INVALID);
        }

        return new VerifiedRulePackage(parsed.packageId(), parsed.revision(),
                parsed.createdAtEpochMs(), parsed.expiresAtEpochMs(),
                parsed.payloadSha256(), actualDigest, parsed.canonicalPayload(),
                parsed.ruleSet(), parsed.keyId(), parsed.algorithm());
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

    private static void validatePackageTime(SignedRulePackageCodec.Parsed parsed, long now)
            throws SignedRulePackageException {
        long createdAt = parsed.createdAtEpochMs();
        if (createdAt > now && createdAt - now > MAX_FUTURE_SKEW_MS) {
            throw error(SignedRulePackageException.Code.PACKAGE_NOT_YET_VALID);
        }
        if (parsed.expiresAtEpochMs() <= now) {
            throw error(SignedRulePackageException.Code.PACKAGE_EXPIRED);
        }
        if (parsed.expiresAtEpochMs() - createdAt > MAX_PACKAGE_LIFETIME_MS) {
            throw error(SignedRulePackageException.Code.PACKAGE_MALFORMED);
        }
    }

    private static void validateKey(TrustedRuleKeyRegistry.Entry key,
                                    SignedRulePackageCodec.Parsed parsed,
                                    VerificationMode mode, long now)
            throws SignedRulePackageException {
        if (!parsed.keyId().equals(key.keyId()) || !parsed.algorithm().equals(key.algorithm())) {
            throw error(SignedRulePackageException.Code.KEY_NOT_VALID);
        }
        if (key.status() == TrustedRuleKeyRegistry.Status.REVOKED) {
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

    private static byte[] sha256(String canonicalPayload) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static SignedRulePackageException error(SignedRulePackageException.Code code) {
        return new SignedRulePackageException(code);
    }
}
