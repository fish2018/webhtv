# C6：合并 origin/beta 最新应用修复

## Recovery anchor

- 目标：将 `origin/beta` 最新头合并到当前 `dev2`，评审合并树及已提交未推送的实时字幕快捷切换代码，修复有效问题并循环复审至通过，提交、推送并拉取远端最新代码。
- 状态：已完成并推送；评审问题已修复，最终验证通过。
- 当前基线：`dev2@a33ff92b8e65e11330ab17270b5f86a4c0b08183`。
- beta 目标：`origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`。
- 合并基线：`db4b1650f73c819b3eebd7e7534e7b9e4ec65ff4`。
- 受保护路径：`app/src/main/java/com/fongmi/android/tv/bean/Result.java`，本任务不采用、不修改、不提交。
- 回滚：本次合并已提交，使用 `git revert a33ff92b8e65e11330ab17270b5f86a4c0b08183` 回退；恢复 tag 保留为回滚锚点。
- 下一动作：代码任务已闭合；有设备时补验原生增强详情页和实时字幕关闭/切换失败场景。

## Authority and scope

- 用户已明确授权：拉取 beta 最新代码、合并到当前 `dev2`、评审全部相关修改（包括已提交未推送提交）、修复后验证并再次评审，循环至通过，提交、推送和 `git pull`。
- 本任务 guard：`C6-beta-sync`，模式 `upstream`。
- 本任务不升级 FFmpeg、Media3、MPV、JNI、lock 或 native 二进制；beta 的有效树差异仅涉及应用播放页和对应测试。
- 评审边界：beta 有效差异、当前未被 `origin/dev2` 包含的本地字幕提交，以及二者在播放器生命周期、加载状态、字幕/播放入口上的交互。

## Frozen beta ledger

| # | Repository | Full commit | Parent(s) | Functional area | Disposition |
| ---: | --- | --- | --- | --- | --- |
| 1 | `origin/beta` | `ab3093072fc075d81da02bacb02454779f9ccb82` | `b3fab34cab38f89384b7e1065e27094f07b99ec1` | Leanback/mobile 原生增强详情页避免双层 loading | beta 功能提交；合并后按最终树复审 |
| 2 | `origin/beta` | `826a4c10bef026222e49915979ad968bc8ceea44` | `ab3093072fc075d81da02bacb02454779f9ccb82` | 手机端原生增强 shell/backdrop/loading 修复 | beta 功能提交；合并后按最终树复审 |
| 3 | `origin/beta` | `a26a80d0c207c06d9e09192febee95ae976e8963` | `826a4c10bef026222e49915979ad968bc8ceea44` + `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976` | dev3 合并 beta 的集成承载 | 合并提交；处置跟随最终树 |
| 4 | `origin/beta` | `abe3872a351f828a6375261a39ef648f94076288` | `c1c53da674c0e0f2945fdc159de8a6ac4c4fe976` + `a26a80d0c207c06d9e09192febee95ae976e8963` | dev3 合并回 beta 的集成承载 | 合并提交；处置跟随最终树 |
| 5 | `origin/beta` | `c975ae1ed482a4bf47f106f5931bd2392e8ecce3` | `abe3872a351f828a6375261a39ef648f94076288` + `db4b1650f73c819b3eebd7e7534e7b9e4ec65ff4` | beta 发布目标合并提交 | 合并提交；处置跟随最终树 |

## Effective tree difference

`origin/beta` 相对当前 `dev2` 的有效差异在合并前已确认无冲突，路径为：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`

当前 `dev2` 相对 `origin/dev2` 的未推送提交为：

- `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`：播放字幕界面快捷切换实时原声识别语言，涉及 `TrackDialog`、`RealtimeSubtitleController`、两端布局、语言资源和 `TrackDialogTest`。

## Review and validation plan

1. 合并后检查冲突标记、完整有效差异和 beta 变更是否覆盖/破坏实时字幕入口。
2. 独立评审最终树，重点检查播放页 loading/reveal 状态、生命周期竞态、字幕控件可达性、移动端与电视端一致性，以及已提交字幕切换的模型代次保护。
3. 对每个 Critical/Important 问题先验证根因，再按 TDD 增加回归测试，修复后运行最小验证并重新评审完整修复差异。
4. 最小验证：`git diff --check`、冲突标记扫描、受影响的字幕/播放页测试、Mobile/Leanback Arm64 Java 编译；不因本次仅 Java/UI 合并重复 native 重建。
5. 提交前由 task guard 原子提交并创建恢复 tag；随后只推送当前 `dev2` 和新建恢复 tag，再执行 `git pull --ff-only`。

## Acceptance and rollback

- beta 的 5 个完整 commit ID 均有处置，最终树无未合并路径和冲突标记。
- 播放字幕界面仍可直接选择原声识别语言；实时识别输出继续为简体中文；切换不改变播放位置、不误重建播放器/音频管线。
- beta 的原生增强详情页 loading/backdrop 行为与字幕入口共存，移动端和电视端编译通过。
- 评审无未处理 Critical/Important 问题；测试失败若属于无关既有问题则记录而不扩大范围。
- 本地回滚锚点为当前基线 `b7b2b1a6c92d4691e12c313e7111325c6f5d4113` 及其恢复 tag `recovery/realtime-subtitle-language-quick-switch/20260902031226-b7b2b1a6c92d`。

## Implementation log

- 2026-09-02 Asia/Shanghai：刷新 `origin/beta`，确认目标头 `c975ae1ed482a4bf47f106f5931bd2392e8ecce3`；`git merge-tree --write-tree HEAD origin/beta` 返回无冲突树 `44e8345c83abd9525e5b4dce9d56560f4474627e`。
- 2026-09-02 Asia/Shanghai：启动 `C6-beta-sync` guard，保护预先存在的 `Result.java`。
- 2026-09-02 Asia/Shanghai：执行 `git merge --no-commit --no-ff origin/beta`，Git 报告自动合并成功；task guard check 通过，未发现未合并路径。

## Current status

- 合并：已完成并创建提交 `a33ff92b8e65e11330ab17270b5f86a4c0b08183`，包含 beta 有效树和字幕竞态修复。
- 评审：已修复独立审查发现的两项 Important 和一项 Medium：模型切换失败回退代次竞态、Mobile 缓冲覆盖 shell、Leanback 无初始预览焦点恢复；未发现 Critical。
- 验证：修复后的四个目标测试类共 171 项全部通过（failures/errors/skipped 均为 0）；Mobile 与 Leanback Arm64 Java 编译均 `BUILD SUCCESSFUL`。
- 提交/推送/拉取：已完成；恢复 tag 为 `recovery/C6-beta-sync/20260902090623-a33ff92b8e65`，`dev2` 与该 tag 已推送，`git pull --ff-only` 返回 `up-to-date`。

## Checkpoint 1: 2026-09-02 13:55 Asia/Shanghai

- Completed: 恢复上一会话并核对当前工作树；beta 合并仍未提交，原始实时字幕快捷切换提交仍在 HEAD，新增回归断言已存在。
- Source identities: `origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`; local rollback anchor `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`。
- Decisions/evidence: `RealtimeSubtitleController.prepareModel()` 在 `enabled = true` 后无条件排队 `disableNativeSubtitle()`；`disable()` 只取消 ticker，不能取消已排队 lambda。回归断言要求回调重新检查 `request` 与 `enabled`。
- Workspace: branch `dev2`, HEAD `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`; `Result.java` 及其他 beta 合并路径保持为受保护/既有改动。
- Files/artifacts changed: 本 checkpoint 仅更新本任务文档；生产控制器尚未修改。
- Validation: task guard `check` 已通过；RED 单测尚未运行。
- Rollback anchor: 合并未提交前可用 `git merge --abort`；本次竞态修复不改变该回滚锚点。
- Unresolved: 需要先观察现有 `TrackDialogTest` 是否因控制器缺少保护而失败，再实施一个 lambda guard。
- Next action: 运行最终定向测试与 Mobile/Leanback Arm64 Java 编译批次。

## Checkpoint 2: 2026-09-02 14:35 Asia/Shanghai

- Completed: `TrackDialogTest` 已完成 RED/GREEN；未修复树中 10 项测试有 1 项在新竞态断言失败，加入主线程回调代次/启用状态保护后同一测试通过。
- Repairs: `RealtimeSubtitleController.prepareModel()` 的已排队主线程回调现在先检查 `request != generation || !enabled`；`VideoActivityLayoutTest` 的内核切换断言改为限定实际方法体并检查请求编号、当前播放上下文和提前返回，修正两个测试边界误用。
- Validation: `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.TrackDialogTest --no-daemon` 通过；`... --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest --no-daemon --console=plain` 通过。此前布局测试失败均定位为测试文本/截取边界问题，未改生产播放器逻辑。
- Review: 本地复核确认关闭、模型切换、失败恢复路径仍由 generation 和 enabled 保护；独立只读审查已发起，待收取结果。
- Workspace: branch `dev2`, HEAD `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`; beta 合并差异仍在 index，字幕控制器、两项测试和文档为工作树改动；`Result.java` 及其他初始脏路径未触碰。
- Rollback anchor: 未提交合并仍可用 `git merge --abort`；完成提交后以 task guard recovery tag 或 `git revert` 回滚。
- Unresolved: 最终定向测试/双端编译、最终 guard check、原子提交与远端同步尚未完成；设备播放回归仍不是本地可执行证据。
- Next action: 执行一次最终 Gradle 定向测试与双端 Java 编译，并保留完整结果。

## Checkpoint 3: 2026-09-02 16:26 Asia/Shanghai

- Completed: 完成最终定向验证和双端 Java 编译；最终树无冲突路径，准备进行合并提交。
- Repairs: `RealtimeSubtitleController.prepareModel()` 的已排队主线程回调先检查 `request != generation || !enabled`，关闭实时字幕后不会再误禁用原生字幕；`VideoActivityLayoutTest` 的内核切换断言限定到实际方法体，检查请求编号、当前播放上下文和先行返回。
- Validation: `:app:testMobileArm64_v8aDebugUnitTest` 目标为 `TrackDialogTest`、`VideoActivityLayoutTest`、`RealtimeSubtitleModelCatalogTest`、`RealtimeSubtitleTranslatorTest`，JUnit XML 分别为 10、150、5、6 项，均 `failures=0 errors=0 skipped=0`；`:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`。
- Review: 本地最终树复核已覆盖 beta loading/shell/backdrop/focus、内核切换异步请求和实时字幕 enable/disable/switchModel/failure 路径，未发现 Critical/Important。独立只读审查代理在两个 300 秒等待窗口内未返回，未将其当作通过证据。
- Workspace: branch `dev2`, HEAD `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`, `MERGE_HEAD=origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`；C6 任务路径待由 guard 提交，`Result.java` 仍为受保护的预先脏改动。
- Rollback anchor: 提交前使用 `git merge --abort`；提交后使用本次恢复 tag 或 `git revert` 回退合并提交。
- Unresolved: 未执行真实设备播放回归；需在有设备时补验原生增强详情页 loading/backdrop、内核选择和实时字幕关闭交互。提交、恢复 tag、推送和 `git pull --ff-only` 尚未完成。
- Next action: 修复 C6 guard 的 `Result.java` 保护指纹后运行 `task_guard.sh finish`，创建合并提交和恢复 tag。

## Checkpoint 4: 2026-09-02 16:53 Asia/Shanghai

- Completed: 收取独立审查结果并核实三项反馈；新增三条回归断言均在修复前按预期失败。控制器失败回退修复后 `TrackDialogTest` 通过。
- Repairs: replacing 失败回退把 generation 检查、旧会话状态恢复置于同步块，并给回退 ticker 加 `request/generation/enabled` 保护；Mobile `showProgress()` 在原生增强富集期间不再切回整页 loading；Leanback shell 从隐藏态揭开时仅在根视图无焦点的情况下异步恢复播放器焦点，并在执行前再次检查。
- Validation: RED 批次显示 `TrackDialogTest`、Mobile 缓冲保护和 Leanback 焦点保护共 3 项失败；控制器修复后 `TrackDialogTest` 返回 `BUILD SUCCESSFUL`。其余两项生产修复后的最终详情布局测试和双端 Java 编译待执行。
- Review: 独立审查报告发现 2 项 Important、1 项 Medium，均已逐项核实并修复；未发现 Critical。设备验证风险仍保留。
- Workspace: branch `dev2`, HEAD `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`, `MERGE_HEAD=origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`; `Result.java` 继续作为受保护初始脏路径。
- Rollback anchor: 提交前 `git merge --abort`；提交后用 task guard recovery tag 或 `git revert` 回退。
- Unresolved: 最终定向测试和双端 Java 编译、guard check、原子提交/tag、推送和 `git pull --ff-only` 尚未完成；无设备播放验证。
- Next action: 运行修复后的完整 C6 定向测试并编译 Mobile/Leanback Arm64 Java。

## Checkpoint 5: 2026-09-02 16:54 Asia/Shanghai

- Completed: 完成独立审查反馈的逐项核实、TDD RED/GREEN 修复和最终验证，C6 合并树准备 closure。
- Review: 独立审查报告发现 2 项 Important、1 项 Medium，均确认属于当前代码真实缺口并已修复；未发现 Critical。审查席位未修改工作区。
- Repairs: replacing 失败回退在同步块内复核 generation 并保护 ticker；Mobile 播放器缓冲不再在原生增强 TMDB 富集期间切回整页 loading；Leanback shell 从隐藏态揭开时仅在根视图无焦点时异步恢复播放器焦点，并在执行前复核。
- Validation: `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.TrackDialogTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleModelCatalogTest --tests com.fongmi.android.tv.subtitle.RealtimeSubtitleTranslatorTest --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`；JUnit 统计分别为 10、150、5、6 项，均 `failures=0 errors=0 skipped=0`。`:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`。
- Workspace: branch `dev2`, HEAD `b7b2b1a6c92d4691e12c313e7111325c6f5d4113`, `MERGE_HEAD=origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`; `Result.java` 仍是受保护的预先脏改动，未纳入本任务。
- Rollback anchor: 提交前使用 `git merge --abort`；提交后使用本次 task guard recovery tag 或 `git revert` 回退合并提交。
- Unresolved: 真实设备播放验证仍未执行；需在 Mobile/Leanback 上补验慢速/失败 backdrop、TMDB 不匹配回退、无初始预览焦点、缓冲状态和实时字幕“切换失败后立即关闭/再次启用”。提交、恢复 tag、推送和 `git pull --ff-only` 尚未完成。
- Next action: 执行最终 guard check 后运行 `task_guard.sh finish`，创建 C6 合并提交和恢复 tag。

## Checkpoint 6: 2026-09-02 17:25 Asia/Shanghai

- Completed: C6 合并、评审修复、最终验证、原子提交、恢复 tag、远端推送和 fast-forward 拉取全部完成。
- Source identities: `origin/beta@c975ae1ed482a4bf47f106f5931bd2392e8ecce3`；local/remote `dev2@a33ff92b8e65e11330ab17270b5f86a4c0b08183`；beta effective merge base `db4b1650f73c819b3eebd7e7534e7b9e4ec65ff4`。
- Implementation: `a33ff92b8e65e11330ab17270b5f86a4c0b08183`；task guard recovery tag `recovery/C6-beta-sync/20260902090623-a33ff92b8e65`。
- Review: 独立只读审查发现 2 项 Important、1 项 Medium，均确认属于当前代码真实缺口并已修复；最终本地复核未发现 Critical/Important。
- Validation: `TrackDialogTest` 10 项、`VideoActivityLayoutTest` 150 项、`RealtimeSubtitleModelCatalogTest` 5 项、`RealtimeSubtitleTranslatorTest` 6 项，合计 171 项均 `failures=0 errors=0 skipped=0`；Mobile/Leanback Arm64 Java 编译均返回 `BUILD SUCCESSFUL`；`git diff --check`、冲突标记扫描和 task guard check 通过。
- Remote: 首次默认 HTTPS 推送遇到 `SSL_ERROR_SYSCALL`；使用 Git `HTTP/1.1` 重试后 `dev2` 和恢复 tag 均推送成功，随后 `git pull --ff-only` 返回 `up-to-date`。
- Workspace: branch `dev2`, HEAD `a33ff92b8e65e11330ab17270b5f86a4c0b08183`；仅保留受保护的预先脏改动 `app/src/main/java/com/fongmi/android/tv/bean/Result.java`，未纳入 C6 提交。
- Rollback anchor: 使用 `git revert a33ff92b8e65e11330ab17270b5f86a4c0b08183` 回退本次合并；恢复 tag 指向同一已验证提交。
- Unresolved: 尚未执行真实设备播放回归；需补验 Mobile API 29/31/35 动态取色、慢速/失败 backdrop、TMDB 不匹配回退、Leanback 无初始预览焦点、播放器缓冲，以及实时字幕“切换失败后立即关闭/再次启用”。
- Next action: 有设备时执行上述代表性播放回归；在此之前不再修改 C6 代码或构建产物。
