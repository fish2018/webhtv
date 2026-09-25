# 字幕接入

字幕来自 Spider 播放结果、媒体内嵌轨道或局域网注入。Spider 应把字幕放进播放结果 `subs`。

## 播放结果中的字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` | string | `""` | 字幕 URL 或路径 |
| `name` | string | `""` | 字幕名称 |
| `lang` | string | `""` | 字幕语言 |
| `format` | string | `""` | 字幕 MIME；部分本地注入链路会按扩展名推断 |
| `flag` | number | 默认字幕 | Media3 subtitle selection flag |

## 示例

```json
{
  "url": "https://example.com/video.m3u8",
  "subs": [
    { "name": "简体中文", "lang": "zh-CN", "url": "https://example.com/zh-CN.srt" },
    { "name": "ASS", "format": "text/x-ssa", "url": "https://example.com/movie.ass" }
  ]
}
```

## 接入要求

1. URL 必须返回实际字幕正文，不能是登录页或反爬页。
2. UTF-8 编码最稳；扩展名、MIME 和实际格式一致。
3. 字幕需要鉴权时，返回 `header` 或让 URL 通过本地代理补 header。
4. 字幕加载失败不应导致视频播放失败。

## 局域网注入

```text
GET/POST /action?do=refresh&type=subtitle&path=字幕URL
```

本地文件注入支持 `.srt`、`.ssa`、`.ass`。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Sub.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Result.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Action.java`
- `docs/SUB-EXT-HISTORY-external-subtitle-restore.md`
