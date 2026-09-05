# E-SP3：Exo seek 预载隔离与 HLS 预缓存崩溃防护

- 任务 ID：`E-SP3`
- 类别：Exo 性能/生命周期
- 唯一文档：`docs/E-SP3-exo-seek-preload-isolation.md`
- 评估日期：2026-08-27（Asia/Shanghai）
- 当前状态：`E-SP3-A + E-SP3-B` 已实施并形成独立 recovery tag；Media3 产物、App 构建和 vivo 安装已完成，HLS 预缓存崩溃暂未复现，但用户确认普通 seek 后仍有约 1 秒可感知停顿，因此设备验收未通过，任务继续定位。
- 实施边界：只修改 Exo App 预载策略、Media3 `PreCacheHelper` 补丁、锁文件和 `media3-exoplayer` 产物；未修改前台 HLS、解码器、MIME、MPV、FFmpeg、nextlib native 或共享 `.so`。
- 现场设备：vivo V2453A，Android 15 / SDK 35，`com.fongmi.android.tv`，序列号 `10CF6H1D2L0009S`。
- 原始证据：[`/tmp/webhtv-repro-logcat.txt`](/tmp/webhtv-repro-logcat.txt)、用户截图 `/var/folders/ty/xxvjkz4s4pb9mndtj0hys0w40000gn/T/IMAGE 2026-08-27 10:13:59.jpg`。

## 完成目标

消除用户复现的两个 Exo 预缓存问题，同时保持前台播放契约不变：

1. seek 时不让旧预载任务继续占用网络，也不在前台缓冲尚未恢复时立即启动新的预载任务；
2. HLS 预缓存准备阶段遇到 Media3 playlist 生命周期竞态时，不再让异常冒泡导致应用崩溃，并停止该媒体的预载重试；
3. Exo 前台 HLS 播放、解码器选择、字幕、DV/HDR、TrueHD/Atmos、代理 Range 和缓存格式不被这两个修复改变。

## 1. 现场证据与可证伪根因

### 1.1 seek 停顿的日志闭环

两次操作均在同一 Exo preload session 中观察到“取消旧任务很晚、随后又启动新任务”的序列：

| 日志时间 | 事件 | 证据 | 解释 |
| --- | --- | --- | --- |
| 10:14:04.419 | task 1 start | `generation=6`, `buffered=11684`, `loading=true` | seek 前/中的预载仍在运行，前台只有约 12 ms 缓冲 |
| 10:14:06.554 | task 1 cancelled | `reason=seek`, `elapsedMs=2135`, `cachedBytesRead=7291154` | `stopCurrentTask()` 返回后，后台任务仍存活约 2.1 s |
| 10:14:08.397 | task 2 start | `generation=8`, `buffered=9358`, `loading=true`, `waitTotalMs=2036` | seek 后不到 2 s 即重新提交预载；前台缓冲仍不足 |
| 10:14:08.812 | task 2 prepare-error | `HttpDataSourceException` | 失败任务进入当前重试路径 |
| 10:14:08.959 | task 3 start | `generation=8`, `buffered=17087`, `loading=true`, `waitTotalMs=2186` | prepare 失败后约 147 ms 又启动下一次预载 |
| 10:14:10.462 | task 3 cancelled | `reason=seek`, `elapsedMs=1504` | 第二次 seek 仍需等待后台任务退出 |
| 10:14:13.429 及以后 | task 4--12 start/error | 约 5 s 周期，`buffered=9182..51239` | 失败且无进展的预载继续重试，制造额外网络/优先级竞争 |

本地调用链：

- `app/src/main/java/com/fongmi/android/tv/player/exo/PreCache.java:296-304` 在 seek 回调中调用 `stopCurrentTask("seek")`，随后立即 `refillActive = true`、`check()`；
- `PreCache.java:503-508` 的 `stopCurrentTask()` 只能调用 `PreCacheHelper.stop()`；
- Media3 `PreCacheHelper.stop()` 将取消动作 post 到预缓存 looper，而不是同步终止下载线程；
- `PriorityTaskDataSource` 以 `C.PRIORITY_PLAYBACK_PRELOAD` 注册等待，取消期间累计等待从约 `2036 ms` 增长到 `3647 ms`；
- `PreCachePolicy.hasSafeBuffer()` 允许“非 loading 且达到 idle floor”时通过，但 seek 期间状态可能先经过 `STATE_READY`/回调竞态，当前代码没有独立的“seek 恢复闸门”阻止重新提交。

**根因判断（Grade A，本地可重复日志 + 源码）：** 停顿的主要原因是 seek 取消与新预载提交没有隔离。旧任务异步退出，且 `onPositionDiscontinuity()` 在同一回调中允许下一次 `check()`；前台只有约 9--35 ms 缓冲时，预载仍会进入下载/重试路径。日志没有显示 vivo MediaCodec 崩溃或解码器初始化失败，不能把这次停顿归因于设备解码器。

### 1.2 HLS 崩溃的堆栈闭环

用户截图中的完整关键栈为：

```text
java.lang.NullPointerException
  at com.google.common.base.Preconditions.checkNotNull
  at androidx.media3.exoplayer.hls.HlsMediaPeriod.getStreamKeys(HlsMediaPeriod.java:219)
  at androidx.media3.exoplayer.offline.DownloadHelper.getDownloadRequestBuilder(DownloadHelper.java:938)
  at androidx.media3.exoplayer.offline.DownloadHelper.getDownloadRequest(DownloadHelper.java:911/879)
  at androidx.media3.exoplayer.source.preload.PreCacheHelper$DownloadCallback.onPrepared(PreCacheHelper.java:473)
```

本地 AAR 对应代码：

- `PreCacheHelper.DownloadCallback.onPrepared()` 在 `PreCacheHelper.java:468-481` 无条件调用 `helper.getDownloadRequest(null, startPositionMs, durationMs)`；
- `HlsMediaPeriod.getStreamKeys()` 在 `HlsMediaPeriod.java:216-220` 对 `playlistTracker.getMultivariantPlaylist()` 调用 `checkNotNull()`；
- Media3 `HlsPlaylistTracker` 的公开契约明确写明：`getMultivariantPlaylist()` 在初始 playlist 尚未加载时允许返回 `null`；
- 因此“period 已触发 prepared 回调”与“multivariant playlist 已可用于离线 stream-key 计算”不是同一个生命周期条件，预缓存路径存在竞态。

**根因判断（Grade A，本地 AAR + 官方契约 + 截图）：** 崩溃发生在预缓存下载请求建单，不是视频解码器或前台播放线程。当前 `PreCacheHelper` 把一个允许抛出状态竞态的 `getDownloadRequest()` 当成了不会失败的回调操作。

### 1.3 附带异常（不并入本阶段）

日志还出现：

```text
UnrecognizedInputFormatException
ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
TV-exo-preload: ... mime=application/x-mpegURL
```

这表示至少有直链响应被判为 HLS MIME、但内容不像 HLS playlist。当前 `PreCache.java:154-158` 已有“预载使用原始 MediaItem、不要由 route classifier 覆盖 MIME”的保护；本阶段只保留该风险记录和 telemetry，不把 MIME/Extractor 选择扩展成第三个修复目标。若后续仍能用同一资源复现，应另开任务按输入探测处理。

## 2. 当前 WebHTV 代码与边界

### 2.1 现有保护

- `PreCache` 已有 generation、`PreloadLifecycleTracker`、buffer gate、内存/存储/外部回路状态和 seek 诊断；本阶段应补一个明确的 seek-suppressed 状态，而不是重写整个预载状态机。
- `PriorityTaskDataSource` 已给前台播放更高的 `C.PRIORITY_PLAYBACK`，但它只能在读/开阶段让预载等待，不能保证后台网络调用立即从阻塞的 `upstream.read()` 返回。
- `PreCacheHelper.release()` 的资源销毁顺序已有专门处理，不能因本问题改成先 shutdown executor 再 release helper。
- `third_party/patches/media3-deferred-cues.patch` 已存在并随 `third_party/media-lock.json` 使用，但它只处理 Matroska Cues 延后，和 HLS `getStreamKeys()` 竞态无关；不能借此扩大 Media3 patch 范围。

### 2.2 明确不改的行为

- 不改 `DefaultLoadControl` 的启动/重缓冲阈值；现场的停顿发生在 seek 取消和预载重试竞争，不是单纯的起播缓冲阈值问题。
- 不改连接/读取 timeout、OkHttp 或代理 Range；本次现场已看到后台错误和优先级等待，不能证明网络建连是首因。
- 不改解码器、Surface、DV7→P8.1/HDR10 fallback、TrueHD/Atmos、字幕和 MPV native。
- 不在 `HlsMediaPeriod.getStreamKeys()` 中伪造空 stream keys；这样可能静默生成不完整的下载请求，掩盖前台/离线语义问题。

## 3. 最佳实践证据

| 结论 | 来源与修订 | 等级 | WebHTV 适用性与决策影响 |
| --- | --- | --- | --- |
| HLS tracker 初始 playlist 未加载时可返回 null | AndroidX Media `HlsPlaylistTracker` 官方源码：<https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/playlist/HlsPlaylistTracker.java>，访问 2026-08-27；`getMultivariantPlaylist()` Javadoc 与 `@Nullable` | A | 直接证明 `checkNotNull()` 不是安全的生命周期判断；修复应放在预缓存建单边界。 |
| `HlsMediaPeriod.getStreamKeys()` 当前对 playlist 使用强制非空 | AndroidX Media `HlsMediaPeriod.java` 官方源码：<https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaPeriod.java>，release，访问 2026-08-27，约 216-220 行 | A | 不宜在 App 层假设 period prepared 等于 playlist ready；也不宜伪造 stream key。 |
| `PreCacheHelper.onPrepared()` 无条件生成 DownloadRequest | AndroidX Media `PreCacheHelper.java` 官方源码：<https://raw.githubusercontent.com/androidx/media/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/preload/PreCacheHelper.java>，release，访问 2026-08-27，约 468-481 行 | A | 最窄的安全边界是把建单异常转成预缓存失败回调，并让前台继续播放。 |
| HLS 下载/stream-key 计算已有已知边界缺陷，但不是本次 NPE 的直接修复 | AndroidX Media issue #3333：<https://github.com/androidx/media/issues/3333>，关联修复 `242c34228d3823b0dec5b40af02096f47681e58f`，访问 2026-08-27 | B/A | 上游修复针对 `IndexOutOfBoundsException` 和冗余 rendition 索引，不解决 `multivariantPlaylist == null`；不能声称 cherry-pick 该提交即可修复本崩溃。 |
| Media3 `stop()` 是 looper 异步取消 | 本地锁定 AAR `PreCacheHelper.java:331-361` 与 WebHTV `PreCache.java:229-248` 资源释放注释 | A | 解释旧任务仍存活；App 必须在异步取消期间禁止新任务，而不是只增加优先级等待。 |
| 前台优先级只能降低预载竞争，不能替代取消闸门 | WebHTV `PriorityTaskDataSource.java:44-123`；本地日志 task 1/3 的 `waitTotalMs` 与 `elapsedMs` | A | 保留 playback priority，同时增加 seek-suppressed 和失败 circuit；不改变前台读取路径。 |
| 成熟播放器把昂贵后台动作与前台路径隔离 | MPV `demux/demux_mkv.c`（HEAD `49418246f30a9c24af31ac184aa24f39755db89a`，已记录于 E-SP2）及 FFmpeg `matroskadec.c`（HEAD `eb0bfa852e7b9c524960300607ba2c4617060a9b`） | B | 这是相同的隔离原则，但它们不提供可直接移植的 HLS pre-cache API；仅作为设计佐证，不引入 MPV/FFmpeg 代码。 |

不适用的证据类别：本问题是 Android Media3 生命周期/并发竞态，没有算法或编解码质量假设需要学术论文；没有把无关 benchmark 或论坛经验作为设计依据。设备差异由同一 vivo 设备的原始日志和后续 A/B 验收覆盖。

## 4. 方案比较

| 方案 | seek 停顿 | HLS 崩溃 | 前台/离线语义 | 风险与回滚 | 决定 |
| --- | --- | --- | --- | --- | --- |
| 不变更 | 保留 1.5--2.1 s 旧任务退出和 5 s 失败重试 | 保留 NPE | 无新增风险 | 不能满足用户问题 | 拒绝 |
| 只升级/照搬未修改的上游 Media3 | `PreCacheHelper.stop()` 仍是异步；无 seek 隔离 | 当前 release 仍允许 tracker 返回 null；`242c342...` 只针对另一类索引错误 | 表面版本变化可能带来 AAR/patch 冲突 | 高，收益不足 | 拒绝为单独方案 |
| App 仅调用 `helper.stop()`，然后延迟固定 1--2 s | 可能减少新任务，但固定 sleep 与设备/网络无关 | 不处理 NPE | 前台不变 | 体感和耗电不稳定，无法定义正确恢复条件 | 拒绝 |
| App seek-suppressed gate + Media3 建单异常保护（推荐） | 立即阻止新 task；旧 task 异步退出期间不再竞争；前台恢复后按安全 buffer 放行 | 将 request-build NPE 变成 `onPrepareError`，对当前媒体关闭预载重试 | 前台 HLS 不变，预载失败不伪造下载请求 | 两个原子单元，可分别回滚；需 AAR 重建/设备验证 | 推荐 |
| 在 HlsMediaPeriod 中把 null playlist 当空 stream keys | 不解决 seek 竞争 | 可能不崩溃，但会静默生成不完整 DownloadRequest | 破坏离线/缓存语义且难诊断 | 高，拒绝后难发现 | 拒绝 |

## 5. 推荐实现（等待批准）

### E-SP3-A：App 层 seek 预载隔离

预计修改路径（批准后重新建立 `upstream` 实施 guard）：

- `app/src/main/java/com/fongmi/android/tv/player/exo/PreCache.java`
- 如需可测试的纯状态机，再增加 `app/src/test/java/com/fongmi/android/tv/player/exo/` 下单个 policy test；不做无关重构。

行为设计：

1. `onPositionDiscontinuity()` 先标记 `seekSuppressed=true`、`refillActive=false`、取消 scheduled callback、增加 generation，再调用现有 `helper.stop()`；不在同一 seek 回调中 `check()` 重新提交任务。
2. 所有 `update()`/`check()` 在 `seekSuppressed` 时只记录等待状态，不启动 `PreCacheHelper.preCache()`。
3. 仅在前台播放重新进入稳定状态（`isPlaying=true` 或等价的 READY 回调）后清除 suppression；清除后仍经过现有 `BufferGate.RECOVERY`/`PreCachePolicy.hasSafeBuffer()`，不以固定 sleep 代替安全条件。
4. 旧任务完成/失败/取消回调必须校验 generation；过期回调只能结束旧 telemetry，不能打开新下载。
5. 对连续“无进展的 prepare/download error”增加当前媒体的 preload-only circuit，避免 task 4--12 这类重复重试；不影响 foreground DataSource，也不把 transient foreground 网络错误升级为播放失败。

### E-SP3-B：Media3 `PreCacheHelper` HLS 建单保护

预计修改路径（批准后）：

- `third_party/patches/media3-precache-hls-safety.patch`（新补丁，窄改 `PreCacheHelper.DownloadCallback.onPrepared()`）；
- `third_party/media-lock.json` 中该补丁的 hash/顺序；
- 重建并更新受影响的 Media3 `media3-exoplayer`/HLS AAR、sources、POM/module 校验文件。

行为设计：

1. `helper.getDownloadRequest(null, startPositionMs, durationMs)` 放入窄异常边界；至少覆盖 `NullPointerException`/`IllegalStateException` 这类 request-build 生命周期异常，并保留原始 cause。
2. 异常时先 release `DownloadHelper`，再通过已有 `Listener.onPrepareError()` 传递可诊断的 `IOException` 包装；不调用 `onPrepared()`，不生成伪造 `DownloadRequest`。
3. App 收到该错误后结束当前预载任务并打开 E-SP3-A 的媒体级 preload circuit；前台 `ExoPlayer` 不停止、不切换 decoder、不改变 HLS playlist tracker。
4. 真正的网络/IO 错误继续遵循现有 `onPrepareError`/`onDownloadError` 分类；只有无进展或明确 request-build 生命周期异常触发预载 circuit，避免把所有临时网络波动永久熔断。

### 原子性、依赖和回滚

- E-SP3-A 不依赖 AAR，可先独立验证并回滚；E-SP3-B 与 Media3 AAR/lock 必须成套回滚。
- 两个单元都只属于 Exo；不修改 MPV、FFmpeg、nextlib native 或共享 `.so`。
- 只有用户批准后才进入 `upstream` guard；批准前不创建 patch、AAR 或 APK。

## 6. 验收矩阵与阈值

### 6.1 静态/单元验证

- `git diff --check`、task guard scope/checkpoint 检查。
- E-SP3-A：新增 policy/state 单测覆盖 seek suppression、generation 过期回调、恢复条件和 preload-only circuit；运行受影响的 Exo Java 编译/单测一次。
- E-SP3-B：Media3 patch 应用、`media3-exoplayer`/HLS 源码编译、AAR/POM/module/source hash 与 `third_party/media-lock.json` 一致。

### 6.2 设备复现（同一 vivo V2453A）

使用与本次相同的设置、来源和网络条件，基线与候选各至少 3 次，保存原始 logcat：

| 场景 | 必须满足 |
| --- | --- |
| 连续两次拖动 seek | 从 seek 事件到前台恢复稳定前不得出现新的 `task-start`；旧 task 的取消回调可异步到达，但不能阻塞前台；`waitTotalMs` 不再随新预载任务增长。 |
| seek 后缓冲恢复 | 预载只在现有 recovery watermark/安全 buffer 条件满足后恢复；不能用固定延时掩盖竞态。 |
| 原始 HLS 崩溃资源 | 应用不崩溃；日志出现一次可识别的 preload request-build/prepare-error；前台播放保持可继续或按现有错误 UI 结束，不能生成不完整 DownloadRequest。 |
| HLS 正常播放 | 不因预载保护改变 playlist、track、解码器、字幕、DRM 或时间线。 |
| 非 HLS MP4/MKV/TS | 预载和 seek 行为不回归；E-SP2 deferred Cues、DV/TrueHD/字幕路径保持原有结果。 |
| stop/release/source switch | 无线程向已 shutdown executor 提交任务，无 stale callback 重新启动 preload。 |

性能结论只在同设备/同资源/同网络的 3 次中位数和失败率均记录后给出；“编译成功”不视为 seek 或生命周期通过。

### 6.3 Go/No-Go

任一条件成立即不接受候选：

- 仍有 NPE 或其他 request-build 异常从预载 looper 冒泡到应用；
- seek suppression 期间出现新的 preload task，或前台首个可播放时刻变慢；
- HLS 前台播放被预载错误连带停止、切 decoder、改 MIME 或改变 track keys；
- AAR、patch、lock、sources/POM hash 不一致；
- 任一现有本地 Media3 patch、E-SP2 deferred Cues、缓存/Range 或双 ABI 产物被无关改动。

## 7. 观测、回滚和发布

- 继续记录 `session/generation/task/reason/waitTotalMs/buffered/loading`；新增 `seek-suppressed`、`preload-circuit-open` 和 `request-build-error` 事件，便于确认旧任务与新任务是否隔离。
- E-SP3-A 回滚：恢复该原子 App commit，不触碰 Media3 AAR。
- E-SP3-B 回滚：成套恢复 `third_party/patches/media3-precache-hls-safety.patch`、`third_party/media-lock.json`、受影响 AAR/POM/module/sources 和 App 适配；不回滚 MPV/native。
- 实施阶段必须记录完整 commit、AAR/source SHA-256、验证日志和唯一 recovery tag；未通过设备验收不得标为完成或发布。

## 8. 用户决策

- 推荐批准：`E-SP3-A` + `E-SP3-B`，按上述两个原子单元实施。
- 暂不批准：修改 HlsMediaPeriod 返回空 stream keys、调大网络 timeout、全局关闭预载、修改 decoder/renderer/MIME、MPV/FFmpeg 依赖升级。
- 当前决定：**approved by user on 2026-08-27**。

## Checkpoint 1：2026-08-27 现场诊断与方案包

- 目标/范围：vivo V2453A 上两次 seek 停顿 + 用户截图 HLS NPE；仅 assessment，文档和索引为唯一允许修改路径。
- 基线：分支 `fongmi-sync-bugfix`，HEAD `a20a31a7c6be2454459db68ec41b7cebf824d1a6`；工作区在本阶段开始时干净。
- 已完成证据：保存 `/tmp/webhtv-repro-logcat.txt`；确认 task 1/3 的 2135/1504 ms 异步取消、seek 后 9--35 ms buffer、重复 HttpDataSourceException；截图确认 `HlsMediaPeriod.getStreamKeys()` null 竞态。
- 文件变化：本任务文档和主评估索引；无代码、AAR、lock、APK、`.so` 变化。
- 验证：待文档/索引机械校验与 `git diff --check`；实现验证待用户批准后进行。
- 未决风险：原始 HLS URL 在脱敏日志中不可见，设备验收需重新使用用户截图对应资源或等价可复现 HLS；附带 MIME 误判另开问题。
- 回滚锚点：当前 HEAD `a20a31a7c6be2454459db68ec41b7cebf824d1a6`；本阶段不创建生产 commit/tag。
- 下一动作：用户确认是否按 E-SP3-A/E-SP3-B 进入实施；收到批准后重新建立 `upstream` guard 并先实现 E-SP3-A。

## Checkpoint 2：2026-08-27 用户批准实施

- 用户决定：批准在不直接或间接破坏现有功能的前提下实施 `E-SP3-A + E-SP3-B`。
- 完成记录：Assessment packet approved by user; begin E-SP3-A App seek isolation, then E-SP3-B Media3 HLS request-build guard.
- 权限边界：批准范围只包含本文已列的两个原子单元；不授权修改 decoder/renderer、MIME、MPV/FFmpeg/native 或其他播放器行为。
- 下一动作：Start upstream guard for E-SP3-A with declared App source/test paths.

## Checkpoint 3：2026-08-27 E-SP3-A 已实施

- 实现：seek 发生时停止当前预载并进入 `seekPreloadSuppressed`；只有前台恢复到 `STATE_READY + isPlaying + !isLoading + safeBuffer` 才恢复预载。连续两次非 loopback 预载错误后，仅打开当前媒体的 preload-only circuit。
- 保持的契约：不改前台播放、`DefaultLoadControl`、MIME、解码器、Range、代理、网络 timeout、MPV 或 native 依赖。
- 验证：`PreCachePolicyTest` 与 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 在该原子单元完成时通过。
- 提交：`41b1276111f37bdef75b0d057e4458b70bd8ad28`。
- Recovery tag：`recovery/E-SP3-A/20260827112014-41b1276111f3`。
- 回滚：回滚该提交即可恢复旧 App 预载策略，不触碰 Media3 AAR。

## Checkpoint 4：2026-08-27 E-SP3-B 源补丁已实施

- 实现：`PreCacheHelper.DownloadCallback.onPrepared()` 捕获 `getDownloadRequest()` 的 `RuntimeException`，release helper，并把 cause 包装成 `IOException("Pre-cache download request unavailable", cause)` 交给已有 `onPrepareError()`。
- 保持的契约：不伪造 stream keys，不修改 `HlsMediaPeriod`、playlist tracker 或前台 HLS 播放路径。
- 补丁：`third_party/patches/media3-precache-hls-safety.patch`，SHA-256 `9b48e895bd3923159f880152a54160e7c937b2b0369d717da559a7ad98fd14f7`。
- 提交：`a007af32c85cb120f469c3dc34a58c2c7cd907ef`。
- Recovery tag：`recovery/E-SP3-B/20260827113637-a007af32c85c`。

## Checkpoint 5：2026-08-27 E-SP3-B Media3 产物已发布

- 固定源码：`e3e922d5c01bc0b564849940fe589daf37360d15`；构建脚本按显式顺序应用全部既有补丁和 E-SP3-B 补丁。
- 构建：Media3 发布成功，`474 actionable tasks: 474 executed`。全模块发布产生的无关机械改写已恢复，只保留真正受补丁影响的 `media3-exoplayer` AAR、sources JAR 和校验侧车。
- AAR SHA-256：`485805506d44b739f3cd4bf179241f0322a3e52de6b29625c3133832eaa187ea`。
- Sources SHA-256：`f378864ac3817fd95132268170e6c0ca6d3dc76f9a7eb09c51d60cb06027d442`。
- 内容验证：sources 与 AAR bytecode 均包含 `Pre-cache download request unavailable`；MD5/SHA-1/SHA-256/SHA-512 发布侧车与实际文件一致。
- App 验证：干净构建已成功完成 `:app:compileMobileArm64_v8aDebugJavaWithJavac`，证明新 AAR 可被当前 App 编译消费。
- 提交：`97d02d72b1b4a3eed92d42cd6857fd822c036a8d`。
- Recovery tag：`recovery/E-SP3-B-PUBLISH/20260827123702-97d02d72b1b4`。
- 回滚：成套回滚该提交与前一补丁提交；不回滚 E-SP3-A、MPV 或 native 依赖。

## Checkpoint 6：2026-08-27 设备验收阻塞

- 设备：vivo V2453A，Android 15 / SDK 35，序列号 `10CF6H1D2L0009S` 已连接。
- APK 构建：`:app:assembleMobileArm64_v8aDebug` 在完成 App Java 编译、dex 和 native merge 后，失败于既有 `:app:processMobileArm64_v8aDebugResources`。
- 阻塞证据：缺少 `attr/selectableItemBackgroundBorderless`、`attr/resize_mode`、`Theme.Material3.DynamicColors.DayNight.NoActionBar`、`TextAppearance.MaterialComponents.Tooltip` 等 Material/UI 资源。该失败不在本任务声明路径，也不是 Exo AAR 编译错误。
- 结论：没有候选 APK，故未安装到设备；不得宣称两次 seek suppression 或原 HLS 崩溃场景已通过实机验证。
- 当前风险：代码与依赖产物已完成并可编译，但 Go/No-Go 的设备运行项仍未关闭。
- 下一动作：单独解决或取得已知可构建基线中的 Material 资源链接问题后，重新构建 arm64 APK，安装到同一 vivo，清空 logcat，并按 6.2 的 seek/HLS 场景完成验收。

## Checkpoint 7：2026-08-27 候选实机复现纠正

- 候选 APK 已通过隔离 Gradle 缓存构建并安装到 vivo V2453A；原 Material 资源链接失败确认是共享 Gradle transform 缓存污染，不是仓库源码缺失。
- 用户在同一视频多次复现：普通 seek 后仍有约 1 秒明显停顿，并非此前日志推断的约 8.5 秒。
- 设备日志显示 seek 时正在运行的自动预载任务立即以 `reason=seek` 取消；seek 前后只有 codec flush/config 更新，没有新的 `Created component`，因此“每次 seek 都重新探测并重建 decoder”不成立。
- `UnrecognizedInputFormatException`、MIME 从自动探测切为 HLS、decoder 创建发生在媒体首次打开或重新打开阶段，不等同于普通 seek 停顿。
- 当前最小可证伪假设：seek 到未缓存位置后进入 `BUFFERING`，现有 Exo `3000 ms` rebuffer 门槛造成约 1 秒恢复等待；需要补充 seek 起点/目标、`BUFFERING -> READY` 时长和 READY 时缓冲量日志后再决定行为修复，避免降低正常网络断流的抗抖动能力。
- 下一动作：增加不改变播放行为的 seek 恢复定向日志，构建安装后用同一视频复现一次。

## Checkpoint 8：2026-08-27 seek 诊断入口纠正

- 第一版埋点已编译安装，但用户复现时没有输出 `TV-exo-seek`；调试日志开关已开启。
- 根因是拖动进度条经 `MediaController` 直接发送 seek，未进入 `PlayerManager.seekTo()` 的请求埋点；Analytics discontinuity 已在正确的公共路径上。
- 纠正方案：保留调试开关，在 `DISCONTINUITY_REASON_SEEK/SEEK_ADJUSTMENT` 到达且没有请求序号时建立诊断序号，继续量化 `BUFFERING -> READY -> first-frame/playing`；不改变播放行为。
- 下一动作：增量构建安装纠正后的诊断 APK，清空 logcat 后复现一次并据时序实施最小修复。

## Checkpoint 9：2026-08-27 画面恢复后停顿的诊断边界

- 用户进一步明确：问题不是 seek 后等待缓冲期间黑屏或静止，而是缓冲结束、画面已经恢复并开始播放后，又额外停顿一下再正常播放。
- 现有多次设备日志已排除每次 seek 的 MIME 重新探测、MediaSource/decoder 重建；seek 邻近只看到 codec flush/config 更新。
- 因此不调整 seek 精度或 rebuffer 阈值；下一轮只观察恢复首帧后的连续视频帧间隔、`isPlaying`/playback state、音频推进、underrun/sink error 和预载状态。
- 完成说明：seek 后停顿发生在画面恢复之后；现有日志排除每次 seek 的 MIME 探测和 decoder 重建，下一轮只补首帧后视频帧间隔与音频推进时序。
- 下一动作：增加 seek 后首帧/视频帧间隔/音频推进诊断，构建安装并复现一次。

## Checkpoint 10：2026-08-27 最新诊断 APK 重新打包安装

- 目标/车道/范围：`quick-fix`；仅重新构建并安装当前 E-SP3-DIAG 诊断代码，范围仍为 `PlayerManager.java`、`PlaybackAnalyticsListener.java` 与本文档，不扩展播放行为。
- 基线：分支 `fongmi-sync-bugfix`，HEAD `0250fd01295fad8c499c699da45e07eb3834256d`。
- 受保护的既有改动：上述三个路径均为当前任务已有未提交改动；重新打包不得清理、覆盖或混入其他路径。
- 已完成证据/文件变化：首帧后视频间隔、音频推进、underrun/sink error 与 isPlaying 恢复诊断代码已经存在；本检查点只追加任务状态，不新增播放行为。
- 验证状态：此前同一变体构建成功；本轮按用户要求重新执行 `:app:assembleMobileArm64_v8aDebug`，随后覆盖安装并核对设备端包与启动状态。
- 未决风险：必须以本轮新产物成功安装为准；上一次安装曾被 OEM 权限确认中止。
- 回滚锚点：HEAD `0250fd01295fad8c499c699da45e07eb3834256d`；诊断改动尚未提交，不执行清理或回退。
- 下一动作：立即重新构建 mobile arm64 debug APK，并用 OEM 安装器辅助流程覆盖安装到已连接 vivo 手机。

## Checkpoint 11：2026-08-27 三次 seek 停顿根因定位

- 现场证据：设备应用内日志保存为 `/tmp/e-sp3-3seek-app-debug.log`，系统日志保存为 `/tmp/e-sp3-3seek-current.log`。用户连续三次 seek 后都复现“目标画面先出现，再冻结一下才连续播放”。
- 第一次：目标首帧在 seek 后 `485 ms` 出现；下一帧相隔 `1734 ms`；播放器在累计约 `9054 ms` 前向缓冲后进入 `READY`。
- 第二次：目标位置首帧出现后，下一帧相隔 `1003 ms`；播放器在约 `8466 ms` 前向缓冲后进入 `READY`。
- 第三次：目标位置首帧出现后，下一帧相隔 `642 ms`；播放器在约 `9088 ms` 前向缓冲后进入 `READY`。
- 一致原因：三次都在 `BUFFERING` 阶段允许渲染一张 seek 目标帧，但 `AutoLoadControl` 随后锁定 `episode=startup startMs=8000`，原因均为 `throughput-deficit`；播放时钟必须等约 8 秒媒体缓冲后才启动，所以用户看到目标画面冻结约 0.6--1.7 秒。
- 排除项：三次窗口内没有 decoder `Created component`/release-recreate，没有 audio underrun、audio sink error 或崩溃；因此不是 decoder 重建，也不是音频 sink 故障或预载取消。
- 诊断日志限制：三次 seek 在 30 秒追踪窗口内复用了 `seq=2`，因此 `elapsed`/`bufferingDuration` 为该窗口累计值；每次 `discontinuity`、`playback-buffer event=start/end` 和视频帧 gap 仍能独立确定上述时序，不影响根因结论。
- 最小修复边界：不得降低正常首次启动或普通网络断流的全局阈值；应把用户 seek 恢复识别为独立 episode，仅调整 seek 后启动门槛，并阻止主动 seek 被计入网络 rebuffer 风险历史。
- 下一动作：补充 E-SP3 seek-specific threshold 的决策设计与验收/回滚条件，取得该播放行为阶段批准后实施、构建并在同一视频复测三次。

## E-SP3-C：seek 专用恢复门槛

- 用户决定：用户于 2026-08-27 明确要求修复，批准该窄播放行为单元。
- 决策问题：如何消除“seek 目标帧已出现但仍冻结约 0.6--1.7 秒”，同时不降低首次启动或真正网络 buffer depletion 的保护门槛？
- 官方/上游语义（A）：当前锁定 Media3 sources `e3e922d5c01bc0b564849940fe589daf37360d15` 的 `DefaultLoadControl` 明确定义 `bufferForPlaybackMs` 用于首次启动和 seek 等用户动作，默认值为 `1000 ms`；`bufferForPlaybackAfterRebufferMs` 只用于非用户动作导致的 buffer depletion，默认值为 `2000 ms`。同一 sources 中 `ExoPlayerImplInternal.seekToPeriodPosition()` 在 seek 时显式把 `isRebuffering` 重置为 false。
- WebHTV 实机证据（A）：`/tmp/e-sp3-3seek-app-debug.log` 的三次 seek 都被本地自适应层锁成 `episode=startup startMs=8000`，目标首帧先出现，随后冻结 `1734/1003/642 ms`，直到前向缓冲达到 `9054/8466/9088 ms` 才进入 READY。
- 当前代码原因：`AutoLoadControl` 只区分 `STARTUP/REBUFFER`；Media3 正确地把 seek 报为 `rebuffering=false`，但 WebHTV 因此误选 `STARTUP`。同时 `PlaybackAnalyticsListener` 把 seek 后 BUFFERING 计入网络 rebuffer 历史，进一步抬高后续风险。
- 方案比较：不变更无法满足用户；把全局 startup/rebuffer 降到 1 秒会破坏真实弱网保护；依赖 seek 时间猜测或固定 sleep 不可靠；推荐在现有 App 协调器增加一次性 `SEEK` episode，沿用 Media3 的 `1000 ms` 用户动作门槛，并只从网络 rebuffer 统计中排除该 seek 区间。
- 最终设计：收到 `DISCONTINUITY_REASON_SEEK/SEEK_ADJUSTMENT` 时标记当前 Exo session 的 seek recovery；`AutoLoadControl` 在 `rebuffering=false` 且标记有效时选择 `SEEK`，阈值为 `min(当前自适应 startMs, 1000 ms)`；READY/IDLE/ENDED 或超时后清除。真实 `rebuffering=true` 始终优先选择 `REBUFFER`，首次启动仍选择 `STARTUP`。
- 保护契约：不改用户配置值、target bytes、min/max buffer、ABR、网络保护、预载、decoder/renderer、Surface、MIME、Media3 AAR、MPV 或 native；不允许 seek 风险覆盖真实 rebuffer。
- 验收：单测覆盖 `REBUFFER > SEEK > STARTUP` 分类和 `SEEK <= 1000 ms`；arm64 Java 编译通过；同一 vivo/视频三次 seek 日志必须为 `episode=seek`，READY 不再等待约 8--9 秒媒体缓冲，目标首帧后的额外 gap 不超过 `300 ms`，且 seek 不增加网络 rebuffer count/total。
- 回滚：单独回滚 E-SP3-C App commit，恢复旧 `STARTUP/REBUFFER` 分类；E-SP3-A/B、诊断日志和 Media3 产物不动。
- 不适用证据：不涉及 ABI、native、codec 算法、安全解析或新依赖，学术论文、跨播放器代码和二进制供应链研究不会改变该 App 层分类修复。

## Checkpoint 12：2026-08-27 E-SP3-C App 行为实现

- 基线：分支 `fongmi-sync-bugfix`，HEAD `0d2f80fb3a0f92717d820556f739b68b9d9a62db`；诊断单元已提交并有 recovery tag。
- 实现：阈值协调器新增 session-bound、30 秒自动过期的 `SEEK` 标记；`AutoLoadControl` 按 `REBUFFER > SEEK > STARTUP` 分类，seek 实际门槛为 `min(startMs, 1000 ms)`；Analytics 在 seek recovery 中不增加自适应阈值使用的 rebuffer 历史。
- 保持契约：首次启动、真实 rebuffer、target bytes、min/max buffer、ABR、网络保护、预载、decoder/renderer、Media3 AAR 和其他播放器未改变。
- 验证：`AutoLoadControlTest` 与 `ExoPlaybackThresholdCoordinatorTest` 共 17 个测试通过，`:app:compileMobileArm64_v8aDebugJavaWithJavac` 通过；Gradle `BUILD SUCCESSFUL`。
- 未决风险：`PlayerManager` 的公共 `PlaybackBufferingTracker` 仍可能把 seek 后 BUFFERING 记入通用网络重缓冲 telemetry；该统计排除应作为独立窄单元完成，不与 LoadControl 行为提交混合。
- 回滚锚点：回滚 E-SP3-C App commit 即恢复旧阈值分类，不影响 E-SP3-A/B 和诊断单元。
- 下一动作：提交并标记 E-SP3-C 行为单元；随后单独排除公共 buffering tracker 的用户 seek 事件，再打包安装实机验收。

## Checkpoint 13：2026-08-27 seek 公共统计隔离

- 实现边界：`PlayerManager.recordBufferingState()` 只在 Exo、刚进入 BUFFERING、当前没有 tracker episode 且 Analytics 确认是有效用户 seek recovery 时跳过公共 rebuffer 计数，并记录 `phase=seek outcome=user-action`。
- 保持契约：首次启动、真实网络 rebuffer、MPV/IJK managed reload、播放阈值和 telemetry 字段格式不变；READY 无需伪造结束事件，因为 seek BUFFERING 从未打开 tracker episode。
- 验证结果：`bash gradlew --offline --no-daemon :app:clean :app:compileMobileArm64_v8aDebugJavaWithJavac` 通过；用户已在 vivo 实机使用同一视频重复 seek，确认修复后播放表现正常。
- 回滚：单独回滚该统计隔离 commit 即恢复旧公共计数，不影响 seek 专用播放门槛。
- 下一动作：通过 task guard 原子提交该统计隔离单元并创建 recovery tag。

## Checkpoint 14：2026-08-27 实机验收与收尾

- 完成：用户已在 vivo 实机重复 seek，确认修复后播放正常，本轮补充的公共 buffering 统计隔离代码已通过干净 arm64 Java 编译。
- 工作区：分支 `fongmi-sync-bugfix`；基线 `038b6c58b265a7fbd5c6a173068d7fe1f4d868d3`；受保护的其他脏文件未改。
- 变更文件：`PlayerManager.java`、`PlaybackAnalyticsListener.java`、本任务文档。
- 验证：`bash gradlew --offline --no-daemon :app:clean :app:compileMobileArm64_v8aDebugJavaWithJavac` 通过；用户实机验收通过。
- 回滚锨点：`038b6c58b265a7fbd5c6a173068d7fe1f4d868d3`；本单元提交后使用 task guard recovery tag 回滚。
- 未决：无。
- 下一动作：执行 task guard finish 并创建唯一 annotated recovery tag。

## Checkpoint 15：2026-08-27 APK 资源链接恢复

- 现象：执行 `:app:clean` 后，普通 assemble 在 `processMobileArm64_v8aDebugResources` 缺少 AppCompat、Material3 和 Media3 UI 资源。
- 定性：变体 runtime 依赖图仍完整包含三组依赖，最近 seek 提交未改 Gradle 或 XML；根因是 AGP/Gradle 恢复了不完整的资源构建缓存，不是源码或依赖配置缺失。
- 恢复：`--no-build-cache --rerun-tasks :app:processMobileArm64_v8aDebugResources` 成功重建资源；随后普通 `:app:assembleMobileArm64_v8aDebug` 通过。
- 产物：`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`，SHA-256 `da1514a4e826cd8a363051c615bb89ca44d49d861088dcf9acb3f7987ff0c46d`。
- 代码处置：无需修改或回滚 seek 代码、Gradle 配置或依赖版本。
- 下一动作：关闭资源恢复诊断单元并交付 APK。
