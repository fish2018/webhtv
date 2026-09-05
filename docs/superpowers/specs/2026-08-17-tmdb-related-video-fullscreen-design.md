# TMDB 相关视频全屏播放设计

## 状态

已获用户确认，待实现。

## 背景

TMDB 相关视频当前通过 `TmdbVideoPlayerDialog` 在详情页上方打开独立弹窗。弹窗使用独立的 Media3 `PlayerView`，窗口尺寸受 Dialog 布局约束，无法获得正片播放页的全屏体验、控制栏、手势、字幕、音轨和 TV 焦点行为。

直接把弹窗铺满屏幕只能解决尺寸问题，仍会维护第二套播放器控制逻辑。直接启动普通播放页则可能覆盖父页面的共享播放会话，关闭后无法可靠恢复原视频。因此需要一个明确的临时播放模式。

## 目标

- 点击相关视频后进入与正片相同的标准全屏播放页。
- 复用现有 `VideoActivity`、`PlayerManager`、控制栏、手势、字幕、音轨、画面比例和 TV 焦点行为。
- 打开前暂停原视频并保存可恢复状态。
- 返回、关闭或 TV 返回后恢复原视频的媒体、进度和原播放/暂停状态。
- TMDB 临时视频使用隔离解析，不停止原视频的全局 Source。
- 临时视频不写入正片观看历史、不触发普通 TMDB 详情跳转、不参与下一集流程。

## 非目标

- 本次不重构整个播放器控制栏为独立跨 Activity 组件。
- 本次不改变普通正片播放、直播播放或音频播放的既有入口。
- 本次不为非 `PlaybackActivity` 页面设计无法恢复父播放器的临时播放行为。

## 方案

### 入口与数据流

`TmdbVideoPlayback.Launch` 继续作为一次播放请求的数据对象，包含站点、视频 ID、标题、封面、备注、播放标记和已解析的剧集 URL。

点击处理改为：

1. `TmdbVideoPlayback.play()` 验证当前 Activity 是 `PlaybackActivity`。
2. 父页面调用统一的临时播放入口，暂停当前播放器并创建 `PlaybackResumeSnapshot`。
3. 父页面启动 `VideoActivity`，Intent 携带 `transientPlayback=true` 和 Launch 播放参数。
4. 临时 `VideoActivity` 使用现有标准播放布局和控制链路，但在 TMDB PUSH URL 解析时调用 `SiteApi.playerContentIsolated()`。
5. 临时页面结束后向父页面返回完成结果；父页面根据快照重新准备原媒体并恢复位置和播放状态。

### 临时播放标记

新增内部 Intent 标记 `EXTRA_TRANSIENT_PLAYBACK`。临时分支必须在解析、历史、详情跳转和播放完成处理处统一生效，避免只在入口处标记而被后续普通流程覆盖。

临时分支的行为：

- 跳过普通 TMDB 详情页路由。
- 不写入观看历史或正片播放进度。
- 不自动进入下一集。
- 标题和备注使用 Launch 中的 TMDB 视频信息。
- 控制栏和输入处理完全使用现有 `activity_video.xml` / `view_control_vod` 链路。

### 父页面快照与恢复

`PlaybackActivity` 增加临时播放生命周期协调，快照至少包含：

- 站点 Key、视频 ID、播放标记、剧集 URL 和标题信息。
- 当前播放位置及必要的播放选择信息。
- 打开临时页前是否处于播放状态。

恢复顺序：

1. 临时页面通过返回键、关闭键、解析失败或系统结束返回结果。
2. 父页面重新准备快照中的原媒体。
3. 恢复到原播放位置。
4. 只有快照记录为播放中时调用 `play()`；原本暂停则保持暂停。

恢复操作由父页面统一执行，临时页面不直接调用父播放器的播放方法，避免生命周期竞争。

### 隔离解析

保留 `SiteApi.playerContent()` 的现有全局行为，新增的 `playerContentIsolated()` 只为临时 TMDB 播放使用。临时路径创建独立 `Source`，不得调用全局 `Source.get().stop()`。

## 错误与生命周期

- 启动期间锁定入口，避免同一父页面重复打开多个临时页面。
- URL 解析失败时，标准播放页显示现有错误状态；用户返回后父页面仍按快照恢复。
- 屏幕旋转或 Activity 重建通过 Intent 参数和现有播放器状态恢复临时页面，不提前恢复父页面。
- 临时页面不主动修改父页面的历史、同步、下一集和详情状态。
- 父页面销毁时不强行恢复已不存在的播放器；既有历史恢复机制负责下次进入。
- 不属于 `PlaybackActivity` 的调用方不启动临时模式，保留明确的失败返回，避免提供无法恢复的伪全屏体验。

## 文件边界

- 修改 `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbVideoPlayback.java`：从 Dialog 入口改为临时播放入口。
- 修改 `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`：增加快照、启动和结果恢复协调。
- 修改 Mobile/Leanback 两套 `VideoActivity.java`：识别临时 Intent，并将临时路径接入标准播放流程。
- 修改 `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java`：复用已实现的隔离解析 API，确保临时路径不停止全局 Source。
- 删除 `TmdbVideoPlayerDialog.java` 及其专用布局，避免保留第二套播放器 UI。
- 更新相关接线测试和恢复测试；不改普通播放器布局的视觉结构。

## 测试与验收

### 自动化测试

- 验证 TMDB 点击只通过临时播放入口，不再打开专用 Dialog。
- 验证临时 Intent 携带 `EXTRA_TRANSIENT_PLAYBACK` 和完整 Launch 参数。
- 验证临时 PUSH 解析使用 `playerContentIsolated()`。
- 验证快照保存媒体、位置和播放/暂停状态，并在结束后恢复。
- 验证临时模式不写历史、不走详情跳转、不触发下一集。
- Mobile 与 Leanback 两个变体分别运行相关单元测试并构建 Debug APK。

### emulator-5558 验收

- 手机详情页：点击相关视频进入标准全屏播放页，返回后恢复原视频。
- TV 详情页：验证遥控器焦点、播放/暂停、快进/快退、设置和返回键。
- 炫彩详情模式：验证详情页内播放器暂停、临时页播放和关闭后的恢复。
- 原视频播放中与原视频暂停中各验证一次，确认恢复状态不同。
- 解析失败、重复点击、旋转/重建和返回边界各验证一次。

## 备选方案与取舍

1. **现有 `VideoActivity` 临时模式（本设计）**：能最大程度复用现有播放器体验，改动中等，恢复逻辑需要严谨测试。
2. **独立全屏播放器 Activity + 抽取共享播放器壳**：隔离最干净，但需要重构当前散落在 `VideoActivity` 的控制和输入逻辑，范围明显更大。
3. **全屏 Dialog + 独立 Media3 控制栏**：实现最快，但控制能力、字幕、手势和 TV 焦点会与正片长期分叉。
4. **当前播放页原地接管媒体**：视觉最连贯，但详情页和播放页入口不一致，且对 P2P/直播会话恢复风险最高。

