package com.fongmi.android.tv.ad.audio;

public final class VerifiedProbeRuleSidecarPackage {

    private final String packageId;
    private final long revision;
    private final long createdAtEpochMs;
    private final long expiresAtEpochMs;
    private final ProbeRuleSidecar sidecar;
    private final String artifactSha256;
    private final byte[] artifactDigest;
    private final String keyId;
    private final String signatureAlgorithm;
    private final byte[] signingInput;

    VerifiedProbeRuleSidecarPackage(String packageId, long revision,
                                    long createdAtEpochMs, long expiresAtEpochMs,
                                    ProbeRuleSidecar sidecar, String artifactSha256,
                                    byte[] artifactDigest, String keyId,
                                    String signatureAlgorithm, byte[] signingInput) {
        if (packageId == null || sidecar == null || artifactSha256 == null
                || artifactDigest == null || keyId == null || signatureAlgorithm == null
                || signingInput == null) {
            throw new IllegalArgumentException("verified sidecar package fields are required");
        }
        if (revision <= 0L || artifactDigest.length != 32 || artifactSha256.length() != 64) {
            throw new IllegalArgumentException("verified sidecar package metadata is invalid");
        }
        this.packageId = packageId;
        this.revision = revision;
        this.createdAtEpochMs = createdAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.sidecar = sidecar;
        this.artifactSha256 = artifactSha256;
        this.artifactDigest = artifactDigest.clone();
        this.keyId = keyId;
        this.signatureAlgorithm = signatureAlgorithm;
        this.signingInput = signingInput.clone();
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

    public ProbeRuleSidecar sidecar() {
        return sidecar;
    }

    public String artifactSha256() {
        return artifactSha256;
    }

    public byte[] artifactDigest() {
        return artifactDigest.clone();
    }

    public String keyId() {
        return keyId;
    }

    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    byte[] signingInput() {
        return signingInput.clone();
    }
}
