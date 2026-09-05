# C10：解决 dev2 向 beta 合并冲突

## Recovery anchor

- 目标：将最新 `origin/beta` 合入当前 `dev2`，保留 C8 修复与 beta 后续应用修复，解决内容冲突，验证合并树并推送，使 `dev2` 可继续向 `beta` 合并。
- 状态：语义合并和验证已完成，待守卫原子提交、恢复标签、推送与同步确认。
- 当前第一父基线：`dev2@602203b7f3266473a23cc88f2edbaf06e1a08481`。
- beta 目标：`origin/beta@bb53d224e084348518bd13c6733d3c359db4ed51`。
- 合并基线：`308694aaadd59d9d1ef230bded83cf84dafa114c`。
- 任务守卫：`C10-beta-conflict-resolution`，模式 `upstream`，范围 `app/**`、`docs/**`；任务开始前无预先脏路径。
- 排除项：不修改 FFmpeg、Media3、MPV、JNI、lock、AAR、APK、`.so` 或其他 native/二进制资产；不重写已发布标签。
- 回滚：合并未提交前使用 `git merge --abort`；提交后使用本任务恢复标签或 `git revert -m 1 <merge-commit>`。
- 下一动作：执行 task guard finish，随后只推送当前 `dev2` 和新恢复标签，并执行 `git pull --ff-only`。

## Authority and decision

- 用户明确要求解决当前无法自动合并到 `beta` 的冲突；本任务授权在当前 `dev2` 上合入最新 `origin/beta`，修复冲突、测试、提交、创建恢复标签并推送当前分支。
- 决策问题：如何在不丢失 C8 的 IntroSkip 状态/生命周期边界的前提下，吸收 beta C9 对片段身份、解析边界和受影响测试的后续修复。
- 反事实：直接保留任一侧会丢失另一侧已验证的行为；无合并则远端 PR 继续冲突。
- 推荐方案：使用 Git 三方合并定位冲突，保留两侧互不重叠改动；对同一方法以当前最终行为和测试契约为准，优先保留更严格的边界/生命周期保护，再纳入 beta 的新增测试与功能，不采用整文件覆盖。

## Conflict evidence

- 预览合并显示 4 个内容冲突：
  - `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java`
  - `app/src/main/java/com/fongmi/android/tv/service/IntroSkipService.java`
  - `app/src/test/java/com/fongmi/android/tv/service/IntroSkipServiceTest.java`
  - `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`
- 其余 beta 差异由 Git 可自动合并，仍须在最终 diff 中检查是否意外覆盖本地修复。
- beta 侧 C9 已有独立记录 `docs/C9-beta-sync.md`；本任务只记录 dev2 为解决合并冲突所需的整合与验证，不重写 C9 的历史。

## Alternatives

- 不合并：保留当前稳定 `dev2`，但无法消除向 beta 的合并阻塞，拒绝。
- 全部采用当前侧：可能丢失 beta 的后续播放器/布局/测试修复，拒绝。
- 全部采用 beta 侧：可能回退 C8 的严格 IntroSkip 生命周期、缓存、溢出和回调保护，拒绝。
- 三方语义合并：保留双方有效契约并用受影响测试验证，采用。

## Acceptance criteria

1. 合并完成后无未解决路径、冲突标记或意外删除，且当前 `dev2` 的有效改动与 beta 的有效增量同时存在。
2. `IntroSkipPlayback`、`IntroSkipService` 的状态机、片段身份、时长/溢出和缓存边界保持可解释且有测试覆盖。
3. beta 新增及现有受影响的 IntroSkip、布局、Exo 测试通过，失败/错误/跳过为 0。
4. Mobile 与 Leanback Arm64 Java 编译通过。
5. 最终 diff 检查未发现 Critical/Important 冲突引入；不改 native/依赖/锁/二进制所有权。
6. 由 task guard 创建一个原子合并提交和 annotated recovery tag，推送 `dev2` 后 `git pull --ff-only` 保持同步。

## Verification plan

- 最小冲突验证：冲突清零、`git diff --check`、冲突标记扫描。
- 受影响单测：`IntroSkipServiceTest`、`VideoActivityLayoutTest`、`ExoUtilTest`，以及 C8/C9 涉及的源代码回归测试（按实际测试任务可用性选择）。
- 编译验证：`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。
- 发布闭环：task guard finish、恢复标签、推送当前 `dev2`、`git pull --ff-only`。

## Provenance ledger

| # | Repository/ref | Full commit | Disposition |
| ---: | --- | --- | --- |
| 1 | `origin/beta` | `bb53d224e084348518bd13c6733d3c359db4ed51` | 合并目标；最终树复审 |
| 2 | 当前 `dev2` | `602203b7f3266473a23cc88f2edbaf06e1a08481` | 第一父基线；保留 C8 最终树 |
| 3 | `origin/beta` ancestor | `308694aaadd59d9d1ef230bded83cf84dafa114c` | 三方合并基线；历史已在双方树中保留 |

## Checkpoint 1：2026-09-04 00:24 Asia/Shanghai - baseline frozen

- Completed：刷新 `origin/beta`，确认目标与当前 `dev2` 的完整身份；工作区干净，预览三方合并确认 4 个内容冲突。
- Source identities：第一父 `602203b7f3266473a23cc88f2edbaf06e1a08481`，beta `bb53d224e084348518bd13c6733d3c359db4ed51`，merge base `308694aaadd59d9d1ef230bded83cf84dafa114c`。
- Review boundary：只处理 `app/**` 与 `docs/**`；冲突核心为 IntroSkip 代码/测试和总评估台账。
- Rollback anchor：保持第一父不变；未提交合并可用 `git merge --abort`。
- Unresolved：实际合并、语义冲突解决、测试/编译、提交/tag/push/pull。
- Next action：执行 `git merge --no-commit --no-ff origin/beta`，然后按冲突块逐项合并。

## Checkpoint 2：2026-09-04 09:35 Asia/Shanghai - conflicts semantically resolved

- Completed：执行 `git merge --no-commit --no-ff origin/beta`；`IntroSkipPlayback`、`IntroSkipService`、`IntroSkipServiceTest` 和总评估索引已清除冲突标记。
- Resolution：`IntroSkipService` 保留 C8 的显式片段身份、缓存完整性和长整型安全比较，吸收 C9 的负边界拒绝与尾部段越参考时长拒绝；`IntroSkipPlayback` 采用规范化稳定身份，区分同源多段且不随本地时长折算漂移；测试保留 C8 与 C9 的互补契约。
- Validation：首次 `:app:testMobileArm64_v8aDebugUnitTest` 因合并保留的重复 `identity` 赋值失败；删除重复赋值后同一定向测试任务通过。`IntroSkipServiceTest`、`VideoActivityLayoutTest` 和 `ExoUtilTest` 均无失败、错误或跳过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。`git diff --check` 和冲突标记扫描通过。
- Unresolved：原子提交、恢复 tag、push 和 pull。
- Next action：运行 task guard finish，随后推送 `dev2` 与新恢复标签并执行 `git pull --ff-only`。
