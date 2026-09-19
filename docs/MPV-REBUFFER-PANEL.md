# MPV 播放参数面板重缓冲计数修复

- 目标：MPV 重缓冲时，手机/电视共用的播放参数面板显示当前播放的重缓冲次数和持续时间；保留原有掉帧来源及 Exo 显示行为。
- 范围：`quick-fix`，guard `MPV-REBUFFER-PANEL`；仅 `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` 和本文。基线 `a18c409b6d5a3c9d1e64edd673cd672daf448ac1`，分支 `feature/mpv-dv7-fel`；保护预存 `app/.cxx/` 的 70 个文件。
- 时间：2026-09-15 10:48 Asia/Shanghai 开始修改；约 1 分钟修改、1 分钟打包和记录，目标 10:51。用户要求监听正在播放的手机，并沿用用户自行功能测试的要求；不为验证制造卡顿，不中断当前播放。

## 根因与现场证据

- `PlayerOsdController.getDiagnostics` 对 MPV 使用 `PlaybackAnalyticsListener.Snapshot.empty()`，却从这个 Exo 快照读取重缓冲次数与时长，因此恒为 0。
- `PlayerManager.getRebufferCount/getRebufferTotalMs` 已提供当前播放器的共用状态计数，并合入 MPV 原盘导航独立计数；正在进行的重缓冲时间由单调时钟累计。`PlaybackBufferingTracker.update` 在非首开缓冲开始时递增，不依赖掉帧。
- 掉帧数据独立来自 `MpvPlayer.getDroppedFrames`，读取 `decoder-frame-drop-count` 与 `frame-drop-count`，没有与重缓冲计数交换。
- 用户当前手机 vivo V2453A（`10CF6H1D2L0009S`）原有日志，trace `p-1737sha-1`：手机日志时间 10:44:05.997 重缓冲开始 count=9；10:44:18.145 开始 count=10；10:44:24.164 恢复后 count=10、total=76383ms。另一路帧统计 dec=0、out=46。现场证明内部计数正常、面板数据源错误。
- 计数关系：第 9 次缓冲中 out=37（10:44:11.016），恢复后 out=43（10:44:16.017）；第 10 次缓冲中 out=43（10:44:23.167），恢复后 out=46（10:44:29.197）。两种计数独立，日志中的掉帧增量出现在恢复之后；恢复时追赶音视频时钟可能丢弃迟到帧，但这些聚合日志不证明每帧的具体丢弃原因。
- 只读证据：`/private/tmp/webhtv-rebuffer-panel-20260915-current.log`；短时实时监听 `/private/tmp/webhtv-rebuffer-panel-20260915-live.log`，取得证据后已停止监听，未操作播放。

## 最小修改与交付

- MPV 面板改用现有 `PlayerManager` 次数/时长 getter；保留 Exo 原快照、掉帧来源、格式及刷新周期。手机和电视共用同一控制器。
- 这是既定统计接口的显示接线修复，不改播放、缓存、解码、JNI、依赖或上游代码；没有新增采样或原生查询。不再扩大研究或改动计数器。
- 最便宜决定性证据：已取得当前播放的真实缓冲开始/结束日志；随后只运行一次手机 arm64 debug 打包。按用户要求不运行自动化或功能测试。
- 打包已完成：`:app:assembleMobileArm64_v8aDebug` 一次通过（23 秒，`--offline`，沿用 `/private/tmp/webhtv-libass-stage1/isolate-cxx.gradle` 保护预存 CMake 目录）；未运行测试。完整日志 `/private/tmp/webhtv-rebuffer-panel-20260915-build.log`，APK `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`。
- 用户随后明确要求安装：直接使用上述现成 APK，未重新构建。安装助手已覆盖安装到 vivo V2453A，确认安装包指纹更新并启动 `com.fongmi.android.tv`，退出码 0；日志 `/private/tmp/webhtv-rebuffer-panel-20260915-install.log`。未运行功能测试。
- 回滚：恢复本次控制器改动即可；原有统计、配置与设备数据保持原状。

## Recovery anchor

- 实现：MPV 面板的数据来源已修正；手机 arm64 debug APK 已构建成功。
- 当前手机已按用户要求覆盖安装修正版并启动应用，用户已实测确认“可以了，打个tag”。
- 用户验收：修正版通过用户实测；不追加构建、测试或设备检查，立即闭环。
- 唯一下一步：执行原 guard finish 原子提交并创建本地恢复 tag，不推送远端。
