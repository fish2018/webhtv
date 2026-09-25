# MPV 音频切换 Surface 等待修复

## Recovery anchor（2026-09-21）

- Objective / acceptance：音乐 MPV -> EXO / EXO -> MPV 来回切换不得因初始视频 Surface gate 卡住；纯音频且不需要 OSD Surface 时，MPV 必须直接 load，视频、字幕和 ISO 的既有等待合同保持不变。
- Evidence：用户日志 `webhtv-debug-log (21).txt` 中 trace `p-6kwdkf-19` 于 `13:53:02.797` 记录 `initial surface gate waiting before load`，直到 15 秒首帧超时后于 `13:53:18.278` 才真正 load；旧 MPV 会话因此等待约 15.8 秒。
- Scope：`MpvPlayer`、纯策略 `MpvSurfaceLoadPolicy`、策略单测和本文；不修改 MPV native、Exo direct fallback、播放器选择或缓存策略。
- Verification：`MpvSurfaceLoadPolicyTest` 与 `MpvSurfaceLifecycleSourceTest` 通过；mobile/arm64-v8a Debug 覆盖安装成功；本地 MP3 以 MPV 启动时直接 `start-file -> file-loaded`，没有 `initial surface gate waiting`。
- Rollback：回滚本任务的原子提交即可恢复原 Surface gate；不涉及二进制或依赖。

## 根因与修法

MPV 初始 load 使用 `MpvPlayer.isSurfaceReadyForLoad()` 防止 Android 尚未创建 `SurfaceView` 时过早打开输出。原逻辑对所有可见 `SurfaceView` 一视同仁，因此纯音频播放器已经不需要视频 Surface 时，仍会等待未就绪 Surface；首帧超时后重建才恢复。

新增 `MpvSurfaceLoadPolicy.shouldWaitForVideoSurface()`：仅在“音频媒体且 `requiresOsdSurface()==false`”时跳过视频 Surface 等待。其余条件继续保留：视频、`mediacodec_embed` 和 ISO/OSD 场景不改变。`MpvPlayer` 以 `audio/*` MIME 或 `AudioUtil.isAudioUrl()` 判定本地/直连音频。

## 验证记录

- `:app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvSurfaceLoadPolicyTest --tests androidx.media3.mpvplayer.MpvSurfaceLifecycleSourceTest`：`BUILD SUCCESSFUL`。
- `scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5557`：mobile/arm64-v8a Debug 构建并覆盖安装成功。
- 运行证据：本地 60 秒 MP3 以 MPV 启动，trace `p-1vsvjt-1` 的 `mpv: load`、`event=start-file`、`event=file-loaded` 连续完成，当前 trace 无 `initial surface gate waiting` 日志。

## 边界

设备端测试的 Surface 已处于 attached=true，因此在 dev2 上没有强制复现“Surface 未就绪”分支；该分支由纯策略三态测试覆盖。用户 Sony 设备仍需用原本音乐片源做 MPV/EXO 来回切换确认，重点检查日志不再出现 `initial surface gate waiting before load`。
