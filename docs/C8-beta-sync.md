# C8: merge origin/beta and review current unpushed changes

## Recovery anchor

- Objective: merge the latest `origin/beta` into `dev2`, review the effective tree including the local unpushed commit and current task changes, fix actionable defects, verify, review the repaired tree again, commit, push, and pull the latest remote state.
- Status: C8 is complete and pushed; beta merge `5cf2f2e7fddd48454d10b86c27cc9f02e979098a` is preserved, post-merge repairs and final verification passed, and the repair commit/tag are synchronized to `origin/dev2`.
- Branch and baseline: `dev2`, base `b235cee7408b8531c257f94fb65b0b4f4068d4c2`.
- Beta target: `origin/beta@308694aaadd59d9d1ef230bded83cf84dafa114c`.
- Scope: `app/**` and `docs/**`; unrelated pre-existing paths remain protected and are not part of this task.
- Next action: code task closed; retain the recovery tag as the local rollback anchor. Optional device playback follow-up is outside this task's acceptance gate.

## Authority and review boundary

- User-authorized operations: fetch `origin/beta`, merge it into `dev2`, review committed-but-unpushed and working-tree changes, repair actionable issues, verify, commit, push the current `dev2`, and pull the latest remote code.
- No native dependency, lock, AAR, APK, `.so`, FFmpeg, Media3, MPV, JNI, or patch ownership change is authorized by this task.
- Effective review tree: the staged merge result plus the unstaged changes relative to `HEAD`; the review includes the final contents of all changed `app/**` files.
- Rollback before commit: `git merge --abort`.
- Rollback after commit: revert the resulting merge commit as a two-parent merge with `git revert -m 1 <merge-commit>`; the task recovery tag is the local restore point.

## Source commit ledger

| # | Repository/ref | Full commit | Functional area | Disposition |
| ---: | --- | --- | --- | --- |
| 1 | `origin/beta` | `d8910cc35a27c5761f8618ada758dac4efe1dca4` | automatic ending skip and segment-kind handling | merged; review final tree |
| 2 | `origin/beta` | `d1878923682800c448f946e07c847971a1d0d066` | skip landing, deduplication, and notices | merged; review final tree |
| 3 | `origin/beta` | `381266a579404087ac55de9af2bd99ac3c4f1bf1` | second-round skip state-machine correction | merged; review final tree |
| 4 | `origin/beta` | `624e0b16a33c765b1f9d4a96422fc48e4c403340` | player mask/preload regression repair | merged; review final tree |
| 5 | `origin/beta` | `fddca6e60d6ab82f690484ca552cc8bc26210153` | tolerate unreadable archive signing metadata | merged; security review required |
| 6 | `origin/beta` | `59fd2688f79d4e6ef46da23a162c8236920629e6` | narrow signature-validation tolerance | merged; security review required |
| 7 | `origin/beta` | `308694aaadd59d9d1ef230bded83cf84dafa114c` | beta publication merge target | merge parent; final tree under review |

## Current findings and repairs

### Finding 1: missing production contract for checksum-gated signature fallback

- Evidence: `UpdaterFutureTest.unreadableArchiveSignatureRequiresVerifiedChecksum` references `Updater.canAcceptUnreadableArchiveSignature(boolean)`, but the final production tree did not define it. The targeted Gradle pass failed during `compileMobileArm64_v8aDebugJavaWithJavac` before tests ran.
- Root cause: the test and the intended security rule were added without wiring the rule through `validate()` and `signaturesMatch()`.
- Required behavior: an unreadable installed signature is always rejected; an unreadable candidate signature is accepted only when the downloaded APK SHA-256 matches a non-empty manifest digest; readable signatures still require signer compatibility.
- Repair status: implemented in `app/src/main/java/com/fongmi/android/tv/Updater.java` and covered by `UpdaterFutureTest`.

## Alternatives and decision

- No change: rejected because the current tree does not compile and the fallback rule is not enforceable.
- Unmodified beta tolerance: rejected because it allows an unreadable candidate signature without requiring the manifest checksum to be verified.
- Narrow WebHTV adaptation: use the existing SHA-256 validation result as an explicit boolean passed into package/signature validation; keep the OS installer as the final platform gate and preserve OEM compatibility only for the candidate side.

## Acceptance criteria

1. The final merge tree has no unresolved conflict entries or conflict markers.
2. Candidate signature metadata may be unreadable only after a matching non-empty SHA-256 has been verified; missing or mismatched checksums are rejected.
3. The targeted `Updater`, intro-skip, reader-history, and routing tests pass with zero failures/errors/skips.
4. Mobile and Leanback Arm64 Java compilation passes.
5. A second review of the repaired effective diff finds no Critical/Important issues.
6. The task guard creates one atomic merge commit and an annotated local recovery tag, then the current branch and newly created tag are pushed, followed by a fast-forward pull of the latest remote state.

## Verification log

- 2026-09-03 Asia/Shanghai: fetched `origin/beta`; target is `308694aaadd59d9d1ef230bded83cf84dafa114c`.
- 2026-09-03 Asia/Shanghai: `git merge --no-commit --no-ff origin/beta` completed automatically with no unmerged paths.
- 2026-09-03 Asia/Shanghai: RED targeted Gradle pass failed at Java compilation because `Updater.canAcceptUnreadableArchiveSignature(boolean)` was missing.
- 2026-09-03 Asia/Shanghai: added the checksum-gated signature fallback and fixed the first compile pass; the next compile exposed two stale `firstContent` calls and a missing `Segment.createTrailing` identity parameter, both repaired.
- 2026-09-03 Asia/Shanghai: the following compile reached test compilation but failed because `ReaderHistoryProgressTest` calls the not-yet-implemented `NovelRouter.alignResolvedHistoryProgress(History, History, String)`.
- 2026-09-03 Asia/Shanghai: the next test pass compiled production and test sources but reported six failures: stale source assertions, eager `Looper` initialization, missing legacy reader-key migration, and the expected three cancellable reader entry points. Root causes are recorded for the next repair.
- 2026-09-03 Asia/Shanghai: the following pass had 62 target tests passing and one stale source-count assertion failing; the final tree contains four `readerPayload` assignment entry points, so the expected count was corrected from 3 to 4.
- 2026-09-03 Asia/Shanghai: second-pass semantic review found two actionable risks still to test: TMDB intro-skip callbacks lacked the same live-owner guard as the other playback activities, and `Segment.withinDistance` could overflow for extreme long values.
- 2026-09-03 Asia/Shanghai: independent reviewer agents timed out without a usable report; no pass verdict is inferred from the timeout.
- 2026-09-03 Asia/Shanghai: the interrupted session committed the conflict-free beta merge as `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; post-merge review repairs remain in the worktree for one final atomic closure commit.
- 2026-09-03 Asia/Shanghai: `assembleDebug` completed successfully across the configured debug variants (335 tasks); this is compile/package evidence only. A root `test --tests` invocation failed because the aggregate `:app:test` task does not accept `--tests`, so it is an invocation error rather than a regression result.
- 2026-09-03 Asia/Shanghai: the focused Mobile Arm64 test run completed with 72 tests and 0 failures/errors/skips; the subsequent `duration=0` IntroSkip regression also completed with `BUILD SUCCESSFUL`.
- 2026-09-03 Asia/Shanghai: final semantic review found no additional Critical/Important issue; the independent reviewer timed out without a report, so no pass verdict is attributed to that reviewer.
- 2026-09-03 Asia/Shanghai: the final combined command returned `exit 0` with `BUILD SUCCESSFUL in 2m 26s`; `:app:compileMobileArm64_v8aDebugJavaWithJavac` and `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` completed, and the selected Mobile Arm64 XML reported 73 tests, 0 failures, 0 errors, and 0 skips.

## Current status

- Merge: committed as `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`, with no unresolved paths or conflict markers.
- Code review: local root-cause review repaired the checksum gate, reader routing/cancellation/progress boundaries, IntroSkip lifecycle/cache/parser boundaries, and TMDB callback ownership; no further Critical/Important issue was found in the final semantic pass.
- Verification: `assembleDebug` succeeded; the focused Mobile Arm64 run passed 72/72, the added `duration=0` IntroSkip regression passed separately, and the final combined dual Arm64 Java compile plus focused test command passed with 73/73 and no skips/errors.
- Commit/push/pull: repair commit `1d08c6fba24763023bf51792d344a3912b6d3cdb` and recovery tag `recovery/C8-beta-sync/20260903160741-1d08c6fba247` were created by task guard, pushed to `origin/dev2`, and followed by `git pull --ff-only` returning `Already up to date`.

## Checkpoint 1: 2026-09-03 13:18 Asia/Shanghai - resumed C8 diagnosis

- Completed: reconciled the prior Orca transcript with the authoritative workspace; confirmed the beta merge target, current HEAD, staged merge result, and unstaged repair layer.
- Source identities: local `HEAD=b235cee7408b8531c257f94fb65b0b4f4068d4c2`; `MERGE_HEAD=308694aaadd59d9d1ef230bded83cf84dafa114c`; `origin/beta=308694aaadd59d9d1ef230bded83cf84dafa114c`.
- Decisions/evidence: the prior 62/63 targeted pass is historical only; the later lifecycle and overflow regressions have not yet been run. Current source inspection confirms remaining actionable issues in TMDB intro-skip lifecycle gates, cancellable reader history writes, cross-source reader progress typing/initialization, unknown trailing endpoints, monotonic timeout, raw cache reuse, and malformed numeric/version fields.
- Workspace: branch `dev2`; merge uncommitted; no unmerged index entries; task guard `C8-beta-sync` active with scope `app` and `docs`.
- Files/artifacts changed: existing staged merge and repair files under `app/**`; `docs/C8-beta-sync.md` records this checkpoint. No native, lock, binary, or dependency ownership changes are in scope.
- Validation: checkpoint script previously reported one missing checkpoint; all current worktree/index diffs passed whitespace checks; targeted tests/compilation after this resumed session remain unverified.
- Rollback anchor: before commit use `git merge --abort`; after commit revert the two-parent merge with `git revert -m 1 <merge-commit>`; recovery tag is created by task guard finish.
- Unresolved: final combined verification, task-guard repair commit/recovery tag, push, and fast-forward pull remain.
- Next action: run the final combined verification once.

## Checkpoint 2: 2026-09-03 18:06 Asia/Shanghai - merge commit reconciled

- Completed: reconciled the interrupted session with the authoritative repository; confirmed that the beta merge now exists as a two-parent commit and that the later review repairs are still a separate worktree layer.
- Source identities: `HEAD=5cf2f2e7fddd48454d10b86c27cc9f02e979098a`, parents `b235cee7408b8531c257f94fb65b0b4f4068d4c2` and `308694aaadd59d9d1ef230bded83cf84dafa114c`; no `MERGE_HEAD` remains.
- Decisions/evidence: retain the merge commit and complete the bounded repair layer; do not redo the beta merge or historical assessment. The failed root `test --tests` command is classified as an invocation error, while `assembleDebug` is fresh successful compile/package evidence.
- Workspace: branch `dev2`; task guard `C8-beta-sync` remains active with scope `app` and `docs`; post-merge source, test, and documentation repairs remain uncommitted.
- Files/artifacts changed: only existing C8 paths under `app/**` and `docs/**`; no native artifact, lock, dependency, or binary ownership change.
- Validation: `assembleDebug` succeeded in 12m48s with 335 actionable tasks; the focused Mobile Arm64 run passed 72/72, and the separate `duration=0` regression passed with a normal `BUILD SUCCESSFUL` exit.
- Rollback anchor: revert `5cf2f2e7fddd48454d10b86c27cc9f02e979098a` with `git revert -m 1` after closing the repair set; the task-guard recovery tag remains required before push.
- Unresolved: final combined verification, task-guard closure/tag, push, and fast-forward pull.
- Next action: run the final combined verification once and record its exact result.

## Checkpoint 3: 2026-09-03 20:20 Asia/Shanghai - repair set ready for final verification

- Completed: implemented the demonstrated C8 repairs and reconciled the final source/test boundary; removed one duplicate static test import. The beta merge remains preserved as the two-parent commit `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`.
- Source identities: local `HEAD=5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; parents `b235cee7408b8531c257f94fb65b0b4f4068d4c2` and `308694aaadd59d9d1ef230bded83cf84dafa114c`; beta target unchanged at `308694aaadd59d9d1ef230bded83cf84dafa114c`.
- Decisions/evidence: retain the conservative legacy reader-key behavior because untyped legacy rows cannot be distinguished from playback rows; reject unknown IntroSkip trailing segments as file-ending actions until a valid duration exists; accept the independent reviewer timeout as unavailable evidence, not a pass.
- Workspace: branch `dev2`; task guard `C8-beta-sync` active with base head set to `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; no unmerged paths; only `app/**` and `docs/**` task changes remain.
- Files/artifacts changed: source and tests under `app/**`, the C8 task/assessment documents under `docs/**`; no native, lock, AAR, APK, `.so`, FFmpeg, Media3, MPV, JNI, or dependency ownership change.
- Validation: prior focused run passed 72/72; the new `IntroSkipPlaybackStateTest` duration-zero regression passed separately; `assembleDebug` passed. Final combined dual-product Java compilation and 73-test run remain unverified.
- Rollback anchor: preserve `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; after the repair closure commit, revert the merge with `git revert -m 1 5cf2f2e7fddd48454d10b86c27cc9f02e979098a` and revert the repair commit if the review-only fixes also need removal.
- Unresolved: final combined verification, task-guard atomic repair commit, annotated recovery tag, push, and fast-forward pull.
- Next action: run `:app:compileMobileArm64_v8aDebugJavaWithJavac`, `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`, and the 73-test focused Mobile Arm64 unit-test set in one Gradle invocation.


## Checkpoint 4: 2026-09-03 20:53 Asia/Shanghai - final verification passed

- Completed: ran the final combined verification after all code and test edits. Both Mobile and Leanback Arm64 Java compilation tasks completed, and the focused Mobile Arm64 test set passed.
- Source identities: local `HEAD=5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; parents `b235cee7408b8531c257f94fb65b0b4f4068d4c2` and `308694aaadd59d9d1ef230bded83cf84dafa114c`; beta target remains `308694aaadd59d9d1ef230bded83cf84dafa114c`.
- Decisions/evidence: no further production change after the `duration=0` guard; untyped legacy reader keys remain intentionally rejected because they cannot be distinguished from playback rows. The independent reviewer timeout remains unavailable evidence, not a pass.
- Workspace: branch `dev2`; task guard `C8-beta-sync` active with base `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; no unmerged paths; task changes are confined to `app/**` and `docs/**`.
- Files/artifacts changed: IntroSkip lifecycle/cache/parser logic, update manifest/signature validation, reader routing/cancellation/progress handling, mobile/Leanback/TMDB callback guards, regression tests, and C8 documentation; no native/lock/AAR/APK/.so/dependency ownership change.
- Validation: final command `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest` with six `--tests` selectors returned `BUILD SUCCESSFUL in 2m 26s`; XML totals are 73 tests, 0 failures, 0 errors, 0 skips.
- Rollback anchor: retain merge commit `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; after the repair commit is created, revert the repair commit and use `git revert -m 1 5cf2f2e7fddd48454d10b86c27cc9f02e979098a` to remove the beta merge if required.
- Unresolved: none for the approved code-sync scope; device playback regression remains an optional follow-up outside this task's acceptance gate.
- Next action: code task closed; retain the recovery tag as the rollback anchor.


## Checkpoint 5: 2026-09-04 00:10 Asia/Shanghai - task closed and synchronized

- Completed: task guard committed the repaired C8 tree atomically and created the annotated recovery tag; the current branch and tag were pushed successfully.
- Source identities: repair commit `1d08c6fba24763023bf51792d344a3912b6d3cdb`; recovery tag `recovery/C8-beta-sync/20260903160741-1d08c6fba247`; beta merge remains the two-parent commit `5cf2f2e7fddd48454d10b86c27cc9f02e979098a`; beta target remains `308694aaadd59d9d1ef230bded83cf84dafa114c`.
- Validation: final combined Mobile/Leanback Arm64 Java compilation and six-selector Mobile Arm64 test command returned `BUILD SUCCESSFUL in 2m 26s`; XML totals were 73 tests, 0 failures, 0 errors, and 0 skips; `git diff --check` passed.
- Remote: pushed `dev2` and the recovery tag with Git HTTP/1.1; `git pull --ff-only` returned `Already up to date`.
- Workspace: the approved C8 scope is closed; no native, lock, AAR, APK, `.so`, FFmpeg, Media3, MPV, JNI, or dependency ownership change was made. Device playback remains an optional follow-up, not an acceptance requirement for this sync task.
- Rollback: use the recovery tag for the verified restore point; revert the repair commit and, if needed, revert the beta merge with `git revert -m 1 5cf2f2e7fddd48454d10b86c27cc9f02e979098a`.
