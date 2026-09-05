# E6-1 Exo 有界缓存写入修复

## Recovery anchor

- 目标：让 Exo 预缓存严格遵守 `DataSpec.length`，避免 Range 被服务器/代理忽略时继续下载整个文件。
- 状态：已实施、验证并提交；实现 commit `0a8ed3b910679a08a7e41c735338c3804a2eb938`，恢复 tag `recovery/E6-1/20260827145043-0a8ed3b91067`。
- 分支/HEAD：`fongmi-sync` / `c67b1498a2d5fcbb3152d7199e2af19a9f3544a0`。
- 来源：`FongMi/media@dd00f94b58b7324ab29febb0b50f3a190d544a3b`；父提交 `FongMi/media@32c20a091ba6e5fd09e13e67df3149326232eda5` 的 SMB/proxy 主体已由当前 fork 覆盖，不重复移植。
- 保护：`AGENTS.md`、`.codex/scripts/task_guard.sh`、`docs/agents-md-effective-constraints-review-2026-08-21.md`、`third_party/sources/media` 既有脏改动不修改、不提交。

## 决策与范围

用户已批准实施。采用窄适配：只移植上游 `CacheWriter` 的 `long` 计数、bounded `read` 和 end-position 修复，并补齐当前项目缺少的边界测试；不引入同一上游提交中的 progressive 并行下载、`DiskPreloadManager`、priority manager setter 或 E6-2 factory/cancel 行为。

### 实际能力

当播放网盘直链或本地代理只请求视频的一小段进行预缓存，而服务端错误返回整段/整个文件时，缓存写入会在请求长度处停止，不会制造无界下载、重叠缓存或额外磁盘占用。未知长度请求仍会正常读到 EOF。

### 当前项目已有实现与缺口

- `app/src/main/java/com/fongmi/android/tv/player/exo/PreCache.java` 通过 `PreCacheHelper` → `ProgressiveDownloader` → `CacheWriter` 写入共享 `SimpleCache`，因此该修复实际可达。
- `PreCache` 已有播放优先级、缓存水位、代理 circuit breaker、内存/网络/电量/温度策略；本任务不改变这些策略。
- 当前发布的 `media3-datasource` `CacheWriter` 仍以 `int totalBytesRead` 统计，并在 bounded 请求时持续读到 EOF；确认没有被近期 WebHTV 提交等价覆盖。
- WebHTV 提交 `2a2af0c218bc7dee871b9a7d4f25f808420214d2` 只限制预加载范围生成，不能修复底层数据源忽略长度的问题。

### 方案比较

1. 不变：保留服务器/代理忽略 Range 时的无界读取风险。
2. 原样合并 `dd00f94...`：会额外引入并行下载和新 manager，与当前 `PreCache` 策略重叠，范围过大。
3. WebHTV 窄适配（采用）：只修 `CacheWriter` 并补测，保持单线程、现有 priority 所有权和所有播放器策略不变。

## 收益、风险与兼容性

- 收益：减少无效网络流量、磁盘写入和缓存区间重叠；大文件计数不溢出。
- 风险：边界 off-by-one、未知长度误截断、提前 EOF/取消/重试状态处理错误。
- 兼容性：普通前台播放、SMB、`proxy://`、MPV、字幕、解码器、native ABI 和公共 API 不变；仅影响 Media3 cache writer 的读取终止条件。
- 性能/包体积：减少错误 Range 场景的读取和写入；正常场景增加一次长度计算，AAR 增量可忽略。
- 最佳实践：是，缓存层必须尊重调用者声明的长度，并使用 `long` 表示累计字节。
- 回滚：恢复本任务提交/tag，或只回滚 `media3-datasource` AAR、补丁和 lock；不触碰 E4/J1、MPV/native 和受保护脏路径。

## 验收标准

- 九个既有 Media3 补丁后追加 E6-1 补丁可重复 `git apply --check/apply`。
- `CacheWriterTest` 覆盖 bounded 请求、Range 被忽略返回 200、未知/已知长度、提前 EOF、部分缓存、取消/重试及 long 计数边界。
- `:lib-datasource:testDebugUnitTest`、`:lib-datasource:compileDebugJavaWithJavac`、单模块发布、`:lib-exoplayer:compileDebugJavaWithJavac` 和 Mobile/Leanback arm64 Java 编译通过。
- AAR、sources、patch 和 lock SHA-256 一致；不更新 nextlib/FFmpeg/MPV/native。

## 实施记录

### 2026-08-27：实施启动

- 已创建基线 tag `recovery/E6-1/baseline-202608271355-c67b1498a2d5`。
- 已启动 task guard `E6-1`，范围限定为本文件、评估索引、构建脚本、Media3 datasource 补丁/产物和 lock。
- 在隔离 checkout `/private/tmp/e61-media-202608271402` 从 Media3 基线 `e3e922d5c01bc0b564849940fe589daf37360d15` 依序重放 9 个既有补丁和 E6-1；全部 `git apply --check/apply` 及 `git diff --check` 通过。
- 使用 JDK 21、代理 `127.0.0.1:7896` 和持久 Gradle 缓存，在同一 Gradle 进程执行 `:lib-datasource:testDebugUnitTest --tests androidx.media3.datasource.cache.CacheWriterTest`、`:lib-datasource:compileDebugJavaWithJavac`、`:lib-datasource:publishReleasePublicationToMavenRepository`、`:lib-exoplayer:compileDebugJavaWithJavac`，结果 `BUILD SUCCESSFUL in 3m 45s`，249 个任务执行成功。
- 已将 datasource publication 安装到 `third_party/maven/androidx/media3/media3-datasource/1.11.0-alpha01-fongmi/`：
  - AAR SHA-256：`ce5c9919bcfe611ecc9de5c4d40c0baa94f2e5858bebc54431c400a90fc37b18`
  - sources JAR SHA-256：`4ac57076178a78996a4ab9ffe4466d28db71d804f735d179b9d8cbdf3d2325e7`
  - Gradle module SHA-256：`b2492a158f300145c2cbafef2f76becdca3740a9645b7bbd37338774c9620d64`
  - POM SHA-256：`b51078a1e63db36711db979356929957a716a01441c182cd772e2b02620a1d52`
- 补丁 SHA-256：`1b591c618fdbba91b0880284126ac8bd5c55f6fe6532187d891b4225a87cc200`；已写入构建顺序和 `third_party/media-lock.json`。
- App 消费路径：使用 JDK 21、固定用户级 Gradle 缓存和 `127.0.0.1:7896` 代理，执行 `bash ./gradlew --no-daemon --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`，结果 `BUILD SUCCESSFUL in 38s`；两个产品路径均实际重新执行 Java 编译。直接 `./gradlew` 因仓库 launcher 未设置可执行位而未进入 Gradle，改用等价的 `bash ./gradlew` 后成功，不属于代码或依赖失败。
- 验收结论：补丁重放、精确失败模式单测、datasource 编译/发布、Exoplayer 编译、产物/lock 哈希以及 Mobile/Leanback arm64 App 编译全部通过；未改变 MPV、FFmpeg、nextlib、native、公共 API 或现有预缓存策略。
- 剩余风险：未做实机网络流量抓包；定向测试已经精确模拟 Range 长度被忽略、数据源返回完整资源的故障，并确认缓存严格停止在请求的 20 字节处，因此不把实机抓包作为本窄修复的提交门槛。
- 回滚：回退本任务原子提交，或恢复基线 tag `recovery/E6-1/baseline-202608271355-c67b1498a2d5`；datasource publication、patch、lock、构建顺序和文档必须作为一个兼容集合回退。
- 原子提交：`0a8ed3b910679a08a7e41c735338c3804a2eb938`（`Exo: bound cache writer requests`）。
- 恢复 tag：`recovery/E6-1/20260827145043-0a8ed3b91067`；tag 创建耗时 0 秒。
- 当前结论：E6-1 完成。下一动作：按主评估索引评估下一项尚未实施的 Exo 任务，不自动扩大到 E6-2 或并行预加载。
