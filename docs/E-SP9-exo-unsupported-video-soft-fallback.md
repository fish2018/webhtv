# E-SP9：Exo 硬解模式禁止自动软解回退

状态：已回滚，保持“视频只能人工手动切换硬解/软解”的既有合同（2026-09-20，Asia/Shanghai）

## 目标与验收

- 复评提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de` 是否违反现有播放合同。
- 结论：该提交让硬解模式在无硬件候选时自动使用 FFmpeg 软解，属于自动软解回退，与 `C-AVS3` 已确认的“视频只能人工手动切换硬解/软解，绝对禁止自动切软解”合同冲突。
- 修复：恢复硬解模式不注册任何 FFmpeg 视频 renderer，音频独立回退策略不变。硬件模式无候选时保持原有行为，用户需手动切换软解。

## 现场证据

- 用户给出的最后可用发布为 `v5.6.0-beta-202609141608`，目标提交 `7e8fdbd4c2c27c141f89ff2e9a84aaf7f52a6966`；首次失效发布为 `v5.6.0-beta-202609160047`，目标提交 `32640b193f7585353d3120c236b932afa1548165`。
- 两个发布之间的播放器链路差异是 Exo HLS DataSource 包装，入口提交为 `6bc447a14c1334100bd52a3cb5577f4c95a29c4c`；该差异没有修改 `ExoUtil` 的渲染器选择。
- 5561 现场 URL 为 `http://45.192.97.170:8880/play/1.m3u8`，302 后得到咪咕 H.265 主清单；媒体清单为 4 个 `6s` 分片。
- 同一现场日志中，Media3 解析出 `video/hevc`、`hvc1.1.2.L153.80`、`1280x720`，但报告 `supported=NO_UNSUPPORTED_SUBTYPE`；AAC 音频被选中并初始化，链路没有视频解码器初始化、视频尺寸或首帧事件。UI 因此一直停留在 shutter，形成“有声音没画面”。
- `ExoUtil.getVideoRenderMode()` 在硬解时返回 `EXTENSION_RENDERER_MODE_OFF`，`buildVideoRenderers()` 因此直接返回，不注册任何 FFmpeg 视频渲染器。MediaCodec 没有候选解码器时不会产生可归因的播放错误，音频继续推进。

## 方案比较与决策

- 方案 A（不改 Exo 硬解分支）：硬解模式下无硬件候选时保持无声画面；用户手动切到软解即可播放。与既有合同一致。
- 方案 B（原提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`）：硬解模式注册 `CompatFfmpegVideoRenderer(systemDecoderFallbackOnly=true)`，无硬件候选时自动由 FFmpeg 软解接管。画面恢复，但属于自动软解回退，违反 `C-AVS3` 已确认合同。
- 方案 C（提示而不接管）：无硬件候选时向用户提示并引导手动切软解。这是可选的产品改进，需要独立的 UI/状态设计，不在本次修复范围。
- 决策：采用方案 A，回滚方案 B；不引入方案 C。现有 `CompatFfmpegVideoRenderer` 本体的“平台有候选时让轨”合同保留，仅撤销 `ExoUtil` 中的自动注册。

## 验证计划

1. 运行 `CompatFfmpegVideoRendererSourceTest`，覆盖硬解模式不注册 FFmpeg 视频回退的源码合同，以及 `CompatFfmpegVideoRenderer` 本体的让轨合同。
2. 运行 `MpvHardwareDecodePolicyTest` 和 `ExoAudioCodecSelectorTest`，确认 MPV 硬解禁止自动软解、Exo 音频独立回退未受影响。
3. 编译 Mobile ARM64 Debug；如需设备确认，走 `bash scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5561` 覆盖安装，验证硬解模式确实不出现自动软解出帧，手动软解仍可用。

## 实施边界与回滚

- 只修改 `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`、定向源码合同测试和本任务文档/索引。
- 不修改 Media3 AAR、nextlib、FFmpeg、MPV、native lock、输出面或音频路由。
- 回滚：本提交的父提交是 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`；撤销本回滚即可恢复该提交的自动软解行为。

## 实施记录

- 2026-09-20：确认 `4a9f0fd14d04aa5f45c8601d9f77334666b435de` 自动软解回退违反 `C-AVS3` 既有合同；开启 `E-SP9-manual-decode-contract` guard 进行窄回滚。
- 回滚实现：移除硬解模式下的 `CompatFfmpegVideoRenderer` 注册和对应构造帮助方法，恢复 `if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;`。
- 定向测试更新：`CompatFfmpegVideoRendererSourceTest` 现在断言硬解模式不注册 FFmpeg 视频回退，同时保留 `CompatFfmpegVideoRenderer` 本身“平台有候选时让轨”的单元合同。
- 回滚原因：设备的“有声音无画面”不是原提交可绕过的自动软解场景；正确操作是用户手动切换软解。若需要改善硬解无候选的提示文案，应作为独立产品需求评估，不在本任务中改变解码选择。
- 回滚锚点：本提交的父提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`。撤销本回滚即可恢复该提交行为。
