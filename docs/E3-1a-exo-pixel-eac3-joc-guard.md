# E3-1a：Exo Pixel E-AC3 JOC capability guard

- 任务 ID：`E3-1a`
- 所属分类：Exo
- 状态：已实施，待实机验收
- 唯一任务文档：docs/E3-1a-exo-pixel-eac3-joc-guard.md
- FongMi Media 基线：`FongMi/media@e3e922d5c01bc0b564849940fe589daf37360d15`
- WebHTV 基线分支：`dev2@6312c485f06346bb6205092c1909efa59bae7ac2`

## Recovery anchor

- 目标：在 Google/Pixel 设备上，当平台 E-AC3 decoder 无法解码 E-AC3 JOC 流时，阻止 Exo 将 `audio/eac3-joc` 降级为 `audio/eac3` 交给已知会失败的 MediaCodec；让 `CompatFfmpegAudioRenderer` 用 FFmpeg `eac3` 软解为 PCM 接管。非 Google 设备保持现有 2D 降级行为不变。
- 接受标准：`MediaCodecUtilTest` 的 Google/非 Google JOC 两个 case 通过；App 在 Google 设备上 JOC 流不选平台 E-AC3 fallback 而走 FFmpeg PCM；非 Google 设备 JOC 仍可 2D 降级；`media3-exoplayer` AAR/sources 独立更新且可单独回滚。
- 允许路径：本任务文档、主评估索引、`third_party/patches/media3-exo-eac3-joc-pixel-guard.patch`、`scripts/build_media_deps.sh`、`third_party/media-lock.json`、Media3 `media3-exoplayer` Maven 产物。
- 回滚：删除 patch、还原 build 脚本 patch 数组和 lock override，重新发布 `media3-exoplayer` AAR/sources。完整恢复目标为实施前 HEAD；不回滚 E1、E2-1、E2-2、E-SP 系列或其他本地提交。

## 上游提交台账与处置

| 完整 commit ID | 主题 | 处置 | 阶段 |
| --- | --- | --- | --- |
| `1066f642a64434e7c3c0be687d3e94a4ca2815d7` | Support multiple alternative MediaCodec MIME types | **窄取**：只取 E-AC3 JOC 的 Google/Pixel guard 和对应测试 | E3-1a |

### 上游 commit 详细记录

- 仓库：`https://github.com/FongMi/media.git`
- commit：`1066f642a64434e7c3c0be687d3e94a4ca2815d7`
- parent：`2bc207851df311340767e913931ca7b28cab1794`
- 日期：2026-05-23
- 消息：`Support multiple alternative MediaCodec MIME types` — DTS-HD MA with core maps to both DTS-HD and DTS. Add wiring to support more than one alternative MIME type.
- 在线核对时间：2026-08-26（Asia/Shanghai）
- 访问方式：GitHub API `gh api repos/FongMi/media/commits/<sha>`，已读取完整 7 文件 diff

该提交共修改 7 个文件：

| 文件 | 改动 | E3-1a 是否纳入 |
| --- | --- | --- |
| `libraries/exoplayer/.../MediaCodecUtil.java` | `getAlternativeCodecMimeType()->String` 改为 `getAlternativeCodecMimeTypes()->List<String>`；新增 `supportsEac3JocFallbackDecoding()` | **窄取**：只取 `supportsEac3JocFallbackDecoding()` 和 JOC 分支的 guard 逻辑 |
| `libraries/exoplayer/.../MediaCodecUtilTest.java` | 两个 JOC case 改名并适配 List 返回 | **窄取**：两个 JOC case |
| `libraries/exoplayer/.../MediaCodecInfo.java` | `isSampleMimeTypeSupported` 适配 List | 不纳入（fork 已有等价实现） |
| `libraries/exoplayer/.../DefaultTrackSelector.java` | `VideoTrackInfo` 适配 List | 不纳入（fork 已有等价实现） |
| `libraries/inspector/.../MediaExtractorCompatInternal.java` | 适配 List | 不纳入（fork 已有等价实现） |
| `libraries/transformer/.../SampleExporter.java` | 适配 List | 不纳入（fork 已有等价实现） |
| `libraries/transformer/.../TransformerUtil.java` | 适配 List | 不纳入（fork 已有等价实现） |

### 当前 fork 覆盖关系

主评估检查点 9.1 已确认：当前 fork `e3e922d5...` 的祖先 `0592f21c...` 和 `07cc217a...` 已实现多 alternative MIME API 主体和 DTS-HD MA/DTS fallback 消费。当前 fork 的 `MediaCodecUtil.getAlternativeCodecMimeTypes()` 已存在且已处理 DTS-HD/DTS-UHD/DV/MV-HEVC 等分支，唯独 E-AC3 JOC 分支缺少 Google 设备 guard：

```java
// 当前 fork（无 guard）
if (MimeTypes.AUDIO_E_AC3_JOC.equals(format.sampleMimeType)) {
  return Collections.singletonList(MimeTypes.AUDIO_E_AC3);
}

// 上游 1066f642（有 guard）
if (MimeTypes.AUDIO_E_AC3_JOC.equals(format.sampleMimeType)) {
  return supportsEac3JocFallbackDecoding()
      ? Collections.singletonList(MimeTypes.AUDIO_E_AC3)
      : Collections.emptyList();
}
// ...
private static boolean supportsEac3JocFallbackDecoding() {
  return !Objects.equals(Build.MANUFACTURER, "Google");
}
```

## 证据与设计决策

### 当前 WebHTV 路径

- `CompatFfmpegAudioRenderer.supportsFormatInternal`（app/src/main/java/.../CompatFfmpegAudioRenderer.java:48）先用 `FfmpegLibrary.supportsFormat` 判断 FFmpeg 是否支持该 MIME。
- `FfmpegLibrary.getCodecName`（nextlib AAR sources）将 `MimeTypes.AUDIO_E_AC3_JOC` 映射到 `"eac3"`，FFmpeg 支持软解。
- `ExoUtil.buildAudioRenderers`（ExoUtil.java:1013-1017）在 `super.buildAudioRenderers` 之后按 `getExtensionRendererIndex` 插入 `CompatFfmpegAudioRenderer`。`PREFER` 模式下 FFmpeg renderer 排在 MediaCodec renderer 之前，`ON` 模式排在之后。
- `MediaCodecAudioRenderer.supportsFormat` 调用 `MediaCodecUtil.getDecoderInfos(format.sampleMimeType, ...)` 查询平台 decoder 是否支持原始 JOC MIME。若平台无 JOC decoder，再由 `getAlternativeDecoderInfos` 用 alternative MIME 查询。当前 fork 无条件返回 `audio/eac3` 作为 alternative，导致 Pixel 的平台 E-AC3 decoder 被选中，但该 decoder 无法解码 JOC 流，播放失败。
- AndroidX Media Issue #3257（closed）标题 "EAC3-JOC MIME type should not be taken as EAC3 on Pixel Device" 记录了此问题：Pixel 集成了 E-AC3 decoder 但不支持 E-AC3 JOC 流；不带 guard 时 manifest 中 JOC 轨被选中后播放失败，带 guard 后回退到 AAC 轨可正常播放。访问方式 `gh api repos/androidx/media/issues/3257`，2026-08-26。

### 外部证据

- 上游 commit `1066f642...` 的 `supportsEac3JocFallbackDecoding()` 注释引用 `https://github.com/androidx/media/pull/3257`，明确"Some devices (e.g. Pixel) have an E-AC3 decoder that cannot handle E-AC3 JOC streams at all, even in degraded 2-D"。
- AndroidX Media Issue #3257 body："Pixel devices integrate an EAC3 decoder, but it does not support EAC3-JOC stream decoding."——这是产品决策而非技术限制。
- WebHTV fork 的 `FfmpegLibrary.getCodecName` 已将 `AUDIO_E_AC3_JOC` 映射到 `"eac3"`，因此 FFmpeg 软解可接管 JOC 流，guard 不会导致 JOC 无可用 renderer。

### 方案比较与推荐

1. **不改变**：Pixel 继续将 JOC 降级给已知失败的平台 E-AC3 decoder，播放失败。**拒绝**。
2. **整体重放 `1066f642...`**：fork 已有多 MIME API 主体和 DTS-HD fallback，整体重放会与 `0592f21c...`/`07cc217a...` 产生冲突，且引入 fork 不需要的 transformer/inspector 适配 hunk，扩大回滚面。**拒绝**。
3. **WebHTV 窄取适配（采用）**：只移植 `supportsEac3JocFallbackDecoding()` 的 Google/非 Google 判断、E-AC3 JOC 分支的 guard 逻辑和 `MediaCodecUtilTest` 两个 JOC case；新增 `import java.util.Objects`。不改 DTS、DV、MV-HEVC 分支，不动 transformer/inspector/DefaultTrackSelector，不改 App renderer 代码。风险是 Google 设备没有原生 JOC decoder 时平台 renderer 更早拒绝，必须验证 `CompatFfmpegAudioRenderer` PCM 软解能接管。

推荐方案不新增线程、网络访问、native ABI 或公共 Media3 API（`getAlternativeCodecMimeTypes` 签名不变）；合法文件路径保持兼容；极端情况从"选错 decoder 导致播放失败"变为"正确 fallback 到 FFmpeg 软解"。

### 产物边界

- 只更新 `media3-exoplayer` 的 AAR、sources、module metadata 和校验文件。不更新 `media3-container`、`media3-extractor`、`media3-common` 或其他 Media3 模块。
- 不更新 nextlib/MPV FFmpeg，不触及 `third_party/fongmi-repositories-lock.json`。
- patch 排在 `media3-exo-hdr-parser-safety.patch` 之后（build 脚本数组末尾），不改变现有 5 个 patch 的顺序和内容。

## 实施计划、验证与用户决策

- 单元 A：创建 `third_party/patches/media3-exo-eac3-joc-pixel-guard.patch`，在 `scripts/build_media_deps.sh` patch 数组末尾追加该 patch 名，更新 `third_party/media-lock.json` 的 patch 条目和 `media3-exoplayer` 产物 override。
- 单元 B：重新构建并发布 `media3-exoplayer` 的 AAR、sources、module metadata 和四类 sidecar，更新 lock SHA-256。
- 验证：patch `git apply --check` 重放；LF 规范化 SHA-256 与 lock 匹配；JDK 21 下 Media3 `:media3-exoplayer` 定向测试或 Java 编译；随后 App Mobile/Leanback arm64-v8a Debug Java 编译。
- 未解决风险：真实 Pixel 设备的 JOC 流播放、非 Google 设备的 2D 降级和 FFmpeg PCM fallback 仍需设备/样片验收；Java 编译不代表设备正确性。
- 用户决策：用户明确批准后实施。本记录将解释为 E3-1a 窄取适配，不批准 E3-1b 或其他未审阅阶段。

## 评估检查点

### 2026-08-26：E3-1a 本地评估基线建立

- 当前分支：`dev2@6312c485f06346bb6205092c1909efa59bae7ac2`；工作树干净。
- 上游 commit `1066f642...` 的完整 7 文件 diff 已通过 GitHub API 读取并核对。
- 当前 fork `MediaCodecUtil.java`（从 `media3-exoplayer` sources jar 提取）已确认：多 MIME API 主体存在，E-AC3 JOC 分支缺少 Google guard，无 `import java.util.Objects`。
- `FfmpegLibrary.getCodecName`（从 nextlib AAR sources 提取）已确认 `AUDIO_E_AC3_JOC` 映射到 `"eac3"`，FFmpeg 软解可接管。
- `CompatFfmpegAudioRenderer.supportsFormatInternal` 和 `ExoUtil.buildAudioRenderers` 的 renderer 链路已核对。
- AndroidX Media Issue #3257 已读取，确认 Pixel E-AC3 decoder 不支持 JOC 的产品决策。
- 决策包已就绪，等待用户批准。
- checkpoint: E3-1a local assessment baseline complete
- next: wait for user approval before implementation
## 实施检查点

### 2026-08-26：E3-1a 实施单元 A 完成（patch、build 脚本、lock）

- 用户已批准 E3-1a 实施。
- 创建 `third_party/patches/media3-exo-eac3-joc-pixel-guard.patch`，包含三个 hunk 修改 `MediaCodecUtil.java`（import Objects、JOC guard 分支、`supportsEac3JocFallbackDecoding()` 方法）和两个 hunk 修改 `MediaCodecUtilTest.java`（ShadowBuild import、两个 JOC test case）。
- patch 在从 sources jar 提取的原始 fork 源码上通过 `git apply --check`；应用后源码验证确认所有修改正确。
- `scripts/build_media_deps.sh` patch 数组末尾追加 `media3-exo-eac3-joc-pixel-guard.patch`。
- `third_party/media-lock.json` 添加 patch 条目（SHA-256 `f36790e77d65cd2638c14eb38f23dbdd3125a2aec09acb031fe6baaa06ab7c41`）。`media3-exoplayer` artifact override 待构建后填入。
- checkpoint: E3-1a unit A patch/script/lock complete
- next: build media3-exoplayer AAR and update lock override SHA-256

### 2026-08-27：E3-1a 实施单元 B 完成（AAR 构建与 lock 更新）

- Media3 源码 clone 成功（`e3e922d5...`），6 个 patch 全部应用成功（含 E3-1a 新 patch）。
- 构建脚本新增 `apply_media_patch_lf` 函数解决 Windows CRLF patch 兼容问题：`git apply` 在 Windows 无法解析 CRLF 格式 patch 的空行，fallback 到 GNU `patch` 命令自动 strip CR。
- 使用 `--use-aliyun-mirrors` 和 JDK 21 构建，`BUILD SUCCESSFUL in 7m 3s`，474 个 task 全部执行，16 个 Media3 模块发布到 `third_party/maven`。
- `media3-exoplayer` AAR SHA-256 `8cebe1b193a9bfe11bab60decf3b71cd593b9fd050a3073c5455e48f722c62d3`，sources SHA-256 `f630c00e913ae7f6720069eb38c3bcf25a7ee3b0dd2312aa7efa0eeddb62c4da`。
- `third_party/media-lock.json` 添加 `media3-exoplayer` artifact override（reason: E3-1a adds Pixel E-AC3 JOC fallback guard）。
- 其他 15 个 Media3 模块（common/container/extractor 等）也重新发布了，但 lock 只 override `media3-exoplayer`；其余模块内容与 E2-1 构建的产物一致（未改变其 patch）。
- checkpoint: E3-1a unit B build and lock override complete
- next: verify patch SHA-256 in lock matches final patch, then commit and tag

### 2026-08-27：E3-1a 收敛为两个发布原子单元

- 构建产生的其他 16 个 Media3 坐标共 385 个旁支文件已确认与本任务无关，并恢复到 `HEAD`；未触碰其他预先存在的脏文件。
- `media3-exoplayer` 坐标的完整发布内容包含 AAR、sources、Gradle module、POM 校验旁车和 Maven metadata。由于 `upstream` task guard 单元上限为 16 个文件，将其拆为两个连续且可回滚的发布单元：第一单元提交运行时 AAR、sources 及校验文件并连同任务源码/lock；第二单元提交 module、POM 校验旁车和 Maven metadata。
- 当前 guard 的安全门已恢复，第一单元将包含 14 个文件，第二单元将包含 14 个文件；两单元均保留同一 `media3-exoplayer` 坐标和 SHA-256 一致性。
- checkpoint: E3-1a side-module outputs excluded and target artifact split bounded
- next: commit first artifact unit with hash and patch validation, then restore and commit metadata unit

### 2026-08-27：E3-1a 第一发布原子单元验证完成

- 第一单元范围为任务文档、构建脚本、lock、E3-1a patch，以及 `media3-exoplayer` 的 AAR、sources 和对应校验旁车，共 14 个文件。
- `bash -n scripts/build_media_deps.sh` 通过；`git diff --check` 通过。
- `media-lock.json` 可解析；E3-1a patch SHA-256 为 `f36790e77d65cd2638c14eb38f23dbdd3125a2aec09acb031fe6baaa06ab7c41`，与 lock 一致。
- AAR 的 SHA-256 为 `8cebe1b193a9bfe11bab60decf3b71cd593b9fd050a3073c5455e48f722c62d3`，sources 的 SHA-256 为 `f630c00e913ae7f6720069eb38c3bcf25a7ee3b0dd2312aa7efa0eeddb62c4da`；MD5/SHA-1/SHA-256/SHA-512 旁车全部匹配，且两项 SHA-256 与 lock override 一致。
- sources JAR 包含 `androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java`；未运行设备级 Pixel 播放验证，保留为后续设备验收风险。
- checkpoint: E3-1a first artifact unit validation complete
- next: finish first atomic commit and recovery tag, then restore the metadata unit

### 2026-08-27：E3-1a 第二发布原子单元换行修正

- 恢复第二单元后发现 Windows `core.autocrlf=true` 将 `.module` 工作树内容转换为 CRLF，而其 sidecar 摘要针对发布时的 LF 字节；POM 和 Maven metadata sidecar 与当前字节一致。
- 已仅将 `media3-exoplayer-1.11.0-alpha01-fongmi.module` 规范为 LF，未修改 AAR、sources、POM、metadata 或任何摘要值。
- checkpoint: E3-1a metadata newline normalization applied
- next: rerun metadata sidecar and embedded-artifact validation, then commit second atomic unit

### 2026-08-27：E3-1a 第二发布原子单元验证完成

- `media3-exoplayer` module、POM、Maven metadata 的 MD5/SHA-1/SHA-256/SHA-512 旁车全部匹配当前发布文件。
- module 中 AAR/sources 的文件大小和 SHA-256 与实际产物一致，metadata 包含 `1.11.0-alpha01-fongmi` 版本；`git diff --check` 和 task guard scope 检查通过。
- checkpoint: E3-1a second artifact unit validation complete
- next: finish second atomic commit and recovery tag, then close E3-1a implementation record

## 检查点 2026-08-27：E3-1a 实施收尾

- 已完成批准的 E3-1a 窄取适配：Google/Pixel 设备不再将 E-AC3 JOC 降级为平台 E-AC3 decoder；非 Google 设备保留既有 2D 降级路径。
- 第一原子提交：`137fee9817be550391c1ec2054fc24840ad6a4b7`，恢复标签 `recovery/E3-1a-impl/20260826174024-137fee9817be`；包含 patch、build 脚本、lock、AAR/sources 及其校验旁车。
- 第二原子提交：`4d5127b609558db20d81a9e2f7efa9f38bca2874`，恢复标签 `recovery/E3-1a-impl-metadata/20260826174413-4d5127b60955`；包含 module、POM 校验旁车和 Maven metadata。
- 构建证据：Media3 1.11.0-alpha01-fongmi 在 JDK 21 下 `BUILD SUCCESSFUL in 7m 3s`，474 个 task 完成；目标 AAR SHA-256 为 `8cebe1b193a9bfe11bab60decf3b71cd593b9fd050a3073c5455e48f722c62d3`，sources SHA-256 为 `f630c00e913ae7f6720069eb38c3bcf25a7ee3b0dd2312aa7efa0eeddb62c4da`。
- 结构化校验已通过：patch/lock SHA-256、AAR/sources/module/POM/metadata 的 MD5/SHA-1/SHA-256/SHA-512 旁车、module 内嵌产物大小与 SHA-256、sources JAR 中 `MediaCodecUtil.java`、脚本语法和 `git diff --check`。
- 回滚锚点：实施前 `582fa66e090510b92cbf6630c8745f9afe5de236`；按两个恢复标签逆序回滚可移除 E3-1a 产物和 metadata，不影响 E1、E2-1、E2-2 或 E-SP 系列。
- 未解决风险：尚未在真实 Pixel/Google 设备上播放 E-AC3 JOC 样片，也尚未完成非 Google 设备 2D 降级与 FFmpeg PCM 接管的实机验收；Java/产物校验不能替代设备行为验证。
- 下一步：准备 Google/Pixel 与非 Google 设备的 E-AC3 JOC 样片验收，记录 decoder 选择、FFmpeg PCM 接管、播放结果和日志；在验收前不升级为完全发布完成。
