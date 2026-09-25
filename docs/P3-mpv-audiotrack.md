# P3: MPV AudioTrack codec-aware carrier

## Recovery anchor

- Objective: enable the upstream DTS-HD MA 8-channel IEC61937 carrier mask on Android 12+ without changing the already verified TrueHD, DTS-HD HRA, AC3/E-AC3, PCM fallback, ABI, or MPV output contracts.
- Approval: user authorized implementation on 2026-08-29 after the assessment recommendation; scope is limited to P3-2. P3-0 carrier-rate behavior is already covered by the locked tree and P3-1 TrueHD behavior remains local.
- Baseline: branch `fongmi-sync`, HEAD `3cd32eea89fd492c7060fb33845a16aec27bf39f`; recovery tag `recovery/P3-MPV-AUDIOTRACK/baseline-20260829-073010`.
- Protected dirty path: `AGENTS.md`. It must remain outside the task commit.
- Scope: the MPV AudioTrack patch, Java carrier probe and unit tests, two ARM MPV asset directories, native build/verifier contracts, native build documentation, this record, and the assessment index.
- Excluded: FFmpeg/libplacebo/mpv-android lock revisions, Exo/Media3, JNI source/API and `libplayer.so`, video/rendering/DV policy, unrelated audio formats, and broad passthrough redesign.
- Rollback: revert the P3 commit or restore the baseline tag; native source patch, Java probe, tests, and both ABI assets must move together.
- Current status: complete in implementation commit `d82336bde585b62af43771284075a0a94a3d999e` with recovery tag `recovery/P3/20260829094014-d82336bde585`. Focused tests, two-ABI native build/ELF verification, Mobile arm64 APK build/install, and the available phone-speaker PCM fallback matrix passed. The active phone has no HDMI/eARC/USB passthrough route, so receiver bitstream identification and route hotplug remain residual validation rather than claimed results.

## 1. User-visible capability

When an Android 12+ device exposes an HDMI/eARC/USB passthrough route, DTS-HD Master Audio tracks with an 8-channel carrier can be sent to the receiver with a 7.1 IEC61937 channel mask. The receiver can then identify the original DTS-HD MA stream instead of receiving a stereo-described carrier that may fail to initialize, route incorrectly, or fall back to PCM. DTS-HD HRA remains a 2-channel carrier; TrueHD keeps WebHTV's existing 7.1 behavior on all supported API levels.

## 2. Source identity and disposition

| Repository/input | Full commit | Disposition |
| --- | --- | --- |
| FongMi/mpv target | `7282d53d58fcb8841ff93debea2a75e0b2afcd15` | Selectively adapt only SDK/API and 8-channel mask behavior; do not cherry-pick the full commit |
| FongMi/mpv target parent | `c2bc880511fd20850c586f2dc25aff770723b6b4` | Provenance for the target diff |
| WebHTV locked MPV | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | Baseline source; retain all local patches |
| WebHTV locked MPV carrier-rate equivalent | `416d4a0fae8213ecf8e730feda6e2d8591bbd76f` | Covered; do not reapply the sample-rate hunk |
| FongMi/mpv-android | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | Build framework only; unchanged |
| FongMi/FFmpeg | `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | Audio carrier input/codec support; unchanged |
| FongMi/libplacebo | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | Not involved in AudioTrack; unchanged |

The native owner is `FongMi/mpv`; `mpv-android` packages the coherent graph. The App-side `MpvAudioCapabilities` probe is a WebHTV consumer and must use the same rate/mask rules. No Exo AAR or shared FFmpeg binary is changed.

## 3. Existing implementation and exact gap

- `third_party/patches/mpv-audiotrack-truehd-channel-mask.patch` already selects 7.1 for TrueHD on every API level and stereo for other IEC61937 formats.
- The build and verifier already reject the obsolete native-output-rate override; the locked tree preserves 44.1/48 kHz AC3/DTS core and 192 kHz E-AC3/DTS-HD/TrueHD carriers.
- `app/src/main/java/com/fongmi/android/tv/player/engine/MpvAudioCapabilities.java` probes `dts-hd` as stereo for every device. It has no MA/HRA profile input, so native and Java can diverge if native starts selecting 7.1 for 8-channel MA.
- Existing tests cover codec-family mapping and probe filtering, but not the carrier mask/rate contract or the distinction between MA and HRA.

## 4. Adapted design

The native patch will add the upstream `Build.VERSION.SDK_INT` mapping and select 7.1 only when API >= 31 and `ao->channels.num == 8`, while preserving the local all-API TrueHD rule. The Java probe will expose a pure, testable mask/rate decision and conservatively advertise `dts-hd` only when the carrier contract required by both possible DTS-HD layouts is supportable; it will never relabel HRA as 8-channel. If the device cannot prove the required mask, MPV keeps its existing PCM fallback.

Alternatives considered:

- No change: preserves behavior but leaves DTS-HD MA 7.1 direct output unavailable on Android 12+.
- Full upstream commit: rejected because its sample-rate hunk is already covered and its API-gated TrueHD behavior would undo WebHTV's older-device workaround.
- Narrow WebHTV adaptation: selected; it is the smallest reversible change that aligns native and Java contracts and does not touch the decoder/rendering path.

## 5. Implementation stages

1. Add a Java carrier mask/rate helper and focused tests; keep the existing codec-family output order and conservative fallback.
2. Adapt `mpv-audiotrack-truehd-channel-mask.patch` for API 31+ 8-channel carriers while retaining TrueHD on older API levels.
3. Add deterministic build/verifier markers, apply the patch in the existing order, and rebuild both ARM ABIs without changing locks or JNI.
4. Build the affected Mobile arm64 APK, verify asset identity, and run the connected-device audio matrix when the route/device is available.

## 6. Acceptance and risks

Acceptance requires Java unit coverage, strict two-ABI native/ELF verification, APK asset identity, and playback/AudioTrack diagnostics for DTS-HD HRA, DTS-HD MA, TrueHD/Atmos, AC3 and E-AC3. The matrix must include API 29/30/31+, HDMI/ARC/eARC/USB routes, seek/pause/resume, route hotplug, direct-playback failure and PCM fallback.

The main residual risk is vendor HAL behavior: a 7.1 IEC61937 mask may be rejected on a device that accepts stereo. No per-frame work or new library is introduced; expected APK growth is limited to the small native code delta, and `libplayer.so` remains byte-identical. A missing DTS-HD profile or device route is a reason to keep the conservative probe, not to broaden the task.

## 7. Verification record

### Source and pre-build checkpoint: 2026-08-29 07:43 CST

- Java: `MpvAudioCapabilities` now models the exact carrier formats used by native AudioTrack. TrueHD remains 192 kHz + 7.1 on every API; Android 12+ `dts-hd` is advertised only when both 192 kHz stereo and 7.1 direct carriers are supported, so HRA cannot be admitted by an MA-only probe or vice versa; AC3/DTS core and E-AC3 retain their existing rates/masks.
- Native: the existing patch now maps `Build.VERSION.SDK_INT`, keeps the all-API TrueHD workaround, and selects 7.1 for other IEC61937 streams only on API 31+ when the actual native carrier has eight channels. No decoder, packet, renderer, JNI, or lock behavior changed.
- Focused test: `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.engine.MpvAudioCapabilitiesTest --no-daemon` passed (`73` tasks, `6` executed, `67` up-to-date).
- Patch-stack check: `bash scripts/build_mpv_native.sh --prepare-only` passed against locked MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` after applying the complete WebHTV MPV patch order and all P1/P2 shader/DV assertions.
- Source hashes: patch `f4acb5cb4ba04f73b8787120c1053fda1d01f5a5632aa65de107201b82f5dd03`; build script `98c0d50970fba47d6751c8563ce97e36790b8ba96bbca90c8f103801c1a6e8c8`; verifier `6cdab150d41b5d066b42e28694e64e3c39cb80388df6744756c83150f6bfcd99`; Java source `63bea8077ad65d03b4c6443cbe38eb5b37dfd016cc8e0eda241417edf10584a1`; Java test `07d0f13c438b296c43f6fe9e5a4823e9f5d48dfd0a6c8d09c615a7fab9d76b35`.
- Device evidence source: vivo `V2453A`, Android 15/API 35, serial `10CF6H1D2L0009S`; `/storage/emulated/0/Download/声道测试/` contains DTS-HD MA 5.1/7.1, DTS:X, DTS core, TrueHD/Atmos, E-AC3/Atmos, AC3, AAC, and LPCM fixtures. Actual passthrough result still depends on the active HDMI/eARC/USB route and will be recorded after APK installation.
- Workspace: branch `fongmi-sync`, baseline HEAD `3cd32eea89fd492c7060fb33845a16aec27bf39f`; `AGENTS.md` remains protected.
- Exactly one next action: run `bash scripts/build_mpv_native.sh --abi all --install` once, then verify the installed assets.

### Native build and APK checkpoint: 2026-08-29 09:21 CST

- The single two-ABI native build completed for `arm64-v8a` and `armeabi-v7a`; the build script installed both `libmpv.so` assets and confirmed that neither `libplayer.so` changed.
- `scripts/verify_mpv_native_assets.sh --require-elf` passed for both ABIs after the install. The resulting `libmpv.so` SHA-256 values are `04cfe3ae40118ec77b988323791925b67014b7dda33fdbe54848db8ff219c9a5` (`arm64-v8a`) and `d13da6308db9c6d1757a8c71928284efd83e9de82a14e5465293fc297ab2cc75` (`armeabi-v7a`).
- The native size delta is bounded to 432 bytes on `arm64-v8a` (`17,714,480` -> `17,714,912`) and 400 bytes on `armeabi-v7a` (`14,520,052` -> `14,520,452`). No dependency, Java library, resource, or additional ABI was added.
- The two `libplayer.so` assets remain byte-identical to baseline HEAD: Git blobs `4a047ea474760421666d69687d1e05b284708b93` (`arm64-v8a`) and `e99bbbd363d695255304c9fa8cb0c6918c2df2c1` (`armeabi-v7a`).
- `:app:assembleMobileArm64_v8aDebug` passed (`103` tasks, `9` executed). `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` has SHA-256 `ad57a1d453281f21921f5898724966aae61aab5c8574537c7729afbc82da39b8` and was installed through the OEM installer-assist workflow.

### Connected-device playback checkpoint: 2026-08-29 09:36 CST

- Device: vivo `V2453A`, Android 15/API 35, serial `10CF6H1D2L0009S`; App process `2774` remained alive in `VideoActivity` for the complete run. Evidence is stored under `/private/tmp/p3-device-20260829/`.
- The active route was the phone speaker (`deviceId=3`), not HDMI/eARC/USB. This verifies stable decode-to-PCM fallback and AudioTrack lifecycle on API 35, but it does **not** claim that an AVR accepted DTS-HD/TrueHD IEC61937 passthrough.
- Command-driven fixtures from `/storage/emulated/0/Download/声道测试/` reached `PLAYING`: DTS-HD MA 7.1 and repaired 7.1 (`48 kHz`, `0x63f`), DTS-HD MA 5.1 (`48 kHz`, `0x3f`), TrueHD 7.1 (`48 kHz`, `0x63f`), E-AC3/Atmos 7.1.4 (`48 kHz`, `0x63f`), LPCM 7.1 (`48 kHz`, `0x63f`), and AC3 5.1 (`48 kHz`, `0x3f`). DTS core/DTS:X/AAC/other adjacent entries also changed media without terminating the process.
- Pause/resume retained the DTS-HD MA 5.1 AudioTrack session `17689`; seek on DTS-HD MA 7.1 retained session `17697` and the `0x63f` format. Stable TrueHD session `18137` and E-AC3/Atmos session `18177` each created one AudioTrack for the item rather than entering a rebuild loop.
- Final AudioFlinger evidence for session `18177` reports one active track at 48 kHz, mask `0x63f`, and `Underruns=0`. Final MediaSession remained `PLAYING`; logcat contained no App crash, ANR, fatal native signal, or AudioTrack initialization failure.
- Residual matrix: no explicitly identified DTS-HD HRA fixture, no API 29/30 device, and no HDMI/ARC/eARC/USB route or route hotplug were available. The pure Java tests cover the HRA stereo-carrier rule; actual receiver format display and direct-playback failure fallback remain follow-up hardware validation and do not block the conservative phone fallback release.
- Exactly one next action: keep P3 code frozen; when an HDMI/eARC/USB receiver route is available, run the remaining passthrough/HRA/hotplug hardware matrix and append only its evidence here.

### Commit and rollback closure: 2026-08-29 09:40 CST

- Atomic implementation commit: `d82336bde585b62af43771284075a0a94a3d999e` (`feat(mpv): support DTS-HD MA AudioTrack carriers`).
- Annotated recovery tag: `recovery/P3/20260829094014-d82336bde585`; task guard created it immediately after the commit in 0 seconds.
- The commit contains only the declared P3 Java/native patch/build/verifier/assets/tests/documentation paths. Pre-existing `AGENTS.md` remains uncommitted and protected; generated `app/.cxx/` was moved outside the worktree to `/private/tmp/p3-app-cxx-20260829/`.
- Rollback is the commit revert or the recovery tag. The App probe, native patch, build markers, tests, and both ABI `libmpv.so` assets must roll back together; `libplayer.so`, dependency locks, Exo, FFmpeg, libplacebo, and mpv-android remain outside the rollback unit.
