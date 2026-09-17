# dev3 合并 beta 最新代码与代码复评（2026-09-07 第二轮）

## Recovery anchor

- **目标：** 合并 `origin/beta` 最新代码，评审当前分支全部已提交未推送改动，修复发现的问题并定向验证、二轮复评通过后提交、推送 `dev3`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
- **任务守卫：** `beta-sync-review-20260907-dev3-round2`，模式 `standard`，范围 `.codex/**`、`app/**`、`docs/**`、`gradle/**`、`scripts/**`、`third_party/**`；开始时工作树干净，无受保护脏路径。
- **本地基线：** `dev3@075b9d84e474b6b843eccbf593bd57f3a5333521`，唯一本地已提交未推送提交为 `075b9d84e474b6b843eccbf593bd57f3a5333521`（TMDB 集卡片长按详情映射）。
- **beta 目标：** `origin/beta@9fc936fd88cb5b58d645c49b97209b0f246ed078`；共同祖先为 `fca898f9d940816c378ec37785184f46b334cd4d`。
- **合并状态：** `git merge --no-commit --no-ff origin/beta` 自动完成，零未合并路径；当前索引为待提交的合并结果。相对 beta 的最终本地代码差异仅为 TMDB 三个文件和本文档。
- **回滚：** 提交前使用 `git merge --abort`；提交后使用本任务创建的 `recovery/beta-sync-review-20260907-dev3-round2/*` 标签回退。

## 评审范围与处理原则

### 本地已提交未推送改动

- `075b9d84e474b6b843eccbf593bd57f3a5333521`：让 TMDB 剧集卡片长按详情请求使用当前可见卡片经过 `TmdbEpisodeMatcher` 校验后的 `TmdbEpisode`，避免手动选季/扁平集列表时把列表位置或当前季状态误当作请求集号。
- 变更文件：
  - `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbEpisodeAdapter.java`
  - `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivityLayoutTest.java`
- 二轮复核覆盖：适配器绑定和长按回调、普通详情与原生增强详情两条入口、TMDB 电影/剧集分流、跨季映射、无映射/API 失败回退、异步 generation/条目/季上下文校验、关闭弹窗后的列表重渲染与焦点恢复。未发现剩余阻断问题。

### beta 增量

`origin/beta` 相对共同祖先的 72 个提交以 Git 为权威完整台账（见下表）。按用户约定，已有评审覆盖且本轮无路径冲突的改动不重复实现审查，仅核对最终树继承、合并无冲突和既有验证记录：

- 音频/Exo/MPV/双 ABI/供应链资产及 C4 上游合并：由 `docs/C4-main-upstream-merge.md`、`docs/C13-beta-sync-review.md` 及相关 P/E/C 任务记录覆盖。
- LitePan 标题识别、AI 字段/缓存和测试：由 `docs/beta-sync-review-20260907-dev3.md` 覆盖。
- 设置/站点主题/ABI/TV 壁纸：由 `docs/C13-beta-sync-review.md`、`docs/dev1-beta-review-20260907.md`、`docs/beta-sync-review-20260907-dev4-round2.md` 覆盖。
- TV 分类焦点、详情首帧壳层及其测试：由 `docs/beta-sync-review-dev2-20260907-round2.md` 及 `HomeCategoryNavigationSourceTest`、`VideoActivityDetailShellSourceTest` 覆盖。
- 最终树不存在 `.backup`、`.orig`、`.rej` 残留；beta 新增提交未修改本地 TMDB 三个文件，因此不发生内容冲突。

## beta 增量完整 commit 台账

| # | 完整 commit | disposition |
|---:|---|---|
| 1 | `b208d26546cf6fd4498a1e54d45a37106a313d69` | MPV 多声道 fallback，随 C4/音频阶段进入 beta，已有记录覆盖 |
| 2 | `37995ff14016fd5a26fdae2b482f08470aa6a162` | 运行时音频诊断，已有 C4/音频阶段覆盖 |
| 3 | `cf0a5dabc77fb2bbdc3e0f2cc867a9eac86060b8` | Exo 硬件音频解码优先，已有 C4/音频阶段覆盖 |
| 4 | `d41155f16cd81f1354672a5479743462fc168ed9` | MPV 硬件音频 MediaCodec fallback，已有 C4/音频阶段覆盖 |
| 5 | `3fdf9f82f37843699a2545ed97d4a2dd17b8ead5` | Exo 压缩音频 PCM fallback，已有 C4/音频阶段覆盖 |
| 6 | `d8a994af5a64b93d7aeafb81f3755f81fcb8194c` | 音频解码标签对齐，已有 C4/音频阶段覆盖 |
| 7 | `734e99253ef5e856b3639810da5d9a05b0493646` | MPV 压缩 AudioTrack 输出，已有 P3/C4 记录覆盖 |
| 8 | `cb0a59f819a219dccd32bc1bf1c22b9caf754f07` | MPV 实际硬件音频状态诊断，已有 C4/音频阶段覆盖 |
| 9 | `37888d8b9d99da29f9ecfc3cd1f5eba458e09ee0` | Exo 网络保护按实际音频输出 gating，已有 C4/音频阶段覆盖 |
| 10 | `d00aa5737980d976cbae491948bf65dab906bf68` | MPV 压缩音频 patch 修复，已有 P3/C4 记录覆盖 |
| 11 | `347801b81f56bdcf515e4a9d7013814f8b777519` | Exo 音频兼容性调查记录，已有 C4 设计/验证记录 |
| 12 | `5c104a199fa07ad5f33c575deb6e0b91eea6668a` | MP4 AV3A Exo 路由，已有 C4/音频阶段覆盖 |
| 13 | `53eab9c2d6101220f26525a2af309a5578e7dc3a` | MPV AV3A canonical MIME，已有 C4/音频阶段覆盖 |
| 14 | `12ebbf8d15280e248dff9818c7969c7261173c16` | Exo 音频输出配置恢复，已有 C4/音频阶段覆盖 |
| 15 | `c7d43205f9125077a471ce57ee46a9c1f206e982` | Exo PCM 声道 fallback，已有 C4/音频阶段覆盖 |
| 16 | `8419003c2fe9eac200d2e6a9ef0dddf58638be31` | MPV 压缩音频 fallback，已有 P3/C4 记录覆盖 |
| 17 | `69191f78a37c5d56f87591c857d2d4be0112815d` | MPV AAC PCM fallback，已有 C4/音频阶段覆盖 |
| 18 | `2f2f99fb7d91c5e8c71e8f408b1ee45b287b2917` | MPV 未知 AV3A 声道降混，已有 C4/音频阶段覆盖 |
| 19 | `639a046125c4375685cb96c9ea004b620778bbb9` | Exo AVS3A DASH 声道解析，已有 C4/音频阶段覆盖 |
| 20 | `c826ee327b1cc33c0f0522fa120b1b2039e789d8` | Exo AV3A 5.1 混合内容降混，已有 C4/音频阶段覆盖 |
| 21 | `56b802ef585e83099953979b802172512c2fb447` | 音频/多声道策略评估记录，已有 C4 文档覆盖 |
| 22 | `04904ff99ae67ef25ebd4cca3740b30f0178662` | Exo ALAC decoder 路由，已有 C4/音频阶段覆盖 |
| 23 | `80e313830824f2e86d341079b58c330726de3e99` | QuickTime ALAC cookie，已有 C4/音频阶段覆盖 |
| 24 | `ebb5285238aa19eeab11ec4595985d496550ced2` | common 音频策略契约，已有 C4 文档覆盖 |
| 25 | `43fba18a8d074268c26a6ddbd30fe3483247320a` | C4 音频评估收口，已有 C4 文档覆盖 |
| 26 | `0a31951e3c923154b2ef8218d1a3811a96fa446b` | common 音频诊断契约，已有 C4/音频阶段覆盖 |
| 27 | `047ad74f76c859862b39b64d0545ec2dc83dd865` | C4 实施记录收口，已有 C4 文档覆盖 |
| 28 | `e5dd86b344b8ab1c5bda68f96ecf77a2e356ad4d` | Exo APE demux/FFmpeg，已有 C4/音频阶段覆盖 |
| 29 | `aed962426d7da7ddc268091f443b72c12187bd66` | MPV P8.1 HDR10 fallback，已有 C4/P2 记录覆盖 |
| 30 | `bf22a22fd60fd2cb9e2fef93b13f379b8d8b4f9e` | 音频诊断 API 兼容，已有 C4/音频阶段覆盖 |
| 31 | `8dd15ff24a5406091819c86b2dfa5e8499a6cbef` | MPV 禁用自动软件视频 fallback，已有 P3/C4 记录覆盖 |
| 32 | `ea9587dcb7feb187cc91f5f958f35696fea2996a` | MPV 压缩音频按 passthrough route gating，已有 P3/C4 记录覆盖 |
| 33 | `5a5a6ef383fb601e5bbce9d932a3bcc7cfa6ccc7` | MPV 脚本按钮设计记录，已有 C4 文档覆盖 |
| 34 | `72200c16fed08bd0309ea6290fab238bd21e7fa6` | MPV 自定义脚本按钮实现，已有 C4 文档覆盖 |
| 35 | `5b9a641678cfeae4db305fc490f6d5e17190921f` | MPV 脚本控制接线，已有 C4 文档覆盖 |
| 36 | `c0968836fbf3f9045449782355eb311c6dd89911` | MPV 脚本按钮管理 UI，已有 C4 文档覆盖 |
| 37 | `8baec44153035bb71723b4b2e0012415eb3eb337` | MPV 脚本创建对话框收敛，已有 C4 文档覆盖 |
| 38 | `bafdf34b765fca776ecc94a92cabb26234213f2e` | MPV 脚本按钮布局，已有 C4 文档覆盖 |
| 39 | `7ad7e1406efc2cda01bd6b14bd94822b7c31dedd` | MPV 脚本按钮右对齐，已有 C4 文档覆盖 |
| 40 | `61b352f8086554364d0ce402eb20c27105e01c6e` | MPV 配置同步，已有 C4 文档覆盖 |
| 41 | `123db7e7553eb0066e3c815f437b8cf266fe1aa7` | 恢复时清理旧 MPV profile，已有 C4 文档覆盖 |
| 42 | `3b346c85d0a3ed519d1bdac7b2e431a238a313ed` | MPV 配置同步收口，已有 C4 文档覆盖 |
| 43 | `41f02fd3c1f9e40fc64dd810475f7a45109bd4cb` | 脚本文本编辑器和列表刷新，已有 C4 文档覆盖 |
| 44 | `37a3685621b9b3882625fe721e3bf5a4e54ac372` | MPV 脚本设置流程统一，已有 C4 文档覆盖 |
| 45 | `b70f96ec0f732cc88edcd5edc26e39ecffe2cba3` | 脚本按钮点击反馈，已有 C4 文档覆盖 |
| 46 | `219ea082bd17719db49389b57f83fe2199bb79bf` | 脚本按钮状态反馈，已有 C4 文档覆盖 |
| 47 | `77df324e28c5befefe2f050e04c01ea62d556e3a` | 脚本标题后缀修复，已有 C4 文档覆盖 |
| 48 | `e1a873b37aa2243e014a9ab1be78c3f9696161be` | TV CSP 搜索焦点重置，已有 dev2/TV 复评覆盖 |
| 49 | `54b0b6a875d9d26fcf26c96ae04d4a2302e49625` | TV 搜索结果焦点入口，已有 dev2/TV 复评覆盖 |
| 50 | `686522fa8f4a9405f906848c2b938d9da300d6cd` | MPV 脚本自动执行/按钮上限修复，已有 C4 文档覆盖 |
| 51 | `784b90420d646eb6c7ddcc63ad622a92c65b02b4` | 上游 main 文档清理，最终树已有 C4 评审边界 |
| 52 | `188553addf6692220a2a715790fb8706b2f423b0` | C4 上游合并承载，已有 C4 文档和双 ABI/编译验证 |
| 53 | `adeabec2588a81d204dfd608269e3db8c7cdc80e` | C4 收口记录，已有 C4 文档覆盖 |
| 54 | `57932a7e7055489403d3e170bba706048df0e065` | Mobile 个性设置膨胀修复，已有 dev1 复评覆盖 |
| 55 | `a6eb0e96019da096b6e50aa8944270f911a2cdfa` | TV 壁纸设置入口，已有 dev4 复评覆盖 |
| 56 | `f8b41fd07e578ae8ec12cee827aba449c7c49e48` | dev2 beta 评审承载，已有 dev2 记录覆盖 |
| 57 | `7e6fd548190834408e40332e8392d2e379536511` | PR224 冲突修复承载，已有 dev2 记录覆盖 |
| 58 | `f0e9ab75bd0770b17db9f9bcd6101026d2c63466` | PR225 dev3 集成承载，已有 dev3 记录覆盖 |
| 59 | `161b190fac6c304c240bfb3b142b4dfa531fb1d5` | PR224 beta 集成承载，已有 dev2 记录覆盖 |
| 60 | `4425e9cb63186cfe41cacb1fd3982a23249a27e6` | TV 外层壁纸设置恢复，已有 dev4 记录覆盖 |
| 61 | `db59f3eeb85ff0e4ed993e814d9b81b08036bcb7` | dev1 beta 评审承载，已有 dev1 记录覆盖 |
| 62 | `03e3941196c84031bae764ac4192add4ed8619ec` | PR226 beta 集成承载，已有 dev1 记录覆盖 |
| 63 | `dbea2b995260ba73908a1e67078a938773699658` | dev4 beta 评审承载，已有 dev4 记录覆盖 |
| 64 | `58c6ef28436cad6b22c8cdf464629ca4897377c7` | PR227 beta 集成承载，已有 dev4 记录覆盖 |
| 65 | `7ab86f629306bf1b6a47de9dcba0df9d05a56d99` | TV 分类焦点回到首行，已有 dev2/TV 复评覆盖 |
| 66 | `d60297370a9ffefcba63aff9fe2d63e053f012ed` | TV 原生增强首帧/选集工具栏，已有 dev2/TV 复评覆盖 |
| 67 | `023058ba779791eef6b408a32f785bf430531427` | TV 顶部滚动保持分类焦点，已有 dev2/TV 复评覆盖 |
| 68 | `0f37b489f7f35719cf572da32a11701f5445a6be` | dev4 beta 合并承载，已有 dev4/TV 复评覆盖 |
| 69 | `17f6e3afb2b765b545205bb1390d4b29267aea08` | TV 分类切换内容滚动位置，已有 dev4/TV 复评覆盖 |
| 70 | `410c5303ab3005e2209c80734e3e4541543b0714` | dev2 第二轮 beta 合并承载，已有 dev2 记录覆盖 |
| 71 | `08be6b043c9656b944a10f56b1238fe1e616c2bb` | dev2 分类滚动 follow-up 承载，已有 dev2 记录覆盖 |
| 72 | `9fc936fd88cb5b58d645c49b97209b0f246ed078` | PR229 beta 最新集成承载，最终树已复核 |

## 评审发现、验证与二轮复评

- **首轮评审：** 合并预检无冲突；beta 本轮新增代码与本地 TMDB 三个文件无路径交集。已有记录覆盖的 beta 播放器/音频、标题识别、设置/主题、TV 焦点/详情壳层按约定跳过重复实现评审，仅核对最终树继承和无 `.backup/.orig/.rej` 残留。
- **本地差异评审：** 未发现阻断问题。适配器仅传递通过 `TmdbEpisodeMatcher` 校验的卡片对象；详情请求使用卡片绑定季/集号；跨季映射、电影/剧集分流、无映射/API 失败回退和异步 generation/条目/季校验均保持闭环。
- **验证：**
  - `bash .codex/scripts/task_guard.sh check`：通过。
  - `git diff --cached --check`：通过。
  - `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest --tests com.fongmi.android.tv.ui.helper.TmdbEpisodeWiringTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain`：`BUILD SUCCESSFUL`，98 actionable tasks，9 executed，15 from cache，74 up-to-date。
- **二轮复评：** 针对测试通过后的最终索引重新检查上述三处生产代码、两个长按入口、详情请求参数、回退分支和异步 guard；未发现剩余阻断问题，结论为 **通过**。
- **验证边界：** 未执行真实设备 TMDB 网络请求、电视逐帧焦点/视觉回归或 HDMI/音频 passthrough；这些不属于本次本地 TMDB 三文件窄改动的决定性验证，已有 beta 任务记录保留其边界。

## 下一动作

使用 `task_guard.sh finish` 原子提交当前 beta 合并、TMDB 改动、测试和本文档并创建恢复标签；随后推送 `dev3`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
