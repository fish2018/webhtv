package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SignedProbeRuleSidecarStoreTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installAndLoadUseIndependentCurrentSlot() throws Exception {
        Path directory = temporaryFolder.newFolder("sidecar").toPath();
        SignedProbeRuleSidecarStore store = new SignedProbeRuleSidecarStore(
                directory, SignedProbeRuleSidecarFixtures.verifier());

        SignedProbeRuleSidecarStore.InstallResult installed = store.install(
                SignedProbeRuleSidecarFixtures.signedEnvelope(1L, "probe-1"));
        SignedProbeRuleSidecarStore.LoadResult loaded = store.load();

        assertTrue(installed.changed());
        assertEquals(1L, installed.verifiedPackage().revision());
        assertTrue(loaded.hasPackage());
        assertFalse(loaded.recoveredPrevious());
        assertEquals(1L, loaded.verifiedPackage().revision());
        assertEquals(1L, loaded.highestAcceptedRevision());
    }

    @Test
    public void corruptCurrentRecoversPreviousWithoutLoweringHighWater() throws Exception {
        Path directory = temporaryFolder.newFolder("recovery").toPath();
        SignedProbeRuleSidecarStore store = new SignedProbeRuleSidecarStore(
                directory, SignedProbeRuleSidecarFixtures.verifier());
        store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(1L, "probe-1"));
        store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(2L, "probe-2"));

        Files.write(directory.resolve("current.json"),
                "corrupt".getBytes(StandardCharsets.UTF_8));

        SignedProbeRuleSidecarStore.LoadResult loaded = store.load();

        assertTrue(loaded.hasPackage());
        assertTrue(loaded.recoveredPrevious());
        assertEquals(1L, loaded.verifiedPackage().revision());
        assertEquals(2L, loaded.highestAcceptedRevision());
        assertTrue(loaded.warnings().contains("CACHE_RECOVERED"));
    }

    @Test
    public void rollbackAndSameRevisionConflictAreRejected() throws Exception {
        Path directory = temporaryFolder.newFolder("rollback").toPath();
        SignedProbeRuleSidecarStore store = new SignedProbeRuleSidecarStore(
                directory, SignedProbeRuleSidecarFixtures.verifier());
        store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(2L, "probe-2"));

        assertCode(SignedRulePackageException.Code.REVISION_ROLLBACK,
                () -> store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(1L, "probe-1")));
        assertCode(SignedRulePackageException.Code.REVISION_CONFLICT,
                () -> store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(2L, "probe-3")));
    }

    @Test
    public void clearDownloadedLeavesNoUsableSidecar() throws Exception {
        Path directory = temporaryFolder.newFolder("clear").toPath();
        SignedProbeRuleSidecarStore store = new SignedProbeRuleSidecarStore(
                directory, SignedProbeRuleSidecarFixtures.verifier());
        store.install(SignedProbeRuleSidecarFixtures.signedEnvelope(1L, "probe-1"));

        store.clearDownloaded();

        assertFalse(store.load().hasPackage());
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
