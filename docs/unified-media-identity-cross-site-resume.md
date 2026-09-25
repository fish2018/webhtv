# 统一媒体身份与跨站续播设计文档

> **状态：❌ 未实现，仅设计文档（2026-07-14 核实）**
>
> - grep `MediaIdentity` / `CrossSiteResume` / `MediaPlaybackState` 仅命中本设计文档，无任何代码。
> - 无 `media/` 包、无相关 DAO、无新表。
> - **⚠️ DB 迁移版本号已过时**：本文档规划用 `MIGRATION_36_37` 建四张统一身份表，但实际 36→37 迁移已被"AI 观影报告"占用（给 History 加了 typeName/area/actor/director/year 五个字段）。若要实施本功能，需改用 **37→38** 迁移，第 11、14 节的版本号规划需相应更新。
> - 这是当前唯一完全未动工的设计，也是投入最大的一块。

状态：草案  
日期：2026-07-04  
目标版本：MVP  
范围：播放页、详情页内联播放、换源、历史进度、字幕、倍速、片头片尾设置

## 1. 背景

当前播放历史以站点资源为中心：

```text
historyKey = siteKey + "@@@" + vodId + "@@@" + cid
```

这适合记录“某站某 vod”的播放状态，但用户真实关心的是：

> 我正在看这部剧的第几集，换一个站点后也应该继续看同一部剧、同一集、同一进度。

现有 `History` 已经保存了许多可复用的播放状态：

- `position`：播放进度。
- `duration`：时长。
- `speed`：倍速。
- `opening`：片头跳过点。
- `ending`：片尾跳过时长。
- `scale`：画面比例。
- `vodFlag` / `vodRemarks` / `episodeUrl`：当前线路和集。

现有 `Track` 以播放 key 记录音轨/字幕轨选择：

```text
Track.key = player/playback key
Track.type = track type
```

字幕模块已经有相近概念：

- `SubtitleRequest`
- `SubtitleContext`
- `ResolvedMediaIdentity`
- `SubtitleTmdbResolver`
- `SubtitleTitleParser`

因此 MVP 不需要推翻现有历史体系，而是新增一层“统一媒体身份”和“统一剧集播放状态”，让旧的站点历史继续负责兼容展示和入口，新状态负责跨站继承。

### 1.1 现有换源能力盘点

当前播放页已经有一套可复用的换源续播基础，核心不是统一媒体身份，而是“同名 `History` 复制 + 当前会话迁移”。

现有链路：

1. mobile / leanback `VideoActivity.getDetail(Vod item)` 在切换到候选源前会先调用 `saveHistory()`。
2. `saveHistory()` 会把当前 `mHistory` 的 `position` / `duration` / `speed` / `opening` / `ending` 等写入旧源历史。
3. 新源详情加载后，`checkHistory(vod)` 调用 `History.findPlayback(getHistoryKey(), List.of(item.getName(), getName()), item.getFlags())`。
4. `History.findPlayback()` 先按新源 `historyKey` 精确查找；找不到时按 `vodName` 查同名历史。
5. `findPlaybackCandidate()` 优先选择“有进度且能匹配新源任一集”的同名历史；其次选择任意有进度的同名历史；最后选择第一条同名历史。
6. 命中后通过 `History.copy()` 把旧历史复制成新源 key 的历史对象。
7. 新源 `Vod` 更新后会通过 `mHistory.replace(getHistoryKey())` 将当前会话迁移到新源 key。

现有同集匹配规则：

- 优先匹配 `episodeUrl`。
- URL 不同则匹配 `vodRemarks` 与新源 `episode.getName()` / `episode.getDisplayName()`。
- 具体选集还会经过 `Flag.find(...)`，按 URL、名称、数字集号、包含关系打分。

现有能力可继承的字段：

- `position`
- `duration`
- `speed`
- `opening`
- `ending`
- `scale`
- `revSort`
- `revPlay`
- `vodFlag`
- `vodRemarks`
- `episodeUrl`
- `vodPic` / `wallPic` / `vodName`

现有边界：

- 只按当前 `cid` 查找，不跨配置。
- 主要按片名和集名工作，不能可靠区分同名不同年份。
- 不理解 TMDB ID、季、集的稳定身份。
- `EpisodePositionCache` 的 key 是 `siteKey|vodId|flag`，只适合同站同线路内切集恢复，不是跨站续播状态。
- `Track` 与字幕/音轨选择按 playback key 保存；换源迁移 `History` 时不会形成稳定的跨源字幕身份。
- `History.merge()` 保存时只把 `opening`、`ending`、非 1 倍速同步给同名历史，不会把进度反向同步到所有同名记录。

结论：现有机制应该保留为兼容层和 fallback。统一媒体身份方案要在它之上增强，而不是替换它。

## 2. 目标

MVP 目标：

1. 用 TMDB / 标题 / 年份 / 季集号建立统一媒体身份。
2. 同一部剧在不同站点、不同 vodId 下可以识别为同一媒体。
3. 换源时定位到同一季同一集。
4. 换源后保留：
   - 当前进度。
   - 字幕选择或字幕匹配上下文。
   - 倍速。
   - 片头片尾设置。
5. 不破坏现有 `History`、收藏、历史页、播放器入口和字幕自动匹配。

## 3. 非目标

MVP 暂不做：

1. 不做账号级云同步。
2. 不做跨设备同步。
3. 不做复杂 UI 管理页。
4. 不强制所有站点都使用 TMDB。
5. 不保证无集号、乱序、合集、剧场版一定能自动跨站续播。
6. 不迁移历史页主展示逻辑为统一媒体维度。
7. 不合并收藏 `Keep` 的跨站身份。

## 4. 设计原则

### 4.1 旧 History 保持站点维度

`History` 继续保存现有站点播放记录，避免影响：

- 历史页排序和展示。
- 站点入口恢复。
- 投屏 / DLNA / BrowseTree。
- 收藏和 keep 现有 key。
- 外部调用 `VideoActivity.start(...)` 的行为。

统一身份作为旁路增强，不替代第一阶段的 `History`。

### 4.2 只有高置信身份才自动继承

跨站续播如果错认，会比“不续播”更伤体验。MVP 规则：

- TMDB ID + 季集号命中：自动继承。
- 标题 + 年份 + 明确集号命中：自动继承。
- 只有标题相同、年份缺失或集号不明：只记录候选，不自动 seek。
- 电影无集号：标题 + 年份 或 TMDB ID 命中后可自动继承。

### 4.3 统一状态只保存可跨源复用的设置

跨源稳定复用：

- position
- duration
- speed
- opening
- ending
- scale
- subtitle logical preference

不跨源复用：

- 真实播放 URL。
- 解析后的临时 URL。
- 请求 header。
- 站点私有 flag 名称本身。
- 只在当前源存在的临时 track id。

### 4.4 先服务换源，不重做历史中心

MVP 的主路径是：

```text
当前正在看 A 站第 N 集 -> 切到 B 站 -> 自动选中 B 站第 N 集 -> 恢复进度和设置
```

历史页是否聚合多个站点记录，可以放到后续版本。

### 4.5 现有 `History.findPlayback` 作为兜底层

统一身份恢复不是唯一恢复来源。MVP 应采用分层恢复：

```text
1. 当前站点 History 精确命中
2. 统一 episodeKey 状态命中
3. 现有 History.findPlayback 同名历史兜底
4. 新建 History
```

原因：

- 当前同名历史续播已经覆盖了大量真实换源场景。
- 旧逻辑已经处理了 `History.key`、`Keep`、播放器入口、历史页刷新等兼容问题。
- 统一身份早期可能有误判或未覆盖场景，需要旧逻辑兜底。
- 新方案应优先补“同名不同年份、集名不一致、字幕/设置跨源”这些旧逻辑短板。

## 5. 目标体验

### 5.1 换源前

用户在 A 站播放：

```text
庆余年 第二季 第 12 集，播放到 18:32，1.5x，片头 01:35，片尾 02:10，已选择中文字幕
```

系统保存两份状态：

1. 旧 `History(siteA@@@vodA@@@cid)`，保证现有功能不变。
2. 新 `MediaPlaybackState(media episode key)`，用于跨站继承。

### 5.2 换源后

用户切到 B 站同一剧：

1. 系统识别 B 站 vod 与 A 站 vod 是同一媒体。
2. 系统在 B 站剧集列表中找到第 12 集。
3. 播放 B 站第 12 集。
4. 播放器恢复到 18:32。
5. 自动套用 1.5x、片头、片尾。
6. 字幕优先恢复同一个外部字幕；如果不可用，则基于统一身份重新自动匹配。

### 5.3 不确定场景

如果 B 站标题相似但年份缺失、集号解析失败：

- 不自动跳进度。
- 可以保留现有换源逻辑。
- 可选轻提示：`已切换来源，未能确认同一集，未自动恢复进度`。

## 6. 统一身份模型

### 6.1 媒体身份

媒体身份代表“这部作品”，不代表某站资源。

```java
public final class MediaIdentity {
    public String mediaKey;
    public String mediaType;      // "tv" | "movie" | "unknown"
    public int tmdbId;            // unknown = 0
    public String title;
    public String normalizedTitle;
    public int year;              // unknown = 0
    public int confidence;        // 0-100
    public String source;         // "TMDB" | "TITLE_YEAR" | "TITLE_ONLY"
}
```

推荐 key：

```text
TMDB 剧集：tmdb:tv:{tmdbId}
TMDB 电影：tmdb:movie:{tmdbId}
标题年份：title:{mediaType}:{normalizedTitle}:{year}
标题兜底：title:{mediaType}:{normalizedTitle}:0
```

### 6.2 剧集身份

剧集身份代表“一部剧的某一季某一集”。

```java
public final class EpisodeIdentity {
    public String episodeKey;
    public String mediaKey;
    public int seasonNumber;      // unknown = -1
    public int episodeNumber;     // unknown = -1
    public int tmdbEpisodeId;     // unknown = 0
    public String episodeTitle;
    public int confidence;
}
```

推荐 key：

```text
TMDB 剧集：tmdb:tv:{tmdbId}:s{season}:e{episode}
TMDB 单集：tmdb:episode:{tmdbEpisodeId}
标题集数：title:tv:{normalizedTitle}:{year}:s{season}:e{episode}
电影：{mediaKey}:movie
```

默认 season 规则：

- TMDB 明确 season：使用 TMDB。
- 站点标题含 `Season 2` / `第二季` / `S02`：使用解析值。
- 无 season 但有 episode：默认 `seasonNumber = 1`。
- 完全无法解析 episode：`episodeNumber = -1`，不能自动跨站 seek。

### 6.3 站点资源绑定

绑定记录“某站某 vod / 某条线路某集”对应哪个统一身份。

```java
public final class MediaSourceBinding {
    public String bindingKey;
    public int cid;
    public String playbackKey;    // 旧 historyKey: site@@@vod@@@cid
    public String siteKey;
    public String vodId;
    public String flag;
    public String episodeName;
    public String episodeUrlHash;
    public String mediaKey;
    public String episodeKey;
    public int confidence;
    public long updatedAt;
}
```

推荐 binding key：

```text
{cid}:{siteKey}:{vodId}:{flag}:{episodeUrlHash}
```

不直接保存完整 `episodeUrl` 作为统一 key，原因是 URL 可能长、临时、含 token，也不适合作为跨站身份。

### 6.4 统一播放状态

```java
public final class MediaPlaybackState {
    public String episodeKey;
    public int cid;
    public long position;
    public long duration;
    public float speed;
    public int scale;
    public long opening;
    public long ending;
    public String subtitleJson;
    public String lastPlaybackKey;
    public long updatedAt;
}
```

`episodeKey + cid` 唯一。`cid` 保留是为了避免不同配置源集合之间互相污染。

## 7. 字幕状态模型

字幕跨源有两层：

### 7.1 外部字幕资产

手动或自动匹配到的外部字幕，可保存：

```java
public final class SubtitleSnapshot {
    public String mode;           // "EXTERNAL" | "TRACK" | "DISABLED" | "AUTO"
    public String provider;
    public String candidateId;
    public String url;
    public String name;
    public String lang;
    public String format;
    public boolean manual;
    public long updatedAt;
}
```

应用规则：

1. 如果 `mode = DISABLED`，换源后保持字幕关闭。
2. 如果有外部字幕 `url`，优先注入到新播放结果。
3. 如果外部字幕不可用，使用统一身份重新触发自动字幕匹配。
4. 如果只是内嵌轨道选择，则按语言 / 名称 / format 做模糊恢复，不能用旧 track id。

### 7.2 复用现有字幕身份

现有 `SubtitleContextBuilder` 已经能通过：

- `TmdbItem`
- `TmdbEpisode`
- `vodName`
- `vodYear`
- `episodeName`

构建字幕查询上下文。

MVP 建议抽出或复用其中的身份解析能力，避免另写一套标题/年份/集数解析：

```text
SubtitleRequest -> SubtitleTmdbResolver -> ResolvedMediaIdentity
```

新增统一身份服务可以把 `ResolvedMediaIdentity` 作为输入之一。

## 8. 核心服务设计

### 8.1 `MediaIdentityResolver`

职责：把当前播放上下文解析成统一媒体身份。

输入：

```java
public final class PlaybackIdentityInput {
    public int cid;
    public String playbackKey;
    public Site site;
    public Vod vod;
    public Flag flag;
    public Episode episode;
    public TmdbItem tmdbItem;
    public TmdbEpisode tmdbEpisode;
}
```

输出：

```java
public final class PlaybackIdentity {
    public MediaIdentity media;
    public EpisodeIdentity episode;
    public int confidence;
    public String reason;
}
```

解析优先级：

1. `tmdbItem.tmdbId + tmdbEpisode.season/number`
2. TMDB 匹配缓存：`Setting.getTmdbMatchCache().find(siteKey, vodId)`
3. `vod.name + vod.year + episode.number`
4. `vod.name + episode.name` 中解析年份、季、集
5. 标题兜底，仅用于记录，不自动恢复

### 8.2 `MediaPlaybackStateStore`

职责：读写统一播放状态。

接口：

```java
public interface MediaPlaybackStateStore {
    MediaPlaybackState find(String episodeKey, int cid);
    void save(MediaPlaybackState state);
    void bind(MediaSourceBinding binding);
    List<MediaSourceBinding> findBindings(String mediaKey, int cid);
}
```

实现建议：

- MVP 使用 Room 新表。
- 不存入 `Prefers`，因为这是高频更新状态，且需要查询。
- 保存频率跟现有 `History.save()` 对齐，不额外高频写库。

### 8.3 `CrossSiteResumeService`

职责：在换源或进入播放页时，把旧站点状态映射到新站点。

接口：

```java
public interface CrossSiteResumeService {
    PlaybackResumePlan resolve(PlaybackIdentityInput input, List<Flag> targetFlags);
    void saveSnapshot(PlaybackIdentityInput input, History history, List<Track> tracks);
}
```

输出：

```java
public final class PlaybackResumePlan {
    public boolean canApply;
    public String mediaKey;
    public String episodeKey;
    public Flag targetFlag;
    public Episode targetEpisode;
    public long position;
    public float speed;
    public long opening;
    public long ending;
    public int scale;
    public SubtitleSnapshot subtitle;
    public String reason;
}
```

## 9. 播放流程接入

### 9.1 进入播放页

当前流程大致是：

```text
VideoActivity.checkHistory(vod)
  -> History.findPlayback(historyKey, names, flags)
  -> createHistory(vod)
  -> applyIntentPlaybackSelection(vod)
  -> set speed/opening/ending/scale
```

MVP 接入点：

```text
checkHistory(vod)
  -> 先查当前站点 History 精确 key
  -> resolve unified identity
  -> CrossSiteResumeService.resolve(...)
  -> 如果 unified plan 可应用：覆盖 mHistory 的 episode / position / speed / opening / ending / scale
  -> 如果 unified plan 不可应用：回退现有 History.findPlayback 同名历史逻辑
  -> 继续现有 UI 和 player 初始化
```

注意：

- 不改变 `getHistoryKey()`。
- 不改变 `startPlayer(getHistoryKey(), ...)`。
- 只在同一集高置信命中时覆盖 `mHistory` 的播放状态。
- 保留现有 `History.findPlayback` 作为 fallback，避免统一身份未覆盖时丢失已有续播能力。

### 9.2 播放中保存

当前保存：

```text
saveHistory()
  -> updatePlaybackHistoryPosition()
  -> History.copy().merge().save()
```

MVP 增加：

```text
saveHistory()
  -> 保存旧 History
  -> CrossSiteResumeService.saveSnapshot(input, history, Track.find(playerKey))
```

保存内容：

- 当前 episodeKey。
- position / duration。
- speed。
- opening / ending。
- scale。
- 字幕 snapshot。
- lastPlaybackKey。

### 9.3 换源

换源前：

1. 沿用现有行为，先调用 `saveHistory()` 保存当前 `History`。
2. 在 `saveHistory()` 后保存统一状态。
3. 记录当前 `mediaKey` / `episodeKey`。

换源后：

1. 拉取新站点详情。
2. 先保留当前新源 `historyKey` 精确历史。
3. 对每个候选 episode 解析 episode identity。
4. 优先选择 episodeKey 完全相同的集。
5. 找不到时，按 season/episode number 匹配。
6. 仍找不到时，回退现有 `History.findPlayback` 和 `Flag.find(...)`。
7. 再找不到时，不自动恢复进度。

现有 mobile / leanback 的 `getDetail(Vod item)` 已经会在切源前 `saveHistory()`，实现时应复用这个时机，而不是新增一条并行保存路径。

### 9.4 详情页内联播放

`TmdbDetailActivity` 内联播放也已有 `History.findPlayback` 和 `saveInlineHistory` 逻辑。MVP 应同步接入：

- `initHistory()` 后 resolve unified state。
- `updateInlineHistory()` 后绑定当前 episode。
- `saveInlineHistory()` 同步保存 unified state。

这样用户在详情页内联看过后，进入全屏播放或换源也能继承状态。

## 10. 状态应用规则

### 10.1 进度

应用条件：

- episodeKey 高置信相同。
- `position > 0`。
- 非接近片尾完成状态，或用户仍明确选择继续播放。

时长兼容：

- 如果目标 duration 未知：先保存 pending seek，播放器拿到 duration 后再 seek。
- 如果目标 duration 与保存 duration 差异小于 10 分钟：使用绝对 position。
- 如果差异较大但都有效：MVP 不自动 seek，避免错位。

### 10.2 倍速

规则：

- 优先使用统一状态中的 `speed`。
- `speed <= 0` 时回退 `History.speed`。
- 都无效时回退播放器默认速度。
- 换源成功后写回新站点 `History.speed`。

### 10.3 片头片尾

`opening` 是从开头跳过的时间点，`ending` 是片尾剩余时长。

应用条件：

- `opening > 0 && opening < duration`
- `ending > 0 && ending < duration`
- 目标 duration 未知时先暂存，duration 可用后校验。

如果目标片源删减明显，duration 差异过大，MVP 不自动套用片头片尾。

### 10.4 字幕

应用优先级：

1. 用户手动选择的外部字幕。
2. 上次自动匹配成功的外部字幕。
3. 内嵌字幕轨道的语言/名称偏好。
4. 基于统一身份重新自动匹配。
5. 默认播放器行为。

如果上次用户关闭字幕，换源后也应保持关闭。

## 11. 数据库方案

### 11.1 新增实体

建议新增三张表：

```text
MediaIdentity
EpisodeIdentity
MediaPlaybackState
MediaSourceBinding
```

也可以把 `MediaIdentity` 和 `EpisodeIdentity` 合成一张，但分开后更利于后续历史聚合、收藏聚合、推荐。

### 11.2 Room 迁移

当前 `AppDatabase.VERSION = 36`。实现时需要：

1. 新增实体到 `@Database(entities = {...})`。
2. 版本升级到 `37`。
3. 添加 `MIGRATION_36_37`。
4. 创建索引：
   - `MediaIdentity(mediaKey, cid)` unique。
   - `EpisodeIdentity(episodeKey, cid)` unique。
   - `MediaPlaybackState(episodeKey, cid)` unique。
   - `MediaSourceBinding(playbackKey, mediaKey, episodeKey, cid)`。

### 11.3 为什么不直接改 History 主键

不推荐把 `History.key` 改成统一身份 key，原因：

- 当前大量逻辑依赖 `siteKey@@@vodId@@@cid`。
- `History.getSiteKey()` / `getVodId()` 直接 split key。
- `Keep`、`Track`、播放器入口、BrowseTree 等都与旧 key 有耦合。
- 改主键会导致大范围迁移和回归。

MVP 用旁路表更稳。

## 12. 兼容策略

### 12.1 旧历史自动提升

首次播放旧历史时：

1. 从旧 `History` 生成 `PlaybackIdentityInput`。
2. resolve 出 mediaKey / episodeKey。
3. 保存 `MediaSourceBinding`。
4. 把当前 `History` 状态复制到 `MediaPlaybackState`。

不需要启动时全量迁移，避免阻塞和误识别。

### 12.2 隐私模式

如果 `Setting.isIncognito()`：

- 不保存旧 `History`。
- 不保存 `MediaPlaybackState`。
- 不保存 `MediaSourceBinding`。
- 不复用本次隐私播放产生的字幕 snapshot。

是否读取已有统一状态：建议仍然读取，因为当前隐私模式通常是不写历史而非完全隔离；如果产品希望更严格，可以增加设置项。

### 12.3 删除历史

MVP 中删除某条站点历史：

- 删除旧 `History` 和对应 `Track`，保持现状。
- 不立即删除 `MediaPlaybackState`，因为其他站点可能还在引用。

后续可以做清理任务：当某 episodeKey 没有任何 binding 且超过一定时间，再删除统一状态。

## 13. 模块和文件建议

推荐新增包：

```text
app/src/main/java/com/fongmi/android/tv/media/
  MediaIdentityResolver.java
  CrossSiteResumeService.java
  MediaPlaybackStateStore.java
  MediaIdentityKeys.java
  MediaTitleNormalizer.java
  EpisodeIdentityMatcher.java

app/src/main/java/com/fongmi/android/tv/media/model/
  PlaybackIdentityInput.java
  PlaybackIdentity.java
  MediaIdentity.java
  EpisodeIdentity.java
  MediaSourceBinding.java
  MediaPlaybackState.java
  PlaybackResumePlan.java
  SubtitleSnapshot.java

app/src/main/java/com/fongmi/android/tv/db/dao/
  MediaIdentityDao.java
  EpisodeIdentityDao.java
  MediaPlaybackStateDao.java
  MediaSourceBindingDao.java
```

如果不想引入 `media` 这个新顶层包，也可以放在：

```text
com.fongmi.android.tv.playback.identity
```

但不建议放进 `ui/helper`，因为这是播放状态能力，不是 UI helper。

## 14. MVP 开发步骤

### Phase 1：身份解析和 key 生成

任务：

1. 新增 `MediaIdentityKeys`。
2. 新增 `MediaIdentityResolver`。
3. 复用或抽取 `SubtitleTitleParser` 的标题、年份、季集解析。
4. 增加单测：
   - TMDB 剧集 key。
   - TMDB 电影 key。
   - 标题年份 key。
   - 无年份低置信。
   - `第 12 集` / `EP12` / `S02E03`。

验收：

- 不接入播放流程。
- 纯 JVM 测试通过。

### Phase 2：数据库和状态存取

任务：

1. 新增 Room entity / dao。
2. 添加 `MIGRATION_36_37`。
3. 实现 `MediaPlaybackStateStore`。
4. 单测 key 和状态合并规则。

验收：

- 旧 `History` 不变。
- 数据库升级可跑通。

### Phase 3：播放页保存统一状态

任务：

1. 在 mobile / leanback `VideoActivity.saveHistory()` 后保存 snapshot。
2. 在 `TmdbDetailActivity.saveInlineHistory()` 后保存 snapshot。
3. 保存 subtitle snapshot。
4. 隐私模式不写。

验收：

- 播放原有历史仍正常出现。
- 新表能看到 mediaKey / episodeKey / position / speed / opening / ending。

### Phase 4：进入播放页恢复统一状态

任务：

1. 在 `checkHistory(vod)` 中先保留当前站点 key 精确历史查询。
2. 当前站点无精确历史或需要跨站恢复时，resolve unified resume plan。
3. 高置信时覆盖 `mHistory` 的 episode / position / speed / opening / ending / scale。
4. unified plan 不可应用时，继续调用现有 `History.findPlayback` 同名历史逻辑。
5. 恢复字幕 snapshot 或触发自动匹配。

验收：

- A 站看第 N 集后，B 站同剧同集自动恢复。
- 不确定身份不自动 seek。
- 现有同站续播不回退。
- 现有同名换源续播不回退。

### Phase 5：换源路径增强

任务：

1. 复用现有 `getDetail(Vod item)` 切源前 `saveHistory()` 时机保存统一 snapshot。
2. 新源详情加载后按 episodeKey 选集。
3. 选不到时回退现有 `History.findPlayback` / `Flag.find(...)`。
4. 只有统一身份高置信且 duration 校验通过时自动 seek。
5. 加轻提示和 debug log。

验收：

- 手动换源不丢进度。
- 不同站点剧集名称不同但集号一致时可恢复。
- 旧逻辑已能恢复的同名同集场景继续恢复。

## 15. 测试策略

### 15.1 单元测试

重点覆盖：

- 标题归一化。
- 年份解析。
- 季集解析。
- TMDB key 生成。
- fallback key 生成。
- 置信度判断。
- duration 差异下是否应用 position。
- opening / ending 校验。
- subtitle snapshot 序列化。
- `History.findPlayback` fallback 优先级：
  - 精确 key 优先。
  - 同名且匹配集优先。
  - 同名有进度次之。
  - 无进度同名最后。

### 15.2 集成测试

可以用 fake `Vod` / `Flag` / `Episode` 构造：

1. A 站 `vodId=100` 第 12 集保存。
2. B 站 `vodId=abc` 第 12 集加载。
3. 断言 resume plan 指向 B 站第 12 集。
4. 断言 position/speed/opening/ending 被应用。
5. 构造 unified plan 不可用但旧 `History.findPlayback` 可用的场景，断言仍能恢复。

### 15.3 手工冒烟清单

必须覆盖：

1. 同站继续播放。
2. A 站切 B 站，同 TMDB，同集恢复。
3. A 站切 B 站，无 TMDB，标题年份同，集号同，恢复。
4. 标题相同但年份不同，不恢复。
5. 集号解析失败，不恢复。
6. 电影跨站恢复。
7. 倍速恢复。
8. 片头片尾恢复。
9. 手动字幕恢复。
10. 字幕关闭状态恢复。
11. 隐私模式不写新状态。
12. mobile / leanback 都验证。
13. 详情页内联播放到全屏播放继承。
14. 当前已有同名换源续播路径不退化。
15. 当前站点已有精确历史时，不被跨站统一状态错误覆盖。

## 16. 风险和缓解

| 风险 | 表现 | 缓解 |
| --- | --- | --- |
| 错认媒体 | A 剧进度恢复到 B 剧 | 只对高置信身份自动应用，低置信只记录 |
| 错认集数 | 跳到错误集 | episodeKey 优先，season/episode 次之，名称兜底不自动 seek |
| 片源时长差异 | seek 到剧情不对应位置 | duration 差异超过阈值不自动 seek |
| 字幕 URL 失效 | 换源后字幕加载失败 | 失败后用统一身份重新自动匹配 |
| 旧历史被破坏 | 历史页/收藏异常 | 不改 `History.key`，新增旁路表 |
| 写库过频 | 播放中频繁保存拖慢 | 跟随现有 saveHistory 节奏，不新增高频 timer |
| source set 重复 | mobile/leanback 各写一套 | 核心服务放 main，Activity 只接入 |
| 旧换源能力退化 | 以前按同名能续播的场景失效 | unified plan 不可用时必须回退 `History.findPlayback` |
| 字幕能力误判 | 以为现有 Track 已经跨源，实际换源后丢选择 | 新增 `SubtitleSnapshot`，现有 Track 只作为当前 key 的输入 |

## 17. 日志和可观测性

建议加 debug log tag：

```text
media-identity
cross-site-resume
```

关键日志：

- resolve identity 成功/失败。
- mediaKey / episodeKey / confidence。
- resume plan 是否应用。
- 不应用原因：低置信、集号缺失、duration 差异过大、隐私模式。
- 字幕 snapshot 应用成功/失败。

日志不应输出完整播放 URL、header、token。

## 18. 验收标准

MVP 完成标准：

1. 不改变现有 `History.key` 格式。
2. 同站续播行为保持不变。
3. TMDB 命中的跨站同集可以恢复进度。
4. 标题 + 年份 + 集号命中的跨站同集可以恢复进度。
5. 换源后恢复倍速、片头、片尾。
6. 字幕关闭和手动字幕选择可被继承；外部字幕失效时可回退自动匹配。
7. 隐私模式不产生新统一状态。
8. mobile / leanback / 详情页内联播放三条路径都有接入。
9. 关键纯逻辑有单测。
10. 错认风险场景不会自动 seek。
11. 当前 `History.findPlayback` 同名换源续播作为 fallback 保留。
12. 当前站点精确历史优先级高于跨站统一状态。

## 19. 后续方向

MVP 稳定后可以继续做：

1. 历史页按统一媒体聚合，多来源作为子项。
2. 收藏跨站聚合。
3. 手动“绑定为同一部剧”。
4. 手动“取消错误绑定”。
5. 多季剧集更强的 season 映射。
6. 跨设备同步统一播放状态。
7. 字幕资产缓存和失效检测。
8. 换源弹窗中标记“可续播来源”。

## 20. 建议结论

建议采用“旁路统一身份表 + 旧 History 兼容”的 MVP 方案：

1. 旧 `History` 继续服务现有播放、历史页、收藏和入口。
2. 现有 `History.findPlayback` 同名续播保留为兼容层和 fallback。
3. 新增统一身份和统一播放状态，专门补足同名不同年份、集名不一致、字幕/设置跨源继承。
4. 身份解析复用现有 TMDB 匹配缓存和字幕侧解析能力。
5. 恢复优先级固定为：当前站点精确 History -> 统一 episodeKey -> 现有同名 History fallback -> 新建 History。
6. 只在高置信命中时自动应用进度和设置。
7. 先把播放页、详情页内联播放、换源这三条主路径打通，再考虑历史聚合。

这条路线改动可控，用户收益明显，也给后续“媒体库化”的历史和收藏打好地基。
