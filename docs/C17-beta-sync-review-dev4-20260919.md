# C17：dev4 合并 beta 最新代码与合并后复评

> 状态：已完成。合并、两轮复评与验证通过，提交与恢复标签已创建，`dev4` 已推送，PR #317 已创建并可合并，远端最新状态已拉取。

## Recovery anchor

- 目标：将 `origin/beta@da00b7a1f20815fd49921d415c315c21fd6d8bb1` 合入 `dev4`，评审合并后的全部本地改动（包括 `c4fd5acd74` 等已提交未推送的 C16 提交），修复发现的问题，循环复评直到通过，然后提交、推送、创建 PR 到 `beta`，最后拉取远端最新代码。
- 本地基线：`dev4@c4fd5acd747e1a26c5dfc54273967fa0d01038de`，相对 `origin/dev4` 领先 11 个提交；工作树启动时干净。
- 合并基线：共同祖先 `32a52698e5dab09fe18e49d18849a947057ca717`；目标 beta 头 `da00b7a1f20815fd49921d415c315c21fd6d8bb1`。
- 范围：`app/**`、`docs/**`；不修改依赖、锁、二进制、爬虫 ABI 或公开接口，除非复评证明合并必须处理且仍在授权范围内。
- 回滚：最终合并提交以 `dev4` 当前头为第一父；如需回滚，使用 `git revert -m 1 <merge-commit>`。既有 C16 提交及恢复标签保持不动。
- 下一动作：无；等待 PR #317 的正常评审与合并。

## 合并提交账本

| 提交 | 内容 | 当前处置 |
| --- | --- | --- |
| `39a5f783e9d899cff4161d73fce09f038e84f3ab` | 避免启播阶段重复重建和缓冲的 Exo/播放器修复 | 待合并后复评 |
| `fe88163cf750e62b8aa664c74334955c59dfda54` | 节流预加载任务突发 | 待合并后复评 |
| `e87161fdd952c3dadb10a4223547641d2f4de0a5` | beta 同步合并提交 | 结构提交，检查最终树 |
| `837847cb8598f4a14bae5fdc8842620826d6e812` | beta PR #315 合并提交 | 结构提交，检查最终树 |
| `93c2413b022ea229ff6c1534f94cba569555b0f9` | Leanback 广告统计对话框电视端交互优化 | 待合并后复评 |
| `da00b7a1f20815fd49921d415c315c21fd6d8bb1` | beta PR #316 合并提交 | 结构提交，检查最终树 |

## 评审与验证计划

1. 以 `--no-commit --no-ff` 合并 `origin/beta`，确认无冲突或明确处理所有冲突。
2. 评审最终差异：C16 TMDB 详情协议与 beta 播放器/Leanback 改动是否有覆盖、语义冲突、生命周期或 UI 回归风险。
3. 对发现的问题做最小修复；每轮修复后先跑能直接证伪问题的定向检查，再进行一次合并树编译与相关测试。
4. 首轮通过后再次按需求逐项复评，确认没有遗漏或不一致，再进入提交与 PR。

## 证据记录

- 2026-09-19 Asia/Shanghai：确认工作树干净；抓取 `origin/beta` 和 `origin/dev4`；冻结上述基线并建立任务守卫。
- 2026-09-19 Asia/Shanghai：`git merge --no-commit --no-ff origin/beta` 自动完成，无未合并路径；评审确认 beta 播放器修正、预加载节流、Leanback 广告统计交互与 C16 TMDB 详情协议调用链相互独立，没有覆盖 C16 已提交但未推送的实现。
- 第一轮验证：`bash ./gradlew --console=plain :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 返回 `BUILD SUCCESSFUL in 26s`。
- 第一轮定向测试：`bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest` 配合 14 组过滤规则返回 `BUILD SUCCESSFUL in 34s`；覆盖 C16 解析/适配/合并/缓存/详情接线、TMDB UI、广告统计布局、预加载策略和播放器取址约束。
- 第二轮复评：最终树无冲突标记、无未合并路径、无越界或二进制变更；`git diff --check` 与 `git diff --cached --check` 均通过；6 个 beta 提交及其两个合并父关系保持完整，10 个 C16 非合并提交仍在最终树中。
- 复评结论：未发现需要修改的正确性、兼容性、性能、生命周期、资源或回滚问题；无需追加代码修复。
- 收口结果：合并提交 `5acd3f084155e7016b3798528eb8343f2f076177`；恢复标签 `recovery/C17-beta-sync-review-dev4-20260919/20260919122049-5acd3f084155`；`origin/dev4` 已更新到该提交。
- PR：<https://github.com/Silent1566/webhtv/pull/317>，目标分支 `beta`，创建时为非草稿、`MERGEABLE`、状态 `OPEN`。
- 远端同步：在推送后执行 `git pull --ff-only`，结果为 `Already up to date.`，当前 `dev4` 与 `origin/dev4` 一致。
