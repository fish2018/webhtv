# WebHome Devkit

`webhome-devkit/` 是 WebHome 开发套件，统一放置完整开发文档、WebHome 首页示例、WebHome 扩展示例、起步模板和 AI skills。它不是 App 运行时的 `ext` 配置目录，也不等同于 CSP 站点字段里的 `ext`，因此放入主项目后使用 `webhome-devkit` 命名，避免和配置字段混淆。

本文主体是 WebHome 扩展脚本开发指南。

本文档面向两类读者：

- 人工开发者：拿到一个真实网站 URL 后，开发 WebHome 注入脚本，让网页调用 App 原生播放、网盘、嗅探、网络和缓存能力。
- AI 开发助手：读取本文档和目标 URL 后，按第 16 章协议分析页面、生成脚本、给出配置方式和测试步骤。

使用方式：把本文档（必要时连同 `templates/extensions/` 目录）和目标网站 URL 一起交给 AI，即可直接产出可用的扩展脚本。本文档自包含扩展开发所需的全部 API 与规则；WebHome 单文件主页开发、网盘检测细节、性能改造长文见 `docs/应用完整开发文档.md`（下称"主文档"），本文按需交叉引用。若从主项目根目录访问，路径是 `webhome-devkit/docs/应用完整开发文档.md`。

WebHome 扩展脚本的主流场景不是把网站完全改写成 CSP 爬虫，而是在 App WebView 里加载真实网站，然后通过注入脚本增强这个网站。例如：拦截资源按钮、把网盘/磁力/直链交给 App 播放、给页面增加"App 播放"按钮、清理干扰 UI、嗅探页面运行时出现的媒体地址、按手机/电视形态重排版面。

## 目录结构

- `examples/extensions/<site>/`：站点扩展示例，每个站点独立放 JS 和 manifest。
- `examples/homepages/`：单文件 WebHome 首页示例，例如 `nostr.html`。
- `templates/extensions/`：扩展脚本模板和调试辅助脚本。
- `templates/homepages/`：首页起步模板；完整首页范式以 `examples/homepages/` 和 skill demo 为准。
- `skills/`：Codex/Claude/OpenCode 等客户端可安装的 WebHome skills。
- `docs/`：长文档和跨主题说明。

## 1. 能力模型与架构

WebHome = 一个真实网页 + App 注入的 `fm` SDK + 用户/配置注入的扩展脚本。

注入管线（对应 Native 实现 `HomeWebController` / `WebHomeExtensionRegistry` / `WebHomeExtension`）：

```text
站点配置(homePage) ──► WebView 加载真实网页
                          │
配置三层扩展来源 ──► Registry 解析/匹配/排序/依赖检查 ──► ready 列表
                          │
        document-start: SDK + start 扩展（WebView 支持时提前注入）
        onPageFinished: SDK 注入 ──► document-end 扩展 ──► 600ms 后 document-idle 扩展
```

脚本可以做的事：

- 读取和改写当前网页 DOM、注入样式（`GM_addStyle`）。
- 捕获用户点击，阻止网页默认跳转。
- 从 `href`、`data-url`、`onclick`、文本、剪贴板逻辑、网络请求里提取资源 URL。
- 调用 `fm.play()` 播放直链；`fm.vodInline()` 构造多集播放并按集回调解析。
- 调用 `fm.pan.play()` 把网盘、磁力、电驴、迅雷、荐片等推送到 App 既有解析链路。
- 调用 `fm.pan.check()` 检测支持的网盘分享是否有效。
- 调用 `fm.req()` 用 Native OkHttp 请求接口，绕开普通 WebView `fetch` 的 CORS 限制。
- 调用 `fm.res()` 把图片、视频、字幕等 DOM 资源转成本地资源网关地址。
- 调用 `fm.cache` / `GM_getValue` 保存脚本配置和轻量状态。
- 通过 `console.log()`、`GM_log()`、`fm.ext.log()` 和调试工作台排查行为。
- 按 `fongmiClient.isLeanback` 区分手机/TV，分别优化布局和遥控焦点。

脚本不适合做的事：

- 不应在没有用户动作的情况下批量打开播放页。
- 不应把所有普通站内链接都拦截成播放；必须先判断是不是资源链接。
- 不应依赖过度脆弱的第 N 个子元素选择器，例如 `body > div:nth-child(4) > a:nth-child(2)`。
- 不应为了"万能"而破坏网站自己的搜索、筛选、登录、翻页和详情跳转。
- 不应把账号、Cookie、隐私数据输出到远程日志或第三方服务。

运行边界（由 Native 包装层强制）：

- 扩展只在**顶层 frame** 执行（包装层开头 `if(window.top!==window)return;`），iframe 内不会运行。
- 每个扩展独立包装在一个 IIFE 里，互相不共享局部变量；需要共享时挂到 `window` 并用 `depends` 声明顺序。
- 包装层自带 `try/catch`：运行期异常会被捕获、打到 console 和扩展日志，并派发 `fmexterror` 事件。但**语法错误无法被捕获**——整段脚本解析失败、静默不执行（见第 13 章）。

## 2. 快速上手

10 分钟从零跑通第一个扩展：

1. 准备一个带 `homePage` 的站点并切换为主页站点（站点配置见主文档第 5、14 章）。
2. 打开 App：设置 → 增强功能 → WebHome 扩展。确认顶部总开关开启（默认开启）。
3. 点"新增"，选择"代码"，粘贴最小脚本：

```js
GM_log("hello", location.href);
GM_addStyle("body{outline:4px solid #0f766e;}");
window.addEventListener("fmsdk", function () {
  fm.ext.toast("扩展已生效");
});
```

4. 保存。回到 WebHome 页面（扩展管理保存后会触发刷新；也可手动下拉刷新或点扩展管理里的 Refresh）。
5. 看到页面出现绿色描边和 toast 即成功。点"Debug"打开调试工作台，Console 里应有 `[fm-ext] <id> hello ...`。
6. 后续迭代：在扩展管理的代码编辑里改脚本 → 保存 → 自动刷新预览；定型后托管成远程 `.js` + manifest 分发。

## 3. 扩展来源与加载机制

### 3.1 三层来源与优先级

| 层 | 配置位置 | 作用域 | 默认启用 | 排序基数 |
| --- | --- | --- | --- | --- |
| 全局扩展 | 点播配置根级 `webHomeExtensions` | 不绑定站点，**必须**用 `cspKeyRegex` 限定 | **否**（需 `"enabled": true` 或用户在管理页手动启用） | 0 |
| 站点扩展 | `sites[].extensions` | 天然绑定所属站点 | 是 | 10000 |
| 用户扩展源 | App 内"WebHome 扩展"管理页 | 可绑定某个站点或全部站点 | 是 | 20000 |

加载顺序：全局 → 站点 → 用户。**按 `id` 去重，后加载的覆盖先加载的**，因此用户本地扩展可以用相同 `id` 覆盖配置下发的扩展（调试改版常用）。同层内按书写顺序保持稳定排序。

全局扩展默认禁用是有意设计：配置作者批量下发的脚本对用户而言是"可选增强"，用户需在扩展管理页里逐个确认启用（启用远程脚本时会弹确认）。希望默认生效的站点增强应写进 `sites[].extensions`。

### 3.2 总开关与启用状态

- 总开关：设置 → 增强功能 → WebHome 扩展，对应 `Setting.isWebHomeExtension()`，默认开启。关闭后所有扩展不加载、不注入。
- 单扩展开关：按扩展 `id` 持久化（preferences key `web_home_ext_enabled_<md5(id)>`）。用户的手动开关**优先于** manifest 的 `enabled`/`disabled` 声明。
- manifest 里 `"disabled": true` 强制默认禁用；`"enabled": true/false` 显式设置默认值；都没写时用所在层的默认值（见 3.1 表）。

### 3.3 远程脚本缓存与更新

- 远程 `.js` 和 manifest 下载后缓存在 App cache 目录 `webhome_ext/`（按 URL md5 命名）。
- 每次加载都是**网络优先**：先尝试重新下载，成功则更新缓存；失败（断网、源挂了）回退使用缓存副本。所以扩展离线可用，但源端更新会在下次成功联网加载时生效。
- 扩展管理页"Clear cache"清空缓存目录并重载；"Refresh"强制重新准备并刷新页面。
- 扩展配置变化触发的页面重载有 **5 秒节流**，避免连续保存导致反复刷新。
- `updateUrl` 字段会被解析保存（管理页可展示/跳转），当前不做自动版本比对升级；版本管理依靠源 URL 内容更新 + 网络优先策略。

## 4. 配置格式

### 4.1 扩展对象完整字段

```json
{
  "id": "pomo-native-router",
  "name": "Pomo native router",
  "version": "1.2.0",
  "runAt": "document-end",
  "cspKeyRegex": ["^pomo$"],
  "excludeCspKeyRegex": [],
  "js": ["https://example.com/webhome/pomo.mom.js"],
  "code": "",
  "depends": [],
  "updateUrl": "",
  "enabled": true
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 扩展唯一 ID。为空时自动生成 `md5((sourceUrl 或 siteKey) + ":" + 序号)`——**配置顺序变化会改变自动 id 并丢失用户开关状态**，正式分发必须固定 `id` |
| `name` | string | 否 | 展示名称；为空时用 `id` |
| `version` | string | 否 | 版本号，按 `.` 和 `-` 分段比较（数字段按数值、非数字段按字符串），可被 `depends` 约束 |
| `runAt` | string | 否 | `document-start` / `document-end` / `document-idle`；非法值和缺省都按 `document-end` |
| `cspKeyRegex` | string[] / string | 否 | 匹配站点 `key` 的**正则**（`find` 语义，非全匹配；精确匹配写 `^key$`）。全局扩展必填；站点/用户扩展填写后会收窄默认作用域。非法正则会被忽略并记日志 |
| `excludeCspKeyRegex` | string[] / string | 否 | 排除站点 `key` 的正则，命中则不注入（优先级高于 include） |
| `js` | string[] / string | 否 | 外部 JS 地址，支持相对地址（相对 manifest/配置 URL 解析）和 `file://`、`local://` |
| `code` | string | 否 | 内联 JS 代码。`js` 和 `code` 至少一个，否则该扩展被丢弃；两者同时存在时 `code` 先执行，`js` 依次拼接其后 |
| `depends` | string[] / string | 否 | 依赖其它扩展 `id`，支持版本约束 `id@>=1.0.0`（见 4.7） |
| `updateUrl` | string | 否 | 更新地址，支持相对解析；当前仅记录展示 |
| `enabled` / `disabled` | boolean | 否 | 默认启用状态声明（见 3.2） |
| `manifestUrl` / `manifest` / `sourceUrl` / `url` | string | 否 | 指向远程 manifest 或 JS（见 4.5）；对象自身没有 `js`/`code` 时才会按远程指针处理 |
| `extensions` | array | 否 | 嵌套一组扩展（manifest 容器形态，见 4.5） |

### 4.2 URL 协议与相对解析

| 形态 | 解析方式 |
| --- | --- |
| `https://...` / `http://...` | 直接下载（网络优先 + 缓存回退） |
| `./path.js`、`../x/y.json`、`path.js` | 相对所在配置/manifest 的 URL 解析 |
| `file://<path>` | 读取设备本地路径（适合本地调试，无缓存） |
| `local://<name>` | 读取 App files 目录下文件 |

URL 是否被当作"脚本"判定规则：包含 `.js` 或以 `file://`、`local://` 开头按脚本处理；其余按 manifest JSON 下载解析。

### 4.3 站点内联扩展

站点自己的 `extensions` 与当前站点一一对应，通常不需要写 `cspKeyRegex`。站点对象仍要保留普通 CSP 外壳（`type: 3`、`api: "csp_Builtin"`、`homePage`）：

```json
{
  "key": "pomo",
  "name": "Pomo",
  "type": 3,
  "api": "csp_Builtin",
  "homePage": "https://pomo.mom/",
  "extensions": [
    {
      "id": "pomo-native-router",
      "name": "Pomo native router",
      "runAt": "document-end",
      "js": ["https://example.com/webhome/pomo.mom.js"]
    }
  ]
}
```

三种等价简写（适合只挂远程 JS）：

```json
"extensions": ["https://www.252035.xyz/dm.xueximeng.com.js"]
```

```json
"extensions": "https://www.252035.xyz/dm.xueximeng.com.js"
```

```json
"extensions": { "js": ["https://www.252035.xyz/dm.xueximeng.com.js"] }
```

字符串 URL 指向 `.js` 时自动生成 `id`（`remote_<urlHash>`）/`name`（文件名），按默认 `document-end` 注入。简写无法设置 `version`、`runAt`、`depends`；需要时换完整对象。

站点注入顶部有"识别"按钮，可粘贴单个或多个松散站点 JSON 片段（包括 `{...}, {...}` 或结尾带逗号的对象），自动归类并追加到 UI 列表或 JSON 文本。站点注入表单里 Key 下方有"扩展"开关，打开后输入框显示为"扩展 URL / JSON"。最简单只填一个 JS URL，例如 `https://example.com/site.js`；也兼容 `extensions` 数组或单个扩展对象，保存时规范化。也可以点"文件"选择本地 `.js` / `.css` / `.json`，本地 JS 会生成内联 `code` 扩展对象，CSS 会生成 `GM_addStyle(...)`，JSON 会规范化为 `extensions` 数组。

### 4.4 全局扩展

点播配置根级 `webHomeExtensions`，适合配置作者批量下发。必须用 `cspKeyRegex` 限定站点，且记住默认禁用（3.1）：

```json
{
  "webHomeExtensions": [
    {
      "manifestUrl": "https://example.com/webhome/extensions.json",
      "cspKeyRegex": ["^pomo$", "^dm-xueximeng$"],
      "enabled": true
    }
  ]
}
```

外层对象上的字段（如 `cspKeyRegex`、`enabled`、`runAt`）会**合并覆盖**到远程 manifest 解析出的扩展上（远程指针字段本身除外），适合在不改源文件的情况下收窄作用域或改默认状态。

### 4.5 远程 manifest 形态

`manifestUrl` 指向的 JSON 支持三种形态：

```json
[ { "id": "a", "js": ["./a.js"] }, { "id": "b", "js": ["./b.js"] } ]
```

```json
{ "extensions": [ { "id": "a", "js": ["./a.js"] } ] }
```

```json
{ "id": "a", "name": "Single", "runAt": "document-end", "js": ["./a.js"] }
```

manifest 内的相对路径相对 manifest URL 解析。`extensions` 容器可以嵌套（容器上的 `enabled`/`disabled` 会下传为子项默认值）。

### 4.6 用户扩展源（App 内）

入口：设置 → 增强功能 → WebHome 扩展。管理页提供总开关、新增、调试工作台（Debug）、刷新、清缓存，并分"User sources"与"Loaded extensions"两组展示。

| 新增方式 | 适合场景 | 说明 |
| --- | --- | --- |
| 文件 | 本地维护 `.js`、`.json` | 读取文件内容保存为本地代码源 |
| 链接 | 扩展托管在 HTTP 服务、GitHub raw、CDN | 可填 `.js` 或 manifest JSON 地址，可附带名称/runAt/匹配范围 |
| 代码 | 直接在 App 内写 JS | 开发调试主力；保存即刷新预览 |
| 表单 | 只填 ID、名称、运行时机、匹配规则和 JS 地址 | 不想手写 manifest 的用户 |
| 文本 JSON | 维护完整扩展源 JSON | 批量导入、复制、AI 输出 |

用户源匹配范围默认从当前点播配置里的 WebHome 站点弹窗多选，保存时写成精确 `cspKeyRegex`；也可以切换到正则模式手写 CSP key 正则。每个用户源有独立启用开关，源记录持久化在 preferences（`web_home_extension_user_sources`）。

### 4.7 依赖 `depends`

```json
{ "id": "my-site", "depends": ["tv-focus-helper", "shared-lib@>=1.2.0"] }
```

- 约束运算符：`>=`、`>`、`<=`、`<`、`=`（缺省为 `=`）。版本按 `.`/`-` 分段，数字段数值比较。
- 依赖检查在"匹配 + 启用"筛选之后进行，不满足的扩展整组跳过（级联：A 依赖 B、B 被跳过则 A 也跳过），状态标记 `skipped`，原因可在管理页看到：缺少依赖 / 依赖未启用 / 依赖未匹配当前站点 / 依赖不可用 / 依赖版本不满足 / **依赖注入时机更晚**（依赖的 `runAt` 必须不晚于使用方：start ≤ end ≤ idle）/ 依赖存在循环。
- 注入顺序：先按 `runAt`（start → end → idle）再按配置顺序，最后做依赖拓扑排序，保证依赖先于使用方执行。
- 被依赖的库扩展把共享 API 挂在 `window` 上（如 `window.fmTvHelper = {...}`），使用方直接读取。

## 5. 注入时机与生命周期

### 5.1 runAt

| `runAt` | 适用 | 实际注入点与注意 |
| --- | --- | --- |
| `document-start` | 提前 Hook `window.open`、`fetch`、`XMLHttpRequest`、历史路由，拦截站点最早注册的全局行为 | 依赖 androidx.webkit `DOCUMENT_START_SCRIPT` 特性（需较新系统 WebView）。支持时 SDK + start 扩展在文档创建时执行；**不支持时自动降级到 document-end 注入**（日志可见 `document-start downgraded`），脚本必须能容忍降级——Hook 写成幂等、DOM 逻辑不要假设 body 不存在 |
| `document-end` | 大多数 DOM 增强、点击拦截、按钮注入 | 默认值。在 `onPageFinished` 注入 SDK 之后立即执行，主体 DOM 通常可读，但 SPA 内容可能仍在异步渲染——配合 `MutationObserver` |
| `document-idle` | 非关键增强、批量扫描、网盘检测、性能开销大的任务 | document-end 批次之后约 600ms 注入，避免影响首屏和首次点击 |

经验规则：

- 要拦截网站很早注册的全局行为，优先 `document-start`，同时写好降级逻辑。
- 要找按钮、资源区、标题、卡片，优先 `document-end`。
- 要做大范围扫描、可见区网盘检测、媒体性能条目扫描，优先 `document-idle` 或在 end 里延迟执行。

### 5.2 包装层与生命周期事件

每个扩展被包装成独立 IIFE 注入，结构上等价于：

```js
(function(){
  if (window.top !== window) return;                  // 仅顶层 frame
  const __fmExt = { id, name, siteKey, source, runAt }; // 当前扩展元信息
  // GM_addStyle / GM_log / GM_getValue / GM_setValue / GM_deleteValue / GM_xmlhttpRequest（见第 6 章）
  try {
    /* 你的脚本（code 先、js 依次拼接） */
    window.dispatchEvent(new CustomEvent("fmextload", { detail: __fmExt }));
  } catch (e) {
    console.error("[fm-ext]", __fmExt.id, e && e.stack || e);
    // 同时写入扩展日志，并派发：
    window.dispatchEvent(new CustomEvent("fmexterror", { detail: { ...__fmExt, message } }));
  }
})();
//# sourceURL=fm-ext-<id>.js
```

要点：

- `//# sourceURL` 让 Console 报错能定位到 `fm-ext-<id>.js`。
- 运行期异常被捕获并上报；**SyntaxError 不会**——整段 evaluate 失败，`fmextload`/`fmexterror` 都不触发，表现为"扩展像没装一样"。排查方法见第 13、14 章。
- 同一次页面加载中每个扩展只注入一次（按 id 去重）；页面跳转/刷新后重新注入。
- 扩展配置变更（保存、开关、刷新）会重新准备并 reload 页面（5 秒节流）。

### 5.3 扩展状态机

管理页和 `fm.ext.info()` 反映 Registry 状态，调试时按此判断卡在哪一步：

| 状态 | 含义 | 常见原因 |
| --- | --- | --- |
| `unmatched` | 未匹配当前站点 | `cspKeyRegex` 写错（记住是对站点 key 的正则 find，不是对域名） |
| `disabled` | 被禁用 | 全局扩展默认禁用；用户手动关闭 |
| `matched` | 匹配且启用，待依赖检查 | - |
| `skipped` | 依赖检查未通过 | 见 4.7 失败原因 |
| `ready` | 进入注入列表 | - |
| `injected` | 已注入当前页面 | reason 字段记录实际注入时机 |

## 6. 脚本运行环境与 GM API

包装层提供以下油猴风格 API。**与 Tampermonkey 的关键差异：存取值是异步的（返回 Promise）**。

| API | 签名 | 语义 |
| --- | --- | --- |
| `GM_addStyle(css)` | 同步，返回 `<style>` 元素 | 追加到 `head`（或 documentElement） |
| `GM_log(...args)` | 同步 | 同时输出到页面 console（前缀 `[fm-ext] <id>`）和 App 扩展日志，调试首选 |
| `GM_getValue(key, def)` | **返回 Promise\<string\>** | 底层 `fm.cache.get(key, "webhome_ext_<id>")`；存储值为空串时返回 `def`。必须 `await` 或 `.then()` |
| `GM_setValue(key, value)` | **返回 Promise** | 值会被 `String()` 转换——对象请自行 `JSON.stringify` |
| `GM_deleteValue(key)` | **返回 Promise** | - |
| `GM_xmlhttpRequest(details)` | 返回 `{abort(){}}`（abort 为空操作） | 底层 `fm.req(details.url, details)`；成功回调 `details.onload(response)`、失败 `details.onerror(error)`。response 即 `fm.req` 返回结构（见 7.2） |

```js
// 正确：异步读取
const saved = await GM_getValue("config", "{}");
const config = JSON.parse(saved);

// 错误：当成同步用，拿到的是 Promise
const broken = GM_getValue("config", "{}").length; // undefined
```

存取值按扩展 id 隔离（rule 前缀 `webhome_ext_<id>`），不同扩展互不可见；与页面 `localStorage` 也是不同存储源。`fm.cache` 走 Native preferences，跨页面刷新与 App 重启保留。

环境探测：

```js
window.fongmiClient            // { mode: "mobile"|"leanback", isLeanback: boolean }，SDK 注入后可用
__fmExt                        // 包装层内可直接访问当前扩展元信息
document.documentElement.classList.contains("fm-native") // SDK 注入时加的标记类
```

## 7. fm SDK API 参考

SDK 由 Native 注入：`window.fongmi`（完整命名空间）+ `window.fm`（短别名）。注入完成派发 `fmsdk` 事件。`document-end`/`idle` 扩展执行时 SDK 必定已就绪；`document-start` 扩展与 SDK 同批注入、顺序在 SDK 之后，也可直接使用——但为兼容降级与健壮性，统一建议封装等待：

```js
function whenFm() {
  if (window.fm) return Promise.resolve(window.fm);
  return new Promise(function (resolve) {
    window.addEventListener("fmsdk", function () { resolve(window.fm); }, { once: true });
  });
}
```

所有 `fm.*` 调用（除 `fm.res()` 返回字符串外）返回 Promise，必须捕获异常。超过 12000 字符的 Native 返回会自动分片传输并在 SDK 侧还原，对脚本透明。

### 7.1 网络

#### `fm.req(url, options)` → Promise\<response\>

Native OkHttp 请求，无 CORS 限制。接 App 统一 DNS/代理策略（配置 `hosts`/`doh`/`proxy`、壳代理）。

| options 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `method` | string | `GET` | 自动转大写；`GET`/`HEAD` 不发 body |
| `headers` | object | `{}` | 未提供 `User-Agent` 自动补默认 UA；未提供 `Accept-Encoding` 自动补 `gzip` |
| `body` | string | `""` | 必须是字符串；JSON 自行 stringify |
| `responseType` | string | `text` | `text` / `json` / `base64` |
| `timeout` | number | `30` | 秒，最小 1 |
| `credentials` | string | - | `include` 且未手写 Cookie 头时，自动带上 WebView CookieManager 里目标 URL 的 Cookie |

返回 `{ ok, status, url, headers, cookies, body, error? }`。注意：响应 `Set-Cookie` 会写回 WebView CookieManager；自动解 `gzip`/`deflate`，**`br` 不支持**（服务端强返 br 会进 error）；请求异常时 `ok:false, status:500, error:<消息>` 而不是 reject。

与站点 `header` 配置的关系：加载 WebHome 页面时，站点 `header` 里的 `User-Agent` 会覆盖 WebView UA、`Cookie` 写入 CookieManager、其余 header 只附加到**首个页面请求**——页面内子资源和 `fm.req()` 都不会自动携带。扩展需要这些 header 时必须在 `options.headers` 里显式传，或经 `fm.res()` 代理。

#### `fm.res(url, options)` → string（同步）

生成本地资源网关地址 `http://127.0.0.1:{port}/webResource?url=...`，给 `img.src`、`video.src`、字幕、CSS 背景用。`options.headers` / `credentials: "include"` 同上。网关透传 `Range`（视频拖动必需）、回写 CORS 头、只允许 http/https 目标。

选型：JS 读 API 数据用 `fm.req`；DOM 元素加载资源用 `fm.res`。

### 7.2 播放

#### `fm.play(url, title, options)` — 播放直链

```js
await fm.play("https://example.com/video.m3u8", "影片名", {
  pic: "https://example.com/poster.jpg",
  wallPic: "https://example.com/backdrop.jpg",
  headers: { Referer: location.href },
  credentials: "include"
});
```

进入 App 推送播放链路（`SiteApi.PUSH`）。`pic` 是海报/默认 artwork，`wallPic` 是播放页背景图；没有 `wallPic` 时播放页使用 App 默认背景/壁纸，不用 `pic` 兜底。传了 `headers` 或 `credentials: "include"` 时，播放 URL 自动转成 `/webResource` 网关地址。`url` 也可以是 App 可识别的特殊协议地址（主文档 26.12）。如果页面要自己用 `fm.history()` 渲染 push 最近观看，应额外用 `fm.cache` 按 URL 保存并恢复 `pic/wallPic`，不要假设原生历史一定带回 `wallPic`。

#### `fm.vod(siteKey, vodId, title, pic, { wallPic })` — 打开 CSP 站点影片

进入对应站点的原生详情/播放链路，`vodId` 即该站 Spider 的 `vod_id`。

#### `fm.vodInline(payload)` — 临时多集 / 按集解析

见第 8 章专述。

#### `fm.ctrl(action)` / `fm.stat()`

`action`: `play` / `pause` / `stop` / `prev` / `next` / `loop` / `replay`。无播放服务时 `ctrl` 静默返回 `{}`，`stat` 返回 `{}`。`fm.stat()` 正常返回 `{ state, speed, duration, position, url, title, artist, artwork }`，`state`: 1 其它 / 2 ready / 3 playing / 6 buffering。

### 7.3 网盘与推送

#### `fm.pan.play({ type, url, password, title, pic, wallPic })`

统一推送播放入口：网盘分享、`magnet:`、`ed2k:`、`thunder:`、`jianpian:`、普通 http 均可，内部走 `SiteApi.PUSH`/`push_agent`/`pvideo` 链路。`type` 只用于语义和日志，不参与路由。`pic`/`wallPic` 只影响原生播放页展示图，不参与解析。开头的 `push://` 会自动剥离。不受网盘检测开关影响。原生历史对 push 链路通常只可靠保留海报；WebHome 自建“最近观看”需要继续保留剧照时，应在播放前用 `fm.cache` 缓存 URL 到 `{ pic, wallPic }` 的映射，并在读取 `fm.history()` 后恢复。

推荐 `type`：`quark` / `aliyun` / `baidu` / `uc` / `xunlei` / `tianyi` / `123` / `115` / `mobile` / `magnet` / `ed2k` / `thunder` / `jianpian` / `http`。

#### `fm.pan.check(items)`（别名 `fm.check`）

```js
const config = await fm.config();
if (config.driveCheck) {
  const result = await fm.pan.check([{ type: "quark", url: "https://pan.quark.cn/s/xxxx", password: "" }]);
  // result.results[i]: { type, url, normalized_url, state, cache_hit, checked_at, expires_at, summary }
}
```

- `state`：`ok` 有效 / `bad` 失效 / `locked` 需提取码 / `unsupported` 不支持该类型 / `uncertain` 风控或请求失败。
- 受"增强功能 → 网盘检测"开关控制（关闭时 reject），**调用前必须检查 `fm.config().driveCheck`**。
- 只提交支持检测的网盘类型；磁力、电驴、thunder、普通网页不要提交。
- 内部每批 10 条并发、结果缓存（ok 1h / bad 6h / locked 12h / uncertain 30min）。检测应异步、限可见范围、不阻塞首屏。
- 支持类型明细、URL 特征和别名表见主文档 21.1。

### 7.4 缓存 `fm.cache`

```js
await fm.cache.set("key", "value", "rule");   // 只存字符串
const value = await fm.cache.get("key", "rule"); // 不存在返回 ""
await fm.cache.del("key", "rule");
```

逻辑缓存 key 为 `cache_<rule>_<key>`，底层存放在应用缓存目录，用户清理缓存时会一并删除。扩展建议直接用 `GM_getValue/GM_setValue`（自动带扩展隔离前缀），手写 `rule` 时注意避免与其它扩展/页面冲突。

### 7.5 App 能力

| 接口 | 说明 |
| --- | --- |
| `fm.search(keyword, { direct, pic, wallPic })` | 打开原生搜索；`direct: true` 直达结果列表，`pic`/`wallPic` 会带入后续搜索结果播放链路 |
| `fm.openVod()` / `fm.openSite()` | 返回原生点播首页 / 打开内容源选择器 |
| `fm.openLive()` / `fm.openKeep()` / `fm.openSetting()` | 打开直播 / 收藏 / 设置 |
| `fm.history()` | 观看记录数组（字段见主文档 19.3），可用于"从播放页返回后补偿进度" |

### 7.6 信息

| 接口 | 返回 |
| --- | --- |
| `fm.device()` | `{ uuid, name, ip, type(0 TV/1 手机/2 DLNA), serial, eth, wlan, time }` |
| `fm.site()` | `{ key, name, homePage, type, header }` —— 当前主页站点 |
| `fm.config()` | `{ id, url, desc, driveCheck }` —— 当前点播配置 |

### 7.7 扩展专用 `fm.ext`

| 接口 | 说明 |
| --- | --- |
| `fm.ext.info()` | `{ siteKey, siteName, homePage, enabled, matched, ready }`：总开关状态与当前站点匹配/就绪扩展计数 |
| `fm.ext.log(message, data)` | 写 App 扩展日志；`data` 传 `__fmExt` 可让日志关联到扩展条目（`GM_log` 已自动做） |
| `fm.ext.toast(message)` | 弹原生 toast，用户可见的轻量反馈（操作失败、复制成功等） |

### 7.8 UI 与导航

| 接口 | 说明 |
| --- | --- |
| `fm.ui.setChrome(options)` | 运行时切换 WebHome chrome。手机端支持 `normal` / `edge` / `immersive`；首页融合用 `edge`，专注详情页才用 `immersive`。TV 端支持 `tv-normal` / `tv-toolbar-hidden` / `tv-overlay` / `tv-full`，跨端 `edge` / `immersive` 会映射为 `tv-full`。`options` 可含 `mode`、`statusBarStyle`、`navigationBarStyle`、`restoreAffordance`、`scrim`。仅首页全屏偏好这类“下次冷启动也要生效”的设置才带 `startup: true`，Native 只持久化 `normal` / `edge`，不会保存 `immersive` |
| `fm.ui.restoreChrome()` | 从 `immersive` 回到进入前的 chrome；关闭自绘详情页时优先用它，或显式 `setChrome({ mode: "edge" })` |
| `fm.ui.getViewport()` | 返回当前 WebView 尺寸、安全区、手势区、系统栏高度和 `chromeMode`；持续变化看 `fmviewport` |
| `fm.ui.setToolbar(visible)` | legacy chrome API。`false` 在手机端等价旧式沉浸行为，会隐藏原生顶部/底部和系统栏；TV 端 `false` 切到 `tv-toolbar-hidden`，`true` 恢复站点默认 chrome。新脚本不要用它表示首页融合，保留给旧页面兼容 |
| `fm.back()` | 触发 App 侧 WebHome 返回，遵循返回边界规则（下） |
| `fm.reload()` | 清 WebView 缓存并以 `_fm_reload={ts}` 参数重新加载主页 |

返回边界规则（物理返回键与 `fm.back()` 一致）：上一条 history 与当前页**同协议+host+端口**才执行 `goBack()`；已回到主页 URL（忽略 `_fm_reload`/`_fm_restore` 参数）或上一条跨站点时交还原生返回。含义：扩展不必担心用户被返回键带去站外页，但站内多级跳转的返回体验需要自己保证（如 SPA 用 History API）。

### 7.9 事件与 CSS 变量总览

| 事件 | detail | 触发时机 |
| --- | --- | --- |
| `fmsdk` | - | SDK 注入完成（重复注入也会再次派发） |
| `fmextload` | `__fmExt` | 某扩展执行成功结束 |
| `fmexterror` | `__fmExt + { message }` | 某扩展运行期抛错 |
| `fmurlchange` | `{ url }` | SDK hook 了 `pushState`/`replaceState`/`popstate`，SPA 路由变化必听 |
| `fmviewport` | `{ width, height, safeTop, safeRight, safeBottom, safeLeft, safeBottomMax, gestureLeft, gestureRight, gestureBottom, statusBarHeight, navigationBarHeight, keyboardBottom, chromeMode, systemBarsHidden }` | WebView 布局尺寸、安全区、手势区或 chrome mode 变化；同时更新根元素 CSS 变量 |
| `fmresume` | `{ time, pausedMs }` | App 从后台恢复 WebHome（延迟分多次派发以兜底渲染恢复） |
| `fmpause` | `{ time }` | WebHome 进入后台暂停 |

CSS 变量（挂在 `:root`）：`--fm-web-width`、`--fm-web-height`、`--fm-safe-top`、`--fm-safe-right`、`--fm-safe-bottom`、`--fm-safe-left`、`--fm-safe-bottom-max`、`--fm-gesture-left`、`--fm-gesture-right`、`--fm-gesture-bottom`、`--fm-status-bar-height`、`--fm-navigation-bar-height`、`--fm-keyboard-bottom`、`--fm-chrome-mode`、`--fm-system-bars-hidden`。注入 UI 的高度用 `var(--fm-web-height, 100vh)` 替代裸 `100vh`，规避旧 WebView 视口偏差。

扩展样式需要同时兼容浏览器 `env()` 和 Native 注入变量时，必须用 `max()` 去重，不要相加：

```css
padding-bottom: calc(12px + max(var(--fm-safe-bottom, 0px), env(safe-area-inset-bottom, 0px)));
top: max(12px, max(var(--fm-safe-top, 0px), env(safe-area-inset-top, 0px)));
```

## 8. 按集解析深入（`fm.vodInline` + resolver）

### 8.1 payload

```js
await fm.vodInline({
  vod_id: "demo-1",              // 可省略，自动生成
  vod_name: "影片名",             // 或 title
  vod_pic: "https://...",        // 或 pic
  vod_play_from: "WebHome",      // 线路名
  mark: "02",                    // 进入播放页默认选中的集名
  headers: { Referer: location.href },  // 全局播放请求头
  credentials: "include",        // 播放请求带 WebView Cookie
  episodes: [ /* 见下 */ ]
});
```

`episodes[]` 字段：

| 字段 | 说明 |
| --- | --- |
| `name` / `label` / `title` | 集名（用于显示与 `mark` 匹配） |
| `url` | 直链或集数标识。也接受字符串元素简写 |
| `mediaUrl` | 已解析好的真实媒体地址（有它就不再回调解析） |
| `pageUrl` / `href` | 集数页面地址；按集解析必填 |
| `resolve` | `true` 时点击该集才回调 resolver；有 `pageUrl` 且无 `mediaUrl` 时自动视为需要解析 |
| `format` | 如 `application/x-mpegURL`；URL 含 `.m3u8` 时自动推断 |
| `headers` / `referer` / `credentials` | 该集独立请求头（覆盖合并全局） |
| `active` | 标记当前集（优先于 `mark` 匹配） |

### 8.2 resolver 协议

```js
window.__fmWebHomeInlineResolver = async function (episode) {
  // episode 是 episodes[] 中对应项的深拷贝
  return {
    url: "https://example.com/01.m3u8",   // 必填，空则视为失败
    format: "application/x-mpegURL",      // 可选
    headers: { Referer: episode.pageUrl },// 可选，合并到该集请求头
    referer: "",                          // 可选简写
    credentials: "include"                // 可选
  };
};
```

Native 行为（务必了解，影响脚本设计）：

1. **预解析当前集**：调用 `fm.vodInline()` 时，Native 会先同步解析"当前集"（`active: true` 的项 → 否则 `mark` 匹配项 → 否则第一项），成功后才打开播放页，保证点开即播。失败不阻塞打开（进播放页后点集再试）。
2. **按需解析**：用户在原生播放页点击某集时，Native 在 **WebHome 页面上下文**里调用 resolver，**20 秒超时**。解析成功的集会被缓存，重复点击不再回调。
3. **页面必须存活**：resolver 跑在 WebHome 页面里。页面被刷新、跳走或渲染进程被杀后，未解析的集会失败。扩展应避免在播放期间触发页面跳转；站点 SPA 路由变化不影响（页面还在）。
4. WebHome 在后台暂停时，Native 会临时恢复 WebView 执行解析、完毕后再暂停，脚本无需处理。
5. resolver 可以复用站点自身的解密函数/播放器配置（这是它优于服务端解析的地方——拿的是页面运行时上下文）。

模板见 `templates/extensions/inline-episodes.js`，实战见 `examples/extensions/ymvid/ymvid.com.js`。

### 8.3 适用判断

| 场景 | 用法 |
| --- | --- |
| 已拿到全部集直链 | `episodes` 直接给 `url`，不需要 resolver |
| 只有集页面链接，单集解析成本高 | `pageUrl + resolve: true` + resolver，按需解析 |
| 单集直链 | 直接 `fm.play()` 更简单 |
| 站点本身是 CSP 可爬 | 写 Spider 而不是扩展 |

## 9. 网站分析流程

AI 或人工拿到网站 URL 后，按下面顺序分析。

### 9.1 判断页面类型

先回答：

- 首页、列表页、详情页、播放页分别是什么 URL 形态？（写成正则，用于脚本内分页路由）
- 资源按钮在列表页还是详情页？
- 页面是服务端直出 HTML，还是 SPA 运行后才渲染？
- 资源 URL 是直接写在 DOM，还是点击后通过 API 获取？
- 默认行为是 `window.open`、`location.href`、复制剪贴板、下载文件，还是打开站内播放页？

### 9.2 找稳定选择器

优先语义稳定的选择器：

```js
".download-item"
".download-link"
"[data-url]"
"a[href*='magnet:']"
"a[href*='pan.quark.cn']"
```

避免：

```js
"body > div:nth-child(3) > div:nth-child(2) > a"
".text-gray-900.mt-4.flex.items-center"   // 原子化 CSS 类不稳定
```

选择器需要能跨多部影片、多页结果、多种资源分组工作。

### 9.3 找资源 URL

按优先级提取：

1. `data-url`、`data-href`、`data-link`、`data-clipboard-text`。
2. `href`，排除 `javascript:;`、`#`、空值和站内导航。
3. `onclick` 字符串里的 URL。
4. 当前卡片或按钮附近文本里的 URL。
5. 点击后出现的弹窗、复制文本、网络请求（用 `templates/extensions/media-sniffer.js` 采样）。

### 9.4 识别资源类型

```js
function classify(url) {
  if (/^magnet:/i.test(url)) return "magnet";
  if (/^ed2k:/i.test(url)) return "ed2k";
  if (/^thunder:/i.test(url)) return "thunder";
  if (/pan\.quark\.cn/i.test(url)) return "quark";
  if (/aliyundrive\.com|alipan\.com/i.test(url)) return "aliyun";
  if (/pan\.baidu\.com/i.test(url)) return "baidu";
  if (/drive\.uc\.cn/i.test(url)) return "uc";
  if (/pan\.xunlei\.com/i.test(url)) return "xunlei";
  if (/cloud\.189\.cn/i.test(url)) return "tianyi";
  if (/123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i.test(url)) return "123";
  if (/115\.com|115cdn\.com/i.test(url)) return "115";
  if (/yun\.139\.com|caiyun\.139\.com/i.test(url)) return "mobile";
  if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url)) return "media";
  return "http";
}
```

`media` → `fm.play()`；其余 → `fm.pan.play({ type, url, title })`。

## 10. 注入策略选择

| 策略 | 使用场景 | 优点 | 风险 |
| --- | --- | --- | --- |
| 捕获点击并改路由 | 页面已有"下载/播放"按钮 | 改动小，用户习惯不变 | 需要准确判断资源按钮，避免拦截站内导航 |
| 注入"App 播放"按钮 | 不想改变原网站按钮行为 | 可控、明确 | UI 需要适配移动/TV（第 12 章） |
| 改写链接 `href` | 链接列表很规整 | 实现简单 | 容易破坏站点原跳转和复制 |
| 网络/媒体嗅探 | 资源点击后动态加载直链 | 能发现 DOM 没暴露的媒体 | JS 只能看到页面层 fetch/XHR/performance，不等同完整 DevTools |
| 按集解析（vodInline） | 站点有剧集列表、每集懒解析 | 原生播放页体验、保留换集 | 页面必须存活、20s 超时（第 8 章） |
| 页面清理 | 弹窗、遮罩、新窗口影响使用 | 改善体验 | 不能过度隐藏正文和关键操作 |
| 整页重排 | 移动端排版混乱、TV 无法操作 | 体验提升最大 | 工作量大、对站点改版敏感，参考 `examples/extensions/` |

最稳妥的默认组合：

1. 注入"App 播放"按钮。
2. 对明确资源按钮做捕获点击（捕获阶段 `addEventListener(..., true)`，抢在站点 handler 之前）。
3. 保留复制按钮、详情跳转、搜索筛选的原行为。
4. 对 SPA 使用 `MutationObserver`（debounce）+ `fmurlchange` 重扫。
5. TV 端补焦点支持（`templates/extensions/tv-focus-helper.js`）。

在线播放、线路切换或异步渲染区域不要只在首次扫描时把 URL 写进按钮 `dataset` 后长期复用。站点可能在切换线路、展开分组、通过跳转页解析后改写真实播放地址；扩展应在每次重扫时刷新已存在按钮的数据，并在点击"App 播放"时从同一个资源条目/相邻原站按钮重新读取实时 URL，再调用 `fm.play()`、`fm.vodInline()` 或 `fm.pan.play()`。如果桌面浏览器能播但 App 按钮不能播，优先比对"被点击的原站播放页 URL / 最终媒体 URL / 响应类型"， stale URL 或绑定到错误线路比播放器本身故障更常见。

## 11. 通用脚本结构

完整骨架直接使用 `templates/extensions/site-enhance-skeleton.js`（含分页路由、自适应类、TV 焦点标记、资源路由）。最小结构要素：

```js
(function () {
  const CONFIG = { /* 所有站点相关选择器、正则集中在这里 */ };

  function log() { /* GM_log 优先，console 兜底 */ }
  function whenFm() { /* 见 7 章 */ }
  function ready(fn) {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fn, { once: true });
    else fn();
  }
  function schedule(fn) {            // 所有重扫必须 debounce
    clearTimeout(schedule.timer);
    schedule.timer = setTimeout(fn, 120);
  }

  ready(function () {
    document.addEventListener("click", onClick, true);   // 捕获阶段
    new MutationObserver(function () { schedule(scan); })
      .observe(document.documentElement, { childList: true, subtree: true });
    scan();
  });
  window.addEventListener("fmurlchange", function () { schedule(scan); });

  function scan() { /* 注入按钮、刷新状态；用 data-fm-* 标记防重复 */ }
  function onClick(event) { /* 捕获资源点击；try/catch + toast */ }
})();
```

硬性规则：

- 不拦截所有 `a[href]`，只拦截已分类为资源的链接。
- 点击处理 `try/catch`，失败 `fm.ext.toast()` 或 `GM_log()`，不静默。
- 用 `closest()` 和事件委托，不给每个按钮单独绑监听。
- 用 `data-fm-*` 标记已处理节点，避免重复注入。
- MutationObserver 回调必须 debounce，不全量重扫。
- 标题提取有兜底链：详情标题 → `h1/h2` → `document.title` → URL。
- 远程分发脚本固定 `id` 和 `version`。

## 12. 影视页面 UI/UX 最佳实践（手机端 / 电视端 / 自适应）

扩展注入第三方网站时，目标是"让原站在 App 两种形态下都好用"，而不是重做一个站。改动按性价比排序：资源入口 > 干扰清理 > 焦点可用性 > 排版重构。

### 12.1 形态判定与自适应

- **唯一可靠判定是 `window.fongmiClient.isLeanback`**（Native 注入）。不要用屏幕宽度或 UA 猜测：1080p 电视的 CSS 视口宽度可能与平板接近，盒子 UA 千奇百怪。
- 判定结果落到 `body` 状态类（如 `fm-tv` / `fm-mobile`），CSS 和 JS 都从类读取，每次 `scan` 时用 `classList.toggle` 同步（SDK 晚于脚本就绪时也能纠正）。
- 同一脚本服务两种形态：共用 DOM 和扫描逻辑，只切换类与少量参数（列数、字号、按钮尺寸）。不要写两套脚本。
- 布局尺寸读 `--fm-web-width` / `--fm-web-height`，监听 `fmviewport` 响应窗口、安全区和 chrome mode 变化（分屏、折叠屏、TV 工具栏显隐、手机端 edge/immersive 切换）。

### 12.2 手机端

触控与可达性：

- 注入按钮的可点热区不小于约 44x44 CSS px（视觉可以更小，用 padding 扩热区）。
- "App 播放"等高频入口放在资源条目内或吸底操作区，避免顶部。吸底元素要避开手势条，Native 安全区和浏览器 `env()` 必须用 `max()` 去重：`padding-bottom: calc(12px + max(var(--fm-safe-bottom, 0px), env(safe-area-inset-bottom, 0px)))`。
- 如果移动端顶部存在固定/粘性操作区（返回、搜索、标签、播放按钮等），只在 WebView 与系统栏融合或全屏时预留顶部安全区，例如 `chromeMode: "edge"`、`"immersive"` 或 `systemBarsHidden: true` 且确有 `safeTop/statusBarHeight`。退出全屏恢复普通 chrome 后必须移除顶部预留，避免出现一条不可点击的空白栏。实现上监听 `fmviewport`，用状态类控制 `padding-top`，不要永久写死。
- 屏幕左右边缘约 24dp 是系统返回手势区，注入的浮动按钮不要贴边。

清理与重排：

- 优先清理：弹窗遮罩、`window.open` 广告（hook 改为本窗打开）、滚动锁定、底部下载横幅。模板 `templates/extensions/site-cleanup.js`。
- 隐藏要克制：用 `display:none!important` 类标记（`data-fm-hidden`），不要 `remove()` 节点——站点脚本可能引用它们导致报错。
- 详情页重排参考 `examples/extensions/dm-xueximeng/dm.xueximeng.com.js`：海报/标题/标签/简介优先，资源区聚合成统一面板，剧照弱化为背景。
- 双列海报 grid 适合手机；卡片固定 2:3 比例容器，避免图片加载抖动。

弹层与滚动：

- 自绘面板打开时给 `body` 加锁滚动类，关闭时**必须移除**；关闭后恢复原滚动位置。
- 进入自绘全屏详情可 `fm.ui.setChrome({ mode: "immersive", restoreAffordance: "none" })`，自绘仅图标返回按钮，关闭时 `fm.ui.restoreChrome()` 或 `fm.ui.setChrome({ mode: "edge" })` 回到首页融合；缺少自绘返回兜底时才用 `restoreAffordance: "native"`。`fm.ui.setToolbar(false)` 只作为旧脚本兼容，不再作为新脚本推荐写法。

### 12.3 电视端

遥控器是"当前焦点 + 上下左右 + OK + 返回"模型，第三方网站几乎不会为此设计，扩展要补齐最小可用集（完整方法论见主文档 22.5-22.7）：

焦点可用性（必做）：

- 所有需要操作的元素必须可聚焦：`<a href>`/`<button>` 天然可聚焦；卡片、`div` 按钮补 `tabindex="0"`。用 `templates/extensions/tv-focus-helper.js`。
- 焦点样式必须明显且**不改变布局**：用 `outline` + 背景提亮 + 轻微 `transform: scale(1.02)`；禁止改 `width/height/margin/border-width`（布局抖动会引发整页重排和跳动）。
- 用 `:focus` 写焦点样式，**不要用 `:focus-visible`**（旧内核会丢弃整条规则，见 13.3）。
- OK 键处理优先调用元素原生 `.click()`——部分旧 WebView 对合成 `MouseEvent("click")` 不可靠；`keydown` 同时判断 `event.key === "Enter"` 和 `event.keyCode === 13/23`（部分盒子 keyCode 23 = DPAD_CENTER 且 `key` 非标准）。

输入与防误触：

- 文本输入框默认 `readonly`，按 OK/触摸才解除进入编辑态，失焦恢复——否则方向键路过搜索框就弹输入法。
- 默认焦点不要落在输入框；落在第一个内容卡片或 Tab。

性能（低端盒子按键路径必须轻）：

- 方向键高频路径上不要全局 `querySelectorAll` + 逐个 `getBoundingClientRect()`；规则 grid 用索引算目标（`index ± 1`、`index ± columns`）。
- 焦点后的滚动修正用 `focus({ preventScroll: true })` + `requestAnimationFrame` 合帧；长按方向键只处理最后一个目标。
- MutationObserver 重扫 debounce；TV 上减少 `backdrop-filter`、大面积阴影、图片 filter。
- 注入面板可加 `contain: layout style` 收窄重排范围；**不要用 `contain: paint` 或 `content-visibility`**——会裁切焦点高亮、影响可聚焦性，必须真机验证后才可用。
- 注入面板打开时，限制方向键在面板内（局部焦点域），返回键先关面板再交给系统。

视觉（10-foot UI）：

- 3 米观看距离：注入 UI 正文 ≥ 24px、次要信息 ≥ 20px，对比度比手机端更高。
- 屏幕四周约 5% 是 overscan 风险区，注入的浮动面板和按钮避开。
- 海报墙焦点放大不应遮挡相邻行标题。

### 12.4 注入 UI 视觉规范

- 所有注入元素带统一前缀类（`fm-` 或站点缩写），避免与站点样式冲突；样式通过 `GM_addStyle` 注入并用足够特异性（必要时 `!important`）抵御站点全局样式。
- 浮动面板 `z-index` 用大值（如 `2147483647`），但要确认不遮挡站点关键弹窗（登录、验证码）。
- 注入按钮配色与站点主色协调即可，不必复刻；状态用轻量圆点/短字（"有效/失效/需码"），不要大段文字挤占列表。
- 异步状态四态俱全：加载中、有数据、空、失败（带重试或提示），不留空白洞。
- 图片经 `fm.res()` 代理时记得带 `Referer`（很多站点海报有防盗链）。

## 13. 旧 WebView 兼容避雷

App `minSdk` 24（Android 7.0，出厂 Chromium ~51）；电视和盒子经常无法升级 WebView，**把 Chromium 50-70 当成必须兼容的基线**。完整版本对照表见主文档 15.1-15.5，本章是扩展脚本视角的避雷要点。

### 13.1 扩展脚本的特殊风险：语法错误 = 整个扩展静默消失

扩展由 Native `evaluateJavascript` 注入。旧内核解析到不认识的语法直接 `SyntaxError`：

- 包装层的 `try/catch` 救不了（解析阶段失败，根本没执行）。
- `fmextload` / `fmexterror` 都不触发，管理页状态停在 `injected` 但页面毫无变化。
- 唯一线索是调试工作台 / 调试日志（`webhome-console` tag）里的 `SyntaxError`。

**语法基线：ES2017（`async/await` 及以下）。** 高危清单（最低 Chromium 版本）——生成或评审脚本后全文搜索这些字符：

| 禁用语法 | 需要 | 替代 |
| --- | --- | --- |
| 可选链 `?.` | 80 | `a && a.b` |
| 空值合并 `??` | 80 | `a == null ? b : a` |
| 逻辑赋值 `||=` `&&=` `??=` | 85 | 拆开写 |
| `catch {}` 省略绑定 | 66 | `catch (e) {}` |
| 正则命名捕获 `(?<name>)`、lookbehind `(?<=)` | 64/62 | 普通分组（正则**字面量**解析失败等同语法错误） |
| 正则 dotAll `s` 标志 | 62 | `[\s\S]` |
| class 字段 / `#private` | 72/74 | constructor 赋值 |
| `**` 指数 | 52 | `Math.pow()` |

包装层自身使用 `const`/箭头函数/模板字符串（ES2015），这是事实下限——无需也不可能降到 ES5。ES2015-2017 的箭头函数、解构、`let/const`、`class`、`for...of`、Promise、`async/await` 都安全。

### 13.2 API 避雷（运行时报错，可检测可兜底）

| API | 需要 | 应对 |
| --- | --- | --- |
| `String.replaceAll` | 85 | `split/join` 或全局正则 |
| `Promise.allSettled` | 76 | `Promise.all` + 每项自行 catch |
| `Object.fromEntries` | 73 | 手写循环 |
| `Array.flat/flatMap` | 69 | `[].concat.apply` 或循环 |
| `structuredClone` | 98 | `JSON.parse(JSON.stringify())` |
| `Promise.prototype.finally` | 63 | `.then(done, done)` 兜底 |
| `Object.values` / `Object.entries` | 54 | 基线内可用；更低目标用 `Object.keys` 循环 |
| `globalThis` | 71 | `window` |
| `AbortController` | 66 | 请求序号/token 让旧响应失效 |
| `IntersectionObserver` | 51（~58 前不稳） | `"IntersectionObserver" in window` 检测 + 被动 scroll 兜底 |
| `Element.replaceChildren` | 86 | `textContent = ""` + append |
| `navigator.clipboard` | 66 且需安全上下文 | 站点是 http 时大概率不可用；`document.execCommand("copy")` 兜底 |
| `scrollIntoView({behavior})` | 61 | 旧内核把对象参数当 boolean；统一封装滚动 |
| `NodeList.forEach` | 51 | 基线内可用；更稳妥用 `Array.prototype.slice.call()` 转数组 |
| `CSS.escape` | 46 | 检测后用正则转义兜底（见 `templates/extensions/page-analyzer.js`） |

扩展与单文件 WebHome 的差别：扩展**没有页面头部**可放 ES5 引导层，polyfill 只能写在扩展自身开头；多个扩展共享站点时，把 polyfill 收进一个公共依赖扩展（`depends` 保证先执行）。改第三方站点时还要注意：**不要 polyfill 全局原型去"修"站点代码**，只为自己的逻辑做特性检测。

### 13.3 CSS 避雷（静默失败：不报错但布局崩）

两条解析语义：

- **值不认识 → 整条声明丢弃**：`clamp()`、`env()`、`100dvh` 所在的那条属性整个失效。先写兜底声明，下一行再写现代值。
- **选择器列表里一个不认识 → 整条规则丢弃**：`:is()`、`:where()`、`:has()`、`:focus-visible` 不能与普通选择器混写在同一条逗号列表，否则普通选择器也一起失效。扩展样式直接别用这些选择器。

| 特性 | 需要 | 扩展场景应对 |
| --- | --- | --- |
| flex `gap` | **84** | 注入面板布局间距用 `margin` 而不是 flex gap |
| `aspect-ratio` | 88（部分内核 `CSS.supports` 撒谎） | 海报占位用 `padding-top` 百分比技巧 |
| `min()/max()/clamp()` | 79 | 固定值兜底声明在前 |
| `backdrop-filter` | 76（~87 前不稳） | 永远配半透明纯色底色；TV 端干脆不用 |
| `inset` | 87 | 先写 `top/right/bottom/left` |
| `:focus-visible` | 86 | TV 焦点样式用 `:focus` |
| `position: sticky` | 56 | 可接受降级 |
| `100dvh` | 108 | 用 `var(--fm-web-height, 100vh)` |
| CSS 变量 / `@supports` / `object-fit` / `-webkit-line-clamp` | 基线内 | 安全（line-clamp 必须配 `display:-webkit-box` + `-webkit-box-orient:vertical`） |

### 13.4 其它环境差异

- `document-start` 依赖 `DOCUMENT_START_SCRIPT` 特性，旧 WebView 自动降级到 document-end（5.1）——Hook 类脚本必须容忍降级。
- 渲染进程崩溃时 Native 会重建 WebView 并以 `_fm_restore=1` 参数重载页面，扩展会重新注入；不要依赖跨重载的内存状态，重要状态走 `GM_setValue`。
- 验收方法：开启调试日志（设置 → 增强功能 → 调试日志），`webview` tag 输出当前设备 WebView provider 包名和版本；在手头最旧的盒子上完整过一遍核心路径。电脑 Chrome 预览**不能**暴露这些问题。

## 14. 调试与排查

### 14.1 工具入口

| 工具 | 入口 | 用途 |
| --- | --- | --- |
| 扩展管理页 | 设置 → 增强功能 → WebHome 扩展 | 总开关、增删改用户源、单扩展开关、状态/原因展示、刷新、清缓存 |
| 调试工作台 | 扩展管理页 → Debug | Console（页面 console + `GM_log`）、Network（页面 fetch/XHR + Native 请求记录）、Elements（DOM 片段与候选选择器）、代码编辑保存即预览 |
| 调试日志 | 设置 → 增强功能 → 调试日志；或 `http://127.0.0.1:{port}/debug/logs` | 全链路日志，支持 WebHome/Console/WebView 等过滤（明细见主文档 26.14） |
| chrome://inspect | 电脑 Chrome + USB 调试 | 工作台开启调试时 WebView 远程调试可用，完整 DevTools |

### 14.2 推荐迭代流程

1. 扩展管理页"新增 → 代码"，绑定当前站点。
2. 打开调试工作台进入目标页面，跑 `templates/extensions/page-analyzer.js` 采集候选选择器/资源/标题结构。
3. 基于模板改 `CONFIG`，保存预览，点击目标按钮验证路由。
4. Console 看 `GM_log` 输出；Network 看请求；Elements 验证选择器。
5. 定型后托管远程 `.js` + manifest，换"链接"方式装载，固定 `id`/`version` 分发。

### 14.3 常见故障速查

| 现象 | 排查顺序 |
| --- | --- |
| 扩展完全没生效 | 总开关 → 管理页状态（`unmatched`：cspKeyRegex 是匹配**站点 key** 的正则，不是域名；`disabled`：全局扩展默认禁用；`skipped`：看依赖原因）→ Console 找 `SyntaxError`（13.1） |
| 脚本生效但时灵时不灵 | SPA 路由没监听 `fmurlchange`；MutationObserver 没装或没 debounce；节点被站点重渲染冲掉（用 `data-fm-*` 标记 + 重扫补齐） |
| 点击没反应也没报错 | 站点 handler 先吃掉事件 → 改捕获阶段监听；按钮其实是复制逻辑 → 先分析数据源 |
| `GM_getValue` 拿到奇怪值 | 它返回 Promise（第 6 章），漏了 `await` |
| `fm.req` 返回 `ok:false status:500` | 看 `error` 字段；`br` 编码不支持；目标站防爬（补 Referer/UA/Cookie，`credentials:"include"`） |
| 图片/视频加载不出 | 防盗链：`fm.res(url, { headers: { Referer: ... } })`；混合内容已放行，不是 https 问题 |
| 播放页点集失败 | resolver 20s 超时或页面已被刷新（8.2）；先看 `webhome-inline` 日志 |
| 网盘检测不执行 | `fm.config().driveCheck` 为 false；提交了不支持的类型 |
| TV 上无法操作 | 元素不可聚焦（补 tabindex）；焦点样式用了 `:focus-visible`（整条规则被旧内核丢弃）；OK 键没映射 `.click()` |
| 老盒子白屏/扩展消失 | 语法红线（13.1）逐项搜索；确认 WebView 版本 |

Native 日志 tag 速查（调试日志页可过滤）：`webhome-ext`（注入/匹配/依赖）、`webhome`（bridge 调用）、`webhome-inline`（按集解析）、`webhome-net`（fm.req）、`web-resource`（资源网关）、`webhome-webview`（页面加载/崩溃/返回边界）、`webhome-console`（页面 console）、`webhome-focus` / `webhome-key`（TV 焦点与按键）。

## 15. 质量检查清单

发布脚本前逐项确认：

功能：

- [ ] 首页、列表页、详情页、播放页都不报错（Console 无新增异常）。
- [ ] 没有资源的页面不注入多余按钮。
- [ ] 搜索、筛选、翻页、详情跳转、登录仍可用。
- [ ] 资源按钮点击后进入 App 播放或推送链路；失败有 toast 或日志。
- [ ] 复制按钮保持复制行为（除非明确改为播放）。
- [ ] SPA 路由切换后按钮不重复、不丢失。
- [ ] 网盘检测只在 `driveCheck` 开启时运行，且限可见范围。

工程：

- [ ] `id` / `version` 固定；`cspKeyRegex` 精确（`^key$`）。
- [ ] 扫描 debounce；大页面无卡顿。
- [ ] `GM_getValue` 等异步 API 均已 await；所有 Promise 均有 catch。
- [ ] 无语法红线用法：搜 `?.`、`??`、`||=`、`&&=`、`??=`、`(?<`、`replaceAll`、`allSettled`、`fromEntries`、`flat(`、`structuredClone`。
- [ ] CSS 无 `:is()/:where()/:has()/:focus-visible`；flex gap、`aspect-ratio`、`clamp()` 有兜底。
- [ ] 没有把账号、Cookie、隐私数据输出到远程。

体验：

- [ ] 手机端：按钮热区 ≥ 44px、吸底元素避开手势条、弹层关闭恢复滚动。
- [ ] TV 端：可聚焦、焦点样式可见且不抖动布局、OK 可点、输入框不误弹输入法。
- [ ] `GM_log("ready")` 能在调试工作台看到。

## 16. AI 自动开发协议

给 AI 本文档 + 网站 URL 时，AI 按以下协议工作。

### 16.1 输入

- 本 README（必读）；可选 `templates/extensions/` 与 `examples/extensions/`。
- 目标网站 URL；可选：用户期望（拦截/注入按钮/重排/按集解析）、目标站点 key、目标形态（手机/TV/双端）。
- 可选：`templates/extensions/page-analyzer.js` 在目标页面的输出 JSON（最可靠的选择器依据）。

### 16.2 分析步骤

1. 按 9.1 判断页面类型与渲染方式（直出/SPA）。
2. 按 9.2-9.4 确定资源选择器、URL 来源属性、资源类型集合、标题选择器。
3. 按第 10 章选择注入策略（默认组合优先；有剧集列表且懒解析 → 第 8 章按集解析）。
4. 确定 `runAt`（默认 `document-end`；需 Hook 全局行为才用 `document-start` 并写降级容忍）。

### 16.3 输出物（按序完整给出）

1. 页面角色判断与 URL 正则。
2. 关键 DOM 选择器表：标题、资源区、资源项、按钮、链接（标注稳定性依据）。
3. 资源 URL 来源与分类规则。
4. 注入策略与理由。
5. **完整可运行 JS 脚本**（基于 `templates/extensions/site-enhance-skeleton.js` 结构）。
6. 装载配置：站点 `extensions` 写法 + 可选远程 manifest。
7. 测试步骤（对照 14.2）与已知风险。

### 16.4 生成脚本硬性规则

- 遵守第 11 章硬性规则、第 12 章双端体验、第 13 章兼容红线（生成后自查 15 章工程清单）。
- 语法基线 ES2017；CSS 不用现代选择器；所有站点特定值集中在 `CONFIG`。
- 只拦截已分类为资源的链接；点击处理 try/catch + toast。
- TV 适配：资源按钮/卡片可聚焦 + `:focus` 样式；或声明依赖 `tv-focus-helper`。
- 不调用未在本文档列出的 `fm.*` 接口；不假设端口、不硬编码 `127.0.0.1:9978`。
- 输出的 manifest 固定 `id`（kebab-case，含站点名）与 `version`（从 `1.0.0` 起）。

### 16.5 半自动生成（App 内能力设计）

"完全无确认自动改网站"不可取——下载按钮可能是广告/登录/复制，同页多资源版本无法自动取舍，泛拦截破坏浏览。可行路径是"自动分析 + 候选确认 + 模板生成 + 立即预览"：工作台增加"分析当前页"（跑 page-analyzer 本地生成候选 JSON）→ 用户确认资源按钮/标题/动作 → 套模板生成 manifest + JS → 保存预览。分层：页面采集器（中）/ 规则生成器（中）/ 交互调试 UI（中高）。涉及把页面数据发给外部 AI 时必须用户确认。

## 17. 模板与示例清单

模板不是最终脚本：使用时先改 `CONFIG`，再按目标网站微调选择器和标题提取。所有模板已按第 13 章兼容基线书写。

| 文件 | 用途 |
| --- | --- |
| `templates/extensions/site-enhance-skeleton.js` | **推荐起点**：完整站点增强骨架——分页路由（home/list/detail/play）、SPA 重扫、手机/TV 自适应类、TV 焦点标记、资源点击路由 |
| `templates/extensions/page-analyzer.js` | 页面候选 DOM/资源/标题分析器，在调试工作台运行，输出 JSON 供人或 AI 生成脚本 |
| `templates/extensions/auto-resource-router.js` | 通用资源路由：捕获网盘/磁力/直链点击改走原生播放 |
| `templates/extensions/inject-play-buttons.js` | 给资源条目注入"App 播放"按钮，不改原按钮行为 |
| `templates/extensions/pan-link-router.js` | 网盘链接增强：注入播放按钮 + 可见范围有效性检测（含状态显示） |
| `templates/extensions/inline-episodes.js` | 按集解析：收集剧集列表 → `fm.vodInline` + `__fmWebHomeInlineResolver` 协议完整示范 |
| `templates/extensions/tv-focus-helper.js` | TV 遥控焦点助手：可聚焦标记、布局安全焦点样式、OK→click、输入框 readonly 守卫、恢复焦点；可作公共依赖（`depends`） |
| `templates/extensions/media-sniffer.js` | 页面层媒体嗅探：hook fetch/XHR、扫 video/source/performance，浮动面板点击即播 |
| `templates/extensions/site-cleanup.js` | 页面清理：弹窗/遮罩/广告隐藏、解锁滚动、window.open 改本窗、恢复右键选择 |
| `templates/homepages/basic-homepage.html` | 单文件首页起步模板：透明背景、SDK 就绪、顶部安全区按 chrome mode 动态启停 |
| `templates/homepages/app-capabilities-showcase.html` | App SDK 能力测试台：集中演示 `fm.req/res/play/vod/vodInline/pan/cache/ui/device/site/config/history` 等开放能力，默认使用国内可访问媒体/图片资源，内置 PanSou 实时搜索和网盘检测矩阵 |
| `examples/extensions/pomo/pomo.mom.js` + `pomo.manifest.json` | 实战：详情页资源聚合为"在线播放/网盘/磁力"统一面板，双端重排 |
| `examples/extensions/dm-xueximeng/dm.xueximeng.com.js` + `dm.xueximeng.manifest.json` | 实战：移动端详情页重排、资源链接增强、TV 焦点 |
| `examples/extensions/ymvid/ymvid.com.js` + `ymvid.manifest.json` | 实战：按集解析完整案例（站点解密函数复用 + vodInline + resolver） |
| `examples/extensions/gying/gying-extension.js` + `gying-extension.manifest.json` | 实战：在线播放分组注入右侧 App 播放、点击时重读动态线路 URL、公开 HLS/DASH 直播、安全区随 fullscreen/chrome mode 动态开关 |
| `examples/homepages/nostr.html` | 单文件 WebHome 首页示例：透明 WebView、移动/TV 适配、顶部安全区按 chrome mode 动态启停 |

## 18. 实战示例结论

### 18.1 Pomo（pomo.mom）

首页是影片列表，详情页有"资源下载"区：资源项 `.download-item`，链接/按钮 `.x-dbjs-download-link` / `.x-dbjs-download-btn`，真实 URL 在 `data-url`，标题在 `.x-dbjs-title`。

策略：隐藏分散的在线播放/下载区减少误点；首页改搜索优先的简洁列表（保留筛选翻页）；卡片手机双列、平板/TV 多列加焦点高亮；详情页重排海报/元数据/简介层级，从资源区提取网盘/磁力/电驴/迅雷，后台读在线播放页解析 `rawData` 里的 m3u8 选集；统一播放面板按"在线播放 / 网盘 / 磁力"三组展示——在线条目 `fm.play()`，其余 `fm.pan.play()`。

```json
{
  "key": "pomo", "name": "Pomo", "type": 3, "api": "csp_Builtin",
  "homePage": "https://pomo.mom/",
  "extensions": [{
    "id": "pomo-native-router", "name": "Pomo native router",
    "version": "1.2.2", "runAt": "document-end",
    "js": ["https://example.com/webhome/pomo.mom.js"]
  }]
}
```

### 18.2 美漫共建（dm.xueximeng.com）

策略：清理移动端详情页，重排海报/剧照/标题/标签，增强资源链接为原生播放入口，补 TV 焦点。站点结构规整、资源直接在 DOM，属于"清理 + 注入按钮"标准组合，见 `examples/extensions/dm-xueximeng/dm.xueximeng.com.js`。

### 18.3 粤漫之家（ymvid.com）—— 按集解析范例

播放页 `/play/{videoId}` 或 `/play/{videoId}/{seriesId}`；`#main` 带 `data-id`/`data-series-id`，剧集列表 `.play-list`，当前集 `.item.active`；播放地址由站点脚本解密隐藏 input 拼出 m3u8。

策略：保留原站播放器，播放区下方注入"App播放 / 刷新地址"和剧集栏；点击"App播放"把集数链接传给 `fm.vodInline()`，用户在原生播放页点集时经 `window.__fmWebHomeInlineResolver` 回调，在页面上下文里复用站点 `decryptByAES()` 解出该集 m3u8（这正是 resolver 模式的价值：服务端拿不到的运行时解密，页面里现成可用）；单集兜底从 Artplayer 构造参数或 Hls `loadSource()` 捕获；手机端隐藏评论/页脚/侧栏并重排为播放器→App入口→剧集栏→海报信息；TV 端卡片加 tabindex 与焦点高亮；清理首屏公告和播放器广告层但保留登录等业务弹窗。

```json
{
  "key": "ymvid", "name": "粤漫之家", "type": 3, "api": "csp_Builtin",
  "homePage": "https://www.ymvid.com/",
  "extensions": [{
    "id": "ymvid-native-router", "name": "Ymvid native router",
    "version": "1.0.0", "runAt": "document-end",
    "js": ["https://example.com/webhome/ymvid.com.js"]
  }]
}
```
