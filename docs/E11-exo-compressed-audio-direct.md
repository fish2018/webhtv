# E11 Exo 压缩音频输出与跳转生命周期

## Recovery anchor（2026-09-18）

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
