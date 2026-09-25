# 音频频谱指纹 Phase 0-1 实施说明

> 状态：Phase 0 算法、Phase 1 本地运行时与签名规则包基础设施已实现
> 日期：2026-08-18
> 算法：`spectral-sequence-v2`

## 1. 本阶段目标

在不增加第二个 ExoPlayer 音频处理器的前提下，复用现有解码 PCM 链路，完成可独立验证的频谱指纹基础能力，并接入会话级共享媒体信号 Hub、本地规则导入、人工确认跳过与撤销。

首个可用版本默认关闭，不下载远程规则包、不自动跳过，也不改变现有 Sherpa-ONNX 实时字幕识别模型；只有 Exo 可 seek 的非直播点播允许展示候选并由用户确认跳转。

## 2. 已实现模块

| 模块 | 职责 |
|---|---|
| `AudioFingerprintConfig` | 采样率、窗口、hop、频带和 FFT 计算预算 |
| `SpectralFingerprint` | 单声道化、重采样、Hann、FFT、16 频带 32-bit hash 和四相位变体 |
| `StreamingPcmResampler` | 跨 PCM chunk 保留有理数重采样相位，保证分块不改变采样结果 |
| `AudioFingerprintRule` / `RuleSet` | 不可变规则、锚点、变体、数量和长度边界 |
| `AudioFingerprintRuleCodec` | schema v2 严格解析、流式预检、深度/token/规则数/2 MiB 限额和受限序列化 |
| `AudioFingerprintMatcher` | 前缀/完整匹配、短规则同帧确认、时间线 reset、冷却和流尾 `finish()` |
| `PlaybackMediaSignalHub` / `PlaybackMediaClock` | 会话、generation、共享 PCM、输出时钟、独立消费者邮箱与捕获租约 |
| `PlaybackMediaAudioPipeline` | Exo 单一 PCM 生产管线；独占 PipelineLease 隔离旧引擎和新引擎 |
| `RealtimeSubtitleMediaConsumer` | 将实时字幕迁移为 Hub consumer，保留原 Sherpa-ONNX 行为 |
| `AdAudioRuleStore` / `AdAudioRuntimeController` | 2 MiB 本地原子规则导入、运行时启停、matcher 热重载与 Exo VOD 资格校验 |
| `SignedRulePackageCodec` / `SignedRulePackageVerifier` | 严格签名信封、SDK v2 payload 规范化、Tink RAW Ed25519、有效期和可信密钥生命周期校验 |
| `SignedRulePackageStore` | revision 高水位、current/previous 双槽、`fsync` 原子替换、逐次重新验签和损坏恢复 |
| `SignedRulePackageSource` / `PrioritizedAdAudioRuleSource` | 已验证缓存到运行时快照的映射，以及本地有效规则优先、整包回退且不合并规则集 |
| `AdSkipCoordinator` / `AdSkipPromptPresenter` | START/FULL 候选、人工确认、ignore、5 秒撤销及 Activity 生命周期隔离 |
| `AdAudioDiagnostics` | 固定枚举的本地计数快照，不记录 URL、规则内容或用户标识 |

## 3. 关键契约

- 标准算法配置：16 kHz、512 ms 窗口、256 ms hop、16 频带。
- 指纹规则主序列最少 4 帧、最多 4096 帧，最多 4 个变体；规则包最多 2048 条。
- 默认匹配使用 6 帧前缀、7 bit 汉明距离、0.84 前缀比例和 0.78 完整比例。
- 4～6 帧短规则在完整序列到达时同帧发出 `START_MATCHED` 与 `FULL_MATCHED`。
- PCM 时间线跳变或输入格式改变会重置内部状态；即使新块同时命中，`RESET` 状态仍可观察且事件不会丢失。
- `finish()` 结算需要尾部插值的最后一个样本，再重置 matcher；consumer 在生命周期 reset/close 时结算并清理。
- PCM 帧携带 `sessionId`、`generation` 和 `captureStartTimeMs`；seek、换源、音频 flush、引擎重建和释放均可观察。
- PipelineLease 只允许最新物理音频管线发布 PCM、重置时间线或写入时钟；最近一次 audio flush 绑定的 generation 不匹配时拒绝旧流样本。
- 任一 Hub consumer 的 `RuntimeException` 不得中断音频透传或阻塞其他消费者。

## 4. 安全与资源边界

- 输入 JSON 最多 2 MiB、嵌套最多 64 层、最多 300,000 token；解析前流式检查规则数组，在第 2049 条立即拒绝。
- 输出 JSON 使用受限 `JsonWriter` 边写边计数，不创建超限字符串，也不依赖可能误判的估算值。
- 单 PCM chunk 最多 1,000,000 个交错 `short` 样本、30 秒输入和 1,000,000 个目标帧。
- 指纹配置限制 FFT 不超过 32768 点，且 `FFT 点数 × 每秒变换次数` 不超过 131072。
- matcher 历史使用原始 `int[]` 环形缓冲；FFT 工作区复用，避免播放期间逐 hop 分配大型数组。
- 空规则集直接返回，不执行 downmix、重采样或 FFT。
- 签名信封严格限制字节数、JSON 深度/token/对象成员、canonical base64url 和字段长度；
  签名覆盖 packageId、revision、时间、keyId、algorithm 与 payload SHA-256。
- 安装只接受当前有效的 ACTIVE 密钥；缓存重载允许创建时有效的 ACTIVE/RETIRED 密钥，
  REVOKED、过期、未来包和同 revision 不同摘要全部拒绝。
- 下载规则缓存保留 revision 高水位；清除 current/previous 不会降低回滚屏障。

## 5. 兼容性与验证

- 固定 SDK 黄金 PCM 向量用于验证 32-bit hash 序列。
- 覆盖 48 kHz 立体声、16 kHz 单声道、44.1 kHz 跨 chunk 重采样、精确窗口边界和流尾插值。
- 覆盖非法规则、重复 ID、超深/超宽 JSON、超限序列化、NaN 阈值、超大 PCM、时间线 reset 和 Consumer 异常。
- Leanback ARM64 debug 全量单元测试与 Java 编译作为本阶段门禁。

## 6. Phase 1 运行时行为

- 设置入口位于增强设置页，短按启停，长按导入或清空本地 JSON 规则。
- 规则持久化为应用私有目录 `files/ad-audio-rules.json`；先严格解析并规范化，再以临时文件、`fsync` 和原子移动替换。
- 开关默认关闭；无规则、规则错误、非 Exo、直播、不可 seek、时长未知或 UI 未绑定时均不申请 PCM 捕获。
- `START_MATCHED` 显示“疑似广告”，`FULL_MATCHED` 可升级当前提示；没有用户确认不得 seek。
- 确认前再次校验 session/generation、时钟新鲜度、直播和 seek 能力；seek 后 generation 变化仍保留 5 秒撤销窗口。
- 规则热重载会关闭旧 consumer 和旧捕获租约，清理旧提示，再用当前会话创建新 matcher。
- 本地诊断仅维护 `QUEUE_OVERFLOW`、`STALE_GENERATION`、`CLOCK_UNAVAILABLE`、`RULE_LOAD_FAILED`、`MATCHER_ERROR`、`SEEK_REJECTED` 固定计数。

## 7. 后续阶段

以下内容保持暂缓，必须单独设计和验收：

1. 生产公钥配置、轮换/撤销发布流程和固定 HTTPS 规则包更新器；
2. 作者端规则质量流水线、审核发布和真实样本回归；
3. H5 采集端与 Android/JVM/H5 三端兼容 oracle；
4. 自动跳过策略、每规则信任级别和可回滚的用户授权；
5. 大型规则库的候选索引与低端设备性能基准；
6. IJK/MPV PCM 生产适配和跨内核一致性验证。
