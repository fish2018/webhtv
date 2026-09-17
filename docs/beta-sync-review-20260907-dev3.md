# dev3 合并 beta 最新代码与代码复评（2026-09-07）

## Recovery anchor

- **目标：** 将 `origin/beta` 最新代码合入 `dev3`，评审当前分支全部任务改动（包括已提交但未推送的 LitePan 风格剧名识别清洗），发现问题即最小修复并重新验证，最终提交、推送 `dev3`、创建中文 PR 到 `beta`，再拉取远端最新代码。
- **任务守卫：** `beta-sync-review-20260907-dev3`，模式 `standard`，范围 `.codex/scripts/task_guard.sh`、`app/**`、`docs/**`、`gradlew`；开始时工作树干净，无受保护脏路径。
- **本地基线：** `dev3@d1ca2d2756c334a281587503e1d23b06a3369940`；待评审本地提交为 `1c2fa82822f6`（设计授权）和 `d1ca2d2756c3`（基础实施），两者均未推送到 `origin/dev3`。
- **beta 目标：** `origin/beta@1f37b723f9ccdc02bc4cc44b6490d1c2cc547e2c`；共同祖先为 `6a0b967dd533250d061e9457a93b4c15a724b5f5`。
- **合并状态：** `git merge --no-commit --no-ff origin/beta` 自动完成，零未合并路径；本地标题识别文件与 beta 增量没有内容冲突。
- **当前状态：** 代码与测试已修复、验证并完成最终复评；下一步仅由任务守卫原子提交和创建恢复标签，然后推送与创建 PR。
- **回滚：** 提交前可 `git merge --abort`；提交后使用本任务生成的 `recovery/beta-sync-review-20260907-dev3/*` 标签整体回退。

## 评审范围与已有覆盖

### 本地已提交未推送改动

- `1c2fa82822f6`：LitePan 风格剧名识别清洗设计与实施授权记录。
- `d1ca2d2756c3`：受控文件夹/同组标题上下文、规则证据链、AI 字段约束、上下文隔离缓存及回归测试。
- 评审覆盖 `MediaTitleRequest`、`MediaTitleParser`、`MediaTitleResolution`、`MediaTitleCache`、`MediaTitleResolver`、`AiTitleExtractionService` 及对应单测。

### beta 增量

beta 从共同祖先到目标的全部提交已在当前仓库历史中逐项复核；其中此前已有评审文档且与本地标题识别改动无冲突的播放器、站点主题、外观语言、ABI 和广告规则变更按用户约定跳过重复实现审查，仅确认最终树继承且无未合并路径。相关既有记录包括：

- `docs/C13-beta-sync-review.md`：dev4 合并 beta 后的播放器、站点主题、外观语言、广告规则和冲突修复复评。
- `docs/dev1-beta-review-20260907.md`：实验室 ABI 选择、下载/安装/版本显示复评。
- `docs/mobile-site-theme-20260906.md`、`docs/fix-exo-startup-double-resume.md`、`docs/C20260906161136-mpv-duration-recovery-fix.md`：对应主题、EXO、MPV 阶段的验证边界与证据。

beta 相对共同祖先的完整 commit 台账：

| 完整 commit | disposition |
| --- | --- |
| `1f37b723f9ccdc02bc4cc44b6490d1c2cc547e2c` | beta 最新集成提交，已合入并复核最终树 |
| `0b3e06e4de61e17ebbe2ce53e1ed621d988fdc20` | beta 集成提交，已有 dev1 复评记录 |
| `b158b71b97ca7abc76cbc2cf9e4e4784ede8af43` | ABI 选择复评集成提交，已有 `LabEnvTest` 与编译证据 |
| `8163e6fbbcca2187e3c816f96f378defa7cb0405` | dev4 beta 复评集成提交，已有 C13 记录 |
| `01ee74c595fd9e6b2fdb7e758274c2423781fa00` | ABI 规范化及版本显示修复，已有 dev1 记录 |
| `cc88e278a8ddc2088a82a68dbf1671e419606a29` | PR #218 最终集成 |
| `c344741692b3cb4e7dda473a8dbebb433ea50e65` | 手机版外观与语言入口，已有 C13 记录 |
| `016edd1d50fede43e119613737fe2288c99e45ba` | PR #218 冲突修复，已有双端编译证据 |
| `7a93ec7336b57dcf418c30b399862193e6991446` | PR #218 集成承载 |
| `c78b7cb11550209b2c0f884441e4a0fb65784e05` | PR #220 集成承载，已有 C13 记录 |
| `03f600acc5672c858814e4eab720987b3980e027` | Leanback 构建修复，已有编译证据 |
| `912208261e4e342ced009b1a0b71feed4855a01d` | dev2 beta 集成承载 |
| `cb386895da22a8836bb5587cafff3ef10f89a4fa` | 播放器重建后进度绑定，已有 C13 记录 |
| `7a380654329fa4800c55799440b5dd0798e22167` | Android 9 站点主题适配，已有设备探针证据 |
| `6b0490907da3e0b09a6563c1572cf283a3ae49d3` | MPV 时长恢复，已有阶段记录 |
| `910b9c00ef0f772dc284931a970449c60636440b` | beta 合并评审及脚本执行位修复 |
| `f627d99e88078ccd8760986cbae65cc4a3eb5adc` | 站点主题中间实现，最终窄实现已取代 |
| `33d9ef2393747dc689fb735a9c91de2ca4f6c0cb` | 弹窗主题中间实现，最终树已复核 |
| `66b5505282eb3de95538a1eab39ad9196d27ba22` | EXO 起播重复续播修复，已有阶段记录 |
| `1558c90fb1c27cbb0f2325ea845640288986a527` | 站点主题中间修复，最终窄实现已取代 |
| `03f14ae107325a117185f1cd3a85a3622019b332` | Leanback 外观与语言设置入口，已有 C13 记录 |
| `3a2d7723666eb2f331bec2a2b22cbcaa3c617aef` | 站点主题中间实现，最终树已复核 |
| `4242a06987b1080a923e14800380a866c95e8f67` | 站点主题中间修复，最终树已复核 |
| `74e60ef606ce6b64f5e3dab21230213e180cc504` | 移除 Mobile 重复主题订阅，已有 C13 记录 |
| `ea7f7ac89234ea4766dcb5d208f6ae973b4a40c4` | 包装器执行位修复，最终树已复核 |
| `99d0dad351f851bc0dd58ee0de37f3a3e996e6d3` | dev1 beta 合并承载 |
| `a2127a773ee9a88d40254349287b1e33be42dce8` | TV 外观大小设置修复，已有 C13 记录 |
| `0e9fbb0ad4fe2484e32860e0768f258a93c6e276` | TV 外观行为回归修复，已有 C13 记录 |
| `2677512b27458eb3e7a32e91fae421440b5151d2` | TV 外观与语言设置入口，已有 C13 记录 |
| `1a95bf977543bca2de9f62e8005506520ae8608b` | 主题色即时生效修复，已有 C13 记录 |

## 评审发现与修复

1. **AI 缓存键遗漏有效输入。** 原实现只纳入文件夹和同组标题，遗漏 `rawRemarks`、`vodYear`、学习样本等会影响规则结果或 AI prompt 的字段，可能错误复用不同上下文的 AI 结果。
   - 修复：缓存键升级为 `v3`，使用长度编码纳入请求字段、文件夹、同组标题、AI 配置和学习样本，避免分隔符碰撞及上下文串用。
   - 回归：验证备注、年份和学习样本变化都会产生不同键。
2. **同一上下文输入可能产生多个搜索候选并被重复计票。** 原实现将单个文件名的多个清洗候选都加入共识列表，放大单条输入的权重。
   - 修复：每个上下文输入只取首个清洗候选，保持一条输入一票；新增回归验证。
3. **相对路径未完全脱敏、目录尾斜杠边界不稳。** 原实现只截取绝对路径，可能把相对路径或尾斜杠路径的中间目录传入 AI。
   - 修复：统一按最后一个 `/` 截取上下文基名，并在文件夹名截取前移除尾斜杠；新增路径边界测试。
4. **beta 带入未引用的源码备份文件。** `ThemeDialog.java.backup` 与正式源码完全相同且没有调用方，属于构建/维护噪声。
   - 修复：不纳入最终合并提交。

## 最终复评结论

- 重新检查标题识别最终差异及所有调用/缓存边界：请求字段均经过受控清洗，AI prompt 与缓存键使用同一上下文集合；缓存键长度编码避免字段内容中的分隔符改变解析边界；每个上下文文件只贡献一票；正式树无 `.backup` 残留。
- beta 既有改动与本地标题识别改动没有冲突；已有复评文档覆盖的行为未被本次合并改写。
- 最终评审：**通过，未发现剩余阻断问题。**

## 验证

- `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.title.*' --tests 'com.fongmi.android.tv.service.AiTitleExtractionServiceTest' --console=plain`
  - `BUILD SUCCESSFUL`；32 项相关测试全部通过，`failures=0`、`errors=0`、`skipped=0`。
  - 同一任务包含合并后 Leanback Arm64 Java 编译。
- `git diff --check` 与 `git diff --cached --check`：通过。
- `bash .codex/scripts/task_guard.sh check`：通过，分支/HEAD、保护路径、范围和暂存路径安全。
- 合并冲突检查：无未合并路径；未发现 `.backup` 等残留文件。
- beta 中此前已有的 Mobile/Leanback 编译、ABI、Android 9 主题探针、播放器和广告规则验证沿用对应任务文档证据，因本轮无冲突且未改写其实现不重复执行。

## 下一动作

由 `task_guard.sh finish` 原子提交当前合并、标题识别修复、测试和本文档并创建恢复标签；随后推送 `dev3` 并创建中文 PR 到 `beta`，最后拉取远端最新代码。
