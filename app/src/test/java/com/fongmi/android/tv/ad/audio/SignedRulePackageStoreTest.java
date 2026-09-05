package com.fongmi.android.tv.ad.audio;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SignedRulePackageStoreTest {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;
    private static final long NOW = 1_786_900_000_000L;
    private static final long CREATED_AT = NOW - MINUTE_MS;
    private static final long EXPIRES_AT = NOW + 30 * DAY_MS;
    private static final String PACKAGE_ID = "official.ad-audio";
    private static final String KEY_ID = "release-1";
    private static final String ALGORITHM = "ED25519";
    private static final String EMPTY_PAYLOAD =
            "{\"schemaVersion\":2,\"algorithm\":{"
                    + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                    + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},\"rules\":[]}";
    private static final String ONE_RULE_PAYLOAD =
            "{\"schemaVersion\":2,\"algorithm\":{"
                    + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                    + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},"
                    + "\"rules\":[{\"id\":\"ad-1\",\"durationMs\":15000,"
                    + "\"anchorOffsetMs\":0,\"anchorDurationMs\":3000,"
                    + "\"fingerprint\":[\"00000000\",\"00000001\","
                    + "\"00000002\",\"00000003\"]}]}";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void firstInstallPersistsCurrentAndState() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("first");
        SignedRulePackageStore store = store(directory, trust);

        SignedRulePackageStore.InstallResult installed = store.install(envelope(1));
        SignedRulePackageStore.LoadResult loaded = store.load();

        assertTrue(installed.changed());
        assertEquals(1L, installed.verifiedPackage().revision());
        assertEquals(1L, installed.highestAcceptedRevision());
        assertTrue(Files.exists(directory.resolve("current.json")));
        assertTrue(Files.exists(directory.resolve("state.json")));
        assertFalse(Files.exists(directory.resolve("previous.json")));
        assertTrue(loaded.hasPackage());
        assertEquals(1L, loaded.verifiedPackage().revision());
        assertFalse(loaded.recoveredPrevious());
        assertEquals(1L, loaded.highestAcceptedRevision());
    }

    @Test
    public void higherRevisionRotatesCurrentToPrevious() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("rotation");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));

        store.install(envelope(2));
        String previous = Files.readString(directory.resolve("previous.json"));

        assertEquals(2L, store.load().verifiedPackage().revision());
        assertTrue(previous.contains("\"revision\":1"));
    }

    @Test
    public void lowerRevisionIsRejectedWithoutChangingCurrent() throws Exception {
        TestTrust trust = new TestTrust();
        SignedRulePackageStore store = store(directory("rollback"), trust);
        store.install(envelope(2));

        assertCode(SignedRulePackageException.Code.REVISION_ROLLBACK,
                () -> store.install(envelope(1)));
        assertEquals(2L, store.load().verifiedPackage().revision());
    }

    @Test
    public void sameRevisionAndDigestIsIdempotentWithoutAnyWrite() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("idempotent");
        store(directory, trust).install(envelope(1));
        SignedRulePackageStore noWriteStore = new SignedRulePackageStore(
                directory, trust.verifier(), stage -> {
            throw new IOException("write should not occur");
        });

        SignedRulePackageStore.InstallResult result = noWriteStore.install(envelope(1));

        assertFalse(result.changed());
        assertEquals(1L, result.highestAcceptedRevision());
    }

    @Test
    public void sameRevisionWithDifferentDigestIsRejected() throws Exception {
        TestTrust trust = new TestTrust();
        SignedRulePackageStore store = store(directory("conflict"), trust);
        store.install(envelope(1));

        assertCode(SignedRulePackageException.Code.REVISION_CONFLICT,
                () -> store.install(signedEnvelope(1, CREATED_AT, EXPIRES_AT,
                        ONE_RULE_PAYLOAD)));
        assertEquals(0, store.load().verifiedPackage().ruleSet().rules().size());
    }

    @Test
    public void sameRevisionCanRestoreCurrentAfterDownloadedFilesAreCleared() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("clear-restore");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));

        store.clearDownloaded();
        SignedRulePackageStore.LoadResult cleared = store.load();
        SignedRulePackageStore.InstallResult restored = store.install(envelope(1));

        assertFalse(cleared.hasPackage());
        assertEquals(1L, cleared.highestAcceptedRevision());
        assertTrue(restored.changed());
        assertEquals(1L, store.load().verifiedPackage().revision());
    }

    @Test
    public void corruptCurrentRecoversPreviousWithoutLoweringHighWater() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("recover-previous");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));
        store.install(envelope(2));
        corrupt(directory.resolve("current.json"));

        SignedRulePackageStore.LoadResult recovered = store(directory, trust).load();

        assertTrue(recovered.hasPackage());
        assertTrue(recovered.recoveredPrevious());
        assertTrue(recovered.warnings().contains("CACHE_RECOVERED"));
        assertEquals(1L, recovered.verifiedPackage().revision());
        assertEquals(2L, recovered.highestAcceptedRevision());
        assertCode(SignedRulePackageException.Code.REVISION_ROLLBACK,
                () -> store(directory, trust).install(envelope(1)));
    }

    @Test
    public void expiredPreviousIsNotRecovered() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("expired-previous");
        SignedRulePackageStore store = store(directory, trust);
        store.install(signedEnvelope(1, CREATED_AT, NOW + 1_000, EMPTY_PAYLOAD));
        store.install(signedEnvelope(2, CREATED_AT, NOW + DAY_MS, EMPTY_PAYLOAD));
        corrupt(directory.resolve("current.json"));
        trust.now.set(NOW + 2_000);

        SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

        assertFalse(loaded.hasPackage());
        assertEquals(SignedRulePackageException.Code.NO_VALID_SIGNED_PACKAGE,
                loaded.errorCode());
        assertEquals(2L, loaded.highestAcceptedRevision());
    }

    @Test
    public void revokedPreviousIsNotRecovered() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("revoked-previous");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));
        store.install(envelope(2));
        corrupt(directory.resolve("current.json"));
        trust.status.set(TrustedRuleKeyRegistry.Status.REVOKED);

        SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

        assertFalse(loaded.hasPackage());
        assertEquals(SignedRulePackageException.Code.NO_VALID_SIGNED_PACKAGE,
                loaded.errorCode());
        assertEquals(2L, loaded.highestAcceptedRevision());
    }

    @Test
    public void missingOrCorruptStateIsReconstructedFromCurrent() throws Exception {
        TestTrust trust = new TestTrust();
        for (String mode : new String[]{"missing", "corrupt"}) {
            Path directory = directory("state-" + mode);
            SignedRulePackageStore store = store(directory, trust);
            store.install(envelope(2));
            if ("missing".equals(mode)) Files.delete(directory.resolve("state.json"));
            else corrupt(directory.resolve("state.json"));

            SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

            assertTrue(loaded.hasPackage());
            assertEquals(2L, loaded.highestAcceptedRevision());
            assertTrue(Files.readString(directory.resolve("state.json"))
                    .contains("\"highestAcceptedRevision\":2"));
        }
    }

    @Test
    public void syntacticallyValidConflictingStateIsRepairedFromSignedCurrent() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("state-conflicting-digest");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));
        Path stateFile = directory.resolve("state.json");
        String validState = Files.readString(stateFile);
        String conflictingState = validState.replaceFirst(
                "\"payloadSha256\":\"[0-9a-f]{64}\"",
                "\"payloadSha256\":\"" + "0".repeat(64) + "\"");
        assertFalse(validState.equals(conflictingState));
        Files.writeString(stateFile, conflictingState);

        SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

        assertTrue(loaded.hasPackage());
        assertEquals(validState, Files.readString(stateFile));
        assertFalse(store(directory, trust).install(envelope(1)).changed());
    }

    @Test
    public void clearDownloadedPreservesReconstructedHighWater() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("preserve-high-water");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));
        store.install(envelope(2));
        Files.delete(directory.resolve("state.json"));
        store(directory, trust).load();

        store(directory, trust).clearDownloaded();

        assertCode(SignedRulePackageException.Code.REVISION_ROLLBACK,
                () -> store(directory, trust).install(envelope(1)));
    }

    @Test
    public void corruptPreviousDoesNotDisplaceValidCurrent() throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("corrupt-previous");
        SignedRulePackageStore store = store(directory, trust);
        store.install(envelope(1));
        store.install(envelope(2));
        corrupt(directory.resolve("previous.json"));

        SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

        assertTrue(loaded.hasPackage());
        assertEquals(2L, loaded.verifiedPackage().revision());
        assertFalse(loaded.recoveredPrevious());
    }

    @Test
    public void everyAtomicReplacementFailureLeavesOnlyACompleteOldOrNewPackage()
            throws Exception {
        for (SignedRulePackageStore.WriteStage target : SignedRulePackageStore.WriteStage.values()) {
            TestTrust trust = new TestTrust();
            Path directory = directory("fault-" + target.name());
            store(directory, trust).install(envelope(1));
            SignedRulePackageStore failing = new SignedRulePackageStore(
                    directory, trust.verifier(), stage -> {
                if (stage == target) throw new IOException("injected");
            });

            assertCode(SignedRulePackageException.Code.CACHE_IO_FAILED,
                    () -> failing.install(envelope(2)));
            SignedRulePackageStore.LoadResult loaded = store(directory, trust).load();

            assertTrue(loaded.hasPackage());
            long revision = loaded.verifiedPackage().revision();
            assertTrue(revision == 1 || revision == 2);
            assertFalse(Files.exists(directory.resolve("current.tmp")));
            assertFalse(Files.exists(directory.resolve("previous.tmp")));
            assertFalse(Files.exists(directory.resolve("state.tmp")));
        }
    }

    @Test
    public void separateStoreInstancesSerializeOperationsForTheSameDirectory()
            throws Exception {
        TestTrust trust = new TestTrust();
        Path directory = directory("concurrent-instances");
        CountDownLatch firstBeforeCurrent = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondBeforeCurrent = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        SignedRulePackageStore first = new SignedRulePackageStore(
                directory, trust.verifier(), stage -> {
            if (stage == SignedRulePackageStore.WriteStage.BEFORE_CURRENT_REPLACE) {
                firstBeforeCurrent.countDown();
                await(releaseFirst);
            }
        });
        SignedRulePackageStore second = new SignedRulePackageStore(
                directory, trust.verifier(), stage -> {
            if (stage == SignedRulePackageStore.WriteStage.BEFORE_CURRENT_REPLACE) {
                secondBeforeCurrent.countDown();
                await(releaseSecond);
            }
        });
        Thread firstThread = installThread(first, 1, firstFailure);
        Thread secondThread = installThread(second, 2, secondFailure);

        firstThread.start();
        assertTrue(firstBeforeCurrent.await(5, TimeUnit.SECONDS));
        secondThread.start();
        boolean secondEnteredEarly = secondBeforeCurrent.await(500, TimeUnit.MILLISECONDS);
        if (secondEnteredEarly) {
            releaseSecond.countDown();
            secondThread.join(5_000);
            releaseFirst.countDown();
        } else {
            releaseFirst.countDown();
            assertTrue(secondBeforeCurrent.await(5, TimeUnit.SECONDS));
            releaseSecond.countDown();
        }
        firstThread.join(5_000);
        secondThread.join(5_000);

        assertFalse(firstThread.isAlive());
        assertFalse(secondThread.isAlive());
        if (firstFailure.get() != null) throw new AssertionError(firstFailure.get());
        if (secondFailure.get() != null) throw new AssertionError(secondFailure.get());
        assertEquals(2L, store(directory, trust).load().verifiedPackage().revision());
    }

    private Path directory(String name) throws IOException {
        return temporary.newFolder(name).toPath();
    }

    private static SignedRulePackageStore store(Path directory, TestTrust trust) {
        return new SignedRulePackageStore(directory, trust.verifier());
    }

    private static byte[] envelope(long revision) throws Exception {
        return signedEnvelope(revision, CREATED_AT, EXPIRES_AT, EMPTY_PAYLOAD);
    }

    private static byte[] signedEnvelope(long revision, long createdAt,
                                         long expiresAt, String payload) throws Exception {
        AudioFingerprintRuleSet ruleSet = AudioFingerprintRuleCodec.fromJson(payload);
        String canonicalPayload = AudioFingerprintRuleCodec.toJson(ruleSet);
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        byte[] signature = SignedRulePackageTestKeys.signer().sign(
                SignedRulePackageCodec.signingInput(PACKAGE_ID, revision, createdAt,
                        expiresAt, KEY_ID, ALGORITHM, digest));
        String json = "{"
                + "\"packageSchemaVersion\":1,"
                + "\"packageId\":\"" + PACKAGE_ID + "\","
                + "\"revision\":" + revision + ','
                + "\"createdAtEpochMs\":" + createdAt + ','
                + "\"expiresAtEpochMs\":" + expiresAt + ','
                + "\"payloadSha256\":\"" + hex(digest) + "\","
                + "\"payload\":" + canonicalPayload + ','
                + "\"signature\":{"
                + "\"keyId\":\"" + KEY_ID + "\","
                + "\"algorithm\":\"" + ALGORITHM + "\","
                + "\"value\":\"" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature) + "\"}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void corrupt(Path file) throws IOException {
        Files.writeString(file, "{");
    }

    private static Thread installThread(SignedRulePackageStore store, long revision,
                                        AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                store.install(envelope(revision));
            } catch (Throwable e) {
                failure.set(e);
            }
        });
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
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

    private static final class TestTrust {
        final AtomicLong now = new AtomicLong(NOW);
        final AtomicReference<TrustedRuleKeyRegistry.Status> status =
                new AtomicReference<>(TrustedRuleKeyRegistry.Status.ACTIVE);

        SignedRulePackageVerifier verifier() {
            TrustedRuleKeyRegistry registry = keyId -> KEY_ID.equals(keyId)
                    ? Optional.of(new TrustedRuleKeyRegistry.Entry(
                    KEY_ID, ALGORITHM, 0, Long.MAX_VALUE,
                    status.get(), SignedRulePackageTestKeys.verifier()))
                    : Optional.empty();
            return new SignedRulePackageVerifier(PACKAGE_ID, registry, now::get);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
