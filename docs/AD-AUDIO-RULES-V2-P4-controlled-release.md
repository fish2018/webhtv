# AD-AUDIO-RULES-V2-P4-controlled-release

## Phase 4 目标
在播放出现退化（卡顿、掉帧、音频 underrun、重缓冲等）时，自动抑制语音识别 Provider，以避免加重系统负担，而不影响指纹通道。抑制仅对当前播放会话生效，下次媒体会话重新评估。

## 设计方案对比
### 方案 1：不变（no change）
- 保持当前行为：语音 Provider 按现有逻辑运行，不考虑播放健康。
- 优点：无实现成本，无风险。
- 缺点：在资源受限的设备上可能加剧卡顿或 underrun。

### 方案 2：原样接入（upstream-as-is）
- 直接采用上游 Sherpa/ONNX Runtime 的线程调度建议（如降低线程数）。
- 优点：已有最佳实践参考。
- 缺点：不解决突发的播放退化；仅静态调整，无法动态响应；且 Android 后台优先级不等同于资源隔离。

### 方案 3：WebHTV 自适应门控（chosen）
- 在 PlayerManager 处理播放遥测 tick（约 5 秒）时，读取 PlaybackAnalyticsListener 的快照；
- 若检测到退化（掉帧、underrun、重缓冲异常）则通过 AdAudioRuntimeController 新增 API 抑制语音 Provider（仅 speech，不影响指纹）；
- 抑制持续到当前播放会话结束；下次会话重新评估；
- 失败时 fail-open：任何错误都不抑制语音，保证主播放优先。

## 现有指标和调用链
- PlaybackAnalyticsListener.Snapshot 提供：
  - droppedFrames（累计）
  - rebufferCount、rebufferTotalMs
  - （无 audio underrun 计数，仅在 SpiderDebug 开启时日志）
- PlayerManager.publishPlaybackTelemetryTick 每 ~5s 调用一次 refreshAdAudioRuntime；
- AdAudioRuntimeController.refreshLocked 中根据 speechConfig.enabled() && speechConfig.hasSpeechRules() 决定是否创建 speech provider。

## 抑制触发条件候选
- droppedFrames 增长速度超过阈值（当前实现采用每秒至少 4 帧）持续两次检测；
- rebufferCount 增长；
- 音频 underrun：目前仅在 SpiderDebug.isEnabled() 时写日志，复用该开关以避免双重开关；
- 模型启动失败（SPEECH_MODEL_UNAVAILABLE）已有诊断，可直接触发抑制；
- 队列溢出（SPEECH_PCM_QUEUE_PEAK）已有诊断。

为避免误判，采用低基数计数（delta）和简单阈值：underrun 或 rebuffer 增量、或掉帧速率达到每秒 4 帧，连续两个有效 tick 才触发抑制。阈值是保守的初始门槛，不是 TV 性能验收结论；真实设备矩阵仍必须测量后再调整。

## 是否自动恢复
- 本次实施不自动恢复：抑制仅持续到当前播放会话结束，下次会话重新评估。
- 这样避免了在同一段媒体内反复开关导致的抖动。

## 真实 TV 验收矩阵
- 低端 Android TV（如盒子）和高端 Android TV（如索尼、TCL 高端机型）；
- 相同 VOD、相同音轨、相同离线模型；
- 四组对照：
  1. 语音关闭（baseline）
  2. 模型未就绪（通过禁用 SpeechAdSetting 实现）
  3. 模型就绪 + 线程 1（ Sherpa 单线程）
  4. 模型就绪 + 线程 2（默认）
- 测量项：droppedFrames、AudioTrack underrun、rebuffer、视频/音频 backlog、speech/Sherpa CPU、识别队列峰值、首帧时间。
- 注意：由于当前工作环境缺乏真实 Android TV 设备，本阶段仅通过 JVM 单元测试和 Leanback ARM64 定向测试验证逻辑正确性；真实 TV 验收留给后续专项任务。

## Acceptance Criteria
- 纯 JVM 单元测试覆盖门控逻辑：无退化不抑制；任意退化指标增长触发抑制；抑制不影响指纹 Provider；新会话重置抑制状态；
- Leanback ARM64 定向 JVM 测试通过；
- Leanback Java 编译通过；
- 不把手机性能或 JVM 编译结果当作 TV 性能验证。

## Rollback Path
- 由于仅新增增量代码且不修改现有行为（除新增抑制外），回滚为删除新增文件和还原修改；
- 在 P4 guard 结束前，若发现任何回归，可直接放弃本次更改；完成后通过 recovery tag 回滚。

## 官方资料和当前不可获得资料
- 已参考：Android thread 最佳实践（https://developer.android.com/topic/performance/threads，2026-09-09 读取）、Media3 AnalyticsListener API（https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener，2026-09-09 读取；官方定义 onAudioUnderrun 为 audio underrun、onDroppedVideoFrames 为 dropped frames、onPlaybackStateChanged 为状态变化）、设计文档已记录的 ONNX Runtime threading 指南（https://onnxruntime.ai/docs/performance/tune-performance/threading.html）；
- 无法获得：真实 Android TV 设备性能基准、 Sherpa 在不同线程数下的 CPU/延迟曲线（需实际设备测量）。

## 手机不是 TV 证据
- 现有 ADB 设备均为手机（SM-N9700、NX627J），不能用于 Android TV 性能验证；
- 因此本文档明确标注：手机性能结果不等同于 TV 表现，不能替代真实 TV 验收。
