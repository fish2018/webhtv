# 点播源接入

点播源位于顶层 `sites` 数组。`type` 决定请求协议或 Spider 运行时；`key` 是站点唯一标识。

## 字段表

| 字段 | 类型 | 默认值 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `key` | string | `""` | 是 | 站点唯一标识；收藏、历史、详情、播放和本地 API 都用它定位站点 |
| `name` | string | `""` | 是 | 展示名 |
| `type` | number | `0` | 是 | 协议/运行时类型，取值见下表 |
| `api` | string | `""` | 是 | HTTP API 地址或 Spider 类名/脚本入口 |
| `jar` | string | `""` | 否 | 站点专用 JAR，为空时继承顶层 `spider` |
| `ext` | string/object | `""` | 否 | 传给 Spider `init` 的扩展参数；`type=4` HTTP 源会在首页请求前拉取远程 URL 文本，Spider 模式直接传递原始字符串 |
| `click` | string | `""` | 否 | WebView 解析点击脚本 |
| `playUrl` | string | `""` | 否 | 站点级播放前缀或解析辅助 |
| `homePage` | string | `""` | 否 | WebHome 首页；别名 `home_page`、`webHome`、`web_home` |
| `chromeMode` | string | 端默认 | 否 | WebHome 浏览器 UI 模式 |
| `webHomeChrome` | object/string | `null` | 否 | WebHome chrome 配置 |
| `extensions` | JSON | `null` | 否 | WebHome 站点扩展配置 |
| `hide` | number | `0` | 否 | `1` 隐藏站点 |
| `indexs` | number | `0` | 否 | `1` 作为索引站点进入聚合搜索 |
| `timeout` | number | `15` | 否 | 播放超时秒数；最小按 `1` 处理 |
| `searchable` | number | `1` | 否 | `0` 永久禁用，`1` 启用，`2` 临时禁用但允许 App 恢复 |
| `changeable` | number | `1` | 否 | `0` 永久禁用换源，`1` 允许，`2` 临时禁用但允许 App 恢复 |
| `quickSearch` | number | `1` | 否 | `1` 参与快速搜索 |
| `categories` | string[] | `[]` | 否 | 限定分类列表；空数组表示不限制 |
| `header` | object | `{}` | 否 | 站点级请求 header |
| `style` | object | `null` | 否 | 卡片样式；不提供时继承默认 `rect` |

## 站点类型

| `type` | 名称 | 行为 |
| --- | --- | --- |
| `0` | XML API | 首页/分类按 XML 解析；分类/详情带 `ac=videolist` |
| `1` | JSON API | 首页/分类按 JSON 解析；分类/详情带 `ac=detail`，筛选参数以 `f={json}` 发送 |
| `2` | JSON API 兼容 | 按 JSON 解析；分类/详情带 `ac=detail`，不追加 `f` |
| `3` | Spider | 按 `api` 分发到 JAR、JS、Python 或 CatSpider |
| `4` | HTTP API + Base64 ext | 首页带 `filter=true`，分类带 `ext={base64(extend)}`，播放带 `play`、`flag` |

`type=3` 的 `api` 分发规则：

| `api` 形态 | 运行时 |
| --- | --- |
| `http.../spider/...` | CatSpider HTTP 协议 |
| `*.py` / 含 `.py` | Python Spider |
| `*.js` / 含 `.js` | QuickJS Spider |
| `csp_*` | JAR/JAVA Spider |
| 其它 | `SpiderNull`，站点不可用 |

## WebHome Chrome

`chromeMode` 支持手机端 `normal`、`edge`、`immersive` 和电视端 `tv-normal`、`tv-toolbar-hidden`、`tv-overlay`、`tv-full`；跨端配置里的 `edge` 在 TV 端映射为 `tv-full`。`webHomeChrome` 对象可写：

```json
{
  "mode": "edge",
  "statusBarStyle": "light",
  "navigationBarStyle": "light",
  "restoreAffordance": true,
  "scrim": true
}
```

## 示例

```json
{
  "key": "demo",
  "name": "演示点播源",
  "type": 3,
  "api": "csp_Demo",
  "jar": "./spider.jar",
  "ext": { "host": "https://api.example.com" },
  "searchable": 1,
  "changeable": 1,
  "quickSearch": 1,
  "timeout": 15,
  "categories": ["电影", "剧集"],
  "header": { "User-Agent": "Mozilla/5.0" },
  "style": { "type": "rect", "ratio": 1.33 }
}
```

## 无效配置

- `key` 为空、`type` 不支持、`api` 为空或不可达。
- JAR、JS、Python 或 `ext` 加载失败。
- Spider 抛异常、返回空 JSON 或 HTML 错误页。
- 上述情况通常会退回 `SpiderNull` 或站点加载失败，不应影响其它站点。

## 验证清单

1. 首页能返回分类和推荐。
2. 分类能翻页且筛选值能传回。
3. 详情能返回至少一个线路和一集。
4. 搜索和快速搜索按配置生效。
5. 播放返回直链或可解析地址。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Site.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java`
- `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java`
