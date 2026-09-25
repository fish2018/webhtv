# C12：合并 origin/beta 到 dev3 并复审全部本地改动

## Recovery anchor

- **目标：** 将远端 `origin/beta` 最新代码合入 `dev3`，审查 beta 变更与本地已提交未推送的广告规则批量导入/启停功能；发现问题时修复并验证，复审直至无阻断问题，然后提交、推送 `dev3`、创建中文 PR 到 `beta`，并拉取远端最新代码。
- **任务守卫：** `C12-beta-sync`，模式 `upstream`，范围 `app/**`、`docs/**`、`scripts/**`、`third_party/**`、`.gitignore`。
- **任务开始时间：** 2026-09-06 01:05，`Asia/Shanghai`；任务开始前工作树干净，无受保护 dirty 路径。
- **本地基线：** `dev3@60fc55e18cf755d25dc9c140908188fb21898c44`，提交为本地未推送的 `feat(ad rule): implement batch import and enable/disable for imported rules`。
- **beta 目标：** `origin/beta@cebe42b190d5d7f1306e4ea3d0b6d833112ad464`。
- **合并基线：** `1a41a6f0ae03d418eb8cdf55a88f1b8bbcd0bf50`。
- **回滚锚点：** 合并前 HEAD `60fc55e18cf755d25dc9c140908188fb21898c44` 及恢复标签 `recovery/ad-rule-bulk-enable/20260906004930-60fc55e18cf7`；在未提交阶段使用 `git merge --abort`，提交后按本任务恢复标签回退/反向提交。
- **最便宜决定性验证：** 先运行受影响的 Mobile/Leanback Java 编译与新增/修改单测；随后按审查发现补充最小回归测试，不运行与本任务无关的全量矩阵。

## 当前合并状态

- `git fetch --prune origin beta dev3` 已完成；远端 beta 实际头为上列完整 commit。
- `git merge --no-ff --no-commit origin/beta` 已完成，自动合并、零未合并路径；合并结果暂存，尚未提交。
- 合并结果保留本地广告规则批量功能文件的 HEAD 版本，但 beta 同样修改了这些文件；因此本任务把这些路径视为冲突风险路径，必须逐文件复审，不能仅凭 Git 无冲突结论跳过。
- 合并后 staged 生产/测试/documentation 路径共 35 个；`git diff --cached --check` 已通过。仓库内已有示例/许可证文本中的分隔线不作为冲突标记。

## 研究与决策边界

### 决策问题

最终树是否同时保留 beta 的弹幕匹配记忆、触控优化、TMDB 焦点、MPV duration 恢复和偏好备份修复，并且不回退本地广告规则批量导入/启停契约；尤其要确认同文件合并选择没有丢失任一方的独立行为。

### 证据与适用性

| 证据 | 等级 | 用途/结论 |
| --- | --- | --- |
| 本地 Git 完整 commit、parent、最终树与 staged merge diff | A | 作为实现范围、血缘、合并结果和回滚依据；所有 beta-only commit 在下表逐一列出。 |
| beta 分支提交中的测试、设计文档与后续修复提交 | A/B | 用于判断意图、回归覆盖及是否被后续提交取代；不以 commit subject 单独作为正确性证据。 |
| WebHTV 当前调用方、布局、存储类和现有测试 | A | 用于核对线程/生命周期、持久化、TV 焦点、缓存 key 和播放器状态契约。 |
| Android/Media3 官方行为 | A | 仅在代码审查发现 API 生命周期或播放器状态语义疑问时按具体符号核对；本任务不改变依赖、ABI 或 native 二进制，故不展开无关供应链研究。 |
| 设备实机播放/触控验证 | 未取得 | 当前环境无授权设备证据；不把 Java 编译/单测扩大为完整设备验收，在结论中明确记录。 |

### 备选方案

1. **不合并 beta：** 不满足用户要求，排除。
2. **盲目接受 beta 的同文件版本：** 可能丢失本地广告规则批量功能，排除。
3. **保留最终合并树并按契约逐处复审，必要时做最小适配：** 推荐；保持 beta 能力、本地已完成能力、可测试性和可回滚性之间的最小变更。

## beta 完整提交台账（相对合并基线）

每个 commit 均已纳入最终树审查范围；合并提交也不省略。

| 完整 commit | 摘要 | disposition |
| --- | --- | --- |
| `ad702b041f18a1a40edefae77df8cafc86f02f2a` | feat: add leanback touch optimization mode | candidate/included，需审查 TV 焦点与生命周期 |
| `eab79fba9251dd6d07283fe149d7ce4f6874554d` | Merge pull request #211 | integration-only，最终树保留 |
| `317490478fc89917287d946ca7414cf5c4969794` | ui: move touch optimization below personal settings | included，需审查设置入口与备份 |
| `868e1a9020047141edeb7a72b3c6a0758afc578a` | Revert incorrect test assertion in TmdbDetailActivityLayoutTest | test-correction，需核对最终断言 |
| `97a7dd8898c0635fe13b696402c25c57acbfb3be` | feat: remember manual danmaku search intent | included/combined，需审查请求身份与缓存 |
| `83176f4d2a3a1f6bc6bbcaf3912fcf854189991b` | Merge remote-tracking branch origin/beta into dev1 | integration-only |
| `36b04d1a51a0ef2dab4fe304831944362754ed42` | feat: 实现弹幕手动匹配记忆功能 | included/combined，需审查 key 隔离与持久化 |
| `78b36585ef0ab9063eb3d87e53c9c63626c7a008` | feat: move touch optimization to personal settings | included/combined |
| `6831f897a8927ab5d1da7b05c998d329409247e6` | Merge remote-tracking branch origin/beta into dev2 | integration-only |
| `727e4d640e986d344b0d5dff1d24ac1bb4ec0c75` | Merge pull request #213 | integration-only |
| `db44647432ea1ac094298cebca9f535f73dc8202` | Merge pull request #214 | integration-only |
| `3d6cb55868501592348fbb3eb02742591fb0987d` | Center focused TMDB detail episode in list mode | included |
| `399023ced875b3bdb298afd7b09219c8e0eef57e` | Fix danmaku auto-line selection | included |
| `db022097bff5d015934288ed2258ff28a18c61d0` | Merge remote-tracking branch origin/beta into dev4 | integration-only |
| `a970acb89834de182cf2227883673fde79208f6a` | fix: repair beta merge and center TMDB episodes | included/repair |
| `bc5f9b42e090dd7fd303a56c052618dcb5506047` | Merge pull request #215 | integration-only |
| `97a5e3e8a59e82c88c1747ecbec7055f8287a9c4` | fix(mpv): preserve unknown duration and recover timeline on restart | included/partially superseded by readback follow-up |
| `5a479aa6962d3082dff35b4f330ef3232ad96961` | 合并 beta 并修复弹幕搜索记忆与偏好备份 | included/repair |
| `b077fd23adf0478a946cbdfa62c0253e8c031cb6` | beta: fix keyword binding..., retain overlay preference | superseded by `8ce1fbd...` final tree; inspect final result |
| `0ce6f13e6d597953ea02132eee50ba553191f14f` | fix(mpv): recover missing duration during playback state refresh | included/repair |
| `8ce1fbd783b6cf90e9332391f37d1bb68c1afe50` | beta: fix keyword binding..., retain overlay preference, resolve conflicts | included/final repair |
| `53e4f53788cf40832cdb63413ebb1eba13f6d4fb` | Merge pull request #216 | integration-only |
| `cebe42b190d5d7f1306e4ea3d0b6d833112ad464` | Merge pull request #217 | integration-only/final beta head |

## 审查轮次与验证记录

### Checkpoint 1：合并完成，待逐簇审查

- **完成：** 获取 beta、建立 guard、完成无冲突 merge；本地广告规则 commit `60fc55e18cf755d25dc9c140908188fb21898c44` 仍为第一父系本地改动。
- **工作区：** `dev3`，HEAD 仍为 `60fc55e18cf755d25dc9c140908188fb21898c44`，合并结果暂存，`MERGE_HEAD` 由 Git 管理。
- **未决风险：** 同文件广告规则功能是否丢失 beta 的独立修复；弹幕缓存/异步回调；触控优化对 TV 焦点和输入控件；MPV duration 状态；备份兼容。
- **下一动作：** 对上述功能簇进行只读 adversarial review，并运行最小受影响测试/编译。

## 结论（待完成）

- 当前尚未允许提交、推送或创建 PR。
- 只有在所有重要发现完成修复、验证结果可复现、最终复审无阻断问题后，才执行 task guard finish、推送 `dev3`、创建中文 beta PR，并再次 `git pull --ff-only`。
