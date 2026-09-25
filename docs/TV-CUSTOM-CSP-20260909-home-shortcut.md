# TV-CUSTOM-CSP-20260909：站点注入界面前置到首页

## Recovery anchor

- 目标：让 TV 用户从首页通过一个可聚焦的快捷按钮直接打开“增强功能 → 站点注入”管理界面，避免进入设置页多层操作。
- 验收：TV 首页出现可配置的“站点注入”按钮；点击后打开现有 `CustomCspDialog`，用户可在界面内启用/禁用全局开关并编辑条目；保存后由现有对话框重新加载当前 VOD/直播配置并刷新首页按钮状态；手机端行为不变。
- 车道/范围：`standard`；仅撤销前一版误加的 TV 去广告按钮，增加站点注入首页按钮、站点注入开关复用、TV 文案、针对性测试和任务记录。
- 当前分支/基线：`dev2` / `6eb76c5bca0b00e69520256fffee900bc9ed8dac`。
- 初始工作区：干净，无需保护的 pre-existing dirty path。
- 已完成：用户目标纠正、站点注入开关与配置重载路径定位、方案决策、task guard 启动、首页入口实现、点击行为修复和定向验证。
- 当前待办：原子提交并创建恢复 tag。
- 未验证风险：当前无 Android TV 设备；真实遥控器焦点、文件权限弹窗和首页按钮在不同屏幕宽度下的视觉效果需设备验证。
- 回滚锚点：本任务 guard 基线 HEAD；回滚仅还原 TV 首页点击分发、移除上一版未使用的保存辅助方法，无数据迁移、依赖或 native 变更。
- 下一步：执行 `bash .codex/scripts/task_guard.sh finish` 完成本次点击行为修复的提交和恢复 tag。

## 需求与现状证据

用户澄清的目标是“增强功能 → 站点注入”的启用/禁用，而不是广告功能。当前站点注入由 `CustomCspSetting.Registry.enabled` 控制：

- `CustomCspSetting.load()` 从 `TV/CustomCsp/registry.json` 读取注册表；缺少字段时 `Registry.isEnabled()` 默认返回 `true`。
- `CustomCspSetting.save(Registry)` 会保留全部站点/直播/其它注入条目并写回同一注册表，但要求文件访问权限。
- VOD、直播和根配置注入均在配置重新加载时读取这个全局开关；单纯写文件而不重载，当前页面可能继续使用旧的注入结果。
- TV `SettingEnhanceActivity` 的“站点注入”行当前进入完整 `CustomCspDialog`，不是单独切换；手机 `SettingEnhanceFragment` 保持原有完整编辑入口。
- TV 首页已有可排序、可隐藏的 `HomeButton`/`Func` 按钮行，适合承载状态型快捷开关；首页菜单键是一次性导航动作弹窗。

因此首页快捷入口应打开既有 `CustomCspDialog`，让用户沿用原有的全局开关、条目编辑、权限和保存流程；不能在首页另写一套隐式切换逻辑，也不能只改首页文案而不进入管理界面。

## 方案比较

### 方案 A：不变

- 优点：零代码风险。
- 缺点：TV 用户继续需要进入设置、增强功能、站点注入后才能切换，不满足用户明确的快捷操作需求。
- 结论：拒绝。

### 方案 B：加入首页菜单键

- 优点：可以复用菜单键动作分发。
- 缺点：菜单键可能已被绑定为切源、切线路、搜索、历史等动作；菜单动作没有常驻状态显示；首次使用仍需进入个性设置配置菜单键；站点注入保存还可能触发权限请求和配置重载，不适合隐藏在一次性动作列表中。
- 结论：不采用。

### 方案 C：加入首页按钮并直接打开站点注入界面（采用）

- 使用现有 `HomeButton` 配置体系新增独立稳定 id；按钮文案按注册表全局 `enabled` 状态显示“站点注入：开/关”。
- 点击时先走现有 `PermissionUtil.requestFile`；获得权限后直接调用 `CustomCspDialog.show(...)`。对话框内部负责全局开关、条目编辑、保存和 VOD/直播重载。
- 新安装或没有显式自定义首页按钮列表的用户默认可见；已有自定义列表不被强制改写，仍可在“个性设置 → 首页按钮”中手动启用、隐藏和排序。
- 无文件权限时沿用现有权限提示；对话框打开失败不修改状态；不改变手机端完整编辑流程。
- 结论：实施。

## 最佳实践与设计决定

- TV 首页按钮比菜单键更适合高频、需要显示状态的全局开关；这也延续项目已有首页按钮和 D-pad 焦点模型。
- 不把全局开关迁移到 `Setting.java`：它不是普通 preference，而是与站点条目、插入位置和本地文件同一注册表的一部分。
- 不在首页按钮点击时直接改内存对象或静默写文件：`CustomCspSetting.inject(...)` 在 VOD/直播配置加载阶段读取注册表，现有 `CustomCspDialog` 已负责保存并触发配置重载；首页只负责打开这个经过验证的管理入口。
- 不覆盖用户自定义首页按钮顺序；新按钮使用新 id `9`，避免把前一版误加的去广告 id `8` 解释成新的站点注入动作。
- 按钮状态只显示“开/关”，不在首页读取并展示条目计数，避免首页功能按钮每次刷新承担不必要的完整统计文案；详细条目数量仍由增强功能页展示。

## 实施设计

1. 删除前一版 TV 去广告按钮的 id、文案、状态读取和点击分发；删除其源测试，避免错误行为继续存在。
2. 在 `HomeButton` 中加入站点注入按钮 id `9`，更新默认/排序目录；保留旧 id `8` 不再复用，避免旧版本自定义列表发生语义转换。
3. 在 `Func` 中绑定站点注入图标和动态状态文案；内容比较包含开关状态，确保刷新后按钮视图更新。
4. 在 TV `HomeActivity` 中处理权限并调用现有 `CustomCspDialog.show(...)`；对话框保存成功后通过回调调用 `setFunc()`。
6. 手机端不增加首页按钮、不改变 `SettingEnhanceFragment` 的站点注入编辑入口。

## 接受标准

- [x] TV 首页默认功能按钮包含“站点注入”。
- [x] “个性设置 → 首页按钮”可看到、启用/禁用和排序“站点注入”；旧的自定义列表不会被强制覆盖。
- [x] 点击按钮打开现有站点注入管理界面，不能静默切换或无反馈。
- [x] 在管理界面内修改全局 `enabled` 后，由现有保存流程重新加载当前 VOD/直播配置，使注入开关对当前页面生效。
- [x] 没有文件权限时沿用现有权限提示；管理界面保存失败时不刷新成错误状态。
- [x] 前一版误加的去广告首页按钮、文案和测试被移除。
- [x] 手机端源码、布局和行为未修改。
- [x] Leanback 定向源测试与 ARM64 Java 编译通过。
- [x] 无真实 Android TV 设备时，已明确记录焦点/视觉/权限弹窗仍需设备验收。

## 验证计划

- 定向源测试：`SiteInjectHomeButtonSourceTest`，覆盖新按钮 id/默认值、状态文案、`CustomCspDialog` 打开入口、权限检查以及旧去广告入口移除。
- 编译：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。
- 测试：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest`。
- 静态检查：`git diff --check` 和 task guard scope 检查。
- 设备场景（当前不可用）：首页聚焦“站点注入”→确认；有权限时直接打开站点注入管理界面，在界面内切换并保存后按钮状态与当前 VOD/直播注入结果刷新；无权限时出现既有授权提示；首页按钮设置中的隐藏/排序和左右焦点循环正常。

## 回滚与实施记录

- 回滚：还原本任务原子提交即可；不需要清理用户数据或重建 native assets。新 id `9` 不复用误加的 id `8`，避免历史自定义按钮出现错误语义迁移。
- 发布影响：仅 Leanback 首页快捷入口和站点注入注册表的既有保存/重载路径；不新增依赖、网络请求、公开 API 或手机行为。
- 最终验证（2026-09-09）：`bash .codex/scripts/task_guard.sh check` 通过；`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --check` 通过。
- 构建过程仅出现仓库既有资源命名空间、字符串格式和 CXX 32-bit 警告，没有本任务新增错误。
- 设备审计：当前连接设备均为 Android 9 tablet，不是 Android TV；因此没有把手机/平板运行结果冒充 TV 遥控器、权限弹窗或视觉验收。
- 2026-09-09 点击修复：将首页按钮从静默调用注册表切换改为调用现有 `CustomCspDialog.show(this, this::setFunc)`；站点注入界面、全局开关和保存重载统一由既有对话框负责。
- 2026-09-09 用户反馈的根因：首页按钮原先只修改注册表并重载配置，没有打开任何可见界面，因此用户看不到反馈；修复为复用增强功能页已有的权限申请和 `CustomCspDialog` 入口。
- 点击修复最终验证（2026-09-09）：`bash .codex/scripts/task_guard.sh check` 通过；`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --check` 通过。
- 状态：点击行为修复和定向验证完成；待原子提交与恢复 tag。
