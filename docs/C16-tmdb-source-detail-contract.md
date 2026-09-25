# C16：T3/T4 详情内嵌 TMDB 元数据设计

> 状态：C16 基础协议阶段 1-6 已完成并通过模拟器验收；无 TMDB Key 的“源内嵌数据驱动详情模式”第 18 节已按阶段 A-F 实现，并通过 Mobile/Leanback 设备验收。第 19 节的临时 Key 主合同已按订阅配置接口根对象修订并实施：根字段剥离、订阅 epoch、用户优先、官方 HTTPS 白名单、旧异步隔离、source-first/fill-only 接线和双端设备验收均已完成。未提供真实第三方 TMDB Key，因此真实网络补齐成功及真实 401/403 仍为未验证；T4 服务端仍需在其独立仓库按本文合同实施。不得据此宣称“发布完成”。

## Recovery anchor

- 目标：扩展现有详情返回协议，使 T3 客户端爬虫和 T4 服务端接口可在 `detailContent` 结果中直接携带 TMDB 数据；APP 优先采用身份匹配的源数据，仅为缺失能力按需访问 TMDB。没有 App 配置的 TMDB Key 时，可把**订阅配置接口响应根对象**的 `tmdb_api_key` 放入只属于当前订阅接口的内存凭据作用域；详情响应根对象中的同名字段只用于向后兼容剥离，不再作为凭据来源。切换订阅接口时先清空旧 Key，新接口配置有 Key 才加载，没有则保持为空；Key 只允许在当前接口内跨站点、页面、详情和 Activity 使用，不得落盘、进入缓存/Parcel/日志或覆盖用户配置。
- 基线：WebHTV `dev4@32a52698e5dab09fe18e49d18849a947057ca717`；OmniBox `main@d57b3e6672337febd46feca0d8ad59c6a2b507c3`；alist-tvbox `master@8a222f69a80291e836db702c34daf1ed5bdd5630`；atv-player `master@09feed1d5e5102f13bf91c9fbb76ea92808cb76d`。
- 范围：实现合同；阶段 1-6 已完成本仓库 APP 侧协议、解析、合并、详情接入、延迟能力和设备验收。第 19 节临时源 Key 已完成订阅配置根级接收、详情 legacy 字段剥离、作用域、有效配置、source-first 补齐和设备验收。
- 回滚：删除本文档并撤销总评估索引中的 C16 条目即可。
- 追加结论：alist-tvbox 适合作为 T4 的元数据持久化和图片访问参考，但当前 `/vod` 输出仍是平铺 `vod_*` 字段；atv-player 适合作为 APP 的字段级合并、季级身份、缓存和异步取消参考，不能直接作为 Android 协议实现。
- 当前进展：阶段 1-5 已完成客户端协议、纯逻辑、源缓存、source-first 详情接入和延迟能力；阶段 6 已在 `HD1910/Android 9` 模拟器完成 Mobile 与 Leanback 验收。第 18 节阶段 A-E 已完成纯策略、设置解耦、独立页、原生增强和交互收口；阶段 F 已在无 Key Mobile/Leanback 设备完成验收。第 19 节原实现和订阅配置根级修订均已通过 JVM 回归与分配设备 `SM-N9700/Android 9 @ 192.168.50.3:5557` 的双端矩阵验证。
- 下一动作：客户端无待办；T4 生产服务的版本门控、Key 轮换、真实 Key 验证与双端回归需在独立仓库继续。

## 1. 设计结论

推荐在现有详情结果的 `Vod` 对象中增加可选 `tmdb` 字段，而不是照搬 OmniBox 的独立元数据请求接口。

核心规则：

1. 保持 `detailContent(ids)` 方法签名和顶层 JSON 不变，旧源和旧客户端继续兼容。
2. T3 与 T4 返回完全相同的数据合同，不在 APP 中按来源类型分叉。
3. `tmdb.id + tmdb.media_type` 是剧集级身份锚点；若详情处于选中季上下文，还必须包含 `season_number`。
4. APP 按能力组判断缺口。源数据完整时不得连接 TMDB；部分缺失时只补所需能力。
5. 合并采用 fill-only，源提供的合法非空字段优先，网络结果不得覆盖它们。
6. 空值默认表示未知。只有能力组列入 `complete` 时，空数组才表示明确为空。
7. 季、集、视频以及更多推荐等延迟能力只在用户实际访问时补齐。

## 2. 两项目现状分析

### 2.1 WebHTV 当前链路

`SiteViewModel.detailContent()` 最终进入 `SiteApi.detailContent()`：

- T3 调用爬虫的 `detailContent(List<String> ids)`，再由 `Result.fromJson()` 解析。
- T4 以 `ac=detail`、`ids=<id>` 等参数调用服务端，再由 `Result.fromType()` 解析。
- 两种来源随后统一进入 `Result.list<Vod>`、播放线路解析和 `VodDetailCache`。
- 当前 `Vod` 只有传统详情和播放字段，没有结构化 TMDB 数据。
- TMDB 详情模式当前通过 `TmdbService` 独立访问 TMDB，详情请求覆盖图片、演职员、翻译、外部 ID、视频、推荐和相似内容；电视内容另有季与单集请求。
- `TmdbDetailPrefetch` 使用 `tmdbId + mediaType` 标识请求并负责取消与身份隔离。

因此，扩展点应是 `Vod` 的结构化可选字段，并由统一仓库层消费，不能让 Activity 或 T3/T4 各自实现合并逻辑。

### 2.2 OmniBox 可借鉴逻辑

OmniBox 固定提交 `d57b3e6672337febd46feca0d8ad59c6a2b507c3` 使用 `TMDBMetadata` 容器保存资源身份、刮削类型、`scrapeData.tmdb`、更新时间和视频映射。`TMDBDetails` 包含 ID、电影/剧集名称、海报、背景、简介、评分、日期、类型、演职员和剧集信息。页面异步获取元数据，存在时显示增强内容，不存在时退回普通源详情。

可复用部分是结构化对象、媒体类型、资源映射及页面降级。不能直接照搬的部分是：

- 独立元数据接口会在详情之后增加一次请求，不符合源随详情直接返回的目标。
- OmniBox 字段不足以覆盖 WebHTV 的图片集、外部 ID、视频、推荐、相似内容和季集分层能力。
- 整体 `hasData()` 不能表达“已有核心信息但缺少演职员”这类局部缺口。

### 2.3 alist-tvbox：T4 服务端参考

固定版本 `8a222f69a80291e836db702c34daf1ed5bdd5630` 是 AList/TVBox 服务端。它的相关实现分成两条链：

- `src/main/java/cn/har01d/alist_tvbox/dto/TmdbDto.java:9-29` 和 `TmdbCredits.java:7-11` 将 TMDB 的 ID、电影/剧集标题、简介、类型、语言、国家、演职员、海报、评分和日期收敛为服务端 DTO。
- `src/main/java/cn/har01d/alist_tvbox/entity/Tmdb.java:20-38` 保存较窄的 TMDB 媒体快照；`TmdbMeta.java:23-40` 将本地路径、站点和 TMDB 记录关联起来。
- `src/main/java/cn/har01d/alist_tvbox/entity/MediaMetadata.java:15-53` 是更接近本需求的实现：以 `provider + metaId + season` 唯一索引保存 `status`、完整 JSON `payload` 和 `fetchTime`，用于完结剧长期零网络、在播剧按状态/TTL 刷新。
- `src/main/java/cn/har01d/alist_tvbox/dto/MetadataDetails.java:7-62` 已覆盖简介、季总数、集数、播出状态、别名、分集、评分、外部 ID、播放链接、导演、编剧、演员、背景图和背景图候选，比单纯 `TmdbDto` 更适合作为服务端快照字段清单。
- `src/main/java/cn/har01d/alist_tvbox/service/metadata/MetadataService.java:93-214` 实现“持久层 -> provider 内存/网络 -> 持久化”的读路径；旧快照或坏快照视为过期，失败的空对象不覆盖已有快照。
- `src/main/java/cn/har01d/alist_tvbox/service/TmdbEndpoint.java:18-32,126-174` 处理 TMDB API/图片镜像、Worker 池、URL 安全和 API key/Bearer token 隔离。
- `/vod` 详情入口位于 `web/TvBoxController.java:66-103`，最终由 `TvBoxService` 返回 `MovieDetail`；`tvbox/MovieDetail.java:11-35` 没有 `tmdb` 对象。`TvBoxService.java:2687-2768` 将 TMDB 记录映射回 `vod_pic`、`vod_year`、`vod_remarks`、`vod_actor`、`vod_director`、`vod_area`、`type_name`、`vod_lang` 和 `vod_content`。

因此，alist-tvbox 对 C16 的结论是“**服务端生产和快照设计有帮助，输出协议不能直接复用**”：它证明 T4 可以在服务端预抓取并持久化 TMDB，且季必须进入缓存键；但若只照搬其当前 `/vod` 平铺映射，APP 无法区分源字段和 TMDB 字段，也无法表达局部能力完整性。C16 应保留 `Vod.tmdb` 结构化对象，同时允许 T4 内部采用 `provider + tmdbId + season` 的持久化键。

### 2.4 atv-player：客户端参考

固定版本 `09feed1d5e5102f13bf91c9fbb76ea92808cb76d` 是桌面客户端，当前 HEAD 包含完整 metadata 子系统；TMDB Worker 轮询池提交 `e33fc859752f46547b992ecfffab7f5e48b902e1` 不是 HEAD 的祖先，因此本评估不把该提交的轮询实现当作冻结基线，只采纳 HEAD 中已存在的 provider、缓存和详情代码。

可借鉴证据：

- `src/atv_player/metadata/models.py:14-99` 将查询、匹配和详情记录分层；`MetadataRecord` 明确保存标题、原始标题、年份、海报、背景图候选、简介、评分、演员/导演、角色/演职员详情、类型、别名、季集、IMDb/TMDB ID 和详情字段。
- `src/atv_player/metadata/providers/tmdb.py:39-52,880-925` 将电视剧季号编码进 provider ID（`<tmdbId>:season:<n>`），并从 TMDB 原始响应保留 `external_ids`、`images`、cast/crew 角色、别名、季简介和背景图候选；`get_detail_full` 在 `:927-958` 进一步补充季/集和剧集级统计字段。
- `src/atv_player/metadata/merge.py:13-73,140-185` 采用按字段的 provider 优先级，而不是整条记录覆盖；简介还区分 `tmdb` 与 `tmdb_season`，说明剧集总简介和选中季简介不能混作一个无上下文字符串。
- `src/atv_player/metadata/cache.py:48-102` 对 provider/detail ID 做 TTL 缓存并使用 SHA-256 文件键；空结果支持更短 TTL。`cache_key.py:6-25` 在已有 TMDB 外部锚点时跳过标题搜索。
- `src/atv_player/metadata/async_runner.py:17-54` 将 provider 搜索/详情封装为可并行、可捕获单 provider 异常的任务；详情控制器在 `controllers/media_detail_controller.py:509-539,584-620,667-729` 以 TMDB 记录为基础再加载其他 provider，并按当前视图身份清理缓存。
- 测试 `tests/test_metadata_cache.py:26-101` 覆盖详情往返、空结果短 TTL 和通用 payload；`tests/test_metadata_cache_key.py:6-25` 覆盖 TMDB 外部锚点；`tests/test_media_detail_page_ui.py:25-67` 使用 `tv:1399:season:1` 和角色、评分、IMDb/TMDB ID、集列表验证详情展示。

因此，atv-player 对 C16 的结论是“**客户端消费机制有帮助，数据模型不直接移植**”：WebHTV 应引入字段级缺口规划、季级身份和缓存隔离，但仍以本项目已有 `TmdbService`、`TmdbDetailPrefetch`、Gson/Parcelable 和 Android 生命周期为实现边界。其 provider 优先级不能原样变成 WebHTV 的覆盖规则，因为用户要求源内嵌 TMDB 字段优先于 APP 网络补齐；在 C16 中它只作为“网络补齐内部不同 provider 的次级策略”参考。

## 3. 方案比较

### 3.1 保持现状

APP 继续自行匹配并请求 TMDB。兼容风险最低，但无法满足源数据直用，会继续产生重复请求、凭据依赖和匹配误差，不推荐。

### 3.2 复制 OmniBox 独立接口

模型清晰，但 T3 难以统一实现，而且页面仍需额外网络往返，不推荐。

### 3.3 在 Vod 中增加可选 tmdb 对象

T3/T4 现有响应、解析、缓存和页面入口不变；新客户端识别该字段，旧客户端忽略未知字段。APP 按能力组定向补齐，兼容性和可回滚性最佳，推荐。

## 4. 统一返回协议

在详情结果的第一个 `Vod` 中增加可选 `tmdb`：

```json
{
  "list": [{
    "vod_id": "source-item-id",
    "vod_name": "示例剧集",
    "vod_play_from": "线路一",
    "vod_play_url": "第1集$https://example/1.m3u8",
    "tmdb": {
      "schema": 1,
      "id": 1399,
      "media_type": "tv",
      "language": "zh-CN",
      "fetched_at": "2026-09-19T00:00:00Z",
      "complete": ["core", "credits", "images"],
      "detail": {}
    }
  }]
}
```

字段约束：

- `schema`：协议主版本，首版固定为整数 `1`。
- `id`：正整数 TMDB ID。
- `media_type`：仅允许 `movie` 或 `tv`。
- `season_number`：可选非负整数；仅 `tv` 使用，表示选中季上下文。电影固定为 0。
- `language`：可选，表示数据主要语言，例如 `zh-CN`。
- `fetched_at`：可选 ISO 8601 UTC 时间，只用于新鲜度判断。
- `complete`：可选能力组数组，表示生产者确认该组完整，即使内容为空。
- `detail`：保持 TMDB v3 的 snake_case JSON 形状，生产者只需返回实际拥有的字段。

### 4.1 detail 支持字段

首版支持当前 TMDB 详情模式使用的字段：

- 身份与标题：`id`、`title`、`name`、`original_title`、`original_name`。
- 摘要与状态：`overview`、`tagline`、`status`。
- 图片：`poster_path`、`backdrop_path`、`images`。
- 日期与评分：`release_date`、`first_air_date`、`vote_average`、`vote_count`。
- 分类与地区：`genres`、`origin_country`、`original_language`。
- 时长：电影 `runtime`，电视 `episode_run_time`。
- 电视：`number_of_seasons`、`number_of_episodes`、`seasons`。
- 扩展：`external_ids`、`credits`、`videos`、`recommendations`、`similar`。

图片字段可为 TMDB 相对路径或绝对 HTTPS URL。绝对 URL 原样使用；以 `/` 开头的路径由 APP 使用当前图片基址和尺寸策略拼接；其他形式无效。源不能通过该对象提供 TMDB API 地址、Authorization 或请求头。

### 4.2 季与集

`seasons` 可内嵌已知季；季对象可带 `episodes`。单集沿用 TMDB 字段，如 `id`、`season_number`、`episode_number`、`name`、`overview`、`air_date`、`still_path`、`runtime`、`guest_stars` 和 `crew`。

当详情对应“某一选中季”而不是整部剧时，`season_number` 参与缓存和合并身份；不能只用剧集级 `id + media_type`，否则不同季的简介、集列表或海报可能互相污染。等价身份为 `id + media_type + season_number(无则 0)`。

只有 `complete` 包含 `season:1` 时，该季空 `episodes` 才表示确认无数据；否则用户进入该季时仍可按需补齐。单集完整声明使用 `episode:1:1`。

### 4.3 能力组

首版定义：

- `core`
- `credits`
- `images`
- `external_ids`
- `videos`
- `recommendations`
- `similar`
- `season:N`
- `season_videos:N`
- `episode:N:E`
- `episode_videos:N:E`

推荐和相似内容的 `complete` 只表示首批结果完整，不表示所有分页已加载。

## 5. 缺失、空值与合并语义

- 字符串缺失、`null` 或空白均视为缺失。
- ID 必须大于 0。
- 分数必须有限且在合理范围内。未声明完整时零分视为未知；完整组中的零分可表示真实值。
- 对象类型错误时只丢弃该字段，不得使普通详情失败。
- 非空数组可直接采用；空数组只有在相应组包含于 `complete` 时才表示明确为空。
- 图片加载失败只尝试同组候选或普通 `Vod` 图片，不因此自动发起 TMDB 元数据请求。

同一字段优先级：

1. 身份匹配且合法的源内嵌字段。
2. 本地缓存中同 `id + media_type + season_number + language` 的有效字段。
3. 本次按需请求 TMDB 得到的字段。
4. 普通 `Vod` 的标题、封面和简介回退。
5. UI 默认值。

合并必须为 fill-only。用户刷新来源时重新执行详情函数；用户刷新 TMDB 时只更新非源字段。

身份最小校验：`schema == 1`、`id > 0`、媒体类型合法；若 `detail.id` 存在，必须与外层 ID 一致。强冲突时拒绝增强对象，继续显示普通详情并使用现有 TMDB 匹配流程。

## 6. APP 请求规划

### 6.1 首屏

1. 解析并验证 `tmdb`。
2. 在启动 `TmdbDetailPrefetch` 之前计算当前页面所需能力组。
3. 所需字段完整时直接渲染，TMDB 请求数必须为 0。
4. 部分缺失且用户 TMDB 配置可用时，复用现有详情请求，并只填缺口。
5. 只有合法 ID 时跳过标题搜索，直接按 ID 补齐。
6. ID 缺失或身份无效时维持现有匹配流程。
7. TMDB 未配置或补齐失败时，保留源增强数据与普通详情，不把增强失败升级为整页失败。

### 6.2 延迟能力

- 选择某季时检查 `season:N`。
- 打开单集扩展信息时检查 `episode:N:E`。
- 打开视频区时检查对应视频组。
- 推荐和相似内容先用内嵌首批结果，加载更多继续走现有分页。
- 页面关闭、切源或身份变化时继续使用现有取消与 identity 机制，旧请求不得覆盖新页面。

内嵌数据不依赖 APP 的 TMDB API Key。没有凭据时仍应完整显示已有源数据，只是不补缺口。

## 7. 数据模型与代码边界

批准实施后建议新增：

- `Vod.tmdb: TmdbSourcePayload`
- `TmdbSourcePayload`：版本、身份、语言、时间、完整组及详情对象
- `TmdbCapabilityPlanner`：纯函数计算缺口
- `TmdbSourceMerger`：校验、图片规范化和 fill-only 合并
- `TmdbDetailRepository`：统一源数据、本地缓存与网络补齐

同步覆盖 Gson、`Vod` Parcelable 或替代的大对象传递策略、`Result` 复制路径、`VodDetailCache`、页面生命周期和缓存键。Activity 不直接解析 JSON，源对象也不能未经校验写入 `TmdbService` 缓存。

## 8. 缓存、安全与负载

- 详情缓存继续保存整个 Result，旧缓存没有 `tmdb` 时按旧逻辑工作。
- TMDB 补齐缓存键必须包含 `id + media_type + season_number + language`，不能让同一剧不同季共享详情或简介。
- T4 内部持久化可采用 `provider + tmdbId + season_number` 唯一键，并保存状态、抓取时间和 JSON 快照；完结数据可长 TTL，在播数据按状态刷新。网络失败或空白快照不得覆盖已有有效快照。
- 合并后保留字段来源归属，防止缓存命中后误覆盖源字段。
- `fetched_at` 只影响刷新建议，不阻止旧数据先展示。
- APP 补齐只能使用用户当前 TMDB 配置，禁止源控制 API 地址、图片基址或凭据。
- 建议限制 `tmdb` 对象为 2 MiB、单数组 500 项、单字符串 64 KiB；超限时局部丢弃增强组，不影响播放线路。
- 日志只记录站点 key、Vod ID、TMDB ID、组命中和拒绝原因，不记录凭据或完整原始内容。

## 9. T3 与 T4 生产者指南

### 9.1 T3

继续返回原有 `detailContent(List<String> ids)` JSON，只在目标 Vod 内增加 `tmdb`。不新增回调，也不要求宿主向爬虫提供 TMDB 凭据。Java、JS 和 Python 桥接均将其作为普通 JSON 字段。

### 9.2 T4

继续响应现有 detail 请求。有合法 TMDB 数据时内嵌相同合同；没有时省略 `tmdb`，不得返回伪造 ID 或滥用 `complete`。

只有身份数据也有价值：

```json
"tmdb": {
  "schema": 1,
  "id": 1399,
  "media_type": "tv",
  "detail": {}
}
```

完整首屏对象应准确声明已有能力。若 `credits`、`images`、`videos`、`recommendations` 或 `similar` 已列入 `complete`，其中的空数组就是确认为空，APP 不再访问 TMDB。

T4 内部若有持久化元数据，建议按 `provider=tmdb、metaId=id、season=season_number` 保存，再在 `/vod` 的 `MovieDetail`/详情 `Vod` 序列化阶段组装为 `tmdb`。不能仅把 `Tmdb` 表的 `cover/score/actors` 平铺字段当作协议，因为这会丢失来源、季上下文、能力组和空值语义。

## 10. 收益、风险与取舍

收益：完整数据可实现零 TMDB 请求，降低延迟和凭据依赖；合法 ID 可避免标题误匹配；部分数据可增量补齐；旧源和普通详情兼容。

主要风险及控制：

- 错误 ID：严格身份校验，冲突时降级。
- 大型季集数据：能力组、延迟加载和大小上限。
- 空数组歧义：使用 `complete`。
- 文案或图片差异：源优先，fill-only。
- 数据陈旧：记录语言和抓取时间，时间敏感组按访问场景判断。
- T3/T4 分叉：两端只生产同一合同，APP 集中解析和消费。

## 11. 分阶段实施计划

### 阶段 1：协议和纯逻辑

新增模型、能力组、校验与合并器；给 `Vod` 增加可选字段；覆盖 Gson、Parcelable、复制和缓存合同测试。

### 阶段 2：详情模式接入

在预取前规划缺口；完整首屏直接渲染；部分缺失复用现有 `TmdbService.detail()`；保持取消和身份隔离。

### 阶段 3：季集与延迟组

接入季、集和各级视频组；推荐/相似首批内嵌，分页保持现状；补充诊断和生产者文档。

三个阶段独立可回滚。本轮不授权任何阶段实现。

## 12. 验收矩阵

1. 无 `tmdb` 的旧 T3/T4 响应与现状一致。
2. T3/T4 返回相同 JSON 时解析结果和请求计划一致。
3. 完整首屏打开详情时 TMDB 请求数为 0。
4. 只缺 `credits` 时最多发一次补齐请求，源字段不被覆盖。
5. 只有合法 ID 时跳过搜索并直接按 ID 补齐。
6. ID、媒体类型或 `detail.id` 冲突时拒绝增强对象，普通详情和播放仍可用。
7. 未声明完整的空数组触发按需补齐；已声明完整的空数组不联网。
8. 未配置 TMDB Key 时仍展示全部源增强数据。
9. 绝对图片 URL、相对路径和非法路径分别按合同处理。
10. 详情缓存往返后字段、来源归属和 `complete` 不丢失，旧缓存可读。
11. Parcelable 或复制后身份与组不丢失，且不产生超大 Binder 传输。
12. 切源、快速退出或切换条目时，旧请求不能覆盖新页面。
13. 电视内容只在打开缺失季或集时访问对应接口。
14. 超限或类型错误 payload 被局部拒绝，普通详情和播放线路不受影响。

最小实机验证：移动端和电视端各测试一条完整 T3、一条部分 T4 和一条无 TMDB 的旧源，记录首屏请求数、字段渲染、季集访问和切源隔离。

## 13. 证据与边界

- WebHTV 基线 `32a52698e5dab09fe18e49d18849a947057ca717`：`SiteViewModel`、`SiteApi`、`Vod`、`TmdbService`、`TmdbDetailPrefetch` 证明 T3/T4 已汇聚到 Result/Vod，并说明当前 TMDB 请求和生命周期边界。
- OmniBox 基线 `d57b3e6672337febd46feca0d8ad59c6a2b507c3`：`TMDBMetadata`、`TMDBDetails`、服务端元数据处理和页面消费证明结构化元数据及降级方式，也显示独立请求模型不满足本需求。
- alist-tvbox `8a222f69a80291e836db702c34daf1ed5bdd5630`：`dto/TmdbDto.java`、`dto/MetadataDetails.java`、`entity/MediaMetadata.java`、`service/metadata/MetadataService.java`、`service/metadata/TmdbMetadataProvider.java`、`service/TmdbEndpoint.java`、`web/TvBoxController.java` 和 `service/TvBoxService.java`。证据等级 A/B：实际源码和服务端测试；支持“服务端可按季持久化 TMDB 快照、失败不覆盖旧值、图片/凭据应隔离”，同时证明其当前 `/vod` 是平铺 `vod_*` 输出而非 C16 结构化合同。
- atv-player `09feed1d5e5102f13bf91c9fbb76ea92808cb76d`：`metadata/models.py`、`metadata/providers/tmdb.py`、`metadata/merge.py`、`metadata/cache.py`、`metadata/cache_key.py`、`metadata/async_runner.py`、`controllers/media_detail_controller.py` 及 `tests/test_metadata_cache.py`、`test_metadata_cache_key.py`、`test_media_detail_page_ui.py`。证据等级 A/B：实际源码和测试；支持“客户端应按字段合并、按季隔离身份、缓存外部锚点、异步任务隔离旧视图”。TMDB Worker 轮询提交 `e33fc859752f46547b992ecfffab7f5e48b902e1` 非该 HEAD 祖先，未纳入采用范围。
- 外部 PR、Issue、论文和性能基准不适用：本阶段只设计兼容 JSON 合同，不升级依赖、不改变运行行为，也不提出未经测量的性能结论。

## 14. 最终建议

**建议实施** `Vod.tmdb` 可选对象、TMDB 原生字段形状、能力组完整声明及 fill-only 合并；增加 `season_number` 上下文和 `id + media_type + season_number + language` 缓存键。吸收 alist-tvbox 的服务端按季快照、状态/TTL、失败不覆盖和图片/凭据隔离，吸收 atv-player 的字段级合并、外部 ID 直达、季级 provider ID、空结果短 TTL 和异步隔离。不要增加独立 T3/T4 元数据接口，不要在数据完整时连接 TMDB，也不要让源控制 APP 的 TMDB 地址或凭据。

## 15. 实现合同

本节是实现的唯一执行依据。若本节与前文概念描述冲突，以本节为准；未列入本节的 TMDB 字段不得成为首版的必需字段。

### 15.1 WebHTV 修改文件清单

首版客户端实现限定在以下路径：

| 文件 | 必须改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/bean/Vod.java` | 增加可选 `TmdbSourcePayload tmdb`，Gson 解析、getter/setter、Parcelable、`isSameContent` 参与内容比较 |
| `app/src/main/java/com/fongmi/android/tv/bean/TmdbSourcePayload.java` | 新增协议容器、校验、能力组、来源标记和深拷贝 |
| `app/src/main/java/com/fongmi/android/tv/bean/TmdbSourceDetail.java` | 新增 TMDB 原始详情包装；内部 `JsonObject` 以 JSON 字符串保存，不直接放进 Parcelable |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourcePayloadParser.java` | 新增大小限制、身份校验、字段类型校验和图片 URL 规范化 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceCapabilityPlanner.java` | 新增能力组判定和缺口计划，必须是无 Android UI 副作用的纯逻辑 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceMerger.java` | 新增 source/cache/network 的字段级 fill-only 合并 |
| `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java` | 不改变 T3/T4 请求；仅确保解析后的 `Vod.tmdb` 随 Result 缓存返回 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | 重排 `loadContent` 为先取源详情、再决定 TMDB 请求；增加 payload 到现有 `TmdbBundle` 的适配 |
| `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java` | 仅补充缺口请求入口/缓存键；保留现有请求 URL 和认证策略 |
| `app/src/main/java/com/fongmi/android/tv/utils/VodDetailCache.java` | 不改变源详情键；如新增 TMDB 合并缓存，单独使用带季和语言的键，不把网络合并结果写回源内容缓存 |

不允许在首版修改 `catvod/`、`quickjs/`、`chaquo/` 的爬虫 ABI，也不允许新增 TMDB 独立 HTTP 接口。

### 15.2 Java 模型字段

`TmdbSourcePayload` 固定字段如下：

| 字段 | Java 类型 | JSON | 规则 |
| --- | --- | --- | --- |
| `schema` | `int` | `schema` | 仅接受 `1` |
| `tmdbId` | `int` | `id` | `> 0` |
| `mediaType` | `String` | `media_type` | 仅 `movie`/`tv`，统一小写 |
| `seasonNumber` | `int` | `season_number` | `tv` 可为 `0` 或正数；`movie` 必须为 `0` |
| `language` | `String` | `language` | 空值规范为 `""` |
| `fetchedAt` | `String` | `fetched_at` | 仅校验长度和 ISO-8601 形状，不参与身份 |
| `complete` | `Set<String>` | `complete` | 未知组丢弃；保留合法组并去重 |
| `detailJson` | `String` | `detail` | 保存 TMDB snake_case JSON；默认 `{}` |
| `sourceKind` | `String` | 不出现在协议 | `source_tmdb` / `cache_tmdb` / `remote_tmdb`，仅 APP 内部使用 |

`Vod` 中的字段声明为 `@SerializedName("tmdb") private TmdbSourcePayload tmdb;`，不添加 Simple XML `@Element`，因此 XML 详情自然忽略该扩展。旧 JSON、旧缓存和没有 `tmdb` 的源必须得到 `null`。

`TmdbSourceDetail` 不把所有 TMDB v3 字段复制成 Java 成员；保留 `JsonObject` 字符串并提供以下白名单读取器：`id`、标题、简介、海报、背景图、日期、评分、类型、地区、时长、`images`、`credits`、`aggregate_credits`、`external_ids`、`videos`、`recommendations`、`similar`、`seasons`、`episodes`、`translations`、`content_ratings`、`release_dates`。读取器遇到类型错误返回空值，不抛出到详情页。

### 15.3 JSON 规范与完整示例

T3/T4 只能在详情 `list[0]` 中返回 `tmdb`。搜索列表不返回该字段，播放接口也不返回该字段。`detail` 必须是 JSON object；不能是字符串、数组或 TMDB API 响应外层包装。

电影完整示例：

```json
{
  "list": [{
    "vod_id": "movie-1",
    "vod_name": "示例电影",
    "vod_pic": "https://source.example/poster.jpg",
    "vod_content": "源详情简介",
    "vod_play_from": "线路一",
    "vod_play_url": "正片$https://source.example/movie.m3u8",
    "tmdb": {
      "schema": 1,
      "id": 550,
      "media_type": "movie",
      "season_number": 0,
      "language": "zh-CN",
      "complete": ["core", "credits", "images", "external_ids", "videos", "recommendations", "similar"],
      "detail": {
        "id": 550,
        "title": "Fight Club",
        "original_title": "Fight Club",
        "overview": "...",
        "poster_path": "/poster.jpg",
        "backdrop_path": "/backdrop.jpg",
        "release_date": "1999-10-15",
        "vote_average": 8.4,
        "vote_count": 28000,
        "genres": [],
        "credits": {"cast": [], "crew": []},
        "external_ids": {"imdb_id": "tt0137523"},
        "videos": {"results": []},
        "recommendations": {"page": 1, "results": []},
        "similar": {"page": 1, "results": []}
      }
    }
  }]
}
```

电视剧选中季示例：

```json
{
  "list": [{
    "vod_id": "show-1",
    "vod_name": "示例剧集",
    "vod_play_url": "S01E01$https://source.example/1.m3u8",
    "tmdb": {
      "schema": 1,
      "id": 1399,
      "media_type": "tv",
      "season_number": 1,
      "language": "zh-CN",
      "complete": ["core", "credits", "images", "external_ids", "season:1"],
      "detail": {
        "id": 1399,
        "name": "Game of Thrones",
        "original_name": "Game of Thrones",
        "overview": "...",
        "first_air_date": "2011-04-17",
        "vote_average": 8.4,
        "number_of_seasons": 8,
        "number_of_episodes": 73,
        "seasons": [{"season_number": 1, "episode_count": 10, "episodes": []}],
        "aggregate_credits": {"cast": [], "crew": []},
        "external_ids": {"imdb_id": "tt0944947"}
      }
    }
  }]
}
```

`season:1` 的完整语义是 `seasons[]` 中第 1 季对象、`episodes` 数组和该季需要的 `images/credits` 均已由生产者确认；如果只返回季摘要而没有集列表，不能声明 `season:1`，只能返回 `core`。

### 15.4 能力组到字段和请求映射

| 能力组 | 最低字段/条件 | APP 现有请求 | 首屏还是延迟 |
| --- | --- | --- | --- |
| `core` | `id`、标题、简介、日期、`vote_average`、`genres`；TV 还需 `number_of_seasons`/`number_of_episodes`（若 TMDB 有值） | `TmdbService.detail(item, config, false)` | 首屏 |
| `credits` | `credits` 或 TV 的 `aggregate_credits`，cast/crew 均可为空但必须声明完整 | 同上 | 首屏 |
| `images` | `poster_path`、`backdrop_path` 和 `images` 已覆盖当前页面需要的语言候选 | 同上 | 首屏 |
| `external_ids` | `external_ids` object（无 ID 也必须能声明空） | 同上 | 首屏 |
| `videos` | 详情级 `videos.results` | `TmdbService.videos` 或现有相关视频入口 | 延迟 |
| `recommendations` | `recommendations.page=1` 和 `results` | `TmdbService.recommendations(item, config, 1)` | 首屏/延迟加载 |
| `similar` | `similar.page=1` 和 `results` | `TmdbService.similar(item, config, 1)` | 首屏/延迟加载 |
| `season:N` | 季摘要、`episodes`、季图和季演职员 | `TmdbService.season(item, N, config, detail)` | 选季时 |
| `season_videos:N` | 当前季视频 results | `TmdbService.seasonVideos(...)` | 打开视频时 |
| `episode:N:E` | 单集字段和当前集详情字段 | `TmdbService.episode(item, N, E, config, detail)` | 打开单集时 |
| `episode_videos:N:E` | 单集视频 results | `TmdbService.episodeVideos(...)` | 打开视频时 |

规划器规则：首屏只要求 `core`、`credits`、`images`；`external_ids` 只在当前 UI 显示 IMDb/外部 ID 或需要身份确认时要求。`recommendations`、`similar`、`videos` 不得因为缺少而阻塞首屏。TV 选季后才要求对应 `season:N`；不能用 `season:1` 满足 `season:2`。

### 15.5 详情页精确调用顺序

修改 `TmdbDetailActivity.loadContent()`（当前 `:2312`）为以下顺序，禁止在源详情完成前提交 `loadTmdbResult()`：

```text
generation = ++loadGeneration
detailTasks.cancelAll()
sourceResult = SiteApi.detailContent(key, id)
loadedVod = sourceResult.getVod()
payload = TmdbSourcePayloadParser.parse(loadedVod.getTmdb())
sourceBundle = TmdbSourceAdapter.toBundle(payload, loadedVod, config)
plan = TmdbSourceCapabilityPlanner.plan(sourceBundle, payload, currentUiState)
applyLoaded(loadedVod, sourceBundle, ...)

if plan.hasInitialNetworkGaps() and tmdbConfig.isReady() and siteAllowsTmdb:
    networkBundle = loadTmdbMissing(plan, payload.identity())
    mergedBundle = TmdbSourceMerger.fillOnly(sourceBundle, networkBundle)
    applyLoadedIfGenerationCurrent(loadedVod, mergedBundle)
```

具体分支：

1. payload 合法且首屏能力完整：不创建 `TmdbLoadResult`，不调用搜索、`loadTmdbBundle` 或 `TmdbService.detail`。
2. payload 合法但缺 `core`：用 payload 的 ID 构造 `TmdbItem`，直接调用详情接口；禁止标题搜索。
3. payload 合法但缺 `credits/images`：直接按 ID 请求完整详情并只合并缺口。
4. payload 只有 ID 且 `detail` 为空：直接按 ID 请求；只有 payload 无效或 ID 缺失时才进入原有 `searchResolvedTmdbMatch()`。
5. payload 冲突（外层 ID、`detail.id`、媒体类型不一致）：丢弃 payload，保留普通源详情，并执行现有匹配流程。
6. `reusableBundle` 非空时仍先合并合法 source payload；复用对象只作为低优先级网络缓存，不得覆盖源字段。
7. 每个后台结果回 UI 前检查 `generation`、`identity`、`season_number` 和 `mediaType`，任一不符直接丢弃。

`TmdbSourceAdapter.toBundle()` 必须填充现有内部 record：

| `TmdbBundle` 成员 | 来源 |
| --- | --- |
| `item` | payload 身份和 detail 标题/简介/图片/评分构造 `TmdbItem` |
| `detail` | `detailJson` 解析后的 `JsonObject` |
| `cast`/`creators` | `credits.cast/crew` 或 `aggregate_credits` 经现有转换器 |
| `photos` | `images.backdrops/posters` 与 detail 主图去重 |
| `related` | `recommendations.results` + `similar.results` 去重 |
| `seasons`/`seasonCounts` | `detail.seasons` |
| `seasonEpisodes` | 当前 `season_number` 对应季的 `episodes` |
| `seasonCast`/`seasonPhotos` | 对应季对象的 `credits`/`images` |

适配器不得调用网络；缺字段返回空集合，交给 planner 决定是否补齐。

### 15.6 合并、来源和缓存实现

`TmdbSourceMerger` 保存三层对象：`sourcePayload`、`networkPayload`、`effectiveBundle`，不把网络结果写回 `Vod.tmdb`。字段来源以能力组记录：`SOURCE`、`LOCAL_CACHE`、`REMOTE_TMDB`、`VOD_FALLBACK`。

缓存键固定为：

```text
tmdb-detail:{mediaType}:{tmdbId}:season={seasonNumber}:lang={language}:include={capabilityMask}
```

能力 mask 必须排序后生成，不能以 Java Set 的迭代顺序直接拼接。详情源缓存仍使用现有 `VodDetailCache` 的 `source + NUL + id` 键；不要改变其 TTL 或把合并结果存入该缓存。TMDB 网络缓存可沿用 `TmdbService` 现有 cache，但新季键必须追加 `seasonNumber`。

fill-only 的字段规则：字符串空白、数值未定义、对象缺失、未声明 complete 的空数组都允许由低优先级填充；源字段非空或其能力组明确声明空数组后，低优先级不得覆盖。`detail.id` 和身份字段永不由低优先级修改。

`Vod` Parcelable 只传 `tmdb` 的协议字段和 `detailJson` 字符串；传输前执行 256 KiB 单对象限制。超过限制不走 Parcelable，继续通过 `VodDetailCache.put()` 传递 key；绝不把 `JsonObject` 或完整图片/推荐对象直接写入 Binder。

### 15.7 T3/T4 服务端实现边界

T3 只需在现有 `detailContent(ids)` 返回的 `list[0]` 增加 `tmdb`，不新增函数、不改变 `ids` 语义；JS/Python/Java 爬虫桥接均把它当作普通 JSON。

T4 只需改造其 `ac=detail&ids=` 响应的 `MovieDetail`/Vod 序列化层；若使用 alist-tvbox 的实现，内部可将 `MediaMetadata(provider, metaId, season, status, payload, fetchTime)` 映射为 C16 对象，但不能把 `vod_*` 平铺字段当作替代合同。T4 必须：

- 没有合法快照时省略 `tmdb`，不能返回空 ID 对象。
- 读取快照失败时返回普通详情，不让整个 `ac=detail` 失败。
- 按 `provider=tmdb + metaId + season` 读写，网络失败不覆盖旧 payload。
- 图片只返回相对 TMDB path 或受信任的 HTTPS 图片 URL，不能返回 API key、Authorization、API base 或任意请求头。
- 服务端响应保持 JSON；XML 类型站点不承担 `tmdb` 合同，APP 仅从 JSON 读取该字段。

T4 的服务端测试必须验证旧客户端仍可读取 `vod_*`，新客户端读取 `tmdb`，同一 `ids` 在无快照、完整快照、部分快照和坏快照四种状态下均有确定响应。

### 15.8 测试文件和通过条件

首版必须新增或扩展以下测试：

| 测试 | 必须证明 |
| --- | --- |
| `TmdbSourcePayloadTest` | Gson 解析、schema/media/id/season 校验、未知组和坏类型处理 |
| `TmdbSourceCapabilityPlannerTest` | 每个能力组缺口、季/集隔离、完整空数组不联网 |
| `TmdbSourceMergerTest` | source > cache > network > Vod fallback，源值不被覆盖 |
| `TmdbSourceAdapterTest` | payload 无网络转成现有 `TmdbBundle` 所需的所有集合 |
| `VodTmdbParcelableTest` | Parcelable 往返、旧对象兼容、256 KiB 超限走 cache key |
| `SiteApiT3TmdbDetailTest` | type 3 JSON 详情保留 tmdb 且缓存往返不丢失 |
| `SiteApiT4TmdbDetailTest` | type 4 JSON 详情保留 tmdb，普通字段不变 |
| `TmdbDetailSourcePayloadTest` | 完整 payload 不调用 TMDB；部分 payload 只按 ID 补齐 |
| `TmdbDetailGenerationTest` | 切源、切季、退出后旧任务不能更新页面 |
| `TmdbServiceCacheKeyTest` | media/id/season/language/capability 不串缓存 |

“零 TMDB 请求”必须通过可注入的 fake `TmdbService` 或请求计数器断言，不能仅通过 UI 截图推断。每个测试失败都必须保留实际请求 URL/能力组，便于确认没有误触发标题搜索或整包详情请求。

## 16. 实施顺序与提交边界

实施必须按以下顺序，每阶段独立编译/测试并保持可回滚：

1. **协议模型**：新增 payload/detail/parser，扩展 `Vod` Gson、Parcelable、copy/diff；运行模型和解析测试。
2. **纯逻辑**：实现 adapter/planner/merger，运行字段、能力、季集、来源优先级测试。
3. **源接入**：确认 T3/T4 JSON 详情和 `VodDetailCache` 往返，运行 SiteApi 测试。
4. **详情页接入**：重排 `loadContent` 的先源后网状态机，接入 `TmdbBundle`，运行零请求、缺口补齐和 generation 测试。
5. **延迟能力**：接入季、集、视频、推荐分页，运行季集和视频回归。
6. **设备验收**：移动端、Leanback 各验证完整 T3、部分 T4、无扩展旧源；记录网络请求计数和页面结果。

每阶段只允许修改对应任务文档声明的客户端文件；T4 服务端应使用独立任务文档和独立提交，不与 APP 提交混合。任何新增字段、接口、依赖或跨仓库变更都必须先更新本实现合同并重新评审。

## 17. 实施记录

### 阶段 1：协议模型

- 提交：`4865bd7ad08cfd1dd12fba60a202b346e71336a0`；恢复标签 `recovery/C16-stage1/20260919104302-4865bd7ad08c`。
- 已实现 `TmdbSourcePayload` 的固定协议字段、内部来源标记、深拷贝、身份合法性判断、确定性能力组集合和 Binder 大小判断。
- 已实现 `TmdbSourceDetail` 的 `JsonObject` 字符串保存及白名单读取器；读取器遇到缺失或类型错误返回空值，不向详情页抛出异常。
- 已实现 `TmdbSourcePayloadParser`：校验 `schema/id/media_type/season_number/detail.id/detail.season_number`，清理未知能力组和坏类型字段，规范化图片 URL，并执行 2 MiB、500 项、64 KiB 限制。
- 已扩展 `Vod`：`tmdb` 参与 Gson、Parcelable 和 `isSameContent`；超过 256 KiB 时不把 `detailJson` 放入 Binder，而是通过现有 `VodDetailCache.put()` 生成 key。
- 定向验证：`TmdbSourcePayloadTest`、`VodTmdbParcelableTest` 共 7 项通过；同一 Gradle 调用完成 Mobile Arm64 单测和 Leanback Arm64 Debug Java 编译，结果 `BUILD SUCCESSFUL`。
- 未验证项：真实 Android Binder 往返、设备详情页消费和 T3/T4 源接入，按第 16 节后续阶段继续处理。

### 阶段 2：纯逻辑

- 提交：`bbd0a4de5fbbbe497cc4f5935a6c17ed46d50708`；恢复标签 `recovery/C16-stage2/20260919105729-bbd0a4de5fbb`。
- 将原活动内私有 `TmdbBundle` 提升为 `ui.helper.TmdbBundle`，保持现有字段和详情页调用不变，供纯逻辑和后续源接入共用。
- 新增 `TmdbSourceAdapter`：从已校验 payload 无网络构造 `TmdbItem`、详情 JSON、演职员、图片、相关推荐、季集、季演职员和季图片；绝对 HTTPS 图片保持原样，相对路径使用当前 APP 图片基址。
- 新增 `TmdbSourceCapabilityPlanner`：按首屏、季、集和视频场景计算 required/available/missing，完整空组视为已满足，季与集严格隔离。
- 新增 `TmdbSourceMerger`：按 source > local cache > remote TMDB 的 fill-only 规则合并 item、detail、集合和季映射，并记录字段来源；源声明完整的空值不会被覆盖。
- 修复 `TmdbSourcePayloadParser` 对数组元素递归校验时误用父数组字段类型、导致 cast/images/seasons 等数组被清空的问题；新增 adapter/planner/merger 测试覆盖。
- 定向验证：`TmdbSourceAdapterTest`、`TmdbSourceCapabilityPlannerTest`、`TmdbSourceMergerTest` 共 10 项通过；同一 Gradle 调用完成 Leanback Arm64 Debug Java 编译，结果 `BUILD SUCCESSFUL`。
- 未验证项：真实网络补齐请求、Activity 状态机接线、真实设备 UI 和源详情缓存往返，按阶段 3-6 继续处理。

### 阶段 3：源接入

- 提交：`070efd6f4ec818a840f4b2bccb384389d116b368`；恢复标签 `recovery/C16-stage3/20260919105928-070efd6f4ec8`。
- 新增 `SiteApiT3TmdbDetailTest`：使用 T3 `detailContent` 的 JSON 形状验证 `Vod.tmdb`、身份、能力组和详情读取器保留，并通过 `VodDetailCache` 内容往返后仍保留完整协议。
- 新增 `SiteApiT4TmdbDetailTest`：使用 T4 `ac=detail` 的 JSON 形状验证电视剧季上下文、`season:1`、季数组和缓存往返，普通详情字段未被改变。
- 本阶段确认 Gson、`Result` 列表复制和现有详情缓存无需生产代码改动；`SiteApi` 的 T3/T4 请求与分派保持不变。
- 定向验证：两个测试类共 4 项通过，Mobile Arm64 Debug 单测任务返回 `BUILD SUCCESSFUL`。
- 未验证项：Activity 尚未在调用 `TmdbDetailPrefetch` 前消费 payload，真实 T3/T4 网络请求和页面请求数留待阶段 4/6。

### 阶段 4：详情页接入

- 提交：`7de6bed8886ab34e429f4e66295d12e946815393`；恢复标签 `recovery/C16-stage4/20260919111304-7de6bed8886a`。
- `TmdbDetailActivity.loadContent` 已改为先完成 `SiteApi.detailContent`、解析并应用 `Vod.tmdb`，再决定是否创建旧 TMDB 匹配任务；旧源路径仍在源详情完成后串行执行。
- 合法 payload 先按 identity 构造 `TmdbBundle`，再计算 `TmdbSourceCapabilityPlanner` 首屏缺口；`core/credits/images` 完整时直接返回，不调用来源详情 TMDB 请求。
- 部分 payload 使用 `TmdbService.detailForSource()` 按 TMDB ID 请求，合并后通过 `TmdbSourceMerger.fillOnly()` 仅填缺口；不再为有效身份执行标题搜索。
- `TmdbService` 新增 source detail 缓存键，包含媒体类型、TMDB ID、季上下文、语言和排序后的能力 mask；请求 URL、认证和磁盘缓存实现保持现有策略。
- `TmdbSourceAdapter.fromNetwork()` 复用与服务端 payload 相同的字段解析、图片规范化和季集映射，确保 source 与 network bundle 形状一致。
- 后台合并回 UI 前继续检查 generation、当前 `Vod` 和 TMDB identity；旧页面、切源或退出不会应用迟到结果。
- 同步调整旧 standalone 源码测试：仍保留单次首屏绑定和季预加载，但断言顺序改为 source detail -> payload plan -> TMDB wait。
- 定向验证：`TmdbDetailSourcePayloadTest`、`TmdbDetailGenerationTest`、`TmdbServiceCacheKeyTest`、`TmdbSourceAdapterTest`、`TmdbDetailActivityLayoutTest`、`TmdbUIAdapterTest` 共 75 项通过；Leanback Arm64 Debug Java 编译通过，Gradle 返回 `BUILD SUCCESSFUL`。
- 未验证项：真实网站 T3/T4 网络请求计数、移动端/电视端字段渲染和真实退出/切源竞态，留待阶段 6 设备验收。

### 阶段 5：延迟能力

- 新增活动级 source payload 身份状态；仅当当前 TMDB 身份仍与源 payload 一致时，季、集和视频的完整组短路生效。
- `fetchSeasonIfNeeded` 对已由 `season:N` 完整声明的季直接返回；内部 stale split 探针也跳过已完整声明的季。用户显式 refresh 仍保留原刷新语义。
- 长按单集时，`episode:N:E` 完整声明直接使用已绑定的 TMDB 分集元数据打开详情，不再请求单集接口；缺失该组时继续走原补齐路径。
- 新增 `TmdbSourceAdapter.videos()`：无网络从 detail、season 和 episode 对象解析 `TmdbVideo`，保持 title/season/episode scope，并按现有规则去重排序。
- 视频区仅在当前上下文需要的 `videos`、`season_videos:N`、`episode_videos:N:E` 全部声明完整时使用内嵌视频；否则保持现有网络聚合，避免只补一部分造成上下文缺口。
- 推荐和相似内容继续使用阶段 2 的内嵌首批 `related`；加载更多和个性化推荐仍走现有分页/服务流程。
- 定向验证：`TmdbSourceAdapterTest` 新增三 scope 视频解析；`TmdbDetailSourcePayloadTest` 新增季、集、视频短路断言；连同 generation、缓存键、布局和 UI 接线测试共 203 项通过；Leanback Arm64 Debug Java 编译通过。
- 未验证项：真实设备上选择缺失季、长按缺失集、打开视频区的请求数与 UI 结果，留待阶段 6。

### 阶段 6：设备验收

- 测试入口：新增 `app/src/androidTest/java/com/fongmi/android/tv/ui/activity/C16TmdbSourceDetailDeviceTest.java`。测试通过 `ActivityScenario` 启动未导出的 `TmdbDetailActivity`，等待真实 View 文本后断言字段渲染；夹具使用 `adb reverse` 暴露本机 T4/T3 源。测试仅在 `Config.vod()` 指向 C16 夹具时执行，普通设备保持 JUnit skip，不影响既有 AndroidTest。
- 设备：`192.168.50.3:5561`，`HD1910`，Android 9，`x86_64 + arm64` 兼容层，1920×1080/280 dpi。所有数据改动在测试前备份，验收后已恢复 `com.silent.android.webhtv_preferences.xml` 和 `databases/tv`；测试 APK 已卸载，reverse 已移除。
- Mobile APK：SHA-256 `17e0ec2c403a9185134cbc609fb80867a65a54094b5ccc1f6166ba2c79256dbe`。Leanback APK：SHA-256 `563a222b1773ad8df7beeddb765d49cf6e6e39701a2fbe1622c63785ce873887`。AndroidTest APK：SHA-256 `4a659334f8c617b45e5bcb53c5867a52b5c7250c74b15684864e266a86367ed8`。
- Mobile 结果：完整 T4、部分 T4、无扩展旧源、完整 T3 各 1 项仪器测试通过。
- Leanback 结果：完整 T4、部分 T4、无扩展旧源、完整 T3 各 1 项仪器测试通过；另通过完整 TV 季集场景 1 项。
- 完整 T4 请求证据：夹具仅收到 `/config.json` 与 `/t4_complete?ac=detail&ids=c16-t4-complete-1`；日志为 `source payload plan required=[core, credits, images] missing=[]`，没有 TMDB detail/search 请求。UI 显示 `C16 Complete T4`、`Embedded complete overview`、`Embedded Actor`。
- 完整 T3 请求证据：夹具仅收到 `/config.json` 与 `/t3_complete.js`；Mobile 和 Leanback 均为 `required=[core, credits, images] missing=[]`，没有 TMDB detail/search 请求。JS Spider 返回普通 `detailContent` JSON，证明 T3 JSON 字段无需 ABI 改动即可透传。
- 部分 T4 请求证据：夹具收到 `/t4_partial?ac=detail&ids=c16-t4-partial-1`；Mobile 日志为 `missing=[credits, images]`，Leanback 同组，随后只出现按 TMDB ID 的 `requestJson type=detail`，没有标题搜索。UI 保留 `C16 Partial T4` 和 `Source partial overview must win`。
- 旧源证据：夹具收到 `/legacy?ac=detail&ids=c16-legacy-1`；Mobile/Leanback 继续走原有 `load tasks ... singlePass=true`、`tmdb wait` 与单次首屏绑定，UI 显示普通标题、`line-a` 线路和 `正片` 选集，无崩溃。
- 季集证据：Leanback 完整 TV 夹具仅收到 `/t4_tv_complete?ac=detail&ids=c16-t4-tv-1`；`season:1`、`episode:1:1` 与视频组均已内嵌，UI 显示 `Embedded Episode 1`，日志没有 `season`、`episode` 或 TMDB detail/search 请求。
- 切源/退出隔离：设备场景不制造真实竞态；阶段 4 的 `TmdbDetailGenerationTest` 已验证迟到的 source-fill 结果在 generation、当前 Vod 或 TMDB identity 变化后不会更新页面。真实弱网快速切源仍需发布前抽样关注，但不阻塞本期合同完成。

## 18. 补充设计：无 Key 的源内嵌 TMDB 详情

### 18.1 目标与结论

本补充设计解决两个相互独立但当前被同一把锁绑住的问题：

1. 用户是否能选择 TMDB 详情模式和主题；
2. APP 是否有权向 TMDB 发起网络请求。

设计要求把这两件事彻底分开：

- 模式、主题始终是可持久化的用户配置，不要求 API Key。
- TMDB Key 只决定是否允许在线匹配和按缺口网络补齐。
- 爬虫详情 `Vod.tmdb` 是独立数据源。没有 Key 时，只要其中存在用户可见的 TMDB 数据，就可以使用用户选择的模式和主题做 source-only 渲染。
- 没有可展示 `Vod.tmdb` 且没有 Key 时，运行时不使用 TMDB 模式，也不显示空壳或 `detail_tmdb_need_key`，而是按影视原始模式打开并复用已经取得的源详情。
- API 未返回的区域直接隐藏。标题、简介、海报等同义字段允许按既有规则回退到 `Vod`；TMDB 专属区域不得用空标题、空列表、零值或空白间距占位。

推荐方案是“配置模式与运行时模式分离 + source-first 决策 + 无 Key 网络禁用”。禁止采用“无 Key 也总是打开 `TmdbDetailActivity`”或“只在 Activity 内吞掉 Key 提示”的方案，因为它们都会制造空详情页或错误模式页面。

### 18.2 决策依据与本项目证据

本节是 C16 的增量设计，不改变第 4-9 节的生产者协议，也不改变 TMDB 在线 API 的认证合同。现有证据已经足够决定实现方向：

| 证据 | 结论 |
| --- | --- |
| C16 阶段 1-5 已实现 `TmdbSourcePayload`、`TmdbSourcePayloadParser`、`TmdbSourceAdapter`、`TmdbSourceCapabilityPlanner` 和 `TmdbSourceMerger` | 源内嵌数据已经能无网络转成现有 `TmdbBundle`，不需要新增协议或重新研究上游 |
| `TmdbDetailActivity.loadContent()` 已先请求源详情、再解析 payload、再决定在线补齐 | 独立详情页三模式只需补齐“无 Key 路由”和“源数据不可展示时回退” |
| `TmdbConfig.isReady()` 只表示 API Key 或 Access Token 存在 | 该条件适合控制网络，不应继续作为模式/主题可配置性或详情页可进入性的同义词 |
| `Setting.getDetailOpenMode()` 在无 Key 时把 TMDB 模式强制降为 `DETAIL_OPEN_DIRECT` | 这是当前“选不了/选了不生效”的直接根因 |
| 设置页在无 Key 时弹出 `TmdbSourceDialog` 并拒绝保存模式 | 这是第二个直接根因，和运行时降级重复 |
| `VideoActivity` 的独立模式路由依赖 `Setting.isTmdbDetailPage()`，其中包含 Key 就绪 | 无 Key 时不会进入已经具备 C16 消费能力的页面 |
| 现有 C16 设备测试通过显式启动 `TmdbDetailActivity` 验证源数据渲染 | 证明渲染能力成立，但没有覆盖正常设置、入口路由、无 Key 回退和 Mobile/Leanback parity |
| `loadTmdbMediaBlocks()` 仍包含个性化推荐等网络型后处理 | source-only 路径必须显式跳过，不能只靠单个 `canMatchTmdb()` 分散拦截 |

证据适用范围：

- 上游仓库和成熟项目证据继续沿用第 2 节，本轮不新增 OMDB、T4 或播放器依赖，不形成新的 commit ledger。
- 官方 API 合同没有变化：在线 TMDB 请求仍必须经过 `TmdbConfig.isReady()`；本设计只处理在不请求 API 时消费源数据。
- Android 生命周期、进程重建、并发 generation 和 Binder 大小合同继续沿用第 8、15 节；本设计不改变这些边界。

### 18.3 三种方案比较

| 方案 | 行为 | 结论 |
| --- | --- | --- |
| 保持现状 | 无 Key 时模式被强制改为影视原生，设置页也拒绝选择 | 不满足需求，继续把“在线能力”误当作“展示能力” |
| 仅删除 Key 校验，总是进入 TMDB 模式 | 无 Key、无源 TMDB 的详情会出现空壳，并可能显示 Key 提示 | 拒绝，破坏普通详情体验 |
| 配置模式与运行时模式分离，按源数据状态选择 | 有源数据使用用户模式；没有源数据时影视原始；无 Key 时零 TMDB 网络 | 推荐，改动边界清晰且可回滚 |

### 18.4 状态定义

#### 18.4.1 持久化配置

`configuredMode` 是用户在选择器中保存的模式，允许值：

```text
DETAIL_OPEN_DIRECT
DETAIL_OPEN_ORIGINAL_ENHANCED
DETAIL_OPEN_FUSION
DETAIL_OPEN_ENHANCED
DETAIL_OPEN_PLAYER
```

`configuredTheme` 是用户保存的详情主题：

```text
DETAIL_STYLE_NATIVE
DETAIL_STYLE_PROFILE
DETAIL_STYLE_CINEMA
```

`configuredMode` 和 `configuredTheme` 不依赖 `TmdbConfig.isReady()`。

#### 18.4.2 源 TMDB 状态

新增纯逻辑枚举 `TmdbSourceState`：

| 状态 | 定义 | 无 Key 的行为 | 有 Key 的行为 |
| --- | --- | --- | --- |
| `ABSENT_OR_INVALID` | 无 `Vod.tmdb`、解析失败、身份冲突，或 `toBundle()` 失败 | 影视原始 | 原自动匹配/搜索流程 |
| `IDENTITY_ONLY` | payload 身份合法，但没有任何用户可见字段 | 影视原始 | 使用 payload 的 ID/media type 直接补齐，不做标题搜索 |
| `RENDERABLE` | 至少存在一个用户可见 TMDB 字段 | source-only 用户模式 | source-first 用户模式，仅补齐缺口 |

“用户可见 TMDB 字段”至少包括一项：

- 标题或原名；
- 简介、标语、状态；
- 海报、背景、图片集；
- 日期、评分、分类、地区；
- 演职员；
- 外部 ID；
- 季、集或视频；
- 推荐或相似内容。

仅存在 `schema/id/media_type/season_number` 或仅声明空能力组，不等于 `RENDERABLE`。

#### 18.4.3 运行时模式

`runtimeMode` 是一次详情页面会话实际采用的模式，不写回偏好：

```text
configuredMode == DIRECT
    -> DIRECT

configuredMode == ORIGINAL_ENHANCED
    source == RENDERABLE or tmdbReady
    -> ORIGINAL_ENHANCED
    otherwise
    -> DIRECT

configuredMode in {FUSION, ENHANCED, PLAYER}
    source == RENDERABLE or tmdbReady
    -> configuredMode
    otherwise
    -> DIRECT
```

`runtimeMode` 一旦基于当前 `Vod` 决定，必须在本次会话内稳定；切源、切 ID、`onNewIntent()` 或显式刷新时重新计算。

### 18.5 完整行为矩阵

| 配置模式 | Key | 源 payload | 运行时结果 | 网络行为 |
| --- | --- | --- | --- | --- |
| 影视原生 | 任意 | 任意 | 影视原生 | 不因 payload 改变 |
| 原生增强 | 无 | 无/无效 | 影视原生 | 零 TMDB 请求 |
| 原生增强 | 无 | 仅身份 | 影视原生 | 零 TMDB 请求 |
| 原生增强 | 无 | 可展示 | 原生增强 source-only | 零 TMDB 请求 |
| 原生增强 | 有 | 无/无效 | 原生增强 | 原自动匹配 |
| 原生增强 | 有 | 仅身份 | 原生增强 | 按 ID 补齐 |
| 原生增强 | 有 | 可展示 | 原生增强 source-first | 仅补缺口 |
| 独立三模式 | 无 | 无/无效 | 加载后回退影视原生 | 零 TMDB 请求 |
| 独立三模式 | 无 | 仅身份 | 加载后回退影视原生 | 零 TMDB 请求 |
| 独立三模式 | 无 | 可展示 | 用户选择模式和主题 | 零 TMDB 请求 |
| 独立三模式 | 有 | 无/无效 | 用户选择模式 | 原匹配/搜索 |
| 独立三模式 | 有 | 仅身份 | 用户选择模式 | 按 ID 补齐 |
| 独立三模式 | 有 | 可展示 | 用户选择模式 | source-first，仅补缺口 |
| 任意 TMDB 模式 | 任意 | 任意，但站点被 TMDB 规则排除 | 影视原始 | 保持现有站点策略 |

### 18.6 纯策略接口

新增纯逻辑类，建议路径：

`app/src/main/java/com/fongmi/android/tv/setting/DetailRuntimeModePolicy.java`

接口：

```java
public final class DetailRuntimeModePolicy {

    public enum SourceState {
        ABSENT_OR_INVALID,
        IDENTITY_ONLY,
        RENDERABLE
    }

    public record Input(
            int configuredMode,
            boolean tmdbReady,
            boolean siteAllowed,
            SourceState sourceState
    ) {}

    public record Decision(
            int runtimeMode,
            boolean sourceOnly,
            boolean networkAllowed
    ) {}

    public static Decision resolve(Input input) {
        if (!Setting.isTmdbMode(input.configuredMode())) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        if (!input.siteAllowed()) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        boolean renderable = input.sourceState() == SourceState.RENDERABLE;
        if (!renderable && !input.tmdbReady()) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        return new Decision(
                input.configuredMode(),
                renderable && !input.tmdbReady(),
                input.tmdbReady()
        );
    }
}
```

约束：

- 该策略不得读取 Activity、Intent、网络或 UI。
- `siteAllowed` 由 `TmdbSitePolicy` 在调用侧解析后传入。
- `sourceOnly` 只表示本次会话是否禁止网络补齐，不表示数据一定完整。
- `networkAllowed` 为 false 时，所有 TMDB 网络入口必须短路。

新增纯逻辑可用性判断，建议路径：

`app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceAvailability.java`

接口：

```java
public final class TmdbSourceAvailability {
    public static TmdbSourceState classify(
            Vod vod,
            TmdbSourcePayload payload,
            TmdbBundle bundle
    );

    public static boolean isRenderable(TmdbBundle bundle);
}
```

实现必须复用已经规范化后的 bundle，不得用原始 JSON 直接猜字段。`classify()` 的判定顺序固定为：

```text
payload == null or bundle == null or identity conflict -> ABSENT_OR_INVALID
!isRenderable(bundle) -> IDENTITY_ONLY
otherwise -> RENDERABLE
```

### 18.7 设置层改造

#### 18.7.1 Setting

`Setting.getDetailOpenMode()` 必须改为“纯配置读取”：

```diff
- return isTmdbMode(mode) && !isTmdbReady() ? DETAIL_OPEN_DIRECT : mode;
+ return mode;
```

保留 `Setting.isTmdbDetailPage()` 作为“在线 TMDB 详情能力就绪”的旧语义，不把它改成无 Key 也返回 true。新增独立方法：

```java
public static boolean isTmdbDetailModeConfigured() {
    return isTmdbMode(getDetailOpenMode()) && getTmdbModel() == TMDB_MODEL_NATIVE;
}
```

路由使用 `isTmdbDetailModeConfigured()`；网络补齐继续使用 `isTmdbReady()` 或 `TmdbConfig.isReady()`。

`getDetailThemeMode()`、`getTmdbDetailStyle()`、`putDetailThemeMode()` 不需要 Key 条件。主题选择只要求模式属于 TMDB 模式。

#### 18.7.2 移动端设置页

文件：`app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingTmdbFragment.java`

删除 `setDetailOpenMode()` 中的：

```java
if (Setting.isTmdbMode(mode) && !Setting.isTmdbReady()) {
    dialog.dismiss();
    Notify.show(R.string.detail_tmdb_need_key);
    TmdbSourceDialog...
    return;
}
```

改为直接保存模式：

```java
Setting.putDetailOpenMode(mode);
setText();
dialog.dismiss();
```

主题行显示条件继续使用 `Setting.isTmdbMode(Setting.getDetailOpenMode())`。由于模式读取不再降级，无 Key 时也会显示并可保存。

#### 18.7.3 Leanback 设置页

文件：`app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingTmdbActivity.java`

执行与移动端相同的接线移除，不新建第二套规则。

#### 18.7.4 保持 Key 化的功能

以下能力继续要求 `Setting.isTmdbReady()`，本设计不解锁：

- 自动匹配、手动重匹配、手动选季和 TMDB 搜索；
- 按缺口在线补齐；
- 个性化 TMDB/豆瓣/AI 推荐；
- TMDB 字幕辅助、历史聚合和需要远程 API 的 OMDb 评分；
- 任何写 TMDB 匹配缓存或季匹配缓存的动作。

source-only 页面应隐藏或禁用这些入口，不得点击后再弹 Key 对话框。

### 18.8 独立三模式接线

目标文件：`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`

#### 18.8.1 入口路由

移动端和 Leanback `VideoActivity.shouldOpenLegacyTmdbDetail()` 改用：

```java
return canOpenLegacyTmdbDetail(key, id, cast)
        && Setting.isTmdbDetailModeConfigured()
        && Setting.isStandaloneTmdbDetailMode(Setting.getDetailOpenMode());
```

不再直接要求 `Setting.isTmdbDetailPage()`。由于此时还不知道爬虫详情是否带 payload，允许先进入 `TmdbDetailActivity`，由加载完成后的运行时策略决定保留或回退。

#### 18.8.2 loadContent 分支

`TmdbDetailActivity.loadContent()` 固定顺序：

```text
generation = ++loadGeneration
cancel previous detail tasks
sourceResult = SiteApi.detailContent(key, id)
loadedVod = sourceResult.getVod()
payload = TmdbSourcePayloadParser.parse(loadedVod.tmdb)
bundle = TmdbSourceAdapter.toBundle(payload, loadedVod, tmdbConfig)
siteAllowed = isTmdbAllowedForCurrentSite()
sourceState = TmdbSourceAvailability.classify(loadedVod, payload, bundle)
decision = DetailRuntimeModePolicy.resolve(
        getDetailMode(),
        tmdbConfig.isReady(),
        siteAllowed,
        sourceState)

if decision.runtimeMode == DIRECT:
    fallbackToOriginalDetail(loadedVod, directFallbackReason)
    return

if sourceState == RENDERABLE:
    applyLoaded(loadedVod, bundle, emptySearchItems, error, false)
    if !decision.networkAllowed or no initial gaps:
        bindSourceOnlyBlocks(bundle)
        return
    fillMissingGroupsOnly(...)
    return

if decision.networkAllowed:
    run existing loadTmdbResult() / search flow
else:
    fallbackToOriginalDetail(...)
```

关键约束：

- `fallbackToOriginalDetail()` 之前不得显示 TMDB 空状态、Key 状态或空列表。
- `applyLoaded()` 后不得因为 `!tmdbConfig.isReady()` 再显示 `detail_tmdb_need_key`。
- source-only 路径不得创建 `tmdbFuture`、不得调用搜索、不得调用 `detailForSource()` 或任何分页网络接口。
- 在线补齐的 generation、identity 和 season 隔离保持第 15.6 节合同。

#### 18.8.3 回退影视原生

回退必须复用已经加载的 `loadedVod`，禁止再次请求爬虫详情。建议新增：

```java
private void fallbackToOriginalDetail(Vod loadedVod, String reason) {
    if (isFinishing() || isDestroyed()) return;
    VideoActivity.startDirectResolved(this, loadedVod);
    finish();
}
```

`VideoActivity.startDirectResolved()` 负责：

- 将 `loadedVod` 放入 `VodDetailCache`；
- 传递 `EXTRA_DETAIL_RUNTIME_MODE = DETAIL_OPEN_DIRECT`；
- 传递 `collect=false`、`cast=false`；
- 不传递 `auto_play=true`；
- 不触发再次 TMDB 路由。

回退原因只用于日志，不写偏好：

```text
source_absent_or_invalid
source_identity_only_without_key
site_tmdb_disabled
```

### 18.9 原生增强接线

目标文件：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`

#### 18.9.1 运行时模式字段

两端各增加：

```java
private int runtimeDetailMode = Setting.getDetailOpenMode();

private boolean isRuntimeFusionMode();
private boolean isRuntimeOriginalEnhancedMode();
private boolean isRuntimeDirectMode();
private void applyRuntimeDetailMode(int mode);
```

`EXTRA_DETAIL_RUNTIME_MODE` 只允许：

- `DETAIL_OPEN_DIRECT`：来源于独立页 fallback；
- `DETAIL_OPEN_ORIGINAL_ENHANCED`：显式直达原生增强源数据页；
- 未传时使用 `configuredMode`。

活动内所有影响布局和交互的 `Setting.isFusionDetailPage()`、`Setting.isOriginalEnhancedDetailPage()`、`Setting.isDirectDetailPage()` 必须逐步替换为 runtime 版本；设置页仍读取配置版本。首轮至少覆盖：

- 移动端 VideoActivity：`isTmdbSourceEnabled()`、`shouldUseUpstreamNativeEpisodeModule()`、`shouldUseTmdbDetailLayout()`、首屏 reveal、Fusion backdrop/control 分支、Original Enhanced 动作区；
- Leanback VideoActivity：`shouldUseUpstreamNativeEpisodeModule()`、`isTmdbSourceEnabled()`、`prepareInitialDetailShell()`、`setOriginalEnhancedActionVisibility()`、`shouldRevealShellWhileLoading()`；
- `TmdbHeaderView` 继续读取当前独立页的配置模式；它不处理 `VideoActivity` fallback。

#### 18.9.2 初始化适配器

`initTmdbMode()` 不再因为 `!mTmdbUIAdapter.isReady()` 直接放弃 source-only 能力。建议拆成：

```text
configured TMDB mode && site allowed
    -> 创建 TmdbUIAdapter

canNetworkLoad = adapter.isReady() && site allowed
hasSourceBundle = false

detail loaded
    -> classify source
    -> if RENDERABLE: adapter.loadSource(bundle, vod)
    -> if no key && no source: applyRuntimeDetailMode(DIRECT)
```

`TmdbUIAdapter` 新增无网络方法：

```java
public void loadSource(TmdbBundle bundle, Vod vod, TmdbSourcePayload payload);
```

该方法只允许：

- 做 generation 校验；
- 写入 `tmdbItem`、`tmdbDetail`、`tmdbCast`、季节/集数、媒体块；
- 调用 `enrichVod()`；
- 派发 `VOD_CORE` 等本地刷新事件；
- 标记 `loaded=true` 和 `sourceOnly=true`。

不得调用 `tmdbService.detail()`、`season()`、`episode()`、`search()`、推荐分页或 AI。

#### 18.9.3 无 Key、无源数据回退

在 `setDetail(Vod item)` 的最早稳定点决策：

```java
TmdbSourcePayload payload = TmdbSourcePayloadParser.parse(item.getTmdb());
TmdbBundle sourceBundle = TmdbSourceAdapter.toBundle(payload, item, tmdbConfig);
TmdbSourceState sourceState = TmdbSourceAvailability.classify(item, payload, sourceBundle);
DetailRuntimeModePolicy.Decision decision = DetailRuntimeModePolicy.resolve(
        Setting.getDetailOpenMode(),
        tmdbConfig.isReady(),
        TmdbSitePolicy.isEnabled(getKey(), getId()),
        sourceState);
applyRuntimeDetailMode(decision.runtimeMode());
```

顺序必须在以下操作前完成：

- `mFlagAdapter.addAll()` 和原生/增强选集模块选择；
- `setOriginalEnhancedActionVisibility()`；
- `shouldUseTmdbDetailLayout()`；
- `loadNativePersonalRecommendations()`。

`applyRuntimeDetailMode(DIRECT)` 后：

- `isTmdbSourceEnabled()` 返回 false；
- 继续使用原始线路、原始集名和源详情字段；
- 不显示 TMDB Key 状态；
- 不调用已创建的 adapter 网络路径。

#### 18.9.4 有源数据的原生增强

当 `sourceState == RENDERABLE` 时：

```java
applyRuntimeDetailMode(DETAIL_OPEN_ORIGINAL_ENHANCED);
mTmdbUIAdapter.loadSource(sourceBundle, item, payload);
```

无 Key 时，`loadSource()` 只做本地绑定和 UI 刷新；有 Key 时，继续按现有 C16 planner 仅补缺失能力。

### 18.10 缺失区域的显示合同

#### 18.10.1 字段优先级

| UI 字段 | source-only 规则 | 有 Key 的补充规则 |
| --- | --- | --- |
| 标题、原名 | TMDB 标题优先，缺失回退 `Vod.name` | 缺失时按现有网络详情补齐 |
| 简介、标语、状态 | TMDB 值优先，简介缺失可回退 `Vod.content` | 缺失时按能力组补齐 |
| 海报、背景 | 合法 TMDB 图片优先，缺失回退 `Vod.pic/wallPic` | 缺失时补齐图片 |
| 日期、评分、类型、地区 | 有值显示；无值隐藏 | 缺失时补齐 |
| 演职员、主创 | 数组非空才显示 | 缺失且未声明完整时补齐 |
| 图片集、海报集 | 非空才显示 | 缺失且未声明完整时补齐 |
| 外部 ID | 非空才显示 | 缺失且未声明完整时补齐 |
| 季、集 | 内嵌有数据才显示 TMDB 信息；源选集始终保留 | 缺季时按 `season:N` 补齐 |
| 视频 | 内嵌有数据才显示 | 缺组时按 scope 补齐 |
| 推荐、相似 | 内嵌有数据才显示 | 缺组时按页补齐 |
| 个性化/AI 推荐 | 无 Key 永远隐藏 | 有 Key 且原设置开启时保留 |

#### 18.10.2 视图规则

对每个 TMDB 专属区域统一执行：

```text
hasData == false
    -> title GONE
    -> content/list GONE
    -> previous section bottom margin 重置
    -> 不创建空 adapter 项目
```

不得使用以下占位：

- `"暂无"`、`"未知"`、`"null"`、`"0"`；
- 空字符串 TextView 但保留高度；
- `INVISIBLE` 代替 `GONE`；
- 空列表仍保留标题、分隔线或固定高度；
- `detail_tmdb_empty` 或 `detail_tmdb_need_key` 作为 source-only 的缺字段提示。

`binding.tmdbStatus` 规则改为：

```text
sourceOnly && sourceRenderable
    -> GONE
!tmdbReady && !sourceRenderable
    -> 不展示状态，因为最终应回退 DIRECT
tmdbReady && !siteAllowed
    -> 保持现有 site_disabled 行为
tmdbReady && no blocks
    -> 保持现有 empty 行为
```

### 18.11 分阶段实施顺序

不得跳过阶段 A 和支持矩阵。每阶段必须保持可独立回滚。

#### 阶段 A：纯策略与测试

范围：

- 新增 `DetailRuntimeModePolicy`；
- 新增 `TmdbSourceAvailability`；
- 增补 `TmdbSourceState`；
- 为配置模式、Key、站点和三种源状态建立纯单元测试。

验收：不改任何现有运行时行为；所有决策矩阵测试通过。

#### 阶段 B：设置解耦

范围：

- `Setting.getDetailOpenMode()` 去除无 Key 降级；
- 新增 `isTmdbDetailModeConfigured()`；
- 移动端和 Leanback 设置页移除模式 Key 门禁；
- 主题行在任意 TMDB 模式下可选。

验收：无 Key 时模式与主题保存后立即回显，重启后保持；网络型功能仍按 Key 隐藏/禁用。

#### 阶段 C：独立三模式

范围：

- 路由改用 `isTmdbDetailModeConfigured()`；
- `TmdbDetailActivity.loadContent()` 接入运行时决策；
- `RENDERABLE` 走 source-only；
- 无源数据回退 `VideoActivity` 原始详情并复用缓存；
- 隐藏 source-only 的 Key/空状态。

验收：三种独立模式、三种主题、完整/部分/旧源矩阵在无 Key 下行为正确。

#### 阶段 D：原生增强

范围：

- 两套 `VideoActivity` 增加 runtime mode；
- `TmdbUIAdapter.loadSource()`；
- 无 Key、无源数据直接使用影视原生路径；
- 无 Key、有源数据启用原生增强 source-only。

验收：移动端和电视端均不因配置为原生增强而出现空增强模块或网络错误。

#### 阶段 E：缺失区域与交互收口

范围：

- 审计所有 TMDB 标题、列表、间距、状态、重匹配和选季按钮；
- 无 Key 时禁用/隐藏网络型入口；
- 校验回退页、返回、切源、`onNewIntent()` 和进程重建。

验收：无空标题、空列表、空值、错误 Key 提示或点击后才失败的入口。

#### 阶段 F：设备验收

范围：

- 复用 C16 fixture，增加无 Key 配置；
- Mobile 和 Leanback 各跑完整矩阵；
- 记录请求计数、页面文本、返回栈和偏好保持。

### 18.12 最小改动文件清单

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/setting/Setting.java` | 配置读取解耦、新增 configured 判断，不改变网络就绪方法 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingTmdbFragment.java` | 移除模式 Key 门禁 |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingTmdbActivity.java` | 移除模式 Key 门禁 |
| `app/src/main/java/com/fongmi/android/tv/setting/DetailRuntimeModePolicy.java` | 新增纯策略 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSourceAvailability.java` | 新增源状态判断 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | source-only、无源回退、状态隐藏、网络入口收口 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java` | 新增无网络 `loadSource()` |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | runtime mode、source payload、无源回退 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | runtime mode、source payload、无源回退 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbPersonActivity.java` | 仅如需传递 runtime mode，不改变人物页协议 |

禁止修改：

- `Vod.tmdb` JSON 合同和 `TmdbSourcePayload` schema；
- T3/T4 的生产者调用协议；
- `TmdbService` 的网络认证或 URL 规则；
- Exo、MPV、FFmpeg、JNI 和二进制依赖。

### 18.13 测试矩阵

#### 18.13.1 纯单元测试

新增或更新：

| 测试 | 必须证明 |
| --- | --- |
| `DetailRuntimeModePolicyTest` | 所有模式 × Key × site × source state 组合正确 |
| `TmdbSourceAvailabilityTest` | 仅身份不算可展示；任一可见字段算可展示；无效身份拒绝 |
| `SettingDetailModeTest` | 无 Key 时模式读取不再降级；网络就绪判断仍保持 Key 语义 |
| `TmdbSourcePayloadParserTest` | 原有协议和清理规则不回归 |
| `TmdbDetailGenerationTest` | source-only、清空、切源后旧异步结果不能更新 |
| `TmdbUIAdapterTest` | `loadSource()` 只本地绑定，不触发网络 |

#### 18.13.2 源码/接线测试

| 测试 | 必须证明 |
| --- | --- |
| `SettingTmdbSourceOnlyWiringTest` | 两个设置页不再弹出 Key 后拒绝保存模式 |
| `VideoActivityRuntimeModeWiringTest` | Mobile/Leanback 路由和布局使用 runtime mode，而非配置模式 |
| `TmdbDetailSourceOnlyWiringTest` | 无 Key、无源数据调用回退；无 Key、有源数据不创建网络任务 |
| `C16TmdbSourceDetailDeviceTest` | 设备场景覆盖正常路由，而非只显式启动 Activity |

#### 18.13.3 设备矩阵

每项在 Mobile 和 Leanback 各执行一次：

| 场景 | 期望 |
| --- | --- |
| 无 Key + 旧源 + 配置独立三模式 | 加载后进入影视原生，无 Key 提示，无 TMDB 请求 |
| 无 Key + 仅身份 payload | 影视原生，不按 ID 联网 |
| 无 Key + 完整 payload + 沉浸融合 | 融合布局、用户主题、无 TMDB 请求 |
| 无 Key + 完整 payload + 炫彩详情 | 富信息布局、无 TMDB 请求 |
| 无 Key + 完整 payload + 详情直放 | 播放器优先布局、无 TMDB 请求 |
| 无 Key + 部分 payload | 有值区域显示，缺区域全 GONE，无多余间距 |
| 无 Key + 完整 payload + 原生增强 | `VideoActivity` 内显示源增强，零网络 |
| 无 Key + 旧源 + 原生增强 | 使用影视原生线路和选集，无空增强模块 |
| 有 Key + 旧源 | 原自动匹配不回归 |
| 有 Key + 完整 payload | 零 TMDB 补齐请求 |
| 有 Key + 部分 payload | 只请求缺失能力，源字段优先 |
| 任意 Key + 站点规则禁用 | 影视原生，不进入源 TMDB UI |

请求计数必须来自可注入的 fake `TmdbService`、request counter 或夹具访问日志。仅凭截图通过不能证明“零网络”。

### 18.14 验收标准

全部条件必须同时满足：

1. 无 Key 可以保存任意详情模式和主题，设置回显和重启持久化正确。
2. 无 Key、无有效源 TMDB 时，独立三模式和原生增强均退化为影视原始行为，不显示 TMDB 空壳或 Key 提示。
3. 无 Key、有可展示源 TMDB 时，用户选择的模式和主题生效，且全流程零 TMDB 网络请求。
4. 有 Key 时，原自动匹配、按 ID 补齐、source-first 缺口补齐和站点策略不回归。
5. 缺字段时 TMDB 专属标题、内容、列表、状态和间距全部消失；没有 `null`、`0`、“暂无”或空占位。
6. 源线路、源集名、播放 URL、历史续播和返回栈不因模式回退而丢失。
7. Mobile 与 Leanback 行为一致，差异只允许来自既有布局实现。
8. 不使用手工修改偏好来伪造设置页通过；设置保存、读取和运行时结果必须闭环。

### 18.15 风险、兼容性与回滚

| 风险 | 控制 |
| --- | --- |
| 无 Key 用户曾在旧版本保存过 TMDB 模式但一直被运行时降级，升级后行为改变 | 这是目标行为；无源数据仍自动回退，不产生空壳 |
| `getDetailOpenMode()` 变为配置语义后，活动内旧判断误把页面当 TMDB | 阶段 D 必须把布局相关调用全部替换为 runtime mode，并用接线测试锁定 |
| source-only 后处理遗漏网络入口 | 在 `loadTmdbMediaBlocks()`、推荐、季节、单集、视频和重匹配处统一检查 `networkAllowed` |
| 回退重新请求源详情 | 通过 `VodDetailCache` 传递已加载 Vod，并用 fixture 请求日志断言只有一次详情请求 |
| 来源策略被绕过 | 路由和 `TmdbDetailActivity.loadContent()` 都必须检查 `TmdbSitePolicy` |
| 缺失字段导致布局空洞 | 所有区域统一使用 `GONE` 和动态间距复位，设备测试覆盖部分 payload |
| 回滚不完整 | 阶段 A-F 分别提交；关闭第 18 节接线即可回到现有 C16 source-first 行为，协议和生产者数据保持不变 |

兼容性结论：

- 旧源、旧客户端和现有 T3/T4 协议不受影响；
- 无 Key 的新行为只增加 source-only 展示，不扩大在线能力；
- 有 Key 的现有路径保持 source-first 和 fill-only 语义；
- 不改变 ABI、依赖、许可证、二进制、缓存格式或 Binder 上限。

### 18.16 设计完成定义

第 18 节从“文档完成”到“实现完成”必须分别记录，不得混用：

- 文档完成：本节的决策、接口、文件清单、测试矩阵和回滚合同经用户批准；
- 纯逻辑完成：阶段 A 测试通过；
- 配置完成：阶段 B 设置保存/回读验收通过；
- 独立页完成：阶段 C 三模式及回退矩阵通过；
- 原生增强完成：阶段 D Mobile/Leanback 通过；
- 功能完成：阶段 E 无空字段审计通过；
- 交付完成：阶段 F 设备请求计数和 UI 验收通过，并记录 APK、提交和 recovery tag。

阶段 A-F 的提交、验证与设备证据见第 18.17 节。

### 18.17 实施记录

第 18 节按第 18.11 节顺序完成，未采用“先删除 Key 校验、再补运行时路由”的危险捷径：

- 阶段 A：`19128b7d18107b5c614078ebdadc863faa2476b5`，标签 `recovery/C16-stage-A/20260919153225-19128b7d1810`。新增 `DetailRuntimeModePolicy`、`TmdbSourceState` 和 `TmdbSourceAvailability`；11 项策略/可用性测试通过，Mobile/Leanback Arm64 Java 编译通过。
- 阶段 B：`1c4213db0baf24e9da9495edfdbaa9dcab43f527`，标签 `recovery/C16-stage-B/20260919154930-1c4213db0baf`。`getDetailOpenMode()` 变为纯配置读取，新增 `isTmdbDetailModeConfigured()`，Mobile/Leanback 设置页不再因缺少 Key 拒绝保存模式或主题；3 项接线测试通过。
- 阶段 C：`6a994fa1acd85cd307bf111bc077f6e5a1385da0`，标签 `recovery/C16-stage-C/20260919155514-6a994fa1acd8`。独立详情页按运行时策略选择用户模式/source-only/影视原生；无有效源数据时用已加载 `Vod` 直接回退，不重复请求详情；4 项接线测试通过。
- 阶段 D：`b2343d1d1de476226b52bef11b80248e1d5033d9`，标签 `recovery/C16-stage-D/20260919160225-b2343d1d1de4`。Mobile/Leanback `VideoActivity` 使用会话级 runtime mode；新增完全无网络的 `TmdbUIAdapter.loadSource()`，补入内嵌季/集缓存、适配器状态和本地 `VOD_CORE` 刷新；220 项定向 Mobile 测试和双 flavor Java 编译通过。
- 阶段 E：`6e2cde565ae34c1a0da392c7ca93f34cb97aaa99`，标签 `recovery/C16-stage-E/20260919160601-6e2cde565ae3`。source-only 页隐藏重新匹配/选季和网络评分入口，阻止手动匹配、搜索和匹配缓存写入；无数据 TMDB 专属区域不保留空壳；10 项交互/运行时测试通过。
- 阶段 F：本次收口新增 `C16KeylessSourceDetailDeviceTest`，在测试内清除 TMDB Key、切换可控 C16 假源并在结束后恢复原 VOD/TMDB 配置。设备 `V1923A`、Android 9、`192.168.50.3:5559`；Mobile 和 Leanback 各执行 3 项：完整内嵌 payload 独立页、完整内嵌 payload 原生增强、无扩展旧源回退，全部 `3/3` 通过，无 Key 提示；夹具只收到 `/config.json` 与对应 `/c16_keyless_*` 详情端点。
- 阶段 F APK SHA-256：Mobile `ffd6ebfdb7d509f68ec4ced17c0bed37326307d00d71f77f32f69440768e805e`；Leanback `c603991d937850a3c705835ae44d912d42f878f8c4617eed5ee225f9a3e0d750`；Mobile AndroidTest `ac46e38fc5a241e25c7837d93071633463b9a7bdb085b4c8971dc2ec02c8dafe`；Leanback AndroidTest `55ce93d5b93f1f0e4108b3630a8a70d84879a0dd93e4d4a7289d9651cb7890f1`。阶段 F 自身的提交与 recovery tag 由本次收口命令创建。
- 未在本仓库验证：真实 T4 服务端生产数据、真实弱网切源和发布包签名分发；这些不改变客户端第 18 节合同完成状态，但接入生产 T4 前必须由服务端仓库按第 4-9 节完成请求计数和恢复测试。

## 19. 订阅接口级 `tmdb_api_key` 临时作用域设计

> 状态：用户已确认临时 Key 的预期层级是订阅配置接口响应根对象；本节按该层级实施。详情响应根对象只保留 sanitized 剥离，不再写入凭据作用域。

### 19.1 设计修正与结论

此前把 Key 放在 `Vod.tmdb` 内、随后又按整个 App 进程全局保留的设计都与最终目标不符。最终合同是：

1. 正式源字段固定为**订阅配置接口响应根对象**的 `tmdb_api_key`，例如 `/sub/...` 返回的 `sites` 配置对象。不是 `detailContent` 响应根字段，不是 `list[].tmdb.key`，也不进入 `Vod`、`Result`、`TmdbSourcePayload` 或任何详情项。详情响应若仍带同名字段，客户端为了日志/缓存安全会剥离，但不会接受它。
2. 临时 Key 的使用范围是**当前订阅接口**，而不是整个 App 进程，也不是单条详情。当前接口内的不同站点、条目、详情页和 Activity 可以共用它。
3. 切换订阅接口时必须先清空旧接口的临时 Key 和作用域 epoch；新接口返回 `tmdb_api_key` 时才加载，新接口没有返回时保持为空，不允许沿用上一个接口的值。
4. App 已配置的 `accessToken` 或 `apiKey` 永远优先；存在用户配置时，源 Key 必须丢弃，不进入订阅临时作用域。
5. Key 只存在当前 App 进程内存中，并且必须绑定到规范化后的订阅接口身份 `(configId, configUrl)`；不能写入偏好、数据库、文件、备份、Parcel、Intent、Binder、日志、崩溃信息、详情缓存或诊断包。
6. 同一订阅接口存活期间不设置时间 TTL。页面切换、站点切换、条目切换和 Activity 重建都不清空；只有切换订阅接口、用户配置出现、收到新的有效 Key、认证失效、显式清理或进程结束才变化。
7. 源 Key 只允许向官方 HTTPS TMDB API 主机发送；用户自定义 API Base、HTTP、局域网和代理地址一律忽略该 Key。
8. 源 Key 失效、网络失败或能力不支持时，无提示回退到已有源内嵌数据或影视原始详情，不弹 Key 配置提示，不循环重试。

一句话概括：**Key 跟随“当前订阅接口”的内存会话；切换接口先清空，接口内一直可用，接口外绝不复用。**

### 19.2 用户可见行为

在用户未配置 TMDB Key 的前提下：

| 场景 | App 行为 |
| --- | --- |
| 当前订阅配置无 `tmdb_api_key` | 保持第 18 节 source-only/影视原始详情，不新增 TMDB 请求 |
| 当前订阅配置首次返回合法 `tmdb_api_key` | 配置解析成功后剥离原文，绑定当前接口身份存入内存；后续本接口请求可使用 |
| 同一接口内切换站点、条目或详情页 | 临时 Key 继续可用，不重新要求源返回 |
| 同一接口内 Activity 重建 | 只要 App 进程没有重建，临时 Key 继续可用 |
| 切换到另一个订阅接口 | 先清空旧接口 Key 和 epoch；新接口有 Key 再加载，没有则保持为空 |
| 切回原订阅接口 | 视为一次新的接口切换，先清空；只有新订阅配置再次返回 Key 才能使用 |
| App 进程重启 | 临时 Key 不存在，必须重新加载订阅配置获取 |
| Key 无效、超时或限流 | 保留源数据或公开缓存，缺失区域隐藏，回退不阻塞详情 |
| 用户随后配置了 TMDB Key | 立即以用户配置为准；源临时 Key 不再参与请求 |

用户不应看到源 Key、Key 来源或“正在使用订阅临时凭据”等技术信息。设置页中的“TMDB Key 是否已配置”仍只表示用户配置，不因订阅临时作用域有值而改变。

### 19.3 当前实现与切换点

| 位置 | 当前行为 | 修正设计的影响 |
| --- | --- | --- |
| `VodConfig.config(Config)` | `124-127` 行是所有启动、切换和 failover 路径设置当前接口的统一入口 | 在这里比较旧/新 `config.id + url`；身份变化时先清空旧 Key、递增 epoch，再保存新接口身份 |
| `VodConfig.startNewLoad()` / `loadFailoverAttempt()` | `103-107`、`115-118` 行最终都会调用 `config(config)` 后加载新接口 | 不在每个调用点复制凭据逻辑，统一依赖 `config()` 的作用域切换 |
| `VodConfig.clear()` | `133-149` 行会在新接口加载前清空站点、规则和 loader 状态 | 不应无条件清除凭据，否则同接口刷新会误清；由随后调用的 `config(Config)` 根据身份决定保留或清空 |
| `VodConfig.load(Config)` | 订阅配置 JSON 在 `CatSource.normalize()` 前被解析 | 成功解析且订阅身份/epoch 匹配时，从配置响应根对象接收 `tmdb_api_key`；失败或旧 epoch 不写入 |
| `SiteApi.detailContent()` | T3/T4 原始详情在日志、解析和缓存前完整存在 | 详情根对象中的 `tmdb_api_key` 仅被 ingress 剥离并丢弃；不得写入订阅作用域 |
| `Result.fromJson()/fromType()` | 只关心 `list/code/...` 等公开字段 | subscription-config ingress 已剥离 Key，`Result` 不需要持有 Key |
| `VodDetailCache.putContent()` | `225-227` 行缓存 `Result.toString()` | 只缓存 sanitized Result，绝不缓存原始带 Key JSON |
| `TmdbConfig` | `apiKey/accessToken` 只来自用户配置，`isReady()` 表示用户配置就绪 | 新增当前订阅作用域快照与 `effective()`，不修改 `Setting` |
| `TmdbService` | 已有 api_key/Bearer 请求、认证熔断和响应缓存 | 继续接收 `TmdbConfig`；临时 Key 只能经当前订阅作用域进入 |
| `TmdbUIAdapter` | 构造时固定用户配置并长期持有 | 每次请求前取当前订阅的有效配置，旧 epoch 请求必须失效 |
| `TmdbDetailActivity` / 两个 `VideoActivity` | 无用户 Key 时只运行第 18 节路径 | 可使用当前订阅临时配置，但切换接口后旧页面不得继续使用旧 Key |
| 旧客户端 | 在解析前记录完整源详情 | 新客户端无法保护旧客户端，生产源必须做版本/能力门控 |

### 19.4 方案比较

| 方案 | 优点 | 缺点 | 决定 |
| --- | --- | --- | --- |
| 不变更，用户必须自己配置 Key | 零新增风险 | 不满足源直接提供 Key 的目标 | 仅作为关闭开关 |
| Key 放入每条 `Vod.tmdb`，当前详情使用 | 身份绑定清晰 | 不符合接口级复用目标，且容易进入详情模型/缓存 | 拒绝 |
| Key 在 App 进程中全局复用 | 使用最方便 | 切换订阅接口会串用旧 Key，不符合用户要求 | 拒绝 |
| 订阅配置响应根级 `tmdb_api_key` + 当前订阅接口临时作用域 | 与订阅身份天然同层；接口内可复用；配置切换即清空；不落盘 | 需要完整的切换、epoch 和异步失效控制 | 推荐 |
| 源端代理 TMDB，App 完全不接触 Key | 安全最好 | 需要源端实现和额外数据合同 | 长期首选替代方案 |

### 19.5 数据合同

订阅配置接口（例如 `/sub/...`）返回示例：

```json
{
  "tmdb_api_key": "<TMDB v3 API Key>",
  "sites": [
    {
      "key": "example_site",
      "name": "示例站点",
      "api": "https://source.example/api"
    }
  ]
}
```

字段规则：

1. `tmdb_api_key` 是**订阅配置响应根对象**的可选字符串字段；仅作为 TMDB v3 `api_key`，不是 Bearer Token，不是 v4 Access Token。
2. 不提供、空字符串、非字符串、含控制字符、超长或 JSON 类型异常时，按“当前接口没有源 Key”处理；错误不能破坏 `sites` 配置解析。
3. 规范化值为去除首尾空白后的 16 至 256 个可打印 ASCII 字符；不接受空白、换行和控制字符。
4. C16 详情的 `schema` 仍为 `1`，`list[].tmdb`、`Vod`、`Result` 和旧详情响应结构不变。
5. 订阅配置 Key 在 `VodConfig` 调用 `CatSource.normalize()` 和保存站点配置之前剥离；只有订阅配置解析成功、站点列表非空、当前订阅身份和 epoch 匹配且用户没有 TMDB 配置时才进入订阅级内存作用域。
6. 不允许放在 HTTP header、播放 URL、图片 URL、`vod_*`、`tmdb.detail`、`ext` 或详情响应中作为正式来源；生产者必须使用订阅配置响应根字段。
7. 详情响应根对象若出现同名 `tmdb_api_key`，客户端只在日志、解析和详情缓存前剥离，随后丢弃；它不能覆盖或补充订阅配置 Key。
8. 旧接口的异步配置/详情响应在切换后即使返回 Key，也必须因 epoch/接口不匹配而丢弃。

### 19.6 订阅接口级凭据作用域

#### 19.6.1 接口身份

“订阅接口”使用 `VodConfig` 当前 `Config` 的稳定身份：

```text
subscriptionKey = config.id + NUL + normalizedConfigUrl
```

`normalizedConfigUrl` 至少去除首尾空白和无语义尾部斜杠。若 URL 的响应会重定向，仍以用户选择并写入 `Config` 的规范化 URL 为准，不能把重定向后的临时地址当成新接口。

#### 19.6.2 数据结构

建议新增进程内单例：

```java
public final class SubscriptionTmdbCredentialStore {
    public static synchronized Scope beginSubscription(
            int configId,
            String configUrl,
            String reason
    );

    public static synchronized void accept(
            String apiKey,
            int configId,
            String configUrl,
            long scopeEpoch,
            String sourceKey,
            String sourceRevision
    );

    public static synchronized Snapshot snapshot(
            int configId,
            String configUrl,
            long scopeEpoch
    );

    public static synchronized void clear();
}
```

`beginSubscription()` 语义：

- 若当前作用域身份与传入接口相同，保留已有临时 Key 和 epoch，用于同一接口刷新/重载。
- 若身份不同，先清除旧 Key，递增 epoch，再建立新接口作用域；此时新接口 Key 为空，等待订阅配置响应。
- 首次启动或 failover 切换同样走该入口，不允许绕过。

`Snapshot` 至少包含 `apiKey`、`subscriptionKey`、`scopeEpoch`、`receivedAt`、`sourceKey` 和不可逆指纹。没有 Key 时返回空的 `Snapshot`，不能用 `null` 让调用方误读成“尚未初始化”。

#### 19.6.3 优先级

| 用户配置 | 当前接口临时 Key | 有效配置 | 行为 |
| --- | --- | --- | --- |
| 有 `accessToken` 或 `apiKey` | 有/无 | 用户配置 | 源 Key 丢弃，不能覆盖或旁路用户错误 |
| 无用户配置 | 有，且订阅身份和 epoch 匹配 | 临时源 Key | 在官方 HTTPS API 主机上作为 `api_key` 使用 |
| 无用户配置 | 无 | 无 | 保持第 18 节 source-only/原始详情 |
| 有用户配置但认证失败 | 有 | 用户配置 | 不自动回退源 Key |
| 无用户配置 | 有但 API Base 非官方 | 无 | 不发送源 Key |

#### 19.6.4 生命周期

1. **接口建立/切换**：所有路径最终进入 `VodConfig.config(Config)`；该入口调用 `beginSubscription()`，身份变化时原子清空旧 Key 和旧 epoch，同接口重载则保留。
2. **接收**：`VodConfig.load(Config)` 在订阅配置 raw JSON 进入 `CatSource.normalize()` 前提取根级 `tmdb_api_key`；`SiteApi` 对详情中的同名字段只剥离、不接受。
3. **验证与写入**：订阅配置解析成功、站点列表非空且候选接口身份、epoch 仍匹配时，才允许 `accept()`。
4. **接口内使用**：请求线程每次都使用当前订阅身份和 epoch 取快照；不同站点、条目和页面可复用，但不可跨接口。
5. **切换失效**：切换接口立即清除 Key 并递增 epoch；旧请求完成时身份或 epoch 不匹配，不能写入、刷新 UI 或影响新接口。
6. **认证失效**：401/403 只清除当前接口对应 Key；不污染其他接口的潜在值，因为其他接口的值在切换时本已清空。
7. **释放**：用户配置出现、显式清空或进程结束即释放引用。Activity `onDestroy()` 不清空，除非它同时触发了接口切换。
8. **重建**：Activity 配置变更或普通 `onDestroy()` 不丢失 Key；整个 App 进程重建后作用域为空，必须重新加载订阅配置获得。

Java `String` 无法可靠擦除底层字节，因此“不落盘”的准确含义是不进入持久化、序列化或日志路径，并在订阅作用域结束、认证失效或进程结束时清除引用；不能宣称内存字节被物理清零。

### 19.7 安全边界

#### 19.7.1 网络白名单

源 Key 只允许发送到：

- `https://api.tmdb.org/3`
- `https://api.themoviedb.org/3`

校验必须基于规范化后的 `apiBase`：HTTPS、精确 host 匹配、无用户名密码、无非标准端口。任何自定义域名、IP、镜像、代理、HTTP 或含附加认证路径的 Base 都不能注入源 Key。

#### 19.7.2 禁止泄漏面

| 泄漏面 | 必须行为 |
| --- | --- |
| `SpiderDebug` / raw detail | 先剥离根字段再记录；异常路径只记录长度、站点和解析状态 |
| URL / HTTP 异常 | `api_key`、`key`、Bearer 和查询参数全部脱敏 |
| `VodDetailCache` | 只缓存 sanitized JSON；带原始 Key 的字符串不得进入缓存 |
| `Result` / `Vod` / `TmdbSourcePayload` | 不新增 Key 字段；Gson、Parcel、copy、equals、toString 均不可见 |
| Intent / Bundle / Binder | 不传 Key 明文；界面之间只通过当前订阅作用域读取 |
| SharedPreferences / Room / 文件 / 备份 | 不写 Key；导入导出和诊断包扫描不到 Key |
| TMDB 响应缓存 | 只保存公开响应；不得保存可还原的认证 URL 或明文 Key |
| UI / 通知 / 截图 / accessibility | 不显示、不朗读、不设置文本 |
| 崩溃与性能日志 | 异常和指标不得包含根字段、认证 header 或完整请求 URL |

旧客户端的限制必须单独写清：旧版本会在 `SiteApi` 解析前记录完整源响应。新客户端无法阻止旧客户端泄漏，因此生产源必须使用客户端版本/能力门控；无法门控时不得把共享 API Key 直接返回给所有客户端。

#### 19.7.3 权限与范围

订阅级临时 Key 可以在**当前订阅接口内**由既有 `TmdbConfig` 请求路径使用，但不具备跨订阅能力：

- 只允许官方 TMDB API；
- 只允许当前订阅身份和当前 epoch 读取快照；
- 切换接口后，旧页面、旧任务和旧详情不能再读取；
- 用户设置和用户操作仍以用户配置为准；
- 不把临时 Key 写入设置、匹配绑定、历史、备份或跨进程共享；
- 不把源字段解释为 Bearer Token；
- 不允许通过自定义 API Base、代理或第三方域名使用；
- 用户配置出现后立即丢弃临时 Key。

如果产品希望进一步限制为“当前订阅内只用于详情/季集、不允许搜索或个人推荐”，应在批准阶段 2 时明确收窄；本文默认按当前订阅内的现有 TMDB 请求能力设计。

### 19.8 运行时架构

#### 19.8.1 切换与入口管线

```text
用户选择/自动切换订阅接口
  -> VodConfig.startNewLoad() / loadFailoverAttempt()
  -> VodConfig.clear().config(config)
  -> SubscriptionTmdbCredentialStore.beginSubscription(configId, configUrl, reason)
       -> 身份不同：清除旧 Key，epoch++
       -> 身份相同：保留当前 Key
  -> config(config).load()

subscription config raw JSON (/sub/...)
  -> TmdbSourceCredentialIngress.extractRootAndStrip()
       -> sanitized config JSON
       -> candidate apiKey
  -> CatSource.normalize() / sites 解析
  -> 配置有效、站点非空且当前订阅身份/epoch 匹配时 accept()
  -> 业务层只通过 effective config 读取

detailContent raw JSON (legacy compatibility only)
  -> TmdbSourceCredentialIngress.extractRootAndStrip()
       -> sanitized detail JSON
       -> candidate is discarded, never accepted
  -> Result.fromJson() / Result.fromType() / VodDetailCache
```

推荐接口：

```java
public final class TmdbSourceCredentialIngress {
    public static Ingress extractRootAndStrip(String rawJson);
}

record Ingress(String sanitizedJson, String candidateKey) {}
```

核心要求：

1. 订阅配置根字段只允许字符串；解析错误移除字段并继续 `sites` 配置解析。
2. 同一个剥离器可用于订阅配置和详情响应，但只有 `VodConfig` 的订阅配置路径可以调用 `accept()`；`SiteApi` 不得接受详情候选。
3. `VodDetailCache` 不参与 Key 刷新；详情缓存命中不能恢复或更新临时凭据。
4. 认证失败、无效形式、超长输入和异常只影响候选 Key，不影响订阅配置、普通详情和播放线路。
5. 旧接口提交来的 Key 即使格式正确，只要 `subscriptionKey` 或 `scopeEpoch` 不匹配就必须丢弃。

#### 19.8.2 有效配置

新增只读工厂方法，不改变用户 `Setting`：

```java
TmdbConfig configured = TmdbConfig.objectFrom(Setting.getTmdbConfig());
TmdbConfig effective = TmdbConfig.effective(
        configured,
        SubscriptionTmdbCredentialStore.snapshot(
                currentConfigId,
                currentConfigUrl,
                currentScopeEpoch
        )
);
```

规则：

1. `configured.isReady()` 为 true：返回用户配置副本，并清空订阅临时 Key。
2. 用户未配置且当前接口快照有 Key：返回临时 `apiKey` 配置，来源标记为 `TRANSIENT_SUBSCRIPTION`。
3. 无有效来源：返回无凭据配置，保持第 18 节路径。
4. 临时配置必须保留官方 `apiBase`、语言和图片配置；不得继承用户自定义 Base 后再注入源 Key。
5. 调用方必须同时校验 `subscriptionKey` 和 `scopeEpoch`；只有 Key 存在但接口不匹配时按无 Key 处理。

需要区分：

- `configuredReady`：用户是否在设置中配置了 Key；
- `effectiveReady`：当前订阅接口是否有用户配置或合法临时 Key。

设置页、Key 状态、备份和用户偏好继续使用 `configuredReady`；请求规划使用 `effectiveReady`。

#### 19.8.3 Adapter 接线

1. `TmdbUIAdapter` 不永久持有构造时的用户配置或临时 Key；每次加载或每个网络任务开始前读取当前订阅快照。
2. 网络任务启动时捕获 `subscriptionKey`、`scopeEpoch`、generation 和 TMDB identity；完成时必须全部匹配。
3. `VodConfig` 切换接口递增 epoch 后，旧任务的结果不得通过 `RefreshEvent`、详情缓存或 UI 更新写回。
4. `release()`、Activity `onDestroy()`、换源站点和普通页面刷新只释放当前任务引用；只有 `VodConfig` 接口切换、用户配置变化、401/403、显式重置或进程结束才清空订阅作用域。
5. `TmdbMatcher`、详情、季集、视频和推荐等现有请求都从 effective config 取凭据；临时 Key 不绕过站点策略。
6. 若产品决定限制搜索或个人推荐，则在对应入口检查 `origin == TRANSIENT_SUBSCRIPTION` 后短路；不能只依赖 UI 隐藏。

### 19.9 缓存、并发和失败策略

#### 19.9.1 缓存

1. 原始带 Key JSON：绝不缓存。
2. sanitized 详情 JSON：按现有 `VodDetailCache` 规则缓存，不含 Key。
3. TMDB 公开响应：沿用现有缓存；认证参数不得进入文件内容、文件名、日志或可还原 URL。
4. 订阅临时作用域：只在当前 App 进程和当前订阅接口中存在，不参与 `Result`、`Vod`、Parcel 或 `SavedState`。
5. 进程重启：缓存可以提供公开 TMDB 数据，但不能恢复源 Key。
6. 接口切换：即使详情或 TMDB 响应缓存仍存在，旧接口 Key 也不得被新接口读取。

#### 19.9.2 并发

1. 读取使用不可变快照；每个网络任务只持有当前接口和 epoch 对应的一次性引用。
2. `beginSubscription()`、`accept()`、`clear()` 使用同步或等价原子更新，避免切换和详情写入竞态。
3. 切换接口时先递增 epoch 再清除 Key；任何旧 epoch 的完成回调都必须丢弃。
4. 用户配置出现后，后续请求必须立即拒绝订阅临时快照。
5. 认证熔断继续使用不可逆指纹，不把原始 Key 作为 map key、日志标签或指标维度。
6. 同一接口内多个站点的 Key 以最后一次成功订阅配置响应为准；新接口不继承旧接口值。

#### 19.9.3 失败降级

| 失败 | 行为 |
| --- | --- |
| 根字段缺失或非法 | 当前接口池保持为空或旧值不变，继续第 18 节逻辑 |
| 用户已有配置 | 丢弃源 Key，使用用户配置 |
| API Base 非白名单 | 丢弃源 Key，不发送 |
| 401/403 | 只清除当前订阅临时 Key，保留已有源数据或公开缓存 |
| 429/超时/网络失败 | 保留源数据，不循环重试；是否保留 Key 由 4xx/5xx 分类决定 |
| 源数据完整 | 即使当前接口有 Key，也不因 Key 产生新的首屏请求 |
| 切换到新接口且新接口无 Key | 新接口有效配置为空，回退 source-only/原始详情 |
| 切换回旧接口 | 旧 Key 已清除；该接口的新订阅配置响应再次提供 Key 后才可使用 |

### 19.10 生产者实施合同

T3 爬虫和 T4 服务端：

1. 只在订阅配置响应根对象返回 `tmdb_api_key`；不要在详情响应、每条 `Vod.tmdb` 或订阅 URL 查询参数中重复。
2. 不把 Key 放入 HTTP header、播放地址、图片地址、`vod_*`、错误信息或第三方 analytics。
3. 尽量通过 HTTPS 提供订阅配置；HTTP 会暴露 Key，必须在源端风险评估中单独处理。
4. 优先使用可轮换、低配额、可撤销的共享 Key，并准备失效后的快速轮换流程。
5. 源端必须实施客户端版本/能力门控；旧客户端会记录完整 raw response。
6. 源已经提供完整 C16 数据时应优先返回数据；Key 只用于客户端确有缺失能力的场景。
7. 源端不得假设 Key 会跨订阅接口复用；客户端切换接口时会主动清除。

### 19.11 实施文件与职责

| 文件 | 计划改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java` | 在统一的 `config(Config)` 入口比较订阅身份并建立作用域；`load(Config)` 在订阅配置解析和站点校验成功后从响应根对象接收 Key；`clear()` 不无条件清除凭据 |
| `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java` | 详情入口先执行根级剥离，防止 legacy 同名字段进入日志/缓存；但绝不调用 `accept()`，也不把详情字段写入临时作用域 |
| 新增 `TmdbSourceCredentialIngress.java` | 根字段解析、类型/长度/字符校验、sanitized JSON 生成和无异常降级 |
| 新增 `SubscriptionTmdbCredentialStore.java` | 订阅身份、epoch、可选 Key、原子切换/清理和快照读取 |
| `app/src/main/java/com/fongmi/android/tv/bean/TmdbConfig.java` | 当前订阅的有效配置、临时来源标记、官方 host 校验；不改用户持久配置 |
| `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java` | 认证/URL 脱敏、临时凭据指纹熔断、保证缓存不保存 Key |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java` | 每次请求读取当前订阅有效配置，校验订阅身份/epoch/generation |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | 无用户 Key 时按当前订阅有效配置决定网络补齐；切换后回退第 18 节 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 原生增强路径使用当前订阅有效配置，不把 Key 放入 `Vod` |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 与 Mobile 等价接线和生命周期验证 |
| 测试目录 | 订阅配置根字段、详情 legacy 字段剥离、订阅切换清空、接口内复用、旧响应隔离、401、缓存、日志、Parcel 和双端设备测试 |

不得修改 `TmdbSourcePayload.SCHEMA_VERSION`，不得新增 `Vod.tmdb.key`，不得让 `Result` 或 `Vod` 携带 Key。

### 19.12 分阶段实施计划

每阶段独立提交、验证和回滚；前一阶段的泄漏/作用域边界未通过，不得进入下一阶段。

#### 阶段 1：订阅配置根级接收、详情剥离、订阅作用域和不落盘

范围：

- 新增 `TmdbSourceCredentialIngress` 和 `SubscriptionTmdbCredentialStore`；
- `VodConfig.load(Config)` 在 `CatSource.normalize()` 前剥离订阅配置根级 `tmdb_api_key`，配置有效且身份/epoch 匹配后写入作用域；
- `VodConfig` 在接口切换前建立新作用域，切换时清空旧 Key；
- `SiteApi` 在详情日志/解析/缓存前仍剥离 legacy `tmdb_api_key`，但丢弃候选、不写入作用域；
- 锁定 `Result`、`Vod`、`TmdbSourcePayload`、Parcel 和详情缓存的不可见性。

验收：

- 原始 Key 不出现在 sanitized JSON、日志、详情缓存、`toString()`、Parcel、偏好、数据库或文件；
- 无根字段旧订阅配置逐字段保持当前行为；
- 新接口没有 Key 时旧接口 Key 不能被读取；
- 重复、非法、超大订阅配置根字段只影响 Key 候选，不影响站点解析；详情 legacy 根字段只影响剥离，不影响详情。

#### 阶段 2：有效配置、接口内复用与切换竞态

范围：

- 实现当前订阅 `effective()`、官方 Base 白名单和 configured/effective 双语义；
- `TmdbUIAdapter`、详情页、Mobile/Leanback 原生增强改用当前订阅有效配置；
- 用户配置出现时清空临时作用域；
- 旧接口的异步详情、匹配和详情补齐结果在 epoch 不匹配时全部丢弃。

验收：

- 用户配置优先，源 Key 不覆盖用户；
- 同一订阅内跨站点、条目、详情和 Activity 保持可用；
- 切换订阅接口立即清空，新接口无 Key 时为空，新接口有 Key 才生效；
- 自定义 Base、HTTP、非白名单 host、401/403 均不会继续发送。

#### 阶段 3：详情、季集、视频、推荐能力收口

范围：

- 复用 C16 `TmdbSourceCapabilityPlanner` 的首屏缺口；
- 接通季、集、视频、推荐/相似和个人推荐的现有请求；
- 保持完整源数据零新增请求和 fill-only 合并；
- 切换接口时取消或隔离所有旧详情任务。

验收：

- 完整 payload 零网络；
- 缺失能力只按现有 planner 请求；
- 电视季集和推荐分页遵守当前订阅作用域、取消和去重；
- 无效 Key 回退不出现空标题、空列表或 Key 提示。

#### 阶段 4：泄漏扫描与回归

范围：

- 扫描 `SpiderDebug`、异常、URL、偏好、Room、文件、备份、Parcel、Intent、Bundle 和诊断导出；
- 补齐 T3/T4 同合同测试；
- 记录测试 Key 的注入、切换、清空和撤销结果。

验收：

- 自动化与设备日志均找不到测试 Key；
- 用户配置 Key、无 Key 第 18 节路径、当前订阅临时路径互不回归；
- 临时作用域在接口切换、接口内复用、401/403、用户配置和进程重启场景行为正确。

#### 阶段 5：Mobile/Leanback 设备验收

场景：

- 无用户 Key、根字段合法、源 payload 完整/部分/仅身份；
- 用户已配置 Key 且源同时返回 Key；
- 同一接口跨条目/跨站点复用；
- 切换到无 Key 新接口后不回退旧 Key，新接口返回 Key 后才可用；
- 切回旧接口后旧 Key 不恢复；
- 401/403、进程重启和 Activity 重建；
- Mobile 与 Leanback 各至少 3 组核心场景。

验收：

- 记录请求端点、次数、源码来源、当前订阅身份、UI 状态和 Artifact SHA-256；
- 真实第三方 Key 未验证时必须标记为未验证，不能用 stub 通过替代。

### 19.13 验收矩阵

| 编号 | 场景 | 预期 |
| ---: | --- | --- |
| 19-A1 | 旧订阅配置与旧 T3/T4 均无 `tmdb_api_key` | 与第 18 节完全一致，无新增 TMDB 请求 |
| 19-A2 | 用户已配置 Key，订阅配置返回根字段 | 只用用户配置；源值不进入作用域、不进入日志/缓存 |
| 19-A3 | 无用户配置，订阅配置合法根字段 + 完整 payload | 源数据上屏；首屏 TMDB 请求数为 0 |
| 19-A4 | 无用户配置，订阅配置合法根字段 + 部分 payload | 按有效配置只补缺失能力，源字段不被覆盖 |
| 19-A5 | 无用户配置，订阅配置合法根字段 + 仅身份 | 可用有效配置加载详情；失败回退原始详情 |
| 19-A6 | 同一订阅接口内跨站点/条目/详情 | 临时 Key 持续可用，不清空 |
| 19-A7 | 切换到无 Key 的新订阅接口 | 旧 Key 先清空；新接口有效配置为空，绝不沿用旧 Key |
| 19-A8 | 切换到有 Key 的新订阅接口 | 新订阅配置返回 Key 后加载；旧接口值与旧异步结果均不可用 |
| 19-A9 | 切回原订阅接口 | 之前 Key 已清除，必须重新由该接口的订阅配置响应提供 |
| 19-A10 | 订阅配置根字段非法/重复/超长 | 不写作用域、不崩溃、站点配置正常；详情 legacy 同名字段只剥离 |
| 19-A11 | 自定义 API Base | 不发源 Key，按无有效配置处理 |
| 19-A12 | HTTP/非白名单 host | 不发源 Key |
| 19-A13 | 站点被 TMDB 策略排除 | 不新建临时请求，保持普通详情 |
| 19-A14 | 401/403 | 清除当前订阅临时 Key，保留源数据/公开缓存，无循环重试 |
| 19-A15 | 429/超时 | 不无限重试，按失败策略回退 |
| 19-A16 | 进程重启 | 作用域为空；公开缓存不能恢复 Key |
| 19-A17 | `Result.toString()`、Parcel、详情缓存、Room、偏好 | 均不包含/不恢复 Key |
| 19-A18 | 日志、崩溃、URL、诊断、备份 | 扫描不到 Key |
| 19-A19 | 订阅配置接口 + T3/T4 详情 | 配置根字段是唯一凭据来源；详情 legacy 字段只剥离；订阅身份、epoch、优先级和请求计划一致 |

### 19.14 验证证据要求

1. 单元测试覆盖订阅配置根字段合法、缺失、重复、非字符串、超长、控制字符，以及无 `sites`/无 `tmdb` 上下文；详情 legacy 同名字段必须仅剥离。
2. 订阅作用域测试覆盖同接口复用、不同接口清空、A→B→A 不复用旧 Key。
3. 并发测试证明切换接口后旧 epoch 的订阅配置、详情、匹配和补齐响应不能写入凭据或刷新 UI。
4. 序列化测试证明 `Result.toString()`、`Vod.toString()`、`TmdbSourcePayload.ProtocolAdapter` 和 Parcelable 不包含 Key。
5. 缓存测试证明 raw 带 Key JSON 不进入 `VodDetailCache`，sanitized JSON 保持现有缓存行为。
6. 配置测试证明用户 Key 优先、官方 Base 白名单、非官方 Base 禁用、401/403 清理和用户配置覆盖。
7. 生命周期测试证明同接口 Activity 重建不清空，接口切换立即清空，进程重建后为空，卸载/备份/诊断包找不到 Key。
8. 请求测试记录端点、次数、订阅身份、epoch、认证来源和回退状态；日志、异常和缓存文件扫描不到测试 Key。
9. 最终至少执行 `git diff --check`、定向单测、双 flavor Java 编译和可用设备场景；构建成功不能替代安全验证。

### 19.15 风险、兼容性与回滚

| 风险 | 等级 | 控制 |
| --- | --- | --- |
| 切换订阅接口后旧 Key 串用 | 高 | `beginSubscription()` 比较接口身份，变化时先清 Key、递增 epoch，再加载新接口 |
| 旧异步详情在新接口写入 Key | 高 | Key 写入和业务消费同时校验 subscriptionKey + scopeEpoch |
| 旧客户端记录 raw root field | 高 | 服务端版本/能力门控；无法门控则不投放共享 Key |
| 自定义 Base 截获 Key | 高 | 仅官方 HTTPS host，其他配置直接忽略 |
| 日志、缓存、Parcel、备份泄漏 | 高 | 入口剥离、sanitized 缓存、订阅内存作用域和全路径测试 |
| 共享 Key 被限流/撤销 | 中高 | 源端轮换、限额、完整数据零请求、尊重 429，不做无限重试 |
| 同一接口重载时误清 Key | 中 | 身份相同且 epoch 相同时保留；身份变化才清空 |
| Java String 无法物理擦除 | 中 | 限定作用域和引用释放；不承诺物理清零 |
| 数据合同破坏旧 C16 | 低 | 根字段可选、schema 不变，无字段行为不变 |
| 权限在订阅内扩展到搜索/推荐 | 中 | 若产品要求收窄，阶段 2 可配置为详情/季集白名单 |

兼容性结论：

- 不影响无根字段的旧源、旧 T4 和无 Key 第 18 节行为；
- 不改变用户配置 Key 的现有功能和优先级；
- 不改变 C16 `tmdb` 内嵌数据、`complete` 和 fill-only 合并合同；
- 不增加依赖、ABI、二进制、许可证或网络协议；
- 可单独关闭：服务端停止返回根字段，App 入口不再写入订阅作用域即可回退。

回滚：

1. 移除 `VodConfig.config(Config)` 对 `beginSubscription()` 的调用，再移除 `SiteApi` 根字段提取；未知字段自然被忽略。
2. 删除 `SubscriptionTmdbCredentialStore` 的使用点，恢复直接使用用户 `TmdbConfig`。
3. 生产者停止返回 `tmdb_api_key`；无需修改 C16 `schema` 或每条 `Vod.tmdb`。
4. 如果发生泄漏，先轮换源 Key 并停止投递该字段，再回滚 App；不能只依赖代码回滚解决已暴露凭据。

### 19.16 实施完成定义

- **设计完成**：本节的根字段、订阅身份、切换清空、接口内复用、epoch、文件清单、验证矩阵和回滚通过用户确认。
- **阶段 1 完成**：剥离、sanitized 日志/缓存、订阅作用域写入和切换清空测试通过。
- **阶段 2 完成**：用户优先、接口内复用、切换清空、官方 Base、401/403 和旧响应隔离通过。
- **阶段 3 完成**：详情/季集/视频/推荐复用当前订阅能力，完整数据零新增请求。
- **阶段 4 完成**：日志、缓存、偏好、Room、备份、Parcel、Intent、进程重启扫描通过。
- **发布完成**：源端有版本门控和 Key 轮换方案，双端设备矩阵、Artifact SHA-256、提交和 recovery tag 已记录。

任一项没有证据，不得宣称“订阅接口级 TMDB API Key 支持已完成”或“Key 已安全保存”。

### 19.17 评审证据

| 声明 | 来源 | 等级 | WebHTV 适用性 | 决策影响 |
| --- | --- | --- | --- | --- |
| TMDB v3 使用 query `api_key` 或 Bearer；官方示例使用 `https://api.themoviedb.org/3` | [TMDB Application Authentication](https://developer.themoviedb.org/docs/authentication-application)，页面 `updated_at=2025-10-27`，访问于 2026-09-19 | A（官方文档） | 现有 `TmdbService` 已支持两种认证；本节只新增 v3 `api_key` | 根字段只解释为 v3 API Key |
| TMDB 仍有限流和 429 要求，约每秒 40 次级别 | [TMDB Rate Limiting](https://developer.themoviedb.org/docs/rate-limiting)，页面 `updated_at=2025-10-20`，访问于 2026-09-19 | A（官方文档） | 订阅接口内可能复用共享 Key | 保留 source-first、禁止无限重试、限制并发并尊重 429 |
| TMDB API 免费用于非商业项目、要求 attribution，官方强烈建议 SSL；商业用途需许可 | [TMDB FAQ](https://developer.themoviedb.org/docs/faq)，页面 `updated_at=2025-10-07`，访问于 2026-09-19 | A（官方文档） | 源 Key 共享不改变产品性质，但 HTTP/泄漏影响凭据安全 | 生产要求 HTTPS、归属展示和源端许可评估 |
| `api.tmdb.org` 与 `api.themoviedb.org` 当前均为官方 TLS 域名，未带 Key 请求返回 401 | 2026-09-19 `openssl s_client` 和 HTTPS 探测 | A（可复现实测） | 默认 API Base 为 `api.tmdb.org/3` | 订阅临时作用域只允许这两个官方 HTTPS host |
| `VodConfig.config(Config)` 是启动、手动切换和 failover 设置当前接口的统一入口；`clear()` 会先执行但在随后配置新接口 | `api/config/VodConfig.java:90-149`；`bean/Config.java:22-35,133-155` | A（当前源码） | 能在身份变化时统一清空旧 Key，同时避免同接口刷新误清；`Config.id/url` 可构成稳定接口身份 | 作用域清理接入 `config()`，不散落在 Activity |
| 当前 `SiteApi` 先记录 raw detail，再解析 `Result`，`VodDetailCache` 缓存 `Result.toString()` | `api/SiteApi.java:174-183,211-227` | A（当前源码） | 根级字段必须最先剥离，不能依靠 `Vod` 事后清理 | 新增入口 ingress 与 sanitized JSON 管线 |
| `Vod.tmdb` 进入 Parcelable、copy 和缓存合同；当前 `TmdbConfig` 只表达用户配置 | `bean/TmdbSourcePayload.java:151-217`、`bean/Vod.java:93-115,372-394`、`bean/TmdbConfig.java:16-43,98-164` | A（当前源码） | 临时 Key 不能进入 `Vod`，也不能写回 Setting | 新增独立订阅内存作用域、effective config 和白名单 |

外部 PR、Issue、论文和性能基准对本节不构成必要证据：本能力是本地凭据生命周期和 JSON 合同设计，不涉及播放器 ABI、性能算法或依赖升级。

### 19.18 最终建议

建议实施，但先批准第 19.12 节阶段 1，并要求先证明：

- 根字段在日志、缓存、Gson、Parcel、`Vod`、Room 和备份中不可见；
- 无根字段旧响应与第 18 节完全一致；
- 用户已配置 Key 时源 Key 不写入订阅作用域、不产生任何请求；
- 同一订阅接口内跨站点、条目和 Activity 可以使用；
- 切换到新订阅接口时旧 Key 立即清空；新接口无 Key 则保持为空，新接口有 Key 才加载；
- A→B→A 不会恢复 A 的旧 Key；
- 无用户配置时，订阅临时作用域只在官方 HTTPS host 运行，401/403 后能清理并回退。

阶段 1 通过后再进行阶段 2。本文明确拒绝“整个 App 进程全局复用”的实现；如果后续要扩大作用域，必须另行设计并与订阅切换清理策略一起评审。

### 19.19 实施记录

> 说明：本节“订阅配置根级修订”之前的记录保留了当时实现的提交事实，其中把 `SiteApi` 详情根字段作为接收入口的表述已被下面的修订取代。

#### 阶段 1：根级剥离、订阅作用域和不落盘

- 状态：已完成并收口。
- `TmdbSourceCredentialIngress`：在 T3/T4 共用入口剥离根级 `tmdb_api_key`，只接受去首尾空白后的 16-256 位可打印 ASCII Key；缺失时原样保留旧 JSON，非法、重复、非字符串、控制字符和超长值只丢弃候选 Key。
- `SubscriptionTmdbCredentialStore`：Key 只存在于进程内当前订阅作用域；身份使用 `configId + normalizedUrl`，接口切换和显式清理递增 epoch，同一接口重载保留，A→B→A 不恢复旧 Key。
- `SiteApi`：原始详情在 `SpiderDebug`、`Result` 解析和 `VodDetailCache` 之前统一剥离；只有详情列表有效、当前 epoch 匹配且用户未配置 TMDB Key 时才写入订阅作用域。
- `VodConfig.config(Config)`：所有启动、切换和 failover 的统一入口调用 `beginSubscription()`；`clear()` 不无条件清除凭据。
- 验证：`git diff --check` 通过；`bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.api.TmdbSourceCredentialIngressTest' --tests 'com.fongmi.android.tv.api.config.SubscriptionTmdbCredentialStoreTest' --tests 'com.fongmi.android.tv.api.TmdbSourceCredentialWiringTest'` 返回 `BUILD SUCCESSFUL in 9s`，12 项测试通过。
- 提交：`a50de802a1c2c3491088fa0307c0484bc95e7f1e`；tag：`recovery/C16-19-stage1/20260919190829-a50de802a1c2`。
- 回滚：撤销本阶段提交即可恢复未知根字段被忽略的旧行为；若发现 Key 泄漏，必须先轮换源 Key 并停止源端投递。

#### 阶段 2：有效配置、接口内复用与切换竞态

- 状态：已完成并收口。
- `TmdbConfig.effectiveCurrent()` / `effective()`：用户 `apiKey` 或 `accessToken` 始终优先；无用户配置时，当前订阅快照才生成 `TRANSIENT_SUBSCRIPTION` 配置，并固定使用 `https://api.tmdb.org/3`，保留语言和图片配置；临时配置的 `toJson()` 不包含源 Key。
- 官方 Base 白名单：仅允许 HTTPS 的 `api.tmdb.org/3` 与 `api.themoviedb.org/3`，拒绝端口、用户信息、查询、附加路径、HTTP 和自定义镜像；`TmdbService` 在请求构造前再次防守。
- `TmdbUIAdapter`：构造和每次 `prefetch/load/autoMatch/search` 入口重新读取有效配置；本页保留订阅身份与 epoch，旧 epoch 的详情、匹配、季集、视频和推荐回调都不能通过 `isCurrentGeneration()` 写回；`ConfigEvent` 调用 `invalidateSubscription()` 取消旧任务。
- Mobile/Leanback：详情运行策略改用有效配置；订阅切换事件使 Adapter 进入失效状态，不再复用旧接口任务。
- `TmdbDetailActivity`：初始化与源详情返回后刷新有效配置；订阅切换触发详情重新加载，旧 generation 结果被丢弃。
- `TmdbService`：源 Key 命中 401/403 时清理当前订阅临时作用域；熔断键仍只使用不可逆摘要，不记录 Key。
- 验证：定向 15 项单测通过，Mobile 与 Leanback `Arm64_v8aDebug` Java 编译均返回 `BUILD SUCCESSFUL`；`git diff --check` 通过。
- 提交：`7cc55beae44c891e01c0cbc5c4ea2ffee844cb3f`；tag：`recovery/C16-19-stage2/20260919191419-7cc55beae44c`。
- 回滚：撤销本阶段提交即可恢复用户配置直连和页面内固定配置；阶段 1 的剥离与内存作用域保持独立。

#### 阶段 3：详情、季集、视频和推荐能力收口

- 状态：已完成并收口。
- `VideoActivity` 的 Mobile/Leanback 路径改为：只要 C16 source 状态为 `RENDERABLE` 就优先调用 `loadSource()`；没有可用 Key 时保持完全离线，有有效 Key 时仅按 planner 补齐首屏缺口。
- `TmdbUIAdapter.loadSource()` 先把 source bundle 直接上屏，再计算首屏 `core/credits/images` 缺口；完整 source 不发起请求，部分 source 使用 `detailForSource(missing)`，结果通过 `TmdbSourceMerger.fillOnly()` 合并，源字段不被覆盖。
- 季集缓存继续从 source bundle 预填；无 Key 的 `sourceOnly` 路径在缓存未命中时直接返回空列表，不再尝试 TMDB 网络。
- 视频打开前先用 `TmdbSourceCapabilityPlanner.isAvailable()` 检查 `videos`、`season_videos` 或 `episode_videos`；完整源组本地读取，缺失组才使用当前订阅有效配置。
- `PersonalRecommendationService`、Mobile/Leanback 单集详情、人物详情页与人物弹窗的请求入口改用 `TmdbConfig.effectiveCurrent()`，当前订阅临时 Key 在当前页面链路内复用。
- 验证：6 个定向测试类共 19 项通过，覆盖 planner、merger、source-only、source-first 接线、运行模式与有效凭据入口；Mobile 与 Leanback `Arm64_v8aDebug` Java 编译均返回 `BUILD SUCCESSFUL`；`git diff --check` 通过。
- 提交：`73abd0350b9808c5da727763deff3764e590d970`；tag：`recovery/C16-19-stage3/20260919192119-73abd0350b98`。
- 回滚：撤销本阶段提交即可恢复仅无 Key 时使用 source、普通网络路径仍按用户配置匹配的旧行为；阶段 1-2 的凭据作用域不受影响。

#### 阶段 4：泄漏扫描与回归

- 状态：已完成并收口。
- `TmdbService.execute()`：OkHttp 异常重新包装前先调用 `redactMessage()`，查询参数 `api_key/apikey/key/token/access_token` 和 Bearer 值不会进入异常文本、`SpiderDebug` 或上层日志。
- T3/T4 合同回归：同一 `tmdb` 对象经过根字段剥离后使用相同模型，序列化、详情缓存和两个详情入口的结果不包含源 Key；`Vod`、`Result`、`TmdbSourcePayload`、`VodDetailCache` 源码中没有根字段。
- 生命周期回归：测试覆盖合法 Key 注入、同一订阅复用、不同接口清空、A→B→A 不复用、403 清理、用户配置优先和 `toJson()` 不写临时 Key。
- 泄漏面核对：日志/异常经脱敏；详情缓存只接收 sanitized JSON；TMDB 缓存文件名使用 MD5；临时 Key 不进入 Preference、Room、Backup、Intent、Bundle 或 Parcel 模型；诊断文本走已有敏感字段过滤。
- 验证：C16 定向回归共 57 项通过；Mobile 与 Leanback `Arm64_v8aDebug` Java 编译均返回 `BUILD SUCCESSFUL`；`git diff --check` 通过。
- 提交：`d2136e463391a6c34a4bf042d85cc001982cd001`；tag：`recovery/C16-19-stage4/20260919192800-d2136e463391`。
- 回滚：撤销本阶段提交即可恢复未脱敏的底层网络异常文本和旧合同测试；凭据生命周期实现不受影响。已暴露在旧版本日志中的 Key 必须轮换，不能用代码回滚补救。

#### 阶段 5：Mobile/Leanback 设备验收

- 状态：已完成并收口。
- 设备与夹具：分配设备 `SM-N9700`、Android 9、`192.168.50.3:5557`；使用 `/tmp/c16_subscription_fixture.py`、`/tmp/c16_keyless_fixture.py` 和 `adb reverse tcp:18080/18081`。测试结束后已停止本地夹具并移除 5557 的反向端口。
- 凭据矩阵：Mobile 和 Leanback 各 5/5 通过，覆盖根字段接收与 source UI、部分 source 回退、同一订阅复用、A→B→A 不恢复、用户配置优先、非法 Key 不写入。
- 无 Key 回归：Mobile 和 Leanback 各 3/3 通过，覆盖独立详情页、原生增强详情和无扩展旧源回退，无 Key 提示。
- 泄漏扫描：设备测试 `@After` 在包仍安装时递归读取 app data 和 external files，测试 Key 未出现在持久化文件、偏好、数据库或缓存；5557 `logcat -d` 也未命中测试 Key。
- Artifact SHA-256：
  - Mobile APK：`26e87690843c0c0b8f3cb0abf35110ddeea8a66ad100c08685c787db6972a217`
  - Leanback APK：`9829589622294d2f087a0d949928d131dcc718435c952c3aad7d154d83cb9256`
  - Mobile androidTest APK：`fdeaeb20df76b74641ef748f4865e478f165bcb9264776dbecefc504dfd59236`
  - Leanback androidTest APK：`7bb4c76601170d03aa482db220d27a7a6581299c5f1a8cc4a9b8d59c5e4bcc1d`
- 未验证：工作区未提供真实第三方 TMDB Key；设备矩阵验证了无效 Key 不得误写、用户优先、source 回退与清空路径，但真实有效 Key 的网络补齐成功、真实 401/403 和源端版本门控/轮换仍未验证。确定性 401 状态处理仍由 JVM 单测覆盖，不能用 stub 结果替代真实 Key 结论。
- 提交：`fd593f3d3c3972811c7572ca92342b78f16b9fb3`；tag：`recovery/C16-19-stage5/20260919201842-fd593f3d3c39`。
- 回滚：撤销本阶段测试提交即可恢复原有无 Key 三场景设备测试；生产实现及阶段 1-4 锚点保持不变。

#### 订阅配置响应根级修订（2026-09-19）

- 用户确认的预期层级：`http://192.168.50.50:4567/sub/2024/buye-991566.xyz` 这类订阅配置接口返回 JSON 的**根对象**携带 `tmdb_api_key`，例如与 `sites` 同级。
- `VodConfig.load(Config)`：在 `CatSource.normalize()` 前调用 `TmdbSourceCredentialIngress.extractRootAndStrip()`；`sites` 配置解析成功、站点列表非空且当前 `configId + normalizedConfigUrl + epoch` 匹配后，才把 Key 写入当前订阅内存作用域。
- `SiteApi.detailContent()`：保留 ingress 剥离以满足旧源/自定义爬虫的泄漏防护，但候选 Key 不再传给 `SubscriptionTmdbCredentialStore`；详情根字段不能成为凭据来源。
- 用户配置优先级保持不变：用户 `apiKey/accessToken` 存在时，即使订阅配置返回合法 Key，也不写入临时作用域。
- 切换语义保持不变：切换订阅接口先清空旧作用域并递增 epoch；切回原接口必须由新的订阅配置响应重新提供 Key，而不是恢复旧值。
- 测试更新：定向 JVM 测试覆盖订阅配置根字段解析、详情 legacy 字段剥离但不接受、订阅作用域切换；设备夹具改为只有 `/config.json` 根对象携带 Key，详情端点不携带 Key。
- 验证：7 个定向 JVM 测试类共 26 项通过；分配设备 `SM-N9700/Android 9 @ 192.168.50.3:5557` 上 Mobile 凭据 5/5 + 无 Key 3/3、Leanback 凭据 5/5 + 无 Key 3/3 通过；app 私有目录自动扫描和 `logcat -d` 均未发现测试 Key。
- 提交：`b51a3795e9c88d2f370271f64513e5928d6e067f`；tag：`recovery/C16-config-root-credential/20260919211254-b51a3795e9c8`。
- 回滚：撤销本次修订即可恢复“详情响应根字段接收”的旧实现；已暴露在日志或旧客户端中的 Key 必须轮换。

#### 认证失败竞态修复（2026-09-19）

- 设备复现：5557 上旧进程日志显示 `runtime policy mode=1 runtime=2 source=ABSENT_OR_INVALID tmdbReady=false`，详情页因此回退 `VideoActivity`；重启并重新加载订阅配置后，同一详情返回 `runtime=1 tmdbReady=true bundle=true`。
- 根因：旧订阅或旧测试任务的 401/403 会无条件调用 `SubscriptionTmdbCredentialStore.clear()`，可能在新订阅配置已接收 Key 后把新凭据一起清掉。
- 修复：`TmdbConfig` 的临时凭据快照携带 `subscriptionKey + scopeEpoch`；`TmdbService` 的 401/403 改为 `clearIfCurrent()`，只清除与失败请求相同订阅身份和 epoch 的 Key。旧任务失败不再影响新订阅。
- 诊断日志：订阅配置接收路径新增低敏 `tmdb-credential` 日志，只记录候选是否存在、用户配置是否就绪、订阅 ID/URL 是否匹配、accept 结果和 epoch，不记录 Key 内容。
- 测试：新增 JVM 与设备用例，构造 A→B 后由 A 的旧请求返回 401，断言 B 的 Key 保持不变；B 自身 403 仍会清除 B。
- 提交：`9cd5c6399dac79411354c1be104d28a826e37127`；tag：`recovery/C16-config-key-runtime-diagnosis/20260919215433-9cd5c6399dac`。

#### 远端 beta 合并后的凭据边界硬化（2026-09-19）

- 订阅配置加载的凭据接收以实际加载参数 `Config` 为准，而不是全局 `getConfig()`；迟到的旧订阅响应无法再把 Key 写入新订阅作用域。
- 同一订阅内检测到 Key 替换时推进 `scopeEpoch`；`TmdbService` 在每次请求前验证临时凭据快照仍属于当前订阅，失效快照直接拒绝。
- 畸形 JSON 的 ingress 在结构性解析失败且检测到根字段时返回空对象，不再把可能含 Key 的原文本交给日志、缓存或后续解析。
- `TmdbUIAdapter` 的订阅失效路径和 Leanback 缓存详情入口先刷新有效凭据再开始新任务，避免切换后继续使用旧配置。
- 验证：17 个定向 JVM 测试类共 248 项通过，Leanback `Arm64_v8aDebug` Java 编译通过；详细合并与 PR 记录见 `docs/beta-sync-review-dev2-20260919.md`。
