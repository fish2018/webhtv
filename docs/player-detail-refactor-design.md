# 播放器与 TMDB 详情页解耦设计

> **状态：🟢 第一、二阶段已完成，第三阶段未开始（2026-07-14 核实）**
>
> **✅ 第一阶段（播放器 UI Controller）已完成**：
> - `VodPlayerUiController` / `VodPlayerUiHost` / `VodPlayerChrome` 已建。
> - 已接入三个 Activity：
>   - `TmdbDetailActivity` (line 758)：内联播放器。
>   - mobile `VideoActivity` (line 802)：全屏播放器。
>   - leanback `VideoActivity`：全屏播放器。
> - OSD、PiP、Clock、控制栏基础按钮已统一。
>
> **✅ 第二阶段（TMDB 详情页模式 Controller）已完成（2026-07-14）**：
> - 已建 `TmdbDetailModeController` 接口和 `BaseTmdbDetailModeController` 基类。
> - 已建三个模式实现：
>   - `FusionDetailController`（沉浸融合）
>   - `EnhancedDetailController`（炫彩详情）
>   - `PlayerDetailController`（详情直放）
> - `TmdbDetailActivity.initModeController()` 根据模式创建对应 Controller。
> - `initPage()` 里的模式判断可见性设置已删除，统一委托给各 Controller 的 `applyInitialLayout()`。
> - 新增 `DetailModeControllerTest` 用源码断言锁定三种模式的布局差异实现。
>
> **❌ 第三阶段（ViewModel 状态管理）未开始**：
> - 无 `TmdbDetailViewModel` / `PlayerSessionViewModel`。

## 状态

In Progress

## 日期

2026-07-07

## 实施记录

- 2026-07-07：第一阶段开始，新增 `VodPlayerUiController`、`VodPlayerUiHost`、`VodPlayerChrome`，先承接 `TmdbDetailActivity` 沉浸融合内嵌播放器的 OSD、PiP、Clock、控制器初始化和 TV 控制栏按钮绑定。
- 2026-07-07：两个 `VideoActivity` 接入 `VodPlayerUiController`，共享 OSD、Clock、PiP 初始化和生命周期；控制栏按钮仍按阶段继续迁移。
- 2026-07-14：第二阶段完成，新增模式 Controller 体系（`TmdbDetailModeController` 接口 + 三个实现），`TmdbDetailActivity` 接入并删除 `initPage()` 里的旧模式判断逻辑。

## 背景

当前详情页和播放器相关逻辑主要集中在几个大型 Activity 中：

- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`

`PlaybackActivity` 已经统一了播放器服务绑定、`MediaController`、Player surface、生命周期基础回调等底层能力。因此本设计不重写播放器底座，而是把 Activity 里重复的播放器 UI 穿线、OSD、LUT、PiP、控制按钮、弹幕入口和详情页模式差异逐步抽出。

核心判断：

1. 播放器统一应该先做，因为每次新增播放器功能都会在多个 Activity 中重复实现。
2. TMDB 详情页三种模式不适合拆成多个 Activity。沉浸融合、炫彩详情、详情直放共享大量实现，只应把差异点放入模式 Controller。
3. ViewModel 改造暂时不作为第一阶段主线。项目现有代码主要是 Java + `LiveData`，强行迁移 `StateFlow` 会扩大范围。

## 目标

1. 降低 `TmdbDetailActivity` 和两个 `VideoActivity` 的播放器相关代码量。
2. 统一播放器按钮、OSD、LUT、弹幕、PiP 和生命周期接入方式。
3. 保留现有页面行为、布局和入口路由，不做用户可见交互改版。
4. 让详情页模式支持“一套共用实现，少量差异覆盖”。
5. 每阶段都可独立编译、测试和回滚。

## 非目标

- 不重写 `PlaybackActivity`、`PlaybackService` 或 `PlayerManager`。
- 不把沉浸融合、炫彩详情、详情直放拆成三个 Activity。
- 不在第一阶段引入 Kotlin、协程或 `StateFlow`。
- 不改变详情页模式配置项和路由规则。
- 不处理 `RemoteTrustDialog` 拆分。它属于独立技术债，后续单独设计。

## 现状边界

### 已有底座

`PlaybackActivity` 负责：

- 绑定 `PlaybackService`
- 创建和释放 `MediaController`
- 挂载播放器 surface
- 同步 keep screen on
- 转发播放状态回调
- 提供 `startPlayer(...)`

这部分继续保留为播放器底座。

### 已有可复用组件

项目已经有几个局部组件：

- `VodPlayerControlController`
- `PlayerOsdController`
- `PiP`
- `Clock`
- `SubtitlePlaybackSession`

第一阶段不是新增一套完全不同的播放器系统，而是把这些组件的初始化、按钮绑定和生命周期收拢到一个更高层的播放器 UI Controller。

## 总体方案

采用组合方式，不新增 Activity 继承层。

```text
PlaybackActivity
  ├─ VideoActivity mobile
  ├─ VideoActivity leanback
  └─ TmdbDetailActivity

Activity 组合：
  ├─ VodPlayerUiController
  │    ├─ VodPlayerControlController
  │    ├─ PlayerOsdController
  │    ├─ PiP
  │    ├─ Clock
  │    └─ LUT / danmaku / track actions
  │
  └─ TmdbDetailModeController
       ├─ FusionDetailController
       ├─ EnhancedDetailController
       └─ PlayerDetailController
```

Activity 继续负责：

- 页面入口和 Intent 参数解析
- ViewBinding 持有
- Android 生命周期回调入口
- 调用 `PlaybackActivity` 提供的底层播放能力
- 少量必须依赖 Activity 的导航行为

Controller 负责：

- 控件绑定
- UI 状态刷新
- 模式差异判断
- 组合现有播放器辅助组件
- 把用户操作转发给 Activity Host

## 第一阶段：播放器 UI Controller

### 范围

先抽播放器 UI 相关逻辑，不碰 TMDB 数据加载和详情模式整体结构。

优先从 `TmdbDetailActivity.initFusionPlayer()` 开始，因为这里已经把 OSD、LUT、PiP、Clock、控制按钮、焦点、弹幕入口集中在一个方法中，边界最明显。

### 新增文件

预计新增 4 个文件：

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerUiController.java` | 播放器 UI 总控，持有 OSD、控制栏、PiP、Clock、LUT 入口 |
| `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerUiHost.java` | Activity 暴露给 Controller 的最小能力接口 |
| `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerChrome.java` | 播放器控件集合，屏蔽不同布局的 View 查找差异 |
| `app/src/main/java/com/fongmi/android/tv/ui/player/VodPlayerLifecycle.java` | 可选。若生命周期方法很少，可并入 `VodPlayerUiController` |

### 修改文件

预计修改 5 个文件：

| 文件 | 修改内容 |
|---|---|
| `TmdbDetailActivity.java` | 用 `VodPlayerUiController` 替换部分内联播放器穿线 |
| `app/src/mobile/.../VideoActivity.java` | 接入 Host，逐步迁移 OSD、LUT、控制栏逻辑 |
| `app/src/leanback/.../VideoActivity.java` | 接入 Host，逐步迁移 OSD、LUT、控制栏逻辑 |
| `VodPlayerControlController.java` | 仅在必要时扩展 Host 能力，保持小接口 |
| 相关测试文件 | 增加或调整播放器按钮、OSD、LUT 行为测试 |

### 删除文件

第一阶段删除 0 个文件。

### Host 接口草案

接口只暴露 Controller 真正需要的能力，不把整个 Activity 泄漏进去。

```java
public interface VodPlayerUiHost {

    Context context();

    PlayerManager player();

    MediaController controller();

    boolean isOwner();

    boolean isFullscreen();

    boolean isMobile();

    String playbackKey();

    String playbackTitle();

    String currentEpisodeTitle();

    void play();

    void pause();

    void seekBy(long offsetMs);

    void refreshPlayback();

    void showEpisodes();

    void showQuality();

    void showTrack(int trackType);

    void showSubtitle();

    void showDanmaku();

    void showPlayerInfo();

    void showPlayerChoice();

    void onDanmakuStateChanged(boolean show);
}
```

实现时不要求一次放入所有方法。按迁移顺序从 `TmdbDetailActivity` 需要的方法开始，后续 VideoActivity 接入时再补充。

### Chrome 对象草案

`VodPlayerChrome` 负责持有播放器 UI 所需 View。不同 source set 可以用不同工厂创建。

```java
public final class VodPlayerChrome {

    public final PlayerView exo;
    public final View playerPanel;
    public final CustomSeekView seek;
    public final View controlsRoot;
    public final View actionRoot;
    public final View osdRoot;
    public final TextView osdTopLeft;
    public final TextView osdTopRight;
    public final TextView osdBottomLeft;
    public final TextView osdBottomRight;
    public final TextView osdDiagnostics;
    public final MiniProgressView osdMiniProgress;

    // 按钮按需加入，避免第一版对象过大。
}
```

### 生命周期接入

Activity 生命周期只保留转发：

```java
@Override
protected void onStart() {
    super.onStart();
    playerUi.onStart();
}

@Override
protected void onStop() {
    super.onStop();
    playerUi.onStop();
}

@Override
protected void onDestroy() {
    playerUi.release();
    super.onDestroy();
}
```

### 第一阶段成功标准

- `TmdbDetailActivity.initFusionPlayer()` 明显缩短，只保留创建 Controller 和少量 Host 绑定。
- OSD、LUT、弹幕按钮、控制栏基础行为不变。
- mobile 和 leanback `VideoActivity` 可逐步接入，不要求第一版完全迁完。
- 不改变 `PlaybackActivity` 的外部行为。

## 第二阶段：TMDB 详情页模式 Controller

### 范围

抽出沉浸融合、炫彩详情、详情直放的模式差异。

不拆 Activity，不拆布局主文件。仍由 `TmdbDetailActivity` 承载独立 TMDB 详情页。

### 新增文件

预计新增 6 个文件：

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/ui/detail/TmdbDetailModeController.java` | 模式 Controller 接口 |
| `app/src/main/java/com/fongmi/android/tv/ui/detail/BaseTmdbDetailModeController.java` | 三种模式共享实现 |
| `app/src/main/java/com/fongmi/android/tv/ui/detail/FusionDetailController.java` | 沉浸融合差异 |
| `app/src/main/java/com/fongmi/android/tv/ui/detail/EnhancedDetailController.java` | 炫彩详情差异 |
| `app/src/main/java/com/fongmi/android/tv/ui/detail/PlayerDetailController.java` | 详情直放差异 |
| `app/src/main/java/com/fongmi/android/tv/ui/detail/TmdbDetailHost.java` | Activity 暴露给模式 Controller 的最小能力接口 |

### 修改文件

预计修改 3 到 5 个文件：

| 文件 | 修改内容 |
|---|---|
| `TmdbDetailActivity.java` | 创建并委托 `TmdbDetailModeController` |
| `TmdbHeaderView.java` | 如有模式专属样式，改为接收模式配置或 Host 回调 |
| `Setting.java` | 尽量不动。若需要，添加纯判断工具方法 |
| 相关测试文件 | 覆盖模式选择、按钮可见性、播放器面板行为 |

### 删除文件

第二阶段删除 0 个文件。

### 模式接口草案

```java
public interface TmdbDetailModeController {

    void bind();

    void applyInitialLayout();

    void applyTheme();

    void onContentLoaded();

    void onPlaybackStarted();

    boolean shouldShowInlinePlayer();

    boolean shouldAutoPlay();

    boolean handleBack();
}
```

### 继承使用原则

可以在 Controller 层使用继承，但不在 Activity 层增加继承。

推荐结构：

```text
BaseTmdbDetailModeController
  ├─ FusionDetailController
  ├─ EnhancedDetailController
  └─ PlayerDetailController
```

共用逻辑放在 `BaseTmdbDetailModeController`，差异点通过小方法覆盖：

```java
protected boolean showInlinePlayer() {
    return false;
}

protected boolean useFusionActions() {
    return false;
}

protected int heroSpacerVisibility() {
    return View.VISIBLE;
}
```

如果差异只是常量和可见性，优先使用配置对象，不急着增加 override。

```java
public record TmdbDetailModeSpec(
        boolean inlinePlayer,
        boolean fusionActions,
        boolean autoPlay,
        boolean compactHero
) {}
```

### 第二阶段成功标准

- `TmdbDetailActivity` 不再到处直接判断 `isFusionMode()`、`isPlayerMode()`、`isCinemaMode()`。
- 三种模式的差异集中在 Controller 或 `TmdbDetailModeSpec` 中。
- 新增一个模式时，不需要在 Activity 中散落新增大量 `if`。
- 现有模式路由和设置项保持不变。

## 第三阶段：ViewModel 和状态管理

### 范围

第三阶段只在前两阶段稳定后做。目标是把数据加载和可恢复状态从 Activity 中移走。

第一版继续使用 Java + `LiveData`，避免一次性迁移 Kotlin + `StateFlow`。

### 新增文件

预计新增 3 到 4 个文件：

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/model/TmdbDetailViewModel.java` | TMDB 数据、演员、剧照、推荐内容加载状态 |
| `app/src/main/java/com/fongmi/android/tv/model/TmdbDetailState.java` | 页面状态快照 |
| `app/src/main/java/com/fongmi/android/tv/model/PlayerSessionViewModel.java` | 播放状态、选集、历史记录状态 |
| `app/src/main/java/com/fongmi/android/tv/model/TmdbDetailRepository.java` | 可选。封装 TMDB service 和缓存 |

### 修改文件

预计修改 2 到 3 个文件：

| 文件 | 修改内容 |
|---|---|
| `TmdbDetailActivity.java` | 观察 ViewModel 状态，减少直接数据加载 |
| 两个 `VideoActivity.java` | 视播放器状态复用情况接入 `PlayerSessionViewModel` |
| `SiteViewModel.java` | 默认不动，除非需要复用搜索任务接口 |

### 删除文件

第三阶段删除 0 个文件。

### 第三阶段成功标准

- 配置变更后，TMDB 基础数据和已选集数不容易丢失。
- Activity 更像渲染层，数据加载和状态更新进入 ViewModel。
- 保持与现有 `SiteViewModel` 风格一致。

## 分期汇总

| 阶段 | 新增 | 修改 | 删除 | 风险 | 收益 |
|---|---:|---:|---:|---|---|
| 第一阶段：播放器 UI Controller | 4 | 5 | 0 | 中 | 高 |
| 第二阶段：详情模式 Controller | 6 | 3 到 5 | 0 | 中 | 中高 |
| 第三阶段：ViewModel 状态管理 | 3 到 4 | 2 到 3 | 0 | 中高 | 中 |
| 合计 | 13 到 14 | 8 到 12 | 0 | 中高 | 高 |

## 迁移顺序

1. 新增 `VodPlayerUiHost` 和 `VodPlayerChrome`，不改变现有逻辑。
2. 新增 `VodPlayerUiController`，先只迁移 `PlayerOsdController`、`Clock`、`PiP` 的创建和释放。
3. 迁移 `TmdbDetailActivity.initFusionPlayer()` 中的按钮绑定到 Controller。
4. 跑现有布局测试和播放器相关单测。
5. mobile `VideoActivity` 接入 OSD 和 LUT 生命周期。
6. leanback `VideoActivity` 接入 OSD 和 LUT 生命周期。
7. 新增 `TmdbDetailModeController`，先只承接模式可见性和标题逻辑。
8. 逐步把 `applyDetailTemplate()`、主题、按钮组差异迁入模式 Controller。
9. 前两阶段稳定后，再评估 ViewModel。

## 风险与对策

| 风险 | 对策 |
|---|---|
| Host 接口过大，变成 Activity 代理垃圾桶 | 每次只为已迁移代码添加方法，禁止预留暂时不用的方法 |
| Controller 直接依赖具体 Activity，导致无法复用 | 只依赖 `VodPlayerUiHost` / `TmdbDetailHost` |
| mobile 和 leanback 布局差异导致 Controller 充满条件判断 | 用 `VodPlayerChrome` 工厂屏蔽 View 差异 |
| 迁移时破坏焦点导航 | 每迁移一组按钮就跑对应布局测试，避免批量搬迁 |
| 抽象过早导致文件多但收益小 | 第一阶段只抽播放器 UI 穿线，不抽数据加载 |
| ViewModel 迁移范围失控 | 第三阶段再做，并优先沿用 `LiveData` |

## 验证计划

### 单元和布局测试

优先跑已有测试：

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest
```

重点覆盖：

- 沉浸融合内嵌播放器按钮顺序不变
- OSD 显示设置不变
- LUT 快捷面板焦点和选择不变
- 全屏进入和退出恢复布局
- mobile 和 leanback 播放器按钮可见性不变

### 手工验证

每阶段至少验证：

1. 原生增强进入播放。
2. 影视原生进入播放。
3. 沉浸融合进入详情，内嵌播放，切全屏，再退出。
4. 炫彩详情进入详情并播放。
5. 详情直放自动进入播放。
6. LUT 导入、切换、退出。
7. 弹幕开关和弹幕设置入口。
8. 返回键、PiP、横竖屏切换。

## 决策记录

### 决策 1：不用 Activity 继承拆模式

原因：

- 三个详情模式共享大量实现。
- Activity 已经继承 `PlaybackActivity`。
- 再引入 `BaseTmdbDetailActivity` 会把复杂度从一个大类转移到一个大父类。

结论：

模式差异放在 Controller 层，Activity 只组合 Controller。

### 决策 2：第一阶段不迁移 ViewModel

原因：

- 播放器重复代码收益更直接。
- 项目现有 ViewModel 是 Java + `LiveData`。
- `StateFlow` 迁移会引入语言和运行时风格变化。

结论：

先做播放器 UI Controller，再做模式 Controller，最后再做 ViewModel。

### 决策 3：不追求一次减少 80% 代码

原因：

- 大型 Activity 中有很多状态和 UI 细节互相耦合。
- 一次性搬迁难以评审，也难以定位回归。

结论：

以可验证的小步迁移为准。第一阶段只要求降低播放器穿线重复，后续再继续收敛。
