# C9：合并 origin/beta 最新应用修复

## Recovery anchor

- 目标：将 `origin/beta` 最新代码合并到当前 `dev4`，评审合并树及 E-SP7 相关改动，验证通过后提交、推送。
- 状态：合并已完成，E-SP7 与 beta 受影响测试已通过，待 task guard 完成原子提交、创建恢复标签并推送。
- 当前第一父基线：`dev4@ff438637b89587cf4f378843338a4122ba07e9d3`。
- beta 目标：`origin/beta@bcfe7b22a05e32913448a228f9513c690bc8233f`。
- 合并基线：`59fd2688f79d4e6ef46da23a162c8236920629e6`。
- 受保护路径：无；任务开始前工作区干净。
- 任务 guard：`C9-beta-sync`，模式 `standard`，范围为本次 beta 变更路径和三份任务记录。
- 回滚：提交前使用 `git merge --abort`；提交后使用本任务恢复 tag 或 `git revert` 回退两父合并提交。
- 下一动作：记录最终验证结果后执行 task guard finish，随后只推送当前 `dev4` 和新恢复标签。

## Authority and scope

- 用户已明确授权：拉取远端 beta 最新代码、合并到当前分支、评审全部相关修改（包括已提交未推送部分），发现问题则修改、验证并再次评审，循环至通过后提交、推送并拉取远端最新代码。
- 本任务不升级 FFmpeg、Media3、MPV、JNI、lock 或 native 二进制；只合并和评审 beta 已有应用/测试/文档树及当前本地移动端详情页滚动提交。
- C5/C6 已覆盖且在本次最终树中未改变的历史代码，仅依据既有评审记录跳过逐行重复检查；其与本次同名文件、生命周期和播放器状态交互仍纳入最终树审查。
- 评审边界：更新 APK 身份校验、片段查询/时间轴/自动跳过、实时字幕模型切换、MPV 预载与输出遮罩、TMDB/详情页状态、移动/电视布局，以及三项本地未推送移动端滚动提交。

## Frozen commit ledger

`origin/beta` 相对 C6 目标 `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` 的历史可达提交如下。合并提交、文档提交和已被当前第一父包含的提交均保留在账本中，不因功能影响为零而省略；本次 dev4 合并使用的最新 beta 增量另列在下方。

| # | Repository | Full commit | Parent(s) | Functional area | Disposition |
| ---: | --- | --- | --- | --- | --- |
| 1 | `origin/beta` | `d8910cc35a27c5761f8618ada758dac4efe1dca4` | `6ccc0de7f0060113bc52005b0d48a731407621df` | 片尾跳过、片段类型、查询缓存和确认模式扩展 | 最终树候选；重点复审时间轴和状态机 |
| 2 | `origin/beta` | `9e8e029665b1ee4de41c86de630047172667c9ec` | `d8910cc35a27c5761f8618ada758dac4efe1dca4`, `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976` | dev4 beta 集成承载 | 合并承载；处置跟随最终树 |
| 3 | `origin/beta` | `d1878923682800c448f946e07c847971a1d0d066` | `9e8e029665b1ee4de41c86de630047172667c9ec` | 片段落点、去重、提示和缓存修正 | 最终树候选；重点复审边界/去重 |
| 4 | `origin/beta` | `5a8122f8f6b3be38bcbb3f89f593ce08439040f5` | `a26a80d0c207c06d9e09192febee95ae976e8963` | 手动 TMDB 绑定恢复 | 当前第一父已包含；无新增有效差异 |
| 5 | `origin/beta` | `d5182a794ecdec79819dab782a3c3eb096aa3839` | `5a8122f8f6b3be38bcbb3f89f593ce08439040f5`, `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` | dev3 beta 集成承载 | 当前第一父已包含；无新增有效差异 |
| 6 | `origin/beta` | `b0af66ac567a85856509bb4cd042c3cbce7feee2` | `d5182a794ecdec79819dab782a3c3eb096aa3839` | 手动 TMDB 通配符和更新竞态 | 当前第一父已包含；交互随最终树复审 |
| 7 | `origin/beta` | `381266a579404087ac55de9af2bd99ac3c4f1bf1` | `d1878923682800c448f946e07c847971a1d0d066` | 片段状态机、无界片尾和失败缓存 | 最终树候选；重点复审重试/溢出 |
| 8 | `origin/beta` | `adbc9913857d7b1f406ae3ee00143d9e4c41aeda` | `b0af66ac567a85856509bb4cd042c3cbce7feee2` | TMDB 别名与全局标题域 | 当前第一父已包含；无新增有效差异 |
| 9 | `origin/beta` | `b7b2b1a6c92d4691e12c313e7111325c6f5d4113` | `db4b1650f73c819b3eebd7e7534e7b9e4ec65ff4` | 实时字幕原声识别语言快捷切换 | beta 新增候选；重点复审模型生命周期 |
| 10 | `origin/beta` | `9188c8a6a1919529438257bbc7c86bc2514f4fbe` | `c975ae1ed482a4bf47f106f5931bd2392e8ecce3`, `adbc9913857d7b1f406ae3ee00143d9e4c41aeda` | dev3 PR 集成承载 | 合并承载；处置跟随最终树 |
| 11 | `origin/beta` | `671888a5565bc6d26d523fa3e8087770b9567d99` | `381266a579404087ac55de9af2bd99ac3c4f1bf1`, `9188c8a6a1919529438257bbc7c86bc2514f4fbe` | dev4 beta 集成承载 | 合并承载；处置跟随最终树 |
| 12 | `origin/beta` | `a33ff92b8e65e11330ab17270b5f86a4c0b08183` | `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`, `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` | C6 合并及实时字幕竞态修复 | C6 已独立评审/验证；最终树交互复审 |
| 13 | `origin/beta` | `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b` | `a33ff92b8e65e11330ab17270b5f86a4c0b08183` | C6 文档收口 | 文档承载；处置跟随文档一致性 |
| 14 | `origin/beta` | `7db1b9d188e27877154757528d441150142b90ed` | `9188c8a6a1919529438257bbc7c86bc2514f4fbe`, `1b376ee0461fdf9a2e424c73cfb46f31f8301f9b` | beta 发布集成承载 | 合并承载；处置跟随最终树 |
| 15 | `origin/beta` | `624e0b16a33c765b1f9d4a96422fc48e4c403340` | `671888a5565bc6d26d523fa3e8087770b9567d99` | 播放器遮罩和预载回归修复 | beta 新增候选；复审 MPV 状态交互 |
| 16 | `origin/beta` | `db7218f51f0e7df5a0c0c3fd486932c6129a5818` | `624e0b16a33c765b1f9d4a96422fc48e4c403340`, `7db1b9d188e27877154757528d441150142b90ed` | dev4 合并承载 | 合并承载；处置跟随最终树 |
| 17 | `origin/beta` | `e7c1c9cccc7949022cae432085dcbdc2576e3fd4` | `7db1b9d188e27877154757528d441150142b90ed`, `db7218f51f0e7df5a0c0c3fd486932c6129a5818` | beta PR 集成承载 | 合并承载；处置跟随最终树 |
| 18 | `origin/beta` | `fddca6e60d6ab82f690484ca552cc8bc26210153` | `db7218f51f0e7df5a0c0c3fd486932c6129a5818` | APK 签名信息不可读兼容 | beta 新增候选；安全边界复审 |
| 19 | `origin/beta` | `c493488de47eca8ff08847cd190e47358c93994f` | `fddca6e60d6ab82f690484ca552cc8bc26210153`, `e7c1c9cccc7949022cae432085dcbdc2576e3fd4` | dev4 beta 集成承载 | 合并承载；处置跟随最终树 |
| 20 | `origin/beta` | `59fd2688f79d4e6ef46da23a162c8236920629e6` | `c493488de47eca8ff08847cd190e47358c93994f` | 收紧更新签名容错边界 | beta 新增候选；安全验证必需 |
| 21 | `origin/beta` | `308694aaadd59d9d1ef230bded83cf84dafa114c` | `e7c1c9cccc7949022cae432085dcbdc2576e3fd4`, `59fd2688f79d4e6ef46da23a162c8236920629e6` | beta 发布目标合并 | 最终目标；处置跟随最终树 |

当前 `dev3` 未推送提交也纳入评审边界：

| # | Full commit | Area | Disposition |
| ---: | --- | --- | --- |
| 1 | `1c1ab72ec8cbfca852e5c3191f4ec8c0eabb1d05` | 移动端原生增强详情页可用高度 | 已提交未推送；最终树复审 |
| 2 | `ddb9045561faec417555e764806f66736469c030` | 原生增强集数区域外层滚动高度 | 已提交未推送；最终树复审 |
| 3 | `c0192b6c716451d284e2d4b04c71000950413223` | backdrop 集数区域外层滚动和触摸交接 | 已提交未推送；最终树复审 |

## Latest beta extension used by this merge

本次实际目标为 `origin/beta@bcfe7b22a05e32913448a228f9513c690bc8233f`，相对当前第一父 `dev4@ff438637b89587cf4f378843338a4122ba07e9d3` 的共同基线 `59fd2688f79d4e6ef46da23a162c8236920629e6` 新增以下 9 个完整提交：

| # | Full commit | Parent(s) | Functional area | Disposition |
| ---: | --- | --- | --- | --- |
| 1 | `1c1ab72ec8cbfca852e5c3191f4ec8c0eabb1d05` | `adbc9913857d7b1f406ae3ee00143d9e4c41aeda` | 移动端原生增强详情页可用高度 | 已合并；由布局测试覆盖 |
| 2 | `ddb9045561faec417555e764806f66736469c030` | `1c1ab72ec8cbfca852e5c3191f4ec8c0eabb1d05` | 原生增强详情页集数区域外层滚动高度 | 已合并；由布局测试覆盖 |
| 3 | `308694aaadd59d9d1ef230bded83cf84dafa114c` | `e7c1c9cccc7949022cae432085dcbdc2576e3fd4`, `59fd2688f79d4e6ef46da23a162c8236920629e6` | beta 发布集成承载 | 合并承载；处置跟随最终树 |
| 4 | `c0192b6c716451d284e2d4b04c71000950413223` | `ddb9045561faec417555e764806f66736469c030` | backdrop 集数区域外层滚动和触摸交接 | 已合并；由布局测试覆盖 |
| 5 | `0c71cad17573f7fe36458a4785ee969ba36da171` | `308694aaadd59d9d1ef230bded83cf84dafa114c` | 电视端速度加速键失焦/漏释放修复 | 已合并；由布局测试覆盖 |
| 6 | `97e980c8bda8af2187ac7e678ca59d5c78dbd40e` | `c0192b6c716451d284e2d4b04c71000950413223`, `308694aaadd59d9d1ef230bded83cf84dafa114c` | beta 同步复审与最终修复承载 | 最终树已复审 |
| 7 | `9b2f02bd302c5dcb52352716ae2be0b9d84188f5` | `308694aaadd59d9d1ef230bded83cf84dafa114c`, `97e980c8bda8af2187ac7e678ca59d5c78dbd40e` | dev3 PR 合并承载 | 合并承载；处置跟随最终树 |
| 8 | `0c62583d4d9e86b672850d737ca9ef9e57dec3fc` | `0c71cad17573f7fe36458a4785ee969ba36da171`, `9b2f02bd302c5dcb52352716ae2be0b9d84188f5` | beta 分支合并承载 | 合并承载；处置跟随最终树 |
| 9 | `bcfe7b22a05e32913448a228f9513c690bc8233f` | `9b2f02bd302c5dcb52352716ae2be0b9d84188f5`, `0c62583d4d9e86b672850d737ca9ef9e57dec3fc` | beta 最新发布目标合并 | 本次合并目标；已完成最终树复审 |

## Current behavior and decision

- 合并方式：当前 `dev3` 作为第一父，`origin/beta` 作为第二父，已执行 `git merge --no-commit --no-ff origin/beta`，Git 自动合并成功，无未合并路径。
- 已确认的第一轮风险：`IntroSkipPlayback` 使用“片段类型 + provider”作为单集去重键，同一 provider 返回多个同类片段时会误跳过后续片段；续播位置落在片头内部时，自动跳过状态机缺少与片尾等价的续播抑制；`IntroSkipService` 的极端时长差值比较可能发生 `long` 溢出；Activity 销毁时未统一使异步片段查询回调失效。
- 待定安全项：`Updater.signaturesMatch()` 在候选包签名信息不可读时放行到系统安装器。需要结合包名、版本、文件长度、SHA-256 和系统安装器边界判定是否为可接受兼容策略；在证据不足前不扩大放行范围。
- no-change 方案会保留当前 dev3，但缺少 beta 的更新校验、实时字幕快捷切换和已修复的播放/详情页行为；盲目替换 beta 会丢失 dev3 的三项移动滚动修复，因此采用保留第一父的合并方案。
- 当前适配原则：保留 beta 的最终功能设计，只修复可由本地测试证明的状态、边界和生命周期问题；不引入新的播放器架构或依赖。

## Review and validation plan

1. 完成第一轮静态审查和两个独立只读审查意见的核对；Critical/Important 必须逐项验证根因。
2. 对确认的问题先写最小失败测试，再修改最小生产代码；每个修复后运行受影响的定向测试。
3. 修复后重新审查完整修复差异，确认没有新的状态/线程/兼容性回归；重复至无未处理 Critical/Important。
4. 最终验证至少包括：冲突标记扫描、`git diff --check`、片段解析/状态/时间边界测试、实时字幕模型测试、TrackDialog 测试、移动详情布局测试、Mobile/Leanback Arm64 Java 编译，以及当前仓库既有的 MPV asset ELF 门禁（不重建 native）。
5. 真实设备播放、APK 安装器对不可读签名信息的 OEM 行为若当前环境无法提供，只记录为限制，不用编译结果冒充运行时证据。

## Acceptance and rollback

- 21 个 beta 增量提交和 3 个本地未推送提交均有完整处置，合并树无冲突路径和冲突标记。
- 同一数据源的多个片段可以独立按顺序处理；续播不会误跳当前片段；异常/极端时间输入不会溢出并产生错误落点；Activity 销毁后异步回调不再更新已销毁页面。
- 实时字幕模型切换保持播放会话和音频管线，旧请求/旧识别器回调不能改变新状态。
- 更新校验仍保留包名、版本、文件长度和 SHA-256 门槛；签名不可读的兼容策略必须有明确安全理由和测试证据。
- Mobile/Leanback 受影响 Java 代码编译通过，定向测试无失败、错误或跳过；未能执行的设备场景明确记录。
- 提交使用 task guard 原子生成，并立即创建唯一 annotated recovery tag；随后只推送当前分支和新 tag，再执行 `git pull --ff-only`。

## Implementation log

- 2026-09-03 Asia/Shanghai：记录初始工作区 `dev3@c0192b6c716451d284e2d4b04c71000950413223`，保护既有未跟踪 `.claude/`。
- 2026-09-03 Asia/Shanghai：刷新 `origin/beta`，确认目标 `308694aaadd59d9d1ef230bded83cf84dafa114c`；确认 C6 目标 `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` 为其祖先，完整新增范围为 21 个提交。
- 2026-09-03 Asia/Shanghai：启动 guard `review-beta-sync-20260903`，范围 `app`、`docs`；首次 bash 调用因环境无 WSL 失败，改用 `G:/Git/bin/bash.exe` 后启动成功。
- 2026-09-03 Asia/Shanghai：执行 `git merge --no-commit --no-ff origin/beta`，自动合并成功；当前 `HEAD` 未移动，`MERGE_HEAD` 为 beta 目标。

## Checkpoint 3: 2026-09-03 dev4 beta merge and E-SP7 review closure

- Current merge: first parent `ff438637b89587cf4f378843338a4122ba07e9d3`, second parent `bcfe7b22a05e32913448a228f9513c690bc8233f`, merge base `59fd2688f79d4e6ef46da23a162c8236920629e6`; only the assessment index conflicted and was resolved by retaining both C9 and E-SP7 rows.
- E-SP7 review: `a228e988d488f178890b64592f9dd89761f8e011` remains unchanged; `applyVideoLimit()` still uses `setForceHighestSupportedBitrate(false)`, and the beta tree does not modify `ExoUtil.java`, `ExoUtilTest.java`, or E-SP7 runtime code.
- Validation: E-SP7 `ExoUtilTest` passed; Mobile and Leanback Arm64 Java compilation passed; `IntroSkipServiceTest` 22/22 and `VideoActivityLayoutTest` 153/153 passed, with zero failures, errors, or skips.
- Review decision: no Critical/Important issue remains in the final beta delta; existing Gradle deprecation and `CXX5202` 32-bit native-library warnings are pre-existing.
- Remaining limitation: no Dangbei X7 Ultra playback A/B was available, so codec/frame-drop behavior remains a device follow-up and is not claimed as fully device-validated here.
- Next action: run task guard finish, create the annotated recovery tag, and push `dev4` plus the new tag.

## Checkpoint 2: 2026-09-03 dev4 beta 合并与 E-SP7 复核

- 合并树：第一父 `ff438637b89587cf4f378843338a4122ba07e9d3`，第二父 `bcfe7b22a05e32913448a228f9513c690bc8233f`，合并基线 `59fd2688f79d4e6ef46da23a162c8236920629e6`；唯一冲突为总评估索引的 C9/E-SP7 行，已保留两项记录并清除冲突标记。
- E-SP7 复核：`a228e988d488f178890b64592f9dd89761f8e011` 的 `ExoUtil.applyVideoLimit()` 仍保持 `setForceHighestSupportedBitrate(false)`；beta 暂存树不包含 `ExoUtil.java`、`ExoUtilTest.java` 或 E-SP7 业务代码覆盖。
- E-SP7 验证：`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoUtilTest --no-daemon` 通过；Mobile 与 Leanback Arm64 Java 编译均通过，Leanback 最终执行 `BUILD SUCCESSFUL in 5m 59s`。
- beta 受影响测试：`IntroSkipServiceTest` 22 项、`VideoActivityLayoutTest` 153 项，共 175 项，失败 0、错误 0、跳过 0。
- 评审结论：片段身份改为原始边界稳定身份，电视端速度键在失焦、按键释放和退出时释放，移动详情页外层滚动与 episode viewport 约束接线完整；未发现应在本次合并前修复的 Critical/Important 问题。
- 现有警告：Gradle deprecation 和仓库已有的 `CXX5202` 32 位 native library 警告，均未由本次 beta 变更新增。
- 下一步：执行 task guard 原子提交、恢复标签和推送；真实设备播放仍作为后续补验，不阻断本次源码合并。

## Checkpoint 1: merged tree awaiting first review

- Completed: beta 合并完成，无冲突；已核对 C6 历史覆盖边界、21 个 beta 增量提交和 3 个本地未推送提交。
- Source identities: first parent `c0192b6c716451d284e2d4b04c71000950413223`; beta `308694aaadd59d9d1ef230bded83cf84dafa114c`; merge base `adbc9913857d7b1f406ae3ee00143d9e4c41aeda`。
- Workspace: branch `dev3`; `HEAD` 保持第一父；`MERGE_HEAD` 指向 beta；`.claude/` 是受保护初始脏路径。
- Files changed: 合并暂存树涉及 `app`、`docs` 共 27 个有效路径；本地滚动提交与 beta 的两个 `VideoActivity`/测试文件发生最终树交互。
- Review evidence: 已发现需验证的片段身份、续播、极端时间和 Activity 回调生命周期风险；更新签名容错仍待安全边界核验；两路独立只读评审已发起，尚未返回。
- Validation: 合并暂存 diff 无 whitespace 错误；尚未运行修复前回归测试。
- Rollback anchor: 未提交合并可用 `git merge --abort`；不触碰 `.claude/`。
- Unresolved: 第一轮 RED 测试、评审意见核对、修复、最终测试/编译、commit/tag/push/pull 均未完成。
- Next action: 添加最小失败回归测试，先证明同源多段片段身份和续播片头抑制缺陷，再实施对应窄修复。
