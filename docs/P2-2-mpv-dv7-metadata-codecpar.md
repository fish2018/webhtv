# P2-2: MPV DV7 metadata/codecpar/error completeness

## Recovery anchor

- Objective: complete the existing MPV Dolby Vision Profile 7 HDR10 base-layer fallback when container metadata is incomplete, while preserving WebHTV packet ownership, Surface Direct, single-Surface EL, performance, ABI, and packaging contracts.
- Acceptance: Profile 7 HDR10 fallback creates the splitter without relying on `dv_el_present`, initializes the BSF with checked errors, copies filtered `par_out` back to decoder codec parameters, clears `dv_el_present`, rejects packet sizes above `INT_MAX`, and keeps all current WebHTV safety paths; both ARM ABIs, ELF/package checks, and focused device playback pass without a performance or neighboring-format regression.
- Status: complete and committed. Source adaptation, strict patch preparation, complete two-ABI native build/install, ELF/asset verification, arm64 APK packaging, installation, and focused device playback verification all passed.
- Task/lane: `P2-2-MPV-DV7-METADATA-CODECPAR` / `upstream`.
- Workspace baseline: branch `fongmi-sync`, HEAD `14931888cf5c6e47902323c06180969cd4b9c32d`.
- Baseline recovery tag: `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/baseline-20260829-0342`.
- Protected pre-existing dirty path: `AGENTS.md`.
- Scope: the existing DV7 local patch, deterministic native verification if required, both ARM MPV asset sets, native build documentation, this task record, and the master assessment index.
- Excluded: MPV/FFmpeg/libplacebo/mpv-android lock updates, C0-M, C2, Exo, App/JNI APIs, DV5 auto routing, Vulkan backend selection, Android BL+EL software decoding, AudioTrack, and unrelated native dependencies.
- Rollback: restore the baseline tag or revert the eventual single atomic implementation commit; both ABI asset sets and the source patch move together.
- Next action: continue with the queued P3 assessment only as a separately approved task; P2-2 itself is closed.

## 1. Approved user capability

Some Profile 7 remuxes or disc-derived files retain interleaved base/enhancement NAL units but lose a reliable `dv_el_present` container flag. WebHTV already has an explicit MPV HDR10 fallback, but those files can currently bypass its splitter or leave the decoder parameters describing Dolby Vision after packets have been reduced to the HDR10 base layer.

The approved result is that MPV consistently feeds a clean, independently decodable HDR10 base layer to MediaCodec/software decoding and describes that filtered stream accurately. This reduces the risk of wrong colour, black output, hardware-decoder rejection, or avoidable software fallback on malformed or incompletely tagged DV7 inputs. It does not add full Android FEL/EL playback and does not change the default policy for DV5, Profile 8.1, ordinary HDR10, or SDR.

## 2. Source identity and cross-repository relationship

| Repository/input | Full commit | Role and disposition |
| --- | --- | --- |
| FongMi/mpv original assessed source | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | Original `demux: support Dolby Vision profile 7 HDR10 fallback`; stable patch-id `c1de01aba5dec040af172c2f2832cdab7dcf9bfa` |
| FongMi/mpv current force-push equivalent | `2477400b9732a8cf63951ff66cdf3a948e7a0822` | Current `fongmi` head on 2026-08-29; same stable patch-id; no later follow-up or revert exists on that branch |
| WebHTV locked mpv | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | Retained source baseline; this task adds only a local narrow patch delta |
| FongMi/FFmpeg dovi_split | `6026988b753ebb1bd424612f40b17c0c363d8ed7` | Supplies the `dovi_split` BSF already present in the locked FFmpeg graph |
| FongMi/FFmpeg dovi_split tests | `59619a191724cc1bcfb47b906f596b8372032764` | FATE coverage for `bl`, `bl_rpu`, `el`, and `el_rpu` filter modes |
| FongMi/FFmpeg DV/HDR chain | `6dc8edecd7ebafc80764b8c0a20f87e3f9fb1382`, `691a7d5a125b40dcc427ee298c983729e673d974`, `eb107bbafe37442065e42b4f2d410f371b758143`, `dd537f9a852d0ce40078f9ac520d7267ba850883` | Existing safe output, per-frame metadata, and FFmpeg 9 container support; no FFmpeg code change is approved here |
| WebHTV locked FFmpeg | `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | Continues to provide the tested BSF and metadata behavior through the MPV `libmv*` namespace |
| FongMi/mpv-android | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | Unchanged NDK r29 two-ABI build framework |
| FongMi/libplacebo | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | Unchanged static rendering dependency; P2-2 does not modify shader or mapping behavior |

MPV owns the demux policy and copies FFmpeg BSF output parameters into its `mp_codec_params`. FFmpeg owns packet filtering and output parameter derivation. mpv-android only builds the coherent graph; libplacebo and App/Exo code are not behavioral participants in this source change.

## 3. Current WebHTV implementation and exact gap

The current `mpv-dovi-profile7-hdr10-base-layer.patch` already provides:

- opt-in `demuxer-dovi-profile7=preserve|hdr10`;
- `dovi_split=mode=bl` before the decoder;
- a three-state packet result distinguishing expected empty output from hard errors;
- zero-copy only when the AVPacket and demux buffer match exactly, otherwise a padded packet copy with properties;
- decoder-level native-DV suppression while retaining Surface Direct for the HDR10 base layer;
- `VO_CAP_GPU_DOVI_EL` as the Android single-Surface EL gate;
- App option/diagnostic behavior and binary marker verification.

The current final source still has five missing contracts:

1. lavf and Matroska only create the splitter when `dv_el_present` is true;
2. `mp_codec_params_to_av()` and parameter-copy failure are not propagated;
3. `av_opt_set()` and `av_bsf_init()` errors are not retained for useful diagnostics;
4. HDR10 mode returns before applying `par_out` to `lav_codecpar` and clearing `dv_el_present`;
5. `demux_packet.len` is passed to FFmpeg without an explicit `INT_MAX` bound.

P1, P2-1, and P2-5 did not modify this patch after the last DV7 stabilization commit. The task is therefore not already implemented or superseded.

## 4. Best-practice evidence

Access date for all evidence is 2026-08-29.

| Evidence class | Source/revision | Grade | Claim and decision impact |
| --- | --- | --- | --- |
| Exact upstream source/history | FongMi/mpv commits `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` and `2477400b9732a8cf63951ff66cdf3a948e7a0822`; current branch history | A | The current commit is a force-push equivalent, not a new design; only five narrow hunks remain useful, while the complete parent chain would reopen Android EL software decoding |
| Official FFmpeg API contract | locked FFmpeg `libavcodec/bsf.h` at `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | A | `par_out` is set by the filter during `av_bsf_init()`; consumers must use the initialized output parameters rather than retaining stale input codec parameters |
| Official packet/padding contract | locked FFmpeg `libavcodec/packet.h` and `libavcodec/bsf/dovi_split.c` | A | Packet data is assumed to include `AV_INPUT_BUFFER_PADDING_SIZE`; WebHTV's exact-ref/padded-copy implementation is safer than the target commit's arbitrary subrange ref |
| Upstream tests | FFmpeg `59619a191724cc1bcfb47b906f596b8372032764`, `tests/fate/hevc.mak` | A | The BSF has deterministic mode-level output tests; WebHTV must add integration checks around MPV detection, codec parameters, and Android output policy rather than reimplementing the filter |
| Maintainer/revert/issue evidence | FongMi/mpv current branch has no commit after `2477400b...`; GitHub issue search for `dovi_split`/`profile 7` returned zero results | B | There is no later upstream correction or discussion that supersedes the assessed hunk; local safety adaptations remain necessary and explicitly owned by WebHTV |
| Mature related implementation | current WebHTV DV7 patch and FFmpeg BSF/FATE implementation | A | Preserve the already verified packet ownership and Android Surface rules; change only detection, output parameters, errors, and bounds |

Papers, general blogs, and cross-project benchmarks are inapplicable: this task introduces no new colour algorithm, decoder, scheduling policy, or performance technique. Correctness is determined by the FFmpeg BSF/API ownership contract, exact MPV call flow, and target-device playback evidence.

## 5. Alternatives and decision

| Alternative | Decision |
| --- | --- |
| No change | Rejected: incomplete metadata can bypass filtering, and filtered packets can retain stale DV decoder parameters |
| Adopt `2477400b...` or its full parent chain | Rejected: it weakens packet padding/error distinctions and can reopen BL MediaCodec + EL software decoding on Android |
| Narrow WebHTV adaptation | Approved: absorb metadata-missing detection, checked initialization, `par_out` synchronization, `dv_el_present=false`, `INT_MAX`, and optionally the auto-VO defense while retaining all local safety gates |

This narrow adaptation is the best-practice design for WebHTV. The one deliberate upstream adjustment is failure ownership: initialization errors remain explicit and observable, while the product must not silently claim a successful HDR10 fallback if the BSF could not be established.

## 6. Implementation stages

1. Update only the existing DV7 patch. Define `base_only` as HEVC Profile 7 plus explicit HDR10 fallback, so missing `dv_el_present` is tolerated only for the approved mode.
2. Check all allocation/conversion/copy/option/init results and keep the direct cause in one warning.
3. After successful `mode=bl` initialization, atomically replace `lav_codecpar` from `par_out`, clear `dv_el_present`, and emit one diagnostic marker.
4. Add an `INT_MAX` guard before converting packet length to FFmpeg's `int` size while preserving the current exact-ref/padded-copy and three-state behavior.
5. Keep the decoder-level Surface Direct gate and `VO_CAP_GPU_DOVI_EL`; only add the upstream auto-VO guard if it composes without changing explicit App output behavior.
6. Rebuild the complete locked graph for `arm64-v8a` and `armeabi-v7a`; do not change lock revisions or `libplayer.so`.

## 7. Verification and performance floor

- Strict ordered patch application against mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`.
- Static assertions for Profile 7 base-only detection, checked `par_out` copy, `dv_el_present=false`, initialization diagnostics, `INT_MAX`, and preserved packet/EL/Surface safety markers.
- One complete two-ABI native build and one `scripts/verify_mpv_native_assets.sh --require-elf` pass.
- arm64 APK asset identity and focused connected-device playback.
- Inputs: DV7 interleaved with complete metadata, a metadata-missing equivalent/fixture when available, BL-only/EL-only or malformed packet coverage, plus DV5/P8.1/HDR10 negative controls.
- Outputs/lifecycle: Surface Direct, Vulkan auto/stable, OpenGL GPU; start, seek, pause/resume, flush, replace media, and EOF.
- Metadata: filtered decoder parameters no longer advertise EL/RPU, HDR10 mastering/content-light/transfer data remain present, source profile reporting and fallback diagnostics remain correct.
- Performance: no new per-frame allocation or copy on already supported packets; the only unconditional packet-path work is an integer bound check. Existing exact zero-copy behavior remains. Any sustained new drop, rebuffer, CPU/memory increase, or second EL decoder rejects the candidate.
- Packaging: no new library; `libplayer.so` remains byte-identical; APK size change is limited to normal native code delta.

## 8. Source implementation checkpoint: 2026-08-29 03:52 CST

- Baseline/tag/guard: HEAD remains `14931888cf5c6e47902323c06180969cd4b9c32d`; baseline tag is `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/baseline-20260829-0342`; task guard is active and continues to protect the pre-existing `AGENTS.md` change.
- Source change: the existing DV7 patch now creates a splitter for explicit Profile 7 HDR10 fallback even when `dv_el_present` is absent; all conversion/copy/option/init returns are checked; `par_out` is copied atomically into `lav_codecpar`; `dv_el_present` is cleared; packet length is bounded by `INT_MAX`.
- Preserved behavior: three-state packet results, exact AVPacket-ref condition, padded-copy fallback and copied properties, decoder-level Surface Direct gate, `VO_CAP_GPU_DOVI_EL`, App option/diagnostics, current lock revisions, and all P1/P2-1/Vulkan/AImageReader patches remain unchanged.
- Deliberate omission: the target commit's player-level automatic direct-VO helper was not copied. The existing decoder-level gate already prevents native-DV decoder configuration for explicit or automatic Surface Direct while retaining the current low-overhead output choice; adding another output selector would expand behavior without a demonstrated gap.
- Deterministic build checks: `scripts/build_mpv_native.sh` now rejects a prepared source missing the metadata guard, atomic codec-parameter swap, cleared EL flag, or `INT_MAX`; both native verifiers require the codec-parameter synchronization marker.
- Patch/source hashes: `mpv-dovi-profile7-hdr10-base-layer.patch` `bad45f5db2b45b21ba410414596580496ee571d77233d2a7a615bb5bd0613ed2`; `build_mpv_native.sh` `d6806bb787f2019c8d74473e63fdec34cb63a34bc5d54e452f75a6932b7da066`; `verify_mpv_native_assets.sh` `c9cbd8fe01794bec3438c057aa0b0ec2299607ff6fa1e156c2e1c05b2287e5c8`.
- Verification completed: production `--prepare-only` passed after the source edit and again after adding deterministic assertions. It applied the complete locked patch stack, including P1 and P2-1, and verified both stable and generic Vulkan shader contracts.
- Unverified: compilation, both ABI assets, ELF/package identity, APK packaging, and device playback.
- Exactly one next action: run the complete locked two-ABI native build/install once.

## 9. Two-ABI native build checkpoint: 2026-08-29 05:09 CST

- Build: the single planned `bash scripts/build_mpv_native.sh --abi all --install` run completed successfully for `arm64-v8a` and `armeabi-v7a`. Both builds compiled the adapted `demux/dovi_split.c`, linked `libmpv.so`, and installed the packaged App assets.
- Deterministic verification: one `bash scripts/verify_mpv_native_assets.sh --require-elf` pass succeeded for both ABIs using NDK r29 `llvm-readelf`. It confirmed lock/source versions, FFmpeg namespace separation, ELF and packaging rules, the existing stable/generic Vulkan contracts, and the P2-2 codec-parameter synchronization marker.
- Changed native asset hashes:
  - `arm64-v8a/libmpv.so`: `bff445a09a3daac5b42a20ea002e95ace90172922f7efe737f68e0f549e1cdba`
  - `arm64-v8a/libmvcodec.so`: `dd0df8e451f34d1f1f04e829d5e3c54415ecfd100e1da185ba99400672a356ce`
  - `arm64-v8a/libmvformat.so`: `d452ca2a0ac2d81eb31176b454d0a4c641f4ae776c4b2549dac0f705c0816b77`
  - `armeabi-v7a/libmpv.so`: `608ba379ea583d0cfafbd4010eb01d27f25ea8eb04152a662d8f3e03723fafbe`
  - `armeabi-v7a/libmvcodec.so`: `057737104206f1091f1e498f09112f6fa4786a126b1699d477a2a3f9227c53ef`
  - `armeabi-v7a/libmvformat.so`: `fb64b119a50abdf5d83b7b7c3283b25bcaefa0c86a440eab806a7aba1f06ad1a`
- JNI preservation: both committed `libplayer.so` files are byte-identical to HEAD. Their hashes remain `aedfcb5bcce929cd08bdd113e2031945efc514f3ec4e21daaa39c5744d941bff` (`arm64-v8a`) and `d146b4f7b5aa95f6768c5bae981bd2f01aaa5166e36d28e342b3789e0233b4b4` (`armeabi-v7a`). No JNI source, header, API, or binary was rebuilt.
- Existing warnings: FFmpeg/libbluray legacy-source warnings and shaderc jobserver `fcntl(): Bad file descriptor` messages remained non-fatal and were already present in this build graph; all affected targets linked and installed successfully.
- Workspace: branch and HEAD remain `fongmi-sync` / `14931888cf5c6e47902323c06180969cd4b9c32d`; protected pre-existing `AGENTS.md` remains unstaged and outside task scope.
- Unverified: APK asset identity and runtime behavior on the connected arm64 device, including DV7 fallback diagnostics, decoder/output selection, frame/rebuffer behavior, and one neighboring HDR/DV case.
- Exactly one next action: build one Mobile arm64 Debug APK and inspect its packaged MPV asset hashes before installation.

## 10. APK packaging checkpoint: 2026-08-29 05:14 CST

- Build: the single planned `bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` run passed in 1m16s (`103` actionable tasks: `9` executed, `94` up-to-date).
- APK: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`, size `162651210` bytes, SHA-256 `8c3f1d569d5b325d1e577a5a77d196c800c167159d76f313adc2c3bb02049a4c`.
- Package identity: all ten `assets/mpv-libs/arm64-v8a/` files were extracted once from the APK. A directory comparison against the verified workspace assets returned no differences; the packaged `libmpv.so`, `libmvcodec.so`, `libmvformat.so`, and unchanged `libplayer.so` hashes exactly match checkpoint 9.
- Device discovery: `adb devices -l` and `adb mdns services` returned no device. The previously used `192.168.1.9:5555` and the only current LAN neighbor `192.168.76.250:5555` both refused connection. This is an external ADB connectivity state, not a build or candidate failure.
- Superseded by checkpoint 11: the phone was subsequently connected over USB, the exact APK was installed with installer-assist, and the user confirmed the required focused playback verification passed.
- Exactly one next action: continue with the queued P3 assessment only as a separately approved task; P2-2 itself is closed.

## 11. Device verification and implementation closure checkpoint: 2026-08-29 06:54 CST

- Device: USB-connected vivo `V2453A`, serial `10CF6H1D2L0009S`.
- Installation: `install_apk_with_installer_assist.sh` installed and launched `app-mobile-arm64_v8a-debug.apk` successfully. `adb shell pm path com.fongmi.android.tv` returned the installed base APK; `dumpsys package` reported version `5.6.0`, code `560`, and the app process was running.
- Playback: the user confirmed the required focused P2-2 verification passed on the connected phone, including the approved DV7 playback path and its neighboring playback check. No playback failure, visible regression, performance degradation, or crash was reported.
- Evidence boundary: the APK/package identity, native ELF/asset checks, and user-confirmed runtime result together satisfy this stage's acceptance. No new raw log or synthetic metadata-missing fixture was available in this run, so those remain diagnostic follow-up material rather than blockers for the approved device result.
- Scope/protection: only the declared P2-2 source, native asset, script, and documentation paths are task-owned. `AGENTS.md` remains protected and `app/.cxx/` remains untracked build output outside the task commit.
- Rollback: revert the resulting atomic P2-2 commit, or restore `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/baseline-20260829-0342`; source patch and both ABI asset sets move together.
- Exactly one next action: continue with the queued P3 assessment only as a separately approved task; P2-2 itself is closed.

## 12. Commit and recovery tag closure: 2026-08-29 06:58 CST

- Atomic implementation commit: `ba47756d7e463abeb9377088b819a2520e150935` (`mpv: complete DV7 HDR10 fallback codec metadata sync`). It contains only the approved P2-2 source adaptation, both ABI MPV assets, deterministic build/verifier updates, and the task/build/assessment records.
- Recovery tag: `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/20260829065811-ba47756d7e46`, annotated on the implementation commit immediately after commit creation.
- Verification recorded in the commit: complete two-ABI native build/install, `verify_mpv_native_assets.sh --require-elf`, Mobile arm64 Debug APK/package identity, USB V2453A install/launch, and user-confirmed DV7 plus neighboring playback verification.
- Rollback: `git revert ba47756d7e463abeb9377088b819a2520e150935` for a shared branch, or restore the baseline tag `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/baseline-20260829-0342` in an isolated worktree. Revert/restore the source patch and both ABI assets together.
- Final status: P2-2 is complete. No lock, FFmpeg/libplacebo/mpv-android revision, JNI API, `libplayer.so`, or Android EL policy changed.
- Exactly one next action: continue with the queued P3 assessment only as a separately approved task; P2-2 itself is closed.
