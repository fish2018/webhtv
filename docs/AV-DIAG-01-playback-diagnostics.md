# AV-DIAG-01：音视频全链路调试日志改造方案

## 2026-09-17 直播播放参数入口补齐

- 用户授权优先补手机直播播放参数按钮，并在同样简单时同步电视端；当前基线 `8dd32cb9aab018a9d2c74437d235bc0e1912eded`，guard `AV-DIAG-01-live-params` / quick-fix，保护104个原有 `app/.cxx/` 文件。
- 两端直播已经持有 `PlayerOsdController`，缺少与点播 `VideoActivity.onPlayParams` 对等的入口。复用其全局设置、实时数据及采样生命周期，补手机顶部图标、直播控制行/控制面板和电视控制行；开关后隐藏控制层以显示参数，按钮选中状态及重新进入时的状态同步。手机图标48dp，电视沿用原横向滚动及遥控焦点顺序。
- 本单元只是已有能力的入口补齐，不改变解码/自动回退、音频输出、网络、native或采样频率；采用已经存在的本地设计，按局部修复处理，无需扩展上游研究。验证为Mobile ARM64打包、Leanback ARM64 Java/资源编译和手机一次入口检查。
- 同时只读监听用户的AV3A/AVS3直播问题。已保留 `/private/tmp/webhtv-live-avs-20260917/` 日志；当前安装的是release包，使用Web增量日志和ADB logcat，不依赖run-as。普通直播源已观察到 `video/avc` 1920×1080与 `audio/av3a` / `libarcdav3a`，不能将其误称为AVS3视频；MPV部分尝试尚未到file-loaded，须另行核对加载链路。
- 最小编译已通过：同一次Gradle完成 `compileLeanbackArm64_v8aDebugJavaWithJavac` 和 `assembleMobileArm64_v8aDebug`，耗时2分1秒，日志 `build/avdiag-live-params/gradle.log`。APK SHA-256 `d1927aa145edcd719f47626d737f67b49092cbb842a145a933c2917a0aaba916`，181893921字节，50个原生条目与AVS3 MediaCodec基线相同；身份记录 `build/avdiag-live-params/apk-identity.json`。UI接线复用已有实现，不新增镜像单测。
- 日志已进一步定位MPV加载失败：21:45尝试出现HTTP 400、首个HLS分片加载失败和 `avformat_open_input()` 失败，另一次代理请求30秒超时；不能归因于AVS3解码器不支持。21:37本地4K50测试片实际为 `video/avs3` / `avs3.32.55` / 3840×2160 / 50fps / 10bit + `audio/av3a` / 48kHz / 9声道；这不能代替随后4K直播源尚缺的实际轨道证据。
- 当前状态：按钮实现和两端编译完成，用户随后确认“打个tag，播放参数面板有了”，据此验收手机入口并关闭可选验证。期间USB多次断开，安装辅助脚本超时，自动UI检查未形成有效结果；最终设备APK哈希为 `d125250477df01ec518201eaecfffaa68c89f77f29b18aaacc32878b119ba8a1`，不同于上述本机构建，不把它记为本候选安装校验通过。手机入口以用户确认作为运行证据，电视端仅有编译验证。直播解码/请求问题仍待后续处理，不包含在本次按钮验收中。回滚为撤销本单元提交，解码库不变；唯一下一动作：guard原子提交并生成本地恢复tag，不推送。

> 状态：2026-09-15 **D0–D5实现与对应产物已补齐**，覆盖索引见14.13，软件验证及交付记录见14.14。设备实播、T01–T25和性能A/B由用户实测，不记作已通过；物理屏幕/扬声器等平台不可观察边界保留。
>
> 2026-09-14夜间评审的持续记录与查名操作边界（9.2.1、9.3.1）已合入当前实施文档；原隔离工作区仅作输入。
>
> 目标：没有 ADB 的手机、电视、盒子、模拟器，仅通过 App「调试日志」及其导出文件，重建一次播放的输入、选轨、解码、视频输出、音频输出、策略变化和故障恢复链路，得到有证据的根因或明确的剩余观测边界。

## 0. 范围、基线与交付位置

- 编写日期：2026-09-13，Asia/Shanghai，UTC+08:00。
- 代码审阅基线：WebHTV `feature/mpv-dv7-fel`，HEAD `0a82dc13e255524d7c0e4e04c2f51ec9119aec88`；文中本仓库位置均相对此工作区。
- 本地 Media3 源码基线：`e3e922d5c01bc0b564849940fe589daf37360d15`，来自 `third_party/media-lock.json`；该锁另有补丁和 artifact overrides，**不能把基线源码等同于每个最终 AAR**。
- 原设计时 FEL guard 为 `P2-4-fel-vo-handoff`；2026-09-14 实施前核实最新 `P2-4-fel-warmup-push` 已 finished。D0 基线为 `feature/mpv-dv7-fel` / `2ec5afd8cc3f21bf1693b198f87018488775c660`，独立 guard `AV-DIAG-01-D0`，保护已有 `app/.cxx/` 全部 70 个未跟踪文件。
- 原设计交付：`/private/tmp/webhtv-av-diagnostics-2026-09-13/AV-DIAG-01-音视频全链路调试日志改造方案.md`。相关已读取资料保存于同目录 `evidence/`，实施验证也保存在该临时目录；当前任务文档位于仓库 `docs/AV-DIAG-01-playback-diagnostics.md`。
- 已归档为 `docs/AV-DIAG-01-playback-diagnostics.md`，原临时文档及证据快照保留。`AV-DIAG-01` 是用户插入需求标识，不占用、改写既有上游 E/P/C 任务编号；D0–D5 的后续工作均续写本文件。
- 范围：Exo/Media3、其 FFmpeg/nextlib 扩展、MPV、仍可用的 IJK，以及公共播放/Surface/AudioManager/日志导出层。网络、代理、DRM、字幕/LUT、生命周期作为解码和输出故障的必要上下文，不扩展成全量抓包或系统监控。
- 原评审非目标：修复模拟器黑屏、改默认硬解/软解策略、自动切播放器、自动更改直通、升级依赖、读取其他 App 日志或要求用户打开 ADB。14.10的全文实施授权包含必要的native诊断hook和同锁重编；不改变原播放策略。
- 夜间复核基线：`5cde3c015258f620f264d5f3ffe0a437c2ea3d48`。原工作区有活动 guard `E4-LIBASS-stage1` 及 ASS 相关既有改动，本轮全部保护；在 `/private/tmp/webhtv-avdiag-review-20260914`、分支 `assessment/av-diag-01-log-review-20260914` 中仅修改本文件，guard 为 `AV-DIAG-01-review-20260914`。不改变原工作区的 HEAD、索引、文件或任务状态。
- 持续输出补充：用户随后要求明确持续记录方式；在同一隔离分支、基线 `2ac420f8c038e5a6016cc0169cd80b8b4c8608d3` 上，仅细化9.2.1和T21，guard `AV-DIAG-01-continuous-log`。这是后续实施的设计约束，未修改现有日志代码。

### 0.1 验收承诺的正确边界

**不能诚实承诺“任何视频不显示/任何无声，仅凭 App 日志一定定位到最终根因”。** Android 不向普通 App 开放全部厂商解码器、SurfaceFlinger、AudioFlinger、HDMI/功放及物理屏幕/扬声器内部状态。硬解回调、AudioTrack 写入成功甚至时间戳前进，都不等于用户一定看见/听见。

本方案承诺的可验收目标是：

1. App 能观测的各层都有输入、输出、失败、生命周期和关联标识；不是只有最终一行错误。
2. 明确区分事实、配置、推断、能力声明和用户观察；未采集/不支持/过期/权限受限不伪装为正常。
3. 可定位的故障提供具体失败操作、实际参数、原始错误与前后状态；不可定位的故障提供最后一个已证实正常的边界、缺失证据，以及**一个**最小区分步骤。
4. 单一音频或视频失败不会被另一条正常链路、`READY`、进度前进或封面图片掩盖。
5. 导出包自身能够说明信息是否完整。日志被截断、限流、丢失、尚未落盘时，不输出“已排除某层故障”的假结论。

## 1. 问题样本与需要避免的误判

### 1.1 已有用户样本

2026-09-12，两份 `webhtv-debug-log` 对应同一模拟器、同一网盘原画 MP4。媒体为 HEVC Main10、1920×1080、24fps、SDR、AC3 5.1。敏感网盘链接和 Token 不复制到本文。

| 已观察到的事实 | 能说明什么 | 不能说明什么 |
| --- | --- | --- |
| Exo 使用 `OMX.qcom.video.decoder.hevc`，收到首帧回调，`READY`、进度持续前进 | 解码/播放接口至少进入了对应状态 | 屏幕真实画面正确；名字含 qcom 不证明模拟器使用真实高通硬件 |
| Exo `renderedFps=24` 来自呈现时间戳间隔估算 | 媒体/提交帧的时间戳节奏 | 实际屏幕刷新了 24 张不同的正确画面 |
| 首帧附近有 `syncVideoSurfaceSize fixed=1280x720` | App 实际请求过固定 Surface 尺寸 | 黑屏一定由该操作导致；请求值也不等于下一次 callback 的实际尺寸 |
| MPV `Failed to initialize a decoder for codec 'hevc'`，之后 `vid=no aid=1` | 视频初始化失败，音频仍选中 | 设备根本不能硬解；也不能只因音频继续而认为整次播放正常 |
| MPV 存在视频轨元数据，仍打印 `source=mpv-playback-restart` 的 `first-frame` | 当前上层把就绪信号当作首帧标记 | 原生层已经输出有效视频帧 |
| 画面是“夸父”静态标识 | 可能是无视频时的 artwork/封面 | 不能称为“视频卡在第一帧”，必须记录画面来源 |
| 用户补充原版影视 TV 同设备同视频能硬解 | 应优先做 App/内核/配置差异对照 | 尚未拿到原版准确版本、内核、实际 decoder/VO/AO，不能假定两条执行链完全相同 |

此案例必须成为后续诊断验收 fixture；**它不是本文已经完成的修复或可复现设备测试**。

### 1.2 要覆盖的问题族

- 完全黑屏、有声无画、封面占位、首帧后冻结、seek/切集/横竖屏后黑屏、绿屏/紫屏/花屏、HDR/DV 输出错误。
- 无声、有画无声、音轨未选中、音量/焦点导致静音、PCM 全零、声道映射错、爆音/断续/缓冲不足、直通和 offload 失败、蓝牙/HDMI/eARC/USB 路由切换后无声。
- 硬解创建/配置/启动失败、运行中失效、资源耗尽、厂商能力误报、软解扩展未加载、ABI/库组合不匹配、用户选项未生效。
- 资源不是预期视频、清晰度/转码变化、代理/Range 错误、音视频某一轨无数据、DRM/key 不可用、PTS/DTS/同步问题。
- 日志开启过晚、应用卡主线程、native 卡住/崩溃、进程被杀、日志洪泛、导出丢上下文。

### 1.3 2026-09-14 新日志复核与设计增量

用户再次报告模拟器上 Exo、MPV 硬解均无影片画面，提供 `IMAGE 2026-09-14 23:16:43.jpg` 和 `webhtv-debug-log (3) (1).txt`。截图显示 MPV/硬解设置及静态网盘标识；新 TXT 的播放 trace 是 `p-6i9b-3`，只覆盖 MPV，不能据此补出 Exo 的失败链。App 为 5.6.0(560)、buildTime `202609141846`、Android 14/API34、mobile arm64_v8a；用户 APK 的 Git/native revision 仍未采集，不能把本地源码直接当作该 APK 的精确来源。

| 新证据（TXT 的 logSeq） | 可作出的判断与边界 |
| --- | --- |
| 1040：`hevc_mediacodec: Failed to getCodecNameByType(video/hevc, 2)`，后续多次出现 | HEVC Main10 对应的查名调用未返回 decoder 名称。尚缺本次候选、过滤原因、JNI/能力查询结果；不能等同于设备不存在 HEVC 硬解 |
| 1042：`MediaCodec 0x0 failed to start` | 未获得可用 codec；本地 FFmpeg 的通用失败出口也打印此句，不能仅凭文字把失败操作定为 `MediaCodec.start()`。Java 查名失败可在创建前返回，NDK 分支还可能按 MIME 创建，实际分支必须另取证 |
| 1055、1077：`Software decoding fallback is disabled`；1228：`vid=no aid=1` | 视频失败后没有软解兜底，音轨仍选中。不能以有视频元数据、进度前进或 `mpv-playback-restart` 的兼容首帧标记证明视频输出；aid 有效本身也不证明物理有声 |
| 导出含 64 行 `mpv-native`，按事件身份去重后为 32 条，最后为1090；未见 native 限流汇总 | 与当前32条/5秒窗口吻合，提示尾部证据可能未导出；不能把相同文字的不同尝试合并，也不能凭条数断言具体丢失数量 |
| manifest：`droppedNormal=0`、`droppedCritical=0`、`nativeOverflow=not-collected`、`complete-within-declared-window` | 只说明已进入 D0 sink 的声明窗口，不能证明进入 sink 之前的 native 过滤、限流或队列没有损失 |

上述去重身份为 `processRunId=4cffb428-81fc-4286-9239-ed8a73e587f6`、`captureGeneration=7` 加 `logSeq`；pinned 与时间线中的同一事件只算一次，不能按消息正文去重。附件不复制进仓库，不记录网盘凭证。

**原方案已覆盖的内容不重列为缺口：** V01/V02 的 Exo 候选和尝试链、S01–S05 的 Surface/封面、M04/M09 的视频局部失败、9.2 的分级限流、T24 的原版对照，都属于待实施项。此次补充的是两项原来不够具体、会影响验收的契约：① M08 的 native 查名失败必须区分候选剔除、查询失败及实际后续操作（9.3.1）；② 关键证据必须在上游过滤前取得，且限流后即使没有下一条日志也能导出健康统计（9.2.1）。这次仍未证明 Exo 与 MPV 同因，也未证明32位兼容、设备能力或 Surface 是根因。

## 2. 现有实现盘点：复用什么、补什么

下表保留原设计时的实现盘点，D0 后的实际交付以14.4为准；“缺口”指代码尚不能形成完整可导出证据链，不意味着方案漏写或整个仓库没有任何同类零散日志。

| 现有落点 | 已有能力 | 本次设计确认的缺口 |
| --- | --- | --- |
| `catvod/src/main/java/com/github/catvod/crawler/SpiderDebug.java:15` | 开关门控；字符串/Throwable 进入 DebugLogStore；同时可进入平台日志 | 没有公共结构化 envelope、字段级隐私白名单和会话完整性协议 |
| `catvod/src/main/java/com/github/catvod/crawler/DebugLogStore.java:20` | 内存列表、cache 文件、恢复、下载文本；单消息限制 12000 字符 | 当前列表和文件没有总量上限；持锁同步逐条开文件写入；失败被吞；关日志即 clear；长日志全文读回 |
| `app/src/main/java/com/fongmi/android/tv/server/process/DebugLogs.java:24` | `/debug/logs`、`/debug/logs.txt`、`/debug/stream`、启停和清空 | stream 在 version 变化时返回全文；缺游标/缺口标识、会话快照、诊断包完整性、故障标记操作 |
| `app/src/main/java/com/fongmi/android/tv/setting/Setting.java:587` | App/Android/机型/ABI 环境日志 | 缺实际加载库集合与构建标识、GPU/音频路由、配置来源、诊断能力清单 |
| `app/src/main/java/com/fongmi/android/tv/player/PlaybackTrace.java:13` | trace 和 request/prepare/tracks/ready/first-frame 阶段 | 跨进程/播放器实例、重试、预载和新资源 generation 的关联不充分；缺独立音视频终态 |
| `app/src/main/java/com/fongmi/android/tv/player/PlaybackTelemetryCoordinator.java`、`PlaybackTelemetryMapper.java` | 聚合播放/内存/网络等状态，部分字段已有来源和置信度 | 不能把估算 FPS/未知值当最终渲染证据；需与新证据事件统一语义，而非再建冲突仪表盘 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/PlaybackAnalyticsListener.java:390` | decoder 名称、输入 Format、VideoSize、AudioTrackConfig、掉帧、sink error、seek、最终 error | 缺候选及剔除原因、配置/输出格式、完整 fallback 链、实际写入/呈现分层；`onAudioPositionAdvancing` 目前主要服务 seek 跟踪 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/PlaybackAnalyticsListener.java:963` | 主要 decoder init 的 diagnosticInfo、最多 8 层异常类型名 | 没有同等完整的异常 message/stack/suppressed/fallback 证据；记录全部候选不能反向增加黑名单计数 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ObservedVideoFrameRateEstimator.java:11` | 由 PTS 估算源帧节奏 | 必须改进日志字段命名或标注，不能宣称物理显示 FPS |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:420` | attach、render 切换、尺寸、Surface valid；固定尺寸请求 | 缺 Surface 生命周期 generation、actual holder 回读、窗口可见性/遮罩及解码输出绑定关系 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:4244` | 设置默认 artwork | 缺“当前展示的是封面/视频/遮罩”的明确日志，TV 也需等价采集 |
| `app/src/main/java/com/fongmi/android/tv/player/AudioPlaybackDiagnostics.java:98` | 原轨/当前轨、decode/output mode、降级原因、失败阶段模型 | 需自动输出到调试日志，不依赖用户打开某个 OSD/能力面板 |
| `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java:631` | 从 decoder 和 AudioTrack config 区分 PCM/直通/offload | 缺具体 AudioTrack 的 route/write/head/timestamp 与有效音量链 |
| `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:5335` | native 音频焦点请求失败、noisy、焦点变化的部分日志 | 需贯通 Exo/native 两种 owner，记录成功、duck/resume、音量和发生顺序 |
| `app/src/main/java/com/fongmi/android/tv/player/codec/CodecCapabilityInspector.java:42` | 设备与当前媒体能力报告 | 需自动保存本次媒体相关能力摘要；面板能力声明不替代实际创建/configure 结果 |
| `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java:1276` | native warn/error 等可直接进日志；generation 检查；主线程队列延迟；错误窗口 | 现有 5 秒 32 条窗口可能压制关键上下文；需结构化尝试链、分级保留和明确丢失统计，不全开逐帧 verbose |
| `app/src/main/java/androidx/media3/mpvplayer/MpvPropertySnapshot.java`、`MpvDiagnosticsPolicy.java` | observer 缓存、部分过期/新文件处理、限制同步查询、部分敏感信息脱敏 | 缺统一字段 age/status 与跨属性快照一致性；不能恢复为 UI 线程大量同步 JNI 查询 |
| `third_party/mpv-player-jni/src/event.cpp`、`app/src/main/java/is/xyz/mpv/MPVLib.java` | native event/property/log 回调及库加载 | 需 event queue overflow、command reply/error、native attempt/generation 和库实际来源的明确证据 |
| `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:7176` | 根据 MPV ready/轨道存在标记启动完成 | 诊断需独立记录 `videoAvailable` 与 `videoSelected/videoOutputActive`，不能继续只认该 FIRST_FRAME |
| `app/src/main/java/com/fongmi/android/tv/player/engine/IjkSimplePlayer.java:532`、`IjkPlayerEngine.java:219` | onPrepared/onError/onInfo、音视频快照 | 不把 IJK 当盲区；能取的属性写出，取不到的原生输出信息明确 unknown |
| `app/src/main/java/com/fongmi/android/tv/player/mpv/PlaybackRecoveryMonitor.java:145` | 卡死恢复请求、上次用户退出结果持久记录 | 不等于系统崩溃/ANR 完整记录；需公共会话 journal 与按 API 获取的退出原因 |

## 3. 设计决策与最佳实践选择

### 3.1 三种方案比较

| 方案 | 优点 | 无法满足的部分/风险 | 决策 |
| --- | --- | --- | --- |
| 不改，只让用户换软解/抓 ADB | 无代码成本 | 无 ADB 设备无法定位；丢失真正硬解差异；只缓解不解释 | 不满足需求 |
| 原样启用 Media3 EventLogger、MPV `all=v`、FFmpeg debug | 上游现成；起步快 | EventLogger 默认面向 Logcat；缺 UI/音频实际路由；可能泄露 URL/请求头；大量日志可拖慢 native/UI，仍可能把 READY 当成功 | 只借鉴事件覆盖，不整套照搬 |
| 扩展现有日志通路，分层结构化事件＋定时聚合＋异常前后快照＋可选深度探针 | 用户不需 ADB；保持现有入口；可判定“失败发生在哪层”；能控制开销 | 需统一 schema、owner/thread、隐私与完整性；部分底层采集需独立依赖阶段 | **推荐：WebHTV 适配方案** |

### 3.2 不改变播放决策的原则

- 采集器默认只读；不能为了诊断主动 flush/recreate decoder、seek、改格式/直通/音量/硬解或重启播放器。
- 记录“发生了 fallback”和“为什么发生”，不增加 fallback、不修改黑名单、不可让诊断错误触发恢复策略。
- 保留已有首帧/启动行为接口；先新增语义正确的证据字段并将旧 `firstFrameMs` 标为兼容/就绪信号。修改 UI 或启动状态机属于另外的经审批行为改动。
- Media3/MPV/IJK、前台播放/预载、音频/视频、主 Surface/OSD Surface 不能共用不带 owner 的计数器。
- 最大化复用上游 public/UnstableApi 扩展点。当前 Media3 有 `ForwardingAudioSink`、`ForwardingAudioOutputProvider`、`ForwardingAudioOutput`；`AudioTrackAudioOutput.getAudioTrack()` 可取得其实际对象。先验证最终 AAR 的签名再接入，**不反射私有字段、不替换底层默认实现来凑日志**。
- 只有 public hooks 无法提供必要字段时，才设计窄的 Media3/native 诊断扩展；它是单独审批、单独构建验证的阶段，不混入公共日志改造。

## 4. 统一事件模型与关联规则

### 4.1 同一个日志通路

```text
用户/遥控器动作、站源解析、媒体和策略
          │
          ├─ Exo Analytics + codec adapter + AudioSink/AudioOutput
          ├─ MPV event/property/log + JNI + 必要 native 诊断点
          ├─ IJK info/error/property
          └─ Surface/窗口 + AudioManager/route + 系统/生命周期
          │
          ▼
结构化事件 → 隐私白名单/长度限制 → 有界优先级队列
          │                         │
          ├─ 按会话快照/环形前序窗口 └─ 独立日志写线程/落盘进度
          ▼
现有 SpiderDebug / DebugLogStore 的兼容日志视图
          ├─ App 调试日志/电视诊断入口
          ├─ /debug/logs 与 /debug/logs.txt（仍可直接下载完整可读文本）
          └─ 诊断包：timeline + snapshots + manifest + summary
```

**不能只新增 Logcat、OSD、内部对象或一张截图。** 每个验收事件必须出现在用户能导出的调试日志中。native 尚未 bridge 的错误必须写明 unavailable，不得靠用户去 adb 找补。

### 4.2 每条事件的公共字段

文本兼容外壳建议：`日期 [线程] av-diag: {单行 JSON}`。保留现有分类，网页另增加「音视频诊断」过滤。JSON 转义换行，禁止把外部文本伪造成下一条日志。

| 字段 | 必须满足的语义 |
| --- | --- |
| `schemaVersion`, `event`, `level` | 版本化字段定义；事件名稳定；severity 不从自由文本猜 |
| `wallTime`, `timezone`, `elapsedRealtimeNs` | wall 用于用户对照；耗时用单调时钟；不混合 wall 与 media time |
| `processRunId`, `processRole`, `pid`, `thread` | 每次进程启动随机 run id；主进程/native恢复辅助进程分开；不使用设备永久标识 |
| `seq`, `sourceSeq`, `capturedAtNs`, `enqueuedAtNs` | 全局写入顺序与源头捕获顺序分开；晚到事件保留原时间；不能把导出顺序冒充因果 |
| `trace`, `playerInstanceId`, `engine`, `engineVersion` | 复用现有 trace，补播放器实例；切内核明确 parentTrace/parentAttempt |
| `mediaGeneration`, `attemptId`, `operationId`, `parentEventId` | 新文件、重试、seek、flush、track switch 可分辨；旧 generation 的事件记 late/orphan，不能污染新会话 |
| `role`, `eventMediaId`, `currentMediaId` | foreground/preload；Media3 EventTime 的事件媒体与当前媒体可能不同；不使用单个可变全局 trace 给回调补身份 |
| `videoTrackId`, `audioTrackId`, `decoderId`, `surfaceId`, `audioOutputId` | 只在适用时填写；同名 codec 重新创建仍是新的 decoderId；对象 ID 为本进程不透明序号，不导出地址 |
| `positionUs`, `durationUs`, `seekEpoch`, `clockDomain` | 不把未知时长、TIME_UNSET、无效浮点当 0；写清 media/system/audio clock |
| `requested`, `effective`, `observed`, `origin` | 用户值/策略结果/底层读回；origin 为 default/user/profile/mpv.conf/per-file/runtime-fallback 等 |
| `status`, `source`, `evidenceClass`, `ageMs` | evidenceClass为observed/configured/inferred/user-reported；status固定为known/unknown/not-collected/not-supported/not-applicable/permission-denied/stale/read-error/timed-out/pending-callback/unavailable/not-observable |
| `snapshotId`, `fieldsChanged`, `suppressedCount`, `truncated` | 周期输出增量；完整基线/终态另保留；任何省略可追踪 |

规则：数值 `0`、布尔 `false`、空列表只有真实查询/事件支持时才写；没有 `hwdec-current` 的 MPV 不能回退到 `config.hwdec` 再宣称“实际硬解”。`available`、`selected`、`initialized`、`producingOutput` 独立建模。`unavailable`表示接口存在但当前没有值，`not-supported`表示该API/构建不支持，`not-observable`表示App无合法观测手段，`unknown`表示证据尚不足；禁止混用。

### 4.3 音视频成功证据分层

| 视频层 | 音频层 | 证据强度与边界 |
| --- | --- | --- |
| V0 媒体声明视频轨 | A0 媒体声明音频轨 | 只有元数据 |
| V1 轨道已选且收到 sample | A1 轨道已选且收到 sample | 不证明 decoder 可工作 |
| V2 decoder 输出 buffer/frame | A2 decoder 输出 PCM，或已选择直通的压缩帧 | 直通不应强求软件/硬件 audio decoder；压缩帧不是 PCM |
| V3 frame release/render submission | A3 sink/AudioOutput 实际接收/写入 | 提交不等于呈现；写入不等于声压 |
| V4 MediaCodec output-surface callback / GPU present 成功等 | A4 播放头/有效时间戳前进、实际路由存在 | 命名注明具体 API；不能跨内核把 V4 定义成同一强度 |
| V5 可选像素统计 + 用户确认 | A5 可选 PCM 统计 + 用户确认/外部链路检查 | PixelCopy 仍不是面板出光；PCM 非零仍不是扬声器发声 |

每次 report 同时输出 `videoEvidenceLevel`、`audioEvidenceLevel`、`lastGoodBoundary`、`failedBoundary`、`missingEvidence`。`READY` 仅是状态，不出现在“最终音画正常”的判定条件中。

## 5. 触发、留存与用户操作

### 5.1 无 ADB 的最短使用流程

1. App 设置或局域网调试页开启「音视频诊断」，展示隐私、时间/空间上限和实际采集能力。
2. 用户原样复现，不先切软解/直通，避免破坏原故障现场。
3. 遥控器可选择「黑屏」「画面不动」「无声」「断音」「音画不同步」「其他」，或网页点「标记此刻故障」。先保存快照，再允许深度捕获。
4. 一次点击下载 `webhtv-debug-log.txt`；可选下载完整诊断包。下载不能依赖播放线程响应，也不能触发重新查询所有 native 属性。
5. 如需 A/B，保持原内核/硬解，仅改变一个相关参数，分别保存修改前后的日志；实际生效结果以播放事件为准。按14.16用户决定，移除只记录手填备注的“记录单参数对照”入口。

未提前开启时无法恢复此前没有采集的事实；此时输出 `captureStartedLate=true`、现有基线和能得到的历史记录，再请求一次复现。不开日志时不暗中常驻采集媒体/设备隐私。

### 5.2 必须触发的快照

- 打开调试、process start、player create、play request、解析返回、prepare、轨道变化、decoder configured/output-format changed。
- 首个 sample、首个解码输出、首个提交/回调、首个音频写入、audio-position advancing；这些分别触发而不是共用 `first-frame`。
- 用户故障标记、明确 decoder/sink/native 错误、长时间无输出的观察告警、route/focus/volume 变化。
- seek/flush/reinit、切音轨/视频轨、切源/切内核、Surface 重建/尺寸变更、进后台/前台/PiP/投屏。
- stop/release、end-file、进程恢复、导出开始/完成；记录会话终态或为何没有终态。

启动无帧观察建议默认 5 秒、持续无进展观察建议 3 秒/连续 3 个采样，**仅为候选阈值**，必须与 ready/playWhenReady、pause、buffering、seek、suppression、音频-only、封面轨、低帧率、静止图等门控一起判断。超时只生成“未观测到输出”事件，不直接认定 decoder 故障或切换播放器。

### 5.3 模式和预算（设计目标，尚未测量）

| 模式 | 捕获内容 | 限制 |
| --- | --- | --- |
| 关闭 | 不做新增详细诊断，不保留新的媒体内容统计 | 既有业务错误机制不因诊断关闭而失效 |
| 标准诊断 | 状态事件即时记录；计数器每 1 秒内聚合、通常每 5 秒输出；异常时输出完整快照 | 不逐包/逐帧字符串化，不强制获取 GPU/PCM 样本 |
| 深度复现 | 指定 trace/attempt 的 decoder/native 部分日志、有限时间戳/像素/PCM统计 | 用户确认；默认 60 秒，最多 120 秒；自动恢复原日志级别 |
| 异常冻结 | 最少目标前 30 秒、后 15 秒的有界上下文＋当前/起始快照 | 留存窗口受预算影响时显式 partial，不承诺所有洪泛情况下都不丢 |

初始预算建议：内存诊断队列约 1 MiB、每事件通常 ≤4 KiB（长异常分片总计 ≤16 KiB）、单 trace pinned 基线 ≤256 KiB；运行日志 16 MiB、4 个轮转段，异常包最多 3 份/合计 24 MiB，两者总磁盘上限 40 MiB，临时导出也计入预算。低内存设备预算可减半并记录配置。它们是容量设计值，实施测试后再固定，不能随意无上限扩张。

## 6. 公共链路：必须增加的事件与字段

每行“字段”均叠加第 4 节 envelope；下表中的 ID 同时用于验收和阶段交付，不代表现有类已经实现。

| ID / 事件 | 字段与触发点 | 要排除/定位的问题 |
| --- | --- | --- |
| C01 `diag.session.begin` | 诊断模式、开关来源、captureStartedLate、用户问题标记、所有 collector 版本和 available/status；开日志/起播各一次 | 为什么某类字段缺失 |
| C02 `env.device` | App/version/buildId/git（构建注入）、product/flavor/ABI、进程位数、OS/API/targetSDK/build fingerprint摘要、厂商/型号、低 RAM/内存、设备类别；声明的 ABI 与实际库 ABI分开 | 同名版本不同包、ARM 转译/系统差异；模拟器仅是线索 |
| C03 `env.native` | 每条播放器链实际加载的库名、相对来源、ABI、build-id/version、加载成功/失败、期望 manifest 与读取值是否一致；编译期开关 | “库在包里但没加载”、分离 FFmpeg 命名空间、用户 APK 与测试 APK 不同 |
| C04 `env.display` | 当前 displayId/mode/尺寸/刷新率/HDR types、preferred 与 applied display mode；GPU vendor/renderer/version/backend capability已有值 | HDR/display/GPU 不匹配；不要为采集另建 GL/Vulkan context |
| C05 `config.snapshot` | 当前内核、视频/音频 decode 独立策略、fallback、渲染、fixed-size、队列、tunneling、LUT/shader、音效/倍率/offset、passthrough/offload及来源 | “菜单硬解”不等于实际 decoder 或音频直通 |
| C06 `config.change` | 用户/自动原因、old/requested/effective/observed、apply返回码、需不需要重建及实际重建 attempt；mpv.conf各层覆盖次序 | 用户改了设置但旧播放器没生效 |
| C07 `play.request` / `resolve.result` | 本地匿名 mediaId、站源/解析组件版本标识、资源类型、scheme、route owner、解析耗时、返回container hints、headers名称（非值）、候选数量 | 网盘链接解析和播放器失败分界 |
| C08 `input.open` / `input.read.summary` | requestId、trace/attempt、range起止、响应码、Content-Type/Length/Content-Range、首字节/读取字节/速率、超时/重试、cache命中/读取来源 | 200/206错配、缓存/代理返回HTML、单轨数据不足 |
| C09 `input.route` | direct/App proxy/外部loopback、可见网络段、owner、upstream visibility、native/App cache归属 | 6677等外部代理不能假装看见其网盘上游状态 |
| C10 `media.container` | 实际 extractor/demuxer、容器/时长/seekable/live、variant/rendition/edition、ID映射；来源与 declared MIME分开 | 播放的不是预期原画/同名不同转码 |
| C11 `media.tracks` | 各轨 id/type/flags/language/role/default/forced/attached-picture、available/support/selected、Format、禁选/超能力/手选原因 | 没视频轨、误选封面、静音音轨、未选轨却READY |
| C12 `drm.state` | scheme UUID、session状态、secure-required、key状态/过期、license结果码/耗时、平台错误；仅结构摘要 | 等key/secure surface被误诊为硬解；不得记录key/license正文 |
| C13 `play.lifecycle` | Activity/service/controller owner、绑定/解绑定、playWhenReady/isPlaying/state/suppression及reason、前后台/窗口、播放重建原因 | 控制器暂停、旧回调、后台限制导致无音画 |
| C14 `play.clock` | master clock、position来源、audio/video PTS摘要、seekEpoch、speed/offset/sync correction、sample窗口 | 进度是音频/估算时钟而视频停滞；时钟不同步 |
| C15 `play.resources` | Java/native内存摘要、低内存/trim、温控/省电、电量状态、线程队列延迟、已有buffer压力 | 资源耗尽/线程饿死；不高频读取全进程/proc |
| C16 `play.attempt.end` | video/audio各自终态、成功证据层级、首个错误/最后错误、fallback链、release结果和耗时 | 不能用一条“播放成功”遮盖半失败 |
| C17 `diag.health` | 采集器注册结果、queue size/high watermark/dropped、写盘error/lastFlushedSeq、限流/截断数量、native event overflow、时间窗口缺口 | 区分“没有错误”与“错误日志没采到” |

对端不受控的网盘/Spider仅可传播不含凭证的诊断 requestId（协议明确允许时）；不能为了串 trace 向第三方任意加头、更改缓存 key 或泄露内部 trace。

## 7. 视频链路：Exo、Surface 与画面来源

### 7.1 Exo 解码输入与选择

| ID / 事件 | 必填证据 | 采集落点 |
| --- | --- | --- |
| V01 `video.candidates` | mime/codec string/profile/level/tier、bitdepth/chroma、尺寸/fps/bitrate、secure/tunnel；候选名/canonicalName/vendor/hw/sw/alias；系统支持与策略剔除逐项reason；最终候选顺序 | `ExoUtil` factory/selector、`ExoRuntimeAwareVideoRenderer.getDecoderInfos`、能力 inspector；仅本次相关MIME |
| V02 `video.decoder.attempt` | 每次 create/configure/start begin/end、decoderId、耗时、当前候选序号、返回或异常、fallbackAllowed及下一步；不只最后成功的decoder | MediaCodecAdapter.Factory装饰器/现有renderer路径；仅诊断，不改变排序 |
| V03 `video.configure` | 配置MediaFormat白名单：mime、width/height/max dims、profile/level、color keys、rotation、max-input-size、operating-rate/priority、低延迟/tunnel/secure相关有效参数；CSD条数/长度/摘要；绑定surfaceId/generation | adapter configure入口，记录输入实际值；不整包 `MediaFormat.toString()` |
| V04 `video.output.format` | codec实际output format、crop/stride/slice-height/color/pixelFormat/HDR摘要；input-vs-output变化；DecoderReuseEvaluation结果/丢弃原因 | renderer/adapter output-format-changed、AnalyticsListener input format |
| V05 `video.sample.summary` | 选中轨的sample/bytes/keyframe/EOS/encrypted数量、首末PTS、PTS回退/断裂、input feed age | renderer已有计数/adapter queue聚合；不复制数据，不额外demux |
| V06 `video.output.summary` | dequeued/decoded/output-skipped、release-to-surface、release-without-render、rendered/drop counters；每项定义、scope及reset epoch | DecoderCounters和adapter；tunneling与软件renderer单独注明可观测项 |
| V07 `video.first-output` | first sample、first output、first release、Media3 first-frame callback各自时间/PTS/surfaceId；不能合并 | 当前AnalyticsListener/renderer已有回调 |
| V08 `video.surface-render-callback` | 如果合法可用：MediaCodec回调PTS/nanoTime、收集时间/批量延迟、callback count、对应codec/surface generation | API保护＋复用现有adapter/renderer listener；禁止替换tunneling已有listener |
| V09 `video.flush-reuse-release` | flush/stop/reset/reinit/release起止、触发原因、最后input/output时间、在途量、结果和复用判断 | codec生命周期；release前保存允许取得的metrics，失败不再无界查询 |
| V10 `video.error` | error域/stage/codec、errorCode/diagnosticInfo/recoverable/transient、原始异常链和有限堆栈、fallback候选链 | 所有错误回调，不只onPlayerError；错误证据与策略计数隔离 |

特别规则：

- `sourceCadenceFps`（PTS间隔）、`decodedBuffersPerSecond`、`submittedFramesPerSecond`、`codecRenderCallbacksPerSecond`、`displayRefreshHz` 必须分开；不统一命名为 `renderedFps`。
- MediaCodec frame callback 在旧 Android 可延迟/批量/缺失；Android 14 起也有 executing-state条件，缺回调不独自构成无帧证据。Surface回调不证明物理显示正确。[R04]
- 通过adapter统计的“output buffer”不是逐张完整画面的通用等价物；对secure/tunnel/软件/特殊codec写明计量范围。
- `getMetrics()` 按支持情况白名单采集，不假定错误后仍能读取、不调用已release对象。build/API变化统一记采集状态。
- 原版同设备对照必须取实际 decoder、renderer、queue、Surface、库版本；名字“硬解”只作为 requested 配置。

### 7.2 Surface/窗口/封面链路

| ID / 事件 | 字段与要求 |
| --- | --- |
| S01 `surface.created/changed/destroyed` | SurfaceId/generation、类型SurfaceView/TextureView、valid、format、holder尺寸、view尺寸、创建/销毁时间；主视频/OSD用途分开 |
| S02 `surface.bind` | playerInstance/decoderId ↔ surfaceId，old/new、attach/detach结果、调用线程、是否已release；native ANativeWindow acquire/release在其owner层记录 |
| S03 `surface.resize` | media原尺寸→能力profile来源→requested fixed/layout→surfaceChanged后actual holder尺寸；带operationId；不可用同一调用栈立即回读当成完成 |
| S04 `surface.visibility` | attachedToWindow/isShown/windowVisibility/view及祖先alpha/visibility、view/content bounds、clip/rotation/transform、Z-order、当前Activity/window owner |
| S05 `video.presentation.source` | `video/artwork/shutter/loading/error/unknown`、设置来源和原因、对应selectedVideo状态；artwork只记匿名id；手机和TV同时覆盖 |
| S06 `video.effects` | 输入色彩/HDR→效果/LUT/shader/tone mapping→输出色彩/渲染后端；程序/滤镜配置摘要和apply/error结果 |
| S07 `display.change` | requested vs actual mode/refresh、frame-rate hint、HDR/色彩空间、时间和是否发生surface重建 |
| S08 `video.pixel-probe` | 用户主动触发；source明确指向视频Surface或窗口；有限3次低分辨率统计、返回码、亮度/变化率/blackRatio、间隔/耗时；不默认保存图片 |

S08只是可选证据：PixelCopy取的是最近排队的buffer，不是外部屏幕；Window截图可能只有UI，必须区分主视频Surface和封面。[R06] 保护内容、secure surface、FLAG_SECURE或API不支持时记 `permission-denied/not-supported`，**不绕过保护**。黑场、静止片段、字幕、UI遮罩都会造成假阳性。默认不采集完整截图或帧；如用户另行同意附图，进入单独的受控附件与留存流程。

## 8. 音频链路：必须从“格式”补到“实际输出”

### 8.1 分层路径

```text
选中音轨/压缩sample
   ├─ decoder → PCM → 声道映射/重采样/音效/倍速/增益 → AudioOutput
   └─ passthrough/IEC61937/编码直通（可能无audio decoder） → AudioOutput
                                                          │
                AudioTrack / MPV AO / IJK AudioTrack/OpenSL ES
                                                          │
                Android路由/焦点/系统音量 → 扬声器/蓝牙/HDMI/AVR
```

offload、直通和软件/硬件解码是不同维度；日志不能用“硬解开关=音频也硬解”或者“没有decoder=无声”替代它们。

### 8.2 必须增加的音频事件

| ID / 事件 | 必填字段 | 解释目标 |
| --- | --- | --- |
| A01 `audio.track` | 原始和当前id/codec/profile、sampleRate、channelCount及layout/mask、bitrate、language/role/selection、encoderDelay/padding、DRM | 误选/禁用音轨、2声道与多声道差异 |
| A02 `audio.candidates` | decoder/audio-output支持结果、hardware/software/FFmpeg可用性、passthrough/offload能力及查询来源、候选剔除reason | 平台支持声明、App策略和实际路径分界 |
| A03 `audio.decoder.attempt` | 每次create/configure/start/flush/release及耗时、actual decoder、异常原始码/恢复性质、fallback顺序 | codec失败与音频设备失败分开 |
| A04 `audio.decoder.output` | PCM/encoded标记、采样格式/位宽/浮点、sampleRate、channel layout、输出帧/sample数、首末PTS | decoder是否真有输出；位深/布局转换 |
| A05 `audio.processing` | 按顺序列出active processor/AF：mix/resample/map/tempo/skip-silence/EQ/增益/limiter等；输入/输出格式，配置来源，生效结果 | 滤镜把音量乘零、中心声道丢失、采样率错误 |
| A06 `audio.output.configure` | 实际outputId/sessionId、AO/AudioTrack backend、encoding与PCM/直通/offload、channelMask/indexMask/layout、sampleRate、buffer大小/单位、attributes usage/contentType/flags、tunnel/session关联 | “请求直通”与实际PCM/失败；mask仅bitCount不足以解释映射 |
| A07 `audio.output.write` | 同一outputId的调用/请求/接受字节、pending、短写/0写/错误数、首末PTS、lastWriteAge、耗时分布；异常保存raw code、阶段和原始异常 | CPU解码有输出但sink阻塞/拒写/失效 |
| A08 `audio.output.playhead` | playState/state、播放头raw uint32和扩展计数、epoch、单位；timestampValid/framePosition/nanoTime/age、position来源和速度；underrun增量 | 数据接受与音频时钟前进分开 |
| A09 `audio.route` | discovered/advertised/preferred/actual routed device分开；device类型和会话内匿名id、encodings/channel counts/masks/rates、变更reason/time；HDMI接入状态 | 蓝牙/HDMI/扬声器实际路线；不能由“连接设备列表”断定实际路由 |
| A10 `audio.focus` | owner=Media3/native/App、请求类型/attributes、结果granted/failed/delayed、gain/loss/transient/duck、pause/resume动作、effective gain来源 | 未取得焦点、被duck/mute、恢复遗漏 |
| A11 `audio.volume` | player requested/effective、native volume/mute、AudioOutput setVolume实参、已知duck倍率/音效增益、STREAM_MUSIC current/max/min、mute/fixed-volume、外部设备音量可观测性 | 多级静音；App音量1.0不代表外部AVR有声 |
| A12 `audio.output.lifecycle` | initialize/play/pause/flush/stop/release/route-recreate的起止、错误、pending bytes、旧/新outputId、offload end及延迟填充 | 多声道→立体声、seek后、路由切换后失声 |
| A13 `audio.error` | AudioSink configuration/init/write、AudioCodec error、MediaCodec diagnostic、FFmpeg/AO错误、AudioTrack raw返回、异常链、事件时route/config | 原始错误不能只剩一个Exception类型 |
| A14 `audio.pcm-probe` | 用户明确开启，有限窗口，输入/后处理/输出检查点、frameCount、per-channel RMS/peak/zeroRatio/clipping/NaN；PCM格式与归一化 | 区分源静音、解码全零、滤镜输出静音；不是声音录制 |
| A15 `audio.sync` | clock来源/epoch、audioPTS/videoPTS差、音频延迟/用户offset/tempo/drop补偿、时间戳有效性 | 断续、音画漂移与真正无声分界 |

### 8.3 实施时不能踩的坑

- 在现有 `ExoUtil.buildAudioSink` 的最终 provider 路径接入 forwarding，保留直通/offload、多声道、audio processors、设备能力动态更新等行为。不要为了日志换成“简化 AudioTrack”。
- `ForwardingAudioSink.handleBuffer` 的消耗量仅说明 sink接收，不等于底层每次 `AudioTrack.write`。利用 `ForwardingAudioOutput` 观察真正输出层；确需底层原始写返回码/头部写入等时再加窄 hook，并标明哪个计数是 payload、哪个是内部头部。
- 当前基线 `AudioTrackAudioOutput` 暴露实际 `AudioTrack`，可在合法owner线程/API保护下读状态、route、metrics；不能在HTTP/UI线程持锁跨层读、不能调用对象的play/flush/写入来探测。
- buffer bytes→frames 的换算仅适用于已知线性PCM和正确的每sample字节数；编码直通/offload按其API定义记录，不用 `channels×2` 硬算。
- `getPlaybackHeadPosition()` 是uint32，会wrap且flush/stop会reset；按outputId/epoch计算，不把回零当倒播或卡死。[R05]
- `getTimestamp()` 返回false时保留availability状态，不读取旧结构当新样本。route/模式切换后允许短期不稳定；稳定时直接调用约10–60秒一次，复用Media3已有tracker数据，禁止为日志再每帧调用。[R05]
- 时间戳可代表已呈现或已承诺将呈现的数据；不证明外部AVR实际发声。`getRoutedDevice()` 非播放期可返回null，此时不直接判为路由失效。
- 系统自动duck、来电静音可能没有完整App焦点回调；只记录App能看到的结果，不能因“没loss日志”排除系统静音。[R03]
- A14不得申请麦克风权限、录制环境声音或保存原始PCM。只在非保护PCM可观察点算低频聚合；直通流标not-applicable，不能解释为zero amplitude。
- 声道日志必须有layout/mask和mapping；`channels=6` 无法区分侧环绕/后环绕/中心声道映射。外部扬声器物理接线、AVR模式要由用户观察补充。

## 9. MPV、FFmpeg、JNI 和 IJK

### 9.1 MPV事件与属性

| ID / 事件 | 必须输出到调试日志的内容 |
| --- | --- |
| M01 `mpv.init` | libmpv/FFmpeg/libplacebo/JNI版本/ABI/buildId、load/init结果；源revision来自构建manifest，不从App版本猜 |
| M02 `mpv.option` | 白名单的mpv.conf/per-file/App策略/命令覆盖、requested/effective/readback/status；设置失败的原始返回码与`mpv_error_string` |
| M03 `mpv.event` | start-file/file-loaded/video-reconfig/audio-reconfig/playback-restart/end-file/shutdown；end reason/error；client/source sequence、generation |
| M04 `mpv.tracks` | `track-list` 的available/selected/default/attached-picture、实际 `vid/aid/sid` 和current-tracks id、input metadata；区分不存在/暂时未选/失败后禁用 |
| M05 `mpv.video.path` | `hwdec`配置 vs `hwdec-current`实际、hwdec-interop、video-codec、decoder/FFmpeg具体实现；`current-vo`、gpu API/context/backend；video-dec/params/out-params分层 |
| M06 `mpv.audio.path` | audio-codec/decoder、`audio-params`、`audio-out-params`、`current-ao`、audio-device、volume/mute、AF配置、delay/speed、SPDIF实际格式/降级reason |
| M07 `mpv.runtime` | time-pos、video-pts等可用PTS、cache/buffering、pause/idle、avsync、frame-drop-count、decoder-frame-drop-count、mistimed/vo-delayed及各自有效性；不能把estimated-vf-fps当显示FPS |
| M08 `mpv.decoder.attempt` | 来自native初始化/失败回调或日志解析：codec名/具体hwdec、GL/Surface/native_window关联是否建立、顺序/阶段/错误码、copy/direct/software候选；查名与实际create/configure/start按9.3.1区分；事实来源是结构化native还是文本推断必须明确 |
| M09 `mpv.output.failure` | 有video轨但初始化失败后vid=no，audio仍活跃时：保存videoPartialFailure=true、原始错误、最后成功层；不只生成整机READY |
| M10 `mpv.command.result` | loadfile/seek/track/option/attach等commandId、提交/返回/完成、错误码/耗时、generation；readback缺失不冒充成功 |
| M11 `mpv.collector.health` | property注册format/result、MPV_FORMAT_NONE/unavailable、freshness、node解析失败、event queue overflow、native→Java→writer各段延迟、drop计数 |
| M12 `mpv.native.output` | 必要的ANativeWindow/Surface、EGL/Vulkan、hwdec interop/VO present失败，以及AO实际AudioTrack/OpenSL状态；拿不到的字段明确native-hook-required |

`hwdec-current` 的 `no` 在活跃解码时代表软解；**未加载decoder时属性不可用，不等于no**。`audio-params` 是decoder输出、`audio-out-params` 是写给音频API的格式。[R08]

### 9.2 MPV采集的线程与可靠性

- 复用现有 `MpvPropertySnapshot` 和观察器，给每个值增加更新时刻/generation/status；快照不保证跨属性原子一致，记录采样跨度，丢弃跨generation拼接。
- 优先在事件线程把必要字段复制为有界不可变消息，再交给日志writer；必须及时清空mpv event queue。[R07]
- native log callback不能同步反查MPV属性、等待主线程、做文件写入/HTTP/复杂JSON；现有直接SpiderDebug写入最终也要进入无阻塞队列，不让持锁文件I/O反压native。
- 少量需读回的属性仅在安全线程用受控异步请求；总在途数有界，记录request/replyId、generation、超时和unsupported；timeout不调用强杀/销毁decoder来“解除诊断阻塞”。
- node/字符串必须在mpv event内存失效前复制，限制大小/深度，明确截断；handler/release卸载observer，忽略晚到事件但增加统计。
- 不开启全局每帧 `all=trace`。标准模式保留错误/警告与初始化/选轨/实际VO/AO关键信息；深度模式限定组件如vd/vo/ao/demux和指定trace，超时恢复。
- 取消当前32条/5秒的统一丢弃窗口；标准诊断开启期间，纳入采集级别的事件持续输出到异步writer。容量充足时不得仅因条数/时间配额丢弃事件；实际队列拥塞时按关键证据优先保留、重复消息汇总及损失报告处理，具体契约见9.2.1。
- MPV `PLAYBACK_RESTART` 是seek/播放重新初始化信号，不是视频frame-present信号。[R07] 新日志必须纠正解释，但不顺手改既有播放状态机。

### 9.2.1 过滤前取证与没有后续日志时的收尾（本轮补充）

**持续输出契约：** 标准诊断从开启一直采集到关闭或进程结束，包括播放器没有报错、用户没有打开日志面板的期间。文件由后台writer批量追加，达到单段大小后继续写新段，按11.1的总字节预算淘汰最旧片段；报告声明实际保留时间范围并固定起播/故障上下文。文件轮转不暂停采集，也不以“每几秒最多几条”代替容量管理。持续采集的范围由声明的日志级别/collector决定，不等于无限保存历史；5.3的逐帧/逐包深度复现限时独立适用。

1. **覆盖源端级别。** M02/M11记录 `mpv_request_log_messages` 订阅级别、有效 `msg-level` 及组件覆盖。当前 App 标准路径可能使用 `all=warn`，而 FFmpeg 的 `Failed to getCodecNameByType` 是 INFO；只扩大 Java 队列不能找回源端未发出的信息。标准诊断须通过有界的组件级初始化消息或结构化 hook 取得必要查名/失败摘要，不要求用户另开全局 verbose。源端低级别消息未生成、无法计数时，明确过滤范围与不可观测计数，不编造0。
2. **持续记录与拥塞处理分开。** native/JNI/Java 不沿用统一条数配额；普通负载下持续传递所选级别的事件。周期指标按预先声明的schema聚合；重复消息若合并，必须带count/first/last，不能合并不同attempt/操作的事件身份。新错误、尝试边界和最终失败形成有界固定摘要，实际队列容量不足时优先保留。原生 severity/domain/stage 要传至结构化事件及关键队列；只经旧文本入口成为 normal 优先级，或仅豁免 `WebHTV FEL fatal`，不满足普通 HEVC 初始化失败的验收。文本解析必须标为 inferred，日志分类不触发新的播放恢复动作。
3. **各段分别核算。** M11/C17区分源端级别过滤、native event overflow、Java限流、应用队列丢弃和writer失败，保留可得的累计count/firstSeen/lastSeen/sourceStage/attempt。sink 的零丢弃不能代表前段零损失；某段不可观测则报覆盖缺项，必要错误摘要丢失时 report 必须 partial。
4. **不能等下一条获准日志才报告。** 限流计数和最后失败摘要保存在有界缓存；attempt结束、诊断模式切换及TXT导出均可读取，周期收尾也不能依赖再次收到native消息。HTTP导出只读缓存，不同步反查MPV或等待UI；重复导出使用带generation/窗口的累计统计，不能一次取走计数让下一份报告假装零损失。关闭/清空仍遵守现有D0删除语义，不要求关闭后恢复已删除日志。
5. **持续记录及静默尾部验收。** 在真实native→Java入口或等价契约fixture，队列/磁盘容量充足时，于5秒内发送超过32条不同的已纳入采集级别事件，再发送新的codec查名/初始化失败，随后完全停止native日志；保持播放未结束直接导出，再结束attempt导出。两份TXT都须按事件身份完整保留这批事件和最后错误，不能仅因旧窗口配额发生丢失。另以实际队列耗尽/磁盘失败注入验证损失的来源/计数/时间范围及partial，无后续事件也能导出；持续记录跨文件轮转仍有连贯seq及明确的历史淘汰边界。还须覆盖标准日志级别下INFO查名失败及sink零丢弃但上游有缺口；仅向D0 sink人工注入日志不算验证了上游链路。

### 9.3 FFmpeg/nextlib与native扩展

需要记录 `avcodec` implementation、hwdevice/hwframes创建、AVCodecContext输入/实际pix_fmt、sample_fmt/channel layout、thread配置、extradata长度/摘要、open/send/receive失败的数值与可读错误、EAGAIN/EOF统计和flush epoch。EAGAIN是正常背压，不当作解码失败刷屏。

- 保留Exo扩展FFmpeg、MPV FFmpeg、IJK FFmpeg各自的库身份与owner，不共享/替换 `.so`。
- `av_log_set_callback` 要求线程安全，默认日志到stderr不保证进App调试日志。[R09] 在各播放器已有日志owner处扩展桥接，不随意替换全局callback、级别或其他模块handler。
- 不将日志中的native对象地址作为公共ID，不解引用外部未知指针获取ctx信息；未能绑定attempt的日志写 `association=unresolved`。
- 新FFmpeg/MPV/JNI插桩若确有必要，进入独立native阶段，保留源锁/补丁/ABI/SONAME/DT_NEEDED/导出兼容与精确artifact记录；不能用Java日志完成冒称该阶段已完成。

### 9.3.1 MediaCodec查名失败的操作边界（本轮补充）

M08不能只收集“初始化失败”和另一次Java能力枚举：必须能解释**本次实际native选择器**为什么未返回名字，并区分查询失败与确实没有合格候选。记录下列证据，按decoder attempt/lookup operation关联；同样的失败文字可属于不同direct/copy/重试，不能据正文合并身份。

| 取证边界 | 必填内容与语义 |
| --- | --- |
| 请求与分支 | 实际mime、AVCodecContext profile与Android profile的数值/名称/命名空间、MediaFormat覆盖来源、hardware_only/allow-profile-mismatch等有效标志、`use_ndk_codec`；与菜单“硬解”分开 |
| 实际枚举 | 枚举API/路径、已知总数、本MIME候选、实际检查数/中止位置；本次访问到的候选名或枚举序号、hw/sw/encoder声明、profile列表及可用状态；不另建解码器做能力试探 |
| 每个候选的处理 | 原生实际顺序及accept/reject/not-visited，具体规则：encoder、hw/sw声明、名称过滤、MIME/alias、profile匹配等；原始能力为空、查询未执行、查询失败要分开。API异常不能伪装成“不支持该profile” |
| 查名结果与重试 | selected/no-eligible-codec/query-error及对应证据；JNI/JVM、方法映射或能力API失败保存操作、原始错误/有限异常链；profile-mismatch重查单独标识请求变化和结果，不悄悄覆盖第一次失败 |
| 后续真实操作 | 每次create-by-name/create-by-type/configure/start的attempted、begin/end/result；Java查名失败提前返回与NDK按MIME继续创建明确分开。通用`failed to start`只保留为原文，实际stage从操作证据得出；未采集时为unknown，不能自动填`createAttempted=false` |

本地 `mediacodec_wrapper.c:ff_AMediaCodecList_getCodecNameByType` 把多种返回NULL路径合并；`mediacodecdec_common.c:mediacodec_dec_get_video_codec` 在未返回名字时按Java/NDK分支采取不同后续动作，外围失败出口仍可打印 `MediaCodec %p failed to start`。[R18] 因而 Exo V01 的候选快照只能作对照，不能冒充 MPV 选择器已访问/拒绝了同一候选。Surface为空也须绑定具体direct/copy和操作阶段，不能跨尝试归因为同一个Surface错误。

实施归属：D3先交付public API和现有native日志可得部分；若本次选择器的原因仍不可得，M08明确 `native-hook-required`，由D5中单独批准的窄hook补齐。D3可以按其声明范围交付，但不能据此宣称本样本的查名原因已闭环。此条只增加取证要求，不授权修改profile过滤、启用软件fallback或改变解码器顺序。

### 9.4 IJK最小同等覆盖

从 `IjkSimplePlayer.onPrepared/onInfo/onError/onVideoSizeChanged`、`IjkPlayerEngine`现有快照获取：what/extra原始值、decoder类别与名字、选中轨、fps/cache/delay等可用属性、first-video-render/first-audio-render info、buffering、Surface和生命周期；与公共route/focus/volume串联。

IJK走OpenSL ES时不能声称有Java AudioTrack对象。没有原生write/head/route证据时明确unknown并分配后续hook；不得伪造与Exo完全相同的字段覆盖率。

## 10. 异常、卡死、进程退出和证据完整性

### 10.1 错误结构

`errorId/domain/stage/nativeCode/javaClass/message/diagnosticInfo/recoverable/transient/operationId/attemptId/firstSeen/lastSeen/count`；一次新错误保留经过脱敏的cause、suppressed、decoder fallback chain和有限堆栈。设置最大深度、循环检测、总长度及chunk编号，长异常不能被旧12000字符上限截掉而无提示。

错误打印不改变策略状态；特别是“遍历fallback异常链做诊断”不能被现有runtime profile当成多次独立故障，导致额外黑名单。

### 10.2 线程卡死时仍可交付

- 诊断/HTTP导出读缓存和已落盘快照，不依赖主线程调度或`getProperty`成功。记录collector last heartbeat/queue delay。
- 用户标记黑屏/无声时立即固定已有上下文；完整snapshot超时则输出partial和超时component，不整次导出等待native。
- Java崩溃捕获与既有handler兼容，只做有界最小落盘；不吞异常、不执行网络上报、不阻塞任意线程等待日志锁。
- native signal handler不能调用JNI/Java/普通锁/复杂格式化；使用已有安全崩溃路径或下一次启动恢复，不为本需求贸然建立新崩溃框架。
- journal保存last active trace/attempt、各层最后进展、write seq；next boot合并上次unfinished标记、已有PlaybackRecoveryMonitor记录和公开可得的系统退出信息。
- Android API30+按权限读取自身 `ApplicationExitInfo` 摘要；ANR trace可能存在，API31+native crash tombstone可能存在，但系统环形缓存会丢失。[R10] 返回null/不可读均应记录，不能保证一定获得native stack。
- `cacheDir`可能被系统清理；故障journal/固定异常包规划在App私有持久目录，容量有界，并有清空/删除策略。移除App/清除数据/突然断电仍存在不可恢复边界。

### 10.3 完整性状态

导出manifest至少包含：`schema/build/processRun/trace范围、collector覆盖、start/end seq、gaps、droppedByPriority、truncatedChunks、nativeOverflow、writeFailures、lastCapturedSeq/lastFlushedSeq、closed/unclosed、captureStartTime、logMode、文件长度与SHA-256`。

`completeness=complete` 只指**声明模式/时间窗/可用collector内的证据完整**，不表示物理世界全部可观测。异常包缺起始配置/轨道/实际decoder/output或存在关键缺口时为partial，并列出具体缺项；不能只报一个绿色“日志导出成功”。

## 11. 存储、UI和隐私安全

### 11.1 日志存储必须先改，不能先无上限加字段

1. 保留SpiderDebug公共兼容入口；新结构化事件和旧文本统一进入非阻塞有界队列，单writer批量写盘，不在codec/native/音频线程逐条打开文件。
2. 两级优先队列：状态/新错误/配置/终态优先，周期样本与重复文本可合并；“关键永不丢”在有限内存下不可绝对承诺，必须有保底摘要及丢失记录。
3. ring只放运行窗口，起始环境/配置/选轨/尝试链另做有界pinned快照，避免几分钟后问题只剩末尾几行。
4. 日志文件轮转，active trace元数据可重建；写盘失败记录独立计数/内存告警，避免记录写盘错误时无限递归写同一失败sink。
5. 普通flush按批次，异常可请求有界快刷；不在实时线程fsync每条日志。导出记录flush watermark和超时；不保证系统强杀前最后一微秒落盘。
6. 区分“停止采集”和“清空记录”。目前关闭会clear，后续若改为保留历史，UI必须明确提示、提供删除和到期清理；这是需审批的用户可见语义变化，不能偷偷实施。
7. HTTP增量改为有界cursor/afterSeq，报告oldest/newest/gap；保留旧TXT入口的一致有界快照，后台流式输出，不在每次网页刷新拼接全文件。

### 11.2 导出内容与界面

建议包结构（示意，非本轮已生成产品功能）：

```text
webhtv-av-report-<reportId>.zip
  report.txt                  可直接读：症状、最后正常层、错误链、缺项、下一步
  webhtv-debug-log.txt         兼容现有调试日志，包含AV结构化事件和必要旧文本
  av-events.jsonl              与TXT一致的结构化事件
  session-snapshots.json       环境/初始配置/能力/轨道/错误前后/终态
  manifest.json               完整性、schema、版本、脱敏级别、各文件校验
  optional/                   用户另行同意且允许的附件，不默认截图/音频
```

摘要和结构化附件的核心信息也必须进入TXT，不能要求用户必须解压JSON才知道当前decoder、音频route和失败阶段。面板字段与导出使用同一数据源；不开面板也自动收集。

TV界面支持焦点移动、一次按键标记症状、查看/复制局域网地址或二维码、下载失败提示。网页不只提供关键词过滤，还可按trace/attempt/音视频/优先级查看；过滤不能导致下载丢掉未显示的必要上下文。

### 11.3 隐私与访问控制

- 默认只记URI的scheme/路由类型/会话内匿名标识；URL path/query/fragment、网盘sToken/share token、Cookie、Authorization、Referer可能含凭证，不能原样落盘。
- config、header、异常/native自由文本都可能含密码；使用字段白名单＋统一敏感值脱敏，不依靠单个“遇到URL替换”的正则当作完整安全边界。
- 不记录DRM key/license/session私密数据、媒体原始字节、音频/视频原始sample、设备serial/MAC/账号/局域网完整拓扑。必要设备名称/地址用report内盐化标识。
- CSD/extradata只记录类型/长度及适当摘要；保护内容默认不做内容指纹/PCM/像素probe。可比对的媒体样本hash仅限用户明确提供的测试样本，不自动下载全片求hash。
- 白名单过的错误字符串仍需长度上限、CR/LF/控制字符转义、HTML/JSON正确编码；不能让站源或媒体metadata伪造结构化event。[R11]
- 深度模式/附件/远程导出单独提示用户；不自动上传用户日志，不因诊断而扩大现有远程托管授权。
- 2026-09-16用户明确要求去掉调试日志配对，按14.16取消配对码/token身份认证，保留POST、设备已知Host和严格同源Origin、操作限频、导出预算与脱敏；同局域网可访问设备者可直接操作。Origin是浏览器跨站请求防护，不冒充用户身份认证，不改变其他远程托管授权。
- 启停/清空维持POST，GET不产生修改；缺失/null/异常Origin的修改请求拒绝，TXT/stream/status只读接口保持原用法。

## 12. 根因判定矩阵

“证实”是具体层内事实；跨层推论用likely/unknown。自动摘要必须输出命中的证据ID，而不是一句“设备不支持”。

| 症状与组合证据 | 可作出的判断 | 仍需的最小区分步骤 |
| --- | --- | --- |
| open/read失败，没有可识别tracks | 输入/网络/代理/容器层失败，不是已证实的decoder故障 | 对照实际响应与代理owner，不盲目切解码 |
| videoAvailable=true、selected=false、有禁选reason | 轨道选择/策略未启用视频 | 检查一次手选/限制参数的requested/effective |
| native查名未返回codec名，后有通用`failed to start` | 查名结果已知；创建/启动是否执行仍须分支与操作证据，不能只按错误文案定stage | 取9.3.1本次候选处理/JNI查询/Java或NDK后续链，区分无合格候选与查询失败 |
| create/configure/start明确异常，含候选和错误码 | 当前decoder尝试在该操作失败 | 对照下一个候选/原版实际decoder；能力声明不能推翻错误 |
| 有input无output，未pause/seek/等待key且超过窗口 | decoder/输入数据之间停滞候选 | 看keyframe/CSD/DRM/错误；不能仅超时定厂商bug |
| 有output/release，surface invalid或绑定到旧generation | 绑定/生命周期异常证据明确 | 对照surface重建操作和decoder绑定事件 |
| output/release持续、UI shutter/artwork盖住 | UI展示路径候选很强 | 记录遮罩来源/可见性；保持硬解做UI/渲染单变量对照 |
| fixed-size请求紧邻失画，无后续实际尺寸/回调证据 | Surface尺寸调整是候选，不是根因结论 | 保持硬解，只关固定尺寸并重入；保留A/B report |
| videoSelected=false但audio active且MPV HEVC init failed | 明确视频局部失败；静态图不是影片首帧 | 检查硬解尝试链，不把audio READY当全成功 |
| renderer/Surface回调正常、有效像素统计也有变化但用户仍黑屏 | 已到可观测显示边界；可能窗口合成/外部显示/观察差异 | 用户实拍或系统/厂商诊断；不能自动判“用户看错” |
| 没有选中音轨/aid=no | 选轨问题或音频初始化失败后的结果 | 看禁选原因与audio error先后 |
| audio decoder明确失败 | 解码层失败 | 逐个candidate错误/扩展库加载状态 |
| PCM输出有、processor之后全零 | 滤镜/增益或源内容问题候选 | 比对pre/post统计与配置，只改一个滤镜/增益 |
| source PCM本来全零 | 源轨当前区间静音候选 | 同一PTS另一音轨/已知测试片；静音片段不算故障 |
| 有音频输出但write错误/0写持续、playhead停 | AudioOutput/设备交互异常候选 | raw返回、route/epoch/重建前后；区分pause/背压 |
| write/head正常、有效volume为0或明确focus loss/duck | 可证实的App/系统策略静音因素 | 恢复单个已知静音因素再确认，记录请求结果 |
| write/head正常但路由是蓝牙/HDMI非预期 | 路由差异证据 | 用户确认实际输出设备；preferred不能替代actual |
| 直通active，PCM probe不存在 | 正常采集边界，不是“无PCM导致无声” | 在用户同意下单独关直通做A/B，检查外部设备支持 |
| 单次播放正常、换声道/route后无声 | 重配置/设备残留状态候选 | 对照old/new outputId、release、encoding/mask、设备能力变化 |
| write/head非零、各增益和route正常，用户仍无声 | App证据到音频呈现/路由边界；不能排除系统自动mute或AVR/扬声器 | 用户确认AVR/系统输出或厂商信息；不声称唯一根因 |
| report有关键seq gap/collector unavailable | 证据不足 | 开启指定缺失collector做一次复现；不能结论性排除未观测层 |

## 13. API与设备兼容策略

| 能力组 | 版本/限制 | 设计要求 |
| --- | --- | --- |
| 普通播放器事件/Java异常/日志导出 | App支持的全部Android | 作为基础层，TV/手机同覆盖 |
| MediaCodec错误详情/Output format | 依具体API与codec模式 | 新字段逐项SDK保护；异常不会因读diagnostic再次崩溃 |
| MediaCodec frame rendered listener | API23+；旧系统不保证逐帧回调；与tunneling现有listener冲突风险 | 不可默认抢占；缺失标可用性边界 |
| AudioTrack timestamp/playhead | timestamp为公开API；具体route可能不支持，单位/reset不同 | 正确validity/epoch；不调用hidden getLatency |
| actual routed device | API23+；播放状态影响null；现代AudioRouting监听按SDK适配 | 已连接与实际路由分开；listener卸载 |
| underrun/buffer capacity | 多项API24+，buffer相关API需逐项核对 | 旧设备缺字段非0值 |
| MediaCodec/AudioTrack metrics | API26+相关公开接口，字段因实现/版本而异 | 白名单，release前快照；不依赖vendor键稳定 |
| codec hw/sw/vendor/alias、performance point | 主要API29+，低版本只可有限推断 | low confidence/claimed与实测结果分开 |
| PixelCopy | 按具体重载API24+/26+保护，secure/没有buffer可能失败 | 用户主动、有限频率、失败码不是黑屏根因 |
| ApplicationExitInfo | API30+；native tombstone API31+且不保证保留 | 只自身公开可读信息，缺失明确 |
| 音频焦点 | API26+请求模式、API31+系统fade/duck、target35+请求前台限制等 | 同时记录runtimeAPI、targetSDK、前台服务状态；不扩大权限 |
| MPV properties/native能力 | 按实际加载版本/构建探测，不假定同名App相同lib | 注册失败/属性不可用保留；可用列表写manifest |
| IJK/OpenSL ES | 不一定有Java AudioTrack | 不伪造route/write等字段 |

实施时以项目最终SDK与AAR确认精确方法签名和minAPI，补测试。本文是设计门禁，不能用“源码里有该方法”跳过最终artifact可达性检查。

## 14. 改造位置、分期及回滚

### 14.1 拟修改位置（不是本轮授权修改清单）

| 层 | 现有落点/拟新增小组件 | 边界 |
| --- | --- | --- |
| schema/collector | `player/PlaybackTrace.java`、`PlaybackTelemetry*`；拟增`PlaybackDiagnosticEvent/Session/Collector`等小组件 | 一套语义，兼容原日志；不替代播放控制器 |
| sink/留存 | `catvod/.../SpiderDebug.java`、`DebugLogStore.java` | 支持旧调用，新增结构化事件/有界队列/轮转/健康状态 |
| 无ADB入口 | `server/process/DebugLogs.java`、`ui/dialog/DebugLogDialog.java`，手机/TV入口 | 单一数据源、遥控器可用、导出不依赖playback线程 |
| 环境/能力 | `setting/Setting.java`、`player/codec/CodecCapabilityInspector.java`、实际native loader | 只查本次媒体相关项，不每秒枚举所有codec |
| Exo | `PlaybackAnalyticsListener.java`、`ExoUtil.java`、`ExoRuntimeAwareVideoRenderer.java`、`ExoPlayerEngine.java` | public hooks优先；不重写decoder策略 |
| 音频公共 | `AudioPlaybackDiagnostics.java`、`PlayerManager.java`、现有AudioOutput/provider路径 | 保持音效/直通/offload/焦点owner，不注册竞争控制器 |
| UI/Surface | `ui/activity/PlaybackActivity.java`、手机/TV `VideoActivity.java`、PlayerView适配 | 使用生命周期回调采集实际尺寸；不为采集额外resize |
| MPV | `androidx/media3/mpvplayer/MpvPlayer.java`、`MpvDiagnosticsPolicy.java`、`MpvPropertySnapshot.java`、`is/xyz/mpv/MPVLib.java` | 缓存/异步，不回退到主线程同步大批JNI读取 |
| JNI/native（有缺口才动） | `third_party/mpv-player-jni/src/event.cpp`及经批准的MPV/FFmpeg/Media3诊断hook | 独立native阶段、独立原子回滚 |
| IJK | `player/engine/IjkSimplePlayer.java`、`IjkPlayerEngine.java` | 已有属性优先，无能力明确unknown |
| 崩溃/恢复 | 现有`PlaybackRecoveryMonitor`及App启动路径 | 不改变退出/恢复策略；仅串联上次会话证据 |

### 14.2 分期顺序

| 阶段 | 交付范围 | 退出条件 | 回滚 |
| --- | --- | --- | --- |
| D0 公共最小底座 | envelope/隐私/有界sink/关键快照/导出健康、C01–C17必要上下文 | 开/关/洪泛/磁盘失败不阻塞播放；TXT能恢复会话；未知不伪装0 | 独立功能开关与原子commit，保持原导出兼容 |
| D1 Exo视频与公共Surface | V01–V10、S01–S07，纠正首帧/FPS日志含义 | 黑屏/封面/硬解失败/Surface重建fixture可区分；不改播放策略 | Exo collector独立关闭，撤回对应commit |
| D2 Exo音频与系统路由 | A01–A13/A15、PCM可用路径、AudioOutput桥接 | 解码/写入/路由/音量/焦点/直通各失败可区分；旧行为无回归 | 音频collector独立关闭；不改变输出默认值 |
| D3 MPV | M01–M12中现有public API可得部分、native log关联、局部失败、queue健康 | HEVC init失败＋音频继续必须报视频局部失败；9.2.1源端级别和静默尾部可验收；9.3.1缺项明确归属D5；无主线程同步查询回归 | Java/JNI边界分清，若改JNI需整组兼容回滚 |
| D4 IJK与恢复闭环 | IJK最小覆盖、journal/exit-info、跨进程完整性 | 旧内核/旧API缺项被正确表达；卡住/杀进程后可导出已有证据 | 独立开关、保持既有恢复行为 |
| D5 经批准的深度探针/native缺口 | S08/A14、必要codec/VO/AO低层hook、A/B报告 | 有明确用户同意、限时、成本/安全/ABI门禁；观测边界不虚假消失 | 深度默认关，native阶段独立锁/产物/commit/tag |

公共底座为Exo必需的陪同项；播放器实施顺序仍为 **Exo → MPV → IJK/公共闭环**。D5不是默认必须全量native重构：每项先证明现有public hook缺口。待批准实施时按真实工作树、设备与构建缓存给逐阶段当前agent墙钟估计，不在本文虚构未来构建耗时。

所有阶段都需单独明确scope、现有脏文件保护、测试证据和本仓库规定的原子commit/annotated recovery tag。用户已批准从 D0 开始；完成 D0 不代表整个需求完成。

### 14.3 D0 实施决定（2026-09-14）

- 授权：用户在明确的“开始分阶段实施 / 先归档并评审”问题中选择前者；本轮执行恢复锚点指定的 D0，不同时开工 D1–D5。
- 完成目标：复用 SpiderDebug/DebugLogStore，建立有界异步写盘、结构化会话与固定上下文、统一脱敏、可判断缺口的 TXT 导出及增量网页读取；诊断失败不改变播放器策略。
- 当前代码核对：原设计至当前 HEAD 的相关提交均为 FEL 修复，D0 sink/HTTP/PlaybackTrace 尚未实现本方案。`DebugLogStore.add/writeLocked/loadLocked` 仍持锁同步 I/O 且无总量上限；`DebugLogs.stream` 仍反复返回全文；`ProxySetting.collectDebugUrls` 消费日志中的 URL，必须在脱敏时保存其域名建议功能。
- 范围：`catvod/.../crawler/{DebugLogStore,SpiderDebug}.java`、新增 `crawler/diagnostics/**` 及对应单测；App `PlaybackTrace`、新 `PlaybackDiagnosticSession` 及对应测试；`Setting`、`ProxySetting`、`server/process/DebugLogs`；本文件和任务索引。保护已有 `app/.cxx/`；不改 AAR、native、锁、解码/选轨/输出/恢复策略。
- 设计选择：保留同步原 sink 会继续阻塞播放且无法限制资源；全开 EventLogger/native verbose 仍缺完整性并泄露外部文本；采用原入口后接单 writer、有界队列/轮转、固定快照与独立健康统计。队列拥塞优先舍弃周期/旧文本，但关键事件也可能丢失，必须报告；写盘失败不能递归写自身。
- 身份与事实：沿用每个 PlaybackTrace 的身份并补进程 run、实例和 generation；只有明确回调产生的阶段是 observed。READY、兼容 first-frame/audio-playable 只记控制器信号，视频可见/声音可闻均为 not-observable；D1–D5 尚无 collector 的层列为 not-collected，不声称覆盖完整 62 类事件。
- 留存与并发：标准队列 1 MiB，内存读取窗口 1 MiB，pinned 总量 256 KiB，磁盘 4×4 MiB；单 writer 批处理/轮转，采集线程不做文件 I/O。TXT 导出使用有界等待与独立快照；writer 不响应时返回内存 partial 和明确 watermark。临时导出纳入 40 MiB 总预算，不保留无限份文件。
- 隐私与兼容：结构化字段白名单，旧文本在进入内存/磁盘/平台日志前统一脱敏、限制长度、转义换行及控制字符；异常限制深度、帧数和循环。代理建议仅保存有界、本机内存中的 origin/域名，不导出凭证或 URL path。保留“关闭即清空”和现有 TXT 下载地址。本阶段不增加 mark/deep-capture/ZIP 远程操作；原 LAN 读取/启停权限模型保持现状，鉴权迁移是新增敏感操作上线前的后续门禁，不能据此宣称 T22 全部完成。
- 基础上下文：D0 交付 session/device/config/request/resolve/controller lifecycle/end/health 与各 collector 的覆盖状态。codec/sample/AudioTrack/Surface/native 实际输出由 D1–D4 接入；未取到不补零、不复用 requested 冒充 observed。开日志过晚、日志清空、进程重启和截断均列入完整性。
- 证据复用：第17节 R01/R02/R11/R12/R13/R14 的原始快照仍在原 `evidence/`，2026-09-14 按 D0 问题复核其相关正文与当前源码；不合入任何上游提交。官方事件媒体关联、OWASP 敏感字段/注入/资源耗尽与写失败、成熟项目桥接以及维护者观测边界支持本决定。无新算法，论文/通用 benchmark 不适用；并发和性能以真实 sink 故障注入及三次可比测量裁决。
- 验收：有界/关键优先/轮转、写盘失败与超时导出、清空与并发写竞态、截断/seq gap、水位、字段/异常脱敏、旧文本和结构化 JSON 防注入、会话隔离/晚开启/非物理成功语义的定向单测；相关 Java 编译和 App 导出检查。构建只用已有二进制，不重建 native；设备不可用时明确记录，不能把构建代替设备性能验收。
- 预算：2026-09-14 12:44 Asia/Shanghai 起，约45分钟，预计13:29；设计归档6分钟、实现24分钟、验证提交15分钟。基线性能与设备信息以实际取得的证据记录，不虚构。
- 回滚：独立 D0 原子 commit/tag；撤回该提交整体恢复旧 Java sink/读取通路，native 产物不受影响。关闭诊断立即停止新增采集并按既有语义清空。

### 14.4 D0 实施与验证记录

- 已实现：`DiagnosticLogBuffer` 单 writer、有界内存/队列/pinned、优先保留关键事件、generation 清空隔离、游标缺口和健康统计；`RollingDiagnosticFile` 4 段轮转、历史脱敏、单份临时 TXT 导出、前置内容 SHA-256 与超时缓存回退；`DiagnosticText` 统一旧文本/异常脱敏，`DiagnosticEvent` 版本化白名单与固定未知状态。
- 已接入：SpiderDebug/DebugLogStore 原入口、PlaybackTrace 控制器会话与 prepare attempt、Setting 设备环境/晚开启基线、网页增量日志/音视频过滤/TXT 下载。`ProxySetting` 改用仅保存在本机内存的最多200个 origin；`ShellProxyDialog.suggestRules` 已核实只消费 `suggestion.hosts()`，不依赖凭证或资源路径。
- D0 事件覆盖：C01、C02、C05 的请求配置、C07 请求/解析、C11 的控制器轨道摘要、C13 控制器阶段、C16 未知音视频终态、C17 健康统计。C03/C04/C06/C08–C10/C12/C14/C15 及实际 codec/Surface/AudioTrack 仍明确 not-collected；C11 不声称逐轨完整选轨覆盖。控制器当前 trace 不冒充来源回调的媒体身份，`association=controller-context-only` / `eventMediaIdStatus=not-collected` 保留边界，D1/D3 才能补来源关联。
- 首轮验证：JDK21 / 现有 Android37.0 SDK，Gradle 定向35项测试通过（新日志17项、新会话4项、原 Trace7项、PlaySpec4项、Exo trace3项），Mobile/Leanback arm64 Java 编译通过，耗时2分26秒。仅出现原有资源命名空间、Room查询字段和废弃 API 警告。完整输出：原证据目录 `d0-gradle-2.log`；首次沙箱缓存锁拒绝为环境错误，获准访问已有缓存后完成。
- 网页验证：从实际 Java 字符串提取的脚本通过 Node 语法与执行检查，覆盖增量追加、run/clear generation 重置、gap 替换、结构化 severity 不靠关键词猜测、HTML 转义。脚本和输出位于原证据目录 `d0-web-check.py` / `d0-web-check.cjs` / `d0-debug-page.js`。
- 并发审查补充：超时与 future 完成同时发生时主动释放未交付导出；内存 fallback 编码/哈希和文件关闭均放在 producer lock 外。新增旧 producer 跨 clear 丢弃、超时后文件释放两项用例；8项日志核心测试及双端 Java 编译通过，45秒，见 `d0-gradle-final.log`。合计37项不同用例。公开开关/恢复/清空再按同一控制锁串行化，防止并发 HTTP 启停造成 facade 与 writer 开关不一致；producer 不取该锁，最后 `:catvod:compileDebugJavaWithJavac` 通过。
- 实际导出 fixture：`d0-fixtures/d0-export-fixture-1.txt`、`-2.txt`、`-3.txt` 由生产 `DiagnosticLogBuffer/RollingDiagnosticFile` 写入并导出，输入是人工构造的控制器信号和敏感字段，**不是设备实播日志**。三轮每轮1204条采集/落盘一致、零丢弃/写失败，样例内 URL/Cookie 值已移除、物理音画为 not-observable。生产文件校验测试验证前置内容 SHA-256，fixture 也保留 manifest。
- 主机开销：同一 JDK21/机器、每轮200条预热后1000条标准文本采集，三轮均值0.148/0.118/0.095 ms，P95 0.277/0.202/0.157 ms，最大2.610/0.421/0.330 ms；内存窗口约347728字节、pinned约2322字节、队列高水位2628/1566/3927字节。原始输出 `d0-host-smoke.log` 与 `D0ExportSmoke.java`；这只验证主机 producer/预算，不证明设备 CPU、起播、seek 或播放性能达标。
- 时长偏差：原目标13:29，导出并发收尾、缓存权限等待及证据生成超出估计；13:40后停止扩展，只完成公开开关时序、最小编译、文档校验与原子收尾。
- 本地提交/恢复：guard `AV-DIAG-01-D0` 将本节、代码和测试一并原子提交并立即创建 `recovery/AV-DIAG-01-D0/<timestamp>-<commit>`；精确 commit/tag 使用 guard 回执和 Git 的 `Task-Guard: AV-DIAG-01-D0` 提交记录恢复，未授权推送。回滚只撤回该 D0 提交，基线 `2ec5afd8cc3f21bf1693b198f87018488775c660`。
- 设备状态：本轮 `adb devices -l` 无已连接设备。未安装 APK、未进行手机/TV 实播或设备性能 A/B，T25 用户端闭环及产品 CPU/起播/seek 阈值保持未验收；不得用主机测试代替。

### 14.5 2026-09-15 D1–D4 实施授权与边界

- 用户授权：按 `/private/tmp/webhtv-avdiag-review-20260914/docs/AV-DIAG-01-playback-diagnostics.md` 实施；复用第17节研究，不合入上游提交、不升级或重建native。
- 基线：`feature/mpv-dv7-fel` / `684f6066393fa503b2a0573c25a2aa25d01294fc`，恢复tag `recovery/MPV-REBUFFER-PANEL/20260915110914-684f6066393f`。预存 `app/.cxx/` 70文件全部保护。
- 单元一 `AV-DIAG-01-EXO`：D1/D2共用每播放器来源上下文；装饰现有renderer factory/codec adapter/audio provider，增加Analytics、公共Surface及系统音频观察。不改变候选排序、fallback、ASS/DV、处理链或启动状态机。后续D3 MPV、D4 IJK/恢复分别原子提交。
- 单元一范围：`app/src/main/java/com/fongmi/android/tv/player/`、公共 `PlaybackActivity.java`、`DiagnosticEvent.java`、本文件及索引。后续单元按实际文件另开guard，不含二进制、锁或预存脏路径。
- 最终接口证据：已读取本项目 `media3-exoplayer/1.11.0-alpha01-fongmi` AAR classes.jar；javap确认四类forwarding接口及 `AudioTrackAudioOutput.getAudioTrack()` 可用。`createAdapter`合并内部创建/配置/启动，公开边界只报告整体尝试，子操作不可伪造。
- 关联：Analytics按EventTime分别匿名映射事件/当前媒体；adapter和sink保存来源上下文，无法确认复用媒体时显式记录边界。HTTP只读缓存，不反查native或等待UI。
- 验证：遵循用户自己实测偏好，只核对接口及受影响Mobile/Leanback arm64 Java编译；不运行自动化测试或设备用例。真实导出、播放、性能A/B仍待用户验收，不声称模拟器故障根因闭环。
- 时间：北京时间2026-09-15 11:34（Asia/Shanghai）起，Exo20–30分钟、MPV10–15分钟、IJK/恢复10–15分钟、编译收尾5–10分钟，预计12:20–12:45。
- 回滚：各单元guard原子commit/annotated recovery tag；关闭诊断停止详细采集，撤回对应提交恢复接线，不推送。

### 14.6 D1/D2 公开接口交付（2026-09-15）

- `PlaybackDiagnosticCollector` / `ExoDiagnosticCollector`：每实例匿名身份和不可变media tag随EventTime走，晚到回调不借用全局可变trace；独立音视频证据层、错误cause/suppressed有界分片，清空/重新开启后重置窗口。
- V01–V10公开部分：选择器原结果/硬件声明/profile-level及runtime profile过滤原判定；原factory装饰器记录create/configure/start整体尝试和错误、MediaFormat白名单、CSD长度、输入/output/release/flush聚合、原有frame listener计数；Analytics逐轨、复用、first-frame、错误、DRM、load范围/字节/有限响应头。未改变排序、黑名单计数、fallback、ASS或杜比路径。
- S01–S07公开部分：共享PlaybackActivity绑定SurfaceHolder附加观察器；fixed/layout请求与actual callback分别记录，view/祖先alpha、shutter/artwork、选中视频、display请求/当前值及effects请求有明确来源。TextureView不替换播放器listener；其native Surface身份不可见时标not-observable。
- A01–A13公开部分：复用原DefaultAudioSink及直通provider，实际AudioOutput（包括vendor-direct）被装饰；分别统计sink接收和output payload接收、短/零写及原始写异常。实际AudioTrack head uint32扩展/epoch、合法timestamp有效性（不快于10秒）、underrun、actual/preferred/discovered route、系统/播放器/AudioOutput音量分别记录；所有播放/写入/flush/stop调用仍只转发原行为。A15当前用clock/seekEpoch/speed及原播放器同步指标，不推算不可见的物理延迟。
- 明确缺项：factory没有内部子操作hook，不能区分其内部create/configure/start；没有抢占普通codec frame listener；实际processor内部数据、完整焦点请求结果、物理呈现/声压、CSD内容、native metrics/低层阶段均不伪造，深度字段属于D5或明确not-collected边界。没有像素/PCM探针、额外解码器探测或native重建。
- 验证：首次接线双端Java编译42秒通过；修正窗口/Surface关联后最终双端Java编译36秒通过，记录 `/private/tmp/avdiag-exo-final-compile.log`。字段白名单唯一性、collector字面字段匹配及diff格式检查通过；没有运行自动化测试、设备场景或性能A/B。
- 时间：11:34–12:05完成本单元代码/编译；继续MPV，保持整体12:45执行目标。guard `AV-DIAG-01-EXO` 原子提交/tag后转下一单元；实际设备/导出验收未完成。
- 提交：`57656c06bdb4e13de3cbaf8f573dc76212e7d718`，tag `recovery/AV-DIAG-01-EXO/20260915120613-57656c06bdb4`，未推送。

### 14.7 D3 MPV 公开接口交付

- 12:06起，预计10–15分钟；guard `AV-DIAG-01-MPV`，基线为上述Exo提交。范围是MPV Java wrapper/现有JNI Java入口、诊断schema/缓存/sink、本文件及索引；不修改JNI源码或native产物。
- 实际JNI已将QUEUE_OVERFLOW转给通用event；日志订阅是 `terminal-default`，因此以可观察的 `msg-level` 组件覆盖取得INFO初始化消息。保留原播放恢复逻辑；诊断日志不触发额外fallback。
- 缓存健康与最后错误由后台心跳和现有事件维护，HTTP直接读sink内已发布副本；无native反查/UI等待。属性事实与acceptedWrite请求缓存分开，保留generation/status/age及注册结果。
- 已实现：所有订阅native消息在旧业务过滤之前持续进入统一结构化sink，移除播放器路径对32条及FEL测量条数窗口的使用；错误/边界关键优先，普通/周期事件按容量处理。sink新增最多16个不可变collector健康缓存，导出读取缓存；队列、native overflow、Java无条数丢弃、过期/坏NODE、源端不可观测过滤计数分别报告。
- 已实现：INFO组件覆盖只提高不足的级别，保留已有verbose；观察实际msg-level，区分App请求/原生读回。新增INFO不流入原有恢复分类器。关闭诊断恢复保存的日志配置；初始mpv.conf回调及后续用户修改分别作为恢复基线，不把自己的设置回执当成用户配置。
- 已实现：新增版本、hwdec实际/请求、视频/音频格式、输出/音量/同步、逐轨观测和command返回/reply；属性值只用observer缓存，不使用acceptedWrite冒充实际值，跨文件保留过程级选项并清理文件数据。静态字段只输出变化，周期时钟最多5秒一次。
- M08/M09：保留查名失败原severity/prefix/原文和独立事件身份，查名结果与实际create/configure/start分开；缺选择器内部原因明确native-hook-required。有video轨、vid=no及audio选中并伴视频init错误时报告videoPartialFailure，保留音频是否实际发声的未知边界。
- 验证：MPV接线及日志级别恢复/跨文件缓存补齐后的Mobile/Leanback arm64 Java编译通过，日志 `/private/tmp/avdiag-mpv-final-compile.log`；schema、live路径不再引用nativeLogWindow及diff检查通过。未运行用户保留自行执行的native入口洪泛/静默尾部fixture、设备导出或性能A/B；不能把编译称作T03/T05/T21已验收。
- 用时超过10–15分钟估计：原恢复策略输入隔离与原生回调代际/关闭恢复需补齐；后续仅推进D4必要实现，不增加native研究或测试范围。
- 提交：`88780d4dc8a197fc9bb5e767f450e1847e50a35d`，tag `recovery/AV-DIAG-01-MPV/20260915123353-88780d4dc8a1`，未推送。

### 14.8 D4 IJK 与恢复公开接口交付

- 12:34起，预计15–20分钟；guard `AV-DIAG-01-RECOVERY`，基线为上述D3提交。范围：IjkSimplePlayer/IjkPlayerEngine、PreviousProcessExitLogger/App、诊断持久化及本文件/索引。
- IJK只接入现有原始info/error和状态刷新，原生AudioTrack/OpenSL内部状态保持不可观察；实际stream选择与旧UI默认首轨表示分开。
- journal由原日志writer写入App私有持久目录，大小有界，普通进展限频；保存已记录的run/trace/attempt、终态/错误/输出进展。next boot读取旧journal并随TXT导出，保留旧run身份；关闭/清空同时删除。
- Java崩溃仅排队有界摘要并短时best-effort flush，随后交给原handler；系统退出信息在后台读取自身公开API，trace缺失明确未知，不更改已有恢复/重启策略。
- 已实现：IJK原始info/error、实际stream选轨、视频/音频render-start、解码名称/类型、输出FPS（明确计量口径）、缓存字节、系统音量/可发现设备及生命周期。普通快照复用现有状态刷新，每5秒最多一轮；OpenSL/AudioTrack实际write/head/route缺项保持native-hook-required。
- 已实现：`JournaledDiagnosticFile`包装原RollingDiagnosticFile；原writer在私有files目录维护最多16个实例、64KiB journal及临时替换文件，普通进展最多每秒一次、开始/结束/崩溃优先。cache清理后仍可从journal恢复旧run/trace/attempt及unfinished状态，旧记录经字段脱敏并进入TXT；磁盘预算包含journal。
- 已实现：DebugLogStore与原Java uncaught handler串联，最多150ms best-effort排队刷写后必定交还原handler；从后台线程读取自身ApplicationExitInfo摘要及最多8KiB ANR文本前缀，缺少trace/API明确unavailable/not-supported。native平台二进制trace仅报告可用性及字节边界，不伪造Java堆栈。
- 验证：Mobile/Leanback arm64最终Java编译32秒通过（`/private/tmp/avdiag-recovery-compile.log`）；schema静态检查、diff格式和上游checkpoint检查均通过（0错误/0警告）。未进行设备、崩溃注入、真实用户端导出或性能A/B；这些验收仍归用户执行。
- 提交：`ac02ce84ae14332fa8bf2aab840ee4ab5a7fac2b`，tag `recovery/AV-DIAG-01-RECOVERY/20260915125623-ac02ce84ae14`，未推送。

### 14.9 D3 原生回调尝试归属收尾

- guard `AV-DIAG-01-MPV-OWNER`，基线为上述D4提交；仅修改MpvDiagnosticCollector/MpvPlayer及本文件/索引，保护原`app/.cxx/`70文件。复用R07/R13及既定不可变Context设计，不改变播放控制、恢复策略或native。
- 证据：openCurrent会先创建新controller attempt，旧end-file在stop timeout后仍可能到达。原collector取当前Context并关闭它，会把旧文件终态写入新尝试。
- 修正：在loadfile调用前保存请求Context，START_FILE在native事件线程固定该次owner；后续原生日志、事件、属性/轨道快照、局部失败与health使用该owner和property generation。新controller请求不重绑旧回调；只有native owner仍等于当前attempt时，原生end-file才关闭当前记录。controller stop/release另外记录请求来源。
- 无法归属时明确未知：START_FILE没有公开playlist entry ID，重叠的不同attempt、无App加载请求的START_FILE和queue overflow不猜媒体；START_FILE之前的初始化日志也不使用新controller媒体冒充来源。原生日志本身仍标`log-media-unconfirmed`，async reply只凭operationId关联请求，不借用当前媒体。诊断锁内不调用MPV、文件IO或等待UI。
- 验证：本次修改后Mobile/Leanback arm64 Java编译一次通过，24秒（`/private/tmp/avdiag-mpv-owner-compile.log`）；collector字面字段/事件schema静态校验通过（`/private/tmp/avdiag-mpv-owner-schema.log`）。diff格式与文档checkpoint在本单元收尾验证，不追加自动化测试、设备操作或性能测试。
- 回滚：撤回本guard对应提交即可恢复上述D4状态；精确提交/tag由guard回执及`Task-Guard: AV-DIAG-01-MPV-OWNER`提交记录恢复。

### 14.10 全量补齐（2026-09-15）

- 授权：用户明确要求“全部实施完毕，而且要快”。包含前次遗漏的采集字段、症状/对照操作、报告/结构化筛选、受保护的限时深度探针和必要native hook；不是再次等待授权的评审。
- 基线：`8d46751d7fd0f61bd2cb2557bb8fe99a0d33d494`，`feature/mpv-dv7-fel`；保护`app/.cxx/`70文件。App单元guard `AV-DIAG-01-COMPLETE-APP`，底层补丁/对应产物后续独立原子单元。
- 研究与选择：复用R01–R19已读源码/平台资料；不变方案不能满足用户需求，直接替换播放器/focus owner会改变行为，因此继续采用原owner内只读hook。PixelCopy严格沿R06只做3次低分辨率统计；PCM沿R05/R12只读duplicate buffer，保护/编码流禁用，不保存媒体内容。新远程操作按R11使用本机显示的短期配对码、header token、POST、Origin和限频；保留旧TXT读取。
- App范围：现有player/诊断、DebugLogs、DebugLogDialog、公共PlaybackActivity、Setting/App、strings、catvod日志及直接相关定向测试；唯一文档及索引。不得修改播放器选择、fallback、音量、处理顺序或输出参数以获得诊断。
- 待补齐清单：C03/C06/C09/C10/C15上下文与资源；A05/A10实际处理链和焦点；V02/A03操作分步；S08/A14限时统计；M08/M12及native库身份；故障标记/冻结/对照、TXT可读报告与ZIP、trace/attempt/音视频/优先级筛选、远程操作访问控制；逐项覆盖表与最小验证/产物。
- 验收：探针关闭时不扫描像素/PCM、不提升native日志；会话/代际/保护条件正确；新增远程写操作不匿名、不接受跨站请求；导出只读缓存/磁盘快照，不回调播放线程。使用定向编译/安全与预算契约校验、受影响ABI和APK打包校验；实际设备/物理输出结果只按真实证据记录。
- 回滚：各单元独立commit/tag；默认标准诊断，深度需产品内用户主动启动，最长120秒自动恢复；必要底层补丁与其二进制同单元撤回。不升级依赖，不推送。
- 时间目标：13:26 Asia/Shanghai起55–75分钟，14:20–14:40；既有缓存、窄hook/增量重编、验证输出只保存一次。任何真实不可控阻塞须具体报告，不用追加研究消耗时间。
- 用户追加（13:50–13:55）：移除MPV性能页重复的“详细日志”；全局调试弹窗按播放流程/网络请求/视频画面/音频输出/字幕脚本/内核设备提供子开关，缺省全部开启、持久保存用户选择。关闭分类在采集入口生效；控制和完整性事件保留，并记录选择，避免将用户关闭误判为无错误。限时像素/PCM探针服从视频/音频分类及原主动确认、120秒上限。

### 14.11 App操作、报告、受控采样交付

- 已接入：默认全开的六类采集开关；本机/TV故障标记；短期配对和有界远程操作；单变量对照记录；同writer持有的前30秒/后15秒incident，标记立即落checkpoint、静默到期收尾、进程恢复保留partial；TXT尾部可读报告及流式ZIP/逐文件SHA256，单并发60秒导出预算；trace/attempt/音视频/priority网页筛选。
- 深度：默认60秒、上限120秒、绑定具体player/trace/generation/attempt；最多3次64×36像素统计，每个PCM检查点最多3×4096 frame；不保存原图/PCM。切播放/关闭/清空/到期停止，MPV在原owner恢复日志等级并保留用户更高verbosity。受保护内容和编码直通不采样。普通日志记录完整的所选分类，旧性能verbose值已从构建MPV配置路径解除，菜单清理随下一单元。
- 补齐公共上下文：真实native/App focus请求返回、callback/abandon；复用既有memory/system monitor记录资源，不增加proc/PSS轮询；输入route所有者及可见边界、Exo容器timeline、controller配置old/requested与重建attempt。具体底层create/configure/start、active processors及原始AudioTrack write仍在下一单元。
- 验证：双端arm64 Java编译92秒通过；catvod诊断23项用例20秒通过（含4项新的配对失效/限频/Origin、ZIP校验/不可变快照、TXT既有manifest与伪造事件、incident即时保存/静默完成/崩溃恢复）。嵌入网页JS语法通过；新增事件字段白名单通过；`git diff --check`通过。编译首次仅遇Gradle缓存沙箱权限，授权后使用原缓存完成。
- 证据：`/private/tmp/avdiag-complete-app-compile.log`、`/private/tmp/avdiag-complete-contracts.log`，catvod测试XML；不等同于设备实播或性能A/B。已有Room查询警告未改动。
- 下一单元：基于当前已补丁AAR重编窄hook类并保持其他字节；MPV/FFmpeg只读事件补丁、同锁双ABI产物；完成C03库身份、设置菜单清理和APK。

### 14.12 底层观察点与产物

- 基线：`180811f16073271ba1cb6a4f2f889008966facd3`，恢复tag `recovery/AV-DIAG-01-COMPLETE-APP/20260915141054-180811f16073`。guard `AV-DIAG-01-COMPLETE-NATIVE`；14:12 Asia/Shanghai；继续保护`app/.cxx/`70文件。
- 精确源码：读取当前已提交common/exoplayer sources.jar的AudioFocusManager、AudioProcessingPipeline、AudioTrackAudioOutput、同步/异步codec adapter、ExoPlayerImpl/Internal及extractor adapter。与R12相同API所有者，focus已在common并由ExoPlayerImplInternal管理；不新增焦点请求者。codec只包装原API调用并原样抛回异常；原始AudioTrack写统计与payload/header单位分开，处理链在configure/flush时采集。
- 采用方案：common中的可关闭只读observer，App负责固定owner/attempt关联、有界聚合/脱敏；当前AAR选择性重编类，不改变其他class字节，保留ASS和现有所有扩展。对比不改无法获必要内部字段，替换整个sink/focus owner会改变行为，故拒绝。FFmpeg/MPV在当前已打补丁缓存上追加独立补丁；保留原查名排序、fallback、AO/VO参数及所有本地DV/字幕补丁。
- 范围：guard内App设置/诊断接线、common/exoplayer两个AAR/source/metadata、构建脚本和独立补丁、MPV两ARM ABI的受影响产物、唯一任务文档/索引。构建缓存为临时编译输入，不提交、不reset其已有修改。
- 验收/回滚：新增hook关闭时不做字符串/媒体扫描；错误码/生命周期不被改写；两ABI同输入、ELF依赖不变；App双端打包；这一提交连同补丁/二进制可整体回滚到本节基线。继续复用R01–R19，不扩展研究。
- 已完成源码和接线：Media3 `PlaybackDiagnostics`/`Media3DiagnosticBridge`记录原owner内的焦点请求、duck倍率、实际active处理链、codec分步和AudioTrack原始返回；`ForwardingExtractor.init`把真实output绑定到创建MediaItem时的不可变Context，保留底层implementation和全部既有ASS/DV extractor行为。关闭音频分类后跳过新增逐buffer统计。
- Media3产物：`scripts/build_media3_diagnostics.py`从上述完整已提交基线选择性重编common/exoplayer，两模块未修改的class字节逐一保持；新补丁、AAR、sources、module/checksum和media-lock一起更新。编译临时副本仅去除AAR未携带的package-private SOURCE-retention注解，发布sources保留。构建输出见`/private/tmp/avdiag-media3-build.log`，已通过，不重复重编。
- MPV/FFmpeg：保留同一source lock及所有本地补丁；查名实际访问/拒绝原因/profile比较、查询失败具体stage、Java提前返回/NDK by-MIME和create/configure/start结果均进入原av_log桥。App兼容AVClass文本前缀，新增hook不进入旧恢复分类器。AudioTrack记录真实写入返回及bytes/int16/float单位、配置/路由/已有head和timestamp缓存；outputId使用进程内递增编号，避免重建后复用。两ABI FFmpeg、MPV首轮成功；最后outputId/JNI异常清理修改只需增量重链MPV。
- C03：后台只读自身可执行映射，支持APK内未提取SO；解析实际ELF ABI/GNU build-id/SHA256、对照MPV asset或APK条目，不把“包里存在”当成已加载。不创建native context。构建注入Git完整revision＋dirty/clean状态、MPV/FFmpeg/libplacebo/framework源锁，避免提交前包伪装成提交后精确版本。
- 观察告警：复用既有5秒样本，连续3次且至少15秒无进展才生成只读`diag.observation`；暂停/缓冲/seek/suppression、无选中motion video、未知或低帧率、无有效输出指标均门控。AudioTrack只观察PLAYING且有已接受payload的playhead；不据此断言物理无声、不触发恢复。MPV新增file-format/seekable/current-edition缓存；A/V clock口径明确。
- 用户设置：六类默认全开和持久选择沿用14.11；已删除MPV“详细日志”的菜单、value/action、getter/setter和preset残留。正常启用即收集所选分类完整的标准诊断；像素/PCM主动限时捕获仍独立。
- 最后覆盖核对：非保护、当前owner的CSD按index/length/bytes计算SHA-256，总读取上限64 KiB，只读duplicate；保护内容、超预算、旧owner或读取失败保留明确状态。adapter输出PTS与输入PTS分别记录，flush/capture重置；新增统计服从对应分类。

### 14.13 已实现覆盖索引

同一事件内的具体字段仍以其`status/source/association`为准；此表表示采集能力已接入，不表示每台设备、每个后端都能返回所有字段，更不表示物理输出已验证。

| ID | 实现落点及证据 |
| --- | --- |
| C01 | `PlaybackDiagnosticSession/Collector`：开关、迟开、版本、身份、分类选择及可用边界 |
| C02 | `Setting.logDebugEnvironment`：构建Git/revision状态、设备/API/ABI、fingerprint摘要；资源补充见C15 |
| C03 | `NativeLibraryDiagnostics`：实际可执行映射、ELF ABI/build-id/SHA256、APK/asset对照、加载失败；未加载库不伪装成功 |
| C04 | `SurfaceDiagnosticCollector`及既有MPV输出日志：display/mode/HDR与已有后端信息；不创建额外GPU context |
| C05 | `PlaybackDiagnosticSession`及MPV配置入口：当前播放配置及来源 |
| C06 | `PlayerManager`配置变化与`MpvDiagnosticCollector.option`：请求、old/new、原始返回和重建attempt |
| C07 | `PlaybackTrace/Session`：请求/解析结果、资源分类、匿名媒体身份及输入路由 |
| C08 | Exo load事件、既有网络日志与MPV observer：Range/响应头摘要、字节/耗时及缓存；不可见上游不补造数据 |
| C09 | `PlaybackDiagnosticSession`：direct/App/外部loopback owner及可见网络边界 |
| C10 | Media3 extractor owner hook/timeline及MPV `file-format/seekable/current-edition`：实际容器实现与媒体身份 |
| C11 | Exo tracks、MPV track-list、IJK选轨快照：available/support/selected与格式 |
| C12 | Exo DRM analytics与媒体保护标记：会话/keys/error阶段；不采license正文或key |
| C13 | 公共PlaybackActivity/PlayerManager与各内核collector：生命周期、暂停、抑制和来源 |
| C14 | Exo player/adapter及MPV observer：播放时钟、独立decoder输出PTS、seek epoch和speed |
| C15 | 复用既有memory/system/recovery monitor：资源、trim/温控/省电与线程延迟；不增加proc/PSS轮询 |
| C16 | `PlaybackDiagnosticCollector.end`：音视频分别记录最后证据层及错误；保留物理输出未知 |
| C17 | `DiagnosticLogBuffer/RollingDiagnosticFile`及collector health：队列、seq、轮转、flush、丢失、写失败、迟到与未结束 |
| V01 | `ExoDiagnosticCodecAdapter.selector`：本次selector返回的候选顺序与能力/策略来源；不把独立枚举当实际尝试 |
| V02 | Media3同步/异步codec原API hook：create/configure/start分步及原异常，绑定factory operation owner |
| V03 | `ExoDiagnosticCodecAdapter.mediaFormat`：配置白名单、surface关联与受保护/有界CSD摘要 |
| V04 | adapter output-format及analytics input/reuse：输入输出格式分层、复用结果 |
| V05 | adapter queue聚合：bytes/input/加密/EOS/PTS范围与回退；不复制sample |
| V06 | adapter dequeue/release及DecoderCounters：解码输出、提交、丢弃、drop各自口径 |
| V07 | first-input、first-output、release及Media3 first-frame回调分别标记 |
| V08 | 包装原有Media3 frame-render listener；没有该listener时明确未采集，不抢占tunneling回调 |
| V09 | adapter flush/release/reuse及epoch；生命周期错误保留原异常 |
| V10 | 独立`video.error`关联`diag.error`的有界cause/stack链，不累加播放策略故障次数 |
| S01 | 公共SurfaceHolder附加观察器：create/change/destroy与尺寸/有效性 |
| S02 | 公共UI与codec adapter绑定/解绑、old/new surface；native侧保留原owner输出日志 |
| S03 | fixed/layout请求、operation与之后actual holder callback分别记录 |
| S04 | view/祖先可见性、alpha、窗口/附着状态；TextureView不替换播放器listener |
| S05 | `SurfaceDiagnosticCollector`：video/artwork/shutter等来源与selectedVideo状态 |
| S06 | Exo effects请求及MPV原后端/滤镜日志；requested不冒充applied或色彩正确 |
| S07 | display/frame-rate请求与当前实际显示mode分别记录 |
| S08 | `PixelDiagnosticProbe`：主动限时、最多3次64×36统计、PixelCopy原始返回和保护门控 |
| A01 | Exo输入/轨道Format、MPV current-track、IJK选轨：原始/当前音频路径 |
| A02 | codec selector与实际AudioOutputProvider支持查询：解码与输出能力分开 |
| A03 | Media3/FFmpeg codec原操作：create/configure/start及原错误；音频不套用视频成功状态 |
| A04 | adapter与AudioOutput输入：编码/PCM格式和独立decoder输出PTS；不把AudioOutput接受量当decoder帧数 |
| A05 | `AudioProcessingPipeline` configure/flush owner hook：active处理器顺序、输入输出格式；MPV AF配置由observer取得 |
| A06 | 实际provider返回对象与MPV AO配置：outputId、encoding/mask/buffer、offload/tunnel及来源 |
| A07 | AudioOutput payload统计与AudioTrack原始API返回分层；MPV保留bytes/int16/float单位、短写/0/错误与耗时 |
| A08 | Exo真实AudioTrack head/timestamp/epoch；MPV复用AO现有head/timestamp缓存，validity不伪造 |
| A09 | `SystemAudioDiagnosticCollector`与实际AudioTrack路由：discovered/preferred/actual角色分开，MPV在原AO内低频取得actual |
| A10 | Media3原AudioFocusManager与App/native原focus owner：请求返回、callback、duck gain及player command，不新增请求者 |
| A11 | player、AudioOutput、MPV与系统音量/静音分别采集；系统不可见mute/外部AVR音量保留未知 |
| A12 | 实际AudioOutput生命周期及MPV recreate；新AudioTrack使用新的进程内outputId |
| A13 | `audio.error/diag.error`：初始化、write、codec异常与raw错误码分离，保留当时owner |
| A14 | `PcmDiagnosticProbe`：主动限时、每检查点最多3×4096 frames、逐声道数值，保留原buffer position；保护/编码流禁用 |
| A15 | `audio.sync`：Exo独立decoder输出PTS/player clock/epoch，MPV实际avsync/delay；不把decoder PTS差当物理同步误差 |
| M01 | 实际加载映射/库摘要、源锁构建注入与mpv/FFmpeg版本observer |
| M02 | 白名单option原始set返回、requested与readback；分类/限时日志覆盖到期恢复，保留用户更高verbosity |
| M03 | 原native事件入口、source seq、start-file owner与end reason/error |
| M04 | 有界track-list NODE解析，available/selected/default/albumart及实际轨选择 |
| M05 | hwdec requested/current、interop、codec/VO/GPU与分层video参数缓存 |
| M06 | decoder/输出音频参数、AO、volume/mute、AF和SPDIF配置；native实际配置补充A06–A08 |
| M07 | position/PTS/cache/pause/idle及各类drop独立字段，不混为重缓冲计数或物理FPS |
| M08 | FFmpeg实际选择器访问/拒绝/profile比较、查询失败stage及真实create/configure/start；识别AVClass日志前缀 |
| M09 | 有视频但初始化失败后vid=no且audio仍选中，报告videoPartialFailure及原错误；audio selected不冒充有声 |
| M10 | commandId、提交/返回/完成、耗时与generation；无法确认媒体归属时明确unresolved |
| M11 | observer注册、NONE/unavailable、age、NODE/late/native overflow与源端过滤边界；静默时可从缓存导出 |
| M12 | 原native VO/interop失败进入结构化sink；新增AudioTrack owner hook，非AudioTrack后端不伪造write/head |

- D4 IJK最小覆盖已接入原info/error/decoder/track/FPS/cache及公共focus/route/volume；OpenSL ES内部write/head按9.4明确不可见，不建立新的JNI/音频owner。
- 5/10/11/12节的产品闭环已接入：症状标记、前30秒/后15秒incident、崩溃恢复partial、有界短期配对/POST/Origin/限频、对照步骤、TXT可读报告、流式ZIP/校验和、网页结构筛选、队列及轮转损失状态。真实症状判读仍需用户提供同次复现导出，不能用fixture声称实机闭环。
- 无输出观察默认采用三个5秒采样、至少15秒，复用现有定时器；文中5秒/3秒为候选阈值，并非已确认的设备故障期限。合法暂停、缓冲、seek、抑制、封面/音频only、未知cadence均门控，观察不触发任何恢复动作。

### 14.14 软件验证、产物与验收边界

- App前一原子单元：`180811f16073271ba1cb6a4f2f889008966facd3`；tag `recovery/AV-DIAG-01-COMPLETE-APP/20260915141054-180811f16073`。14.11的23项契约验证不重复运行。
- Media3选择性重编common/exoplayer成功，保留全部无关class字节；`/private/tmp/avdiag-media3-build.log`保存完整输入hash和结果。APK包含新`PlaybackDiagnostics/Media3DiagnosticBridge`定义，确保新AAR可达。
- 两ABI FFmpeg及MPV构建成功，最后outputId修改仅增量重链MPV；`scripts/verify_mpv_native_assets.sh --require-elf`通过，日志`/private/tmp/avdiag-final-elf-check.log`。保留同锁、命名空间、DT_NEEDED及JNI contract；未重编`libplayer.so`。
- 双端arm64 Debug首轮构建6分22秒通过，显式`-PexoAssPrototype=true`保留已经验收的ASS字体能力，使用既有CMake隔离init-script保护`app/.cxx/`。随后仅因补齐CSD/输出PTS这个相关Java改动增量打包，最终双端构建28秒通过；未构建全ABI/App矩阵。
- 包内核验：两APK的10个MPV asset逐个与最终工作树SHA256一致；libass存在且构建flag开启；五个新增诊断类的DEX **class definition**存在，不仅搜索类名引用。原生库身份生产解析器以两ABI的真实ELF对照llvm-readelf，验证GNU build-id存在/缺失、ABI、SHA256；STORED APK fixture验证真实映射条目和未映射排除。当前libmpv没有GNU build-id，使用实际SHA256并保留build-id缺失；首次验证脚本错误地假设必有build-id，已修正参考预期并增加有build-id的NDK C++库对照，未修改生产解析器或重跑APK断言。
- 最后Java增量之后，仅针对新APK重新核对包内六个诊断class definitions、更新的PTS代码、libass和10个MPV asset，均通过；不重复原生ELF、解析器或23项契约检查。验证文件：`/private/tmp/avdiag-final-apk-build.log`、`/private/tmp/avdiag-final-apk-content.log`、`/private/tmp/avdiag-final-artifacts.log`、`/private/tmp/avdiag-final-parser-check.log`、`/private/tmp/avdiag-final-artifacts.json`；不包含伪造的设备实播结果。
- Media3 AAR SHA256：common `81339bd78814f83987c2d3dd07dfbfd16b32ae34aabd23616abf4938a77bc696`；exoplayer `47f90bd4fc52567738847ceb59c435ddeb6d35b987866d6899de87de3170bdf6`。source/metadata/checksum与media-lock同单元交付。
- 原生关键SHA256：arm64 libmpv `8213150b467bc2dd9501bbd8e484ac8db04f537ebc8276133a845c18fda1b01a`、libmvcodec `3720c16422139d874f1954e6851330530b6767056f527062c37d4d197cb44c1d`；armv7 libmpv `2a0e1f749b8d57377da086a5c150db588b90cf65b656eddce36e6a3ba194709d`、libmvcodec `fd283848a22160964511e819992fe76aca4e37cbb4ac69e3817a63951a805e25`。
- 仍然明确的验收边界：用户自行进行设备实播、TV遥控器操作、T01–T25和性能A/B；本轮未用ADB安装/操作。编译与ELF检查不能替代这些结果，不宣称“零开销”或物理音画正常。常规缺回调/未知后端、旧owner、保护内容、日志关闭/迟开等状态持续保留在导出中。
- 原子回滚：当前guard `AV-DIAG-01-COMPLETE-NATIVE`将App适配、三份窄补丁、两个AAR及两ABI原生产物一起提交并生成唯一`recovery/AV-DIAG-01-COMPLETE-NATIVE/…`本地annotated tag。不push；撤销该单元可恢复到14.12基线且保留14.11分类/导出等App功能。

| 最终安装包（arm64 Debug） | 字节数 | SHA256 |
| --- | ---: | --- |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | 188388733 | `d35d0a46d648b66f716cb76eea378cfadf3f6c0bdf916553d1b3d186b188d88d` |
| `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk` | 176524458 | `e19d8fb0f4feefe45ca9361cace6da333507b50ca0d82cc01eea2c9cb05d0885` |

安装包标记构建时的完整基线Git revision及`dirty`状态，产物hash如上；不为写入提交后hash再做一次整包构建。当前代码提交/恢复tag可通过guard回执与本单元提交记录唯一定位。

### 14.15 PR #107 爬虫日志与 Web 日志页（2026-09-15）

用户已授权参考 [PR #107](https://github.com/fish2018/webhtv/pull/107) 实现 QuickJS/Python 爬虫日志，不直接采用 PR 代码；同时修复手机/电脑日志页顶部空间、滚动后操作不可达和控件重叠。这是本任务的追加单元，不重开已完成的播放器/native 阶段。

基线：`feature/mpv-dv7-fel` / `5acbb05afff34235d66bc1a6f3d7f67427e2a239`；guard `AV-DIAG-01-CRAWLER-WEB`，保护预存 `app/.cxx/` 70 文件。16:42 CST 声明预计 25 分钟（研究 5、实现 13、验证/构建/收尾 7），目标 17:07 CST。范围只含日志桥接、Web 页、相关验证和本文/索引；不改爬虫执行方式、播放器、依赖、签名或 CNB 发布。

#### 决策证据（访问日 2026-09-15）

| 来源 / 等级 | revision / 实际读取 | 结论、适用性与决策影响 |
| --- | --- | --- |
| PR #107 / A（代码） | base `784b90420d646eb6c7ddcc63ad622a92c65b02b4`，head `3e383c6ed5537d008024f223bc670794aeb4dcf8`；API正文、diff/patch及相关方法；API无评论/代码评审评论 | PR把QuickJS四回调和Python self.log转给SpiderDebug.log、Web按tag归Console。当前facade本身写Logcat，照搬会重复输出/丢失级别；普通print不经过self.log。 |
| 本地调用链 / A | 上述基线的 `quickjs/.../Spider.createCtx` → `Console`；`chaquo/Loader` → `app.spider`/`SourceFileLoader` → `base.Spider.log`；`SpiderDebug` → `DebugLogStore` → `DiagnosticLogBuffer`；`DebugLogs` | Console在evaluate前已注册；Python app导入前适合一次性安装桥接。复用现有异步sink、脱敏、generation和分类，不另建writer。 |
| Chaquopy官方文档/源码 / A | [sys/Android](https://chaquo.com/chaquopy/doc/current/android.html#sys)，当日正文；[16.1.0 stream.py](https://github.com/chaquo/chaquopy/blob/16.1.0/product/runtime/src/main/python/java/android/stream.py)、同版 `java/android/__init__.py` | stdout/stderr已有行缓冲Logcat owner，stderr为WARN；native fd不在sys流可见范围。用公开TextIO委托保留原输出，不依赖或修改私有类；本地Chaquopy17.0.0不升级。 |
| CPython / A | [v3.10.18 logging.StreamHandler](https://github.com/python/cpython/blob/v3.10.18/Lib/logging/__init__.py)，构造/emit/flush/setStream | 默认写sys.stderr，logging仍拥有formatter/异常；额外root handler会影响basicConfig/重复日志，因此不添加handler、不改logging配置。 |
| pytest成熟实现/测试 / B、A | [8.4.2 TeeCaptureIO](https://github.com/pytest-dev/pytest/blob/8.4.2/src/_pytest/capture.py)、[test_capture.py](https://github.com/pytest-dev/pytest/blob/8.4.2/testing/test_capture.py) 的tee-sys、Unicode、原输出检查 | 借鉴tee保留原流write返回值/异常语义；测试用无限缓冲不适合App，采用每线程有界行缓冲、明确截断、关闭时不保留文本。不复制代码。 |
| MDN技术文档 / B | [position](https://developer.mozilla.org/en-US/docs/Web/CSS/position)，sticky/scrolling ancestor正文 | 现有main的overflow-x:hidden会影响sticky滚动祖先；顶部须正确固定，tabs独立横滚，输入框规则排除checkbox。 |

相关提交处置（仅用户指定日志功能进入候选范围）：

| 完整 commit | 内容 | 处置 |
| --- | --- | --- |
| `cd30288078162c6266fe62303032eb144554336c` | QuickJS Console桥接 | 手工重实现：只新增sink通道、保留级别，避免第二次Logcat输出 |
| `636ff64b2ced0433c67bd1faf689a55393585b96` | Python self.log桥接 | 补充/替代：一次性TextIO tee覆盖self.log、print及标准流logging/traceback |
| `7ac206819f6e60be04f26e8e6312014fa87f88b8` | Console tab包含爬虫 | 手工重实现：来源与显式级别、错误筛选和安全转义 |
| `e452bdbdefb74ff8891057b2ea02ede2f612c8d1`、`83b61ba3ee9d0ad8a8b5fa5f319c7e1e2735c3b2` | XBPQ地址及变量修复 | 不适用此次需求；其余PR签名/站源/构建改动不在授权单元内 |

已检查相关patch的后续同文件变更，无本需求的维护者讨论/revert证据（评论接口为空）。算法论文/编码器benchmark与此次TextIO和布局决策无关，不扩大搜索。

#### 方案、验收与回滚

- 不改动：继续缺少爬虫输出和可达操作区，不能满足需求。原样PR：改动少但重复Logcat、无级别、仅self.log，也未解决布局，因此拒绝直接合并。
- 采用窄适配：SpiderDebug新增仅写调试sink的Console入口，先检查总开关/分类，再保留显式级别；QuickJS保持原Logcat owner。Python在app/爬虫导入前安装一次tee，原流仍负责输出；每线程缓存上限、flush和generation隔离；采集异常不打断爬虫。不添加logging handler，不把stderr等同error，不用当前影片伪造异步爬虫归属。
- 爬虫收集跟随“网络请求”分类（默认全开）；Console tab区分QuickJS/Python。Web/TXT/ZIP仍用已有脱敏/轮转/完整性机制。超长单行保留有界前缀、显式截断，不能任意拆散凭据而绕过脱敏。
- 顶部合并为一个固定操作区，分类单行横滑；“地址说明”位于开关后，诊断操作/结构筛选按需展开；搜索限宽，解释checkbox和暂停独立布局。展开面板在小屏限制高度并自行滚动，保留全部配对/导出/诊断操作。
- Java/Python/HTML变更不改ABI/依赖ownership/播放器；启用日志有新增有界开销，不宣称零开销。Python native fd、主动替换sys.stdout的脚本、安装前已持有原流的第三方handler不冒充已覆盖。
- 决定性验证：Python原流/分段/线程/禁用/generation/洪泛/异常契约；Java编译及分类/错误保留；真实生成网页在手机/桌面宽度的顶部固定、无重叠、tabs横滚、Console/错误/搜索/暂停交互。只构建mobile/leanback arm64 debug，保留 `-PexoAssPrototype=true` 并隔离CMake，设备体验由用户验收。
- 回滚：整体revert本单元commit至上述基线；已有tag `recovery/AV-DIAG-01-COMPLETE-NATIVE/20260915162159-5acbb05afff3`。不push。

#### 17:29 用户 UI/UX 反馈后的布局决策

用户截图中诊断表单、配对和类别换行挤占正文，要求使用折叠/tab/抽屉/弹窗，按UI/UX最佳实践重排。首轮本地浮层的浏览器检查也发现 `popup outside viewport`，不能交付；改用独立于页面高度的视口级面板。原17:07目标因用户反馈与布局调整失效，17:30重新估计15分钟、目标17:45；不重跑已经通过的Python/分类检查。

新增证据均于2026-09-15经代理读取正文/实际源码：

- [Chrome DevTools ConsoleView.ts](https://github.com/ChromeDevTools/devtools-frontend/blob/main/front_end/panels/console/ConsoleView.ts) 当日main快照，B：`console-main-toolbar`仅保留主要操作；`console-show-settings-toolbar=false`和默认隐藏sidebar体现按需揭示设置。采用这种职责分层，不引入其组件依赖。
- [Carbon Data table usage](https://carbondesignsystem.com/components/data-table/usage/)，B：toolbar承载主要按钮/搜索/筛选，展开用于小空间中的大量信息；依操作频率整理，而非把所有控件缩小后平铺。
- [WAI-ARIA APG modal dialog](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/)，A：背景不可交互、焦点进入/圈定面板、Escape/可见关闭按钮、关闭返回触发控件。优先HTML dialog，保留不支持showModal时的焦点/遮罩降级。
- [APG Tabs](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/)，A：一次展示一个panel，选中态、左右/Home/End键和对应标签关系；适用于面板内筛选/诊断/工具三个同级区域。
- [WCAG 2.2 Target size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)，A：最小24×24及间距；本任务手机主操作采用44px目标，不靠缩小点击区域节省空间。

最终结构：默认顶部两行（状态与搜索/暂停/工具入口；单行横滚分类），手机搜索按需展开，桌面搜索限宽；筛选/诊断/工具放入一个带三页签的面板，手机底部sheet、桌面右侧drawer。地址说明按钮紧跟关闭采集，展开在工具页内；解释选项放筛选页，避免与暂停争夺主工具栏宽度。面板独立滚动、关闭后回到原日志位置，分类/关键词/结构筛选生效时显示状态提示，不嵌套弹窗或重排原日志内容。

#### 最终实现和验证（2026-09-15 17:56 CST）

本单元实现完成：QuickJS四级Console和Python标准流接入统一sink；默认全开的网络分类生效，错误保留、原Logcat唯一输出、脱敏/轮转沿用现有实现。Python在app/爬虫导入前一次性安装，真实self.log、普通print及标准流logging/traceback可见。现有 `app/proguard-rules.pro:62` 保留 `com.github.catvod.crawler.**`，Java反射入口不需要新增规则。

Web默认两行固定顶部；搜索在手机按需展开、桌面限宽260px；Console靠前显示。一个三页签工具面板承载结构筛选、解释显示、配对/诊断、下载/采集管理、地址说明；手机底部sheet、桌面右侧drawer。关键词和高级筛选数量可见，提供重置。面板内滚动、点击遮罩/关闭/Escape、Tab焦点循环/恢复、页签方向键及旧浏览器降级均已实现。

| 验证 | 结果 / 证据 |
| --- | --- |
| Python实际方法/流契约 | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s chaquo/src/test/python -v`，7项通过，0.006秒；覆盖真实base.Spider.log、print/writelines/flush、原输出/异常/返回值、分线程、禁用/generation/分类、100k长行、采集异常、一次安装与logging级别；`/private/tmp/pr107-python-tests.log` |
| Java分类契约 | `:catvod:testDebugUnitTest --tests com.github.catvod.crawler.diagnostics.CrawlerLogCategoryTest`，1项通过；QuickJS/Python/Java爬虫统一网络类别且默认全开 |
| 真实Java生成页 + Chrome | `scripts/verify_crawler_web_logs.py` 生成实际HTML；用已安装Chrome运行产出的browser.cjs，320×640、390×844、844×390、1440×900全部通过；手机顶部93px、桌面83px。单行分类、触控区域、正文宽度、头部固定、Console/错误/关键词/暂停、安全转义、面板视口边界、焦点/返回/Escape、页签、配对、结构筛选、无showModal降级通过；`/private/tmp/pr107-web/browser-result.json`、`/private/tmp/pr107-web-check.log` |
| 可视核对 | 已检查 `logs-390.png`、`drawer-390.png`、`drawer-1440.png`，默认正文可见，面板按区域展示；预览目录 `/private/tmp/pr107-web/`。宿主JVM输出编码修正为UTF-8，避免测试夹具把中文变成问号；不是Android产品字体问题 |
| 最终双端APK | 保留 `-PexoAssPrototype=true` 与CMake隔离init-script；最后一次相关焦点修正后增量构建57秒成功，未重跑已通过且未变的Python/分类检查；`/private/tmp/pr107-final-build.log` |
| 包内与治理 | 两APK均包含 `webhtv_logging.pyc`、新drawer的DEX内容、`lib/arm64-v8a/libexo_ass.so`；BuildConfig的Exo ASS仍开启。checkpoint verifier 0错误/0警告；预存app/.cxx由guard保护。最终收尾使用本guard原子提交和唯一annotated tag，不push |

最终产物（未安装设备，用户实测/性能A/B不冒充通过）：

| 变体 | 路径 | SHA-256 |
| --- | --- | --- |
| 手机arm64 debug | `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | `ec6f44be454ea45dede31ca2f9e508015c0cddffcd27f2e551699983f5027e79` |
| 电视arm64 debug | `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk` | `00a23b8285b81e853d56bcecf63d279b57611ba7502abb21471df948e955c3cb` |

前一轮浮层越界已由视口级dialog替代，Chrome在末尾Tab移出焦点的问题已加显式循环修正；最终浏览器检查全部通过。没有进一步更改播放器/native、业务爬虫执行或签名/CNB发布。

### 14.16 无配对日志操作与固定顶部按钮（2026-09-16）

用户插入需求：删除调试日志配对，把下载/清空放顶部，评估“记录单参数对照”。基线`feature/mpv-dv7-fel` / `ed3d710ef551210278920ba4cd25e8dda6e19ad6`，FEL内容复用候选已独立提交/tag；本轮不重编native。guard `AV-DIAG-01-WEB-ACTIONS` / quick-fix，保护104个既有 `app/.cxx/` 文件。06:59 Asia/Shanghai开始，修改/定向验证约10–18分钟、TV64打包及收尾约5–7分钟，目标07:15–07:25。

#### 依据、选择与批准

- 实际调用链：`DebugLogs.doResponse/diagnosticAction`使用Bearer和`DiagnosticControls.ACCESS`；`DiagnosticAccess`生成6位码、15分钟token并同时做5次/秒限频；App `DebugLogDialog`显示配对码，网页将token放sessionStorage。用户要求的是整个调试操作流程取消配对，仅删除页面输入框会留下403，不能作为实现。
- `DiagnosticControls.compare`只发布`diag.comparison`的手填old/new/note，result仍为unknown；`DiagnosticReport`未使用它做对照计算，只提示使用该入口。日常使用价值不足，删除页面/接口/该专用发布方法，并将报告指引改为保存前后日志；正常参数事件、故障标记、限时统计、历史日志解析保留。报告这一处必要文案纳入同一任务scope，无新增行为或模块边界。
- UI复用14.15已读Chrome DevTools toolbar、Carbon、APG dialog/tabs及WCAG触控目标证据：高频动作在固定顶部，手机仍两行，44px点击区域；下载/清空/暂停/工具同排，搜索移到单行横滑分类旁；结构筛选及低频ZIP/采集管理继续在原抽屉，不叠加新的面板体系。

| 新访问证据（2026-09-16） | 事实/等级及本地适用性 |
| --- | --- |
| [Go net/http csrf.go](https://github.com/golang/go/blob/56ebf80e57db9f61981fc0636fc6419dc6f68eda/src/net/http/csrf.go)与[测试](https://github.com/golang/go/blob/56ebf80e57db9f61981fc0636fc6419dc6f68eda/src/net/http/csrf_test.go)，go1.25.1=`56ebf80e57db9f61981fc0636fc6419dc6f68eda` | A；不安全方法校验Origin/Host或Fetch Metadata，GET不能做状态修改；测试覆盖跨站/null/方法及bypass误匹配。借鉴同源防护边界，不引入Go/新依赖；其无Origin默认放行不直接采用 |
| [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)，当日完整正文 | B；强匹配源/目标Origin，目标地址应由服务端确定，缺失来源推荐拒绝；该机制也适用于无认证请求。沿用设备实际地址allowlist，去token后拒绝缺失/null/异常Origin，HTTP页面的fetch POST自动携带Origin；不信任任意Host/转发头 |
| [Go #75054](https://github.com/golang/go/issues/75054)与[#79766](https://github.com/golang/go/pull/79766)实际问题/提议正文 | B/C；bypass匹配/Origin userinfo接受边界需要明确。本地不增加bypass，已有userinfo/path/query/fragment拒绝保留；两项均只读参考，不移植patch，也不把closed状态当作已合并证据 |

原始正文、源码/测试、API元数据保存在`/private/tmp/webhtv-debug-web-actions.ALJuwC/`。这是已有产品流程与HTTP/UI契约调整，无新的渲染/密码学/媒体算法；论文和GPU benchmark不能决定此取舍，不新增此类检索。网页布局继续复用昨日固定快照及实际浏览器验证，不重复泛搜。

比较：不改违背用户需求；只去UI但保留Bearer会使按钮失败；完全删掉来源/方法/资源预算并无必要。采用窄适配：完整删除配对/token链，严格同源POST和原5次/秒限频独立保留；删除手工对照入口及无效报告指引；把高频动作放顶部。用户本次指令已明确批准产品访问行为变化，无需重新请求相同批准。

验收：无配对即可标记、停止统计、导出ZIP及启停/清空；跨站/未知Host/缺失Origin/GET修改继续拒绝、过快返回429；TXT读取兼容。320/390/844/1440视口无重叠/横向页面溢出，下载/清空滚动后仍可用，抽屉/键盘/筛选/暂停保留；手机/TV共用弹窗不再显示配对码。先跑修改后的访问/报告JUnit和实际Java页面浏览器检查，再一次TV64构建、包内MPV库身份/签名验证。回滚到本节基线恢复这组App源码/测试；FEL native提交保持独立。APK与性能验收不混同。

#### 实现及软件验证结果

- `DiagnosticAccess`删除配对码、token及其过期状态，`allowAction()`独立保留每秒5次有界限频；`sameOrigin()`拒绝缺失/空/null来源，并保留设备已知Host与URI严格比较。`DebugLogs.controlRequestError()`统一限制所有修改/诊断操作，方法、来源、频率失败分别返回405/403/429。网页不再读写sessionStorage或发送Authorization；App共用弹窗也删除配对提示。
- 顶部第一行依次为下载TXT、清空、暂停、工具；第二行分类横滑，手机搜索入口位于分类右侧。ZIP及低频工具保留在原抽屉。删除手填对照表单、接口及专用发布方法，报告改为提示保存修改前后日志；历史事件格式保持可解析。
- `DiagnosticExportAccessTest`共5项通过：无需配对即可操作、5次/秒及窗口恢复/时钟回退；同源IPv4/IPv6/localhost、跨站/未知Host/缺失与异常Origin；既有ZIP快照/哈希、TXT防伪及incident恢复检查保留。结果见证据目录`access-tests.xml`。
- `scripts/verify_crawler_web_logs.py`使用实际Java生成的开启/关闭页面、已安装Chrome和本地模拟HTTP响应通过320×640、390×844、844×390、1440×900。顶部手机93px、桌面83px，下载/清空滚动后命中有效，按钮尺寸/重叠、分类、搜索/暂停、Console、安全转义、抽屉焦点/Escape、页签及旧dialog降级通过；标记/深度统计确认/停止/ZIP/清空/采集启停均发送不含Authorization的同源POST。浏览器检查验证页面发出的请求，真实服务器访问判定由上述JUnit及编译验证覆盖，未冒充电视实测。证据：`browser-run.log`、`browser/browser-result.json`及截图。
- JDK21下定向JUnit和`:app:assembleLeanbackArm64_v8aDebug`一次实际构建61秒通过，108任务中14执行、94复用缓存，隔离CXX目录且不重编FEL。最初仅因沙箱拒绝Gradle缓存锁和Chrome启动而重试权限；页面生成成功项没有重跑。证据：`gradle-build.log`，原始环境拒绝见`gradle.log`/`browser.log`。
- TV64 buildTime=`202609160721`，APK为`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`；164143897字节，SHA-256=`f5f13571b3a210ab689d8aed3963620399bbcd75f191ecdf7e49debc98938eab`。10个MPV包内库全部与已提交资产一致，v2签名通过，ZIP开销802588字节。libmpv仍为FEL候选`240935cf90a8ff660bc11cf8f3d20ef2be228ee559317db952af1ee1d1a1a62d`。固定副本为证据目录`webhtv-tv64-debug-web-actions.apk`；旧候选另存`prior-fel-candidate-tv64.apk`，未安装或推送。
- 本轮证据统一位于`/private/tmp/webhtv-debug-web-actions.ALJuwC/`。FEL电视实际像素、bind/map收益及p95验收继续归属P2-4，不能把本轮软件/打包通过作为卡顿已解决的证据。

### 14.17 音频直通的面板结论误报（2026-09-18）

- 用户确认音频行已显示实际直通，但结论仍报“decoder 未初始化”。根因位于共用 `PlayerOsdController.getDiagnosis`：只检查 Exo analytics 的 decoder 名称为空，没有使用音频行的实际输出快照；名称缺失不能证明解码失败。本文既有音频诊断约定明确直通无需 App 音频解码器，本轮是落实既有诊断设计的局部修复，无上游/播放策略变更。
- 实现：每次面板刷新只读取一次 `AudioPlaybackDiagnostics.Snapshot`，音频行与结论复用它。实际 `ACTIVE` 直通说明“无需 App 解码器”，压缩直出/卸载说明由音频设备解码；PCM 名称缺失说明“名称未上报”，尚未观测到输出说明待确认，不据此断言无声。
- 优先级：实际音频 `FAILED` 仍提示音频链路失败，播放器错误、轨道不支持/未选中、网络和掉帧提示继续优先于正常输出说明。音频失败不会再被当前视频硬解选项误解释成视频硬解失败。手机/电视、点播/直播共用此控制器。
- 范围：仅上述控制器与本文；guard `AV-DIAG-01-AUDIO-HINT` / `quick-fix`，基线 `623b069261bf8f9d559969e9c99a6775e547443c`，保护既有 `app/.cxx/` 104个文件。采样频率、音频输出、解码选择及计数逻辑保持原行为。
- 验证：JDK21 下 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 一次通过，耗时38秒，39项任务中2项执行、37项复用；使用既有隔离 CMake init-script。`git diff --check` 通过，编译日志位于 `build/avdiag-audio-hint/compile.log`。无新增测试、native重编或设备操作；未打包/安装 APK，不以编译代替 HDMI/AVR 的真实出声验证。
- 回滚：将本单元两个文件恢复至基线提交；不回退其他播放器修复。

### 14.18 单视频轨无效重选与重复能力日志优化（2026-09-18）

用户在核对日志35后明确批准两项优化：只有可用的替代视频规格才执行自动约束；解码能力首次/变化时完整记录，重复查询复用快照。作为本诊断任务暴露的 Exo 性能修复，继续使用本文，不另建平行方案。基线 `4818057cd64c2c62c94e7208d9121719b4d11fe0`，guard `AV-DIAG-01-EXO-RESELECT` / standard；原有 `app/.cxx/` 104文件保护。06:35开始，06:55–07:00为代码、定向验证、TV32打包和原子提交/tag的执行目标。

#### 证据与最佳实践决定

| 来源 / 日期 / 等级 | 实际阅读内容、适用性与决定 |
| --- | --- |
| 用户 `webhtv-debug-log (35).txt`，2026-09-18 / A（记录事实） | `dvhe.05.09/3840×2160` 使用 `OMX.MS.DOLBY_VISION.DVHE.STN.Decoder`；单视频轨仍恢复/收紧约束。三轮播放线程能力日志各92条，跨度641/530/531ms，`sourceCapturedAtNs→enqueuedAtNs`累计461/365/358ms，含调度/锁等待，不能当纯CPU耗时。两段集中新增166/144掉帧；4条underrun是回调，feed age不是实际断音时长。导出标记partial，不能由此声称完整因果或最终收益 |
| 当前 `ExoUtil.AutomaticVideoConstraintController`、`ExoDiagnosticCodecAdapter`、`PlaybackDiagnosticCollector`、`DiagnosticLogBuffer.event/offer`，基线同上 / A | 轨道形状只参与日志；`setParameters`仍执行。候选包装器在同步选轨路径逐项生成JSON，再入队；异步落盘并未移走生产端序列化。与用户APK `206a57e0e337304a8b78712c487ef245d7cb0fa0` 比较，相关逻辑仍在当前代码中 |
| Media3锁 `e3e922d5c01bc0b564849940fe589daf37360d15`及实际本地sources.jar SHA-256 `4d158d63ab0a99688880d6acfdc73ed340f09fa9ac9dd1934fbaa0babfcad086`，2026-09-18 / A | 已读 `DefaultTrackSelector.setParametersInternal`、`MappingTrackSelector.selectTracks`、`ExoPlayerImplInternal.reselectTracksInternal`、`MediaCodecUtil.getDecoderInfos`、`MediaCodecVideoRenderer.maybeDropBuffersToKeyframe`。参数改变会重选并重新调用supportsFormat；列表本身有缓存，相同选轨结果不会必然重建renderer，flush也可能来自丢帧追赶。补读同revision的 `DefaultTrackSelectorTest` 视频约束/override测试，保留Media3本身的ABR和手选语义 |
| [Media3 Track selection](https://developer.android.com/media/media3/exoplayer/track-selection)，2026-09-18 / A | 准备后以实际Tracks判断可用性，同一group代表同内容的不同格式，override表达用户选择。因此门控使用已选group内受支持的可自适应规格；不靠URL、协议名或仅manifest数量推断。固定轨、手选视频覆盖及尚无轨道信息时保留当前选择 |
| [Media3 #2316](https://github.com/androidx/media/issues/2316)、[#824](https://github.com/androidx/media/issues/824)，2026-09-18 / D | 已读issue正文：轨道启用可能伴随停顿、ABR行为依赖真实可选轨与输入。未将用户报告当本设备因果或维护者方案；检索中的#2521预览缩略图无关，排除。不移植上游issue补丁 |
| [spdlog v1.15.3 dup_filter_sink.h](https://github.com/gabime/spdlog/blob/v1.15.3/include/spdlog/sinks/dup_filter_sink.h)，2026-09-18 / B | 已读完整实现，借鉴重复信息计数；拒绝照搬字符串生成之后才过滤、按时间抑制任意事件的做法。WebHTV在逐项JSON生成之前比较结构化能力快照，每次重复查询仍发一条引用/累计查询次数，不抑制真实错误 |
| [Log4j Performance](https://logging.apache.org/log4j/2.x/manual/performance.html)，2026-09-18 / A（官方实现建议） | 已读生产端避免昂贵参数/消息提前构造的说明；将判重放在候选事件展开之前，不在本修复中重构全局日志writer。此处不是新的调度/解码算法，学术论文和GPU benchmark不适用；目标电视性能仍须同片实测 |

在线原文与哈希保留于 `build/avdiag-exo-reselect/research/`；使用仓库配置的127.0.0.1:7897代理。一次architecture旧URL返回404，线程语义由实际Media3源码确认，不依赖该页面。没有依赖升级、上游提交合入、native或ABI改变。

| 方案 | 取舍 |
| --- | --- |
| 不修改 | 单轨无收益的选轨和重复JSON开销继续存在，不采用 |
| 只恢复为原生Media3策略或关闭调试日志 | 前者移除项目已有多档位保护，后者失去正常诊断能力，不采用 |
| 窄App适配（采用） | 轨道适用性门控保留初始化基线、实际多档位调整/恢复与手选；每个collector独立、有界、按媒体owner/capture generation隔离的能力快照记录首次/变化与重复引用。保留候选顺序、secure/tunneling/policy区分、完整错误和诊断分类 |

实现范围为 `ExoUtil`、新增轨道适用性策略及测试、Exo诊断适配器/collector/快照缓存及测试、本文/索引。快照覆盖候选顺序、硬软属性、profile/level；读取失败不被当成可复用的成功能力。缓存不改变delegate查询结果，不保存播放器/Surface/native资源，不增加线程或周期采样。

验收：固定单轨/未知/非自适应/不支持替代轨/手选不发自动约束；真实多规格仍使用现有冷却与恢复策略；重复能力不再展开整套候选事件，变化/清空/重新采集/切源重新完整记录，缓存有界且并发安全。一次定向JUnit与TV32快速Release构建；按同一日志三轮候选查询fixture核对输出事件量。电视无法ADB，目标电视DV5掉帧和听感收益不冒充已实测；构建产物供用户用同片段、同设置在线日志对照。

回滚：本guard所有App源、测试、文档作为一个提交回退至上述基线；现有native、手动视频解码和音频直通策略保持。

#### 实现与验证结果

- `ExoVideoConstraintApplicability` 以当前已选video group、adaptive support、受支持格式的尺寸/帧率/码率差异和手选override决定适用性；未知与固定单轨返回不可调整。`AutomaticVideoConstraintController.buildInput` 在普通刷新和掉帧/codec故障两条路径共同门控，并撤销固定轨上的定时重评；切会话仍按原流程恢复初始化基线。多规格的原冷却/恢复策略不改，最低档被选中后也保留恢复资格。
- `ExoDiagnosticCodecSnapshotCache` 每collector最多32个查询键，每键至多128个候选；以owner和capture generation隔离，准备新媒体/释放时清空。结构化快照包含候选次序、硬软/vendor属性及原有最多64条profile/level展开信息，保留truncated标记；MIME、secure、tunneling、policy分别建键。相同数据只输出带snapshotId/queryCount的单条引用；读取失败不缓存为成功，delegate的真实查询、排序及异常路径原样保留。全部JSON仍走现有脱敏/有界队列。
- 32项不同的定向用例通过：原自动约束冷却/恢复16项、快照变化/会话/清空/有界/并发计数8项、单轨/手选/最低档恢复/未知规格等门控8项。第一轮24秒完成编译，门控测试因宿主JVM的 `Android TextUtils not mocked` 在构造Media3轨道对象时有7项环境失败；将平台对象读取与纯策略判断分开后，只重跑该8项，未放宽Android mock或断言。其余24项成功证据保留在 `first-test-results/`。
- 单次最终Gradle执行在3分12秒内通过定向重验及 `:app:assembleLeanbackArmeabi_v7aRelease`，192项任务中33执行、4 from-cache、155 up-to-date；使用JDK21、`-PfastRelease=true` 和既有隔离CMake init-script。因上述测试夹具适配和TV32打包，收尾晚于最初06:55–07:00目标；未重复已通过的缓存/恢复用例，未增加设备矩阵。
- 将日志35的18次实际codec-selector查询转换为fixture并调用编译后的生产缓存：4个完整快照、14次引用；原222条候选/profile事件变为56条，减少约74.8%。这是同一输入的诊断事件量结果，不是CPU或掉帧收益测量；独立的动态音频能力/错误事件继续记录。原始fixture、Java回放与输出分别为 `codec-query-fixture.json`、`CodecSnapshotReplay.java`、`replay-result.txt`。
- TV32 APK：`app/build/outputs/apk/leanbackArmeabi_v7a/release/leanback-armeabi_v7a.apk`，125767037字节，SHA-256 `8df919f2188530913f8a3adaf833ab27133afc22ef6339a4da2e8baea4fd4525`。ZIP完整性、29个native条目均为armeabi-v7a及APK签名校验通过；未安装或发布。Java代码由手机/电视共用，构建证据不代替用户电视DV5的同片实测。
- 所有本轮软件证据集中于 `build/avdiag-exo-reselect/`；源码和产物在本guard内按一个逻辑单元提交/tag。用户电视上的最终掉帧、重缓冲及听感变化仍需新版在线日志确认。

## 15. 验收矩阵：如何证明日志真的够用

### 15.1 无ADB原则

验收执行者可以在研发机用测试工具制造故障，但**判读者只能收到与普通TV用户相同的TXT/诊断包**。不能偷用ADB/logcat/IDE观察来补漏后宣称“无需ADB已覆盖”。结果必须能引用包内事件ID还原链路。

### 15.2 具体场景

| 用例 | 故障/对照 | 日志最低验收结果 |
| --- | --- | --- |
| T01 正常AVC/AAC | Exo硬解、Surface、PCM | C/V/S/A各层身份连贯，实际decode/output路径明确，音视频终态独立 |
| T02 HEVC Main10黑屏样本 | 本文用户样本或等价fixture | 记录实际codec、fixed-size requested/actual、frame层级、surface/shutter；不能直接断言模拟器不支持 |
| T03 MPV视频失败音频继续 | mock/native故障使hevc init fail、vid=no、aid有效 | `videoPartialFailure=true`，封面来源明确；不输出“首帧成功=视频正常” |
| T04 codec候选首个失败 | create/configure/start失败后第二个成功 | 完整顺序/错误码/实际成功codec；诊断不多计黑名单 |
| T05 所有codec失败/扩展缺库 | hw-only、名称/profile过滤、JNI/能力查询失败、Java提前返回、NDK按MIME创建及soft扩展缺失分别测试 | 按9.3.1区分无合格候选、查询失败、加载失败及实际create/configure/start失败；未知操作不能填未尝试 |
| T06 Surface生命周期 | 旋转/切全屏/PiP/重建/seek | old/new surface和decoder绑定明确；过期事件不能写入新attempt |
| T07 封面/遮罩/静态视频/合法黑场 | 分别触发 | 展示来源与selectedVideo明确；合法静态或黑场不能直接判decoder挂死 |
| T08 输入不是媒体/错误Range | HTML/401/403/206不匹配/外部loopback不可见 | 输入层证据和不可见边界；不误标解码器坏 |
| T09 DRM/secure | key不可用/secure surface | 等key与codec失败分开；不泄露key或进行像素捕获 |
| T10 音频解码失败 | AAC/AC3等decoder init/runtime故障 | 原/实际轨、异常链和最后正常层；其他video进度不掩盖 |
| T11 音量与焦点 | player0/系统mute/focus loss/duck | 每一层设置来源、有效值和变化时间；平台不可见mute明确未知 |
| T12 AudioOutput短写/失效 | 0写、部分写、dead-object/负返回、underrun | 单位/接受量/错误/播放头/epoch可还原；正常短写非故障洪泛 |
| T13 路由切换 | speaker→Bluetooth/HDMI/USB→speaker | discovered/preferred/actual分开；切换后output重建和格式快照完整 |
| T14 直通/offload | AC3/EAC3/DTS/TrueHD、可用和不支持各一项 | actual模式、降级链；不存在PCM统计不判无声；不自动改直通 |
| T15 多声道→立体声 | 5.1/7.1与2.0切轨/切文件 | mask/layout/mapping、release/reinit、路由能力前后；定位残留状态候选 |
| T16 处理链静音 | 源静音、后处理gain0、正常低音量 | 可选pre/post统计区分；不录制原PCM，不认定物理声压 |
| T17 时间戳/seek/reset | audio uint32 wrap、flush回零、timestamp false、PTS跳变 | epoch/validity正确，不用陈旧timestamp/0伪造正常或卡死 |
| T18 切源/预载/切内核 | 快速切3个媒体并延迟回调 | trace/player/mediaGeneration正确；preload不能被统计成前台帧 |
| T19 主线程/MPV卡死 | 阻塞模拟或既有hang fixture | 缓存快照和native此前错误仍可导出；export timeout有partial；诊断不制造额外deadlock |
| T20 崩溃/被杀后启动 | Java异常/native crash/系统kill/API旧版本 | 上次unfinished trace、journal、exit-info状态；未获stack不宣称完整 |
| T21 持续日志/洪泛/磁盘不足 | 容量充足下5秒内超过32条不同事件→最后新错误→静默；跨文件轮转；实际队列耗尽/writer失败另测；标准级别INFO错误 | 正常容量下所选级别事件完整导出，无固定条数丢弃；轮转持续采集并声明历史边界；实际损失在结束前/后导出均可见，sink零丢弃不冒充全链完整；不阻塞播放 |
| T22 安全输入 | URL query token、Cookie空格、堆栈多行、恶意标题HTML/CRLF、超长node | TXT/JSON/网页都脱敏并正确转义；日志不可伪造；修改操作仅接受同源POST并限频（14.16已取消配对） |
| T23 旧API/不支持属性 | API支持下限、MPV属性unavailable/IJK无AudioTrack | not-supported/unavailable与实际false分开；没有崩溃或同步轮询风暴 |
| T24 原版对照 | 同设备/同sample/同PTS、原版与WebHTV硬解 | 对照包记录确知和unknown：APK/内核/decoder/renderer/配置/output；不靠“硬解”标签断言同路径 |
| T25 TV用户端闭环 | 不接ADB，仅遥控器+局域网网页 | 开启→复现→标记→下载→判读全程可完成，面板不开也有日志 |

### 15.3 单元、契约和性能测试

- schema兼容、脱敏、JSON/HTML、异常cause循环/分片、ring/pinned/rotation、writer失败、seq gap、flush watermark、开关/停止/清空及崩溃恢复的确定性单元测试。
- Exo基于已有Media3 audio sink测试/forwarding contracts覆盖buffer position不被采集破坏、短写、格式变化、flush后epoch、listener完整转发；禁止把错误“修成继续播放”来通过诊断测试。[R12]
- MPV NODE深度/大小、MPV_FORMAT_NONE、旧generation、late callback、queue overflow、限流计数、observer卸载和command timeout契约测试。
- “无输出”判定加入合法暂停/seek/缓冲/音频-only/封面/低帧率/静音对照，检验误报。
- 开关诊断的性能A/B至少3次可比运行：同设备同sample同播放参数，观察起播/seek、音视频drop/underrun、CPU/Java/native内存、分配率、磁盘和queue延迟。目标是不出现新增卡顿/ANR/持锁I/O；若差异超过测试噪声且达到事先约定的产品阈值，必须优化或下调采集，不宣称零开销。
- 建议实施前明确阈值：标准诊断CPU均值增加≤1个百分点、P95起播/seek额外≤20ms、稳态额外内存不超过声明预算；这些为候选门槛不是已测事实，低端TV基线噪声及CPU核数归一方式需写入测试记录。深度模式单独告知开销并验证限时恢复。
- 仅改变Java诊断不跑无关全ABI/native矩阵；改JNI/native则验证两受影响ARM ABI/导出/依赖、对应播放器实播。依赖版本对齐和实际APK内容仍是门禁。

### 15.4 完成定义

每个已批准阶段必须同时交付：对应事件覆盖清单、真实导出样例、测试结果、哪些设备/API字段仍不可得、单变量A/B例子、性能/安全结果及回滚锚点。实现了D0/D1不能声称整个方案已完成；只写日志但仍无法分清“音轨未选”与“AudioTrack无声”不通过。

## 16. 结构化日志示例（设计示意，不是真实新测试结果）

为可读性省略公共字段；实际记录每行均有schema/run/trace/attempt/seq/timestamp。

```json
{"event":"video.configure","decoderId":"vd-2","observed":{"decoder":"OMX.qcom.video.decoder.hevc","mime":"video/hevc","size":[1920,1080],"bitDepth":10,"surfaceId":"sv-3"},"status":"known","source":"codec-adapter"}
{"event":"surface.resize","operationId":"op-8","surfaceId":"sv-3","requested":{"mode":"fixed","size":[1280,720]},"observed":{"size":null},"status":"pending-callback","reason":"capability-profile"}
{"event":"surface.changed","parentEventId":"op-8","surfaceId":"sv-3","observed":{"holderSize":[1280,720],"valid":true},"status":"known"}
{"event":"video.output.summary","decoderId":"vd-2","windowMs":5000,"observed":{"submittedFrames":120,"sourceCadenceFps":24},"codecRenderCallbacks":{"value":null,"status":"not-collected"},"physicalDisplay":{"value":null,"status":"not-observable"}}
{"event":"mpv.output.failure","observed":{"videoAvailable":true,"videoSelected":false,"audioSelected":true,"videoPartialFailure":true},"errorId":"err-4","reason":"hevc-decoder-init-failed"}
{"event":"video.presentation.source","observed":{"kind":"artwork","selectedVideo":false},"source":"player-view"}
{"event":"audio.output.configure","audioOutputId":"ao-4","requested":{"passthrough":true},"observed":{"mode":"pcm","sampleRate":48000,"channelMask":"0xFC","channels":6},"reason":"output-policy","source":"audio-output"}
{"event":"audio.route","audioOutputId":"ao-4","observed":{"deviceType":"HDMI","deviceId":"report-local-2"},"status":"known","source":"AudioTrack.getRoutedDevice"}
{"event":"audio.output.playhead","audioOutputId":"ao-4","epoch":2,"observed":{"headDeltaFrames":240000,"windowMs":5000},"timestamp":{"valid":false,"status":"unavailable"},"audibility":{"value":null,"status":"not-observable"}}
{"event":"diag.health","observed":{"droppedPeriodic":80,"lastCapturedSeq":421,"lastFlushedSeq":410,"nativeOverflow":false},"completeness":"partial","missingEvidence":["periodic-window","unflushed-tail"]}
```

正式schema采用第4节固定status枚举并拒绝未定义值；不能让同一“未知”在不同collector里随意产生不可解析文案。示例中事件存在不代表失败已被单条解释，report必须组合上下文。

## 17. 调研依据、适用性与限制

原设计在线资料访问日为2026-09-13；通过系统配置代理 `127.0.0.1:7897` 获取并读取正文/实际源码，而非搜索摘要。2026-09-14夜间增量仅复核本地代码和用户附件，未重复开展上游合并研究。未上传用户日志或请求网盘资源。固定版用于接口语义，不意味着建议升级/降级到该版本。

| 编号 / 等级 | 实际来源与revision | 支持的结论 | WebHTV适用性/限制与决策影响 |
| --- | --- | --- | --- |
| R01 / A | [Media3 Debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging)，在线文档快照 | EventLogger覆盖状态、轨道support、decoder；默认调试观察面向Logcat | 借鉴事件，不以“加EventLogger”代替写入App调试日志 |
| R02 / A | [Media3 Analytics](https://developer.android.com/media/media3/exoplayer/analytics)，在线快照 | AnalyticsListener/EventTime关联事件媒体；预载媒体可能不同于当前媒体；状态需要上下文解释 | 保留event/current media及role，不用全局当前trace回填旧事件 |
| R03 / A | [Android Audio focus](https://developer.android.com/media/optimize/audio-focus)，在线快照 | Android版本/target影响焦点请求；系统可自动duck/fade/mute，部分不通知App | 采集有效gain和系统能力，不能用“没focus loss”排除静音 |
| R04 / A | [AOSP MediaCodec.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/media/java/android/media/MediaCodec.java)，tag `android-15.0.0_r1` | CodecException恢复/暂时性、output-surface rendered callback可延迟批量且旧版本可漏 | 错误链字段和V2/V3/V4分层；不把callback等同物理图像 |
| R05 / A | [AOSP AudioTrack.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/media/java/android/media/AudioTrack.java)，同tag | head uint32/reset、timestamp可不可用且稳定后应低频查询；actual route；写入错误 | A07–A13计量/采样/未知语义；不用hidden latency接口 |
| R06 / A | [AOSP PixelCopy.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/graphics/java/android/view/PixelCopy.java)，同tag | 复制最近排队buffer、source无数据/保护/invalid等失败码 | 可选有限探针；不证明物理显示、不绕过保护 |
| R07 / A | [mpv client API](https://github.com/mpv-player/mpv/blob/v0.40.0/include/mpv/client.h)，tag `v0.40.0` | event queue有限、overflow；property/async请求；PLAYBACK_RESTART不是present成功 | 有界observer/队列健康、重命名诊断证据而不是照搬ready |
| R08 / A | [mpv properties](https://github.com/mpv-player/mpv/blob/v0.40.0/DOCS/man/input.rst)，tag `v0.40.0` | hwdec配置vs实际、decoder/output格式分层、drop/avsync语义和不可用状态 | 每属性可用性/age；平台实际fork需capability probe |
| R09 / A | [FFmpeg libavutil/log.h](https://github.com/FFmpeg/FFmpeg/blob/n8.0/libavutil/log.h)，tag `n8.0` | 默认stderr、log callback须线程安全、format_line2截断语义 | 复用各内核日志owner桥接；不能进callback做慢I/O或假定stderr已导出 |
| R10 / A | [AOSP ApplicationExitInfo.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/app/ApplicationExitInfo.java)，同AOSP tag | ANR/native trace可能存在且环形保留不保证 | journal＋下一次启动恢复；不承诺所有crash有stack |
| R11 / B | [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)，在线快照 | interaction id、敏感数据排除、日志注入、资源耗尽/写失败和访问控制验证 | 日志基础设施与安全不是可选附录；纳入D0与T21/T22 |
| R12 / A | 本地Media3 `e3e922d5c01bc0b564849940fe589daf37360d15`：`libraries/exoplayer/.../util/EventLogger.java`、`DecoderCounters.java`、`audio/DefaultAudioSink.java`、`AudioTrackAudioOutput.java`、`ForwardingAudioSink.java`、`ForwardingAudioOutput.java`、`mediacodec/ForwardingMediaCodecAdapter.java`、对应`audio/DefaultAudioSinkTest.java` | public forwarding hook、实际AudioTrack、生命周期/格式变化测试；日志可重用而不重写播放 | 注意锁的patch/override与最终AAR一致性；不是已完成二进制验证 |
| R13 / B | [mpv-android MPVLib.kt](https://github.com/mpv-android/mpv-android/blob/751d53205cc3d878c7161d79a97e1b65ed6ed37b/app/src/main/java/is/xyz/mpv/MPVLib.kt)，HEAD `751d53205cc3d878c7161d79a97e1b65ed6ed37b` | 现成event/property/log observer桥接模式 | 参考职责，不复制其同步锁行为或以外部项目能力替代WebHTV实际实现 |
| R14 / B | [Media3 #2765 维护者讨论](https://github.com/androidx/media/issues/2765#issuecomment-3258334393)，读取issue及comments快照 | READY+音频正常+视频黑屏仍可能是decoder自报正常或UI隐藏；建议对照设备/decoder/media | 支持多层证据、单变量A/B和厂商边界；不是本次模拟器根因证明 |
| R15 / D | [Media3 #2711](https://github.com/androidx/media/issues/2711)、[#2519](https://github.com/androidx/media/issues/2519)、[#3339](https://github.com/androidx/media/issues/3339)、[#3316](https://github.com/androidx/media/issues/3316)，issue正文/相关comments快照 | 用户报告无错误黑屏、多声道→立体声无声、AVR侧/后声道映射问题 | 用于选择T02/T15等场景，不把未复现报告当普遍Android事实或直接修复依据 |
| R16 / A（实现事实） | 本文第2节当前WebHTV源码；基线HEAD完整SHA见第0节 | 当前sink同步无上限、部分日志已有、MPV语义/Surface操作等 | 改造必须复用/补齐，不重复建设或改写已有播放策略 |
| R17 / D（用户现场） | 2026-09-12用户截图、两份日志及“原版可硬解”补充 | 存在实际用户症状与部分日志链 | 缺设备现场、原版准确版本和可控A/B；只作为诊断需求与fixture素材 |
| R18 / A（本地实现事实） | 2026-09-14读取 WebHTV `5cde3c015258f620f264d5f3ffe0a437c2ea3d48` 的 `MpvDiagnosticsPolicy.NativeLogWindow`、`MpvPlayer` native log回调、`MpvPlayerEngine.buildConfig`相关配置；本地 `build/mpv-native/mpv-android/buildscripts/deps/ffmpeg/libavcodec/{mediacodec_wrapper,mediacodecdec_common,mediacodecdec}.c`，源码HEAD `177f090e0503b7e013922ca903bde14b1c375f18`，前两文件含本地修改 | 32条限流及仅下一条日志带出汇总；标准`all=warn`与INFO查名错误；查名NULL的多义性、Java/NDK分支和通用失败出口 | 证明9.2.1/9.3.1需要的本地取证边界，不证明用户APK包含完全相同源码/补丁，不把基线HEAD冒充最终artifact身份 |
| R19 / D（新用户现场） | 2026-09-14接收的 `webhtv-debug-log (3) (1).txt` 与 `IMAGE 2026-09-14 23:16:43.jpg`；run/generation/trace及logSeq见1.3 | MPV HEVC Main10查名失败、音视频状态分离、32条native事件与缺失限流统计 | 只含MPV本次复现；未获得新的Exo或原版对照链，不证明两内核同因或设备/ABI不兼容 |

补充读取了 [Media3 Troubleshooting](https://developer.android.com/media/media3/exoplayer/troubleshooting) 的seek/媒体容器/平台限制说明，帮助限定输入/时间戳边界。本文未提出新编解码算法或通用性能优化，因此学术论文/算法benchmark类证据不直接适用；性能验证采用本项目同设备同sample开关诊断A/B，而非搬用无关论文分数。上游revert/完整commit ledger不适用：本轮没有选择/合入任何上游提交。

### 17.1 调研结论与停止条件

官方接口语义、实际代码、成熟项目事件桥接及维护者讨论已能决定：**建立分层证据、输出路径观测、非阻塞有界日志、隐私和完整性，不能把更多verbose等同于足够诊断。** 继续扩大搜索不会解决无法访问的电视/模拟器/AVR状态；剩余不确定性应转入上面的验收和最小设备对照，而不是继续罗列资料。

## 18. Recovery anchor / 后续唯一动作

- Objective：完成获批的Exo单轨无效自动约束门控和能力日志快照复用，减少选轨时对播放线程的干扰，见14.18。
- Plan：两项优化、32项定向用例、真实查询fixture回放和TV32快速Release均已完成；按本guard原子提交/tag收尾。
- Current unit：`feature/mpv-dv7-fel` / 基线 `4818057cd64c2c62c94e7208d9121719b4d11fe0`；guard `AV-DIAG-01-EXO-RESELECT`；保护 `app/.cxx/` 104文件。
- Files：14.18列出的Exo App源码、两个定向测试与本文/索引。
- Evidence：32项用例通过；18次真实查询的候选事件222→56；TV32构建、ZIP/ABI条目与签名校验通过。产物SHA及详细证据见14.18与 `build/avdiag-exo-reselect/`。
- Unverified：未安装/发布新APK；用户电视无法ADB，同片DV5最终掉帧收益需新包在线日志确认。
- Rollback：本guard单元原子回退至基线；不推送。
- Exactly one next action：以本节软件验证证据执行guard finish，原子提交并创建本地恢复tag。
