# C21：TMDB `zh-CN` 配置下国产剧偶现英文的完整修复设计

- 日期：2026-09-24
- 状态：实施完成，Leanback JVM 针对性验证通过（2026-09-25）
- 任务类型：common / TMDB 元数据语言一致性
- 目标版本分支：`dev2`
- 风险等级：中高（跨详情、分季、单集、缓存、播放页和历史；不修改播放器内核）

## 1. 背景

用户配置 TMDB 语言为 `zh-CN` 后，某些国产剧在详情页或播放页偶现英文标题、简介或单集名。用户确认这些剧集本身有中文信息，因此这不是“TMDB 完全没有中文数据”的正常降级，而是语言选择、缓存或数据合并链路未把已有中文数据选出来。

本文档用于指挥后续全部代码改动、测试和验收，不包含本次设计任务中的代码修改。

## 2. 当前证据与结论

### 2.1 已确认正常的部分

- `TmdbConfig.sanitize()` 把空语言兜底为 `zh-CN`。
- 保存配置时会读取语言输入框并序列化到 `tmdb_config`。
- `TmdbService` 的 detail、season、episode、search 请求都携带 `language`。
- detail、season、episode、search 的主要磁盘缓存键已经包含语言。
- `TmdbServiceCacheKeyTest` 已覆盖“缓存键不能串语言”。

### 2.2 已确认的缺陷路径

1. **标题/简介只看顶层字段**
   - detail 标题直接读取 `title` 或 `name`。
   - `translatedOverview()` 优先顶层 `overview`，后续才查 `translations`。
   - 只要 TMDB 或源内嵌数据顶层是英文，即使 `translations` 中有中文，也不会被选中。

2. **英文兜底过早**
   - `translatedOverview()` 在找不到中文时最终回退英文。
   - 该行为没有区分“确实无中文”和“当前数据形状没解析出中文”。
   - 对已有中文的国产剧，这是错误结果。

3. **season/episode 虽请求 `translations`，但单集标题解析仍直接使用 `name`**
   - `TmdbService.episodes()` 只读取 `episodes[].name`。
   - season/episode translations 中已有中文 `name` 时没有参与选择。

4. **源内嵌 TMDB 数据未校验语言**
   - C16 `Vod.tmdb.language` 只是普通字段。
   - 源 payload 顶层为英文时，fill-only 合并仍把英文 core 当作强优先。
   - 用户配置 `zh-CN` 不会触发中文网络数据覆盖。

5. **内存快照和持久匹配缓存不绑定展示语言**
   - `TmdbDetailCache.Entry` 只校验 `tmdbId + mediaType`。
   - `TmdbMatchCache.Entry` 保存英文历史标题/简介，但没有语言身份。
   - 播放页快照命中后直接显示传入标题/简介，后续异步刷新前或失败时可能停留在英文。

6. **历史与标题学习可能固化英文**
   - 英文标题一旦写入 `History.vodName`、标题学习缓存或匹配缓存，后续入口可能在重新请求中文数据前读回英文。

## 3. 调研依据

| 证据 | 位置 | 结论 |
| --- | --- | --- |
| TMDB Languages 文档 | https://developer.themoviedb.org/docs/languages.md，访问日期 2026-09-24 | TMDB 尽量本地化，但并非所有字段都有目标语言翻译；`language` 不保证所有字段中文。 |
| TV season translations API | https://developer.themoviedb.org/reference/tv-season-translations.md，访问日期 2026-09-24 | season translations 提供逐语言 `data.name` 和 `data.overview`。 |
| TV episode translations API | https://developer.themoviedb.org/reference/tv-episode-translations.md，访问日期 2026-09-24 | episode translations 提供逐语言 `data.name` 和 `data.overview`。 |
| NuvioTV PR 2863 | https://github.com/NuvioMedia/NuvioTV/pull/2863，访问日期 2026-09-24 | 成熟项目同样遇到“目标语言缺集名时拿英文占位/缺失”的问题，采用“本地化优先、按字段英文兜底”的策略。 |
| WebHTV 当前代码 | `TmdbService`、`TmdbSourceAdapter`、`TmdbSourceMerger`、`TmdbUIAdapter`、`TmdbDetailActivity`、Leanback `VideoActivity` | 顶层字段优先、源字段强优先和快照无语言是本仓库缺陷。 |

### 3.1 方案比较

- **不改动**：实现成本最低，但已有中文的国产剧仍会偶现英文，无法满足目标。
- **照搬上游 Nuvio 双请求合并**：能覆盖集名，但每次额外拉英文季数据，成本高；且没有解决标题、简介、源 payload 和缓存残留。
- **本仓库窄改方案**：优先复用现有 `translations` 和已有网络请求，不增加常规请求数；仅当目标语言数据缺失且用户允许英文兜底时才使用英文；同时让缓存和快照具备语言身份。风险更小、成本更低。
- **强制禁止英文显示**：若目标语言缺失则显示空值，会导致冷门内容体验回归，不采用。

## 4. 目标行为

配置为 `zh-CN` 时：

1. 详情主标题和简介优先选择中文。
2. 分季标题和简介优先选择中文。
3. 单集标题和简介优先选择中文。
4. 播放页标题、简介、单集标题与详情页一致，不能因快照命中而显示英文。
5. 源内嵌英文 TMDB 数据不得压过可获得的中文 TMDB 数据。
6. 历史或持久缓存中的英文展示值不得阻断重新选择中文。
7. TMDB 确实没有目标语言时，允许有限降级：
   - 先同语言根（如其他中文地区变体）；
   - 再原语言；
   - 最后英文；
   - 降级必须发生在同一个语言策略入口，不允许分散判断。

配置为其他语言（例如 `en-US`）时，同一策略必须正确返回英文，不能写死中文。

## 5. 非目标

1. 不修改 TMDB API Key、路由、鉴权和网络重试策略。
2. 不修改播放器内核、字幕、弹幕和音轨选择。
3. 不批量重写历史数据库记录。
4. 不要求所有 TMDB 内容必须有中文；无中文内容允许降级。
5. 不引入新的服务端接口或新的持久数据库表。
6. 不放宽 C16 源安全边界，源仍不能控制 API 地址、凭据或请求头。

## 6. 总体设计

新增一个纯语言策略层，所有标题/简介/集名的最终选择必须经过它：

```text
原始数据
  ├─ TmdbLanguagePolicy 判断当前配置语言
  ├─ 从顶层字段和 translations 中收集候选
  ├─ 按目标语言优先级排序
  └─ 返回语言感知后的展示值
```

核心原则：

1. **语言身份属于展示值**：标题、简介、集名都必须知道自己的语言来源。
2. **请求语言和展示语言统一**：请求参数和缓存键使用同一个 normalized language。
3. **缓存命中不能降低语言质量**：若缓存语言与当前目标不匹配，不得作为展示优先值。
4. **源数据可用但语言不匹配**：身份、图片、演员等技术数据可继续复用；标题/简介/集名要允许网络中文数据覆盖。
5. **无中文时不中断页面**：按明确顺序降级，不显示空页。

## 7. 详细改动

### 7.1 新增 `TmdbLanguagePolicy`

新增文件：

```text
app/src/main/java/com/fongmi/android/tv/utils/TmdbLanguagePolicy.java
```

职责：

1. 规范化语言：
   - `zh-CN`、`zh-Hans`、`zh-SG` => Simplified Chinese；
   - `zh-TW`、`zh-Hant`、`zh-HK` => Traditional Chinese；
   - 保留 en-US/en-GB 等根语言和地区。
2. 判断字段文本是否属于目标语言：
   - 至少覆盖 CJK 判断；
   - 不能只判断 `contains("中")`。
3. 从 `translations` 选择字段：
   - 输入 `JsonArray translations`、字段名 `name/title/overview`、目标语言；
   - 返回候选值和语言来源，不直接返回英文。
4. 提供统一降级顺序：
   - 请求语言地区匹配；
   - 请求语言根匹配；
   - 中文其他地区变体（仅当目标语言为中文）；
   - 原语言；
   - 英文。
5. 提供纯函数，不依赖 Android Context、网络、数据库和设置存储，便于 JVM 单测。

建议公开 API：

```java
public static String requestLanguage(TmdbConfig config)
public static boolean isChinese(String language)
public static boolean matchesTarget(String target, String candidateLanguage)
public static TranslatedValue bestValue(String primary, JsonArray translations,
                                        String valueKey, String targetLanguage)
```

`TranslatedValue` 建议为 record：

```java
public record TranslatedValue(String value, String language, boolean exact) {}
```

### 7.2 改造 `TmdbService` 标题与简介选择

文件：

```text
app/src/main/java/com/fongmi/android/tv/service/TmdbService.java
```

#### 7.2.1 请求语言

所有 `.addQueryParameter("language", config.getLanguage())` 改为：

```java
.addQueryParameter("language", TmdbLanguagePolicy.requestLanguage(config))
```

所有 `cacheLanguage(config)` 内部也使用 `requestLanguage(config)`，避免空值、空白和大小写差异。

#### 7.2.2 详情标题

新增方法：

```java
public String preferredTitle(TmdbItem item, JsonObject detail, TmdbConfig config)
```

逻辑：

1. 对电影读取顶层 `title`，对剧集读取顶层 `name`。
2. 读取 `translations.translations`。
3. 中文配置下，如果顶层是英文而 translations 有中文 `title` 或 `name`，返回中文。
4. 目标语言缺失时按统一降级顺序返回。
5. 完全无 translations 时保留顶层值。

替换调用点：

- `TmdbUIAdapter.detailTitle()`；
- `TmdbDetailActivity.tmdbDetailTitle()`；
- `TmdbDetailActivity.matchedTmdbTitle()`；
- 其他直接以 `detail.title/name` 归一化标题的地方。

#### 7.2.3 详情简介

重构：

```java
public String translatedOverview(JsonObject detail, TmdbConfig config)
```

要求：

1. 顶层 `overview` 不能只因非空就直接返回；必须先经过语言策略。
2. 顶层值可标记为“请求返回值”，其语言优先级按请求语言处理。
3. translations 中同语言值优先于顶层英文。
4. 保留现有防御性 JSON 解析和异常日志。
5. 不在 UI 层复制降级逻辑。

#### 7.2.4 season 标题与简介

新增方法：

```java
public String seasonName(JsonObject season, TmdbConfig config)
public String seasonOverview(JsonObject season, TmdbConfig config)
```

当前 UI 对 season 名称的直接读取需替换为这两个入口。

#### 7.2.5 episode 标题与简介

修改：

```java
public List<TmdbEpisode> episodes(JsonObject season, TmdbConfig config,
                                  int tmdbId, int seasonNumber)
```

要求：

1. 每个 episode 顶层 `name/overview` 先收集。
2. 从 episode 对象内 `translations.translations` 选择语言最佳值。
3. 若 episode 对象没有 translations，允许使用 season 级 translations 的同 episode 对应项作为补充。
4. `TmdbEpisode` 保存最终语言感知后的标题和简介。
5. 不再出现“season/episode 明明有中文 translations，但 UI 仍显示顶层英文 name”的情况。

#### 7.2.6 单集详情弹窗

`TmdbDetailActivity` 中 episode detail 的标题和简介读取改为调用语言策略，不再直接 `string(detail, "name")` 和 `string(detail, "overview")`。

### 7.3 缓存修复

#### 7.3.1 磁盘缓存键

文件：

```text
app/src/main/java/com/fongmi/android/tv/service/TmdbService.java
```

要求：

1. `cacheLanguage()` 必须使用 `TmdbLanguagePolicy.requestLanguage(config)`。
2. detail、season、episode、search 缓存键继续包含语言。
3. `sourceDetailCacheKey()` 继续包含 season、language 和 capability mask。
4. 修改后运行既有 `TmdbServiceCacheKeyTest`，并新增大小写/别名用例。

#### 7.3.2 禁止 detail 主键命中无语言保证的 legacy URL 缓存

当前 `detail()` 会在 fallback 列表后追加完整 URL 作为 legacy key。该 URL 缓存来自旧版本，无法证明语言。

要求：

1. 正常 detail 读缓存只允许带语言的新键。
2. stale recovery 若保留 legacy key，必须读取 JSON 并确认其中存在目标语言可用值；无法确认则跳过。
3. 不允许因网络失败把旧英文 URL 缓存重新当作 `zh-CN` 数据返回。

#### 7.3.3 `TmdbDetailCache` 增加语言身份

文件：

```text
app/src/main/java/com/fongmi/android/tv/utils/TmdbDetailCache.java
```

改动：

1. `put()` 增加 `String language` 参数。
2. `Entry` 保存 language。
3. `take(key, expected)` 增加当前目标语言校验：
   - 精确匹配；
   - 或策略判定为同一语言；
   - 不匹配则返回 null。
4. `Entry.matches()` 不只校验 `tmdbId/mediaType`。
5. 播放页调用处传入 `TmdbLanguagePolicy.requestLanguage(config)`。

兼容行为：

- 旧调用若无法提供语言，视为 `""`；
- `""` 不能匹配 `zh-CN` 快照，只能作为无语言兜底，不允许覆盖中文结果。

#### 7.3.4 `TmdbMatchCache` 展示字段语言化

文件：

```text
app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java
```

最小方案：

1. `Entry` 增加可选 `language` 字段。
2. 新写入时保存匹配时语言。
3. `toItem()` 不改变返回形状，但新增：

```java
public TmdbItem toDisplayItem(String targetLanguage)
```

4. 目标语言不匹配时，展示字段按空值或原值标记为“不可信”，身份仍可复用。
5. 自动匹配命中缓存后仍必须走 detail 请求归一化标题/简介；不得把英文缓存标题直接作为最终显示。

不做数据库迁移：旧数据缺语言时仅作为身份缓存，不作为最终语言展示依据。

### 7.4 C16 源内嵌数据语言治理

#### 7.4.1 `TmdbSourcePayload.language` 判断

新增或扩展策略：

```java
TmdbLanguagePolicy.isDisplayLanguageCompatible(target, payloadLanguage)
```

规则：

- payload `language = zh-CN` 与目标 `zh-CN` 兼容。
- `zh` 与 `zh-CN` 兼容为中文根，但精确地区低于 `zh-CN`。
- payload `en-US` 与目标 `zh-CN` 不兼容。
- payload language 为空：不能直接判英文，需要按字段推断或保守请求网络补齐。

#### 7.4.2 `TmdbSourceCapabilityPlanner`

文件：

```text
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceCapabilityPlanner.java
```

改动：

1. `plan()` 增加目标语言输入。
2. `coreAvailable()` 不能仅判断 title/overview 非空：
   - 若 payload 声明语言不兼容，core 视为 missing；
   - 若 payload 无 language 但核心字段明显不是目标语言，core 视为 missing。
3. 现有 images、credits、external_ids 等不受展示语言影响，继续按能力组判断。
4. 无用户凭据时不能因语言不兼容而丢弃 source-only 渲染；此时保持离线显示，避免页面回退。

#### 7.4.3 `TmdbSourceMerger`

文件：

```text
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceMerger.java
```

改动：

1. merge item/detail 时增加目标语言。
2. 对 core 展示字段应用语言规则：
   - 源字段语言兼容 => source wins；
   - 源字段语言不兼容且网络字段兼容 => 网络 wins；
   - 两者都不兼容 => 保持现有 fill-only，避免空值。
3. `Origin` 增加或复用来源记录，明确记录 `REMOTE_TMDB_LANGUAGE` 或类似来源，便于测试。
4. images、cast、外部 ID 等非语言字段继续 fill-only，不引入额外网络请求。

#### 7.4.4 集数数据

`TmdbSourceAdapter.episodes()`：

1. 读取 payload language 或按字段推断。
2. 语言不兼容时，不允许中文网络补齐结果被英文源集名挡住。
3. 源季完整且语言兼容时仍完全 source wins。
4. 源季完整但语言不兼容且用户有凭据时，视为 season missing，允许当前语言 season 请求覆盖。

### 7.5 详情页与播放页统一

#### 7.5.1 `TmdbUIAdapter`

文件：

```text
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java
```

要求：

1. `normalizeLoadedItem()` 使用 `preferredTitle()`。
2. `enrichVod()` 使用语言策略后的标题和简介。
3. `seasonEpisodes()` 返回的 `TmdbEpisode` 已是语言感知结果。
4. 快照加载路径调用 `TmdbDetailCache` 时写入/读取语言。
5. `sourceAwareTitle()` 保留显式季标题保护，但 TMDB 标题本身必须先经过语言策略。

#### 7.5.2 `TmdbDetailActivity`

文件：

```text
app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
```

要求：

1. `matchedTmdbTitle()` 使用 `preferredTitle()`。
2. `displayOverview()` 使用统一策略。
3. `fastPlaybackEpisodeTitles()` 只携带语言校验后的集名。
4. `TmdbDetailCache.put()` 传语言。
5. 播放启动 Intent 的 `name`、`overview`、episode titles 必须来自语言感知后的值。
6. 不允许直接把 `matchedTmdbDetail.name/title/overview` 写入播放页。

#### 7.5.3 Leanback `VideoActivity`

文件：

```text
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
```

要求：

1. `TmdbDetailCache.take()` 增加语言参数。
2. `cachedTmdbOverview()` 删除私有英文兜底，改用 `TmdbService.translatedOverview()` 或提取到公共纯策略。
3. `prepareFastTmdbPlaybackItem()` 不使用未校验快照标题覆盖详情。
4. `applyTmdbEpisodeTitles()` 只应用与当前语言一致的集名。
5. `mTmdbUIAdapter.invalidateSubscription()` 或配置变化路径重新读取语言并刷新详情。

#### 7.5.4 Mobile `VideoActivity`

文件：

```text
app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
```

如有对应 fast TMDB / source-only 路径，需同步修改：

1. 快照语言校验；
2. 集名语言校验；
3. 播放页标题/简介读取。

Mobile 与 Leanback 行为必须一致，不允许一个端修复另一个端仍显示英文。

### 7.6 历史、标题学习和匹配缓存

要求：

1. 新写入 `History.vodName` 时，写入语言策略后的当前展示名。
2. 读历史恢复播放时：
   - TMDB 身份仍可用于续播；
   - 展示标题必须在详情加载完成后按当前语言刷新；
   - 不允许旧英文历史名阻塞中文刷新。
3. `MediaTitleLearningStore` 新样本写入当前语言值。
4. 不批量迁移历史。
5. 若无网络且历史只有英文，可显示历史值；这是降级，不算失败。

### 7.7 日志和诊断

新增日志，不打印凭据：

```text
tmdb-language target=zh-CN source=translations primary=... selected=... sourceLanguage=zh-CN exact=true
tmdb-language target=zh-CN source=source-payload primary=... selected=... fallback=en reason=source-language-mismatch
tmdb-language-cache snapshotLanguage=en-US target=zh-CN result=reject
```

调试信息必须能回答：

1. 最终标题来自顶层、translations、source、network、cache 还是 history。
2. 为什么使用英文。
3. 快照/缓存为什么被拒绝。

## 8. 实施步骤

建议分成三个原子提交，每个提交独立可回滚。

### 阶段 1：语言策略与 TMDB service

改动范围：

```text
app/src/main/java/com/fongmi/android/tv/utils/TmdbLanguagePolicy.java
app/src/main/java/com/fongmi/android/tv/service/TmdbService.java
app/src/test/java/com/fongmi/android/tv/utils/TmdbLanguagePolicyTest.java
app/src/test/java/com/fongmi/android/tv/service/TmdbServiceTranslationTest.java
app/src/test/java/com/fongmi/android/tv/service/TmdbServiceCacheKeyTest.java
```

验证：

```bash
bash ./gradlew :app:testArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.utils.TmdbLanguagePolicyTest \
  --tests com.fongmi.android.tv.service.TmdbServiceTranslationTest \
  --tests com.fongmi.android.tv.service.TmdbServiceCacheKeyTest \
  --no-daemon --console=plain
```

阶段验收：

1. translations 有中文时，顶层英文不会赢。
2. 无中文时，明确降级。
3. 缓存键仍区分语言。

### 阶段 2：源 payload、合并与缓存身份

改动范围：

```text
app/src/main/java/com/fongmi/android/tv/utils/TmdbDetailCache.java
app/src/main/java/com/fongmi/android/tv/bean/TmdbMatchCache.java
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceAdapter.java
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceCapabilityPlanner.java
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceMerger.java
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java
app/src/test/java/com/fongmi/android/tv/utils/TmdbDetailCacheTest.java
app/src/test/java/com/fongmi/android/tv/bean/TmdbMatchCacheTest.java
app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSourceMergerTest.java
app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSourceCapabilityPlannerTest.java
```

验证：

```bash
bash ./gradlew :app:testArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.utils.TmdbDetailCacheTest \
  --tests com.fongmi.android.tv.bean.TmdbMatchCacheTest \
  --tests com.fongmi.android.tv.ui.helper.TmdbSourceMergerTest \
  --tests com.fongmi.android.tv.ui.helper.TmdbSourceCapabilityPlannerTest \
  --no-daemon --console=plain
```

阶段验收：

1. 英文 source core 不再压过中文 network core。
2. 快照语言不匹配会拒绝。
3. source-only 无凭据路径不回退整页。

### 阶段 3：详情页、播放页和历史展示收口

改动范围：

```text
app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
docs/C21-tmdb-zh-language-fix.md
```

验证：

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.activity.TmdbDetailLanguageRegressionTest \
  --no-daemon --console=plain

bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.activity.VideoTmdbLanguageRegressionTest \
  --no-daemon --console=plain
```

阶段验收：

1. 详情页与播放页标题/简介一致。
2. 中文集名在播放页不会被英文快照替换。
3. 历史旧英文名在详情加载后刷新为中文。

### 阶段 4：设备验证和测试包

使用本工作区分配设备：

```text
192.168.50.3:5557
```

构建安装：

```bash
scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5557
```

要求：

1. 覆盖安装，禁止卸载。
2. 如有构建/安装任务正在运行，排队等待，不得强抢。
3. 构建完成后清理不使用的构建进程和临时文件。
4. 不打正式包。

## 9. 测试计划

### 9.1 JVM 单测

新增测试类：

```text
TmdbLanguagePolicyTest
TmdbServiceTranslationTest
TmdbSourceLanguageMergeTest
TmdbDetailCacheLanguageTest
TmdbDetailLanguageRegressionTest
VideoTmdbLanguageRegressionTest
```

必须覆盖：

1. 顶层英文 + translations 中文 => 选择中文标题。
2. 顶层中文 + translations 英文 => 保持中文。
3. 目标 `en-US` => 英文仍优先，不能写死中文。
4. `zh-Hans` 与 `zh-CN` 兼容。
5. `zh-TW` 与 `zh-CN` 不等价，但可作为中文根降级。
6. season translations 有中文集名 => 选择中文。
7. episode translations 有中文集名/简介 => 选择中文。
8. 中文完整缺失 => 按顺序降级到原语言/英文。
9. 英文 source core + 中文 network core => network core wins。
10. 中文 source core + 英文 network core => source wins。
11. source-only 无凭据 + 英文 source => 仍渲染 source，不回退页面。
12. `TmdbDetailCache` 中英文快照在 `zh-CN` 下被拒绝。
13. 缺语言旧 `TmdbMatchCache` 不作为最终展示。
14. 缓存键对 `zh-CN` 与 `en-US` 不串。
15. `zh-cn` / `zh-CN` / `zh-Hans` 归一化稳定。

### 9.2 静态源码测试

新增或扩展 source test，断断：

1. `TmdbService` 中不存在绕过 `TmdbLanguagePolicy` 的标题/简介最终选择。
2. `VideoActivity` 不再包含私有英文 overview fallback。
3. `TmdbDetailCache.put/take` 均包含语言。
4. 播放启动 Intent 的 TMDB 标题来自 `preferredTitle()`。

### 9.3 设备验收

在 `192.168.50.3:5557` 覆盖安装测试包。

准备三类测试数据：

1. 国产剧 A：TMDB 顶层英文，detail translations 有中文标题/简介。
2. 国产剧 B：season/episode translations 有中文集名，但顶层 name 为英文。
3. 源内嵌 C16：payload language 为 `en-US`，detail 顶层英文；本地网络 mock 或测试源提供中文 detail。

验收操作：

1. 清空或仅临时使用测试缓存目录；禁止清除用户正式数据。
2. 配置 TMDB language 为 `zh-CN`。
3. 打开剧集 A 详情页：
   - 标题中文；
   - 简介中文；
   - 日志显示 selected language 为中文；
   - 播放页标题和详情页一致。
4. 打开剧集 B 分季页：
   - 集名中文；
   - 播放页当前集名中文；
   - 单集详情弹窗中文。
5. 打开源内嵌剧 C：
   - 首屏若源先显示英文，异步刷新后必须变为中文；
   - 若无凭据，允许英文 source-only 显示，但日志必须说明 fallback 原因。
6. 从播放历史重新进入：
   - 若 TMDB 中文可用，历史旧英文名不得最终显示；
   - 详情和播放页刷新为中文。
7. 断网重试：
   - 页面不崩溃；
   - 显示已有可用数据；
   - 日志明确记录无网络降级。
8. 切换语言为 `en-US`：
   - 英文内容仍正常显示；
   - 中文优先逻辑不影响英文用户。

## 10. 验收标准

全部满足才算通过：

1. JVM 定向测试全部通过，失败、错误、跳过为 0。
2. Mobile/Leanback 相关 JVM 或 source test 全部通过。
3. `scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5557` 覆盖安装成功。
4. 设备验收中，已存在中文数据的国产剧不再显示英文标题、简介或集名。
5. 播放页与详情页语言一致。
6. 英文配置、source-only 和断网降级无回归。
7. 日志能定位每个显示值来源。
8. 无 API Key 泄漏到日志。
9. 未修改播放器内核和上游二进制。
10. 相关提交有唯一恢复标签。

## 10.1 实施记录

- 2026-09-25：完成语言策略、detail/season/episode 展示值、请求/缓存语言归一、源 payload 语言治理、语言感知合并、详情/播放页语言身份与 Leanback fast playback 收口。
- 2026-09-25：新增并更新 JVM 回归测试，覆盖中文 translations 优先、英文明确降级、语言别名缓存键、快照拒绝、匹配缓存语言身份、英文 source core 被中文 network 覆盖。
- 2026-09-25：Leanback 与 Mobile 定向 JVM 测试分别通过，覆盖中文 translations 优先、英文明确降级、语言别名缓存键、快照拒绝、匹配缓存语言身份、英文 source core 被中文 network 覆盖、source payload fill-only 接线和 season 名称语言策略。
- 2026-09-25 04:42：`scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5557` 覆盖安装成功；设备确认 `lastUpdateTime=2026-09-25 04:42:00`，应用可在 5557 启动。设备端三类真实 TMDB 数据场景仍待用户指定/提供后执行。
- 2026-09-25 第二轮复评：修正 `zh-CN` 与 `zh-TW` 的缓存/源数据兼容边界，避免繁体快照或繁体源数据阻断简体目标语言刷新；同时移除 Leanback 播放页遗留的私有语言兜底辅助方法，改为统一走 `TmdbService`/`TmdbLanguagePolicy`。
- 2026-09-25 第二轮验证：Leanback Arm64 Debug Java 编译通过；Leanback 相关 JVM 回归（语言策略、TMDB 翻译、缓存键、详情快照、匹配缓存、源合并、能力规划、直连播放静态回归）通过；Mobile 定向静态回归通过。

## 11. 风险与回滚

| 风险 | 缓解 | 回滚 |
| --- | --- | --- |
| 误把英文原题判成中文 | 使用字符策略 + translations 语言码，不依赖猜测字符串 | 撤销阶段 1 |
| 增加请求量 | 优先复用现有 translations，不默认双请求 | 撤销阶段 2 |
| source-only 无凭据页面被误回退 | 无凭据保留现有渲染，只记录降级日志 | 撤销阶段 2 |
| 历史旧数据不可修复 | 不迁移历史，播放时按当前语言刷新 | 撤销阶段 3 |
| Mobile/Leanback 行为不一致 | 双端测试和设备矩阵验收 | 分端修复或整体撤销阶段 3 |
| 缓存大量 miss | 只对语言不匹配的展示值拒绝；身份和技术数据继续可用 | 恢复 legacy cache 读取 |

## 12. 完成定义

- 代码、测试和本文档状态更新全部提交。
- 设备验收通过并记录。
- 用户确认中文显示符合预期。
- 生成唯一 annotated local recovery tag。
- 不推送远端，除非用户明确要求。

## 13. 下一步

- Leanback 测试包覆盖安装已完成；三类真实 TMDB 数据验收仍待用户提供/指定具体剧目后执行。
- 完成设备验收后，按任务守卫提交并创建唯一 annotated local recovery tag。
