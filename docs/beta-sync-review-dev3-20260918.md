# dev3 同步 beta 与代码评审记录（2026-09-18）

## 目标

同步远端 `beta` 最新代码，评审当前任务的未提交改动及同步引入的已提交改动，完成针对性验证与复评后提交、推送，并创建合入 `beta` 的拉取请求。

## 同步基线

- 工作分支：`dev3`
- 同步前提交：`d014ea2a21620d221145a845be93776ac8a21b0e`
- 同步的远端 `beta`：`58f53536fd998a77a060718d478fb08c5933607d`
- 同步方式：快进合并
- 同步结果：成功，无冲突

## 改动内容

### 当前任务改动

- 将站点健康报表对话框调整为接近全屏的自适应尺寸。
- 横屏保留 `24dp` 外边距，竖屏保留 `16dp` 外边距。
- 宽高均保留最小值保护，避免极端屏幕尺寸产生无效窗口参数。
- 增加源码约束测试，覆盖横竖屏边距、宽高计算、装饰视图边距及旧比例尺寸移除。

### 从 beta 同步的改动

- 前台崩溃时恢复显示自定义故障恢复界面，而不是静默终止。
- 增加对应的崩溃恢复配置测试。

## 评审结论

### 第一轮评审

- 站点健康报表尺寸改动范围集中，没有改变报表数据、筛选、排序或清理行为。
- 尺寸计算使用现有 `ResUtil` 屏幕尺寸与 dp 转换能力，横竖屏策略明确。
- `Math.max(1, ...)` 可避免异常环境下产生零或负尺寸。
- 同步自 `beta` 的崩溃恢复改动仅恢复前台崩溃的自定义恢复页面，并有对应测试约束。
- 未发现需要修改的问题。

### 验证

执行：

```text
./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.setting.SiteHealthReportSourceTest \
  --tests com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest
```

结果：`BUILD SUCCESSFUL`，87 个任务中 6 个执行、81 个为最新状态。

同时执行 `git diff --check`，通过。

### 第二轮复评

- 重新核对相对 `origin/beta` 的完整任务差异，改动仍仅涉及站点健康报表窗口尺寸及其测试。
- 测试覆盖当前任务和本次同步的崩溃恢复行为。
- 未发现正确性、兼容性、性能或作用域问题。
- 复评通过，可以提交并创建合入 `beta` 的拉取请求。

## 回滚方式

如需回滚当前任务，可还原站点健康报表对话框原有比例尺寸，并删除本次新增的尺寸约束测试；从 `beta` 同步的提交不属于当前任务回滚范围。

## 本轮同步复评补充（2026-09-18）

### 同步结果

- 已将 `origin/beta` 合入 `dev3`，合并提交为 `5791c9c713853ffdf1ff6550472468f0afd7faed`。
- 合并过程由 `ort` 策略自动完成，没有未解决冲突。
- `origin/beta` 已是当前 `HEAD` 的祖先。

### 复评发现与修复

1. 站点健康报表的“最近失败”排序只统计搜索、详情、解析和播放阶段，遗漏本轮新增的首页与分类阶段。现已把 `row.home.lastFailAt` 和 `row.category.lastFailAt` 纳入排序，并增加源码约束测试。
2. HLS 兼容规则会缓存 `RuleConfig` 编译结果，但保存用户广告规则或切换默认规则启用状态时只失效 `RuleConfig`，没有同步失效 `HlsRuleConfig`。现已在 `UserAdRuleStore.save` 与 `DisabledDefaultRuleStore.save` 中同步调用 `HlsRuleConfig.invalidate()`，避免规则变更后继续使用旧的 HLS 编译缓存，并增加回归测试。

### 最终验证

- Mobile arm64-v8a 定向单元测试：`BUILD SUCCESSFUL`，87 个 Gradle actionable tasks，5 executed、82 up-to-date。
- Leanback arm64-v8a 定向单元测试：`BUILD SUCCESSFUL`，87 个 Gradle actionable tasks，6 executed、81 up-to-date。
- 覆盖测试：`SiteHealthReportSourceTest`、`SiteHealthReportDialogSourceTest`、`HlsRuleConfigTest`、`MpvHlsAdblockGateTest`、`ExoParserAdblockGateTest`、`M3u8LegacyFallbackTest`、`HlsAdblockPipelineTest`。
- `git diff --check` 通过。
- `task_guard.sh check` 通过。
- 合并后的构建仅保留既有 deprecated API、unchecked operation 和 32 位原生库提示，没有新增编译或测试失败。

### 复评结论

同步内容与 `dev3` 当前站点健康统计、HLS 去广告兼容逻辑可以共存。本轮发现的两个缓存/排序遗漏均已修复并由 Mobile 与 Leanback 两个变体的定向测试覆盖，可以进入提交、推送及创建合入 `beta` 的拉取请求阶段。

## 最新 beta 同步与未推送播放修复复评（2026-09-18 晚间）

### 同步基线

- 工作分支：`dev3`
- 同步前提交：`fe88163cf750e62b8aa664c74334955c59dfda54`
- 同步的远端 `beta`：`32a52698e5dab09fe18e49d18849a947057ca717`
- 合并共同祖先：`533d3bf18d44cede36b0bd43cadb436c1ef3f481`
- 合并方式：保留双方历史的 `--no-commit --no-ff` 合并，完成后由任务守卫形成合并提交。

### 冲突与合并处理

- `ExoPlayerEngine.java` 与 `ExoUtil.java` 发生内容冲突。
- `beta` 侧新增 ASS 会话、双字幕会话和诊断包装；`dev3` 未推送提交新增播放媒体时钟、PCM 信号管线和音频处理器接入。
- 两者位于不同职责层，现已统一为一个完整播放器构建调用链，同时传递 `assSession`、`subtitleSession`、`mediaSignals` 和 `mediaClock`。
- 保留原有短参数重载并让它们委托到完整实现，避免其他 Exo 调用路径失去兼容性。
- 音频输出保留 `beta` 的诊断 Provider/Sink 包装，同时在其内部接入本地媒体时钟管线和音频采样处理器。

### 第一轮评审

- 发现首次手工合并后的播放器构建参数数量错误，主代码编译能够直接暴露该问题；已补齐完整参数顺序并清理全部冲突标记。
- 复核两笔未推送播放修复：重复请求取消、首帧稳定窗口、预加载分段间隔均仍作用于最终合并树。
- 复核 `PlayerManager` 创建 Exo 引擎时使用的播放媒体信号与时钟实例，和 `ExoUtil` 最终重载参数一致。
- 未发现 beta 新增 ASS/双字幕能力被本地改动覆盖，也未发现本地预加载节流被 beta 自动合并删除。

### 第一轮验证

执行：

```text
bash ./gradlew --console=plain \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac
```

结果：`BUILD SUCCESSFUL in 1m 2s`，47 个 actionable tasks。

执行：

```text
bash ./gradlew --console=plain \
  :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.player.exo.PreCachePolicyTest \
  --tests com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest \
  --tests com.fongmi.android.tv.player.exo.ExoCompressedAudioDirectPolicyTest \
  --tests com.fongmi.android.tv.player.exo.ExoAudioOutputStateTest
```

结果：`BUILD SUCCESSFUL in 22s`，87 个 actionable tasks，15 executed、72 up-to-date。

### 第二轮复评

- 以最终工作树对照 `origin/beta` 复核，差异仅剩 9 个预期本地文件：播放修复、预加载节流、对应测试及本评审文档。
- 重新检查 `ExoPlayerEngine` 两个构建入口和 `ExoUtil` 全部重载，均最终落到同一个完整实现。
- 重新检查预加载 `check -> update -> schedule -> finishTask` 状态流，完成分段后的 5 秒间隔不会影响前台 buffering、seek 或暂停处理路径。
- 未发现正确性、兼容性、性能、生命周期或回滚问题，复评通过。

### 回滚方式

- 回滚本轮 beta 同步：对任务守卫生成的合并提交执行 `git revert -m 1 <merge-commit>`。
- 单独回滚未推送播放修复：恢复 `PreCache`、`PreCachePolicy`、`ExoPlayerEngine`、`ExoUtil`、`PlayerManager` 与 TV `VideoActivity` 的相关改动，并删除对应源码约束测试。
