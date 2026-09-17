# TweakCN 风格主题配色系统设计

> 状态：设计阶段，尚未实施
> 设计日期：2026-09-07
> 适用范围：Android mobile 优先；共享主题模型可供 leanback/TV 后续消费
> 参考：<https://tweakcn.com/community> 以及用户提供的五张界面截图
> 关联现有能力：`Setting.theme_color`、Material 3 Dynamic Color、应用壁纸、WebHome 主题系统

## Recovery anchor

- 目标：为 WebHTV 设计一个可持久化、可预览、可导入/导出的原生主题配色系统，达到截图所示的“主题配色”编辑体验，同时保持现有首页、详情页、播放页和站源功能可回退。
- 验收标准：设计明确主题数据模型、用户流程、原生颜色消费边界、旧设置迁移、导入/导出安全规则、应用/回滚策略和分阶段验证；未获明确实施批准前不修改运行时代码。
- 当前文件/符号：`app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java`、`AppearanceDialog.java`、`app/src/main/java/com/fongmi/android/tv/setting/Setting.java`、`BaseActivity`、`CustomWallView`、`SiteDialogTheme`。
- 已完成证据：已查看五张参考截图；已核对当前主题色、动态壁纸、Material 3 和刷新事件实现；当前工作树另有预先存在的 `app/src/main/java/com/fongmi/android/tv/ui/helper/TouchOptimizationHelper.java` 脏改动，属于保护路径，不纳入本任务。
- 未验证项：本设计尚未落地，未进行编译、设备交互或视觉验收。
- 下一步唯一动作：用户批准设计后，以本文件为基线启动独立实现任务；实现前先锁定具体阶段和路径范围。

---

## 1. 背景与目标

### 1.1 用户希望解决的问题

参考截图展示的是一个独立的“主题配色”编辑器，主要能力包括：

- 在同一个编辑页面实时预览主题效果；
- 选择壁纸、着色壁纸或纯色背景；
- 选择跟随系统、浅色或深色模式；
- 从预设色板快速选择强调色；
- 编辑高亮色、应用背景色、内容表面色等核心颜色，并直接看到十六进制值；
- 导入主题（浏览 TweakCN 社区、粘贴链接或 JSON、选择 JSON 文件）；
- 导出主题；
- 点击“应用”后，设置页、首页、详情页等原生页面统一换色。

本项目需要的是**原生 Android 应用的设计变量主题系统**，而不是把 TweakCN 网页直接嵌入 WebView，也不是引入一套新的 CSS 运行时。

### 1.2 设计目标

1. 让用户可以在应用内编辑并应用一套可验证的主题配色。
2. 保留现有 `theme_color`、壁纸和 Material 3 动态色行为，升级过程不丢设置。
3. 在 Android 9 等无法依赖系统动态色的设备上，也能让关键原生界面使用自定义颜色。
4. 主题编辑期间只改变临时草稿；点击“应用”后一次性持久化并刷新，点击“取消”不影响当前主题。
5. 支持 JSON 导入/导出，允许后续接入 TweakCN 兼容映射，但不执行远程脚本、不加载任意 CSS。
6. 对文字/背景对比度、导入大小、来源和回滚做确定性校验。
7. 保持播放器内核、解析器、内容源数据和业务权限不受主题影响。

### 1.3 非目标

本阶段不做：

- 不把首页、详情页或播放器整体迁移到 WebView；
- 不让远程主题执行 JavaScript、加载任意插件或修改原生布局；
- 不直接抓取 TweakCN 社区页面并自动执行其 CSS；
- 不在第一版建设带账号、点赞、评论、在线发布能力的主题市场；
- 不重新设计播放器解码、DRM、字幕、音轨和播放状态机；
- 不同步修改 leanback 的全部视觉资源；TV 只先共享数据模型和最低限度消费契约。

---

## 2. 当前实现盘点

### 2.1 已有主题色入口

| 位置 | 当前行为 | 对新设计的影响 |
| --- | --- | --- |
| `Setting.getThemeColor()` / `putThemeColor(int)` | 以整数保存主题色；`-1` 表示关闭，`0` 表示跟随壁纸，其他值为自定义种子色 | 保留作为旧设置兼容字段和快速回退字段 |
| `Setting.getDynamicColor()` | 将主题色解析为 `0`、自定义色或壁纸色 | 可作为新主题的 seed 默认值，但不能承担所有核心 token |
| `ThemeDialog` | 只有一组固定颜色圆点，点击后立即写入设置并触发主题刷新 | 由新的编辑器替代入口；保留旧行为作为迁移/回退 |
| `AppearanceDialog` | “主题色彩”行打开 `ThemeDialog` | 行点击改为打开主题编辑器，摘要显示主题名/背景模式 |
| `BaseActivity.enableDynamicColor()` | 使用 Material 3 `DynamicColors.applyToActivityIfAvailable()` | Android 12+ 可继续使用；低版本需增加应用侧 token 绑定 |
| `SiteDialogTheme` | 使用 Material 1.14.0 `ColorRoles`，不依赖系统动态色支持 | 可复用同一套 token/对比度规则 |
| `CustomWallView` | 读取现有壁纸设置并绘制壁纸/纯色/动效壁纸 | 新系统不直接破坏壁纸设置，使用主题背景层和内容 surface 控制可读性 |
| `RefreshEvent.theme()` | 当前主题改变后重建 Activity | 作为应用主题应用后的统一刷新路径 |
| `Backup.APP_PREFS` | 已备份 `theme_color`、`wall_color` 等字段 | 新增主题配置字段时必须加入备份白名单并兼容旧备份 |

### 2.2 参考截图拆解

| 截图 | 观察到的交互/视觉 | 本设计对应方案 |
| --- | --- | --- |
| `QQ20260907-215135.png` | 主题编辑器、实时预览、背景/明暗分段控件、预设色板、核心颜色列表、取消/应用 | `ThemeEditorDialog` 草稿状态 + `ThemePreviewView` |
| `QQ20260907-215204.png` | 导入弹窗包含浏览社区、粘贴链接或 JSON、选择 JSON 文件 | `ThemeImportDialog`；第一版浏览社区使用系统浏览器，导入走 JSON |
| `QQ20260907-215232.png` | 设置页显示主题摘要和已应用提示 | `AppearanceDialog` 摘要 + 应用后统一刷新提示 |
| `QQ20260907-215313.png` | 首页卡片、分类和底部面板使用同一强调色 | 首页的语义 token 消费和选中态颜色统一 |
| `QQ20260907-215333.png` | 详情页工具栏、线路、选集和正文表面共享强调色/背景色 | 详情动作、标签、选集和正文 surface 接入 token |

---

## 3. 总体方案

### 3.1 核心原则

采用“**主题配置 → 解析为语义 token → 原生页面消费 token**”三层结构：

```text
ThemeProfile JSON / 旧设置
          |
          v
ThemeProfileStore + ThemeProfileValidator
          |
          v
ThemeResolver（模式、seed、显式覆盖、对比度修正）
          |
          v
ThemeTokens（canvas / surface / text / accent / focus ...）
          |
          +--> BaseActivity / ThemeContextBinder
          +--> Home / Detail / Setting semantic resources
          +--> SiteDialogTheme
          +--> WebHome chrome / WebTheme token bridge（只读）
```

主题只描述外观，不描述页面业务、数据源、播放地址或权限。页面继续使用现有原生数据和事件链路。

### 3.2 为什么不直接把 `theme_color` 扩展成多个整数

单独增加多个 `theme_*_color` 偏好值会造成：

- 明色/暗色没有成对数据，系统模式切换时颜色可能不可读；
- 不能表达主题名称、来源、版本和导入信息；
- 导入/导出缺少稳定 schema；
- 新增 token 时需要不断增加独立偏好键；
- 回滚和校验难以做到原子化。

因此使用一个带版本的主题 JSON 作为新数据源，同时保留 `theme_color` 等旧键作为兼容镜像和故障回退。

### 3.3 应用边界

- **设置编辑器**：完整支持主题草稿、预览、导入和导出。
- **原生页面**：第一阶段覆盖设置、首页、站源弹窗、详情页关键表面和动作；播放器仅覆盖控制层可着色区域，不改变视频画面和播放内核。
- **WebHome**：向内置/受信主题提供只读 token 快照；远程主题不能调用写入接口改变原生主题。
- **leanback**：读取同一 `ThemeProfile`，先映射 seed、背景和选中/焦点色；完整 TV 版资源替换作为后续任务。

---

## 4. 主题数据模型

### 4.1 顶层 schema

建议文件格式为 `webhtv-theme/v1`，颜色统一采用不带透明度的 `#RRGGBB`；解析时接受 `#RGB`、`#RRGGBB` 和 `#AARRGGBB`，但应用 token 拒绝非 `FF` alpha，避免透明色污染文字和 surface。

```json
{
  "schemaVersion": 1,
  "format": "webhtv-theme",
  "id": "webhtv.claude-plus",
  "name": "Claude +",
  "author": "optional",
  "source": {
    "type": "local|tweakcn|url",
    "url": "https://example.com/theme.json"
  },
  "mode": "system|light|dark",
  "background": {
    "type": "wallpaper|tinted-wallpaper|solid",
    "color": "#F8FAFC",
    "scrimAlpha": 0.0
  },
  "seedColor": "#155DFC",
  "colors": {
    "light": {
      "primary": "#155DFC",
      "appBackground": "#F8FAFC",
      "surface": "#FFFFFF",
      "surfaceElevated": "#FFFFFF",
      "onSurface": "#182230",
      "onSurfaceVariant": "#526071",
      "outline": "#CBD5E1",
      "focus": "#155DFC",
      "error": "#BA1A1A"
    },
    "dark": {
      "primary": "#A9C7FF",
      "appBackground": "#111827",
      "surface": "#1F2937",
      "surfaceElevated": "#273449",
      "onSurface": "#E5EDF8",
      "onSurfaceVariant": "#B7C4D6",
      "outline": "#455468",
      "focus": "#A9C7FF",
      "error": "#FFB4AB"
    }
  },
  "metadata": {
    "tags": ["blue", "minimal"],
    "previewVersion": 1
  }
}
```

### 4.2 字段语义

- `schemaVersion`：只用于文件解析兼容，不等同于应用版本。
- `id`：稳定主题 ID；导入相同 ID 时通过版本/来源确认是否覆盖。
- `name`：设置摘要和主题编辑器标题使用的可见名称。
- `mode`：`system`、`light`、`dark`，对应截图中的明暗模式分段。
- `background.type`：
  - `wallpaper`：保留当前壁纸，内容表面按 token 叠加；
  - `tinted-wallpaper`：保留壁纸并叠加主题色 scrim；
  - `solid`：使用 `background.color` 的不透明 canvas 覆盖壁纸视觉。
- `seedColor`：Material 角色生成和旧版兼容使用的强调色种子。
- `colors.light/dark`：最终消费的语义色。缺少某个角色时由 `ThemeResolver` 从 `seedColor` 和背景推导。
- `metadata`：只保存展示和兼容信息，不允许嵌入脚本、资源路径或任意 Android 属性。

### 4.3 可编辑核心颜色

第一版编辑器只暴露三个核心色，避免把 Material 角色表变成难以理解的专业配置：

| UI 名称 | token | 用途 |
| --- | --- | --- |
| 高亮色 | `primary` | 选中态、主操作、导航选中、线路/选集当前项、焦点 ring |
| App 背景色 | `appBackground` | 页面 canvas、设置页和列表页底层背景 |
| 内容表面色 | `surface` | 卡片、面板、内容区域和弹窗主体 |

以下颜色自动派生或在高级导入中接受：

- `onSurface`、`onSurfaceVariant`；
- `surfaceElevated`；
- `primaryContainer`、`onPrimaryContainer`；
- `outline`、`focus`、`error`。

如果用户导入完整 token 集，则保留已验证的显式值；如果只提供 seed/三种核心颜色，则补齐缺失角色。

### 4.4 TweakCN/shadcn token 映射

第一版不依赖 TweakCN 的内部 API，只提供通用 CSS token 到 WebHTV token 的离线映射：

| 常见 TweakCN token | WebHTV token |
| --- | --- |
| `--primary` | `primary` |
| `--primary-foreground` | `onPrimary` |
| `--background` | `appBackground` |
| `--card` / `--popover` | `surface` / `surfaceElevated` |
| `--foreground` | `onSurface` |
| `--muted-foreground` | `onSurfaceVariant` |
| `--border` / `--input` | `outline` |
| `--ring` | `focus` |
| `--destructive` | `error` |

未识别 token 忽略并记录导入警告，不影响当前主题。若未来需要直接支持社区主题页面，增加独立适配器，不把网页 CSS 直接当成 Android 主题输入。

---

## 5. 颜色解析与可读性规则

### 5.1 解析顺序

`ThemeResolver` 按以下顺序生成当前运行态 `ThemeTokens`：

1. 读取并校验本地 `ThemeProfile`；
2. 根据 `mode` 确定 light/dark 分支；
3. 以显式 token 为最高优先级；
4. 用 `seedColor` 调用 Material 1.14.0 的 `ColorRoles` 补齐强调色相关角色；
5. 缺失背景/表面时读取当前壁纸色或稳定默认值；
6. 计算 `on*`、outline 和 focus 的对比度；
7. 对不满足最低对比度的自动派生值进行修正；
8. 生成不可变的 `ThemeTokens`，供 Activity 生命周期内使用。

### 5.2 可读性约束

- 正文文字与其背景目标对比度至少 4.5:1；
- 大字号、图标、焦点边框目标对比度至少 3:1；
- 主按钮前景和背景至少 4.5:1；
- 禁止透明核心色；
- 用户显式输入对比度不足时，编辑器显示明确警告并禁止“应用”，而不是静默改变用户颜色；
- 自动派生的 `onSurface` 等角色可以在计算时调整，但预览必须反映最终值；
- TV 焦点态不能仅依赖颜色变化，还应保留边框/形状/亮度差异。

### 5.3 壁纸可读性

壁纸不是 token 的替代品：

- `wallpaper` 模式必须加一层根据当前 token 透明度计算的内容 surface；
- `tinted-wallpaper` 的 scrimAlpha 限制在 `0.0..0.85`；
- `solid` 模式的 canvas 必须完全不透明，确保文字和卡片不会受底层动态图影响；
- 动态壁纸、视频壁纸和 GIF 壁纸不参与主题 JSON 导入，仍由现有 `CustomWallView` 管理。

---

## 6. 用户体验设计

### 6.1 设置入口

保留现有设置层级：

```text
设置 → 外观与语言 → 主题配色
```

点击后打开 `ThemeEditorDialog`（mobile 可使用全屏/大尺寸 Material Dialog；不新建独立 Activity），避免用户离开设置上下文。

设置行摘要建议：

```text
Claude + · 纯色
Claude + · 着色壁纸
默认 · 跟随壁纸
```

应用成功后显示一次轻量提示，并触发现有 `RefreshEvent.theme()`。刷新/重建期间不修改用户当前导航、历史、收藏和播放数据。

### 6.2 编辑器布局

按截图顺序设计：

1. 顶部标题“主题配色”；右侧操作：重置、导入、导出；
2. “实时预览”卡片：应用名、搜索图标、收藏图标、主按钮、次按钮、集数标签；
3. “背景模式”：壁纸 / 着色壁纸 / 纯色；
4. “明暗模式”：跟随系统 / 浅色 / 深色；
5. “高亮色预设”：横向可滚动圆点，保留现有预设色并增加主题 profile 的预设；
6. “核心颜色”：三行可点击的色块、名称和 HEX；
7. 底部“取消 / 应用”。

所有控件最小触摸目标 48dp；预设色横向滚动不抢占核心颜色列表滚动；HEX 文本应支持复制但不要求用户直接编辑文本。

### 6.3 实时预览

预览只使用内存中的 draft `ThemeProfile`：

- 改变模式、背景或颜色时，只重新解析 `ThemeTokens` 并刷新预览；
- 不写 `SharedPreferences`；
- 不重建宿主 Activity；
- 预览覆盖至少：页面背景、卡片、主操作、次操作、焦点/选中态、正文和弱化文字；
- 若 draft 对比度不足，预览顶部显示“不可应用”的原因。

### 6.4 颜色编辑器

颜色编辑器支持：

- 预设色选择；
- HEX 输入（规范化为大写 `#RRGGBB`）；
- Android 原生可访问的色相/饱和度/亮度选择控件（若项目现有组件不足，第一版以 HEX + 预设为主）；
- 显示当前颜色与建议前景色；
- 显示对比度结果；
- 取消时回到编辑器 draft，不影响已应用主题。

### 6.5 导入弹窗

对应截图的三个入口：

1. **浏览 TweakCN 社区**：使用系统浏览器打开社区页面；第一版不在应用内抓取社区页面。
2. **粘贴链接或 JSON**：支持本项目 schema JSON、TweakCN 常见 token JSON；HTTPS 链接下载后经过大小、内容和 schema 校验。
3. **选择 JSON 文件**：使用 Storage Access Framework `ACTION_OPEN_DOCUMENT`，不申请整盘存储权限。

导入流程：

```text
读取 -> 限制大小 -> JSON 解析 -> schema/颜色校验
     -> token 映射 -> 对比度检查 -> 预览导入结果
     -> 用户确认 -> 替换编辑器 draft
```

导入不会直接应用；用户仍需在编辑器点击“应用”。

### 6.6 导出

导出内容为脱敏后的 `webhtv-theme/v1` JSON：

- 通过 `ACTION_CREATE_DOCUMENT` 保存，或调用系统分享；
- 不包含站点 URL、Cookie、播放记录、用户配置和设备信息；
- 保留主题名、颜色、模式、背景模式和来源元数据；
- 导出失败不改变当前主题。

---

## 7. 原生架构设计

### 7.1 建议新增组件

| 组件 | 职责 |
| --- | --- |
| `ThemeProfile` | 不可变主题配置模型；负责 schema 字段，不负责 UI |
| `ThemeProfileCodec` | Gson JSON 读写、版本兼容、旧格式映射 |
| `ThemeProfileValidator` | 字段、颜色、范围、大小和来源校验 |
| `ThemeProfileStore` | 读写当前 profile、last-known-good 和迁移状态 |
| `ThemeResolver` | 根据系统模式、壁纸 seed 和显式 token 生成 `ThemeTokens` |
| `ThemeTokens` | 运行时不可变语义颜色集合 |
| `ThemeController` | 启动时读取主题、应用模式、发出刷新事件 |
| `ThemeEditorDialog` | 编辑器 UI、draft、应用/取消 |
| `ThemeImportDialog` | 社区、链接、文件导入入口 |
| `ThemeColorPickerDialog` | 单个核心颜色编辑和对比度提示 |
| `ThemePreviewView` | 与实际应用无业务耦合的预览组件 |
| `ThemeExport` | SAF 创建文件与系统分享 |
| `ThemeAware`（可选） | 需要在 Android 低版本运行时接收 token 的原生 View/容器契约 |

首版不新增第三方依赖：项目已经使用 Gson 和 Material 1.14.0，优先复用现有实现。

### 7.2 Activity 应用时机

```text
Application.onCreate
    -> 读取并验证 profile
    -> 设置 AppCompat night mode（system/light/dark）

BaseActivity.onCreate
    -> 解析当前 ThemeTokens
    -> API 31+ 继续尝试 Material DynamicColors
    -> setContentView
    -> 对需要低版本运行时换色的语义容器绑定 ThemeTokens
    -> 创建 CustomWallView / 其他内容
```

现有 `RefreshEvent.THEME` 继续作为应用后的统一刷新信号。应用配置时先写入 profile 和兼容字段，再发事件；若写入失败则不发事件。

### 7.3 低版本兼容策略

Material Dynamic Color 的系统入口不能作为 Android 9 等设备的唯一方案。建议分两层：

1. **资源层**：把新增/维护中的原生布局从固定白/黑色迁移到语义属性或语义 color selector，例如 `colorPrimary`、`colorSurface`、`colorOnSurface`、`colorOutline`。
2. **运行时层**：对无法通过主题属性解析、但必须支持自定义色的页面容器实现 `ThemeAware`，从 `ThemeTokens` 设置背景、文字、按钮、图标 tint 和状态列表。

以下区域继续保持独立，不强行套用普通页面主题：

- 视频内容本身；
- 播放器上需要保证可见性的黑色渐变和控制层；
- 健康状态点、错误/警告等语义色；
- 站点内容图片和源数据中的颜色。

### 7.4 页面消费优先级

第一阶段按以下顺序替换：

1. 主题编辑器预览和设置页；
2. 首页导航、分类、选中卡片、底部面板和站源弹窗；
3. 详情页顶部操作、线路/选集、正文 surface；
4. 播放器控制层的强调色和焦点色；
5. 其他低风险原生弹窗和列表。

每个页面都必须保留旧资源回退，不允许主题失败导致页面不可进入。

---

## 8. 持久化、迁移与回滚

### 8.1 新旧设置关系

新增建议键：

- `theme_profile_json`：当前完整 profile；
- `theme_profile_last_good`：最近一次验证通过且成功应用的 profile；
- `theme_profile_schema`：可选，仅用于快速判断迁移版本。

现有键继续保留：

- `theme_color`：写入 profile 的 `seedColor` 镜像；
- `wall_color`、`wall`、`wall_type`：继续由现有壁纸系统管理；主题 profile 只记录编辑器选择的背景模式和必要覆盖。

如果主题使用 `solid` 背景，不应直接删除用户现有壁纸；切回 `wallpaper` 时恢复原壁纸设置。

### 8.2 首次迁移

当 `theme_profile_json` 不存在时：

- `theme_color == -1`：生成默认 profile，`seedColor` 为空，模式跟随系统；
- `theme_color == 0`：生成 seed 来源为壁纸的 profile；
- `theme_color > 0`：生成对应 seed 的 profile；
- 当前 `wall/wall_type` 只用于解析预览背景，不自动改变已有壁纸；
- 不在启动时立即触发第二次重建，只在用户打开/应用编辑器或下一次正常 Activity 创建时完成迁移。

### 8.3 应用事务

应用按钮按以下顺序执行：

```text
validate(draft)
  -> serialize(draft)
  -> write theme_profile_json atomically
  -> update legacy mirrors
  -> write last_good
  -> publish RefreshEvent.theme()
```

任何一步失败：

- 保留旧 profile；
- 不更新兼容镜像；
- 不刷新页面；
- 向用户显示“应用失败，当前主题未改变”。

### 8.4 回滚

- 编辑器“重置”：只将 draft 恢复为内置默认 profile，等待用户点击应用；
- 设置中的“恢复默认”：删除当前 profile，恢复 `theme_color=-1`，模式跟随系统，保留用户壁纸；
- profile JSON 损坏：读取 `theme_profile_last_good`；若也损坏，回退旧 `theme_color` + 系统默认色；
- 任何导入主题都必须先进入 draft，不允许远程输入直接覆盖 last-good；
- 回滚不删除导出文件，不修改备份中的其他偏好。

### 8.5 备份兼容

新增 profile 键加入 `Backup.APP_PREFS`。恢复旧备份时缺少 profile 字段则走迁移逻辑；恢复新备份到旧版本时，旧版本忽略未知键并继续使用 `theme_color` 镜像。

---

## 9. 安全设计

### 9.1 本地 JSON

- 最大大小：建议 256 KiB；
- 最大嵌套深度：建议 8；
- 只接受对象，不执行字符串中的代码；
- 颜色、枚举、数值范围严格校验；
- 忽略未知字段，拒绝危险/模糊字段（脚本、资源路径、Intent、类名、任意 CSS）；
- `source.url` 只作为展示元数据保存，不在离线导入时自动访问。

### 9.2 HTTPS 链接

- 只接受 `https://`；拒绝 HTTP、文件 URI、私有 IP、环回地址和本地路径；
- 首次访问显示域名并要求用户确认；
- 使用现有网络栈，设置连接/读取超时和响应上限；
- 不发送 Cookie、Authorization、站点请求头或用户配置；
- 不允许重定向到非 HTTPS、私网或不同来源而不重新确认；
- 下载完成后按本地 JSON 相同规则校验；
- 网络失败只影响导入，不影响现有主题。

### 9.3 TweakCN 社区

“浏览 TweakCN 社区”第一版只是打开用户可见的系统浏览器页面。应用不保存登录态、不注入脚本、不自动抓取页面、不把社区 CSS 当作 Android 样式执行。

如果后续提供内置主题索引，必须采用静态 JSON + 预览图 + schema 校验，增加签名/哈希、版本、来源和回滚机制后才能进入应用内主题列表。

---

## 10. 分阶段实施计划

### 阶段 A：模型与迁移（建议先做）

范围：

- `ThemeProfile`、`ThemeTokens`、codec、validator、store、resolver；
- 旧 `theme_color` 迁移；
- `Backup.APP_PREFS` 新字段；
- 无 UI 大改，现有 `ThemeDialog` 仍可工作；
- 单元测试覆盖 JSON、颜色规范化、迁移、对比度和回滚。

完成标志：任何旧安装启动后都能得到有效 profile；无 profile/corrupt profile 不阻塞启动。

### 阶段 B：主题编辑器与实时预览

范围：

- `AppearanceDialog` 入口替换；
- `ThemeEditorDialog`、预览、背景/明暗分段、预设色和三个核心颜色；
- 取消/应用/重置；
- 颜色编辑器和对比度提示。

完成标志：不点击应用不会改变当前界面；应用后主题摘要和关键页面发生预期变化。

### 阶段 C：原生页面 token 接入

范围：

- BaseActivity / ThemeController 应用模式；
- 设置、首页、站源弹窗、详情关键 surface、播放器控制层逐步迁移到 semantic tokens；
- 低版本 `ThemeAware` 绑定；
- 保留现有 `SiteDialogTheme` 的对比度行为并统一 token 来源。

完成标志：Android 9+ 在不依赖平台 Dynamic Color 的情况下，至少设置/首页/详情关键控件能切换明暗和强调色；失败页面仍能回退。

### 阶段 D：导入/导出与 TweakCN 映射

范围：

- 浏览社区、粘贴 JSON/HTTPS 链接、SAF 文件导入；
- TweakCN/shadcn 常见 token 映射；
- SAF 导出/系统分享；
- 导入预览、警告和失败回滚。

完成标志：导出的文件可在另一台安装同版本应用中导入；不支持的 token 不会导致崩溃或执行代码。

### 阶段 E：TV 与社区索引（后续）

范围：

- leanback 焦点和遥控交互；
- TV 语义资源完整迁移；
- 受签名/哈希保护的静态主题索引和预览图；
- 主题版本回滚和缓存。

不与阶段 A-D 混合实施。

---

## 11. 验证计划

### 11.1 单元/源代码测试

- `ThemeProfileCodecTest`：合法、缺字段、未知字段、版本号、颜色格式；
- `ThemeProfileValidatorTest`：透明色、范围、枚举、大小和对比度；
- `ThemeProfileMigrationTest`：`theme_color=-1/0/自定义`、旧备份和损坏 profile；
- `ThemeResolverTest`：system/light/dark、显式覆盖、seed 补全和壁纸模式；
- `ThemeTweakCnAdapterTest`：常见 CSS token 映射及未知 token；
- `ThemeStoreRollbackTest`：写入失败和 last-good 回滚；
- `Backup` 源码契约测试：新偏好键包含在备份集合中。

### 11.2 Android 编译验证

最小验证顺序：

1. `:app:compileMobileArm64_v8aDebugJavaWithJavac`；
2. `:app:processMobileArm64_v8aDebugResources`；
3. `:app:assembleMobileArm64_v8aDebug`；
4. 若修改 TV 共享模型，再运行对应 leanback Java 编译/assemble。

不因主题 UI 任务运行全 ABI 矩阵，除非共享代码或资源触及所有变体。

### 11.3 设备场景

至少验证：

- Android 9：默认主题、蓝色主题、暗色主题、纯色/壁纸主题；
- Android 12+：系统动态色与应用显式 profile 的优先级；
- 打开编辑器修改但取消，当前页面颜色不变；
- 应用主题后设置、首页、站源弹窗、详情页能进入且颜色一致；
- 应用重启后 profile 不丢失；
- 导入无效 JSON、超大 JSON、低对比度颜色不改变当前主题；
- 导出后删除当前 profile，再导入导出文件恢复；
- 播放页进入、暂停、切换线路、返回均不受主题系统影响；
- 动态壁纸刷新和主题切换不泄漏播放器/壁纸资源；
- 断网时打开主题编辑器仍可编辑本地主题，只有链接导入不可用。

### 11.4 视觉验收

以用户提供的截图为验收参考，不要求像素级复制，但应满足：

- 编辑器信息层级、分段控件、色板、颜色行和底部操作可辨识；
- 预览中的主色、surface、正文和选中态同步变化；
- 主题应用后设置摘要、首页和详情页的强调色一致；
- 深浅模式下正文、按钮、焦点态均满足对比度规则；
- 窄屏手机滚动、输入法、返回键和系统字体放大不裁切关键操作。

---

## 12. 风险、取舍与回滚策略

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 当前大量布局使用固定白/黑色 | 低版本无法全局换色 | 先迁移关键页面到语义 token；播放器和特殊遮罩保留专用资源 |
| Material Dynamic Color 在低版本不可用 | Android 9 主题色不生效 | 使用 `ThemeTokens` + `ThemeAware`，不把系统动态色当唯一实现 |
| 自定义颜色对比度不足 | 文字不可读 | 应用前验证；自动角色派生；编辑器显示对比度 |
| 壁纸动效与文字冲突 | 闪烁/不可读 | scrim 和 opaque solid canvas；保留现有壁纸生命周期 |
| 旧设置与新 profile 不一致 | 升级后主题变化 | 单向迁移、兼容镜像、last-good 和原子写入 |
| 导入远程 JSON 不可信 | 资源消耗或恶意输入 | HTTPS、来源确认、大小/范围/schema 校验，不执行脚本 |
| 全量 Activity 重建造成闪白 | 用户体验下降 | 草稿只更新预览；应用一次重建；启动读取已验证缓存 |
| TV 资源未同步 | 电视端显示不一致 | 第一阶段 TV 只消费共享模型和最低 token，完整迁移单独排期 |
| 主题改动影响播放层 | 播放回归 | 明确播放器内核非目标；播放场景单独回归 |

回滚方式：撤销本任务对应实现提交即可；数据层保留 `theme_color` 和旧壁纸键，不要求数据库迁移。若实现阶段发现全局 token 改造范围超出批准边界，应先回滚到模型/编辑器独立阶段，不把半套全局颜色接入留在主分支。

---

## 13. 建议与审批点

### 建议

建议采用 **A → B → C → D → E** 的顺序，但将 A/B 作为第一期可独立验收单元：先建立可迁移、可验证的 profile 和编辑器，再逐步扩大原生页面消费范围。第一期不直接建设在线主题市场，也不直接抓取 TweakCN 页面。

### 需要用户确认的范围

1. 是否先只实施 mobile，还是 A/B 阶段同时制作 leanback 编辑器？
2. 是否接受“第一版只支持 TweakCN 常见 JSON/CSS token 映射，社区浏览通过系统浏览器完成”，而不是在应用内直接展示社区列表？
3. 是否接受现有播放器画面/特殊黑色遮罩不参与普通页面主题换色？
4. 是否批准先实施阶段 A+B，完成后再单独审批阶段 C 的全局原生页面接入？

在以上边界未确认前，本文件只作为设计基线，不修改生产代码、资源、锁文件或运行时行为。
