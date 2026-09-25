# 手机版站点选择主题配色修复

## 目标与边界

- 修复主题色彩在站点选择弹窗、搜索框、站点项及分组项不生效。
- 手选色、跟随壁纸（由 `Setting.getDynamicColor()` 解析）、关闭自定义色均保留；兼容当前 Android 9 设备与明暗主题。
- 仅修改 mobile 站点 UI 与针对性测试；不改全局主题、电视版、依赖、站点数据或播放逻辑。
- `quick-fix`；任务 `mobile-site-theme-20260906`；基线 `dev1` / `f627d99e88078ccd8760986cbae65cc4a3eb5adc`。
- 保护初始脏路径 `gradlew`。

## 原因与方案

1. `adapter_site.xml` 使用 main 中固定蓝白 `site_button_*`，`SiteGroupAdapter` 使用固定浅色 `dialog_outlined_button_*`；搜索框文字和图标也未接入配色。
2. `BaseActivity.enableDynamicColor()` 调用的 Material 1.14.0 `DynamicColors.applyToActivityIfAvailable()` 有平台可用性门槛；当前设备 SDK 28 会直接返回。
3. 旧 `SiteDialog.applyDynamicColor()` 只将原始色涂到底板，既不改变控件，也不保证背景/文字对比度。

不新增 EventBus 订阅：现有主题事件已重建宿主，弹窗创建时读取当前设置即可。
新增 mobile 专用 `SiteDialogTheme`，每个弹窗生成一次调色板并共享给两个 Adapter。
使用已有 Material `getColorRoles(context, seed)` 和 `getSurfaceContainerFromSeed(context, seed)`，不依赖系统动态色支持；无 seed 时读取当前主题属性。
站点与分组统一 selected/focused/pressed/default 状态色，选中与交互前景/背景成对；保留禁用站点文字的不透明度。
健康状态点继续使用原来的语义色，搜索/换源/屏蔽/列数/筛选/拖动排序逻辑不变。

## 依据（2026-09-06）

- 项目实际依赖：`gradle/libs.versions.toml` 的 Material 1.14.0；本地 Maven 缓存对应官方 AAR。
- 用 `javap` 核实上述三个 API 的实际字节码：动态色入口先检查平台支持；ColorRoles 依主题明暗生成配对色；surface 采用低色度、浅色 tone 94 / 深色 tone 12。
- 本地源码：`Setting.getDynamicColor()`、mobile `BaseActivity.enableDynamicColor()`、`SiteDialog.initView()`、两个站点 Adapter 及对应布局。
- 外部官方页面读取尝试因 TLS 失败未取得正文；实现以当前锁定版本实际依赖为据，不据此宣称做过全面设计调研。

## 验证与收尾

- **手机版编译通过**：`:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:processMobileArm64_v8aDebugResources`，覆盖修改后的类、调用方、ViewBinding 与两个布局。首次编译发现 `colorPrimary` 属于 AppCompat 的非传递 R 命名空间，修正后编译通过。
- **5 项针对性 JUnit 测试通过**：`SiteDialogThemeSourceTest`（4 项）与 `MobileSiteAdapterStyleTest`（1 项）。
  - 原 Gradle 单测任务在生产代码和资源编译后，被离线缺少 JUnit 4.13.2 / MockWebServer 5.4.0 缓存阻断。
  - 从 Maven Central 获取项目已指定版本 JUnit 4.13.2 与 Hamcrest 1.3，直接用 JDK 21 `javac` + `org.junit.runner.JUnitCore` 执行上述两类；没有替换断言、跳过测试或修改项目依赖。完整 Gradle 单测任务未宣称通过。
- **Android 9 / SDK 28 控件运行验证通过**：独立测试包直接链接当前 `SiteDialogTheme.java` 源文件及相同 Material 1.14.0 依赖；不覆盖已安装的 WebHTV，不读写其设置。
  - 实际输出：`PASS sdk=28 dynamicAvailable=false palettes=28 checks=1168 minTextContrast=6.425314122550928`。
  - 覆盖明/暗主题 ×（关闭自定义色、12 个预设色、1 个壁纸颜色输入）；真实 MaterialButton 的颜色状态列表、实际选中/禁用文字状态，以及搜索文字、提示和图标着色。
  - 每组检查站点和分组的 normal / selected / focused / pressed / disabled / selected+focused 配色；成对前景/背景对比度最低约 6.43:1（不含现有非选中分组的视图透明度效果）。
  - 示例：蓝色 `FF1E88E5` 的浅色底板/强调色为 `FFEDEDF4` / `FF0060A8`；绿色 `FF43A047` 为 `FFECEFE6` / `FF006E1C`，证明更换颜色会实际改变配色，而非只更新设置值。
  - 测试包 `com.silent.android.webhtv.sitethemeprobe` 已卸载；此次没有安装新的整包 WebHTV，也没有进行整页手动操作验收。
- **静态差异审查**：搜索、分组筛选、站点及分组拖动排序、屏蔽、单/双列、搜索/换源开关与健康状态点逻辑未变；主题变化仍沿用现有宿主重建机制。
- 日志：`/tmp/webhtv-site-theme/gradle-unit-fixed.log`、`junit-focused.log`、`probe-device-result.log`、`probe-cleanup.log`。独立测试包及构建材料仅在 `/tmp/webhtv-site-theme/probe/`，不纳入项目。
- 本任务代码、测试和本文件由一次 `task_guard.sh finish` 原子提交并创建带注释本地恢复标签；不推送。

回滚：撤销本任务提交即可，不涉及数据迁移或设置格式变更。

## Recovery anchor

- 状态：局部修复、手机版编译、5 项回归测试和 SDK 28 配色控件验证均已完成。
- 当前文件：mobile SiteDialog / SiteAdapter / SiteGroupAdapter / SiteDialogTheme、两个站点布局、两个针对性测试、本文件。
- 未验证范围：未替换手机现有整包，未进行整页手动操作验收；不影响本次已验证的编译与配色契约。
- 唯一下一步：由任务守卫完成本地原子提交和恢复标签；完成后无剩余代码工作，整包安装或发布按后续授权处理。
