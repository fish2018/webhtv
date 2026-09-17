# TV版详情页模式架构文档

本文档记录 TV 版（leanback）详情页各模式的代码结构、文件对应关系和差异控制点，便于后续维护和功能对齐。

## 一、模式总览

设置项位于：**设置 → 增强 → 详情页模式**（`SettingEnhanceActivity`）

字符串定义：`app/src/main/res/values-zh-rCN/strings.xml`

模式常量定义：`app/src/main/java/com/fongmi/android/tv/setting/Setting.java`（第37-42行）

| 设置项名称（中文） | 常量 | 值 | 承载 Activity | 说明 |
|---|---|---|---|---|
| 原生增强 | `DETAIL_OPEN_ORIGINAL_ENHANCED` | 5 | `VideoActivity` | 上游原生详情页，TMDB 内嵌增强 |
| 沉浸融合 | `DETAIL_OPEN_FUSION` | 0 | `TmdbDetailActivity` | TMDB 独立详情页，融合布局 |
| 炫彩详情 | `DETAIL_OPEN_ENHANCED` | 1 | `TmdbDetailActivity` | TMDB 独立详情页，富信息布局 |
| 详情直放 | `DETAIL_OPEN_PLAYER` | 4 | `TmdbDetailActivity` | TMDB 独立详情页，播放器优先 |
| 影视原生 | `DETAIL_OPEN_DIRECT` | 2 | `VideoActivity` | 上游原生详情页，无 TMDB |

> 设置界面可选模式（`SettingEnhanceActivity.DETAIL_OPEN_MODES`，第55行）共5项，顺序为：原生增强、沉浸融合、炫彩详情、详情直放、影视原生。
>
> 另有 `DETAIL_OPEN_CINEMA`（光影剧幕，值3）和 `DETAIL_OPEN_ORIGINAL`（原始详情，已废弃）为代码内保留模式，不在设置项中展示。

## 二、Activity 路由

### 关键文件
- **`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`** — 原生增强 / 影视原生
- **`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`** — 沉浸融合 / 炫彩详情 / 详情直放

### 路由入口
`VideoActivity.start(...)`（第372行附近）负责决定打开哪个 Activity：

```java
// 简化逻辑
if (tmdbItem == null && shouldOpenLegacyTmdbDetail(key, cast)) {
    // 满足 TMDB 独立详情页条件 → 跳转 TmdbDetailActivity
    TmdbDetailActivity.start(activity, key, id, name, pic, mark, null, Setting.getDetailOpenMode());
    return;
}
// 否则 → 打开 VideoActivity
Intent intent = new Intent(activity, VideoActivity.class);
...
```

### 路由判断条件

**`shouldOpenLegacyTmdbDetail(key, cast)`**（`VideoActivity.java:311`）：
```java
return canOpenLegacyTmdbDetail(key, cast)
    && Setting.isTmdbDetailPage()                          // TMDB 原生模式且配置就绪
    && Setting.isStandaloneTmdbDetailMode(mode);           // mode ∈ {FUSION, ENHANCED, PLAYER}
```

**`isStandaloneTmdbDetailMode(mode)`**（`Setting.java:915`）：
```java
return mode == DETAIL_OPEN_FUSION
    || mode == DETAIL_OPEN_ENHANCED
    || mode == DETAIL_OPEN_PLAYER;
```

> 结论：**沉浸融合 / 炫彩详情 / 详情直放** 走 `TmdbDetailActivity`；**原生增强 / 影视原生** 走 `VideoActivity`。

## 三、VideoActivity 内部模式分支

`VideoActivity` 同时承载"原生增强"和"影视原生"两个模式，通过以下判断区分：

### 影视原生（DIRECT）的标志
```java
// Setting.java:927
public static boolean isDirectDetailPage() {
    return getDetailOpenMode() == DETAIL_OPEN_DIRECT;
}

// VideoActivity.java:544 —— 是否使用上游原生选集模块
private boolean shouldUseUpstreamNativeEpisodeModule() {
    return Setting.isDirectDetailPage() && !isTmdbMode();
}
```
影视原生模式下，选集列表使用上游原生分段网格布局（`setUpstreamNativeEpisodeItems`）。

### 原生增强（ORIGINAL_ENHANCED）的标志
```java
// Setting.java:935
public static boolean isOriginalEnhancedDetailPage() {
    return getDetailOpenMode() == DETAIL_OPEN_ORIGINAL_ENHANCED;
}

// VideoActivity.java:1094 —— 是否加载 TMDB 富集数据
setOriginalEnhancedActionVisibility(loadTmdbDetail && Setting.isOriginalEnhancedDetailPage());
```
原生增强模式下，会在 `VideoActivity` 内部加载 TMDB 富集信息（演员、剧照、推荐等），但布局主体仍是 `activity_video.xml`。

### TMDB 内嵌加载开关
```java
// VideoActivity.java:1131
private boolean shouldLoadTmdbDetail() {
    return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
}
```
`mTmdbUIAdapter` 由 `initTmdbMode()`（`VideoActivity.java:4183`）在 `isTmdbSourceEnabled()` 为真时初始化。

## 四、TmdbDetailActivity 内部模式分支

`TmdbDetailActivity` 承载三个模式，通过 Intent 的 `detail_mode` extra 区分：

### 模式判断方法（`TmdbDetailActivity.java:4076`）
```java
private int getDetailMode() {
    if (getIntent().hasExtra("detail_mode"))
        return normalizeDetailMode(getIntent().getIntExtra("detail_mode", DETAIL_OPEN_ENHANCED));
    return getIntent().getBooleanExtra("fusion", false) ? DETAIL_OPEN_FUSION : DETAIL_OPEN_ENHANCED;
}

private boolean isFusionMode()  { return getDetailMode() == DETAIL_OPEN_FUSION  || getIntent().getBooleanExtra("fusion", false); }
private boolean isPlayerMode()  { return getDetailMode() == DETAIL_OPEN_PLAYER; }
private boolean isCinemaMode()  { /* DETAIL_OPEN_CINEMA 或 isTmdbCinemaStyle() */ }
```

### 布局差异控制点（`TmdbDetailActivity.java:524` 附近）
```java
binding.playerPanel.setVisibility(isFusionMode() ? VISIBLE : GONE);     // 沉浸融合显示内嵌播放器面板
binding.heroSpacer.setVisibility(isFusionMode() ? GONE : VISIBLE);       // 沉浸融合无顶部间距
binding.fusionActions.setVisibility(isFusionMode() ? VISIBLE : GONE);    // 沉浸融合用 fusionActions 按钮组
binding.detailActions.setVisibility(isFusionMode() ? GONE : VISIBLE);    // 其他模式用 detailActions 按钮组
```

### 主题（Theme）维度
- `Setting.getDetailThemeMode()`：`DETAIL_STYLE_PROFILE(0)` / `DETAIL_STYLE_CINEMA(1)` / `DETAIL_STYLE_NATIVE(2)`
- 主题影响配色和卡片样式，通过 `setDetailActionButton`、`castAdapter.setCinema()` 等应用
- 原生增强模式默认 `DETAIL_STYLE_NATIVE`，其余默认 `DETAIL_STYLE_PROFILE`（`Setting.java:795`）

## 五、布局文件对应

### VideoActivity 布局
- **`app/src/leanback/res/layout/activity_video.xml`** — 详情页主布局
  - 顶部按钮行（HorizontalScrollView，id=row2）：`content`(简介) / `shortDisplay`(短显) / `searchDetail`(搜索) / `keep`(收藏) / `change1`(换源，已隐藏) / `tmdbRematch`(重匹配)
  - 滚动区（NestedScrollView，id=scroll）：`flag`(线路) / `quality`(清晰度) / `array`(分段) / `episodeContainer`(选集) / TMDB 富集区（演员/剧照/主创/推荐）
- **`app/src/leanback/res/layout/view_control_vod.xml`** — 播放器控制栏容器（include 到 activity_video 的 id=control）
- **`app/src/leanback/res/layout/view_control_vod_action.xml`** — 控制栏动作按钮行
  - 按钮：`next`/`prev`/`episodes`/`reset`/`search`/`change2`(已隐藏)/`fullscreen`/`player`/`decode`/`speed`/`scale`/`actionQuality`/`lut`/`text`/`audio`/`video`/`opening`/`ending`/`danmaku`/`title`/`repeat`

### TmdbDetailActivity 布局
- **`app/src/main/res/layout/activity_tmdb_detail.xml`** — TMDB 独立详情页主布局（共用，通过 `fusionActions`/`detailActions` 等切换子区域可见性）
  - 按钮组：`changeSource`/`changeSourceDetail`/`playerChangeSource`（换源，已改为搜索文本）/ `themeMode`/`themeModeTop`/`themeModeDetail`(主题) / `rematch`/`rematchTop`/`rematchFusion`(重匹配)

## 六、TMDB 模式选集数据边界

原生增强、沉浸融合、炫彩详情和详情直放统一遵循以下规则：

- 当前线路的 `Flag.getEpisodes()` 是可播放内容的唯一事实源；
- TMDB 完整季度只用于补充标题、剧照、日期和季集位置，不生成播放项；
- 季度导航只显示能够从当前线路可靠确认的季度；
- 当前线路只有一个可播放季度时，隐藏切换导航，但必须在顶部元信息和选集标题保留该季度上下文；
- 无法可靠分季时隐藏季度导航，并保留线路原始集数列表；
- 不显示可点击但没有播放 URL 的 TMDB 季度或集数。

- 原生增强必须在 TMDB 标准标题覆盖线路原名之前保存来源季度，并在“选集 · 第 X 季”及移动端融合元信息中展示；
- 明确来源季度时，TMDB 剧集标题只允许绑定该季，失败时不得回退套用第 1 季；
- 聚合续播与单集位置缓存都以季号隔离：不同季度的同集号不得共享进度，季号未知的旧记录只在同一来源 key 内兼容恢复。

详细解析优先级、状态切换、测试矩阵见：[`docs/tmdb-playable-episode-availability-design.md`](docs/tmdb-playable-episode-availability-design.md)。

## 七、关键经验与陷阱

### 1. ViewBinding ID 冲突（重要）
**问题**：`activity_video.xml` 和 `view_control_vod_action.xml`（通过 include 嵌套）中曾同时存在 `android:id="@+id/search"`，导致 `mBinding.search` 绑定到错误视图，点击事件失效。

**解决**：将详情页顶部搜索按钮改名为 `searchDetail`，控制栏的保留为 `search`（通过 `mBinding.control.action.search` 访问）。

**规则**：使用 `<include>` 嵌套布局时，**整个布局树内所有 `android:id` 必须唯一**。ViewBinding 不会对重复 id 报错，但会绑定到不确定的视图，导致点击/可见性设置失效且无任何错误提示。新增按钮前务必全局搜索 id 是否已存在。

### 2. 两个 VideoActivity 共存
项目同时存在：
- `app/src/leanback/java/.../VideoActivity.java`（TV 版）
- `app/src/mobile/java/.../VideoActivity.java`（手机版）

修改 TV 版详情页时，确认改的是 `leanback` source set 下的文件。同理布局文件区分 `leanback/res/layout` 与 `mobile/res/layout`（及 `mobile/res/layout-sw600dp`）。

### 3. 换源按钮的多处定义
"换源"按钮在多处存在，统一改为搜索时需逐个处理：

| 按钮 ID | 文件 | 位置 |
|---|---|---|
| `change1` | `activity_video.xml` | VideoActivity 顶部按钮行（已隐藏） |
| `change2` | `view_control_vod_action.xml` | VideoActivity 控制栏（已隐藏） |
| `changeSource` | `activity_tmdb_detail.xml` | TmdbDetailActivity 顶部（文本改为搜索） |
| `changeSourceDetail` | `activity_tmdb_detail.xml` | TmdbDetailActivity 详情区（文本改为搜索） |
| `playerChangeSource` | `activity_tmdb_detail.xml` | TmdbDetailActivity 播放器（文本改为搜索） |

### 4. 搜索功能的两套实现
- **VideoActivity 视频内搜索**：`onSearch()` → `initSearch(keyword, false)` → `startSearch()` → `mViewModel.searchContent()` → `QuickSearchDialog`（弹窗式搜索结果列表）
- **全局搜索**：`SearchActivity.start(context, keyword)`（带输入框）或 `SearchActivity.direct(context, keyword)`（直接进 `CollectActivity` 聚合结果页，无输入框）
- **TmdbDetailActivity 无 QuickSearchDialog**：当前 TmdbDetailActivity 的搜索按钮（短按+长按）均调用 `openGlobalSourceSearch()` → `SearchActivity.direct()`，与 VideoActivity 的视频内搜索效果不同。若要对齐原生增强的弹窗式搜索，需在 TmdbDetailActivity 引入 `SiteViewModel` + `QuickAdapter` + `QuickSearchDialog`，成本较高。

### 5. 调试方法
- **模拟器架构**：项目只编译 ARM 架构（`leanbackArm64_v8a` / `leanbackArmeabi_v7a`），x86_64 模拟器无法安装运行。需用 ARM 模拟器或真机测试。
- **点击事件无效排查**：在 `setOnClickListener` 的 lambda 内首行加 `android.util.Log.e("TAG", "=== clicked ===")`，配合 `adb logcat | grep "==="` 验证回调是否触发。若无日志，优先怀疑 id 冲突或焦点被父容器拦截。
- **adb 操作模拟器**：TV 版要选宽屏设备，先用 `adb devices` 和 `adb -s <device> shell wm size` 确认，例如 `emulator-5556` 是 `1920x1080`。实际安装包名是 `com.silent.android.webhtv`，源码 namespace 是 `com.fongmi.android.tv`。启动当前 app 用 `adb -s emulator-5556 shell monkey -p com.silent.android.webhtv -c android.intent.category.LEANBACK_LAUNCHER 1`，或显式组件 `adb -s emulator-5556 shell am start -n com.silent.android.webhtv/com.fongmi.android.tv.ui.activity.HomeActivity`；`adb logcat -c` 清空日志。

### 6. 编译命令
```bash
# 清理
./gradlew clean

# 编译 TV 版（ARM64）
./gradlew app:assembleLeanbackArm64_v8aDebug

# 安装到已连接设备
adb install -r app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
```

任务名规则：`app:assembleLeanback{Arm64_v8a|Armeabi_v7a}Debug`（注意 flavor 与 ABI 连写，没有单独的 `assembleLeanbackDebug`）。

## 八、设置相关方法速查（Setting.java）

| 方法 | 判断内容 |
|---|---|
| `getDetailOpenMode()` | 当前详情页模式（0~5） |
| `isTmdbDetailPage()` | 是否为 TMDB 原生模式且配置就绪 |
| `isStandaloneTmdbDetailMode(mode)` | mode ∈ {FUSION, ENHANCED, PLAYER} |
| `isFusionDetailPage()` | 沉浸融合 |
| `isSearchDetailPage()` | 炫彩详情（ENHANCED） |
| `isPlayerDetailPage()` | 详情直放 |
| `isDirectDetailPage()` | 影视原生 |
| `isOriginalEnhancedDetailPage()` | 原生增强 |
| `isCinemaDetailPage()` | 光影剧幕 |
| `getDetailThemeMode()` | 详情页主题（PROFILE/CINEMA/NATIVE） |
| `getTmdbModel()` | TMDB 模型（默认 NATIVE=0） |

## 九、字符串资源速查

详情页模式相关字符串（`values-zh-rCN/strings.xml`）：

```
setting_detail_open_mode          详情页模式
setting_detail_open_original_enhanced  原生增强
setting_detail_open_fusion        沉浸融合
setting_detail_open_enhanced      炫彩详情
setting_detail_open_player        详情直放
setting_detail_open_direct        影视原生
setting_detail_open_cinema        光影剧幕
setting_detail_open_native        TMDB 内嵌增强
setting_detail_open_original      原始详情（已废弃）
setting_detail_theme_mode         详情页主题
```

按钮相关字符串：
```
play_search    搜索
play_change    换源
detail_desc    简介
keep           收藏
```
