# P1：MPV 格式与 shader correctness

## Recovery anchor

- Objective: implement the approved first MPV native rebuild with four narrow correctness fixes while preserving current playback, ABI, and performance contracts.
- Acceptance: the four source patches apply after all existing WebHTV patches; both ARM ABIs rebuild from the locked graph; native ELF/package checks and focused playback/rendering checks pass; exact source, artifact, commit, tag, and rollback records are written here.
- Status: implementation committed and tagged; build/package/device smoke and user multi-disc regression checks passed, while four P1-specific fixtures remain optional follow-up evidence.
- Task ID/lane: `P1-MPV-FORMAT-SHADER-CORRECTNESS` / `upstream`.
- Workspace: branch `fongmi-sync`, HEAD `42f7f54cd0da748b0f2fbad5faab3869fe19f50a`.
- Baseline recovery tag: `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/baseline-20260828-1246`.
- Implementation guard: active `upstream` session with declared build script, lock, four patch, two ABI asset, and this document paths.
- Protected pre-existing dirty paths: `.codex/scripts/task_guard.sh`, `AGENTS.md`, `docs/agents-md-effective-constraints-review-2026-08-21.md`.
- Next action: treat P1 as complete; exercise the four dedicated RGB10/EBML/HLS/alpha fixtures only when matching samples are available, or continue with the next approved upstream task.

## 1. Decision packet

### Question

Should WebHTV's first controlled MPV native rebuild add packed 10-bit RGB format identity, Matroska EBML default handling, HLS program-level edition selection, and libplacebo alpha preservation, while retaining the existing FFmpeg namespace, Vulkan/AImageReader, Dolby Vision, Matroska seek, AudioTrack, OSD, and two-ABI contracts?

Current hypothesis: these are narrow correctness fixes with visible input or rendering value and no App/JNI API change. Counter-hypothesis: one or more changes may be incompatible with WebHTV's generated Matroska descriptors, program metadata, packed-format interpretation, or transparent OSD/shader behavior and therefore should be adapted or deferred.

### Scope and authority

- Assessment-only changes: this document and the P1 row/recovery text in `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`.
- Candidate repositories: `FongMi/mpv` and `FongMi/libplacebo`; source range and full commit identities are listed below.
- Implementation scope, if approved later: `third_party/mpv-native-lock.json`, the corresponding MPV/libplacebo source/patch inputs, both ARM native asset sets, and only the tests/build records required by the approved substage. `libplayer.so`, App Java/Kotlin APIs, and Exo AARs are excluded.
- No lock update, source checkout migration, native rebuild, APK publication, push, or local production patch is authorized by this assessment.

## 2. Complete candidate ledger

| Substage | Full commit | Disposition | Decision boundary |
| --- | --- | --- | --- |
| P1-1 packed RGB10 RA | `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75` | candidate; recommend narrow merge | `video/out/placebo/ra_pl.c`; add `X2BGR10`/`X2RGB10` special format identity only |
| P1-2 EBML generator prerequisite | `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` | dependency-only within P1-2; do not release alone | `TOOLS/matroska.py`; required so generated descriptors can carry defaults |
| P1-2 EBML defaults | `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8` | candidate; recommend narrow merge with `52bb...` | `TOOLS/matroska.py`, `demux/ebml.c`, `demux/ebml.h`; zero-length values use EBML/RFC/context defaults |
| P1-3 HLS edition | `e7191f2a65d64af266c5c80793e79d2f4b92b789` | candidate; recommend merge with metadata fallback gate | `demux/demux_lavf.c`; select by program `variant_bitrate` before any compatibility fallback |
| P1-4 libplacebo alpha | `22ee762e8e0890fc54068beb670310f0edce7263` | candidate; recommend搭载 | `pl_shader_extract_features()` preserves extracted alpha instead of forcing `1.0` |

The master assessment records the parent/tree and patch-id evidence for all five commits (checkpoint 34 and checkpoint 42.4). The two EBML commits are one implementation unit; the generator prerequisite has no independent runtime value.

## 3. Current WebHTV baseline and contracts

- Native graph is pinned by `third_party/mpv-native-lock.json`: MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`, libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`, mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`, NDK r29/API 24, and both `arm64-v8a`/`armeabi-v7a` assets.
- MPV FFmpeg libraries use the `libmv*`/`libmw*` SONAME and `DT_NEEDED` namespace; Exo keeps separate `libav*`/`libsw*` assets. Any approved P1 rebuild must rebuild the coherent graph and preserve this separation.
- Existing local behavior that must remain: `mpv-matroska-segment-end.patch` for seek metadata, `mpv-dovi-profile7-hdr10-base-layer.patch` and DV BlockAdditional handling, `mpv-android-vulkan-{conversion-default,smart-backend,legacy-backend}.patch`, the stable AImageReader override/fence ownership, optional OSD and timestamped MediaCodec release, TrueHD 7.1 workaround, FFmpeg starvation/Range patches, and the ten-library per-ABI packaging contract.
- P0 verification has already passed once at HEAD: `bash scripts/verify_mpv_native_assets.sh --require-elf` checked both ABIs, NDK r29 `llvm-readelf`, shader contract, markers, SONAME/`DT_NEEDED`, and package contents. That result is the baseline, not proof that P1 behavior is implemented.
- App already exposes `hls-bitrate`/automatic bitrate policy and consumes Matroska track/chapter/metadata and subtitle/OSD output. P1 does not change these APIs; it changes native parsing/selection/format identity beneath them.

## 4. Substage decisions

### P1-1: packed RGB10 RA identity

The locked libplacebo API already describes Vulkan `rgb10a2`/`bgr10a2`; the gap is mpv's libplacebo RA wrapper not recognizing packed `IMGFMT_X2BGR10` and `IMGFMT_X2RGB10` as special formats. The proposed hunk adds one-plane RGB descriptors with explicit channel order.

Recommendation: merge as a narrow mpv change, only in the first controlled native rebuild. It should be exercised through Vulkan direct, stable, generic/conversion, and automatic fallback, then compared with OpenGL `gpu-next`. The key failure mode is red/blue reversal or HDR gradient distortion on little-endian packed layouts. No local Vulkan backend or shader ownership patch may be removed.

### P1-2: EBML zero-length/default semantics

The current parser rejects or empties some legal zero-length EBML elements. The upstream pair adds generated `context_default` descriptors and parser handling so explicit zero length, missing element, and explicit non-zero value remain distinguishable. Context-derived `DisplayWidth`/`DisplayHeight` and `OutputSamplingFrequency` must continue to use existing demux fallback rather than being written as zero.

Recommendation: merge both commits as one unit. The fixture must cover header defaults, `TimecodeScale`, track flags/language/lacing, display dimensions, sampling/channels, colour, `BlockAddID`/DV, content encoding, chapters, and tags, each in missing/zero-length/explicit forms. Keep the local segment-end seek and DV BlockAdditional paths intact. A failure rolls back only P1-2.

### P1-3: HLS program-level edition selection

The App already sets `hls-bitrate`; the current native selection can read a per-stream bitrate that is absent or misleading when variants share audio/subtitle groups. The candidate reads FFmpeg's program-level `variant_bitrate` metadata and ignores empty programs.

Recommendation: merge after recording actual FFmpeg 9.0-fongmi behavior when program metadata is absent. The default rule is program metadata first; only if the complete program set lacks usable metadata may a WebHTV-adapted stream fallback be added. Preserve explicit edition selection and `flatten-editions`. Validate threshold boundaries, shared groups, audio-only and empty programs, invalid/missing metadata, and dynamic App reload from 15 Mbps to 8 Mbps.

### P1-4: libplacebo alpha preservation

The candidate changes `pl_shader_extract_features()` so alpha extracted from the shader feature set is not overwritten with `1.0`. This is a small shader correctness change relevant to transparent subtitle/OSD overlays and screenshots; it does not change libplacebo API level.

Recommendation:搭载 with P1, but keep its source hunk and test evidence independently reversible. Validate transparent OSD/subtitle compositing, alpha overlays, HDR shader paths, screenshots, and both OpenGL and Vulkan output. A regression must not be “fixed” by disabling alpha globally.

## 5. Best-practice evidence

The mandatory research was completed in the master assessment checkpoints 5, 34, 42, and the upstream-integration-governor evidence record. It was not repeated because source heads and local contracts are unchanged.

| Claim | Source/revision | Grade | WebHTV applicability and impact |
| --- | --- | --- | --- |
| The five commits are real endpoint-tree deltas, not merely rebase duplicates | `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`, checkpoints 5.2, 34, 42; full hashes above; accessed 2026-08-28 | A | Supports a narrow P1 stage while leaving covered API-375 and Android GPU behavior untouched |
| MPV contribution changes should be split, tested, and accompanied by disclosed test scope | `https://github.com/mpv-player/mpv/blob/master/DOCS/contribute.md`, current `master`, accessed 2026-08-28 | B | Requires four independently attributable substages and no claim beyond the selected playback matrix |
| FFmpeg/container changes need deterministic regression coverage | `https://ffmpeg.org/developer.html` and `https://ffmpeg.org/fate.html`, accessed 2026-08-28 | A/B | Drives synthetic EBML fixtures and explicit HLS/Matroska regression cases rather than a build-only gate |
| Android behavior requires tests matched to the affected behavior and device path | `https://developer.android.com/studio/test`, accessed 2026-08-28 | A | Packed RGB/alpha and native selection require APK/device evidence; verifier output alone is insufficient |
| Native artifacts need reconstructable source/toolchain/provenance and ABI identity | `https://slsa.dev/spec/v1.0/` and current `third_party/mpv-native-build.md`, accessed 2026-08-28 | A | Any implementation must rebuild both ABIs from the declared lock, preserve SONAME/DT_NEEDED, and retain artifact hashes |

Inapplicable categories: no new App API, JNI contract, or security boundary is proposed in P1, so no separate API migration or threat-model stage is needed. Device-specific rendering and HLS metadata uncertainty remain implementation gates, not assumptions.

## 6. Alternatives

| Alternative | Result |
| --- | --- |
| No change | Keeps the known-good native assets, but leaves legal EBML zero-length inputs, packed RGB10 identity, program-level HLS selection, and alpha extraction gaps unresolved. Acceptable only if no P1 rebuild is approved. |
| Adopt the upstream commits/tree wholesale | Rejected. It risks dropping WebHTV's Vulkan/AImageReader ownership, DV7 packet/Surface safeguards, Matroska segment seek, FFmpeg namespace, and local AudioTrack/OSD behavior. |
| Narrow WebHTV-adapted P1 | Recommended. Carry the five commits as four independently testable source units, add the HLS metadata fallback only if evidence requires it, and preserve the current native graph and rollback boundaries. |

## 7. Acceptance and rollback

Before implementation approval, acceptance criteria are:

1. User explicitly approves the P1 source/lock/artifact stage; no code is changed before that approval.
2. P1 rebuild uses one declared FFmpeg revision for MPV, reapplies all MPV-specific patches, and rebuilds both ARM ABIs as a coherent graph. `libplayer.so` remains unchanged unless a later API diff proves otherwise.
3. `scripts/verify_mpv_native_assets.sh --require-elf` passes once for the candidate assets, including version markers, shader contract, SONAME/`DT_NEEDED`, static dependency rules, and package manifest.
4. P1-1 passes packed RGB10 red/green/blue order, 10-bit gradient, HDR/LUT, Vulkan direct/stable/generic/auto, and OpenGL comparison checks.
5. P1-2 passes missing/zero-length/explicit EBML fixtures, DAR/timebase/audio/chapter/tag behavior, DV `BlockAddID`, segment-end seek, and subtitle/track selection checks.
6. P1-3 passes shared audio/subtitle groups, audio-only/empty programs, metadata missing/invalid cases, bitrate threshold boundaries, explicit edition, `flatten-editions`, and dynamic reload checks.
7. P1-4 passes transparent subtitle/OSD/overlay and HDR shader screenshot checks on both rendering families.
8. Lock, patch order, source identities, artifact hashes, device/API/GPU/settings, logs, and per-substage results are recorded before commit/tag.

Rollback is one native candidate rollback to the P0 asset/lock state. Within the candidate source tree, remove only the failing P1 hunk/unit: P1-1 RA mapping, P1-2 both EBML commits, P1-3 edition selection/fallback, or P1-4 alpha extraction. Never roll back by deleting the shared FFmpeg namespace or local Vulkan/DV/Matroska safety patches.

## 8. Approval gate and next action

This document recommends approval of P1-1, P1-2, P1-3, and P1-4 for one controlled MPV native rebuild, with separate source commits, tests, results, and rollback notes. It does not authorize implementation. P2 generic UV/DV7, P3 AudioTrack mask, P4 JNI shutdown, Android BL+EL, C2 DV7 conversion, and maintenance-only items remain separate decisions.

Next action: wait for explicit user approval; if approved, start a new `upstream` task-guard session declaring the exact lock, patch, source, two-ABI asset, and verification paths.

## 9. Assessment validation

Validated on 2026-08-28 at HEAD `98f872eed700213ce03345d7d20d794c8ec4123a`:

- `git diff --check` passed.
- `bash .codex/scripts/task_guard.sh check` passed for `P1-MPV-FORMAT-SHADER-CORRECTNESS`; only the two declared documentation paths are task-owned, and the three pre-existing dirty paths remain protected.
- `bash .codex/skills/upstream-integration-governor/scripts/verify_upstream_checkpoint.sh docs/upstream-player-dependency-merge-assessment-2026-08-20.md` passed with zero errors/warnings, including 436 unique full commit IDs and the latest recovery anchor.
- Static checks confirmed the unique P1 document exists, the master index links it, and all five P1 source commits are recorded with full 40-character identities.

## 10. Implementation progress

### 2026-08-28 source integration checkpoint

- User approval: approved implementation with the explicit condition that current functionality and performance must not regress.
- Local baseline: `42f7f54cd0da748b0f2fbad5faab3869fe19f50a`; baseline tag `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/baseline-20260828-1246`.
- Source strategy: retain the existing MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` and libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` locks; apply only the approved upstream deltas as four local patches. No MPV/libplacebo branch-head upgrade, FFmpeg lock change, JNI change, or App API change is included.
- Patch provenance and SHA-256:
  - `mpv-p1-packed-rgb10.patch`: `FongMi/mpv@7b8915bc1d04c7e1b61184e00c7fbfaab1911e75`; `47aae2b7dda83a0c9216a528721e3ae85355d4304f27511d0bd738adcb8e8d5d`.
  - `mpv-p1-ebml-defaults.patch`: inseparable delta from `FongMi/mpv@52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` and `FongMi/mpv@e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8`; `4bbc22014242eb182ba3a479d17250a39a48621ed3345a22531584d84d8301ff`.
  - `mpv-p1-hls-edition.patch`: `FongMi/mpv@e7191f2a65d64af266c5c80793e79d2f4b92b789`; `a0bfaf35e30ffb9eb187943ba4a73c125cbf9293f596b68f87d5e560ee120425`.
  - `libplacebo-p1-alpha.patch`: `FongMi/libplacebo@22ee762e8e0890fc54068beb670310f0edce7263`; `1a077fe46235df90347348fabac2e56c81361b2ddecb962135dacbeea8d75ba8`.
- Build-script compatibility correction: the pre-existing `mpv-mediacodec-embed-timed-release.patch` has stale hunk counts and a clean rebuild failed at line 291 before reaching P1. `scripts/build_mpv_native.sh` now applies that unchanged patch with `git apply --recount`, matching the existing DV7 patch handling. This changes patch parsing only and preserves the timed-release source delta byte-for-byte.
- Completed evidence: `bash -n scripts/build_mpv_native.sh` passed; all 15 MPV patches passed a strict ordered apply test from the locked MPV tree; `bash scripts/build_mpv_native.sh --abi all --prepare-only` passed from the repository cache and applied the locked sources and all patches in the production build order.
- Unverified worktree edits: build script, four new patch files, and this document. No lock, App/JNI, or committed native asset has changed yet.
- Remaining risks: compilation, ELF/package identity, both ABI artifacts, APK packaging, and device-visible RGB10/EBML/HLS/alpha behavior are not yet verified.
- Next action: run one clean `bash scripts/build_mpv_native.sh --abi all --install`, then run the native asset verifier before any commit.

### 2026-08-28 two-ABI native candidate checkpoint

- Clean rebuild: `bash scripts/build_mpv_native.sh --abi all --install` compiled and linked the locked graph for `arm64-v8a` and `armeabi-v7a`, including the generated EBML descriptors, HLS demux path, packed RGB10 RA mapping, libplacebo shader path, and all retained Android Vulkan/AImageReader/MediaCodec/AudioTrack sources.
- Install-end anomaly: after both candidate sets had been copied, the command exited `1` because the final duplicate `verify_directory` call reported that armeabi-v7a `libmpv.so` did not depend on `libmvcodec.so`. The staged output and installed assets are byte-identical, current `llvm-readelf -d` shows the required `libmvcodec.so` dependency, and the same exact match could not be reproduced. No verifier relaxation or speculative script change was made.
- Decisive native verification: one independent `bash scripts/verify_mpv_native_assets.sh --require-elf` pass succeeded for both ABIs. It verified the stable Vulkan shader contract, locked version markers, ELF class/machine, SONAME/`DT_NEEDED` namespace, required local patch markers, and packaged asset rules with NDK r29 `llvm-readelf`.
- Candidate `libmpv.so` SHA-256: arm64-v8a `69e9a8d10560a41107680ca4de737996885f2f37fa353fd5ede30334866eeb7b`; armeabi-v7a `ec2cbc58616bb383eb2f80d3fde883da02036b00062287a6f956ebda1aeb5ce3`.
- Preserved contracts: `third_party/mpv-native-lock.json` has no diff; arm64-v8a `libplayer.so` remains `aedfcb5bcce929cd08bdd113e2031945efc514f3ec4e21daaa39c5744d941bff`; armeabi-v7a `libplayer.so` remains `d146b4f7b5aa95f6768c5bae981bd2f01aaa5166e36d28e342b3789e0233b4b4`.
- Guard state: `bash .codex/scripts/task_guard.sh check` passed after installation; protected pre-existing dirty paths remain outside task scope.
- Remaining risks: App packaging and device-visible packed RGB10 colour order, EBML defaults, HLS edition selection, and transparent alpha behavior have not yet been exercised. The non-reproduced install-end verifier anomaly is retained as build-script reliability evidence but does not change the verified artifact contract.
- Next action: build `MobileArm64_v8aDebug`, `LeanbackArm64_v8aDebug`, and `LeanbackArmeabi_v7aDebug` once against these exact assets.

### 2026-08-28 package and device checkpoint

- Targeted App build: one Gradle invocation built `:app:assembleMobileArm64_v8aDebug`, `:app:assembleLeanbackArm64_v8aDebug`, and `:app:assembleLeanbackArmeabi_v7aDebug`; result `BUILD SUCCESSFUL in 3m 52s` with 208 actionable tasks (28 executed, 3 from cache, 177 up-to-date).
- APK SHA-256: Mobile arm64-v8a `a45e0eab9a419da42eaa6dea9c95e9b4f93190bdca2f70bb14d9ff3c3a5107fb`; Leanback arm64-v8a `eaaeb88effdf4dad21f2998ed929148d10d11d31ad63ede9e0b56b81bce3791b`; Leanback armeabi-v7a `482edbcdf7a98eb5bfd12a945670e0063a5f366d9e84551d6656e746e83bb9c0`.
- Packaging identity: every `assets/mpv-libs/<abi>/*.so` entry in all three APKs was compared byte-for-byte with the corresponding candidate asset and matched.
- Final native asset SHA-256 manifest:

| Asset | arm64-v8a | armeabi-v7a |
| --- | --- | --- |
| `libc++_shared.so` | `c4c2fe5cbcb1fba0003a31fc7ab29a9bb12df6cc187ec45a806462540e83d93b` | `af383654daf4cf0829615460419a180f84edd9d8bf51aa0f81ed0db811bf8491` |
| `libmpv.so` | `69e9a8d10560a41107680ca4de737996885f2f37fa353fd5ede30334866eeb7b` | `ec2cbc58616bb383eb2f80d3fde883da02036b00062287a6f956ebda1aeb5ce3` |
| `libmvcodec.so` | `dd0df8e451f34d1f1f04e829d5e3c54415ecfd100e1da185ba99400672a356ce` | `057737104206f1091f1e498f09112f6fa4786a126b1699d477a2a3f9227c53ef` |
| `libmvdevice.so` | `6e1eb48c069a2a5a9d2668048a8fcde47d8638d815ed8486d755a9c7ef811c1d` | `67054a2f81bbfce49f2089819224dfcbf1f67dcb1a92581b7b5116efdd651605` |
| `libmvfilter.so` | `15ecd2e2b6e9856377dfdf788aaffd4e13baf3aacb143fe12a81a7218e35dfeb` | `ee6a911db507f1314d6215582ed2166c86f7c01067d1a8672b0c97d56c5590ed` |
| `libmvformat.so` | `d452ca2a0ac2d81eb31176b454d0a4c641f4ae776c4b2549dac0f705c0816b77` | `fb64b119a50abdf5d83b7b7c3283b25bcaefa0c86a440eab806a7aba1f06ad1a` |
| `libmvutil.so` | `36809585992a694ccefe28194f49b9313ce431c5c008dabb15296f2be3d3de2c` | `92c75297d4bb6ab0486ffef09065ed74762eefd41e1a0ba33d0f77484122d821` |
| `libmwresample.so` | `abd917e5ed21a302fd61957f093c4f289854dc6b33d2849077919ab35bc2b1d9` | `fb96fa91e7fde0a537333ab7061e438dc606e738b4fa2d805feae02ce8686442` |
| `libmwscale.so` | `4228aacfc2992b9b66943e6050ee59e679ef0e249024f3f2a02478e750e644fa` | `7a88b24141d7117aa06135bea69ba5ffe84f12004f81daaed12cf2b84b2754a3` |
| unchanged `libplayer.so` | `aedfcb5bcce929cd08bdd113e2031945efc514f3ec4e21daaa39c5744d941bff` | `d146b4f7b5aa95f6768c5bae981bd2f01aaa5166e36d28e342b3789e0233b4b4` |

- Device: vivo V2453A / Android 15 / API 35 / arm64-v8a. The pre-update installed APK was saved locally as `/private/tmp/P1-device-20260828-73CIHY/baseline-installed.apk`, SHA-256 `c3d8e88f226ff1c44577ce969ad0815d98bf7ac18e6ce67877c3638a84887a16`.
- Install identity: the OEM-assisted update completed without uninstalling or clearing data. Pulling the installed candidate back from the device produced SHA-256 `a45e0eab9a419da42eaa6dea9c95e9b4f93190bdca2f70bb14d9ff3c3a5107fb`, identical to the Mobile arm64-v8a APK.
- Device smoke: the App launched to `HomeActivity`, then an existing MPV playback flow reached `VideoActivity`. Visible playback and diagnostics exercised Vulkan `gpu-next`/`androidvk`, MediaCodec hardware decode, AudioTrack, 1920x1080 H.264/SDR playback, track parsing including PGS subtitles, and later a 3840x2160 HEVC/HDR10/TrueHD source. The App process log contained no `FATAL EXCEPTION`, fatal signal, `SIGSEGV`, `SIGABRT`, or `dlopen failed`; the App was force-stopped after evidence capture to avoid continued metered-network use.
- Device evidence: `/private/tmp/P1-device-20260828-73CIHY/home.png`, `home.xml`, `home-scrolled.png`, `home-scrolled.xml`, `playback.png`, `launch-logcat.txt`, and `playback-app-logcat.txt`.
- Interpretation limit: this is a compatibility/load/playback smoke, not a controlled performance comparison. It does not prove packed RGB10 channel order/gradient quality, zero-length EBML defaults, program-level HLS bitrate edition selection, or libplacebo alpha preservation. Those require the dedicated fixtures and rendering comparisons listed in section 7; no performance improvement or full behavioral acceptance is claimed from this smoke.
- Rollback: repository rollback is the baseline tag `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/baseline-20260828-1246` or a revert of the eventual P1 commit. The device can be restored with the saved baseline APK if needed; no device data was cleared.
- Next action: run one final scoped diff/checkpoint validation, then finish the active task guard unit and create its annotated recovery tag.

### 2026-08-28 implementation closure

- Implementation commit: `a5971e3814d3b0826a5702d607dd6d1675b2ce53` (`mpv: add P1 format and shader correctness fixes`).
- Recovery tag: `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/20260828184107-a5971e3814d3`.
- Commit contents: four provenance-preserving P1 patches, deterministic build-script application, rebuilt two-ABI MPV/FFmpeg assets, and the complete implementation/validation record. The existing lock and both `libplayer.so` files remained unchanged.
- User acceptance evidence: after candidate installation, the user played several original-disc videos and observed no playback problem. This supplements the automated/device smoke for disc/container parsing, track selection, subtitles, hardware decode, Vulkan output, audio routing, and normal playback continuity without claiming a controlled performance comparison.
- Final decision: P1 is accepted as a successfully implemented candidate. No additional broad regression suite is required. Packed RGB10 colour/gradient output, crafted zero-length EBML defaults, multi-variant HLS `variant_bitrate` selection, and transparent libplacebo alpha composition remain useful targeted tests when suitable samples exist, but they do not block this completed stage.
- Rollback: revert `a5971e3814d3b0826a5702d607dd6d1675b2ce53` or return to `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/baseline-20260828-1246`; the committed candidate itself is recoverable at `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/20260828184107-a5971e3814d3`.
- Next action: continue the master assessment ordering from the next incomplete MPV task; do not reopen P1 unless a dedicated fixture reveals a concrete regression.
