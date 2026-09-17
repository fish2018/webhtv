# 播放器倍速弹窗直选

## 目标与范围

- 将倍速短按的循环切换改为与播放核心一致的选项弹窗，保留倍速功能，不替换画质入口。
- quick-fix：只改同一 app 模块的六处短按入口、共用弹窗及定向测试；不改播放核心、依赖、资源和二进制。
- 起点：`dev3` / `4d42cc34c8a5ba74bed68455c5ab1c9761fd14ad`；初始工作区干净。

## 设计与验收

- 当前六处入口调用 `PlayerManager.addSpeed()`，用户只能循环选择；画质已有单选弹窗，播放核心复用 `ChoiceDialog.showSingleNoCancel`。
- 复用项目既有设计，属于局部交互修复，不引入新框架或播放契约，不需要上游设计研究。
- 共用 `PlaybackSpeedDialog` 使用与现有循环相同的十档：0.5、0.75、1、1.25、1.5、1.75、2、2.5、3、5。
- 当前为滑块或遥控器微调值时，将该值按序补入并选中；不强行改变当前倍速。打开/返回不写倍速，点击某档立即应用并关闭。
- 手机/电视点播、TMDB 内嵌播放器、直播页中的点播、电视投屏入口使用同一弹窗；保持各入口原有的剧集/默认倍速记忆规则。
- 保留长按切换、长按临时加速、遥控器上下键微调及设置页滑块；不改变真正直播流的倍速限制。
- 回调执行前重新检查服务与播放所有权，避免延迟选择触碰已释放/非当前播放器。

## 验证与回滚

- 待执行：`PlaybackSpeedDialogTest`、`PlaybackSpeedSyncTest` 定向单测，Mobile/Leanback arm64 Java 编译，共用弹窗设备点击验证。
- 回滚：撤销本任务单个提交即可；最终提交及 annotated recovery tag 由 task guard 创建，不推送。

## Recovery anchor

- 已完成：六个短按入口、共用单选弹窗及定向测试已完成；`PlaybackSpeedDialogTest`、`PlaybackSpeedSyncTest` 通过；Mobile arm64 APK 构建成功；Leanback arm64 Java 编译成功；`git diff --check` 通过。
- 真机证据（2026-09-09，Asia/Shanghai，设备 `192.168.50.3:5557` / SM-N9700）：WebHTV `VideoActivity` 前台播放真实视频流。控制栏短按 `1x` 打开“播放倍速”单选弹窗，截图确认十档（`0.50x` 至 `5.00x`）且当前 `1.00x` 高亮；按返回后诊断面板仍显示可支持/当前 `1.00x`，未改速；重新打开并直选 `1.25x` 后控制栏显示 `1.25x`，logcat 记录 `requested=1.250 actual=1.250` 及 `PlaybackParameters(speed=1.25)`。全程未见 WebHTV `FATAL EXCEPTION`。`uiautomator dump` 因视频 Surface 持续刷新无法进入 idle，但不影响截图、前台窗口、播放器诊断和日志证据。
- 当前风险：未发现本任务范围内的代码回归；未改变长按、遥控器微调、设置滑块、倍速记忆或直播限制路径。
- 唯一下一步：执行 `task_guard.sh finish` 原子提交并创建本地 recovery tag，不推送。
