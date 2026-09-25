# C11：合并 origin/beta 到 dev3 并评审修复

## Recovery anchor

- 目标：将最新 `origin/beta` 合入 `dev3`，评审合并引入的改动，修复缺陷并验证通过，然后提交、推送、向 beta 发起 PR。
- 第一父基线：`dev3@9fc6ca2457883ef2e846b64f7c6119cc3694fb79`。
- beta 目标：`origin/beta@a90be6678a`。
- 合并基线：`97e980c8bda8af2187ac7e678ca59d5c78dbd40e`。
- 任务守卫：`C11-beta-sync`，模式 `upstream`，范围 `app/**`、`docs/**`、`scripts/**`、`third_party/**`、`.gitignore`。
- 任务开始前脏路径：`.claude/worktrees/agent-ac882912b5956f97d/`（受守卫保护，不进提交）。
- 回滚锚点：`recovery/beta-sync-20260904-1526-pre/20260904-072657`（合并前 HEAD）。
- 排除项：不改 FFmpeg/Media3/MPV/JNI 源码与二进制所有权；`third_party` 下的变更全部由 beta 带入，本任务未手工改动。

## 合并结果

`git merge --no-ff origin/beta` 自动合并成功，**零冲突**，83 个文件变更。

本地两个未推送提交（`5a1e4f0f76`、`9fc6ca2457`：详情缓存三道挡板）完整保留。已核实 beta 未触及
`SiteApi.java`、`CatWebEvent.java`、`CatAction.java`、`VodDetailCache.java`，无静默覆盖。

冲突标记扫描与 `git diff --cached --check` 均通过。已删除类 `com.fongmi.android.tv.update.GithubProxy`
在全仓无残留引用（12 处调用点全部指向 `utils.GithubProxy`）。

## 评审发现与修复

### 1. mobile 加速源列表首次点击需先抢焦点（本次合并引入）

`app/src/mobile/res/layout/adapter_github_proxy.xml`

beta 的 `55a6ed3531` 给 `@id/text` 与 `@id/remove` 加了 `android:focusable="true"`，
后续 `6d8e97763c` 只删掉 `focusableInTouchMode`。这与本地 `d3f2fd31b4` 立的契约冲突——
`AboutDialogLayoutTest.mobileGithubProxyActionsDoNotRequireFocusBeforeClick` 断言手机端不得
可聚焦，因为首次点击应直接生效而非先抢焦点。`GithubProxyAdapter` 用 `setOnClickListener`
绑定，触控路径不需要 `focusable`。

修复：移除两处 `android:focusable="true"`，保留 main（电视）布局的可聚焦属性不变。

### 2. 迁移把刚合并出的代理列表抹成一条

`app/src/main/java/com/fongmi/android/tv/setting/Setting.java`

`migrateLegacyGithubProxy()` 先 `addSources` 合出完整列表，末尾又
`if (!hadGlobalSources) putGithubProxy(url)` 用单个 URL 覆盖回去。旧版用户选了 `ghfast`
升级后，5 个内置源被抹成只剩一条。

同时 `legacyGithubProxySources(String selectedUrl)` 完全忽略入参，用户当年选中的地址
没有排到列表首位（即生效源）。

修复：`selectedUrl` 置于候选源首位后并入，去掉覆盖那一行；方法改用 `List` 拼装，
避免自填地址为空时 `String.join` 产生空元素。

### 3. 迁移存在丢写窗口

同文件。`migrateLegacyGithubProxy()` 由 `getGithubProxy()`/`getGithubProxyMode()` 两个 getter
调用，先读后删旧键，无同步。「关于」对话框（主线程）与 `Task.submitLarge` 的探测线程可能
同时进入，两边都看到旧键存在，后写的 `putGithubProxy` 盖掉前一次已扩好的列表。
窗口只有升级后第一次读，但那是唯一一次机会。

修复：方法改为 `private static synchronized`。

### 4. 备份丢弃迁移所依赖的旧键

`app/src/main/java/com/fongmi/android/tv/bean/Backup.java`

beta 把 `update_github_proxy`、`update_github_proxy_url`、`update_github_proxy_mode` 从
`isAppPref` 白名单移除。这三个键虽已废弃，但正是迁移的输入：从旧版备份恢复到新版时它们
不再落盘，`migrateLegacyGithubProxy()` 永不触发，用户的代理选择被丢弃而非迁移。
代价仅为三个死键随备份走。

修复：恢复三个旧键，并同步把 `BackupPreferenceFilterTest` 的断言改回 `assertTrue`
（beta 侧连同断言一起改成了 `assertFalse`）。

### 5. 段落身份跨 provider 归一导致片尾永久失效

`app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java`

合并新增的别名归一把 TheIntroDB 的 `credits#0` 折成 `outro`，与 IntroDB 的 `outro`
撞成同一个 `OUTRO|outro`。当两家片尾边界差异大到服务层 `overlaps()` 判为不同段
（起点差 > 3s 且重叠 < 60%）时，去重不合并、计划里保留两段；自动跳过其中一段后写入
`skipped`，另一段被 `isSegmentHandled` 永久吞掉，用户再也跳不到真正的片尾。

修复：id 保留 provider 维度（`kind|provider|identity`）。别名归一只消解同一家内部的
字段名差异；同段合并交给服务层 `addDeduped`/`overlaps` 按时间轴判断，那里才有边界信息。
新增 `IntroSkipPlaybackStateTest.differentProvidersDoNotShareSegmentIdentity` 钉住该契约。

### 6. 更新设置弹窗电视端焦点死路（本次合并引入）

`app/src/main/java/com/fongmi/android/tv/ui/dialog/UpdateSettingsDialog.java`

GitHub 代理面板从该弹窗移除后，`focusPrimary` 仍无条件 `binding.ociMirror.requestFocus()`。
GitHub 标签下 `ociPanel` 是 `GONE`，对隐藏视图 `requestFocus` 恒返回 false，遥控从标签条
向下走不动，保存按钮再也聚焦不到。`focusLastControl` 同理，从保存按钮向上回不到标签条。

修复：GitHub 源时 `focusPrimary` 跳到保存按钮、`focusLastControl` 回到标签条。

## 评审为 clean 的区域

- `utils/GithubProxy` 的 `apply`/`config`/`sources` 语义、`GithubProxyAdapter` 的
  `ConcurrentHashMap` 用法、`AboutDialog.probeLatency` 的线程编排（工作在
  `Task.submitLarge`，结果经 `App.post` 回主线程，不阻塞 UI）。
- `IntroSkipService` 的锁条带索引、`safeAdd` 溢出哨兵、`integer()` 夹取、缓存身份与替换语义。
- `IntroSkipPlayback` 的 `beginConfirmation`/`cancelConfirmation`/`completeConfirmation`
  取消流与 `reset()` 顺序。
- mobile/leanback `advanceEpisode`/`checkPrev` 的 revPlay offset 方向与提示文案映射自洽。
- `SearchSourceVisibility.shouldShow(boolean)` 签名收窄，两个调用点与四个测试一致。

## 未纳入本任务的既有问题

- `hasNextEpisode()`（mobile:10466、leanback:10723）未随 revPlay 取反，但全仓无调用方，
  是合并前既有的死代码，不属本次范围。
- `ReaderHistory.find()` 对未打 `mediaType="reader"` 的旧行返回 null，属 beta 侧
  `b235cee740` 的有意取舍（"宁可让旧进度失效"），会让既有阅读进度一次性重置，建议在
  发布说明中提及。
- `FfmpegVc1SupportTest` 2 项失败为 C10 上游二进制对齐的已知取舍，见
  `docs/pull-merge-beta-20260904.md`，与本次合并无关。

## 验证

- `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：BUILD SUCCESSFUL。
- 修复前基线 `:app:testMobileArm64_v8aDebugUnitTest`：4123 项 3 失败
  （`AboutDialogLayoutTest.mobileGithubProxyActionsDoNotRequireFocusBeforeClick` 为合并引入，
  `FfmpegVc1SupportTest` 2 项为已知 C10）。
- 修复后 `:app:testMobileArm64_v8aDebugUnitTest`：**4124 项 2 失败**，仅剩
  `FfmpegVc1SupportTest`。合并引入的失败已消除；用例总数 +1 即新增的
  `differentProvidersDoNotShareSegmentIdentity`，该用例通过。
- 修复后 `:app:testLeanbackArm64_v8aDebugUnitTest`：**3311 项 2 失败**，同为
  `FfmpegVc1SupportTest`。
- `git diff --cached --check` 通过；全仓无冲突标记残留。
- 已知失败归属：`FfmpegVc1SupportTest` 属 C10 上游二进制对齐取舍
  （`docs/pull-merge-beta-20260904.md`），该测试文件与被测路径均未被本次合并或本次修复触及。
