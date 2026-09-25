# THEME-COLOR-D-COMPLETE-20260908：主题导入导出闭环

## Recovery anchor

- 目标：补齐主题系统阶段 D，使 JSON/TweakCN/HTTPS/SAF 导入、预览、警告、SAF 导出和系统分享形成可回滚闭环；不执行远程脚本、不加载任意 CSS，不改变播放器行为。
- 验收标准：WebHTV profile 可导出并在同版本应用中重新导入；TweakCN registry JSON 的 `cssVars.light/dark` 和常见 token 可映射；HTTPS 输入只允许安全 HTTPS、响应体受 256 KiB 限制且不自动跟随跳转；SAF 文件读取和导出不绕过系统文件选择器；解析失败或取消导入不改变当前 profile；未支持 token 只生成警告，不崩溃、不执行代码。
- 当前文件/符号：`ThemeTweakCnAdapter`、`ThemeTransfer`、`ThemeImportDialog`、`ThemeEditorDialog`、`ThemeExport`、`ThemeProfileCodec/Validator`。
- 已完成证据：阶段 A+B/C 已由前序恢复 tag 固化；阶段 D 原提交只包含底层 token 常量、适配器、传输边界和导出 Intent，本任务已补全 UI 与 draft-only 流程。
- 已完成验证：主题相关定向测试 25 项全部通过；mobile Java 编译和资源处理通过；无设备交互验收。
- 下一步唯一动作：执行 `task_guard.sh finish` 原子提交并创建恢复 tag；阶段 E 另开独立 guard。

## 1. 决策问题与证据

本阶段只回答“如何在 Android 应用内安全完成主题文件/链接导入导出”，不建设在线主题市场，也不执行 TweakCN CSS。

| 证据 | 访问日期 | 级别 | 支持的结论 | 对 WebHTV 的影响 |
|---|---|---:|---|---|
| Android Developers：<https://developer.android.com/training/data-storage/shared/documents-files> | 2026-09-08 | A | 用户选择的文档应通过 Storage Access Framework 读写；应用使用 `ContentResolver` 消费 URI，不依赖真实文件路径。 | 使用 `ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT`，通过 `openInputStream`/`openOutputStream` 读取写入，避免路径转换和存储权限耦合。 |
| Android API：<https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT> | 2026-09-08 | A | `ACTION_OPEN_DOCUMENT` 返回用户授权的可读文档 URI。 | 导入 launcher 使用 `OpenDocument`，MIME 允许 JSON 和纯文本。 |
| Android API：<https://developer.android.com/reference/android/content/Intent#ACTION_CREATE_DOCUMENT> | 2026-09-08 | A | `ACTION_CREATE_DOCUMENT` 让用户选择新文件的保存位置。 | 导出不自行写公共存储目录；由系统文件选择器决定目标。 |
| Android Developers：<https://developer.android.com/training/sharing/send> | 2026-09-08 | A | 文本分享使用 `ACTION_SEND`，并用 chooser 交给系统接收器选择。 | 分享 canonical JSON 文本，不授予任意内部文件 URI。 |
| OkHttp 官方源码/调用约定：<https://github.com/square/okhttp> | 2026-09-08 | A | `ResponseBody` 需要关闭；响应体可能没有可靠的 `Content-Length`，读取时仍需自行限流。 | 关闭 response/body，先检查已知长度，再用计数流限制未知长度；禁用自动重定向。 |
| TweakCN 官方 registry JSON：<https://tweakcn.com/r/themes/twitter.json>；官方仓库 HEAD `a3b47b37cba97dd637de517aab52c45ec0f83456` | 2026-09-08 | A | 真实 registry style 使用 `cssVars.light`/`cssVars.dark`，颜色常用 `oklch(...)`，同时包含 CSS/font/radius 等非颜色字段。 | 只读取 allowlist 颜色 token，支持 light/dark 和 Oklch 转换；忽略 CSS、字体、圆角和未知字段并显示警告。 |

## 2. 方案比较

### 不变更
保留现状，只导入 WebHTV profile 的底层 codec。优点是风险最低；缺点是用户无法完成设计文档承诺的 TweakCN/SAF/HTTPS 导入导出，阶段 D 不成立。拒绝。

### 不加适配的上游方式
直接加载 TweakCN CSS 或 WebView，并执行 registry 中的 CSS。优点是覆盖字段多；缺点是引入脚本/CSS 执行面、远程资源和版本漂移，无法保证 Android 原生控件或离线回滚。拒绝。

### WebHTV 窄适配（采用）
导入面只接受 JSON；识别 WebHTV profile 或 TweakCN allowlist token，支持 `cssVars.light/dark`、扁平 token 和 Oklch 颜色；HTTPS 只下载一次明确的 HTTPS 响应，拒绝非 HTTPS、跳转、私有/特殊 host 和超限响应；SAF 负责本地读写；导入只替换编辑器 draft，用户确认应用后才调用现有 `ThemeProfileStore.apply`。优点是功能可用、无代码执行、失败自动保留 last-good；代价是只覆盖原生主题语义色，不支持字体/圆角/CSS 动画。

## 3. 接口与不变量

- `ThemeTweakCnAdapter.parse(String)`：纯函数式、allowlist 映射；未知 token 进入 warnings；没有任何支持颜色时失败。
- `ThemeTransfer.fetch(String)`：只接受 `ThemeTransfer.isHttps`；不自动跟随跳转；读取上限为 `ThemeProfileValidator.MAX_JSON_BYTES`；返回原始 JSON，仍必须经过适配器/codec 校验。
- `ThemeImportDialog`：网络/文件读取在 `Task` 后台执行，结果回到主线程；预览成功不写偏好；取消、读取失败、解析失败均保持当前 profile。
- `ThemeExport`：SAF 输出 canonical WebHTV JSON；系统分享只发送文本。
- 所有进入 `ThemeProfileStore.apply` 的 profile 仍经过现有 validator、原子 commit 和 last-good 逻辑。

## 4. 验收与回滚

### 自动验收

1. `ThemeTweakCnAdapterTest` 验证扁平 token、真实 `cssVars.light/dark`、Oklch、未知字段警告和无支持 token 失败。
2. `ThemeTransferTest` 验证 HTTPS 边界、URI host 和读取大小限制。
3. `ThemeImportSourceTest` 验证 SAF/HTTPS/chooser/草稿边界的源代码契约。
4. 运行主题定向 JVM 测试；运行 mobile Java 编译和 mobile 资源处理。
5. `ThemeProfileCodec.encode` 输出再次交给 import adapter，验证同版本导出→导入。

### 人工验收

- 编辑器中导入 JSON 后可预览，点击取消不会改变已应用主题；点击导入只替换草稿，点击应用后才刷新。
- SAF 文件导入、SAF 文件导出、系统分享均交给 Android 系统 UI。
- 断网时本地 JSON 仍可导入；HTTPS 失败只显示错误，不覆盖当前主题。

### 回滚

撤销本阶段单一提交即可回到阶段 D 原提交；其后的主题 profile 数据格式不变，现有 `theme_profile_json`/`theme_profile_last_good` 和旧 `theme_color` 镜像继续可读。若网络导入出现风险，删除/禁用 `ThemeTransfer.fetch` 调用不影响本地 JSON、SAF 或已应用主题。

## 5. 实施记录

### 2026-09-08

- 状态：已实施，待 task guard 提交收口。
- 前序 `0316262eeb70cd1831fa686012c2304dc6da4b98` 的阶段 D 提交已审计为底层能力，不足以满足 UI/回滚验收；本任务以独立 guard 补全，不修改该提交。
- 实现：补齐 `ThemeColorUtil` CSS 色值转换、`ThemeTransfer` 安全 HTTPS/大小限制、`ThemeTweakCnAdapter` registry 层级与 warning、`ThemeImportDialog`、编辑器导入/导出/分享接线、字符串、布局和测试。
- 验证：25 个主题单测/源代码契约全部通过；`compileMobileArm64_v8aDebugJavaWithJavac` 和 `processMobileArm64_v8aDebugResources` 通过。
