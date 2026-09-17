# dev4 合并 beta 最新代码与复评（2026-09-09 round4）

## Recovery anchor

- **目标：** 拉取远端 `beta` 最新代码，合并后评审全部当前改动（包括已提交未推送的 dev4 改动），发现问题则修复、验证并复评至通过，随后提交、推送 dev4、创建中文 PR 到 beta，最后再次拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev4-20260909-round4`，模式 `standard`，范围 `app`、`docs`。
- **开始状态：** 2026-09-09 10:39 CST；分支 `dev4`，开始前 HEAD 为 `9769ca8a084600285887f7d150446f12379bf5c0`，工作树无 dirty 路径，无需保护 pre-existing dirty path。
- **远端状态：** 已执行 `git fetch --prune origin beta dev4`；`origin/beta` 为 `a65c7320d9213b3af01f121d9d68412dd527f380`，其父提交为 `242c089b06ccd330933533b856f14a9321921a11` 与 `515d1e04e5f6685d507c9667831cee4bc460e364`；`origin/dev4` 为 `515d1e04e5f6685d507c9667831cee4bc460e364`。
- **beta 合入证据：** `origin/beta` 的树为 `fdeaa3b8b724eceb6a865058b31d8888651777a4`，与其第二父提交 `515d1e04e5f6685d507c9667831cee4bc460e364` 的树一致；`git diff --exit-code 515d1e04e5f6685d507c9667831cee4bc460e364 a65c7320d9213b3af01f121d9d68412dd527f380` 通过，`git merge --no-commit --no-ff origin/beta` 无冲突且不产生内容变更。远端 PR #242 已将 beta 最新线与 `515d1e04…` 汇合，因此没有伪造空 merge commit。
- **本轮待审本地提交：** `9769ca8a084600285887f7d150446f12379bf5c0`（直播播放不受点播自动播放设置影响），相对 beta 只新增 6 个生产/资源文件；本轮另修正对应的 source regression test 并增加覆盖断言。
- **回滚：** 本轮新提交使用 task guard 自动创建的恢复 annotated tag；若需仅回退本轮文档/测试修正，回退其新提交即可，`9769ca8a…` 保留为独立原子提交。

## 评审范围与结论

### 1. `PlaybackActivity` 自动播放策略

- `protected boolean shouldAutoPlay()` 默认继续返回 `PlayerSetting.isAutoPlay()`，保持点播、音频、Cast、TMDB 等既有调用方的设置行为。
- `startPlayer` 的解析路径和直连路径都改为使用 `shouldAutoPlay()`，没有遗漏一条播放入口。
- 移动端和 Leanback 端 `LiveActivity` 都精确覆写为 `true`，频道初次播放、切台及重新拉流均通过同一个 `startPlayer` 入口恒自动播放。
- 没有修改 `PlayerManager` 的播放状态机、暂停/恢复或后台策略；变更只改变宿主传入的 `playWhenReady`，范围与需求一致。

### 2. 设置文案

- 默认、简体中文、繁体中文三份 `player_auto_play` 均明确为“点播自动播放”，与直播恒自动播放的实际策略一致。

### 3. 测试契约

- 初轮运行既有 `PlayerPlaybackRegressionSourceTest` 时，第 254 行的旧断言仍寻找 `PlayerSetting.isAutoPlay()` 直接传参，确认是本次抽取 `shouldAutoPlay()` 后的测试滞后。
- 已将该断言更新为 `shouldAutoPlay()`，并加入对默认点播策略、解析/直连两条调用路径、移动端/Leanback 直播覆写的精确 source assertions。
- 同轮另外两项失败（`panDiagnosticRespectsTheConfiguredPlayerButtonOrder` 第 82 行、`runtimePlayerActionsRefreshFocusAfterVisibilityChanges` 第 263 行）依赖的 Leanback 播放器按钮/焦点源文件未被本轮提交修改；测试文件 blame 显示其逻辑来自更早提交，归类为既有基线失败，不在本任务范围内修复。

## 验证记录

- 初轮守卫检查通过；初轮全量 `PlayerPlaybackRegressionSourceTest` 结果为 9 项完成、3 项失败，失败分类见上。
- 修正测试后执行一次聚焦验证：

  ```text
  bash .codex/scripts/task_guard.sh check
  bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
    --tests 'com.fongmi.android.tv.ui.activity.PlayerPlaybackRegressionSourceTest.livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy' \
    --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest' \
    :app:compileMobileArm64_v8aDebugJavaWithJavac \
    :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
    --no-daemon --console=plain
  ```

- 结果：`BUILD SUCCESSFUL`，98 个 actionable tasks 中 8 个执行；直播自动播放新断言、`LiveActivityLayoutTest`、移动端 Java 编译和 Leanback Java 编译均通过。
- 修正后最终静态复评：重新核对 `9769ca8a…` 的 6 个生产/资源路径、两种 Live source set、`parse`/直连 `start` 调用链、三份资源文案及测试断言；未发现遗漏、行为回退或超出需求的改动。
- `git diff --check` 与 task guard `check` 通过。未执行真实设备逐帧直播切台、网络站源和音视频硬件回归；这些不属于本轮源码策略变更的最小决定性验证范围。

## 当前状态与下一动作

- [x] fetch 远端 beta/dev4 并确认 beta 合入树无冲突
- [x] 评审已提交未推送的直播/点播自动播放改动
- [x] 修正测试契约并完成验证
- [x] 修正后再次复评改动路径
- [ ] 使用 task guard 原子提交本轮测试与文档，随后推送 `dev4`
- [ ] 创建目标为 `beta` 的中文 PR
- [ ] 最后拉取远端最新代码并核对分支、PR 和工作树状态

**唯一下一动作：** 在不扩大范围的前提下执行 task guard `finish`，生成本轮提交与恢复 tag。
