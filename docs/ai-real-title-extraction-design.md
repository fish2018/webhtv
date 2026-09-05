# AI 真实剧名提取设计

> **状态：✅ 已完成（2026-07-14 核实）**
> - `MediaTitleResolver` + `MediaTitleParser` + `MediaTitleCache` + `MediaTitleRequest` / `MediaTitleResolution` / `MediaTitleCandidate` 已实现。
> - 已接入三大调用方：
>   - `TmdbUIAdapter.autoMatch()` (line 232)：TMDB 自动匹配。
>   - `SubtitleContextBuilder.buildWithAiFallback()` (line 48)：字幕自动匹配。
>   - `DanmakuApi.search()` (line 116)：弹幕搜索。
> - `Setting.isAiTitleExtraction()` 开关已实现。
> - 含单元测试 `MediaTitleResolverTest` / `MediaTitleResolverSourceTest`。
> - **剩余增强（未做）**：`MediaTitleLearningStore`（学习样本持久化）、`DanmakuMatchCache`、弹幕手动选择沉淀、术语表。

## 背景

项目里已经有多处功能依赖“剧名”做自动匹配：

- TMDB 增强：`TmdbUIAdapter.autoMatch()` 调用 `TmdbMatcher.searchAndMatch(videoName, vod)`，命中后会把 TMDB 标题回写到 `Vod.name`。
- 弹幕：`VideoActivity` 播放成功后用 `mHistory.getVodName()` + 当前集名调用 `DanmakuApi.search()`；`TmdbDetailActivity` 内联播放用 `playbackHistoryName()` + 集名调用弹幕搜索。
- 字幕：`SubtitleContextBuilder` / `SubtitleTmdbResolver` 通过 `SubtitleTitleParser`、TMDB 缓存和请求上下文生成字幕查询词。

现有 `TmdbMatcher.cleanVideoName()` 和 `SubtitleTitleParser.cleanTitle()` 能处理清晰度、年份、季集、语言版本、合集等常规噪声，但网盘资源名经常不是标准剧名，例如：

- `庆余年2 S02E05 4K 高码 国语中字 更新至18集`
- `qyn 第二季 防和谐版`
- `青余年 第18集`
- `长相思2.2024.2160p.WEB-DL`
- `凡人修仙传 年番 EP115`

这类标题靠规则很难稳定还原真实剧名，导致 TMDB、弹幕、字幕自动匹配失败。目标是利用已有 AI 配置能力，新增一个“真实剧名解析层”，在不破坏现有规则兜底的前提下提高自动匹配成功率。

## 目标

1. 从资源标题、集名、备注等上下文中提取可用于检索的真实剧名。
2. 识别并保留年份、媒体类型、季号、集号、集标题、别名等结构化信息。
3. 为 TMDB、弹幕、字幕提供同一套候选查询词，减少各自重复清洗逻辑。
4. 使用缓存和置信度控制 AI 成本、延迟和误匹配风险。
5. 保持“展示名”和“检索名”分离，避免把 AI 猜测结果直接污染用户看到的资源名。

## 非目标

- 不用 AI 直接替代 TMDB 结果，也不让 AI 生成 TMDB ID。
- 不强制所有播放源都走 AI；规则解析、现有 TMDB 缓存和手动重匹配仍然有效。
- 不在第一版做大型模型微调或离线字典训练。
- 不把 API key、播放 URL、完整网盘链接等敏感信息发送给 AI。

## 总体方案

新增一个共享标题解析模块，建议包名：

```text
app/src/main/java/com/fongmi/android/tv/title/
  MediaTitleResolver.java
  MediaTitleRequest.java
  MediaTitleResolution.java
  MediaTitleCandidate.java
  MediaTitleLearningExample.java
  MediaTitleParser.java
  MediaTitleCache.java
  MediaTitleLearningStore.java

app/src/main/java/com/fongmi/android/tv/service/
  AiCompletionClient.java
  AiTitleExtractionService.java

app/src/main/java/com/fongmi/android/tv/bean/
  DanmakuMatchCache.java
```

解析流程：

```text
原始资源上下文
  -> 规则解析 MediaTitleParser
  -> 读取手动/TMDB/弹幕/AI 缓存与学习样本
  -> 判断是否需要 AI
  -> AI 提取真实剧名
  -> 本地校验与置信度合并
  -> 输出 MediaTitleResolution
  -> TMDB / 弹幕 / 字幕按候选词查询
```

核心原则：规则解析永远先跑，AI 只补规则不擅长的部分，例如谐音、拼音缩写、防和谐、过度包装标题。

## 数据模型

### MediaTitleRequest

```java
public final class MediaTitleRequest {
    public String siteKey;
    public String vodId;
    public String rawTitle;
    public String rawRemarks;
    public String vodYear;
    public String episodeName;
    public String flag;
    public String source; // TMDB_AUTO, DANMAKU_AUTO, SUBTITLE_AUTO, MANUAL_SEARCH
    public List<MediaTitleLearningExample> learningExamples;
    public boolean allowAi;
}
```

### MediaTitleResolution

```java
public final class MediaTitleResolution {
    public String rawTitle;
    public String ruleTitle;
    public String canonicalTitle;
    public String originalTitle;
    public String mediaType; // movie, tv, unknown
    public int year;
    public int seasonNumber;
    public int episodeNumber;
    public String episodeTitle;
    public List<String> aliases;
    public List<MediaTitleCandidate> candidates;
    public float confidence;
    public String source; // TMDB_CACHE, MANUAL, RULE, AI, RULE_AI_MERGED
    public boolean needsVerification;
}
```

### MediaTitleCandidate

候选词用于下游按顺序尝试：

```java
public final class MediaTitleCandidate {
    public String title;
    public String source; // tmdb, ai, rule, alias, raw
    public float confidence;
}
```

### MediaTitleLearningExample

学习样本来自用户手动修正和已验证命中，用来告诉 AI“类似输入应该被理解成什么”：

```java
public final class MediaTitleLearningExample {
    public String rawTitle;
    public String ruleTitle;
    public String expectedTitle;
    public String mediaType;
    public int year;
    public int seasonNumber;
    public String source; // TMDB_MANUAL, DANMAKU_MANUAL, SUBTITLE_MANUAL
    public int hitCount;
    public long updatedAt;
}
```

学习样本只参与提示词上下文和本地确定性命中，不作为最终事实源。最终仍由 TMDB 命中、用户手动选择、弹幕/字幕实际可用结果来确认。

候选词排序建议：

1. 手动修正标题。
2. 已命中的 TMDB 标题。
3. 高置信度 AI `canonicalTitle`。
4. 规则清洗标题。
5. AI aliases。
6. 原始标题兜底。

## 规则解析层

`MediaTitleParser` 应复用并逐步收敛现有两套逻辑：

- `TmdbMatcher.cleanVideoName()`
- `SubtitleTitleParser.cleanTitle()` / `seasonNumber()` / `episodeNumber()` / `aliases()`

第一阶段可以先新建共享解析器，再让 TMDB 和字幕逐步委托它，避免一次性大改。规则层负责：

- 去掉扩展名、清晰度、编码、HDR、平台标记。
- 去掉季集、更新状态、全集/合集/完结。
- 提取年份、季号、集号。
- 生成基础 alias。
- 对明显无需 AI 的标题直接给高置信度结果。

建议规则置信度：

- `0.95`：已存在 TMDB 缓存或手动选择。
- `0.80`：规则清洗后标题短、干净，且无拼音/谐音迹象。
- `0.55`：标题包含大量噪声但能清洗出候选。
- `0.30`：标题疑似拼音缩写、谐音、防和谐、资源包名。

低于 `0.75` 且 AI 已启用时进入 AI 提取。

## AI 提取服务

`AiTitleExtractionService` 复用现有 `AiConfig` 的协议、端点、API key、模型、User-Agent 和响应解析思路。通用 completion 能力由 `AiCompletionClient` 提供：

- `requestSpec(AiConfig, prompt)`
- `extractCompletionText(body, config)`
- `extractJson(text)`
- OpenAI Responses / Chat / Anthropic Messages / Gemini Native 的兼容处理。

不要把剧名提取塞进 `AiRecommendationService`，避免推荐任务和解析任务互相耦合。`AiRecommendationService` 只负责推荐 prompt、推荐结果解析和推荐缓存；`AiTitleExtractionService` 只负责真实剧名 prompt 和标题结果校验，两者共同依赖 `AiCompletionClient`。

### Prompt

内置任务提示词建议版本化，例如 `TITLE_EXTRACT_PROMPT_VERSION = 1`：

```text
你是影视资源标题解析器。你的任务是从网盘/资源站标题中提取真实影视作品名。

要求：
- 只返回严格 JSON，不要 Markdown，不要解释。
- 不要保留清晰度、编码、字幕、语言版本、更新状态、集数、合集、资源组、平台名。
- 如果标题是拼音缩写、谐音、防和谐写法，请尽量还原为中文正式片名。
- 如果无法确定，canonicalTitle 使用规则清洗后的最可能标题，confidence 降低。
- 不要编造 TMDB ID。

返回格式：
{
  "canonicalTitle": "剧名",
  "originalTitle": "",
  "mediaType": "tv|movie|unknown",
  "year": 0,
  "seasonNumber": -1,
  "episodeNumber": -1,
  "episodeTitle": "",
  "aliases": ["别名1"],
  "confidence": 0.0,
  "reasonCode": "clean|homophone|pinyin|uncertain"
}

输入 JSON:
{...}
```

输入只包含必要字段：

```json
{
  "rawTitle": "qyn 第二季 4K 更新至18集",
  "ruleTitle": "qyn",
  "rawRemarks": "更新至18集",
  "episodeName": "第18集",
  "year": 0,
  "seasonNumber": 2,
  "episodeNumber": 18
}
```

当存在高相关学习样本时，追加最多 3-5 条脱敏示例：

```json
{
  "learningExamples": [
    {
      "rawTitlePattern": "qyn 第二季",
      "ruleTitle": "qyn",
      "expectedTitle": "庆余年",
      "mediaType": "tv",
      "seasonNumber": 2,
      "source": "TMDB_MANUAL"
    }
  ]
}
```

提示词需要明确：学习样本是用户在本设备上的历史修正，优先级高于模型常识；但如果当前输入和样本明显不相似，不要强行套用。

### AI 输出校验

AI 结果必须经过本地校验，失败则丢弃：

- `canonicalTitle` 为空或长度异常，丢弃。
- 仍包含 `1080P/4K/WEB-DL/H.265/更新至/全集/第N集` 等噪声，降权或丢弃。
- `confidence` 不在 `0..1`，归一化。
- `year` 不在 `1900..2099`，置 0。
- `seasonNumber`、`episodeNumber` 异常，置 -1。
- aliases 去重，并过滤与噪声相同的内容。

如果 AI 标题和规则标题差异很大，标记 `needsVerification=true`，下游可以用于“尝试检索”，但不要直接覆盖展示名。

## 缓存策略

新增 `MediaTitleCache`，缓存目录建议：

```text
Path.cache()/ai_title/
```

缓存类型：

- 手动修正：按 `siteKey + vodId` 保存，长期有效，最高优先级。
- TMDB 命中标题：复用 `TmdbMatchCache`，无需重复存储。
- 弹幕命中映射：建议新增 `DanmakuMatchCache`，记录 `siteKey + vodId + episode` 到用户选中的弹幕标题、弹幕 URL、来源和搜索词。
- AI 解析：按原始标题和 AI 配置指纹保存。
- 学习样本：由手动 TMDB 重匹配、手动弹幕选择、字幕手动搜索成功等事件沉淀，供后续相似标题解析参考。

AI 缓存 key：

```text
v1|siteKey|vodId|rawTitle|episodeName|protocol|endpoint|model|promptVersion
```

保存前做 MD5，文件内容保存 `MediaTitleResolution` JSON。建议 AI 缓存有效期 30 天；手动修正和 TMDB 缓存不过期。缓存命中时不再请求 AI。

## 学习与进化闭环

用户手动纠正是最可靠的监督信号。设计上应把“用户选对了什么”沉淀成两类能力：

1. 本地确定性映射：同一 `siteKey + vodId` 或同一集再次播放时，直接使用已确认的 TMDB/弹幕/字幕映射，不需要 AI。
2. 相似标题学习样本：当遇到新的相似标题时，把少量历史修正作为 few-shot 示例传给 AI，帮助它理解本用户常见缩写、谐音和资源命名习惯。

### 样本来源

- TMDB 手动重匹配：`TmdbDetailActivity.showManualTmdbMatchDialog()`、mobile/leanback `VideoActivity.showManualTmdbMatchDialog()` 最终写入 `TmdbMatchCache` 的条目。
- TMDB 自动命中后用户未改动：可作为弱正样本，权重低于手动修正。
- 弹幕手动选择：`DanmakuSearchDialog` / `DanmakuSearchInputDialog` 中用户点选的 `Danmaku`，建议写入新增 `DanmakuMatchCache`。
- 字幕手动搜索或手动选择：如果后续字幕选择有可持久化入口，也写入同一学习存储。

### 学习样本索引

`MediaTitleLearningStore` 建议维护两种索引：

- 精确索引：`siteKey + vodId`，用于同资源直接命中。
- 相似索引：`normalizedRuleTitle`、拼音首字母、年份、季号、资源标题 token，用于找 few-shot 示例。

每条样本保存：

- 原始标题摘要或脱敏标题模式。
- 规则清洗标题。
- 用户确认的真实剧名。
- TMDB `mediaType/year/seasonNumber` 或弹幕结果的 title/source。
- `source`、`hitCount`、`updatedAt`、`manual=true/false`。

### AI 使用方式

AI 请求中只携带与当前标题最相似的 3-5 条样本，排序规则：

1. 同站点、同资源或同规则标题的手动修正。
2. 同拼音缩写/谐音模式的手动修正。
3. 同季号/年份的近似标题。
4. 自动命中但重复成功次数较高的弱样本。

示例不应包含弹幕 URL、播放 URL、站点 Cookie、Header；弹幕样本只传“用户用关键词 X 最终选中了标题 Y”，不传下载地址。

### 反馈写入时机

- 用户手动选择 TMDB 条目后，立即写入 `TmdbMatchCache` 和 `MediaTitleLearningStore`。
- 用户在弹幕搜索结果里手动选择某项后，写入 `DanmakuMatchCache` 和 `MediaTitleLearningStore`。
- 自动匹配成功并被用户持续播放一段时间未纠正，可增加弱样本 `hitCount`，但不覆盖手动样本。
- 用户重新修正同一资源时，新样本覆盖旧样本，并把旧样本标记为 superseded，避免继续误导 AI。

### 防止学坏

- 手动样本权重大于自动样本。
- 低置信 AI 结果不写入学习样本，除非后来被用户手动确认。
- 同一 rawTitle 多次被用户改成不同标题时，标记冲突，后续只做手动候选，不传给 AI。
- 学习样本有数量上限，例如最近 500 条；按 `manual`、`hitCount`、`updatedAt` 淘汰。

## 接入点

### TMDB 自动匹配

现状：

```java
mTmdbUIAdapter.autoMatch(item.getName(), item);
```

建议：

1. `TmdbUIAdapter.autoMatch()` 内先调用 `MediaTitleResolver.resolveCachedOrRule()`。
2. 如果规则置信度不足且 AI 开启，异步调用 AI。
3. 对 `MediaTitleResolution.candidates` 逐个调用 `tmdbMatcher.searchAndMatch(candidate.title, vod)`。
4. 命中 TMDB 后继续使用现有 `saveMatch()` 和 `loadDetailSync()`。
5. 只有 TMDB 命中后才允许通过 `applyTmdbTitle()` 回写 `Vod.name`。
6. 用户手动重匹配成功后，同步写入 `TmdbMatchCache` 和 `MediaTitleLearningStore`。

这样可以保持 TMDB 作为最终权威来源，AI 只负责给更好的搜索词。

### 弹幕自动搜索

现状：

```java
DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), ...)
```

建议新增查询规划，按候选词顺序异步尝试，首个命中后停止后续请求：

```java
List<String> names = titleResolution.queryTitles();
DanmakuSearchPlanner.searchFirst(names, episodeName, found -> applyDanmaku(found));
```

第一版可以只改调用方，不改 `DanmakuApi.newCall()`。优先尝试：

1. TMDB 标题或 AI 高置信标题。
2. 规则清洗标题。
3. 原始历史标题。

如果弹幕接口成本较高，可限制最多 2 个自动查询词；手动弹幕搜索界面可以展示更多候选。

用户手动选择弹幕后，建议保存：

- 当前资源身份：`siteKey`、`vodId`、季集信息。
- 当次搜索词：AI/规则候选或用户手输关键词。
- 用户选择的弹幕 `name`、`sourceName`、`url` 的本地缓存值。
- 可学习摘要：`rawTitle -> DanmakuTitle.titleKey(item)`。

下次同资源同集优先使用 `DanmakuMatchCache`，相似标题才把摘要样本交给 AI。

### 字幕自动匹配

`SubtitleContextBuilder` 当前会使用 `SubtitleTmdbResolver` 和 `SubtitleTitleParser.aliases()`。建议：

1. 在构建 `SubtitleRequest` 时带上 `MediaTitleResolution`，或让 `SubtitleContextBuilder` 内部按 `siteKey/vodId/rawTitle` 读取缓存。
2. `canonicalTitle` 优先使用 TMDB 标题，其次高置信 AI 标题，再其次规则标题。
3. 把 AI aliases 加入 `SubtitleContext.aliases`，让 `SubtitleQueryPlanner` 自然生成更多查询词。
4. 字幕手动选择成功后，如有持久化入口，也写入 `MediaTitleLearningStore`。

这样不会破坏现有字幕 provider/ranker。

### 播放 metadata

`VideoActivity.buildMetadata()` 目前使用 `mHistory.getVodName()` 作为播放器标题，弹幕手动搜索也读取 `player.getMetadata().title`。建议保持展示标题不变，同时增加检索标题的独立来源：

- 展示：仍用用户当前播放的历史名或 TMDB 命中名。
- 检索：使用 `MediaTitleResolution.canonicalTitle`。

如果需要传到播放器，可放到 `MediaMetadata.extras` 或由调用处直接持有，不建议把 AI 猜测结果强制写入 `mHistory.vodName`。

## 设置项

建议新增一个独立开关：

- `AI 真实剧名识别`：默认关闭或跟随“智能推荐”总开关但可单独关闭。
- `仅在自动匹配失败后使用 AI`：默认开启。
- `允许发送资源标题给 AI`：首次启用时提示隐私说明。

底层仍复用 `AiConfig` 的端点、协议、模型和 API key，避免用户重复配置。

## 隐私与安全

发送给 AI 的字段只包含：

- 原始标题短文本。
- 规则清洗标题。
- 备注短文本。
- 集名、年份、季号、集号。
- 少量脱敏学习样本，例如 `qyn -> 庆余年`。

不发送：

- API key。
- 播放 URL。
- 网盘分享链接。
- Cookie、Header、站点完整配置。
- 弹幕 URL、字幕下载 URL。

日志中避免输出完整原始标题和 AI 响应，建议只记录短摘要、hash、耗时、来源和置信度。

## 失败策略

- AI 不可用：完全回退现有规则和 TMDB/弹幕/字幕逻辑。
- AI 超时：使用规则结果，不阻塞播放。
- AI 低置信：只作为候选查询，不覆盖标题。
- AI 高置信但 TMDB 无结果：弹幕/字幕可尝试，TMDB 不缓存。
- 用户手动重匹配：写入手动缓存，后续优先使用。
- 学习样本冲突：停止自动套用，只在手动候选里展示。

推荐阈值：

- `>= 0.85`：可作为首选自动查询词。
- `0.65 - 0.85`：可作为第二候选查询词。
- `< 0.65`：仅在手动搜索界面展示，不自动请求多个下游接口。

## 实施步骤

1. 新增 `MediaTitleParser` 和 `MediaTitleResolution`，用现有规则覆盖基础场景。
2. 给 `MediaTitleParser` 增加单元测试，覆盖清晰度、季集、年份、合集、更新状态。
3. 抽取 AI completion 公共能力，新增 `AiTitleExtractionService`。
4. 新增 `MediaTitleCache`，先支持 AI 缓存和手动修正缓存。
5. 新增 `MediaTitleLearningStore`，从 TMDB 手动重匹配写入学习样本。
6. 新增 `DanmakuMatchCache`，从弹幕手动选择写入确定性映射和学习样本。
7. 在 `TmdbUIAdapter.autoMatch()` 中接入候选标题，但保持现有 `TmdbMatcher` 逻辑不变。
8. 在 mobile/leanback `VideoActivity` 的弹幕自动搜索处接入标题候选。
9. 在 `SubtitleContextBuilder` 中加入标题解析 alias。
10. 增加设置项、学习样本开关和调试日志。
11. 小范围默认关闭发布，验证命中率和误匹配率后再考虑默认开启。

## 测试计划

### 单元测试样例

```text
庆余年2 S02E05 4K 高码 国语中字 更新至18集 -> 庆余年, season=2, episode=5
qyn 第二季 防和谐版 -> 庆余年, season=2
青余年 第18集 -> 庆余年, episode=18
长相思2.2024.2160p.WEB-DL -> 长相思, year=2024, season=2
凡人修仙传 年番 EP115 -> 凡人修仙传, episode=115
边水往事.2024.EP10.2160p -> 边水往事, year=2024, episode=10
The Last of Us S02E03 1080p -> The Last of Us, season=2, episode=3
```

### 集成测试

- mock AI 响应，验证 JSON 解析、缓存命中、低置信丢弃。
- stub `TmdbService.search()`，验证 `TmdbUIAdapter` 会按候选词重试。
- 弹幕自动搜索 mock：首个候选无结果、第二个候选命中时停止。
- 字幕 `SubtitleQueryPlanner`：AI alias 出现在 fallback 查询里且不重复。
- TMDB 手动重匹配后，`MediaTitleLearningStore` 能生成 `rawTitle -> expectedTitle` 样本。
- 弹幕手动选择后，`DanmakuMatchCache` 能在同资源同集下次播放时直接命中。
- 学习样本冲突时，不再进入 AI few-shot 示例。

### 人工验收

- 网盘标题里包含清晰度/集数/更新状态时，TMDB 命中率提升。
- 谐音/拼音缩写标题能进入正确手动候选或自动命中。
- AI 关闭、接口失败、无网络时行为与当前版本一致。
- 播放器展示标题不会被低置信 AI 结果污染。
- 手动修正一次后，相似资源名再次出现时优先使用学习样本生成正确候选。

## 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| AI 幻觉导致误匹配 | TMDB 命中才写入 TMDB 缓存；低置信只做候选 |
| 请求变慢影响播放 | 先用缓存/规则，AI 异步；超时回退 |
| 成本增加 | 只在低置信或首轮失败后调用；按标题缓存 |
| 隐私担忧 | 独立开关；不发送 URL、Cookie、Header |
| 学习样本污染 | 手动样本优先；冲突样本隔离；自动样本只弱计数 |
| 多处调用重复 | 用 `MediaTitleResolver` 输出统一候选 |
| 现有清洗逻辑继续分叉 | 新规则先兼容，再逐步让 TMDB/字幕委托共享 parser |

## 推荐结论

采用“规则优先、用户修正学习、AI 补强、TMDB 验证、缓存兜底”的方案。AI 不作为最终事实源，而是作为真实剧名候选生成器。用户手动修正会先变成本地确定性映射，再作为少量脱敏示例帮助 AI 理解相似标题。这样能最大化利用现有 AI 配置和用户反馈，又能把误匹配风险控制在 TMDB、弹幕、字幕各自现有的校验链路之前。
