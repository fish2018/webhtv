# P4-3: MPV terminal Surface teardown ordering

## Recovery anchor

- Objective: prevent MPV from starting disposable MediaCodec decoders after the Android video Surface has been destroyed during terminal playback exit, while preserving normal picture-in-picture, background/foreground, configuration change, decoder switch, and Surface recreation behavior.
- Lane: approved `upstream` implementation. The user approved the narrow terminal-release signal on 2026-08-29 with explicit preservation of existing behavior and performance.
- Baseline: branch `fongmi-sync`, HEAD `ea23c1dc163d29fe256ba623cc803285b3416491`; annotated rollback tag `recovery/P4-3-MPV-SURFACE-TEARDOWN-BASELINE/20260829125003-ea23c1dc163d`.
- Protected dirty path: `AGENTS.md`; it remains outside this task.
- Current evidence: C0-M device logcat, pre-C0-M device logs, current WebHTV Java/JNI flow, locked FFmpeg/mpv-android sources, Android SurfaceHolder documentation, and upstream mpv-android history.
- Status: complete. Implementation commit `8250e2204f4054601202a3a3f2fe04f8766744ee` passed the required verification and is anchored by `recovery/P4-3-MPV-SURFACE-TEARDOWN/20260829132806-8250e2204f40`.
- Next action: no P4-3 work remains; continue the upstream assessment only after a separate user request.

## 1. User-visible capability

When the user closes playback or exits picture-in-picture, the player should shut down directly instead of briefly creating and destroying new hardware video decoders after the display Surface is already gone. The fix removes avoidable exit-time decoder work and error logs, reducing the chance of a vendor MediaCodec/Surface race without changing picture quality, supported formats, normal playback, or frame-time performance.

## 2. Observed failure and root cause

The focused vivo V2453A run on Android 15 produced this terminal sequence on 2026-08-29:

1. `12:20:36.364-389`: the Surface freed dequeued buffers and its BufferQueue became abandoned.
2. `12:20:36.393-418`: the existing codec and video output began releasing and emitted video reconfiguration events.
3. `12:20:36.422` and `12:20:36.536`: FFmpeg logged `h264_mediacodec: Both surface and native_window are NULL`; each attempt created a `c2.qti.avc.decoder` component and immediately released it.
4. `12:20:36.818-822`: MPV delivered normal `end-file` and `shutdown`. There was no port-starvation precursor, Java crash, SIGSEGV, SIGABRT, or destroyed-mutex report.

This message predates C0-M in saved device logs, so FFmpeg 9.0.1 did not introduce it. The exact responsibility chain is:

- `PlaybackActivity.finishPlayback()` marks terminal exit and asks `PlaybackService.shutdown()` to stop/clear the current item, but final service/player release can occur after the Activity Surface starts disappearing.
- `MpvPlayer.surfaceDestroyed()` always calls `detachMpvSurface()`. That method queues `set vo null`, `set force-window no`, optional OSD detach, and video Surface detach.
- P4-1 correctly serializes those mutations with shutdown, but an MPV property reply does not prove asynchronous VO/decoder teardown has completed. The `vo=null` transition can therefore trigger video reconfiguration while the Android Surface has already become unusable.
- Locked FFmpeg `libavcodec/mediacodec_surface.c` logs the observed error and returns `NULL` when decoder initialization receives neither a Java `Surface` nor an `ANativeWindow`. Hiding this log would not remove the invalid initialization attempt.

## 3. Source and evidence record

| Evidence | Revision / access | Grade | Supported conclusion and caveat |
| --- | --- | --- | --- |
| WebHTV post-C0-M logcat `/private/tmp/C0-M-posthoc-logcat-20260829.txt` | device buffer captured 2026-08-29 | A, direct observation | Surface abandonment precedes two no-Surface decoder creations; normal shutdown follows. One device/API only. |
| WebHTV pre-C0-M log `/private/tmp/webhtv-dv5-auto-20260829/app-debug-log.txt` | captured before `9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223` | A, direct observation | The same FFmpeg error exists before C0-M, so this is not a 9.0.1 regression. Other occurrences also follow decoder fallback and must not be globally suppressed. |
| Android `SurfaceHolder.Callback.surfaceDestroyed()` | <https://developer.android.com/reference/android/view/SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)>, accessed 2026-08-29 | A, platform contract | After the callback returns, code must no longer access the Surface and rendering threads must no longer touch it. It does not prescribe WebHTV's shutdown API. |
| FongMi/mpv-android locked tree | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | A, exact source | `BaseMPVView.surfaceDestroyed()` uses `vo=null` then detach and explicitly states a race may remain because setting the property may not wait for VO deinit. The sample App has no separate terminal-release state. |
| mpv-android background-output history | `4e7916ea995e07ad09eb4285c2b2f23c4f891cd1`, `e185cdf53429653e3923a16f7453d7c523310319`, `cc30506e012a49ac6721baab54ee2421ad468860` | A, upstream history | `vo=null` was selected to preserve cache/background behavior, not as a terminal shutdown primitive; copying it unchanged cannot distinguish transient and terminal Surface loss. |
| mpv-android issue 1107 | <https://github.com/mpv-android/mpv-android/issues/1107>, accessed 2026-08-29 | B, maintainer issue | Background Surface/VO transitions can overwrite intended output state. It corroborates the need to preserve local VO ownership but does not supply this teardown fix. A second bounded search found no direct upstream race fix. |
| WebHTV P4-1 | `907bfca982a4b1d4d9ee0eeddd05d02226b8f9bb` | A, current local implementation | Shutdown and Surface mutations share one FIFO and native cleanup releases global Surface references. P4-3 should use that cleanup rather than rebuild JNI. |
| FongMi/FFmpeg | `177f090e0503b7e013922ca903bde14b1c375f18` | A, exact source | `ff_mediacodec_surface_ref()` emits the error when both handles are absent and returns `NULL`. Changing FFmpeg would only move or hide the symptom. |

Independent papers, benchmarks, and general technical blogs are not decision-relevant here: the disputed contract is concrete Android Surface ownership and the locked mpv/FFmpeg lifecycle implementation, not a codec algorithm or performance model. No applicable academic evidence was found or required.

## 4. Current contracts that must survive

- Transient Surface destruction must still detach native output so configuration changes, PlayerView replacement, decoder/output switches, and real background/foreground recreation do not retain stale Java Surface references.
- Picture-in-picture entry and return must continue playing and reusing/rebinding the intended output path.
- P4-1 FIFO ordering, pending request cancellation, `MPV_EVENT_SHUTDOWN`, force-wakeup fallback, and one-time JNI Surface global-reference cleanup must remain unchanged.
- `vo=null` background behavior must remain available outside terminal release; no cache, decoder, renderer, DV/HDR, OSD/subtitle, audio, network, ABI, or binary ownership policy changes.
- No frame-loop branch or native rebuild is justified. The exit-only branch should reduce codec work and have no playback performance cost.

## 5. Alternatives

| Alternative | Benefit | Defect / risk | Decision |
| --- | --- | --- | --- |
| No change | Zero code risk | Keeps unnecessary decoder creation after Surface loss and leaves a vendor lifecycle race | Reject |
| Suppress or downgrade the FFmpeg log | Small patch | Hides a real invalid initialization and also masks non-terminal decoder-fallback occurrences | Reject |
| Always use `vid=no`, reorder detach, or delay commands | Can stop decoding before detach in some cases | Changes cache/background semantics, still depends on asynchronous VO timing, and can slow or break normal Surface recreation | Reject |
| Copy upstream `surfaceDestroyed()` unchanged | Matches sample App | That source contains an explicit race FIXME and has no terminal-release distinction | Reject |
| Narrow WebHTV terminal-release signal | Separates permanent exit from transient Surface loss; shutdown owns final native reference cleanup | Requires a small service-to-player signal and focused lifecycle verification | Select |

## 6. Recommended WebHTV adaptation

Add an idempotent terminal-release signal that is set synchronously when `PlaybackService.shutdown()` commits to permanent shutdown, before `stopAndClear()` and before the Activity Surface can disappear.

Planned behavior:

1. `PlayerManager` exposes a narrow terminal-release preparation method and forwards it only when the current engine is MPV. `MpvPlayerEngine` forwards to `MpvPlayer`.
2. `MpvPlayer` records `terminalReleaseRequested`. After that point, Surface create/change callbacks do not reattach output.
3. On terminal video/OSD `surfaceDestroyed`, clear only Java-side callback/state references and mark the native attachment state as owned by shutdown. Do not enqueue `vo=null`, `force-window`, or Surface-detach mutations.
4. The existing P4-1 shutdown path remains responsible for final MPV destruction and JNI global-reference cleanup. Normal non-terminal Surface loss continues through the current detach/rebind path unchanged.
5. `PlayerManager.release()` repeats the signal as an idempotent fail-safe for service destruction paths that bypass the ordinary `shutdown()` entry.

Expected implementation scope:

- `app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java`
- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java`
- `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java`
- one focused package-level policy/test under `app/src/main/java/androidx/media3/mpvplayer/` and `app/src/test/java/androidx/media3/mpvplayer/` if needed for deterministic transient-versus-terminal coverage
- this document and the master assessment status

Excluded: FFmpeg/mpv/mpv-android source revisions, JNI/C++, native assets, renderer/decoder policy, Exo/IJK behavior, and unrelated playback-service refactoring.

## 7. Impact and risk

- Benefit: avoids two observed decoder create/release cycles and the no-Surface error on terminal exit; reduces exposure to vendor codec/BufferQueue races.
- Compatibility: no format, ABI, Android API, or package-size impact. The new method is internal App code only.
- Performance: no frame-path work; terminal exit should do less work. Startup, seek, steady playback, and normal PiP performance are unchanged by design.
- Main regression risk: incorrectly classifying transient Surface loss as terminal could leave playback without output. Mitigation is an explicit service shutdown signal, not inference from pause, stop, `mediaItem == null`, or Activity state.
- Secondary risk: pending OSD/video Surface requests may already be in the P4-1 FIFO when shutdown begins. They remain ordered ahead of shutdown and are bounded; the implementation must prevent any new attachment after the terminal flag and leave final reference cleanup to native shutdown.
- Best practice: explicit ownership state plus idempotent terminal transition is preferable to timing delays, log suppression, or accessing a destroyed Surface. The WebHTV adaptation is narrower than modifying upstream/native code and preserves the upstream transient-background intent.

## 8. Acceptance and rollback

Minimum verification after approval:

1. Focused unit tests prove transient Surface loss still requests native detach, terminal Surface loss skips detach/rebind, and repeated terminal signaling is idempotent.
2. Compile the affected Mobile arm64 Java/App target once; no native or dual-ABI rebuild is required.
3. On the connected V2453A, use command-driven playback of one local H.264/TS or MKV sample. Verify normal HOME picture-in-picture and return continue playback with Surface recreation.
4. Exit playback terminally and require: `end-file` plus `shutdown`; no decoder component creation after BufferQueue abandonment; no `Both surface and native_window are NULL` in that terminal window; no crash/ANR/native fatal/destroyed-mutex signal.
5. One rapid reopen after exit must create the next MPV context normally, proving P4-1 cleanup and the terminal flag are instance-local.

Rollback is a revert of the one atomic P4-3 commit and its App-only Java/test/document paths. Existing C0-M binaries and P4-1 JNI assets remain untouched.

## 9. Recommendation and decision

Recommendation: **implement** the narrow terminal-release signal. Do not patch FFmpeg, change global `surfaceDestroyed()` semantics, add sleeps, or rebuild native libraries.

User decision: **approved for implementation** on 2026-08-29. Scope remains the Java-only terminal-release signal recorded above; native/FFmpeg changes and unrelated lifecycle refactors remain unapproved.

## 10. Implementation and verification

Implemented the approved Java-only design:

- `PlaybackService.shutdown()` and service destruction synchronously prepare terminal release before stopping or releasing the player.
- `PlayerManager` forwards the signal only to `MpvPlayerEngine`; Exo and IJK behavior is unchanged.
- `MpvPlayer` uses an instance-local, idempotent `MpvSurfaceTeardownPolicy`. Normal Surface loss keeps attach/detach enabled; terminal release blocks new video/OSD attachment and skips teardown commands that can reconfigure video after Surface destruction.
- Existing P4-1 native context destruction remains the sole final owner of JNI Surface-reference cleanup. No FFmpeg, mpv, JNI, native asset, renderer, decoder, ABI, or package dependency changed.

Verification on 2026-08-29:

- Focused test and App build passed in one corrected Gradle invocation: `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvSurfaceTeardownPolicyTest :app:assembleMobileArm64_v8aDebug --no-daemon`; `BUILD SUCCESSFUL in 3m 48s`, 112 tasks, 11 executed. The first attempted invocation used the nonexistent flavor spelling `Arm64` rather than `Arm64_v8a` and did not execute tests or compilation.
- APK: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`, 162,651,210 bytes, SHA-256 `42bef6007367653686ca301ad6791519a3efde54d20a025b7bbaa84d07ec2d07`.
- Install: OEM installer-assist completed successfully on vivo V2453A (`10CF6H1D2L0009S`, Android 15); installed package remained version `5.6.0`, code `560`, `arm64-v8a`.
- Representative input: `/storage/emulated/0/Download/声道测试/LPCM7.1原盘文件.m2ts`, using MPV (`player=2`) and `c2.qti.avc.decoder`.
- Transient lifecycle: HOME entered a real pinned task while media remained `PLAYING`; position advanced from 39.294 s to 42.323 s during the observed PiP interval and continued beyond 165 s. A compact rerun restored the same `VideoActivity` to `RESUMED` through its existing media-notification `PendingIntent`; playback remained `PLAYING` at 16.954 s.
- Terminal lifecycle: normal BACK returned to `HomeActivity`; MPV emitted `end-file` at `13:17:01.614` and `shutdown` at `13:17:02.175`. The only `c2.qti.avc.decoder` creation in that run was initial playback startup at `13:15:01.079`; there was no decoder creation after terminal Surface teardown, no `Both surface and native_window are NULL`, and no Java/native crash, ANR, fatal signal, or destroyed-mutex report.
- Reopen: after the verified shutdown, a fresh process (`11128`, replacing `9808`) created media session `/1028`, resumed `VideoActivity`, and played the same input. This confirms terminal state is instance-local and does not block the next MPV context.

Performance/compatibility result: no frame-path or native work was added. The only new checks run on Surface/OSD lifecycle and terminal release events; normal PiP/recreation retained playback, while terminal exit performed less decoder work. No observed compatibility, playback, performance, ABI, or package-size regression remains.

Rollback: revert `8250e2204f4054601202a3a3f2fe04f8766744ee`, or restore the annotated tag `recovery/P4-3-MPV-SURFACE-TEARDOWN/20260829132806-8250e2204f40`. The rollback affects only the Java lifecycle policy/wiring, its focused unit test, and this task's documentation; P4-1 JNI and C0-M native assets remain unchanged.
