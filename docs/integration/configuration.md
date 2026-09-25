# 总配置接入

WebHTV 配置的主要入口是 JSON。点播配置由 `VodConfig` 加载；若配置中包含 `lives`，也会同步生成直播配置。相对路径以配置文件 URL 为基准解析。

## 顶层字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `spider` | string | `""` | 全局 JAR Spider 地址。站点或直播源未单独指定 `jar` 时使用 |
| `sites` | array | `[]` | 点播站点列表，见 [点播源接入](vod-site.md) |
| `parses` | array | `[]` | 点播解析器列表，见 [解析器接入](parser.md) |
| `lives` | array | `[]` | 直播配置列表，见 [直播源接入](live.md) |
| `doh` | array | `[]` | DNS over HTTPS 配置，见下方 `doh` 表 |
| `proxy` | array | `[]` | HTTP/HTTPS/SOCKS 代理规则，见下方 `proxy` 表 |
| `hosts` | array | `[]` | host 覆盖规则，字符串数组，格式 `匹配规则=目标host或IP` |
| `headers` | array | `[]` | 按 host 注入请求 header，见下方 `headers` 表 |
| `rules` | array | `[]` | 嗅探规则，见下方 `rules` 表 |
| `hlsRules` | array | `[]` | HLS 广告清理规则 |
| `groupRules` | array | `[]` | 直播分组规则 |
| `ads` | array | `[]` | 广告域名或正则字符串 |
| `flags` | array | `[]` | 播放 flag 字符串数组 |
| `wallpaper` | string | `""` | 壁纸 URL 或路径 |
| `logo` | string | `""` | 配置图标 |
| `notice` | string | `""` | 配置公告 |
| `home` | string | `""` | 默认站点 `key` |
| `parse` | string | `""` | 默认解析器 `name` |
| `urls` | array | `[]` | 配置仓库/多配置入口 |
| `msg` | string | `""` | 错误消息；非空时加载会直接失败 |

## 子对象字段

### `doh[]`

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `""` | DoH 显示名 |
| `url` | string | `""` | DoH endpoint，例如 `https://dns.google/dns-query` |
| `ips` | string[] | `[]` | bootstrap DNS IP；为空时用系统 DNS 解析 DoH host |

### `proxy[]`

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `""` | 代理规则名 |
| `hosts` | string[] | `[]` | host 匹配规则；支持 `contains`、Java 正则和 `*` |
| `urls` | string[] | `[]` | 代理地址；支持 `http://`、`socks://`、`socks5://`，账号密码写 `scheme://user:pass@host:port` |

### `headers[]`

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `host` | string | `""` | host 匹配规则 |
| `header` | object | `null` | 命中后注入的 header，覆盖同名值 |

### `rules[]`

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `""` | 规则名 |
| `hosts` | string[] | `[]` | 适用 host；匹配 URL host 和 `url` 查询参数里的 host |
| `regex` | string[] | `[]` | 视频 URL 识别规则 |
| `exclude` | string[] | `[]` | 排除规则 |
| `script` | string[] | `[]` | WebView 页面加载后执行的 JS |

## 本地配置记录字段

Room/本地配置记录也使用 `Config` 模型：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `int` | `0` | Room 自增主键，不是远程配置必须提供的字段 |
| `type` | `int` | `0` | 配置类型 |
| `time` | `long` | `0` | 更新时间 |
| `url` | `string` | `""` | 配置 URL |
| `json` | `string` | `""` | 内联配置 JSON |
| `name` | `string` | `""` | 显示名 |
| `logo` | `string` | `""` | 图标 |
| `home` | `string` | `""` | 首页站点 key 或直播源 name |
| `parse` | `string` | `""` | 默认解析器名 |
| `notice` | `string` | `""` | 公告 |
| `danmaku` | `string` | `""` | 弹幕配置入口，见 [弹幕接入](danmaku.md) |

## 加载流程

1. 请求或读取配置文本。
2. `VodConfig` 解析顶层 `sites`、`parses`、`lives` 和网络规则。
3. `Site.objectFrom` / `Live.objectFrom` / `Parse.objectFrom` 转换为对应模型。
4. 剔除无效项并合并用户自定义站点注入。
5. 按 `home` / `parse` 恢复首页源和解析器；找不到时回退第一个有效项。

## 完整示例

```json
{
  "spider": "./spider.jar",
  "wallpaper": "./wallpaper.jpg",
  "logo": "./logo.png",
  "notice": "测试配置",
  "home": "demo",
  "parse": "演示解析",
  "sites": [
    { "key": "demo", "name": "演示点播", "type": 3, "api": "csp_Demo", "jar": "./demo.jar", "ext": {} }
  ],
  "parses": [
    { "name": "演示解析", "type": 1, "url": "https://example.com/parse?url=", "ext": { "flag": ["demo"] } }
  ],
  "lives": [
    { "name": "演示直播", "url": "./live.m3u", "epg": "https://example.com/epg.xml" }
  ],
  "doh": [
    { "name": "Google", "url": "https://dns.google/dns-query", "ips": ["8.8.8.8"] }
  ],
  "proxy": [
    { "name": "app", "hosts": ["example.com"], "urls": ["socks5://127.0.0.1:7897"] }
  ],
  "headers": [
    { "host": "example.com", "header": { "User-Agent": "Mozilla/5.0" } }
  ],
  "rules": [
    { "name": "demo", "hosts": ["example.com"], "regex": ["\\.m3u8"], "exclude": ["ad"], "script": [] }
  ],
  "ads": ["ad.example.com"],
  "flags": ["需要解析"]
}
```

## 校验清单

- 所有数组字段都应为数组，字符串字段都应为字符串或数字。
- 相对路径必须与配置文件位于同一服务器目录，或改写为绝对 URL。
- `home` 必须精确匹配 `sites[].key`；`parse` 必须精确匹配 `parses[].name`。
- `proxy.hosts` 不要写 `a=b`；DNS 覆盖应写在顶层 `hosts`。
- 含 `msg` 的配置会被视为错误响应。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java`
- `app/src/main/java/com/fongmi/android/tv/api/config/LiveConfig.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`
- `catvod/src/main/java/com/github/catvod/bean/Doh.java`
- `catvod/src/main/java/com/github/catvod/bean/Proxy.java`
- `catvod/src/main/java/com/github/catvod/bean/Header.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Rule.java`
