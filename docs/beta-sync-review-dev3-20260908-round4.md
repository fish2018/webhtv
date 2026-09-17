# dev3 合并 beta 最新代码并复评全部未推送改动（2026-09-08）

## Recovery anchor

- **目标：** 在不丢失 dev3 已提交未推送的 TV 详情焦点修复的前提下，合并远端 `beta` 最新代码，复评合并后的全部本地改动与 beta 增量；如发现问题则最小修复并再次验证、复评，随后提交、推送 dev3、创建中文 PR 到 beta，最后拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev3-20260908-round4`，模式 `standard`；开始时工作树干净，无受保护脏路径；范围 `app`、`docs`、`gradle`、`gradlew`、`scripts`、`third_party`。
- **时间与基线：** 2026-09-08 20:08 CST 开始；本地基线 `dev3@f89c301d65c0c8d4bf6e44a3eab11e6c9c9266dd`；共同祖先 `e09e785d87b95f9da249825a79f7ad0a213f61b6`；执行了 `git fetch origin --prune`，当时 `origin/beta@74e572173fcc9ab7e274a94b6245b8de90b217c1`。
- **本地已提交未推送改动：** `1c0bd8e1d75e0c8d3a2f420a39fcb5b23e96f48b`（剧集详情照片网格焦点）、`400c059369ead1041e1afbd9fc589bddd51490c3`（炫彩详情关闭后焦点）、`f89c301d65c0c8d4bf6e44a3eab11e6c9c9266dd`（长按详情关闭后焦点）。
- **beta 合并证据：** `git merge --no-ff --no-commit origin/beta` 自动完成，无冲突；合并暂存树相对 `origin/beta` 仅保留上述本地 5 个焦点相关文件，beta 增量与本地文件无路径交集。
- **回滚：** 提交前可执行 `git merge --abort`；提交后使用本任务创建的 recovery annotated tag 回退该原子合并提交。

## 评审范围与复用证据

### beta 增量

- beta 从共同祖先至 `74e572173fcc9ab7e274a94b6245b8de90b217c1` 的主题系统、TV 主题资源、导入导出、目录缓存和测试已在 beta 自带的 `docs/beta-sync-review-20260908.md` 中完成首轮发现、修复、定向验证和修复后复评。
- 该记录明确闭环了两项安全问题：TweakCN 输入复用统一 JSON 大小/深度边界；主题传输拒绝 ULA、映射 IPv4、CGNAT、保留/文档地址等非公网解析结果，并记录了对应测试与 `BUILD SUCCESSFUL`。
- 本轮确认该评审记录随 beta 原样进入最终合并树；beta 文件与本地 TV 焦点文件无交集，且合并无未解决路径。依据用户约定，对已覆盖且未发生冲突的 beta 改动不重复展开无效复评，仅核对最终树、血缘和测试覆盖。

### dev3 未推送改动

- `EpisodeDetailDialog.java` 与 `dialog_episode_detail.xml`：TV 剧集详情从海报/剧照/客串网格之间的方向键切换，会识别嵌套焦点并将焦点落到实际卡片；没有可用数据时保留后备路径，并通过 `nextFocus` 补强布局焦点链。
- `TmdbDetailActivity.java`：炫彩详情弹窗关闭后，按返回 RecyclerView 精确定位被长按的集数；主选集 RecyclerView 在 pending adapter update 时强制恢复祖先布局，并在真实 pre-draw 后恢复焦点；焦点请求在布局计算或 holder 尚未附着时按动画帧有限重试，避免卡在旧 position 或丢焦点。
- 两个对应测试文件新增了源码/布局契约，覆盖实际调用顺序、生命周期保护、精确 adapter position、重试路径、网格父子焦点和照片网格下导航。

## 评审发现与处理

### 首轮复评

- 未发现 beta 与本地改动的文件交集或合并冲突。
- 逐处检查了 dismiss 回调的 `binding`、Activity 生命周期、RecyclerView 附着/可见状态，确认无效页面不会抢焦点；独立选集面板保留原有恢复路径，主选集才使用 pre-draw 恢复和 pending-layout 修复。
- 检查了 `focusTmdbRecyclerItem` 的目标边界、`isComputingLayout` 分支、holder 查找、`requestFocus`/`requestFocusFromTouch` 结果和最大重试次数；未发现无限 post、越界或将 key-up 当作移动的风险。
- 检查了 Leanback 网格空数据/不可见数据回退、选中位置边界和海报嵌套子焦点；未发现空指针或焦点落在容器而无法继续导航的问题。

### 修复后复评

- 本轮没有发现需要修复的问题，因此没有新增生产代码或测试修复；复评结论为**通过**。
- `git diff --cached --check`、`git diff --check` 通过，`git ls-files -u` 无输出，任务守卫检查通过。

## 验证

- 定向命令：

  ```text
  bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
    --tests 'com.fongmi.android.tv.theme.*' \
    --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' \
    --tests 'com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapterTest' \
    --tests 'com.fongmi.android.tv.ui.dialog.EpisodeDetailDialogThemeTest' \
    :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
    --no-daemon --console=plain
  ```

- 结果：`BUILD SUCCESSFUL`；主题测试 34 项、本地 TV 焦点测试 139 项，共 173 项测试均 `failures=0`、`errors=0`、`skipped=0`；Mobile Java 编译和 Leanback Java 编译均通过。
- 构建输出中的既有 XML 命名空间、字符串格式及 Room 查询提示为 warning，不构成本轮失败，也未修改无关资源。
- 验证期间再次 `git fetch origin beta --prune`，`origin/beta` 仍为 `74e572173fcc9ab7e274a94b6245b8de90b217c1`，无需重新合并或重复验证。

## 关闭动作

- 下一动作：执行 `bash .codex/scripts/task_guard.sh finish --verified ... --commit-message ...`，由守卫原子提交合并及本任务记录并创建恢复标签。
- 提交后：推送当前 `dev3` 分支和新恢复标签；用中文创建目标为 `beta` 的 PR；最后执行 `git fetch --prune origin` 与 `git pull --ff-only origin dev3`，确认工作树和远端分支状态。
