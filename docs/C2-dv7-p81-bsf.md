# C2-DV7-P81-BSF：MPV Profile 7 到 P8.1 的显式转换

## Recovery anchor

- 目标：按设备能力自动选择 DV7 原生、P8.1 或 HDR10：原生 DV7 始终优先；设备不支持 DV7 时按用户选择尝试 P8.1 或直接 HDR10；P8.1 不支持/失败必须自动回退 HDR10。
- 任务范围：MPV native patch/build 接线、MPV 两态用户设置与本任务文档/总索引；原生 DV7 保留仅作为内部能力结果，不作为用户选项；不修改 Exo、JNI、Vulkan、AudioTrack、网络或现有 HDR10/Surface 安全补丁。
- 基线：`fongmi-sync` @ `5e90c2ed76830f5c45988d8597d14ffd599dba34`；恢复 tag：`upstream/mpv/c2-dv7-p81-bsf-baseline-20260829`。
- 保护 dirty 路径：无。
- 验收：patch 可按当前锁定 MPV 树应用；Java 编译通过；两 ABI native 产物/ELF/资产校验通过；原生 DV7、P8.1、HDR10、seek/flush/换源和失败回退有证据。
- 当前状态：arm64 已通过删除失效 `HEVC_CONF` 修复 TCL MT9655 卡死；同一本地 DV7 FEL 转换起播仍有约 4--5.5 秒先出声音、后出画的体验问题，MPV 参数面板也因转换后轨道资料变为 Profile 8 而丢失源 DV7 身份。
- 已完成：创建 baseline tag；完成 C2 patch、两态 UI、原生 DV7 优先、P8.1 能力门和 HDR10 自动回退；修复 P8.1 packet/CSD 不一致黑屏和失效 `HEVC_CONF`；Leanback arm64 实机已确认正常画面、DV8 识别、零丢帧且无 decoder release。
- 已完成：章节查询门控修复已原子提交并创建恢复 tag；电视实机回归仍受 ADB 离线限制。
- 下一动作：保留转换前源 DV profile/level，并增加 direct MediaCodec 每会话一条的首输出时间；构建 Leanback arm64 后用同一 P7 FEL 样片一次区分 decoder/VO 首帧延迟与面板遥测延迟，再只修复已证实的启动环节。

## 决策与来源

### 上游来源

| 仓库 | 完整 commit | 作用 | WebHTV 处置 |
| --- | --- | --- | --- |
| `FongMi/FFmpeg` | `177f090e0503b7e013922ca903bde14b1c375f18` | `dovi_rpu` BSF 的 `convert=p81`，改写 DV metadata/CSD、删除 EL NAL | 复用已锁定并已由 C0-M 验证的输入；只在 MPV 显式模式调用 |
| `FongMi/mpv` | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | 当前 `dovi_split`、lavf/Matroska demux 生命周期和 decoder/output 消费者 | 在本地锁定树上窄适配，不替换整树 |
| `FongMi/mpv-android` | `eabfaf9501fc08fb726953a9328da43ae4154d35` | 当前 Android native 构建框架和 ABI 打包 | 只沿用现有构建链；无 JNI/API 变化 |

FFmpeg 的 `dovi_rpu` 必须与 MPV 的 `dovi_split` 共享同一 AVCodecParameters/packet 生命周期。C0-M 已把 MPV FFmpeg 锁定到 `177f090...`，因此 C2 不再改 lock；MPV 与 Exo 仍分别构建和回滚。

### 本地现状

- `mpv-dovi-profile7-hdr10-base-layer.patch` 已实现 `preserve|hdr10` 两态、BL-only packet 过滤、`par_out`/metadata 同步、错误传播和 `INT_MAX` 防护。
- MPV 的 `demux/dovi_split.c` 目前只创建 `dovi_split`，不能调用 `dovi_rpu`；App 只把 MPV 旧布尔值映射成 `hdr10|preserve`。
- Exo 已有独立 `DolbyVisionP81ExtractorsFactory`/libdovi 链路，C2 不替换该实现。

## 最终行为

MPV UI 只保留两种非原生兼容策略：

- `升级P8.1`：默认；设备不能原生播放 DV7、但声明支持对应 P8.1 时使用 FFmpeg `dovi_rpu` 转换；
- `降级HDR10`：设备不能原生播放 DV7 时直接使用 BL-only HDR10 fallback。

设备能够原生播放当前 DV7 profile/level/分辨率时，无论上述 fallback 选择为何都保留原始 DV7。P8.1 静态能力不成立、转换失败或解码运行时失败时只自动重试一次 HDR10，不能保持黑屏；`preserve` 仍是内部自动结果，不再是用户选项。

P8.1 仅对 HEVC、Profile 7、存在有效 RPU+BL 配置记录的轨道生效。BSF 初始化、send/receive、损坏 NAL 或内存失败均不会静默改变默认路径；当前 demux 调用方会丢弃失败 AU 并记录明确错误，换源、seek、flush 继续复用现有 reset 生命周期。

## 方案比较

- 不变：风险最低，但 MPV 无法使用已锁定 FFmpeg 的 P8.1 转换能力。
- 直接整合上游：代码量少，但会绕过 WebHTV 的双态用户策略、packet ownership 和现有 HDR10 decoder gate，容易改变默认行为。
- 本次窄适配：只增加一个 BSF 选择和一条 packet 替换路径，默认仍 `hdr10`，并复用已有错误/flush/Surface 保护；这是本项目的推荐方案。

## 收益、风险与影响

- 收益：不支持 DV7 原生解码但支持 P8.1 的设备可显式播放动态 RPU，色彩和高光映射优于丢弃全部 RPU 的 HDR10 fallback；转换在 demux 层完成，不增加 renderer 循环开销。
- 缺点/风险：每个 DV7 AU 需要一次 BSF 解析/重写；损坏或非标准 RPU 可能失败；厂商对 Profile 8.1 CSD 的接受度仍需实机覆盖。
- 兼容性：原生 DV7 能力优先；旧 `true` 映射 HDR10、旧 `false` 映射 P8.1；P8.1 默认仅在能力门通过后启用，否则自动 HDR10。无 JNI/API/ABI 名称变化。
- 性能/包体积：native 代码复用已编译的 FFmpeg `dovi_rpu`，不新增独立库；P8.1 模式增加 CPU 解析成本，其他模式无额外路径。
- 最佳实践：遵循 FFmpeg 官方 BSF 的配置/初始化/flush/packet ownership 约定，同时保留 WebHTV 的失败回退和会话策略，优于盲目 cherry-pick。
- 上游调整：需要。上游 BSF 本身不负责 MPV 的用户策略、demux packet 所有权和 Android decoder/output 选择，必须由本地适配补齐。

## 实施阶段与验证

1. 增加 `mpv-dovi-profile7-p81.patch`，扩展 demux option、`dovi_split` 选择和 P8.1 参数同步；在构建脚本中按现有 patch 顺序应用并增加 marker 校验。
2. 将 MPV 设置从旧布尔值迁移为两个用户选项，`MpvPlayerEngine` 内部仅传递 `preserve|p81|hdr10`；不改变 Exo 两态行为。
3. 运行 `git apply --check`/shell syntax/Java 编译；随后按同一 lock 做 arm64-v8a 与 armeabi-v7a native build 或在环境缺失时记录阻塞。
4. 设备验证：DV7 原生/P8.1/HDR10 开播、seek、暂停恢复、换源和 EOF；无 RPU、无 BL、损坏 NAL、BSF 初始化失败回退；普通 HDR10/DV5/音频样片无行为变化。

## 回滚

先恢复 `upstream/mpv/c2-dv7-p81-bsf-baseline-20260829`；发布后按原子 commit 恢复 C2 patch、设置接线和 native assets，保留 C0-M/P2-2/P3/P4 已验证能力。

## 实施记录

### 2026-08-29 14:42 CST

- 已启动 `task_guard`：`C2-DV7-P81-BSF`，范围锁定为本文件、总索引、C2 patch、MPV build/verify 脚本和四个 App 设置/engine 文件。
- 已创建 baseline tag；尚未修改代码或 lock。

### 2026-08-29 15:18 CST：实现与静态验证

- 已完成 C2 patch：`demuxer-dovi-profile7=p81` 仅在 HEVC/Profile 7 且有效 RPU+BL 配置存在时选择 `dovi_rpu`，同步 `par_out`/Profile 8 参数，复用已有 packet ownership、flush/seek 和错误路径；`preserve`/`hdr10` 逻辑保持。
- 已完成 App 三态：旧 MPV 布尔设置兼容映射为 `hdr10`（默认）/`preserve`，新增显式 `p81`；Exo 两态 API 不变。
- 已通过：`bash .codex/scripts/task_guard.sh check`、`bash -n scripts/build_mpv_native.sh`、`git diff --check`、在现有 HDR10 patch 后的临时 MPV 树上 `git apply --check --recount`。
- Gradle 尝试：`:app:compileDebugJavaWithJavac` 不存在；任务枚举在隔离缓存首次下载 Gradle 时无输出，已中止，待使用项目实际任务或仅记录环境限制。

### 2026-08-29 17:04 CST：首轮实机失败与方案纠正

- 双 ABI native ELF/marker/依赖校验已通过，`:app:assembleMobileArm64_v8aDebug` 成功，ADB 安装成功。
- vivo V2453A（Android 15、设备无 DV 解码能力）选择首轮“升级P8.1”后黑屏；日志持续出现 `hevc_mediacodec: No start code is found`。检查确认 C2 patch 更新了 P8.1 codec parameters，却遗漏让 `mp_dovi_split_filter_base()` 在 `convert_p81` 时替换原 packet，形成 Annex-B 参数与原 NALFF packet 不一致。
- 用户纠正产品策略：删除多余的“保留DV7”选项；设备支持 DV7 时始终原样播放；否则只提供 P8.1/HDR10 两态，P8.1 不支持或失败自动 HDR10。P8.1 作为默认 fallback，重置按钮也必须恢复该默认值。

### 2026-08-29 17:20 CST：C2 收敛修正

- Native packet 过滤必须同时覆盖 `base_only` 与 `convert_p81`；否则 P8.1 codec parameters 已切到 Annex-B，而原始 Matroska NALF packet 仍会送入 MediaCodec，复现黑屏和 `No start code is found`。
- P8.1 请求缺少有效 Profile 7 RPU+BL 配置，或 `dovi_rpu` 不可用时，native 层改用 HDR10 base-layer 过滤，不再保留不受支持的 DV7 bitstream。
- 待完成：应用最终 patch、策略单测、双 ABI native 重建与 `mobileArm64_v8aDebug`/`leanbackArm64_v8aDebug` 构建，然后在非 DV 设备确认默认 P8.1 自动落到 HDR10 且无 `No start code`。

### 2026-08-29 19:59 CST：最终构建与收尾验证

- `bash scripts/verify_mpv_native_assets.sh --require-elf` 通过：`arm64-v8a`、`armeabi-v7a` 的 ELF、SONAME、DT_NEEDED、C2 marker 和资产集合均匹配；使用 NDK `29.0.14206865` 的 `llvm-readelf`。
- `bash ./gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug --no-daemon` 通过，耗时 3 分 43 秒。产物：`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`（SHA-256 `2c5b6ef8b0e73d9b79b95f9b655bc5fa619018d2cdd3384ec373037fa97271e6`）和 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`（SHA-256 `3867a8c7fb352815c7bfe4882301aefdd88fe6cc88c648ad9b03353c6ccdd392`）。
- `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.engine.MpvDolbyVisionFallbackPolicyTest --no-daemon` 通过，耗时 1 分 26 秒；覆盖原生 DV7 优先、P8.1 能力门和 HDR10 回退策略。
- 尝试 `adb devices -l` 和 `adb connect 192.168.1.9:5555`；当前无在线设备，连接返回 `Connection refused`，因此未宣称本轮实机播放通过。此前首轮黑屏根因及修正已由日志和静态 patch 检查确认，最终包仍需设备在线后验证默认 P8.1/HDR10 实际解码路径。

### 2026-08-29 20:00 CST：提交与恢复锚点

- C2 原子提交：`ae337b81e44657d85050bee3a9f92a780fb418ab`（`mpv: complete DV7 to P8.1 fallback integration`）。
- 恢复 tag：`recovery/C2-DV7-P81-BSF/20260829200055-ae337b81e446`。
- 状态：实现、静态验证、native 资产验证、双端 arm64 包构建和策略单测已完成；实机验证因设备离线保留为明确剩余风险。回滚锚点为 `upstream/mpv/c2-dv7-p81-bsf-baseline-20260829`。

### 2026-08-29 20:41 CST：电视端 P8.1 启动卡死证据与修复决策

- TCL Smart TV Pro（Android 14/API 34，MT9655，arm64-v8a）使用 MPV、自动模式、硬解、默认“升级 P8.1”播放 `/storage/emulated/0/Download/P7_FEL_GIJoe_The_Rise_of_Cobra.mkv` 时复现卡死。
- 直接证据：`/private/tmp/c2-tv-card-freeze-latest.log` 中 `20:41:16.858` 起主线程 watchdog 连续报告 `native=get-string:current-tracks/sub2/id`，耗时增长到 31 秒；栈固定为 `MpvPlayer.refreshTracks -> syncOsdSurfaceRequirementFromMpv -> MPVLib.getPropertyString`。没有 Java/native 崩溃或 MediaCodec fatal error。
- 结论：`refreshTracks()` 在主线程无条件读取字幕 current-track；本次样片启动期间 MPV core 正忙于 P8.1 demux/BSF 工作，`current-tracks/sub2/id` 的同步读取把内部等待放大成 UI 卡死。该属性不是播放必需状态，且 direct output 默认 `sid=no`、`secondary-sid=no` 时无需读取。
- 采用窄修复：新增 `MpvOsdSurfacePolicy.needsCurrentTrackQuery()`；两个字幕选择都明确禁用时直接关闭 OSD 请求并跳过 current-track 查询；`auto`、已选字幕和未知选择仍查询真实 current-track。`secondarySubtitleTrackId()` 同步遵循该规则，避免 debug/轨道快照再次触发同一阻塞。
- 不修改 `dovi_rpu`、FFmpeg lock、MPV native 资产、解码器/渲染器选择或字幕菜单数据流；因此不增加包体积，不改变正常播放帧路径，回滚为本阶段单个 Java/测试提交。
- 当前验收状态：策略单测和 Mobile arm64 Java 编译均通过；Leanback arm64 Debug APK 已构建（156,646,041 bytes，SHA-256 `74f3d70d9ccb5ab8edd09ead456706d661020f752d2a267cab23c91b9b6a7d0e`）。电视 ADB（`192.168.1.5:5555`）仍返回 `Connection refused`，所以 P7 FEL 自动模式实机回归尚未宣称通过。
- 待设备上线后的最小验收：同一 P7 FEL 样片自动模式不再出现主线程 `current-tracks/sub2/id` 长时间阻塞；普通 P5/DV5、字幕 `auto`/已选路径保持可播放；日志无 Java/native crash、ANR 或 decoder fatal error。

### 2026-08-29 22:04 CST：原生 P8.1 能力确认与窄修复实施范围

- 用户确认：同一 TCL MT9655 电视播放原生 P8.1 样片正常，排除“电视不支持 P8.1 硬解”的解释。
- 结论：当前故障限定为 DV7 FEL 经 FFmpeg `dovi_rpu convert=p81` 后的输出，或该输出在启动期间触发的 native 等待；不能通过删除 P8.1 选项或改变普通 P8.1 解码路径解决。
- 批准的窄修复：在配置为 `p81` 的 MPV 会话中，文件加载到首次播放重启前不执行同步 `track-list/*` 刷新；若现有播放超时仍没有首帧，调用现有一次性 HDR10 回退并重建播放。已产生首帧的 P8.1、原生 DV7 和直接 HDR10 不触发该回退。
- 不在本阶段处理：重新设计 `dovi_rpu`、替换 FFmpeg/MPV lock、修改 Exo、改变 Vulkan/Surface 策略、异步重写全部 MPV 属性 API。
- 验收重点：普通 HDR10/DV5 轨道菜单和音频选择仍在首帧后刷新；P8.1 失败时主线程保持可响应并自动切 HDR10；回退仅发生一次，seek/换源/退出不改变既有生命周期。

### 2026-08-29 22:26 CST：窄修复验证

- 代码结果：`MpvPlayerConfig` 增加 P8.1 启动期轨道刷新门控；`MpvPlayer` 仅对 P8.1 会话延后 `FILE_LOADED`/启动期 `track-list/*` 同步查询，收到 `PLAYBACK_RESTART` 后恢复刷新；`restoreVideoTrackSelection` 同样受门控保护。普通 MPV 会话保持原有立即刷新路径。
- 回退结果：`PlayerManager.onPlaybackTimeout()` 在无首帧且当前为 MPV P8.1 时调用现有一次性 `prepareDv7P81Hdr10Fallback()`，重建为 HDR10；已出首帧、原生 DV7 或已回退会话不会重复触发。
- 定向单测：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvTrackRefreshPolicyTest --tests com.fongmi.android.tv.player.engine.MpvDolbyVisionFallbackPolicyTest --no-daemon` 通过，耗时 3 分 1 秒。
- Java 编译：`bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon` 通过，耗时 2 分钟。
- 电视包：`bash ./gradlew :app:assembleLeanbackArm64_v8aDebug --no-daemon` 通过，产物 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，大小 156646041 bytes，SHA-256 `e801f04e9e57baa3a537eb542b1b4a5b69f7e1c59993ac89dac4c56c6ff1379e`。
- 环境限制：`adb devices -l` 当前无在线设备，未宣称电视实机回归通过；原生 P8.1 正常是用户实测事实，转换 P8.1 的设备接受度仍需在设备上线后验证。

### 2026-08-30 00:24 CST：电视再次复现与章节查询门控

- 电视日志快照：`/private/tmp/c2-tv-live-full.txt`。TCL MT9655、MPV 自动模式、硬解、默认升级 P8.1 播放同一 P7 FEL 文件时，`FILE_LOADED` 后主线程 watchdog 从 `00:04:09.725` 开始报告 `native=get-string:chapter-list`，随后持续到 `00:04:35.333`，最长 `27113ms`。
- 调用栈固定为 `MpvPlayer.refreshChapters -> stringProperty("chapter-list") -> MPVLib.getPropertyString`；这证明上一轮只延后 `track-list/*` 仍不足，`FILE_LOADED` 的同步章节读取才是当前卡死触发点。`chapter`/`chapter-list` 属性事件和手动刷新入口也可能在同一启动窗口触发同步读取。
- 窄修复：P8.1 启动窗口内统一跳过 `refreshChapters()`、`handleChapterListProperty()` 及对应属性事件；首次 `PLAYBACK_RESTART` 后由既有延迟轨道刷新任务补做一次章节刷新。普通 MPV 会话、原生 DV7、直接 HDR10 和字幕/章节在首帧后的路径不变。
- 未修改 FFmpeg/MPV native、`dovi_rpu`、Vulkan、解码器、Surface 或默认策略；无新增依赖和包体积变化。
- 验证：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvTrackRefreshPolicyTest --tests com.fongmi.android.tv.player.engine.MpvDolbyVisionFallbackPolicyTest :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon` 通过（57 秒）；`git diff --check` 通过。
- 设备限制：本次读取时 `http://192.168.1.5:9978/debug/logs.txt` 暂不可连接，电视 ADB 仍离线；因此只记录日志根因和静态/编译验证，不宣称电视实机回归已通过。
- 当前状态：代码提交 `a83fe86baa245f58c1f5143584c1a5ceaa348530`，恢复 tag `recovery/C2-DV7-P81-BSF/20260830003041-a83fe86baa24`；设备重新上线仅需复测同一 DV7 自动模式，确认不再出现 `chapter-list` 长时间 JNI 阻塞。

### 2026-08-30 00:31 CST：提交收尾

- 原子代码提交：`a83fe86baa245f58c1f5143584c1a5ceaa348530`（`fix(mpv): defer startup chapter metadata for DV7 P8.1`）。
- 恢复 tag：`recovery/C2-DV7-P81-BSF/20260830003041-a83fe86baa24`。
- 验证记录：C2 定向单测、Mobile arm64 Java 编译和 `git diff --check` 已在提交前通过；未重复执行构建。电视端仍无 ADB/live 日志连接，实机回归保留为唯一剩余风险。

### 2026-08-30 06:36 CST：native 失败链、离线码流对照与上游复核

- 复现 `p-de6eo9-1` 的顺序已收敛：`dovi_rpu` 转换包持续送入 MediaCodec，但没有视频输出；FFmpeg 的 packet-property 队列达到 256 条后丢弃，随后出现 `Pending dequeue input buffer request cancelled`、`Invalid to call at Released state` 和 `hevc_mediacodec: Failed to dequeue output buffer`。主线程之后才阻塞于 `demuxer-cache-state/reader-pts`，最长超过 145 秒，因此同步属性查询不是码流失败根因，但会阻止 15 秒 HDR10 回退任务执行。
- 离线样片 `/private/tmp/c2-p7-full.mkv` 的 27--30 秒区间与转换结果 `/private/tmp/c2-p81-27s.mkv` 已逐 packet 解析：原始 72 个 packet 和转换后 62 个 packet 都恰好包含一个 type-62 RPU；转换后 type-63 EL NAL 为 0。重复 RPU 假设被否定。
- 电视上已验证正常的原生 P8.1 样片 `P81_GlassBlowing2_3840x2160@59_94fps_15200kbps.mkv` 前 57 个 packet 与转换结果都把 RPU 放在 VCL 之后，因此 RPU NAL 顺序假设也被否定。
- 转换结果的 codec side data 仍含 `AV_PKT_DATA_HEVC_CONF`，而其 DOVI config 已是 `profile=8, el_present=0`；FFmpeg 自带 `dovi_split` 在删除 EL 后会明确移除该 side data。该输出元数据不一致需要后续修正评估，但当前 MediaCodec 初始化代码只直接读取 `AV_PKT_DATA_DOVI_CONF`，所以尚不能把残留 `HEVC_CONF` 宣称为零输出根因。
- `FongMi/FFmpeg` 当前 `release-9.0-fongmi` 头为 `5e6ba5e987284d8ecb6dc25d2d3fd45d309f3fdd`。其 P7->P8.1 提交 `86b827daa9401f781f8660ea511a2cae0baa2833` 与锁定 `177f090e0503b7e013922ca903bde14b1c375f18` 的 stable patch-id 均为 `c3e9cba49f8bbe3c1d0ea7ab15868198b75d18bd`，`dovi_rpu.c/.h` 最终行为无差异；新头只以 `5e6ba5e987284d8ecb6dc25d2d3fd45d309f3fdd` 公开 RPU parser API，没有修复 BSF 输出或 Android MediaCodec 零输出。
- `FongMi/mpv` 当前 `fongmi` 头为 `f70b385f57ad930a298f8b54e0199ce17c4f4ad3`，分支已强制重落基。近期相关提交 `c318236b8882af860f16f936225430ad053a2179` 只在独立 enhancement-layer stream 被标记 absent 时解除 BL/EL pairing；`e8673660ab7ee5d4ea8f93e4bf3a6e170ab2a19a` 只保留 GPU peak-detection HDR metadata，均不触及当前 `dovi_rpu` 到 Dolby MediaCodec 的初始化和零输出链。
- `FongMi/mpv-android` 当前 `fongmi` 头为 `e1a1f75106afefa6fb3ec9aa6c9ca081155486dd`；该唯一新提交只导出 libplacebo/shaderc renderer SDK 与来源清单，没有 JNI、MediaCodec、Surface 或播放行为变更。
- 最小下一步不是修改 native，而是只在 C2 P8.1 会话把 mpv 的 `ffmpeg` 与 `ffmpeg/video` 日志提升到 info。锁定 FFmpeg 已存在 `Dolby Vision profile %u detected` 与 `MediaCodec started successfully: codec = %s` 两条初始化日志；一次复现即可区分“错误 MIME/profile/codec 选择”和“正确 Dolby codec 收到转换流后拒绝输出”。普通 MPV 会话继续使用 `all=warn`，无播放路径、性能或包体积变化。
- 诊断包已通过 `:app:assembleLeanbackArm64_v8aDebug`，构建耗时 2 分 16 秒；APK 为 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，大小 156646041 bytes，SHA-256 `e7bf658078a0ad059373d84c1b20ea274737b16539259908b117838df7436235`。06:44 之后观测到的新播放走的是 Exo 而非 MPV，尚未产生本诊断包的 codec 初始化证据；下一动作保持为安装该包并用 MPV 复现同一 P7 FEL 一次。

### 2026-08-30 07:01 CST：最新 MPV 复现与日志接收门槛

- 完整快照为 `/private/tmp/c2-tv-latest-repro.txt`，最新有效 MPV trace 为 `p-dg4unu-1`。日志记录 `requested=opengl`，但自动模式最终为 `direct=true`、`actual=surface/mediacodec_embed`；因此本次不是 OpenGL GPU renderer 卡死，界面中的请求模式不能代替实际输出路径。
- `06:49:04` 已收到 `FILE_LOADED`；`06:49:05.106` 起连续出现 `Dropping unmatched MediaCodec packet properties`，`06:49:05.482` 首次出现 `Invalid to call at Uninitialized state`，随后 `hevc_mediacodec: Failed to dequeue output buffer`。全程没有首帧。`reader-pts` 同步读取约 3 秒后才开始阻塞，并从约 46 ms 增长到 40 秒以上，继续证明解码器失败在先、Java 属性阻塞在后。
- P8.1 会话的 `msg-level=all=warn,ffmpeg=info,ffmpeg/video=info` 已生效到 mpv client 请求链；未看到 `Dolby Vision profile %u detected` 的直接原因是 `MpvPlayer.shouldDebugLogMpvLine()` 没有放行该文本。`MediaCodec started successfully: codec = %s` 本应因包含 `codec` 被现有筛选放行，但本次没有出现，说明 MediaCodec 尚未成功启动，不能再按“已启动后零输出”描述本次状态。
- 四个遗留轮询曾同时每 1--2 秒下载约 5 MB 完整日志，已停止并改为单个 8 秒轮询，避免调试服务负载干扰复现。该清理不改变 App 或电视播放配置。
- 已把 `androidx/media3/mpvplayer/MpvPlayer.java` 纳入本诊断单元，只放行 `Dolby Vision profile` 初始化行；`bash ./gradlew :app:assembleLeanbackArm64_v8aDebug --no-daemon` 通过，用时 1 分 54 秒。APK 为 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，大小 156646041 bytes，SHA-256 `08a48bf18b7a27f284897952191f1a806970a568463a927dbe3f1cd0ae1d78e0`。
- 唯一下一动作：在电视安装该诊断包并用 MPV 自动模式复现同一 P7 FEL 一次，从单个后台轮询采集最终 profile/codec 证据；在该证据前，仍不修改 BSF、CSD、硬解、Surface、OpenGL/Vulkan、默认策略或上游 lock。

### 2026-08-30 07:32 CST：厂商 Dolby decoder 释放顺序确认

- 本次完整日志已冻结为 `/private/tmp/c2-tv-user-repro-20260830.txt`，有效 MPV trace 为 `p-dhezv2-1`，播放文件为 `/storage/emulated/0/Download/P7_FEL_GIJoe_The_Rise_of_Cobra.mkv`。
- 自动模式实际为 `direct=true`、`surface/mediacodec_embed`。`07:24:56.408` FFmpeg 识别转换结果为 Dolby Vision profile 8；`07:24:56.421` 厂商 decoder `c2.mtk.dvhe.st.decoder` 启动成功，排除错误 MIME、错误 profile、错误 decoder 和 OpenGL/Vulkan 选择。
- 全程没有首帧。`07:24:57.716` 起连续出现 `Dropping unmatched MediaCodec packet properties`；`07:24:58.027` 出现 `Pending dequeue input buffer request cancelled`，随后 codec 已处于 `Released`，再出现 `hevc_mediacodec: Failed to dequeue output buffer`。主线程之后才阻塞于 `demuxer-cache-state/reader-pts`，最长超过 130 秒，因此 JNI 查询是卡死放大点，不是 decoder 释放的起因。
- 诊断日志已完成使命，临时 P8.1 FFmpeg verbose 配置与 Java 文本放行不进入生产修复。当前单变量假设是：`dovi_rpu convert=p81` 删除 EL 并改写 DOVI config 后仍保留表示增强层配置的 `AV_PKT_DATA_HEVC_CONF`；FFmpeg 自带 `dovi_split` 对同类输出明确删除该 side data，并注明输出端已失效。
- 下一动作：只在 P8.1 转换的 `par_out` 删除 `AV_PKT_DATA_HEVC_CONF`；不改变 RPU 内容、extradata、packet、codec/MIME、Surface、GPU、Java 回退和其他播放模式。验收是同一文件产生首帧且不再进入厂商 decoder `Released`；失败则立即撤销该假设。

### 2026-08-30 07:38 CST：失效增强层配置单变量候选

- 在 `third_party/patches/mpv-dovi-profile7-p81.patch` 中，`dovi_rpu` 初始化完成后、`par_out` 复制给 MPV decoder 参数前，仅对 `convert_p81` 删除 `AV_PKT_DATA_HEVC_CONF`。该 side data 的公开语义是 Dolby Vision 增强层 HEVC decoder configuration，而当前转换已删除全部 type-63 EL NAL。
- 没有修改 `AV_PKT_DATA_DOVI_CONF`、主 HEVC extradata、RPU 重写、packet 数据、MIME/profile/codec 选择、Surface/GPU、Java 回退或其他 DV/HDR 模式。新增 verbose marker 仅用于证明构建资产包含该候选，默认 `all=warn` 不输出。
- 静态验证通过：新 C2 patch 在锁定 MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 及既有 HDR10 patch 后通过 `git apply --check --recount`；`bash -n` 两个 native 脚本和 `git diff --check` 通过。
- 回滚锚点：`e343efd2e544c9957a0fe5d825e2cb81b5972d5d`，恢复 tag `recovery/C2-DV7-P81-BSF/20260830073401-e343efd2e544`。未验证候选不得提交或标记为修复。
- 下一动作：只重建并安装 Leanback arm64 候选，用同一 P7 FEL 文件验证首帧与 decoder 生命周期；通过后再补 armeabi-v7a 资产。

### 2026-08-30 08:03 CST：arm64 单变量候选完成，等待安装

- `scripts/build_mpv_native.sh --abi arm64-v8a --install --incremental --jobs 8` 已完成；新 arm64 `libmpv.so` 已确认包含 `DV7 P8.1 conversion: removed stale enhancement-layer configuration.`。包装构建同时改写的七个无关 FFmpeg 资产均已恢复到 `e343efd2e544c9957a0fe5d825e2cb81b5972d5d`，候选包只保留 C2 `libmpv.so` 单变量。
- 全资产 `--require-elf` 校验对 arm64 已通过，随后按阶段设计在尚未重建的 armeabi-v7a marker 处停止；这不是 arm64 候选失败。task guard 在恢复无关资产后通过。
- `bash ./gradlew :app:assembleLeanbackArm64_v8aDebug --no-daemon` 一次通过，耗时 1 分 17 秒。APK 为 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，SHA-256 `acc1cc698a556654e969de75b2530bad115ee21179bc522acf10d74554758870`。
- 用户在候选构建完成前进行的播放仍使用电视旧包；最新日志中的持续播放 trace `p-did5nt-3` 是网络 H.264，最近一次 DV7 trace `p-di77tq-1` 又在启动后约一秒主动销毁，均不能验证本候选。
- 安装阻塞：`adb connect 192.168.1.5:5555` 返回 `Connection refused`；电视 `9978` 调试服务没有 APK 安装入口，因此候选尚未安装，禁止继续用旧包重复复现并据此判断修复结果。
- 用户新增后续要求：优化 native 构建脚本，避免 `--incremental` 仍逐个重跑 nghttp2、curl、libarchive 等未变化依赖。该优化应在 C2 播放修复收尾后使用新的稳定任务 ID、独立 guard/提交/恢复 tag 实施；设计采用输入指纹和已验证产物共同决定跳过，lock、patch、override、NDK、ABI 或构建参数变化必须使缓存失效，不能仅按文件存在跳过。
- 唯一下一动作：电视开启无线 ADB、使 `192.168.1.5:5555` 可连接后，安装上述候选 APK，清空日志并只用 MPV 自动模式播放 `/storage/emulated/0/Download/P7_FEL_GIJoe_The_Rise_of_Cobra.mkv` 一次；验收首帧和厂商 decoder 是否保持运行。

### 2026-08-30 08:08 CST：电视 arm64 实机通过

- 用户手动安装 SHA-256 `acc1cc698a556654e969de75b2530bad115ee21179bc522acf10d74554758870` 的 Leanback arm64 候选，并使用 MPV 自动模式播放 `/storage/emulated/0/Download/P7_FEL_GIJoe_The_Rise_of_Cobra.mkv`；用户确认画面正常且显示为转换后的 DV8。
- 冻结日志为 `/private/tmp/c2-tv-hevc-conf-candidate-20260830.txt`，有效 trace `p-diwh05-1`。`08:06:37.112` 收到首帧，首帧耗时 7604 ms；随后播放位置推进到 17081 ms，`dropped=0`、`rebufferCount=0`，最终由用户正常停止，`end-file reason=stop error=success`。
- 本次没有 `Dropping unmatched MediaCodec packet properties`、pending dequeue 取消、decoder `Released`、output dequeue 失败、`reader-pts` JNI 阻塞或 P8.1->HDR10 自动回退。与旧包同一样片的失败链形成直接对照，支持“删除失效 `AV_PKT_DATA_HEVC_CONF` 修复 MT9655 厂商 Dolby decoder 接受度”的结论。
- 当前恢复点只声明 Leanback arm64 实机通过；armeabi-v7a 资产尚未补入该 marker，双 ABI 完整校验和 C2 最终收尾仍待后续独立阶段完成。
- 用户要求先固化当前已验证状态，再单独优化播放参数面板文案为 `DV7（升级P8.1）`，与 `DV7（降级HDR10）` 的显示风格一致。

### 2026-08-30 08:34 CST：起播音画间隔与参数面板修复决策

- 用户确认输入是电视本地文件；网络读取、代理和带宽不足不适用。三次转换 P8.1 复现中，`PLAYBACK_RESTART` 后约 4--6 秒才出现 App 的 `first-frame`，但该事件实际由延迟轨道刷新后的 `onTracksChanged` 推断，不是真实 Surface 帧回调，不能据此修改解码或音频时序。
- 锁定 FFmpeg 的 `dovi_rpu` 保留首视频包的 key flag；样片首包为 `pts=0`、`K__`，转换 10 秒仅耗时 0.83 秒（host 对照），首帧仍为独立 RPU。历史 `Unknown previous RPU ID` 只出现在停止/重配后的晚期日志，因此“首 RPU 错误引用前态”被否定，不实施强制 key 或禁用全部 RPU 压缩。
- 参数面板的目标以用户提供的 Exo 实机图为准：`格式 Dolby Vision DV.07`，`codec dvhe.07.06（升级P8.1）`。当前 Java formatter 已符合该文本，但 MPV C2 在 native 中把 `dv_profile` 改为 8 后，`track-list` 只暴露转换后的身份；必须同时保留源 profile/level，不能根据设置项猜测，否则原生 P8.1 文件会被误标为 DV7 转换。
- 方案比较：不改会保留音画体验问题和错误面板；直接使用上游转换结果无法提供源身份，也没有真实首 Surface 输出证据；WebHTV 窄适配是在 C2 splitter 中保存源 DV profile/level、通过现有 `track-list` 增加只读属性，并在 direct MediaCodec VO 每会话只记录一次首输出提交时间。它不改变 packet、RPU、CSD、解码器、Surface 调度或音频时序，诊断开销为一次日志。
- 推荐：先实施上述源身份和一次性首输出证据；同一 arm64 包即可同时验证面板和定位起播延迟。若首输出本身晚，修复 decoder/VO 前的已证实阻塞；若首输出及时而屏幕仍黑，只在 P8.1 转换会话设计首画同步门，禁止用固定全程 audio-delay。任何最终时序修复仍不得影响原生 DV7、原生 P8.1、HDR10、GPU 输出或 seek/flush。
- 验收：MPV 面板与 Exo 图一致；同一本地样片记录 `PLAYBACK_RESTART`、首 direct MediaCodec output 和用户可见首画；无 decoder `Released`、packet-property 丢弃、JNI 长阻塞、HDR10 意外回退或新增掉帧。回滚到 `b1f07cea3d36b8207ac0d518b837e295cb212323` 及 `recovery/C2-DV7-P81-BSF-CSD/20260830081107-b1f07cea3d36`。

### 2026-08-30 09:58 CST：参数面板候选引入 GPU 软解回归及修正

- 用户实测首个面板候选播放严重卡顿。冻结证据 `/private/tmp/c2-tv-stutter-20260830.txt` 显示会话最初保持 `surface/mediacodec_embed` 和 MediaCodec 硬解，但源身份接线错误地把运行时 Profile 8 覆盖为 Profile 7；自动输出策略随后记录 `dvProfile=7`、`dvSupport=UNSUPPORTED`、`transition=LEAVE_SURFACE_DIRECT`，并以 `auto-dolby-vision-hw-unsupported` 重建为 `vo=gpu / hwdec=no`，掉帧增长到 89。该回归不是电视性能波动，而是本候选的数据边界错误。
- 修正后 `VideoTrackDiagnostics` 同时保留运行时 Profile 8 和源 Profile 7/Level 6；`MpvPlayerEngine` 仅用源 Profile 判断面板的 `（升级P8.1）` 标记，所有播放能力、自动输出和硬解策略继续读取运行时 Profile 8。`DolbyVisionFormatLabel` 从源 codec 生成 `Dolby Vision DV.07`，因此面板显示不再改变渲染决策。
- 本次修正没有修改 `PlayerManager`、自动输出策略实现、解码器选择、Surface/VO、BSF packet/CSD/RPU、音频或起播时序。现有 native 候选仅增加源 profile/level 的只读轨道属性和一次性首 MediaCodec output 诊断，用于面板与黑屏根因取证。
- 定向验证通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.DolbyVisionFormatLabelTest :app:assembleLeanbackArm64_v8aDebug --no-daemon`，耗时 1 分 33 秒。Leanback arm64 Debug APK 为 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，大小 174363865 bytes，SHA-256 `4f98512b119f1da83ef64c462bea0ce652023a42e82104d4b6abdc4b3db041cb`。
- 当前状态：尚未提交或创建恢复 tag；等待用户手动安装该包复测同一 DV7 自动/P8.1 场景。验收必须保持 `vo=mediacodec_embed`、`hwdec=mediacodec`，不得再次出现 `LEAVE_SURFACE_DIRECT` 或 `auto-dolby-vision-hw-unsupported`，且面板显示源 DV7 与 `（升级P8.1）`。
- 唯一下一动作：用户安装上述 APK 并播放同一样片后，读取一次电视日志确认硬解直通与掉帧计数，再决定是否进入提交/tag 收尾。

### 2026-08-30 10:08 CST：面板修正版电视实机验收

- 用户手动安装 SHA-256 `4f98512b119f1da83ef64c462bea0ce652023a42e82104d4b6abdc4b3db041cb` 的 Leanback arm64 Debug APK，并以 MPV 自动/P8.1 模式播放同类本地 DV7 样片；冻结日志为 `/private/tmp/c2-tv-live-panel-fixed.txt`，有效 trace 为 `p-dn3rgx-4`。
- 播放从始至终保持 `vo=mediacodec_embed`、`hwdec=mediacodec` 和 `mpv-surface-direct`；自动策略结论为 `dolby-vision-hw-supported`。统计 `LEAVE_SURFACE_DIRECT=0`、`auto-dolby-vision-hw-unsupported=0`、`vo=gpu=0`、`hwdec=no=0`，确认此前 GPU 软解回归已修复。
- direct MediaCodec 首个输出在加载后约 `759283 us` 提交，启动缓冲约 `988 ms` 后进入 READY；播放期间掉帧从 0 增至 1 后保持不变，`rebufferCount=0`。位置停在 `33033 ms` 是用户手动暂停，已排除为播放停滞。
- 当前原子单元可以提交并创建恢复 tag。后续“先有声音、后出画面”仍作为 C2 的下一个独立修正单元处理：现有证据已经证明 decoder/VO 在约 0.76 秒提交首输出，而 App 的 `first-frame=7165 ms` 来自延迟轨道刷新后的 `PLAYBACK_RESTART` 推断，不能把两者的差值直接解释为真实黑屏时长，也不能据此增加固定音频延迟。
- 唯一下一动作：提交并标记当前面板/源身份/诊断单元；随后从本次真实 MediaCodec 输出证据与用户可见首画观察之间建立可证伪的时序，再决定最小代码修复。

### 2026-08-30 10:15 CST：先有声音后出画面的根因与修复设计

- 根因已由同一 trace `p-dn3rgx-4` 的完整时序证实：direct MediaCodec 在 `10:04:09.349` 提交首个视频输出，MPV 于 `10:04:09.370` 发出 `PLAYBACK_RESTART` 并在 `10:04:09.378` 进入 READY；但 App 的自动输出 shutter 仍因 `mpvAutoOutputEvaluated=false` 保持 Surface alpha=0/黑色遮罩，直到延迟轨道刷新完成并于 `10:04:15.607` 得出 `KEEP_SURFACE_DIRECT` 后才调用 output-ready。可见画面被 App 额外遮住约 6.26 秒，期间音频正常播放，因此形成“先有声音、后出画面”。
- 这不是 decoder、BSF、CSD、RPU、Surface 提交或音频同步变慢。`firstFrameMs=7165` 是 `markStartupCompletion()` 等待非空 tracks 后的延迟推断；它不代表真实视频首输出。此前 P8.1 失败 trace `p-de6eo9-1` 没有 `PLAYBACK_RESTART`，随后直接进入 packet-property 丢弃和 MediaCodec dequeue 失败，因此 READY/restart 可区分本次成功输出与既有失败回退路径。
- 方案比较：不改会固定保留约 6 秒黑遮罩；增加 `audio-delay` 会破坏全程 A/V sync、seek 和暂停恢复；恢复启动期同步轨道查询会重新引入电视主线程 JNI 卡死；等待完整自动评估仍是当前缺陷。采用窄适配：自动 MPV Surface Direct 在 `PLAYBACK_RESTART/READY`、视频尺寸有效且输出尚未评估时，把该帧作为运行时成功证据，立即打开 shutter并记录真实首帧；完整自动输出评估继续后台运行，若后续确需重建仍走现有 reset/pending 流程。
- 保留合同：不修改解码器、`vo`/`hwdec`、native binary、BSF/CSD/RPU、音频时序、轨道刷新门控或失败回退。音频-only、GPU 输出、未 READY、无有效视频尺寸和已经评估的会话不得提前打开；失败的 P8.1 因没有 `PLAYBACK_RESTART` 继续保持 shutter 并等待现有 HDR10 fallback。
- 验收：定向策略单测覆盖允许/拒绝条件；Leanback arm64 构建通过；电视同一样片的 shutter/output-ready 应从约 7 秒提前到 READY 约 1 秒附近，同时保持 `mediacodec_embed`/`mediacodec`、无 GPU transition、无 rebuffer，掉帧不高于当前候选的稳定水平。
- 回滚：恢复 `3db8252c95ad23096db58d6adb75c094565a33bc` 或 `recovery/C2-DV7-P81-BSF-PANEL/20260830100826-3db8252c95ad`；本单元只修改 App Java、策略单测和本文件。

### 2026-08-30 10:22 CST：shutter 提前释放候选实现与构建

- `PlayerManager` 新增会话级 `mpvAutoOutputFrameReady`：自动 MPV Surface Direct 收到 READY（其来源为 `PLAYBACK_RESTART`）且已有有效视频尺寸时，立即调用 output-ready、打开 shutter，并将该时刻记录为 MPV 首帧。轨道列表稍后刷新时不会重复记录首帧。
- `MpvAutoOutputPolicy.canRevealDirectFrame()` 集中约束允许条件；未 READY、GPU 输出、无视频尺寸、非自动模式或已经完成输出评估的会话均拒绝提前打开。完整 `evaluateMpvAutoOutput()` 仍继续运行，重建时既有 reset/pending 会重新关闭 shutter。
- 定向验证通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicyTest :app:assembleLeanbackArm64_v8aDebug --no-daemon`，耗时 1 分 45 秒。没有修改或重建 native 资产。
- Leanback arm64 Debug APK：`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，大小 174363865 bytes，SHA-256 `8c2c6bf0394e60619d62df07a68d4be6ef3257680839292007b67e40b5da2c6f`。
- 当前状态：候选尚未提交/tag。唯一下一动作是用户手动安装该 APK 并播放同一 DV7→P8.1 样片；日志必须先出现 `MediaCodec first output submitted`/READY，紧接 `auto shutter release reason=direct-playback-restart`，后续自动决策仍为 `KEEP_SURFACE_DIRECT`，且无 GPU transition、rebuffer 或异常掉帧。

### 2026-08-30 10:30 CST：电视实机验收与收尾

- DV7→P8.1 有效 trace 为 `p-dnwxvd-4`：direct MediaCodec 在 `10:26:50.953` 提交首个输出，App 在 `10:26:51.051` 记录首帧并于 `10:26:51.055` 打开 shutter，首帧总耗时 `1603 ms`。完整自动输出评估直到 `10:26:56.422` 才结束，但画面已提前约 5.37 秒显示；最终保持 `mpv-surface-direct`、`hwdec=mediacodec`、`vo=mediacodec_embed`，结论为 `dolby-vision-hw-supported`。
- 该样片继续播放至 EOF，位置推进到 `57099/57140 ms`，`dropped=0`、`rebufferCount=0`、`rebufferTotalMs=0`，`end-file reason=eof error=success`。没有 GPU/软解切换、MediaCodec 释放失败、JNI 长阻塞、Java/native 崩溃或 HDR10 意外回退。
- 随后一轮 trace `p-dnyatr-6` 同样保持硬解直出：MediaCodec 首输出后约 `56 ms` 打开 shutter，首帧总耗时 `1021 ms`；完整策略评估稍后保持 Surface Direct，播放期间 `dropped=0`、`rebufferCount=0`。这同时覆盖了非首次会话的状态重置与普通自动输出路径。
- 验收结论：修复已消除 App 自身额外维持 5--6 秒黑遮罩的问题，不改变解码、渲染、音频同步、DV 转换或失败回退路径。回滚仍为 `3db8252c95ad23096db58d6adb75c094565a33bc` 或 `recovery/C2-DV7-P81-BSF-PANEL/20260830100826-3db8252c95ad`。
- 收尾结果：实现、测试和实机证据已提交为 `fa6a9ab2df507f1e223225efbabd2b2978dfafcb`（`fix(mpv): reveal direct video at playback restart`），并在提交后立即创建 annotated recovery tag `recovery/C2-DV7-P81-BSF-FIRST-FRAME/20260830103126-fa6a9ab2df50`；tag 创建耗时 `0s`。
- 下一动作：本原子单元已完成；不重复构建，不推送远端。后续 C2 工作必须另开独立 guard、提交和恢复 tag。

### 2026-08-31 22:26 CST：补齐 C2 P8.1 的 armeabi-v7a 已验证资产

- CI 运行 `Silent1566/webhtv` 的 `33392660102`（job `99489724919`，`beta@b35ff038d26b297e3a1b020c0117cd365b35425f`）在 `verify_mpv_native_assets.sh --require-elf` 的第二个 ABI 失败：`armeabi-v7a/libmpv.so` 缺少 `DV7 P8.1 conversion: removed stale enhancement-layer configuration.`。`b1f07cea3d36b8207ac0d518b837e295cb212323` 仅更新 arm64 产物却把该 marker 加入双 ABI 校验，导致旧 armv7 资产无法满足已提交的锁定 patch 合同。
- 依用户要求优先采用上游精确产物：`fish2018/webhtv@23a3c74417fdcc107ad8efc43ca366482af89e58` 的 `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libmpv.so`。该来源提交只改动该二进制和未采用的 `MpvPlayerEngine.java` 一行；本地与上游的 `third_party/mpv-native-lock.json`、`scripts/build_mpv_native.sh`、`scripts/verify_mpv_native_assets.sh`、`third_party/mpv-native-build.md`、`mpv-dovi-profile7-p81.patch` Git blob 完全一致，因此未引入任何新源码、锁、patch 或 Java 行为。
- 安装产物：Git blob `64701f8a778bb3cfc2edecbcf8f2da204d4828d9`，大小 `14,522,228` bytes，SHA-256 `c2b952f9e6d3fb38399672d74bb9a3607668825c6eaacf40d02a1b8706407753`；ELF 为 `ELF32` / `ARM` / `DYN`，包含所需 P8.1 stale-enhancement-layer marker。仅替换 `armeabi-v7a/libmpv.so`；其余 armv7 资产、全部 arm64 资产及两 ABI 的 `libplayer.so` 均未改动。
- 验证：使用 NDK `29.0.14206865` 的 `llvm-readelf` 和 `llvm-strings` 执行 `scripts/verify_mpv_native_assets.sh --require-elf`，稳定 Vulkan shader contract、P2 generic UV patch scope、`arm64-v8a`、`armeabi-v7a`、ELF `SONAME`/`DT_NEEDED`/命名空间与过期 marker 拒绝规则均通过。
- 回滚：恢复本任务父提交 `119b3aec7345f578a93e1d59bf2a87d0dc61248a` 中的唯一 armv7 `libmpv.so`，或恢复本次 guard 收尾创建的 recovery tag；不需要回滚 C2 P8.1 patch、arm64 产物或任何 App Java 代码。
