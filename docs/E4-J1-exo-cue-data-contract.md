# E4-J1 Exo Cue 数据契约

## Recovery anchor

- 目标：为 Exo 字幕增加 `collisionAvoidance`、`textRegionHeight` 和 `LetterSpacingSpan` 的完整数据契约，但不启用新的碰撞、viewport 或字号行为。
- 状态：已实施并完成验证；实现提交 `af78e3b7656d6a0f210d7344b3852f301690c417`；恢复 tag `recovery/E4-J1/20260827133106-af78e3b7656d`。
- 分支/HEAD：`fongmi-sync` / `a20a31a7c6be2454459db68ec41b7cebf824d1a6`。
- 上游来源：`FongMi/media@6794d75b7a39db42dcfcab18c915f0da165515b5`（ASS collision 数据契约）；`FongMi/media@3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`（TTML region/spacing 数据契约）。
- 当前 fork：`e3e922d5c01bc0b564849940fe589daf37360d15`；已有基础 `4db2ca63351a4abaf0daf3c1a65913b846215ab9`、`55fe0481b9429d3a12f6b42ed1945cd12cc88c9c`、`56fd27d919504bfebd78172acb99cf7e3bc8f490`、`9d7ea02aae18e03db0407e2146b50908acece81c`。
- 保护：`AGENTS.md`、`.codex/scripts/task_guard.sh`、`docs/agents-md-effective-constraints-review-2026-08-21.md` 和所有其他初始脏路径不修改、不提交；尤其不触碰主仓库 `third_party/sources/media`。
- 接受标准：新字段默认值与旧 Bundle 兼容；Builder、`buildUpon()`、`equals/hashCode`、Serializable/Binder Bundle 和 custom span 往返完整；现有 z-index、bitmapHeight、字幕位置、parser-side stacking、字号策略和 extraction 开关不变。

## 决策与范围

用户已批准实施 E4-J1。采用 WebHTV 窄化方案：

- 移植 `Cue.collisionAvoidance`、`Cue.textRegionHeight`、`LetterSpacingSpan` 及其完整序列化/复制/比较支持。
- 保留现有 `Cue.zIndex`、`Cue.bitmapHeight`、App 字号/位置控制和 fork SSA `applyStacking()`。
- 不移植 ASS `Collisions: Reverse` parser 行为、TTML parser layout、Canvas collision、`PlayerView` viewport、bitmap scaling、WebView parity 或任何 extraction 开关。
- `collisionAvoidance` 默认 `NONE`，`textRegionHeight` 默认 `Cue.DIMEN_UNSET`；本阶段不由现有 parser 产生 `UP/DOWN`。

### 研究与证据

| 证据 | 来源/修订 | 结论与决策影响 |
| --- | --- | --- |
| 上游实现与测试 | `FongMi/media@6794d75b7a39db42dcfcab18c915f0da165515b5`；评估索引检查点 11、16 | `collisionAvoidance` 必须进入 Cue 构造、`buildUpon()`、equals/hashCode 和 Bundle；不能整体 cherry-pick，因 fork 已有 parser-side stacking。 |
| 上游实现与测试 | `FongMi/media@3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`；评估索引检查点 13、16 | `textRegionHeight` 和 `LetterSpacingSpan` 是 TTML layout 的跨模块数据；本阶段只保留字段和 span 往返，不伪造 Format 画布尺寸或启用缩放。 |
| 当前 fork 源码 | `third_party/sources/media` 已发布基线 `e3e922d5c01bc0b564849940fe589daf37360d15`；`Cue.java`、`CustomSpanBundler.java`、SSA parser | 已有 `zIndex`/`bitmapHeight`，缺少三个新契约；SSA 已有 `applyStacking()`，因此不能同时打开 Canvas collision。 |
| 当前 App 消费 | `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`、`SubtitleDialog.java`、`PlayerManager.java` | App 明确关闭 embedded font sizes 并提供字号/位置设置；本阶段不改变这些调用和 UI 行为。 |
| 平台/项目规范 | AndroidX Media Bundleable/Cue serialization、Media3 player gates；见 `.codex/skills/upstream-integration-governor/references/` | 新字段必须向后兼容旧 Bundle，并在 AAR、sources、锁和应用编译链中保持一致。 |

### 方案比较

1. 不改：没有新的 Cue 字段，后续 ASS/TTML 阶段无法安全传递策略和区域信息。
2. 直接采用上游：会把 parser、Canvas、viewport 和字号行为一起带入，覆盖或叠加 WebHTV 既有 stacking，风险超出本阶段。
3. WebHTV 窄化方案（采用）：只引入可独立验证的数据契约和 span 序列化，默认不改变用户可见行为；后续行为阶段再单独审批。

## 计划实施文件

- `third_party/patches/media3-exo-cue-data-contract.patch`
- `scripts/build_media_deps.sh`
- `third_party/media-lock.json`
- `third_party/maven/androidx/media3/media3-common/1.11.0-alpha01-fongmi/`
- 本文档与 `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`

补丁只允许包含 Media3 common 的 Cue/custom-span 生产文件及对应测试文件；不包含 extractor/ui/App 源码。

## 验证、回滚与状态

- 首先运行 common 的 Cue/Bundle/custom-span 定向测试和 common 编译，再编译 extractor、ui 及 Mobile/Leanback arm64 App Java 接线。
- 核对 AAR、sources、module、sidecar、锁文件和补丁 SHA-256；确认非 class 内容及既有 `zIndex`/`bitmapHeight` 行为不变。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`；失败时只移除本任务补丁与 common artifact override，不触碰 E4-1 及其它阶段。
- 当前下一步：开始下一项 Exo 候选的只读评估；不得把 E4-J2/E4-2/E4-4 行为并入本任务。

## Checkpoint 1：2026-08-27 补丁链恢复

- 完成：在 `/private/tmp/e4j1-media-clean` 从 `e3e922d5c01bc0b564849940fe589daf37360d15` 按 `scripts/build_media_deps.sh` 顺序重放 `media3-danmaku-live`、`media3-dolby-vision-matroska`、`media3-upstream-playback-fixes-2026-08`、`media3-deferred-cues`、`media3-exo-hdr-parser-safety`、`media3-exo-pixel-eac3-joc-guard`、`media3-exo-dts-14bit-frame-size`、`media3-exo-subtitle-byte-safety`，全部 `--check` 和应用成功。
- 诊断结论：此前第 5 个补丁的失败来自对未按顺序应用的树做独立检查；在正确前序补丁上下文中 E2-1 补丁可重放，不需要修改既有补丁或使用三方合并。
- 主仓库：`fongmi-sync` / `a20a31a7c6be2454459db68ec41b7cebf824d1a6`；预存脏路径保持不变，未触碰 `third_party/sources/media`。
- 验证：8 个补丁应用成功；clean checkout 当前仅包含预期 Media3 补丁改动。
- 未解决：E4-J1 common 数据模型尚未移植，AAR/lock 尚未更新。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`。
- 下一动作：读取 clean checkout 中 `Cue`/`CustomSpanBundler` 及上游 E4-J1 来源，实施字段和序列化测试。

## Checkpoint 2：2026-08-27 Cue 契约移植与验证环境

- 完成：clean checkout 已恢复 8 个既有 Media3 补丁；已移植 `Cue.collisionAvoidance`、`Cue.textRegionHeight`、`LetterSpacingSpan` 及 `CustomSpanBundler` 支持，并新增默认值、Builder、Bundle/Binder round-trip 与 custom span 测试。
- 源码范围：仅 common 的 `Cue.java`、`CustomSpanBundler.java`、新增 `LetterSpacingSpan.java` 及对应两组测试；未修改 parser、renderer、UI、App 或 MPV。
- 工作区：主仓库 `fongmi-sync` / `a20a31a7c6be2454459db68ec41b7cebf824d1a6`，预存脏路径未变；实现仍只在 `/private/tmp/e4j1-media-clean`。
- 验证：`git diff --check` 通过；首次 `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :lib-common:testDebugUnitTest ...` 未进入编译，因沙箱禁止访问 `/Users/macbookpro/.gradle/wrapper/dists/.../gradle-9.1.0-all.zip.lck`（环境权限问题）。放权后复用原缓存的构建又被残留 Gradle 输出目录阻断；改用隔离缓存后已越过权限点，但首次缓存生成在 build-logic 阶段因磁盘只余约 `1.8 GiB`、新缓存占 `661 MiB` 而 `No space left on device`，尚未进入 E4-J1 源码编译。
- 未解决：common 定向测试/编译、补丁生成、AAR/lock 更新尚未完成。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`。
- 下一动作：删除仅属于本任务的失败隔离缓存/生成目录，复用已有 Gradle 依赖缓存重跑同一 JDK 21 定向 common 测试与编译。

## Checkpoint 3：2026-08-27 common 定向验证通过

- 环境固定：按用户提供的 `https_proxy=http://127.0.0.1:7896`、`http_proxy=http://127.0.0.1:7896` 完成 Gradle 9.1.0 下载；发行包固定在 `/Users/macbookpro/.gradle/wrapper/dists/gradle-9.1.0-all/7wzd0jkjit61aq2p43wpjgij9/`，后续 Media3 构建复用该持久缓存，不再使用会被清理的临时 Gradle home。
- 验证命令：JDK 21、`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp/e4j1-robo` 下运行 `./gradlew :lib-common:testDebugUnitTest --tests androidx.media3.common.text.CueTest --tests androidx.media3.common.text.CustomCueBundlerTest :lib-common:compileDebugJavaWithJavac --no-daemon`。
- 结果：`BUILD SUCCESSFUL in 7m 1s`，205 个任务中 27 executed、178 up-to-date；`CueTest` 和 `CustomCueBundlerTest` 全部通过，common 主源码与测试源码编译通过。
- 兼容结论：`collisionAvoidance` 默认 `NONE`、`textRegionHeight` 默认 `DIMEN_UNSET`；旧 Bundle 缺字段仍使用默认值；Binder/Serializable/deprecated Bundle 和 pixel/em `LetterSpacingSpan` 往返均有测试覆盖。
- 未解决：增量 patch、common AAR/sources/sidecars、lock、App 接线编译尚未完成。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`。
- 下一动作：生成 E4-J1 独立 patch，并从同一 clean checkout 发布 `media3-common` AAR/sources。

## Checkpoint 4：2026-08-27 独立补丁与重放

- 补丁：`third_party/patches/media3-exo-cue-data-contract.patch`，SHA-256 `a6389e0c34a9a3e895072f84c9ffd13d0c883c5909d8887873717c337066eb61`；包含 common 的 `Cue`、`CustomSpanBundler`、新增 `LetterSpacingSpan` 和三组测试文件。
- 顺序：已在 `scripts/build_media_deps.sh` 的 Media3 补丁列表末尾加入 E4-J1，位于 E4-1 字幕 byte/charset safety 之后。
- 独立重放：在 `/private/tmp/e4j1-replay` 从 `e3e922d5c01bc0b564849940fe589daf37360d15` 依序应用 8 个既有补丁和 E4-J1，共 9 个补丁；每项 `git apply --check --unidiff-zero` 与应用均成功，最终 `git diff --check` 通过。
- 范围确认：E4-J1 补丁只触及 common 的 3 个生产文件和 3 个测试文件；没有 parser、renderer、UI、App、MPV 或 native 变更。
- 未解决：`media3-common` 发布物、lock、App 接线编译、最终提交/tag 尚未完成。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`。
- 下一动作：用 JDK 21、固定 Gradle 9.1.0 缓存和 7896 代理，仅发布 `lib-common` 到临时 Maven 目录。

## Checkpoint 5：2026-08-27 common 产物发布

- 发布：clean checkout 的 `:lib-common:publishReleasePublicationToMavenRepository` 成功，`BUILD SUCCESSFUL in 44s`；发布目录仅包含 `androidx/media3/media3-common/1.11.0-alpha01-fongmi`。
- 产物：AAR SHA-256 `5a4815ab415c0650bc8adaa60e518f32df548ddcfafb43e02381281a96dac6e5`；sources SHA-256 `31c38835a729f422ba25835733aa94ac23d8fe6445df2d725a71e46a11d86bfc`；module SHA-256 `e1bc887749ab6b69b1f8d5b56a129f5e2d6618f31ebe3db2a841b352b9f24c87`；POM SHA-256 `c5aff59e17d032f665ff55d26540fee65b8b0e843b834cea4d371c3cc7163f44`。
- 内容检查：AAR classes.jar 与 sources.jar 均包含 `Cue`、`CustomSpanBundler`、`LetterSpacingSpan`；module/POM 仅有预期产物大小/哈希变化。
- 锁定：`third_party/media-lock.json` 已登记 E4-J1 patch SHA-256 `a6389e0c34a9a3e895072f84c9ffd13d0c883c5909d8887873717c337066eb61` 及 common AAR/sources 哈希。
- 未解决：App Mobile/Leanback arm64 Java 接线编译、最终文档状态、原子提交/tag 尚未完成。
- 回滚锚点：`recovery/E4-J1/baseline-20260827102011-a20a31a7c6`。
- 下一动作：运行 Mobile/Leanback arm64-v8a Debug Java 编译，确认 App 对 common AAR 的间接兼容。

## Checkpoint 6：2026-08-27 接线验证与实施完成

- App 接线：JDK 21 下运行 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`，`BUILD SUCCESSFUL in 1m 25s`。
- Media3 直接消费者：在完整九补丁重放树中运行 `:lib-extractor:compileDebugJavaWithJavac` 与 `:lib-ui:compileDebugJavaWithJavac`，`BUILD SUCCESSFUL in 56s`；90 个任务中 23 executed、67 up-to-date。
- Common 验证：`CueTest`、`CustomCueBundlerTest`、`LetterSpacingSpanTest`、`:lib-common:compileDebugJavaWithJavac` 通过；`media3-common` 单模块发布 `BUILD SUCCESSFUL in 44s`。
- 最终产物：补丁 SHA-256 `a6389e0c34a9a3e895072f84c9ffd13d0c883c5909d8887873717c337066eb61`；AAR `5a4815ab415c0650bc8adaa60e518f32df548ddcfafb43e02381281a96dac6e5`；sources `31c38835a729f422ba25835733aa94ac23d8fe6445df2d725a71e46a11d86bfc`；module `e1bc887749ab6b69b1f8d5b56a129f5e2d6618f31ebe3db2a841b352b9f24c87`；POM `c5aff59e17d032f665ff55d26540fee65b8b0e843b834cea4d371c3cc7163f44`。
- 实施结论：E4-J1 数据契约已实现并验证；新字段保持默认无行为，因此现有字幕位置、字号、SSA stacking、Canvas/WebView 渲染和 extraction 开关不变。ASS/TTML 可见行为仍属于后续独立任务。
- 剩余风险：本阶段没有启用新 parser 或 renderer 行为，未做字幕截图/实机视觉验收；该验证应在后续 E4-J2/E4-2/E4-4 行为阶段按样片执行，不阻塞本数据契约阶段。
- 实现提交：`af78e3b7656d6a0f210d7344b3852f301690c417`；恢复 tag：`recovery/E4-J1/20260827133106-af78e3b7656d`。
- 下一动作：开始下一项 Exo 候选的只读评估；E4-J2/E4-2/E4-4 仍需单独决策和批准。
