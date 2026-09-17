# 详情直放播放回归修正记录（2026-09-15）

## Recovery anchor

- 目标：详情直放模式点击播放必须进入内嵌全屏播放器，不能误入沉浸融合内嵌播放。
- 基线：`06442ea996fc8e964f51da973507b8ac3fa8d186`。
- 范围：`TmdbDetailActivity.getDetailMode()` 单处模式还原、`DetailModeControllerTest` 回归用例、本任务记录。
- 当前状态：模式还原、首播时序与播放语义已提交；本轮复测确认最近首帧延迟改动再次让详情直放先显示融合详情页，已删除首帧延迟路径并恢复点击后立即全屏，待收口提交与恢复标签。

## 根因与修正

- 根因：`getDetailMode()` 在既无 `detail_mode` 也无 `fusion` 标记时固定返回炫彩详情（`DETAIL_OPEN_ENHANCED`）。详情直放模式通过无标记入口载入后，点击播放误判成沉浸融合并调用 `playInline()`，而不是进入详情全屏播放器。
- 修正：无 `detail_mode` 标记时改用 `Setting.getDetailOpenMode()` 还原用户当前选择；保留融合入口的 `fusion=true` 标记行为，已标记 `detail_mode` 的入口不受影响。
- 防回归：`DetailModeControllerTest.playerDetailMode_keepsFullscreenInlinePlayback` 锁定 `onPlay()` 的 `isFusionMode() -> playInline()` / `isPlayerMode() -> playDetailFullscreen()` 分支和无标记模式还原契约。

## 验证

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests DetailModeControllerTest --tests TmdbDetailActivityLayoutTest --offline` 通过，`BUILD SUCCESSFUL in 15s`；生产 Java 编译随用例执行通过。

## 运行时复测与二次修正（2026-09-15）

- 首次设备复测：模拟器 `192.168.50.3:5559` 已安装包含提交 `01c5798b0f` 的 APK，设置 `detail_open_mode=4`；点击“继续播放”后播放器仍以 252dp 内嵌卡片显示，详情页内容可见，说明前一版 Java 分支测试不足以覆盖首播异步时序。
- 根因：`playDetailFullscreen()` 在首播无视频尺寸时先设置 `inlineFullscreenDeferred=true`，但 `startInlinePlayer()` 随后无条件清零该标记；`STATE_READY` 到达后无法调用 `enterInlineFullscreen()`。
- 修正：在 `playDetailFullscreen()` 作出首播形态决策前清除旧的 deferred 状态；保留本次首播设置的状态，不在异步 `startInlinePlayer()` 中覆盖它。
- 防回归：`DetailModeControllerTest.playerDetailMode_preservesDeferredFullscreenUntilPlayerReady` 先在旧代码上失败，再在修正后通过，锁定 deferred 状态的生命周期。
- 最终验证：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.detail.DetailModeControllerTest --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest --offline` 通过，`BUILD SUCCESSFUL in 9s`；`git diff --check` 通过。
- 设备验证：重新打包并安装 `app-mobile-arm64_v8a-debug.apk` 后，从首页打开“早春晴朗”详情，点击“继续播放”；截图 `/tmp/webhtv-detail-direct-fixed.png` 显示播放器铺满 `1920x1080` 窗口，详情背景不再可见，播放标题为“早春晴朗：2. 职场不是过家家”，播放正常。

## 立即全屏复测与第三次修正（2026-09-15）

- 用户复测反馈仍可见融合式过渡。对 `192.168.50.3:5559` 重现：上一版本点击后 1 秒截图 `/tmp/webhtv-5559-after-1s.png` 仍为播放器卡片叠在详情页上，约 6 秒才全屏；入口日志确认 `mode=4`，不是设置或入口参数错误。
- 根因：`ea9b666752` 为避免首帧前黑色覆盖，给 `playDetailFullscreen()` 增加了 `inlineFullscreenDeferred`，导致详情直放首播必须等待视频尺寸/READY 才进入全屏；这与“点击后直接全屏”的产品契约冲突。
- 修正：删除 `inlineFullscreenDeferred` 字段、延迟判断和 READY 回调补偿路径；`playDetailFullscreen()` 在发起异步解析前直接执行 `enterInlineFullscreen()`，保留当前播放复用与融合模式路径不变。
- 防回归：`DetailModeControllerTest.playerDetailMode_entersFullscreenBeforeAsyncPlayerLoad` 先在旧延迟逻辑上失败，再在删除延迟后通过。
- 设备验证：重新打包安装后，5559 点击“继续播放”约 1 秒截图 `/tmp/webhtv-direct-immediate-fixed-1s.png` 已是全屏黑色播放器窗口；约 6 秒截图 `/tmp/webhtv-direct-immediate-fixed-playing.png` 已正常显示视频，详情页不再可见。

## 详情直放入口播放语义校正（2026-09-15）

- 设备复测确认，曾将 `isPlayerMode()` 加入 `maybeAutoPlayInline()` 后，用户从内容列表进入详情页会在资源加载完成后立即开始播放。
- 产品预期：详情直放只约束用户明确触发播放后的行为，即点击播放、继续播放或选集等操作后直接进入全屏播放器；进入详情页本身仍应停留在详情内容中。
- 修正：从 `maybeAutoPlayInline()` 的自动播放条件中移除 `isPlayerMode()`，保留融合模式和入口显式 `auto_play=true` 的既有自动播放语义；`playDetailFullscreen()` 的立即全屏逻辑保持不变。
- 防回归：新增 `playerDetailMode_waitsForExplicitPlaybackAction`，锁定详情直放不会因进入详情页而自动调用 `onPlay()`。
- 最终验证：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.detail.DetailModeControllerTest --offline` 通过，`BUILD SUCCESSFUL in 13s`。

## 全屏启动连续性修正（2026-09-15）

- 用户复测现象：点击播放后先进入全屏播放界面，随后短暂黑屏，再次出现播放画面。
- 根因：`playDetailFullscreen()` 已先调用 `enterInlineFullscreen()`，但异步地址解析入口 `playInline(long, String, String)` 随即调用 `stopInlinePlayerForReload()` 清空播放器，形成“显示全屏容器 → 清空播放器 → 重新准备播放”的二次切换。
- 修正：移除异步解析开始前的 `stopInlinePlayerForReload()`，保留真正开始新播放时 `startInlinePlayer()` 内既有的播放器停止、清理和重新准备流程，避免在等待解析期间提前破坏全屏播放表面。
- 防回归：新增 `playerDetailMode_doesNotClearPlayerAfterEnteringFullscreen`，锁定详情直放进入全屏以后，异步解析入口不得提前清空播放器。
- 定向验证：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.detail.DetailModeControllerTest` 通过，`BUILD SUCCESSFUL in 11s`；`git diff --check` 通过。

## 首帧延迟回归修正（2026-09-15）

- 用户复测现象：详情直放点击播放后先显示沉浸融合详情页，没有直接全屏。
- 根因：`948000af96` 为避免首帧前黑屏引入 `detailPlayerFullscreenPending`，`playDetailFullscreen()` 首播时保留详情页，直到 `onFirstFrameRendered()` 才进入全屏；这覆盖了此前 `dcae3bb7c7` 的“点击后立即全屏”契约。
- 修正：删除 `detailPlayerFullscreenPending`、`revealDetailPlayerFullscreen()` 和首帧回调；`playDetailFullscreen()` 在异步解析前直接执行 `enterInlineFullscreen()`。保留异步解析入口不提前清播放器的既有修复。
- 防回归：改写 `playerDetailMode_entersFullscreenBeforeAsyncPlayerLoad` 与 `playerDetailMode_doesNotClearPlayerAfterEnteringFullscreen`，锁定 `enterInlineFullscreen()` 必须先于 `playInline()`，且不得重新引入首帧延迟。
- 定向验证：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*DetailModeControllerTest' --tests '*TmdbDetailActivityLayoutTest'` 通过，`git diff --check` 通过。

## 回滚

- 回退 `TmdbDetailActivity.getDetailMode()` 单处修改和 `DetailModeControllerTest.playerDetailMode_keepsFullscreenInlinePlayback` 用例即可恢复基线行为。
