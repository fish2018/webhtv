# WebHTV 触屏优化模式设计

状态：实现完成，设备补验受限
日期：2026-09-04
范围：TV 版 leanback flavor

## 1. 背景

WebHTV 的 TV 版当前以遥控器/焦点导航为主。部分车机和触屏电视用户会安装 leanback 版本，因为它的横屏布局、投屏、推送和播放界面更合适，但触屏体验仍按遥控器逻辑设计。典型现象是：第一次点击只把焦点移动到目标控件，第二次点击才触发实际动作。

代码层面的直接原因是 leanback 布局中有大量控件使用了 `android:focusableInTouchMode="true"`。当前统计共有 252 处，分布在 73 个 layout 文件中。该属性在遥控器场景下有意义，但在触摸场景下会让第一次触点先进入焦点选择流程。

本次只设计“触屏优化模式”，不处理“某些页面无法下滑”的问题。滚动问题需要单独复现和定位，不应和本项改动混在一起。

## 2. 目标

1. 在 TV 版中提供可开关的触屏优化模式。
2. 开启后，普通可点击控件第一次触点即可触发动作，不再先抢焦点。
3. 保持遥控器、D-pad、OK 键和返回键的现有导航行为不变。
4. 不引入新依赖，不修改应用包名、播放器引擎或数据存储结构。
5. 改动集中在设置和视图应用层，避免直接大范围改 73 个布局文件。
6. 设置可随用户偏好备份，卸载重装后可按项目现有备份能力恢复。

## 3. 非目标

1. 不修复嵌套滚动、无法下滑、焦点与滚动冲突问题。
2. 不复制 mobile 版整套界面。
3. 不在第一版引入完整平板布局。
4. 不改变播放器手势、双击、长按、音量/亮度/进度手势的语义。
5. 不改变 WebView 的加载、渲染、输入和脚本行为。
6. 不做触屏设备自动识别的静默默认值，避免现有用户行为被意外改变。

## 4. 方案选择

### 方案 A：直接修改 leanback layout

把 73 个 layout 里的 `android:focusableInTouchMode="true"` 改为 `false` 或删除。

优点是实现简单。缺点是影响范围大，所有现有用户都会立即改变行为，不利于灰度和回滚；部分控件可能依赖当前焦点行为，需要逐页确认。

结论：不作为第一版方案。

### 方案 B：运行时触屏优化模式

新增一个用户可见设置项，只在 leanback flavor 生效。开启后，通过统一的 helper 在 Activity、Dialog 和动态视图上调整可点击控件的触摸模式。

优点是范围可控、可回滚、可灰度，默认不影响现有遥控器用户。缺点是实现上需要覆盖动态视图和弹窗，不能只靠静态布局修改。

结论：采用该方案。

### 方案 C：完整电视模式/平板模式

参考影视仓，引入电视模式和平板模式两套布局。

优点是最终体验更完整。缺点是涉及页面结构、尺寸、导航和交互重设计，范围过大，不应与触屏点击修复绑定。

结论：延后，不进入本期范围。

## 5. 产品行为

### 5.1 默认状态

第一版默认关闭。

理由是当前产品以遥控器交互为主，直接默认开启会改变所有 leanback 用户的第一触点行为。车机用户可在设置中手动开启。后续收集反馈后，可以再评估“检测到触屏设备时默认开启”的策略。

### 5.2 设置入口

设置页新增“触屏优化”开关。

建议放在界面/显示相关配置附近。该入口只在 leanback 版本显示；mobile 版本本身已经是触屏优先，不需要显示该设置。

### 5.3 开启后的行为

普通可点击控件：

- 第一次触点直接触发点击动作。
- 不再先移动焦点。
- 长按行为保持系统默认语义，不额外扩展。

输入框：

- 保持系统默认行为，允许第一次触点聚焦、弹出软键盘或进入编辑状态。

WebView：

- 保持现有 WebView 焦点和交互行为，不强制关闭其触摸能力。

列表和网格：

- item 的点击行为遵循“第一次触点直接触发”。
- item 内部如果还有独立按钮，则优先命中按钮。

播放器：

- 不调整 `PlayerGesture` 和播放控制栏的业务逻辑。
- 只确保触屏优化不额外改变手势判定顺序。

### 5.4 关闭后的行为

完全回到当前 leanback 行为：第一次触点可先聚焦，第二次触点触发动作。遥控器导航不变。

## 6. 技术设计

### 6.1 配置存储

新增一个布尔型偏好：

```text
key: touch_optimized
default: false
effective flavor: leanback
```

在 `Setting` 中提供读写入口：

```java
public static boolean isTouchOptimized()
public static void putTouchOptimized(boolean enabled)
```

读取时建议至少叠加 leanback 判断，避免 mobile 版本误用：

```java
return Util.isLeanback() && Prefers.getBoolean("touch_optimized");
```

同时把 `touch_optimized` 加入 `Backup.APP_PREFS`，保证用户偏好可以随现有备份/恢复机制迁移。

### 6.2 统一 Helper

新增一个轻量工具类，例如：

```text
app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java
```

职责：

1. 判断当前是否开启触屏优化。
2. 遍历传入的 View 树。
3. 对 `focusableInTouchMode == true` 且不是输入类控件的 View 调用 `setFocusableInTouchMode(false)`。
4. 不修改 `focusable`，避免破坏 D-pad 导航。
5. 不修改 `clickable`、`longClickable`、`OnClickListener`。

处理规则：

```text
View.focusable = 保持不变
View.focusableInTouchMode = false
View.clickable = 保持不变
View.longClickable = 保持不变
View.onClickListener = 保持不变
```

例外控件：

- `WebView`
- `EditText`
- 其他文本输入控件

这些控件保持系统默认的触摸焦点行为，否则可能影响输入、光标和选择。

### 6.3 Activity 接入

在 leanback 的 `BaseActivity.setContentView()` 后统一应用：

```text
setContentView(view)
    -> super.setContentView(view)
    -> addCustomWall()
    -> TouchOptimizationHelper.sync(decorView)
```

这样 leanback 侧大多数页面可以在一个入口接入，不需要每个 Activity 单独调用。

对动态添加的子 View，可以使用 `ViewTreeObserver.OnGlobalLayoutListener` 或 `OnAttachStateChangeListener` 补应用。第一版建议先使用布局后的重扫方式，减少对业务页面的侵入。

### 6.4 Dialog 接入

DialogFragment / AlertDialog 的 root view 通常不在 Activity content view 树的生命周期内，需要单独接入。

helper 提供 Dialog 重载：

```java
TouchOptimizationHelper.sync(dialog);
```

`BaseActivity` 使用递归 `FragmentLifecycleCallbacks` 覆盖 `DialogFragment`，同时在 `LightDialog`、`SiteDialog.showDirect()` 和播放器集中式面板入口覆盖直接创建的窗口。第一版覆盖的高频弹窗包括：

- 设置弹窗
- 站点/配置选择
- 选集
- 轨道/音轨/字幕
- 搜索
- 备份/恢复

低频弹窗可以后续逐步覆盖，不影响功能开关本身。

### 6.5 RecyclerView 接入

RecyclerView 的 item view 会在滚动、复用和数据刷新时重建。helper 不应只处理初始布局。

建议两种方式结合：

1. Activity 布局完成后对当前 view 树做一次遍历。
2. 对 `RecyclerView` 添加 `OnChildAttachStateChangeListener`，在 child attach 时继续应用规则。

这样可以在不做全量 layout 修改的情况下，覆盖大部分动态列表。

### 6.6 设置页接入

在 leanback `SettingActivity` 中新增开关入口，逻辑为：

```text
click -> 取反 Setting.isTouchOptimized()
      -> Setting.putTouchOptimized(next)
      -> updateSettingSummary()
```

切换后不需要重启应用。当前 Activity 可以立即重应用；其他已打开 Activity 可以在下一个 `onResume` 或 `onContentChanged` 时重应用。第一版如果存在个别页面状态复杂，允许提示用户重新进入页面，但不建议要求杀进程。

## 7. 兼容性

### 遥控器

helper 只关闭 `focusableInTouchMode`，不关闭 `focusable`。D-pad 焦点导航仍可移动到目标控件，OK 键仍然触发点击。

### 触屏电视

关闭默认开启后，普通触屏电视用户行为不变。需要触屏直达的用户可以手动打开。

### 车机

车机开启设置后，主要页面可从“点两下”变为“点一下”。投屏、推送和播放控制页保持 TV 版布局能力。

### mobile flavor

不注册设置入口，不执行 helper。mobile 版本保持现有触屏行为。

## 8. 实施顺序

1. 新增 `touch_optimized` 偏好和 `Setting` 读写方法。
2. 新增 `TouchOptimizationHelper`。
3. 在 leanback `BaseActivity` 接入。
4. 覆盖高频 Dialog。
5. 处理 RecyclerView 动态 child。
6. 设置页增加开关。
7. 补备份 key。
8. 手工回归。

## 9. 验收标准

开启触屏优化后：

1. TV 版首页、分类、历史、搜索、设置页中，普通可点击 item 第一次触点直接进入或执行动作。
2. 投屏、推送、播放器控制相关入口第一次触点可用。
3. 弹窗中的主要按钮和列表项第一次触点可用。
4. 输入框仍可正常聚焦和输入。
5. WebView 页面不因该开关失去正常交互能力。
6. 遥控器 D-pad 和 OK 键导航行为不变。
7. 关闭开关后，行为恢复到当前版本。
8. 备份/恢复可以包含该设置。

## 10. 验证计划

建议手工验证设备组合：

1. 车机触屏：验证车机用户最关心的单击直达和投屏/推送入口。
2. 普通电视 + 遥控器：验证焦点导航和 OK 键不回归。
3. 触屏电视或带触摸的调试设备：验证设置开关、列表、弹窗和播放器入口。
4. Android TV 模拟器：作为基础回归环境，覆盖设置切换和主要页面。

重点页面：

- 首页
- 分类
- 搜索
- 历史/收藏
- 详情/选集
- 直播
- 投屏
- 推送
- 设置

重点场景：

- 开启触屏优化后的首次点击。
- 关闭触屏优化后的双击旧行为。
- 遥控器导航和触摸操作交替使用。
- 弹窗打开后的触摸点击。
- 输入框聚焦和编辑。
- WebView 页面加载和返回。
- 播放器显示/隐藏控制栏、单双击和长按。

## 11. 风险

### 风险：输入类控件无法聚焦

处理：helper 遇到 `EditText`、`WebView` 等控件直接跳过，保持系统默认行为。

### 风险：某些页面依赖先聚焦展示状态

处理：第一版采用显式开关，默认关闭。发现问题可以立即关闭回滚，不需要改动布局或数据。

### 风险：动态弹窗遗漏

处理：先覆盖高频弹窗；遗漏弹窗继续表现为当前“先聚焦再点击”，不会造成崩溃。

### 风险：列表复用导致规则失效

处理：helper 除了扫描根布局外，还给 RecyclerView 增加 child attach 监听，覆盖滚动复用场景。

### 风险：与播放器手势冲突

处理：本期不修改播放器手势。触屏优化只作用于焦点属性，不新增或拦截手势。

## 12. 回滚策略

该功能由单个布尔偏好控制。

出现问题时：

1. 用户可在设置中关闭“触屏优化”。
2. 如果需要服务端止血，可在后续版本中把读取逻辑临时收敛为 `return false`。
3. 代码回滚时只回退设置项、helper、Activity/Dialog 接入和设置页入口，不涉及数据迁移。

## 13. 后续可选项

1. 触屏设备检测默认开启。
2. 触摸反馈样式统一。
3. 针对车机横屏的更高对比度或更大触控目标。
4. 独立平板布局模式。
5. 单独排查嵌套滚动和无法下滑问题。

## 14. 决策依据

### 14.1 平台源码

- 来源：本机 Android SDK 37 官方源码 `sources/android-37.0/android/view/View.java`，访问日期 2026-09-04，证据等级 A。
- `View` 的 touch mode 文档明确说明：触摸模式下只有 `isFocusableInTouchMode()` 为 `true` 的 View 才会取得焦点；普通可触摸按钮不会取得焦点，而是直接触发点击。
- `View.onTouchEvent()` 的 ACTION_UP 路径会在 View 可聚焦、触摸模式可聚焦且尚未聚焦时先调用 `requestFocus()`；只有 `focusTaken == false` 才调用 `performClickInternal()`。这直接解释了“第一次只聚焦、第二次才点击”。
- `setFocusableInTouchMode(false)` 只清除 `FOCUSABLE_IN_TOUCH_MODE`；本实现不调用 `setFocusable(false)`，因此 D-pad 所需的普通焦点能力保留。
- 对 WebView、EditText 和 `onCheckIsTextEditor()` 为真的自定义编辑控件跳过整个子树，避免改变输入、光标和软键盘行为。

### 14.2 项目实际依赖

- Gradle `dependencyInsight` 确认 leanback debug 运行时解析到 `androidx.fragment:fragment:1.6.1`，证据等级 A。
- Gradle `dependencyInsight` 确认 leanback debug 运行时解析到 `androidx.recyclerview:recyclerview:1.4.0`，证据等级 A。
- Fragment 视图创建与 Dialog 启动通过递归 `FragmentLifecycleCallbacks` 接入；RecyclerView 通过 `OnChildAttachStateChangeListener` 在复用 child 重新挂载时同步。
- 外部 Android 文档网页在 2026-09-04 连续发生 TLS 握手失败，因此没有以搜索摘要替代原文；平台结论改由同版本本地 SDK 官方源码和实际解析依赖证明。

### 14.3 其他证据类别

- 上游 PR、Issue、revert 与维护者讨论：本变更不移植上游补丁，且 AOSP 源码直接给出确定行为，因此不作为设计判定条件。
- 成熟相关项目代码：本项目 73 个 leanback layout、252 处 `focusableInTouchMode="true"` 是当前产品行为的直接样本；未引入另一套产品架构作为依赖。
- 论文、博客与基准：本问题是 Android View 确定性事件分支，不涉及算法、性能模型或经验性参数，不适用。

### 14.4 方案比较结论

- 不改：保留车机/触屏电视的双击问题，不满足目标。
- 原样批量改 73 个布局：简单但不可灰度，默认改变全部 TV 用户行为，回滚面过大。
- WebHTV 适配方案：默认关闭、leanback-only、运行时遍历、保留原值并可恢复，集中覆盖 Activity、Fragment/Dialog 和 RecyclerView；这是当前风险与回滚成本最低的方案。

## 15. 实现记录

1. `Setting` 新增 `touch_optimized` 读写，读取叠加 leanback flavor 判断，默认值为 false。
2. `Backup.APP_PREFS` 纳入该键，并补设置同步筛选测试。
3. `TouchOptimizationHelper` 使用弱引用表只记录首次看到的原始 `focusableInTouchMode=true` 状态；重复扫描不覆盖原值，关闭后恢复并移除记录。
4. helper 在 mobile flavor 直接返回；WebView、EditText 和文本编辑器子树不参与修改。
5. leanback `BaseActivity` 在内容树建立、`onResume`、Fragment view 创建和 DialogFragment 启动时同步。
6. RecyclerView 仅在优化开启时安装一次弱引用跟踪的 child attach 监听，动态 item 继续同步。
7. `LightDialog`、`SiteDialog.showDirect()` 和播放器直接面板补充 Dialog 同步入口。
8. 设置页新增三语言“触屏优化”开关；开关本身保持 `focusable=true`、`focusableInTouchMode=false`，确保功能关闭时也能单击启用，同时仍支持 D-pad/OK。

## 16. 当前验证记录

- RED：原 helper 因 BOM、非法常量和未定义 tag key 无法通过 leanback Java 编译。
- RED→GREEN：原始状态只记录一次、RecyclerView child 同步、输入子树跳过、mobile no-op、Activity/Fragment 接入、直接 Dialog/播放器面板接入等契约测试已完成。
- RED→GREEN：新增开关最初需要两次触点；新增 XML 行为测试后将该行 `focusableInTouchMode` 改为 false，7 项 helper 契约测试通过。
- `BackupPreferenceFilterTest` 9 项通过，其中包含 `touch_optimized` 只随 settings 备份的断言。
- 最终组合构建 `compileMobileArm64_v8aDebugJavaWithJavac assembleLeanbackArmeabi_v7aDebug` 通过，耗时 3 分 26 秒；日志保存在 `build/touch-optimization-final-build.log`。
- 最终 APK 大小为 177,375,055 bytes，已明确安装到 `emulator-5554`；从设备当前 code path 拉回的 APK 与本地产物长度一致，且设备侧 Manifest 包含 `SettingActivity`、`HomeActivityHistory` 和 `HomeActivityCurrent`。
- API 28 模拟器已完成同一 leanback 调试安装链的交互检查：默认关闭时首页设置入口第一次触点只聚焦；开启后重启应用，普通设置入口第一次触点可直达；D-pad Right 可从 `vod` 移动到 `vodHome`；站点选择直接窗口可单击打开。
- `touch_optimized` 已恢复为 false，`home_menu_key` 已恢复为原值 1。最终安装后，shell 直接启动未导出的 `SettingActivity` 被系统权限拒绝，因此无法再独立重复“最终 APK 开关单击启用/关闭恢复”的完整设备闭环；该行为由 7 项 helper/XML 契约测试和源码路径覆盖。
- 定向结果：`TouchOptimizationHelperSourceTest` 7/7、`BackupPreferenceFilterTest` 9/9，均为 0 failures、0 errors。

## 17. Recovery anchor

- 目标：完成 leanback 触屏优化模式，保持 mobile、输入控件、WebView、D-pad 和播放器手势语义不变。
- 验收：开关默认关闭且可单击启用；开启后普通控件单击直达；关闭后恢复旧行为；动态列表/高频弹窗覆盖；偏好可备份。
- 当前分支/基线：`dev2` / `fbbefb75dbcbce1fcec259bb3a81a5e2e889362e`。
- 当前文件：见 `.codex/task-state/current/scope`；任务外 dirty 路径为空。
- 已完成：实现、RED→GREEN 契约测试、最终 mobile 编译、最终 leanback APK 构建、APK 安装、主要 API 28 设备场景和模拟器状态恢复。
- 设备补验限制：最终安装后无法由 shell 启动未导出的 `SettingActivity`，所以最终 APK 的开关单击/关闭设备闭环未独立重放；代码契约已验证入口属性、模式切换路径和恢复逻辑。
- 未决风险：低频直接 Dialog 可能仍保持旧双击行为；第一版按设计只保证高频路径，遗漏不会导致崩溃。
- 回滚：用户关闭 `touch_optimized`；代码回滚设置项/helper/入口/生命周期接入即可，无数据迁移。
- 下一步：执行 `task_guard finish`，原子提交本任务路径并创建本地恢复 tag。
