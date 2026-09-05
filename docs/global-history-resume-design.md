# 全局历史续播设计文档

> **状态：MVP 已实现，emulator-5558 当前构建交互验收通过（2026-07-30）**<br>
> **日期：2026-07-29；最近复核：2026-07-30**<br>
> **范围：个性设置、历史页、TV 首页最近观看、跨点播配置续播、自动换源、全局搜索、TMDB 优先匹配**<br>
> **关联文档：** `docs/unified-media-identity-cross-site-resume.md`、`docs/search-group-and-precise-filter-design.md`

## 实施状态（2026-07-30）

已完成：

- 三态个性设置及中、英、繁中资源；
- 跨 `cid` 历史展示、排序、TMDB 身份聚合、单删和全局清空；全局清空仅移除历史记录关联的轨道偏好，不影响直播等其他轨道设置；
- mobile / leanback 历史页及 TV 首页统一点击协调；
- 自动搜索当前配置可换源站点，并与全局手动搜索一样使用完整搜索结果（`quick=false`）；按 TMDB 缓存、规范标题、年份和目标集数选择播放源；只有 TMDB 一致或规范标题精确一致的候选可自动播放，模糊标题留给手动选择；
- 历史记录持久化播放流程已确认的 TMDB 标准季号和集号：优先使用已绑定的 `TmdbEpisode`，独立 TMDB 详情页也可使用当前季/集列表的确定性映射；续播匹配优先使用该标准位置，没有标准位置的旧历史才回退 `Episode.getNumber()` 所复用的现有集数提取逻辑，不新增标题解析规则；
- 自动匹配期间显示不可自动消失的白底加载弹层；成功跳转或搜索终止时才关闭，失败降级弹层同样使用项目统一白底样式；
- 手动全局搜索续播上下文在两端完整透传，包括搜索结果中的文件夹/分类层级；
- 手动搜索固定发起时的目标 `cid`，搜索、文件夹、分类、失败回退和最终选择均会拒绝已切换配置的过期结果；
- 目标源线路、集数重新映射，并继承原历史进度、倍速、片头片尾等状态；
- 搜索竞态、历史删除和点播配置切换保护；异步搜索、候选详情和站源调用在取消时保留线程中断状态；
- 偏好备份、双端源码测试、匹配策略单测和构建验证。

首版有意简化或延后：

- 未命中 `TmdbMatchCache` 时不额外批量请求 TMDB；TMDB 已缓存身份仍具有最高优先级；
- 继续复用播放器现有绝对毫秒进度恢复，尚未加入跨片源时长差异的比例换算；
- 外部历史来源统一显示“其他接口”，未在卡片绑定阶段查询配置名称；
- WebHome、Android TV Browse、观影报告和同步范围不因该设置扩大；AI 与个性推荐继续沿用原有 `History.getAll()` 全量历史语义；
- 自动搜索采用结果代次保护并向未完成任务发出线程中断；站源实现是否能立即终止底层网络调用仍取决于其自身实现。

验证结果：

- `assembleMobileArm64_v8aDebug` 与 `assembleLeanbackArm64_v8aDebug` 通过；
- Mobile 完整单测 1349 项、Leanback 完整单测 1356 项全部通过，均为 0 失败、0 错误、0 跳过；
- emulator-5558 使用保留数据覆盖安装后冷启动正常；Room `user_version = 40`，`tmdbSeasonNumber`、`tmdbEpisodeNumber` 两列存在，86 条既有历史保持不变，启动日志无 Room、SQLite 或迁移异常；
- emulator-5558 三态设置持久化验证通过：`2 → 0 → 2` 后 UI 与偏好值一致，并恢复用户原值；
- emulator-5558 全局历史验证通过：4 个不同 `cid` 的记录可同时展示，外部记录显示“其他接口”；点击“佳偶天成 / 第1集”进入手动全局搜索，选择同名新源后进入播放器并写入标准 `S1E1`；本次产生的唯一验收历史已精确清理，总数恢复为 86；
- 2026-07-29 的自动模式验收同样通过：加载弹层在 30 秒搜索期间持续显示，“凡人修仙传”命中新源并准确选择第 19 集，无结果时切换为白底搜索降级弹层，临时数据已清理。
- 2026-07-30 聚合进度策略复核通过：mobile / leanback 在「历史记录跨源聚合」生效时，普通详情显式选择同一 TMDB 标准季集的其他来源会共享进度；聚合关闭且不是历史续播时仍使用原始剧集身份。相关双端定向单测和编译均通过。

## 1. 结论

新增个性设置 **「全局历史续播」**，采用三态枚举：

| 值 | 设置页文案 | 行为 |
|---:|---|---|
| `0` | 关闭 | 默认值。维持当前历史范围和点击行为 |
| `1` | 自动匹配播放源 | 展示全局历史；点击当前配置无法直接打开的历史时，在当前点播配置中自动换源并续播 |
| `2` | 搜索后选择播放源 | 展示全局历史；点击当前配置无法直接打开的历史时，进入全局搜索，由用户选择播放源后续播 |

本文将“全局历史”明确为：

> 汇总所有仍保存在数据库中的点播配置历史，即跨 `History.cid` 查询，而不只是当前 `VodConfig.getCid()`。

这样定义的原因是：当前代码已经通过 `history_aggregation_by_tmdb` 支持同一 `cid` 内的跨站源、跨线路历史聚合和续播；若新功能仍只覆盖当前 `cid`，会与现有设置高度重复。

核心决策：

1. **新设置与现有「历史记录跨源聚合」相互独立。**
   - 新设置决定历史查询范围，以及外部历史点击后的入口策略。
   - 现有设置决定是否使用 TMDB 身份聚合同一作品；开启后，同一作品、同一标准季集在历史续播和普通显式跨源选集时共享播放进度。
2. **关闭为真正默认值。**不存在 `global_history_mode` 时返回 `0`。
3. **TMDB 只在相关设置生效时优先使用。**建议以 `Setting.isHistoryAggregationEffective()` 作为统一判定，不因仅配置了 TMDB Key 就静默启用。
4. **自动模式只接受高置信匹配。**不确定时不得自动播放疑似同名作品；自动失败后提供“打开全局搜索”降级入口。
5. **搜索模式保留用户选择权。**搜索结果可以按 TMDB 匹配度排序，但不能替用户直接选中播放源。
6. **播放流程已确认的 TMDB 剧集位置必须持久化。**`History` 增加 `tmdbSeasonNumber`、`tmdbEpisodeNumber`，Room 数据库升级到 40；位置可来自已绑定的 `TmdbEpisode`，也可来自独立 TMDB 详情页对当前剧集列表的确定性映射。旧记录以 `(0, 0)` 表示未知并继续使用现有集数提取逻辑。标准集号大于 `0` 时，季号 `0` 表示特别篇，负数才表示季未知。
7. **不修改通用 `History.get()` 的语义。**新增专用历史 UI 查询层，避免观影报告、AI 推荐、同步和 WebHome 被意外扩展到全局范围。

## 2. 名词与范围

### 2.1 当前历史

满足以下条件的历史可直接按当前流程打开：

- `history.cid == VodConfig.getCid()`；
- `history.siteKey` 仍存在于当前 `VodConfig`；
- 对应条目不是已经失效的合成入口。

### 2.2 外部历史

满足任一条件即视为外部历史：

- `history.cid != VodConfig.getCid()`；
- `history.cid` 相同，但原站源已从当前配置移除；
- 聚合卡片的最新播放记录来自其他 `cid`，当前配置没有可直接打开的成员。

“外部历史”比“其他线路”更精确，涵盖跨接口、跨配置、站源移除和历史来源失效等情况。

### 2.3 播放源

本设计中的播放源分两层：

- **站源：** `Site`，如不同影视站点。
- **线路：** `Flag`，同一 `Vod` 下的不同播放线路。

自动换源先选择 `Site + Vod`，再根据历史集数在目标 `Vod.flags` 中选择包含目标集的 `Flag + Episode`。

## 3. 实施前实现核查（历史背景）

### 3.1 已有能力

| 能力 | 当前实现 |
|---|---|
| 历史主键 | `siteKey + @@@ + vodId + @@@ + cid` |
| 当前配置历史 | `History.get()` → `HistoryDao.find(cid)` |
| 全量历史查询 | `HistoryDao.findAll()` 已存在，但主要用于备份 |
| TMDB 身份 | `History.tmdbId`、`History.mediaType` |
| 当前配置内聚合 | `History.deduplicateByTmdbId()` |
| 当前配置内跨源续播 | `History.findPlaybackByTmdb()`、`History.findPlaybackCandidate()` |
| 集数容错 | `Episode.matchesPlayback()`、集号回退 |
| 历史入口 | mobile / leanback `HistoryActivity` 调用 `VideoActivity.startFromHistory()` |
| 自动换站源 | 两端 `VideoActivity` 的 quick search / `nextSite()`；`TmdbDetailActivity.changeSource()` |
| 全局搜索 | mobile `SearchActivity → SearchFragment → CollectFragment`；TV `SearchActivity → CollectActivity` |
| 详情预取缓存 | `VodDetailCache` |

### 3.2 当前限制

1. `History.get()` 只读取当前 `cid`，无法形成真正的全局历史页。
2. `History.findPlayback()` 也只在当前 `cid` 中查找。
3. 外部历史的原 `siteKey/vodId` 在当前配置中通常不可直接使用。
4. 当前全局搜索入口只传关键词、站点和图片，没有携带历史续播上下文。
5. `TmdbDetailActivity.changeSource()` 和两端 `VideoActivity` 的换源逻辑都绑定在 Activity 内，不能直接被历史页调用。
6. `History.getSiteName()` 基于当前 `VodConfig` 解析站源名，无法可靠显示其他 `cid` 的来源。
7. `History.delete()` 在 TMDB 聚合分支中使用当前 `VodConfig.getCid()`，不能直接用于删除外部历史或跨 `cid` 聚合卡片。
8. `HistoryAdapter.clear()` 和 TV 首页清空逻辑目前只删除当前 `cid`。
9. `History.get()` 被观影报告、AI 推荐、同步、WebHome、Android TV Browse 等模块复用，不能直接改成全量查询。
10. 当前 TMDB 聚合只按 `tmdbId` 分组；全局聚合应使用 `(mediaType, tmdbId)`，避免电影和剧集命名空间中相同数字 ID 被误合并。

## 4. 目标

1. 用户可在个性设置中选择关闭、自动匹配、搜索后选择三种模式。
2. 模式开启后，原生历史页展示所有点播配置的历史。
3. TV 首页“最近观看”与原生历史页使用相同的展示范围和点击策略。
4. 点击当前配置内可直接打开的历史时，保持现有快速直达体验。
5. 点击外部历史时：
   - 自动模式自动搜索并选择高置信播放源；
   - 搜索模式进入全局搜索，由用户选择播放源。
6. 目标源确定后，恢复同一作品、同一季集、同一播放进度。
7. 继续继承倍速、片头片尾、画面比例等可跨源复用的播放状态。
8. 当 TMDB 聚合设置生效且历史具有 TMDB 身份时，优先使用 `(mediaType, tmdbId)` 匹配。
9. 自动匹配失败、目标源缺集或身份冲突时，不覆盖原历史、不错误播放。
10. mobile 与 leanback 行为一致。

## 5. 非目标

首版不做：

1. 不自动切换当前点播配置，不加载其他 `Config` 的站源配置来播放。
2. 不做账号级云端统一历史。
3. 不重构全部 `History.get()` 调用方。
4. 不让 AI 推荐、观影报告、历史同步自动改为跨 `cid`。
5. 不为旧历史离线猜测或批量回填季号、集号；仅在播放流程已绑定 `TmdbEpisode`，或独立 TMDB 详情页已根据当前季/集列表确定标准位置时保存。
6. 不保证标题模糊匹配一定能自动续播；低置信场景必须交给用户选择。
7. 不在列表加载阶段批量请求 TMDB 或站源网络接口。
8. 不把 WebHome、Android TV Browse、媒体库外部入口纳入首版点击链路。

## 6. 设置与文案

### 6.1 推荐文案

设置名称：

```text
全局历史续播
```

选项：

```text
关闭
自动匹配播放源
搜索后选择播放源
```

简体中文完整说明：

- 关闭：仅使用当前点播配置的历史。
- 自动匹配播放源：显示全部点播历史，外部历史会在当前配置中自动匹配播放源并续播。
- 搜索后选择播放源：显示全部点播历史，外部历史会打开全局搜索，选择播放源后续播。

英文建议：

```text
Global history resume
Off
Auto-match source
Choose source from search
```

繁体中文建议：

```text
全域歷史續播
關閉
自動配對播放源
搜尋後選擇播放源
```

### 6.2 设置存储

新增偏好键：

```text
global_history_mode
```

常量：

```java
GLOBAL_HISTORY_OFF = 0
GLOBAL_HISTORY_AUTO = 1
GLOBAL_HISTORY_SEARCH = 2
```

接口：

```java
public static int getGlobalHistoryMode();
public static void putGlobalHistoryMode(int mode);
public static boolean isGlobalHistoryEnabled();
```

规则：

- 不存在键时返回 `OFF`。
- 非法值统一夹取为 `OFF`。
- 设置变化后发送 `RefreshEvent.history()`。
- 新设置始终显示，不依赖 TMDB 是否配置。
- mobile 与 leanback 均按现有枚举设置习惯循环切换：`关闭 → 自动 → 搜索 → 关闭`。

### 6.3 与现有 TMDB 聚合设置的关系

| 全局历史续播 | 历史记录跨源聚合 | 列表范围 | 身份策略 |
|---|---|---|---|
| 关闭 | 关闭 | 当前 `cid` | 当前原始记录 |
| 关闭 | 开启 | 当前 `cid` | 当前已有 TMDB 聚合 |
| 自动/搜索 | 关闭 | 全部 `cid` | 不跨记录做 TMDB 聚合；点击时使用保守标题/年份匹配 |
| 自动/搜索 | 开启 | 全部 `cid` | 按 `(mediaType, tmdbId)` 聚合，点击时优先 TMDB 匹配 |

「历史记录跨源聚合」开启且 `Setting.isHistoryAggregationEffective()` 生效时，播放器不仅聚合历史卡片，也会在同一作品、同一标准季集之间共享进度。标准季集身份必须包含季号和集号；已知目标季度时，其他季度的同集号必须硬拒绝，季号未知的旧记录仅允许在同一来源 `key` 内兼容恢复，不能跨来源猜测。该规则同时适用于历史入口、用户从普通详情页显式选择的其他来源以及单集位置缓存；关闭聚合时，只有明确的历史续播或跨源续播请求使用宽松集身份，普通显式选集继续按原始剧集身份判断。

不建议让新设置隐式打开旧设置，否则用户无法单独控制“展示全局历史”和“按 TMDB 合并同剧记录”。

## 7. 用户交互

### 7.1 历史列表

模式关闭：

- 保持现有列表和排序。
- 当前 TMDB 聚合开关继续按现有逻辑生效。

模式开启：

- 读取全部 `History`。
- 按 `createTime DESC` 排序。
- TMDB 聚合生效时，同一 `(mediaType, tmdbId)` 只显示一张卡。
- 聚合卡使用最近一次播放记录作为续播状态来源，而不是取最大播放进度；用户主动回看前一集时，最新行为应当优先。

来源文案建议复用卡片现有站源文本：

| 场景 | 来源文本 |
|---|---|
| 当前配置可直接打开 | 当前站源显示名 |
| 外部单条历史 | `其他接口 · {配置名}`；配置名为空时显示 `其他接口` |
| 跨配置 TMDB 聚合卡 | `全局 · {N} 个来源` |

不得直接展示配置 URL，避免在普通历史卡片暴露私有接口地址。

### 7.2 点击判定

```text
点击历史
  │
  ├─ 设置关闭 ───────────────→ 现有 startFromHistory
  │
  ├─ 当前记录可直接打开 ─────→ 现有/增强后的历史续播
  │
  ├─ 自动匹配模式 ───────────→ 自动换源解析
  │                              ├─ 成功：目标源续播
  │                              └─ 失败：提示并可打开全局搜索
  │
  └─ 搜索后选择模式 ─────────→ 打开全局搜索
                                 ├─ 用户选择：解析目标集并续播
                                 └─ 取消：返回历史页，不修改记录
```

### 7.3 自动匹配模式

显示持续加载弹层：

```text
正在匹配可续播播放源…
```

加载状态不得使用会自动超时消失的 Toast；它应一直显示到成功启动详情/播放页，或匹配流程结束并切换到失败弹层。加载弹层与失败弹层统一使用项目白底样式。

成功：

```text
已匹配「{站源名}」，正在续播第 {N} 集
```

失败时不直接跳转到错误播放源，弹出或提示：

```text
未找到可可靠续播的播放源
```

操作：

- `打开全局搜索`
- `取消`

### 7.4 搜索后选择模式

- 直接进入现有全局搜索结果页，默认关键词取统一标题或历史片名。
- 搜索页顶部可显示轻量上下文：`继续观看：{片名} · 第 N 集 · 进度 xx:xx`。
- 用户点击结果后，先加载目标详情并解析目标集。
- 解析成功后再离开搜索页并启动播放。
- 目标源没有对应集时，停留在搜索页并提示：

```text
该播放源未找到第 {N} 集，请选择其他来源
```

- 若 TMDB 明确不一致，应提示“可能不是同一作品”；手动模式允许用户二次确认，自动模式则直接拒绝该候选。

## 8. 历史展示模型

不建议让 Adapter 直接处理裸 `History` 的全局聚合、来源显示和删除语义。新增只用于 UI 的展示模型：

```java
public final class GlobalHistoryEntry {
    private final History resumeHistory;
    private final List<HistoryRef> members;
    private final String identityKey;
    private final String sourceLabel;
    private final boolean aggregated;
}
```

其中：

```java
record HistoryRef(int cid, String key) {}
```

### 8.1 代表记录

`resumeHistory` 取分组内 `createTime` 最新的记录，提供：

- 当前季集；
- 播放进度；
- 倍速；
- 片头片尾；
- 图片和展示标题。

### 8.2 聚合键

TMDB 聚合仅在 `Setting.isHistoryAggregationEffective()` 为真时启用：

```text
tmdb:{mediaType}:{tmdbId}
```

要求：

- `tmdbId > 0`；
- `mediaType` 为明确的 `movie` 或 `tv`；
- 空 `mediaType` 的旧记录不在全局范围内仅凭数字 ID 合并，避免电影/剧集 ID 冲突。

无可靠 TMDB 身份时：

- 首版不按“仅标题”跨 `cid` 合并；
- 每条历史独立展示；
- 点击时才通过标题、年份等信息搜索当前配置。

这是有意的保守策略：历史页出现少量重复卡片，比把同名不同年份作品错误合并更安全。

## 9. 续播请求模型

新增轻量请求对象，不修改 Room `History` 实体的 Parcelable 能力：

```java
public final class HistoryResumeRequest {
    int sourceCid;
    String sourceKey;
    int targetCid;
    String title;
    String year;
    String mediaType;
    int tmdbId;
    String episodeName;
    int seasonNumber;
    int episodeNumber;
    long position;
    long duration;
}
```

实际启动时至少通过 Intent 保存：

- `sourceCid`
- `sourceKey`
- `targetCid`

其余字段可从数据库重新读取并重新推导，确保进程重建后仍能恢复。可同时保留小型快照作为找不到原记录时的兜底，但不得传整个站源配置或播放地址列表。

`targetCid` 用于检测搜索期间用户切换点播配置的竞态：

- 若选择播放源时当前 `VodConfig.getCid()` 已变化，取消本次解析并提示重新操作。

## 10. TMDB 与标题匹配策略

### 10.1 是否启用 TMDB 优先

统一判定：

```java
boolean useTmdb = Setting.isHistoryAggregationEffective()
        && request.tmdbId > 0
        && !request.mediaType.isEmpty();
```

同时遵守目标站源的 `TmdbSitePolicy`。

不满足条件时：

- 不为本功能单独发起 TMDB 请求；
- 使用本地标题、年份、媒体类型和集数匹配。

### 10.2 TMDB 身份

必须同时比较：

```text
mediaType + tmdbId
```

规则：

- 两端都有 TMDB 身份且相同：最高置信。
- 两端都有 TMDB 身份但不同：自动模式硬拒绝。
- 历史有 TMDB、候选无缓存：先看 `TmdbMatchCache`；必要时仅对少量高排名候选调用 `TmdbMatcher`。
- `Setting.isHistoryAggregationEffective()` 为真且标准季集一致时，普通详情页的显式跨源选集也沿用聚合历史进度；不要求入口必须来自历史页。
- 聚合关闭且不是历史续播请求时，不使用启动参数临时补充的 TMDB 身份扩大普通选集匹配范围。
- 列表展示阶段不做 TMDB 网络请求。

### 10.3 搜索关键词

优先级：

1. `MediaTitleResolver` 解析出的规范标题；
2. TMDB 缓存标题或别名；
3. `History.vodName`；
4. 去除季、集、清晰度和发布组噪声后的标题。

最多保留 3 个不重复关键词。自动模式按顺序尝试；搜索模式默认展示第一个关键词，零结果时允许切换后续别名。

### 10.4 自动候选等级

不建议仅依赖一个难以解释的总分。候选划分为以下置信等级：

| 等级 | 条件 | 自动播放 |
|---|---|---|
| `TMDB_CONFIRMED` | TMDB 身份一致，并找到目标集 | 是 |
| `TITLE_YEAR_CONFIRMED` | 规范标题完全一致、年份一致，并找到目标集 | 是 |
| `UNIQUE_TITLE_CONFIRMED` | 规范标题完全一致、无冲突候选，并找到目标集 | 是 |
| `AMBIGUOUS` | 包含匹配、年份缺失、多候选同分 | 否 |
| `REJECTED` | TMDB 冲突、年份明确冲突、目标集缺失 | 否 |

同等级候选排序：

1. 目标集匹配质量；
2. `SiteHealthStore` 健康顺序；
3. 站源原始顺序；
4. 搜索结果顺序。

## 11. 自动换源解析

### 11.1 共享换源服务

当前换源逻辑分散在：

- mobile / leanback `VideoActivity`；
- `TmdbDetailActivity.changeSource()`。

建议提取纯业务服务：

```java
VodSourceResolver.resolve(HistoryResumeRequest request, List<Site> sites)
```

返回：

```java
record ResolvedHistoryTarget(
        Site site,
        Vod vod,
        Flag flag,
        Episode episode,
        MatchConfidence confidence,
        String vodDetailCacheKey) {}
```

服务复用现有规则：

- 只搜索 `site.isSearchable()` 的站源；
- 自动模式额外要求 `site.isChangeable()`；
- 使用 `SiteHealthStore.sortSites()`；
- 使用现有搜索线程数和 `Constant.TIMEOUT_SEARCH`；
- 支持取消；
- 记录站源搜索健康度。

### 11.2 两阶段解析

#### 阶段 A：搜索结果预筛

- 并发搜索当前配置中的可用站源；
- 每站最多保留 2 个高相关结果；
- 全局最多保留 12 个候选；
- 先按标题、年份、TMDB 缓存进行低成本筛选。

#### 阶段 B：详情与集数验证

- 只对阶段 A 的高排名候选加载详情；
- 从 `Vod.flags` 中查找目标 `Flag + Episode`；
- 找不到目标集的候选不能进入自动成功结果；
- 已加载的详情放入 `VodDetailCache`，启动播放器时避免重复请求。

### 11.3 自动模式的短路

若 TMDB 聚合卡片中已经存在当前 `cid` 的成员：

- 自动模式可优先把该成员作为目标，不再进行全站搜索；
- 仍需用最新全局记录的集数和进度覆盖该成员的旧进度；
- 若该成员详情已失效或缺少目标集，再继续执行全站搜索。

搜索后选择模式不做该短路；外部历史仍进入搜索页，遵守“由用户选择播放源”的设置语义。

## 12. 集数与线路匹配

目标集匹配复用 `HistorySourceResolver.findEpisode()`、`Flag.find()` 与 `Episode.matchesNumber()`；不再增加一套标题解析器。

匹配优先级：

1. 双方都有已确认的 TMDB 标准位置时，优先使用标准集号；位置可以来自已绑定的 `TmdbEpisode` 或播放入口转发的确定性季集映射。双方季号均已知时同时约束季号，其中 `0` 是合法的特别篇季号，负数才表示未知；
2. 候选尚未绑定 TMDB 时，同一 `siteKey + vodId` 的精确 `episodeUrl` 优先于候选标题提取；
3. `Episode.matchesPlayback()`；
4. 仅一方或双方缺少标准集号时，回退目标 `Episode.getNumber()` 和历史标题的现有 `Util.getEpisodeNumber()` 提取结果；
5. 电影或单集内容使用默认第一线路/第一集。

线路选择：

1. 目标历史成员仍存在时，优先其 `vodFlag`；
2. 否则选择第一个包含目标集的 `Flag`；
3. 多线路同时命中时保持源站顺序，不引入新的线路质量评分。

安全规则：

- 明确存在季号冲突时拒绝。
- 目标集号大于目标源最大集数时拒绝。
- 仅标题相似但无法确定集数时，自动模式拒绝；手动模式提示用户重新选择或从头播放。
- 不能把旧源的 `vodFlag` 和 `episodeUrl` 原样写入新源历史，必须替换为目标源解析结果。

## 13. 播放状态迁移

### 13.1 可迁移字段

从原历史继承：

- `position`
- `duration`
- 用户显式倍速及 `speedOverride`
- `opening`
- `ending`
- `scale`
- `revSort`
- `revPlay`
- `tmdbId`
- `mediaType`
- 可复用的展示元数据

### 13.2 必须替换的字段

使用目标源数据：

- `key`
- `cid`
- `vodFlag`
- `vodRemarks`
- `episodeUrl`
- `vodName`
- `vodPic`
- `wallPic`

不建议直接使用 `History.copy()` 后只改 key；应新增明确的播放状态快照或 `applyResumeState()`，避免把旧源专属字段带入新源。

### 13.3 进度换算

仅在确认是同一电影或同一集时应用进度。`Setting.isHistoryAggregationEffective()` 生效后，同一 `(mediaType, tmdbId)` 且标准季集一致即视为可共享进度，即使用户是从普通详情页显式选择另一来源；聚合关闭时，普通显式选集不因临时转发的 TMDB 身份而强制继承旧进度。

目标时长可用后：

1. 源、目标时长差异不超过 `max(10 分钟, 20%)`：沿用绝对毫秒位置；
2. 差异更大但身份和集数为高置信：按 `position / duration` 比例换算；
3. 结果夹取到 `[0, targetDuration - 5 秒]`；
4. 继续复用现有 `History.isNearEnding()` 规则，接近结尾时从头开始；
5. 若目标集不一致，必须清空进度。

该逻辑建议封装为可单测的 `ResumePositionPolicy`，播放器继续使用现有 `pendingResumeSeekMs` / `setPosition()` 应用一次性 seek。

## 14. 点击与播放协调器

新增共享入口：

```java
HistoryResumeCoordinator.open(Activity activity, GlobalHistoryEntry entry)
```

两端 `HistoryActivity` 和 TV `HomeActivity` 不再自行决定启动哪个 Activity。

协调器职责：

1. 读取当前 `global_history_mode`；
2. 判断记录是否可直接打开；
3. 构造 `HistoryResumeRequest`；
4. 自动模式调用 `VodSourceResolver`；
5. 搜索模式打开带历史上下文的全局搜索；
6. 解析成功后调用统一播放器启动接口；
7. 处理 loading、取消、超时和配置切换竞态。

播放器新增统一入口示意：

```java
VideoActivity.startFromResolvedHistory(
        Activity activity,
        ResolvedHistoryTarget target,
        HistoryResumeRequest request);
```

Intent 至少携带：

- 目标 `siteKey/vodId/name/pic`；
- 目标 `playFlag/episodeName/episodeUrl`；
- 原历史 `sourceCid/sourceKey`；
- `resume_from_history = true`；
- 可选 `vod_detail_cache_key`。

`VideoActivity.checkHistory()` 中显式的跨 `cid` 续播请求优先于当前目标源自己的旧进度，随后映射目标集并应用播放状态。普通详情入口仍走现有 `History.findPlayback()`；但「历史记录跨源聚合」生效时，若当前选择与聚合历史属于同一 TMDB 作品和同一标准季集，也应共享该聚合进度，而不要求用户必须从历史页进入。

## 15. 全局搜索上下文透传

### 15.1 Mobile

链路：

```text
HistoryResumeCoordinator
  → SearchActivity
  → SearchFragment
  → CollectFragment
  → 用户点击 Vod
  → 解析详情和目标集
  → VideoActivity.startFromResolvedHistory
```

新增 Bundle/Intent 参数或小型 Parcelable：

```text
history_resume_source_cid
history_resume_source_key
history_resume_target_cid
```

`CollectFragment.onItemClick(Vod)`：

- 普通搜索保持调用 `VideoActivity.collect()`；
- 存在历史续播上下文时，改由协调器解析选中的 `Vod`；
- 解析失败时留在当前页面。

### 15.2 Leanback

链路：

```text
HistoryResumeCoordinator
  → SearchActivity.direct / CollectActivity
  → 用户点击 Vod
  → 解析详情和目标集
  → VideoActivity.startFromResolvedHistory
```

`CollectActivity` 同样区分普通搜索和历史续播搜索。

### 15.3 搜索排序

历史续播搜索可以额外提升：

- TMDB 身份一致；
- 标题与年份完全一致；
- 目标集可用。

但搜索模式不能自动隐藏全部低置信结果。明确 TMDB 冲突的候选可降权并在点击时提示。

## 16. 历史查询与其他模块隔离

新增专用查询：

```java
GlobalHistoryRepository.getHistoryUiEntries();
```

规则：

- `global_history_mode == OFF`：内部使用当前 `History.get()`；
- 其他模式：使用 `HistoryDao.findAll()` 并投影为 `GlobalHistoryEntry`。

首版接入：

- mobile `HistoryActivity`
- leanback `HistoryActivity`
- leanback `HomeActivity` 最近观看行

首版保持当前范围：

- `ViewingReportGenerator`
- `ViewingReportCache`
- `AiRecommendationService`
- `PersonalRecommendationService`
- `SyncDialog`
- `HomeWebBridge`
- `VodBrowse`
- 远端同步和 webhook

原因：这些模块部分会对外发送标题或执行删除/同步；不能因为用户打开一个 UI 设置就扩大数据范围。

## 17. 删除与清空

### 17.1 单条删除

- 非聚合卡：按该记录自己的 `(cid, key)` 删除。
- TMDB 聚合卡：删除该展示卡的全部成员，即跨 `cid` 删除相同 `(mediaType, tmdbId)` 的记录。
- 不得调用当前依赖 `VodConfig.getCid()` 的 `History.delete()` 来删除外部历史。

建议由 `GlobalHistoryRepository.delete(entry)` 统一执行。

### 17.2 清空

模式关闭：

- 保持当前“清空当前点播配置历史”。

模式开启：

- 清空按钮代表清空当前展示的全部全局历史；
- 确认文案必须改为：

```text
确定删除所有点播配置的历史记录吗？
```

- 调用明确的全表删除方法，而不是 `History.delete(VodConfig.getCid())`。

### 17.3 删除后的状态

- 发送一次 `RefreshEvent.history()`；
- 刷新历史页和 TV 首页最近观看；
- 自动/搜索解析任务若引用已删除记录，应取消并提示“历史记录已不存在”。

## 18. 偏好备份与兼容

在 `Backup.APP_PREFS` 中加入：

```text
global_history_mode
history_aggregation_by_tmdb
```

后者当前未列入备份白名单，建议在本功能中一并补齐，确保全局历史和 TMDB 聚合组合能够恢复。

兼容规则：

- 新键不存在：默认关闭，不迁移旧开关。
- 旧 `history_aggregation_by_tmdb` 保持原值和原语义。
- `History` 新增标准季集字段需要 Room 39→40 迁移；旧行以 `(tmdbSeasonNumber=0, tmdbEpisodeNumber=0)` 初始化并表示未绑定，不根据展示标题猜测回填；绑定后季号 `0` 表示特别篇。
- 旧行再次播放并完成 TMDB 剧集绑定后会保存标准季集位置；在此之前继续调用现有集数提取工具。
- 备份恢复出非法枚举时回退关闭。

## 19. 隐私、无痕与同步

1. 自动匹配只向当前配置中已经存在且允许搜索的站源发送片名关键词。
2. TMDB 仅在现有相关设置生效时参与。
3. 不把播放进度、原播放 URL、配置 URL 或账号信息发送给 TMDB。
4. 搜索站源只收到关键词，续播进度仅在本机应用。
5. 无痕模式下允许读取用户主动点击的旧历史用于本次播放，但不得写入新目标历史，也不得删除原外部历史。
6. 新功能不改变远端同步的当前 `cid` 语义。
7. 日志只记录 `cid`、站源 key、匹配级别、集号和耗时；不得记录完整播放 URL。

## 20. 并发与性能

1. 历史列表全局查询和聚合在线程池中执行，UI 线程只接收投影结果。
2. 列表加载不调用网络。
3. 自动匹配复用 `Task.searchExecutor()` 和用户配置的搜索线程数。
4. 每次点击生成 request generation；新的点击、Activity 销毁或返回应取消旧任务。
5. 自动匹配总时限复用 `Constant.TIMEOUT_SEARCH`，详情验证使用现有 VOD 超时。
6. 仅验证有限候选，避免“所有站源 × 所有结果 × TMDB 详情”的请求爆炸。
7. `VodDetailCache` 命中后播放器不重复加载详情；缓存失效时仍可按目标 key/id 重新请求。
8. 连续点击同一历史时应防抖，避免启动多个播放器或搜索页。

## 21. 错误与降级

| 场景 | 自动模式 | 搜索模式 |
|---|---|---|
| 无可搜索站源 | 提示并结束 | 搜索页显示空状态 |
| 搜索超时 | 提示，可打开全局搜索 | 保留已返回结果 |
| TMDB 明确冲突 | 拒绝候选 | 降权并二次确认 |
| 目标源缺少对应集 | 尝试下一候选 | 留在搜索页提示 |
| 只有模糊标题候选 | 不自动播放 | 由用户选择 |
| 原历史已删除 | 取消 | 取消 |
| 当前配置在过程中切换 | 取消并提示重试 | 取消并提示重试 |
| 目标详情加载失败 | 尝试下一候选 | 留在搜索页 |
| 进程重建后内存缓存丢失 | 按 key/id 重载详情 | 按 key/id 重载详情 |

任何失败都不得更新原历史的 `createTime`、进度或目标源绑定。

## 22. 建议代码改动范围

### 22.1 新增共享类

| 文件建议 | 职责 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/history/GlobalHistoryEntry.java` | 历史 UI 展示项与成员引用 |
| `app/src/main/java/com/fongmi/android/tv/history/GlobalHistoryRepository.java` | 当前/全局查询、聚合、来源标签、删除 |
| `app/src/main/java/com/fongmi/android/tv/history/HistoryResumeRequest.java` | 跨页面续播上下文 |
| `app/src/main/java/com/fongmi/android/tv/history/HistoryResumeCoordinator.java` | 点击决策、自动/搜索分流、统一启动 |
| `app/src/main/java/com/fongmi/android/tv/history/VodSourceResolver.java` | 自动搜索和候选置信度 |
| `app/src/main/java/com/fongmi/android/tv/history/EpisodeResumeResolver.java` | 目标线路和集数映射 |
| `app/src/main/java/com/fongmi/android/tv/history/ResumePositionPolicy.java` | 绝对进度/比例进度换算 |

可将小型 `record` 作为上述类的内部类型，避免为每个结果对象单独建文件。

### 22.2 修改共享代码

| 文件 | 修改内容 |
|---|---|
| `setting/Setting.java` | 新三态设置与 clamp |
| `bean/Backup.java` | 备份新键及现有 TMDB 聚合键 |
| `bean/History.java` | 增加按指定 cid 查找、播放状态应用辅助；避免扩大 `get()` 语义 |
| `db/dao/HistoryDao.java` | 如需要，补精确跨 cid 删除接口；全局读取可直接复用 `findAll()` |
| `utils/VodDetailCache.java` | 默认无需修改 |
| `TmdbDetailActivity.java` | 可选：改用共享 `VodSourceResolver`，消除重复换源策略 |

### 22.3 修改 Mobile

| 文件 | 修改内容 |
|---|---|
| `SettingPersonalFragment.java` | 新设置显示与切换 |
| `fragment_setting_personal.xml` | 新设置行 |
| `HistoryActivity.java` | 使用全局仓库与协调器 |
| `HistoryAdapter.java` | 接收 `GlobalHistoryEntry` 或展示投影 |
| `SearchActivity.java` | 透传历史续播上下文 |
| `SearchFragment.java` | 透传上下文 |
| `CollectFragment.java` | 选择结果后解析并续播 |
| `VideoActivity.java` | 接收显式跨 cid 续播请求 |

### 22.4 修改 Leanback

| 文件 | 修改内容 |
|---|---|
| `SettingPersonalActivity.java` | 新设置显示与切换 |
| `activity_setting_personal.xml` | 新设置行 |
| `HistoryActivity.java` | 使用全局仓库与协调器 |
| `HistoryAdapter.java` | 接收展示投影 |
| `HomeActivity.java` | 最近观看使用相同仓库和点击协调器 |
| `HistoryPresenter.java` | 展示来源标签并传递展示项 |
| `SearchActivity.java` / `CollectActivity.java` | 透传请求并在选择后解析 |
| `VideoActivity.java` | 接收显式跨 cid 续播请求 |

### 22.5 资源

修改三套字符串资源：

- `values/strings.xml`
- `values-zh-rCN/strings.xml`
- `values-zh-rTW/strings.xml`

新增：

- 设置名称；
- 三态数组；
- 自动匹配加载、成功、失败；
- 缺集、TMDB 冲突、配置切换；
- 全局清空确认文案。

## 23. 实施步骤

### Phase 1：设置和全局展示

1. 新增 `global_history_mode`。
2. mobile / leanback 个性设置接入三态 UI。
3. 新增 `GlobalHistoryRepository` 和 `GlobalHistoryEntry`。
4. 历史页按模式读取当前或全局记录。
5. TMDB 聚合使用 `(mediaType, tmdbId)`。
6. 修正全局单删和清空语义。

验收：

- 默认关闭时列表完全不变；
- 开启后可看到其他 `cid` 历史；
- 删除不会误删当前配置中的无关记录。

### Phase 2：统一历史点击和直接续播

1. 新增 `HistoryResumeRequest`、`HistoryResumeCoordinator`。
2. 两端历史页和 TV 首页接入协调器。
3. 当前可直接打开记录继续走快速路径。
4. 支持聚合卡使用最新记录进度续播当前 `cid` 已有成员。

验收：

- 所有原生历史入口行为一致；
- 当前历史没有性能回退；
- 外部历史不会直接拿旧 `siteKey/vodId` 启动。

### Phase 3：自动换源

1. 提取或实现共享 `VodSourceResolver`。
2. 接入 TMDB 优先、标题年份兜底和站源健康排序。
3. 加载候选详情并验证目标集。
4. 通过 `VodDetailCache` 启动播放器。
5. 自动失败提供全局搜索降级。

验收：

- 高置信候选可自动续播；
- 同名不同年份、TMDB 冲突和缺集不会自动播放。

### Phase 4：搜索后选择

1. 两端全局搜索透传历史上下文。
2. 搜索结果选择后解析目标集。
3. 解析成功后统一启动播放器。
4. 缺集或冲突时留在搜索页。

验收：

- 用户选择的站源被尊重；
- 选择后恢复正确集数和进度；
- 取消搜索不改变历史。

### Phase 5：播放状态、备份和回归

1. 接入 `ResumePositionPolicy`。
2. 验证倍速、片头片尾、比例等字段。
3. 补偏好备份。
4. 完成双端单测、构建和手工矩阵。

## 24. 测试策略

### 24.1 单元测试

#### 设置

- 无键时为 `OFF`。
- `0/1/2` 正常读写。
- 非法值回退 `OFF`。

#### 全局历史投影

- 关闭时只返回当前 `cid`。
- 开启时返回全部 `cid`。
- TMDB 聚合关闭时不跨 `cid` 合并。
- TMDB 聚合开启时按 `(mediaType, tmdbId)` 合并。
- 相同数字 ID、不同 `mediaType` 不合并。
- 代表记录取最新 `createTime`。
- 来源标签不泄露配置 URL。

#### 点击判定

- 当前 `cid` 且站源存在：直接打开。
- 外部 `cid`：按设置进入自动或搜索。
- 当前 `cid` 但站源已移除：视为外部历史。
- 搜索模式不做自动短路。

#### 匹配

- TMDB 相同优先。
- TMDB 冲突硬拒绝自动候选。
- 标题、年份、集数完全一致可自动。
- 同名不同年份拒绝。
- 集号一致、URL 不同可匹配。
- 季号冲突拒绝。
- 目标源缺集拒绝。
- 多线路命中时顺序稳定。

#### 进度

- 时长接近使用绝对进度。
- 时长差异过大使用比例。
- 进度夹取到目标时长。
- 接近结尾重置。
- 换到不同集时清空进度。
- 显式 1.0 倍速继续覆盖个人默认倍速。

#### 删除

- 外部单条按自己的 `cid/key` 删除。
- 聚合卡删除全部成员。
- 全局清空删除全部 History。
- 关闭模式清空只删除当前 `cid`。

### 24.2 源码/集成测试

延续项目现有 SourceTest 风格，验证：

- 两端 `HistoryActivity` 都调用 `HistoryResumeCoordinator`；
- TV `HomeActivity` 不再直接调用普通 `VideoActivity.start()`；
- 普通搜索仍调用原入口；
- 带历史上下文的搜索调用解析入口；
- `History.get()` 未被改为全局查询；
- AI 推荐、观影报告和同步调用范围未变化；
- 两端播放器都处理显式跨 `cid` 请求。

### 24.3 构建验证

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest
.\gradlew.bat :app:assembleMobileArm64_v8aDebug
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug
```

### 24.4 手工验证矩阵

| 模式 | TMDB 聚合 | 历史来源 | 内容 | 预期 |
|---|---|---|---|---|
| 关闭 | 任意 | 当前 `cid` | 剧集 | 行为与当前版本一致 |
| 关闭 | 任意 | 其他 `cid` | 剧集 | 不出现在列表 |
| 自动 | 开启 | 其他 `cid` | 剧集 | TMDB 优先匹配并恢复集数/进度 |
| 自动 | 关闭 | 其他 `cid` | 剧集 | 标题年份高置信时自动，模糊时拒绝 |
| 自动 | 开启 | 其他 `cid` | 电影 | 匹配同一电影并恢复进度 |
| 自动 | 开启 | 其他 `cid` | 缺集 | 尝试下一源，最终失败则提供搜索 |
| 搜索 | 开启 | 其他 `cid` | 剧集 | 打开搜索，用户选择后续播 |
| 搜索 | 关闭 | 其他 `cid` | 同名作品 | 用户可选择，冲突候选提示 |
| 自动/搜索 | 任意 | 当前 `cid` | 剧集 | 直接打开，不做无意义搜索 |
| 自动/搜索 | 任意 | 已移除站源 | 剧集 | 作为外部历史处理 |
| 自动/搜索 | 任意 | 聚合卡 | 回看旧集 | 使用最新行为而非最大进度 |
| 任意 | 开启 | 当前 `cid` | 普通详情显式选择同一 TMDB 标准季集的另一来源 | 共享聚合历史进度 |
| 任意 | 关闭 | 当前 `cid` | 普通详情显式选择 URL 不同的剧集 | 不因临时 TMDB 映射强制继承进度 |
| 自动/搜索 | 任意 | 任意 | 无痕模式 | 本次可播但不写新历史 |

## 25. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 把 `History.get()` 直接改为全局 | 报告、AI、同步范围意外扩大 | 使用专用 UI Repository |
| 同名作品误匹配 | 自动播错影片或剧集 | TMDB 冲突硬拒绝；标题年份高置信门槛；自动模式不接受模糊候选 |
| 只按 tmdbId 聚合 | 电影/剧集数字 ID 冲突 | 使用 `(mediaType, tmdbId)` |
| 外部历史删除使用当前 cid | 误删或删不掉 | 删除统一走 Repository，并显式传 cid/key |
| 搜索请求过多 | 加载慢、站源压力大 | 两阶段筛选、候选上限、超时和取消 |
| 旧源字段复制到新源 | 线路、URL、标题污染 | 使用播放状态快照，目标字段重新赋值 |
| 搜索期间切换配置 | 选择结果属于旧配置 | 请求保存 targetCid，提交前校验 |
| 搜索页丢失续播上下文 | 用户选源后从头播放 | Intent/Bundle 全链路透传并加 SourceTest |
| 进程重建丢失详情缓存 | 播放无法启动 | Intent 保留 key/id，缓存仅作优化 |
| 全局清空语义不明确 | 用户误删多配置记录 | 使用明确确认文案 |

## 26. 验收标准

1. 新安装或无偏好时，「全局历史续播」显示“关闭”。
2. 关闭时历史列表、点击、删除、同步和推荐行为与当前版本一致。
3. 自动或搜索模式下，历史页可展示其他 `cid` 的记录。
4. TV 首页最近观看与历史页范围和点击策略一致。
5. 当前配置可直接打开的历史不会无意义触发全局搜索。
6. 自动模式能在当前配置中找到高置信播放源，并恢复正确季集与进度。
7. 自动模式不会播放 TMDB 冲突、年份冲突或缺少目标集的候选。
8. 自动失败时用户可进入全局搜索继续操作。
9. 搜索模式始终由用户选择外部历史的播放源。
10. 用户选择后，目标源线路和集数被重新映射，不复用旧源 URL。
11. 进度、显式倍速、片头片尾和画面比例按规则继承。
12. 「历史记录跨源聚合」开启时，普通详情页显式选择同一 TMDB 作品、同一标准季集的其他来源也共享进度；关闭时不扩大普通选集匹配。
13. 外部历史和跨 `cid` 聚合卡可正确删除；全局清空有明确确认。
14. TMDB 未配置或聚合设置关闭时，功能仍可使用标题/年份保守匹配。
15. AI 推荐、观影报告、同步、WebHome 和外部 Browse 的数据范围不被本设置隐式扩大。
16. mobile 与 leanback 单测和 Debug 构建通过。

## 27. 最终建议

推荐按以下最小闭环实施：

1. 先完成三态设置和全局历史展示；
2. 用统一协调器接管所有原生历史点击；
3. 自动模式复用并抽取现有换源搜索策略；
4. 搜索模式只增加续播上下文透传，不重写搜索页面；
5. 通过 Room 39→40 迁移保存 TMDB 标准季集位置，旧历史以默认值无损兼容；
6. 以高置信自动、低置信交给用户为基本安全边界。

该方案能最大程度复用当前已经实现的 TMDB 历史聚合、集号容错、全局搜索、站源健康排序和播放器续播能力，同时把“全局历史的展示范围”与“同剧身份聚合”拆成两个清晰、可独立控制的设置。
