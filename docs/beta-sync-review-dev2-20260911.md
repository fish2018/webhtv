# dev2 合并 beta 最新代码与代码复评（2026-09-11）

## Recovery anchor

- 目标：合并远端最新 `beta`，评审合并结果及已提交未推送改动，发现问题则修复、验证并再次复评，最终提交、推送 `dev2`、创建中文 PR 到 `beta`，并再次拉取远端最新代码。
- 任务守卫：`beta-sync-review-dev2-20260911`，模式 `standard`；开始时工作树干净，无受保护的既有脏路径。
- 本地基线：`dev2@68df13503c07b20e68cdac072fa381037f672461`。
- beta 合并目标：`origin/beta@1e7d79ef29fd7568f376fe571bde0bd4cd7c6838`。
- 合并结果：`git merge --no-ff --no-commit origin/beta` 自动完成，无冲突；`MERGE_HEAD` 指向上述 beta 提交。
- 回滚：提交前可使用 `git merge --abort`；提交后使用任务守卫创建的恢复标签回退。
- 当前唯一下一动作：完成最终复评后调用任务守卫 `finish`，随后推送分支和恢复标签、创建中文 PR，并拉取远端最新状态。

## 评审范围与既有覆盖

### beta 增量

- beta 增量包含主题 profile、目录缓存、导入导出安全边界、语义颜色绑定、播放速度选择弹窗、TV 播放加载状态、焦点资源、备份偏好过滤及相关测试和文档。
- 主题导入安全问题已由 `docs/beta-sync-review-20260908.md` 记录修复和验证，包括 JSON 大小与深度限制，以及非公网地址和 IPv4 映射地址过滤。
- 播放速度选择与 TV 列表滚动分别由 `docs/playback-speed-choice-20260908.md` 及后续 `ChoiceDialog` 测试覆盖。
- beta 增量与本地提交的 6 个焦点相关路径没有内容合并冲突；最终合并树相对 `origin/beta` 只保留本地焦点改动。

### 本地已提交未推送改动

本地提交 `68df13503c07b20e68cdac072fa381037f672461` 涉及：

- `HomeActivity.java`
- `VodActivity.java`
- `FolderFragment.java`
- `TypeFragment.java`
- `VodActivityCategoryEdgeTest.java`
- `HomeCategoryNavigationSourceTest.java`

改动为首页和独立点播分类切换增加 generation 校验、精确移除待执行 Runnable、Fragment 隐藏及销毁时失效异步焦点请求，并防止旧页面回调抢占新页面焦点。

## 首轮评审与验证

- 已检查页面切换、分类横向边界、Fragment transaction、隐藏与销毁生命周期、pending content focus、scroll-to-top 回调和 Activity 暂停/销毁清理。
- 未发现越界、空指针、无限回调、旧页面抢焦点或 beta 合并导致本地修复丢失的阻断问题。
- `git diff --cached --check` 通过，未发现冲突标记。
- 定向 Gradle 验证结果为 `BUILD SUCCESSFUL`，耗时 1 分 26 秒，共 114 个 actionable tasks，其中 16 executed、16 from cache、82 up-to-date。
- Leanback `VodActivityCategoryEdgeTest`：3 项通过，0 failures、0 errors、0 skipped。
- Mobile 定向测试：77 项通过，0 failures、0 errors、0 skipped。覆盖首页分类导航、主题 profile/catalog/import/export、主题控制器、播放速度弹窗、ChoiceDialog、播放器 ownership 和 TV loading progress。
- `compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `compileMobileArm64_v8aDebugJavaWithJavac` 均通过。
- 构建仅出现既有 Room query mismatch、deprecated API、unchecked operation 和 32 位 native library 提示，没有新增编译或测试失败。

## 验证后复评

- 验证后重新核对最终索引相对 `origin/beta` 的 6 个本地差异路径。
- generation 在每次新请求和失效操作中更新，异步 holder、滚动及内容焦点回调均校验当前 generation 和 Fragment 可见性。
- `onPause`、`onDestroy`、`onDestroyView`、Fragment 隐藏及类别切换均会使旧请求失效。
- 合并结果保留 beta 的主题、播放速度、加载状态、资源焦点和备份过滤改动，同时保留本地 stale callback 防护。
- 当前未发现可执行阻断问题，复评结论为通过。

## 验证边界

- 未执行真�实电视设备上的逐帧遥控器焦点验证、实际播放网络场景或主题编辑器人工视觉验收。
- 本地证据覆盖源码契约、相关单元测试、Android 资源处理和双端 Java 编译，不将其扩大解释为完整设备验收。

## 后续动作

1. 使用任务守卫原子提交合并结果和本文档，并创建恢复标签。
2. 推送 `dev2` 和新恢复标签。
3. 创建目标为 `beta` 的中文 PR。
4. 再次拉取远端最新代码并核对分支、PR 和工��作树状态。

## 后续拉取与最终复评（2026-09-11）

- 2026-09-11 21:43 CST 已执行 `git fetch --prune origin beta`；当前 `origin/beta` 为完整提交 `be1b02e06b22a4fa2f08c791555536e3e6154c95`，没有发现新的远端 beta 提交。
- 当前 `HEAD` 为完整提交 `3a2b9d39e8bd3314027e5ce8e709c17e75a21236`，其父提交正是 `origin/beta`；`git merge --ff-only origin/beta` 返回 `Already up to date`，无新增合�并、无冲突。
- 复用本文件此前对 beta 增量的评审与验证记录：此前合并提交及其相关 beta 改动均已成为 `origin/beta` 的祖先。本次相对最新 beta 的唯一未合入提交是 `3a2b9d39e8bd3314027e5ce8e709c17e75a21236`（`修复电视直播屏显重影`），实际差异仅为：
  - `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`
  - `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/LiveActivityLayoutTest.java`
- 最终复评�检查 `showControl`/`hideControl` 生命周期：控制栏显示前始终隐藏旧 `mBinding.widget.top`，再由共享 `PlayerOsdController` 显示标题、分辨率和时间；控制栏隐藏时同时隐藏旧栏并关闭共享 OSD；源码中没有其他路径重新显示该旧栏。未发现空指针、状态遗漏或与 beta 最终树的合并问题。
- 定向验证已通过：
  - `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain`：`BUILD SUCCESSFUL`，98 actionable tasks（7 executed、91 up-to-date）。
  - `git diff --check origin/beta...HEAD`：通过。
- 结论：本轮复评通过，无需新增修复；当前任务改动可提交并推送。
