# C7：合并 `origin/beta` 最新 TMDB 手动匹配修复

## Recovery anchor

- 目标：将 `origin/beta` 在 C6 之后的最新应用代码合并到 `dev2`，保留当前应用行为和可回滚边界。
- 状态：已完成；代码合并、定向验证、原子提交和本地恢复 tag 均已完成，未推送远端。
- 当前基线：`dev2@1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`。
- beta 目标：`origin/beta@7db1b9d188e27877154757528d441150142b90ed`。
- 合并基点：`1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`；目标提交的第二父提交就是当前 `dev2`。
- 保护范围：guard 启动时工作树已复核为干净；本任务不触碰 `app/src/main/java/com/fongmi/android/tv/bean/Result.java` 及其他未声明路径。
- 回滚：合并未提交前使用 `git merge --abort`；提交后使用本任务恢复 tag 或 `git revert -m 1 <merge-commit>`。
- 下一动作：如需发布，再由用户明确授权推送当前 `dev2` 和恢复 tag；本任务不再修改代码。

## Authority and scope

- 用户已明确授权拉取 beta 最新代码并合并到当前 `dev2`。
- 任务 guard：`C7-beta-sync`，模式：`upstream`。
- 来源：`origin` (`https://github.com/Silent1566/webhtv.git`) 的 `beta` 分支；本次 fetch 使用 Git `HTTP/1.1` 成功完成。
- 本阶段只同步应用层 TMDB 匹配缓存、详情页和适配器逻辑；不升级或修改 FFmpeg、Media3、MPV、JNI、lock、patch、AAR、APK 或 native 二进制。
- 声明路径：
  - `app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java`
  - `app/src/main/java/com/fongmi/android/tv/setting/Setting.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/helper/EpisodeSeasonSnapshot.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
  - `app/src/test/java/com/fongmi/android/tv/bean/TmdbMatchCacheTest.java`
  - `docs/C7-beta-sync.md`
  - `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`

## User-visible capability

手动选择 TMDB 条目后，重新进入详情页、标题被 TMDB 富集改写、同一站源的季度结构发生排序变化，或后台自动匹配稍后完成时，仍能保持用户的手动选择。不同作品共用一个 `vodId` 时不会因退化的标题锚点串台；全局标题缓存中手动结论也不会被自动猜测覆盖。

## Source commit ledger

| # | Repository/ref | Full commit | Parent(s) | Functional area | Disposition |
| ---: | --- | --- | --- | --- | --- |
| 1 | `origin/beta` | `5a8122f8f6b3be38bcbb3f89f593ce08439040f5` | `a26a80d0c207c06d9e09192febee95ae976e8963` | 手动 TMDB 缓存、标题别名和稳定季度指纹 | 待合并验证 |
| 2 | `origin/beta` | `d5182a794ecdec79819dab782a3c3eb096aa3839` | `5a8122f8f6b3be38bcbb3f89f593ce08439040f5` + `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` | dev3 与 beta 的集成合并 | 依赖性合并，随目标树保留 |
| 3 | `origin/beta` | `b0af66ac567a85856509bb4cd042c3cbce7feee2` | `d5182a794ecdec79819dab782a3c3eb096aa3839` | 标题锚点通配符和读改写竞态修复 | 待合并验证 |
| 4 | `origin/beta` | `adbc9913857d7b1f406ae3ee00143d9e4c41aeda` | `b0af66ac567a85856509bb4cd042c3cbce7feee2` | 手动别名污染、全局标题优先级和空标题读取修复 | 待合并验证 |
| 5 | `origin/beta` | `9188c8a6a1919529438257bbc7c86bc2514f4fbe` | `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` + `adbc9913857d7b1f406ae3ee00143d9e4c41aeda` | beta 发布合并提交 | 依赖性合并，随目标树保留 |
| 6 | `origin/beta` | `7db1b9d188e27877154757528d441150142b90ed` | `9188c8a6a1919529438257bbc7c86bc2514f4fbe` + `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b` | beta 合并当前 dev2 的目标提交 | 本次合并目标 |

相对当前 `dev2` 的有效树差异为 6 个文件：`TmdbMatchCache.java`、`Setting.java`、`TmdbDetailActivity.java`、`EpisodeSeasonSnapshot.java`、`TmdbUIAdapter.java` 和 `TmdbMatchCacheTest.java`。没有检测到播放器/native 依赖差异。

## Decision record

- 不变更：继续使用 C6 树。优点是零合并风险；缺点是保留 beta 已修复的手动 TMDB 丢失、串台和竞态问题。
- 原样合并：采用 beta 目标树的应用修复和测试。目标提交已经把当前 `dev2` 作为第二父提交，合并基点明确，且预检 `git merge-tree --write-tree` 无冲突。
- WebHTV 适配：本轮不额外改写上游逻辑。现有 `dev2` 已包含 C6 的应用状态，beta 增量只触及上述 6 个文件；原样保留可最大化来源可追溯性并减少语义偏差。
- 决定：实施本次应用层同步；不推送远端，不扩展到设备播放或 native 重建。若定向测试发现回归，停止收口并在本文件记录具体失败，不降低验收标准。

## Acceptance and verification

1. 合并完成后无未合并路径、冲突标记或意外删除，最终工作树源码树与 `origin/beta` 一致。
2. `git diff --check` 和 task guard 检查通过。
3. `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.bean.TmdbMatchCacheTest --no-daemon --console=plain` 通过，失败、错误和跳过均为 0。
4. `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 通过。
5. 不修改 `Result.java`、lock、patch、AAR、APK 或 native 资产；不要求本阶段进行真实设备播放回归。

## Rollout, provenance, and rollback

- 来源完整身份：本记录台账中的 6 个 40 位 commit；目标树为 `7db1b9d188e27877154757528d441150142b90ed`。
- 发布方式：仅创建本地原子合并提交和 task guard annotated recovery tag；未经用户明确授权不 push。
- 回滚锚点：`1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`。共享分支回滚使用 `git revert -m 1 <本次合并提交>`，未提交合并使用 `git merge --abort`。

## Checkpoint 1：2026-09-02 17:48 Asia/Shanghai

- Completed：fetch `origin/beta` 成功，目标 head 冻结为 `7db1b9d188e27877154757528d441150142b90ed`；提交范围和有效树差异已核对；task guard 已启动。
- Workspace：分支 `dev2`，基线 HEAD `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`，guard 启动时无预存脏路径或暂存路径。
- Validation：`git merge-tree --write-tree` 生成无冲突树 `b81632fc4e83b4af1c8ab8fce778761e3cc5629d`；尚未执行实际合并和 Gradle 验证。
- Unresolved：合并结果、定向测试、双端 Java 编译、原子提交和恢复 tag 尚未完成。
- Next action：执行无提交 no-ff 合并并检查 staged 树。

## Checkpoint 2：2026-09-02 17:55 Asia/Shanghai

- Completed：`git merge --no-commit --no-ff origin/beta` 自动完成，无冲突；beta 的 6 个有效变更文件已进入合并树。
- Source identities：当前基线 `dev2@1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`；目标 `origin/beta@7db1b9d188e27877154757528d441150142b90ed`；`MERGE_HEAD` 与目标一致。
- Workspace：分支 `dev2`，合并进行中；工作区变更均属于 C7 声明路径，未发现 `Result.java` 或其他保护路径变化；无未合并路径。
- Validation：`git diff --cached --check`、工作区 `git diff --check` 和 guard check 通过；`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.bean.TmdbMatchCacheTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 返回 `exit=0`，Gradle 报告 `BUILD SUCCESSFUL in 2m 21s`。输出中的既有 `CXX5202` 仅为仓库已有的 32 位 native library warning，本阶段未修改 native 资产。
- Rollback anchor：未提交合并可用 `git merge --abort`；收口后使用本次恢复 tag 或 `git revert -m 1 <merge-commit>`。
- Unresolved：仅剩 guard 原子提交和恢复 tag，以及将其确切身份补入本记录。
- Next action：运行 `task_guard.sh finish`，创建 C7 合并提交和本地 annotated recovery tag。

## Checkpoint 3：2026-09-02 18:10 Asia/Shanghai

- Completed：C7 合并已由 task guard 原子收口。
- Implementation：本地合并提交为 `a8f2015363819c70b4e7ae67d419035e579b857f`；父提交为 `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b` 与 `7db1b9d188e27877154757528d441150142b90ed`；恢复 tag 为 `recovery/C7-beta-sync/20260902101049-a8f201536381`。
- Source/result：`origin/beta@7db1b9d188e27877154757528d441150142b90ed` 的有效代码路径已与 `HEAD` 一致；6 个 beta 增量文件已合入。
- Validation：定向 `TmdbMatchCacheTest` 与 Mobile/Leanback Arm64 Java 编译返回 `exit=0`，Gradle 报告 `BUILD SUCCESSFUL in 2m 21s`；`git diff --check`、冲突检查和 task guard check 通过。仅记录既有 `CXX5202` 32 位 native library warning，未改动 native 资产。
- Workspace：分支 `dev2`，相对 `origin/dev2` ahead 7；没有未提交改动，没有未合并路径；本轮未 push。
- Rollback anchor：回退到 `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b`，共享分支使用 `git revert -m 1 a8f2015363819c70b4e7ae67d419035e579b857f`。
- Status：C7 已完成；真实设备回归不属于本轮授权或验收范围。
- Next action：等待用户决定是否将当前 `dev2` 和新恢复 tag 推送到远端。
