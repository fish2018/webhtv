# 广告音频频谱指纹 SDK 评估与融合升级建议

评估日期：2026-08-17

分析对象：

- APK：`G:\下载\ad-audio-extractor-v0.9.0.apk`
- SHA-256：`086a20b72850403892052dfa815f4a59bdb91781300970c983d782c4e605c99d`
- 包名：`com.fongmi.ad.extractor`
- 版本：`0.9.0`（versionCode 9）
- 反编译工具：`G:\jadx-1.5.2`
- 当前项目：WebHTV / FongMi TV 分支

## 1. 结论

**建议结合升级，但应把它作为“已知广告的高置信音频指纹通道”，而不是替换现有 HLS 清理、用户规则或 Sherpa-ONNX 语音通道。**

这个 APK 的核心价值与当前项目的播放链路高度匹配：它直接读取 ExoPlayer 解码后的 PCM，不依赖麦克风，也不需要第二套识别模型。当前项目已经完成 `PlaybackMediaAudioPipeline`、`PlaybackMediaSignalHub` 和 `AdAudioConsumer`，同一条 PCM 链路可以隔离地服务实时字幕和频谱指纹，不再需要第二个独立音频处理器。

但 `0.9.0` 更像“采集和验证工具 + 可嵌入的核心 Java 算法”，还不是可直接用于大规模线上规则库的完整方案。它缺少签名规则包、版本回滚、来源治理、跨规则碰撞检测和大库索引；规则数量增加后，当前匹配器的逐规则扫描也会成为瓶颈。指纹一致只能说明在当前算法容差内相似，不能保证对变速、变调、插入静音、重新剪辑或强均衡处理仍然命中。

本轮使用同一 APK 原件重新反编译并复核：目标 SDK 共 16 个核心类，均未落入 JADX 的 3 个反编译失败类；有向关系图包含 183 个节点、356 条边和 10 个社区，缺失端点、悬空边、自环及同端点折叠均为 0。复核后的决策是：**保持协议兼容，但不把 APK 中的 SDK 二进制或反编译代码作为运行时依赖。** 当前独立实现已经覆盖 `spectral-sequence-v2`，并补齐流式重采样、时间线、输入预算、热更新、队列隔离和可观测性边界。

## 2. APK 实现证据

### 2.1 PCM 采集而非麦克风录音

Manifest 只有 `INTERNET`、`ACCESS_NETWORK_STATE` 等权限，没有 `RECORD_AUDIO`。采集器使用 `TapAudioProcessor extends BaseAudioProcessor` 接入 ExoPlayer 音频处理链：

1. 仅接受 16-bit PCM；
2. 读取当前解码的 PCM `ByteBuffer`；
3. 把输入复制回输出，保证播放音频不被采集器吞掉；
4. 将 PCM 交给采集会话做单声道平均和规则提取。

`AudioCaptureSession` 预分配采样缓冲区，采集时长限制为 1 秒至 120 秒。自动提取默认只取广告开始处最多 5 秒作为锚点，之后生成主序列和三个相位变体。

这与当前项目的 `PlaybackMediaAudioProcessor` 属于同一层级能力。当前实现已经通过 `PlaybackMediaSignalHub` 扇出，不应再安装 APK 中的 `TapAudioProcessor`，以免重复复制、重复 downmix 和产生第二套 flush/seek 时间线。

### 2.2 指纹算法

核心算法标识为 `spectral-sequence-v2`，默认参数为：

| 参数 | 值 |
|---|---:|
| 采样率 | 16,000 Hz |
| 窗长 | 512 ms |
| hop | 256 ms |
| 频带数 | 16 |
| hash | 32 bit，序列化为 8 位十六进制 |
| 相位变体 | 4 组，偏移约为 hop 的四分之一 |

每个窗口使用 Hann 窗、补零到 2 的幂次后做 FFT，再把 180 Hz 到最高约 6.2 kHz 的频段压成 16 个对数频带。hash 的低 16 位表示各频带相对全局均值的高低，高 16 位表示相邻频带的比较结果。匹配时使用两个 hash 的 Hamming distance。

它是“连续粗粒度频谱序列”，不是 Shazam 类的峰值地标或 constellation hash。优点是实现简单、纯 Java、离线可运行、容易移植到影视壳；缺点是对变速、变调、插入静音、不同剪辑点、强 EQ 和部分编码差异的鲁棒性有限。四个相位变体只解决窗口对齐偏移，不解决内容变换。

### 2.3 匹配和跳过时序

`AdAudioMatcher` 每个 hop 计算一个 hash，并对所有规则及其变体进行前缀匹配。默认 `MatcherConfig.conservative()` 为：

- 最少前缀 6 帧；
- 单帧最多 7 个 bit 不同；
- 前缀命中率 0.84；
- 完整序列命中率 0.78；
- 冷却时间 5 秒；
- PCM 时间线断裂超过 2.5 秒则重置。

默认配置的有效检测延迟约为 1.5 至 1.8 秒；`fastStart()` 使用 2 帧前缀和更严格的 5 bit 距离，延迟更低但更容易受到短片段偶然相似影响。命中前缀时立即产生 `START_MATCHED`，并根据规则的 `durationMs` 预测广告结束；序列完成后产生 `FULL_MATCHED`。因此实际跳过应使用 START 事件，FULL 事件主要用于校验、统计和确认规则质量。

`SafeMatcherWorker` 使用有界队列（默认容量 12，范围 2 至 64），队列满时清空待处理块并要求 matcher reset。这种退压方式能保护播放线程，但会造成当前广告片段丢失，运行时必须把 reset 作为可观测事件，而不能静默当成未命中。

### 2.4 规则格式和采集闭环

规则 JSON 的顶层结构为 `schemaVersion: 2`、`algorithm` 和 `rules`。单条规则包括：

- `id`；
- `durationMs`；
- `anchorOffsetMs`；
- `anchorDurationMs`；
- `fingerprint` 主序列；
- 可选的 `variants`。

构造器要求广告时长大于 1 秒、锚点范围不能越界、主序列至少 4 帧；但没有对每个 hash 的十六进制格式、序列最大长度和规则 ID 字符集做强校验。`RuleJsonCodec` 只接受算法 ID 精确等于 `spectral-sequence-v2`，逐条规则解析失败时会跳过该条，可能造成“包成功加载但规则数量少于预期”。

采集器在本地完成质量分析和规则测试，必须先观察 `START_MATCHED`，再确认 `FULL_MATCHED`，最后由用户显式确认后，通过 Android `ACTION_SEND` 分享 `application/json` 文件。APK 没有发现内置上传接口、服务端地址或指纹数据库；“开发者愿意接入”和“有足够指纹”确实是这个方案的主要覆盖瓶颈。

### 2.5 规则加载和质量检查的生产缺口

`RuleLoader` 使用 `HttpURLConnection` 读取 HTTP/HTTPS JSON，连接超时 5 秒、读取超时 8 秒、文件上限约 2 MiB；失败时依次回退缓存和内置规则。缓存是原始 JSON 文本，没有看到签名、ETag、过期时间、版本回滚或原子快照。`SharedPreferences` 存储也不适合长期承载大规模规则库。

`RuleQualityAnalyzer` 能检查锚点时长比例、零 hash 比例、hash 唯一率、前几帧是否全零和变体数量，分为 GOOD、REVIEW、REJECTED。但它是单规则检查，没有跨规则相似度、误命中回放集或来源审核。

APK 内未发现 native 库、WASM、预置指纹数据库或许可证文件。接入前应向发布者确认 SDK 源码、许可证、规则数据授权和 H5 版本的维护边界，不能仅凭 APK 的签名描述推断可直接复制或再分发。

## 3. 与当前项目的结合方式

### 3.1 三层信号定位

建议将去广链路分成三类信号，职责保持隔离：

```text
HLS manifest / URL
        -> 结构化清理（当前主路径）

ExoPlayer 解码 PCM
        -> PlaybackMediaAudioPipeline / PlaybackMediaSignalHub
             -> AdAudioConsumer（已知广告，高置信）
             -> VoiceAdSignal（未知广告，Sherpa-ONNX 语音回退）
        -> AdSkipCoordinator
        -> AdAudioRuntimeController / AdSkipPromptPresenter
        -> 确认、自动跳过、撤销、seek 边界保护
```

- HLS 清理继续处理可以在播放前确定的分片级广告；
- 频谱指纹只产生运行时信号，不反推 URL、host 或 HLS 规则；
- Sherpa-ONNX 继续处理没有指纹覆盖的动态广告；
- `AdSkipCoordinator` 负责候选去重、冷却、seek 校验和撤销；跨通道统一优先级仍属于后续扩展。

### 3.2 推荐的模块边界

| 模块 | 责任 | 不负责 |
|---|---|---|
| `PlaybackMediaAudioPipeline` / `PlaybackMediaSignalHub` | 从 ExoPlayer PCM 得到单声道样本，并向多个消费者隔离扇出 | seek、广告判断、持久化规则 |
| `AudioFingerprintMatcher` | 复用 `spectral-sequence-v2`，维护滚动 hash 和匹配状态 | UI、网络加载、UserAdRule |
| `AdAudioRuleStore` | 管理经过严格校验的本地 SDK v2 JSON；签名包、版本和回滚留给外层包源 | 运行时跳过 |
| `AdAudioConsumer` | 将 START/FULL 映射为带 session/generation 的候选，隔离 matcher 和播放线程 | 直接调用播放器 |
| `VoiceAdSignal` | 接收现有 Sherpa-ONNX 识别结果并生成语音信号 | 写入指纹库 |
| `AdSkipCoordinator` | 处理指纹候选去重、冷却、seek 状态和撤销 | 解析 HLS、下载规则 |
| `AdAudioRuntimeController` / `AdSkipPromptPresenter` | 管理启停、规则热更新、捕获租约、确认和撤销 UI | 规则采集和上传 |

指纹规则必须使用独立的 `FingerprintRuleSet`，不能塞进现有 `UserAdRule`。两者来源、算法、可信等级和失效策略不同；混用会让用户自定义 URL 规则承担不可验证的音频数据风险。

### 3.3 信号优先级和用户行为

首版建议采用以下行为：

1. 已签名、质量为 GOOD、且前缀命中率达到策略阈值的 `START_MATCHED` 作为高置信信号；
2. 默认仍显示“确认后跳过”，目标位置为 `matchStart + durationMs`，并沿用当前 seek 边界钳制和撤销入口；
3. 只有经过本地观察期和回放集验证后，用户才可以打开指纹自动跳过；
4. `FULL_MATCHED` 用于确认规则与实际音频一致、结束统计和质量反馈，不等待它才跳过；
5. 同一规则在冷却窗口内不重复触发；指纹 START 命中后，语音通道在相同时间窗内的提示应被抑制；
6. 语音信号没有指纹命中时继续作为未知广告的回退，不能因为指纹库覆盖不足而关闭语音检测。

首版仍只支持 ExoPlayer 点播、已知或可计算 duration、可 seek 媒体。直播、音频直通、无法得到 PCM 或无法相对 seek 的场景只记录信号，不执行跳过。

### 3.4 SDK 能力取舍

| APK / SDK 能力 | 当前映射 | 结论 |
|---|---|---|
| `SpectralFingerprint`、`AdAudioMatcher`、SDK v2 JSON | `SpectralFingerprint`、`AudioFingerprintMatcher`、`AudioFingerprintRuleCodec` | 保持协议兼容，继续使用当前加固实现 |
| `SafeMatcherWorker` | `AdAudioConsumer` + Hub consumer mailbox | 不引入；当前实现具备 generation、退压和诊断隔离 |
| `RuleStore`、`RuleLoader` | `AdAudioRuleStore`、`AdAudioRuntimeController.RuleSource` | 不直接复用；原实现缺少签名、revision、过期和原子回滚 |
| `RuleQualityAnalyzer` | 尚无生产等价物 | 吸收其思路，但放在采集端、CI 或服务端，不放在播放热路径 |
| 采集/边界编辑/本地回放测试 UI | 独立作者工具 | 保持外置；TV 客户端只消费审核后的规则包 |

当前 `sdkV2JsonRoundTrips`、频谱黄金向量和跨 chunk 流式测试已经锁定 Android/JVM 兼容契约。SDK 原版在短规则、分块重采样、输入上限和规则解析上没有同等级边界，因此“协议兼容”不等于“实现回退到原 SDK”。

## 4. 必须补齐的升级项

### 4.1 算法兼容和黄金样本

SDK 核心输入是 `short[]`，当前项目 tap 同时支持 PCM16 和 PCM float。接入时应统一为明确的 16-bit little-endian 语义，并固定：

- 声道下混顺序和整数/浮点舍入方式；
- 16 kHz 线性重采样实现；
- 窗长、hop、频带上限和 hash 字节序；
- PCM 时间戳的起点、切集 reset 和 seek 后清空策略。

应建立一组 Android、JVM 和 H5 共用的黄金 PCM 向量，要求同一向量产生完全一致的 hash 序列。没有这个向量测试，H5 采集端和影视壳 SDK 可能“看起来使用同一算法”，实际却因浮点和边界差异不能互相匹配。

当前 Android/JVM 侧已完成固定黄金 hash、SDK v2 JSON round-trip、跨 PCM chunk 重采样和 EOS 边界测试；仍缺发布者 H5 版本作为独立 oracle，以及真实广告在不同编码器、声道布局和响度下的回放集。因此下一步是补齐 H5/真实样本，不是再次移植 Java 算法。

### 4.2 规则包安全和治理

线上分发不应把 SDK v2 的 `schemaVersion` 直接改成 3，否则采集 APK、H5 和现有客户端都会失去 round-trip。应在不变的 v2 payload 外增加可签名分发信封：

```json
{
  "packageSchemaVersion": 1,
  "packageId": "cn.example.ad-fingerprint",
  "revision": 42,
  "createdAt": "2026-08-17T00:00:00Z",
  "expiresAt": "2026-09-17T00:00:00Z",
  "payload": {
    "schemaVersion": 2,
    "algorithm": {"id": "spectral-sequence-v2", "sampleRate": 16000, "windowMs": 512, "hopMs": 256, "bandCount": 16},
    "rules": []
  },
  "signature": {"keyId": "release-1", "algorithm": "Ed25519", "value": "..."}
}
```

签名不依赖 JSON 字段顺序或空白，而是覆盖带领域分隔的确定性二进制元数据和规范 v2 payload 的 SHA-256；精确格式见签名规则包设计规格。客户端需要校验 HTTPS、签名、keyId、revision 单调性、算法版本、包大小、规则数、单规则帧数和过期时间；验证成功后再把 payload 交给现有严格 codec。写入采用临时文件加原子替换，并保留上一份可恢复快照。规则包下载地址不能由普通用户输入，避免把 `RuleLoader` 变成任意 HTTP 请求入口。

服务端或 CI 应增加：

- 采集样本去重和同一广告多版本归并；
- 单规则质量检查；
- 跨规则前缀碰撞和误命中回放；
- 不同编码、音量、背景噪声和起始偏移的兼容测试；
- 来源、提交者、审核状态、启用状态和撤销记录。

### 4.3 大库匹配性能

当前 `AdAudioMatcher` 在每个 hop 遍历全部规则、每条规则的全部相位序列，再逐帧计算 Hamming distance，复杂度近似为 `O(规则数 × 变体数 × 前缀帧数)`。十几或几十条规则可以接受；当社区库达到数百至数千条规则时，播放线程之外的 matcher 线程仍会持续消耗 CPU，并增加队列积压概率。

建议分两步：

1. 首版限制客户端规则包规模，先测量低端电视盒的每 hop 耗时、队列深度和 reset 次数；
2. 规模化时把 hash 序列改为紧凑的 `int[]`/二进制包，并按首帧或前两帧建立候选桶，再对候选做 Hamming 容差匹配。不要把每帧都保留为 Java `String` 并在每次播放时全库扫描。

当前实现已经完成 `int[]` 内存表示、最多 2,048 条规则、每序列最多 4,096 帧以及 FFT/PCM/JSON 预算；尚未实现前缀候选桶，运行时复杂度仍是全规则扫描。候选索引应在真实库达到数百条、并拿到低端设备 profile 后再落地，避免过早固定错误索引结构。

## 5. 分阶段融合路线

### Phase 0：兼容性 Spike（已完成 Android/JVM 部分）

- 已实现独立 JVM 可测试的 `spectral-sequence-v2`、SDK v2 codec 和黄金 hash；
- 已覆盖 PCM16/float、跨 chunk 重采样、EOS、时间线 reset 和短规则；
- 待补发布者 H5 oracle、10 至 20 条真实广告和跨编码回放集。

### Phase 1：本地运行时（已完成）

- 已通过 `PlaybackMediaAudioPipeline` 和 Hub 共享 PCM，不增加第二处理链；
- 已增加 `AdAudioConsumer`、本地原子规则仓库、feature flag、诊断和规则热更新；
- 已隔离播放线程、字幕 consumer、matcher 异常、队列溢出和跨 generation 旧结果。

### Phase 2：确认后跳过（本地导入已完成，可信分发未完成）

- ExoPlayer 点播已支持候选提示、确认跳过、seek 钳制、不可 seek 降级和撤销；
- 默认关闭，空规则、直播、时钟无效或不具备跳转条件时不捕获或不提示；
- 待补签名包、GOOD 质量门槛、FULL_MATCHED 质量反馈和错误规则自动降级。

### Phase 3：受控自动跳过和社区闭环

- 建立规则提交、审核、碰撞测试、撤销和版本回滚服务；
- 采集 APK 与 H5 只作为提交端，客户端只下载签名包；
- 观察期达到足够样本后，允许用户单独开启指纹自动跳过；
- 以规则覆盖率、误跳率、漏检率和低端设备 CPU/队列指标决定扩容。

### Phase 4：大库优化

- 引入前缀索引/候选桶和紧凑二进制规则包；
- 评估按语言、地区、站点或内容源拆分规则包；
- 对规则包做按需加载，避免每台设备常驻完整社区库。

## 6. 验收建议

以下是上线前应设定的门槛，数值需用真实样本校准：

| 类别 | 验收项 |
|---|---|
| 算法 | Android/JVM/H5 对同一黄金 PCM 生成完全一致的 hash 序列 |
| 可靠性 | 正常播放、暂停、seek、切集、释放后 matcher 不重复触发或泄漏线程 |
| 精度 | 干净片段回放集的误触发率低于产品允许阈值；已知广告的漏检率单独统计 |
| 行为 | START 命中后目标位置正确，FULL 未到达时仍可确认跳过，seek 失败可恢复播放 |
| 退压 | 队列满、PCM 时间线断裂和规则包加载失败均有可观测降级，不影响主播放 |
| 安全 | 无签名、过期、超限、错误算法或回滚版本的规则包不会生效 |
| 规模 | 在目标低端设备上测得每 hop 耗时、常驻内存、队列峰值和 reset 次数 |

## 7. 最终建议

可以结合升级，推荐决策如下：

1. **复用现有 PCM + Sherpa-ONNX**，不引入 Vosk，也不增加第二个独立 PCM 处理器；
2. 继续使用当前独立且加固的兼容实现，不导入 APK 内的 `SafeMatcherWorker`、`RuleLoader` 或 SDK 二进制；
3. 指纹规则独立于 `UserAdRule`，下一优先级是签名信封、revision 防回滚、来源审核和上一个可用快照；
4. 把 `RuleQualityAnalyzer` 的单规则评分思路迁到采集端/CI，并增加跨规则前缀碰撞、负样本回放和人工审核；
5. 在指纹库扩大前优先补齐 H5 oracle、真实广告回放集和低端设备 profile；前缀索引由数据决定，不先做猜测性实现；
6. 在确认 SDK 源码和规则数据许可证前，不直接复制 APK 代码或将其作为项目依赖发布。

该方案已经进入现有智能去广路线并完成本地可用闭环。下一步不再是重复 Phase 0，而是建立 `SignedRulePackageSource`、作者端质量管线和 H5/真实样本 oracle；这些完成前保持本地导入、默认关闭和确认后跳过，不开放社区包自动更新或自动跳过。
