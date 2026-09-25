# C19：dev4 合并 beta 最新代码与合并后复评

## Recovery anchor

- **目标**：将远端 `beta` 最新代码合入 `dev4`，复评相对 `origin/dev4` 的全部未推送改动（含已提交未推送的 `21ebe9612b31bd9cc117a3ac5955d68b596394d0`）；发现问题则最小修复并验证，重复评审直到通过；随后提交本任务改动、推送、创建中文 PR 到 `beta`，并拉取远端最新代码。
- **状态**：发现并修复一个组合回归；已完成首轮修复验证，等待最终提交/推送/PR/拉取收口。
- **基线**：合并前本地 `dev4@21ebe9612b31bd9cc117a3ac5955d68b596394d0`；远端 `beta@4eafca853`；合并提交 `00bc40e1f7208a9e5daf842a7ab90ac6576fafe5`。
- **范围**：`app/**`、`.codex/scripts/task_guard.sh`、本任务文档；合并引入文件均属 `app/**` 与远端已有文档，本任务只评审其组合结果，不重写远端已合入功能。
- **首轮事实**：`git merge-tree` 预检无冲突，实际合并成功；本地解析 WebView 三个文件与本次 beta 引入变更无直接文件重叠。
- **下一动作**：记录最终评审与验证后，通过 task guard 提交、推送 `dev4`、创建中文 PR 到 `beta`，再拉取远端最新代码。

## Review and verification log

### 第 1 轮组合评审

- 合并前 `git merge-tree --write-tree HEAD origin/beta` 生成合法树且无冲突；实际合并提交为 `00bc40e1f7208a9e5daf842a7ab90ac6576fafe5`。
- 本任务待评审范围确定为 `origin/dev4..HEAD`：包含本地未推送的超级解析 WebView 修复（`21ebe9612b31bd9cc117a3ac5955d68b596394d0`）与本次远端 beta 合并增量。
- 本地解析改动（`Constant.java`、`ParseJob.java`、`CustomWebView.java`）与 beta 新增主题/TMDB/焦点/任务守卫变更无文件重叠；组合差异中只有 `TmdbDetailActivity` 出现真实测试冲突。
- `ParseJob`/`CustomWebView` 复评结论：串行 Web 解析、解析器 URL 解包、顺序重排、WASM 兜底与 DOM 媒体探测相互衔接；外部生命周期仍通过 `PlayerManager.stopParse()` 调用 `ParseJob.stop()`，无合并引入的新调用方或资源泄漏。
- 发现问题：beta 新增的 `installCardRowFocusMargin()` 将 `postDelayed` 插入 `focusTmdbRecycler()` 与 `scrollDetailChildIntoView*` 之间，触发旧契约 `detailEpisodeDownToTmdbRowsUsesImmediateFocusWithoutScrollFlicker` 的方法区间扫描失败。这不是测试误报：新增延迟重试聚焦与旧契约“不要为焦点引入可见闪烁”冲突。

### 修复

- 将焦点滚动校正从 `post/postDelayed` 改为 `postOnAnimation/postOnAnimationDelayed`：保留 beta 已验证的 260ms 二次校正，同时让首次校正对齐动画帧，避免普通消息队列导致的可见闪烁。
- 改动仅限 `TmdbDetailActivity.java` 的 3 行监听器调度，不改变 12dp 上下留白、卡片行/按钮判断和滚动偏移算法。

### 验证

- `git diff --check` 与 `git diff --cached --check`：通过。
- 首次 `:app:testMobileArm64_v8aDebugUnitTest`：4908 tests，1 failed，2 skipped；唯一失败为 `TmdbDetailActivityLayoutTest.detailEpisodeDownToTmdbRowsUsesImmediateFocusWithoutScrollFlicker`，XML 确认无其他失败类。
- 修复后完整重跑 `:app:testMobileArm64_v8aDebugUnitTest`：`BUILD SUCCESSFUL in 40s`，4908 tests，0 failed，2 skipped。
- 合并后双 flavor Java 编译：`:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`，`BUILD SUCCESSFUL in 2m 16s`。

### 第 2 轮组合复评

- 修复差异复评：仅 `postOnAnimation`/`postOnAnimationDelayed` 替换原调度 API，方法区间满足旧契约，未扩大行为或改动路径。
- 全量 JVM 测试结果证明 beta 新增 942 行测试、主题/TMDB/站点切换变更与本地解析改动在当前组合树上可同时通过。
- 未发现需要继续修改的问题；进入提交与交付阶段。

## Delivery record

- 待记录最终提交、标签、推送、PR 与远端拉取状态。
