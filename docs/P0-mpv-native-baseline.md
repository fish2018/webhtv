# P0：MPV native 基线、等价提交与运行验收

## Recovery anchor

- Objective: freeze the current MPV native build graph and establish a reproducible baseline before any P1/P2 native rebuild.
- Acceptance: current lock, patch/override set, both ARM ABI asset sets, ELF namespace, packaging markers, and existing MPV behavior are recorded; no source, lock, patch, or binary change is made in P0.
- Status: assessment complete; P0 has no code delta. P1/P2 implementation remains pending explicit user approval.
- Current workspace: branch `fongmi-sync`, HEAD `3ab9dc97b93a7425ce80135acc13ffb3678b2620`; protected pre-existing dirty paths are `.codex/scripts/task_guard.sh`, `AGENTS.md`, and `docs/agents-md-effective-constraints-review-2026-08-21.md`.
- Next action: user decides whether to approve the separately reversible P1/P2 native rebuild described in the master assessment.

## 1. Decision packet

- User decision: P0 baseline confirmation — recommended; no production code change.
- Lane: `assessment`; task guard: `P0-MPV-NATIVE-BASELINE`.
- Scope: `third_party/mpv-native-lock.json`, `scripts/build_mpv_native.sh`, `scripts/build_mpv_player_jni.sh`, `scripts/verify_mpv_native_assets.sh`, `third_party/patches/` MPV/FFmpeg patches, the AImageReader stable override, current two-ABI MPV assets, and the P0/P1/P2 records in the master assessment. P0 edits only this document and the master assessment index.
- Exclusions: no lock update, source checkout migration, patch deletion, JNI rebuild, native rebuild, APK publication, push, or change to App/runtime behavior.
- Rollback: revert the P0 documentation commit only. The native rollback anchor remains the committed asset/lock state at HEAD; no binary rollback is needed because P0 does not replace assets.

P0 is atomic because it freezes the complete native dependency graph and verifies the existing packaged result without introducing a new behavior. The target upstream trees contain many equivalent commits, but a new hash is not by itself a missing WebHTV capability. P0 therefore records equivalence and runtime obligations; it does not cherry-pick the target trees.

## 2. Frozen local baseline

### 2.1 Source and toolchain identities

| Input | Frozen value | Evidence |
| --- | --- | --- |
| Build framework / JNI reference | `FongMi/mpv-android@99a60ad2141d5ace94453590903c2c6b9a0a2443` | `third_party/mpv-native-lock.json` |
| MPV | `FongMi/mpv@cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, `0.41.0-940-gcca559b41` | lock and embedded asset marker |
| FFmpeg | `FongMi/FFmpeg@04482c8d13ac27b2a9fe93f5d388929eef8af5f4`, `9.0-fongmi` | lock and `libmv*` markers |
| libplacebo | `FongMi/libplacebo@b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`, `7.375.0` | lock and embedded asset marker |
| Android | NDK `29.0.14206865` / r29, API level 24 | lock |
| Build tools | Meson `1.11.2`, Ninja `1.11.1.1` | lock |
| Network stack | curl `8.21.0`, nghttp2 `1.69.0`, MbedTLS `3.6.7`, statically linked | lock, verifier |
| Font stack | Harfbuzz `14.2.1`, fontconfig `2.18.2`, libxml2 `2.15.3`, statically linked | lock, verifier |

Lock SHA-256: `cbfd1f4c925a6250a33f2b0de726adcd5830b6aa5d6c98366dd4db6a8460fc81`.

### 2.2 Build and verification inputs

The current build order is the one documented in [third_party/mpv-native-build.md](../third_party/mpv-native-build.md). The native output must contain exactly ten libraries per ABI:

`libc++_shared.so`, `libmpv.so`, `libmvcodec.so`, `libmvdevice.so`, `libmvfilter.so`, `libmvformat.so`, `libmvutil.so`, `libmwresample.so`, `libmwscale.so`, and `libplayer.so`.

The required local patch order is:

1. `ffmpeg-webhtv-proxy-range.patch`
2. `ffmpeg-mediacodec-port-starvation.patch`
3. `mpv-stream-cb-disc-controls.patch`
4. `mpv-android-dovi-el-surface.patch`
5. `mpv-dovi-profile7-hdr10-base-layer.patch`
6. `mpv-audiotrack-truehd-channel-mask.patch`
7. `mpv-mediacodec-embed-timed-release.patch`
8. `mpv-mediacodec-embed-optional-osd.patch`
9. `mpv-mediacodec-output-timing-diagnostics.patch`
10. `mpv-android-vulkan-conversion-default.patch`
11. `mpv-android-vulkan-smart-backend.patch`
12. `mpv-android-vulkan-legacy-backend.patch`
13. `mpv-aimagereader-stable-flow.patch`
14. `mpv-matroska-segment-end.patch`

The stable AImageReader override is `third_party/mpv-native-overrides/aimagereader-stable/video/out/hwdec/hwdec_aimagereader_vk_stable.c` plus its `.comp` shader. The verifier also enforces the generated 16x8 shader contract and rejects accidental standalone curl, nghttp2, fontconfig, Expat, or libxml2 libraries.

Selected input hashes:

| Path | SHA-256 |
| --- | --- |
| `scripts/build_mpv_native.sh` | `be36c235c57ab57c68d1ab2f5d48584690e7ba66dad18158e6af20c0def2dbe0` |
| `scripts/build_mpv_player_jni.sh` | `10d76c0177ac4cf7df8c4c0fc883f010ee52a9aaba69ce317b80c7a0b9c0fa98` |
| `scripts/verify_mpv_native_assets.sh` | `2bc4a6cd8c49699e65cda0f254e86e1277def053a34a828f5768f67b88962aec` |
| `ffmpeg-mediacodec-port-starvation.patch` | `3d61cb210b6eeb25373073c9c96783629b951178151848c7e96c2a59d191b48b` |
| `ffmpeg-webhtv-proxy-range.patch` | `df20c6fb3f6ee4837fcc12994b0f5fcb287c50ce62bdca96bb32c900e719d684` |
| `mpv-dovi-profile7-hdr10-base-layer.patch` | `e7efa84efc75b08f96040c2cd136b847de1e59fca96aa3716ae97844fdeb0c95` |
| `mpv-aimagereader-stable-flow.patch` | `ff0b78886abe7e5f03b2e46c45e885d888479f7d1f384513fc8a0c6f1d25d653` |
| stable AImageReader `.c` override | `e288333372285fe64572088fd942dcb01122c17da8a63bfee80db6b540fe70b9` |
| stable AImageReader `.comp` override | `c49240fd0a9aae5d1c40adf8501fc7828b7cd76522d31444e6ca92f1468f9a04` |

### 2.3 Packaged asset hashes

The committed assets were checked for both required ARM ABIs. The full ten-file manifest is retained by Git; the following `libmpv.so` and `libplayer.so` hashes provide the primary runtime/JNI identity, while the verifier covers every file and marker:

| ABI | `libmpv.so` | `libplayer.so` |
| --- | --- | --- |
| `arm64-v8a` | `298d9c0159818ad6e3b2968190fddd365b93fd2295d2ceacfe2a1ca9122fa04c` | `aedfcb5bcce929cd08bdd113e2031945efc514f3ec4e21daaa39c5744d941bff` |
| `armeabi-v7a` | `b26654c4f50ad99aae6fba1024be4f5fc0201f97a79642db6c88edc4437f50ab` | `d146b4f7b5aa95f6768c5bae981bd2f01aaa5166e36d28e342b3789e0233b4b4` |

## 3. Upstream equivalence and disposition

The master assessment contains the exhaustive 24/7/27 commit ledger. P0 does not reapply any of these commits. Their current disposition is:

- `mpv-android` 24 commits: 15 exact/semantic infrastructure equivalents are covered by the locked build; example-App, CI, and maintenance-only changes are skipped; Harfbuzz `7cc841e3b5e726c09376fb2e33d5f8e33e42f059` remains a conditional P4 input; shutdown serialization `f4c5d614d5f68d483b2e1889ffad11e513b877d2` remains a separate P4-1 candidate.
- `libplacebo` 7 commits: API 375, HDR validation, checked DV mapping, `disable_storage`, and raw external YUV are covered by the current locked tree; alpha preservation `22ee762e8e0890fc54068beb670310f0edce7263` is a P1 candidate; shader-object helper `c78c4b4a5336473ff169ed2017a4535deed63d50` is dependency-only for P2.
- `mpv` 27 commits: helper schemes, ISO, rewind, curl Range/worker, MMT/TLV, artwork, live status, TTML, playback/GPU/HDR主体 are covered and require runtime confirmation; packed RGB10 `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75`, EBML defaults `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` + `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8`, HLS edition `e7191f2a65d64af266c5c80793e79d2f4b92b789`, and DV7 metadata/codecpar safety `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` are separate P1/P2 candidates. Android BL+EL and other maintenance-only items remain deferred/skipped.

This disposition is evidence-backed by the full tree/diff mapping in master assessment checkpoint 42. It deliberately distinguishes “covered by local code or locked tree” from “same subject” and from “safe to cherry-pick.”

## 4. Best-practice review

The review question was: can WebHTV treat the target MPV trees as a direct replacement, or must it preserve a locked, patched, two-ABI graph and validate behavior before any rebuild?

| Evidence | Revision/access | Claim and decision impact |
| --- | --- | --- |
| WebHTV native build and verifier | `third_party/mpv-native-build.md`, `scripts/verify_mpv_native_assets.sh`; 2026-08-28 | The product consumes committed assets, requires `libmv*`/`libmw*` namespace separation, and has explicit local DV/Vulkan/Range/JNI safeguards. Direct tree replacement is rejected. |
| mpv contribution guide | `https://github.com/mpv-player/mpv/blob/master/DOCS/contribute.md`, current `master`, accessed 2026-08-28 | Upstream asks contributors to test changes, disclose when they did not test, and split independent changes into logical commits. P0 therefore keeps equivalence records and separate P1/P2 rollback units. |
| FFmpeg developer guide | `https://ffmpeg.org/developer.html`, accessed 2026-08-28 | Changes should preserve buildability and carry suitable tests/regression handling. P0 requires deterministic verifier/ELF evidence before native rebuild. |
| FFmpeg FATE | `https://ffmpeg.org/fate.html`, accessed 2026-08-28 | Regression tests can be run as a complete suite or targeted subset. P0 uses targeted native/package checks and reserves format-specific playback for the stage that changes it. |
| Android testing guide | `https://developer.android.com/studio/test`, accessed 2026-08-28 | Android behavior requires choosing local, instrumented, emulator, or device tests according to the behavior. P0 records device/runtime cases as acceptance obligations rather than claiming asset-marker checks prove playback. |
| SLSA v1.0 | `https://slsa.dev/spec/v1.0/`, accessed 2026-08-28 | Build provenance must connect source inputs and produced artifacts. P0 records lock, toolchain, patch order, hashes, and artifact identities before any rebuild. |
| GitHub release management | `https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository`, accessed 2026-08-28 | Binary assets should be attached to a deliberate tagged release and immutable-release workflow. P0 does not publish or push assets; candidate publication remains a later approved stage. |

Applicable evidence classes are all covered: exact upstream commits and local diffs are in the master ledger; official project/platform/build-provenance guidance is listed above; maintainer/contributor expectations are represented by the mpv/FFmpeg contribution documents; mature related tests are represented by FFmpeg FATE and the Android testing model. No additional external research could change the P0 decision because P0 changes no runtime or dependency input.

## 5. Alternatives and recommendation

| Alternative | Result |
| --- | --- |
| No change, with no baseline record | Rejected: it leaves the current asset provenance and equivalent-commit boundary implicit, making a later native rebuild hard to attribute or roll back. |
| Directly point the lock at the latest upstream trees | Rejected: it would mix rebase-equivalent commits with real deltas and risks dropping WebHTV patches, ELF renaming, JNI ownership, or Surface/fence behavior. |
| P0 baseline freeze, then narrow P1/P2 adaptations | Recommended: preserves current behavior, makes each native topic independently reversible, and keeps the Exo→MPV order. |

### P0 acceptance criteria

1. `bash scripts/verify_mpv_native_assets.sh --require-elf` passes for `arm64-v8a` and `armeabi-v7a`.
2. The verifier uses NDK r29 `llvm-readelf`, confirms all required SONAMEs and `DT_NEEDED`, and rejects unrenamed FFmpeg or accidental dynamic network/font dependencies.
3. Lock, toolchain, patch/override set, and primary asset hashes are recorded above.
4. No P0 source, lock, patch, JNI, APK, or native asset changes are present.
5. P1/P2 remain separate user-approved implementation decisions; P2-3/P2-4, P3, P4, and C2 are not silently enabled.

## 6. Validation result

Command run once on 2026-08-28:

```text
bash scripts/verify_mpv_native_assets.sh --require-elf
```

Result: PASS. The script reported the stable Vulkan shader contract (`16x8`, CPU-precomputed UV transform), verified both ARM ABI asset sets, used `/Users/macbookpro/Downloads/bizhi/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf`, and confirmed that all committed MPV native assets match lock versions and packaging rules.

The worktree remained limited to the three protected pre-existing dirty paths plus the two documentation paths in this task. No build, network fetch, native command, or binary replacement was required for P0.

## 7. Checkpoint 47 — 2026-08-28

- Completed: P0 baseline packet, source/patch/asset identity capture, upstream equivalence disposition, best-practice review, and ELF/package verification.
- Source identities: lock MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`, libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`, mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`; target commit ledgers remain in master checkpoint 42.
- Workspace: branch `fongmi-sync`, HEAD `3ab9dc97b93a7425ce80135acc13ffb3678b2620`; protected dirty paths unchanged.
- Files changed: this task document and the P0 row/status in the master assessment; no locks, patches, scripts, assets, APKs, or JNI outputs.
- Validation: `bash scripts/verify_mpv_native_assets.sh --require-elf` passed for both ABIs with NDK r29 `llvm-readelf`.
- Rollback anchor: HEAD `3ab9dc97b93a7425ce80135acc13ffb3678b2620` and its existing recovery tags; documentation commit can be reverted independently.
- Unresolved: no device playback manifest was available in this assessment; runtime playback remains a prerequisite for any later P1/P2 candidate, not a P0 failure.
- Next action: request explicit approval for P1/P2 native implementation, then open a new `upstream` task-guard session with an approved source/lock/artifact scope.
