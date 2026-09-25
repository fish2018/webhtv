# 解析器接入

解析器位于顶层 `parses`。播放结果 `parse=1` 或 `jx=1` 时会按线路 flag 选择解析器。

## 字段表

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `""` | 解析器名称，UI 和 `parse:{name}` 前缀匹配的键 |
| `type` | number | `0` | 解析类型，取值见下表 |
| `url` | string | `""` | 解析地址或 JAR parser key |
| `ext` | object | `{}` | 解析扩展，结构见下文 |
| `ext.flag` | string[] | `[]` | 适用播放 flag；空数组表示不过滤 |
| `ext.header` | object | `{}` | 解析请求 header |
| `click` | string | `""` | WebView 点击脚本 |

解析器请求 header 只从 `ext.header` 读取；顶层 `header` 字段不会反序列化为解析器配置。

## 解析类型

| `type` | 名称 | 行为 |
| --- | --- | --- |
| `0` | Web 解析 | 打开解析 WebView 嗅探真实播放地址 |
| `1` | JSON 解析 | 请求 `url + webUrl`，读取返回 JSON 的 `url` 或 `data.url` |
| `2` | JAR Json 扩展 | 调用 JAR 中 `com.github.catvod.parser.Json{url}.parse(jxs, webUrl)` |
| `3` | JAR Mix 扩展 | 调用 JAR 中 `com.github.catvod.parser.Mix{url}.parse(jxs, parseName, flag, webUrl)` |
| `4` | 聚合解析 | 并发尝试符合 flag 的 JSON 和 Web 解析器 |

当 `url` 已含 `?` 且 `ext` 非空时，App 追加 `cat_ext={base64(ext)}`。

## playUrl 前缀

| 前缀 | 行为 |
| --- | --- |
| `json:{url}` | 临时使用 `type=1` 解析器，地址为 `{url}` |
| `parse:{name}` | 使用配置里 `name` 等于 `{name}` 的解析器 |
| 无前缀且非空 | 作为 `type=0` Web 解析地址 |

## 示例

```json
{
  "parses": [
    {
      "name": "演示解析",
      "type": 1,
      "url": "https://example.com/parse?url=",
      "ext": {
        "flag": ["qq", "iqiyi"],
        "header": { "User-Agent": "Mozilla/5.0" }
      }
    }
  ]
}
```

## 错误处理

- JSON 解析返回的 `url` 长度大于 40 才视为成功。
- 解析失败不中断播放流程，会进入解析错误处理。
- Web 解析页面不支持时，不要让 Spider 返回可解析 HTML 伪装成媒体地址。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Parse.java`
- `app/src/main/java/com/fongmi/android/tv/player/ParseJob.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java`
