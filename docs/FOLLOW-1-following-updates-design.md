# WebHTV 追更功能完整设计与验证方案

> 状态：设计稿，待评审；本文只定义方案，不代表已获准实现
> 任务编号：`FOLLOW-1`
> 设计日期：2026-09-19
> 评估基线：`dev4@552b68bb8e781d06ca989abfd8caa96a567e494a`
> 参考版本：`power721/atv-player@5867e5f16c8bdb8a320d3ea6bb7f242afd2f02a0`、`power721/alist-tvbox@5bb4b93fbdb2dc6fe61c585e83302e37fe2c7acd`
> 适用范围：Android Mobile、Android TV/Leanback、现有单机备份与跨设备一键同步
> 目标读者：实现者、评审者、测试者、后续维护者

## 0. 结论摘要

可以给 WebHTV 增加追更功能。推荐实现为：

1. **客户端本地追更**：以 TMDB/TV 剧集身份为主键，记录官方播出状态、当前剧集进度、提醒水位和来源绑定。
2. **独立数据库 `following.db`**：不升级现有 `tv` 数据库的 45 版本，避免把新功能迁移风险和全库回滚风险绑定在一起。
3. **后台调度使用 WorkManager**：周期检查最小 15 分钟，业务默认 6 小时兜底；下一集预计播出后再安排一次性检查；手动检查走前台任务。不会承诺分钟级准时提醒。
4. **官方更新和可播状态分离**：TMDB 的“已播出”不等于当前站点“已可播”。列表必须同时显示“官方已更新至”和“当前源可播至”。
5. **首版不内置 alist-tvbox 的网盘巡检、转存和自动换源**：只预留服务端订阅导入接口；如果用户部署了 alist-tvbox，可读取其 `currentEpisodes` 作为可播信号。
6. **不直接移植参考项目代码**：两个参考仓库当前均未声明 License 且没有 LICENSE 文件。WebHTV 是 GPLv3 项目，不能把未授权代码直接复制进来；只能基于功能行为、接口语义和独立实现完成 clean-room 适配。

### 0.1 决策摘要

| 决策 | 结果 | 原因 |
|---|---|---|
| 追更是否复用 `Keep` | 不复用 | `Keep` 同时承载点播收藏、直播频道和频道收藏，语义不同；复用会破坏现有收藏行为 |
| 首版是否支持电影 | 不支持 | 电影没有持续“新集”语义，建议只对 TMDB `tv`、动漫、剧集、综艺等连续内容开放 |
| 官方元数据来源 | TMDB 优先，原站详情兜底 | TMDB 提供稳定季集、播出状态和下一集；原站详情只代表当前可播情况 |
| 数据库位置 | 独立 `FollowingDatabase` v1 | 不升 `AppDatabase` v45，便于禁用/回滚，事件隔离 |
| 后台调度 | WorkManager | 满足 Android 后台约束；不使用精确闹钟或常驻前台服务 |
| 自动换源 | 首版不做 | 首版只提示“当前源缺集”并允许用户手动换源；自动搜索/切换留给后续阶段 |
| 服务端联动 | 默认关闭、人工导入 | 依赖外部部署、网络和账号；不能成为本地追更的必要条件 |
| 首次添加是否提醒 | 不提醒 | 首次添加只建立基线，避免用户刚加入就收到“更新”通知 |

---

## 1. 背景与目标

### 1.1 用户问题

当前 WebHTV 已支持收藏和观看历史，但没有稳定的“我追的剧更新了没有”状态。用户需要：

- 加入“我的追更”，而不是只把作品放进收藏。
- 知道官方已经播到哪一集、当前来源可播到哪一集。
- 知道从上次观看后新增了几集，能一键继续看。
- 在没有打开原站详情页时也能看到更新提醒。
- 在手机端和电视端看到一致的状态。
- 手机、电视和局域网同步设备之间不互相覆盖进度和提醒状态。

### 1.2 首版目标

首版必须完成：

1. 从详情页加入/取消追更。
2. 追更列表、更新筛选、继续播放、手动检查。
3. TMDB 官方集数/状态/下一集解析。
4. 当前来源可播集数探测，但必须与官方状态分开表达。
5. 后台周期检查和一次性到点检查。
6. 去重通知、站内角标和已读水位。
7. 备份、恢复和跨设备同步。
8. 手机端与电视端的基本功能一致。
9. 一处可控的关闭开关，异常时可立刻停止后台检查。

### 1.3 非目标

首版不做：

- 网盘自动挂载、转存、磁力离线、AList storage 管理。
- 全自动跨站点换源和自动播放新集。
- 以通知到达时间作为精确播出时间保证。
- 电影追更、直播节目单追更、B站动态追更。
- 账号级云追更、服务端统一身份体系。
- 把 alist-tvbox 整个服务端嵌入 APK。
- 对所有收藏记录自动执行追更扫描。
- 在没有用户明确开启时向外部服务上传本地观看记录。

---

## 2. 参考项目评估

### 2.1 `atv-player`

`atv-player` 的追更思想和 WebHTV 很接近，主要实现位于：

- `src/atv_player/following_models.py`
- `src/atv_player/following_repository.py`
- `src/atv_player/following_update_service.py`
- `src/atv_player/controllers/following_controller.py`
- `src/atv_player/following_backend.py`
- `src/atv_player/ui/following_page.py`

值得借鉴的行为：

- 保存标题、原始标题、封面、评分、外部 ID、播放来源绑定和当前季集。
- 保存“官方最新集”“上次检查时间”“下次检查时间”“上次错误”“未看更新数量”等检查状态。
- 启动后延迟检查，普通时段约 6 小时一次，更新窗口缩短间隔；WebHTV 不能照搬其桌面 QTimer 频率。
- 首次添加建立基线，不把“首次发现的最新集”当作新更新。
- 更新提醒有已读/忽略水位，避免每轮重复提示。
- 播放记录可以反向更新追更进度。
- 可以从 alist-tvbox 的服务端订阅导入本地追更，并读取 `currentEpisodes` 作为服务端可播集数。

相关提交（完整 40 位 ID）：

| 提交 | 说明 |
|---|---|
| `73a0e2ed6458d55f85fe71bae55b56853abf98cb` | 新增追更仓库 |
| `74a8246d50c8be89a7f4670d4c3574a27b9d1889` | 新增追更更新服务 |
| `0176b70f432bdad137174a9e4061fbd2cdaa18a3` | 新增追更控制器 |
| `e77886fbc8694a28f98669a0716738c52fe3a429` | 避免错误追更更新提示 |
| `5d8fff942c3c5340767859fa619b1d40031f56bc` | 接入 alist-tvbox 追剧信号并支持直达网盘播放 |
| `b20f76bcdffdab8cb9615c3696e198a3795ecbed` | 服务端追剧订阅一键导入本地追更 |
| `a9c79fa8d2a3436e1656585eab41eba23601177b` | 修复开启联动后检查更新未走后端 |
| `fea2e3680590da7fb31e27d0e37df700de830684` | 追剧播放会话接入元数据刮削和来源白名单 |

### 2.2 `alist-tvbox`

`alist-tvbox` 的追更是服务端资源编排系统，核心文件包括：

- `src/main/java/cn/har01d/alist_tvbox/entity/MediaSubscription.java`
- `src/main/java/cn/har01d/alist_tvbox/service/MediaSubscriptionService.java`
- `src/main/java/cn/har01d/alist_tvbox/service/MediaSubscriptionCheckService.java`
- `src/main/java/cn/har01d/alist_tvbox/service/MediaSubscriptionNotificationService.java`
- `src/main/java/cn/har01d/alist_tvbox/web/MediaSubscriptionController.java`

其系统包含：

- 服务端订阅实体和 `next_check_time` 调度。
- AList/网盘分享资源搜索、候选池、挂载、转存和换源。
- 缺集检测、固定挂载路径、播放历史关联和通知事件。
- TVBox 播放分类和伪播放 id。
- `/api/media-subscriptions` 等管理接口。

起始提交：

| 提交 | 说明 |
|---|---|
| `83fa30ea4411d5ec79391d393082880bdaf4c909` | 追剧订阅（自动追更）系统 |

适合借鉴：

- “官方元数据”和“资源可播状态”分离的思想。
- `next_check_time`、退避、播后短轮和资源状态模型。
- 追更记录与播放历史的关系。
- 可选的服务端订阅导入契约。

不适合直接搬入首版：

- AList、网盘账号、挂载、转存、候选池和自动换源。
- 服务端 Spring/数据库/调度依赖。
- 服务端固定挂载路径和伪播放 id 协议。
- 无 License 的代码。

### 2.3 许可证结论

截至 2026-09-19，两个 GitHub 仓库 API 均返回 `license: null`，仓库根目录也没有 `LICENSE`/`COPYING` 文件。结论：

- 不复制、粘贴、翻译或机械改写其源码。
- 只保留公开行为、系统接口和可验证的架构思想。
- WebHTV 实现必须由本项目作者独立编写，并在代码评审中记录该边界。
- alist-tvbox 的 HTTP 接口只作为可选的互操作协议，不把其 Java 实现带入 APK。

---

## 3. 当前 WebHTV 实现核查

### 3.1 现有基础设施

| 能力 | 当前位置/符号 | 追更复用方式 |
|---|---|---|
| 收藏 | `bean/Keep.java`、`KeepDao`、mobile/leanback `KeepActivity` | 不复用数据表，只参考 UI 和收藏入口 |
| 历史主键 | `History` 的 `siteKey@@@vodId@@@cid` | 作为无 TMDB 时的来源回退身份 |
| TMDB 历史身份 | `History.tmdbId`、`mediaType`、`tmdbSeasonNumber`、`tmdbEpisodeNumber` | 作为跨来源播放进度主身份 |
| 分季进度 | `bean/TmdbSeasonProgress.java`、`TmdbSeasonProgressDao` | 作为播放进度的权威来源之一 |
| TMDB HTTP/缓存 | `service/TmdbService.java` | 官方集数、状态、下一集和季详情 |
| TMDB 集数策略 | `ui/helper/TmdbEpisodeInfo.java` | 复用 `last_episode_to_air`、`next_episode_to_air` 解析，但补齐强制刷新和持久化 |
| 站点详情 | `api/SiteApi.detailContent(key,id,refresh)` | 探测当前来源可播集数 |
| 来源详情缓存 | `utils/VodDetailCache.java` | 追更探测必须支持绕过过期缓存 |
| 全局搜索 | mobile/leanback `SearchActivity`、`CollectActivity` | 首版用于用户手动换源，不自动批量搜索 |
| 播放进度保存 | `History.save()`、`TmdbSeasonProgressStore.write()` | 变更后投影到追更 DB，或下次检查时重建 |
| 数据库 | `AppDatabase.VERSION = 45`、Room 45 schema | 首版不改 `tv` 数据库版本，新增独立数据库 |
| 备份 | `bean/Backup.java`、`utils/AppBackup.java` | 增加 following 部分的序列化/恢复 |
| 同步选项 | `bean/SyncOptions.java`、mobile/leanback `SyncDialog` | 增加 `follow` 类别 |
| 后台线程 | `utils/Task.java` | 手动检查、探测和投影复用线程池 |
| 通知 | `utils/Notify.java`、Manifest `POST_NOTIFICATIONS` | 新建追更通知 channel，不混用通用通知 |

### 3.2 现有缺口

1. `Keep` 没有追更水位、官方集数、下一集、来源探测和调度字段。
2. `TmdbService.detail()` 当前 detail 缓存最长可到 7 天；在线剧集不能直接依赖默认缓存判断“刚刚更新”。追更刷新必须显式 force refresh，或提供专用的刷新 API。
3. 当前没有追更后台 Worker，也没有独立的下一集检查队列。
4. `History` 有 TMDB 进度，但没有“官方播出”与“当前来源可播”分离状态。
5. `AppDatabase` 单库升级会扩大迁移影响面；首版不应把追更表直接塞进 `tv` 数据库。
6. `alist-tvbox` 的导入接口和本地追更模型没有映射层。

### 3.3 已有设计文档的边界

- `docs/unified-media-identity-cross-site-resume.md` 仍是未完全实现的统一媒体身份设计；追更首版不依赖它完成，而是复用现有 `History.tmdbId/mediaType/tmdbSeasonNumber/tmdbEpisodeNumber`。
- `docs/tmdb-playable-episode-availability-design.md` 解决的是 TMDB 季度与线路可播季度映射；追更只调用该结论，不重构详情页季度模型。
- `docs/update-history-design.md` 的“历史版本更新”是 APK 更新，不是剧集追更，不能复用其概念。

---

## 4. 领域术语与状态语义

### 4.1 术语

| 术语 | 定义 |
|---|---|
| 追更记录 | 一个“作品/季”的用户追更单元，首版建议一条记录对应一个 `seriesKey + seasonNumber` |
| 官方已播出 | TMDB 或原站元数据认为已经上线、可以观看的剧集 |
| 当前源可播 | 当前绑定的 WebHTV 站点/Vod/Flag 实际返回的剧集范围 |
| 本地观看进度 | `History` 和 `TmdbSeasonProgress` 中最后一次真实观看的季/集/进度 |
| 更新水位 | 用户已经看过、已点开、已提醒或已忽略到的剧集号 |
| 可播缺口 | 官方已播出集数大于当前来源可播集数 |
| 新集提醒 | 官方已播出集数超过提醒水位且用户未读的状态 |

### 4.2 三种状态不能混为一谈

```text
TMDB 官方状态  = 该剧集是否已经播出、总集数、下一集
  ≠
当前站源状态  = 当前站点/Vod/Flag 实际上能播放到第几集
  ≠
本地观看状态  = 用户实际看到了第几集、播放位置和时长
```

UI 文案示例：

- 官方：`已更新至第 24 集`
- 当前源：`当前源可播至第 20 集`
- 用户：`你看到第 18 集，还有 6 集未看`

### 4.3 更新判定公式

针对追更记录所跟踪的季：

```text
released  = 官方已播出集数（季内序号）
watched   = 用户已观看到的季内集号
read      = 用户已读/已忽略水位
notified  = 最近一次通知水位

unwatched = max(0, released - max(watched, read))
hasUpdate = released > max(watched, read)
shouldNotify = notified > 0 && released > max(notified, read) && notifyEnabled
```

首次添加：

```text
released > 0 时：
  read = released
  notified = released
  hasUpdate = false
  unwatched = max(0, released - watched)
```

这样可以避免“用户刚添加就被通知所有历史集数”的错误。

### 4.4 完结/取消/未开播

- `RETURNING`：继续检查；允许通知。
- `PLANNED`：不把未来集算成已更新；可以安排下一集检查。
- `ENDED`：默认不再通知；如果后续官方重新回到在播或新增特别篇，则重新进入检查。
- `CANCELED`：默认不再通知；UI 显示取消状态。
- `UNKNOWN`：保留记录，使用原站详情兜底，但降低检查频率。

---

## 5. 功能需求

### 5.1 用户故事

#### US-01 添加追更

用户在 TMDB 剧集详情页点击“追更”，系统保存作品、季、来源和当前进度基线；首次添加不发送“有新集”通知。

#### US-02 看到更新

用户打开“我的追更”，看到：

- 官方已更新到哪一集。
- 当前来源可播到哪一集。
- 自己看到哪一集。
- 新增几集、是否已读。

#### US-03 继续观看

用户点击“继续播放”，系统按现有 `History`/`TmdbSeasonProgress` 恢复到同一媒体、季、集和进度。

#### US-04 检查更新

用户点击“检查更新”，系统立即检查当前记录并显示结果；检查失败不能清空旧数据。

#### US-05 处理来源缺集

官方有更新但当前源缺集时，系统显示缺口，并提供现有全局搜索/换源入口；首版不自动切换。

#### US-06 处理提醒

用户可以选择“知道了”或“忽略到下一集”；同一集不重复通知。

#### US-07 同步

用户在一键同步中勾选追更后，作品身份、观看水位和可播状态能跨设备合并；本机的 `nextCheckAt` 和错误信息不应被远端旧值覆盖。

#### US-08 关闭功能

用户关闭追更开关后，后台 Worker 取消或停止安排新检查；已有记录保留，重新打开后继续。

### 5.2 功能验收编号

| 编号 | 要求 | 必须留下的证据 |
|---|---|---|
| F-01 | 详情页可添加/取消追更 | UI 源结构测试 + 真机点击 |
| F-02 | 首次添加不产生新集通知 | 策略单测 + 通知 fake |
| F-03 | 官方集数更新可被识别 | TMDB fixture + 策略单测 |
| F-04 | 官方更新和当前源可播分离 | 详情页/列表 fixture + UI 结构测试 |
| F-05 | 同一媒体不同来源不重复建记录 | 身份单测 + DAO 单测 |
| F-06 | 继续播放复用现有 History/TMDB 进度 | 播放恢复单测 + 真机 |
| F-07 | 手动检查失败保留旧快照 | 异常单测 + 数据库 DAO 测试 |
| F-08 | 后台周期检查不依赖精确闹钟 | Scheduler 单测 + WorkManager 测试 |
| F-09 | 通知去重到集号 | 通知 fake 单测 |
| F-10 | 手机端底部导航、电视端首页按钮均提供与点播/直播/设置同级的一级入口，并能打开追更列表 | 入口源结构测试 + 两端真机 |
| F-11 | 备份恢复保留追更 | Backup 单测 + 恢复测试 |
| F-12 | 同步不会用旧数据覆盖新数据 | merge 单测 |
| F-13 | 关闭开关停止后台检查 | Scheduler 单测 |
| F-14 | 现有收藏/历史/播放无回归 | 现有相关测试集 |
| F-15 | 不修改 `AppDatabase` v45 schema | `git diff` 检查 + Room schema 检查 |

### 5.3 非功能要求

- **性能**：列表首屏不触发 N 次网络请求；默认只读本地数据库。
- **耗电**：后台每轮最多检查 5 条到期记录；默认 Wi-Fi 或任意已连接网络时执行，不保证准时。
- **网络**：单条失败不能导致整轮 Worker 重试风暴；只在全局网络失败时返回 `Result.retry()`。
- **安全**：只保存媒体身份、用户进度和来源标识；不保存站点 Cookie、token 或密码到追更表。
- **兼容**：`minSdk 24`、`targetSdk 28` 保持现状；WorkManager 与 Android 13+ 通知权限按运行时能力判断。
- **可回滚**：功能开关关闭即可停止新逻辑；独立数据库可保留，不影响旧客户端打开 `tv` 数据库。
- **可维护**：纯策略类不依赖 Android UI，Worker、DAO、元数据客户端和通知器可以替换实现。

---

## 6. 总体架构

### 6.1 组件图

```text
详情页 / 播放页
  └─ FollowingAction -> FollowingStore.addFromDetail()
                          └─ FollowingDatabase (following.db)
                               ├─ Following
                               └─ FollowingSource

后台 / 手动检查
  └─ FollowingUpdateCoordinator
       ├─ FollowingMetadataClient -> TmdbService
       ├─ FollowingSourceProbe -> SiteApi.detailContent(refresh=true)
       ├─ FollowingUpdatePolicy (pure)
       ├─ FollowingStore (DAO)
       └─ FollowingNotifier

播放历史
  └─ History.save() / TmdbSeasonProgressStore.write()
       └─ FollowingProgressProjector -> FollowingStore.updateWatched()

备份 / 同步
  └─ Backup -> FollowingDatabase export / import / merge
```

### 6.2 分层职责

#### Presentation

- `FollowingActivity`：手机端和电视端追更列表。
- `FollowingDetailDialog`/详情面板：显示官方状态、来源状态、进度和操作。
- `FollowingAdapter`：卡片、更新角标、进度文案、错误状态。
- `FollowingAction`：详情页/播放页的添加、取消和状态刷新。

#### Domain

- `FollowingIdentity`：生成和解析 `identityKey`/`seriesKey`。
- `FollowingUpdatePolicy`：纯函数计算 latest、unwatched、hasUpdate、shouldNotify、nextCheckAt。
- `FollowingSchedulePolicy`：计算周期、一次性检查和退避时间。
- `FollowingMergePolicy`：备份/同步冲突解决。

#### Data

- `FollowingDatabase`：独立 Room 数据库。
- `FollowingDao`、`FollowingSourceDao`：查询和事务更新。
- `FollowingStore`：业务 facade，隐藏 DAO 细节。
- `FollowingMetadataClient`：读取 TMDB 并解析标准化快照。
- `FollowingSourceProbe`：读取当前站点详情并提取可播范围。
- `FollowingProgressProjector`：把 `History` 进度投影到追更记录。

#### Infrastructure

- `FollowingUpdateWorker`：WorkManager Worker。
- `FollowingScheduler`：注册周期、一次性检查、取消任务。
- `FollowingNotifier`：通知 channel、权限、去重和点击跳转。
- `FollowingBackupCodec`：备份和同步序列化适配。

---

## 7. 数据设计

### 7.1 为什么使用独立数据库

选择 **独立 `FollowingDatabase` v1**，而不是直接把追更表加入 `AppDatabase`：

- `AppDatabase` 当前版本为 45，升级会触发所有用户的迁移路径。
- `AppDatabase` 使用 `fallbackToDestructiveMigration(true)`；回滚旧 APK 时数据库降级风险过大。
- 追更首版是不需要与 `History` 保持跨表事务的附属功能，适合独立演进。
- 独立数据库可保留在设备上；关闭功能或回滚 APK 不会影响核心收藏、历史和配置。
- `History` 仍是播放进度权威源，追更表可以随时从历史重建，不需要分布式事务。

代价：

- 不能做跨数据库 SQL JOIN。
- 备份和同步需要显式处理 following 部分。
- 播放进度投影是“最终一致”而不是单事务强一致。

这些代价是可接受的，因为追更列表不是支付、登录或关键配置数据。

### 7.2 独立数据库定义

建议新增：

```java
@Database(
    entities = {Following.class, FollowingSource.class},
    version = 1,
    exportSchema = true
)
public abstract class FollowingDatabase extends RoomDatabase {
    public static final String NAME = "following";
    public abstract FollowingDao getFollowingDao();
    public abstract FollowingSourceDao getFollowingSourceDao();
}
```

数据库文件名为 `following.db`，schema 导出到：

```text
app/schemas/com.fongmi.android.tv.following.FollowingDatabase/1.json
```

数据库创建使用单独的 `Room.databaseBuilder()`，不注册到 `AppDatabase.create()`。

### 7.3 `Following` 实体

一条记录对应一个 `seriesKey + seasonNumber`。首版不自动扩展到新季；新季可由用户从详情页加入，后续版本再做自动建季。

建议字段：

```text
identity_key                    TEXT PK  稳定唯一键
series_key                      TEXT     作品级分组键
cid                             INTEGER  来源配置 id
site_key                        TEXT     当前首选站点
vod_id                          TEXT     当前首选 vod id
vod_name                        TEXT     标题快照
vod_pic                         TEXT     封面快照
media_type                      TEXT     tv / anime / variety / documentary
tmdb_id                         INTEGER  无 TMDB 时为 0
tracked_season                  INTEGER  追踪季号
tracked_episode                 INTEGER  最近定位集号，可为 0
watched_season                  INTEGER  已观看季号，可为 0
watched_episode                 INTEGER  已观看集号，可为 0
position                        INTEGER  播放位置 ms
duration                        INTEGER  时长 ms
official_status                 TEXT     UNKNOWN/RETURNING/PLANNED/ENDED/CANCELED
latest_released_season          INTEGER  官方最新已播季
latest_released_episode         INTEGER  官方最新已播集
season_total_episodes           INTEGER  当前追踪季总集数
season_released_episodes        INTEGER  当前追踪季已播集数
series_total_episodes            INTEGER  全剧总集数
next_air_season                 INTEGER  下一集季号
next_air_episode                INTEGER  下一集集号
next_air_at                     INTEGER  预计播出时间 ms，无法确定时为 0
last_observed_episode           INTEGER  上次成功检查看到的最新集
read_watermark_episode          INTEGER  用户已读/已忽略水位
last_notified_episode           INTEGER  最近通知水位
last_notified_at                INTEGER  最近通知时间
has_update                      INTEGER  是否存在未读更新
unwatched_count                 INTEGER  未看集数，派生快照
notify_enabled                  INTEGER  此记录是否允许通知
enabled                         INTEGER  是否参与后台检查
metadata_updated_at             INTEGER  官方快照更新时间
last_checked_at                 INTEGER  最近检查时间
next_check_at                   INTEGER  下次到期时间
failure_count                   INTEGER  连续失败次数
last_error                      TEXT     最近错误摘要
created_at                      INTEGER
updated_at                      INTEGER
```

设计说明：

- `has_update` 和 `unwatched_count` 是为了列表查询方便而缓存的派生值，权威计算仍由 `FollowingUpdatePolicy` 决定。
- `watched_*` 是 `History`/`TmdbSeasonProgress` 的投影缓存，不是独立编辑源。
- `last_notified_episode` 与 `read_watermark_episode` 必须分开，前者控制通知去重，后者控制角标已读。
- `last_error` 仅保存短文本，不保存完整 URL、Cookie 或 HTML。

### 7.4 `FollowingSource` 实体

```text
following_key       TEXT     关联 following.identity_key
cid                 INTEGER  来源配置 id
site_key            TEXT     Site key
vod_id              TEXT     Vod id
vod_name            TEXT     来源标题快照
vod_pic             TEXT     来源封面快照
vod_flag            TEXT     最后成功播放线路
playable_season     INTEGER  来源可播季号
playable_episode    INTEGER  来源可播最新集号
playable_count      INTEGER  来源可播集数
preferred           INTEGER  是否首选来源
last_probe_at       INTEGER  最近探测时间
last_error          TEXT     来源探测错误
PRIMARY KEY (following_key, cid, site_key, vod_id)
```

使用独立来源表的原因：

- 同一部 TMDB 剧集可能从多个站点添加，来源 URL 不能只保存一个。
- 用户手动换源后，可以保留旧来源作为回退候选。
- 首版虽然不自动换源，但探测和手动切换需要区分来源。

### 7.5 身份键规则

#### TMDB 可用

```text
seriesKey   = "tmdb:" + mediaType + ":" + tmdbId
identityKey = seriesKey + ":s" + trackedSeason
```

示例：

```text
seriesKey   = tmdb:tv:1399
identityKey = tmdb:tv:1399:s1
```

#### TMDB 不可用

```text
seriesKey   = "source:" + cid + ":" + siteKey + ":" + vodId
identityKey = seriesKey + ":s" + trackedSeason
```

规则：

- `mediaType`、`siteKey`、`vodId` 先做 trim 和大小写规范化。
- TMDB 记录永远不能因标题变化而换 key。
- 无 TMDB 记录只允许在当前 `cid + siteKey + vodId` 内匹配；标题只用于展示和辅助搜索，不作为唯一身份。
- 如果一个无 TMDB 记录后来获得了 TMDB 匹配，执行显式迁移事务，合并来源和提醒水位，不保留两个可见记录。

### 7.6 建议索引

```text
Following:
  PK(identity_key)
  INDEX(series_key)
  INDEX(enabled, next_check_at)
  INDEX(has_update, updated_at)
  INDEX(cid, site_key, vod_id)
  INDEX(tmdb_id, media_type, tracked_season)

FollowingSource:
  PK(following_key, cid, site_key, vod_id)
  INDEX(following_key, preferred)
  INDEX(cid, site_key, vod_id)
```

所有“到期检查”查询必须使用 `(enabled, next_check_at)`，不能在内存中全表扫描。

---

## 8. 独立数据库与现有系统集成

### 8.1 为什么不改 `AppDatabase`

本设计**不要求**：

- 修改 `AppDatabase.VERSION = 45`。
- 修改 `AppDatabase` 的 entity 列表。
- 增加 45→46 `Migrations` 条目。
- 重导出 46 schema。

这使 FOLLOW-1 的核心数据层可以与播放器、历史、收藏和配置迁移解耦。

### 8.2 播放进度投影

在 `History.save()` 成功写入后，执行一个 best-effort 投影：

```text
History.save()
  -> 原有 tv 数据库事务提交
  -> FollowingProgressProjector.project(history)
       -> 若 tmdbId/mediaType/season 命中 Following，更新 watched_* 和 position/duration
       -> 否则按 cid/siteKey/vodId 命中无 TMDB 记录
       -> 无匹配记录时直接返回，不创建追更
```

约束：

- 投影失败不能影响播放、历史保存或应用退出。
- 投影使用单线程队列，避免并发写 following DB。
- Worker 每次检查前对 due 记录执行一次进度 reconcile，修复投影丢失。
- 删除历史记录时，不把追更记录的 `watched_*` 立刻清零；下一次 reconcile 从剩余历史重建。若没有剩余历史，则保留最后一次投影并标记 `progressStale=true`（可在后续版本加入字段，或首版保持缓存值并允许用户重新标记）。

### 8.3 详情页集成

在 `TmdbDetailActivity` 和 mobile/leanback `VideoActivity` 的详情头部增加“追更”动作：

- 已追更：显示“已追更”，点击进入追更详情。
- 未追更：显示“加入追更”，点击保存当前季和当前来源。
- 失败：不清空页面，显示可恢复错误。
- 非 TV/非连续内容：隐藏按钮。

按钮状态使用 `FollowingStore.find(identityKey)`，不能仅凭运行时内存标记。

### 8.4 播放页集成

播放页不负责检查网络更新，只负责：

- 当前播放来源绑定到 `FollowingSource`。
- 在既有 `History.save()`/`TmdbSeasonProgressStore.write()` 完成后触发投影。
- 如果当前媒体属于追更记录，返回详情页时刷新追更卡片。

---

## 9. 元数据与更新检查设计

### 9.1 `FollowingMetadataSnapshot`

所有元数据先转换为统一快照：

```java
public final class FollowingMetadataSnapshot {
    public String source;              // tmdb / source / alist
    public String status;              // UNKNOWN/RETURNING/PLANNED/ENDED/CANCELED
    public int latestReleasedSeason;
    public int latestReleasedEpisode;
    public int seasonTotalEpisodes;
    public int seasonReleasedEpisodes;
    public int seriesTotalEpisodes;
    public int nextAirSeason;
    public int nextAirEpisode;
    public long nextAirAt;
    public long fetchedAt;
}
```

快照不保存完整 TMDB JSON、剧照列表或用户不可见的原始响应。

### 9.2 TMDB 主路径

条件：

```text
Following.tmdbId > 0 && Setting.isTmdbReady()
```

实现要求：

1. 调用 `TmdbService` 的 TV detail API。
2. 为追更检查提供强制刷新入口，不能复用 `DETAIL_CACHE_TTL = 7 days` 的旧详情来判断更新。
3. 优先读取：
   - `status`
   - `number_of_seasons`
   - `number_of_episodes`
   - `last_episode_to_air`
   - `next_episode_to_air`
   - `seasons[]`
4. 必要时只请求当前追踪季的 season detail，不遍历全部季。
5. 解析 `next_episode_to_air.air_date` 时，如果只有日期没有具体时间，统一按本地 20:00 计算“预计检查时间”，UI 显示日期而非承诺精确到分钟。

推荐新增 `TmdbService.detailForFollowing(item, config)` 或等价方法，内部使用 `refresh=true` 和较短的在线剧集 TTL；不要直接改变现有详情页缓存语义。

### 9.3 原站兜底

条件：

```text
tmdbId <= 0 || TMDB 不可用 || 用户关闭 TMDB
```

调用：

```java
SiteApi.detailContent(siteKey, vodId, true)
```

解析来源的 `Vod.flags` 和 `Episode`：

- 优先使用已绑定的 TMDB season/episode 元数据。
- 无 TMDB 元数据时使用现有 `Episode.getNumber()`、`Util.getEpisodeNumber()` 和标题规则。
- 当前来源可播集数不能直接当成官方总集数。
- 解析结果必须标记 `source=source-probe`，不能覆盖 TMDB 官方状态。

### 9.4 当前来源可播探测

默认策略：

- 新添加时执行一次轻量探测。
- 官方发现新集后探测首选来源。
- 用户手动“检查更新”时探测首选来源。
- 不在周期 Worker 中默认探测所有来源。

提取规则：

```text
playableCount = 当前来源中匹配 trackedSeason 的有效集数
playableEpisode = 当前来源中匹配 trackedSeason 的最大有效集号
```

要求：

- 结果去重排序，忽略空 URL、重复集和无效占位线路。
- 多线路时先使用当前 `FollowingSource.vodFlag`，其次选择覆盖集数最大的线路。
- 单个来源失败只更新该来源的 `last_error`，不改变其他来源和官方快照。

### 9.5 更新检查算法

```text
1. 读取到期 Following(enabled=1, next_check_at<=now, limit=5)
2. 按记录计算 provider：
   2.1 TMDB 可用 -> 刷新官方快照
   2.2 否则 -> 刷新当前来源快照
3. 如果 provider 失败：
   3.1 failure_count += 1
   3.2 next_check_at = now + backoff(failure_count)
   3.3 保留旧快照并记录 last_error
   3.4 continue
4. 执行进度 reconcile（从 History/TmdbSeasonProgress 读取）
5. 计算 latestReleased、seasonReleased、unwatched、hasUpdate
6. 如果需要，探测首选来源的可播范围
7. 事务写回 Following/FollowingSource
8. 计算 shouldNotify
9. 发送通知并更新 last_notified_episode
10. 计算 next_check_at
```

### 9.6 多季与季切换

首版记录按季创建：

- 用户在详情页选择的季就是 `tracked_season`。
- 官方出现新季时，旧记录的 `latest_released_season` 会变化，但不能把旧记录强行改成新季。
- UI 在作品分组上显示“新一季已开播”，提供“追新季”动作。
- 追新季创建新 `identityKey`，保留旧季记录和历史。

后续版本可增加：

- 自动创建新季记录。
- 在旧季完结且用户已看完时自动切换 `trackedSeason`。
- 多季合并卡片。

### 9.7 强制刷新策略

不能用简单的“每次 Worker 都 force refresh”：

- 对 TMDB 网络有成本。
- 对站点 Spider 有成本和副作用。
- 对完结剧没有必要。

建议：

| 状态 | 周期 | 是否强制网络 |
|---|---|---:|
| `RETURNING` | 6 小时 | 是 |
| `PLANNED` | 24 小时或下一集日期 | 是，频率降低 |
| `ENDED` | 7 天轻查 | 否，优先缓存；只有用户手动检查时强制 |
| `CANCELED` | 7 天轻查或停止 | 否 |
| 下一集日期已过 30 分钟 | 一次性检查 | 是 |
| 用户手动检查 | 立即 | 是 |

---

## 10. 后台调度设计

### 10.1 依赖

在 `gradle/libs.versions.toml` 增加稳定版 WorkManager 条目，建议使用 `androidx.work:work-runtime:2.11.2`；不要选择 `2.12.0-rc01` 这类预发布版本。

在 `app/build.gradle` 增加：

```groovy
implementation libs.work.runtime
testImplementation libs.work.testing
androidTestImplementation libs.work.testing
```

最终版本以实际 Gradle 解析和 APK 体积为准；如果媒体库已经传递引入兼容版本，仍应显式声明并锁定版本。

### 10.2 周期任务

```text
唯一任务名：webhtv.following.periodic
周期：6 小时
flex：1 小时
约束：NetworkType.CONNECTED
退避：EXPONENTIAL，初始 15 分钟
```

实现要点：

```java
PeriodicWorkRequest request =
    new PeriodicWorkRequest.Builder(
        FollowingUpdateWorker.class,
        6, TimeUnit.HOURS,
        1, TimeUnit.HOURS)
    .setConstraints(new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
    .build();

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(
        FollowingScheduler.PERIODIC_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        request);
```

### 10.3 一次性任务

用途：

- 新添加后的首次检查。
- 下一集预计播出后的检查。
- 用户手动检查。

一次性任务也要使用唯一名称，避免重复排队：

```text
webhtv.following.one-shot
webhtv.following.one-shot.<identityKeyHash>
```

规则：

- 下一集时间不可用时不设置一次性任务。
- 一次性任务至少延迟 15 分钟；如果刚完成检查，至少延迟 1 小时。
- 同一记录已有未完成的 one-shot 时使用 `KEEP`，不重复创建。
- 关闭追更后取消周期任务和 one-shot 任务。

### 10.4 前台/手动检查

手动检查不依赖 WorkManager 完成 UI 反馈。实现可以使用 `Task.execute()` 调用同一个 `FollowingUpdateCoordinator.checkNow(id)`，由 EventBus 或 callback 通知 UI。要求：

- 同一记录同一时间只运行一个检查。
- UI 显示进行中状态。
- 捕获所有异常，显示错误但保留旧快照。
- 不用主线程做网络或 Room 查询。

### 10.5 Android 系统约束

以下约束必须写入实现和验收：

- WorkManager 周期最小 15 分钟，且实际运行时间由系统、Doze、网络和厂商策略决定。
- Doze 或 App Standby 会推迟作业和网络。
- 本设计不申请 `SCHEDULE_EXACT_ALARM`，不使用 `AlarmManager.setExact()` 保证分钟级提醒。
- 应用被杀后 WorkManager 依赖系统持久调度，不能假设立刻恢复。
- 用户重新打开应用时必须执行一次 due check，弥补错过的窗口。
- Worker 不启用前台服务，避免为追更增加常驻通知。

### 10.6 退避策略

```text
第 1 次失败：15 分钟
第 2 次失败：30 分钟
第 3 次失败：1 小时
第 4 次失败：2 小时
第 5 次失败：6 小时
之后：24 小时上限
```

成功检查将 `failure_count` 归零。HTTP 401/403 和配置缺失等永久错误不进入无限重试，直接记录并等待用户修复。

---

## 11. 通知与站内角标

### 11.1 通知 channel

建议新增：

```text
channelId: webhtv.following.updates
名称：追更更新
重要性：DEFAULT（允许用户自行关闭）
```

### 11.2 权限

- Android 13/API 33+ 使用 `POST_NOTIFICATIONS` 运行时权限。
- 只在用户首次开启“追更通知”时请求，不在应用启动时索要。
- 用户拒绝后，站内角标、列表状态和手动检查继续工作。
- 通知权限检查使用 `NotificationManagerCompat.areNotificationsEnabled()` 和平台权限 API，不能只看 Manifest 声明。

### 11.3 去重与内容

通知 id 使用 `identityKey.hashCode()`，更新同一记录时替换旧通知。

文案示例：

```text
《示例剧》更新至第 12 集
官方已更新至第 12 集，当前源可播至第 10 集
```

点击行为：

- 点击单条通知：打开 `FollowingActivity` 并定位该记录。
- 点击摘要：打开追更列表。
- 当前应用前台时不强制发送系统通知，可只显示站内事件；具体前台抑制应复用现有播放/活动状态，不能在 Worker 中直接持有 Activity。

### 11.4 水位更新

| 用户动作 | 更新字段 |
|---|---|
| 首次添加 | `read_watermark_episode = released`、`last_notified_episode = released` |
| 打开列表/卡片 | `read_watermark_episode = max(current, released)`，清除角标 |
| 点击忽略到下一集 | `read_watermark_episode = released`，不清除历史 |
| 发送通知成功 | `last_notified_episode = released`、`last_notified_at = now` |
| 看完最新集 | 由进度投影更新 `watched_episode`，角标由策略自动清除 |

---

## 12. UI/UX 设计

### 12.1 入口层级与位置

入口已确认采用**一级入口**，不做成设置页二级项，也不只放在“更多”菜单中。手机端与电视端的“追更”均和“点播、直播、设置”处于同一主导航层级；详情页另保留上下文操作。

| 端 | 入口层级 | 建议位置 | 打开方式 |
|---|---|---|---|
| Android Mobile | 底部导航一级项 | `点播 / 直播 / 追更 / 设置`；实验室显示时为 `点播 / 直播 / 追更 / 实验室 / 设置` | 点击底部“追更”打开 `FollowingActivity` |
| Android TV/Leanback | 首页功能按钮一级项 | `点播 / 直播 / 搜索 / 收藏 / 追更 / 推送 / 投屏 / 历史 / 设置 / 站点注入` | 点击首页“追更”打开 `FollowingActivity` |
| 详情页 | 上下文操作 | 顶部主操作区，显示“加入追更/已追更” | 当前作品没有记录时加入，有记录时打开追更详情 |
| 通知 | 深链入口 | 系统通知点击 | 打开 `FollowingActivity` 并定位对应记录 |

一级入口的验收标准是：用户不需要先进入设置、收藏、历史或更多菜单，在主页一次点击即可到达“我的追更”。

#### Mobile 精确接入点

1. 在 `app/src/mobile/res/menu/menu_nav.xml` 增加 `R.id.following`，位置放在 `R.id.live` 与 `R.id.setting` 之间。
2. 新增 `nav_following` 和追更图标；底部导航当前为无文字图标模式，图标必须有明确 content description。
3. 在 `HomeActivity.setNavigation()` 中按 `following_enabled` 控制 `R.id.following` 可见性。
4. 在 `HomeActivity.onNavigationItemSelected()` 中处理 `R.id.following`，调用 `FollowingActivity.start(this)`；首版沿用 `LiveActivity` 这种“底部导航项打开独立 Activity”的既有模式，不强行改成 `Fragment`。
5. 返回主页时保持当前 `Vod`/`Setting` 选中态稳定，不能因为曾点击“追更”而让底部选中态错位。
6. 使用 `BottomNavigationView` 的 badge 能力在 `R.id.following` 上显示未读新集数；0 时移除 badge，超过 99 显示 `99+`。

#### Leanback 精确接入点

1. 在 `HomeButton.all()` 使用尚未占用的稳定 id `8`，添加 `new HomeButton(8, R.string.home_following)`，排在 `home_keep` 与 `home_push` 之间。
2. 将 `8` 加入 `HomeButton.ALL` 和 `getDefaultButtons()`，保证新安装默认可见、老用户升级后缺失项可以补入排序。
3. 在 `Func.setDrawable()` 中为 `R.string.home_following` 映射独立图标；需要动态未读数时可像 `home_custom_csp` 一样只改变显示文本，不改变稳定 id。
4. 在 `HomeActivity.onItemClick(Func)` 中处理 `R.string.home_following`，调用 `FollowingActivity.start(this)`。
5. 保留“首页按钮”配置，用户可以隐藏或调整追更位置；这只改变显示顺序，不改变 id。
6. `select_home_menu_key` 是遥控器快捷键动作表，首版不修改其 1–9 映射；“追更”先作为首页一级按钮提供，避免改变既有快捷键语义。
7. 追更列表沿用 `KeepActivity` 类似的网格卡片与 D-pad 焦点规则。

#### 入口角标

- Mobile：底部“追更”显示未读新集数 badge；进入追更页并处理已读后清除。
- Leanback：首页“追更”卡片显示 `追更 · 3` 这类文本后缀或等价角标；焦点移动时数字不得闪烁。
- 两端角标只读 `following.db`，首页加载不得为追更角标发起 TMDB 或站点网络请求。
- 详情页顶部“加入追更/已追更”继续保留；历史卡片可提供追更快捷动作，但不自动把历史变成追更。

### 12.2 列表筛选

```text
全部
有更新
未看完
已完结
检查失败
```

列表排序：

1. 有更新且未读。
2. 有未看集数。
3. 最近观看时间。
4. 最近官方更新时间。
5. 创建时间。

### 12.3 卡片字段

- 封面、标题、季号。
- 更新角标：`新 3 集`。
- 官方状态：`更新至 S2E8`。
- 当前来源：`当前源 E6`。
- 用户状态：`看到 E5` 或 `未记录进度`。
- 错误状态：小图标，不展示长堆栈。

### 12.4 追更详情页

必须包含：

- 标题、封面、简介、评分、播出状态。
- 当前追踪季集数和已播集数。
- 当前来源可播状态。
- 继续播放。
- 检查更新。
- 标记已读/忽略到下一集。
- 手动换源入口（复用现有全局搜索）。
- 取消追更。

### 12.5 TV 焦点和可访问性

- 所有按钮必须可通过方向键和确认键到达。
- 卡片焦点状态不得因更新角标闪烁。
- 长标题使用 marquee，遵守现有列表行为。
- 图标提供 content description。
- 更新数量使用文本，不只依赖颜色。

---

## 13. 备份、恢复与跨设备同步

### 13.1 备份字段

在 `Backup` 中增加：

```java
@SerializedName("following")
private List<Following> following;

@SerializedName("followingSource")
private List<FollowingSource> followingSource;

@SerializedName("followingSchemaVersion")
private Integer followingSchemaVersion;
```

要求：

- 新备份写入 `followingSchemaVersion = 1`。
- 旧备份缺少以下字段时不删除本机追更数据。
- 全量恢复时，在 `FollowingDatabase` 事务内先删后插。
- 部分恢复时按 `identityKey` 和来源主键 merge。
- 恢复失败必须回滚 following 事务，不能留下半套记录。

### 13.2 同步冲突规则

| 字段类别 | 规则 |
|---|---|
| 身份、标题、封面、TMDB ID | 以 `metadataUpdatedAt` 更新者为新；缺失字段可补齐 |
| `watched_*`、position、duration | 取播放进度时间更新者；相同则取较大集号 |
| `read_watermark_episode` | 取最大值，避免设备已读后被旧设备重新标为未读 |
| `last_notified_episode` | 取最大值，避免多设备重复通知 |
| `enabled`、`notify_enabled` | 以显式用户状态为准，合并时保留“关闭”意图 |
| `next_check_at`、`failure_count`、`last_error` | 不参与同步，由各设备重新计算 |
| 来源绑定 | 合并唯一键；优先级/首选字段以更新时间更新者为新 |

### 13.3 一键同步 UI

在现有同步选项增加：

```text
追更
```

默认状态：

- 首版默认关闭，避免用户未确认就把新数据发送到其他设备。
- 用户开启后，远端协议和所有 serverless 实现要保留该布尔项，防止同步配置互相覆盖。
- 如果现有同步以完整备份 ZIP 传递，不要求服务端理解追更表；如果服务端会规范化 options，则 Rust/Go/Cloudflare/Deno/Vercel 五种实现必须加入 `follow` 字段。

### 13.4 alist-tvbox 可选导入

该功能默认关闭，作为 P5 可选能力：

```text
GET  /api/media-subscriptions
GET  /api/media-subscriptions/{id}/detail
POST /api/media-subscriptions
```

导入映射：

| 服务端字段 | WebHTV 字段 |
|---|---|
| `name` | 标题候选 |
| `season` | `tracked_season` |
| `metaProvider/metaId` | TMDB/Bangumi 身份 |
| `doubanId` | 豆瓣辅助匹配 |
| `currentEpisodes` | `FollowingSource.playable_count` |
| `status` | 服务端状态展示 |

约束：

- 仅按 TMDB ID、豆瓣 ID、标题+季依次匹配。
- 导入前先预览，不自动创建大量记录。
- 服务端失效不能影响本地追更。
- 不自动调用服务端的挂载、转存、磁力或自动换源动作。
- token 使用现有安全存储/用户输入，不写入追更表。

---

## 14. 性能、隐私和安全

### 14.1 性能预算

- 500 条追更记录首屏本地查询目标小于 500 ms（中端设备，不含图片解码）。
- Worker 每轮最多检查 5 条到期记录、最多并发 2 条。
- 列表加载阶段不请求 TMDB，不请求站点详情。
- 单个来源探测超过 20 秒视为失败，不能无限等待。
- 通知和进度投影不能阻塞主线程。

### 14.2 包体和依赖

- 记录 WorkManager 引入前后的 release APK 压缩体积差异。
- 目标增量不超过 1 MB；超过时必须给出依赖树和原因，不能默默接受。
- 不因追更新增 native library、FFmpeg、MPV 或 AAR。

### 14.3 隐私

- 只保存媒体 ID、标题、图片、来源 key 和本地进度。
- 不保存站点 Cookie、HTTP Authorization、WebHome token、网盘 token。
- 备份和同步属于用户显式动作；默认不上传后台观察结果。
- 日志中不输出完整 URL 查询参数和用户账号。

### 14.4 安全

- alist 导入 token 使用现有安全存储能力或仅保存在用户会话中。
- 任何外部服务返回的标题、概述、URL 都必须限长、转义，不能直接作为 WebView HTML 注入。
- 不执行服务端或 TMDB 返回的脚本。

---

## 15. 测试与验证计划

### 15.1 单元测试

建议新增：

```text
FollowingIdentityTest
FollowingUpdatePolicyTest
FollowingSchedulePolicyTest
FollowingMergePolicyTest
FollowingMetadataSnapshotTest
FollowingSourceProbeTest
FollowingProgressProjectorTest
FollowingBackupTest
FollowingNotifierPolicyTest
```

必须覆盖：

#### 身份

- TMDB 同剧不同站点合并为同一记录。
- 同剧不同季不合并。
- 无 TMDB 来源身份稳定。
- 标题变化不改变 TMDB identity。
- 空 key、非法 season、特殊季 0 的处理。

#### 更新策略

- 首次添加不通知。
- 官方 8 集、已看 5 集，计算 3 集未看。
- 官方 8 集、已读 8 集但未看 5 集，角标清除但未看计数仍可显示。
- 官方状态回退或错误响应不能降低已有最新集。
- `ENDED` 后不重复通知。
- 未来播出集不计入已播。
- 特别篇、电影和季 0 不误判为普通集。
- 多季切换不会把旧季进度写入新季。

#### 调度

- 初次启用创建唯一周期任务。
- 重复启用不会产生多个周期任务。
- 关闭功能取消周期和 one-shot 任务。
- 失败退避不超过 24 小时。
- 下一集时间已过时只安排一次，不创建重复 one-shot。

#### 来源探测

- 多线路选择覆盖集数最大的线路。
- 空 URL、重复集和无效占位项被过滤。
- 当前来源缺集时官方状态仍更新。
- 来源探测失败不清空官方快照。

#### 同步

- 旧备份缺少 following 字段不删本地数据。
- 新备份空 following 按明确的“清空”语义处理。
- read/notified 水位取最大。
- nextCheckAt/lastError 不覆盖本机调度状态。
- 合并后不出现重复 identity。

### 15.2 DAO 与数据库测试

新增测试：

```text
FollowingDaoTest
FollowingSourceDaoTest
FollowingDatabaseTest
FollowingBackupCodecTest
```

检查：

- `upsert` 不覆盖更大水位。
- due 查询只返回 `enabled` 且 `next_check_at <= now`。
- 来源表主键去重。
- 删除追更同时删除来源，或由 facade 显式事务删除。
- 备份导出/导入顺序正确。
- Room `FollowingDatabase` version 1 schema 可导出。

### 15.3 迁移测试

首版不迁移 `AppDatabase`，但必须增加独立数据库的未来迁移测试骨架：

- `FollowingDatabase` v1 空库可创建。
- 通过完整备份恢复 v1 数据。
- 如果后续升级 v2，使用 `MigrationTestHelper` 验证 v1→v2。

如果团队最终选择把追更表放进 `AppDatabase`，则必须改为：

- `AppDatabase.VERSION = 46`。
- 新增 `MIGRATION_45_46`。
- 导出 `46.json`。
- instrumentation 测试从 45 迁移到 46，并验证 Keep、History、TmdbSeasonProgress 不丢失。

本设计的默认实现不采用该路径。

### 15.4 Worker 与通知测试

使用 `work-testing` 的 `TestListenableWorkerBuilder` 或等价方式：

- 注入 fake clock、fake metadata client、fake source probe。
- 只检查 due 记录。
- 单条错误不导致整轮失败。
- 全局网络失败返回 `Result.retry()`。
- 成功检查写入 `last_checked_at` 和 `next_check_at`。
- 重复通知水位会抑制第二次通知。
- 通知权限关闭时仍更新站内状态。

### 15.5 UI 源结构测试

移动端和电视端分别检查：

- 详情页按钮只在 TV/连续内容显示。
- Mobile `menu_nav.xml` 存在 `R.id.following`，且位于 `live` 与 `setting` 之间。
- Mobile `HomeActivity.setNavigation()` 按 feature flag 控制追更项，`onNavigationItemSelected()` 能启动 `FollowingActivity`。
- Mobile 追更 badge 能显示、更新和清除，且不会在首页触发网络请求。
- Leanback `HomeButton.all()`、`ALL`、`getDefaultButtons()` 都包含 id `8`，顺序在收藏与推送之间。
- Leanback `Func` 能映射追更图标，`HomeActivity.onItemClick()` 能启动 `FollowingActivity`。
- Leanback 首页按钮配置允许隐藏或移动追更，不改变 id `8` 和其他按钮语义。
- 两端一级入口都能打开 `FollowingActivity`。
- 列表卡片读取官方、来源、用户三类状态。
- D-pad 焦点不丢失。
- 点击继续播放使用现有历史和 TMDB 进度入口。
- 错误状态不会覆盖旧卡片内容。

### 15.6 真机/设备测试矩阵

至少覆盖：

| 设备 | 场景 |
|---|---|
| Android Mobile API 24/26 | 添加、列表、恢复、通知权限关闭 |
| Android Mobile API 33+ | 首次通知授权、拒绝后角标、后台检查 |
| Android TV/Leanback Android 9+ | D-pad、首页入口、后台恢复 |
| 低内存设备 | 500 条列表、Worker 单条失败 |
| 无网络/弱网 | 手动检查失败、旧快照保留、下一轮恢复 |
| Doze/待机 | 一次性任务延迟不崩溃、回前台补查 |
| 双设备 | 一键同步合并、重复通知抑制 |

### 15.7 推荐验证命令

定向单元测试：

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.following.*' \
  --tests 'com.fongmi.android.tv.bean.BackupFollowingTest' \
  --tests 'com.fongmi.android.tv.db.*Following*' \
  --no-daemon --console=plain

bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.following.*' \
  --no-daemon --console=plain
```

UI 和 Java 编译：

```bash
bash ./gradlew \
  :app:testMobileArm64_v8aDebugUnitTest \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  --tests 'com.fongmi.android.tv.ui.activity.Following*' \
  --no-daemon --console=plain
```

调试 APK：

```bash
bash ./gradlew :app:assembleMobileArm64_v8aDebug \
  :app:assembleLeanbackArm64_v8aDebug \
  --no-daemon --console=plain
```

数据库/Worker instrumentation（按项目现有设备命令替换 flavor）：

```bash
bash ./gradlew :app:connectedMobileArm64_v8aDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.fongmi.android.tv.following.FollowingDatabaseTest \
  --no-daemon --console=plain
```

最终静态检查：

```bash
git diff --check
```

### 15.8 不接受的“通过”证据

- 只编译通过，不证明更新策略、通知去重或同步合并正确。
- 只有日志看到“已检查”，不证明官方/来源/用户三类状态分离。
- 只在一台在线设备通过，不证明 Doze、拒绝通知权限和跨设备合并。
- 只验证旧 APK 能打开，不证明旧版本回滚不会影响 `tv` 数据库；因此采用独立数据库。

---

## 16. 分阶段实施计划

### Phase P0：接口和基线冻结

范围：

- 固定本文档、上游 commit ID、现有本地符号和测试命令。
- 确认 WorkManager 版本、独立数据库方案和 feature flag。
- 不写业务实现。

退出标准：

- 评审确认 `identityKey`、三类状态、数据库位置和首版非目标。
- 明确哪些路径属于本阶段，哪些属于后续。

### Phase P1：独立数据层与备份

新增/修改：

- `following/Following.java`
- `following/FollowingSource.java`
- `following/FollowingDatabase.java`
- `db/dao` 或 `following/dao` 下的 DAO
- `FollowingStore.java`
- `FollowingBackupCodec.java`
- `bean/Backup.java`
- 资源：following strings/layout 初始骨架

退出标准：

- following DB 可创建、读写、导出 schema。
- 备份/恢复单测通过。
- `AppDatabase.VERSION` 保持 45，核心 schema 无改动。

### Phase P2：元数据与更新引擎

新增：

- `FollowingIdentity.java`
- `FollowingMetadataSnapshot.java`
- `FollowingMetadataClient.java`
- `FollowingSourceProbe.java`
- `FollowingUpdatePolicy.java`
- `FollowingUpdateCoordinator.java`
- `FollowingProgressProjector.java`

退出标准：

- TMDB fixture、原站 fixture、首次添加、季集回退、失败保留旧值均通过。
- 手动检查可完成一次端到端内存验证。

### Phase P3：调度、通知和生命周期

新增：

- `FollowingUpdateWorker.java`
- `FollowingScheduler.java`
- `FollowingNotifier.java`
- WorkManager 依赖和权限检查

退出标准：

- 周期唯一任务、one-shot 去重、失败退避、关闭取消均通过。
- 通知权限关闭时仍能更新站内状态。
- Worker 不阻塞主线程，不启动前台服务。

### Phase P4：手机端和电视端 UI

新增/修改：

- `app/src/mobile/res/menu/menu_nav.xml`：增加 `R.id.following`，位于“直播”和“设置”之间
- mobile `HomeActivity.java`：显示一级导航项、处理独立 Activity 跳转和 badge
- mobile `FollowingActivity.java`：追更一级页面
- leanback `HomeButton.java`：使用 id `8`，加入 `all()`、`ALL` 和默认按钮顺序
- leanback `Func.java`：追更图标与状态文本
- leanback `HomeActivity.java`：处理 `home_following` 一级按钮跳转
- leanback `FollowingActivity.java`：追更一级页面
- mobile/leanback `FollowingAdapter`
- 详情页“加入追更/已追更”动作
- 继续播放、检查更新、已读、忽略、换源入口
- default/zh-rCN/zh-rTW 的 `nav_following`、`home_following` 和追更图标资源

退出标准：

- 手机端底部导航和电视端首页按钮均可一次点击进入追更，与点播/直播/设置同级。
- 两端可添加、查看、继续播放、取消追更。
- 两端 D-pad/触摸互不回归。
- 现有收藏、历史、详情布局测试通过。

### Phase P5：同步与 alist 可选导入

新增：

- `SyncOptions.follow`
- mobile/leanback `SyncDialog.follow()`
- `FollowingMergePolicy`
- `AlistSubscriptionImporter`
- serverless options 兼容检查

退出标准：

- 旧备份恢复不丢本地追更。
- 双设备合并去重、水位 max、调度字段不覆盖。
- alist 默认关闭，未配置时不发起网络请求。

### 预计工作量

| 阶段 | 工程量估计 |
|---|---:|
| P0 设计冻结 | 0.5–1 小时 |
| P1 数据/备份 | 2–3 小时 |
| P2 更新引擎 | 3–4 小时 |
| P3 调度/通知 | 2–3 小时 |
| P4 双端 UI | 3–5 小时 |
| P5 同步/alist | 2–4 小时 |
| 完整验证与设备回归 | 2–4 小时 |

总工程量以单端 MVP 优先；手机端和电视端、后台、同步全部闭环通常需要 12–18 个 Codex 工作小时，实际取决于 TMDB/站点网络和真机可用性。

---

## 17. 发布、开关与回滚

### 17.1 Feature flag

建议设置：

```text
following_enabled = false   // 初始开发构建
following_notifications = false
following_source_probe = when_update
following_server_import = false
```

进入内部测试后：

```text
following_enabled = true
following_notifications = user_opt_in
```

正式放量前必须完成 P0–P4，P5 可独立延后。

### 17.2 开关行为

关闭 `following_enabled`：

- 取消 `FollowingScheduler` 的周期和 one-shot 任务。
- 不删除 following DB。
- 详情页隐藏“追更”动作或显示不可用状态。
- 不调用 `FollowingMetadataClient` 和 `FollowingSourceProbe`。

关闭 `following_notifications`：

- 停止系统通知。
- 保留列表角标、手动检查和进度更新。
- 不清除 `last_notified_episode`，重新开启后仍按水位去重。

### 17.3 回滚

首选回滚是关闭 feature flag，而不是降级 APK。

原因：

- 独立 following DB 不参与 `AppDatabase` 版本，可以保留给后续修复版本读取。
- 旧 APK 不会访问 following DB，也不会因 `tv` DB 版本不匹配触发 destructive migration。
- 即使新版本有 Worker 崩溃，取消 WorkManager 任务即可停止后台行为。

如果必须回滚 APK：

- 旧版本可以继续工作。
- following DB 文件可保留为用户数据，不自动删除。
- 如果需要彻底清理，必须由新版本提供显式清理入口或用户手动清除数据，不能让回滚过程静默删库。

---

## 18. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---:|---|
| TMDB 表示已播，当前来源未上架 | 高 | 官方/来源/用户三状态分离；不把来源可播当成官方 |
| 7 天详情缓存导致漏更新 | 高 | 追更使用专用 force refresh/TTL，不改变现有详情缓存语义 |
| 后台任务不准时 | 中 | 使用 WorkManager + 回前台补查 + 站内角标，不承诺精确通知 |
| 每轮扫描过多记录导致耗电 | 中 | 索引 due 查询、每轮最多 5 条、并发 2、退避封顶 24h |
| 站点蜘蛛有副作用 | 中 | 默认只在有官方更新时探测首选来源，探测失败不重试风暴 |
| 参考项目无 License | 高 | 不复制代码；只借鉴行为，独立实现并记录来源 |
| 新季错过或误判 | 中 | MVP 按季记录，UI 提供追新季；自动切换推迟 |
| 多设备重复通知 | 中 | `last_notified_episode` 取最大；系统通知使用稳定 id |
| 备份恢复丢进度 | 中 | 追更 DB 独立事务、旧备份缺失时保留本机数据、恢复前自动备份 |
| 回滚旧 APK 破坏数据库 | 高 | 独立 following DB，核心 `tv` DB 不升级；Feature flag 作为主回滚 |
| 自动导入 alist 造成大批记录 | 中 | 默认关闭、先预览、人工确认、按身份去重 |

---

## 19. 需求追踪矩阵

| 需求 | 设计章节 | 主要实现 | 主要验证 |
|---|---|---|---|
| F-01 添加/取消追更 | 8.3、12.1 | `FollowingAction`、`FollowingStore` | UI 源测试、真机 |
| F-02 首次不通知 | 4.3、11.4 | `FollowingUpdatePolicy` | `FollowingUpdatePolicyTest` |
| F-03 官方更新识别 | 9.2、9.5 | `FollowingMetadataClient` | TMDB fixture、单测 |
| F-04 官方/来源分离 | 4.2、9.4 | `FollowingSourceProbe`、UI | `FollowingSourceProbeTest` |
| F-05 身份去重 | 7.5 | `FollowingIdentity` | `FollowingIdentityTest` |
| F-06 继续播放 | 8.2、12.4 | `History`/`TmdbSeasonProgress` 入口 | 播放恢复测试 |
| F-07 失败保留快照 | 9.5、10.6 | `FollowingUpdateCoordinator` | 异常单测 |
| F-08 后台调度 | 10.1–10.4 | `FollowingScheduler`、Worker | WorkManager 测试 |
| F-09 通知去重 | 11.3–11.4 | `FollowingNotifierPolicy` | 通知 fake |
| F-10 双端一级入口 | 12.1、12.5 | mobile `menu_nav`/HomeActivity、leanback HomeButton/Func/HomeActivity | 源测试、真机 |
| F-11 备份恢复 | 13.1 | `FollowingBackupCodec` | `FollowingBackupTest` |
| F-12 同步合并 | 13.2 | `FollowingMergePolicy` | merge 单测 |
| F-13 开关停止 | 17.2 | `FollowingScheduler` | scheduler 单测 |
| F-14 无核心回归 | 8.1 | 独立 DB、feature flag | 现有相关测试 |
| F-15 不升核心 DB | 8.1 | `FollowingDatabase` | schema/diff 检查 |

---

## 20. 最佳实践证据与来源

访问日期：2026-09-19。证据等级：A=上游/本地实际源码，B=官方平台文档，C=项目现有设计/测试。

### 20.1 上游源码

| 来源 | 修订 | 级别 | 支持的结论 | WebHTV 适用性 |
|---|---|---|---|---|
| `power721/atv-player` `src/atv_player/following_repository.py` | `5867e5f16c8bdb8a320d3ea6bb7f242afd2f02a0` | A | 追更身份、快照、进度、检查时间和提醒水位可持久化 | 采用模型思想，独立实现 |
| `power721/atv-player` `src/atv_player/following_update_service.py` | `5867e5f16c8bdb8a320d3ea6bb7f242afd2f02a0` | A | 桌面端使用 QTimer 和更新窗口；不能照搬到 Android | 改为 WorkManager + 延时一次性任务 |
| `power721/atv-player` `src/atv_player/following_backend.py` | `5867e5f16c8bdb8a320d3ea6bb7f242afd2f02a0` | A | alist 订阅可导入本地追更并以 `currentEpisodes` 作为可播信号 | 放 P5 默认关闭 |
| `power721/alist-tvbox` `MediaSubscriptionCheckService.java` | `5bb4b93fbdb2dc6fe61c585e83302e37fe2c7acd` | A | `next_check_time`、退避、播后短轮、资源巡检和挂载属于服务端系统 | 只借鉴调度/状态分离，不搬网盘编排 |
| `power721/alist-tvbox` `MediaSubscriptionController.java` | `5bb4b93fbdb2dc6fe61c585e83302e37fe2c7acd` | A | `/api/media-subscriptions` 是明确的导入互操作边界 | P5 可选适配 |

### 20.2 官方平台文档

| 来源 | 级别 | 支持的结论 | 设计影响 |
|---|---|---|---|
| [Android WorkManager: Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) | B | 周期任务最小 15 分钟；时间受 constraints 和系统优化影响；存在 flex period | 默认 6 小时+1 小时 flex，不承诺准确时间 |
| [Android WorkManager: Retry and back-off policy](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#retries) | B | 退避延迟不精确，但不会小于初始值 | 退避时间用于限制频率，不用于精确提醒 |
| [Android Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | B | Doze 和待机会延迟作业、网络和维护操作 | 回前台必须补查；不用 AlarmManager 保证实时 |
| [Android notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) | B | Android 13/API 33+ 提供 `POST_NOTIFICATIONS` 运行时权限 | 通知仅作增强；拒绝时保留站内角标 |
| [Android Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions) | B | schema 导出、Migration 和测试应作为数据库变更的一部分 | 独立 DB v1，保留未来 MigrationTestHelper 路径 |
| [Android Room testing migrations](https://developer.android.com/training/data-storage/room/testing-db) | B | 数据库迁移需要验证真实旧版本到新版本 | 后续 version upgrade 必须做 instrumentation migration 测试 |
| [TMDB TV Series Details](https://developer.themoviedb.org/reference/tv-series-details) | B | 提供 `last_episode_to_air`、`next_episode_to_air`、`number_of_episodes`、`status` | 官方快照主字段 |
| [TMDB TV Season Details](https://developer.themoviedb.org/reference/tv-season-details) | B | 提供 season episodes、air date 和 episode count | 当前追踪季只按需请求 |
| [TMDB TV Series Changes](https://developer.themoviedb.org/reference/tv-series-changes) | B | 默认 24 小时、最多 14 天；season/episode 变更可能映射到 show-level change | 本首版不依赖 changes 接口，保留后续优化空间 |

### 20.3 本地项目证据

| 本地路径 | 级别 | 支持的结论 |
|---|---|---|
| `app/src/main/java/com/fongmi/android/tv/bean/Keep.java` | A | 收藏不等于追更，缺少季集和检查状态 |
| `app/src/main/java/com/fongmi/android/tv/bean/History.java` | A | 已有 TMDB 身份、季集和播放进度 |
| `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonProgress.java` | A | 已有跨来源分季进度 |
| `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java` | A | 有 TMDB 详情缓存；追更需要专用刷新 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeInfo.java` | A | 已有 TMDB 状态/下一集解析基础 |
| `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java` | A | 站点详情支持 refresh，可用于来源可播探测 |
| `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java` | A | 核心数据库版本 45，具有 destructive fallback，需隔离 |
| `app/src/main/java/com/fongmi/android/tv/bean/Backup.java` | A | 备份是显式 entity JSON，新增 following 字段可控 |
| `app/src/main/java/com/fongmi/android/tv/utils/Task.java` | A | 有现成后台和 scheduled executor 可复用于手动检查 |

### 20.4 未采用或未依赖的来源

- 不依赖任何未经核实的第三方博客或搬运文章来决定状态语义。
- 不依赖 `alist-tvbox` 的数据库 schema 或 Spring 调度细节；只把它作为可选 HTTP 互操作目标。
- 不使用 Android 精确闹钟文档作为实现路径，因为首版不申请精确闹钟权限。

---

## 21. 未决事项与默认决策

如果评审没有提出不同意见，按以下默认值实施：

| 未决事项 | 默认值 |
|---|---|
| 追更数据库位置 | 独立 `following.db` v1 |
| 首版支持媒体 | 仅 TMDB `tv` 和连续内容 |
| 检查周期 | 6 小时，flex 1 小时 |
| 通知精度 | 不承诺分钟级，不申请精确闹钟 |
| 自动新季 | 不自动创建，UI 提供追新季 |
| 自动换源 | 不自动，跳转现有全局搜索 |
| alist 导入 | 默认关闭、手动预览 |
| 默认通知 | 用户显式开启 |
| 默认同步 | `follow=false` |
| 进度投影 | best-effort，后台 reconcile 修复 |
| 电影/直播 | 不纳入首版 |
| 入口层级 | Mobile 底部导航一级项，与点播/直播/设置同级；Leanback 首页按钮一级项，位于收藏与推送之间 |

任何改变以下内容的决定都必须重新评审：

- 把 following 表并入 `AppDatabase`。
- 承诺分钟级通知。
- 自动转存、挂载或自动换源。
- 将 TMDB/站点可播状态合并成一个布尔值。
- 导入第三方未授权源码。

---

## 22. 实施前检查清单

开始编码前必须确认：

- [ ] 本文档已评审，`FOLLOW-1` 的范围和路径已冻结。
- [ ] 已确认采用独立 `following.db`。
- [ ] 已确认 WorkManager 版本和 APK 体积预算。
- [ ] 已确认首版不自动换源、不自动转存。
- [ ] 已确认 alist 导入默认关闭。
- [ ] 已确认 Mobile 追更为底部导航一级项，Leanback 追更为首页按钮一级项，不做隐藏二级入口。
- [ ] 已确认 TMDB 不可用时使用原站详情兜底。
- [ ] 已确认现有 `Keep`、`History`、`TmdbSeasonProgress` 不改变语义。
- [ ] 已确认旧备份缺少 following 字段时保留本机数据。
- [ ] 已确认通知拒绝授权不影响站内功能。
- [ ] 已准备手机和电视真机，或明确将哪些设备验证延后。

## 23. 完成定义

FOLLOW-1 只有在以下证据都存在时才算完成：

1. 独立数据库和迁移/备份测试通过。
2. 更新策略、身份合并、来源探测和通知去重单测通过。
3. 手动检查和 WorkManager 调度测试通过。
4. mobile 与 leanback 的添加、列表、继续播放、取消操作通过。
5. 旧备份恢复、新备份导出和跨设备合并测试通过。
6. 至少一台 API 33+ 设备和一台 TV 设备完成拒绝通知、后台延迟和回前台补查。
7. 现有 Keep/History/TmdbSeasonProgress 相关回归测试通过。
8. 记录 APK 体积差异、完整验证日志、commit 和回滚锚点。

## 24. 恢复锚点

```text
objective:         为 WebHTV 设计可实施、可验证的追更功能
authority:         本文为设计稿；未获用户明确“开始实施”前禁止生产代码变更
baseline:          dev4@552b68bb8e781d06ca989abfd8caa96a567e494a
selected_design:   独立 following.db v1 + WorkManager + TMDB/原站分离 + Feature flag
protected_inputs:  Keep/History/TmdbSeasonProgress/AppDatabase v45 语义
upstream_refs:      atv-player@5867e5f16c8bdb8a320d3ea6bb7f242afd2f02a0
                    alist-tvbox@5bb4b93fbdb2dc6fe61c585e83302e37fe2c7acd
next_action:       等待用户评审本文；如批准，按 P0 冻结接口后从 P1 数据层开始
rollback_anchor:   关闭 following_enabled 并取消 WorkManager 唯一任务；保留 following.db
```

## 25. 实施记录（2026-09-19）

> 本节记录当前代码状态，不改变上方设计决策。任务 ID 仍为 `FOLLOW-1`。

### 25.1 已实施

- P1：新增独立 Room v1 数据库 `following`，实体、DAO、identity key、备份 codec 和 schema 已落盘；`AppDatabase.VERSION` 保持 45。
- P2：新增 TMDB 专用强制刷新入口、官方/来源可播 snapshot、来源线路探测、更新水位、去重策略和播放进度 best-effort 投影。
- P3：新增 WorkManager 6 小时周期任务、one-shot 任务、失败退避、通知 channel、通知权限检查；首页恢复时排队一次 due check。
- P4：手机底栏和 Leanback 首页一级入口已接入；两端共用追更列表，支持继续播放、手动检查、只看更新筛选、已读、通知开关、换源入口和取消追更；TMDB 详情页暴露“加入追更/已追更”。
- 备份/恢复已加入 following 字段和 schema version；旧备份缺少 following 字段时不删除本机追更数据。

### 25.2 验证证据

- `:app:testMobileArm64_v8aDebugUnitTest` 与 `:app:testLeanbackArm64_v8aDebugUnitTest` 的 FOLLOW-1 定向测试通过。
- `:app:assembleMobileArm64_v8aDebug` 通过。
- 模拟器 `HD1910 - 9`（ADB `192.168.50.3:5561`）安装成功；从手机首页点击追更入口打开 `FollowingActivity`，并确认界面显示检查更新、追更数量与空状态。
- 设备上实际创建独立 `following` 数据库；`following`、`following_source`、`room_master_table` 表存在。
- 设备端 `FollowingDatabaseTest` 通过；WorkManager 的 `SystemJobService` 作业已出现在 `dumpsys jobscheduler`。
- 设备端 `FollowingActivityDeviceTest` 通过：预置两条记录后，真实活动显示“2 部/未读 1 部”；点击“只看更新”后卡片数从 2 降为 1。
- 模拟器实际点击“只看更新”后按钮切换为“显示全部”，空态切换为筛选空态；个性化设置页可见“追更更新”开关，关闭/重新打开均即时更新。
- 当官方快照出现更高季号时，旧季卡片显示“追新季 Sx”；点击后仅创建/跳转到新季 identity，不改变旧季观看进度和提醒水位。
- 点击追更卡片打开详情操作面板，集中显示官方/来源/用户三类状态与错误，并提供继续播放、检查更新、标记已读、手动换源和取消追更。
- 首次添加时若尚无官方缓存，第一次成功拉取只建立提醒基线，不发送“从第 1 集开始”的历史集通知；后续集数才触发提醒。
- 来源探测优先使用已绑定线路；若该线路没有有效集，则回退到覆盖集数最多的线路，全部无效时不伪装成可播。
- Leanback 首页按钮全量顺序包含追更 id `8`，并继续保留站点注入 id `9`，两者互不替代。
- TMDB 主 provider 失败但绑定来源探测成功时，保留上一次官方快照，仅更新来源可播状态，并把本轮视为成功；不会因 TMDB 暂时不可用而丢弃可用来源结果。关闭 `following_enabled` 后 Leanback 首页不显示追更按钮。
- 追更列表支持“全部/有更新/未看完/已完结/检查失败”五类筛选，并按更新、未看数量、最近观看/修改、官方更新时间和创建时间排序；Leanback 首页追更按钮显示本地未读后缀，不触发网络请求。
- 来源可播探测会校验 TMDB 映射的季号；映射属于其他季的集不会计入当前追踪季。WorkManager 唯一周期/one-shot 任务的创建和关闭取消已有设备端直接测试。
- 同配置 `mobileArm64_v8aDebug` 体积比较：功能前 `ceac8d89af` 为 `200,780,386` bytes，当前测试包为 `204,653,512` bytes，增量 `3,873,126` bytes（约 `3.694 MiB`，`1.929%`）。该差值只代表未裁剪的 Debug 测试包，不等价于 Release/市场包体积。
- 设备端 `FollowingBackupDeviceTest` 使用真实 `following.db` 验证完整导出、合并去重、read/notified/watched 水位取最大、本地 `nextCheckAt`/错误不被远端覆盖，以及恢复后来源表一致性。
- 修复 TMDB 详情页打开时的 `RoomDatabase.assertNotMainThread` 崩溃：详情页不再在主线程调用 `FollowingStore.findByTmdb/findBySource`，查库、建库记录都通过 `FollowingPlaybackBridge` 在后台执行并回主线程更新按钮。
- 手机播放页原生动作区、手机 TMDB 详情头部和电视播放页动作行均新增“加入追更/已追更”入口；入口只读取当前 `History`，实际 Room 读写全部异步。
- 设备端 `FollowingDetailDeviceTest` 已验证：从主线程调用桥接查询不会触发 Room 主线程异常，回调回到主线程。
- 修复手机横屏/平板布局启动 `VideoActivity` 时 `mBinding.following` 为空导致的 NPE：`layout`、`layout-land`、`layout-sw600dp`、`layout-sw600dp-land` 四个 `activity_video.xml` 变体均加入追更按钮，代码同时保留空值保护。
- 修复取消追更时主线程调用 `FollowingStore.delete()` 导致的 Room 崩溃：取消、已读、通知开关和来源导入均通过后台桥接执行；设备端 `FollowingDeleteDeviceTest` 验证主线程发起删除可安全完成并同时清理来源表。
- 手机播放页追更图标改用与搜索、投屏、设置一致的 `_shadow` 双层样式，避免系统主题对 vector tint 产生不同颜色。
- 修复追更列表点击“检查更新”时的同类主线程崩溃：`checkAll()` 先完整进入后台线程再读取 `FollowingStore.list()`；设备端 `FollowingCheckDeviceTest` 通过真实 Activity 点击验证该入口。
- 修复手机底栏与 Leanback 首页追更角标的同类主线程崩溃：未读数统一通过 `FollowingPlaybackBridge.refreshUnreadCountAsync()` 在后台刷新并缓存，UI 只读取 `cachedUnreadCount()`；两端首页恢复后异步更新显示。双端定向单元测试通过，`mobileArm64_v8aDebug` 测试包已覆盖安装到 `192.168.50.3:5561`，启动与首页恢复日志未出现主线程 Room 或 `FATAL EXCEPTION`。
- 播放进度投影改为专用单线程队列：`FollowingStore.project()` 只入队，历史/季进度事务提交后再投影，避免 UI 线程访问 following Room 和事务提交前读取旧进度；设备测试验证主线程调用可安全完成并更新追更水位。
- 应用处于前台时抑制追更系统通知，避免用户正在使用应用时弹出重复提醒；站内状态、角标和后台后续检查不受影响。
- 官方元数据回退保护：同一追踪季内 `latestReleasedEpisode` 和已播集数只增不减；TMDB 返回旧季或较小集数时保留已有水位，避免更新角标和未看计数倒退。
- 设备端 `FollowingNotifierDeviceTest` 验证应用前台时通知器返回抑制状态且不推进 `lastNotifiedEpisode`，确保站内状态更新与系统提醒解耦。
- 来源线路选择按当前 `trackedSeason` 统计覆盖集数；其他季线路即使集数更多，也不能压过真正覆盖当前季的线路，单测覆盖该回退场景。
- 季详情解析按“已播最大集号”而不是“已播集数”更新 `latestReleasedEpisode`，并忽略未来集；修复第 1、10 集已播却误记为第 2 集的场景。
- 继续播放优先复用同源历史，同源没有记录时回退到同一 TMDB 季的其他来源历史，避免用户换源后丢失已有季集和播放进度；设备测试覆盖跨源回退。
- 调度策略让 RETURNING 状态也使用 `next_episode_to_air`：在 6 小时周期和“播出后 30 分钟”中取更早者，刚完成检查时至少延迟 1 小时，避免在播剧多等一个周期。
- 追更投影入口受 `following_enabled` 控制；关闭功能后历史保存不再访问 following DB，重新开启后由 Worker reconcile 恢复进度。
- alist 导入记录显式设置 `enabled=true`，确保导入后能被到期查询和后台 Worker 调度；单测覆盖导入 identity 和调度启用状态。
- PLANNED 状态在下一集日期较远时仍至少每 24 小时轻查一次，取“24 小时”和“下一集检查时间”中更早者，避免播出前长期不刷新官方状态。
- 详情页新建追更记录显式设置 `enabled=true`，确保从详情页加入的记录会进入到期查询和后台 Worker；源代码回归测试防止再次漏设。
- 无 TMDB 的源站追更记录在详情页后续匹配成功后，按 `cid + siteKey + vodId` 自动迁移到 TMDB 季身份；迁移保留来源绑定、观看进度和提醒水位，并立即回填当前 TMDB 官方状态，不保留重复可见记录。
- 手机和 Leanback 个性化设置切换“追更更新”后额外发送 `ConfigEvent.common()`，立即重建首页入口；修复开关已为开启但底栏/首页追更按钮仍保持旧可见状态的问题。
- 播放页及 TMDB 头部追更按钮初始为 `gone`，待历史身份与开关状态确认后再显示，避免关闭追更后进入播放页先闪现按钮再消失。
- 追更列表采用可视区域停留后自动已读：卡片进入可视区域约 800ms 后清除未读；打开卡片也会立即标记已读。未读徽标只表示未读更新，已读后不再冒充“未看集数”，显式操作统一为“标记已读”。
- 追更 instrumentation 测试新增真实数据库快照/恢复规则；所有会清表或删除数据库文件的测试结束前恢复原有 `following` 和 `following_source`，`FollowingDatabaseTest` 改用内存数据库，避免设备测试再次清空用户追更数据。
- `following_enabled` 默认关闭；新安装或从未设置过该开关的用户不会显示追更入口，用户显式开启后才注册后台检查并展示追更功能。
- schema 导出为 `app/schemas/com.fongmi.android.tv.following.FollowingDatabase/1.json`。

### 25.3 P5 同步与 alist 导入

- `SyncOptions.follow` 默认关闭；mobile/leanback 共用的一键同步对话框已增加“追更”选项。
- Rust、Deno、Cloudflare、Vercel、Go 五种 serverless relay 的 `normalizeSyncOptions` 均显式保留 `follow=false` 默认值，避免远端规范化时丢选项。
- 追更页新增“导入订阅”手动入口：地址和 token 只在本次对话框输入，token 不落盘；先预览后导入，也可直接导入。
- `AlistSubscriptionImporter` 读取 `/api/media-subscriptions`，支持直接数组、`data[]` 和 `data.list[]`；按 TMDB ID 或“标题+季”匹配现有记录，没有本地记录时建立 TMDB/alist 身份记录并把 `currentEpisodes` 写入来源可播快照。
- 导入能力默认关闭，只有用户在追更页明确提交地址后才设置启用并访问网络；不调用服务端挂载、转存、磁力或自动换源动作。

### 25.4 尚未完成的验证

- 本轮只具备手机 arm64 模拟器与双端编译证据；TV 真机、Doze/弱网、双设备同步和通知拒绝授权后的完整人工矩阵仍需后续执行。
- JavaScript serverless relay 已通过 `node --check`；当前工作环境没有 Go/Rust 工具链，因此这两个 relay 只完成了源码级 `follow` 兼容检查和仓库单测，未执行各自编译。
- 分配的 `5561` 设备是 API 28，因此设计完成定义第 6 条要求的 API 33+ 通知权限实测尚不能成立；不能把 API 28 上的权限代码路径冒充 API 33 验收。
- 按用户明确要求，资源有限时只构建 Debug 测试包和 androidTest 包，不构建正式 Release 包；因此 Release 体积和签名产物验收未执行，不能以 Debug 结果替代。

### 25.4.1 2026-09-20 最新设备回归

- 最新 `mobileArm64_v8aDebug` 测试包与 androidTest 包均使用覆盖安装部署到 `192.168.50.3:5561`，未卸载现有应用。
- 运行 `com.fongmi.android.tv.following` 全包 instrumentation：`FollowingActivityDeviceTest`、`FollowingBackupDeviceTest`、`FollowingCheckDeviceTest`、`FollowingDatabaseTest`、`FollowingDeleteDeviceTest`、`FollowingDetailDeviceTest`、`FollowingNotifierDeviceTest`、`FollowingProjectionDeviceTest`、`FollowingSchedulerDeviceTest`、`FollowingUpdateCoordinatorDeviceTest`，共 10 项全部通过。
- 该回归覆盖最新提交后的 Activity 渲染、Room 主线程安全、取消/检查更新、备份合并、调度、投影队列和前台通知抑制。
- 同一批次执行 `:app:testMobileArm64_v8aDebugUnitTest` 与 `:app:testLeanbackArm64_v8aDebugUnitTest`，双端全量单元测试均通过；最新设备包没有发现 `FATAL EXCEPTION` 或主线程 Room 崩溃。
- 在最后的调度和详情页启用修复后，重新构建 Debug 包并以 `adb install -r -t` 覆盖安装：双端全量单元测试再次通过，追更 instrumentation 仍为 `OK (11 tests)`，日志未出现主线程 Room 或 `FATAL EXCEPTION`。
- 新增源站身份到 TMDB 身份的迁移设备测试后，追更 instrumentation 为 `OK (12 tests)`；测试确认旧 `tmdb_id=0` 记录迁移到 TMDB 季身份、来源绑定跟随迁移，并立即写入官方状态和已播集数。

### 25.5 提交与回滚记录

```text
bd4f15581ebc048cd3f10bb6ab4a5e4d11e9809d  feat(following): implement core following updates flow
6a7b3a85bb310503a59f582314b1345f0a819e14  feat(following): add sync compatibility and alist import
197b995e3cc2f8902aaf810cb152ea3a0c56d869  feat(following): add filter and feature switch
a0787318670ee74771db137618e15822616e4f8b  feat(following): add next season action
dc2e3d4096b85d359c3cf79c5d72b5a3cccba3d6  test(following): verify activity rendering on device
4eb674d89dbd2f2e2e11bffb51b81ba2f1b7d6d4  feat(following): add detail action panel
89a517ce0e79916f82bb57e5cb362befbaf090ee  fix(following): avoid first fetch notification
189e622522aa7eabe06a525b9c2c53b7619791c7  fix(following): harden source probe fallback
1496d617d74124fb258646f62b14541f9d6b948b  test(following): update leanback home button catalog
fb6b5042f42bba4b8c45418cfbcde7a2ec8c1c29  fix(following): preserve source fallback and gate leanback entry
b85a58209f51b553be1b68c4d1d5f0d772936882  feat(following): complete list filters and leanback badge
8994c01086c917d05ec7959e813cc1afa8d8000a  fix(following): enforce season bounds and verify scheduler
e64abf8df4341a5dc5f860ac695577b191673856  docs(following): record debug size and package constraint
78471f77fd4192092c8d1a692ee0f3abeb681f13  test(following): verify backup restore and merge on device
98c198926d02be7e23b0f20c540d08a5e6a5c145  fix(following): prevent main-thread crash and add playback entries
212db245ebef18ce15f963922ed404522a1f06f9  fix(following): add playback entry to mobile layout variants
0b34d1821d82c925a4396cf7fd570ea3f70177c8  fix(following): make cancellation async and unify icon color
f90b311716d30038457a56c583ee5995c93078ff  fix(following): move check-all database read off main thread
6c762b863c8ce202ba7c2119150821af54d776f3  fix(following): refresh home badges off the main thread
feb1f5c78731cc7a8985c876b2e6ac7ddafb2958  fix(following): serialize progress projection off the main thread
33398512e3bf1827829af6ee1a5ca309636d94d0  fix(following): suppress foreground update notifications
ad76a8b3ea0f72c4c677efa15efbcd727bc95630  fix(following): keep released metadata monotonic
d5a72def79f0aee2428d7ddd956ed6114f76abdf  fix(following): select source flag by tracked season
03b7cd5e78d22609fe61b0c7d8f33cacc5890133  fix(following): use latest aired episode from season detail
981b63bf211b42f607f610916b2b84f7590222bf  fix(following): resume from cross-source TMDB history
a6978e6ba4818eab6e1407657deb49c5692e24b5  fix(following): schedule returning shows after next air
7567032bc07a99cb0755f61503e60aa86ae4683e  fix(following): gate progress projection by feature flag
2b8d7943938cde453f173261f792ff0165ef0017  fix(following): enable imported alist subscriptions
1cab41ffc9c6d2dc9d77e42b4967e5f32bbb5b53  fix(following): keep planned shows on a daily check
9b17375d2bf60e7083a01586f882d8ec3382d98f  fix(following): enable detail-page subscriptions
```

每个原子提交均由 `task_guard.sh` 创建独立 `recovery/FOLLOW-1-*` annotated tag；完整标签可在仓库中用 `git tag -l 'recovery/FOLLOW-1*'` 查询。回滚时可以回退到对应提交，也可以仅关闭 `following_enabled`；独立 `following.db` 不需要随 APK 降级删除。

### 25.6 恢复锚点（当前）

```text
objective:         FOLLOW-1 P0–P5 代码、备份/同步、双端入口和手动 alist 导入已实施
authority:         用户已要求按设计实施；当前已提交多个可回滚原子单元
status:            implementation + all simulator-available targeted/device verification complete; badge unread queries are off-main-thread as of FOLLOW-1-BADGE-CRASH; design completion item 6 (API 33+/TV/Doze/multi-device) not available
                          Release/package-size build intentionally excluded by explicit user constraint
next_action:       如需完成正式发布验收，由具备 API 33+ 手机和 TV 真机的环境执行完成定义第 6 条；当前 5561 上没有剩余可执行的代码级验证
rollback_anchor:   following_enabled=false + FollowingScheduler.cancelAll；不修改 AppDatabase v45
```
