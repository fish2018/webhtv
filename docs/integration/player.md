# 播放结果与播放器接入

Spider `playerContent`、HTTP API 播放和解析器最终都会得到 `Result` 播放结果。

## 输入

| Spider 方法 | 参数 | 说明 |
| --- | --- | --- |
| `playerContent` | `flag` | 详情结果中的线路名 |
| `playerContent` | `id` | 选集播放 ID 或原始地址 |
| `playerContent` | `vipFlags` | 需要解析的 flag 列表 |

## 播放结果字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `parse` | number | `0` | `1` 强制解析 WebView |
| `jx` | number | `0` | `1` 等同需要解析 |
| `url` | string/array/object | `""` | 播放地址，结构见下文 |
| `playUrl` | string | `""` | 播放前缀或 `json:`、`parse:` 指令 |
| `header` | object | `{}` | 播放请求 header；Cookie 会进入 CookieStore |
| `format` | string | `""` | 媒体 MIME/格式 |
| `subs` | array | `[]` | 字幕列表，见 [字幕接入](subtitle.md) |
| `danmaku` | array | `[]` | 弹幕列表，见 [弹幕接入](danmaku.md) |
| `drm` | object/null | `null` | DRM 配置 |
| `position` | number | `0` | 起播位置毫秒 |
| `artwork` | string | `""` | 播放器封面 |
| `flag` | string | `""` | 线路名；为空时用调用传入 flag |
| `click` | string | `""` | WebView 点击脚本 |
| `msg` | string | `""` | Toast；仅 `code` 为空或 `0` 时显示 |
| `code` | number | `0` | 状态码 |
| `jxFrom` | string | `""` | 解析来源 |
| `desc` | string | `""` | 播放描述 |

## URL 结构

```json
{ "url": "https://example.com/video.m3u8" }
```

数组按 `[显示名1, 地址1, 显示名2, 地址2]` 解析：

```json
{ "url": ["标清", "https://example.com/sd.m3u8", "高清", "https://example.com/hd.m3u8"] }
```

对象支持多地址和默认选中位置：

```json
{
  "url": {
    "values": [
      { "n": "高清", "v": "https://example.com/hd.m3u8" },
      { "n": "标清", "v": "https://example.com/sd.m3u8" }
    ],
    "position": 0
  }
}
```

## DRM

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | string | License URL 或 ClearKey JSON |
| `type` | string | `widevine`、`playready`、`clearkey` 分别映射对应 UUID |
| `forceKey` | boolean | 是否强制默认 license URL |
| `header` | object | license 请求 header |

## 直链示例

```json
{
  "parse": 0,
  "jx": 0,
  "url": "https://example.com/video.m3u8",
  "header": { "User-Agent": "Mozilla/5.0", "Referer": "https://example.com/" },
  "format": "application/x-mpegURL"
}
```

## 解析示例

```json
{
  "parse": 1,
  "jx": 0,
  "playUrl": "parse:演示解析",
  "url": "https://page.example.com/watch?id=1",
  "header": { "User-Agent": "Mozilla/5.0" }
}
```

## 接入要求

1. `parse=0`、`jx=0` 且 `url` 是媒体直链时直接播放。
2. `parse=1` 或 `jx=1` 时走解析流程。
3. `header` 覆盖主清单、子清单、分片、密钥和字幕请求。
4. 空 URL、HTML 错误页、过期签名都视为失败。
5. 播放结果必须始终是 JSON；XML 只用于 `type=0` 兼容 API。

## 源码依据

- `catvod/src/main/java/com/github/catvod/crawler/Spider.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Result.java`
- `app/src/main/java/com/fongmi/android/tv/player/ParseJob.java`
- `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Drm.java`
