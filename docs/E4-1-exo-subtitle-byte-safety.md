# E4-1 Exo 字幕字节与边界安全

## Recovery anchor

- 目标：在不改变 WebHTV 当前 raw bitmap 字幕和默认关闭 extraction 策略的前提下，修复文本字幕有效长度、字符集白名单和 TTML XML 声明处理。
- 状态：A4-1a/A4-1b 已通过定向测试、干净重放、两个模块发布校验和 App 接线编译；实现已提交并创建 recovery tag，文档闭环待提交。
- 基线：`cafd4f69e613a5db49df5e38e762b6bf4fe58819`；恢复 tag `recovery/E4-1/baseline-20260826210500-cafd4f69e6`。
- 上游来源：`FongMi/media@d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`；当前 fork 部分实现 `63531ddcd508b646e0cf515df3bb6caf4835120e`。
- 保护：`AGENTS.md`、`.codex/scripts/task_guard.sh`、`docs/agents-md-effective-constraints-review-2026-08-21.md` 及所有其他初始脏路径不修改、不提交。
- 实现提交：`9018f2b5c2b132644cde3841f33fe306209d2499`（`Exo: harden subtitle byte and charset handling`）。
- 实现恢复 tag：`recovery/E4-1/20260827074736-9018f2b5c2b1`。
- 下一步：完成本文件与总评估索引的文档闭环提交；不重复发布 AAR/sources，不修改受保护脏路径。

## 决策与范围

用户已批准只实施 A4-1a 和 A4-1b：

- A4-1a：`DelegatingSubtitleDecoder` 只对白名单文本 MIME 探测，严格使用有效 `length`，非 UTF-8 TTML 转码后同步 XML encoding 声明。
- A4-1b：`SubtitleExtractor` 使用有效 `bytesRead`，接收源 MIME 判断是否探测，并同步 TTML 声明；`DefaultMediaSourceFactory` 传递源 MIME。
- 不实施 A4-1c/A4-1d：不新增 extraction-safe factory，不改变当前 `parseSubtitlesDuringExtraction=false`、`textTrackTranscodingEnabled=false` 默认策略，不启用全量字幕 extraction。

## 保留的项目契约

- PGS/VobSub/DVB/TX3G/MP4VTT 等非纯文本字幕不进入通用字符集探测，原始字节保持不变。
- `TextRenderer.legacyDecodingEnabled=true` 保留，外挂和容器 raw subtitle 继续在 renderer 阶段解析。
- 不修改 FFmpeg、MPV、libplacebo、JNI、字幕 UI 或 App 公共 API。
- Media3 变更通过补丁和锁定 AAR/sources 交付，隔离 checkout 本身不作为仓库文件提交。

## 实施记录

### A4-1a/A4-1b：已实现并验证

- 补丁：`third_party/patches/media3-exo-subtitle-byte-safety.patch`
- 隔离 checkout：`/private/tmp/e41-media-clean`，基于 `e3e922d5c01bc0b564849940fe589daf37360d15` 顺序重放现有 7 个补丁后实施；未修改主仓库中预存脏的 `third_party/sources/media`。
- 生产改动：`DelegatingSubtitleDecoder` 和 `SubtitleExtractor` 严格按有效字节长度探测/转码；`SubtitleDecoderFactory` 仅允许 SSA/WebVTT/SubRip/TTML；`DefaultMediaSourceFactory` 向 whole-file extractor 传递源 MIME；TTML 转码后同步 XML encoding；未知或不支持字符集保留原始字节而不抛异常。
- 测试改动：新增 render-time 有效长度、GB18030、TTML declaration、二进制直通覆盖；扩展 extractor 未知长度/partial read、非 UTF-8 和 TX3G 直通覆盖。
- 受影响模块：`media3-exoplayer`、`media3-extractor`。
- 验收：非 UTF-8 SSA/SRT/VTT/TTML、缓冲区容量大于有效长度、未知长度非 1024 整数倍、二进制字幕不探测、TTML declaration 更新、UTF-8/BOM/UTF-16 回归。

### 验证检查点：2026-08-27

- `git diff --check`：隔离 checkout 通过。
- JDK：`21.0.12.1`，Gradle `9.1.0`。
- `:lib-exoplayer:compileDebugJavaWithJavac`：通过。
- `:lib-extractor:compileDebugJavaWithJavac`：通过。
- `:lib-exoplayer:testDebugUnitTest --tests androidx.media3.exoplayer.text.DelegatingSubtitleDecoderCharsetTest`：在沙箱外通过。
- `:lib-extractor:testDebugUnitTest --tests androidx.media3.extractor.text.SubtitleExtractorTest`：在沙箱外通过。
- 补强 A4-1b 的非 UTF-8 TTML whole-file declaration 用例后，单独重跑同一 `SubtitleExtractorTest`，`BUILD SUCCESSFUL in 51s`。
- 首次受限沙箱测试未进入断言即因 Robolectric 无法创建 `/Users/macbookpro/.robolectric-download-lock` 失败；未发现代码或测试失败，重跑时使用同一命令、JDK 21 和 `-Pkotlin.compiler.execution.strategy=in-process`，仅申请了用户批准的沙箱外权限。
- 正式补丁：`third_party/patches/media3-exo-subtitle-byte-safety.patch`，SHA-256 `8ed566a26b70609262d6c49ada2e8912997f36d75a166825e368182b5b7f35bf`；仅含 4 个生产文件和 2 个测试文件。
- 干净重放：新 worktree `/private/tmp/e41-media-replay` 从 `e3e922d5c01bc0b564849940fe589daf37360d15` 顺序应用 8 个锁定补丁，全部 `git apply --check` 和 `git diff --check` 通过；6 个 E4-1 文件与已测试树逐字节一致。
- 定向发布：仅执行 `:lib-exoplayer:publishReleasePublicationToMavenRepository` 和 `:lib-extractor:publishReleasePublicationToMavenRepository`，`BUILD SUCCESSFUL in 1m 58s`；未发布其他 Media3 模块，未安装根级 Maven metadata。
- `media3-exoplayer` AAR SHA-256：`8f34ea1a6c2951c4ed4f033144e38e9e0616443a7a05fa488e5525ca47084c3f`；sources SHA-256：`71f9bee851acf22e7a63b91347b03a4966c1b9cc23958672b3da091cab3f32c6`。
- `media3-extractor` AAR SHA-256：`14811712a342517841cf2087d8316f4458f15df2ab1688fb795570a33351f47d`；sources SHA-256：`818c7917af9cb3b4e090f14d085e91482d2b8cfef9a1d592a781d106c9e0af64`。
- 产物内容对比：两个 AAR 的非 class 内容不变；Exo 仅 3 个修改源码对应的外部/内部 class 变化，Extractor 仅 `SubtitleExtractor` 及其内部 `Sample` class 变化；sources 仅 3 个 Exo 和 1 个 Extractor 生产文件变化；POM 内容不变，module 仅同步新 artifact 大小和哈希。
- App 接线：JDK 21、Gradle 9.5.1，`bash ./gradlew --no-daemon --console=plain -Pkotlin.compiler.execution.strategy=in-process :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`，`BUILD SUCCESSFUL in 4m 4s`。
- 契约审计：`parseSubtitlesDuringExtraction=false`、`textTrackTranscodingEnabled=false` 和 `TextRenderer.legacyDecodingEnabled=true` 保持；补丁不包含 extraction-safe factory、HLS/DASH/SS/chunk 默认接线或字幕 UI 改动。
- 扩展回归审计：5 组既有 decoder 测试共 63 条；2 条 SSA `Cue.lineType` 旧期望在仅前 7 个补丁的精确基线中同样失败，确认为预存测试漂移；TX3G UTF-16LE 失败也在该基线中复现。未知 MIME 的两参数构造保持默认不探测，运行时 factory 仍按 MIME 白名单显式开启文本探测，避免把 TX3G/MP4VTT 等二进制 payload 交给 detector。

## 回滚

优先回滚本任务提交，恢复到基线 tag `recovery/E4-1/baseline-20260826210500-cafd4f69e6`；若仅 AAR 校验或运行测试失败，删除本任务补丁和对应 artifact override，保留 E1/E2/E3 及既有 Media3 补丁链。

## 验证与剩余风险

已完成补丁、单元测试、模块编译、干净重放、定向发布、产物内容对比和 App 接线编译。字符集探测本身仍是统计判断，极短或高度含糊的文本可能无法识别，此时保留原始字节；二进制字幕已由 MIME 白名单隔离。默认 extraction 开关、外挂 bitmap 路由和 HLS/DASH segmented extraction 不在本次实施范围，A4-1c/A4-1d 仍需独立批准；本阶段不以实机字幕 UI 截图替代后续 A4-J 联合验收。
