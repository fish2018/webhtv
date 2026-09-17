# E-SP3：BUFFERING 停滞看门狗

- 任务 ID：`E-SP3`
- 类别：Exo 性能专项
- 唯一文档：`docs/E-SP3-exo-buffering-stall-watchdog.md`
- 状态：已实施并通过目标验证；待实机验收。
- 下一动作：在能复现原症状的设备上确认停滞后自动降级触发；另立单元处理 `E-SP1` 文档记载更正与 `chaquo` Spider 漏 close。

## 用户观察到的失败

电视端最新测试版（beta，含上游合并 `c72d09092a`）：进入播放后转圈长时间不消失，随后整个界面无响应，只能结束应用；EXO 硬解自动。拖拽进度后需要很久才出画面与声音，且「不像加载中」——转圈已消失、进度条显示已缓存完毕，但无声无画。

## 根因

三条独立事实叠加，前两条是既有缺陷，第三条把它放大成不可用。

### 1. BUFFERING 一进入就撤掉启播超时（FongMi 原版既有）

`PlayerManager.onPlaybackStateChanged()`：

```java
if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
```

`runnable` 即 `onPlaybackTimeout`（`Constant.TIMEOUT_PLAY` = 15s）。`STATE_BUFFERING` 也满足 `!= STATE_IDLE`，因此播放器一进缓冲就解除唯一的启播保护。`git log -L` 追溯显示该行来自最初导入提交，非本地引入。

### 2. BUFFERING 停滞没有任何通用兜底

- `PlaybackBufferingTracker` 只统计 `rebufferCount`/`rebufferTotalMs`，不含超时或恢复动作。
- 唯一的停滞看门狗 `ExoTunnelingProgressWatchdog`（3s）要求隧道启用 + 已出首帧 + `STATE_READY`；而 `PlayerSetting.isTunnel()` 默认 false，命中不了。

因此一旦停在 BUFFERING，`fallbackPlayback()` 的自动降解码/切内核链（用户正用「硬解自动」）永不触发，表现为彻底卡死而非自动切换。

### 3. 首帧到达时再撤一次超时（上游 `f2721c43b6` 引入，本次回归来源）

```java
public void onRenderedFirstFrame() {
    if (isExo()) App.removeCallbacks(runnable);
```

上游意图是避免「慢音频轨让 Exo 短暂留在 BUFFERING 时被误判为连接超时」。但首帧 ≠ 可播放：项目自身的 `PlaybackStartupPolicy.resolve()` 在 `!ready` 时返回 `Completion.NONE`，即必须 `STATE_READY` 才算起播完成。音频轨若不只是「短暂」慢，就永久停在 BUFFERING 且再无保护。

该提交的 Task-Guard 为 `exo-dv7-timeout-after-first-frame`（DV7 任务），但改的是通用启播超时，影响所有 Exo 播放。`E-SP1` 文档将其计入自身实现，却记载「超时取消保持不变」且声明「不修改 `STATE_READY`、`PlaybackStartupPolicy`、缓冲参数、seek」——记载与代码不符，本任务同时纠正该记录。

### seek 侧的连带表现

- `PlayerManager.seekTo()` 无任何超时兜底，只能被动等 LoadControl 阈值；重缓冲阈值上限 `ExoPlaybackThresholdPolicy.MAX_STREAMING_REBUFFER_MS` = 15s。
- 电视版 `VideoActivity.hideSeekProgressIfReady()` 是一次性 500ms 回调，且仅在 `STATE_READY` 时收圈，超时不重试；此后收圈只靠 `onStateChanged(STATE_READY)`。
- 上游 `PlaybackActivity.onExoFirstFrame()` 在首帧即把 `R.id.progress` 设为 GONE，绕过 `VideoActivity.hideProgress()`。于是 seek 后转圈被提前抹掉，而播放器仍在缓冲，观感即「画面静止、无转圈、无声音」。
- `getBufferedPercentage()` 已改为计入 `PlaybackDiskBufferStore` 磁盘区间，与 Exo 实际起播判据（内存 SampleQueue 时长）不一致，故进度条显示「已缓存完毕」。

## 方案对比

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 无改动 | 保持现状 | 拒绝。BUFFERING 停滞无兜底，自动降级链失效。 |
| 回退 `f2721c43b6` | 删掉首帧撤超时那行 | 拒绝。会退回上游要解决的「慢音频轨误报连接超时」。 |
| 直接放宽 `TIMEOUT_PLAY` | 把 15s 调大 | 拒绝。既不解决停滞无兜底，又拖慢真实失败的降级。 |
| **换防（采纳）** | 首帧到达时把「启播超时」换成「BUFFERING 停滞看门狗」，而非解除保护 | 采纳。既保住上游意图，又消除裸奔窗口。 |

## 设计

新增纯逻辑类 `ExoBufferingStallWatchdog`，形态对齐既有 `ExoTunnelingProgressWatchdog`（`arm`/`observe`/`shouldTimeout`/`reset`），便于单测且不持有 Player 引用。

判据必须**同时**满足两条，缺一不可：

- `positionMs` 未前进（正常缓冲时 position 本就不动，只看它会误杀）
- `bufferedPositionMs` 未增长（仍在进数据就不算停滞）

阈值取 `STALL_TIMEOUT_MS = 20_000`，必须**大于**重缓冲阈值上限 15s，否则会在 LoadControl 正常填充缓冲期间误杀。

第三个条件用于避开 `E-SP2` 的延后 Cues：远程 MKV 首次 seek 会先取文件尾部 Cues，期间 position 与 buffered 都合法地不动且产不出样本，仅用前两条会把一次正常抓取误判为停滞并触发多余降级。因此当 `player.isLoading()` 为真时改用更长的 `LOADING_STALL_TIMEOUT_MS = 60_000` 上限，而不是直接豁免——否则挂死的 socket 读会让 `isLoading()` 永真，看门狗永不触发，恰好放过本任务要修的场景。

`bufferedPositionMs` 必须取 `player.getBufferedPosition()` 原生值，**不可**用 `getEffectiveBufferedPosition()`——后者含磁盘区间，会让停滞看起来仍在增长。

停滞入口独立于 `onPlaybackTimeout()`，只复用 `fallbackPlayback(e)` + `callback.onError()`。不得复用 `retryExoDv7FirstFrameTimeout()`、`retryLutWarmupByRefresh()`、`completeIjkBufferManagedReload(false, "timeout", ...)`——这些是启播语义，播放中途停滞时重复触发会引出新问题（尤其 DV7 那条会再走一次 rebuild + 1200ms 延迟启动）。

## 边界

只改「超时保护的装/撤时机」与「新增停滞检测」。不修改：`STATE_READY` 语义、`PlaybackStartupPolicy`、缓冲参数与阈值策略、解码器/渲染器选择、DV7→P8.1/HDR10 fallback、TrueHD/直通、Range/cache、软解降载、MPV 输出策略、`setSeekParameters`（seek 精度属产品取舍，另议）。

不在本任务修的已知问题（另立单元）：

- ~~`chaquo/src/main/java/com/fongmi/chaquo/Spider.java` 漏 close 的临时 PyObject~~ —— **已评审后撤销，见下节**。
- `E-SP2` 延后 Cues 的实机性能/seek 验收（索引第 7 行标注仍未完成，却已随 beta 分发）。
- `getBufferedPercentage()` 计入磁盘区间导致进度条显示「已缓存完毕」，与实际起播判据不一致；同时使 `PlayerOsdController` 的「缓冲偏少」提示不再触发。
- `retryExoDv7FirstFrameTimeout()` 的 1200ms 延迟回调若命中 `seq != prepareSeq || spec != target || engine != exo || player == null` 提前返回，则既不 `engine.start()` 也不重投 `runnable`，而函数入口已执行 `App.removeCallbacks(runnable)` 与 `rebuildPlayer(true)`——播放器已重建但从未启动且无超时保护。缺一个补投看门狗的 else 分支。本任务的停滞看门狗不覆盖该路径（那里停在 IDLE 而非 BUFFERING）。

## 验收标准

- `ExoBufferingStallWatchdogTest` 覆盖：position 与 buffered 均不动才超时；仅 buffered 增长不超时；仅 position 前进不超时；`reset` 后不超时；未 `arm` 时不超时。
- Mobile 与 Leanback arm64 debug Java 编译通过。
- 代码层可证：任一 BUFFERING 停滞路径都存在已装载的看门狗（首帧后、seek 后、起播中）。

验证边界：编译与单测证明判据与装撤时机正确，不等同于设备上的首帧耗时、音频初始化或缓冲性能结论。实机验收需在受影响设备上复现原症状后确认自动降级触发。

## 验证结果

- `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "...ExoBufferingStallWatchdogTest"`：`tests="10" failures="0" errors="0" skipped="0"`。
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL`。
- `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac`（随单测任务执行）：`BUILD SUCCESSFUL`。
- `git diff --check`：通过。

代码层可证的装载覆盖：起播中（`setMediaItem` 的 `runnable` 撤除后由 BUFFERING 分支接手）、首帧未到 READY（`onRenderedFirstFrame` 换防）、seek 后（`seekTo` 尾部装载）、播放中途重缓冲（BUFFERING 分支）。`reset()` 与 `release()` 均已取消轮询，避免越过会话存活。

## 评审后修正（合并 origin/beta 后的评审轮）

合并 `origin/beta` 为空操作（无冲突、无新提交），但此前只在各单元内自查过，未对合并 diff 做系统评审，故补做一轮对抗性评审，改出三处：

### 1. 暂停被误判为停滞（必修）

`checkBufferingStall()` 原先只排除 `READY`/`ENDED`/`IDLE`，**没有 `playWhenReady` 守卫**。在缓冲中按暂停时状态仍是 BUFFERING、position 由设计冻结，缓冲填满后 `isLoading()` 转 false、buffered 也冻结，于是判据成立 → 在用户只是暂停的情况下擅自 `fallbackPlayback` 切解码器或换内核。已加暂停守卫。

随之出现的连带缺口需要一并处理：暂停若 `cancel`，而恢复播放时状态并未离开 BUFFERING，看门狗将在该次缓冲的余下时间内一直缺失。

第一版试图在 `onIsPlayingChanged` 内重新装载，**这是错的**（复审指出）：media3 的 `isPlaying()` 要求 `state == STATE_READY`，缓冲期间恒为 false，暂停与恢复都是 false→false，该回调**不会派发**，那段代码是死代码，缺口并未堵上。`PlayerManager` 的 `Player.Listener` 也没有覆写 `onPlayWhenReadyChanged`。

最终改为**不依赖任何回调**：暂停时不 cancel，而是每轮开启一段新 episode（推平基线与计时）并继续轮询。恢复时天然获得完整窗口，且不必判断哪条恢复路径会触发哪个回调 —— UI 暂停键与 `PlaybackService` 的 `ActionEvent.PLAY`／`dispatchReplay` 最终都只是改 `playWhenReady`，走同一条路。

### 2. 回退式 seek 使基线永久失配（必修）

`observe()` 原先只把「增长」视为进展，回退不重置基线。而向后 seek 会让 position 与 buffered 双双低于已记录基线，于是 `shouldTimeout()` 里 `positionMs <= lastPositionMs` 恒成立，其后每个采样都被判为「无进展」，超时后误触发降级。

已改为：**采样回退即视为不连续（seek/flush），以新的较低基线重新 arm 并重置计时**，而非计入停滞。

原测试 `staleProgressDoesNotRearmTheClock` 锁的正是这个错误行为，已替换为 `regressedSampleRearmsBecauseItIsADiscontinuity` 与 `aBackwardSeekDoesNotInheritTheOldBaseline`。

**但「任何回退即 re-arm」本身又引入了「永不触发」的新洞**（自查发现）：position 持平而 buffered 小幅抖动（例如 9000→9300→9000→…）时，每次下降都重新 arm、每次上升也重置计时，计时永远累积不起来，原本要修的停滞问题就复活了。原先的 `Math.max` 累积恰好挡住了抖动。

故判据改为：**回退幅度超过 `DISCONTINUITY_TOLERANCE_MS = 1000` 才视为不连续并重置基线，小于该值按抖动忽略且不重置计时**；上升分支保留 `Math.max` 累积 —— 加了容差后它变成承重逻辑（容差内的小跌会落进上升分支，直接赋值会拉低基线，随后回弹又被当作进展形成锯齿）。

### 4. 重置基线无上界导致可被无限推迟（复审 P1，已修）

即便有容差，**周期性超过容差的回退**仍可每轮重置计时，使超时无限推迟，停滞问题复活。复审给出的数值序列：position 冻结、buffered 在 12000/10000 之间交替，每轮回退 2000ms > 容差，计时永远归零。

修法：引入 `EPISODE_CEILING_MS = 90_000` 绝对上限。区分两种操作 —— `arm()` 开启**新 episode**（重置 `episodeStartedAtMs`），仅在真实装载点与暂停轮询时调用；`observe()` 命中大回退时只 `rebaseline()`（**保留** `episodeStartedAtMs`）。于是无论采样如何波动，一段 BUFFERING 最多持续 90 秒即无条件触发，终止性可证。

暂停期间每轮 `arm()` 开新 episode，所以暂停时长不会累计进该上限。

与 `LOADING_STALL_TIMEOUT_MS = 60_000` 的数值关系是刻意的：上限高于 loading 宽限，因此**正常的长时间抓取不会被上限抢先杀掉** —— loading 且无进展时 60s 先触发，上限根本轮不到。上限只在「计时被反复重置」时生效，也就是它唯一要防的那种情形。若远程 MKV 尾部 Cues 抓取真的超过 90s 且期间还伴随大幅回退，会被上限终止；此时体感已经是坏的，触发降级比继续干等更合理。

测试：`repeatingLargeRegressionCannotDeferTheTimeoutForever`（锁定终止性）、`episodeCeilingDoesNotCountPausedTime`（锁定暂停不累计）、`toleranceDipWithGrowthKeepsTheHigherBaseline`（锁定 `Math.max` 承重行为 —— 复审指出此前无测试覆盖，改回直接赋值也能全绿）。

补充事实：真实用户 seek 多数**绕过** `PlayerManager.seekTo()` —— `PlaybackActivity:375`、`CustomSeekView:250`、`VideoActivity`(leanback 6439 / mobile 6832) 都经 controller 直达。故 `seekTo()` 内的 arm 只覆盖部分内部调用方；覆盖不缺是因为 BUFFERING 状态变更这条装载点仍然生效。

### 3. Spider.java 的 PyObject close 已整体撤销（必修）

以 `javap -p -c` 反汇编 `chaquopy_java-17.0.0.jar` 确认：

```java
private static final Map<Long, WeakReference<PyObject>> cache;
public static PyObject getInstance(long)   // 命中缓存返回同一实例
public void close()                        // 移出缓存 + closeNative() + addr = 0
```

同一 native Python 对象在 Java 侧是**同一个 PyObject 实例**，因此 close 会替所有持有者一起关闭，之后任何使用都抛 `PyObject is closed`。而新增的六处 close 关的多是共享单例：`callAttr("init"/"destroy"/"download")` 返回 `None`；`obj.put()` 返回旧值（首次为 `None`）；`o.type()` 返回**类型对象**（该类型所有实例共享、长生命周期）；`asMap()` 的 key/value 仍被 dict 持有且字符串可能是 interned。

该改动本是为「finalizer 压力」而加，而该假设**从未被证实**是本次故障成因（实测成因为停滞看门狗缺失与 FFmpeg 调优未启用）。收益未证实、风险已证实，故整体回退至 `origin/beta` 版本。若日后要重做，需先取得 finalizer 超时的实机证据，并逐个确认所关对象非共享单例。

验证：`ExoBufferingStallWatchdogTest` `tests="16" failures="0" errors="0"`、`PlaybackTraceTest` `tests="12" failures="0"`、`ExoFfmpegFallbackTuneTest` `tests="10" failures="0"`；`compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `:chaquo:compileArm64_v8aDebugSources` 均 `BUILD SUCCESSFUL`；`git diff --check` 通过。

经历两轮评审共修出四项：暂停误判（P0）、回退式 seek 基线失配（P0）、重新装载挂在不会派发的回调上（P0，复审发现）、重置基线无上界（P1，复审发现）。其中后两项与「任何回退即 re-arm 导致永不触发」都是我在修前一个问题时自己引入的，均由测试或复审拦下。

## 与内核回退链的时间上界（合并 beta 后确认）

合并 `origin/beta` 的 `0afdcbd7d1`（回退按 `KERNEL_ORDER` = EXO→IJK→MPV→系统 扫描）与 `7fe00bd7cf`（修标记表短于内核常量时的死循环）后，本看门狗成为该链的一个新触发点。终止性已逐环核对：

- `nextFallbackPlayer()` 先 `markPlayerFallbackTried(playerType)` 标记自身，循环内每个候选**取出即标记**，故每内核最多进入一次；全部标记后 `PlayerSetting.firstUntriedPlayer()` 返回 `NONE`，`fallbackPlayer` 终止并走 `callback.onError`。
- `playerFallbackTried` 仅在 `reset()`／`switchPlayer()`（手动切）／`start()`／`parse()` 重置，**不在 `fallbackPlayer()` 内重置**，故单个片源内不会反复耗尽整条链。
- `7fe00bd7cf` 的修复位于 `PlayerSetting:228-230`（越界内核按「已试过」跳过）。本看门狗走的是同一个函数，自动受该修复保护，无法绕过。

**已知时间上界（刻意接受，不加链级总时长上限）**：链长是 **8 步**而非 4 步 —— `getFailureFallback()` 默认 `FALLBACK_FULL`（`PlayerSetting:411`），故每个内核先降解码档再换内核，四内核 × HARD/SOFT 双档；且 `isAutoChange()` 默认 `true`（`PlayerSetting:402`），线路层面还有倍数。最坏情况八步各耗尽 `EPISODE_CEILING_MS` = 90s，约 **12 分钟**才向用户报错。

但这不是典型值：死源通常 `isLoading()` 为假，20s 即触发，八步合计约 160s。且 90s 上限现在**要求净进展不足 `SEGMENT_PROGRESS_MARGIN_MS`** 才生效（见下节），所以「慢但在推进」的会话不会被计入该长尾。改动前 BUFFERING 停滞**永不报错**，故即便最坏值仍是严格改进。加链级上限需引入跨会话状态与额外复杂度，收益不足，暂不做；若实机出现该长尾再单独处理。

### 上限必须与进展联合判定（复审 P0，已修）

初版的上限是**无条件**的：

```java
if (nowMs - episodeStartedAtMs >= EPISODE_CEILING_MS) return true;
```

于是「慢但在稳步推进」的会话也会在 90s 被杀 —— 大文件配慢链路完全可能花超过 90s 稳步填那 15s 阈值。这恰恰绕过了双条件判据本要保护的场景，是我加上限时引入的误杀。

修法：上限改为**同时**要求「已过 90s」**且**「净进展 < `SEGMENT_PROGRESS_MARGIN_MS` = 15_000」。净进展取 position 与 buffered 两者增量的较大值（任一轴前进都是真实前进，要求两者同时增长会杀掉「仅在填缓冲」的会话）。余量取 15s 量级与 `MAX_STREAMING_REBUFFER_MS` 对齐：既保住「正在填阈值」的会话，又不放过净进展仅 3s 的病态回退循环（`repeatingLargeRegressionCannotDeferTheTimeoutForever` 的净进展正是 3s，仍会触发）。

**水位必须随不连续重锚（自查发现，P0 从另一个门重回）。** 初次修 P0 时我把进展水位记在 `arm()`、`rebaseline()` 不动，理由是「维持回退只重置进展时钟、不重置 episode 的区分」。但这样一来，向后 seek 后水位仍是 seek **之前**的高位，`net = max(p - 旧高位, b - 旧高位)` 恒被 `Math.max(0, ...)` 夹成 0 —— 即使 position 一路上涨也永远读作零进展，90s 照样误杀。我用一个独立推演程序复现了它：`seek 后正常推进被误杀 = true 于 90000ms`。

因此进展水位属于**当前连续段**而非 episode，`rebaseline()` 必须一并重锚（字段已改名 `segmentStart*` 以反映真实语义）；而**时间锚 `episodeStartedAtMs` 保持不动**，那才是约束回退循环的东西。两者职责不同，不能同进同退。

修后同一程序四个场景全部正确：向后 seek 后推进不误杀、向后 seek 后停滞 21s 触发、病态回退循环 90s 终止、慢但在填缓冲不误杀。其中「回退循环终止」与「慢会话不被杀」是相互拉扯的一对，两者同时成立才说明判据到位。

新增 `ceilingSparesProgressAfterABackwardSeek` 与 `stallAfterABackwardSeekStillFires` 锁定这两侧。

**严重程度比我当时判断的高得多。** 我当时只从「向后 seek」这一面理解它，commit message 与文档也只写了那一面。实际的失效面是：**水位钉死时，凡 trough 高于 `arm + 余量` 的有界振荡都永久不触发** —— 即 E-SP3 立项要修的永久转圈从另一个门原样回归。**若按上一提交推送，等于推出一个比原 bug 更隐蔽的同类问题。**

该结论可从代码演绎：净进展自固定锚点起算，trough 高于锚点 + 15000 时每个采样的净进展都超余量，上限分支永不进；而大幅回退每轮重置 `lastProgressAtMs`，停滞分支也永不进。两个分支同时失效，故永不触发。

触发条件很日常 —— **播放中途的重缓冲**：此时 arm 时 buffered 已远高于 0，整段振荡自然位于 `arm + 15s` 之上。

参数扫描（本机复核，脚本非仓库产物）对比两版本，扫 arm 水位 × trough 抬升量 × 振幅 × 周期 × loading 共 3456 组：钉死版 1980 组永不触发，当前版 0 组；仅取 trough 高于 `arm + 余量` 的 1728 组子集，钉死版 1584 组永不触发，当前版仍为 0。

**关于比例的说明（重要）**：此前本文曾引用「9477 / 35100（27%）」并标注来源为复审。那是一次未经复核的转述，我把它写成了带权威感的实测事实 —— 这是我的错误。自行复核得到的是 57.3%（全空间）与 91.7%（靶心子集），与 27% 相差甚远。三个数字描述的是同一个定性事实，差异全部来自取样空间的选取。**比例是取样空间的产物，不是代码的性质**，故不应引用任何单一百分比作为该缺陷的度量。定性结论（钉死即永不触发）可演绎、可反向验证，是本节的真正依据；量化仅供感知规模，复现须自带扫描参数。

该严重区域此前只由 seek 用例间接守住，现补 `oscillationWhollyAboveTheArmWatermarkStillTerminates` 直测（trough 明确设在 `arm + 余量 + 30s` 之上）。反向验证：把水位改回钉在 `arm()`，该用例与 seek 用例双双变红。

终止性可证的机制：上限要被持续阻断，需每个采样都满足「当前 > 段起点 + 余量」；而 `rebaseline()` 会把段起点重锚到每个新低点，于是每次大回退都把门槛抬到新低点之上。有界信号的低点有上界，门槛终会高到无法跨过。只有无界上升能永久豁免 —— 而那本就是真实进展。

`SEGMENT_PROGRESS_MARGIN_MS / EPISODE_CEILING_MS = 15s/90s` 隐含一条吞吐门槛：**buffered 须维持 ≥ 1/6 实时速率**才能免于上限（复审实测边界卡在 +166ms/tick 触发、+167ms/tick 不触发）。持续低于该速率的流追不上播放，降级优于干等。该门槛此前完全隐式，现已写入 `SEGMENT_PROGRESS_MARGIN_MS` 的 javadoc —— 单独调任一常量改的就是这条门槛。

同时更正上一节曾写下的错误论证 —— 「60s 档先触发、90s 上限轮不到」**只在无进展时成立**，而这正是本 P0 的根因。

另新增 `ceilingSparesAGenuinelyProgressingEpisode`（每 tick 双双 +200ms，跨越上限仍不得触发）与 `ceilingStillFiresWhenNetProgressIsBelowTheMargin`。看门狗单测现共 20 条。

### 其余两项（复审 P1／P2，已修）

- **手动切内核后停滞不得劫持用户选择**：`onBufferingStall` 原先直接走 `fallbackPlayback`，而 `onPlaybackTimeout` 对 `manualPlayerSwitchPending` 是单独报错的。已补同形分支，遥测理由用 `manual-switch-stall` 以便与 `manual-switch-timeout` 区分入口。
- **新片源须失效旧基线**：`setMediaItemNow` 补 `cancelBufferingStallWatchdog()`，与紧邻的 `App.removeCallbacks(runnable)` 职责同构。只加在这个漏斗底部 —— `awaitLocalProxyAndSetMediaItem` 的异步路径最终也汇入此处，加在两处会让「取消发生在哪一刻」依赖代理是否就绪。

三项均以 `PlayerManagerLifecycleSourceTest` 的源码字符串断言锁定（本仓库既有约定），并已反向验证：删掉 `setMediaItemNow` 里那行会令 `newMediaItemCancelsTheStallWatchdog` 变红。其中一条断言还专门守住 BUFFERING 分支的 `!isArmed()` 守卫，防止后人为「修」P2 而删掉它 —— 那会让 seek 路径的 cancel+arm 退化成重复装载并永久重置基线。

## 回滚

恢复本任务的原子提交即可。不涉及依赖锁、AAR、native 二进制或 patch。
