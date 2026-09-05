# 星落 APK 智能去广功能评估

评估日期：2026-08-17

分析对象：

- APK：`G:\下载\星落.apk`
- 截图：`F:\temp\orca-paste-1786891313090-148c0121-c855-4923-8dd7-e697cd6651be.png`
- 反编译工具：`G:\jadx-1.5.2`
- 当前项目：WebHTV / FongMi TV 分支

## 结论

**有明显帮助，但应作为现有 HLS 去广告之后的可选语音兜底能力，不应替换当前的 HLS manifest 净化、URL 拦截和用户规则体系。**

星落 APK 最有价值的部分是：从播放器解码后的 PCM 音频中做离线语音识别，命中广告关键词后按冷却策略执行相对跳转。当前项目已经具备同类基础设施，包括 ExoPlayer 音频处理器、单声道转换、16 kHz 重采样、Sherpa-ONNX 中文识别模型和播放器 seek 能力，所以不需要照搬 APK 的 Vosk 实现，主要复用其行为设计即可。

建议优先级：**中等，放在现有智能去广 Phase 1 稳定之后实施。** 首版只支持 ExoPlayer、点播、非音频直通场景，并默认使用提示确认或可撤销跳过。

## APK 功能实现证据

### 1. 功能不是设置页占位

APK 包名为 `xinghe.tv`，版本 `5.8.5`，启动 Activity 仍为 `com.fongmi.android.tv.ui.activity.HomeActivity`，与当前项目有明显的 FongMi TV 血缘关系。

资源表包含完整状态和操作文案：

- `智能去广`
- `AI语音去广`
- `广告关键词`
- `AI跳过时长`
- `AI模型状态`
- `模型未下载 / 下载中 / 已就绪 / 加载失败`
- `最近识别：%1$s`

Manifest 同时申请了 `RECORD_AUDIO` 和麦克风硬件特性。不过去广告主链路并非监听电视麦克风，而是直接读取播放器解码后的 PCM 音频。

### 2. PCM 音频入口

混淆类 `Q3/e$a.d(...)` 构建 ExoPlayer 音频输出时，把 `T3/i` 作为 `AudioProcessor` 插入音频链路。`T3/i.c(ByteBuffer)` 的行为是：

1. 复制当前 PCM `ByteBuffer`，保证原音频继续输出；
2. 保存输入采样率和声道数；
3. 把音频字节交给语音检测管理器 `T3/h.C(byte[], sampleRate, channelCount)`。

因此它分析的是节目原始音轨，稳定性和隐私边界都优于用麦克风重新采集电视声音。

### 3. 离线识别模型

APK 内置 Vosk 运行库，并从以下地址下载中文小模型：

`https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip`

模型目录为 `vosk-model-small-cn-0.22`。初始化时检查 `am`、`conf` 等目录，并以 16 kHz 创建 `org.vosk.Recognizer`。输入 PCM 会先转单声道和重采样，再调用 `acceptWaveForm`、`getResult` 或 `getPartialResult`。

### 4. 关键词与命中策略

默认关键词为：

`麻将来了,澳门,赌场,娱乐城,荷官,百家乐,老虎机,时时彩,六合彩,彩票,下注,投注,首充,提现,棋牌,捕鱼,斗地主`

关键词配置键为 `ai_adblock_keywords`，使用逗号、中文逗号或换行分隔。识别结果的命中顺序为：

1. 原文包含关键词；
2. 识别文本和关键词规范化后再做包含匹配；
3. 模糊前缀匹配，长度为 `min(关键词长度, max(4, floor(关键词长度 * 0.7)))`。

第三步能提高弱识别场景的召回率，但也明显增加误跳风险，不建议在当前项目首版默认启用。

### 5. 节流、冷却和跳过

APK 的控制策略为：

- 识别事件处理间隔至少 800 ms；
- 广告命中后 5 秒内不重复触发；
- 连续两次命中间隔小于 10 秒时增加连续命中计数；
- 基础跳过时长默认 15 秒，配置键为 `ai_adblock_skip_seconds`；
- 实际跳过时长按连续命中放大为 1 倍、2 倍、4 倍。

`T3/h` 最终把跳过毫秒数回调给播放器管理器。`O3/q.x0(long)` 获取当前播放位置，加上跳过时长，在已知总时长时钳制到 duration，随后调用播放器 seek。可以确认这是播放器内相对跳转，不是 URL 或 HLS 分片拦截。

## 与当前项目的对应关系

### 当前项目已有能力

1. `HlsManifestCleaner` 已有结构化分片删除和安全阈值：最多删除 35% 分片、最多 90 秒，并对 byte-range、LL-HLS、超大 manifest 等情况回退原文。
2. `AiAdDetectionService` 根据脱敏 URL 和 M3U8 证据生成候选规则，`VideoActivity` 只在用户确认后保存 `UserAdRule`。
3. `RealtimeSubtitleAudioProcessor` 已能从 ExoPlayer PCM 输出中提取单声道 float 音频。
4. `RealtimeSubtitleController` 已有队列、16 kHz 重采样、模型下载、内存检查、线程调度和生命周期管理。
5. `RealtimeSubtitleRecognizer` 已使用 Sherpa-ONNX，并已有中文流式模型 `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`。
6. `ExoUtil.buildAudioSink(...)` 已通过 `DefaultAudioSink.setAudioProcessors(...)` 安装实时字幕音频处理器。
7. `IntroSkipPlayback` 已具备自动跳过、确认跳过和 seek 边界处理模式。

### 可复用价值

| APK 能力 | 对当前项目的价值 | 建议 |
|---|---:|---|
| 播放器 PCM 旁路 | 高 | 直接复用当前实时字幕音频链路，不再增加一次 ByteBuffer 复制 |
| Vosk 中文模型 | 低 | 当前已有 Sherpa-ONNX，不建议再引入第二套 JNI、模型和下载管理 |
| 关键词规范化和命中 | 高 | 抽成纯 Java `VoiceAdMatcher`，便于单元测试 |
| 800 ms 节流和 5 秒冷却 | 高 | 可直接借鉴行为参数 |
| 1/2/4 倍连续跳过 | 中低 | 误跳影响大，首版不要照搬，最多将跳过上限设为 30 秒 |
| 模型状态设置页 | 中 | 可复用现有实时字幕模型状态，避免重复下载入口 |
| 播放器相对 seek | 高 | 复用现有 PlayerManager / IntroSkipPlayback 的边界和确认机制 |

## 推荐接入方案

### 架构定位

新增 `VoiceAdDetector`，只产生语音广告信号，不直接生成或持久化 URL 规则：

`PCM 音频 -> 现有 Sherpa-ONNX 识别 -> VoiceAdMatcher -> AdSignal -> 跳过策略 -> PlayerManager.seekTo`

语音信号与现有 HLS 规则应保持独立：

- HLS manifest、URL 和 WebView 规则继续作为主路径，结果更确定；
- 语音检测用于非 HLS、动态广告、规则未命中等兜底场景；
- 一次语音命中不能反推出某个 host 或切片规则，禁止自动写入 `UserAdRule`。

### 复用当前音频链路

首选方案不是再创建一个 ExoPlayer `AudioProcessor`，而是把现有 `RealtimeSubtitleAudioProcessor` 提升为通用音频 tap，向实时字幕和语音去广两个消费者分发同一份单声道样本。这样可以避免多次复制、重复重采样和音频链路重建冲突。

### 首版安全策略

建议首版限制：

- 仅 ExoPlayer；
- 仅点播，排除直播和不可 seek 媒体；
- 音频直通开启时禁用，因为处理器拿不到解码 PCM；
- 只启用精确匹配和规范化匹配；
- 默认基础跳过 15 秒，上限 30 秒；
- 默认“确认后跳过”或跳过后提供 5 至 10 秒撤销入口；
- 同一关键词 5 秒冷却，全局识别事件 800 ms 节流；
- 不保存原始音频，日志只记录脱敏关键词 ID、识别置信信息和跳转起止位置。

### 兼容性边界

- APK 的 PCM 注入点只覆盖 ExoPlayer，不能证明 MPV、IJK、VLC 等播放核心同样支持。
- 当前项目也应先明确标注 ExoPlayer 支持，其他核心需要单独提供 PCM tap 才能接入。
- 直播通常无法稳定相对 seek，应直接禁用。
- 低端电视需要测量识别 CPU、内存和音频缓冲抖动；模型未下载或加载失败必须完全降级，不影响正常播放。

## 不建议直接照搬的部分

1. 不直接复制混淆后的 APK 代码。其许可证和来源不明确，应按行为重新实现，并单独核对 Vosk 模型及依赖许可。
2. 不同时引入 Vosk 和 Sherpa-ONNX。当前项目已有可工作的中文 ASR 栈，重复依赖会扩大 APK、ABI、模型下载和故障面。
3. 不默认开启 70% 前缀模糊匹配。短关键词容易在正常对白中命中。
4. 不默认使用 4 倍跳过。15 秒基础值会放大到 60 秒，误跳代价过高。
5. 不把语音识别结果写成网络去广告规则，两者缺少可靠因果关系。

## 建议实施顺序

1. 先把现有实时字幕 PCM 管道抽成可多消费者订阅的音频 tap。
2. 新增纯 Java `VoiceAdMatcher` 和冷却状态机，覆盖精确匹配、规范化、节流、冷却和上限测试。
3. 复用中文 Sherpa-ONNX 最终识别结果，先做仅日志的观察模式。
4. 增加确认跳过和撤销操作，验证误报率后再提供显式自动模式。
5. 只在确认收益明显后考虑独立小模型或其他播放核心。

## 最终判断

星落 APK 证明了“解码音频语音识别 + 关键词命中 + 相对 seek”在 FongMi TV 架构中可行。对当前项目而言，其**产品机制和控制策略有高参考价值，底层 Vosk 实现的直接复用价值较低**。由于当前项目已具备更完整的音频 ASR 基础设施，这项能力适合作为现有智能去广告的第二检测通道，预计实现难度为中等，主要风险不是技术接入，而是误识别造成的内容误跳。
