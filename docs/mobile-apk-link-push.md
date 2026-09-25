# Mobile APK link push

## Recovery anchor

- Objective: after a mobile user selects a LAN WebHTV device, offer local APK upload or URL push; URL push makes the receiving TV download one APK and open the normal Android package-installer confirmation.
- Acceptance: preserve the existing local-file path; accept the supplied HTTPS example including valid CDN redirects; reject unsafe/oversized/invalid downloads; never silently install; report older receivers as unsupported.
- Lane: standard implementation.
- Branch/base: `main` at `3005574c10bacff08291df665e19725c5337fa9e` before integration.
- Protected pre-existing dirty paths: `app/.cxx/**` (35 task-guard entries).
- Current task-owned file: `docs/mobile-apk-link-push.md`.
- Status: the URL-push and receiver progress-dialog implementation is integrated into the guarded `main` worktree; focused policy/progress/cancel tests and both Arm64 Java compilation paths pass.
- Current implementation: custom mobile method dialog and URL input, additive `apk_url` action, asynchronous bounded TV downloader, strict network policy, and a custom receiver progress dialog with bytes, percentage, speed, elapsed/remaining time, real cancellation, and activity-resume restoration.
- Unverified worktree edits: none; the guarded 19-path integration patch passed its targeted verification.
- Device evidence: the mobile APK was installed on `V2453A` (`10CF6H1D2L0009S`), `pm path com.fongmi.android.tv` confirmed the package, and the home screen launched successfully; evidence is under `/private/tmp/webhtv-apk-link-test-20260830/`.
- Unresolved device gate: the TV endpoint `192.168.1.9:5555` still refuses connection, so the receiver progress dialog, remote focus, live speed/time updates, cancellation cleanup, and URL-download-to-installer flow remain unverified on TV hardware.
- Next action: install the next leanback Arm64 build on the TV, run one representative URL download, and cancel a second run after progress is visible.

## Requested capability

Current mobile behavior is:

1. Open `推送apk` from the home toolbar overflow menu.
2. Discover and select a LAN WebHTV device.
3. Immediately open Android's local document picker.
4. Validate and upload the selected APK to `POST /action?do=apk`.

The requested behavior adds a choice after step 2:

- `选择本地 APK`: keep the current flow unchanged.
- `通过链接推送`: enter an APK URL; the TV downloads it and opens Android's installation confirmation.

Representative URL supplied by the user:

`https://gh.acmsz.top/https://github.com/fish2018/webhtv/releases/download/v5.6.0-beta-202608281927/mobile-arm64_v8a-beta.apk`

## Current WebHTV implementation

Evidence is from local commit `322b97db4205d8452ff02d2da27a9ecfc1f95639`.

- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java`
  - `push_apk` opens `ApkPushDialog` in discovery mode.
  - `onApkDeviceSelected` stores the device and launches `OpenDocument` immediately.
  - `onApkSelected` passes the selected `Uri` to the transfer dialog.
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ApkPushDialog.java`
  - Copies the content URI to cache, checks the `.apk` name, parses package metadata, computes SHA-256, and uploads multipart content.
  - Uses `POST /action?do=apk` and expects the exact acknowledgement `APK received`.
  - Retries the local transfer twice and cancels its OkHttp call when the dialog is destroyed.
- `app/src/main/java/com/fongmi/android/tv/server/process/Action.java`
  - `onApk` validates the uploaded file, optional length and SHA-256, free storage, and package metadata.
  - Copies it to an app cache file, calls `FileUtil.openFile`, and clears it after 30 minutes.
- `app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java`
  - Opens a `FileProvider` URI with `ACTION_VIEW` and read permission, leaving final installation to Android's package installer.
- `app/src/main/AndroidManifest.xml`
  - Already declares `INTERNET`, `REQUEST_INSTALL_PACKAGES`, and a non-exported `FileProvider`.
  - Existing LAN features require `usesCleartextTraffic=true`; URL APK download can still enforce HTTPS independently.
- `app/src/main/java/com/fongmi/android/tv/Updater.java` and `utils/Download.java`
  - Establish the local pattern of streaming a download, validating it as an APK, then opening the system installer.
  - The generic `Download` helper is not sufficient for this endpoint because it follows redirects by default and has no task-specific maximum-byte or destination-address policy.

No existing URL-to-TV APK push endpoint, task manager, or focused test was found.

## External evidence

All external sources below were retrieved on 2026-08-30 using the user-provided proxy unless a result explicitly says otherwise.

### Android platform documentation

1. Android `Manifest.permission.REQUEST_INSTALL_PACKAGES`
   - URL: <https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES>
   - Evidence grade: official platform reference.
   - Claim: the permission allows an application to request package installation; it does not grant silent installation.
   - Impact: retain the current package-installer confirmation path.

2. Android `PackageManager.canRequestPackageInstalls()`
   - URL: <https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()>
   - Evidence grade: official platform reference.
   - Claim: from Android O, users choose which external sources they trust. If the caller is not trusted, the package installer blocks the request and offers a settings path.
   - Impact: URL push must not claim installation is automatic; acceptance ends when the system installer is opened.

3. Android `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`
   - URL: <https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES>
   - Evidence grade: official platform reference.
   - Claim: Android exposes per-package settings for trusted external sources.
   - Impact: no new custom permission flow is necessary for the first version; existing system behavior remains authoritative.

4. Android network security configuration
   - URL: <https://developer.android.com/privacy-and-security/security-config#CleartextTrafficPermitted>
   - Evidence grade: official security documentation.
   - Claim: HTTPS protects traffic from hostile networks; cleartext is disabled by default for apps targeting Android 9+ unless explicitly enabled.
   - Impact: even though WebHTV enables cleartext for LAN compatibility, the new remote APK source policy should accept HTTPS only.

### OkHttp 5.4.0

- Repository/tag: `square/okhttp`, `parent-5.4.0`.
- Tag object: `95c1c03d45fc935e91c2968859fd9add09f85af2`.
- Commit: `61423f472da24e0ccc42b6a2c0863fb27932fea5`.
- Sources inspected:
  - `OkHttpClient.kt`
  - `RetryAndFollowUpInterceptor.kt`
  - `ResponseBody.kt`
- Evidence grade: exact dependency source used by this project.
- Claims:
  - Redirect following is enabled by default.
  - OkHttp allows up to 20 follow-up requests by default.
  - Automatic cross-protocol redirects are enabled by default.
  - `ResponseBody` is one-shot, must be closed, and supports streaming responses larger than process memory or device storage.
- Impact:
  - Build a dedicated client with automatic redirects disabled.
  - Follow only a small bounded number of redirects manually and revalidate every destination.
  - Stream directly to a cache file while enforcing a byte counter; never call `bytes()` or `string()` for the APK body.

### SSRF prevention

- Source: OWASP Server-Side Request Forgery Prevention Cheat Sheet.
- URL: <https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html>
- Evidence grade: mature security guidance.
- Claims:
  - User-supplied URLs can make the receiver access internal/external resources.
  - Restrict schemes, validate resolved IPv4 and IPv6 destinations, account for DNS pinning/rebinding, and do not let automatic redirects bypass validation.
- WebHTV applicability:
  - `/action` is reachable by LAN peers and currently has no authentication, so arbitrary URL fetching would create a real network-pivot and resource-exhaustion surface.
  - A domain allowlist is not viable because the feature explicitly accepts arbitrary public APK hosts.
- Impact: use a deny policy for all non-global destinations, validate DNS answers at connection time, and repeat the same validation for every redirect.

### Mature related implementation and field evidence

- Project: Obtainium.
- Commit: `00d545b36ea9c2ff74f97a2a73d345771839bf00`.
- Sources inspected:
  - `lib/app_sources/direct_apk_link.dart`
  - `lib/providers/apps_provider_install.dart`
  - issues `#896` and `#3255`.
- Evidence grade: mature open-source Android APK downloader/installer and user field reports.
- Claims:
  - A direct APK URL is a legitimate source type.
  - Download progress, cancellation, cleanup, APK selection, and installation are separate lifecycle stages.
  - Installation completion can stall or become unavailable when tied to background state; mature implementations bound waits and keep the system installer as a distinct step.
- Impact:
  - Do not keep the phone's LAN request open for the full TV download/install lifecycle.
  - Acknowledge the job quickly, download asynchronously on the TV, and treat opening the system installer as the job's terminal success.

No paper or synthetic benchmark was found that could change this bounded design. The material questions are URL/network safety, bounded streaming, and Android installer lifecycle; official source, a security standard, exact dependency source, and field reports provide stronger evidence than throughput benchmarks. Final performance evidence should be the representative 133 MB APK on the actual TV network.

### Representative URL observation

- Direct request observation: HTTP 200, `application/vnd.android.package-archive`, `Content-Length: 133582234`, filename `mobile-arm64_v8a-beta.apk`.
- Through the supplied proxy, both `HEAD` and a one-byte range request returned Cloudflare HTTP 403.
- Impact:
  - Do not require `HEAD` or range preflight; some mirrors/CDNs treat those methods differently from a normal download.
  - Use one bounded streaming `GET`, validate headers when present, and enforce limits while reading.
  - The final TV test must verify the URL from the TV's actual network path, because the development proxy result is not representative of the TV.

## Alternatives

### A. No change

Keep local APK upload only.

- Benefits: no new network or security surface.
- Costs: does not satisfy the requested workflow; the phone must first download and then retransmit a large APK over LAN.
- Decision: reject.

### B. Direct blocking implementation

Add `url` to the existing `/action?do=apk` path, let the generic downloader follow redirects, and keep the mobile HTTP request open until download and installer launch finish.

- Benefits: fewest source lines and the phone receives the final result synchronously.
- Risks:
  - Default redirects can bypass initial URL validation and permit HTTPS downgrade.
  - The server request thread remains occupied for a 133 MB or larger transfer.
  - Phone disconnects/timeouts become coupled to a TV-local download.
  - Existing `Download` has no task-specific hard size or destination-address limit.
- Decision: reject.

### C. Narrow WebHTV-adapted asynchronous URL push

Add a separate `apk_url` action. The TV validates and accepts one job, responds immediately, then downloads it on a bounded background task, validates the resulting APK, and opens the existing system installer.

- Benefits:
  - Existing local multipart protocol and behavior remain unchanged.
  - The APK travels directly from source to TV, eliminating the phone download and LAN retransmission.
  - Network safety and resource limits are isolated to the new action.
  - The LAN request is short and independent of a potentially slow download.
- Costs:
  - The phone can confirm acceptance, not final installation; final state is visible on the TV through download errors or the system installer.
  - Adds a small receiver-side job coordinator and URL policy.
- Decision: recommend.

A phone-side polling/status protocol is deliberately deferred. It would improve remote progress visibility but adds job persistence, status endpoints, polling, cancellation semantics, and version negotiation beyond the requested first version.

## Recommended design

### Mobile interaction

1. Keep the existing device discovery dialog.
2. After selecting a device, show a compact two-option method dialog:
   - `选择本地 APK`
   - `通过链接推送`
3. Local option launches the existing `OpenDocument` flow without changing `ApkPushDialog` transfer behavior.
4. URL option opens a focused URL input dialog that:
   - labels the selected target device;
   - prefills a valid HTTPS URL from the clipboard when available;
   - trims and validates an absolute HTTPS URL before sending;
   - disables submit while the LAN request is in flight;
   - cancels that LAN request if dismissed before acceptance;
   - reports old receivers with a specific `不支持链接推送` message.
5. On `APK URL accepted`, show `链接已发送，电视正在下载` and close the phone dialog.

The method choice uses a custom ViewBinding dialog and dedicated layout, as required by the approved interaction design. The URL input reuses the established custom text-command binding pattern.

### LAN protocol

- Existing local upload remains: `POST /action?do=apk` multipart, acknowledgement `APK received`.
- New URL request: `POST /action?do=apk_url` form body:
  - `device`: existing serialized sender metadata for logging only.
  - `url`: absolute HTTPS APK URL.
- Success acknowledgement: exact body `APK URL accepted`.
- Receiver errors:
  - invalid/unsafe URL: HTTP 400 with a concise reason;
  - another URL APK job active: HTTP 409;
  - scheduling failure: HTTP 500.
- Compatibility:
  - New receiver continues to accept old local upload clients.
  - An old receiver falls through to its current `OK` response; the new sender treats any body other than `APK URL accepted` as unsupported.

### Receiver job lifecycle

1. Perform cheap syntactic URL validation in the request handler.
2. Atomically reserve the single URL APK job slot.
3. Return `APK URL accepted` immediately after the background task is scheduled.
4. On the TV, show a short notification that download has started.
5. Stream to `Path.cache("pushed-url-<timestamp>.apk")`.
6. Validate non-empty content, maximum size, available storage, and `PackageManager.getPackageArchiveInfo`.
7. On the main thread call the existing `FileUtil.openFile`.
8. Clear partial files on every error; clear the install file after the existing 30-minute handoff window.
9. Release the active job slot in all terminal paths.

Do not automatically retry the full download. A retry can duplicate hundreds of megabytes and an accepted job is no longer coupled to the phone request; the user can explicitly retry after a visible TV error.

### URL and network policy

- Accept `https` only.
- Reject user-info credentials and malformed/relative URLs.
- Disable OkHttp automatic redirects.
- Manually follow at most five HTTP redirects (`301`, `302`, `303`, `307`, `308`).
- Require HTTPS again after every redirect; never downgrade to HTTP.
- Resolve each destination through a validating `Dns` wrapper used by the actual OkHttp connection.
- Reject every result if any selected destination is non-global, including unspecified, loopback, link-local, site-local/private, carrier-grade NAT, multicast, and other special-use IPv4/IPv6 ranges.
- Validate literal IP hosts with the same policy before creating the request.
- Do not forward cookies, authorization headers, or sender-provided arbitrary headers.
- Ignore `Content-Disposition` for the local path; use a generated cache filename.

This does not prove the APK publisher's identity. It preserves the same trust model as local-file push: the user chooses the APK source, WebHTV verifies only that the result is a structurally valid APK, and Android presents the final install confirmation.

### Resource limits

- Hard APK response limit: `512 MiB`.
  - The supplied APK is about `127.4 MiB`, leaving substantial release growth headroom.
  - Single-file APKs above this limit are outside this push workflow; split APK/APKS/XAPK support is not included.
- Storage reserve: keep at least the existing `16 MiB` margin beyond the expected/written APK bytes.
- If `Content-Length` is present, reject it before writing when it exceeds the hard limit or available-storage budget.
- Independently enforce the same limits after every streamed chunk because length may be absent or false.
- Permit only one active URL APK job to bound concurrent bandwidth, storage, and installer prompts.
- Use a dedicated finite call timeout and close every response body/call; never buffer the APK in memory.

## Expected code scope after approval

- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ApkPushUrlDialog.java` (new)
- `app/src/main/java/com/fongmi/android/tv/server/process/Action.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/ApkUrlPush.java` (new, background job/download lifecycle)
- `app/src/main/java/com/fongmi/android/tv/server/process/ApkUrlPolicy.java` (new, URL/address/redirect policy)
- Mobile strings in `app/src/mobile/res/values*/strings.xml`
- Receiver strings in `app/src/main/res/values*/strings.xml`
- Focused tests under `app/src/test/java/com/fongmi/android/tv/server/process/`
- This task document for implementation results and recovery state.

No dependency, manifest, native library, ABI artifact, public API, or existing `/action?do=apk` protocol change is expected.

## Acceptance criteria

1. After device selection, the mobile app offers local APK or URL push.
2. Local APK selection/upload behavior and acknowledgement remain unchanged.
3. The supplied HTTPS URL is accepted, downloaded by the TV, parsed as an APK, and handed to Android's installer confirmation.
4. Valid HTTPS-to-HTTPS CDN redirects work within the redirect bound.
5. HTTP, relative, credential-bearing, loopback, private/link-local, unsafe redirect, and malformed URLs are rejected before APK content is accepted.
6. Known-length and chunked responses over 512 MiB are stopped and partial files are deleted.
7. Empty, truncated, non-APK, insufficient-storage, network, and timeout failures leave no partial cache file and show a TV-side error.
8. A second concurrent URL push is rejected without disturbing the active job.
9. An older receiver produces a clear unsupported-version message on the phone.
10. The feature never silently installs an app and does not bypass Android's unknown-source controls.
11. No new dependency or material package-size increase is introduced.

## Verification plan

Cheapest decisive order after implementation:

1. Focused JVM tests for URL schemes, IP ranges, DNS result filtering, redirect bounds/downgrades, content-length limit, streaming overflow, truncation, and cleanup.
2. Compile the affected `mobileArm64_v8aDebug` and `leanbackArm64_v8aDebug` variants once.
3. Install the mobile build on connected phone `V2453A` (`10CF6H1D2L0009S`).
4. Install or confirm the matching receiver build on one LAN TV.
5. Run one end-to-end scenario with the supplied URL:
   - phone selects TV;
   - phone chooses URL mode and sends the link;
   - TV downloads directly;
   - Android installer confirmation appears;
   - installation is not confirmed automatically.
6. Run one representative rejection, preferably `https://127.0.0.1/app.apk` or an HTTPS redirect to a private destination, without contacting that destination.

If no LAN TV is reachable, compilation and focused tests can complete, but the task must remain device-unverified and must not claim the requested end-to-end behavior is proven.

## Rollout and rollback

- Rollout is additive and version-negotiated by the exact acknowledgement body; no migration is needed.
- The existing local method remains the immediate fallback for mixed-version devices or CDN-specific failures.
- Rollback is one implementation commit/recovery tag: remove the URL choice, `apk_url` action, job/policy classes, new strings/tests, and clear any `pushed-url-*.apk` cache file.
- Existing local APK upload remains usable before, during, and after rollback.

## Recommendation

Implement alternative C exactly as bounded above. It satisfies the requested direct-to-TV download, preserves the proven local upload and Android installer path, and adds the minimum controls needed for an unauthenticated LAN endpoint that fetches user-supplied URLs.

## Implementation record (2026-08-30)

Implemented after explicit user approval, with the additional requirement that the method selection use a custom-drawn dialog rather than a standard item-list alert.

### Mobile implementation

- Added `ApkPushMethodDialog` and `dialog_apk_push_method.xml`.
  - Custom 8 dp panel, explicit target device, icon buttons for `选择本地 APK` and `通过链接推送`, and a cancel command.
  - Does not use `MaterialAlertDialogBuilder.setItems`.
- Added `ApkPushUrlDialog` using the project's custom text-command view binding.
  - Accepts and clipboard-prefills credential-free HTTPS URLs only.
  - Sends `device` and `url` to `POST /action?do=apk_url`.
  - Requires exact acknowledgement `APK URL accepted`; a successful legacy `OK` response is reported as unsupported.
- Updated `VodFragment` so the existing file picker is launched only after the local method is selected. Existing `ApkPushDialog` upload code was not changed.

### Receiver implementation

- Added `Action` dispatch for `apk_url` without changing `apk`.
- Added `ApkUrlPolicy`:
  - HTTPS and no URL credentials;
  - manual maximum-five redirects with no downgrade or missing destination;
  - validating DNS used by the actual connection;
  - rejection of private, loopback, link-local, carrier-grade NAT, documentation, transition, multicast, and other special-use IPv4/IPv6 destinations.
- Added `ApkUrlPush`:
  - one active job;
  - immediate protocol acknowledgement followed by TV-local background download;
  - dedicated standard-TLS OkHttp client, automatic redirects disabled, retries disabled, and `Proxy.NO_PROXY` so system proxy configuration cannot bypass address validation;
  - 512 MiB hard limit and 16 MiB storage reserve checked for declared and actual bytes;
  - streamed file output, truncation/empty detection, APK package parsing, existing `FileUtil.openFile` installer handoff, and cleanup on failure/after 30 minutes.

### Verification completed

- Focused tests:
  - `ApkUrlPolicyTest`
  - `ApkUrlPushTest`
  - Result: `BUILD SUCCESSFUL`.
- Final post-security-fix compilation:
  - `testMobileArm64_v8aDebugUnitTest` for both focused classes: pass.
  - `compileLeanbackArm64_v8aDebugJavaWithJavac`: pass.
- Final artifacts:
  - `assembleMobileArm64_v8aDebug`: pass.
  - `assembleLeanbackArm64_v8aDebug`: pass.
  - Mobile APK: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`.
  - TV APK: `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`.
- Existing build warnings about 32-bit native libraries, string resource namespaces, and Gradle deprecations remain unrelated to this task.

### Device verification

- Installed the mobile Arm64 debug APK on phone `V2453A` (`10CF6H1D2L0009S`) with the installer-assist workflow.
- `pm path com.fongmi.android.tv` confirmed installation, and the app launched to its home screen successfully.
- Launch screenshot: `/private/tmp/webhtv-apk-link-test-20260830/launch.png`.
- Launch UI hierarchy: `/private/tmp/webhtv-apk-link-test-20260830/launch.xml`.

### Pending verification

- Visual inspection of the custom method dialog after selecting a discovered LAN device.
- LAN TV install/availability and the representative URL download-to-system-installer flow.
- One private/unsafe destination rejection observed on the receiver.

## Follow-up: receiver download progress dialog (2026-08-30)

### Requested behavior

After the TV accepts an APK URL, replace the start-only toast with a foreground dialog modeled on the About page's update download state. The dialog must show download progress, transferred size, speed, elapsed time, estimated remaining time when the response length is known, and a cancel button that stops the actual download.

### Decision evidence

1. Current WebHTV update flow
   - Sources: `app/src/main/java/com/fongmi/android/tv/Updater.java`, `utils/Download.java`, and both variant `ui/dialog/UpdateDialog.java` implementations at base commit `f6d5b30e391642493f1cfb831e1cdc5cc3838b5a`.
   - Evidence grade: exact local production code.
   - Claim: the established UI changes to a non-dismissible progress state, reports percentage/speed/elapsed/remaining time, keeps one explicit cancel action, cancels the underlying OkHttp request, and restores the dialog from `BaseActivity.onResume()` while a download remains active.
   - Impact: match these interaction and lifecycle rules without changing the existing version-selection dialog.

2. Current URL push flow
   - Source: `app/src/main/java/com/fongmi/android/tv/server/process/ApkUrlPush.java` at base commit `f6d5b30e391642493f1cfb831e1cdc5cc3838b5a`.
   - Evidence grade: exact local production code.
   - Claim: the receiver already streams one bounded job and cleans partial files, but it retains no active `Call`, exposes no progress callback, and only emits start/failure/ready toasts.
   - Impact: cancellation and progress belong in this job controller and streaming loop; closing a UI alone would be incorrect.

3. Android `DialogFragment` guidance
   - URL: <https://developer.android.com/guide/fragments/dialogs>
   - Accessed: 2026-08-30 through the configured proxy.
   - Evidence grade: official Android architecture documentation.
   - Claim: `DialogFragment` lets `FragmentManager` manage dialog state and restoration; callers should avoid duplicate `show()` calls and locate an existing fragment by tag.
   - Impact: use a tagged custom `DialogFragment`, rebind it to the active job on TV activity resume, and never create overlapping progress dialogs.

4. OkHttp 5.4.0 `Call`
   - Source: `square/okhttp` tag `parent-5.4.0`, commit `61423f472da24e0ccc42b6a2c0863fb27932fea5`, `okhttp3/Call.kt`.
   - Accessed: 2026-08-30 through the configured proxy.
   - Evidence grade: exact dependency source.
   - Claim: `Call.cancel()` cancels an in-flight request when possible, and blocking `execute()` reports cancellation as `IOException`.
   - Impact: retain the current active `Call`, call `cancel()` from the dialog action, and classify the resulting exception as user cancellation rather than download failure.

5. Mature related implementation
   - Reuses the Obtainium evidence already recorded above at commit `00d545b36ea9c2ff74f97a2a73d345771839bf00`.
   - Evidence grade: mature APK downloader/installer plus field reports.
   - Claim: download progress, cancellation, cleanup, and installer handoff are separate lifecycle stages.
   - Impact: the progress dialog ends when the validated APK is handed to Android's installer; installation remains a separate system confirmation.

Upstream PR/revert archaeology and throughput papers are not decision-relevant for this follow-up: no dependency, protocol, redirect, TLS, size-limit, or performance policy changes. The behavior is decided by exact local implementation, official lifecycle guidance, the exact OkHttp cancellation contract, and the already reviewed mature APK-downloader lifecycle.

### Alternatives

- No change: keeps the download invisible and cannot cancel it. Rejected because it does not satisfy the requested receiver interaction.
- Reuse `UpdateDialog` unchanged: would display version/channel controls unrelated to a pushed APK and would couple two independent owners to one singleton update flow. Rejected because it creates misleading UI and lifecycle conflicts.
- Add a dedicated progress-only dialog using the update dialog's visual language and timing format: keeps ownership in `ApkUrlPush`, supports known and unknown response lengths, and leaves the About updater untouched. Recommended.

### Implementation plan

1. Add one shared custom `ApkPushProgressDialog` and layout with title, concise source/install hint, transferred-size detail, horizontal progress, and a focused cancel button.
2. Extend `ApkUrlPush` with progress state, a cancellable active `Call`, cancellation checks in the streaming loop, tagged dialog show/restore/dismiss behavior, and no failure toast for deliberate cancellation.
3. Restore an active push dialog from leanback `BaseActivity.onResume()`, matching the established updater lifecycle.
4. Extend focused copy tests for progress delivery and cancellation, then compile/test only the affected Arm64 variants.

### Acceptance criteria

1. A foreground TV shows exactly one custom progress dialog after accepting an APK URL.
2. Known-length downloads show percentage, downloaded/total size, current sampled speed, elapsed time, and estimated remaining time; unknown-length downloads show downloaded size, speed, and elapsed time with indeterminate progress.
3. Cancel is remote-focusable, stops the active OkHttp call and streaming loop, deletes the partial APK, releases the one-job slot, dismisses the dialog, and reports cancellation without a failure message.
4. Successful download reaches 100%, validates the APK, dismisses the progress dialog, and opens the existing Android installer confirmation.
5. Failure dismisses the dialog, deletes the partial file, and preserves the existing concise TV-side error.
6. Activity recreation/resume reuses the tagged dialog and latest job state instead of starting a second job or duplicate dialog.
7. URL, redirect, public-address, proxy, TLS, 512 MiB, storage-reserve, concurrency, and local APK upload behavior remain unchanged.

### Rollback

Revert the follow-up implementation commit/recovery tag. This removes only the progress dialog, cancellation/progress callbacks, and leanback resume hook; the already committed URL-push protocol and bounded background download remain independently usable.

### Follow-up implementation record

- Added shared custom `ApkPushProgressDialog` and `dialog_apk_push_progress.xml`.
  - Shows percentage, transferred/total size, sampled speed, elapsed time, and estimated remaining time when `Content-Length` is known.
  - Uses indeterminate progress with transferred size, speed, and elapsed time when length is unknown.
  - Keeps a single remote-focusable cancel action and cannot be dismissed accidentally with outside touch.
- Extended `ApkUrlPush` to retain and cancel the active OkHttp `Call`, check cancellation inside the streaming loop, publish bounded progress updates, suppress failure reporting for deliberate cancellation, and keep partial-file cleanup and the one-job slot correct across success, failure, and cancellation.
- Added tagged dialog reuse and leanback `BaseActivity.onResume()` restoration, following the existing updater lifecycle without modifying `UpdateDialog`.
- Added focused tests for known-length progress delivery and streaming cancellation while preserving the original size, storage, truncation, and empty-file tests.

### Follow-up verification completed

- `testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ApkUrlPushTest`: pass after the final cancellation cleanup edit.
- `compileMobileArm64_v8aDebugJavaWithJavac`: pass.
- `compileLeanbackArm64_v8aDebugJavaWithJavac`: pass.
- `assembleLeanbackArm64_v8aDebug`: pass.
- `assembleMobileArm64_v8aDebug`: pass.
- Installed the resulting mobile Arm64 APK on `V2453A` (`10CF6H1D2L0009S`) with the installer-assist workflow.
- `pm path com.fongmi.android.tv` confirmed installation, PID `7490` was active, and `.ui.activity.HomeActivity` was the top resumed activity.
- Phone launch screenshot: `/private/tmp/webhtv-apk-progress-test-20260830/phone-launch.png`; the home screen and `推送apk` menu entry rendered normally.
- Focused recent logcat contained no `FATAL EXCEPTION`, `AndroidRuntime`, or package process-crash entry.
- Mobile APK: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`.
- TV APK: `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`.
- Existing 32-bit native-library, resource-namespace, resource-format, compiler deprecation, and Gradle deprecation warnings remain unrelated to this follow-up.

### Follow-up TV verification pending

- Phone installation and launch verification are complete; per user direction, Codex will not install the TV APK.
- `adb connect 192.168.1.9:5555` returns `Connection refused`.
- TV screenshot/UI hierarchy, remote-button cancellation, live speed/time behavior, partial-file deletion, and installer handoff therefore remain device-unverified.

### Long-download failure and screen-on fix

- User observation: the receiver could sleep while the progress dialog was visible, and the tested large APK repeatedly failed near the end.
- Screen-on cause: neither the receiver dialog nor its host activity set `FLAG_KEEP_SCREEN_ON`; background partial wake locks keep CPU work alive but do not keep the display on.
- Screen-on fix: the progress dialog window now owns `FLAG_KEEP_SCREEN_ON`. The flag exists only while that window is attached and is removed with the dismissed dialog.
- TV evidence retrieved read-only from `http://192.168.1.5:9978/debug/logs.txt`:
  - `2026-08-30 17:46:47`: first `apk_url` request accepted for the leanback Arm64 APK.
  - `2026-08-30 17:48:50`: second request accepted for the same URL.
  - `2026-08-30 17:50:07`: `apk-push-url: okhttp3.internal.http2.StreamResetException: stream was reset: INTERNAL_ERROR`.
- Diagnosis: the transfer failed inside the HTTP/2 stream before APK length/package validation. It was not a 512 MiB limit, storage-reserve, truncation comparison, or `PackageManager` parse failure.
- Network fix: the dedicated single-file APK client now advertises HTTP/1.1 only. This avoids the observed mirror/CDN HTTP/2 long-stream reset; HTTP/2 multiplexing has no material benefit for one serial APK transfer.
- Rejected alternative: automatically restarting the full HTTP/2 download would likely repeat the same server behavior and could waste hundreds of megabytes. Range resume is deferred because it requires validator-bound resume semantics across redirects and mirrors.
- Added focused configuration coverage asserting that the APK client exposes only `Protocol.HTTP_1_1`.

## Main integration (2026-08-30)

- Target: `main` at `3005574c10bacff08291df665e19725c5337fa9e`.
- Applied only the patches from `13fe87431c71b259d5c56f853ae597fdd5339441`, `f6d5b30e391642493f1cfb831e1cdc5cc3838b5a`, and `c98638dd36ea8d96dde32bc99aa4e1d3c8b304e6`.
- Explicitly excluded the later WebVTT assessment and implementation commits `6d3c96f3d46cb045e329a23b0da99a54a4de370d` and `e068d0957db8386310cae5dab90b43ae2528e82b`.
- Boundary verification: exactly 19 APK URL-push paths changed, no WebVTT path overlapped, `git diff --check` passed, and the pre-existing `app/.cxx/**` files remained protected.
- Targeted verification: `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ApkUrlPolicyTest --tests com.fongmi.android.tv.server.process.ApkUrlPushTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon` completed with `BUILD SUCCESSFUL`.
