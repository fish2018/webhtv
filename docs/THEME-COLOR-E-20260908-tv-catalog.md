# THEME-COLOR-E-20260908：TV 主题焦点与本地 catalog

> Recovery anchor：目标是让 leanback 的核心焦点/选中状态消费同一份语义主题色，并让内置主题索引经过版本、SHA-256、current/last-good 校验后可安全读取；验收为主题测试、leanback Java 编译和资源处理通过。当前阶段在 `dev1`、基线 `cb7229964f77cd43c68157111dae639d3de88531`，已启动独立 task guard；下一步是完成实现后运行一次定向验证。

## 1. 范围与决策

本阶段只处理：

- leanback 核心列表的焦点、选中/激活背景和文本/图标语义色；
- 共享 `ThemeController` 对 TV 视图树的低 API 运行时绑定；
- 内置静态主题 catalog、预览资源、版本和 profile SHA-256 校验；
- catalog 的 SharedPreferences current/cache、last-good 和损坏回滚。

本阶段不处理：

- 播放器内核、视频画面、播放器黑幕或字幕颜色；
- 所有历史 TV 硬编码颜色的全量重构；
- TV 主题编辑器、在线社区请求、远程 CSS/脚本/字体执行；
- 新增依赖、数据库迁移或公共网络接口。

### 1.1 方案比较

| 方案 | 结论 | 原因 |
| --- | --- | --- |
| 不变更 | 拒绝 | TV 仍固定白色/黄色/蓝色，无法消费阶段 A-D 的 profile；没有 catalog 的完整性和回滚契约。 |
| 直接把所有 leanback XML 重写成动态资源 | 拒绝 | 范围大、难以区分 `selected` 跑马灯状态与实际激活状态，容易改变播放器和既有遥控交互。 |
| 不加适配地执行远程主题 CSS/索引 | 拒绝 | 引入代码/资源执行面和网络版本漂移，不符合离线、可回滚和无远程执行约束。 |
| **窄化的 TV runtime binder + 静态 catalog/cache** | **采用** | 只迁移核心 selector 的语义 token，并在 Activity 树完成后绑定；catalog 只接受应用内 asset，profile 先校验 schema 和 SHA-256，再以 current/last-good 原子偏好写入。 |

### 1.2 接受标准

1. `selector_item`、`selector_item_round`、`selector_site_item`、`selector_group_button` 和 `selector_video_item` 保留 `focused`、`selected`/`activated` 的状态顺序，状态颜色来自可替换的语义属性或运行时绑定；选集的 `selected` 不被误认为当前播放。
2. TV `BaseActivity` 在动态子树创建前后都沿用 `ThemeController.apply`，运行时只作用于普通语义控件，不进入显式播放器根节点。
3. `assets/themes/index.json` 至少包含一个版本化条目和预览 asset；每个 profile 的 UTF-8 原文 SHA-256 与索引声明一致才可激活。
4. 索引 schema、catalog version、条目 ID/版本、asset 路径、digest 和主题 profile 均有边界校验；无效 asset、digest 不匹配、损坏缓存不会覆盖 last-good。
5. 新 catalog 版本激活前保留旧 current 为 last-good；读取失败时优先返回经过 digest/结构校验的 last-good；没有可用缓存时返回明确失败，不伪造主题。
6. 验证只覆盖主题相关 JVM/source-contract 测试、leanback Java 编译和 leanback 资源处理；不运行全 ABI 矩阵。

### 1.3 回滚路径

撤销本阶段单一提交即可回到阶段 D 的 `cb7229964f77cd43c68157111dae639d3de88531`。profile schema、`theme_profile_json`、旧 `theme_color` 和播放器控制隔离不变；catalog 使用独立偏好键，删除或忽略这些键不会影响已应用主题。

## 2. 设计研究与证据

访问日期：2026-09-08（Asia/Shanghai）。

| 来源 | 证据等级 | 支持的结论 | 对 WebHTV 的影响与限制 |
| --- | --- | --- | --- |
| Android Developers，Drawable resources：`https://developer.android.com/guide/topics/resources/drawable-resource` | A | XML selector 由一个 `selector` 和多个 `item` 组成；支持 `state_pressed`、`state_focused`、`state_selected`，且 state item 的匹配顺序重要。 | 保留 TV selector 的显式状态顺序；把焦点 ring 与选中填充分开。页面没有定义 WebHTV 的业务状态，因此 `activated` 仍由本地 adapter 语义决定。 |
| Android Developers，`ColorStateList` API：`https://developer.android.com/reference/android/content/res/ColorStateList` | A | ColorStateList 是 state-spec/color 对，按声明顺序匹配；无状态项通常作为默认项。 | 运行时为文本和图标构造 focused/selected/default 状态列表；默认项必须放最后，避免吞掉焦点状态。 |
| Android Developers，Material Design for Android：`https://developer.android.com/develop/ui/views/theming/look-and-feel` | A | Material-based theme 为标准控件提供统一样式和主题属性。 | 保留已有 Material `?attr/colorPrimary` 等路径；对非 Material 的 leanback 自绘 selector 使用最小 runtime binder，而不是重建整套主题。 |
| Android Developers，`AssetManager` API：`https://developer.android.com/reference/android/content/res/AssetManager` | A | `open` 读取应用 assets，`list` 枚举相对 asset 路径；支持 streaming 访问。 | catalog/profile/preview 走应用内 asset，使用 UTF-8 有界读取；不从网络或任意外部路径加载。 |
| Oracle Java SE 21，`MessageDigest`：`https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html` | A | `MessageDigest` 提供 SHA-256 等单向摘要，`digest` 完成摘要；SHA-256 是标准算法名。 | 对 profile 原始 UTF-8 bytes 计算 64 位十六进制 digest，并在激活前比较；不把 digest 当作签名或来源认证。 |
| WebHTV `app/src/main/java/com/fongmi/android/tv/web/WebThemeManifestLoader.java` | A（本地） | 已有有界读取、current/previous、revision、last-known-good 和 rollback 状态。 | 复用其状态思路，但 catalog 是本地静态 asset，不复制网络刷新、ETag、DNS 或线程模型。 |
| WebHTV `app/src/main/java/com/fongmi/android/tv/remote/RemoteTokens.java`、`app/src/main/java/com/fongmi/android/tv/ad/audio/ProbeRuleStore.java` | A（本地） | 已有 SHA-256 和 `MessageDigest.isEqual` 的项目实现模式。 | 采用相同的 digest 编码/比较原则，避免引入第三方校验库。 |

### 2.1 未采用的推断

SHA-256 只能检测内容变化或缓存损坏，不能证明静态索引的发布者身份。因此本阶段把索引限定为 APK 内置资源，不宣称“签名社区目录”；若未来允许远程 catalog，必须另开阶段引入签名密钥、密钥轮换和发布策略。

## 3. 接口与不变量

- `ThemeCatalog.load(Context)`：只打开 `themes/index.json`，解析并校验条目和 profile；任何失败都不得写入新的 last-good。
- `ThemeCatalog.sha256(String)`：对 UTF-8 原文返回小写 64 字符 hex；digest 比较使用恒定时间等价比较。
- `ThemeCatalogStore`：current、last-good 和 catalog version 使用独立 key；写入失败保留旧值；缓存 envelope 本身也带 digest。
- catalog `schemaVersion` 只接受当前实现版本；catalog `version` 和 entry `version` 必须为正数且有上限；同版本不同内容不静默覆盖已验证 current。
- 主题 profile 仍必须交给 `ThemeProfileCodec.parse`，不能只凭索引字段激活。
- TV binder 对 `playerControlRoot` / `detailControlHost` 维持现有隔离；视频画面和黑幕不受普通 surface 主题覆盖。

## 4. 实施记录

### 2026-09-08

- 状态：设计研究完成，已启动 `THEME-COLOR-E-20260908` guard；待实现和验证。
- 研究结论：采用最小 TV selector 迁移和本地静态 catalog/cache，不修改 TV 主题编辑入口，不接入远程社区索引。
- 下一步：实现 `ThemeCatalog`/`ThemeCatalogStore`、核心 leanback selector 语义色和对应测试，然后执行一次定向验证。
