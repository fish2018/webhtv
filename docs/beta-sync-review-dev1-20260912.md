# dev1 合并 beta 最新代码并复评全部改动（2026-09-12）

## Recovery anchor

- **目标：** 合并远端 `origin/beta` 最新代码，评审 beta 增量及 dev1 当前已提交但未推送的全部改动；发现问题时最小修复、验证并再次复评，随后原子提交、推送 `dev1`、创建中文 PR 到 `beta`，最后拉取远端最新代码并核对交付状态。
- **任务守卫：** `beta-sync-review-dev1-20260912`，模式 `standard`；启动时工作树干净，无受保护的既有脏路径；范围为 `app`、`docs`、`scripts`。
- **开始时间：** 2026-09-12 14:04 CST（Asia/Shanghai，UTC+08:00）。
- **当前分支与基线：** `dev1@b0f0343664502854ade683cdf0702d55a3ea32d3`；与 `origin/beta` 的共同祖先为 `6e876636caa36a8e833c0860c779faffc5d9592e`。
- **远端 beta：** 已执行 `git fetch --prune origin beta`，目标为 `origin/beta@81435e97eafabd64727412169ead107687f7325c`。
- **合并状态：** 已执行 `git merge --no-ff --no-edit origin/beta`；仅 `TmdbDetailActivity.java` 与 `TmdbDetailActivityLayoutTest.java` 发生内容冲突，已合并保留 beta 的剧照栏 124dp 修复、episode grid 契约测试，以及 dev1 的海报完整圆角显示修复；当前无未解决冲突，合并结果已暂存，`MERGE_HEAD` 为 `81435e97eafabd64727412169ead107687f7325c`。
- **已完成动作：** 完成 beta 增量与本地差异的首轮静态复评；未发现需要修复的代码问题。首次组合 Gradle 验证中 Mobile 定向测试 159 项全部通过，Mobile/Leanback Arm64 Java 编译均通过；命令整体仅因对 `testLeanback` 使用了不存在于该 source set 的 `LiveActivityLayoutTest` 过滤器而返回失败，已分类为测试命令范围错误，不是代码回归。
- **未验证风险：** 未执行真实电视遥控器逐键焦点/视觉高亮验收，也未用真实网络完成短剧连续切集、网络争用、字幕/弹幕时序验收；这些不影响本次源码合并与定向编译判断，但不能被本任务结论扩大解释为实机放量验收。
- **回滚：** 提交前使用 `git merge --abort`；提交后使用本任务 recovery tag 或对合并提交执行 `git revert -m 1 <merge-commit>`。
- **唯一下一步：** 记录最终验证结果后，调用 `task_guard.sh finish` 原子提交并创建 recovery tag；随后推送 `dev1` 与该标签、创建目标为 `beta` 的中文 PR，并执行最后一次远端拉取核对。

## 合并范围与提交台账

### beta 增量（相对共同祖先）

以下提交均已进入最终暂存树。功能提交已由本轮或此前对应分支复评文档覆盖；合并承载提交与文档提交不重复拆解生产行为，但保留其完整 ID 以便审计。

| 完整 commit | 内容与 disposition |
| --- | --- |
| `398d6b900b0e684ea96b9d0b353620847c421c64` | TMDB API 25 对话框按钮 inflate 崩溃修复；最终树复核通过，契约测试保留。 |
| `3a2b9d39e8bd3314027e5ce8e709c17e75a21236` | Leanback 直播 OSD 顶部信息重影修复；最终树复核通过，契约测试保留。 |
| `f17443f5e880053bdeb825001bb964088bf48a49` | 站点健康报表增加全量清空入口；最终树复核通过。 |
| `c36a29038360fb205cbca6c49f835ee65fc874e1` | dev2 beta 后续复评记录；文档提交，无额外生产代码。 |
| `87ab74014a62382af8786109099ccf95d8b58ff5` | PR #251 合并承载提交；保留最终树。 |
| `1c70eb0c679226d702e6cad9c4cdd06e5a440994` | 站点健康清空后重新读取并刷新 UI；最终树复核通过。 |
| `5345b8762eee58dc77cb7119d3fbeec14078b064` | beta 同步承载提交；无额外未覆盖生产行为。 |
| `03046756f4d786e11cfbfb92cc9aa1e61685d9a6` | 光影剧幕选集左侧对齐修复；最终树复核通过。 |
| `edaeb6609e57a62ca56ed6a362496a531f86ce98` | arm64 debug 安装脚本；最终脚本树与 dev1 本地脚本提交一致，未产生额外差异。 |
| `b993795ac88ed723dbb532401890bf408a0c25ac` | TV 站点健康清空确认框样式修复；最终树复核通过。 |
| `d094e2faaf4467b86ad4584ddf0ad384abd9e7c3` | 炫彩详情选集元信息补齐；最终树复核通过。 |
| `cff4d2f6d48d6841752627c5db9a1b7123e2d4e3` | beta 同步承载提交；无额外未覆盖生产行为。 |
| `91571b47365a8beb619e7158e284f79696b0bff6` | dev3 beta 合并复评承载提交；既有复评记录已随 beta 保留。 |
| `f312bc3136d99e66eceb34efccf69216588ff7b8` | 炫彩详情选集网格起始对齐修复；最终树复核通过。 |
| `d1fb2fdca07487ddc70f16e7bce913dbd0fb2d8a` | PR #255 合并承载提交；保留最终树。 |
| `d28a968b60fd2757e7cdc9af2a4de26b2f165d36` | PR #254 合并承载提交；保留最终树。 |
| `b9c2861c9c74f8aaf3128f92c6737360c1b91a78` | PR #253 合并承载提交；保留最终树。 |
| `4ee05ac28cf3ba8f869f6e2d3e0cc99743b925b4` | PR #252 合并承载提交；保留最终树。 |
| `e8056e5ad412cd0ec141fff0ee12aa56e582fbfa` | TV 关于页“检查更新”上键焦点修复；最终树复核通过。 |
| `e82f50cbaa4b3c16edda98fcf0d2f5e368b2ef0a` | TV 关于页“我已知悉”上键焦点修复；最终树复核通过。 |
| `30fff5f4ff80fbb90cb2852e78f08147c74cd4d8` | TV 关于页聚焦/按下态背景、文字和图标资源；资源引用闭合，最终树复核通过。 |
| `8f8797462082f0667341cdf430bf1fa70a73d39f` | 光影剧幕剧照栏容器统一为 124dp；与 dev1 海报修复冲突点已组合保留，最终测试覆盖。 |
| `7c325e4a04891fc1df224d359039998500ec4235` | PR #257 合并承载提交；保留最终树。 |
| `4353f020eb541ee74a2be98f1b5ce20eef3f4021` | dev2 beta 合并复评承载提交；既有复评记录已随 beta 保留。 |
| `38ff48d8d12a49cb4edbd08514e6342aaf2099ac` | dev2 复评交付收口记录；文档提交，无额外生产代码。 |
| `81435e97eafabd64727412169ead107687f7325c` | PR #258 合并承载提交；本次合并目标，保留最终树。 |

### dev1 已提交但未推送提交

| 完整 commit | 相对 beta 的 disposition |
| --- | --- |
| `f32787745c0dd0b587c5ec91751deb5d519cf339` | 新增 arm64 debug 安装脚本；最终 `scripts/build_arm64_debug_install.sh` 与 beta 的 `edaeb6609e57a62ca56ed6a362496a531f86ce98` 树内容一致，无独立最终差异。 |
| `4269dc5e83b2dafe29052b341a85f00ff17b0f5c` | 为脚本增加 Leanback TV flavor；最终脚本树已被 beta 等价提交覆盖，无独立最终差异。 |
| `2bc1244a09ca5d0bd9992a4c5e617346827fe8a9` | E-SP8 短剧队列准入修复；两端 `VideoActivity`、策略注册和测试均保留，本轮编译/单测复核通过。 |
| `42d6abe02e962e3d92e156ab7e5f994a7026b1f1` | 光影剧幕海报保留圆角；与 beta 剧照栏修复无行为冲突，最终树保留。 |
| `b0f0343664502854ade683cdf0702d55a3ea32d3` | 修正海报列表高度及焦点放大裁剪；与 beta 剧照栏 124dp 修改发生文本冲突，已合并为同时设置剧照 124dp、海报列表 238dp 且关闭列表裁剪，最终测试覆盖。 |

### 最终差异边界

- 相对 `origin/beta` 的 dev1 独有最终差异为 7 个路径：Mobile/Leanback 两端短剧队列准入、策略/测试、TMDB 光影剧幕布局/测试，以及 E-SP8 记录。
- 相对合并前 `HEAD` 的 beta 引入差异为 23 个路径，覆盖 Leanback 直播 OSD、TMDB 选集/来源、站点健康报表、TV 关于页资源和对应测试/复评文档。
- `git ls-files -u` 无输出；未出现越界路径或未解决合并文件。

## 首轮代码复评

### E-SP8 短剧队列

- `PlaybackExperimentPolicy.Action.EXO_SHORT_DRAMA_QUEUE` 使用 `AUTOMATIC_OPTIMIZATION` 风险等级和 schema 2；稳定策略允许该动作，实验策略仍由统一策略状态管理。
- Mobile 与 Leanback 的 `isShortDramaQueueEligible` 均改为调用 `isAllowed(EXO_SHORT_DRAMA_QUEUE)`；短剧源、Exo、播放列表能力、预载、代理、单集/重复播放和历史位置等既有门槛未被放宽。
- 对应 `PlaybackExperimentPolicyTest`、既有 E-SP8 文档和两端 Java 编译保留，未发现只修一端或绕过统一准入的问题。

### TMDB 光影剧幕布局冲突组合

- `applyCinemaDetailTemplate()` 最终同时保留剧照卡片 124dp 容器高度，以及海报卡片 148x222dp 所需的 238dp 列表高度和关闭列表 `clipToOutline`/`clipChildren`，避免相邻栏空白与焦点放大时四角被裁剪。
- `TmdbDetailActivityLayoutTest` 同时约束海报列表裁剪/高度、剧照栏高度和选集网格起始边缘；冲突解决没有丢失任一侧契约。
- `TmdbPhotoAdapter` 的海报 item 仍为 148x222dp，最终列表额外空间与焦点放大意图一致；默认模板未调用该 cinema 分支，未发现跨模板状态泄漏。

### beta 侧已覆盖功能

- **Leanback 直播 OSD：** `LiveActivity.showControl()` 无条件隐藏旧 `widget.top`，交由共享 `PlayerOsdController` 在控制栏显示时输出标题/分辨率/时间；生命周期和 `LiveActivityLayoutTest` 契约均保留。
- **TMDB 选集元信息与对齐：** native-enhanced 卡片在可用时显示日期/文件大小、评分和简介；grid 统一零 start margin、单 end spacing，未改变卡片宽度计算。
- **站点健康报表：** 全量清空按钮在空报表禁用；确认后调用正确的全量/单站点 `SiteHealthStore.clear`，再通过根视图队列重新读取并渲染，确认框统一 `Theme_WebHTV_LightDialog` 与 `LightDialog.apply`。
- **TMDB API 25：** 四个 MaterialButton 的文字在 inflate 完成后通过 `TextView` 设置，布局不再解析对应 `android:text`；点击监听和文案资源保持闭合。
- **TV 关于页：** `checkUpdate`、`githubProxy`、`confirm` 的焦点路径和新增背景/文字/图标 selector 引用有效，未改变原有点击入口。

首轮复评结论：**未发现需要修复的问题。**

## 验证记录

执行 Gradle 前检查实际进程：没有运行中的 Gradle/gradlew、assemble、bundle、cargo build/package、cmake、ninja、ndk-build 或 make；仅存在空闲 Gradle daemon，因此没有并发打包任务，无需排队。

执行的定向命令为：

```text
bash ./gradlew \
  :app:testMobileArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest \
  --tests com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapterTest \
  --tests com.fongmi.android.tv.setting.SiteHealthReportSourceTest \
  --tests com.fongmi.android.tv.ui.dialog.TmdbSourceDialogInflationContractTest \
  --tests com.fongmi.android.tv.player.PlaybackExperimentPolicyTest \
  --tests com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest \
  :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest \
  :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  --no-daemon --max-workers=1 --console=plain
```

结果：

- Mobile 定向测试报告显示 6 个测试类共 159 项，`failures=0`、`errors=0`、`skipped=0`。
- `:app:compileMobileArm64_v8aDebugJavaWithJavac` 通过。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。
- Gradle 最终返回失败只来自 `:app:testLeanbackArm64_v8aDebugUnitTest`：该 flavor 没有 `LiveActivityLayoutTest` 测试类，过滤器报告 `No tests found for given includes`；该测试位于 Mobile 测试 source set，已在上面的 Mobile 结果中通过。该失败属于验证命令过滤范围错误，不是源码失败。
- `git diff --cached --check` 通过；无未解决路径、无冲突标记。

## 验证后复评与接受标准

修复/冲突处理后再次检查最终暂存树：

1. beta 目标 `81435e97eafabd64727412169ead107687f7325c` 的功能提交和既有复评文档均在最终树中；不存在因 dev1 局部提交而回退 beta 功能的路径。
2. dev1 相对 beta 的 7 个独有路径均为预期 E-SP8/TMDB 改动；两端队列准入和测试一致，TMDB 剧照与海报的冲突点同时存在。
3. Mobile 159 项定向测试、Mobile/Leanback Arm64 Java 编译支持源码合并安全性；未发现需要二次修复的问题。
4. 实机电视焦点、短剧连续切集及网络/字幕/弹幕时序仍明确列为未验证边界，不扩大“定向验证通过”的含义。

当前结论：**代码复评与交付通过；本轮新增的 EXO FFmpeg 模式清理改动已提交、推送，并创建中文 PR #260 到 `beta`。**

## 状态

- [x] 抓取 `origin/beta` 最新代码并确认目标提交。
- [x] 合并 beta 并解决两个 TMDB 布局冲突。
- [x] 复评 beta 增量和 dev1 已提交未推送改动。
- [x] 确认没有并发打包任务并完成定向验证；命令过滤失败已分类为测试 source set 错误。
- [x] 验证后再次复评最终暂存树，未发现需要修复的问题。
- [x] 完成最终文档校验；本轮新增 EXO 清理提交 `c2dcf52b5676eef00026f55f013eb58ae522804f` 已由 `E-ROLLBACK-EXO` 任务守卫原子提交并创建 recovery tag。
- [x] 推送 `dev1` 与 recovery tag，创建中文 PR #260 到 `beta`。
- [x] `git fetch --prune origin` 并核对远端最新代码、分支和 PR 状态：`origin/dev1` 与本地代码提交一致，`origin/beta` 为 `ccd608c503bed84711bb1bc75875c030b6be5ce5`，PR #260 状态为 OPEN、merge state 为 CLEAN。

**唯一下一动作：** 完成当前文档闭合提交后再次拉取远端，确认文档闭合提交已推送；除此之外无剩余代码修复工作。

## 2026-09-12 后续提交复评与交付记录

### 新增提交范围

- `c2dcf52b5676eef00026f55f013eb58ae522804f`：移除 EXO 已不再消费的 FFmpeg 模式设置、AUTO 模式失败遍历、手机/TV 设置入口、备份偏好键、过时说明文档及其专属契约测试；保留 `ExoUtil` 的 FFmpeg renderer 和普通解码/内核回退。
- 该提交的父提交为 `ccd608c503bed84711bb1bc75875c030b6be5ce5`，即当前 `origin/beta`；因此本轮没有新的 beta 合并冲突。

### 复评结论

- 运行时状态、`PlayerSetting` API、`PlayerManager` 生命周期接线、Data Binding/UI 资源、备份偏好和测试删除均逐项检查，未发现 dangling reference、错误删除或影响其他播放器的改动。
- `ExoUtil` 仍明确构造 `FfmpegRenderersFactory`，并保留 `CompatFfmpegAudioRenderer` 与 `FfmpegVideoRenderer`；移除的只是用户可选模式及其失效的 AUTO 重建链。
- 历史 EXO 评估/审计文档中的旧模式术语按审计记录保留，不视为运行时或用户可见设置残留。
- 首轮复评通过，无需修复，也不存在需要“修复后再次复评”的代码循环。

### 交付证据

- `origin/dev1@c2dcf52b5676eef00026f55f013eb58ae522804f` 已与本地提交一致。
- recovery tag：`recovery/E-ROLLBACK-EXO/20260912151536-c2dcf52b5676`，已推送。
- PR：#260，标题为“EXO：移除已废弃的 FFmpeg 模式设置”，目标分支为 `beta`，创建时状态为 OPEN、merge state 为 CLEAN。
