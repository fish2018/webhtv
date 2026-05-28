# WebHome 用户脚本扩展改造方案

## 1. 目标定位

本方案面向 WebHome 的下一阶段开放能力：App 不内置具体站点适配脚本，不维护具体网站规则，只提供一套稳定的“用户脚本扩展运行框架”。

用户脚本由影视 App 开发爱好者、站点维护者或用户自己独立开发和分发。App 负责：

- 读取扩展清单。
- 按 CSP `key` 正则匹配是否加载扩展。
- 在 WebHome WebView 中按合适时机注入 JS 入口脚本。
- 向扩展提供完整 WebHome SDK 能力。
- 提供脚本缓存、更新、日志和开关管理。
- 提供扩展依赖和脚本级工具复用能力。

最终原则：**彻底不做扩展权限管理，不要求开发者填写能力授权字段。扩展一旦被用户启用，就默认可以调用 App 已开放给 WebHome 的 SDK 能力。**

本方案不是为了绕过 Cloudflare、验证码或网站风控。合理边界是：用户可以在 WebView 中正常完成人机验证，验证通过后脚本增强页面交互，例如移动端适配、播放链接接管、网盘/磁力链接交给 App 播放。

## 2. 参考对象和取舍

### 2.1 Tampermonkey / Greasemonkey

用户脚本生态的成熟经验：

- 使用少量脚本元数据描述扩展，例如 `id`、`name`、`version`。
- 使用匹配规则控制脚本运行范围。
- 使用 `run-at` 控制注入时机：`document-start`、`document-end`、`document-idle`。
- 使用外部依赖和资源管理脚本复用。
- 使用版本号和更新地址支持脚本更新。

对本项目的取舍：

- 保留元数据、版本、注入时机、依赖和更新模型。
- 不照搬 URL 匹配规则，改成适合 CSP 体系的 `cspKeyRegex`。
- 不照搬浏览器用户脚本的能力授权模型，开发者不需要声明每个 SDK 能力。
- 不照搬完整 GM API，只提供 WebHome 场景真正需要的兼容子集。

### 2.2 Chrome Extension Content Scripts

Chrome 扩展内容脚本的成熟经验：

- 内容脚本与扩展主体分离，通过 manifest 声明注入规则。
- 支持 `run_at` 控制注入时机。
- 需要调试能力，让开发者知道哪个脚本在当前页面被加载、何时执行、是否报错。

对本项目的取舍：

- 扩展清单应该是声明式，而不是把规则写死进 App。
- JS 入口、依赖和注入时机分开描述。
- 不实现浏览器扩展式后台页、标签页管理、WebRequest 修改和隔离世界。
- 不做扩展权限管理，改用“用户显式启用 + 来源展示 + 匹配范围展示 + 运行时限制 + 日志”的方式控制风险。

### 2.3 可视化编辑器实践

扩展配置和脚本编辑可以参考 CodeMirror、Monaco Editor 等浏览器代码编辑器的能力。CodeMirror 更轻、更适合 Android WebView 和手机端内嵌编辑；Monaco 能力强但体积和运行成本更高，更适合桌面端或外部管理工具。

对本项目的启发：

- 第一版可以用普通多行文本编辑器加 JSON 校验，降低实现成本。
- 后续内嵌 CodeMirror，提供语法高亮、搜索替换、折叠、括号匹配、基础 lint、行号。
- 代码编辑不是高频入口，应放在扩展详情的高级编辑页里，不需要区分用户模式。
- TV 端遥控器不适合复杂代码编辑，TV 端应以启用、禁用、查看匹配和查看日志为主。

### 2.4 Android WebView 约束

当前项目 WebHome 使用 Android WebView，现状是：

- `HomeWebController` 中启用了 JavaScript、DOM Storage、Cookie、第三方 Cookie。
- `HomeWebBridge` 通过 `addJavascriptInterface` 暴露 Native Bridge。
- 当前 `window.fm` / `window.fongmi` SDK 在 `onPageFinished()` 后通过 `evaluateJavascript()` 注入。
- 当前项目未引入 `androidx.webkit`，没有使用 `WebViewCompat.addDocumentStartJavaScript()`。

Android WebView 和 Chrome 扩展最大差异：

- Android WebView 没有完整 Chrome Extension 的隔离世界模型。
- `addJavascriptInterface` 暴露的对象对页面 JS 可见，加载第三方网站时需要把该网站视为有能力调用 Bridge 的页面。
- `evaluateJavascript()` 通常只能可靠地做 `document-end` 或更晚注入。
- 要实现更接近 `document-start` 的能力，应引入 `androidx.webkit:webkit` 并检查 `WebViewFeature.DOCUMENT_START_SCRIPT`。

结论：本项目可以实现用户脚本扩展能力，但必须明确安全边界，不能把它设计成浏览器级隔离插件系统。

## 3. 为什么只基于 CSP key 正则匹配

本方案建议 App 层只使用 CSP `key` 做扩展匹配，而不是使用页面 URL 做匹配。

原因：

- CSP `key` 是配置维护者可控的稳定标识，比网页 URL 更适合表达“这是哪个站点/哪个 WebHome”。
- 很多网站有多域名、跳转、登录页、CF 验证页、移动端/PC 端域名变化，URL 匹配容易误伤或漏匹配。
- 一个 WebHome 可能在运行过程中跳转多个 URL，但它仍然属于同一个 CSP 站点上下文。
- 用户脚本面向影视 App 场景，CSP key 比通用浏览器 URL 更符合现有配置体系。
- 可以避免“同域名不同 CSP 配置”互相污染。

推荐规则：

- App 只用 `site.key` 和扩展声明的 `cspKeyRegex` / `excludeCspKeyRegex` 判断是否加载扩展。
- URL 不参与 App 层加载匹配。
- 扩展脚本内部如果需要精细处理某个页面，可以自己读取 `location.href` 做运行时判断。
- 一个扩展可以通过正则适配多个 CSP key，也可以匹配全部 WebHome。
- 一个 CSP 可以加载多个扩展，多个扩展按依赖、注入时机和优先级排序。

示例：

```json
{
  "id": "common-link-catcher",
  "name": "通用播放链接接管",
  "cspKeyRegex": ["^.*$"],
  "excludeCspKeyRegex": ["^local_debug$"],
  "runAt": "document-start"
}
```

```json
{
  "id": "site-foo-mobile",
  "name": "Foo 站移动端适配",
  "cspKeyRegex": ["^foo$", "^foo_.+$"],
  "runAt": "document-end"
}
```

## 4. 总体架构

建议新增以下模块：

```text
app/src/main/java/com/fongmi/android/tv/web/ext/
├── WebHomeExtension.kt/java       扩展清单模型
├── WebHomeExtensionSource.kt/java 扩展订阅源模型
├── WebHomeExtensionRegistry       读取、缓存、更新、启用/禁用扩展
├── WebHomeExtensionMatcher        基于 CSP key 正则匹配扩展
├── WebHomeExtensionResolver       依赖解析、版本约束和加载顺序
├── WebHomeExtensionInjector       注入 JS 入口，管理 runAt
├── WebHomeExtensionBridge         扩展专用 API 包装和运行日志
├── WebHomeExtensionLog            日志、错误、调试信息
└── WebHomeExtensionStore          脚本级持久化存储
```

与现有代码集成点：

- `Site`：不需要强制新增字段，第一阶段只读取已有 `site.getKey()`。
- `HomeWebController.load(Site site)`：保存当前 `siteKey`、`siteName`、`homePage`，并让 Registry 选择匹配扩展。
- `HomeWebController.client()`：在 `onPageStarted()`、`onPageFinished()`、页面恢复时触发不同 runAt 注入。
- `HomeWebBridge`：保持现有 WebHome SDK；扩展启用后可以调用完整 SDK；Bridge 侧只做参数校验、限流、异常保护和日志。
- 设置页：增加“WebHome 扩展”入口，用于启用/禁用、配置、更新、依赖和日志查看。

## 5. 扩展清单设计

清单要尽量短。一个扩展本质上只需要回答三个问题：

- 它叫什么。
- 它匹配哪些 CSP key。
- 它要注入哪个 JS 入口。

`js` 是 App 的注入入口：告诉 App 下载、缓存并执行哪个扩展脚本。第一版不设计独立 `css` 字段，样式统一由 JS 自己处理，例如调用 `GM_addStyle`、动态创建 `<style>`，或按需创建 `<link rel="stylesheet">` 引用外部 CSS。

扩展不区分类型，也不要求权限声明。一个扩展可以同时处理样式、依赖其他扩展、修改页面或只作为工具脚本使用。是否做页面增强取决于脚本内容本身，不需要用字段提前分类。

最小可运行示例：

```json
{
  "id": "foo-mobile-fix",
  "name": "Foo 移动端修复",
  "cspKeyRegex": ["^foo$"],
  "js": ["https://example.com/foo-mobile-fix.js"]
}
```

稍完整示例：

```json
{
  "id": "common-link-catcher",
  "name": "通用播放链接接管",
  "cspKeyRegex": ["^.*$"],
  "excludeCspKeyRegex": ["^local_debug$"],
  "runAt": "document-start",
  "depends": ["fm-open-lib@>=1.0.0"],
  "js": ["https://example.com/common-link-catcher.js"],
  "updateUrl": "https://example.com/common-link-catcher.json"
}
```

工具脚本示例：

```json
{
  "id": "fm-open-lib",
  "name": "通用打开链接工具",
  "cspKeyRegex": ["^.*$"],
  "runAt": "document-start",
  "js": ["https://example.com/fm-open-lib.js"]
}
```

字段说明：

| 字段 | 是否必需 | 说明 |
| --- | --- | --- |
| `id` | 必需 | 扩展唯一 ID，用于缓存、依赖、日志和本地开关 |
| `name` | 必需 | 展示名称 |
| `cspKeyRegex` | 必需 | 匹配 CSP key 的正则列表，任一命中即可加载 |
| `js` | 必需 | 需要注入的 JS 入口文件；样式也通过这个 JS 入口处理 |
| `runAt` | 可选 | `document-start`、`document-end`、`document-idle`，默认 `document-end` |
| `excludeCspKeyRegex` | 可选 | 排除 CSP key 的正则列表，优先级高于 include |
| `depends` | 可选 | 依赖的扩展 ID 和版本约束，例如 `fm-open-lib@>=1.0.0` |
| `version` | 可选 | 版本号，只在订阅更新或依赖版本判断时需要 |
| `updateUrl` | 可选 | 更新检查地址 |

不进入第一版主清单的字段：

- `type`：没有实际必要，是否是页面增强或工具脚本由 JS 自己决定。
- `grants`：不做扩展权限管理。
- `enabled`：这是 App 本地开关状态，不应写进远程扩展清单。
- `namespace`、`author`、`homepage`、`description`：展示信息可以放在扩展说明页或订阅源元数据里，不作为脚本运行必需字段。
- `noframes`：WebHome 扩展默认只面向顶层 WebHome 页面，第一版不做 iframe 细分。
- `priority`：优先用 `depends` 表达加载关系；没有依赖时按清单顺序加载。
- `css`：样式由 JS 通过 `GM_addStyle`、`<style>` 或 `<link>` 自己处理，第一版不单独做 CSS 注入字段。
- `exports`：不让开发者在清单里声明导出命名空间；工具脚本先加载后，自己在 JS 里决定暴露什么函数。
- `require`、`resource`：第一版先用 `js`、`depends` 覆盖主要场景，避免再引入一套资源模型。
- `sha256`：完整性校验可以作为订阅源平台能力后续补充，不进入开发者日常手写清单。

清单中不设计 SDK 能力授权字段。开发者不需要声明脚本会调用哪些 `fm.*` API。

## 6. 能力开放边界

本方案明确不做扩展权限管理：

- 不要求开发者填写 SDK 能力授权字段。
- 不在运行时按扩展逐项拦截 `fm.req`、`fm.play`、`fm.res`、`fm.check` 等能力。
- 不做“用户逐项勾选能力”的配置页。
- 不区分本地扩展、远程扩展、普通模式、开发者模式。
- 用户启用扩展后，该扩展默认可以调用 App 已开放给 WebHome 的完整 SDK。

这样设计的原因：

- WebHome 扩展的典型用途通常天然需要页面增强、Native 请求、播放、资源转发、存储、网盘检测等组合能力。
- 细粒度声明很容易变成开发者全选、用户看不懂，增加复杂度但收益有限。
- 这个项目的边界不是浏览器扩展商店，而是用户主动配置和启用的影视 App 扩展生态。
- 真正危险的能力应谨慎决定是否放进 WebHome SDK，而不是交给每个扩展单独声明。

风险控制改为以下机制：

- 新增扩展默认不运行，必须用户显式启用。
- 启用时展示扩展来源、名称、匹配 CSP key、依赖、更新地址等关键信息。
- 扩展来源、依赖、匹配范围明显变化时，需要重新确认。
- 完整性校验可以作为订阅源平台能力后续补充，不要求开发者手写。
- Native 请求、资源网关、网盘检测、播放调用做全局限流、超时、大小限制和错误保护。
- 运行日志记录关键 API 调用摘要，方便排查异常。
- 设置页提供一键禁用全部扩展。
- 剪贴板读取、原始 Cookie 读写、任意文件系统访问、完整隐私数据读取等能力第一版不放进 WebHome SDK。

重要安全限制：Android WebView 没有完整隔离世界。只要 WebHome 加载的是第三方网页，并且 Native Bridge 暴露到页面环境，页面本身理论上也可能访问 Bridge。因此扩展系统不能宣称拥有 Chrome Extension 等级的隔离安全。最佳实践是：

- WebHome 页面仍应视为可信页面或用户明确授权页面。
- App 已开放的 SDK 必须本身足够稳健，做好参数校验、限流、异常保护和日志。
- 不直接鼓励脚本调用底层 `fongmiBridge`，统一走 `window.fm` / `window.fongmi` 包装层。
- 后续可考虑随机 Bridge 名称、调用令牌和调试日志，但这只能降低误用风险，不能提供完整隔离。

## 7. 注入时机设计

参考 Tampermonkey / Greasemonkey / Chrome Content Scripts，支持三个时机。

### 7.1 document-start

用途：

- 尽早注册点击拦截。
- Hook `window.open`、`history.pushState`、`fetch`、`XMLHttpRequest`。
- 给页面补 viewport 或基础样式。
- 注册扩展导出的工具函数，让后续脚本可以调用。

实现建议：

- 引入 `androidx.webkit:webkit`。
- 如果 `WebViewFeature.DOCUMENT_START_SCRIPT` 可用，使用 `WebViewCompat.addDocumentStartJavaScript()`。
- 每次 WebHome 切换 CSP key 时，移除旧的 ScriptHandler，重新注册当前 key 匹配的 document-start 脚本。
- 默认只在顶层 WebHome 页面执行，基础注入脚本可内置保护：

```js
if (window.top !== window) return;
```

降级策略：

- 如果当前 WebView 不支持 document-start，则降级到 `onPageFinished()` 执行，并在调试日志里标记 `document-start downgraded`。
- 不要为了模拟 document-start 做不稳定的轮询注入。

### 7.2 document-end

用途：

- 修改 DOM。
- 绑定按钮事件。
- 插入移动端适配样式。
- 处理已经渲染出的搜索结果、播放按钮、网盘链接。

实现建议：

- 在 `onPageFinished()` 后注入。
- 当前项目已有 `injectSdk()`，可扩展成：先注入 SDK，再注入匹配扩展的 document-end 脚本。
- 样式由扩展 JS 调用 `GM_addStyle` 或自行创建 `<style>` 实现。

### 7.3 document-idle

用途：

- 页面资源加载完成后的低优先级增强。
- 复杂 DOM 扫描。
- SPA 页面二次渲染后的补丁。

实现建议：

- 在 `onPageFinished()` 后 `setTimeout()` 执行。
- 默认延迟 300-1000ms。
- 不要阻塞页面 ready。

这三个时机足够覆盖 WebHome 场景。复杂页面、SPA、多层跳转和异步渲染不需要增加更多 `runAt`，应由脚本内部通过 `fmurlchange`、`MutationObserver`、点击捕获等方式处理。

## 8. SPA 和多层页面处理

很多网站是 SPA，URL 变化不会触发完整页面加载。建议注入一个通用 URL 变化监听器，参考 Tampermonkey 的 URL change 思路。

App 注入基础脚本：

```js
(function(){
  const rawPush = history.pushState;
  const rawReplace = history.replaceState;
  function emit(){
    window.dispatchEvent(new CustomEvent('fmurlchange', { detail: { url: location.href } }));
  }
  history.pushState = function(){ const r = rawPush.apply(this, arguments); emit(); return r; };
  history.replaceState = function(){ const r = rawReplace.apply(this, arguments); emit(); return r; };
  window.addEventListener('popstate', emit);
})();
```

扩展脚本可以监听：

```js
window.addEventListener('fmurlchange', () => enhancePage());
```

注意：App 层仍然只按 CSP key 判断扩展是否加载；URL 变化只通知已经加载的脚本自行处理。

## 9. 扩展 API 设计

建议不要直接暴露完整 GM API，而是提供 WebHome 场景最需要的一组 API。核心原则是：Native 提供稳定原子能力，工具脚本可以在 JS 里封装高级组合能力。

Native 原子能力建议包括：

```js
await fm.play(url, title, options)
await fm.search(keyword, options)
await fm.req(details)
fm.res(url, options)
await fm.pan.check(items)
await fm.cache.get(key, defaultValue)
await fm.cache.set(key, value, options)
await fm.site.info()
await fm.device.info()
await fm.ext.info()
fm.ext.log(message, data)
fm.ext.toast(message)
```

### 9.1 `fm.open(url, options)`

`fm.open()` 是推荐的通用链接接管入口，但它不一定必须由 App Native 直接实现。最佳实践是：

- App Native 提供 `fm.play`、`fm.req`、`fm.res`、`fm.search`、`fm.pan.check`、`fm.pan.play` 等稳定原子能力。
- 可信工具脚本可以在运行时补充 `fm.open(url, options)` 这类高级判断逻辑。
- 如果后续这个库足够稳定，可以由系统库把 `fm.open` 作为别名暴露出来。

调用示例：

```js
await fm.open(url, {
  title: document.title,
  referer: location.href
});
```

推荐处理规则：

- `magnet:`、`ed2k:`、`thunder:`、`jianpian:`、网盘分享链接：统一调用 `fm.pan.play({ type, url, password, title })`，让 App 进入 `SiteApi.PUSH` 播放链路。
- `fm.play('push://' + url)` 仍兼容，但工具脚本里优先用 `fm.pan.play`，避免到处手动拼协议前缀。
- 视频直链、m3u8、mp4：直接播放。
- 普通 HTTP 网页：默认交给 WebView 继续打开。
- 带 headers/cookie 的资源：可结合 `fm.res()`。

这样扩展开发者不需要重复写协议判断，同时 App 不需要把所有高级策略都写死在 Native 里。

### 9.2 兼容 GM 子集

建议提供轻量 GM 兼容层，降低用户脚本开发门槛。

| GM API | 对应实现 |
| --- | --- |
| `GM_addStyle(css)` | 插入 `<style>` |
| `GM_log(message)` | 写入扩展日志 |
| `GM_getValue(key, defaultValue)` | 脚本级存储读取 |
| `GM_setValue(key, value)` | 脚本级存储写入 |
| `GM_deleteValue(key)` | 脚本级存储删除 |
| `GM_xmlhttpRequest(details)` | 映射到 `fm.req()` |
| `GM_getResourceText(name)` | 读取缓存资源文本 |
| `GM_getResourceURL(name)` | 返回资源 URL 或 data URL |

不建议第一版实现：

- `unsafeWindow`。
- 任意 WebRequest 修改。
- 跨标签页 API。
- 后台常驻脚本。
- 浏览器扩展式完整菜单体系。

## 10. 工具脚本和脚本级 SDK

用户提到的“一个扩展实现一个工具类，别的脚本调用这个函数，比如 `fm.open()` 不在 App 实现，而是在扩展中实现”是可行的，而且不需要在清单里额外写 `exports`。

推荐模型：

- App Native 提供稳定原子能力。
- 工具脚本可以封装高层逻辑，例如链接识别、DOM 等待、移动端修复、网盘类型判断。
- 其他扩展通过 `depends` 声明依赖，确保工具脚本先加载。
- 工具函数怎么暴露由 JS 自己决定，清单不声明导出命名空间。
- 推荐工具脚本只补充缺失的辅助函数，例如 `if (!fm.open) fm.open = async function (...) { ... }`，不要覆盖已有 Native SDK 方法。

工具脚本示例：

```js
(function(){
  if (fm.open) return;

  fm.open = async function(url, options = {}) {
      if (/^(magnet|ed2k|thunder|jianpian):/i.test(url) || fm.link.isDrive(url)) {
        return fm.pan.play({
          type: fm.link.panType(url),
          url,
          password: options.password || '',
          title: options.title || url
        });
      }
      if (/\.m3u8(\?|$)|\.mp4(\?|$)/i.test(url)) {
        return fm.play(url, options.title || url, {
          headers: options.headers,
          credentials: options.credentials
        });
      }
      location.href = url;
    };

  fm.link = fm.link || {};
  fm.link.isDrive = function(url) {
    return /pan\.quark\.cn|drive\.uc\.cn|pan\.baidu\.com|aliyundrive\.com|alipan\.com/i.test(url);
  };
  fm.link.panType = function(url) {
    if (/pan\.quark\.cn/i.test(url)) return 'quark';
    if (/drive\.uc\.cn/i.test(url)) return 'uc';
    if (/pan\.baidu\.com/i.test(url)) return 'baidu';
    if (/aliyundrive\.com|alipan\.com/i.test(url)) return 'aliyun';
    return '';
  };
  fm.link.isMagnet = function(url) {
    return /^magnet:/i.test(url);
  };
})();
```

加载和依赖规则：

- 同一时机内按依赖拓扑排序；没有依赖关系时按清单顺序加载。
- 扩展声明 `depends` 后，依赖缺失或版本不满足时不注入，并在日志里显示原因。
- App 只负责按依赖顺序加载，不关心工具脚本导出了哪些函数。
- App 应向用户展示“此脚本依赖哪些工具脚本、这些工具脚本来自哪里”。

依赖示例：

```json
{
  "id": "site-foo-enhance",
  "cspKeyRegex": ["^foo$"],
  "depends": ["fm-open-lib@>=1.0.0"],
  "runAt": "document-end"
}
```

调用示例：

```js
document.addEventListener('click', async function(event) {
  const a = event.target.closest && event.target.closest('a[href]');
  if (!a) return;
  const url = a.href || a.getAttribute('href');
  if (!fm.link || (!fm.link.isDrive(url) && !fm.link.isMagnet(url))) return;
  event.preventDefault();
  await fm.open(url, { title: a.textContent || document.title });
}, true);
```

这种模式的好处是：通用 SDK 能力可以由开发者生态迭代，不需要每次都改 App；App 只需要保证底层 Native 能力稳定、运行限制合理、日志可查。

## 11. 通用链接接管脚本示例

此类脚本可以作为第三方扩展示例，不建议写死进 App。

```js
// ==UserScript==
// name        通用播放链接接管
// run-at      document-start
// depends     fm-open-lib@>=1.0.0
// ==/UserScript==
(function(){
  const RE = /(magnet:|ed2k:|thunder:|jianpian:|\.m3u8(\?|$)|\.mp4(\?|$)|pan\.quark\.cn|aliyundrive\.com|alipan\.com|cloud\.189\.cn)/i;

  document.addEventListener('click', async function(event){
    const a = event.target && event.target.closest ? event.target.closest('a[href]') : null;
    if (!a) return;
    const url = a.href || a.getAttribute('href');
    if (!url || !RE.test(url)) return;
    event.preventDefault();
    event.stopPropagation();
    await fm.open(url, { title: a.textContent || document.title, referer: location.href });
  }, true);
})();
```

## 12. CF 盾和验证码页面处理

可支持的部分：

- 用户把目标网站配置为 WebHome。
- WebView 正常显示 CF 盾、验证码或登录页。
- 用户手动完成验证。
- Cookie 保留在 WebView CookieManager。
- 验证通过后用户脚本增强页面，接管播放/网盘/磁力链接。

不应该承诺的部分：

- 自动绕过验证码。
- 自动破解 CF Challenge。
- 绕过网站风控策略。

建议机制：

- 扩展默认不要在明显的验证页面做重 DOM 改造。
- 可以在扩展内部判断 `location.href` 是否包含 `/cdn-cgi/` 或页面是否有验证码特征，如果是则直接 return。
- App 可以在调试面板显示“当前疑似验证页，脚本已加载但建议等待用户验证完成”。

## 13. 配置入口建议

具体配置形式可以后续再定，但建议支持两层。

### 13.1 全局扩展订阅

```json
{
  "webHomeExtensions": [
    "https://example.com/webhome-extensions/index.json",
    "./extensions/index.json"
  ]
}
```

### 13.2 站点级开关

即使扩展自身使用 `cspKeyRegex`，站点也应能显式控制是否启用扩展。

```json
{
  "key": "foo",
  "name": "Foo WebHome",
  "homePage": "https://foo.example.com",
  "extensions": {
    "enable": true,
    "include": ["common-link-catcher", "foo-mobile-fix"],
    "exclude": ["debug-extension"]
  }
}
```

解释：

- `cspKeyRegex` 负责扩展声明“我适配哪些 CSP key”。
- `site.extensions.include/exclude` 负责配置维护者对当前站点做最终裁剪。
- 如果不想扩展配置污染 `Site` 模型，第一版可以只做全局开关和全局订阅。

## 14. 扩展管理 UI 和可视化编辑器

这个功能不需要区分普通模式和开发者模式。最佳实践是统一入口、分页面承载复杂度：列表页负责启用和确认，详情页负责查看匹配、依赖和日志，编辑页负责精细配置。

### 14.1 扩展列表与详情页

建议放在设置页的“WebHome 扩展”入口。

列表能力：

- 顶部搜索框：按扩展名、ID、作者、来源、CSP key 搜索。
- 过滤标签：全部、已启用、已禁用、有依赖、异常、可更新。
- 每个扩展显示：名称、版本、启用开关、匹配 CSP 数量、依赖状态、更新状态。
- 支持一键禁用全部扩展、清理缓存、手动更新订阅、查看运行日志。

详情页能力：

- 显示来源、updateUrl 和更新状态。
- 显示匹配规则和当前命中的 CSP key。
- 显示依赖库、依赖来源和版本状态。
- 显示最近注入记录、错误和脚本日志。
- 启用时用一个确认页说明“启用后该扩展可以调用 WebHome SDK 已开放能力”。

TV 端设计：

- 遥控器优先支持上下选择、左右切换启用状态、OK 进入详情、返回退出。
- TV 端不建议做完整代码编辑，只提供启用/禁用、查看匹配、查看日志、更新和清理缓存。
- 启用确认弹窗必须能用遥控器清晰确认，默认焦点放在“取消”。

### 14.2 扩展编辑视图

手机和平板端可以提供完整编辑器，TV 端可只读。这个编辑页只是功能入口，不代表 App 需要区分开发者模式。

基础信息表单：

- `id`、`name`、`cspKeyRegex`、`js`、`runAt`、`depends`、`updateUrl`。
- `runAt` 使用分段控件：document-start、document-end、document-idle。

CSP key 正则编辑：

- `cspKeyRegex` 和 `excludeCspKeyRegex` 使用可增删的多行输入。
- 输入正则时即时校验，非法正则在当前行显示错误。
- 下方实时展示匹配到的 CSP key 列表，包含 key、站点名、homePage。
- 支持“只看命中”“只看排除”“显示全部 CSP”。
- 支持输入关键词过滤 CSP 列表，便于在大量配置里确认匹配范围。

依赖选择：

- 依赖使用可搜索多选，来源是已安装且可被依赖的扩展。
- 支持版本约束输入，例如 `>=1.0.0`。
- 显示依赖图：当前脚本依赖哪些库、库又依赖什么。
- 缺失依赖、版本冲突、循环依赖要在保存前报错。

代码编辑：

- 支持在 App 界面直接新建本地扩展，粘贴 JS 代码后保存为 App 私有目录下的本地脚本文件。
- 本地脚本保存后自动生成或更新清单，`js` 指向 App 内部本地路径，例如 `local://webhome-ext/foo.js`。
- 支持编辑本地脚本文件，保存后下次注入使用新内容；当前页面可提供“试运行/重新注入”按钮。
- 远程脚本默认不直接覆盖编辑；如果用户要改远程脚本，应提供“复制为本地扩展”入口，避免远程更新覆盖本地修改。
- 第一版可用普通多行编辑框，提供保存、撤销、格式化 JSON、校验清单。
- 后续建议内嵌 CodeMirror，提供 JS/JSON 语法高亮、搜索替换、括号匹配、行号、折叠、基础 lint。
- Monaco 能力更强但体积更大，在 Android App 内嵌不是第一优先级。
- 长脚本不建议在 TV 端编辑，TV 端只显示内容摘要和外部更新地址。

测试工具：

- “测试匹配”：输入或选择一个 CSP key，显示将加载哪些扩展和加载顺序。
- “在当前 WebHome 试运行”：只对当前页面临时注入，不写入正式配置。
- “查看注入日志”：显示 runAt、耗时、异常和脚本错误。
- “校验清单”：校验字段完整性、依赖是否满足、正则是否合法。
- “导入/导出”：导出单个扩展 JSON 或从 URL/剪贴板导入。

### 14.3 UI 风格建议

这是设置型工具界面，不是营销页。视觉应保持克制：

- 列表信息密度适中，优先让用户看清扩展状态、匹配范围和依赖状态。
- 状态、异常、依赖状态用小标签表达，不用大面积彩色卡片。
- 错误状态用明确但不过度刺眼的颜色。
- 搜索、筛选、排序放在顶部，日志和调试信息放在详情页或折叠区。
- 手机端保证所有表单项可单手滚动编辑，按钮固定在底部安全区上方。

### 14.4 运行边界：不做 Node/npm 后端运行时

WebHome 扩展第一版只做页面增强脚本，不把 App 做成 Node.js 或服务端渲染平台。

明确不做：

- 不在 App 内集成 Node.js。
- 不在 App 内执行 `npm install`、`pnpm install`、`yarn install`。
- 不支持在手机端安装 npm 依赖、编译 TypeScript、跑 Vite/Webpack/esbuild。
- 不提供常驻后端 JS/TS 服务，不做类似服务端渲染的运行模型。
- 不让扩展启动本地后台进程或长期任务。

原因：

- Node/npm 会显著增加包体、存储、网络、CPU、电量和安全复杂度。
- Android 上安装任意 npm 依赖不可控，原生依赖、二进制包和 postinstall 脚本都会带来维护风险。
- 服务端式运行会改变功能定位，从“WebHome 页面增强”变成“移动端后端平台”，复杂度和风险都不匹配。
- 当前 App 已有 Native SDK、OkHttp 请求、资源网关、网盘检测等能力，扩展脚本调用这些能力即可覆盖主要玩法。

需要 TypeScript 或 npm 生态时，推荐在电脑端构建成一个普通浏览器可运行的 JS 文件，再把构建产物 URL 填到 `js`，或者直接粘贴构建后的 JS 到 App 本地扩展编辑器。

## 15. 加载和缓存策略

推荐流程：

1. App 加载 Vod 配置。
2. 读取扩展订阅源列表。
3. 下载扩展清单，校验 JSON 格式。
4. 下载 JS 入口脚本。
5. 缓存到 App 本地目录。
6. 解析依赖、版本约束和加载顺序。
7. WebHome 切换 CSP 时按 `site.key` 正则匹配扩展。
8. 注入已缓存脚本。网络失败时使用上次缓存版本。

缓存策略：

- 扩展清单和脚本应有独立缓存目录。
- 缓存 key 使用扩展 `id`、资源 URL 和内容版本信息。
- 更新失败不影响旧版本运行。
- 提供“清理扩展缓存”功能。
- 依赖扩展更新后应提示哪些扩展会受影响。

## 16. 调试和可观测性

用户脚本开发者需要知道脚本是否运行。建议提供简单状态面板。

每个 WebHome 页面记录：

- 当前 CSP key。
- 当前 URL。
- 匹配到的扩展列表。
- 每个扩展的 runAt。
- 依赖解析结果和加载顺序。
- 每个扩展是否注入成功。
- JS 异常信息。
- `GM_log` / `fm.ext.log` 输出。
- Native API 调用摘要，例如 `fm.req` 域名、`fm.play` 类型、`fm.check` 数量。
- 限流、超时、响应过大、检测批量过大等运行保护记录。

建议 App 暴露：

```js
fm.ext.info()
fm.ext.log(message, data)
fm.ext.toast(message)
```

设置页可以提供：

- 扩展列表。
- 单个扩展开关。
- 手动更新。
- 查看日志。
- 清除缓存。
- 导出调试报告。

## 17. 与现有 WebHome SDK 的关系

现有 `window.fm` 是 WebHome 页面 SDK。用户脚本扩展应复用它。

建议分层：

```text
Native Bridge: fongmiBridge
        ↓
WebHome SDK: window.fm / window.fongmi
        ↓
Extension Runtime: GM_* / fm.ext
        ↓
Injected Scripts / Tool Scripts
```

第一版可以先复用 `window.fm`。扩展启用后默认可以调用 App 已开放的 WebHome SDK 能力。需要重点保证 SDK 自身稳定，包括参数校验、异常保护、限流和日志。

现有 SDK 需要新增或调整：

- 新增 `fm.ext.info()`。
- 新增 `fm.ext.log()`。
- 新增 `fm.ext.toast()`。
- 新增脚本级 cache namespace。
- `fm.open()` 可以先由工具脚本补充，成熟后再考虑 Native 内置。
- 当前 `injectSdk()` 从 `onPageFinished()` 拆分为可复用脚本，支持 document-start 注入。

## 18. 安全策略

必须明确：用户脚本扩展是高风险能力，应默认以显式启用为前提，但不需要做权限管理。

最低安全要求：

- 本地和远程扩展使用同一机制，新增扩展导入后不自动运行。
- 首次启用扩展时展示来源、匹配 CSP key、依赖、更新地址和校验状态。
- 远程来源建议要求 HTTPS，本地来源也要显示文件或配置来源。
- 完整性校验作为订阅源平台能力后续补充。
- 来源、依赖或匹配范围扩大必须重新确认。
- 脚本存储按扩展隔离。
- 扩展日志可查看。
- 设置页允许一键禁用全部扩展。
- 扩展不能默认覆盖核心 `fm`。
- Native 请求、资源网关、网盘检测、播放调用必须有全局限流和异常保护。

需要避免：

- 让扩展静默启用在所有 CSP。
- 用 URL 匹配替代 CSP key 匹配。
- 让扩展系统承担绕过验证码/风控的功能定位。
- 让某个扩展无提示地成为所有脚本的隐式依赖。
- 把完整观看历史、原始 Cookie、任意文件访问、剪贴板读取等强敏感能力直接放进第一版 SDK。

## 19. 推荐实施阶段

### 阶段一：最小可用能力

目标：让开发者能写脚本增强 WebHome。

实现：

- 扩展清单模型。
- 支持界面新建本地扩展，直接粘贴 JS 代码保存成本地脚本文件。
- 统一扩展来源列表，支持本地和远程来源，但启用确认机制一致。
- CSP key 正则匹配。
- `document-end` JS 入口注入。
- `GM_addStyle`、`GM_log`、`GM_getValue`、`GM_setValue`。
- 复用现有 `fm.play`、`fm.req`、`fm.res`。
- 简单日志。

这个阶段不强求 document-start，改动较小，能覆盖移动端样式修复、按钮绑定、普通链接接管。

### 阶段二：工具脚本和链接接管

目标：形成可复用的脚本级 SDK。

实现：

- 增加 `depends`、版本约束和依赖解析。
- 提供系统示例工具脚本 `fm-open-lib`。
- 支持 `fm.open()` 由工具脚本补充或由 Native 内置。

### 阶段三：增强注入和运行体验

目标：接近 Tampermonkey 脚本增强体验。

实现：

- 引入 `androidx.webkit:webkit`。
- 支持 `document-start`，不支持时降级。
- 支持 `document-idle`。
- 支持 SPA URL 变化事件。
- 支持脚本依赖缓存。
- 增加页面恢复后重新注入和状态恢复机制。

### 阶段四：完整管理能力

目标：形成可长期开放给开发者的扩展生态基础。

实现：

- 扩展管理页。
- 扩展编辑页。
- 本地脚本编辑、试运行和复制远程脚本为本地扩展。
- 远程订阅更新。
- 单扩展开关。
- CSP key 正则实时匹配预览。
- 依赖选择和依赖图。
- 错误日志面板。
- 清理缓存。
- 文档和示例脚本。

## 20. 当前项目改造点清单

### 20.1 `HomeWebController`

需要改造：

- 保存当前 `Site` 上下文，而不仅是 `homePage`。
- 在 `load(Site site)` 时调用扩展匹配器。
- 在 `onPageStarted()` 注入 document-start 脚本或准备上下文。
- 在 `onPageFinished()` 注入 SDK、document-end、document-idle 脚本。
- 在 `onRenderProcessGone()` 重建 WebView 后重新注册 document-start 脚本。
- 在 `onResume()` 触发 `fmresume` 后通知扩展恢复。
- 页面恢复时不直接刷新主页，尽量恢复当前 WebView 状态和已注入扩展上下文。

### 20.2 `HomeWebBridge`

需要改造：

- 增加扩展调用上下文：扩展 ID、当前 CSP key、当前 URL。
- 不做扩展级 SDK 能力拦截。
- 扩展 cache 使用 `cache_ext_{id}_{key}`。
- 增加日志方法。
- 对 `fm.req`、`fm.res`、`fm.play`、`fm.check` 做全局限流、参数校验、异常保护和日志。

### 20.3 `Site`

第一版不必改。后续可选增加：

```json
{
  "extensions": {
    "enable": true,
    "include": [],
    "exclude": []
  }
}
```

### 20.4 设置界面

需要新增：

- “WebHome 扩展”入口。
- 扩展总开关。
- 扩展列表、搜索、筛选。
- 单扩展开关。
- 启用确认页。
- 依赖详情。
- CSP key 正则匹配预览。
- 日志面板。
- 扩展编辑器入口。

### 20.5 Gradle 依赖

如果实现 document-start，建议增加：

```gradle
implementation "androidx.webkit:webkit:<latest-stable>"
```

并在运行时判断：

```java
WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
```

如果实现代码编辑器，后续可以选择：

- 内嵌 CodeMirror 静态资源，适合手机端。
- 外部网页编辑器，App 只导入导出配置。
- 不建议第一版直接内嵌 Monaco，除非确认体积和 WebView 性能可接受。

## 21. 不建议的设计

不建议：一个 CSP 只能绑定一个扩展。

原因：

- 无法复用通用能力。
- 一个站点经常需要多个增强脚本组合。
- 通用播放链接接管、通用移动端修复、站点专用脚本是不同职责。

不建议：App 层按网页 URL 匹配扩展。

原因：

- 与 CSP 配置体系不一致。
- 容易受跳转、验证页、多域名影响。
- 同一个域名可能被多个 CSP 以不同方式使用。

不建议：让开发者填写 SDK 能力声明。

原因：

- WebHome 扩展通常需要组合使用多个 SDK 能力，字段会变成形式主义。
- 开发者和用户都要理解大量能力项，实际收益不高。
- 更简单的做法是启用即开放 WebHome SDK，SDK 自身做全局限制和日志。

不建议：第三方扩展随意覆盖核心 `fm`。

原因：

- 用户无法区分 Native API 和第三方脚本补充的 API。
- 会增加脚本互相污染和兼容风险。
- 更合理的做法是工具脚本只补缺失 helper，例如 `if (!fm.open) fm.open = ...`，不覆盖已有 Native 方法。

不建议：在 App 内集成 Node/npm 或服务端式 JS/TS 运行时。

原因：

- 扩展目标是方便增强 WebHome 页面，不是把手机 App 变成后端运行平台。
- npm 依赖安装、TypeScript 编译、打包工具和 postinstall 脚本会显著增加包体、性能、安全和维护成本。
- 需要 npm/TS 时应在电脑端构建成浏览器 JS，再通过 URL 或本地粘贴方式加载。

不建议：第一版实现完整 Chrome 插件模型。

原因：

- Android WebView 没有完整浏览器扩展隔离环境。
- 后台脚本、WebRequest 修改、标签页管理都不是当前 WebHome 核心需求。
- 过度设计会拖慢落地。

## 22. 最终推荐方案

最终推荐定位：

```text
WebHome 用户脚本系统：App 提供脚本匹配、注入、缓存、日志、依赖解析和完整 WebHome SDK；脚本由第三方开发者独立开发，加载范围只由 CSP key 正则决定；通用高级能力通过普通工具脚本复用；不做扩展类型配置，也不做扩展权限管理。
```

推荐第一版落地范围：

- 只按 `site.key` 正则匹配。
- 支持多扩展同时命中一个 CSP。
- 支持一个扩展命中多个 CSP。
- 支持 JS 入口注入。
- 支持 App 界面直接粘贴 JS 代码，保存成本地扩展脚本。
- 不集成 Node/npm，不提供服务端式 JS/TS 后端运行。
- 不设计扩展类型字段。
- 不设计独立 CSS 字段，样式由 JS 处理。
- 不设计 SDK 能力声明字段。
- 不把作者、主页、命名空间、启用状态、iframe、优先级、资源模型、校验 hash 等非必要信息放进第一版主清单。
- 优先实现 `document-end`，随后补 `document-start`。
- 提供最小 GM 兼容 API：`GM_addStyle`、`GM_log`、`GM_getValue`、`GM_setValue`、`GM_xmlhttpRequest`。
- 提供扩展日志和一键禁用。

推荐第二阶段补齐：

- 支持 `depends` 和版本约束。
- 用系统工具脚本实现 `fm.open()`，成熟后可选择 Native 内置。
- 提供依赖选择、CSP key 正则匹配预览和扩展编辑器。

这个方案能满足：

- 把普通网站作为 WebHome 后增强移动端体验。
- 用户手动通过验证后增强页面。
- 点击播放、网盘、磁力链接后调用 App 播放。
- 不把具体站点脚本写进项目。
- 让用户脚本开发者围绕 CSP key 独立发布适配脚本。
- 让通用 SDK 能力由工具脚本演进，App 保持底层能力稳定、限制合理和日志可查。
