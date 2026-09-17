# dev2 beta 合并后复评记录（2026-09-14）

## Recovery anchor

- 目标：合并远端 `origin/beta` 最新代码，复评增量及已提交未推送改动，必要时修复并验证，最后提交、推送、创建中文 PR，并拉取远端最新代码。
- 接受标准：远端 beta 增量已合入且无冲突；相对合并基线的 beta 侧无内容差异；本次改动通过移动端定向测试和 Leanback Java 编译；原子提交、恢复标签、推送、中文 PR、PR 后回拉完成。
- 当前状态：本记录第一轮复评已收口；后续推送、PR 和回拉待本记录提交后继续。
- 下一步：推送 `dev2`，创建 base `beta` / head `dev2` 的中文 PR，并在 PR 后拉取远端最新代码。

## 第二轮合并与复评（2026-09-14）

- 远端 `origin/beta` @ `06442ea996fc8e964f51da973507b8ac3fa8d186`；合并前本地 `dev2` @ `d9bcceb9b4119488d51f30394d3364c5e1f20552`。`git merge-tree --write-tree` 无冲突，随后合并生成 `3cb0a94dcdbda0195edf167a4c28321162654a37`。
- beta 侧新增 `cf1a3eef83e2c207a1627c24b82379dc9f6d9184`（恢复 TV 控制栏确认动作）与文档记录提交 `968aedb88176ede3f9a2cc7965e42134a12266b7`。复评确认无代码问题：TV 按钮监听完整、测试覆盖相关确认链路，且当前合并不缺 beta 内容。
- 定向验证 `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.PlayerControlFocusIntegrationTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 返回 `BUILD SUCCESSFUL in 35s`；`git diff --check HEAD` 通过，工作区干净。
- 复评循环第一轮通过，无修复需求；推送、PR 与回拉按交付计划继续。

## 评审范围与结论

- 远端 `origin/beta` @ `058a5e22d1`；本地 `dev2` @ `8ae8bd1ce6`。`git fetch origin beta` 已执行。
- 本轮合并 `fcf0285d0e5f2d470999538dad797a54d4a8c690`，无冲突。该 beta 提交只删除两个陈旧 `FfmpegVc1SupportTest` Java 行为断言并更新 `dev1` 评审文档；当前测试仍保留双 ABI `libavcodec.so` 中 VC-1 decoder 的存在性校验，未把依赖接线缺口伪装成已修复。
- 相对合并基线 `fcf0285d0e5f2d470999538dad797a54d4a8c690`，beta 侧没有非 merge 内容提交；`git diff --stat $(git merge-base dev2 origin/beta)..origin/beta` 为空。当前 `dev2` 侧只有 `1b2dda6b98` 的 3 个路径：leanback/mobile `VideoActivity` 的 `shouldRevealShellWhileLoading()` 让影视原生模式也由播放器窗口表达加载态，以及对应 `VideoActivityLayoutTest` 防回归断言。
- 复评通过：上述 Java/测试改动与合并基线一致，`git diff --check` 无问题；`VideoActivityLayoutTest` 覆盖移动端行为，Leanback Java 编译覆盖 TV 端同源逻辑。

## 验证证据

- `git fetch origin beta`：远端状态确认。
- `git merge fcf0285d0e5f2d470999538dad797a54d4a8c690`：`Merge made by the 'ort' strategy.`，无冲突。
- `./gradlew :app:testMobileArmeabi_v7aDebugUnitTest --tests VideoActivityLayoutTest`：`BUILD SUCCESSFUL in 24s`。
- `./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL in 8s`。
- `git diff --check`：通过。
- `git diff --stat $(git merge-base dev2 origin/beta)..origin/beta`：空，证明 beta 增量内容已全部收敛到本地。
