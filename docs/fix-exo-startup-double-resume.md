# EXO 起播闪屏与重复续播修复

## Recovery anchor

- **目标**：确认“EXO 播放前起播两次、第一次闪屏，第二次才从历史位置续播”是否存在；若存在，消除首次起播后的重复历史定位。
- **验收**：手机端首次 EXO 起播把历史续播位置直接交给 `PlayerManager`；`onPrepare()` 的 `setPosition()` 不再对同一位置再次 `seekTo()`；无历史位置、IJK、TMDB 恢复等既有分支继续可用；源码回归测试与手机/TV Java 编译通过。
- **当前状态**：已实现；源码回归与语法检查通过，Android flavor 编译和真实设备视觉验证受环境限制未完成。
- **下一动作**：提交本任务三处文件并创建本地恢复标签。

## 结论与证据

这条反馈**存在**，但严格说不是正常路径中 EXO `start()` 被调用两次：

1. `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 的 `setPlayer(Result)` 使用不带起播位点的 `startPlayer(...)`，因此 `PlayerManager`/`ExoPlayerEngine` 首次以默认位点（通常为 0）执行 `setMediaItem(..., C.TIME_UNSET)`。
2. 同一个播放准备回调随后进入 `onPrepare() -> setPosition()`；手机端旧逻辑无条件调用 `player().seekTo(historyPosition)`。若第一帧先于该 seek 显示，就会看到首帧闪过后跳到续播位置，用户感知为“起播两次”。
3. `app/src/leanback/java/.../VideoActivity.java` 已有对应契约：`mInitialPlaybackPosition` 传入带位置的 `startPlayer(...)`，`setPosition()` 发现位置相同就清空一次性标记并跳过重复 seek。`PlayerManager` 和 `ExoPlayerEngine` 已支持该位置参数，因此缺口在手机 flavor 的接线，不在 EXO 引擎本身。

## 最小修复

- 手机端增加一次性 `mInitialPlaybackPosition`。
- 首次 `setPlayer(Result)` 计算 `resolveInitialPlaybackPosition()`，通过已有的 position-aware `startPlayer(...)` 传入 EXO/IJK 共用的起播链路。
- 手机端补齐与 TV 相同的 `resolveInitialPlaybackPosition()` 和重复定位保护；无有效历史位置时清理标记，不改变原有恢复逻辑。
- 增加源码回归断言，锁住“首次带位点起播 + `onPrepare` 跳过同位置 seek”的顺序。

## 风险、回滚与验证边界

- 不修改 Media3/EXO 引擎、AAR、lock、native、MPV 或公共 API；位置参数原本已在公共播放器链路中使用。
- 只改变手机端首次播放的历史位置注入，质量切换及显式 TMDB 恢复路径仍沿用原有语义。
- 若出现回归，可整体回退本任务提交；变更文件仅限手机 `VideoActivity`、对应源码回归测试和本任务文档。
- 当前环境无可用 ADB 设备，无法完成真实设备的视觉 A/B；定向源码测试与两种 flavor Java 编译用于确认生命周期接线和编译契约，设备端仍需后续用带历史进度的 EXO 片源观察只出现一次有效首帧。

## 实施记录

### 2026-09-06 12:00 CST：手机端生命周期补齐

- 在 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 增加一次性 `mInitialPlaybackPosition`，在首次 `setPlayer(Result)` 中复用 `resolveInitialPlaybackPosition()`，并通过已有的 position-aware `startPlayer(...)` 传入 `PlayerManager`。
- 将历史位点解析从 `setPosition()` 抽成 `resolveInitialPlaybackPosition()`；`onPrepare() -> setPosition()` 在位点已由首次起播消费时清空一次性标记并跳过重复 `seekTo()`，其余 IJK/显式恢复/无历史分支保持原有路径。
- 在 `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/PlayerPlaybackRegressionSourceTest.java` 增加源码回归断言，锁定首次带位点起播与重复定位保护的顺序。

### 验证结果

- **通过**：定向源码回归测试 `PlayerPlaybackRegressionSourceTest`，上一轮执行结果为 `PASS 9 tests`。
- **通过**：两处变更 Java 源文件的括号/引号平衡检查；`git diff --check` 无输出。
- **未执行（环境阻断）**：`compileMobileArm64_v8aDebugJavaWithJavac`。Gradle 配置阶段明确失败：未找到 Android SDK，且工作区没有 `local.properties`，`ANDROID_HOME`/`ANDROID_SDK_ROOT` 均未设置。该失败属于验证环境问题，不是代码编译错误证据。
- **未执行（设备不可用）**：带历史进度的 EXO 真实设备视觉 A/B；需要后续在可用 ADB 设备上确认首帧不再闪回。

### 回滚与提交

- 回滚锚点：`cebe42b190d5d7f1306e4ea3d0b6d833112ad464`（任务启动时 HEAD）。
- 本任务提交应只包含本文档、手机 `VideoActivity` 和源码回归测试；若设备验证发现回归，整体回退该提交即可。
- **提交/恢复标签**：由本次 guard 收口命令生成；最终 commit 与 recovery tag 见交付摘要及 guard 状态。


### 2026-09-12：片头片尾异步回调与 EXO 起播竞态修复

- 两份现场日志显示 EXO 起播存在多轮缓冲/恢复定位，同时去广告规则网络探测出现超时；片头片尾计划查询也可能在播放器尚未 READY 时返回。
- 手机端和 TV 端的 `onIntroSkipPlanLoaded()` 原先在查询完成后立即调用 `applyAutoIntroSkip()`，可能让 seek 与 EXO 首次 prepare、历史位置恢复或去广告定位并发。
- 现在异步计划回调只在播放器处于 `STATE_READY` 时应用跳过。若计划提前返回，READY 主路径仍会申请并应用缓存计划，不会丢失自动跳过能力。
- 增加源码回归断言，确保两个 flavor 都先检查 READY，再执行自动跳过。
