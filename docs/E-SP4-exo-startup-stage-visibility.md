# E-SP4：起播阶段耗时可见 + 延后 Cues 可关

- 任务 ID：`E-SP4`
- 类别：Exo 性能专项
- 唯一文档：`docs/E-SP4-exo-startup-stage-visibility.md`
- 状态：两个单元均已实施（诊断可见 + 工厂 gate + UI 开关入口）。
- 下一动作：由用户在实机读「起播」行最慢阶段，并按下表做 A/B 取证。

## 用户观察到的失败

合并上游（`c72d09092a`，2026-08-24）后的测试版：**所有片源起播都要等一会**，包括非杜比视界、码率正常的 1080p；播起来不掉帧；之前版本没有此问题。用户明确排除了画质与掉帧诉求，只要解决起播等待。

## 为什么需要先做可见性而不是直接改

合并里能同时影响**所有片源**起播的候选有三个，且无法用现有信息区分：

1. `E-SP2` 远程 Matroska 延后 Cues（`FLAG_DEFER_SEEK_FOR_CUES`）。gate 只判 `isRemoteUri()`，**不判文件大小**，因此覆盖每一个远程 MKV 而非仅大文件。
2. nextlib FFmpeg 升级至 9.0.1。音频渲染器恒为 `EXTENSION_RENDERER_MODE_ON`，所有片源都经过它。
3. 自定义 `media3-extractor` 构件（含 deferred-cues patch）替换的是整个 extractor 模块，非仅 MKV 路径。

已排除的候选：`DolbyVisionP81ExtractorsFactory` 的逐样本包装。`Dv7ToP81TrackOutput.format()` 以 `isProfile7()`（仅比较 `format.codecs` 前缀 `dvhe.07`/`dvh1.07`）先判后放行，`sampleMetadata()` 非 DV7 时只多一个布尔判断，`sampleData()` 无条件转发；`HevcFrameTransformer` 与 10KB 重写缓冲在非 DV 片源上不实例化。开销不足以解释症状。

项目已有 `PlaybackTrace` 全阶段埋点（request / parse-complete / prepare / tracks / first-frame / audio-playable / ready），但 `stageElapsedMs` 是包级私有，且仅在 `SpiderDebug.isEnabled()` 时落日志。也就是说定位起播瓶颈必须导出 debug log，用户侧成本高。

因此第一步是让阶段耗时在设备上直接可见：一次播放即可把延迟归因到某个阶段，而不是逐个假设试改。这与 `E-SP2` 文档记录的基线口径一致（该文档 §1.2 记 `stage=tracks 7518ms`、`stage=first-frame 9877ms`，元凶为 73GB 文件尾部 `bytes=72937784060-` 的 Cues 请求）。

## 实现（第一单元）

- `PlaybackTrace` 新增 `startupSummary()` 与 `slowestStage()`。两者按**时间顺序**而非枚举声明顺序输出：`READY` 在枚举中声明于 `FIRST_FRAME` 之前，但实际通常后到，直接迭代 `EnumMap` 会把时间线打乱，故引入显式 `STAGE_ORDER`。未到达的阶段省略而非显示为 0。
- `PlayerManager` 暴露 `getStartupSummary()`、`getSlowestStartupStage()`，以及 `getNativeBufferedDuration()`（Exo 原生值，不含磁盘区间折叠）。
- `PlayerOsdController` 诊断面板新增「起播」行，显示各阶段累计耗时、相邻增量与最慢阶段。
- `PlaybackPerformanceSetting.isDeferredCuesEnabled()`，默认 `true`（维持现状）；`DolbyVisionP81ExtractorsFactory` 的 `deferSeekForCues` 改为 `isRemoteUri(uri) && isDeferredCuesEnabled()`。

顺带修复一处 beta 引入的回归：`PlayerOsdController` 第 483 行「缓冲偏少，可能是网络或源响应慢」原读 `getBufferedDuration()`，该值已被 `PlaybackDiskBufferStore` 磁盘区间灌高，导致该提示永不触发；改用 `getNativeBufferedDuration()`。

## 边界

只增加可观测性与一个默认不改变行为的开关读取点。不修改：缓冲阈值策略、解码器/渲染器选择、DV7→P8.1 与 HDR10 回退语义、音频直通、seek 精度、`E-SP2` 的按需建索引状态机本身。

第一单元不含 UI 入口（`PlaybackPerformanceCatalog` 条目与 `PlaybackPerformanceDialog` 接线），因初次实施时越出已声明 scope 被 task guard 拦截，故按契约收回并拆为第二单元。此时开关读取点已生效且默认值等于现状，代码自洽。

## 实现（第二单元）

`PlaybackPerformanceCatalog.DEFERRED_CUES` 条目加入 `addExo()`（仅 EXO 档，因为该 flag 只作用于 Exo 的 `MatroskaExtractor`），`PlaybackPerformanceSetting.putDeferredCuesEnabled()` 与 `PlaybackPerformanceDialog` 的取值/切换分支按既有 `DECODER_FALLBACK` 模式接线。设置位置：播放器设置 → 性能 → 解码档「延后MKV索引」。

验证：`compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `compileMobileArm64_v8aDebugJavaWithJavac` 均 `BUILD SUCCESSFUL`；`git diff --check` 通过。

## 验证

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "...PlaybackTraceTest"`：`tests="12" failures="0" errors="0"`。
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL`。
- `git diff --check`：通过。

验证边界：单测覆盖阶段排序、增量计算、未达阶段省略与 `clear()` 语义；编译证明诊断链路可构建。**不等同于**已定位用户的起播延迟成因——那需要实机读数。

## 待用户取证的判据

在同一设备、资源、网络下打开播放器诊断面板，读「起播」行的最慢阶段：

| 最慢阶段 | 指向 | 下一步 |
| --- | --- | --- |
| `tracks` | Matroska Cues / 索引建立 | 关「延后MKV索引」再测，对比同一片源 |
| `audio-playable` | 音频解码器初始化 | 把 FFmpeg 模式从 NEXTLIB 切 OFFICIAL 再测（对应 FFmpeg 9.0.1） |
| `first-frame` 且 `tracks` 正常 | 视频解码器初始化 | 查解码器选路与 DV7→P8.1 |
| `request` / `parse-complete` | 爬虫解析或网络 | 与播放器无关，查解析链路 |

## 回滚

恢复本任务的原子提交即可。不涉及依赖锁、AAR、native 二进制或 patch。
