# E7-2 + C3 ISO multi-extent integration

## Recovery anchor

- Objective: make Media3 and the App read an ISO/UDF file stored in multiple allocation extents as one continuous logical file, while preserving the existing single-extent fast path and E7-1 reader safety.
- Authority: approved by the user on 2026-08-27; implementation started at 2026-08-27 17:39:10 CST.
- Lane/scope: `upstream`; Media3 ISO/UDF entry/parser/datasource and required BDMV/DVD callers, App `IsoTrackMetadataResolver`, affected Media3 artifacts/patch/lock, and this task record. Excludes MPV native, FFmpeg, nextlib, unrelated disc correctness, and unrelated cleanup.
- Branch/base: `fongmi-sync` at `9ab038c91adb1b49b34d59a8cbdc292c2df04074`.
- Rollback anchor: `recovery/E7-2-C3/baseline-202608271739-9ab038c91adb`.
- Protected pre-existing dirty paths: `.codex/scripts/task_guard.sh`, `AGENTS.md`, `docs/agents-md-effective-constraints-review-2026-08-21.md`, plus unrelated dirty files in the Media3 working copy.
- Source: `FongMi/media@990abc2368fd74779f525ee345734470659f3d53`, parent `c85d124102c5b25a1bcd270d78f78603e87a6214`; local ISO/UDF origin `39585f19e01324308213e2bdc9aa84dcfa4d5ebc`; Media3 base `e3e922d5c01bc0b564849940fe589daf37360d15`.
- Acceptance: single-extent behavior remains one direct upstream range; multiple recorded extents and unrecorded zero-filled extents are represented in logical order; allocation continuation is bounded; App metadata reads all extents; affected Media3 and product compilation pass. Tests are explicitly omitted by user direction.
- Known evidence limit: no real split ISO/MPLS/CLPI image is available. No test fixture was added or run by user direction, so this task does not claim real-disc behavioral coverage.
- Current status: implemented and committed as `5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f`; recovery tag `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`. The reproducible patch, three affected AAR publications, and Mobile/Leanback arm64 App Java compilation are complete. No tests or real split-disc playback were run by user direction.
- Next action: optional real split ISO/MPLS/CLPI device validation when representative media is available; no implementation work remains in E7-2 + C3.

## Decision and best-practice review

The no-change option leaves a demonstrated single-offset truncation in `UdfFileSystem`, `IsoDataSource`, `BdmvSourceHelper`, and the App resolver. Applying the complete upstream commit unchanged is inappropriate because the current fork already contains the ISO stack plus later WebHTV changes and E7-1 safety fixes. The selected design is a narrow adaptation of the upstream extent contract: retain legacy constructors as the single-extent fast path, add immutable extent arrays, parse standard recorded/unrecorded allocation descriptors with explicit bounds, and migrate only consumers that address an `IsoFileEntry`.

Evidence used:

- Grade A: exact upstream source and diff at `FongMi/media@990abc2368fd74779f525ee345734470659f3d53` define `extentOffsets`, `extentLengths`, zero-filled unrecorded extents, allocation continuation, and a virtual multi-extent datasource.
- Grade A: current WebHTV source shows `IsoTrackMetadataResolver.readEntry()` and BDMV metadata reads using only `entry.byteOffset`, proving the remaining gap.
- Grade A: UDF allocation descriptors distinguish recorded extents, unrecorded extents, and continuation descriptors; the logical file must not be represented by only its first physical offset.
- Grade B: mature UDF readers use an extent map and explicit handling for holes/continuations rather than assuming file contiguity.
- Upstream discussion/PR and field sample evidence were unavailable; this limits real-media confidence but does not change the data-model requirement. Academic papers are inapplicable because this is a filesystem mapping contract, not an algorithmic or performance technique.

Performance decision: keep the existing scalar constructor and one-element arrays, avoid extra upstream reopen operations for the normal single extent, and only traverse/reopen when the file actually has multiple extents. Package-size impact should be source-level only with no new library.

Security/correctness decision: reject invalid lengths/offsets and overflow, bound allocation continuation depth and bytes, detect repeated continuation locations, preserve zero-progress read protection, and cap metadata using the existing 8 MiB resolver limit.

## Implementation history

- Added immutable `extentOffsets`/`extentLengths` while preserving scalar constructors and single-extent behavior.
- Ported bounded UDF allocation descriptor parsing, recorded/unrecorded extents, zero filling, AED continuation loop/depth/size checks, and overflow/no-progress guards.
- Ported the logical multi-extent `IsoDataSource`; single extent still opens one upstream range and reads directly.
- Adapted BDMV M2TS, MPLS/CLPI reads and prefetch; adapted DVD VOB cell/IFO reads without importing later DVD grouping or time-axis redesign.
- Adapted App `IsoTrackMetadataResolver` with the existing 8 MiB cap and strict short-read behavior.
- No tests or synthetic fixtures were added or run by user direction. Real split-disc playback remains an explicit residual validation gap.
- Replayed the full existing Media3 patch order plus `media3-exo-iso-multi-extent.patch` in an isolated clean checkout. Datasource/extractor/exoplayer debug Java compilation passed in 1m48s after cache warm-up; no tests were run per the user's instruction.
- Published only datasource/extractor/exoplayer release artifacts from the isolated checkout; release compilation and publication passed in 2m03s.
- Artifact hashes:
  - datasource AAR `1b6b51e921577cb955db2ce05106a44001ad126b8f4cc67f997d1f3404ec3598`, sources `26306314752ff1b4297aea2f25e6ff0213497404564f23af95ec4bb5c25ada62`, module `7f50a6b1817a68d7ca57899765029fda3fe37d926ee69689a6116fa518d0903d`, POM `b51078a1e63db36711db979356929957a716a01441c182cd772e2b02620a1d52`.
  - extractor AAR `12e03f413c9251109515011249c7151e1f33b55715d7bfad3b8687adcf876c58`, sources `c80e000e7960fca4f256ead3ed830aa1661bdb23d06b1c6ecfae6ac7e3ba73b0`, module `4b7a4df9b09f1e62c5d61f57cbe70ab4c3e14f5d50837e588edf5ee62a931128`, POM `32fb358a5f4ecee7bb58ac8f97b975e8af89cdd19533e35b520533bf76c5979b`.
  - exoplayer AAR `11d572f59f0404878353d9c85e022e153e114ecbb4d43cf61c910cd9e8c2536d`, sources `c2a74d177abff6e91de2b26d0680920a966672796a080572d6477a2e3c9aa6db`, module `f20106bb4c89f0b167dec34a2909daf427149800e14f11db130431e95523e95d`, POM `ca5be4f0911931b82df270917cb91d2f1d221a97bcecd11f0d78eeaa620308a5`.
- Patch hash: `b4954d32f5c98dc5caf2a3cbc8cd72f72d24f4fdeec9425e5061a252d1ccaf68`.
- Implementation commit and recovery tag: `5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f` / `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`.
- App consumption: `:app:compileMobileArm64_v8aDebugJavaWithJavac` and `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` both executed and passed in one Gradle invocation (`BUILD SUCCESSFUL in 1m26s`, 4 executed / 46 up-to-date tasks).
- Performance preservation: legacy scalar constructors remain; a single recorded extent opens one upstream range and does not reopen while reading. Extra range opens and extent traversal occur only for genuinely split files. No benchmark was run, so this is a structural preservation claim rather than a measured performance result.
- Verification deliberately omitted: unit tests, real split ISO/MPLS/CLPI playback, HTTP/SMB/content-URI extent boundary playback, seek/device testing, and SACD cross-extent stripping. These remain residual risks rather than hidden completion claims.
- Rollback: revert `5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f` or restore `recovery/E7-2-C3/baseline-202608271739-9ab038c91adb`; the completed implementation is anchored by `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`. Source patch, lock, three AAR publication directories, App adapter, and documentation must move together.
