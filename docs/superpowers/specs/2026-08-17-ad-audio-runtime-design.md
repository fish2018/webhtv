# 音频指纹去广告运行时设计

## 状态

Accepted，Phase 1 本地运行时已实现，后续扩展按本文边界进行。

## 日期

2026-08-17

## 背景

Phase 0 已完成频谱指纹算法、流式重采样、规则编解码和匹配器；Phase 1 已完成 `PlaybackMediaSignalHub`、`PlaybackMediaClock`、本地规则运行时和人工确认跳过闭环：

- PCM 由每个 PlayerManager 会话唯一的 Exo producer 采集，字幕与指纹仅作为隔离 consumer；
- Matcher 的结果已经接入播放器生命周期、媒体时间映射、人工确认和撤销入口；
- 规则包加载、后台计算背压、seek/换源后的结果失效和 UI 撤销语义均已定义并测试；
- 如果直接把去广告逻辑挂到 `RealtimeSubtitleController`，首版虽然改动小，但未来会让字幕、广告、语音识别等消费者相互耦合。

用户确认首版交互：命中疑似广告后先提示，用户确认才跳转；默认不自动跳过；支持关闭/忽略和跳转后的撤销。

## 目标

1. 建立播放器会话级、强类型的媒体信号中心，使 PCM、媒体时钟和生命周期事件只有一个权威来源。
2. 让实时字幕、音频指纹以及未来的其他识别器成为相互隔离的消费者。
3. 在不阻塞音频输出线程的前提下，提供稳定的确认跳转、忽略和撤销行为。
4. 为 Exo、IJK、MPV 等不同内核保留 producer 适配器边界；当前只实现 Exo，其他内核不申请指纹跳过。
5. 为规则包、匹配事件和播放器命令定义可扩展且向后兼容的接口。

## 非目标

- 本阶段不实现远程规则下载、签名校验、社区规则分发或 H5 同步；
- 本阶段不改变 `AudioFingerprintMatcher` 的 spectral-sequence-v2 算法和规则 JSON 格式；
- 本阶段不对直播、不可 seek 媒体或音频直通执行跳转；
- 本阶段不要求 MPV/IJK 立即提供 PCM producer，只保留接口和适配位置。

## 决策

采用“会话级强类型媒体信号中心”（方案 3R），不采用通用全局 EventBus。

```text
Exo / IJK / MPV producer
          │
          ▼
PlaybackMediaSignalHub  (每次播放器会话一个实例)
  ├─ PcmFrame（PCM、采样率、声道、captureTimeMs）
  ├─ MediaClock（captureTimeMs ↔ mediaPositionMs）
  ├─ SessionLifecycle（flush、seek、source、release）
  └─ ConsumerRegistration（强类型、有界、隔离）
       │                    │
       ▼                    ▼
RealtimeSubtitleConsumer  AdAudioConsumer
                               │
                               ▼
                       AdSkipCoordinator
                               │
                               ▼
                       PlaybackCommandPort/UI
```

### 为什么不是原始方案三

通用事件总线会隐藏事件顺序、线程、生命周期和错误语义，容易出现旧会话事件污染新会话、消费者异常影响其他模块、字符串事件无法安全演进等问题。3R 只允许固定的领域接口和显式注册关系：

- Hub 的所有事件带 `sessionId` 和递增 `generation`；
- consumer 注册返回可关闭的 `Registration`，关闭即停止收到后续事件；
- Hub 对每个 consumer 使用独立有界队列和异常隔离；
- producer 只能写入 PCM/时钟/生命周期，不能调用 UI 或业务 seek；
- UI 只能通过 `PlaybackCommandPort` 发出已校验的媒体命令。

## 核心接口契约

以下为 Java 形态的契约草案，实际包名以实现阶段为准。

```java
public interface PlaybackMediaSignalHub {
    SessionId sessionId();
    MediaClockSnapshot clockSnapshot();
    Registration register(ConsumerSpec spec, MediaSignalConsumer consumer);
    void publishPcm(PcmFrame frame);       // 仅 producer 线程调用
    void publishClock(MediaClockSample sample);
    void publishLifecycle(SessionLifecycle event);
    void close();
}

public interface MediaSignalConsumer {
    void onPcm(PcmFrame frame);
    void onClock(MediaClockSnapshot snapshot);
    void onLifecycle(SessionLifecycle event);
    default void onError(Throwable error) {}
}

public interface Registration extends AutoCloseable {
    boolean isClosed();
    @Override void close();
}

public record PcmFrame(
        SessionId sessionId,
        long generation,
        float[] monoSamples,
        int sampleRate,
        long captureStartTimeMs) {}

public sealed interface SessionLifecycle
        permits SessionStarted, TimelineReset, AudioFlush, SourceChanged, SessionReleased {}
```

### 接口约束

- `PcmFrame.monoSamples` 为只读所有权；consumer 不得修改或长期持有，Hub 在需要异步处理时复制到自己的受限缓冲区。
- `captureStartTimeMs` 使用单调递增的会话相对时间；媒体位置映射由 `MediaClock` 完成，不允许 consumer 自行猜测时间偏移。
- 所有跨线程回调都必须定义线程和顺序；同一 consumer 内按 `generation` 和源顺序交付，跨 consumer 不保证顺序。
- 接口新增字段只能以可选/默认方式扩展；不得改变已有字段单位或语义。

## 生命周期与时间线

### 会话创建

1. `PlayerManager` 创建新的 `PlaybackMediaSignalHub`，生成不可复用的 `SessionId`。
2. Exo audio processor/output provider 作为 producer 注册。
3. 字幕和指纹控制器按需注册 consumer。
4. Hub 发布 `SessionStarted`，generation 从 0 开始。

### seek、切源、换集和 flush

任何会导致 PCM 与媒体时间不连续的事件都执行同一顺序：

1. producer 停止发布旧 generation 的 PCM；
2. Hub 原子递增 generation；
3. 发布 `TimelineReset`，携带原因和新的媒体锚点；
4. 清空各 consumer 的有界队列；
5. 重新发布时钟样本后恢复 PCM。

旧 generation 的事件即使已经在后台队列中，也必须在 consumer 入口丢弃。这样可以避免 seek 前的匹配结果在 seek 后弹窗。

### 释放

`PlayerManager` 释放 session 时先发布 `SessionReleased`，随后关闭全部 registration、停止 worker、清空 UI pending prompt，最后关闭 Hub。任何 late callback 都只能被丢弃，不能重新触发播放器操作。

## 音频时钟映射

现有 `RealtimeSubtitleAudioOutputProvider.ClockSink` 已能观测 written/output position。3R 将该能力抽象成 `MediaClock`：

```java
public interface MediaClock {
    OptionalLong mapCaptureToMediaMs(long captureTimeMs);
    OptionalLong currentMediaPositionMs();
    boolean isPlaying();
    void reset(long generation, long mediaAnchorMs);
}
```

`AdSkipCoordinator` 只接受 `MediaClock` 的映射结果：

- START 事件的起点和终点都必须可映射，才能显示确认框；
- 映射缺失、时钟过期、位置为负或目标超出媒体时长时，放弃本次提示并记录原因；
- 目标位置按 `[0, duration]` 夹紧，并再次确认播放器仍处于同一 `sessionId/generation`；
- 用户确认与实际 seek 之间若发生 discontinuity，确认无效，不执行旧目标。

## 音频指纹消费者

`AdAudioConsumer` 只做三件事：接收 PCM、在自己的 worker 中调用 `AudioFingerprintMatcher`、向 `AdSkipCoordinator` 提交候选事件。它不直接访问 `PlayerManager`、Activity 或 Android UI。

### 背压与资源

- PCM 入队操作必须是非阻塞的；队列容量固定，满载时丢弃最旧 PCM chunk，并产生限频诊断事件；
- FFT、规则编解码和磁盘读取不在 producer 线程执行；
- worker 只有一个顺序执行器，保证 matcher 时间线稳定；
- Hub 和 consumer 都提供 `close()`，释放时等待不超过固定超时，超时强制丢弃剩余任务；
- 空规则、关闭开关、非 Exo/不可 seek 媒体直接不注册 consumer。

### 事件语义

- `START_MATCHED`：疑似广告候选，包含规则 id、预计起止 capture time、置信度和 generation；触发确认 UI，但不自动 seek。
- `FULL_MATCHED`：同一候选的完整匹配确认；用于更新 UI 可信度和诊断，不绕过用户确认。
- 同一规则在 cooldown 内只保留一个 pending candidate；generation 变化立即失效。

## 规则仓库

定义 `AdAudioRuleRepository`，首版提供本地文件实现和测试内存实现：

```java
public interface AdAudioRuleRepository {
    RuleSnapshot load(SessionContext context);
}

public record RuleSnapshot(
        String sourceId,
        long version,
        AudioFingerprintRuleSet ruleSet,
        List<String> warnings) {}
```

- 默认从应用私有目录读取 `ad-audio-rules.json`；不存在或校验失败时返回空规则集和诊断信息；
- 继续使用 Phase 0 的 token、深度、规则数量、FFT budget 和 2 MiB 输出限制；
- 规则快照在一次 session 内不可变，更新规则需要在下一个 session 生效，避免 matcher 中途替换规则；
- 远程签名仓库未来只实现同一接口，不改 consumer 和 matcher。

## 播放命令与 UI

定义与播放器解耦的命令端口：

```java
public interface PlaybackCommandPort {
    PlaybackSnapshot snapshot(SessionId sessionId, long generation);
    CommandResult seekTo(SessionId sessionId, long generation, long mediaPositionMs);
    void postToMain(Runnable action);
    void showAdSkipPrompt(AdSkipPromptModel model, AdSkipPromptListener listener);
    void dismissAdSkipPrompt(SessionId sessionId);
}
```

`AdSkipCoordinator` 的状态机：

```text
IDLE
  └─ START_MATCHED + 时钟有效 → PROMPT_PENDING
       ├─ 用户关闭/忽略 → SUPPRESSED（本候选结束）
       ├─ generation 改变 → IDLE
       ├─ FULL_MATCHED → PROMPT_PENDING（提升可信度）
       └─ 用户确认 + 快照仍有效 → SEEKING
                              ├─ seek 成功 → UNDO_WINDOW
                              └─ seek 失败/过期 → IDLE
UNDO_WINDOW
       ├─ 用户撤销 → seek 回原位置 → IDLE
       └─ 超时/关闭 → IDLE
```

UI 约束：

- 确认框明确写“疑似广告”，显示预计跳过时长和规则来源；
- 默认焦点为“跳过”，但不会自动触发；
- “关闭/忽略”只抑制当前候选，不关闭全局功能；
- 跳转成功后显示有限时长的“撤销”入口；
- Activity 销毁、换集、seek 或切源时自动关闭所有提示；
- TV 和移动端共用状态机，分别提供布局和焦点策略。

## 故障隔离与可观测性

- Hub 捕获每个 consumer 的 `RuntimeException`，记录 consumer id、session、generation 和阶段后继续投递其他 consumer；
- matcher 错误只使指纹 consumer 进入本 session 的 disabled 状态，不影响字幕或音频输出；
- 诊断事件采用固定枚举：`QUEUE_OVERFLOW`、`STALE_GENERATION`、`CLOCK_UNAVAILABLE`、`RULE_LOAD_FAILED`、`MATCHER_ERROR`、`SEEK_REJECTED`；
- 记录计数器和最近一次错误，不打印 PCM、规则 hash 或用户媒体 URL；
- 首版提供 debug 日志和测试注入 sink，后续可接现有播放诊断系统。

## 迁移策略

### 阶段 A：抽取基础设施

1. 从 `RealtimeSubtitleController` 抽取会话级 Hub、MediaClock 和 lifecycle generation。
2. 将现有字幕处理器改成 Hub consumer，保持现有字幕开关、队列和输出行为不变。
3. Exo producer 改为只向 Hub 发布 PCM 和 clock sample。
4. 增加跨 consumer 异常隔离、flush 和 discontinuity 回归测试。

### 阶段 B：接入指纹运行时

1. 实现本地规则仓库和 `AdAudioConsumer`。
2. 实现 `AdSkipCoordinator` 与 `PlaybackCommandPort`。
3. 在设置页增加实验开关和规则状态展示，默认关闭。
4. 为 TV/移动端接入确认、忽略和撤销 UI。

### 阶段 C：扩展适配器

后续按需增加 IJK/MPV producer、远程签名规则仓库和 H5 规则导入；这些扩展不得改变 Hub consumer 契约。

## 测试策略

### 单元测试

- Hub 注册、注销、session/generation 丢弃和 consumer 异常隔离；
- 每个 consumer 的队列上限、丢弃策略和 close 超时；
- MediaClock 映射、过期、seek 重置和边界夹紧；
- `AdSkipCoordinator` 全状态机：START、FULL、关闭、确认、seek 失败、撤销、超时；
- 规则仓库空文件、非法 JSON、超限 JSON、版本切换。

### 集成测试

- Exo producer → Hub → 字幕 consumer 与指纹 consumer 同时消费同一 PCM，确认只 downmix/resample 一次；
- seek/换源/flush 紧邻匹配事件时，旧事件不得弹窗或 seek；
- 一个 consumer 抛异常时，音频输出和其他 consumer 仍继续；
- 目标播放器位置、媒体时长和 generation 不一致时拒绝 seek。

### UI/设备测试

- TV 遥控器焦点默认落在“跳过”，关闭和撤销可达；
- Activity 重建、后台、旋转/窗口切换不保留旧 prompt；
- 低内存、规则为空和时钟不可用时无误导性提示。

## 性能预算

- producer 线程额外工作仅限于封装 `PcmFrame` 和非阻塞入队；
- 指纹 matcher 单独 worker，不超过一个实例处理当前 session；
- Hub 不复制 PCM 给每个 consumer，只有异步 consumer 在自己的边界复制；
- 默认 PCM 队列不超过 16 个 chunk，规则和 FFT 预算沿用 Phase 0 上限；
- 所有诊断限频，避免播放期间日志和对象分配失控。

## 替代方案

### 直接复用 `RealtimeSubtitleController` 的 Tap

优点是改动最少，适合快速验证；拒绝原因是广告、字幕和音频时钟共享一个业务控制器，未来接入更多识别器会形成高扇出耦合，且难以独立演进。

### 增加第二条 Exo AudioProcessor

拒绝原因是重复 downmix/resample 和 FFT 前置工作，时间戳、flush、seek 和异常路径也会复制一套，稳定性低于共享 producer。

### 通用全局 EventBus

拒绝原因是事件类型、线程、顺序、订阅释放和错误隔离容易变成隐式约定，无法保证跨会话结果不会污染当前播放器。

## 验收标准

在进入提交/合入前必须满足：

1. 现有字幕相关全量测试不回归；
2. Hub、Clock、consumer、coordinator 和规则仓库新增测试全部通过；
3. Exo 点播中，检测到候选只出现一次确认提示，未确认不会跳转；
4. 确认后能跳到有效媒体位置，撤销能回到确认前位置；
5. seek、换集、切源、flush、Activity 销毁后旧候选全部失效；
6. 指纹 consumer 发生异常或队列满载时，音频输出和字幕仍可继续；
7. 空规则、直播、不可 seek 或时钟无效时不显示误导提示；
8. 文档、接口和测试与实现一起提交，且在可用版本完成前不合入 beta/dev。

## 实现对照（Phase 1）

- 实际 PCM 生产者为 `PlaybackMediaAudioPipeline`，以 `PipelineLease` 和最近一次 audio flush 的 generation 保护旧引擎回调。
- 实际规则存储为 `AdAudioRuleStore`，运行时由 `AdAudioRuntimeController` 管理；规则导入后会原子替换并重建活动 matcher。
- 实际播放器命令由 `PlayerManager.AdAudioPlaybackPort` 提供；它只允许 Exo、非直播、时长已知且可 seek 的点播。
- 实际 UI 由 `AdSkipPromptPresenter` 提供，Activity stop/disconnect/destroy 会解绑并使旧按钮回调失效。
- IJK、MPV、远程规则、H5 采集和自动跳过仍是后续扩展，不应被视为当前版本已支持的能力。
