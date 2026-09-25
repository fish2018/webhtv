# Signed Ad Audio Rule Packages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add API-24-compatible Ed25519 verification, strict signed envelopes, revision anti-rollback, two-slot recovery, and a stable rule-source abstraction without changing current local-rule playback behavior.

**Architecture:** Keep SDK v2 as the canonical payload. A strict package codec creates a domain-separated binary signing input, a Tink-backed verifier authenticates it against a pinned registry, and a disk store installs only verified monotonic revisions into current/previous slots. Playback consumes a top-level immutable `AdAudioRuleSnapshot`; network updating and production key provisioning remain outside this plan.

**Tech Stack:** Java 17, Android API 24+, Gson 2.14, Google Tink Android 1.23.0, JUnit 4, Gradle version catalog.

---

## File Structure

**Create:**

- `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuleSource.java` - stable runtime source contract.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuleSnapshot.java` - immutable source-neutral rule snapshot.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageException.java` - fixed machine-readable failures.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageCodec.java` - strict envelope parser and signing-input builder.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/TrustedRuleKeyRegistry.java` - pinned key lookup and lifecycle policy.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageVerifier.java` - payload digest, time, key, and Tink verification.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageStore.java` - revision high-water and current/previous persistence.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageSource.java` - verified-cache runtime adapter.
- `app/src/main/java/com/fongmi/android/tv/ad/audio/PrioritizedAdAudioRuleSource.java` - local-first source policy.
- Focused JUnit tests with matching class names under `app/src/test/java/com/fongmi/android/tv/ad/audio/`.

**Modify:**

- `gradle/libs.versions.toml` and `app/build.gradle` - pin and add Tink Android.
- `AdAudioRuleStore.java`, `AdAudioRuntimeController.java`, `PlayerManager.java`, UI call sites, and existing tests - migrate nested snapshot/source types without changing behavior.
- `AudioFingerprintRuleCodec.java` - only if signed-envelope tests expose a payload boundary that cannot be enforced externally.
- `docs/audio-fingerprint-phase0-implementation.md` and the signed-package design status - record delivered scope and remaining production-key/updater work.

## Task 1: Stable Rule Source Contract

**Files:**

- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuleSource.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuleSnapshot.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuleStore.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioRuntimeController.java`
- Modify: all Java references to `AdAudioRuleStore.Snapshot` and `AdAudioRuntimeController.RuleSource`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/AdAudioRuleSourceContractTest.java`

- [x] **Step 1: Write the failing source contract test**

```java
@Test
public void localStoreImplementsStableSourceContract() throws Exception {
    Path directory = temporary.newFolder("rules").toPath();
    AdAudioRuleSource source = new AdAudioRuleStore(directory);
    AdAudioRuleSnapshot snapshot = source.load();
    assertEquals("local", snapshot.sourceId());
    assertFalse(snapshot.hasRules());
    assertThrows(UnsupportedOperationException.class,
            () -> snapshot.warnings().add("mutable"));
}
```

- [x] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
rtk .\gradlew.bat -Dandroid.overridePathCheck=true :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ad.audio.AdAudioRuleSourceContractTest" --no-daemon --no-build-cache --console=plain
```

Expected: compilation fails because `AdAudioRuleSource` and `AdAudioRuleSnapshot` do not exist.

- [x] **Step 3: Add the stable types and migrate callers**

```java
public interface AdAudioRuleSource {
    AdAudioRuleSnapshot load();
}

public record AdAudioRuleSnapshot(String sourceId, String version,
        AudioFingerprintRuleSet ruleSet, List<String> warnings, String lastError) {
    public AdAudioRuleSnapshot {
        if (sourceId == null || version == null || ruleSet == null
                || warnings == null || lastError == null) {
            throw new IllegalArgumentException("snapshot fields are required");
        }
        warnings = List.copyOf(warnings);
    }

    public boolean hasRules() { return !ruleSet.rules().isEmpty(); }
    public boolean hasError() { return !lastError.isEmpty(); }
}
```

Make `AdAudioRuleStore implements AdAudioRuleSource`, remove its nested `Snapshot`, and make `AdAudioRuntimeController` depend on `AdAudioRuleSource`.

- [x] **Step 4: Run all existing ad-audio tests and confirm GREEN**

Run the focused suite with `--tests "com.fongmi.android.tv.ad.audio.*"`.

Expected: all existing and new audio-ad tests pass with no behavioral changes.

- [x] **Step 5: Commit the contract migration**

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio app/src/test/java/com/fongmi/android/tv/ad/audio app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java app/src/leanback app/src/mobile
rtk git commit -m "refactor: decouple ad audio rule sources"
```

## Task 2: Tink Dependency and RAW Ed25519 Test Fixture

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle`
- Create: `app/src/test/java/com/fongmi/android/tv/ad/audio/TinkEd25519CompatibilityTest.java`
- Create: `app/src/test/java/com/fongmi/android/tv/ad/audio/SignedRulePackageTestKeys.java`

- [x] **Step 1: Write a test that loads a TEST-ONLY RAW Tink keyset**

The fixture stores a fixed Tink JSON private keyset with `outputPrefixType: "RAW"`. The test obtains `PublicKeySign` and `PublicKeyVerify`, signs `"webhtv-ed25519-test"`, asserts a 64-byte signature, and verifies it.

```java
@Test
public void rawEd25519SignatureIsPortableLength() throws Exception {
    byte[] message = "webhtv-ed25519-test".getBytes(StandardCharsets.US_ASCII);
    byte[] signature = SignedRulePackageTestKeys.signer().sign(message);
    assertEquals(64, signature.length);
    SignedRulePackageTestKeys.verifier().verify(signature, message);
}
```

- [x] **Step 2: Run and confirm RED**

Expected: compilation fails because Tink classes and the catalog dependency are absent.

- [x] **Step 3: Add the pinned dependency**

Add:

```toml
tink = "1.23.0"
tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
```

and:

```groovy
implementation libs.tink.android
```

Load the test key with `TinkJsonProtoKeysetFormat`, obtain primitives using `RegistryConfiguration.get()`, and keep the fixture under test sources only.

- [x] **Step 4: Run and confirm GREEN**

Expected: signature length is exactly 64 and verification succeeds.

- [x] **Step 5: Commit**

```powershell
rtk git add gradle/libs.versions.toml app/build.gradle app/src/test/java/com/fongmi/android/tv/ad/audio
rtk git commit -m "build: add Tink Ed25519 verification"
```

## Task 3: Strict Signed Envelope Codec

**Files:**

- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageException.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageCodec.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/SignedRulePackageCodecTest.java`

- [x] **Step 1: Write RED tests for valid parsing and exact signing input**

Tests construct a schema-1 envelope around the existing minimal SDK-v2 fixture and assert:

```java
SignedRulePackageCodec.Parsed parsed = SignedRulePackageCodec.parse(jsonBytes);
assertEquals("official.ad-audio", parsed.packageId());
assertEquals(42L, parsed.revision());
assertEquals("release-1", parsed.keyId());
assertArrayEquals(expectedSigningInput, parsed.signingInput());
assertEquals(expectedCanonicalPayload, parsed.canonicalPayload());
```

Add one test per boundary: unknown field, duplicate field at each nesting level, trailing JSON, decimal revision, illegal ID, uppercase digest, padded base64, wrong signature length, deep JSON, token budget, and raw size over `MAX_PACKAGE_BYTES`.

- [x] **Step 2: Run and confirm RED**

Expected: compilation fails because codec and exception types do not exist.

- [x] **Step 3: Implement the minimal strict codec**

Expose:

```java
static final int PACKAGE_SCHEMA_VERSION = 1;
static final int MAX_PACKAGE_BYTES = AdAudioRuleStore.MAX_IMPORT_BYTES + 64 * 1024;

static Parsed parse(byte[] bytes);
static byte[] signingInput(String packageId, long revision, long createdAt,
        long expiresAt, String keyId, String algorithm, byte[] payloadDigest);
```

Use a streaming preflight that tracks object member names per depth, token count, and maximum depth before building a Gson tree. Parse payload with `AudioFingerprintRuleCodec`, re-encode it canonically, and build the exact big-endian domain-separated input from the design.

`SignedRulePackageException` contains only a `Code` enum and stable generic message; it never embeds untrusted text.

- [x] **Step 4: Run codec and existing rule-codec tests and confirm GREEN**

Expected: strict package tests and `AudioFingerprintRuleCodecTest` pass.

- [x] **Step 5: Commit**

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio app/src/test/java/com/fongmi/android/tv/ad/audio
rtk git commit -m "feat: add strict signed rule envelope codec"
```

## Task 4: Trusted Key Registry and Package Verification

**Files:**

- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/TrustedRuleKeyRegistry.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageVerifier.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/SignedRulePackageVerifierTest.java`

- [x] **Step 1: Write RED tests for verification policy**

Create an in-memory registry entry using the test RAW verifier. Test valid install/cache verification plus tampering of every signed field, unknown key, unsupported algorithm, ACTIVE/RETIRED/REVOKED behavior, key validity bounds, package not-yet-valid, expiry, and 90-day maximum lifetime.

```java
SignedRulePackageVerifier verifier = new SignedRulePackageVerifier(
        "official.ad-audio", registry, () -> NOW);
VerifiedRulePackage verified = verifier.verify(signedEnvelope, VerificationMode.INSTALL);
assertEquals(42L, verified.revision());
assertEquals("official.ad-audio", verified.packageId());
```

- [x] **Step 2: Run and confirm RED**

Expected: compilation fails because registry and verifier do not exist.

- [x] **Step 3: Implement registry and verifier**

Registry contract:

```java
public interface TrustedRuleKeyRegistry {
    Optional<Entry> find(String keyId);
    enum Status { ACTIVE, RETIRED, REVOKED }
    record Entry(String keyId, String algorithm, long notBeforeEpochMs,
            long notAfterEpochMs, Status status, PublicKeyVerify verifier) { }
}
```

Verifier order is raw limit -> strict parse -> canonical digest -> constant-time digest comparison -> package/time/key policy -> Tink verify. Convert all Tink `GeneralSecurityException` failures to `SIGNATURE_INVALID` without retaining exception text.

- [x] **Step 4: Run verifier and codec tests and confirm GREEN**

- [x] **Step 5: Commit**

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio app/src/test/java/com/fongmi/android/tv/ad/audio
rtk git commit -m "feat: verify trusted audio rule packages"
```

## Task 5: Revision High-Water and Two-Slot Store

**Files:**

- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageStore.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/SignedRulePackageStoreTest.java`

- [x] **Step 1: Write RED persistence tests**

Cover first install, higher revision rotation, lower revision rejection, same revision/same digest idempotence, same revision/different digest conflict, reinstall after downloaded files are cleared, current corruption recovery, expired/revoked previous rejection, state reconstruction, and high-water preservation after recovery.

Add a package-private `WriteFault` callback with named stages so tests can throw `IOException` before each atomic replacement and then construct a new store to assert it loads only a complete verified slot.

- [x] **Step 2: Run and confirm RED**

Expected: compilation fails because `SignedRulePackageStore` does not exist.

- [x] **Step 3: Implement store state and atomic operations**

Public surface:

```java
public final class SignedRulePackageStore {
    public InstallResult install(byte[] signedPackage);
    public LoadResult load();
    public void clearDownloaded();
}
```

Keep `current.json`, `previous.json`, and `state.json` in the configured package directory. Write every file through a same-directory `.tmp`, sync, and atomic replace. Revalidate raw signed envelopes on every load. Preserve state during `clearDownloaded()` and never lower the high-water revision.

- [x] **Step 4: Run store, verifier, and codec tests and confirm GREEN**

- [x] **Step 5: Commit**

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio app/src/test/java/com/fongmi/android/tv/ad/audio
rtk git commit -m "feat: persist verified audio rule packages"
```

## Task 6: Signed and Prioritized Runtime Sources

**Files:**

- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/SignedRulePackageSource.java`
- Create: `app/src/main/java/com/fongmi/android/tv/ad/audio/PrioritizedAdAudioRuleSource.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/SignedRulePackageSourceTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ad/audio/PrioritizedAdAudioRuleSourceTest.java`

- [x] **Step 1: Write RED source mapping and precedence tests**

Assert signed snapshots use `signed:<packageId>` and `<revision>:<digest16>`, recovered previous adds `CACHE_RECOVERED`, local valid rules win, empty local falls back to signed, invalid local falls back with `LOCAL_SOURCE_INVALID`, and both unavailable return an empty snapshot with the most specific error.

- [x] **Step 2: Run and confirm RED**

- [x] **Step 3: Implement the two source adapters**

Do not merge `AudioFingerprintRuleSet` instances. Do not add network calls. Do not wire an empty production key registry into `PlayerManager`; keep its current local source until a separately reviewed production public key is provided.

- [x] **Step 4: Run all ad-audio tests and confirm GREEN**

- [x] **Step 5: Commit**

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ad/audio app/src/test/java/com/fongmi/android/tv/ad/audio
rtk git commit -m "feat: add signed audio rule sources"
```

## Task 7: Documentation and Full Verification

**Files:**

- Modify: `docs/superpowers/specs/2026-08-17-signed-ad-audio-rule-packages-design.md`
- Modify: `docs/audio-fingerprint-phase0-implementation.md`
- Modify: this plan to mark executed steps.

- [x] **Step 1: Update delivered-state documentation**

Mark signed verification/cache/source infrastructure complete. Keep production key provisioning, fixed HTTPS updater, author quality pipeline, and H5 oracle explicitly pending.

- [x] **Step 2: Run focused security and persistence tests**

Run all `SignedRulePackage*`, `TrustedRuleKeyRegistry*`, `PrioritizedAdAudioRuleSource*`, and existing `AudioFingerprintRuleCodecTest` tests.

- [x] **Step 3: Run the full Leanback unit test suite**

```powershell
rtk .\gradlew.bat -Dandroid.overridePathCheck=true :app:testLeanbackArm64_v8aDebugUnitTest --no-daemon --no-build-cache --console=plain
```

Expected: all suites pass with zero failures/errors.

- [x] **Step 4: Compile Mobile**

```powershell
rtk .\gradlew.bat -Dandroid.overridePathCheck=true :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon --no-build-cache --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Run repository hygiene and secret checks**

```powershell
rtk git diff --check
rtk git status --short
```

Inspect staged content for private-key or secret material. Only the clearly marked TEST-ONLY fixture may contain private test key bytes; no production key or endpoint is allowed.

- [x] **Step 6: Commit documentation**

```powershell
rtk git add docs
rtk git commit -m "docs: record signed audio package infrastructure"
```

- [x] **Step 7: Request correctness and security review**

Review the complete diff against the signed-package design, with findings prioritized around canonicalization, signature coverage, downgrade/recovery semantics, crash consistency, resource bounds, and accidental private-key inclusion. Fix every Critical/Important finding with a new failing test before final merge.
