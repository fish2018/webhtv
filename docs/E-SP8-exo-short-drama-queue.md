# E-SP8：Exo 短剧队列连播设计

## 0. 状态与 Recovery anchor

- 状态：**IMPLEMENTING／用户已批准按本计划实施；尚未完成代码、设备验收或正式放量**。
- 日期：2026-09-08，Asia/Shanghai；本轮起始时间 20:58，预计 21:16 前完成文档。
- 用户目标：减少短剧自动下一集的重新起播、转圈和画面中断；识别依据使用现有“短剧源设置”，而不是时长猜测。
- 完成标准：第 11 节四阶段实施及第 12 节验收；请求／解析隔离、单实例有界队列、双端业务事务、受控预载全部接通，定向测试和双端编译通过，真实设备对照证据齐备，原子提交及 annotated recovery tag。实验默认关闭，正式放量保留用户批准门槛；不得以 queue-only 或编译通过替代完整完成。
- 本地基线：`dev1@ae64c42369a6b2ee660fee86cbe47c40382df667`；起始工作区干净，无受保护的既有脏文件。
- 实施基线：`dev1@655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`；2026-09-08 21:20 首次恢复，工作区干净，无受保护脏文件。前一设计 guard 已 finished，不存在遗留实施进程。
- 本轮 lane：`upstream`；task guard：`E-SP8`（active）。
- 本轮允许路径：第 11 节明确列出的 App 文件、对应测试、本文件及索引；实际封闭列表见 guard scope。不改依赖／lock／AAR／native／布局／其他页面，不自动切换分支。
- 已完成证据：本地识别及切集调用链、请求头工厂、解析任务取消模型、预缓存保护、本地 AAR API／sources、官方播放列表与预加载资料、FongMi 对照源码、维护者讨论。
- 尚未验证：真实连续切集、各类 LoadControl 的准入组合、Spider 取消／重入、网络争用、字幕／历史时序及设备性能。文档校验不能替代这些验证。
- 当前改动／验证：仅更新本文件和索引记录实施授权；生产代码尚未修改，未运行新测试或构建。
- 回滚锚点：实施基线 `655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`；后续只回滚 E-SP8 原子单元，不更换依赖产物。
- **下一步（唯一）：实现并验证每项 HTTP 请求／缓存身份隔离及队列取消身份契约。**

## 1. 设计结论与非目标

采用：**现有短剧源设置 + 单个 ExoPlayer + 当前集／下一集有界播放队列 + 下一集预解析 + 受控 playlist preload + 真实媒体项过渡提交业务状态**。

成功的自动过渡边界不主动调用播放器的 `stop()`、`clearMediaItems()`、`release()`、`setMediaItem(s)`、`setMediaSource(s)` 或 `prepare()`，也不走 Activity 的完整 `onRefresh()`。这里禁止的是播放器重启，不是禁止取消旧集后台缓存任务。

保证范围是同一次前台播放会话内的 App 行为：不主动重建 ExoPlayer、PlayerView、Surface，不主动重置页面。系统销毁 Surface、生命周期重建、播放器错误恢复和显式换源不属于这条成功路径。

不承诺所有资源使用同一个 MediaCodec、绝对零黑帧、绝对零音频间隙或跨集连续时间轴。每集仍是独立 MediaItem，进度从该集起点计算。编码、音轨、分辨率及渲染要求变化仍可能导致解码器重配置。

非目标：文件合并、本地 HLS／TS 伪直播、FFmpeg 转码、播放器内核迁移、双播放器方案、MPV／IJK／System 队列改造、全局缓存重写、依赖升级、短剧页面重新设计。

## 2. 当前实现与缺口（已核对）

以下代码位置均以本地基线为准，行号仅用于定位。

| 位置／符号 | 现有行为 | 本任务处理 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/setting/Setting.java:922–932` | 读写 `short_drama_config`，统一调用 `isShortDramaSiteEnabled(key, name)` | 原样复用，不新增分类配置 |
| `app/src/main/java/com/fongmi/android/tv/bean/ShortDramaConfig.java:52–63,113–120` | 排除站点优先；启用规则匹配站点 key／名称；无自定义规则时使用 `[短]`、`短剧` | 不改识别语义 |
| 移动端 `VideoActivity.java:7679–7694` | `isShortDramaSource()` 判源；`isShortDramaSession()` 锁存短剧界面形态，避免换源后布局突变 | UI 会话标志不等于队列资格 |
| TV 端 `VideoActivity.java:7192–7199` | 短剧模式同样使用源设置 | 两端复用识别策略；不修改布局 |
| 移动端 `VideoActivity.java:3324–3338,4597–4607,7514–7515`；TV 对应切集路径 | 自动下一集最终进入 `onItemClick()`／`onRefresh()`，保存历史后 stop、clear、重新取流；倒序播放使用 `isRevPlay()` 选择相邻集 | 增加受控自动过渡分支；原路径作为回退 |
| `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java:981–996` | 启动资源时设置单个 MediaItem、启动 PreCache、prepare、play | 首集保留；自动队列过渡不调用 start/restart |
| `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java:201–205,268–276` | 创建源前修改共享 `OkHttpDataSource.Factory` 的默认 headers；使用现有 DefaultMediaSourceFactory | 加入下一集前必须解决请求配置的独立所有权 |
| 同文件 `:208–214,250–265` | 保留 `***`／`|||` 的 ConcatenatingMediaSource2、多协议、提取器、共享磁盘缓存；前台缓存源不写磁盘 | 不替换成仅支持 Progressive 的工厂；特殊拼接首版走旧路径 |
| `app/src/main/java/com/fongmi/android/tv/model/SiteViewModel.java:141–155,221–231` | 同类 PLAYER 任务递增 taskId、取消上一个 Future，并投递到前台 LiveData | 下一集预解析不能直接复用这个结果槽，否则会覆盖／取消前台播放请求 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/PreCache.java:149–193` | 绑定当前 MediaItem、磁盘 key、当前路线与生命周期；已有缓冲、seek、内存、系统条件及熔断保护 | 不是现成的“下一集预加载管理器”；真实过渡时需要重新绑定当前项 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/AutoLoadControl.java:185–190,262–266` | 已有 `shouldContinuePreloading` 委托和内存暂停约束 | 保留并补足队列准入，不用默认 LoadControl 覆盖它 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ExoPreloadTrafficCoordinator.java` | 已统计 CUSTOM／MEDIA3 预载竞争，带会话身份与释放保护 | 可复用统计，**它不是互斥调度器**，不能误认为已经消除竞争 |
| `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:8442` 起的 `Player.Listener` | 当前状态、轨道、不连续、错误与诊断回调 | 补媒体项身份桥接和自动过渡提交，不假定已有业务 Listener API |

本仓库已有的 UI 换源保持修复 `af0bc991d9e281a19238dc292f85d651304de696` 和短剧手势修复 `86d351d54c21c89f72d8559f0283a5887e5b792e` 必须保持。短剧 dock、全屏形态、上下滑切集不能被队列重构覆盖。

## 3. 短剧识别与首版准入

### 3.1 分类唯一依据

```text
Setting.isShortDramaSiteEnabled(currentSiteKey, currentSiteName)
    1. disabledSites 中站点 key 忽略大小写精确命中 → false
    2. enabledSites 非空 → 按自定义规则判断
    3. enabledSites 为空 → 按默认 [短]、短剧 判断
    4. 启用规则对站点 key 或名称进行忽略大小写的包含匹配
```

这是站点级分类，不识别每部作品的类型。综合源被配置成短剧源后，其中作品不会因时长不同而自动退出短剧分类；不要擅自加入标题、分辨率、竖屏比例或“少于五分钟”的猜测。清空启用规则会回到默认规则，不代表关闭全部短剧源。

### 3.2 分类、资格、时机分离

| 决策 | 依据 |
| --- | --- |
| 是否短剧场景 | 当前源设置；UI 锁存只负责界面 |
| 是否允许队列增强 | 下述首版准入条件、实验放量及用户现有预加载设置 |
| 何时解析下一集 | 当前首帧后、前台解析空闲、资源条件许可时尽早一次性解析 |
| 何时下载下一集数据 | 当前播放安全水位、seek 恢复、负载与取消完成状态；时长／进度仅作调度输入 |

首版准入条件全部满足才启用：

- 当前源命中短剧配置；Exo 视频点播；存在相邻集；实验开关允许；用户没有关闭相关预加载能力。
- 已获得或可以安全后台获得普通 HTTP(S) HLS VOD／渐进式 MP4 直链及完整请求配置；URL 后缀不能单独证明协议类型。
- 不需要 DRM 会话、嗅探／二次解析、内核切换、特殊解密或 renderer／特效重建；不包括直播、RTSP、光盘／ISO、内部多段拼接、音频／阅读器和未验证代理生命周期。
- 首版使用普通顺序或现有倒序规则；不支持的随机／重复模式继续走旧路径，不修改用户配置来强行准入。
- 下一集没有必须保留的非零起播点、片头／片尾跳过或其他提前切集语义；在这些语义接通并验证之前，**不进入增强路径**，不静默忽略它们。
- 当前会话未处于前台恢复、seek 恢复、内核降级或错误熔断；后台解析能够遵守第 5 节取消与线程约束。

这是首版功能覆盖限制，不是把这些资源重新分类为“非短剧”。不准入时仍保留短剧界面和原有播放功能。

## 4. 架构与数据契约（拟新增，不是当前已存在的 API）

```text
Mobile / Leanback VideoActivity
  ├─ 原首集／手动选集／失败回退路径（保留）
  └─ ShortDramaQueueCoordinator（共享业务状态机，拟新增）
       ├─ 短剧准入、按既有顺／倒序找相邻集
       ├─ 独立下一集解析结果通道、generation 取消隔离
       └─ PlayerManager 最小队列接口
            └─ ExoPlayerEngine
                 ├─ item-scoped MediaSource → addMediaSource
                 ├─ 现有 LoadControl + 有界 playlist preload
                 └─ 真实媒体项事件 → 当前业务状态提交
```

只新增一个共享业务协调器，避免在手机和电视端复制两套状态机；快照／结果类型优先作为嵌套不可变类型，不预先搭建泛化播放队列框架。

建议的引擎契约是 `supportsQueue()`、`appendNext(snapshot)`、`invalidatePending(generation, reason)` 及结构化过渡事件，具体签名在实施前按现有接口收敛。其他内核默认返回 unsupported，不增加实际队列行为；业务 Episode 对象不泄漏进底层引擎接口。

| 数据 | 内容与约束 |
| --- | --- |
| 会话身份 | `generation`、配置／站点 key、作品 ID、线路、画质、内核、顺倒序快照；与 UI 短剧会话锁存分开 |
| 剧集身份 | 使用作品／线路／剧集稳定 ID；若无 ID，则使用当前列表快照内不重复身份。不能只用标题或播放 URL |
| `mediaId` | 队列内唯一、可反查剧集，包含 generation；不携带明文 URL、Cookie、Token |
| 已解析项 | 不可变 URL、headers 副本、MIME／路线判断、字幕信息、起播策略、有效期（仅可信字段）、原 PlaySpec 必需语义 |
| 过渡快照 | old/new mediaId、旧集 position／duration 快照、新集 position、reason、generation、事件序号 |
| 幂等键 | `(generation, episodeIdentity)` 防重复入队；`(generation, oldMediaId, newMediaId, eventSequence)` 防重复提交 |

**播放器队列索引只是瞬时位置，不是剧集编号。** 用 `getCurrentMediaItemIndex()` 与真实 `mediaId` 校验；清理旧项后索引可以归零，禁止 `currentIndex++` 推导业务剧集。

所有播放器读写及队列状态提交在 `player.getApplicationLooper()` 上串行执行。耗时解析、历史落盘和网络请求不阻塞该线程；跨线程只传不可变快照，不读取已经换集的 Activity 可变对象。

## 5. 下一集解析：独立结果、前台优先

1. 首集继续走现有 getPlayer／start／prepare 流程，成功建立会话后才初始化增强状态。
2. 当前首帧后，在准入和前台空闲条件允许时尽早解析一个相邻集；不强制等到 80%，避免很短的剧集准备过晚。
3. 复用现有 `SiteApi.playerContent(..., explicitPlayerType)` 语义，但**不得向 `SiteViewModel` 的前台 PLAYER LiveData 投递预取结果，也不得复用其取消槽**。
4. 后台结果先验证 generation、作品、源、线路、顺倒序及目标剧集，再生成不可变播放快照。预取完成不得选中下一集、更新标题、写当前播放历史或调用 start。
5. 同时最多一个下一集解析任务；取消／超时后旧结果只能丢弃，不能重新激活队列。下一集网络解析失败不消耗前台播放的重试预算。

独立结果通道不等于允许 Spider 并发重入。必须核对相应运行时的线程约束；复用受控执行器而不是每集建线程。手动取流优先，后台任务须可取消或在不阻塞前台的情况下让出资源。对会忽略中断、共享全局解析状态或需 WebView 嗅探的站点，首版不调度后台取流；已有可靠直链可以单独准入。不得为了预解析引入前台排队等待的回归。

签名 URL 不猜测失效时间，也不剥离 query。可信有效期不足以覆盖预计使用窗口时不入队；有效期未知仍可能在切集时失败，按第 9 节重新取流一次，不能宣传预解析后必然可播。

## 6. MediaSource、请求头与缓存隔离

本次发现的实质前置风险是：现有 `createMediaSource()` 修改共享 HTTP 工厂默认属性。在 A 正播放时为 B 改写这些属性，可能使 A 后续创建的请求继承 B 的配置；这属于由当前结构推导出的并发风险，尚不是已复现的串头事故。

首版使用 **item-scoped MediaSource**：基于现有 `MediaSourceFactory` 的构造链，为每个队列项持有独立、初始化后不再变更的 HTTP／上游工厂与 headers 副本，再通过 `player.addMediaSource(nextSource)` 追加。共享受管理的 Cache 和连接客户端，但不共享可变请求默认属性。

必须保留现有 DefaultDataSource／DefaultMediaSourceFactory、HLS／MP4 类型识别、UA 清理、EOF 恢复、优先级、提取器、轨道设置与缓存会话引用计数。不得创建额外 SimpleCache 指向同一目录，不能因新增工厂增加不配对的 acquire/release。

补充约束：

- A 的分片／Range 请求、B 的 playlist／分片请求分别使用各自快照；取消 B 不改变 A。
- 预加载与前台消费同一个队列项时使用相同缓存身份。涉及认证隔离的缓存命名空间必须覆盖站点和认证上下文，不能把同 URL 不同身份当成同一资源；不得删签名来追求命中率。
- HLS 子请求、重定向、CookieJar 和代理也要验证身份边界；headers 副本并不意味着共享 CookieJar 已完全隔离。无法保持这些约束的路线暂不准入。
- 下一集的资源判断、解码／DV 状态、字节位置统计不能提前覆盖当前集的运行上下文。实际切换时才提交新 PlaySpec／route／trace；依赖共享可变特殊媒体状态的路径先排除。

## 7. 预加载策略：不要把入队当作下载完成

区分四件事：URL 已解析、MediaSource 已构造、播放器已接受队列项、下一集数据已经可用。`addMediaSource()` 成功只证明追加操作成立，不能记录为 `preload_ready`。

### 7.1 方案选择

首选当前 AAR 已有的 playlist preload，不引入第二个播放器或 DefaultPreloadManager。内置能力可通过 `ExoPlayer.PreloadConfiguration` 设置目标时长；本地字节码确认支持，默认配置关闭此项，单位是**微秒**。这是配置示意，不是本轮已实施代码：

```java
// 仅在队列准入、安全调度与用户设置全部允许时配置。
player.setPreloadConfiguration(new ExoPlayer.PreloadConfiguration(5_000_000L));
// 非增强会话恢复原配置；首次启动仍沿用现有流程。
```

初始候选目标为下一集 5 秒，实验上限 10 秒，并受用户更严格设置限制；这些是待测参数，不是最佳实践结论。按时长限额不等于按字节限额，HLS 分片粒度、码率、allocator 和在途请求仍需预算控制。

### 7.2 与 PreCache 的所有权

- 当前集磁盘预缓存继续由现有 PreCache 负责；它不能直接被称为下一集预取器。
- 下一集额外内存预载由 playlist preload 负责；不再给同一下一集另开 PreCacheHelper／DefaultPreloadManager。
- 复用 `ExoPreloadTrafficCoordinator` 的会话统计，但补充明确的准入与让出策略：**前台当前集／seek 恢复 > 当前集缓存安全契约 > 下一集额外预载**。
- 首版不允许额外的下一集预载与活跃自定义磁盘预缓存无约束竞争；先等 PreCache 自然空闲／任务取消确认，再放行。不能仅调用 stop 后立刻认为旧网络任务消失。
- 不为提前下载下一集降低用户当前集缓冲水位、不删 E-SP3 的 seek 恢复与熔断保护。无法安全获得预载窗口时允许“已排队但未额外预载”，记录降级原因。
- 默认、增强、自动等所有可达 LoadControl 分支都要挂接相同准入；保留 AutoLoadControl 的内存暂停条件。依赖配置存在不等于整个 App 策略已经接通。

### 7.3 安全闸门与资源上限

进入 buffering、seek 恢复、后台／暂停策略限制、内存／存储／网络／电量限制、用户关闭预载、全局实验抑制或错误熔断时，停止提交额外预载并按现有生命周期处理在途任务。后台线程只读取线程安全的策略快照，不能读取 Activity。

当前集安全水位按**当前媒体项**计算，不能把下一集缓冲计入当前集保护量。队列稳定态至多当前集＋下一集；当前集结束后清理已消费项，再准备下下集。不预解析全剧、不同时下载多个后续集。

当前前台 MediaSource 的 CacheDataSource 已关闭磁盘写入；新增 playlist preload 不得偷偷开启它。磁盘容量、缓存写入、计量网络和用户暂停预载选择维持原契约。若用户明确关闭预载，首版整体不进入该增强路径，避免通过排队绕过选择。

## 8. 队列状态与自动切集事务

### 8.1 状态机

下表描述相对于当前集的“下一集槽”，额外保存当前播放身份；`QUEUED` 与 `PRELOADING` 不代表已经切集。

| 状态 | 触发／动作 | 后继 |
| --- | --- | --- |
| DISABLED | 不满足准入 | 原播放路径 |
| EMPTY | 当前集稳定、可准备相邻集 | RESOLVING |
| RESOLVING | 独立解析；结果通过 token 校验 | RESOLVED／EMPTY／DISABLED |
| RESOLVED | 不可变请求快照与隔离源完成 | 追加后 QUEUED |
| QUEUED | 等待播放器自然过渡；安全窗口内额外预载 | PRELOADING 或保持 QUEUED |
| PRELOADING | 有下一集身份的加载证据；受闸门控制 | QUEUED／TRANSITIONING |
| TRANSITIONING | 播放器当前 mediaId 已变为该项 | 一次性提交后 EMPTY |
| INVALIDATED | 手动操作、配置变化、退出或旧 token | 丢弃旧结果、取消旧后台任务 |
| FALLBACK | 已无法安全自然过渡 | 一次旧路径重播；本 generation 禁止再次自动尝试同项 |

所有状态变更幂等。收到空 mediaItem、初始化／清空列表事件时不得推进集数；同项 seek、repeat、playlist mutation、remove-old 引发的事件也不能当成“自动下一集”。

### 8.2 真实过渡提交

正常队列内部切换由媒体项事件驱动，不依赖每集都出现 `STATE_ENDED`：

1. `onPositionDiscontinuity(oldPosition, newPosition, reason)` 捕获旧集身份与结束位置快照；不能切换后读取 `getCurrentPosition()` 再写到旧集。
2. `onMediaItemTransition()`／同批次 `onEvents()` 对照播放器真实 mediaId、reason、generation、预期相邻项，形成一次提交；不得假定不同回调总以业务期望顺序到达。
3. 用旧快照提交上一集历史／远端进度，随后更新新集对应的选中状态、标题、当前历史对象、MediaSession／外部播放状态。
4. 引擎更新当前 PlaySpec、route、诊断身份和缓存绑定；取消旧 PreCache generation 后以新项重新建立当前集缓存会话，不调用播放器 start／prepare。
5. 切换字幕、弹幕和 TMDB 集信息；取消旧集异步任务，新回调均校验媒体身份。旧内容清除与新内容绑定按现有字幕／弹幕能力执行，不重建 PlayerView。
6. 提交完成后移除已消费队列项，再为下下集创建 EMPTY 槽。保留必要的旧历史不可变快照，不保留旧 MediaSource／Activity 引用。

保存历史不要求阻塞播放线程等待磁盘或网络完成，但写任务必须携带独立身份，并保持旧集／新集语义顺序。新媒体项的轨道、时长和首帧可能晚于 transition 到达，应按当前身份增量更新，不能拿上一集信息补成下一集的最终状态。

### 8.3 与 STATE_ENDED 及手动操作的关系

只有“当前会话有效、确有下一项且未落入失败状态”的自动边界由队列协调器消费。队列耗尽、未及时排入下一集或准入失效时，原结束处理仍可推进一次；不得全局屏蔽 STATE_ENDED，也不得仅凭 `nextReady=true` 永久等待。

手动选集、上下滑切集、换源、换线路、换清晰度、换内核、顺倒序变化、onNewIntent 和退出页：先使 generation 失效、清理未来项与后台请求，再走现有显式操作。首版**不把手动滑动伪装为自然过渡**。UI 短剧锁存保持原行为，新源的队列资格重新判断。

## 9. 错误与回退

| 失败位置 | 处理 |
| --- | --- |
| 解析失败／不支持／取消／签名预计过期 | 不影响当前播放；不追加；当前集结束时交给旧流程 |
| 下一集尚未成为当前项时的预载失败 | 能准确归属下一项时取消额外预载、必要时移除该项；不把当前集标为失败，不触发换内核／换源熔断 |
| 错误无法归属媒体项 | 不猜测是“无害预载错误”；走已有播放器恢复规则并禁用本次增强，保留诊断 |
| 已自动进入下一项但起播失败／401／403 | 按真实当前项保存业务身份；对这个目标集重新解析并走一次原 start 路径，不错误跳到下下集 |
| 已排队却未过渡 | 检查实际 timeline、播放状态和错误；仅在前台应继续播放且越过预先定义的等待门槛时一次回退，不在暂停、后台或反复 buffering 中循环 restart |
| 历史／字幕／弹幕新集任务失败 | 不重启正在正常播放的视频；隔离业务错误并提供原有重试／缺省展示 |

回退可以调用 stop／clear／prepare，但必须标为 `queue_fallback`，不统计为无缝成功。每个 generation／目标剧集最多一次队列到旧路径的接管；之后遵循既有错误处理，禁止死循环、双重推进或静默跳过失败集。

## 10. 方案比较与最佳实践审查

### 10.1 决策问题

在不迁移内核、不损失现有缓存／seek／请求安全契约的条件下，是否可用当前 Media3 的播放列表替代短剧自动切集的完整刷新？备选反假设是：仅复用上游预载或简单追加即可解决问题。源码显示还必须补解析隔离、请求头所有权与业务状态事务，因此不能只改一个播放器管理方法。

| 方案 | 正确性／兼容性 | 性能、维护与结论 |
| --- | --- | --- |
| 不改动 | 现有行为稳定，保留全部功能 | 切集仍 stop／clear／prepare；作为基线及回退 |
| 原样采用用户最初 ConcatenatingMediaSource 示例 | 忽略当前多格式源和业务状态；不选择新功能的过时入口 | 不采纳；已有 ConcatenatingMediaSource2 内部拼接功能保留 |
| 原样移入当前 FongMi 的 DefaultPreloadManager 消费路径 | 其 Session／PlaySpec／字幕架构与本地不同；消费仍 setMediaItem/Source + prepare | 有预热价值，但不满足成功边界无刷新，不能作为直接补丁 |
| 仅 addMediaItem，无预解析／请求隔离／状态事务 | 无法保证下一项可播，可能串请求配置或错写历史 | 不完整，不批准作为成品 |
| **WebHTV 窄适配：隔离源 + 动态队列 + 受控原生预载** | 保留现有源设置、多格式工厂、缓存保护及旧路径；补充明确所有权 | **推荐**；先验证队列正确性，再启用有界预载，不升级依赖 |
| 本地 HLS／TS／双播放器 | 改变时间轴、容器或 Surface／解码器所有权 | 超出需求，拒绝首版引入 |

推荐是“补全并适配”，不是上游整类替换。只排队但未获得预载窗口可以作为运行时降级；不得将阶段性 queue-only 验证称为完整预加载优化完成。

### 10.2 固定来源与逐项处置

本任务是 App 新需求设计，不是上游提交范围合并；没有新增 cherry-pick 范围。以下完整 revision 均有明确用途，不能由本表推导出依赖升级授权。

| 仓库／组件 | 完整 revision | 处置 |
| --- | --- | --- |
| 当前 WebHTV 工作树 | `ae64c42369a6b2ee660fee86cbe47c40382df667` | 设计与回归基线 |
| `third_party/media-lock.json` 声明的 Media3，仓库 `fish2018/webhtv`，分支 `media/release-1.11.0-alpha01-fongmi` | `e3e922d5c01bc0b564849940fe589daf37360d15` | 保留锁定，不升级；以本地 AAR／sources 进一步确认所需 API |
| nextlib，`anilbeesetti/nextlib` | `6ff6cf9d0820382b3c233d018c52e4163b09d345` | 仅记录现有依赖，排除修改 |
| nextlib 对应 FFmpeg，`FongMi/FFmpeg` | `177f090e0503b7e013922ca903bde14b1c375f18` | 排除修改，不触及 ABI／软解产物 |
| 对照项目 `FongMi/TV`，2026-09-08 读取的 `fongmi` 分支快照 | `4afc4473e22a7ed3d98ee12233e0c2a490061000` | 仅参考 ExoPlayerEngine／ExoPlayerSession 预载消费，不移植整个架构 |

本地坐标：`androidx.media3:media3-exoplayer:1.11.0-alpha01-fongmi`；不是将官方稳定版 `1.11.0` 当成本项目版本。

- 本地 AAR：`third_party/maven/androidx/media3/media3-exoplayer/1.11.0-alpha01-fongmi/media3-exoplayer-1.11.0-alpha01-fongmi.aar`；SHA-256 `cfea29681799509923174cac7ce31b7e944e542db9df58fd56b7a86499a5c07e`。
- 同目录 sources JAR：`media3-exoplayer-1.11.0-alpha01-fongmi-sources.jar`；SHA-256 `83f4f83b4f44e621d52002c161f63fbcb77be6856af4b1e4a4cb0982b04549e1`。
- `javap` 已确认 `addMediaSource`、`setPreloadConfiguration`、`PreloadConfiguration(long)`、`targetPreloadDurationUs`；sources 中 `ExoPlayer.java:192–210` 确认微秒单位和默认关闭。`LoadControl.java:373` 的接口默认实现不放行，不能忽略自定义实现；`DefaultLoadControl.java:832–839` 先检查前台加载状态。

### 10.3 证据登记

访问日期统一为 **2026-09-08**。A＝直接源码／测试／官方契约；B＝维护者解释或成熟项目；D＝未经本项目复现的现场线索。所有来源只支持其列出的结论，不证明 WebHTV 性能已改善。

| 证据类别／等级 | URL／固定路径与版本 | 支持的结论、适用性及决策影响 |
| --- | --- | --- |
| 本地实现／A | 第 2 节代码，WebHTV 基线 revision | 源设置已有、自动切集刷新、前台解析单槽及共享 headers；决定必须跨引擎／业务桥接窄适配 |
| 精确依赖源码／A | 上述 AAR／sources SHA；ExoPlayer、LoadControl、DefaultLoadControl | 所需 API 已打包，无需升级；原生预载不会自动理解 App PreCache 竞争 |
| 本地测试／A（仅阅读，未运行） | `app/src/test/java/com/fongmi/android/tv/player/exo/AutoLoadControlTest.java`、`ExoPreloadTrafficCoordinatorTest.java`、`PreCacheTest.java`、`MediaSourceFactoryTest.java` | 已有内存暂停、会话隔离及源工厂验证入口；新增用例应补而不替换这些保护 |
| 官方播放列表指南／A | `https://developer.android.com/media/media3/exoplayer/playlists` | 支持播放期间修改列表及 mediaId 映射；自然过渡可报告媒体项事件，决定不用 currentIndex++ |
| 官方预加载说明／A | `https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager` | 简单顺序队列适合 playlist preload，复杂候选管理才考虑 DefaultPreloadManager；本次只需一个下一项 |
| 官方技术文章／B | `https://developer.android.com/blog/posts/elevating-media-playback-introducing-preloading-with-media3-part-1` | 内存预载与磁盘缓存不是同一功能，前台加载优先；5／10 秒并非本项目实测最优值 |
| 维护者讨论／B；提问者现场／D | `https://github.com/androidx/media/issues/2804`，读取正文及 2025-09-25／26 评论 | 通过首帧／buffering 时间验证收益，不应因配置调用成功就认定生效；讨论是人工切项案例，不能直接代替自然过渡验收 |
| 成熟相关项目／B | `https://github.com/FongMi/TV/blob/4afc4473e22a7ed3d98ee12233e0c2a490061000/app/src/main/java/com/fongmi/android/tv/player/exo/ExoPlayerEngine.java` 及同目录 `ExoPlayerSession.java` | `usePreloadedMediaSource()` 后仍调用 set／prepare；说明预热和队列自然过渡不同，本地不整类迁移 |
| 历史修复／A | `docs/E-SP3-exo-seek-preload-isolation.md`，Media3 修复 `c9d3bd912b90ec0ca440c28455f3e6d9bba019ea`、App 修复 `17f1a4cfe2547b8f3ddc61fab34212e77ae719ff` | 已有 seek 取消延迟、恢复水位、HLS 请求竞态证据；下一集预载不能绕过这些约束 |

已使用 GitHub `gh` 阅读固定快照和完整 issue 正文／评论；公开网页读取官方正文，不以搜索摘要作结论。API reference 页面抓取未成功，相关 API 可用性以本地字节码和 sources 为依据；未据此臆造最新版 API 行为。

论文／算法 benchmark 不适用：本任务不发明解码、预测或调度算法，当前决策取决于既有 API、App 生命周期和设备对照结果。相关技术文章和现场线索已纳入；缺少本项目可比较性能数据，明确留待第 12 节，不能标注“实测无缝”或“最佳性能”。

## 11. 实施范围与阶段（2026-09-08 已批准）

仅增加一条 Exo 短剧路径。用户以“本文件按计划实施”批准以下实施范围；已再次核对实际分支与脏文件，保留 `dev1`，不默认切换 `fongmi-sync` 或合并其他任务。

| 范围 | 最小职责 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/player/ShortDramaQueueCoordinator.java`（拟新增） | 识别／准入、相邻集、解析结果隔离、状态机及业务提交协调 |
| `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`、`player/engine/PlayerEngine.java`、`player/engine/ExoPlayerEngine.java` | 默认 unsupported 的最小队列契约、Exo 入队、媒体项身份与当前上下文切换 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java` | 每项请求配置隔离，保留现有工厂功能 |
| 同目录 `PreCache.java`、`AutoLoadControl.java`、`ExoUtil.java`、必要时 `AutoTargetLoadControl.java`／`ExoPreloadTrafficCoordinator.java` | 复用当前缓存策略、LoadControl 准入、真实切换重绑定与竞争诊断；不泛化重构 |
| `app/src/main/java/com/fongmi/android/tv/model/SiteViewModel.java`（仅确需时） | 复用解析能力但隔离结果／取消槽；不得更改前台结果契约 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 与 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 区分自然过渡与显式刷新；复用共享协调器，不改 PlayerView 布局 |
| 上述对应的 `app/src/test/` 测试及唯一任务文档／索引 | 定向回归与证据 |

若实际调用链需要扩展其他播放器页面、HTTP 层或设置模块，先解释必要性并征得新范围批准，不把它悄悄并入“只改管理类”。特别是 TMDB 内嵌播放入口，首版仅对能走上述两端 VideoActivity 的会话放量；其他入口保持旧行为。

阶段顺序：

1. **契约与请求隔离**：先补队列身份、取消、headers／缓存隔离的可失败测试，核对前台解析及 LoadControl 可达分支；不启用功能。
2. **队列正确性**：一个当前项＋一个下一项，真实过渡只提交一次，两端状态同步与一次回退；实验仍关闭，queue-only 仅用于归因验证。
3. **受控预加载**：接通用户设置、资源／取消闸门与原有 PreCache 保护；确认真实下一集加载证据及前台不受损。
4. **对照验收与放量**：双端编译、风险场景、同源 HLS／MP4 性能对照通过后才批准首版启用。一个逻辑单元优先同 guard／原子提交；不能以阶段 2 结束冒充全部完成。

实施耗时不在本文虚报人日；用户批准时按当时工作树、热缓存、设备和样片可用性重新给出当前 agent 的分阶段墙钟估算。设备等待单列，文档产出时间不等于功能交付时间。

## 12. 验证与验收标准

### 12.1 最便宜的确定性验证

首批验证状态机／事件顺序和源隔离，不先全 ABI／全播放器构建：

- 短剧规则：默认关键词、自定义替换、排除优先、综合源、清空回默认；任何 duration 不改变分类。
- 队列：同集重复回调、旧 generation、移除旧项后索引变化、repeat／初始化／空项、不同时序的 discontinuity／transition／events。
- 解析：预取不触发前台 observer、不取消手动取流；不可中断 Spider 不准入；用户连续选集后旧结果不复活。
- 请求：A／B 不同 Referer、Cookie、UA；A 播放时 B 建源／取消，A 后续请求仍正确；同 URL 不同认证不错误命中缓存；HLS 分片与重定向覆盖。
- 缓存与负载：旧 PreCache 任务退出前不放行新额外任务；seek／内存／后台／用户关闭时抑制；计数释放幂等、不同会话不互相借用状态。
- 业务：上一集结束位置不写到下一集；新集标题／字幕／弹幕／历史／外部播放状态只提交一次；预加载从不写“已观看”。

补充测试应包含共享协调器测试和 Media3 可控时间线／假数据源的引擎桥接测试。仅测字符串／状态纯函数不足以证明真实 Player 回调时序。对已有 `PreCacheTest`、`MediaSourceFactoryTest`、`AutoLoadControlTest`、`ExoPreloadTrafficCoordinatorTest` 做必要定向回归，测试名称以实际新增类为准。

代码通过后按修改范围编译 Mobile／Leanback Arm64 Java；本任务不修改 native，不要求重编所有 ABI。真实播放仍需两端代表性验证，手机通过不冒充电视通过。

### 12.2 设备场景与正确性红线

| 场景 | 验收要求 |
| --- | --- |
| 同源 HLS、同源 MP4，各连续多集 | 播放器／View／Surface 无 App 主动重建；成功自动边界无 stop／clear／set／prepare；集数正确 |
| 不同 CDN／headers、MP4→HLS、AVC→HEVC | 请求不串；允许 decoder 重配置；不宣称此组零黑帧 |
| 弱网、后台／前台、暂停、seek、临近结束手动滑动 | 前台优先；暂停不被后台回调强行恢复；旧队列不干扰手动操作 |
| 下一集超时、404、401／403、签名失效、无法归属的播放错误 | 当前播放不被预取错误误杀；目标集仅接管一次，不跳集、不死循环 |
| 末集、倒序、重复／随机、片头片尾／非零起播点 | 维持既有语义；未支持模式不准入，而不是悄悄关闭用户功能 |
| 普通长视频、音频、直播、MPV／IJK／System | 旧路径不启用队列副作用；挑选受共享接口影响的代表用例回归 |
| 远端历史同步、字幕／弹幕异步迟到 | 身份正确，旧任务不能覆盖新集，不出现预取即上报观看 |

功能红线：零串集、零串请求身份、零错误历史归属、零重复推进、零生命周期泄漏；任一失败不放量。

### 12.3 性能方法与候选目标

冻结基线／候选 App commit 和 APK SHA、设备／OS／内核／解码路径、样片、网络、缓存冷热、设置及温控。相同条件各至少 3 轮，每轮至少 30 个自然边界，记录全部数据及 p50／p95／离散程度；变异过大时不宣布改善。

主要指标：自然边界到下一集首帧的间隔、buffering 时长／次数、音频中断、掉帧与 A/V 同步、队列／预载命中率、失败回退率、当前集 startup／seek 延迟、PSS／Java heap、网络读量及放弃下一集的浪费量。

候选实验目标（测试前固定，非已有结果）：同源同编码受控样片自然边界首帧间隔 p95 ≤ 300 ms；若原基线明显高于 300 ms，目标至少降低 30%。基线已经较快时以不退化为主。不得通过降低画质、当前缓冲安全线或取消功能达标。

startup／seek、重缓冲和稳定播放质量不得发生可重复的实质退化；超过测量噪声的恶化必须分析，不能用连播收益抵消。首轮可把 `max(50 ms, 基线的 10%)` 作为 startup／seek 告警线而非允许回归额度。内存必须达到稳定平台；连续 100 次切集后不能随集数增长，队列稳定态不超过两项；额外下载只允许一个未来项并遵守既定预算。

`onMediaItemTransition()` 不是首帧。优先用带媒体身份的渲染／加载事件归因；若当前版本不能逐项提供可靠首帧事件，应标记指标不可用，并用帧时间戳／录屏及音频采样补证，不能把 transition 时间当作零延迟。`getTotalBufferedDuration()` 或设置了 5 秒预载目标也不能单独证明下一集已预载 5 秒。

### 12.4 日志与隐私

复用 PlaybackTrace／现有诊断，不逐帧打日志。记录 `queue_eligible`、`resolve_next`、`queue_append`、`queue_drop_stale`、`preload_allowed/suppressed`、`media_transition_commit`、`queue_fallback`、`queue_invalidate`。

字段限于 generation、脱敏剧集／mediaId、reason、阶段、耗时、字节／缓冲摘要、错误类别、是否自然过渡及计数。没有加载证据时不要发 `preload_started/ready` 成功事件；无法归属时标记 unknown。禁止明文记录 URL 签名、Cookie、Authorization 或 Referer 凭据。

## 13. 风险、兼容性、放量与回滚

- 正确性／生命周期：最高风险是旧回调污染、双重推进、旧历史写入新集、预缓存未退出与新任务竞争；由身份、单线程事务和确定性取消测试约束。
- 安全：请求头副本之外还须验证缓存、CookieJar、代理及日志隔离；来源未知时保守不准入。
- 性能／功耗：下一集预载可能增加内存、流量与电量消耗；必须保留前台优先和用户限制，以对照结果决定放量，不先承诺提升百分比。
- 兼容性／质量：首版范围之外不改变行为；同实例不代表同 decoder，不降低既有解码／渲染／字幕能力来换取“无感”。
- ABI／包体／许可／供应链：本设计不升级 Media3／nextlib／FFmpeg，不改 JNI、SO、AAR、lock 或 ABI；预计只有少量 App 字节码增长，实际 APK 差异实施后测量，不声称包体零变化。引用上游设计不等于复制代码，若后续移植代码须保留对应许可与来源。
- 维护：一个共享短剧协调器、现有引擎与工厂窄接口；不另建通用预载框架，不把架构较新的 FongMi Session 强行移入本地。

放量：开发／实验默认关闭 → 定向测试源启用 → 同源 HLS／MP4 两端验收 → 用户批准后启用首版范围。现有短剧源设置是分类入口，不新增另一份源名单；实验开关是发布／回退控制，不是内容识别。

回退：关闭实验策略并让当前会话安全失效，取消未来项及后台任务，当前集继续播放，到下一次显式／自然边界使用旧流程；不要为了关开关立即 stop 当前正常播放。代码回滚使用后续 E-SP8 实施原子提交的 `git revert`，同时回滚其桥接／策略／测试，不回滚其他任务和既有脏文件。不需要替换播放器依赖产物。

## 14. 实施前置条件与本轮验证记录

以下条件必须在批准实施后的首个单元中解决，不能默认为已通过：

1. 每项源工厂与所有相关子请求真正隔离，缓存身份和特殊媒体状态不会跨项污染。
2. 下一集解析结果与前台隔离，且对应 Spider／运行时不产生不可取消阻塞或并发重入；不能证明的站点不放量。
3. 所有实际可达 LoadControl 分支、PreCache 生命周期及用户／系统闸门有一致准入，额外预載不降低当前播放保护。
4. 两端真实 Player 回调顺序下，历史／字幕／弹幕／外部进度事务正确，并能一次回退。
5. 真实样片和设备对照达到第 12 节标准；没有设备数据时只能报告代码／测试状态，不能宣告播放优化完成。

本轮已做：只读源码及本地 AAR 字节码／sources 核对、固定上游参考快照、官方资料与维护者讨论阅读；未运行 Android 构建、单元测试或设备播放。文档归档采用一次组合检查：改动文件格式、相对路径／索引互链、来源 revision 格式、未授权状态声明、检查点脚本；具体通过证据写入 task guard 提交说明。

本文件是 E-SP8 唯一任务记录。后续批准、实现、测试、修复、完整 commit／tag、放量和回滚都追加于此，不另建同任务的 plan／implementation／fix 文档。本轮文档提交与 annotated recovery tag 由 `E-SP8-DESIGN` task guard 生成；实际完整 ID 以该提交元数据及交付回执为准，不在提交前虚填自身 hash。

## Recovery anchor（2026-09-09 10:08 Asia/Shanghai）

- 目标：按本文件已批准范围完成 E-SP8 Exo 短剧队列连播；当前仍未完成，实验保持默认关闭。
- lane/scope：`upstream` / `E-SP8`；仅修改本文件允许的 Exo、SiteViewModel、PlayerManager、两端 `VideoActivity`、队列协调器、测试及本任务索引。
- branch/HEAD：`dev1` / `655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`；设计 recovery tag 已存在：`recovery/E-SP8-DESIGN/20260908211614-655bf1a61e75`。
- protected dirty paths：任务开始时为空；当前未提交任务改动均为 E-SP8 已声明路径，无预存脏文件混入。
- 已完成证据：MediaSource 前台按请求 headers 建立独立工厂；cache namespace 为 header 的 SHA-256；`PreCacheHelper` 通过 scoped `Cache` 使用同一 namespace；`SiteViewModel` 已有独立串行下一集解析 executor/future/callback，绝不写入前台 `player` LiveData；`ShortDramaQueueCoordinator` 已实现 generation、顺/倒序、当前/下一项有界队列、一次性 AUTO transition、准入拒绝和过期回调丢弃；`PlayerEngine`／`ExoPlayerEngine`／`PlayerManager` 已增加默认 unsupported 的只追加、未来项移除及自然过渡上下文提交契约；`ShortDramaQueueCoordinatorTest`（5 项）与 `MediaSourceFactoryTest`（5 项）已通过 Mobile Arm64 定向单测；`git diff --check`、guard `check`、Mobile Arm64 Java 编译通过。
- 未完成／风险：两端 `VideoActivity` 尚未接入协调器与独立解析；自然过渡后的历史、字幕、弹幕、外部播放状态事务尚未验证；受控 playlist preload 尚未接通；尚无 SiteViewModel 取消隔离测试、真实 Media3 fake timeline／bridge 测试、Leanback 编译或设备对照；尚未创建 E-SP8 实施 commit／最终 recovery tag。
- rollback anchor：`655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`；保持不改依赖／native／lock。
- **唯一下一步：在 Mobile 与 Leanback `VideoActivity` 接入默认关闭的短剧 Exo 队列事务；先实现当前项首帧后的下一项独立解析、普通 HLS／MP4 入队、AUTO transition 身份校验与一次性业务提交，失败即回退旧路径。**

## Recovery anchor（2026-09-11 20:14 Asia/Shanghai）

- 目标：完成 E-SP8 当前实现的复评、修复、验证，并在合并 `origin/beta` 最新代码后继续复评；实验仍默认关闭。
- lane/scope：`upstream` / `E-SP8`；当前 guard 仍绑定 `dev1@655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`，未提交改动仅在既定 E-SP8 scope 内。
- 远端基线核对：已 fetch `origin/beta`，当前头为 `be1b02e06b22a4fa2f08c791555536e3e6154c95`；尚未把该头写入本分支，避免在 E-SP8 guard 未关闭时污染其封闭 scope。
- 复评发现及修复：自然媒体项过渡时，原实现先由 `PlayerManager.commitPlaylistTransition()` 替换当前 `PlaySpec`，再发送上一集停止事件，可能使停止记录读取下一集地址/位置；两端现已在 commit 前发送停止事件，并移除 commit 后的重复发送。
- 已完成验证：`git diff --check` 通过；Mobile Arm64 定向 `ShortDramaQueueCoordinatorTest`、`MediaSourceFactoryTest`、`MediaSourceFactoryIsolationTest` 通过（2026-09-11 20:12，`BUILD SUCCESSFUL in 46s`）；Leanback Arm64 Java 编译通过（2026-09-11 20:14，`BUILD SUCCESSFUL in 40s`）。构建仅报告既有 CXX5202 32 位 native library warning。
- 尚未完成：E-SP8 实施 commit/tag 尚未生成；尚未在合并 beta 后复评 beta 新增提交与冲突组合结果；尚无真实设备连续切集／网络争用／字幕与弹幕时序证据。
- rollback anchor：`655bf1a61e752dd5c6ccbbc3e92f92e2c6a35c02`；已另存当前 E-SP8 工作树临时补丁于 `/tmp/webhtv-esp8-before-beta-20260911/`，不纳入仓库。
- **唯一下一步：用当前 guard finish 提交并标记已验证的 E-SP8 原子改动；随后以新的 beta-sync guard 合并 `origin/beta`，评审合并差异及已提交未推送提交。**

## Recovery anchor（2026-09-11 23:22 Asia/Shanghai）

- E-SP8 原子提交已完成：`2b22c5240d52a8c2054299326f44fee6743ab26f`，recovery tag 为 `recovery/E-SP8/20260911201514-2b22c5240d52`。
- 当前合并任务为 `beta-sync-review-dev1-20260911`（`standard`），在 `dev1@2b22c5240d52a8c2054299326f44fee6743ab26f` 上无冲突合入 `origin/beta@be1b02e06b22a4fa2f08c791555536e3e6154c95`；冲突组合保留 beta 的 EXO 上游恢复/速度选择/HLS 分类改动，并保留 E-SP8 的媒体 ID、独立取址、header-cache namespace 与队列事务。
- 合并后定向验证：Mobile/Leanback Arm64 Java 编译及播放器/EXO/UI-player Mobile 单测（1263 项）`BUILD SUCCESSFUL in 50s`；Mobile Arm64 Debug APK 组装 `BUILD SUCCESSFUL in 54s`；`git diff --cached --check` 与 task guard check 通过。
- 设备启动证据：V1923A（API 28）安装并启动 Mobile Debug APK 成功，首页与短剧内容卡片可见、无应用 `FATAL EXCEPTION`。详情页尝试随后被外部 `pid 5484` 执行 force-stop 中断；没有可重复的短剧站源/自然连续切集链路，因此不将该运行记录提升为播放优化或正式放量验收。
- 当前状态：**E-SP8 代码实现和合并后源码复评通过；实验默认策略及正式放量前的连续切集、网络争用、字幕/弹幕和 Leanback 实机验收仍保持未完成。**
- 回滚：代码层面可使用已有 E-SP8 recovery tag；当前 beta 合并提交完成后另以本 task 的 recovery tag 回滚整合层。
- **唯一下一步：完成 `beta-sync-review-dev1-20260911` 的 merge commit/tag，推送 `dev1` 并创建目标为 `beta` 的中文 PR。**

## Recovery anchor（2026-09-12 01:00 Asia/Shanghai）

- 现场根因：短剧队列资格错误使用 `isDomainEnabled(EXO)`，这会在稳定策略默认关闭内部实验时直接拒绝队列，因此用户无法看到下一集预解析、播放列表追加和受控预载。
- 修复：新增生产自动优化动作 `EXO_SHORT_DRAMA_QUEUE`，Mobile 与 Leanback 改为通过 `isAllowed(EXO_SHORT_DRAMA_QUEUE)` 准入；安全条件、短剧源识别、Exo、预载设置、协议及恢复位置等既有门槛保持不变。
- 非目标保持不变：不会把多个剧集文件合并成一个文件；增强路径仍使用两个独立 MediaItem 的有界播放列表。
- 验证结果：`testMobileArm64_v8aDebugUnitTest` 中的 `PlaybackExperimentPolicyTest` 与 `ShortDramaQueueCoordinatorTest` 通过，Mobile 与 Leanback Arm64_v8a Debug Java 编译通过；Gradle 报告 `BUILD SUCCESSFUL in 20s`，仅有既有 CXX5202 与弃用 API 警告。
- 唯一下一步：执行 task guard 检查并提交本次准入修复；真实设备连续切集、网络争用、字幕与弹幕时序仍须后续验收。
