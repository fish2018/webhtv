# C4: fish2018/main 上游应用合并

## Recovery anchor

- 目标：在已完成的首轮 C4 基础上，将 `fish2018/webhtv:main@784b90420d646eb6c7ddcc63ad622a92c65b02b4` 相对 `ec478b0b697422a7785171c7b51a35b7a526564e` 的 51 个最新提交合并到当前 `dev2`，保留本地播放器修复、评估记录和用户备份文件。
- 状态：第二轮增量已完成；merge commit `188553addf6692220a2a715790fb8706b2f423b0` 与 recovery tag `recovery/C4/20260907105426-188553addf66` 已创建；本地基线 `912208261e4e342ced009b1a0b71feed4855a01d`，上游目标 `784b90420d646eb6c7ddcc63ad622a92c65b02b4`，共同祖先 `ec478b0b697422a7785171c7b51a35b7a526564e`。
- 回滚锚点：首轮 C4 合并 `d0809f804f812b818bcb22f36cae8634022db673`；本轮实施前 `dev2@912208261e4e342ced009b1a0b71feed4855a01d`。
- 任务 guard：复用稳定任务 ID `C4`，范围为 194 个上游净变更路径以及本文和评估索引；5 个会话初始 `.bak20260906*` 文件由 guard 保护且不得提交。
- 接受条件：形成以本地基线和完整上游目标为双父的 merge commit；无未解决冲突/冲突标记；本地任务文档和 5 个备份文件不丢失；上游目标成为 HEAD 祖先；双 ABI native 资产门禁、受影响 JVM 测试以及 Mobile/Leanback Java 编译通过；原子提交和 annotated recovery tag 已创建。
- 下一动作：代码任务已闭合；保持当前分支和 recovery tag，不执行远端 push。

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


## 第二轮增量：2026-09-07 最新 `main` 合并

### 冻结基线与范围

| Role | Repository/ref | Full commit |
| --- | --- | --- |
| Local baseline | `dev2` | `912208261e4e342ced009b1a0b71feed4855a01d` |
| Common ancestor / previous C4 target | `fish2018/webhtv` | `ec478b0b697422a7785171c7b51a35b7a526564e` |
| Upstream target | `fish2018/webhtv:main` | `784b90420d646eb6c7ddcc63ad622a92c65b02b4` |
| Upstream target parent | `fish2018/webhtv` | `686522fa8f4a9405f906848c2b938d9da300d6cd` |

- GitHub API 与完整 Git 父链均确认目标提交日期为 2026-09-06 16:45:23 +08:00；本地最初的 `--depth=1` 抓取已通过 `--deepen=100` 补齐，`git merge-base` 明确返回旧 C4 目标。
- 精确增量为 51 个提交、194 个净变更路径、8213 行新增和 1062 行删除；顶层范围仅为 `app/`、`gradle/`、`scripts/`、`third_party/` 和两个旧任务文档删除。
- 本轮包含 Exo/MPV 音频策略、双 ABI MPV 资产、Media3/Nextlib AAR 与 sidecar、APE/AV3A/ALAC、P8.1 HDR10 fallback、MPV 脚本按钮与配置同步、TV 搜索焦点修复。每个材料变更均已有同链任务文档、源码、测试或提交验证记录；本轮问题是把已审核的上游最终树安全组合进当前本地分支，而不是重新设计这些能力。
- 上游最后提交删除了上游工作区的本地任务文档；该提交不构成删除当前仓库 `docs/`、评估索引或用户备份的授权。两个相对旧 C4 目标的删除项 `docs/OCI1-oci-apk-update.md` 与 `docs/mobile-apk-link-push.md` 继续保留。
- 受保护的会话初始备份已从其创建时 Git 对象精确恢复，旧 guard 指纹逐一一致；SHA-256 为：四个 `MpvPlayer.java.bak2026090616*` 均为 `e311a8627594520fa8373af1ec931c814697ffaae7421ca010ea1ce0d4675e6c`，`VideoActivity.java.bak202609061540` 为 `7c0c3724329f6339e5ea8da237ad1ab443484d2f9cc07b29144ac565c1c024ae`。

### 现有实现、证据与方案判断

- 当前本地后续修复包括 MPV duration 恢复 `6b0490907da3e0b09a6563c1572cf283a3ae49d3`、移动端历史重建后 seek 进度重绑 `cb386895da22a8836bb5587cafff3ef10f89a4fa`，以及 beta 合并 `912208261e4e342ced009b1a0b71feed4855a01d`；这些均晚于共同祖先，冲突时必须保留。
- **精确上游源码/提交/测试（A）：** 51 个实际 commit/tree diff、父链、提交自带 Verification、`upstream/main^` 中对应 E/P/C 唯一任务文档；直接决定实现语义与回归门槛。
- **官方规范/项目文档（A）：** 上游任务文档已绑定 Android AudioTrack/AudioManager、Media3 与 MPV 锁定版本文档；本轮不改变其已批准设计，只验证三方集成不破坏合同。
- **PR/issue/revert/维护者讨论（A/B）：** 由同链任务文档按功能记录；本轮范围中没有新的未解释 revert，最后提交仅为远端文档清理。
- **成熟相关项目代码与测试（A/B）：** Media3、FFmpeg、MPV 和 nextlib 锁定源码、补丁与 JVM/native verifier 已随提交落盘，适用于供应链与行为核对。
- **论文/技术文章/基准/现场报告：** 对本轮“同仓库已审核提交的三方合并”没有新增决策价值；性能和设备证据沿用各原子任务记录，本轮不把编译结果扩大为新的设备性能结论。

备选方案：

1. **不变更：** 无法满足“合并上游最新代码”，排除。
2. **以 `upstream/main` 最终树整体覆盖本地：** 会丢失 1956 个本地侧提交结果、当前任务文档和后续播放器修复，排除。
3. **逐提交 cherry-pick 51 次：** 破坏原始上游连续历史、重复冲突且回滚边界过碎，排除。
4. **真实两父三方 merge，并在冲突处组合本地后续修复与上游能力：** 推荐且已获用户本轮明确合并授权；保留可追溯性、最小化重演、可用单一 merge commit 回滚。

验收与回滚：

- `git diff --check`、无 unmerged path/冲突标记；`git merge-base --is-ancestor 784b90420d646eb6c7ddcc63ad622a92c65b02b4 HEAD` 成功。
- `scripts/verify_mpv_native_assets.sh --require-elf` 验证双 ABI ELF/资产；一次 Gradle 调用运行受影响单测和 Mobile/Leanback Arm64 Java 编译。
- guard 证明 5 个 `.bak` 指纹不变、只提交声明路径；`task_guard finish` 创建双父提交及 annotated recovery tag。
- 合并前回滚为 `git merge --abort`；提交后回滚为 revert 本轮 merge commit 或恢复 `912208261e4e342ced009b1a0b71feed4855a01d`，不改写已发布历史。

### 51 个上游提交完整台账

| # | Full commit | Summary | Planned disposition |
| ---: | --- | --- | --- |
| 1 | `b208d26546cf6fd4498a1e54d45a37106a313d69` | feat(mpv): add multichannel audio fallback policy | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 2 | `37995ff14016fd5a26fdae2b482f08470aa6a162` | Show runtime audio playback diagnostics | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 3 | `cf0a5dabc77fb2bbdc3e0f2cc867a9eac86060b8` | feat(exo): prioritize hardware audio decoders | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 4 | `d41155f16cd81f1354672a5479743462fc168ed9` | mpv: prefer hardware audio MediaCodec with fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 5 | `3fdf9f82f37843699a2545ed97d4a2dd17b8ead5` | exo: recover compressed audio output failures with PCM fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 6 | `d8a994af5a64b93d7aeafb81f3755f81fcb8194c` | player: align audio decode labels with video diagnostics | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 7 | `734e99253ef5e856b3639810da5d9a05b0493646` | mpv: add compressed AudioTrack output path | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 8 | `cb0a59f819a219dccd32bc1bf1c22b9caf754f07` | mpv: report actual hardware audio decoder state | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 9 | `37888d8b9d99da29f9ecfc3cd1f5eba458e09ee0` | exo: gate network protection on actual audio output | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 10 | `d00aa5737980d976cbae491948bf65dab906bf68` | mpv: repair compressed AudioTrack fallback patch hunks | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 11 | `347801b81f56bdcf515e4a9d7013814f8b777519` | docs: record Exo audio codec compatibility investigation | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 12 | `5c104a199fa07ad5f33c575deb6e0b91eea6668a` | exo: route MP4 AV3A tracks to compatible decoder | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 13 | `53eab9c2d6101220f26525a2af309a5578e7dc3a` | mpv: expose AV3A audio tracks with canonical MIME | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 14 | `12ebbf8d15280e248dff9818c7969c7261173c16` | fix Exo audio output configuration recovery | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 15 | `c7d43205f9125077a471ce57ee46a9c1f206e982` | fix Exo compressed audio PCM channel fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 16 | `8419003c2fe9eac200d2e6a9ef0dddf58638be31` | Fix MPV compressed audio output fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 17 | `69191f78a37c5d56f87591c857d2d4be0112815d` | fix(mpv): recover AAC playback through PCM fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 18 | `2f2f99fb7d91c5e8c71e8f408b1ee45b287b2917` | fix(mpv): downmix unknown AV3A channels to stereo | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 19 | `639a046125c4375685cb96c9ea004b620778bbb9` | fix(exo): parse AVS3A DASH channel configuration | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 20 | `c826ee327b1cc33c0f0522fa120b1b2039e789d8` | fix(exo): support AV3A 5.1 mixed-content channel downmix | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 21 | `56b802ef585e83099953979b802172512c2fb447` | docs: add audio and multichannel strategy assessment | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 22 | `04904ff99ae67efb25ebd4cca3740b30f0178662` | fix(exo): route ALAC away from stalled vendor decoder | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 23 | `80e313830824f2e86d341079b58c330726de3e99` | fix(exo): extract ALAC cookie from QuickTime wave atoms | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 24 | `ebb5285238aa19eeab11ec4595985d496550ced2` | docs: define common audio policy contract | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 25 | `43fba18a8d074268c26a6ddbd30fe3483247320a` | docs: close C4 audio policy assessment record | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 26 | `0a31951e3c923154b2ef8218d1a3811a96fa446b` | common: unify audio diagnostics contract | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 27 | `047ad74f76c859862b39b64d0545ec2dc83dd865` | docs: close C4 implementation record | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 28 | `e5dd86b344b8ab1c5bda68f96ecf77a2e356ad4d` | fix(exo): support APE demux and FFmpeg playback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 29 | `aed962426d7da7ddc268091f443b72c12187bd66` | Fix MPV P8.1 HDR10 hardware fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 30 | `bf22a22fd60fd2cb9e2fef93b13f379b8d8b4f9e` | Fix audio diagnostics Android API compatibility | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 31 | `8dd15ff24a5406091819c86b2dfa5e8499a6cbef` | MPV: disable automatic software video fallback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 32 | `ea9587dcb7feb187cc91f5f958f35696fea2996a` | fix(mpv): gate compressed audio by passthrough route | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 33 | `5a5a6ef383fb601e5bbce9d932a3bcc7cfa6ccc7` | docs: record MPV script button design | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 34 | `72200c16fed08bd0309ea6290fab238bd21e7fa6` | feat: add MPV script custom buttons | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 35 | `5b9a641678cfeae4db305fc490f6d5e17190921f` | fix(mpv): use scripts new button for custom controls | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 36 | `c0968836fbf3f9045449782355eb311c6dd89911` | feat(mpv): add script custom button management UI | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 37 | `8baec44153035bb71723b4b2e0012415eb3eb337` | fix(mpv): keep script creation in one dialog | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 38 | `bafdf34b765fca776ecc94a92cabb26234213f2e` | fix(mpv): reposition custom script buttons | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 39 | `7ad7e1406efc2cda01bd6b14bd94822b7c31dedd` | fix(mpv): align right script buttons | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 40 | `61b352f8086554364d0ce402eb20c27105e01c6e` | sync: add optional MPV configuration transfer | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 41 | `123db7e7553eb0066e3c815f437b8cf266fe1aa7` | sync: clear stale MPV profile preferences on restore | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 42 | `3b346c85d0a3ed519d1bdac7b2e431a238a313ed` | docs: close MPV configuration sync record | evidence/included：作为对应实现的设计、验证或收口证据纳入历史 |
| 43 | `41f02fd3c1f9e40fc64dd810475f7a45109bd4cb` | fix: open script text editor and refresh list | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 44 | `37a3685621b9b3882625fe721e3bf5a4e54ac372` | fix: unify mpv scripts settings flow | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 45 | `b70f96ec0f732cc88edcd5edc26e39ecffe2cba3` | fix: show custom script button click feedback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 46 | `219ea082bd17719db49389b57f83fe2199bb79bf` | fix: toggle custom script button feedback | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 47 | `77df324e28c5befefe2f050e04c01ea62d556e3a` | fix: omit generated script suffix from button title | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 48 | `e1a873b37aa2243e014a9ab1be78c3f9696161be` | fix(tv): reset search result focus on CSP switch | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 49 | `54b0b6a875d9d26fcf26c96ae04d4a2302e49625` | fix(tv): intercept search result focus entry | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 50 | `686522fa8f4a9405f906848c2b938d9da300d6cd` | fix(mpv): prevent automatic script execution and remove button cap | candidate/included：三方合并；冲突处保留本地后续修复并接入上游能力 |
| 51 | `784b90420d646eb6c7ddcc63ad622a92c65b02b4` | chore: remove local docs from remote | adapted：保留提交历史与代码结果，但不删除本地任务/评估文档 |

### 第二轮实施与验证结果

- 2026-09-07：已完成 14 个 Git 冲突文件的三方组合；保留本地播放器生命周期、性能与历史恢复修复，同时接入上游 Exo/MPV、音频策略、双 ABI 资产、脚本按钮/配置同步及 TV 焦点修复。额外修复合并树中的 Leanback `placePanDiagnosticAction()` 缺失、`Backup` 的 `BaseLoader` 导入和 `ExoUtil` 工厂参数对齐问题。
- `bash .codex/scripts/task_guard.sh check`：通过；无 unmerged path，5 个初始 `.bak` 文件仍受保护。
- `bash scripts/verify_mpv_native_assets.sh --require-elf`：通过；`arm64-v8a` 与 `armeabi-v7a` 的 ELF、锁定版本和打包规则均通过。
- `bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoUtilTest --tests com.fongmi.android.tv.player.exo.ExoCompressedAudioDirectPolicyTest`：`BUILD SUCCESSFUL`；Mobile/Leanback Java 编译和两个受影响 Exo 单测通过。
- `git diff --check`：通过。Gradle 仅报告仓库既有的 32-bit native library 警告；本轮未重建 native、未做连接设备播放回归，故不把本地构建结果扩大为实机行为结论。
- 当前状态：代码、验证、merge commit 与 recovery tag 均已收口；不执行远端 push。

## Closure：2026-09-07 Asia/Shanghai

- Merge commit：`188553addf6692220a2a715790fb8706b2f423b0`（`merge: synchronize latest fish2018 main updates`），第一父提交为 `912208261e4e342ced009b1a0b71feed4855a01d`，第二父提交为 `784b90420d646eb6c7ddcc63ad622a92c65b02b4`。
- Recovery tag：`recovery/C4/20260907105426-188553addf66`；`git merge-base --is-ancestor` 已确认本地基线和上游目标均为 HEAD 祖先，merge metadata 已清理。
- 最终工作树仅保留任务开始前的 5 个受保护 `.bak` 未跟踪文件；其 SHA-256 与 guard 初始指纹一致。`docs/OCI1-oci-apk-update.md` 与 `docs/mobile-apk-link-push.md` 均保留。
- 最终状态：完成（本地未推送）。连接设备播放、真实 OCI 下载/局域网 APK 推送和 native 重建不属于本轮验证范围，后续如需验收应另开任务。

## 第三轮源码合并：2026-09-13 Asia/Shanghai

- 目标：`fish2018/webhtv:main@fc62397591701b2232ae7de4f50a032bd7742064`；本地基线 `154e003520a751a19187057f103e1496c5197457`；三方合并基 `2b36396c0d76b312154d560c0c94e55909b951a2`。
- 冲突：26 处，按“本地行为契约 + 上游新功能并集”解决；二进制/编译产物按用户要求直接覆盖更新，本地任务/评估文档保留。
- 关键并集：播放器蓝光菜单、原盘导航、历史进度、MPV 渲染设置、短剧 dock/生命周期、Disc 菜单清理与 native patch 链；`.gitignore` 保留本地缓存目录。
- 尚未完成：定向验证、guard finish 提交与 recovery tag。
- 2026-09-13 05:44–06:13：修复 mobile/leanback 合并残留的结构、绑定、设置行、动作注册与初始续播冲突；恢复 mobile disc-menu 生命周期触发与 leanback `updateDiscMenuButton()`。mobile/leanback Java 编译和 8 个定向单测通过，`git diff --check` 通过。
