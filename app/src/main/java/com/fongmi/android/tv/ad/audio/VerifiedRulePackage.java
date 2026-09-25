package com.fongmi.android.tv.ad.audio;

public final class VerifiedRulePackage {

    private final String packageId;
    private final long revision;
    private final long createdAtEpochMs;
    private final long expiresAtEpochMs;
    private final String payloadSha256;
    private final byte[] payloadDigest;
    private final String canonicalPayload;
    private final AudioFingerprintRuleSet ruleSet;
    private final String keyId;
    private final String signatureAlgorithm;

    VerifiedRulePackage(String packageId, long revision,
                        long createdAtEpochMs, long expiresAtEpochMs,
                        String payloadSha256, byte[] payloadDigest,
                        String canonicalPayload, AudioFingerprintRuleSet ruleSet,
                        String keyId, String signatureAlgorithm) {
        if (packageId == null || payloadSha256 == null || payloadDigest == null
                || canonicalPayload == null || ruleSet == null || keyId == null
                || signatureAlgorithm == null) {
            throw new IllegalArgumentException("verified package fields are required");
        }
        if (payloadDigest.length != 32) {
            throw new IllegalArgumentException("payload digest length is invalid");
        }
        this.packageId = packageId;
        this.revision = revision;
        this.createdAtEpochMs = createdAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.payloadSha256 = payloadSha256;
        this.payloadDigest = payloadDigest.clone();
        this.canonicalPayload = canonicalPayload;
        this.ruleSet = ruleSet;
        this.keyId = keyId;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String packageId() {
        return packageId;
    }

    public long revision() {
        return revision;
    }

    public long createdAtEpochMs() {
        return createdAtEpochMs;
    }

    public long expiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public String payloadSha256() {
        return payloadSha256;
    }

    public byte[] payloadDigest() {
        return payloadDigest.clone();
    }

    public String canonicalPayload() {
        return canonicalPayload;
    }

    public AudioFingerprintRuleSet ruleSet() {
        return ruleSet;
    }

    public String keyId() {
        return keyId;
    }

    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }
}
