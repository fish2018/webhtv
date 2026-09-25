# C19：远端 beta 合并与 TMDB 路由遥控修复评审

## 目标

合并远端 `beta` 最新代码，评审本地已提交未推送改动；发现问题修复并验证，直到二次评审通过后原子提交、推送并创建中文 PR。

## 基线

- 分支：`dev1`
- 远端目标：`origin/beta` / `4b8e6bb8084dbb0d683d9488b4b6a932c0b816c3`
- 合并提交：`d5065df98063b52c1e0c359e9025d30f50ec359a`
- 恢复标签：`recovery/C19-beta-sync-review-dev1-20260924/20260924161713-d5065df98063`

## 评审结论

远端 beta 合并无冲突。本地待 PR 差异集中在 TMDB API/图片来源路由选择，既有实机证据覆盖自动打开、选择和确认。

二次复查发现一个真实缺陷：`wireConfigDialogFocus()` 对路由输入调用 `wireTextDpadFocus()` 时覆盖了 `setupRouteDropdown()` 安装的 Enter/中键监听。结果是用户从语言输入按 Down 聚焦 API 路由后，自动弹出的选择器被关闭，之后按 Enter/中键无法再次打开。

修复为新增 `wireRouteDpadFocus()`：同一个监听器同时处理 Enter/中键激活、Up/Down 焦点导航，避免监听器互相覆盖；测试同步锁定路由焦点接线必须保留激活处理。

## 验证计划

- `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.TmdbSourceDialogInflationContractTest`
- `scripts/build_arm64_debug_install.sh` 覆盖安装到 `192.168.50.3:5555`
- 遥控场景：从语言输入按 Down 聚焦 API 路由并关闭自动弹窗后，按 Enter/中键能重新打开；选择后输入文本更新，Up/Down 导航不被破坏。

## 验证结果

- 2026-09-24 16:35–16:45：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.TmdbSourceDialogInflationContractTest --no-daemon` 通过，2 个测试，构建成功。
- 2026-09-24 16:47–16:49：`scripts/build_arm64_debug_install.sh` 通过，构建 mobile/arm64-v8a Debug APK 并覆盖安装到 `192.168.50.3:5555`，未卸载既有包。
- 2026-09-24 16:50–16:55：实机复测通过。从语言输入按 Down 聚焦 API 线路时自动打开选择器；Back 关闭后 API 输入保持焦点，按 Enter 能重新打开选择器；从 API 线路按 Down 能进入图片线路并自动打开对应选择器。
- 2026-09-24 16:56：`task_guard.sh check` 与 `git diff --check` 通过；二次评审确认同一监听器同时保留激活键与 Up/Down 导航，无新的作用域或生命周期问题。
- 实机验证产生的 14 个临时 XML 已清理，未纳入提交；任务开始前已有的 18 个根目录验证文件保持原状并受 guard 保护。

## 状态

- 2026-09-24：二次评审与验证通过；待原子提交。
