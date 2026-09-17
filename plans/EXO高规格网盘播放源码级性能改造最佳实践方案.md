# EXO 高规格网盘播放源码级性能改造最佳实践方案

日期：2026-07-21<br>
代码基线：`main-2` / `44af7eb598` / `playback-optimization-item-32`<br>
Media3：`1.11.0-alpha01-fongmi`<br>
目标场景：4K/5K、HEVC Main10、HDR10/HDR10+/Dolby Vision、大体积 MKV/REMUX、70～200Mbps 峰值码率、网盘 Range、外部回环代理、Android TV/电视盒子

## 一、结论先行

当前工程已经完成了 EXO 高码率播放的第一层优化：`SurfaceView`、MediaCodec 硬解、异步队列、动态调度、播放优先级、Heap 安全缓冲、磁盘缓存和预载避让均已具备。继续只调 `LoadControl`、缓冲秒数、线程数，收益会快速进入平台期。

下一阶段真正可能产生质变的源码级改造只有以下几类：

1. **把现有 MediaCodec Surface 输出升级为可自动启用、可验证、可回退的 tunneled sideband 路径。** 这是 Android 标准能力中最接近电视硬件旁路直出的方案。
2. **实现独立于 EXO 默认策略的电视输出模式管理器。** 同时处理内容帧率、显示刷新率、物理分辨率、HDR 能力、黑屏切换和播放结束恢复，而不只使用“仅无缝”的 `Surface.setFrameRate()`。
3. **承认 Go 二进制代理是不可绕过的外部边界。** App 只优化 EXO 到 `127.0.0.1` 的读取、缓存、预载、断流恢复和观测，不复制或接管网盘鉴权、签名和 CDN 连接。
4. **为大 MKV/REMUX 提供平台 `MediaParser`、Media3 Java Matroska 和 native libavformat 三种 extractor 后端的可观测 A/B。** 不预设谁一定更快，以设备和文件数据决定。
5. **建立真正的 codec 策略层。** 按内容 profile/level/bit depth、PerformancePoint、tunneling、显示 HDR 能力和设备黑名单选择 decoder，而不是只做“硬件 decoder 优先”。
6. **修正 Dolby Vision fallback。** 当前把 DV Profile 5/7 仅改 MIME 后送入 HEVC decoder 的实现不安全；Profile 5 必须立即禁止这种 fallback，Profile 7 只能在确认存在可用 base layer 并正确处理 EL/RPU 后启用。

推荐优先级：

| 优先级 | 改造 | 预期性质 | 结论 |
|---|---|---|---|
| P0 | 修正 DV Profile 5/7 fallback | 正确性、稳定性 | 立即做 |
| P0 | EXO 输出模式管理器 | 流畅度、电视直出体验 | 立即设计实施 |
| P0 | tunneling 自动探测/黑名单/回退 | 解码到显示链路质变 | 先做受控灰度 |
| P1 | 代理边界观测与断流恢复 | 区分本地交付问题和代理内部问题 | 仅 App 可实施 |
| P1 | extractor backend A/B | 大 MKV 起播、Seek、CPU | 可独立实施 |
| P1 | codec 选择与运行档案 | 稳定解码、设备适配 | 与 tunneling 同步实施 |
| P2 | native demux + EXO SampleQueue bridge | 解析性能 | 数据证明 Java demux 是瓶颈后实施 |
| P3 | native demux 直喂 MediaCodec | 最大改造、接近自研 player | 只用于少量确证机型/格式 |

### 1.1 当前实施状态

- ✅ `item-33`：Dolby Vision Profile 5/7 不安全 fallback 修正。
- ✅ `item-34`：播放能力报告拆分，解码、显示、来源和网络不再混用单一档位。
- ✅ `item-35`：轨道码率、网络吞吐和设备 codec 码率能力分离。
- ✅ `item-36`～`item-37`：电视输出模式策略、Display.Mode 应用与退出恢复。
- ✅ `item-38`～`item-43`：tunneling 资格判断、失败回退、codec 失败记忆、首帧/播放中 watchdog 和实际状态诊断。
- ✅ `item-44`：编码、帧率、码率和 decoder 参数面板增强；远程托管禁用时停止执行与日志。
- ✅ `item-45`：HTTP 固定长度响应 EOF 后按准确字节位置发起 Range 续读。
- ✅ `item-46`：EOF 恢复从单次请求累计次数改为连续失败熔断。
- ✅ `item-47`：参数面板在视频轨码率缺失时使用文件/读取估算，并直接显示帧率和码率值。
- ✅ `item-48`：EXO 帧处理偏移、滞后批次和可恢复 codec 错误聚合诊断。
- ✅ `item-49`：自动模式修正视频码率估算，并在当前会话内按重缓冲表现逐级提升恢复阈值（最高 15 秒）。
- ✅ `item-50`：外部回环代理安全缓冲后的低优先级连续预载，不再因 EXO 长期 loading 状态被永久暂停。
- ✅ `item-51`：外部回环自动预载在 degraded 带宽档仍保持 40 秒连续前向范围，避免 10 秒预载块被快速耗尽。
- ✅ `item-52`：外部回环预载首次连接/下载失败后会话级熔断，停止所有失败重试并保留主播放连接独占 Go 代理；不改变 `127.0.0.1` 代理边界。
- ✅ `item-53`：EXO 自动档改为时间优先加载，避免高码率视频被约 161MB 字节目标硬性截断在约 15 秒缓冲；仍受 60 秒时间上限和设备堆内存预算约束。
- ✅ `item-54`：增加 EXO 帧释放时序基础诊断，聚合释放提前/滞后、滞后帧数、最大滞后、释放抖动和渲染回调间隔；明确 Android App 层不虚构 HWC 合成计数或 MediaCodec 队列占用。
- ✅ `item-55`：在重缓冲、Seek 和视频轨切换时重置帧释放连续性锚点，避免停顿时长污染释放抖动和回调间隔，同时保留累计诊断数据。
- ✅ item-46/47 APK 已完成基础真机播放验收；item-46 长时间 EOF 专项仍需单独监听。
- ⬜ item-48/54 新 APK 真机验收（帧调度与释放时序诊断行）。
- ⬜ MediaParser/Java Matroska/native extractor A/B。
- ⬜ FrameTimeline、HWC composition 和 codec queue 深度诊断（需 Perfetto/厂商工具证据）。
- ⬜ Cues/SeekMap sidecar、字幕和附件懒加载。
- ⬜ thermal-aware 预载、索引和线程策略。
- ⬜ Media3/vendor codec 深水区专项。

## 二、先定义“零拷贝”和“电视直出”

### 2.1 播放链路必须分段描述

```text
远端网盘/CDN
  ↓ 压缩字节
网盘 SDK / 外部代理 / HTTP 客户端
  ↓
DataSource
  ↓
Extractor / SampleQueue
  ↓ 压缩 access unit
MediaCodec
  ↓ 解码图形 buffer handle
Surface / BufferQueue
  ↓
SurfaceFlinger / HWC / GPU
  ↓
电视面板或 HDMI 输出
```

“零拷贝”不能作为整条链路的笼统承诺：

- Android 的 `BufferQueue` 不复制图形 buffer 内容，而是传递 buffer handle；MediaCodec 输出到 `SurfaceView` 已经具备解码图像阶段的低拷贝基础。
- `SurfaceView` 提供独立 Surface layer，SurfaceFlinger/HWC 可以直接合成该层；相比 `TextureView` 先进入 App UI/GPU texture，再合成到屏幕，通常少一层离屏合成工作。
- **tunneling** 更进一步：AOSP 定义为压缩视频经硬件 decoder 直接进入 display sideband layer，绕开 App 和 Android framework 对每个已解码视频帧的调度。
- 网络压缩数据仍会经过 socket/SDK buffer、Java `DataSource.read(byte[])`、Extractor 和 SampleQueue。即使视频图像是低拷贝，也不代表压缩数据路径零拷贝。
- `AHardwareBuffer` 适合图形 buffer，不是解决 EXO 压缩字节 DataSource 拷贝的通用接口；`SharedMemory` 或 Unix domain socket 最多减少代理边界成本，标准 EXO `DataSource` 最终仍要读入 Java buffer。

因此本方案使用三个准确术语：

| 术语 | 含义 |
|---|---|
| MediaCodec Surface 低拷贝 | decoder 直接输出到 Surface/BufferQueue，不把解码 YUV 拉回 App CPU |
| tunneled sideband | decoder 通过 sideband handle 交给 HWC，绕过 App/框架逐帧显示调度 |
| Go 代理高效 Range 链路 | 保留代理鉴权和 CDN 能力，只优化本地传输、Range、缓存、取消和 telemetry |

### 2.2 App 无法保证的部分

- App 不能保证任意电视都把视频层放到硬件 overlay；最终由 SurfaceFlinger、HWC HAL、图层数量、色彩空间、缩放、透明 UI、厂商驱动决定。
- App 不能把不支持 5K/HEVC Main10/DV Profile 7 FEL 的 SoC 通过源码优化成支持。
- `SurfaceView` 不等于 HDMI bypass；`preferredDisplayModeId` 也不等于播放器能直接控制 HDMI 色深、chroma subsampling 或厂商专有画质通道。
- tunneling 是设备相关能力，Media3 自身也明确警告存在大量机型问题，必须有黑名单和非 tunneling 回退。

## 三、外部研究形成的关键证据

### 3.1 Android/AOSP 官方证据

| 来源 | 支持的结论 |
|---|---|
| [AOSP Multimedia tunneling](https://source.android.com/docs/devices/tv/multimedia-tunneling) | tunneling 让压缩视频经硬件 decoder 直接到 display；减少 App/framework 参与；通过 sideband handle 关联 HWC layer；要求 `SurfaceView`；`KEY_OPERATING_RATE` 应覆盖内容帧率 × 播放速度 |
| [AOSP BufferQueue and Gralloc](https://source.android.com/docs/core/graphics/arch-bq-gralloc) | BufferQueue 不复制 buffer 内容，而是传递 handle；硬件专用格式和 usage flag 可改善性能；受保护内容依赖 overlay 路径 |
| [AOSP SurfaceView and GLSurfaceView](https://source.android.com/docs/core/graphics/arch-sv-glsv) | SurfaceView 使用独立 Surface，SurfaceFlinger 可直接合成，避免先合成到 offscreen surface 再显示的额外工作 |
| [AOSP Hardware Composer](https://source.android.com/docs/core/graphics/implement-hwc) | HWC 从 GPU 接手图层合成；是否走 HWC、MIXED 或 GLES 由 HWC/SurfaceFlinger 决定，App 不能直接保证 |
| [Android frame rate guide](https://developer.android.com/media/optimize/performance/frame-rate) | 长电影推荐允许非无缝切换；`setFrameRate()` 不能保证切换；需要改变分辨率或强制 heavy mode switch 时应考虑 `preferredDisplayModeId` |
| [Android HDR playback](https://developer.android.com/media/grow/hdr-playback) | HDR 需要 decoder 与 display 双重支持；`TextureView` 的 HDR 支持受限，官方建议使用 `SurfaceView` |
| [MediaCodec codec capabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback) | decoder 必须显式声明 `FEATURE_TunneledPlayback` |
| [VideoCapabilities PerformancePoint](https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities#getSupportedPerformancePoints()) | Android 10+ 可用 PerformancePoint 判断 codec 对分辨率/帧率组合的性能承诺，而不仅是尺寸上限 |

### 3.2 Media3 源码证据

| 来源 | 支持的结论 |
|---|---|
| [MediaCodecVideoRenderer](https://github.com/androidx/media/blob/main/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java) | 默认已设置 realtime `KEY_PRIORITY`、动态 `KEY_OPERATING_RATE`、Surface 输出、tunneling 配置；无需重新发明基础 MediaCodec 硬解器 |
| [DefaultMediaCodecAdapterFactory](https://github.com/androidx/media/blob/main/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/DefaultMediaCodecAdapterFactory.java) | API 31+ 默认使用 asynchronous adapter；API 23+ 可强制异步；旧设备存在兼容风险 |
| [VideoFrameReleaseHelper](https://github.com/androidx/media/blob/main/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/VideoFrameReleaseHelper.java) | EXO 会平滑 presentation timestamp、贴近 VSYNC，并向 Surface 报告内容帧率；但 Media3 暴露的策略只有关闭或仅无缝 |
| [DefaultTrackSelector tunneling](https://github.com/androidx/media/blob/main/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java) | tunneling 需要选中的音频和视频 renderer 都支持；与 audio offload 配置互斥；官方要求手工测试机型兼容性 |
| [MediaParserExtractorAdapter](https://github.com/androidx/media/blob/main/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MediaParserExtractorAdapter.java) | Media3 已提供 API 30+ 平台 MediaParser 的 `ProgressiveMediaExtractor` 适配器，可直接作为 A/B 后端 |
| [MatroskaExtractor](https://github.com/androidx/media/blob/main/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java) | Java Matroska extractor 会处理 Cues、字幕、章节、DV block additional 等；可通过 flag 禁止为 Cues 反向 Seek，但会牺牲可 Seek 性 |
| [Media3 customization](https://developer.android.com/media/media3/exoplayer/customization) | 官方支持替换 Renderer、DataSource、Extractor、MediaSource，适合本方案的分层改造 |

### 3.3 成熟项目和 issue 的工程经验

| 来源 | 可借鉴点 |
|---|---|
| [Kodi MediaCodec Surface renderer](https://github.com/xbmc/xbmc/blob/master/xbmc/cores/VideoPlayer/VideoRenderers/HwDecRender/RendererMediaCodecSurface.cpp) | Kodi 把 MediaCodec Surface 输出作为独立 renderer；按 presentation time 释放 codec buffer，并单独管理 HDR GUI/输出状态 |
| [Kodi Android display mode management](https://github.com/xbmc/xbmc/blob/master/xbmc/windowing/android/AndroidUtils.cpp) | 枚举完整 `Display.Mode`，按分辨率和刷新率切换，保存/恢复模式；这是 EXO 当前缺少的输出控制层 |
| [Just Player frame-rate matching](https://github.com/moneytoo/Player/blob/master/app/src/main/java/com/brouken/player/Utils.java) | 在 EXO `Surface.setFrameRate()` 之外使用 `preferredDisplayModeId`，处理 Android TV 上非无缝刷新率切换；同时采用 Surface fixed size 机型 workaround |
| [Jellyfin Android TV EXO backend](https://github.com/jellyfin/jellyfin-androidtv/blob/master/playback/media3/exoplayer/src/main/kotlin/ExoPlayerBackend.kt) | 使用独立 `SurfaceView`，显式启用 audio offload preferences，并把播放器后端能力做成可替换模块 |
| [androidx/media #2541](https://github.com/androidx/media/issues/2541) | 当前 Media3 tunneling 仍假定有效音频轨；Codec2 虽支持 REALTIME video-only tunneling，但库侧尚有限制 |
| [androidx/media #2990](https://github.com/androidx/media/issues/2990) | 高规格电视播放仍有人报告 frame release 调度阈值造成掉帧，说明要以 Perfetto/掉帧时序验证 renderer 调度，而不是盲改常量 |
| [androidx/media #2298](https://github.com/androidx/media/issues/2298) | “目标字节已满但缓冲不足 500ms”是高码率内容的真实问题；本项目上一专项已经针对这一层处理，但它不是显示直出的替代品 |
| [androidx/media #1865](https://github.com/androidx/media/issues/1865) | Android TV 上 DV Profile 7 FEL 不是 Media3 通用支持能力，不能只靠更改 Format 声称支持 |
| [dovi_tool](https://github.com/quietvoid/dovi_tool) | Profile 7 → 8.1 需要实际 RPU/EL 处理；`mode 2` 会移除 FEL mapping 并可丢弃 enhancement layer，不是 MIME 重标记 |

### 3.4 论文的适用结论与边界

- [BOLA: Near-Optimal Bitrate Adaptation for Online Videos](https://doi.org/10.1109/INFOCOM.2016.7524428)、[Pensieve](https://doi.org/10.1145/3098822.3098843) 和 [Puffer](https://www.usenix.org/conference/nsdi20/presentation/yan) 都说明：吞吐预测不稳定时，buffer occupancy 和真实 QoE 反馈比瞬时带宽估计更可靠。
- 这些论文主要研究有多档码率可切换的 ABR。对单轨 80～150Mbps REMUX，它们只能支持“以缓冲趋势决策预载/降档”的方法，不能创造不存在的低码率轨道，也不能替代数据链路改造。
- [Energy efficient video decoding for the Android operating system](https://doi.org/10.1109/ICCE.2013.6486921) 和后续硬件/软件 decoder 能耗研究支持优先使用硬件解码的方向，但不能据此推导某个厂商 decoder 一定支持 5K、DV FEL 或完整零拷贝。

## 四、当前项目源码审阅

### 4.1 已经做对并应保留的能力

| 位置 | 现状 | 审阅结论 |
|---|---|---|
| `ExoUtil.setPlayerView()` | 默认动态创建 Surface/Texture，推荐档强制 Surface | 保留 SurfaceView 默认路径 |
| `ExoUtil.buildRenderersFactory()` | 支持 codec 异步队列、动态调度、late input drop、decoder fallback | 作为基线保留；加入设备策略而不是全局继续堆参数 |
| `FfmpegRenderersFactory.getVideoCodecSelector()` | 硬解模式只保留 hardware codec | 方向正确；应升级为评分/黑名单策略 |
| `PlaybackActivity.syncVideoSurfaceSize()` | 可将 Surface buffer 固定到视频尺寸 | 可降低无谓尺寸转换；必须保留设备回退，避免 mode/尺寸切换重分配冻结 |
| `MediaSourceFactory` | `OkHttpDataSource + CacheDataSource + PriorityTaskDataSource` | 适合作为通用 HTTP fallback |
| `PlaybackBytePositionDataSource` | 获取真实字节位置和 Content-Range 总长 | 是 Go 代理 Range 观测与码率估算的重要观测层 |
| `PreCache` | 播放优先、首帧后预载、BUFFERING/Seek 取消、外部代理避让 | 保留；与 Go 代理的播放/预载优先级契约协同 |
| `PlaybackAnalyticsListener` | 已记录 decoder、掉帧、重缓冲和带宽 | 扩展为 output/tunnel/extractor/codec 决策证据 |
| 定制 Media3 1.11 | 已包含新 renderer scheduling 与 `ProgressiveMediaExtractor` 能力 | 具备继续源码级改造的基础，不必降级到旧 ExoPlayer |

### 4.2 必须修正的问题

#### A. Dolby Vision Profile 5/7 fallback 不安全

当前位置：`ExoUtil.DolbyVisionHdr10FallbackRenderer`。

当前做法：

```text
DV Profile 5 或 7
  → 把 sampleMimeType 改成 video/hevc
  → 清空 codecs
  → 把 ColorInfo 改成 BT.2020/PQ
  → 原始 sample 不变地送给 HEVC decoder
```

问题：

- 这只修改描述信息，没有处理 BL/EL/RPU，也没有转换码流。
- 当前 Media3 `MediaCodecUtil.getAlternativeCodecMimeTypes()` 明确排除 DV Profile 5 和 Profile 7 的通用 HEVC fallback：Profile 5 不向后兼容；Profile 7 已废弃且并非始终向后兼容。
- Profile 5 强制按 HDR10 解码可能出现错误色彩、紫绿画面或 decoder 行为不确定。
- Profile 7 即使有 HEVC Main10 base layer，也必须确认 extractor 输出布局、decoder 是否忽略 EL、是否需要丢弃 EL、HDR10+ metadata 是否保留。

修正：

1. 立即从强制 fallback 中移除 Profile 5。
2. Profile 7 默认交给 Media3/平台能力探测；没有 DV decoder 时优先明确回退 HDR10 base layer或 MPV/native 处理，不得只改 MIME。
3. 如要支持 P7 → P8.1，使用 libdovi/dovi_tool 同类的实际 RPU/EL bitstream filter，且作为 native、可关闭、可校验的路径。
4. 记录 `originalProfile / selectedDecoder / fallbackMode / baseLayerCompatible / outputHdrType`。

#### B. tunneling 有开关但默认策略等于未产品化

- `DefaultTrackSelector.Parameters.setTunnelingEnabled()` 已接入。
- `PlaybackPerformanceSetting` 的自动、推荐、兼容、轻量档都明确写入 `tunnel=false`。
- 没有能力探测结果缓存、机型黑名单、失败计数、首次回退或 OSD 真实状态。

结论：当前 tunneling 只是手工实验开关，不是高规格播放能力。

#### C. 帧率匹配只有“关闭/仅无缝”，不足以覆盖电视

- `ExoPerformanceSetting` 只提供 `OFF` 和 `ONLY_IF_SEAMLESS`。
- Media3 的策略也只有关闭和仅无缝；典型电视/盒子的 60Hz ↔ 23.976Hz 可能是 heavy mode switch，默认不会切。
- 当前没有 `preferredDisplayModeId`，没有分辨率切换，没有切换前暂停/首帧恢复，没有退出恢复原模式。

这解释了“已调用 EXO 帧率匹配，但电视仍显示 60Hz”的常见现象。

#### D. 解码能力与显示能力被错误耦合

`ExoUtil.detectEnhancedVideoProfile()` 先用显示最大尺寸筛选 profile，再判断 HEVC codec 能力；`EnhancedVideoProfile.targets()` 最大只有 3840×2160，并把 4K 目标码率固定为 20Mbps。

问题：

- 5K 内容在 4K 屏上仍可能由支持 5K 的 decoder 解码后由 HWC 缩放，但当前模型直接排除。
- 显示输出能力、decoder 解码能力、内容码率限制是三个不同维度，不应合为一个 `EnhancedVideoProfile`。
- 20Mbps 不是 4K REMUX 的硬件能力上限。轨道 bitrate 有值时，它可能错误限制多轨选择；bitrate 未知时又无法提供保护。
- `VideoCapabilities.getBitrateRange()` 是 codec 声明，不应直接当作网盘稳定吞吐或内容安全码率。

应拆分为：

```text
DecodeCapability   = codec/profile/level/bitDepth/size/rate/performancePoint/tunnel
DisplayCapability  = modes/HDR types/current mode/non-seamless policy
SourceCapability   = content size/fps/bitrate/container/DV layout/seekability
NetworkCapability  = direct/proxy/range/length/throughput/buffer trend
```

#### E. audio passthrough 已接近可用，但 offload 未显式启用

- `DefaultAudioSink` 默认 provider 可探测 passthrough/offload；关闭音频直通时项目会替换 provider。
- TrackSelector 没有设置 `AudioOffloadPreferences`，因此不能把 audio offload 当作已启用能力。
- tunneling 与 audio offload 在 Media3 renderer configuration 中互斥；视频播放应优先 tunneling/encoded passthrough，音频独播再优先 offload。

#### F. extractor 后端固定为 Java `DefaultExtractorsFactory`

- 本地定制 Media3 source jar 已包含 `MediaParserExtractorAdapter`。
- `MediaSourceFactory` 没有使用 `ProgressiveMediaSource.Factory(DataSource.Factory, ProgressiveMediaExtractor.Factory)`，因此无法 A/B 平台 parser。
- 当前 Matroska 默认会为后置 Cues 发起 Seek；网盘代理上的远距离 Range 可能引发起播额外请求，但关闭 Cues Seek 又会损失准确 Seek。

#### G. Go 二进制代理是固定边界，App 不接管其内部 CDN

`PlaySpec` 当前以 `127.0.0.1` URL/Header 表达播放入口。网盘 fileId、下载 token、分片映射、签名刷新和 CDN 连接由 Go 二进制代理持有，App 不具备这些能力，也不尝试绕过或复制这套鉴权状态机。App 只能观测本地 HTTP 交付结果，并对自身的读取、缓存、预载和 EOF 恢复负责。

## 五、目标架构

### 5.1 能力决策总线

新增一次播放一次生成的不可变报告：

```java
record PlaybackCapabilityReport(
        SourceProfile source,
        DecoderProfile decoder,
        DisplayProfile display,
        RouteProfile route,
        OutputDecision output,
        ExtractorDecision extractor) {}
```

它必须成为 TrackSelector、RendererFactory、MediaSourceFactory、输出模式管理器和诊断 OSD 的共同输入，避免各模块分别猜测。

推荐新增：

- `exo/ExoPlaybackCapabilityProbe.java`
- `exo/ExoCodecPolicy.java`
- `exo/ExoTunnelingPolicy.java`
- `exo/ExoOutputModeManager.java`
- `exo/ProgressiveExtractorBackendFactory.java`
- `exo/DolbyVisionFallbackPolicy.java`

### 5.2 双渲染路径

```text
路径 A：兼容低拷贝
MediaCodec → SurfaceView BufferQueue → SurfaceFlinger/HWC
支持：字幕、OSD、普通 Seek、设备覆盖最广

路径 B：tunneled sideband
MediaCodec tunneled decoder → sideband handle → HWC
支持：最少 App/frame scheduling 参与、电视硬件路径概率最高
限制：需音视频同时支持、GPU effects 不可用、设备问题较多
```

自动策略：

1. 仅 Android TV/盒子、SurfaceView、硬解、无 LUT/GL effect 时考虑 tunneling。
2. TrackSelector 和 codec query 同时确认选中的音视频轨支持 tunneling。
3. HDR/DV 还要确认 display HDR type 和 tunneled decoder profile。
4. 首次启用后观察 decoder init、首帧、音视频同步、Seek、暂停恢复。
5. 同一 `Build.MANUFACTURER/MODEL/DEVICE + codecName + mime/profile` 发生两次明确 tunnel 错误，写入本地运行时黑名单。
6. 失败时只重建一次播放器并回退普通 Surface；不要在同一次播放中循环尝试。

禁止 tunneling 的条件：

- TextureView、VideoEffect、LUT、需要抓帧/截图、倍速超出设备验证范围。
- 只有视频无音频；Media3 当前尚要求音频和视频 renderer 同时参与 tunneling。
- 外接蓝牙音频、音频设备切换中、已知 passthrough/tunnel 冲突。
- 设备/codec 黑名单、secure decoder resize 已知故障。

### 5.3 电视输出模式管理器

不能把输出模式管理塞进 `MediaCodecVideoRenderer`；它属于 Activity/Window/Display 生命周期。

#### 输入

- 内容：精确 fps（23.976/24/25/29.97/30/50/59.94/60）、宽高、HDR type。
- 显示：当前 mode、支持 modes、HDR capabilities。
- 用户策略：关闭、仅无缝、电影匹配、分辨率+刷新率匹配。
- 环境：是否 PiP、是否外接 display、是否短视频/直播。

#### mode 评分

```text
1. exact fps 或整数倍刷新率优先：23.976→23.976/47.952/119.88
2. 同分辨率刷新率切换优先于分辨率切换
3. 对长电影允许非无缝切换；短视频和频道切换仅无缝
4. 4K 内容优先 4K mode；1080p 是否切 1080p 由用户策略决定
5. 5K 内容不寻找不存在的 5K mode；输出到最高可靠 mode，由 HWC 缩放
6. HDR mode 必须与 display HDR capability 和 decoder 输出一致
```

#### 生命周期

```text
保存进入播放前 mode
  → prepare 获得可靠 Format/fps
  → 暂停首帧或保持 shutter
  → 请求 Surface.setFrameRate(ALWAYS) 或 preferredDisplayModeId
  → DisplayListener 确认 mode 已变化/超时
  → 开始播放并记录实际 mode
  → 播放结束、切换内核、进入后台时恢复原 mode
```

实现注意：

- Android 官方对长电影推荐允许 `CHANGE_FRAME_RATE_ALWAYS`；但如果要同时改变分辨率，使用 `preferredDisplayModeId`。
- 不能只按 `float % float == 0` 判断倍频；统一转成 milli-Hz 或 rational，正确区分 23.976 与 24、59.94 与 60。
- mode change 可能黑屏 1～2 秒，必须提供用户策略和 OSD 提示。
- 记录请求 mode 和实际 mode；平台可能拒绝请求。
- 可抽取项目现有 MPV `Surface.setFrameRate` 代码为播放器无关组件，EXO/MPV 共用。

### 5.4 Go 二进制代理边界（不在 App 内实施）

当前架构固定保留：

```text
EXO → OkHttp → 127.0.0.1 TCP/HTTP → Go 二进制代理 → 网盘 SDK/CDN
```

Go 代理拥有网盘鉴权、token 刷新、分片映射、CDN 连接、上游重试和内部缓存。当前 App 没有这些接口和凭证，因此本方案不设计、不实施以下内容：

- 网盘 SDK/provider 接入或 CDN 直连。
- App 侧复制 Go 代理的鉴权、签名刷新或上游重试。
- 由 App 修改 Go 代理内部线程、连接池、Range 合并或缓存参数。
- 假设 Unix socket、Binder、SharedMemory 等 IPC 可以自动获得零拷贝收益。

App 侧只保留可验证的边界能力：本地 HTTP 读取、CacheDataSource、预载避让、EOF 续读、缓冲/掉帧/吞吐诊断，以及对“代理未持续供数”的中性错误分类。

### 5.5 大 MKV/REMUX extractor 三后端

#### 后端 A：Media3 Java Matroska

优点：版本可控、行为一致、已有 DV/字幕/章节支持。<br>
改造点：

- 对远端网盘统计 `sniff bytes / cues seek distance / track count / attachment bytes / first sample latency`。
- 对“先播后 Seek”模式可试验 `FLAG_DISABLE_SEEK_FOR_CUES`，但必须标记为临时不可 Seek，后台获得索引后再升级 SeekMap。
- `setLoadOnlySelectedTracks(true)` 只减少未选轨 sample 加载，不代表 extractor 完全不解析轨道元数据。
- 内嵌 ASS、字体附件和大量章节需单独计时，避免把字幕初始化误判为 decoder 慢。

#### 后端 B：平台 MediaParser（API 30+）

用本项目 Media3 source jar 中已有的 `MediaParserExtractorAdapter.Factory` 构造 `ProgressiveMediaSource.Factory`。

只在以下维度 A/B：

- prepare 到 tracks known
- prepare 到 first video sample
- Seek 请求数/字节数/首帧恢复时间
- extractor CPU time、GC、分配量
- 字幕、章节、DV/HDR metadata 完整性

平台 parser 随 Android 版本和 OEM 变化，不能默认全局替换 Java extractor。

#### 后端 C：native libavformat

第一阶段仍输出到 Media3 `ExtractorOutput/TrackOutput`，保留 EXO 的 TrackSelector、SampleQueue 和 MediaCodec renderer。

适用条件：

- Perfetto/CPU profile 明确显示 Java Matroska 是高码率播放的持续 CPU 热点。
- MediaParser 在目标设备不兼容或收益不足。
- 需要 native bitstream filter，例如 DV P7 → P8.1、异常 NAL 修复或特殊容器支持。

局限：仍会进入 Media3 SampleQueue，不是完整压缩数据零拷贝。

第二阶段“native demux 直接喂 MediaCodec”已经接近自研播放器，需要重做：时钟、Seek、DRM、字幕、音视频同步、错误恢复和 MediaSession 语义。除非数据证明 EXO SampleQueue 本身是主瓶颈，否则不建议作为通用路径。

### 5.6 codec 策略层

当前“只保留 hardware codec”应升级为评分：

```text
硬件加速
  + profile/level/bit depth 精确支持
  + size/rate 支持
  + PerformancePoint covers
  + tunneling 支持（若请求）
  + secure/HDR/DV display 匹配
  + 历史成功记录
  - 设备/codec/content 黑名单
  - 曾发生 init、flush、EOS、seek、色彩错误
```

建议持久化 `CodecRuntimeProfile`：

- `deviceKey`
- `codecName`
- `mime/profile/level`
- `resolution/fps/bitDepth`
- `tunneled`
- 首帧耗时、掉帧率、decoder init 次数、fatal/recoverable error
- 最后一次 App/系统版本

#### MediaFormat/codec 参数策略

Media3 当前已经设置：

- `KEY_PRIORITY=0`
- `KEY_OPERATING_RATE`
- max width/height/input size
- tunneling feature 与 audio session id
- API 35+ codec importance

因此不要重复设置同样参数。源码改造只考虑：

1. 对缺失/错误 fps 的文件，在确认真实 fps 后动态更新 operating rate。
2. 2x 倍速 4K60 时，目标 operating rate 为 120；能力不足则限制倍速或降级，而不是强推 codec。
3. vendor key 必须以 `codecName + Build.DEVICE` 白名单管理，逐项验证；禁止全局发送未知参数。
4. `maxInputSize` 仅在捕获到超大 HEVC access unit/codec buffer 错误后按内容峰值修正，不能无条件放大导致额外内存。
5. API 23～30 的强制异步队列建立 blacklist；API 31+ 保持 Media3 默认异步。

### 5.7 Dolby Vision/HDR 策略

| 内容 | 推荐路径 |
|---|---|
| HDR10 HEVC Main10 | 确认 display HDR10 + decoder profile；SurfaceView；优先 tunneling/普通 Surface 硬解 |
| HDR10+ | 保留逐帧动态 metadata；对已知 DV+HDR10+ 冲突机型按 codec policy 选择一种输出 |
| DV Profile 5 | 只用真正支持 P5 的 DV decoder/display；不允许伪装 HDR10 |
| DV Profile 7 MEL | 优先原生 DV；否则经验证后提取 BL/RPU 转 P8.1 或回退 HDR10 BL |
| DV Profile 7 FEL | Android 通用 MediaCodec 不保证应用 FEL；只能原生厂商链路或丢弃 EL 后降级，不能宣称完整 FEL |
| DV Profile 8.1 | DV decoder/display 优先；不支持 DV 时可用向后兼容 HDR10 base layer，但要保留正确 ColorInfo |

新增 `DolbyVisionFallbackPolicy` 必须先解析 codec string 与必要的 bitstream metadata，再选择：

```text
NATIVE_DV
HEVC_BASE_LAYER
CONVERT_P7_TO_P81
FALLBACK_OTHER_PLAYER
UNSUPPORTED
```

### 5.8 音频路径

- 电影播放：优先 encoded passthrough；tunneling 启用时由同一个 renderer configuration 管理音视频，禁用 audio offload。
- PCM、蓝牙、音效、倍速：走普通 AudioTrack；不要强求 passthrough。
- 音频独播/后台播放：显式启用 Media3 `AudioOffloadPreferences`，降低 CPU/功耗。
- 输出 mode 切换后重新探测 AudioCapabilities；`androidx/media #2258` 表明刷新率切换可能与部分设备的 passthrough/PCM 路径互相影响，必须纳入测试矩阵。

## 六、逐文件落地建议

| 文件/模块 | 修改建议 |
|---|---|
| `ExoUtil.java` | 移除 P5 强制 HDR10 fallback；P7 交给策略类；注入 `ExoCodecPolicy`；TrackSelector 接受 session capability；补 audio offload preference；不再用一个 `EnhancedVideoProfile` 混合 display/decode/network |
| `PlaybackActivity.java` | 接入 `ExoOutputModeManager`；保存/恢复 Display.Mode；DisplayListener 确认实际模式；Surface fixed size 改为设备策略；输出状态进入 OSD |
| `ExoPlayerEngine.java` | prepare 前生成 session capability；tunnel 失败只重建一次；维护 playback session id 与回退原因 |
| `MediaSourceFactory.java` | 保持 Go 代理 HTTP 作为网盘入口；按 session 选择 Java/MediaParser/native extractor；避免全局共享可变 header 状态扩展到多 source |
| `PlaySpec.java` | 保持 URL/Header 和代理 source identity；不在 App 侧引入网盘 token、SDK handle 或 CDN 直连字段 |
| `PlaybackRoute.java` | 明确区分 App 自有 Go 代理、外部/未知回环和直接远程 HTTP；网盘回环不新增绕过代理的 route |
| `PlaybackAnalyticsListener.java` | 增加 requested/actual display mode、surface type/size、tunneling actual、codec name/profile、extractor backend、Cues Seek、remote bytes、range count |
| `PlayerOsdController.java` | 显示“请求/实际”而不是只显示设置开关，例如 `Tunnel requested/active/fallback`、`Mode 2160p23.976 actual 2160p60` |
| `PlaybackPerformanceSetting.java` | 新增输出策略和 tunnel 自动档；不再让所有 preset 无条件写 `tunnel=false`；保留兼容档关闭 |
| 定制 Media3 fork | 只在上游扩展点不足时改：video-only tunnel、renderer timing、DV P7 安全 fallback、额外 codec telemetry；每项保持独立 patch 和上游 issue 链接 |

## 七、分阶段实施路线

### 阶段 0：正确性和可观测性

1. ✅ 删除 DV Profile 5 伪 HDR10 fallback。
2. ✅ Profile 7 fallback 默认关闭或只走已验证 base-layer policy。
3. ✅ OSD/日志显示 actual decoder、tunneling active、Surface 类型、请求/实际 display mode。
4. ✅ 将 `EnhancedVideoProfile` 拆为四类 capability，不改变默认播放行为。
5. 建立测试素材元数据清单和 SHA-256，不在日志记录敏感 URL/header。

退出条件：每次播放能回答“慢在网络、demux、decoder 还是 display scheduling”。

### 阶段 1：电视输出模式管理器

1. 抽取 EXO/MPV 共用 frame-rate/display mode 组件。
2. ✅ 支持 `OFF / SEAMLESS / MOVIE_ALWAYS / RESOLUTION_AND_RATE` 的策略决策。
3. ✅ 实现 Display.Mode 应用和播放退出恢复；PiP/多显示专项仍需补测。
4. 先在非 DRM、非直播、时长大于 10 分钟的电影启用。

退出条件：23.976/24/25/50/59.94/60 测试片请求与实际 mode 可核对，退出播放能恢复。

### 阶段 2：tunneling 自动策略

1. ✅ 能力探测和 codec query。
2. ✅ TV + SurfaceView + hard decode + no effect 才候选。
3. ✅ 首帧及播放中 stall watchdog；Seek/暂停恢复仍需长稳覆盖。
4. ✅ 失败一次回退普通 Surface；形成运行时黑名单。
5. 先 SDR/HDR10，再验证 DV；不从 DV FEL 开始。

退出条件：目标白名单设备 30 分钟播放、Seek 20 次、暂停恢复 20 次无异常；失败设备能自动回退且不循环重建。

### 阶段 3：代理边界（App 不实施内部改造）

Go 代理继续作为唯一网盘入口。App 不新增 SDK、Provider、CDN 直连或 IPC；只对本地 HTTP 交付进行观测和容错。代理内部 Range、缓存、并发和 CDN 状态不纳入 App 的实施 TODO。

### 阶段 4：extractor A/B

1. Java Matroska 基线。
2. API 30+ MediaParser 实验组。
3. 仅对高成本 MKV 加 native libavformat 组。
4. backend 选择结果写入机型/文件特征策略，不做全局一次性替换。

退出条件：字幕、章节、音轨、DV/HDR metadata、Seek 全量回归后，再按数据选默认。

### 阶段 5：定制 Media3/vendor codec

只有前四阶段仍证明 decoder feed 或 renderer scheduling 是瓶颈时才进入：

- video-only Codec2 tunneling。
- 特定电视 codec 的 early scheduling/frame release patch。
- P7 → P8.1 native bitstream filter。
- vendor codec parameter 白名单。
- native demux 直喂 MediaCodec 专用后端。

## 八、验收指标和测试矩阵

### 8.1 不使用单一“是否卡”判断

| 层 | 指标 |
|---|---|
| Source | remote bytes、Range 次数、平均/最大 Range gap、取消延迟、token refresh |
| DataSource | read throughput、read stall P50/P95/P99、local proxy/request count |
| Extractor | tracks known、first sample、Cues Seek、Seek 恢复、CPU、allocation/GC |
| SampleQueue | allocated bytes、forward buffer、back buffer、`<500ms target reached` |
| Decoder | init time、codec name、input/output buffer stall、dropped/skipped frames、error |
| Surface | SurfaceView/TextureView、buffer size、frame release offset、actual first frame |
| Display | requested mode、actual mode、switch latency、恢复结果、HDR type |
| QoE | 起播、重缓冲次数/总时长、Seek P95、音画同步、连续播放稳定性 |

### 8.2 工具

- Media3 Analytics/EventLogger 的结构化精简日志。
- Perfetto：`sched/freq/gfx/view/binder/hal/memory`，观察 codec、extractor、代理和 SurfaceFlinger。
- `dumpsys media.codec`、`dumpsys SurfaceFlinger`、`dumpsys display`、`dumpsys media.audio_flinger`。
- HWC 是否 DEVICE/CLIENT composition 以系统证据判断，不能只看 App 代码。
- Android FrameTimeline/JankStats 只用于 UI；视频掉帧以 decoder counters、frame release offset 和 SurfaceFlinger 为准。

### 8.3 素材矩阵

| 类别 | 最少覆盖 |
|---|---|
| 分辨率/fps | 2160p23.976、2160p60、5120×2160/2880、1080p25/50 |
| codec | HEVC Main10、AV1 Main10、H.264 High |
| HDR | SDR、HDR10、HDR10+、DV P5、P7 MEL、P7 FEL、P8.1 |
| container | MKV REMUX、MP4、TS/M2TS |
| 音频 | AAC、AC3/EAC3、TrueHD Atmos、DTS-HD；PCM/直通 |
| source | 本地文件、远端 HTTPS、App 自有 Go 代理、外部/未知 loopback |
| 操作 | 起播、连续播放、±10s Seek、大跨度 Seek、暂停恢复、切音轨/字幕、切后台 |

每个阶段先做 3～5 分钟快速验收，再做至少 30 分钟稳定性和多次 Seek；快速验收不能替代长稳测试。

## 九、明确不建议的“优化”

1. 无条件把所有设备强制 tunneling。
2. 把 TextureView 或 GPU LUT 路径称为电视直出。
3. 继续提高 buffer 秒数而不看 allocator 字节和真实码率。
4. 无条件扩大 MediaCodec `maxInputSize`。
5. 把 4K capability 的 bitrate 固定为 20Mbps 并当作 decoder 极限。
6. 用本地 `127.0.0.1` 突发带宽估算网盘 CDN 吞吐。
7. 对 DV Profile 5/7 只改 MIME/ColorInfo。
8. 把 MediaParser 或 native libavformat 当作必然更快；必须 A/B。
9. 看到 `SurfaceView` 就宣称已实现完整零拷贝或 HDMI bypass。
10. 在没有稳定接口契约时由 App 篡改外部代理的网盘线程参数。

## 十、最终建议

本项目下一轮不应继续以“EXO 参数页增加更多开关”为主，而应建立三个正式子系统：

```text
ExoPlaybackCapabilityProbe + ExoCodec/TunnelingPolicy
ExoOutputModeManager
Go 代理 HTTP 边界 + EXO CacheDataSource/EOF recovery
```

最先落地顺序：

1. 修正 DV fallback。
2. 输出模式管理器。
3. tunneling 自动探测和回退。
4. MediaParser/native extractor A/B。

其中 1～3 可以完全在当前仓库内推进；第 4 项要获得网盘接口 JAR/代理维护方的正式随机读或 token 契约；第 5 项应由真机 Perfetto 数据决定是否值得进入 native 层。

这条路线不会把 EXO 变成 MPV，但能把 EXO 从“通用 Java 播放器 + 参数优化”推进为“Media3 控制层 + 设备能力策略 + tunneled/Surface 双硬件路径 + Go 代理边界观测与容错”。这是在继续保留 MediaSession、TrackSelector、DRM 和 Android 生态兼容性的前提下，当前 App 能实际掌控的优化边界。

## 十一、第二层深水区候选清单

本节补充第一版方案没有展开的所有重要候选方向。它们不是都应该立即实施，必须按“证据 → 小范围 A/B → 白名单 → 默认化”的顺序推进。

### 11.1 MediaCodec 输入/输出调度

#### A. 异步 codec queue 深度和线程亲和性

Media3 API 31+ 默认使用异步 MediaCodec adapter，但异步并不保证每个厂商实现都同样稳定。可研究：

- callback thread 与 queueing thread 是否落在被热控降频的 LITTLE 核；
- input queue 一次性提交多少 access unit；
- callback burst 是否导致播放器线程和代理线程同时抢占；
- decoder output callback 到 Surface queue 的时间间隔；
- API 23～30 强制异步是否在目标 SoC 上真正减少掉帧。

实施方式：

1. 在 `DefaultMediaCodecAdapterFactory` 的定制 fork 中允许按 `deviceKey + codecName + mime` 选择线程策略。
2. 记录 queue wait、callback interval、input starvation、output starvation。
3. 不直接固定“大 queue”；高码率下过深的 queue 会增加内存和 Seek 延迟。

#### B. codec operating rate 的动态修正

Media3 已根据 Format fps 和播放速度设置 `KEY_OPERATING_RATE`，但大文件经常出现 fps 未知、VFR、真实 fps 与容器声明不一致的情况。

可增加：

- 首 2～5 秒样本 PTS 间隔估计；
- 23.976/29.97/59.94 的 rational 归一化；
- 倍速播放前先检查 `PerformancePoint` 和历史成功档案；
- 过高 operating rate 触发 codec reconfigure/功耗升高时自动回落。

验收不能只看设置值，必须同时看 decoder 实际频率、掉帧和 SoC 温度。

#### C. codec 预热与复用

连续剧集、同一文件不同分段或快速切换清晰度时，重复 create/configure/flush codec 会造成首帧延迟和瞬时内存峰值。

候选方案：

- 在同一 MIME/profile/size/fps/secure/tunnel 条件下保留一个短生命周期 codec warm pool；
- 使用 Media3 renderer 的 resource transfer，而不是并行持有两个完整播放器；
- 对不同 profile 或 HDR mode 不复用 codec，避免颜色状态和 tunneled session 污染；
- 预热必须受 thermal/memory budget 限制，低 RAM 设备禁用。

这是起播优化候选，不应在单个超大文件连续播放中盲目启用。

#### D. 输入 buffer 与超大 access unit

高码 HEVC/TrueHD 可能出现远高于平均值的单帧/单样本。应记录：

- access unit P95/P99 大小；
- codec input buffer capacity；
- `max-input-size` 是否导致 configure 失败或频繁扩容；
- 扩容发生时的 Java/native heap 峰值。

只有在捕获到实际 buffer 不足后，才按 P99 峰值增加上限；不能凭“4K/5K”标签无限放大。

### 11.2 Renderer、VSYNC 和 FrameTimeline

#### A. 电视专用 frame release policy

Media3 `VideoFrameReleaseHelper` 会采样 VSYNC 并对齐 release timestamp，但电视设备上可能存在：

- Choreographer 频率与实际 panel refresh 不一致；
- display mode 切换后 VSYNC sample 过期；
- 24fps 在 60Hz 上出现长短帧抖动；
- renderer 过早唤醒 CPU 或过晚提交 Surface buffer。

可在定制 Media3 fork 增加：

- display mode 切换后的 VSYNC sampler 强制重建；
- FrameTimeline expected/actual present time 记录；
- 电视 profile 的 early/late threshold；
- 与 tunneling 路径分开的 release policy（tunneling 不应由 App 重复调度每帧）。

`androidx/media #2990` 说明这一方向需要实测，不能直接把 50ms 常量改小或改大。

#### B. SurfaceControl transaction 与固定尺寸

当前 `PlaybackActivity.syncVideoSurfaceSize()` 使用 `SurfaceHolder.setFixedSize()`。进一步可研究：

- 使用 `SurfaceControl.Transaction` 同步设置 crop、buffer size、destination frame；
- 让 resize transaction 与下一帧 buffer latch 对齐；
- 避免 secure tunneling resize 时出现黑边/黑块；
- 仅在真正改变 buffer size 时调用，避免每次布局触发重新分配。

AndroidX issue #3070 表明 secure tunneling 的 SurfaceView 动画 resize 可能是平台限制，不能用 UI 动画掩盖。

#### C. 组合层/HWC 诊断

增加系统证据采集：

- `dumpsys SurfaceFlinger` 中 video layer 的 composition type；
- HWC device/client composition 变化；
- layer alpha、rounded corner、blur、crop、transform；
- HDR layer metadata 和 output color mode；
- Surface buffer usage、跨进程 fence 等待。

只有确认从 DEVICE/HWC 退化到 CLIENT/GLES，才值得针对 overlay 条件做源码改造。

### 11.3 大文件容器与索引

#### A. Cues/SeekMap sidecar

超大 MKV 的 Cues 可能位于文件末尾。对网盘 Range 源，读取 Cues 可能产生额外远端请求。

可建立本地 sidecar：

```text
source identity + ETag/size/last-modified
  → Cues/SeekMap/track metadata
  → SQLite 或紧凑二进制索引
```

使用条件：

- source identity 稳定且可验证；
- token 变化不代表文件变化；
- index version 与 extractor 版本匹配；
- 文件 hash/size 改变时立即失效。

sidecar 可以改善二次播放起播和大跨度 Seek，但不能替代首播的容器 sniff。

#### B. 渐进式索引

对于没有 sidecar 的首播，可在首段播放期间后台解析并持久化已发现的 Cluster/Cue 信息；不能让后台索引读取抢占播放 Range。

索引器必须使用独立低优先级 `DataSource`，并由 `PriorityTaskManager` 与播放共享预算。

#### C. 字幕/附件懒加载

MKV 可能包含大量字体附件、章节和未选字幕。候选方案：

- 首次只解析视频、当前音频和必要时间基；
- 用户打开字幕时再读取对应 Track/Attachment；
- 字体附件按字幕 renderer 实际需要加载；
- 对外挂字幕避免重复网络请求和全文缓存。

必须验证 EXO/Media3 TrackGroup 语义，不能破坏默认轨道选择和字幕时间轴。

### 11.4 网络、代理和缓存深水区

#### A. HTTP 客户端/协议

Cronet、HttpEngine、HTTP/2、HTTP/3 只对 App 直接连接远端 origin 有意义。若 EXO 仍只连 `127.0.0.1`，替换 OkHttp 不会优化代理到 CDN 的上游段。

由于 EXO 只连接 `127.0.0.1`，App 侧不对代理到 CDN 的 HTTP/2、HTTP/3、TLS、socket buffer 或拥塞控制做方案承诺；这些属于 Go 代理内部实现，当前不在 App TODO 范围。

#### B. Range 合并与顺序窗口

播放器读取通常是顺序的，但容器 Seek、预载和字幕可能产生相邻 Range。Go 代理内部可把距离很近的请求合并为一个顺序窗口；这些属于代理实现，不纳入 App TODO：

- 播放窗口高优先级；
- 预载窗口低优先级；
- Seek 立即取消旧窗口；
- 已下载块去重；
- 不跨越过大的空洞，避免无效下载。

合并窗口应以秒数和字节双重封顶，不能只按固定 10MB/20MB。

#### C. 块缓存与稀疏文件

当前 `SimpleCache` 是 Media3 CacheDataSource 层的文件缓存。对于超大远端文件，可研究专用块缓存：

- 固定块大小或按 GOP/Cluster 对齐；
- bitmap 记录已缓存块；
- 读命中直接返回；
- 取消只保留已完成块；
- 低磁盘空间按 LRU/播放历史淘汰。

不要把完整 50GB 文件映射成 Java byte buffer；块缓存仍应通过 `DataSource` 流式读取。

#### D. 真实上游吞吐反馈

外部代理应提供可选 telemetry：

- CDN 连接数和并发分片数；
- 上游实际吞吐 P50/P95；
- Range 合并/重试/回源次数；
- token 过期和重新签名次数。

App 侧 `DefaultBandwidthMeter` 只能代表本地连接的交付速度，不能替代这些指标。

### 11.5 内存、GC 和热控

#### A. Allocator 分层预算

当前专项已经修正 target bytes/back buffer，但还可以把预算拆成：

```text
前向 SampleQueue
音频队列
codec input staging
extractor scratch
subtitle/attachment
预载/索引
```

每层记录峰值和来源，才能知道“target bytes 未满但 RSS 已很高”时是哪里占用。

#### B. Java/native heap 关联

MediaCodec、Surface buffer、FFmpeg/native demux 的内存不完全计入 Java Allocator。应同时采集：

- Java heap、native heap、PSS/RSS、graphics；
- `dumpsys meminfo` graphics/GL/ashmem；
- codec buffer 和 Surface buffer 数量；
- GC pause 与播放状态。

不能只用 `Runtime.maxMemory()` 推断整进程安全上限。

#### C. Thermal-aware policy

长时间播放 4K/5K 可能因为 SoC/DDR/GPU 温度导致降频，表现为“开始流畅，十几分钟后掉帧”。可加入：

- `PowerManager` thermal status；
- CPU/GPU/DDR 频率采样；
- 过热时关闭预载、降低后台索引、停止 UI 动画；
- decoder 不支持降级时优先维持音频和显示连续性。

App 不应擅自锁定最高频率或修改厂商 thermal daemon；只能做负载收敛。

### 11.6 音视频时钟和输出设备

#### A. tunneling 音频时钟

tunneling 依赖 AudioTrack/AudioFlinger 的硬件同步。要记录：

- audio session id、HW AV sync id；
- audio timestamp 是否连续；
- tunneled first frame、audio start、video start 的相对时间；
- pause/seek/route change 后时钟是否重新建立。

没有这些证据，不能把 tunneling 黑屏或音画不同步简单归因于网络。

#### B. passthrough/PCM/蓝牙切换

电视功放、ARC/eARC、蓝牙和系统音效会改变 AudioTrack 能力。输出模式管理器必须与音频能力重新协商，不能只在播放器创建时读取一次。

#### C. 音频重采样与大声道数

TrueHD、DTS-HD、96/192kHz 多声道会增加 PCM fallback 的 CPU 和内存；应记录实际输出模式、重采样器启用状态和 AudioTrack buffer underrun。

### 11.7 DRM 和 secure path

DRM 视频具有额外边界：

- secure decoder 只能输出受保护 Surface；
- SurfaceFlinger/HWC 必须保留 protected overlay；
- TextureView、截图、GPU effect 通常不可用；
- tunneling + secure + resize 的组合存在设备限制；
- license、CDM session 和 codec init 失败时间可能主导起播。

可优化但需严格隔离：

1. license 请求预热和 session 复用；
2. secure decoder capability 缓存；
3. protected Surface 生命周期减少重复销毁；
4. DRM 失败后一次性回退普通路径，不循环重建。

不得把 secure buffer 转成普通 YUV 来“优化”，那会破坏安全模型。

### 11.8 厂商 codec 和 SoC 特化

只对有足够设备样本的 codec 建立 profile：

- OMX/CCodec 名称和版本；
- profile/level/bit depth；
- tunneled/secure 支持；
- input buffer 数、output delay、flush/EOS 行为；
- HDR10+/DV metadata；
- 4K60/5K30 PerformancePoint；
- thermal 降频曲线。

vendor 参数示例只能进入白名单配置，不可从用户输入或远端 URL 注入。每个参数需要：

- codec name 匹配；
- API/固件版本匹配；
- 开关和回退；
- A/B 日志；
- 至少一个可复现故障或收益证据。

### 11.9 UI、字幕和合成层

即便视频使用 SurfaceView，透明字幕、弹幕、LUT、圆角、模糊和动画 UI 也可能让 HWC 从纯 DEVICE 合成退化为 MIXED/CLIENT。

候选方案：

- 播放时隐藏全屏透明背景，只保留实际字幕区域；
- 字幕更新只触发局部 layer 更新；
- 高规格/HDR/tunnel 时关闭 LUT 和 GPU video effects；
- OSD 显示短暂出现后自动淡出，避免持续触发 UI 合成；
- 通过 dumpsys/Perfetto 验证 layer composition，而不是凭视觉判断。

不能为了“直出”强行隐藏用户明确启用的字幕；应让输出策略显示当前路径和代价。

### 11.10 完整 native 直喂路径

最终极的方案是：

```text
native network/range
  → native demux
  → native clock/queue
  → MediaCodec input surface/output surface
  → HWC/tunnel
```

它可能减少 Java SampleQueue 和 JNI 边界，但要重新实现：

- container sniff、track selection、DRM sample encryption；
- PTS/DTS、B-frame reorder、audio/video clock；
- Seek、Cues、live edge、EOS；
- subtitle、chapter、metadata；
- MediaSession、通知、错误分类；
- Surface 生命周期和 codec reuse。

这不是普通 EXO 优化，而是新增一套播放器内核。只建议作为极少数设备/格式的实验后端，不建议一开始替代 EXO 主路径。

## 十二、候选点取舍矩阵

| 方向 | 预计收益 | 证据门槛 | 改动范围 | 设备依赖 | 建议 |
|---|---|---|---|---|---|
| tunneling 状态机 | 显示/CPU 高 | 中 | App+Media3 | 高 | P1 白名单 |
| Display.Mode 管理 | 观感高 | 低 | App/Window | 中 | P0 |
| MediaParser A/B | 起播中 | 低 | MediaSource | 高 | P1 |
| native libavformat bridge | 解析中～高 | 高 | native+Media3 | 中 | P2 |
| native 直喂 codec | 理论最高 | 极高 | 重写内核 | 极高 | P3 实验 |
| codec queue 线程策略 | 稳定性中 | 高 | Media3 fork | 高 | 有 Perfetto 再做 |
| frame release TV policy | 掉帧中～高 | 高 | Media3 fork | 高 | 有 FrameTimeline 再做 |
| codec warm pool | 起播中 | 中 | renderer/生命周期 | 高 | 连续切集场景 |
| Cues sidecar | Seek/二次起播高 | 中 | extractor/cache | 低～中 | P1 |
| 块缓存/稀疏文件 | 网络稳定性中～高 | 中 | Go 代理内部 | 中 | 不在 App 范围 |
| thermal policy | 长稳中 | 低 | App 调度 | 中 | P1 |
| vendor codec 参数 | 特定机型高 | 极高 | Media3 fork | 极高 | 白名单 |
| DRM secure 优化 | 特定内容高 | 高 | DRM/Surface | 极高 | 设备专项 |
| UI/HWC 合成收敛 | 直出观感中 | 中 | UI/Surface | 高 | 与 tunnel 同测 |

## 十三、为每个优化建立“证据门”

任何候选改造进入默认配置前，都必须回答：

1. 当前瓶颈属于 network、DataSource、extractor、SampleQueue、decoder、Surface 还是 display mode？
2. 改造是否减少了明确的等待、拷贝、调度或合成步骤？
3. 是否只在某个厂商 codec/固件有效？
4. 失败时能否无损回退到当前稳定路径？
5. 是否增加内存、耗电、黑屏、Seek 或 DRM 风险？
6. 3～5 分钟快速 A/B 是否有信号？30 分钟长稳是否无回归？
7. 是否需要上游 Media3 patch，能否保持独立 cherry-pick？

建议为每项建立如下记录：

```text
Optimization ID
Scope/device/content
Hypothesis
Changed layer
Baseline metrics
Experiment metrics
Failure modes
Fallback
Default decision
Evidence links
```

## 十四、扩展后的最终实施顺序

### 第一批：当前仓库内即可完成

1. ✅ DV Profile 5/7 fallback 安全修正。
2. ✅ Capability model 拆分。
3. ✅ Display.Mode/Surface frame-rate 输出管理器第一版。
4. ✅ tunneling 白名单、watchdog、回退和 OSD actual 状态第一版。
5. ✅ FrameTimeline/HWC/codec queue 可观测边界梳理与 EXO 帧释放时序基础诊断（真实 HWC 合成和队列占用需 Perfetto/厂商工具补证）。

### 第二批：需要接口或真机证据

1. Cues/SeekMap sidecar 和字幕/附件懒加载。
2. MediaParser、Java Matroska、native bridge A/B。
3. Go 代理内部块缓存、Range 合并和 telemetry 不属于 App 实施范围。
4. thermal-aware preload/index/线程策略。

### 第三批：厂商或 Media3 fork 专项

1. video-only Codec2 tunneling。
2. TV FrameReleaseControl/FrameTimeline 特化。
3. codec warm pool 和 vendor 参数。
4. secure Surface/DRM 专项。
5. P7 → P8.1 native bitstream filter。

### 第四批：只作为实验后端

1. native demux 直喂 MediaCodec。
2. 自有 native clock、queue、Seek、subtitle 和 MediaSession 内核。

扩展后的结论修正为：当前 App 真正值得优先投入的是 tunneling 状态机、电视输出模式、codec/DV 正确性、extractor A/B，以及 Go 代理边界上的本地交付诊断与 EOF 容错；Go 代理内部 CDN、鉴权、Range 合并和缓存不属于 App 可实施范围。

## item-48 实施记录：EXO 帧调度与 codec 运行诊断

通过 Media3 `onVideoFrameProcessingOffset` 聚合帧处理偏移，记录平均提前/滞后时间、样本数和滞后批次；通过 `onVideoCodecError` 记录可恢复 codec 错误，不逐帧刷日志。参数面板新增 `EXO帧调度` 行，用于区分 decoder/frame release 压力和网络缓冲问题。HWC composition 仍需系统侧 `dumpsys SurfaceFlinger`/Perfetto 证据，App 不虚构该指标。

专项测试：`ExoFrameTimingMetricsTest`。对应 Tag：`playback-optimization-item-48`。

## item-47 实施记录：参数面板直接显示帧率和码率

当视频轨没有声明码率、但音频轨存在码率时，原估算器会把音频-only FORMAT 结果当作整段媒体码率，导致视频行不显示估算值。item-47 在视频轨码率缺失时忽略该 FORMAT 结果，改用 Content-Length/时长或真实字节斜率估算；同时移除“约”和“（观测）”等修饰词，直接显示 `59.940fps`、`19.5Mbps` 这类值。

专项测试：`ObservedMediaBitrateEstimatorTest`。对应 Tag：`playback-optimization-item-47`。

## item-46 实施记录：HTTP EOF 续读改为连续失败熔断

真机 item-45 监听发现同一长文件约每 31 秒出现一次上游固定长度响应截断；“每个 DataSpec 总共最多续接 2 次”会在第三次断流时把可恢复的 Range 续读升级为 Exo `loadError`。item-46 将限制改为连续失败次数（3 次），每次成功读取数据后清零计数。这样长视频可以跨越多次独立断流，同时仍能在上游持续失败时快速交回 Exo 的原生错误处理，避免无限重试。

实现文件：`app/src/main/java/com/fongmi/android/tv/player/exo/HttpEofRecoveryDataSource.java`；专项测试：`HttpEofRecoveryPolicyTest`。对应 Tag：`playback-optimization-item-46`。
