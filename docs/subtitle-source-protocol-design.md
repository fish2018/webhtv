# WebHTV 字幕源订阅与 T3 脚本协议设计

## 1. 文档状态

- 状态：设计草案
- 协议名称：WebHTV Subtitle Source Protocol
- 协议版本：1
- 配置根节点：`subtitles`
- 主要实现目标：支持用户通过订阅自主维护字幕源，并以 Python、JavaScript、HTML 等实体文件执行字幕搜索与资源解析
- 兼容方向：T3 本地脚本源为主，T4 远程接口源为辅

## 2. 背景

当前点播配置使用 `sites`，直播配置使用 `lives`。字幕源需要独立配置节点，确定使用 `subtitles`：

```json
{
  "sites": [],
  "lives": [],
  "subtitles": []
}
```

现有字幕模块已经具备统一的 `SubtitleProvider` 抽象，其核心能力分为：

```java
List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception;

SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception;
```

现有 `SubtitleProviderRegistry` 负责字幕提供者的注册、搜索调度、候选结果合并和资源解析。因此新增订阅字幕源时，不应重写自动匹配或手动搜索流程，而应把外部字幕源适配成标准 `SubtitleProvider`。

## 3. 目标

### 3.1 核心目标

1. 新增顶层配置节点 `subtitles`，与 `sites`、`lives` 并列。
2. 支持远程订阅、本地文件和内容 URI 等配置入口。
3. 优先支持 T3 字幕源，由 Python、JavaScript、HTML 实体文件自行完成网络请求、鉴权、页面解析和数据转换。
4. 支持 T4 字幕源，由远程服务直接返回标准字幕协议。
5. T3 与 T4 采用完全一致的搜索结果和资源解析结果协议。
6. 复用现有 `SubtitleProvider`、`SubtitleProviderRegistry`、`SubtitleCandidate` 和 `SubtitleAsset`。
7. 支持字幕源启用状态、优先级、超时、语言、格式及私有参数配置。
8. 支持订阅更新、文件缓存、完整性校验和失败回滚。
9. 保证单个字幕源失败不会阻塞其他字幕源。

### 3.2 非目标

1. 不把字幕源模拟成完整点播站点。
2. 不要求字幕脚本实现首页、分类、详情、播放等点播 Spider 方法。
3. 不在 App 配置中描述每个目标网站的字段映射和页面选择器。
4. 不允许脚本直接访问 App 的高权限内部接口。
5. 第一阶段不迁移或删除现有内置字幕源。
6. 第一阶段不要求支持任意原生二进制插件。

## 4. 总体架构

```text
字幕订阅地址或本地配置文件
  -> SubtitleConfigLoader
  -> 解析 subtitles 列表
  -> 校验、规范化实体文件地址
  -> SubtitleSourceCache 下载并缓存实体文件
  -> SubtitleProviderFactory
       -> type=3: T3SubtitleProvider
           -> PythonSubtitleRuntime
           -> JavaScriptSubtitleRuntime
           -> HtmlSubtitleRuntime
       -> type=4: T4SubtitleProvider
           -> HTTP 标准协议客户端
  -> SubtitleProviderRegistry
  -> 现有自动匹配与手动搜索流程
  -> SubtitleCandidate
  -> resolve
  -> SubtitleAsset
  -> 字幕下载、缓存、解压和播放器加载
```

配置层只说明字幕源是谁、执行类型是什么、实体文件在哪里。目标字幕网站的请求、签名、Cookie、解析和结果转换由实体文件自行处理。

## 5. 配置协议

### 5.1 完整配置示例

```json
{
  "protocol": "webhtv-subtitle",
  "version": 1,
  "name": "我的字幕源订阅",
  "subtitles": [
    {
      "key": "assrt_py",
      "name": "Assrt Python",
      "type": 3,
      "api": "py",
      "ext": "./subtitle/assrt.py",
      "enabled": 1,
      "priority": 100,
      "timeout": 15,
      "queryIndependent": 0,
      "languages": ["zh-Hans", "zh-Hant", "en"],
      "formats": ["srt", "ass", "ssa", "vtt"],
      "sha256": "",
      "params": {
        "token": "${ASSRT_TOKEN}"
      }
    },
    {
      "key": "xunlei_js",
      "name": "迅雷字幕 JavaScript",
      "type": 3,
      "api": "js",
      "ext": "./subtitle/xunlei.js",
      "enabled": 1,
      "priority": 90,
      "timeout": 12,
      "queryIndependent": 1
    },
    {
      "key": "browser_html",
      "name": "网页字幕源",
      "type": 3,
      "api": "html",
      "ext": "./subtitle/provider.html",
      "enabled": 1,
      "priority": 80,
      "timeout": 20
    },
    {
      "key": "private_t4",
      "name": "私有字幕服务",
      "type": 4,
      "api": "https://subtitle.example.com/v1",
      "enabled": 1,
      "priority": 70,
      "timeout": 10,
      "headers": {
        "Authorization": "Bearer ${SUBTITLE_SERVICE_TOKEN}"
      }
    }
  ]
}
```

### 5.2 字段定义

#### 根对象

- `protocol`：可选，协议标识；推荐固定为 `webhtv-subtitle`。
- `version`：必填，协议主版本；第一版为 `1`。
- `name`：可选，订阅显示名称。
- `subtitles`：必填，字幕源对象数组。

#### 字幕源对象

- `key`：必填，订阅内唯一、稳定的字幕源标识。
- `name`：必填，用户界面显示名称。
- `type`：必填；`3` 表示本地执行实体，`4` 表示远程标准接口。
- `api`：必填；T3 为 `py`、`js` 或 `html`，T4 为服务基础地址。
- `ext`：T3 必填，脚本或 HTML 实体文件地址。
- `enabled`：可选，`1` 启用、`0` 禁用，默认启用。
- `priority`：可选，数值越大优先级越高；相同分数保持订阅顺序。
- `timeout`：可选，单次调用超时秒数；应设置全局上限。
- `queryIndependent`：可选，是否只需对一组查询调用一次。
- `languages`：可选，字幕源声明支持的语言标签。
- `formats`：可选，字幕源声明支持的字幕格式。
- `params`：可选，提供给脚本的私有配置。
- `headers`：可选，T4 请求头或 T3 下载实体时需要的请求头。
- `sha256`：可选，T3 实体文件 SHA-256 校验值。
- `minAppVersion`：可选，最低兼容 App 版本。
- `protocolVersion`：可选，单源协议版本；未设置时继承根对象版本。

### 5.3 地址形式

T3 的 `ext` 应支持以下形式：

```text
./subtitle/provider.py
https://example.com/subtitle/provider.js
file:///storage/emulated/0/WebHTV/subtitle/provider.py
content://com.android.externalstorage.documents/document/...
asset://subtitle/provider.html
```

相对地址必须以订阅文件最终地址为基准解析，不以当前页面或进程工作目录为基准。

## 6. T3 与 T4 执行模型

### 6.1 T3

```text
App
  -> 调用已缓存的本地 Python、JavaScript 或 HTML 实体
  -> 实体访问目标字幕网站
  -> 实体完成鉴权、抓取、解析和转换
  -> 返回标准字幕协议 JSON
```

T3 适合复杂网页解析、签名鉴权、多步跳转、Cookie 会话及社区自行维护的爬虫源。

### 6.2 T4

```text
App
  -> 请求远程字幕服务
  -> 远程服务返回标准字幕协议 JSON
```

T4 适合已部署的字幕聚合服务、私有服务或局域网字幕服务器。

### 6.3 统一原则

T3 与 T4 只在执行位置和调用方式上不同。传入的业务请求以及返回的 `search`、`resolve` 数据结构必须一致。

## 7. 脚本生命周期与方法

每个 T3 实体使用以下生命周期：

```text
init(config)
search(request)
resolve(request)
destroy()
```

- `init`：可选；加载源配置、初始化会话或检查运行环境。
- `search`：必填；按统一查询协议返回候选字幕。
- `resolve`：条件必填；把候选项解析成最终可下载资源。
- `destroy`：可选；释放会话、WebView、脚本上下文及临时资源。

返回值统一为 JSON 对象或可解析为 JSON 对象的字符串。异步 JavaScript 或 HTML 实现允许返回 Promise。

## 8. 通用响应封装

所有方法建议使用统一响应结构：

```json
{
  "code": 0,
  "message": "",
  "data": {}
}
```

约定：

- `code = 0` 表示成功。
- 非零 `code` 表示业务失败。
- `message` 提供可记录的错误摘要，不应包含密钥。
- `data` 保存对应方法的结果。
- 运行时异常、超时和崩溃由 App 转换为统一 Provider 错误，不要求脚本捕获所有异常。

## 9. init 协议

### 9.1 请求

```json
{
  "protocol": "webhtv-subtitle",
  "version": 1,
  "source": {
    "key": "assrt_py",
    "name": "Assrt Python",
    "type": 3,
    "api": "py",
    "params": {
      "token": "运行时注入值"
    }
  },
  "environment": {
    "appVersion": "1.0.0",
    "platform": "android",
    "locale": "zh-CN"
  }
}
```

### 9.2 响应

```json
{
  "code": 0,
  "message": "",
  "data": {
    "capabilities": {
      "search": true,
      "resolve": true,
      "queryIndependent": false,
      "languages": ["zh-Hans", "zh-Hant", "en"],
      "formats": ["srt", "ass", "ssa", "vtt"]
    }
  }
}
```

脚本声明能力与配置冲突时，应采用更严格的能力交集。

## 10. search 协议

### 10.1 请求

```json
{
  "action": "search",
  "requestId": "2fc5d947-9103-4c7f-a90b-63a83d60e19c",
  "query": {
    "text": "流浪地球",
    "key": "流浪地球 2019",
    "language": "zh-Hans",
    "source": "title",
    "strictness": "normal",
    "year": 2019,
    "season": -1,
    "episode": -1
  },
  "context": {
    "canonicalTitle": "流浪地球",
    "originalTitle": "The Wandering Earth",
    "year": 2019,
    "season": -1,
    "episode": -1,
    "fileName": "The.Wandering.Earth.2019.1080p.mkv",
    "mediaUrl": "",
    "tmdbId": 535167,
    "imdbId": "",
    "preferredLanguage": "zh-Hans"
  },
  "source": {
    "key": "assrt_py",
    "name": "Assrt Python",
    "params": {
      "token": "运行时注入值"
    }
  }
}
```

### 10.2 响应

```json
{
  "code": 0,
  "message": "",
  "data": {
    "list": [
      {
        "id": "subtitle-1001",
        "name": "流浪地球.2019.简体中文.ass",
        "language": "zh-Hans",
        "format": "ass",
        "url": "",
        "score": 92,
        "year": 2019,
        "season": -1,
        "episode": -1,
        "matchType": "metadata_fuzzy",
        "requiresResolve": true,
        "payload": {
          "fileId": "1001",
          "downloadPage": "https://example.com/subtitle/1001"
        }
      }
    ]
  }
}
```

### 10.3 候选字段

- `id`：必填，在当前字幕源内稳定且唯一。
- `name`：必填，用户可见名称或文件名。
- `language`：可选，推荐使用 BCP 47 标签。
- `format`：可选，推荐 `srt`、`ass`、`ssa`、`vtt`。
- `url`：可选，候选已具有直接下载地址时填写。
- `score`：可选，脚本估算匹配分；App 可以再次排序。
- `year`：可选，影片年份。
- `season`：可选，季度；未知使用 `-1`。
- `episode`：可选，集数；未知使用 `-1`。
- `matchType`：可选，匹配类型。
- `requiresResolve`：必填，是否需要调用 `resolve`。
- `payload`：可选，Provider 私有 JSON，App 只负责原样保存并在 `resolve` 时回传。

### 10.4 映射到现有模型

```text
source.key       -> SubtitleCandidate.provider
id               -> SubtitleCandidate.candidateId
name             -> SubtitleCandidate.displayName
language         -> SubtitleCandidate.language
format           -> SubtitleCandidate.format
url              -> SubtitleCandidate.downloadUrl
score            -> SubtitleCandidate.score
year             -> SubtitleCandidate.year
season           -> SubtitleCandidate.season
episode          -> SubtitleCandidate.episode
matchType        -> SubtitleCandidate.matchType
requiresResolve  -> SubtitleCandidate.requiresResolve
payload          -> SubtitleCandidate.providerPayload
```

## 11. resolve 协议

### 11.1 请求

```json
{
  "action": "resolve",
  "requestId": "4fc64ac6-f767-4eb7-b824-a2babc0f916a",
  "candidate": {
    "id": "subtitle-1001",
    "name": "流浪地球.2019.简体中文.ass",
    "language": "zh-Hans",
    "format": "ass",
    "url": "",
    "payload": {
      "fileId": "1001",
      "downloadPage": "https://example.com/subtitle/1001"
    }
  },
  "context": {
    "canonicalTitle": "流浪地球",
    "originalTitle": "The Wandering Earth",
    "year": 2019,
    "season": -1,
    "episode": -1
  },
  "source": {
    "key": "assrt_py",
    "params": {
      "token": "运行时注入值"
    }
  }
}
```

### 11.2 响应

```json
{
  "code": 0,
  "message": "",
  "data": {
    "url": "https://example.com/download/1001.ass",
    "fileName": "流浪地球.2019.简体中文.ass",
    "language": "zh-Hans",
    "format": "ass",
    "headers": {
      "Referer": "https://example.com/",
      "User-Agent": "Mozilla/5.0"
    },
    "archive": {
      "type": "none",
      "entry": ""
    }
  }
}
```

### 11.3 资源字段

- `url`：与 `content` 至少提供一个；最终下载地址。
- `content`：可选，Base64 编码的小体积字幕正文，不建议用于大文件。
- `fileName`：可选，建议包含扩展名。
- `language`：可选，字幕语言。
- `format`：可选，字幕格式。
- `headers`：可选，下载时附加的 HTTP 请求头。
- `archive.type`：可选，`none`、`zip`、`gzip` 或后续扩展值。
- `archive.entry`：可选，压缩包内优先文件路径或匹配提示。
- `expiresAt`：可选，临时 URL 过期时间戳。

App 必须把响应转换为现有 `SubtitleAsset`，再进入现有字幕下载、缓存和播放器应用流程。

## 12. Python 实体约定

优先复用工程当前 Python Loader 及其 `Spider` 类加载习惯，字幕脚本增加字幕专用方法，不引入第二套 Python 运行环境。

建议形式：

```python
class Spider:

    def init(self, config=""):
        return {
            "code": 0,
            "data": {}
        }

    def search(self, request):
        return {
            "code": 0,
            "message": "",
            "data": {
                "list": []
            }
        }

    def resolve(self, request):
        return {
            "code": 0,
            "message": "",
            "data": {}
        }

    def destroy(self):
        return {
            "code": 0
        }
```

兼容实现可以允许模块级函数，但协议基线以现有 Loader 最容易复用的调用形式为准。

## 13. JavaScript 实体约定

JavaScript Runtime 应提供受控网络桥接，例如现有运行时的 `req` 能力。脚本不应创建绕过 App 安全策略的网络客户端。

```javascript
const subtitle = {
  async init(config) {
    return { code: 0, data: {} };
  },

  async search(request) {
    return {
      code: 0,
      message: "",
      data: { list: [] }
    };
  },

  async resolve(request) {
    return {
      code: 0,
      message: "",
      data: {}
    };
  },

  async destroy() {
    return { code: 0 };
  }
};

export default subtitle;
```

运行时应兼容同步返回值和 Promise，并统一序列化为标准 JSON。

## 14. HTML 实体约定

HTML 类型用于必须依赖 DOM、浏览器 Cookie、LocalStorage 或网页 JavaScript 的字幕源。

App 应在独立、隔离的 WebView 中加载实体文件，并调用：

```javascript
window.WebHTVSubtitle.init(configJson)
window.WebHTVSubtitle.search(requestJson)
window.WebHTVSubtitle.resolve(requestJson)
window.WebHTVSubtitle.destroy()
```

页面返回 JSON 字符串或 Promise：

```html
<script>
window.WebHTVSubtitle = {
  async init(configJson) {
    return JSON.stringify({ code: 0, data: {} });
  },

  async search(requestJson) {
    const request = JSON.parse(requestJson);
    return JSON.stringify({
      code: 0,
      message: "",
      data: { list: [] }
    });
  },

  async resolve(requestJson) {
    const request = JSON.parse(requestJson);
    return JSON.stringify({
      code: 0,
      message: "",
      data: {}
    });
  },

  async destroy() {
    return JSON.stringify({ code: 0 });
  }
};
</script>
```

HTML Runtime 不得复用 WebHome 等具有高权限原生桥接的 WebView。

## 15. App 侧接口设计

### 15.1 字幕脚本运行时

```java
public interface SubtitleScriptRuntime {

    String init(String sourceKey, String scriptPath, String config) throws Exception;

    String search(String sourceKey, String request) throws Exception;

    String resolve(String sourceKey, String request) throws Exception;

    void destroy(String sourceKey);
}
```

### 15.2 通用 T3 Provider

```java
public final class T3SubtitleProvider implements SubtitleProvider {

    private final SubtitleSourceConfig source;
    private final SubtitleScriptRuntime runtime;

    @Override
    public String getName() {
        return source.getKey();
    }

    @Override
    public boolean isEnabled() {
        return source.isEnabled();
    }

    @Override
    public boolean isQueryIndependent() {
        return source.isQueryIndependent();
    }

    @Override
    public List<SubtitleCandidate> search(
            SubtitleQuery query,
            SubtitleContext context) throws Exception {
        String request = SubtitleProtocolMapper.createSearchRequest(source, query, context);
        String response = runtime.search(source.getKey(), request);
        return SubtitleProtocolMapper.parseCandidates(source, response);
    }

    @Override
    public SubtitleAsset resolve(
            SubtitleCandidate candidate,
            SubtitleContext context) throws Exception {
        String request = SubtitleProtocolMapper.createResolveRequest(source, candidate, context);
        String response = runtime.resolve(source.getKey(), request);
        return SubtitleProtocolMapper.parseAsset(source, response);
    }
}
```

### 15.3 Runtime Factory

```java
public final class SubtitleScriptRuntimeFactory {

    public SubtitleScriptRuntime create(String api) {
        switch (api) {
            case "py":
                return pythonRuntime;
            case "js":
                return javaScriptRuntime;
            case "html":
                return htmlRuntime;
            default:
                throw new IllegalArgumentException("Unsupported subtitle runtime: " + api);
        }
    }
}
```

### 15.4 T4 Provider

`T4SubtitleProvider` 使用 HTTP 调用远程服务：

```text
POST {api}/init
POST {api}/search
POST {api}/resolve
POST {api}/destroy
```

服务端返回与 T3 完全相同的标准响应。实现可以允许服务声明实际路径，但第一版宜保持固定路由，减少配置复杂度。

## 16. 配置加载和注册

建议新增独立的 `SubtitleConfig`，不要把字幕源混入 `VodConfig` 或 `LiveConfig`。

加载流程：

1. 读取字幕订阅 JSON。
2. 校验根对象和协议版本。
3. 解析 `subtitles`。
4. 规范化 `key`、`type`、`api`、`ext` 和超时。
5. 忽略或记录不合法源，不因单项错误丢弃全部有效源。
6. T3 实体下载到临时文件。
7. 校验大小、扩展名和 SHA-256。
8. 调用 `init` 做可加载性检查。
9. 成功后原子替换缓存版本。
10. 构造 `T3SubtitleProvider` 或 `T4SubtitleProvider`。
11. 按优先级注册到 `SubtitleProviderRegistry`。
12. 保留现有内置 Provider，并明确其与订阅源的顺序和冲突策略。

## 17. key、去重和冲突策略

- `key` 在单个订阅内必须唯一。
- 多订阅场景下，运行时内部 ID 应使用 `subscriptionId:key` 命名空间。
- 用户界面仍显示 `name`，不显示内部命名空间。
- 同一订阅出现重复 `key` 时，默认保留第一项并记录错误。
- 不同订阅出现相同 `key` 时不得静默覆盖。
- 订阅源与内置源重名时不得替换内置实现，除非未来增加显式覆盖字段。
- 候选去重必须至少包含 Provider 命名空间，不能只按候选 `id` 跨源去重。

## 18. 缓存与更新

建议缓存结构：

```text
subtitle_sources/
  <subscription-id>/
    manifest.json
    active/
      assrt.py
      xunlei.js
      provider.html
    staging/
    metadata.json
```

更新规则：

1. 下载到 `staging`。
2. 限制单文件和订阅总大小。
3. 校验扩展名、MIME 类型和哈希。
4. 执行最小加载测试或 `init`。
5. 所有关键文件成功后原子切换到 `active`。
6. 失败时删除 `staging` 并继续使用旧版。
7. 保留最近一个可用版本用于回滚。
8. 不在搜索时重复下载脚本。
9. 订阅刷新和搜索调用使用独立锁，避免切换过程中执行半更新文件。

## 19. 安全要求

### 19.1 通用要求

- 默认不信任订阅配置、脚本、HTML、远程响应和字幕文件。
- 限制脚本文件大小、响应大小、字幕下载大小和解压后总大小。
- 所有调用必须具有超时和取消能力。
- 日志不得输出 Token、Cookie、Authorization 或完整私有参数。
- 对下载 URL 和重定向继续应用 App 的网络安全策略。
- 禁止路径穿越，压缩包解压必须校验目标路径。
- 禁止脚本指定任意 App 私有文件作为输出位置。

### 19.2 Python 和 JavaScript

- 优先使用工程现有受控运行时。
- 仅开放字幕搜索所需的最小桥接能力。
- 脚本不得获取 Android Context、任意 Java 反射或原生进程执行能力。
- 每个字幕源使用隔离的逻辑上下文，销毁时释放资源。

### 19.3 HTML

- 使用独立 WebView 和独立数据目录或可实现的最强隔离。
- 禁止高权限 JS Bridge。
- 默认关闭文件系统任意访问。
- 限制混合内容、窗口创建、下载和外部 Intent。
- 页面销毁时清理计时器、请求和回调。

### 19.4 敏感参数

配置中的 `${NAME}` 只表示引用本地安全设置，不应在公共订阅中存放真实密钥。App 在调用脚本前按白名单注入，导出配置和日志时继续保留占位符。

## 20. 超时、并发和故障隔离

- 每个字幕源单次 `search` 和 `resolve` 独立超时。
- 全局并发数应受限，避免大量脚本同时占用 CPU、网络或 WebView。
- HTML Runtime 的并发数应单独限制。
- 单个 Provider 超时或异常只产生该源失败结果。
- Registry 应继续等待其他源，直到全局搜索窗口结束。
- 生命周期销毁不得在 UI 主线程阻塞。
- 切换订阅、退出播放或清理配置时必须调用 `destroy`。

## 21. 错误分类

建议内部错误类型：

```text
CONFIG_INVALID
PROTOCOL_UNSUPPORTED
SOURCE_DISABLED
ENTITY_DOWNLOAD_FAILED
ENTITY_HASH_MISMATCH
RUNTIME_UNSUPPORTED
RUNTIME_INIT_FAILED
RUNTIME_TIMEOUT
RUNTIME_CRASHED
RESPONSE_INVALID
SEARCH_FAILED
RESOLVE_FAILED
ASSET_DOWNLOAD_FAILED
ARCHIVE_INVALID
CANCELED
```

用户界面显示简短可理解提示，详细错误仅进入脱敏日志。

## 22. 向后兼容

1. 没有 `subtitles` 节点时，保持当前内置字幕源行为。
2. `subtitles` 为空数组时，不代表强制禁用内置字幕源，除非未来增加明确配置。
3. 未识别字段必须忽略，以支持协议向前扩展。
4. 未支持的主版本应拒绝加载，并继续使用上一可用配置。
5. 新增可选字段不得改变版本 1 已定义字段的语义。
6. 现有内置 Provider 第一阶段继续注册，订阅源作为增量能力加入。

## 23. 实施阶段

### 阶段一：协议模型和解析

- 新增 `SubtitleSourceConfig` 和 `SubtitleSubscription`。
- 解析根节点 `subtitles`。
- 完成字段校验、地址解析和版本检查。
- 建立协议请求与响应 DTO。
- 建立 `SubtitleProtocolMapper`。

### 阶段二：T3 Python 和 JavaScript

- 复用现有 Python、JavaScript Loader。
- 增加字幕专用方法调用。
- 实现 `T3SubtitleProvider`。
- 接入 `SubtitleProviderRegistry`。
- 完成搜索和解析单元测试。

### 阶段三：订阅缓存和管理

- 增加订阅地址管理。
- 增加实体下载、缓存、哈希校验和原子更新。
- 增加启用、禁用、优先级和刷新操作。
- 增加失败回滚和脱敏日志。

### 阶段四：HTML Runtime

- 实现隔离 WebView。
- 实现 JSON Bridge、超时和销毁。
- 建立 HTML 来源的权限边界和测试。

### 阶段五：T4 和内置源迁移评估

- 实现 `T4SubtitleProvider`。
- 验证 T3/T4 协议一致性。
- 评估将部分内置字幕源迁移成随 App 发布的 T3 实体文件。
- 内置迁移必须保持可回滚，不应一次性删除当前 Java Provider。

## 24. 验收标准

### 配置

- 能正确解析同时包含 `sites`、`lives`、`subtitles` 的配置。
- 不含 `subtitles` 时现有功能不退化。
- 非法单项不会导致全部字幕源失效。
- 相对实体地址能以订阅地址为基准正确解析。

### T3

- Python 和 JavaScript 实体可以完成 `init`、`search`、`resolve`、`destroy`。
- HTML 类型在后续阶段可以完成相同协议调用。
- 脚本返回结果能无损映射到 `SubtitleCandidate` 和 `SubtitleAsset`。
- 单源超时、异常或非法 JSON 不影响其他源。

### 缓存

- 成功更新后使用新实体。
- 下载、哈希或初始化失败时继续使用旧实体。
- 搜索过程中不会执行半更新文件。

### 安全

- 日志不泄露密钥和 Cookie。
- 路径穿越和压缩炸弹被拒绝。
- HTML 页面无法访问高权限 App Bridge。
- 运行时资源在销毁或配置切换后得到释放。

### 回归

- 现有自动匹配、手动搜索、候选去重和字幕应用流程保持可用。
- 现有内置 Assrt、Shooter、Xunlei Provider 在第一阶段保持原行为。

## 25. 测试建议

### 单元测试

- 配置字段默认值和非法值。
- 相对 URL、远程 URL、file URI、content URI 和 asset URI 解析。
- `search` 响应映射和未知字段兼容。
- `resolve` 响应映射、请求头和压缩信息。
- 重复 key、跨订阅命名空间和排序。
- 响应缺字段、非法 JSON、过大响应和非零错误码。

### 集成测试

- Python 源搜索和二次解析。
- JavaScript Promise 搜索和二次解析。
- T4 HTTP 搜索和二次解析。
- 多 Provider 并发、单源超时和取消。
- 订阅更新成功、失败回滚和 App 重启恢复。

### 安全测试

- 脚本路径穿越。
- 压缩包路径穿越和异常压缩比。
- 重定向到不允许的协议。
- HTML Bridge 越权调用。
- Token、Cookie 和 Authorization 日志脱敏。

## 26. 最终决策

- 字幕配置根节点确定为 `subtitles`。
- T3 字幕源是主要扩展方式，`type` 固定为 `3`。
- T3 的 `api` 表示运行时类型：`py`、`js`、`html`。
- T3 的 `ext` 指向实体文件，可为相对远程地址、绝对远程地址或受支持的本地 URI。
- T4 字幕源的 `type` 固定为 `4`，`api` 指向远程标准接口。
- T3 和 T4 统一使用 `init`、`search`、`resolve`、`destroy` 语义及相同 JSON 结果协议。
- App 通过通用 `T3SubtitleProvider` 和 `T4SubtitleProvider` 接入当前 `SubtitleProviderRegistry`。
- 第一阶段保留所有现有内置字幕源，新增能力以增量、可回滚方式落地。
