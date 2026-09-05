# TMDB 多季度识别与手动季度绑定设计

- 状态：Phase 1 已实现
- 日期：2026-08-10
- 适用范围：TMDB 自动匹配、详情页刮削、原生增强/沉浸融合/炫彩详情、移动端与 TV 播放页
- 设计基线：`beta` / `4a2c76477e3385fa0714ef59ad03dd18a6bffbf8`
- 集成基线：`beta` / `2c6d42df47`
- 关联代码：`TmdbUIAdapter`、`TmdbMatcher`、`TmdbMatchCache`、`EpisodeSeasonPolicy`、`SourceEpisodeSeasonCache`、`TmdbDetailActivity`、两端 `VideoActivity`

## 1. 摘要

当前实现已经能较可靠地匹配“作品身份”，也具备标题、年份、媒体类型、分季变体和集号校验，但**作品身份匹配**与**源线路对应哪个 TMDB 季度**仍混在一条隐式流程中：

1. `TmdbMatcher` 只返回 `TmdbItem`，不返回季度映射；
2. `TmdbMatchCache` 只保存 TMDB 作品，不保存季度；
3. `TmdbUIAdapter` 用单个 `sourceSeasonNumber` 代表整个 `Vod`；
4. 无法识别季度时，`episodeMetadataSeasonCandidates(-1)` 会依次尝试第 1 季和特别篇；
5. `applyEpisodeTitles()` 用同一季元数据覆盖当前 `Vod` 的全部线路。

因此，多季度作品只要标题、线路名或集名缺少明确季度，系统就可能把第 2/3 季按第 1 季刮削；若 TMDB 第 1 季不存在、集数不兼容或候选作品本身是“分季”变体，则表现为匹配不到、标题错位、海报/单集元数据异常。

本设计决定：

> **将“TMDB 作品身份”与“源内容到 TMDB 季度的绑定”拆成两个独立层级；自动判断不确定时保持源集列表不变，并提供可持久化、可清除、可重选的手动季度绑定。**

MVP 不引入后端服务，不新增 TMDB 远程接口；现有 `detail()` 和 `season()` 已能提供季度列表与单季详情。

## 2. 背景、问题与约束

### 2.1 用户可见问题

- 源标题是作品总名，但内容实际是第 2 季，系统默认使用第 1 季；
- 标题中的“第二部”“S02”等信号被站源格式或清洗隐藏；
- 第 0 季与多个普通季共存时，自动回退命中错误季度；
- TMDB 搜索命中同名作品或“分季”拆分条目，媒体身份和季度同时出错；
- 一个 `Vod` 的多个 `Flag` 实际代表不同季度，但当前只保存一个全局季度；
- 用户通过“重新匹配 TMDB”选对作品后，仍无法指定源内容对应第几季；
- 匹配失败时可能显示错误的单集标题、剧照或季度进度。

### 2.2 约束

1. 线路 `Episode` 是播放事实源，TMDB 只能补充元数据，不能创建或删除可播放集；
2. 必须同时适配移动端、TV 遥控器、详情直放和现有播放页；
3. 不能因网络失败阻断播放；
4. 不能把“未知季度”静默解释为“第一季”；
5. 手工选择必须持久化，但源条目或 TMDB 作品变化后不能无限复用旧绑定；
6. 第 0 季是合法季度，不能与“未知”共用同一个值；
7. 现有媒体身份缓存与标题学习数据需要保持兼容。

## 3. 当前项目实现调研

### 3.1 自动媒体身份匹配

`TmdbUIAdapter.autoMatch(videoName, vod)` 当前流程：

1. `captureSourceSeason()` 尝试解析源季度；
2. 从 `TmdbMatchCache` 按站点、Vod ID、源标题读取作品身份；
3. 对缓存命中执行 `TmdbMatchPolicy.isUnwantedSplitSeasonVariant()` 防护；
4. 缓存未命中时，`MediaTitleResolver` 生成候选查询词；
5. `TmdbMatcher.searchAndMatch()` 按标题、年份、媒体类型、地区/语言及分季变体评分；
6. 保存 `TmdbItem` 并加载详情；
7. 失败时发送完成刷新，让页面继续使用源数据。

已有优点：媒体类型和年份有约束、支持标题清洗与 AI 回退、对“分季”重复条目有惩罚、加载失败能安全结束、媒体缓存能处理部分 Vod ID 冲突。

关键缺口：搜索阶段识别出的源季度只用于辅助选择 TMDB 作品，最终结果仍只是 `TmdbItem`，季度没有进入返回值或缓存。

### 3.2 源季度解析

`captureSourceSeason()` 的信号优先级为：

1. Intent `tmdb_play_season_number`；
2. 当前标题、Intent 标题、`Vod.name`、`Vod.remarks`；
3. 所有 `Flag.show` 中一致的显式季度；
4. 所有 `Episode.name` 中一致的显式季度；
5. 已有关联 `TmdbEpisode.seasonNumber` 中一致的季度；
6. 无法确认时为 `-1`。

这套解析对“一个 Vod 只对应一季”有效，但输出是一个全局 `int`。不同线路包含不同季度时，一致性检查会返回未知；随后分集刮削仍可能进入第 1 季回退。

### 3.3 分集元数据应用

`TmdbUIAdapter.applyEpisodeTitles()` 调用 `EpisodeSeasonPolicy.episodeMetadataSeasonCandidates(sourceSeasonNumber)`：

- 已知季度：只尝试该季度；
- 未知季度：依次尝试 `1`、`0`。

拿到第一份非空季度详情后，按集号或位置把 `TmdbEpisode` 写入所有线路的 `Episode`。主要风险：

- “未知”被隐式降级为第 1 季；
- 只验证单个集号，未验证整条线路是否属于该季；
- 多线路、多季度共存时共用同一季；
- 第 1 季集数恰好覆盖源集号时，错误映射很难被现有校验发现。

### 3.4 缓存与手动匹配

`TmdbMatchCache.Entry` 保存媒体 ID、类型和展示字段，不保存季度、绑定来源、源指纹或失效条件。`SourceEpisodeSeasonCache` 只缓存当前页面内的一致季度扫描结果，不能持久化人工选择。

`TmdbDetailActivity`、leanback `VideoActivity`、mobile `VideoActivity` 已提供手动 TMDB 媒体匹配，支持搜索、选择 `TmdbItem`、写缓存和重新加载，但缺少：

- TV 候选选中后的季度步骤；
- 已匹配作品的“仅重选季度”入口；
- 手动绑定状态展示与清除；
- 季度候选的集数、首播年份和风险预览；
- 线路级季度映射。

### 3.5 可复用的安全策略

`EpisodeSeasonPolicy.resolveAvailableSeasons()` 已规定：不能可靠分季时返回空集合，页面保持源集平铺；`docs/tmdb-playable-episode-availability-design.md` 也确立了“TMDB 是元数据源，线路是播放事实源”的原则。本设计应统一到这套保守策略，而不是继续维护未知季度默认第一季的旁路。

## 4. 外部开源项目调研

调研日期：2026-08-10。主要依据为官方文档和官方仓库源码。

### 4.1 Jellyfin

- Web `Identify` 对话框把名称、年份和各 Provider ID 作为搜索条件；
- 搜索返回远程候选，选择后调用 `Items/RemoteSearch/Apply/{itemId}` 应用并刷新；
- Provider ID 可写入文件/目录名，减少模糊搜索；
- `Season` 是由 `SeasonMetadataService` 管理的独立元数据实体。

可借鉴：支持 `tmdb:12345` 直达、搜索和应用分步、季度有独立刷新边界、用户能看到并重新选择绑定。

不直接照搬：Jellyfin 是有服务端数据库的媒体库；本项目是站源运行时客户端，不能依赖预扫描实体。

### 4.2 Sonarr

- Manual Import 资源显式携带 `Series`、`SeasonNumber` 和 `Episodes`；
- Reprocess 请求携带 `SeriesId`、`SeasonNumber`、`EpisodeIds`，用户选择可覆盖自动解析；
- `ParsingService` 先解析发布名，再通过 `SceneMappingService` 映射到标准季集编号；
- 系列身份和季集映射分属不同模型，`MappedSeasonNumber` 独立保留。

可借鉴：媒体身份与季集映射分层、人工选择优先、映射记录来源和上下文。

不直接照搬：Scene/XEM、下载导入和数据库模型远超客户端需要；MVP 只实现“源内容 -> TMDB 季度”。

### 4.3 Kodi

- 以目录结构和 `SxxEyy` 命名作为季集号主要事实；
- 无法正确识别时允许刷新信息并重新搜索；
- NFO 的 `uniqueid` 与 episode guide 可固定 Provider 身份；
- 节目级 NFO 和单集 NFO 分离。

可借鉴：显式季度信号优先、人工钉住持久化、系列身份与单集映射分别记录、无法判断时要求修正而不是继续猜。

### 4.4 tinyMediaManager

- 支持 `TMDB:xxxxxx`、`TVDB:xxxxxx` 等 Provider ID 直接搜索；
- 官方明确正则解析不能 100% 正确；
- 解析错误时允许手动编辑季度、集号和元数据；
- 自动最佳匹配与人工编辑并存。

可借鉴：Provider ID 直达、自动失败有人工纠正、人工覆盖不会被后续自动刮削覆盖。

### 4.5 TMDB Episode Groups

TMDB 官方提供 Episode Groups 列表与组详情，可表达 DVD 顺序、绝对顺序或其他非标准分组。普通多季度错配不需要它；动漫绝对集、平台拆季和播出顺序差异可在后续阶段支持，MVP 仍以标准 `season_number` 为唯一目标。

## 5. 目标与非目标

### 5.1 目标

1. 未知季度不再默认刮削第 1 季；
2. 用户可在选对 TMDB 作品后明确选择季度；
3. 手动季度绑定跨页面、重进应用仍有效；
4. 媒体重新匹配、源内容显著变化时旧绑定自动失效或要求重验；
5. 多季度/多线路无法安全映射时保留原始集列表和名称；
6. 移动端与 TV 端具备一致能力；
7. 不新增服务端，不改变 TMDB 配置或鉴权方式；
8. 为未来线路级映射和 Episode Groups 留出扩展点。

### 5.2 非目标

- 不自动补齐源站不存在的集；
- 不把 TMDB 季度列表当成可播放季度列表；
- 不在 MVP 中提供逐集任意映射；
- 不实现 Sonarr XEM/Scene 全量编号体系；
- 不修改播放历史主键、线路 URL 或源 Vod ID；
- 不因匹配失败阻止播放。

## 6. 核心设计决策

### 6.1 拆分媒体身份与季度绑定

1. **媒体身份 `MediaIdentity`**：`tmdbId`、`mediaType`、标题和展示信息；
2. **季度绑定 `SeasonBinding`**：当前源内容对应哪个 `seasonNumber`，及作用域、来源、校验状态和源指纹。

一个媒体身份可以没有季度绑定；电影永远不需要季度绑定。

### 6.2 人工优先、自动保守

解析优先级：

1. 当前会话明确选择（季度导航/播放 Intent）；
2. 持久化人工季度绑定；
3. 标题、备注、线路和集名中的一致显式季度；
4. 唯一普通季度或唯一合法季度；
5. 源总集数与某一季集数唯一精确相等；
6. 源总集数与全部季度集数之和精确相等时允许多季切分；
7. 其他情况返回 `AMBIGUOUS`。

禁止未知时自动尝试 `[1, 0]` 并采用第一个成功结果。

### 6.3 不改变播放事实

- 手动选择只改变元数据映射；
- 不增删 `Episode`；
- 某集在所选季度不存在时保留源名称、图片和 URL；
- 映射失败不影响选集、换源和历史续播。

### 6.4 结果可解释

每次解析输出：状态、来源、置信度和原因，供日志、状态栏与测试断言使用。

## 7. 数据模型

### 7.1 新增 `TmdbSeasonMatchCache`

不把 `seasonNumber` 直接加入 `TmdbMatchCache.Entry`，因为媒体身份生命周期更稳定；同一作品可能按不同源条目绑定不同季度；清除季度不应丢失媒体身份。

建议模型：

```java
public final class TmdbSeasonMatchCache {
    Map<String, Entry> items;

    public static final class Entry {
        int version;                 // 初始为 1
        int tmdbId;
        String mediaType;            // 必须为 tv
        Integer seasonNumber;        // 0 合法；null 表示保持平铺
        String mode;                 // MANUAL_SEASON / MANUAL_FLAT
        String scopeType;            // MVP 为 VOD；预留 FLAG
        String sourceTitle;
        String sourceFingerprint;
        int sourceEpisodeCount;
        int tmdbSeasonEpisodeCount;
        long updatedAt;
    }
}
```

### 7.2 缓存键和失效

MVP 键：

```text
siteKey + SYMBOL + vodId + SYMBOL + normalizedSourceTitle
```

Entry 额外保存 `tmdbId`，读取时必须与当前媒体身份一致。失效规则：

- `tmdbId` 变化：立即失效；
- 绑定季度不再存在：立即失效；
- 标题不兼容且 Vod ID 被复用：失效；
- 仅集数增减且季仍存在：继续人工绑定，但重新验证并记录日志；
- `MANUAL_FLAT` 也持久化，防止系统再次猜第一季。

源指纹建议包含规范化标题、线路数、总集数、首尾集稳定键。

### 7.3 Setting 与应用缓存

新增：

```java
AppCache.KEY_TMDB_SEASON_MATCH
Setting.getTmdbSeasonMatchCache()
Setting.putTmdbSeasonMatchCache(...)
```

与 `tmdb_match_cache` 一样存入可清理应用缓存；清除应用缓存同时清除媒体和季度绑定。

## 8. 解析模型

### 8.1 新增 `TmdbSeasonResolver`

用结构化结果替代全局 `int`：

```java
public final class SeasonResolution {
    Status status;              // RESOLVED / MULTI_SLICE / FLAT / AMBIGUOUS
    Integer selectedSeason;
    List<Integer> availableSeasons;
    Map<String, Integer> scopeMappings;
    Source source;
    Confidence confidence;
    String reason;
}
```

输入包括：当前 `TmdbItem` 与 TV detail、`Vod`/当前 `Flag`、Intent 季度、人工绑定、标题和线路解析结果、TMDB 季度及各季集数。

### 8.2 自动解析顺序

1. **会话选择**：Intent 季度存在于当前 TMDB 作品时使用；
2. **人工绑定**：校验媒体、源键和季度后直接返回，自动规则不得覆盖；
3. **显式季度**：所有信号一致则使用；出现多个季度时进入多季映射；指向不存在季度时返回歧义；
4. **唯一季度**：过滤特别篇后只有一个普通季度时自动使用；仅有第 0 季时使用 0；
5. **集数唯一匹配**：仅当源集数与某一季集数唯一精确相等时使用；多个季集数相同时不猜；
6. **完整多季切分**：仅当 `canSliceBySeasonCounts()` 为真时返回 `MULTI_SLICE`；
7. **歧义**：不写入季度单集元数据，保留源集平铺并显示“季度待匹配”。

### 8.3 第 0 季语义

- `0`：特别篇，合法；
- `-1`：未知，只存在于解析过程；
- `null + MANUAL_FLAT`：用户明确要求保持平铺且不绑定季度；`null + MANUAL_MULTI_SLICE`：用户确认按提取集号主键关联到普通季。

## 9. 手动匹配交互

### 9.1 两个入口

1. **重新匹配 TMDB**：保留现有入口；选择 TV 候选且有多个季度时进入季度步骤；
2. **匹配季度**：当前已有 TV 作品时直接显示，不重复搜索作品；歧义时突出显示。

### 9.2 两步流程

#### 第一步：选择作品

- 保留现有搜索和候选；
- 搜索框接受 `TMDB:12345` / `tmdb:12345`；
- 候选显示媒体类型、年份、原名和 TMDB ID；
- TV 候选显示季度数；
- 电影选中后直接保存。

#### 第二步：选择季度

列表项：

- 自动判断；
- 保持原始集列表，不绑定季度；
- 第 0 季（若存在）；
- 第 N 季：季名、首播日期、集数、海报；
- 风险状态：源集数与该季集数一致、少于或超过；
- 可选预览：源前 3 集与 TMDB 前 3 集标题对照。

操作：确认并重新刮削、取消、清除已有绑定。

### 9.3 TV 与移动端要求

- TV 初始焦点落在当前绑定或推荐季度；
- 上下移动季度，左右移动操作按钮；
- 返回键在加载中仍可用；
- 保存后焦点回到原入口；
- 状态和警告不能只靠颜色表达；
- 移动端使用可滚动列表，不把关键操作放在长按中。

### 9.4 状态展示

- `TMDB 已匹配 · 第 2 季（手动）`
- `TMDB 已匹配 · 第 2 季（标题识别）`
- `TMDB 已匹配 · 多季映射`
- `TMDB 已匹配 · 季度待选择`
- `TMDB 已匹配 · 保持原始集列表`

## 10. 保存、刷新与清除

### 10.1 保存作品与季度

1. 写 `TmdbMatchCache`；
2. 写标题学习；
3. 写 `TmdbSeasonMatchCache`；
4. 清除当前 Adapter 内存解析；
5. 取消旧请求并递增 generation；
6. 重新 `load(item, vod)`；
7. resolver 优先读取人工绑定；
8. 加载指定季，只补充可验证单集；
9. 发送详情和分集刷新；
10. 恢复当前线路、选集和播放历史位置。

### 10.2 仅重选季度

不修改媒体身份和标题学习，只更新季度 Entry 并重跑分集元数据阶段；作品详情已缓存时不重复请求。

### 10.3 清除绑定

删除季度 Entry，重新自动解析；若仍歧义则保持平铺。不得删除媒体身份。

### 10.4 媒体重新匹配

TMDB ID 改变时删除旧季度绑定；新 TV 作品重新选择季度；新结果为电影时移除所有季度状态。

## 11. 分集元数据应用修改

### 11.1 删除盲目第一季回退

由 `SeasonResolution` 驱动：

- `RESOLVED`：加载指定季；
- `MULTI_SLICE`：按 scope/切片分别加载对应季；
- `FLAT`、`AMBIGUOUS`：不写季度单集元数据。

### 11.2 应用前校验

- 季度必须存在；
- 媒体必须是 TV；
- 人工绑定的 TMDB ID 必须匹配；
- 源集号在该季存在时才应用；
- 按位置回退只能在整条线路已确认属于该季时启用。

### 11.3 多线路处理

MVP：整个 Vod 的人工季度对所有线路生效；若线路出现相互冲突的显式季度，返回 `AMBIGUOUS`，不再用一个全局季度覆盖。

Phase 2：为 `Flag` 生成稳定 scope key，支持“线路 A -> 第 1 季、线路 B -> 第 2 季”，并按线路复用不同 season 详情。

## 12. 类与文件调整

### 12.1 新增

- `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCache.java`
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolver.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/TmdbSeasonMatchDialog.java`
- 对应 cache/resolver/dialog 测试。

### 12.2 修改

- `TmdbUIAdapter.java`
  - 用 `SeasonResolution` 替换单一季度决策；
  - 删除未知季度 `[1, 0]` 回退；
  - 增加应用/清除人工季度绑定；
  - 输出解析来源和状态。
- `EpisodeSeasonPolicy.java`
  - 保留纯函数；
  - 作为 resolver 底层安全规则；
  - 旧 candidates 方法弃用或只接受已解析结果。
- `Setting.java`、`AppCache.java`：增加季度缓存读写。
- `TmdbDetailActivity.java`、两端 `VideoActivity.java`、`TmdbHeaderView.java` 与对应资源：增加入口和状态。

建议三个 Activity 共用 `TmdbSeasonMatchDialog`，不要复制第四套对话框逻辑。现有媒体匹配对话框的全面统一可独立提交，避免本修复变成大重构。

### 12.3 TmdbService

现有接口足够：

- `detail()` 包含 seasons 摘要；
- `season()` 获取指定季；
- `episodes()` 解析单集。

MVP 不新增网络接口，可增加纯解析 helper：`List<TmdbSeasonSummary> seasons(JsonObject detail)`。

## 13. Provider ID 直达搜索

手动搜索建议支持：

```text
TMDB:12345
tmdb:12345
```

识别前缀后直接请求详情；若媒体类型未知，要求用户选择电影/电视剧或使用 TMDB 查找能力。ID 无效时显示明确错误，不能回退成标题“12345”搜索。该能力借鉴 Jellyfin Provider ID 和 tinyMediaManager Provider 前缀。

## 14. 错误处理与安全退化

| 场景 | 行为 |
|---|---|
| TMDB 未配置/鉴权失败 | 保持源详情与选集，提示配置问题 |
| 季度列表请求失败 | 不覆盖 Episode；保留有效旧绑定并允许重试 |
| 绑定季度已被 TMDB 删除 | 标记失效，保持平铺，提示重选 |
| 源集数大于所选季 | 只匹配存在集号，其余保留源名称并提示风险 |
| 多个季度集数都与源相等 | 不自动选择 |
| 第 0 季 | 作为合法季度显示 |
| 同一 Vod 多线路季度冲突 | MVP 保持平铺；Phase 2 提供线路级映射 |
| 用户选择保持原始列表 | 写入 `MANUAL_FLAT`，后续不自动猜季 |
| 媒体重新匹配为电影 | 删除季度绑定 |
| 页面销毁/请求竞态 | 沿用 generation 和任务取消，禁止旧结果回写 |

## 15. 日志与可观测性

建议日志：

```text
season_resolve status=RESOLVED source=MANUAL tmdb=123 season=2
season_resolve status=AMBIGUOUS reason=equal_episode_counts candidates=[1,2]
season_binding stale reason=tmdb_changed old=123 new=456
season_metadata skip reason=ambiguous
season_metadata apply season=2 sourceEpisodes=12 matched=12 skipped=0
```

不得记录完整播放 URL、鉴权参数或 TMDB Token。

## 16. 测试计划

### 16.1 Resolver

1. 手动绑定高于标题推断；
2. Intent 会话选择高于持久绑定；
3. `S02`、`第二季`、`Season 2` 解析为 2；
4. 特别篇解析为 0；
5. 未知多季不再返回第 1 季；
6. 单一普通季自动解析；
7. 多个季度集数相同返回歧义；
8. 唯一集数匹配返回高置信度；
9. 全季集数精确总和允许多季切分；
10. 多线路显式季度冲突返回歧义；
11. 绑定季度不存在时失效；
12. `MANUAL_FLAT` 阻止自动推断。

### 16.2 Cache

1. 同站点/同 Vod ID/不同源标题互不污染；
2. TMDB ID 变化失效；
3. 第 0 季可序列化；
4. `null` 只用于 `MANUAL_FLAT` 或 `MANUAL_MULTI_SLICE`；
5. 旧缓存不存在时兼容；
6. 源集数变化重新验证。

### 16.3 Adapter 与 UI

1. 歧义状态不请求 season 1；
2. 人工第 2 季只应用第 2 季标题；
3. 所选季缺集时不覆盖源名称；
4. 多线路冲突不共用单季；
5. generation 变化后旧请求不回写；
6. 电影和单季 TV 无回归；
7. 三个入口都能打开季度选择；
8. TV 焦点、返回键、加载态可用；
9. 保存后恢复原线路和选集；
10. 清除绑定后重新自动解析。

## 17. 分阶段实施

### Phase 1：单 Vod 手动绑定（推荐 MVP）

1. 新增季度缓存和 resolver；
2. 删除未知季度的第一季盲回退；
3. 现有手动 TMDB 匹配增加季度步骤；
4. 增加独立“匹配季度/清除绑定”；
5. `TmdbUIAdapter` 使用持久绑定；
6. 完成移动端、TV 端和单元测试。

验收重点：不再错刮第一季，用户可以可靠固定季度。

### Phase 2：线路级多季度映射

- 为 `Flag` 增加稳定 scope key；
- 每条线路可选择不同季度；
- 按线路复用多个 season 详情；
- 提供批量映射 UI；
- 与历史、换源和季度导航联动。

### Phase 3：高级编号体系

按真实反馈决定是否支持 Episode Groups、动漫绝对集、源集号偏移和逐集映射。没有足够案例前不实现，避免过度设计。

## 18. 迁移、回滚与备选方案

### 18.1 迁移与回滚

- 不迁移旧 `TmdbMatchCache`；新缓存为空时按新规则解析；
- 已有媒体身份继续命中；首次歧义时提示选择，不写错误绑定；
- Entry 带版本号；
- 旧版本会忽略新季度缓存；
- 不修改 Vod、Episode、History 持久结构，无数据库回滚。

### 18.2 备选方案

1. **只增强标题正则**：改动小，但不能解决全集、同名季和人工纠正，拒绝；
2. **直接扩展 `TmdbMatchCache.Entry`**：文件少，但耦合身份与季度生命周期，拒绝；
3. **按集数最接近自动选季**：会制造难察觉错误，只允许唯一精确匹配；
4. **一次实现逐集映射和 Episode Groups**：范围过大，延后；
5. **每次进入都询问季度**：TV 操作成本高，应持久化。

## 19. 验收标准

1. 未知多季度作品不会静默使用第 1 季单集元数据；
2. 用户可从详情页和两端播放页选择并保存季度；
3. 重进页面后人工绑定仍生效；
4. 重新匹配其他 TMDB 作品后旧绑定失效；
5. 第 0 季可以明确选择；
6. 歧义或网络失败时播放列表、URL、历史位置不受影响；
7. 单集标题只在季号和集号验证通过后写入；
8. 单季 TV 和电影无回归；
9. TV 遥控器可完整操作；
10. 新增规则有单元测试，关键 Activity 有回归测试。

## 20. 建议决策

建议批准 Phase 1：

1. 新建独立 `TmdbSeasonMatchCache`；
2. 用结构化 `SeasonResolution` 替代“未知就试第 1 季”；
3. TV 手动媒体匹配增加季度步骤；
4. 增加独立季度重选与清除入口；
5. 歧义时保持平铺，不写错误单集元数据；
6. Provider ID 直达搜索纳入同一阶段；
7. 线路级映射和 Episode Groups 延后。

## 21. 调研来源

### 当前项目

- `TmdbUIAdapter.java`、`TmdbMatcher.java`、`TmdbMatchPolicy.java`
- `EpisodeSeasonPolicy.java`、`SourceEpisodeSeasonCache.java`
- `TmdbMatchCache.java`、`TmdbService.java`
- `TmdbDetailActivity.java`、两端 `VideoActivity.java`
- `docs/tmdb-playable-episode-availability-design.md`

### Jellyfin

- <https://jellyfin.org/docs/general/server/metadata/identifiers/>
- <https://jellyfin.org/docs/general/server/media/shows/>
- <https://github.com/jellyfin/jellyfin-web/blob/376eed435d4bd1a7df91190bb9ba241ce2d687c4/src/components/itemidentifier/itemidentifier.js>
- <https://github.com/jellyfin/jellyfin-web/blob/376eed435d4bd1a7df91190bb9ba241ce2d687c4/src/components/itemidentifier/itemidentifier.template.html>
- <https://github.com/jellyfin/jellyfin/blob/fb763c47bfc88b1661f8dd1f3f7a4340d140380e/MediaBrowser.Providers/TV/SeasonMetadataService.cs>

### Sonarr

- <https://github.com/Sonarr/Sonarr/blob/818ac70df13c84f0ef41d18e6c45d095b6ea22ae/src/Sonarr.Api.V5/ManualImport/ManualImportResource.cs>
- <https://github.com/Sonarr/Sonarr/blob/818ac70df13c84f0ef41d18e6c45d095b6ea22ae/src/Sonarr.Api.V5/ManualImport/ManualImportReprocessResource.cs>
- <https://github.com/Sonarr/Sonarr/blob/818ac70df13c84f0ef41d18e6c45d095b6ea22ae/src/NzbDrone.Core/MediaFiles/EpisodeImport/Manual/ManualImportService.cs>
- <https://github.com/Sonarr/Sonarr/blob/818ac70df13c84f0ef41d18e6c45d095b6ea22ae/src/NzbDrone.Core/DataAugmentation/Scene/SceneMappingService.cs>
- <https://github.com/Sonarr/Sonarr/blob/818ac70df13c84f0ef41d18e6c45d095b6ea22ae/src/NzbDrone.Core/Parser/ParsingService.cs>

### Kodi

- <https://kodi.wiki/view/Naming_video_files/TV_shows>
- <https://kodi.wiki/view/NFO_files/TV_shows>

### tinyMediaManager

- <https://www.tinymediamanager.org/blog/search-with-id/>
- <https://www.tinymediamanager.org/blog/tv-show-management/>
- <https://www.tinymediamanager.org/docs/scrapers>
- <https://www.tinymediamanager.org/features/>

### TMDB API

- <https://developer.themoviedb.org/reference/tv-series-season-details>
- <https://developer.themoviedb.org/reference/tv-series-episode-groups>
- <https://developer.themoviedb.org/reference/tv-episode-group-details>

---

# AI 刮削、集数识别与跨季映射扩展设计（2026-08-13）

> 本节为现有“TMDB 多季度识别与手动季度绑定”设计的增量方案。AI 不替代规则或人工绑定，只在用户主动点击时提供可校验的识别建议。

## 22. 新增目标

1. 修复类似 `一人之下6_01` 被识别为第 `601` 集的问题；`6_01` 应理解为第 6 季第 1 集候选。
2. 识别 `601-620`、`621-27` 等区间文本，不把区间误当作单集。
3. 支持源站平铺 `1-50` 与 TMDB 多季之间的安全偏移映射，例如源第 11 集映射为 `S02E01`。
4. 当 TMDB 与源站、豆瓣等渠道的季数/集数结构不一致时，提供可插拔证据源与 AI 辅助判断。
5. AI 默认不进入自动刮削主链路，由用户点击“AI 识别集数/分季”后异步执行。
6. 所有 AI 结果先预览、校验，再由用户应用；失败时保持原始播放列表和 URL 不变。

## 23. 分层事实与优先级

- 源站 `Episode` 列表：可播放事实，不因刮削结果而新增或删除。
- 人工绑定/人工映射：最高优先级，可清除、可撤销。
- 明确季集标记：`S02E03`、`第 2 季第 3 集`、源线路季名。
- TMDB 季度和 Episode Groups：标准元数据证据。
- 豆瓣及其他渠道：辅助证据，通过统一 Provider 接口接入。
- 连续集数、总集数、年份等结构信号：只在无缺口、无重复时自动使用。
- AI：推理建议，不得覆盖人工结果；低置信度或证据冲突时只展示候选。

## 24. 集数解析模型

本地规则应返回结构化候选，而不是只返回一个裸整数：

- `kind`：`SINGLE`、`SEASON_EPISODE`、`RANGE`、`UNKNOWN`；
- `seasonNumber`、`episodeNumber`；
- `rangeStart`、`rangeEnd`；
- `matchedText`、`confidence`、`reason`。

解析优先级：

1. `SxxEyy`、`第 N 季第 M 集`；
2. `第 N 集/话/回/期`、`EPyy/Eyy`；
3. 作品名后的 `季_集`、`季-集`、`季.集`，例如 `一人之下6_01`；
4. 独立纯数字；
5. 兼容旧格式的弱规则。

以下内容必须先排除：`00/000`、年份、日期、分辨率、文件大小、编码、版本号。纯区间不得进入单集匹配。

## 25. 平铺跨季映射

新增纯策略 `FlatEpisodeSeasonMapper`（首期可落在 `EpisodeSeasonPolicy` 内部），输入：

- 按源顺序排列的源集号；
- TMDB 季顺序；
- 每季集数。

无人确认的自动推断仍必须同时满足：

1. 参与连续切分的 TMDB 普通季（季号大于 0）每季集数均为正数；第 0 季特别篇不参与平铺偏移累计，但仍可显式选择；
2. 各季总集数与源列表数量完全一致；
3. 源集号从 1 开始、严格连续、无重复、无缺口；
4. 每个目标季集号都在 TMDB 范围内；
5. 映射覆盖率为 100%。

映射示例：`S01=10,S02=12,S03=28` 时，源 1 -> `S01E01`，源 10 -> `S01E10`，源 11 -> `S02E01`，源 22 -> `S02E12`，源 23 -> `S03E01`。

若源列表缺集、重复、从非 1 起始或总数不一致，保守的连续位置映射必须拒绝。自动判断随后会检查提取集号是否实际触及至少两个普通 TMDB 季；满足且没有明确季号冲突时，才回退到按集号主键映射。只触及一个季时仍保持歧义，避免把每季从 1 开始的列表误判为全局集号。

自动判断的安全回退、用户明确选择“按集号主键自动分季”或确认 AI 的 `FLAT_BY_COUNTS` 后，均改用提取集号作为主键：

1. 每个源条目独立按累计普通季集数映射，不读取其列表位置；
2. 缺号不会让后续集偏移，重复号可关联到同一个 TMDB 季集；
3. `0`、未知集号和超出 TMDB 总集数的条目保持原样并跳过；
4. 至少存在一个可映射集号才允许保存绑定；可用季度只包含实际命中的季度。

## 26. AI 手动分析

复用 `AiConfig` 与 `AiCompletionClient`，新增集数/分季分析服务。请求仅包含：作品标题、线路名、脱敏后的集名、规则解析集号、TMDB 季号及集数、可选外部证据摘要；不上传 Cookie、Token 或完整播放 URL。

AI 只允许输出严格 JSON：

- `mode`：`SINGLE_SEASON`、`FLAT_BY_COUNTS`、`KEEP_ORIGINAL`、`EXPLICIT_MAPPING`；
- `seasonNumber`：单季模式的目标季；
- `confidence`、`summary`、`warnings`；
- `mappings`：源索引到目标季集的映射。

本地校验包括：季集范围、重复目标、映射覆盖率、单调性、源索引范围和 TMDB 集数上限。非法输出不可应用。

## 27. 手动入口与交互

现有“匹配 TMDB 季度”对话框增加“AI 识别集数/分季”：

1. 用户点击后异步分析当前线路；
2. 显示建议模式、置信度、摘要和警告；
3. `SINGLE_SEASON` 可确认后保存现有手动季绑定；
4. `FLAT_BY_COUNTS` 仅在本地平铺映射校验通过后应用；
5. `KEEP_ORIGINAL` 可确认后保存平铺模式；
6. `EXPLICIT_MAPPING` 首期展示建议，后续由版本化映射快照承载；
7. 返回键或取消不保存，网络失败不影响播放。

## 28. 第一阶段实施与验收

第一阶段按以下顺序落地：

1. 修复 `6_01 -> 601` 和区间误识别并补单元测试；
2. 实现并接入安全的平铺跨季映射；
3. 修复映射后“源 11 != TMDB 1”导致元数据被拒绝的问题，同时继续拒绝 `00.mp4` 的位置回退；
4. 增加手动 AI 分析服务与季度对话框入口；
5. 对 AI JSON 做严格本地校验；
6. 保持自动流程无 AI、无网络阻塞。

验收场景：

- `一人之下6_01`、`一人之下6_02` 分别识别为第 1、2 集，不再生成 `601-620` 分页；
- `601-620`、`621-27` 不作为单集集号；
- 源 `1-50` 能按 TMDB 季集数映射，源 11 正确使用 `S02E01` 元数据；
- 源缺第 12 集或含重复集时，手动按集数分季仍按各自集号正确关联；`00.mp4` 与超范围集号跳过且不按位置补位；
- AI 未配置、超时、返回非法 JSON 时继续使用规则与手动选季；
- 应用建议不改变源播放 URL、源列表数量和当前播放事实。
## 29. 第一阶段实施状态（2026-08-13）

本轮已经落地并通过构建/单元测试的内容：

- 集数解析不再把作品名中的季号与集号拼接：`一人之下6_01`、`一人之下6_20.mp4` 分别解析为第 1、20 集。
- 纯数字区间（如 `601-620`、`621-27`）不再作为单集集号进入分页和卡片标题。
- 无人确认的自动跨季推断先接受严格 `1..N` 和完整覆盖；无法确认时，若提取集号实际触及至少两个普通季且没有明确季号冲突，则回退到主键映射。手动“按集号主键自动分季”及 AI `FLAT_BY_COUNTS` 始终以提取集号为主键，允许缺号、重复和乱序，未知/超范围条目跳过。第 0 季特别篇不参与普通季偏移累计，映射后的季/集元数据带显式安全标记，并在列表合并时保留。
- TMDB 详情页、Mobile 播放页和 Leanback 播放页的季选择对话框均已增加手动触发的 AI 分析入口。
- AI 请求只发送标题、线路名、源索引、规则解析集号、截断后的集名以及 TMDB 季号/集数，不发送播放 URL、Cookie 或 Token。
- AI 输出采用严格 JSON 和本地二次校验；单季、按季集数切分、保持原列表可确认后应用，显式逐集映射在第一阶段仅预览，不落盘。
- AI 未配置、超时、网络失败、JSON 非法、季集越界、映射不完整/重复/非单调时均保持当前源列表和播放事实不变。

第一阶段有意保留的后续项：

1. 豆瓣、Bangumi、AniDB 等外部证据 Provider 与证据摘要/冲突裁决；
2. `EXPLICIT_MAPPING` 的版本化快照、持久化、原子应用和一键撤销；
3. 低置信度阈值配置、映射差异表及逐集人工编辑界面；
4. 按当前线路而非整个 `Vod` 保存复杂映射规则。
