# dev2 合并 beta 最新代码与代码复评（2026-09-08）

## Recovery anchor

- **目标：** 合并 `origin/beta` 最新代码，评审本分支已提交未推送的 `7e12d70f331b6ae48359fb54d0f0e9e70f51a2ea` 及 beta 增量，修复问题、定向验证、复评通过后提交、推送 `dev2`，创建中文 PR 到 `beta`，最后再次拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev2-20260907-round3`，模式 `standard`；开始时保护 5 个既有未跟踪 `.bak20260906*` 文件，不修改、不提交。
- **本地基线：** `dev2@7e12d70f331b6ae48359fb54d0f0e9e70f51a2ea`；该提交相对已推送 `origin/dev2@1882ec67aa3eb07c4ff0e5d2abe8a4548f494e8e` 的唯一代码改动是搜索站源横排方向键分流。
- **beta 目标：** `origin/beta@a4e68ca8f8e6d6d4b545164413411bcc33c35978`；已执行 `git fetch --prune origin beta dev2`，自动合并 `git merge --no-ff --no-commit origin/beta`，无冲突。
- **回滚：** 提交前 `git merge --abort`；提交后使用本任务自动创建的 `recovery/beta-sync-review-dev2-20260907-round3/*` annotated tag 回退整个原子提交。

## 审查边界与已有覆盖

- 本轮实际差异覆盖：`CollectActivity`、`HomeActivity`、TV `VideoActivity`/播放器布局、`TouchOptimizationHelper`、TMDB 长按详情映射、1.2 倍速预设、对应源码测试和本评审文档。
- beta 中早先已评审且与本地改动无冲突的 TMDB、触摸 R2、详情首帧和分类滚动承载改动，保留其已有记录并对最终树做调用点核对；不重复实现已有修复。
- 保护的初始脏路径：
  - `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java.bak202609061600`
  - `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java.bak202609061605`
  - `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java.bak202609061630`
  - `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java.bak202609061645`
  - `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java.bak202609061540`

## 完整增量 commit 台账

以下为 `7e12d70f331b6ae48359fb54d0f0e9e70f51a2ea..origin/beta` 的完整提交集合；合并提交也列出，disposition 以最终树和已有评审记录为准。

| 完整 commit | disposition |
|---|---|
| `3bb2ff086ffbf0aab5906205526bd9f887e22fd7` | 触摸适配首阶段，最终树纳入；与 R2 一并复评，本轮发现其调用链边界问题并修复。 |
| `075b9d84e474b6b843eccbf593bd57f3a5333521` | TMDB 长按集映射，已有 `docs/beta-sync-review-20260907-dev3-round2.md` 覆盖；最终树无冲突，保留。 |
| `235d31ac1182ed08d18ed9f83946b9c4014926d4` | 触摸 R2，最终树纳入；本轮复评并修复触摸结束、生命周期和站源切换时序问题。 |
| `ca4d79dc389294d469301492fa2e92be77919c84` | dev1 合并承载提交；内容已随 beta 进入最终树，已有相关评审覆盖。 |
| `1f3589430f9edb9263de334febadcd1c4765ca3a` | dev3 beta 合并及 TMDB 复评记录承载，最终树已纳入；不重复实现。 |
| `e8b0b56824add5cfd5ce2423295b3570649ec1be` | PR231 合并承载提交，最终树已纳入；无本地路径冲突。 |
| `a45630f15bd3a64bbafdc0c042c12dc422c6d613` | PR230 合并承载提交，最终树已纳入；无本地路径冲突。 |
| `4ec0fa597766b129732791da45fee0c66f2efd56` | PR232 合并承载提交，最终树已纳入；无本地路径冲突。 |
| `42f17a204831d04de6170f136b5c8ccebba97933` | 移除播放器快捷速度预设 1.2x；最终 `PlayerManager.SPEED_PRESETS` 与产品意图一致，未发现回归。 |
| `a4e68ca8f8e6d6d4b545164413411bcc33c35978` | PR234 beta 最新集成承载提交；作为本轮合并目标，最终树已核对。 |

本地已提交未推送提交 `7e12d70f331b6ae48359fb54d0f0e9e70f51a2ea` 的横排站源左右导航分流在合并后仍保留；反向评审未发现该分支本身的问题。

## 发现的问题与修复

### 1. 触摸结束后焦点策略泄漏（已修复）

`TouchOptimizationHelper` 在 `ACTION_DOWN` 临时设置 `FOCUS_SCROLL_ITEM` 和 `focusSearchDisabled=true`，原实现收到 `ACTION_UP` 只清 `touchActive`，导致焦点搜索禁用和滚动策略可能一直保留，并让松手后的选中回调绕过触摸保护。

修复为：抬手立即恢复原焦点状态，同时以 `touchSettling` 覆盖滚动 settling；注册 `RecyclerView.OnScrollListener` 在 idle 后清除 settling；用 generation 防止旧的 post 清理新手势状态。`isTouchActive()` 在触摸和 settling 期间都抑制站源/分类的自动切换。

复评时还发现 `BaseGridView.setOnKeyInterceptListener()` 会覆盖本项目 `CustomRowPresenter` 为内容行安装的既有边界拦截器；因此不再安装触摸助手的 key interceptor，触摸抬手/取消直接恢复焦点状态，保留项目原有 D-pad 边界导航，避免跨组件回调被覆盖。

### 2. Home 恢复时绕过触摸保护（已修复）

`HomeActivity.onResume()` 原来无条件按当前选中分类调用 `resumeTypeSwitch()`。触摸拖动改变选中位置但未点击后离开再回来，会重新调度自动分类切换。

新增 `mTypeSelectionFromTouch`：触摸选中不调度，也不在恢复时重新调度；显式分类点击和内容边界切换清除该标记，遥控器路径保持原自动加载行为。

### 3. 站源切换延迟导致结果数据错位（已修复）

`CollectActivity` 的站源选中立即更新 active site，但结果 adapter 更新延迟 260ms。期间按右/下可能对仍属于旧站源的结果聚焦并播放。

新增 `mBoundSearchSiteKey` 记录结果 adapter 实际绑定的站源。进入结果前若绑定站源不是 active site，则立即绑定当前站源并在两级 post 中再次校验站源和绑定 token，避免异步任务把旧结果聚焦给新站源。

## 设计与保留契约

- 不改变播放器内核、ABI、依赖或二进制资产；1.2x 仅从快捷预设数组移除，手动速度设置路径保留。
- 横排站源左右键继续交给 Leanback 列表，竖排站源右键继续进入结果；搜索、筛选、列数改变继续清理不适用的位置缓存。
- 触摸优化关闭时保留原 `CustomKeyDownVod`、遥控器自动切类和站源行为；触摸优化开启时只在全屏 TV 播放器启用横滑 seek、左右半屏亮度/音量，不改变 mobile、直播、投屏。
- TMDB 长按详情继续使用可见卡片经过匹配校验的 `TmdbEpisode`，无映射/API 失败仍展示源详情回退。

## 验证记录

### 已完成

- `git merge --no-ff --no-commit origin/beta`：自动合并，无冲突。
- `git diff --check`：通过。
- 合并前全量 `:app:testLeanbackArm64_v8aDebugUnitTest`：3427 tests 中 2 个既有 `FfmpegVc1SupportTest` 失败（`codecName_mapsWvc1ToVc1`、`extraData_returnsFirstInitializationBlockForWvc1`），与本轮文件/行为无关；不将其改成通过，也不扩大范围。
- 合并后针对本轮触摸契约的 `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TouchOptimizationHelperSourceTest`：通过。
- Leanback Java 编译在上述 Gradle 任务中通过；此前合并树编译阶段无 Java/XML 资源错误。

### 本轮最终验证

- `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TouchOptimizationHelperSourceTest :app:assembleLeanbackArm64_v8aDebug --console=plain`：`BUILD SUCCESSFUL`（25 秒）；触摸源码契约测试、Leanback Java 编译与 Arm64 APK 组装均完成。
- `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest --console=plain`：`BUILD SUCCESSFUL`（17 秒）。
- 最终索引与工作树反向复评：重新检查触摸恢复/settling、Home 恢复门控、Collect 站源绑定 token、TV 手势取消清理、TMDB 卡片映射及异步 generation guard，未发现剩余阻断问题，结论为 **通过**。
- 设备端 `FocusInstrumentation` 冒烟曾安装/启动到 `SearchActivity`，但长时间无结果输出；已终止遗留进程，记为环境/测试 harness 不确定，不宣称设备端手势场景通过，也不阻塞本地契约与编译收敛。

## 回滚与交付

- 当前仍处于 task guard active、merge 未提交状态；任何最终验证失败均可在不影响保护文件的前提下修复，无法安全收敛时使用 `git merge --abort`。
- 通过后提交当前任务所有 task-owned 路径，推送 `dev2`，使用中文标题/正文创建 base=`beta` 的 PR；随后再次 `git fetch --prune origin beta dev2` 并核对远端最新提交。

## Recovery anchor（更新于 2026-09-08 09:19 Asia/Shanghai）

- 已完成：beta fetch、无冲突 merge、完整增量台账、双上下文反向评审、3 个问题定位和最小修复。
- 当前文件：12 个 beta/本地差异文件及本评审文档；初始 5 个备份仍未跟踪且未修改。
- 已完成验证：修复后的 focused Leanback touch/source test 与 Arm64 组装、focused mobile TMDB layout test、`git diff --check`、task guard 检查及最终反向复评均通过；设备 instrumentation 因无输出卡住后已终止，未作为通过证据。
- 未完成交付：task guard finish、提交/恢复标签、推送 `dev2`、创建中文 PR 到 `beta`、最后一次远端 fetch。
- 下一步：执行 `bash .codex/scripts/task_guard.sh finish`，原子提交当前 task-owned 路径并创建恢复标签。
