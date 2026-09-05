# 后台前视音频识别与双轨规则设计

## 状态

已批准，进入第一阶段实现。

## 日期

2026-08-18

## 背景

项目当前已经具备 PCM 音频采集、`spectral-sequence-v2` matcher、签名规则包和提示后跳过流程。参考项目 [m3u8-ad-audio-probe](https://github.com/0o755/m3u8-ad-audio-probe) 提供独立的后台前视解码与 `SkipRequest`，而 [m3u8-ad-audio-collector](https://github.com/0o755/m3u8-ad-audio-collector) 验证了提示跳过和自动快进两种宿主体验。

外部 Probe 当前仍是预发布 API，使用 `spectral-sequence-v1` 规则；项目现有生产规则是带 Ed25519 信任链的 v2。因此需要把 Probe 作为可替换的信号提供者，而不是让播放器业务代码直接依赖 AAR 或不受信任的远程规则。

## 目标

1. 保留现有 PCM matcher，增加可选的 Probe 前视 Provider。
2. 让两个 Provider 的候选进入同一个去重、排序和跳转校验链路。
3. 支持播放中实时切换“提示”与“自动跳过”。
4. 为 Probe 生成独立签名、且与签名 v2 强绑定的 v1 sidecar artifact，运行时只消费已验签并成功配对的 sidecar。
5. AAR 缺失、解码失败、规则不兼容或时间线不确定时 fail-open，不影响正常播放和字幕音频线程。
6. 为未来替换 AAR、接入 H5/本地解码器和扩展规则算法保留稳定接口。

## 非目标

- 第一阶段不直接把预发布 AAR 固定到播放器主模块。
- 不在播放运行时把 v2 hash 近似转换成 v1 hash。
- 不直接信任 Probe 自己下载的 HTTPS 规则，也不开放运行时任意规则 URL。
- 不在本阶段处理直播滑窗、DRM、个性化 SSAI 的确定性跳过。
- 不修改宿主播放器的底层 seek 实现；所有跳转仍由 `AdSkipCoordinator` 执行。

## 方案选择

### 方案 A：Probe 与 PCM matcher 并行（采用）

两个 Provider 共享会话和规则 revision，输出统一候选，由 multiplexer 去重。Probe 失效时 PCM matcher 仍可用，适合预发布依赖和逐步灰度。

### 方案 B：Probe 完全替代 PCM matcher

接入简单，但把播放稳定性绑定到预发布 AAR、外部解码能力和 v1 规则，无法满足当前的降级要求。

### 方案 C：只做离线采集和规则转换

安全边界清晰，但不能提供用户需要的后台前视体验，暂不采用。

## 总体架构

```text
Playback source snapshot
        │
        ▼
AdAudioRuntimeController
   ┌────┴──────────────┐
   ▼                   ▼
PcmSignalProvider   ProbeSignalProvider (optional adapter)
   │                   │
   └────────┬──────────┘
            ▼
AdAudioDetectionMultiplexer
            │
            ▼
AdSkipPolicyController (PROMPT/AUTO, realtime mutable)
            │
            ▼
AdSkipCoordinator (唯一 seek authority)
            │
            ▼
Host playback/UI
```

## 规则与信任

### v2 规范规则

现有签名 v2 payload 仍是唯一规范规则源。其 `packageId`、`revision`、payload digest、有效期和 Ed25519 签名由现有 verifier/store 管理。

### Probe v1 sidecar artifact

Probe v1 规则使用独立签名 artifact，不嵌入主 v2 envelope。这样 sidecar 文件损坏、过期、回滚或缓存故障只会禁用 Probe，不会让已经验签的 v2 规则和 PCM matcher 一起失效。

独立 artifact 的逻辑结构为：

```text
SignedProbeRuleSidecarArtifact {
  artifactSchemaVersion: 1
  artifactType: "ad-audio-probe-sidecar"
  packageId: ASCII id
  revision: long
  createdAtEpochMs: long
  expiresAtEpochMs: long
  sourcePackageId: ASCII id
  sourceRevision: long
  sourceDigest: 32 raw bytes
  algorithm: "spectral-sequence-v1"
  converterVersion: ASCII id
  rules: canonical Probe v1 JSON bytes
  sidecarDigest: 32 raw bytes
  signature: Ed25519 metadata and bytes
}
```

artifact 的 `revision` 是 sidecar 自己的单调版本，因此同一个 v2 source revision 可以在发现转换器问题后发布更高 artifact revision；`sourcePackageId/sourceRevision/sourceDigest` 才是与主规则包的强绑定键。

sidecar 在发布/构建阶段由固定版本转换器生成。转换器使用明确的 v1 oracle（采样率、窗口、hop、downmix、resample、phase offset 全部固定），而不是从 v2 hash 文本猜测。无法通过 oracle 或参数一致性检查的规则不生成 sidecar。

sidecar artifact 使用独立签名域 `webhtv.ad-audio.probe-sidecar/v1`。签名覆盖 artifact/package/source 元数据、v2 source digest、算法、转换器版本、sidecar digest、keyId 和签名算法。它复用主规则包的可信公钥注册表、有效期和 key lifecycle，但使用独立 current/previous/high-water 缓存。

加载顺序为：分别加载已验签主 v2 包和已验签 sidecar artifact → 校验 `sourcePackageId/sourceRevision/sourceDigest` → 校验 v1 算法与资源上限 → 构造配对快照。任一步失败，只禁用 Probe sidecar，不拒绝主 v2 包。

### 规则加载

`AdAudioRuleSnapshot` 扩展为不可变的运行时快照，包含 v2 `ruleSet` 和可选的已验签、已配对 `ProbeRuleSidecar`。配对发生在 rule source 层，播放运行时不访问网络、不读写规则文件、不解析任意远程 JSON。

## Provider 接口

播放器只依赖内部接口，第三方类型不得越过 adapter 模块：

```java
public interface AdAudioSignalProvider extends AutoCloseable {
    void start(SessionContext context, RuleSnapshot rules, Listener listener);
    void onHostPosition(HostPosition position);
    void onTimelineReset(TimelineReset reset);
    void setEnabled(boolean enabled);
    ProviderState state();
    @Override void close();
}
```

`SessionContext` 携带 `sessionId/generation/mediaId/mediaUrl/headers`；`HostPosition` 携带当前媒体位置和 seekability；`TimelineReset` 携带 reset reason 和新 generation。所有回调都带 session/generation，旧回调必须丢弃。

实现包括：

- `PcmSignalProvider`：包装现有 `AdAudioConsumer`。
- `ProbeSignalProvider`：只在可选 adapter 模块中引用外部 AAR。
- `NoopAdAudioSignalProvider`：能力关闭或依赖缺失时使用。

Provider 只能发出 `AdAudioCandidate`，不能直接调用 UI 或 `seekTo`。

## 候选合并

`AdAudioDetectionMultiplexer` 负责：

- 校验 session/generation、时间范围和 rule revision。
- 以 `sessionId + generation + ruleId + adStart + adEnd` 去重。
- 同一候选优先保留时间线完整、匹配等级高、更新时间早的结果。
- 为同一候选维护 `NEW/PROMPTED/AUTO_APPLIED/IGNORED/EXPIRED` 状态。
- 对 Provider 异常、超时和重复回调做隔离和诊断计数。

Probe 的分析结果不能绕过 multiplexer；PCM matcher 的结果也不能直接绕过它。

## 实时策略

`AdSkipPolicyController` 暴露线程安全的 `PROMPT` 与 `AUTO` 模式：

- `PROMPT`：向 coordinator 提交候选，显示“跳过广告”。
- `AUTO`：候选仍先经过 coordinator 的 session、generation、PTS、seekability、live 和 cooldown 校验，再执行 seek。
- 从提示切到自动：尚未展示的候选可以自动执行；已经展示的候选保持提示状态，不突然跳转。
- 从自动切到提示：只影响后续候选，已经执行的 seek 不回滚。
- reset、close 或 generation 变化会清理策略关联的候选。

`AdSkipCoordinator` 仍是唯一宿主 seek authority，现有 undo 窗口和 UI 回调保持兼容。

## 生命周期与线程

播放开始、切源、seek、音轨切换、flush、结束必须向所有 Provider 广播。Probe 控制操作在自己的后台 Looper 串行执行，PCM/解码回调通过受控 executor 进入 multiplexer；第三方异常不能穿透 `PlaybackMediaSignalHub`、字幕队列或音频线程。

媒体 URL、headers、轨道和时间线必须来自同一次播放快照。VOD 是首期确定性范围；直播、DRM、SSAI 或无法建立稳定时间线时，Provider 报告不可用并 fail-open。

## 资源边界

- sidecar 规则数、canonical bytes、JSON token、字符串长度和哈希数量沿用签名包上限。
- Probe lookahead、解码并发、PCM block、缓存大小和单次任务时长均设硬上限。
- 空规则、无 sidecar 或 provider disabled 时在分配解码资源前短路。
- 所有网络和解码工作都不在播放器调用线程执行。

## 分阶段交付

### 第一阶段

实现 sidecar 数据模型、独立 artifact codec/verifier/cache、主包配对、运行时 Provider 接口、Noop/Pcm provider、multiplexer、实时策略和 fake provider 测试；不引入外部 AAR。

### 第二阶段

接入固定版本 Probe adapter，增加 v1 oracle、HLS TS/fMP4/MP4 和 API 23/29/35 设备矩阵；默认关闭。

### 第三阶段

在灰度开关下启用 Probe 前视，采集命中、重复、失败、seek rejected、回退率和资源指标；异常可单独关闭 Probe，PCM matcher 不受影响。

## 验收标准

1. sidecar artifact 与 v2 packageId/revision/digest 错配时永不进入 Probe，且主 v2 规则仍可用。
2. 同一候选由两个 Provider 同时报告时只出现一个提示或一次自动 seek。
3. 播放中切换提示/自动模式只影响规定范围内的新候选。
4. seek、切源、flush 后旧候选和旧回调全部无效。
5. Probe 依赖缺失、规则无 sidecar、解码异常和不支持媒体类型均 fail-open。
6. 不影响现有字幕音频处理、PCM matcher 和 `AdSkipCoordinator` 回归测试。
