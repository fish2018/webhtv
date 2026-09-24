# E11 Exo 压缩音频输出与跳转生命周期

## Recovery anchor（2026-09-21，首次起播快速恢复实施）

- Objective / acceptance：首次新视频也能受益；厂商压缩输出已具备启动供数却不推进时，用短观察窗触发 PCM，并复用当前媒体与样本队列。保留开关修复、标准 offload/HDMI、用户解码偏好、音画同步与正常播放性能。
- User decision：用户明确“根据最佳实践方案进行优化”，随后限定“仅优化，不联机测试。尽快落地最佳实践方案”；2026-09-21 续接时明确“测试忽略”“继续”，因此跳过剩余策略测试，使用已有集成测试与构建结果完成代码交付，不连接设备、不把硬件时延写成已验证。
- Lane / guard：`upstream` / `E11-first-playback-recovery`；开始实施 17:22 Asia/Shanghai，预计实现 15 分钟、定向故障注入/TV32 构建 10 分钟、闭合 5 分钟，目标约 17:52。
- Branch / baseline：`main` / `24fa078d2dc8a404fad23fc30972e5fc4a8a1b5b`；回滚锚点 `recovery/E11-audio-passthrough-switch/20260921163519-24fa078d2dc8`。保护预存 `app/.cxx/` 104 个文件；会话恢复后新增的 `.codex-resume/` 与 `codex-resume` 同样保持原样并排除出提交。
- Scope：`ExoCompressedAudioDirectPolicy.java`、`ExoUtil.java`、新增 `ExoStartupAudioRenderer.java`、对应 policy/renderer 主机测试及 `testLeanback` 恢复集成测试、本文/索引；不改依赖、native、网络或视频选路。
- Decision：沿用下方已完成的跨项目研究，选择 Media3 现有 recoverable renderer error 的恢复入口；不伪造 AudioTrack DEAD_OBJECT，也不复制整套 renderer factory。用 `ForwardingRenderer` 包装原平台音频 renderer，保留其构造选项/排序。800 ms 是有充分供数和有效 play 意图后的初始观察值，不是无条件起播等待。
- Startup fill：API 31+ 仅对自定义 vendor 输出，在完整 AU 已交付约 200 ms 音频后，允许降低实际启动门槛至已交付字节数并读回，保持 256 KiB 稳态容量。沿用发货 Media3 的 AAC/MP3 每 AU 帧数定义；未知/失败读回不当作填满，暂停、flush、stop、换路由及旧 attempt 不可触发提前恢复。
- Source proof：发货 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15` 的 `ForwardingRenderer` 完整委托；`ExoPlayerImplInternal.attemptRendererErrorRecovery → reselectTracksInternalAndSeek → seekToPeriodPosition` 复用当前 prepared MediaPeriod；`ProgressiveMediaPeriod.seekInsideBufferUs` 能在现有样本队列定位。该恢复会 disable/enable renderer，**不是音频独占重启**；缓存不足、live 或 keyframe 不在队列时仍可能重新加载。WebHTV 正常 back-buffer 设置保留关键帧，最终以真实 Media3 故障注入核验同 period/读取复用。
- Alternatives / risk：维持 10 秒只作通用兜底无法满足首次体验；直接 App prepare 已被日志证明额外消耗 3–9 秒；自建压缩 AU 重放/直接操作内部 renderer 生命周期引入额外拷贝与状态风险，本轮采用现有恢复合同。短启动门槛不缩容量或改变稳态写入节奏；不做跨媒体永久禁用或并行建轨。
- Completed implementation：policy 已实现完整 AU 供数统计、启动门槛调整与读回、800 ms 窗口、原始播放头/实际路由复核及每 attempt 一次恢复；`ExoStartupAudioRenderer` 包装原平台音频 renderer，在原播放线程发出明确类型的可恢复异常。内部恢复后的 PCM 进度继续用于确认既有失败记忆，不再要求 App 整项重新 prepare。
- Verification：已有 3 项真实 Media3 播放流程集成用例通过（首次停滞、断点起播停滞、健康直出），TV32 Debug 构建通过（44 秒），APK v2 签名与包/ABI 检查通过。集成用例使用受控 extractor/renderers/audio outputs；不能替代真实解码器或 HAL 验收。策略补跑因临时测试 source set 未发现目标用例而未执行，用户已要求忽略剩余测试，不能记录为全量回归通过。
- Evidence / artifact：`/private/tmp/webhtv-E11-recovery-wx7chb0j/` 保存 `gradle-tested.log`、3 项用例的 JUnit XML、`gradle-policy.log` 及 APK 检查记录；集成测试快照与当前源码一致。TV32 APK 为 162,518,123 bytes，SHA256 `15c31a01404a1e1678020cecb20df5085508b67ccd880904fb9ef02072654fd5`。
- Closure / limitations：代码实现和已有构建完成，按用户要求跳过剩余测试，以本 guard 一次原子提交及 annotated recovery tag 收尾，不推送。800 ms 是数据和状态条件成立后的判错窗口，未知门槛/路由、正常缓冲或 seek 等仍保留原通用兜底；不宣称真实 Sony 2 秒恢复或性能不回退已经实测。跨媒体失败记忆扩展和音频独占重启未纳入本次实现。
- Next action：使用已有证据关闭 `E11-first-playback-recovery` guard，提交本轮源码、测试及任务记录并创建本地恢复标签；不再运行测试或构建。

### 首次起播快速恢复交付记录

- `ExoUtil` 将同一个 policy 交给 sink 与 renderer 包装，只包装已有平台音频 renderer，保留原构造参数、扩展 renderer 排序、解码器偏好和直通开关。没有修改依赖、native 库、视频选路或网络实现。
- 自定义压缩输出在完整 access unit 对应至少约 200 ms 音频时，可降低实际启动门槛至已接收数据量，并以读回值为准；不缩小稳态容量。满足供数、播放状态、位置仍为零等条件后观察 800 ms，再检查原始播放头、路由和实际门槛。进度已推进、暂停、flush/stop、输出替换、路由失效或读回未知时不会按旧证据提前切换。
- 恢复通过 Media3 recoverable renderer error 复用现有 MediaPeriod 和可定位的样本队列，避免 App `startInternal` 重新准备媒体源。该入口会重新启用音视频 renderer，不能称为“只重启音频”；缓存不足、live 或缺少关键帧时仍可能重新加载。已有 10 秒通用检测保留为不满足早期判定条件时的兜底。
- 3 项集成用例已验证：首次和断点起播的静止输出均切换到 PCM，媒体源 prepare 与 period 创建各一次，恢复不新增数据源打开，首段待播音频会被重放；健康直出不创建 PCM、不重选 renderer。用例还断言受控场景在 2 秒以内进入 PCM，**不等同于实际电视在 2 秒以内恢复出声**。
- `gradle-policy.log` 的失败是 `No tests found for given includes`，属于临时测试目录配置问题，没有策略断言结果。此前“定向测试通过”应仅理解为上述 3 项集成用例；策略/失败记忆/输出归属的剩余回归依用户最新要求跳过，保留测试源码供后续使用。
- APK 路径：`app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`，`com.fongmi.android.tv`，560 / 5.6.0，唯一 ABI 为 `armeabi-v7a`。在本次提交前构建，应按上方 SHA256 识别；没有安装或联机测试。
- 代码、测试与本记录在 guard `E11-first-playback-recovery` 内原子提交，紧接着创建唯一 annotated local recovery tag；完整提交与 tag 以 guard 的 Git 记录为准。回滚可 revert 该单一提交，恢复到已修好直通开关的 `24fa078d2dc8a404fad23fc30972e5fc4a8a1b5b` 行为。

## 历史 Recovery anchor（2026-09-21，音频直通开关修复）

- Objective / acceptance：关闭 Exo 音频直通后直接使用 decoder + PCM，不再选择 vendor-direct、标准 encoded passthrough 或 compressed offload；保持 PCM 输出归属、隧道 PCM、开启时正常 direct/offload、失败记忆、音视频同步及用户解码偏好。
- User decision：用户明确要求顺道修复开关；此前首次快速起播研究已完成，新方案未被冒称已实现。
- Lane / guard：`quick-fix` / `E11-audio-passthrough-switch`。
- Branch / baseline：`main` / `e9fef2b90ee8aa634327c2c179eb3dc5d22cb1be`；研究 recovery tag `recovery/E11-first-playback-research/20260921160724-e9fef2b90ee8`。
- Scope：`ExoUtil.java`、`ExoCompressedAudioDirectPolicy.java`、`ExoCompressedAudioDirectPolicyTest.java`、本文/索引；保护既有 `app/.cxx/` 104 个文件。
- Completed actions：在 renderer 创建时将真实 Exo 设置传给 policy；track selector 的 offload 偏好同步受控。policy 在 offload/format/config/输出创建处检查设置，清理过时 vendor 能力缓存；保留所有输出的原状态包装。5 项新增用例覆盖禁用时零查询/零建轨、标准 encoded/offload、过时配置、PCM/tunneled PCM 和重新开启。
- Verification：53 项定向用例通过（policy 46、输出归属 7，0 failure/error/skipped），TV32 Debug 构建一次通过，Gradle 用时 41 秒；测试快照与当前源码一致。APK ZIP CRC、armeabi-v7a 唯一 ABI、包标识和 v2 签名通过。本轮未调整任何超时、实际缓冲或媒体重建方式。
- Evidence cache：`/private/tmp/webhtv-E11-first-playback-khaovka_/`，新 host init/测试快照与完整 Gradle 日志；native 暂存复用上一轮 `/private/tmp/webhtv-E11-admission-c18ee9ju/cxx`，不触碰预存 app/.cxx。
- Risk / rollback：开启直通的首次坏路径仍可能等待原 10 秒检测，B/C 仍未实现；开关修复单独 commit/tag，可独立 revert。无设备时不宣称 Sony 实播或性能已验收。
- Next action：关闭本开关修复 guard 后，用最终 APK 在目标 Sony 关闭直通播放原片，核对 decoder/PCM 与实际推进；开启直通的短窗及局部恢复按下方方案另行继续，不能把本轮开关修复当作 B/C 已完成。

### 开关修复交付记录

- 真实设置到选路：`ExoUtil.buildTrackSelector` 在关闭时使用 `AUDIO_OFFLOAD_MODE_DISABLED`；`buildAudioSink` 把同一 Exo 设置送入 `setAudioPassthroughEnabled`。policy 的标准 offload、encoded format、encoded output config 与最终 AudioTrack 创建均受控；关闭时清除已缓存 vendor 能力，避免旧查询绕过。不是仅修改 UI 标签，也没有全局强制 FFmpeg。
- PCM 仍经原包装发布/释放 `ExoAudioOutputState`；tunneled PCM、正常开启时的标准 offload/vendor、既有失败记忆与迟到回调保护的测试保持。用户在播放内的配置刷新继续通过已有 `PlaybackPerformanceDialog.refresh` 的 `ConfigEvent.playerPerformance()` 与 callback 处理，本轮不新增热切换状态机。
- 新增 5 项测试覆盖：关闭时 vendor/offload 查询和 vendor 建轨均零调用；标准 AAC/MP3/AC3/EAC3/DTS/TrueHD encoded 能力被拒绝；先前 cached/standard-offload/tunneled-encoded 配置不能创建；PCM/tunneled PCM 正常委托并维护快照；重新开启恢复候选。其余 41 项 policy 用例和 7 项输出状态用例形成 53 项最终回归。
- 构建命令：`bash gradlew -I /private/tmp/webhtv-E11-first-playback-khaovka_/switch-tests.init.gradle :app:testLeanbackArmeabi_v7aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoCompressedAudioDirectPolicyTest --tests com.fongmi.android.tv.player.exo.ExoAudioOutputStateTest :app:assembleLeanbackArmeabi_v7aDebug --console=plain`。仅执行一次，完整输出 `gradle-switch.log`；对应 JUnit XML 与测试源码快照已比对，没有重跑不相关错误分类/全部 ABI/native 依赖测试。
- APK：`app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`，162,518,123 bytes，SHA256 `5a05a6f7009dc1f323d7579053d4ca53437508d56073c57b0d1915a916ca8622`；`com.fongmi.android.tv`，560 / 5.6.0，仅 `armeabi-v7a`。ZIP CRC 和 APK v2 签名通过。证据 `switch-result.json`、`apk-switch-signature.txt`、`apk-switch-badging.txt` 在当前证据目录。
- APK 在代码提交前构建，诊断基线为研究提交 `e9fef2b90ee8aa634327c2c179eb3dc5d22cb1be` / dirty，应按最终 SHA256 识别。`adb devices -l` 仍无设备，本轮未安装，未验证 Sony 实际秒数或健康设备性能。关闭开关后的控制流已不进入坏 vendor 路线；开启时原 10 秒兜底仍存在，不能声称全部首次起播问题已解决。
- 本修复源码/测试/本文/索引为一个原子提交，guard `E11-audio-passthrough-switch` 立即创建 annotated recovery tag；具体提交和 tag 以本次 Git 记录为准。保护既有 104 个 `app/.cxx/`，不推送，不发布。

## 历史 Recovery anchor（2026-09-21，首次起播与快速恢复再研究）

- Objective / acceptance：首次播放的选路、实际输出确认和错误切换都计入起播时间；10 秒全局卡死异常不能作为常见音频故障的主恢复机制。保留健康直出、声道/音质、用户偏好、音画同步、隧道、视频手动解码和稳定播放性能。
- User decision：用户要求继续深度研究跨平台论文、文档、issues 和项目实现，并追加明确实施需求“音频直通开关没起作用，关了还是优先走的直通”。此前 A/B 授权继续有效；新的默认选路/跨媒体经验/音频局部恢复方案在下节分别标出边界。
- Lane / guard：`assessment` / `E11-first-playback-research`；仅本文和主索引。研究从 15:17 Asia/Shanghai 预计 25 分钟，后因调用链复核及追加开关修复延长；15:52 收敛查询，研究和开关修复的合并目标约 16:15。
- Branch / baseline：`main` / `123d871c027eb686702766bca995f616e8d2bd1e`；A 已提交，恢复 tag `recovery/E11-audio-startup-admission/20260921150255-123d871c027e`。保护原有 `app/.cxx/` 104 个文件。
- Completed evidence：A 只有同媒体/音轨/路由确认失败记忆，首次故障仍有 10 秒检测和约 3–9 秒重建成本；`ExoUtil.buildAudioSink/buildTrackSelector` 只把 passthrough=false 转为空 Context，仍安装 vendor policy 且启用 offload，已建立开关失效原因。下节已补读 Chromium/mpv 早期输入重放、GStreamer preroll/候选、Android 启动门槛、Envoy 分层失败隔离、AWS 超时文章及《The Tail at Scale》作者版正文。
- Evidence cache：`/private/tmp/webhtv-E11-first-playback-khaovka_/`，全文/源码、URL/时间/SHA256 元数据；此前证据继续保留，不重新评估已完成的上游范围。
- Plan status：研究方案已收敛；本 guard 没有生产改动、构建或设备测试。追加的开关修复将在本研究闭合后用独立 `quick-fix` guard 实施，范围见下节。B 的短窗和 C 的数据保留仍不是已实现能力。
- Rollback：本文/索引为独立研究提交；当前可执行生产基线仍为 A。追加开关修复独立提交，可单独 revert。
- Next action：关闭本研究 guard，随即实施已获明确授权的音频直通开关准入修复并运行对应主机回归与 TV32 构建。

## 2026-09-21 首次播放必须快速成功：补充证据与修订方案

### 纠正目标与当前缺口

旧方案 A 提升了准入一致性和部分重复播放的成功率，但没有完成首次起播优化。旧 B 即使把等待从 10 秒变成 2 秒，仍可能叠加日志中约 3–9 秒的媒体重建，不能据此交付“快速起播”。最终验收须覆盖**首次新视频、首次新音频配置、选错路径后的恢复全过程**，同时记录点击到播放的总时延与各阶段时延；不能只报首帧或解码器初始化速度。

最新用户追加的开关故障与原日志直接关联。现基线 `ExoUtil.buildAudioSink` 读取 `PlayerSetting.isAudioPassThrough(EXO)` 后只决定 `AudioTrackAudioOutputProvider.Builder` 的 Context 是否为空；之后仍无条件注册 `ExoCompressedAudioDirectPolicy` 的 offload provider、builder modifier 和输出包装。`buildTrackSelector` 又无条件设置 `AUDIO_OFFLOAD_MODE_ENABLED`。因此 `passthrough=false` 不是最终压缩输出的准入条件。修复应让关闭设置直接选择解码后 PCM，保留平台硬件/平台软件/FFmpeg 的现有优先级；这能在控制流上消除该设置下的首次 vendor 试错，尚不能用主机测试宣称 Sony 实际起播耗时已经测得。

### 新增来源与适用限制

访问日期均为 **2026-09-21**。等级沿用 Skill：A 为正式合同/准确源码，B 为维护者或成熟项目设计，C/D 为类比或未验证现场线索。本轮未选取新的依赖合并提交；下列固定修订全部只作研究引用，不 cherry-pick。

| 来源与版本 | 已读证据与等级 | 对 WebHTV 的决定影响及限制 |
| --- | --- | --- |
| [Chromium DecoderStream](https://chromium.googlesource.com/chromium/src/+/c52f26bf4995863398cbdd1a7cabc3dd45aad973/media/filters/decoder_stream.cc) 与 [DecoderSelector](https://chromium.googlesource.com/chromium/src/+/c52f26bf4995863398cbdd1a7cabc3dd45aad973/media/filters/decoder_selector.cc)，`c52f26bf4995863398cbdd1a7cabc3dd45aad973` | A/B：`Decode` 在首个成功解码输出前保留 `pending_buffers_`；初始化成功仍不是最终成功。初始解码报错后作废旧异步回调，继续有限候选，向备用 decoder 重放缓冲，首个有效输出才 `FinalizeDecoderSelection` 并清保留数据。另读同修订 `audio_decoder_stream_unittest.cc` 的配置变更/flush 测试。 | 借鉴“暂定选择→实证成功→提交选择”和旧回调隔离/输入重放。该测试不是 AudioTrack 停滞恢复测试，Chromium 的成功点是解码输出，不是扬声器实际发声；本项目必须把确认延伸到音频输出时钟。模板源码也适用于视频，但不据此改变本项目视频手动软解合同。 |
| [mpv vd_lavc.c](https://github.com/mpv-player/mpv/blob/e76a35ec95b27f5cf2d27b043b5e2e0d90e468ae/video/decode/vd_lavc.c)，`e76a35ec95b27f5cf2d27b043b5e2e0d90e468ae` | A/B：`send_packet` 在硬解探测期有界保留至多 32 个输入 packet；`receive_frame` 在失败后尝试下一候选，重放 `requeue_packets`；首个交付帧后结束 probing 并释放保留 packet。 | 支持有界保留“已做的工作”，无需从 URL 再启动整个解封装。32 个视频包不是适合 AAC 的容量或时限；此代码不解决 Android 压缩 sink 未报告错误的情况，不能直接移植或声称本项目已能音频局部切换。 |
| [GStreamer preroll](https://gstreamer.freedesktop.org/documentation/additional/design/preroll.html) / [decodebin](https://gstreamer.freedesktop.org/documentation/playback/decodebin.html)，访问日官方页面 | A/B：preroll 先准备样本，收到 buffer/GAP/EOS 分别处理；flush/状态变化有明确解除等待语义。候选可按 rank/应用规则过滤与排序，逐个 TRY/SKIP。 | 以实际阶段就绪替代把 READY/首帧当作整条链成功；候选提前准备，不默认同时启动三个占硬件的输出。EOS/短片不能永远等“填够”或被当成坏设备。 |
| [Android 12 AudioTrack](https://github.com/aosp-mirror/platform_frameworks_base/blob/cebf5c06997b64f4e47a1611edb5f97044509d76/media/java/android/media/AudioTrack.java)，`cebf5c06997b64f4e47a1611edb5f97044509d76`；[当前正式 API](https://developer.android.com/reference/android/media/AudioTrack#setStartThresholdInFrames(int)) | A：初始 start threshold 默认是实际 capacity；压缩输出这些 API 的 frame 单位是 byte。API 31+ 可单独 `setStartThresholdInFrames`；而 `setBufferSizeInFrames` 明确仅支持 PCM。实际值可能被钳制、随路由变更，必须读回。 | **新增可选方向：分别管理起播门槛和稳态容量**。不要把固定 256 KiB 当作必须填完才配享有早回退的产品条件，也不要通过不支持的压缩 buffer setter 硬缩容量。短启动门槛须根据完整音频 access unit/可播放时长设定并实测 underrun/功耗，不全格式写死字节数。 |
| [The Tail at Scale 作者全文](https://www.barroso.org/publications/TheTailAtScale.pdf)，Dean/Barroso，CACM 2013，DOI `10.1145/2408776.2408794` | B，跨场景迁移为 C：本轮已取得并读作者 PDF 正文 pp.74–80，SHA256 `fffb9132a10c001692f3017ad2ef6fd872a96851638554497ebd23dfbefcc0da`。区分单次请求的立即适应与跨请求长期适应；讨论延迟启动备用、取消重复工作及 latency-induced probation；强调竞速仅在拖慢因素不同时影响副本时有效。 | 失败记忆不能代替首次快速恢复；只看均值或少量成功快路径会遗漏长尾。音频的多条路径共用 CPU/HAL，且输出有不可重复的声音副作用，不具备论文副本独立假设，因此不以论文授权无条件三路竞速。旧研究“仅读摘要”限制由本轮补读解除，论文中的网络数值不转成音频阈值。 |
| [AWS Builders' Library：超时、重试和抖动回退](https://aws.amazon.com/cn/builders-library/timeouts-retries-and-backoff-with-jitter/)，Marc Brooker，访问日全文 | B/C：从可接受误报率和健康延迟分布选超时；端到端超时必须覆盖实际工作；多层独立重试会放大负载，最高层重试又会浪费已做工作。文章也明确警告断路器的模态行为与额外测试成本。 | 一个播放 attempt 共用恢复预算和恢复 owner；不能每换一条音频路径重新拿 10 秒，也不能音频/engine/网盘各自盲重试。借鉴预算与分层归因，不为单机音频恢复增加指数退避、随机延迟或后台联网。 |
| [Envoy outlier detection](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/outlier)，访问日官方页面 | A/B，音频类比为 C：区分请求本身的错误与本地链路错误；有界暂时摘除/恢复；共享实例的失败状态会影响其它调用方；只通过浅层 active health check 可能错误地提前恢复流量。 | 媒体解码/内容错误保留媒体级隔离；只有充分的输出层证据才能提升为 route+format 经验。重新查询“支持”不能清除已实证的失败；恢复资格须由实播重新验证。 |
| [NuvioTV PR #3107](https://github.com/NuvioMedia/NuvioTV/pull/3107)，读取 PR 正文及 diff 快照，SHA256 `c1ac441f93f640ad1303828131f34184aa3e41b6e8d27985a4167ece7c4bc066` | C/D：访问时为 DRAFT；报告大压缩 buffer 与某些 IEC AudioTrack 创建耗时，提出按格式 buffer 上限、后台 probe、内容时间 pacing 等多个改变。 | 仅作为现场线索，不作为成熟修复或合并候选。它排除 AAC 的 pacing，涉及不同 HDMI 格式/模式；不能复制 200 ms、buffer 上限、时钟钳制或后台真建轨来修 Sony。按引用快照保留，不把未固定最终 commit 的草稿当成可移植基线。 |

启动门槛还交叉核对了 `android-12.0.0_r1` 的 [native AudioTrack::setStartThresholdInFrames](https://android.googlesource.com/platform/frameworks/av/+/android-12.0.0_r1/media/libaudioclient/AudioTrack.cpp) 和 [AudioFlinger Track::isReady](https://android.googlesource.com/platform/frameworks/av/+/android-12.0.0_r1/services/audioflinger/Tracks.cpp)：setter 调用共享 proxy 并在降低门槛时处理 disabled 状态；服务端按 `min(startThreshold, bufferSize)` 再钳制到实际 capacity 判断填充。这里没有在 setter 处禁止 compressed/direct 的分支。这证明它不同于仅 PCM 可用的 buffer-size setter，但不证明 Sony HAL 无额外缓冲/锁定延迟；只改 API 门槛不能冒称必然有声。

本轮普通 Bing RSS 返回与查询不相干的结果，未用作证据；Google 搜索页没有可用结果；GitHub 部分 core API 达到匿名限额。已改用官方 Gitiles、原始固定源码、项目文档、GitHub issue/PR 页面与作者 PDF。未获取的页面不算已审阅。旧 Media3 #2258/#3122/#3269 与 mpv/VLC/Kodi 音频输出证据继续适用；没有证据支持用跨 period 实验开关、任意 ADTS 包装或独立视频时钟解决本例。

### 推荐的完整方案

1. **用户开关先于能力与性能偏好。** 关闭直通时压缩 passthrough、标准 compressed offload 和自定义 vendor-direct 都不准入；选现有 decoder 输出 PCM。开启时保留正常直通/硬件/DSP 候选和用户软解偏好。开关不改视频选择，不在播放中额外造第二个音频输出。
2. **先形成候选，再按实际输出提交选择。** 轻量的格式解析、已有能力/经验查询可提前复用；不让常见 AAC 起播等待与它无关的 HDMI 格式探测。一个候选区分“初始化成功、收到样本、满足起播条件、首次实际推进”，最后一项才代表输出链可用。明确初始化/写入/解码错误立即进入下一可用候选，不等全局 stuck 异常。
3. **把填充与运行分开，并让填充本身有设计目标。** 读取实际 capacity/start threshold、累计完整 AU 的供数和时间戳，区分没数据与有数据不走。API 31+ 对受测 vendor 路径可评估设置较小的 start threshold，同时保留稳态容量；读回实际值，发生路由变化重建门槛证据。普通 AAC 的候选起播数据窗可从约 100–250 ms 的完整 AU 开始设备对照，这是实验范围而非已经安全的全局参数。短片/EOS 使用明确结束语义，不为凑阈值多读几秒或补造静音。
4. **首次恢复使用短而有条件的预算。** 对本实例、本路由、play 意图有效、无 pause/buffering/seek/EOS、实际供数已足够且应消费的音频，观察 raw head/有效 position 的推进；无进度时投递一次带 attempt/output 身份的恢复事件，在接收线程再次验证。短窗由健康设备冷启动/恢复分布和可接受误报率裁决；建议以 300–800 ms 做故障注入对照、把判断预算控制在 1 秒内。不是把所有 timeout 改成该数值，也不能把未知门槛当作已填满。新起播会产生新 budget，失败候选切换不重置同一 attempt 的总预算。
5. **恢复尽量不丢已经读到的内容。** 首次成功输出前，只为有风险的 vendor 尝试有界保留压缩 AU 及其时间戳/配置；失败时释放旧输出，向同音轨备用 decoder 重放，视频 renderer 与 media source 保持。确认输出后立即释放保留数据；DRM、seek/flush、切轨、异步回调和队列水位有独立世代，不重复播放声音。先验证发货 Media3 是否能通过现有 renderer/recoverable-error 接口完成；不能就把局部 sink/renderer 扩展作为明确的新阶段，而不冒称 capability invalidation 已实现。**若全媒体重建仍花 3–9 秒，整体速度验收不通过；C 或等效避免重建的方案不再是最终目标的可忽略项。**
6. **分层记忆故障，让新视频也受益。** A 的 exact-media 记录继续用于内容相关或证据不足的失败；另建议 route+模式+编码/profile+采样率+声道+有效属性+firmware/policy version 的短期经验。可重复的输出初始化/模式拒绝可归输出层；零 head 这类存在内容歧义的失败，至少需不同媒体的同配置失败、各自 PCM 成功，才能提升范围。这个“至少两媒体”只是保守候选规则，未标作统计证明。未知路由不合并，不按 Sony 品牌禁用 AAC，不仅去掉 URL key 就扩大旧缓存。过期后只允许下一次自然起播一次试探，健康播放中不切回，不永久拉黑。
7. **一个恢复控制点与准确归因。** 音频输出失败、codec 失败、无视频关键帧/无视频输出、网络供数不足各有证据，避免错误地重试网盘或切视频软解。视频候选只在用户既定解码模式内处理已有允许的回退；优化本音频问题不扩大视频自动软解。10 秒 Media3 全局检测可以保留作未覆盖故障的最后告警/恢复，但受支持的正常起播和常见坏 vendor 路径不能依赖它。

### 备选、产品预算与阶段

| 方案 | 对首次错误路径的效果与代价 | 决定 |
| --- | --- | --- |
| 不改 / 只保留 A | 首次仍等 10 秒，换媒体/路由不明可能每次重复；不满足目标。 | 拒绝作为任务完成状态。 |
| 完全退回上游标准路径 | 避免自定义 vendor 风险，但一概移除原 E11 的可用 DSP 路径。 | 不全局采用；关闭用户开关时正应选择标准 PCM 能力。 |
| 所有 AAC 先软解，稍后自动换直出 | 可能快速首播，但改变健康设备功耗/既有偏好，并增加一次运行中切换。 | 不作默认；不把临时规避冒充保留功能的最终方案。 |
| 只把 10 秒改成 2 秒 | 仍受实际填充/重建成本影响，误判可能制造更多重建。 | 不单独交付为最终优化。 |
| 默认并发直通/硬解/软解 | 可能减少独立初始化尾延迟，但共享 HAL、双声音副作用、解码资源和取消复杂度不符合当前性能要求。 | 不默认启用；先准备候选元数据和失败后的输入可重放性。 |
| 受控起播门槛 + 快速实播确认 + 同轨局部恢复 + 分层经验 | 同时处理首次、切换、下一新视频；需要验证 underrun/资源与样本/时钟连续性。 | 推荐，按下面最小单元推进。 |

**建议产品验收目标（尚无候选实测，不是已达成或跨设备保证）**：在音视频所需样本已可读取的受控场景，健康路径无新增人为等待；已供数的坏音频路径在 1 秒内决定恢复，判断与切换到真实稳定推进合计以 **不超过 2 秒** 为目标。两次候选失败共用这份预算，不能各自获得 2/10 秒。网盘点击到播放另报全量时间，网络等待不能从用户体验指标中删除；同时以分段日志识别是否由 App 额外造成。局部恢复暂未实现时，应明确报告离 2 秒目标还有多少，不能只统计 detector。

最小实施顺序：

- **本轮追加开关修复，已明确授权**：仅 `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`、`ExoCompressedAudioDirectPolicy.java`、对应 `app/src/test/java/com/fongmi/android/tv/player/exo/ExoCompressedAudioDirectPolicyTest.java`、本文/索引；独立 quick-fix guard。关闭时在 track selector、offload support、format/output config 和真实输出创建处一致拒绝 encoded 输出，继续用原包装记录 PCM 输出所有权；开启时原能力/失败保护不变。不改默认值、设置 UI、native、网络或视频。
- **B 已授权方向**：带真实供数/身份/生命周期门控的提前恢复，可先做故障注入和实际 policy/provider 测试；数值必须用 healthy cold-start 与 Sony 验证，不能因暂缺设备就把 10 秒设计成最终默认体验。单独缩时只是过渡候选，不满足总目标即继续推进。
- **启动门槛调整、跨媒体经验与 C**：本节完成推荐与约束；改变默认尝试范围、共享健康范围或实际 renderer/依赖所有权时，需按具体最小设计另行实施批准。当前用户追加只要求开关修复，不把这些新架构改动混进修复。

### 最便宜的决定性验证与回滚

开关修复：真实 policy/provider 定向测试证明关闭时 vendor 查询/建轨零调用、offload 不支持、标准 encoded passthrough 同样被拒绝、缓存的 encoded config 不能绕过、PCM 及 tunneled PCM 正常委托且输出快照仍准确、重新开启可选正常 direct；现有失败记忆/生命周期用例继续通过。一次受影响 TV32 主机测试和 Debug 构建，保护已有 native 暂存；无设备时不伪称实播通过。

后续 B/C：可控 fake clock/输出重现足量供数但 head=0、刚好低于/达到门槛、慢供数/未写满、head 先走后停、未知 position、pause/resume、seek/flush、EOS、路由变更、退出后迟到通知；验证恢复只一次、旧样本不跨 attempt。局部恢复进一步核验 media prepare 次数/HTTP 新请求/视频 decoder 初始化均不因音频切换增加，重放时间戳不丢不重、AV sync 不劣化。设备对照至少覆盖 Sony AAC、已知健康 vivo vendor、一条健康 HDMI 多声道；冷/暖首次与不同新视频分开，基线/候选交替并保留全部样本，少量样本只报中位数/最差值，不伪报稳定 p99。

正常路线的 CPU/内存/功耗、AudioTrack/codec 数量、网络读取、underrun、丢帧、A/V 偏差不能出现可归因于改动的回退。API 31 的 setter 可用并不等于 HAL 兼容/低门槛性能已证明；未知/未测路径保留现行行为直至有证据的独立候选，而不能声称最终任务已经完成。开关修复与后续每个行为独立 commit/tag/revert；无新 ABI/包依赖、不持久化 URL/音轨内容、不新增长期轮询/双 decoder/网络流。本轮不推送或发布。

## 历史 Recovery anchor（2026-09-21，起播优化实施）

- Objective / acceptance：实施获批 A 的直出准入一致、已确认失败配置的短期记忆和准确错误分类；保留正常 DSP、标准 offload/HDMI、用户偏好、音画同步、隧道、视频解码及已有失败回退。B 只在真实启动门槛和状态证据满足后推进，C 暂缓。
- User decision：恢复后用户明确“继续实施”；不再重复请求同一阶段批准。
- Lane / guard：`standard` / `E11-audio-startup-admission`。
- Branch / baseline：`main` / `8d7ccf42b449db6ae12eb04a38f95196ef29c737`；研究已提交并创建 `recovery/E11-audio-startup-research/20260921140026-8d7ccf42b449`。
- Scope：`app/src/main/java/com/fongmi/android/tv/player/` 下 `exo/ExoCompressedAudioDirectPolicy.java`、`exo/ExoAudioDirectFailureMemory.java`、必要时 `exo/ExoAudioOutputState.java`、`engine/ExoPlayerEngine.java`、`PlaybackErrorClassifier.java`；对应四个测试类与本文/主索引。保护预存 `app/.cxx/` 104 个文件。
- Completed evidence：恢复与研究文档校验通过，0 error/0 warning；实际发货 Media3 输出提供 `getAudioTrack()`，可读取真实启动门槛；API 33 可按属性查询预期设备，旧 API 仅在唯一输出设备且与实播一致时允许经验命中。多路/未知路由保守不命中。
- Implementation status：A 的代码和主机验证已完成；仅确认 vendor 失败且同媒体/同输入音轨/同输出路由的 PCM 连续推进后，才写入最多 32 项、10 分钟的进程内记录。媒体完整 URL 与音轨初始化数据仅保留摘要；系统设备连接通知和能力变化使经验失效；旧输出与新 attempt 隔离。B 未开启，C 暂缓。
- Verification：64 项定向用例通过；随后公共错误分类收窄以保留 IJK 通用超时合同，其 10 项用例重测通过，其余 54 项对应代码未再修改。Leanback armeabi-v7a Debug APK、ZIP CRC、目标 ABI 和 v2 签名验证通过，最终 SHA-256 `4093aeff27e55101e6ba199a78e77fd5d1c4bc608aa8f89aeac7939131b0c4f9`。本机 `adb devices -l` 无设备；Sony 和健康 direct/HDMI 的实播、速度、性能尚未验收。
- Rollback：A 作为独立代码/测试/文档原子提交，可 revert；不修改依赖、native、ABI 或打包配置。
- Next action：用本节最终 APK 在目标 Sony 与健康直出设备做同片对照，采集真实 capacity/start threshold、首次推进及恢复日志，再裁决 B 的短观察窗；不需要重复批准已授权的 A/B 范围。

## 2026-09-21 阶段 A：准入一致与已确认失败记忆

### 最终行为与边界

- `ExoCompressedAudioDirectPolicy.effectiveAttributes/supportsBitstream`：保留既有 UNKNOWN → MUSIC 的 vivo 兼容处理，但在能力查询、决策 key、OutputConfig 和建轨之前统一执行；API 33+ 的 non-offload vendor 路径必须包含 BITSTREAM 支持位。API 29–32 保留官方布尔查询。标准 offload、隧道与原始 AAC/MP3 格式白名单保持。
- `ExoAudioDirectFailureMemory`：只存完整媒体 URL、输入音轨 ID/编码/profile/采样率/声道/CSD 的摘要、有效音频属性、实际设备端口及能力标识；不存 URL/token/CSD 明文，不落盘。容量 32 项、确认起 10 分钟有效，读取不续期；设备/能力变化使世代失效。
- 只有本实例 vendor 输出有真实停滞或写入失败证据，显式 PCM 重试仍是同一媒体，选中输入音轨一致，PCM 在至少 2 秒的连续观察中推进至少 1 秒，且实际与预期输出设备均和故障时相同，才确认共享经验。未成功恢复、零供数、暂停、跳转/flush、输出释放/替换、不同音轨/媒体及迟到回调均不会形成新经验。
- 能力查询处命中经验时只跳过相应 non-offload vendor 配置，交给现有解码候选输出 PCM；健康配置不初始化备用解码器、不复制音频、不新增网络流或定时轮询。确认 PCM 的包装只用于明确的恢复过程，确认结束后不继续采集进度。
- `AudioManager.getAudioDevicesForAttributes()` 在 API 33+ 提供当前预期路由；API 29–32 只接受唯一输出设备。多路、未知路由、非 NORMAL 音频模式或无法订阅设备变化时不共享经验，保留原引擎内失败保护与原 10 秒 stuck 回退。这意味着本机测试不能证明反馈 Sony 必然命中跨 engine 记忆。
- 系统 `AudioDeviceCallback` 在第一次故障/经验查询时才注册，使用现有主 Looper，进程内只有一个观察者，不持有 engine/Activity。初始已连接设备通知与真正增删分开；已有 provider 的能力变化通知继续使经验失效。AOSP Android 13 `0d3ff311e6e80dee7fe88a2a2cfa272ce231c3c6` 的 `registerAudioDeviceCallback/broadcastDeviceListChange_sync/NativeEventHandlerDelegate` 是此行为的 A 级源码依据，访问 2026-09-21，沿用研究证据目录。
- `ExoPlayerEngine` 仅在明确音频重试时传递待确认故障，结合当前输入音轨验证恢复；其它 prepare、stop、rebuild 和 release 隔离旧 attempt。故障提交和新播放启动串行化，PCM 最终确认重新核对输出身份，避免跨线程迟到状态污染新播放。
- `PlaybackErrorClassifier` 将类型明确的 `StuckPlayerException` 标成 `playback-stuck`，renderer 归属仍 UNKNOWN；音频归因由实际 vendor 输出证据决定。收尾核对发现 `IjkErrorMappingPolicy` 也用 `ERROR_CODE_TIMEOUT` 表示原生 timed-out，因此保留未类型化超时的既有分类和 IJK 恢复合同，没有扩大到 IJK 的错误模型改造；MPV 稳定 marker 与明确网络/解析/DRM 分类保持。
- 真实 AudioTrack 创建处新增 capacity、buffer size、start threshold 和有效门槛的单次日志；压缩输出的单位为 byte。首次写入、首次推进和 PCM 确认各记录一次，日志关闭时不格式化字符串。没有改变实际 buffer、音频数据封装或音画时钟。

### 已完成验证

临时构建/证据目录：`/private/tmp/webhtv-E11-admission-c18ee9ju/`。host init 仅选择任务内测试源码并启用 Android mock 默认值，原生构建暂存指向该目录的 `cxx`；未修改正式 Gradle 配置，也未重建播放器依赖或其它 ABI。

| 测试类 | 最终用例数 | 覆盖 |
| --- | ---: | --- |
| `ExoCompressedAudioDirectPolicyTest` | 41 | 标准 offload/隧道、模式位/属性匹配、真实输出包装、已供数但停滞、正常进度、未知位置、暂停/flush/stop/release、输出/attempt 隔离、不同媒体/音轨/路由、PCM 成功才共享、最终路由查询期间开始新播放 |
| `ExoAudioDirectFailureMemoryTest` | 6 | TTL 不续期、容量淘汰、路由世代/迟到确认、未知身份拒绝、媒体/路由隔离、摘要与音轨/CSD 区分 |
| `ExoAudioOutputStateTest` | 7 | 已有实例归属与交错释放合同 |
| `PlaybackErrorClassifierTest` | 10 | typed stuck 与真实网络区分、既有 IJK 通用超时语义、MPV marker、解析/输出/DRM 与脱敏 |

- 首轮 62 项通过，Gradle 用时 2 分 29 秒；补齐同能力设备增删与交错确认边界后，64 项全部通过，并完成电视 32 位 Debug 打包，用时 2 分 57 秒。最终保留 IJK 合同只修改错误分类及其测试，重跑该类 10 项并刷新同一 APK，53 秒通过；未重复其余已通过的 54 项检查。
- 实际构建均用 `bash gradlew -I /private/tmp/webhtv-E11-admission-c18ee9ju/host-tests.init.gradle :app:testLeanbackArmeabi_v7aDebugUnitTest`；最终两次按完整测试类名加 `--tests`，并构建 `:app:assembleLeanbackArmeabi_v7aDebug`。完整输出依次为 `gradle-verified.log`、`gradle-final.log`、`gradle-classifier-final.log`。最初沙箱调用只因用户 Gradle 缓存锁权限失败，授权后才执行实际构建。
- 最终 APK：`app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`，162,518,123 bytes；SHA-256 `4093aeff27e55101e6ba199a78e77fd5d1c4bc608aa8f89aeac7939131b0c4f9`。包名 `com.fongmi.android.tv`，versionCode 560 / versionName 5.6.0，native ABI 仅 `armeabi-v7a`；ZIP 全文件 CRC 与 APK v2 签名通过。签名/manifest 记录为临时目录的 `apk-signature.txt`、`apk-badging.txt`。
- APK 在本次代码提交之前构建，因此调试日志中的 Git 基线是 `8d7ccf42b449db6ae12eb04a38f95196ef29c737` / dirty；以最终 SHA-256 识别候选，不把旧 APK `912276b40ca353704943451eab6ae8ddc33e1a5784193426ffd58f89af3961cf` 当作最终版本。最后一次构建后仅更新任务文档，没有再改生产代码。

### 尚未通过的设备门槛与交付

`adb devices -l` 没有连接设备，已在工作期间询问目标设备可用性，尚无设备响应。本轮没有安装或实测，不能声称 Sony 起播已缩短、健康 DSP/HDMI 实播已验收或性能已证明不下降。特别是 API 31 的多输出环境可能不命中共享经验，首次播放依然保留原 10 秒检测。

B 的早回退没有启用：旧日志只有请求的 256 KiB 和总写入量，没有实际启动门槛、路由变更后的门槛以及健康直出的启动分布。先用本次 APK 采集这些证据，按前述同片对照裁决观察窗；不把 2 秒建议值直接推广到生产。C 音频局部重建仍暂缓。此限制来自实际证据缺口及用户要求保留功能/性能，不是等待重复的实施批准。

A 的源码、测试、本文和索引按 `E11-audio-startup-admission` guard 原子提交并创建 annotated recovery tag；具体提交/tag 由 Git 记录确定，回滚 revert 该单提交。保留原有 104 个 `app/.cxx/` 文件，不推送或发布。

## 历史 Recovery anchor（2026-09-21，起播速度与成功率研究）

- Objective / acceptance：为 AAC/MP3 厂商直出形成兼顾起播时间与成功率的决策方案；保留正常直出、标准 offload/HDMI passthrough、音画同步、用户偏好、视频手动解码和既有格式能力。研究须覆盖平台合同、实际发货源码、上游问题、成熟播放器与相关性能实践，并明确实施阶段、验收和回滚。
- User decision：研究方案形成后，用户于 2026-09-21 明确要求“继续实施”，授权推荐的准入一致、确认失败后有限记忆及具备安全门槛的较早恢复；保留“当前功能不被破坏、性能不会降低”的验收条件。先实施 A；B 仍须满足真实启动门槛与输出状态证据；C 不进入本轮生产变更。
- Lane / guard：`assessment` / `E11-audio-startup-research`；只修改本文和主评估索引，临时证据 `/private/tmp/webhtv-E11-startup-research.o62jzZ/`。
- Branch / baseline：`main` / `fa09d226f9f3763bc0f50167d6485178438fad52`；保护原有 `app/.cxx/` 下 104 个未跟踪文件。
- Recovery reconciliation：2026-09-20 回退修复已提交为 `f836d419518d1a93d4ff77414a88558f3854c7e3`；2026-09-18 所有权/隧道修复为 `ee216d8ba7dee9637e9b79478d819532c846691c`。下方旧 anchor 的待提交措辞仅保留为历史，不是当前下一步。
- Completed evidence：用户 2026-09-21 第二份日志含 7 次同片播放；5 次 AAC vendor-direct 的音频 head 均为 0，4 次在 10 秒无进度后回退 PCM，1 次在超时前释放；2 次 FFmpeg 音频软解可正常推进。回退已经提高成功率，但首帧至音频推进仍需约 13.3–19.3 秒。
- Plan status：平台合同、发货源码、上游问题、mpv/VLC/Kodi 和跨领域延迟实践已复核；下节记录推荐方案、拒绝项及分阶段验收。仅本文和索引有研究记录修改，尚无生产代码改动；本轮只执行文档校验，不能记为设备性能验证。
- Risk / limit：应用日志不能区分平台直出实现、输出封装与播放位置反馈的底层责任；查询支持不等于目标 AudioTrack 模式实播成功。速度目标待候选包与相同设备对照验证。
- Rollback：本轮仅文档，可整体 revert 本研究提交；后续实施需另开 guard 和独立原子提交，不改现有锁/ABI/native 制品。
- Next action：校验并关闭恢复的研究 guard，随后为已获批的阶段 A 启动实现 guard；研究提交和 recovery tag 以本次 guard 的 Git 记录为准，不为回填 ID 另开文档提交。

## 2026-09-21 起播速度与成功率：证据、决定与实施边界

### 问题与实际能力

本轮决策问题：如何缩短厂商 AAC/MP3 压缩直出没有播放进度时的等待，同时保留能正常工作的 DSP、标准 offload、HDMI 直通、手动解码及音画同步。推荐补充 WebHTV 的实播健康判断和有限故障记忆，继续使用 Media3 的解码候选和同步机制。没有推荐升级依赖、移植其它播放器的时钟或默认并发运行多个音频输出。

当前任务为既有 E11 的 Exo/App 优化；不是另一项上游合并任务，也不更改 E11 的原始 DSP 能力目标。研究开始于 2026-09-21 11:12 Asia/Shanghai，目标约 11:37 完成（日志/调用链约 8 分钟，外部裁决约 10 分钟，文档/校验/收尾约 7 分钟）。本轮允许路径仅本文和主评估索引；预存 app/.cxx/ 104 个文件受保护。最便宜的决定性验证为源码合同对照及一次文档校验，无需构建 APK。

### 日志事实与因果边界

来源：用户提供的 `webhtv-debug-log (3) (3).txt` 和 `webhtv-debug-log (4) (3).txt`，均在 `/Users/macbookpro/Downloads/`。第二份包含前面的部分记录及后续会话；按 `(processRunId, seq)` / `logSeq` 去重，按事件时间还原，不能把置顶片段的文本位置当作发生顺序。设备为 Sony BRAVIA 4K VH2 / Android 12 / API 31 / armeabi-v7a，片源为同一 Wogg/Quark MP4，AAC-LC 44.1 kHz 双声道。

| 播放 trace | 实际音频路径 | 首帧到音频时钟开始推进 |
| --- | --- | --- |
| `p-d8zas-q` | vendor-direct → 平台软件 AAC → PCM | 13.298 秒 |
| `p-dgqaa-r` | vendor-direct → 平台软件 AAC → PCM | 13.958 秒 |
| `p-doclo-s` | FFmpeg → PCM | 0.292 秒 |
| `p-dptk9-t` | FFmpeg → PCM | 0.364 秒 |
| `p-dqqzo-u` | vendor-direct，超时前退出 | 未观察到推进，不计入成功时延 |
| `p-drxix-v` | vendor-direct → PCM | 19.349 秒，其中恢复重新缓冲约 8.53 秒 |
| `p-du2hp-w` | vendor-direct → 平台软件 AAC → PCM | 13.643 秒 |

五次 direct 都是写入已接受但 raw playback head 为 0；四次出现 `StuckPlayerException: Player stuck playing with no progress for 10000 ms` 后回退，一次提前退出。两次 FFmpeg 解码器初始化为 51/72 ms；回退的平台 `c2.android.aac.decoder` 为 146–213 ms。这不是仅仅等 UI 更新“软解”标签，而是真实输出路径在等待后发生了切换。这里的时间是首帧到日志中的音频推进，不是点击到首帧，也不能当作实测扬声器发声时刻。

最后完整会话：10:38:08.829 创建 AAC encoded AudioTrack（encoding=10，offload=false），08.998 首帧；19.210 媒体位置 71.772 s、buffered position 94.714 s，约有 22.942 s 数据在前，下载速率约 4.82–5.05 MiB/s；约 734,864 音频 bytes 已接受、writeErrors=0、head=0。19.232 无进度异常；21.862 软件 AAC 初始化（171 ms）；22.127 PCM Track；22.641 音频推进。后续 PCM 连续推进约 95 秒。

因此，反复出现的 10 秒静帧主要与厂商直出/输出时钟链路不前进吻合，不能用“单纯下载慢”解释；恢复时的重新取数仍可能受网络影响。日志不能证明设备整体不支持 AAC，也不能在 HAL 解码、原始帧封装、输出模式/属性、启动填充和位置反馈之间最终定责。Android 的声明是格式/模式能力查询，不是每个时刻、每种参数组合的实播保证，不能据此称厂商“虚假声明”。

特别注意：现有 256 KiB 是请求的 buffer size，不是已读出的真实 AudioTrack capacity/start threshold。即使接受的总字节数更大，也不能用它替代未采集的 HAL 启动门槛或设备侧声音证据。暂不添加 ADTS，也不凭本日志调整所有 direct buffer。

### 本地调用链与已经具备的能力

基线 `fa09d226f9f3763bc0f50167d6485178438fad52`；实际发货 Media3 源锁 `e3e922d5c01bc0b564849940fe589daf37360d15`，版本 `1.11.0-alpha01-fongmi`。读取的是仓库内 sources.jar，SHA256 为 `4d158d63ab0a99688880d6acfdc73ed340f09fa9ac9dd1934fbaa0babfcad086`，不是拿互联网最新版替代运行实现。

| 位置/符号 | 已有实现、缺口与决定 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ExoCompressedAudioDirectPolicy.java`：`getFormatSupport`、`platformSupportsDirectPlayback`、`createVendorDirectAudioOutput` | 标准 offload/隧道已有保护。API 33+ 把模式位 `!=0` 当成普通 direct；查询使用原始属性，创建时把 UNKNOWN 内容类型变为 MUSIC。应先让查询、决策缓存及实际建轨采用一致的有效属性，并保留模式位。Sony API 31 没有这些细分位，不能声称单改位判断就能修复它。 |
| 同文件：`VendorDirectAudioOutput`、`requestPcmFallbackForStuckPlayback` | 已按实际输出观察写入和位置，2 秒证据窗只供 10 秒 Media3 异常确认；failed set 只活在单个 engine。应保留输出/attempt 隔离，补齐启动门槛、确认后记忆和较早恢复。 |
| `engine/ExoPlayerEngine.java`：`handleError`、`retryAudioOutputWithPcm`、`startInternal` | 上轮已能成功回退。当前通过重新 prepare 当前媒体项恢复，会停止预缓存并重建播放准备过程；额外 3–9 秒不能全归为音频解码慢。 |
| `exo/ExoUtil.java`：`buildAudioSink`、`FfmpegRenderersFactory`；Media3 `MediaCodecRenderer.maybeInitCodecWithFallback` | 已有硬件/平台软件/FFmpeg 候选及 decoder fallback。候选查询和真正实例化是两回事；选中路径后初始化失败再试下一个，不需要默认把所有 codec 同时初始化。保留音频偏好及视频手动选择。 |
| `exo/ExoAudioOutputState.java` | 输出状态按真实实例归属，旧实例 release 不能清新状态。跨 engine 的经验缓存只能存已确认结果，不能替代当前输出所有权。 |
| `player/PlaybackErrorClassifier.java`：`exactStage` | 通用 `ERROR_CODE_TIMEOUT` 被标成 NETWORK_IO；当前日志中的 typed stuck 并非网络证据。将来应先按类型区分；单独 StuckPlayerException 也不一定证明是音频，只有结合本实例证据才称音频输出停滞。其它真正网络错误保持原分类。 |
| `engine/PlaySpec.java`：`getKey`、`getUrl` | 有 key 和 URL，但仅凭字段名不能认定 key 是稳定的单集资源 ID。首版故障记忆可用完整 URL 的进程内摘要作保守媒体隔离；签名变化造成未命中可接受，不擅自去掉查询参数归并不同资源。 |

音频和视频有各自的样本队列和解码器，设备可以并行执行它们；播放器的 render 调度和同步时间轴仍共同协调。Media3 音频 renderer 提供主媒体时钟，视频按它决定帧何时显示。首帧可以先显示，音频时钟为 0 时后续画面会等待；强行让视频自由前进会把静帧换成音画错位。

`AudioOutputProvider.Listener.onFormatSupportChanged` 能把变化传到 sink/renderer/track selector，但当前 `ExoPlayerImplInternal.reselectTracksInternalAndSeek` 会附加当前位置 seek，且同轨同 renderer 的等价 selection 可能根本不重建 decoder。`AudioOutput.isStalled` 的现有 sink 处理是 flush，不代表切到 PCM；直接复用它可能再次打开同一失败路径。这两点是“只重启音频”必须证明的合同，不能只开一个开关就宣称无损恢复。

### 外部研究与来源处置

访问日期均为 **2026-09-21**，网络读取使用已配置的 `127.0.0.1:7897` 代理。原始全文、API 响应及带 URL/时间/SHA256 的元数据保存在 `/private/tmp/webhtv-E11-startup-research.o62jzZ/`；下面保留可重新访问的定位，不以临时目录存在作为唯一依据。A 为实际代码/正式合同，B 为维护者解释或可核对的案例，C 为未确认线索/跨场景推论。

| 来源与固定版本 | 等级、已读取的证据 | WebHTV 适用范围、限制与决定影响 |
| --- | --- | --- |
| [AOSP AudioTrack](https://github.com/aosp-mirror/platform_frameworks_base/blob/cebf5c06997b64f4e47a1611edb5f97044509d76/media/java/android/media/AudioTrack.java)，Android 12 `cebf5c06997b64f4e47a1611edb5f97044509d76`；`isDirectPlaybackSupported`、`getStartThresholdInFrames` | A：能力查询不保证此刻有足够资源；streaming 在 play 后仍需达到启动门槛。压缩格式在 buffer/threshold API 中一 frame 按一 byte 计，不能拿这个单位直接除 sample rate 算音频秒数。 | 创建时读取实际 capacity/threshold；不在首个写入后无条件开始 2 秒故障倒计时。路由可能改变门槛；低 API/未知门槛走保守旧兜底。 |
| [AOSP AudioManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/0d3ff311e6e80dee7fe88a2a2cfa272ce231c3c6/media/java/android/media/AudioManager.java)，Android 13 `0d3ff311e6e80dee7fe88a2a2cfa272ce231c3c6`；[官方 API](https://developer.android.com/reference/android/media/AudioManager#getDirectPlaybackSupport(android.media.AudioFormat,android.media.AudioAttributes)) | A：OFFLOAD、GAPLESS_OFFLOAD、BITSTREAM 是不同支持位；查询必须针对将用于播放的属性。 | non-offload vendor-bitstream 准入不能用任意非零位替代，标准 offload 继续委托原 provider。仅修合同，不把 API 33+ 差异当作 Sony API 31 根因。 |
| [AOSP NuPlayerDecoderPassThrough](https://android.googlesource.com/platform/frameworks/av/+/android-12.0.0_r1/media/libmediaplayerservice/nuplayer/NuPlayerDecoderPassThrough.cpp)，`android-12.0.0_r1`，`aggregateBuffer` / `fetchInputData` | A：此标准 offload 路径可以聚合 extractor access unit 后写入，并非在这里统一追加 ADTS。 | 它使用不同的标准 offload 配置，不能证明 Sony non-offload 能接受当前数据；足以否决“所有 AAC direct 都必须补 ADTS”这类无证据改动。 |
| [Media3 #2258](https://github.com/androidx/media/issues/2258)，包括 2025-05-20 至 05-27 维护者及复测评论 | B：HDMI 刷新率/热插拔后能力恢复与 PCM fallback 状态不同步；capabilities receiver 覆盖及 DecoderAudioRenderer 通知缺口已在当时提交。重选可短暂中断。 | 学习路由世代和故障状态失效；不是本轮新移植项。不永久拉黑设备，也不把 capability invalidation 当成音频局部重置证明。 |
| [Media3 #3122](https://github.com/androidx/media/issues/3122)；[提交](https://github.com/androidx/media/commit/418aaeaf395751c56be09b561699cb0bef608727) `418aaeaf395751c56be09b561699cb0bef608727` | A/B：短片循环/倍速时下个 period 的供数被视频阻塞，音频未填够而卡住；维护者否决随意切 standalone clock。提交提供实验性 per-stream media progression。 | **已有 API、此例不适用**：发货 sources.jar 已有 `enablePerStreamMediaProgression`，App 未启用；它解决跨媒体项推进，不是有大量当前项数据的厂商 AAC 输出停滞。本轮不启用、不 cherry-pick。 |
| [Media3 #3269](https://github.com/androidx/media/issues/3269)，问题及评论 | C：其它 MediaTek/Hisense API 31 设备的 AC3/EAC3 seek 后 playback head 异常，尚无确认的通用修复。 | 提醒 seek/flush/时钟 epoch 风险；触发条件、设备、格式都不同，不能推导 Sony 的根因或照搬社区实验补丁。 |
| [mpv AudioTrack](https://github.com/mpv-player/mpv/blob/e76a35ec95b27f5cf2d27b043b5e2e0d90e468ae/audio/out/ao_audiotrack.c)，`e76a35ec95b27f5cf2d27b043b5e2e0d90e468ae` | A：分离 PCM/IEC 输出，处理时间戳/播放头 wrap 与 flush，DEAD_OBJECT 可局部重建 AudioTrack。 | 借鉴实际输出状态和有类型恢复；不是同样的 AAC vendor-direct，不能把其线程/时钟算法复制进 Media3。引用，不集成。 |
| [VLC AudioTrack](https://github.com/videolan/vlc/blob/5751b4a48706db62f74f6c5fffcdc66ca7b9f3ea/modules/audio_output/android/audiotrack.c)，`5751b4a48706db62f74f6c5fffcdc66ca7b9f3ea`；`StartPassthrough`、`AudioTrack_Write`、`AudioTrack_ReportTiming` | A：IEC61937 → raw 兼容模式按优先级逐个尝试；写入成功与有效非零 timing 分开；DEAD_OBJECT 尝试重建。旧 Android 阻塞 workaround 另有条件。 | 支持“有序尝试、按实播确认、只恢复失败层”的原则，不支持默认三路同时占用输出。旧系统 workaround 不适用于直接照搬。引用，不集成。 |
| [Kodi AudioTrack](https://github.com/xbmc/xbmc/blob/a2468936c07367799cd82957b858fda35b7e5e58/xbmc/cores/AudioEngine/Sinks/AESinkAUDIOTRACK.cpp)，`a2468936c07367799cd82957b858fda35b7e5e58`；`GetDelay`、`AddPackets` | A：区分 PCM/raw/IEC；部分模式按原始 buffer 时长监督播放头，达到条件请求 reopen。明确有 sink 需要额外数据才启动。 | 支持把 buffer 和状态纳入监督；其 watchdog 排除 raw passthrough，不能复制其 400 ms 或两倍 buffer 常量用来判断 AAC。引用，不集成。 |
| [RFC 8305](https://www.rfc-editor.org/rfc/rfc8305.html)，2017-12，§4–5 | A 规范，跨领域应用为 C：按历史和优先级排序候选、分批延迟尝试、成功后取消其它连接，控制额外负荷。 | 只借鉴有界尝试和失败经验；音频输出有共享硬件/时钟及不可重复消费样本，不能照搬网络竞速或时间参数。 |
| [The Tail at Scale](https://research.google/pubs/the-tail-at-scale/)，Dean/Barroso，2013 | C：仅读 Google 摘要，全文镜像 404、CACM 页面 403；未取得并审阅论文全文。 | 不将摘要当作并发解码或具体期限的依据，也不宣称完成论文全文评估。本题的平台/播放器事实与已读 RFC 足以决定是否默认竞速，全文不构成本方案的必需门槛。 |

证据类别：精确源码/相关提交、正式平台合同、issues/维护者讨论、成熟播放器实际实现均已覆盖；本轮没有移植候选，因此没有要重建的上游测试矩阵。性能/现场报告以用户七次会话及公开 issue 的复现/维护者反馈为依据，不能当作统一基准。未找到直接验证这条 Sony AAC non-offload 链路的论文或受控 benchmark；不制造这种证据。对每个引用的不同模式保留适用限制，研究结论是设计选择，不是性能已经达标。

本地相关完整提交处置：`3fdf9f82f37843699a2545ed97d4a2dd17b8ead5`（已有 vendor-direct 能力，保留）、`cf0a5dabc77fb2bbdc3e0f2cc867a9eac86060b8`（已有硬件优先，保留）、`ee216d8ba7dee9637e9b79478d819532c846691c`（已有实例所有权/隧道修复，保留）、`f836d419518d1a93d4ff77414a88558f3854c7e3`（已有 10 秒后 PCM 回退，保留为兜底并补充）。上表 mpv/VLC/Kodi/AOSP 修订仅作固定引用，均不合并；唯一具体评估的 Media3 新特性提交已给出“API 已有、当前问题不适用”处置。

### 方案比较

| 方案 | 速度、成功率和当前能力影响 | 决定 |
| --- | --- | --- |
| 不改 | 四次恢复证明可播，但首次失败要等 10 秒，新 engine 又重新踩同一路径。 | 不推荐。 |
| 只使用标准 Media3 行为，移除 vendor-direct | 标准 decoder/offload/HDMI 的合同简单；但会丢掉原 E11 在 vivo 等设备验证过的 DSP 入口，也没有自动解决上层重建成本。 | 拒绝整体替换，标准路径作为正常委托和恢复基础。 |
| 所有 AAC 强制 FFmpeg/按 Sony 品牌拉黑 | 可绕过样例问题，但改变正常 DSP、功耗和既有偏好；日志不能支持这种范围。 | 拒绝全局默认。用户已有的手动软解可作为临时规避方式。 |
| 直通、硬解、软解同时运行，最快者胜 | 增加 codec/HAL 争用、内存/CPU、样本副本和取消时序；优先级选中一个“声明支持”路径也不能证明其音频时钟会走。 | 不作为默认方案。轻量能力查询/候选元数据可复用缓存，真正初始化、解码与输出仍保持有界。 |
| 所有超时改成 1–2 秒或把视频切系统时钟 | 可能误判填充、网络、暂停/seek、HDMI 变化；自由视频时钟可能损害 A/V sync。 | 拒绝。 |
| WebHTV 窄适配：查询/建轨一致，确认失败后有限记忆，满足数据及生命周期条件时较早回退 | 正常路径沿用原能力和优先级；失败路径减少无效尝试和等待。需控制误判与记忆范围。 | **推荐，分 A/B 两个可回滚单元实施。** |
| 完整音频局部重建、共享保留现有媒体队列 | 理论上可减少剩余重新 prepare 成本，但标准能力重选可能 seek 或无动作，存在样本丢失/重复、旧回调及同步风险。 | C 阶段条件研究；不阻塞 A/B，也不提前承诺效果。 |

### 推荐设计及最小阶段

**阶段 A：先阻止已确认失败的重复尝试，纠正准入与诊断。建议实施。**

1. 为 vendor-direct 计算一次有效 AudioAttributes，查询、决策 key、OutputConfig 与建轨用同一结果；保留现有 UNKNOWN → MUSIC 的兼容意图。API 33+ 保留具体支持位，non-offload vendor 路径需要 BITSTREAM；标准 offload 原样委托。API 29–32 的布尔查询只表示可尝试，不能作为已验证成功。保持原格式白名单。
2. 在真实 AudioTrack 创建处读取并保存实际 buffer capacity、effective start threshold、输出模式及可用的路由身份。API 31+ 有正式启动门槛接口，异常或旧 API 不猜测成确定值。记录 play、首次接受数据、首次有效推进、回退原因及回退后推进的单次时间点；日志关闭时不做逐帧字符串格式化，不新增音频数据复制、常驻计时线程或网络取样。
3. 首版只增加**进程内、容量有界、短期的失败记忆**，建议最多 32 项、10 分钟有效期（初始设计值，非实测最优）。记录前须满足“本实例 vendor 失败，且同媒体/同输出环境下 PCM 已连续推进”，未成功恢复的不成为跨 engine 经验。新 engine 在相同媒体和配置下可跳过这一路 vendor-direct；原 engine 现有失败保护保留。
4. key 必须包含媒体身份、音频编码/profile/采样率/声道及初始化数据摘要、有效属性、输出模式、路由世代。首版可用完整媒体 URL 的摘要保守隔离；不存 URL/token 明文、不使用尚未证明唯一的 PlaySpec.key，也不去掉查询参数猜同资源。签名变化未命中仍走正常检测。路由/能力变化（HDMI、蓝牙、音频设备）即清除对应经验；无法识别实际路由就不启用跨 engine 命中。进程结束自然清空，固件/版本变化不会沿用磁盘黑名单。
5. 网络断流、用户暂停、seek/切轨/退出、无音频、其它 renderer 故障及未确认为该输出的超时不写入经验。有效期结束只在下一次自然起播重新尝试；不在正常播放中强行切回 direct。对健康 direct 不预热备用 decoder、不增加等待。真正标准 offload/HDMI 不受 vendor 故障记忆影响。
6. 在错误分类处区分 typed playback stuck 与明确的网络 IO；只有带当前 vendor 输出证据的事件才归因于音频。不要把全部 StuckPlayerException 标成音频，也不要通过错误文本猜类型。

阶段 A 拟允许的生产路径：`app/src/main/java/com/fongmi/android/tv/player/exo/ExoCompressedAudioDirectPolicy.java`、`exo/ExoAudioOutputState.java`（仅在现有实例快照需要承载新状态时）、同目录新 `ExoAudioDirectFailureMemory.java`、`engine/ExoPlayerEngine.java`、`player/PlaybackErrorClassifier.java`；每个缩写路径均相对 `app/src/main/java/com/fongmi/android/tv/player/`，其中 `player/PlaybackErrorClassifier.java` 的实际路径为该根下的 `PlaybackErrorClassifier.java`。测试限定相应 `ExoCompressedAudioDirectPolicyTest`、`ExoAudioOutputStateTest`、新 `ExoAudioDirectFailureMemoryTest`、`PlaybackErrorClassifierTest`，以及本文和索引。实施前 guard 使用真实完整路径，不按这一段的展示缩写声明目录通配范围。无锁、AAR、JNI、.so、Gradle 或 MPV/IJK 改动。

**阶段 B：缩短第一次失败的等待。建议在 A 的实际门槛证据上实施。**

- 将初次输出分成“填充中 → 可运行但待确认 → 已推进”；只有 active attempt、当前输出、play 意图有效、未暂停/缓冲/seek/释放，且已写入足够越过真实启动门槛的数据、存在应播放的未消费音频时，才累计短期无进度观察。门槛以真实单位判断，不用整体视频 buffered duration 或压缩 bytes/sampleRate 代替音频可播放量。
- 候选观察窗为满足上述条件后的 2 秒，而非首次写入后 2 秒；数值必须由健康 direct 与 Sony 对照数据裁决。暂停、flush、seek、路由变化及新 output 重置世代，旧事件不得触发新播放回退；EOS/短片和未知数据门槛保留原通用检测，不做激进判断。
- 复用已有 position/write 调度观察；满足条件后只投递一次带 attempt/output 身份的恢复事件，由现有播放器线程模型处理，接收侧再次验证身份及当前状态。实现时必须证明写入暂时停止后仍能触发，以及退出/新播放不会被迟到通知重启；不在 AudioTrack 回调中阻塞等待另一个线程。
- 首版沿用已经实测能成功的 PCM 原位重新准备流程，保持播放位置、音轨和用户 play/pause 意图；Media3 10 秒通用 stuck detector 继续作为未覆盖状态的最后兜底。不伪造普通网络超时，也不扩大视频自动软解。
- 能节省的是已确认输出停滞后的观察时间；若从 10 秒变为 2 秒，理论可少等约 8 秒，但仍有恢复 prepare/网络时间。本日志的 3–9 秒恢复成本尚未消除，不能承诺所有片源在 2 秒内播放。

**阶段 C：仅在 B 后仍有明显重建成本时，验证局部音频恢复。暂缓生产实施。**

先以发货 Media3 的真实 renderer/sink/track selection 做一个可证伪验证，记录 media source prepare 次数、HTTP 请求/读取量、video decoder 初始化次数、音频样本位置及 A/V 偏差，证明 direct → decoder PCM 可以保持媒体队列且没有丢样/重样。若 capability invalidation 会 seek、同 selection 不重建，或须 fork Media3，更新本任务设计并另行批准；不得默认用全局重选或切 standalone clock冒充音频局部恢复。没有这一证据就保留 A/B 的可靠回退。

### 验收、性能边界与回滚

| 合同 | 最便宜且有决定性的验证 |
| --- | --- |
| A 准入/记忆正确 | 用实际 policy 包装和可控 provider 做定向主机测试：offload-only/bitstream/组合位、属性一致、API 31 布尔查询、健康进度、失败后 PCM 成功才记忆、TTL/容量、不同媒体/格式/路由/engine、旧回调隔离。失效/过期允许新起播尝试；同片同配置可跳过已失败 vendor 路径。 |
| 错误归因 | typed stuck 无网络 cause 不标 NETWORK_IO；未知类型不冒充音频；真正 HTTP/socket/connect 错误和 MPV 现有 marker 维持。 |
| B 不误伤 | threshold 未满足、零写入、慢供数、未知位置、暂停/恢复、seek/flush、EOS、短片、路由变化、被替换/释放的 output 均不得触发快速恢复；持续有足够音频但不推进应只恢复一次。 |
| 真实故障速度/成功率 | 反馈 Sony 同音轨/相同起点，基线和候选交替各至少 5 次（冷启动与重复播放分别记录）；记录点击→首帧、首帧→实际音频推进、回退→推进、失败次数及全部样本。样本少时报告中位数/最坏值，不声称统计显著或可靠 p95。先看 A 重复起播不重复 10 秒等待，再看 B 第一次等待减少。 |
| 健康路径与既有功能 | 能正常 vendor-direct 的 vivo/同类设备、一个已有 HDMI AC3/EAC3/DTS passthrough 场景、标准 offload/隧道门禁、平台硬件/平台软件/FFmpeg 偏好、原有 AAC/MP3 及其它格式路由；定向验证暂停、seek、切轨、倍速资格和退出。无需重跑无关全格式/ABI 矩阵。 |
| 性能不回退 | 健康设备相同片源/路线做候选与基线交替；正常路径的 codec/AudioTrack 数量、请求/读取量、CPU、内存、首帧和稳定播放掉帧不出现可归因于改动的增加；A/V sync 不恶化。若波动大到无法裁决，只追加能区分该问题的样本。源码中无双 decoder/新线程并不等于实测性能保证。 |

每阶段只运行受影响的主机测试与一个 Leanback armeabi-v7a 编译目标；现有同类编译/定向测试最近为约 38–92 秒，不能当作新阶段实际用时。需要设备时才打包对应候选，不提前重建 native 或全 ABI。实施时间按当时设备与 Gradle 状态重新声明；本轮未构建/安装/实测候选包。受影响 Sony 与正常 direct/HDMI 的现场对照未完成前，不能宣称“所有现有功能和性能均已确保”。

兼容性/质量：保留当前音画同步、声道/采样率、手动偏好、视频路径、隧道、AV3A 及其它格式；通过同音轨 PCM 恢复，不自动挑低质量替代音轨。维护/生命周期：采用现有 provider 包装和实例世代，故障记忆有界且不持有播放器/Activity。安全/数据所有权：仅进程内技术状态，不持久化用户 URL/token 或上传诊断；不增加网络接口。ABI/体积/许可证/供应链：阶段 A/B 只改 App Java 与定向测试，依赖锁/native ABI/二进制及许可均不变，外部代码只引用原则而不复制到制品。

落地顺序为 A → B → 按实測决定是否 C；每阶段独立 guard、验证记录、原子提交和 annotated recovery tag。先用目标设备候选包对照，满足合同再推广；若健康路径降级、音画异常、重复恢复或性能回退，停止推广并 revert 对应单提交，进程内记忆随进程结束清空。生产行为不得为满足速度指标而跳过门槛/路由/所有权保护。

本研究单元交付决策记录；文档校验与原子提交/tag 由 `E11-audio-startup-research` guard 收尾，无生产变更或推送。用户已于恢复后明确批准继续实施，后续按 A → 具备证据的 B 推进；C 的局部恢复暂缓。实施自 2026-09-21 13:52 Asia/Shanghai 起预计 50–70 分钟（核对/研究收尾约 8 分钟，实现/定向测试约 35–50 分钟，验证/记录/提交约 7–12 分钟），实际硬件验证另按可用设备记录，不能把主机测试写成设备验收。

## 历史 Recovery anchor（2026-09-20）

- Objective / acceptance：修复 AAC/MP3 厂商压缩直出已接收音频却无播放进度、最终被 Media3 判定超时后未自动回退 PCM 的缺口；仅恢复失败配置，保留视频解码、正常直出、标准 offload、隧道和用户设置。
- User decision：用户在收到日志/源码定位与最小修复建议后明确要求“修复Bug”。
- Lane / guard：`quick-fix` / `E11-audio-direct-stall-fallback`。
- Branch / baseline：`feature/mpv-dv7-fel` / `2623cb812ea842b676bc7d8db699c1a7e70b8e1e`。
- Scope：`ExoCompressedAudioDirectPolicy.java`、`ExoPlayerEngine.java`、`ExoCompressedAudioDirectPolicyTest.java`、本文与任务索引；保护原有 `app/.cxx/` 下 104 个文件。
- Evidence：Sony BRAVIA 4K VH2 / Android 12 / armeabi-v7a；反馈日志中 AAC-LC 44.1 kHz stereo 的 encoded AudioTrack 接收 1,929,665 bytes、writeErrors=0、headFrames=0；10 秒后 `STUCK_PLAYING_NO_PROGRESS` / code 1003 被按 FATAL 处理。日志标记的基线 `88aceb110959ff50afc23b10d9b9abe3e0f53255` 与本次基线的策略/引擎文件一致；反馈 APK 标记 dirty，不能仅凭 revision 证明其全部内容。
- Plan status：类型明确的错误路由、vendor 输出进度观察和 attempt/输出隔离已实现；Leanback armeabi-v7a 编译与 33 项定向测试通过，进入原子提交/tag 收尾。
- Unverified edits：无未验证代码；任务内两个生产文件、一个测试文件及本文/索引待原子提交。本轮未打包 APK。
- Risk / limit：本轮依据反馈日志和截图实施，尚未在反馈 Sony 上执行同片源回归；主机测试验证控制流与生命周期，不能冒充 Sony HAL 已通过回归。
- Rollback：本次 App 源码、测试、文档作为一个原子提交，可 revert 该提交；不涉及依赖或 native 制品。
- Next action：使用当前 guard 原子提交并创建恢复 tag；完成后以该提交和 `recovery/E11-audio-direct-stall-fallback/*` 的 Git 记录作为恢复点，不为回填 ID 另开文档提交。

## 2026-09-20 压缩音频直出无进度回退

### 最佳实践与本地合同复核

本次为 E11 已建立的“实际输出失败后恢复同轨 PCM”合同补漏，属于根合同允许的局部纠错。复用现有 Media3 检测器、输出包装和重试入口，不新增定时器、线程、平台兼容分支、解码算法或依赖升级；既有官方 API/issue 研究见下文，不重复泛化搜索。

访问日期：2026-09-20。发货 Media3 为 `1.11.0-alpha01-fongmi`，锁定源 `e3e922d5c01bc0b564849940fe589daf37360d15`；以下为 `third_party/maven/androidx/media3/{media3-common,media3-exoplayer}/1.11.0-alpha01-fongmi/*-sources.jar` 中实际源码（A级证据）：

| 来源 | 可核实合同与决定影响 |
| --- | --- |
| `StuckPlayerDetector.StuckPlayingDetector`、`StuckPlayerException` | 只在 `isPlaying()` 且同一 period/广告/媒体位置持续不变时产生 `STUCK_PLAYING_NO_PROGRESS`；暂停/缓冲会撤销检测。按异常类型和 `stuckType` 匹配，不按错误文本或所有 code 1003 猜测。 |
| `ExoPlayerImpl.ComponentListener.onStuckPlayerDetected/stopInternal` | 使用 `ERROR_CODE_TIMEOUT` 包装异常，先异步要求播放线程 stop，再通知 App；音频 pause/release 与 App 收到错误存在竞争。停滞证据必须属于本次输出，并能保留到本次错误处理。 |
| `AudioOutput.write/getPositionUs/play/pause/flush/stop/release`、`AudioTrackAudioOutput` | write 的 buffer.position 增量证明实际接受 payload；position 是输出播放时间。仅观察已有调用，不主动跨线程调用 AudioTrack，不复制音频或改变返回值。 |
| `DefaultAudioSink.getCurrentPositionUs/hasAudioOutputPendingData` | 正常调度已查询输出位置；可在包装层记录进度，无需额外轮询或线程。 |
| 当前 `ExoCompressedAudioDirectPolicy`、`ExoPlayerEngine.handleError/retryAudioOutputWithPcm/startInternal` | 只处理初始化/写入异常；已有同配置禁用、屏蔽 generic passthrough、保留原位置重启逻辑可直接复用。每次 prepare 清除旧输出证据，但保留引擎内失败配置集合，防止反复直出。 |

没有新的上游提交候选；不改源码锁/补丁/二进制。论文、广泛论坛或性能基准不能改变这条类型明确、数据可观测的错误路由决定；本轮以实际源码、用户日志和可控输出回归验证。保留 E11 既有平台/成熟实现研究及其限制。

### 方案、验收与风险

- 不改：拒绝，已证实在 no-progress 超时后直接终止。
- 只用原有 Media3 行为：检测准确，但超时默认终止，不能禁用 WebHTV 厂商直出配置。
- 对所有超时或所有无硬件音频 codec 强制软解：拒绝，会误伤断流、视频故障及正常 DSP 直出。
- 采用窄适配：只对实际厂商 direct 输出观察已接受数据和播放位置；在连续播放期间输出至少 2 秒无进度时保留证据，再以现有 Media3 10 秒 `STUCK_PLAYING_NO_PROGRESS` 作为恢复触发。仅标记该 encoding/rate/channel-mask 失败并复用 PCM 原位重试。独立的 2 秒证据窗避免把最近仍在前进的音频归因到播放器停滞，也允许线程通知误差；不提前产生错误。
- 正常 play/resume 重新开始观察；暂停时不累计新的证据；flush、自然 stop、下一次 prepare 和新输出清除不适用证据。旧输出异步释放不能污染新输出；已确认停滞证据保留到异步错误回调。
- 正常路径只在现有 vendor-direct write/position 调用中更新少量标量，无新增 buffer、解码器、音频复制或工作线程。标准 PCM/offload/隧道不安装检测包装。
- 定向测试覆盖真实包装层的成功写入但停滞、正常进度、零写入、暂停/恢复、flush/stop、释放先于错误、新输出/新 attempt 隔离、其它错误类型、配置禁用及一次性消费；同时保留原有直出/失败 PCM/隧道/offload 测试。编译一个受影响 App 变体。
- 设备验收仍需在反馈 Sony 上用同一片源、音频优先软解关闭，确认发生一次 `playing-no-progress` 回退后 PCM 与媒体进度恢复；正常直出设备及暂停/seek 需按风险验证。主机结果不等同于该设备验收。

### 实施与验证记录

- `ExoCompressedAudioDirectPolicy` 的 vendor 输出包装只观察已有 write/position 调用；真实 payload 已接受、播放中位置连续 2 秒不变时保存证据。恢复必须同时匹配 Media3 `ERROR_CODE_TIMEOUT` 的 `StuckPlayerException.STUCK_PLAYING_NO_PROGRESS`，不匹配错误文案，也不泛化其它超时。
- 每次 prepare/stop/rebuild/release 换新 attempt；初始化前捕获 attempt，旧初始化晚完成也不能填入新 attempt。正常输出替换旧证据，旧 release 不覆盖新实例；已证实停滞可跨越 Media3 异步 pause/release，供同一次错误处理消费。
- `ExoPlayerEngine.handleError` 复用已有配置禁用与 `retryAudioOutputWithPcm()`，保持原位置/播放意图；成功返回 RECOVERED。已核对 `PlayerManager` 调用链，恢复成功后退出错误处理，不进入视频软解或线路重试。
- JDK 21.0.10 / Gradle 9.5.1：`:app:testLeanbackArmeabi_v7aDebugUnitTest` 及依赖的 App Java 编译成功，Gradle 执行用时 1 分 32 秒。仅执行 `ExoCompressedAudioDirectPolicyTest`（26 项）与 `ExoAudioOutputStateTest`（7 项），合计 33 项，failures/errors/skipped 均为 0。
- 测试覆盖反馈的 44.1 kHz 与既有 48 kHz 配置；已接受数据但零进度、非零位置后停滞、正常进度、零写入/未知位置、暂停/恢复、flush/stop、先释放后通知、新输出和延迟初始化隔离、其它错误类型、标准 PCM/offload/隧道以及原有写入失败回退均通过。
- 通过 `/private/tmp/webhtv-E11-audio-stall-zxepv15j/host-tests.init.gradle` 临时限定两个测试类并启用 Android mock 默认值，原生暂存重定向到同目录 `cxx`；没有修改生产构建配置。主机测试使用实际生产包装层和可控 `AudioOutput`，不是真实 AudioTrack/DSP 运行证明。
- 完整成功日志：`/private/tmp/webhtv-E11-audio-stall-zxepv15j/gradle-verified.log`；JUnit XML：`app/build/test-results/testLeanbackArmeabi_v7aDebugUnitTest/`。首次沙箱调用只在 Gradle 缓存锁处被拒绝，授权后完成唯一一次实际编译/测试；未重复成功检查。
- 本次基线和提交/tag 由 `E11-audio-direct-stall-fallback` guard 维护；同一原子提交含源码、测试及本文/索引，不另建 E11 跟进文档。

## 历史 Recovery anchor（2026-09-18）

- Objective / acceptance：修复 seek 后旧音频输出释放清空新状态、输出未知时参与自动调速、厂商压缩输出破坏隧道配置三项合同；不改变视频手动硬/软解选择，保留标准直通/offload、非隧道 vendor-direct 和既有音频失败回退。
- User decision：用户已明确“实施修复”；无需新增设备日志作为代码修复前置条件。
- Lane / guard：quick-fix / `E11-seek-output-consistency`。
- Branch / baseline：`feature/mpv-dv7-fel` / `ad6f68d5120d49345e5c3b147e1580f9cc280c5f`。
- Scope：`ExoAudioOutputState`、`ExoCompressedAudioDirectPolicy`、`ExoNetworkGuardEligibility`、`PlaybackAnalyticsListener`、`ExoPlayerEngine`，对应三个定向测试，本文与任务索引。
- Protected：原有 `app/.cxx/` 下 104 个 dirty 文件；无依赖、native、构建脚本或视频解码选择修改。
- Completed evidence：已对比两个发布版本、当前实际 Media3 源码和 App 调用链；已确认现有测试将隧道单边关闭和 UNKNOWN 调速作为预期，需按音视频整体合同纠正。
- Implementation：三项生产修复已完成；每个引擎按实际 AudioOutput 实例持有状态，旧释放仅能清本实例；UNKNOWN 阻止自动调速；标准 tunneling/offload 配置不再被 vendor-direct 缓存覆盖。
- Verification：Mobile ARM64 App Java 编译及三个定向 JVM 测试类通过，共 25 项，失败/错误/跳过均为 0。覆盖输出实例交错释放、当前输出释放、实例隔离、隧道/标准 offload/cache 交错以及未知输出调速门禁。没有受影响用户设备，不能把本机检查表述为所有 seek 卡死已实机解决。
- Unverified edits：无未验证的代码修改；尚未验证受影响设备的实际 seek 场景，本轮未打包或安装 APK。
- Rollback：完成时使用 guard 原子提交并立即创建 annotated recovery tag；必要时 revert 本次单提交即可，依赖制品不变。
- Next action：由当前 guard 原子提交并创建恢复 tag；提交/tag 以该逻辑提交的 Git 记录为准，不为补写哈希另开提交。

## 2026-09-18 跳转后画面停滞：复核、决定与实施计划

### 版本和证据

访问/核对日期：2026-09-18。两次前序分析已经完成，不重复网络研究或编译。

| 来源与版本 | 等级 | 核实结论与决定影响 |
| --- | --- | --- |
| 旧版 `v5.6.0-202609051525` → `ec478b0b697422a7785171c7b51a35b7a526564e`；截图版 `v5.6.0-202609061650` → `784b90420d646eb6c7ddcc63ad622a92c65b02b4` | A，实际 Git 内容 | 旧 tag 指向 8 月 31 日代码，期间加入音频策略；不能按发布日期推定只有一天代码差异，也不能仅凭这些差异确认用户卡死根因。 |
| WebHTV `cf0a5dabc77fb2bbdc3e0f2cc867a9eac86060b8` | A，实际 diff | 音频硬解优先引入点；本轮保留该选择策略，不把视频切到软解或整体取消音频硬解。 |
| WebHTV `3fdf9f82f37843699a2545ed97d4a2dd17b8ead5` | A，实际 diff | vendor-direct 能力声明/建轨强制 non-tunneling 的引入点；修正其与实际隧道配置的协商。 |
| WebHTV `37995ff14016fd5a26fdae2b482f08470aa6a162`、`37888d8b9d99da29f9ecfc3cd1f5eba458e09ee0` | A，实际 diff | 按 AudioTrack 配置匹配释放回调的全局状态，后来被网络保护用于控制调速；同配置新旧输出无法区分。 |
| 当前 `ad6f68d5120d49345e5c3b147e1580f9cc280c5f` 的 `third_party/maven/androidx/media3/media3-exoplayer/1.11.0-alpha01-fongmi/*-sources.jar`：`DefaultAudioSink`、`AudioTrackAudioOutput`、`MediaCodecAudioRenderer`、`DefaultMediaClock` | A，实际发货源码 | seek 会 release AudioOutput；AudioTrack 真正释放/通知异步完成，新输出可先初始化；Media3 用音频 renderer 时钟同步视频。采用实际输出对象的所有权，不再让延迟的统计回调决定运行状态。 |
| 同一 Media3 源码：`DefaultTrackSelector.maybeConfigureRenderersForTunneling`、`MediaCodecAudioRenderer.onEnabled/supportsFormat` | A，成熟项目代码 | 隧道同时配置音视频；音频渲染器在启用后按 sink 实际能力决定 bypass/decoder。隧道请求必须交给标准 provider，禁止 vendor-direct 把已请求的隧道单边清掉。 |
| E11 历史官方 API、设备实验和 issue 资料（下文保留） | A/B，既有研究 | 保留按实际 encoding/sample rate/channel mask 查询的非隧道 direct；不扩大格式白名单、不改建轨线程或音频样本处理。 |

本轮属于已建立生命周期和隧道合同的局部纠错，适用根合同的窄修复研究豁免；没有新增上游候选、算法、架构或 native 兼容路径。上述实际源码已足以决定修法，论文/广泛论坛搜索不能改变实例所有权和配置一致性的结论，故不新增这类搜索。

### 当前调用链与缺口

1. `ExoPlayerEngine` 持有每个引擎独立的 `ExoCompressedAudioDirectPolicy`；`ExoUtil.buildAudioSink` 已将它包在所有实际 AudioOutput 创建路径上，因此不需要增加另一套播放器或修改 Media3。
2. 旧 `PlaybackAnalyticsListener.onAudioTrackReleased` 仅比较 encoding/rate/channel/tunneling/offload；同配置 A → B 后，A 的延迟回调可能清空 B。该全局快照由 `ExoPlayerEngine.getAudioPlaybackDiagnostics` 读取并传给 `PlayerManager.getNetworkProtectionEligibility`。
3. `ExoNetworkGuardEligibility` 对 UNKNOWN 放行；状态不确定时不能决定对压缩输出调速。
4. vendor-direct 缓存只按 encoding/rate/channel 记录。仅在能力查询时判断隧道还不够：最终 OutputConfig 与建轨/modifier 也必须保护 tunneling/offload，避免旧查询缓存覆盖标准输出。

### 方案比较与选择

| 方案 | 决定 |
| --- | --- |
| 不改 | 拒绝：保留可证明的状态归属错误与单边隧道配置。 |
| 继续按配置猜测回调所属，或延时清理 | 拒绝：同配置无法区分新旧；任意延时也不能证明实例归属。 |
| 直接照搬 Media3 内部监听器 | Media3 内部按监听器实例隔离旧事件的原则正确，但 App Analytics 回调只给配置；须在现有输出 provider 处取得实例所有权。 |
| 每个引擎跟踪实际 AudioOutput + 身份比较释放；未知状态门禁；隧道委托标准 provider | 采用：在初始化/释放边界处理，借助既有 provider 包装，不增加线程、样本复制、逐帧状态统计或全局禁用正常能力。 |
| 整体回滚音频功能、升级 Media3、增加通用卡死重启 | 不采用：会扩大行为/验证范围，且现有证据不能支持针对所有设备的重启策略。 |

### 实施与验收

- 新 `ExoAudioOutputState` 在实际创建成功后发布不可变快照，每次创建产生独立身份；release 只清除本实例仍持有的快照，并继续委托原始释放。状态归属单个引擎，读取为原子快照，旧异步 Analytics 回调仅保留日志。
- `ExoPlayerEngine` 读取自身 policy 的输出快照；移除全局 Analytics 输出状态及按配置匹配的释放更新。
- UNKNOWN 输出阻止网络保护自动调速；保留 PCM、既有 offload、手动倍速和其它既有门禁的处理。
- FormatConfig 请求 tunneling 时不探测/启用 vendor-direct，标准 provider 决定支持和输出配置；最终建轨与 builder modifier 也尊重 OutputConfig 的 tunneling/offload，缓存不能越权覆盖。
- 测试真实输出包装的交错 release、同配置新身份、当前实例 release 和引擎隔离；测试标准隧道保留、unsupported 返回、已缓存 direct 后的隧道/offload，以及既有非隧道 direct/失败 PCM 回退。
- 成本仅为每次输出创建/释放的不可变快照与原子更新；不改逐帧/音频数据内容、ABI、JNI、包内 .so、网络请求或音视频解码选择。
- 验证结果与提交/tag 在本节收尾记录；受影响设备缺少实机复现条件属于结论限制，不反复向用户索取，也不因此留下已获批代码修复未完成。

### 本轮验证结果（2026-09-18）

- JDK 21，`:app:testMobileArm64_v8aDebugUnitTest` 及依赖的 `compileMobileArm64_v8aDebugJavaWithJavac` 成功，最终 Gradle 用时 38 秒。
- `ExoAudioOutputStateTest` 7 项、`ExoCompressedAudioDirectPolicyTest` 13 项、`ExoNetworkGuardEligibilityTest` 5 项，合计 25 项全部通过，无跳过。
- 临时 host init 只选择上述三个任务内测试类，并启用 Android mock 默认值；不把它当作真实 AudioTrack、硬件隧道或受影响设备实测。生产构建配置未修改。
- 首次执行被沙箱阻止写 Gradle 缓存锁；授权后编译暴露一处已删除局部变量仍被日志语句引用，改读实际 OutputConfig 后完成上述验证。未重复已通过的检查。
- 完整成功日志：`/private/tmp/webhtv-E11-seek-output-verification/gradle-fixed.log`；JUnit XML：`app/build/test-results/testMobileArm64_v8aDebugUnitTest/`。原生构建暂存目录沿用既有隔离 init，原有 `app/.cxx/` 保留。
- 本轮 App 代码修复/定向验证完成；guard ID `E11-seek-output-consistency` 的原子提交与 annotated `recovery/E11-seek-output-consistency/*` tag 为恢复点。没有设备证据支持“所有 seek 卡死均已解决”的结论。

## 原始 E11 记录（从历史提交恢复）

## 历史 Recovery anchor（2026-09-02，非当前状态）

- Objective: Exo 对设备 Audio HAL/DSP 明确支持的普通压缩音频优先发送原始 access unit 到 AudioTrack，失败后自动回退同一音轨 PCM；播放参数只显示当前实际输出链路。
- Acceptance: AAC/MP3 代表样本在 vivo V2453A 上出现 WebHTV UID 的压缩 `DIRECT/COMPRESS_OFFLOAD` AudioFlinger Track；不支持的声道/采样率保持 PCM；初始化或写入失败只触发 Media3 可恢复回退；pause、seek、flush、切轨和 A/V 同步正常；无双解码、逐帧额外复制或新增线程。
- Branch / baseline: `feature/mpv-audio-fallback-policy` / `d41155f16cd81f1354672a5479743462fc168ed9`.
- Protected pre-existing dirty paths: `app/.cxx/` 下 69 个 task guard 记录的既有生成文件。
- Approved scope: `ExoCompressedAudioDirectPolicy.java`、`ExoUtil.java`、Exo 运行输出快照/映射、聚焦测试、本文和主索引。
- Status: approved and implementation active.
- Rollback anchor: revert the atomic E11 commit or restore the recovery tag created at closure.
- Next action: run the final E11 task-guard closure with the recorded unit-test, build, and V2453A cold-start evidence.

## 用户目标与批准

用户要求解决三播放器“设备有音频硬件能力但实际始终软解 PCM”的完整问题，并已批准全部阶段实施。E11 是第一独立回滚单元，只处理 Exo；MPV native、IJK 和通用能力页分别在后续任务完成。

## 现场根因

- vivo `V2453A` / Android 15 / API 35 的 91 个 MediaCodec decoder 中没有真实硬件音频 decoder；E10 和 P3-4 正确回退 FFmpeg/平台软件 PCM。
- 设备 Audio policy 同时声明 `compress_offload_out`，支持 MP3、AAC LC/HE、FLAC、ALAC、APE、Vorbis，flags 为 `DIRECT|COMPRESS_OFFLOAD|NON_BLOCKING|GAPLESS_OFFLOAD`。
- 标准 offload API 对 AAC/MP3 返回不支持，但 `AudioManager.getDirectPlaybackSupport()` 返回 bitstream support；独立压缩 AudioTrack 即使调用 `setOffloadedPlayback(false)`，AudioFlinger 仍实际打开 AAC LC 的 type 4 OFFLOAD 线程。
- 当前 `ExoUtil.buildAudioSink()` 未启用 audio offload preference；默认 `DefaultAudioOffloadSupportProvider` 只依据标准 offload API，因此不会进入设备已有的厂商 direct-bitstream DSP 路径。

证据目录：`/private/tmp/android-device-test-P3-4-20260901-rootcause/`、`/private/tmp/android-device-test-P3-4-20260901-aac-live2/`；独立探针：`/private/tmp/AudioTrackProbe.java`、`/private/tmp/AudioOffloadInventory.java`。

## 最佳实践证据

访问日期均为 2026-09-01。

| 来源 | revision / URL | 等级 | 支持的结论与 WebHTV 影响 |
| --- | --- | --- | --- |
| Android `AudioManager.getDirectPlaybackSupport` | `https://developer.android.com/reference/android/media/AudioManager#getDirectPlaybackSupport(android.media.AudioFormat,%20android.media.AudioAttributes)` | A，官方 API | direct support 必须按实际 encoding、sample rate、channel mask 和 audio attributes 查询；不能把 MIME 或 policy 白名单单独当作运行能力。 |
| Android `AudioTrack.Builder.setOffloadedPlayback` | `https://developer.android.com/reference/android/media/AudioTrack.Builder#setOffloadedPlayback(boolean)` | A，官方 API | 标准 offload 是 AudioTrack 构建属性；厂商可能在普通 direct bitstream 请求上隐式选择 DSP，因此必须以实际压缩 Track/运行回调验收。 |
| WebHTV 锁定 Media3 | `1.11.0-alpha01-fongmi` AAR；`DefaultAudioSink`、`AudioTrackAudioOutputProvider`、`MediaCodecAudioRenderer`、`TrackSelectionParameters.AudioOffloadPreferences` | A，实际发货二进制 | 已实现压缩 access-unit 帧数、非阻塞写入、播放头、pause/seek/flush、路由回调和 offload 初始化/写入可恢复错误；WebHTV 只需补能力判定与选择策略，不应重写热路径。 |
| AndroidX Media issue #2258 | `https://github.com/androidx/media/issues/2258` | B，上游设备问题 | 路由/显示切换可能使压缩输出退回 PCM；参数面板必须取当前 AudioTrack 配置，不能取预先能力或设置值。 |
| vivo V2453A Audio HAL 与独立探针 | 上述本地证据，设备 build/API 35 | A，目标设备实测 | 标准 offload false 与实际 DSP direct/offload 可同时存在；厂商适配应以官方 direct support 为门禁，并让 AudioTrack 初始化作为最终裁决。 |
| P3-4 / E10 本项目实现 | `d41155f16cd81f1354672a5479743462fc168ed9`、`cf0a5dabc77fb2bbdc3e0f2cc867a9eac86060b8` | A，本项目代码与真机证据 | MediaCodec 能力与 Audio HAL 能力是两个维度；实际输出失败必须回退，能力页和运行面板必须分离。 |

### 证据类别说明

- PR/issue/revert：issue #2258 证明输出路由可在运行时改变；未发现可直接覆盖 vivo 非标准 direct-bitstream offload 的通用上游修复。
- 成熟相关实现：Media3 本身是本阶段采用的成熟实现；mpvRex、SaltPlayerSource 没有比 Media3 更完整的视频容器 compressed AudioTrack 时钟/seek 合同，因此不复制其输出层。
- 论文/基准：本阶段不改变编解码算法；学术论文不适用。性能以零双解码、零新增热路径复制和真机 CPU/AudioFlinger 证据验收。

## 方案比较与决定

| 方案 | 正确性 | 性能 | 风险 | 决定 |
| --- | --- | --- | --- | --- |
| 不改 | 所有普通压缩音频继续软件 PCM | CPU/功耗较高 | 不满足目标 | 拒绝 |
| 仅启用 Media3 默认 offload | 标准 API 正常设备有效，vivo 仍被判不支持 | 成熟稳定 | 覆盖不足 | 拒绝 |
| App 自建 packet/AudioTrack 管线 | 可完全控制 | 容易重复复制、时钟和生命周期代码 | 回归面大 | 拒绝 |
| Media3 管线 + 标准 offload/direct 双证据 provider | 复用成熟 encoded 管线，设备明确支持才启用，失败可恢复 PCM | 稳态无额外解码/复制 | 厂商 direct 模式需真机严验 | 采用 |

## 实施设计

1. `DefaultTrackSelector` 保留 `AUDIO_OFFLOAD_MODE_ENABLED`，仅供标准 Media3 offload 使用；厂商 direct 不再伪装成 offload。
2. 新 provider 先保留标准 passthrough/offload 判断；标准输出不支持时，仅对 Media3 已能计算 encoded sample frames、Android encoding 有效、系统 direct playback 明确支持的格式返回 `FORMAT_SUPPORTED_DIRECTLY`，使 `MediaCodecAudioRenderer` 走 encoded bypass。
3. direct 查询使用真实 sample rate/channel mask/audio attributes；未知声道、超出 policy 的 AAC 5.1 等不强行启用。
4. 厂商 direct 输出使用 encoded、non-offload `AudioOutputProvider.OutputConfig`，AudioTrack builder 最后固定 256 KiB 缓冲并设置 `setOffloadedPlayback(false)`；vivo HAL 可隐式路由 DSP，同时该路径不受 Media3“仅纯音频允许 offload”的限制。
5. 厂商 direct AudioTrack 初始化或写入失败时，在本次 player 会话中拉黑该 encoding/sample-rate/channel-mask；Media3 重新选择同轨 decoder + PCM，不持久化错误状态。
6. 运行面板以 AudioTrack 初始化快照中的实际 encoding/output mode 为事实；能力或设置只用于选择，不能直接生成“硬件直出”文案。

## 性能与兼容合同

- 不并行创建软件 decoder，不双解码，不预热，不增加线程。
- 不进入 PCM buffer 内容处理，也不复制数据；encoded write 只增加异常边界，正常成功路径没有额外分配；能力查询只发生在格式支持/AudioTrack 配置阶段。
- 标准 Media3 offload 设备保持上游行为；厂商 direct 适配只在标准 provider 不支持且系统 direct support 明确成立时生效。
- 倍速、音频处理器、karaoke/音效等需要 PCM 的场景由 Media3 support/selection 约束自动保留 PCM。
- HDMI 原码直通和普通压缩 DSP 直出是不同语义；E11 不改变既有 AC3/DTS/TrueHD passthrough 决策。

## 验收、发布与回滚

1. 聚焦单测覆盖标准支持优先、direct fallback、未知格式拒绝、encoding 白名单和同配置失败后不重试。
2. App Java 编译与 Mobile arm64 Debug APK。
3. V2453A 播放 AAC/MP3 stereo：AudioFlinger 必须显示 WebHTV UID 的压缩 DIRECT/OFFLOAD Track，面板显示实际硬件直出；AAC 5.1 必须保持 PCM。
4. pause/resume、seek、切轨/切集、路由变化和至少一次失败回退；不得循环重建或 A/V 漂移。
5. 比较同一输入 CPU/温度和 prepare-to-audio；稳态不得比 PCM 路径增加可见负担。
6. E11 为 App-only 单提交，不更新 Media3 AAR/native；异常时回滚该提交即可恢复默认 PCM/直通行为。

## 实施记录

- 2026-09-01 13:18 CST：恢复 `feature/mpv-audio-fallback-policy@d41155f16cd81f1354672a5479743462fc168ed9`，确认仅 `app/.cxx/` 为既有受保护生成目录，V2453A USB 在线。
- 2026-09-01：完成锁定 Media3 二进制 API/字节码、Android 官方 API、目标设备 Audio policy/AudioFlinger/独立探针和 issue #2258 复核；用户已批准实施。
- 2026-09-01 16:41 CST：ADB 直接启动 `AAC_LC_2.0_48kHz.mp4`，provider 多次判定 `vendor-direct`，但 Media3 仍创建 `c2.android.aac.decoder` 和 PCM AudioTrack。锁定 Media3 字节码确认 `DefaultTrackSelector.maybeConfigureRendererForOffload` 在任何非音频 renderer 被选中时禁止 offload，因此原实现只可能覆盖纯音频，不能满足视频播放目标。
- 2026-09-01 16:49 CST：ADB 直接启动 MP3 纯音频，Media3 选中 vendor offload 后 AudioTrack 初始化失败并自动回退 `c2.android.mp3.decoder` + PCM；独立 `app_process` 探针确认同一 AAC/MP3 配置采用 non-offload direct AudioTrack 和 256 KiB 缓冲均能成功打开。实施设计据此修正为 encoded bypass + non-offload direct AudioTrack。
- 2026-09-01 18:04 CST：使用手机测试库 AAC 视频连续完成 v4/v5 命令行回放。Media3 provider 在 encoding=10、48 kHz、stereo、256 KiB、session=0、non-tunneling、non-offload 下仍返回 `status=-38`；同一设备、同一 WebHTV UID、同进程名的独立探针在完整 AudioAttributes、重复 builder setter、32 次 direct-support 查询后均成功创建 AAC OFFLOAD 线程。已否定 tunneling、session、virtual-device Context、扩展 AudioAttributes、setter 顺序及 capability-query 竞争假设，下一步仅替换 vendor-direct 的建轨动作，仍由 Media3 AudioTrackAudioOutput 管理时钟与生命周期。
- 2026-09-01 18:59 CST：连续播放失败根因进一步确认：vendor-direct 建轨异常后虽然配置已加入失败集合，但 provider 仍透传底层标准 passthrough/direct 支持，Media3 没有重新选择 PCM decoder。采用同一 Exo 引擎内复用 direct policy、由 `ExoPlayerEngine.handleError()` 原地重启当前 item 一次、并对失败配置强制返回 encoded 不支持的方案，避免新增播放器实例和重试循环。
- 2026-09-01 22:39 CST：V2453A 冷启动播放手机测试库 `A11_MP3/MP3_2.0_44.1kHz_128kbps.mp3`。首次 vendor-direct 初始化返回 `status=-38` 并上报 `ERROR_CODE_AUDIO_TRACK_INIT_FAILED`；同一 PID 随后创建 `c2.android.mp3.decoder`，未发生用户二次点击。
- 2026-09-01 23:00 CST：为避免 3.7 秒 AAC 样本在恢复窗口结束前自然结束，临时生成 59 秒 AAC LC 2.0 副本（仅 `/private/tmp`，未进入测试库/Git）。首次播放在 `mediaPos=0.00` 报 direct 初始化失败，随后同一 PID 创建 `c2.android.aac.decoder`；AudioFlinger 记录 PID 23905、Track 3367、PCM 16-bit、48 kHz、stereo，播放器保持 `VideoActivity`。
- 2026-09-01 23:02 CST：V2453A 冷启动播放 `A01_AAC/AAC_5.1_声道.mp4`。AudioFlinger 记录 PID 24878、Track 3368、PCM 16-bit、44.1 kHz、channel mask `0x3F`（6 声道）；该声道组合未进入 vendor-direct，直接走 decoder + PCM。
- 2026-09-01 23:06 CST：确认短 AAC 样本的首次 2 秒抓取不作为验收证据；其后长样本已闭合首次自动回退、解码器初始化和 PCM 输出链路。

## 测试库扩展（2026-09-01）

依据 V2453A 的 `compress_offload_out` profile（MP3、AAC-LC/HE、FLAC、ALAC、APE、Vorbis），在本机与手机 `/storage/emulated/0/Download/影音测试库/` 各补充以下 6 个可追溯样本。每个文件已用 `ffprobe` 验证编码/采样率/声道，并完成手机回读 SHA-256 比对；这些样本用于后续 Exo/MPV/IJK 能力覆盖和回退测试，当前 E11 的 Exo 直出验收仍限定 AAC/MP3。

| 文件 | 实际格式 | 来源 | SHA-256 |
| --- | --- | --- | --- |
| `A01_AAC/AAC_HE_V1_2.0_44.1kHz.aac` | HE-AAC v1，44.1 kHz，2.0，ADTS | `https://samples.ffmpeg.org/A-codecs/suite/AAC+/WishI-48kSBR.aac` | `5326480b94f1828fdfce398a6bf528308e7152dbf44b9d4838f4633efaf5a414` |
| `A01_AAC/AAC_HE_V2_2.0_44.1kHz.aac` | HE-AAC v2，44.1 kHz，2.0，ADTS | `https://samples.ffmpeg.org/A-codecs/suite/AAC+/WishI-48kSBRPS.aac` | `91f9eba3b402b755d05dfc503220ba4b85ca877c95f16464117241b7852bbd46` |
| `A12_ALAC/ALAC_2.0_48kHz.mov` | ALAC，48 kHz，2.0，MOV | `https://samples.ffmpeg.org/A-codecs/lossless/ALAC/ALAC_24bits2.mov` | `3ddb9d38a4ec51cde8be6c5840cb622f77d20aa7a4652b613d7b49ab181e9e55` |
| `A12_ALAC/ALAC_5.1_48kHz.mov` | ALAC，48 kHz，5.1，MOV | `https://samples.ffmpeg.org/A-codecs/lossless/ALAC/ALAC_6ch.mov` | `944a78472074f6c0a4df74d0126eede3581f70cd171943dfe45c93a3c0e8dc35` |
| `A13_APE/APE_2.0_44.1kHz_sh3.ape` | Monkey's Audio，44.1 kHz，2.0 | `https://samples.ffmpeg.org/monkeyaudio/sh3.ape` | `9b8e89b81a87001648d58dc9ef440a5b9b8c214a4df07bd22776da1ff6e32004` |
| `A14_Vorbis/Vorbis_2.0_44.1kHz_160kbps.ogg` | Vorbis，44.1 kHz，2.0，160 kbps，Ogg | AndroidX Media3 `2bc207851df311340767e913931ca7b28cab1794` `media.exolist.json`; `https://storage.googleapis.com/exoplayer-test-media-1/ogg/play.ogg` | `d5bdb7257d6b9bb2d22c005685e4fa0984db32ac1963b792414916ee79352f62` |

`samples.ffmpeg.org` 是 MPlayer/FFmpeg 测试样本集合，新增样本仅用于本地/测试设备验证，不对外重新分发。
