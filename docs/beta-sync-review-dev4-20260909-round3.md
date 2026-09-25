# dev4 合并 beta 最新代码与复评（2026-09-09）

## Recovery anchor

- **目标：** 拉取远端 `beta` 最新代码，合并后评审全部当前改动（包括已提交未推送的 dev4 改动），发现问题则修复、验证并复评至通过，随后提交、推送 dev4、创建中文 PR 到 beta，最后再次拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev4-20260909-round3`，模式 `standard`，范围 `app`、`docs`；初始未跟踪文件 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java.bak` 已显式保护，不修改、不提交。
- **开始状态：** 2026-09-09 08:52 CST；当前分支 `dev4`，基线 `f5096d437576891ff387670593b72750163c4fc6`；开始前仅有上述一个预-existing dirty path。
- **beta 状态：** 已执行 `git fetch origin beta`，目标 `origin/beta@242c089b06ccd330933533b856f14a9321921a11`；共同祖先 `0f37b489f7f35719cf572da32a11701f5445a6be`；`git merge --no-commit --no-ff origin/beta` 自动完成、无冲突。
- **回滚：** 提交前使用 `git merge --abort`；提交后使用本任务生成的恢复 annotated tag 回退原子提交。
- **实际收口：** 合并提交 `6fd26879a1c2ceba36344cc57d14d9c6ab04e1ab` 已创建恢复 tag `recovery/beta-sync-review-dev4-20260909-round3/20260909093714-6fd26879a1c2` 并推送；发现预-existing 的 `VodActivity.java.bak` 曾被错误纳入后，未改写已发布历史，使用修正提交 `41133f85db4062f4a72e353ce61774cb2850531f` 删除仓库中的误提交文件，并创建/推送恢复 tag `recovery/beta-sync-review-dev4-20260909-bak-correction/20260909095428-41133f85db40`。该 `.bak` 已恢复为本地未跟踪保护文件。

## 当前范围与证据

- beta 合并暂存树包含 24 个 task-owned 路径；相对 `origin/beta` 的最终本地差异仅为 `HomeActivity.java`、`VodActivity.java` 及其焦点测试，另有本地 6 个已提交焦点修复承载。
- beta 本轮新增的焦点、TMDB 长按映射、触摸适配、播放器手势、MPV 脚本按钮、速度预设和 WebHome 历史续播均已有 beta/dev 任务文档与提交验证；本轮已核对最终树未发生路径冲突或未合并残留。
- 首轮静态评审已检查：Leanback `BaseGridView.OnTouchInterceptListener` API 与反射调用、触摸 ACTION_DOWN/UP/CANCEL 生命周期、站源搜索位置/绑定 token、首页/独立 VOD 分类边界焦点、TMDB 长按绑定集上下文与 dismiss 后焦点恢复、WebHome History 直达续播及动态 MPV 按钮可达性。

## 进度

- [x] fetch beta、启动 task guard、无冲突合并
- [x] 保存完整差异并完成首轮静态评审
- [x] 定向测试、Leanback/Mobile 编译与验证后复评
- [x] 原子提交与恢复 tag（含误提交备份文件的修正提交）
- [x] 推送 dev4、创建中文 PR 到 beta
- [ ] 最后 fetch/pull 并核对状态

## 验证记录

- 已执行一次覆盖最终合并索引的定向命令：

  ```text
  bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
    --tests 'com.fongmi.android.tv.ui.helper.TouchOptimizationHelperSourceTest' \
    --tests 'com.fongmi.android.tv.ui.activity.VodActivityCategoryEdgeTest' \
    --tests 'com.fongmi.android.tv.ui.activity.HomeCategoryNavigationSourceTest' \
    --tests 'com.fongmi.android.tv.ui.dialog.EpisodeDetailDialogThemeTest' \
    :app:testMobileArm64_v8aDebugUnitTest \
    --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' \
    --tests 'com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapterTest' \
    :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
    :app:compileMobileArm64_v8aDebugJavaWithJavac \
    :app:assembleLeanbackArm64_v8aDebug --no-daemon --console=plain
  ```

- 结果：`BUILD SUCCESSFUL`，耗时 1 分 41 秒；Leanback/Mobile Java 编译和 Leanback Arm64 APK 组装通过。生成的 XML 结果中，`TouchOptimizationHelperSourceTest` 11 项、`VodActivityCategoryEdgeTest` 2 项、`TmdbDetailActivityLayoutTest` 119 项、`TmdbEpisodeAdapterTest` 14 项均为 `failures=0`、`errors=0`、`skipped=0`；同一命令包含的其余定向测试也随任务成功完成。
- 验证后安全检查：`git diff --cached --check`、`git diff --check`、`bash .codex/scripts/task_guard.sh check` 均通过；`git ls-files -u` 无输出。

## 验证后最终复评

- 针对验证后的最终索引重新检查了本地 `HomeActivity`/`VodActivity` 分类边界切换、fragment transaction 完成后的焦点恢复、触摸选择抑制自动切换，以及 beta 合并引入的 Collect/TMDB/剧集详情/播放器触摸和 WebHome 历史直达路径。
- 最终索引相对 `origin/beta` 仅保留本地首页/独立点播分类焦点修复及对应测试；beta 新增路径与已有 dev/beta 评审记录一致，合并无未解决路径或索引冲突。
- 未发现需要修复的生产问题，最终复评结论为**通过**。本轮不重复成功的 Gradle 构建/测试。
- 验证边界：未执行真实设备逐帧焦点、触摸视觉、网络站源和 HDMI/音频 passthrough 回归；这些不属于本次源码/布局/单测/编译与 APK 组装的最小决定性验证范围。

## 下一动作

PR 已创建：[#242](https://github.com/Silent1566/webhtv/pull/242)，标题为“合并 beta 最新代码并完成 dev4 复评”，目标 `beta`、来源 `dev4`，远端 head 为 `41133f85db4062f4a72e353ce61774cb2850531f`。唯一下一动作：推送本收口文档提交后，执行 `git fetch --prune origin beta dev4` 与 `git pull --ff-only origin dev4`，确认 `.bak` 仍为本地未跟踪文件且工作树无其他未预期改动。
