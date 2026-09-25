# 任务编号和名称
C20260906161136-mpv-duration-recovery-fix: MPV 首次播放时长未显示修复

# 所属分类
MPV 播放器时长恢复 Bug 修复

# 要实现的实际能力
确保手机版 MPV 在首次进入播放时能够正确获取并显示视频总时长，即使 MPV 在初始化过程中未及时发布 duration 属性变更事件。

# 当前项目已有实现
项目中已有时长恢复机制（见提交 0ce6f13e6 和 97a5e3e8a），但仅在特定条件下触发（cachedDurationMs > 0 或 !initialized）。在某些场景下，MPV 首次加载媒体时可能不会立即发布 duration 事件，导致缓存时长保持为初始值（0 或 TIME_UNSET），UI 因而不显示总时长。

# 涉及仓库及完整 commit ID
- 本地仓库：https://github.com/Silent1566/webhtv.git (dev2 分支)
- 上游参考仓库：https://github.com/fish2018/webhtv (main 分支)
- 本次修改基于的本地提交：66b5505282eb3de95538a1eab39ad9196d27ba22
- 相关既有修复：0ce6f13e6d59（fix(mpv): recover missing duration during playback state refresh）
- 相关既有修复：97a5e3e8a59e（fix(mpv): preserve unknown duration and recover timeline on restart）

# 收益
- 手机版 MPV 首次播放时能够正确显示视频总时长。
- 减少用户需要切换播放核心以刷新时长的需要。
- 修改范围窄，仅影响 MPV 播放器的状态刷新逻辑。

# 缺点与风险
- 轻微增加每秒状态刷新的开销（一次额外的属性查询），但仅在未缓存有时长时触发。
- 可能在极少数情况下导致时长抖动（如果 MPV 在同一秒内多次更新 duration），但已有相同判据（timelineDurationMs != cachedDurationMs）可防止无效写入。
- 无已知回归风险，因为该逻辑仅在缺失有时长时尝试恢复。

# 与现有功能的关系
- 与既有时长恢复机制（0ce6f13e6, 97a5e3e8a）互补，覆盖了它们未处理的场景（即 initialized 为 true 但 cachedDurationMs 仍为 <= 0）。
- 不影响 TV 版或 Exo 播放器。
- 与现有的定时状态刷新循环（startStateRefresh / stateRefreshRunnable）集成，无需新增定时器或回调。

# 建议
实施（实施）。该修复是最小化的、风险低的改进，能够直接解决用户报告的手机版 MPV 时长未显示问题。

# 最小实施步骤
1. 修改 `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java` 中的 `refreshPlaybackState` 方法。
2. 将现有时长恢复判断（基于 `cachedDurationMs > 0 || !initialized`）改为 `initialized && cachedDurationMs <= 0`，以便在已初始化但未缓存有时长时尝试查询 MPV 的 duration 属性。
3. 保留现有的 `doublePropertyMs("duration", -1)` 查询和更新逻辑。
4. 编译并运行单元测试（如有）以确保无语法错误。
5. 在模拟器或真机上验证手机版 MPV 首次播放时长显示正常。

# 预计需要的验证
- 编译通过：`./gradlew assembleDebug`（或等效命令）。
- 运行相关单元测试（如 `MpvMainThreadPropertySourceTest`）以确保无回退。
- 在 Android 模拟器（192.168.50.3:5555）上启动应用，选择一个视频，使用 MPV 作为播放核心，进入播放页后确认控制栏显示总时长（非 --:-- 或 0:00）。
- 可选：切换回其他播放核心再切回 MPV，确认时长仍然正确显示（防止回归）。
