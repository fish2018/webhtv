# C20 - 前台追更高时效刷新

## Recovery anchor

- 目标：用户在 App 前台使用时缩短追更元数据检查间隔，提高更新发现时效；后台不引入常驻强后台，也不提高 WorkManager 6 小时兜底任务频率。
- 验收：前台常规追更项在成功检查后下一次检查不超过 15 分钟；后台成功检查仍为 6 小时；已完结/取消和 Planned 的低频语义不被全局缩短；自动请求保持 30 分钟 TMDB 缓存，手动检查仍强制刷新；聚焦单测覆盖策略，代码评审覆盖调度入口。
- 当前状态：实现已通过定向验证和第二轮代码复评，待原子提交、推送和 PR。
- 下一步：完成 task guard 原子提交、推送 `dev4` 并创建到 `beta` 的中文 PR。

## 决策与设计记录（2026-09-24）

### 当前证据

- `FollowingScheduler.ensurePeriodic()` 使用 6 小时周期、1 小时 flex，这是兜底而非实时调度。
- `FollowingUpdateCoordinator.checkDue()` 每轮最多 5 个到期项，过多追更会排队。
- `FollowingSchedulePolicy.nextCheckAt()` 成功检查后默认 6 小时；`nextAirAt` 仅来自 TMDB `next_episode_to_air.air_date`，并被启发式解释为本地 20:00，因此不能可靠做分钟级准时判断。
- `TmdbService.detailForFollowing()` 自动检查使用 30 分钟缓存；手动传入 refresh，绕过缓存。
- `App` 已实现 `ActivityLifecycleCallbacks`，当前仅维护 `activity`，可最小改动记录前台状态，不需要新增依赖或前台服务。
- Android/WorkManager 证据：周期 Work 最低周期为 15 分钟，且系统会因 Doze/网络限制延后；用户要求的是“前台使用时缩短”，不是强后台实时保活。

### 备选方案

1. **不变**：零风险，但前台最多仍要等约 6 小时，不满足目标。
2. **全局周期 6 小时改为 15 分钟**：会提高后台请求和耗电，且仍受 `next_check_at` 和系统调度限制；不符合“只在前台提高”的要求。
3. **前台状态 + 前台检查间隔 15 分钟（推荐）**：用户正在使用时每项最多 15 分钟刷新一次；进入后台后仍按原 6 小时低频策略，不新增前台服务或精确闹钟。
4. **播出时间 10/30/60 分钟重试**：受 TMDB 日期粒度与本地 20:00 假设限制，容易在错误时刻高频请求；本任务不做。

### 决策

采用方案 3，保持全局 WorkManager 6 小时任务不变。前台判定使用 `ActivityLifecycleCallbacks` 的 started/stopped 计数，不依赖网络探测或系统 API。为了不破坏低频语义，`ENDED/CANCELED` 仍保持 7 天；`PLANNED` 仍保持 1 天；`RETURNING/UNKNOWN/无状态` 在前台使用 15 分钟，后台保持 6 小时；前台 `RETURNING` 的 `nextAirAt` 约束也被 15 分钟时效上限覆盖。到期批处理从前台最多 20、后台最多 5，控制请求数量。复用现有共享 due-now 任务和逐项 one-shot 任务；逐项任务已有唯一名称，前台批量入口不改全局周期。

### 兼容与回滚

- 不改数据库 schema、通知、缓存、上游 API、播放行为或后台任务周期。
- 若前台判断异常，最坏表现为前台回到 6 小时策略或保持 15 分钟到进程结束；停止追更功能仍会 cancelAll。
- 回滚边界为 C20 涉及的前台状态、策略、调度、协调器、测试和本文档；不影响其他播放器/上游任务。

## 实施记录

- 2026-09-24：`App` 使用 `ActivityLifecycleCallbacks` 的 started/stopped 计数提供前台状态。
- 2026-09-24：`FollowingSchedulePolicy` 新增前台 15 分钟常规间隔；保持 `PLANNED` 24 小时、`ENDED/CANCELED` 7 天、后台 6 小时。
- 2026-09-24：`FollowingUpdateCoordinator` 前台批量提升到 20，后台保持 5；自动检查保持 30 分钟 TMDB 缓存，手动行为仍强制刷新。
- 2026-09-24：未修改 WorkManager 6 小时全局周期、数据库 schema、通知逻辑或来源站探测策略。

## 验证记录

- 通过：`git diff --check`。
- 通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.following.FollowingSchedulePolicyTest' :app:compileLeanbackArm64_v8aDebugJavaWithJavac --stacktrace`，6 tests completed，0 failures/errors，`BUILD SUCCESSFUL`。
- 通过：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --stacktrace`，`BUILD SUCCESSFUL`。
- 边界：未打包 APK、未连接设备验证前台生命周期实机场景；本改动不涉及 native、ABI、依赖和数据库 schema。

## 提交与标签

- 待 task guard finish 记录。
