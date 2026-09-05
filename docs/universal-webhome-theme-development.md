# 全局 WebHome 皮肤开发指南

> 实现状态：V1 首页兼容层已实现（2026-07-28）<br>
> 内置示例：`Eclipse`<br>
> 本文协议范围：WebHome VOD Contract V1

> **文档范围说明（2026-08-03）**：本文主要记录旧的 V1 单 HTML 首页协议和兼容开发方式。当前应用已经实现基于 `theme.json` 的 WebTheme V2、Host API 3、`HOME`/`DETAIL` 页面、页面级权限和安全远程桥接。V2 已落地边界见 [`universal-webhome-theme-design.md`](universal-webhome-theme-design.md) §18～§19；契约收口的实施状态以及公共 Runtime、列表页、原生设计变量与分发计划见同文档 §20。新增远程主题应优先采用 V2，V1 仅作为现有首页兼容层继续维护。
> **V2 校验入口（2026-08-03）**：新增或修改 `theme.json` 时，先参考 [`webhome-devkit/schemas/webtheme-v2.schema.json`](../webhome-devkit/schemas/webtheme-v2.schema.json)，并运行 [`webhome-devkit/scripts/validate_webtheme.py`](../webhome-devkit/scripts/validate_webtheme.py)；命令示例见 [`webhome-devkit/README.md`](../webhome-devkit/README.md) 的“WebTheme V2 Manifest 校验”章节。

本文说明如何为 WebHTV 开发一套可同时运行在手机版和电视版的全局 WebHome 皮肤，以及应用侧的选择优先级、桥接接口、数据结构、兼容性和调试方法。

## 1. 功能定位

全局 WebHome 皮肤是一个 HTML 首页。它不自带跨站聚合逻辑，而是通过应用注入的 JavaScript SDK 读取**当前选中的 VOD 内容源**，并调用原生搜索、详情和播放器。

这使皮肤和内容源解耦：

- 换内容源时，皮肤不变，首页数据自动换成新内容源。
- 同一份皮肤可用于手机和 Android TV。
- 内容请求继续由应用现有 `SiteApi`、Spider、Cookie、代理和播放器链路处理。
- 皮肤只得到稳定、裁剪后的 V1 DTO，不直接访问应用内部 Java/Kotlin Bean。

内置示例文件：

```text
app/src/main/assets/webhome/eclipse.html
```

内置 URL：

```text
file:///android_asset/webhome/eclipse.html
```

## 2. 用户如何启用

手机版和电视版都提供相同入口：

```text
设置 → 增强设置 → 全局 WebHome 皮肤
```

可选项：

1. **关闭**：不使用全局皮肤。
2. **Eclipse 内置皮肤**：使用随 APK 发布的离线演示皮肤。
3. **自定义网址**：输入 `https://` 地址。

设置会立即发送首页刷新事件，不要求重启应用。偏好项为：

```text
web_home_theme_enabled
web_home_theme_url
web_home_theme_trusted_url
```

前两项已经加入应用设置备份白名单。`web_home_theme_trusted_url` 是本机同意记录，不进入备份；从备份恢复远程 URL 后必须在设置中重新确认，解析器才会加载它。

## 3. 加载优先级

首页目标按以下顺序解析：

```text
内容源自己的 homePage
    > 已启用的全局 WebHome 皮肤
    > 原生首页
```

具体规则：

1. 当前内容源声明了 `homePage` 时，保持现有站点首页行为，全局皮肤不覆盖它。
2. 内容源没有 `homePage`，且全局皮肤已启用时，加载全局皮肤。
3. 全局皮肤 URL 为空时，自动使用内置 Eclipse。
4. URL 非法、WebView 主文档加载失败，或二次超时恢复仍失败时，回退原生首页。
5. 切换内容源时，即使全局皮肤 URL 不变，也会按内容源 identity 重新加载，避免显示旧源数据。

应用内部只接受三类目标：

- 自定义远程主题：`https://`
- 内置主题：固定的 `file:///android_asset/webhome/eclipse.html`

设置对话框只允许用户配置语法有效、包含主机名的 HTTPS 地址。HTTP、localhost/`.local`、loopback/私网/链路本地/保留地址的字面 IP（包括非常规数字写法）和任意 `file://` 目标都会被拒绝；`android_asset` 仅允许应用内置的 Eclipse 资源。首次启用或更换远程 URL 时，还会显示来源域名与风险确认。

## 4. 安全边界

全局皮肤和“内容源自带首页”采用不同信任模式。

### 4.1 当前源隔离

全局皮肤调用 `vod.home`、`vod.category`、`player.playVod` 时，只能使用当前选中的内容源：

- `vod.home` 始终读取当前源。
- `vod.category` 始终读取当前源。
- `player.playVod` 的 `siteKey` 必须为空或等于当前源 key；不能借此访问其他源。
- 全局模式不向页面暴露全量站点配置、扩展代码或敏感请求头。

因此，皮肤是“当前源的视图”，不是任意跨源查询器。

### 4.2 站点扩展隔离

内容源自带首页继续保留站点扩展注入能力。全局皮肤则：

- 不注入当前站点的 WebHome extension 脚本；
- 不继承站点首页专用 headers；
- 不执行站点专用 document-start 扩展；
- 只通过公开 SDK 调用受控能力。

### 4.3 远程皮肤风险

远程 HTML 本质上仍是可执行代码。应用会把它绑定到配置 URL 的精确 Origin，只接受主 frame 消息，并使用单独的最小能力 Bridge。每次远程会话固定监听 generation，每次主文档导航轮换 nonce；旧文档和已经失效的异步结果即使同源也不能沿用新会话。iframe、跨 Origin 导航、HTTP 和被拒绝的本地/字面 IP 地址不能获得原生能力。主题侧仍应：

- 只配置自己维护或信任的 HTTPS 地址；
- 不在页面中保存账号、Cookie、Token 或内容源配置；
- 不依赖第三方统计、广告或动态脚本；
- 发布时固定版本并保留可回滚 URL；
- 页面失效时关闭全局皮肤即可恢复原生首页。

## 5. SDK 就绪方式

SDK 在 WebView 页面加载过程中注入。不要假设 `<head>` 执行时它已经存在；同时处理当前已就绪和后续 `fmsdk` 事件。

```html
<script>
(function () {
  function start() {
    if (!window.fm || !window.fm.vodHome) return;
    window.fm.vodHome({}).then(function (home) {
      renderHome(home);
    }).catch(function (error) {
      showError(error && error.message ? error.message : '加载失败');
    });
  }

  window.addEventListener('fmsdk', start, false);
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, false);
  } else {
    start();
  }
}());
</script>
```

为了兼容旧电视 WebView，示例刻意使用 ES5 函数语法。业务脚本最高按 ES2017 编写，不要使用可选链、空值合并、逻辑赋值、JavaScript module 或正则 lookbehind。

## 6. VOD API

### 6.1 读取首页

```javascript
fm.vodHome(options) -> Promise<HomeResultV1>
```

当前 V1 不要求任何 option：

```javascript
fm.vodHome({}).then(function (result) {
  console.log(result.source.name);
  console.log(result.classes);
  console.log(result.items);
});
```

### 6.2 读取分类

```javascript
fm.vodCategory(typeId, page, options) -> Promise<CategoryResultV1>
```

示例：

```javascript
fm.vodCategory('movie', 1, {
  filter: true,
  extend: {
    area: '中国大陆',
    year: '2026'
  }
}).then(function (result) {
  appendCards(result.items);
  updateAutoPagination(result.hasMore);
});
```

约束：

- `typeId` 必填。
- `page` 必须是 `1..10000` 的整数；越界值以 `INVALID_ARGUMENT` 拒绝。
- `filter` 表示是否启用内容源筛选。
- `extend` 是筛选 key/value 字典。
- 返回值中的 `query` 是实际执行的规范化查询。

### 6.3 打开 VOD

```javascript
fm.vod(siteKey, vodId, title, pic, options) -> Promise<object>
```

```javascript
fm.vod(item.siteKey, item.vodId, item.name, item.pic, {
  content: item.content || '',
  remarks: item.remarks || ''
});
```

调用后由原生应用进入详情/播放链路。全局皮肤中 `siteKey` 应直接使用 DTO 中的 `item.siteKey`，不要自行拼接。

### 6.4 图片代理

```javascript
fm.res(url, options) -> string
```

`fm.res` 仅向受信站点首页和内置 Eclipse 主题提供。远程主题没有原生图片代理，应直接使用 DTO 中的 HTTPS 图片地址，并依赖 WebView 自身的网络与同源策略：

```javascript
    image.src = item.pic || fallbackImage;
```

必须设置 `onerror` 本地占位图，避免源图片失效造成空白卡片。

### 6.5 原生入口

```javascript
fm.search(keyword, options)  // 打开原生搜索
fm.openVod()                 // 打开 VOD/媒体库
fm.openSetting()             // 打开设置
fm.ui.getViewport()          // 获取 WebHome 可用视口
```

这些函数均返回 Promise，调用方应处理 rejection。V1 兼容层继续把原始失败文本放在 `Error.message`。V2 的受信页面与远程页面都会额外设置稳定的 `Error.code`：`PERMISSION_DENIED`、`INVALID_ARGUMENT`、`SOURCE_CHANGED`、`STALE_REFERENCE`、`PAGE_UNAVAILABLE`、`NATIVE_FALLBACK`、`RATE_LIMITED`、`RESPONSE_TOO_LARGE`、`INVALID_REQUEST` 或 `REQUEST_FAILED`；为兼容旧主题，`Error.message` 仍可能是 `INVALID_ARGUMENT`、`BUSY` 或 `UNAVAILABLE` 等旧别名。新主题应优先判断 `Error.code`：`RATE_LIMITED` 可在短暂退避后重试，`SOURCE_CHANGED` 表示必须丢弃旧请求并重新读取当前页面状态，`STALE_REFERENCE` 表示重新从最新 DTO 获取不透明引用。完整 Bridge 中的 `net.*`、`cache.*`、`ext.*`、`device.info`、任意 URL 播放和 UI chrome 修改不会暴露给远程主题。

远程边界还会限制请求和响应资源：请求体最多 64 KiB，同时只有少量原生取数任务可在途，单次响应最多 1 MiB（UTF-8），`items` 最多返回 500 条。超过条数时根对象的 `truncated` 为 `true`；主题应提示用户缩小筛选范围，而不是假设数据完整。

## 7. V1 数据结构

### 7.1 首页根对象

```json
{
  "version": 1,
  "source": {},
  "client": {},
  "classes": [],
  "filters": {},
  "items": [],
  "truncated": false,
  "capabilities": {}
}
```

### 7.2 `source`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | string | 当前内容源唯一 key |
| `name` | string | 当前内容源显示名 |
| `type` | number | 内容源类型 |

### 7.3 `client`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `isLeanback` | boolean | 是否电视版 |
| `isLandscape` | boolean | 当前视口是否横屏 |
| `suggestedColumns` | number | 应用建议列数，最小为 1 |

皮肤可以使用这些值选默认布局，但仍应提供 CSS 响应式兜底。

### 7.4 `classes[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `typeId` | string | 传给 `vodCategory` 的分类 ID |
| `typeName` | string | 分类显示名 |
| `typeFlag` | string | 内容源附加 flag |
| `folder` | boolean | 是否目录型分类 |
| `filter` | boolean | 分类是否声明筛选能力 |
| `style` | object | 分类建议样式 |

### 7.5 `filters`

`filters` 是以 `typeId` 为 key 的对象：

```json
{
  "movie": [
    {
      "key": "year",
      "name": "年份",
      "init": "",
      "values": [
        { "name": "全部", "value": "", "selected": true },
        { "name": "2026", "value": "2026", "selected": false }
      ]
    }
  ]
}
```

选择筛选值后，把 `filter.key -> value.value` 写入 `vodCategory` 的 `options.extend`。

### 7.6 `items[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `index` | number | 当前返回列表中的稳定顺序 |
| `kind` | string | `vod`、`folder` 或 `action` |
| `vodId` | string | 内容 ID；播放时原样回传 |
| `siteKey` | string | 当前内容源 key；播放时原样回传 |
| `name` | string | 标题 |
| `pic` | string | 海报 URL |
| `remarks` | string | 更新集数、清晰度等角标 |
| `year` | string | 年份 |
| `typeName` | string | 类型名 |
| `area` | string | 地区 |
| `director` | string | 导演 |
| `actor` | string | 演员 |
| `content` | string | 简介 |
| `action` | string | 仅 `kind=action` 时可能存在 |
| `style` | object | `{ "type": "rect", "ratio": 0.714 }` 等建议样式 |

建议处理方式：

- `vod`：调用 `fm.vod(...)`。
- `folder`：可把 `vodId` 作为新的分类 ID 请求 `fm.vodCategory(...)`。
- `action`：仅在明确理解内容源 action 语义时处理；否则保守地显示为入口，不执行任意脚本。

所有字符串都可能为空。页面不得把标题、简介或 action 直接拼进 `innerHTML`；优先使用 `textContent`，防止内容源文本破坏 DOM。

### 7.7 `capabilities`

```json
{
  "category": true,
  "filters": true,
  "recommend": true
}
```

| 字段 | 含义 |
| --- | --- |
| `category` | 首页返回了分类 |
| `filters` | 返回了筛选项，或分类声明可筛选 |
| `recommend` | 首页返回了推荐列表 |

### 7.8 分类返回对象

```json
{
  "version": 1,
  "source": {},
  "client": {},
  "query": {
    "typeId": "movie",
    "filter": true,
    "extend": { "year": "2026" }
  },
  "page": 1,
  "pageCount": 12,
  "hasMore": true,
  "items": [],
  "truncated": false,
  "capabilities": {}
}
```

自动分页应以 `hasMore` 为首要依据：滚动接近列表末尾或 TV 焦点进入最后一行卡片时预取下一页。加载期间必须防止重复请求，且不要假设所有内容源都会返回准确的 `pageCount`。`pageCount == 0` 表示未知：非空页仍可继续请求，空页才停止。不应让“加载更多”成为遥控器焦点终点。

### 7.9 内容源忠实性与重复分类

`vod.category` 忠实返回当前内容源的 `categoryContent` 结果，不在桥接层按分类名二次切分、去重或伪造数据。`query.typeId`、`query.filter` 和 `query.extend` 可用于确认原生实际执行的规范化请求。

若不同 `typeId` 返回逐条完全相同的第一页列表，通常表示内容源插件没有使用分类 ID，或源站降级到了统一推荐列表。皮肤应继续展示原始数据，并明确提示“当前源返回了相同分类结果”；不要在前端随机拆分推荐列表冒充真实分类。内置 Eclipse 皮肤会对不同分类的完整条目指纹做源内比较，只提示而不改写结果。

## 8. 手机与电视布局

一份 HTML 同时服务触屏和遥控器，不能只做“网页响应式”。

### 8.1 手机要求

- 竖屏至少保证 320 CSS px 可用宽度。
- 海报建议 3 列；极端窄屏可降为 2 列。
- 点击区域至少约 40 CSS px。
- 横向分类、筛选条允许滚动，但正文不能横向溢出。
- 搜索输入框不能被方向键处理逻辑破坏正常光标移动。

### 8.2 电视要求

- 1920×1080 目标布局通常为 6 列。
- 每个可操作元素必须有清晰 `:focus` 状态，不能只写 `:hover`。
- 焦点边框与内容背景应有足够对比度。
- 遥控器上下左右必须有确定目标；不能完全依赖浏览器默认 Tab 顺序。
- 分类栏及每一行筛选项应是独立横向焦点行：左右键只在当前行移动，上下键负责跨行。
- 列表重新渲染、翻页和从播放器返回时，应按稳定 key 恢复焦点。
- 焦点移动后主动保证元素位于安全视区，避免聚焦到屏幕外。

Eclipse 使用 `data-focus` 标识焦点、`data-focus-row` 约束分类与筛选的横向导航、几何方向搜索处理跨区移动，并通过 `sessionStorage` 恢复焦点。自定义皮肤可以采用其他实现，但必须在真实 TV WebView 验证。

## 9. 旧 WebView 兼容规则

项目最低 Android 版本为 24，电视固件可能长期停留在旧 Chromium/WebView。皮肤按以下基线开发：

### JavaScript

允许使用 ES2017 范围内、且目标 WebView 已验证的能力。禁止或必须转译：

- optional chaining：`a?.b`
- nullish coalescing：`a ?? b`
- logical assignment：`||=`、`&&=`、`??=`
- private fields
- top-level await
- JavaScript modules
- regex lookbehind
- optional catch binding

第一个 `<script>` 必须是 ES5 兼容 bootstrap，在业务代码前提供必要 polyfill 或能力标记。

### CSS

避免把以下能力作为关键布局的唯一实现：

- `gap`
- `aspect-ratio`
- `min()`、`max()`、`clamp()`
- `:is()`、`:where()`、`:has()`、`:focus-visible`
- `content-visibility`
- 仅依赖 `backdrop-filter` 的可读背景

推荐使用 flex + margin、padding 百分比海报占位和普通 `:focus`。

### 自动检查

每次修改皮肤后运行：

```powershell
py -3 webhome-devkit/skills/webhome-homepage-builder/scripts/check_webhome_compat.py app/src/main/assets/webhome/eclipse.html
```

期望结果：

```text
0 error(s), 0 warning(s)
```

## 10. 最小完整皮肤骨架

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>My WebHome</title>
  <script>
    (function () {
      /* 在这里放 ES5 bootstrap/polyfill。 */
    }());
  </script>
  <style>
    .card:focus { outline: 3px solid #fff; }
  </style>
</head>
<body>
  <nav id="classes"></nav>
  <main id="items"></main>
  <script>
    (function () {
      'use strict';
      var home;

      function clear(element) {
        while (element.firstChild) element.removeChild(element.firstChild);
      }

      function play(item) {
        fm.vod(item.siteKey, item.vodId, item.name, item.pic, {})
          .catch(function (error) { console.error(error); });
      }

      function renderItems(items) {
        var root = document.getElementById('items');
        clear(root);
        items.forEach(function (item) {
          var button = document.createElement('button');
          button.className = 'card';
          button.type = 'button';
          button.textContent = item.name || '未命名';
          button.addEventListener('click', function () { play(item); });
          root.appendChild(button);
        });
      }

      function render(result) {
        home = result;
        renderItems(result.items || []);
      }

      function start() {
        if (!window.fm || !fm.vodHome) return;
        fm.vodHome({}).then(render).catch(function (error) {
          console.error(error);
        });
      }

      window.addEventListener('fmsdk', start, false);
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start, false);
      } else {
        start();
      }
    }());
  </script>
</body>
</html>
```

生产皮肤还需要补充图片失败占位、加载态、空态、筛选、分页、方向键焦点、焦点恢复和浏览器预览数据。

## 11. Eclipse 示例说明

Eclipse 是一个完全离线的内置资源包：

- 不请求外部字体、图标、CSS 或 JavaScript；
- 自带 SVG data URL 海报作为离线回退；
- 浏览器直接打开时展示演示数据；
- 应用 SDK 就绪后自动替换为当前内容源数据；
- 支持分类、筛选、自动分页、搜索、原生媒体库与设置入口；
- 手机使用 3 列卡片，电视宽屏使用 6 列卡片；
- 提供明确的 D-pad 几何焦点移动和焦点恢复；
- 页面脚本不使用可选链、空值合并或 module；
- 已通过项目 WebHome 兼容性检查器。

它是协议和交互演示，不是要求第三方皮肤复制的固定视觉模板。

## 12. 应用侧代码索引

| 文件 | 作用 |
| --- | --- |
| `WebHomeTarget.java` | 解析站点首页/全局皮肤/原生回退优先级 |
| `WebHomeVodContract.java` | 把内部 VOD 数据映射为稳定 V1 DTO |
| `HomeWebBridge.java` | 受信站点首页与内置主题的完整 Bridge |
| `WebHomeThemeBridge.java` | 远程主题的当前源数据、播放和受控导航白名单 |
| `WebHomeThemePolicy.java` | 远程消息 Origin、主 frame 与方法权限校验 |
| `HomeWebController.java` | WebView 生命周期、SDK 注入、目标 identity 和错误回退 |
| `WebHomeThemeDialog.java` | 手机/电视共用的皮肤选择和自定义 URL 对话框 |
| `Setting.java` | 全局皮肤偏好读写 |
| `assets/webhome/eclipse.html` | 内置 Eclipse 示例 |

## 13. 开发与验证流程

### 13.1 单元测试

先运行 Eclipse 分类诊断的 JavaScript 回归测试：

```powershell
node --test app/src/test/js/eclipse-category-diagnostics.test.js `
  app/src/test/js/eclipse-focus-navigation.test.js `
  app/src/test/js/eclipse-auto-pagination.test.js
```

再运行 WebHome 聚焦测试：

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest `
  --tests "com.fongmi.android.tv.web.WebHomeTargetTest" `
  --tests "com.fongmi.android.tv.web.WebHomeVodContractTest" `
  --tests "com.fongmi.android.tv.web.HomeWebMediaLifecycleTest" `
  --tests "com.fongmi.android.tv.web.WebHomeInlineVodStoreTest" `
  --tests "com.fongmi.android.tv.bean.BackupPreferenceFilterTest"

.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest `
  --tests "com.fongmi.android.tv.web.WebHomeTargetTest" `
  --tests "com.fongmi.android.tv.web.WebHomeVodContractTest" `
  --tests "com.fongmi.android.tv.web.HomeWebMediaLifecycleTest" `
  --tests "com.fongmi.android.tv.web.WebHomeInlineVodStoreTest" `
  --tests "com.fongmi.android.tv.bean.BackupPreferenceFilterTest"
```

需要做全项目回归时，再分别运行不带 `--tests` 的两个完整单元测试任务，并按当前分支基线判断非 WebHome 失败。

重点测试：

- 站点首页高于全局皮肤；
- 全局皮肤关闭时返回原生首页；
- 空 URL 回退 Eclipse；
- 非法 scheme 被拒绝；
- 内容源切换会改变全局皮肤 identity；
- DTO 字段、空数据和分页规范化；
- 新设置项可进入设置备份。

### 13.2 构建

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug
```

### 13.3 真机/模拟器检查表

手机版：

- 设置中可选择 Eclipse、远程 URL、关闭；
- 启用后立即刷新为皮肤首页；
- 竖屏卡片不溢出，触摸可打开内容；
- 分类、筛选、搜索和滚动接近末尾自动翻页可用；
- 切换内容源后标题和数据更新；
- 远程 URL 主文档错误时回退原生首页。

电视版：

- 设置页选项可用遥控器操作；
- 首页初始焦点可见；
- 上下左右能遍历顶栏、分类、筛选和卡片，焦点不会停在分页控件；
- 焦点进入最后一行卡片时会自动预取下一页；
- 跨行移动不会跳到不可见元素；
- 打开内容后能进入原生播放/详情；
- 返回首页后焦点尽量恢复；
- 1080p 下卡片、文字和焦点环清晰。

### 13.4 日志关键词

WebHome 相关日志主要使用：

```text
webhome
webhome-webview
AndroidRuntime
```

主文档错误、加载超时、桥接 rejection 和播放参数校验失败都应先从这些日志定位。

## 14. 发布建议

1. 优先发布为单文件 HTML，减少资源跨域、缓存和离线问题。
2. 远程皮肤 URL 使用 HTTPS，并提供版本化路径。
3. 保留浏览器回退数据，方便在普通浏览器调视觉，但不要把它误当真实数据。
4. 不要根据内部 Bean 的偶然字段开发；只依赖本文 V1 DTO。
5. 对未知字段宽容，对缺失字段提供默认值。
6. 协议未来增加字段时保持向后兼容；破坏性变化必须提升 `version`。
7. 发布前至少在一台手机 WebView 和一台 Android TV WebView 上执行完整检查表。
