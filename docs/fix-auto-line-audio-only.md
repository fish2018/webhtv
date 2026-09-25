# fix-auto-line-audio-only

## 目标与验收

- 播放失败后自动切换到下一条线路时，不再沿用失败播放器实例的解码/Surface 状态。
- 保留本剧用户记住的播放核心；自动线路回退不得自动改选另一个核心。
- Mobile 与 Leanback 保持一致处理；手动切换核心的既有行为不变。

## 原因

`onError()` 原来只执行 `reset/stop` 后进入 `startFlow()`。自动切线路会重新取址并复用同一个引擎实例；若失败现场留下视频 renderer/Surface 异常，音频仍可能继续，形成“有声音没画面”。手动切核心会重建引擎，因此能恢复。

## 修复

在自动回退取址前调用 `applyHistoryPlayerKernel(true)`。该方法恢复本剧记忆的核心，并直接进入 `preparePlayer(type, force)`；`preparePlayer(type, force)` 保留既有起播路径的默认语义，但在 `force=true` 时即使目标核心与当前核心相同，也释放并重建同核心引擎，同时要求 UI 清理并重绑渲染 Surface、进度条和 UI 状态。这样不会改变用户选择的核心，也不影响正常自动回退的地址流程。

第一轮评审发现仅调用原 `preparePlayer(type)` 不满足验收：该方法在目标核心等于当前核心时直接返回，音频-only 故障现场仍可能被下一条线路继承。因此修复必须显式区分“普通起播前准备”与“错误恢复强制重建”，避免影响手动切换和正常起播。

2026-09-25 测试版日志证实第一版强制重建仍未生效：`preparePlayer(type, force)` 在回调 `onPlayerRebuild()` 前已把 `spec=null`，导致 `isOwner()` 失败并跳过 `setRender()`；同时 `onError()` 通过原 helper 触发了一次普通重建、再触发一次强制重建，造成重复重建。现在 `onPlayerRebuild()` 完成新播放器和 Surface 重绑后才清空旧 `spec`，且错误恢复路径只执行一次强制重建。

## 验证

- `PlayerPlaybackRegressionSourceTest.autoLineFallbackRebuildsRememberedPlayerBeforeFetchingNextLine`
- `PlayerPlaybackRegressionSourceTest.automaticLineFallbackForcePreparesEvenForTheRememberedKernel`
- `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.PlayerPlaybackRegressionSourceTest --no-daemon`：14 个测试通过。
- `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon`：通过。
- 如后续需要实机复核，用首线路失败且第二线路可播的资源覆盖安装测试包，验证自动切线路后画面恢复。

## 回滚

- 撤销本提交即可恢复旧行为；改动只在 Mobile/Leanback `VideoActivity.onError`。
