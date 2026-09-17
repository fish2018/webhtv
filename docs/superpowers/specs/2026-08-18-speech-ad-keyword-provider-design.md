# 独立语音广告关键词 Provider 设计

## 状态

已选择方案 A，等待书面设计审阅。

## 日期

2026-08-18

## 背景

早期参考项目具备一条独立的语音广告检测通道：播放器输出 PCM，Sherpa-ONNX 将语音转写为文本，关键词匹配后产生广告信号，再按配置确认或自动跳过固定时长。当前代码已经具备以下基础设施：

- `PlaybackMediaSignalHub`、会话 `sessionId/generation` 和 PCM 管线租约；
- `RealtimeSubtitleRecognizer`、16 kHz 重采样、VAD、在线/离线 Sherpa-ONNX 识别；
- `AdAudioSignalProvider`、`AdAudioDetectionMultiplexer`、`AdSkipPolicyController`；
- `AdSkipCoordinator` 的时钟校验、seek、撤销窗口和异常隔离；
- PCM 音频指纹 Provider 与可选 Probe Provider。

当前缺失的是把“语音识别结果 + 用户关键词”作为第三个独立 Provider 接入现有音频去广告运行时，以及对应的共享配置和 TV/Mobile 设置界面。

## 目标

1. 新增独立 `SpeechAdSignalProvider`，与 PCM 指纹、Probe Provider 并行运行。
2. 复用现有 Sherpa-ONNX 识别模型和共享 PCM 管线，不创建第二条物理音频捕获管线。
3. 支持用户配置：启用状态、广告关键词、跳过时长、确认/自动模式。
4. 默认关闭语音去广；启用后默认使用“弹窗确认后跳过”。
5. 命中后统一经 `AdSkipPolicyController` 和 `AdSkipCoordinator` 执行，不允许 Provider 直接 seek。
6. 保留当前音频指纹和 Probe 行为，不因语音 Provider 的配置改变其策略。
7. 对 seek、切源、换集、flush、引擎重建、模型异常和延迟回调进行代际隔离。
8. 在 Leanback 与 Mobile 设置页提供一致的共享配置。

## 非目标

- 不使用 URL 广告正则作为语音关键词。
- 不将识别文本自动转换成 `UserAdRule`、HLS 规则或音频指纹规则。
- 不自动下载语音模型；沿用现有实时字幕模型管理和校验能力。
- 不支持 IJK、MPV、直播、不可 seek 或时长未知的媒体。
- 不保存 PCM、完整识别文本、媒体 URL、Cookie、Authorization 或账号信息。
- 不在首期提供云端识别、关键词订阅或远程关键词同步。
- 不修改已验证的音频指纹 JSON、签名规则包和 Probe sidecar 格式。

## 方案选择

### 采用：独立语音广告 Provider

新增 `SpeechAdSignalProvider implements AdAudioSignalProvider`。它从共享 `PlaybackMediaSignalHub` 获取 PCM，通过可注入的语音识别会话获得文本，使用不可变关键词快照匹配，并输出标准 `AdAudioCandidate`。

### 未采用：把广告匹配嵌入实时字幕 Controller

该方案会让字幕显示状态、翻译、模型切换和广告 seek 互相影响。字幕关闭时也可能错误关闭广告识别；广告 Provider 的异常还可能污染字幕生命周期。

### 未采用：复制一套 PCM 和 Sherpa 运行时

重复管线会增加 JNI、模型内存、线程、重采样和生命周期成本，并破坏当前“一个物理 PCM 管线，多个隔离 consumer”的架构。

## 用户配置

### `SpeechAdSetting`

新增独立配置类，避免把语音关键词语义塞进 `AdAudioSetting`：

- `speech_ad_enabled`：布尔值，默认 `false`；
- `speech_ad_keywords`：规范化后的用户关键词文本；
- `speech_ad_skip_seconds`：整数，默认 `15`，范围 `1..120`；
- `speech_ad_skip_mode`：`PROMPT` 或 `AUTO`，默认 `PROMPT`。

配置读取必须容错：未知模式回退到 `PROMPT`，非法时长钳制到安全范围，空关键词集合使 Provider 保持 `IDLE/DEGRADED`，不得启动识别。

### 默认关键词

首次使用显示早期参考项目的小型本地默认集合，用户可完整编辑或清空：

```text
麻将来了,澳门,赌场,娱乐城,荷官,百家乐,老虎机,时时彩,六合彩,彩票,下注,投注,首充,提现,棋牌,捕鱼,斗地主
```

默认集合只作为用户可见的本地初始值，不从网络更新，不扩展为大型敏感词库。

### 关键词规范化

新增不可变 `SpeechAdKeywordSet`：

1. 支持半角逗号、中文逗号、分号、中文分号和换行分隔；
2. Unicode NFKC 规范化；
3. `Locale.ROOT` 小写化；
4. 去除首尾空白并去重，保持首次出现顺序；
5. 限制单条关键词长度、总数量和总字符数；
6. 中文关键词采用规范化子串匹配；
7. 仅由 ASCII 字母或数字构成的关键词采用词边界匹配，避免 `ad` 命中 `download`；
8. 空文本、纯标点和超过上限的输入被忽略或拒绝，不得拖慢识别线程。

## 设置界面

### Leanback

在“设置 → 增强功能”中新增或恢复以下焦点项：

1. `AI 语音去广`：点击启用/关闭；
2. `广告关键词`：打开多行文本编辑对话框；
3. `AI 跳过时长`：循环或数字对话框设置 `1..120` 秒；
4. `语音跳过方式`：在“确认后跳过 / 自动跳过”之间切换。

焦点顺序必须可由 DPAD 完整到达，列表滚动后仍可看到当前焦点。状态文字示例：

```text
关闭
启用 · 确认 · 15 秒
启用 · 自动 · 15 秒
启用 · 模型未就绪
```

### Mobile

在增强设置 Fragment 中提供同样四项，复用同一 `SpeechAdSetting` 和字符串资源。关键词编辑使用多行输入，保存前展示规范化后的摘要。

### 运行时通知

任何配置保存后发送进程内配置变更事件：

- 开关或关键词变化：重建/重置语音 Provider；
- 跳过模式变化：实时更新语音 Provider 的策略，不重放历史候选；
- 跳过时长变化：只影响后续候选；
- 当前已经展示的提示保持原始目标，不因配置变化突然自动 seek。

## 语音识别复用边界

### 公共识别门面

`RealtimeSubtitleRecognizer` 当前属于字幕实现细节。新增窄接口，避免 `ad.audio` 直接依赖字幕 UI：

```java
interface SpeechRecognitionSession extends AutoCloseable {
    void accept(PlaybackMediaSignalHub.AudioFrame frame);
    void reset(long mediaAnchorUs, int timelineToken);
}

interface SpeechRecognitionFactory {
    SpeechRecognitionSession create(ResultListener listener);
}
```

默认工厂在字幕/语音基础设施包中包装现有 `RealtimeSubtitleRecognizer`。实时字幕与语音广告分别持有独立的识别会话和队列，但共享同一物理 PCM 来源；首期不共享同一个解码器实例，以保持错误和生命周期隔离。

测试通过注入 fake factory，不依赖 JNI 或真实模型。

### 模型条件

Provider 启动前通过现有模型目录、模型规格和校验器确认模型可用。模型未就绪时：

- Provider 状态为 `DEGRADED`；
- 上报固定低基数 `RULES_UNAVAILABLE/START_FAILED` 类错误；
- 不影响 PCM 指纹、Probe、字幕和正常播放；
- UI 显示“模型未就绪”，但不自动下载。

## `SpeechAdSignalProvider`

### 依赖

- `PlaybackMediaSignalHub`；
- `SpeechRecognitionFactory`；
- `SpeechAdConfigSource`，返回不可变配置快照；
- 工作线程/串行执行器；
- `AdAudioDiagnostics` 或独立低基数诊断计数器。

### 启动条件

只有以下条件全部满足才进入 `RUNNING`：

- Provider 已启用；
- 会话为 Exo VOD；
- `HostPosition.seekable == true`；
- `HostPosition.live == false`；
- 时长已知；
- 关键词非空；
- 模型已验证；
- Hub 捕获租约申请成功。

条件暂不满足时保持 `IDLE` 或 `DEGRADED`，不得创建无意义的识别线程。

### 识别与匹配

1. Hub 回调只复制有界 PCM 帧并投递到 Provider 邮箱，不在音频线程做识别；
2. 识别会话输出 `text/startUs/endUs/timelineToken`；
3. Provider 检查实例 token、session、generation 和 timeline token；
4. 规范化文本，使用当前不可变关键词快照匹配；
5. 同一关键词和相邻识别片段进入冷却窗口，避免在线 partial 与离线 final 重复触发；
6. 命中后基于最新 `HostPosition` 构造候选，而不是盲信识别线程中的旧播放位置。

### 候选映射

语音命中映射为标准 `AdAudioCandidate`：

- `providerId = "speech"`；
- `ruleId = "speech-keyword"`，不把完整识别文本或具体关键词写入候选/日志；
- `ruleVersion` 使用当前运行时规则版本，以通过既有 multiplexer/policy 代际校验；
- `startMs` 为命中时经时钟校验的当前播放位置；
- `endMs = min(durationMs, startMs + skipSeconds * 1000)`；
- `fullMatch = true`；
- `similarity = 1.0`。

若 `endMs <= startMs`、接近媒体末尾或位置已经过期，则丢弃候选。

### 冷却与容量

- 默认命中冷却为 30 秒；
- 邮箱、语音段队列和待匹配文本均有固定容量；
- 超限采用丢旧保新并记录固定计数；
- 不记录原始文本和关键词正文。

## Provider 组合

`AdAudioRuntimeController` 当前组合 PCM 与 Probe。扩展为三 Provider：

```text
PcmAdAudioSignalProvider
ProbeAdAudioSignalProvider
SpeechAdSignalProvider
        ↓
AdAudioDetectionMultiplexer
        ↓
Provider-aware policy routing
        ↓
AdSkipCoordinator
```

任意 Provider 创建或运行失败只关闭自身；其他 Provider 继续运行。

## 按 Provider 路由跳过模式

当前 `AdSkipPolicyController` 只有全局 `Mode`。直接复用会导致用户切换“语音自动跳过”时同时改变音频指纹和 Probe 的行为，这是不可接受的。

将策略扩展为按 `providerId` 解析模式：

```java
interface ModeResolver {
    Mode modeFor(String providerId);
}
```

- `speech` 使用 `SpeechAdSetting.skipMode()`；
- `pcm`、`probe` 保持当前 `AdAudioSetting.isAutoSkipEnabled()` 行为；
- 未知 Provider 默认 `PROMPT`；
- 模式切换只影响后续新候选；
- 已提示、已忽略或已自动执行的候选不重新分配。

此改动保持 Coordinator 为唯一 seek authority，不创建第二个 Coordinator。

## 生命周期与代际

### start

Runtime 创建语音 Provider，传入当前 `SessionContext`、运行时规则版本和 listener。Provider 注册 Hub consumer、申请捕获租约，并在条件满足时创建识别会话。

### seek / source changed / audio flush / engine rebuild

- 立即提升或接受新的 generation/timeline token；
- 清空识别队列、partial 文本、冷却状态和旧 HostPosition；
- reset 或释放旧识别会话；
- 旧实例回调必须因实例 token/session/generation/timeline token 不匹配而丢弃；
- 向 multiplexer/policy 转发标准 timeline reset。

### 配置变化

关键词/启用状态变化通过 Provider 实例 token 或 provider generation 隔离旧回调。即使 `ruleVersion` 未变化，旧 Provider 在关闭后也不能再发出候选。

### close

按以下顺序释放：

1. 标记 closed 并提升实例 token；
2. 注销 Hub consumer；
3. 释放捕获租约；
4. 清空有界队列；
5. 关闭识别会话和执行器；
6. 丢弃延迟回调。

## 播放策略与用户体验

### 默认确认模式

命中后进入现有提示流程。用户确认时再次校验：

- session/generation；
- 当前媒体非直播且可 seek；
- 当前播放位置与候选仍在允许窗口；
- 目标位置经过 duration 钳制。

成功 seek 后保留现有 5 秒撤销能力。

### 自动模式

候选经 provider-aware policy 路由到 `onAutoCandidate`，仍走 Coordinator 的相同校验、seek 和撤销流程。Provider 本身无权调用播放器。

### 与现有提示的兼容

语音候选使用通用“检测到疑似语音广告”文案，不展示哈希、完整转写或敏感关键词。音频指纹候选继续显示现有规则语义。

## 故障隔离与可观测性

新增固定低基数计数：

- provider 启动/停止；
- 模型未就绪；
- 识别会话创建失败；
- PCM 邮箱淘汰；
- 识别文本为空；
- 关键词命中；
- 冷却抑制；
- 旧代际回调；
- 非法/过期位置；
- 候选发出；
- 下游 listener 异常。

禁止记录：PCM、完整识别文本、关键词列表、URL、header、Cookie、Authorization、用户标识。

## TDD 与测试设计

### 配置与关键词单元测试

- 默认关闭、默认 `PROMPT`、默认 15 秒；
- 时长 `1..120` 钳制；
- 中英文分隔符、NFKC、大小写、去重；
- 中文子串和 ASCII 词边界；
- 空输入、超长关键词、容量上限；
- 未知模式回退 `PROMPT`。

### Provider 单元测试

使用 fake Hub、fake recognizer 和 fake clock：

- 未启用/无关键词/模型未就绪不启动；
- 支持场景启动并请求捕获租约；
- 文本未命中不发候选；
- 命中映射正确的 providerId、区间和版本；
- 跳过时长在媒体尾部钳制；
- 冷却抑制 partial/final 重复；
- seek/reset/切源后旧回调丢弃；
- 队列溢出、识别器异常、listener 异常隔离；
- close 幂等并释放 consumer/lease。

### Policy 测试

- `speech` 默认进入 prompt；
- speech AUTO 只使后续 speech 候选自动执行；
- speech 模式切换不改变 pcm/probe；
- 未知 Provider 默认 prompt；
- 历史候选不因模式切换重放。

### Runtime 组合测试

- 三 Provider 同时启动并经 mux 去重；
- speech 失败不停止 pcm/probe；
- pcm/probe 失败不停止 speech；
- 设置变化重建 speech，旧实例回调被拒绝；
- Runtime reset 同步三 Provider、mux、policy 和 coordinator。

### UI 测试

- Leanback 四个设置项可由 DPAD 到达并保存；
- Mobile 四个设置项展示一致；
- 默认状态、确认/自动文案和时长显示；
- 关键词编辑取消不写入，确认后规范化保存；
- 模型未就绪状态可见且不崩溃。

### 集成与模拟器验证

1. 构建并安装 Leanback ARM64 Debug 到 `emulator-5560`；
2. 验证设置页焦点、配置写入和重启持久化；
3. 使用 fake/test recognizer 或可控音频 fixture 触发关键词；
4. 验证默认弹窗确认后 seek；
5. 切换自动模式，只对后续 speech 候选自动 seek；
6. 验证撤销、冷却、媒体尾部钳制、seek/切源后无旧回调；
7. 检查无 FATAL/ANR，最终恢复安全默认配置。

## 分阶段交付

### 阶段 1：纯逻辑与策略

- `SpeechAdSetting`；
- `SpeechAdKeywordSet`；
- provider-aware policy；
- 全部纯 JVM 红绿测试。

### 阶段 2：识别门面与 Provider

- 公共识别门面；
- `SpeechAdSignalProvider`；
- Hub 生命周期、代际和异常隔离测试。

### 阶段 3：Runtime 组合

- 三 Provider 组合；
- 配置热更新；
- Coordinator 提示/自动路径集成测试。

### 阶段 4：Leanback 与 Mobile UI

- 四个配置项；
- DPAD/触摸交互；
- 状态文案和模型状态。

### 阶段 5：完整回归与模拟器验证

- 音频模块测试；
- Leanback 全量测试；
- Mobile 编译；
- `emulator-5560` 真实入口验证。

## 验收标准

1. 默认关闭语音去广，默认跳过方式为弹窗确认。
2. 用户可以编辑关键词、设置 `1..120` 秒时长并选择确认/自动。
3. 可控识别结果命中关键词后，确认模式展示提示且不自动 seek。
4. 自动模式只对后续语音候选自动 seek，并提供撤销。
5. 语音策略不改变 PCM 指纹和 Probe 的确认/自动模式。
6. 三 Provider 共享一条物理 PCM 管线和一个 Coordinator。
7. seek、切源、flush 和重建后旧识别结果不能触发提示或 seek。
8. 模型缺失、识别异常和资源溢出不影响正常播放或其他 Provider。
9. 无敏感文本和媒体信息进入日志或诊断。
10. 自动化测试、Leanback/Mobile 构建及 `emulator-5560` 验证通过。

## 已知权衡

- 语音广告与实时字幕分别创建识别会话，模型内存可能增加；首期优先错误隔离，后续在有内存证据时再考虑共享解码器。
- 关键词子串匹配简单可解释，但无法理解复杂语义；通过默认确认模式、冷却和用户可编辑列表降低误跳风险。
- 自动模式存在误跳风险，因此不是默认值，并始终保留 Coordinator 校验和撤销能力。