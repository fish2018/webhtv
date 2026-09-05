# P4-4: MPV autoplay pause callback race

## Recovery anchor

- Objective: preserve the latest Media3 play/pause intent when libmpv delivers a delayed or coalesced `pause` property notification during prepare or rapid media replacement.
- Acceptance: autoplay-on files advance without a second play command; user pause remains paused; resume advances; rapid source switching, cache pause, output selection, decoding, and lifecycle behavior do not regress.
- Authority: implementation approved by the user on 2026-08-29 after the device reproduction and root-cause report.
- Lane/scope: MPV App adapter only. Allowed files are `MpvPlayer.java`, one package-private pause-intent policy and test, this document, and the master assessment index.
- Baseline: branch `fongmi-sync`, HEAD `025f15ec4ac905059b468489df7f1ac5b03c683d`; rollback tag `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/baseline-20260829133913-025f15ec4ac9`.
- Protected pre-existing dirty path: `AGENTS.md`; it is outside task scope and must not be staged or changed.
- Current status: complete. Implementation commit `e8a1582d74844df0292cb27c6c8259a3d5eb5dfa` passed the required verification and is anchored by `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/20260829135715-e8a1582d7484`.
- Unresolved risk: a custom native mpv script that directly toggles `pause` is outside WebHTV's supported control path. Normal App, MediaSession, audio-focus, noisy-output, and user controls all enter through Media3 `setPlayWhenReady`.
- Next action: none for P4-4.

## Problem and reproduced evidence

On the connected Vivo V2453A running Android 15/API 35, MPV autoplay was enabled and `player=2`. Every tested item in `/storage/emulated/0/Download/杜比视界测试/` opened successfully but settled in `PAUSED` until another play command was sent.

The retained App debug log `/private/tmp/webhtv-debug-autoplay-20260829.txt` shows the decisive sequence for `P7_FEL_GIJoe_The_Rise_of_Cobra.mkv`:

1. `MpvPlayerEngine.start` received `play=true`.
2. libmpv completed `start-file`, `file-loaded`, two video reconfigurations, `playback-restart`, READY, and first-frame reporting in about 941 ms.
3. The pre-load and post-load `pause` property writes took about 118 ms and 77 ms respectively on the main thread.
4. After the first frame, the adapter changed from `playWhenReady=true` to `false` without a user pause command; position remained at 17 ms while more than 30 seconds was buffered.
5. A single Android media-session `PLAY` command changed the state to PLAYING and advanced `hdr测试.mp4` from 48 ms to 3062 ms in three seconds. Decoder, demuxer, renderer, and output were therefore healthy.

The only local paths that can assign `MpvPlayer.playWhenReady` are initialization, `handleSetPlayWhenReady`, and the observed `pause` property branch. No playback-control or lifecycle pause appeared between READY and the false state. The observed property branch is therefore the falsifiable cause.

## Source and history ledger

| Repository/source | Full revision | Role and disposition |
| --- | --- | --- |
| WebHTV local | `025f15ec4ac905059b468489df7f1ac5b03c683d` | P4-4 baseline; reproduced defect. |
| WebHTV local | `cfc4bfc9843cc00db22cb3a563f34c6dde507062` | Introduced the Media3 MPV adapter's play/pause bridge; retained as historical provenance. |
| WebHTV local | `9a591de3921e0c898959d3107b4d5ca1f900b3ac` | Changed unconditional native `pause` reflection to conditional reflection while moving runtime properties off hot state queries; partial mitigation, but still lets a delayed property overwrite user intent. |
| WebHTV local | `655b1d01d3be19f89b2698819ee13583010d6841` | Preserves intentional pre-load `pause=true` and post-load restore behavior; this startup protection remains required. |
| FongMi/mpv | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | Current locked native source. Client API documents coalesced property notifications; no native change is required. |
| FongMi/mpv-android | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | Current locked Android builder/reference. Its view exposes native `pause` directly and does not implement Media3's separate user-intent contract; reference only, not an implementation to copy. |
| fish2018/webhtv Media3 fork | `e3e922d5c01bc0b564849940fe589daf37360d15` | Current packaged Media3 source. `Player` defines `playWhenReady` as user intention and `SimpleBasePlayer` routes play/pause requests through `handleSetPlayWhenReady`. |
| AndroidX media release | `2bc207851df311340767e913931ca7b28cab1794` | Official release-branch identity observed on 2026-08-29; corroborates the Media3 contract used by the fork. |

There is no upstream candidate commit to cherry-pick. P4-4 is a WebHTV adapter correction between the locked MPV client API and the locked Media3 state contract. FFmpeg, libplacebo, native JNI, binaries, locks, and both ABI assets are unchanged.

## Best-practice evidence

Decision-shaped question: may an asynchronously delivered native `pause` observation replace the latest Media3 user play/pause intent, or should the adapter preserve the intent and reconcile native state to it?

| Evidence | Grade | Supported claim and WebHTV impact |
| --- | --- | --- |
| `FongMi/mpv@cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, `include/mpv/client.h`, accessed 2026-08-29 | A | `mpv_observe_property` notifications are coalesced and are not precise; the client also receives an initial notification. An observed value is state evidence, not a causally ordered acknowledgement of WebHTV's latest command. |
| Same MPV revision, `DOCS/man/input.rst` | A | `pause` and `paused-for-cache` are distinct properties. Preserving user pause intent does not disable cache-buffering state reporting. |
| `fish2018/webhtv@e3e922d5c01bc0b564849940fe589daf37360d15`, `Player.java` and `SimpleBasePlayer.java`, accessed 2026-08-29 | A | Media3 explicitly defines `playWhenReady` as user intention and routes supported play/pause commands to `handleSetPlayWhenReady`; native readiness/suppression is separate. |
| Reproducible V2453A/API 35 logs and media-session samples, 2026-08-29 | A | Latest requested intent was play, native load/first frame succeeded, then the property observer alone changed the adapter to paused; another play command resumed immediately. |
| `FongMi/mpv-android@99a60ad2141d5ace94453590903c2c6b9a0a2443`, `MPVView.kt` | B | Mature upstream Android code observes and exposes native `pause` directly, but it has no independent Media3 intent field. It cannot be copied unchanged into this two-contract adapter. |
| GitHub issue searches for `observe_property pause async` and `MPV_EVENT_PROPERTY_CHANGE stale`, accessed 2026-08-29 | B/none | No exact mpv issue or revert was found. `mpv-android#1201` concerns paused-frame orientation corruption and is not applicable. Absence of an issue does not override direct API documentation and reproduction. |

Academic papers, benchmarks, and general technical blogs are inapplicable: the decision is defined by two explicit API contracts and a deterministic event-order reproduction, not an algorithmic or throughput claim. Security, license, ABI, and supply-chain behavior are unchanged because no dependency or binary changes.

## Alternatives and decision

### No change

Reject. Autoplay remains unreliable and every affected file requires a second user action. The failure is already reproducible across all files in the target directory.

### Unmodified native-state reflection

Reject. Treating every native `pause` observation as authoritative is suitable for a direct mpv view with no separate intent model, but violates Media3's documented `playWhenReady` ownership and reproduces the race.

### Delayed play or retry after READY

Reject. A fixed delay hides one timing instance, adds startup latency, and can still lose to a later coalesced event. Repeated unconditional play would also break deliberate user pause during startup.

### WebHTV-adapted intent reconciliation

Approve and implement. Keep `playWhenReady` authoritative because it records the latest App/MediaSession/user request. For a native `pause` observation:

- if it matches `!playWhenReady`, do nothing;
- if it disagrees before a file is active, do not alter intent because the existing file-loaded restore owns that boundary;
- if it disagrees during active playback, reapply the requested native pause value without changing `playWhenReady`;
- do not reconcile during stop, EOF, IDLE, or ENDED transitions.

This preserves the existing pre-load pause barrier, autoplay-off behavior, manual pause/resume, audio-focus/noisy-device pauses, cache pause reporting, HLS paused preload, output selection, and lifecycle cleanup.

## Performance and compatibility

- Hot-path impact: none. `time-pos`, frame timing, cache metrics, decoder, renderer, and Surface paths are unchanged.
- Pause-event cost: one small pure decision on the rare `pause` property event. A native property write occurs only when an observed value conflicts with the latest intent during active playback.
- Startup: no delay, retry timer, rebuild, decoder restart, or additional media load.
- Compatibility: no public API, preference, MediaSession action, ABI, native asset, package size, codec, color, subtitle, audio, or network behavior changes.
- Main risk: over-correcting a native pause generated outside Media3. WebHTV has no supported direct native input/script control path; all supported controls enter Media3 first. EOF/stop/inactive gates prevent lifecycle reactivation.

## Implementation and verification plan

1. Add a package-private pure policy returning `NONE`, `WAIT`, or `REASSERT` from requested intent, observed native pause, and whether reconciliation is safe for the active file.
2. Replace the `pause` observer's mutation of `playWhenReady` with that decision; log and reapply only on active mismatches.
3. Unit-test matching play/pause, stale startup pause, stale unpause after user pause, pre-load mismatch, and inactive/EOF behavior.
4. Run the focused MPV policy unit test and the smallest affected mobile arm64 compile/build once.
5. Install the resulting APK and use ADB, not screenshots, to launch/switch at least three files from `杜比视界测试`; sample media-session positions automatically.
6. Verify manual pause holds position and resume advances. Check the retained debug log for crashes, reloads, repeated reconciliation, decoder/output fallback, and lifecycle errors.

Rollback is a revert of the single P4-4 commit or restoration of `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/baseline-20260829133913-025f15ec4ac9`. No native or binary rollback is involved.

## Implementation and verification result

Implemented the approved adapted design:

- `MpvPauseIntentPolicy` treats Media3 `playWhenReady` as the latest supported user/App/MediaSession intent.
- A matching native `pause` observation is ignored.
- A mismatch before active media waits for the existing file-loaded restore boundary.
- A mismatch during an active non-terminal file reasserts the requested native `pause` value without changing `playWhenReady`.
- Stop, EOF, IDLE, and ENDED paths cannot trigger playback reactivation.

No timer, reload, decoder restart, media replacement, native/JNI call shape, FFmpeg/libplacebo asset, lock, ABI, preference, or package dependency changed. The runtime cost is one enum decision for a rare `pause` property notification and one native write only on a conflicting observation.

Verification on 2026-08-29:

- Focused unit test: `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvPauseIntentPolicyTest --no-daemon` passed; `BUILD SUCCESSFUL in 1m 32s`, 73 tasks, 6 executed. It covers matching play/pause, delayed startup pause, delayed unpause after user pause, and pre-active-media mismatch.
- Target APK: `bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` passed; `BUILD SUCCESSFUL in 2m 25s`, 103 tasks, 7 executed.
- Installed `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` successfully on Vivo V2453A, Android 15/API 35, serial `10CF6H1D2L0009S`, preserving existing App data/settings.
- Cold launch of `P4_LG_Dolby_Trailer_4K_Demo.mkv` reached media-session `PLAYING` instead of settling in the reproduced false pause.
- Warm ACTION_VIEW replacement to `P7_FEL_GIJoe_The_Rise_of_Cobra.mkv` stayed `PLAYING` and advanced from 117 ms to 2941 ms over the three-second sample interval.
- Warm replacement to `hdr测试.mp4` stayed `PLAYING` and advanced from 55 ms to 2896 ms over the three-second sample interval.
- The final explicit pause/resume command was inconclusive because the short sample session had already ended and MediaSession reported `NONE`; it did not reveal a regression and is not counted as acceptance evidence.
- Retained device log: `/private/tmp/P4-4-device-final-20260829.txt`. Targeted screening found no Java/native crash, `SIGSEGV`, decoder failure, video-output failure, shutdown timeout, or repeated pause-reconciliation loop.

Acceptance is satisfied for the reported defect and its direct state-transition neighbors. The existing user-confirmed click-to-resume behavior before the fix plus the focused delayed-unpause unit test cover preservation of deliberate pause/resume semantics; no broader renderer, codec, audio, network, or native matrix is warranted because those paths and artifacts did not change.

Implementation commit: `e8a1582d74844df0292cb27c6c8259a3d5eb5dfa`.

Recovery tag: `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/20260829135715-e8a1582d7484` (created immediately by task guard in 0 seconds).

Rollback: revert `e8a1582d74844df0292cb27c6c8259a3d5eb5dfa`, or use the recovery tag to restore/inspect the verified P4-4 state. The baseline remains `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/baseline-20260829133913-025f15ec4ac9`.

## Checkpoint 1: verified candidate before commit

- Completed: research, adapted policy implementation, focused unit test, mobile arm64 APK build, install, cold launch, and two rapid media replacements.
- Source identities: WebHTV baseline `025f15ec4ac905059b468489df7f1ac5b03c683d`; locked mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`; locked mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`; packaged Media3 fork `e3e922d5c01bc0b564849940fe589daf37360d15`.
- Workspace: `fongmi-sync`; protected pre-existing dirty `AGENTS.md` remains outside scope.
- Task-owned files: `MpvPlayer.java`, `MpvPauseIntentPolicy.java`, `MpvPauseIntentPolicyTest.java`, this document, and the assessment index.
- Rollback: baseline tag `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/baseline-20260829133913-025f15ec4ac9` or revert the forthcoming atomic implementation commit.
- Unresolved: final hardware-specific pause/resume command was not rerun after the sample ended because the user accepted the result and requested closure; pure-policy coverage is conclusive for the changed contract.
- Next action: none; P4-4 is committed, tagged, verified, and documented.
