# P2-1: MPV Vulkan generic UV precomputation

## Recovery anchor

- Objective: move frame-invariant crop/normalization division out of MPV's generic Android Vulkan compute/fragment shaders while preserving every current WebHTV backend, AImage, fence, Dolby Vision, ABI, packaging, and performance contract.
- Acceptance: only the generic converter shader contract changes; both ARM ABIs rebuild from the unchanged lock; shader/SPIR-V, ELF/package, focused rendering/fallback, and no-regression checks pass; the implementation commit and recovery tag are recorded here.
- Status: complete; implementation commit `fe4184933fbb3a02bd1ff2ff794a277123c35bdc` and recovery tag recorded below.
- Task/lane: `P2-1-MPV-VULKAN-GENERIC-UV` / `upstream`.
- Workspace baseline: branch `fongmi-sync`, HEAD `9fcab83f9084446566240a8e8f5233d87d0274cc`, clean worktree.
- Baseline recovery tag: `recovery/P2-1/baseline-20260828-2219`.
- Protected pre-existing dirty paths: none.
- Excluded: FFmpeg/C0-M, libplacebo upgrades, P2-2 DV7 changes, App/JNI APIs, backend selection, stable conversion, AImage acquire/release flow, release-fence policy, and unrelated native dependencies.
- Next action: wait for explicit user direction before assessing or implementing another upstream task.

## 1. Decision and source identity

The approved stage is the five-file residual recorded in the master assessment checkpoint 38, not the complete Android Vulkan interop commit.

| Identity | Full commit/tree | Role and disposition |
| --- | --- | --- |
| Assessed FongMi/mpv source | `f5c9f148d00db652da1ee900f386d8e0e615ed84`, parent `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`, tree `49376fbe987a833dd1c8e41a18f428e900a48474` | Original approved source; stable patch-id `ebb2a24858351b9815717d9fd146e3949a72e8f6` |
| Refreshed FongMi/mpv equivalent | `e3e71ed793fbba1c6994726bfa5346ae6073bb5b`, parent `34663ec8415eae0a4ee946f9f793e5610238ec88`, tree `732da1b81cf347301b8f7e3e2f671b24078206c3` | 2026-08-28 force-push replacement; same title, timestamp, and stable patch-id `ebb2a24858351b9815717d9fd146e3949a72e8f6`; no new behavior is added to this task |
| Refreshed branch head | `2477400b9732a8cf63951ff66cdf3a948e7a0822` | Observed `FongMi/mpv@fongmi` head on 2026-08-28; later history has no UV follow-up or revert |
| Locked-line equivalent | `cb007d6f6b520ff57a4bedd5f8bcd330f64c88a0`, parent `62648ab1789c6e1b025fe9392857385f37314710`, tree `20c10c0e55654ac95125a7b4ead6fbc319a80c83` | Supplies the Android Vulkan interop body; stable patch-id `274120b84448030dc4406782c8820f0f702b3ce1`; UV residual remains absent |
| Current WebHTV mpv pin | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, tree `bb1d0974d85eea46dfec90bddb9c9e1392765ea7` | Actual source rebuilt by `scripts/build_mpv_native.sh`; generic shaders still use per-pixel division |

The refreshed hash does not change approval scope. The stable patch-id proves that `e3e71ed...` is the current identity of the already assessed `f5c9f148...` change. Implementation provenance records both full IDs and ports only the approved UV invariant.

## 2. Current WebHTV flow and gap

- `auto` remains direct -> stable -> generic. Direct sampling does not use a conversion shader.
- The four-output stable override already pushes `uv_offset`, `uv_scale`, and `output_size`; its CPU uses double intermediates and the shader performs one multiply/add sequence per pixel.
- The pinned generic compute and fragment shaders still push integer crop/source geometry and perform crop scaling plus source normalization division for every output pixel.
- Explicit `legacy`, `compute`, and `fragment`, plus stable initialization failure to generic, therefore retain the gap.
- `mpv-android-vulkan-conversion-default.patch`, `mpv-android-vulkan-smart-backend.patch`, `mpv-android-vulkan-legacy-backend.patch`, and `mpv-aimagereader-stable-flow.patch` are preservation contracts, not replacement candidates.
- P1 commit `a5971e3814d3b0826a5702d607dd6d1675b2ce53` rebuilt the same MPV pin but added only format/EBML/HLS/libplacebo correctness patches; it did not implement this generic UV change.

## 3. Alternatives and recommendation

| Alternative | Decision |
| --- | --- |
| No change | Correct and safe, but leaves redundant per-pixel division in generic fallback and forced conversion modes. |
| Adopt `f5c9...`/`e3e71...` wholesale | Rejected. It would collide with later WebHTV backend selection, four-output pool, release-fence disablement, AImage lifetime, bounded acquire, P1, DV, and disc changes. |
| Narrow WebHTV adaptation | Approved. Port only the two shader contracts, C push constants/CPU calculation, and generated SPIR-V headers; add deterministic source/header verification and a unique asset marker. |

This is best practice for the current project because the divisions depend only on frame geometry, not on pixel identity. Precomputing them once per frame removes redundant GPU ALU without changing the algebra or the default direct/stable path. WebHTV must adapt the upstream change rather than replace its surrounding files.

## 4. Best-practice evidence

| Evidence class | Source/revision/access | Grade | Decision impact |
| --- | --- | --- | --- |
| Exact upstream source/history | `FongMi/mpv` commits and trees above, fetched 2026-08-28; master assessment checkpoint 38 | A | Establishes the five-file residual, old/new force-push equivalence, and absence of later UV reverts/fixes |
| Current local source/data flow | `third_party/mpv-native-lock.json`, Vulkan patches/override, `scripts/build_mpv_native.sh`, both packaged `libmpv.so`, inspected 2026-08-28 | A | Confirms stable-only coverage and identifies backend/fence/AImage contracts that cannot change |
| Vulkan official guidance | `https://docs.vulkan.org/guide/latest/push_constants.html`, accessed 2026-08-28 | A | Requires C and GLSL push-constant size/layout/stage agreement; supports explicit contract checks |
| Shader compiler documentation | `google/shaderc` `glslc/README.asciidoc`, current main, accessed 2026-08-28 | A | Supports deterministic shader-stage, target-environment, optimization, and generated SPIR-V output commands |
| mpv maintainer guidance | `FongMi/mpv@2477400b9732a8cf63951ff66cdf3a948e7a0822:DOCS/contribute.md`, accessed 2026-08-28 | B | Requires a logical, attributable, tested patch and disclosure of verification scope |
| Mature related implementation | WebHTV stable Vulkan override at baseline HEAD; upstream generic implementation `f5c9...`/`e3e71...` | A | Two independent implementations use the same formula and double-intermediate CPU calculation |
| PR/issues/reverts/discussion | Exact file history through refreshed head; only later backend-selection commit `6e7f2db65d654dad588fb02e6366498bb256adc8` touches the files, with no UV revert | B | No known upstream regression requires altering the approved formula; backend selection remains out of scope |
| Papers/blogs/field reports | Inapplicable to the decision: this is algebraic invariant hoisting, not a new rendering algorithm; performance magnitude is device-specific and will be measured locally | N/A | Prevents unsupported universal speed/power claims; acceptance is no regression plus measured generic-path evidence |

## 5. Implementation design

1. Add a standalone MPV patch after the existing Vulkan backend/lifetime patches and before unrelated P1 patches.
2. Change generic compute and fragment shaders from integer crop/source geometry to `vec2 uv_offset`, `vec2 uv_scale`, and `ivec2 output_size`.
3. Keep full integer geometry separately for the existing diagnostic log. Calculate offsets/scales with double intermediates and cast once to float.
4. Generate both checked-in C headers from the patched shader sources with the locked shader compiler and validate the SPIR-V target; never hand-edit byte arrays.
5. Extend the existing shader-contract checker to cover stable and generic layouts and generated-header provenance. Add a generic-only marker so packaged binaries prove that this task, not only stable conversion, is present.
6. Rebuild the unchanged locked graph for `arm64-v8a` and `armeabi-v7a`; install only the resulting verified asset set. `libplayer.so` remains unchanged.

## 6. Acceptance, performance, and rollback

- Static acceptance: the patch applies after all existing patches; C/compute/fragment layouts match; generated headers come from source; `spirv-val` passes; no backend, queue, fence, AImage, DV, audio, network, or App/JNI hunk changes.
- Native acceptance: both ABI builds complete; `bash scripts/verify_mpv_native_assets.sh --require-elf` passes with unchanged source locks, SONAME/`DT_NEEDED`, package manifest, and existing markers plus the generic marker.
- Rendering acceptance: explicit compute, fragment, and legacy cover odd crop/non-integer scaling/rotation where available; auto direct and stable smoke remain unchanged; subtitles/OSD/LUT and at least one HDR or raw-DV conversion case show no coordinate or component regression.
- Performance acceptance: on the same device/sample/settings, generic-path startup, dropped/late frames, and observed frame timing must not regress. Any improvement is reported only from comparable runs; direct/stable behavior must remain byte/source unchanged apart from relinking.
- Package/ABI acceptance: no new library, export, JNI/API, or dependency version; size growth is limited to diagnostic/generated-code variance and recorded for both `libmpv.so` files.
- Rollback: restore baseline tag `recovery/P2-1/baseline-20260828-2219`, or revert the atomic P2-1 commit and its two ABI assets. No other player stage is coupled to this rollback.

## 7. Approval record

The user explicitly approved implementation after reviewing the P2-1 assessment, with the condition that existing functionality and performance must not be directly or indirectly degraded. P2-2, C0-M, and all other upstream tasks remain unapproved for this unit.

## Checkpoint 1: 2026-08-28 22:34 CST, source and shader gate

- Completed: created the baseline recovery tag; refreshed `FongMi/mpv@fongmi`; mapped force-pushed `f5c9f148...` to patch-identical `e3e71ed...`; added the three-file P2 source patch, deterministic two-header generation, generic/stable contract checks, packaged marker checks, and build documentation.
- Source identities: baseline pin `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`; approved source `f5c9f148d00db652da1ee900f386d8e0e615ed84`; refreshed equivalent `e3e71ed793fbba1c6994726bfa5346ae6073bb5b`; refreshed head `2477400b9732a8cf63951ff66cdf3a948e7a0822`; stable patch-id `ebb2a24858351b9815717d9fd146e3949a72e8f6`.
- Files changed so far: this task document, master index, `scripts/build_mpv_native.sh`, `scripts/verify_mpv_native_assets.sh`, `scripts/verify_mpv_vulkan_shader_contract.py`, `third_party/mpv-native-build.md`, and `third_party/patches/mpv-p2-generic-uv.patch`. Native assets are not yet changed.
- Validation passed once: `bash -n scripts/build_mpv_native.sh`; repository shader-contract check; reverse and forward P2 patch application after the complete existing 15-patch stack; NDK r29 `glslc` compute/fragment compilation; Vulkan 1.2 `spirv-val`; prepared-source generic contract including legacy, release-fence disablement, and AImage lifetime preservation; `git diff --check`; task-guard safety check.
- Generated shader evidence: compute SPIR-V SHA-256 `b36d0fc510d31c6d54ddff181386f295a43ec31d4001fb85efb1c4d4284fade7`; fragment SPIR-V SHA-256 `46c42820dddab594b38767077f8b9e04b1cdc37bb868c89cd8e1d183de572e1a`.
- Rollback anchor: `recovery/P2-1/baseline-20260828-2219` at `9fcab83f9084446566240a8e8f5233d87d0274cc`.
- Unresolved: two-ABI compile/link/package identity, final asset hashes/size delta, and runtime generic/direct/stable behavior.
- Next action: run one clean `--abi all --install` native build using the unchanged lock and capture its single complete output.

## Checkpoint 2: 2026-08-28 23:09 CST, native build storage failure

- Completed before failure: source preparation, the complete P2 shader/contract gate, and the clean arm64 dependency build through FFmpeg, fontconfig, FriBidi, HarfBuzz, libunibreak, libass, Lua, and almost all shaderc objects.
- Failure classification: environment storage exhaustion, not a source, compiler, shader, ABI, or P2 contract failure. NDK r29 `llvm-ar` failed while combining `libshaderc_combined.a` with `No space left on device`; the data volume had 62 MiB free.
- Preserved state: the active guard, baseline HEAD/tag, task-owned source edits, generated shader hashes, and the successfully installed arm64 prefix remain intact. No App asset was replaced and both committed ABI directories remain at their baseline contents.
- Recovery evidence: the wrapper supports `--incremental`; completed dependency outputs live under the arm64 prefix and the failed build workspace occupies about 3.8 GiB. Previously completed WebHTV task caches under `/private/tmp` provide enough reclaimable space without deleting repository source, packaged assets, or unrelated user files.
- Verification impact: the failed attempt is not acceptance evidence. Final acceptance still requires both ABI outputs, `verify_mpv_native_assets.sh --require-elf`, package identity, and focused Vulkan runtime checks.
- Rollback anchor: unchanged at `recovery/P2-1/baseline-20260828-2219` / `9fcab83f9084446566240a8e8f5233d87d0274cc`.
- Next action: remove only the identified completed-task temporary caches, then resume arm64 with the unchanged lock and `--incremental` rather than repeating completed compilation.

## Checkpoint 3: 2026-08-28 23:44 CST, two-ABI native candidate

- Completed: removed only the completed-task temporary caches recorded in checkpoint 2, resumed the preserved arm64 prefix, completed a clean armv7 build, installed both ABI asset sets, and ran the required ELF/lock/package verification once.
- Build command/result: `env https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897 scripts/build_mpv_native.sh --abi all --install --jobs 8 --incremental`; exit 0. Both `hwdec_aimagereader_vk_convert.c` objects compiled and both `libmpv.so` targets linked. Existing compiler warnings were non-fatal and outside the P2 hunk.
- Candidate asset identities:

| ABI / file | Baseline SHA-256 / bytes | Candidate SHA-256 / bytes | Size delta |
| --- | --- | --- | ---: |
| arm64-v8a `libmpv.so` | `69e9a8d10560a41107680ca4de737996885f2f37fa353fd5ede30334866eeb7b` / 17,714,112 | `f050af3c3d1ffb814b48bd50963fdde33cd07e38ad0ff025a62b4e31228bc480` / 17,714,032 | -80 |
| arm64-v8a `libmvcodec.so` | `dd0df8e451f34d1f1f04e829d5e3c54415ecfd100e1da185ba99400672a356ce` / 15,315,384 | `3aeeff9d487b6dc0b63e804bb2563b71d6a589d109bb5dcde9fc1a7fdbdf912f` / 15,315,464 | +80 |
| arm64-v8a `libmvformat.so` | `d452ca2a0ac2d81eb31176b454d0a4c641f4ae776c4b2549dac0f705c0816b77` / 4,497,672 | `a898c68026344705451507395315173cc663d754463eb0813c5aff8ce0207ee5` / 4,497,688 | +16 |
| armeabi-v7a `libmpv.so` | `ec2cbc58616bb383eb2f80d3fde883da02036b00062287a6f956ebda1aeb5ce3` / 14,519,876 | `7ed0c6aa8f674031588e0b649f5766954a1e3a8caf4bffb76caaa7de63e82cd7` / 14,519,652 | -224 |
| armeabi-v7a `libmvcodec.so` | `057737104206f1091f1e498f09112f6fa4786a126b1699d477a2a3f9227c53ef` / 14,470,380 | `28b6227211a13123c58fb0921e2c1c76c2e9d1d4189898d9a94075c592f72547` / 14,470,380 | 0 |
| armeabi-v7a `libmvformat.so` | `fb64b119a50abdf5d83b7b7c3283b25bcaefa0c86a440eab806a7aba1f06ad1a` / 4,230,756 | `f9b9ad3289c47fe26a303fb07a66fec16ab5ed82383c7f967126e4144f296a0e` / 4,230,788 | +32 |

- Preserved binary contract: arm64-v8a `libplayer.so` remains `aedfcb5bcce929cd08bdd113e2031945efc514f3ec4e21daaa39c5744d941bff` / 91,264 bytes; armeabi-v7a remains `d146b4f7b5aa95f6768c5bae981bd2f01aaa5166e36d28e342b3789e0233b4b4` / 57,588 bytes.
- Validation: `bash scripts/verify_mpv_native_assets.sh --require-elf` passed for both ABIs using NDK r29 `llvm-readelf`; lock versions, source/patch markers, stable and generic shader contracts, ELF identity, namespace separation, and packaging rules match. The rebuilt FFmpeg shared objects show only the recorded small relink variance and no version/contract change.
- Performance status: the implementation removes per-pixel divisions only from forced/fallback generic conversion; direct and stable source paths are unchanged. Compilation and ELF evidence do not prove runtime performance, so candidate acceptance still requires focused same-device playback/diagnostic checks and no observed regression.
- Rollback anchor: unchanged at `recovery/P2-1/baseline-20260828-2219` / `9fcab83f9084446566240a8e8f5233d87d0274cc`; the candidate assets are uncommitted and can be discarded only as the complete task-owned set if validation fails.
- Unresolved: APK asset identity and connected-device generic compute/fragment/legacy plus direct/stable neighbor behavior.
- Next action: build the `mobileArm64_v8a` debug APK once and verify the packaged arm64 asset hashes before installation.

## Checkpoint 4: 2026-08-29 00:14 CST, APK and focused device acceptance

- APK build/package: `bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` passed once in 2m 2s. `app-mobile-arm64_v8a-debug.apk` is 157,579,720 bytes with SHA-256 `1f992ddff117eba2c3c4ab8dea7ebd0f531551c6c358f05a6285244f14f90227`; its four arm64 MPV assets match the candidate workspace hashes exactly.
- Device: vivo V2453A / PD2453, serial `10CF6H1D2L0009S`, Android 15, `arm64-v8a`, 1080x2400 panel. The existing App data was retained during installation. Runtime checks used Vulkan `gpu-next`, hard decode, and the same candidate APK throughout.
- Decisive sample: `/sdcard/Download/杜比视界测试/hdr测试.mp4`, AV1 3840x2160 at 59.940 fps, HDR10 BT.2020/PQ, approximately 12.2 Mbps. All accepted runs reported `av1_mediacodec`, `actualDecode=hardware`, `hwdec=mediacodec`, and a Vulkan render path. The portrait device layout scales the 16:9 video into a non-integer display viewport and exposed no component, crop-coordinate, or color-placement error.
- Test-only backend selection: the formal App UI exposes direct/legacy/stable, while the current pre-init libmpv option takes precedence over `mpv.conf` for compute/fragment. A uniquely named temporary Lua script set the native option at `start-file` solely for this test; it was not added to the repository or APK and was removed after testing. This existing App selection limitation is outside P2-1 and does not block the reachable legacy/fallback generic path.

| Requested path | Native evidence | Timing evidence under thermal status 2 | Visual/result |
| --- | --- | --- | --- |
| `compute` | backend `compute`; generic CPU-precomputed UV marker; crop `0,0 3840x2160`, compute output `3840x2160` | first frame 535 ms; after about 20 s: decoder drops 0, output drops 5, mistimed 0, delayed 0, rebuffer 0 | HDR image rendered normally; no black frame, UV/component swap, offset, or scaling error |
| `fragment` | backend `fragment`; generic marker; matching fragment geometry | first frame 1848 ms after saved-position seek; after about 20 s: decoder/output drops 0, mistimed/delayed 0 | normal image and color; no coordinate error |
| `legacy` | backend `legacy`; generic marker; selected compute conversion for the device format | first frame 1877 ms after saved-position seek; after about 20 s: decoder/output drops 0, mistimed/delayed 0 | normal image and color |
| `stable` | backend `stable`; existing four-output bounded-fence pool and stable CPU-precomputed transform markers | first frame 1828 ms after saved-position seek; after about 20 s: decoder/output drops 0, mistimed/delayed 0 | unchanged neighboring path rendered normally |
| `auto/direct` | backend `auto`; log confirms direct AHardwareBuffer sampling preference with no stable/generic fallback | first frame 2025 ms after saved-position seek; after more than 30 s: decoder/output drops 0, mistimed/delayed 0 | unchanged default path rendered normally |

- Performance interpretation: the runs were performed while the USB-connected phone remained at Android thermal status 2, so first-frame values are diagnostic only and are not used to claim a speedup. The compute run's five output drops over roughly 1200 4K60 frames did not continue as a decoder, sync, or rebuffer failure; the other four paths stayed at zero. The source change removes per-pixel divisions and adds no work, while direct/stable source paths are unchanged, so there is no evidence of a P2-1 performance regression. A numeric power or speed gain is intentionally not claimed without a frozen baseline run.
- Additional DV evidence: `P81_GlassBlowing2_3840x2160@59_94fps_15200kbps.mkv` rendered HDR/DV colors and geometry correctly, but this device decoded that Profile 8 stream in software. It therefore remains visual stress evidence only and is not counted as AImageReader backend acceptance.
- Failure/lifecycle result: the accepted traces contain no AImageReader acquisition timeout, crash, ANR diagnostic, slow libmpv call, destroyed-mutex signal, black screen, or playback rebuffer. Audio continued through Android AudioTrack.
- Coverage limit: no reliable odd-crop/rotated MediaCodec fixture was available; the observed decoder crop was full-frame. Static source/header/SPIR-V contract checks cover the changed formula and layout, while device evidence covers full-frame 4K60 HDR, hard decode, non-integer viewport scaling, and all neighboring backends. Runtime coverage is arm64 on one Qualcomm device; armv7 remains build/ELF/package verified.
- User-state restoration: the original preference XML and `mpv.conf` were restored byte-for-byte with SHA-256 `c0951653ec2806a28c50cf6382a2c5933f78dcd5fcd539a2847daeb9fe6a23e0` and `941af30205752147c8ef4f1931a8d5dc15478fe3fcf5c8e3bd8fa788031b9282`. The temporary Lua script and `/data/local/tmp` inputs were removed; a cold restart returned to the original MPV/Vulkan/automatic-output/non-verbose settings.
- Acceptance: static shader/SPIR-V, two-ABI native build, ELF/package checks, APK identity, and focused device behavior all pass. P2-1 does not change App/JNI, versions, dependencies, backend policy, stable/direct code, AImage/fence ownership, DV policy, or `libplayer.so`.
- Rollback: revert the pending atomic P2-1 commit or restore `recovery/P2-1/baseline-20260828-2219`; both ABI asset sets, the source patch, build/verification scripts, and documentation move together.
- Unresolved risk: measured improvement magnitude and odd-crop/rotation behavior are not quantified on this device; no observed failure requires broadening the approved task.
- Next action: close the task record with the generated implementation commit and recovery tag; do not start P2-2 or C0-M without separate approval.

## Closure: 2026-08-29 00:36 CST

- Implementation commit: `fe4184933fbb3a02bd1ff2ff794a277123c35bdc` (`mpv: precompute generic Vulkan UV transforms`).
- Recovery tag: `recovery/P2-1-MPV-VULKAN-GENERIC-UV/20260829003632-fe4184933fbb`, annotated locally at the implementation commit; tag creation completed in 0 seconds.
- Committed unit: the narrow generic UV patch and shader-generation/verification support, both rebuilt ARM ABI asset sets, native build documentation, this task record, and the master task index. No App/JNI, lock revision, FFmpeg version, libplacebo version, backend policy, or unrelated file entered the commit.
- Verification attached to the commit: shader/SPIR-V contract, two-ABI native build, `verify_mpv_native_assets.sh --require-elf`, mobile arm64 debug APK asset identity, V2453A HDR10 hardware-decoded compute/fragment/legacy/stable/auto playback, and byte-identical user-config restoration.
- Completion decision: P2-1 is accepted. The remaining limits are one-device runtime coverage and the absence of an odd-crop/rotation fixture; neither provides evidence of a regression or justifies expanding this task.
- Rollback: revert `fe4184933fbb3a02bd1ff2ff794a277123c35bdc` or restore `recovery/P2-1/baseline-20260828-2219`. The candidate recovery tag above identifies the verified post-change state.
- Authorization boundary: P2-2, C0-M, FFmpeg/libplacebo upgrades, and all App/JNI work remain unapproved and unimplemented by this task.
- Next action: wait for explicit user direction before opening another upstream stage.
