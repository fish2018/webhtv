# 个性推荐结果页缓存设计

> **状态：✅ 已实现并验证**
> **日期：2026-09-14**
> **范围：** 个性推荐 TMDB / 豆瓣结果页。智能 AI 已有缓存优先路径，猜你喜欢继续沿用 TMDB 底层缓存 + 异步细粒度刷新。

## 1. 背景

当前个性推荐 TMDB / 豆瓣首屏通过 `PersonalRecommendationService.loadPage()` 后台生成，但结果页本身没有持久缓存。再次进入详情页时，即使观看历史、反馈、当前片名和配置未变，也会重新匹配种子、请求推荐列表并补齐豆瓣评分，造成等待、接口压力和重复关键词查询。

相关证据：

- `PersonalRecommendationService.loadPage()` 首屏串行调用 `loadTmdbPage()` / `loadDoubanPage()`。
- `loadFromTmdb()` 与 `loadFromDouban()` 均按当前片名、历史种子和反馈重新计算。
- `RecommendationFeedbackStore.fingerprint()` 已能感知“不感兴趣”变化。
- `RecommendationPage` 已包含 `offset`、`nextOffset`、`hasMore`、`historyFingerprint`。
- 智能推荐 `AiRecommendationService.loadCached()/resolveCached()/refresh()` 已验证“缓存优先 + 后台刷新”范式。

## 2. 目标与非目标

### 目标

1. TMDB 和豆瓣个性推荐结果页支持先读取持久缓存。
2. 缓存键包含推荐来源、当前片名、历史种子指纹、反馈指纹、TMDB API 地址和语言。
3. 指纹一致时优先展示缓存；异步刷新得到等价或新结果后写回缓存并刷新 UI。
4. 指纹变化、功能关闭、配置不就绪、空页或无效序列化数据不使用陈旧缓存。
5. 失败时保留可用缓存兜底，但重新经过当前 blockedTitles 与反馈过滤。
6. 分页 `nextOffset` / `hasMore` 随缓存恢复，保证滚动懒加载继续可用。
7. TMDB / 豆瓣独立缓存，不互相阻塞。
8. TMDB 豆瓣评分富集异步进行，富集后更新内存页和缓存。

### 非目标

1. 不缓存“猜你喜欢”结果页；继续复用 TMDB 底层缓存。
2. 不改变现有推荐排序、去重、反馈、分页和焦点行为。
3. 不增加数据库表或第三方依赖。
4. 不在主线程执行磁盘 IO。
5. 不缓存原始 HTML/API payload；只缓存过滤后的 `TmdbItem` 结果页。

## 3. 缓存设计

### 文件与格式

- 目录：`Path.cache()/personal_rec`
- 文件：`<source>_<md5(cacheKey)>.json`
- payload：JSON 对象，`version=1`、`items`、`offset`、`nextOffset`、`hasMore`、`fingerprint`。
- TTL：7 天，TTL 内不因时间主动失效。
- 上限：单页最多 128 个 item；每次写入执行 LRU 清理，保留最新 32 个结果页。

### 缓存键

`source|currentTitle|historyFingerprint|feedbackFingerprint|apiBase|language`

- `currentTitle`：`currentTitle(currentVod, currentItem)`。
- `historyFingerprint`：`historySeedFingerprint(...)`。
- `feedbackFingerprint`：`RecommendationFeedbackStore.fingerprint()`。
- TMDB 和豆瓣分别使用各自历史种子，豆瓣额外保留当前片名作为首个 seed。
- fingerprint 存入 payload，读取时再校验 payload fingerprint 与当前 fingerprint 一致。

### 加载流程

1. `loadPage()` 进入 service 后台线程后读取独立缓存。
2. 缓存命中且 fingerprint / TTL / 结构有效时直接返回。
3. 缓存未命中、无效或指纹变化时调用现有 `loadFromTmdb()` / `loadFromDouban()`。
4. 非空有效结果写入缓存后返回。
5. 网络失败时回退现有网络失败行为；若已有旧缓存则在本次进入时仍可作为兜底。
6. `enrichTmdbPageRatingsAsync()` 富集后由 UI 层把富集页写回缓存。
7. “不感兴趣”立即从内存列表移除；下一次加载因 feedback fingerprint 变化重新生成，不返回已反馈作品。

### UI 流程

1. `TmdbUIAdapter.loadPersonalRecommendationsAsync()` 恢复现有异步调度。
2. 缓存页到达时先绑定；若随后刷新出相同 display list，不重复 notify。
3. 新结果到达后替换内存页，写回缓存。
4. Leanback 原生路径继续通过现有 `loadNativePersonalRecommendations()` 消费同一 service API。
5. Web/桌面通用路径通过现有 `VOD_PERSONAL` 事件细粒度刷新。

## 4. 方案比较

| 方案 | 结论 | 原因 |
|---|---|---|
| 不改动 | 不采用 | 每次重新匹配和请求，等待和接口压力没有解决 |
| 只缓存原始接口响应 | 不采用 | 会绕过当前 blockedTitles、历史种子合并和去重排序 |
| 缓存过滤后结果页（采用） | 采用 | 最小改动、可校验 fingerprint、保留分页与反馈行为 |

## 5. 验收标准

1. 新增单元测试覆盖缓存命中、TTL 过期、结构损坏、fingerprint 变化、空页不写缓存和写回富集页。
2. 现有 `PersonalRecommendationServiceTest` 全部通过。
3. `:app:compileDebugJavaWithJavac` 通过。
4. `task_guard.sh check` 通过。
5. 不修改“猜你喜欢”路径和智能 AI 缓存路径。

## 6. 实施步骤

1. 新增 `PersonalRecommendationCache`：Gson 序列化、TTL、结构校验、fingerprint 校验、LRU cleanup。
2. `PersonalRecommendationService` 新增 `loadCachedPage()` / `refreshPage()` / `writeCachedPage()`。
3. `loadPage()` 改为 cache-first，只影响首屏。
4. `TmdbUIAdapter.loadPersonalRecommendationsAsync()` 使用 cache-first service API。
5. 富集回调写回对应来源缓存。
6. 补充聚焦单元测试，并在同一测试任务内编译通过。

## 7. 风险与回滚

**风险：**

1. 错误复用旧结果：由当前 fingerprint、TTL、结构校验和当前 blockedTitles/feedback 过滤降低。
2. 磁盘失败：缓存操作失败不影响正常加载。

**回滚锚点：** 任务守卫 base HEAD `55006efe62961a0bdd6e62686611adc9c560b09b`。

## 8. 状态记录

- 2026-09-14：完成代码链路证据审查并建立设计文档。
- 2026-09-14：完成 cache-first 实现。验证期间修复了 fingerprint 辅助方法、`RecommendationPage` 类型、multi-catch、refresh/load-more 回调参数与测试构造类型问题。
- 2026-09-14：`:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.service.PersonalRecommendationServiceTest'` 与 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 通过。
