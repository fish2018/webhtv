# C22 dev2 beta 合并评审

## 目标
- 合并远端 `beta` 最新代码，评审并保留本地未推送改动；确保远端已移除/回退的内容不被重新提交。
- 验证通过后提交、推送 `dev2`，并创建到 `beta` 的中文 PR；只创建，不合并。

## 合并记录
- 基线：本地 `3200f6bb01866bc77b1d2875d3e1e6eac04e19e9`，远端 `origin/beta` 为 `2d21687ad51552ec5f002bafb061a447ae14a436`。
- 已创建合并提交 `c32ff7872c536fe789c99bd41d3adc749e6d573d`，无冲突。
- 相对 `origin/beta` 的净改动只有移动端追更卡片操作按钮布局相关 4 个文件；`ConfigSyncPolicy` 与点播/直播同步改动均完整来自远端 beta，未引入被远端剔除的主题系统文件。

## 评审结论
- 第一轮审查确认：追更卡片改为“海报/信息区 + 下方操作区”的垂直结构；操作区继续使用 `FlexboxLayout`，`flexWrap="wrap"` 支持窄屏自动换行。
- 每个可见按钮使用相同的 `following_action_basis` 百分比宽度并设置 `layout_flexGrow="0"`：普通手机为 `30%`，`sw600dp` 为 `18%`，避免行内剩余空间被单个按钮拉伸。
- 所有既有 ID、点击监听和可见性逻辑保持不变；`nextSeason` 与 `read` 仍由 `FollowingAdapter` 控制隐藏/显示。
- `error` 文本仍位于信息区，操作区在 `error` 之后，符合“错误提示不参与操作按钮”的布局意图；长文本按钮已有单行省略，避免撑破行宽。
- 第二轮复评确认合并树无额外脏文件，未恢复远端已回退的主题系统路径。

## 最佳实践依据
- AndroidX `FlexboxLayout` 官方 README（2026-09-25 读取）：`flexWrap` 支持换行；`justifyContent` 控制每行主轴对齐；`layout_flexBasisPercent` 设置百分比主轴基准；`layout_flexGrow` 控制剩余空间扩展。
- 决策：保留 `wrap + flex_start` 与固定百分比基准。相比 `space_evenly`/`center`，该方案不因隐藏按钮导致少量按钮被拉伸或错位，并能自然换行；相比不改，窄屏操作按钮不再被横向滚动容器裁切。

## 验证
- `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.FollowingUiSourceTest`：13/13 通过。
- Python ElementTree 解析 `item_following.xml` 与两份 fraction 资源：全部通过；`git diff --check` 通过。
- `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5557` 生成的 arm64 debug APK 已以覆盖安装方式安装成功（未卸载现有包）。
- 1920×1080 设备场景：5 个可见操作按钮按 328px 等宽连续排布，间隔 11px；“标记已读”隐藏后剩余 5 个按钮仍等宽；滚动后文本和按钮无裁切。
- 720×1280 窄屏场景：每行 3 个按钮，宽度均为 187px，并自动换行到下一行；无按钮或文本裁切。设备分辨率已恢复为 1920×1080。
- 构建后已执行 `./gradlew --stop` 清理本任务 Gradle daemon。

## 恢复锚点
- 当前状态：合并完成，第一轮与第二轮代码评审通过，目标验证已完成。
- 下一步：用 task guard 提交任务评审文档，创建 recovery tag，推送 `dev2`，创建到 `beta` 的中文 PR；不合并 PR。
