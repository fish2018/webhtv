# WebHTV Player Integration Gates

Use this reference for FFmpeg, media/Media3/nextlib, Exo, mpv, mpv-android, libplacebo, JNI, native assets, and player-common changes.

## Contents

1. Source of truth
2. Cross-player boundary
3. Exo gates
4. MPV gates
5. Common-function gates
6. Native supply-chain gates
7. Risk-based playback matrix
8. Go/no-go rules

## 1. Read current source-of-truth files

Do not rely on the versions printed here. At the start of each stage, inspect current repository state, including:

- `README.md`
- `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`
- `third_party/fongmi-repositories-lock.json`
- `third_party/media-lock.json`
- `third_party/mpv-native-lock.json`
- `third_party/mpv-native-build.md`
- `scripts/build_media_deps.sh`
- `scripts/build_mpv_native.sh`
- `scripts/build_mpv_player_jni.sh`
- `scripts/verify_mpv_native_assets.sh`
- Relevant `third_party/patches/`, native overrides, JNI headers/source, AAR/POM, and packaged assets

Record complete source hashes, build toolchain, both relevant ARM ABIs, artifact hashes, and local patch state before changing a lock or binary.

## 2. Keep the player boundaries explicit

- Exo/nextlib and MPV may use the same FFmpeg source revision, but they are separate builds with different toolchains, configurations, exported names, consumers, artifacts, and rollback units.
- Never treat one player's successful playback as proof for the other.
- Do not share or silently substitute `.so`, AAR, renderer, demuxer, decoder, Surface, audio, or JNI behavior across players.
- Common assets may include samples, expected metadata, diagnostic schema, or App adapters. Shared source semantics do not imply shared binary artifacts.
- Follow the user-required order: decide and implement Exo first, stabilize it, then decide and implement MPV. Re-check common assumptions before MPV work.

## 3. Exo gates

### Source and packaging

- Keep Media3 modules on a compatible, intentional version set; document any unstable API use and all App consumers.
- Preserve nextlib FFmpeg configuration, ABI level, exported library naming, AAR/POM coordinates, and native load behavior unless the approved stage explicitly changes them.
- Verify that new upstream code is compiled and reachable in the actual WebHTV build; source presence alone is not capability.
- Track AAR, POM, contained `.so`, license, source commit, patches, and SHA-256 as one release unit.

### Local contracts to audit

The exact list evolves; inspect the current fork and assessment. At minimum, check relevant preservation of:

- AV3A and other local codec/format support
- FFmpeg software audio/video renderer selection and fallback
- Soft-load-shedding/decoder recovery behavior and quality policy
- Dolby Vision/HDR parsing, conversion policy, CSD/metadata, renderer/output selection, and failure fallback
- DTS/TrueHD/passthrough/offload/AudioTrack behavior and diagnostics
- HLS/DASH/RTSP timing, SAMPLE-AES, proxy/Range/key/cache/error classification, and live behavior
- Subtitle/Cue/track-name/artwork metadata consumed by App UI
- ISO/UDF/Blu-ray/DVD/SACD reader ownership, extents, cancellation, metadata, and App resolver contracts
- Mobile/Leanback and `armeabi-v7a`/`arm64-v8a` packaging where affected

Do not replace a local factory/renderer/parser wholesale if it would drop later WebHTV behavior. Port the narrow invariant and add regression coverage.

### Exo candidate evidence

Select relevant checks from:

- Media/nextlib source tests and fixtures
- AAR contents, symbol/ELF/ABI/license/provenance inspection
- Targeted Java/Kotlin unit tests and compilation
- Mobile and Leanback builds for affected ABI/product paths
- Representative playback with decoder/renderer/output/fallback diagnostics
- Malformed, truncated, seek/flush, discontinuity, encrypted, or cancellation cases for changed state machines
- Baseline/candidate performance and quality comparison for decoder/rendering changes

Do not publish an AAR/API change before all coupled App adapters compile and their cross-player behavior is understood.

## 4. MPV gates

### Treat native dependencies as one locked build graph

The MPV product includes mpv, its FFmpeg build, libplacebo, mpv-android build framework, networking/crypto and other static dependencies, local patches/overrides, `libc++_shared.so`, and sometimes the App JNI bridge. Change only an approved subset while rebuilding a coherent graph from pinned inputs.

- Build both affected ARM ABIs from the same declared lock and patch set.
- Preserve the MPV FFmpeg filename, ELF `SONAME`, and `DT_NEEDED` namespace separation (`libmv*`/`libmw*` in the current design) from Exo/nextlib `libav*`/`libsw*`.
- Confirm static versus shared linkage and packaged file set. Do not add accidental standalone network, graphics, codec, or C++ libraries.
- Rebuild `libplayer.so` only when JNI source/header/client API or its linked contract changes, but verify compatibility whenever `libmpv.so` changes.

### Local contracts to audit

Inspect current scripts/patches and preserve relevant behavior, including:

- MediaCodec port-starvation/fallback and timestamped release/diagnostics
- Proxy Range handling and direct curl/HTTP/2 versus App proxy/`stream_cb` path distinctions
- Disc controls, Matroska/segment seek, helper schemes, archive/disc behavior
- TrueHD/DTS-HD AudioTrack carrier/channel mask policy
- Dolby Vision profile 7 packet/config safety, HDR10 base-layer fallback, metadata and Surface/output gates
- OpenGL and Vulkan direct/stable/legacy/generic paths
- AImageReader/AHardwareBuffer acquisition, bounded waits, fences, release ownership, and vendor-specific starvation avoidance
- Optional/double Surface OSD lifecycle, generation/request ordering, and rapid Surface recreation
- HDR negotiation, LUT/subtitles, hardware/software decoding, track/edition/chapter behavior
- Android lifecycle, especially repeated prepare/play/stop/release and destroyed-mutex/resource-use-after-release signals

Do not drop a local patch because upstream has a similarly named change. Compare semantics, error states, ownership, later fixes, and verification markers.

### MPV candidate evidence

At minimum for a native candidate:

- Run `bash scripts/verify_mpv_native_assets.sh --require-elf` with a suitable NDK `llvm-readelf` or system `readelf`.
- Verify lock, build script, patch/override hashes, source revisions, versions, two-ABI artifact hashes, `SONAME`, `DT_NEEDED`, exported/required symbols, packaged manifest, and license/provenance.
- Build affected App variants and confirm the packaged assets are the candidate artifacts.
- Run risk-selected OpenGL/Vulkan, direct/stable/legacy/generic, hard/soft decode, HDR/DV, LUT/subtitle, audio, HLS/network/Range, switching, disc/ISO, and lifecycle cases.
- Retain device/API/GPU/codec, settings, logs, selected backend/decoder/VO/AO, fallback/error reason, and artifact hash for every result.

Compilation or marker-string checks alone cannot validate Surface, fence, decoder, audio, lifecycle, or vendor behavior.

## 5. Common-function gates

Classify common work before implementation:

1. **Same source, separate build:** for example one FFmpeg source commit used by Exo and MPV with different configuration/toolchain/names/artifacts. Validate and roll back separately.
2. **Shared input/evidence:** use the same sample ID and expected container/codec/metadata/timing but record separate `exo_result` and `mpv_result`.
3. **App common adapter/API:** attach it to the earliest player stage whose API requires it, and simultaneously regress the other player if the App behavior is shared.
4. **Experimental shared policy:** keep behind a feature flag or out of formal locks until both players' semantics and fallback are defined.

A common stage must not become a catch-all refactor. State ownership, why it cannot remain player-local, and its independent rollback boundary.

Suggested shared sample manifest fields:

```text
sample_id, source/hash, container, codec/profile, encryption, network/range/proxy condition,
expected tracks/timestamps/metadata, exo entry/result, mpv entry/result,
device/API/settings, decoder/renderer/VO/AO, fallback/error, evidence paths
```

“Played successfully” is not enough when the changed contract is metadata, timing, fallback, quality, or resource lifecycle.

## 6. Native supply-chain gates

For every rebuilt binary set, retain:

- Source repository URLs, branches/tags, and full commits
- Build script and lock revision
- Toolchain/JDK/SDK/NDK/API/ABI and relevant environment/configuration
- Applied patch/override order and hashes
- Direct/static/shared dependency versions and license notices
- Rebuild command and build logs
- Unstripped/stripped identity as applicable and final SHA-256
- ELF class/machine/SONAME/DT_NEEDED/symbol evidence
- AAR/APK asset manifest and final artifact hash
- Tests tied to that exact artifact hash

Do not accept an unexplained binary, a floating dependency input, or a source/lock/artifact mismatch. SLSA-style provenance does not require a specific service, but inputs and transformation must be reconstructable.

## 7. Risk-based playback matrix

Choose only dimensions touched by the stage plus critical neighbors:

| Dimension | Examples |
| --- | --- |
| Product/ABI | Mobile/Leanback; `arm64-v8a`/`armeabi-v7a` |
| Player/path | Exo hardware/software/extension; MPV lavf/curl/`stream_cb`/App proxy |
| Video output | Surface direct, OpenGL, Vulkan direct/stable/legacy/generic |
| Codec/color | AVC/HEVC/AV1/VP9; SDR/HDR10/HLG/Dolby Vision; 8/10-bit |
| Audio | PCM, AAC, AC3/EAC3, TrueHD, DTS families; decode/passthrough/offload |
| Container/protocol | MP4/MKV/TS/HLS/DASH/RTSP, live/VOD, Range/reconnect, ISO/disc |
| State transition | prepare, seek, flush, track switch, source switch, Surface recreate, background/foreground, stop/release/repeat |
| Adversarial | truncated/malformed input, missing metadata/config, short read, cancellation, network error, decoder failure |
| User feature | subtitle/OSD, LUT, speed, chapter/edition, artwork/track names, diagnostics |

For performance-sensitive stages, measure startup, seek/rebuffer, dropped/late frames, A/V sync, CPU/memory, power/thermal, and quality/fallback as applicable. Define thresholds before reviewing results.

## 8. Go/no-go rules

Do not mark a stage ready when any applicable condition remains:

- An in-scope upstream commit is missing from the ledger or uses only an abbreviated source identity.
- A current WebHTV contract or local safeguard was removed without evidence and explicit approval.
- Source, lock, patches, packaged artifact, or documented hashes disagree.
- One ABI/player/product path substitutes for another required path.
- A correctness, crash, lifecycle, ABI, security, or material performance regression is unresolved.
- Performance claims lack comparable repeated evidence or trade quality for speed without approval.
- Rollback cannot restore the coupled source/lock/artifact/App state.
- Required documentation/checkpoint/commit provenance is stale.
- The change has expanded beyond the approved functional stage.

If a risk is accepted by the user, record the exact risk, scope, evidence, mitigation, and rollback rather than silently treating the gate as passed.
