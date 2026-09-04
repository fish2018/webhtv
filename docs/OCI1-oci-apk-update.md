# OCI1: OCI APK update source

## Recovery anchor

- Objective: publish each release APK as an OCI artifact and let Android update through OCI Registry mirrors without Docker or an ORAS binary on-device.
- Authority: implementation approved by the user on 2026-08-31.
- Branch / baseline: `dev` / `332f8b26c89e69d19f287b1d911a780826149619`.
- Lane / scope: `upstream`; task guard `OCI1-oci-apk-update` owns the paths declared at guard start.
- Protected pre-existing dirty paths: `app/.cxx/` and all paths recorded by the task guard.
- Acceptance: GitHub discovery remains available; download methods are `oci` and `github`, defaulting to OCI; the other method is always retained as failure fallback; OCI verifies manifest and layer digests, APK identity and signer; progress, cancel, synchronized settings, and mobile/leanback focus work.
- Rollback anchor: baseline commit above. Published clients can be moved back to GitHub by omitting the `downloads.oci` object from a later update manifest.
- Current status: Beta GitHub/OCI publication is verified end to end on `dev`; four APK artifacts and matching update descriptors are public.
- Next action: perform the Android client download scenario with the `free.hubfast.cn` OCI mirror, then decide whether to promote `dev` into `main`.

## Product decision

CNB is not part of the new download route because releases are no longer being published there. Existing CNB helpers may remain for unrelated legacy consumers, but update discovery and APK transfer must not depend on CNB.

Stable and beta releases remain visible in the update dialog, but there is no persistent version-track preference. The update settings dialog owns transport only:

- Source: `oci` or `github`, defaulting to `oci`; legacy `auto` values normalize to `oci`.
- `github`: GitHub first, then OCI when available.
- `oci`: OCI first, then GitHub when available.
- Cross-source fallback is an invariant and is no longer user-disableable.

The existing update JSON remains the discovery and release-notes control plane. Legacy `apk`, `size`, and `sha256` fields remain so older clients continue to update.

## Best-practice review

Decision question: should WebHTV embed an upstream ORAS implementation or implement the narrow OCI Distribution pull flow required for one APK layer?

Hypothesis: a narrow HTTP implementation is safer and smaller on Android because the app only needs anonymous/public manifest and blob pulls, while official ORAS remains appropriate in CI for publishing.

Counter-hypothesis: embedding an ORAS binary or general client library reduces protocol implementation work enough to justify its size, native packaging, maintenance, and security surface.

### Evidence

| Claim | Source | Grade | Applicability and decision impact |
| --- | --- | --- | --- |
| Registry pulls are manifest and blob HTTP operations with standard digest descriptors | OCI Distribution Specification, `https://github.com/opencontainers/distribution-spec/blob/main/spec.md`, accessed 2026-08-31 | A | Supports a narrow Android client instead of a Docker/ORAS runtime |
| An OCI image manifest can describe an artifact type and one APK layer | OCI Image Manifest Specification, `https://github.com/opencontainers/image-spec/blob/main/manifest.md`, accessed 2026-08-31 | A | Defines the exact accepted manifest shape |
| ORAS supports arbitrary artifact media types and is the maintained publishing tool | ORAS documentation, `https://oras.land/docs/how_to_guides/pushing_and_pulling`, accessed 2026-08-31 | A | Use official ORAS in CI; do not reimplement push in the app |
| Public registries commonly use a `WWW-Authenticate: Bearer` token exchange | Distribution token authentication specification, `https://distribution.github.io/distribution/spec/auth/token/`, accessed 2026-08-31 | A | Android client must parse and strictly scope bearer challenges |
| APK identity and signing information are available through Android `PackageManager` / `SigningInfo` | Android SDK API and current WebHTV package validation path, accessed 2026-08-31 | A | Final downloaded bytes must be checked against the installed app identity and signer |
| Content-addressed artifacts still need a trusted root digest | OCI descriptors plus WebHTV update JSON data flow | A/inference | The manifest digest from the update control plane must be verified before parsing proxy content |
| `dockerproxy.net` served a 490,640,394-byte layer in 24.94 seconds and the SHA-256 matched | Reproducible 2026-08-30 proxy test retained in task context | A field test | Suitable first built-in mirror; Range cannot be required because this blob returned 200 |
| `docker.1panel.live`, `docker.jiaxin.site`, `free.hubfast.cn`, and `proxy.vvvv.ee` accepted non-image artifact media types with materially lower or variable speed | Reproducible 2026-08-30 proxy tests retained in task context | A field test | Only qualified alternatives should be presets; availability is not a security assertion |
| `dockerproxy.com` redirected to `dockerproxy.net` and direct TLS was unreliable | Reproducible 2026-08-30 proxy test | A field test | Canonicalize the preset to `.net`; do not ship duplicate `.com` entry |

Applicable source classes covered: exact specifications, official project documentation, current WebHTV code, mature ORAS implementation behavior, and reproducible field tests. Academic papers are inapplicable because this decision implements a standardized content-addressed transport rather than an algorithmic or performance technique. No private-registry implementation is included because credentials must not be embedded in the APK.

## Alternatives

### No change

Keep GitHub APK URLs only. This has the lowest implementation risk but does not solve unreliable or slow domestic GitHub downloads.

### Embed ORAS or a general OCI client

This closely follows upstream tooling but introduces a broad dependency/native binary, more media/platform behavior than the app needs, additional package size, and a larger update-time security surface.

### WebHTV-adapted design (selected)

Use official ORAS CLI only in GitHub Actions. Implement manifest authentication, validation, and one blob stream with the existing OkHttp dependency on Android. Keep routing, progress, cancellation, APK validation, and UI in WebHTV-owned code.

## Release representation

- Manifest media type: `application/vnd.oci.image.manifest.v1+json`.
- Artifact type: `application/vnd.webhtv.apk.v1`.
- Config media type: `application/vnd.oci.empty.v1+json`.
- Exactly one layer with media type `application/vnd.android.package-archive`.
- One artifact per mode/ABI APK. OCI indexes are intentionally deferred.
- Tag: `<release-tag>-<mode>-<abi>`; clients fetch and pin by manifest digest.

The release workflow reads optional repository configuration from `vars.OCI_REPOSITORY` and credentials from `secrets.OCI_USERNAME` / `secrets.OCI_TOKEN`. Missing OCI configuration is fail-open for the existing GitHub release: no invalid OCI metadata is emitted. Once an OCI push succeeds, descriptor verification is fail-closed before it can enter update JSON.

## Update manifest extension

```json
{
  "apk": "https://github.com/.../mobile-arm64_v8a.apk",
  "size": 12345678,
  "sha256": "hex",
  "downloads": {
    "github": { "url": "https://github.com/.../mobile-arm64_v8a.apk" },
    "oci": {
      "registry": "registry-1.docker.io",
      "repository": "owner/webhtv-apk",
      "reference": "v1.2.3-202608310030-mobile-arm64_v8a",
      "manifestDigest": "sha256:hex",
      "layerDigest": "sha256:hex",
      "size": 12345678
    }
  }
}
```

The layer digest must equal the SHA-256 of the APK bytes. The client accepts legacy manifests without `downloads`, using the legacy GitHub `apk` URL.

## Android design

New `com.fongmi.android.tv.update` classes own:

- immutable direct/OCI download targets;
- source preference and route ordering;
- GitHub proxy URL rewriting with explicit full-URL-prefix or strip-scheme modes;
- OCI endpoint normalization and built-in mirror presets;
- a dedicated platform-TLS OkHttp client;
- bearer challenge parsing, manifest validation, and blob streaming;
- a cancellable transfer interface shared by direct and OCI downloads.

`Updater` remains the lifecycle and UI orchestrator. It receives an ordered route list, starts one transfer, preserves progress/cancel behavior, validates the downloaded APK, and tries the next route once on failure.

## Security contracts

- HTTPS is required by default. Custom endpoints cannot contain credentials, query strings, or fragments.
- OCI requests use a dedicated OkHttp client with platform trust. The shared trust-all client is prohibited.
- Manifest requests use `Accept-Encoding: identity` and an exact OCI manifest `Accept` value.
- Raw manifest bytes are SHA-256 verified against the update JSON before JSON parsing.
- Only schema version 2, the expected manifest/artifact media types, and exactly one expected APK layer are accepted.
- Descriptor size/digest and streamed APK size/digest are verified.
- Authentication credentials/tokens are scoped to the exact registry request. Cross-origin redirects never carry `Authorization`.
- Bearer realm URLs must be HTTPS and must match an explicitly allowed authentication host rule.
- No private Registry credential is stored in the app.
- APK package name, version code/name where available, and signing certificate lineage are checked before opening the installer.
- A Range request returning 200 discards partial state and restarts. Resume is deferred from the first implementation because mirror behavior is inconsistent.

Third-party GitHub proxies terminate TLS and can alter unsigned metadata. This implementation therefore applies them to GitHub asset URLs; direct GitHub API/update-manifest discovery remains the trusted default until detached update-manifest signing is introduced as a separate, explicitly reviewed security stage.

## Built-in endpoints

Initial OCI presets:

1. `https://dockerproxy.net` (default).
2. `https://free.hubfast.cn`.
3. `https://docker.jiaxin.site`.

`dockerproxy.com`, endpoints with certificate failures, internal-only DNS, whitelist responses, HTML responses, or confirmed unusable throughput are excluded. Presets are availability hints only; all bytes remain untrusted until digest and APK signature verification succeeds.

GitHub proxy presets retain an explicit rewrite mode. Custom proxy input must pass HTTPS and URL-shape validation before persistence.

## Verification plan

Focused JVM tests with MockWebServer:

- direct and proxied GitHub URL construction;
- route ordering and fallback settings;
- OCI anonymous manifest/blob flow;
- bearer challenge/token retry;
- bad realm, cross-origin redirect, digest mismatch, wrong media type, multiple layers, wrong size, and canceled stream;
- a Range response returning 200 is not treated as a resumed response when resume is later enabled.

Build checks:

- focused update unit tests;
- compile mobile ARM64 debug Java;
- compile leanback ARM64 debug Java.

Device checks:

- open update settings on mobile/leanback-compatible UI;
- select OCI and a mirror, then cancel a real download;
- verify OCI-to-GitHub fallback using a deliberately invalid mirror;
- download a valid APK, verify it, and open the package installer;
- inspect logcat for crashes, leaked credentials, cleartext traffic, and lifecycle errors.

## Rollout and rollback

1. Publish OCI metadata for beta releases only.
2. Ship the client with OCI selected by default and GitHub retained as the unconditional failure fallback; explicit GitHub remains available and falls back to OCI.
3. Promote OCI metadata to stable after one beta cycle and device/network evidence.
4. Operational rollback: omit `downloads.oci` from a later update JSON; all clients use GitHub.
5. Code rollback: revert the atomic task commit/recovery tag; release artifacts and legacy JSON fields remain compatible.

## Implementation checkpoint 0: 2026-08-31 00:30 CST

- Completed: repository/branch/dirty-state recovery, protocol and product decision, proxy evidence consolidation, task guard start.
- Source identities: WebHTV `332f8b26c89e69d19f287b1d911a780826149619`.
- Workspace: `dev`; protected pre-existing `app/.cxx/` paths.
- Validation: assessment only; no code changed before this document.
- Rollback: baseline commit above.
- Unresolved: live OCI publishing requires repository variables/secrets not present in source control.
- Next action: implement release metadata and Android transport types.

## Implementation checkpoint 1: 2026-08-31 01:15 CST

- Completed: optional ORAS release publishing, OCI metadata emission, Android OCI/GitHub routing, independent proxy settings, progress/cancel/fallback, APK identity and signer validation, update settings UI, and focused tests. CNB was removed from update discovery and APK transfer; the optional legacy workflow input remains disabled by default.
- Source identity: ORAS `v1.3.4` resolves to `db9e29505c3059f2b8fde34ae8cae266c5c765e9` (annotated tag object `2f11c9ec2d4816bf0a7a709f7a51ed5ca5d2d5c5`).
- OCI representation: ORAS 1.3.4 was exercised against a local OCI layout. The generated artifact used manifest `application/vnd.oci.image.manifest.v1+json`, artifact type `application/vnd.webhtv.apk.v1`, config `application/vnd.oci.empty.v1+json`, and one `application/vnd.android.package-archive` layer. Verified test manifest digest: `sha256:fdfc0f6efbedf87b00d9a9d2417e8c6e109be6ffdd4bf33d7274e09104d3a221`.
- Validation: `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.update.*'`, mobile ARM64 Java compilation, leanback ARM64 Java compilation, and `:app:assembleMobileArm64_v8aDebug` passed. After the final authentication hardening, the complete update test package passed, followed by focused `OciRegistryClientTest` coverage for a redirected Bearer challenge and rejection of another repository's scope. A final `UpdateRoutePlannerTest` pass verifies that explicit OCI mode never silently uses GitHub when fallback is disabled.
- Release checks: `bash -n .github/scripts/publish-oci-apks.sh` passed; missing OCI configuration exited successfully and wrote exactly `{}`; `.github/workflows/android-release.yml` parsed as YAML; the Android update path contains no CNB reference.
- Artifact: `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`, 144 MB, SHA-256 `413d2d2b34533638d850bd8d69ac8d647fe49373cd88df5d48640d2bd1734bc8`.
- Device limitation: no ADB device was connected, `adb connect 192.168.1.9:5555` was refused, and no emulator command was available. Installer UI, live mirror transfer, cancellation, and real fallback remain beta rollout checks rather than locally verified claims.
- Workspace: branch `dev`, baseline `332f8b26c89e69d19f287b1d911a780826149619`; protected pre-existing `app/.cxx/` content remains unchanged. ARM64 caches generated by task builds were moved to `/private/tmp/webhtv-OCI1-cxx-9aTdgo` instead of being committed or deleting pre-existing data.
- Rollback: revert the atomic task commit or omit `downloads.oci` from update JSON. Legacy GitHub fields remain sufficient for older and rolled-back clients.
- Unresolved: live authenticated OCI publishing and device behavior require repository credentials plus a reachable test device; neither blocks the source-controlled beta-capable implementation.
- Next action: run the final scoped diff/safety check, then create the atomic commit and annotated local recovery tag.

## Deployment checkpoint 2: 2026-08-31 01:39 CST

- Local implementation: commit `d4508dd30ece874c3595df6a80498c861c06f7b0`; annotated recovery tag `recovery/OCI1-oci-apk-update/20260831012035-d4508dd30ece`.
- Device evidence: the current mobile ARM64 debug APK was installed on vivo `V2453A`, launched into `HomeActivity`, and reported version `5.6.0` / code `560` with no app crash or ANR in the focused startup log. Live OCI download remains untested because no release artifact has been published yet.
- Docker Hub: created the dedicated public repository `2011820123/webhtv-apk`. The existing `2011820123/tvbox` repository was deliberately left unchanged because it contains an unrelated TVBox tool image and historical tags.
- Credential validation: ORAS 1.3.4 successfully authenticated to Docker Hub using a temporary registry config outside the repository. The PAT value was never written to a tracked file or documentation.
- GitHub Actions configuration: repository secrets `OCI_USERNAME` and `OCI_TOKEN` are configured for `fish2018/webhtv`; Actions variable `OCI_REPOSITORY` is `2011820123/webhtv-apk`. Secret values are not readable through the repository API.
- Workflow decision: no follow-up YAML change is required. `.github/workflows/android-release.yml` already defaults `publish_oci` to `true`, installs ORAS 1.3.4, pushes every collected release APK, verifies manifest descriptors, and emits OCI metadata only after a successful verified push.
- Remote state: as observed at this checkpoint, `origin/main` remains `332f8b26c89e69d19f287b1d911a780826149619` and no `origin/dev` branch exists. Therefore GitHub cannot use the OCI workflow until the local implementation branch is explicitly authorized for push.
- Security follow-up: the Docker Hub PAT was supplied in chat. Rotate it in Docker Hub after this setup, then replace only the `OCI_TOKEN` Actions secret; no code change is required.
- Rollback: delete the three GitHub Actions settings and optionally delete the still-empty `webhtv-apk` Docker Hub repository. Existing GitHub release publication remains independent.
- Next action: obtain explicit authorization to push `dev` and the recovery tag; then run the release workflow from that branch.

## Deployment checkpoint 3: 2026-08-31 02:05 CST

- Published state: `dev` and both OCI recovery tags were pushed to `fish2018/webhtv`. Local upstream-tracking writes were blocked by the workspace sandbox, but the remote branch and tags were created successfully.
- First Beta run: GitHub Actions run `33326309073` completed successfully at the job level and created a GitHub prerelease, but OCI publication was skipped. `setup-oras@v1` reported `official ORAS CLI releases does not contain version 1.3.4`; the fail-open workflow emitted a warning and generated manifests without `downloads.oci`. CNB sync was skipped as requested.
- Root cause: ORAS `v1.3.4` is an official stable release with Linux AMD64 assets, but the setup Action's version-resolution path did not recognize it. The setup Action documents a supported alternative that accepts a trusted release URL plus SHA-256.
- Fix: pin `https://github.com/oras-project/oras/releases/download/v1.3.4/oras_1.3.4_linux_amd64.tar.gz` with SHA-256 `f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454` instead of the failing `version` input.
- Evidence: the checksum matches the official `oras_1.3.4_checksums.txt`; the archive is a statically linked Linux x86-64 ELF; the same release's macOS CLI reports version `1.3.4` and commit `db9e29505c3059f2b8fde34ae8cae266c5c765e9`.
- Rollback: revert the fixed setup input to the previous fail-open version lookup; GitHub-only release publication remains available but OCI metadata will be absent.
- Next action: parse the workflow, finish the atomic fix, push its commit and recovery tag, then rerun a Beta release and verify Docker Hub descriptors.

## Deployment checkpoint 4: 2026-08-31 02:28 CST

- Setup fix: commit `0b27856ac8ed787747072b2ff25e4715f6ef95c5`; recovery tag `recovery/OCI1-oras-setup-fix/20260831020719-0b27856ac8ed`; both pushed to `origin`.
- Successful run: GitHub Actions `https://github.com/fish2018/webhtv/actions/runs/33327187989` completed in 15m57s. Build, ORAS setup, OCI publishing, manifest generation, GitHub prerelease creation, and workflow artifact upload all passed. The OCI failure-report and CNB sync steps were correctly skipped.
- Release: `v5.6.0-beta-202608310210`, `https://github.com/fish2018/webhtv/releases/tag/v5.6.0-beta-202608310210`. It contains four signed APKs and four update JSON files; every JSON retains the GitHub URL and includes a verified `downloads.oci` descriptor for `2011820123/webhtv-apk`.
- Leanback ARM64: size `133773180`, layer `sha256:946eaa5270ba52ad5aaf6c6c9017ab7607da294cc60f83e2466ab1edc3be8bd4`, manifest `sha256:5abdf621143d55961b2287e6f00e56005df421d28cfee3ddac55125e6a2707e1`.
- Leanback ARMv7: size `112866618`, layer `sha256:7ea8bc27c3df3e6a0e395a313dd30513b4e7ae99a29b9b8f61c0ab81d26913e9`, manifest `sha256:782acccc3aaebdeeaceb97e270abf104ac087f5df1237b3fc504b8fce6840dd6`.
- Mobile ARM64: size `133629314`, layer `sha256:7f48bdb3169112a8e6dedb975dee5e4eb8a8a4c46d0970f9b1579ea5bc4db322`, manifest `sha256:bd0446240520bb8f4d183bce8433cede1f8fe1cb45139efc63d965eb5b89f5bd`.
- Mobile ARMv7: size `112722752`, layer `sha256:014cf7057f06c19fa725462eea11172926ed839594ec9798eeea52c694c86d5c`, manifest `sha256:7d1de6ae523a19fda1ced3fc0e0a9534668a47733e12598e4a7f669a579e08eb`.
- Registry verification: ORAS fetched every public Docker Hub descriptor by tag and every manifest by digest. All four manifest digests, one-layer APK media types, layer digests, and layer sizes exactly matched the release JSON.
- Mirror verification: `free.hubfast.cn` returned the new manifest by both tag and digest; the raw Mobile ARM64 manifest SHA-256 matched `bd0446240520bb8f4d183bce8433cede1f8fe1cb45139efc63d965eb5b89f5bd`. At the same observation point, `dockerproxy.net` returned HTTP 502 for both forms and `docker.jiaxin.site` returned HTTP 401. Use `free.hubfast.cn` for the first device test; OCI mode still falls back to GitHub on mirror failure.
- Non-blocking warnings: GitHub reports Node.js 20 compatibility forcing for several Actions and recommends `actions/setup-java@v5`; these warnings did not affect this release and are outside the OCI functional fix.
- Rollback: remove `downloads.oci` from a future manifest or disable `publish_oci`; the GitHub release path remains complete. The published OCI objects are content-addressed and can remain as harmless beta history.
- Next action: on a device with an older version code, select Beta + OCI + `free.hubfast.cn`, verify full APK transfer/cancel/install, then promote the verified implementation to `main` if accepted.

## Update settings simplification: 2026-08-31 10:15 CST

Decision question: how should the update settings UI expose the two download transports without mixing release selection, transport routing, and fallback policy?

### Evidence and current flow

| Evidence | Source / revision | Grade | Decision impact |
| --- | --- | --- | --- |
| Stable and beta manifests are fetched concurrently; the saved channel only chooses the initially expanded update item | `Updater.doInBackground`, `getPreferredUpdate`, `onChannel` at `2de49b6dddfebdb2653d0568df13244993be8731` | A | Remove the channel preference from settings without removing beta discovery or manual beta selection |
| APK route construction is independent from release discovery | `Updater.getRoutes` and `UpdateRoutePlanner.plan` at the same revision | A | Keep transport selection in update settings and make fallback an invariant |
| Fixed tabs expose all options concurrently and `OnTabSelectedListener` is the supported selection callback | Material Components `TabLayout.java`, upstream `master`, accessed 2026-08-31 | A | Use one fixed two-tab control and render only the selected transport panel |
| Directional navigation should be tested with a D-pad and explicit `nextFocus*` targets used when default focus movement is unsuitable | Android Developers, keyboard navigation guide, accessed 2026-08-31 | A | Provide visible focused tab styling and explicit up/down focus paths on leanback |
| WebHTV already adapts Material tabs for TV by making tab child views focusable and applying `selector_mpv_tab_focus` | `MpvConfigDialog` and `PlaybackPerformanceDialog` at the same revision | A | Reuse the established local focus pattern instead of adding a new widget abstraction |
| One-key sync includes only keys in `Backup.APP_PREFS`; current update source and proxy keys are absent | `Backup.include` / `APP_PREFS` at the same revision | A | Add the active update transport and proxy keys to the app-settings allowlist and test them |

Exact upstream feature commits, upstream PR/revert history, and academic/benchmark evidence are inapplicable: this stage changes a local settings presentation and preference policy without introducing a dependency, protocol, algorithm, or performance technique. The current application code and the established Material/Android navigation contracts decide the implementation.

### Alternatives

- No change: preserves existing controls but continues to mix a weak version preference with transport settings, exposes a redundant auto mode, and lets users disable the only recovery path.
- Keep segmented buttons and hide unrelated controls: smaller layout change, but does not provide the requested tab/view relationship.
- WebHTV-adapted fixed tabs (selected): OCI and GitHub are peer tabs; each tab owns only its proxy controls; source selection persists; the opposite source is always appended as fallback; TV focus uses the project's existing tab focus selector and explicit directional navigation.

### Acceptance and rollback

- No version-channel control or persisted default-channel behavior remains in update settings.
- OCI is the default for new installs and for legacy `auto` values; GitHub remains explicitly selectable.
- Only the selected source's proxy/mirror controls are visible.
- There is no fallback switch; an available opposite transport is always tried after the selected transport fails.
- About-dialog action order is check update, acknowledge, update settings.
- Leanback tabs show a visible focus state and all controls are reachable by D-pad; mobile touch behavior remains intact.
- One-key sync with app settings includes source, GitHub proxy URL/mode, and OCI mirror URL.
- Rollback: revert this stage commit. Existing stored `update_channel`, `update_fallback`, and `auto` values are harmless; the reverted code can read them again.

### Implementation checkpoint 5: 2026-08-31 10:25 CST

- Implemented: removed the persistent version-track setting, replaced auto/GitHub/OCI controls with fixed OCI/GitHub tabs, defaulted legacy/unknown sources to OCI, made cross-source fallback unconditional, and rendered only the selected transport's proxy controls.
- About dialog: action order is now check update, acknowledge, update settings; explicit left/right focus links keep the settings icon reachable after the acknowledge button.
- Leanback focus: tab child views reuse `selector_mpv_tab_focus`; close, selected tab, active proxy control, and save have explicit D-pad transition handlers. Mobile remains touch-selectable through Material `TabLayout`.
- One-key sync: `Backup.APP_PREFS` now includes `update_source`, GitHub proxy id/URL/mode, and OCI mirror id/URL; retired `update_channel` and `update_fallback` remain excluded.
- Verification: focused `UpdateRoutePlannerTest` and `BackupPreferenceFilterTest` passed; mobile ARM64 and leanback ARM64 debug Java compilation passed in the same Gradle invocation. XML parsing passed before the build, and Android resource processing plus ViewBinding generation succeeded for both variants.
- Device limitation: `adb devices -l` returned no connected devices. Real TV D-pad focus appearance and mobile touch screenshots remain unverified device scenarios; no claim of physical-device testing is made.
- Protected state: the 35 pre-existing `app/.cxx/` paths remain outside task scope.
- Rollback: revert the atomic stage commit/recovery tag; no manifest, Registry artifact, or remote release needs to change.
