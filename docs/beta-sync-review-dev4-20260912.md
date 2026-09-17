# dev4 合并 beta 与代码复评记录（2026-09-12）

## 目标

将远端 `origin/beta` 最新代码合并到 `dev4`，覆盖评审当前分支相对 beta 的全部修改及此前已提交但未推送的提交；完成针对性验证和验证后复评，随后提交、推送并创建合入 beta 的中文 PR，最后再次拉取远端最新状态。

## 合并基线

- 合并前 HEAD：`b6b18ac1dd2f248c3001b1802ac285622620b6fa`
- 合并时远端 beta：`ccd608c503`
- 合并结果：使用 Git `ort` 策略成功完成，无冲突。
- 合并后当前分支相对 beta 的有效功能提交：
  - `667992b22e`：统一光影剧幕分集客串演员图片圆角行为。
  - `6a002d29f7`：统一光影剧幕分集客串演员横向卡片布局。
  - `b6b18ac1dd`：稳定移动端分集详情底部滚动边界。

## 评审范围

- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java`
- `app/src/main/res/layout/dialog_episode_detail.xml`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialogThemeTest.java`

同时逐提交检查了上述三个功能提交，并检查合并后相对 `origin/beta` 的完整差异。

## 第一轮评审结论

- 影院风格下，移动端与 TV 端分集客串演员列表均复用外层演员栏的横向卡片布局，非影院风格保持原有纵向卡片行为。
- TV 端两个客串演员数据入口均同步设置影院样式、行高及容器高度，没有发现只覆盖单一路径的问题。
- 移动端移除冗余关闭按钮后仍保留系统返回和点击外部关闭方式，避免固定底部按钮挤压滚动区域。
- 横向图片列表关闭嵌套滚动与过度滚动，父滚动容器关闭过度滚动并增加底部安全间距，处理底部客串演员卡片滚动边界。
- 新增测试覆盖影院风格卡片几何、移动��端与 TV 端接线、关闭按钮移除以及底部滚动边界。
- `git diff --check origin/beta...HEAD` 未发现空白符错误。
- 未发现需要修改的正确性、兼容性或回归问题。

## 验证

执行构建前已检查实际 Gradle、Kotlin、AAPT2、Ninja 和 CMake 构建进程；排除仅驻留的 Gradle Daemon 后，未发现其他实际打包任务。

针对性验证命令：

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.ui.dialog.EpisodeDetailDialogThemeTest'
```

结果：`BUILD SUCCESSFUL`，共 87 个任务，其中 6 个执行、81 个为最新状态。

构建输出中仅有项目既存的 32 位原生库、弃用 API 和未检查操作警告，本次改动未新增相关问题。

## 验证后第二轮复评

验证通过后再次检查 `origin/beta...HEAD` 的完整四文件差异及 `git diff --check`：

- 差异仍严格限定于分集详情影院风格客串演员卡片和移动端滚动边界。
- 合并 beta 后没有产生冲突残留、重复实现或行为覆盖。
- 测试断言与实际源集路径、变体及运行时代码一致。
- 未发现新的阻断问题，复评通过，无需追加代码修复。

## 回滚

若合入后发现问题，可按功能提交分别回滚 `b6b18ac1dd`、`6a002d29f7`、`667992b22e`；本次合并和交付提交另有任务恢复标签用于整体回退。
