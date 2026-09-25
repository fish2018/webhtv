# 本地代理与服务端接口

App 内置 NanoHTTPD 服务。端口从 `9978` 到 `9998` 依次尝试，实际端口以 `Proxy.getPort()` 为准。

```text
http://127.0.0.1:{port}
http://{局域网IP}:{port}
```

## Spider 代理入口

`/proxy` 会调用当前最近使用的 Spider：

- Java/JAR：`proxy(Map<String, String> params)`
- QuickJS：普通模式 `proxy(params)` 或 CatVod 兼容模式 `proxy(segments, headers)`
- Python：`localProxy(param)`

Java/JAR 返回：

```java
new Object[]{200, "video/mp2t", inputStream, headers}
```

| 下标 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `0` | number 或 `NanoHTTPD.Response` | 是 | HTTP 状态码；`Response` 会原样返回 |
| `1` | string | 是 | Content-Type |
| `2` | InputStream | 是 | 响应流 |
| `3` | Map<String, String> | 否 | 响应 header |

JS/Python 返回：

```js
[200, "text/plain; charset=utf-8", "content", { "Cache-Control": "no-store" }, 0]
```

| 下标 | 类型 | 说明 |
| --- | --- | --- | --- |
| `0` | number | HTTP 状态码 |
| `1` | string | Content-Type |
| `2` | string/bytes | 响应内容；字符串默认 UTF-8 |
| `3` | object | 响应 header，可为空 |
| `4` | number | `1` 表示第 2 项是 Base64；内容含 `base64,` 前缀时自动截掉 |

## 常用端点

| 路径 | 方法 | 能力 |
| --- | --- | --- |
| `/device` | GET/POST | 设备信息 |
| `/media` | GET/POST | 当前播放状态 |
| `/action` | GET/POST | 搜索、推送、刷新、播放控制、注入字幕/弹幕、设置配置 |
| `/cache` | GET/POST | 字符串缓存 |
| `/file/{path}` | GET | 本地文件 |
| `/parse` | GET/POST | 内置解析 HTML |
| `/proxy` | GET/POST | Spider 本地代理 |
| `/webResource` | 多方法 | WebHome 资源网关 |
| `/pan/check` | POST/OPTIONS | 网盘检测 |
| `/spider` | GET/POST | Spider HTTP 桥 |
| `/debug/logs` | GET | 调试日志网页 |
| `/debug/stream` | GET | 调试日志增量轮询 |
| `/m` | GET | 增强管理页面 |

## `/device`

```json
{
  "uuid": "android-id",
  "name": "device-name",
  "ip": "http://192.168.1.23:9978",
  "type": 1,
  "time": 1710000000000
}
```

`type`：`0` 电视端、`1` 手机端、`2` DLNA 设备记录。

## `/media`

```json
{
  "state": 3,
  "speed": 1.0,
  "duration": 3600000,
  "position": 600000,
  "url": "https://example.com/video.m3u8",
  "title": "标题",
  "artist": "",
  "artwork": ""
}
```

`state`：`1` 其它、`2` ready、`3` playing、`6` buffering。

## `/action`

返回固定 `200 OK`，大部分动作异步执行。常用：

```text
GET/POST /action?do=search&word=关键词
GET/POST /action?do=push&url=播放地址
GET/POST /action?do=setting&text=配置内容或配置URL&name=显示名称
GET/POST /action?do=refresh&type=home
GET/POST /action?do=refresh&type=subtitle&path=字幕URL
GET/POST /action?do=refresh&type=danmaku&path=弹幕URL
GET/POST /action?do=control&type=play
```

`refresh.type`：`home`、`live`、`detail`、`player`、`category`、`subtitle`、`danmaku`、`vod`。
`control.type`：`play`、`pause`、`stop`、`prev`、`next`、`repeat`、`replay`。

## 安全要求

1. `/proxy` 只应处理当前站点或明确授权目标，不要实现开放代理。
2. 校验参数、URL、来源和 header，不泄露 Cookie、Token、密钥或内网地址。
3. 请求结束后关闭输入流、响应体、临时文件和注册的播放会话。
4. 非法参数返回 4xx；上游失败返回明确 5xx；不要用 200 返回 HTML 错误页。
5. Range 请求要正确处理，HLS 重写必须保留 playlist、分片和密钥的会话一致性。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/server/Server.java`
- `app/src/main/java/com/fongmi/android/tv/server/Nano.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Proxy.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/SpiderApi.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Action.java`
- `catvod/src/main/java/com/github/catvod/crawler/Spider.java`
