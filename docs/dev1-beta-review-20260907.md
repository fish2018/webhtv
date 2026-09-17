# dev1 合并 beta 与代码评审（2026-09-07）

## 范围

- 基线：`origin/beta` 的 `cc88e278a8ddc2088a82a68dbf1671e419606a29`
- 待合入提交：`01ee74c595fd9e6b2fdb7e758274c2423781fa00`
- 保护的既有脏路径：`gradlew`（仅工作树执行位变化，不纳入提交）

## 合并与评审结论

- 合并 `origin/beta` 无内容冲突；`beta` 新增的 PR #218 合并提交只补齐分支历史，合并后代码树与待评审提交的代码树一致。
- 已进入 `beta` 且未与本次 ABI 修复冲突的站点弹窗主题改动不重复评审。
- 首轮评审发现：下载选择会规范化 ABI 名称，但安装后记录版本的 `displayVersion()` 仍做原始字符串比较，可能出现已正确选择下载包却记录回退版本的情况。
- 修复：`displayVersion()` 复用统一的 ABI 下载选择逻辑，避免两套匹配规则漂移。
- 二轮评审覆盖安装入口、安装实现、详情页版本、列表版本/大小显示、ABI 规范化及 fallback 路径；未发现新的阻塞问题。

## 验证

- `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.lab.LabEnvTest`：通过。
- `:app:testMobileArmeabi_v7aDebugUnitTest --tests com.fongmi.android.tv.lab.LabEnvTest`：通过。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：通过。
- `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac`：通过。
- `git diff --check`：通过。
- 一次组合 Mobile 单测命令还执行了 4186 个既有测试，其中 4 个与本任务无关的历史测试失败：`AdRuleManageDialogLayoutTest`、`SiteDialogThemeSourceTest`、`FfmpegVc1SupportTest` 两项；本任务 `LabEnvTest` 未失败，未扩大范围修复。

## 复核结论

通过。ABI 选择、下载匹配、安装、版本显示、更新判断和错误提示共用同一套规范化选择规则；当前 APK flavor 优先于设备 ABI，未知架构和缺失下载包均不会静默回退到首个包或 arm64。

## 回滚

回滚本任务最终提交或使用对应的 `recovery/dev1-beta-review-20260907/*` 本地恢复标签。
