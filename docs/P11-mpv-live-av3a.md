# P11：MPV AV3A 直播分片误判修复

## Recovery anchor

- 目标：修复同一 AV3A 直播 Exo 正常而 MPV 加载失败；MPV 应将媒体分片交给已有解码链，保留视频仅手动切换软硬解、音频允许软件解码的合同。
- 基线：`feature/mpv-dv7-fel` / `18ccd0785f04b279759399623adf3e593e86a22e`；回滚 tag `recovery/AV-DIAG-01-live-params/20260917231508-18ccd0785f04`。
- guard：`P11-mpv-live-av3a` / `quick-fix`。范围为 `MpvHlsProxy.java`、`MpvHlsSegmentContentPolicy.java`、对应内容策略测试、本文及任务索引；证据和隔离构建输出位于 `build/av3a-live-fix/`、`build/avs3-native/app-cxx/`。保护原有 `app/.cxx/` 104 个文件，不修改 native、依赖或已验收直播 UI。
- 已证实：手机日志连续出现上游 `code=200`、`invalid nested playlist`，随后 MPV `HTTP error 400`、首分片加载失败。实际清单的 `EXTINF` 后媒体 URI 是 `cctv5.m3u8?ts=…`。旧代码去掉 query 后依 `.m3u8` 后缀判断清单，将数 MB 媒体转为字符串并返回 400；不是解码器初始化失败。
- 手机：`10CF6H1D2L0009S` / vivo V2453A / Android 15 / ARM64；当前媒体为“CCTV5-真低码AV3A”。已取日志和清单，Web 增量监听已启动。电脑直接尝试分片请求返回 403，不把该请求当成手机媒体 HTTP 200 的替代证据。
- 状态：修复、19 项定向测试、Debug 增量构建及快速 Release 构建完成；Release 已安装且设备 SHA 相符。真实 CCTV5 AV3A 直播已正常出画面并持续超过 6 分钟，分片 HTTP 200、视频/音频时间戳持续前进、AudioTrack 写入无错误。Debug 的源内迅雷插件 CheckJNI 崩溃独立保留，不以 Release 成功冒称 Debug 源插件兼容。
- 时间：2026-09-17 23:38 Asia/Shanghai；预计修改 3 分钟、测试/打包 4 分钟、安装实播 6 分钟、收尾 2 分钟，目标 23:53；设备连接不稳时记录实际阻塞。
- 唯一下一动作：guard 原子提交任务文件并创建 annotated 本地恢复 tag，不推送；本次分片误判修复无剩余实施项。

## 证据与最小设计

这是已有 HLS 分片处理设计的局部修正，不增加编解码能力或引入上游提交。用户“mpv播放av3a直播还没修复”“继续”及“exo正常,mpv失败”授权继续该故障修复。

| 证据（访问 2026-09-17） | 支持的结论和边界 |
| --- | --- |
| 本机 `build/av3a-live-fix/initial-logs.txt`；23:22–23:23 的 `mpv-proxy: invalid nested playlist`，上游 HTTP 200、正文 1.8–3.6 MB，紧接 native HTTP 400 | A，代理自身错误分类导致失败；上游已经返回媒体数据，尚未进入 AV3A 解码 |
| 同目录 `source-playlist.m3u8`；根清单 2065 字符、3 个 `EXTINF`，媒体 URI 仍使用 `.m3u8?ts=…` 端点 | A，后缀不能推翻清单已声明的媒体角色；不在文档保存带鉴权参数的完整地址 |
| [RFC 8216 §3、§4.3.2.1](https://www.rfc-editor.org/rfc/rfc8216.txt)，已读缓存 `build/p10-mpv-smart-adblock/research/rfc8216.txt` | A，媒体分片由 URI、可选字节范围及 `EXTINF` 声明；URI 可以是动态端点，不应在取分片时重新按扩展名选择清单解析器 |
| 已锁定 Media3 `e3e922d5c01bc0b564849940fe589daf37360d15` 的本地 HLS sources.jar；`HlsPlaylistParser`、`HlsMediaChunk.createInstance` 114–120 行 | A，Exo 从清单对象创建媒体 `DataSpec`，直接使用 segment URL、byteRangeOffset/Length，不凭分片 URI 的 `.m3u8` 后缀改成加载清单 |
| 当前 `HlsPlaylistRewriter.rewrite` → `UriContext.role` → `MpvHlsProxy.proxyItemUrl` → `Target.role` → `serveItem` | A，语义角色已完整传递，只有消费端忽略它；复用现有结构即可，无需内容扫描、二次请求或重写解析器 |

选择：不改则持续失败；强制所有 `.m3u8` 都走媒体会破坏主清单及变体；按站点或 `ts` 参数特判不可维护。采用现有语义角色：`MEDIA_SEGMENT` 始终按媒体流处理，`VARIANT_PLAYLIST` 始终按清单处理，`OTHER` 保留 URL/Content-Type 的既有推断。

- 请求发出前和响应到达后使用相同判断，避免媒体 Range 被清除、正文被 `body.string()` 消耗或错误记录为播放列表。
- 已由清单解析出的预取分片同样不再按 URL/Content-Type 拒绝缓存；流式写入、大小限制、HTTP 状态及缓存门控不变。
- 无额外网络请求、探测缓冲、逐帧工作或新线程；媒体沿用原字节流。密钥/MAP/其他 URI 的既有推断、广告时间轴、IJK 共用链的其余行为保持。
- 不新增上游/ABI/二进制变更。该问题已有真实日志、规范、成熟 Exo 实现和本地调用链决定方案，无需扩展论文、泛搜 issue 或 native 构建。

## 验证与回滚

1. 定向测试覆盖 `EXTINF` 后 `.m3u8?ts=…`、错误 MIME、字节范围、低延迟分片、无扩展名变体及未知 URI 的旧推断；保留 PNG 包装分片测试。
2. 一次 Mobile ARM64 测试/增量打包；核对 native 条目与既有 AVS3 MediaCodec 基线一致。
3. 安装该包，在实际 AV3A 直播中确认不再出现分片误判、MPV 已识别并初始化 AV3A 音频/视频、持续播放；构建通过不能替代实播。
4. 定向验证通过后同一 guard 原子提交并创建 annotated 本地恢复 tag，不推送。回滚为撤销本单元提交，保留此前直播播放参数入口。

### 2026-09-17 23:48 实施与设备环境

- `MpvHlsSegmentContentPolicyTest` 11 项、`HlsPlaylistRewriterTest` 8 项通过；同次 Gradle 完成 Mobile ARM64 Debug，112 tasks 中 11 执行，56 秒。证据：`build/av3a-live-fix/gradle.log` 和 Gradle JUnit XML。
- Debug APK SHA-256 `1264a29b46945be48710c98955ab5d730bfda191c716817ebdf03861edc8a89c`，181893921 字节；50 个 `.so` 与 `WebHTV-5.6.0-AVS3-MediaCodec-mobile-arm64-debug.apk` 完全一致。OEM 安装确认自动完成，设备 APK SHA 相符。
- 原设备包 `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA LARGE_HEAP ]`，无 DEBUGGABLE。新 Debug 在加载源附带的 `cache/thunder/.../libxl_thunder_sdk.so.arm64-v8a` 时，`XLLOader.init` 的 `CallNonvirtualObjectMethodV` 触发 CheckJNI SIGABRT；发生于媒体清单加载之前。栈、进程退出和启动日志已保存于同目录。不是本轮 Java 分片处理栈，不将该失败计作播放通过，也不修改无关 JNI/插件。
- 为匹配原设备运行环境，增加一次 Release 变体打包/安装，保持标准构建配置；不关闭 Debug 校验、不重编原生库、不重复通过的测试。预计完成时间由 23:53 调整到约 23:57。
- 23:55：完整 Release 的 Java 编译和 lintVital 已完成，R8 优化超过 6 分钟仍未结束。为完成当前局部修复，取消本任务 Gradle 客户端（退出 130），切换仓库已有 `fastRelease` 路径；仅跳过混淆/资源压缩，保留非 Debug 配置和完整功能/原生库。完整 R8 构建未完成，不计作验证通过；不再重复已通过的单测。

### 2026-09-18 交付与真实直播结果

- 快速 Release：`assembleMobileArm64_v8aRelease -PfastRelease=true` 通过，2 分 30 秒；119 tasks 中 12 执行、1 来自缓存、106 up-to-date。完整输出 `build/av3a-live-fix/gradle-fast-release.log`。
- 交付 APK：`app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk`，150043820 字节，SHA-256 `37ead8e3a5a986b669b032e386f2ec94134b7285edb89630585c00aec91b7863`。OEM 确认自动完成，设备 APK SHA 相符；安装记录 `install-release.log`、`installed-release-sha256.txt`。
- 制品核对：50 个 `.so` 中 49 个与 Debug 基线逐字节相同，包括全部 MPV、FFmpeg/AV3A 和 ASS 库；唯一差异是本来由 App CMake 构建的 `libexo_dovi_renderer.so` 的 Release/Debug 变体，源码未变，ELF 依赖和动态导出一致，构建命令及对照证据记录在 `release-apk-identity.json`。没有依赖、补丁或原生源码变更。
- 手机同一路“CCTV5-真低码AV3A”：00:15:38 发起播放，初始状态由缓冲转为播放，8 次采样中后 6 次持续 `state=3`，位置从 2.353 秒到 17.484 秒。00:22:03 同一 trace `p-1ar58nd-1` 的视频位置 381.280 秒、音频 PTS 381.241 秒，清单持续刷新，分片 `code=200 range=bytes=0- mime=video/MP2T`；当前快照无 `invalid nested playlist` 或 HTTP 400。
- `release-playing.png` 可见实际直播画面；面板显示 H.264/AVC、1920×1080、MPV MediaCodec 硬解，AV3A 10ch 音频软件解码至 PCM 2.0/48kHz，MPV Vulkan/gpu-next，A-V 显示 0ms。软件日志的实际 `avsync` 约 0.000015 秒；不将此值当作物理屏幕/扬声器测量。
- AudioTrack 定向证据：2516 次写入，requested/accepted 均为 72444160 字节，short/zero/errors 均为 0；原生播放头持续前进。未自动测量扬声器可听声压，不扩展至其他直播频道或 4K 软件解码性能。
- 证据位于 `build/av3a-live-fix/`：`release-playback-samples.jsonl`、`release-snapshot.json`、`release-logs.txt`、`verified-events.json`、`release-playing.png`。已停止额外构建/测试；提交定位使用 `Task-Guard: P11-mpv-live-av3a` trailer，恢复 tag 前缀 `recovery/P11-mpv-live-av3a/`，实际完整提交/tag 由 guard 输出和 Git 记录提供。
