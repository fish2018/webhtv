# TV 短显按钮无响应修复

## Recovery anchor

- 目标：修复 TV 版（leanback flavor）「短显」按钮获得焦点后按确认键无响应的问题。
- 验收：Leanback `VideoActivity.initEvent()` 包含 `mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay())`，控制栏确认键可沿 Android View 点击链执行既有动作；回归测试覆盖关键按钮，Leanback Java 编译通过。
- 根因证据：commit `7dc58af1b0bb28818b23f43748c3ac67f76e0449`（2026-09-13 05:57，C4 合并）将 Leanback `VideoActivity.initEvent()` 中原有控制栏监听块删除（包括 `mBinding.shortDisplay.setOnClickListener`），导致按钮虽有布局和方法但没有点击事件绑定。
- 已知修复：commit `015be8734cf561cf549a9998744809b7c88160fa`（2026-09-14 10:48，fix(tv): restore player control confirm actions）已经修复了此问题，但该提交不在当前 HEAD（`0b43e10040`）的祖先链上，因此电视版最新测试版仍受影响。
- 当前修改：将该修复 cherry-pick 到当前 HEAD（`0b43e10040`），恢复丢失的 TV 控制栏监听（包括短显按钮的点击绑定），并恢复焦点滚动、选集/片头跳过初始化接线；新增 `PlayerControlFocusIntegrationTest.leanbackPlaybackControlButtonsKeepConfirmActionsWired` 防止再次回归。
- 下一步：提交并创建恢复标签。
- 已完成证据：定向测试 `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --cached --check` 通过。
- 完成状态：修复提交 `cf1a3eef83` 已创建，恢复标签 `recovery/tv-short-display-dead-20260914/20260914143520-cf1a3eef83` 已创建，任务完成。
