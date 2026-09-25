# dev2 第二轮 beta 同步与电视端详情闪烁复评（2026-09-07）

## Recovery anchor（本次最新收口）

- 目标：合并远端最新 beta，评审当前全部改动（含已提交未推送），修复、验证、复评通过后提交、推送并创建中文 PR 到 beta，最后确认远端最新状态。
- 本次远端基线：`origin/beta@9fc936fd88cb5b58d645c49b97209b0f246ed078`；已无冲突合并为本地 `ff6f6562d42ad3e1efdf1941c564899b86e455cd`。
- 本次相对 beta 的独有代码：`804d83f6e88a07974ef1b48d302558fbc78e7329` 在 `TmdbDetailActivity.java` 增加融合播放器 MPV 自定义按钮入口；首轮复审发现 TV 动态按钮未接入方向键焦点链，已在同文件补充稳定 View ID 与动态焦点链重建。
- 当前保护路径：5 个 `*.bak20260906*` 备份文件，均保持未修改、不纳入提交；任务守卫为 `M202609071946-beta-review-fusion-mpv-scripts`，范围为 `TmdbDetailActivity.java` 与本文档。
- 已完成证据：Mobile Arm64 `TmdbDetailActivityLayoutTest` 与 Leanback Arm64 Java 编译均 `BUILD SUCCESSFUL`（日志 `/tmp/beta-review-fusion-mpv-scripts-final.log`）；此前双 flavor 编译日志为 `/tmp/beta-review-fusion-mpv-scripts-gradle.log`；`git diff --check` 与任务守卫 check 通过。
- 最新复评结论：自定义按钮只读取启用配置，短按/长按分别透传 MPV script-message；非 MPV、无服务或空播放器时隐藏；Mobile 双动作栏不重复挂载；TV 动态按钮已分配稳定 ID 并接入可见焦点链。未发现剩余 P1/P2 阻断问题。
- 未决风险：真实设备逐帧视觉与遥控器验证不在本地证据内；该边界不影响源码编译、源契约测试和焦点链静态复评结论。
- 回滚锚点：`ff6f6562d42ad3e1efdf1941c564899b86e455cd`；下一动作：任务守卫原子提交并创建恢复标签，然后推送 dev2、创建中文 PR 到 beta，最后拉取远端最新状态。

## Recovery anchor

- 目标：合并远端最新 beta，评审全部当前改动（含已提交未推送），修复、定向验证、复评通过后提交与恢复标签，推送 dev2 并创建中文 PR 到 beta，最后拉取远端最新状态。
- 任务守卫：`beta-sync-review-dev2-20260907-round2`，`standard`。范围为 beta 增量实际 20 个路径、TV `VideoActivity.java`、`VideoActivityDetailShellSourceTest.java` 和本文档；精确路径由任务守卫记录。
- 开始时间：2026-09-07 16:29 CST（Asia/Shanghai，UTC+8）；预计本地完成时间 16:50–17:10。
- 本地基线：`dev2@d60297370a9ffefcba63aff9fe2d63e053f012ed`。
- 远端基线：`origin/dev2@7e6fd548190834408e40332e8392d2e379536511`；`origin/beta@58c6ef28436cad6b22c8cdf464629ca4897377c7`。
- 保护路径：`app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java.bak202609061600`、`.bak202609061605`、`.bak202609061630`、`.bak202609061645`（均为相同 MpvPlayer 前缀），以及 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java.bak202609061540`；不纳入范围或提交。初始校验和保存在 `/tmp/beta-sync-review-dev2-20260907-round2/protected.sha256`。
- 已完成：fetch 最新 beta/dev2；确认旧 PR #224 已合并；`git merge --no-ff --no-commit origin/beta` 零冲突，beta 未修改本地独有的 TV `VideoActivity.java`。
- 当前未提交内容：beta 合并结果、TV 详情初始壳层/选集工具栏源契约测试、复用 Activity 修复、本文档。首轮隔离审查发现并修复了 `onNewIntent()` 切换条目时继承旧操作按钮的 P1；修复后定向测试、Leanback 编译和复评均已通过。
- 验证边界：源契约测试与 Leanback 编译不代表真实设备逐帧视觉测试；不改播放器、依赖、ABI 或二进制。
- 回滚：提交前仅撤销本任务文件并 `git merge --abort`，不可清理初始备份；提交后通过本任务恢复标签定位，已发布历史不得重写。
- 下一动作：使用任务守卫原子提交并创建恢复标签，然后推送 dev2、创建中文 PR 到 beta，最后拉取远端最新状态。

## 范围与既有评审复用

- 当前分支相对最新 beta 唯一独有生产提交为 `d60297370a9ffefcba63aff9fe2d63e053f012ed`：首帧揭开前设置原生增强详情动作显隐；倒序/原文件名重绑不重新计算选集工具栏显隐。逐项审查该提交及其直接调用链。
- beta 的已评审代码原样合入，无冲突、无本地重写。标题识别沿用 `docs/beta-sync-review-20260907-dev3.md` 的最终复评和 32 项定向测试；TV 壁纸入口沿用 `docs/beta-sync-review-20260907-dev4-round2.md` 的复评和 Leanback 编译；外观/主题/实验室等沿用 `docs/C13-beta-sync-review.md`、`docs/dev1-beta-review-20260907.md` 和相关提交验证信息。
- 本次没有新增上游依赖候选或架构设计；属于既定 TV UI 行为的窄修复与集成，不重新进行播放器上游设计调研。

## beta 增量完整台账

| 完整 commit | 处理 |
| --- | --- |
| `1c2fa82822f65050c5b15f956238d48488111e08` | 标题清洗设计，随 beta 原样集成，dev3 已复评 |
| `d1ca2d2756c334a281587503e1d23b06a3369940` | 标题清洗实现及测试，dev3 最终树已复评 |
| `57932a7e7055489403d3e170bba706048df0e065` | Mobile 设置膨胀修复，已有 beta 评审覆盖 |
| `a6eb0e96019da096b6e50aa8944270f911a2cdfa` | TV 壁纸入口，dev4 已复评 |
| `fca898f9d940816c378ec37785184f46b334cd4d` | dev3 合并复评、标题边界修正及验证，原样继承 |
| `f0e9ab75bd0770b17db9f9bcd6101026d2c63466` | PR #225 集成承载 |
| `161b190fac6c304c240bfb3b142b4dfa531fb1d5` | PR #224 集成承载，包含此前 dev2 复评 |
| `4425e9cb63186cfe41cacb1fd3982a23249a27e6` | TV 外层壁纸设置恢复，dev4 已复评 |
| `db59f3eeb85ff0e4ed993e814d9b81b08036bcb7` | dev1 beta 合并复评，原样继承 |
| `03e3941196c84031bae764ac4192add4ed8619ec` | PR #226 集成承载 |
| `dbea2b995260ba73908a1e67078a938773699658` | dev4 beta 合并与 TV 壁纸复评，原样继承 |
| `58c6ef28436cad6b22c8cdf464629ca4897377c7` | PR #227 集成承载，本轮 fetch 的 beta 最新基线 |

## 本轮评审与验证

- 首轮错误任务：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest ...` 报 `No tests found`，原因为目标测试在 `testMobile` 源集；同命令中的 Leanback Java 编译成功。未将该工具调用错误误判为回归。
- 修复后 Mobile Arm64 定向测试：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.VideoActivityDetailShellSourceTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest --no-daemon --console=plain`，`BUILD SUCCESSFUL`。
- 修复后 Leanback Arm64 Java 编译：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain`，`BUILD SUCCESSFUL`。
- `git diff --check`、`git diff --cached --check` 和任务守卫 check 通过；无未合并路径。日志保存在 `/tmp/beta-sync-review-dev2-20260907-round2/`。

### 修复后隔离复评

- 复查 `initView(Bundle)`、`resetDetailForNewIntent()`、`prepareInitialDetailShell()`、`setDetail(Vod)`、`setEpisodeAdapter(...)`、`reverseEpisode(...)`、`toggleEpisodeFileName()` 和 `setOriginalEnhancedActionVisibility(...)` 的调用顺序与状态边界。
- P1 修复已覆盖：新 Activity 与 `singleTop/onNewIntent` 两条入口都在预览/加载态前计算新条目详情动作；普通详情、TMDB 未就绪、原生增强和直接 TMDB 播放仍共享已有守卫；普通重绑继续刷新工具栏，倒序/原文件名切换只重绑列表。
- 结论：**复评通过，未发现剩余可操作阻断问题**。未覆盖真实设备逐帧观测，保留为运行时验证边界。

### 首轮隔离审查发现

- P1（已修复）：`resetDetailForNewIntent()` 原先未调用 `prepareInitialDetailShell()`；`onNewIntent()` 复用 Activity 切换条目时会继承上一条目的 `change1/searchDetail/shortDisplay` 状态。修复为重置详情内容后、显示 loading 前按新 Intent 准备壳层，普通/TMDB 未配置与增强入口仍由同一配置守卫决定。

### 修复后复评

已完成，结论见上方“修复后隔离复评”：通过，未发现剩余可操作阻断问题。
