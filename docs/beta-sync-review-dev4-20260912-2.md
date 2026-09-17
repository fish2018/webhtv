# dev4 合并 beta 后代码复评记录（2026-09-12）

## 目标

将远端 `origin/beta` 最新提交合并到 `dev4`，复评 `dev4` 尚未进入 `beta` 的全部代码，并完成针对性验证、提交、推送和中文 PR。

## 合并基线

- 合并前 `origin/beta`：`6750b21dc4040f22ef51bff408a1bfb5ad6649d0`
- 合并前本地 HEAD：`a123124c442295fffa8f3087c4fc6ee1ada42fd3`
- 合并方式：`git merge --no-edit origin/beta`
- 合并结果：成功，无冲突。

## 评审范围

相对合并后的 `origin/beta`，当前分支仅保留以下任务差异：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`
- `docs/fix-exo-startup-double-resume.md`

## 评审结论

1. 移动端和电视端的片头片尾查询回调在播放器进入 `Player.STATE_READY` 前不再调用自动跳过，避免其 seek 与 EXO 首次 prepare、历史进度恢复或去广告定位竞争。
2. 移动端和电视端实现保持一致，READY 守卫位于 `applyAutoIntroSkip()` 之前。
3. 合并 beta 后新增的播放器卡顿看门狗等代码未与本任务的起播定位守卫产生冲突。
4. 电视端失焦时释放倍速属于同一已提交变更中的输入状态收口，逻辑局部且不会改变正常持焦播放行为。
5. 未发现需要继续修改的正确性、兼容性或回归问题，复评通过。

## 验证

执行：

`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest`

结果：`BUILD SUCCESSFUL in 33s`，87 个任务中 7 个执行、8 个命中缓存、72 个为最新状态。

同时执行了相对 `origin/beta` 的 `git diff --check`，无空白错误。

## 回滚

- 功能回滚锚点：`recovery/fix-exo-startup-seek-race/20260912191224-a123124c4422`
- 如需撤销本次同步收口，可回退本次任务提交或使用本任务完成时生成的 recovery 标签。
