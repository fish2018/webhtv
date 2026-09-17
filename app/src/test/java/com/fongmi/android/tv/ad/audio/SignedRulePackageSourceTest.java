package com.fongmi.android.tv.ad.audio;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SignedRulePackageSourceTest {

    private static final String PACKAGE_ID = "official.audio.ads";
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void verifiedPackageMapsToStableSignedSnapshotIdentity() {
        VerifiedRulePackage verified = verifiedPackage(42);
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.success(
                        verified, false, 42, List.of()));

        AdAudioRuleSnapshot snapshot = source.load();

        assertEquals("signed:" + PACKAGE_ID, snapshot.sourceId());
        assertEquals("42:0123456789abcdef", snapshot.version());
        assertEquals(verified.ruleSet(), snapshot.ruleSet());
        assertTrue(snapshot.warnings().isEmpty());
        assertFalse(snapshot.hasError());
        assertFalse(snapshot.probeAvailable());
    }

    @Test
    public void matchingSidecarIsAttachedWithoutChangingMainSnapshotIdentity() {
        VerifiedRulePackage main = verifiedPackage(42);
        VerifiedProbeRuleSidecarPackage sidecar = verifiedSidecar(
                PACKAGE_ID, 42, main.payloadDigest());
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.success(main, false, 42, List.of()),
                () -> SignedProbeRuleSidecarStore.LoadResult.success(sidecar, false, 1, List.of()));

        AdAudioRuleSnapshot snapshot = source.load();

        assertEquals("42:0123456789abcdef", snapshot.version());
        assertTrue(snapshot.probeAvailable());
        assertEquals(sidecar.sidecar(), snapshot.probeSidecar());
        assertTrue(snapshot.warnings().isEmpty());
    }

    @Test
    public void mismatchingSidecarIsIgnoredAndMainPackageStillLoads() {
        VerifiedRulePackage main = verifiedPackage(42);
        VerifiedProbeRuleSidecarPackage sidecar = verifiedSidecar(
                PACKAGE_ID, 41, main.payloadDigest());
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.success(main, false, 42, List.of()),
                () -> SignedProbeRuleSidecarStore.LoadResult.success(sidecar, false, 1, List.of()));

        AdAudioRuleSnapshot snapshot = source.load();

        assertFalse(snapshot.probeAvailable());
        assertNull(snapshot.probeSidecar());
        assertEquals(List.of("PROBE_SIDECAR_MISMATCH"), snapshot.warnings());
        assertFalse(snapshot.hasError());
    }

    @Test
    public void unavailableSidecarAddsWarningWithoutFailingMainPackage() {
        VerifiedRulePackage main = verifiedPackage(42);
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.success(main, false, 42, List.of()),
                () -> SignedProbeRuleSidecarStore.LoadResult.failure(
                        0, SignedRulePackageException.Code.NO_VALID_SIGNED_PACKAGE));

        AdAudioRuleSnapshot snapshot = source.load();

        assertFalse(snapshot.probeAvailable());
        assertEquals(List.of("PROBE_SIDECAR_UNAVAILABLE"), snapshot.warnings());
        assertFalse(snapshot.hasError());
    }

    @Test
    public void recoveredPreviousAddsCacheRecoveredWarningExactlyOnce() {
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.success(
                        verifiedPackage(7), true, 9, List.of("CACHE_RECOVERED")));

        AdAudioRuleSnapshot snapshot = source.load();

        assertEquals(List.of("CACHE_RECOVERED"), snapshot.warnings());
    }

    @Test
    public void unavailableCacheMapsItsSpecificErrorToAnEmptySnapshot() {
        SignedRulePackageSource source = new SignedRulePackageSource(
                PACKAGE_ID,
                () -> SignedRulePackageStore.LoadResult.failure(
                        8, SignedRulePackageException.Code.CACHE_IO_FAILED));

        AdAudioRuleSnapshot snapshot = source.load();

        assertEquals("signed:" + PACKAGE_ID, snapshot.sourceId());
        assertEquals("", snapshot.version());
        assertFalse(snapshot.hasRules());
        assertEquals("CACHE_IO_FAILED", snapshot.lastError());
    }

    private static VerifiedRulePackage verifiedPackage(long revision) {
        return new VerifiedRulePackage(
                PACKAGE_ID,
                revision,
                1_000L,
                2_000L,
                DIGEST,
                new byte[32],
                "{}",
                AudioFingerprintRuleSet.empty(),
                "test-key",
                "ED25519");
    }

    private static VerifiedProbeRuleSidecarPackage verifiedSidecar(
            String sourcePackageId, long sourceRevision, byte[] sourceDigest) {
        byte[] rules = "{\"format\":\"ad-audio-probe-rules\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] sidecarDigest = sha256(rules);
        ProbeRuleSidecar sidecar = ProbeRuleSidecar.verified(
                sourcePackageId, sourceRevision, sourceDigest,
                ProbeRuleSidecar.ALGORITHM_ID, "converter-1", rules, sidecarDigest);
        byte[] artifactDigest = new byte[32];
        return new VerifiedProbeRuleSidecarPackage(
                "probe.package", 1L, 1_000L, 2_000L, sidecar,
                "0000000000000000000000000000000000000000000000000000000000000000",
                artifactDigest, "test-key", "ED25519", new byte[] {1, 2, 3});
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
