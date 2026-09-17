# AI 推荐利用历史富集元数据设计

> **状态：✅ 已实现并验证**
> **日期：2026-07-19**
> **关联文档：** `docs/ai-viewing-report-design.md`

## 1. 背景

`History` 已通过数据库版本 36→37 增加以下富集字段：

- `typeName`：类型/题材
- `area`：地区
- `actor`：演员/人员
- `director`：导演/主创
- `year`：年份

当前 `AiRecommendationService` 对正在查看的 `Vod` 已传递题材、地区、导演、演员、年份等信息，但历史记录只传递标题、集数、观看时长、完成率和最近观看时间。结果是长期偏好中最有区分度的题材、地区、年代与主创信息没有进入 AI 上下文。

另外，现有 AI 推荐缓存指纹只基于历史标题。老历史在重新播放后补齐演员、导演等元数据时，缓存无法识别偏好信号已经增强。

## 2. 目标与非目标

### 2.1 目标

1. AI 推荐使用历史记录中所有现有富集元数据，不新增数据库字段。
2. 保留观看完成率、观看时长和新近度，避免把“点开过”误判成强兴趣。
3. 明确告诉模型如何组合即时兴趣与长期偏好，提高推荐理由和候选作品的相关性。
4. 富集元数据变化后使 AI 推荐缓存失效，但播放进度的频繁变化不应造成缓存抖动。
5. 继续遵守最多 24 部去重历史的上下文限制，避免 prompt 无界增长。

### 2.2 非目标

- 不新增本地推荐算法或第三方推荐依赖。
- 不修改 `History` 表结构或数据库版本。
- 不上传播放线路、站点名、播放地址等与内容偏好无关的信息。
- 不因单个演员/导演的一次出现就强制形成长期偏好。

## 3. 数据映射

每条 `playHistory` 在现有播放信号之外增加以下字段：

| History 字段 | Prompt 字段 | 处理方式 |
|---|---|---|
| `vodName` | `title` | 非空才输出 |
| `year` | `year` | 保留原字符串，兼容区间或未知格式 |
| `typeName` | `mediaType` | 复用 `inferMediaType` 推断 `movie` / `tv` |
| `typeName` | `genres` | 复用统一分隔逻辑输出字符串数组 |
| `area` | `country` | 非空才输出 |
| `area` | `language` | 复用 `inferLanguage` 推断主要语言 |
| `director` | `director` | 非空才输出 |
| `actor` | `actors` | 复用统一分隔逻辑输出字符串数组 |
| `vodRemarks` | `episodeName` / `episodeNumber` | 保持现状 |
| `position` / `duration` | `watchedMinutes` / `durationMinutes` / `completionRate` | 保持现状 |
| `createTime` | `lastWatchedAt` | 保持现状 |

示例：

```json
{
  "title": "示例作品",
  "year": "2025",
  "mediaType": "tv",
  "country": "中国大陆",
  "language": "中文",
  "genres": ["剧情", "悬疑"],
  "director": "示例导演",
  "actors": ["演员甲", "演员乙"],
  "episodeName": "第08集",
  "episodeNumber": 8,
  "watchedMinutes": 42,
  "durationMinutes": 45,
  "completionRate": 0.93,
  "lastWatchedAt": "2026-07-18 20:30"
}
```

## 4. 推荐权重规则

Prompt 增加以下约束：

1. `currentItem` 表示即时兴趣，`playHistory` 表示长期偏好，两者冲突时应综合判断，不能只围绕当前作品推荐。
2. 同一题材、演员、导演、地区或年代在多条高完成率历史中重复出现时，视为更强偏好。
3. 高完成率、较长观看时长、较新记录权重更高；低完成率或仅短暂播放的记录权重更低。
4. 单条历史中的某个人员或导演只能作为弱信号，避免过拟合。
5. 空字段表示本地未知，不应被当作负面偏好。
6. 继续排除当前作品和已观看作品。

本次不额外生成聚合画像对象。模型直接读取最多 24 条结构化历史，避免在客户端维护第二套偏好统计口径；字段说明负责约束模型完成聚合判断。

### 4.1 为什么不传全部历史

24 条不是推荐算法的理论最优值，而是当前面向请求成本、响应时间和信号质量设置的安全上限：

- 用户历史可能增长到数百或数千条；全部传输会使 prompt、费用和响应时间无界增长。
- 较老记录的兴趣代表性通常弱于近期记录，过多旧数据会稀释当前偏好。
- 结构化历史包含标题和人员偏好，限制传输范围也符合数据最小化原则。
- 最近 24 部去重作品通常已经能覆盖多个题材、地区和人员重复信号。

选择顺序固定为：按最近观看时间排序、排除 `currentItem`、按标准化标题去重，再截取 24 条。Prompt 与缓存指纹必须复用同一选择结果。若后续需要利用更长期历史，应优先在本地对更老记录生成题材/人员计数摘要，而不是把全部原始历史无界传给模型。

## 5. 缓存与刷新

### 5.1 AI 历史指纹

新增面向 AI 推荐的元数据指纹，包含最近去重历史的：

- 标题
- 题材
- 地区
- 演员
- 导演
- 年份

指纹不包含播放位置、时长和更新时间，原因是这些字段在播放期间高频变化，会导致重复请求。新作品加入、标题变化或富集元数据变化时，指纹必须变化。

同时提升 AI 推荐总指纹版本，确保升级后的首次读取不会复用旧版缺少富集信息的缓存。历史指纹与 Prompt 共用同一历史选择器，先排除当前作品再应用 24 条上限，避免 Prompt 已变化但指纹未变化。

### 5.2 历史变更通知

`History.save()` 的推荐相关变更判断应包含上述稳定元数据。只改变播放进度时仍不发送推荐刷新事件；首次补齐题材、演员、导演、地区或年份时发送一次刷新事件。

## 6. 兼容性与隐私

- 老历史字段为空时，按现有行为省略字段，AI 仍可使用标题和播放信号。
- 不需要数据库迁移。
- 保持历史标题去重和 24 条上限。
- 不传站点、线路、URL、设备标识等信息。
- 自定义推荐 prompt 仍会附加统一字段说明和硬性约束。

## 7. 测试方案

1. `historyContextItem` 能输出 `year/mediaType/country/language/genres/director/actors`。
2. 空元数据不输出空字符串或空数组。
3. Prompt 明确说明人员、导演、题材、地区、年代及完成率/新近度的组合权重。
4. AI 历史指纹在演员、导演、题材、地区、年份变化时改变。
5. AI 历史指纹在仅播放进度变化时保持不变。
6. 历史推荐信号判断在稳定元数据变化时返回 true，在仅播放进度变化时返回 false。
7. 当前作品位于历史首位且历史超过 24 条时，Prompt 与历史指纹仍选择同一批记录。
8. 剧集备注可补充推断 `mediaType=tv`，但 `1080P` 等清晰度文本不会被误判；历史指纹只跟随推断结果变化，不跟随集号变化。
9. 英文演员姓名中的空格得到保留。
10. 现有 `AiRecommendationServiceTest` 全量通过。

## 8. 实施结果（2026-07-19）

- `AiRecommendationService.historyContextItem()` 已输出全部历史富集元数据。
- 推荐 Prompt 已加入即时兴趣、长期偏好、人员/导演重复度、完成率和新近度权重规则。
- AI 历史指纹已改为元数据敏感，并提升总指纹版本以淘汰旧缓存；Prompt 与指纹共用排除当前作品后的最近 24 条历史。
- `History.save()` 仅在标题、身份或稳定富集元数据变化时发送推荐刷新事件，播放进度变化不触发。
- 剧集备注可补充媒体类型推断，演员数组会保留英文姓名内部空格。
- Mobile arm64 全量单元测试通过；Leanback arm64 相关单元测试通过。

## 9. 影响文件

- `app/src/main/java/com/fongmi/android/tv/service/AiRecommendationService.java`
- `app/src/main/java/com/fongmi/android/tv/service/PersonalRecommendationService.java`
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`
- `app/src/test/java/com/fongmi/android/tv/service/AiRecommendationServiceTest.java`
- `app/src/test/java/com/fongmi/android/tv/bean/HistoryPlaybackTest.java`
- `docs/ai-viewing-report-design.md`
