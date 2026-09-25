# TMDB 详情与爬虫详情并行预取设计（方案 A）

## 1. 状态

- 状态：Implemented / Verified
- 范围：仅处理进入详情时已经携带明确 `TmdbItem` 的场景
- 客户端：mobile、leanback
- 用户体验：保持现有“爬虫详情与 TMDB 核心数据都就绪后一次性揭开”行为

## 2. 背景与问题

当前 `VideoActivity` 先调用 `SiteViewModel.detailContent(key, id)` 获取源站/爬虫详情；只有爬虫返回真实 `Vod` 并进入 `setDetail(Vod)` 后，才调用 `TmdbUIAdapter.load(TmdbItem, Vod)` 获取 TMDB 详情。

当前关键路径为：

```text
T总耗时 = T爬虫详情 + TTMDB核心详情 + T绑定/图片
```

当 Intent 已携带明确 `TmdbItem`（TMDB ID 与媒体类型已知）时，TMDB 核心详情不依赖爬虫匹配结果，网络请求可以与爬虫详情并行。

## 3. 目标

1. 在明确 `TmdbItem` 场景下，让 TMDB 核心详情请求与爬虫详情请求并行开始。
2. 爬虫返回前不创建或发布临时 `Vod`。
3. 爬虫返回后仍以真实 `Vod` 为唯一播放数据主体，沿用现有 enrichment、集数元数据绑定和 `RefreshEvent.VOD` 流程。
4. 保持 mobile / leanback 当前 loading 和一次性揭开行为不变。
5. 同一详情请求只发送一次 TMDB 核心详情请求，不因 `setDetail(Vod)` 再次调用 `load()` 而重复请求。
6. 页面切换、重新加载或条目不匹配时，不得将旧预取结果应用到当前页面。

## 4. 非目标

本阶段不处理以下内容：

- 普通源站条目的推测式 TMDB 自动匹配并行化；
- 爬虫返回后先展示原生详情、TMDB 后增量更新的渐进式 UI；
- 推荐、AI 推荐、季集信息等启动后台任务的提前执行；
- 修改线程池大小或引入新依赖；
- 修改 TMDB 匹配策略。

## 5. 现有架构约束

### 5.1 真实 `Vod` 必须来自爬虫

`Vod.flags`、`Episode`、播放地址、请求头及站源身份由爬虫详情提供。TMDB 只能增强这些数据，不能替换它们。

### 5.2 `TmdbUIAdapter.loadDetailSync()` 兼有获取与提交职责

现有方法会：

1. 请求/解析 TMDB detail 与 cast；
2. 设置 adapter 已加载状态；
3. enrichment 真实 `Vod`；
4. 发布 `RefreshEvent.VOD`；
5. 延迟启动推荐等后台任务。

并行化需要把步骤 1 的网络结果提前准备，但步骤 2—5 必须等真实 `Vod` 到达后执行。

### 5.3 UI 保持单次揭开

mobile 与 leanback 都会在 TMDB 模式下保持 loading。预取只缩短等待时间，不改变揭开时机。

## 6. 方案概览

在 `TmdbUIAdapter` 内新增一个仅属于当前 adapter 的“直接条目详情预取槽位”。

```text
VideoActivity.getDetail()
  ├─ TmdbUIAdapter.prefetch(intentTmdbItem)
  │    └─ Future<PrefetchResult(detail, cast, normalizedItem)>
  └─ SiteViewModel.detailContent(key, id)
       └─ crawler Vod
             └─ TmdbUIAdapter.load(intentTmdbItem, crawlerVod)
                   ├─ 接管匹配的预取 future
                   ├─ 等待/消费预取结果
                   └─ 执行原有 loadDetailSync 的提交、enrichment 与事件流程
```

### 6.1 预取内容

预取阶段只执行 TMDB 核心数据加载：

- `tmdbService.detail(item, config, false)`；
- `normalizeLoadedItem(item, detail)`；
- `tmdbService.cast(detail, config)`。

预取阶段不执行：

- `enrichVod()`；
- `notifyVodChanged()`；
- `saveMatch()`；
- 推荐和 AI 推荐加载；
- 集数元数据绑定；
- UI 操作。

### 6.2 预取结果

新增 adapter 内部不可变结果：

```java
private static final class DetailPrefetchResult {
    private final TmdbItem item;
    private final JsonObject detail;
    private final List<TmdbPerson> cast;
}
```

结果只在 `TmdbUIAdapter` 内部传递，不新增跨模块公共 DTO。

### 6.3 预取身份

预取是否可复用由以下稳定字段判断：

```text
normalize(mediaType) + tmdbId
```

其中 `mediaType` 会执行 trim 和小写规范化，与 `TmdbDetailCache` 保持一致。标题不作为主键，避免语言或详情规范化造成误判。

当以下任一字段不一致时，`load()` 不消费预取结果，按原流程直接加载：

- TMDB ID；
- media type。

### 6.4 并发模型

使用项目已有 `Task.executor()` / Guava `ListenableFuture`，不新增线程池。

`TmdbUIAdapter` 保存：

- 当前预取条目身份；
- 当前预取 future；
- 预取 generation/token。

行为：

1. `prefetch(item)` 对同一条目幂等；已有未完成或已完成 future 时直接复用。
2. 新条目调用 `prefetch()` 时取消并替换旧 future。
3. `load(item, vod)` 消费匹配的 future，并清空槽位，防止重复消费。
4. future 成功后，用当前 `loadGeneration` 调用原有提交路径。
5. future 失败或取消时，若当前页面 generation 仍有效，则回退到现有 TMDB 直接加载。
6. 旧页面结果即使完成，也必须被预取 token 和 `loadGeneration` 双重拦截。

## 7. API 设计

### 7.1 新增 API

```java
public void beginDetailRequest()
public void prefetch(TmdbItem item)
public void release()
```

`beginDetailRequest()` 在每次爬虫详情请求开始前推进 load generation，并取消尚未接管的旧预取，使已经接管旧 future 的回调也会因 generation 不匹配而失效。`release()` 在 Activity 销毁时执行相同失效与取消动作，避免未使用的预取继续持有 adapter/Activity 生命周期。

约束：

- `item == null` 或 TMDB 配置未就绪时无操作；
- 只预取明确条目，不触发自动匹配；
- 不发送事件；
- 不修改 `Vod`。

可选生命周期 API：

```java
public void cancelPrefetch()
```

用于 Activity 销毁或明确重置详情时取消未消费和已被 `load()` 接管但仍在运行的 future，并使旧结果失效。测试必须证明取消后的结果不会被应用。

### 7.2 现有 API 语义保持

以下 API 签名不变：

```java
load(TmdbItem item, Vod vod)
load(TmdbItem item, Vod vod, TmdbDetailCache.Entry cached)
autoMatch(String videoName, Vod vod)
```

优先级：

```text
显式传入的 TmdbDetailCache.Entry
  > 匹配的 adapter 预取 future
  > 现有 service 请求
```

显式缓存代表调用方已经指定可复用结果，不能被预取覆盖。

## 8. Activity 接入

mobile 与 leanback 的 `getDetail()` 在发起爬虫详情前执行：

```java
private void prefetchDirectTmdbDetail() {
    if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return;
    mTmdbUIAdapter.beginDetailRequest();
    TmdbItem item = getTmdbItem();
    if (item != null) mTmdbUIAdapter.prefetch(item);
}
```

调用顺序：

```java
prefetchDirectTmdbDetail();
mViewModel.detailContent(getKey(), getId());
```

`setDetail(Vod)` 保留现有 `load(tmdbItem, item)` 调用，由 adapter 自动接管预取结果。普通自动匹配路径保持不变。

## 9. 状态与时序

### 9.1 TMDB 先完成

```text
TMDB future 完成并留在 adapter
→ 爬虫返回 Vod
→ load() 消费完成结果
→ enrichment / RefreshEvent / UI 揭开
```

### 9.2 爬虫先完成

```text
爬虫返回 Vod
→ load() 接管仍在运行的 future
→ 保持现有 loading
→ future 完成
→ enrichment / RefreshEvent / UI 揭开
```

### 9.3 TMDB 失败

```text
预取失败
→ 若真实 Vod 已到且 generation 有效，回退现有 service load
→ 回退也失败时沿用 notifyLoadComplete()，让原生内容继续
```

为避免失败导致双倍超时，实施时应区分：

- 明确网络/服务失败：直接走现有失败完成逻辑，默认不立即重复同一请求；
- 取消、身份不匹配或预取未被消费：允许正常 load。

最终实现以“不因相同请求失败立即重复请求”为默认策略。

### 9.4 页面切换

```text
旧预取 A 运行
→ 页面切换并预取 B
→ A token 失效/任务取消
→ A 完成时不得覆盖 B 或发送 VOD 事件
```

## 10. 错误处理与降级

- 未配置 TMDB：完全保持爬虫原流程。
- 未携带 `TmdbItem`：保持现有 `autoMatch()` 串行流程。
- 预取结果身份不匹配：丢弃预取，执行现有 `load()`。
- 预取失败：沿用现有 TMDB 失败日志与原生详情降级。
- 爬虫失败：预取结果不发布，不构造临时 Vod；页面沿用现有爬虫失败处理。

## 11. 可观测性

沿用 `SpiderDebug`，新增以下日志节点：

```text
tmdb-prefetch start media=%s id=%d
tmdb-prefetch reuse media=%s id=%d
tmdb-prefetch finish cost=%dms media=%s id=%d
tmdb-prefetch attach state=running|done media=%s id=%d
tmdb-prefetch discard reason=stale|mismatch|cancelled
tmdb-prefetch failed cost=%dms error=%s
```

现有 `video-flow detail start/finish` 和 `tmdb detail core loaded` 日志继续保留，可用于计算串行与并行后的关键路径差异。

## 12. 测试策略

### 12.1 共享层单元测试

验证：

1. 相同 `TmdbItem` 的 `prefetch()` 幂等，不创建第二个请求；
2. `load()` 能消费已完成预取结果；
3. `load()` 能接管尚未完成的预取 future；
4. 预取阶段不修改 Vod、不发送 VOD 事件；
5. 不同 TMDB ID/media type 不复用结果；
6. 新预取使旧 token 失效；
7. 消费预取后不再次调用 TMDB detail；
8. 显式 `TmdbDetailCache.Entry` 优先于预取。

如果当前 Android/JVM 测试难以替换 `TmdbService`，先抽取最小、包内可见的预取状态协调器并对协调器做纯 JVM 测试，不引入 mocking 依赖。

### 12.2 Activity 结构测试

现有项目使用源码结构测试覆盖 mobile / leanback 的关键调用顺序。新增断言：

- 两端 `getDetail()` 均先调用 `prefetchDirectTmdbDetail()`，再调用 `detailContent()`；
- 只有明确 `TmdbItem` 才调用 `prefetch()`；
- `setDetail(Vod)` 仍调用原有 `load()`，没有临时 Vod 分支；
- loading / reveal 相关代码未改变。

### 12.3 回归验证

至少运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

若 flavor 任务独立，则运行仓库实际存在的 mobile / leanback 单元测试任务；最后至少编译两个 flavor 的 debug 变体。

## 13. 分阶段实施

### Phase 1：文档和红灯测试

- 落地本设计文档；
- 增加预取协调行为测试；
- 增加两端调用顺序结构测试；
- 确认测试在实现前失败。

### Phase 2：共享层预取

- 增加内部预取结果与状态；
- 实现 `prefetch(TmdbItem)`；
- 修改 `load()` 消费 future；
- 保持显式缓存优先级；
- 通过共享层测试。

### Phase 3：两端接入

- leanback 在爬虫请求前启动明确条目预取；
- mobile 同步接入；
- 保持自动匹配路径与 UI 揭开策略不变；
- 通过结构测试。

### Phase 4：回归与测量

- 运行定向测试及两端编译；
- 检查日志顺序确认为并行；
- 对比 `detail start` 到内容揭开的耗时；
- 进行代码审查，检查竞态、生命周期和重复请求。

## 14. 验收标准

1. 明确 `TmdbItem` 场景中，TMDB prefetch 日志出现在爬虫 `detail finish` 之前或与其并发。
2. 相同详情进入流程只发生一次 TMDB 核心 detail 请求。
3. 爬虫真实 `Vod.flags` 与播放线路不被预取对象替换。
4. TMDB 数据仍能增强标题、简介、演员、年份与集数元数据。
5. mobile / leanback 保持现有 loading 与一次性揭开行为。
6. 快速切换详情时旧预取结果不会更新新页面。
7. 未携带 `TmdbItem` 的普通详情行为不变。
8. TMDB 失败或未配置时仍能按现有逻辑显示/降级到源站详情。
9. 定向单元测试和两个 flavor 的编译通过。

## 15. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 预取失败后重复请求导致更慢 | 同一已消费预取失败默认不立即重试 |
| Activity 切换导致旧结果串页 | 预取 token + loadGeneration 双重校验 |
| 预取 future 占用线程等待 | 使用 callback 接管，不在固定线程池中阻塞等待 |
| 显式内存缓存与预取竞争 | 明确缓存优先级高于 adapter 预取 |
| `TmdbItem` 被详情规范化时身份变化 | 使用规范化后的 mediaType + tmdbId 判断身份 |
| 页面切换时旧 VOD 事件串入 | 新 Intent 立即推进 adapter generation；mobile / leanback 统一校验 siteKey/id |
| future 已被 load 接管后无法取消 | adapter 保存 active future，beginDetailRequest/release 统一取消 |

## 16. 决策

采用 adapter 内部、单槽位、明确条目专用的异步预取方案。它是对现有 `TmdbUIAdapter.load()` 的增量扩展，不新增依赖，不改变公开页面模式，不引入临时 Vod，也不扩大到自动匹配场景。


## 17. 实施结果

已按本设计完成：

- 新增 `TmdbDetailPrefetch`，以规范化后的 `mediaType + tmdbId` 管理单槽位 `ListenableFuture`；
- `TmdbUIAdapter.prefetch()` 只加载并保存 detail、规范化条目和 cast；
- `TmdbUIAdapter.load()` 在真实爬虫 `Vod` 到达后消费匹配的预取结果；
- 显式 `TmdbDetailCache.Entry` 保持最高优先级；
- `beginDetailRequest()` 在新详情开始时使旧 load generation 和旧预取立即失效；
- `release()` 在 mobile / leanback Activity 销毁时取消 pending/active future 并使旧回调失效；
- mobile / leanback 均在 `detailContent()` 前启动明确 `TmdbItem` 预取；
- 新 Intent 以 `siteKey + vodId` 判断详情身份，即使命中 `VodDetailCache` 也会先使旧 TMDB generation 失效；
- 两端 VOD 更新均通过共享 `VodEventGuard` 校验站点和条目身份；
- 预取日志区分 start/reuse/replace/cancelled/failed；
- 普通 `autoMatch()` 路径和现有一次性揭开 UI 未改变。
- 新增 `VodEventGuardTest`、mediaType 规范化测试和 active future 取消测试。

### 验证记录

以下命令已于 2026-07-20 执行成功：

```powershell
.\gradlew.bat `
  :app:testLeanbackArm64_v8aDebugUnitTest `
  :app:testMobileArm64_v8aDebugUnitTest `
  :app:assembleLeanbackArm64_v8aDebug `
  :app:assembleMobileArm64_v8aDebug `
  --console=plain
```

结果：`BUILD SUCCESSFUL`。Gradle 仅报告仓库既有的 deprecated API、unchecked operation 与 Gradle 10 兼容性警告，本次变更未新增编译错误或测试失败。
