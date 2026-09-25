# C15：dev1 合并 beta 最新代码并复评 E-SP8（2026-09-11）

## Recovery anchor

- **目标：** 将 `origin/beta` 最新代码合入 `dev1`，复评 beta 增量及 `dev1` 已提交但尚未推送的 E-SP8 短剧 Exo 队列；发现问题即最小修复、验证并再次复评，随后提交、推送 `dev1`、创建中文 PR 到 `beta`，最后刷新远端状态。
- **状态：** 合并冲突已解决；源码、双端编译、播放器相关单测、Mobile Debug APK 组装与设备启动检查均通过；待本 guard 创建两父合并提交和 recovery tag。
- **任务守卫：** `beta-sync-review-dev1-20260911`，`standard`；范围 `.codex`、`app`、`docs`。开始前工作区无 dirty 路径，未保护任何 pre-existing dirty path。
- **分支与基线：** `dev1@2b22c5240d52a8c2054299326f44fee6743ab26f`（`recovery/E-SP8/20260911201514-2b22c5240d52`）；共同祖先 `ae64c42369a6b2ee660fee86cbe47c40382df667`。
- **beta 目标：** `origin/beta@be1b02e06b22a4fa2f08c791555536e3e6154c95`，最新提交为 `revert: remove dynamic theme color system (Task C/D/E)`；当前仍处于无冲突的 `--no-commit --no-ff` 合并状态。
- **回滚：** 提交前使用 `git merge --abort`；提交后使用本任务 recovery tag 或 `git revert -m 1 <merge-commit>`。E-SP8 可独立使用其已有 recovery tag 回滚。
- **唯一下一步：** 使用当前 task guard 完成原子合并提交与 recovery tag，随后推送、创建 PR 并 `fetch --prune`。

## 本轮 beta 增量完整记录

相对已覆盖的 `C14` 评审，当前 `origin/beta` 末端新增的三个完整提交均按最终树复核：

| 完整 commit | 父提交 | 内容 | disposition |
| --- | --- | --- | --- |
| `24bda15fb12172e44eb2b58fce588992acdbcf90` | `cf2a4c915a836b52a6cd0d0ae9f20a7d9535ecf7`、`dbff441aa8a4bb54883ae07f722e53071413dd99` | 同步 beta 后续最新代码，包含 TV 分类边界焦点修复及其测试/文档 | 已在最终 beta 树；对应 dev2 评审已覆盖，合并无冲突，复核通过 |
| `e0e6ebebfc04f6db178c82ba8a4354938feaf487` | `dbff441aa8a4bb54883ae07f722e53071413dd99`、`24bda15fb12172e44eb2b58fce588992acdbcf90` | 合并 PR #250，包含 EXO 上游恢复、播放器相关清理及既有 beta 评审结果 | 已在最终 beta 树；保留 beta 明确删除项，并与 E-SP8 接口复核通过 |
| `be1b02e06b22a4fa2f08c791555536e3e6154c95` | `e0e6ebebfc04f6db178c82ba8a4354938feaf487` | 删除动态主题颜色系统 | 已在最终 beta 树；删除路径、资源和测试未被恢复，双端编译通过 |

## 审查边界与已有覆盖

- `HEAD..origin/beta` 共 61 个可达提交。此前的 `beta-sync-review-dev2-20260911.md`、`beta-sync-review-dev3-20260908-round4.md`、`beta-sync-review-dev4-20260909-round3.md`、`beta-sync-review-dev4-20260909-round4.md` 和 `C14-beta-sync-review-dev4-20260911.md` 已分别覆盖主题系统、TV 焦点、播放速度、广告音频、EXO 上游恢复及其无冲突后续同步。
- 本轮仍检查最终合并树，而不是仅依赖历史文档：beta 目标的动态主题回滚已删除对应生产、资源和测试路径；双端 Java 编译与运行时首页启动均未出现遗留类型或资源引用。
- 合并后相对 `origin/beta` 的本地唯一行为差异为 E-SP8 的 14 个预期路径：两端 `VideoActivity`、`SiteViewModel`、`PlayerManager`、`PlayerEngine`、`ExoPlayerEngine`、`ExoUtil`、`MediaSourceFactory`、`PreCache`、队列协调器及其测试/任务文档/索引。没有将 beta 已删除的 EXO recovery、旧 HLS helper 或主题实现重新引入。

## 冲突解析与最终复评

1. **短剧队列与 beta 播放改动同时保留。** 两端 `VideoActivity` 同时保留 beta 的播放速度选择、共享 `PlaybackResourceClassifier.isHlsUrl()` 等接线，以及 E-SP8 的单项队列、独立取址、自然切集事务和失败回退。
2. **Exo 资源与缓存隔离保持一致。** `ExoUtil` 保留 beta 的当前 `PlaySpec`/请求元数据结构，并保留带稳定 `mediaId` 的播放项重载；`MediaSourceFactory` 前台 `CacheKeyFactory` 与 `PreCacheHelper` 的 scoped cache 使用同一 header-derived namespace，防止不同认证上下文复用缓存。
3. **beta 的 EXO 上游恢复保持原状。** `ExoBufferingStallWatchdog`、`PreCacheWorkerRecovery`、旧 `MediaSourceFactoryTest` 等 beta 明确删除的类/测试未恢复；生产引用已由 `PlaybackResourceClassifier` 和当前 Exo 树替代。
4. **自然切集事件归属已复核。** 两端在 `PlayerManager.commitPlaylistTransition()` 替换当前 `PlaySpec` 前关闭上一集 `PlaybackEventCollector`，避免上一集停止事件写入下一集媒体信息；提交失败仍保留一次旧路径回退。
5. **最终树完整性。** 没有未合并路径、没有冲突标记；`git diff --cached --check` 通过。合并包含 beta 的广告规则、TV 焦点、主题回滚、速度选择和 EXO 恢复最终树，未发现新的编译或可定位的行为阻断问题。

## 验证记录

1. 修正错误的 Gradle 任务名后，在确认没有其他实际 Gradle/assemble/bundle 任务运行的情况下执行：

   ```text
   ./gradlew \
     :app:compileMobileArm64_v8aDebugJavaWithJavac \
     :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
     :app:testMobileArm64_v8aDebugUnitTest \
     --tests 'com.fongmi.android.tv.player.*' \
     --tests 'com.fongmi.android.tv.player.exo.*' \
     --tests 'com.fongmi.android.tv.ui.player.*' \
     --continue
   ```

   结果：`BUILD SUCCESSFUL in 50s`；Mobile/Leanback Arm64 Java 编译完成，播放器、EXO 与 UI player 相关 Mobile 单测共 1263 项完成。

2. 在同一设备验证槽位中执行：

   ```text
   ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon --console=plain
   ```

   结果：`BUILD SUCCESSFUL in 54s`，生成 Mobile Arm64 Debug APK。

3. 将 APK 安装至 `192.168.50.3:5559`（V1923A、Android API 28），安装成功；启动后应用进入 `HomeActivityCurrent` 且进程存活，无 `FATAL EXCEPTION`。设备 UI 可正常加载点播首页与短剧内容卡片。

4. 点击一个详情卡片后，系统记录 `pid 5484` 对应用执行 `Force stopping com.silent.android.webhtv`，随后进程收到 SIGKILL；日志没有 Java `FATAL EXCEPTION`、native tombstone 或 ANR 归因。该外部 force-stop 不能作为当前合并回归，也不构成真实短剧连续切集的成功证据。

## 验收结论与边界

- **本次合并/代码复评结论：通过。** beta 最终树、E-SP8 差异、冲突组合、请求头缓存隔离和自然切集事件顺序均已复核；定向编译、单测、APK 组装和设备启动检查支持提交。
- **未扩大结论：** E-SP8 的实验开关仍遵守既有实验策略；没有把一次 APK 启动或外部中止的详情页尝试描述为连续切集、网络争用、字幕/弹幕时序或 Leanback 实机验收。上述场景仍是后续正式放量前的真实设备风险。
- **提交后动作：** 推送 `dev1` 与本任务的新 recovery tag，创建中文 PR 到 `beta`，最后 `git fetch --prune origin` 并核对远端分支和 PR。
