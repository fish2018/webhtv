# TOUCH-20260907 TV 触摸适配

## 授权与验收
用户已明确“按计划实施”。一个任务/提交覆盖：首页自由触摸滚动、站源触摸滚动不切换数据、按站源恢复结果位置、TV 点播全屏横滑进度/左半屏竖滑亮度/右半屏竖滑音量。原反馈“高度”按已批准方案解释为亮度，不新增画面缩放。开关关闭和遥控器操作保留原有语义；不修改 mobile、直播、投屏和播放器内核。

## 决策就绪的证据（访问日期 2026-09-07）
- A / 当前工程：基线 `db59f3eeb85ff0e4ed993e814d9b81b08036bcb7`；`HomeActivity.setRecyclerView/initEvent`、`CollectActivity.scheduleCollect/setSearchItemsLazy`、`CustomKeyDownVod.onTouchEvent`、`VideoActivity.initEvent` 是实际路径。先前草稿只有源码字符串测试通过，不能证明回弹解决。
- A / 精确依赖源码、官方 API 注释：`https://dl.google.com/dl/android/maven2/androidx/leanback/leanback-grid/1.0.0/leanback-grid-1.0.0-sources.jar`（本机依赖 leanback 1.2.0 → grid 1.0.0）；`GridLayoutManager.onLayoutChildren` 的 `scrollToFocus` 仅在 `FOCUS_SCROLL_ALIGNED` 成立，且与是否触摸无关；`focusToViewInLayout` 会重新对齐旧选中项。这比此前“焦点可能抢占”的推测更直接。
- A / 官方 API：上述 jar 的 `BaseGridView.setFocusScrollStrategy`、`setFocusSearchDisabled` 注释及实现。触摸期间改用 ITEM 策略并阻止子焦点回收；按键到来时恢复原策略并从当前可见项恢复焦点，不能永久关闭 D-pad。
- A / 上游维护证据：同一 `GridLayoutManager` 的 b/67370222 注释说明布局对齐/可见项裁剪的反馈循环风险。它不是用户设备的复现记录，不据此声称“已真机复现”。本任务不修改/升级 AndroidX。
- B / 成熟本地实现与测试：`main/.../PlayerGesture.java`、`utils/BrightnessPolicy.java` 和 `testMobile/.../VideoActivityLayoutTest.java`；借鉴窗口亮度和音乐音量控制。**不直接复用整个 PlayerGesture**：其侧边四分区、中央竖滑切集动画、双指缩放、长按倍速及小窗消费 DOWN 均超出本次批准语义。最初草稿直接接入会破坏小窗点击进入全屏，因此撤销这部分未提交草稿，补充现有 TV 控制器的可选滑动能力。
- 官方在线文档尝试：web 工具未返回正文，agent-reach/Jina 读取 Android 手势文档出现 TLS EOF；以可获取的 Google Maven 精确版本源码及其 API 文档为依据，不声称已读取失败网页。
- 论文/博客/性能基准：本变更不发明识别算法、不变更渲染/解码/ABI；无须用论文替代 Android 事件契约。性能验收为 MOVE 中常数开销、无 Toast 连发/全树重复扫描/网络请求。

### 方案比较与选定
1. 不变更：无法满足三项反馈，拒绝。
2. 直接复用上游/共享行为：Leanback 默认对齐造成回弹；共享 PlayerGesture 引入未授权缩放/切集等，拒绝原样接入。
3. 窄适配（采用）：触摸期间关闭焦点对齐和焦点驱动业务切换，遥控器恢复；结果按实际展示站源记录首项+像素偏移并隔离延迟任务；TV 自有手势仅在优化开关开启且全屏生效，使用现有 seek 通道、BrightnessPolicy 和 AudioManager，无依赖升级。

### 验收和回滚
- 首页和两种站源排列：多次拖动/惯性滚动不被布局拉回；触摸列表不自动切源，点击才切换；按 D-pad 后仍能导航。
- 结果：同源重按不回顶，A→B→A 恢复 A 的首项与像素偏移；快速连续切源不得错记位置；新搜索/筛选清除不适用位置；空结果及列表缩短不得越界。
- 手势：横滑预览、仅正常 UP 提交一次；CANCEL/多指/失焦不提交；左/右半屏起点锁定亮度/音量；OSD 抬手清理；小窗点击、关闭优化、控制栏按钮和遥控器不回归。
- 回滚：恢复本任务唯一提交前状态；未提交阶段回退仅限 guard 内任务补丁，不触及其他工作。关闭触屏优化可回到旧输入行为。

## R2 实施记录（TOUCH-20260907-R2）

### 实际实现
- `TouchOptimizationHelper` 在不把 leanback 依赖泄漏到 mobile 公共源码的前提下，通过 leanback 运行时公开 API 绑定 `BaseGridView` 的触摸拦截器：触摸 DOWN 临时切到 `FOCUS_SCROLL_ITEM` 并禁用焦点搜索，抬手/取消时恢复原策略，并在列表 settling 完成前继续抑制触摸触发的选中回调；不覆盖项目已有的 `setOnKeyInterceptListener`。优化关闭时不安装/不保留该行为。动态 RecyclerView 子项仍由现有 attach listener 覆盖。
- `HomeActivity` 和 `CollectActivity` 在站源/类别选择回调中忽略触摸期间的焦点变化；点击回调仍执行真实切换。首页自动切类仅保留遥控器路径。
- `CollectActivity` 为每个站源保存首个可见 adapter position 与像素 top offset；恢复时按当前 adapter 数量夹紧，异步恢复前再次确认站源仍是 active；新搜索、分组/相似度筛选和列数改变清空旧位置。
- TV `VideoActivity` 不再将公共 `PlayerGesture` 直接接到 TV 播放器：优化关闭时保留 `CustomKeyDownVod`；优化开启且全屏时由 TV 层使用现有 `seekTo` 通道处理横滑，按按下点左/右半屏分别调亮度/音乐音量，使用 `BrightnessPolicy`、`AudioManager` 和 TV widget OSD。多指、CANCEL、失焦不提交 seek；正常 UP 只提交一次并清理 OSD。

### R2 验证
- `git diff --check`：通过。
- `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.helper.TouchOptimizationHelperSourceTest :app:assembleLeanbackArm64_v8aDebug --no-daemon`：通过，11 tests completed；同时完成 Leanback Arm64 Java 编译并组装 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`。
- 首次测试失败原因已修正：旧源码断言仍要求上一版已撤销的 `PlayerGesture` 接线，非生产代码失败；测试契约已改为断言当前 TV 触摸接线、像素恢复、焦点策略和取消边界。
- 设备端：使用 `192.168.50.3:5555` 安装上述 APK 成功，`monkey -p com.silent.android.webhtv 1` 启动成功，前台为 `HomeActivity`，进程 PID `6464`；本次只完成启动冒烟，未宣称完整触摸场景已在设备端覆盖。

## Recovery anchor
- 目标/范围：完成首页/站源触摸滚动与不误切换、按站源恢复结果像素位置、TV 全屏横滑 seek 与左右半屏亮度/音量 OSD；保留触屏开关关闭和遥控器语义。
- 阶段：`TOUCH-20260907-R2`，guard active；上一提交 `3bb2ff086ffbf0aab5906205526bd9f887e22fd7` 和恢复标签保留。
- 当前文件：本 R2 guard 的 7 个任务文件处于未提交修改；无其他脏路径。
- 已验证：Leanback Arm64 Java 编译、11 项 focused 源码契约测试、`git diff --check`、Arm64 APK 组装，以及设备安装/`HomeActivity` 启动冒烟。
- 未验证：完整设备端 MotionEvent/焦点回弹与各类手势场景；仍需按验收清单在真实设备上继续覆盖。
- 下一步：执行 `task_guard.sh finish`，原子提交本 R2 变更并创建恢复 tag。
