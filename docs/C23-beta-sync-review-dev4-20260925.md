# C23 beta 同步与本地增量评审（dev4）

## Recovery anchor

- Objective: 将 `origin/beta` 最新代码合并到 `dev4`，评审本地相对 beta 的有效改动（尤其是 `9991cc095ade067e072b4761ce7ccab1397807bf`），确认远端已回退/移除的内容不会被重新提交，完成风险匹配验证、原子提交/标记、推送并创建到 beta 的 PR（不批准 PR）。
- Acceptance: `dev4` 包含 `origin/beta` 当前头提交；相对 beta 的有效播放器修复通过针对性测试与编译；合并改动无冲突且静态/源码测试通过；提交、本地恢复标记、推送、PR 全部完成；PR 描述使用中文并清晰说明改动与验证。

## Status

- 2026-09-25 13:14 CST：启动任务守卫 `C23-beta-sync-review-dev4-20260925`，基准 `9991cc095ade067e072b4761ce7ccab1397807bf`，无受保护既有脏文件。
- 2026-09-25 13:14 CST：`git fetch origin beta` 后远端 beta 头为 `e8ebb6b9a671d83940f1ced8c92294c9deeb4ea8`；本地与远端 merge-base 为 `2d21687ad51552ec5f002bafb061a447ae14a436`。
- 图谱确认：本地相对 `origin/dev4` 的 `2d21687ad` 是 beta 祖先，不是需要新增提交的本地内容；真正相对 beta 独有的有效改动是 `9991cc095ade067e072b4761ce7ccab1397807bf`。
- 2026-09-25 13:15 CST：`git merge origin/beta --no-edit` 无冲突，生成合并提交 `ced589c09a8a660c647e24c0389532c7ca380dc3`。远端新增内容集中在移动端关注卡片布局/资源与 `dev2` 的 C22 评审文档；与本地播放器 Surface 重建修复无文件重叠。
- 第一轮与验证后第二轮评审均完成；详见 `Review` 与 `Verification`。

## Review

### 合并内容

- 远端新增有效内容为 `3200f6bb01866bc77b1d2875d3e1e6eac04e19e9`（关注卡片下排操作布局）与 `b5cac8a33941669deb3401726279f2725a579af0`（dev2 C22 评审文档）。布局改为纵向卡片结构，使用 FlexboxLayout 按普通/平板资源分别以 30%/18% 的基准宽度换行排列七个操作；错误提示保持在操作区之前，避免操作被裁剪。`FollowingUiSourceTest` 覆盖了无横向滚动、非居中布局、动作 ID 与尺寸资源。
- 该合并范围只触及移动端关注卡片资源、布局与测试及文档，与本地播放器修复没有文件或调用链重叠。

### 本地有效改动

- `9991cc095ade067e072b4761ce7ccab1397807bf` 修复自动线路回退后“仅声音、无画面”的问题：错误路径先停止旧引擎，再通过 `applyHistoryPlayerKernel(true)` 强制重建同内核；同内核也重建，避免继承旧 renderer/Surface 状态。
- `preparePlayer(type, force)` 将 `callback.onPlayerRebuild(...)` 放在 `spec = null` 之前。回调链经 `PlaybackService` 到 `PlaybackActivity`；`PlaybackActivity.onPlayerRebuild` 依赖 `isOwner()`，而 `isOwner()` 读取 `manager.getKey()`，因此延迟清空 `spec` 是必要的，否则 UI 不会重绑 `PlayerView` 与进度条。
- 移除错误路径的第二次普通准备调用后，强制重建只执行一次；`force` 仍限制在错误恢复路径，普通起播保持同内核不重建的原有语义。测试补充了这两个回归约束。
- 复审结论：未发现需要修改的问题；没有把远端已移除/回退的提交重新带入 beta。相对 `origin/beta` 的非合并新增提交仅有 `9991cc095ade067e072b4761ce7ccab1397807bf`。

## Verification

- 2026-09-25 13:24 CST：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.FollowingUiSourceTest --tests com.fongmi.android.tv.ui.activity.PlayerPlaybackRegressionSourceTest --tests com.fongmi.android.tv.ui.activity.PlaybackOwnershipSourceTest --tests com.fongmi.android.tv.player.PlayerManagerTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 通过（BUILD SUCCESSFUL，1m17s）。
- `git diff --check` 与 `git diff --check origin/beta..HEAD` 通过。
- Gradle 单次守护进程构建后已执行 `./gradlew --stop`，守护进程已清理。
- `git merge-base --is-ancestor origin/beta HEAD` 通过；有效 PR 差异为播放器修复 5 个文件（29 行新增/10 行删除）及本任务文档。
- 任务守卫说明：`start` 时基准为合并前 `9991cc095ade067e072b4761ce7ccab1397807bf`；本任务目标明确要求先合并 `origin/beta`，因此守卫不支持合并场景。为不重写已验证的合并提交，守卫基准显式更新为授权合并提交 `ced589c09a8a660c647e24c0389532c7ca380dc3`，并继续由同一守卫会话完成文档提交与恢复标记。

## Rollback

- 当前合并提交可通过 `git revert -m 1 ced589c09a8a660c647e24c0389532c7ca380dc3` 独立回退；本地播放器修复已有 `recovery/auto-line-surface-rebind-fix/20260925124906-9991cc095ade` 标记。
