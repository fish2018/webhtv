# dev2 合并 beta 最新代码与代码复评（2026-09-12）

## Recovery anchor

- **目标：** 拉取远端最新 `beta`，合并到 `dev2`，评审 beta 增量及已提交未推送改动；发现问题时最小修复并验证、再次复评，最终提交、推送 `dev2`、创建中文 PR 到 `beta`，并再次拉取远端最新代码。
- **当前恢复锚点时间：** 2026-09-12 18:10 CST（Asia/Shanghai，UTC+08:00）。
- **当前任务守卫：** `beta-sync-review-dev2-20260912-followup`，模式 `standard`；启动时工作树干净，无受保护的既有脏路径；范围为 `FFMPEG_MODE_SWITCH.md`、`app`、`docs`。
- **本地基线：** `dev2@20dcf312452c7ae95bfe0fd9df85b15d5890bd50`，即此前已提交但尚未推送的缓冲卡死恢复改动。
- **远端 beta：** 已执行 `git fetch origin beta dev2`，目标为 `origin/beta@07fb0b5e13ac56a5cf234fd684ed2932ead4b91c`。
- **合并结果：** `git merge --no-ff --no-commit origin/beta` 自动完成，无冲突；当前合并结果仍未提交，`MERGE_HEAD` 为 `07fb0b5e13ac56a5cf234fd684ed2932ead4b91c`。
- **当前有效本地差异：** 相对 `origin/beta` 仅为 `PlayerManager.java`、`ExoBufferingStallWatchdog.java`、`PlayerManagerLifecycleSourceTest.java` 和 `ExoBufferingStallWatchdogTest.java` 四个路径；beta 侧新增行为由此前 dev1/dev4 复评记录覆盖。
- **已完成动作：** 发现并修复“普通 `STATE_BUFFERING` 未启动看门狗”、非 Exo 误接线，以及轮询先判断超时导致迟到回退 seek 误判三个问题；定向单测和验证后复评均通过。
- **未验证风险：** 未在真实设备上执行网络断流、遥控器 seek、硬解码器切换和逐帧视觉验收；源码测试覆盖不等价于实机放量验证。
- **当前交付结果：** 已完成 task guard 原子提交、恢复标签、`dev2`/标签推送、中文 PR #262，以及最终远端拉取核对。
- **唯一下一动作：** 无；本轮代码评审、修复、验证、提交、推送、PR 和最终同步均已闭合。

## 合并范围与提交台账

### beta 本轮新增提交

相对本地基线 `4ee05ac28cf3ba8f869f6e2d3e0cc99743b925b4`，`origin/beta` 新增以下完整提交：

| 完整 commit | 内容 | disposition |
| --- | --- | --- |
| `e8056e5ad412cd0ec141fff0ee12aa56e582fbfa` | 为 TV 关于页“检查更新”路径补充上键焦点目标。 | 已在最终树复核；`dialog_about.xml` 引用有效，未改变点击监听。 |
| `e82f50cbaa4b3c16edda98fcf0d2f5e368b2ef0a` | 为 TV 关于页“我已知悉”按钮补充上键焦点目标。 | 已在最终树复核；与 `githubProxy` 共用 `checkUpdate` 上键目标，无文件冲突。 |
| `30fff5f4ff80fbb90cb2852e78f08147c74cd4d8` | 为关于页三个主要操作引入 TV 聚焦/按下态背景、文字和图标资源。 | 已在最终树复核；4 个 XML 资源均可解析，状态颜色和资源引用闭合。 |
| `7c325e4a04891fc1df224d359039998500ec4235` | 合并 PR #257“修复 TV 关于页面焦点导航与高亮”。 | 合并承载提交；无未解决冲突，保留上面 3 个提交的最终树。 |

### 本地已提交未推送改动

| 完整 commit | 内容 | disposition |
| --- | --- | --- |
| `8f8797462082f0667341cdf430bf1fa70a73d39f` | 将剧幕主题剧照横向列表容器统一为 124dp，并增加对应源码契约测试。 | 与 beta 无内容冲突；最终树仍保留改动，已在本轮定向测试中复验。 |

当前 `git log origin/dev2..HEAD` 可达链中其余 15 个提交均已被 `origin/beta` 包含，且由此前 dev1/dev2/dev3/dev4 beta 评审文档覆盖；本轮不重复执行相同模块的全量复评，仅复核它们在最终合并树中的可达性和无冲突状态。

## 首轮评审

### TV 关于页焦点与高亮

- `checkUpdate`、`githubProxy` 位于同一横向操作行；`githubProxy` 的 `android:nextFocusUp="@id/checkUpdate"` 使右侧按钮向上返回左侧主操作，不依赖设备的空间焦点猜测。
- `confirm` 的 `android:nextFocusUp="@id/checkUpdate"` 使底部确认按钮向上回到主操作行；三个按钮仍保留原有文案、点击监听和布局尺寸。
- 两个 TonalButton 使用 `about_primary_action_bg`/`about_primary_action_text`：聚焦/按下时为深蓝背景和白色文字，未聚焦时为浅色 Tonal 样式，禁用态保留 alpha；未改变其他对话框共用的 `dialog_tonal_button_*` 资源。
- `updateSettings` 使用独立的 `about_primary_icon_button` 和 `about_primary_icon_tint`，聚焦/按下时显示深蓝背景和白色图标，默认态保持透明背景和灰色图标；点击逻辑仍由 `AboutDialog.show()` 绑定。
- 已检查资源 XML、ID 引用、焦点目标、`AboutDialog` 点击回调以及 `MaterialButton` 类型/样式兼容性，未发现空引用、资源命名冲突、点击行为回归或焦点环路问题。

### TMDB 剧照栏

- `TmdbDetailActivity.applyCinemaDetailTemplate()` 只在剧幕模板分支把 `episodePhotoList` 固定为 124dp；其它横向栏和默认模板未改变。
- `TmdbPhotoAdapter` 的剧照卡片高度同为 124dp，因此容器不会为卡片预留额外底部空间；现有剧照加载、点击和横向导航代码未被改动。
- 与 beta 变更没有文件交集，合并后相对 `origin/beta` 仍仅有该 Java 文件及其契约测试两条差异路径。

### 合并完整性

- `git ls-files -u` 无输出，未发现未解决合并路径。
- `git diff --cached --check` 与 `git diff --check` 均通过。
- 首次定向 Gradle 客户端会话未返回最终输出，不能作为证据；检查确认进程已终止且没有 OOM 记录后，使用 `--max-workers=1` 重新执行了同范围验证并保存完整日志。

## 验证记录

开始每次 Gradle/资源任务前均检查实际进程；未发现其它 `gradle/gradlew`、`assemble`、`bundle`、`cargo build/package` 或前端 build/package 任务，因此没有并发打包，未需排队。

1. 移动端资源处理、Java 编译和定向单测：
   ```text
   bash ./gradlew :app:processMobileArm64_v8aDebugResources :app:testMobileArm64_v8aDebugUnitTest \
     --tests com.fongmi.android.tv.ui.dialog.AboutDialogLayoutTest \
     --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest \
     --no-daemon --max-workers=1 --console=plain
   ```
   结果：`BUILD SUCCESSFUL in 1m 6s`，87 个 actionable tasks（13 executed，74 up-to-date）；`AboutDialogLayoutTest` 10 项通过，`TmdbDetailActivityLayoutTest` 121 项通过，均为 0 failures、0 errors、0 skipped。日志：`/tmp/beta-sync-review-dev2-20260912-focused.log`。
2. Leanback 共享资源处理：
   ```text
   bash ./gradlew :app:processLeanbackArm64_v8aDebugResources \
     --no-daemon --max-workers=1 --console=plain
   ```
   结果：`BUILD SUCCESSFUL in 30s`，54 个 actionable tasks（1 executed，6 from cache，47 up-to-date）。日志：`/tmp/beta-sync-review-dev2-20260912-leanback-resources.log`。
3. 使用 Python `xml.etree.ElementTree` 逐个解析 4 个新增资源及 `dialog_about.xml`，5 个文件均输出 `XML_OK`。
4. 定向评审前后任务守卫检查均通过；无未解决路径、无冲突标记、无越界变更。

## 验证后复评

- 最终暂存树相对 `origin/beta` 只有：`TmdbDetailActivity.java`、`TmdbDetailActivityLayoutTest.java` 两个本地差异，以及 beta 的 5 个关于页资源/布局差异；没有 beta 覆盖本地修复的情况。
- 重新核对四个 beta 完整提交和本地完整提交的可达关系：本地 TMDB 提交为唯一 `LOCAL_ONLY`，四个 beta 提交均已进入合并树；其它历史本地链提交均已在 beta，且此前评审覆盖。
- 再次检查 `dialog_about.xml` 的 `checkUpdate`、`githubProxy`、`confirm`、`updateSettings` 关系和资源状态选择器，焦点目标、点击入口、颜色状态和默认态均保持预期。
- 再次检查剧照容器 124dp、适配器卡片 124dp 及对应契约测试，未发现因合并导致的回退。
- **复评结论：通过，无需修复。**

## 接受标准与回滚

1. `origin/beta@7c325e4a04891fc1df224d359039998500ec4235` 已无冲突合入最终树。
2. 本地已提交未推送的 `8f8797462082f0667341cdf430bf1fa70a73d39f` 保留，且其行为和测试已复验。
3. 关于页资源、焦点路径、TMDB 剧照栏及既有已评审链均完成最终树复审；定向资源/测试验证通过。
4. 在当前合并树和本文档均通过 task guard 后，使用 `task_guard.sh finish` 原子提交并创建唯一恢复标签。
5. 推送 `dev2` 和恢复标签，创建目标为 `beta` 的中文 PR，最后重新拉取远端并核对分支、PR、工作树状态。

回滚方式：提交前执行 `git merge --abort`；提交后使用本任务创建的 annotated recovery tag，或对合并提交执行 `git revert -m 1 <merge-commit>`。

## 关闭记录（2026-09-12 13:50 CST）

- 原子合并提交：`4353f020eb541ee74a2be98f1b5ce20eef3f4021`（`merge: 合并 beta 最新代码并完成 dev2 复评 (2026-09-12)`）。
- 原始任务恢复标签：`recovery/beta-sync-review-dev2-20260912/20260912134415-4353f020eb54`，已推送。
- `dev2` 已推送至 `origin`；在首次 PR 创建失败后，使用显式 head `Silent1566:dev2` 成功创建中文 PR [#258](https://github.com/Silent1566/webhtv/pull/258)，目标分支为 `beta`。
- 最终 `git fetch --prune origin && git pull --ff-only origin dev2` 返回 `Already up to date`；当时 `HEAD == origin/dev2 == 4353f020eb541ee74a2be98f1b5ce20eef3f4021`，`origin/beta == 7c325e4a04891fc1df224d359039998500ec4235`。
- GitHub 最终核对：PR #258 为 `OPEN`、非草稿、`mergeStateStatus=CLEAN`，head 为 `dev2@4353f020eb541ee74a2be98f1b5ce20eef3f4021`，base 为 `beta@7c325e4a04891fc1df224d359039998500ec4235`；PR 包含本地 TMDB 修复和本次合并复评提交。

## 状态

- [x] 拉取 `origin/beta` 最新代码并确认当前目标提交。
- [x] 无冲突合并 beta。
- [x] 评审 beta 增量和已提交未推送的本地 TMDB 改动。
- [x] 完成移动端定向测试、Leanback 资源处理和验证后复评。
- [x] `task_guard.sh finish` 原子提交并创建恢复标签。
- [x] 推送 `dev2`/恢复标签并创建中文 PR 到 `beta`。
- [x] 最后拉取远端最新代码并核对交付状态。

## 2026-09-12 后续 beta 合并与缓冲卡死复评（18:10 CST）

### 合并基线与既有评审复用

- 本轮先执行 `git fetch origin beta dev2`；远端 `origin/beta` 为完整提交 `07fb0b5e13ac56a5cf234fd684ed2932ead4b91c`，`origin/dev2` 为 `38ff48d8d12a49cb4edbd08514e6342aaf2099ac`。
- 本地唯一未被 `origin/beta` 包含的生产提交为 `20dcf312452c7ae95bfe0fd9df85b15d5890bd50`（`fix(player): recover from buffering stalls`）；此前 TMDB、Exo 队列、关于页、分集详情及脚本改动均已进入 beta 或由既有 dev1/dev2/dev3/dev4 复评记录覆盖。
- `git merge --no-ff --no-commit origin/beta` 无冲突，`git ls-files -u` 无输出。合并后相对 `origin/beta` 的初始有效差异为该提交的 3 个路径；修复后扩展为下列 4 个路径，未触及保护路径：
  - `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`
  - `app/src/main/java/com/fongmi/android/tv/player/exo/ExoBufferingStallWatchdog.java`
  - `app/src/test/java/com/fongmi/android/tv/player/PlayerManagerLifecycleSourceTest.java`
  - `app/src/test/java/com/fongmi/android/tv/player/exo/ExoBufferingStallWatchdogTest.java`

### beta 增量完整提交处置

相对上次 dev2 复评基线 `7c325e4a04891fc1df224d359039998500ec4235`，本次 `origin/beta` 可达增量的完整提交均已核对；功能提交沿用已有复评，合并/文档提交不引入未覆盖生产行为：

| 完整 commit | 处置 |
| --- | --- |
| `f32787745c0dd0b587c5ec91751deb5d519cf339` | 构建安装脚本；已由此前 beta 复评覆盖，当前树无 dev2 独有差异。 |
| `4269dc5e83b2dafe29052b341a85f00ff17b0f5c` | 构建安装脚本 Leanback 参数；已由此前 beta 复评覆盖。 |
| `2bc1244a09ca5d0bd9992a4c5e617346827fe8a9` | Exo 短剧队列稳定策略；已有 E-SP8/前轮复评覆盖。 |
| `8f8797462082f0667341cdf430bf1fa70a73d39f` | TMDB 剧照栏；已由本任务前轮复评覆盖，并已进入 beta。 |
| `42d6abe02e962e3d92e156ab7e5f994a7026b1f1` | TMDB 剧幕海报圆角；已有 beta 复评覆盖。 |
| `b0f0343664502854ade683cdf0702d55a3ea32d3` | TMDB 剧幕海报完整显示；已有 beta 复评覆盖。 |
| `4353f020eb541ee74a2be98f1b5ce20eef3f4021` | dev2 合并承载提交；不新增未评审生产路径。 |
| `38ff48d8d12a49cb4edbd08514e6342aaf2099ac` | dev2 收口文档；无运行时代码。 |
| `81435e97eafabd64727412169ead107687f7325c` | PR #258 合并承载提交；保留已评审最终树。 |
| `667992b22e917735be5e5932ab33ac5293a4953c` | 分集客串演员圆角；由 `docs/beta-sync-review-dev4-20260912.md` 覆盖。 |
| `f1c15ef025601ab3fb3cfb2316fdafb650bcbae1` | dev1 合并承载提交；由 dev1 复评记录覆盖。 |
| `ccd608c503bed84711bb1bc75875c030b6be5ce5` | PR #259 合并承载提交；保留已评审最终树。 |
| `c2dcf52b5676eef00026f55f013eb58ae522804f` | 移除废弃 Exo FFmpeg 模式设置；由 dev1 后续复评记录覆盖。 |
| `6a002d29f74e2444c5372f53d361c67e018e0737` | 分集客串演员横向卡片；由 dev4 复评记录覆盖。 |
| `d42f2de6c803fda57f169740cd55ac6efd1154c5` | dev1 收口文档；无额外未覆盖生产行为。 |
| `b6b18ac1dd2f248c3001b1802ac285622620b6fa` | 移动端分集详情底部滚动；由 dev4 复评记录覆盖。 |
| `cf8c95b1053bf89be975692fbd8765fd700d2799` | dev4 合并承载提交；无新的未评审生产路径。 |
| `fe08b1c167618fa0d40148668a5e9e78a3a597e9` | dev4 复评文档；无运行时代码。 |
| `4a669575963614565cbde6a1e85a813792b8ec0a` | PR #261 合并承载提交；保留已评审最终树。 |
| `07fb0b5e13ac56a5cf234fd684ed2932ead4b91c` | PR #260 合并承载提交；保留 dev1 已复评最终树。 |

### 首轮代码评审发现

- `20dcf312452c7ae95bfe0fd9df85b15d5890bd50` 新增 `ExoBufferingStallWatchdog`，并在 `PlayerManager` 添加轮询与超时后的回退链。
- **问题 1（功能缺口）：** 初始实现只在 `seekTo()` 中调用 `armBufferingStallWatchdog()`；正常进入 `Player.STATE_BUFFERING` 时没有启动轮询，因此普通缓冲卡死不会被检测。
- **问题 2（作用域错误）：** 初始 `armBufferingStallWatchdog()`/`checkBufferingStall()` 没有限制 Exo，MPV/IJK 的 seek 或 buffering 状态也可能复用该 Exo 看门狗并错误进入回退链。
- **问题 3（边界误判）：** 初始轮询先调用 `shouldTimeout()`，后调用 `observe()`；在已超过普通超时窗口后发生回退 seek 时，旧位置/缓冲基线可能先触发超时，来不及被识别为 discontinuity。

### 修复与验证后复评

- `onPlaybackStateChanged(STATE_BUFFERING)` 现在启动看门狗，`READY`/非活动状态及时取消；arm 和 polling 均限制为 Exo，保持 MPV/IJK 原有行为不变。
- `checkBufferingStall()` 现在先 `observe()` 再 `shouldTimeout()`，使回退 seek/flush 先重建连续性基线，再进入超时判断。
- `PlayerManagerLifecycleSourceTest` 增加状态接线、Exo 限制和 observe-before-timeout 契约断言；`ExoBufferingStallWatchdogTest` 增加超时窗口后回退 seek 回归用例。
- 验证前内存：`free -h` 显示 `Mem available 1.9GiB`，满足至少 1.5 GiB；最终验证前再次显示 `Mem available 3.5GiB`，未发现活动构建任务。
- 最终定向验证命令：

  ```text
  bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
    --tests 'com.fongmi.android.tv.player.exo.ExoBufferingStallWatchdogTest' \
    --tests 'com.fongmi.android.tv.player.PlayerManagerLifecycleSourceTest' \
    --no-daemon --max-workers=1 --console=plain
  ```

- 结果：`BUILD SUCCESSFUL in 40s`，87 个 actionable tasks（6 executed，81 up-to-date）；日志：`/tmp/beta-sync-review-dev2-20260912-followup-final.log`。
- 最终静态复评：相对 `origin/beta` 仅保留上述 4 个路径；无冲突路径、无空白符错误；看门狗仅在 Exo buffering 期间运行，READY/结束/非活动均取消，暂停时按 tick 重置 episode，进度、loading 延迟、discontinuity、episode ceiling 和 fallback 入口均与测试/调用链一致。
- **复评结论：通过，无剩余 P1/P2 问题。**

### 当前状态与回滚

- [x] fetch `origin/beta`/`origin/dev2` 并确认远端 head。
- [x] 无冲突合并 `origin/beta@07fb0b5e13ac56a5cf234fd684ed2932ead4b91c`。
- [x] 复用既有 dev1/dev4 评审覆盖 beta 增量，并评审 `20dcf312452c7ae95bfe0fd9df85b15d5890bd50`。
- [x] 修复 3 个问题，完成定向单测，验证后再次复评通过。
- [x] `task_guard.sh finish` 原子提交并创建恢复标签。
- [x] 推送 `dev2`/恢复标签，创建目标为 `beta` 的中文 PR。
- [x] 最后拉取远端最新代码并核对分支、PR、工作树状态。

### 交付记录

- 原子提交：`00c171160ee0b310e3db3874500cdb7228236d45`（`merge: 合并 beta 最新代码并完成 dev2 缓冲卡死复评 (2026-09-12)`）。
- 恢复标签：`recovery/beta-sync-review-dev2-20260912-followup/20260912181235-00c171160ee0`，已推送。
- 代码交付提交 `00c171160ee0b310e3db3874500cdb7228236d45` 与文档收口提交 `e47fa3dee3718b8295389daced540fae5edcd52d` 均已推送到 `dev2`。
- 中文 PR：[#262](https://github.com/Silent1566/webhtv/pull/262)，目标 `beta`，状态 `OPEN`、非草稿、`mergeStateStatus=CLEAN`；head 为 `dev2@00c171160ee0b310e3db3874500cdb7228236d45`，base 为 `beta@07fb0b5e13ac56a5cf234fd684ed2932ead4b91c`。
- 最终执行 `git fetch --prune origin beta dev2 && git pull --ff-only origin dev2` 后应保持 `Already up to date`；最终工作树干净。
- **任务结论：通过并闭合，无剩余 P1/P2 问题。**
