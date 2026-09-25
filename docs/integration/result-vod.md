# Result 与 Vod 返回协议

Spider 首页、分类、详情、搜索和播放返回的 JSON 统一解析为 `Result`；列表项解析为 `Vod`。

## Result 顶层字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `class` | array | `[]` | 分类列表 |
| `list` | array | `[]` | Vod 列表 |
| `filters` | object | `{}` | key 为分类 ID 的筛选数组 |
| `url` | string/array/object | `""` | 播放地址 |
| `header` | object | `{}` | 播放或请求 header |
| `msg` | string | `""` | Toast；仅 `code=0` 时生效 |
| `code` | number | `0` | 状态码 |
| `danmaku` | array | `[]` | 弹幕源列表 |
| `subs` | array | `[]` | 字幕列表 |
| `playUrl` | string | `""` | 播放前缀或解析指令 |
| `artwork` | string | `""` | 播放器封面 |
| `jxFrom` | string | `""` | 解析来源 |
| `flag` | string | `""` | 当前线路 |
| `desc` | string | `""` | 播放描述 |
| `lrc` | string | `""` | 歌词 |
| `format` | string | `""` | 媒体 MIME |
| `click` | string | `""` | WebView 点击脚本 |
| `key` | string | `""` | 站点 key |
| `position` | number | `0` | 起播位置毫秒 |
| `pagecount` | number | `0` | 总页数 |
| `parse` | number | `0` | 解析 WebView 标记 |
| `jx` | number | `0` | 需解析标记 |
| `drm` | object/null | `null` | DRM 配置 |

## Class / Filter

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type_id` | string | `""` | 分类 ID；别名 `id` |
| `type_name` | string | `""` | 分类名；别名 `name` |
| `type_flag` | string | `""` | `1` 时作为文件夹/子分类入口处理 |
| `filters` | array | `[]` | 内联筛选 |
| `land` | number | `0` | 横图快捷样式 |
| `circle` | number | `0` | 圆形快捷样式 |
| `ratio` | number | `0` | 图片宽高比 |
| `filter.key` | string | `""` | 筛选参数名，放入 `extend` |
| `filter.name` | string | `""` | 显示名 |
| `filter.init` | string | `""` | 初始值 |
| `filter.value[].n` | string | `""` | 显示名 |
| `filter.value[].v` | string | `""` | 提交值 |

## Vod 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vod_id` | string | `""` | 详情 ID |
| `vod_name` | string | `""` | 标题；HTML 会被清理 |
| `type_name` | string | `""` | 类型 |
| `vod_pic` | string | `""` | 海报 |
| `vod_remarks` | string | `""` | 备注/更新信息 |
| `vod_year` | string | `""` | 年份 |
| `vod_area` | string | `""` | 地区 |
| `vod_director` | string | `""` | 导演 |
| `vod_actor` | string | `""` | 演员 |
| `vod_content` | string | `""` | 简介 |
| `vod_play_from` | string | `""` | 线路名，多个用 `$$$` |
| `vod_play_url` | string | `""` | 播放列表 |
| `vod_tag` | string | `""` | `folder` 作为文件夹入口 |
| `action` | string | `""` | 点击触发 `action()`，不进普通详情 |
| `tmdb` | object | `null` | TMDB 匹配 payload |
| `cate` | object | `null` | 子分类入口 |
| `style` | object | `null` | 单条目样式 |
| `land` | number | `0` | 横图快捷样式 |
| `circle` | number | `0` | 圆形快捷样式 |
| `ratio` | number | `0` | 图片宽高比 |

`vod_play_url` 格式：线路之间用 `$$$`；单线路内选集用 `#`；单集为 `集名$播放地址`。`vod_play_from` 与 `vod_play_url` 线路数量必须一致。

## Style

```json
{ "type": "rect", "ratio": 1.33 }
```

| 字段 | 取值 | 默认值 |
| --- | --- | --- |
| `type` | `rect`、`oval`、`list` | `rect` |
| `ratio` | number，最大 4 | rect `0.75`，oval `1.0` |
| `land` | `0`/`1` | `0`；`1` 等价 `rect + ratio=1.33` |
| `circle` | `0`/`1` | `0`；`1` 等价 `oval + ratio=1.0` |

## 示例

```json
{
  "class": [{ "type_id": "movie", "type_name": "电影" }],
  "list": [
    {
      "vod_id": "1",
      "vod_name": "示例影片",
      "vod_pic": "https://example.com/poster.jpg",
      "vod_remarks": "全 2 集",
      "vod_play_from": "主线$$$备用线",
      "vod_play_url": "第1集$id-1#第2集$id-2$$$第1集$backup-1"
    }
  ],
  "page": 1,
  "pagecount": 1,
  "total": 1
}
```

## 错误处理

- 列表为空返回 `{ "list": [], "page": 1, "pagecount": 0, "total": 0 }`。
- `msg` 不能替代分类/列表字段；错误页不是合法结果。
- `pagecount` 与实际分页一致，避免无限翻页。
- 详情线路名不要为空；播放 ID 在同一线路内唯一。

## 源码依据

- `app/src/main/java/com/fongmi/android/tv/bean/Result.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Vod.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Class.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Filter.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Style.java`
