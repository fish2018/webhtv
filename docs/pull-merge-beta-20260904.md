# Pull merge beta 2026-09-04

## Recovery anchor

Objective: merge `origin/beta` into `dev4` and verify the resulting merge.
Acceptance: worktree is clean, `dev4` contains `fbbefb75dbcbce1fcec259bb3a81a5e2e889362e`, targeted validation evidence is recorded.

## Status

- 2026-09-04: fast-forward merged `origin/beta` into `dev4` to `fbbefb75dbcbce1fcec259bb3a81a5e2e889362e`; no conflicts.
- 2026-09-04: `:app:testMobileArm64_v8aDebugUnitTest` found 4118 tests with 3 failures.
  - `FfmpegVc1SupportTest` (2 failures): `video/wvc1` no longer maps to `vc1` and no longer returns initialization data. This is the known C10 upstream-binary alignment tradeoff recorded in `docs/C10-binary-upstream-alignment.md`; it is not introduced by this merge.
  - `ReaderPlaybackRoutingSourceTest.readerProgressRefreshesHistoryAfterEverySave`: the literal contains an LF, while the checked-out source uses CRLF.
- 2026-09-04: made the Reader source assertion CRLF-tolerant; targeted `testMobileArm64_v8aDebugUnitTest` run completed 28 tests with the two known C10 `wvc1` failures only, so the 25 Reader tests passed.
- `git diff --check` passed.
- Rollback: merge state is inherently present in the fast-forward history; the Reader test repair can be reverted independently from this commit.
- Unresolved: the two C10 `FfmpegVc1SupportTest` failures are pre-existing and belong to the upstream binary alignment tradeoff, not this merge task.
