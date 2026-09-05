# C0-M：MPV FFmpeg 9.0.1 独立重建

## Recovery anchor

- Objective: move the MPV native build from FongMi FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` to the Exo-validated `177f090e0503b7e013922ca903bde14b1c375f18`, while preserving the existing MPV patches, ABI namespace, renderer, decoder, audio, subtitle, disc, and JNI contracts.
- Authority: user approved implementation on 2026-08-29; scope is limited to C0-M.
- Baseline: branch `fongmi-sync`, HEAD `5f865d0dbdcc37dfc07f06a8d9514e4523eac0d0`; recovery tag `recovery/C0-M-MPV-FFMPEG-9.0.1/baseline-20260829105431-5f865d0dbdcc`.
- Protected dirty path: `AGENTS.md`; it remains outside the task commit.
- Scope: MPV FFmpeg lock/provenance, both ARM native asset sets, required build documentation, this record, and the master assessment status.
- Excluded: MPV/libplacebo/mpv-android revisions, Exo AARs, JNI source/API, C2 DV7-to-P8.1 activation, and unrelated App behavior.
- Status: completed and closed in `9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223`; recovery tag `recovery/C0-M-MPV-FFMPEG-9.0.1/20260829122948-9b7cf9cfbbea`.
- Next action: keep C0-M closed. Handle the pre-existing no-Surface MediaCodec reinitialization during teardown as a separate lifecycle bug with its own task, scope, verification, commit, and rollback tag.

## 1. User-visible capability

This is a dependency safety and compatibility update. Normal MPV playback remains on the current paths, while malformed or unusually large MPEG-PS/TS, DASH, HEVC, RTP, AV1, WebP, and audio inputs get the FFmpeg 9.0.1 boundary and initialization fixes. Some 32-bit HEVC software-decoding paths also receive upstream arithmetic and branch optimizations. The new `dovi_rpu convert=p81` code present in the source is not enabled by this task.

## 2. Source graph and provenance

| Input | Baseline | Candidate | Role |
| --- | --- | --- | --- |
| `FongMi/FFmpeg` | `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | `177f090e0503b7e013922ca903bde14b1c375f18` | Only source revision changed by C0-M |
| `FongMi/mpv` | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | unchanged | Existing MPV source and local WebHTV patches |
| `FongMi/libplacebo` | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | unchanged | Static renderer dependency |
| `FongMi/mpv-android` | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | unchanged | Build framework and JNI reference |

The candidate FFmpeg parent chain contains 49 audited commits. Commits 1–26 are 9.0.1 maintenance/security/version changes; 27–48 are rebase-equivalent or already covered behavior; 49 adds `dovi_rpu convert=p81` and remains disabled. C0-M rebuilds the coherent MPV graph from the candidate source, retaining `ffmpeg-webhtv-proxy-range.patch`, `ffmpeg-mediacodec-port-starvation.patch`, `libmv*`/`libmw*` renaming, and all existing MPV/Vulkan/DV/AudioTrack safeguards.

## 3. Acceptance and rollback

Acceptance requires both ARM ABIs to build from the candidate lock, the existing patch stack to apply without semantic drift, ELF `SONAME`/`DT_NEEDED` separation to remain intact, APK assets to match the candidate hashes, and focused playback/lifecycle checks to show no functional or material performance regression. The current `libplayer.so` remains unchanged unless an actual client-API incompatibility is proven.

Rollback is the baseline tag above or a revert of the atomic C0-M commit, restoring `third_party/mpv-native-lock.json`, provenance records, and both ABI MPV assets together. Exo's already-published FFmpeg 9.0.1 AAR is not part of this rollback.

## 4. Implementation record

### 2026-08-29 10:54 CST — approved start

- Task guard: `C0-M-MPV-FFMPEG-9.0.1`, mode `upstream`.
- Baseline tag created before edits: `recovery/C0-M-MPV-FFMPEG-9.0.1/baseline-20260829105431-5f865d0dbdcc`.
- Initial dirty state: only `AGENTS.md`, preserved and excluded.
- Result: `scripts/build_mpv_native.sh --abi all --prepare-only` passed in about 9 seconds. FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`, mpv-android, mpv, and libplacebo were pinned successfully; the existing proxy Range, MediaCodec starvation, Vulkan shader, and P2 patch checks passed.
- Next action: run one clean two-ABI native build/install.

### 2026-08-29 12:05 CST — dual-ABI build and deterministic verification

- The single planned `scripts/build_mpv_native.sh --abi all --install --jobs 8` run completed successfully for `arm64-v8a` and `armeabi-v7a`. FFmpeg 9.0.1, the unchanged MPV/libplacebo graph, and all existing local patches compiled and linked without errors; only existing dependency/compiler warnings were emitted.
- `libplayer.so` was preserved byte-for-byte. Its current Git object IDs match the baseline for both ABIs: arm64 `f5aa3274174503014210dd4558003e11351aca73`, armv7 `2a3435a7f89ff6dd74a3f7538baf6db275b379b3`.
- `bash scripts/verify_mpv_native_assets.sh --require-elf` passed for both ABIs, including lock provenance, stable Vulkan shader contract, P2 patch scope, ELF `SONAME`/`DT_NEEDED`, namespace, and packaging rules.
- `bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` passed in 59 seconds. The APK is `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`.
- The combined committed native payload grows by only 6,316 bytes before APK compression: arm64 +3,176 bytes and armv7 +3,140 bytes. `libmpv.so` is unchanged in arm64 size and 32 bytes smaller in armv7; the increase is confined to FFmpeg component libraries.
- Native asset SHA-256 values:

| ABI | Library | SHA-256 |
| --- | --- | --- |
| arm64-v8a | `libmpv.so` | `8ffa22949f3e99c0425e6c1154e294294c65c70025bbad485d835395b91b1e80` |
| arm64-v8a | `libmvcodec.so` | `615f1f8dfb20f4deca8a1cc5506178f0b9dd2ffd24d778845db69726f0d5b734` |
| arm64-v8a | `libmvdevice.so` | `da88a7db8b5cb01dc946b4957ae9b7276f043b387e10af9c46272a41c7ecb53e` |
| arm64-v8a | `libmvfilter.so` | `58593467521973c86eed1c703e8ac85d21e1c136251e9bde17155c4bb9c6e8bc` |
| arm64-v8a | `libmvformat.so` | `8ea1973b30262bf4e89c1d68e582aaa1298111310001e15abfb7fe329e6cdd1a` |
| arm64-v8a | `libmvutil.so` | `06b12c501e678f6206cee88c46a7966f667879d876a038ccdb7cae9ac2654291` |
| arm64-v8a | `libmwresample.so` | `b363207d482ee2dbce63c10b90e335c19a20039f26e086be9476cb08c4780815` |
| arm64-v8a | `libmwscale.so` | `5bbe36d1b068d2e51520a6ccac6bbc2f09ea34e0215b717b77fdc159d0282fa8` |
| armeabi-v7a | `libmpv.so` | `b24f28f88fe1d560003d5f8db719c81d57aaece7489c5dc9a248cba41c678898` |
| armeabi-v7a | `libmvcodec.so` | `9c31756aee4365df899a233e57f48206afcfae73ad258b93bff20c551457507e` |
| armeabi-v7a | `libmvdevice.so` | `58150681bb12356108014297439c5393bb87b0cb0621a5e30b6c30fcce416d3e` |
| armeabi-v7a | `libmvfilter.so` | `b48111a074c87f947edde54c12d0e6e71a492802e539cac4894d110a84c8c36a` |
| armeabi-v7a | `libmvformat.so` | `d9e135c02a58f53ea9fb0a87bed1d665dceedd1b8546ba76f8eaf171b1c54a30` |
| armeabi-v7a | `libmvutil.so` | `e41bb8455a23593d61c97fdaf23e42672787fb3cb240dcb34cec7b968209789f` |
| armeabi-v7a | `libmwresample.so` | `4a3a84a0e716f32d039d56377ef9d34365294e590b67bc930a7db2855bd175e3` |
| armeabi-v7a | `libmwscale.so` | `563b154eea57805ad96e629ddf2d2f604ede89a718c22ba0449abaa4551fda80` |

- Device gate: `adb devices -l` returned no device, and the prior Wi-Fi endpoint `192.168.1.9:5555` refused one reconnect attempt. No repeated connection checks were made. Runtime acceptance, commit, and recovery tag remain intentionally pending.

### 2026-08-29 12:20 CST — focused device playback and lifecycle verification

- Device: vivo V2453A (`10CF6H1D2L0009S`), Android arm64-v8a. The current arm64 debug APK was installed once and App preference `player=2` confirmed that the exercised path was MPV.
- Command-driven local playback covered subtitle-bearing MKV, AAC 5.1, E-AC3/Atmos DDP, TrueHD 7.1, DTS-HD MA 5.1, LPCM 7.1, and DTS 5.1 in TS. Every input entered `VideoActivity` and produced MPV `file-loaded`, audio/video reconfiguration, and `playback-restart` evidence without decode initialization failure.
- AAC, TrueHD, LPCM, and DTS TS were observed actively advancing. E-AC3 and DTS-HD MA loaded and configured successfully; their paused state came from the existing playback-history position rather than a decoder error.
- The TS case continued playing after HOME moved the Activity into picture-in-picture. A command-driven fast-forward advanced the position from `81234 ms` to `87220 ms`, including about three seconds of expected playback progress during the observation window.
- Stop/exit produced MPV `end-file` and `shutdown`. The crash buffer was empty, with no Java crash, SIGSEGV, or SIGABRT.
- Remaining risk: during picture-in-picture teardown, the BufferQueue was abandoned and FFmpeg then logged `h264_mediacodec: Both surface and native_window are NULL` twice while creating decoder components that were immediately released. Source review shows `ff_mediacodec_surface_ref()` emits this error and returns `NULL` when decoder initialization has neither a Java `Surface` nor an `ANativeWindow`; it is not a normal unref message. The focused run had no port-starvation/decode-failure precursor and proceeded to `end-file`/`shutdown` without crash. The same message exists in pre-C0-M device logs, so it is a pre-existing teardown lifecycle race rather than a regression introduced by FFmpeg 9.0.1. It remains a separate bug because the unnecessary decoder initialization should be suppressed after Surface loss.
- Existing unrelated warnings `Error parsing option http-allow-redirect` and `mpv_get_property(...demuxer-cache-state...) error` remain unchanged and are outside this dependency-only task.
- Conclusion: C0-M meets the available-device release gate. MPV now independently uses the same FFmpeg 9.0.1 source revision as Exo while retaining its separate ABI namespace, build graph, renderer/decoder/audio policies, JNI binary, and rollback boundary.

### 2026-08-29 12:34 CST — atomic implementation closure

- Implementation commit: `9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223` (`build(mpv): align FFmpeg with 9.0.1`).
- Recovery tag: `recovery/C0-M-MPV-FFMPEG-9.0.1/20260829122948-9b7cf9cfbbea`; task guard created it immediately after the commit in 0 seconds.
- Commit scope: the FFmpeg source lock/provenance update, both ARM ABI MPV/FFmpeg asset sets, build and user documentation, this task record, and the master assessment index. Protected pre-existing `AGENTS.md` was not staged or committed.
- Verification recorded in the commit: prepare-only, one dual-ABI native build/install, ELF/assets validation, Mobile arm64 debug APK build and asset identity, plus V2453A MPV subtitle/audio/video playback, seek, picture-in-picture, stop/shutdown, and crash-buffer checks.
- Rollback: revert `9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223` or reset the complete C0-M-owned paths to the baseline tag `recovery/C0-M-MPV-FFMPEG-9.0.1/baseline-20260829105431-5f865d0dbdcc`; do not partially mix the old FFmpeg component libraries with the new `libmpv.so` graph.
