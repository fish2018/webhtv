package com.fongmi.android.tv.ad.audio;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ProbeRuleSidecar {

    public static final String ALGORITHM_ID = "spectral-sequence-v1";

    private static final int DIGEST_BYTES = 32;
    private static final Pattern PACKAGE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern CONVERTER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String sourcePackageId;
    private final long sourceRevision;
    private final byte[] sourceDigest;
    private final String algorithm;
    private final String converterVersion;
    private final byte[] canonicalRules;
    private final byte[] sidecarDigest;

    private ProbeRuleSidecar(String sourcePackageId, long sourceRevision,
                             byte[] sourceDigest, String algorithm,
                             String converterVersion, byte[] canonicalRules,
                             byte[] sidecarDigest) {
        this.sourcePackageId = sourcePackageId;
        this.sourceRevision = sourceRevision;
        this.sourceDigest = sourceDigest;
        this.algorithm = algorithm;
        this.converterVersion = converterVersion;
        this.canonicalRules = canonicalRules;
        this.sidecarDigest = sidecarDigest;
    }

    public static ProbeRuleSidecar verified(String sourcePackageId, long sourceRevision,
                                            byte[] sourceDigest,
                                            String algorithm, String converterVersion,
                                            byte[] canonicalRules, byte[] sidecarDigest) {
        if (sourcePackageId == null || !PACKAGE_ID.matcher(sourcePackageId).matches()) {
            throw new IllegalArgumentException("invalid sourcePackageId");
        }
        if (sourceRevision <= 0L) {
            throw new IllegalArgumentException("sourceRevision must be positive");
        }
        byte[] sourceDigestCopy = requireDigest(sourceDigest, "sourceDigest");
        if (!ALGORITHM_ID.equals(algorithm)) {
            throw new IllegalArgumentException("unsupported probe algorithm");
        }
        if (converterVersion == null || !CONVERTER_ID.matcher(converterVersion).matches()) {
            throw new IllegalArgumentException("invalid converterVersion");
        }
        Objects.requireNonNull(canonicalRules, "canonicalRules");
        if (canonicalRules.length == 0 || canonicalRules.length > AdAudioRuleStore.MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("probe rules are empty or too large");
        }
        requireUtf8(canonicalRules);
        byte[] canonicalRulesCopy = canonicalRules.clone();
        byte[] sidecarDigestCopy = requireDigest(sidecarDigest, "sidecarDigest");
        if (!MessageDigest.isEqual(sha256(canonicalRulesCopy), sidecarDigestCopy)) {
            throw new IllegalArgumentException("probe sidecar digest mismatch");
        }
        return new ProbeRuleSidecar(sourcePackageId, sourceRevision, sourceDigestCopy, algorithm,
                converterVersion, canonicalRulesCopy, sidecarDigestCopy);
    }

    public void requireBoundTo(String packageId, long revision, byte[] payloadDigest) {
        if (!sourcePackageId.equals(packageId) || revision != sourceRevision
                || !MessageDigest.isEqual(sourceDigest, requireDigest(payloadDigest, "payloadDigest"))) {
            throw new IllegalArgumentException("probe sidecar is not bound to the signed payload");
        }
    }

    public String sourcePackageId() {
        return sourcePackageId;
    }

    public long sourceRevision() {
        return sourceRevision;
    }

    public byte[] sourceDigest() {
        return sourceDigest.clone();
    }

    public String algorithm() {
        return algorithm;
    }

    public String converterVersion() {
        return converterVersion;
    }

    public byte[] canonicalRules() {
        return canonicalRules.clone();
    }

    public byte[] sidecarDigest() {
        return sidecarDigest.clone();
    }

    private static byte[] requireDigest(byte[] digest, String name) {
        if (digest == null || digest.length != DIGEST_BYTES) {
            throw new IllegalArgumentException(name + " must be 32 bytes");
        }
        return digest.clone();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void requireUtf8(byte[] value) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("probe rules must be valid UTF-8");
        }
    }
}
