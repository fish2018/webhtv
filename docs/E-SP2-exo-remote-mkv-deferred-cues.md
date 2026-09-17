# E-SP2：Exo 远程大体积 Matroska/MKV 延后 Cues

- 任务 ID：`E-SP2`
- 类别：Exo 性能专项
- 唯一文档：`docs/E-SP2-exo-remote-mkv-deferred-cues.md`
- 评估日期：2026-08-22（Asia/Shanghai）
- 范围：Exo 起播速度、远程百度网盘 MKV、Matroska Cues/索引，以及首次随机访问。
- 状态：链式 `SeekHead` 修正已完成；定向测试、extractor 发布、双产品 arm64 编译和 vivo 实机连续 seek 均通过。
- 下一动作：无；本单元提交并创建 recovery tag 后关闭。

## 结论先行

当前 Exo 起播慢的主要瓶颈不是网络连接建立、`DefaultLoadControl` 的启动缓冲阈值，也不是 Exo 预加载抢占，而是 `MatroskaExtractor` 为建立可 seek 时间轴主动读取位于文件尾部的 Cues。

针对当前项目，结论分为四类：

| 类别 | 决定 | 说明 |
| --- | --- | --- |
| 前置已完成 | [`E-SP1` 首帧立即可见](E-SP1-exo-first-frame-visible.md) | 只作为前置依赖引用；其实现、验证、commit 和 tag 不在本文重复记录。 |
| 已实施待实机验收 | 远程大 MKV 延后 Cues 读取，保留首次 seek 时按需建索引 | 成熟播放器普遍采用“启动先读 Tracks/首个 Cluster，索引按需读取或增量建立”；Media3 现成 flag 会直接让媒体不可 seek，因此实现增加了按需建索引状态机。 |
| 暂缓 | 直接降低 `startMs`/`rebufferMs`、禁用 TrueHD 初始化、关闭 Exo 预加载 | 当前证据不能证明这些措施减少首帧等待，且会增加卡顿、音频能力或缓存行为风险。 |
| 忽略 | 通过增加连接超时、重试次数或替换 OkHttp 解决本问题 | 日志中 Range 请求正常返回 `206`；这类调整不会消除尾部 Cues 读取。 |

本轮没有得到“可以立即合并一行配置就加速”的安全结论。最终采用 Media3 deferred Cues：起播时延后读取 Cues，第一次随机访问时按需建立完整索引，并以独立 commit/tag 保持可回滚。

## 1. 当前基线与可重复证据

### 1.1 项目与回滚基线

- 历史评估基线：`f2721c43b6654ae7307647ebaaaa4248a50a9ab7`（Checkpoint 1 记录；不是当前实现 HEAD）。
- E-SP2 评估前置 HEAD：`c07e2b27eddbbee3240ed25fd6e2c8e5a64c5c7e`；E-SP1 recovery tag：`recovery/exo-sp1-first-frame-visible-20260822/20260822224344-c07e2b27eddb`。
- 该评估阶段没有生产代码或依赖变更；后续实现结果见 Checkpoint 4-6。
- 预先存在的脏路径保持不变：`.gitignore`、`third_party/fongmi-repositories-lock.json`、`.codex/`、`AGENTS.md`、已有上游评估文档。

### 1.2 Exo 远程大 MKV 实测

证据文件：`/Users/macbookpro/Downloads/webhtv-debug-log (19).txt`，trace `p-2vl4f6-g`，从约 140 秒断点启动。

关键时间线：

| 事件 | 观测 |
| --- | ---: |
| `stage=request` | 9 ms |
| `stage=prepare` | 60 ms |
| 首个 `bytes=0-` Range 完成 | 约 890 ms |
| Cues 疑似尾部 Range：`bytes=72937784060-` | 从 21:16:03.602 开始，直到轨道阶段前持续等待 |
| `stage=tracks` | 7518 ms |
| 视频解码器 `c2.mtk.hevc.decoder` 初始化 | 97 ms |
| TrueHD AudioTrack 初始化 | 约 1.9 s（轨道阶段后） |
| `stage=first-frame` | 9877 ms |
| 首帧时 Exo 状态 | 仍为 `BUFFERING`，前向缓冲约 187 ms |
| `stage=ready` | 本次未在首帧后及时到达；会话后续被切换/停止 |

启动期间至少出现以下 Range：

```text
bytes=0-
bytes=72937784060-
bytes=867350-
bytes=1326257783-
```

其中约 73 GB 文件尾部的 `bytes=72937784060-` 与 Matroska `SeekHead -> Cues` 跳转高度吻合。它不是连接失败：响应为 `206 Partial Content`，且请求随后继续读取头部/Cluster 数据。

### 1.3 MPV 对照

证据文件：`/Users/macbookpro/Downloads/webhtv-debug-log (17).txt`，trace `p-2capni-9`。

- `stage=tracks`：2530 ms；
- `stage=ready`：5702 ms；
- `stage=first-frame`：5706 ms；
- 使用 `hwdec=mediacodec`、`vo=mediacodec_embed`、`mpv-surface-direct`；
- `rebufferCount=0`。

MPV 更快不能简单说明网络更好。它的 Matroska demuxer 允许启动时不读取仅用于 seek 的 Cues，并在需要 seek 时再读取/建立索引；Exo 默认行为不同。

## 2. 官方 Media3 语义

来源：

- [Media3 `MatroskaExtractor.java`](https://raw.githubusercontent.com/androidx/media/release/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java)
- [Media3 `DefaultLoadControl.java`](https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java)
- [Media3 Matroska extractor tests](https://raw.githubusercontent.com/androidx/media/release/libraries/extractor/src/test/java/androidx/media3/extractor/mkv/MatroskaExtractorTest.java)

### 2.1 Cues 行为

官方 `MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES` 的定义是：

1. 默认情况下，如果 `SeekHead` 指向位于首个 Cluster 之后的 Cues，Extractor 会跳转读取 Cues；
2. 设置该 flag 后不再跳转读取 Cues；
3. 如果 Cues 在首个 Cluster 后，媒体会被视为不可 seek。

因此它不是“先播后索引”的开关。全局启用会直接影响：

- 断点续播和从 2 分钟等非零位置启动；
- 用户 seek、章节/时间轴跳转；
- Blu-ray/本地 MKV 的随机访问；
- 可能的字幕、章节和 DV/TrueHD 关联时间轴行为。

当前本地 `third_party/patches/media3-dolby-vision-matroska.patch` 只增加 Matroska DV BlockAdditional RPU 输出，没有改变 Cues 策略。`DolbyVisionP81ExtractorsFactory` 当前使用 `FLAG_EMIT_RAW_SUBTITLE_DATA` 和本地 DV 处理，不等于已经启用或验证了 Cues 优化。

### 2.2 LoadControl 不是本次主因

官方 `DefaultLoadControl.shouldStartPlayback` 只根据已获得的 `bufferedDurationUs`、播放速度、重缓冲状态和目标 buffer bytes 决定是否开始播放。它不能避免 Extractor 在轨道建立前发起尾部 Cues Range。

本次日志还明确记录了当前自动策略最终使用 `startMs=3000`、`rebufferMs=5000`，但首帧只等待约 187 ms 前向缓冲，说明首帧等待主要发生在轨道/解码建立，而不是 3 秒启动缓冲门槛。

## 3. 成熟开源实现对照

### 3.1 MPV 原生 Matroska demuxer

来源：[mpv `demux/demux_mkv.c`](https://raw.githubusercontent.com/mpv-player/mpv/master/demux/demux_mkv.c) 和 [mpv options](https://raw.githubusercontent.com/mpv-player/mpv/master/DOCS/man/options.rst)。

关键实现：

- `demux_mkv_open` 先读取 EBML、Info、Tracks 和可用头部；
- 如果未解析的尾部头部只有 Cues，则打印 `Deferring reading cues.`，不在打开阶段跳转；
- `read_deferred_cues` 只在索引确实需要时读取 Cues；
- 默认 `--index=default` 使用已有索引或按需构建；另有 `--index=recreate` 明确表示不读/不使用文件索引。

这是“启动路径与随机访问路径分离”的成熟做法。它并没有无条件放弃 seek，而是把代价推迟到第一次真正需要索引的操作。

### 3.2 FFmpeg Matroska demuxer

来源：[FFmpeg `libavformat/matroskadec.c`](https://raw.githubusercontent.com/FFmpeg/FFmpeg/master/libavformat/matroskadec.c)。

关键实现：

- `matroska_execute_seekhead` 对 Cues 明确 `defer cues parsing until we actually need cue data`；
- `matroska_read_header` 建立流和基本元数据后，不必先解析完整 Cues；
- `matroska_read_seek` 在真正 seek 时才解析 Cues；
- `AVFMT_FLAG_IGNIDX` 是显式忽略索引的能力，而不是默认启动策略。

FFmpeg 与 MPV 的共同点不是“关闭索引”，而是“把索引解析放到需要随机访问的路径”。这比直接套 Media3 `FLAG_DISABLE_SEEK_FOR_CUES` 更符合当前产品需求，但 Exo `Extractor`/`SeekMap` 生命周期需要单独适配，不能盲目移植。

### 3.3 VLC Matroska demuxer

来源：[VLC `modules/demux/mkv/mkv.cpp`](https://raw.githubusercontent.com/videolan/vlc/master/modules/demux/mkv/mkv.cpp)。

VLC 将 MKV 的 seekability、fast-seekability、Cues/segment preload 分开管理，并把 linked segments 的本地目录预加载作为显式行为。它说明成熟播放器会根据输入是否可 seek、是否 fast-seekable 以及是否需要关联 segment 选择策略，而不是把“预加载/索引/起播”混成一个全局开关。

## 4. 当前项目链路核对

已确认的相关代码：

- `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`
  - 使用 `DefaultMediaSourceFactory`、缓存 DataSource、`PriorityTaskDataSource`；
  - `setLoadOnlySelectedTracks(...)` 已存在；
  - `DefaultExtractorsFactory` 外包 `DolbyVisionP81ExtractorsFactory`。
- `app/src/main/java/com/fongmi/android/tv/player/exo/AutoTargetLoadControl.java`
  - 已有自动 target bytes、内存压力和预加载优先级协调；不应把它当作 Cues 读取优化。
- `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java`
  - 已记录 `onRenderedFirstFrame`，并在首帧后取消通用启动超时；该修复解决误报，不改变 Extractor 读取顺序。
- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`
  - 已记录 `TRACKS`、`FIRST_FRAME`、`READY` 三个独立阶段；
  - `PlaybackStartupPolicy` 当前偏向把视频首帧作为启动完成信号，但 UI shutter 的消费仍集中在 `PlaybackActivity.syncShutter`，需要单独设计“首帧可见、音频/READY 未完成”的状态边界。
- `app/src/main/java/com/fongmi/android/tv/player/PlaybackStartupPolicy.java`
  - 当前 `resolve` 只有 `ready=true` 且具备视频首帧信号时才返回 `FIRST_FRAME`；这限制了“首帧先显示”的进一步应用，不能仅改 loading 文案解决全部问题。
- `third_party/patches/media3-dolby-vision-matroska.patch`
  - 只负责 DV BlockAdditional RPU 注入；不提供 Cues 延迟索引。

## 5. 候选阶段与实施边界

### E-SP2：远程大 MKV 的 Cues 延后/按需索引

- 用户决策：已批准并实施，进入候选实机验收。
- 目标：从文件头或可接受的起始位置播放时，不因尾部 Cues 读取阻塞 Tracks/首帧；第一次 seek/断点启动时再读取 Cues 或建立局部索引。
- 不能直接做：全局设置 `FLAG_DISABLE_SEEK_FOR_CUES`。该 flag 会把 Cues 位于首个 Cluster 后的媒体标记为不可 seek。
- 推荐实验形态：在本地 Media3 fork 中增加“defer Cues”实验模式，或在 DataSource/代理层做有界的 Cues 预取；仅对满足以下条件的远程 VOD 开启：可识别完整文件大小、起播位置为 0、来源不是 ISO/光盘、用户未要求立即 seek、DV/TrueHD/字幕轨道仍正常。
- 首次实验不应覆盖：非零断点、用户 seek、章节跳转、未知长度 HTTP、live/分片流、Blu-ray/ISO、加密媒体。
- 关键难点：Media3 `SeekMap` 通常在 extractor 初始化阶段发布；要在不破坏 Exo seek contract 的情况下延后索引，可能需要 Media3 extractor/period 层联合修改，而不是只改 App Java。
- 最小验收：
  1. 起始位置 0 的远程大 MKV：记录 `tracks`、`first-frame`、`ready` 和尾部 Range 等待时间；
  2. 同一资源从 140 秒断点启动：必须不比基线更慢且能正常 seek；
  3. 前后 seek、章节、字幕、TrueHD/Atmos、DV7 P8.1/HDR10 fallback；
  4. 中断/重试/未知长度/尾部 Cues 缺失；
  5. 至少同一设备、同一资源、同一网络条件下基线与候选各 3 次，比较中位数和失败率。
- 建议：只做 feature flag + telemetry 实验，不直接更新正式 Media3 lock/AAR。

### 被否决的替代方案：调低起播缓冲阈值

- 当前证据：首帧前向缓冲只有约 187 ms，但轨道阶段已经耗时约 7.5 s；不是主瓶颈。
- 风险：Range 波动、TrueHD 和高码率 DV7 资源更容易起播后卡顿。
- 决定：除非 E-SP2 之后仍有明确的 `shouldStartPlayback` 等待证据，否则不实施。

### 被否决的替代方案：禁用或延迟 TrueHD

- 当前证据：TrueHD 初始化约 1.9 s，但发生在 Tracks 阶段之后，视频首帧主要延迟仍已形成。
- 风险：损失 TrueHD/Atmos、音画同步、直通/降级能力。
- 决定：不为追求起播速度默认禁用；只有单独的音频初始化 profiling 证明收益，才建立独立实验。

### 被否决的替代方案：关闭预加载或替换网络库

- 当前 `PreCache` 在首帧前处于等待/取消状态，前台播放 DataSource 优先级更高；没有证据证明它抢占主读取。
- 远程请求为正常 `206`，连接建立不是主要耗时。
- 决定：不作为本次 Exo 起播优化方案。

## 6. 实施顺序

1. 前置 `E-SP1` 已独立完成，不在本文重复记录。
2. E-SP2 的 Media3 patch、AAR 和 HTTP/HTTPS gate 已按独立提交完成。
3. 只有通过同设备/同资源/同设置的基线比较和 seek 回归，才能把候选实现视为性能验收完成。
4. 不得因为 MPV 更快而直接复制其 demux 实现到 Exo。

## 8. 研究来源与证据等级

| 来源 | 类型 | 等级 | 用途 |
| --- | --- | --- | --- |
| Media3 `MatroskaExtractor.java` | 本地采用 revision `e3e922d5c01bc0b564849940fe589daf37360d15`；官方 release HEAD `2bc207851df311340767e913931ca7b28cab1794`（2026-08-22） | A | 确认 `FLAG_DISABLE_SEEK_FOR_CUES` 的精确语义 |
| Media3 `DefaultLoadControl.java` | 官方源码 | A | 确认启动缓冲只作用于已获得的 buffered duration |
| Media3 `MatroskaExtractorTest.java` | 官方测试 | A | 确认官方默认测试路径不等于关闭 Cues |
| mpv `demux_mkv.c` + options | 成熟开源实现/源码，HEAD `49418246f30a9c24af31ac184aa24f39755db89a`（2026-08-22） | A/B | 确认 Cues 延后、按需读取和索引模式 |
| FFmpeg `matroskadec.c` | 成熟开源实现/源码，HEAD `eb0bfa852e7b9c524960300607ba2c4617060a9b`（2026-08-22） | A/B | 确认 seekhead/Cues 延后到真正 seek |
| VLC `mkv.cpp` | 成熟开源实现/源码 | A/B | 确认 seekability、fast-seekability、preload 分层 |
| WebHTV trace `p-2vl4f6-g` | 本地可重复日志 | A | 确认当前实际尾部 Range 与阶段耗时 |

## 9. E-SP2 深度研究与最终方案决策

### 9.1 问题的精确定义

E-SP2 要解决的不是“让所有 MKV 都不可 seek”，也不是“关闭 Cues”。目标是把两个成本不同的动作分开：

1. 起始位置为 0 的远程、可 Range 的大体积 MKV，先读取 EBML/Info/Tracks 和首个 Cluster，尽快交给解码器；
2. 用户第一次 seek、章节跳转或非零断点启动时，再读取尾部 Cues，并用 Cues 计算精确的 Cluster 位置。

成功标准是首帧等待减少，同时首次随机访问仍使用正常 Matroska seek 语义；任何不能证明输入适合该路径的资源继续走现有默认流程。

### 9.2 外部证据（按决策价值排序）

| 结论 | 来源与完整 revision | 关键证据 | 对 WebHTV 的影响 |
| --- | --- | --- | --- |
| Cues 是用于按时间定位 Cluster 的索引，不是播放样本本身 | Matroska 规范：<https://www.matroska.org/technical/cues.html>（访问 2026-08-22） | `Cues` provides an index of `Cluster` elements; 视频 keyframe 应被 CuePoint 引用 | 可把索引读取从首帧路径移到随机访问路径；不能丢弃正常 seek 能力 |
| Media3 默认会在首个 Cluster 前跳到 Cues | 本地 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15`，`MatroskaExtractor.java:929-941,2320-2340`；官方源码 <https://github.com/androidx/media/blob/release/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java> | `seekForCues` 触发 `RESULT_SEEK`；解析完成后再跳回原位置 | 当前日志尾部 `bytes=72937784060-` 与该逻辑一致，主瓶颈在 extractor，不是 LoadControl |
| `FLAG_DISABLE_SEEK_FOR_CUES` 会牺牲 seek | 同上，官方 API 文档 <https://developer.android.com/reference/androidx/media3/extractor/mkv/MatroskaExtractor> | Cues 在首个 Cluster 后时，设置 flag 会把媒体标为 unseekable | 不能全局设置；否则断点、时间条、章节和切轨会退回 0 或失效 |
| Media3 的 SeekMap 可以在准备后更新，但没有现成“延后 Cues”契约 | 本地 `ProgressiveMediaPeriod.java:940-1010`、`1088-1110` | `seekMap()` 回调可在 `prepared` 后再次刷新；seek 时由 `seekMap.getSeekPoints()` 计算 DataSpec 起点 | 可在 Media3 fork 中增加一个明确的 deferred seek-map 状态，而不改 App 的所有播放器路径 |
| FFmpeg 采用按需解析 Cues | FFmpeg `eb0bfa852e7b9c524960300607ba2c4617060a9b`，`libavformat/matroskadec.c`（master，访问 2026-08-22） | `matroska_execute_seekhead()` 明确跳过 Cues；`matroska_read_seek()` 首次 seek 时调用 `matroska_parse_cues()` | 证明“延后而非禁用”是成熟实现；可移植的是时机/状态，不是直接复制 C 代码 |
| mpv 采用启动延后、随机访问时读取 | mpv `49418246f30a9c24af31ac184aa24f39755db89a`，`demux/demux_mkv.c`（master，访问 2026-08-22） | `Deferring reading cues.`；`read_deferred_cues()` 在 seek/index 构建路径调用 | MPV 更快的根因得到独立源码印证；Exo 需要补生命周期适配 |
| SeekHead/Cues 组合存在真实兼容性缺陷，必须先修正顺序 | Media3 commit `859f7b3b5388378698ff23a667d3e2db5ac41aed`，issue #3377 <https://github.com/androidx/media/issues/3377> | Tracks 在 Clusters 后时旧逻辑会先建 Cues，`primarySeekTrackNumber` 尚未确定，时间线永久 unseekable；该提交将 `maybePrepareSeekMap()` 延后到 Tracks 已读 | E-SP2 patch 必须包含该修正或等价逻辑；否则“保留 seek”目标本身不成立 |
| 递归 SeekHead 需要完整处理，不能只取一个入口 | Media3 PR #2268 的 commit `ffca82f981e975d302c6480ba9cbce6e05260e74`、`a77bd285dcfe3ebd56eb5f487f55638625005e35` 等 <https://github.com/androidx/media/pull/2268> | PR 讨论指出多个/递归 SeekHead、IO 重试和未知长度输入会改变访问顺序；简单实现曾导致样本丢失 | 不在 App/DataSource 层猜测 Cues 偏移；复用 Media3 的解析状态和测试模型 |
| 当前项目的网络链路已支持 Range 和 EOF 恢复 | WebHTV `PlaybackBytePositionDataSource.java`、`HttpEofRecoveryDataSource.java`、本地 trace `p-2vl4f6-g` | 尾部请求返回 `206`；缓存/Range/重连已有独立诊断 | 不替换 OkHttp、不增加超时；优化点应留在 extractor 生命周期 |

### 9.3 方案比较

| 方案 | 首帧收益 | seek/断点 | 代码与回滚风险 | 决定 |
| --- | --- | --- | --- | --- |
| 不变更 | 无 | 完整 | 最低 | 不能解决当前 7-10 秒尾部等待 |
| 全局 `FLAG_DISABLE_SEEK_FOR_CUES` | 高 | 直接破坏 | 低实现成本、高产品风险 | 拒绝 |
| DataSource/代理后台预取尾部 | 通常无，前台请求仍可能竞争 | 完整 | 受缓存、并发和服务端 Range 行为影响；不能阻止 extractor 等待 | 拒绝为主方案 |
| 起播临时 unseekable，第一次 seek 重建播放器 | 高 | 可恢复，但会重建 Surface/音频状态 | App 状态复杂，首个 seek 可能丢状态 | 仅保底，不作为最终方案 |
| Media3 deferred Cues + 可更新 SeekMap | 高 | 先播；首次随机访问精确读 Cues | 需要 extractor patch 和临时 SeekMap；可用输入门槛与 App URI 范围控制 | 推荐 |

### 9.4 推荐实现的状态机

```text
prepare(position=0)
  -> provisional DeferredSeekMap(duration, isSeekable=true, points=START)
  -> Tracks/formats/endTracks
  -> Cluster samples -> first frame (不读尾部 Cues)

first seek/非零 prepare
  -> 临时 SeekMap 保留目标 timeUs，并把 byte position 指向已知 Cues
  -> extractor 直接从 Cues 读取，不重读 EBML/Tracks
  -> 发布完整 MatroskaSeekMap
  -> RESULT_SEEK 到精确 CueClusterPosition
  -> 正常样本读取/章节/字幕/TrueHD/DV 继续
```

实现约束：

- 只对 Matroska/WebM extractor 的显式实验 flag 生效；MP4、TS、HLS、DASH、RTSP、ISO、live 和未知长度非 VOD 不改变；
- `DeferredSeekMap` 的 0 秒 point 指向文件开头；非零 point 保留目标时间并指向已知 Cues 偏移，实际媒体位置由 extractor 建图后重新定位；
- Cues 解析前不改变 `Format`、DV BlockAdditional RPU、HDR10 fallback、AV3A、TrueHD/Atmos、字幕原始数据和软解降载；
- IO 重试必须清理“已访问 SeekHead/待处理跳转”状态，沿用 Media3 Extractor 的 unchanged-position contract；
- 先加入 `FLAG_DEFER_SEEK_FOR_CUES`，默认只对符合输入条件的远程大文件启用；不把该 flag 写入所有 extractor；
- 任何异常（Cues 缺失、未知长度、解析失败、首帧/seek 失败）回退到现有默认 extractor，不能回退到软解或改变 DV7/HDR10 策略。

### 9.5 结合 WebHTV 代码的审阅结论

- `MediaSourceFactory` 已集中创建 `DefaultExtractorsFactory` 和 `DolbyVisionP81ExtractorsFactory`，是唯一合适的 Exo extractor 注入点；不需要改 MPV 或共用 native 二进制。
- `DolbyVisionP81ExtractorsFactory` 目前只重建 Matroska extractor 以发出 DV RPU；新构造函数必须同时保留 `FLAG_EMIT_RAW_SUBTITLE_DATA`、DV BlockAdditional 开关和 P8.1/HDR10 状态，不能用一个裸 `MatroskaExtractor` 替换。
- `ProgressiveMediaPeriod` 已支持 prepared 后更新 SeekMap；临时 SeekMap 保持 `isSeekable=true`，因此不需要修改该类，也不会触发“不可 seek 强制归零”的路径。
- `PreCache` 使用同一 MediaItem 做后台预加载，但前台优先级更高；不把 E-SP2 逻辑放进 PreCache，避免尾部索引和预加载互相竞争。
- `PlayerManager`、`PlaybackActivity`、MPV/IJK 路径不需要改；首帧 UI 由 E-SP1 独立负责，E-SP2 只改变 Exo extractor 的读取时机。

### 9.6 验收与回滚

生产合并前必须记录同一资源/设备/网络的基线与候选各至少 3 次中位数：`tracks`、`first-frame`、`ready`、尾部 Range 等待、首次 seek 完成时间、重缓冲次数、A/V sync、掉帧和失败率。

最小输入矩阵：起始 0 秒远程大 MKV、同资源 140 秒断点、首次/连续/章节 seek、字幕、TrueHD/Atmos、DV7→P8.1、DV7→HDR10、Cues 缺失/损坏、Tracks-after-Clusters、递归 SeekHead、未知长度 HTTP、中断重试和本地 MKV。任何现有能力退化即回滚整个 E-SP2 commit/tag。

推荐回滚锚点：E-SP1 `c07e2b27eddbbee3240ed25fd6e2c8e5a64c5c7e`；E-SP2 另建独立 commit 和 recovery tag，绝不与 MPV/native 合并。

## 10. 当前决策与实施边界

- E-SP1 已完成并独立提交/tag。
- E-SP2 已由本次用户指令批准实施，采用 9.4 的 Media3 deferred Cues 方案；不采用代理/DataSource 尾部预取，也不全局设置 `FLAG_DISABLE_SEEK_FOR_CUES`。
- 实现先补入上游 `859f7b3b5388378698ff23a667d3e2db5ac41aed` 的等价修正，再加入 deferred Cues 状态机、测试、Media3 AAR 和 App factory 接入，作为独立可回滚单元。
- E-SP2 不修改 MPV/native、LoadControl、PreCache、DV7→P8.1/HDR10 转换、AV3A、TrueHD 或软解降载策略。

## Checkpoint 1：2026-08-22 E-SP2 初始评估

- 完成：完成 Exo 远程大 MKV 起播性能评估；确认尾部 Cues Range 是主要实耗时，并对照 Media3、mpv、FFmpeg、VLC。
- 前置：首帧 UI 分层已转入独立 [`E-SP1`](E-SP1-exo-first-frame-visible.md) 文档；本文不再保存其实现记录。
- 基线：`c07e2b27eddbbee3240ed25fd6e2c8e5a64c5c7e`。
- 工作区：只新增本评估文档；既有脏路径未触碰。
- 证据：WebHTV trace `p-2vl4f6-g`；Media3、mpv、FFmpeg、VLC 官方源码链接见上文。
- 验证：本检查点为评估记录，未修改 E-SP2 生产代码或依赖。
- 回滚：无生产变更；既有依赖锁和其他脏路径保持不变。
- 未决：E-SP2 采用 Media3 fork 延后 Cues 还是有界代理/DataSource 预取，必须完成研究与实验后再决定。
- 下一步：进入 E-SP2 深度研究，审阅本地 Media3 源码与实验边界。

## Checkpoint 3：2026-08-22 E-SP2 最终方案已冻结

- 完成：对照 Matroska 规范、Media3、FFmpeg、mpv、VLC、Media3 issue/PR 和 WebHTV 实际链路，确认尾部 Cues 同步读取是远程超大 MKV 从 0 秒起播的主要 extractor 延迟。
- Source identities：本地 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15`；Media3 release `2bc207851df311340767e913931ca7b28cab1794`；FFmpeg `eb0bfa852e7b9c524960300607ba2c4617060a9b`；mpv `49418246f30a9c24af31ac184aa24f39755db89a`；必要兼容修复 `859f7b3b5388378698ff23a667d3e2db5ac41aed`。
- 决策：采用 Media3 deferred Cues + 可更新 SeekMap；拒绝全局禁用 Cues、代理尾部预取和播放器重建方案。
- 工作区：分支 `fongmi-sync`，基线 HEAD `c07e2b27eddbbee3240ed25fd6e2c8e5a64c5c7e`；本阶段只修改本评估文档，预存脏路径继续保护。
- 验证：外部 revision 已记录；文档完成后运行 checkpoint 校验与 `git diff --check`。
- 回滚：评估文档单独提交/tag；生产实现以 E-SP1 commit 为前置回滚锚点。
- Rollback anchor: `c07e2b27eddbbee3240ed25fd6e2c8e5a64c5c7e` (`recovery/exo-sp1-first-frame-visible-20260822/20260822224344-c07e2b27eddb`); revert the future E-SP2 source/AAR/App unit together if any acceptance gate regresses.
- 未决风险：deferred seek 请求与 extractor 重入、递归 SeekHead、未知长度和网络重试必须由定向测试覆盖；设备性能验证需在候选 AAR 接入后进行。
- 下一步：完成评估提交/tag，启动独立 E-SP2 upstream guard，修改 Media3 patch/测试、生成对应 AAR，并接入 `DolbyVisionP81ExtractorsFactory`。

## Checkpoint 4：2026-08-23 E-SP2 实施中

- 目标与权限：用户已批准实施 E-SP2；当前原子单元是生成并接入带 deferred Cues 的 Media3 AAR，在完成定向编译/seek 验证后提交并立即创建 recovery tag。
- Lane/scope：`upstream`；范围限于 `third_party/patches/media3-deferred-cues.patch`、`scripts/build_media_deps.sh`、两个 Exo extractor factory、受补丁影响的 `third_party/maven/androidx/media3/**` 产物/校验文件以及本评估文档。
- 工作区：分支 `fongmi-sync`，HEAD `3aae091dbba7a2140f4c157f86aa42d901f01ff9`；活动 task id 为 `exo-sp2-defer-cues-implementation-20260822`。
- 已完成：新增显式 `FLAG_DEFER_SEEK_FOR_CUES`、64 MiB/已知长度门槛、临时可更新 SeekMap、首次非零 seek 时加载 Cues 的状态机，并等价纳入 Media3 `859f7b3b5388378698ff23a667d3e2db5ac41aed` 的 Tracks-after-Clusters 顺序修复；补丁顺序已固定。App 的 HTTP/HTTPS URI 收窄将在候选 AAR 接入单元中完成。
- 已有验证：完整补丁链上的 `git apply --check` 和仓库 `git diff --check` 已通过；尚不能据此声明运行时 seek 或性能正确。
- 构建环境：使用 JDK 21；后续构建显式设置 `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`。使用隔离临时 checkout，不清理或覆盖预存 `third_party/sources/media/`。
- 保护路径：`.gitignore`、`third_party/fongmi-repositories-lock.json`、`.codex/`、`AGENTS.md`、`docs/agents-md-effective-constraints-review-2026-08-21.md`、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`、`third_party/sources/media/` 均保持在本提交之外。
- 回滚锚点：`3aae091dbba7a2140f4c157f86aa42d901f01ff9`；若 extractor、seek、DV 或产物验证失败，整体撤销 E-SP2 source/AAR/App 单元，不改变 E-SP1。
- 未决风险：首次/连续/反向 seek、Cues 缺失/损坏、未知长度、IO 重试、递归 SeekHead、Tracks-after-Clusters，以及 DV7→P8.1/HDR10、TrueHD/字幕邻接能力仍需由定向测试和代表性播放验证。
- 下一步：在隔离 Media3 checkout 中用 JDK 21 应用完整补丁链，先运行 extractor 编译和定向 Matroska 测试。
- 进度记录：E-SP2 implementation continues: final deferred-Cues patch and factory URI scoping remain; no source commit yet.
- 唯一下一步：Replace patch, narrow extractor activation to remote HTTP/HTTPS Matroska, publish only lib-extractor AAR/sources, run targeted tests, then finish guard and tag immediately.

## Checkpoint 5：2026-08-23 E-SP2 Media3 补丁单元完成

- 完成：最终 438 行 `media3-deferred-cues.patch` 已落库，包含 deferred Cues 状态机、Tracks-after-Clusters 修复、Format 去重及 3 项 Matroska 定向测试；`build_media_deps.sh` 已固定四个 Media3 补丁的依赖顺序。
- Source identities：WebHTV Media3 基线 `e3e922d5c01bc0b564849940fe589daf37360d15`；等价吸收上游 `859f7b3b5388378698ff23a667d3e2db5ac41aed`；FFmpeg 参考 `eb0bfa852e7b9c524960300607ba2c4617060a9b`；mpv 参考 `49418246f30a9c24af31ac184aa24f39755db89a`。
- 验证：隔离 checkout 的 `:lib-extractor:compileDebugJavaWithJavac` 和 `MatroskaExtractorNonParameterizedTest` 已通过；同一源码树的 `:lib-extractor:publishReleasePublicationToMavenRepository` 于 2026-08-23 `BUILD SUCCESSFUL`。
- 工作区：本单元只提交补丁、构建顺序和本评估文档；不提交 App 接入或 Maven 二进制，因此当前运行时行为不变且中间提交仍可编译。
- 回滚：回滚本单元只移除尚未启用的补丁/构建配方，不影响 E-SP1 或现有播放器行为。
- 下一步：启动独立 artifact/App 护栏，将候选 extractor AAR、sources、校验文件、URI 范围接入、锁和最终记录作为一个运行时单元提交并立即 tag。

## Checkpoint 6：2026-08-23 E-SP2 候选 artifact/App 单元完成

- 目标：把已验证的 Media3 extractor 补丁接入 WebHTV，但只让 HTTP/HTTPS Matroska 进入 deferred Cues；本地文件、MP4、TS、HLS、DASH、直播和未知 scheme 保持原读取策略。
- App 适配：`DolbyVisionP81ExtractorsFactory.createExtractors(Uri, ...)` 按 URI scheme 收窄；远程 Matroska 在 DV7 设置关闭时仍启用 deferred Cues，但不发出 DV BlockAdditional RPU；DV7 设置开启时同时保留原始字幕和 RPU。无 URI 的 `createExtractors()` 不启用 deferred。
- Artifact：`androidx.media3:media3-extractor:1.11.0-alpha01-fongmi`；AAR SHA-256 `ec3ac41088e496bdfe63925ca0751234e0178a9d92e8deedf8734dfe36fab8fe`；sources JAR SHA-256 `a9a813622f10b4735f71ba3c8e535981adca4941bd0f6d832efe31e80b033295`。只替换 AAR、sources JAR 及各自四类 checksum，未改 POM/module 或其他 Media3 模块。
- Provenance：源码 `e3e922d5c01bc0b564849940fe589daf37360d15`；等价上游修复 `859f7b3b5388378698ff23a667d3e2db5ac41aed`；deferred patch SHA-256 `8bfcf98dadfd70e56ebec4c5fd49701ddc8ed66b02707a7f733b3aec1ed4b2c7`；构建命令为 `:lib-extractor:publishReleasePublicationToMavenRepository`，JDK `21.0.12.1`、compileSdk `36`。
- 已验证：AAR 内含 `MatroskaExtractor$DeferredSeekMap.class`；发布任务 `BUILD SUCCESSFUL`；AAR/sources/module 与各自 sidecar checksum 一致，POM 未变化；Mobile/Leanback Arm64 Java 编译与 `DolbyVisionP81ExtractorsFactoryTest` 均 `BUILD SUCCESSFUL`。尚未声称设备起播/seek 性能通过。
- 保护：不修改 MPV/native、nextlib FFmpeg、DV7→P8.1/HDR10 转换、AV3A、TrueHD、PreCache、LoadControl 或网络超时策略。
- 提交链：补丁单元 `fb2a9ab839958a711a73ee34232d5baa082bddc7`（tag `recovery/exo-sp2-defer-cues-implementation-20260822/20260823101713-fb2a9ab83995`）；完整 Maven/lock 单元 `71514fdb101db56e836902405700f39c891428e5`（tag `recovery/exo-sp2-deferred-cues-artifact-20260823/20260823103155-71514fdb101d`）；最终 App URI gate 单元 `8c6567adff1b5268b1aba7bb4c12b2faa1a6477e`（tag `recovery/exo-sp2-deferred-cues-app-gate-20260823/20260823103404-8c6567adff1b`）。
- 回滚：若只需关闭新行为，回滚最终 App URI gate 即可；若候选 artifact 有兼容问题，再一并回滚 `71514fdb101db56e836902405700f39c891428e5`；若要完全撤销 E-SP2，再回滚 `fb2a9ab839958a711a73ee34232d5baa082bddc7`，E-SP1 不受影响。
- 未决：当前无 ADB 设备；实机首帧、首次 seek、连续 seek、DV7→P8.1、DV7→HDR10、字幕/TrueHD 及网络失败率需在候选包阶段验收。该限制不影响源码、AAR 与 App 编译结论，但禁止宣称性能提升已在设备上验证。
- 下一步：用同一远程大 MKV 做基线/候选至少 3 次中位数及首次/连续 seek、DV fallback 回归；该实机证据完成前不宣称性能验收完成。

## Checkpoint 7：2026-08-28 链式 SeekHead 实机失败与修正设计

### 现场证据与根因

- 设备：vivo V2453A，Android 15；App `com.fongmi.android.tv`；Exo 播放约 4.20 GB 远程 MKV。
- 用户连续三次拖动进度条，系统日志逐次输出 `Controller isn't allowed to call command= 5`；Media3 `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` 的值为 5，说明请求在 `MediaController` 层被当前不可 seek 时间线拒绝，未到达 Exo seek 状态机。
- 通过播放链原有认证参数和用户指定代理执行最小 Range 检查，没有记录或输出 URL、Cookie、token：文件长度为 `4508956833` 字节，Segment content 起点为绝对偏移 `52`。
- 文件头绝对偏移 `52` 的首个 `SeekHead` 不含直接 `Cues` 条目，只含 `SeekID=SeekHead`，其相对位置为 `4508956696`、绝对位置为 `4508956748`。
- 文件尾绝对偏移 `4508956748` 的第二个 `SeekHead` 含 `SeekID=Cues`，其相对位置为 `4508747919`、绝对位置为 `4508747971`；直接读取该位置的前四字节为 `1c53bb6b`，确认目标确实是 `Cues`。
- 当前 `media3-deferred-cues.patch` 只在 `SeekID` 为 `Cues` 或 `Tracks` 时保存位置，忽略 `SeekID=SeekHead`。因此到首个 Cluster 时 `cuesContentPosition == C.INDEX_UNSET`，`maybePrepareSeekMap()` 发布 `SeekMap.Unseekable`，MediaSession 随后移除当前媒体内 seek 命令。
- 结论：这是 E-SP2 未覆盖链式/递归 `SeekHead` 的 correctness 缺口，不是 E-SP3 seek 恢复门槛或统计隔离引入的回归。

### 上游 PR #2268 完整提交处置

上游 PR <https://github.com/androidx/media/pull/2268> 截至 2026-08-28 仍为 closed、未合并。维护者验证了三个 `SeekHead`、未知长度和每个读位置注入 IOException 的组合，指出早期实现会丢样本或在重试后得到不一致的 seekability。以下提交全部纳入本修正的来源账本：

| Full commit | 内容 | E-SP2 处置 |
| --- | --- | --- |
| `ffca82f981e975d302c6480ba9cbce6e05260e74` | 初版递归 SeekHead + 样片/测试 | `partial`：采用识别 `ID_SEEK_HEAD` 和二阶段跳转思想，不采用起播阶段立即尾跳 |
| `3a79ac28707f138adb8a2f9c09c3b59125bdf261` | 初版在 PR 分支上的重落基 | `superseded`：由同 PR 后续实现覆盖 |
| `04c8b3da1b1538ab10caf926e75bae4beeeb9226` | 增加 extractor dump | `test-only`：测试意图保留，本地使用更小的定向构造输入 |
| `f5a6ed0f3f5e3ad827e886de088bd93ecbc2178e` | 三 SeekHead 实验样片 | `evidence-only`：证明单位置字段不足，需要 pending/visited 集合 |
| `a77bd285dcfe3ebd56eb5f487f55638625005e35` | 任意数量 SeekHead 的 pending/visited 状态 | `partial`：采用有界 pending/visited 设计，访问时机改为首次非零 seek |
| `73a79cb0c496b55d7a1eb14d30785cd537be7954` | PR 分支 merge | `maintenance-only`：无独立行为移植 |
| `3525b89ef2bdb549bb9744cc7c51af3a56a2cb58` | 补 HashSet import | `covered`：随本地状态集合实现自然覆盖 |
| `705b31db25dd08708cf578e39b60e3b9e14574e0` | 三 SeekHead dump 更新 | `test-only`：不复制 dump，覆盖相同行为断言 |
| `5ee7bd2fa4f2416d3cb75c437444c20585d1a468` | 补漏 dump 更新 | `test-only`：不影响生产逻辑 |
| `46f72f3e8ead70c3b6b375d1b7f41e5b15d82329` | IO 重试状态清理及向前位置限制 | `partial`：采用 `seek(0,0)` 重置和位置/边界检查，不在 Segment 回调无条件清空 |
| `6d8ce0f41e8c688ef4fff1b1ae7403e6deb0871d` | 最终 dump 同步 | `test-only`：不影响生产逻辑 |

### 方案比较与批准设计

| 方案 | correctness | 起播性能 | 风险 | 决定 |
| --- | --- | --- | --- | --- |
| 不变更 | 链式 SeekHead 永久不可 seek | 保持当前首帧 | 用户核心功能失败 | 拒绝 |
| 强制 MediaSession 暴露 seek 命令 | UI 请求可下发，但底层没有有效 SeekMap | 无直接影响 | 可能回到 0、错误定位或循环读取 | 拒绝 |
| 原样采用 PR #2268 | 能查找多个 SeekHead | 首个 Cluster 前访问尾部 SeekHead/Cues，重新把尾部 RTT 放回起播路径 | PR 未合并，且重试/未知长度测试曾失败 | 拒绝原样移植 |
| WebHTV 惰性链式 SeekHead | 起播先发布临时可 seek map，首次非零 seek 才解析 SeekHead 链和 Cues | 保留 E-SP2 起播收益 | 需要有界状态、重试清理和定向测试 | 批准实施 |

最终设计：

1. 解析 `SeekID=SeekHead` 时记录绝对位置；只接受位于当前 Segment 内、严格向前、尚未访问的条目，并限制最多 10 个待访问 SeekHead，避免循环或恶意唯一链造成无界 I/O。
2. deferred 模式下，只要已知直接 Cues 或至少一个待访问 SeekHead，就发布 `isSeekable=true` 的临时 SeekMap。非零 seek 的临时 byte position 优先指向 Cues，否则指向待访问 SeekHead；起播阶段不主动执行尾部跳转。
3. 首次非零 seek 到达 SeekHead 后，若解析出 Cues 则继续跳到 Cues；否则访问下一个未访问 SeekHead。完整 Cues map 发布后，用原目标时间计算最终 Cluster 位置并继续正常 seek。
4. `seek(0, 0)` 和新的 Segment 重启清理 pending/visited/中间跳转状态；相同位置的 unchanged-position IOException 重试保留状态。链耗尽、越界或超过上限时回退 `SeekMap.Unseekable`，不得死循环、无限分配或强制暴露错误命令。
5. 保留直接 Cues、Tracks-after-Clusters、Format 去重、DV BlockAdditional、字幕、TrueHD/DTS 分析和其他 extractor 语义；不修改 App 工厂、LoadControl、PreCache、E-SP3、MPV/native 或网络超时。

### 验收、可观测性与回滚

- 定向测试：直接 Cues 保持原行为；单层链式 SeekHead 在 prepare 阶段不读取尾部、临时 map 可 seek；首次非零 seek 按 `SeekHead -> Cues -> Cluster` 顺序完成；循环/重复/超过上限不死循环；`seek(0,0)` 后可重新解析；IO 短读/重试不丢状态。
- 构建：锁定 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15` 应用完整补丁链，运行 Matroska 定向测试并发布 extractor AAR/sources；更新补丁、artifact 和 lock SHA-256；Mobile/Leanback arm64 Java 编译通过。
- 实机：同一 vivo、同一 MKV 连续 seek 至少三次；不得再出现 `command=5` 拒绝；第一次 seek 必须出现尾部 SeekHead/Cues Range，播放位置到达目标且后续连续播放；起播阶段不得提前读取尾部 SeekHead。
- 日志：不另做大范围诊断 APK；修复中只增加一条 debug 级分支日志（disabled/direct-cues/nested-seekhead/unseekable）或等价测试可观测点，不记录 URL、Cookie、token。
- 回滚：作为独立 extractor source/artifact/lock 单元回滚到当前 HEAD `9fcab83f9084446566240a8e8f5233d87d0274cc`；App gate 和 E-SP3 不需回滚。
- 用户批准：2026-08-28 用户明确要求忽略 C2、集中解决该 bug；C2 文档保持受保护且不进入本单元。
- 下一动作：修改 `media3-deferred-cues.patch` 和定向测试，重建 extractor 产物后执行实机验收。

## Checkpoint 8：2026-08-29 链式 SeekHead 修正完成

- 完成：`MatroskaExtractor` 在 deferred 模式记录有界 pending/visited `SeekHead`，起播只发布临时可 seek map，首次非零 seek 才按 `SeekHead -> Cues -> Cluster` 解析；循环、重复、越界或链耗尽安全回退，`seek(0,0)` 可重置重试状态。
- 补丁链：扩展后的 deferred patch 会改变后续零上下文补丁的行号，因此 `scripts/build_media_deps.sh` 固定为 `media3-upstream-playback-fixes-2026-08.patch -> media3-exo-hdr-parser-safety.patch -> media3-deferred-cues.patch`。从锁定源码重新应用全部 Media3 补丁后，最终 Java 源码与已测试源码 SHA-256 逐字节一致。
- 定向测试：锁定 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15` 上运行 `:lib-extractor:testDebugUnitTest --tests androidx.media3.extractor.mkv.MatroskaExtractorNonParameterizedTest`，`BUILD SUCCESSFUL`；覆盖直接 Cues、Tracks-after-Clusters、首次非零 seek、链式 SeekHead 成功和自循环回退。
- Artifact：deferred patch SHA-256 `ebbfbe0bcd1f002780b2db300bdcf42d9c3309c0738b43212ffa224eedcba8dd`；extractor AAR SHA-256 `59a090bad8efc32552cf5a7ec1d2fb66444168bfbc97bd45e50764527f80c768`；sources JAR SHA-256 `9314a303f402e903dd075db5e9babf9be6474da539a4b76bb1ed765b95b6ef4e`。AAR、sources、module 及各自 MD5/SHA-1/SHA-256/SHA-512 sidecar 一致，module 内嵌摘要一致，POM 未变化。
- 发布与编译：`:lib-extractor:publishReleasePublicationToMavenRepository`、`:app:compileMobileArm64_v8aDebugJavaWithJavac` 和 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均 `BUILD SUCCESSFUL`；Gradle transform 中实际消费的 `MatroskaExtractor` 已包含 `pendingSeekHeadPositions`。
- APK：Mobile arm64 debug APK SHA-256 `1563a1208b4721140fd88abf9548732e752ae700c00ad3a6711f010385b2e4b1`；vivo 流式安装被 OEM 拒绝，改用 `adb install --no-streaming -r -d -g` 后成功，不属于 APK 或代码失败。
- 实机验收：vivo V2453A、Android 15、EXO、同一 4.20 GB 远程 MKV；用户完成连续拖动后明确确认“可以了”。本次日志未出现 `Controller isn't allowed to call command=5` 或 App fatal exception。
- 可观测性决定：不增加额外运行时调试日志。定向测试已覆盖分支状态，现有 MediaSession 拒绝日志、播放器位置和网络 Range 行为足以判定本缺陷；避免在 extractor 热路径增加长期噪声或泄露请求信息的风险。
- 回滚：整体回滚本单元即可恢复至 `9fcab83f9084446566240a8e8f5233d87d0274cc` 的 extractor patch/artifact/lock/build-order 状态；App URI gate 和 E-SP3 无需回滚。
- 下一动作：运行最终一致性校验，使用 task guard 原子提交并立即创建 recovery tag。
