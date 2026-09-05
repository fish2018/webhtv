# 全局可配置主题（Universal Web Theme / WebHome V2）设计文档

> 状态：🟡 V1 首页兼容层与 V2 `HOME`/`DETAIL` 已实现，后续页面处于规划阶段<br>
> 首次设计：2026-07-27<br>
> 本次更新：2026-08-05<br>
> 适用端：mobile / leanback（Android TV）<br>
> 当前落地范围：全局 WebHome 首页、WebTheme V2 Manifest、当前内容源取数、分类筛选、详情页、TMDB 渐进增强、遥控焦点和原生播放器入口<br>
> 后续规划范围：搜索结果页、收藏/历史页、播放器控制层、设置页及全局设计变量<br>
> 关联文档：`docs/universal-webhome-theme-development.md`<br>
> 说明：羊壳 `.rfwtxt` 仅作为效果和架构参考，不支持其格式/语法，也不规划直接兼容

## 0. 结论

后续可以把当前“全局首页皮肤”扩展为覆盖详情、搜索、收藏、历史、播放控制和设置等页面的“全应用主题系统”，但不应简单地把所有原生页面全部替换成 WebView。

推荐采用以下边界：

- **页面视觉与布局可替换**：首页、列表、搜索、详情、收藏、历史等内容页面允许主题完全接管渲染。
- **播放器内核保持原生**：解码、DRM、字幕、音轨、播放状态机、画中画和生命周期继续由原生负责；主题只定制播放器控制层和视觉。
- **设置业务保持原生**：主题可以渲染设置页，但配置定义、权限校验、值校验和持久化必须由原生负责。
- **所有页面独立回退**：主题没有声明某个页面、加载失败或协议不兼容时，仅该页面回退原生，不影响其他页面。
- **桥接接口按页面授权**：远程主题视为不可信代码，只能调用当前页面所需的受控能力。

因此，整体方向不是“把 App 改成一个大网页”，而是：

> **保留原生数据、业务和播放能力，把页面渲染层做成可插拔主题。**

当前 WebHome V1 已验证“同一份皮肤读取不同内容源”的核心可行性。下一步最合适的是先建设 WebTheme V2 公共宿主，再实现 Web 详情页 MVP。

---

## 1. 背景与当前基础

### 1.1 参考模型：远程 UI 主题

羊壳 `.rfwtxt` 的关键价值不在具体 DSL，而在它把主题拆成了三部分：

1. **宿主能力**：数据请求、图片、播放、导航和系统能力由 App 提供。
2. **稳定数据契约**：主题只消费宿主提供的结构化数据。
3. **稳定事件契约**：主题通过明确事件请求打开内容、切换分类或播放。

主题本身不需要理解 JAR、JS、Python 等爬虫差异，也不应该直接依赖应用内部 Bean。只要所有内容源最终被原生归一化为同一份 DTO，同一主题就能跨源工作。

本项目继续使用自己的 HTML/JavaScript 载体和桥接协议，不引入 Flutter Engine，也不兼容 `.rfwtxt`。

### 1.2 WebHome V1 已实现能力

当前工作区已经完成 WebHome 首页阶段的主要能力：

- 全局 WebHome 皮肤设置，支持内置 Eclipse 和自定义 HTTPS 地址。
- 首页加载优先级：

```text
内容源自己的 homePage
    > 已启用的全局 WebHome 皮肤
    > 原生首页
```

- `vod.home`：通过当前内容源读取首页分类、筛选和条目。
- `vod.category`：按分类、页码和筛选条件读取真实分页数据。
- 当前内容源隔离：全局主题不能通过伪造 `siteKey` 跨源读取或播放。
- `player.playVod`、`player.playVodInline`、`player.playUrl` 等原生播放入口。
- 搜索、媒体库、设置、直播和返回等原生导航入口。
- mobile / leanback 双端加载、错误回退和页面 identity 刷新。
- Eclipse 示例主题的分类筛选、自动分页和电视遥控焦点。
- V1 DTO、目标解析和关键交互的 Java/JavaScript 回归测试。

主要代码位置：

| 文件 | 当前职责 |
| --- | --- |
| `WebHomeTarget.java` | 解析站点首页、全局主题和原生首页优先级 |
| `WebHomeVodContract.java` | 将内部 `Result` / `Vod` 映射为稳定的 V1 DTO |
| `HomeWebBridge.java` | 受信站点首页和内置主题使用的完整桥接 |
| `WebHomeThemeBridge.java` | 不可信远程主题使用的最小能力桥接 |
| `HomeWebController.java` | WebView 生命周期、SDK 注入、目标切换和错误回退 |
| `WebHomeThemeDialog.java` | 双端共用的主题选择与 URL 配置 |
| `assets/webhome/eclipse.html` | 内置示例主题 |

实现和第三方主题开发细节以 `docs/universal-webhome-theme-development.md` 为准。

### 1.3 V1 尚未解决的问题

V1 是首页专用实现，还不能自然覆盖整个应用：

- `HomeWebController` 和 `HomeWebBridge` 的语义仍然绑定 Home。
- 当前只有 `vod.home`、`vod.category`，没有稳定的 `vod.detail`、`vod.search` 等数据接口。
- `app.search`、`app.openSetting` 等接口只能跳转原生页面，不能让主题渲染这些页面。
- 没有多页面主题 Manifest、页面路由、页面级权限和协议能力协商。
- 详情页、播放器和设置页包含大量原生状态，不能直接复用首页的扁平列表模型。
- 电视焦点逻辑目前由单个主题实现，尚未形成跨页面统一规范。
- `HomeWebBridge` 如果继续直接增加所有页面接口，会逐渐变成难以维护的“大桥”。

### 1.4 现有原生页面的约束

以下页面已经有成熟而复杂的原生实现：

- 详情页：`TmdbDetailActivity` 及其多个 detail mode controller。
- 播放页：mobile 和 leanback 各自的 `VideoActivity`，并依赖原生播放服务。
- 设置页：mobile fragment 与 leanback activity 两套页面组织。

这些页面不仅负责布局，还包含来源线路、选集、历史、收藏、播放恢复、遥控器、生命周期、权限和配置校验等逻辑。V2 的目标是把它们的“显示层”逐步可替换，而不是复制或绕开其核心业务。

---

## 2. 目标、边界与非目标

### 2.1 目标

1. 一份主题可以同时声明首页、详情、搜索、历史、收藏等多个页面。
2. 每个页面独立启用、独立授权、独立回退原生。
3. 主题只依赖版本化 DTO 和桥接方法，不依赖内部 Java Bean、Activity 或数据库结构。
4. JAR、JS、Python 等内容源继续通过统一的原生 `SiteApi` / Spider 链路取数。
5. 同一主题支持手机触控和电视遥控，并有明确的焦点行为规范。
6. 主题升级或应用升级时保持向后兼容；破坏性变化必须提升协议版本。
7. 远程主题不获得任意原生调用、任意设置写入或敏感数据读取能力。
8. 原生页面始终可作为安全兜底，用户可以一键关闭主题。
9. 首页 V1 单文件 HTML 继续可用，不强迫现有主题迁移。

### 2.2 能力边界

| 能力 | 主题负责 | 原生负责 |
| --- | --- | --- |
| 颜色、字体、间距、圆角、焦点效果 | 是 | 提供默认值和能力边界 |
| 页面结构和内容排列 | 是 | 提供数据 DTO |
| 分类、筛选、分页状态展示 | 是 | 真实取数与参数校验 |
| 详情信息、线路和选集展示 | 是 | 详情解析、播放引用生成 |
| 收藏和历史交互 | 是 | 数据读取、写入和一致性 |
| 视频画面与解码 | 否 | 是 |
| 播放状态机、字幕、音轨、DRM | 否 | 是 |
| 播放器控制层视觉 | 可选 | 提供受控命令和状态 |
| 设置项布局 | 可选 | Schema、校验、保存、权限 |
| 文件、凭据、Cookie、站点扩展代码 | 否 | 始终不直接暴露 |

### 2.3 非目标

- 不引入 Flutter Engine 或运行真正的 RFW。
- 不自动转换或兼容 `.rfwtxt`。
- 不把 ExoPlayer/原生播放器替换成 HTML5 Video。
- 不允许主题直接访问 Room、SharedPreferences、文件系统或任意 Android API。
- 不要求每个主题实现全部页面；部分主题只实现首页是合法场景。
- 不在 V2 第一阶段开放任意插件代码或无边界的 `net.request`。
- 不在同一版本中一次性重写所有原生页面。

---

## 3. 方案选择

### 3.1 候选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 全部页面 WebView 化 | 表达力强、开发统一 | 播放、设置、安全、焦点和生命周期风险最高 | 否决 |
| 只做原生颜色/资源换肤 | 性能和稳定性最好 | 无法改变详情结构和信息层级 | 仅作为基础层 |
| 自研原生 DSL | 原生性能、理论上可远程配置 | 解析器、组件工厂和双端维护成本很高 | 暂不启动 |
| Web 页面 + 原生能力的混合模式 | 复用 WebHome、布局自由、核心能力可靠 | 需要稳定契约、权限和统一焦点运行时 | 选定 |

### 3.2 选定架构

```text
┌──────────────────────────────────────────────────────────┐
│ Theme Package                                            │
│ HTML / CSS / JS / assets / theme.json / design tokens   │
└────────────────────────────┬─────────────────────────────┘
                             │ versioned contracts
┌────────────────────────────▼─────────────────────────────┐
│ WebTheme Runtime                                         │
│ Page Host / Router / Focus / Lifecycle / Permission      │
└────────────────────────────┬─────────────────────────────┘
                             │ page-scoped bridge
┌────────────────────────────▼─────────────────────────────┐
│ Native Capability Layer                                 │
│ Vod / Detail / Search / History / Favorite / Player /   │
│ Settings / Navigation / Device                           │
└────────────────────────────┬─────────────────────────────┘
                             │ existing domain services
┌────────────────────────────▼─────────────────────────────┐
│ SiteApi / Spider / Database / PlaybackService / Android  │
└──────────────────────────────────────────────────────────┘
```

### 3.3 核心原则

1. **DTO 优先**：内部 Bean 不直接穿过桥。
2. **页面隔离**：详情页不自动获得设置写入能力。
3. **当前源默认**：VOD 页面默认只能访问当前内容源。
4. **原生内核**：播放、存储、权限和校验不交给主题。
5. **渐进增强**：主题缺少能力时继续使用原生页面。
6. **显式版本**：Manifest、Host API 和每类 DTO 均有版本。
7. **安全失败**：超时、异常或不兼容优先回退，而不是留白或卡死。
8. **TV 优先验证**：任何新页面在完成遥控焦点验收前不算完成。

---

## 4. 页面覆盖与推荐实现方式

| 页面/区域 | 可定制程度 | 推荐方式 | 复杂度 | 优先级 |
| --- | --- | --- | --- | --- |
| 首页 | 完全定制 | 当前 WebHome V1 / V2 page host | 已实现 | P0 |
| 分类页 | 完全定制 | `vod.category` + 自动分页 | 已实现基础 | P0 |
| 搜索结果 | 完全定制 | 新增 `vod.search` 数据接口 | 中 | P2 |
| 详情页 | 基本完全定制 | Web 渲染，原生详情与播放能力 | 中高 | P1 |
| 收藏页 | 完全定制 | 归一化收藏 DTO + 原生写入 | 中 | P2 |
| 历史页 | 完全定制 | 归一化历史 DTO + 原生写入 | 中 | P2 |
| 专题/推荐页 | 完全定制 | 通用列表页契约 | 中 | P2 |
| 播放器背景和控制视觉 | 高度定制 | 原生 Player Chrome + tokens | 高 | P3 |
| 播放器内核 | 不定制 | 保留原生 | 极高风险 | 不做 |
| 设置页视觉 | 高度定制 | 先原生 tokens，后 Schema 页面 | 中高 | P3/P4 |
| 全局弹窗、Toast、加载态 | 部分到高度定制 | 原生组件消费设计变量 | 中 | P3 |
| 直播频道列表 | 可定制 | Web/原生列表 + 原生直播播放 | 高 | 后续 |

最优先的新页面是详情页，因为：

- 用户感知价值最高。
- 已有 `player.playVod` 原生入口可复用。
- 可以验证复杂 DTO、路由参数、收藏/历史和原生播放衔接。
- 详情页跑通后，搜索、历史和收藏大多可以复用同一个通用页面宿主。

---

## 5. WebTheme V2 主题包

### 5.1 V1 兼容

现有“直接配置一个 HTML URL”的方式继续保留：

- 没有 Manifest 的 HTML 按 **WebHome V1 首页主题**处理。
- 只能接管首页，继续使用现有 `fm` SDK 和 V1 DTO。
- 不因为 V2 上线而改变现有加载优先级或接口行为。

V2 主题使用 Manifest 声明页面和权限。V1 与 V2 可以长期并存。

### 5.2 建议目录结构

```text
theme/
├── theme.json
├── pages/
│   ├── home.html
│   ├── detail.html
│   ├── search.html
│   ├── history.html
│   ├── favorite.html
│   └── settings.html
├── shared/
│   ├── runtime.js
│   ├── focus.js
│   └── theme.css
└── assets/
    ├── icons/
    ├── fonts/
    └── images/
```

第一阶段可以支持“远程 Manifest URL + 同源文件”和内置 assets。ZIP 安装、签名包和主题市场属于后续分发议题，不阻塞多页面架构。

### 5.3 Manifest 草案

```jsonc
{
  "schemaVersion": 2,
  "id": "maple.eclipse",
  "name": "枫叶 · 光影",
  "version": "2.0.0",
  "minHostApi": 2,
  "targets": ["mobile", "leanback"],

  "pages": {
    "home": {
      "entry": "pages/home.html",
      "contract": "vod.home@1",
      "fallback": "native"
    },
    "detail": {
      "entry": "pages/detail.html",
      "contract": "vod.detail@1",
      "fallback": "native"
    },
    "search": {
      "entry": "pages/search.html",
      "contract": "vod.search@1",
      "fallback": "native"
    },
    "history": {
      "entry": "pages/history.html",
      "contract": "history.list@1",
      "fallback": "native"
    },
    "favorite": {
      "entry": "pages/favorite.html",
      "contract": "favorite.list@1",
      "fallback": "native"
    },
    "settings": {
      "entry": "pages/settings.html",
      "mode": "schema",
      "fallback": "native"
    }
  },

  "player": {
    "engine": "native",
    "chrome": "tokens"
  },

  "permissions": {
    "home": ["vod.home", "vod.category", "navigation.openDetail", "app.search", "app.openVod", "app.openSetting"],
    "detail": ["vod.detail", "favorite.read", "favorite.write", "history.read", "player.playVod", "app.search"],
    "search": ["vod.search", "navigation.openDetail"],
    "history": ["history.read", "history.write", "navigation.openDetail"],
    "favorite": ["favorite.read", "favorite.write", "navigation.openDetail"],
    "settings": ["settings.schema", "settings.read", "settings.write.safe"]
  },

  "tokens": {
    "color.background": "#070B18",
    "color.surface": "#11172B",
    "color.primary": "#51DDF2",
    "color.focus": "#7B61FF",
    "color.text.primary": "#F4F6FF",
    "color.text.secondary": "#8C96B4",
    "radius.card": 16,
    "radius.control": 999,
    "motion.focusScale": 1.04
  }
}
```

Manifest 中声明的权限只是“申请”，最终权限由宿主按页面白名单取交集。主题不能通过自行声明获得宿主未开放的能力。

### 5.4 页面解析优先级

首页保留 V1 既有优先级：

```text
站点 homePage > 全局主题 pages.home > 原生首页
```

其他页面按页面独立解析：

```text
全局主题声明且兼容该页面 > 原生页面
```

规则：

- 某个内容源有 `homePage`，只影响首页，不阻止全局主题接管详情页。
- 主题只声明首页时，详情、播放和设置继续走原生。
- 单个 Web 页面失败时，只回退该页面。
- 用户设置中应允许“启用全局主题，但关闭某个页面接管”。

### 5.5 设计变量

设计变量应同时服务 Web 页面和原生页面，以实现真正统一的视觉语言：

- 颜色：background、surface、primary、focus、text、danger、divider。
- 排版：字体族、标题/正文/说明字号、字重和行高。
- 形状：卡片、按钮、弹窗和图片圆角。
- 间距：页面边距、网格间距、分组间距。
- 动效：焦点缩放、过渡时长、透明度。
- 图片：默认海报、横图占位、头像占位。

原生组件必须对字段做范围限制。例如焦点缩放不能无限增大，动画时长不能为负，远程字体需要明确的下载和缓存策略。

---

## 6. WebTheme Runtime

### 6.1 建议组件拆分

在现有 WebHome 实现基础上，建议逐步抽出以下通用组件，名称为设计建议，落地时可按项目风格调整：

| 组件 | 职责 |
| --- | --- |
| `WebThemeManager` | 读取主题设置、Manifest、缓存和版本 |
| `WebThemeResolver` | 按页面、端类型和能力决定 Web/原生目标 |
| `WebThemePageActivity` / `WebThemePageFragment` | 通用 Web 页面宿主 |
| `WebThemeController` | 从 `HomeWebController` 抽取通用 WebView 生命周期 |
| `WebThemeBridgeRouter` | 根据当前页面注册允许的桥接模块 |
| `ThemePageContext` | 向页面提供路由、客户端、主题和当前源信息 |
| `NativePageFallback` | 页面失败时打开对应原生实现 |
| `TvFocusRuntime` | 统一电视端焦点、恢复和自动分页行为 |

不建议立即删除或大改 `HomeWebController`。先让新的通用宿主复用已验证的加载、安全区、SDK 注入和错误恢复逻辑；详情页稳定后，再判断是否合并公共基类。

### 6.2 页面上下文

每个页面加载后先通过 `theme.info` 或初始化事件获得上下文：

```jsonc
{
  "hostApiVersion": 3,
  "page": "detail",
  "theme": {
    "id": "maple.eclipse",
    "version": "2.0.0"
  },
  "client": {
    "isLeanback": true,
    "isLandscape": true,
    "width": 1920,
    "height": 1080,
    "density": 1.0,
    "safeArea": { "top": 0, "right": 0, "bottom": 0, "left": 0 }
  },
  "source": {
    "key": "current-source-key",
    "name": "当前内容源"
  },
  "route": {
    "vodId": "opaque-vod-id"
  },
  "capabilities": [
    "theme.info@1",
    "ui.getViewport@1",
    "navigation.back@1",
    "navigation.reload@1",
    "navigation.openNativeDetail@1",
    "vod.detail@1",
    "favorite.read@1",
    "favorite.write@1",
    "player.playVod@1"
  ]
}
```

路由参数由原生注入，主题不应从 URL 查询串猜测内部 Activity 参数。

`theme.info`、`ui.getViewport`、`navigation.back`、`navigation.reload` 是 V2 页面固定基础能力；详情页另有固定的原生详情逃生能力。其余业务能力必须同时位于宿主页面白名单和 Manifest 对应页面的 `permissions` 中，`capabilities` 返回的是最终交集而不是 Manifest 原始声明。

### 6.3 生命周期

建议定义统一页面状态：

```text
CREATED
  → LOADING
  → SDK_READY
  → DATA_LOADING
  → ACTIVE
  → SUSPENDED
  → DESTROYED
```

要求：

- 主文档加载和 SDK 就绪分别超时处理。
- 页面暂停时停止轮询、动画和非必要网络任务。
- 进入原生播放器前调用现有的 native playback preparation，避免 WebView 抢占媒体资源。
- 返回页面时恢复路由、滚动位置、选中筛选和焦点 ID。
- 页面销毁时取消未完成桥接任务，禁止长期 observe LiveData。
- 同一时刻不保留多个不可见的重型 WebView；使用原生 back stack 管理页面。

### 6.4 导航与返回

统一返回优先级：

```text
页面内弹层/筛选面板
    > 页面内历史栈
    > 当前 Web 页面
    > 原生 Activity/Fragment 返回栈
```

主题不能拦截系统返回后永久不释放。宿主应允许页面声明“已处理返回”，但连续异常或超时后仍由原生完成返回。

---

## 7. 桥接接口设计

### 7.1 接口规则

1. 方法按领域拆分，不继续全部堆进 `HomeWebBridge`。
2. 方法名使用 `domain.action`，例如 `vod.detail`、`favorite.set`。
3. 输入输出必须有长度、数量和类型限制。
4. DTO 字段默认可选；主题必须容忍空值和未知字段。
5. 新增字段保持向后兼容；删除、改名或改变语义必须提升 contract major version。
6. Host API 3 的 Promise rejection 使用标准 `Error`：`Error.code` 返回稳定规范码，`Error.message` 为兼容既有主题保留旧线码。

```javascript
try {
  await window.fongmi.vod.detail({});
} catch (error) {
  if (error.code === "SOURCE_CHANGED") await reloadPageContext();
}
```

稳定规范码为：`PERMISSION_DENIED`、`INVALID_ARGUMENT`、`SOURCE_CHANGED`、`STALE_REFERENCE`、`PAGE_UNAVAILABLE`、`NATIVE_FALLBACK`、`RATE_LIMITED`、`RESPONSE_TOO_LARGE`、`INVALID_REQUEST`、`REQUEST_FAILED`。兼容别名为：`STALE_REFERENCE → INVALID_ARGUMENT`、`PAGE_UNAVAILABLE/NATIVE_FALLBACK → UNAVAILABLE`、`RATE_LIMITED → BUSY`。主题的新逻辑应判断 `Error.code`。

### 7.2 建议桥接模块

```text
ThemeBridge
├── ThemeInfoBridge
├── NavigationBridge
├── VodBridge
├── DetailBridge
├── SearchBridge
├── FavoriteBridge
├── HistoryBridge
├── PlayerBridge
├── SettingsBridge
├── UiBridge
└── DeviceBridge
```

桥路由根据当前页面只注册允许模块。例如详情页不注册 `settings.write`，设置页不注册 `player.playUrl`。

### 7.3 当前 V1 接口

以下能力已经存在，应保持兼容：

- `vod.home`
- `vod.category`
- `player.playUrl`
- `player.playVod`
- `player.playVodInline`
- `player.preloadArtwork`
- `player.control`
- `player.status`
- `app.search`
- `app.openVod`
- `app.openLive`
- `app.openKeep`
- `app.openSetting`
- `app.history`
- 既有 cache、device、site、config、ui 和 navigation 能力

其中导航型 `app.search` 与未来数据型 `vod.search` 含义不同，必须同时保留：

- `app.search`：打开原生搜索页。
- `vod.search`：给自定义搜索页返回结果数据。

### 7.4 V2 建议新增接口

| 方法 | 用途 | 主要页面 |
| --- | --- | --- |
| `theme.info` | Host API、上下文和能力协商 | 全部 |
| `vod.detail` | 获取归一化详情、线路和选集 | 详情 |
| `vod.search` | 在允许的来源范围内搜索并分页 | 搜索 |
| `favorite.list` | 获取收藏列表 | 收藏 |
| `favorite.status` | 查询单条收藏状态 | 详情 |
| `favorite.set` | 收藏/取消收藏 | 详情、收藏 |
| `history.list` | 获取播放历史 | 历史 |
| `history.item` | 获取单条进度 | 详情 |
| `history.remove` | 删除历史 | 历史 |
| `navigation.openDetail` | 打开 Web 或原生详情 | 列表 |
| `navigation.openNativeDetail` | 强制使用原生详情兜底 | 详情 |
| `player.playVod` 扩展参数 | 指定线路/集数/恢复进度 | 详情 |
| `app.search` | 按标题打开原生片源搜索 | 首页、详情推荐 |
| `person.open` | 使用人物引用打开原生人物详情 | 详情 |
| `image.preview` | 使用图片引用打开原生全屏预览 | 详情 |
| `image.save` | 使用图片引用保存原图 | 详情 |
| `recommendation.open` | 当前源匹配成功后打开详情，失败时进入全局搜索 | 详情 |
| `recommendation.info` | 打开推荐说明、评分和 AI 理由 | 详情 |
| `recommendation.feedback` | 提交“不感兴趣”等受控反馈 | 详情 |
| `external.open` | 使用宿主生成的链接引用打开外部页面 | 详情 |
| `settings.schema` | 获取允许展示的设置定义 | 设置 |
| `settings.get` | 获取允许读取的设置值 | 设置 |
| `settings.set` | 校验并写入安全设置 | 设置 |

### 7.5 当前详情 DTO（`vod.detail@1`）

```jsonc
{
  "version": 1,
  "source": {
    "key": "source-key",
    "name": "内容源"
  },
  "item": {
    "vodId": "opaque-id",
    "siteKey": "source-key",
    "name": "影片名",
    "pic": "https://...",
    "remarks": "更新至 10 集",
    "year": "2026",
    "area": "内地",
    "typeName": "剧情",
    "actor": "演员",
    "director": "导演",
    "content": "简介"
  },
  "media": {
    "tmdbId": 123,
    "mediaType": "tv",
    "originalName": "Original title",
    "tagline": "宣传语",
    "releaseDate": "2026-01-02",
    "lastAirDate": "2026-02-20",
    "status": "Returning Series",
    "backdrop": "https://...",
    "rating": 8.6,
    "voteCount": 321,
    "runtimeMinutes": 45,
    "seasonCount": 2,
    "episodeCount": 18,
    "originalLanguage": "zh",
    "originCountry": "CN",
    "genres": ["剧情", "悬疑"]
  },
  "people": [
    {
      "personId": 1,
      "kind": "cast",
      "name": "演员名",
      "role": "角色名",
      "department": "Acting",
      "profile": "https://..."
    }
  ],
  "gallery": ["https://..."],
  "sources": [
    {
      "sourceId": "line-0",
      "name": "线路一",
      "selected": true,
      "episodes": [
        {
          "episodeId": "episode-0",
          "name": "第 1 集",
          "number": 1,
          "playRef": "opaque-play-reference",
          "selected": false,
          "title": "本集标题",
          "date": "2026-01-02",
          "overview": "本集简介",
          "still": "https://...",
          "rating": 8.4,
          "runtimeMinutes": 45,
          "seasonNumber": 1
        }
      ]
    }
  ],
  "state": {
    "favorite": false,
    "history": {
      "sourceId": "line-0",
      "episodeId": "episode-0",
      "positionMs": 0,
      "durationMs": 0,
      "updatedAt": 0
    }
  },
  "recommendations": [
    {
      "tmdbId": 456,
      "mediaType": "movie",
      "name": "推荐影片",
      "subtitle": "2025 · 电影",
      "overview": "推荐简介",
      "pic": "https://...",
      "backdrop": "https://...",
      "rating": 7.9
    }
  ],
  "capabilities": {
    "canFavorite": true,
    "canPlay": true,
    "canSearchRecommendations": true,
    "hasPeople": true,
    "hasGallery": true,
    "hasRecommendations": true,
    "hasEpisodeMetadata": true,
    "tmdbEnriched": true
  }
}
```

设计要求：

- `vodId`、`typeId`、筛选 key/value、`sourceId`、`episodeId` 和 `playRef` 对主题来说都是不透明值；主题只能回传当前响应给出的引用，不能构造供应商 ID。
- 首页、分类和详情共用当前页面的短期访问会话；主文档导航、切源、重载、页面错误或 WebView 重建后，旧的影片、分类和筛选引用全部失效。
- V2 不输出供应商 `action` 字符串；此类入口只能显示为不可执行项或交由原生兼容页面处理。
- 不直接把内部 `vod_play_url` 或解析器对象暴露给主题。
- 主题选择集数后，把 `playRef` 交回原生；原生验证其与当前详情会话匹配。
- 同一详情的增量刷新会复用稳定 `playRef`；切换来源或 `vodId` 时全部失效。
- 超长选集需要分页、分组或虚拟化，不能一次创建数千个 DOM 节点。
- `media`、`people`、`gallery`、推荐和单集扩展字段均为可选；缺失时主题直接隐藏对应区域。
- 宿主最多输出 24 个人物、12 张剧照和 18 条推荐，所有公开字符串仍执行长度限制。
- `state.favorite` 仅在页面声明 `favorite.read` 时返回；`state.history` 与由历史推导的线路/选集选中态仅在声明 `history.read` 时返回。`capabilities.canFavorite` 只有同时具备 `favorite.read` 和 `favorite.write` 才为 `true`。
- TMDB 增强异步完成后，宿主触发 `fmdetailchange`；主题可调用 `vod.detail({vodId, cached:true})` 获取当前路由的最新快照，不会重新请求内容源。
- TMDB 推荐没有当前内容源的 `vodId`，不得把 `tmdbId` 传给 `navigation.openDetail`；有 `app.search` 权限时按标题搜索片源，否则只展示。
- 图片地址由主题再次限制为普通 `http` / `https` URL；不接受 `data:`、`file:` 或脚本协议。

### 7.6 播放接口扩展

当前 `player.playVod` 可以按 `siteKey + vodId` 打开原生播放器。详情页需要进一步支持指定线路和集数，建议兼容性扩展为：

```jsonc
{
  "siteKey": "current-source-key",
  "vodId": "opaque-vod-id",
  "playRef": "opaque-play-reference",
  "title": "影片名",
  "pic": "https://...",
  "wallPic": "https://...",
  "resume": true
}
```

约束：

- `playRef` 只能由当前 `vod.detail` 响应产生。
- 全局主题仍只能播放当前源内容。
- 原生播放器决定最终线路、集数、历史恢复和错误重试。
- 未传 `playRef` 时保持现有行为，由原生详情/播放器选择默认集数。

### 7.7 设置 Schema 草案

```jsonc
{
  "version": 1,
  "groups": [
    {
      "id": "playback",
      "title": "播放设置",
      "items": [
        {
          "key": "player.auto_next",
          "type": "switch",
          "title": "自动播放下一集",
          "description": "当前集播放结束后自动继续",
          "value": true,
          "writable": true
        },
        {
          "key": "player.default_scale",
          "type": "select",
          "title": "默认画面比例",
          "value": "fit",
          "options": [
            { "label": "适应", "value": "fit" },
            { "label": "填充", "value": "fill" },
            { "label": "原始", "value": "original" }
          ],
          "writable": true
        }
      ]
    }
  ]
}
```

设置桥必须：

- 只接受 Schema 中存在且 `writable=true` 的 key。
- 由原生完成类型、范围、依赖和权限校验。
- 高风险操作继续打开原生确认页或系统页面。
- 写入成功后返回最终值，避免主题假定保存成功。

默认不开放：备份恢复、清除全部数据、凭据、Cookie、本地文件路径、调试开关、任意 Intent 和任意 URL Scheme。

---

## 8. 页面设计

### 8.1 详情页

推荐调用链：

```text
首页/搜索/收藏点击影片
    → navigation.openDetail(siteKey, vodId)
    → WebThemeResolver 检查 pages.detail
    → WebThemePageActivity(detail)
    → theme.info
    → vod.detail
    → 主题渲染详情、线路、选集和推荐
    → favorite.set / player.playVod
    → 原生播放器
```

实现策略：

1. 保留当前 `TmdbDetailActivity` 作为完整兜底。
2. 新建平行的 Web 详情宿主，不直接把现有大型 Activity 改成半原生半 Web。
3. 基础响应提供海报、简介、线路、选集、收藏和播放，内容源详情可先显示。
4. 复用 `TmdbUIAdapter` 渐进补齐背景、演员与主创、评分、季集数、单集剧照和相关推荐。
5. Web 详情加载失败时原地切换到原生详情，而不是返回首页。
6. TMDB 增量更新和返回详情页时保留线路、集数、分页、滚动位置和焦点。
7. 推荐项通过受控 `app.search` 查找当前可用片源，不把 TMDB ID 当成源站 VOD ID。

详情页 MVP 验收：

- JAR、JS、Python 源各至少一个详情可正常展示。
- 能选择线路和集数并进入正确原生播放。
- 收藏状态和历史进度正确。
- TMDB 可用时人物图片、剧照、单集资料和推荐渐进出现；不可用时基础详情不受影响。
- 空详情、无线路、超长选集和接口错误有明确状态。
- 手机触控和电视遥控均可完整操作。
- 主题缺少详情页或加载失败时原生详情可用。

### 8.2 播放页

播放器采用分层能力：

#### L0：设计变量换肤（优先）

原生播放器控制层读取主题颜色、圆角、文字和焦点变量。改动风险最低，可以最先落地。

#### L1：可配置原生 Player Chrome（推荐目标）

主题通过声明式配置决定控制项排列、显示密度、背景和面板样式，但控件仍是原生 View，由原生处理遥控、触摸和无障碍。

#### L2：Web Overlay（实验性，不作为默认方案）

仅把非关键的信息层叠加为 Web UI。需要解决视频层级、透明 WebView、遥控焦点、性能和生命周期问题，不应在 V2 第一阶段使用。

始终保持原生的能力：

- 视频解码和渲染 Surface。
- DRM、字幕、弹幕、音轨和解码器选择。
- 进度、缓冲、错误恢复和播放队列。
- 前后台、画中画、媒体通知和音频焦点。
- 遥控器快进/快退、长按和关键播放按键。

因此，“播放页全定制”的正确含义是视觉和控制布局可定制，而不是用网页重新实现播放器。

### 8.3 设置页

分两步实施：

1. **原生设置页消费全局 tokens**：先统一颜色、背景、焦点、分组、按钮和弹窗样式。
2. **Schema 驱动的 Web 设置页**：主题负责布局，原生提供安全设置定义和读写。

设置页是高权限页面，不能直接复用首页的全部桥。若主题设置页崩溃或协议不兼容，必须能从系统入口进入原生设置并关闭主题。

### 8.4 搜索、历史和收藏

这些页面应共享通用列表能力：

- 相同的 `items[]` 基础 DTO。
- 统一分页元数据和空态/错误态。
- `navigation.openDetail` 打开主题或原生详情。
- 统一图片代理、预加载和焦点恢复。
- 历史/收藏的删除操作由原生确认和持久化。

不建议每个页面复制一份首页分页和焦点脚本，应由 `shared/runtime.js` 或宿主注入的公共运行时提供。

---

## 9. 电视遥控、焦点与自动分页规范

遥控焦点必须成为 WebTheme Runtime 的公共能力，而不是每个主题自由猜测。

### 9.1 焦点元素约定

建议使用：

```html
<button
  data-focus-id="category-tv"
  data-focus-group="categories"
  data-focus-row="0"
  data-focus-col="5">
  电视剧
</button>
```

可选显式方向：

```html
<button
  data-nav-left="category-short"
  data-nav-right="category-movie"
  data-nav-down="filter-type-all">
</button>
```

### 9.2 方向键选择顺序

统一算法：

1. 如果声明了 `data-nav-*`，优先走显式目标。
2. 否则优先在**当前 focus group** 内寻找同主轴目标。
3. 同组无目标时，再在方向锥形区域内计算最近可见元素。
4. 只有已经到达当前组边界时，才允许跨组。
5. 隐藏、禁用、透明或屏幕外元素不得参与计算。

这一规则可以避免“分类按钮按右键却跳到下方筛选按钮”的问题。分类横向移动应先遍历完整分类组，到达末端后才允许进入其他区域。

### 9.3 动态内容与焦点恢复

- 每个逻辑条目使用稳定 `data-focus-id`，不要只用 DOM index。
- 分类、筛选或分页重绘后，优先恢复同一 ID。
- 条目不存在时，恢复到同组最近元素；仍不存在才回退页面默认焦点。
- 弹层关闭后恢复打开弹层前的焦点。
- 返回页面时同时恢复焦点、滚动位置和筛选状态。

### 9.4 自动分页

默认不向电视用户暴露可聚焦的“加载更多”按钮。分页状态机：

```text
IDLE → LOADING → APPEND_SUCCESS → IDLE
                 └→ EXHAUSTED
                 └→ ERROR_RETRYABLE
```

触发条件：

- 手机滚动接近列表末尾时自动预取。
- 电视焦点进入倒数一至两行时自动预取。
- 同一页只允许一个请求，防止重复加载。
- 分类或筛选变化时取消旧请求并重置页码。
- `pageCount > 0 && page >= pageCount`，或在 `pageCount == 0` 时返回空列表，才进入 `EXHAUSTED`。
- 出错时可显示可聚焦“重试”，但普通加载更多控件默认不参与焦点。
- 追加数据后保持当前卡片焦点，不自动跳到新条目。

### 9.5 返回键

- 筛选面板打开：先关闭面板。
- 详情内选集弹层打开：先关闭弹层。
- 页面有内部历史：退回内部状态。
- 无内部状态：交还原生返回栈。

---

## 10. 安全与权限模型

### 10.1 信任模型

远程主题本质上是可执行 HTML/JavaScript，必须视为不可信：

- 首次启用远程主题时显示来源域名和风险提示。
- 已信任 URL 作为本机同意状态保存，不随设置备份迁移；配置 URL 与同意记录不匹配时直接回退原生首页。
- 设置中始终提供“关闭主题”和“恢复原生界面”。
- 主题失败不能阻止用户进入原生设置。
- 不因主题声明权限而自动授予权限。
- 远程主题只接受 HTTPS，并绑定到配置 URL 的精确 Origin；跨 Origin 主文档导航会被拒绝。
- 远程主题通过 `WebViewCompat.addWebMessageListener` 通信，宿主同时校验来源 Origin 和 `isMainFrame`，iframe 无法调用原生能力。
- 监听器固定远程会话 generation，主文档导航轮换不可猜 nonce；切源、重载、WebView 重建和主文档错误都会让旧会话失效。
- 远程请求设置消息长度、在途数量、页码、返回条数和响应字节上限，避免占满共享执行器或向 WebView 传输超大消息。
- 写入、播放和导航类远程调用共享 400 ms 的最小间隔，读取类调用不受影响；会话轮换时节流状态清空。
- 诊断日志不记录完整桥接 payload，URL 日志移除用户信息、查询参数和片段。
- 远程协议不暴露 `net.*`、`cache.*`、`ext.*`、设备/站点完整配置、任意 URL 播放或 raw 资源代理，也不携带 Cookie、Authorization 和站点请求头。

### 10.2 页面级能力白名单

宿主维护硬编码白名单，例如：

| 页面 | 默认允许 |
| --- | --- |
| home | `vod.home`、`vod.category`、受控导航、当前源播放 |
| detail | `vod.detail`、收藏、历史读取、当前详情播放 |
| search | `vod.search`、打开详情 |
| history | 历史读写、打开详情 |
| favorite | 收藏读写、打开详情 |
| settings | 安全设置 Schema 和受控写入 |

主题 Manifest 的权限与宿主白名单取交集。没有授权的方法返回 `PERMISSION_DENIED`。

### 10.3 内容源隔离

- 默认只允许当前源。
- `siteKey` 为空时自动使用当前源。
- 传入不同 `siteKey` 时拒绝，而不是静默切源。
- 页面加载期间内容源发生变化时，取消旧请求并返回 `SOURCE_CHANGED`。
- 未来若支持多源搜索，应设计单独的显式能力和用户开关，不能复用普通 `vod.search` 偷渡跨源访问。

### 10.4 数据最小化

默认不向主题暴露：

- Cookie、Authorization、完整请求头。
- 站点扩展脚本和 Spider 实例。
- 本地数据库实体和内部路径。
- 设备唯一标识和账号凭据。
- 任意文件读写、Intent 和系统设置。

图片和受保护资源继续通过受控资源代理处理。

### 10.5 网络能力

通用主题不应默认获得无限制的 `net.request`。建议区分：

- 内容数据：必须走 `vod.*`、`history.*`、`favorite.*`。
- 主题静态资源：由 WebView 正常加载，受 CSP、同源和 URL 校验约束。
- 第三方 API：只有主题显式申请、用户允许并满足域名白名单时才开放。

V1 兼容接口可以保留，但 V2 多页面主题应使用更严格的默认策略。

---

## 11. 兼容、回退与恢复

### 11.1 兼容策略

- V1 HTML 继续按首页主题加载。
- V2 Manifest 未识别字段应忽略。
- `minHostApi` 高于当前应用时，不加载不兼容页面。
- 单个页面 contract 不支持时，只回退该页面。
- 现有方法只做兼容性扩展，不改变已有参数语义。
- 破坏性接口使用新 major version，例如 `vod.detail@2`。

### 11.2 回退矩阵

| 故障 | 行为 |
| --- | --- |
| Manifest 获取失败 | 使用上次有效缓存；无缓存则原生 |
| 页面入口不存在 | 该页面原生 |
| 主文档加载错误 | 尝试一次恢复；仍失败则原生 |
| SDK 未就绪 | 超时后该页面原生 |
| 数据接口失败 | 页面显示错误/重试；严重错误可原生 |
| 焦点运行时异常 | 切换安全焦点模式或原生 |
| 主题连续崩溃 | 临时禁用该页面并提示用户 |
| 设置主题不可用 | 强制保留原生设置入口 |

### 11.3 更新与回滚

- 缓存最后一份成功加载的 Manifest 和资源版本。
- 新版本首次加载失败时自动回滚到上一版本。
- 主题缓存必须有大小上限和清理策略。
- 调试模式记录页面、主题版本、Host API、方法名和错误码，不记录敏感 payload。

---

## 12. 性能与资源管理

### 12.1 原则

- 不在播放器上方长期保留 WebView。
- 同一时刻只保持必要的可见 WebView；后台页面暂停定时器和动画。
- 图片使用懒加载、合适尺寸和原生资源代理。
- 首页和列表按页追加，不一次性加载全部内容。
- 长选集使用虚拟列表、分组或分页。
- CSS 动画优先 transform/opacity，避免大面积重排。
- 内置主题不依赖外部字体和运行时 CDN。

### 12.2 性能验收

在设定硬指标前，先在低配 Android TV 上记录原生页面和 WebHome V1 基线。V2 至少需要监测：

- WebView 创建到首个可见骨架的时间。
- SDK_READY 和首批数据返回时间。
- 分类/筛选切换耗时。
- 详情长选集 DOM 数量和内存。
- 遥控连续移动时的掉帧和焦点丢失。
- 页面退出后 WebView、LiveData 和异步任务是否释放。

如果 Web 详情或列表在低配 TV 上长期无法达到可接受体验，再评估把特定页面切换为原生 DSL，而不是先建设一套全应用 DSL。

---

## 13. 实施路线

### M0：首页 V1（当前已完成）

- `vod.home` / `vod.category` 当前源取数。
- 全局皮肤选择和加载优先级。
- Eclipse 示例、分类筛选、自动分页和 TV 焦点。
- 双端设置、回退和基本测试。

### M1：WebTheme V2 基础设施

- 定义 `theme.json` Schema V2。
- 增加 `theme.info` 和能力协商。
- 增加页面级 resolver、权限白名单和原生 fallback。
- 从 Home WebView 中抽取最小公共生命周期能力。
- 建立统一 TV focus runtime。
- V1 HTML 回归保持不变。

验收：同一主题只声明 `pages.home` 时行为与 V1 等价；添加空的 `pages.detail` 后能进入独立宿主并可靠回退。

### M2：详情页 MVP

- `vod.detail@1` DTO。
- `navigation.openDetail`。
- `player.playVod` 的 `playRef`/集数扩展。
- 收藏状态、历史进度读取和写入。
- 内置 Eclipse detail 示例。
- 原生详情兜底。

验收见 §8.1。

### M3：通用列表页面

- `vod.search`。
- `history.list/item/remove`。
- `favorite.list/status/set`。
- 共用列表分页、图片、空态、错误态和焦点恢复。

验收：搜索、历史和收藏三页共用同一套基础运行时，不复制三套分页实现。

### M4：全局设计变量与原生组件换肤

- Manifest tokens 解析、校验和默认值。
- 设置、弹窗、加载态和播放器控制层消费 tokens。
- 主题切换时原生组件同步刷新。

### M5：播放器控制层

- 先实现 L0 tokens。
- 再评估 L1 声明式原生 Player Chrome。
- 不把播放器内核迁入 WebView。

### M6：Schema 设置页

- 建立安全设置 Schema。
- 实现 `settings.get/set` 白名单。
- 高风险设置继续跳原生确认页。
- 保留任何情况下都可进入的原生安全设置入口。

---

## 14. 测试与验收策略

### 14.1 契约测试

建议新增：

- `WebThemeManifestTest`
- `WebThemeResolverTest`
- `WebThemePermissionTest`
- `WebDetailContractTest`
- `WebThemePageContextTest`
- `WebThemeFallbackTest`

每个 DTO 覆盖：空字段、超长字段、未知字段、分页边界、来源切换和异常映射。

### 14.2 JavaScript 运行时测试

在现有 Eclipse JS 测试基础上扩展：

- 同组左右移动不会跳入下方筛选组。
- 组边界上下移动正确。
- 动态隐藏筛选后不会聚焦隐藏元素。
- 自动分页不会重复请求。
- 分类切换时旧分页响应不会污染新分类。
- 追加条目后焦点保持。
- 详情线路和选集重绘后焦点恢复。
- 返回键按弹层、页面、原生顺序处理。

### 14.3 双端集成测试

手机：

- 触控、滚动、旋转和返回。
- 页面间跳转和状态恢复。
- 详情进入播放器并返回。
- 远程主题错误后的原生回退。

电视：

- 从首个元素遍历到页面所有可操作区域。
- 横向分类不误跳到筛选区。
- 最后一行触发自动分页。
- 超长选集不会卡顿或丢焦点。
- 播放返回后恢复正确影片和集数焦点。
- 原生设置始终可达。

### 14.4 安全测试

- 伪造其他 `siteKey` 被拒绝。
- 未声明或未授权方法返回 `PERMISSION_DENIED`。
- 设置页不能写入 Schema 外 key。
- 超长 payload、过多筛选项和非法 URL 被限制。
- 内容源切换期间的旧请求被取消。
- 主题加载失败不会形成返回死循环。

---

## 15. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| Bridge 持续膨胀 | 难维护、权限不清 | 按领域模块化并按页面注册 |
| DTO 复制内部 Bean | 内部改动导致主题破坏 | 独立 Mapper、版本化 DTO、契约测试 |
| TV 焦点不稳定 | 页面不可用 | 公共 focus runtime + 显式导航 + 真机测试 |
| WebView 内存和冷启动 | 低配 TV 卡顿 | 单可见宿主、懒加载、缓存和原生回退 |
| 远程主题滥用能力 | 隐私和安全问题 | 最小权限、当前源隔离、设置白名单 |
| 详情选集过长 | DOM 和焦点性能差 | 虚拟化、分组、分页 |
| 主题阻断设置入口 | 用户无法恢复 | 原生安全设置入口永远保留 |
| 播放器 Web 化诱惑 | 性能和兼容性退化 | 明确原生 engine 边界 |
| 多端行为分叉 | 维护成本增加 | 同一 DTO/JS runtime，端差异仅通过 client context |
| 一次改动范围过大 | 回归难定位 | 按 M1～M6 增量交付，每阶段独立回退 |

---

## 16. 待决策问题

以下问题不阻塞详情页 MVP，但在正式冻结 V2 前需要决定：

1. V2 主题初期只支持 Manifest URL，还是同时支持本地目录/ZIP 安装。
2. Manifest 和静态资源是否强制同源。
3. 是否允许第三方域名字体和图片，允许时如何做域名白名单。
4. `playRef` 使用会话内 token，还是稳定的线路/集数索引组合。
5. 搜索页默认只搜索当前源，还是允许用户显式选择多源搜索。
6. Player Chrome 第一版只做 tokens，还是同步建设声明式布局。
7. 设置 Schema 第一批开放哪些低风险配置项。
8. 主题更新是否需要签名、哈希校验和版本回滚 UI。
9. mobile 页面宿主采用 Activity 还是 Fragment 为主；leanback 是否保持 Activity 导航。
10. 是否将公共 focus runtime 作为宿主注入脚本，还是随官方 SDK 发布并由主题引用。

默认建议：

- 第一阶段使用 Manifest URL + 同源资源。
- `playRef` 使用短生命周期、当前详情会话有效的 opaque token。
- 搜索默认当前源，多源搜索另设权限。
- Player Chrome 先 tokens。
- focus runtime 由宿主注入，确保版本和修复统一。

---

## 17. 下一步建议

下一步只实施 **M1 + M2 的最小闭环**：

1. 冻结 `theme.json` 最小 Schema。
2. 新建通用 WebTheme page host 和页面级 resolver。
3. 实现 `theme.info`、`vod.detail@1`、`navigation.openDetail`。
4. 扩展 `player.playVod` 以接收安全的集数引用。
5. 制作一个最小 Eclipse 详情页。
6. 详情加载或交互失败时回退 `TmdbDetailActivity`。
7. 完成手机、电视遥控、收藏、历史和播放返回测试。

暂时不要同时改播放器内核、设置 Schema 和所有列表页。详情页是验证多页面主题架构是否成立的最佳切片；它成功后，再按相同宿主和契约扩展搜索、历史和收藏。

最终架构目标可以概括为：

> **视觉上尽可能全局可定制，业务上保持原生可信；页面可替换，核心不失控；每页可失败，整机仍可用。**

---

## 18. V2 详情页落地基线（2026-07-29）

本轮已按 M1 + M2 的最小闭环实现 WebTheme V2 首页/详情页链路。以下约束视为当前宿主 API 2 的实现基线，后续扩展搜索、历史和收藏列表页时应保持兼容。

### 18.1 已冻结的兼容与安全边界

- V2 入口为 `theme.json`，必须声明 `schemaVersion: 2`、主题标识、最低宿主 API 和 `pages`；`HOME`、`DETAIL` 独立解析，单页无效不拖垮其他页面。
- 旧的 V1 单 HTML 远程主题继续按原协议加载；已保存的内置 `eclipse.html` 地址自动迁移到内置 V2 Manifest，避免用户手动重选主题。
- 内置页面只允许加载精确映射的 `android_asset/webhome` 资源；远程 Manifest 只接受 HTTPS，页面入口必须与 Manifest 同源。
- 远程 Manifest 限制为 128 KiB，不跟随重定向，不携带 Cookie 或认证信息，不使用系统代理，并拒绝解析到本机、私网及保留地址的主机。
- 页面最终业务调用能力为“宿主按页面硬编码白名单”与“Manifest 显式声明权限”的交集。`HOME` 和 `DETAIL` 不共享业务权限；内置 V2 页面的 `invoke` 调用也经过同一策略。
- `theme.info`、视口、返回和重载属于固定基础能力，详情页固定提供原生详情逃生能力；这些能力与 Manifest 申请的业务权限分别计算并统一通过 `capabilities` 公布。
- 宿主 API 2 当前支持 `theme.info`、`vod.home`、`vod.category`、`vod.detail`、`favorite.status/set`、`history.item`、`navigation.openDetail/openNativeDetail`、`player.playVod` 以及基础 UI/返回能力。
- 首页、分类和详情 DTO 在进入 V2 页面前把影片、分类和筛选参数转换为当前页面会话内的不透明引用；供应商原始 ID 和 `action` 字符串不进入远程主题。
- DTO 映射总响应预算为 768 KiB，并分别限制项目、分类、筛选、选集和 TMDB 扩展集合的扫描/输出规模；远程桥最终仍执行 1 MiB 硬上限。

### 18.2 详情数据与播放边界

- `vod.detail@1` 只返回主题渲染所需的独立 DTO，不暴露内部 Bean 或原始播放地址。
- 每个详情会话最多登记 64 条线路、500 个可播放条目；超出部分通过 `truncated` 告知主题，官方 Eclipse 详情页每次只渲染 120 集。
- 页面指定选集时只能把宿主生成的短生命周期 opaque `playRef` 交回 `player.playVod`；省略引用只会进入当前影片的原生默认播放流程。引用与当前 `siteKey`、影片和线路绑定，详情会话重建后立即失效。
- 收藏通过现有原生存储读写；历史由主题读取、由原生播放器在实际播放链路中继续写入，主题不能伪造播放历史。
- 播放仍由原生 `VideoActivity` 完成，Web 页面不接触解析器、嗅探器、播放内核或媒体 URL。

### 18.3 宿主、回退与当前完成度

- 手机和电视共用非导出的 `WebThemeDetailActivity`。主题声明有效 `DETAIL` 页面时优先进入该宿主，否则直接进入 `TmdbDetailActivity`。
- Manifest、详情页或桥接初始化失败时，详情宿主只回退一次到原生详情并结束自身，避免返回死循环；页面同时保留显式“原生详情”逃生入口。
- 内置 Eclipse 详情页已覆盖加载、错误、空态、海报降级、收藏、历史进度、线路、选集分页、播放、触控布局和 TV 几何焦点恢复。
- Java 契约测试、JavaScript 状态测试、mobile/leanback 编译和桌面/窄屏 Chromium 视觉检查已纳入本轮验收。真实设备上的旋转、遥控全路径、播放返回和远程主题故障注入仍属于发布前集成验收。
- 本轮暂不抽取宿主统一注入的 focus runtime，也不扩展播放器控制层、设置 Schema、ZIP 安装或跨域资源；这些继续按 M3 之后的里程碑增量推进。


---

## 19. Host API 3：TMDB 详情效果开放设计（2026-07-30）

本阶段把现有原生 TMDB 详情模式中已经成熟的数据与交互能力开放给 WebTheme。目标不是让远程页面重新实现 TMDB、文件保存、外部 Intent 或跨源匹配，而是让主题自行决定布局、文案、显示时机和触发入口，再通过受控语义接口调用原生实现。

### 19.1 当前差距与本阶段目标

当前 `vod.detail@1` 已能渐进返回背景、人物、剧照、单集资料和一组相关推荐，但仍存在以下差距：

- 只公开普通相关推荐，没有区分原生详情已有的 TMDB 个性推荐、豆瓣个性推荐和 AI 个性推荐。
- 人物卡片、剧照和外部链接缺少受控动作接口，官方示例只能展示，不能进入人物详情、预览原图、保存图片或打开外部页面。
- 推荐卡片只能调用 `app.search(title)`，没有复用原生的“当前源匹配，失败后全局搜索”链路。
- AI 推荐理由、推荐详情和“不感兴趣”反馈没有进入 WebTheme 能力模型。
- 页面未消费宿主注入的安全区变量；横向轨道末端、焦点放大光环和系统状态栏附近可能出现内容或焦点目标显示不全。

本阶段完成后，用户主题应能自行组合上述区域，并通过独立函数调用原生效果；官方 Eclipse 详情页必须作为完整参考实现。

### 19.2 兼容策略

- Manifest `schemaVersion` 保持 `2`，页面结构与权限声明格式不变。
- 宿主能力版本提升为 `hostApiVersion: 3`；依赖本节接口的主题可声明 `minHostApi: 3`。
- `vod.detail@1` 保持 major version 不变，新增字段全部为可选字段；旧主题继续读取现有 `people`、`gallery` 和 `recommendations`。
- 主题必须以 `theme.info.capabilities` 和 DTO 中的布尔能力为准，不能仅依据宿主版本假定某个动作一定存在。
- 宿主 API 2 的页面仍可加载；缺少 API 3 时只隐藏新增动作，不得影响基础详情、收藏和播放。

### 19.3 详情 DTO 的增量字段

`vod.detail@1` 增加以下可选结构：

```jsonc
{
  "people": [
    {
      "personRef": "opaque-person-reference",
      "personId": 1,
      "kind": "cast",
      "name": "演员名",
      "role": "角色名",
      "department": "Acting",
      "profile": "https://..."
    }
  ],
  "galleryItems": [
    {
      "imageRef": "opaque-image-reference",
      "preview": "https://...",
      "width": 0,
      "height": 0
    }
  ],
  "recommendationGroups": [
    {
      "id": "personal.ai",
      "title": "AI 为你推荐",
      "source": "ai",
      "items": [
        {
          "recommendationRef": "opaque-recommendation-reference",
          "tmdbId": 456,
          "mediaType": "movie",
          "name": "推荐影片",
          "subtitle": "2025 · 电影",
          "overview": "简介",
          "reason": "因为你最近观看了……",
          "pic": "https://...",
          "backdrop": "https://...",
          "rating": 8.1,
          "tmdbRating": 8.1,
          "doubanRating": 7.9
        }
      ]
    }
  ],
  "externalLinks": [
    {
      "linkRef": "opaque-external-link-reference",
      "label": "TMDB",
      "host": "themoviedb.org"
    }
  ],
  "capabilities": {
    "canOpenPeople": true,
    "canPreviewImages": true,
    "canSaveImages": true,
    "canOpenRecommendations": true,
    "canInspectRecommendations": true,
    "canSendRecommendationFeedback": true,
    "canOpenExternalLinks": true,
    "hasPersonalTmdbRecommendations": true,
    "hasPersonalDoubanRecommendations": true,
    "hasPersonalAiRecommendations": true,
    "hasExternalLinks": true
  }
}
```

兼容和数量规则：

- 现有 `gallery: string[]` 与 `recommendations: item[]` 保留；分别作为无动作主题的简化剧照列表和 `related` 推荐组兼容视图。
- `recommendationGroups` 的固定组 ID 为 `related`、`personal.tmdb`、`personal.douban`、`personal.ai`。空组可省略，主题不得依赖固定顺序。
- 单组最多输出 18 条，总推荐最多输出 72 条；人物仍最多 24 个，剧照仍最多 12 张，外部链接最多 8 条。
- `reason` 只用于解释推荐，不作为 HTML；主题必须使用文本节点渲染。
- 图片预览地址可展示，但预览、保存、人物、推荐和外部链接动作都必须回传 opaque reference，不能以 URL、`tmdbId` 或 `personId` 代替引用。

### 19.4 新增语义动作接口

| 方法 | Manifest 权限 | 输入 | 原生行为 |
| --- | --- | --- | --- |
| `person.open` | `person.open` | `{personRef}` | 打开 `TmdbPersonActivity`，人物作品继续使用原生匹配与导航 |
| `image.preview` | `image.preview` | `{imageRef}` | 打开当前详情会话的原生全屏图片查看器，并定位到所选图片 |
| `image.save` | `image.save` | `{imageRef}` | 下载宿主登记的原图并保存到系统图片目录 |
| `recommendation.open` | `recommendation.open` | `{recommendationRef}` | 当前源匹配成功后打开同一全局主题详情；无匹配时进入原生全局搜索 |
| `recommendation.info` | `recommendation.info` | `{recommendationRef}` | 打开原生推荐详情，显示来源、评分、简介和 AI 理由 |
| `recommendation.feedback` | `recommendation.feedback` | `{recommendationRef, action:"notInterested"}` | 写入受控反馈；主题自行立即移除或弱化该卡片 |
| `external.open` | `external.open` | `{linkRef}` | 仅打开宿主预先生成且通过校验的 HTTP/HTTPS 链接 |

官方 JavaScript 包装：

```js
await fongmi.person.open(person.personRef)
await fongmi.image.preview(image.imageRef)
await fongmi.image.save(image.imageRef)
await fongmi.recommendation.open(item.recommendationRef)
await fongmi.recommendation.info(item.recommendationRef)
await fongmi.recommendation.feedback(item.recommendationRef, 'notInterested')
await fongmi.external.open(link.linkRef)
```

所有方法先返回“请求已接受”或结构化错误。导航、下载和系统选择器属于原生异步副作用，主题不能假定 Promise 完成等于目标 Activity 已完成或文件已经落盘；保存结果继续由原生 Toast/通知反馈。

### 19.5 “当前源匹配 → 全局搜索”语义

`recommendation.open` 固定复用原生 `TmdbNavigation` 语义：

```text
recommendationRef
    → 解析为当前详情会话登记的 TmdbItem
    → 当前内容源可搜索时先执行当前源标题匹配
        → 匹配成功：使用当前全局主题打开匹配到的 VOD 详情
        → 无匹配/当前源不可搜索：打开原生全局搜索并携带标题与图片提示
```

该能力是导航动作，不是跨源数据读取接口：

- 远程主题看不到其他内容源列表、原始源站 ID、搜索响应或评分过程。
- 页面不能指定任意 `siteKey`、搜索范围或匹配结果。
- `tmdbId` 只用于展示和宿主内部识别，不能当作导航参数。
- 当前主题详情不可用时，匹配结果仍按原生详情回退规则打开。

### 19.6 引用、权限与安全边界

- 新增人物、图片、推荐和外链引用由独立的详情动作会话签发；同一详情增量刷新应复用稳定引用。
- 切换来源、切换 `vodId`、重新加载主题、WebView 重建、宿主销毁或安全代际变化后，旧引用立即失效并返回 `SOURCE_CHANGED` 或 `INVALID_ARGUMENT`。
- `image.save` 只接受当前 DTO 已登记的图片引用，不接受任意 URL、文件名、目录或 MIME 类型。
- `external.open` 只接受宿主生成的链接引用；链接必须是普通 HTTP/HTTPS，禁止 `intent:`、`file:`、`content:`、自定义 Scheme、Header/Cookie 拼接和本地地址。
- `person.open` 只允许当前 TMDB 元数据中 `personId > 0` 的人物。
- `recommendation.feedback` 初期只允许 `notInterested`，不开放任意标签、评分或用户画像写入。
- 每个动作仍需同时通过页面硬编码白名单和 Manifest 权限；未声明能力必须返回 `PERMISSION_DENIED`。

### 19.7 官方 Eclipse 示例要求

内置 `theme.json` 申请并演示本节全部权限。详情页至少包含：

1. 普通相关推荐、TMDB 个性推荐、豆瓣个性推荐、AI 个性推荐四个独立横向轨道。
2. AI 卡片聚焦时显示 `reason`；长按、菜单键或辅助按钮可打开推荐说明并提交“不感兴趣”。
3. 人物卡片点击调用 `person.open`，人物图片缺失时保留文字降级。
4. 剧照点击调用 `image.preview`；查看器可前后切换并保存原图，触控与遥控均可操作。
5. 外部链接区域显示宿主提供的 `label`/`host`，点击调用 `external.open`，页面不自行拼接 URL。
6. 推荐卡片点击调用 `recommendation.open`，不再直接把标题交给 `app.search`。
7. 所有新增区域在 DTO 缺失、权限不足或 TMDB 未就绪时独立隐藏，基础详情与播放不受影响。

### 19.8 安全区、横向轨道与完整显示规范

为解决状态栏覆盖、末端卡片被截断和焦点光环裁切，官方示例及后续主题应遵循：

- 页面根容器消费 `--fm-safe-top/right/bottom/left`，并在宿主触发 `fmviewport` 后重新计算布局。
- 横向轨道必须保留左右 `scroll-padding` 和可容纳焦点光环的上下内边距；禁止用负右边距把末端内容推出安全区。
- 卡片获得焦点时，脚本必须把**整张卡片加焦点光环**滚入轨道可视区域，而不只调用页面级 `scrollIntoView`。
- 第一项和最后一项均应有与内容区一致的端部留白；允许屏幕右侧展示“还有内容”的局部预告，但当前焦点项不得局部裁切。
- 电视方向键保持同轨道左右移动；跨轨道上下移动使用几何中心匹配，并在移动完成后同时校正纵向页面和横向轨道。
- 移动端支持触控横向滚动、点击和长按；文字使用省略/行数限制，不得撑破卡片或覆盖相邻区域。

### 19.9 测试与验收

契约与安全：

- DTO 测试覆盖四个推荐组、AI 理由、外链、人物/图片/推荐引用、数量限制和兼容字段。
- 权限测试证明每个方法均需对应 Manifest 权限，首页无法调用详情动作。
- 引用测试证明伪造、跨详情、重载后和过期引用均被拒绝；任意 URL 不能通过图片保存或外链接口进入系统。
- 全局匹配测试覆盖当前源成功、当前源无结果后全局搜索和当前源不可搜索三条路径。

页面与交互：

- JavaScript 测试覆盖四类推荐渲染、能力降级、人物/剧照/外链动作绑定、反馈后的本地移除和焦点恢复。
- 电视宽屏逐一聚焦人物、剧照、四个推荐轨道的首项/中间项/末项，当前项及光环必须完整可见。
- 手机状态栏显示和隐藏、横竖屏、手势导航下，首屏内容不得进入系统栏；图片查看、保存和返回必须正常。
- 完成 Java 单元测试、JavaScript 状态测试、mobile/leanback Debug 构建，并在模拟器上检查日志无 FATAL、应用 ANR 或主题桥接权限异常。


### 19.10 选集详情与电视端交互补充（2026-07-31）

为使 WebTheme 能完整复用原生 TMDB 详情的选集体验，本版本在 Host API 3 内增加以下可选能力：

- 详情 episode DTO 可带宿主签发的 `episodeRef`；主题通过 `episode.info(episodeRef)` 打开原生 `EpisodeDetailDialog`。引用只对应当前详情、当前线路中的实际 `Episode` 对象，不能由页面自行构造。
- `episode.info` 受详情页 Manifest 权限控制；缺少权限或引用时，选集仍可播放，但不显示长按详情入口。
- 电视端主题必须为图片查看器建立显式焦点链：图片获得焦点时按下方向进入底部按钮，底部按钮按上方向回到图片；左右方向在按钮组内移动，返回键关闭查看器。
- 推荐和选集的长按不能只依赖浏览器 `contextmenu`；主题应同时支持触摸/指针长按和遥控器确认键长按，并抑制长按结束后产生的误点击。
- AI 推荐卡片展示图片时按 `pic` → `backdrop` 顺序回退；宿主异步补全 TMDB 图片后必须触发详情增量刷新，主题不能永久停留在缓存的文字降级状态。

### 19.11 选集分组、完整简介与走马灯规范

- 选集数量较多时应按范围分组，范围标签使用绝对集号（例如 `1-20`、`21-40`），切换范围不改变当前线路和详情上下文；官方示例默认以 20 集为电视端一组，避免用户选中末行时看不到简介。
- 当前选集的简介必须完整渲染，不能用固定三行 `line-clamp` 截断；超长文本应允许正常换行，并限制单词/长 URL 造成的横向撑破。
- 选集名称和集标题在未获得焦点时可以省略；获得焦点或处于当前选中状态且确实溢出时，应启用连续走马灯。走马灯只作用于文本轨道，不得改变卡片尺寸或焦点几何。
- 选集范围按钮、选集卡片、播放线路和详情动作必须分别声明焦点行，电视方向键在同一行内移动，跨行时保持与目标几何中心最近。

### 19.12 本轮新增验收项

- 电视模拟器中打开剧照查看器，图片焦点按下方向可进入“上一张/旋转/保存/下一张/关闭”，按钮左右移动和返回均正常。
- AI 推荐卡片使用遥控器确认键长按能打开推荐详情，且短按仍只执行打开推荐。
- AI 推荐存在 `backdrop` 而无 `pic` 时仍显示图片；异步解析补全后页面能刷新图片。
- 36、100 和 500 集详情分别验证范围分组、简介可见、选中卡片走马灯及长按 `episode.info`。
---

## 20. 下一阶段架构评估与实施计划（2026-08-03）

本节记录 Host API 3 首页/详情页闭环完成后的下一阶段建议，作为后续实现、评审和拆分任务的当前基线。它补充 §13～§17 的早期路线，不改变 §18～§19 已冻结的兼容与安全边界。

### 20.1 当前定位与结论

当前能力应准确描述为 **WebHome V2 可插拔页面主题**，而不是已经覆盖整个 App 的任意全局换肤：

- `WebThemePage` 当前只包含 `HOME` 和 `DETAIL`；搜索、历史、收藏、设置和播放器尚未成为独立主题页面。
- 站点自己的 `homePage` 仍优先于全局主题；全局主题主要作为无站点首页时的统一页面渲染层。
- 播放器内核、解析、DRM、字幕、音轨、画中画和生命周期继续由原生实现负责。
- 设置值定义、校验、权限和持久化继续由原生负责。
- 远程 V2 页面通过精确 origin 的 WebMessage 通道调用页面白名单能力，不直接暴露完整的站点页 `HomeWebBridge`。
- 首页、分类、详情、播放和详情动作继续使用当前页面会话内的不透明引用，切源、重载和宿主销毁后旧引用失效。

这一边界继续保持：**视觉和页面布局可替换，数据、业务、播放与恢复能力保持原生可信；每个页面可独立失败并回退。**

### 20.2 需要先收口的具体问题

#### 20.2.1 Manifest 字段与实际运行时能力必须一致

内置 `theme.json` 已声明：

```json
"player": {
  "engine": "native",
  "chrome": "tokens"
}
```

截至 2026-08-03，运行时已采用第一种方案：`WebThemeManifest` 只确认 `player`/`tokens` 为对象并记录其存在，Schema 以 `x-webhtv-status: reserved` 标记，Devkit 校验器输出警告；Host API 3 不解释、执行或通过 `theme.info` 暴露其中配置。因此这些字段仅用于向前兼容，不属于稳定公共能力。

正式支持前仍需增加类型化的 `PlayerPresentation`/设计变量模型、能力协商和契约测试；在此之前不得依据这些字段改变原生播放器或布局行为。

#### 20.2.2 能力定义需要单一事实来源

在本轮 P0 之前，页面方法、Manifest 权限、宿主白名单和 `theme.info.capabilities` 分别维护，容易出现文档、Manifest 和运行时不一致。当前已由 `WebThemeCapabilityRegistry` 统一维护；继续增加页面时仍必须在注册表中扩展同一条目，并同步 Schema 漂移测试和兼容文档。注册表至少包含：

```text
method
permission
page
contractVersion
legacyAllowed
manifestRequired
```

运行时由注册表直接驱动：

- Manifest 权限过滤；
- Bridge 方法授权；
- `theme.info.capabilities`。

创作 Schema 与 Devkit 校验器读取同一份 Schema；Java 漂移测试会把 Schema 的页面权限枚举与注册表逐页比较，避免两份跨语言定义静默分叉。文档兼容矩阵也应随注册表和 Schema 同步更新。

#### 20.2.3 Runtime 与 Bridge 在扩页前需要拆分

`HomeWebController` 同时承担 WebView 生命周期、Manifest 加载、Bridge 会话、扩展注入、焦点、原生播放前媒体暂停和错误恢复；`WebHomeThemeBridge` 同时承担首页、详情、收藏、历史、播放、TMDB 动作和导航。后续不应继续把搜索、历史列表和设置接口直接堆入这两个类。

推荐的目标拆分为：

```text
WebThemeRuntime
├── WebThemeManifestResolver
├── WebThemeSession
│   ├── generation / cancellation
│   ├── access references
│   ├── play references
│   └── detail action references
├── WebThemeCallRouter
└── WebThemePageHost

WebThemeCallRouter
├── HomeApi
├── DetailApi
├── ListApi
├── NavigationApi
├── PlayerApi
└── UiApi
```

第一阶段只做行为不变的抽取，`HomeWebController` 可继续作为兼容 façade，避免在重构同时改变协议。

#### 20.2.4 电视焦点能力需要成为公共 Runtime

当前官方首页和详情页分别维护焦点、横向轨道滚动和恢复逻辑。增加更多主题页面前，应抽取宿主注入的公共 focus runtime，统一处理：

- `fmviewport` 与安全区；
- 同焦点行左右移动；
- 跨焦点行的几何中心匹配；
- 横向轨道首尾留白和完整滚入；
- 动态追加内容后的焦点恢复；
- 页面返回后的焦点恢复；
- 遥控确认键短按/长按分离；
- 自动分页的去重和过期响应隔离。

主题仍负责声明焦点元素和焦点行，宿主 Runtime 负责算法版本、兼容和修复。

### 20.3 分阶段实施顺序

#### P0：契约与运行时收口

优先完成且不改变现有首页/详情视觉行为：

1. 增加正式的 `theme-v2` JSON Schema 和 Devkit 校验入口；
2. 建立统一能力注册表；
3. 明确 `player`、后续 `tokens` 等未实现字段的保留策略；
4. 统一稳定错误码：

```text
PERMISSION_DENIED
INVALID_ARGUMENT
SOURCE_CHANGED
STALE_REFERENCE
PAGE_UNAVAILABLE
NATIVE_FALLBACK
RATE_LIMITED
RESPONSE_TOO_LARGE
```

5. 记录 Manifest 加载、页面解析和回退原因，但日志不输出敏感 payload、Cookie 或完整令牌 URL；
6. 维护 Host API、页面契约和权限的生成式兼容矩阵；
7. 增加 Manifest 更新、缓存、非法字段、错误码和能力漂移测试。

验收：V1 首页行为不变，V2 首页/详情现有测试全部通过，Manifest 校验、Bridge 授权和 `theme.info` 使用同一份能力定义。

**2026-08-03 实施状态：** 本轮已完成 P0 的契约核心：正式 JSON Schema 与 Devkit 校验入口、统一能力注册表、保留字段策略、规范错误码/旧错误别名，以及对应的 Java/Python 契约测试。Manifest 运行时、Bridge 授权和 `theme.info.capabilities` 已统一依赖能力注册表；Schema 权限枚举由漂移测试约束。受信和远程 V2 Bridge 均通过 `Error.code` 暴露规范码，同时保持 `Error.message` 的旧别名；过期不透明引用返回 `STALE_REFERENCE`，Android 与 Devkit 均拒绝非法 UTF-8 Manifest。第 5～7 项继续按独立的运维与发布任务推进；第 5 项的首个增量见下述运行日志状态，第 7 项的进程内更新/缓存/last-known-good 基础矩阵见第二增量；持久缓存、ETag/TTL 和更完整的生成式兼容矩阵仍未完成。

**2026-08-05 运行日志状态（第一增量）：** 已为 WebTheme Manifest 与文档生命周期增加结构化诊断，使用 `operation + generation` 关联请求，并以稳定原因码区分 `manifest_io`、`manifest_invalid`、`page_unavailable`、`bridge_unavailable`、`load_timeout`、`empty_document`、`web_resource_error`、`http_error`、`render_process_gone` 和 `stale_operation`。持久日志只记录页面、低基数目标模式、数值错误码以及移除 userinfo/query/fragment 的 URL，不记录 Manifest payload、Cookie、Bridge nonce、自由格式异常文本或页面控制台原文；控制台持久日志改为级别、行号、消息长度和脱敏来源 URL，原文只保留给现有调试界面回调。对应格式、原因分类、URL 脱敏和控制器 wiring 回归测试已补齐。

**2026-08-05 Manifest 更新/缓存状态（第二增量）：** Manifest Loader 现以 `Manifest URL + platform target` 为键维护最多 8 项的进程内 LRU 已验证缓存：非强制加载命中缓存时不重复读取；强制刷新成功后替换缓存；强制刷新发生 I/O 失败或校验失败时，继续使用同键上一份已验证 Manifest；冷启动或无缓存失败仍抛给控制器并走原生 fallback，不会把失败内容写入缓存。Resolver 会携带 `REFRESHED`、`CACHE_HIT` 或 `LAST_KNOWN_GOOD` 状态，控制器记录 `manifest_cache_fallback` 及稳定失败分类，并在最终 `manifest_load_resolved` 上标记 `last_known_good`，不持久化自由格式异常文本。单元测试覆盖首次刷新、按平台 target 隔离、缓存命中、刷新替换、I/O 与非法 Manifest 回退以及冷缓存失败。重装本增量 `mobileArm64_v8aDebug` 后，`emulator-5562` 已回归首页进入详情及返回首页，结构化生命周期事件完整，进程保持存活且未发现崩溃或 ANR；设备仍使用内置 Manifest，因此不把远程刷新失败注入记为已验证。本增量仅保证当前进程内回退，磁盘持久化、跨进程恢复、ETag/TTL 和手动回滚仍未实现。

**2026-08-06 Manifest 持久回退状态（第三增量第一切片）：** 远程 Manifest 每次成功校验后，会以 `Manifest URL + platform target` 为键把原始 JSON 最佳努力写入应用私有 `noBackup` 目录；文件名只保留键的 SHA-256 摘要，磁盘条目与进程内缓存同样最多保留 8 项。写入使用同目录临时文件、`fsync`、备份和重命名发布；持久化失败不会拒绝已经校验成功的新 Manifest。进程内缓存为空且远程读取或校验失败时，Loader 才读取磁盘 LKG，并再次执行大小、严格 UTF-8、Schema/目标平台校验；有效条目返回既有 `LAST_KNOWN_GOOD` 状态并回填进程内缓存，损坏或 0 字节条目会被清理，仍无可用版本时继续抛出原始刷新失败并走原生 fallback。内置 Eclipse asset 不落盘，进程重启后仍优先尝试远端源，因此本切片不冒充 ETag/TTL 缓存或手动回滚。单元测试覆盖进程内缓存清空后的恢复、平台隔离、损坏/截断条目、写盘失败、文件名脱敏、容量上限和超限拒绝；真实远程故障注入仍是设备验收项。

**2026-08-06 Manifest 条件刷新状态（第三增量第二切片）：** 远程 Manifest 的进程内与磁盘条目现同时保存已验证 JSON、规范化 ETag 和最近验证时间，并采用宿主控制的 15 分钟新鲜期。非强制加载命中新鲜条目时直接返回 `CACHE_HIT`，包括进程重启后从磁盘恢复的条目；条目到期或调用方强制刷新时，Loader 携带可用 ETag 发出 `If-None-Match` 条件请求。`304 Not Modified` 仅延长验证时间并保留原 Manifest，响应未提供可用 ETag 时继续沿用旧值；`200` 响应仍需通过大小、严格 UTF-8、Schema 与目标平台校验后才替换缓存。网络、HTTP 或校验失败不会延长旧条目时间，仍返回 `LAST_KNOWN_GOOD`；没有可用缓存时继续抛错并走原生 fallback。系统时间回拨会把条目视为过期，非法、超长或含控制字符的 ETag 不会写入请求头。磁盘格式升级为带魔数、时间戳和十六进制 ETag 的 V2 包装，上一切片的纯 JSON 文件按“已过期但可回退”兼容读取。单元测试覆盖跨进程新鲜命中、到期与强制条件刷新、`304` 续期、`200` 替换、离线回退、时间回拨、冷缓存 `304`、请求头净化、元数据往返、旧格式兼容和最大 Manifest 边界；真实设备上的远程故障注入仍保留为发布验收项。

**2026-08-06 Manifest 可控回滚状态（第三增量第三切片）：** 远程 Manifest 缓存格式升级为 V3，每个 `Manifest URL + platform target` 最多保留当前与上一份两个已校验版本，磁盘总条目仍限制为 8。新候选版本先标记 `activationPending`，只有首个目标文档完成才由控制器确认；在确认前发生 Bridge 不可用、加载超时、主框架资源/HTTP 错误或渲染进程退出时，会按候选 revision 原子切回上一稳定版本，保留失败候选并标记为 blocked，防止同一失败内容被再次自动提升。远端发布新 revision 后可重新进入候选流程；设置页新增“版本恢复”，稳定状态可手动回滚，已回滚状态可手动重试，重试失败仍自动恢复稳定版本。磁盘当前内容损坏时不会把已知 blocked 候选误提升为稳定版本；V2 与纯 JSON 缓存继续兼容迁移。单元测试覆盖双版本上限、首次确认、失败回滚状态机、过期 revision、防重复提升、手动回滚/重试、V3 往返与损坏恢复，Mobile/Leanback arm64 Debug 单测和 APK 构建均通过。`emulator-5562` 实际注入“候选主页 404 + 上一稳定页 200”：日志出现 `manifest_load_resolved(candidate) → fallback(http_error, 404) → manifest_rollback → document_ready(stable)`；设置页识别 blocked 版本并展示“重试”，确认后提示“主题版本已更新”，随后测试 Manifest 强制刷新失败仍安全回滚。验证中同时修复了 Android 9 不支持 `String.formatted` 导致远程 SDK 注入崩溃的问题，改用 Android 兼容的字面替换并补回归测试；测试结束后已恢复模拟器原偏好与内置主题，未发现新的崩溃或 ANR。

**2026-08-06 远程主题数据隔离状态（第三增量第四切片）：** 受信站点页和内置 Eclipse 继续使用默认 WebView Profile；远程 V2 按规范化精确 Origin 派生稳定、无主机明文的命名 Profile，不同 Origin（包括非默认端口）不复用，同一 Origin 在进程重建后仍映射到同一分区。Profile 切换通过销毁并替换 WebView 完成，并在新 WebView 构造后、任何其他配置前调用 `WebViewCompat.setProfile`；Cookie 请求头和 Cookie 接受策略改用当前 Profile 的 `CookieManager`，远程 Profile 禁止第三方 Cookie。由此远程主题不复用默认 Profile 中受信站点的 Cookie、DOM/WebStorage、缓存及由这些状态形成的登录会话，只能延续自身 Origin 对应 Profile 的状态。提供方缺少 `MULTI_PROFILE`、Origin 无效或 Profile 创建失败时，宿主拒绝加载远程文档并以 `data_isolation_unavailable` 走原生 fallback，不降级到共享默认 Profile。WebView 替换同时推进主题 generation、取消旧 Bridge/播放请求，并让 mobile 首页、详情页和扩展调试宿主只操作控制器当前 WebView；已销毁旧 WebView 的错误、导航、资源拦截和渲染进程回调会被忽略或阻断。单元测试覆盖受信/内置默认分区、同 Origin 跨进程稳定映射、跨 Origin/端口隔离、Profile 切换及宿主 wiring；Leanback/Mobile arm64 Debug 完整单测与 Mobile APK 构建通过。`emulator-5562` 使用 `com.android.webview 91.0.4472.114`，实际远程清单解析后记录 `remote data profile unavailable provider_feature=multi_profile` 和 `fallback(... reason=data_isolation_unavailable)`，未发现崩溃并显示原生首页；测试后按相同 SHA-256 恢复原偏好，本地 Manifest 再次达到 `document_ready`。因此当前设备只验证了失败关闭路径，支持 `MULTI_PROFILE` 的真实设备或新版提供方上的实际 Profile 分区、进程重启后状态边界仍是发布门槛。

**2026-08-07 生成式兼容矩阵状态（第三增量第五切片）：** 新增 [`webtheme-compatibility-matrix.md`](webtheme-compatibility-matrix.md)，从 `WebThemeCapabilityRegistry` 与 `WebThemePage` 确定性生成当前 Manifest Schema、Host API、页面基础契约，以及每个 Bridge method 的 capability ID、权限、页面范围、契约版本、V1 legacy 状态和 Manifest 权限要求。生成器只存在于测试源码，不进入 APK；运行时注册表提供不可变兼容条目视图。矩阵测试会逐条遍历所有 method × page 组合，验证页面范围、权限缺失拒绝、V1 allowlist、capability ID，并要求生成结果与提交的 Markdown 完全一致；已有 Schema 漂移测试继续保证页面权限枚举与同一注册表同步。新增能力或页面若只修改运行时、Schema 或文档中的任意一处，测试都会失败。本切片的 Mobile/Leanback arm64 Debug 完整单测（1653/1662，均 0 failure/error）、Devkit 校验器 9 项测试、Mobile APK 构建及 emulator-5562 覆盖安装冒烟均已通过；设备日志达到 `document_ready`，截图确认内置主题正常显示。

#### P1：公共 WebTheme Runtime 与焦点层

1. 抽取 Manifest resolver、页面 host、会话 generation/cancellation 和 Bridge router；
2. 保持旧的公开调用签名和回退行为不变；
3. 引入公共 focus runtime，并先迁移内置 Eclipse 首页和详情页；
4. 统一切源、重载、暂停、恢复、销毁和原生播放返回后的会话失效规则。

验收：旧请求不能在切源或销毁后更新页面；首页和详情独立回退；不会产生重复 Bridge、返回死循环或丢失遥控焦点。

**2026-08-04 实施状态（第一增量）：** 已抽取 `WebThemeManifestResolver` 和 `WebThemeSession`，`HomeWebController` 继续保留原有公开/包内调用签名作为兼容 façade。会话现在以同一快照持有 generation、access/play/detail action 引用仓库；受信与远程 Bridge 调用都同时固定到发起调用时的主题 generation，远程调用还继续固定 document generation、精确 origin 与 nonce。任意主框架新文档开始（包括同源页面内导航）、切源、重载、销毁、WebView 重建、空文档恢复和主框架失败都会推进 generation 并替换全部不透明引用仓库；暂停、恢复、页面完成和无需重载的上下文切换只取消旧异步请求，保留当前文档仍可使用的引用。

**2026-08-04 实施状态（第二、第三增量）：** 已抽取 `WebThemePageHost` 页面上下文快照，`HomeWebController` 保留兼容 façade，并通过控制器级锁原子发布和读取页面/会话状态；Bridge 以同一运行时快照构建 `CallContext`，`theme.info` 与详情异步发布继续复用该调用快照，迟到的旧页面完成回调不会向新页面重复注入 SDK。已抽取 `WebThemeCallRouter`，统一稳定 API 的 HOME、LIST、DETAIL、NAVIGATION、PLAYER、UI 分组、权限校验和 generation 活跃性检查，业务 handler 行为保持不变。`eclipse-focus-navigation.js` 现在提供共享的横向与几何焦点算法，Eclipse 详情页已接入共享几何导航，首页继续复用共享横向导航；对应的 Java wiring、路由、页面 host、会话和 JavaScript 回归测试已补齐。

**2026-08-05 mobile 阶段性验收：** `emulator-5562` 已安装本轮 `mobileArm64_v8aDebug` 构建并验证 Eclipse 首页、WebTheme 详情页、方向键横向焦点、原生播放器进入与首帧、播放返回详情、Home 键后台切换与恢复；进程在这些路径中保持存活，未发现崩溃或 ANR。首页和详情页都实际产生了 `manifest_load_started → manifest_load_resolved → document_load_started → document_ready` 事件。该模拟器显示面固定为 `1920×1080`，锁定 `user_rotation` 未改变 `SurfaceOrientation`，因此不能据此宣称旋转通过；Leanback 运行时也尚无分配设备，本轮仅完成对应单测和 APK 构建。

本轮已完成 P1 的代码拆分、Eclipse 首批迁移、结构化生命周期诊断、进程内及进程重启后的 Manifest 更新/缓存/last-known-good 基础矩阵、ETag/TTL 条件刷新、可控回滚、远程主题数据隔离代码与旧提供方失败关闭验收，以及 mobile 关键路径阶段性验收，但不宣称整个 P1 的发布验收已完成：支持 `MULTI_PROFILE` 的真实设备或新版提供方上的实际 Profile 分区和跨进程状态边界、可旋转 mobile 设备、真实 Leanback 设备上的遥控全路径，以及 DNS/TLS/超时、渲染进程退出等更完整远程故障矩阵仍需验证；其余 Bridge/扩展调试日志的敏感字段审计继续按独立发布任务推进。

#### P2：通用列表页面

新页面按以下顺序增量交付：

```text
搜索 → 历史 → 收藏
```

建议契约：

```text
vod.search@1
history.list@1
history.remove@1
favorite.list@1
favorite.status@1
favorite.set@1
```

三类页面共用列表 DTO 和 Runtime，统一包含分页、空态、错误态、`truncated`、图片、当前源约束、不透明引用和焦点恢复。搜索默认只搜索当前源；多源搜索继续作为单独高权限原生动作。

每增加一页都必须保留原生 fallback，不复制一套新的 WebView 生命周期或分页实现。

#### P3：原生全局设计变量

真正覆盖原生页面的“全局视觉皮肤”应通过受控设计变量实现，而不是允许远程 CSS 操作 Android View。第一批建议字段包括：

```text
canvas
surface
surfaceElevated
text
textMuted
accent
focus
divider
radius.small / medium / large
spacing.small / medium / large
```

约束：

- 只接受预定义语义字段；
- 对颜色、尺寸、圆角和间距做类型与范围校验；
- 不接受 Android resource ID、类名或任意原生样式表达式；
- 不允许设计变量改变业务可见性、权限或安全行为；
- 对电视焦点色、文字与背景做最低可读性检查；
- 应用启动时使用已验证缓存，避免切换主题产生明显闪白或布局重建。

首批原生消费面建议限制为 WebHome 外壳、详情动作/弹窗和播放器控制层。播放器仍保持原生 engine，仅允许控制层视觉消费 tokens。

#### P4：分发、更新与高级页面

在公共远程主题生态开放前再增加：

- ETag / TTL 条件刷新（已完成）；
- 持久化 last-known-good 与最多双版本的自动/手动回滚（已完成）；
- Manifest 与静态资源哈希；
- 签名主题包；
- 本地目录或 ZIP 安装；
- 设置 Schema；
- 声明式原生 Player Chrome；
- 主题预览、来源和权限管理 UI。

没有签名、完整性校验和回滚机制前，不建设公开主题市场。

### 20.4 安全和发布门槛

当前远程 V2 的 deny-by-default、精确 origin、页面权限交集、私网地址拦截、无重定向 Manifest、短生命周期引用和独立 Profile 数据边界继续保留。后续至少补充：

- 在支持 `MULTI_PROFILE` 的真实设备或新版提供方上验证远程 Profile 的 Cookie、DOM/WebStorage、缓存与登录态隔离，以及同 Origin 跨进程延续和跨 Origin 不复用；当前旧提供方只完成失败关闭验收；
- `external.open` 首次域名或非 HTTPS 链接的用户可见确认；
- 引用预算耗尽时返回可区分的错误或 `truncated`，而不是不可诊断的通用失败；
- 继续扩充远程 Manifest 故障矩阵，覆盖 DNS/TLS/超时、渲染进程退出和跨进程 blocked/retry；当前已覆盖进程/磁盘 last-known-good、条件刷新、候选主页 404 自动回滚和设置页重试；
- 恶意 Manifest、IDN/IPv6/私网地址、超长消息、能力绕过和旧 generation 回调的安全测试；
- 500 集详情、多推荐轨道、频繁切源和低配电视 WebView 的内存与响应时间测试。

真实设备上的旋转、Leanback 遥控全路径，以及 DNS/TLS/超时、渲染进程退出等更完整远程故障矩阵仍是发布门槛，不能只由 Java 单元测试或单一 404 注入替代。

### 20.5 明确非目标与下一项工作

下一阶段不做：

- 不把 `VideoActivity` 或播放器内核迁入 WebView；
- 不向主题暴露原始 `Vod`、`Site`、播放地址、解析器或内部 Bean；
- 不同时实现搜索、历史、收藏、设置和播放器；
- 不在缺少签名与完整性校验时先做 ZIP 或主题市场；
- 不允许任意 CSS 或脚本反向控制原生 Android 布局。

当前推荐的下一个独立变更是 **P0 运维与发布加固：生成式兼容矩阵最小闭环**。该切片只从统一能力注册表生成或校验 Host API、页面契约、Manifest 权限与 `theme.info.capabilities` 的兼容矩阵，补齐 capability/version drift 测试和发布前检查；不同时扩展页面、主题市场、签名包或播放器。Bridge/扩展/开发调试日志的敏感字段审计继续作为随后独立切片。在支持 `MULTI_PROFILE` 的真实设备或新版提供方上补齐实际 Profile 分区及进程重启状态边界，在可旋转 mobile 设备和真实 Leanback 设备上补齐旋转与遥控全路径，以及 DNS/TLS/超时、渲染进程退出等更完整远程故障矩阵，仍是发布门槛。`emulator-5562` 已通过的首页/详情、后台恢复、原生播放与返回、候选主页 404 自动回滚、设置页 blocked 版本重试和远程数据隔离失败关闭路径不再重复列为未验证项；P0 尚未完成的运维与发布加固仍不与页面功能增量混合推进。
