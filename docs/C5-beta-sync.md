# C5：合并 origin/beta 最新应用修复

## Recovery anchor

- 目标：将 `origin/beta@c1c53da674c0e0f2945fdc159de8a6ac4c4fe976` 合并到当前 `dev2`，评审当前全部修改（含已提交未推送提交），修复真实问题并完成验证、提交、推送，最后拉取远端最新代码。
- 状态：实施中；基线为 `dev2@18b39774a3f5879c1b7df3b63a7f154f804854d5`，已执行 `git merge --no-commit --no-ff origin/beta`，无未合并路径。
- 远端基线：`origin/beta@c1c53da674c0e0f2945fdc159de8a6ac4c4fe976`，`origin/dev2@8a1524335f1684df044f74b3aceac58b9c585d63`。基线已有 18 个已提交未推送提交，用户明确要求一并评审和推送。
- 回滚锚点：`18b39774a3f5879c1b7df3b63a7f154f804854d5`；提交前使用 `git merge --abort`，不删除既有 recovery tag 或 stash。
- 任务 guard：`C5-beta-sync`，范围 `app`、`docs`；初始工作树干净，未发现受保护脏路径。
- 接受条件：8 个 beta-only commit 均有完整 disposition；合并树无冲突；历史投影、播放 ownership、Activity recreation、沉浸融合标题和 armv7 MPV 资产契约通过评审与定向验证；已有 C4 改动不被覆盖；task guard 原子提交、recovery tag、当前分支和新 tag 推送成功，并完成 `git pull --ff-only`。
- 下一动作：收集并核验两个只读评审结论；Critical/Important 问题必须修复，修复后重新评审修复差异，循环至通过。

## Authority and scope

- 用户授权：拉取远端 beta 最新代码并合并到当前 dev2；评审已修改代码（包括已提交未推送部分）；发现问题则修复、验证并复审；通过后提交、推送，并拉取远端最新代码。
- 本任务是通用 App/MPV 集成同步，不升级 FFmpeg、Media3、MPV、libplacebo、JNI 或 lock。`armeabi-v7a/libmpv.so` 只接受 beta 已验证的 C2 资产修复，并执行现有双 ABI 资产门禁。
- C4 `d0809f804f812b818bcb22f36cae8634022db673` 及其文档闭环已有独立评审和验证；本轮只确认 beta 无冲突合并未覆盖其路径，不重复未变更代码的逐行评审。
- 本轮重新评审 beta 新增的历史/播放归属/TMDB 标题代码、对应测试、armv7 native 资产和与 C4 最终树的交互。

## Frozen beta ledger

| # | Full commit | Parent(s) | Functional area | Disposition |
| ---: | --- | --- | --- | --- |
| 1 | `342f99c1ff40ff96a34bc59d1ce8852e6f069650` | `4db26d042ba0faa7359f480c6b372a33aad43d47` | Unknown-season history projection | Candidate; review season isolation and duplicate suppression. |
| 2 | `39ac9634ae243a47b8ca45ac98da3d65a96d4346` | `342f99c1ff40ff96a34bc59d1ce8852e6f069650` | Playback ownership restoration after recreation | Candidate; review lifecycle and stale-result isolation. |
| 3 | `119b3aec7345f578a93e1d59bf2a87d0dc61248a` | `b35ff038d26b297e3a1b020c0117cd365b35425f` | Immersive fusion current-episode OSD title | Candidate; review title source and fallback. |
| 4 | `b3fab34cab38f89384b7e1065e27094f07b99ec1` | `39ac9634ae243a47b8ca45ac98da3d65a96d4346` | Playback ownership and push-history key hardening | Candidate; review key stability and source binding. |
| 5 | `701f8ae6fb64351b7f2b86ab2d113d57ac003f9b` | `b35ff038d26b297e3a1b020c0117cd365b35425f 119b3aec7345f578a93e1d59bf2a87d0dc61248a` | Merge PR #193 | Integration-only; disposition follows final tree. |
| 6 | `76b3fe182b5222bd2061719939779a82cf00511e` | `701f8ae6fb64351b7f2b86ab2d113d57ac003f9b b3fab34cab38f89384b7e1065e27094f07b99ec1` | Merge PR #194 | Integration-only; disposition follows final tree. |
| 7 | `6ccc0de7f0060113bc52005b0d48a731407621df` | `119b3aec7345f578a93e1d59bf2a87d0dc61248a` | Restore armv7 C2 P8.1 native asset | Candidate; verify exact asset and both-ABI contract. |
| 8 | `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976` | `76b3fe182b5222bd2061719939779a82cf00511e 6ccc0de7f0060113bc52005b0d48a731407621df` | Merge PR #195 | Integration-only; final beta target. |

## Decision and evidence

- No change keeps the verified C4 tree but omits the latest history projection, playback ownership, fusion-title and armv7 asset fixes.
- Blind beta replacement is rejected because it would discard the current dev2-only C4 update/OCI/APK-push integration and safeguards.
- Selected approach: preserve current dev2 as first parent, merge beta with no-commit/no-fast-forward, resolve any conflict in favor of existing local contracts, and review the final combined tree.
- Exact beta source code/tests and existing WebHTV history/MPV lifecycle contracts are Grade A evidence for this bounded synchronization. Android lifecycle and native asset requirements are checked against the repository's established implementation and verification scripts. No new algorithm, dependency revision or performance claim is introduced, so broad external research is not material.

## Review and validation plan

- Pass 1: independent read-only application review plus native/integration review over the final merge tree.
- For each finding, verify the claim against source, callers and tests; classify valid actionable, valid trade-off or noise. Fix only valid actionable findings.
- After every fix, run the smallest affected test/static check and re-review the complete fix diff. Do not proceed with unresolved Critical/Important findings.
- Minimum final checks: `git diff --check`, conflict-marker scan, focused history/ownership/TMDB tests, Mobile and Leanback Arm64 Java compilation, and `scripts/verify_mpv_native_assets.sh --require-elf` for both ARM ABIs.
- Runtime limitation: build/unit success does not replace device validation; retain existing C2/device limitations unless a new device run supplies evidence.

## Rollout and rollback

- Rollout: create a verified two-parent C5 merge commit and recovery tag, push current `dev2` and that new tag, then run `git pull --ff-only`.
- Before commit: `git merge --abort`.
- After commit: revert the C5 merge commit or restore its recovery tag; previous C4 tags and history remain available.

## Implementation log

- 2026-09-01 Asia/Shanghai: fetched `origin/beta` and `origin/dev2`; remote beta head is `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976`.
- 2026-09-01 Asia/Shanghai: confirmed clean baseline, enumerated all 8 beta-only commits and 13 changed paths including the armv7 asset; `git merge-tree` reported no conflicts.
- 2026-09-01 Asia/Shanghai: started `C5-beta-sync` guard at `18b39774a3f5879c1b7df3b63a7f154f804854d5` and merged beta with `--no-commit --no-ff`; no unmerged paths remain.

## Checkpoint 1: merged beta tree awaiting review

- Source identities: local first parent `18b39774a3f5879c1b7df3b63a7f154f804d5`; beta target `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976`; merge base `b35ff038d26b297e3a1b020c0117cd365b35425f`.
- Workspace: branch `dev2`, `MERGE_HEAD` is beta target, staged merge tree has no unmerged paths; initial worktree was clean.
- Beta paths: armv7 `libmpv.so`, six App Java files, five new/changed tests, and `docs/C2-dv7-p81-bsf.md`.
- Existing unpushed local work remains part of the review/push contract; no unrelated paths are adopted.
- Unresolved: reviewer reports, focused verification, final merge commit/tag and push/pull result.
- Next action: reconcile both reviewer reports against the final tree.

## Checkpoint 2: review reconciliation and validation preparation

- Review result: the external review findings were checked against the final tree; the reported TMDB, fusion-surface, and title issues were contract misreads, not regressions.
- Repairs: removed duplicate Java test method signatures left by the resumed session and added the missing Leanback `canApplyPlayerContentRequest` guard used by both async switch callbacks.
- Validation: `PlaybackOwnershipSourceTest` passed after the repair; the first red run correctly failed on the missing Leanback guard.
- Workspace: branch `dev2`, HEAD remains `18b39774a3f5879c1b7df3b63a7f154f804854d5`, `MERGE_HEAD` remains `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976`; all pre-existing dirty paths remain protected.
- Unresolved: final diff hygiene, focused C5 tests, Mobile/Leanback Arm64 Java compilation, MPV ELF asset gate, merge commit/tag, and authorized remote synchronization.
- Next action: run one final validation batch, then record its exact results and close the C5 guard.

## Checkpoint 3: final validation complete, closure pending

- Repairs: the resumed merge left Leanback's request-state helpers incomplete. The final tree now uses captured request id/generation/context for both player-switch callbacks, clears a stale service-pending player result when a new episode request begins, and releases Leanback's kernel-switch refreshing state before a temporary player-availability check can return.
- Review: both read-only review reports were reconciled against the final tree. The reported TMDB, fusion-surface, and title issues were contract misreads; no unresolved Critical or Important finding remains.
- Validation: `git diff --check HEAD`, conflict-marker scan, and `task_guard.sh check` passed. `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.bean.HistoryPushKeyStabilityTest --tests com.fongmi.android.tv.bean.HistorySeasonSnapshotProjectionTest --tests com.fongmi.android.tv.ui.activity.PlaybackOwnershipSourceTest --tests com.fongmi.android.tv.ui.activity.TmdbDetailInlineOsdTitleTest --tests com.fongmi.android.tv.ui.helper.TmdbUIAdapterTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` passed with 83 tests and both Arm64 Java compilation targets.
- Native validation: `scripts/verify_mpv_native_assets.sh --require-elf` passed for `arm64-v8a` and `armeabi-v7a` using NDK 29 `llvm-readelf` and `llvm-strings`; it verified expected assets, embedded markers, SONAME, and DT_NEEDED rules without rebuilding any native artifact.
- Runtime limitation: no new device playback run was performed in this merge task. Existing C2 device evidence remains valid for the restored armv7 artifact; mobile/Leanback interaction cases still require their normal manual device regression when a device is available.
- Rollback: before closure use `git merge --abort`; after closure revert the two-parent C5 merge commit or restore its task-guard recovery tag.
- Next action: run `task_guard.sh finish` with the recorded validation evidence, then push `dev2` and the newly created recovery tag as authorized by this task.

## Closure: 2026-09-01 Asia/Shanghai

- Merge commit: `fc5b6ba029348c2c06214a80e4c080d6b210269a` (`merge: synchronize beta playback and history fixes`), with first parent `18b39774a3f5879c1b7df3b63a7f154f804854d5` and second parent `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976`.
- Recovery tag: `recovery/C5-beta-sync/20260901135541-fc5b6ba02934`.
- Remote synchronization: pushed `dev2` and the recovery tag to `origin`; `git pull --ff-only` returned `Already up to date`.
- Final status: complete. The only unrelated remaining dirty path is `app/src/main/java/com/fongmi/android/tv/bean/Result.java`; it was protected and excluded from both C5 commits.
