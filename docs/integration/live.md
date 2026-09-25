# 直播源接入

直播源支持 TXT、M3U、JSON 和 Spider 返回。直播源级配置会作为频道默认值；频道级配置优先。

## 来源识别

| 来源 | 识别方式 | 说明 |
| --- | --- | --- |
| M3U | 文本包含 `#EXTM3U` 且非 `#genre#` TXT | 解析 `#EXTINF`、`group-title`、`tvg-*`、`catchup-*`、`#EXTVLCOPT`、`#KODIPROP` |
| TXT | 分组行含 `#genre#`，频道行 `频道名,播放地址` | 同频道多地址用 `#`；单地址可 `url|header参数` |
| JSON | JSON array | 按 `groups[].channel[]` 解析 |
| Spider | `api` 非空 | 调用 Spider `liveContent(url)` 返回文本或 JSON |

## Live 根字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `""` | 直播源唯一名称 |
| `url` | string | `""` | 列表 URL；`api` 为空时直接请求 |
| `type` | number | `0` | 常规直播列表写 `0` |
| `playerType` | number | `0` | 保留的播放器类型元数据；站点注入默认写 `2`，当前播放内核选择不读取此字段 |
| `api` | string | `""` | 直播 Spider 入口 |
| `ext` | string/object | `""` | 直播 Spider 扩展参数 |
| `jar` | string | `""` | 直播专用 JAR，为空时继承顶层 `spider` |
| `click` | string | `""` | WebView 点击脚本 |
| `logo` | string | `""` | logo 模板，支持 `{id}`、`{name}`、`{logo}` |
| `epg` | string | `""` | EPG 地址；多个地址用英文逗号分隔 |
| `ua` | string | `""` | User-Agent，合并进频道 header |
| `origin` | string | `""` | Origin，合并进频道 header |
| `referer` | string | `""` | Referer，合并进频道 header |
| `timeZone` | string | 系统时区 | EPG 时区 |
| `keep` | string | `""` | App 保存的最近频道，作者一般不写 |
| `timeout` | number | `15` | 播放超时秒数；最小按 `1` 处理 |
| `header` | object | `{}` | 直播源级 header |
| `catchup` | object | `null` | 回看规则，见下文 |
| `core` | object | `null` | TVBus 特殊内核配置 |
| `groups` | array | `[]` | JSON 直播分组 |
| `boot` | boolean | `false` | App 状态字段，作者一般不写 |
| `pass` | boolean | `false` | `true` 时不拆分分组名中的密码 |

## Group / Channel

| 对象 | 字段 | 说明 |
| --- | --- | --- |
| `group` | `name` | 分组名；含 `_密码` 时默认拆出 `pass` |
| `group` | `pass` | 非空为隐藏分组 |
| `group` | `channel` | 频道数组 |
| `channel` | `name` | 频道名 |
| `channel` | `urls` | 播放地址数组；每项可为 `url` 或 `url$线路名` |
| `channel` | `number` | 频道号；为空按顺序生成三位编号 |
| `channel` | `logo` | 频道 logo |
| `channel` | `epg` | EPG 名；直播源 `epg` 模板支持 `{epg}` |
| `channel` | `ua` / `origin` / `referer` | 频道级请求头 |
| `channel` | `click` | WebView 点击脚本 |
| `channel` | `format` | MIME；`dash`/`mpd` 转 `application/dash+xml`，`hls` 转 `application/x-mpegURL` |
| `channel` | `tvgId` / `tvgName` | EPG 匹配字段；缺省回退频道名 |
| `channel` | `catchup` | 频道级回看，优先于直播源 |
| `channel` | `header` | 频道级 header |
| `channel` | `parse` | `1` 强制解析 WebView |
| `channel` | `drm` | DRM 配置 |

## Catchup

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | string | `default` 直接使用 `source`；其它值按追加模式 |
| `days` | string | 保留字段；当前回看 URL 生成不读取 |
| `regex` | string | 匹配频道 URL；为空时 `source` 非空即认为支持回看 |
| `source` | string | 模板，支持 `${(b)格式}`、`${(e)格式}`、`${utc:}`、`${utcend:}` |
| `replace` | string | 追加模式替换规则 `正则,替换值` |

## DRM

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | string | License URL 或 ClearKey JSON |
| `type` | string | 含 `widevine`、`playready`、`clearkey` 时映射对应 UUID；其它值映射空 UUID |
| `forceKey` | boolean | 是否强制使用默认 license URL |
| `header` | object | license 请求 header |

## 示例

```json
{
  "name": "演示直播",
  "url": "./live.m3u",
  "logo": "https://example.com/logo/{name}.png",
  "epg": "https://example.com/epg.xml.gz",
  "ua": "Mozilla/5.0",
  "referer": "https://example.com/",
  "timeZone": "Asia/Shanghai",
  "timeout": 15,
  "catchup": {
    "type": "append",
    "regex": "/PLTV/",
    "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}",
    "replace": "/PLTV/,/TVOD/"
  }
}
```

## 验证清单

1. 分组和频道能加载，隐藏分组需要输入密码。
2. 播放地址按频道/直播源 header 正常起播。
3. EPG 名称、tvg-id 和时区匹配。
4. 支持回看的频道可生成回看 URL。
5. DRM 与线路切换不破坏频道列表。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Live.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Group.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Channel.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Catchup.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Epg.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Drm.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/LiveConfig.java`
