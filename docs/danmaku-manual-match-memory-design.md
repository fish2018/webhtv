# 弹幕手动匹配记忆设计

## 状态

已实现并完成合并复评：站点级与 TMDB 全局关联可复用手动匹配意图；关键词绑定到产生所选结果的已提交请求。验证与当前同步状态见 `docs/beta-sync-review-20260905.md`。

## 日期

2026-09-05

## 背景

WebHTV 的自动弹幕搜索由 `DanmakuApi.search(MediaTitleRequest, Consumer<Danmaku>)` 发起。它先查询本集缓存，未命中后通过 `MediaTitleResolver` 解析搜索标题，再调用弹幕接口搜索。

当前手动选择弹幕时，两个入口都会写入 `DanmakuMatchCache`：

- `DanmakuSearchDialog.rememberManualDanmaku`
- `DanmakuSearchInputDialog.rememberManualDanmaku`

写入的缓存 key 为：

```text
siteKey@@@vodId@@@episodeKey
```

其中 `episodeKey` 会被规范化为本集编号。这个粒度保证了同一集不会重复搜索，但换到下一集后，缓存 key 变化，自动搜索会重新走标题解析流程。

标题解析流程会把手动匹配写入 `MediaTitleLearningStore`，学习样例的 `expectedTitle` 是清洗后的作品名，通常不包含季信息。例如“某动画 第二季”会被清洗为“某动画”。因此下一集搜索时会优先搜索这个作品名，而弹幕源常将最新季排在前面，最终再次匹配到最新季。

用户必须每集手动调整弹幕，无法延续一次手动选择。核心诉求是：同一部片源中，下一集自动弹幕搜索应优先沿用用户手动确认过的搜索意图。

另一个使用场景是换源：用户切到其他站点或不同 `vodId` 时，`siteKey@@@vodId` 会变化。如果播放内容已经匹配到 TMDB，同一部作品、同一季应能复用一次手动匹配，而不是要求用户在每个站点重复操作。

## 参考

已对照本地参考项目 `F:/Workspace/CatVodSpider/app/src/main/java/com/github/catvod`：

- `DanmakuUIHelper.showSearchDialog` 使用稳定视频 key 读取和保存手动搜索关键词，用户修改关键词后下次搜索仍使用该关键词。
- `DanmakuManager` 将弹幕结果按“剧名 + 来源 + API + epId”缓存，换集时通过 `epId` 差值推断下一集弹幕。
- 参考项目可依赖结构化 `DanmakuItem.epId`、`animeTitle`、`apiBase` 字段。

WebHTV 不能直接移植 `epId` 递增方案：现有 `Danmaku` 模型只有 `name`、`url`、`source`、`apiSourceName`，没有结构化集 ID 和系列字段。直接改 URL 或数字递推，容易跨源拿错集、拿错季。

## 目标

1. 同一站点同一影片 ID 下，手动匹配过的弹幕搜索意图能延续到后续集。
2. TMDB 已可靠匹配到同一部作品和同一季时，换源跨站点也能复用手动匹配意图。
3. 下一集自动搜索优先使用用户确认的关键词，避免默认匹配最新季。
4. 手动再次选择时，新记忆覆盖旧记忆。
5. 不同影片之间不串扰；TMDB 身份不可信时不做全局复用。
6. 手动关键词失效或无结果时，保留现有自动搜索回退能力。
7. 不修改播放器、弹幕渲染、下载和缓存文件格式以外的核心行为。

## 非目标

1. 不推断或构造弹幕 URL。
2. 不直接将上一集弹幕内容用于下一集。
3. 不新增 UI 设置页，不改变弹幕搜索对话框交互流程。
4. 不引入网络请求依赖或第三方库。
5. 不解决弹幕内容与画面的对齐问题，匹配粒度仍由弹幕源结果决定。

## 方案对比

### 方案 A：分层延续手动搜索关键词（推荐）

在 `DanmakuMatchCache` 中增加两层映射：

```text
siteKey@@@vodId -> searchTitle
tmdbId@@@seasonNumber -> searchTitle
```

第一层用于同一站点同一片源的后续集；第二层用于已确认 TMDB 身份的同一作品、同一季。手动选择时写入用户实际使用的搜索关键词；下一集或换源后自动搜索时优先使用该关键词。

优点：

- 实现粒度小，只需修改缓存对象、两个手动选择入口和自动搜索入口。
- 保留用户输入的“某某第二季”等季信息，不会被标题清洗逻辑丢掉。
- 无需新增 SharedPreferences 表，可复用现有持久化入口。
- TMDB 层可以跨 `siteKey/vodId` 复用，换源后不需要重复手动调整。
- 搜索失败可回退现有 resolver，不会永久卡在错误关键词。

缺点：

- 关键词映射是“用户最后一次手动选择”的意图，不是多季、多版本的完整数据库。
- 如果用户手动选择时只输入了纯作品名，记忆不会自动补全季信息，但至少不会漂移到 resolver 生成的其他候选标题。
- 自动搜索结果仍需依赖现有结果排序和选择逻辑。
- TMDB 匹配错误时可能把错误意图扩散到多个站点，因此全局写入必须依赖可信身份。
- 站点集数与 TMDB 季集映射不统一时，全局层需要降级，不能强行复用。

### 方案 D：仅使用 TMDB 全局记忆

只保存 `tmdbId@@@seasonNumber -> searchTitle`，不再保存站点级映射。

优点：

- 数据结构最少，换源和换集都由一个 key 处理。

缺点：

- 未匹配 TMDB 的播放完全失去记忆能力。
- 站点级最后修正无法优先于全局旧记忆。
- TMDB 短暂误匹配会影响原本正确的站点匹配。

结论：不采用。TMDB 层应作为跨站点复用层，站点层仍保留。

### 方案 B：系列级缓存手动 URL

保存 `siteKey@@@vodId -> danmaku.url`，下一集直接或改造该 URL。

优点：

- 不需要再发搜索请求。

缺点：

- WebHTV 的弹幕 URL 缺少结构化 epId 信息。
- URL 改造规则高度依赖弹幕源实现，无法保证跨源正确。
- 弹幕源改版时难以排查。

结论：不采用。

### 方案 C：移植 CatVodSpider 的 epId 递增

记录当前集的 epId，下一集按差值计算目标 epId，再查系列缓存。

优点：

- 参考项目已有实现，命中时准确率高。

缺点：

- WebHTV `Danmaku` 模型没有 `epId`、`animeTitle`、`apiBase` 字段。
- 需要扩展弹幕源响应模型、解析逻辑和缓存结构，涉及搜索 API 兼容性。
- 弹幕源返回结构不统一时，解析失败率不可控。

结论：暂不采用。若后续弹幕源响应统一携带结构化集 ID，可再评估。

## 推荐方案设计

### 数据结构

在 `DanmakuMatchCache` 中新增两个映射：

```java
private Map<String, Entry> seriesItems;
private Map<String, Entry> tmdbSeasonItems;
```

序列化后的结构继续由现有 Gson 持久化。旧缓存没有这两个字段时，反序列化后按空 Map 处理，不需要迁移脚本。

站点系列级 key：

```text
clean(siteKey) + "@@@" + clean(vodId)
```

TMDB 全局季级 key：

```text
"tmdb" + "@@@" + tmdbId + "@@@" + seasonNumber
```

`tmdbId` 必须来自已确认的 TMDB 匹配，而不是从弹幕标题猜测。`seasonNumber` 小于等于 0 时不写全局层。

两层 `Entry` 复用现有字段：

- `name`：手动选择的弹幕项展示名。
- `url`：手动选择的本集 URL，仅作诊断参考，不用于下一集或换源加载。
- `sourceName`：手动选择的弹幕来源。
- `searchTitle`：用户手动搜索使用的关键词，下一集优先使用。
- `rawTitle`：选择时的站点标题。
- `ruleTitle`：由关键词清洗出的规则标题。
- `expectedTitle`：弹幕项展示名清洗出的作品名。
- `updatedAt`：更新时间。

### 写入时机

手动选择弹幕时：

1. 保留现有本集 `put(siteKey, vodId, episodeName, ...)`。
2. 写入站点系列级 `putSeries(siteKey, vodId, searchTitle, rawTitle, item)`。
3. 如果当前播放上下文有可信 TMDB 身份，写入全局 `putTmdbSeason(tmdbId, seasonNumber, searchTitle, rawTitle, item)`。
4. `searchTitle` 取产生当前结果的已提交请求关键词，不读取用户随后编辑的输入框；为空时回退 rawTitle。
5. 站点系列级条目总是覆盖旧值，代表“用户最后一次手动确认的意图”。
6. TMDB 全局条目同样覆盖旧值，但仅在 TMDB 身份可信时写入。

两个手动入口必须同步写入：

- `DanmakuSearchDialog.rememberManualDanmaku`
- `DanmakuSearchInputDialog.rememberManualDanmaku`

### TMDB 身份获取

全局层不主动发起新的 TMDB 请求，也不解析弹幕结果推断 TMDB ID。实现应从播放请求上下文或现有 TMDB 匹配缓存读取：

- `tmdbId` 必须已存在且有效。
- `seasonNumber` 必须已解析且为正数。
- 媒体类型应能确定为电视剧或动漫。
- TMDB 匹配置信度不足、仅有作品 ID 但季映射不明确、或当前站点使用绝对集数而无法映射到 TMDB 季集时，跳过全局写入。

这保证全局层只承担“跨站点身份关联”，不引入新的匹配失败面。

### 读取时机

`DanmakuApi.search(MediaTitleRequest, Consumer<Danmaku>)` 调整为以下顺序：

1. 查询本集缓存，命中则直接返回。
2. 查询站点系列级手动搜索关键词。
3. 查询 TMDB 全局季级手动搜索关键词，与站点关键词相同则去重。
4. 按站点级、TMDB 全局季级的顺序，将非空关键词放在常规标题之前调用 `searchFirst`；站点关键词无结果时可以继续尝试全局关键词。
5. 若手动关键词搜索无结果，继续现有 `resolver.queryTitles()`。
6. 清洗标题 fallback 与 AI fallback 保持现有顺序。
7. 两层关键词都为空时，不改变现有搜索行为。

搜索仍使用当前集的 `episodeName`，不会复用上一集集数。

优先级原则：

```text
本集 URL 缓存
  -> 站点级手动关键词
  -> TMDB 全局季级关键词
  -> resolver 常规标题
  -> resolver 清洗标题
  -> AI fallback 标题
```

站点级优先于全局级，因为用户可能针对某个站点的特殊标题或片源结构做过最后一次修正。

### 与学习数据的关系

现有 `MediaTitleLearningStore` 仍保持不变：

- 本集 URL 缓存负责“同一集不重复搜索”。
- 系列级关键词负责“下一次搜索沿用用户意图”。
- 学习样例继续用于标题解析，但自动弹幕搜索中手动关键词优先级高于学习样例。

### 边界行为

- `siteKey` 或 `vodId` 为空时不写入系列级记忆。
- TMDB ID、季数或身份可信度不足时不写全局层。
- 手动选择空弹幕或空 URL 时不写入。
- 用户再次手动选择时，覆盖 `siteKey@@@vodId` 下的旧关键词；若 TMDB 身份可信，同步覆盖全局层。
- 手动关键词搜索失败后回退现有自动搜索，不做次数统计。
- 清理弹幕匹配缓存或学习数据时，系列级记忆随 `DanmakuMatchCache` 一起清除。
- 不按 rawTitle 做跨 `vodId` 匹配，避免不同版本之间串扰。
- TMDB 层 key 使用作品 ID 和季，不使用集数；集数只作为当前搜索参数。
- 换源后使用全局关键词重新搜索，不跨源复用上一集 URL。

## 验收标准

1. 同一 `siteKey + vodId` 下，第 1 集手动输入“某动画 第二季”并选择结果；第 2 集自动搜索时发出的第一个请求使用“某动画 第二季”。
2. 第 2 集返回结果后，本集 URL 缓存正常建立。
3. 播放内容匹配到同一 `tmdbId + seasonNumber` 后，换站点或换 `vodId`，自动搜索优先使用全局手动关键词。
4. 手动关键词无结果时，仍会走现有 resolver 标题搜索。
5. 不同 `siteKey` 或不同 `vodId` 且无 TMDB 关联时，不共享系列级关键词。
6. TMDB 匹配错误或季映射不可信时不写全局层。
7. 旧 `DanmakuMatchCache` JSON 反序列化后新增 Map 为空，不崩溃。
8. 再次手动选择时，站点级关键词被覆盖；TMDB 身份可信时全局关键词同步覆盖。

## 测试计划

新增或扩展单元测试：

- `DanmakuMatchCacheTest`
  - 系列级写入与读取。
  - TMDB 全局季级写入与读取。
  - 手动覆盖旧关键词。
  - `siteKey/vodId` 隔离。
  - TMDB 层与站点层隔离。
  - TMDB ID 或季数无效时不写入。
  - 旧 JSON 无 `seriesItems` 字段时可解析。
- `DanmakuApiSourceTest`
  - 下一集搜索优先使用系列级手动关键词。
  - 站点级缺失时使用 TMDB 全局关键词。
  - 站点级优先于 TMDB 全局关键词。
  - 手动关键词为空时保持现有标题顺序。
  - 手动关键词无结果时回退 resolver 标题。
  - 本集缓存命中时不再搜索。

建议执行：

```bash
bash ./gradlew app:testDebugUnitTest --tests 'com.fongmi.android.tv.bean.DanmakuMatchCacheTest' --tests 'com.fongmi.android.tv.api.DanmakuApiSourceTest'
```

如果 `DanmakuApi.search(MediaTitleRequest, ...)` 的搜索行为难以在现有测试中注入，测试可先覆盖 `DanmakuMatchCache` 的系列级读取与写入，并将 `DanmakuApi` 的优先级逻辑拆到可测的纯函数中，避免为了测试引入网络层 mock。

## 回滚

改动集中在：

- `app/src/main/java/com/fongmi/android/tv/bean/DanmakuMatchCache.java`
- `app/src/main/java/com/fongmi/android/tv/api/DanmakuApi.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/DanmakuSearchDialog.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/DanmakuSearchInputDialog.java`
- 需要把可信 TMDB 身份传入弹幕搜索请求的相关调用链
- 对应测试文件

若线上行为异常，可整体 revert 以上改动。旧缓存 JSON 不受影响，新增字段会被忽略；新缓存 JSON 被旧版本读取时，多余字段也会被 Gson 忽略。

## 开放问题

1. 用户手动选择时，是否只输入了纯作品名而没有季信息？若是，方案 A 只能保证搜索词不漂移，无法自动识别应选哪一季。
2. 弹幕源搜索接口是否支持“作品名 + 季”关键词稳定返回目标季结果？需要在真实设备上用用户实际站点验证。
3. 现有播放调用链中 TMDB `tmdbId + seasonNumber` 的最短可靠传递路径需要实现前确认。
4. 是否需要在弹幕搜索对话框中显示“已记住关键词”或“已同步 TMDB”？首版不增加 UI，避免扩大改动面。
