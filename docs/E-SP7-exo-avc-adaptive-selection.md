# E-SP7：Exo H.264/AVC 自适应选轨与掉帧修复

- 任务 ID：`E-SP7`
- 类别：Exo 性能/播放行为
- 唯一文档：`docs/E-SP7-exo-avc-adaptive-selection.md`
- 状态：已实施，待真实设备 A/B 验收
- 当前恢复锚点：源码、定向单测和 Mobile arm64 Java 编译已通过；下一步执行 task guard 原子提交并创建 recovery tag。

## 目标与范围

用户反馈部分 H.264 视频使用 Exo 播放掉帧，而 MPV 和上游 `fish2018/webhtv` 正常。两张截图显示同一类 MediaCodec 硬解均为 `c2.mtk.avc.decoder`，但本地选择 `1920x1080 / 800Kbps`，上游选择 `1280x720 / 800Kbps`；本地诊断显示掉帧 150、CPU 约 158%，上游掉帧为 0。

本任务只修复 Exo 受约束轨道的自适应资格：保留最大分辨率、最大码率、最大帧率和已有自动降档控制，让 Media3 根据带宽选择轨道。无轨道限制时的既有最高码率行为保持不变。

明确不修改：Media3 AAR、`third_party/media-lock.json`、FFmpeg、MPV/native/JNI、预载、tunneling、帧调度、Dolby Vision、字幕、音频和其他播放器路径。

## 基线与证据

- 工作区分支：`dev4`
- 实施前 HEAD：`59fd2688f79d4e6ef46da23a162c8236920629e6`
- 实施前工作区：无脏文件
- 回滚锚点：`59fd2688f79d4e6ef46da23a162c8236920629e6`
- 受影响源码：`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`
- 回归测试：`app/src/test/java/com/fongmi/android/tv/player/exo/ExoUtilTest.java`

### 上游与平台来源

| 来源 | 完整 revision/commit | 证据与结论 |
| --- | --- | --- |
| `Silent1566/webhtv` 本地修复 | `1536c1bcc8d409d6f2479764a8fee20c45fd1fc8` | 将 `applyVideoLimit()` 的 `setForceHighestSupportedBitrate(false)` 改为 `true`，意图解决 HLS 多码率起播落到最低轨；副作用是受约束轨道变为固定选择。 |
| `fish2018/webhtv` `main` | `ec478b0b697422a7785171c7b51a35b7a526564e` | 当前上游 `ExoUtil.java` 在 `applyVideoLimit()` 中保持 `setForceHighestSupportedBitrate(false)`，与用户截图中的 720p 结果一致。 |
| AndroidX Media3 `release` | `2bc207851df311340767e913931ca7b28cab1794` | `DefaultTrackSelector` 的源码将 `forceHighestSupportedBitrate` 排除在自适应资格之外；为 `true` 时，满足约束的轨道会走 fixed selection。 |
| `FongMi/media` H.264 AU 候选 | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | 修改 TS 无 AUD 的 AU 边界、recovery point 和关键帧识别，不改变已经启动的 `c2.mtk.avc.decoder` 的轨道选择；本故障不移植。 |

### 根因判断

`ExoUtil.applyVideoLimit()` 同时设置了设备/策略上限和 `forceHighestSupportedBitrate=true`。在 Media3 中，这会让轨道失去自适应资格；当前设备因此在 800 Kbps 码率下固定选择 1080p，而上游选择 720p。1080p 像素量是 720p 的 2.25 倍，和本地高 CPU、连续掉帧相符。

这不是 `FongMi/media@aac6ec964681dd0476a33e3ad220ca7b5bf771f6` 所覆盖的 TS sample 边界问题：截图显示视频已经使用平台 H.264 decoder 播放，且没有无 AUD、recovery-point、首帧失败或 seek 错误证据。

## 方案决策

### 不变

维持本地 `true`：无需改动，但会保留受约束 H.264 固定高分辨率选轨和用户反馈的掉帧问题。不接受。

### 直接移植 H.264 AU/recovery-point 上游提交

整体移植 `aac6ec...`：它能改善特定 TS 无 AUD 流的 sample 边界、关键帧和 seek，但会触及本地 synthesized PUSI/EOF、4-byte start code、M2TS、Dolby Vision 和多个 Media3 AAR 文件；没有当前样片证据证明这些边界是本故障原因。拒绝作为本任务修复。

### WebHTV 窄适配，采用

只将 `applyVideoLimit()` 中的 `setForceHighestSupportedBitrate(true)` 恢复为 `false`。保留 `buildTrackSelector()` 在轨道限制关闭分支中的 `true`，避免回退本地此前修复的起播选轨行为。已有 `AutomaticVideoConstraintController` 的带宽/重缓冲/掉帧降档继续作为约束层使用。

这是一个单参数、可独立回滚的 Exo App 修复，不需要重建 native 或 Media3 AAR。

## 接受标准

1. `ExoUtilTest` 证明 `applyVideoLimit()` 保持自适应选轨，且源码中不再在该方法内强制最高码率。
2. Exo Mobile arm64 Java 编译通过。
3. 同一 Dangbei X7 Ultra、同一 H.264 资源、同一网络和同一播放位置的候选版本不再固定选择截图中的 1080p/800Kbps 组合；decoder 仍可使用 `c2.mtk.avc.decoder`。
4. 目标样片连续播放 30 秒无持续掉帧增长，掉帧结果达到上游同量级；若设备仍掉帧，必须以诊断数据区分轨道选择之外的原因，不宣称本任务已完全解决所有 H.264 问题。
5. 在带宽和设备余量足够时，自适应轨道可以向上切换到更高分辨率。
6. 既有轨道限制、自动降档、无轨道限制的最高码率行为、HEVC/其他格式、MPV 和 IJK 不回归。

## 验证计划

先运行：

```text
:app:testMobileArm64_v8aDebugUnitTest
--tests com.fongmi.android.tv.player.exo.ExoUtilTest
```

测试先红后改绿，再运行同一 Exo 定向测试和：

```text
:app:compileMobileArm64_v8aDebugJavaWithJavac
```

由于本任务不涉及 AAR、native、ABI 或 JNI，不运行 native 重建和 MPV 资产门禁。真实设备播放需要用户提供或复现同一 H.264 资源；未取得设备结果时，只报告源码/单测/编译证据，不把编译当成掉帧已消失的证明。

## 风险、回滚与发布

- 主要风险：自适应起播可能短暂选择较低分辨率；这是用带宽适配换取连续播放的预期行为。
- 主要保护：最大分辨率、码率、帧率约束仍由 `applyVideoLimit()` 设置；自动控制器继续按实际带宽和播放状态收紧约束。
- 回滚：恢复 `59fd2688f79d4e6ef46da23a162c8236920629e6`，或对本任务原子提交执行 `git revert`；不触碰 Media3/FFmpeg/MPV 资产。
- 提交前提：定向单测、Mobile arm64 Java 编译和适用的设备证据必须记录在本文；通过后由 task guard 创建一个原子提交及 `recovery/E-SP7/<timestamp>-<commit>` annotated tag。

## Checkpoint 1：2026-09-03 E-SP7 实施启动

- 用户决定：明确批准“开始修复”。
- 诊断完成：用户截图、本地 `1536c1...`、上游 `ec478b...` 和 Media3 `release` 语义共同指向受约束选轨被强制固定的回归。
- 当前工作区：`dev4`，HEAD `59fd2688f79d4e6ef46da23a162c8236920629e6`，task guard 已启动，尚无任务代码改动。
- 下一步：先把既有 `ExoUtilTest` 改成自适应选轨回归断言并运行红灯测试，然后修改 `ExoUtil.java` 单个参数。

## Checkpoint 2：2026-09-03 E-SP7 红绿验证完成

- TDD 红灯：旧实现上运行 `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoUtilTest --no-daemon`，16 项中仅 `videoLimits_keepAdaptiveSelectionInsideTheConstraints` 失败，失败原因是 `applyVideoLimit()` 仍包含 `setForceHighestSupportedBitrate(true)`。
- 实现：`ExoUtil.applyVideoLimit()` 将该参数改为 `setForceHighestSupportedBitrate(false)`；`buildTrackSelector()` 无轨道限制分支的 `true` 未修改。
- TDD 绿灯：同一 ExoUtilTest 定向任务通过，Gradle `BUILD SUCCESSFUL in 58s`。
- Java 编译：`:app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon` 通过，Gradle `BUILD SUCCESSFUL in 57s`。
- 既有警告：Java deprecation 提示仍存在；本任务未新增编译错误。未运行 native/MPV 门禁，因为本任务不修改 AAR、native、ABI、JNI 或 MPV。
- 当前工作区：分支 `dev4`，task guard `E-SP7/upstream` active；任务源码、测试、文档和评估索引在声明范围内。
- 未决：真实 Dangbei X7 Ultra 同资源 A/B 尚未执行，因此当前结论限于源码、单测和编译证据，不能宣称设备端所有 H.264 掉帧均已消失。
- 下一步：执行一次 scoped diff/check 后调用 task guard finish，创建原子提交和 `recovery/E-SP7/<timestamp>-<commit>` annotated tag。

## Checkpoint 3：2026-09-03 E-SP7 验证与提交收尾

- Mobile arm64 Java 编译：`:app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon`，`BUILD SUCCESSFUL in 57s`。
- Leanback arm64 Java 编译：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon`，`BUILD SUCCESSFUL in 2m 5s`。
- 定向测试：`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoUtilTest --no-daemon`，`BUILD SUCCESSFUL in 58s`，16 项测试通过。
- 红绿证据：修改前的新断言按预期失败；修改 `applyVideoLimit()` 后同一测试通过。
- 变更审计：`git diff --check` 通过；变更仅限 `ExoUtil.java`、`ExoUtilTest.java`、本任务文档和主评估索引。未改 Media3 AAR、lock、FFmpeg、MPV、native、JNI 或其他播放器。
- 设备限制：当前 ADB 只有 `emulator-5554` 和 `emulator-5556`，没有 Dangbei X7 Ultra；真实同资源分辨率、decoder、掉帧和 CPU A/B 尚待设备条件具备后补验。
- 当前状态：源码修复已完成并通过静态/单测/两产品 Java 编译，待 task guard 完成原子提交和本地 recovery tag。
- 下一步：执行 task guard finish；后续设备 A/B 结果追加到本文，不改变本次源码提交边界。

## Checkpoint 4：2026-09-03 beta 合并后复核

- 合并基线：当前第一父为 `ff438637b89587cf4f378843338a4122ba07e9d3`，beta 第二父为 `bcfe7b22a05e32913448a228f9513c690bc8233f`；beta 暂存差异未触及 `ExoUtil.java`、`ExoUtilTest.java` 或 E-SP7 业务路径。
- 代码复核：`applyVideoLimit()` 的 `setForceHighestSupportedBitrate(false)`、最大分辨率/码率/帧率约束和无轨道限制分支均保持原实施边界。
- 合并后验证：E-SP7 `ExoUtilTest` 定向任务通过；Mobile Arm64 与 Leanback Arm64 Java 编译通过，Leanback 最终执行耗时 5 分 59 秒。
- 结论：beta 合并未覆盖或削弱 E-SP7，当前没有需要在提交前修正的 E-SP7 相关问题；真实 Dangbei X7 Ultra 同资源 A/B 仍未执行。
- 下一步：随 C9 beta 合并原子提交创建恢复标签并推送；设备 A/B 结果后续追加，不改变本次源码提交边界。
