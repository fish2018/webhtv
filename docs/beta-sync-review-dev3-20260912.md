# dev3 合并 beta 最新代码与复评全部改动（2026-09-12）

## Recovery anchor

- **目标：** 合并远端最新 `beta`，覆盖评审当前 `dev3` 相对 `beta` 的全部代码改动（含已提交未推送提交）；发现问题则最小修复、验证并再次复评，随后原子提交、推送 `dev3`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev3-20260912`，模式 `standard`；启动时工作树干净，无受保护脏路径；范围 `app`、`docs`。
- **开始时间：** 2026-09-12 00:45 CST（Asia/Shanghai）。
- **本地基线：** `dev3@b993795ac88ed723dbb532401890bf408a0c25ac`。
- **远端目标：** `origin/beta@87ab74014a62382af8786109099ccf95d8b58ff5`；`upstream` 远端不存在 `beta` 引用，不能作为本项目 beta 目标。
- **远端 dev3 增量：** `origin/dev3@398d6b900b0e684ea96b9d0b353620847c421c64`，必须保留，不能以本地分支覆盖。
- **共同祖先：** 本地 `dev3` 与 `origin/beta` 为 `be1b02e06b22a4fa2f08c791555536e3e6154c95`；本地 `dev3` 与 `origin/dev3` 的共同祖先为 `1e7d79ef29fd7568f376fe571bde0bd4cd7c6838`。
- **合并状态：** 已用 `git merge --no-ff --no-commit origin/beta origin/dev3` 自动完成，无冲突；当前仍处于待提交合并状态，`MERGE_HEAD` 包含 `87ab74014a62382af8786109099ccf95d8b58ff5` 与 `398d6b900b0e684ea96b9d0b353620847c421c64`。
- **回滚：** 提交前执行 `git merge --abort`；提交后使用本任务恢复标签，或 `git revert -m 1 <merge-commit>` 回退合并提交。
- **当前状态：** 定向验证和验证后复评已通过，待 task guard 原子提交、推送、创建 PR 并完成最终拉取。
- **回滚锚点：** 提交前仍可 `git merge --abort`；提交后由 task guard 创建的 annotated recovery tag 指向完整合并树。

## 合并范围与提交台账

### beta 增量（相对 `be1b02e06b22a4fa2f08c791555536e3e6154c95`）

| 完整 commit | disposition |
| --- | --- |
| `3a2b9d39e8bd3314027e5ce8e709c17e75a21236` | 已实现并复评；修复 Leanback 直播控制栏与共享 OSD 的顶部信息重影，新增 `LiveActivityLayoutTest` 契约测试。 |
| `c36a29038360fb205cbca6c49f835ee65fc874e1` | 文档记录；其验证覆盖上一个功能提交，最终树无额外生产代码。 |
| `87ab74014a62382af8786109099ccf95d8b58ff5` | PR #251 的合并承载提交；保留，不重复拆解已由上表及既有评审覆盖的内容。 |

beta 自带的 `docs/beta-sync-review-dev2-20260911.md` 已记录 PR #251 的源码复评、`LiveActivityLayoutTest` 和 Leanback Java 编译通过。本轮仍检查最终合并树、调用链和路径关系；beta 增量与本地站点健康/TMDB 改动没有文件交集。

### 远端 dev3 增量

| 完整 commit | disposition |
| --- | --- |
| `398d6b900b0e684ea96b9d0b353620847c421c64` | 已由远端 dev3 提交并验证；将 TMDB 四个 MaterialButton 的文字从 API 25 易崩溃的布局 inflate 阶段移到 inflate 后设置，新增契约测试。本轮确认最终合并树保留该提交且与 beta、本地站点健康改动无冲突。 |

### 本地已提交未推送改动（相对 beta）

| 完整 commit | disposition |
| --- | --- |
| `f17443f5e880053bdeb825001bb964088bf48a49` | 已实现；站点健康报表增加全量清空入口、确认文案和来源契约测试。 |
| `1c70eb0c679226d702e6cad9c4cdd06e5a440994` | 已实现；清空后重新读取报表并刷新 UI，定向单测通过。 |
| `b993795ac88ed723dbb532401890bf408a0c25ac` | 已实现；TV 清空确认框统一使用 `Theme_WebHTV_LightDialog` 与 `LightDialog.apply`，Leanback 定向单测和 debug assemble 已通过。 |

## 首轮复评

### Leanback 直播 OSD（`3a2b9d39e8bd3314027e5ce8e709c17e75a21236`）

- `LiveActivity.showControl()` 现在无条件隐藏废弃的 `widget.top`，并交给 `PlayerOsdController.setControlsVisible(true)` 输出标题、分辨率和时间，避免 OSD 开关状态切换后留下旧栏造成重影。
- `PlayerOsdController.render()` 的控制栏分支会停止诊断采样、隐藏底部进度/流量信息，只保留控制栏所需的顶部标题与时间；`start()`、`onStart()`、`onStop()`、`onDestroy()` 的生命周期调用保持成对，未发现刷新循环泄漏或控制栏隐藏后继续显示的问题。
- `LiveActivityLayoutTest` 对 `showControl()` 的隐藏旧栏和启动共享 OSD 契约已覆盖；未发现 beta 合并覆盖本地其他播放逻辑。
- **结论：** 未发现阻断问题。

### TMDB API 25 修复（`398d6b900b0e684ea96b9d0b353620847c421c64`）

- 四个按钮仍保持原有点击监听和资源文案，仅将文字和大小写设置移到 `LayoutInflater` 完成后；`TextView` 类型可覆盖 `MaterialButton`，不改变行为。
- `TmdbSourceDialogInflationContractTest` 同时约束布局不再在四个按钮上解析 `android:text`、Java 代码补齐四个文案以及 `textAllCaps=false`，能够覆盖记录的 API 25 二进制 XML 崩溃路径。
- **结论：** 既有验证已覆盖，最终树复核通过，无需重复修复。

### 站点健康报表（`f17443f5e880053bdeb825001bb964088bf48a49`、`1c70eb0c679226d702e6cad9c4cdd06e5a440994`、`b993795ac88ed723dbb532401890bf408a0c25ac`）

- 全量清空入口在报表为空时禁用并降低透明度；确认后调用 `SiteHealthStore.clear()`，再通过根视图队列重新读取 `SiteHealthStore.report()` 后渲染，避免在确认回调中直接修改当前行容器。
- 单站点清空仍使用带站点名称的确认文案和 `SiteHealthStore.clear(row.siteKey)`；两类操作都复用统一的浅色确认框样式，三套资源文案齐全。
- `SiteHealthStore.clear()` 的全量语义与“清空全部站点健康样本”文案一致；当前报表按当前 CID 展示，但该 API 清除的是健康存储整体，属于该入口的明确全量行为，不是隐藏的部分清除。
- **结论：** 未发现阻断问题。

## 验证与验证后复评

-执行前已检查实际运行中的 Gradle/打包进程：仅有 Gradle daemon，没有其他正在运行的 Gradle 构建、打包、`make` 或 `ninja` 命令，因此未发生需要排队的任务。

实际执行：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.setting.SiteHealthReportSourceTest' \
  --tests 'com.fongmi.android.tv.ui.dialog.TmdbSourceDialogInflationContractTest' \
  --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest' \
  :app:testMobileArm64_v8aDebugUnitTest \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  --no-daemon --console=plain
```

-结果：`BUILD SUCCESSFUL in 37s`，114 actionable tasks（28 executed、86 up-to-date）。
  - `SiteHealthReportSourceTest`：3 项通过，0 failures、0 errors、0 skipped。
  - `TmdbSourceDialogInflationContractTest`：1 项通过，0 failures、0 errors、0 skipped。
  - `LiveActivityLayoutTest`：5 项通过，0 failures、0 errors、0 skipped。
  - `compileLeanbackArm64_v8aDebugJavaWithJavac` 与 Mobile Java 编译均通过；只有既有 32 位 native library、deprecated API 等构建警告，无新增失败。
- 另行执行 `git diff --cached --check`、`git ls-files -u` 和任务守卫检查，均通过；当前无未解决冲突。

### 验证后再次复评

- 重新检查合并暂存树相对 `origin/beta` 的 9 个差异路径：站点健康报表 6 个路径、TMDB API 25 修复 3 个路径；Leanback OSD 的 2 个 beta 路径和远端 dev3 的 TMDB 提交均保留在最终树。
- `LiveActivity.showControl()` 仍在调用共享 OSD 前隐藏旧 `widget.top`；共享 OSD 的控制栏分支继续只显示标题/分辨率/时间，控制栏隐藏后由 `setControlsVisible(false)` 恢复用户 OSD 状态，未发现重复显示或刷新生命周期遗漏。
- TMDB 四个 MaterialButton 的布局文本仍全部移除，Java inflate 后设置文案和 `setAllCaps(false)` 均保留；点击监听未被改动，API 25 防护未回退。
- 站点健康单站点与全量清空仍分别调用正确的 `SiteHealthStore.clear` 重载，确认框样式、三套文案、清空后的重新读取/渲染均保留；`clearAll` 为空报表禁用，未发现误清空或 UI 更新顺序问题。
- 复评结论：**通过，无需修复。**

## 当前状态与下一动作

- [x] 抓取 `origin/beta`、`origin/dev3` 并确认 `upstream/beta` 不存在。
- [x] 保留远端 dev3 未推送到本地的 TMDB 提交。
- [x] 合并 `origin/beta` 与 `origin/dev3`，无冲突。
- [x] 完成首轮静态复评，未发现需要修复的问题。
- [x] 检查无其他实际构建任务并运行定向验证，记录完整结果。
- [x] 验证后再次复评最终树，未发现需要修复的问题。
- [ ] 使用 task guard 原子提交并创建恢复标签。
- [ ] 推送 `dev3`，创建中文 PR 到 `beta`。
- [ ] 最后拉取远端最新代码并核对分支、PR、工作树。

**唯一下一动作：** 执行 task guard `finish`，原子提交合并树与本任务记录并创建 recovery tag。
