# TV 网盘线路切换布局冲突修复

## 目标与范围

- 任务：`leanback-pan-switch-layout`，`quick-fix`。
- 目标：线路选择回调发生在 Leanback 布局期间时，完整切换操作在布局结束后执行；快速切换只处理最新选择，页面退出或列表替换后不执行失效选择。
- 基线：`feature/mpv-dv7-fel` / `f836d419518d1a93d4ff77414a88558f3854c7e3`。
- 保护：开始前已有的 `app/.cxx/`，共 104 个文件；测试构建的 CMake staging 放在临时目录。
- 范围：Leanback `VideoActivity`、`FlagSelectionListener`、对应 Robolectric 测试、`app/build.gradle` 的测试配置及本文档。无播放器内核或依赖升级。

## 证据与修复

用户提供的 5.6.0 崩溃截图来自 XGIMI / Android 9（SDK 28）：
`Cannot call this method while RecyclerView is computing a layout or scrolling`，目标为 `VideoActivity` 的 `HorizontalGridView`（`id/flag`），由 `notifyDataSetChanged()` 触发。

本地调用链：`onChildViewHolderSelected` 同步进入 `onItemClick(Flag)`，随后可通过 `updateFocus` → `FlagAdapter.setNextFocusDown`，或 `seamless` → `selectEpisode` → `FlagAdapter.toggle`，立即刷新仍在布局的线路列表。已有 `notifyItemsChanged` 的延迟处理只覆盖直接的选中样式通知，未覆盖这两条间接路径。截图缺少对应 R8 mapping，无法把混淆方法精确还原到某一条路径。

使用与现有 `BaseActivity.postRecyclerUpdate` 一致的主线程延迟及布局重试方式，将完整线路切换移出选择回调。合并待处理选择，执行前核对当前位置、对象身份、adapter 和窗口附着状态；播放页同时拒绝退出期间的线路点击。不捕获并吞掉异常，不按系统版本分支。

这项 RecyclerView 约束适用于所有系统版本。不同设备的布局、滚动时序及不同网盘的剧集/清晰度结构决定是否触发间接刷新；当前证据不能证明 Android 9 独有。

## 验证

2026-09-20 验证通过：

- Leanback armeabi-v7a Debug Java 编译通过。
- Robolectric 4.16 / Android 9（API 28），`FlagSelectionListenerTest` 共 8 项，0 失败、0 错误、0 跳过，测试耗时 20.536 秒；最终 Gradle 运行 `BUILD SUCCESSFUL`，共 46 秒。
- 真实 RecyclerView 观察者确认 `setNextFocusDown` 在布局临界区会抛出截图中的异常。`toggle` 在本地 `cb21feb42287e2299b3cbc9a6c911254b58060aa` 已改为布局期不通知列表（上游 `fa09d226f9` 的 `notifyDataSetChanged()` 是不同基线），因此合并到 `dev2` 后该测试只断言焦点刷新异常，不再要求 `toggle` 抛出。
- 真实 Leanback 布局确实触发选择回调，修复后在布局结束才切换线路并更新焦点与剧集。
- 快速连续切换只执行最后一次；无效位置、同名线路对象替换、adapter 替换及视图离开窗口均不执行旧选择。

定向任务：`:app:testLeanbackArmeabi_v7aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.FlagSelectionListenerTest`。本次临时 init 脚本只保留 Leanback 测试源集、把 CMake staging 放在临时目录，并使用已缓存的 Android SDK 测试包。测试显式推进 16ms 虚拟帧，避免在模拟持续布局时无限执行同一时刻的下一帧回调；进入测试前先推进首帧完成窗口附着。

结果文件：`app/build/test-results/testLeanbackArmeabi_v7aDebugUnitTest/TEST-com.fongmi.android.tv.ui.custom.FlagSelectionListenerTest.xml`。完整成功日志：`/private/tmp/webhtv-pan-switch-01j13nva/gradle-tests-2.log`。此前测试框架调用和虚拟帧初始化问题已在测试代码中修正。

没有受影响的 XGIMI 实机复测证据；Robolectric 不等同于实机网盘端到端播放。

## Recovery anchor

- 已完成：截图/本地调用链分析、任务保护、最小修复、Java 编译及 8 项 Android 9 回归。
- 2026-09-21 C4 第六轮合并复核：本地 `onVideoTouch`（含 `dispatchDiscMenuTouch` 前置）优先于上游整行替换，保留触摸优化与蓝光菜单触摸；`FlagSelectionListener` 按上游引入并在 `dev2` 上重跑 8 项测试通过。
- 未验证：受影响 XGIMI 实机的网盘播放全流程。
- 回滚：本任务单独提交，可整体 revert；上一个已验证状态为上述基线。
- 本记录随修复原子提交，恢复标签使用 `recovery/leanback-pan-switch-layout/` 前缀。
- 后续建议：在受影响 XGIMI 设备上复测原网盘线路切换场景。
