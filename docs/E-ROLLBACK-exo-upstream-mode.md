# E-ROLLBACK-EXO：EXO 播放链路恢复上游模式

## Recovery anchor

- 目标：将当前 `dev4` 中相对 `fish2018/webhtv:main` 的 EXO 播放实现恢复为上游模式；不改动 MPV、IJK、系统播放器或与 EXO 无关的应用功能。
- 状态：已实施，定向编译与双端受影响单测通过，待任务守卫提交并创建恢复标签。
- 当前本地基线：`4e303d30d34c499e1676a3481b4b45358618638d`。
- 上游目标：`784b90420d646eb6c7ddcc63ad622a92c65b02b4`（`upstream/main`）。共同祖先为 `784b90420d646eb6c7ddcc63ad622a92c65b02b4`。
- 保护 dirty 路径：`build-tv-debug.sh`；不纳入本任务。
- 已完成证据：EXO engine、`player/exo` 和对应测试的暂存内容相对冻结的 `upstream/main` 无差异；共享 EXO 看门狗解耦及 TV 加载圈回退已在工作区；保护文件 `build-tv-debug.sh` 已取消误暂存且内容保持不变。
- 未验证编辑：无；任务代码已通过双端 Java 编译和受影响单测，尚未提交/tag。
- 已知非阻断结果：一次未正确收窄到目标类的 Mobile 全量测试执行了 4238 项并报告 10 项失败；其中 5 项是当前 HEAD 已存在且不在本任务差异内的旧契约/二进制基线失败，5 项对应本任务明确恢复上游模式后移除的本地看门狗/轨道高亮行为。随后按正确任务过滤重跑本任务覆盖的测试并通过，不把全量旧失败宣称为本任务回归。
- 唯一下一步：使用 `task_guard.sh finish` 原子提交本任务并创建恢复标签。

## 用户授权与范围

用户明确选择“全部”，授权将 EXO 播放相关逻辑统一恢复到上游 `https://github.com/fish2018/webhtv/tree/main` 模式。

本任务的代码范围：

1. `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java`；
2. `app/src/main/java/com/fongmi/android/tv/player/exo/` 全目录；
3. 上述实现对应的 `app/src/test/java/com/fongmi/android/tv/player/engine/ExoPlayerEngineSourceTest.java` 与 `app/src/test/java/com/fongmi/android/tv/player/exo/`；上游不存在的本地 EXO 测试/实现文件随恢复删除；
4. 共享入口中仅服务本地 EXO 看门狗的 wiring（`PlayerManager.java`）；
5. 本任务文档和上游评估索引。

保留历史 EXO 评估/实施文档作为审计记录，不因代码回滚删除；不恢复 `build-tv-debug.sh`。

## 证据与决策

### 基线证据

- 本地仓库：当前 worktree `dev4`，HEAD 为 `4e303d30d34c499e1676a3481b4b45358618638d`。
- 上游仓库：`upstream` 指向 `https://github.com/fish2018/webhtv.git`，`upstream/main` 为 `784b90420d646eb6c7ddcc63ad622a92c65b02b4`，读取时间为 2026-09-10（Asia/Shanghai）。
- 本地代码审计显示直接 EXO 包相对上游有 12 个已修改/新增生产文件；对应测试包含本地新增的 EXO 测试。`ExoBufferingStallWatchdog` 还由 `PlayerManager` 直接持有并调度，不能只恢复 EXO 包而不清理入口 wiring。
- GitHub 上游目录作为目标树来源；本地已通过 `git fetch upstream` 冻结并使用完整 commit ID，不依赖网页摘要。

### 方案比较

| 方案 | 决策 | 理由 |
| --- | --- | --- |
| 不变更 | 拒绝 | 无法满足用户“全部改回上游模式”；本地 EXO 自定义策略仍会影响播放。 |
| 整个仓库重置到上游 | 拒绝 | 会误删当前分支的 MPV、IJK、系统播放器、UI 和业务功能，超出用户指定的 EXO 范围。 |
| 直接恢复 EXO 实现/测试，并移除共享入口的 EXO 专属本地 wiring | 采用 | 使 EXO 核心实现与测试精确对齐上游，保留其他播放器和业务功能；改动可由本任务提交和恢复 tag 原子回滚。 |

### 风险与接受条件

- 接受上游模式会失去本地 EXO 停滞看门狗、延迟 Cues、FFmpeg 回退调优、缓存 worker 崩溃恢复、部分 DV/轨道/负载策略和相关诊断增强；这些是此次用户明确选择“全部”所接受的行为变化。
- 不能留下对已删除本地类的编译引用；`PlayerManager` 必须不再引用 `ExoBufferingStallWatchdog`。
- EXO 直接代码/测试路径相对 `upstream/main` 应无差异；历史任务文档不作为代码一致性判据。
- 最小验证：`git diff --check`、EXO 相关路径与 `upstream/main` 的差异审计、低内存门禁后一次定向 Gradle Java 编译和 EXO 单测。

## 实施记录

- 2026-09-10：已将 EXO engine、`player/exo` 包及对应测试恢复/删除到上游树；下一步清理 `PlayerManager` 对本地停滞看门狗的共享 wiring。

## 回滚

使用本任务提交对应的 `recovery/E-ROLLBACK-EXO/<timestamp>-<commit>` 标签恢复本次原子变更；如需撤销本任务，按该 tag 反向恢复，不触碰保护性 dirty 文件 `build-tv-debug.sh`。

## 实施记录（续）

- 2026-09-10：移除 `PlayerManager` 对本地 `ExoBufferingStallWatchdog` 的 import、字段、调度、回调和超时分支；确认代码中不再存在该本地类、预缓存 worker recovery 类或 TV 端本地 seek-loading 状态符号的引用。
- 2026-09-10：反向撤销 `4e303d30d34c499e1676a3481b4b45358618638d` 在 Leanback `VideoActivity` 及其来源测试中的 EXO 加载圈改动；保留该提交的任务文档，不回滚同文件的其他业务功能。
- 2026-09-10 14:47（Asia/Shanghai），恢复会话验证：先检查内存并等待，最近采样可用约 3.2 GiB、无持续 swap I/O；以单 worker、2 GiB Gradle 堆和 `--no-daemon` 执行 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。48 秒后 Java 编译失败，准确错误为 `TmdbDetailActivity.java:8527`、Leanback `VideoActivity.java:4369` 引用不存在的 `MediaSourceFactory.isHlsUrl(String)`。完整本机日志：`/tmp/e-rollback-exo-compile.log`。这是本次回退产生的兼容接线缺口，不是环境错误；未继续执行单测或提交。
- 恢复会话安全核对：`task_guard check`、`git diff --check` 通过；`git diff --cached --exit-code upstream/main -- <EXO engine/package/direct tests>` 无差异。原会话的 `finish` 实际被安全门拒绝，没有产生本任务提交/tag；根因为 `.codex/scripts/task_guard.sh` 的删除文件分支使用 `git add -u -- .`（全仓库暂存），会再次纳入受保护的 `build-tv-debug.sh`。不通过重复取消暂存、篡改保护指纹或绕过安全检查来收尾。
- 2026-09-11 10:40–11:00（Asia/Shanghai）：修复两个已证实阻塞：将 `TmdbDetailActivity`、Leanback/Mobile `VideoActivity` 的 HLS 广告反馈判断迁移到共享 `PlaybackResourceClassifier.isHlsUrl(String)`，并将任务守卫删除文件暂存收窄为 `git add -u -- "$path"`；新增 HLS 兼容别名单测。
- 2026-09-11：任务守卫检查和 `git diff --check` 通过；在确认没有其他构建任务后，`./gradlew --no-daemon --max-workers=1 --console=plain -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。
- 2026-09-11：同一构建槽位确认后，Mobile 目标测试类（`PlaybackResourceClassifierTest`、`ExoAutomaticVideoConstraintPolicyTest`、`ExoLoadControlPolicyTest`、`PreloadLifecycleTrackerTest`、`TrackUtilTest`、`PlaybackOwnershipSourceTest`、`TmdbDetailActivityLayoutTest`）和 Leanback 目标测试类（前六项）分别运行一次，均 `BUILD SUCCESSFUL`；日志分别保留于 `/tmp/e-rollback-exo-focused-mobile-20260911.log` 与 `/tmp/e-rollback-exo-focused-leanback-20260911.log`。
- 2026-09-11：首次 `task_guard.sh finish` 暴露删除路径收口缺陷：已暂存删除且工作树不存在的路径不能继续使用 `git add -u`/`git add -A` pathspec；将 guard 分支改为逐路径执行 `git update-index --remove -- "$path"`，仍不触碰保护 dirty 路径。

## Recovery anchor（2026-09-12 FFmpeg 模式清理后续）

- 目标：移除 EXO 已不再消费的用户可选 FFmpeg 模式及其无效 AUTO 重建链；保留 `ExoUtil` 当前上游 `FfmpegRenderersFactory`、真实 FFmpeg 音视频 renderer 和普通解码/内核回退。
- 当前分支/实施基线：`dev1` / `ccd608c503bed84711bb1bc75875c030b6be5ce5`；实施提交为 `c2dcf52b5676eef00026f55f013eb58ae522804f`。
- 保护 dirty 路径：无（启动本阶段任务守卫时工作树干净）。
- 已完成编辑：删除 `PlayerSetting` FFmpeg 模式 API、`PlayerManager` AUTO 模式失败遍历和刷新状态、手机/TV 设置行及资源、备份字段、过时文档和对应契约测试；保留 `ExoUtil` 的 FFmpeg renderer 构造。
- 未验证编辑：无。
- 回滚锚点：`recovery/E-ROLLBACK-EXO/20260912151536-c2dcf52b5676`。
- 已完成验证：`git diff --check`、运行时/API/UI/资源引用审计和修改后 XML 解析均通过；运行时代码与用户可见资源已无 `ffmpeg_mode`、`FFMPEG_MODE_*`、`player_ffmpeg_mode`、`default_ffmpeg_mode` 或 `ffmpegMode` 引用。历史 EXO 评估/审计文档仍保留当时的模式术语作为审计记录；`ExoUtil` 仍保留 `FfmpegRenderersFactory`、`CompatFfmpegAudioRenderer` 和 `FfmpegVideoRenderer`。
- 2026-09-12：在确认没有实际 `GradleWrapperMain`/编译器任务后，Mobile/Leanback Java 编译均通过；首次包含整个 `VideoActivityLayoutTest` 的测试命令因既有的 `leanbackSpeedBoostReleaseIsGuarded`（`VideoActivityLayoutTest.java:815`）失败而结束，未扩大范围修复。
- 2026-09-12：按本次改动收窄后的 Mobile/Leanback `PlayerManagerTest`、`PlayerDisplaySettingSyncTest`、`PlayerSettingTest` 均通过；日志为 `/tmp/e-rollback-exo-ffmpeg-mode-cleanup-20260912.log` 与 `/tmp/e-rollback-exo-ffmpeg-mode-cleanup-focused-tests-20260912.log`。
- 2026-09-12：当前提交相对 `origin/beta` 的全部差异完成首轮 adversarial 复评，未发现需要修复的源码问题；`origin/beta` 已是当前提交祖先，因此无需产生新的合并提交。
- 2026-09-12：`dev1` 已推送至 `origin/dev1@c2dcf52b5676eef00026f55f013eb58ae522804f`，恢复标签已推送；已创建目标为 `beta` 的中文 PR #260（`EXO：移除已废弃的 FFmpeg 模式设置`）。
- 当前状态：本阶段代码已提交、复评、推送并进入 PR；历史审计文档中的旧模式描述不属于运行时残留。
