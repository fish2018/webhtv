# TV-ADBLOCK-20260909：去广告总开关前置到首页

> 历史纠正：2026-09-09 用户明确目标为“增强功能 → 站点注入”的总启用开关，不是去广告总开关。本记录对应的实现已由 `TV-CUSTOM-CSP-20260909` 纠正并 supersede；当前代码不再保留本记录中的去广告首页按钮。

## Recovery anchor

- 目标：让 TV 用户从首页通过一个可聚焦的快捷按钮启用/禁用去广告总开关，避免进入设置页多层操作。
- 验收：TV 首页出现可配置的“去广告”按钮；按钮点击复用 `Setting.isAdblock()` / `Setting.putAdblock(boolean)`，并在首页即时显示开/关状态；手机端行为不变。
- 车道/范围：`standard`；仅 TV 首页按钮模型、功能按钮展示/点击、TV 文案/图标、针对性测试和本任务文档。
- 当前分支/基线：`dev2` / `fbb7197e489a233686208fc948c821586f88a91a`。
- 初始工作区：干净，无需保护的 pre-existing dirty path。
- 已完成：入口定位、方案评估、官方 Android TV 导航资料核对、task guard 启动、TV 首页按钮实现、源代码契约测试、Leanback ARM64 Java 编译。
- 当前待办：收录验证结果，执行 task guard finish 原子提交并创建恢复 tag。
- 未验证风险：当前连接的 `192.168.50.3:5557`、`:5559`、`:5561`、`:5563` 均报告 `ro.build.characteristics=tablet`，没有 Android TV 设备；真实遥控器焦点与不同屏幕宽度仍需在 TV APK 安装后补测。
- 回滚锚点：本任务 guard 基线 HEAD；实现只涉及 TV UI 与配置复用，无数据迁移和 native 变更。
- 下一步：更新本记录的验证结果，然后执行 `bash .codex/scripts/task_guard.sh finish`。

## 需求与现状证据

用户需要在 TV 版快速启用/禁用“去广告”功能。当前总开关是 `Setting.isAdblock()`，写入入口为 `Setting.putAdblock(boolean)`；TV 设置页在 `SettingAdActivity` 的 `adblock` 行上切换。TV 首页已有可排序、可隐藏的 `HomeButton`/`Func` 按钮行，首页菜单键则进入固定动作弹窗或直接执行一个导航动作。

现有路径：

1. 首页进入“设置”；
2. 进入“广告”；
3. 聚焦“去广告总开关”后确认。

这条路径对遥控器不够短，而且用户无法在首页判断当前开关状态。当前开关影响 HLS 播放列表净化、Exo `MediaItem` 的 adblock 标志及 AI 广告反馈显示条件；快捷入口必须只改同一个总开关，不能另建一份状态。

## 最佳实践研究

### 证据记录

| 证据 | 访问日期 | 等级 | 支持的结论 | 对 WebHTV 的影响 |
|---|---|---:|---|---|
| Android Developers，TV navigation：`https://developer.android.com/training/tv/get-started/navigation` | 2026-09-09 | A | TV 遥控器控制受限，应减少点击和页面层级；所有可见控件应可通过 D-pad 到达；焦点项应清晰可见；难以到达的控件应迁移到更直接的位置。 | “去广告”属于用户明确要求的高频总开关，放入首页现有可聚焦按钮行比藏在设置页或仅放菜单弹窗更符合 TV 导航原则。 |
| WebHTV 当前 `app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java`、`HomeActivity.java`、`Func.java` | 2026-09-09 | A | 项目已有首页按钮的排序、隐藏、恢复默认和 `Func` 点击分发机制。 | 复用现有 HomeButton，不引入新的导航容器或持久化格式。 |
| WebHTV 当前 `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialog.java`、`SettingPersonalActivity.java` | 2026-09-09 | A | 菜单键是可配置的一次性动作；弹窗内容是固定动作网格，没有开关状态展示。 | 不把状态型开关塞进菜单键动作列表，避免菜单键变成第二套首页入口。 |
| WebHTV 当前 `SettingAdActivity.java`、`SettingAdFragment.java`、`Setting.java` | 2026-09-09 | A | 手机和 TV 已共享同一 `adblock` preference 与切换语义。 | TV 快捷按钮只复用 setter；不修改手机端，不改变播放链路。 |

以下证据类别对本任务不适用：本任务不是上游依赖合并、播放器算法或 native/ABI 变更，因此没有新增上游 commit、PR/revert、维护者讨论、论文、性能基准或二进制 provenance 门禁；现有项目代码和官方 TV 导航规范足以决定入口位置。

## 方案比较

### 方案 A：不变

- 优点：零代码和零回归风险。
- 缺点：继续要求用户进入多层设置，TV 遥控器操作成本高；不满足用户希望前置快捷开关的目标。
- 结论：拒绝。

### 方案 B：加入“首页菜单键”动作

- 优点：按菜单键后可在一个动作中切换；改动面较小。
- 缺点：需要用户先进入“个性设置 → 首页菜单键”配置；菜单键可能被用户绑定为切源、历史、搜索等高频动作；弹窗/动作本身不显示当前开关状态；部分遥控器不提供一致的菜单键体验。
- 结论：不作为主入口。它适合导航动作，不适合需要确认当前状态的全局开关。

### 方案 C：加入“首页按钮”（采用）

- 入口：加入现有 `HomeButton.all()`，默认新安装/未自定义首页按钮的 TV 用户可见，并保留“个性设置 → 首页按钮”的排序和隐藏能力。
- 行为：点击按钮反转 `Setting.isAdblock()`；按钮文字显示“去广告 开/去广告 关”，状态变化后刷新当前功能按钮行。
- 兼容：已有明确保存过首页按钮列表的用户不强制插入新按钮，避免覆盖用户的个性化布局；新按钮会出现在“首页按钮”管理列表中，可手动启用和排序。未保存自定义列表的用户通过新默认值自然获得按钮。
- 优点：首页可见、可聚焦、一次确认完成、状态明确；复用现有焦点环、按钮排序和配置，不增加设置层级。
- 风险：首页按钮行增加一个项目，窄屏可能需要横向移动；通过用户可隐藏/排序、保留左右循环焦点和状态文案缓解。
- 结论：实施。

### 不采用“同时加入两处”

同时加入首页按钮和菜单键会重复暴露同一动作、增加文案/测试/维护面，也会让用户不清楚应配置哪一个入口。本阶段保持一个明确的主入口；菜单键保留现有导航语义。

## 实施设计

1. 给 `HomeButton` 增加稳定 id `8` 和 `home_adblock` 资源；更新完整排序串与默认按钮串。
2. 在 TV `Func` 中为该资源绑定去广告图标，并根据 `Setting.isAdblock()` 生成带状态的按钮文案；内容比较需包含动态文案，确保切换后重绑视图。
3. 在 TV `HomeActivity.onItemClick(Func)` 中增加分支，调用 `Setting.putAdblock(!Setting.isAdblock())` 后刷新 `setFunc()`；不刷新当前播放器或新增运行时副作用，保持现有设置页语义。
4. 为英文、简体中文、繁体中文补齐首页按钮文案。
5. 添加 Leanback 源代码契约测试，锁住 id、默认值、图标/状态文案和点击分发，防止后续首页按钮扩展遗漏。

## 接受标准

- [x] TV 首页的默认功能按钮列表包含“去广告”。
- [x] “个性设置 → 首页按钮”可看到、启用/禁用和排序“去广告”；已有自定义列表不被强制改写。
- [x] 点击 TV 首页“去广告”按钮只反转 `adblock` preference，并即时显示开/关状态。
- [x] 手机端源码、布局和行为未修改。
- [x] Leanback 定向源测试通过。
- [x] Leanback ARM64 Java 编译通过。
- [x] 已明确记录当前无真实 Android TV 设备，焦点/视觉仍需设备验证，没有把编译当作遥控器验收。

## 验证计划

- 最便宜决定性检查：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.bean.HomeButtonSourceTest`。
- 编译检查：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。
- 若构建资源允许，再执行一次 `:app:assembleLeanbackArm64_v8aDebug`，确认 view binding、资源和 TV 变体完整闭合。
- 设备场景（可用时）：首页聚焦按钮 → 确认，观察文案从“去广告 开”切换为“去广告 关”，返回/重新进入首页后状态保持；在首页按钮设置中隐藏/恢复后焦点仍可循环。

## 回滚与发布

- 回滚方式：还原本任务单次提交；不需要迁移 preference，不需要删除用户数据，不需要重建 native assets。
- 发布影响：仅 Leanback TV UI 和既有 `adblock` preference 读取；不新增依赖、API、网络请求、包内二进制或手机行为。

## 实施与验证记录

- 2026-09-09：`HomeButton` 增加稳定 id `8`，新安装/未自定义首页按钮的 TV 用户默认看到“去广告”；已有显式自定义列表不被强制插入。
- 2026-09-09：`Func` 复用现有 `ic_live_block` 图标，保存按钮文案快照并让内容比较包含状态文案，保证切换后 `setFunc()` 会重绑为“开/关”。
- 2026-09-09：`HomeActivity` 点击分支只反转 `Setting` 的 `adblock` preference，然后刷新首页功能按钮；没有改菜单键、手机端或播放器链路。
- 首次编译：因 `Func.java` 遗漏 `Setting` import 失败；补上直接 import 后未改变设计并重跑同一验证。
- 最终验证（2026-09-09）：`bash .codex/scripts/task_guard.sh check` 通过；`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.HomeButtonSourceTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --check` 通过。
- 构建过程只出现仓库既有资源警告（`values` 中未知 `g` 命名空间、既有非 positional string substitution、CXX 32-bit 提示），没有本任务新增错误。
- 设备审计：ADB 当前仅连接四台 Android 9 tablet（SM-N9700、V1923A、HD1910、NX627J），因此没有把手机/平板运行结果冒充 TV 遥控器验收。
- 状态：历史误判实现已提交；目标经用户澄清后由 `docs/TV-CUSTOM-CSP-20260909-home-shortcut.md` 纠正，不再作为当前产品行为依据。
