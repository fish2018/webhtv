# E-SP5：硬解回退到 FFmpeg 软解时补上降负载

- 任务 ID：`E-SP5`
- 类别：Exo 性能专项
- 唯一文档：`docs/E-SP5-exo-ffmpeg-fallback-load-shedding.md`
- 状态：已实施；待实机验收。
- 下一动作：用户在受影响设备上确认卡顿是否缓解，并回报诊断面板「视频」行的 `decoder` 字段。

## 用户观察到的失败

「画面是有但是一步三卡，没掉帧但画面无法正常连续播放」。诊断面板读数（模拟器 `samsung SM-N9700` / `x86_64` / `Android 9 SDK 28`）：

```
视频   H.265 / HEVC / 3840x2160 / 25fps / 10.5Mbps / hvc1.1.6.L150.90
       decoder ffmpegLavc63.3.100-hevc          ← FFmpeg 软解
音频   E-AC3 2ch 48kHz 256Kbps / dec ffmpegLavc63.3.100-eac3
配置   EXO / 硬解 / Surface / 隧道关 / 性能自动   ← 设置是硬解
播放   就绪(READY) / 50.1s / 12% / 重缓冲 0 次 / 掉帧 0
起播   request 11ms  prepare 17ms(+6)  tracks 2262ms(+2245)
       first-frame 3452ms(+1190)  ready 3500ms(+48)   最慢 tracks:2245ms
CPU    App 当前 9% / 10秒 25% / 峰值 88%
```

`掉帧 0` 与「一步三卡」并不矛盾：帧不是被丢弃，而是解码来不及、渲染迟到，因此掉帧计数器不增长。

## 根因

`ExoUtil.buildPlaybackRenderersFactory()` 原先这样计算调优开关：

```java
decode == PlayerEngine.SOFT && PlaybackPerformanceSetting.isSoftVideoTuneEnabled()
```

即**只有用户显式选择软解档时才启用降负载**。但硬解档下：

- `getVideoRenderMode(HARD)` 返回 `EXTENSION_RENDERER_MODE_OFF`
- `getFfmpegVideoRenderMode(OFF)` 又把它转成 `EXTENSION_RENDERER_MODE_ON`

因此 FFmpeg 视频渲染器**仍会作为兜底装入**（`isFfmpegVideoFallbackOnly()` 为真，只接 MediaCodec 无法处理的编码）。当设备确实缺少该编码的硬解时，解码就落到这个兜底渲染器上，而它走的是 `!softVideoTune` 分支：

```java
if (!softVideoTune) return new CompatFfmpegVideoRenderer(
        allowedVideoJoiningTimeMs, eventHandler, eventListener,
        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, fallbackOnly, platformDecoderSelector);
// 带调优的那条另外传入：
// availableProcessors(), 4, 4,
// FFMPEG_SKIP_FRAME_NONREF, FFMPEG_SKIP_LOOP_FILTER_ALL, FFMPEG_LOWRES_HALF
```

结果是**不给线程数、不跳环路滤波、不跳非参考帧、不降 lowres** —— 恰好在最需要降负载的场合（内容重到硬件都不接）反而完全不降。用户若主动选软解档，反而能拿到多线程与跳滤波，比硬解档回退时流畅。

该门控为既有代码（`6ded321295 Add Exo FFmpeg soft decode load shedding`），**非本次上游合并引入**；合并对 `ExoUtil` 的改动仅限 DV 相关部分（`asHdr10` 与 `resetAttempt`）。但合并把 nextlib 升到 FFmpeg 9.0.1（`ffmpegLavc63.3.100` 即 libavcodec 63 = FFmpeg 9.x），同一条无调优路径的耗时随之变化，使既有缺陷在这一版显形。

## 实现

拆分两个决策，互不影响：

- `shouldTuneFfmpegVideo(tuneEnabled, ffmpegVideoReachable)` —— 纯函数，**不以解码档为门控**，仅要求用户开启降负载且该 profile 下 FFmpeg 视频渲染器可达。
- `isFfmpegVideoReachable(videoRenderMode)` —— 以 `getFfmpegVideoRenderMode()` 是否为 `OFF` 判定可达性。
- 新增 `ffmpegVideoTune` 参数贯穿 `buildRenderersFactory` 与 `FfmpegRenderersFactory`，只供视频渲染器使用；交给 `CompatFfmpegAudioRenderer` 的 `softVideoTune` 原样保留，音频行为不变。
- `buildRenderersFactory()`（无参，探测/预载路径）显式传 `false`，保持既有行为。

兜底语义不变：`isFfmpegVideoFallbackOnly()` 仍为真，调优不会让该渲染器去抢 MediaCodec 能处理的轨（`91a667637e` 修过的抢轨问题不回归）。

## 边界

只改「硬解档下 FFmpeg 视频兜底渲染器是否启用降负载」。不修改：解码器选路与硬件筛选、音频渲染器、隧道、DV7→P8.1 与 HDR10 回退、缓冲阈值、seek 精度、MPV。

用户已明确「不用管画质问题」，且降负载受既有开关「软解降负载」（默认开）控制，可关闭。

## 验证

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "...ExoFfmpegFallbackTuneTest"`：`tests="6" failures="0" errors="0"`。
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL`。
- `git diff --check`：通过。

验证边界：单测覆盖调优判定与兜底语义不变；编译证明参数贯穿正确。**不等同于**实机卡顿已缓解——软解 4K60/4K25 HEVC 即使降负载后仍可能跑不满帧率，尤其在无硬解的 x86_64 模拟器上。

## 第二轮：调优参数本身的两处问题

第一轮生效后用户回报「好很多了，但时不时还是会卡画面」。同源对比读数：

| 指标 | 第一轮前 | 第一轮后 |
| --- | --- | --- |
| CPU 10秒 / 峰值 | 25% / 88% | 50% / **167%** |
| 网络 | 524 KB/s | 2.1 MB/s |
| `tracks` | 2262ms | 1863ms |
| `first-frame` 增量 | +1190ms | **+4305ms** |
| 最慢阶段 | `tracks:2245ms` | `first-frame:4305ms` |

峰值 88% → 167% 证明线程数确实生效。但两处参数本身有问题：

### 1. 输出缓冲固定为 4，小于线程数

`FfmpegVideoDecoder` 直接用它开池：`super(new DecoderInputBuffer[numInputBuffers], new VideoDecoderOutputBuffer[numOutputBuffers])`。而 FFmpeg 帧级并行需要约 `threads + 1` 帧同时在飞。池子小于线程数会把解码器压成周期性停顿 —— 与「CPU 峰值仅 167%（4 核可用 400%）却仍卡」完全吻合：瓶颈是流水线节流，不是算力。

改为 `ffmpegDecodeBuffers(threads) = clamp(threads + 2, 4, 12)`，下限保持旧的固定值 4 以免单核回退，上限 12 限制内存。

### 2. `skipFrame = AVDISCARD_NONREF` 在丢帧

非参考帧根本不被解码，渲染器从未见过它们，因此运动连续性受损而掉帧计数仍为 0 —— 这正是用户「掉帧 0 却看得见卡」的来源。既然瓶颈是缓冲节流而非 CPU，这个牺牲不必要。

改为 `AVDISCARD_DEFAULT`（保留每一帧）；`skipLoopFilter = AVDISCARD_ALL` 维持不变，它只牺牲画质、不丢帧，符合用户「不用管画质」的取向。

`lowres = 1` 保留原样：HEVC 原生忽略它，对支持的编码仍有效。

实现期间单测抓到我自己引入的整数溢出（`Integer.MAX_VALUE + 2` 回绕为负、结果坍回下限 4），已在加法前先夹取修正。

## 尚未解决的部分

- **起播慢的主因未定。** 本次读数中 `tracks` 阶段占 2245ms（总 3500ms 的 64%）。但该片源 codec 为 `hvc1`（MP4 系 sample entry），而延后 Cues 只作用于 Matroska，故此例与 `E-SP2` 无关，更可能是读 moov 加网络（实测 524 KB/s ≈ 4.2Mbps，而片源 10.5Mbps）。需要 MKV 片源的读数才能判定延后 Cues。
- **诊断面板存在误导**：「配置」行显示「硬解」，而「视频」行的 `decoder` 已经是 `ffmpegLavc`，两者不一致时前者未提示实际走了软解兜底。
- `DolbyVisionTrackOutput` 对每个视频轨无条件分配约 2MB（1MB direct ByteBuffer + 1MB byte[] + 16KB），仅在 P8.1 转换时使用，应改懒分配。属浪费但为毫秒级，未在本任务处理。

## 回滚

恢复本任务的原子提交即可。不涉及依赖锁、AAR、native 二进制或 patch。
