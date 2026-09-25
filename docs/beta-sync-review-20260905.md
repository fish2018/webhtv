# Beta 同步与合并复评

## 目标与范围

- 完成已开始的 `origin/dev1`、`origin/beta` 合并，评审包含已提交未推送内容的最终差异，修复并验证后提交、创建恢复标签、推送 `dev1`，创建或更新中文 beta PR，最后同步远端状态。
- 首个 guard `beta-sync-review-20260905` 已关闭；续用 `beta-sync-latest-20260905`，范围仅为 `Backup.java`、`TmdbDetailActivity.java`、`TmdbDetailActivityLayoutTest.java` 和本文档。没有登记的任务外初始脏路径，不重置索引或工作树。
- 不改变播放器依赖、二进制、弹幕渲染、搜索顺序或缓存 key 格式。

## 恢复证据

- 2026-09-05 23:36，Asia/Shanghai：工作区分支 `dev1`，HEAD 为 `399023ced875b3bdb298afd7b09219c8e0eef57e`。
- `.git/MERGE_HEAD` 为 `eab79fba9251dd6d07283fe149d7ce4f6874554d`（origin/dev1）和 `db44647432ea1ac094298cebca9f535f73dc8202`（origin/beta）；合并尚未提交。
- 当前索引包括触屏优化与播放菜单遮罩。工作树还保留 `Backup.java` 类头恢复、遮罩偏好备份补齐、弹幕请求生命周期修复及测试。
- 上一会话完成了回调线程和主线程双重请求身份检查，但遗漏手动匹配缓存的关键词接线。
- 当前磁盘 `app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.dialog.DanmakuSearchIntentTest.xml` 记录 4 项测试、1 项失败、0 errors；失败断言为 `DanmakuSearchDialog must remember the submitted keyword`。结果时间为 2026-09-05T15:17:06.793Z，是有效 RED 证据，不重复运行。

## 修复与验收

- 两个搜索对话框将本集、站点系列、TMDB 季级三层缓存统一绑定到 `searchIntent.getResultKeyword()`，避免结果返回前编辑输入框污染匹配记忆。
- `DanmakuSearchDialog.search` 读取一次关键词，并向请求与状态对象传递同一值。
- 保留旧请求在后台回调及主线程更新前的失效检查；销毁对话框或再次搜索后旧请求不能覆盖结果。
- 验收：纯状态测试与两个入口接线测试通过；mobile/leanback arm64 聚焦测试和 Java 编译通过；合并差异复评没有未解决阻断项。

## 验证与已知限制

- GREEN：独立 JUnit `DanmakuSearchIntentTest` 4/4，日志 `build/beta-review-junit.log`。
- GREEN：一次 Gradle 调用分别筛选 mobile/leanback arm64 debug 单测，两端各 40/40，均为 0 failures、0 errors；包含两端 Java 编译，`BUILD SUCCESSFUL in 3m 1s`。日志 `build/beta-sync-review-20260905-tests.log`。
- 每端覆盖：`DanmakuApiSourceTest` 2、`BackupPreferenceFilterTest` 10、`DanmakuMatchCacheTest` 8、`PlaySpecTest` 4、`SettingPlaybackOverlayTest` 2、`DanmakuSearchIntentTest` 4、`TouchOptimizationHelperSourceTest` 10。
- 修复后复评：核对相对原合并 beta `db44647432ea1ac094298cebca9f535f73dc8202` 的全部本地生产差异与对应测试，包括三个播放入口的 TMDB 身份传递、对话框返回父层、缓存兼容/隔离、搜索回退、弹幕选择状态、备份恢复及异步生命周期。没有未解决阻断项；当前会话无子代理评审工具，复评由本代理完成。
- 2026-09-05 本轮 fetch 后 beta 更新至 `bc5f9b42e090dd7fd303a56c052618dcb5506047`，新增 `Backup` 类头修复与 TMDB 横向选集居中。先关闭当前合并单元，再在独立 guard 中合入该增量并仅补验受影响范围。
- 旧全量测试报告的两项 `FfmpegVc1SupportTest` 失败属于未改动的 FFmpeg 运行库测试，本任务不更改或规避这些测试，也不把聚焦验证称为全量通过。
- 本轮不重复触屏优化已有的设备验证；历史记录见 `docs/touch-optimization-mode-design.md`，其中最终 APK 开关闭环的限制仍保留。



## dev2 增量复评记录（2026-09-07）

- 本轮基线：`dev2@adeabec2588a81d204dfd608269e3db8c7cdc80e`；拉取到 `origin/beta@cc88e278a8ddc2088a82a68dbf1671e419606a29`；共同祖先为 `912208261e4e342ced009b1a0b71feed4855a01d`。合并使用 `git merge --no-ff --no-commit`，无内容冲突；当前分支此前已提交但未推送的 C4 播放器/依赖变更与 beta 本轮无路径交集，保留不变。
- 初始保护脏路径：5 个 `*.bak20260906*` 文件；均未修改、暂存或提交。beta 的播放器相关删除/供应链变化相对当前 HEAD 均为已存在于 beta 合并祖先之前的本地增量与路径差异，未作为本轮盲目覆盖。
- 首轮审查发现并修复：`gradlew` 可执行位从 `100755` 回退为 `100644`；测试断言将 Leanback 已实现的 `current.detail()` 错判为 `item.detail()`；主题事件测试未考虑 Leanback 的 `UI_SCALE` 中间条件。
- 第二轮敌对审查发现并修复：新增 TV 颜色选择项缺少 DPAD 可聚焦属性和焦点背景；外观页直接以持久化 `PlayerSetting.getSize()` 作为资源数组下标，增加边界归一化后再显示/轮换。
- 验证：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.AdRuleManageDialogLayoutTest --tests com.fongmi.android.tv.ui.dialog.SiteDialogThemeSourceTest --tests com.fongmi.android.tv.ui.adapter.MobileSiteAdapterStyleTest --no-daemon` 通过，`BUILD SUCCESSFUL in 45s`；共 11 项定向测试，失败/错误为 0。`./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon` 通过，`BUILD SUCCESSFUL in 40s`。`git diff --cached --check` 通过，合并无未解决路径。
- 二次最终复评：重新核对 Leanback 外观设置入口、主题选择焦点、UI_SCALE/LANGUAGE/THEME 重建事件、Mobile 站点动态色调色板、站点/分组适配器、配置边界与既有 C4 播放器路径；未发现剩余阻断问题。设备实机主题/遥控器验收未在当前环境取得，不将 JVM/Java 编译结果扩大为完整设备验收。

## 新 beta 增量与续作纠正

- 首个合并单元已提交为 `5a479aa6962d3082dff35b4f330ef3232ad96961`，恢复标签为 `recovery/beta-sync-review-20260905/20260905154625-5a479aa6962d`。上文双端各 40/40 的结果仅对应该单元。
- 续作接收时，guard `beta-sync-latest-20260905` 已在干净的 `5a479aa6962d3082dff35b4f330ef3232ad96961` 上启动，`MERGE_HEAD` 为 `bc5f9b42e090dd7fd303a56c052618dcb5506047`。三处现有改动均属于此 guard，不是任务外脏文件。
- 冲突解析保留本地 `playback_overlay_enabled` 设置备份键及完整 `Backup` 类头；TMDB 与测试沿用 beta 的横向列表焦点居中增量，不改变网格导航、播放器依赖或弹幕逻辑。
- 2026-09-06 00:18，续作代理错误地在未补验时手工创建合并提交 `b077fd23adf0478a946cbdfa62c0253e8c031cb6`。其提交消息误用了上一单元的测试结果，且 RTK 输出提示被污染到 `Backup.java` 首行；该提交不能作为已验证恢复点。
- 同一错误操作创建的本地标签 `recovery/beta-sync-review-20260905/-` 不作为恢复点，不推送。采用前向修复，不重写合并提交或其他历史；只移除污染行，并将仍活动的 guard 基线对齐到本会话创建的 `b077fd23adf0478a946cbdfa62c0253e8c031cb6`，其范围、初始脏文件及保护记录保持不变。
- 待验证：一次 Mobile/Leanback Arm64 定向 `BackupPreferenceFilterTest` 与 `TmdbDetailActivityLayoutTest` 及其 Java 编译。不重复弹幕、触屏设备或 native 测试。

## Recovery Anchor

- 目标及验收：修复已提交关键词绑定，验证并闭环当前 beta 合并、提交、恢复标签、推送与中文 PR。
- 当前阶段：首个单元已关闭；第二个合并提交存在上述续作错误，正在前向修复，尚未完成受影响范围补验。
- 保护：旧 Orca 会话和 transcript 只读；任务外路径不改动；不要再次启动合并或重建 guard。
- 回滚锚点：已验证的 `5a479aa6962d3082dff35b4f330ef3232ad96961` 及其恢复标签。`b077fd23adf0478a946cbdfa62c0253e8c031cb6` 不能作为独立恢复点；回退合并须明确主线后另行批准。
- 未决事项：补验、前向修复提交与正确恢复标签、推送及中文 PR；全量 FFmpeg 运行库测试和历史设备补验限制仍如上。
- 下一步：执行一次受影响的双端定向测试及 Java 编译，记录实际结果。

## 修复记录 (2026-09-06)

- 修复：移除 HomeActivity 中对 RefreshEvent.Type.THEME 的冗余订阅。
