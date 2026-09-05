# C4: fish2018/main 上游应用合并

## Recovery anchor

- 目标：将 `fish2018/webhtv:main@ec478b0b697422a7785171c7b51a35b7a526564e` 的有效增量合并到当前 `dev2`，保留 WebHTV 的本地播放器、评估和文档资产。
- 状态：已完成；两父合并提交 `d0809f804f812b818bcb22f36cae8634022db673`，recovery tag `recovery/C4/20260901032617-d0809f804f81`。
- 基线：`dev2@0452b2256b263ae7d7ec528cee7d5de5efabdb59`；共同祖先 `4489ca9ecc91c2c30fd23610cb0342aa1224717b`；回滚锚点为该基线及 `recovery/merge-beta-20260831/20260831120515-0452b2256b26`。
- 任务 guard：`C4`，范围 `.github`、`.gitignore`、`app`、`docs`；初始工作树干净。
- 接受条件：合并树无未解决冲突或冲突标记；本地评估文档不被删除；受影响的 Java/Kotlin 代码可编译；上游已有的聚焦单测可通过；原子提交和本地 annotated recovery tag 已创建。
- 下一动作：在具备对应设备和网络环境时，补做真实 OCI 下载与局域网 APK URL 推送验收；该实机验证不阻塞已完成的 C4 本地集成。

## Authority and scope

- 用户授权：合并 `https://github.com/fish2018/webhtv/tree/main`。
- 实施策略：使用真实 merge commit 保留上游可追溯性；保留当前 `dev2` 对上游应用代码后的本地演进；不把上游 `main` 的“临时文档清理”作为删除本仓库评估、测试或任务记录的授权。
- 不在本任务中：升级 FFmpeg、Media3、MPV、libplacebo、JNI 或重新构建 native 资产；这些仍按已分配的 `E*`、`P*`、`C*` 阶段另行决策。
- 风险边界：上游 `main` 含 MPV 字幕/Surface 相关 App 代码和 `armeabi-v7a/libmpv.so` 资产变更；本地现有 MPV 生命周期、DV、AudioTrack 和双 ABI 契约优先，冲突仅做行为兼容组合，不整树覆盖。

## Frozen sources

| Role | Repository/ref | Full commit |
| --- | --- | --- |
| Local baseline | `dev2` | `0452b2256b263ae7d7ec528cee7d5de5efabdb59` |
| Common ancestor | `fish2018/webhtv` | `4489ca9ecc91c2c30fd23610cb0342aa1224717b` |
| Upstream target | `fish2018/webhtv:main` | `ec478b0b697422a7785171c7b51a35b7a526564e` |
| Pre-target merge parent | `fish2018/webhtv` | `3a408780f848f2888dfd5bf1cef4889f22811269` |

## Complete upstream ledger

| # | Full commit | Functional area | Disposition | C4 decision |
| ---: | --- | --- | --- | --- |
| 1 | `3a408780f848f2888dfd5bf1cef4889f22811269` | Merge `fongmi-sync` into `main`; deletes temporary docs relative to its first parent | partial | Preserve its application/player changes through the merge, retain current `docs/` task records and assessment index. |
| 2 | `23a3c74417fdcc107ad8efc43ca366482af89e58` | MPV direct subtitle controls and armv7 parity | candidate | Merge with local MPV subtitle/lifecycle behavior preserved. |
| 3 | `ece528179af7ac7a00b27c1347472e533ccd9b4b` | MPV subtitle and transient Surface lifecycle | candidate | Merge as a narrow App-layer complement; retain local teardown and DV safeguards. |
| 4 | `4f801a1e50223e30344da4083659a82d5878e4e4` | Restore subtitles before autoplay | candidate | Merge with local autoplay pause-race contract preserved. |
| 5 | `3005574c10bacff08291df665e19725c5337fa9e` | Preselect persisted subtitle before load | candidate | Merge with local track-selection behavior preserved. |
| 6 | `332f8b26c89e69d19f287b1d911a780826149619` | Local-network APK URL push | candidate | Merge together with its policy, dialogs and tests. |
| 7 | `d4508dd30ece874c3595df6a80498c861c06f7b0` | OCI APK update source | candidate | Merge together with OCI registry/auth tests and release workflow additions. |
| 8 | `e8dba9968ea788784f0ad460c80fbc1fdb2ee5cb` | OCI publishing documentation | candidate | Merge documentation as evidence for the implementation. |
| 9 | `0b27856ac8ed787747072b2ff25e4715f6ef95c5` | Pin ORAS release asset | candidate | Merge as a release workflow correctness fix. |
| 10 | `2de49b6dddfebdb2653d0568df13244993be8731` | OCI beta publication record | candidate | Merge documentation as provenance for the OCI source. |
| 11 | `dae010645655dacc1747e52de3ebbd860a58f930` | Simplify update download settings | candidate | Merge with the OCI update source and existing update configuration retained. |
| 12 | `ec478b0b697422a7785171c7b51a35b7a526564e` | Ignore local `docs/` directory | adapted | Keep only the root ignore rule if compatible; never delete currently tracked documentation. |

## Decision and validation

- No-change alternative: retains current `dev2`, but misses update-source, APK-push, subtitle-selection, reader, and rule-safety improvements from the upstream chain.
- Unmodified upstream-tree alternative: rejected because it deletes current evaluated task records and risks regressing local `dev2` features through a divergent 1,808-commit branch history.
- Selected approach: merge upstream history, resolve each conflict by preserving current local contracts while admitting upstream additions, and retain the local documentation ledger.
- Cheapest decisive verification: `git diff --check`, targeted unit tests for the new update/APK-push/MPV policy code, and `:app:compileMobileArm64_v8aDebugJavaWithJavac`.
- Rollback: revert the C4 merge commit or reset an uncommitted merge to `0452b2256b263ae7d7ec528cee7d5de5efabdb59`; the guard-created recovery tag identifies the final verified state.

## Implementation log

- 2026-08-31 Asia/Shanghai: frozen upstream target, validated clean baseline, enumerated all 12 non-ancestor commits, and started `C4` upstream task guard.
- 2026-08-31 Asia/Shanghai: completed the no-commit merge, retained all tracked local task documentation, combined the update/OCI and MPV subtitle paths, and resolved all Git conflicts. The staged tree contains the upstream application increment; resource additions and the backup preference-prefix fix are pending the focused build.
- 2026-09-01 Asia/Shanghai: focused `:app:testMobileArm64_v8aDebugUnitTest` completed successfully with Java compilation and 251 tests/0 failures covering update/OCI, APK URL push, MPV policy, and backup filtering. The first two attempts exposed and fixed merge-only resource/model/layout gaps; the final run passed. `scripts/verify_mpv_native_assets.sh --require-elf` also passed for both ARM ABIs, including ELF SONAME/DT_NEEDED and embedded contract checks; only the repository's existing 32-bit native-library warning was emitted by Gradle.
- 2026-09-01 Asia/Shanghai: independent review found two release-pipeline issues: requested OCI publication could fail open, and `oras-project/setup-oras@v1` was mutable. The workflow now fails closed when OCI setup, configuration, or publication fails and pins setup-oras to official commit `22ce207df3b08e061f537244349aac6ae1d214f6`. A pre-fix assertion failed on all three conditions; the post-fix pass verified shell syntax, missing-configuration failure, the immutable Action pin, workflow structure, and staged/unstaged diff checks.
- 2026-09-01 Asia/Shanghai: `task_guard.sh finish` created two-parent merge commit `d0809f804f812b818bcb22f36cae8634022db673` and annotated local tag `recovery/C4/20260901032617-d0809f804f81`; no remote push was performed.

## Checkpoint 1: merged tree before focused verification

- Source identities: local `dev2@0452b2256b263ae7d7ec528cee7d5de5efabdb59`; upstream `fish2018/main@ec478b0b697422a7785171c7b51a35b7a526564e`; common ancestor `4489ca9ecc91c2c30fd23610cb0342aa1224717b`.
- Workspace: branch `dev2`, `MERGE_HEAD` is the upstream target, C4 guard active, no unmerged paths; original user worktree was clean.
- Files changed: upstream application/update/MPV increment plus `docs/C4-main-upstream-merge.md`, the assessment index, and restored tracked task documents; no lock or JNI source upgrade was intentionally added.
- Decisions: retain local `.gitignore`, tracked `docs/`, backup-before-update flow, GitHub proxy fallback, MPV output/lifecycle safeguards, and both ARM asset paths; add OCI/LAN update functionality and upstream subtitle selection behavior.
- Validation: `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.update.* --tests com.fongmi.android.tv.server.process.ApkUrl* --tests androidx.media3.mpvplayer.* --tests com.fongmi.android.tv.bean.BackupPreferenceFilterTest --no-daemon --console=plain` passed; `scripts/verify_mpv_native_assets.sh --require-elf` passed for `arm64-v8a` and `armeabi-v7a`; the OCI workflow regression assertions passed after the fail-closed and immutable-pin fix.
- Rollback anchor: `0452b2256b263ae7d7ec528cee7d5de5efabdb59` (or abort the uncommitted merge); do not drop the pre-existing stashes.
- Remaining risk: no connected-device test was run for real OCI download or LAN APK URL push, and no new native rebuild was performed; those scenarios remain follow-up validation for their respective runtime environments. Next action: run `task_guard.sh finish` with the recorded verification evidence.
