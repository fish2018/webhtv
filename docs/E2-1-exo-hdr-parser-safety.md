# E2-1：Exo HDR / Dolby Vision parser safety

- 任务 ID：`E2-1`
- 所属分类：Exo
- 状态：已完成
- 唯一任务文档：`docs/E2-1-exo-hdr-parser-safety.md`
- WebHTV 同步目标：`fish2018/webhtv@c410bf4f40a0ef7babb5b6281b97fa4bc621c24d`
- WebHTV 同步基线：共同祖先 `b2eccc357662065e02e49af4caff4c059cf508f3`
- FongMi Media 基线：`FongMi/media@e3e922d5c01bc0b564849940fe589daf37360d15`

## Recovery anchor

- 目标：让 Exo 遇到截断、过短或越界的 Dolby Vision/HDR 配置时安全拒绝输入，并按 CTA-861.3 规范编码 Matroska 最低 mastering luminance；正常 MP4/MKV、现有 DV RPU 和 fallback 行为保持不变。
- 接受标准：Media3 container/extractor 定向测试通过；parser-safety patch 可在现有四个 Media3 补丁之后重放；只发布受影响的 `media3-container`/`media3-extractor` AAR 与 sources；App Mobile/Leanback Java 编译通过；初始脏文件不被覆盖或提交。
- 允许路径：本任务文档、主评估索引、`third_party/patches/media3-exo-hdr-parser-safety.patch`、`scripts/build_media_deps.sh`、`third_party/media-lock.json`、Media3 container/extractor Maven 产物。
- 回滚：按 container、extractor、接线/文档三个单元分别回滚；完整恢复目标为实施前 `09af6a0069490a17e1f626407b483de3a688a4bf`，不回滚 E1、E2-2、MPV 或其他本地提交。

## 上游提交台账与处置

在线核对时间：2026-08-26（Asia/Shanghai）。GitHub API 返回 `fongmi-sync` 头 `c410bf4f40a0ef7babb5b6281b97fa4bc621c24d`；相对共同祖先只有以下两个 WebHTV 提交，台账完整：

| 仓库 | 完整 commit | 处置 | 范围 |
| --- | --- | --- | --- |
| `fish2018/webhtv` | `e19289a3c9871563f891500bdc2d42be6be23f3d` | 实施候选；窄合并 | parser-safety patch、Media3 构建顺序/lock、container/extractor 产物 |
| `fish2018/webhtv` | `c410bf4f40a0ef7babb5b6281b97fa4bc621c24d` | 文档收尾；吸收内容并保留本地后续检查点 | E2-1 文档和主评估索引 |

关联 FongMi/media 源提交：

| 完整 commit | 关联与处置 |
| --- | --- |
| `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` | 仅取 Matroska minimum mastering luminance 语义；不整提交替换本地 fork。 |
| `0cefd3ceec27444cf8faf02486b472bab39109fe` | 仅取短 DV config、major-version 和 MP4 box-boundary hunk；拒绝连带 CSD、compatible BL、renderer/output policy。 |
| `b63139c6432caa3f058e7f0496f0d754aa0eaa93`、`249774647b026e16b56467eb5d79479816f79f11`、`08c664eb8a213a956ff2c8b3d0fcea49902a81fa` | 当前 fork 已有等价覆盖，仅登记，不重复应用。 |

## 证据与设计决策

### 当前 WebHTV 路径

- `scripts/build_media_deps.sh:230-247` 固定按 `media3-danmaku-live`、`media3-dolby-vision-matroska`、`media3-upstream-playback-fixes-2026-08`、`media3-deferred-cues` 顺序应用 patch；新 parser patch 必须排在 deferred Cues 之后。
- `third_party/media-lock.json:4-32` 锁定 Media3 源 commit、每个 patch 的 SHA-256 和受影响 AAR/sources；`gradle/libs.versions.toml` 与 `settings.gradle:18` 证明 App 消费本地完整 Maven publication。
- E2-2、Matroska Dolby Vision RPU、HDR10 fallback 和 deferred Cues 是必须保留的本地契约；本阶段不修改 renderer、CSD、RPU、网络、seek 或 MPV。

### 外部证据

- Android AOSP `MediaFormat.KEY_HDR_STATIC_INFO`（在线核对 2026-08-26）明确要求 ByteBuffer 包含 CTA-861.3 Static Metadata Descriptor；这支持按 CTA-861.3 的 `0.0001 cd/m²` 单位写入最低 mastering luminance。
- AndroidX Media `release` 的 `DolbyVisionConfig.parse()` 仍从配置数据读取版本、profile 和 level；FongMi 源提交提供了可独立测试的长度/版本拒绝与 box 边界保护。Google ExoPlayer 对应 extractor 路径作为成熟相关实现，支持在解析外部字节前校验边界。
- GitHub API 未发现两个 FongMi 源提交关联的 Pull Request；不把 PR 标题或提交标题当作正确性证据。论文、基准和性能报告对本次常数时间 parser safety 变更不适用。
- 上游 WebHTV `e19289a3c9871563f891500bdc2d42be6be23f3d` 声称 JDK 21 定向测试、五 patch 重放、两个 release AAR、Mobile/Leanback Java 编译和 SHA-256 一致；这些是待在当前分支复核的候选证据，不直接视为本地验证结果。

### 方案比较与推荐

1. **不变**：继续承受 malformed DV 输入导致的异常/错误元数据风险；拒绝。
2. **原样移植上游大提交**：会连带 DV CSD、compatible BL 和 renderer/output policy，覆盖已落地的 E2-2/本地策略；拒绝。
3. **WebHTV 窄适配（采用）**：只引入 parser safety 与 CTA-861.3 luminance hunk，保留本地 Media3 patch、DV RPU、P8.1 CSD、fallback 和输出策略；按三个单元回滚。

推荐方案不新增线程、网络访问、native ABI 或公共 Media3 API；合法文件路径保持兼容，极端 malformed 文件从未定义行为变为受控解析失败。每个 boundary check 是常数时间，包体变化限于受影响 Media3 publication。

## 实施计划、验证与用户决策

- 单元 A：合并 parser patch、`scripts/build_media_deps.sh`、`third_party/media-lock.json` 和本记录。
- 单元 B：只更新 `media3-container` 的 AAR、sources、module metadata 与校验文件。
- 单元 C：只更新 `media3-extractor` 的 AAR、sources、module metadata 与校验文件。
- 合并收尾：纳入 `fongmi-sync` 两个提交的内容；主评估索引保留本地已完成的后续检查点，不用上游旧文档覆盖本地诊断。
- 最小验证顺序：`git diff --check` 与 lock/产物 SHA-256；五 patch `git apply --check` 重放；JDK 21 下 Media3 定向测试/Java 编译；随后 Mobile 与 Leanback arm64-v8a Debug Java 编译。
- 未解决风险：真实厂商 DV codec、设备级 HDR 输出和非标准文件兼容性仍需设备/样片验收；不把 Java 编译结果表述为设备正确性证明。
- 用户决策：用户明确要求合并上游最新代码，本记录将其解释为批准 `fongmi-sync@c410bf4f40a0ef7babb5b6281b97fa4bc621c24d` 的 E2-1 窄适配，不批准 E3-1a 或其他未审阅阶段。

## 当前状态

- 评估结论：E2-1 已完成，范围未超出批准的 parser-safety 窄适配。
- 当前进度：parser patch、固定应用顺序、container/extractor publication、lock override、当前树定向验证和上游双亲合并均已完成。
- 实施提交：`c9ddcf2bbdf902b771694cabbf85930d467bd8ba`、`f2db5952ebec66e94e8cddce6230cff035202274`、`1e96933f5fdc35e2eec49c474f5e5552cba4dd7a`。
- 恢复标签：`recovery/E2-1-sync-parser/20260826104622-c9ddcf2bbdf9`、`recovery/E2-1-sync-container/20260826105926-f2db5952ebec`、`recovery/E2-1-sync-extractor/20260826110557-1e96933f5fdc`。
- 最终双亲合并提交：`af28547d965fb862a4b8d005abe0eac07fe5196a`；父提交为 `1e96933f5fdc35e2eec49c474f5e5552cba4dd7a` 与 `c410bf4f40a0ef7babb5b6281b97fa4bc621c24d`。
- 最终恢复标签：`recovery/E2-1-sync-closeout/20260826131620-af28547d965f`。
- 当前未解决风险：真实厂商 DV codec、设备级 HDR 输出和非标准文件兼容性仍需设备/样片验收；Java 编译不代表设备正确性。
- 下一动作：E2-1 已完成；进入队列中的下一项 Exo 任务 `E3-1a` 前先评估并等待用户批准。

## 评估检查点

### 2026-08-26：fongmi-sync 最新头评估完成

- 当前分支：`dev2@09af6a0069490a17e1f626407b483de3a688a4bf`；远端目标：`fish2018/fongmi-sync@c410bf4f40a0ef7babb5b6281b97fa4bc621c24d`。
- 共同祖先：`b2eccc357662065e02e49af4caff4c059cf508f3`；目标领先 2 个提交，完整提交台账已记录。
- 无副作用合并预演显示只有主评估索引存在内容冲突；代码、脚本、锁、补丁和 Media3 产物没有三方冲突。
- 已核对本地 Media3 消费路径、FongMi/media 源提交、Android AOSP `KEY_HDR_STATIC_INFO` 契约和上游提交关联状态；建议采用 E2-1 窄适配，不引入 E3-1a 或其他阶段。
- checkpoint: E2-1 fongmi-sync head assessment complete
- next: commit assessment record, then start implementation guard

### 2026-08-26：单元 A 哈希验证修正

- 发现：Windows `core.autocrlf=true` 下，`git hash-object` 默认输出 Git blob SHA-1，不能直接与 lock 的文件 SHA-256 比较；这不是补丁内容变化。
- 修正：用 `git hash-object --path` 与上游 blob ID 验证规范化内容，再用上游 LF blob 的 `sha256sum` 验证 lock 值。
- 当前进度：单元 A 的 parser patch、固定应用顺序和 lock patch 条目已写入，尚未提交。
- checkpoint: unit A hash verification command corrected
- next: run normalized blob and LF SHA-256 checks, then commit unit A

### 2026-08-26：实施单元完成，进入合并收尾

- parser 单元提交 `c9ddcf2bbdf902b771694cabbf85930d467bd8ba`，container publication 单元提交 `f2db5952ebec66e94e8cddce6230cff035202274`，extractor publication 单元提交 `1e96933f5fdc35e2eec49c474f5e5552cba4dd7a`；三个提交均已创建 annotated recovery tag。
- container/extractor 的 AAR、sources、module、POM 及四类 sidecar 已按上游规范化 blob 校验；lock 中 patch、AAR 和 sources SHA-256 已对应更新。
- 当前合并冲突只涉及本任务文档和主评估索引；脚本、锁、补丁与产物没有新增冲突。上游 `e19289a3c9871563f891500bdc2d42be6be23f3d`、`c410bf4f40a0ef7babb5b6281b97fa4bc621c24d` 的内容已吸收，未采用其覆盖本地诊断的文档版本。
- checkpoint: E2-1 implementation units complete; merge closeout pending
- next: stage the resolved documents and run current-tree verification

### 2026-08-26：文档合并稿完成，进入当前树验证

- 合并稿保留主评估索引的本地检查点、`E-SP3` 状态和 E2-2/C2/MPV 边界，同时吸收上游 E2-1 完成记录；未修改脚本、锁、补丁或 Media3 产物。
- 当前合并仅待将两份文档标记为已解决，并运行既定的 Media3 定向测试与 Mobile/Leanback Java 编译。
- checkpoint: E2-1 documentation merge draft complete
- next: stage the two resolved documents and run current-tree verification

### 2026-08-26：当前树定向验证通过

- JDK 21 下运行 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon`，Gradle daemon 日志记录 `BUILD SUCCESSFUL in 6m 22s`。
- 五个 Media3 patch 按 LF 规范化内容与 lock SHA-256 全部匹配；`media3-container` 与 `media3-extractor` 的 AAR、sources SHA-256 全部匹配 lock override。
- 当前工作树无未解决冲突，验证范围未触及受保护的 `third_party/sources/media` 外部 checkout；Media3 单元测试仍以既有上游 JDK 21 验证记录为依据。
- checkpoint: E2-1 current-tree verification passed
- next: create the two-parent merge commit and recovery tag

### 2026-08-26：双亲合并与恢复点完成

- 双亲合并提交 `af28547d965fb862a4b8d005abe0eac07fe5196a` 已创建，父提交为本地 E2-1 实施头 `1e96933f5fdc35e2eec49c474f5e5552cba4dd7a` 与上游收尾头 `c410bf4f40a0ef7babb5b6281b97fa4bc621c24d`。
- annotated recovery tag：`recovery/E2-1-sync-closeout/20260826131620-af28547d965f`。
- checkpoint: E2-1 merge and recovery point complete
- next: wait for approval before assessing E3-1a
