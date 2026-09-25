# 扩展包接入

站点和直播源通过 `type`、`api`、`jar`、`ext` 选择运行时。扩展 JAR 可以由顶层 `spider` 或站点/直播 `jar` 提供。

## 运行时选择

| `api` 形态 | 运行时 |
| --- | --- |
| `http.../spider/...` | CatSpider HTTP 协议 |
| `*.py` / 含 `.py` | Python/Chaquopy |
| `*.js` / 含 `.js` | QuickJS |
| `csp_*` | JAR/JAVA |
| 其它 | `SpiderNull` |

## JAR/JAVA

1. 继承 `com.github.catvod.crawler.Spider`。
2. 在 `init(Context, String extend)` 解析站点 `ext`。
3. 实现内容接口并返回合法 JSON。
4. 在 `destroy()` 释放线程、网络、脚本上下文和流。
5. JAR 地址支持配置相对路径；站点 `jar` 覆盖顶层 `spider`。

## QuickJS

1. `api` 指向 `.js`。
2. 可 `export default` 对象/工厂函数，或导出 CatVod 兼容 `__jsEvalReturn()`。
3. 方法签名见 [Spider 接入协议](spider.md)。
4. 可用 `req/_http/pdfh/pdfa/pdfl/joinUrl/md5X/aesX/rsaX/local/getProxy/js2Proxy`。
5. CatVod 兼容模式初始化对象为 `{ stype: 3, skey, ext }`，代理使用 `js2Proxy(...)`。

## Python/Chaquopy

1. `api` 指向 `.py`，继承 `base.spider.Spider`。
2. `getDependence()` 可返回依赖模块名列表；运行时先下载到缓存目录再初始化。
3. 可用基类工具：`loadSpider/loadModule/fetch/post/html/regStr/cleanText/getProxyUrl/getCache/setCache/delCache`。
4. 只有包含 Chaquopy 运行时的构建变体可用；普通构建遇 Python 源会退回 `SpiderNull`。
5. `ext=[]` 会被规范化为 `{}`，避免部分直播 Python Spider 反序列化失败。

## CatSpider HTTP

`api` 是 `http.../spider/...` 时的内置协议，不需要本地 JAR/脚本；方法映射与请求体见 [Spider 接入协议](spider.md)。

## 配置示例

```json
{
  "spider": "./java-spiders.jar",
  "sites": [
    { "key": "java", "name": "Java 演示", "type": 3, "api": "csp_Demo", "jar": "./java.jar", "ext": {} },
    { "key": "js", "name": "JS 演示", "type": 3, "api": "./spider.js", "ext": {} },
    { "key": "cat", "name": "CatSpider 演示", "type": 3, "api": "https://bundle.example.com/spider/index", "ext": {} }
  ]
}
```

## 错误处理

- 初始化失败会退回 `SpiderNull`，不应影响其它站点。
- JAR/脚本更新后需要清理加载缓存或刷新配置。
- Python 依赖下载失败会初始化失败；不要在 `init` 中无限重试。
- 外部脚本不要依赖未公开的 App 内部类；优先使用运行时公开 API。

## 验证清单

1. 冷启动后站点可用。
2. JAR/脚本路径加载正确。
3. JS/Python 初始化 `ext` 类型正确。
4. 切换站点后 `siteKey`、代理和缓存隔离正确。
5. 清理或重载配置后不再泄漏旧 Spider。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/JarLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java`
- `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java`
- `quickjs/src/main/java/com/fongmi/quickjs/crawler/Spider.java`
- `chaquo/src/main/python/base/spider.py`
- `chaquo/build.gradle`
