# Spider 接入协议

WebHTV 支持 Java/JAR、QuickJS JavaScript、Python 和 CatSpider HTTP 协议。所有运行时最终都映射到 `com.github.catvod.crawler.Spider`。

## Java/JAR 方法

```java
void init(Context context, String extend)
String homeContent(boolean filter)
String homeVideoContent()
String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)
String detailContent(List<String> ids)
String searchContent(String key, boolean quick)
String searchContent(String key, boolean quick, String pg)
String playerContent(String flag, String id, List<String> vipFlags)
String liveContent(String url)
boolean manualVideoCheck()
boolean isVideoFormat(String url)
Object[] proxy(Map<String, String> params)
String action(String action)
void destroy()
```

## 生命周期

1. `Site` 反序列化后由 Loader 创建 Spider。
2. `init(Context, String extend)` 接收站点 `ext`；远程 URL 会先拉取文本。
3. UI 依次调用首页、分类、详情、搜索、播放。
4. 换源和本地代理按需调用 `proxy`。
5. 配置清理、应用清理或缓存清理时调用 `destroy()`。

## JS 方法

QuickJS 模块可 `export default` 对象/工厂函数，也可导出 CatVod 兼容的 `__jsEvalReturn()`。

| 方法 | 参数 | 返回 | 对应 Java |
| --- | --- | --- | --- |
| `init(ext)` | 字符串或对象 | 任意 | `init(Context, String)` |
| `home(filter)` | boolean | string | `homeContent` |
| `homeVod()` | 无 | string | `homeVideoContent` |
| `category(tid, pg, filter, extend)` | string,string,boolean,object | string | `categoryContent` |
| `detail(id)` | string | string | `detailContent`；只传 `ids[0]` |
| `search(key, quick)` / `search(key, quick, pg)` | string,boolean[,string] | string | `searchContent` |
| `play(flag, id, flags)` | string,string,array | string | `playerContent` |
| `live(url)` | string | string | `liveContent` |
| `sniffer()` | 无 | boolean | `manualVideoCheck` |
| `isVideo(url)` | string | boolean | `isVideoFormat` |
| `proxy(params)` | object；CatVod 模式另有 `proxy(segments, headers)` | array/string | `proxy` |
| `action(action)` | string | string | `action` |
| `destroy()` | 无 | 任意 | `destroy` |

JS 全局能力：

| 方法 | 说明 |
| --- | --- |
| `req(url, options)` | 同步请求，返回 `{ code, headers, content }` |
| `_http(url, options)` | 无 `complete` 时同步；有 `complete` 时异步 |
| `pdfh(html, rule)` | HTML 单节点/属性选择 |
| `pdfa(html, rule)` | HTML 列表选择 |
| `pdfl(html, rule, texts, urls, urlKey)` | 列表结构化 |
| `joinUrl(parent, child)` | URL 合并 |
| `md5X(text)` | MD5 |
| `aesX(mode, encrypt, input, inBase64, key, iv, outBase64)` | AES |
| `rsaX(mode, pub, encrypt, input, inBase64, key, outBase64)` | RSA |
| `s2t` / `t2s` | 简繁转换 |
| `getPort()` / `getProxy(local)` | 本地端口/代理地址 |
| `js2Proxy(dynamic, siteType, siteKey, url, headers)` | CatVod 兼容代理 URL |
| `setTimeout` / `clearTimeout` | QuickJS 定时器 |
| `local.get(rule, key)` / `local.set(rule, key, value)` / `local.delete(rule, key)` | Native Prefers 存储 |

JS `req/http` options：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `method` | `get` | `get`、`post`、`header` |
| `headers` | `{}` | 请求 headers |
| `data` | 空 | POST 数据，配合 `postType` |
| `body` | 空 | 原始请求体；`data` 为空且含 `Content-Type` 时使用 |
| `postType` | `json` | `json`、`form`、`form-data` |
| `timeout` | `10000` | 毫秒 |
| `redirect` | `1` | `1` 跟随，`0` 不跟随 |
| `buffer` | `0` | `0` 文本，`1`/`3` bytes，`2` Base64 |

## Python 方法

继承 `base.spider.Spider`：

| 方法 | 参数 | 返回 |
| --- | --- | --- |
| `init` | `extend=""` | 任意 |
| `homeContent` | `filter` | string |
| `homeVideoContent` | 无 | string |
| `categoryContent` | `tid, pg, filter, extend` | string |
| `detailContent` | `ids` | string |
| `searchContent` | `key, quick, pg="1"` | string |
| `playerContent` | `flag, id, vipFlags` | string |
| `liveContent` | `url` | string |
| `localProxy` | `param` | list |
| `isVideoFormat` | `url` | bool |
| `manualVideoCheck` | 无 | bool |
| `action` | `action` | string |
| `destroy` | 无 | 任意 |
| `getName` | 无 | string |
| `getDependence` | 无 | list |

Python 基类提供 `loadSpider/loadModule/regStr/removeHtmlTags/cleanText/fetch/post/html/str2json/json2str/getProxyUrl/log/getCache/setCache/delCache`。`getDependence()` 返回依赖模块名列表（不带 `.py`）。

## CatSpider HTTP 协议

`api` 是 `http.../spider/...` 绝对地址时走 CatSpider。所有路由使用 POST JSON：

| Java 方法 | CatSpider 路由 | 请求字段 |
| --- | --- | --- |
| `init` | `/init` | `{}` |
| `homeContent` | `/home` | `{}` |
| `categoryContent` | `/category` | `id`, `page`, `filters` |
| `detailContent` | `/detail` | `id` |
| `searchContent` | `/search` | `wd`, `page` |
| `playerContent` | `/play` | `flag`, `id` |

响应中的 `{ code, data }` 会被解包，`data` 为数组时会包装成 `{ list: data }`。

## 返回协议

分类、搜索、首页：

```json
{
  "class": [{ "type_id": "1", "type_name": "电影" }],
  "filters": {
    "1": [{ "key": "area", "name": "地区", "value": [{ "n": "大陆", "v": "大陆" }] }]
  },
  "list": [{ "vod_id": "1", "vod_name": "示例" }],
  "page": 1,
  "pagecount": 1,
  "total": 1
}
```

详情：

```json
{
  "list": [{
    "vod_id": "1",
    "vod_name": "示例",
    "vod_play_from": "主线$$$备用线",
    "vod_play_url": "第1集$id-1#第2集$id-2$$$第1集$backup-1"
  }]
}
```

播放、字幕、弹幕、代理结构见 [播放结果与播放器接入](player.md)、[字幕接入](subtitle.md)、[弹幕接入](danmaku.md)、[本地代理与服务端接口](proxy-server.md)。

## 错误与资源

1. 所有字符串方法返回合法 JSON，不能返回 `null` 或异常文本。
2. 捕获上游异常并返回空结果；不要把 HTML 当 JSON。
3. `destroy()` 释放线程、请求、WebView、脚本上下文、流和缓存。
4. Proxy 返回的流和响应资源由调用方使用后关闭；不要缓存长生命周期流。

## 源码依据

- `catvod/src/main/java/com/github/catvod/crawler/Spider.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/CatSpider.java`
- `quickjs/src/main/java/com/fongmi/quickjs/crawler/Spider.java`
- `chaquo/src/main/python/base/spider.py`
