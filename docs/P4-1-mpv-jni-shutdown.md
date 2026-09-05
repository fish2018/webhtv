# P4-1: MPV JNI shutdown/lifecycle serialization

## Recovery anchor

- Objective: put MPV shutdown behind the existing command/video-Surface/OSD-Surface mutation FIFO so rapid switch, release, and Surface recreation remain ordered and recoverable.
- User decision: approved for implementation on 2026-08-29; scope is limited to the narrow JNI shutdown adaptation.
- Baseline: branch `fongmi-sync`, HEAD `052206a133640f209177cc84640931bdcf46926e`, baseline tag `recovery/P4-1-MPV-JNI-SHUTDOWN/baseline-20260829100803-052206a13364`.
- Protected dirty path: `AGENTS.md`; it must remain outside the task commit.
- Scope: `third_party/mpv-player-jni/src/main.cpp`, `request.cpp`, `request.h`, both ARM `libplayer.so` assets, this document, and the existing assessment index only if its status needs closure.
- Excluded: MPV/FFmpeg/libplacebo locks and sources, `libmpv.so`, Exo/Media3, renderer/decoder/audio policy, and unrelated Java behavior.
- Rollback: restore the baseline tag or revert the atomic P4-1 commit; source and both ABI `libplayer.so` assets roll back together.
- Current status: JNI queue adaptation, two-ABI native build/ELF verification, and Mobile arm64 APK build passed; device lifecycle validation is blocked by no attached ADB device.
- Exactly one next action: after an authorized Android device appears, install the recorded APK and run the lifecycle matrix before committing/tagging.

## 1. User-visible capability

This is a lifecycle stability fix, not a new format or quality feature. When a user changes episodes quickly, exits playback, backgrounds the app, or a video/OSD Surface is recreated, the shutdown request waits behind already submitted mutations instead of racing them. This reduces intermittent black screens, stuck exits, failed context recreation, and native lifecycle crashes without adding work to frame decoding or rendering.

## 2. Source identity and provenance

| Repository | Full commit | Disposition and relationship |
| --- | --- | --- |
| `FongMi/mpv-android` | `f4c5d614d5f68d483b2e1889ffad11e513b877d2` | Original audited shutdown-serialization commit |
| `FongMi/mpv-android` | `70252056460ab3983762e0a7090f59446f004279` | Force-push/rebase equivalent of the shutdown commit; parent `042f08d93e694e940ec81f61c0f7ffb1005f37b8` |
| `FongMi/mpv-android` | `b3c4cf87f71c9a62b343ea6bbcccdcc1520edd8f` | Lifecycle/Java interop prerequisite; equivalent rebased identity `fc92307825eae205e3f2b4cf3ef4a204ded81eca` |
| `FongMi/mpv-android` | `eabfaf9501fc08fb726953a9328da43ae4154d35` | Current `fongmi` head; FFmpeg 9.0 build pointer only, no shutdown replacement |
| `FongMi/mpv-android` | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | WebHTV locked build framework; retain as the current build input |
| `FongMi/mpv` | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | Locked MPV source; unchanged by P4-1 |
| `FongMi/FFmpeg` | `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | Locked MPV FFmpeg; unchanged by P4-1 |
| `FongMi/libplacebo` | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | Locked static libplacebo; unchanged by P4-1 |

The local queue and Java event bridge already absorb the prerequisite command/Surface and lifecycle commits. Only the missing `SHUTDOWN` queue item is being adapted; the upstream files are not copied wholesale.

## 3. Best-practice review and design

`mpv/client.h` documents asynchronous command replies and recommends sending `quit` and waiting for `MPV_EVENT_SHUTDOWN`; `mpv_terminate_destroy()` must not race other calls on the same context. The selected design therefore serializes shutdown with all other MPV mutations and preserves the existing forced-wakeup fallback.

Alternatives:

- No change: keeps current playback behavior but leaves `destroy()` racing queued commands and Surface updates.
- Unmodified upstream replacement: rejected because WebHTV has custom OSD request IDs, dual-Surface ownership, ISO controls, END_FILE/error propagation, and Android 15 diagnostics.
- Narrow WebHTV adaptation: selected. Add `SHUTDOWN` to the existing FIFO, reject new requests after shutdown is queued, and report canceled external request IDs during final cleanup.

Contracts that must survive:

- command, video Surface, and OSD Surface FIFO ordering;
- Surface global-reference ownership and one-time cleanup;
- `eventCommandReply`, END_FILE/error bridge, ISO controls, and Java context-shutdown timeout;
- `g_force_shutdown + mpv_wakeup` fallback and no changes to `libmpv.so` behavior.

## 4. Implementation and validation plan

1. Add `SHUTDOWN`, `enqueue_shutdown()`, command-event classification, and FIFO shutdown dispatch.
2. Remove only the shutdown-blocking check that would prevent the queued shutdown from starting; retain rejection for new external requests.
3. Make `release_requests()` notify external in-flight/pending IDs with `MPV_ERROR_UNINITIALIZED` before dropping them, while never notifying the internal shutdown ID.
4. Rebuild only `libplayer.so` for `arm64-v8a` and `armeabi-v7a` with `scripts/build_mpv_player_jni.sh --abi all --install`.
5. Run JNI/ELF checks, an affected Mobile arm64 build, and command-driven lifecycle playback: create/play/seek, in-flight command, dual Surface attach/detach, rapid switch, repeated destroy/recreate, and Android 15 crash/ANR log inspection.

Acceptance requires both ABI JNI builds, unchanged `libmpv.so`/FFmpeg/libplacebo assets, no crash/ANR/destroyed-mutex signal, bounded shutdown completion, and every external pending request receiving completion or explicit cancellation.

## Checkpoint 1: implementation start

- Recorded: 2026-08-29 10:08 CST.
- Workspace: `fongmi-sync` at `052206a133640f209177cc84640931bdcf46926e`; protected `AGENTS.md` remains dirty and untouched.
- Baseline recovery tag: `recovery/P4-1-MPV-JNI-SHUTDOWN/baseline-20260829100803-052206a13364`.
- Next action: patch `main.cpp`, `request.cpp`, and `request.h` only.

## Checkpoint 2: JNI adaptation

- Recorded: 2026-08-29 10:09 CST.
- Changed: `main.cpp` now calls `enqueue_shutdown()`; `request.cpp` adds the internal `SHUTDOWN` FIFO request, allows it to start after shutdown is marked, preserves force-wakeup on dispatch failure, and reports canceled external IDs during cleanup; `request.h` exposes `enqueue_shutdown()`.
- Preserved: existing command/video/OSD ordering, Surface global references, Java event bridge, ISO controls, END_FILE/error bridge, and all MPV/FFmpeg/libplacebo assets.
- Validation so far: `git diff --check` and task-guard scope check passed.
- Next action: run `scripts/build_mpv_player_jni.sh --abi all --install` once, then verify both installed `libplayer.so` files.

## Checkpoint 3: JNI build and asset verification

- Recorded: 2026-08-29 10:10 CST.
- Build: `bash scripts/build_mpv_player_jni.sh --abi all --install` passed for both ABIs.
- Asset hashes/sizes: `arm64-v8a/libplayer.so` `977f78ed786ed8b305f5625929e7e042ae03fa55eb6aaf1bb24e27086400ea41` / 92,120 bytes; `armeabi-v7a/libplayer.so` `6bb52e833b94e930068ad1f9f678d691a1b60ca99ee99658a11d42134df687f1` / 58,460 bytes.
- Baseline delta: 856 bytes (arm64) and 872 bytes (armv7). `libmpv.so` remains byte-identical to baseline (`04cfe3ae40118ec77b988323791925b67014b7dda33fdbe54848db8ff219c9a5` / `d13da6308db9c6d1757a8c71928284efd83e9de82a14e5465293fc297ab2cc75`).
- Verification: `bash scripts/verify_mpv_native_assets.sh --require-elf` passed for both ABIs; `git diff --check` passed.
- Next action: build Mobile arm64 Debug APK and install it for lifecycle smoke tests.

## Checkpoint 4: APK build

- Recorded: 2026-08-29 10:13 CST.
- Build: `bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` passed in 1m24s (`103` tasks, `9` executed).
- APK: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`, SHA-256 `3af5ccf9b75c9c4c6c654ba59d76ff226a4319e3705853400fa88326c64a69de`, size 180,377,248 bytes.
- Generated CXX intermediates were moved to `/private/tmp/p4-1-app-cxx-20260829/` and are outside the worktree.
- Device gate: `adb devices -l` currently reports no attached device, so install and lifecycle playback are not yet verified.
- Next action: when an authorized Android device appears, install this APK with installer-assist and run the command-driven lifecycle matrix; do not commit/tag before that required behavior check.

## Checkpoint 5: device validation blocker

- Recorded: 2026-08-29 10:14 CST.
- Completed evidence: JNI source compile, both ABI `libplayer.so` install, full MPV native ELF/asset verification, and Mobile arm64 Debug APK build all passed.
- Blocker: two `adb devices -l` checks report no attached or authorized device. No APK install, playback, shutdown, rapid-switch, Surface recreation, or Android 15 crash-buffer result is claimed.
- Worktree: task guard remains active; `AGENTS.md` is the only protected pre-existing dirty path. Generated `app/.cxx` is outside the worktree at `/private/tmp/p4-1-app-cxx-20260829/`.
- Rollback anchor: `recovery/P4-1-MPV-JNI-SHUTDOWN/baseline-20260829100803-052206a13364`.
- Next action: connect/authorize one Android device, install the recorded APK, execute the lifecycle matrix, then close the task guard only if it passes.

## Checkpoint 6: Android 15 lifecycle validation

- Recorded: 2026-08-29 10:31 CST.
- Device: vivo `V2453A`, serial `10CF6H1D2L0009S`, Android 15 / API 35, `arm64-v8a`.
- Install: installer-assist installed `app-mobile-arm64_v8a-debug.apk` successfully; installed package `com.fongmi.android.tv` reports version `5.6.0` (`versionCode=560`) and `primaryCpuAbi=arm64-v8a`.
- Inputs: local files from `/storage/emulated/0/Download/声道测试/`, including DTS-HD MA 5.1/7.1, Dolby Atmos DD+ 7.1.4, AC-3 5.1, and `sub-cross.mkv`.
- Command-driven matrix: MPV playback start, media seek/fast-forward, Home/background then foreground Surface recreation, rapid local-file switch, seek followed immediately by Back, process stop during an active playback cycle, cold recreate, and a final graceful Back/destroy.
- Result: playback and repeated recreate completed without app crash, ANR, native fatal signal, destroyed-mutex report, or use-after-free evidence. New MPV instances waited for the previous MPV/HWUI teardown (`339-350ms`) instead of overlapping it.
- Shutdown evidence: after enabling verbose logging only for the native `mpv` tag, the focused play/seek/Back run emitted `V mpv: event: shutdown` at `2026-08-29 10:31:13.102`, confirming the queued quit reached `MPV_EVENT_SHUTDOWN` and completed the event-thread cleanup path.
- Logs: `/private/tmp/P4-1-mpv-jni-shutdown-device-20260829.log` and `/private/tmp/P4-1-mpv-jni-shutdown-verbose-20260829.log` (local validation artifacts, not committed).
- Residual risk: device coverage is one Android 15 arm64 handset; armeabi-v7a is build/ELF verified but not device exercised. Forced process death cannot prove graceful JNI ordering by itself, so the decisive evidence is the separate graceful Back run with the native shutdown event.
- Acceptance: satisfied for the approved P4-1 stage. Next action: run the final task-guard safety check, commit the task-owned source/binary/document changes atomically, and create the annotated recovery tag immediately.

## Closure

- Status: implemented and verified.
- Implementation commit: `907bfca982a4b1d4d9ee0eeddd05d02226b8f9bb` (`fix(mpv): serialize JNI shutdown requests`).
- Recovery tag: `recovery/P4-1-MPV-JNI-SHUTDOWN/20260829103212-907bfca982a4`.
- Rollback: restore the implementation commit's first parent `052206a133640f209177cc84640931bdcf46926e`, or use the recovery tag to inspect/reapply the verified P4-1 state.
- Remaining risk: no armeabi-v7a physical-device lifecycle run; both ABI binaries passed the native build and ELF/asset checks, and the arm64 Android 15 runtime matrix passed.
- Next action: none for P4-1; select the next unfinished task from the master assessment in the required Exo -> MPV -> common order.
