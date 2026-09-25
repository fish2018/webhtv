# 弹幕接入

弹幕可作为播放结果 `danmaku` 数组返回，也可由局域网接口注入。当前模型记录弹幕源，具体渲染由 Media3 扩展处理。

## 播放结果中的字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` | string | `""` | 弹幕文件或接口 URL |
| `name` | string | 回退 `url` | 弹幕源显示名 |
| `source` | string | `""` | 来源名；别名 `from`、`site`、`provider`、`platform` |
| `apiSourceName` | string | `""` | API 来源名；别名 `apiSource`、`api_source`、`api_source_name` |

## 示例

```json
{
  "url": "https://example.com/video.m3u8",
  "danmaku": [
    { "name": "演示弹幕", "url": "https://example.com/danmaku.xml", "source": "demo" }
  ]
}
```

## 局域网注入

```text
GET/POST /action?do=refresh&type=danmaku&path=弹幕URL
```

## 接入要求

1. 弹幕 URL 应返回目标影片/集数的弹幕数据。
2. 无法可靠匹配时应返回空数据，不应混入其它内容。
3. 弹幕接口异常不应导致视频播放失败。
4. 弹幕格式和时间单位按目标渲染器协议提供，不要自行混用秒和毫秒。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Danmaku.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Result.java`
- `app/src/main/java/com/fongmi/android/tv/server/process/Action.java`
- `docs/danmaku-manual-match-memory-design.md`
