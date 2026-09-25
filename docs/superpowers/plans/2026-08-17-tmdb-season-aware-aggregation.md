# TMDB 季度感知聚合实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在共用 TMDB 节目详情页的前提下，让电视剧历史、续播、线路绑定和删除都按已确认季度隔离，并对季度不明的数据保持来源级隔离。

**Architecture:** 以 `mediaType + tmdbId` 作为节目详情身份，以 `mediaType + tmdbId + seasonNumber` 作为电视剧历史和进度身份。来源绑定下沉到 `siteKey + vodId + flagKey`，一个线路可以绑定一个季度或经过验证的多季度切片；无法唯一确认时使用 `Unknown`，不参与跨来源自动续播。现有 `History` 保留为来源路由记录，新增季度进度表保存同一跨季来源的独立最近位置。

**Tech Stack:** Android Java、Room、现有 `TmdbSeasonResolver`/`EpisodeSeasonPolicy`、Gson 设置缓存、JUnit/Gradle unit test。

---

## 文件边界

### 新建文件

- `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonScope.java`：季度范围的 `KNOWN`、`MULTI`、`UNKNOWN` 值对象，以及 `accepts`、缓存键和展示键所需的纯方法。
- `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonProgress.java`：季度级最近剧集和播放位置的 Room entity。
- `app/src/main/java/com/fongmi/android/tv/db/dao/TmdbSeasonProgressDao.java`：季度进度查询、替换、删除接口。
- `app/src/main/java/com/fongmi/android/tv/playback/TmdbSeasonProgressStore.java`：在 `History` 写入/删除后同步季度进度，并按季度选择续播来源。
- `app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonScopeTest.java`：季度范围、UNKNOWN 隔离和多季度接受规则的纯单元测试。
- `app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonProgressStoreTest.java`：季度进度选取、来源优先级和迁移 SQL 契约测试。

### 修改文件

- `app/src/main/java/com/fongmi/android/tv/history/HistoryDisplayPolicy.java`：已知季度分组、未知季度来源键和展示排序。
- `app/src/main/java/com/fongmi/android/tv/bean/History.java`：按季度读取续播、写入/重建季度快照、按季度删除。
- `app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java`：自动换源前增加季度兼容性过滤。
- `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCache.java`：缓存键从 Vod 粒度扩展到 Flag 粒度，兼容旧键读取。
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolver.java`：把解析结果转换为 `TmdbSeasonScope`，保留现有证据优先级和无猜测原则。
- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`：保存来源标题、逐线路解析季度、生成多季度线路分段并暴露当前季度线路。
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`：详情页按当前季度切换剧集、线路和续播；季度历史删除使用季度身份。
- `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`：注册新 entity/DAO，将数据库版本从 41 升至 42。
- `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`：新增 `MIGRATION_41_42`。
- `app/src/main/java/com/fongmi/android/tv/bean/Backup.java`：完整备份、历史同步和恢复季度进度。
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`：API、远端同步和用户删除路径同步季度快照。
- `app/src/test/java/com/fongmi/android/tv/history/HistoryDisplayPolicyTest.java`：补充 UNKNOWN 和季度 0 边界。
- `app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java`：补充季度换源和删除边界的源码契约测试。
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolverTest.java`：补充跨季度合集唯一切片和歧义降级。
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java`：补充来源标题、Flag 绑定和线路投影。
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeWiringTest.java`：补充详情页季度切换与线路过滤契约。

现有工作区中的五个未提交文件必须作为用户基线保留：`HistoryDisplayPolicy.java`、`TmdbDetailActivity.java`、`TmdbUIAdapter.java` 及其两份测试。每次提交只暂存当前任务明确修改的路径。

### Task 1: 实施前清理与基线确认

**Files:**
- Modify: none
- Test: none

- [ ] **Step 1: 记录完整工作区状态**

Run:

```powershell
rtk git status --short --untracked-files=all
rtk git diff --stat
```

Expected: 能看到现有五个修改文件；任何新增临时目录都必须单独确认，不把它们与用户代码混淆。

- [ ] **Step 2: 只删除已确认的工具临时目录**

若状态明确显示 `?? graphify-out/`，先执行下面的路径校验，再删除：

```powershell
rtk proxy powershell -NoProfile -Command '$root = [IO.Path]::GetFullPath((Get-Location).Path); $candidate = [IO.Path]::GetFullPath((Join-Path $root ''graphify-out'')); $prefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar; if ((Test-Path -LiteralPath $candidate) -and ($candidate -ne $root) -and $candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { Remove-Item -LiteralPath $candidate -Recurse -Force }'
```

不要删除 `build`、Gradle 缓存、依赖目录或任何已跟踪文件；这些目录通过检索排除而不是批量清除。

- [ ] **Step 3: 清理后重新确认基线**

Run:

```powershell
rtk git status --short --untracked-files=all
```

Expected: 用户已有的五个修改仍在，临时目录不再参与后续检索。

### Task 2: 先锁定季度身份和历史投影行为

**Files:**
- Create: `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonScope.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/history/HistoryDisplayPolicy.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonScopeTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/HistoryDisplayPolicyTest.java`

- [ ] **Step 1: 写失败测试，定义季度范围契约**

在 `TmdbSeasonScopeTest` 增加以下纯测试：

```java
@Test
public void unknownDoesNotAcceptAnySeason() {
    TmdbSeasonScope scope = TmdbSeasonScope.unknown();
    assertFalse(scope.accepts(1));
    assertFalse(scope.accepts(0));
}

@Test
public void multiAcceptsOnlyCoveredSeasons() {
    TmdbSeasonScope scope = TmdbSeasonScope.multi(List.of(1, 2));
    assertTrue(scope.accepts(1));
    assertTrue(scope.accepts(2));
    assertFalse(scope.accepts(3));
}

@Test
public void specialSeasonZeroIsKnownOnlyWhenExplicitlyCreated() {
    assertEquals(TmdbSeasonScope.Kind.UNKNOWN, TmdbSeasonScope.unknown().getKind());
    assertEquals(Integer.valueOf(0), TmdbSeasonScope.known(0).getSeasonNumber());
}
```

在 `HistoryDisplayPolicyTest` 增加一个同节目、季度字段均为默认未知但来源键不同的场景：

```java
@Test
public void unknownTvHistoriesRemainSourceIsolated() {
    History first = history("tv", 88, 100, "site-a@@@vod-a");
    History second = history("tv", 88, 200, "site-b@@@vod-b");
    List<History> result = HistoryDisplayPolicy.project(List.of(first, second), true);
    assertEquals(2, result.size());
}
```

保留当前已有的第一季/第三季和明确来源标题测试，不重写用户已添加的断言。

- [ ] **Step 2: 运行测试确认当前实现暴露缺口**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.TmdbSeasonScopeTest" --tests "com.fongmi.android.tv.history.HistoryDisplayPolicyTest"
```

Expected: 新增 `TmdbSeasonScopeTest` 因类不存在而失败，UNKNOWN 投影测试在当前只按 TMDB 身份聚合时失败；这确认测试确实锁定了目标行为。

- [ ] **Step 3: 实现最小季度范围值对象**

实现以下公开契约，不把 `0` 当作未知：

```java
public final class TmdbSeasonScope {
    public enum Kind { KNOWN, MULTI, UNKNOWN }

    private final Kind kind;
    private final Integer seasonNumber;
    private final List<Integer> seasons;

    public static TmdbSeasonScope known(int seasonNumber) {
        if (seasonNumber < 0) return unknown();
        return new TmdbSeasonScope(Kind.KNOWN, seasonNumber, List.of(seasonNumber));
    }

    public static TmdbSeasonScope multi(List<Integer> seasons) {
        LinkedHashSet<Integer> distinct = new LinkedHashSet<>();
        if (seasons != null) for (Integer season : seasons) if (season != null && season >= 0) distinct.add(season);
        if (distinct.size() < 2) return unknown();
        return new TmdbSeasonScope(Kind.MULTI, null, List.copyOf(distinct));
    }

    public static TmdbSeasonScope unknown() {
        return new TmdbSeasonScope(Kind.UNKNOWN, null, List.of());
    }

    public Kind getKind() { return kind; }
    public Integer getSeasonNumber() { return seasonNumber; }
    public List<Integer> getSeasons() { return seasons; }
    public boolean accepts(int seasonNumber) { return seasons.contains(seasonNumber); }
    public boolean isKnown() { return kind == Kind.KNOWN || kind == Kind.MULTI; }
}
```

`known` 只接受 `seasonNumber >= 0`，`multi` 去重并保留至少两个非负季度；非法输入统一返回 `unknown`，避免产生空的多季度绑定。

- [ ] **Step 4: 修改历史投影键**

在 `HistoryDisplayPolicy` 抽出季度判定方法，并将 `tmdbIdentity` 固定为：

```java
String base = mediaType + ":" + item.getTmdbId();
if (!"tv".equals(mediaType)) return base;
int season = item.getTmdbSeasonNumber();
boolean known = season > 0 || (season == 0 && item.getTmdbEpisodeNumber() > 0);
if (known) return base + ":season:" + season;
return item.getKey() == null || item.getKey().isEmpty() ? "" : "source:" + item.getKey();
```

保留当前已提交前工作区中的已知季度键格式，避免用户已有测试和历史展示发生无意义变化。未知电视剧不得回退到节目级 TMDB 键。

- [ ] **Step 5: 运行测试并提交阶段一**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.TmdbSeasonScopeTest" --tests "com.fongmi.android.tv.history.HistoryDisplayPolicyTest"
```

Expected: 两个测试类全部通过，已知季度与未知来源均不再错误合并。

Commit only the changed implementation/test paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonScope.java app/src/main/java/com/fongmi/android/tv/history/HistoryDisplayPolicy.java app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonScopeTest.java app/src/test/java/com/fongmi/android/tv/history/HistoryDisplayPolicyTest.java
rtk git commit -m "fix: 按季度隔离 TMDB 历史投影"
```

### Task 3: 将季度过滤贯穿续播、自动换源和删除

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/bean/History.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/HistorySourceResolverTest.java`

- [ ] **Step 1: 写续播和换源失败测试**

在 `HistorySourceResolverTest` 增加纯评分测试，验证同 TMDB 节目不同季度被拒绝、未知季度不自动换源：

```java
@Test
public void candidateFromAnotherKnownSeasonIsRejected() {
    History saved = historyWithTmdb("tv", 88, 1, 5, "saved");
    Vod candidate = vod("乐高幻影忍者：神龙崛起第三季");
    TmdbItem tmdb = tmdb("tv", 88);
    candidate.setRemarks("第三季");
    assertFalse(HistorySourceResolver.canAutoReuseSeason(saved, 3, "candidate"));
    assertEquals(HistorySourceResolver.REJECTED,
            HistorySourceResolver.scoreCandidate(saved, candidate, tmdb));
}

@Test
public void unknownSeasonDoesNotCrossSourceAutomatically() {
    History saved = historyWithTmdb("tv", 88, -1, 5, "source-a@@@vod-a");
    assertFalse(HistorySourceResolver.canAutoReuseSeason(saved, -1, "source-b@@@vod-b"));
}
```

如果现有测试夹具没有 `Vod` 或 `TmdbItem` 工厂，直接在该测试类中使用已有构造器和 setter，保持测试只依赖纯对象，不访问 Room。

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.HistorySourceResolverTest" --tests "com.fongmi.android.tv.history.GlobalHistoryResumeSourceTest"
```

Expected: 新增的季度兼容入口尚不存在，或同节目不同季度仍能进入评分候选。

- [ ] **Step 3: 集中实现季度兼容判断**

在 `HistorySourceResolver` 增加包可见纯方法，所有候选在标题/年份评分之前调用：

```java
static boolean canAutoReuseSeason(History history, int candidateSeason, String candidateKey) {
    int savedSeason = history == null ? -1 : history.getTmdbSeasonNumber();
    boolean savedKnown = savedSeason > 0
            || savedSeason == 0 && history.getTmdbEpisodeNumber() > 0;
    boolean candidateKnown = candidateSeason >= 0;
    if (!savedKnown || !candidateKnown) return TextUtils.equals(history.getKey(), candidateKey);
    return savedSeason == candidateSeason;
}
```

实际候选的 `candidateSeason` 从 `TmdbSeasonScope` 得到：`KNOWN` 返回一个季度，`MULTI` 只在 `accepts(savedSeason)` 时通过，`UNKNOWN` 返回 `-1`。`scoreCandidate` 在 `hasIdentityConflict` 之后、标题评分之前调用该判断，拒绝跨季候选。

- [ ] **Step 4: 保持 History 读取入口的 expectedSeason 语义**

在 `History.findPlaybackByTmdb` 和 `findPlaybackCandidate` 中统一使用 `TmdbSeasonScope` 的已知季度判定。电影维持节目级跨源续播；电视剧未提供季度时只接受当前来源：

```java
if (!"tv".equalsIgnoreCase(item.getMediaType())) return true;
if (expectedSeason < 0) return TextUtils.equals(requestedKey, item.getKey());
if (!savedHasKnownSeason) return TextUtils.equals(requestedKey, item.getKey());
return TmdbSeasonScope.known(savedSeason).accepts(expectedSeason);
```

不要把 `expectedSeason == -1` 解释成第一季；未传季度时只允许当前来源历史或调用方显式提供同季度范围。

- [ ] **Step 5: 验证季度删除边界**

保留 `History.deleteRelated` 中通过 `HistoryDisplayPolicy.tmdbIdentity(item)` 比较完整季度键的逻辑，并补充源码契约断言：

```java
assertTrue(method.contains("identity.equals(HistoryDisplayPolicy.tmdbIdentity(item))"));
assertTrue(method.contains("identity.contains(\":season:\")"));
```

节目级“全部删除”仍允许按 TMDB 身份处理，但季度卡片删除必须只传入当前季度投影。未知季度只能删除自己的来源键。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.HistorySourceResolverTest" --tests "com.fongmi.android.tv.history.GlobalHistoryResumeSourceTest"
```

Expected: 同季度或覆盖目标季度的线路可参与候选，不同季度和未知跨源候选被拒绝。

Commit only the task paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/bean/History.java app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java app/src/test/java/com/fongmi/android/tv/history/HistorySourceResolverTest.java
rtk git commit -m "fix: 隔离 TMDB 跨季续播和换源"
```

### Task 4: 将手动季度绑定下沉到 Flag

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCache.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolver.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolverTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java`

- [ ] **Step 1: 写缓存粒度失败测试**

在 `TmdbUIAdapterTest` 增加两个线路使用同一 Vod、不同 Flag 的测试：

```java
@Test
public void seasonBindingKeyIncludesFlag() {
    TmdbSeasonMatchCache cache = new TmdbSeasonMatchCache();
    cache.put("site", "vod", "source", "flag-s1", 88, "tv", 1,
            TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp1", 12, 12);
    cache.put("site", "vod", "source", "flag-s2", 88, "tv", 2,
            TmdbSeasonMatchCache.Mode.MANUAL_SEASON, "fp2", 12, 12);
    assertEquals(Integer.valueOf(1), cache.find("site", "vod", "source", "flag-s1", 88).getSeasonNumber());
    assertEquals(Integer.valueOf(2), cache.find("site", "vod", "source", "flag-s2", 88).getSeasonNumber());
}
```

在 `TmdbSeasonResolverTest` 增加“第一、二季合集完整切片返回 `MULTI_SLICE`，缺一集返回 `AMBIGUOUS`”测试。

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.TmdbSeasonResolverTest" --tests "com.fongmi.android.tv.ui.helper.TmdbUIAdapterTest"
```

Expected: 当前缓存只有 `siteKey + vodId + sourceTitle` 键，第二条 Flag 会覆盖第一条。

- [ ] **Step 3: 扩展缓存 API 并保留旧数据读取**

在 `TmdbSeasonMatchCache` 中新增带 `flagKey` 的重载，旧 API 委托到空 `flagKey`：

```java
public Entry find(String siteKey, String vodId, String sourceTitle, int tmdbId) {
    return find(siteKey, vodId, sourceTitle, "", tmdbId);
}

public Entry find(String siteKey, String vodId, String sourceTitle, String flagKey, int tmdbId) {
    Entry entry = getItems().get(key(siteKey, vodId, sourceTitle, flagKey));
    if (entry == null && TextUtils.isEmpty(flagKey)) {
        entry = getItems().get(key(siteKey, vodId, sourceTitle, ""));
    }
    return entry != null && entry.matches(tmdbId) ? entry : null;
}
```

`put`、`remove`、`removeIfMediaChanged` 使用相同键函数。旧的 Vod 级条目只在详情中没有可区分 Flag 或只有一个 Flag 时回退读取，不能复制到多个 Flag。

- [ ] **Step 4: 计算稳定 Flag 键并绑定每条线路**

在 `TmdbUIAdapter` 增加纯方法：

```java
static String flagKey(Flag flag, int index) {
    String value = flag == null ? "" : flag.getFlag();
    return normalize(value) + "#" + index;
}
```

`captureSourceSeason`、`resolveSeason`、`updateSeasonBinding` 和 stale binding 清理都遍历 `Vod.getFlags()`，传递该键；同一 Flag 内的多季度映射使用现有 `EpisodeSeasonPolicy` 重新验证集数和扁平集号。

- [ ] **Step 5: 将解析结果转为范围值对象**

在 `TmdbSeasonResolver.Resolution` 增加：

```java
public TmdbSeasonScope toScope() {
    if (status == Status.RESOLVED && selectedSeason != null) return TmdbSeasonScope.known(selectedSeason);
    if (status == Status.MULTI_SLICE) return TmdbSeasonScope.multi(availableSeasons);
    return TmdbSeasonScope.unknown();
}
```

`FLAT` 和 `AMBIGUOUS` 都必须返回 `UNKNOWN`，不能因为存在多个 TMDB 季度就默认全部归入合集。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.TmdbSeasonResolverTest" --tests "com.fongmi.android.tv.ui.helper.TmdbUIAdapterTest"
```

Expected: 同一 Vod 的不同 Flag 绑定互不覆盖；完整第一、二季合集得到 `MULTI`，不完整或冲突数据得到 `UNKNOWN`。

Commit only the task paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonMatchCache.java app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolver.java app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolverTest.java app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java
rtk git commit -m "feat: 按播放线路保存 TMDB 季度绑定"
```

### Task 5: 建立季度级进度存储和 Room 迁移

**Files:**
- Create: `app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonProgress.java`
- Create: `app/src/main/java/com/fongmi/android/tv/db/dao/TmdbSeasonProgressDao.java`
- Create: `app/src/main/java/com/fongmi/android/tv/playback/TmdbSeasonProgressStore.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/db/Migrations.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonProgressStoreTest.java`

- [ ] **Step 1: 写进度存储失败测试**

测试覆盖同一节目两季同一集号、跨季来源和未知季度：

```java
@Test
public void progressKeySeparatesSeasonOneAndTwo() {
    TmdbSeasonProgress first = TmdbSeasonProgress.of(1, "tv", 88, 1, 5, 1000, 5000, "a");
    TmdbSeasonProgress second = TmdbSeasonProgress.of(1, "tv", 88, 2, 5, 2000, 5000, "a");
    assertNotEquals(first.identityKey(), second.identityKey());
}

@Test
public void progressKeySeparatesConfigurations() {
    TmdbSeasonProgress first = TmdbSeasonProgress.of(1, "tv", 88, 1, 5, 1000, 5000, "a");
    TmdbSeasonProgress second = TmdbSeasonProgress.of(2, "tv", 88, 1, 5, 1000, 5000, "a");
    assertNotEquals(first.identityKey(), second.identityKey());
}

@Test
public void unknownHistoryDoesNotCreateCanonicalProgress() {
    History history = historyWithTmdb("tv", 88, -1, 5, "source-a@@@vod-a");
    assertFalse(TmdbSeasonProgressStore.isEligible(history));
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.TmdbSeasonProgressStoreTest"
```

Expected: 新 entity/store 尚不存在。

- [ ] **Step 3: 创建 Room entity 和 DAO**

`TmdbSeasonProgress` 使用带 `cid` 命名空间的复合主键，避免不同配置或不同季度互相覆盖。逻辑季度身份仍是 `mediaType + tmdbId + seasonNumber`，`cid` 只对应现有 History 的本地数据边界：

```java
@Entity(primaryKeys = {"cid", "mediaType", "tmdbId", "seasonNumber"})
public class TmdbSeasonProgress {
    public int cid;
    @NonNull public String mediaType = "";
    public int tmdbId;
    public int seasonNumber;
    public int episodeNumber;
    public long position;
    public long duration;
    public String sourceHistoryKey = "";
    public String sourceBindingKey = "";
    public long updatedAt;

    public static TmdbSeasonProgress of(int cid, String mediaType, int tmdbId, int seasonNumber,
                                        int episodeNumber, long position, long duration,
                                        String sourceHistoryKey) {
        TmdbSeasonProgress item = new TmdbSeasonProgress();
        item.cid = cid;
        item.mediaType = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        item.tmdbId = tmdbId;
        item.seasonNumber = seasonNumber;
        item.episodeNumber = episodeNumber;
        item.position = position;
        item.duration = duration;
        item.sourceHistoryKey = sourceHistoryKey == null ? "" : sourceHistoryKey;
        item.updatedAt = System.currentTimeMillis();
        return item;
    }

    public String identityKey() {
        return cid + ":" + mediaType + ":" + tmdbId + ":season:" + seasonNumber;
    }
}
```

DAO 必须提供以下接口：

```java
@Query("SELECT * FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId AND seasonNumber = :season")
public abstract TmdbSeasonProgress find(int cid, String mediaType, int tmdbId, int season);

@Query("SELECT * FROM TmdbSeasonProgress ORDER BY updatedAt DESC")
public abstract List<TmdbSeasonProgress> findAll();

@Insert(onConflict = OnConflictStrategy.REPLACE)
public abstract long insertOrUpdate(TmdbSeasonProgress item);

@Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId AND seasonNumber = :season")
public abstract int delete(int cid, String mediaType, int tmdbId, int season);

@Query("DELETE FROM TmdbSeasonProgress WHERE cid = :cid AND mediaType = :mediaType AND tmdbId = :tmdbId")
public abstract int deleteMedia(int cid, String mediaType, int tmdbId);

@Query("DELETE FROM TmdbSeasonProgress")
public abstract int deleteAll();
```

- [ ] **Step 4: 注册版本 42 和迁移**

在 `AppDatabase` 将 entity 列表加入 `TmdbSeasonProgress.class`，声明 `getTmdbSeasonProgressDao()`，版本从 `41` 改为 `42`，并注册 `Migrations.MIGRATION_41_42`。

在 `Migrations` 新增：

```java
public static final Migration MIGRATION_41_42 = new Migration(41, 42) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS TmdbSeasonProgress (" +
                "cid INTEGER NOT NULL, mediaType TEXT NOT NULL, tmdbId INTEGER NOT NULL, seasonNumber INTEGER NOT NULL, " +
                "episodeNumber INTEGER NOT NULL, position INTEGER NOT NULL, duration INTEGER NOT NULL, " +
                "sourceHistoryKey TEXT NOT NULL, sourceBindingKey TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(cid, mediaType, tmdbId, seasonNumber))");
    }
};
```

- [ ] **Step 5: 实现季度进度同步 store**

`TmdbSeasonProgressStore` 的核心规则：

```java
public static boolean isEligible(History history) {
    if (history == null || !"tv".equalsIgnoreCase(history.getMediaType())) return false;
    int season = history.getTmdbSeasonNumber();
    return history.getTmdbId() > 0 && (season > 0 || season == 0 && history.getTmdbEpisodeNumber() > 0);
}

public static void write(History history) {
    if (!isEligible(history)) return;
    TmdbSeasonProgress item = TmdbSeasonProgress.of(
            history.getCid(), history.getMediaType().toLowerCase(Locale.ROOT), history.getTmdbId(),
            history.getTmdbSeasonNumber(), history.getTmdbEpisodeNumber(), history.getPosition(),
            history.getDuration(), history.getKey());
    item.sourceBindingKey = history.getSiteKey() + AppDatabase.SYMBOL
            + history.getVodId() + AppDatabase.SYMBOL + history.getVodFlag();
    item.updatedAt = history.getCreateTime() > 0 ? history.getCreateTime() : System.currentTimeMillis();
    AppDatabase.get().getTmdbSeasonProgressDao().insertOrUpdate(item);
}

public static TmdbSeasonProgress find(int cid, String mediaType, int tmdbId, int season) {
    return AppDatabase.get().getTmdbSeasonProgressDao().find(cid, normalize(mediaType), tmdbId, season);
}

public static void reconcile(int cid, String mediaType, int tmdbId, int season) {
    History latest = null;
    for (History item : AppDatabase.get().getHistoryDao().findByTmdbIdentity(cid, normalize(mediaType), tmdbId)) {
        if (!isEligible(item) || item.getTmdbSeasonNumber() != season) continue;
        if (latest == null || item.getCreateTime() > latest.getCreateTime()) latest = item;
    }
    if (latest == null) AppDatabase.get().getTmdbSeasonProgressDao().delete(cid, normalize(mediaType), tmdbId, season);
    else write(latest);
}

public static void deleteMedia(int cid, String mediaType, int tmdbId) {
    AppDatabase.get().getTmdbSeasonProgressDao().deleteMedia(cid, normalize(mediaType), tmdbId);
}

private static String normalize(String mediaType) {
    if (mediaType == null) return "";
    String value = mediaType.trim().toLowerCase(Locale.ROOT);
    return "tv".equals(value) || "movie".equals(value) ? value : "";
}
```

`normalize` 只接受 `tv`/`movie` 小写值；`reconcile` 从同一 `cid`、同一季度的 History 记录选择 `createTime` 最新项。删除某一来源时若该季度仍有其他来源，保留并重建季度快照，只有没有任何同季度来源时才删除快照。

- [ ] **Step 6: 运行迁移和 store 测试并提交**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.TmdbSeasonProgressStoreTest"
```

Expected: entity 复合键、`cid` 隔离、未知季度不落盘、跨季同集号独立通过。

Commit only the task paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/bean/TmdbSeasonProgress.java app/src/main/java/com/fongmi/android/tv/db/dao/TmdbSeasonProgressDao.java app/src/main/java/com/fongmi/android/tv/playback/TmdbSeasonProgressStore.java app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java app/src/main/java/com/fongmi/android/tv/db/Migrations.java app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonProgressStoreTest.java
rtk git commit -m "feat: 增加 TMDB 季度独立进度存储"
```

### Task 6: 接入 History、播放写入、远端同步和备份

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/bean/History.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/bean/Backup.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonProgressStoreTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java`

- [ ] **Step 1: 写 History 写入/删除契约测试**

增加以下行为断言：

```java
@Test
public void savingKnownSeasonUpdatesSeasonProgressWithoutChangingHistoryKey() {
    History history = historyWithTmdb("tv", 88, 2, 5, "site@@@vod");
    history.setPosition(1234);
    assertEquals("site@@@vod", history.getKey());
    assertTrue(TmdbSeasonProgressStore.isEligible(history));
}

@Test
public void deletingOneSeasonDoesNotDeleteOtherSeasonProgress() {
    assertTrue(sourceContainsSeasonScopedDelete());
    assertFalse(sourceContainsProgramWideCascadeForSeasonCard());
}
```

同步入口测试覆盖 `PlaybackProgressWriter.applyInternal`、`deleteFromUser` 和 `deleteAllFromUser` 的调用链，确保 API/远端写入也更新季度快照，而不是只有详情页写入时才更新。

- [ ] **Step 2: 接入本地 History 保存和读取**

在 `History.save()` 完成 `HistoryDao.insertOrUpdate(this)` 后调用 `TmdbSeasonProgressStore.write(this)`。在 `findPlaybackByTmdb` 中先读取当前 `expectedSeason` 的季度快照，按快照中的 `sourceHistoryKey` 查找和重绑定来源；快照来源失效时回退到现有 `findPlaybackCandidate`，但仍通过 `isSeasonEligible` 过滤。

不要修改 `History.key` 结构，也不要把季度编码拼入来源主键；季度身份只存在于 TMDB 字段和新进度表。

- [ ] **Step 3: 接入 PlaybackProgressWriter 的所有写入路径**

在 `PlaybackProgressWriter.applyInternal` 对 History 完成字段赋值并写入 DB 后调用 `TmdbSeasonProgressStore.write(history)`。在 `deleteInternal` 删除每个 History 后调用 `reconcile`；全量删除时调用 `deleteMedia`。调用必须在同步锁内完成，保证快照不会比来源记录更新更早。

- [ ] **Step 4: 接入 Backup 的完整和选择性历史同步**

在 `Backup` 增加 `@SerializedName("tmdbSeasonProgress") private List<TmdbSeasonProgress> tmdbSeasonProgress`、`getTmdbSeasonProgress()` 和 `setTmdbSeasonProgress(List<TmdbSeasonProgress> value)`，并在以下路径同步：

```java
backup.setTmdbSeasonProgress(AppDatabase.get().getTmdbSeasonProgressDao().findAll());
if (options.isHistory()) backup.setTmdbSeasonProgress(AppDatabase.get().getTmdbSeasonProgressDao().findAll());
AppDatabase.get().getTmdbSeasonProgressDao().insertOrUpdate(getTmdbSeasonProgress());
if (options.isHistory() && force) AppDatabase.get().getTmdbSeasonProgressDao().deleteAll();
```

旧备份没有该字段时按空列表恢复。恢复配置产生 `cid` 映射时，同时改写季度进度的 `cid`；季度进度不复制或改写 `History.key`，只保留它作为最近来源引用。

- [ ] **Step 5: 运行相关测试并提交**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.history.TmdbSeasonProgressStoreTest" --tests "com.fongmi.android.tv.history.GlobalHistoryResumeSourceTest"
```

Expected: 本地、远端、备份恢复都保持季度独立进度；未知季度只保留来源 History。

Commit only the task paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/bean/History.java app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java app/src/main/java/com/fongmi/android/tv/bean/Backup.java app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java app/src/test/java/com/fongmi/android/tv/history/TmdbSeasonProgressStoreTest.java app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java
rtk git commit -m "feat: 接入季度进度续播和同步"
```

### Task 7: 在共用详情页按季度汇总线路

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeWiringTest.java`

- [ ] **Step 1: 写详情页线路矩阵测试**

增加纯映射测试，明确合集源和第三季独立源的结果：

```java
@Test
public void sourceMatrixPlacesMultiSeasonLineInEachCoveredSeason() {
    Map<Integer, List<String>> matrix = TmdbUIAdapter.projectSourceFlags(
            List.of(new TmdbUIAdapter.FlagSeasonBinding("flag-a", TmdbSeasonScope.multi(List.of(1, 2))),
                    new TmdbUIAdapter.FlagSeasonBinding("flag-b", TmdbSeasonScope.known(3))));
    assertEquals(List.of("flag-a"), matrix.get(1));
    assertEquals(List.of("flag-a"), matrix.get(2));
    assertEquals(List.of("flag-b"), matrix.get(3));
}
```

测试使用的绑定类型定义为 `TmdbUIAdapter.FlagSeasonBinding(String flagKey, TmdbSeasonScope scope)`，由 `projectSourceFlags` 只做纯映射，不访问网络或数据库。

在 `TmdbEpisodeWiringTest` 断言详情页季选择变化时同时调用当前季度线路过滤和季度进度读取，而不是只替换剧集标题。

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.TmdbUIAdapterTest" --tests "com.fongmi.android.tv.ui.helper.TmdbEpisodeWiringTest"
```

Expected: 当前 UI 只有当前 Vod/Flag 列表，没有可测试的季度线路矩阵。

- [ ] **Step 3: 在 TmdbUIAdapter 提供线路矩阵和季度过滤**

增加绑定类型和纯映射方法：

```java
public record FlagSeasonBinding(String flagKey, TmdbSeasonScope scope) {}

public static Map<Integer, List<String>> projectSourceFlags(List<FlagSeasonBinding> bindings) {
    Map<Integer, List<String>> result = new LinkedHashMap<>();
    if (bindings == null) return result;
    for (FlagSeasonBinding binding : bindings) {
        if (binding == null || binding.scope() == null || !binding.scope().isKnown()) continue;
        for (Integer season : binding.scope().getSeasons()) {
            result.computeIfAbsent(season, key -> new ArrayList<>()).add(binding.flagKey());
        }
    }
    return result;
}

public static Map<Integer, List<Flag>> sourceFlagsForSeason(Vod vod, int season) {
    Map<Integer, List<Flag>> result = new LinkedHashMap<>();
    if (vod == null || vod.getFlags() == null || season < 0) return result;
    for (int i = 0; i < vod.getFlags().size(); i++) {
        Flag flag = vod.getFlags().get(i);
        TmdbSeasonScope scope = resolveFlagScope(vod, flag, i);
        if (scope.accepts(season)) result.computeIfAbsent(season, key -> new ArrayList<>()).add(flag);
    }
    return result;
}
```

`UNKNOWN` 线路不进入已知季度列表；在原始来源区域单独展示。`MULTI` 线路进入其覆盖季度，并通过已有 `applyEpisodeTitlesForSlices`/`EpisodeSeasonPolicy` 映射当前季度的集数。

- [ ] **Step 4: 接入 TmdbDetailActivity 的季度切换**

详情页保留一个 `MediaIdentity`，切换 `selectedSeasonNumber` 时依次执行：

```java
tmdbUIAdapter.selectSeason(seasonNumber);
List<Flag> flags = tmdbUIAdapter.sourceFlagsForSeason(vod, seasonNumber).getOrDefault(seasonNumber, List.of());
renderEpisodesForSeason(seasonNumber);
renderSourceFlags(flags);
restoreSeasonProgress(matchedTmdbItem, seasonNumber);
```

主标题使用 TMDB 标题；播放历史标题通过现有 `sourceAwareTitle` 保留明确来源季度，不能把第三季再次改成节目根标题。

- [ ] **Step 5: 增加未知线路入口和整部节目删除确认**

未知线路不自动加入季度线路矩阵，用户手动选择后进入现有手动绑定流程。季度卡片删除调用当前季度身份；整部节目删除必须通过独立确认入口调用 TMDB 身份级删除。

- [ ] **Step 6: 运行测试并提交**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.TmdbUIAdapterTest" --tests "com.fongmi.android.tv.ui.helper.TmdbEpisodeWiringTest"
```

Expected: 第一、二季合集源分别出现在第一、二季；第三季线路不会出现在前两季；季度切换同步剧集、线路和进度。

Commit only the task paths:

```powershell
rtk git add app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeWiringTest.java
rtk git commit -m "feat: 在 TMDB 详情页按季度汇总线路"
```

### Task 8: 回归验证、迁移检查和可观测性

**Files:**
- Modify: `app/src/main/java/com/fongmi/android/tv/history/HistorySourceResolver.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolver.java`
- Modify: `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
- Test: `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbSeasonResolverTest.java`
- Test: `app/src/test/java/com/fongmi/android/tv/history/HistoryDisplayPolicyTest.java`

- [ ] **Step 1: 增加完整复现场景测试**

用以下数据构造端到端纯对象场景：A 线路覆盖 S1/S2，B 线路为 S3；播放 S1E5、S2E5、S3E2 后分别断言三个不同的季度进度 `identityKey()`、三条历史投影和季度级删除结果。

- [ ] **Step 2: 增加解析诊断日志**

在季度解析完成、候选被拒绝、绑定失效时沿用以下结构化日志调用，字段至少包含 `tmdbId`、`mediaType`、`scope.kind`、`source`、`reason` 和不可敏感的线路键：

```java
SpiderDebug.log("tmdb", "season scope tmdb=%d media=%s kind=%s source=%s reason=%s flag=%s",
        tmdbId, mediaType, scope.getKind(), source, reason, safeFlagKey);
```

不得写入完整播放 URL、Cookie 或鉴权参数。

- [ ] **Step 3: 运行移动端和电视端单元测试**

Run:

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest
```

Expected: 两个变体的 unit test 均以 `BUILD SUCCESSFUL` 结束，且没有季度相关失败。

- [ ] **Step 4: 编译两个主要变体**

Run:

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

Expected: Room schema、Java 编译和资源合并全部成功。

- [ ] **Step 5: 复核迁移和工作区**

Run:

```powershell
rtk git diff --check HEAD~8..HEAD
rtk git status --short --untracked-files=all
```

Expected: 最近任务提交没有空白错误；工作区只剩用户原有、尚未明确交由本任务处理的修改，或者已明确记录的生成产物。

## 计划自审

- 规格中的节目身份、季度身份、来源绑定、标题、历史投影、进度、详情页、换源、删除、异常降级、迁移、备份、测试和清理要求均有对应任务。
- `TmdbSeasonScope`、`TmdbSeasonProgress`、`TmdbSeasonProgressStore` 的类型名称在所有任务中保持一致。
- 旧 `History.key` 和旧 `TmdbSeasonMatchCache` API 都保留兼容入口；新增 Flag 键只扩展，不破坏旧设置。
- 所有代码任务先写失败测试，再实现，再运行目标测试；每个阶段只提交列出的路径。
- 没有使用“以后补充”或未定义的占位接口。
