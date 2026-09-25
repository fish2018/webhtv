# C14：dev4 合并 beta 最新代码并复评未推送改动（2026-09-11）

## Recovery anchor

- **目标：** 将 `origin/beta` 最新代码合入 `dev4`，评审合并树与已提交未推送的 `08b8bbc5401c32cc562d403e96f95ece3db5c029`，修复所有本任务发现的问题，验证并复评至通过，提交、推送 `dev4`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
- **状态：** 合并、修复、双端编译与聚焦复评已通过，待任务守卫提交合并单元、推送和创建 PR。
- **分支/基线：** `dev4@08b8bbc5401c32cc562d403e96f95ece3db5c029`；`origin/dev4@4e303d30d34c499e1676a3481b4b45358618638d`。
- **beta 目标：** `origin/beta@1e7d79ef29fd7568f376fe571bde0bd4cd7c6838`，相对当前基线 31 个提交，共同祖先为 `4e303d30d34c499e1676a3481b4b45358618638d`。
- **保护路径：** `build-tv-debug.sh`；任务启动时唯一既有 dirty 路径，不纳入本任务。
- **回滚：** 未提交合并结果前使用 `git merge --abort`；提交后使用本任务恢复 annotated tag 或 `git revert -m 1 <merge-commit>`。
- **唯一下一步：** 使用 `task_guard.sh finish` 提交当前无冲突合并树并创建恢复标签。

## Authority and scope

- 用户明确授权：拉取远端 `beta` 最新代码、合入当前 `dev4`，审查全部当前改动（含 `08b8bbc...`），发现问题即修复并验证、再次复评，随后提交、推送、创建中文 PR，并再次拉取远端最新代码。
- 任务守卫：`C14-beta-sync-review-dev4-20260911`，模式 `standard`。
- 声明路径：`app/**`、本文档、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`。
- 排除范围：不升级或修改 FFmpeg、Media3、MPV/IJK native、JNI、AAR、lock、patch、APK、`.so` 和 `build-tv-debug.sh`；仅评审 beta 在应用层的最终树影响及本地 EXO 上游恢复提交与其 App 接线。
- 合并前已确认：没有未完成的 task guard；没有提交冲突（`git merge-tree --write-tree HEAD origin/beta` 成功）；任何实际合并仍须保留本地 task guard 修复和受保护 dirty 文件。

## Source commit ledger

以下为 `HEAD..origin/beta` 的完整 31 个提交；合并后逐项以最终树、父提交关系和相关测试复审，不因 merge commit 或已有历史评审而省略记录。

| # | 完整 commit | 父提交 | 内容 | 初始 disposition |
| ---: | --- | --- | --- | --- |
| 1 | `619ed77dabd66bb0e7cdb492be483451aedccd25` | `ca4d79dc389294d469301492fa2e92be77919c84` | 设计 TweakCN 风格主题颜色系统 | 已在 beta，合并后复核最终树 |
| 2 | `341da7f9d099056c375c7477364a9b06dcbedf1b` | `619ed77dabd66bb0e7cdb492be483451aedccd25` | 移动端主题配置编辑器 A+B | 已在 beta，合并后复核最终树 |
| 3 | `9c77828cfd3f248d029e6f36b6c5030122b36fc2` | `341da7f9d099056c375c7477364a9b06dcbedf1b` | 原生语义主题 token 绑定 | 已在 beta，合并后复核最终树 |
| 4 | `0316262eeb70cd1831fa686012c2304dc6da4b98` | `9c77828cfd3f248d029e6f36b6c5030122b36fc2` | 主题导入导出与 TweakCN 适配 | 已在 beta，合并后复核最终树 |
| 5 | `1c0bd8e1d75e0c8d3a2f420a39fcb5b23e96f48b` | `e09e785d87b95f9da249825a79f7ad0a213f61b6` | TV 详情页照片焦点导航修复 | 已在 beta，合并后复核最终树 |
| 6 | `cb7229964f77cd43c68157111dae639d3de88531` | `0316262eeb70cd1831fa686012c2304dc6da4b98` | 完成移动端主题导入导出 | 已在 beta，合并后复核最终树 |
| 7 | `32ace88636f90f507288df859c325afd493b3e89` | `cb7229964f77cd43c68157111dae639d3de88531` | TV 主题焦点绑定与本地 catalog/cache | 已在 beta，合并后复核最终树 |
| 8 | `400c059369ead1041e1afbd9fc589bddd51490c3` | `1c0bd8e1d75e0c8d3a2f420a39fcb5b23e96f48b` | 炫彩详情 dismiss 后焦点恢复 | 已在 beta，合并后复核最终树 |
| 9 | `e4b6e9e169ac155bb55540ed6de551c71d9287a0` | `32ace88636f90f507288df859c325afd493b3e89`、`253ffaa4f7908d0b90fa092f020cb20611aaadb5` | 同步 beta 并修复主题导入安全边界 | merge，复核冲突解决与安全边界 |
| 10 | `ae64c42369a6b2ee660fee86cbe47c40382df667` | `e4b6e9e169ac155bb55540ed6de551c71d9287a0` | 记录 beta 同步关闭证据 | 文档，复核不污染运行时 |
| 11 | `74e572173fcc9ab7e274a94b6245b8de90b217c1` | `253ffaa4f7908d0b90fa092f020cb20611aaadb5`、`ae64c42369a6b2ee660fee86cbe47c40382df667` | 合并 PR #238 | merge，复核最终树 |
| 12 | `f89c301d65c0c8d4bf6e44a3eab11e6c9c9266dd` | `400c059369ead1041e1afbd9fc589bddd51490c3` | 炫彩详情长按集焦点恢复 | 已在 beta，合并后复核最终树 |
| 13 | `4d42cc34c8a5ba74bed68455c5ab1c9761fd14ad` | `f89c301d65c0c8d4bf6e44a3eab11e6c9c9266dd`、`74e572173fcc9ab7e274a94b6245b8de90b217c1` | 同步 beta 并完成 dev3 TV 焦点复评 | merge，复核最终树 |
| 14 | `1658013bd5241d112d74f6bda663e28723638e23` | `4d42cc34c8a5ba74bed68455c5ab1c9761fd14ad` | 播放速度选择对话框替代循环切换 | 已在 beta，复核 TV 焦点/状态保存 |
| 15 | `6eb76c5bca0b00e69520256fffee900bc9ed8dac` | `fbb7197e489a233686208fc948c821586f88a91a` | TV 首页广告拦截快捷入口 | 已在 beta，复核入口和可见性 |
| 16 | `a488d11b62dd2d05a9e5109d5a5f065959c3dd05` | `6eb76c5bca0b00e69520256fffee900bc9ed8dac` | TV 首页站点注入开关快捷入口 | 已在 beta，复核入口和状态 |
| 17 | `bfef295dce3174ceb8cc41882979a70795bd03f5` | `a488d11b62dd2d05a9e5109d5a5f065959c3dd05` | 从首页快捷入口打开站点注入编辑器 | 已在 beta，复核导航目标 |
| 18 | `dc9003e3134e1c3a82b5c4f964567e7c0d479beb` | `1658013bd5241d112d74f6bda663e28723638e23` | TV 速度选择保持当前焦点项可见 | 已在 beta，复核对话框焦点 |
| 19 | `6ac335c020ce7a168d8e5a25365d3774e6a3d77d` | `dc9003e3134e1c3a82b5c4f964567e7c0d479beb`、`5246948300c176d198b284167b02d2f576c29bb2` | dev3 合并 beta，Backup 冲突取 beta APP_PREFS | merge，重点复核 Backup 数据兼容 |
| 20 | `d85060eb91497ebd5fa94764bb23ae6da9f360fa` | `bfef295dce3174ceb8cc41882979a70795bd03f5`、`5246948300c176d198b284167b02d2f576c29bb2` | dev2 合并 beta 并复评 | merge，复核最终树 |
| 21 | `7dd78510730895282d599becdd912fb64199e418` | `6ac335c020ce7a168d8e5a25365d3774e6a3d77d` | Backup 冲突收口并保留语音广告规则 | 已在 beta，复核数据与规则共存 |
| 22 | `76f2be03ca680687f7d902dd067d235988a0873b` | `5246948300c176d198b284167b02d2f576c29bb2`、`d85060eb91497ebd5fa94764bb23ae6da9f360fa` | 合并 PR #245 | merge，复核最终树 |
| 23 | `1b7c9f70f9db8cf4e33cdf2ae756d29024a26181` | `76f2be03ca680687f7d902dd067d235988a0873b`、`7dd78510730895282d599becdd912fb64199e418` | 合并 PR #247 | merge，复核最终树 |
| 24 | `79fd8c6fb5707798d372050d5f318911ad7b533c` | `7dd78510730895282d599becdd912fb64199e418` | TV 质量与线路焦点互相导航 | 已在 beta，复核焦点边界 |
| 25 | `5bd95ba0c96232015d0ab551f5b085a978431cb0` | `5246948300c176d198b284167b02d2f576c29bb2` | 音频诊断验证注释 | 文档/注释，复核无行为变化 |
| 26 | `30e90403591ab9006050dda3ebfc876f4d1d6c10` | `5bd95ba0c96232015d0ab551f5b085a978431cb0`、`1b7c9f70f9db8cf4e33cdf2ae756d29024a26181` | beta 分支合并 | merge，复核最终树 |
| 27 | `db9210136006a5e880d320a17ce12eb49d18c0ff` | `79fd8c6fb5707798d372050d5f318911ad7b533c`、`30e90403591ab9006050dda3ebfc876f4d1d6c10` | dev3 合并 beta | merge，复核最终树 |
| 28 | `35c6975776ca03eeabf3b77715294146547293e1` | `30e90403591ab9006050dda3ebfc876f4d1d6c10`、`4e303d30d34c499e1676a3481b4b45358618638d` | 合并 PR #246 | merge，复核与本地 EXO 恢复的关系 |
| 29 | `96d53148173a1fd7a8b1f0235cb60384e2398df1` | `79fd8c6fb5707798d372050d5f318911ad7b533c`、`30e90403591ab9006050dda3ebfc876f4d1d6c10` | dev3 再次合并 beta | merge，复核最终树 |
| 30 | `f769befae8c8724573c55b0ec7dae7ef8ea01ebd` | `96d53148173a1fd7a8b1f0235cb60384e2398df1` | 首页 part 缺失 paddingEnd 修复 | 已在 beta，复核 TV 焦点布局 |
| 31 | `1e7d79ef29fd7568f376fe571bde0bd4cd7c6838` | `35c6975776ca03eeabf3b77715294146547293e1`、`f769befae8c8724573c55b0ec7dae7ef8ea01ebd` | 合并 PR #248 | merge，复核最终树 |

## Review and verification log

### 首轮

- `git merge --no-commit --no-ff origin/beta` 自动完成，无未合并路径；beta 的完整 31 个提交进入暂存合并树。
- 受保护的 `build-tv-debug.sh` 未被暂存；`task_guard.sh check` 与 `git diff --cached --check` 通过。
- 静态复评确认 `E-ROLLBACK-EXO` 的 EXO 核心/直接测试仍保持与 `upstream/main@784b90420d646eb6c7ddcc63ad622a92c65b02b4` 对齐；HLS 广告反馈接线仍使用共享 `PlaybackResourceClassifier`。
- 首轮发现并修复：beta 保留的 `PlayerManagerLifecycleSourceTest` 中 4 个已删除 EXO 停滞看门狗断言，以及 `TrackDialogTest` 中 1 个已删除 `TrackUtil.uniqueActiveFormat` 断言，均与批准的上游模式恢复相冲突；保留同文件仍适用的其他生命周期/字幕断言。
- 修复后第二轮复评：Mobile Arm64 Java 编译和聚焦测试通过；Leanback Arm64 Java 编译和聚焦测试通过。日志：`/tmp/c14-beta-review-focused-20260911.log`、`/tmp/c14-beta-review-focused-leanback-20260911.log`。
- 聚焦范围覆盖主题 profile/catalog/import/export/controller、备份偏好、速度对话框、站点主题、TV 站点注入/焦点、播放器 ownership、HLS 分类、`PlayerManagerLifecycleSourceTest`、`TrackDialogTest`、TMDB 布局以及本地 EXO 策略测试。
- 复评结论：未发现 beta 与本地 EXO 上游恢复之间的未解决编译、引用、冲突或行为契约问题；全量 Mobile 中此前的 10 项失败仍属于本任务前已存在的基线契约/二进制失败或本任务明确移除的行为，已由正确聚焦测试覆盖并通过，不扩大范围修复。

### Best-practice / design boundary

- 本轮是应用层已存在提交的同步与复评，不引入新的依赖、二进制、公共 API 或架构设计；不重复改写已经在 `C5`–`C13` 任务文档中完成的主题、焦点、速度和播放器行为设计。
- 仍以实际源代码、完整 commit ledger、现有回归测试和最终编译结果为决策证据；若合并揭示新的跨模块行为冲突，再在本文补充独立方案、验收和回滚记录后修复。

## Final acceptance criteria

1. `origin/beta` 的 31 个提交全部进入最终 `dev4` 历史，合并无未解决冲突。
2. `08b8bbc...` 的 EXO 上游恢复、HLS 兼容接线、task guard 修复和受保护 `build-tv-debug.sh` 均按授权处理，不被 beta 覆盖或误提交。
3. 首轮和修复后复审覆盖所有最终变更路径；不得遗留编译错误、冲突标记或可由本任务修复的定向测试失败。
4. 通过最小风险比例验证后，使用 task guard 原子提交并创建恢复标签。
5. 推送当前 `dev4` 和恢复标签，创建中文 PR 到 `beta`，最后 `git pull`/fetch 验证远端最新状态。

## Rollback

- 合并未提交时：`git merge --abort`。
- 合并提交后：使用本文记录的恢复标签，或 `git revert -m 1 <merge-commit>`；修复提交保持独立可回滚。
- `build-tv-debug.sh` 始终保留为本地保护 dirty 文件，不纳入提交。

## Follow-up：2026-09-11 12:17 后 origin/beta 最新增量

- 最终拉取发现 `origin/beta` 已从 `1e7d79ef29fd7568f376fe571bde0bd4cd7c6838` 前进至 `dbff441aa8a4bb54883ae07f722e53071413dd99`，新增完整提交链：
  - `68df13503c07b20e68cdac072fa381037f672461`：修复 TV 分类边界切换中的过期焦点回调；
  - `2cf5a93087d695e7cdd36a045d9b8af15a7430c7`：dev2 合并 beta 并完成复评；
  - `dbff441aa8a4bb54883ae07f722e53071413dd99`：合并 PR #249 到 beta。
- 只读 `git merge-tree --write-tree cf2a4c915a836b52a6cd0d0ae9f20a7d9535ecf7 origin/beta` 成功，实际 `git merge --no-commit --no-ff origin/beta` 无冲突。
- 新增 beta 最终树相对当前 `dev4` 仅增加 7 个 dev2 已评审路径：`HomeActivity.java`、`VodActivity.java`、`FolderFragment.java`、`TypeFragment.java`、`VodActivityCategoryEdgeTest.java`、`HomeCategoryNavigationSourceTest.java` 和 `docs/beta-sync-review-dev2-20260911.md`。
- 这些路径由 `docs/beta-sync-review-dev2-20260911.md` 覆盖，记录了源码生命周期/焦点复评、Leanback 分类测试 3 项、Mobile 定向测试 77 项及 Mobile/Leanback Arm64 Java 编译通过；本次新增 beta 仅为该已评审提交链及其 PR merge，按“已有评审覆盖且无合并冲突”条件跳过重复构建和复评。
- 当前后续单元状态：最新 beta 已合入暂存树，guard/diff-check 通过，待提交、推送和更新 PR。
