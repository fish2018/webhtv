# TMDB 相关视频发现与应用内播放设计

- 状态：Proposed，可行，建议实施
- 日期：2026-08-16
- 目标分支：`dev`
- 当前基线：`731baa2483`
- 适用界面：独立 `TmdbDetailActivity`、Mobile `VideoActivity` TMDB 头部、Leanback `VideoActivity` TMDB 区域
- 参考快照：`ErrorFlynn/ytdlp-interface@d5293db92c208cf1a6e2701dd920ee1aaba5fb2f`、`libre-tube/LibreTube@29a5eeb65de8382021f9161dab64f90524b53e50`、本地 `sabr-source-20260814`

## 1. 结论

该需求可行，而且首版不需要引入 yt-dlp 可执行文件、YouTube Data API、LibreTube 业务层或 SABR 模块。

建议的最小可靠方案是：

1. 以已经刮削匹配成功的 TMDB ID、季号和集号为唯一发现依据；
2. 从 TMDB 官方 movie、TV series、TV season、TV episode videos 接口取得策划过的视频元数据；
3. 只接受 `site=YouTube` 且 key 合法的结果，由应用自己生成标准 YouTube watch URL；
4. 点击卡片后走现有 `SiteApi.PUSH`、`VideoActivity.startDirect`、`Source`、`Youtube`、`PlayerManager` 链路；
5. 视频仍在 webhtv 的现有播放器中播放，播放器内核、字幕、清晰度、解码、倍速、画面比例和返回栈行为继续复用现状。

这条路线把两个问题分开：TMDB 负责确定视频和当前影片、季度、剧集之间的业务关联；现有 YouTube 提取器负责在点击时把稳定的 watch URL 解析为可播放流。

首版不建议用 YouTube 搜索结果或 LibreTube 的算法推荐代替 TMDB 关联数据，否则容易出现标题误匹配、剧透、无关内容和不可重复排序。

## 2. 当前工程能力

### 2.1 TMDB 元数据与相关内容

当前 `TmdbService` 已支持 movie、TV、season、episode 详情，`recommendations` 与 `similar`，以及匹配、季度绑定、剧集元数据回写、缓存和 load generation 竞态保护。

当前缺口：

- `detailAppend` 只追加 `recommendations,similar`，没有 `videos`；
- season 详情只追加 `images,credits,aggregate_credits,translations`；
- episode 详情只追加 `images,credits,translations`；
- 相关区域只接受 `TmdbItem`，点击语义是打开另一部影片的详情，不是播放视频。

### 2.2 三个必须覆盖的 UI 表面

当前相关内容不是一个统一界面：

1. `TmdbDetailActivity`
   - 使用 `TmdbRailAdapter`；
   - `openRelatedItem` 会先搜索当前站点，再打开匹配详情；
   - 布局为 `app/src/main/res/layout/activity_tmdb_detail.xml`。
2. Mobile `VideoActivity`
   - 通过共享的 `TmdbHeaderView` 显示 TMDB 内容；
   - 相关影片点击走 `TmdbNavigation.open`；
   - 布局为 `app/src/main/res/layout/view_tmdb_header.xml`。
3. Leanback `VideoActivity`
   - 使用 flavor 自己的 `HorizontalGridView`、`ArrayObjectAdapter` 和 `TmdbRecommendationPresenter`；
   - 相关影片点击同样走 `TmdbNavigation.open`；
   - 布局为 `app/src/leanback/res/layout/activity_video.xml`。

因此实现时不能只改 `TmdbHeaderView`，必须同时覆盖以上三个表面。

### 2.3 现有 YouTube 播放链路

工程已依赖 `NewPipeExtractor v0.26.3`，并在 `Source` 中注册 `Youtube` 提取器：

1. `Youtube.match` 接受 `youtube.com` 和 `youtu.be`；
2. `Youtube.fetch` 调用 `StreamInfo.getInfo`；
3. 提取器写入字幕和视频元数据，并返回直播 URL、progressive URL 或生成的 DASH MPD；
4. `Source.fetch` 继续执行 MPD 清理、YouTube 请求头、播放器内核适配和错误回退；
5. 最终由现有 `PlayerManager` 播放。

现有推送入口也已经能把任意 URL 包装为临时 `Vod`：

- `PushParser.of(url, title)` 生成安全的推送 ID；
- `SiteApi.pushDetail` 建立单线路 `Vod`；
- `VideoActivity.startDirect` 可绕过详情页跳转并带入明确的 flag、episode name 和 episode URL。

因此详情 UI 不应直接调用 `PlayerManager`，也不应预解析短期有效的媒体流 URL。正确做法是在点击时把标准 YouTube watch URL 交给现有播放入口。

## 3. 数据来源

### 3.1 TMDB 官方接口

首版使用以下官方接口：

- Movie：`GET /3/movie/{movie_id}/videos`
- TV series：`GET /3/tv/{series_id}/videos`
- TV season：`GET /3/tv/{series_id}/season/{season_number}/videos`
- TV episode：`GET /3/tv/{series_id}/season/{season_number}/episode/{episode_number}/videos`

TMDB 还允许在 movie、TV、season、episode 详情请求中通过 `append_to_response=videos` 合并子请求。首版建议在 `TmdbService` 中提供独立 videos 方法和独立缓存，原因是：

- 剧集切换时需要独立刷新 episode videos；
- 空结果需要负缓存；
- 视频失败不能污染核心详情缓存；
- 可以单独控制语言回退和 TTL；
- 更容易为接口和排序策略写单元测试。

官方文档：

- https://developer.themoviedb.org/reference/movie-videos.md
- https://developer.themoviedb.org/reference/tv-series-videos.md
- https://developer.themoviedb.org/reference/tv-season-videos.md
- https://developer.themoviedb.org/reference/tv-episode-videos.md
- https://developer.themoviedb.org/docs/append-to-response.md

### 3.2 为什么不在首版做 YouTube 搜索

不建议首版调用 YouTube 搜索，原因包括：

- YouTube Data API 需要额外 API key 和配额；
- NewPipe 搜索或页面解析更容易受上游变化影响；
- 仅凭中文、英文标题和集号搜索会产生误匹配；
- 搜索结果不是 TMDB 编辑过的关联关系；
- 结果排序难以稳定测试。

如果 TMDB 没有视频，首版直接隐藏相关视频区域。后续如确实需要，可增加明确关闭默认值的实验性 YouTube 搜索来源，并在 UI 上标注为搜索结果，而不是 TMDB 相关视频。

### 3.3 默认行为

首版不增加新的播放器设置项。只要现有 TMDB 详情能力已启用且匹配成功，就异步加载相关视频；结果为空时完全隐藏。该功能不改变播放器内核、解析策略、历史续播、字幕或画面设置的现有默认值。

## 4. 数据模型

新增轻量模型 `TmdbVideo`，建议字段：

```text
id
key
site
name
type
official
size
iso6391
iso31661
publishedAt
scope            TITLE / SEASON / EPISODE
seasonNumber
episodeNumber
```

派生属性：

- `identity`：`site + key`，用于去重；
- `watchUrl`：仅当 site 为 YouTube 时生成 `https://www.youtube.com/watch?v={key}`；
- `thumbnailUrl`：生成 YouTube 缩略图 URL，失败时显示本地播放占位图；
- `scopeLabel`：全剧、当前季、当前集；
- `displayType`：预告、先导、片段、花絮、幕后、回顾等本地化文案。

安全约束：

- 不直接使用响应中的任意 URL；
- 只接受大小写无关的 `YouTube` site；
- key 只允许字母、数字、下划线和连字符，并限制合理长度；
- name、type、语言和地区字段做长度上限与空值处理；
- 卡片和日志中不记录认证 token、Cookie 或解析后的临时媒体 URL。

## 5. 发现、合并与排序规则

### 5.1 Movie

只加载 title scope videos。

### 5.2 TV

根据当前播放或选中的剧集上下文加载：

1. 有明确 season 和 episode：加载 episode videos；
2. episode 结果为空或数量不足：加载 season videos；
3. 再加载 series videos 作为低优先级补充；
4. 没有明确剧集上下文：只加载 series videos。

合并后按 `site + key` 去重，保留最高精度 scope 的记录。

### 5.3 排序

稳定排序优先级：

1. scope：当前集高于当前季，高于全剧；
2. official 为 true 优先；
3. 语言：当前 TMDB 语言优先，其次英文，再其次其他语言；
4. 类型：Trailer、Teaser、Clip、Featurette、Behind the Scenes、Recap、其他；
5. 发布时间倒序；
6. TMDB id 作为最终稳定 tie breaker。

默认最多显示 12 条。空结果不显示标题和列表，也不显示错误占位卡。

## 6. 服务与缓存设计

在现有 `TmdbService` 中增加：

```text
movieVideos(item, config)
seriesVideos(item, config)
seasonVideos(item, seasonNumber, config)
episodeVideos(item, seasonNumber, episodeNumber, config)
relatedVideos(item, seasonNumber, episodeNumber, config)
```

`relatedVideos` 负责作用域回退、合并、过滤、去重和排序，UI 不感知接口细节。

缓存建议：

- key 包含 media type、TMDB ID、scope、season、episode、语言和当前 TMDB profile；
- 正常结果 TTL 为 6 小时；
- 空结果负缓存为 30 分钟；
- 失败不覆盖已有成功缓存；
- 切换 TMDB 匹配、手动季度绑定或 API profile 时清除对应 UI 状态，缓存仍按 key 隔离。

网络和解析失败只写 `tmdb-video` 分类日志并隐藏区域，不影响详情、选集和主视频播放。

## 7. 异步与生命周期

### 7.1 `TmdbUIAdapter`

新增：

- `List<TmdbVideo> relatedVideos`；
- 当前 videos context key；
- 独立 `relatedVideoGeneration`；
- `loadRelatedVideosAsync`；
- `getRelatedVideos`；
- `RefreshEvent.Type.VOD_RELATED_VIDEOS`。

触发时机：TMDB 核心详情成功后，当前 flag、season 或 episode 变化后，以及手动 TMDB 匹配和手动季度绑定生效后。

回调应用前必须同时校验主 load generation、related video generation、TMDB ID、season 和 episode context key，以及 Activity 生命周期。

### 7.2 `TmdbDetailActivity`

独立详情页不依赖 `TmdbUIAdapter`，在现有 `detailTasks` 上调用同一个 `TmdbService.relatedVideos`。

选中的 season 或 episode 变化时：

1. 立即清空旧 scope 卡片，避免短暂显示上一集视频；
2. 递增 generation；
3. 后台加载；
4. 仅当 bundle、season、episode 和 generation 仍一致时更新 UI。

快速切集时允许旧任务完成，但旧结果必须被 generation guard 丢弃。

## 8. UI 设计

新增独立视频卡片，不复用 `TmdbItem` 影片卡，避免点击语义和数据模型混淆。

卡片内容：

- 16:9 缩略图；
- 中央播放图标；
- 视频名称，两行省略；
- 类型和 scope 标签；
- official 标识可选；
- TV 遥控焦点边框与缩放效果遵循现有 TMDB 卡片规范。

### 8.1 独立 `TmdbDetailActivity`

新增 `TmdbVideoAdapter` 和横向 `relatedVideoList`，建议放在选集区域之后、剧照和演员之前。该内容与当前集上下文直接相关，比影片推荐更接近播放动作。

### 8.2 Mobile `VideoActivity`

在 `view_tmdb_header.xml` 增加相关视频标题与 RecyclerView，由 `TmdbHeaderView` 完成初次 bind、`refreshRelatedVideos`、点击播放和销毁清理。

### 8.3 Leanback `VideoActivity`

在 `app/src/leanback/res/layout/activity_video.xml` 增加独立 `HorizontalGridView`，使用新的 `TmdbVideoPresenter` 和 `ArrayObjectAdapter`。

必须同步处理上下焦点链、第一个和最后一个可见 TMDB 区块、空列表跳过、返回焦点、横向间距、row height 和销毁清理。

## 9. 播放入口

新增共享 `TmdbVideoPlayback`，只负责把经过验证的 `TmdbVideo` 转换为现有播放请求。

推荐流程：

```text
TmdbVideo
  -> PushParser.of(watchUrl, name)
  -> VideoActivity.startDirect(
       key = SiteApi.PUSH,
       id = parsed.id,
       name = parsed.name,
       pic = thumbnailUrl,
       mark = type 或 scopeLabel,
       playFlag = ResUtil.getString(R.string.push),
       playEpisodeName = name,
       playEpisodeUrl = watchUrl,
       resumeFromHistory = false)
  -> SiteApi.pushDetail
  -> Source.get().parse / Source.fetch
  -> Youtube.fetch(StreamInfo)
  -> PlayerManager
```

使用 `startDirect` 而不是普通 `start`，可保证不受详情页打开模式影响、不先进入另一层 TMDB 详情、明确选择唯一视频 episode、Mobile 和 Leanback 使用同一播放语义，并让返回键回到原详情或原播放页。

首版把相关视频视为独立短视频历史项，不继承正片进度，也不写回正片 episode 的播放位置。

## 10. 三个参考项目的取舍

| 项目 | 可借鉴内容 | 不直接采用的原因 |
|---|---|---|
| ytdlp-interface | 延迟解析、JSON 元数据、格式选择、Cookie 和 JS runtime 诊断、失败信息可见性 | Windows C++ GUI，通过子进程调用 `yt-dlp.exe` 的 `-j`、`-J`；Android 无法直接复用，二进制体积、ABI、更新和进程生命周期成本高；它也不负责 TMDB 关联发现 |
| LibreTube | `StreamInfo.getInfo`、`relatedItems` 映射、队列导航、Media3 播放与 PoToken 生命周期 | 当前工程已经使用 NewPipeExtractor 并有自己的播放器链路；直接移植会重复架构。算法 related streams 不等于 TMDB 相关视频。两边均为 GPLv3，许可证兼容，但仍应保留版权说明并避免无必要复制 |
| sabr-source-20260814 | 原生 `serverAbrStreamingUrl`、高码率和 HDR、SABR manifest、续播回退思路 | 是未带 Git 元数据和 LICENSE 的本地源码包；需要新模块、protobuf、JDK 21、PlayerManager 和 Source 补丁、OAuth、visitor data、PoToken；当前需求只需播放短视频，首版收益不足以覆盖集成风险 |
| 当前 webhtv | TMDB 匹配上下文、三套详情 UI、Push 临时 Vod、NewPipe YouTube 提取、双播放器内核与完整播放设置 | 已具备首版所需全部核心能力，应优先复用 |

结论：参考项目主要用于确认方向和边界，不应成为首版依赖。

## 11. 预期文件改动

### 新增

- `app/src/main/java/com/fongmi/android/tv/bean/TmdbVideo.java`
- `app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbVideoAdapter.java`
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbVideoPlayback.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbVideoPresenter.java`
- `app/src/main/res/layout/adapter_tmdb_video.xml`
- `app/src/test/java/com/fongmi/android/tv/service/TmdbVideoPolicyTest.java`
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbVideoPlaybackTest.java`

### 修改

- `TmdbService.java`
- `TmdbUIAdapter.java`
- `TmdbHeaderView.java`
- `TmdbDetailActivity.java`
- Mobile `VideoActivity.java`
- Leanback `VideoActivity.java`
- `activity_tmdb_detail.xml`
- `view_tmdb_header.xml`
- Leanback `activity_video.xml`
- strings 和必要的尺寸、颜色资源
- 现有 TMDB UI 和布局回归测试

如果实现时确认共享 launcher 可以直接调用两端已有且签名一致的 `VideoActivity.startDirect`，则 Mobile 和 Leanback `VideoActivity.java` 不需要业务修改，只需增加或调整测试。

## 12. 测试计划

### 12.1 纯单元测试

- TMDB video JSON 正常解析；
- 非 YouTube site 和非法 key 被过滤；
- episode、season、series 合并与去重；
- scope、official、语言、type、时间排序稳定；
- 空结果和负缓存；
- generation 或 context 不一致时拒绝旧结果；
- `TmdbVideoPlayback` 生成 `SiteApi.PUSH` 和明确 episode URL；
- 正片历史 key 不被相关视频复用。

### 12.2 UI 源码与布局回归

- 三个 UI 表面都存在独立 related video row；
- 空列表时标题和列表均隐藏；
- Mobile 切集刷新正确；
- Leanback 上下焦点不会落到隐藏 row；
- 专用卡片点击不是 `TmdbNavigation.open`，而是播放 launcher；
- 现有影片推荐点击行为保持不变。

### 12.3 Flavor 验证

```text
:app:testMobileArm64_v8aDebugUnitTest
:app:testLeanbackArm64_v8aDebugUnitTest
:app:assembleMobileArm64_v8aDebug
:app:assembleLeanbackArm64_v8aDebug
```

### 12.4 运行时验收

至少覆盖：Movie 有 trailer、TV 只有 series video、season 有 video、episode 有 video、TMDB 无 video、YouTube 不可用、快速连续切集、Mobile 触屏、Leanback 遥控焦点和返回、Exo 与 MPV 两个内核。

验收标准：

- 有数据时三处 UI 均显示正确内容；
- 无数据或失败时不影响详情与正片播放；
- 点击后不打开浏览器和外部 YouTube App；
- 在现有 `VideoActivity` 中开始播放；
- 返回后原页面上下文和焦点可恢复；
- 不把相关视频进度写入正片剧集历史。

## 13. 分阶段实施

### Phase 1：数据与播放闭环

- `TmdbVideo`；
- TMDB 四级 videos API；
- 过滤、排序、缓存；
- `TmdbVideoPlayback`；
- 单元测试。

### Phase 2：三套 UI

- 独立详情页；
- Mobile TMDB header；
- Leanback grid、presenter 和焦点链；
- 异步 generation guard。

### Phase 3：运行时验证与可选增强

- 真机或模拟器验收；
- 缩略图 fallback；
- 埋点和错误分类；
- 根据真实失败率再决定是否引入 SABR 作为受控 fallback。

## 14. 风险与明确非目标

首版不运行 yt-dlp 子进程，不捆绑 Python、QuickJS、Deno 或 yt-dlp 二进制，不接 YouTube Data API，不做任意关键词 YouTube 搜索，不复制 LibreTube 播放服务和队列，不引入 SABR 模块，不自动加入正片播放队列，不把相关视频当作正片 episode，也不改变现有播放器默认内核和设置。

主要风险：TMDB 某些条目没有视频；YouTube 视频可能下架、地区限制或需要登录；YouTube 页面和 NewPipeExtractor 会持续变化；三套 UI 的焦点和刷新逻辑容易出现只修一端的回归。

应对方式是保持 TMDB 发现与 YouTube 播放解耦、点击时解析、失败静默降级、三端共享模型和服务、Mobile 与 Leanback 同步测试。

## 15. 最终建议

按上述 Phase 1 和 Phase 2 实施。首版只使用 TMDB 官方相关视频元数据和当前项目已经存在的 YouTube 播放能力，可以以较小改动实现完整闭环，并且不会把 ytdlp、LibreTube 或 SABR 的维护成本带入主工程。
## 16. 参考来源

- 当前仓库：`F:/Workspace/webtv3/webhtv`，基线 `731baa2483`
- ytdlp-interface：https://github.com/ErrorFlynn/ytdlp-interface，快照 `d5293db92c208cf1a6e2701dd920ee1aaba5fb2f`
- LibreTube：https://github.com/libre-tube/LibreTube，快照 `29a5eeb65de8382021f9161dab64f90524b53e50`
- SABR 本地源码包：`G:/下载/sabr-source-20260814`，无 Git 元数据和独立 LICENSE
- TMDB 官方 API：第 3.1 节列出的官方 markdown 文档
