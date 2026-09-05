# 播放器上游依赖增量合并评估（持续整理）

审计日期：2026-08-20（Asia/Shanghai）

文档状态：进行中。本文按检查点持续落盘；未标记“已完成逐提交审阅”的仓库，不应据此直接升级依赖。

当前恢复入口：以“稳定任务 ID 与唯一文档索引”及各任务文档顶部状态为准。完整逐提交审计已完成至检查点 43；`E1`、`E2-2`、`E-SP1`、`E2-1`、`E3-1a`、`E7-1`、`E7-2 + C3`、`E-SP3`、`E9-3`、`P1`、`P2-1`、`P2-5`、`P2-2`、`P3`、`P4-1` 和 `C0-M` 已完成验证，`E-SP2` 候选已接入但仍待实机性能/seek 验收。C0-M 已在 `9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223` 把 MPV FFmpeg 从 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 切换到 Exo 已验证的 `177f090e0503b7e013922ca903bde14b1c375f18`；`C2` 默认暂缓。

## 稳定任务 ID 与唯一文档索引

本节是后续评估、实施和新会话恢复的任务入口。旧的 `A*`/`B*` 仍保留为审计分组和历史定位信息，不再作为新任务文档编号。

规则：

- Exo 使用 `E*`/`E*-*`，Exo 性能专项使用 `E-SP*`，MPV 使用 `P*`/`P*-*`，通用功能使用 `C*`/`C*-*`。
- ID 一经分配不得重排、复用或改成通用数字序号。
- 一个已启动任务只有一个 `docs/<TASK-ID>-<slug>.md`；研究、方案、实施、反复修复、验证、commit/tag、回滚、状态和下一动作持续追加到同一文件。
- 不为同一任务再创建 `plans/`、assessment、implementation、fix 或日期续篇。本文仅保留跨任务索引和完整上游 commit 台账。
- 未启动任务不预建空文档；开始任务时按下表给定路径创建，并立即在本索引更新状态。

### 当前任务队列

| 顺序 | 任务 ID | 类别 | 功能/能力 | 状态 | 唯一文档 |
| ---: | --- | --- | --- | --- | --- |
| 1 | `E1` | Exo | nextlib 内置 FFmpeg 升级至 9.0.1 | 已完成 | [E1-exo-ffmpeg-9.0.1.md](E1-exo-ffmpeg-9.0.1.md) |
| 2 | `E2-2` | Exo | DV7→P8.1 codec/CSD 一致性 | 已实施 | [E2-2-exo-dv7-p81-csd.md](E2-2-exo-dv7-p81-csd.md) |
| 3 | `E-SP1` | Exo 性能 | 首帧已渲染时立即解除遮罩 | 已完成 | [E-SP1-exo-first-frame-visible.md](E-SP1-exo-first-frame-visible.md) |
| 4 | `E-SP2` | Exo 性能 | 远程大 MKV 延后 Cues、首次 seek 按需建索引 | **已完成**：`2156c74749a575b747d2f043419a5d47b485c0cf` / `recovery/E-SP2-CHAINED-SEEKHEAD/20260829004352-2156c74749a5`；双产品 arm64、连续 seek 和实机验证通过 | [E-SP2-exo-remote-mkv-deferred-cues.md](E-SP2-exo-remote-mkv-deferred-cues.md) |
| 5 | `E-SP3` | Exo 性能/生命周期 | seek 预载隔离、HLS 预缓存崩溃防护和 seek 恢复门槛 | **已合入 `fongmi-sync`**：App `17f1a4cfe2547b8f3ddc61fab34212e77ae719ff`，Media3 `c9d3bd912b90ec0ca440c28455f3e6d9bba019ea` | [E-SP3-exo-seek-preload-isolation.md](E-SP3-exo-seek-preload-isolation.md) |
| 6 | `E2-1` | Exo | HDR/Dolby Vision parser safety | **已完成** | [E2-1-exo-hdr-parser-safety.md](E2-1-exo-hdr-parser-safety.md) |
| 6 | `E3-1a` | Exo | Pixel E-AC3 JOC capability guard | 已实施：`cda1ac8cf2f5d4d9c3beec68b0b520d6f7c218ec` / `recovery/E3-1a/20260826175658-cda1ac8cf2f5` | [E3-1a-exo-pixel-eac3-joc-guard.md](E3-1a-exo-pixel-eac3-joc-guard.md) |
| 7 | `E3-1b` | Exo | DTS 14-bit 解析 | 已实施：`27b85eeeed5ceb55e56a67ae3b5cf8ff64b8da40` / `recovery/E3-1b/20260826201735-27b85eeeed5c` | [E3-1b-exo-dts-14bit.md](E3-1b-exo-dts-14bit.md) |
| 8 | `E4-1` | Exo | 字幕字节与边界安全 | **已完成：A4-1a/A4-1b** | [E4-1-exo-subtitle-byte-safety.md](E4-1-exo-subtitle-byte-safety.md) |
| 9 | `E4-J1` | Exo | Cue 数据契约 | **已实施并验证**：`af78e3b7656d6a0f210d7344b3852f301690c417` / `recovery/E4-J1/20260827133106-af78e3b7656d`；默认不启用碰撞或新渲染行为 | [E4-J1-exo-cue-data-contract.md](E4-J1-exo-cue-data-contract.md) |
| 10 | `E6-1` | Exo | 有界缓存写入 correctness | **已实施并验证**：`0a8ed3b910679a08a7e41c735338c3804a2eb938` / `recovery/E6-1/20260827145043-0a8ed3b91067`；不引入并行预加载 | [E6-1-exo-smb-proxy-cache-correctness.md](E6-1-exo-smb-proxy-cache-correctness.md) |
| 11 | `E7-1` | Exo | ISO reader safety | **已实施并验证**：`491a7def30484b0936426bbc57b09f5b6435ae80` / `recovery/E7-1/20260827160011-491a7def3048`；仅移植 IsoDataReader 安全修复 | [E7-1-exo-iso-reader-safety.md](E7-1-exo-iso-reader-safety.md) |
| 12 | `E7-2` | Exo | ISO multi-extent reader/API | **已实施并编译验证**：`5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f` / `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`；按用户要求未跑测试/真实 split ISO | [E7-2-exo-iso-multi-extent-api.md](E7-2-exo-iso-multi-extent-api.md) |
| 13 | `P0` | MPV | native 基线、等价提交与运行验收 | **评估已完成：基线/ELF/资产校验通过；无代码，待 P1/P2 明确批准** | [P0-mpv-native-baseline.md](P0-mpv-native-baseline.md) |
| 14 | `P1` | MPV | 格式与 shader correctness | **已实施并验证**：`a5971e3814d3b0826a5702d607dd6d1675b2ce53` / `recovery/P1-MPV-FORMAT-SHADER-CORRECTNESS/20260828184107-a5971e3814d3`；用户多原盘回归通过 | [P1-mpv-format-shader-correctness.md](P1-mpv-format-shader-correctness.md) |
| 15 | `P2-1` | MPV | Vulkan generic UV | **已实施并验证**：`fe4184933fbb3a02bd1ff2ff794a277123c35bdc` / `recovery/P2-1-MPV-VULKAN-GENERIC-UV/20260829003632-fe4184933fbb`；双 ABI、ELF、APK 资产身份及 compute/fragment/legacy/stable/auto 真机路径通过 | [P2-1-mpv-vulkan-generic-uv.md](P2-1-mpv-vulkan-generic-uv.md) |
| 16 | `P2-5` | MPV | 非原生 DV5 自动选择 Vulkan/gpu-next | **已实施并验证**：V2453A 上自动切换至 `vulkan/gpu-next` + `hevc_mediacodec`，普通 HDR 新媒体项恢复 `opengl/gpu`；用户确认正常 | [P2-5-mpv-dv5-auto-vulkan.md](P2-5-mpv-dv5-auto-vulkan.md) |
| 17 | `P2-2` | MPV | DV7 metadata/codecpar/error 完整性 | **已实施并验证**：`ba47756d7e463abeb9377088b819a2520e150935` / `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/20260829065811-ba47756d7e46`；仅吸收 metadata-missing、`par_out`、错误传播和 `INT_MAX`，保留本地 packet/Surface/EL 安全契约 | [P2-2-mpv-dv7-metadata-codecpar.md](P2-2-mpv-dv7-metadata-codecpar.md) |
| 18 | `P3` | MPV | AudioTrack 能力与直通 | **已完成**：`d82336bde585b62af43771284075a0a94a3d999e` / `recovery/P3/20260829094014-d82336bde585`；双 ABI、ELF、APK、API 35 手机端多声道 PCM fallback、pause/resume/seek 通过，HDMI/eARC/USB 原码直通留作硬件补验 | [P3-mpv-audiotrack.md](P3-mpv-audiotrack.md) |
| 19 | `P4-1` | MPV | JNI shutdown/lifecycle | **已完成**：`907bfca982a4b1d4d9ee0eeddd05d02226b8f9bb` / `recovery/P4-1-MPV-JNI-SHUTDOWN/20260829103212-907bfca982a4` | `docs/P4-1-mpv-jni-shutdown.md` |
| 20 | `C0-M` | 通用/MPV 搭载 | MPV 使用 FFmpeg 9.0.1 同源 revision 独立重建 | **已完成并关闭**：`9b7cf9cfbbeac00b0e5a342d4c6071c2c2d7a223` / `recovery/C0-M-MPV-FFMPEG-9.0.1/20260829122948-9b7cf9cfbbea`；双 ABI、ELF/资产、arm64 APK、多格式播放、快进、画中画和退出通过；退出期无 Surface 重初始化为升级前已存在的独立生命周期 bug | [C0-M-mpv-ffmpeg-9.0.1.md](C0-M-mpv-ffmpeg-9.0.1.md) |
| 21 | `C2` | 通用 | FFmpeg DV7→P8.1 BSF | **实施中（显式 MPV P8.1，默认行为不变）** | [C2-dv7-p81-bsf.md](C2-dv7-p81-bsf.md) |
| 22 | `C3` | 通用 | ISO multi-extent App resolver | **已随 `E7-2` 联合实施并通过 App 编译**：`5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f` / `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`；真实 split metadata 未验收 | [C3-iso-multi-extent-resolver.md](C3-iso-multi-extent-resolver.md) |
| 23 | `C4` | 通用/上游应用合并 | 合并 `fish2018/webhtv:main` 的 APK 更新、局域网推送、MPV 字幕与阅读器修复 | **已实施并验证**：`d0809f804f812b818bcb22f36cae8634022db673` / `recovery/C4/20260901032617-d0809f804f81`；保留当前 `dev2` 文档、评估台账和本地播放器契约；OCI 发布失败即阻断并固定 setup-oras 完整 commit | [C4-main-upstream-merge.md](C4-main-upstream-merge.md) |
| 24 | `E9-3` | Exo | DV5 MediaCodec + Vulkan/libplacebo GPU 映射 | **已实现并通过目标设备验收**：DV5 色彩映射稳定，DV5 -> DV7/HDR10 Surface 生命周期切换正常；最终提交 `6a3ddd266a94a6b984099876631cc6260e77b776` | [E9-3-exo-dv5-vulkan-renderer.md](E9-3-exo-dv5-vulkan-renderer.md) |
| 25 | `P4-3` | MPV | 终止退出时抑制无 Surface 的 MediaCodec 重初始化 | **已实施并通过定向测试/真机验收**：`8250e2204f4054601202a3a3f2fe04f8766744ee` / `recovery/P4-3-MPV-SURFACE-TEARDOWN/20260829132806-8250e2204f40`；终止退出后不再创建一次性 decoder，PiP 返回和快速重开正常，不改 native/FFmpeg | [P4-3-mpv-surface-teardown.md](P4-3-mpv-surface-teardown.md) |
| 26 | `P4-4` | MPV | 自动播放意图与延迟 pause 回调隔离 | **已完成**：`e8a1582d74844df0292cb27c6c8259a3d5eb5dfa` / `recovery/P4-4-MPV-AUTOPLAY-PAUSE-RACE/20260829135715-e8a1582d7484`；V2453A/API 35 冷启动和两次快速媒体替换均保持自动播放，两个暖切换样本约 3 秒推进约 2.8 秒；不改 native/FFmpeg/渲染链 | [P4-4-mpv-autoplay-pause-race.md](P4-4-mpv-autoplay-pause-race.md) |
| 27 | `C5` | 通用/上游应用同步 | 合并 `origin/beta` 最新历史投影、播放 ownership、沉浸融合标题和 armv7 C2 资产修复 | **已完成并推送**：`fc5b6ba029348c2c06214a80e4c080d6b210269a` / `recovery/C5-beta-sync/20260901135541-fc5b6ba02934`；83 项定向测试、双端 Arm64 Java 编译及双 ABI MPV 资产门禁通过 | [C5-beta-sync.md](C5-beta-sync.md) |
| 28 | `C6` | 通用/上游应用同步 | 合并 `origin/beta` 最新原生增强详情页 loading/backdrop 修复，并复审未推送的实时字幕原声识别语言快捷切换 | **已完成并推送**：提交 `a33ff92b8e65e11330ab17270b5f86a4c0b08183` / 恢复 tag `recovery/C6-beta-sync/20260902090623-a33ff92b8e65`；beta `c975ae1ed482a4bf47f106f5931bd2392e8ecce3`；四个目标测试类共 171 项通过，Mobile/Leanback Arm64 Java 编译通过；评审发现的 2 项 Important/1 项 Medium 已修复；设备播放回归待补验 | [C6-beta-sync.md](C6-beta-sync.md) |
| 29 | `C7` | 通用/上游应用同步 | 合并 `origin/beta` 在 C6 之后的 TMDB 手动匹配持久化、标题锚点隔离和读改写竞态修复 | **已完成（本地未推送）**：目标 `7db1b9d188e27877154757528d441150142b90ed`；本地合并提交 `a8f2015363819c70b4e7ae67d419035e579b857f`；恢复 tag `recovery/C7-beta-sync/20260902101049-a8f201536381`；定向 TMDB 测试和 Mobile/Leanback Arm64 Java 编译通过 | [C7-beta-sync.md](C7-beta-sync.md) |
| 30 | `C8` | 通用/上游应用同步 | 合并 `origin/beta` 在 C7 之后的片头片尾跳过、播放器生命周期、阅读器路由和更新包签名校验变更，并复审当前未推送改动 | **已完成并推送**：修复提交 `1d08c6fba24763023bf51792d344a3912b6d3cdb` / 恢复 tag `recovery/C8-beta-sync/20260903160741-1d08c6fba247`；beta 目标 `308694aaadd59d9d1ef230bded83cf84dafa114c`，合并提交 `5cf2f2e7fddd48454d10b86c27cc9f02e979098a` 已保留；73 项定向测试、Mobile/Leanback Arm64 Java 编译和 `git diff --check` 通过；`dev2` 与恢复 tag 已推送，`git pull --ff-only` 已更新到最新 | [C8-beta-sync.md](C8-beta-sync.md) |
| 29 | `E-SP7` | Exo 性能/播放行为 | H.264/AVC 受约束轨道恢复自适应选轨，避免 800Kbps 视频固定到过高分辨率导致掉帧 | **已实施，待真实设备 A/B 验收**：`ExoUtil.applyVideoLimit()` 已恢复自适应选轨；定向单测和 Mobile arm64 Java 编译通过 | [E-SP7-exo-avc-adaptive-selection.md](E-SP7-exo-avc-adaptive-selection.md) |
| 30 | `C9` | 通用/上游应用同步 | 合并 `origin/beta` 在 C6 之后的播放器、片段跳过、实时字幕、更新校验和移动详情页修复，并复审 E-SP7 合并树 | **已完成并推送**：`80ded1386a108dc8d1b08610c5b616d4d0f1f77f` / `recovery/C9-beta-sync/20260903072404-80ded1386a10`；E-SP7 定向测试、beta 受影响 175 项测试和两产品 Java 编译通过 | [C9-beta-sync.md](C9-beta-sync.md) |
| 31 | `C10` | 通用/播放器供应链 | 播放器 AAR、Maven sidecar、lock、MPV native override 和构建输入以上游为准 | **清理已验证，待提交**：正式发布输入及全部 MPV native override 已与 `fish2018/webtv:main@ec478b0b697422a7785171c7b51a35b7a526564e` 对齐；v556 残留已删除，双 ABI MPV ELF 门禁通过，详情见 [C10-binary-upstream-alignment.md](C10-binary-upstream-alignment.md) | [C10-binary-upstream-alignment.md](C10-binary-upstream-alignment.md) |

`C1` 是跨播放器真实输入验收维度，不单独形成代码任务或文档；它写入对应的 E/P 任务文档。`E-SP3` 已在 `fongmi-sync` 完成 App/Media3 合并，保留既有 `E4-J1`/`E6-1`/`E7-1`/`E7-2 + C3` 能力；`E9-3` 与已完成的 `P1` 现已共同进入集成树，后续按既定顺序处理 P2 阶段。

## 目标与决策顺序

本轮审计覆盖以下五个二进制依赖相关仓库：

- `FongMi/FFmpeg`
- `FongMi/mpv-android`
- `FongMi/media`
- `FongMi/mpv`
- `FongMi/libplacebo`

结论按三类组织：

1. Exo 依赖：优先供第一轮合并决策。
2. MPV 依赖：待 Exo 合并完成后实施。
3. 通用功能：评估应独立合并，还是随 Exo/MPV 某个阶段一起合并。

多个仓库共同实现的同一功能不会按仓库拆散，而会归并为一个可实施阶段；每个阶段记录完整 commit ID、依赖关系、当前项目已有实现、收益、风险、冲突点、建议动作和验证项。

## 检查点 59：2026-09-03 C10 二进制依赖对齐完成

- `C10` 已完成：提交 `79597d2c688a804f2f6f4f3b27815f5c60595da8`，恢复标签 `recovery/C10-binary-upstream-align/20260903111337-79597d2c688a`。
- 正式 Media3/Nextlib AAR、sidecar、lock、Media3/Nextlib patch 和 MPV stable override 已按 `fish2018/webhtv:main@ec478b0b697422a7785171c7b51a35b7a526564e` 对齐；未改 Java 播放策略或已打包播放器 `.so`。
- 双 ABI MPV ELF 门禁及 Mobile/Leanback Arm64 Java 编译通过；目标设备播放 A/B 和 native 重建仍是后续独立证据，不改变本阶段关闭状态。

## 审计口径

- 当前项目基线以 `README.md`、`third_party/fongmi-repositories-lock.json`、`third_party/media-lock.json` 和 `third_party/mpv-native-lock.json` 为准。
- 对每个目标分支枚举“项目锁定 commit 之后、远端审计头之前”的所有提交，逐项查看父提交和实际 diff，不只依据标题筛选。
- 检查远端强推、rebase、等价 patch-id 和跨分支回移，避免重复记录已包含但 hash 改变的功能。
- `media` 除默认的 `release` 外，还检查新的 `release-1.11.0-fongmi` 分支；默认分支未前进不等于没有可评估的新 Exo 改动。
- FFmpeg 同时被 Exo 的 nextlib 和 MPV native 使用。涉及 codec/demuxer/API/ABI 的提交必须判断两条播放器链是否要采用同一 FFmpeg revision，不能默认一次升级同时适合两边。
- 只给出合并建议，不在本轮擅自更新 lock、补丁、AAR 或 native 二进制。

## 检查点 1：基线与远端头

记录时间：2026-08-20。远端头为本次审计快照，后续若上游继续前进，将新增检查点而不覆盖本表。

| 仓库 | 当前项目基线 | 审计目标分支与头 | 初步状态 |
| --- | --- | --- | --- |
| FFmpeg | `release-9.0-fongmi@04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | `release-9.0-fongmi@177f090e0503b7e013922ca903bde14b1c375f18` | 有增量，待逐提交审阅 |
| mpv-android | `fongmi@99a60ad2141d5ace94453590903c2c6b9a0a2443` | `fongmi@7523b5c5199c84da4092787b7bf5d72452d61780` | 有增量，待逐提交审阅 |
| media | `release@2bc207851df311340767e913931ca7b28cab1794`；WebHTV fork 为 `e3e922d5c01bc0b564849940fe589daf37360d15` | `release-1.11.0-fongmi@3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`（该线继承 `release`） | 有 82 个 Exo 增量，已完成提交索引，逐 diff/阶段归组进行中 |
| mpv | `fongmi@cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | `fongmi@44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`（此前观察头 `17a39163...` 已被强推替换） | 有增量且存在重落基，逐提交审阅进行中 |
| libplacebo | `fongmi@b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | `fongmi@2301953d9faf0f5e112ff337f79cec64eab2f4f1` | 有增量，待逐提交审阅 |

## 当前项目必须纳入判断的本地约束

### Exo 链

- 当前使用本地发布的 `androidx.media3:*:1.11.0-alpha01-fongmi`，不能直接用上游产物覆盖。
- `third_party/media-lock.json` 已记录 9 个完整上游 cherry-pick 和 1 个局部 API 前置；新提交要判断是否已经被本地 patch 等价覆盖。
- `media3-upstream-playback-fixes-2026-08.patch`、WebSocket 弹幕、Matroska Dolby Vision RPU、nextlib 软解负载控制和 AV3A 都是需要保留的本地改动。
- Exo nextlib 当前也编入 FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`；若先升级 Exo 使用的 FFmpeg，必须避免与后续 MPV FFmpeg 升级的 ABI、功能和验收范围混在一起。

### MPV 链

- 当前 native lock：mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`。
- MPV、FFmpeg、libplacebo、mpv-android build framework 和 `libplayer.so` 属于同一套可复现输入；涉及 API 或构建脚本变化时必须成套重建两种 ABI。
- 必须保留 FFmpeg `SONAME`/`DT_NEEDED` 的 `libmv*`/`libmw*` 改名，避免与 nextlib 内置 `libav*`/`libsw*` 冲突。
- 必须逐个重放并核对 WebHTV 的 FFmpeg MediaCodec 饥饿回退、代理 Range，以及 MPV 光盘控制、DV7 HDR10 基底层、TrueHD、双 Surface OSD、timestamped release、时序诊断、Vulkan/AImageReader 和 Matroska seek 等补丁。
- 上游若已吸收某个本地补丁，只能在 patch-id/diff 和运行语义均确认后删除本地补丁，不能仅凭标题判断。

### 通用链

- FFmpeg 是最主要的交叉点，但 Exo 和 MPV 使用不同构建工具链、NDK、库命名和 renderer 入口；通用功能可能需要“同 commit、分两次构建/验收”。
- Dolby Vision、HDR 元数据、AV3A、字幕/封装、MediaCodec 行为、网络 seek 都可能横跨 `media`、FFmpeg、mpv 和 libplacebo，需要按最终用户行为归组。
- 纯构建基础设施、安全修复或性能优化若同时影响两条链，将注明最适合搭载的播放器阶段，以及另一条链是否应延后或同步。

## 分阶段目录（待逐步填充）

### A. Exo 依赖

- A0：已确认 `release-1.11.0-fongmi` 线性继承 `release@2bc20785...`；WebHTV fork `e3e922d5...` 是更早的 1.11 migration，不能作为新线最终基线。
- A1：Exo Dolby Vision/HDR/MediaCodec 识别与 fallback（`b63139c6`、`f70e4b6f`、`24977464`、`0cefd3ce`，并与 FFmpeg DV 元数据组联合评估）。
- A2：Exo AV3A、FFmpeg renderer/软解和 TS/容器支持（`d7083781`，并关联 nextlib/FFmpeg `AV3A` 提交）。
- A3：Exo 音频能力与 DTS/TrueHD/多声道（`1066f642`、`98d7e951`、`eb4aa3e4`、`908b27d7`、`d500eb27`、`d9ffc31a`、`1cc8573c`、`ba3af524`）；已完成深审，拆为 A3-0 至 A3-3，待用户决定是否实施。
- A4：Exo 字幕/TTML/ASS/PGS/DVB 与 WebHome 字幕回归（`d82fb7b9`、`92b1570a`、`6794d75b`、`ccc11523`、`aaddc2b9`、`1b112bd1`、`e8573d8c`、`ba27f889`、`3c2cbe8a`）；检查点 16 已完成 A4-1/A4-2/A4-3/A4-4 的联合阶段和验收矩阵。
- A5：Exo DASH/HLS/TS/MP4/Matroska/FLV/RTSP 稳定性与 seek（`0957524d`、`7c725b22`、`eb51dfd7`、`d160d770`、`f0eb7b51`、`a2fe56e7`、`a40e3988` 等）；检查点 17-23 已完成主要容器、网络、DRM、H.264 和 MMT/TLV 关联组深审，按各子阶段选择性实施。
- A6：Exo 光盘/ISO/SMB/代理/预加载和新增 extractor；检查点 24-29 已确认 App 有 Exo/MPV 两条真实 ISO 入口，并拆出 A6-0 至 A6-15。SMB 主体、HDMV 和 file types 已覆盖；优先候选是缓存/ISO 生命周期安全与 DVD correctness，SACD/DSF/DFF decode、DV7 combine 和并行预载仍暂缓。
- A7：Exo metadata/track-name/danmaku UI、debug、mpvplayer Media3 模块、MPEG-H 二进制和发布辅助；检查点 30 已把 #67-69 拆为 A7-1/A7-2/A7-3，三项主体均已存在，禁止整体覆盖，后续只接受窄修复或独立架构评估。

### B. MPV 依赖

- B0：建立 FFmpeg、mpv、libplacebo、mpv-android 四仓库的增量提交图和跨仓库依赖。
- B1 以后：按相关功能归组，给出成套升级或选择性移植建议。

### C. 通用功能

- C0：识别同时影响 Exo/MPV 的 FFmpeg 与媒体语义改动。
- C1：跨播放器媒体语义按真实输入随 A/B 阶段验收。
- C2：DV7→P8.1 转换实验项，默认不启用。
- C3：ISO metadata 多 extent 一致性；随 Exo A6-8b/A6-8c 合入 App 通用层，并同时回归 MPV ISO 语言 metadata。

## 覆盖进度

| 仓库 | 提交集合已固定 | 逐提交 diff 已审阅 | 已归入阶段 | 覆盖复核 |
| --- | --- | --- | --- | --- |
| FFmpeg | 已完成（新线 49 个） | 已完成 | C0-C2 | 已完成树差异与 patch-id 复核 |
| mpv-android | 已完成（新线 24 个） | 已完成 | B0-B3 | 已完成；15 个精确等价、2 个语义重落基、1 个增强、6 个新维护项 |
| media | 已完成（82 个） | 82 个均已完成首轮 commit/diff 映射；A1/A3/A4、A5 主要组、A6 光盘/ISO、A2/A7 收尾组均已复核 | A0-A7、C1、C3 | 已完成首轮覆盖复核；不能据此整支升级 |
| mpv | 已固定旧审计新线 27 个 | 已完成：16 个精确等价 + B8/B9/B10/B11 共 11 个非等价残余 | B5-B11 | 已完成至 `44755d7e...`；后续只审远端新增 |
| libplacebo | 已完成（新线 7 个） | 已完成 | B4 | 已完成；5 个为锁定线已有功能重落基，实际新增 2 个 shader 修复 |

## 检查点 2：提交范围已固定（2026-08-20）

以下数量是“目标分支相对锁定 commit 的提交图差异”初始结果，不等同于最终独立功能数量。mpv、mpv-android 和 FFmpeg 分支存在重落基/强推迹象，后续会用 patch-id、树差异和本地补丁对照去重。

### mpv-android `99a60ad2..7523b5c5`（24 个）

```text
ad98fc97ff1d25e217389e7238a1abda8c13a6c4
b356ac12b1ae3873b767807db6c89b6f2a276542
318ee1817c7810a399cc6fc63db331bde3b11ced
0431208436667ffed11ee571b91bab6ac3d7d239
7cc841e3b5e726c09376fb2e33d5f8e33e42f059
db7df511faffc4319e32f04e11d3aac3e02dad73
a8ab240a0239261a47f1644256472ebdf7fab62f
db8dec699b44dd10f21ee242efcc755e4d40c114
5be109f80714ab69eef8ea567a4360e124dab92b
880622fba9b653a0315e88dda3f60632819a029e
c0786731f9d611fe18ee64b26c66ce7bd3ebd5eb
a3e439523f11d17fa25d2cd2726ea24cd20c9399
19e4f3ec9ab8bd593ec097b42abb0cb821702a20
49482b3c616de15c30babb12c3fec1b287dcabd6
97f994f788a1bd8da0e81335c5265486360f8c20
f523de74d6a2c5d4fc26155d69ed8e063e028c97
1daf7314b280c8d37f84518d7c47d2735556b8f0
f639f3f6136ffa69bce599eb50c1575438ff40f7
b3c4cf87f71c9a62b343ea6bbcccdcc1520edd8f
585376daf5210bb2c2fd37a93a7f00010ad2912b
79d8b4c26135daf56ed2418f6a46163599b44fff
f4c5d614d5f68d483b2e1889ffad11e513b877d2
082d3d4939b75bf78cfc0a3f5f016ed9e9745d5e
7523b5c5199c84da4092787b7bf5d72452d61780
```

提交标题顺序和实际父链已在审计副本中固定；其中前 7 个主要是示例 App/构建依赖维护，后续 native 构建、JNI 和 FFmpeg 更新会与 MPV 资产成套判断。

### media `2bc20785..3c2cbe8a`（82 个）

该分支线性继承当前 `release@2bc207851df311340767e913931ca7b28cab1794`，82 个均是基线之后的提交。与 WebHTV fork `e3e922d5...` 的共同祖先为 `5fb306449733dd71595700c1227ad6087578c559`；fork 侧有 80 个自身提交，新线有 1163 个共同祖先之后的提交，不能用整支替换方式合并。新线 82 个提交的完整 hash/标题如下：

| # | Commit | 摘要 | 初步阶段/建议 |
| ---: | --- | --- | --- |
| 1 | `1066f642a64434e7c3c0be687d3e94a4ca2815d7` | alternative MediaCodec MIME types | A1，条件合并 |
| 2 | `98d7e9518169f187ad2915f20fa46f76ba256fc6` | DTS-HD MA profiles | A3，条件合并 |
| 3 | `eb4aa3e445c1df1f6a58eb9e8896e2f4e1998486` | DTS refactor | A3，随前项 |
| 4 | `b63139c6432caa3f058e7f0496f0d754aa0eaa93` | HLS/TS Dolby Vision | A1，合并价值高 |
| 5 | `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` | Matroska/MP4 color info | A1，随前项 |
| 6 | `249774647b026e16b56467eb5d79479816f79f11` | TS DV descriptor | A1，随前项 |
| 7 | `0cefd3ceec27444cf8faf02486b472bab39109fe` | DV fallback policy | A1，重点评估 |
| 8 | `908b27d736ed1c60d237654debc042b61363d081` | standalone DTS extractor | A3，按需求 |
| 9 | `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` | DTS-CD in WAV | A3，低优先 |
| 10 | `d9ffc31a50fc2377a6b2c91eb3579c4b8e9eab78` | DTS:X markers | A3，条件合并 |
| 11 | `b11a22289694611da2450688d9b6407ba75625bc` | MPEG-1 parsing | A5，安全/稳定 |
| 12 | `08c664eb8a213a956ff2c8b3d0fcea49902a81fa` | H.265 config parsing | A1/A5，低风险 |
| 13 | `0957524dacb0caca8d24819619b9235487f27d4a` | MP4 bad edit lists | A5，合并价值高 |
| 14 | `7c725b22f0b102e1447dd03dec557cc845db5049` | audio-shorter seek hang | A5，合并价值高 |
| 15 | `0417078bfbac37b5012991d696ab8a4803cb2805` | MPEG-H JNI binaries | A7，暂缓 |
| 16 | `7709a03d55c6eaaf999c18f0d4ab9fc9141b7ead` | OkHttp integration | A5/通用，需看现有 datasource |
| 17 | `2d4ab61e69c74796f529bf8f9cab60c68b340d4d` | content type handling | A5，低风险 |
| 18 | `32c20a091ba6e5fd09e13e67df3149326232eda5` | SMB/proxy data sources | A6，按产品需求 |
| 19 | `dd00f94b58b7324ab29febb0b50f3a190d544a3b` | disk preloading | A6，须评估与本地预缓存冲突 |
| 20 | `eb51dfd700290c5b585026d2fa43a7241dd7b734` | TS sync detection | A5，合并价值中高 |
| 21 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` | subtitle pipeline | A4，合并价值高 |
| 22 | `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b` | proportional subtitle controls | A4，需 UI 回归 |
| 23 | `6794d75b7a39db42dcfcab18c915f0da165515b5` | ASS parsing/layering | A4，合并价值高 |
| 24 | `ccc11523d57c3fd430c009b228c674a3195c9fdc` | SubRip parsing | A4，低风险 |
| 25 | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | PGS parsing | A4，条件合并 |
| 26 | `d7083781e629ad1c4683a687261374065fb38925` | AV3A TS reader | A2，必须与 FFmpeg/nextlib 联动 |
| 27 | `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | PGS TS reader | A4，条件合并 |
| 28 | `e8573d8c2ced07096c368d7ec3a40bc2e790d203` | DVB alignment | A4，低风险 |
| 29 | `ba27f889922a281162864a1260e7cb4e73ca0ecf` | bitmap cue clearing | A4，需回归 |
| 30 | `db8f68c8d8990d84b68cca3bcbc0538e10744a14` | FLV track discovery | A5，按输入需求 |
| 31 | `9b535ed30b9fa7e8580264036de1a12115daba32` | HEVC FLV tags | A5，按输入需求 |
| 32 | `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c` | RealMedia extractor | A6，暂缓 |
| 33 | `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b` | ASF extractor | A6，暂缓 |
| 34 | `624167c2a0eaf9af94011e0a556aaf91a15fb25f` | Matroska EBML resync | A5，安全/稳定 |
| 35 | `7feb08018a6e159330293de4878ebc3c9df2ca86` | compressed MKV text | A4/A5，条件合并 |
| 36 | `e25ef9864fce33f0d149820bd7999b30aff1a44d` | Matroska FourCC | A5，低风险 |
| 37 | `938f9958a0756554f8d641315ce626b67efe2143` | chapter ordering | A5，低风险 |
| 38 | `ba3af5240658745bb6383086b8be43438285adc1` | AAC channel parsing | A3，条件合并 |
| 39 | `1cc8573cab9e2453e7917aff1b8945482c8b2190` | TrueHD/Atmos | A3，重点评估 |
| 40 | `65ee9ba81815e67c9d3d08a2be0028859cc20569` | case-insensitive file URI | A5，低风险 |
| 41 | `d160d770887785e3007ff2f1efa50160c2096152` | DASH manifest | A5，合并价值高 |
| 42 | `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e` | HLS edge cases | A5，合并价值高 |
| 43 | `13fbfd88d312de6c4f10fedd2b085cb2710b88ae` | HLS ad filtering | A5，产品需求 |
| 44 | `a1e190005981febfa27e7583e5902d3cc2ce4ef7` | HLS SAMPLE-AES identity | A5，条件合并 |
| 45 | `39fde6f3b29cc5f69164a05fc89d5575b843371b` | ClearKey PSSH helpers | A5/通用，当前无 DRM 证据 |
| 46 | `444971729731edc184f2fb9f1afee2cc03e44b0f` | manifest ClearKey injection | A5/通用，暂缓 |
| 47 | `061d90a1e59639594bad5ffceae0ce7fbeba005f` | local DRM callbacks | A5/通用，暂缓 |
| 48 | `a40e39880378c9129fbfb86601e7e69e0e48a946` | M2TS framing/seek | A5/A6，条件合并 |
| 49 | `a2fe56e7c9a40c894d465d47a424f4c07d1eb50a` | RTSP/MP2T | A5，按输入需求 |
| 50 | `db13d7672f9bca525878292a54ae5e69c021f4c9` | audio/text offsets | A4/A5，需 App API 设计 |
| 51 | `bfd703abe3be1800b63119e5f6fc85154ec94f9d` | PlayerView render switching | A7，当前 UI 不直接复用 |
| 52 | `87e982f7b38bf6a24a2b3c148bdd23f476bec29c` | custom resize modes | A7，当前 UI 不直接复用 |
| 53 | `3216effea715a906ce9dd02ed50b46afe7f14ad4` | effects resize | A7，暂缓 |
| 54 | `f17757b05432e83f7c88c9f2a51377baaf10a227` | chapter/edition APIs | A5，需 App 消费方 |
| 55 | `1e064c30588bde89bf26798d10f071c40fd8da29` | timebar chapters | A7，暂缓 |
| 56 | `c85d124102c5b25a1bcd270d78f78603e87a6214` | chapter-aware seek view | A7，暂缓 |
| 57 | `990abc2368fd74779f525ee345734470659f3d53` | ISO/UDF access | A6-8a 优先；A6-8b/c 与 C3 联合 |
| 58 | `5bca32949e0ad82cb0105962a7ae31234d6cd1a8` | DSF/DFF extractors | A6-15，解析候选但 decoder 闭环暂缓 |
| 59 | `b3a78a2f7a9353359a02efe61e94038238c04fa1` | HDMV readers | A6-9，已覆盖，只回归 |
| 60 | `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` | DTS variants in TS | A3/A5，条件合并 |
| 61 | `15d8d21f3354e6da48c5a47751a3edb943f9ffc6` | DVD private streams | A6-10a/b/c，窄 hunk 联合 DVD IFO |
| 62 | `4d713dded8f59cac265ec612dc263b1287bb08b4` | Blu-ray playlist/M2TS | 主体已覆盖；A6-11 DV7 combine 独立暂缓 |
| 63 | `bd3b52102a1dad1ef9d168165d0e8959fca5d03f` | DVD IFO parsing | A6-12a/b，高价值 correctness |
| 64 | `9a8c256cf14fdfce353dee039f6dd861185d7bfe` | SACD image parsing | A6-13a parser 候选；A6-13b decode 暂缓 |
| 65 | `6cf9aae1e4132d6a8978e53e78f57234951cfd65` | register disc file types | 已覆盖，不合并代码 |
| 66 | `93af478b4cd2126c3844aaf2f813e24c0262eaf7` | ISO source routing | A6-14a/c 建议；A6-14b 条件合并 |
| 67 | `7b787fe2a5616e684d9c0b77b8481724ada4afae` | embedded artwork | A7-1，fork 已覆盖；只补 artwork 优先级/大小回归 |
| 68 | `85add599da1230a62715a232ffa8e87d50638a3e` | generated track names | A7-2，主体已覆盖；只迁移 cues/TrueHD 窄修复并删除错误语言别名 |
| 69 | `845f6fddd3953c36b08c2a878301649f918a1911` | danmaku UI | A7-3，非等价架构重写；勿整体覆盖，按能力矩阵单独评估 |
| 70 | `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97` | decode mode constants | A2，需 API 对照 |
| 71 | `ca7dd917ad574d4241640eb9282f20c5decd5aea` | FFmpeg/native SDK assets | A2，不能直接复用 |
| 72 | `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` | FFmpeg software video decode | A2，需 nextlib 方案对照 |
| 73 | `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` | decode-aware renderer | A2，需 App 架构评估 |
| 74 | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` | mpvplayer Media3 module | A7，当前不引入第二播放器模块 |
| 75 | `7cca3b0bb5cbdccea639e602e713301d8116a99f` | mpvplayer DV/OSD integration | A7/B 类交叉，暂缓 |
| 76 | `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486` | Exo debug info | A7，当前已有诊断体系 |
| 77 | `0f6191bc1bdd7324eef5e512cada65d9b974a6ed` | unified debug overlay | A7，当前已有 UI |
| 78 | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` | capability queries | A7，需 API 对照 |
| 79 | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | H.264 AU detection | A5，低风险 |
| 80 | `12670ce4fb23ad32ed3875d0250486eabe957913` | release AAR helper | A7，构建辅助 |
| 81 | `ccf962e8912695dc60ce82aa4470df899c6306a3` | MMT/TLV streams | A5/C，需 FFmpeg 对齐 |
| 82 | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | TTML layout/spacing/regions | A4，合并价值高 |

### mpv `cca559b4..17a39163`（28 个）

```text
f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47
7b8915bc1d04c7e1b61184e00c7fbfaab1911e75
52bb166f309c8bb55ab34b2b0bc5c8ead05370e4
e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8
e034d612cf6893954e943916988eef9e4426604c
b6d3434493fd04c0ee40a5610d8c311b77b16a6d
e7191f2a65d64af266c5c80793e79d2f4b92b789
70510dee41c41da19d71b952f1b05a6463d8d0d6
4d0423281f850b788e52b64def4b26a1505f6140
00402d0696d734783eb5efe1c23f4e3bda4bc3f8
82790bf10e8f67c4c9afa18d790ca98303276a60
a088b8b9a1c5c3e2520145d69e5543a1a87a5cf7
32c4d5adad29107756ae2987d69d92844bfed243
78617f20a4b449addbe8ac40e7e0791b2aab1c5b
31a5e3bdf76c2f2918a8992f4a9614ab76070af7
c2bc880511fd20850c586f2dc25aff770723b6b4
7282d53d58fcb8841ff93debea2a75e0b2afcd15
72e86486a5dd3a00950a9e5dfa3e381d2e00d230
47bb36190de83db31224d7193bd8514fabefb314
793d89800a425cda856065307c9027997ebf1c9c
06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042
c130c29e3e7153d666d42fbf421e408d9f8f38f3
64c3079e29fde4a65ddf894d25e3d83c4888cb42
0e5b5076801063fc2774be3511dc796712119f97
3ad9f121c0e0de3b590e6fb1a126db71dbe34e75
ba472ba4f8ae612e206b42dc02765cf4db00908f
804c61d10e619afbcd4628283cd761746ab0267a
17a39163dc879cc71e43a4f29d121477e56114be
```

### libplacebo `b694a21b..2301953d`（7 个）

```text
c78c4b4a5336473ff169ed2017a4535deed63d50
22ee762e8e0890fc54068beb670310f0edce7263
f5bdd194e700a002de441a350bbed385ec7ca30b
1797af1ce61d13e998ff4397b017422dd1e0c53c
373cd8be1e5f6c4e7a2c565766d23016be2bce3c
2a1101a2a466944a9d70c64991bc1983cfbe1cd0
2301953d9faf0f5e112ff337f79cec64eab2f4f1
```

### FFmpeg `04482c8d..177f090e` 的当前分支状态

FFmpeg `release-9.0-fongmi` 当前头为 `177f090e0503b7e013922ca903bde14b1c375f18`。旧锁定线和新线的共同祖先为 `03d9533176e98bb9fbf569c1f34968e73e948dd9`：旧线独有 21 个提交，新线独有 49 个提交。新线包含 FFmpeg 9.0.1 的维护/安全修复，以及重写拆分后的 Dolby Vision、MMT/TLV、AV3A、HLS/live 等 FongMi 功能；完整 hash 清单及 21 对 49 的等价/新增映射在 FFmpeg 专节补录。


## 后续检查点规则

每完成一个仓库或一个跨仓库功能组，立即在本文追加：

1. 精确提交范围、提交数量和完整 commit 清单。
2. 每个提交的实际作用及对当前项目的可达性。
3. 与其他仓库提交、本地补丁和 App 调用层的关系。
4. 建议：合并、条件合并、暂缓或不合并。
5. 实施顺序、回滚边界、构建产物和验证矩阵。

## 检查点 4：2026-08-21 远端刷新

FFmpeg、mpv-android、media 和 libplacebo 的观察头与检查点 3 相同。`FongMi/mpv@fongmi` 发生强制重写，观察头从 `17a39163dc879cc71e43a4f29d121477e56114be` 变为 `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`。

新旧头最后 6 个主题一致，但 hash 全部变化：

| 旧 hash | 新 hash | 功能主题 | 初步判断 |
| --- | --- | --- | --- |
| `64c3079e29fde4a65ddf894d25e3d83c4888cb42` | `f5c9f148d00db652da1ee900f386d8e0e615ed84` | AImageReader Android Vulkan interop | 强推重落基，待 patch-id/树差异确认 |
| `0e5b5076801063fc2774be3511dc796712119f97` | `a810f8e4f3c5cfde42367eace6d9015f95b99cd6` | Vulkan backend selection | 同上 |
| `3ad9f121c0e0de3b590e6fb1a126db71dbe34e75` | `43b378853776dfd734d21d9649b2053eefcb39f5` | compositable swapchains | 同上 |
| `ba472ba4f8ae612e206b42dc02765cf4db00908f` | `c7fef70644b3d506340e113689a5923f324c861d` | DV5 GPU mapping | 同上，需与 FFmpeg/libplacebo 联合验证 |
| `804c61d10e619afbcd4628283cd761746ab0267a` | `1c2d989b6b246c36869fff9ec8297c9897e1d964` | ASS formatting across VO switch | 同上 |
| `17a39163dc879cc71e43a4f29d121477e56114be` | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | DV7 HDR10 fallback | 同上，需与 Exo DV fallback 对照 |

因此 MPV 提交计数暂不增加为 34 个“独立功能”；本次先按 28 个功能主题处理，最终采用 hash 以远端新头为准。`selected_commit` 仍不变，等待 B 阶段决策。

## 检查点 5：2026-08-21 逐提交审阅批次（mpv-android / libplacebo / mpv 交叉点）

本批次先落盘可恢复的证据，再继续 FFmpeg。审阅使用远端完整提交对象和本地构建目录中的锁定源码；没有修改任何 lock、AAR、native asset 或补丁文件。

### 5.1 `mpv-android` 增量：完整提交与可实施归组

相对 `99a60ad2141d5ace94453590903c2c6b9a0a2443` 到 `7523b5c5199c84da4092787b7bf5d72452d61780` 共 24 个提交（按父链顺序）：

```text
ad98fc97ff1d25e217389e7238a1abda8c13a6c4
b356ac12b1ae3873b767807db6c89b6f2a276542
318ee1817c7810a399cc6fc63db331bde3b11ced
0431208436667ffed11ee571b91bab6ac3d7d239
7cc841e3b5e726c09376fb2e33d5f8e33e42f059
db7df511faffc4319e32f04e11d3aac3e02dad73
a8ab240a0239261a47f1644256472ebdf7fab62f
db8dec699b44dd10f21ee242efcc755e4d40c114
5be109f80714ab69eef8ea567a4360e124dab92b
880622fba9b653a0315e88dda3f60632819a029e
c0786731f9d611fe18ee64b26c66ce7bd3ebd5eb
a3e439523f11d17fa25d2cd2726ea24cd20c9399
19e4f3ec9ab8bd593ec097b42abb0cb821702a20
49482b3c616de15c30babb12c3fec1b287dcabd6
97f994f788a1bd8da0e81335c5265486360f8c20
f523de74d6a2c5d4fc26155d69ed8e063e028c97
1daf7314b280c8d37f84518d7c47d2735556b8f0
f639f3f6136ffa69bce599eb50c1575438ff40f7
b3c4cf87f71c9a62b343ea6bbcccdcc1520edd8f
585376daf5210bb2c2fd37a93a7f00010ad2912b
79d8b4c26135daf56ed2418f6a46163599b44fff
f4c5d614d5f68d483b2e1889ffad11e513b877d2
082d3d4939b75bf78cfc0a3f5f016ed9e9745d5e
7523b5c5199c84da4092787b7bf5d72452d61780
```

两端并非祖先关系。共同祖先为 `f0a3cebb6b1674b2b36bc708be854248744f4415`，锁定线有 18 个独有提交，新线有 24 个独有提交。`git cherry`/稳定 patch-id 与端点树共同给出以下去重结果：

- 15 个新 hash 是锁定线提交的精确 patch-id 重落基；
- 2 个是最终树语义相同、但因注释或上下文而 patch-id 不同的重落基；
- 1 个是对锁定线异步请求队列的继续增强；
- 6 个才是新线特有维护项，其中仅 Harfbuzz `14.2.1`→`14.3.1` 会进入本项目 native 构建输入，其余属于示例 App/文档。

15 对精确等价映射如下，不能再把右侧新 hash 当作当前项目缺失能力：

| 锁定线 commit | 新线 commit | 主题 |
| --- | --- | --- |
| `63614b566aec95b228ce0bcc7f5709c180bba2f2` | `db8dec699b44dd10f21ee242efcc755e4d40c114` | FongMi native CI |
| `7ba95b10ab78f124f1d25b5571fb7c16636d7518` | `5be109f80714ab69eef8ea567a4360e124dab92b` | Android CMake tooling |
| `fad982bd126a69ae75650583c27435fb7ce921cd` | `880622fba9b653a0315e88dda3f60632819a029e` | NDK shaderc/Vulkan |
| `aeef47e60602816ada7f4fa9945c5ee3edbd5c30` | `c0786731f9d611fe18ee64b26c66ce7bd3ebd5eb` | libbluray |
| `34b2794e7d1a71abb46772ccb5eb261d3d2273fa` | `a3e439523f11d17fa25d2cd2726ea24cd20c9399` | iconv/uchardet |
| `7e201b18b210633c4fa5cedc7bd0fa5836a78e87` | `19e4f3ec9ab8bd593ec097b42abb0cb821702a20` | libarchive |
| `34f917a0ef2a0a128e2832923f900d158e57a238` | `49482b3c616de15c30babb12c3fec1b287dcabd6` | dvdnav |
| `b043e3e722fa0c74d40ad7b4951ff217bfcadaf4` | `97f994f788a1bd8da0e81335c5265486360f8c20` | rubberband |
| `774ce02333db640c5b140295f1a08876de43b401` | `f523de74d6a2c5d4fc26155d69ed8e063e028c97` | libarcdav3a |
| `5372e9f414b1c7a25c048ca26f79afebea3170cb` | `1daf7314b280c8d37f84518d7c47d2735556b8f0` | 音频滤镜/直通 |
| `e536f47e7606d0d31aecb72576892dff30a574ca` | `f639f3f6136ffa69bce599eb50c1575438ff40f7` | libaribcaption |
| `317c454387cc66e009ed5e4d8b9e0bc012a378ce` | `b3c4cf87f71c9a62b343ea6bbcccdcc1520edd8f` | JNI 生命周期/Java interop |
| `860de20006ec219a9a509d0f4ad120f8bfdc5358` | `585376daf5210bb2c2fd37a93a7f00010ad2912b` | byte-array property |
| `08d39a01b56a3f8235ad64492c2da06b554321f5` | `79d8b4c26135daf56ed2418f6a46163599b44fff` | END_FILE 详情 |
| `99a60ad2141d5ace94453590903c2c6b9a0a2443` | `7523b5c5199c84da4092787b7bf5d72452d61780` | FFmpeg 9.0 构建指向 |

两个非精确但最终行为已存在的映射是：`a79e7af6c22f2fe7c2ef24723a229f92d0d9e83a`→`db7df511faffc4319e32f04e11d3aac3e02dad73`（MbedTLS `/dev/urandom`，新线只留下说明性注释差异），以及 `cb677149bcca045f96ceee07342a6b55eb3f053b`→`082d3d4939b75bf78cfc0a3f5f016ed9e9745d5e`（Lua 下载 fallback，端点树无差异）。

按真正可实施差异归组：

| 阶段 | 完整 commit ID | 实际增量 | 建议 |
| --- | --- | --- | --- |
| B0-构建输入维护 | `7cc841e3b5e726c09376fb2e33d5f8e33e42f059` | Harfbuzz `14.2.1`→`14.3.1`；本项目当前 lock 仍为 `14.2.1` | 低优先级条件升级；先查 release notes/字幕 shaping 回归，不因切 builder 自动带入 |
| B1-JNI 关闭顺序 | `f4c5d614d5f68d483b2e1889ffad11e513b877d2`，其锁定线前身为 `5e320439b7afca579ae03e4f1f4060362ba21e4a` | 把 `quit` 也放进 command/Surface 串行队列，避免 destroy 与排队请求竞态；端点 JNI 差异集中在 `main.cpp`、`request.cpp`、`request.h` | 当前 `third_party/mpv-player-jni` 已有 WebHTV 自定义队列与 ANR/双 Surface 逻辑；只移植“SHUTDOWN request”最小语义并做生命周期测试，不覆盖整套 JNI |
| B3-非播放维护 | `ad98fc97ff1d25e217389e7238a1abda8c13a6c4`, `b356ac12b1ae3873b767807db6c89b6f2a276542`, `318ee1817c7810a399cc6fc63db331bde3b11ced`, `0431208436667ffed11ee571b91bab6ac3d7d239`, `a8ab240a0239261a47f1644256472ebdf7fab62f` | 示例 App 版本、日志、后台 service、翻译、许可证页 | 不合并到 WebHTV |

端点树实际只差 11 个文件、84 行新增/24 行删除；大型 native 构建能力和前三个 JNI API 已经处于锁定树中。后续若更新 builder，应以“Harfbuzz + shutdown 串行化”为真实候选，而不是重复合并 24 个提交。

### 5.2 `libplacebo` 增量：API 版本和 MPV 交叉影响

相对 `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 到 `2301953d9faf0f5e112ff337f79cec64eab2f4f1` 共 7 个提交，完整 hash/主题如下：

锁定点和新头同样是从 `4d82c6898551068d4ae6a6b5538efcddc2c7cf64` 分叉的两条重落基线：锁定线独有 5 个提交，新线独有 7 个提交。稳定 patch-id 证明下面 5 对完全等价：

| 锁定线 commit | 新线 commit | 主题 |
| --- | --- | --- | --- |
| `5d5756bd88720580faf935aab6df703d4217c8df` | `f5bdd194e700a002de441a350bbed385ec7ca30b` | Vulkan YCbCr image wrapping / API 372 |
| `971c0f96ac12c77c57802d012e2cfbe53f7e6eae` | `1797af1ce61d13e998ff4397b017422dd1e0c53c` | HDR frame metadata 校验 |
| `04b3a0918fb32b8f374193aaead8b509274aae97` | `373cd8be1e5f6c4e7a2c565766d23016be2bce3c` | checked Dolby Vision mapping / API 373 |
| `16d79b2db71893904c9d238b1c6fad9edde8ae2c` | `2a1101a2a466944a9d70c64991bc1983cfbe1cd0` | swapchain `disable_storage` / API 374 |
| `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | `2301953d9faf0f5e112ff337f79cec64eab2f4f1` | raw external YUV / API 375 |

因此当前锁定的 `b694a21...` 本身已经是 API 375，包含 YCbCr wrapping、HDR/DV 边界检查、`disable_storage` 和 raw external YUV。新头相对锁定树只剩 3 个 shader 文件、27 行新增/9 行删除，真正新增仅两项：

| 完整 commit | 真正增量 | 建议 |
| --- | --- | --- |
| `c78c4b4a5336473ff169ed2017a4535deed63d50` | 抽出 `sh_alloc_obj()`，允许先按 GPU 分配 shader object，再由 shader 引用；属于内部重构/后续外部图像实现前置 | 不值得单独重建 native；若 mpv 残余 AImageReader 改动需要新实现，则随 B8 成套带入 |
| `22ee762e8e0890fc54068beb670310f0edce7263` | `pl_shader_extract_features()` 只改 RGB、不再把 alpha 强制写成 `1.0` | 低风险正确性修复；可随首次 MPV native 重建纳入，也可小范围回移并跑透明字幕/OSD/HDR shader 回归 |

**修正后的 B4 结论：** libplacebo 不存在“从旧 API 升到 375”的缺口；B4 的主要风险在 mpv 与本地 AImageReader/Vulkan 补丁，而不是这 7 个 libplacebo hash。若选择新头仍需重编 `libmpv.so`，但功能决策只需关注 `c78c4b4...`、`22ee762e...` 两项实际树差异。

### 5.3 `mpv` 增量的已确认交叉组

远端新头 `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 相对锁定点存在重写。共同祖先 `513d3407d4e1e95ebb743c8e9c139b39d9880cc2` 后，锁定线有 19 个提交，新线有 27 个提交；稳定 patch-id 证明新线中的 16 个只是锁定线功能重落基，另有 3 个同主题重写和 8 个真正新主题。端点树差异为 31 个文件、567 行新增/329 行删除。以下阶段表已按“实际残余差异”修正：

| 阶段 | 新完整 commit ID | 关联仓库/本地实现 | 初步建议 |
| --- | --- | --- | --- |
| B5-输入与网络稳健性 | 精确等价新 hash：`70510dee41c41da19d71b952f1b05a6463d8d0d6`, `00402d0696d734783eb5efe1c23f4e3bda4bc3f8`, `82790bf10e8f67c4c9afa18d790ca98303276a60`, `a088b8b9a1c5c3e2520145d69e5543a1a87a5cf7`；真正新增：`e7191f2a65d64af266c5c80793e79d2f4b92b789`, `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` | helper scheme、rewind、curl Range/worker 已在锁定 `cca559...` 树；新增只剩按 `variant_bitrate` 选择初始 edition，以及 Wayland null-output 防护 | 不重复合并网络四项。`e719...` 随 B10 测多 edition MKV；`f4d...` 对 Android 无直接价值，不单独移植 |
| B6-光盘/ISO | `4d0423281f850b788e52b64def4b26a1505f6140`（锁定线等价 hash `6b6d0d4a3d5e3ea883beffc8946d601744a080e6`） | Blu-ray/DVD ISO 支持已经在锁定 mpv 树中；WebHTV 另叠加 `mpv-stream-cb-disc-controls.patch` | 不存在上游缺口；升级时只验证本地补丁仍能干净重放，不能以该新 hash 为由删除光盘控制 |
| B7-通用媒体语义 | `32c4d5adad29107756ae2987d69d92844bfed243`, `c2bc880511fd20850c586f2dc25aff770723b6b4`, `31a5e3bdf76c2f2918a8992f4a9614ab76070af7`, `78617f20a4b449addbe8ac40e7e0791b2aab1c5b` | 四项均与锁定线 MMT/TLV、TTML、live、album-art 提交精确等价 | 当前已具备；C1 只做运行验收，不产生新的 cherry-pick 阶段 |
| B8-Android HDR/Surface/解码 | 精确等价：`793d89800a425cda856065307c9027997ebf1c9c`, `a810f8e4f3c5cfde42367eace6d9015f95b99cd6`, `43b378853776dfd734d21d9649b2053eefcb39f5`, `c7fef70644b3d506340e113689a5923f324c861d`, `1c2d989b6b246c36869fff9ec8297c9897e1d964`；重写：`06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`, `f5c9f148d00db652da1ee900f386d8e0e615ed84`；新增：`44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | HDR 协商、Vulkan backend、compositable swapchain、DV5 GPU mapping、ASS VO 已在锁定树。真正要审的是 Android output/AImageReader 两个重写，以及 DV7 HDR10 fallback；三者与 WebHTV stable-flow、Vulkan backend、DV7 本地补丁高度重叠 | 保持实验 B8；只针对三个残余 commit 做逐文件/行为对照，未确认前不删除任何本地补丁 |
| B9-音频/播放控制 | 精确等价：`72e86486a5dd3a00950a9e5dfa3e381d2e00d230`, `47bb36190de83db31224d7193bd8514fabefb314`；残余：`7282d53d58fcb8841ff93debea2a75e0b2afcd15` | 0.1x scaletempo、decoder reinit 已在锁定树；只剩 AudioTrack 高码率 passthrough，相对锁定线的 sample-rate 保留提交是后续改写 | 仅深审 `7282...` 与本地 TrueHD channel-mask；不能把三个 hash 都算作新增 |
| B10-低风险格式修复 | `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75`, `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4`, `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8`, `e7191f2a65d64af266c5c80793e79d2f4b92b789` | 10-bit RA 格式、Matroska 工具字段、zero-length/default element、edition 选择；四项均为锁定树没有的真实增量 | 当前最适合先做的小批次；仍需成套重建 native，跑 MKV edition/损坏 EBML/字幕/DV 样片 |
| B11-非 Android 播放维护 | `e034d612cf6893954e943916988eef9e4426604c`, `b6d3434493fd04c0ee40a5610d8c311b77b16a6d`, `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` | CI action、注释 typo、Wayland 防护 | 不作为 WebHTV 合并理由；随整树升级自然带入即可 |

### 5.4 当前工作树的重叠证据

- `third_party/mpv-player-jni/src/request.cpp` 已实现串行异步 command、video/OSD Surface 队列和失败请求回调；`event.cpp` 已转发 `MPV_EVENT_END_FILE` reason/error；`property.cpp` 已支持 byte-array；`app/src/main/java/is/xyz/mpv/MPVLib.java` 已声明对应返回码和事件。因此 `mpv-android` B1 四提交属于等价吸收，不应重复应用。
- `scripts/build_mpv_native.sh` 已要求 Vulkan AImageReader、raw/稳定转换、DV7 HDR10、TrueHD、可选 OSD、timestamped release、FFmpeg AV3A/MMT/TLV 和重命名 `libmv*` ELF 标记；这意味着上游新 MPV/libplacebo 功能必须在现有补丁顺序和 ELF 校验框架内评估。
- 本地锁定源码目录 `build/mpv-native/mpv-android/buildscripts/deps/{ffmpeg,mpv,libplacebo}` 的 HEAD 分别为 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、`b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`，工作树修改是构建脚本应用的本地补丁，不作为上游提交已合并的证明。

### 5.5 本批次的实施顺序

1. 先完成 Exo A1--A5 决策；不因 B4/B8 的 Android HDR 变化提前替换 MPV assets。
2. MPV 阶段先做 B5/B10 的输入与低风险格式验证，再做 B4/B8 的 FFmpeg + libplacebo + mpv 成套 native 构建；B1 JNI 只在 client header/API 真正变化时重建。
3. B6 光盘/ISO 始终以 WebHTV 本地 `stream_cb` 方案为基线，任何上游移植都必须保留时间轴/章节/语言/Range 行为。
4. C1 通用 MMT/TLV、HDR/DV 元数据和 HLS live 语义在 Exo 与 MPV 各自构建完成后分别验收；“同一 FFmpeg commit”不代表可以共用 `.so`。

本批次结束时，`mpv-android` 和 `libplacebo` 已完成逐提交审阅，`mpv` 完成输入/网络、光盘、媒体语义、Android HDR、音频和低风险格式组的初审；FFmpeg 49 个提交的逐提交结论仍在下一检查点补录。

## 检查点 6：2026-08-21 FFmpeg 逐提交审阅（49 个）

### 6.1 范围、重落基和树差异

FFmpeg 目标为 `release-9.0-fongmi@177f090e0503b7e013922ca903bde14b1c375f18`，项目锁定为 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`。两条历史共同祖先为 `03d9533176e98bb9fbf569c1f34968e73e948dd9`。对旧锁定树和新头做实际 tree diff 后，差异为 35 个文件、412 行新增/70 行删除；旧线的 AV3A、MMT/TLV、HLS/live、MediaCodec HDR/DV、DV 容器和 profile 5 GPU mapping 功能均已在当前树中存在。新头的 49 个提交不能按“49 个缺失功能”计算：

- 26 个是 FFmpeg 9.0.1 维护/安全修复或版本元数据更新；
- 18 个与旧线提交具有精确 `git patch-id --stable` 等价关系，只是重落基后 hash 改变；
- 4 个（`054c8690e16b377eb1c6375c8751a44b8eb1d962`、`befe89da89b4e75168f13256f36617672ce6c5d2`、`691a7d5a125b40dcc427ee298c983729e673d974`、`eb107bbafe37442065e42b4f2d410f371b758143`）是旧线 3 个主题提交的重写/拆分，相关功能在锁定树中已经存在；
- 1 个（`177f090e...`）是当前树真正新增的 DV7→P8.1 bitstream filter 能力。

因此建议把 FFmpeg 更新拆为 C0（安全基线，优先随 Exo 首次 FFmpeg 构建）和 C2（DV7 转换，等 MPV/Exo 具体调用场景确认后再决定）。FFmpeg 对 Exo nextlib（AAR 内打包 `libavcodec.so` 等共享库，NDK r28c）和 MPV native（共享库，NDK r29）仍须分别编译、分别验收，不能共用 `.so`。

### 6.2 49 个提交逐项记录

下表按新分支父链顺序列出完整 commit ID。`已有`指当前 `04482c...` 树已经包含等价行为；`新增`指当前树没有该行为；`随 C0`表示建议进入下一次 FFmpeg 安全基线构建；`随 C2`表示需要播放器功能场景确认后再带入。

| # | 完整 commit ID | 摘要/实际作用 | 当前树 | 建议与阶段 |
| ---: | --- | --- | --- | --- |
| 1 | `bd4a4a0e55bb4ab5d4cf5982f4d1855899921538` | `avformat/shared` 修正 `%zu` 与 `int64_t` 类型不匹配，消除 32 位 UB | 否 | 随 C0，低风险 |
| 2 | `b2f1a85428c36cd49033f57d22c869d9b3f833fe` | LCEVC Process Block 缺少尾部 padding 时警告并继续解析 | 否 | 随 C0；当前 App 未显式启用 LCEVC，收益主要是健壮性 |
| 3 | `e83aa76851c14d80e605c0458688eed11aa910ee` | swscale 快速双线性宽图边缘乘法改用 64 位，修复溢出并带 FATE 回归 | 否 | 随 C0，低风险；覆盖软解截图/缩放 |
| 4 | `9989a953fe78a6c05de314bcac603e653cb8ef0a` | `af_pan` 限制异常命名声道 ID，防栈数组越界 | 否 | 随 C0；与 MPV 音频滤镜链有关 |
| 5 | `f73d8c5c3597e689b5a86139eb540f707a7074fd` | Dolby E 截断且已解析大部分 mantissa 时保留可用音频 | 否 | 随 C0，低风险 |
| 6 | `4832d32889f70d89c06acc830257b9aeefed06bb` | SCD 零声道轨拒绝，避免除零 | 否 | 随 C0 |
| 7 | `8880a174d08131f94f58a0492d1c8c6d68b74f67` | libRIST 读取尊重调用方 buffer 长度，防越界写 | 否 | 随 C0；当前默认构建未启用 RIST，但安全修复应保留 |
| 8 | `b2df2f4f22be1452f6a054d9cb062b11c380e5a7` | MPEG-PS system header 传入真实 buffer 大小 | 否 | 随 C0 |
| 9 | `b274f0d21ba684446fd59b49e00f3f8e9ed954df` | 拒绝过多 MPEG-PS stream，防固定栈缓冲区溢出 | 否 | 随 C0 |
| 10 | `c7132ef8f63c383d11a00a9e3034748d8dd15fb3` | 拒绝 hvcC 中超过 16 位计数的 NAL 数组 | 否 | 随 C0；HEVC/MP4 输入相关 |
| 11 | `999f8ba75ce0bf1167677de7e11a5af678fdb866` | DASH 刷新时拒绝负 fragment index，防越界读 | 否 | 随 C0；与 Exo A5/MPV 网络输入都相关 |
| 12 | `1afd5c3ddafda4209e0881cd30684b919e99de7c` | RTP VC-2 单元长度超过 payload 时拒绝写入 | 否 | 随 C0 |
| 13 | `bc0ac77500ac2e66ba766de04b9a8d9ce1821c75` | Gopher 修复 CRLF 注入 | 否 | 随 C0；即使当前 App 不主动用 Gopher 也应带入 |
| 14 | `f175bd50821f9adcded3acacc6b8e04037a92715` | RTP AV1 keyframe 搜索限制 OBU 大小，防越界读 | 否 | 随 C0 |
| 15 | `0b13d4c50a7ab6b9320452e0ea8f647c7c315154` | RTP AV1 校验 `num_lebs` | 否 | 随 C0 |
| 16 | `7646bb4c42e6837a9396c3d9ab8d8cf476e2053b` | RTP AV1 避免把 OBU size 窄化为 `long` | 否 | 随 C0，32 位 ABI 尤其重要 |
| 17 | `c99a0e9ffdd01e7da9aeab8577ae5ed8272bc2c9` | WebP 动图 ICCP/EXIF 使用完整读取，避免未初始化内存 | 否 | 随 C0 |
| 18 | `96165329e52d5676cbc890c10eaebc4eee7a76b7` | FFmpeg 9.0.1 版本更新前置 | 否 | 随 C0；不单独 cherry-pick |
| 19 | `3fbb9560821bc9780e7c294528929707acc8ad41` | AFIR crossfade 限制在输入 frame 样本范围 | 否 | 随 C0 |
| 20 | `be329d65d563beb0797758e53f2828adfd643532` | spectrumsynth 统一两路输入像素格式，防越界 | 否 | 随 C0 |
| 21 | `d7e89879b693f1576cd271fe88b9a0439cc44d79` | MPEG-TS PES payload 不超过 `max_packet_size` | 否 | 随 C0；TS/HLS 输入相关 |
| 22 | `dc57051f46ef93e2d743f66e460198d5e1ca1f53` | MPEG-TS 拒绝小于一个 TS payload 的 max size | 否 | 随 C0 |
| 23 | `3cb0c0987f09080446ba18d91835dd1b1656018f` | MLV LJ92 packet 在真实数据末端结束，避免未初始化读 | 否 | 随 C0 |
| 24 | `b378901f4a1347c3b7fd20af744971e8afbfa970` | LCL 多线程块清理未解码区域 | 否 | 随 C0 |
| 25 | `2e41be62b762af5c03c3b2f3f5b9db69ef242aff` | FFmpeg 9.0.1 library version bump | 否 | 随 C0；与 `961653...` 成对 |
| 26 | `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa` | 9.0.1 changelog 更新 | 否 | 随 C0；发布元数据，不单独合并 |
| 27 | `d23e19c4da53c1b548956ed409ceb2428234c8a1` | HEVC MP4-to-Annex B 支持 packet-side hvcC extradata | 是；旧 hash `dca07f2768c03114bab9405e86cdf878e0728260` | patch-id 等价，随 C0 重建即可 |
| 28 | `d577f70043502e719571e7e0f932f81bcd63dfd7` | DASH fragment timescale 除零保护 | 是；旧 hash `f7dbf225263f050d04fc39e9994ed81735f87bc5` | patch-id 等价；随 C0 |
| 29 | `16cd5151014c1291fda24ffaf2b3516a05e4580a` | WAV BEXT coding history 分配上限 | 是；旧 hash `a9d1c7d9122192b8076a7c5b06a2951b0a2d637a` | patch-id 等价；随 C0 |
| 30 | `33342e9f2d9fc35b35138d95736ad7a52606b1a8` | HEVC significance context 表预计算 | 是；旧 hash `5536a6a37868c18237de8ff3b8c6d43ffecc32aa` | patch-id 等价；随 C0 |
| 31 | `16a193e64463bf1cf2f15b7547714d66a6a080cb` | HEVC DC-only block 跳过无效清理 | 是；旧 hash `be3bfa37887c391fe75231e13b63ccc95eef1dbd` | patch-id 等价；随 C0 |
| 32 | `9205422dbf82da88e563877bb1c4abf617c34165` | HEVC bypass bins 减少整数除法 | 是；旧 hash `84e1c3c65af2c4c68129790dc7a2aede4bc8584f` | patch-id 等价；性能收益，随 C0 |
| 33 | `3afff595293f94373965439b021d1940265d5888` | HEVC residual 解码移除 data-dependent branches | 是；旧 hash `58c1b1b2f63ab0faae0aac302cfe5ac84e872534` | patch-id 等价；随 C0，需软解回归 |
| 34 | `2151f558cf82ba671a925b3671b772b2adeeb05f` | MediaCodec 获取 codec 失败时增强日志 | 是；旧 hash `340c327b6d1ca3fee362e4152a1bd9b2b18e7cd8` | patch-id 等价；随 C0 |
| 35 | `774937ad551c2f1efd32505788ec78ad149a1d19` | 允许 MediaCodec profile mismatch 的硬解标记 | 是；旧 hash `6a0ca7bd6aec0e89f487115a6882a81acc9473e2` | patch-id 等价；随 C0，检查本地 starvation patch |
| 36 | `23484688ad6ddda545f2380657c85ab1969d4b76` | AV3A decoder/demuxer 支持（含 `libarcdav3a` 集成） | 是；旧 hash `9cf9b48e9ec5150d73cae6af177e53ccc07b5262` | patch-id 等价；Exo A2 和 MPV AV3A 已采用，不重复移植 |
| 37 | `410a1cd4753ddebf3a29799225eae23d4c745283` | dav1d 可选 `get_buffer2` 直接渲染 | 是；旧 hash `53be5e129ca7f3004a38e3126b0259c7ae57586b` | patch-id 等价；当前未打开 opt-in，暂不改行为 |
| 38 | `c67d95f8c85eaf667efa9fdcde0a6bbca110b5d2` | `af_pan` 运行时 remix command | 是；旧 hash `1c4cf4d9a5344a2c8c5ba1810f5bdae0efea7396` | patch-id 等价；MPV 音频设置可复用 |
| 39 | `e8392b0b0fb0ae6a827fa65f678cd4d6827f6f74` | HLS image-wrapped MPEG-TS segment | 是；旧 hash `e570d9c969d9efb8d902224dcc9dd36c1ffd5f67` | patch-id 等价；本地 Kuaishou PNG-HLS 代理仍保留 |
| 40 | `e640443a24dc89993042a99ade8a02a4d5ac2a81` | HLS/DASH/RTSP 三态 live status API | 是；旧 hash `fc7e0eb952e2b4548425c89b1c516d73351cc3f9` | patch-id 等价；C1 通用语义，App 目前主要由 Media3 消费 |
| 41 | `5805f9364c2e9a5f6ce625c9077b308c3ed4014d` | HLS discontinuity 前后时间戳连续化 | 是；旧 hash `e428ea67ac2eb144d8e495d33ee3c540e7347126` | patch-id 等价；与 Exo HLS 回归一起验证 |
| 42 | `054c8690e16b377eb1c6375c8751a44b8eb1d962` | ISDB-S3 MMT/TLV、TTML、raw payload 和 aribb24.js timed-ID3 | 是；旧 hash `57fd170a955746bf450ab0fad26cd211e8c101a9` 为同主题但非精确 patch-id | 当前树已含功能；C1 仅在有 ISDB-S3 输入时验收，不新增 App API |
| 43 | `befe89da89b4e75168f13256f36617672ce6c5d2` | MediaCodec 输出 timestamp 与 release-submission 状态 API | 是；旧 hash `2fade02f686a5d2cc297dfbfdc8e232fcf934a96` 为旧实现，最终树已等价 | 当前本地 MPV timestamped-release 补丁依赖此接口；随 C0 重建并核对 API，不单独移植 |
| 44 | `6dc8edecd7ebafc80764b8c0a20f87e3f9fb1382` | MediaCodec 安全 DV 输出路径与 base-layer fallback | 是；旧 hash `db299436755ff74f2d864b0bc696ceb6bc61b8d3` patch-id 等价 | 与 Exo A1、MPV B8 联合回归；保留 WebHTV 的输出能力判断 |
| 45 | `691a7d5a125b40dcc427ee298c983729e673d974` | 按 MediaCodec buffer timestamp 保留每帧 HDR 元数据 | 是；旧 hash `d0716248f08613432c69c5d7242d5608aa6f921f` 为重写实现 | 与本地 MediaCodec timestamp/诊断补丁联合；不单独 cherry-pick |
| 46 | `eb107bbafe37442065e42b4f2d410f371b758143` | 保留每帧 Dolby Vision RPU/解析元数据 | 是 | 与 MPV DV5 GPU mapping 和 libplacebo checked mapping 联合；需 DV 样片回归 |
| 47 | `dd537f9a852d0ce40078f9ac520d7267ba850883` | FFmpeg 9 保留 Matroska/MOV DV container/group 语义 | 是；旧 hash `cc9b28d3cc31e895d68df95bf364617deba61209` | patch-id 等价；随 C0/C2 构建 |
| 48 | `15b73698835285d68f9615691dd4dfc04422f28e` | MediaCodec DV profile 5 GPU mapping | 是；旧锁定即 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`，patch-id 精确等价 | 不是新增；当前项目已采用，后续只随 FFmpeg 全量重建 |
| 49 | `177f090e0503b7e013922ca903bde14b1c375f18` | `dovi_rpu` 新增 `convert=p81`：profile 7 RPU/配置改写为 P8.1，删除 EL | 否 | 新增 C2 候选；当前 Exo 使用 libdovi、MPV 使用本地 demux HDR10 patch，暂不默认启用 |

### 6.3 C0：FFmpeg 9.0.1 安全/性能基线（建议随 Exo 先合并）

**候选提交集合：** #1--#26，以及 #27--#48 的最终新 hash（这些是当前树已有功能的重落基/版本重建输入）。

**为什么先放 Exo：** `third_party/media-lock.json` 的 nextlib 直接编译 FFmpeg，且 `third_party/patches/nextlib-av3a.patch` 固定 `04482c...`；先将 nextlib FFmpeg 更新到 `177f090...` 并保留 `nextlib-ffmpeg-soft-load-shedding.patch`，可以先验证 Media3/AV3A/硬解回退，不必同时改变 MPV 的 `libmv*` ELF 命名和 Vulkan 链。之后 MPV 构建复用同一源码 revision，但仍按 NDK r29 重新产出共享库。

**收益：** 9.0.1 的边界检查覆盖 DASH、TS、HEVC、RTP、MPEG-PS、AV1、WebP、滤镜和多个音频 demux/decoder；HEVC 软解优化和 swscale 溢出修复对低端/32 位设备有实际价值。

**风险/冲突：**

- nextlib 的 FFmpeg 配置为 `--enable-shared --disable-static`，AAR 内打包 `libavcodec.so` 等裁剪后的共享库；`libarcdav3a` 才以静态方式链接进去。`177f` 的 `dovi_rpu` 仍需确认相关 bsf 是否被该裁剪配置编译、不会与 `nextlib-av3a.patch` 冲突。
- MPV 本地 `ffmpeg-mediacodec-port-starvation.patch` 修改 `libavcodec/mediacodecdec.c`；更新前必须对新头执行 `git apply --check`，并人工检查 MediaCodec buffer timestamp/release API 未被重复定义。
- FFmpeg 的 SONAME/文件名重命名只属于 MPV 构建后处理，Exo nextlib 仍保留 `libav*` 命名；不能把 MPV 的重命名结果装进 AAR。

**实施步骤：**

1. 复制当前 `third_party/media-lock.json` 和 nextlib AAR，建立 `ffmpeg-9.0.1-exo` 临时构建目录。
2. 将 nextlib 使用的 FFmpeg commit 改为 `177f090e0503b7e013922ca903bde14b1c375f18`，应用现有软解负载和 AV3A 补丁。
3. 编译两 ABI，验证 AV3A、HEVC/HDR、DV RPU、HLS PNG 包装、DASH live、SAMPLE-AES 和软解负载回退；失败时只回滚 Exo lock/AAR，不触碰 MPV assets。
4. Exo 验收通过后，再用同一 FFmpeg revision 进入 MPV B4/B8 成套构建，保留 `libmv*` ELF 改名和所有 WebHTV native patches。

### 6.4 C1：跨播放器媒体语义（按需随 A/B 阶段）

MMT/TLV（`054c8690e16b377eb1c6375c8751a44b8eb1d962`）、HLS discontinuity/live status（`e640443a24dc89993042a99ade8a02a4d5ac2a81`、`5805f9364c2e9a5f6ce625c9077b308c3ed4014d`）和 DV 元数据保留（`691a7d5a125b40dcc427ee298c983729e673d974`、`eb107bbafe37442065e42b4f2d410f371b758143`、`dd537f9a852d0ce40078f9ac520d7267ba850883`）同时影响 Media3/Exo、FFmpeg 和 MPV，但当前项目已有等价实现或没有对应输入消费方。建议：

- MMT/TLV 仅在真实 ISDB-S3 样片和字幕需求出现时，与 Media3 `ccf962e8912695dc60ce82aa4470df899c6306a3`、MPV `32c4d5adad29107756ae2987d69d92844bfed243` 一起验收；不提前增加 APK 能力。
- HLS live/timestamp 语义随 Exo A5 先测 Media3，再随 MPV B5 观察直接 lavf 输入；本地 HLS 代理重写路径不能用 FFmpeg 直链结果替代。
- HDR/DV 元数据随 Exo A1 和 MPV B4/B8 分别验证，允许共用源码 commit，不允许共用编译产物。

### 6.5 C2：DV7→P8.1 转换（新增但暂缓默认合并）

`177f090e0503b7e013922ca903bde14b1c375f18` 把 `dovi_rpu` bitstream filter 扩展为 `convert=p81`：要求 HEVC、profile 7、同时存在 RPU 和 base layer，重写配置记录和 RPU 元数据，删除 enhancement-layer NAL；`strip` 与 `convert` 互斥。它与当前项目功能相近但入口不同：

- Exo 目前通过 `DolbyVisionP81ExtractorsFactory` + `ExoplayerHdrUtils/libdovi` 在 extractor 层转换，并依据硬解能力锁定整次播放；直接采用 FFmpeg bsf 会改变错误处理、能力探测和 seek 前后的转换状态。
- MPV 目前通过 `mpv-dovi-profile7-hdr10-base-layer.patch` 的 demux 选项 `demuxer-dovi-profile7=hdr10` 清理 EL/RPU，再交给 MediaCodec base-layer decoder；FFmpeg bsf 可减少自定义 demux 代码，但还需要把 bsf 接入 MPV 的 load/demux 选项，并与本地 DV7 日志/失败回退保持一致。
- 该提交只处理 HEVC packet，不自动解决 Android 输出 sink、HDR metadata 宣告、P8.1 硬件能力或 RPU 无效时的用户策略；不能因为“转换功能存在”就删除 Exo/MPV 现有 fallback。

**实施记录：** C2 已按用户批准在 MPV 独立阶段完成窄适配：保留原生 DV7 优先，仅提供默认“升级 P8.1”和“降级 HDR10”两态；P8.1 能力不成立、转换失败或运行时失败时自动回退 HDR10。来源为 FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、mpv-android `eabfaf9501fc08fb726953a9328da43ae4154d35`，任务记录见 `docs/C2-dv7-p81-bsf.md`。两个 ABI 资产校验、Mobile/Leanback arm64 Debug 构建和策略单测已通过；电视端 P7 FEL 自动 P8.1 播放曾因主线程同步读取 `current-tracks/sub2/id` 卡死，已在任务文档记录并进入窄修复验证。实现提交为 `ae337b81e44657d85050bee3a9f92a780fb418ab`，文档 checkpoint 提交为 `408182daae91dc3d7486092dd223135f9419cc05`，对应恢复 tag 为 `recovery/C2-DV7-P81-BSF/20260829210209-408182daae91`。Exo 仍保持独立实现，不切换为共用 FFmpeg BSF。

### 6.6 FFmpeg 阶段验证矩阵与回滚边界

| 验证面 | Exo（NDK r28c / AAR） | MPV（NDK r29 / shared `.so`） |
| --- | --- | --- |
| 构建输入 | `177f090...` + nextlib 两个本地 patch；保留 `libav*` | `177f090...` + MediaCodec starvation/proxy Range + MPV/libplacebo patches；改名 `libmv*` |
| 编解码 | AV3A、HEVC/HDR10、DV5、DV7 P8.1、软解负载 | AV3A、DV5 GPU mapping、DV7 HDR10、MediaCodec timestamp/release、TrueHD |
| 容器/网络 | HLS PNG、SAMPLE-AES、DASH live/seek、MP4/MKV | HLS/DASH/TS/Matroska、HTTP Range、Blu-ray ISO、`stream_cb` |
| 安全回归 | malformed DASH/TS/HEVC/RTP、32 位 AArch32 | 同左，并加 ELF `SONAME`/`DT_NEEDED` 与 JNI ABI |
| 回滚 | 仅恢复 `third_party/media-lock.json`、nextlib AAR/POM | 仅恢复 `third_party/mpv-native-lock.json`、两 ABI native assets/JNI；不回滚 Exo AAR |

本检查点结论：FFmpeg 9.0.1 安全基线（C0）是目前最明确、最值得优先纳入 Exo 阶段的候选；FFmpeg 的跨播放器功能大多已经在当前树等价存在，真正新增的 C2 DV7→P8.1 bsf 需要产品/播放策略决策，不应顺手替换现有转换链。

## 检查点 7：2026-08-21 Media3 新线与 WebHTV fork 的提交映射

### 7.1 比较边界

上游候选是 `FongMi/media@release-1.11.0-fongmi` 的 `2bc207851df311340767e913931ca7b28cab1794..3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，共 82 个提交。WebHTV 已发布 fork 是 `e3e922d5c01bc0b564849940fe589daf37360d15`；两线共同祖先为 `5fb306449733dd71595700c1227ad6087578c559`，fork 侧有 80 个自身提交。

本地 `third_party/sources/media` 当前还叠加了构建准备产生的未提交改动，包括 `media3-upstream-playback-fixes-2026-08.patch`、`media3-dolby-vision-matroska.patch` 和 `media3-danmaku-live.patch`。因此本检查点分开记录：

1. “fork commit 等价”只比较已发布的 `e3e922d5...` 提交历史；
2. “准备后源码已有”必须进一步追溯到具体 patch hunk，不能因为工作树文件相同就声称某个上游 commit 已合并；
3. 最终实施仍以可重放的 commit/patch 为单位，不以当前 dirty source tree 为版本基线。

### 7.2 精确等价的 8 项

82 个上游提交中，57 个能在 fork 历史找到同标题提交，但稳定 patch-id 完全相同的只有以下 8 对：

| 上游完整 commit | WebHTV fork 完整 commit | 功能 | 结论 |
| --- | --- | --- | --- |
| `b63139c6432caa3f058e7f0496f0d754aa0eaa93` | `7c2a8135edb8eb5c04a7adebe48c5c96bb597b2f` | HLS/TS Dolby Vision | 已精确包含，不重复合并 |
| `08c664eb8a213a956ff2c8b3d0fcea49902a81fa` | `b3c7c816de39335ae8ff744ece3f44707e2907f3` | H.265 configuration parsing | 已精确包含 |
| `7c725b22f0b102e1447dd03dec557cc845db5049` | `8975884750e524ba4256fed83d9de2fa7e269b3d` | 音频短于视频时 seek hang | 已精确包含 |
| `ccc11523d57c3fd430c009b228c674a3195c9fdc` | `da1796da64acf3bd08aca4c3beff5c1ed09f9ccf` | SubRip parsing | 已精确包含 |
| `e8573d8c2ced07096c368d7ec3a40bc2e790d203` | `cccc786e5de5f861705adaba1b6ab760f484f2ee` | DVB subtitle alignment | 已精确包含 |
| `624167c2a0eaf9af94011e0a556aaf91a15fb25f` | `12a10ec5114c0fdaf6eaed4634ad87a7f0a19da1` | Matroska EBML resync | 已精确包含 |
| `7feb08018a6e159330293de4878ebc3c9df2ca86` | `b14c2dcc5899067f93496a82c200cfc719485da1` | compressed Matroska text | 已精确包含 |
| `65ee9ba81815e67c9d3d08a2be0028859cc20569` | `71262cce9d228daf95592bcbbab0b9ac3fbd8ae5` | file URI 大小写不敏感 | 已精确包含 |

这 8 个上游 hash 仍保留在阶段清单中作为来源审计，但实施时必须标记为“跳过，fork 已包含”，不能再次 cherry-pick。

### 7.3 同标题但非精确等价的 49 项

另外 49 项在 fork 中有同标题版本，但 patch-id 不同。原因可能是 Media3 基线迁移后的上下文差异、提交被拆分/合并、本地 API 适配，也可能是上游后来确有功能增强。当前只能得出“存在同主题本地实现”，不能得出“完全等价”或“全部缺失”。

后续按 A1、A3、A4、A5 分组做三层比较：先比较提交触及文件和公共 API，再比较最终树行为，最后检查 App 调用点和本地 patch。光盘/extractor、DRM、UI、danmaku、FFmpeg renderer 等暂缓组只做冲突与可达性复核，不投入与核心播放修复同等的验证成本。

### 7.4 没有同标题本地提交的 25 项

以下 25 个是第一轮深审的残余集合；“无同标题”仍不等于“当前源码没有同等行为”，因为本地可能通过重写提交或未提交 patch 实现。

| 阶段 | 上游完整 commit | 当前已知关系与下一步 |
| --- | --- | --- |
| A1-DV/HDR | `f70e4b6f14d9f3b38ef953be80c53184f9c50bed`, `0cefd3ceec27444cf8faf02486b472bab39109fe` | 前者核对本地 MP4/MKV color info 与 DV RPU patch；后者与 App 的 `DolbyVisionP81ExtractorsFactory`/能力锁定策略逐 hunk 对照，禁止整提交覆盖 |
| A2/A7-native decoder | `0417078bfbac37b5012991d696ab8a4803cb2805`, `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97`, `ca7dd917ad574d4241640eb9282f20c5decd5aea`, `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910`, `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` | 本项目已有独立 nextlib/AV3A/软解负载方案；评估 API 思路，不直接引入上游二进制 SDK 或第二套 renderer 资产 |
| A4-字幕 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`, `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`, `6794d75b7a39db42dcfcab18c915f0da165515b5`, `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 与 fork 的字幕 pipeline 重写提交和 App 字幕 UI/Cue 消费面联合比较；TTML 末项是明确候选，但 API 面较大 |
| A3/A5-播放修复 | `a1e190005981febfa27e7583e5902d3cc2ce4ef7`, `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6`, `aac6ec964681dd0476a33e3ad220ca7b5bf771f6`, `ccf962e8912695dc60ce82aa4470df899c6306a3` | SAMPLE-AES 与本地 synthesized-PUSI 修复核对；DTS variants、H.264 AU 是低风险候选；MMT/TLV 仅在真实输入需求存在时进入 C1 |
| A6-预加载 | `dd00f94b58b7324ab29febb0b50f3a190d544a3b` | 与 App `PreCache`、本地代理、MPV/Exo 缓存策略高度重叠，默认暂缓 |
| A7-UI/诊断/发布 | `3216effea715a906ce9dd02ed50b46afe7f14ad4`, `1e064c30588bde89bf26798d10f071c40fd8da29`, `c85d124102c5b25a1bcd270d78f78603e87a6214`, `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486`, `0f6191bc1bdd7324eef5e512cada65d9b974a6ed`, `ab1bfd8779a4c9112d2a7ad61725f61668dfda85`, `12670ce4fb23ad32ed3875d0250486eabe957913`, `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8`, `7cca3b0bb5cbdccea639e602e713301d8116a99f` | 当前 App 已有独立控制、诊断和 mpvplayer 集成；原则上不进入 Exo 第一轮依赖合并 |

### 7.5 lock 中既有 cherry-pick 的处理

`third_party/media-lock.json` 记录的 9 个完整 upstream cherry-pick：

```text
d1bf079238ae4a36d64f0f3b2129b1944a824317
8103b046bcc0d7e7b8972e709a8ec1002700c96c
8465e107ece2bb26d5fb45d8d302559fc8783fd8
6f542f2fa4ac8bd76b376da79dc84f0f98fff2e3
7745e47713505609a594d5355426b84d0f679c69
dad705254f16b5b276bf56929736f461359ca1ee
b33316303d3ef5ad0ede57cbc9dab6d8ab1e63a6
bb3d9921f14d900a2e76528f2d97cf82e925a499
fc38c2cda8789db646ae6a01e06494de059a2495
```

以及局部前置 `2fcf67cc83b295f389c0c9c4c5b5c40615781dcf`，均已确认是新头 `3c2cbe8a...` 的祖先。如果未来把 fork 基线迁到新线，这 10 项不能再次重放；需要从 lock 的“待应用 patch”改为“新基线已继承的历史来源”。但在本轮只做评估，不修改 lock。

### 7.6 Exo 后续实施顺序

1. A1 先处理 DV/HDR/MediaCodec，因为它与 App 现有 DV7→P8.1 策略、FFmpeg C0/C2 和 MPV B8 的决策耦合最高。
2. A3 处理 DTS/TrueHD/多声道，优先挑可独立验证的 parser/format 修复。
3. A4 处理字幕 pipeline、ASS/PGS/DVB/TTML，以 Cue API 和 WebHome/TV UI 回归为合并边界。
4. A5 处理 DASH/HLS/TS/MP4/MKV/RTSP，先合并 hang/边界修复，再考虑新 extractor 和产品功能。
5. A2/A6/A7 保持后置；除非前四组暴露硬依赖，不与 Exo 第一轮核心升级混合。

本检查点只完成“提交身份与重复项”判定，不代表 74 个非精确项已经完成语义审阅。下一检查点从 A1 开始，每完成一个功能组立即落盘收益、风险、完整 hash 和可执行验证步骤。

## 检查点 8：2026-08-21 A1 Dolby Vision/HDR/MediaCodec 深审

### 8.1 A1 提交身份与最终归类

本组逐 hunk 对照了上游提交、WebHTV fork `e3e922d5c01bc0b564849940fe589daf37360d15`、当前准备后的 `third_party/sources/media`、App 自定义 renderer 和 DV7→P8.1 sample transformer。A1 原索引中的 6 个提交最终关系如下。

| 上游完整 commit | WebHTV 对应实现 | 最终判定 |
| --- | --- | --- |
| `1066f642a64434e7c3c0be687d3e94a4ca2815d7` | `0592f21c689d325b03f8fed4461d15e29f9ea7f4` 为同主题重落基；后续 `07cc217a1148f139af0c3480e6be05b082239516`、`a49c4b7a9693c686777932119023997569fd75a6` 才消费多 MIME 能力补齐 DTS-HD MA/DTS:X | 从 A1 移到 A3。当前 fork 已有多 alternative MIME API；上游版本额外保留 Google 设备 E-AC3 JOC 降级保护，随 A3 单独复核，不作为 DV 阶段依赖 |
| `b63139c6432caa3f058e7f0496f0d754aa0eaa93` | `7c2a8135edb8eb5c04a7adebe48c5c96bb597b2f` | patch-id 精确等价；HLS/TS DV 注册描述符与 `0xB0` descriptor 已包含，跳过 |
| `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` | 无等价提交或本地 patch | 真正缺失，建议作为 A1-1 低风险候选移植 |
| `249774647b026e16b56467eb5d79479816f79f11` | `8d46d1212be4dc7a3665b196f42aeda0bd340c1a` | 功能语义等价，跳过；`DolbyVisionDescriptor.java` 与 `H265Reader.java` 的最终 blob 完全相同，剩余差异仅格式化及 fork 后续增加的 SAMPLE-AES/HDMV/PGS/AV3A reader case |
| `0cefd3ceec27444cf8faf02486b472bab39109fe` | 无单一等价提交；与 App 自定义 DV fallback/P8.1 链有明显重叠 | 不允许整提交 cherry-pick；拆为 A1-1 解析安全、A1-2 CSD 语义、A1-3 输出策略三阶段评估 |
| `08c664eb8a213a956ff2c8b3d0fcea49902a81fa` | `b3c7c816de39335ae8ff744ece3f44707e2907f3` | patch-id 精确等价；H.265 configuration parsing 已包含，跳过 |

因此 A1 没有必要重放 `b63139c6...`、`24977464...`、`08c664eb...`；实际决策只剩 `f70e4b6f...` 和 `0cefd3ce...` 的选择性 hunk。`1066f642...` 的 DTS/E-AC3 影响归入下一检查点 A3。

### 8.2 当前 WebHTV 的 Dolby Vision 行为边界

评估 `0cefd3ce...` 时必须保留以下现状，不能只比较 Media3 类文件。

- 当前 Media3 renderer 已有旧版“显示不声明 Dolby Vision 时尝试 compatible base-layer decoder”的逻辑，但只把部分 Profile 4/8/9/10 映射到 HEVC/H.264/AV1；Profile 5 和 Profile 7 不会由该旧逻辑映射。
- App 额外把 `DolbyVisionHdr10FallbackRenderer` 放在平台 DV renderer 之后。它对 Profile 5 始终开放，对 Profile 7 由用户“DV7 处理”设置控制，并把格式强制改写成 HEVC + BT.2020/PQ 的 HDR10 视图。
- 默认的 DV7 模式不是 HDR10，而是 `DolbyVisionP81ExtractorsFactory`：只有原 DV7 无硬件解码、P8.1 有硬件解码时，才用 libdovi 改写 RPU、删除增强层并锁定本次会话；转换失败时中止，不在会话中静默切换 HDR10。
- Matroska 的本地 `media3-dolby-vision-matroska.patch` 可把 BlockAdditional RPU 追加到 HEVC sample；它不负责保存或重写 container DV configuration record。
- `ExoDolbyVisionPlaybackState` 分别记录实际 HDR10 fallback 和 P8.1 转换，用于播放详情与诊断；任何新策略都必须继续按实际 renderer/codec 初始化结果更新，而不能按“理论可回退”提前标记。
- 当前 `DolbyVisionP81ExtractorsFactory.asProfile81()` 只把 `dvhe.07`/`dvh1.07` codec string 改为 Profile 8，并转换 sample；它没有改写 `Format.initializationData` 中的 DV `csd-2`。TS 路径通过 `24977464...` 的等价实现已经可能携带 Profile 7 `csd-2`，MP4/MKV 若采用 `0cefd3ce...` 后也会携带。由此产生的 Profile 8 codec string + Profile 7 CSD 不一致，是 A1-2 实施前必须修复的真实兼容风险。

### 8.3 A1-1：HDR 静态元数据与畸形 DV 配置防护

#### 候选来源

- `f70e4b6f14d9f3b38ef953be80c53184f9c50bed`
- `0cefd3ceec27444cf8faf02486b472bab39109fe` 中仅限 `DolbyVisionConfig.parse()` 和 MP4 DV box 边界校验的 hunk

`f70e4b6f...` 的提交说明声称“对齐 Matroska 与 MP4 color info extraction”，但实际 diff 很小且明确：Matroska 的 `minMasteringLuminance` 来自 cd/m² 浮点值，写 CTA-861 HDR Static Metadata 时必须乘 `10000`，当前 fork 直接四舍五入，常见的 `0.0001 cd/m²` 会错误写成 `0` 而不是 `1`。该提交只增加比例常量、修正一个 `putShort` 和对应测试，不改变 bitstream color metadata 优先级。

`0cefd3ce...` 中可以独立移植的解析防护包括：

- `DolbyVisionConfig.parse()` 在读取 4 字节前检查长度，并拒绝 `dv_version_major != 1`，避免短数据越界或把未知版本误识别为支持格式。
- `BoxParser.parseDolbyVisionConfigBytes()` 验证 `dvcC`/`dvvC`/`dvwC` box 至少含 4 字节配置、不能越过 sample entry 或输入 limit；畸形 MP4/MOV 明确抛出 container parse error。

**收益：** HDR 最低亮度在 Android `hdrStaticInfo` 中正确表达；恶意或损坏 DV box 不再触发裸数组越界/错误 profile。两者与 App 的 DV7 产品策略无关，可单独验证和回滚。

**风险：** 极低到低。`f70e4b6f...` 与本地 Matroska RPU patch 修改同一大文件但不触及同一函数块，预期只有上下文行号冲突。MP4 防护会把过去可能勉强继续的畸形文件改为明确失败，需要用现网容错样片验证没有错误拒绝合法的扩展 box。

**建议：** A1-1 优先合并。为保持来源审计，可建立一个 WebHTV backport commit，同时在提交说明记录两个完整上游 hash；不要为了取得这几个 hunk 整体引入 `0cefd3ce...`。

### 8.4 A1-2：容器 DV CSD 保真与 compatible base-layer 判定

`0cefd3ceec27444cf8faf02486b472bab39109fe` 的第二组改动把原始 Dolby Vision configuration record 统一保存为 `csd-2`，再依据其中的 `bl_present_flag` 和 `dv_bl_signal_compatibility_id` 判断能否安全按标准基底层解码。

| 文件/能力 | 上游行为 | 当前项目差异 | 建议 |
| --- | --- | --- | --- |
| `CodecSpecificDataUtil.setDolbyVisionCsd()` | 若 index 2 已是 DV CSD 则替换；若是别的 CSD 则插入并保留原项，不再无条件覆盖 | 当前实现会直接覆盖已有 index 2 | 建议带入，防止破坏其它 initialization data |
| `getDolbyVisionCsd()` / `hasDolbyVisionRpu()` | 校验 major version、最短长度、合法 profile，并要求 CSD profile 与 codec string 一致 | 当前没有读取/一致性 API | 建议带入；P8.1 转换测试可直接利用一致性检查 |
| `getDolbyVisionCompatibleBaseLayerMimeType()` | Profile 7 必须明确 `bl_present`；Profile 8/10 还必须有兼容 ID 1/2/4；Profile 4/9 按标准兼容，显式无 BL 时拒绝 | 当前旧逻辑主要按 profile 猜测，Profile 8 不验证 container compatibility signalling | 建议带入，但只作为判定原语，不直接接管 App 产品策略 |
| `MatroskaExtractor` | 把 CodecPrivate 中的 DV config 保存进 `Format.initializationData` | 当前只设置 mime/codecs；本地 RPU patch只改 sample | 条件合并，需 MKV DV5/7/8 样片和 vendor codec 回归 |
| `BoxParser` | 复制完整 `dvcC`/`dvvC`/`dvwC` payload 并保存进 `csd-2` | 当前只解析 profile/level，丢弃 flags 和兼容 ID | 条件合并，需 MP4/MOV DV 回归 |
| `getDolbyVisionBaseLayerMimeType()` | 增加 Profile 7→HEVC 编码映射 | 当前不映射 Profile 7 | 只能与 compatible 检查配套使用，不能把“编码基底类型”误当成“已证明可安全 fallback” |

#### 必须同时完成的 WebHTV 适配

1. `DolbyVisionP81ExtractorsFactory.asProfile81()` 不能再只改 codec string。转换为 P8.1 时必须把合法 Profile 7 `csd-2` 改成 Profile 8，保留实际 level，设置转换后真实的 `rpu_present=1`、`el_present=0`、`bl_present=1` 和 HDR10 compatibility id（P8.1 为 `1`），或者在无法可靠重建时删除旧 DV CSD，绝不能把 Profile 7 CSD 交给 Profile 8 session。
2. 对 TS、MP4、MKV 三条输入路径使用同一组 CSD 一致性测试。TS 已有 `24977464...` 等价代码，不能只测试新加的 MP4/MKV。
3. 本地 Matroska BlockAdditional RPU 追加逻辑继续保留；CSD 描述整条 track，RPU 属于逐 sample 动态元数据，两者不是替代关系。
4. 加密内容仍保持 P8.1 转换禁用；CSD 传播不得改变 secure decoder 查询或 DRM session。

**收益：** 不再仅凭 profile 猜测是否存在 HDR10/SDR/HLG 基底层；可正确区分 Profile 8 的标准兼容和非兼容变体，也为 DV7 fallback、诊断和以后 FFmpeg C2 对比提供统一事实来源。

**风险：** 中等。新增 `csd-2` 会直接进入 Android `MediaFormat`，不同厂商 codec 对额外 CSD 的容忍度必须实机确认；同时会暴露当前 P8.1 stale-CSD 问题。A1-2 应在 A1-1 稳定后单独合并，不能和 renderer 策略同一提交上线。

### 8.5 A1-3：显示能力、Profile 5 tone mapping 与 renderer fallback

`0cefd3ceec27444cf8faf02486b472bab39109fe` 的第三组是约 400 行的策略改造，不是普通 parser 修复：

- 新增 `DolbyVisionOutputPolicy.AUTO`、`ASSUME_SUPPORTED`、`ASSUME_UNSUPPORTED`；API 34+ 从当前 display mode 读取 HDR 类型，较旧系统读取 `HdrCapabilities`。
- `DefaultRenderersFactory` 和 `MediaCodecVideoRenderer.Builder` 暴露 policy。
- 显示不允许原生 DV 时，仅在 `getDolbyVisionCompatibleBaseLayerMimeType()` 证明兼容后查询 HEVC/H.264/AV1 fallback decoder。
- API 31+ 若没有可证明的标准基底层（典型是 Profile 5），仍查询 Dolby Vision decoder，但在 configure 时请求 `MediaFormat.KEY_COLOR_TRANSFER_REQUEST = COLOR_TRANSFER_SDR_VIDEO`。
- 同步/异步 MediaCodec adapter 在 configure 后检查 codec input format 是否接受 tone-map request；拒绝时抛错。异步 adapter 同时改为分项记录 callback、queue 和 codec 启动状态，保证 configure/验证中途失败也能完整释放线程和 codec。
- tone mapping 支持只能在 configure 后确认，因此 renderer 在此前报告 `FORMAT_EXCEEDS_CAPABILITIES`，让其它真正 `FORMAT_HANDLED` 的 renderer 有机会被优先选择。

这组改动的方向有价值，但不能原样覆盖 WebHTV：

| DV 类型 | 当前 WebHTV | `0cefd3ce...` 默认行为 | WebHTV 建议语义 |
| --- | --- | --- | --- |
| Profile 4/9 | 旧 Media3 可按标准基底层 fallback | 继续 fallback，并可利用 CSD 显式拒绝无 BL | 采用上游安全判定即可 |
| Profile 8/10 | 主要按 profile/color info 推断 | 优先使用 CSD compatibility id 证明 | 采用；错误拒绝比把非兼容层当 HDR10 更安全 |
| Profile 7 | 当前由“原生/P8.1/HDR10”用户策略控制；HDR10 renderer 不验证 `bl_present` | 显示不支持 DV 时，只要 CSD 证明 BL 就自动选 HEVC | 不允许自动绕过用户 DV7 选择。P8.1 仍是默认策略；选择 HDR10 时也必须先证明 BL，缺少/冲突 CSD 时停止或走明确的其它 fallback |
| Profile 5 | 当前自定义 renderer 无条件把它伪装为 HEVC HDR10 | 无标准兼容 BL；API 31+ 请求 DV decoder 输出 SDR tone mapping | 上游方案语义更正确。Profile 5 不是 HDR10-compatible base layer，建议 API 31+ 优先使用“已被 codec 接受”的 tone mapping；当前 HEVC-as-HDR10 不应继续作为无条件默认 |
| P8.1（由本地 DV7 转换） | 转换 sample + codec string，保持一次会话锁定 | 若 CSD 仍是 P7，会被一致性检查拒绝，随后可能进入 tone-map/其它 renderer 分支 | 先完成 A1-2 的 CSD 重写；保持 P8.1 renderer 会话锁定和实际路径诊断 |

#### A1-3 决策选项

1. **保守方案：暂不合并策略层。** 只做 A1-1；A1-2 也可在适配 P8.1 CSD 后独立上线。优点是完全不改变 renderer 选择；缺点是 Profile 5 继续走不严谨的 HEVC/HDR10 伪装，Profile 7/8 的 BL 判断仍不够精确。
2. **适配方案：条件推荐。** 移植 output policy、CSD-based eligibility、tone-map request 与 adapter 验证，但在 WebHTV renderer 层保留“原生优先 + DV7 用户策略 + P8.1 会话锁定”。Profile 5 改为 API 31+ codec tone mapping，DV7 不允许上游 AUTO 分支抢先绕过设置。这是收益最大的长期方案，但需要 App 和 Media3 fork 联合改造，不能只升级 AAR。
3. **整提交方案：不推荐。** 直接 cherry-pick `0cefd3ce...` 会让默认 renderer、新增自定义 HDR10 renderer和 P8.1 transformer同时参与决策，容易出现双重 fallback、错误 renderer 抢占、状态标记不准，以及 Profile 7/8 CSD 不一致。

### 8.6 可实施阶段与合并顺序

#### 阶段 A1-0：重复项清理（无代码变更）

1. 在迁移清单中把 `b63139c6432caa3f058e7f0496f0d754aa0eaa93`、`249774647b026e16b56467eb5d79479816f79f11`、`08c664eb8a213a956ff2c8b3d0fcea49902a81fa` 标为“fork 已包含/语义等价”。
2. 保留对应 fork 来源 `7c2a8135...`、`8d46d121...`、`b3c7c816...`，迁移到新基线时避免重复应用。
3. 把 `1066f642a64434e7c3c0be687d3e94a4ca2815d7` 移交 A3，不让音频 API 扩展扩大 A1 回滚面。

#### 阶段 A1-1：低风险 HDR/parser backport（建议先实施）

1. 手工移植 `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` 的 minimum mastering luminance 单位修复和测试。
2. 从 `0cefd3ceec27444cf8faf02486b472bab39109fe` 只移植 `DolbyVisionConfig` 短数据/major version 防护及 MP4 DV box 越界测试；暂不把 CSD 写入 `Format`。
3. 重放 `media3-dolby-vision-matroska.patch`、`media3-upstream-playback-fixes-2026-08.patch` 和 danmaku patch，确认没有同块冲突。
4. 发布独立测试 AAR；失败时只回滚该 AAR/Media3 backport，不触碰 nextlib FFmpeg 或 MPV assets。

#### 阶段 A1-2：DV CSD 保真（条件合并）

1. 移植 `set/getDolbyVisionCsd`、compatible BL helper、MP4/MKV CSD 传播及上游单元测试。
2. 先修正 `DolbyVisionP81ExtractorsFactory` 的 P7→P8.1 CSD 重建，再允许 MP4/MKV 新 CSD 到达 App。
3. 对 TS/MP4/MKV 的同内容样片比较 `Format.codecs`、`csd-2` profile/flags、首帧和 seek 后首帧；确保 BlockAdditional RPU 不重复。
4. 单独发布 AAR + App 测试包。若厂商 codec 因新增 `csd-2` 回归，只回滚 A1-2，保留 A1-1。

#### 阶段 A1-3：WebHTV-aware 输出策略（需用户决策）

1. 以 `0cefd3ce...` 的 output policy 和 tone-map 验证为基础建立适配提交，不直接 cherry-pick。
2. 明确 renderer 优先级：显示与 decoder 均支持时原生 DV；DV7 按用户选择进入 P8.1 或经 CSD 证明的 HDR10；Profile 5 在 API 31+ 优先尝试已验证的 codec SDR tone mapping。
3. 只有 codec configure 成功且接受 transfer request 后，才记录 tone-map 路径；失败要回到下一受控 renderer 或明确报错，不能把理论路径写进 `ExoDolbyVisionPlaybackState`。
4. A1-3 独立于 FFmpeg C0 发布；通过后再决定是否删除/收窄 `DolbyVisionHdr10FallbackRenderer` 的 Profile 5 分支。

### 8.7 A1 验证矩阵

| 维度 | 必测组合 | 通过标准 |
| --- | --- | --- |
| 容器 | HLS/TS、MP4/MOV、MKV（含 BlockAdditional RPU） | 三条路径输出一致的 codec/profile/CSD；MKV RPU 每个 AU 不重复；seek 后仍一致 |
| HDR 静态信息 | MKV minimum mastering luminance `0.0001`、`0.005`、缺字段 | CTA-861 值分别为 `1`、`50`；缺字段仍不伪造完整 HDR static info |
| 畸形输入 | 0--3 字节 DV config、major version 非 1、box 越过 sample entry/limit | 受控返回 null 或 `ParserException`，无越界、无 native crash |
| DV profile | 4、5、7 MEL/FEL、8.1、8.4、9、10 | 只对被 CSD/标准证明的 BL 启用 fallback；Profile 5 不冒充 HDR10；P8.1 CSD 与 codec string 一致 |
| 系统/显示 | API 24--30、31--33、34+；DV 显示、非 DV 显示、能力误报设备 | API 31 以下不请求 tone mapping；API 34 使用当前 mode；原生 DV 不被无故降级 |
| decoder | 原生 DV、仅 HEVC/H.264/AV1、DV decoder 接受/拒绝 transfer request | renderer 选择唯一且可解释；拒绝 request 后线程/codec 完整释放，无泄漏或重复初始化 |
| WebHTV 策略 | DV7“升级 P8.1”与“降级 HDR10”、加密/非加密、原生 DV7 支持/不支持 | 用户选项不被默认 renderer 绕过；P8.1 会话不静默切换；加密内容不进入 sample transformer |
| 诊断/UI | 原生 DV、P8.1、HDR10、SDR tone-map 四条实际路径 | 播放详情只显示实际启用路径，退出/换集/seek 后状态正确复位 |

最低自动化集合应包含上游 `CodecSpecificDataUtilTest`、`DolbyVisionConfigTest`、`BoxParserTest`、`MediaCodecVideoRendererTest` 的相关新增 case，以及现有 `DolbyVisionP81ExtractorsFactoryTest`、`ExoDolbyVisionFallbackPolicyTest`。实机至少覆盖一台 API 31--33 手机、一台 API 34+ 设备和一台支持原生 Dolby Vision 的电视/盒子。

### 8.8 与 FFmpeg、MPV、libplacebo 的联合边界

- A1-1/A1-2 都不要求先升级 FFmpeg；不要把 Media3 parser AAR、nextlib FFmpeg 9.0.1 和 MPV native 放在同一回滚单元。
- FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` 的 `dovi_rpu convert=p81` 仍属于 C2 实验项，不能替代 A1-2 的 container CSD 保真，也不能自动继承 App 的 decoder/display/用户策略。
- FFmpeg 新线的 `6dc8edecd7ebafc80764b8c0a20f87e3f9fb1382`、`691a7d5a125b40dcc427ee298c983729e673d974`、`eb107bbafe37442065e42b4f2d410f371b758143`、`dd537f9a852d0ce40078f9ac520d7267ba850883` 可与 A1 共用 DV/HDR 样片和期望元数据，但 Exo nextlib 与 MPV native 仍各自构建和验收。
- MPV B8 后续应复用 A1 的 display/profile/CSD 测试矩阵，尤其比较 DV5、DV7 HDR10 和 P8.1；不复用 Media3 renderer 代码。
- libplacebo 的 alpha 保留和 shader allocation 修复不阻塞 A1；只有 MPV GPU DV mapping 才进入 B4/B8。

### 8.9 当前建议，供合并决策

| 决策项 | 建议 | 原因 |
| --- | --- | --- |
| `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` | 合并，A1-1 | 实际是明确的 CTA-861 单位修复，收益清晰、风险很低 |
| `0cefd3ce...` 的短 CSD/MP4 box 边界 hunk | 合并，A1-1 | 安全与容错收益，可独立于产品策略验证 |
| `0cefd3ce...` 的 CSD 保存与兼容 BL helper | 条件合并，A1-2 | 能消除 profile 猜测，但必须先修 P8.1 stale-CSD，并做厂商 codec 回归 |
| `0cefd3ce...` 的 output policy/tone mapping | 适配后再合并，A1-3 | Profile 5 方案优于当前伪 HDR10，但原样合并会绕过 DV7 用户策略并造成多 renderer 竞争 |
| 整体 cherry-pick `0cefd3ceec27444cf8faf02486b472bab39109fe` | 不合并 | 解析、API、renderer 和生命周期耦合过大，当前 App 有重叠实现 |
| `b63139c6...`、`24977464...`、`08c664eb...` | 跳过 | 已精确或语义等价包含 |

本检查点完成 A1 深审并已落盘。下一检查点进入 A3，重点区分当前 fork 已有 DTS 系列重写与新线残余差异，同时单独核对 `1066f642...` 上游新增的 Google 设备 E-AC3 JOC fallback 防护是否应补回。

## 检查点 9：2026-08-21 A3 音频、DTS、TrueHD/Atmos 深审

本检查点只做提交归因、现有 fork 对照和实施设计，没有更新 `third_party/media-lock.json`、AAR、native 二进制或 App 播放代码。`third_party/sources/media` 的构建准备未提交改动保持原样。

### 9.1 提交身份与当前 fork 对应关系

A3 涉及的上游完整 commit 如下。表中的“当前 fork 对应”是 WebHTV `third_party/sources/media` 已存在的等价或同主题提交，不代表新 `release-1.11.0-fongmi` 线可以直接 cherry-pick 到当前 fork。

| 上游完整 commit | 主题 | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| `1066f642a64434e7c3c0be687d3e94a4ca2815d7` | 多 alternative MediaCodec MIME | `0592f21c689d325b03f8fed4461d15e29f9ea7f4`，并由 `07cc217a1148f139af0c3480e6be05b082239516` 消费 DTS-HD MA fallback | 主体已有；只补 Google/Pixel E-AC3 JOC guard |
| `98d7e9518169f187ad2915f20fa46f76ba256fc6` | DTS-HD MA/coreless profile 与 fallback | `07cc217a1148f139af0c3480e6be05b082239516` | 跳过，fork 已覆盖 |
| `eb4aa3e445c1df1f6a58eb9e8896e2f4e1998486` | DTS MIME 重命名/refactor | `bb5663b5d06d26dc816e181636f656484e967235` | 跳过，fork 已覆盖 |
| `908b27d736ed1c60d237654debc042b61363d081` | 独立 DTS elementary extractor | `bb688170119645a7303ddd114f04f4e7a7ff6831` | 跳过，fork 已覆盖；只保留格式差异核对 |
| `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` | WAV 中 DTS-CD | `b09d8cfcc6e8a7fc20cfa66540424e168e66e94a` | 主体已有；只补 14-bit frame size 修复 |
| `d9ffc31a50fc2377a6b2c91eb3579c4b8e9eab78` | DTS:X/IMAX codec marker | `a49c4b7a9693c686777932119023997569fd75a6` | 跳过，fork 已覆盖并已有测试 |
| `ba3af5240658745bb6383086b8be43438285adc1` | AAC PCE channel parsing | `69fb5499cf1af600fa611bc9e1d5e17e96cbeb17` | 跳过，语义等价；差异主要是格式化 |
| `1cc8573cab9e2453e7917aff1b8945482c8b2190` | E-AC3 dependent substream、TrueHD/Atmos、容器接线 | `965a80fedf940235ca70f7602deb6813dd058dfe` | 真实新增，不能整体重放 |
| `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` | generic TS `0x82` DTS probe | 无对应 | 条件推荐；需要连 App flag/API 一起改 |

当前 fork `e3e922d5c01bc0b564849940fe589daf37360d15` 的祖先已包含表中 fork 侧提交，`965a80fedf940235ca70f7602deb6813dd058dfe` 是当前 HEAD 祖先。上游 `1cc8573...` 不在当前 fork 内；因此 A3 不能用“整棵树相似”替代实际 commit diff。

### 9.2 已有实现与可跳过提交

#### 9.2.1 `98d7e951...`、`eb4aa3e...`、`908b27d7...`、`d9ffc31a...`、`ba3af524...`

这五个提交的功能在当前 fork 中已经由不同 hash 的提交提供：

- DTS-HD MA/coreless profile、编码常量、DTS decoder fallback 已由 `07cc217a...` 提供。
- DTS MIME 重命名/refactor 已由 `bb5663b5...` 提供。
- 独立 DTS extractor 已由 `bb688170...` 提供；差异主要是格式化和上下文行移动。
- DTS:X、DTS:X IMAX marker 已由 `a49c4b7a...` 提供，且 fork 带 `DtsUtilTest` marker 测试。
- AAC PCE channel parsing 已由 `69fb5499...` 提供，语义与上游 `ba3af524...` 等价。

建议在迁移清单中保留完整上游 hash，动作标记为“跳过（fork 已包含）”，不要把这些提交再组成新的 AAR 回滚单元。若未来切换到纯上游 `release-1.11.0-fongmi` 基线，则应重新做 patch-id 和最终树检查，不能把本表直接当作 cherry-pick 顺序。

#### 9.2.2 `1066f642...`：只补 E-AC3 JOC 的 Google/Pixel 防护

上游提交把 `getAlternativeCodecMimeType()` 改为有序列表 `getAlternativeCodecMimeTypes()`，支持 DTS-HD/DTS-UHD 等多个 alternative MIME。当前 fork 已经具备这一主体，且 `07cc217a...` 已补 DTS-HD MA profile、coreless 和 fallback 逻辑。当前仍缺少的是：

```java
Build.MANUFACTURER == "Google"
```

设备上的 E-AC3 JOC fallback guard。普通厂商可把 `audio/eac3-joc` 降级交给普通 E-AC3 decoder，变成 2D；部分 Pixel 的 E-AC3 decoder 连这种降级也会失败，因此 Google 设备应返回空 alternative 列表。

建议动作：只移植 `supportsEac3JocFallbackDecoding()` 的 Google/非 Google 判断及其 `MediaCodecUtilTest` 两个 case，不整体重放 `1066f642...`。收益是避免 Pixel 选择已知不可用 codec；风险是 Google 设备没有原生 JOC decoder 时平台 renderer 会更早拒绝，必须验证 `CompatFfmpegAudioRenderer` PCM 软解是否仍能接管，而不是只看 codec 查询结果。

最低测试：

1. `MediaCodecUtilTest.getAlternativeCodecMimeTypes_withEac3JocFormatOnGoogleDevice_returnsEmpty`。
2. `MediaCodecUtilTest.getAlternativeCodecMimeTypes_withEac3JocFormatOnNonGoogleDevice_returnsEac3`。
3. App 侧 Google/非 Google、原生 JOC、普通 E-AC3 fallback、FFmpeg PCM fallback 的 renderer 选择和日志。

建议归入阶段 **A3-1a**，独立于 DTS 和 TrueHD 改动，可单独回滚。

### 9.3 DTS-CD 14-bit frame size 修复

`d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` 的 WAV 探测、第二音轨和 `DtsReader` 接入在当前 fork 已经存在；真正缺失的是 `DtsUtil.getDtsFrameSize()` 对 14-bit word 的整数换算：

```java
// 当前 fork 的旧公式
fsize * 16 / 14

// 上游修复
(fsize * C.BITS_PER_BYTE / 14) * 2
```

14-bit DTS-CD 每个 14-bit word 存在 16-bit 容器中，必须先向下取完整 word 数，再换算物理字节数。`FSIZE + 1 = 3585` 时旧公式得到 `4097`，正确值为 `4096`；旧值会让下一个 DTS-CD sync word 偏移一个字节，导致帧边界和后续音频损坏。该 hunk 也影响 raw DTS/TS 中的 14-bit core，不能只验证 WAV。

上游同提交没有对应的 14-bit 单元测试，建议手工移植 hunk 并新增：

- 14-bit big-endian frame size，覆盖整除和非整除边界；
- 14-bit little-endian frame size；
- DTS-CD WAV 双音轨/连续帧；
- raw DTS extractor 和 TS/PES 跨边界；
- 16-bit DTS frame size 不变的回归。

建议归入 **A3-1b**。收益明确、代码面很小；回滚只涉及 `DtsUtil` 和新增测试，不应连带 WAV extractor 或整个 A3 音频 API。

### 9.4 `1cc8573...`：真实新增，拆成三个实施阶段

上游 `1cc8573cab9e2453e7917aff1b8945482c8b2190` 实际修改 9 个文件，新增约 802 行、删除约 125 行：

```text
libraries/common/src/main/java/androidx/media3/common/MimeTypes.java
libraries/extractor/src/main/java/androidx/media3/extractor/Ac3Util.java
libraries/extractor/src/main/java/androidx/media3/extractor/TrueHdSampleRechunker.java
libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java
libraries/extractor/src/main/java/androidx/media3/extractor/mp4/FragmentedMp4Extractor.java
libraries/extractor/src/main/java/androidx/media3/extractor/mp4/Mp4Extractor.java
libraries/extractor/src/main/java/androidx/media3/extractor/ts/Ac3Extractor.java
libraries/extractor/src/main/java/androidx/media3/extractor/ts/Ac3Reader.java
libraries/extractor/src/main/java/androidx/media3/extractor/ts/TrueHdReader.java
```

不能整体 cherry-pick，原因是它同时改变 sample format 延迟、音频时钟样本数、Matroska/MP4/fMP4 生命周期和 TrueHD channel map；当前本地 `media3-dolby-vision-matroska.patch` 还依赖旧的 `trueHdAtmosAnalysisComplete` 状态名。

#### A3-2a：E-AC3 dependent substream、channel map、sample count、JOC

涉及 `Ac3Util.java`、`Ac3Reader.java`、`Ac3Extractor.java` 及 `DefaultAudioSink` 使用的样本数解析：

- `SyncFrameInfo` 新增 `substreamId`、`dependentChannelCount`、`dependentChannelMap`、`channelMap`。
- 解析 E-AC3 dependent substream 的 channel map，把基础声道与依赖声道位置合并，动态更新完整 channel count（例如 5.1 + dependent 7.1 轨道）。
- `Ac3Reader` 将同一 access unit 的 independent/dependent substream 合并为一个 sample，避免 dependent frame 被错误单独提交。
- `parseAc3SyncframeAudioSampleCount()` 从“单个 E-AC3 frame”扩展为“一个 access unit 内按 substream timeline 累加”，直接影响 `DefaultAudioSink` encoded frame count、播放位置和 A/V sync。
- `updateFormatWithEac3JocInfo()` 从首个样本识别 JOC，修复容器只宣告普通 E-AC3 但 bitstream 实际带 JOC 的情况。

收益是正确的 7.1/Atmos 声道描述、样本边界和音频时钟；风险是 `DefaultAudioSink` passthrough/offload、平台 decoder 和 App `PlaybackAnalyticsListener` 都会看到不同的 channel count/sample count。必须验证编码帧数、AudioTrack channel mask、offload 进度和 FFmpeg PCM fallback，不能只跑 extractor dump。

#### A3-2b：TrueHD header/channel map、Atmos marker、rechunker、EOF/seek

涉及 `TrueHdReader.java`、`TrueHdSampleRechunker.java`、`MimeTypes.java`：

- TrueHD Atmos codec marker 统一为 `MimeTypes.CODEC_TRUEHD_ATMOS = "truehd-atmos"`，并让 `MimeTypes.getMediaMimeType()` 识别 `mlpa`/`truehd`。
- `TrueHdReader` 修正 major sync header 的 channel map 位偏移：上游从 `[9,10,11]` 读取，当前旧版从 `[8,9,10]` 读取；旧读法可能把 TrueHD 5.1/7.1 报成错误声道数。
- `TrueHdSampleRechunker.startSample()` 用 `try/finally` 保证 peek position 恢复，读取完整 22 字节后才锁定 syncframe/Atmos，避免短尾或部分 header 把样本误判为 Atmos。
- fMP4 TrueHD rechunk 增加 pending metadata 的 EOF flush、seek reset、加密 sample 边界处理；`Ac3Extractor` EOF 时 flush reader。

收益是 TrueHD/Atmos 的 channel count、codec marker、sample duration 和尾部行为更稳定；风险是 marker 改变可能影响 App 的音频标签、passthrough 编码映射和 `CodecCapabilityInspector`，必须同步核对 `MimeTypes`、`PlaybackAnalyticsListener` 和 MPV/Exo UI 显示。加密 TrueHD 不能使用明文 rechunk 路径，必须保留上游的加密分支。

#### A3-2c：Matroska、普通 MP4、fragmented MP4 接线

涉及 `MatroskaExtractor.java`、`Mp4Extractor.java`、`FragmentedMp4Extractor.java`：

- DTS、E-AC3、TrueHD 统一使用 `waitingForSampleFormat`，首个样本分析完成前延迟输出 format。
- Matroska 在首个 E-AC3/TrueHD sample 后再更新 JOC marker、channel count 和 format；带加密或 stripped bytes 的 track 不应无条件 peek。
- 普通 MP4 将 TrueHD 加入 sample format analysis；首样本识别 Atmos 后再输出 pending format。
- fragmented MP4 在 sample 起始分析 TrueHD、在 fragment/EOF 输出 pending metadata、seek 时重置 rechunker，并对加密样本跳过明文重分块。

本地 `media3-dolby-vision-matroska.patch` 会与 Matroska 部分冲突：当前 patch 依赖 `trueHdAtmosAnalysisComplete`，上游统一为 `waitingForSampleFormat`。实施时必须保留本地 Dolby Vision BlockAdditional RPU 追加逻辑和 `pendingDolbyVisionBlockAdditionalData` 的 seek reset，不能为了接入 TrueHD 重构而丢失 DV 功能。建议把 A3-2c 单独作为容器接线提交，不与 A3-2a 的 Ac3 parser 混成一个回滚单元。

### 9.5 `c2dd4bec...`：generic TS `0x82` DTS probe（条件推荐）

上游 `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` 新增 `DtsProbeReader.java`（约 269 行），并删除 `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS`：

- 仅对裸 `TS_STREAM_TYPE_HDMV_DTS`（`0x82`）先 probe；明确的 `HDMV_DTS_AUTO`、DTS、DTS-HD、DTS-UHD 仍直接进入 `DtsReader`。
- 最多缓存 64 KiB，要求连续识别 4 个 DTS core frame；允许中间 DTS-HD extension frame，且 sample rate 必须一致。
- 成功后按原 PES timestamp/flags 把缓存 replay 给 `DtsReader`；失败后将 track 暴露为 `application/octet-stream`，避免把任意 `0x82` 私有流误报成 DTS。

当前 fork 的 `TsExtractor` 已在识别 PMT `HDMV` registration 后，把真正 Blu-ray `0x82` 映射为虚拟 `0x103 HDMV_DTS_AUTO`；`BdmvTsPayloadReaderFactory` 也会根据 CLPI 映射。因此 probe 只作用于 generic TS 的歧义 `0x82`，不会给已确认 Blu-ray DTS 增加启动等待。

采用时必须联动：

1. 删除 App `MediaSourceFactory` 对 `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS` 的 import 和 `.setTsExtractorFlags(...)` 调用。
2. 调整 `M2tsExtractor`、`BdmvTsPayloadReaderFactory`，不再传已删除 flag；已确认 disc 路径继续保留 `FLAG_IGNORE_SPLICE_INFO_STREAM`。
3. 修正/重做 `TsExtractorTest`，覆盖 generic `0x82` DTS、generic 非 DTS、HDMV DTS、普通 DTS-HD、跨 PES 边界和 probe 失败后的 `application/octet-stream`。
4. 重新检查注释：共享类型的是 SCTE subtitle；SCTE-35 不是 `0x86` 这条歧义路径，不能因删除 flag 而回归。

收益是 generic TS 的误识别风险下降；代价是起播要缓存最多 64 KiB/几个 frame，失败路径不再尝试 DTS 解析，仍不提供 SCTE-27 解析。建议归入 **A3-3**，只有项目确有 generic TS `0x82` 输入时才实施；Blu-ray/M2TS 回归通过后再决定是否启用。

### 9.6 A3 实施顺序、回滚边界与构建产物

按用户要求，A3 属于 Exo 阶段，应在任何 MPV native 更新前实施和验收。建议顺序：

1. **A3-0**：登记并跳过已包含提交，无代码变更。
2. **A3-1a**：Google/Pixel E-AC3 JOC fallback guard；单独生成 Media3 AAR 和 App 测试包。
3. **A3-1b**：14-bit DTS frame size 修复及 unit test；与 A3-1a 分开发布，便于定位 parser 回归。
4. **A3-2a**：E-AC3 dependent substream/channel map/sample count/JOC；先完成 parser 和 extractor tests，再接入 App passthrough/offload 回归。
5. **A3-2b**：TrueHD header/channel map、Atmos marker、rechunker、EOF/seek；确认 `MimeTypes` 和 App 标签后再发布。
6. **A3-2c**：Matroska/MP4/fMP4 接线，适配本地 DV patch；这是容器生命周期阶段，单独验收。
7. **A3-3**：generic TS `0x82` probe 和 flag/API 清理；仅在输入需求成立时实施。

每一步都应保留可独立替换的 Media3 AAR 版本和 App APK。A3-2a、A3-2b、A3-2c 不应合成一个无法定位的“大音频回滚单元”；A3-3 还涉及 App extractor factory，必须单独回滚。任何步骤都不触碰 FFmpeg/MPV `.so`，也不改变 `third_party/fongmi-repositories-lock.json`。

### 9.7 A3 验证矩阵与最低自动化集合

上游 `1066f642...` 有 `MediaCodecUtilTest` 的 Google/非 Google JOC case；`c2dd4bec...` 只有现有 DTS TS 行为测试改为不传 flag；`d500eb27...` 和 `1cc8573...` 没有覆盖上述关键新增语义的同提交测试。因此本项目最低集合必须补齐：

| 领域 | 最低测试/样片 | 通过标准 |
| --- | --- | --- |
| E-AC3 fallback | Google/非 Google；原生 JOC、普通 E-AC3 降级、FFmpeg PCM | Pixel 不选择已知失败的普通 E-AC3 fallback；非 Google 仍可 2D 降级；renderer 日志与实际路径一致 |
| E-AC3 dependent | 5.1、dependent 7.1、多个 substream、channel map 缺失/变化 | 一个 access unit 输出一个 sample；channel count/mask 正确；format 变化不重复刷屏 |
| E-AC3 时钟 | independent/dependent frame 混排、不同 numblkscod、跨 PES/EOF | `DefaultAudioSink` encoded frame/position 与 PCM 参考一致，A/V sync 无漂移 |
| E-AC3 JOC 识别 | TS、MKV、普通 MP4、fMP4，容器标 E-AC3 但首样本带 JOC | 首样本后变为 `audio/eac3-joc`，加密/短尾不越界 |
| TrueHD | 5.1/7.1、Atmos、MKV/MP4/fMP4/M2TS、22-byte 短尾 | channel count、`truehd-atmos` marker、sample duration 正确；短尾不误判 |
| TrueHD 生命周期 | EOF、seek、换集、fragment 边界、加密 sample | pending metadata 不丢、不重复；seek 后重新分析；加密路径不明文 rechunk |
| DTS-CD | 14-bit BE/LE、`FSIZE+1=3585`、WAV/raw DTS/TS | 下一个 sync word 对齐；16-bit DTS 行为不变 |
| DTS variants | core/coreless、DTS:X/IMAX marker、跨 PES | MIME/profile/codec marker 与当前 fork 既有 UI/AudioTrack 映射一致 |
| generic TS `0x82` | DTS、非 DTS、HDMV remap、4-frame 不足、64 KiB 上限 | 只有连续 4 个 core 且采样率一致才分类 DTS；失败暴露 octet-stream；Blu-ray 不增加延迟 |
| App 输出 | passthrough on/off、offload on/off、平台 decoder、FFmpeg PCM | `PlaybackAnalyticsListener` 的 encoding/sampleRate/channelMask 与实际 renderer 一致；换集/退出状态清零 |

至少运行当前 fork 的 `Ac3UtilTest`、`DtsUtilTest`、`TsExtractorTest`、Matroska/MP4/fMP4 extractor tests、`DefaultAudioSinkTest`，并新增针对上述新语义的参数化 case。已有 DTS:X marker 测试不能替代 14-bit、dependent-channel 和 TrueHD channel-map 测试。

### 9.8 A3 决策摘要

| 项目 | 建议 | 实施阶段 | 主要理由 |
| --- | --- | --- | --- |
| `98d7e951...`、`eb4aa3e...`、`908b27d7...`、`d9ffc31a...`、`ba3af524...` | 跳过 | A3-0 | 当前 fork 已有等价实现 |
| `1066f642...` 主体 | 跳过整体提交 | A3-0 | 多 MIME/DTS fallback 已在 fork |
| `1066f642...` Google/Pixel guard | 合并 | A3-1a | 低风险、可独立验证，避免 Pixel 错选 decoder |
| `d500eb27...` 14-bit hunk | 合并 | A3-1b | 明确修复帧边界，影响 raw DTS/WAV/TS |
| `1cc8573...` E-AC3 parser/sample count | 条件合并 | A3-2a | 收益大，但会改变 channel count、encoded clock 和 sample grouping |
| `1cc8573...` TrueHD/rechunker | 条件合并 | A3-2b | 修复 channel map/Atmos/EOF/seek，但需核对 marker 和加密边界 |
| `1cc8573...` 容器接线 | 条件合并 | A3-2c | 与本地 DV Matroska patch 有明确冲突，必须手工适配 |
| `c2dd4bec...` generic TS probe | 条件合并 | A3-3 | 只在 generic `0x82` 输入需求成立时值得承担起播缓存与 API 清理 |
| A3 整体一次性 cherry-pick | 不推荐 | - | 回滚面过大，难以区分 parser、时钟、容器和 App renderer 回归 |

本检查点完成 A3 深审并已落盘。下一检查点进入 A4 字幕组；A4 将优先处理当前 WebHome/TV 字幕消费面直接可见的 TTML、ASS、PGS、DVB 改动，并继续按独立 AAR/回滚边界拆分。

## 检查点 10：2026-08-21 A4 字幕组首轮身份映射与实施边界

### 10.1 审计范围和当前工作树约束

A4 继续审阅 Media3 `media` 增量中与字幕、字幕封装和位图 cue 生命周期有关的提交。当前 fork HEAD 为 `e3e922d5c01bc0b564849940fe589daf37360d15`，其祖先包含此前登记的本地修订；`third_party/sources/media` 有用户/构建准备产生的未提交改动，以下结论只基于已提交树和只读 diff，不清理或覆盖这些改动。

本组必须同时看三层边界：

1. Media3 extractor/parser 是否改变 `Cue`、`SubtitleParser`、MIME 或样本时间线；
2. 本项目 `ExoUtil`、`SubtitleDialog`、`PlayerManager` 和 `DolbyVisionP81ExtractorsFactory` 是否依赖旧 API/旧字幕 MIME；
3. 当前 `media3-dolby-vision-matroska.patch`、`parseSubtitlesDuringExtraction=false` 和 `textTrackTranscodingEnabled=false` 是否要求手工拆 patch。

### 10.2 上游 commit 与当前 fork 对应关系

| 上游 commit | 当前 fork 对应 commit | 初步动作 | 备注 |
| --- | --- | --- | --- |
| `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` | `63531ddcd508b646e0cf515df3bb6caf4835120e` | 条件移植 | 字幕 charset pipeline 主体已有；上游还涉及 TTML XML encoding、精确长度、extraction factory 和 HLS/SS/chunk 接线 |
| `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b` | `56fd27d919504bfebd78172acb99cf7e3bc8f490` | 条件移植 | 当前 fork 已有字号/位置/位图缩放 API；上游 UI/默认值不能直接覆盖 App 控制逻辑 |
| `6794d75b7a39db42dcfcab18c915f0da165515b5` | `9d7ea02aae18e03db0407e2146b50908acece81c` | 重点深审 | 上游新增 Cue collision、ASS reverse collision、layer/z-index 和 margin；fork 已有另一套 stacking 算法 |
| `ccc11523d57c3fd430c009b228c674a3195c9fdc` | `da1796da64acf3bd08aca4c3beff5c1ed09f9ccf` | 跳过 | patch-id 精确等价 |
| `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | `0aae318bded9b6790823d7ed7c9d1261f2af9344` | 条件移植 | PGS parser 改动面大，需样片和位图 cue 对照 |
| `d7083781e629ad1c4683a687261374065fb38925` | `66762a253b12652f3c28420ed0f4f7507a1451cd` | 移回 A2 | 实际是 AV3A TS payload reader，不属于字幕组 |
| `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | `0351ac61d75ce71ffe062be9b57adad09eadc617` | 条件移植 | PGS-TS reader，需与 PGS parser、TS 时间戳和 bitmap 生命周期联合验证 |
| `e8573d8c2ced07096c368d7ec3a40bc2e790d203` | `cccc786e5de5f861705adaba1b6ab760f484f2ee` | 跳过 | patch-id 精确等价 |
| `ba27f889922a281162864a1260e7cb4e73ca0ecf` | `6895be9ae9b777bf8108df7f38d7c32d86fbd222` | 条件移植 | bitmap cue 延迟清除/生命周期，需核对 detach、连续空 cue 和换源 |
| `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 无 | 重点候选 | TTML layout、spacing、proportional regions 和 custom spans 是当前明确的新功能 |
| `7feb08018a6e159330293de4878ebc3c9df2ca86` | `b14c2dcc5899067f93496a82c200cfc719485da1` | 跳过 | patch-id 精确等价，压缩 Matroska 文本处理已在 fork |
| `db13d7672f9bca525878292a54ae5e69c021f4c9` | `f24f5bef688ac62794014a9c275c49306aa27599` | 跳过 | audio/text offset API 已存在，App `OffsetPanel`/`PlayerManager` 正在使用 |

### 10.3 首轮归组和暂定实施阶段

为避免把字幕 parser、位图生命周期和 App UI 绑成一个不可回滚的大提交，A4 暂按下列阶段推进：

- **A4-0：重复项和归类清理**。登记 `ccc11523...`、`e8573d8...`、`7feb080...`、`db13d767...` 的完整上游 hash，代码动作标记为跳过；把 `d7083781...` 移回 A2/AV3A 清单。
- **A4-1：字幕 charset/extraction 接线**。以 `d82fb7b9...` 为来源，先拆 TTML XML encoding/长度修复，再单独评估 `DefaultSubtitleParserFactory.createForExtraction()` 及 HLS/SS/chunk extractor 接线。保留当前位图字幕 raw MIME 路径，不直接打开全量 extraction transcoding。
- **A4-2：Cue 控制和 ASS 碰撞**。以 `92b1570a...`、`6794d75b...` 为来源，对照现有 `SubtitleView`、`ExoUtil` 和 fork 的 alignment/start-time stacking；优先引入数据模型和解析规则，再决定是否启用 Canvas collision avoidance。
- **A4-3：PGS/PGS-TS parser 与 bitmap 生命周期**。联合 `aaddc2b9...`、`1b112bd1...`、`ba27f889...`，按 parser、TS reader、cue 清除拆成可独立回滚的子步骤。
- **A4-4：TTML layout/spans**。单独深审无 fork 对应的 `3c2cbe8...`，确认 `Cue` 自定义 span 能否穿过 Media3 `SubtitleView`、WebView/Canvas 和 WebHome 字幕消费；不与 PGS 位图改动混合。

### 10.4 当前初步判断

`d82fb7b9...` 不是可直接 cherry-pick 的纯 bug fix：当前 fork 有意保留 `parseSubtitlesDuringExtraction=false` 和 `textTrackTranscodingEnabled=false`，以免 PGS/DVB 等位图字幕被转换成文本 MIME。应先选择性移植 TTML 声明编码和精确长度修复，再用外部字幕、嵌入字幕和 PGS/DVB 样片确认 extraction factory 接线。

`92b1570a...` 的控制 API 与 App 已有的自定义字号/位置/位图缩放存在重叠；必须以 `ExoUtil` 和 `SubtitleDialog` 的现有调用为准，避免同一设置同时被 `SubtitleView` 默认值和 App policy 改写。

`6794d75b...` 是 A4 当前最重要的行为差异。上游的 `Cue.collisionAvoidance`、ASS `Collisions: Reverse`、layer/z-index 排序和 margin 解析，可能修复多行 ASS 重叠，也可能与 fork 当前按 alignment/start time 的 stacking 产生双重位移。需要先做 golden cue 序列和屏幕截图对照，再决定只合并 parser 字段还是连 renderer collision 算法一起合并。

`aaddc2b9...`、`1b112bd1...` 和 `ba27f889...` 不能只用单元测试判断。PGS palette/object 分片、TS/PES 跨边界、连续空 cue、detach/release 和换源时机都会影响 Android `SubtitleView` 的 bitmap 引用；这些提交必须在同一组 PGS/BDMV 样片上联合审阅。

`3c2cbe8...` 是当前最明确的新增功能候选，但收益依赖真实 TTML 来源。若 App 只把 TTML 转成 WebHome 歌词或外部文本而不使用 Media3 layout，合并后收益有限；若需要 karaoke、比例区域、字距/行距和 span 样式，则应作为独立 A4-4 阶段实施。

本检查点只完成身份映射和实施边界；下一步深审 `6794d75b...` 的 ASS collision/layer 行为，再审 PGS/DVB 生命周期，最后审 `3c2cbe8...` TTML layout/spans。每完成一组即追加新的检查点，保留完整 commit ID、对应 fork hash、验证样片和回滚边界。

## 检查点 11：2026-08-21 A4-2 ASS collision/layer 深审

### 11.1 `6794d75b...` 与 fork `9d7ea02a...` 的实际差异

上游完整 commit：`6794d75b7a39db42dcfcab18c915f0da165515b5`（父提交 `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`）。当前 fork 对应：`9d7ea02aae18e03db0407e2146b50908acece81c`（父提交 `56fd27d919504bfebd78172acb99cf7e3bc8f490`）。两者同属 ASS 改进，但不是 patch-id 等价：

- 上游改动 `Cue.java`、`SsaDialogueFormat.java`、`SsaDialogueInfo.java`、`SsaParser.java`、`SsaStyle.java`、`CanvasSubtitleOutput.java`、`SubtitlePainter.java`、`SubtitleView.java`、`PlayerView.java`；fork 对应提交只改了四个 SSA extractor 文件。
- fork 已有 MarginL/MarginR/MarginV 解析、`SsaDialogueInfo`、按 alignment/start time 的 parser-side stacking 和 drawing-mode 过滤，但没有上游新增的 UI/render 层碰撞处理。
- 当前 fork 的 `Cue` 已有 `zIndex`（由既有 `4db2ca63351a4abaf0daf3c1a65913b846215ab9`、`55fe0481b9429d3a12f6b42ed1945cd12cc88c9c` 等提交提供），但没有 `collisionAvoidance` 字段、builder、equals/hashCode 或 Bundle 序列化键。

因此不能把上游 `6794d75b...` 整体 cherry-pick 到 fork：它会覆盖/重复 fork 的 stacking 逻辑，并且当前 `media` 工作树已有未提交 UI/Extractor 改动。

### 11.2 上游实现的行为链

上游新增的 `Cue.collisionAvoidance` 有三个值：`NONE`、`UP`、`DOWN`。SSA parser 按最终 alignment 和是否存在绝对 `\\pos`/`\\move` 位置设置：

- bottom-left/center/right 且没有绝对位置：`UP`；
- top-left/center/right 且没有绝对位置：`DOWN`；
- middle alignment、未知 alignment 或绝对位置：`NONE`。

`SsaParser` 同时读取 `[Script Info]` 的 `Collisions: Reverse`。解析后的 dialogue 先按 `layer` 稳定排序；Reverse 模式再对每个 layer 内的事件倒序，使后出现的 cue 保持作者位置、旧 cue 被避让。`SsaStyle`/`SsaDialogueFormat` 还把 style/dialogue 的 MarginL/R/V 保留为独立值，并用 `hasMargin()` 区分“未声明”与显式零值，避免无 margin 时错误覆盖默认 5% 位置。

上游 `CanvasSubtitleOutput` 在所有 painter 完成 layout 后取得文本 cue bounds，按 `zIndex` 分组，逐 cue 计算最小垂直偏移；`UP` 从底部向上查找，`DOWN` 从顶部向下查找，碰撞只在同一 z-index 层内避让。`SubtitlePainter` 增加 collision bounds 和 vertical offset，位图 cue 不参加该算法。`SubtitleView.setCues()` 另外按 `zIndex` 排序；`CueGroup` 在 fork 中已经按 z-index 排序，重复排序本身是低风险。

`Cue.collisionAvoidance` 还必须进入 `Cue.equals/hashCode`、`buildUpon()`、`toSerializableBundle()/toBinderBasedBundle()` 和 `fromBundle()`。缺少 Bundle 键会在 MediaSession/跨进程 Cue 传递时静默丢失碰撞策略，不能只移植 builder 字段。

`PlayerView` 的上游 hunk 在 `onLayout()` 中把 `exo_content_frame` 相对坐标转换到 `exo_subtitles`，调用 `SubtitleView.setVideoViewport()`。这对当前 App 很重要：播放视图可能按 16:9/自定义 resize 留黑边，字幕应以实际视频 viewport 计算碰撞和比例位置，而不是整个 `PlayerView`。当前 fork 的 `exo_player_view.xml` 还包含 `DanmakuView`，实施时需手工保留该层并适配坐标同步。

### 11.3 与当前 fork 自定义 stacking 的冲突

当前 `9d7ea02a...` 的 `SsaParser.processCues()` 会按 alignment 保存活动 cue 的估算占用高度，直接增大后续 cue 的 `MarginV`/`line`。这与上游 Canvas 运行时避让是两套机制：若同时启用，底部/顶部多行 ASS 会先被 parser 上移，再被 Canvas 再次上移，造成字幕逐渐远离视频边缘；字体大小、视频 viewport 或窗口尺寸变化时，parser 的像素估算也不会重新布局。

建议不要把两套 stacking 叠加。可实施拆分如下：

1. **A4-2a（低风险数据模型）**：移植 `Cue.collisionAvoidance` 及完整 Bundle/equals/hashCode 支持；移植 `SsaParser` 的 `Collisions: Reverse`、`toCollisionAvoidance()`、drawing-mode 检查、`hasMargin()` 和上游 margin 位置语义。保留现有 `zIndex`、字体/span、App 的 `SubtitleView` 控制 API。此步骤可以先让 extractor golden cue 暴露策略字段，但若未关闭旧 `applyStacking()`，不能进入用户包。
2. **A4-2b（Canvas renderer）**：移植 `CanvasSubtitleOutput`、`SubtitlePainter` 的 collision bounds/offset 算法，`SubtitleView.setCues()` 的 z-index 排序，以及 `PlayerView.onLayout()` 的 video viewport 同步；同时删除或 feature-gate fork `applyStacking()`，使位置只由 authored margin/position 加运行时 collision 决定。该步骤必须与 2a 同一 AAR 验收。
3. **A4-2c（回归与开关）**：对 `Collisions: Normal/Reverse`、不同 layer、top/bottom/middle、绝对 `\\pos`、多行、SSA 无 PlayRes、PGS 位图混排做截图和 cue 序列回归。若项目不接受运行时位置变化，可只合并 2a 的字段/解析而不设置 `UP/DOWN`，但那样对用户可见收益很小。

### 11.4 利弊与当前建议

收益：ASS 多行/重叠 cue 可在实际视频 viewport 内动态避让；`Collisions: Reverse` 和 layer/z-index 语义更接近 ASS 规范；屏幕尺寸、字体缩放、横竖屏变化后仍能重新计算；绝对定位 cue 不被强行移动。`PlayerView` viewport 同步也会改善当前宽屏/留黑边场景的字幕位置。

代价和风险：改动横跨 common、extractor、ui 三个 Media3 模块；`Cue` Bundle/API 需要完整兼容；Canvas 和 WebView 的行为不一致（WebView 仅使用 CSS z-index，不执行 Canvas collision）；删除当前 parser-side stacking 可能改变现有用户字幕位置；同一 `SubtitleView` 还承载 PGS/DVB 位图，但位图不参与避让。当前 App 默认使用 Canvas，MPV/IJK 原生字幕链不受这组 Exo 改动影响。

当前建议：**条件合并，优先 A4-2a + A4-2b，禁止整提交 cherry-pick**。若没有真实重叠 ASS/SSA 样片或用户明确需要 `Collisions: Reverse`，可延后 2b，仅保留 `9d7ea02a...` 已有 margin/layer 修复。若实施，必须把 `6794d75b7a39db42dcfcab18c915f0da165515b5` 作为来源 hash 记录在 AAR/变更日志中，并以独立 A4-2 回滚单元发布。

### 11.5 A4-2 最低验证集合

| 场景 | 关键断言 |
| --- | --- |
| 两个 bottom cue、Normal | layer/输入顺序稳定；较新的 cue 按策略向上，旧 cue 仍可见 |
| 两个 bottom cue、Reverse | 后出现 cue 保持 authored line，先出现 cue 被移开 |
| 两个 top cue | 只向下避让，不穿过视频 viewport 底部 |
| middle/unknown alignment | `collisionAvoidance=NONE`，不被自动移动 |
| `\\pos`/`\\move` | `collisionAvoidance=NONE`，保持绝对坐标 |
| 不同 `zIndex` | 不因不同 layer 产生错误跨层避让；绘制顺序符合 z-index |
| MarginL/R/V | 未声明、显式 0、非零值分别得到预期 position/size/line |
| PlayRes + 16:9 letterbox | `SubtitleView.videoViewport` 与 content frame 一致，字幕不按整屏错误定位 |
| PGS/DVB bitmap 混排 | bitmap 仍按原尺寸/延迟清除显示，不被文本 collision 算法移动 |
| Cue Bundle/Session | 序列化后 `zIndex` 与 `collisionAvoidance` 均保留 |

建议至少补 `SsaParserTest` 的 Reverse/margin/drawing/strategy case、`CanvasSubtitleOutputTest.resolveCueCollisionOffsets` 参数化 case、`CueTest` Bundle/equals case，并运行当前 App 手机/电视两种 `PlayerView` 截图回归。A4-2 不改变 MPV native、FFmpeg 或 libplacebo。

本检查点完成 ASS 深审。下一步进入 A4-3，联合审阅 `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`、`1b112bd1375c7a796cbde58d4c90226c7fc1947a`、`ba27f889922a281162864a1260e7cb4e73ca0ecf` 的 PGS parser、PGS-TS reader 和 bitmap cue 生命周期。

## 检查点 12：2026-08-21 A4-3 PGS parser/TS reader 首轮差异（先行落盘）

### 12.1 PGS parser 身份与现状

上游完整提交：`aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`（`Improve PGS parsing`）。当前 fork 对应提交：`0aae318bded9b6790823d7ed7c9d1261f2af9344`。两边都把旧的单体 PGS 解析拆成 `PgsSupSegment`、`PgsSupTiming`、`PgsEpochState`、palette/object/window/composition 等状态对象，并支持跨 sample 保留 epoch、palette/object cache、WDS/ODS 分片、多 composition object、palette update、clear display set 和显式 reset。它们不是 patch-id 等价，不能按“已有同名提交”直接跳过。

目前已确认的行为差异：

| 主题 | 上游 `aaddc2b9...` | fork `0aae318b...` | 影响 |
| --- | --- | --- | --- |
| composition object crop | 保存 crop rectangle，并在构造 bitmap 时执行裁剪 | 读取后仅跳过 8 字节，丢弃 crop 信息 | 带 crop 的 SUP 可能显示过大的对象或错误透明区域 |
| 防御性尺寸 | 有 `MAX_BITMAP_DIMENSION = 4096` 等上限/越界保护 | 主要依赖 bitmap 宽高不超过 presentation plane 的早期检查 | 畸形 ODS/超大尺寸可能触发内存或坐标风险 |
| 显式 bitmap 高度 | `Cue.setBitmapHeight(bitmapHeight / planeHeight)` | 没有 `bitmapHeight`，只按 bitmap 宽度和自身宽高比推导高度 | 非方形 plane、crop 或留黑边时 X/Y 比例可能不一致 |
| presentation-plane 边界 | build 阶段检查 bitmap 是否越过 plane，再决定裁剪/拒绝 | 较早按 `bitmapWidth/Height <= planeWidth/Height` 检查 | 合法 crop 与畸形对象的处理边界不同 |

两边共同的 raw SUP 解析能力不能替代上述差异的验证。仓库没有专门的 `PgsParserTest` 或 raw `.sup` 资产；现有媒体只包含 `libraries/test_data/src/test/assets/media/mkv/sample_with_pgs_subtitles.mkv` 及 playback dump。因此在考虑合并前必须新增最小 raw SUP 夹具：正常 display set、crop object、palette update、多个 object、clear display set、跨 sample 分片、畸形 RLE、超大/越界尺寸和 epoch reset。

### 12.2 PGS parser 的可实施拆分

不建议整体 cherry-pick `aaddc2b9...`。建议将上游语义拆成以下独立变更，并在同一组 PGS 样片上验收：

1. **A4-3a：crop 与显式 bitmap 高度**。从 `aaddc2b9...` 选择性移植 composition crop、`Cue.bitmapHeight` 的完整构造/序列化接线，以及 presentation-plane 比例计算。该步骤依赖当前 fork 已有 `Cue` 扩展，需同步检查 `equals/hashCode`、Bundle/Binder 和 WebView/Canvas 对 bitmapHeight 的忽略行为。
2. **A4-3b：尺寸与 RLE 防御**。移植 4096 上限、对象/plane 越界检查和异常 RLE 的拒绝路径；不能只加一个常量而保留会先分配大 bitmap 的路径。验证失败时应丢弃当前 object/display set，不污染下一个 epoch。
3. **A4-3c：App 可达性接线**。`PlayerHelper.getSubtitleMimeType()` 当前不识别 `.sup`，未知扩展名回退为 SubRip。若要让 raw SUP 真正可被外部字幕选择，必须增加 `.sup -> MimeTypes.APPLICATION_PGS`，并验证外部数据源、`SingleSampleMediaSource`/`SubtitleParser` 链路；只合并 parser 不会自动产生用户可见收益。

`bitmapHeight` 不应脱离 A4-2b 的实际视频 viewport 同步单独宣布完成：若字幕仍按整个 `PlayerView` 布局，显式 plane 比例在 16:9 留黑边场景可能反而暴露 X/Y 偏差；应至少在同一验收矩阵中覆盖 `PlayerView` content frame、Canvas、WebView 和 PGS/文本混排。

### 12.3 PGS TS reader 身份与边界

上游完整提交：`1b112bd1375c7a796cbde58d4c90226c7fc1947a`（`Add PGS TS payload reader`）。当前 fork 对应提交：`0351ac61d75ce71ffe062be9b57adad09eadc617`。两边都新增 `TsExtractor.TS_STREAM_TYPE_PGS = 0x90`、`DefaultTsPayloadReaderFactory` 接线和 `PgsReader`，并把完整 PGS display set 聚合成一个 `APPLICATION_PGS` sample；section header/size/body 可以跨 TS packet/PES 分片。

关键差异如下：

| 主题 | 上游 `1b112bd...` | fork `0351ac6...` | 风险/收益 |
| --- | --- | --- | --- |
| `packetStarted()` | 每个 PES 都允许继续写入；保留最近非空 PTS | 只有 `FLAG_DATA_ALIGNMENT_INDICATOR` 才启动 sample | 上游更宽容地接收无 alignment 的合法分片；fork 可能错过一个 display set 的后续起点 |
| display set 提交后 | `resetSampleState()` 后仍保持 `writingSample` | 提交后把 `writingSample=false` | 同一 PES 中后续 display set、下一无 alignment PES 的可达性不同 |
| PTS 状态 | `lastPacketTimeUs` 只在 PTS 非 unset 时更新，后续分片沿用最近时间 | `packetTimeUs` 直接取当前 PES，未提供 PTS 时可能覆盖为 unset | 首 PES 无 PTS、后续 PES 有 PTS、跨 PES display set 的时间戳需要单独验证 |
| `seek()`/状态 | section state、sample state、最近 PTS 一并清理 | 同样清理，但 `writingSample` 的启动条件不同 | seek 后首个无 alignment PES 行为不一致 |

当前 BDMV 路径可以到达该 reader：`BdmvTsPayloadReaderFactory` 最终 delegate 给 `DefaultTsPayloadReaderFactory`，并会把 CLPI language 传入；因此这不是 generic TS 的不可达死代码。需要同时验证 generic TS `0x90`、M2TS/Blu-ray、CLPI 语言和当前 `Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE` 行为。

### 12.4 PGS TS reader 的实施与验证边界

建议拆为 **A4-3d**，不要把 reader 与 parser 或 UI 延迟清除绑成一个提交。最低测试矩阵：

| 场景 | 必须观察 |
| --- | --- |
| section type/header/body 横跨 TS packet | 不丢字节，下一 section 状态正确 |
| 一个 display set 横跨多个 PES | 有/无 data-alignment 均能完成一个 sample，PTS 取值稳定 |
| 一个 PES 含多个 display set | 每个 display set 都产生独立 sample，不吞掉下一个起点 |
| 首 PES 无 PTS、后续 PES 有 PTS | 不产生 `TIME_UNSET` 错误时间线；seek 后重新建立时间 |
| EOF、截断 section、非 PGS 噪声 | 不死循环、不越界、不把噪声当 END section |
| BDMV/M2TS CLPI language | language、track type、sample MIME 与外挂/Matroska PGS 一致 |

当前没有 `PgsReaderTest`，不能因两版代码结构相似而直接采纳任一实现。若项目真实输入主要是 Matroska/外挂 `.sup`，A4-3d 可延后；若需要 Blu-ray/M2TS PGS，则应在 A4-3a/b 后优先实施，并以独立 AAR 回滚。

### 12.5 bitmap cue 延迟清除的首轮生命周期差异

上游完整提交：`ba27f889922a281162864a1260e7cb4e73ca0ecf`；当前 fork：`6895be9ae9b777bf8108df7f38d7c32d86fbd222`。两边都在空 cue 到来且旧列表含 bitmap 时延迟 100 ms，非空 cue 会取消 pending runnable，目的是避免 PGS display set 在相邻 sample 之间闪烁。

但 detach 行为不同：

- 上游 `onDetachedFromWindow()` 取消 runnable、立即把 `cues` 置为空并 `updateOutput()`，然后调用 `super`；
- fork 只取消 runnable，然后调用 `super`，保留当前 bitmap cue 列表。

当前 fork 的 `PlayerView.setPlayer(null)` 会调用 `subtitleView.setCues(null)`。由于该调用可能进入 100 ms 延迟，换 Player/换源时旧 PGS 位图可能短暂残留；如果随后设置新非空 cue，pending 会被取消，旧 cue 被新列表替换，这正是想解决的连续 display-set 闪烁场景，但也意味着旧播放器状态可能跨越切换窗口。`SubtitleView.reset()`、`setViewType()`、`PlayerView.onDetachedFromWindow()` 当前没有显式清除 pending runnable 或 cue 列表。

因此不建议只 cherry-pick `ba27f889...`。可实施拆分为 **A4-3e**：保留 100 ms 策略，同时补齐 detach/release、换源/`setPlayer(null)` 的立即清除规则，并以 handler/runnable 生命周期测试证明不会持有旧 View 或旧 bitmap。需要覆盖连续空 cue 去重、空 cue 后新 PGS cue 取消、text+bitmap 混排、setViewType、reset、Activity 销毁和重新 attach。若产品更重视换源绝不残留，可在 `setPlayer(null)` 路径提供立即清除 API，而不取消正常播放期间的短延迟。

### 12.6 A4-3 阶段总建议（当前版本）

| 阶段 | 来源 commit | 当前建议 | 独立回滚边界 |
| --- | --- | --- | --- |
| A4-3a | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | 条件合并 crop、bitmapHeight；不能整体 cherry-pick | common/extractor Cue + PGS parser |
| A4-3b | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | 条件合并尺寸/RLE 防御，先补 raw SUP 测试 | PGS parser 安全检查 |
| A4-3c | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | 需要 raw `.sup` 用户路径时才合并 `.sup` MIME 接线 | App subtitle MIME/外部字幕入口 |
| A4-3d | `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | 条件合并 TS reader；优先 BDMV/M2TS 需求 | extractor TS PGS reader |
| A4-3e | `ba27f889922a281162864a1260e7cb4e73ca0ecf` | 条件合并延迟清除，并修正 detach/换源生命周期 | UI `SubtitleView` cue 生命周期 |

A4-3a/b 与 A4-2b 的 viewport/bitmap 比例应在同一组手机、电视和留黑边截图中联合验收，但代码和发布包仍保持可独立回滚。A4-3d/e 不改变 FFmpeg、MPV、libplacebo 或 native 二进制；Exo 阶段完成并验收前不开始 MPV native 合并。

本检查点先记录已核实的 PGS parser、TS reader 和 bitmap 生命周期差异，避免后续上下文压缩丢失。下一步继续做 `PgsParser` crop/bitmapHeight 的逐字段核对、`PgsReader` alignment/PTS 样例构造，并审阅无 fork 对应的 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` TTML layout/spans。

## 检查点 13：2026-08-21 A4-4 TTML layout、spacing 与比例区域深审

### 13.1 提交身份和范围

上游完整提交：`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，提交标题为 `Preserve TTML layout, spacing, and proportional regions`，父提交为 `ccf962e8912695dc60ce82aa4470df899c6306a3`。当前 fork 的 Media3 工作树没有对应提交或 patch-id 等价提交，不能按“fork 已包含”处理。提交涉及 11 个文件，354 additions/19 deletions，跨越 common、extractor 和 ui 三个模块；它不是可以整体 cherry-pick 的单一 parser 修复。

该提交的功能链如下：

| 层次 | 上游改动 | 对当前项目的直接意义 |
| --- | --- | --- |
| Cue 数据模型 | 增加 `Cue.textRegionHeight`，Builder/getter、equals/hashCode、`buildUpon()`，Bundle 键为 `21` | 让 TTML text region 的 authored 高度在 parser、Session/Bundle 和 renderer 之间保留；缺字段会在跨进程或 MediaSession 后静默丢布局信息 |
| 自定义 span | 新增 `LetterSpacingSpan`，支持 pixel/em，覆盖 `TextPaint` measure/draw，并可 Bundle 往返；`CustomSpanBundler` 新增 span type `5` | TTML 的 `tts:letterSpacing` 和 ARIB `letter-spacing` 能影响 Canvas 测量和绘制，而不是只记录 XML 属性 |
| TTML parser | `TtmlParser(viewportWidth, viewportHeight)`；缺少根 `tts:extent` 时使用 `Format` 尺寸；计算 `pixelSizeToEm`；解析 `normal`、正负 pixel 字距并支持样式继承 | 对带设计画布的 HLS/MP4/字幕 Format，可把 pixel 字体和字距转换成相对布局；没有尺寸时仍保留绝对 pixel fallback |
| TTML region 输出 | text cue 写入 `textRegionHeight`；bitmap cue 的区域比例和实际 bitmap 高度沿用同一设计画布 | 多 region、非方形 bitmap、留黑边场景下，字号变化不会只改变宽度 |
| UI 缩放 | `SubtitleViewUtils.scaleRegionTextCue()` 按相同 `zIndex`/collision 策略的 region group 缩放位置、宽度和高度；bitmap width/height 均围绕视觉中心缩放 | App 的字号调节可以保持 TTML region 相对位置和 PGS/TTML bitmap 宽高比例 |

### 13.2 逐文件行为核对

1. `Cue.java` 的 `textRegionHeight` 是 authored region 高度（相对 viewport 的 fraction），不是根据文字实际包围盒重新测量的高度。它必须和 `position`、`line`、`size`、anchor 一起传递。上游把它加入 equals/hashCode 和 Bundle key `21`，所以只加一个字段而不补序列化会产生难以观察的跨进程差异。
2. `LetterSpacingSpan` 对 pixel 输入在 `TextPaint` 当前字号大于 0 时换算为 em；em 输入直接设置。它同时覆盖 measure 和 draw，避免测量宽度与绘制宽度不一致。`normal` 被明确转成 0；非法单位、空值和继承由 parser 的日志/样式合并路径处理。该 span 也进入 `CustomSpanBundler`，否则 `Cue.toBundle()` 后字距会消失。
3. `TtmlParser` 只有在调用方提供正的 `Format.width/height` 时才会把缺失的根 `tts:extent` 补成默认画布；未知尺寸仍然保持 `Cue.DIMEN_UNSET` 的 pixel fallback。已知画布时 `pixelSizeToEm = cellRows / ttsExtent.height`，pixel font size 会转为 `RelativeSizeSpan`，pixel letter spacing 会依据字体单位换算为 em。这个换算不是“无条件把 px 当 dp”。
4. `TtmlNode` 在创建 text cue 时写入 region 的 `line`、`position`、`size`、`textSize`、`verticalType` 和 `textRegionHeight`；`TtmlRenderUtil` 把字距应用到实际文本 span。`TtmlSubtitle` 负责把 `pixelSizeToEm` 贯穿到每次 `getCues()`。
5. `SubtitleViewUtils.scaleRegionTextCue()` 先得到 cue 的 region bounds，再在同一 `zIndex` 和 `collisionAvoidance` 组内求 union，围绕 group 中心缩放，最后同时改 position、line、size 和 `textRegionHeight`。它不是简单把每个 cue 的左上角乘一个比例，因此不能用当前单 cue `scaleBitmapCue()` 代替。
6. 上游同一提交还修正了 bitmap scaling：当 `bitmapHeight` 有效时，line/lineAnchor 和高度都随字号比例调整。只移植 TTML 字段而保留 fork 的“只缩放 bitmap width”逻辑，会留下上下方向比例错误。

### 13.3 当前 fork 和 App 的接线差异

已核对当前 fork：

- `Cue` 已有 `bitmapHeight`、Builder、Bundle 和 Canvas painter 消费路径，但没有 `textRegionHeight`。
- `CustomSpanBundler` 没有 `LetterSpacingSpan`；`SpannedToHtmlConverter` 目前只映射常见颜色、字号、字体和强调 span，没有 letter-spacing CSS，也没有把 `textRegionHeight` 转成 WebView region 高度。
- fork 的 `TtmlParser` 仍是无参构造，`DefaultSubtitleParserFactory` 不把 `Format.width/height` 传入；parser 没有 pixel canvas fallback、letter-spacing 属性或 region-height 输出。
- `ExoUtil.setPlayerView()` 明确调用 `setApplyEmbeddedFontSizes(false)`，并通过 `addTextSize()`/`subTextSize()` 改变默认字号。当前 fork 的 `SubtitleView` 在 `bitmapSizeScale != 1` 时只调 `Cue.size` 和横向 position，未同步 `Cue.bitmapHeight`、line 或 lineAnchor。
- App 默认是 Canvas subtitle output；WebView 是 Media3 的实验性 view type，不能假定它会自动获得 Canvas 的 region/collision 行为。
- `app/src/main/java/com/fongmi/android/tv/player/lyrics/TtmlClient.java` 属于歌词业务链：它用 DOM 读取 TTML 的时间信息后转换成增强 LRC，再交给歌词/Karaoke UI；它不走 Media3 `TtmlParser`/`SubtitleView`，因此不能作为本提交在 App 中已经生效的证据。
- 外挂 TTML 经 `SingleSampleMediaSource` 时，当前构造的 `Format` 通常没有设计画布 width/height；MP4 `stpp`/TTML text entry 也未确认会稳定提供与 `tts:extent` 相同语义的尺寸。因此 `Format` fallback 对 HLS/MP4/外置三类来源的收益不一致，必须先记录尺寸来源。

### 13.4 可实施拆分

不建议整体 cherry-pick `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`。建议拆为以下独立阶段，代码阶段和发布回滚边界保持分开：

1. **A4-4a：Cue/TTML parser 数据模型**

   选择性移植 `textRegionHeight`、Bundle/equals/hashCode、`LetterSpacingSpan`/custom span type 5、TTML letter-spacing 解析和 region height 输出。先保持现有字号策略，不打开 UI 缩放。该阶段可用 parser unit test 和 Cue Bundle round-trip 验证，失败时只回滚 common/extractor 改动。

2. **A4-4b：当前 Canvas/App policy 适配**

   将上游 region-group scaling 改造成与 fork 的 `bitmapSizeScale`、`addTextSize/subTextSize` 和 `setApplyEmbeddedFontSizes(false)` 兼容的实现；同时把 bitmap scaling 扩展到高度、line 和 anchor。必须明确 text region 的缩放基准是字幕 view、video viewport 还是设计画布，不能直接照搬上游默认 `textSizeScale`。该阶段是用户可见收益的主要来源。

3. **A4-4c：WebView parity（可选）**

   若项目继续使用 `VIEW_TYPE_WEB` 或 WebHome 字幕消费，再给 `SpannedToHtmlConverter` 增加 `LetterSpacingSpan -> letter-spacing`，并为 text region height/position 生成等价 CSS。若 WebView 仅作为实验性路径，可暂缓，避免维护两套不一致的布局算法；但文档和测试必须明确 Canvas/WebView 差异。

4. **A4-4d：设计画布尺寸接线**

   分别确认 HLS、MP4 `stpp`、外挂 TTML 的 `Format.width/height` 是否存在、是否与 TTML `tts:extent` 同语义；必要时在创建 subtitle `Format` 时显式保存源文件设计尺寸。没有可靠尺寸时不要假装启用 pixel-to-em 换算，应保留绝对 pixel fallback。

### 13.5 收益、风险和决策建议

收益：TTML 的比例 region、非方形 bitmap、pixel 字距/ARIB 字距、字体测量一致性和跨 Bundle 传输得到完整表达；在用户调整字幕字号时，多 region 的相对布局和 bitmap 宽高更稳定。对依赖 AMLL/TTML 歌词转换的 `TtmlClient` 没有直接收益，因为那条链已经在 Media3 之前转换成 LRC。

风险：

- 需要同时改 common、extractor、ui；仅移植 parser 会生成字段但不改变当前 Canvas 的实际表现，容易形成“看似合并、用户无收益”。
- App 关闭 embedded font sizes 后，TTML 的 pixel font/letter spacing span 可能被 `removeEmbeddedFontSizes()` 或自定义字号策略部分移除；需要确认 `LetterSpacingSpan` 不被误判为 size span，并定义字号调节时的优先级。
- `Format` 没有设计尺寸时，pixel-to-em 的 fallback 不可用；错误地填入屏幕尺寸会改变原始 TTML 的 authored 坐标。
- WebView 不支持该提交中的 Canvas group scaling/collision 语义，若没有 CSS parity，两个 view type 的截图会不同。
- `textRegionHeight` 与 A4-2 的 collision bounds、A4-3 的 PGS `bitmapHeight` 共用 Cue/UI 缩放链；如果分阶段合并却不做联合回归，可能出现文本 region、PGS bitmap、ASS cue 混排时的位置漂移。

当前建议：**条件合并，先做 A4-4a，再根据真实 TTML 样片决定 A4-4b；A4-4c 可延后，A4-4d 必须在宣布完成前闭合。** 没有带 `tts:extent` 或可确认 Format 画布尺寸的真实 TTML 样片时，不建议直接合并 parser 的 pixel fallback；如果产品当前只消费外置 TTML 的纯文本，可暂缓整个 A4-4。来源 hash 必须在 AAR/变更日志中记录为 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`。

### 13.6 最低验证集合

| 场景 | 关键断言 |
| --- | --- |
| 根节点有/无 `tts:extent`，Format 有/无 width-height | 有尺寸时 pixel-to-em 可重复；无尺寸时不伪造 viewport，保留 pixel fallback |
| pixel/em/% 字体大小 | Canvas 测量、绘制和预期设计画布比例一致；`setApplyEmbeddedFontSizes(false)` 的行为明确 |
| `letterSpacing` 为 px、`normal`、负值、非法单位、继承 | parser 不崩溃；span 同时影响 measure/draw；非法值不污染后续 cue |
| Cue Bundle/MediaSession 往返 | `textRegionHeight`、bitmapHeight、LetterSpacingSpan 均保留，equals/hashCode 稳定 |
| 多个 region、不同 anchor、字号增减 | group center 缩放；position/line/size/region height 一致，不越出 view |
| 非方形 TTML/PGS bitmap | width 和 height 同步缩放，lineAnchor 不漂移；与 A4-3 bitmapHeight 联合验证 |
| ASS 文本 + TTML region + PGS bitmap 混排 | A4-2 collision 只作用于应参与的文本，位图不被错误移动，z-index 稳定 |
| Canvas 与 WebView | 若 WebView 未实施 parity，差异被明确记录且不会误称为一致；若实施，截图/HTML CSS 均回归 |
| HLS、MP4 `stpp`、外挂 TTML | 分别记录 Format 画布尺寸来源和 parser 行为，不以单一样片推断全部输入 |

### 13.7 PGS 检查点补充修正

对检查点 12 的 PGS 结论再明确两点：

- 当前 fork 的 `Cue.bitmapHeight` 和 Canvas `SubtitlePainter` 已经存在，因此 A4-3a 的核心缺口不是重新引入字段，而是 PGS parser 在 composition crop 后设置正确的 `bitmapHeight`，并让 App 的字号缩放同时调整高度；该工作与 A4-4b 共用 UI 缩放验证，但代码仍可独立回滚。
- `PlayerHelper.getSubtitleMimeType()` 当前对 `.sup` 未映射到 `MimeTypes.APPLICATION_PGS`，未知扩展名回退 SubRip。raw SUP parser 即使合并成功，外部 SUP 仍不可达，除非 A4-3c 同时补 MIME 接线和入口测试。

本检查点完成 TTML 提交的逐文件审计，并把 PGS 的字段/接线修正落盘。下一步回到 A4-1，完成 charset/extraction 接线的最终决策；随后补齐 A4-3 raw SUP、PGS reader、bitmap 生命周期的测试/回滚矩阵，再把 A4-2/A4-3/A4-4 的共同 Cue/UI 截图验收整理成联合表。Exo 阶段完成前继续不修改 MPV、FFmpeg、mpv-android 或 libplacebo 二进制。

## 检查点 14：2026-08-21 A4-1 charset/extraction 接线最终审计

### 14.1 提交身份与已存在部分

上游完整提交：`d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`，标题 `Improve subtitle decoding and extraction pipeline`。当前 fork 对应提交：`63531ddcd508b646e0cf515df3bb6caf4835120e`，标题 `Improve subtitle decoding pipeline`。两者不是 patch-id 等价：上游修改 14 个文件、163 additions/10 deletions；fork 对应提交修改 9 个文件、62 additions/6 deletions。

当前 fork 已有且不应重复移植的部分：

- `juniversalchardet 1.0.3` 的 version catalog、exoplayer/extractor 依赖已经存在。
- `TextRenderer.legacyDecodingEnabled = true` 已存在，这是当前 `parseSubtitlesDuringExtraction=false` 时让 raw text/bitmap subtitle 在 renderer 阶段继续可用的必要条件。
- `DefaultMediaSourceFactory.parseSubtitlesDuringExtraction=false` 和 `DefaultExtractorsFactory.textTrackTranscodingEnabled=false` 是 fork 的明确策略；App 的 `com.fongmi.android.tv.player.exo.MediaSourceFactory` 当前没有显式设置这两个值，而是依赖 fork 默认值。
- render-time `DelegatingSubtitleDecoder` 和 whole-file `SubtitleExtractor` 已经接入字符集探测，但实现比上游旧，存在有效长度、MIME 范围和 TTML XML 声明三个实质缺口。

### 14.2 当前 fork 字符集实现的具体问题

#### 14.2.1 有效长度判断导致探测经常被跳过

fork 的 `DelegatingSubtitleDecoder.decode(data, length, ...)` 只有在 `data.length == length` 时才做转换；decoder buffer 的容量大于当前 sample 有效长度时直接跳过。fork 的 `SubtitleExtractor.convertToUtf8()` 同样要求 `subtitleData.length == bytesRead`；未知 Content-Length 时 buffer 按 1024 字节扩容，实际文件长度通常不会刚好等于容量，因此外挂字幕经常不进入探测。

上游改为始终只把 `[0, length)` 或 `[0, bytesRead)` 交给 detector 和 `new String(...)`，转换后再把有效长度更新成 UTF-8 byte length。这个变化不是微优化，而是决定 GB18030/Big5/Windows-1252 等字幕能否稳定生效的核心修复。

#### 14.2.2 当前“非位图即文本”分类过宽

fork 只把 PGS、VobSub、DVB 标记为 `binaryFormat`，其余格式都会尝试字符集探测。TX3G 和 MP4 WebVTT 本身含二进制长度/box 结构，不应送入通用文本 detector；在 buffer 恰好等长时，误识别和重编码可能破坏 sample。

上游只对白名单执行探测：

- `MimeTypes.TEXT_SSA`
- `MimeTypes.TEXT_VTT`
- `MimeTypes.APPLICATION_SUBRIP`
- `MimeTypes.APPLICATION_TTML`

MP4VTT、TX3G、PGS、VobSub 和 DVB 均保持原字节。该白名单比维护不断增长的 `binaryFormat` 黑名单更符合当前 parser 边界。

#### 14.2.3 TTML 转码后必须同步 XML 声明

fork 把非 UTF TTML bytes 转成 UTF-8，却保留例如 `<?xml encoding="GB18030"?>` 的原声明。`TtmlParser`/XML parser 可能按声明再次把 UTF-8 bytes 当成旧编码读取，造成乱码或 parse failure。

上游用只匹配文档开头 XML declaration 的 `XML_ENCODING_ATTRIBUTE`，在实际发生非 UTF -> UTF-8 转换后把 declaration 改成 `UTF-8`；支持 BOM、前导空白和单双引号。没有 declaration 时不插入新声明。这个 hunk 应同时进入 render-time decoder 和 `SubtitleExtractor`，不能只修一条路径。

### 14.3 `createForExtraction()` 的真实语义和限制

上游给 `DefaultSubtitleParserFactory` 增加两种模式：默认构造仍支持全部格式；`createForExtraction()` 返回的 factory 在 `supportsFormat()` 中对 PGS、VobSub、DVB 返回 false，使 `SubtitleTranscodingExtractorOutput` 在容器 extraction 阶段把这些 bitmap samples 原样传下去，等 track selection 后由 `TextRenderer` legacy decoder 解码。这样可以避免未选中的位图轨也提前创建大量 bitmap/Cue。

但该保护有两个边界：

1. `supportsBitmapSubtitles=false` 只影响 `supportsFormat()`，`create(format)` 的 switch 仍能创建 PGS/VobSub/DVB parser。任何未先调用 `supportsFormat()`、直接调用 `create()` 的路径都不能自动获得保护。当前 `BundledChunkExtractor` 的 standalone text representation 分支就是直接创建 parser，需要在实施时单独检查；常见 HLS WebVTT/SS TTML 不受影响，但不能把 factory 名称理解成全局硬禁用位图。
2. `DefaultMediaSourceFactory` 在 `parseSubtitlesDuringExtraction=true` 的外挂字幕分支，对 factory 不支持的格式使用 `UnknownSubtitlesExtractor`；该 extractor 只发布 `TEXT_UNKNOWN` format 并跳过全部输入，不产生 raw sample。因此把 extraction-safe factory 直接作为外挂字幕默认 factory 后，外部 PGS/VobSub/DVB 不会自动回退到 renderer decoder。若未来打开 extraction，bitmap 外挂字幕必须继续走 `SingleSampleMediaSource`/raw sample 分支，而不是 `UnknownSubtitlesExtractor`。

容器内字幕与外挂字幕因此不能简单共用一个“safe factory + 全局开关”策略。容器的 `SubtitleTranscodingExtractorOutput` 对 unsupported format 会 pass-through；外挂字幕的 `UnknownSubtitlesExtractor` 不会。

### 14.4 当前 App 的实际数据流

当前 App 在 `ExoUtil` 中创建自定义 `com.fongmi.android.tv.player.exo.MediaSourceFactory`，内部构造：

```text
DefaultExtractorsFactory（fork 默认 textTrackTranscodingEnabled=false）
  -> DolbyVisionP81ExtractorsFactory wrapper
  -> DefaultMediaSourceFactory（fork 默认 parseSubtitlesDuringExtraction=false）
```

因此：

- 外挂字幕当前走 `SingleSampleMediaSource`，raw sample 在 `TextRenderer`/`DelegatingSubtitleDecoder` 阶段解析；A4-1 最直接的用户收益是修正这里的字符集有效长度和 TTML declaration。
- Matroska/MP4/TS/AVI 等容器当前设置 `FLAG_EMIT_RAW_SUBTITLE_DATA`，同样依赖 render-time decoder；字符集修复也会覆盖嵌入式 SSA/SRT/TTML 文本。
- HLS/DASH/SS chunk 的 parse flag 由 `DefaultMediaSourceFactory` 向下传递，当前是 false；上游对 `BundledChunkExtractor`、`MediaParserChunkExtractor`、HLS 和 SS factory 的 `createForExtraction()` 替换在当前配置下大多是 dormant wiring，不应被描述成已有用户收益。
- `PlayerHelper.getSubtitleMimeType()` 当前只显式识别 VTT、SSA/ASS、TTML/XML/DFXP，其余回退 SubRip；A4-1 的 MIME 白名单依赖这里正确分类。A4-3 增加 `.sup` 后还要保证它不会进入 charset detector。

建议在真正实施时由 App 显式调用 `experimentalParseSubtitlesDuringExtraction(false)` 和 `setTextTrackTranscodingEnabled(false)`（或项目等价稳定 API），不要继续只依赖 Media3 fork 的构造默认值；这样后续合并上游默认值变化时不会无意切换整个字幕架构。

### 14.5 可实施阶段和回滚边界

1. **A4-1a：render-time charset/TTML 修复（建议优先合并）**

   以 `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` 为来源，手工改造 `DelegatingSubtitleDecoder` 和 `SubtitleDecoderFactory`：按 SSA/WebVTT/SubRip/TTML MIME 白名单探测；严格使用 `length`；TTML 转换后改写 XML encoding。保留 `legacyDecodingEnabled=true`。这是独立、直接可见且容易回滚的 exoplayer 变更。

2. **A4-1b：whole-file `SubtitleExtractor` 修复（建议与 1a 同轮测试，可独立提交）**

   增加 `inputSampleMimeType` 构造参数、nullable detector、相同 MIME 白名单、有效 `bytesRead` 和 TTML declaration 修复；`DefaultMediaSourceFactory` 创建 `SubtitleExtractor(format=null)` 时传入源 MIME。即使当前 parse flag 为 false，这能消除将来打开 extraction 或单独使用 extractor 时的已知错误。

3. **A4-1c：extraction-safe factory API（条件合并）**

   增加 `DefaultSubtitleParserFactory.createForExtraction()`，但第一轮不改变 App 的 parse/transcoding 开关。对所有接线点逐一确认是否先调用 `supportsFormat()`；容器 extraction 可使用 safe factory，外挂字幕需保留 full factory 或 per-format raw fallback。该阶段只提供迁移基础，不应和“启用 extraction”合成一个提交。

4. **A4-1d：按文本格式灰度启用 extraction（暂缓，需产品样片）**

   若确实需要解决 adaptive subtitle segment overlap/flicker，再只对 SSA/WebVTT/SubRip/TTML 启用 extraction；PGS/VobSub/DVB 保持 raw/track-selected decoding。外挂 bitmap 必须明确走 `SingleSampleMediaSource`，并验证 HLS/DASH/SS、Matroska/MP4/TS 和外置字幕的分支。该阶段会改变 sample queue 格式、解析时机、错误传播和内存占用，应作为独立 AAR/发布开关。

### 14.6 利弊与最终建议

| 项目 | 收益 | 代价/风险 | 当前建议 |
| --- | --- | --- | --- |
| A4-1a decoder 修复 | 直接修复外挂/嵌入 GB18030、Big5、Windows-1252 文本及非 UTF TTML | detector 对每个 raw text sample 运行；需防 detector 返回 Android 不支持的 charset 名 | **建议合并** |
| A4-1b extractor 修复 | 修复已存在但受 buffer 容量影响的 whole-file 转码；为未来 extraction 做准备 | 当前默认路径多为 dormant；需补 unknown length/seek 测试 | **建议合并，独立提交** |
| A4-1c safe factory | 容器 extraction 时避免未选 bitmap 轨提前 decode | `supportsFormat()` 保护不是硬禁用；外挂 unsupported 分支会丢 sample | **条件合并，先修接线边界** |
| A4-1d 启用 extraction | adaptive text segment 合并更稳定，可能减少片段切换闪烁 | 改变架构和错误时机；bitmap/外置字幕回退复杂；内存与 CPU 需实测 | **暂缓默认启用** |

总判断：**不要整体 cherry-pick `d82fb7b9...`。优先手工合并 A4-1a 和 A4-1b；保留 fork 的 legacy/render-time 默认策略。A4-1c 可以作为不改变默认行为的基础 API，但必须修复/规避外挂 bitmap 的 `UnknownSubtitlesExtractor` 分支；A4-1d 由真实 adaptive 字幕需求决定。**

### 14.7 最低自动化与样片验证

| 场景 | 必须断言 |
| --- | --- |
| decoder buffer capacity > valid `length` | 只读取有效 bytes，尾部旧数据不参与 detector/parser；非 UTF SRT/ASS/TTML 仍正确 |
| extractor unknown Content-Length、非 1024 整数倍 | `bytesRead` 驱动转换，不能因 capacity 不等而跳过 |
| GB18030/Big5/Windows-1252 SSA、SRT、WebVTT | 中文/标点/西文重音正确；时间轴和样式未改变 |
| 非 UTF TTML，XML declaration 为单双引号/带 BOM/前导空白 | 转为 UTF-8 后 declaration 同步；无 declaration 时不错误插入 |
| UTF-8、UTF-8 BOM、UTF-16LE/BE | 不做破坏性二次转码；parser 结果与合并前一致 |
| TX3G、MP4VTT、PGS、VobSub、DVB | detector 不运行或不修改 bytes；raw sample/decoder 可达 |
| detector 无结果或 charset 不受 Android 支持 | 原数据安全回退或产生可诊断错误，不因 `Charset.forName()` 让整个播放崩溃 |
| 外挂 PGS，parse=false/true | parse=false 继续有 raw sample；未来 parse=true 时不能落入丢数据的 `UnknownSubtitlesExtractor` |
| Matroska/MP4/TS/AVI 文本与 bitmap 多轨 | 未选 bitmap 不提前分配；选中后正常显示；切轨、seek 和换源正常 |
| HLS/DASH/SS segmented text | extraction off/on 的 cue 序列、片段边界和闪烁对照；错误传播可观测 |

建议补 `DelegatingSubtitleDecoderTest`、`SubtitleExtractorTest`、`DefaultSubtitleParserFactoryTest` 和 `DefaultMediaSourceFactory` 外挂 subtitle 路由测试；样片至少包括 GB18030 ASS/SRT、Big5 SRT、Windows-1252 VTT、GB18030 TTML、MP4 TX3G/MP4VTT、Matroska PGS/VobSub、TS DVB/PGS。A4-1 不修改 FFmpeg、MPV、mpv-android 或 libplacebo。

本检查点完成 A4-1 最终决策。下一步补齐 A4-3 的 raw SUP/PGS reader/bitmap 生命周期测试与回滚矩阵，再将 A4-2/A4-3/A4-4 的共同 Cue/UI 验收整理为一个联合阶段表，最后汇总 Exo 全部阶段供用户逐项决策。

## 检查点 15：2026-08-21 A4-3 PGS/SUP、TS reader 与 bitmap 生命周期收尾

### 15.1 提交身份和当前 fork 已有能力

本组需要同时记录三组上游/ fork 提交：

| 功能 | 上游完整 commit | 当前 fork 完整 commit | 结论 |
| --- | --- | --- | --- |
| PGS parser 重写、raw SUP 支持、crop/防御 | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | `0aae318bded9b6790823d7ed7c9d1261f2af9344` | 同一功能线但非 patch-id 等价，不能整体跳过或整体 cherry-pick |
| TS PGS payload reader | `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | `0351ac61d75ce71ffe062be9b57adad09eadc617` | 两边都有 `0x90` reader，状态机/PTS 语义不同 |
| bitmap cue 延迟清除 | `ba27f889922a281162864a1260e7cb4e73ca0ecf` | `6895be9ae9b777bf8108df7f38d7c32d86fbd222` | 两边都有 100 ms 策略，detach/release 行为不同 |

当前 fork 已经具备、不能重复计入上游收益的能力：

- `PgsEpochState`、palette/object/window/composition 状态对象、跨 sample ODS/WDS 分片、palette update、clear display set 和 epoch reset。
- raw SUP magic `0x5047` 识别、13-byte segment header、PTS 换算和 `PgsSupTiming` 的 display-set duration/seek 输出。
- `Cue.bitmapHeight`、Bundle 序列化和 Canvas `SubtitlePainter` 的 bitmap 高度消费。
- RLE 读指针缺失、run length 超出剩余像素、颜色字节缺失等基本拒绝路径。

因此 A4-3 的合并价值必须按“当前 fork 的实际缺口”计算，而不是按上游文件数量计算。

### 15.2 PGS parser：逐字段差异

#### 15.2.1 composition crop 是真实功能缺口

上游 `PgsCompositionObject` 保存 cropped flag 和 `cropX/cropY/cropWidth/cropHeight`，`PgsCueBuilder.buildCue()` 在解码完整 bitmap 后验证 crop rectangle，使用 `Bitmap.createBitmap(sourceOffset, sourceWidth, cropWidth, cropHeight)` 构造实际裁剪 bitmap，并以裁剪后的宽高计算 `Cue.size` 与 `Cue.bitmapHeight`。

当前 fork 的 `PgsCompositionObject` 只有 object id、window id、x、y；解析 composition section 时会消耗 crop 的 8 个字节，但丢弃它们。`buildCue()` 总是使用完整 object bitmap，导致带 crop 的 SUP 可能显示透明/无效区域，宽高和位置也可能偏大。该差异会在字幕带大画布留白、同一 object 复用或多 object 叠加时直接可见。

#### 15.2.2 尺寸上限和现有防御的边界

上游新增 `MAX_BITMAP_DIMENSION = 4096`，在 base ODS 读取宽高时先拒绝超大对象；同时保留 pixel count、plane 边界、crop 合法性和 RLE run end 检查。

当前 fork 已有：

- `bitmapWidth/bitmapHeight <= planeWidth/planeHeight` 的早期检查；
- `pixelCountLong > Integer.MAX_VALUE` 防止乘法溢出；
- RLE 长度、颜色索引和缺失字节检查。

但它没有独立的 4096 上限，也没有 crop 后的 plane 边界检查。只补常量而不补 allocation 前的完整检查没有意义；建议把上游的上限和 crop/plane 检查作为一个原子 parser safety hunk 移植。

#### 15.2.3 raw SUP 能力已有，但外部入口仍断开

当前 fork 与上游的 `PgsSupTiming` 实现一致，能为连续 display set 计算 duration，并将 clear display set 转为空 cue。仓库只发现 `sample_with_pgs_subtitles.mkv`、VobSub playback dump 等资产，没有 `.sup` 原始夹具，也没有 `PgsParserTest`。

`PlayerHelper.getSubtitleMimeType()` 对 `.sup` 未返回 `MimeTypes.APPLICATION_PGS`，未知扩展名回退 `APPLICATION_SUBRIP`。所以仅合并 parser crop/防御不会让外部 raw SUP 进入 PGS parser；必须把 MIME 接线作为独立子步骤并验证 `Sub.from(path)`、`ExoUtil.buildSubtitleConfigs()` 和 `SingleSampleMediaSource`。

### 15.3 PGS TS reader：状态机差异

两边都把 `TsExtractor.TS_STREAM_TYPE_PGS = 0x90` 接到 `DefaultTsPayloadReaderFactory`，并发布 `APPLICATION_PGS` text track；BDMV 路径也会 delegate 到该 factory，CLPI language 可传入 format。差异集中在 PES 边界和时间戳：

| 状态 | 上游 `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | fork `0351ac61d75ce71ffe062be9b57adad09eadc617` | 影响 |
| --- | --- | --- | --- |
| `packetStarted()` | 每个 PES 都令 `writingSample=true`；只有非 `TIME_UNSET` PTS 才更新 `lastPacketTimeUs` | 没有 `FLAG_DATA_ALIGNMENT_INDICATOR` 时直接 return；有 alignment 才启动并覆盖 `packetTimeUs` | 无 alignment 的合法后续 PES 可能被 fork 丢弃 |
| 最近 PTS | 保留最近一个非空 PTS，后续无 PTS PES 继续沿用 | 当前 PES 的 `TIME_UNSET` 可覆盖之前时间 | 首 PES 无 PTS、后续有 PTS 的时间线不同 |
| display set 提交后 | `resetSampleState()` 不关闭 `writingSample`，同一 PES/后续无 alignment 可继续 | `resetSampleState()` 将 `writingSample=false` | 一个 PES 多 display set 在当前 consume 循环可继续，但下一 PES 必须再次 alignment |
| seek | 清理 section/sample/最近 PTS | 同样清理，但首个无 alignment PES 行为仍不同 | seek 后首 display set 可能缺失或出现 `TIME_UNSET` |

`consume()` 在同一次调用中会继续处理一个 PES 内的后续 display set，即使 fork 在 commit 后把 `writingSample` 置 false；真正的差异发生在下一次 `packetStarted()`/`consume()`。测试不能只覆盖“一 PES 一个 display set”。

### 15.4 bitmap cue 延迟清除和 View 生命周期

上游 `ba27f889922a281162864a1260e7cb4e73ca0ecf` 与 fork `6895be9ae9b777bf8108df7f38d7c32d86fbd222` 都在空 cue 到来且旧列表含 bitmap 时延迟 100 ms；非空 cue 会取消 pending runnable 并立即替换列表。这能避免 PGS display set 相邻 sample 的短暂闪烁。

差异：

- 上游 `onDetachedFromWindow()` 仅在存在 pending runnable 时取消它，并把 `cues` 置空、`updateOutput()`；没有 pending 时不主动清除普通文本 cue。
- fork 的 `onDetachedFromWindow()` 只取消 pending runnable，不清空旧 bitmap cue。若 detach 正好发生在延迟窗口内，旧 bitmap 仍可能留在 `cues` 列表。
- 当前 `PlayerView.setPlayer(null)` 会调用 `subtitleView.setCues(null)`；在旧列表含 bitmap 时这会进入 100 ms 延迟，换源/解绑短时间内可能看到上一轨位图。
- `setViewType()`、`reset()`、Activity 销毁/重新 attach 没有公开的“立即清 bitmap”路径；pending runnable 还可能持有 View 直到超时。

正常播放期间的 100 ms 延迟是为避免闪烁，不能简单改成所有空 cue 立即清空。换播放器、换源、detach/release 则应有立即清除语义，建议由 `SubtitleView` 增加包内/受控的 `clearCuesImmediately()`，由 `PlayerView` 在 `setPlayer(null)` 和生命周期释放调用，而不改变连续 PGS display set 的延迟策略。

### 15.5 可实施阶段、依赖和回滚边界

1. **A4-3a：PGS crop + bitmapHeight**

   来源 `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`。补 `PgsCompositionObject` crop 字段、crop 合法性、实际 bitmap 裁剪、plane 边界和 `Cue.bitmapHeight`。当前 fork 已有 Cue 字段，代码范围主要是 extractor PGS；独立回滚不影响 TS reader 或 View 生命周期。

2. **A4-3b：PGS dimension/RLE safety**

   仍来源 `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`。增加 4096 dimension cap，并确认异常 object/display set 被丢弃后不会污染下一个 epoch。与 3a 可同一 AAR 验收，但保留独立提交，便于在极端 SUP 兼容性问题时只回滚安全限制。

3. **A4-3c：raw `.sup` MIME/入口**

   以 parser 已有能力为基础，补 `PlayerHelper.getSubtitleMimeType()` 的 `.sup -> MimeTypes.APPLICATION_PGS`，并验证 Sub/Exo 外挂链。该步骤不应被隐藏在 parser cherry-pick 中；若产品不需要外部 SUP，可延后。

4. **A4-3d：PGS TS reader PES/PTS 语义**

   来源 `1b112bd1375c7a796cbde58d4c90226c7fc1947a`。优先手工移植“最近非空 PTS + 无 alignment 后续 PES 可继续”的语义，保留 fork 已验证的 section state；不要整体覆盖已有 reader。若没有 BDMV/M2TS 输入，可延后到需要时。

5. **A4-3e：bitmap cue lifecycle**

   来源 `ba27f889922a281162864a1260e7cb4e73ca0ecf`。保留播放期间 100 ms debounce，补 detach/release/`setPlayer(null)` 的立即清除，并覆盖 setViewType/reset/reattach。该步骤只改 ui/App 生命周期，独立于 parser/TS reader。

6. **A4-3f：夹具和自动化回归**

   新增最小 raw SUP builder/fixture 或 byte arrays，不把大媒体文件作为唯一证据；增加 PgsParser、PgsReader、SubtitleView lifecycle 测试。该步骤完成前不应把 3a-3e 标为生产完成。

### 15.6 最低测试矩阵

| 层 | 样例/操作 | 断言 |
| --- | --- | --- |
| PGS parser | 普通 display set、palette update、多个 object、WDS/ODS 分片 | cue 数量、palette、object 复用和 display-set duration 正确 |
| crop | cropped flag，合法/零宽高/越界 crop | 只显示 crop bitmap；非法 crop 丢弃当前 cue，不污染下一个 display set |
| dimensions | 4096 边界、4097、plane 大于/小于 bitmap、乘法溢出 | 不提前分配超大 bitmap；异常输入可恢复，不崩溃 |
| RLE | 短 run、长 run、零 run、缺颜色、run 超出 pixel count、截断 ODS | 不越界、不死循环；后续 epoch 仍能显示 |
| raw SUP | segment header/section 跨 sample、首段无 identifier、clear display set、seek | PTS、duration、空 cue 和 seek 输出稳定 |
| external SUP | `.sup` MIME 接线、未知扩展名对照、HTTP/file URI | 进入 `PgsParser` 而不是 SubRip；错误 MIME 不静默乱码 |
| TS reader | section type/size/body 分别跨 TS/PES；有/无 alignment | 不丢 section，display set sample 独立，后续 PES 可继续 |
| PTS | 首 PES 无 PTS、后续有 PTS；中间无 PTS；seek 后首 PES 无 alignment | 沿用最近有效 PTS，不产生错误 `TIME_UNSET` 时间线 |
| BDMV/M2TS | `0x90`、CLPI language、EOF、截断 PES | format language/MIME 一致；EOF 不死循环 |
| View lifecycle | 连续 bitmap cue、一次/多次空 cue、非空取消 pending、detach、setPlayer(null)、setViewType、reset | 播放间隙不闪烁；解绑/销毁不残留旧 bitmap、不持有 View |
| UI 联合 | PGS bitmap + ASS/TTML text、字号增减、16:9 letterbox | bitmap 不参与 ASS collision；width/height/line 同步；viewport 内位置稳定 |

### 15.7 当前建议

**建议有真实 PGS/SUP 需求时先合并 A4-3a + A4-3b，再按外部 SUP 或 BDMV 需求选择 3c/3d；A4-3e 应随 Exo UI 字幕回归一起验收但保持独立回滚。禁止整体 cherry-pick 三个上游提交。**

若项目当前主要使用 Matroska 内嵌 PGS，3a/3b 的 crop/防御收益高于 3d；若主要是 Blu-ray/M2TS，3d 的 PTS/alignment 语义应提前；若没有 raw `.sup` 外挂源，3c 可延后。所有阶段都不修改 FFmpeg、MPV、mpv-android 或 libplacebo 二进制。

本检查点完成 A4-3 的 parser、raw SUP、TS reader 和 bitmap 生命周期收尾，记录了完整 commit ID、现有 fork 能力、实施顺序和独立回滚边界。下一步把 A4-2/A4-3/A4-4 的共同 Cue/UI 截图和联合依赖关系整理成一张可执行阶段表，然后汇总 Exo 全部阶段。

## 检查点 16：2026-08-21 A4 联合验收阶段与 Exo 实施总表

本检查点把 A4-2（ASS collision/layer）、A4-3（PGS/SUP、TS reader、bitmap 生命周期）和 A4-4（TTML layout/spacing）从“各自可行”收敛成一条可以分批发布、独立回滚、共享截图证据的实施链，同时汇总当前已经深审的 Exo 阶段。这里仍然只记录建议和验收要求，不修改依赖 lock、AAR、native 二进制或 `third_party/sources/media` 的用户未提交改动。

### 16.1 三组功能真正共享的契约

A4-2/A4-3/A4-4 不是三个互不相干的 parser 更新，它们最终都会经过同一条 `Cue -> CueGroup -> SubtitleView -> CanvasSubtitleOutput/SubtitlePainter` 路径。合并时必须先冻结以下契约，否则每个提交单独通过 parser 测试，混排后仍可能出现位置漂移或状态残留。

| 共享面 | 必须保留的字段/语义 | 相关来源 commit | 当前 fork 状态与注意事项 |
| --- | --- | --- | --- |
| Cue 几何 | `position`、`line`、`size`、`lineAnchor`、`positionAnchor`、`zIndex`、`bitmapHeight`、新增 `textRegionHeight` | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`、`aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | `bitmapHeight` 已有；`textRegionHeight` 缺失。不能重新定义 bitmap 高度的单位或默认值。 |
| 碰撞策略 | `Cue.collisionAvoidance = NONE/UP/DOWN`；只对文本 cue 计算 bounds，位图不参与 | `6794d75b7a39db42dcfcab18c915f0da165515b5` | 缺少字段；fork 的 SSA parser 已有 parser-side stacking，不能和 Canvas offset 同时生效。 |
| 序列化 | Builder、`equals/hashCode`、`buildUpon()`、Bundle/Binder round-trip 均保留新字段和 `zIndex` | `6794d75b7a39db42dcfcab18c915f0da165515b5`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | `zIndex` 已在 fork；新字段漏进 Bundle 会在 MediaSession/跨进程时静默丢失。 |
| 视口 | collision 和比例缩放以 `exo_content_frame` 的实际 video viewport 为基准，不能默认使用整个 `PlayerView` | `6794d75b7a39db42dcfcab18c915f0da165515b5` | `PlayerView` 还含 `DanmakuView`，坐标同步必须手工适配并保持弹幕层不被字幕算法移动。 |
| 字号策略 | `ExoUtil.setApplyEmbeddedFontSizes(false)`、`addTextSize/subTextSize` 与 TTML pixel/em、ASS margin 的优先级固定 | `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | App 已有用户字号控制；不能让上游默认 `textSizeScale` 覆盖设置，也不能只缩放 bitmap 宽度。 |
| 生命周期 | 连续 PGS display set 的 100 ms debounce 与 detach/release/换源时立即清理是两种不同语义 | `ba27f889922a281162864a1260e7cb4e73ca0ecf` | fork detach 不清旧 bitmap；`setPlayer(null)` 可能暂缓清空。应增加受控的 immediate-clear 路径。 |
| 输出实现 | Canvas collision/region scaling 与 WebView CSS 不是同一实现；未做 parity 时必须显式记录差异 | `6794d75b7a39db42dcfcab18c915f0da165515b5`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 当前默认 Canvas；WebView 是实验路径，不能把 Canvas 测试结果当成 WebView 已验证。 |

### 16.2 A4 联合实施顺序和独立回滚边界

下表是推荐的实施顺序。阶段编号沿用 A4 子组，并增加 `J` 表示必须跨子组联合验收的步骤；同一阶段可以在代码上拆成多个 commit，但发布时应保持表中的回滚边界。

| 顺序 | 阶段 | 来源 commit（完整 ID） | 主要动作 | 前置条件 | 回滚边界/当前建议 |
| ---: | --- | --- | --- | --- | --- |
| 0 | A4-J0 基线冻结 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`、`92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`、`6794d75b7a39db42dcfcab18c915f0da165515b5`、`aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`、`1b112bd1375c7a796cbde58d4c90226c7fc1947a`、`ba27f889922a281162864a1260e7cb4e73ca0ecf`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 记录当前 fork 对应 hash；显式固定 `parseSubtitlesDuringExtraction=false`、`textTrackTranscodingEnabled=false`；建立 ASS/TTML/PGS 最小夹具和截图基线 | 无 | 无代码；必须先完成。若基线无法重现，后续任何“改善”都不具备可归因性。 |
| 1 | A4-1a/1b 文本字节安全 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` | render-time decoder 与 whole-file extractor 使用有效 `length/bytesRead`；只对白名单文本 MIME 探测；非 UTF TTML 改写 XML declaration；保留 raw bitmap 路径 | A4-J0 | 只回滚 Media3 subtitle decoder/extractor；建议优先合并。 |
| 2 | A4-J1 Cue 数据契约 | `6794d75b7a39db42dcfcab18c915f0da165515b5`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 增加 `collisionAvoidance`、`textRegionHeight`、`LetterSpacingSpan` 的 Builder/Bundle/equals/hashCode；确认已有 `bitmapHeight` 不被改名或改单位 | A4-1 可并行，但必须先于 UI 行为 | common/UI 数据模型独立回滚；此阶段默认不启用新 collision。 |
| 3 | A4-J2 viewport/scale 适配 | `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`、`6794d75b7a39db42dcfcab18c915f0da165515b5`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`、`aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | `PlayerView` 同步 video viewport；统一 text region、bitmap width/height、line/anchor 的缩放基准；保留 App 字号策略 | A4-J1；需要手机/电视留黑边截图 | `SubtitleView`/`PlayerView` UI 回滚；不回滚 parser 字段。 |
| 4 | A4-2a/2b ASS 运行时碰撞 | `6794d75b7a39db42dcfcab18c915f0da165515b5`（fork 对应 `9d7ea02aae18e03db0407e2146b50908acece81c`） | 解析 `Collisions: Reverse`、layer、margin 和 `UP/DOWN/NONE`；Canvas 按 z-index 在实际 viewport 避让；关闭或 feature-gate fork 的 `applyStacking()` | A4-J1、A4-J2 | ASS extractor + Canvas UI 独立回滚；禁止两套 stacking 同时启用。没有重叠 ASS 样片时可暂缓 2b。 |
| 5 | A4-3a/3b PGS parser 安全 | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`（fork 对应 `0aae318bded9b6790823d7ed7c9d1261f2af9344`） | 保存并执行 composition crop；设置正确 `bitmapHeight`；增加 4096 dimension cap、plane/crop/RLE 防御；补 `.sup` MIME 接线作为独立 3c | A4-J1；A4-J2 之前可做 parser，但宣布完成需联合截图 | PGS parser/外部 MIME 独立回滚；不影响 ASS/TTML。 |
| 6 | A4-4a/4b TTML region/spacing | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 解析 `tts:extent`、pixel/em font、letter spacing、`textRegionHeight`；把上游 group scaling 适配到 App 的字号开关；bitmap 高度同步缩放 | A4-J1、A4-J2；需确认 HLS/MP4/external TTML 的设计画布尺寸 | TTML parser/common 与 Canvas policy 分开回滚；没有可靠画布尺寸时只做数据模型，暂缓 pixel fallback。 |
| 7 | A4-3d PGS TS reader | `1b112bd1375c7a796cbde58d4c90226c7fc1947a`（fork 对应 `0351ac61d75ce71ffe062be9b57adad09eadc617`） | 允许合法无 alignment PES；沿用最近有效 PTS；覆盖一个 PES 多 display set、BDMV/M2TS 和 seek | PGS parser 3a/3b；真实 Blu-ray/M2TS 输入 | TS reader 单独回滚；无 M2TS 需求时延后。 |
| 8 | A4-3e 生命周期 | `ba27f889922a281162864a1260e7cb4e73ca0ecf`（fork 对应 `6895be9ae9b777bf8108df7f38d7c32d86fbd222`） | 播放期间保持 100 ms debounce；`setPlayer(null)`、detach、reset、换源和 release 使用立即清理；取消 runnable 不持有旧 View | PGS bitmap 已能显示；需要 Handler/生命周期测试 | `SubtitleView` 生命周期独立回滚；不能把所有空 cue 都改成立即清空，否则会重新引入 PGS 闪烁。 |
| 9 | A4-J3 混排验收 | 上述全部来源 commit；另记录 fork `63531ddcd508b646e0cf515df3bb6caf4835120e`、`56fd27d919504bfebd78172acb99cf7e3bc8f490`、`9d7ea02aae18e03db0407e2146b50908acece81c`、`0aae318bded9b6790823d7ed7c9d1261f2af9344`、`0351ac61d75ce71ffe062be9b57adad09eadc617`、`6895be9ae9b777bf8108df7f38d7c32d86fbd222` | 同一 APK/AAR 验证 ASS+TTML+PGS/DVB、字号变化、留黑边、换轨、seek、detach；生成可比较截图和 cue dump | 通过后才可把 A4 标为“可发布”；失败按 1--8 的最小阶段回滚。 |
| 10 | A4-1c/1d、A4-4c 可选 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | extraction-safe factory、按文本格式灰度 extraction、WebView CSS parity | 必须有 segmented text/外挂 bitmap/Canvas-WebView 真实需求和路由测试 | 默认暂缓；不得把 extraction 开关与 parser 修复绑在同一 AAR。 |

其中 A4-J1、A4-J2 是共享基础，不等于要把三个上游提交合并成一个巨型 cherry-pick。实际代码仍应按 common、extractor、ui、App 四个边界提交，发布版本通过依赖图保证顺序。

### 16.3 联合截图和自动化验收矩阵

#### 设备、布局和输出维度

至少保留以下组合的原始截图、cue dump 和日志，截图不能只在单一 16:9 手机上完成：

| 维度 | 最低组合 | 重点断言 |
| --- | --- | --- |
| 设备/窗口 | 手机竖屏、手机横屏、电视 16:9、21:9 或自定义 resize；原生 DV 与普通显示各一类 | `videoViewport` 与 `exo_content_frame` 一致；留黑边不改变 authored position；字号变化不越界 |
| 输出 view | Canvas（当前默认）、WebView（若启用） | Canvas collision/region scaling 可重复；未实施 CSS parity 时差异被记录，不伪称一致 |
| 字幕组合 | ASS bottom/top/middle + layer；TTML 多 region/`tts:extent`/letter spacing；PGS crop/非方形 bitmap；DVB；文本+位图+弹幕混排 | 文本 collision 只作用于允许移动的文本；位图不被上移；DanmakuView 层不参与字幕 z-index |
| 时序操作 | 首帧、连续 PGS display set、空 cue、seek、暂停/恢复、换轨、换源、`setPlayer(null)`、detach/reattach、横竖屏/窗口变化 | 连续 display set 不闪烁；换源/解绑无旧 bitmap；seek 后 PTS/cue 顺序稳定；pending runnable 不持有旧 View |
| 跨进程/状态 | Cue Bundle/Binder、MediaSession、进程重建（若产品支持） | `zIndex`、`collisionAvoidance`、`textRegionHeight`、`bitmapHeight`、LetterSpacingSpan 全部保留 |

#### 必须新增或补齐的测试

| 模块 | 测试建议 | 覆盖来源/风险 |
| --- | --- | --- |
| common Cue | `CueTest`：字段默认值、Builder、equals/hashCode、Bundle round-trip；`CustomSpanBundlerTest`：LetterSpacingSpan pixel/em/负值 | `6794d75b...`、`3c2cbe8...` |
| SSA extractor | `SsaParserTest`：Normal/Reverse、layer、MarginL/R/V、绝对 `\\pos/\\move`、drawing mode、无 PlayRes | `6794d75b...` 与现有 `9d7ea02a...` stacking 冲突 |
| Canvas UI | `CanvasSubtitleOutputTest`：UP/DOWN/NONE、z-index 分组、viewport、文本/位图混排；截图比较字号增减和留黑边 | `6794d75b...`、`92b1570a...` |
| PGS parser | raw SUP byte fixture：crop、4096/4097、RLE 截断、epoch reset、多个 object、clear display set、跨 sample 分片 | `aaddc2b9...`；当前没有专用 PgsParserTest |
| PGS TS | `PgsReaderTest`：无/有 alignment、最近 PTS、一个 PES 多 display set、TS/PES 跨边界、BDMV language | `1b112bd1...` 与 `0351ac61...` 状态机差异 |
| SubtitleView lifecycle | fake Handler/clock：100 ms debounce、非空取消、detach、reset、setPlayer(null)、release、reattach | `ba27f889...` 与 fork detach 缺口 |
| TTML | `TtmlParserTest`：`tts:extent`、Format 尺寸 fallback、pixel/em/normal/非法 letter spacing、region height；Canvas measure/draw 一致 | `3c2cbe8...` |
| 字符集 | `DelegatingSubtitleDecoderTest`、`SubtitleExtractorTest`：capacity>length、unknown length、GB18030/Big5/Windows-1252、TTML declaration、TX3G/MP4VTT/PGS 不转码 | `d82fb7b9...` |
| App 路由 | 外挂 `.sup` MIME、Canvas/WebView view type、字号设置、换源；确认 `parse=false` 时 bitmap raw sample 可达 | A4-1、A4-3c、A4-J2 |

#### 通过门槛

1. 同一输入在未启用新行为和启用新行为时，时间轴、track selection、MIME 和错误传播差异均有记录；不能只比较“画面看起来更好”。
2. ASS parser 不再提前改变 authored margin 后，Canvas collision 只在 `UP/DOWN` cue 上产生偏移；`NONE`、绝对定位和位图必须保持原位置。
3. PGS 的 crop、plane 边界和 `bitmapHeight` 在 16:9/21:9、字号变化和留黑边下都不改变对象实际宽高比例。
4. `setPlayer(null)`、detach 和 release 后旧 bitmap 立即不可见，正常连续 display set 仍保留 100 ms 防闪烁。
5. 任一阶段失败时，能够只替换该阶段的 AAR/App 层，不要求同时回滚 FFmpeg、MPV native 或其它 Exo 阶段。

### 16.4 Exo 依赖总实施表（供用户逐项决策）

下面汇总已经完成第一轮提交映射、且有足够语义证据的组。该表最初写于检查点 16，A5/A6 行已依据检查点 17-29 回写，A7 #67-69 已依据检查点 30 回写；A2 后半段和 A7 其余提交仍未完成同等深度审阅，不能据此直接整组合并。

| 总阶段 | 主要完整 commit ID | 当前 fork/本地基础 | 实施建议 | 决策状态 |
| --- | --- | --- | --- | --- |
| A0 重复项登记 | `ccc11523d57c3fd430c009b228c674a3195c9fdc`、`e8573d8c2ced07096c368d7ec3a40bc2e790d203`、`7feb08018a6e159330293de4878ebc3c9df2ca86`、`db13d7672f9bca525878292a54ae5e69c021f4c9` | 分别已由 `da1796da64acf3bd08aca4c3beff5c1ed09f9ccf`、`cccc786e5de5f861705adaba1b6ab760f484f2ee`、`b14c2dcc5899067f93496a82c200cfc719485da1`、`f24f5bef688ac62794014a9c275c49306aa27599` 覆盖 | 不产生代码变更；迁移清单保留来源 hash | 可直接确认 |
| C0/Exo FFmpeg 安全基线 | `177f090e0503b7e013922ca903bde14b1c375f18`；49 个父链提交已在检查点 6.2 逐项记录 | 当前 nextlib/FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`，保留 AV3A/软解负载 patch | 先只更新 Exo nextlib，NDK r28c 两 ABI 构建；通过后再让 MPV 使用同一源码 revision、分别构建 | **建议优先** |
| A1-1 HDR/parser safety | `f70e4b6f14d9f3b38ef953be80c53184f9c50bed`；`0cefd3ceec27444cf8faf02486b472bab39109fe` 的短 CSD/MP4 box hunk | DV descriptor/HEVC config 已有等价实现；缺 minimum mastering luminance 单位和畸形 box 防护 | 手工移植并补测试；不整体 cherry-pick `0cefd3ce...` | **建议合并** |
| A1-2 DV CSD/兼容 BL | `0cefd3ceec27444cf8faf02486b472bab39109fe` | App 有 DV7->P8.1 transformer，但当前只改 codec string，不重建 `csd-2` | 先修 P8.1 CSD，再传播 MP4/MKV/TS CSD；单独做厂商 codec 回归 | 条件合并 |
| A1-3 output policy/tone-map | `0cefd3ceec27444cf8faf02486b472bab39109fe` | 有自定义 `DolbyVisionHdr10FallbackRenderer` 和用户 DV7 选项 | 适配上游 output policy；保留原生优先、用户策略和会话锁定；不要整提交 | 用户决策 |
| A2 AV3A/软解 renderer | `d7083781e629ad1c4683a687261374065fb38925`、`2a2c8e8e122c13c0e462217f8fb5d7f0910cab97`、`ca7dd917ad574d4241640eb9282f20c5decd5aea`、`7d0d1e3c572aee885ffbbfd6d8317f1f3a581910`、`176e7f58ec3ba82cce3f5071b0a2625890e93b2d`；FFmpeg AV3A `23484688ad6ddda545f2380657c85ab1969d4b76` | 当前 fork/nextlib 已有 AV3A 与软解负载控制；上游还带第二套 native SDK/renderer 资产 | 只做 API/行为对照；不引入上游 SDK、第二套 renderer 或覆盖本地 nextlib patch | 暂缓，待 A5 后复核 |
| A3-1 低风险音频修复 | `1066f642a64434e7c3c0be687d3e94a4ca2815d7`（仅 Google/Pixel JOC guard）；`d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4`（14-bit DTS frame size） | 多 MIME/DTS 主体已有 `07cc217a1148f139af0c3480e6be05b082239516` 等价实现；缺两处小 hunk | 分两个提交和 AAR 验收；Google guard 必须验证 FFmpeg PCM fallback | **建议合并** |
| A3-2 E-AC3/TrueHD/Atmos | `1cc8573cab9e2453e7917aff1b8945482c8b2190` | fork 有旧版 E-AC3/TrueHD；本地 DV Matroska patch 依赖旧状态名 | 拆 parser/channel/sample count、TrueHD/rechunker、Matroska/MP4/fMP4 接线三阶段 | 条件合并 |
| A3-3 generic TS DTS probe | `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` | 当前 generic `0x82` 与 HDMV DTS 路由有 flag/API | 仅在有 generic TS `0x82` 样片时实施；需 App factory 与 M2TS 联动 | 条件合并 |
| A4-1 字幕字节/提取 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` | fork `63531ddcd508b646e0cf515df3bb6caf4835120e`；当前 extraction 默认关闭 | 先合并 1a/1b；safe factory 和 extraction 灰度独立、默认暂缓 | **建议合并 1a/1b** |
| A4-J 字幕联合 UI | `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`、`6794d75b7a39db42dcfcab18c915f0da165515b5`、`aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`、`1b112bd1375c7a796cbde58d4c90226c7fc1947a`、`ba27f889922a281162864a1260e7cb4e73ca0ecf`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | A4-J0--J3 已在本检查点拆出；fork 有自定义 stacking、bitmapHeight 和字号 API | 按 A4-J0--J3 顺序，先数据契约/viewport，再按需求启用 ASS collision、PGS、TTML；通过混排截图才发布 | **分阶段决策** |
| A5-0 已包含的 seek 修复 | `0957524dacb0caca8d24819619b9235487f27d4a`、`7c725b22f0b102e1447dd03dec557cc845db5049` | 分别由 fork `47b1843d84f12faa73390db00f56a3a384d9329e`、`8975884750e524ba4256fed83d9de2fa7e269b3d` 覆盖，且均为当前 AAR tag 的祖先 | 不重复合并；补 malformed edit-list 与“seek 超过短音轨尾部”自动化，迁移新基线时保留映射 | **跳过代码，保留回归** |
| A5 稳定性/seek/网络 | `eb51dfd700290c5b585026d2fa43a7241dd7b734`、`d160d770887785e3007ff2f1efa50160c2096152`、`f0eb7b514d5fcaba843dfe93d92acfff19a14e9e`、`a40e39880378c9129fbfb86601e7e69e0e48a946`、`a2fe56e7c9a40c894d465d47a424f4c07d1eb50a`、`a1e190005981febfa27e7583e5902d3cc2ce4ef7`、`aac6ec964681dd0476a33e3ad220ca7b5bf771f6`、`ccf962e8912695dc60ce82aa4470df899c6306a3` 等 | 检查点 17-23 已完成逐组深审；大量主体已由 fork 覆盖，剩余为 TS/HLS/MP4/Matroska 的窄 correctness、按样片功能和不合并项 | 严格按 A5 子阶段选择性移植；不使用单个上游 commit 覆盖本地代理、SAMPLE-AES、M2TS/RTSP 和字幕语义 | **分阶段决策** |
| A6 SMB/预载/RM/ASF | `32c20a091ba6e5fd09e13e67df3149326232eda5`、`dd00f94b58b7324ab29febb0b50f3a190d544a3b`、`4c3aa7d3293abaaeb0c4de49d73b12241d81d62c`、`0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b` | SMB/代理与 RM/ASF 主体已有；`PreCache` 是 App 自有状态机；RM/ASF decoder 未编入 nextlib | A6-1/A6-2 correctness 可选；并行预载默认关闭；RM/ASF 仅在真实样片和 decoder 闭环后启用 | **窄修复候选/其余暂缓** |
| A6 光盘/ISO | `990abc2368fd74779f525ee345734470659f3d53`、`5bca32949e0ad82cb0105962a7ae31234d6cd1a8`、`15d8d21f3354e6da48c5a47751a3edb943f9ffc6`、`4d713dded8f59cac265ec612dc263b1287bb08b4`、`bd3b52102a1dad1ef9d168165d0e8959fca5d03f`、`9a8c256cf14fdfce353dee039f6dd861185d7bfe`、`93af478b4cd2126c3844aaf2f813e24c0262eaf7` | App 已确认有 Exo/MPV 两条 ISO 入口；fork 有初版 UDF/BDMV/DVD/SACD/DSF/DFF，但缺 multi-extent、部分 DVD/DSD correctness 和 ISO 生命周期；MPV metadata 复用 Media3 parser | 按 A6-8a → A6-8b/c+C3 → A6-10/12 → A6-14 实施；A6-15 按 DSF/DFF 样片评估；DSD/DST decode 与 DV7 combine 独立决策；保留 `cumulativeOffsetUs` | **基础 correctness 分阶段候选** |
| A7-1/2 metadata 与轨道名称 | `7b787fe2a5616e684d9c0b77b8481724ada4afae`、`85add599da1230a62715a232ffa8e87d50638a3e` | fork `e4e4e5f1229f3398390df70eb157bd184d9bb7ff`、`e96590f7163eb420ceda7ae9748176bb7645c5af` 已含主体；App 真实使用 `artworkUri` 与 `DefaultTrackNameProvider` | 不合并整提交；补 artwork precedence/size 测试、`APPLICATION_MEDIA3_CUES` 原 MIME 名，TrueHD 常量随 A3-2；删除 `awr/awq` 误映射并修 59.94FPS 展示 | **窄修复候选** |
| A7-3 danmaku | `845f6fddd3953c36b08c2a878301649f918a1911` | fork `b7ae8eea1ae5af7d330327045da4ece3c224a5c9` + 当前 App/WebSocket 与 worktree live 性能增强 | 不整体合并 6616 行重写；若静态长视频性能确有瓶颈，另开 shadow/benchmark 阶段比较 segment/timeline/render-pool；provider parser 防御可逐文件移植 | **保持现状/独立实验** |
| A7 其余 UI/第二播放器/调试资产 | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8`、`7cca3b0bb5cbdccea639e602e713301d8116a99f`、`c3b25d5f4d6b4cc66c24b512defd8cd7084d2486`、`0f6191bc1bdd7324eef5e512cada65d9b974a6ed` 等 | 当前已有播放器 UI、诊断体系和 MPV 独立模块边界 | 不作为 Exo 第一轮依赖；有明确产品需求时另开阶段 | 待后续检查点/默认暂缓 |

### 16.5 Exo、FFmpeg 和后续 MPV 的交接规则

1. **先 Exo，后 MPV。** A1/A3/A4 以及 FFmpeg C0 的 Exo 构建通过前，不开始 MPV、mpv-android、libplacebo 的 native lock 更新。C0 虽是通用阶段，第一轮仍以 nextlib AAR 为验收载体。
2. **同一源码 revision 不等于同一二进制。** FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` 可在 Exo 和 MPV 共用源版本，但 Exo 使用 NDK r28c/`libav*`，MPV 使用 NDK r29/`libmv*` 重命名，必须分别构建、分别做 ABI/ELF/运行测试。
3. **通用 C1 随功能验收。** MMT/TLV、HLS live/timestamp、DV metadata 等通用语义只有在 Exo A5 和 MPV 对应 B 阶段各自有真实输入时才合并；不能因为 FFmpeg 已有 commit 就提前改变两条播放器的 API。
4. **C2 DV7->P8.1 暂不搭载。** FFmpeg `177f090...` 的 `dovi_rpu convert=p81` 继续作为实验候选，不能替代当前 Exo `DolbyVisionP81ExtractorsFactory` 或 MPV DV7 patch；待 A1-2/A1-3 与 B8 共同样片对照后再决策。
5. **A4 不改变 MPV 字幕链。** A4 的 Cue/SubtitleView 只影响 Media3/Exo；MPV 原生字幕仍在 B 阶段单独审计，不能把 A4 的截图结果外推到 MPV。
6. **C3 随 Exo 基础层先合入。** `IsoTrackMetadataResolver` 虽服务 MPV ISO 轨道语言，但依赖 Media3 `IsoFileEntry`；A6-8b/c 改多 extent 时必须在同一 App 提交中同步，不能等到 MPV native 阶段再补。

### 16.6 当前决策清单和下一检查点

建议用户先对以下最小集合做第一轮决策，避免一次性把所有后置功能带入：

- **可先实施：** FFmpeg C0（Exo nextlib）、A1-1、A3-1a、A3-1b、A4-1a、A4-1b，以及 A4-J0/A4-J1 的测试和数据契约准备。
- **需要样片后实施：** A1-2、A3-2a/b/c、A4-J2、A4-2b、A4-3a/b/d/e、A4-4b。
- **明确产品决策后实施：** A1-3、A3-3、A4-1c/1d、A4-3c、A4-4c、A6-3、A6-7、A6-11、A6-13b 和 A7。
- **A6 可先评估的 correctness：** A6-1、A6-2、A6-8a、A6-10a、A6-12a、A6-14a、A6-14c；A6-8b/c+C3、A6-10b/c+A6-12b 需要完整光盘镜像矩阵后联合实施。
- **当前跳过：** 已 patch-id/语义等价的 A0 项，以及 A2 中会引入第二套 native SDK/renderer 的上游提交。

下一检查点继续审阅 A5，优先顺序为：`0957524dacb0caca8d24819619b9235487f27d4a`（MP4 bad edit list）、`7c725b22f0b102e1447dd03dec557cc845db5049`（audio-shorter seek hang）、`eb51dfd700290c5b585026d2fa43a7241dd7b734`（TS sync detection）、`d160d770887785e3007ff2f1efa50160c2096152`（DASH manifest）和 `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e`（HLS edge cases）。这些提交会与当前 App 的代理 Range、预缓存、MediaSourceFactory 和本地播放修复逐 hunk 对照，完成后再更新 Exo 总表。

本检查点完成后，文档已把 A4 的共享依赖、阶段顺序、完整来源 hash、联合截图矩阵和 Exo 当前决策门槛落盘。后续每审完一个 A5/A6/B 组继续追加检查点，不覆盖历史结论。

## 检查点 17：2026-08-21 A5-0 MP4 错误 edit list 与短音轨 seek 收尾

本检查点联合审阅两个标题不同、但共同解决“视频仍有内容而音轨已经结束，seek 到尾部后播放等待”的提交：

- 上游 `0957524dacb0caca8d24819619b9235487f27d4a`：`Fix MP4 audio duration from bad edit lists`
- 上游 `7c725b22f0b102e1447dd03dec557cc845db5049`：`Fix seek hang when audio is shorter than video`

它们都已经进入当前 WebHTV Media3 fork，当前没有新的代码需要合并；真正缺口是没有针对这条联合失败链的自动化回归。

### 17.1 提交身份、patch-id 和当前树

| 上游完整 commit | 当前 fork 对应完整 commit | 等价性 | 当前结论 |
| --- | --- | --- | --- |
| `0957524dacb0caca8d24819619b9235487f27d4a` | `47b1843d84f12faa73390db00f56a3a384d9329e` | 稳定 patch-id 不同：上游 `174b85762244b0dc096abc520ce5e026bdd12485`，fork `6935c57ead9bf8f9a0dab995dc3d6978fd303ab5`；逐行差异只有 `if` 条件换行和上下文签名，新增判断、常量和返回语义相同 | **语义/最终行为等价，跳过上游提交** |
| `7c725b22f0b102e1447dd03dec557cc845db5049` | `8975884750e524ba4256fed83d9de2fa7e269b3d` | 稳定 patch-id 均为 `799d35f95f5935dbbdd34d96eb2acf833ecf7543` | **精确等价，跳过上游提交** |

两个 fork commit 都是当前 `e3e922d5c01bc0b564849940fe589daf37360d15` 的祖先，也包含在 tag `media3-1.11.0-alpha01-fongmi-20260705` 中，所以当前发布 AAR 已经具备这两项行为。上游目标头 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` 仍保留相同实现，没有后续反转。

当前 `third_party/sources/media` 工作树中：

- `BoxParser.hasBadAudioEdit()` 和 `Mp4Extractor` 的音轨 duration clamp 没有用户未提交覆盖。
- `Mp4Extractor.java` 的未提交差异只涉及本地 ITU-T T.35 metadata track 读取优先级，不触及 `processMoovAtom()` 的音轨 duration 逻辑。
- `SampleQueue.java` 没有未提交差异；短音轨 EOS hunk 仍原样存在。

### 17.2 为什么两个提交应视为同一个相关功能

失败链如下：

1. 某些非分片 MP4 的单条 audio edit list 声称的播放区间长于实际 audio media/sample table，导致音轨上报一个不存在的尾部时长。
2. 视频轨真实时长更长，用户 seek 到音频实际尾部之后仍是合法视频位置；`ProgressiveMediaPeriod` 会给每个 `SampleQueue` 设置新的 `startTimeUs`。
3. 对“所有 sample 都是 sync sample”的音频，`SampleQueue` 在写入侧丢弃所有早于 seek 位置的样本。如果最后一个 audio sample 也早于 `startTimeUs`，旧逻辑连 `BUFFER_FLAG_LAST_SAMPLE` 一起丢掉，空队列既不 ready，也不知道轨道已结束。
4. 音频 renderer 等待后续 sample/loading 完成，表现为尾部 seek 长时间卡住、缓冲不结束，视频也可能无法恢复。

`0957524d...` 从 MP4 容器源头修正虚假的 audio duration；`7c725b22...` 在通用 `SampleQueue` 层保证“合法短音轨”或其它容器仍能产生 EOS。只保留前者不能覆盖真实短音轨、HLS/RTSP/chunk 等输入；只保留后者虽能解除 hang，但 UI/track duration 仍可能被错误 edit list 污染。因此它们应归为同一个 A5-0 阶段，但仍保留两个独立来源 hash。

### 17.3 两个修复的精确语义

#### MP4 bad edit list

`BoxParser.hasBadAudioEdit()` 只在以下条件全部成立时忽略 edit：

- track type 是 audio；
- 只有一条 edit；
- `editMediaTime` 不是 `-1`，且小于实际 media duration；
- 把 edit duration 换算到 media timescale 后，超过 `mediaDuration - editStartTime + 2 timescale units`。

命中后，普通 sample table 和 `FLAG_OMIT_TRACK_SAMPLE_TABLE` 路径都使用未编辑的实际 media duration，并保持原 sample timestamps。`Mp4Extractor.processMoovAtom()` 还会在 audio 的 sample-table duration 比声明 track duration 短超过 1000 us 时，把该 track output duration 收窄到 sample table；媒体总 duration 仍取所有已暴露轨道的最大值，较长视频不会被音频缩短。

该实现不是“忽略所有 audio edit list”：多 edit、empty edit（media time `-1`）、合法 trim、误差在容忍范围内、非 audio track 都继续走原有 edit-list 逻辑。

#### 短音轨 EOS

`SampleQueue.sampleMetadata()` 只在写入侧 discard 开启、sample timestamp 早于 `startTimeUs`、且该 sample 带 `BUFFER_FLAG_LAST_SAMPLE` 时设置 `isLastSampleQueued=true`，随后仍不提交被丢弃的 sample。`isReady(false)` 和 `read(..., loadingFinished=false)` 因此能立即返回 EOS。

写入侧 discard 只对 audio 且 `MimeTypes.allSamplesAreSyncSamples()` 为 true 的格式启用；视频、需要 keyframe/preroll 的流不会因为这个 hunk 被提前截断。`reset()` 会清除 `isLastSampleQueued`，正常换源/重新 load 不继承旧 EOS。

### 17.4 收益、风险和当前项目接线

收益：

- 普通 MP4/MOV 中过长或损坏的 audio edit 不再制造“幽灵音频时长”。
- seek 到较长视频尾部时，较短 AAC/AC3/E-AC3 等同步音频能够立即结束，不等待 loader 的额外状态变化。
- `SampleQueue` 防护同时覆盖 Progressive、HLS、RTSP 和 chunk-based source 的同类短音轨场景，不局限于 MP4。

风险与边界：

- 单条 audio edit 若有意要求在媒体尾部补静音，但时长超过实际 samples，会被视为 bad edit。Media3 本来也不会仅凭 edit list 合成尾部静音；当前选择是使用实际媒体时长，兼容目标合理，但需保留合法 edit 边界测试。
- 1000 us 的 track-duration clamp 和 2 timescale-unit 的 edit 容忍度单位不同；低 timescale、极短音频和 rounding 边界必须测试，不能把两个常量合成一个。
- `BUFFER_FLAG_LAST_SAMPLE` 是上游 extractor/source 对“不会再有 sample”的承诺。若某个自定义 source 错误地在中途标记 last sample，该队列会按契约提前 EOS；这不是该 hunk 新造的问题，但自定义 source 测试应覆盖。
- `BoxParser` 也被 `FragmentedMp4Extractor` 调用，但 `Mp4Extractor` 的 1000 us track output clamp 只在非分片 extractor 中。fMP4 仍应靠其 edit-list 和 fragment duration 逻辑单独验证，不能用普通 MP4 样片外推。

当前 App 的 `MediaSourceFactory` 使用 `DefaultExtractorsFactory` 再包 `DolbyVisionP81ExtractorsFactory`，普通 MP4 会到达这套 `BoxParser`/`Mp4Extractor`。本地 `PlaybackBytePositionDataSource`、cache、`HttpEofRecoveryDataSource` 和 seek UI 的 pending-position hold 都不会替代 renderer EOS；因此这两个底层修复对当前播放链是实际可达的。App UI 的 hold 只稳定进度显示，不能解决旧版音轨 queue 永久不 ready。

### 17.5 缺失测试和最低验收矩阵

上游两个提交及其 fork 对应提交都没有增加单元测试。仓库虽有 `sample_shorter_audio.mp4`，它来自 Transformer 的音频补静音测试，不直接覆盖播放 seek hang；现有 `SampleQueueTest.setStartTimeUs_allSamplesAreSyncSamples_discardsOnWriteSide()` 也没有“最后 sample 仍早于 start time”的分支。

| 层 | 必补场景 | 断言 |
| --- | --- | --- |
| `BoxParser` | audio 单 edit 超出剩余 media duration 2 units 以上 | 忽略 edit；timestamps 保持；duration 等于真实 media/sample table |
| `BoxParser` 边界 | 正好等于、仅超 1/2 units、超 3 units；media time `-1`；start>=media duration；多 edit；video edit | 只有严格超过容忍度的单 audio edit 被忽略，合法 edit 不回归 |
| omit sample table | 同一个 bad edit，`omitTrackSampleTable=true/false` | 两路径上报一致的真实 audio duration |
| `Mp4Extractor` | 声明 track duration 比 sample table 长 999/1000/1001 us；视频更长 | 仅超过 1000 us 时 clamp；总媒体 duration 仍为较长视频 |
| `SampleQueue` | audio all-sync；`startTimeUs` 晚于最后 sample；最后 sample 带 LAST | sample 被丢弃但 queue 立即 ready；先读 Format，再读 EOS；`isLastSampleQueued=true` |
| `SampleQueue` 对照 | 同条件但非 LAST；LAST sample 位于/晚于 start；reset 后重用 | 非 LAST 不伪造 EOS；可达 LAST 正常提交；reset 清旧状态 |
| 播放集成 | 普通 MP4：视频长于音频，seek 到音频尾部后和视频尾部前；坏 edit/无 edit 两种 | 不 hang、不无限 buffering；视频继续或自然结束；position/discontinuity 事件合理 |
| 其它 source | HLS/RTSP/progressive audio 短于 video，最后 sample 在 seek 前 | 通用 EOS 生效，无需依赖 MP4 duration 修复 |
| A3 联合 | E-AC3 dependent、TrueHD/Atmos MP4，启用 A3-2 前后 | 新 sample grouping/channel analysis 不改变真实尾部 EOS 和 track duration |

建议增加 `SampleQueueTest` 的直接单元 case，并用一个小型可生成的 malformed MP4 fixture 或 atom builder 测 `BoxParser`，不要只保留不可解释的大二进制样片。播放集成测试至少执行 seek 后等待 `STATE_READY/STATE_ENDED`，并设置超时证明没有 hang。

### 17.6 最终决策和回滚边界

**两个上游提交均不需要合并代码。** `7c725b22...` 精确等价，`0957524d...` 为仅格式化不同的语义等价。迁移到 `release-1.11.0-fongmi` 新基线时，应把 fork `47b1843d84...`、`8975884750...` 标为由上游替代，不能再次 cherry-pick。

A5-0 的当前动作是“保留现有实现 + 补回归测试”，不需要发布新的 AAR，也不触碰 FFmpeg、MPV、mpv-android 或 libplacebo。若新增测试暴露合法 edit 被错误忽略，可只调整 `hasBadAudioEdit()` 判定；若 SampleQueue EOS 出现 source 契约问题，可只回滚 `8975884750...` 对应 hunk，两者无需与其它 A5 阶段同回滚。

下一检查点继续 A5：先审 `eb51dfd700290c5b585026d2fa43a7241dd7b734` 的 TS sync detection，再联合 `d160d770887785e3007ff2f1efa50160c2096152`、`f0eb7b514d5fcaba843dfe93d92acfff19a14e9e` 的 DASH/HLS 边界和当前代理/缓存链。

## 检查点 18：2026-08-21 A5-1/A5-2/A5-3 TS、DASH 与 HLS 边界收尾

本检查点把上一检查点承诺的三个相关功能组落盘。审阅对象是上游 `FongMi/media@release-1.11.0-fongmi` 的完整提交，比较了 WebHTV fork 已发布历史、当前 `third_party/sources/media` dirty 工作树、`third_party/media-lock.json` 中已经 cherry-pick 的修复，以及 App 的 `MediaSourceFactory`、`PreCache` 和 M3U8 代理。没有修改源码、AAR、native `.so` 或 lock。

### 18.1 A5-1：TS sync detection（普通 TS、HLS 与 M2TS 共用边界）

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `eb51dfd700290c5b585026d2fa43a7241dd7b734` | `d0722906d26fa9c6707df9e720d83d36dcb0a356` | 已在当前 `e3e922d5c01bc0b564849940fe589daf37360d15` 祖先链；语义等价，格式化及后续 `ParserException`/M2TS 上下文不同 |

上游将 TS sync 搜索抽成 `TsUtil.tryToFindSyncBytePosition(...)`，固定检查 `TsUtil.SNIFF_TS_PACKET_COUNT = 5` 个 packet；`TsExtractor.sniff()` 因而读取 `5 + 3` 个 packet 的范围，而不是只依赖单个前置位置。`M2tsExtractor` 复用同一 helper，并以 192-byte packet、188-byte header 偏移处理 M2TS。HLS 模式下找不到 sync byte 不再走旧的“立即把容器判为 malformed”路径。

当前 App 已在 `MediaSourceFactory` 设置 `TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10`，`DefaultExtractorsFactory` 也启用了 HDMV DTS；这条 helper 对 HLS、188-byte 普通 TS 和 192-byte M2TS 都可达。因此再次 cherry-pick 会制造重复冲突，没有代码合并收益。

**实施阶段 A5-1：保留实现，补回归，不发布新依赖。**

收益是短 TS、前置垃圾字节、非零 sync 起点和 M2TS sniff 的容错更一致；风险主要是扩大 sniff 窗口带来的起播读取量，以及短输入到 EOF 时是否被错误地视为 malformed。必须保留当前 fork 后续的 `ParserException` 上下文，不以整提交覆盖。

最低测试矩阵：

| 输入 | 断言 |
| --- | --- |
| 少于 5 个完整 packet、空输入、EOF 在 packet 中间 | 不死循环；返回正常 sniff 失败或上层可识别的 EOF 错误 |
| 前置垃圾 + 188-byte TS，sync 在第 1/2/5 个 packet | 能找到 sync；不会把垃圾当 payload |
| 错误 sync byte、周期性伪 sync、188-byte 间距不稳定 | 不误判为合法 TS |
| 192-byte M2TS（188-byte header 偏移） | 正确识别 packet 起点；不把 4-byte 时间戳当 TS header |
| HLS extractor、seek 后重新 sniff、variant 切换 | 不因一次 sniff 失败永久污染 extractor 状态 |

验收时还要测起播内存/首帧延迟，确认 App 自己的 `PlaybackBytePositionDataSource`、cache 和 Range wrapper 没有把扩大窗口放大成额外网络请求。回滚边界仅为 TS sniff 测试或对应 helper hunk，不触及 HLS 错误处理和 M2TS reader。

### 18.2 A5-2：DASH manifest、TrackGroup 回退与空 SegmentTimeline

#### 18.2.1 普通 DASH manifest 提交

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `d160d770887785e3007ff2f1efa50160c2096152` | `6702e73bada0d9f32301b5717cc4d71d0ae4b849` | 主要语义已存在于当前 fork，且为当前 tag 祖先；不整提交 cherry-pick |

该提交有两组相互独立的改动：

1. `DashMediaPeriod` 在 TrackGroup 对象不相等时，先按 track `type` 与 `Format.id` 回退匹配。这能覆盖 manifest 重建或 representation 对象重新实例化后，旧 selection 与新 TrackGroup identity 不一致的情况。
2. `DashManifestParser` 对缺失/过短 `default_KID` 生成随机 UUID，并让 `SegmentList`、`SegmentTemplate`、`Event`、`SegmentTimeline` 的 duration 解析同时接受 ISO-8601 duration 与逗号小数格式，通过 `parseTicks()` 转换到 timescale。

**阶段 A5-2a：空 timeline 防崩溃，低风险，保留并先验收。**

`8465e107ece2bb26d5fb45d8d302559fc8783fd8` 已经记录在 `third_party/media-lock.json` 的既有 cherry-pick 列表，当前 dirty `third_party/sources/media` 也包含 `DashMediaPeriod`、`DefaultDashChunkSource` 和空 `<SegmentTimeline/>` fixture/test。它的行为是：选择第一个非空 representation；old/new segment index 为空时保护 chunk selection；增加空 timeline 测试。它不是新的独立升级，不能再次列为待合并 commit，也不能覆盖用户未提交的工作树。

**阶段 A5-2b：TrackGroup fallback，条件合并。**

收益是减少动态 MPD 更新、representation 重建和 track selection 恢复时的“找不到原 TrackGroup”失败。风险是同一 `type + Format.id` 重复时可能返回 unset 或错误组，进而让选择器静默切换到错误 representation。应先构造重复 id、id 缺失、type 改变、语言/role 改变和周期切换样片，验证 selection、禁用/启用 track、position discontinuity 和 DRM session 是否正确，再决定是否把对应 hunk 带入新的 fork 基线。

**阶段 A5-2c：duration/ISO-8601 解析，样片后合并。**

收益是兼容非标准但常见的 DASH duration（例如 `PT1.5S` 和逗号小数），可避免 segment timeline 时间轴错位。风险包括 timescale 换算精度、整数溢出、负 duration、逗号小数与旧数字格式的兼容，以及 long/BigDecimal 边界。测试必须覆盖 `timescale=1/1000/90000`、零/负值、超大值、`1.5` 与 `1,5`、ISO-8601 hour/minute/fraction、segment/event/template 三条解析路径，并对照实际 chunk start/end。

**阶段 A5-2d：`default_KID` 随机 UUID，默认暂缓。**

随机生成 KID 能让缺损 manifest 继续通过 parser，但会伪造 DRM 标识，可能使 license 请求指向不存在的 key。当前 App 的 `MediaSourceFactory` DRM setter 实际未接入，项目也没有明确 DRM 需求；因此不能因为 parser 容错而默认启用。只有产品明确需要“缺 KID 的加密 DASH 继续进入自定义 license 流程”时，才应单独实现，并记录随机 KID 不可用于真实解密的限制。

当前 App 的 OkHttp、CacheDataSource、Range、预缓存和 DASH manifest 路由必须在 A5-2b/c 的样片中联合回归；parser 单测通过不等于代理重写后的 MPD 仍有同一 timescale/period 语义。

### 18.3 A5-3：HLS playback edge cases

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e` | `f7cdcaeea1345dd2b777dcc8bf7578ac97a24d89` | 五个主要 hunk 已存在；`Aes128DataSource` 文件精确相同，其余受 fork 格式/API/本地 patch 影响，不能按整提交合并 |

逐 hunk 结论如下：

1. **AES key 截取最多 16 bytes：低风险候选。** `Aes128DataSource` 对超长 key 只取前 16 bytes，能兼容把 ASCII/hex 或服务端填充误传为 17+ bytes 的 HLS。必须测试恰好 16、17、短 key 和 key rotation；短 key 仍应失败，不能默认为合法 key。该 hunk 可独立移植。
2. **默认关闭未在 playlist 声明的 CEA-608：暂不默认启用。** 它可能减少无字幕流中的伪 track，但也可能隐藏实际存在而未声明的 closed captions。当前 App 有字幕样式和 Caption 设置，却没有独立的 CEA-608 路由保障；需先用真实 TS/HLS 样片确认字幕可见性。
3. **不再按 playlist `codecs` 忽略 AAC/H264：条件合并。** 可兼容错误声明的 HLS playlist，但可能引入重复/错误 track 或让 extractor 暴露无法解码的轨道。须结合实际 variant、muxed audio/video、track selection 和 fallback 样片验证，不能只做 parser 单测。
4. **`HlsMediaChunk.load()` 把所有 `IOException` 当 gap：高风险，整体拒绝。** 这会吞掉 404、403、认证失败、代理失败、网络断开、解密失败和损坏 segment，绕过当前 `HttpEofRecoveryDataSource`、CacheDataSource 的错误标记、重试和 telemetry。若后续确有需求，只能按明确的 segment 404/playlist gap 分类处理，并保留取消、错误事件和重试语义。
5. **`HlsSampleStreamWrapper.MAPPABLE_TYPES = emptySet()`：条件合并。** 配合 elementary PID 可避免多个同类型 track 冲突，但必须验证 muxed audio/video、metadata、variant 切换和 extractor 重建，尤其是字幕/ID3/CEA-608 的 track identity。
6. **TS track ID 使用 elementary PID：与 A5-1 联合评估。** 可能改善多个同类型 PID 的稳定映射；必须与 188/192-byte sniff、HDMV reader、PUSI/seek 和 HLS variant 切换一起测试。不能单独替换 `TsExtractor` 的 ID 策略。

**阶段 A5-3 实施顺序：** 先独立验证/移植 AES 16-byte key hunk；其次以真实 HLS 样片评估 codec 声明和 PID track identity；CEA-608 默认策略需产品决定；`IOException -> gap` hunk 默认不合并。所有 HLS hunk 都必须在 App 自己的 M3U8 代理（会重写 playlist、传播 Range 并显式返回上游错误）和 `PreCache` 两条路径验证。

### 18.4 A5 联合阶段表与决策门槛

| 阶段 | 来源 commit | 代码动作 | 依赖/验收 | 当前建议 |
| --- | --- | --- | --- | --- |
| A5-1 TS sync | `eb51dfd700290c5b585026d2fa43a7241dd7b734`；fork `d0722906d26fa9c6707df9e720d83d36dcb0a356` | 不重复合并；保留 fork 实现，补 TS/M2TS/HLS sniff 回归 | 188/192-byte、垃圾前缀、短输入、seek/variant、起播内存 | **保留+测试** |
| A5-2a 空 SegmentTimeline | `8465e107ece2bb26d5fb45d8d302559fc8783fd8`（lock 已有） | 不新增 cherry-pick；保护当前 dirty 实现和 fixture | 空 old/new index、首个非空 representation、MPD 更新 | **已有，先验收** |
| A5-2b TrackGroup fallback | `d160d770887785e3007ff2f1efa50160c2096152`；fork `6702e73bada0d9f32301b5717cc4d71d0ae4b849` | 只在新基线按 hunk 对照移植 | 重复 Format.id、周期/语言/role/DRM/selection | **条件合并** |
| A5-2c duration parser | 同上 | 只移植 parser hunk | ISO/逗号小数、timescale、溢出/负值、chunk 时间轴 | **样片后合并** |
| A5-2d random KID | 同上 | 默认不移植 | 加密 MPD、license/KID 语义、缺失字段 | **暂缓** |
| A5-3a AES key | `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e`；fork `f7cdcaeea1345dd2b777dcc8bf7578ac97a24d89` | 可单独移植最多 16-byte 截取 | 16/17/短 key、rotation、代理/cache | **低风险候选** |
| A5-3b codec/PID/CEA | 同上 | 按 hunk 分别评估 | muxed、variant、字幕、track selection | **条件/产品决策** |
| A5-3c IOException gap | 同上 | 不移植整体行为 | 404/403/认证/断网/解密/重试/telemetry | **拒绝** |

### 18.5 本检查点后的 Exo 覆盖进度

`media` 的 A5 已完成 A5-0（MP4 edit/EOS）、A5-1（TS sync）、A5-2（DASH/空 timeline）和 A5-3（HLS edge cases）的第一轮逐 hunk 审阅。覆盖进度从“2 项”更新为“6 个可实施子阶段/组已审阅”；FLV、Matroska 其它提交、M2TS framing、RTSP/MP2T、HLS ad/SAMPLE-AES 和 A6/A7 仍未完成深审。

下一检查点继续 A5，顺序为：

1. `a40e39880378c9129fbfb86601e7e69e0e48a946`（M2TS framing/seek）；
2. `a2fe56e7c9a40c894d465d47a424f4c07d1eb50a`（RTSP/MP2T）；
3. `db8f68c8d8990d84b68cca3bcbc0538e10744a14`、`9b535ed30b9fa7e8580264036de1a12115daba32`（FLV）；
4. `624167c2a0eaf9af94011e0a556aaf91a15fb25f`、`e25ef9864fce33f0d149820bd7999b30aff1a44d`（Matroska）；
5. `13fbfd88d312de6c4f10fedd2b085cb2710b88ae`、`a1e190005981febfa27e7583e5902d3cc2ce4ef7`（HLS ad/SAMPLE-AES）。

完成这些 A5 子组后，再由用户决定 Exo 的最小合并集合；在此之前不进入 MPV 依赖的成套实施。

## 检查点 19：2026-08-21 A5-4 M2TS/RTSP/FLV/Matroska/HLS 广告过滤初审，以及 SAMPLE-AES 待定项

本检查点继续只审 Media3/Exo 依赖，未改动 `third_party/sources/media` 的用户未提交修改、`third_party/media-lock.json`、AAR、native `.so` 或应用源码。审阅采用上游完整 commit、当前 WebHTV fork 的对应祖先、当前 App 的播放接线三方对照。结论先按“已经在 fork 中存在的实现”和“上游增强但当前尚未最终判定的语义”拆开，避免把同一修复重复 cherry-pick。

### 19.1 M2TS framing、seek 与 Blu-ray 输入

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `a40e39880378c9129fbfb86601e7e69e0e48a946` | `db07c8e05e082043c6c6c0d9151637777196d1e5` | 主要语义已在当前 fork；不整提交合并 |

上游提交的表面主题是 M2TS 支持，但当前 fork 不只是复制普通 192-byte packet framing。当前实现已经包含：

- HDMV DTS flag 处理；
- 192-byte M2TS packet（4-byte 时间戳头 + 188-byte TS）识别和读取；
- CRC 容错；
- M2TS constant-bitrate seek 与 seek point 计算；
- `BdmvTsExtractor` 对 Blu-ray ISO 的 CLPI EP-map seek、STC 累积，以及 Dolby Vision/HDR 输出适配。

`DefaultExtractorsFactory` 已注册 `M2tsExtractor`，但 Blu-ray ISO 路径会进入另一套 `BdmvTsExtractor`。因此只用一个普通 `.m2ts` 文件验证通过，不能证明 Blu-ray 原盘路径没有回归；也不能用上游提交覆盖当前 fork 的 Blu-ray、本地 Dolby Vision 和 seek 增强。

**阶段 A5-4a：保留现有实现，补 M2TS/Blu-ray 回归，不重复合并。**

收益是对前置垃圾、192-byte packet、constant-bitrate seek 和盘内 EP-map seek 的容错统一。主要风险是 packet size/offset 判断错误会把 4-byte timestamp 当成 TS header，或者在截断文件上错误地宣称可 seek；HDMV DTS flag 和 Dolby Vision metadata 的输出路径也不能由普通 TS 样片外推。

最低验收矩阵：

| 输入/场景 | 断言 |
| --- | --- |
| 正常 192-byte M2TS、188-byte TS、前置非 packet 字节 | 正确识别 packet 起点，payload 不包含 4-byte M2TS 头 |
| 截断在 4-byte 头、188-byte payload 中间、多个 packet 之间 | 不死循环；返回明确 EOF/读取失败；不产生虚假的完整 sample |
| seek 到开头、中间、尾部和 constant-bitrate 边界 | seek point、时间戳、下一关键帧一致；不会跳过一个 packet 或重复输出 |
| HDMV DTS flag 与普通 DTS | channel/sample timestamp 正确，未设置 flag 的流不被误判 |
| Blu-ray ISO + CLPI EP-map | `BdmvTsExtractor` 的 EP-map seek、STC 累积与普通 M2TS 结果一致 |
| Dolby Vision/HDR M2TS/Blu-ray | RPU/色彩 metadata 仍按当前本地输出适配，不被上游 extractor 替换 |

回滚边界是 M2TS sniff/reader 或对应测试；不得为了回滚普通 M2TS 行为删除 `BdmvTsExtractor` 的盘内 seek 与 DV/HDR patch。

### 19.2 RTSP redirect、RTP/MP2T、SMIL 与节目回看

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `a2fe56e7c9a40c894d465d47a424f4c07d1eb50a` | `26e0476ecff0e5ab8836e667ab775ee87e7a5b95` | 主要实现已在当前 fork；不整提交合并，转为集成验收 |

这组提交不是孤立的 RTP reader 修复，而是从 SDP 到播放器控制面的完整链路：

- 支持 RTP payload type 33 / MP2T；
- 新增 `RtpMp2tReader`，把 RTP 中的 TS 交给 `TsExtractor` 解析；
- RTP 重排超时按视频 33 ms、音频 20 ms 区分；
- RTSP redirect 上限、重连和取消路径；
- SMIL `<video>`/`<ref src>` 解析；
- `clock=...` range 以及 `rtsp_range` request metadata；
- 多 sample queue、MP2T track；
- SDP 容错和 TCP interleaved buffer 修复。

当前项目确实使用 `media3-exoplayer-rtsp`：`MediaSourceFactory` 采用 `DefaultMediaSourceFactory`，`LiveApi` 会为 RTSP 写入 `rtsp_range`，MediaItem request metadata 已进入 Exo 链。因此该功能对项目是实际可达的，不应因为“当前 fork 已有实现”而跳过验证。

**阶段 A5-4b：保留 fork RTSP/MP2T 实现，补 App 级联调。**

收益是 RTSP 直播、节目回看和 RTP-over-TCP/UDP 输入的覆盖面更完整；风险集中在 redirect loop、UDP 丢包/乱序、TCP interleaved 分帧、seek 取消以及鉴权失败被错误重连。`rtsp_range` 是应用自己的回看约定，不能只验证 Exo parser 能否解析 `clock`。

最低验收矩阵：

| 输入/场景 | 断言 |
| --- | --- |
| UDP RTP/RTCP + payload type 33 | TS PID、音视频 track、时间戳和首帧正常 |
| RTP-over-TCP interleaved | 多 frame 粘包/拆包、非 RTP channel、连接关闭均不串流 |
| 单跳 redirect、redirect 到 SMIL、redirect loop/超过上限 | 合法跳转成功；循环明确失败；取消不留下后台连接 |
| `clock=start-end`、`rtsp_range` 回看 | 请求 Range 与首个 sample 时间一致；seek/position discontinuity 合理 |
| 多音轨/多视频轨 | track selection 不丢失、不把相同 PID 合并成错误 track |
| 鉴权、断线、重连、用户取消 | 错误可见；不把 401/403 当成成功 gap；取消后无继续读 socket |
| App cache/预缓存与 RTSP | 不把不可 seek 的 live session 错当可预缓存文件；Range metadata 不被 wrapper 丢弃 |

该阶段不需要再次 cherry-pick `a2fe56e...`。若新基线缺少某个 hunk，应按 redirect、MP2T reader、metadata 和 interleaved buffer 四个小组分别迁移，不能用整提交覆盖当前 RTSP 本地修复。

### 19.3 FLV late-track、HEVC 与 seek 后重建

#### 19.3.1 Track discovery

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `db8f68c8d8990d84b68cca3bcbc0538e10744a14` | `8e5036dffd8a1d85944524bceee55f7689d11983` | 已有对应实现；不整提交合并 |

该提交修正 FLV header 的音视频存在标志不可信的问题。extractor 不再在 header 阶段立即 `endTracks()`，而是等实际读到 audio/video tag 后再结束 track discovery；seek 时会重置相应 reader。当前 fork 已有这套 late-track 语义。

#### 19.3.2 HEVC FLV

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `9b535ed30b9fa7e8580264036de1a12115daba32` | `0f8fe0c7c7dccb03048913b2459e5fe91181a60c` | 已有对应实现；不整提交合并 |

当前 fork 同时包含：

- codec id 12 的 HEVC FLV 识别；
- sequence header 变化延迟到下一个 keyframe；
- 零长度 NAL 跳过；
- 空 tag 与异常 header length 防护；
- seek 后重新建立 audio/video reader。

**阶段 A5-4c：保留 FLV 实现，补错误 header、late-track 和编码器切换回归。**

收益是兼容错误声明音视频标志、直播中晚到的 track、HEVC/AVC sequence header 更新以及非 keyframe 起播。风险是过早 `endTracks()` 会让晚到 track 永久丢失，过晚结束则可能影响首帧；sequence header 若在非 keyframe 应用，会造成解码器状态混乱；seek 后 reader 未重置会重复或跳过 tag。

最低验收矩阵：

- header 错报 audio/video、只报一种但后续出现另一种 track；
- EOF 前后 `endTracks()` 只调用一次，晚到 track 能建立；
- H.264/HEVC sequence header 首次出现、变化、变化后第一个非 keyframe 和下一个 keyframe；
- 零长度 NAL、空 tag、异常/超长 header length、截断 tag；
- 非 keyframe 起始与 seek 后重新建 track；
- audio-only、video-only、AVC、HEVC 以及混合切换；
- App 的 progressive cache/Range wrapper 不会让 FLV seek 误用旧 reader 状态。

若迁移到新 Media3 基线，只需按 late-track、HEVC codec、sequence-header、reader reset 四组 hunk 对照；不应再次 cherry-pick 两个完整上游提交。

### 19.4 Matroska EBML resync 与旧 FourCC 映射

| 功能 | 上游完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 非法 EBML varint resync | `624167c2a0eaf9af94011e0a556aaf91a15fb25f` | `12a10ec5114c0fdaf6eaed4634ad87a7f0a19da1` | 已覆盖，不整提交合并 |
| 旧视频 FourCC 映射 | `e25ef9864fce33f0d149820bd7999b30aff1a44d` | `7f7960736cb1eba9dc871c378bc0be0f175fff01` | 已覆盖，不整提交合并 |

resync 修复在遇到非法 EBML varint 时，不再立即崩溃，而是尝试定位下一个一级元素；FourCC 修复把 `DIVX`、`DX50`、`XVID`、`FMP4` 映射到 `VIDEO_MP4V`。当前 fork 已包含两组语义，且另有本地 Matroska Dolby Vision RPU patch，不能用上游文件覆盖。

**阶段 A5-4d：保留 Matroska 实现，补损坏输入和旧编码器回归。**

最低验收矩阵：

- 非法 varint、截断 EBML、损坏字节后存在合法一级元素；
- resync 成功后能继续解析且不会死循环或无限跳过；
- 损坏数据位于 cluster、block、codec private 和 cue 附近时的错误边界；
- `DIVX`/`DX50`/`XVID`/`FMP4` 与标准 MPEG-4 Visual 的解码 MIME、初始化数据和 seek；
- Dolby Vision RPU patch 在正常和损坏 Matroska 上仍按当前项目约定输出；
- 音视频 track、字幕、章节和 duration 不因 resync 被错位。

此阶段的风险不是新增 API，而是容错扫描可能吞掉真实元素、增加坏文件读取量，或在损坏输入上形成循环。测试需设置最大扫描窗口和超时断言。

### 19.5 HLS 广告过滤：已是 Exo/MPV 共用功能，不应重复合并

| 项目 | 完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `13fbfd88d312de6c4f10fedd2b085cb2710b88ae` | `47e9fa0922a581d46d685e8384b8cf4d4f75e208` | 代码已存在，且同时接入 Exo 与 MPV；不再次合并 |

当前项目已有完整的接线：

- `MediaItem.adblock` 保存设置；
- `HlsAdsParser` 过滤 playlist；
- Exo `HlsPlaylistParser` 通过 parser factory 接收 adblock；
- `ExoUtil` 根据 App 设置写入 MediaItem；
- `MpvHlsProxy` 也调用 `HlsAdsParser.process()`，所以这不是只属于 Exo 的功能。

当前 `HlsAdsParser` 只处理带 `#EXT-X-ENDLIST` 的 VOD，依据路径/文件名少数群组和 discontinuity block heuristics 删除广告片段。它不是通用 SCTE-35/EXT-X-DATERANGE 广告决策器。

**阶段 C-HLS-AD：在播放器合并阶段前置做一次共用验收；不要在 Exo 和 MPV 各自重复 cherry-pick。**

收益是减少 VOD 广告片段、让 Exo 直链和 MPV HLS proxy 行为尽量一致。风险包括路径误判、同一 CDN 混用、变量 segment、byterange/key/map/discontinuity 关联被错误重建，以及把 live playlist 当成可过滤 VOD。

最低验收矩阵：

- adblock 开启/关闭；VOD（有 ENDLIST）与 live（无 ENDLIST）；
- 多 CDN、相同文件名但不同路径、查询参数和 URL 编码；
- discontinuity 前后媒体序列、`EXT-X-KEY`、`EXT-X-MAP`、byterange、独立 init segment；
- variable segment、低延迟 HLS、playlist reload；
- Exo 直链与 `MpvHlsProxy` 两条路径的 segment 数、时间轴、首帧和 seek；
- 过滤后没有残留引用到已删除的 key/map/range，也没有把合法节目片段误删；
- 过滤失败时回退原 playlist，并保留代理错误、缓存和 telemetry。

若发现误判，优先修正 `HlsAdsParser` 的识别/重建逻辑并为两条播放器路径共用测试；不要把上游整提交再引入一遍。

### 19.6 HLS SAMPLE-AES：当前有旧版实现，但增强语义尚未最终判定

| 项目 | 完整 commit | 当前状态 | 判定 |
| --- | --- | --- | --- |
| 上游提交 | `a1e190005981febfa27e7583e5902d3cc2ce4ef7` | 当前 fork 已有较早的 SAMPLE-AES identity 链，但未证明与该提交全部等价 | **暂不合并，继续逐 hunk 深审** |

当前源码已经具备的旧链路包括：

- `HlsPlaylistParser` 解析 `METHOD=SAMPLE-AES`；
- `HlsMediaPlaylist` 保存 sample AES key URI；
- `HlsChunkSource` 下载 key；
- `HlsMediaChunk` 保存 key/IV，并使用 `SampleAesExtractorOutput` 解密；
- `DefaultHlsExtractorFactory` 支持 identity-encrypted TS；
- 现有 `SampleAesExtractorOutput` 主要覆盖 H.264/AAC。

上游 `a1e190...` 的变化是一次 extractor/decryption 契约重构，不能只看文件名判断已合并：

- 新增 `HlsMediaChunkExtractor.setSampleAesDecryptionData()`；
- `BundledHlsMediaChunkExtractor` 向 `TsExtractor` 传递 key/IV；
- extractor reuse 时严格比较 key/IV；
- TS stream type 映射；
- `HlsSampleAesExtractorOutput` 移入 extractor/ts 包；
- 增加 AC3/E-AC3/JOC SAMPLE-AES；
- key/IV 长度校验和更完整的 H.264/AAC/AC3 解密；
- downloader 也下载 sample encryption key。

当前不能直接得出“应合并”或“无需合并”的结论，原因是项目已有旧版 identity 链和本地 `SampleAesExtractorOutput`/`media3-upstream-playback-fixes-2026-08.patch` 改动；整提交 cherry-pick 很可能覆盖本地改动，且会改变 HLS extractor reuse、缓存和代理时序。

**阶段 A5-5：SAMPLE-AES 差异审计与样片门槛。** 先逐 hunk 比较旧实现和 `a1e190...`，再决定是否只迁移 AC3/E-AC3/JOC、key/IV 校验或 extractor reuse 语义。不得在差异未收敛前发布新的 AAR。

必须回答的产品/技术问题：

- 项目是否实际需要 AC3/E-AC3/JOC SAMPLE-AES，而不仅是 H.264/AAC；
- key rotation 时同一 extractor 的 key/IV 是否能安全切换，旧 key 是否可能泄漏到下一个 chunk；
- variant 切换、seek、缓存命中和 `MpvHlsProxy` 是否保留正确的 key URI/IV；
- TS stream type `0xC1/0xC2/0xCF/0xDB` 在当前 fork 中是否已正确映射；
- 解密失败、短 key、非 16-byte IV、取消下载是否继续通过 `HttpEofRecoveryDataSource`/CacheDataSource 正确上报，而不是被当作 gap；
- HLS playlist 代理重写后，key URI、相对路径和 Range 是否仍保持原语义。

最低样片/单测矩阵：

| 场景 | 断言 |
| --- | --- |
| H.264/AAC identity SAMPLE-AES、16-byte key/IV | 与当前行为一致，首帧、seek、variant 切换不回归 |
| AC3/E-AC3/JOC SAMPLE-AES | 只在解码器和输出链明确支持时暴露 track；否则明确失败而非静默输出损坏音频 |
| key rotation、同 URI 不同 key、同 key 不同 IV | extractor reuse 只在 key/IV 完全兼容时复用；切换后不使用旧 cipher state |
| 短/长 key、错误 IV、key 404/403、解密失败 | 错误可见、重试/取消语义正确，不伪装成普通 HLS gap |
| seek、换 variant、cache hit/miss、预缓存 | key 下载、sample 解密和时间轴一致；不重复下载或读错 key |
| Exo 直链与 MPV HLS proxy | 两条路径的 key URI/相对 URL、缓存和错误传播一致 |

在该阶段结束前，`a1e190005981febfa27e7583e5902d3cc2ce4ef7` 只登记为“待定增强”，不列入 Exo 最小可合并集合。

### 19.7 本检查点实施总表

| 阶段 | 来源 commit | 当前动作 | 建议 |
| --- | --- | --- | --- |
| A5-4a M2TS/Blu-ray | `a40e39880378c9129fbfb86601e7e69e0e48a946`；fork `db07c8e05e082043c6c6c0d9151637777196d1e5` | 保留现有实现；补 192-byte、截断、seek、EP-map、DTS、DV/HDR 测试 | **不重复合并** |
| A5-4b RTSP/MP2T | `a2fe56e7c9a40c894d465d47a424f4c07d1eb50a`；fork `26e0476ecff0e5ab8836e667ab775ee87e7a5b95` | 保留现有实现；补 redirect、UDP/TCP、clock/range、track、鉴权/取消测试 | **不重复合并，重点联调** |
| A5-4c FLV | `db8f68c8d8990d84b68cca3bcbc0538e10744a14`、`9b535ed30b9fa7e8580264036de1a12115daba32`；fork `8e5036dffd8a1d85944524bceee55f7689d11983`、`0f8fe0c7c7dccb03048913b2459e5fe91181a60c` | 保留 late-track/HEVC/reader reset；补错误 header、sequence header、seek 测试 | **不重复合并** |
| A5-4d Matroska | `624167c2a0eaf9af94011e0a556aaf91a15fb25f`、`e25ef9864fce33f0d149820bd7999b30aff1a44d`；fork `12a10ec5114c0fdaf6eaed4634ad87a7f0a19da1`、`7f7960736cb1eba9dc871c378bc0be0f175fff01` | 保留 resync/FourCC；补损坏 EBML、旧 DivX/Xvid/FMP4、DV patch 测试 | **不重复合并** |
| C-HLS-AD | `13fbfd88d312de6c4f10fedd2b085cb2710b88ae`；fork `47e9fa0922a581d46d685e8384b8cf4d4f75e208` | 作为 Exo/MPV 共用能力一次验收；不重复 cherry-pick | **共用阶段，条件保留** |
| A5-5 SAMPLE-AES | `a1e190005981febfa27e7583e5902d3cc2ce4ef7` | 深审旧实现与新 extractor 契约；先做 AC3/E-AC3/key rotation/代理样片 | **暂缓决策** |

截至本检查点，A5 已覆盖 A5-0、A5-1、A5-2、A5-3 以及 A5-4 的 M2TS、RTSP/MP2T、FLV、Matroska 初审；HLS 广告过滤已识别为 Exo/MPV 共用功能。Exo 仍未进入最终合并阶段，下一步优先完成 A5-5 SAMPLE-AES 的逐 hunk 对照，并审阅剩余 A5 提交（包括 Matroska chapter ordering `938f9958a0756554f8d641315ce626b67efe2143`），然后更新“Exo 最小合并集合”和“需用户决策集合”。在用户确认 Exo 集合前，不开始 MPV native lock/AAR 重建。

## 检查点 20：2026-08-21 A5-5 SAMPLE-AES 逐 hunk 结论与 Matroska chapter ordering

本检查点完成了 `a1e190005981febfa27e7583e5902d3cc2ce4ef7` 的第一轮逐 hunk 对照，并补审了 Matroska 章节排序提交。仍只追加审计文档，没有修改源码、锁文件、AAR 或 native 二进制；当前 `third_party/sources/media` 的 dirty 改动保持不变。

### 20.1 SAMPLE-AES 提交身份与当前 fork

| 项目 | 完整 commit | 父提交/当前 fork | 判定 |
| --- | --- | --- | --- |
| 上游 SAMPLE-AES identity 重构 | `a1e190005981febfa27e7583e5902d3cc2ce4ef7` | 上游父提交 `13fbfd88d312de6c4f10fedd2b085cb2710b88ae`；当前 fork 基线 `e3e922d5c01bc0b564849940fe589daf37360d15` | **不是整提交等价；需选择性迁移或暂缓** |
| 当前较早实现 | `e3e922d5c01bc0b564849940fe589daf37360d15` | `third_party/media-lock.json` 当前锁定 | H.264/AAC identity SAMPLE-AES 已接通，但能力范围较窄 |

当前 fork 的实现链是：playlist parser 保存 `sampleAesEncryptionKeyUri` → `HlsChunkSource` 使用既有 key cache 下载 key → `HlsMediaChunk` 以 key/IV 固定创建 `SampleAesExtractorOutput` → `DefaultHlsExtractorFactory` 在 TS URL 情况下强制选择 TS extractor。`TsExtractor`/`DefaultTsPayloadReaderFactory` 已识别 `0xCF` AAC 和 `0xDB` H.264 SAMPLE-AES stream type，因此 H.264/AAC 不是“只解析不解密”，而是确实有逐 sample 解密路径。

但上游提交不是简单重命名。它把解密 output 移到 `extractor/ts/HlsSampleAesExtractorOutput`，由可复用的 `TsExtractor` 持有并动态更新 key/IV；同时将 `0xC1`、`0xC2`、`0xCF`、`0xDB` 统一映射到普通 AC3/E-AC3/AAC/H.264 reader，并新增 AC3/E-AC3/JOC 解密、严格 key/IV 校验、SAMPLE-DATA part 处理和 offline key 下载。

### 20.2 逐 hunk 差异与实际影响

#### 20.2.1 解密挂载点和 extractor reuse

当前 fork 在每个 SAMPLE-AES chunk 初始化一个固定 key/IV 的 `SampleAesExtractorOutput`。只有前后 chunk 的 key 与 IV 完全相等时，才允许复用 extractor；key rotation 会创建新 extractor。上游则让 `TsExtractor` 在 `init()` 时挂接一个持久的 `HlsSampleAesExtractorOutput`，随后通过 `setHlsSampleAesDecryptionData()`/`setDecryptionData()` 更新 key/IV，允许同一 TS extractor 继续处理连续 chunk。

收益：

- 同一 variant 的 key rotation 不必重建 TS reader/track output，减少 track identity 变化和重新探测；
- 可在 extractor 已初始化后清除或替换解密数据；
- 解密状态集中在 TS stream type 与 output wrapper，HLS chunk 层不再包裹一套固定 cipher。

风险/兼容点：

- 上游 `TsExtractor.java` 的目标上下文同时移除了当前 fork 的 M2TS packet、HDMV DTS/TrueHD/LPCM、CRC 容错、Dolby Vision descriptor 和本地 seek 逻辑；不能用整文件覆盖；
- 当前严格 key/IV 相等判断虽然牺牲了复用效率，但不会把旧 key 带入新 chunk，属于安全保守行为；
- 若只移植“允许复用”的条件而不移植可变 decryption output，会重新引入旧 key 解密风险；
- key rotation 时重新建 extractor 是否会导致 App 的 `HlsSampleStreamWrapper` 重复建 track、丢失 splice 或产生短暂 discontinuity，需要真实样片确认。

**阶段 A5-5a：暂不移植 extractor reuse；先保留严格相等策略，补 key rotation/variant/seek 回归。** 只有在样片证明重建 extractor 造成可见故障，或项目明确要求高频 key rotation/低延迟 HLS，才考虑选择性迁移“可变 decryption output + 安全更新”两组 hunk。

#### 20.2.2 H.264/AAC 解密算法边界

当前 fork 对 H.264 和 AAC 的基本 CBC pattern 与上游方向一致，包含 NAL unescape/escape、H.264 每 160-byte pattern 的 16-byte 加密块和 AAC 前 16-byte clear 区。但实现细节仍不同：

- 当前 wrapper 的 key/IV 是构造时引用，未做 16-byte 长度校验和防御性复制；
- 当前 `sampleData(..., sampleDataPart)` 忽略 `SampleDataPart`，会把 supplemental/non-main 数据也纳入 pending buffer；上游只缓存 `SAMPLE_DATA_PART_MAIN`；
- 当前在 metadata 引用范围与 pending buffer 不一致时会“原样 flush 且继续提交”，可能静默绕过解密；上游对 offset/size 做状态断言；
- 当前只按 MIME 判断 H.264/AAC，未知音视频 MIME 可能把密文原样交给 decoder；上游对不支持的音视频 SAMPLE-AES 明确抛出 `UnsupportedOperationException`；
- 当前每个 sample/每个 NAL 使用固定 IV，适合现有 H.264/AAC 路径，但不能外推到一个 sample 内含多个 AC3 frame 的 CBC 链。

这些差异意味着“当前已有 H.264/AAC”不能等同于“可安全替换上游实现”。它们分别影响数据完整性、错误可见性和内存/track output 行为。

#### 20.2.3 AC3/E-AC3/JOC 与 `0xC1/0xC2`

当前 fork 的 `TsExtractor` 只定义/处理 `0xCF`、`0xDB` 两种 SAMPLE-AES stream type；标准 AC3/E-AC3 stream type 会建立普通 `Ac3Reader`，但当前 `SampleAesExtractorOutput.shouldDecrypt*()` 不包含 `AUDIO_AC3`、`AUDIO_E_AC3` 或 `AUDIO_E_AC3_JOC`，因此对应密文音频会被原样转发，不能视为已支持。

上游新增：

- `TS_STREAM_TYPE_HLS_SAMPLE_AES_AC3 = 0xC1`；
- `TS_STREAM_TYPE_HLS_SAMPLE_AES_E_AC3 = 0xC2`；
- `TS_STREAM_TYPE_HLS_SAMPLE_AES_AAC = 0xCF`；
- `TS_STREAM_TYPE_HLS_SAMPLE_AES_H264 = 0xDB`；
- `TsExtractor.getHlsSampleAesStreamType()` 将四种 HLS code point 映射到普通 AC3/E-AC3/AAC/H.264 reader；
- `HlsSampleAesExtractorOutput` 对 AC3/E-AC3/JOC 按 syncframe 逐帧解密，并在同一 sample 内推进 CBC IV。

这组增强对包含 Dolby Digital/Plus 的 Apple HLS 有实际价值，但项目当前没有清晰的 SAMPLE-AES AC3/E-AC3 样片或产品需求证据。不能仅因为普通 E-AC3 renderer 已存在，就假定 SAMPLE-AES E-AC3 必须加入。

**阶段 A5-5b：AC3/E-AC3/JOC 为样片后候选。** 若无样片，保持当前 H.264/AAC 路径并明确标注“不支持加密 AC3/E-AC3”；若有样片，优先只迁移 stream-type mapping、AC3 frame decryption 和 unsupported-MIME fail-fast，并保留本地 M2TS/HDMV/DV/CRC 代码。

#### 20.2.4 key/IV 校验、rotation、缓存与 offline downloader

上游在 output 层强制 key 与 IV 均为 16 bytes，并复制数组；当前 key cache 只按 URI 缓存原始 bytes，`HlsMediaChunk` 在使用时没有等价长度断言。短 key、长 key、空 IV、key 服务器返回错误内容时，当前行为可能延迟到 JCE 异常或产生难以诊断的播放错误。

上游 `HlsDownloader.addSegment()` 还把 `sampleEncryptionKeyUri` 加入离线下载列表。当前 App 主要使用 `PreCacheHelper`/自有 CacheDataSource，不是 Media3 `HlsDownloader` 的完整离线 API；因此该 hunk 不应默认列入在线播放器合并，但若未来启用 HlsDownloader，必须一并下载 sample key，否则离线 playlist 可解析却无法解密。

**阶段 A5-5c：先移植/补测试 key/IV 校验，offline downloader 按产品需求单独决定。** 校验属于低风险防护，但要确认不会破坏服务端返回带 BOM、尾部填充或代理重写的现有 key；不能复用 A5-3 的“超长 AES key 截取”思路，SAMPLE-AES 应严格要求 16 bytes。

#### 20.2.5 parser 字段拆分和 HLS 代理

上游将 `fullSegmentEncryptionIV` 与 `identityEncryptionIV`、`sampleEncryptionKeyUri` 分开维护，在 segment/part/preload hint 的 IV 推导中同时判断 full-segment key 和 sample key。当前 fork 已有 `sampleAesEncryptionKeyUri`，在线 parser 的主要语义已经接通，但字段仍与 AES-128 状态共享，且部分路径使用“full key 优先，否则 sample key”的旧式选择。

对当前 App，真正的风险在 playlist 代理：`MpvHlsProxy` 会重写/过滤 HLS playlist，Exo `MediaSourceFactory` 还会经过 CacheDataSource、Range 和 `HttpEofRecoveryDataSource`。key URI 的相对路径、IV 默认使用 media sequence、variant 切换和 key rotation 必须在原始直链、Exo 代理/缓存、MPV proxy 三条组合路径上保持一致。

#### 20.2.6 unsupported stream 和错误传播

上游对不支持的音视频 SAMPLE-AES 主动失败；当前 HlsMediaChunk 还保留 A5-3 中“捕获所有 `IOException` 当作 gap”的本地行为。两者不能混为一个修复：

- MIME 不支持属于确定性能力错误，应可见且可诊断；
- key 404/403、解密失败、代理断开属于网络/安全错误，不应被静默当作 gap；
- 若迁移上游 fail-fast hunk，必须保留当前 `HttpEofRecoveryDataSource`、CacheDataSource 错误标记、重试和 telemetry。

### 20.3 SAMPLE-AES 实施阶段与最低验收矩阵

| 阶段 | 来源 commit/hunk | 代码动作 | 当前建议 |
| --- | --- | --- | --- |
| A5-5a extractor reuse | `a1e190005981febfa27e7583e5902d3cc2ce4ef7`；当前 `e3e922d5...` | 先保留严格 key/IV 相等才复用；补 rotation/variant/seek | **暂缓迁移** |
| A5-5b AC3/E-AC3/JOC | 同上（`0xC1/0xC2/0xCF/0xDB` + AC3 decrypt） | 有样片再按 hunk 迁移，保留本地 TS 增强 | **条件合并** |
| A5-5c key/IV validation | 同上 | 严格 16-byte 校验与数组复制，补错误传播测试 | **低风险候选** |
| A5-5d SampleDataPart/unsupported MIME | 同上 | 只缓存 MAIN；不支持音视频明确失败 | **建议随 A5-5b 一起做** |
| A5-5e offline key download | 同上 | 仅在启用 HlsDownloader 时添加 key DataSpec | **产品需求后** |
| A5-5f parser/代理 | 同上 | 对照字段拆分；补相对 key URI、默认 IV、rotation、代理/cache | **先测试，后决定** |

最低测试矩阵：

| 场景 | 断言 |
| --- | --- |
| H.264/AAC identity SAMPLE-AES，16-byte key/IV | 与现有路径一致；首帧、连续 chunk、seek、variant 切换正常 |
| `0xCF`/`0xDB` 与普通 `0x0F`/`0x1B` | HLS code point 只改变解密标识，不改变 MIME、PID/track 和时间轴 |
| `0xC1` AC3、`0xC2` E-AC3、JOC | 有样片时逐 syncframe 解密正确；无支持时显式失败，不输出密文噪声 |
| 一个 sample 多个 AC3 frame | CBC IV 在 frame 间正确推进，下一 sample 重新使用 segment IV 规则 |
| key rotation（同 URI 新内容、不同 URI、不同 IV） | 不使用旧 key；当前严格重建 extractor 或新 output 更新均可验证 |
| key/IV 长度 0/15/16/17/32 bytes | 16-byte 才接受；错误在 key 使用前可见，不能静默截断 |
| `SampleDataPart` supplemental/metadata | 只解密 MAIN；supplemental/ID3/字幕不被拼入 pending sample |
| key 404/403、解密失败、取消、断网 | 错误/取消/重试可见，不走“普通 gap”静默路径 |
| Exo 直链、CacheDataSource/PreCache、MpvHlsProxy | key URI、相对路径、IV、缓存命中和错误传播一致 |
| HlsDownloader（若启用） | sample key 被纳入下载集合，离线播放不缺 key |

**A5-5 当前结论：** 不整合 `a1e190...`；当前最小可实施动作是保留现有 H.264/AAC 路径、补 key/IV 和 `SampleDataPart`/错误回归，并以 AC3/E-AC3 样片决定是否选择性迁移。迁移时必须拆开 `TsExtractor` HLS mapping/output hunk 与本地 M2TS/HDMV/DV/CRC/seek 代码，禁止整文件替换。

### 20.4 Matroska chapter ordering

| 项目 | 上游完整 commit | 当前 fork 对应 | 判定 |
| --- | --- | --- | --- |
| 按开始时间排序章节 | `938f9958a0756554f8d641315ce626b67efe2143` | `827cee3b2d5bddc4f90b38e2fc108256351f805b` | 语义等价，当前 fork 已有，不重复合并 |

上游只在 `MatroskaExtractor.maybeAddChaptersMetadata()` 写入 Format metadata 前，对收集到的 `Chapter` 列表按 `startTimeMs` 升序排序。当前 fork 的 `827cee3b...` 已包含同一行；本地 Matroska Dolby Vision RPU BlockAdditional patch 与该逻辑无重叠。

收益是修正 EBML chapter entry 的读取/ID 顺序与用户时间轴顺序不一致时的章节显示和跳转；风险很小，但同一开始时间的章节仍依赖稳定排序，按 track UID 过滤后章节集合也可能与原作者意图不同。该排序不应被外推为“chapter/edition API 已完成”。

**阶段 A5-6：保留排序，补章节回归，不发布新依赖。** 测试应覆盖乱序 start、相同 start、重叠/隐藏章节、track-specific chapter、无章节和带 DV RPU 的 Matroska；验证 metadata 顺序、UI 时间轴和 seek 目标一致。

### 20.5 检查点 20 后的状态

- A5-5 SAMPLE-AES 从“尚未最终结论”更新为“已完成第一轮逐 hunk 审计，当前不整合，AC3/E-AC3/key validation/part handling 为分阶段候选”。
- A5-6 chapter ordering 已确认当前 fork 等价覆盖。
- 当前 Exo 最小合并集合暂不增加 `a1e190...` 或 `938f995...`；下一步继续审阅 `b11a22289694611da2450688d9b6407ba75625bc`、`08c664eb8a213a956ff2c8b3d0fcea49902a81fa`、`2d4ab61e69c74796f529bf8f9cab60c68b340d4d`、`65ee9ba81815e67c9d3d08a2be0028859cc20569` 等低耦合 A5 提交，并同步更新 Exo 阶段总表。

## 检查点 21：2026-08-21 A5-7 MPEG-1 PS、content-type、file URI 与 OkHttp 接线

本检查点完成了四个低耦合提交的逐文件/逐 hunk 对照。仍然只更新审计文档，没有修改 Media3 源码、`third_party/media-lock.json`、AAR、native `.so` 或用户在 `third_party/sources/media` 中留下的 dirty 改动。

### 21.1 提交身份与当前 fork 映射

| 功能 | 上游完整 commit | 当前 fork 对应 commit | 覆盖状态 |
| --- | --- | --- | --- |
| MPEG-1 PS parsing | `b11a22289694611da2450688d9b6407ba75625bc` | `4d0c9cb78c3975fdffbf9d7e88efd3276d2d9ec3` | 语义已覆盖；fork 还叠加 DVD/private-stream、注入 seek map 等本地改动 |
| content type handling | `2d4ab61e69c74796f529bf8f9cab60c68b340d4d` | `e6bf7f5e78fc003433a53285f2bc0c8036571d79` | 大部分已覆盖，但 upstream 的无扩展名末段 `m3u8`/`mpd` 分支尚未见于 fork |
| case-insensitive file URI | `65ee9ba81815e67c9d3d08a2be0028859cc20569` | `71262cce9d228daf95592bcbbab0b9ac3fbd8ae5` | patch-id 精确等价，已覆盖 |
| OkHttp integration | `7709a03d55c6eaaf999c18f0d4ab9fc9141b7ead` | `4b7640ea550f91507818860e311bea206a688187` | 当前 fork 已有对应 Range/header/cleartext 改动；App 已直接使用该 OkHttp 链 |

短 hash 仅用于导航；后续合并或审计记录必须使用上表中的完整 hash。前三个 fork commit 可由当前 media 仓库直接解析，且 `65ee9...` 与 `71262...` 的稳定 patch-id 相同。MPEG-1/content-type/OkHttp 的 patch-id 因上下游父树和格式上下文不同不能直接作为唯一等价判据，应以行为和当前文件为准。

### 21.2 MPEG-1 PS：不重复合并，转入输入回归阶段

`b11a222...` 横跨 `H262Reader`、`MpegAudioReader`、`PsBinarySearchSeeker`、`PsDurationReader` 和 `PsExtractor`，核心行为为：

- 识别 MPEG-1 pack header（固定 8-byte header、无 MPEG-2 stuffing），并与 MPEG-2 PS sniff/skip 分支区分；
- 解析 MPEG-1 SCR、PES stuffing/STD buffer、PTS/DTS，且没有时间戳时保留上一音频 sample timeline；
- 将 MPEG-1 视频输出为 `VIDEO_MPEG`，MPEG-2 仍为 `VIDEO_MPEG2`，并在 MPEG-2 sequence extension 存在时才应用扩展帧率；
- 修正 `PsExtractor` 无 PTS 时初始化为 `C.TIME_UNSET`，避免把缺失时间戳伪装成 0。

当前 `4d0c9cb...` 已包含上述全部功能。工作树中的 `PsExtractor` 还存在 fork 本地的 DVD private stream reader、外部 seek map、DTS/TrueHD/LPCM 等扩展；这些不是上游 MPEG-1 提交的理由，不能用 upstream 文件覆盖当前文件。当前没有在该提交中发现新增的 MPEG-1 专项测试，因此“已覆盖”不等于“已验收”。

**阶段 A5-7a：保留现有实现，不 cherry-pick `b11a222...`。** 只补输入/回归测试：

- MPEG-1 PS（含无 stuffing、带 stuffing 的 MPEG-2 PS）sniff、pack skip、SCR duration 和 binary seek；
- MPEG-1 PES 的无 PTS、PTS、PTS+DTS、stuffing/STD buffer、`0x0F` header，以及音频时间轴连续性；
- 视频 MIME 为 `video/mpeg`，MPEG-2 仍为 `video/mpeg2`，帧率扩展只作用于 MPEG-2；
- 混合或损坏 pack、截断 header、错误 marker bits 必须失败或结束，不得越界/死循环；
- 当前 DVD/private stream、注入 seek map、M2TS/HDMV 路径的既有测试继续通过。

收益是覆盖老 MPEG/VOB/PS 文件而不引入新的代码冲突；风险主要是 fork 本地 reader 与 MPEG-1 的 stream-id/时间戳判断交互，以及错误 sniff 让普通二进制被误认成 PS。回滚边界为 extractor 模块和对应测试，不涉及 FFmpeg/MPV native。

### 21.3 content-type handling：分离已覆盖部分与一个窄候选

上游 `2d4ab61...` 新增了 `MimeTypes.APPLICATION_OCTET_STREAM`，并在 `Util.inferContentType(Uri)` 中处理：

1. `data:` URI：以 `application/dash+xml` 开头判 DASH，其余 data URI 按 HLS 处理；
2. URL 中包含 `=m3u8`/`=mpd` 的动态接口；
3. 无常规扩展名时，末段恰为 `m3u8` 或 `mpd`；
4. `.php` 作为 HLS 的扩展名。

fork `e6bf7f5...` 已包含 1、2、4 和 `APPLICATION_OCTET_STREAM`；当前 `Util.java` 未发现上游第 3 项的 `else` 分支。因此这不是整提交缺失，而是一个很窄的 content-type 候选。它对 App 的价值在于某些重写/代理 URL 使用 `/m3u8` 或 `/mpd` 作为最后路径段但没有点号；对已有 `MediaSourceFactory`、`MediaItem.mimeType` 和 `MpvHlsProxy` 的常规 URL 不会产生明显收益。

**阶段 A5-7b：先不整合 `2d4ab61...`；按需选择性补末段分支。** 实施前须确认：

- `https://host/path/m3u8`、`/mpd`、大小写变体、查询参数和重定向的推断结果；
- 无扩展名的真实媒体文件不会被误判成 manifest；
- `data:` URI 的 MIME 大小写、base64/percent 编码和空 payload；
- App 通过 Exo 直链、CacheDataSource/PreCache、HLS 代理和 MPV proxy 时，content type 与 `DataSpec`/Range 行为一致；
- `APPLICATION_OCTET_STREAM` 只作为错误恢复/显式 MIME 使用，不应让未知二进制自动走 HLS。

如果样本只需要 `/m3u8`/`/mpd`，应单独移植 6 行左右的分支并加 `UtilTest`，保持可独立回滚；不要重复引入已经存在的 data/query/php 逻辑。

### 21.4 case-insensitive file URI：已覆盖，仅补回归

上游 `65ee9ba...` 只在 `FileTypes.inferFileTypeFromUri()` 将最后路径段用 `Locale.US` 小写化。fork `71262cce...` 与上游稳定 patch-id 精确相等，当前源码也保留该逻辑。因此不再 cherry-pick。

**阶段 A5-7c：** 补 `.MP4`、`.M3U8`、`.TS`、混合大小写扩展、查询参数、无扩展名和 Unicode/Locale 回归；验证 `FileTypes` 与 `Util.inferContentTypeForExtension` 的职责不要混淆。该阶段风险低，失败只回滚 common 测试/小修复，不牵涉 native。

### 21.5 OkHttp integration：代码已存在，不能直接照搬 demo 行为

上游 `7709a03...` 的有效代码改动为：

- `HttpUtil.buildRangeRequestHeader(position, length, force)` 增加强制 Range 选项；
- `OkHttpDataSource` 在 `CONTENT_TYPE_OTHER` 时强制发 Range，并用 `header()` 替代 `addHeader()`，避免重复 Range/User-Agent/Accept-Encoding；
- read 跳过逻辑去掉多余的提前 `return`；
- demo manifest 开启 cleartext traffic。

fork `4b7640ea...` 已含同一组 Media3 datasource 改动，且当前 App manifest 已有 `android:usesCleartextTraffic="true"`。更重要的是 App 的实际链是：

`OkHttpDataSource.Factory(OkHttp.player()) → DefaultDataSource → HttpEofRecoveryDataSource → CacheDataSource/PriorityTaskDataSource`。

所以该提交不是缺失依赖，而是现有 Exo 网络语义的基础。直接再次 cherry-pick 会造成空操作或冲突；demo manifest 不应被当作 App 代码合并目标。

**阶段 A5-7d：** 只做网络行为验收，必要时再做局部修正：

- unknown/progressive URL 的 `Range: bytes=0-`、已有 Range、非零 seek 和 206/200 响应；
- HLS/DASH manifest、segment、key、M2TS/MP4 的 Range 与 content type；
- 重复请求头、用户自定义 Range/Cookie/User-Agent 的覆盖优先级；
- gzip/identity、redirect、取消、EOF 重连和 CacheDataSource 命中/错误回退；
- `HttpEofRecoveryDataSource` 不把真实 HTTP/解密错误误判为普通 gap；
- 明文 HTTP 仅按 App 的网络安全策略验证，不能因为 upstream demo 开启 cleartext 就扩大生产权限。

风险是强制 Range 会触发某些服务器对 `bytes=0-` 返回 416/错误 206，`header()` 也可能改变重复 header 的覆盖顺序；收益是 opaque URL 的 progressive seek 和服务器分片响应更稳定。回滚边界是 datasource 模块/App datasource 接线，不涉及 extractor 或 native。

### 21.6 四项联合实施表

| 阶段 | 上游 commit | 当前 fork/状态 | 实施动作 | 建议 |
| --- | --- | --- | --- | --- |
| A5-7a MPEG-1 PS | `b11a22289694611da2450688d9b6407ba75625bc` | `4d0c9cb78c3975fdffbf9d7e88efd3276d2d9ec3` 已覆盖 | 仅补 MPEG-1/VOB/PS 样片和错误输入回归，保留本地 PS 增强 | 不重复合并 |
| A5-7b content type | `2d4ab61e69c74796f529bf8f9cab60c68b340d4d` | `e6bf7f5e78fc003433a53285f2bc0c8036571d79` 部分覆盖 | 只在真实 `/m3u8`/`/mpd` URL 需求下移植末段分支 | 低风险条件候选 |
| A5-7c file URI | `65ee9ba81815e67c9d3d08a2be0028859cc20569` | `71262cce9d228daf95592bcbbab0b9ac3fbd8ae5` 精确等价 | 补大小写 URI 回归 | 不重复合并 |
| A5-7d OkHttp | `7709a03d55c6eaaf999c18f0d4ab9fc9141b7ead` | `4b7640ea550f91507818860e311bea206a688187` 已接线 | 测 Range/header/EOF/cache/代理；不合 demo manifest | 不重复合并 |

### 21.7 检查点后的状态

- MPEG-1 PS、file URI、OkHttp 不加入 Exo 最小合并集合；它们属于 fork 已有能力，当前工作重点是验收而非迁移。
- content-type 只保留 `/m3u8`/`/mpd` 无扩展名末段作为低风险候选，等待真实 URL 样本和单测结果。
- `APPLICATION_OCTET_STREAM` 已存在，不能把该上游提交标记为“完全未合并”。
- 下一批优先审阅剩余 A5/A6 的输入/容器/DRM 提交，并在每组完成后继续追加检查点；用户确认 Exo 最小集合前，仍不开始 MPV native lock/AAR 重建。

## 检查点 22：2026-08-21 ClearKey、压缩 Matroska 字幕、H.264 recovery point、MMT/TLV 与章节/偏移 API

本检查点先把内存审计落盘，再继续后续提交。仍未修改 Media3 源码、native 构建、AAR、lock 文件或 `third_party/sources/media` 中用户已有的 dirty 改动。判断“已覆盖”时同时检查当前 fork 的祖先关系、源码行为、App 接线和本地改动边界；不能仅凭提交主题或短 hash 判断等价。

### 22.1 ClearKey：三个 Exo/通用 DRM 提交已由 fork 覆盖

| 功能 | 上游完整 commit | 当前 fork 对应 commit | 覆盖状态 |
| --- | --- | --- | --- |
| ClearKey PSSH helpers | `39fde6f3b29cc5f69164a05fc89d5575b843371b` | `ec5a02fb5e3727348d390e5f96f87fadc4178d77` | 已覆盖 |
| DASH/HLS manifest 注入 ClearKey PSSH | `444971729731edc184f2fb9f1afee2cc03e44b0f` | `9d0b2d314d10ac6f29a9161d6e58dd4be20084d6` | 已覆盖 |
| local DRM callbacks | `061d90a1e59639594bad5ffceae0ce7fbeba005f` | `3a17e26960390bfc4452f4eb1d8c081f8b9cfd59` | 已覆盖 |

三个 fork commit 都是当前 media 树 `e3e922d5c01bc0b564849940fe589daf37360d15` 的祖先。现有实现能从 `KEYID=0x...`、Widevine PSSH 和 PlayReady XML 提取 KID，生成 Common PSSH，在 DASH/HLS manifest 中补 ClearKey scheme data，并对非 HTTP license URI 使用 `LocalMediaDrmCallback`。App 已有 `Drm`、`PlaySpec` 和 `ExoUtil.buildDrmConfig()`；MPV 当前明确拒绝 DRM。

**阶段 A5-8a：不重复合并代码，先做 Exo DRM 需求确认和测试。** 当前没有真实 ClearKey 样片或产品需求证据，暂不把三个上游提交加入 Exo 最小合并集合。若后续启用，应覆盖 Common PSSH 字节序/KID、Android API 级别、DASH/HLS 注入、license URI/header、离线/本地 callback 和失败传播；测试通过后仍只需要补 App/媒体样例，不应 cherry-pick 上游提交。回滚边界是 DRM 配置与 manifest 处理，不涉及 MPV native。

### 22.2 Matroska 压缩字幕：精确等价，保留现有本地 extractor

上游 `7feb08018a6e159330293de4878ebc3c9df2ca86` 与 fork `b14c2dcc5899067f93496a82c200cfc719485da1` 的稳定 patch-id 都是 `63c0302c876a983209af622588252e1422f5c70b`。当前 fork 已有 `Inflater`、`hasContentCompression` 与 `Util.maybeInflate()`，仅对 SubRip/ASS/SSA/WebVTT 文本轨解压，并且当前 Matroska extractor 还叠加了本地 Dolby Vision/RPU 等改动。

**阶段 A5-8b：不 cherry-pick，不覆盖当前 extractor；补回归测试。** 最低矩阵包括 zlib 字幕、错误压缩数据、reset/重复 sample、非文本轨拒绝、字幕时间轴和带 DV RPU 的 Matroska。任何修复应限定在压缩字幕 helper/测试，避免整文件覆盖导致 RPU 或本地 EBML 逻辑丢失。

### 22.3 H.264 AU/recovery-point：当前未见等价实现，保留为高价值但高风险候选

上游完整 commit：`aac6ec964681dd0476a33e3ad220ca7b5bf771f6`。当前 fork 尚未找到等价提交；源码仍有 `H264Reader.allowNonIdrKeyframes`、`detectAccessUnits` 和 `DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS`，而 `NalUnitUtil.PpsData` 没有 `numRefIdxL0DefaultActiveMinus1`，也没有 `containsImmediateRecoveryPoint()`。

该提交主要：解析 PPS slice groups 与 `numRefIdxL0DefaultActiveMinus1`；移除两个旧 flag，统一依据 slice header/AUD 检测 access unit；解析 SEI recovery point（payload type 6）；将少引用 I 帧和 recovery-point 非 IDR 帧视为关键帧；更新无 AUD H.264 TS 测试。对当前项目可能改善无 AUD 的 HLS/TS 起播、关键帧和 seek，但会触及本地 H.264、Dolby Vision、M2TS、4-byte start code 及 sample boundary 逻辑。

**阶段 A5-8c：样片驱动的选择性移植，暂不整提交。** 实施前必须取得无 AUD、recovery point、非 IDR I-frame 的真实样片，比较当前 `MediaSourceFactory` 的 TS flags，并验证 sample 数/边界、关键帧、seek、DV、字幕和 4-byte prefix。若确认缺口，只移植 PPS/recovery-point 必要 hunk 和对应测试；不得覆盖整个 `H264Reader`。建议把“旧 flags 兼容层”和“新关键帧判定”分成可独立回滚的小步骤。此项属于 Exo 阶段候选，不应提前牵动 MPV native。

### 22.4 MMT/TLV：与 FFmpeg 联合评估，当前暂缓

上游完整 commit：`ccf962e8912695dc60ce82aa4470df899c6306a3`。该提交新增 `MmtData`、`MmtDataDecoder`、`MmtSignalingParser`、`MmtpReader`、`TlvExtractor`，并接入 `FileTypes`/`MimeTypes` 与 `DefaultExtractorsFactory`，同时涉及 MMT metadata/decode 支持。当前 fork 没有对应 MMT/TLV extractor，只有普通 MPEG-H/TS 相关代码。

**阶段 A5-8d：先做输入和需求门槛，不合并。** 应与 FFmpeg 中对应的 MMT/TLV C0/C1 提交联合核对格式覆盖、时间戳、编解码器输出和是否由同一批设备产生；在没有真实 MMT/TLV 样片、产品播放需求和体积预算前，新增 extractor 的维护成本明显高于收益。若未来进入实施，先独立加入 FileTypes/MimeTypes/sniff 单测，再接 reader/metadata，最后做 seek、并发和错误输入回归，保持可单独回滚。

### 22.5 chapter/edition API：语义已有，不重复合并

上游 `f17757b05432e83f7c88c9f2a51377baaf10a227`，fork `a5faabc905bc67e920caa4efafeb5a5039b9d0f1`。当前 fork 已有 `MediaChapter`、`MediaEdition`、Player/Session 转发、ExoPlayer 选择 API，并在 ISO/DVD/Blu-ray/SACD source 中使用。实现因本地 API 结构和格式化不同，patch-id 不同，但不是功能缺口。

**阶段 A5-8e：不迁移代码；只有产品需要显示/选择章节或 edition 时再补消费方。** 回归应覆盖 ISO/DVD/Blu-ray/SACD 输入、章节顺序、edition 选择、Player/Session 生命周期和无 metadata 的退化行为。当前 App 尚未发现明确 UI 消费需求，因此不纳入 Exo 最小合并集合。

### 22.6 audio/text offsets：语义已有，低优先级验收项

上游 `db13d7672f9bca525878292a54ae5e69c021f4c9`，fork `f24f5bef688ac62794014a9c275c49306aa27599`。当前已有 Player API、ExoPlayer、AudioRenderer 和 TextRenderer offset 处理；patch-id 不同（上游 `bf46e50e...`、fork `eecdc24e...`），但行为已覆盖。App 尚未发现直接调用 `setAudioOffsetMs()`/`setTextOffsetMs()` 的产品接线。

**阶段 A5-8f：不重复合并；补 API/renderer/字幕时序测试，等待 UI 需求。** 测试应覆盖正负 offset、动态调整、音频/字幕同时调整、seek/重建 renderer 和无轨道退化。若没有用户可见的同步调节入口，暂不增加合并工作；若后续有需求，应作为 Exo UI/播放控制阶段单独接入，避免与 native 依赖锁变更耦合。

### 22.7 本检查点后的 Exo 决策表

| 阶段 | 关联 commit | 当前判定 | 下一动作 | 是否进入 Exo 最小集合 |
| --- | --- | --- | --- | --- |
| A5-8a DRM/ClearKey | `39fde6f3b29cc5f69164a05fc89d5575b843371b`, `444971729731edc184f2fb9f1afee2cc03e44b0f`, `061d90a1e59639594bad5ffceae0ce7fbeba005f` | fork 已覆盖 | 产品决策、真实样片和 DRM 回归 | 否，暂不 |
| A5-8b 压缩 Matroska 字幕 | `7feb08018a6e159330293de4878ebc3c9df2ca86` | patch-id 精确等价 | 补字幕/错误压缩回归 | 否 |
| A5-8c H.264 AU/recovery point | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | 当前未覆盖，高风险候选 | 样片、flags 对照、选择性 hunk | 待定 |
| A5-8d MMT/TLV | `ccf962e8912695dc60ce82aa4470df899c6306a3` | 无对应实现，暂缓 | 与 FFmpeg 联合、取得样片/需求 | 否，暂不 |
| A5-8e chapter/edition API | `f17757b05432e83f7c88c9f2a51377baaf10a227` | fork 已有语义 | 若有 UI 需求再验收 | 否 |
| A5-8f audio/text offsets | `db13d7672f9bca525878292a54ae5e69c021f4c9` | fork 已有语义 | API/renderer 回归，等待 UI | 否 |

截至本检查点，真正可能改变 Exo 代码的仍主要是 H.264 AU/recovery-point 和此前记录的 content-type 末段分支、SAMPLE-AES 窄 hunk；其余项目优先做样片/产品验收。用户确认 Exo 最小集合前继续禁止 MPV native lock、AAR 或 `.so` 重建。

## 检查点 23：2026-08-21 H.264 AU/recovery-point 与 MMT/TLV 三仓库联合审计

本检查点把 `aac6ec...` 和 `ccf962...` 两个容易被误判为“低风险新增”的提交做了完整的跨仓库核对。两者都只更新 Media3/Exo 源码（前者是 TS H.264 sample 边界，后者是完整 MMT/TLV extractor），但它们分别会触及当前 fork 的本地 H.264/TS 边界和 FFmpeg/MPV 的格式覆盖，不能只按提交标题直接 cherry-pick。仍未修改 Media3 源码、AAR、native `.so`、任何 lock 文件或 `third_party/sources/media` 中用户已有的 dirty patch。

### 23.1 H.264 access-unit 与 recovery-point

| 项目 | 完整 commit | 父 commit | 当前 fork 状态 |
| --- | --- | --- | --- |
| Improve H.264 access unit detection | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` | 当前 `e3e922d5c01bc0b564849940fe589daf37360d15` 未发现等价提交；仍保留旧 flags 和本地 H.264 边界 patch |

上游 diff 涉及 4 个文件，约 184 行新增、133 行删除，核心变化如下：

- `NalUnitUtil.PpsData` 新增 `numRefIdxL0DefaultActiveMinus1`，并补齐 slice-group map 的解析；这使 slice header 的 access-unit 判断可以使用 PPS 的真实默认引用帧数。
- 删除 `FLAG_ALLOW_NON_IDR_KEYFRAMES` 和 `FLAG_DETECT_ACCESS_UNITS` 两个旧式开关，`H264Reader` 改为依据 AUD 与 slice header 自动判断 sample 边界。
- 解析 SEI recovery-point（payload type 6）；只有 `recovery_frame_cnt == 0` 的 recovery point 立即作为可随机访问点使用，避免把尚未恢复的帧误报为关键帧。
- 在 SPS `maxNumRefFrames <= 1` 且 PPS `numRefIdxL0DefaultActiveMinus1 == 0` 时，少引用的 I 帧也可作为关键帧；这改善没有 AUD、但实际可起播的 H.264 流的 seek/起播。
- 测试把无 AUD 的 H.264 TS 改为默认 factory 路径，说明上游意图是移除调用方对 flags 的依赖，而不是只增加一个可选模式。

当前 fork 不能按文件覆盖，原因是本地已有以下相邻但不等价的行为：

- `85826ebc19ff39f9edeae65d7d55a2a3ad948ce1`：synthesized PUSI/EOF/keyframe-only NAL 处理；`PesReader`/`TsExtractor` 在 EOF 或 HLS segment 边界可能发送 synthesized empty PUSI。
- `4adbeed6f6f4bc846f08f2a58ec2f5d0bfeff84f`：4-byte start code 与空 sample 边界。
- `c144420be8e60861a7eac6a62f10896b041b6d98`、`7bbc7e03db35d87d7b01c65c00f83cfa9d516f02`：NAL 类型/stale state 清理。
- `d0722906d26fa9c6707df9e720d83d36dcb0a356`：TS sync detection；`db07c8e05e082043c6c6c0d9151637777196d1e5`：M2TS framing/seek。
- 当前 fork 还包含 Dolby Vision/H.265 sample 边界和 RPU 相关逻辑；H.264 reader 的 sample boundary 改变可能间接改变 DV/混合轨道的 PTS、keyframe 和 seek 行为。

因此“删除 flags”本身不是无风险清理：App/测试、`DefaultTsPayloadReaderFactory` 的调用点、HLS/TS/M2TS/Bdmv 输入和本地 synthesized PUSI 约定必须一起迁移。上游没有提供足够的真实样片来证明这些边界与本 fork 相同；仅通过无 AUD 单测不能证明 HLS segment 拼接、4-byte prefix 或 DV 样片安全。

**阶段 A5-8c：样片驱动的选择性移植，当前不整提交。** 建议拆为三个可独立回滚的小步骤：

1. **H264-1/PPS 基础字段**：只引入 `numRefIdxL0DefaultActiveMinus1` 和 slice-group map 解析，保留现有 flags；先用 slice header dump 对照上游/当前输出。
2. **H264-2/recovery 与关键帧判定**：在保留旧开关兼容层的前提下加入 recovery-point 和少引用 I-frame 逻辑，比较 sample flags、PTS、seek target 和首帧可解码性。
3. **H264-3/flags 清理**：只有所有调用点、测试和真实输入均迁移后，才删除两个旧 flags；若失败，可只回滚该层而保留 PPS/recovery parser。

实施前的最低样片/测试矩阵：无 AUD、带 AUD、recovery-point `recovery_frame_cnt=0` 与非零、非 IDR I-frame、多 slice、多 PPS、跨 PES/HLS segment、EOF synthesized empty PUSI、188/192-byte TS、4-byte start code、M2TS/Bdmv、Dolby Vision/混合 H.264-H.265、seek 到非关键帧和连续起播。必须同时检查 sample 数、NAL 边界、keyframe flags、decoder 首帧、字幕/音频同步和内存增长。

收益是改善部分无 AUD/恢复点流的起播和随机访问；风险是 sample 边界改变、旧调用方失效、HLS/M2TS/DV 回归以及错误关键帧导致花屏/seek 后不可解码。当前决策为：**保留为 Exo A5 高价值候选，不加入第一轮最小合并集合；不得 cherry-pick 整提交或覆盖 `H264Reader`。**

### 23.2 MMT/TLV：Media3 缺口与 FFmpeg/MPV 已有能力的联合核对

#### Media3/Exo 侧

| 项目 | 完整 commit | 父 commit | 当前状态 |
| --- | --- | --- | --- |
| MMT/TLV streams | `ccf962e8912695dc60ce82aa4470df899c6306a3` | `12670ce4fb23ad32ed3875d0250486eabe957913` | 当前 fork 没有 `libraries/extractor/.../mmt`，属于真实 Java extractor 缺口 |

上游 Media3 diff 涉及 13 个文件、约 3836 行新增，新增并接线：

- `TlvExtractor`：ARIB TLV 外层、坏 sync/伪 sync、IPv4/IPv6/压缩 IP 头和 live unseekable 处理；
- `MmtpReader`、`MmtSignalingParser`、`MmtTimestampAdjuster`：MMTP signaling、MPU/MFU 分片、时间戳/NTP 调整；
- `MmtData`/`MmtDataDecoder`：H.264、HEVC、AAC、ALS、STPP 以及 unknown payload 的 track/data 解码；
- `FileTypes`、`MimeTypes`、`DefaultExtractorsFactory` 接线；
- 内存边界：`MAX_TRACKS=64`、fragment 约 32 MiB、单 MFU 约 16 MiB，避免广播流异常输入无限增长。

当前 Exo nextlib 不会通过 FFmpeg 的 libavformat API 播放，因此 FFmpeg 已有 MMT/TLV demuxer 不能替代 Java extractor。即使 FFmpeg AAR 能探测样片，也不能证明 Media3 `ExtractorMediaSource`/track selection 已支持该格式。

#### FFmpeg 侧

| 项目 | 完整 commit | 父 commit | 当前锁定关系 |
| --- | --- | --- | --- |
| MMTP/MMT/TLV demux、TTML、ARIB timed-ID3/FATE | `054c8690e16b377eb1c6375c8751a44b8eb1d962` | `5805f9364c2e9a5f6ce625c9077b308c3ed4014d` | 当前锁定 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 已有旧 hash `57fd170a955746bf450ab0fad26cd211e8c101a9` 的同主题能力；源码已有 `mmtp.c`、`mmttlv.c`、`ttmldec.c`，配置已有 `CONFIG_MMTTLV_DEMUXER=1` |

`054c...` 的 21 文件/约 7466 行新增主要是 FFmpeg 原生 MMTP parser、MMT/TLV demux、TTML、ARIB timed-ID3 和 FATE 测试。对当前 FFmpeg 不是全新功能缺口，实际意义主要是通用 C1 的运行验收：确认当前锁定 hash 的 parser 是否覆盖目标设备样片、是否被 `--disable-everything` 的 nextlib 配置裁掉，以及与 MPV 的 lavf 路径是否有一致行为。不能因为提交规模大就把它作为 Exo Java 依赖升级理由。

#### mpv 侧

| 项目 | 完整 commit | 父 commit | 当前锁定关系 |
| --- | --- | --- | --- |
| mmttlv demuxer flags 与字幕样式标记 | `32c4d5adad29107756ae2987d69d92844bfed243` | `a088b8b9a1c5c3e2520145d69e5543a1a87a5cf7` | 当前锁定 `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 已有等价历史行为/映射；不重复 cherry-pick |

该提交只有 2 个文件、5 行：给 `mmttlv` 增加 `.use_stream_ids`、`.no_seek`，并把 ARIB/DVB/TTML subtitle 标记为 styled。它不会为 Media3 提供 extractor，也不需要为了 Exo 单独重建 MPV native。若未来 MPV 直接播放 MMT/TLV 样片，只需在 B 阶段针对当前 FFmpeg/mpv 组合验证 demuxer flags、live/no-seek、字幕样式和 track IDs。

#### 联合实施阶段与结论

| 阶段 | 关联 commit | 可实施内容 | 依赖/验收 | 当前建议 |
| --- | --- | --- | --- | --- |
| MMT-0 | FFmpeg `054c8690e16b377eb1c6375c8751a44b8eb1d962`；当前锁定 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | 用现有 FFmpeg/MPV 原生链探测真实 ISDB-S3/MMTP/TLV 样片，确认编解码器、时间戳、字幕和内存行为 | TLV 中间起始、坏/伪 sync、IPv4/IPv6/压缩头、NTP、signaling 分片/乱序、H.264/HEVC/AAC/ALS/STPP、unknown payload、超大 MFU、断流 | 先做；不改代码 |
| MMT-1 | Media3 `ccf962e8912695dc60ce82aa4470df899c6306a3` 的 FileTypes/MimeTypes 部分 | 仅加入 sniff、扩展名/MIME 和 unknown payload 的单测，确认 App 路由是否需要识别 TLV/MMTP | `/tlv`、`application/x-arib-*` 等真实 URL/MIME、无扩展名、代理/缓存和 live 输入 | 有真实需求后低风险开始 |
| MMT-2 | 同上 | 分层移植 signaling、timestamp、MMTP fragment、reader；保留 track/fragment 内存上限 | 分片乱序/丢包、wrap-around、seek 不可用、并发 track、32/16 MiB 边界和取消 | 只在 MMT-0 证明产品需求后实施 |
| MMT-3 | 同上；FFmpeg/mpv `32c4d5...` | 最后接 metadata、TTML/ARIB/ALS、styled subtitle 和 App track/subtitle 消费 | 字幕样式、语言/role、时间轴、错误传播、Android 老 WebView/播放器 UI | 最后实施，独立回滚 |

MMT/TLV 的主要收益是覆盖日本广播/ISDB 生态的直播输入；主要代价是新增约数千行 Java extractor、复杂 fragment/时间戳状态机、内存和 live 错误处理维护，以及与现有 `MediaSourceFactory`/代理/缓存路由的接线成本。当前没有真实样片、产品需求或体积预算证据，因此**暂缓 MMT/TLV，不加入 Exo 第一轮最小集合，也不为该功能单独重建 MPV native**。如果未来只需要 MPV，优先复用当前 FFmpeg+mpv 原生链，避免维护第二套 Java extractor。

### 23.3 本检查点后的决策更新

- H.264 `aac6ec...` 从“低风险”更正为“高价值、边界高风险”；只有样片驱动的 H264-1/H264-2/H264-3 分层移植可进入候选，整提交明确拒绝。
- MMT/TLV `ccf962...` 是 Media3 的真实功能缺口，但不是当前 FFmpeg/MPV 的同等缺口；先做 MMT-0 输入探测，不能以 FFmpeg 已支持推断 Exo 已支持。
- FFmpeg `054c...` 与 mpv `32c4d5...` 进入通用 C1/B 阶段的运行验收映射，不改变当前锁定 revision，也不启动 MPV native/AAR 重建。
- Exo 最小合并集合暂不增加 H.264 AU、MMT/TLV、FFmpeg MMT/TLV 或 mpv mmttlv 相关代码；用户确认前继续保持源码、lock、AAR 和 `.so` 不变。

## 检查点 24：2026-08-21 SMB/代理与磁盘预加载联合审计

本检查点审阅 Media3 上游连续的 `32c20a...` 与 `dd00f9...`。前者的大部分 SMB/代理能力已由当前 fork 覆盖，后者则把若干可独立采用的下载正确性修复与一套新的磁盘预加载 manager 混在同一个大提交中。当前 App 已有更完整的 `PreCache`、优先级和代理保护策略，因此不能按提交规模或标题直接整合。仍未修改 Media3 源码、AAR、native `.so`、依赖 lock 或用户在 `third_party/sources/media` 中的 dirty patch。

### 24.1 SMB 与 `proxy://` datasource：主要能力已覆盖，只保留一个窄健壮性候选

| 项目 | 上游完整 commit | 父 commit | 当前 fork 对应 commit | 判定 |
| --- | --- | --- | --- | --- |
| SMB/代理 datasource | `32c20a091ba6e5fd09e13e67df3149326232eda5` | `2d4ab61e69c74796f529bf8f9cab60c68b340d4d` | `ef2e532a7c7ac095498837ccede4c71c0d3da322` | `smb://`、`proxy://`、SMBJ、ProGuard 与 datasource 接线已存在；不应重复 cherry-pick |

上游提交涉及 4 个文件、约 228 行新增，稳定 patch-id 为 `10bd7a144289b24f546945810422864086a89c57`；fork 对应提交是当前 `e3e922d5c01bc0b564849940fe589daf37360d15` 的祖先，稳定 patch-id 为 `a3a8e1eb5d2e986344c7136b9c76ad2e9fb398be`。patch-id 不同并不代表功能缺失：逐文件比较后，`SmbDataSource` 只有格式差异，核心行为相同；`DefaultDataSource` 只有代理端口反射的异常/类型处理存在窄差异：

- 当前 fork 调用实例 `getPort()`，直接把反射结果强转为 `int`，并捕获 `Throwable`；
- 上游调用静态 `getProxyPort()`，先判断返回值是否为 `Number`，只捕获 `ReflectiveOperationException | RuntimeException`。

上游版本对反射返回类型更稳健，也避免无意吞掉 `Error`；但方法名差异还要以 App 实际代理类 API 为准，不能原样替换。App 已有 `UrlUtil.convert()` 的 `proxy://` URL 转换，播放 datasource 能接收 SMB URL；当前没有发现需要为这次上游提交新增的显式 SMB UI。

现有 SMB 行为仍有三个与是否合并上游无关的风险，需要专项验证：

1. 连接/文件缓存复用键没有包含用户名和密码；同一地址/文件切换凭据时，可能复用旧 session 或旧 handle。
2. `close()` 有意保留 SMB 连接供 seek 复用；需要验证播放停止、换源、异常和进程后台时的生命周期及泄漏。
3. 上游提交没有专项测试，当前 fork 也不能仅凭代码存在证明认证、seek、并发和错误恢复可靠。

**阶段 A6-0：不重复合并 `32c20a...`。** 只有在反射兼容测试证明有必要时，选择性移植“返回值先按 `Number` 判断”和窄异常捕获，并保留当前实际代理 API；补代理对象不存在、方法不存在、返回 `Integer`/其他 `Number`/错误类型、端口越界、并发播放和 fallback 测试。SMB 回归至少覆盖匿名/账号密码、凭据切换、Unicode 路径、seek/range、断网重连、服务器重启、并发播放、close/reopen 和错误凭据。该阶段可独立回滚，不应与预加载或 native 依赖升级绑定。

### 24.2 磁盘预加载大提交：拆分正确性修复、并行策略与 manager

| 项目 | 上游完整 commit | 父 commit | 当前 fork 状态 |
| --- | --- | --- | --- |
| disk preload / progressive parallel download | `dd00f94b58b7324ab29febb0b50f3a190d544a3b` | `32c20a091ba6e5fd09e13e67df3149326232eda5` | 当前 media fork 未发现同主题提交；App 已有功能更完整的 `PreCache`，不能整提交替换 |

该提交修改 5 个源码文件，约 844 行新增、68 行删除，且没有新增测试文件。它混合了四类不同风险的改动，必须拆开决策。

#### 24.2.1 `CacheWriter` bounded-read：高价值、较低风险的独立候选

上游把 `totalBytesRead` 改为 `long`，并在 datasource 忽略 bounded `DataSpec.length` 时仍严格停止在请求长度，同时修正 end position 的计算。这能避免服务器对 Range 返回整文件、代理剥掉 Range 或 datasource 退化为 200 时，一个预加载区间继续读取整个文件；在并行 progressive 下载中还能避免相邻区间重叠写入。

**阶段 A6-1：优先候选。** 只移植 `CacheWriter` 的长度/终止修复和对应单测，不同时引入并行 downloader。最低测试矩阵：已知/未知长度、0 长度、接近/超过 `Integer.MAX_VALUE`、datasource 正确返回 206、忽略 Range 返回 200、提前 EOF、重试后 position、缓存已命中一部分、取消和 priority exception。收益是减少无界下载、重复流量与缓存区间重叠；风险主要是 off-by-one 或把允许继续读的未知长度请求过早截断，回滚边界仅为 `CacheWriter`。

#### 24.2.2 factory time-range reset 与取消保护：可独立移植的 correctness 修复

`DefaultDownloaderFactory` 在每次创建 segment downloader 时显式重置 `startPositionUs=0` 和 `durationUs=C.TIME_UNSET`，避免复用 factory 后沿用上一次的 time range。`PreCacheHelper` 增加取消状态保护，避免 preparation executor 已被取消/释放后仍把 pending download 提交到 executor。

**阶段 A6-2：条件候选。** 分为两个小提交：

1. segment downloader time-range reset，测试同一 factory 连续创建全量、时间段、再全量任务时范围不串扰；
2. preparation cancellation guard，测试 prepare 前/中/后取消、release、executor race、重复取消和重新创建 helper。

二者不要求采用上游 `DiskPreloadManager`，也不要求增加 progressive 并发。若当前 fork 的 helper/factory API 因本地修改不同，应按语义移植，不能整文件覆盖。

#### 24.2.3 progressive 分段并行：有潜在收益，但必须默认保持单线程

上游 `ProgressiveDownloader` 在已知 byte length 时最多拆为 N 段并行下载，单段最小约 512 KiB，并新增 runnable、进度聚合、取消和 priority retry。该能力在高带宽、高 RTT 且服务器稳定支持 Range 时可能提高预缓存速度，但也会增加连接、线程、缓存锁竞争、服务器限流和代理异常风险。

当前 App 已有 route-aware 线程限制、外部代理 circuit breaker、storage/memory/network/power/thermal 策略、缓存水位、telemetry、quota、错误分类和自定义 `PriorityTaskDataSource`；外部/未知代理已经强制单线程。上游并行参数不能绕过这些策略，尤其不能把线程数只按文件长度决定。

**阶段 A6-3：实验性、默认关闭。** 只有完成以下测试后，才允许以 feature flag 对受控 HTTP(S) VOD 启用，默认值仍为 1：

- Range：206、忽略 Range 的 200、416、错误/重叠 `Content-Range`、未知/变化的 content length；
- 路由：直连、App 内部代理、外部/未知代理、重定向、鉴权 URL、连接数限制和服务器限速；
- cache：多个区间同时写入、已有稀疏缓存、eviction、磁盘满、cache lock、`CacheDataSource` fallback；
- lifecycle：取消、seek、换源、后台、断网/重连、priority retry、executor shutdown；
- correctness：总进度单调且不超过 100%、无遗漏/重复 byte、播放读取不会被 preload 饥饿、线程和 fd 峰值可控。

若实验失败，只回滚并发调度层并保持 A6-1/A6-2 的 correctness 修复；不得为了并行下载放宽当前代理和资源保护。

#### 24.2.4 `DiskPreloadManager`：只作语义参考，不替换 App `PreCache`

上游新增约 499 行 manager，默认预加载 30 秒、1 线程，仅处理 HTTP(S) VOD；每秒检查进度，允许最多约 5 秒 overlap，并用 5～30 秒 restart step 调整下一轮任务。当前 App 的 `PreCache` 已有 first-frame/recovery buffer gate、seek/cache 水位、设备与网络策略、代理保护、telemetry 和 quota，能力与接线都更完整。

**阶段 A6-4：不合入上游 manager。** 只把其 overlap/restart 条件作为当前 `PreCache` 测试或调参参考。整套替换会造成策略倒退、两套任务状态机并存以及生命周期/优先级冲突，收益不足以抵消风险。

### 24.3 priority manager：只能保留一层所有权

上游为 `PreCacheHelper` 增加 upstream `PriorityTaskManager` setter；当前 App 已有共享的 `PLAYBACK_PRIORITY_MANAGER`、foreground/preload 优先级和自定义 `PriorityTaskDataSource` wrapper。若同时在 helper 与 wrapper 注册/移除优先级，可能出现重复计数、释放顺序错误、取消后优先级残留或播放/预加载互相饥饿。

**决策：不单独启用上游 priority setter。** 在 A6-2/A6-3 实施前先画清唯一所有权：由当前 wrapper 继续负责优先级，或者经完整生命周期测试后迁到 helper；不能双接线。验证至少包括播放开始/停止、preload 排队/运行/取消、seek、换源、错误、release 和多个并发 player，检查 priority add/remove 成对且无残留。

### 24.4 关联功能的实施顺序

| 阶段 | 关联 commit | 可实施内容 | 当前建议 | 回滚边界 |
| --- | --- | --- | --- | --- |
| A6-0 SMB/proxy | `32c20a091ba6e5fd09e13e67df3149326232eda5`; fork `ef2e532a7c7ac095498837ccede4c71c0d3da322` | 不重复迁移；按需补反射类型健壮性和 SMB 生命周期/凭据测试 | 低风险验收项 | `DefaultDataSource` 窄 hunk/测试 |
| A6-1 bounded cache write | `dd00f94b58b7324ab29febb0b50f3a190d544a3b` | `CacheWriter` 严格遵守请求长度、`long` 计数和 end-position 修复 | 本组首选候选 | `CacheWriter`/单测 |
| A6-2 downloader correctness | 同上 | time-range reset；preparation cancellation guard | 分两个提交选择性移植 | factory/helper 各自独立 |
| A6-3 progressive parallel | 同上 | 已知长度的分段并行、进度/取消/retry | 默认 1，完成压力矩阵后 feature flag 实验 | downloader 并发层 |
| A6-4 preload manager | 同上 | 仅参考 overlap/restart 语义 | 不合入、不替换现有 `PreCache` | 无代码迁移 |

实施顺序必须是 **A6-1 → A6-2 → A6-3（可选）**；A6-0 可独立进行，A6-4 不进入合并队列。上游没有为 `dd00f9...` 提供测试，因此每个被选择的 hunk 都必须由本项目补测。A6-1/A6-2 属于 Exo 依赖阶段，A6-3 同时受通用网络/代理策略约束；它们都不构成提前升级 FFmpeg、mpv、mpv-android 或 libplacebo 的理由。

### 24.5 本检查点后的决策更新

- `32c20a...` 不能标为当前 fork 的新功能缺口；SMB/代理主体已覆盖，只保留反射类型处理与现有实现专项测试。
- `dd00f9...` 不能整提交合并：优先评估 A6-1 bounded-read，再评估 A6-2 两个 correctness 修复；progressive 并行必须默认关闭，`DiskPreloadManager` 明确不合入。
- priority manager 必须保持单一所有权；不同时启用上游 helper setter 与当前 `PriorityTaskDataSource` 接线。
- Exo 第一轮最小集合可新增的候选只有 A6-1，以及在竞态/复用测试证明缺口后的 A6-2；A6-3 仍是后置实验项。
- 下一批继续审阅 RealMedia `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c` 与 ASF `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b`，完成后先落盘，再进入 ISO/UDF/HDMV/DVD/SACD 关联组。

## 检查点 25：2026-08-21 RealMedia/ASF extractor 联合审计

本检查点已完成 RealMedia 与 ASF 两个大提交的逐 hunk 语义核对，并先把结论落盘，避免把“Java extractor 已加入”误认为“Exo 已经具备可播放能力”。当前没有修改 Media3 源码、AAR、native `.so`、任何 lock 文件，也没有覆盖 `third_party/sources/media` 中用户已有的 dirty 改动。

### 25.1 RealMedia：上游新增与当前 fork 初版的差异

| 项目 | 完整 commit | 父提交 | 当前 fork 对应提交/状态 |
| --- | --- | --- | --- |
| RealMedia/RMVB extractor | `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c` | `9b535ed30b9fa7e8580264036de1a12115daba32` | `d044f49633583f4960e9c9dfafe8ed95b0c7f661`（当前 `e3e922d5c01bc0b564849940fe589daf37360d15` 的祖先） |

上游提交约新增 1728 行、删除 1 行；当前 fork 的初版 extractor 约新增 1576 行。两者 patch-id 不等价（上游 `71fea8bbe357f3e9f3a813d19a9818d56df871c6`，本地 `e6650da3f85cd936fc734298426f7f698dbc5206`），因此不能用 cherry-pick 是否冲突来判断覆盖关系。主体的 `RmExtractor`、RM/RMVB track reader、Cook/RAAC/SIPR/AC3 和 INDX seek map 已存在，真正值得评估的是以下语义增量。

#### 25.1.1 DATA header 的陈旧 packet count 防护

上游在读取 RM `DATA` chunk 时跳过可能过时的 packet count，并检查后续字节是否为 `PROP`、`MDPR`、`DATA`、`INDX` 或 `CONT` chunk marker；遇到非法 data-packet version 直接终止。这样可以避免封装中的 packet count 错误导致提前结束，或把后续 chunk 当作媒体 packet 继续解析。当前 `d044...` 初版仍较依赖 header 中的计数/顺序。

收益是提高带错误索引、追加 metadata、下载未完成或被工具重写过的 RM 文件的安全性；风险是 marker 识别过宽/过窄会改变合法旧版 RM 的读取边界，错误终止也可能丢掉可恢复的尾部样本。应只移植 chunk-marker/version 防护，不覆盖整个 `RmExtractor`。

#### 25.1.2 SIPR 按真实 `blockAlign` 输出 decoder sample

当前 fork 将一组 SIPR 数据整体输出；上游依据 `calcBlockAlign(codecFourCC, flavor, subPacketSize)` 把 group 按真实 `blockAlign` 切成多个 decoder sample，并只给第一个 sample 设置 keyframe。该修复可能影响音频时长、sample 数、时间戳和 decoder 的输入边界，不能只用代码静态判断。

收益是修复 SIPR group 与实际解码帧不一致造成的音频时序/丢帧；风险是部分 RealAudio 变体的 `blockAlign` 计算与上游假设不同，切分后反而让 Cook/SIPR decoder 收到半帧。必须用真实 SIPR 样片对照当前输出的 sample size、时间戳、首帧和连续播放。

#### 25.1.3 RealVideo 分片声明长度保护

上游在新 picture 开始时记录 `expectedFrameLength=len2`，丢弃旧的残帧；累计 payload 超过声明长度时丢弃该帧；达到声明长度时即使 packet type 没标记为 last 也完成 emit。它针对损坏、截断或分片标记不可靠的 RMVB，能减少花屏、错误 emit 和残帧内存增长。

风险是某些合法 RealVideo packet 会跨越声明长度或依赖 type 标志，过早完成可能截断画面；需要覆盖 RV30/RV40 多片帧、乱序/重复片、截断尾包、错误 `len2` 和 seek 后第一帧。只移植 expected-length/残帧状态机，保留 fork 的本地 sample boundary 改动。

#### 25.1.4 非功能差异不随 RM 合并

上游同一提交还包含格式化、`DefaultExtractorsFactory` 默认 subtitle parser/transcoding 行为和 `MimeTypes` 的 `av3a` codec inference。这些不属于 RM extractor 的必要增量：subtitle 默认值应按独立通用阶段处理，AV3A 已在本项目 A2 记录，不能因为 RM cherry-pick 一并改变全局默认行为。

### 25.2 ASF：上游新增与当前 fork 初版的差异

| 项目 | 完整 commit | 父提交 | 当前 fork 对应提交/状态 |
| --- | --- | --- | --- |
| ASF/WMA/WMV extractor | `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b` | `5b0041dd98363d2eb5db557c0626be0e66307d45` | `4140df3ff32565a021667e3a950a324cd9e2c0d7`（当前 HEAD 的祖先） |

上游提交约新增 1705 行、删除 1 行；当前 fork 已有对应 ASF package。两套实现 patch-id 不等价，但主体 extractor、header reader、packet reader、WMA/WMV track 接线已存在。上游相对当前 fork 的有效语义增量如下。

#### 25.2.1 不完整/截断的最后 ASF packet

上游不再以 `readFully()` 将 EOF 一律视为整包失败，而是允许读取短 packet，并以 `allowIncompletePayload` 将解析范围限制在实际 buffer 尾部；ECC/PPI/header 解析增加剩余长度检查和 nullable 防护。该行为适合 HTTP/代理响应被截断、文件尾部不完整或边下边播场景。

收益是完整输出此前已读到的合法样本，减少尾包 EOF 造成的整段丢失；风险是恶意/损坏短包被误解析成有效 sample，或把截断音视频交给 decoder 后产生花屏/噪声。应把“允许读取短包”和“是否 flush 残样本”分开测试，不能整包替换。

#### 25.2.2 compressed multi-payload 的零长度 sample

上游在 compressed payload 中遇到 `sampleSize == 0` 时推进 `sampleTimeMs += timeDeltaMs` 并继续解析后续 payload；当前 fork 把零长度当作错误并退出。该修复主要改善 ASF 压缩多 payload 的时间轴，属于局部且可回滚的 packet-reader 逻辑。

风险是异常输入包含大量零长度项时造成无界循环或时间溢出；应增加 payload 长度、迭代次数和时间戳单调性断言，并覆盖真实 WMA/WMV 多 payload 样片。

#### 25.2.3 EOF flush pending samples

上游在读取结束时 flush 未完成的 pending sample，但对仍需要 descrambling 的音频保留跳过逻辑。它可能改善完整文件最后一个 sample 因没有后续 fragment 而未输出的情况，尤其是 compressed/fragmented ASF。

风险是将不完整尾样本交给 decoder，导致爆音、花屏或错误时长；必须区分已达到声明大小的 pending sample 与真正截断的残片，并分别验证普通 WMA、descrambling 音频、WMV 视频和代理 EOF。建议只移植 flush 条件和测试，不改变 pending sample 数据结构。

#### 25.2.4 ASF 视频负高度

上游解析 BITMAPINFOHEADER 时对非零 `biHeight` 使用 `Math.abs(biHeight)`，覆盖 top-down DIB/负高度视频；当前 fork 直接使用负值，可能生成非法视频尺寸或方向。收益明确、改动很小，但仍需限制宽高上界，防止 `Integer.MIN_VALUE` 取绝对值溢出和恶意超大值。

### 25.3 Exo 解码链阻塞：extractor 不等于可播放

当前 `FfmpegLibrary.getCodecName()` 只映射 AAC、MP3、AC3、E-AC3、TrueHD、DTS、Vorbis、Opus、AMR、FLAC、ALAC、PCM、H.264/H.265 等常用 MIME，没有 Cook、SIPR、RALF、ATRAC、RV、WMA/WMV/VC-1 映射。nextlib 两个 ABI 的生成配置也明确禁用：

```text
CONFIG_RM_DEMUXER=0       CONFIG_ASF_DEMUXER=0
CONFIG_RV10/20/30/40_DECODER=0   CONFIG_VC1_DECODER=0
CONFIG_WMV1/2/3_DECODER=0         CONFIG_COOK_DECODER=0
CONFIG_RALF_DECODER=0              CONFIG_SIPR_DECODER=0
CONFIG_ATRAC3/ATRAC3P_DECODER=0   CONFIG_WMALOSSLESS/WMAPRO/WMAV1/WMAV2/WMAVOICE_DECODER=0
```

因此 RM/ASF Java extractor 的选择、track format 和 sample 边界修复，并不会自动让当前 Exo 播放 RM/RMVB/ASF。若产品确实需要这些格式，必须另开“MimeTypes → FfmpegLibrary → nextlib FFmpeg configure → 两 ABI AAR → 设备解码”完整阶段；若只需要封装探测或 metadata，则可以单独验证 extractor 而不扩展 decoder。

### 25.4 与 MPV/native 的联合判断

RM/ASF Java extractor 只运行在 Media3/Exo 路径，不会改变 MPV 的 lavf 原生链。当前 MPV 使用独立的 FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 和 mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`，并采用 NDK r29 与 `libmv*`/`libmw*` 命名；Exo nextlib 使用另一套配置和 NDK r28c。即使未来确认 MPV 的 FFmpeg 需要 RM/ASF，也必须作为 MPV 阶段单独更新、两 ABI 成套重建和验收，不能因 Exo extractor 修复触发 native rebuild。

### 25.5 可实施阶段、commit 与回滚边界

| 阶段 | 关联 commit | 可实施内容 | 前置条件/验收 | 当前建议 |
| --- | --- | --- | --- | --- |
| A6-5a RM packet/chunk 防护 | Media3 `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c`；fork 初版 `d044f49633583f4960e9c9dfafe8ed95b0c7f661` | 选择性移植 DATA packet-count/chunk-marker/version 边界 | RM/RMVB、陈旧 packet count、PROP/MDPR/DATA/INDX/CONT、错误 version、截断尾部 | 保留为候选；不整 cherry-pick |
| A6-5b SIPR block-align | 同上 | 按真实 `blockAlign` 切 sample，仅首 sample keyframe | SIPR 样片、sample size/PTS/decoder 输入对照 | 有样片后再做 |
| A6-5c RealVideo length guard | 同上 | expected frame length、残帧丢弃、超长/提前完成保护 | RV30/RV40 分片、损坏/截断/seek 回归 | 有 RMVB 需求后再做 |
| A6-6a ASF short packet | Media3 `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b`；fork 初版 `4140df3ff32565a021667e3a950a324cd9e2c0d7` | 短读、ECC/PPI 剩余长度和 nullable 边界 | HTTP/代理 EOF、恶意短包、WMA/WMV decoder 错误传播 | 条件候选 |
| A6-6b ASF zero-size payload | 同上 | 零长度 payload 只推进时间并继续解析 | multi-payload、时间单调、零长度上限 | 低风险窄 hunk 候选 |
| A6-6c ASF EOF flush | 同上 | 仅 flush 已可判定完成的 pending sample | 普通/descrambling 音频、视频、截断尾包 | 条件候选 |
| A6-6d ASF negative height | 同上 | `Math.abs(biHeight)` 加溢出/上限保护 | top-down DIB、负值/超大值、宽高回归 | 低风险窄 hunk候选 |
| A6-7 RM/ASF decoder enablement | Media3 `4c3aa7...`、`0fa9a1...`；nextlib/FFmpeg 当前 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` | MIME 映射、FFmpeg configure、两 ABI AAR 和设备验证 | 真实样片、体积/许可证/硬解 fallback、decoder 能力 | 未确认需求前不做 |

实施顺序建议为 **A6-5a → A6-5b/A6-5c（按样片）**、**A6-6a → A6-6b → A6-6c/A6-6d**；A6-7 只有在产品确认 RM/ASF 播放需求后才启动。每个窄 hunk 单独提交、单独测试和单独回滚，禁止覆盖当前 `RmExtractor`/ASF package。RM/ASF 阶段完成前不更新 FFmpeg lock、不重建 nextlib AAR，也不触发 MPV native 合并。

### 25.6 本检查点后的决策更新

- RM/ASF extractor 主体已在 fork 中，不能重复 cherry-pick 上游大提交；上游有意义的内容是 A6-5/A6-6 的安全、边界和时序窄 hunk。
- 当前 nextlib 明确关闭 RM/ASF demuxer 及相关 decoder，`FfmpegLibrary` 也没有对应 MIME 映射；不能把 A6-5/A6-6 宣称为完整播放支持。
- A6-5/A6-6 暂不进入 Exo 第一轮最小集合，除非用户提供真实样片和明确格式需求；优先记录为可独立回滚的候选。
- A6-7 若未来启动，必须先完成 Exo 侧两 ABI decoder 构建和样片验收，再决定是否在后续 MPV 阶段同步评估原生 FFmpeg；两条链不能共用未经验证的二进制。
- 下一批转入 ISO/UDF/HDMV/DVD/SACD 关联提交组，审完每组立即追加新检查点。

## 检查点 26：2026-08-21 ISO/UDF 随机访问基础层

本检查点先完成光盘栈最底层 `990abc...` 的逐文件比较并立即落盘。该层被 Blu-ray、DVD、SACD 和最终 `IsoMediaSource` 共同依赖；若错误地把它当成纯重落基，会同时掩盖多 extent 文件支持和损坏镜像边界问题。当前仍只修改本文档，没有改 Media3 源码、AAR、native 二进制或 lock。

### 26.1 commit 身份与当前覆盖关系

| 项目 | 上游完整 commit | 父提交 | fork 对应 commit | 当前状态 |
| --- | --- | --- | --- | --- |
| ISO/UDF data access | `990abc2368fd74779f525ee345734470659f3d53` | `c85d124102c5b25a1bcd270d78f78603e87a6214` | `39585f19e01324308213e2bdc9aa84dcfa4d5ebc` | fork 已有初版且是当前 HEAD 祖先；上游 patch-id `050a75434c948c5f6f5e1be7068800fa6279e4be`，本地 patch-id `b02ae6f8aa24ccc61c8be42d273db9e949c474ae`，不等价 |

当前 fork 的 `CacheDataReader`、`IsoDataReader`、`IsoDataSource`、`IsoFileEntry` 和 `UdfFileSystem` 文件仍基本停留在 `39585f...` 初版。上游提交从约 1031 行扩展到约 1534 行，真实增量不是格式化，而是多 extent、allocation descriptor continuation、安全边界和 datasource 生命周期。

### 26.2 `IsoDataReader`：零长度、预取内存和 open/close 边界

上游相对当前 fork 增加三个窄修复：

1. `read(..., length=0)` 立即返回 0，避免 `(byteOffset + length - 1)` 下溢后计算错误 sector。
2. `prefetchRange()` 拒绝负起点、空/反向范围，并在 sector 数超过 8192 时不分配整段 byte array；当前初版可因镜像内错误的 extent 长度分配超大数组或整数截断。
3. `directRead()` 把 `open()` 放入 `try/finally` 范围内，并统一使用 `C.RESULT_END_OF_INPUT`；即使 open/read 失败也按 datasource 生命周期关闭。

**阶段 A6-8a：优先选择性移植。** 这是 ISO 解析的低风险安全前置，可独立于多 extent 支持实施。最低测试包括 0 长度、负/反向/超大预取、EOF、open 抛错、短读、服务器忽略 Range、并发 sector cache 和 close。回滚边界仅为 `IsoDataReader`。

### 26.3 UDF 多 extent、未记录 extent 与 allocation descriptor continuation

当前 fork 的 `resolveFileEntry()` 只保留第一个物理 offset，并在发现非连续 extent 后停止；`IsoFileEntry` 也只有单一 `byteOffset/length`。上游改为保存 `extentOffsets[]/extentLengths[]`，支持：

- 文件跨多个不连续物理 extent；
- UDF extent type 1/2 的未记录或未分配数据，以 `UNRECORDED_EXTENT_OFFSET=-1` 表示并在读取时零填充；
- extent type 3 的 Allocation Extent Descriptor continuation（`TAG_AED=258`）；
- short/long/extended allocation descriptor 的 allocation/recorded/information length 区分；
- continuation 最多 64 层、单段最多 1 MiB、循环 offset 检测和加法/乘法溢出保护。

这对经过重排、分层刻录、存在 padding/hole 或 allocation descriptor 延续块的 UDF 镜像是实际功能修复。当前单 offset 实现可能读到错误物理区、把多 extent 文件截断，或完全找不到对应 M2TS/VOB/DSD 数据。

风险也较高：所有上层 helper 目前按 `byteOffset` 构造 clip；引入数组后，`IsoFileEntry → IsoDataSource.Factory → Blu-ray/DVD/SACD helper` 必须一起迁移，否则只升级 parser 会产生 API/语义不匹配。零填充 extent 也可能隐藏损坏镜像，应只对标准定义的未记录 extent 使用，其他类型仍要显式失败。

**阶段 A6-8b：作为完整光盘栈的必要候选，不单独上线 parser。** 先增加 UDF 单元测试，再改 `IsoFileEntry`/datasource，最后迁移上层 helper。必须覆盖单 extent、多连续/不连续 extent、type 1/2 零填充、type 3 continuation、循环/超 64 层、超 1 MiB、short/long/extended AD、metadata partition、reserve VDS、截断 FID/FE/EFE、超大 information length 和整数溢出。

### 26.4 `IsoDataSource`：跨 extent 虚拟文件与 M2TS/SACD 头剥离

上游 datasource 接收 extent 数组和 clip 的逻辑偏移，在读取到 extent 边界时关闭并按原 `DataSpec.buildUpon()` 打开下一物理段；未记录 extent 直接输出零字节。它同时保留 M2TS 192→188 和 SACD 2048→2044 的头剥离、非 packet-aligned seek，以及以下生命周期修复：

- 校验 extent 数组、总长度、clip 边界并用 `Math.addExact()` 防溢出；
- 检测 upstream 返回 0 的无进度读取，避免死循环；
- 仅在真实打开过 upstream/transfer 后 close/end；
- 跨 extent 继续传播原 `DataSpec` 的 key、headers/flags 等字段；
- 在可用时返回 upstream URI/response headers。

收益是让 UDF 多 extent 文件在 Exo 看来仍是一条可 seek 的连续字节流，并让 Blu-ray/SACD 头剥离在 extent 边界保持正确。风险集中在虚拟 offset 到原始 sector 的换算：188/192、2044/2048、clip logical offset 和非对齐 seek 任一 off-by-one 都会导致 TS sync、DSD frame 或 seek 错位；跨 extent 反复开 HTTP Range 也会增加连接数和延迟。

**阶段 A6-8c：必须与 A6-8b 同阶段集成、分提交验收。** 先实现纯 raw multi-extent，再分别打开 M2TS 和 SACD strip，最后接网络 Range/代理。测试要逐 byte 对照虚拟流，覆盖 extent 边界前后、header 内/外 seek、空 extent、零填充、短 extent、最后半包、HTTP 206/200/416、SMB/content URI、本地文件和取消/重开。

### 26.5 当前实施决策

| 阶段 | 关联 commit | 内容 | 当前建议 | 回滚边界 |
| --- | --- | --- | --- | --- |
| A6-8a reader safety | `990abc2368fd74779f525ee345734470659f3d53`; fork `39585f19e01324308213e2bdc9aa84dcfa4d5ebc` | 0 长度、预取范围/上限、open/close/EOF | 低风险优先候选 | `IsoDataReader` |
| A6-8b UDF extents | 同上 | 多 extent、未记录零填充、AED continuation、解析边界 | 光盘功能确认后实施 | `IsoFileEntry` + `UdfFileSystem` |
| A6-8c virtual datasource | 同上 | 跨 extent、M2TS/SACD strip、生命周期与 Range | 与 A6-8b 联合，不可单独上线 | `IsoDataSource`/Factory |

当前 fork 已具备简单连续 UDF 镜像的基础读取，不应重复 cherry-pick `990abc...`；但也不能标记为完全覆盖。Exo 第一轮最小集合若不包含 ISO 播放，只建议考虑独立的 A6-8a；A6-8b/A6-8c 等待 Blu-ray/DVD/SACD 产品需求和真实镜像矩阵。该组只影响 Exo 光盘路径，不触发 MPV native 升级；MPV 已有 libbluray/libdvdnav 等独立原生链，后续只做行为对照。

下一检查点继续审阅 `b3a78a2f7a9353359a02efe61e94038238c04fa1`、`15d8d21f3354e6da48c5a47751a3edb943f9ffc6` 与 `4d713dded8f59cac265ec612dc263b1287bb08b4` 的 HDMV/VC-1/LPCM/DVD private stream 和 Blu-ray playlist 增量。

## 检查点 27：2026-08-21 HDMV、DVD private stream 与 Blu-ray DV7 联合审计

本检查点把三个相邻但风险完全不同的提交拆开处理：HDMV elementary reader 主体已覆盖，只需要回归；DVD private stream 主体已覆盖，但上游后续版本包含多个可独立移植的识别、轨道映射和 EOF 收尾修复；Blu-ray playlist 主体也已覆盖，而新增的 DV7 base/enhancement layer 合并会改变送入 renderer 的实际码流，必须作为高风险独立阶段。当前仍只修改本文档，没有修改 Media3 源码、AAR、native 二进制或 lock。

### 27.1 commit 身份与覆盖关系

| 功能 | 上游完整 commit | 父提交 | fork 对应 commit | patch-id 关系 |
| --- | --- | --- | --- | --- |
| HDMV TS readers | `b3a78a2f7a9353359a02efe61e94038238c04fa1` | `5bca32949e0ad82cb0105962a7ae31234d6cd1a8` | `95cd47cb3f6da7e19b593409022fee7365c80c4e` | 上游 `a5ae555a7b473bc7bf904712599f7b6aeabd0cbf`；fork `85d0ad47a89e4b65c3e60c155aee7ca0826e2240`，不等价 |
| DVD private streams | `15d8d21f3354e6da48c5a47751a3edb943f9ffc6` | `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e` | `085a55d8e4b77c78e2738c62e972dac777411093` | 上游 `82bc14ea84a5b6088df54c480c368d7f9bab67f8`；fork `90611d9ae3f2e055a1d62fcb422a9796933c068b`，不等价 |
| Blu-ray playlist/M2TS | `4d713dded8f59cac265ec612dc263b1287bb08b4` | `15d8d21f3354e6da48c5a47751a3edb943f9ffc6` | `82c45f214a96f8b0da6a04f2943f5e54a9f1fdd1` | 上游 `b7973e79bc4cabcb35467f836e0dfd6d8fce2f9b`；fork `7419d7c09fe59559505769455893a8fb51cfc8af`，不等价 |

三组“不等价”都不能直接解释为 fork 缺少整个功能。当前 fork 已包含 `LpcmReader`、`Vc1Reader`、`DvdLpcmReader`、`DvdSubtitleReader`、BDMV/CLPI/MPLS parser、`BdmvTsExtractor` 和 HDR wrapper，并在初版之后叠加了 MPEG-1 PS、seek、连续 playlist 时间轴、HDMV DTS、HLG/HDR10+、Dolby Vision/RPU 等本地语义。后续实施必须按下面的窄阶段移植，禁止整提交覆盖。

### 27.2 HDMV reader：主体语义已覆盖，不再重复合并

逐文件比较后，当前 fork 的 `LpcmReader` 和 `Vc1Reader` 已覆盖上游提交的核心读取状态机；剩余差异主要是格式化、接口落点以及 fork 后续叠加的 factory/stream type 接线。当前 `DefaultTsPayloadReaderFactory`、`TsExtractor` 和 BDMV 专用 factory 还额外处理 SAMPLE-AES、Dolby Vision、AV3A、HDMV DTS auto remap 等本地能力，整文件替换反而容易造成回退。

**阶段 A6-9：仅做覆盖确认和回归，不合并代码。** 回归矩阵至少包括 Blu-ray LPCM 的采样率/位深/声道布局、VC-1 sequence header 与跨 PES picture、普通 TS 中的 `0x82` 歧义流、CLPI 已确认的 DTS/DTS-HD/DTS-UHD、192→188 M2TS strip、seek 后首个关键帧以及多 clip 连播。若回归发现具体差异，再按失败样片定位窄 hunk；不能以 patch-id 不同为由重复 cherry-pick `b3a78a...`。

### 27.3 DVD-1：子流 ID 与 audio header 安全识别

当前 fork 的 DVD private reader 已能输出 AC3、DTS、LPCM 和 VobSub，但上游相对初版增加了几项明确的兼容与误判防护：

- DTS 除 `0x88–0x8F` 外，还接受扩展子流 `0x98–0x9F`；
- LPCM 从 `0xA0–0xA7` 扩展到 `0xA0–0xAF`；
- `0x0B` 只有在下一字节为 AC3 sync 的第二字节 `0x77` 时才按 raw AC3 处理，避免把普通 payload 的首字节误当成 raw AC3 substream id；
- DVD audio private header 的跳过条件按合法 ID 范围处理，不再对所有 `>=0x80` 输入机械跳过三字节；
- LPCM 在创建/分发 reader 前检查 dynamic-range/header 标记，减少随机 private payload 被误识别为 LPCM。

这些改动收益是补齐较少见的 DTS/LPCM 轨并降低损坏 VOB、菜单 private data 或非标准 mux 的误判；风险是部分旧工具生成的非规范 DVD 包不满足新校验，原本“碰巧能播”的流会被拒绝。实施时应把 ID 范围、raw AC3 双字节判断、header 跳过和 LPCM 校验拆成可单独回滚的测试提交，覆盖标准 VOB、菜单 VOB、`0x0B` 假阳性、短包、`0x98–0x9F` DTS、`0xA8–0xAF` LPCM 及截断 header。

### 27.4 DVD-2：IFO 逻辑轨序号到 VOB 物理 substream 的映射

当前 fork 将 IFO 的 active audio/subpicture 数组较直接地与 substream index 对照；上游增加 `audioStreamIndexBySubStreamIndex` 和 `subpictureStreamIndexBySubStreamIndex`，用 IFO 中的逻辑轨顺序映射实际 VOB substream ID。两者在“轨道刚好按 ID 顺序排列”的镜像上没有差异，但在被重排、禁用部分轨、含多个语言或菜单/标题轨布局不同的 DVD 上，直接用物理 index 查语言和 active 标记可能导致：

- 选中的语言对应到另一条音轨或字幕；
- IFO 标记为启用的轨被错误排除；
- 同一个物理 substream 使用错误的逻辑轨 metadata；
- 菜单域和标题域切换后轨号看似相同但实际 PID/substream 不同。

**阶段 A6-10b 必须与后续 DVD IFO parser 审计联合实施。** private reader 只消费映射，真正的映射来源和边界由 IFO parser 构造；不能只增加数组而继续传旧语义。验收应使用至少一个非连续 audio ID、一个字幕重排、一个多 VTS、一个菜单域和一个缺失/损坏 IFO 的镜像，对照语言、App 轨道列表、默认/强制字幕、切轨和 seek 后保持情况。

### 27.5 DVD-3：预创建字幕、EOF flush 与 track discovery 收尾

上游在 `createTracks()` 时根据 IFO 已知的 active subtitle 预创建 `DvdSubtitleReader`，避免字幕在很晚才出现或整段没有 subtitle packet 时 track group 不稳定；在输入结束时把 `endOfInputReached()` 转发给全部子 reader；`PsExtractor` 也在 `init()` 时注册传入的 DVD private reader，并在 program end、短尾包和真实 EOF 时统一完成 pending sample 与 `output.endTracks()`。

这组修复对远程 Range、截断 VOB、只在后半段出现字幕和菜单短片尤其有意义，但当前 fork 的 `PsExtractor` 已有 MPEG-1 pack、注入 seek map、binary search seek 和其他 EOF 改动。**只能移植生命周期语义，不能覆盖整个 `PsExtractor`。** 需要验证：无字幕 packet 仍可见 IFO 字幕轨、最后一个音频/字幕 sample 不丢失、截断尾样本不被错误 emit、`endTracks()` 恰好调用一次、seek 后不重复建轨，以及普通非 DVD MPEG-PS 不受影响。

### 27.6 Blu-ray DV7：新增 BL/EL access unit 合并能力

上游最重要且当前 fork 确实缺少的文件是 `DolbyVisionCombiningTrackOutput.java`。当前 fork 的 `HdrUpgradeOutput` 遇到 Dolby Vision enhancement layer 时直接返回 `DiscardingTrackOutput`；上游则尝试找到对应 base layer，并把两层 access unit 合并后作为一条 DV Profile 7 track 输出：

- 优先使用 descriptor 中的 `dependencyPid` 配对；没有 dependency PID 时，只在唯一 HEVC base candidate 存在时推断，避免多候选误配；
- base/enhancement sample 只在 `timeUs` 完全相同的情况下合并；较早的 base 作为普通 base layer 输出，较早或最终未匹配的 enhancement 丢弃；
- 每层最多缓存 16 个 sample，seek 时 reset，EOF/release 前 flush base sample；
- 合并 base 与 enhancement 的 initialization data，并通过 DV7 BL/EL/RPU 标志生成 Dolby Vision format；
- enhancement layer 不再形成独立可选轨，最终由同一个 output 接收组合后的 access unit。

收益很明确：对支持原生双层/单轨组合 DV7 bitstream 的设备，Exo 有机会保留 enhancement layer，而不是永远只播放 base layer；同时 dependency PID 和唯一候选规则比“看到一条 DV 就随便找 HEVC”安全。

风险也明显高于普通 parser 修复：

- “16 个 sample”限制的是数量而不是总字节，4K 高码率 I-frame/大 AU 可造成显著瞬时内存峰值和复制开销；
- 仅按 PTS 完全相等匹配，BL/EL 存在很小时间戳偏差时会静默退化为 BL-only，并持续丢弃 EL；
- 每个 sample 先缓存在 Java byte array，再拼接写出，会增加 GC 压力；
- 合并 CSD 和 AU 后送入 vendor MediaCodec 的形态发生变化，部分设备只接受 BL+RPU、部分接受完整 BL+EL+RPU，不能由 codec capability 声明可靠推断；
- 上游 flush 规则会输出未匹配 BL、丢弃未匹配 EL，异常配对不一定表现为显式错误，需增加诊断计数才能发现。

### 27.7 与当前 App DV7 策略的联合冲突：ISO 路径确认绕过 P8.1 wrapper

当前普通 Exo progressive 路径使用：

```text
DefaultExtractorsFactory
  -> DolbyVisionP81ExtractorsFactory
  -> renderer 原生 DV / P8.1 / HDR10 策略
```

但 ISO/Blu-ray 路由并不复用这条 extractor factory。`DefaultMediaSourceFactory` 识别 ISO 后创建 `IsoMediaSource`，随后 `BdmvSourceHelper` 为每个 clip 直接构造：

```text
new BdmvTsExtractor(...)
  -> ProgressiveMediaSource.Factory(clipFactory, bdmvFactory)
```

因此已经确认：**当前 ISO 内的 Blu-ray M2TS 不经过 App 的 `DolbyVisionP81ExtractorsFactory`。** 当前 ISO DV7 实际行为是 BDMV extractor 按 CLPI/descriptor 标记动态范围、丢弃 enhancement layer，再由 renderer 对得到的 format 尝试原生 DV 或 HDR10 fallback；App 的 libdovi mode 2 P8.1 access-unit 改写不能覆盖这条内部 factory 路径。

引入上游 combining output 后，内部 extractor 会开始输出 BL+EL 组合 AU，但仍不会自动获得 P8.1 wrapper。若设备不支持完整 DV7，而用户选择“升级 P8.1”，现有设置名与实际 ISO 行为将不一致；若 renderer 走 HDR10 fallback，则应确认组合 AU 中追加的 EL 是否会被普通 HEVC decoder 忽略，还是需要在 extractor 侧显式只输出 BL。

**阶段 A6-11 必须先定义单一策略所有权，再写代码：**

1. 原生 DV7 能力路径：允许 combining output，验证设备可消费 BL+EL+RPU；
2. P8.1 路径：把与 App 相同的转换 wrapper 明确注入 BDMV 内部 output/factory，转换时只保留所需 BL+RPU，不能先无条件合并后再假设 wrapper 可见；
3. HDR10 路径：在 extractor 或 renderer 进入 codec 前确保只送 BL，不能依赖所有 vendor decoder 自动忽略 EL；
4. 策略锁定整次播放，clip 切换不能在原生 DV7、P8.1 和 HDR10 之间来回改变 format；
5. 增加配对成功、PTS 不匹配、队列溢出、BL-only、EL 丢弃、峰值缓存字节和实际输出 profile 的诊断。

推荐先做只记录不改变输出的 pairing dry-run，收集真实 ISO 的 dependency PID、PTS 差值和 AU 大小；确认样片与目标设备后，再决定是否启用真正的 AU 合并。该阶段不能与普通 MPLS/CLPI 修复一起合并，也不能因上游已实现就默认开启。

### 27.8 可实施阶段与当前建议

| 阶段 | 关联 commit | 可实施内容 | 前置条件/验收 | 当前建议 |
| --- | --- | --- | --- | --- |
| A6-9 HDMV reader 回归 | 上游 `b3a78a2f7a9353359a02efe61e94038238c04fa1`；fork `95cd47cb3f6da7e19b593409022fee7365c80c4e` | LPCM/VC-1/HDMV DTS/TS-M2TS 覆盖确认 | Blu-ray LPCM、VC-1、各 DTS 变体、seek/连播 | 已覆盖，不合并代码 |
| A6-10a DVD ID/header safety | 上游 `15d8d21f3354e6da48c5a47751a3edb943f9ffc6`；fork `085a55d8e4b77c78e2738c62e972dac777411093` | DTS/LPCM 扩展 ID、raw AC3 双字节判断、header/LPCM 校验 | 标准/非标准 VOB、短包、误判和新增 ID 样片 | 中低风险窄 hunk 候选 |
| A6-10b DVD logical mapping | 同上，并与后续 DVD IFO commit 联合 | IFO 逻辑轨到 VOB substream ID 映射 | 多语言、重排、菜单/VTS、切轨/seek | 有真实 DVD 矩阵后优先 |
| A6-10c DVD track/EOF finish | 同上 | 预创建字幕、子 reader EOF、`endTracks()` 收尾 | 截断/短片/晚字幕、普通 PS 回归 | correctness 候选；禁止覆盖 `PsExtractor` |
| A6-11 Blu-ray DV7 combine | 上游 `4d713dded8f59cac265ec612dc263b1287bb08b4`；fork `82c45f214a96f8b0da6a04f2943f5e54a9f1fdd1` | dependency PID/PTS 配对、BL+EL 合并、CSD/format/flush | dry-run 数据、目标设备、内存上限、P8.1/HDR10 接线 | 高价值高风险，独立决策，默认不启用 |

建议实施顺序为 **A6-9 回归确认 → A6-10a → A6-10b（与 IFO 同步）→ A6-10c**。A6-11 与普通 DVD/BDMV correctness 修复没有代码上线依赖，可在光盘基础栈稳定后单独实验；若要做，必须先解决 ISO 绕过 P8.1 wrapper 的策略缺口。

### 27.9 本检查点后的决策更新

- HDMV reader 主体已覆盖，`b3a78a...` 不进入合并队列，只保留 A6-9 回归。
- DVD private stream 主体已覆盖，但 A6-10a/10b/10c 是有实际意义的窄增量；其中逻辑轨映射必须等 DVD IFO commit 一起审完再定最终接口。
- 当前 `PsExtractor` 有本地 MPEG-1、seek、EOF 等语义，禁止用上游文件整体替换。
- Blu-ray playlist 主体已覆盖；`DolbyVisionCombiningTrackOutput` 是真实缺口，但属于会改变 decoder 输入的高风险功能，不和 parser 修复捆绑。
- ISO/Blu-ray 内部 extractor 已确认绕过 `DolbyVisionP81ExtractorsFactory`。A6-11 若实施，必须同时明确原生 DV7、P8.1 和 HDR10 三条路径，不能只复制 combining class。
- App 已有 Exo 与 MPV 两条 ISO 播放接线，光盘功能不是假设需求；后续判断从“是否有产品入口”转为“该增量是否有真实镜像/设备收益”。

下一检查点继续审阅 `bd3b52102a1dad1ef9d168165d0e8959fca5d03f`（DVD IFO）、`9a8c256cf14fdfce353dee039f6dd861185d7bfe`（SACD）、`6cf9aae1e4132d6a8978e53e78f57234951cfd65`（disc file types）与 `93af478b4cd2126c3844aaf2f813e24c0262eaf7`（ISO routing），并分别与 fork 的 `e1fe67788d4b3ddbdf1ba9ada4bee5c99875432a`、`6ff0cc2c367ca3e7f54186ad6c8b8b65e3f5ad66`、`b1d47ea460f6d6a8f56ec320d8ac7fbd05d46b3b`、`3a9067e0f28cfa881406d93de5adde75c5701d1e` 比较。

## 检查点 28：2026-08-21 DVD IFO、SACD、文件类型与 ISO 路由

本检查点完成光盘栈上层四个连续提交的逐提交比较，并把它们与检查点 26、27 的 UDF、BDMV 和 DVD private stream 结论合并。四个功能在当前 fork 中都有同名提交，不能按“文件已存在”判断为完整覆盖；稳定 patch-id 证明四组实现均已继续演化。

### 28.1 提交身份与去重结果

| 功能 | 上游 commit / parent / stable patch-id | fork commit / stable patch-id | 去重结论 |
| --- | --- | --- | --- |
| DVD IFO | `bd3b52102a1dad1ef9d168165d0e8959fca5d03f` / `4d713dded8f59cac265ec612dc263b1287bb08b4` / `d5bac9d7430151bae1720276f5ecda959e98e3d2` | `e1fe67788d4b3ddbdf1ba9ada4bee5c99875432a` / `aecf90a879383e4a0a98149dbbcdd384f3ad42f1` | 同主题非等价，存在实际 parser/cell 差异 |
| SACD | `9a8c256cf14fdfce353dee039f6dd861185d7bfe` / `bd3b52102a1dad1ef9d168165d0e8959fca5d03f` / `ad2b37c54f48208c9cf0c57bf6bf45c62771777b` | `6ff0cc2c367ca3e7f54186ad6c8b8b65e3f5ad66` / `0c372b92a53e9e3a60921fd7997560f8c4680b89` | TOC parser 非等价；`DsdExtractor` 主体无须重复迁移 |
| disc file types | `6cf9aae1e4132d6a8978e53e78f57234951cfd65` / `9a8c256cf14fdfce353dee039f6dd861185d7bfe` / `5e11efdd896089abc8dfb9ddf66c9dbae7d37efa` | `b1d47ea460f6d6a8f56ec320d8ac7fbd05d46b3b` / `d4269529ff3126c5b7bc8b6d68bc32881cbf0fcc` | 当前最终树已覆盖必要功能，差异不形成合并项 |
| ISO routing | `93af478b4cd2126c3844aaf2f813e24c0262eaf7` / `6cf9aae1e4132d6a8978e53e78f57234951cfd65` / `76edc67b4739846ebdd28b9371b4eb2008ee1c05` | `3a9067e0f28cfa881406d93de5adde75c5701d1e` / `5c3900ce8010d324ee9a82cc3c13802e732c9832` | 路由主体已有，但生命周期、策略与 wrapper 语义未覆盖 |

### 28.2 DVD-3：PTT、PGC 起点、章节与轨道控制字段

当前 fork 的 `DvdIfoParser` 从 `VTS_PTT_SRPT` 只读取标题对应的第一个 PGC number，然后从该 PGC 的第一个 program 开始组装全部 cell；章节时间也主要按已保留 cell 的累计时间推导。上游读取该 title 的完整 `(PGC number, program number)` 表，并用首个 `startProgram` 经 PGC program map 定位真正的起始 cell，再用全部 PTT entry 构建章节。

这不是纯边界优化。一个 title 可以从 PGC 中部 program 开始；当前实现会把此前的菜单、片头或别的 program 当作标题内容，造成起播点、总时长、章节和 seek map 同时错位。上游还跳过 start cell 之前的 angle/block cell，并保留原始 cell 时间用于章节换算。

PGC 轨道控制字段也存在明确位解析差异：

- 当前 audio control 在 active bit 后读取 `ctrl & 0x7F`，上游按规范读取高字节中的 `(ctrl >>> 8) & 0x07`；
- 当前 subtitle control 固定读取最高字节，上游依据视频是否为 16:9，分别选择 wide 或 4:3 subpicture stream number，并限制为 5 bit；
- 上游得到的是 PGC 中“逻辑轨序号对应的 VOB substream index”，必须由 A6-10b 的 `audioStreamIndexBySubStreamIndex` / `subpictureStreamIndexBySubStreamIndex` 反向映射后，才能正确查语言、active 状态和排除规则。

因此 **A6-10b 现在可确定为真实缺口，且不能只改 private reader。** parser 的 active 数组、private reader 的反向映射和 App 轨道列表必须在同一阶段保持同一语义。

上游同时为 PTT SRPT、PGCIT、PGC offset、VOBU ADMAP 增加长度、对齐和范围校验，动态读取真实 table length，并将单表限制为 8 MiB。当前 fork 对 PGCIT 固定读 16 sectors、PGC 固定复制 4 sectors，多个 offset 在使用前缺少完整上界校验；损坏或特制 IFO 可能产生越界、超大分配、错误回退到 PGC 1，或静默生成错误章节。

**阶段 A6-12a：DVD title/track parser correctness。** 联合选择性移植 PTT 全表、start program、章节换算、audio/subpicture control 位解析和 IFO table 边界，并同步完成 A6-10b 的逻辑轨映射。禁止用上游 `DvdIfoParser` 整文件覆盖当前 fork。最低验收包括 title 从非首 program 开始、一个 PGC 多 title、非连续 audio ID、16:9/4:3 字幕 ID、multi-angle、菜单域、多 VTS、损坏/截断 PTT/PGCIT/ADMAP 和大表上限。

### 28.3 DVD-4：多 extent、STC discontinuity 与 reader 实例隔离

当前 `buildVobPartMap()` 对每个 VOB 只记录 `IsoFileEntry.byteOffset + length`，cell 也只有一个物理 `byteOffset`。上游遍历每个 VOB 的全部 `extentOffsets/extentLengths`，把 VOB-set-relative sector range 切成一个或多个物理 extent，并保留标准定义的未记录 extent。IFO 文件读取本身也改为按逻辑 offset 跨 extent 读取，而不是假定 IFO 连续。

这部分必须建立在 A6-8b/A6-8c 的 `IsoFileEntry` 与虚拟 datasource 上。只升级 IFO parser 而不升级 datasource，会得到数组 API 不匹配；只升级 datasource 而让 `DvdIfoParser` 继续输出单 offset，则 split VOB/IFO 仍不可播放。

上游还从 cell playback flags 记录 `stcDiscontinuity`，由 `DvdSourceHelper` 将连续 cell 分组，在真正的 STC discontinuity 处分隔为新的 `PsExtractor`/`TimestampAdjuster`。这可避免每个 cell 都重建 demuxer造成轨道和时间戳抖动，同时在时间基断点处正确重置。

当前 fork 在所有 child `ProgressiveMediaSource` 间复用同一个 `DvdPrivateStreamReader` 实例。该 reader 会缓存动态创建的 audio/subtitle 子 reader 和最初的 `ExtractorOutput`/`TrackOutput`；后一个 child source 复用它时，已有 sub reader 可能继续向前一个 source 的 output 写入。这是独立于多 extent 的 correctness 风险。上游在每个 cell group 的 extractor factory 内新建 reader，隔离 track/output 生命周期。

需要保留一个上游风险点：`DvdSourceHelper` 把时长至少 1 秒的 cell 定义为“promising”，最终只保留包含 promising cell 的 group。若合法短标题、菜单或测试片全部小于 1 秒，会整组丢弃。该阈值不能原样作为默认产品策略；应基于有效 sector、PGC/title 选择和真实 duration 判断可播放性，并将“过滤可疑短 cell”做成可诊断的窄规则。

**阶段 A6-12b：DVD extent/cell 生命周期。** 在 A6-8b/A6-8c 后联合迁移 VOB/IFO 多 extent、cell extent 映射、STC discontinuity grouping 和每组独立 reader；随后接 A6-10c 的预建字幕与 EOF 收尾。验证需覆盖 split VOB、split IFO、未记录 extent、跨 VOB part 的 cell、连续/不连续 STC、全短 cell、远程 Range、seek、切轨、最后 sample 和多个 child source 的 `TrackOutput` 归属。

### 28.4 SACD：TOC 冗余修复有价值，但播放闭环尚未成立

当前 fork 只检查 master TOC sector 510，固定从 sector 544 读取第一个 area TOC，再用 `firstArea.audioEndSector + 1` 猜第二个 area。TRL1/TRL2 也固定在 area TOC 后的相对 sector。该假设只覆盖布局最简单的镜像。

上游 `SacdTocParser` 的实际增量包括：

- 依次检查 master TOC 的 510、520、530 三份副本；
- 从 master TOC 指针读取 stereo/multichannel area 的 primary 和 backup TOC，不再猜 `audioEnd + 1`；
- 在 area TOC 声明的最多 96 sectors 内搜索 `SACDTRL1` / `SACDTRL2`；
- TRL 缺失时用 `audioStartSector`、`audioEndSector - audioStartSector + 1` 和 area 总播放时长构造单轨 fallback；当前 fork 从 sector 0、长度 `audioEndSector` 回退，会把 TOC/非音频区域误当音频并少算一个 sector；
- 对每轨缺少 TRL2 duration 时，才按 DSD 数据率估算时长。

这些是解析正确性提升，适合做窄移植；但不能把它表述为“补齐 SACD 播放”。当前 Exo `FfmpegLibrary.getCodecName()` 没有 `AUDIO_DSD`/`AUDIO_DST` 映射，`AudioCapabilities` 的已验证 passthrough 白名单也不包含 DSD。DSD 可能只在少数厂商 MediaCodec 上可用，DST 则没有已确认的通用 decoder 路径。`DsdExtractor` 能输出 sample，不代表 renderer/audio sink 能消费。

上游还有一个可调整的失败策略：若 master TOC 声明某个 stereo/multichannel area 存在，但 primary 和 backup 都损坏，parser 会让整个 SACD 失败，即使另一 area 有效。WebHTV 若只需“至少播放一个有效 area”，可考虑保留有效区并把损坏区从 edition 列表排除，同时记录诊断；若要求镜像完整性，则维持严格失败。

**阶段 A6-13a：SACD TOC correctness。** 只迁移 master/area 冗余、TRL 搜索和正确 fallback，补副本损坏、单区有效、双区、无 TRL、错误指针和 Range 矩阵。**阶段 A6-13b：DSD/DST decoder 闭环。** 先确认目标设备、nextlib FFmpeg 是否实际编入 DSD/DST decoder、renderer MIME 映射、PCM 输出成本和 DST seek，再决定是否上线。A6-13a 可先合入 parser，但在 A6-13b 未完成前应把 SACD 标记为实验能力，默认不作为“完整支持”发布。

### 28.5 文件类型注册：当前树已覆盖，不进入合并队列

`6cf9aae...` 新增 ISO/M2TS/DSF/DFF 常量、扩展名和 MIME 推断，并在 `DefaultExtractorsFactory` 注册 `M2tsExtractor`、`DsfExtractor`、`DffExtractor`。当前 fork 的最终 `FileTypes`、`MimeTypes` 和 extractor factory 已具备这些必要接线；剩余差异主要来自格式化、枚举顺序及后续提交叠加，不构成新功能缺口。

因此不 cherry-pick `6cf9aae...`，只补大小写扩展、URI query/fragment、显式 MIME 与 sniff 冲突的回归。上游最终树中 AV3A/TrueHD MIME 变化和全局 subtitle parser 默认行为不是该提交的必要组成，禁止借 file-types 合并夹带。

### 28.6 ISO 路由：资源生命周期、错误策略和统一 wrapper

当前 fork 已能按 `.iso`/`video/x-iso` 创建 `IsoMediaSource`，但在识别 ISO 后直接 `return`。这绕过 `DefaultMediaSourceFactory` 后半段统一处理的外置字幕合并、`ClippingMediaSource` 和 ads wrapper。上游改为先赋值 `mediaSource`，再与其它 source 一起经过这些包装，因此该差异对 App 通用播放语义有直接意义。

当前 `IsoMediaSource.Factory.setLoadErrorHandlingPolicy()` 是空实现；parse loader 使用硬编码最小重试计数，错误 callback 又直接返回 fatal；Blu-ray/DVD/SACD child `ProgressiveMediaSource.Factory` 也没有收到 App 的 policy。结果是用户配置的超时/重试策略对 ISO 无效，远程镜像的瞬态错误与损坏镜像无法按同一规则区分。

上游把 policy 传给 parse 与全部 child source，并使用 `getRetryDelayMsFor()` 决定重试。移植时应再收窄：只对连接重置、超时、可恢复 5xx/Range 等瞬态错误重试；UDF/IFO/MPLS/TOC 格式错误和稳定的 4xx 不应触发整镜像重复解析。每次 load task 结束还要成对调用 `onLoadTaskConcluded()`。

当前 `IsoParseLoadable.result` 是普通字段。取消只设置 boolean；若 parse 在取消竞态中刚生成 `IsoParsedMedia`，`onLoadCanceled()` 为空且 result 没有被 source 接管，内部 `IsoDataReader` 可能不被关闭。上游用 `AtomicReference`、`takeResult()` 和 `releaseResult()` 保证 completed/canceled/error/release 只有一个所有者并恰好 close 一次。

child cache key 也需要修复。当前键只含 `clipName/startM2ts`、`cell.byteOffset` 或 `track/offset`；不同父 ISO 中同名 M2TS、相同 cell offset 或 SACD track 可命中彼此缓存。上游把父 ISO URI 或父 custom cache key 编入 key，隔离不同镜像。

上游 `BdmvSourceHelper` 的多 extent/read/prefetch 属于 A6-8b/A6-8c 的上层消费方；移植时必须保留 fork 已有的 `cumulativeOffsetUs` 接线。上游最终 helper 直接构造 `BdmvTsExtractor` 而不传累计时间偏移，若原样覆盖会让多 clip playlist 的 timestamp adjuster 重新从零开始，破坏当前连续时间轴。

建议拆成三个可独立回滚的阶段：

- **A6-14a parse cancellation/lifecycle：** `AtomicReference` 所有权、cancel/error/release close-once、loader task 收尾；低风险优先候选；
- **A6-14b load policy：** policy 传到 parse 和 child，错误分类后仅重试瞬态网络失败；需要远程 HTTP/SMB/content URI 故障注入；
- **A6-14c source wrappers/cache identity：** ISO 不再提前 return，恢复外置字幕、clipping、ads，并让 child cache key 包含父镜像身份；需验证无字幕/无 ads 的普通路径不变。

### 28.7 通用 C3：Exo 基础层升级必须同步 MPV 的 ISO 元数据读取

App 的 MPV ISO 播放不是完全独立于 Media3 parser。`IsoPlaybackSession` 会调用 `IsoTrackMetadataResolver`，用 Media3 的 `UdfFileSystem`、MPLS/CLPI parser 为 MPV 轨道补语言。当前 `readEntry()` 仍按 `entry.byteOffset + read` 把每个 metadata 文件当作单一连续 extent。

一旦 A6-8b 把 `IsoFileEntry` 升级为 `extentOffsets/extentLengths`，Exo 内部 helper 能正确读 split MPLS/CLPI，但 MPV App 接线仍可能从错误物理位置读取后半段，表现为语言缺失、轨道错配或 metadata EOF。该问题不会由上游 `media` commit 自动修复，因为 resolver 是 WebHTV App 代码。

**阶段 C3：ISO metadata extent consistency。** 必须随 A6-8b/A6-8c 一起把 `IsoTrackMetadataResolver.readEntry()` 改为按逻辑 offset 跨 recorded/unrecorded extent 读取，并补 split MPLS、split CLPI、未记录 extent、8 MiB 上限、短读和取消测试。它在代码归属上是 App 通用层，在实施顺序上随 Exo 光盘基础层合入，在验收上同时覆盖 Exo 与 MPV；不要求提前升级 MPV/FFmpeg/libplacebo native lock。

### 28.8 可实施阶段与联合顺序

| 阶段 | 关联 commit | 可实施内容 | 依赖/验收 | 当前建议 |
| --- | --- | --- | --- | --- |
| A6-12a DVD parser correctness | 上游 `bd3b52102a1dad1ef9d168165d0e8959fca5d03f`；fork `e1fe67788d4b3ddbdf1ba9ada4bee5c99875432a`；并联 A6-10b | PTT/start program/章节、轨 control 位、IFO table 边界、逻辑轨映射 | 非首 program、多 title/PGC、重排 audio/subtitle、损坏表 | 高价值 correctness，优先候选 |
| A6-12b DVD extent/cell lifecycle | 同上；并联 `990abc...`、`15d8d21...`、`93af478...` | VOB/IFO 多 extent、STC grouping、独立 reader、EOF | A6-8b/c、A6-10c；split VOB/IFO、短 cell、Range/seek | 必须联合实施，禁止单独升级 parser |
| A6-13a SACD TOC | 上游 `9a8c256cf14fdfce353dee039f6dd861185d7bfe`；fork `6ff0cc2c367ca3e7f54186ad6c8b8b65e3f5ad66` | master/area primary/backup、TRL 搜索、正确 fallback | 副本损坏、单双 area、无 TRL、远程读 | parser 候选；能力仍标实验 |
| A6-13b SACD decode | 同上，并关联 nextlib FFmpeg/renderer/audio sink | DSD/DST MIME、decoder、PCM/passthrough、seek | 真实设备与样片、CPU/内存/声道矩阵 | 未闭环，暂缓 |
| A6-14a ISO lifecycle | 上游 `93af478b4cd2126c3844aaf2f813e24c0262eaf7`；fork `3a9067e0f28cfa881406d93de5adde75c5701d1e` | parse result 单一所有权、取消/错误/release close-once | cancel race、parse 后取消、重复 release | 低风险优先候选 |
| A6-14b ISO load policy | 同上 | parse/child policy 传递、瞬态错误 retry、task concluded | HTTP/SMB/content URI 故障分类 | 条件合并，避免损坏镜像重扫 |
| A6-14c ISO wrapper/cache | 同上 | 外置字幕、clipping、ads、父级 cache identity | subtitle/clip/ads、跨镜像缓存污染 | 通用行为修复，建议合并 |
| C3 ISO metadata extents | WebHTV `IsoTrackMetadataResolver`，由 `990abc...` 的 `IsoFileEntry` API 触发 | MPV 语言 metadata 跨 extent 读取 | 与 A6-8b/c 同步，Exo/MPV 联合验收 | 随 Exo 基础层合入，不等 MPV native 阶段 |
| file types 回归 | 上游 `6cf9aae1e4132d6a8978e53e78f57234951cfd65`；fork `b1d47ea460f6d6a8f56ec320d8ac7fbd05d46b3b` | 只补 URI/MIME/大小写测试 | 不夹带 AV3A/字幕默认变化 | 已覆盖，不合并代码 |

光盘基础栈的建议顺序更新为：

1. A6-8a reader safety；
2. A6-8b UDF extents + A6-8c virtual datasource + C3 App metadata extents；
3. A6-9 HDMV 回归，并确认保留 fork 的 `cumulativeOffsetUs`；
4. A6-10a + A6-12a（含 A6-10b）完成 DVD parser/轨映射；
5. A6-12b + A6-10c 完成 DVD extent、cell/reader 与 EOF 生命周期；
6. A6-14a → A6-14b → A6-14c 完成 ISO source 生命周期、错误策略和统一 wrapper；
7. A6-13a/A6-13b 按 SACD decoder 决策实施；
8. A6-11 Blu-ray DV7 combine 最后独立 dry-run/决策，不与基础 correctness 捆绑。

### 28.9 本检查点后的决策更新

- DVD IFO 不是“已有即可跳过”：PTT start program、PGC control 位、多 extent、边界和 STC grouping 都有真实增量；A6-12a/12b 应进入 Exo 光盘候选队列。
- A6-10b 已确认必须与 A6-12a 同步，A6-10c 应与 A6-12b 同步；DVD private reader、IFO parser 和 source helper 不能分批上线不兼容语义。
- 上游 1 秒 promising-cell 规则可能丢弃合法短标题/菜单，不原样采用。
- SACD TOC 修复本身有价值，但 DSD/DST renderer/decoder 未闭环；在 A6-13b 完成前不得宣称完整 SACD 支持。
- ISO file type 注册已覆盖；`6cf9aae...` 不进入 cherry-pick 队列。
- ISO cancel、load policy、cache identity 和统一 subtitle/clipping/ads wrapper 是独立的实际缺口；其中 A6-14a、A6-14c 优先级高于新增光盘格式。
- A6-8b/A6-8c 不再只是 Exo 内部改动：必须同步 C3 `IsoTrackMetadataResolver`，否则 MPV ISO 语言 metadata 路径会在 split MPLS/CLPI 上退化。
- App 已有真实 Exo/MPV ISO 入口，A6 的门槛应改为“是否有对应镜像/设备矩阵和可接受风险”，不再要求重复证明产品入口存在。

下一检查点继续审阅 `7b787fe2a5616e684d9c0b77b8481724ada4afae`（embedded artwork）、`85add599da1230a62715a232ffa8e87d50638a3e`（generated track names）与 `845f6fddd3953c36b08c2a878301649f918a1911`（danmaku UI），先判断是否已被 App metadata/轨道 UI/本地弹幕实现覆盖，再进入 A2 的 decode mode、FFmpeg renderer 与二进制资产组。

## 检查点 29：2026-08-21 DSF/DFF extractor 专项补审

`5bca32949e0ad82cb0105962a7ae31234d6cd1a8` 位于 ISO/UDF 与 HDMV 之间，前一轮光盘联合审计只引用了其产物，没有单独评估 extractor 语义。本检查点补齐该提交，避免把“文件已存在”和“DSD MIME 已注册”误写成完整播放支持。

### 29.1 commit 身份与当前覆盖

| 项目 | 上游 | 当前 fork | 结论 |
| --- | --- | --- | --- |
| commit | `5bca32949e0ad82cb0105962a7ae31234d6cd1a8` | `22541f91d701869d023f419fae0c906d71edabc4` | 同标题、同 author date，但重落基后实现继续修正 |
| parent | `990abc2368fd74779f525ee345734470659f3d53` | `39585f19e01324308213e2bdc9aa84dcfa4d5ebc` | 分别基于上游/本地 ISO 初版 |
| stable patch-id | `120b5da8f004532053cf6a61e5ebba97f421e30b` | `98146d402ebc3420221c5e86eb9f43e94e743ff2` | 不等价 |

两边都新增 `DsfExtractor`、`DffExtractor`、`DsdBitrateSeekMap`、`SacdSectorSeekMap`，并把旧 `audio/dsd` 常量替换为 DSD/DST/planar MIME 组。`SacdSectorSeekMap` blob 精确相同，`DsdBitrateSeekMap` 仅格式化；真实增量集中在 DSF 尾块和 DST 时间轴。

### 29.2 DSF：上游修复 planar 尾部 padding 泄漏

DSF 按声道 planar block 存储，最后一个 block 常按每声道 block alignment 填充。当前 fork `DsfExtractor` 只以 data chunk 的物理 `audioDataSize` 为结束条件，会把尾部 padding 一并输出；这可能增加静音/噪点、让 sample 时长超过 header 的 sample count，并在不同声道的 padding 长度不一致时破坏 planar 数据边界。

上游保留 header 的 `sampleCount`，计算真实 `audioSizeBytes = min(sampleCount / 8 * channelCount, audioDataSize)`；最后一个物理 block 含 padding 时，按每个 channel 的 block offset 只抽取有效字节，再组合成最终 sample。它同时以真实字节数推进 timestamp，而不是物理 padding。

该 hunk 有明确 correctness 价值，但当前实现仍缺少输入防御：`channelCount`、`sampleRateBits`、`blockAlign` 为 0 或乘法溢出时可除零/超大分配；`data chunk size < 12` 会产生负长度；sniff/header 对截断输入依赖底层 EOF。移植时应把这些校验和上游尾块修复一起补齐，而不是只复制 `readPaddedFinalBlock()`。

**阶段 A6-15a：DSF valid-size/padding correctness。** 选择性移植 `sampleCount` 限制、per-channel 尾块去 padding，并增加 header/算术上限。验证至少包括 mono/stereo/5.1、LSBF/MSBF、完整/截断 header、sample count 小于/等于/大于物理数据、最后 block 有/无 padding、零 channel/rate/block、超大 size、unknown input length 和 seek 后首个 sample timestamp。

### 29.3 DFF/DST：上游改用 frame count，避免伪 bitrate 时间轴

当前 fork 对 DST frame 读取后，用 `bytesPerSecond / 75` 增加一个“虚拟输出字节数”，再复用 DSD `sampleTimeUs()` 推进时间。其数学结果在 `bytesPerSecond` 可整除且字段合法时接近 75 fps，但把压缩 DST 的时间轴绑定到未压缩 DSD bitrate，存在整数截断累积，并在 `bytesPerSecond == 0` 时除零。

上游为 DST 单独记录 `dstFramesOutput`，使用 `Util.scaleLargeTimestamp(frameIndex, 1_000_000, 75)` 生成 PTS；遇到 `FRTE` 后以 frame count 更新 duration，并再次发布明确不可 seek 的 seek map。这样时间轴与压缩 frame 大小、声道和 DSD bitrate 解耦，长音频不会因每帧整数截断积累漂移。

该提交仍没有提供可靠的 DST seek：`seekable=false` 是正确的保守行为。还需增加 chunk size 加法溢出、`DSTF size > Integer.MAX_VALUE`（当前会截为最大 int 并尝试巨量分配）、奇数 padding、未知/嵌套 chunk、缺 PROP/FS/CHNL/CMPR/FRTE、截断 header 和 skip 无进度的限制。

**阶段 A6-15b：DFF/DST frame timeline 与边界。** 移植 frame-count timestamp 和 FRTE duration/seek-map 更新，同时给 chunk/frame size 设置实际内存上限。DSD form 继续按 bitrate seek；DST 只支持线性播放或明确不可 seek，不伪造随机访问能力。

### 29.4 MIME、decoder 和 AudioTrack：与 A6-13b 共用同一门槛

DSF 输出 `AUDIO_DSD_LSBF_PLANAR` / `AUDIO_DSD_MSBF_PLANAR`，DFF 输出 `AUDIO_DSD` 或 `AUDIO_DST`。当前 `MimeTypes.getEncoding(AUDIO_DSD)` 可映射 `C.ENCODING_DSD`，但：

- `FfmpegLibrary.getCodecName()` 没有 DSD、planar DSD 或 DST 映射；
- `AudioCapabilities` passthrough 白名单没有 DSD；
- Android vendor decoder 对 planar DSD/DSD/DST MIME 的支持不可假定；
- 即使设备接受 `ENCODING_DSD`，DSF 的 planar bit order 是否需要重排、支持的 sample rate/声道与 AudioTrack encoding 仍需设备验证。

因此 A6-15a/b 只能称为 extractor correctness。实际播放闭环与检查点 28 的 **A6-13b** 完全共用 decoder/renderer/audio sink 决策；不应另造一套 DSD 策略。若 A6-13b 不实施，DSF/DFF 仍可用于 metadata/extractor 测试，但不能作为面向用户的可靠格式支持。

### 29.5 实施建议

| 阶段 | 来源 commit | 内容 | 当前建议 |
| --- | --- | --- | --- |
| A6-15a DSF tail | 上游 `5bca32949e0ad82cb0105962a7ae31234d6cd1a8`；fork `22541f91d701869d023f419fae0c906d71edabc4` | sample count、planar 尾块去 padding、header/算术边界 | 中风险 correctness 候选；有 DSF 样片再实施 |
| A6-15b DFF/DST time | 同上 | 75 fps frame-count PTS、FRTE duration、不可 seek map、chunk/frame 上限 | correctness 候选；与 DST 样片联动 |
| A6-13b DSD/DST playback | `5bca329...` + `9a8c256...` + nextlib/renderer/audio sink | MIME 到 decoder/PCM/passthrough 的完整闭环 | 未确认，默认暂缓 |

不整体 cherry-pick `5bca329...`：当前 fork 的 `MimeTypes` 前置还包含 TrueHD/AV3A 等本地演化，整文件覆盖会回退无关语义。只按 A6-15a/b 移植 extractor hunk，并保留现有 `SacdSectorSeekMap` 和格式注册。

### 29.6 本检查点后的恢复锚点

- `media` #58 DSF/DFF 已补审，A6 光盘序列不再有中间缺口。
- 当前 fork 有 DSF/DFF extractor 主体；上游有意义的增量是 DSF 尾 padding 和 DST frame-count 时间轴，不是新增整个格式。
- A6-15 与 A6-13b 共用播放闭环；parser/extractor 修复可以独立评估，但用户可播放能力必须统一决策。
- 下一组仍为 #67-69：`7b787fe2a5616e684d9c0b77b8481724ada4afae`、`85add599da1230a62715a232ffa8e87d50638a3e`、`845f6fddd3953c36b08c2a878301649f918a1911`。

## 检查点 30：2026-08-21 Media3 metadata、轨道名称与弹幕 UI（#67-69）

本检查点完成 `media` 提交索引中最后一组已经落到 WebHTV fork 的 UI/metadata 提交。三项不能按同一种方式处理：artwork 和 track-name 的主体已经在 fork 中重落基存在，danmaku 则是上游对同一初始实现的一次大规模架构重写；当前工作树还包含用户自己的 live danmaku 生命周期、限流和诊断改动，不能用上游整提交覆盖。

### 30.1 提交身份、父链与 patch-id

| 功能 | 上游 commit / parent / tree / stable patch-id | 当前 fork commit / parent / tree / stable patch-id | 去重结论 |
| --- | --- | --- | --- |
| embedded artwork | `7b787fe2a5616e684d9c0b77b8481724ada4afae` / `93af478b4cd2126c3844aaf2f813e24c0262eaf7` / `dc857c61e99b2376f0fc78ca93432acbb3baa793` / `cd876224408e2baecdc9540c36e7621c500b59bc` | `e4e4e5f1229f3398390df70eb157bd184d9bb7ff` / `3a9067e0f28cfa881406d93de5adde75c5701d1e` / `4774d00cf61d99390122752b0bda72975d1d42e3` / `685b6ac176954d842e3e625e4101cfcc9ddb17dc` | patch-id 不同但只有重落基/格式差异，语义主体等价 |
| generated track names | `85add599da1230a62715a232ffa8e87d50638a3e` / `7b787fe2a5616e684d9c0b77b8481724ada4afae` / `91d4e81130731ab008b21638e6066539fb771d29` / `c40f884e6bf0f85df1d6a9471a3497b98384e624` | `e96590f7163eb420ceda7ae9748176bb7645c5af` / `e4e4e5f1229f3398390df70eb157bd184d9bb7ff` /（当前 fork 树）/ `3151910241297016ce85108d31bce6a5e3d06803` | 主体等价，但上游后续窄修复尚未全部进入 fork |
| danmaku UI | `845f6fddd3953c36b08c2a878301649f918a1911` / `85add599da1230a62715a232ffa8e87d50638a3e` / `fad7de3760d8316096511c975e4280feda399ea8` / `5e6bb3dc2078cbf3a8a1d384583ac6c13f04c8c5` | `b7ae8eea1ae5af7d330327045da4ece3c224a5c9` / `e96590f7163eb420ceda7ae9748176bb7645c5af` /（当前 fork 树）/ `653b0edfc3424ad6c4aedf945463e8ef086da82f` | 非等价架构；不能 cherry-pick 上游整体 |

说明：fork 的最终 `media` 源码仓库当前仍有用户未提交修改（`libraries/ui_danmaku` 的 build、controller、view、model 和 live 测试）。本检查点只把这些修改作为现状证据，不将其视为可丢弃的审计临时文件。

### 30.2 A7-1：embedded artwork 的主体已覆盖，但有真实消费语义需要回归

上游 `7b787fe...` 改动两个位置：

1. `ExoPlayerImpl.buildUpdatedMediaMetadata()` 在 `MediaItem.mediaMetadata.artworkData == null` 且 static/dynamic track metadata 有 `artworkData` 时，把内嵌图片补回最终 `MediaMetadata`；
2. `ExoPlayerImplInternal` 在已选 track 没有非空 metadata 时，回退扫描 `TrackGroupArray`，从未选轨道中寻找 metadata。

fork 的 `e4e4e5...` 逐 hunk 等价实现，只是 import、换行和父链不同，因此不应再次合并上游 commit。该功能对本项目仍有意义，因为当前 extractor 已能从 ID3/FLAC 等 metadata 产生内嵌 artwork，而 App 的 `PlayerManager.buildMetadata()` 通常只设置 `artworkUri`：

```text
App MediaItem metadata: artworkUri = episode artwork URL
媒体轨道 metadata: artworkData = 文件内嵌图片
最终 MediaMetadata: 上游规则可能同时保留 URI 和 bytes
BitmapLoader: artworkData 优先于 artworkUri
```

这带来三个必须明确的产品/内存问题：

- `MediaMetadata.Builder.populate()` 只要源 metadata 的 `artworkUri` 或 `artworkData` 任一非空，就会先设置两者；上游随后只检查 `artworkData == null`，所以 App 已有 URI 时仍可能补入 embedded bytes。`BitmapLoader.loadBitmapFromMetadata()` 明确先 decode bytes，再访问 URI，通知和 MediaSession 可能因此从远程封面切换到文件内嵌封面。
- `ExoPlayerImplInternal` 的 fallback 按 `TrackGroup` 顺序取第一条有 metadata 的 Format，没有按 `PICTURE_TYPE_FRONT_COVER`、图片大小或当前语言轨道筛选；多音轨/多封面文件可能得到不可预测的封面。
- `MediaMetadata.Builder.setArtworkData()` 会 clone byte array；通知的 `SizeLimitedBitmapLoader` 限制 bitmap 尺寸，不等于限制压缩 bytes、Binder 传输或 metadata 保留内存。恶意超大 APIC/FLAC picture 仍可能造成内存峰值。

当前 App 直接使用 `artworkUri` 构建播放 metadata，服务端 `Media` 输出也只读取 `artworkUri`；因此 #67 的可见收益主要落在 Media3 notification/MediaSession 和含内嵌封面的本地音频，而不是 App 自有封面 API。建议不要再 cherry-pick，而是开 **A7-1 artwork policy/regression**：

- 先决定“用户显式 episode artwork URI”是否优先于 embedded bytes；若 URI 必须优先，需把 fallback 条件收窄为 URI 与 data 均为空，或在 App 层明确选择封面来源；若 embedded 优先，则记录这是有意行为；
- 为 embedded compressed bytes 设置解析/保留上限，并在 metadata、notification、MediaSession、轨道切换和 release 时测量峰值；不能只依赖 bitmap 尺寸限制；
- 增加多轨/多封面、URI+embedded 同时存在、只有未选轨有 artwork、超大/损坏图片和切换音轨测试；
- 保留 fork 的 metadata 其它本地改动，不用上游 `ExoPlayerImpl*` 整文件替换。

**建议：主体已覆盖，进入回归和策略决策，不进入 merge queue。**

### 30.3 A7-2：generated track names 有用，但当前 fork 与上游存在跨 A3 的窄差异

`85add599...` 新增 `FormatNameUtil`（AAC profile、TrueHD Atmos、DTS:X、Dolby Vision profile、HDR、PCM 深度、DSD/DST、字幕 MIME 等）并让 `DefaultTrackNameProvider` 显示视频 FPS、轨道 MIME 名称及语言/label 组合。App 的 `TrackDialog` 明确实例化 `DefaultTrackNameProvider`，所以这是用户可见功能，不是仅供 demo 的改动。

fork 的 `e96590...` 已有 314 行 `FormatNameUtil` 和对应 provider 改动；上游与 fork 的主体差异经逐文件比较只有几个有意义的窄 hunk：

1. **`APPLICATION_MEDIA3_CUES`**：上游先把 `format.codecs` 的底层 MIME 取出来再显示；fork 没有该分支。对提取字幕/内部 cues 轨道有帮助，但不能把 `application/x-media3-cues` 直接显示给用户。
2. **TrueHD Atmos codec 名**：上游使用新常量 `MimeTypes.CODEC_TRUEHD_ATMOS = "truehd-atmos"`；当前 fork 仍由 `TrueHdReader`、Matroska extractor 和 `FormatNameUtil` 使用旧字符串 `"atmos"`，当前 `MimeTypes` 也没有该常量。这个 hunk 不能单独移植，必须随 A3-2 TrueHD/Atmos parser、MIME 和 Matroska 本地补丁一起迁移，否则会出现编译/API 不一致或 Atmos 名称退化。
3. **未知轨道 fallback**：上游在拼接名称为空时保留 `exo_track_unknown` / `exo_track_unknown_name`；fork 的提交直接返回拼接结果，空 Format 可能显示空行。应恢复 fallback，并为没有 language/label/MIME 的视频、音频、字幕各加测试。
4. **语言别名风险**：上游把 `awr -> zh`、`awq -> yue`、`qph -> yue`、`chs/cht -> zh-Hans/zh-Hant`。IANA language registry 将 `awr` 定义为 Awera，不能静默标为中文；`zh-cmn` 是 Mandarin extlang，直接压成泛 `zh` 也会丢信息；`awq/qph` 没有足够可靠的通用标准依据。应删除这些通用映射，改为明确的来源/格式白名单，或仅保留已确认的 `chs/cht` legacy DVD alias。
5. **FPS 取整**：上游 `(int) Math.floor(frameRate) + "FPS"` 会把 59.94 显示为 `59FPS`、23.976 显示为 `23FPS`。这会误导用户选择轨道；建议用稳定的一位小数（或 59.94/23.976 的格式化规则），并测试未知、整数、NTSC fractional FPS。

其余 MIME 显示名称本身收益明确，尤其是 AAC-LC/HE-AAC、DTS-HD MA/DTS:X、DV profile、HDR10/HLG、LPCM bit depth、PGS/SRT/TTML；但它们改变的是 UI 文本，不应借此覆盖 fork 的 `MimeTypes` 或 TrueHD/AV3A 演化。

**阶段 A7-2：track-name selective cleanup。** 实施顺序为：

1. 以 fork `e96590f7163eb420ceda7ae9748176bb7645c5af` 为基线，保留主体 `FormatNameUtil`；
2. 单独移植 `APPLICATION_MEDIA3_CUES` 显示和未知轨道 fallback；
3. 等 A3-2 决定 TrueHD codec 常量后，再迁移 Atmos 命名；
4. 删除/限制错误语言别名，改正 FPS 格式；
5. 在 App `TrackDialog` 上做音频、视频、字幕和切轨回归。

该阶段不应整体 cherry-pick 上游 `85add599...`，也不应与 A3-2 的 parser/renderer 代码混成一个不可回滚提交。

### 30.4 A7-3：danmaku 上游是架构重写，当前项目已有更强的 live 语义

上游 `845f6f...` 一次增加约 6616 行、37 个文件：

- `DanmakuLoadTask`、`DanmakuSourceLoader`：为单次源加载提供取消、worker 生命周期和错误回调；
- `DanmakuTimeline`、`DanmakuSegmentLoader`、`DanmakuSegmentState`：按 segment 做窗口加载、前后填充、失败重试和 seek 映射；
- `DanmakuRenderPool`、`DanmakuMeasurement`、`DanmakuPainter`、`DanmakuTrackManager`：把测量、绘制、轨道分配和渲染池拆开；
- Bili/IQIYI/MGTV/QQ/Youku fetcher/parser：支持网页/接口解析和分段拉取；
- `PlayerView` API：把 `DanmakuView` 接入标准布局，并暴露 controller/source/config/enable/send API。

当前 fork `b7ae8...` 已有同一批 provider/parser 和基本 `DanmakuController`/`DanmakuView`，但规模和职责划分不同（初始提交约 4496 行、29 文件）。更重要的是，WebHTV 当前工作树与 App 已经在这套基础上加入了上游提交之后不存在的行为：

- `PlayerManager` 已通过 `mBinding.exo.getDanmakuController()` 接入 Exo；
- 静态源仍走 controller 的 segment/window 加载；
- live WebSocket 使用 generation 隔离、停止/重连清空、批处理、优先级队列、TTL、最大帧/队列限制、代理选择、网络恢复和周期诊断；
- `DanmakuView` 已有 `offerLiveBatch`、过期/溢出统计、每帧 activation 上限、后台测量和 `clearLiveItems`；工作树新增 `DanmakuViewLiveLoadTest` 等压力测试。

因此不能把上游 37 文件整体替换到当前分支：它会丢失或重写 App 依赖的 `offerLiveBatch`/generation/TTL/metrics/lifecycle，改变 `PlayerManager` 的静态与 WebSocket 双路径。上游 commit 自身没有随提交加入测试；当前工作树反而已经有 300 messages/s、burst capacity、expiry/clear 的回归测试。

逐项价值评估如下：

| 上游子能力 | 当前状态 | 可取部分 | 风险/建议 |
| --- | --- | --- | --- |
| segmented static source/window | fork controller 已有 segment cursor、前后窗口和 retry；当前 view 已有 pool/后台测量 | 用上游 timeline/segment state 做 benchmark 和边界对照 | 不直接迁移架构；先测长片内存、seek、失败重试和换源竞态 |
| render pool/measurement/track manager | 当前 view 已有后台测量、轨道分配，并额外支持 live bounded queue | 对比上游分层是否能降低 GC/主线程耗时 | 只做 shadow/benchmark；禁止覆盖 live 增强 |
| parser 防御 | fork 已能解析各 provider，但 IQIYI varint、截断字段、未知 wire type 的边界可更严格 | 选择性移植 `MAX_VARINT_BYTES`、skip 无进度/越界保护，逐 parser 加 fixture | 低到中风险，可独立提交；不得带入 controller 重写 |
| Youku fetch/session | 上游加入 HTTP status 检查、URL 解码抽取、cookie domain/path/过期管理、token 获取封装 | 选择性移植 cookie identity/expiry 和非 2xx 错误处理 | 需 MockWebServer；确认不破坏现有 header/proxy/cookie 行为 |
| Bili/IQIYI/MGTV/QQ provider | 多数差异是 API 注解、命名和格式；IQIYI/QQ 有少量输入校验改善 | 仅移植有测试证明的边界修复 | 不以“上游行数更多”作为合并理由 |
| PlayerView 接线 | fork 已由 `b7ae8...` 接入，App 正在使用 | 只回归自定义 layout、电视端焦点和 attach/detach | 不重复改 `PlayerView` 或布局 |

**阶段 A7-3a：静态弹幕可靠性窄修复。** 以当前 fork/worktree 为基线，先评估 IQIYI varint/未知字段边界、Youku cookie/HTTP status 处理和必要的 provider URL 校验；每个 parser/fetcher 一个可回滚提交，配 MockWebServer、截断 protobuf/XML/JSON、cookie 过期和代理测试。

**阶段 A7-3b：静态长片性能实验。** 不改默认行为，加入上游 timeline/render-pool 与当前实现的 shadow benchmark，比较 2 小时/10 万条弹幕、seek、快速换源、后台/前台和低端电视设备的堆占用、主线程帧耗时、丢弃数与可见顺序。只有实测显示当前实现不足，才另开架构迁移设计。

**阶段 A7-3c：live 兼容验收。** 无论是否采用上游静态组件，都必须保持当前 WebSocket generation、TTL、优先级、队列上限、停止/重连清空、代理和诊断；用现有 `DanmakuViewLiveLoadTest`、`LiveDanmaku*Test` 及真实网络故障注入回归。

### 30.5 本检查点的 Exo 实施总表增补

| 阶段 | 完整 commit ID | 当前 fork/应用状态 | 实施内容 | 建议 |
| --- | --- | --- | --- | --- |
| A7-1 artwork policy/regression | 上游 `7b787fe2a5616e684d9c0b77b8481724ada4afae`；fork `e4e4e5f1229f3398390df70eb157bd184d9bb7ff` | 主体已等价；App 同时设置 URI，Media3 BitmapLoader data 优先 | 确定 URI/embedded 优先级、大小上限、多轨封面和通知回归 | 不合并 commit；先做策略/测试 |
| A7-2a track-name common | 上游 `85add599da1230a62715a232ffa8e87d50638a3e`；fork `e96590f7163eb420ceda7ae9748176bb7645c5af` | `FormatNameUtil` 主体已有 | `APPLICATION_MEDIA3_CUES`、unknown fallback、MIME 名称测试 | 窄修复候选 |
| A7-2b TrueHD naming | 同上，关联 `1cc8573cab9e2453e7917aff1b8945482c8b2190` 和本地 `"atmos"` 生产点 | codec 常量尚未统一 | parser/MIME/Matroska/TrackName 一起迁移 | 随 A3-2，不能单独合并 |
| A7-2c language/FPS correctness | 同上 | 上游别名会误标，FPS 使用 floor | 删除不可靠别名；稳定显示 59.94/23.976 | 建议窄修复 |
| A7-3a danmaku parser/fetcher safety | 上游 `845f6fddd3953c36b08c2a878301649f918a1911`；fork `b7ae8eea1ae5af7d330327045da4ece3c224a5c9` | App 已有静态+WebSocket 双链和诊断 | 逐 parser/fetcher 移植有测试的边界修复 | 条件合并 |
| A7-3b danmaku architecture | 同上 | 当前工作树已有 live bounded pipeline 和压力测试 | 仅 benchmark/shadow；若需再设计迁移 | 默认不合并 |

### 30.6 本检查点后的决策更新

- #67 artwork 的 fork 版本已覆盖上游主体；真正待决策的是 App 显式 URI 与 embedded bytes 的优先级和内存上限，不是再次升级 `ExoPlayerImpl`。
- #68 track-name 的主体已覆盖且确实影响 `TrackDialog`；`APPLICATION_MEDIA3_CUES` 和 unknown fallback 可独立补，TrueHD Atmos 必须等待 A3-2，错误语言别名与 FPS floor 不应原样采用。
- #69 danmaku 上游不是可安全 cherry-pick 的增量；当前 App 的 live WebSocket、generation、TTL、限流和诊断是必须保留的本地能力。优先做 parser/fetcher 窄安全修复和性能对照，不做整体架构替换。
- `media` #67-69 已完成逐提交深审；下一组进入 A2：`2a2c8e8e122c13c0e462217f8fb5d7f0910cab97`、`ca7dd917ad574d4241640eb9282f20c5decd5aea`、`7d0d1e3c572aee885ffbbfd6d8317f1f3a581910`、`176e7f58ec3ba82cce3f5071b0a2625890e93b2d`。

### 30.7 恢复锚点

- 检查点 30 已落盘：`media` #67-69 的上游/fork commit、parent、tree、patch-id、最终树差异和 App 消费路径均已记录。
- 已确认的不可覆盖本地能力：`PlayerManager` 的 metadata/`TrackDialog` 接线、danmaku WebSocket generation/TTL/限流/诊断、工作树 `DanmakuViewLiveLoadTest`；后续审阅不得用上游整文件替换这些路径。
- A7-1 只做 artwork policy/regression；A7-2 拆 common、TrueHD 联动、language/FPS 三子阶段；A7-3 拆 parser/fetcher safety、静态性能实验、live 兼容验收。
- 下一检查点从 A2 decode mode/API 对照开始；不更新 lock、不生成 AAR/native 二进制，除非用户另行决定实施。

## 检查点 31：2026-08-21 A2 decode mode、FFmpeg 软件视频 renderer 与选择策略（#70-73）

本检查点把四个连续提交作为一个功能链审阅，但不把它们误写成一个可直接合并的阶段。#70 只是公共 API；#71 固化一整套由 `mpv-android` 产出的 FFmpeg/libplacebo 二进制 SDK；#72 才实现新的 Media3 内置 FFmpeg 软件视频 renderer；#73 再通过 track mapping 在同一 player 内选择 MediaCodec 或 FFmpeg。当前 WebHTV 已使用独立 nextlib AAR、App 自有 renderer factory、硬/软解模式、运行时 codec 黑名单、DV7 策略和软解降载，因此这里是一次**架构替换候选与设计对照**，不是常规四提交 cherry-pick。

### 31.1 提交身份与证据

| # | Commit / parent / tree | Stable patch-id | 实际内容 |
| ---: | --- | --- | --- |
| 70 | `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97` / `845f6fddd3953c36b08c2a878301649f918a1911` / `2c16a9a2efb9098b4f7a90ca2437dda420020367` | `e36d0f00a06fafc870b4ecc8715c88a81c989bea` | `C.DecodeMode`、`DECODE_SOFTWARE=0`、`DECODE_HARDWARE=1`，共 15 行 |
| 71 | `ca7dd917ad574d4241640eb9282f20c5decd5aea` / `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97` / `06c807223923f4eba6c9b25a9f7509f8d1075f10` | 二进制-only/大文件提交，标准 `git patch-id` 不产出稳定文本 patch-id；以完整 commit/tree/hash manifest 为身份 | 129 个新增文件：107 个 FFmpeg/libplacebo headers、14 个两 ABI `libav*`/`libsw*` `.so`、2 个 `libplacebo.a`、license/manifest 等 6 项 |
| 72 | `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` / `ca7dd917ad574d4241640eb9282f20c5decd5aea` / `3f9caafb186fd931e419ef7df78f7f3f991add82` | `c8e3c28f3de10157dc10d0d7fb377112d5abd2f9` | 39 文件，约 `+12941/-818`；替换 FFmpeg audio JNI，新增完整软件视频 decoder、GLES/libplacebo Surface renderer、恢复控制和通用 `DecoderVideoRenderer` 时序改造 |
| 73 | `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` / `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` / `784a0d481c515973a523a15289f256c60a152c60` | `c2e4b643eb2b760afb15c283bbc4793a11e10b10` | 新增 `DecodeTrackSelector`，扩展 `MappingTrackSelector` 的 renderer allow/prefer hook，并加 2 个选择测试 |

#71 的 provenance manifest 不是空文件，明确记录：资产来自 `FongMi/mpv-android@99a60ad2141d5ace94453590903c2c6b9a0a2443`，FFmpeg 为 `FongMi/FFmpeg@04482c8d13ac27b2a9fe93f5d388929eef8af5f4` / 9.0 / libavcodec 63.3.100，NDK 为 `29.0.14206865`，libplacebo 为 `04b3a0918fb32b8f374193aaead8b509274aae97` / API 373、OpenGL 开启而 Vulkan 关闭，ABI 仅 `armeabi-v7a,arm64-v8a`。manifest 还记录每个 `.so`、`libplacebo.a`、headers 和 license 的 SHA-256。因此它与后续 #72 是紧耦合的可复现 SDK，不应只拣若干 header 或 `.so`。

### 31.2 #70：公共 decode mode API 可作为窄前置，但单独没有用户价值

`C.DecodeMode` 只定义 software/hardware 两态，真正消费者只有后续 `DecodeTrackSelector`。当前 App 已有 `PlayerEngine.HARD` / `PlayerEngine.SOFT`、`PlaybackAutoContext.DecodeMode`、`getVideoRenderMode()` 和独立的 audio/video renderer preference；直接再引入一组数值相同但命名空间不同的常量会制造第三套模式表示。

**阶段 A2-1：decode preference API。** 仅在决定采用“播放中 invalidate 并重新映射 renderer”的方案时移植 #70，且应先定义 App `PlayerEngine`、自动策略和 Media3 API 的唯一转换层。若继续保持当前“创建 player 时确定 renderer 列表和顺序”的模式，#70 没有独立收益，跳过即可。不要用数值相等替代显式转换，也不要把 audio 与 video 强行绑成同一 mode。

### 31.3 #71：可复现资产信息有参考价值，二进制不能直接进入当前项目

#71 的优点是 provenance 完整：锁定 producer、FFmpeg、libplacebo、NDK、ABI、headers 和哈希，正好说明 #72 需要何种 native 契约。问题是它把一套新的 `libavcodec.so`、`libavformat.so`、`libavutil.so`、`libswresample.so`、`libswscale.so`，以及 renderer 暂时不直接使用的 `libavdevice.so`、`libavfilter.so` 一起放入 Media3 module；随后 CMake 只链接其中 `avutil/swresample/avcodec/avformat` 和静态 `libplacebo.a`。

当前项目已经通过 `nextlib-media3ext:1.10.0-0.12.1-fongmi-softload-av3a-r1` 打包同名 `libav*`/`libsw*`，源码同样锁在 FFmpeg `04482c8d...`，但使用 NDK r28c，并静态链接 `libarcdav3a`。同时 MPV 原生链使用 NDK r29，并强制把 FFmpeg 文件名、SONAME 和 `DT_NEEDED` 改成 `libmv*`/`libmw*`，目的就是避免 Android linker 与 nextlib 的 `libav*` 复用冲突。#71 的未改名 `.so` 若与 nextlib 同时打包，至少有以下风险：

- Gradle native merge 直接报重复文件，或因打包顺序只留下其中一份；
- `System.loadLibrary`/ELF 依赖加载到 ABI、configure、license 或 AV3A 能力不匹配的同名库；
- #72 的 `ffmpegJNI` 与 nextlib JNI 绑定同一组全局 FFmpeg SONAME，形成不可控的跨 AAR 共享；
- APK 体积、GPL/LGPL notice、符号可见性和漏洞/升级责任重复；
- #71 固定 libplacebo API 373，而当前 MPV lock 是 API 375；虽为独立静态副本，仍会形成两条不同 revision 的 shader/色彩行为和安全维护面。

**阶段 A2-2：native asset architecture gate。** 当前建议是不合并 #71。若未来决定以 #72 替换 nextlib，必须先做独立分支：移除 nextlib video JNI 或改为唯一 FFmpeg 资产生产者；保留/重做 AV3A audio；由 `scripts/build_media_deps.sh` 从 lock 生成而不是长期手工复制不透明二进制；补 SHA-256、license、SONAME/`DT_NEEDED`、ABI 和符号校验。只有“同一 APK 每 ABI 恰好一套 Exo `libav*`”成立后，才进入 renderer 功能测试。此阶段与 MPV native lock 无关，不得因 producer 来自 `mpv-android` 就把 MPV 一起升级。

### 31.4 #72：有价值的是 renderer 生命周期与恢复设计，不能覆盖现有 nextlib 方案

#72 不是把旧 `ExperimentalFfmpegVideoRenderer` 改名，而是重写了 decoder 和输出链：

- `FfmpegVideoDecoder` 不再继承 `SimpleDecoder`，改为独立 send/receive 状态机，输入/输出各自背压，专用 display-priority decode thread，flush/release 与 render lock 分离；
- 输出帧在 native 侧保留 `AVFrame`，经 GLES 3.1 直接/上传路径送到 `Surface`；队列满时 renderer 可暂时拒绝帧而不错误释放；Surface 切换会 detach，flush 等待 render generation 生效；
- `DecoderVideoRenderer` 新增 display-aware release timing、`VideoFrameReleaseHelper`、frame-rate estimator、纳秒 presentation time、暂时拒收 output buffer 和正确释放未消费 buffer；这些是通用基础类变化，会影响所有 decoder video extension，不只是 FFmpeg；
- GLES 快路径处理普通 SDR/可原样输出的 HDR，复杂色彩、HDR10+ 或 Dolby Vision metadata 走 libplacebo OpenGL；支持 PQ/HLG EGL colorspace、HDR metadata、旋转和像素宽高比；
- `FfmpegVideoRecoveryController` 在持续迟到 30 ms 后进入 non-reference 丢弃，严重迟到 250 ms 时同时降低 loop-filter 成本，恢复到 10/100 ms 阈值后退出；每个迟到 episode 至多请求一次 keyframe resync；
- 同一提交还重写 FFmpeg audio JNI、增加 DSD/DST、AV3A、Cook/ATRAC 等 MIME 映射与下混/重采样逻辑，不能把它当纯视频提交整体移植。

这些设计中，Surface ownership、output queue backpressure、flush/detach、纳秒 release time 和迟到 episode 状态机有明显评估价值。但当前项目已经有另一条经过定制的 nextlib 路径：

- `nextlib-ffmpeg-soft-load-shedding.patch` 在 decoder 创建时固定 `skip_frame`、`skip_loop_filter`、`lowres`；App 的增强软解配置使用 `NONREF / ALL / half-resolution`；
- `ExoUtil.PlaybackRenderersFactory` 按硬/软解模式构造 MediaCodec/FFmpeg renderer，保留 audio preference、DV7 fallback、tunneling、帧调度和 runtime decoder profile；
- `ExoRuntimeAwareVideoRenderer` 只排除运行中实际失败的 MediaCodec 组合；App 还有自动轨道限制、带宽/刷新率/输出配置和 codec queue 策略；
- `nextlib-av3a.patch` 支持 `audio/av3a -> libarcdav3a`、超过 8 声道的 `extended_data` 与必要时立体声下混。

上游 #72 的动态降载比当前“创建 decoder 时固定 aggressive 参数”更细腻，恢复时也能回到正常画质；反面是对非参考帧丢弃和 keyframe flush 会改变画面连续性，且状态阈值固定、测试面有限。当前 App 的固定 half-res/skip-all 在性能上更激进，但同样可能长期牺牲画质。二者值得做 A/B，却不能叠加：若在已经固定 `skip_frame=NONREF, skip_loop_filter=ALL, lowres=1` 的 decoder 上再套 #72 动态 level，所谓 normal 状态也无法恢复完整画质。

**阶段 A2-3a：通用 decoder-video correctness 窄移植。** 独立复核并选择 `DecoderVideoRenderer` 中“未处理 output buffer 不误释放”、Surface change 时同步 decoder output mode、frame metadata 使用计划 release time、pixel aspect ratio 和 release helper 生命周期等 hunk。它们必须对现有 MediaCodec、nextlib FFmpeg、AV1/VP9 extension 分别跑回归，不能随 #72 整提交落入。

**阶段 A2-3b：nextlib 动态恢复实验。** 在现有 nextlib 源码上重新实现 recovery controller 的状态机，保留现有 FFmpeg build/AV3A 和 renderer API；先把 normal/non-reference/aggressive 设计成可观测、可关闭、可调阈值，明确 aggressive 是否允许 lowres。用同一样片对照当前固定降载、上游动态降载和无降载三组的 PTS lateness、drop、CPU、功耗、画质、A/V sync、seek/rebuffer 与恢复时间。默认不先改变正式行为。

**阶段 A2-3c：GLES/libplacebo renderer shadow prototype。** 只有 A2-2 解决单一 FFmpeg 资产后才开始。重点验证 GLES 3.1/texture buffer 兼容、10-bit Surface、PQ/HLG、HDR10+、DV5/7/8/10、rotation/pixel ratio、Surface 快速切换、后台前台、连续起播/退出、低内存和 Android 7-16。当前项目已有 Exo DV7→P8.1/HDR10 策略和 MPV 独立 libplacebo；prototype 必须确认不会让三套 DV/HDR 决策互相矛盾。未通过前不替换 nextlib renderer。

### 31.5 #73：选择器 hook 有通用性，按 renderer 名称判定过于脆弱

`DecodeTrackSelector` 的目标是无需重建 player 即可切换 preference：hardware 模式优先 `MediaCodec*Renderer`，但当 MediaCodec 只有 `FORMAT_EXCEEDS_CAPABILITIES` 而 FFmpeg 可处理时仍回退；software 模式则只允许对应 FFmpeg renderer。它通过修改 `MappingTrackSelector.findRenderer()`，在常规最高 support 结果之前增加 allow/prefer hook。

收益是切换 renderer preference 可由 `invalidate()` 重新映射完成，避免为了硬/软解重建整个 player；也可以把硬解失败后的 FFmpeg fallback 表达得更明确。风险包括：

- 用 `getName()` 精确匹配 `MediaCodecVideoRenderer`、`FfmpegVideoRenderer` 等字符串；当前 App 有 `ExoRuntimeAwareVideoRenderer` 和 compat audio renderer，名称/子类不保证匹配；混淆、未来重命名或第三方 renderer 会静默改变结果；
- software 模式完全禁止平台 renderer。若 FFmpeg 不支持 DRM、特定 pixel format、secure/tunneled output 或 Surface probe 失败，track group 可能无 renderer，而不是自动回退；
- preference 在 track-group 到 renderer 的映射阶段生效，可能改变 adaptive group、mixed MIME、audio offload/tunneling、字幕/metadata renderer 关联；提交只测了两个单轨 H.264 hardware 场景，没有 software、audio、DRM、adaptive 或 runtime invalidation 测试；
- 当前 App 的硬/软解不仅是 renderer 顺序，还连带 codec selector、DV7、tunneling、帧调度、输出 profile、轨道上限和 decoder runtime blacklist；只 invalidate track selector 无法安全切换这些 player construction 参数。

**阶段 A2-4a：renderer mapping hook。** `MappingTrackSelector` 的通用 allow/prefer hook 可单独评估，但身份判断应改为显式 capability/tag/interface 或由 App 注入稳定 renderer id，不能依赖 class display name。先补不改变默认 `DefaultTrackSelector` 结果的完整回归。

**阶段 A2-4b：无重建切换实验。** 只在 audio/video preference 变化不要求修改 tunneling、output surface 类型、codec queue、DV pipeline 或 renderer 实例参数时启用。验证 hardware→software→hardware、多 period、adaptive HLS/DASH、音视频分别切换、DRM、AV3A、无 FFmpeg codec、Surface 重建、seek 与错误回退。任何构造期配置不同的模式继续重建 player。

### 31.6 跨仓库关系与实施顺序

| 阶段 | 关联 commit/仓库 | 前置 | 建议与回滚边界 |
| --- | --- | --- | --- |
| A2-1 decode API | media `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97` | 决定采用动态 preference API | 可独立回滚；否则跳过 |
| A2-2 asset gate | media `ca7dd917ad574d4241640eb9282f20c5decd5aea`；manifest 指向 mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`、FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、libplacebo `04b3a0918fb32b8f374193aaead8b509274aae97` | 明确 nextlib 保留还是替换 | 当前不合并资产；架构分支独立回滚，不动 MPV lock |
| A2-3a decoder-video correctness | media `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` 的窄 hunk | 无需 #71 | 可优先做代码/测试对照；不引入新 `.so` |
| A2-3b dynamic recovery | 同上 + 本地 `nextlib-ffmpeg-soft-load-shedding.patch` | 现有 nextlib AAR 可复现构建 | shadow/A-B，默认关闭；只回滚 nextlib patch和 App flag |
| A2-3c GLES/libplacebo renderer | media #71/#72，FFmpeg `04482c8d...`，libplacebo `04b3a091...` | A2-2 单一资产、A1 DV/HDR 策略稳定 | 高风险 prototype；不进入 Exo 第一轮最小集合 |
| A2-4 mapping/switch | media `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` + #70 | renderer 稳定身份、构造期配置分类 | 先 hook 再切换实验；可独立回滚 |
| C0 FFmpeg 安全升级 | FFmpeg `04482c8d...` → `177f090e0503b7e013922ca903bde14b1c375f18` | 与 A2 架构决策解耦 | 仍先升级现有 nextlib；不要先采用 #71 的旧资产快照 |

建议顺序是 **C0 现有 nextlib FFmpeg 安全升级 → A2-3a 通用 correctness → A2-3b 动态恢复实验 → A2-4a mapping hook**。A2-1 只在 A2-4 需要时加入。A2-2/A2-3c 属于未来可能替换 nextlib 的独立架构线，不能阻塞前述低耦合阶段，也不能与后续 MPV native 合并混为一次发布。

### 31.7 本检查点后的决策更新与恢复锚点

- #70 可作为动态选择 API 的窄前置，但当前项目已有多套 decode mode；未决定 A2-4 前不单独合并。
- #71 的 provenance 值得借鉴，资产本身与 nextlib 同名 FFmpeg `.so` 冲突；当前明确不进入合并队列。
- #72 的 Surface/backpressure/release-time/恢复控制有真实设计价值，但其 1.3 万行 renderer、audio JNI、DSD/DST/AV3A 和 native SDK 是架构替换，禁止整体 cherry-pick。
- 动态 recovery 可在 nextlib 上 shadow 实现；不得与当前固定 `NONREF + skip-loop-filter-all + lowres` 无条件叠加，否则 normal 状态也无法恢复画质。
- #73 的 allow/prefer hook可评估，按 renderer 名称判断和 software-only hard ban 不适合直接用于当前 App；动态切换必须区分可 invalidate 的偏好与必须重建 player 的构造参数。
- `media` #70-73 已完成逐提交深审。下一检查点从 `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8`（Add mpvplayer Media3 module）开始，然后依次为 `7cca3b0bb5cbdccea639e602e713301d8116a99f`、`c3b25d5f4d6b4cc66c24b512defd8cd7084d2486`、`0f6191bc1bdd7324eef5e512cada65d9b974a6ed`、`ab1bfd8779a4c9112d2a7ad61725f61668dfda85`；已审的 H.264/MMT/TTML 提交不重复工作。

## 检查点 32：2026-08-21 MPV Media3 模块、Dolby Vision/双 Surface OSD、统一诊断和播放能力 API（#74-78）

本检查点跨三类整理连续五个提交：#74/#75 属于 MPV 依赖和 UI 交接，#76/#77 属于通用诊断，#78 属于 Exo 运行时能力查询。它们在上游是一条连续提交链，但并不是当前项目中应一起合并的一个阶段：当前 App 已经拥有独立的 MPV 适配层、双 Surface OSD、DV7 回退、播放器统一诊断和 codec capability UI；真正还有合并意义的是少量稳定的数据契约、显示能力判定和运行时 capability API，而不是覆盖现有模块或 UI。

### 32.1 提交身份与变更规模

| # | Commit / parent / tree | Stable patch-id | 实际内容 |
| ---: | --- | --- | --- |
| 74 | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` / `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` / `a9adc3982accab00ccdbf59a2dbea7524e296a34` | `d7a73a67d0d63d3a59e25ee6f5a60c485cd84167` | 新增完整 `androidx.media3.mpvplayer` module：111 文件、`+14383`；101 个 Java（其中 13 个测试）、6 个两 ABI native `.so`、4 个构建/ProGuard 文件 |
| 75 | `7cca3b0bb5cbdccea639e602e713301d8116a99f` / `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` / `4161909fad5766025f69c95360c4853c1975ca0b` | `5bfc290f71483a90bc2d561a4bc2452012bc225b` | 18 文件，`+662/-112`；动态 Dolby Vision direct-output policy、MPV 音视频 effect 支持查询、双 Surface OSD、`PlayerView` bridge 和 native Surface API |
| 76 | `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486` / `7cca3b0bb5cbdccea639e602e713301d8116a99f` / `b8de2dd70483b89aac99c82b7ed45a2c3949ff55` | `a6dac2107810b282f73b3c8c286ed0a7c64130c4` | 5 Java 文件，`+1197/-111`；新增 `ExoPlayerDebugInfo`，缓存 decoder/output/audio-track 状态，并把 `DebugTextViewHelper` 扩展为完整播放诊断文本 |
| 77 | `0f6191bc1bdd7324eef5e512cada65d9b974a6ed` / `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486` / `24d45c7c071caa746513f340bdf72923b3109882` | `565bcd061b8d895c69cac66ad3e71223d268dbf7` | 5 文件，`+419`；新增 `DebugTextView`、`PlayerDebugView`，用 reflection 在同一 `PlayerView` 中显示 Exo 文本或切换 MPV stats |
| 78 | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` / `0f6191bc1bdd7324eef5e512cada65d9b974a6ed` / `05cf5aecce5814178f8f1e90913f9a39a40f4227` | `d1ea207c69681288475b66ced2a8a11eab0f6509` | 4 Java 文件，`+214/-2`；给 `ExoPlayer` 增加 audio-processing、skip-silence 和 video-effects 的运行时支持查询 |

#74 的文本 patch-id 可以计算，但提交包含 6 个 ELF；身份和复现不能只依赖 patch-id，还必须保留完整 commit/tree 以及 native 文件清单。#74 的 module 编译依赖 `lib-common`、`lib-decoder-ffmpeg` 和 `lib-exoplayer`，其 `.so` 直接放在 `jniLibs`；当前 WebHTV 则把 MPV 成套资产放在 App ABI assets 下并在运行时加载，FFmpeg 文件名/SONAME 使用 `libmv*`/`libmw*` 隔离。因此两种打包架构不能直接互换。

### 32.2 #74：完整 mpvplayer module 是当前实现的另一套基线，不是可直接升级的依赖

#74 以 `SimpleBasePlayer` 为入口，把 MPV 适配拆成 audio/core/media/nativebridge/options/seek/trackselection/video 多层：包括 playlist/timeline、chapter/edition、subtitle、artwork、seek 状态机、track mapper、audio focus、网络/每文件选项、native session registry、Surface controller 和 error mapper。这个分层本身有维护价值，但它同时携带 `libmpv.so`、`libplayer.so`、`libc++_shared.so` 两 ABI，属于完整播放器产品而非单个库升级。

当前 WebHTV 的 MPV 初始导入为 `cfc4bfc9843cc00db22cb3a563f34c6dde507062`，当时已直接引入 App 内 `MpvPlayer`/`MpvPlayerConfig`/`MpvPlayerEngine`、HLS proxy 和改名后的 native 依赖。此后当前实现已形成不同架构：

- 本地 `MpvPlayer.java` 已从初始 1234 行演化到 5172 行，承担 HLS 代理/预载/磁盘缓存、ISO/BD/DVD、native context 复用、错误分类、属性事件合并、轨道刷新、Surface/OSD request generation、帧时序、ANR/慢 native call 诊断、Vulkan/OpenGL/direct output 和 DV metadata 保留；
- App 还增加了约 30 个独立 MPV policy/state 类及大量单测，例如 cache time、HLS timeline/variant、Surface size、OSD requirement、GPU load、preload gate 和 diagnostics policy；
- 当前 native 每 ABI 是成套的 `libmpv.so + libplayer.so + libmv* + libmw* + libc++_shared.so` 10 个文件，并与 `third_party/mpv-native-lock.json`、构建脚本和改名后的 ELF `DT_NEEDED` 绑定；#74 每 ABI 只有 3 个未说明 provenance 的 `jniLibs` 文件，不能替换或并存；
- 当前 App 通过 `MpvPlayerEngine` 统一接入 `PlayerManager`、track persistence、auto-output、LUT、播放路由和诊断；#74 的 101 个 Java 类型与当前同包名类会产生重复 class，无法作为第二个 AAR 并存。

**阶段 B7-1：MPV adapter parity inventory。** 不合并 #74，也不复制其 `.so`。若后续要降低本地 5172 行单体的维护成本，只把 #74 的职责边界当重构清单：先给现有行为建立 characterization tests，再按 native-session、playlist/timeline、track mapping、seek、Surface 和 options 六个边界逐步抽类，每一步保持 `MpvPlayerEngine` API、HLS/ISO、DV、OSD 和诊断不变。这个阶段是本地重构，不应与 MPV native 版本升级放在同一提交。

**阶段 B7-2：native packaging gate。** 只有决定全面迁移为独立 mpvplayer AAR 时才评估 #74 资产布局；届时必须让 AAR 从当前 `mpv-native-lock` 生成同一套 `libmv*`/`libmw*`、`libmpv`、`libplayer`、curl/nghttp2 和 `libc++_shared`，保留 SHA-256、license、SONAME/`DT_NEEDED` 校验，并删除 App assets 的旧生产者。禁止直接采用 #74 的 6 个 ELF，也禁止两个打包位置同时存在。

### 32.3 #75：显示能力和 effect-support 契约值得选择性吸收，OSD 生命周期不能回退

#75 的 DV/OSD 不是单一功能，而是四层联动：

1. `DolbyVisionOutputPolicy` 新增 `isNativeOutputAllowedOnDisplay(Display, mode)`，AUTO 模式从实际 video `SurfaceView` 所在 display 读取 DV HDR type，而不是只看 default display；
2. `MpvOptions` 只有在 display 支持 DV、MediaCodec 硬解启用、direct video Surface 已配置、direct OSD Surface 也已配置时，才把 `android-dolby-vision-output` 切到 `direct`；否则使用 `configured`；
3. `MpvPlayer` 增加 audio/video effect support 枚举：SPDIF passthrough 禁止 audio effect，`mediacodec_embed` direct DV 禁止 video effect；
4. `MpvNativeOsdSurface`、`MpvSurfaceController` 和 `MpvOsdSurfaceBridge` 创建透明 overlay Surface，并在 attach/replace/detach 时同步 `android-osd-surface-size`。

当前项目已经拥有双 Surface JNI（`MPVLib.attachOsdSurface` 等）和更保守的异步生命周期：OSD 只在 direct output 且实际 subtitle track/visibility 需要时创建；每次 attach/detach 使用 request ID，等待 native command reply 后再推进；快速 Surface 重建、zero-alpha、仅一个 direct video Surface 和销毁顺序已有本地修复/测试（包括 `17aaceb0fac8238c64a9ec45cdb30575b038c022`、`3dad8d5df9dd9ff8cf1f4218d3e95899a4a0d49e`、`6c4a9d5b6ad88e0563ce866c42a9e70ce089292f`）。上游 #75 的同步 `attach/replace/detach` 和 reflection `PlayerView` bridge 若覆盖本地路径，会丢失 generation、按需 OSD、错误回执和本地稳定性修复。

当前 DV 策略也更复杂：`MpvAutoOutputPolicy` 和 `CodecCapabilityInspector.dolbyVisionSupport()` 按源 profile/level、实际 DV hardware decoder、TV/手机、硬软解、LUT/filter 和自定义 GPU 配置决定是否重建进入 direct output；DV7 还包含 `demuxer-dovi-profile7=hdr10/preserve`、GPU fallback 和失败后保留源 metadata（`0d09ee4b4a5d0042d7ca16a063f2b9051a6a14c2`）。不过当前 direct eligibility 主要验证 decoder，尚未把“视频 Surface 当前所属 display 是否报告 DV”作为独立硬门槛；多屏/外接显示场景存在补强价值。

**阶段 B7-3a：display-aware DV gate。** 选择性移植 #75 的 `isNativeOutputAllowedOnDisplay` 思路，接入当前 `MpvAutoOutputPolicy` 输入，而不是引入整套 `MpvOptions`。要求使用实际播放 `SurfaceView.getDisplay()`，区分 default/外接 display，并把 Android DV HDR type 与当前 profile/level decoder 查询同时满足作为 direct 条件；ASSUME_SUPPORTED/UNSUPPORTED 仅作为明确的高级覆盖。测试 API 24-33 `HdrCapabilities`、API 34+ `Display.Mode.getSupportedHdrTypes()`、null/切屏/热插拔，以及“decoder 支持但 display 不支持”和反向场景。

**阶段 B7-3b：runtime effect capability contract。** 把上游 audio/video effect support 的“原因枚举”思想合并进通用 `PlayerEngine`，不要只保留当前静态 `supportsVideoEffects()`/`supportsNativeLut()` boolean。MPV 至少区分未初始化、direct video bypass、GPU effect 可用、SPDIF passthrough；Exo 与 #78 共用 renderer/tunneling/DRM/format 原因。这样 LUT、均衡器、音频处理 UI 可以显示真实原因，并避免输出路径变化后继续应用不生效的 effect。该阶段是通用功能，适合随 Exo #78 API 一起实现，再由 MPV 填充适配。

**阶段 B7-3c：OSD regression only。** 不移植上游 OSD controller/bridge。把 #75 的 attach→size、replace、detach 和 player swap 场景补入当前异步实现的回归矩阵，并保留现有 `MpvOsdSurfacePolicyTest`、request reply、快速重建、后台前台、subtitle on/off/secondary subtitle、release 后 destroyed-mutex 检查。只有测试发现当前缺口时才做窄修复。

### 32.4 #76/#77：数据格式化有可取项，统一 overlay UI 已被当前诊断体系覆盖

#76 新增的 `ExoPlayerDebugInfo` 暴露 active audio/video decoder name、AudioTrack 是否初始化和当前 video output；`ExoPlayerImpl` 在 decoder init/release、audio track init/release 和 output 切换时维护这些状态。扩展后的 `DebugTextViewHelper` 每秒展示文件/标题/时长/edition/chapter、文件大小、container/protocol、buffer、display/refresh、decoder 硬件属性、buffer counters、frame processing offset、分辨率/FPS、encoded MIME/codecs、bit depth、color、bitrate、音频输出/声道/采样率/音量/延迟，以及 player/load/audio/video error。#77 再用 reflection 避免 UI module 编译依赖 Exo/MPV：Exo 创建文字 overlay，MPV 调用 `toggleGeneralStats()`。

当前 App 已有更贴合产品的统一诊断：

- `PlaybackAnalyticsListener.Snapshot` 缓存 Exo decoder、格式、掉帧、buffer、带宽、rebuffer、错误 decoder/diagnostic/secure/cause 和 frame timing；
- `PlayerEngine.PlaybackFactsSnapshot` 为 Exo/MPV/IJK 提供统一 runtime facts，`PlayerOsdController` 同时展示视频/音频、网络保护、MPV render/runtime、GPU/CPU/内存、帧调度、配置和中文诊断；
- 诊断采样只在面板可见时开启，避免 MPV 同步属性查询和系统资源采样长期运行；手机/电视已有明确入口、持久设置和布局；
- `CodecCapabilityDialog` 另行展示 Media3 track support、实际 decoder、DV source/fallback 和系统 codec 能力。

因此 #77 的 UI 若直接引入，会形成第二个 overlay、第二套开关和 reflection/ProGuard 契约；MPV 的 toggle 式 stats 还假定调用两次一定恢复，若 player swap、native stats 被外部改变或异常退出，状态可能反转。#76 的整份 944 行 helper 也不应替换当前中文、按需采样、跨三播放器诊断。

**阶段 C7-1：diagnostics fact contract。** 若需要让 Media3 fork 自身稳定暴露事实，可选择性采用 #76 的 decoder name、video output、AudioTrack lifecycle 缓存 API，并由 `ExoPlayerEngine.getPlaybackFactsSnapshot()` 直接读取；继续保留 `PlaybackAnalyticsListener` 作为时序/错误/网络来源。实现时要处理同名 decoder 重入、多个 AudioTrack init/release 和 player release/reset，避免把请求值冒充实际值。这个阶段可独立于 UI 合并。

**阶段 C7-2：formatter/test harvest。** 只复用 #76 中经测试且当前缺失的纯函数：progressive `Content-Range` 文件总大小、container/protocol 归一化、平均/峰值 bitrate、luma/chroma bit depth、pixel ratio 后的显示宽高、frame processing offset、current-window load-error 过滤。把它们放进当前诊断 formatter 并本地化；不得带入一整套 `DebugTextViewHelper` lifecycle。

**阶段 C7-3：统一 overlay UI。** 当前建议跳过 #77。只有产品决定用 Media3 `PlayerView` 作为跨应用公共组件时才另开实验；届时必须改为显式 `PlayerDebugProvider`/capability interface 或受控 adapter，避免依赖类名、reflection、混淆 keep rule和 toggle 语义。

### 32.5 #78：这是本组最值得合并的 Exo API，但应扩大为当前项目的真实 capability 模型

#78 并不查询“设备能否解码某个 MIME/profile”；它查询**当前已选择/已初始化播放路径是否允许某类处理**：

- audio：用 `AudioSink.AudioTrackConfig` 的实际 encoding、offload、tunneling 维护计数，区分 unavailable、supported、passthrough、offload、非 PCM16 format；`isSkipSilenceSupported()` 还要求未 tunneling；
- video：遍历当前 `TrackSelectorResult`，找到已选择的视频 renderer/format；仅 `MediaCodecVideoRenderer` 可支持 effects，并排除 tunneling、DRM 和 `MediaCodecVideoRenderer.isVideoEffectsFormatSupported(format)` 不支持的格式；
- API 同时加入 `ExoPlayer`、`ExoPlayerImpl`、`SimpleExoPlayer` 和 `StubExoPlayer`，因此必须随 fork AAR 成套发布，不能只在 App 侧调用不存在的方法。

这个语义与现有 `CodecCapabilityInspector` / `ExoPlaybackCapability` 不重复：后两者主要是设备 codec list、分辨率/FPS/profile 和预播放策略；#78 回答的是当前 renderer/output 的动态状态。当前 `ExoPlayerEngine.supportsVideoEffects()` 永远返回 `true`，而实际 LUT pipeline 可能处于 tunneling、DRM、扩展 FFmpeg renderer 或不支持 effect 的格式；所以 #78 能消除真实误判。

但原提交仍有边界：

- video 用 `instanceof MediaCodecVideoRenderer`，当前自定义 `DolbyVisionHdr10FallbackRenderer`/`ExoRuntimeAwareVideoRenderer` 作为其子类可覆盖，但 nextlib FFmpeg renderer 会统一返回 unsupported-renderer；需要明确这是事实还是未来 software effect pipeline 的限制；
- 只检查 selected `Format.drmInitData`，还要验证 clear lead、session 切换和 secure decoder；
- audio 以 PCM16 作为 processor 支持条件，需与当前 nextlib AV3A、超过 8 声道、float/high-resolution PCM 和自定义 audio processor 链对照；
- 多 renderer/secondary renderer、adaptive format change 和 AudioTrack 重建时，计数必须严格成对，不能因 stale release 造成错误状态；原提交未包含对应测试文件；
- API 名叫“playback capability”，不能与 `ExoPlaybackCapability.Report` 的预播放设备能力混为同一对象。

**阶段 A8-1：Exo runtime processing capability。** 建议作为 Exo 第二轮的中优先级窄合并：移植 #78 的接口和实现到当前 `release-1.11.0-fongmi` fork，补 renderer/tunneling/DRM/format/audio-output 原因测试，并让 `ExoPlayerEngine` 的 LUT/skip-silence/UI gate 使用运行时结果。保留现有预播放 `ExoPlaybackCapability` 名称，新增明确的 runtime adapter，避免语义混淆。这个阶段只更新 Media3 源码/AAR和 App adapter，不动 FFmpeg/MPV native。

**阶段 A8-2：capability transition events。** 当前输出状态会在 prepare、adaptive switch、tunneling fallback、AudioTrack 重建、passthrough/offload 切换和 Surface/output 更换时变化。不要只在用户点击时查询；把 capability snapshot 纳入当前 `PlaybackFactsSnapshot` 或独立 listener，在变化时刷新 LUT/音效 UI和诊断。若 effect 已启用而 capability 降级，先停止/重建对应 pipeline，再提示明确原因。

### 32.6 分类后的实施步骤和跨提交关系

| 分类/阶段 | 关联 commit | 前置 | 实施内容 | 建议 |
| --- | --- | --- | --- | --- |
| Exo A8-1 runtime processing capability | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85`；关联 #76 的 runtime facts | 当前 Media3 fork 可复现构建 | audio processing、skip-silence、video effect support API + 测试 + App adapter | **建议条件合并；本组最高优先级** |
| Exo A8-2 capability transition | 同上 | A8-1 | 将动态原因接入 LUT/音频 UI、诊断和输出切换 | 建议随 A8-1 或紧随其后 |
| MPV B7-1 adapter parity/refactor | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` | characterization tests | 只按职责逐步拆当前 module，不替换行为 | 默认暂缓；维护性项目 |
| MPV B7-2 native packaging | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` | 决定迁移独立 AAR | 由当前 lock 生成唯一 MPV 资产生产者 | 当前不合并上游 ELF |
| MPV B7-3a display-aware DV | `7cca3b0bb5cbdccea639e602e713301d8116a99f` | 当前 direct-output policy | 实际 Surface display DV 能力 + decoder/profile 双门槛 | **建议窄移植** |
| MPV B7-3c OSD regression | 同上 | 当前异步 OSD | 补 attach/replace/detach/player-swap 测试 | 不移植架构，只补测试/缺口 |
| 通用 C7-1 runtime fact contract | `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486` | Media3 fork API 决策 | decoder/output/AudioTrack 实际状态 | 可随 A8-1 合并 |
| 通用 C7-2 formatter harvest | 同上 | 当前诊断 formatter | 文件大小、bit depth、pixel ratio、offset 等纯函数 | 低风险独立候选 |
| 通用 C7-3 overlay UI | `0f6191bc1bdd7324eef5e512cada65d9b974a6ed` | 明确公共 PlayerView 产品需求 | 第二套统一 overlay | **当前跳过** |
| 通用 B7-3b effect capability contract | `7cca3b0bb5cbdccea639e602e713301d8116a99f` + `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` | A8-1 | `PlayerEngine` 跨 Exo/MPV 的原因枚举 | 建议随 A8-1 一起设计 |

用户要求先合并 Exo、再合并 MPV，因此建议实际顺序为：

1. **Exo：A8-1 → A8-2，同时带 C7-1/B7-3b 的通用数据契约**；这一步不触碰 MPV native，只让 MPV adapter 预留同一原因模型。
2. **通用：C7-2 可独立插入 Exo 阶段**，因为只改诊断格式化和测试；C7-3 暂不做。
3. **MPV：B7-3a → B7-3c**，先补实际 display DV gate，再用当前异步 OSD 路径回归；不采用 #74/#75 的 native/Surface 架构。
4. **B7-1/B7-2 是未来维护/打包重构**，不阻塞播放器功能合并，也不应与 MPV native 升级混成一次发布。

### 32.7 本检查点后的决策更新与恢复锚点

- #74 的 101 个 Java + 6 个 ELF 是完整、同包名的第二套 MPV 产品；当前实现已高度本地化，禁止整体 cherry-pick、AAR 并存或直接替换 native。
- #75 的 OSD attach/replace/detach 架构弱于当前 request/reply + generation 路径；只吸收实际 Surface display 的 DV 能力门槛和 effect-support 原因模型。
- #76 的 runtime decoder/output facts 与若干 formatter 有价值；#77 的统一 overlay 已被当前 `PlayerOsdController` 覆盖，默认跳过。
- #78 是本组最有合并意义的 Exo 功能：它补的是当前播放路径的动态 processing/effect capability，不等同于系统 codec list；应加测试后窄移植，并替换 `supportsVideoEffects() == true` 的静态判断。
- `media` #74-78 已完成逐提交深审并按 Exo/MPV/通用拆为可执行阶段。下一提交从 `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` 开始，然后是 `12670ce4fb23ad32ed3875d0250486eabe957913`、`ccf962e8912695dc60ce82aa4470df899c6306a3`、`3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`；MMT/TLV 先复用已有审计证据并核对最终树，不重复无效深审。

## 检查点 33：2026-08-21 media 收尾：H.264 AU、发布 AAR 辅助、MMT/TLV 与 TTML（#79-82）

本检查点完成 `media` 目标范围最后四个提交的收尾。#79、#81、#82 已分别在检查点 13、22、23 中完成跨模块或跨仓库深审，因此这里不重复堆叠同一论证，只补齐稳定 patch-id、最终父/tree、文件规模、当前 fork 最终树复核和与 #80 的连续提交关系。#80 此前仅有标题级索引，本检查点完成其完整 diff 和当前发布流程对照。

### 33.1 提交身份与最终覆盖复核

| # | Commit / parent / tree | Stable patch-id | 规模与当前 fork 结论 |
| ---: | --- | --- | --- |
| 79 | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` / `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` / `2fc28867cef51e47869454556a07f1b967e553c2` | `535a55b1dd9c26693d0fb56756be04e870e9490f` | 4 文件，`+184/-133`；当前 fork `e3e922d5c01bc0b564849940fe589daf37360d15` 仍保留 `allowNonIdrKeyframes`/`detectAccessUnits`，没有 PPS 新字段或 recovery-point parser，确认未覆盖 |
| 80 | `12670ce4fb23ad32ed3875d0250486eabe957913` / `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` / `5e8261c1a87a87d9617f4badf879e63af2fc4998` | `4078493ef1f4ffb9afb18e025f2302b4ae0d437a` | 4 文件，`+44`；新增 Windows 批处理和硬编码复制清单；当前项目没有等价脚本，但已有更严格的本地 Maven 发布流程，因此不是功能缺口 |
| 81 | `ccf962e8912695dc60ce82aa4470df899c6306a3` / `12670ce4fb23ad32ed3875d0250486eabe957913` / `b7bd60aff0a22f44e6dcd8f2f70102fc2256eb8b` | `97d3b887362b8a05da693f9c427f56d6231b4cdd` | 13 文件，`+3836/-2`；当前 fork 最终树仍没有 `extractor/mmt`、`TlvExtractor` 或 `MmtData`，确认是未覆盖的 Java extractor 功能 |
| 82 | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` / `ccf962e8912695dc60ce82aa4470df899c6306a3` / `b7a4d78a2168bd5065034d37a242b7e51785ea4e` | `dc1ea9d07536b7f41eb5a94085977a4aaa941b49` | 11 文件，`+354/-19`；当前 fork 最终树仍没有 `LetterSpacingSpan`、`Cue.textRegionHeight` 或 region text scaling，确认未覆盖 |

四个提交形成线性父链，但在当前项目中不应作为一个合并批次：#79 是 TS H.264 sample/keyframe correctness，#80 是个人发布操作辅助，#81 是新的广播容器 extractor，#82 是跨 common/extractor/ui 的字幕数据和渲染契约。父链只说明审计来源，不构成实施依赖。

### 33.2 #79：复核后维持 H264-1~3 的样片驱动方案

完整 patch 再次确认 #79 同时做了三类改变：PPS slice-group/默认引用数解析；SEI immediate recovery-point 与少引用 I-frame 关键帧判断；删除两个公开 TS factory flag 并重写 AUD/VCL sample 边界状态机。它不是只增加 recovery-point 的小修复。

当前 fork 最终树的邻接差异仍与检查点 23 一致：synthesized PUSI/EOF、4-byte start code、stale NAL state、M2TS framing/seek 和本地 H.264/TS 边界补丁均需保留。上游测试只把一个无 AUD TS fixture 改为默认 factory，仍不足以证明 HLS segment、188/192-byte TS、BDMV、跨 PES 和错误 recovery metadata 安全。

因此维持既有 **A5-8c** 决策，不整提交 cherry-pick：

1. **H264-1：PPS parser。** 先移植 `numRefIdxL0DefaultActiveMinus1` 和 slice-group map 跳读，保持旧 flags 和 sample boundary 不变；补截断 PPS、所有 map type、多 PPS 和非法 Exp-Golomb 测试。
2. **H264-2：recovery/keyframe。** 在兼容旧开关的 reader 中加入 `recovery_frame_cnt == 0` 和少引用 I-frame 判定；用 sample dump 比较 flags、PTS、首帧可解码和 seek 花屏。
3. **H264-3：自动 AU/flags 清理。** 只有无 AUD、带 AUD、跨 segment/PES、synthesized EOF、188/192-byte TS、4-byte prefix、M2TS/BDMV 和连续 seek 全部通过后，才删除旧 API；该层必须可独立回滚。

收益仍是改善无 AUD 或 recovery-point 广播/直播流的起播和随机访问；主要风险仍是错误 sample 边界和错误同步帧标记。它属于 Exo A5 的样片后候选，不进入当前 Exo 第一轮最小集合，也不触发 MPV native 更新。

### 33.3 #80：仅是机器相关的 Windows 复制脚本，明确不合并

#80 新增根任务 `assembleLibs`：依赖所有名称以 `lib-` 开头的 subproject 的 `assembleRelease`，完成后执行 `cmd /c move.bat`。`move.bat` 把源码、目标和清单分别硬编码为 `D:\Project\media\libraries`、`D:\Project\TV\app\libs`、`D:\Project\media\move.txt`，递归查找并复制清单中的 20 个 `lib-*-release.aar`；提交还把 `.vscode` 和整个 `.github` 加入 `.gitignore`。

这个 helper 有三个关键限制：

- 只适用于作者的 Windows 目录，非 Windows 执行根任务会因 `cmd` 不存在失败，换目录也会静默找不到或复制到错误项目；
- 只复制裸 AAR，不发布 POM、Gradle module metadata、sources、版本目录或 Maven metadata，也没有校验 AAR 对应的源码 commit、patch、ABI、license 和 SHA-256；
- 清单含 `lib-decoder-ffmpeg`、`lib-decoder-mpegh` 和 `lib-mpvplayer`，但当前 App 的正式 Media3 publication 不是这组模块；直接放入 `app/libs` 还会绕过统一版本约束，并可能与 nextlib 的 `libav*` 和 App MPV assets 形成重复 native 生产者。

当前 `scripts/build_media_deps.sh` 已从 `third_party/media-lock.json` 取版本/commit，应用本地 patch，逐模块执行 `publishReleasePublicationToMavenRepository` 到临时 staging，再把完整 publication 安装进 `third_party/maven`；nextlib 还单独校验两 ABI、`libmedia3ext.so`、`libavcodec.so` 和 AV3A 标记。README 也明确普通构建消费完整 AAR/POM/module/checksum，而不是 `app/libs` 中一组散装 Media3 AAR。

**阶段 C8-1：发布入口/模块 manifest（只借鉴，不移植）。** #80 的唯一可借鉴点是“一条命令构建明确模块集合”。若需要改善开发体验，应在现有 `build_media_deps.sh` 中维护 lock 驱动的模块 manifest 或 `--media-only` 入口，并检查预期 publication 集合完整、无意外 `mpvplayer`/FFmpeg/MPEG-H 资产；不引入 `move.bat`、`move.txt`，也不把 `.github` 整体忽略。

**结论：#80 明确跳过。** 它不修复播放器行为，也不提高当前产物可复现性；直接合并反而削弱现有发布 provenance，并可能让开发者误用裸 AAR 覆盖本地 Maven。该结论不阻塞任何 Exo 功能阶段。

### 33.4 #81：最终树仍是完整 MMT/TLV 缺口，但没有需求证据时继续暂缓

#81 的最终 patch 与检查点 23 记录一致：除 `FileTypes`/`MimeTypes`/factory 接线外，主体是约 3600 行 signaling、timestamp、MMTP fragment 和 TLV 外层解析；还把 MMT metadata 接到 `MetadataDecoderFactory`，并在上游内置 FFmpeg decoder 的 MIME 列表加入 ALS。当前 WebHTV fork 最终树完全没有这些 package，因此不能标为“已覆盖”。

但跨仓库结论没有变化：当前锁定 FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 已有 MMT/TLV 原生 demux 能力，MPV 可用于需求探测；这不能替代 Exo Java extractor，也不等于 nextlib 的 `--disable-everything` 配置实际启用了对应 demux。没有真实 ISDB-S3/MMTP/TLV 输入时，引入 3836 行状态机、fragment 上限和直播错误处理没有足够收益。

继续按 **MMT-0 → MMT-1 → MMT-2 → MMT-3**：先用现有 FFmpeg/MPV 验证样片和产品需求；再做 MIME/sniff；之后移植 timestamp/signaling/reader；最后接 ALS/STPP/metadata/字幕。每层独立回滚，不能因为 #81 位于 #80 之后就采用它的裸 AAR 复制流程。当前仍为 Exo 条件功能，默认暂缓。

### 33.5 #82：维持 A4-J 数据契约优先，不能只移植 parser

#82 的最终 patch 仍跨三层：common 新增 `Cue.textRegionHeight` 与可 Bundle 的 `LetterSpacingSpan`；TTML parser/render 传递 `tts:extent`、pixel/em 比例和 letter spacing；UI 按兼容 region 组的视觉中心同时缩放 position、line、width 和 height。只移植 XML parser 会让新数据在 Cue Bundle/MediaSession 或 Canvas 缩放处丢失，因此必须保持检查点 13/16 的联合阶段。

当前 fork 最终树确认 `LetterSpacingSpan`、`textRegionHeight` 和 `scaleRegionTextCue()` 均不存在。建议顺序不变：

1. **A4-J0/A4-J1：** 先冻结 ASS/TTML/PGS 基线，加入 Cue 字段、Builder/Bundle/equals/hashCode 和 custom span round-trip；默认不改变 UI 行为。
2. **A4-J2：** 接入真实 video viewport，统一 text region、bitmap height、anchor 和 App 字号策略；手机/电视留黑边截图通过后再启用缩放。
3. **A4-4a/4b：** 移植 TTML extent、font/letter spacing 和 region 输出，再适配当前 Canvas policy；没有可靠设计画布尺寸的样片时，不启用 Format 尺寸 pixel fallback。
4. **A4-4c/4d：** WebView CSS parity 和各输入容器的画布尺寸接线按真实需求后置；最终与 ASS collision、PGS/DVB 做 A4-J3 混排验收。

#82 合并价值高，但依赖真实 TTML 和共享 Cue/UI 契约；它可以进入 Exo 字幕阶段的条件集合，不能与 #81 MMT/TLV 捆绑，也不要求升级 FFmpeg/MPV。

### 33.6 分类后的实施顺序与 media 收尾结论

| 分类/阶段 | 关联 commit | 实施建议 | 当前优先级 |
| --- | --- | --- | --- |
| Exo A5-8c H264-1~3 | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | PPS → recovery/keyframe → 自动 AU/flags；样片驱动、分层回滚 | 中高价值，但不进第一轮最小集合 |
| Exo MMT-0~3 | `ccf962e8912695dc60ce82aa4470df899c6306a3`；关联 FFmpeg `054c8690e16b377eb1c6375c8751a44b8eb1d962`、mpv `32c4d5adad29107756ae2987d69d92844bfed243` | 先用现有 MPV/FFmpeg 验证需求，再决定 Java extractor | 无样片时暂缓 |
| Exo A4-J/A4-4 | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | Cue 数据契约 → viewport → TTML parser/Canvas → 可选 WebView/画布接线 | 条件合并；字幕阶段重点候选 |
| 通用 C8-1 publication UX | `12670ce4fb23ad32ed3875d0250486eabe957913` | 不合并脚本；仅在现有 lock/staging/Maven 流程中改善一键入口和模块清单 | 低优先，非播放器功能 |

按“先 Exo、再 MPV”的决策顺序，#82 应回到既有 A4 字幕链，#79 放在 A5 样片后 correctness 队列，#81 只有 MMT-0 证明真实需求后才启动，#80 不进入代码合并队列。它们之间没有必须共同发布的依赖。

### 33.7 恢复锚点

- `media` #1-82 已完成首轮逐提交 diff/身份映射；A1/A3/A4、A5 主要容器/网络/DRM/H.264/MMT、A6 光盘/ISO、A2 软件 renderer、A7 UI/MPV/debug 和 A8 runtime capability 均已有深审或明确后置门槛。
- #79 patch-id 为 `535a55b1dd9c26693d0fb56756be04e870e9490f`，#80 为 `4078493ef1f4ffb9afb18e025f2302b4ae0d437a`，#81 为 `97d3b887362b8a05da693f9c427f56d6231b4cdd`，#82 为 `dc1ea9d07536b7f41eb5a94085977a4aaa941b49`；完整 parent/tree 已在 33.1 记录。
- 下一工作项不是继续重复审阅 `media`，而是回到 `mpv` 新线的 11 个非等价残余提交。开始前重新固定 `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42..44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 的提交图、patch-id 去重和当前远端头，随后按 B5-B10 关联功能逐组落盘。

## 检查点 34：2026-08-21 mpv B10：packed RGB10、Matroska 零长度默认值与 HLS edition 初选

本检查点完成旧审计目标 `FongMi/mpv@44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 中 B10 四个真实增量的首轮深审。四项位于同一重落基父链，但实现上应拆成三个可独立验收的阶段：10-bit packed RGB 的 libplacebo RA 身份、Matroska/EBML 零长度默认值、HLS program/edition 初选。它们都不要求改 App Java API；但任何一项进入正式产物，仍须按当前 `mpv-native-lock` 使用 NDK r29 成套重建两 ABI 的 MPV/FFmpeg/libplacebo 资产，不能只替换 `libmpv.so`。

### 34.1 提交身份与当前覆盖

| 阶段 | Commit / parent / tree | Stable patch-id | 文件与当前锁定树 |
| --- | --- | --- | --- |
| B10-1 | `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75` / `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` / `7c8dc652177f1dc52103ab3b05ad96df33924f3d` | `e57491cdfc2044903b31d1641d9801f67835b1f2` | `video/out/placebo/ra_pl.c`，`+24`；锁定 mpv `cca559...` 没有这两个 `special_imgfmt` 映射 |
| B10-2 前置 | `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` / `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75` / `b7b9ee1b06f714654a3d452c306e6b96572edffd` | `141ab36415fe54d1c1e9316c58c742eca68cc07e` | `TOOLS/matroska.py`，`+12/-7`；只是把可选长度改为命名属性，为下一提交增加 `default=` 做语法准备 |
| B10-2 主体 | `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8` / `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` / `1c22cd7b8fa1c0c35843dd6cc98d55dbc03e2738` | `4a2f6df7eba852ddcae3879b6fad07e161d8a969` | `TOOLS/matroska.py`、`demux/ebml.c`、`demux/ebml.h`，`+77/-37`；锁定树仍拒绝零长度 uint，且没有 RFC/context default 描述 |
| B10-3 | `e7191f2a65d64af266c5c80793e79d2f4b92b789` / `b6d3434493fd04c0ee40a5610d8c311b77b16a6d` / `ede8321cb140fc8daeee1cdb2f9c59e4a9b4b341` | `cd706fd3b07d20e97d2dc31bf15229e00fd09d65` | `demux/demux_lavf.c`，`+10/-23`；锁定树仍用 per-stream `hls_bitrate` 代表 program |

本地构建源码工作树包含 WebHTV 的 disc/DV7/Vulkan/TrueHD 等补丁，但没有覆盖上述四个 patch-id。`52bb...` 没有独立运行时收益，不应单独形成发布或 native rebuild；它必须与 `e167...` 作为同一个 B10-2 实施单元。

### 34.2 B10-1：packed RGB10 RA 身份，建议随首次 MPV native 重建纳入

锁定 libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 已提供 Vulkan `rgb10a2` / `bgr10a2` 格式，分别对应 `VK_FORMAT_A2B10G10R10_UNORM_PACK32` 和 `VK_FORMAT_A2R10G10B10_UNORM_PACK32`。缺口位于 mpv 的 libplacebo RA wrapper：普通循环只能复制组件深度和顺序，无法把非逐字节 packed 格式反向识别为 mpv `IMGFMT_X2BGR10` / `IMGFMT_X2RGB10`。`7b891...` 为这两种格式构造 `ra_imgfmt_desc`，声明单 plane、RGB 各 10 bit，并明确两种通道顺序。

这与当前项目高度相关：WebHTV 已把 Vulkan 分为 direct、legacy、stable/自动回退，并在 AImageReader/AHardwareBuffer 路径处理 HDR/Dolby Vision raw YUV 和 GPU conversion。10-bit RGB 中间/输出格式如果没有特殊身份，可能被迫走额外转换、无法正确匹配上传格式，或在通道顺序上出现仅 HDR 样片可见的偏色。它不需要更新 libplacebo API，当前 API 375 已具备底层格式，因此是 mpv 侧 24 行的窄修复。

**收益：** 补齐 Vulkan/libplacebo 对 packed 10-bit RGB 的真实格式映射，降低 HDR 10-bit 路径格式拒绝、错误 fallback 或通道错位的风险；同时与 B4 的 libplacebo alpha-preservation `22ee762e8e0890fc54068beb670310f0edce7263` 适合搭载同一次 native rebuild。

**风险：** 两个 libplacebo 名称与 mpv `X2RGB10/X2BGR10` 的字节/组件语义看起来相反，提交已显式处理这种命名差异，但设备驱动、little-endian packed layout 和 shader 采样仍需用图像验证；若错误会表现为红蓝互换或 HDR gradient/高光异常。OpenGL 已有独立 `X2RGB10` special mapping，本提交不应意外改变 OpenGL 行为。

**阶段 B10-1：** 建议合并，但不为它单独发布 native。随 MPV 首次受控重建纳入 `7b891...`，分别强制 direct、legacy、stable 以及自动 fallback；使用 RGB 红/绿/蓝阶、10-bit 灰阶、HDR10/DV GPU fallback 样片，检查 banding、红蓝顺序、黑白电平、截图/OSD/LUT；同时跑 OpenGL `gpu-next` 与 Vulkan软解，确认没有把非 Vulkan路径回归。

### 34.3 B10-2：EBML 零长度元素按 RFC default/zero 解释，属于真实 correctness 修复

EBML 允许元素存在但 payload 长度为零：有规范默认值时应解释为该默认值，没有默认值时按零/空值；这与“字段完全缺失”不是同一种状态。锁定实现会把零长度 uint 判为 invalid，字符串变成空字符串，其他数值也没有元素级默认信息。`e167...` 给生成的 `ebml_elem_desc` 增加 `context_default` 与 uint/sint/float/string union，并让 parser 在长度为零时写入 RFC 默认值；`DisplayWidth/DisplayHeight`、`OutputSamplingFrequency` 这类默认值来自同一 Track/Audio 上下文时则跳过写入，保留后续已有的 context-derived fallback。

提交显式登记的默认值覆盖 EBML header、`TimecodeScale=1000000`、`BlockAddID=1`、track enabled/default/lacing、language、codec decode-all、colour matrix/transfer/primaries、audio sampling/channels、content encoding scope、chapter enabled/language、tag target/language/default 等。对当前项目尤其重要的关联面包括：

- 零长度 `TimecodeScale` 若被拒绝会直接影响 Matroska 时间轴、seek 和 duration；
- 零长度 `DisplayWidth/Height` 必须继续从 pixel/context 派生，不能被写成 0，否则会破坏 DAR；
- `OutputSamplingFrequency`、channels/language/track default 会影响轨道展示、自动选择和 AudioTrack 初始化；
- `BlockAddID` 与 WebHTV 的 Matroska Dolby Vision BlockAdditional/RPU 路径关联，默认值错误可能让 DV side data 失配；
- chapter/tag 默认值会影响当前 edition/chapter UI 和 metadata，而不是只影响“损坏文件能否打开”。

**收益：** 修复规范允许但当前被拒绝或误解释的 Matroska/EBML 文件；代码集中在通用 parser 与生成器，运行时改动小，适合随低风险 MPV 格式批次吸收。

**风险：** 本提交没有新增测试文件；默认表若与当前 Matroska spec/生成代码不同步，会把 malformed input 静默解释成合法值。`context_default` 采用“跳过写入”依赖后续 demux_mkv 已有派生逻辑，因此不能只手工复制 `ebml.c` parser hunk而遗漏生成器/descriptor。显式零长度、字段缺失、显式非零值必须三者区分测试。

**阶段 B10-2：** `52bb...` 与 `e167...` 必须连续合并或等价手工移植。用小型 synthetic MKV/EBML fixture 逐类覆盖：header defaults、TimecodeScale、track flags/language/lacing、DisplayWidth/Height、Sampling/OutputSamplingFrequency/channels、colour defaults、BlockAddID/DV、content encoding、chapter/tag；每项同时比较“missing / zero-length / explicit value”。另跑当前 Matroska segment-end seek 补丁、内嵌 ASS/PGS、DV BlockAdditional、多音轨自动选择和 chapter/edition UI。失败时只回滚 B10-2，不连带回滚 RGB10 或 HLS。

### 34.4 B10-3：HLS 初始 edition 使用 program BANDWIDTH，当前 App 功能可达且价值高

锁定 mpv 已把 lavf 多 program 映射为 editions，并按 `--hls-bitrate` 选择初始 edition；旧逻辑从每个 program 中挑一个 video stream，audio-only 时挑 audio stream，再使用 stream metadata 的 `variant_bitrate`。FFmpeg 会把整个 variant 的 bandwidth 写到单个 stream；共享音频/字幕在多个 variant 间复用时，stream 值可能缺失或错误，因此旧逻辑会选错清晰度。`e719...` 改为只读 `AVProgram.metadata[variant_bitrate]`，即 HLS `EXT-X-STREAM-INF:BANDWIDTH` 的 program 级值，并用 `nonempty[]` 跳过没有可用 track 的 program。

该功能在 WebHTV 不是不可达边角：`MpvPerformanceSetting` 暴露最高、15 Mbps、8 Mbps、最低；`MpvPlayerEngine` 把它写入 `hls-bitrate`，自动模式还会依据同网络/真实路径吞吐动态设置并重载媒体。App 同时读取 track `hls-bitrate` 用于诊断和预载匹配。此提交只改变 native 的**初始 edition 选择键**，不会自动修复 flatten-editions 下每条 track 的 tag，也不会改变 App proxy 自身解析出的 variant 列表。

**收益：** 共享音频/字幕的 master playlist 下，15/8 Mbps 和自动限码率更可能选择真正对应 BANDWIDTH 的 variant；空 program 不再被误选。对已有自动降档/reload 策略，这是直接可见的正确性改善，而非单纯内部重构。

**风险与边界：** 新实现明确删除 per-stream fallback。若某 FFmpeg/非标准 demuxer 没有给 program 写 `variant_bitrate`，所有 program 都缺 metadata 时会回退第一个非空 edition，而不是继续尝试 stream bitrate；`--flatten-editions` 仍按 track tag，旧 bug 不在本提交范围。`--edition` 显式选择优先级不变。BANDWIDTH 是声明值而非实测吞吐，清单标错仍会选错。

**阶段 B10-3：** 建议合并，并与当前自动 HLS 策略联合验收。测试矩阵至少包含独立音视频 variant、多个 variant 共享同一 audio group/字幕组、audio-only、空 program、program 缺失/非法 `variant_bitrate`、阈值正下/相等/正上、`min`/`max`、显式 `edition`、`flatten-editions=yes`，以及动态 15M→8M reload 后实际 selected program/track/诊断是否一致。对于 program metadata 全缺场景，应先记录真实 FFmpeg 9.0-fongmi 行为；若样片证明需要兼容 fallback，可在本地做“program 优先、stream 仅在整个 program 集合均无 metadata 时使用”的窄扩展，而不是直接放弃此修复。

### 34.5 分类、实施顺序与决策

| 分类/阶段 | 关联 commit | 前置/搭载 | 建议 |
| --- | --- | --- | --- |
| MPV B10-1 packed RGB10 RA | `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75`；可关联 libplacebo `22ee762e8e0890fc54068beb670310f0edce7263` | 当前 libplacebo API 375 已满足；搭载首次 MPV native rebuild | **建议合并，不单独重建** |
| MPV B10-2 EBML defaults | `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` + `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8` | 两提交不可拆；保留 Matroska segment-end 与 DV patches | **建议合并并补 synthetic fixtures** |
| MPV B10-3 HLS edition | `e7191f2a65d64af266c5c80793e79d2f4b92b789` | 与 App `mpv_hls_bitrate`、自动降档/reload 联合测 | **建议合并；program metadata 缺失为条件门槛** |

它们全部属于 MPV 依赖，不应提前混入 Exo AAR。按用户要求仍先完成 Exo 决策和合并；进入 MPV 时，建议在同一候选源码树中纳入 B10-1/2/3，但保留三个独立 commit/patch 和测试门槛，便于只回滚有问题的主题。B10 不要求升级 App JNI，也不构成删除任何 WebHTV Vulkan、DV7、TrueHD、OSD 或 Matroska seek 本地补丁的理由。

### 34.6 恢复锚点

- B10 四个 commit 的 parent/tree/patch-id 已在 34.1 落盘；`52bb...` 只作为 `e167...` 的生成器前置，不单独实施。
- B10-1 推荐随首次 MPV native rebuild；B10-2 是 Matroska correctness；B10-3 直接影响当前 App 已使用的 `hls-bitrate` 初始 variant/edition。
- 下一项审阅 B9 `7282d53d58fcb8841ff93debea2a75e0b2afcd15`：上游保留 IEC61937 codec carrier sample rate，并在 API 31+ 对 8-channel carrier 使用 7.1 mask；必须与本地无 API gate 的 TrueHD 专用 7.1 patch、构建校验中的“obsolete native-rate patch”禁用规则联合判断。
- 随后快速完成 B11：`f4d13...` Wayland null output、`e034d...` CI action、`b6d343...` comment typo；最后再深审 B8 `06ec6e...`、`f5c9f1...`、`44755d...`，不得在完成逐文件对照前删除本地 Surface/Vulkan/DV7 patches。

## 检查点 35：2026-08-21 mpv B9：AudioTrack 高码率 IEC61937 carrier 与 7.1 channel mask

### 35.1 身份、重落基关系与真实增量

目标提交 `7282d53d58fcb8841ff93debea2a75e0b2afcd15`（`ao_audiotrack: support high-bitrate passthrough`）的父提交为 `c2bc880511fd20850c586f2dc25aff770723b6b4`，tree 为 `036acd6c032236a61d167707f4439246c140e0eb`，stable patch-id 为 `a1e7bdc0b36d73cf7ed37644b4d65f8a3ef9a035`；1 个文件，`+21/-1`。

标题把两项行为放在一起，但相对 WebHTV 锁定 mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`，真实新增只有 channel mask 部分：

- 锁定线已有 `416d4a0fae8213ecf8e730feda6e2d8591bbd76f`（parent `9cdc2e4933a0d4e325aad4c6c2c6187499e6ca1b`，tree `a85b8a9331e890b34f02301c25b606c456c70fc1`，patch-id `8101f5d4f2f25e521fb7e2a8c2fd257e1a0fef54`），已把 `getNativeOutputSampleRate()` 限制为 PCM；IEC61937 不再把 E-AC3/DTS-HD/TrueHD 的 codec-defined 192 kHz carrier 压到常见的 48 kHz。
- `7282...` 在其重落基父链中重新包含这 1 行，因此整提交 patch-id 与锁定线不同；端点 `cca559... → 7282...` 对 `ao_audiotrack.c` 的实际差异只有新增 Android `Build.VERSION.SDK_INT` JNI 映射和 API 31+ 8-channel carrier 的 7.1 mask，共 20 行新增。
- 当前构建脚本把“使用设备 native output sample rate 覆盖 passthrough carrier”视为已废弃反向补丁并主动拒绝；这里不是要删除该校验。真正要决定的是 8-channel carrier mask。

### 35.2 codec carrier 语义和当前项目状态

mpv 的 SPDIF wrapper 当前生成：AC3/DTS core 为 2-channel、44.1/48 kHz；E-AC3 为 2-channel 192 kHz；DTS-HD HRA 为 2-channel 192 kHz；DTS-HD MA 为 8-channel 192 kHz；TrueHD 为 8-channel 192 kHz。Android `AudioTrack` 使用统一 `ENCODING_IEC61937`，因此 sample rate 和 carrier channel mask 描述的是封装载波，不等同于最终扬声器 PCM layout。

WebHTV 已有 `mpv-audiotrack-truehd-channel-mask.patch`：TrueHD 无论 API level 都使用 `CHANNEL_OUT_7POINT1_SURROUND`，其他 IEC61937 保持 stereo。该策略来自已验证的 Android TV/VLC 兼容经验：部分固件即使版本低于 Android 12，也会拒绝或错误路由声明为 stereo 的 TrueHD 8-channel carrier。App 侧 `MpvAudioCapabilities` 又做三层 gate：Media3 编码能力、实际 HDMI/ARC/eARC/USB 设备 encodings、`AudioTrack.isDirectPlaybackSupported()` carrier probe；probe 当前对 TrueHD 使用 7.1，对 `dts-hd` 仍使用 stereo。

因此整体采用 `7282...` 会产生两个语义变化：

1. Android 12/API 31+ 的 DTS-HD MA 也从 stereo 改为 7.1，符合其 8-channel IEC61937 carrier；但 App 预探测仍用 stereo，native 与 Java 会不一致，可能出现 Java 允许后 native 失败，或 Java 因 stereo probe 失败而错误禁用一个 native 7.1 可用路径。
2. Android 11 及更早的 TrueHD 会从本地已验证的 7.1 回退成 stereo，可能重新引入已有设备兼容问题；上游 API gate 的“31 起平台正式接受”不能证明所有旧 TV firmware 都应撤销本地 workaround。

### 35.3 利弊与建议实施

**收益：** 保留 192 kHz carrier 是 E-AC3/DTS-HD/TrueHD 能否建立 AudioTrack 的必要条件，当前已具备；API 31+ 为 DTS-HD MA/TrueHD 的 8-channel carrier 使用 7.1 mask 可更准确描述载波，减少高码率格式初始化失败、声道错误路由或回退 PCM。`Build.VERSION.SDK_INT` 的 JNI 映射本身风险低。

**风险：** Android 音频 HAL/AVR 对 IEC61937 channel mask 的接受度高度设备相关；7.1 并非对所有格式和旧固件都更兼容。DTS-HD HRA 在 mpv 中仍是 2-channel carrier，不应因为 codec 名是 DTS-HD 就一律用 7.1。App probe、native AudioTrack 构造和实际路由设备必须使用同一 codec/profile/channel 规则，否则“能力可用”与实际初始化会漂移。直通失败虽会由 mpv 回退 PCM，但会造成起播延迟、短暂无声和错误的自动轨道偏好。

建议不整体 cherry-pick `7282...`，而按两个已可独立证明的规则整合：

**阶段 B9-1：carrier sample-rate baseline。** 不产生新代码；登记 `416d4a0fae8213ecf8e730feda6e2d8591bbd76f` 已覆盖 `7282...` 的 sample-rate hunk，继续保留构建校验，确保 AC3/DTS core 仍为 44.1/48 kHz，E-AC3/DTS-HD/TrueHD 仍为 192 kHz。任何升级后若重新调用 native output rate 改写 passthrough，都直接判为回归。

**阶段 B9-2：codec-aware 8-channel mask。** 保留当前 TrueHD 7.1 规则，包括旧 API 的兼容路径；吸收 `7282...` 的 SDK_INT 映射，并仅对实际 `ao->channels.num == 8` 的 carrier 使用 7.1。DTS-HD 必须区分 MA（8-channel）与 HRA（2-channel），以 `ao->channels.num` 为准而不是只看 `AF_FORMAT_S_DTSHD`。同时把 App `supportsMpvCarrier()` 改为和 native 相同的 mask/rate 规则：TrueHD 7.1；DTS-HD MA 需要能从轨道/profile 或一次更精确的 native capability probe 区分，无法区分时不得盲目把所有 DTS-HD 当 7.1。

若短期不扩展 Java probe 传入 DTS-HD profile，最保守方案是只保留现有 TrueHD patch，并暂不启用 `7282...` 对 DTS-HD MA 的变化；这不会丢失当前已经工作的 high-bitrate carrier sample-rate。只有取得 DTS-HD MA/HRA 和 API 29/30/31+ 的真实设备矩阵后，再启用通用 8-channel 分支。

### 35.4 验收矩阵、回滚边界与分类

| 输入/系统 | 预期 sample rate / mask | 必测结果 |
| --- | --- | --- |
| AC3、DTS core；API 24-35 | 44.1/48 kHz + stereo | HDMI/ARC 起播、seek、pause/resume，不误用 192 kHz/7.1 |
| E-AC3/JOC；API 24-35 | 192 kHz + stereo | Atmos 标记、长播、线路切换，失败时 PCM fallback 无循环 |
| DTS-HD HRA；API 24-35 | 192 kHz + stereo | 不被通用 DTS-HD 名称误判为 8-channel |
| DTS-HD MA；API 29/30 与 31+ | 192 kHz；旧系统先 stereo/设备实测，31+ 条件 7.1 | Java probe 与 native 初始化一致，AVR 显示、声道和 fallback 正确 |
| TrueHD/Atmos；API 24-30 与 31+ | 192 kHz + 7.1（保留本地旧机兼容） | HDMI/eARC/USB、已知旧 TV firmware、无声/噪声/错误声道、PCM fallback |

还需覆盖路由热插拔、ARC↔eARC、USB DAC、App 自动选择高效/立体声音轨、手动选回无支持 carrier、AudioTrack init failure 后 decoder reinit。日志至少记录 codec、carrier rate、channel count/mask、SDK、`isDirectPlaybackSupported` 结果和 native init/fallback，才能判断失败位于能力探测还是 HAL。

| 分类/阶段 | 关联 commit | 建议 |
| --- | --- | --- |
| MPV B9-1 carrier rate | 锁定线 `416d4a0fae8213ecf8e730feda6e2d8591bbd76f`；目标线重含于 `7282d53d58fcb8841ff93debea2a75e0b2afcd15` | **已覆盖，不重复合并；保留反回归校验** |
| MPV B9-2 channel mask | `7282d53d58fcb8841ff93debea2a75e0b2afcd15` + 本地 `mpv-audiotrack-truehd-channel-mask.patch` | **选择性合并；TrueHD 保留本地规则，DTS-HD MA 待 profile-aware probe/样片** |

B9 是 MPV 依赖阶段，不需要改 Exo AAR。若实施 B9-2，native hunk 与 App capability probe 必须在同一可回滚阶段提交；不能只更新 native 后让 App 继续按旧 stereo probe 做选择。它可与 B10 搭载同一次 MPV native rebuild，但验收和回滚应独立。

### 35.5 恢复锚点

- `7282d53...` 完整身份与 patch-id 已记录；其 sample-rate 语义已由锁定线 `416d4a0f...` 精确覆盖，端点真实新增是 API 31+/8-channel 7.1 mask。
- 不用 `7282...` 整体覆盖本地 TrueHD patch。推荐 B9-1 保持现状，B9-2 采用 codec/channel-aware 合并；DTS-HD HRA 继续 stereo，DTS-HD MA 只有 Java/native 能力规则一致后才启用 7.1。
- 下一步审 B11 三项：`f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47`、`e034d612cf6893954e943916988eef9e4426604c`、`b6d3434493fd04c0ee40a5610d8c311b77b16a6d`；然后进入 B8 三个高风险提交。

## 检查点 36：2026-08-21 mpv B11：非 Android/非播放功能提交收尾

本检查点逐文件确认旧审计目标 `FongMi/mpv@44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 的三个 B11 提交。它们都不应成为 WebHTV 播放器二进制升级的独立理由；其中 Wayland 修复可随未来桌面版上游同步自然带入，CI 和注释改动仅保留在源码同步层。

### 36.1 提交身份

| Commit | Parent | Tree | Stable patch-id | 实际文件/变更 |
| --- | --- | --- | --- | --- |
| `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` | `513d3407d4e1e95ebb743c8e9c139b39d9880cc2` | `fc016de02fa5538c9120dc89dfebe15e2cb0ecfe` | `da714274341de1cb7b92fc444453c00d0756019c` | `video/out/wayland_common.c`，`+9/-4` |
| `e034d612cf6893954e943916988eef9e4426604c` | `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8` | `c149b0f2142476d7d6caf6a3408bf4d209cdfbf7` | `5b805eaba656a3c53a7315dcd8039116f75e9319` | `.github/workflows/build.yml`，`+2/-2` |
| `b6d3434493fd04c0ee40a5610d8c311b77b16a6d` | `e034d612cf6893954e943916988eef9e4426604c` | `2eab2b87c94dabb4b27d157aa308ae1f27c73a24` | `2b06555f107f7d8ecd5277607e6d26a6b71db7a3` | `player/javascript.c`，`+1/-1` |

### 36.2 `f4d13...`：Wayland `wl_surface.enter` 允许 null output

提交在 `surface_handle_enter()` 和 `update_output_geometry()` 两处增加 `wl->current_output` 判空：没有可识别 output 时只打印 `Surface entered unknown output`，并跳过对空指针 geometry 的比较。该行为对应 Wayland 协议允许 `output` 为 null 的事件语义，可避免桌面 Wayland 上的崩溃；它不触及 Android `ANativeWindow`、MediaCodec、Vulkan 或 MPV JNI 路径。

**收益与边界：** 对 Linux/FreeBSD/OpenBSD 等 Wayland 桌面构建是低风险健壮性修复；WebHTV 当前发布目标是 Android 手机/电视，README 和构建矩阵没有 Wayland 产物，因此不能把它当作 Android native 重建理由。若以后同步完整 mpv 源码，保留该提交即可；不需要回移到 `third_party/patches`，也不应为它单独构建 ABI。

### 36.3 `e034...`：OpenBSD/FreeBSD CI action 版本更新

仅把 `.github/workflows/build.yml` 中 `cross-platform-actions/action` 从 `v1.3.0` 更新为 `v1.4.0`，影响 OpenBSD/FreeBSD VM 测试 job。没有运行时、ABI、构建产物或 Android toolchain 变化。

**结论：** 不纳入 WebHTV 依赖合并清单；若维护上游 fork 的 CI，可在 CI 专项同步时单独采用并验证 action 兼容性，但不得与 MPV native 二进制版本绑定。

### 36.4 `b6d343...`：JavaScript API 注释单位 typo

把 `script_format_time()` 参数注释从 `time-in-ms` 改为 `time-in-seconds`，与函数实际 `js_tonumber()`/时间格式化语义一致；没有代码行为变化。

**结论：** 只作源码同步时的可选清洁修改，不形成补丁、测试或发布阶段。

### 36.5 B11 分类与实施决策

| 阶段 | 关联 commit | 归属 | 建议 |
| --- | --- | --- | --- |
| B11-D：Wayland null output | `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` | 通用桌面输出（非 Android） | 随完整上游同步自然带入；不单独合并/重建 |
| B11-M：CI action | `e034d612cf6893954e943916988eef9e4426604c` | 构建维护 | WebHTV Android 阶段跳过；需要时独立更新 CI |
| B11-T：注释 typo | `b6d3434493fd04c0ee40a5610d8c311b77b16a6d` | 文档/注释 | 可选源码清洁，不进入播放器阶段 |

### 36.6 恢复锚点

- B11 三个 commit 的 parent/tree/patch-id 与实际 diff 已记录；没有一个需要改变 Android AAR、MPV native、FFmpeg 或 libplacebo 资产。
- `origin/fongmi` 已重新 fetch，当前头仍为 `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`，其后暂无新增提交。
- 下一步进入 B8：逐文件对照 `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`、`f5c9f148d00db652da1ee900f386d8e0e615ed84`、`44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 与本地 stable/direct/legacy Vulkan、双 Surface OSD、timestamped release 和 DV7 fallback 补丁；在完成对照前不得删除任何本地补丁。

## 检查点 37：2026-08-21 mpv B8-1：Android Surface/HDR/Dolby Vision 重写的真实新增

本检查点完成 `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`（`video/out/android: improve surface, HDR, and Dolby Vision output`）的身份、重落基关系和逐文件对照。该提交的父提交是 `793d89800a425cda856065307c9027997ebf1c9c`，tree 为 `f4046ff725f80bd3938c7f057707c7b93e0b9b4a`，stable patch-id 为 `1400fbf6eb7dfb01b274b9f6b92c4daa566ed5eb`；提交日期为 2026-07-27/08-18，涉及 32 个文件，`+2192/-226`。

### 37.1 与锁定线的覆盖关系

锁定线对应提交 `62648ab1789c6e1b025fe9392857385f37314710`（parent `13d430dcec338fdc3182f7799e1c01682fa6f6aa`，tree `a69ecb011e108a6d1d488edb9eab93ca88fa20ed`，patch-id `2ec295cccb7ba2894d0fd7b0d89d873317dd88f9`）已包含同一标题下的 Surface、HDR metadata、Android OSD、OpenGL/Vulkan context、`mediacodec_embed` 和 DV 基础输出改动。`git range-diff 13d430dc^..62648ab1 793d8980^..06ec6e17` 显示该提交是重落基重写，而不是一个可由 patch-id 判定为完全等价的复制。

相对 `62648ab1` 的端点树差异只有 18 个文件、约 `+213/-84`，实际集中在四个方向：

1. `filters/f_decoder_wrapper.*`、`filters/f_enhancement_pair.*`、`filters/f_output_chain.*`、`video/decode/vd_lavc.c`、`player/loadfile.c`：新增 `force_swdec` 传播和 `VDCTRL_GET_SELECTED_HWDEC`，当 BL 使用 Android `mediacodec` 时强制 EL 解码器避开共享的单一 Surface。
2. `filters/f_enhancement_pair.c`：新增 `dovi_needs_el_pixels()`；若 BL 已有有效 DOVI 表示且不再需要 EL 像素，则只继承 metadata、释放 EL 图像。
3. `player/command.c`：解码器重置后重新计算 EL 链接状态，避免硬解模式变化后仍沿用旧 decoder mode。
4. 其余文件（`common/av_common.c`、Android context、双 Surface OSD、HDR option、`vo_mediacodec_embed.c`、Vulkan context 等）只是新父链对应的重落基上下文，未发现相对锁定树的独立 Android 功能新增。

### 37.2 新增机制的收益

上游新增机制针对一个真实约束：Android AImageReader/MediaCodec 的零拷贝视频输出通常只有一个 producer Surface；如果同时让 BL 和独立 EL 都走硬件 decoder，第二路可能争抢 Surface、初始化失败、卡住或产生错误帧。`force_swdec` 通过 stream-info 传入增强层 decoder，在创建硬件 wrapper 前拒绝带硬件能力的 codec，保留 BL 的硬件输出；它比简单“遇到失败再回退”更早、更确定。`dovi_needs_el_pixels()` 则减少 MEL/metadata-only 情况的额外 GPU/内存占用。

若目标是 Linux/桌面 gpu-next 或未来 Android 多 Surface/多 producer 硬件路径，该机制有明确价值：可以把“EL metadata 需要参与”与“EL 像素必须送进 GPU”分开，降低 Surface 冲突和显存压力。

### 37.3 与 WebHTV 当前策略的冲突和风险

当前项目的 [mpv-android-dovi-el-surface.patch](../third_party/patches/mpv-android-dovi-el-surface.patch) 已把 Android `VO_CAP_GPU_DOVI_EL` 限制为非 Android，并在 Android AImageReader 单 producer 条件下明确不选择独立 EL；`README.md` 和 `third_party/mpv-native-build.md` 也把“GPU DV5 映射 + DV7 HDR10 基底层回退、不开第二路 `mediacodec-copy`”列为现行契约。换言之，`06ec...` 不是对当前策略的无冲突修复，而是把 Android GPU FEL/MEL 双层实验重新打开。

直接 cherry-pick 会带来几个风险：

- `force_swdec` 只约束 EL，BL 仍可能是 MediaCodec；4K/10-bit/高码率 DV7 的软件 EL 解码会显著增加 CPU、内存和功耗，且未证明当前设备上的 FFmpeg EL 输出能与硬件 BL 精确按 PTS 配对。
- `dovi_needs_el_pixels()` 依赖 `bl->params.repr.dovi` 和 `nlq_active` 的时序。如果 RPU metadata 尚未附着到 BL，过早释放 EL 会退化为只带不完整 metadata 的 BL；如果 profile/codec 组合不是 MEL，释放 EL pixels 也可能改变画面语义。
- 新增的 `VDCTRL_GET_SELECTED_HWDEC` 与 `force_swdec` 改动会扩大 `filters`/decoder 公共接口，必须和当前 fork 的本地 `mpv-android-dovi-el-surface.patch`、DV7 demux patch、FFmpeg API 一起重落基，不能只复制几个函数。
- 当前 App 的自动输出策略、`isDv7Hdr10FallbackEnabled()` 和 native verification 都假定 Android DV7 fallback 是 BL-only；引入 Android EL 路径需要重新定义诊断、用户设置和失败回退，不是纯 native 替换。

### 37.4 建议的实施阶段

**B8-1a：默认路径保持现状。** 不合并 `06ec...` 的 Android EL 语义，不删除 `mpv-android-dovi-el-surface.patch`；锁定线已覆盖 Surface/HDR/OSD 主体，当前 Android 产物无需因该提交重建。

**B8-1b：可选 DV7 GPU FEL/MEL 实验（条件合并）。** 只有在取得真实 Profile 7 FEL/MEL 样片、至少一组 Android TV/手机硬件矩阵，并决定接受软件 EL 的 CPU 成本后，才将上述接口作为独立实验补丁移植。实验必须包含：BL mediacodec + EL FFmpeg software、BL/EL PTS 严格配对、RPU metadata 完整性、OpenGL/Vulkan/gpu-next 输出、Surface 销毁/重建、seek/换集和 decoder failure。实验失败时应只回滚 B8-1b，不能影响当前 BL-only HDR10 fallback。

**B8-1c：MEL metadata-only 窄吸收（更可能可行）。** 若样片证明仅需从 EL 继承 RPU、无需 EL 像素，可只吸收 `dovi_needs_el_pixels()` 的等价判断到现有 BL-only/metadata 管线；但必须先确认 `repr.dovi` 与 `nlq_active` 在 FFmpeg 当前锁定版本上的定义，并为 EL 释放增加帧级回归。该窄移植也不能直接 cherry-pick 整个提交。

### 37.5 分类与结论

| 分类/阶段 | 关联 commit | 当前覆盖/依赖 | 建议 |
| --- | --- | --- | --- |
| MPV B8-1a Android Surface/HDR/OSD | `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`；锁定已有 `62648ab1789c6e1b025fe9392857385f37314710` | 当前主功能已覆盖；本地双 Surface/OSD/timed release/Vulkan/DV7 补丁仍有效 | **不重复合并** |
| MPV B8-1b Android BL+EL 双层实验 | `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042` 的 `force_swdec` 相关 hunks | 需要真实 FEL/MEL 样片、软件 EL 性能和多设备矩阵 | **条件合并，默认暂缓** |
| MPV B8-1c MEL metadata-only | `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042` 的 `dovi_needs_el_pixels()` | 依赖 DOVI 表示时序和 FFmpeg 版本语义 | **窄移植候选，不接整提交** |

该阶段属于 MPV Android 输出，不应提前并入 Exo。它也不要求升级 libplacebo；若 B8-1b 实施，必须与 FFmpeg DV metadata、App 输出策略和 native verification 联合发布，并保留可独立回滚边界。

### 37.6 恢复锚点

- B8-1 的两个完整 commit ID、parent/tree/patch-id、18 文件端点差异和四类真实新增已记录。
- 当前 Android 默认仍为 BL-only/单 Surface 安全策略；不得因 `06ec...` 删除 `mpv-android-dovi-el-surface.patch` 或 `mpv-dovi-profile7-hdr10-base-layer.patch`。
- 下一步审 `f5c9f148d00db652da1ee900f386d8e0e615ed84`：对照本地 direct/legacy/stable 三后端、CPU-precomputed UV、AImageReader 稳定 acquire/release、shader contract 和 `gpu-next` deferred mapping；重点判断哪些上游基础设施能选择性吸收，哪些会覆盖本地 queue-safe/fence 语义。

## 检查点 38：2026-08-21 mpv B8-2：AImageReader Vulkan 重写与 generic UV 预计算

目标提交 `f5c9f148d00db652da1ee900f386d8e0e615ed84`（`hwdec/aimagereader: add Android Vulkan interop`）的父提交为 `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`，tree 为 `49376fbe987a833dd1c8e41a18f428e900a48474`，stable patch-id 为 `ebb2a24858351b9815717d9fd146e3949a72e8f6`；提交表面涉及 21 个文件、`+5565/-178`。

### 38.1 绝大多数主体已由锁定线精确覆盖

锁定线对应提交 `cb007d6f6b520ff57a4bedd5f8bcd330f64c88a0`（parent `62648ab1789c6e1b025fe9392857385f37314710`，tree `20c10c0e55654ac95125a7b4ead6fbc319a80c83`，patch-id `274120b84448030dc4406782c8820f0f702b3ce1`）已经包含：

- AImageReader 同时支持 OpenGL 与 Vulkan mapper；
- `AImageReader_acquireLatestImageAsync()`、acquire/release sync-fd、buffer-removed callback 和 AHardwareBuffer cache；
- Vulkan direct YCbCr external-format sampling；
- compute/fragment conversion fallback、三输出池、format/descriptor/queue-family 处理；
- `RA_HWDEC_MAP_RETRY`、deferred dst params、map-on-reset、gpu-next 预取和渲染失败上报；
- crop/dataspace/HDR/DV 参数映射、截图/OSD source-coordinate 修正；
- Android Vulkan 所需扩展与允许 rotated SurfaceView 保持 suboptimal swapchain。

`git diff cb007d6f..f5c9f148` 在完整 21 文件集合中实际只剩五个文件：

| 文件 | 实际差异 |
| --- | --- |
| `video/out/hwdec/hwdec_aimagereader.comp` | push constants 从整数 crop/source geometry 改为 `uv_offset`/`uv_scale` |
| `video/out/hwdec/hwdec_aimagereader.frag` | 同上 |
| `video/out/hwdec/hwdec_aimagereader_comp.h` | 上述 compute shader 的重新生成 SPIR-V C 数组 |
| `video/out/hwdec/hwdec_aimagereader_frag.h` | 上述 fragment shader 的重新生成 SPIR-V C 数组 |
| `video/out/hwdec/hwdec_aimagereader_vk_convert.c` | CPU 用 double 中间值预计算 offset/scale，日志仍保留原始 crop/source geometry |

因此不能把 `+5565` 当成当前项目的新增功能量；真实增量约为 converter/shader 的 `+38/-20` 加生成数组更新。锁定树的 Vulkan interop 主体已经运行在当前二进制中。

### 38.2 UV 预计算的等价性、收益与风险

旧 shader 每个输出像素计算：

`uv = (crop_offset + (position + 0.5) * crop_size / output_size) / source_size`

新实现由 CPU 计算：

`uv_offset = crop_offset / source_size`

`uv_scale = crop_size / (output_size * source_size)`

shader 只执行 `uv_offset + (position + 0.5) * uv_scale`。两式代数等价；CPU 使用 double 进行除法后转 float，减少每像素整数转浮点和两次除法，尤其能降低 4K/8K compute conversion、fragment fallback 和 legacy compute 路径的 ALU 成本。direct backend 没有 conversion shader，因此不受影响。

项目的四输出 stable override 已经使用同一 `uv_offset`/`uv_scale` 契约，并由 `scripts/verify_mpv_vulkan_shader_contract.py` 验证 C/shader workgroup、push constant token 和生成文件；但锁定的 generic converter 仍用旧的 per-pixel 算式。也就是说，上游优化对当前默认 direct 成功设备几乎无收益，对 direct 不支持而落到 stable 的设备已被本地实现覆盖；真正受益的是显式 `legacy`、`compute`、`fragment`，以及 stable 初始化失败后的 generic fallback。

**风险：** CPU float 化可能在奇数 crop、非整倍缩放、超大 stride 或非常大的 source extent 下产生与 shader 分步计算不同的末位舍入；若 push constant C layout、SPIR-V header 和源 shader 未成套更新，会直接导致采样坐标错位。旋转和 crop 的最终变换仍由 gpu-next source mapping 处理，本提交不应顺带改动该逻辑。

### 38.3 必须保留的 WebHTV 本地语义

`f5c9...` 不覆盖、也不能替代以下当前本地功能：

- `mpv-android-vulkan-smart-backend.patch`：`auto` 为 direct → stable → generic，而非上游 direct → generic；App 还能记忆 direct failure 并重建为 stable。
- `mpv-android-vulkan-conversion-default.patch` + stable override：四输出 bounded-fence pool、packed RGB10/RGBA16F format fallback、DV raw YUV component mapping。
- generic converter 禁用 release sync-fd、等待 conversion fence 后再释放 AImage，避免部分 Adreno BufferQueue starvation。
- `mpv-aimagereader-stable-flow.patch`：callback sequence counter、100 ms 有界等待、transient retry/logging；不能退回单个 `image_available` 布尔 wakeup。
- `mpv-android-vulkan-legacy-backend.patch`：保留三输出 compute 路径作兼容比较。
- native verification 中 direct/stable/legacy、AImage lifetime、CPU-precomputed stable shader 和首帧失败回退字符串。

整体切换到目标树后，上述补丁必须逐项重落基并重新检查 `git apply --check`；不能因提交标题相同就删除。

### 38.4 建议实施阶段

**B8-2a：Vulkan interop 主体。** 记为锁定线 `cb007d6f...` 已覆盖，不重复移植 `f5c9...` 的 21 文件主体。

**B8-2b：generic conversion UV 预计算。** 建议选择性吸收 `f5c9...` 在五个文件中的真实增量，并随首次 MPV native rebuild 搭载，不单独发布。必须从 `.comp/.frag` 使用锁定 NDK r29 `glslc` 重新生成两个 header，不手工复制二进制数组；可以给 generic shader 增加与 stable 类似的 contract 校验，防止源/数组/C struct 漂移。

验收矩阵：显式 `legacy`/`compute`/`fragment` 各跑 SDR、HDR10、DV5 raw mapping、奇数 crop、非整倍缩放、旋转 90/270、截图、字幕/OSD、LUT；比较更新前后像素边界和 GPU/帧时；再跑 `auto` direct、direct→stable 和 stable→generic fallback，确认 selector、fence/AImage lifetime 与 App 记忆策略不变。

### 38.5 分类与结论

| 分类/阶段 | 关联 commit | 建议 |
| --- | --- | --- |
| MPV B8-2a AImageReader Vulkan 基础设施 | `f5c9f148d00db652da1ee900f386d8e0e615ed84`；锁定已有 `cb007d6f6b520ff57a4bedd5f8bcd330f64c88a0` | **已覆盖，不重复合并** |
| MPV B8-2b generic UV 预计算 | `f5c9f148d00db652da1ee900f386d8e0e615ed84` 的五文件残余 | **建议窄移植，随 MPV native rebuild** |
| MPV B8-2c 本地 queue/fence/backend 策略 | WebHTV stable/direct/legacy/acquire patches | **必须保留并逐项重落基** |

B8-2 只属于 MPV Vulkan 输出，不要求同步 Exo；也不要求 libplacebo API 升级。可与 B10-1 packed RGB10 和 libplacebo alpha fix 搭载同一次 native rebuild，但测试/回滚仍分开。

### 38.6 恢复锚点

- `f5c9...` 和对应锁定提交的完整身份、真实五文件残余、UV 等价公式与本地覆盖边界已记录。
- 不删除 stable source/patch、smart selector、legacy selector、stable acquire flow 或 release-fence workaround。
- 下一步深审 `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 与 `mpv-dovi-profile7-hdr10-base-layer.patch`：重点比较 BSF 失败/空包语义、AVPacket ref/copy、codecpar 更新、direct Surface 禁用、EL selection，以及 FFmpeg MediaCodec starvation guard。

## 检查点 39：2026-08-21 mpv B8-3：DV7 HDR10 基底层 fallback 的上游增量与本地安全契约

本检查点完成旧审计头最后一个非等价提交 `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`（`demux: support Dolby Vision profile 7 HDR10 fallback`）的完整 diff、最终树和当前 WebHTV patch 对照。提交 parent 为 `1c2d989b6b246c36869fff9ec8297c9897e1d964`，tree 为 `4f4b131d3b9bb713fe1e0127448794de5649ca99`，stable patch-id 为 `c1de01aba5dec040af172c2f2832cdab7dcf9bfa`；涉及 10 个文件，`+178/-51`。

当前本地 [mpv-dovi-profile7-hdr10-base-layer.patch](../third_party/patches/mpv-dovi-profile7-hdr10-base-layer.patch) 的 stable patch-id 为 `d89bc513a3d2f710bc55d225657818aef42b374a`，涉及 7 个文件。App 已在 `MpvPlayerEngine` 始终写入 `demuxer-dovi-profile7=hdr10|preserve`，构建和资产校验还固定检查三条本地诊断字符串。因此上游提交不是一个尚未实现的新功能，而是同一目标的后续独立实现；必须按 hunk 取舍，不能按标题整体覆盖。

### 39.1 已由 WebHTV 覆盖的主体

两套实现都完成以下核心行为：

- 增加 opt-in `--demuxer-dovi-profile7=<preserve|hdr10>`，默认保持 `preserve`；
- HDR10 模式给 FFmpeg `dovi_split` BSF 设置 `mode=bl`，在 decoder 前剥离 EL 和 RPU NAL；
- preserve 模式继续用 `mode=el_rpu` 生成虚拟 EL stream；
- Matroska 和 lavf packet 都在 demux 阶段经过同一 splitter，并在 seek/reset 时 flush BSF；
- HDR10 fallback 不把 EL packet 送入增强层解码链；
- 输出 packet 继承原 BL 的 PTS/DTS/duration/keyframe/stream attributes；
- App 的用户设置、自动诊断和 native asset verification 已认识该模式。

因此 B8-3 的用户可见主体已覆盖。若直接 cherry-pick `44755d...`，不仅会重复 option/BSF/packet 路径，还会同时带回 B8-1 的 Android EL 软件解码策略，覆盖当前单 Surface 安全门。

### 39.2 上游真正值得吸收的五类增量

**1. metadata 不完整时仍建立 splitter。** 本地锁定实现只在 `dv_el_present=true` 时创建 splitter；上游把 `base_only` 严格定义为 `dv_profile == 7 && option == hdr10`，并允许这种情况下即使 `dv_el_present` 缺失也创建。`demux_mkv` 对视频轨统一调用 create，lavf 也不再提前按 `dv_el_present` 过滤，而由 create 自己拒绝非 HEVC/非 DV 候选。这对真实世界中 DOVI config 不完整、EL flag 被 remuxer 丢失但 NAL 仍交织的文件有直接价值；否则 App 显示已启用 fallback，实际 packet 却完全没有被过滤。

**2. 用 BSF `par_out` 清理 decoder codec parameters。** `dovi_split` 的 `bl` 初始化会把 DOVI configuration 的 EL/RPU present flag 清掉，并移除已经无效的 `AV_PKT_DATA_HEVC_CONF`。上游把 `par_out` 复制回 `bl->codec->lav_codecpar`，再设 `dv_el_present=false`，避免 packet 已变成纯 BL、decoder headers 却仍宣称包含 EL/RPU。该修正可降低 FFmpeg/MediaCodec 继续按 Dolby Vision bitstream 配置 decoder、或 GPU 管线错误期待 EL 的概率。

**3. splitter 初始化错误路径完整。** 上游检查 `mp_codec_params_to_av()`、`avcodec_parameters_copy()`、`av_opt_set()` 和 `av_bsf_init()` 的返回值，并记录具体 `mp_strerror()`。本地补丁沿用旧代码，部分 copy/init 失败只静默返回；应吸收上游的 `ret` 传播和日志，但还应决定 fallback 初始化失败时是中止播放、退到 GPU/preserve，还是明确 fail-open。单纯“留下原 bitstream 不变”会让普通 HEVC MediaCodec 再次面对 EL/RPU，不能当作可靠 fallback。

**4. packet 长度边界。** 上游在 `demux_packet.len` 转 `AVPacket.size` 前检查 `INT_MAX`。这是低成本的整数截断防护，建议保留在合成后的本地 helper 中。

**5. 自动 direct Dolby Vision VO 的 track-level 禁用。** 上游在 `wants_android_dolby_vision_direct_output()` 中排除 Profile 7 HDR10 fallback，避免 mpv 因源轨仍标记 `codec->dovi` 而自动切到 native DV direct VO。该判断对使用 `--android-dolby-vision-output=direct` 的 mpv 自动 VO 选择有意义；可作为防御性补充移植，但不能替代 WebHTV 当前 decoder-level 保护。

### 39.3 必须保留的本地 packet safety

上游最终实现的 `copy_packet_data()` 只要存在 `dp->avpacket` 就 `av_packet_ref()`，随后直接把 `copy->data/size` 改成 `dp->buffer/len`。ownership 本身仍由原 `AVBufferRef` 托住，但若 demux packet 指向原 AVPacket 的子区间，新的逻辑不能保证该子区间末尾仍有 `AV_INPUT_BUFFER_PADDING_SIZE` 个零；`dovi_split` 内部 `ff_h2645_packet_split(..., H2645_FLAG_SMALL_PADDING)` 正依赖合法 padding。当前本地实现只在 `avpacket->data == buffer && avpacket->size == len` 时零拷贝 ref，其余情况用 `av_new_packet()` 建立带 padding 的复制，并复制 packet properties。这个条件更严格，应保留。

本地还把结果拆成 `SPLIT_PACKET_ERROR`、`SPLIT_PACKET_EMPTY`、`SPLIT_PACKET_READY`：

- `EMPTY` 表示本 access unit 只有 EL/RPU、没有选中的 BL NAL，是可预期的丢包；原 packet 被释放，返回成功且 `*dp=NULL`；
- `ERROR` 表示分配、send、receive 或 packet conversion 真失败；保留原 packet ownership 给 caller，打印 `DV7 HDR10 fallback: failed to produce base-layer packet.`，caller 再明确丢弃；
- `READY` 才替换为过滤后的 BL packet。

上游把空包、分配失败和过滤失败都折叠成 `NULL/false`，虽然当前 caller 最终都会丢弃 packet，诊断和恢复策略却无法区分。WebHTV 已用字符串校验保证错误 guard 存在，不应退回上游的合并状态。推荐的合成实现是“本地三态 + 精确 AVPacket ref/padded copy + 上游 `INT_MAX`/init ret 检查”。

### 39.4 direct Surface 的两种策略不能混为一谈

上游的 track guard 只阻止 mpv **自动**因 Dolby Vision 轨选择 `mediacodec_embed`。WebHTV App 的 Surface Direct 模式会显式配置 `vo=mediacodec_embed` 和 `hwdec=mediacodec`；即使采用上游 guard，该显式 VO 仍会使用 `VO_CAP_NATIVE_DOVI`。而 `44755d...` 没有修改 `vd_lavc`，decoder 仍会得到 `dovi_sink_support=1`。

本地补丁在 `vd_lavc` 里按 `dv_profile==7 && demuxer option==hdr10` 把 decoder 的 native-DV sink capability 改为 false，同时保留 MediaCodec direct Surface，日志为 `DV7 HDR10 fallback: using MediaCodec base-layer decoder with direct Surface output.`。这正好匹配当前产品契约：Surface Direct 仍可作为低开销输出路径，但 decoder 必须按普通 HEVC/HDR10 处理已过滤的 BL。删除这段会使“packet 已去 DV、decoder 仍按 native DV sink 配置”的语义重新不一致。

建议保持本地 decoder-level gate，并可额外吸收上游 auto-VO guard。是否在 DV7 fallback 下彻底强制 GPU 输出应由设备样片决定，而不是由这次上游同步顺带改变；强制 GPU 的收益是避开不兼容的 direct HAL，代价是 AImageReader/Vulkan/OpenGL 转换、功耗和设备差异增大，也会覆盖用户明确选择 Surface Direct 的行为。

### 39.5 与 B8-1 最终树的联合判断

`44755d...` 的父链仍保留 B8-1 的 `VDCTRL_GET_SELECTED_HWDEC`、`force_swdec` 和“BL 为 MediaCodec 时 EL 强制软件解码”机制；fallback 模式仅通过 `track_uses_dovi_p7_hdr10_fallback()` 让本次播放不选择 EL。preserve 模式下，目标树仍会在 Android GPU 输出启用独立 EL。

当前 WebHTV 的 `mpv-android-dovi-el-surface.patch` 则要求 VO 明确具备 `VO_CAP_GPU_DOVI_EL` 才选择 EL，Android AImageReader 默认不声明该能力，并输出 `video output has no queue-safe EL decoder`。因此整体切换到目标树会重新打开 B8-1 已判定为高风险的 BL MediaCodec + EL software 路径。B8-3 合成时必须保留本地 capability gate；`track_uses...` 可保留为 fallback 的额外判断，但不能代替 `VO_CAP_GPU_DOVI_EL`。

### 39.6 可实施阶段与建议

| 分类/阶段 | 关联 commit / 本地输入 | 建议 |
| --- | --- | --- |
| MPV B8-3a DV7 BL-only 主体 | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`；本地 patch-id `d89bc513a3d2f710bc55d225657818aef42b374a` | **已覆盖，不整体 cherry-pick** |
| MPV B8-3b metadata/codecpar 完整性 | `44755d...` 的 create 条件、`par_out -> lav_codecpar`、`dv_el_present=false`、init ret 和 `INT_MAX` hunks | **建议窄吸收，合入本地补丁** |
| MPV B8-3c packet ownership/error contract | WebHTV 三态 helper、精确 ref/padded copy、错误字符串 | **必须保留；只叠加上游边界检查** |
| MPV B8-3d Surface Direct 策略 | 上游 `track_uses_dovi_p7_hdr10_fallback()` + 本地 `vd_lavc` decoder gate + App 显式 VO | **保留本地 gate；上游 auto-VO guard 可防御性吸收** |
| MPV B8-3e Android EL 安全门 | `06ec6e...`/`44755d...` 的 force-swdec 父链；本地 `VO_CAP_GPU_DOVI_EL` patch | **继续使用本地单 Surface 策略，不重开 EL** |

B8-3b 建议随首次受控 MPV native rebuild 搭载，并与 B8-2b、B10 和 libplacebo shader fix 共用构建批次，但保持独立 commit、测试和回滚边界。它是 MPV 依赖功能；FFmpeg `dovi_split` 是实现前置，样片语义可归入通用 C1 联合验收，但本次不要求修改 Exo AAR，也不能拿它替代 Exo 的 DV7/P8.1 路径。

### 39.7 验收矩阵

- 容器：NALU-interleaved MKV、lavf MP4/M2TS；DOVI config 完整、`dv_el_present` 缺失、只有 profile 7 metadata、独立 EL track 四类。
- 内容：Profile 7 MEL/FEL、DV5、P8.1、普通 HDR10；每类至少覆盖开播、seek、暂停恢复、换集/replace media、decoder flush 和 EOF。
- 输出：显式 Surface Direct + MediaCodec BL、OpenGL gpu-next、Vulkan direct/stable/legacy、软件解码；确认 fallback 仅对 P7 生效。
- packet：BL+EL+RPU、BL-only、EL/RPU-only access unit、损坏 NAL、BSF send/receive failure；验证空 AU 不误报 hard error，真实错误不静默。
- metadata：过滤后的 `lav_codecpar` 不再宣称 EL/RPU，HDR10 mastering/content-light/color transfer 保留，App 仍报告 source profile 7 和“已启用 HDR10 fallback”。
- Android 生命周期：Surface/OSD detach、direct init failure、AImageReader fallback、seek 后 BSF flush；确认不会重开第二路 MediaCodec/EL producer，也不会触发现有 MediaCodec starvation guard。
- 构建契约：两 ABI 的 `libmpv.so` 继续包含三条 WebHTV DV7 校验字符串，并新增或保留 metadata-missing splitter 的可验证日志/fixture。

### 39.8 恢复锚点

- 旧审计头 `mpv@44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` 的 27 个增量提交至此均已完成首轮审阅；B8 三个高风险残余已有逐文件结论。
- B8-3 不整体合并。优先窄吸收 metadata-missing splitter、`par_out` codecpar、`dv_el_present=false`、init ret 和 `INT_MAX`；保留本地三态/padding/ownership、decoder-level Surface Direct gate、App option/diagnostics、native strings 和 `VO_CAP_GPU_DOVI_EL`。
- 下一步重新 fetch FFmpeg、mpv-android、media、mpv、libplacebo 的目标分支并记录旧审计头之后的所有新增 commit ID；若无新增，则进入跨仓库最终实施阶段收敛，不再重复检查已覆盖提交。

## 检查点 40：2026-08-21 五仓库远端头复核：旧审计头之后零新增

本检查点在完成 B8-3 后重新刷新五个二进制依赖仓库的远端 refs，并用 `git ls-remote` 直接核对 GitHub 分支头；随后在本地明确用“旧审计头..remote ref”计算提交数。五个目标分支都没有前进，也没有出现新的强推替代头。

| 仓库/分支 | 检查点 1/39 审计头 | 2026-08-21 远端头 | 审计头之后新增 |
| --- | --- | --- | --- |
| FFmpeg `release-9.0-fongmi` | `177f090e0503b7e013922ca903bde14b1c375f18` | `177f090e0503b7e013922ca903bde14b1c375f18` | 0 |
| mpv-android `fongmi` | `7523b5c5199c84da4092787b7bf5d72452d61780` | `7523b5c5199c84da4092787b7bf5d72452d61780` | 0 |
| media `release-1.11.0-fongmi` | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 0 |
| mpv `fongmi` | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | 0 |
| libplacebo `fongmi` | `2301953d9faf0f5e112ff337f79cec64eab2f4f1` | `2301953d9faf0f5e112ff337f79cec64eab2f4f1` | 0 |

补充观察：FFmpeg、mpv-android、mpv 和 libplacebo 的 `master` 分支各自还有不同头，但当前项目 lock 和上游打包链明确跟踪的是上述 FongMi feature/release 分支；不能把 master 的普通上游推进混入本轮“FongMi 二进制依赖包”提交集合。libplacebo fetch 同时更新了已登记 submodule 的远端 refs/tags，但 `fongmi` 主分支未变化，不形成新的 WebHTV 合并候选。

### 40.1 当前逐仓库审阅状态

- FFmpeg：锁定线之后 49 个提交已审完，远端无新增；后续只在实施 C0/C1/C2 时按选定功能重放。
- mpv-android：24 个提交已审完，远端无新增；15 个精确等价、2 个语义重落基、1 个增强、6 个维护项的结论继续有效。
- media：82 个提交已审完，远端无新增；进入 Exo 实施批次收敛，不接受整支覆盖当前 fork。
- mpv：27 个提交已审完，远端无新增；B8-B11 的非等价残余都有结论。
- libplacebo：7 个提交已审完，远端无新增；实际新增仍只有 shader object allocation 前置和 alpha 保留修复。

### 40.2 下一阶段工作方式

从本检查点开始，除非远端头再次变化，不再按仓库重复枚举提交。下一步按用户决策顺序生成可实施批次：

1. 先整理 Exo 阶段：把 A1-A7 中“建议合并、已覆盖、条件合并、暂缓”压缩成少量有依赖顺序的批次，每批列出完整 commit ID、必须保留的本地补丁和验收门槛。
2. 再整理 MPV 阶段：把 FFmpeg、mpv、libplacebo、mpv-android 的相关功能合成同一次或相邻 native rebuild，但保留主题级 commit/回滚边界。
3. 最后整理通用阶段：标明 C0-C3 应随 Exo、随 MPV、分两次采用同一源码 commit，或继续实验暂缓。
4. 每完成一类批次就更新顶部恢复锚点，避免在最终汇总前丢失 commit 映射和条件结论。

### 40.3 恢复锚点

- 2026-08-21 五个目标远端分支均与已审计头一致，新增 commit 数全部为 0；当前无需启动新的逐提交循环。
- 不修改 `third_party/fongmi-repositories-lock.json`、AAR、`.so` 或任何本地 patch；本轮仍只更新评估文档。
- 下一步先做 Exo 可决策实施批次总表；完成一组即落盘，再做 MPV 和通用功能总表。

## 检查点 41：2026-08-21 Exo 可决策实施批次总表

本检查点不再按仓库提交顺序复述检查点 8--33，而是把已经审完的 `media` #1--82、FFmpeg C0/C1/C2 和 WebHTV 本地实现收敛成可以逐批批准、构建、验收和回滚的 Exo 实施顺序。历史检查点仍保留逐 hunk 证据；本表负责回答“先做哪一批、每批带哪些完整 commit、必须保留什么、产出什么、失败时回滚什么”。

### 41.1 总体实施规则

1. **不整支替换 Media3 fork。** 当前正式基线仍是 WebHTV fork `e3e922d5c01bc0b564849940fe589daf37360d15` 加 `third_party/media-lock.json` 和本地 patch；上游 `release-1.11.0-fongmi@3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` 只作为逐 hunk 来源。
2. **先 Exo、后 MPV。** E1--E9 的已批准 Exo 批次完成 AAR/APK 验收前，不更新 MPV native lock。通用 FFmpeg revision 可以先由 Exo 使用，但不能把 Exo 的 `libav*` AAR 产物复用到 MPV。
3. **一个功能批次可以含多个仓库，但不能含多个不可归因的发布行为。** 例如 E2 可以同时引用 Media3 DV parser 和 FFmpeg DV metadata 作为联合验收来源，但 Media3 AAR、nextlib AAR 与 MPV native 必须是不同产物和回滚单元。
4. **已覆盖不等于删除来源记录。** patch-id/语义等价提交仍保留完整上游 hash，迁移到新基线时标为“由新基线继承”，当前不再次 cherry-pick。
5. **每个子阶段独立提交、独立 AAR 版本。** 同一功能中的 parser、公共数据契约、UI 行为和 App 策略分开提交；若某一步失败，只回滚该 AAR/App adapter，不连带回滚此前已通过的批次。
6. **本检查点只给出实施设计。** 不更新 `third_party/fongmi-repositories-lock.json`、`third_party/media-lock.json`、本地 Maven、AAR、APK 或任何 native `.so`。

### 41.2 建议顺序总览

| 顺序 | 决策批次 | 主要目标 | 当前总建议 | 搭载通用项 | 产物与独立回滚边界 |
| ---: | --- | --- | --- | --- | --- |
| E0 | 基线冻结与去重登记 | 固定 fork、本地 patch、样片和测试基线 | **直接确认，无代码** | 无 | 只更新迁移清单/测试基线；无 AAR |
| E1 | Exo FFmpeg 9.0.1 安全基线 | 先升级现有 nextlib 的 FFmpeg，不改变 renderer 架构 | **建议优先实施** | C0；C2 只存在于源码、保持禁用 | 仅 nextlib 两 ABI AAR + APK；回滚 nextlib 版本，不动 Media3/MPV |
| E2 | Dolby Vision/HDR | HDR parser 安全 → DV CSD → 输出策略 | A1-1 **建议**；A1-2 **条件**；A1-3 **用户决策** | C1 共用样片；C2 不启用 | Media3 AAR 与 App adapter；三个子阶段分别回滚 |
| E3 | DTS/E-AC3/TrueHD/Atmos | 小型 parser 修复 → 多声道/时钟 → 容器接线 | 小修 **建议**；主体 **条件** | 后续与 MPV B9 共用音频样片，不共用代码 | Media3 AAR + APK；不改 FFmpeg/MPV `.so` |
| E4 | 字幕数据与渲染 | 字节安全 → Cue 契约 → viewport → ASS/PGS/TTML | 基础 **建议**；视觉行为 **分阶段条件合并** | 无 C0--C3 强依赖 | common/extractor/ui/App 分开提交和回滚 |
| E5 | 流媒体、容器与 seek | DASH/HLS/TS/MP4/MKV/RTSP 窄 correctness | 已覆盖项只补测；剩余按样片选择 | C1 的 HLS/MMT/时间戳语义 | 每个 demux/protocol hunk 独立 AAR；禁止“大 A5”回滚单元 |
| E6 | SMB、代理、缓存与预载 | bounded cache、取消/范围正确性 | A6-1/2 **候选**；并行预载 **实验**；manager **拒绝** | 通用网络策略随 App 验收 | Media3 cache/upstream AAR + App；不替换 `PreCache` |
| E7 | ISO/UDF/DVD/Blu-ray/SACD | reader safety → multi-extent → DVD → ISO 生命周期 | 基础 correctness **候选**；DV7/DSD **暂缓** | C3 必须随 multi-extent | Media3 AAR + App resolver；不触碰 MPV native |
| E8 | metadata、轨道名、运行时能力和弹幕 | runtime capability、窄 UI 修复与策略回归 | capability/track-name **候选**；大 UI 重写 **跳过** | 可搭载 C7 数据契约，但不属于 C0--C3 | Media3 AAR + App UI；保留现有诊断/弹幕体系 |
| E9 | renderer/格式扩展架构实验 | 第二套 FFmpeg renderer、RM/ASF/MMT/MPEG-H/mpvplayer | **默认暂缓，单独 RFC/原型** | C1 只做需求探测 | 实验分支，不进入首轮正式 AAR |

E0 是所有代码阶段的共同前置；E1 建议作为第一份新二进制单独发布。E2--E4 是首轮核心播放语义，按顺序处理；E5--E8 互相没有全局强依赖，可由样片和产品优先级决定是否跳过某批。E9 不阻塞前八批。

### 41.3 E0：基线冻结、重复项和验收证据

E0 不产生播放器代码。实施前应固定以下可复现输入：

- Media3 fork `e3e922d5c01bc0b564849940fe589daf37360d15`、当前 `third_party/media-lock.json`、本地 Maven 版本和所有本地 patch；
- nextlib FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、AV3A 与软解负载 patch；
- `media3-dolby-vision-matroska.patch`、`DolbyVisionP81ExtractorsFactory`、WebSocket 弹幕、`PreCache`、ISO 双播放器入口和当前诊断 UI；
- A1--A8 所列最小样片、单元测试、截图、cue dump、decoder/renderer 选择日志和当前 APK 行为。

所有“已覆盖/不重复合并”提交仍在 41.12 的 82 项归属表中登记。迁移执行时，E0 的交付物是一个 commit/patch 迁移清单和测试基线清单；若某个已覆盖行为无法在当前发布 AAR 中重现，应停止对应功能批次，而不是先用上游整提交覆盖。

### 41.4 E1：Exo nextlib 的 FFmpeg 9.0.1 安全基线

#### 来源和动作

- 当前：FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`。
- 目标：FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`。
- 完整 49 项父链 commit 已逐项记录在检查点 6.2；E1 启用其中 #1--48 的安全、性能和既有功能重落基结果。终点提交 `177f090e0503b7e013922ca903bde14b1c375f18` 的 `dovi_rpu convert=p81` 属于 C2：源码可随 revision 存在，但 Exo 不新增调用、不切换当前 P8.1 实现，也不把它作为 E1 验收成功条件。

**建议：优先实施。** E1 的目的只是把现有 nextlib 生产链更新到 FFmpeg 9.0.1 安全基线，不采用 Media3 #71/#72 的第二套 native SDK 或 renderer。

#### 必须保留的本地实现

- `nextlib-av3a.patch`、`libarcdav3a` 静态接线、超过 8 声道处理和 App AV3A 路由；
- `nextlib-ffmpeg-soft-load-shedding.patch` 及当前 normal/non-reference/aggressive 产品行为；
- Exo 每 ABI 唯一一套 `libav*`/`libsw*` 生产者、NDK r28c 和当前 AAR/JNI 名称；
- MPV 的 `libmv*`/`libmw*` 改名不进入本批，MPV NDK r29 和 native lock 完全不变；
- 当前 FFmpeg 裁剪配置、license/provenance、ABI/symbol/哈希校验。

#### 构建、验收和回滚

只构建两 ABI 的 nextlib AAR 和对应 App APK。验收至少覆盖 AV3A、AAC/E-AC3/TrueHD/DTS、H.264/HEVC 软件解码、软解降载、TS/HLS/DASH/MP4/MKV、seek/EOF、32 位 swscale/HEVC 和畸形输入；确认 APK 中没有第二份同名 `libav*`，也没有 `libmv*` 混入 Exo AAR。

E1 使用独立 nextlib 版本号。失败时恢复旧 nextlib AAR 即可，不回滚 Media3 fork、不修改 MPV assets。通过后，后续 E2--E9 都以该 nextlib 为默认 Exo native 基线；MPV 是否采用同一 FFmpeg 源 revision 留到 B 阶段另行决策。

### 41.5 E2：Dolby Vision/HDR 的三步实施链

#### 完整来源 commit

- `b63139c6432caa3f058e7f0496f0d754aa0eaa93`：HLS/TS DV，fork 已精确覆盖；
- `f70e4b6f14d9f3b38ef953be80c53184f9c50bed`：HDR minimum mastering luminance 单位修复；
- `249774647b026e16b56467eb5d79479816f79f11`：TS DV descriptor，fork 已语义覆盖；
- `0cefd3ceec27444cf8faf02486b472bab39109fe`：解析防护、DV CSD/compatible BL、output policy/tone-map，必须拆 hunk；
- `08c664eb8a213a956ff2c8b3d0fcea49902a81fa`：H.265 config parsing，fork 已精确覆盖。

| 子阶段 | 代码动作 | 建议 | 构建/回滚 |
| --- | --- | --- | --- |
| E2-0 覆盖登记 | `b63139c6432caa3f058e7f0496f0d754aa0eaa93`、`249774647b026e16b56467eb5d79479816f79f11`、`08c664eb8a213a956ff2c8b3d0fcea49902a81fa` 对应功能只登记 | 无代码 | E0 清单 |
| E2-1 HDR/parser safety | 移植 `f70e4b6f14d9f3b38ef953be80c53184f9c50bed`，并从 `0cefd3ceec27444cf8faf02486b472bab39109fe` 只取短 DV config、major version 和 MP4 box 边界 | **建议合并** | 独立 Media3 AAR；失败只回滚 parser backport |
| E2-2 DV CSD/compatible BL | 从 `0cefd3ce...` 移植 CSD 保存/读取、BL compatibility 和 MP4/MKV 传播；先修 P7→P8.1 `csd-2` | **条件合并** | 独立 AAR + App transformer；厂商 codec 回归失败可保留 E2-1 |
| E2-3 output policy/tone-map | 适配 `0cefd3ce...` 的 display policy、Profile 5 codec tone-map request 与 adapter 验证 | **用户决策，禁止整提交** | Media3 AAR + App renderer/state；与 E2-2 分开回滚 |

> 注：E2-0 表中 fork 对应 hash `b3c7c816de39335ae8ff744ece3f44707e2907f3` 不是新的上游来源；完整上游 commit 仍为 `08c664eb8a213a956ff2c8b3d0fcea49902a81fa`。保留该对应关系是为了迁移时避免重复应用。

#### 必须保留的本地实现

- `DolbyVisionP81ExtractorsFactory` 的 libdovi sample 转换、加密禁用、会话锁定和失败中止；
- `media3-dolby-vision-matroska.patch` 的 BlockAdditional RPU 追加与 seek/reset；
- App 的“原生/P8.1/HDR10”用户策略、renderer 优先级和 `ExoDolbyVisionPlaybackState` 实际路径诊断；
- Profile 7 不得被上游 AUTO 自动绕过用户选择；Profile 5 不再无条件冒充 HDR10；
- TS、MP4、MKV 的 codec string、DV CSD 和逐 sample RPU 必须一致，不能出现 P8.1 codec string + P7 CSD。

E2 可与 C1 共用 DV5、DV7 MEL/FEL、P8.1、HDR10 样片和元数据期望，但不要求再次修改 FFmpeg。C2 `177f090...` 的 `dovi_rpu convert=p81` 继续保持无调用；不能用它替代 E2-2/E2-3。

### 41.6 E3：DTS、E-AC3、TrueHD/Atmos 音频链

#### 完整来源 commit

- `1066f642a64434e7c3c0be687d3e94a4ca2815d7`；
- `98d7e9518169f187ad2915f20fa46f76ba256fc6`；
- `eb4aa3e445c1df1f6a58eb9e8896e2f4e1998486`；
- `908b27d736ed1c60d237654debc042b61363d081`；
- `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4`；
- `d9ffc31a50fc2377a6b2c91eb3579c4b8e9eab78`；
- `ba3af5240658745bb6383086b8be43438285adc1`；
- `1cc8573cab9e2453e7917aff1b8945482c8b2190`；
- `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6`；
- 关联轨道名 commit `85add599da1230a62715a232ffa8e87d50638a3e` 只在 E3-2b 搭载 TrueHD Atmos 常量/显示 hunk，其余部分归 E8。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E3-0 已覆盖主体 | `98d7e951...`、`eb4aa3e...`、`908b27d7...`、`d9ffc31a...`、`ba3af524...`，以及 `1066f642...`/`d500eb27...` 的主体 | 不重复合并 | 保留 DTS/DTS-HD/DTS:X/AAC 现有回归 |
| E3-1a Pixel JOC guard | 从 `1066f642a64434e7c3c0be687d3e94a4ca2815d7` 只取 Google/Pixel E-AC3 JOC fallback guard 和测试 | **建议合并** | 验证平台 decoder 拒绝后 `CompatFfmpegAudioRenderer` 可接管；独立 AAR 回滚 |
| E3-1b DTS 14-bit | 从 `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` 只取 14-bit frame-size 公式与测试 | **建议合并** | WAV/raw DTS/TS 连续帧；只回滚 `DtsUtil` hunk |
| E3-2a E-AC3 | 从 `1cc8573cab9e2453e7917aff1b8945482c8b2190` 取 dependent substream、channel map、sample count、JOC | **条件合并** | passthrough/offload/PCM、encoded clock、A/V sync；parser 单独回滚 |
| E3-2b TrueHD | 同 commit 的 header/channel map、rechunker、EOF/seek、加密边界；同时迁移 `85add599...` 的 Atmos 命名 | **条件合并** | MKV/MP4/fMP4/M2TS、5.1/7.1/Atmos；MIME/命名与 parser 同批 |
| E3-2c 容器接线 | 同 commit 的 Matroska/MP4/fMP4 waiting-format 生命周期 | **条件合并** | 必须手工适配本地 Matroska DV patch；容器 hunk独立回滚 |
| E3-3 generic TS `0x82` | `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` 的 probe、flag/API 清理 | **有样片才合并** | generic TS 与 HDMV DTS 分流、64 KiB/4-frame 延迟；App factory 独立回滚 |

#### 必须保留的本地实现与产物边界

保留 fork 的多 alternative MIME、DTS-HD MA/coreless、DTS:X/IMAX、独立 DTS extractor、DTS-CD WAV、AV3A，以及 App 的 `CompatFfmpegAudioRenderer`、passthrough/offload/AudioTrack 诊断。E3-2c 修改 Matroska 时必须保留 Dolby Vision RPU 状态和 seek reset。

E3 只构建 Media3 AAR 和 App APK，不更新 nextlib/MPV FFmpeg。E3-1a、E3-1b、E3-2a、E3-2b、E3-2c、E3-3 必须各有可定位版本；不得把 `1cc8573...` 整体变成一个无法区分 parser、时钟和容器回归的大提交。

### 41.7 E4：字幕字节、Cue 数据契约与联合渲染

#### 完整来源 commit

- `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37`；
- `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b`；
- `6794d75b7a39db42dcfcab18c915f0da165515b5`；
- `ccc11523d57c3fd430c009b228c674a3195c9fdc`；
- `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528`；
- `1b112bd1375c7a796cbde58d4c90226c7fc1947a`；
- `e8573d8c2ced07096c368d7ec3a40bc2e790d203`；
- `ba27f889922a281162864a1260e7cb4e73ca0ecf`；
- `7feb08018a6e159330293de4878ebc3c9df2ca86`；
- `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E4-0 基线/覆盖 | `ccc11523...`、`e8573d8c...`、`7feb0801...` 已覆盖；冻结 ASS/TTML/PGS/DVB 截图与 cue dump | 无代码 | 若基线不可复现，停止后续 E4 |
| E4-1 字节安全 | 从 `d82fb7b9...` 取有效 length/bytesRead、文本 MIME 白名单和非 UTF TTML declaration；safe factory/extraction 灰度后置 | **已实施 1a/1b**：`9018f2b5c2b132644cde3841f33fe306209d2499`，tag `recovery/E4-1/20260827074736-9018f2b5c2b1` | decoder/extractor AAR 独立回滚；bitmap raw path 不变；1c/1d 仍待独立批准 |
| E4-J1 Cue 契约 | `6794d75b...` + `3c2cbe8a...` 的 `collisionAvoidance`、`textRegionHeight`、LetterSpacingSpan、Bundle/equals/hashCode | **建议先建数据契约** | common/UI 数据模型独立回滚，默认不启用碰撞 |
| E4-J2 viewport | `92b1570a...`、`6794d75b...`、`3c2cbe8a...`、`aaddc2b9...` 的 viewport/region/bitmap scale 适配 | **截图后条件合并** | PlayerView/SubtitleView UI 回滚，不回滚 parser 字段 |
| E4-2 ASS | `6794d75b...` 的 layer/collision/margin；关闭或 feature-gate fork `applyStacking()` | **条件合并** | Normal/Reverse、绝对定位、留黑边、字号；禁止双 stacking |
| E4-3 PGS | `aaddc2b9...` parser/crop/bounds，`1b112bd1...` TS reader，`ba27f889...` 生命周期 | parser 安全 **候选**；TS/生命周期按样片 | parser、TS reader、View lifecycle 三个回滚单元 |
| E4-4 TTML | `3c2cbe8a...` 的 extent、font/letter spacing、region 输出和 Canvas 适配 | **条件合并，重点候选** | common → viewport → parser/Canvas；WebView/Format pixel fallback 后置 |

#### 必须保留的本地实现与产物边界

- `parseSubtitlesDuringExtraction=false`、`textTrackTranscodingEnabled=false` 继续作为默认，除非 E4-1c/1d 单独灰度；
- 当前 WebHome/电视字幕字号、Canvas/WebView 路由、`bitmapHeight` 语义、fork SSA stacking 和弹幕层级不能被整文件覆盖；
- 连续 PGS display set 保留 100 ms 防闪烁，但 `setPlayer(null)`、detach、release、换源必须立即清理；
- ASS collision 只移动允许移动的文本 cue，位图、绝对定位和 DanmakuView 不参与。

E4 构建 Media3 common/extractor/ui AAR 和 App APK，不改 FFmpeg/MPV。只有 E4-J3 的手机/电视、16:9/21:9、ASS+TTML+PGS/DVB 混排截图与生命周期矩阵通过，才能把整批标为可发布；失败按 E4-1/J1/J2/2/3/4 的最小边界回滚。

### 41.8 E5：流媒体、容器、seek 与加密输入

#### 完整来源 commit

- `b11a22289694611da2450688d9b6407ba75625bc`、`0957524dacb0caca8d24819619b9235487f27d4a`、`7c725b22f0b102e1447dd03dec557cc845db5049`；
- `7709a03d55c6eaaf999c18f0d4ab9fc9141b7ead`、`2d4ab61e69c74796f529bf8f9cab60c68b340d4d`、`eb51dfd700290c5b585026d2fa43a7241dd7b734`；
- `db8f68c8d8990d84b68cca3bcbc0538e10744a14`、`9b535ed30b9fa7e8580264036de1a12115daba32`、`624167c2a0eaf9af94011e0a556aaf91a15fb25f`、`e25ef9864fce33f0d149820bd7999b30aff1a44d`、`938f9958a0756554f8d641315ce626b67efe2143`；
- `65ee9ba81815e67c9d3d08a2be0028859cc20569`、`d160d770887785e3007ff2f1efa50160c2096152`、`f0eb7b514d5fcaba843dfe93d92acfff19a14e9e`；
- `13fbfd88d312de6c4f10fedd2b085cb2710b88ae`、`a1e190005981febfa27e7583e5902d3cc2ce4ef7`；
- `39fde6f3b29cc5f69164a05fc89d5575b843371b`、`444971729731edc184f2fb9f1afee2cc03e44b0f`、`061d90a1e59639594bad5ffceae0ce7fbeba005f`；
- `a40e39880378c9129fbfb86601e7e69e0e48a946`、`a2fe56e7c9a40c894d465d47a424f4c07d1eb50a`、`aac6ec964681dd0476a33e3ad220ca7b5bf771f6`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E5-0 已覆盖稳定性 | MPEG-1 PS `b11a222...`、MP4 edit/EOS `0957524...`/`7c725b2...`、OkHttp `7709a03...`、TS sync `eb51dfd...`、FLV `db8f68c...`/`9b535ed...`、Matroska `624167c...`/`e25ef98...`/`938f995...`、file URI `65ee9ba...`、M2TS `a40e398...`、RTSP `a2fe56e...` | 不重复合并，只补回归 | 各 extractor/protocol 的现有实现保持独立；不发布新 AAR |
| E5-1 content type/DASH | `2d4ab61e...` 只保留真实 `/m3u8`/`/mpd` 无扩展名末段候选；`d160d770...` 拆 TrackGroup fallback 和 duration parser，随机 KID 不采用 | 低风险窄项/样片后 **条件合并** | URL、动态 MPD、重复 id、timescale/溢出；每个 hunk独立回滚 |
| E5-2 HLS edge cases | `f0eb7b51...` 的 AES key、codec/PID/CEA 行为以当前 fork 为基线做迁移保持和回归；“所有 IOException 当 gap”不得作为新合并理由，并应单独评估是否收窄 | 已覆盖项保留；策略项 **产品决策** | 代理/cache、404/403/断网/解密、字幕和 variant；禁止整提交覆盖 |
| E5-3 SAMPLE-AES | `a1e19000...` 先做严格 16-byte key/IV、数组复制、MAIN part 与 unsupported MIME/error；AC3/E-AC3/JOC 只在样片存在时移植 | 防护 **候选**；音频扩展 **条件合并** | key rotation、seek、variant、直链/Cache/MPV proxy；output/mapping 分开回滚 |
| E5-4 H.264 AU/recovery | `aac6ec9...` 按 PPS → recovery/keyframe → 自动 AU/flags 三层实施 | **高价值但样片后** | 保留旧 flag 兼容和本地 PUSI/EOF/4-byte start code；三层独立回滚 |
| E5-5 已覆盖通用功能 | HLS 广告 `13fbfd88...`、ClearKey `39fde6f3...`/`44497172...`/`061d90a1...` 已有本地对应 | 不重复合并 | 广告过滤做 Exo/MPV 共用验收；DRM 需产品样片，不进最小集合 |

#### 必须保留的本地实现与产物边界

- `MpvHlsProxy`、App M3U8 重写、Range/header/cookie 传播、CacheDataSource、`HttpEofRecoveryDataSource`、错误 telemetry 和重试分类；
- SAMPLE-AES 现有 H.264/AAC 路径、相对 key URI、默认 IV、key rotation 和本地 TS reader 增强；
- synthesized PUSI/EOF、4-byte start code、M2TS/HDMV/DV、SAMPLE-AES stream type 与 seek 状态；
- HLS 网络/认证/解密错误不能被新增代码静默伪装成普通 gap；
- ClearKey 和 chapter/edition 等已存在 API 不因容器 hunk 被整文件覆盖。

E5 只产出 Media3 AAR 与 App APK。`d160d770...`、`f0eb7b51...`、`a1e19000...`、`aac6ec9...` 必须按子 hunk 版本化；任何一项失败都不能要求回滚其它已通过协议。C1 的 HLS live/timestamp、广告和 MMT/TLV 只共用样片/语义矩阵，不把 FFmpeg 或 MPV 二进制混入 E5。

### 41.9 E6：SMB、代理、缓存写入与预加载

#### 完整来源 commit

- `32c20a091ba6e5fd09e13e67df3149326232eda5`；
- `dd00f94b58b7324ab29febb0b50f3a190d544a3b`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E6-0 SMB/proxy | `32c20a091ba6e5fd09e13e67df3149326232eda5` 主体已覆盖；只按实际代理 API 评估反射返回值 `Number`/窄异常处理 | 不重复合并；窄健壮性候选 | 凭据切换、Unicode、seek、重连、并发、close/reopen；只回滚 datasource hunk |
| E6-1 bounded cache | 从 `dd00f94b58b7324ab29febb0b50f3a190d544a3b` 取 `CacheWriter` long 计数、请求长度和 end-position 修复 | **本批首选，建议合并** | 206/200/416、已知/未知长度、提前 EOF、取消；独立 cache AAR 回滚 |
| E6-2 downloader correctness | 同 commit 的 factory time-range reset 和 preparation cancel guard，拆为两个提交 | **条件合并** | factory 复用与 prepare/cancel/release race；各自回滚 |
| E6-3 progressive parallel | 同 commit 的分段并行、进度、取消和 retry | **默认 1，feature flag 实验** | Range/代理/cache lock/fd/线程/thermal/播放抢占；只回滚并发层 |
| E6-4 preload manager | 上游 `DiskPreloadManager` 只作 overlap/restart 参考 | **不合入** | 不产生迁移代码 |

#### 必须保留的本地实现与产物边界

必须保留 App `PreCache` 的 first-frame/recovery gate、route-aware 并发限制、外部代理 circuit breaker、缓存水位、storage/memory/network/power/thermal、telemetry、quota 和 `PriorityTaskDataSource`。priority manager 只能有一层所有权，不能同时由 helper 与 wrapper 重复 add/remove。

E6 只构建 Media3 cache/upstream AAR 和 App APK；不改变 nextlib/MPV。E6-1、两个 E6-2 修复和 E6-3 必须分别版本化。即使 E6-3 失败，也应保留已通过的 E6-1/E6-2；任何情况下不以 `DiskPreloadManager` 替换当前 `PreCache`。

### 41.10 E7：ISO/UDF、DVD、Blu-ray、SACD 与 C3

#### 完整来源 commit

- `990abc2368fd74779f525ee345734470659f3d53`；
- `5bca32949e0ad82cb0105962a7ae31234d6cd1a8`；
- `b3a78a2f7a9353359a02efe61e94038238c04fa1`；
- `15d8d21f3354e6da48c5a47751a3edb943f9ffc6`；
- `4d713dded8f59cac265ec612dc263b1287bb08b4`；
- `bd3b52102a1dad1ef9d168165d0e8959fca5d03f`；
- `9a8c256cf14fdfce353dee039f6dd861185d7bfe`；
- `6cf9aae1e4132d6a8978e53e78f57234951cfd65`；
- `93af478b4cd2126c3844aaf2f813e24c0262eaf7`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E7-0 覆盖登记 | HDMV `b3a78a2...`、disc file types `6cf9aae...`、Blu-ray playlist 主体 `4d713dd...` 已有 | 不重复合并 | LPCM/VC-1/DTS、MIME/URI、playlist 连续时间轴回归 |
| E7-1 reader safety | 从 `990abc2368fd74779f525ee345734470659f3d53` 取 0 长度、prefetch 上限、open/close/EOF | **低风险优先候选** | `IsoDataReader` 单独 AAR 回滚 |
| E7-2 multi-extent + C3 | 同 commit 的 `IsoFileEntry`/UDF/AED/virtual datasource，并同步 WebHTV `IsoTrackMetadataResolver` 跨 extent | **完整镜像矩阵后联合合并** | raw → M2TS/SACD strip → Range；Media3 与 App resolver 分开提交、同批发布 |
| E7-3 DVD correctness | `15d8d21...` 的 ID/header/映射/EOF + `bd3b521...` 的 PTT/start program/control bits/table bounds/STC/reader isolation | **高价值条件候选** | 先 parser/逻辑映射，再 extent/cell/EOF；禁止整文件覆盖 `PsExtractor` |
| E7-4 ISO lifecycle/wrapper | `93af478...` 拆 cancel/close-once、load policy、subtitle/clipping/ads 和父镜像 cache identity | lifecycle/wrapper **建议**；load policy **条件** | A6-14a/14b/14c 三个回滚单元 |
| E7-5 SACD/DSF/DFF correctness | `9a8c256...` 的 TOC 冗余/TRL/fallback；`5bca329...` 的 DSF tail 与 DST frame timeline | **有样片的 parser 候选** | 只声明 extractor correctness；decoder/AudioTrack 闭环未完成 |
| E7-6 Blu-ray DV7 combine | `4d713dd...` 的 dependency PID/PTS 配对和 BL+EL 合并 | **高风险，默认不启用** | 先 dry-run、内存上限、原生/P8.1/HDR10 策略；独立功能开关和回滚 |

#### 必须保留的本地实现与产物边界

- App 已有 Exo/MPV 两条 ISO 入口；保留 fork 的 `cumulativeOffsetUs`、当前章节/edition/语言接线和 MPV 光盘控制；
- C3 必须与 E7-2 同批：MPV 的 `IsoTrackMetadataResolver` 不能继续假定 MPLS/CLPI 单 extent；
- DVD 不采用“所有 cell 小于 1 秒就整组丢弃”的无条件规则；保留可诊断的合法短标题/菜单行为；
- Blu-ray DV7 combine 不得绕过 App 的原生 DV7/P8.1/HDR10 决策；当前 ISO 内部 extractor 绕过 P8.1 wrapper 的缺口必须先解决；
- `DsdExtractor`/DSF/DFF/SACD parser 存在不等于 DSD/DST 可播放，未完成 nextlib decoder/renderer/audio sink 前保持实验标记。

E7 构建 Media3 AAR 与 App APK；E7-2 同时修改 App 通用 resolver，但不更新 MPV native。回滚顺序按 reader、UDF/datasource、DVD parser、DVD lifecycle、ISO lifecycle/policy/wrapper、SACD/DSF/DFF、DV7 combine 分开；不得把整个光盘栈做成一个 AAR 黑盒。

### 41.11 E8：metadata、轨道名、运行时能力、诊断与现有 UI

#### 完整来源 commit

- `db13d7672f9bca525878292a54ae5e69c021f4c9`；
- `bfd703abe3be1800b63119e5f6fc85154ec94f9d`；
- `87e982f7b38bf6a24a2b3c148bdd23f476bec29c`；
- `3216effea715a906ce9dd02ed50b46afe7f14ad4`；
- `f17757b05432e83f7c88c9f2a51377baaf10a227`；
- `1e064c30588bde89bf26798d10f071c40fd8da29`；
- `c85d124102c5b25a1bcd270d78f78603e87a6214`；
- `7b787fe2a5616e684d9c0b77b8481724ada4afae`；
- `85add599da1230a62715a232ffa8e87d50638a3e`；
- `845f6fddd3953c36b08c2a878301649f918a1911`；
- `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486`；
- `0f6191bc1bdd7324eef5e512cada65d9b974a6ed`；
- `ab1bfd8779a4c9112d2a7ad61725f61668dfda85`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| E8-0 已覆盖 API/UI | audio/text offsets `db13d76...`、chapter/edition `f17757b...`、artwork `7b787fe...`、track-name 主体 `85add599...` 已存在；PlayerView resize/chapter UI `bfd703a...`/`87e982f...`/`3216eff...`/`1e064c3...`/`c85d124...` 无当前产品缺口 | 不重复合并；按需补消费方测试 | 每项只回滚 App UI/adapter，不改底层播放 |
| E8-1 artwork policy | 基于 `7b787fe...` 现状决定 URI 与 embedded bytes 优先级、压缩数据上限和多封面选择 | **策略/测试，不合并 commit** | notification、MediaSession、内存、切轨；App/metadata policy 独立回滚 |
| E8-2 track-name cleanup | 从 `85add599...` 取 `APPLICATION_MEDIA3_CUES`、unknown fallback；删除不可靠语言别名，修正 59.94/23.976 显示；TrueHD 名称已归 E3-2b | **建议窄修复** | `TrackDialog` 音视频字幕矩阵；formatter 独立回滚 |
| E8-3 danmaku safety | `845f6f...` 只取有测试证明的 IQIYI varint/未知字段、Youku cookie/HTTP status、provider URL 边界 | **条件合并** | 每个 parser/fetcher 独立提交；MockWebServer/截断输入/代理 |
| E8-4 danmaku architecture | 同 commit 的 timeline/segment/render-pool 只做 shadow benchmark | **默认不合并** | 2 小时/10 万条、seek、低端电视、GC/帧耗时；不改变默认行为 |
| E8-5 runtime capability | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` 的 audio processing、skip-silence、video effects 动态支持 API与原因 | **中优先级建议条件合并** | tunneling/DRM/renderer/audio-output/adaptive transition；AAR API + App adapter 回滚 |
| E8-6 runtime facts/formatter | 从 `c3b25d5...` 取稳定 decoder/output/AudioTrack facts和纯 formatter；`0f6191...` 的第二套 overlay UI 不采用 | facts/formatter **窄候选**；overlay **跳过** | 接入当前 `PlaybackFactsSnapshot`/`PlayerOsdController`，不引入 reflection overlay |

#### 必须保留的本地实现与产物边界

- 当前 `PlayerManager` metadata、`TrackDialog`、`CodecCapabilityDialog`、`PlaybackFactsSnapshot`、中文 `PlayerOsdController` 和按需采样；
- App 的静态 + WebSocket 弹幕双链、generation/TTL/优先级/队列上限、停止/重连清空、代理、诊断和 `DanmakuViewLiveLoadTest`；
- runtime capability 必须表示当前选中/已初始化路径，不能与预播放系统 codec list 混为同一 API，也不能继续用 `supportsVideoEffects() == true` 的静态假设；
- chapter/timebar/resize/effects UI 没有明确产品需求时，不因上游已有组件改变现有布局和电视焦点。

E8 只构建 Media3 AAR 与 App UI。E8-2、E8-3、E8-5、E8-6 分开版本化；E8-4 只存在于 benchmark 分支。任何 E8 变更都不引入第二个 overlay、第二套弹幕控制器或 MPV native 资产。

### 41.12 E9：renderer、二进制 SDK 与新格式的独立架构线

#### 完整来源 commit

- `0417078bfbac37b5012991d696ab8a4803cb2805`；
- `d7083781e629ad1c4683a687261374065fb38925`；
- `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c`；
- `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b`；
- `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97`；
- `ca7dd917ad574d4241640eb9282f20c5decd5aea`；
- `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910`；
- `176e7f58ec3ba82cce3f5071b0a2625890e93b2d`；
- `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8`；
- `7cca3b0bb5cbdccea639e602e713301d8116a99f`；
- `12670ce4fb23ad32ed3875d0250486eabe957913`；
- `ccf962e8912695dc60ce82aa4470df899c6306a3`。

| 子阶段 | 代码动作 | 当前建议 | 启动门槛/回滚 |
| --- | --- | --- | --- |
| E9-0 已有 AV3A | `d7083781e629ad1c4683a687261374065fb38925` 与 FFmpeg `23484688ad6ddda545f2380657c85ab1969d4b76` 已由 fork/nextlib 覆盖 | 不重复合并 | 只随 E1/E5 做 TS 与 decoder 回归 |
| E9-1 MPEG-H binary | `0417078bfbac37b5012991d696ab8a4803cb2805` 的预编译 JNI/资产 | 暂缓 | 需来源、license、ABI/符号/哈希、真实 MPEG-H 样片和唯一 asset producer |
| E9-2 RM/ASF | `4c3aa7...`、`0fa9a1...` 的窄 packet/parser 修复可保留设计；当前 nextlib 未启用 Cook/SIPR/RV/WMA/WMV/VC-1 闭环 | 默认暂缓 | 先决定 decoder/体积/license；若做，extractor correctness 与 decoder AAR 分开回滚 |
| E9-3 Media3 FFmpeg video | #70--73：decode API、native SDK、软件视频/GLES-libplacebo、mapping selector | 只评估 #72 的通用 `DecoderVideoRenderer` correctness；整体架构暂缓 | 必须先保证每 ABI 唯一 Exo `libav*`、保留 AV3A、A/B recovery；独立实验分支 |
| E9-4 Media3 mpvplayer | `ffbbd97...`/`7cca3b0...` 的完整第二 MPV 模块、ELF、DV/OSD bridge | **不合入正式 Exo** | 当前 App MPV adapter/native 是唯一生产者；可提取 display DV/effect 原因到后续 B/C 阶段 |
| E9-5 MMT/TLV | `ccf962e8912695dc60ce82aa4470df899c6306a3` 的 3836 行 Java extractor | 无样片时暂缓 | 先用当前 FFmpeg/MPV 做 MMT-0 需求探测，再 MIME/sniff → signaling/timestamp → metadata/subtitle 分层 |
| E9-6 publication helper | `12670ce4fb23ad32ed3875d0250486eabe957913` 的机器相关 `move.bat`/裸 AAR 复制 | 明确跳过 | 如需一键入口，只改现有 lock/staging/Maven 脚本；不形成播放器回滚单元 |

E9 的共同特点是会新增一套资产生产者、数千行状态机或当前未闭环的格式能力。它们不得与 E1--E8 的 correctness 合并同批发布，也不能阻塞首轮 Exo 决策。若用户批准其中某项，应先单独建立 RFC、source/ABI/license/size 预算、真实样片与回退方案，再进入实验 AAR；默认不写入正式 lock。

### 41.13 `media` #1--82 唯一归属校验

下表给每个上游提交一个主要归属。一个 commit 可在另一个批次被引用为联合验收来源，例如 `85add599...` 的 TrueHD 命名随 E3，但其主要归属仍是 E8；这类有意交叉引用不改变唯一主要归属。

| `media` 编号 | 完整 commit ID | 主要归属 | 当前动作 |
| ---: | --- | --- | --- |
| 1 | `1066f642a64434e7c3c0be687d3e94a4ca2815d7` | E3 | 主体已覆盖；窄取 Pixel JOC guard |
| 2 | `98d7e9518169f187ad2915f20fa46f76ba256fc6` | E3 | 已覆盖 |
| 3 | `eb4aa3e445c1df1f6a58eb9e8896e2f4e1998486` | E3 | 已覆盖 |
| 4 | `b63139c6432caa3f058e7f0496f0d754aa0eaa93` | E2 | 已覆盖 |
| 5 | `f70e4b6f14d9f3b38ef953be80c53184f9c50bed` | E2 | 建议合并 |
| 6 | `249774647b026e16b56467eb5d79479816f79f11` | E2 | 已覆盖 |
| 7 | `0cefd3ceec27444cf8faf02486b472bab39109fe` | E2 | 拆 parser/CSD/output |
| 8 | `908b27d736ed1c60d237654debc042b61363d081` | E3 | 已覆盖 |
| 9 | `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` | E3 | 窄取 14-bit 修复 |
| 10 | `d9ffc31a50fc2377a6b2c91eb3579c4b8e9eab78` | E3 | 已覆盖 |
| 11 | `b11a22289694611da2450688d9b6407ba75625bc` | E5 | 已覆盖，补测试 |
| 12 | `08c664eb8a213a956ff2c8b3d0fcea49902a81fa` | E2 | 已覆盖 |
| 13 | `0957524dacb0caca8d24819619b9235487f27d4a` | E5 | 已覆盖，补测试 |
| 14 | `7c725b22f0b102e1447dd03dec557cc845db5049` | E5 | 已覆盖，补测试 |
| 15 | `0417078bfbac37b5012991d696ab8a4803cb2805` | E9 | MPEG-H 资产暂缓 |
| 16 | `7709a03d55c6eaaf999c18f0d4ab9fc9141b7ead` | E5 | 已覆盖，补联调 |
| 17 | `2d4ab61e69c74796f529bf8f9cab60c68b340d4d` | E5 | 窄候选 |
| 18 | `32c20a091ba6e5fd09e13e67df3149326232eda5` | E6 | 主体已覆盖 |
| 19 | `dd00f94b58b7324ab29febb0b50f3a190d544a3b` | E6 | 拆 correctness/实验 |
| 20 | `eb51dfd700290c5b585026d2fa43a7241dd7b734` | E5 | 已覆盖，补测试 |
| 21 | `d82fb7b9c93fa2ca0331d3ad455f5805aef47d37` | E4 | 字节安全候选 |
| 22 | `92b1570a2f3d4bcf7e28e0d808dd13bf2b70bd9b` | E4 | viewport 条件合并 |
| 23 | `6794d75b7a39db42dcfcab18c915f0da165515b5` | E4 | Cue/ASS 分阶段 |
| 24 | `ccc11523d57c3fd430c009b228c674a3195c9fdc` | E4 | 已覆盖 |
| 25 | `aaddc2b9f6a9b47e82c2c9009bfdecdb0bc27528` | E4 | PGS 条件合并 |
| 26 | `d7083781e629ad1c4683a687261374065fb38925` | E9 | AV3A 已覆盖 |
| 27 | `1b112bd1375c7a796cbde58d4c90226c7fc1947a` | E4 | PGS TS 条件合并 |
| 28 | `e8573d8c2ced07096c368d7ec3a40bc2e790d203` | E4 | 已覆盖 |
| 29 | `ba27f889922a281162864a1260e7cb4e73ca0ecf` | E4 | 生命周期条件合并 |
| 30 | `db8f68c8d8990d84b68cca3bcbc0538e10744a14` | E5 | 已覆盖，补测试 |
| 31 | `9b535ed30b9fa7e8580264036de1a12115daba32` | E5 | 已覆盖，补测试 |
| 32 | `4c3aa7d3293abaaeb0c4de49d73b12241d81d62c` | E9 | RM parser/decoder 门槛 |
| 33 | `0fa9a12f5463822b7f4ad7c045df4a41be7d4e6b` | E9 | ASF parser/decoder 门槛 |
| 34 | `624167c2a0eaf9af94011e0a556aaf91a15fb25f` | E5 | 已覆盖 |
| 35 | `7feb08018a6e159330293de4878ebc3c9df2ca86` | E4 | 已覆盖 |
| 36 | `e25ef9864fce33f0d149820bd7999b30aff1a44d` | E5 | 已覆盖 |
| 37 | `938f9958a0756554f8d641315ce626b67efe2143` | E5 | 已覆盖 |
| 38 | `ba3af5240658745bb6383086b8be43438285adc1` | E3 | 已覆盖 |
| 39 | `1cc8573cab9e2453e7917aff1b8945482c8b2190` | E3 | 三阶段条件合并 |
| 40 | `65ee9ba81815e67c9d3d08a2be0028859cc20569` | E5 | 已覆盖 |
| 41 | `d160d770887785e3007ff2f1efa50160c2096152` | E5 | DASH 拆 hunk |
| 42 | `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e` | E5 | HLS 按策略保持/复核 |
| 43 | `13fbfd88d312de6c4f10fedd2b085cb2710b88ae` | E5 | 已覆盖，共用验收 |
| 44 | `a1e190005981febfa27e7583e5902d3cc2ce4ef7` | E5 | SAMPLE-AES 分阶段 |
| 45 | `39fde6f3b29cc5f69164a05fc89d5575b843371b` | E5 | 已覆盖，需求后验收 |
| 46 | `444971729731edc184f2fb9f1afee2cc03e44b0f` | E5 | 已覆盖，需求后验收 |
| 47 | `061d90a1e59639594bad5ffceae0ce7fbeba005f` | E5 | 已覆盖，需求后验收 |
| 48 | `a40e39880378c9129fbfb86601e7e69e0e48a946` | E5 | 已覆盖，M2TS 回归 |
| 49 | `a2fe56e7c9a40c894d465d47a424f4c07d1eb50a` | E5 | 已覆盖，RTSP 联调 |
| 50 | `db13d7672f9bca525878292a54ae5e69c021f4c9` | E8 | 已覆盖，等待 UI |
| 51 | `bfd703abe3be1800b63119e5f6fc85154ec94f9d` | E8 | 当前 UI 不采用 |
| 52 | `87e982f7b38bf6a24a2b3c148bdd23f476bec29c` | E8 | 当前 UI 不采用 |
| 53 | `3216effea715a906ce9dd02ed50b46afe7f14ad4` | E8 | 当前 UI 不采用 |
| 54 | `f17757b05432e83f7c88c9f2a51377baaf10a227` | E8 | API 已覆盖，等待消费 |
| 55 | `1e064c30588bde89bf26798d10f071c40fd8da29` | E8 | 当前 UI 暂缓 |
| 56 | `c85d124102c5b25a1bcd270d78f78603e87a6214` | E8 | 当前 UI 暂缓 |
| 57 | `990abc2368fd74779f525ee345734470659f3d53` | E7 | reader/multi-extent 分阶段 |
| 58 | `5bca32949e0ad82cb0105962a7ae31234d6cd1a8` | E7 | DSF/DFF correctness |
| 59 | `b3a78a2f7a9353359a02efe61e94038238c04fa1` | E7 | 已覆盖，HDMV 回归 |
| 60 | `c2dd4becf5a8560ac1f26d4d0b4d4c474ca285e6` | E3 | generic TS 条件合并 |
| 61 | `15d8d21f3354e6da48c5a47751a3edb943f9ffc6` | E7 | DVD 窄增量 |
| 62 | `4d713dded8f59cac265ec612dc263b1287bb08b4` | E7 | 主体已覆盖；DV7 独立 |
| 63 | `bd3b52102a1dad1ef9d168165d0e8959fca5d03f` | E7 | DVD correctness |
| 64 | `9a8c256cf14fdfce353dee039f6dd861185d7bfe` | E7 | SACD parser 候选 |
| 65 | `6cf9aae1e4132d6a8978e53e78f57234951cfd65` | E7 | 已覆盖 |
| 66 | `93af478b4cd2126c3844aaf2f813e24c0262eaf7` | E7 | ISO lifecycle/wrapper |
| 67 | `7b787fe2a5616e684d9c0b77b8481724ada4afae` | E8 | 主体已覆盖，策略/测试 |
| 68 | `85add599da1230a62715a232ffa8e87d50638a3e` | E8 | 窄修复；TrueHD 部分随 E3 |
| 69 | `845f6fddd3953c36b08c2a878301649f918a1911` | E8 | parser safety；架构不合并 |
| 70 | `2a2c8e8e122c13c0e462217f8fb5d7f0910cab97` | E9 | 动态选择 API 条件前置 |
| 71 | `ca7dd917ad574d4241640eb9282f20c5decd5aea` | E9 | 第二套 native SDK 不合并 |
| 72 | `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` | E9 | 只取通用 correctness 设计 |
| 73 | `176e7f58ec3ba82cce3f5071b0a2625890e93b2d` | E9 | mapping/switch 实验 |
| 74 | `ffbbd97a4fcdb08a6a2ce9d38a0c7915a77175e8` | E9 | 第二 MPV module 不合并 |
| 75 | `7cca3b0bb5cbdccea639e602e713301d8116a99f` | E9 | DV/effect 原因供 B/C 参考 |
| 76 | `c3b25d5f4d6b4cc66c24b512defd8cd7084d2486` | E8 | facts/formatter 窄候选 |
| 77 | `0f6191bc1bdd7324eef5e512cada65d9b974a6ed` | E8 | overlay 跳过 |
| 78 | `ab1bfd8779a4c9112d2a7ad61725f61668dfda85` | E8 | runtime capability 候选 |
| 79 | `aac6ec964681dd0476a33e3ad220ca7b5bf771f6` | E5 | H.264 分层候选 |
| 80 | `12670ce4fb23ad32ed3875d0250486eabe957913` | E9 | 发布脚本跳过 |
| 81 | `ccf962e8912695dc60ce82aa4470df899c6306a3` | E9 | MMT/TLV 需求后分层 |
| 82 | `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | E4 | TTML 联合条件合并 |

该表覆盖 `media` #1--82 各一次；跨批引用只用于说明同一上游 commit 的不同 hunk 应随哪个功能上线，不表示允许重复 cherry-pick。

### 41.14 供用户首轮决策的最小集合

| 决策 | 子阶段 | 说明 |
| --- | --- | --- |
| 建议先批准 | E1；E2-1；E3-1a；E3-1b；E4-1；E4-J1；E6-1；E7-1 | 都有明确 correctness/安全收益，且可用单一 AAR/nextlib 版本独立回滚 |
| 建议在样片/截图准备后批准 | E2-2；E3-2a/b/c；E4-J2/2/3/4；E5-1/3/4；E6-2；E7-2/3/4/5；E8-2/3/5/6 | 价值存在，但会改变 container、renderer、UI、时钟或生命周期语义 |
| 需要明确产品策略 | E2-3；E3-3；E5 的 CEA/DRM/广告策略；E6-3；E7-6；E8-1/4 | 结果不是纯 bugfix，可能改变用户选择、网络行为、资源占用或输出路径 |
| 当前跳过/独立架构线 | E6-4；E9-1/2/3/4/5/6；E8 overlay | 会引入第二资产生产者、大量新状态机、未闭环格式或重复 UI |

若只追求第一轮低风险增量，建议发布顺序是：**E1 → E2-1 → E3-1a → E3-1b → E4-1/J1 → E6-1 → E7-1**。E4-1 的 1a/1b 已由提交 `9018f2b5c2b132644cde3841f33fe306209d2499` 实施并以 tag `recovery/E4-1/20260827074736-9018f2b5c2b1` 锚定；1c/1d 和 J1 仍是独立阶段。每一步先发布独立测试 AAR/APK并保存样片、日志与哈希，再进入下一步；不要为了减少构建次数把它们压成一个无法归因的 Media3 大版本。

### 41.15 恢复锚点

- Exo 可决策实施总表已完成：E0--E9、完整 commit 归属、必须保留的本地实现、通用项搭载、产物边界、验收和独立回滚均已记录。
- `media` #1--82 已在 41.13 各分配一个主要阶段；没有提交因“已覆盖”而从审计记录消失。
- 第一轮建议顺序已固定为 E1 → E2-1 → E3-1a → E3-1b → E4-1/J1 → E6-1 → E7-1；其余按样片、截图或产品策略决策。
- E4-1 实现提交及恢复 tag 已完成；下一步整理 MPV 可决策实施总表：合并 mpv-android B1--B3、libplacebo B4、mpv B5--B11、FFmpeg C0/C1/C2 和 WebHTV 本地 native patch，形成少量成套 rebuild 批次。

## 检查点 42：2026-08-21 MPV 可决策实施批次总表

本检查点把检查点 5、34--39 已审完的 `mpv-android` 24 项、`libplacebo` 7 项和 `mpv` 27 项收敛为可以逐批批准、构建、验收和回滚的 MPV 实施顺序。逐 hunk 证据仍以前述检查点为准；本表负责回答“目标整树中哪些只是重落基、首次 rebuild 应带哪些真实增量、哪些本地补丁绝不能丢、何时需要重建 JNI”。

### 42.1 总体实施规则

1. **先 Exo、后 MPV。** 检查点 41 中用户批准的 Exo 批次完成 AAR/APK 验收前，不更新 `third_party/mpv-native-lock.json`、MPV assets 或 `libplayer.so`。
2. **MPV 是成套 native 产物。** 采用任一 mpv/libplacebo/FFmpeg 源码变化时，必须以 NDK r29/API 24 为两 ABI重建 `libmpv.so`、FFmpeg `libmv*`/`libmw*`、静态 libplacebo、curl/nghttp2/MbedTLS 和 `libc++_shared.so`，再执行 ELF、版本和 APK asset 校验。不能只替换单个 `.so`。
3. **新 hash 不等于新功能。** 目标三仓库均发生过分叉重落基；等价提交只登记为“由目标整树继承并运行验收”，不重新手工 cherry-pick，也不以其数量夸大缺口。
4. **目标整树不是可直接替换的补丁集。** `mpv` 的 B8 重写会重新打开 Android BL MediaCodec + EL software 路径，并与 WebHTV 的单 Surface、stable flow、DV7 packet safety 冲突；必须按窄 hunk 合成，不能删除本地 patch 后直接指向目标头。
5. **源码同步、功能启用和发布分开。** P1--P4 可在同一候选源码树累计，但每个主题保留独立 commit/patch、样片结果和回滚开关；只有已批准主题进入正式 lock。P5 维护项不会成为 native rebuild 的理由。
6. **JNI 只在需要时重建。** P1--P3 不改变 mpv client API 或 `stream_cb.h`，可保留当前 `libplayer.so`；P4-1 修改 `third_party/mpv-player-jni` 才单独重建 JNI。若 mpv header/API 或光盘 controls 同时变化，则 native 与 JNI 同批验证但仍保留两种产物的独立回滚记录。

### 42.2 建议顺序总览

| 顺序 | 决策批次 | 主要目标 | 当前建议 | 搭载通用项 | 产物与独立回滚边界 |
| ---: | --- | --- | --- | --- | --- |
| P0 | 基线冻结、等价项和运行验收 | 固定 lock、patch 顺序、两 ABI assets、样片与诊断；证明目标树重复项当前可用 | **直接确认，无代码** | C1 按已有输入做基线 | 测试/迁移清单；无新 `.so` |
| P1 | 低风险格式与 shader correctness | packed RGB10、EBML defaults、HLS edition、libplacebo alpha | **首次 MPV rebuild 首选** | 采用已通过 Exo E1 的 FFmpeg C0 源 revision，但另行构建 | 两 ABI完整 native assets；B10-1/2/3、alpha 分别可从候选源码回滚 |
| P2 | Vulkan/AImageReader/DV7 窄增量 | generic UV 预计算、DV7 metadata/codecpar/error 完整性；MEL metadata-only 可选 | P2-1/P2-2 **建议**；MEL **样片后条件** | C1 的 DV metadata 联合验收；C2 保持禁用 | 两 ABI完整 native assets；每个窄 patch 独立回滚，不重开 EL |
| P3 | AudioTrack 高码率直通 | 保留 carrier rate 与旧机 TrueHD 规则，条件扩展 8-channel mask | **先补统一 probe，再条件合并** | 与 Exo E3 共用音频样片，不共用代码 | native hunk + App capability probe 同批；失败恢复当前 TrueHD patch |
| P4 | mpv-android builder/JNI | shutdown 串行化；可选 Harfbuzz 14.3.1 | shutdown **建议窄移植**；Harfbuzz **低优先条件** | 无 | `libplayer.so` 与字体栈分别版本化；不覆盖 WebHTV JNI |
| P5 | 实验和维护跳过项 | Android BL+EL、示例 App、Wayland/CI/注释、libplacebo 内部前置 | 默认暂缓或自然继承 | C2 只保留实验设计 | 不形成正式播放器发布批次 |

P0 是所有代码阶段的共同前置。若 Exo E1 已验证 FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`，P1 建议在同一源码 revision 上重建 MPV，但必须重新应用 MPV 专属 FFmpeg patch、执行 ELF 改名并独立验收。P1 与 P2 可以为了节省一次完整编译而在同一候选构建中搭载，但提交、测试结果和回滚补丁必须分开；P3 涉及 App 能力探测，不能无条件顺带开启；P4 不阻塞 P1--P3。

### 42.3 P0：基线冻结、等价提交和运行验收

P0 不改变源码。实施前固定以下输入和证据：

- `third_party/mpv-native-lock.json` 当前 mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`、Harfbuzz 14.2.1、NDK r29/API 24；
- `scripts/build_mpv_native.sh`、`third_party/mpv-native-overrides/`、当前 12 个 MPV/FFmpeg native patch 和 `scripts/verify_mpv_native_assets.sh` 的哈希/字符串/ELF 基线；
- 两 ABI当前 assets、`libplayer.so`、App option/diagnostic strings、OpenGL/Vulkan/Surface Direct、音频直通、HLS、ISO、seek/EOF 和生命周期样片结果。

目标 mpv 树中下列 16 项以及目标 libplacebo 树中的 5 项，当前锁定树已有精确或最终语义等价实现，不产生新的移植代码：

| 功能组 | 完整目标 commit | P0 动作 |
| --- | --- | --- |
| 输入、网络和光盘 | `70510dee41c41da19d71b952f1b05a6463d8d0d6`, `4d0423281f850b788e52b64def4b26a1505f6140`, `00402d0696d734783eb5efe1c23f4e3bda4bc3f8`, `82790bf10e8f67c4c9afa18d790ca98303276a60`, `a088b8b9a1c5c3e2520145d69e5543a1a87a5cf7` | helper scheme、ISO、linear rewind、curl Range/worker 做运行验收；保留 `stream_cb` controls 和代理 Range patch |
| 通用媒体语义 | `32c4d5adad29107756ae2987d69d92844bfed243`, `78617f20a4b449addbe8ac40e7e0791b2aab1c5b`, `31a5e3bdf76c2f2918a8992f4a9614ab76070af7`, `c2bc880511fd20850c586f2dc25aff770723b6b4` | MMT/TLV、artwork、live、TTML 只按真实输入验收 |
| 播放与 Android GPU 主体 | `72e86486a5dd3a00950a9e5dfa3e381d2e00d230`, `47bb36190de83db31224d7193bd8514fabefb314`, `793d89800a425cda856065307c9027997ebf1c9c`, `a810f8e4f3c5cfde42367eace6d9015f95b99cd6`, `43b378853776dfd734d21d9649b2053eefcb39f5`, `c7fef70644b3d506340e113689a5923f324c861d`, `1c2d989b6b246c36869fff9ec8297c9897e1d964` | 0.1x、decoder reinit、HDR negotiation、backend/compositable/DV5/ASS 主体已覆盖；迁移目标整树时重跑 P0 矩阵 |
| libplacebo API 375 重落基 | `f5bdd194e700a002de441a350bbed385ec7ca30b`, `1797af1ce61d13e998ff4397b017422dd1e0c53c`, `373cd8be1e5f6c4e7a2c565766d23016be2bce3c`, `2a1101a2a466944a9d70c64991bc1983cfbe1cd0`, `2301953d9faf0f5e112ff337f79cec64eab2f4f1` | YCbCr wrap、HDR/DV checks、`disable_storage`、raw YUV 已在锁定 API 375，不以“升级 API”为重建理由 |

`mpv-android` 的构建框架、依赖接线和 JNI 主体等价项在 42.9 唯一归属表中逐项登记。P0 的退出条件不是“所有目标 hash 都进入当前历史”，而是对应能力在锁定产物上可复现，且目标整树迁移时本地 patch 仍能逐项重落基。

### 42.4 P1：B10 格式修复与 libplacebo alpha 的首次受控 rebuild

#### 完整来源 commit

- mpv packed RGB10：`7b8915bc1d04c7e1b61184e00c7fbfaab1911e75`；
- Matroska 生成器前置与 EBML defaults：`52bb166f309c8bb55ab34b2b0bc5c8ead05370e4`, `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8`；
- HLS program/edition 选择：`e7191f2a65d64af266c5c80793e79d2f4b92b789`；
- libplacebo alpha preservation：`22ee762e8e0890fc54068beb670310f0edce7263`。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| P1-1 packed RGB10 | 合入 `7b8915...` 的 `X2BGR10`/`X2RGB10` special RA identity | **建议合并** | Vulkan direct/stable/generic、红蓝通道、10-bit gradient、HDR/LUT；只回滚 RA hunk |
| P1-2 EBML defaults | `52bb166...` 与 `e167836...` 连续合入，不能只复制 parser 而漏 descriptor/generator | **建议合并** | missing/zero-length/explicit fixture、TimecodeScale、DAR、audio/chapter/tag、DV BlockAddID；独立回滚两提交 |
| P1-3 HLS edition | 合入 `e7191f2...`；若真实 FFmpeg program metadata 全缺，再加“program 优先、全缺才 stream fallback”窄兼容 | **建议合并** | shared audio/subtitle group、空 program、阈值、reload、`flatten-editions`；只回滚 edition hunk |
| P1-4 alpha | 合入 `22ee762...`，只修 feature extraction 不再强制 alpha=1 | **建议随本批搭载** | 透明字幕/OSD、alpha video/overlay、HDR shader、截图；只回滚 libplacebo 1 行语义 |

P1 是当前最适合先批准的 MPV 代码批次：四个主题都有明确 correctness 收益，不改变 App API，也不要求重建 JNI。若 Exo E1 已通过，P1 的 MPV FFmpeg 输入建议使用 `177f090e0503b7e013922ca903bde14b1c375f18`；若用户暂不批准 MPV FFmpeg C0，也可先在当前 FFmpeg 上验证 P1 源码，但正式发布仍应避免制造一个马上需要再次替换的 native 组合。

### 42.5 P2：B8 Vulkan/AImageReader 与 DV7 的窄增量

#### 完整来源 commit

- Android Surface/HDR/DV 重写：`06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042`；
- AImageReader Vulkan 重写：`f5c9f148d00db652da1ee900f386d8e0e615ed84`；
- DV7 HDR10 fallback：`44755d7eaa0f186e4052ffc99c4f0b500a05a2ba`；
- libplacebo shader object 前置：`c78c4b4a5336473ff169ed2017a4535deed63d50`，只有未来采用依赖该 API 的外部图像实现时才搭载。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| P2-0 主体覆盖 | `06ec6e...` Surface/HDR/OSD 主体、`f5c9f1...` Vulkan interop 主体和 `44755d...` BL-only option/BSF 主体均登记为已覆盖 | 无重复代码 | P0 矩阵；不得删除本地 patch |
| P2-1 generic UV | 只取 `f5c9f1...` 五文件的 CPU `uv_offset`/`uv_scale` 预计算，并从 shader source 重新生成 SPIR-V header | **建议合并** | explicit legacy/compute/fragment、奇数 crop/旋转/缩放、像素对比；独立 shader patch 回滚 |
| P2-2 DV7 metadata/codecpar | 从 `44755d...` 取 metadata-missing splitter、`par_out`→codecpar、`dv_el_present=false`、init return/error、`INT_MAX` 和可选 auto-VO guard | **建议合并** | MKV/MP4/M2TS、缺失 flag、BL-only/EL-only、BSF failure、Surface Direct/GPU；独立合成 patch 回滚 |
| P2-3 MEL metadata-only | 仅在样片证明时吸收 `06ec6e...` 的 `dovi_needs_el_pixels()` 等价判断 | **条件合并** | MEL/FEL、RPU 时序、seek/flush、功耗；默认关闭，独立 feature patch |
| P2-4 Android BL+EL | `06ec6e...` 的 `force_swdec`/EL software 路径 | **默认暂缓** | 只有多设备 FEL/MEL 性能和 PTS 配对证据后另开实验 |
| P2-5 libplacebo allocation | `c78c4b4...` 只在所选 AImageReader/DV 实现实际调用新内部 helper 时搭载 | **不单独合并** | 编译/生命周期/shader object ownership；随依赖它的窄实现回滚 |

#### 必须保留的本地安全契约

- `mpv-android-dovi-el-surface.patch` 的 `VO_CAP_GPU_DOVI_EL` 单 Surface gate；Android 默认不启动第二路 EL decoder；
- `mpv-dovi-profile7-hdr10-base-layer.patch` 的三态 packet result、仅在原 AVPacket 与 demux buffer 精确一致时 `av_packet_ref`，否则 padded copy 并复制 properties；
- Surface Direct 下 `vd_lavc` decoder-level native-DV capability gate，以及 App `demuxer-dovi-profile7=hdr10|preserve`、来源 profile 与实际 fallback 诊断；
- Vulkan `auto: direct → stable → generic`、显式 legacy/stable、stable 四输出池、packed RGB10/RGBA16F fallback、conversion fence 后释放 AImage；
- `mpv-aimagereader-stable-flow.patch` 的 sequence counter、100 ms 有界等待、transient retry/logging；
- 可选 OSD、timestamped MediaCodec release、时序诊断和 Surface detach/replace 顺序；
- FFmpeg MediaCodec starvation patch，不能因采用目标 FFmpeg/mpv 树而丢失硬解失败后的下一 decoder/软解回退。

P2-1/P2-2 可与 P1 共用一次两 ABI构建，但验收失败时应能从候选源码只撤销相应窄 patch。P2-3/P2-4 不是 P1/P2-1/P2-2 的发布前置，不能为追求“完整采用上游”而顺带开启。

### 42.6 P3：B9 AudioTrack codec-aware carrier

完整目标 commit 为 `7282d53d58fcb8841ff93debea2a75e0b2afcd15`。其 carrier sample-rate hunk已由锁定线 `416d4a0fae8213ecf8e730feda6e2d8591bbd76f` 覆盖：PCM 才跟随设备 native rate，E-AC3/DTS-HD/TrueHD 的 IEC61937 carrier 保持 codec 声明的 192 kHz。P3 不整体 cherry-pick目标提交。

| 子阶段 | 代码动作 | 建议 | 验收/回滚 |
| --- | --- | --- | --- |
| P3-0 carrier rate | 保留当前 sample-rate 规则和构建脚本对 obsolete native-rate override 的拒绝 | **已覆盖** | AC3/DTS core 44.1/48 kHz；E-AC3/DTS-HD/TrueHD 192 kHz |
| P3-1 TrueHD | 继续保留本地所有 API level 的 TrueHD 8-channel → 7.1 mask workaround | **不回退为上游 API 31 gate** | API 24--35、已知旧 TV、HDMI/eARC/USB、PCM fallback |
| P3-2 DTS-HD MA | 吸收 SDK_INT/8-channel 判断前，先让 App probe 与 native 使用同一 codec/profile/channel 规则；HRA 2-channel 继续 stereo，MA 8-channel 才条件 7.1 | **profile-aware 后条件合并** | API 29/30/31+、HRA/MA、路由热插拔、AVR 显示与 native init 一致 |

P3-2 必须把 native hunk、`MpvAudioCapabilities`/`supportsMpvCarrier()` 和日志作为同一可回滚功能单元；不能出现 Java 用 stereo probe、native 用 7.1 init 的漂移。若短期拿不到 DTS-HD profile 和设备矩阵，P3 的正确决策是保持当前 TrueHD patch，不会丢失已工作的高码率 carrier rate。

### 42.7 P4：mpv-android shutdown 串行化与 Harfbuzz

#### P4-1 JNI shutdown

来源为 `f4c5d614d5f68d483b2e1889ffad11e513b877d2`。该提交的 command/Surface 请求队列主体已在 `third_party/mpv-player-jni` 中，但当前 `destroy()` 仍直接调用 `mpv_command_async("quit")`，没有把 SHUTDOWN 放到同一个 FIFO。建议只移植以下最小语义：

- request type 增加 SHUTDOWN，把 `quit` 排在已提交 command/video Surface/OSD Surface 之后；
- shutdown 一旦入队，拒绝新请求；若 enqueue/start 失败，保留 `g_force_shutdown + mpv_wakeup` 的兜底；
- shutdown reply、event thread退出和 `release_requests()` 只清理一次，不覆盖 WebHTV 已有 async request id、双 Surface、ISO controls、ANR/END_FILE/error bridge。

**建议窄移植并单独重建 `libplayer.so`。** 验收连续执行 create/play/seek/set two surfaces/destroy、快速换集、Surface detach 后退出、command in-flight、native init failure、Android 15 destroyed-mutex crash buffer；还要确认所有 pending Java future 获得 reply 或明确取消，不发生 shutdown 永久排在一个永不回复请求之后。失败只恢复旧 `libplayer.so`，不回滚 P1--P3 native assets。

#### P4-2 Harfbuzz 14.3.1

来源为 `7cc841e3b5e726c09376fb2e33d5f8e33e42f059`，只把构建输入从 14.2.1 更新到 14.3.1。它不是播放器故障修复的独立重建理由；若 P1/P2 已需重编字体栈，可在核对 release notes、源码 commit 和 license 后条件搭载。验收 CJK、Arabic、Indic、combining marks、emoji/variation selector、ASS fallback/fontconfig、长字幕和 32 位 ABI；若 shaping/体积/构建变化不可接受，保留 14.2.1 不影响其余 MPV 源码升级。

### 42.8 P5：实验、维护和明确跳过项

- mpv `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47`（Wayland null output）、`e034d612cf6893954e943916988eef9e4426604c`（CI action）、`b6d3434493fd04c0ee40a5610d8c311b77b16a6d`（注释 typo）不构成 Android native rebuild 理由；若最终选择目标整树可自然继承。
- mpv-android `ad98fc97ff1d25e217389e7238a1abda8c13a6c4`, `b356ac12b1ae3873b767807db6c89b6f2a276542`, `318ee1817c7810a399cc6fc63db331bde3b11ced`, `0431208436667ffed11ee571b91bab6ac3d7d239`, `a8ab240a0239261a47f1644256472ebdf7fab62f` 只改示例 App release/log/service/翻译/license 页面，不进入 WebHTV。
- P2-4 Android BL MediaCodec + EL software、C2 FFmpeg `dovi_rpu convert=p81` 和任何删除现有 DV7/Surface安全门的整树替换都留在实验分支；没有真实 MEL/FEL、多设备性能和用户输出策略决策时不写入正式 lock。
- `c78c4b4a5336473ff169ed2017a4535deed63d50` 只是 libplacebo 内部前置；没有选中依赖它的实现时，单独合并只增加重建成本，不产生可见收益。

### 42.9 三仓库 58 项唯一主归属校验

以下三张表给每个目标分支 commit 一个主要归属。一个 commit 可在其它阶段作为联合验收来源，例如 `06ec6e...` 同时解释 P2-3/P2-4，但其主要归属只记 P2；这不表示允许重复 cherry-pick。

#### `mpv-android` 24 项

| # | 完整 commit ID | 主要归属 | 当前动作 |
| ---: | --- | --- | --- |
| 1 | `ad98fc97ff1d25e217389e7238a1abda8c13a6c4` | P5 | 示例 App release，跳过 |
| 2 | `b356ac12b1ae3873b767807db6c89b6f2a276542` | P5 | 示例 App log，跳过 |
| 3 | `318ee1817c7810a399cc6fc63db331bde3b11ced` | P5 | 示例 App service，跳过 |
| 4 | `0431208436667ffed11ee571b91bab6ac3d7d239` | P5 | 示例 App翻译，跳过 |
| 5 | `7cc841e3b5e726c09376fb2e33d5f8e33e42f059` | P4 | Harfbuzz 条件升级 |
| 6 | `db7df511faffc4319e32f04e11d3aac3e02dad73` | P0 | MbedTLS 语义已覆盖 |
| 7 | `a8ab240a0239261a47f1644256472ebdf7fab62f` | P5 | 示例 license 页面，跳过 |
| 8 | `db8dec699b44dd10f21ee242efcc755e4d40c114` | P0 | native CI 等价登记 |
| 9 | `5be109f80714ab69eef8ea567a4360e124dab92b` | P0 | Android CMake tooling 已覆盖 |
| 10 | `880622fba9b653a0315e88dda3f60632819a029e` | P0 | NDK shaderc/Vulkan 已覆盖 |
| 11 | `c0786731f9d611fe18ee64b26c66ce7bd3ebd5eb` | P0 | libbluray 已覆盖 |
| 12 | `a3e439523f11d17fa25d2cd2726ea24cd20c9399` | P0 | iconv/uchardet 已覆盖 |
| 13 | `19e4f3ec9ab8bd593ec097b42abb0cb821702a20` | P0 | libarchive 已覆盖 |
| 14 | `49482b3c616de15c30babb12c3fec1b287dcabd6` | P0 | dvdnav 已覆盖 |
| 15 | `97f994f788a1bd8da0e81335c5265486360f8c20` | P0 | rubberband 已覆盖 |
| 16 | `f523de74d6a2c5d4fc26155d69ed8e063e028c97` | P0 | libarcdav3a 已覆盖 |
| 17 | `1daf7314b280c8d37f84518d7c47d2735556b8f0` | P0 | filters/passthrough 已覆盖 |
| 18 | `f639f3f6136ffa69bce599eb50c1575438ff40f7` | P0 | libaribcaption 已覆盖 |
| 19 | `b3c4cf87f71c9a62b343ea6bbcccdcc1520edd8f` | P0 | JNI lifecycle 主体已覆盖 |
| 20 | `585376daf5210bb2c2fd37a93a7f00010ad2912b` | P0 | byte-array property 已覆盖 |
| 21 | `79d8b4c26135daf56ed2418f6a46163599b44fff` | P0 | END_FILE detail 已覆盖 |
| 22 | `f4c5d614d5f68d483b2e1889ffad11e513b877d2` | P4 | 只取 shutdown 串行化 |
| 23 | `082d3d4939b75bf78cfc0a3f5f016ed9e9745d5e` | P0 | Lua fallback 语义已覆盖 |
| 24 | `7523b5c5199c84da4092787b7bf5d72452d61780` | P0 | FFmpeg 9 builder 指向已覆盖；目标 revision 由 C0 决策 |

#### `libplacebo` 7 项

| # | 完整 commit ID | 主要归属 | 当前动作 |
| ---: | --- | --- | --- |
| 1 | `c78c4b4a5336473ff169ed2017a4535deed63d50` | P2 | 仅依赖新 helper 时搭载 |
| 2 | `22ee762e8e0890fc54068beb670310f0edce7263` | P1 | alpha correctness，建议搭载 |
| 3 | `f5bdd194e700a002de441a350bbed385ec7ca30b` | P0 | API 375 等价覆盖 |
| 4 | `1797af1ce61d13e998ff4397b017422dd1e0c53c` | P0 | HDR validation 等价覆盖 |
| 5 | `373cd8be1e5f6c4e7a2c565766d23016be2bce3c` | P0 | checked DV mapping 等价覆盖 |
| 6 | `2a1101a2a466944a9d70c64991bc1983cfbe1cd0` | P0 | `disable_storage` 等价覆盖 |
| 7 | `2301953d9faf0f5e112ff337f79cec64eab2f4f1` | P0 | raw external YUV 等价覆盖 |

#### `mpv` 27 项

| # | 完整 commit ID | 主要归属 | 当前动作 |
| ---: | --- | --- | --- |
| 1 | `f4d13e1c2c91f3a56e589aef9cb44cbc02e26e47` | P5 | Wayland，Android 不单独合并 |
| 2 | `7b8915bc1d04c7e1b61184e00c7fbfaab1911e75` | P1 | packed RGB10，建议 |
| 3 | `52bb166f309c8bb55ab34b2b0bc5c8ead05370e4` | P1 | EBML generator 前置 |
| 4 | `e167836802da6d5a4301bd4c4eeb3c5c3c17ccb8` | P1 | EBML defaults，建议 |
| 5 | `e034d612cf6893954e943916988eef9e4426604c` | P5 | CI，跳过 |
| 6 | `b6d3434493fd04c0ee40a5610d8c311b77b16a6d` | P5 | 注释 typo，跳过 |
| 7 | `e7191f2a65d64af266c5c80793e79d2f4b92b789` | P1 | HLS edition，建议 |
| 8 | `70510dee41c41da19d71b952f1b05a6463d8d0d6` | P0 | helper schemes 已覆盖 |
| 9 | `4d0423281f850b788e52b64def4b26a1505f6140` | P0 | disc ISO 已覆盖 |
| 10 | `00402d0696d734783eb5efe1c23f4e3bda4bc3f8` | P0 | rewind 已覆盖 |
| 11 | `82790bf10e8f67c4c9afa18d790ca98303276a60` | P0 | curl Range retry 已覆盖 |
| 12 | `a088b8b9a1c5c3e2520145d69e5543a1a87a5cf7` | P0 | curl worker 已覆盖 |
| 13 | `32c4d5adad29107756ae2987d69d92844bfed243` | P0 | MMT/TLV 已覆盖，按输入验收 |
| 14 | `78617f20a4b449addbe8ac40e7e0791b2aab1c5b` | P0 | attached artwork 已覆盖 |
| 15 | `31a5e3bdf76c2f2918a8992f4a9614ab76070af7` | P0 | live status 已覆盖 |
| 16 | `c2bc880511fd20850c586f2dc25aff770723b6b4` | P0 | TTML layout 已覆盖 |
| 17 | `7282d53d58fcb8841ff93debea2a75e0b2afcd15` | P3 | mask 窄合成，carrier rate 已覆盖 |
| 18 | `72e86486a5dd3a00950a9e5dfa3e381d2e00d230` | P0 | 0.1x 已覆盖 |
| 19 | `47bb36190de83db31224d7193bd8514fabefb314` | P0 | decoder reinit 已覆盖 |
| 20 | `793d89800a425cda856065307c9027997ebf1c9c` | P0 | HDR negotiation 已覆盖 |
| 21 | `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042` | P2 | 主体已覆盖；MEL窄候选，EL实验暂缓 |
| 22 | `f5c9f148d00db652da1ee900f386d8e0e615ed84` | P2 | 主体已覆盖；generic UV 建议 |
| 23 | `a810f8e4f3c5cfde42367eace6d9015f95b99cd6` | P0 | Vulkan selector 已覆盖 |
| 24 | `43b378853776dfd734d21d9649b2053eefcb39f5` | P0 | compositable swapchain 已覆盖 |
| 25 | `c7fef70644b3d506340e113689a5923f324c861d` | P0 | DV5 GPU mapping 已覆盖 |
| 26 | `1c2d989b6b246c36869fff9ec8297c9897e1d964` | P0 | ASS VO formatting 已覆盖 |
| 27 | `44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | P2 | DV7 主体已覆盖；metadata/codecpar 窄增量建议 |

机械校验应从检查点 5.1/5.2 与目标 mpv 父链清单抽取原始集合，再从本节三张表分别抽取完整 hash：预期 `mpv-android 24/24`、`libplacebo 7/7`、`mpv 27/27`，且每个集合无遗漏、无额外、无重复。

### 42.10 必须保留的成套构建和发布契约

无论批准 P1、P2 还是 P3，最终候选产物都必须满足：

- FFmpeg 文件名、ELF `SONAME` 和所有 `DT_NEEDED` 从 `libav*`/`libsw*` 改为 `libmv*`/`libmw*`，并确认 APK 内 Exo `libav*` 与 MPV `libmv*` 不发生 linker 复用；
- 保留 `ffmpeg-mediacodec-port-starvation.patch`、`ffmpeg-webhtv-proxy-range.patch`、`mpv-stream-cb-disc-controls.patch`、Matroska segment-end seek、TrueHD channel mask、DV7、optional OSD、timed release/diagnostics、Vulkan smart/legacy/stable 和 AImageReader stable-flow；
- libplacebo 继续静态链接入 `libmpv.so`，curl/nghttp2/MbedTLS 保持静态网络栈，APK 不增加意外独立 `.so`；
- 两 ABI均执行 `scripts/verify_mpv_native_assets.sh --require-elf`，检查版本字符串、HTTP/2、AV3A/MMT/TLV/ARIB/TTML、Vulkan/DV/光盘/Range/Matroska markers、SONAME/DT_NEEDED 和 native assets manifest；
- App 分别回归 OpenGL、Vulkan direct/stable/legacy/generic、Surface Direct、硬解/软解、LUT/字幕、HLS 限码率、线路切换、连续起播/退出、Blu-ray/DVD ISO 和 Android 15 lifecycle；
- 每次发布保存 lock、构建脚本/patch hash、两 ABI `.so` hash、ELF 输出、APK asset 清单和样片日志。P4 的 `libplayer.so` hash/JNI 测试单独记录。

### 42.11 供用户决策的最小集合与恢复锚点

| 决策 | 子阶段 | 说明 |
| --- | --- | --- |
| 建议先批准 | P0；P1-1/2/3/4；P2-1；P2-2 | 都有明确 correctness 收益；可在一次完整 native 候选构建中搭载，但主题可独立撤销 |
| 建议条件批准 | P3-2；P4-1；P4-2 | 分别需要统一 AudioTrack probe、JNI lifecycle 回归、Harfbuzz shaping/来源核对 |
| 样片后再决定 | P2-3 | MEL metadata-only 依赖真实 DV 表示时序 |
| 当前暂缓/跳过 | P2-4；P2-5 单独合并；P5 维护项；C2 默认启用 | Android EL 成本/Surface风险未闭环，维护项无 Android 播放收益，C2 会改变既有 DV7 策略 |

MPV 第一轮建议顺序为：**P0 → P1 → P2-1/P2-2 → P3（仅在 App/native probe 统一后）→ P4**。若只批准最小低风险集合，正式源变更为 B10 四个 mpv commit、libplacebo alpha 和 B8 两个窄合成 patch；不更新 App JNI，不启用 Android EL，不改变 DTS-HD mask。

- 三仓库目标提交主归属已经完整落盘：mpv-android 24、libplacebo 7、mpv 27；已覆盖提交没有从记录中消失。
- 所有 MPV rebuild 必须保留 WebHTV 的 ELF 改名、FFmpeg starvation/Range、光盘 controls、DV7 packet/Surface安全、Vulkan backend/fence/AImage、Matroska seek、TrueHD 和 App/native verification 契约。
- 下一检查点整理通用 C0--C3 搭载矩阵：C0 同一 FFmpeg 源 revision 在 Exo/MPV 分两次构建；C1 按 MMT/HLS/DV 等真实输入随对应 E/P 阶段验收；C2 DV7→P8.1 继续暂缓；C3 随 Exo E7-2 修改 App ISO metadata resolver，不要求 MPV native升级。

## 检查点 43：2026-08-21 通用功能 C0--C3 搭载矩阵

本检查点把跨仓库功能从“来源提交”转换成实施决策。通用项不代表再建立一条可以独立替换播放器的第三套二进制；它们要么是同一 FFmpeg 源 revision 在 Exo/MPV 两条链分别构建，要么是由真实输入驱动的联合验收，要么是 App 通用层随某个播放器阶段同步修改。

### 43.1 总原则：共享语义，不共享产物

| 维度 | Exo/nextlib | MPV native | App/common 结论 |
| --- | --- | --- | --- |
| FFmpeg 源 | 可采用 `177f090e0503b7e013922ca903bde14b1c375f18` | 可采用同一 commit | **同源码、分两次构建**；不共用 `.so` |
| 工具链 | NDK r28c、Media3 nextlib 裁剪配置、`libav*` | NDK r29、mpv-android build framework、`libmv*`/`libmw*` 改名 | 每条链独立 lock、哈希、ELF、运行回滚 |
| 播放入口 | Media3 extractor/renderer/AAR | libmpv/lavf/VO/AO/JNI | 同一输入必须分别记录“Exo 路径”和“MPV 路径”结果，不能用一条路径代替另一条 |
| 样片/期望 | 可共用原始样片、容器元数据和期望值 | 可共用 | 样片仓、清单和诊断字段共用；编译产物、错误预算和回滚单元分开 |
| 发布顺序 | **先 E1/C0，再 E2--E8** | Exo 通过后才进入 P0/P1 | 通用项的搭载点写入 E/P 阶段，不单独提前改 API/lock |

因此“通用”在本文中有三种不同动作：

1. **同源码双构建**：C0 安全基线；先产出 Exo nextlib AAR，再产出 MPV native assets。
2. **联合验收**：C1 的 HLS/live、MMT/TLV、DV metadata 等；只有对应播放器确实消费输入时才进入该播放器阶段。
3. **App 通用代码搭载**：C3 ISO metadata extents；跟随 Exo Media3 `IsoFileEntry` API 变化修改 `IsoTrackMetadataResolver`，同时回归 MPV ISO，但不升级 MPV native。

### 43.2 C0：FFmpeg 9.0.1 安全基线——先 Exo，后 MPV

#### 来源范围

C0 使用 FFmpeg `release-9.0-fongmi` 目标头 `177f090e0503b7e013922ca903bde14b1c375f18` 的 #1--#48 结果（完整 49 项逐提交表见检查点 6.2）。其中包括：

- 32 位/整数边界与未初始化内存修复：`bd4a4a0e55bb4ab5d4cf5982f4d1855899921538`、`e83aa76851c14d80e605c0458688eed11aa910ee`、`c7132ef8f63c383d11a00a9e3034748d8dd15fb3`、`7646bb4c42e6837a9396c3d9ab8d8cf476e2053b`、`c99a0e9ffdd01e7da9aeab8577ae5ed8272bc2c9` 等；
- MPEG-PS/TS/RTP/DASH/HEVC/AV1/WebP/滤镜安全修复：`b2df2f4f22be1452f6a054d9cb062b11c380e5a7`、`b274f0d21ba684446fd59b49e00f3f8e9ed954df`、`999f8ba75ce0bf1167677de7e11a5af678fdb866`、`d7e89879b693f1576cd271fe88b9a0439cc44d79`、`f175bd50821f9adcded3acacc6b8e04037a92715` 等；
- 9.0.1 版本/库元数据：`96165329e52d5676cbc890c10eaebc4eee7a76b7`、`2e41be62b762af5c03c3b2f3f5b9db69ef242aff`、`bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`；
- 当前树已含的 AV3A、MediaCodec、HLS/live、MMT/TLV、DV container/metadata 和 HEVC 优化等重落基提交：仍随目标源码构建，但不作为新的 App 代码 cherry-pick；
- **不启用** `177f090e0503b7e013922ca903bde14b1c375f18` 新增的 `dovi_rpu convert=p81`，该项归 C2。

#### C0-E：Exo 实施步骤

1. 复制 `third_party/media-lock.json`、当前 nextlib AAR/POM、`nextlib-av3a.patch` 和 `nextlib-ffmpeg-soft-load-shedding.patch`，建立可删除的 `ffmpeg-9.0.1-exo` 构建目录。
2. 仅把 nextlib FFmpeg revision 改为 `177f090e0503b7e013922ca903bde14b1c375f18`；保持 NDK r28c、`libav*` 命名、当前裁剪配置、AV3A 静态 `libarcdav3a` 接线和软解降载三态。
3. 编译 `armeabi-v7a`/`arm64-v8a` 两套 AAR，先做 ELF/SONAME/符号/哈希和 license/provenance 校验，再运行 Exo E1、DV/HDR、音频、字幕、HLS/DASH、MP4/MKV、32 位软解及畸形输入矩阵。
4. C0-E 通过后，保存独立 nextlib 版本号、AAR/POM/APK、两 ABI `.so` hash 和回滚点；失败只恢复 Exo AAR/lock，不触碰 MPV assets。

#### C0-M：MPV 实施步骤

只有 C0-E 通过后，才让 MPV 使用同一 FFmpeg源码 revision。构建时仍应用 `ffmpeg-webhtv-proxy-range.patch`、`ffmpeg-mediacodec-port-starvation.patch`，以 NDK r29 生成 FFmpeg `libmv*`/`libmw*`，再与 mpv `44755d7e...` 或其窄合成树、libplacebo 和 mpv-android 成套链接。C0-M 的回滚只恢复 `third_party/mpv-native-lock.json`、两 ABI MPV assets/JNI（如有），不回滚已经发布的 Exo AAR。

两条链都要验证同一组畸形输入，但预期不同：Exo 检查 `libav*` AAR、Media3 renderer 和 fallback；MPV 检查 `libmv*` ELF、lavf/MediaCodec/Vulkan/VO/AO、本地 Range/光盘/DV7 patch。任何一方失败都不能把另一方的成功当作整体通过。

### 43.3 C1：跨播放器媒体语义——按输入随 E/P 阶段搭载

C1 不建议作为一个“通用功能大提交”独立发布。每项先在当前锁定产物上做需求/输入探测；有真实样片后，再把对应窄代码放到 Exo E 阶段或 MPV P 阶段。来源与搭载关系如下：

| C1 子项 | 主要来源 commit | Exo 搭载点 | MPV 搭载点 | 当前决策 |
| --- | --- | --- | --- | --- |
| HLS/DASH/RTSP live status 与 discontinuity timestamp | FFmpeg `e640443a24dc89993042a99ade8a02a4d5ac2a81`、`5805f9364c2e9a5f6ce625c9077b308c3ed4014d`；mpv `31a5e3bdf76c2f2918a8992f4a9614ab76070af7`；media HLS `f0eb7b514d5fcaba843dfe93d92acfff19a14e9e` | E5-2/E5-1：Media3 HLS/DASH/RTSP、App `MpvHlsProxy` 诊断；已覆盖项先回归 | P0/P1-3：lavf live status、`hls-bitrate` edition 和直接 HLS 输入 | 同样本、分别验收；不因 FFmpeg API 已有就改 App 状态模型 |
| HLS image-wrapped TS/SAMPLE-AES/广告边界 | FFmpeg `e8392b0b0fb0ae6a827fa65f678cd4d6827f6f74`；media `a1e190005981febfa27e7583e5902d3cc2ce4ef7`、`13fbfd88d312de6c4f10fedd2b085cb2710b88ae` | E5-2/E5-3/E5-5：保留代理 Range/key/cache/error 分类；只按 stream type、key rotation、unsupported MIME 样片移植 | P0 对直接 lavf HLS/TS 做回归；不把 App 代理重写路径当成 MPV 已验证 | 已覆盖主体，窄安全 hunk 条件合并 |
| MMT/TLV、ARIB/TTML/ALS、live/no-seek | FFmpeg `054c8690e16b377eb1c6375c8751a44b8eb1d962`；mpv `32c4d5adad29107756ae2987d69d92844bfed243`；media `ccf962e8912695dc60ce82aa4470df899c6306a3` | E9-5：无 ISDB-S3 样片不加入 Java extractor；先用现有 FFmpeg 探测 | P0/B7：当前 lavf parser/flags 已在树中，只做真实 MMT/TLV 输入验收 | **需求门槛**，不提前扩 APK/API |
| DV container/CSD/RPU/HDR metadata | FFmpeg `6dc8edecd7ebafc80764b8c0a20f87e3f9fb1382`、`691a7d5a125b40dcc427ee298c983729e673d974`、`eb107bbafe37442065e42b4f2d410f371b758143`、`dd537f9a852d0ce40078f9ac520d7267ba850883`；media `0cefd3ceec27444cf8faf02486b472bab39109fe`；mpv `c7fef70644b3d506340e113689a5923f324c861d`、`44755d7eaa0f186e4052ffc99c4f0b500a05a2ba` | E2-1/E2-2/E2-3：parser/CSD/output policy 分开；保留 libdovi/P8.1/user policy | P2-2：metadata-missing/codecpar/packet safety；P2-3/P2-4 单独决策 | 共用 DV 样片和元数据期望，不共用 renderer/`.so`；C2 仍禁用 |
| MMT/TTML/字幕样式的 UI 消费 | mpv `c2bc880511fd20850c586f2dc25aff770723b6b4`；media `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | E4-4：Cue/region/letter-spacing → Canvas/WebView；先 common 契约再 parser/UI | MPV ASS/TTML 原生字幕单独回归，不把 Exo Cue 字段移入 JNI | 分离数据模型，不能跨播放器直接复用 |

#### C1 共同样片和验收记录格式

每个 C1 输入建立一条 manifest，至少记录：原始 URL/文件 hash、容器、codec/profile、是否加密、代理/Range 条件、播放器入口、期望 track/时间戳/metadata、实际 decoder/VO/AO、错误/回退原因和设备/API。相同样片在 Exo 与 MPV 的 manifest 中共享 `sample_id`，但分别写入 `exo_result` 与 `mpv_result`；不能只写“播放成功”。

### 43.4 C2：DV7→P8.1 bitstream filter——实验候选，不随 C0/P1 自动启用

唯一新增来源是 FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` 的 `dovi_rpu convert=p81`。它把 profile 7 HEVC packet 的 RPU/config 重写为 P8.1 并删除 EL，但不会自动决定 Android decoder、HDR metadata、输出 VO、加密/seek 状态或用户策略。

当前两条链已有不同且更高层的策略：

- Exo 使用 `DolbyVisionP81ExtractorsFactory`、libdovi 和 `ExoplayerHdrUtils`，按硬解能力/用户选项在 extractor/session 级锁定转换，并保留失败中止与加密禁用；
- MPV 使用 `demuxer-dovi-profile7=hdr10`、`mpv-dovi-profile7-hdr10-base-layer.patch` 和 Surface/decoder gate，在 demux 层做 BL-only HDR10 fallback，同时保留 preserve/native 策略。

贸然把 C2 接入两边会造成三个问题：转换入口从 extractor/demux policy 改成 packet BSF，错误和 seek 状态可能变化；P8.1 codec string/CSD 与实际 decoder/display capability 可能再次漂移；用户已选择 native DV、HDR10 或 preserve 的行为可能被自动转换覆盖。

**C2 实施门槛：**

1. 先在 MPV P2 原型中并列比较“现有 WebHTV BL filter”和“FFmpeg `convert=p81`”的 packet、CSD、RPU、PTS/DTS、seek/flush、加密和错误结果；
2. 再在 Exo E2-2/E2-3 样片中比较 libdovi 与 BSF 的逐 sample 输出、硬解能力探测、HDR10 mastering/content-light metadata 和 renderer 选择；
3. 只有两边对 Profile 7 MEL/FEL、无 RPU、缺失 config、损坏 NAL、跨 segment/seek、direct Surface/GPU 的结果都能定义一致，且产品明确要统一入口，才建立 C2 feature flag；
4. 即使启用，也要保留 Exo/MPV 原有 fallback 作为失败回退，并为每条播放器单独编译/回滚。

当前建议：**C2 只保留源码和实验测试，不进入正式 Exo E1、MPV P1/P2 的 lock，不删除 `DolbyVisionP81ExtractorsFactory` 或 `mpv-dovi-profile7-hdr10-base-layer.patch`。**

### 43.5 C3：ISO/UDF 多 extent 的 App 通用一致性——随 Exo E7-2，不等 MPV native

C3 的触发来源主要是 media `990abc2368fd74779f525ee345734470659f3d53`（`IsoFileEntry` 多 extent/read/prefetch）及其与 `15d8d21f3354e6da48c5a47751a3edb943f9ffc6`、`bd3b52102a1dad1ef9d168165d0e8959fca5d03f`、`93af478b4cd2126c3844aaf2f813e24c0262eaf7` 的光盘生命周期接线。它不是 MPV native 功能，但 WebHTV 的 `IsoTrackMetadataResolver` 读取 Media3 parser 结果，因此必须同步改 App 通用层。

#### 实施步骤

1. **C3-0 API/fixture 准备：** 在 Exo E7-1 reader safety 通过后，冻结 `IsoFileEntry` 的 recorded/unrecorded extent、logical offset、短读、取消和 8 MiB 上限契约；准备 split MPLS、split CLPI、split IFO、未记录 extent、跨 extent EOF/Range fixture。
2. **C3-1 resolver 迁移：** 随 Exo E7-2 的 `IsoFileEntry` API 变更，把 `IsoTrackMetadataResolver.readEntry()` 从单一 `byteOffset + read` 改为按逻辑 offset 遍历 `extentOffsets/extentLengths`，正确跳过 unrecorded extent，限制累计读取并在取消时关闭 reader。
3. **C3-2 联合回归：** Exo 侧验证语言/角色/章节/edition/轨道选择；MPV 侧验证同一 ISO 的语言 metadata、MPLS/CLPI 轨道映射、`cumulativeOffsetUs` 连续时间轴和换源/取消生命周期。MPV native lock 不变。
4. **C3-3 回滚：** Exo Media3 AAR/API 与 App resolver 必须能一起回滚；如果 resolver 不能在新 API 下维持旧 ISO，先停 E7-2，不给 MPV 语言 metadata 发布半成品。

#### 为什么不能独立等 MPV 阶段

`IsoTrackMetadataResolver` 的输入类型来自 Media3/Exo，等到 MPV native 重建再改会让 Exo AAR 已经输出多 extent、而 App resolver 仍按旧单 extent 读错后半段；这会在同一 APK 中产生“Exo 播放正常、MPV 轨道语言错误”的隐蔽回归。C3 应在代码归属上标为通用 App 层，在实施顺序上跟随 Exo E7-2，在验收上同时覆盖两播放器。

### 43.6 通用阶段与 Exo/MPV 顺序总表

| 通用项 | 首次可执行时机 | 是否随 Exo 搭载 | 是否随 MPV 搭载 | 是否独立发布 |
| --- | --- | --- | --- | --- |
| C0 FFmpeg 9.0.1 安全基线 | 现在可做，但按先 Exo 后 MPV | **是，E1** | **是，P1/P2 成套重建** | 不共享二进制；各自独立版本 |
| C1 HLS/live/timestamp | 对应真实样片出现 | E5/E1 的输入验收 | P0/P1-3 的输入验收 | 否；按播放器窄 hunk |
| C1 MMT/TLV | ISDB-S3 样片/需求确认后 | E9-5 分层实验 | P0/B7 先用现有链验收 | 否；不提前扩 API |
| C1 DV metadata | E2 样片 | E2-1/2/3 | P2-2/P2-3 | 否；共用样片，不共用 renderer |
| C1 字幕/TTML语义 | Exo E4 | E4-4 | MPV 字幕单独回归 | 否；Cue 与 native subtitle 分开 |
| C2 DV7→P8.1 | E2/P2 原型比较后 | 默认不搭载 | 默认不搭载 | 否；实验 feature flag |
| C3 ISO extents | E7-2 API 变更时 | **是，E7-2 同批 App resolver** | 只做 MPV ISO metadata 联合验收 | 否；App 通用提交与 Exo AAR 同批 |

### 43.7 通用项的风险、回滚和用户决策

- C0 的风险是 ABI/裁剪配置/本地 patch 与安全修复交互；回滚必须按 Exo AAR 和 MPV assets 分开，不能用“同一源码”掩盖二进制差异。
- C1 的风险是把一个播放器的成功路径误认为另一个播放器的能力，或把代理重写、live 状态和 track metadata 改成全局 API；回滚按具体 extractor/demux/App adapter，不回滚整个通用层。
- C2 的风险最高：会改变 DV7 用户策略、packet/CSD/seek/error 语义和 Android output gate；默认暂缓，实验结果不进入正式 lock。
- C3 的风险集中在 `IsoFileEntry` API 和 App resolver ownership；回滚边界是 Exo E7-2 AAR + resolver adapter，不触碰 MPV native。

首轮通用决策建议：

1. 批准 C0-E（随 Exo E1）；Exo 通过后再决定 C0-M（随 MPV P1 成套 rebuild）。
2. 批准 C1 只做样片 manifest/诊断和当前锁定产物验收；代码按 E/P 阶段逐项决定，不建立 C1 大提交。
3. 暂不批准 C2；保留实验分支和对照脚本，继续使用现有 Exo/MPV DV7 策略。
4. 批准 C3 与 Exo E7-2 同批准备和实施，MPV 只参加联合回归，不等待后续 native 阶段。

### 43.8 恢复锚点

- C0--C3 已按“同源码双构建、输入联合验收、实验暂缓、App 通用层随 Exo”完成矩阵化；C0-E/C0-M、C1、C2、C3 的 commit、搭载点、产物和回滚边界均已写明。
- 当前推荐的完整顺序为：Exo `E1 → E2-1 → E3-1a → E3-1b → E4-1/J1 → E6-1 → E7-1`；随后 MPV `P0 → P1 → P2-1/P2-2 → P3 → P4`，C1 按真实输入插入，C2 暂缓，C3 随 E7-2。
- 下一步只需做文档机械校验、检查顶部恢复锚点和工作区状态；本轮不更新任何 lock、AAR、APK、`.so` 或补丁。

## 检查点 44：2026-08-21 上游评估与实施治理规则落盘

本检查点不改变五仓库的提交结论、lock、补丁、AAR、APK 或 native 资产，只把后续评估/实施必须遵守的方法论固化为仓库级规则和可复用 Skill，避免依赖单次会话记忆。

### 44.1 采用 `AGENTS.md + Skill` 两层结构

- 根目录 `AGENTS.md` 是全仓库强制约束：区分评估与实施授权、禁止需求蔓延、完整 commit 身份、无回归要求、风险驱动研究/测试、原子提交、阶段 tag、工作区保护和上下文检查点。
- `.codex/skills/upstream-integration-governor/SKILL.md` 是上游集成操作流程；详细证据标准、阶段门禁和 WebHTV 播放器专项约束分别位于 `references/evidence-and-research.md`、`references/integration-workflow.md`、`references/webhtv-player-gates.md`。
- `.codex/skills/upstream-integration-governor/scripts/verify_upstream_checkpoint.sh` 只读检查 AGENTS/Skill/评估文档、最新检查点、恢复锚点、完整 commit 数、`git diff --check` 和受保护依赖/二进制路径的工作区变化，不会 fetch、写 lock、commit、tag、merge 或删除文件。

使用两层而不是只选一种，是因为长期不可绕过的仓库规则应始终进入上下文，而逐提交研究、证据分级、实施阶段模板、播放器矩阵等较长内容只应在上游任务触发时加载，避免长期挤占上下文。

### 44.2 回滚点策略修正

“每次修改文件后打 tag”改为以下可维护契约：

1. 每个已经验证的逻辑变更形成一个原子 commit，作为日常最小回滚和 `git bisect` 单元；中间 commit 尽量保持可构建、可测试。
2. 只有获批实施阶段的已提交状态才建立带注释 tag：阶段前已知良好基线、通过门禁的候选、正式发布里程碑；不对未提交编辑或每个文件修改打 tag。
3. tag 必须记录目标、用途和对应文档阶段；不得复用/移动已发布 tag，不经明确授权不得 push。
4. 已共享/发布的提交优先用 `git revert` 回退；源码、lock、生成二进制和 App adapter 有耦合时必须成套回滚。

该策略同时保留高密度恢复点、可二分历史和明确发布锚点，避免 tag 噪声、错误地把未验证状态标成里程碑，以及后续误移动 tag。

### 44.3 网上最佳实践研究基线

治理 Skill 已记录并吸收 Git tag/bisect/worktree、GitHub releases、FFmpeg developer/FATE、mpv contribution、AndroidX Media、Android testing、Linux patch process、SLSA、OpenSSF Scorecard 和 OSS-Fuzz 的官方资料；每项 URL、用途和研究基线日期见 `references/evidence-and-research.md`。

后续具体阶段仍必须围绕真实问题继续搜索精确 commit/符号、issues/PR/邮件列表、平台/格式规范、相关项目代码、可复现实验，以及在算法/并发/性能/安全问题上确有适用性的论文和技术文章。博客、帖子和论坛只作为线索，不能覆盖源码、规范、测试和 WebHTV 实证。

### 44.4 后续执行约束

- 继续按本文件的 Exo `E*`、MPV `P*`、通用 `C*` 阶段让用户决策；先 Exo，稳定后再 MPV。
- 实施前必须重新核对远端头是否漂移，但新 commit 不得静默塞进已批准阶段。
- 每个阶段先写完整 commit 来源、当前缺口、替代方案、本地契约、风险、性能/安全/ABI/license/provenance、验收、预算、停止条件和回滚，再改代码。
- 每个研究批次、实现 commit、验证阶段以及可能发生上下文压缩之前，立即写恢复检查点；恢复时先核对检查点、`git status`、HEAD 和 locks，不能凭会话记忆接续。
- 测试按风险选择，避免文档/窄 Java 改动触发无意义的全 ABI native 重建，也不能用“能编译”代替 parser/decoder/Surface/JNI/生命周期/性能正确性。

### 44.5 恢复锚点

- 五仓库逐提交梳理和 Exo/MPV/通用实施矩阵仍以检查点 40--43 为当前完成状态；本检查点只增加治理层，不改变任何已有推荐或 commit 归属。
- 本轮新增 `AGENTS.md` 和 `.codex/skills/upstream-integration-governor/`，并仅更新本评估文档；`third_party/fongmi-repositories-lock.json` 的既有修改保持原样、未被本轮触碰。
- 下一步由用户先审阅/决定 Exo 实施批次；获批后按 Skill 的 Phase 4 建立阶段基线、测试预算和回滚锚点，再开始 E1 或用户指定的更小阶段。

## 检查点 45：2026-08-21 治理 Skill 校验完成

- 完成：执行 `.codex/skills/upstream-integration-governor/scripts/verify_upstream_checkpoint.sh docs/upstream-player-dependency-merge-assessment-2026-08-20.md`；Skill `quick_validate.py`、Shell 语法检查、工作树/索引 `git diff --check` 和治理文档尾随空白检查均通过。
- 身份：当前仓库 HEAD `04e7713daa951244a2626b1c2fe9fbbe8db5e465`，当前分支 `fongmi-sync`；评估文档中已记录 425 个去重后的完整 40 位 commit ID。
- 结果：指定已记录的 FFmpeg commit `177f090e0503b7e013922ca903bde14b1c375f18` 校验通过；指定不存在的 40 位全零 hash 能正确触发失败，证明脚本不会静默接受遗漏。
- 文件/产物：新增 `AGENTS.md`、`.codex/skills/upstream-integration-governor/` 及本检查点；未修改 lock、patch、AAR、APK、`.so` 或其他二进制。既有 `third_party/fongmi-repositories-lock.json` dirty 状态仍保留并被脚本标为 warning。
- 回滚锚点：当前未创建 commit/tag；本轮是文档/治理文件变更，待用户决定是否纳入后续原子 commit。若需要撤销，只回退本轮新增文件和检查点，不触碰既有 lock 修改。
- 未决：用户需要决定首个 Exo 实施阶段（建议从 E1/C0-E 或更小的获批子阶段开始），以及是否授权为该阶段创建基线 commit/annotated tag。
- 下一步：收到明确阶段授权后，按 Skill Phase 4 重新核对远端头、建立阶段基线、测试预算、样片清单和可执行回滚，再实施第一逻辑单元。

## 检查点 46：2026-08-22 DV7 播放现场根因定位

本检查点只记录日志与源码诊断，不修改播放器代码、依赖 lock、AAR、APK、`.so` 或网络超时参数。证据来源为本地日志 `/Users/macbookpro/Downloads/webhtv-debug-log (17).txt`；本轮禁止联网。

### 46.1 MPV：DV7 不应因硬解能力不足退出 Surface Direct

**现场结论：** 当前行为是输出策略错误，不是设备被迫切 GPU。启用 DV7 HDR10 fallback 时，正确路径应是“保留硬解 + `mediacodec_embed`/Surface Direct + native demux BL-only HDR10”，而不是“`LEAVE_SURFACE_DIRECT` + GPU”。

**源码根因：**

1. `app/src/main/java/com/fongmi/android/tv/player/mpv/MpvAutoOutputPolicy.java:24-30` 对所有 DV profile（包括 profile 7）在 `DolbyVisionSupport.UNSUPPORTED` 时返回 `eligible=false`。
2. `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:4718-4734` 将该结果标记为 GPU pinned，并依据 `eligible=false` 请求 `rebuildAndRestartMpv(false, ...)`，因此从 Surface Direct 退出。
3. `app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:745-747` 虽然设置了 `demuxer-dovi-profile7=hdr10`，但该选项只影响 native packet 处理，发生在输出路径已经选定之后，无法阻止上面的策略切换。
4. `third_party/patches/mpv-dovi-profile7-hdr10-base-layer.patch` 的设计本身是过滤 EL/RPU、保留 BL、交给 MediaCodec 并直出 Surface；它没有要求 GPU。

**日志闭环：** 日志第 256、281 行记录 `auto-dolby-vision-hw-unsupported`、`old=surface-direct target=gpu`；第 338-339 行记录重建后 `transition=KEEP_GPU`、`direct=false`。因此“切 GPU”是 App 策略的确定性结果。

**待实施修复（本检查点未实施）：** 将“DV7 profile + 已启用 HDR10 fallback”建模为独立的可直出决策（例如 `dolby-vision-hdr10-fallback`），保持 Surface Direct；保留 profile 5/原生 DV、用户关闭 fallback、滤镜/LUT/字幕等真正需要 GPU 的分支。补充 `MpvAutoOutputPolicy` 的 fallback/transition 单测，并验证 native `demuxer-dovi-profile7=hdr10` 实际生效。不能只把 `eligible` 改成无条件 true，也不能把 GPU 诊断名称继续当成 HDR10 fallback 证据。

### 46.2 Exo：首个失败是 P8.1 硬解运行时错误，网络超时是后果

**网络不是首因：** 切换 Exo 时 local proxy 在日志第 876 行约 5 ms ready；随后多次连接 `127.0.0.1:1314` 成功并返回 `206 Partial Content`（第 933-946、1341-1364 行）。在播放器进入 `IDLE`（第 1459 行）之后，第 1465 行才出现 `InterruptedIOException`，这是停止播放器取消正在读的请求，不是连接建立失败。不要先增大连接/读取超时。

**首个失败链：**

1. 第 942 行：`source=dvhe.07.06` 被静态能力判断为 `sourceHw=false p81Hw=true`，锁定 DV7→P8.1。
2. 第 947、970-971 行：访问单元转换成功并仍含 `rpu:1`，输出标记为 `locked=P8.1`；第 964 行 `c2.mtk.dvhe.st.decoder` 初始化成功。
3. 播放约 13 秒后第 1283、1291、1294-1299 行出现 `CodecException`、`ERROR_CODE_DECODING_FAILED`、`firstFrames=0`。
4. 第 1387 行排除 DV decoder 后，第 1393 行重试使用 `c2.mtk.hevc.decoder`，但第 1399-1400 行仍显示 P8.1 转换输出含 `rpu:1`。

**Exo 根因分两层：**

- **能力误判：** `DolbyVisionP81ExtractorsFactory.shouldConvert()` 只用 `MediaCodecInfo.isFormatSupported()` 判断 P8.1。该 API 证明的是声明的 MIME/profile/尺寸能力，不能证明 MTK decoder 能接受实际重写后的 CSD、RPU 和访问单元；日志已经证明 `p81Hw=true` 与运行时失败可以同时成立。
- **fallback 语义错误：** `DolbyVisionHdr10FallbackRenderer.asHdr10()` 只把 renderer 配置的 MIME/codecs/color 改成 H.265/HDR10；它没有剥离 P8.1 访问单元中的 RPU/EL，也没有把 Dolby Vision CSD 变成纯 HEVC CSD。于是 generic `c2.mtk.hevc.decoder` 并不是真正的 HDR10 base-layer fallback，不能用“换了 decoder”证明 HDR10 已降级。

**A1-2 的责任边界：** 当前实现提交为 `9306df6afa3d20514764fb8e3ccda08c147e8ffc`，基线为 `9f946cfb003e721c2c36dde1a197c4ce86422cee`，上游参考为 FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`，恢复 tag 为 `recovery/exo-a1-2-dv-csd/20260822103334-9306df6afa3d`。A1-2 改写了 P8.1 Dolby Vision CSD；本日志没有记录实际 CSD 字节，因此不能仅凭一次现场日志断言它是直接诱因。必须用同设备、同资源、同位置（约 `114747 ms`）对 A1-2 前后版本做最小 A/B：首帧、`c2.mtk.dvhe.st.decoder` 是否报错、实际 CSD/codec、`exo-dv` AU 统计和 30 秒内错误结果。

**下一实施顺序：** 先定义并实现真正的 Exo HDR10 fallback（过滤 RPU/EL、纯 HEVC/HDR10 CSD、seek/flush 后保持一致），再决定是否保留 P8.1 runtime 尝试；失败后应进入该 fallback，而不是把 DV P8.1 流直接交给 generic HEVC。A/B 证明 A1-2 为直接诱因时，单独回滚或改 CSD；若前后都失败，则修复能力误判和 fallback 数据路径。网络层只做取消/错误分类核对，不作为本故障的主修复。

### 46.3 当前决策边界

- MPV：需要修正本地输出策略；问题与上游 FFmpeg C2 是否合并无关，先修 App gate，再做 native 回归。
- Exo：根因已定位为“P8.1 静态能力误判 + 非真正 HDR10 fallback”；在 A/B 前不改网络超时、不擅自回滚 A1-2。
- 本检查点没有新的代码 commit/tag；后续代码实施必须重新建立阶段基线并按本文件记录原子 commit 与恢复 tag。

## 检查点 47：2026-08-29 P2-2 DV7 metadata/codecpar 实施验收

- P2-2 已按批准的窄适配完成：仅修改现有 DV7 HDR10 base-layer fallback 的 metadata-missing 检测、BSF 错误传播、`par_out`→codecpar 同步、`dv_el_present=false` 和 `INT_MAX` 边界；没有升级 lock、FFmpeg/libplacebo/mpv-android、JNI 或 Android EL 策略。
- 证据：完整双 ABI native build/install 通过；`scripts/verify_mpv_native_assets.sh --require-elf` 对 `arm64-v8a`/`armeabi-v7a` 通过；Mobile arm64 Debug APK 构建通过且 APK 内 MPV assets 与工作区一致；USB 设备 vivo `V2453A`（`10CF6H1D2L0009S`）安装并启动成功；用户确认 DV7 及邻接播放验证通过。
- 保护：两份 `libplayer.so` 字节不变；`AGENTS.md` 和构建生成的 `app/.cxx/` 不属于任务提交范围；回滚锚点为 `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/baseline-20260829-0342`。
- 当前状态：本检查点的实施验收已由检查点 48 完成 commit/tag 闭环；C2 仍暂缓，下一 MPV 阶段按队列评估 P3。

## 检查点 48：2026-08-29 P2-2 commit/tag 收尾完成

- P2-2 原子实施提交：`ba47756d7e463abeb9377088b819a2520e150935`。
- 恢复 tag：`recovery/P2-2-MPV-DV7-METADATA-CODECPAR/20260829065811-ba47756d7e46`，在提交成功后立即创建，tag 阶段耗时 0 秒。
- 通过证据：双 ABI native build/install、`verify_mpv_native_assets.sh --require-elf`、Mobile arm64 Debug APK `8c3f1d569d5b325d1e577a5a77d196c800c167159d76f313adc2c3bb02049a4c`、APK asset identity、USB V2453A 安装/启动，以及用户确认的 DV7 与邻接播放验证。
- 任务状态：P2-2 已完成并关闭；不改变任何 lock、FFmpeg/libplacebo/mpv-android revision、JNI API、`libplayer.so` 或 Android EL 策略。下一 MPV 阶段为 P3，需单独评估/批准。

## 检查点 49：2026-08-29 P3 AudioTrack 实施与当前设备验收

- P3-2 已按批准的窄适配完成：保留锁定树的 carrier rate、本地全 API TrueHD 7.1 workaround 和 PCM fallback，只为 Android 12+ 的 DTS-HD MA 8-channel carrier 增加 7.1 mask，并让 App probe 与 native 使用一致的 rate/mask 条件。
- 构建证据：Java 定向单测、完整 patch-stack `--prepare-only`、双 ABI native build/install、`verify_mpv_native_assets.sh --require-elf` 和 Mobile arm64 Debug APK 均通过；APK SHA-256 为 `ad57a1d453281f21921f5898724966aae61aab5c8574537c7729afbc82da39b8`。
- 资产证据：`libmpv.so` SHA-256 为 `04cfe3ae40118ec77b988323791925b67014b7dda33fdbe54848db8ff219c9a5`（arm64）和 `d13da6308db9c6d1757a8c71928284efd83e9de82a14e5465293fc297ab2cc75`（armv7）；两个 `libplayer.so` Git blob 与基线 HEAD 一致，lock/JNI/Exo/FFmpeg/libplacebo/mpv-android revision 均未改变。
- 实机证据：vivo `V2453A`、Android 15/API 35 上，DTS-HD MA 5.1/7.1、TrueHD 7.1、E-AC3/Atmos 7.1.4、AC3 5.1 和 LPCM 7.1 均进入播放；pause/resume 和 seek 保持对应 AudioTrack 会话，最终活动轨道 underruns 为 0，App PID 未变化，未出现 crash/ANR/native fatal/AudioTrack init failure。
- 限制：当前输出设备是手机扬声器，只能证明多声道 PCM fallback 与生命周期稳定；DTS-HD HRA 实机、API 29/30、HDMI/ARC/eARC/USB 原码直通、AVR 格式显示和 route hotplug 仍需对应设备，不能由本轮结果代替。
- 当前状态：P3 已达到当前可用设备的发布门槛，等待 task guard 原子提交和 annotated recovery tag；详细记录与回滚边界见 [P3-mpv-audiotrack.md](P3-mpv-audiotrack.md)。

## 检查点 50：2026-08-29 P3 commit/tag 收尾完成

- P3 原子实施提交：`d82336bde585b62af43771284075a0a94a3d999e`。
- 恢复 tag：`recovery/P3/20260829094014-d82336bde585`，由 task guard 在提交成功后立即创建，tag 阶段耗时 0 秒。
- 提交范围仅包含 P3 的 Java capability probe/单测、MPV AudioTrack patch、双 ABI `libmpv.so`、构建/验证契约和对应文档；`AGENTS.md` 保持为用户既有未提交改动，`libplayer.so`、locks、Exo、FFmpeg、libplacebo、mpv-android revision 均未改变。
- 当前状态：P3 已完成并关闭。手机扬声器路由上的 PCM fallback 与生命周期验证通过；HDMI/ARC/eARC/USB 接收器上的 DTS-HD HRA/MA、TrueHD 原码显示和 route hotplug 仅作为后续硬件证据补充，不重新打开或扩大 P3 代码范围。

## 检查点 51：2026-08-30 C2 转换 P8.1 零输出与上游复核

- 当前电视证据表明 C2 的失败顺序是 MediaCodec 零输出/释放在先、Java 主线程同步属性阻塞在后；`reader-pts`、`chapter-list` 和 `current-tracks` 只会放大 native 失败并阻止定时回退，不能作为码流根因。
- 离线 packet 对照已否定重复 RPU 与 RPU NAL 顺序假设：P7 原始区间和转换 P8.1 每个 packet 均只有一个 RPU，转换输出已删除 EL；正常原生 P8.1 与转换 P8.1 的 RPU 都位于 VCL 之后。详细样片、数量和日志路径见 [C2-dv7-p81-bsf.md](C2-dv7-p81-bsf.md)。
- `FongMi/FFmpeg` 的重落基提交 `86b827daa9401f781f8660ea511a2cae0baa2833` 与锁定 `177f090e0503b7e013922ca903bde14b1c375f18` patch-id 相同；当前头 `5e6ba5e987284d8ecb6dc25d2d3fd45d309f3fdd` 仅公开 RPU parser，没有本故障修复，处置为“不升级、不移植”。
- `FongMi/mpv` 的 `c318236b8882af860f16f936225430ad053a2179` 处理缺失独立 EL stream 的 pairing，`e8673660ab7ee5d4ea8f93e4bf3a6e170ab2a19a` 处理 GPU peak-detection metadata；`FongMi/mpv-android` 的 `e1a1f75106afefa6fb3ec9aa6c9ca081155486dd` 只导出 renderer SDK。三项均不覆盖 C2 转换流到 Dolby MediaCodec 的零输出，处置为“本故障忽略，按各自任务另行评估”。
- 最新 MPV trace `p-dg4unu-1` 显示自动模式实际为 `surface/mediacodec_embed`，不是 OpenGL GPU；`FILE_LOADED` 后 MediaCodec 先进入 `Uninitialized` 并无法 dequeue output，约 3 秒后 `reader-pts` 才开始长时间阻塞。P8.1 info 日志已请求，但 Java 筛选遗漏 `Dolby Vision profile`，而本应放行的 `MediaCodec started successfully` 没有出现。
- C2 下一步只放行一次 P8.1 Dolby profile 初始化诊断，采集最终 profile 与厂商 codec 结果；在该证据前不修改锁定版本、BSF、CSD、硬解、Surface、Vulkan/OpenGL 或回退策略。

## 检查点 52：2026-08-30 C2 厂商 Dolby decoder 释放顺序确认

- 最新有效 trace `p-dhezv2-1` 已确认转换结果被识别为 Dolby Vision profile 8，且 `c2.mtk.dvhe.st.decoder` 启动成功；厂商 decoder 在无首帧时主动进入 `Released`，随后才出现 output dequeue 失败和主线程 `reader-pts` 长时间阻塞。
- 因此错误 renderer、错误 MIME/profile/codec 和 Java 同步查询均不再作为码流首因。完整证据与时间线见 [C2-dv7-p81-bsf.md](C2-dv7-p81-bsf.md)。
- 下一单元只验证转换输出残留的 `AV_PKT_DATA_HEVC_CONF`：该 side data 表示已被删除的增强层配置，FFmpeg 自带 `dovi_split` 在输出端会明确删除。验证不得同时改变 RPU、extradata、packet、Surface、GPU、解码器选择或回退策略；若电视仍无首帧，则否定该假设并转向 FEL RPU 重写兼容性。

## 检查点 53：2026-08-30 dev2 beta 覆盖与 fongmi-sync 合并

- 用户目标：以 `origin/beta` 最新头 `8c515e4cce1e0e4596e3e0884a3ab8a37ed117a0` 覆盖 `dev2` 工作基线，再合并 `fish2018/webhtv:fongmi-sync` 当前头 `4489ca9ecc91c2c30fd23610cb0342aa1224717b`。
- 恢复锚点：旧 `dev2` 提交链保存在本地分支 `backup/dev2-before-beta-overwrite-20260830`，beta 基线保存在 `backup/dev2-beta-baseline-20260830`；既有 E7-2 工作继续保留在原有 `stash@{0}`、`stash@{1}`、`stash@{2}`，未应用到本次合并。
- 合并状态：已执行 `git merge --no-commit --no-ff fish2018/fongmi-sync`；8 个内容冲突已完成组合解析，保留 beta 的缓存/FFmpeg/音频历史/seek/telemetry 逻辑和 fongmi-sync 的 DV7/P8.1、Vulkan、Surface teardown、Exo DV5 renderer 及最新治理记录；暂存树为 238 个文件、37231 行新增、812 行删除，无未合并路径或冲突标记。
- 验证：`git diff --cached --check`、全仓冲突标记检查和 task guard 检查已通过；Gradle 任务列表探测曾在 120 秒后超时，尚未替代 Java 编译结论。
- 回滚：放弃未提交合并可回到 `backup/dev2-beta-baseline-20260830`；旧 dev2 工作链回到 `backup/dev2-before-beta-overwrite-20260830`；本地 stash 不删除。
- 验证更新：`:app:compileMobileArm64_v8aDebugJavaWithJavac` 已通过，结果为 `BUILD SUCCESSFUL`；仅有仓库已有的 32 位 `armeabi-v7a` native library warning。
- 下一动作：运行一次 `:app:assembleMobileArm64_v8aDebug` native/CMake 联合验证；通过后创建 dev2 合并提交和本地恢复 tag，不推送远端。

## 检查点 54：2026-08-30 dev2 合并 Java 编译通过

- 完成：在未提交的 `dev2 <- beta + fish2018/fongmi-sync` 合并树上运行 `:app:compileMobileArm64_v8aDebugJavaWithJavac`，退出码为 0，Gradle 报告 `BUILD SUCCESSFUL in 1m 38s`。
- 结果：合并后的 Java/API/资源生成链通过；CXX5202 仅提示 `armeabi-v7a` 32 位 native library，与本次 Java 编译失败无关。
- 验证更新：`:app:assembleMobileArm64_v8aDebug` 已通过，退出码为 0，Gradle 报告 `BUILD SUCCESSFUL in 4m 51s`；同样仅有 CXX5202 32 位 native library warning。
- 回滚锚点：`backup/dev2-beta-baseline-20260830`、`backup/dev2-before-beta-overwrite-20260830`；合并源 `4489ca9ecc91c2c30fd23610cb0342aa1224717b` 保持不变。
- 未决：尚未创建合并提交。
- 下一动作：创建 dev2 两父合并提交并创建 `recovery/dev2-beta-fongmi-sync-20260830/<timestamp>` 本地 annotated tag，不推送。

## 检查点 55：2026-09-03 E-SP7 Exo H.264 自适应选轨实施启动

- 用户已批准实施 E-SP7；唯一任务文档为 [E-SP7-exo-avc-adaptive-selection.md](E-SP7-exo-avc-adaptive-selection.md)。
- 当前基线：分支 `dev4`，HEAD `59fd2688f79d4e6ef46da23a162c8236920629e6`，实施前工作区无脏文件。
- 根因：本地 `1536c1bcc8d409d6f2479764a8fee20c45fd1fc8` 在受约束 `applyVideoLimit()` 中启用 `setForceHighestSupportedBitrate(true)`；上游 `fish2018/webhtv@ec478b0b697422a7785171c7b51a35b7a526564e` 和 AndroidX Media3 `release@2bc207851df311340767e913931ca7b28cab1794` 均支持恢复自适应选轨。
- 范围：仅 `ExoUtil.java`、`ExoUtilTest.java`、E-SP7 任务文档和本索引；不改 AAR、lock、FFmpeg、MPV、native 或其他播放器。
- 当前状态：task guard `E-SP7/upstream` 已启动；下一动作是先运行修改后的 `ExoUtilTest` 证明旧源码不满足新回归断言，再修改生产代码。

## 检查点 56：2026-09-03 E-SP7 Exo H.264 自适应选轨修复完成

- 实现：`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java` 的受约束 `applyVideoLimit()` 已将 `setForceHighestSupportedBitrate(true)` 改为 `false`；无轨道限制分支未改变。
- 测试：先红后绿的 `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoUtilTest --no-daemon` 通过，绿灯执行记录为 `BUILD SUCCESSFUL in 58s`；共 16 项 ExoUtilTest，无失败。
- 编译：`:app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon` 通过，执行记录为 `BUILD SUCCESSFUL in 57s`。
- 范围：仅 E-SP7 源码、测试、任务文档和本索引；没有修改 Media3 AAR、lock、FFmpeg、MPV、native、JNI 或其他播放器。
- 当前基线：分支 `dev4`，实施前 HEAD `59fd2688f79d4e6ef46da23a162c8236920629e6`；真实 Dangbei X7 Ultra 同资源 A/B 尚未执行，设备端掉帧改善仍待用户/设备证据确认。
- 下一动作：完成限定范围安全检查后，由 task guard 创建 E-SP7 原子提交和 annotated recovery tag。

## 检查点 57：2026-09-03 播放差异只读审计收口

- 审计基线：`dev4@a228e988d488f178890b64592f9dd89761f8e011` 对比 `fish2018/webhtv:main@ec478b0b697422a7785171c7b51a35b7a526564e`；工作区干净。
- 结论：在当前源码证据下，没有发现第二个与 E-SP7 同级别、可直接判定的单点回归；本地差异主要为有意叠加的播放能力、稳定性保护和 native 补丁链。
- 需要继续观察的分叉：Exo FFmpeg 兜底与降载、Exo PreCache worker 生命周期、Exo 音频采集管线、MPV 自动直出保护、IJK 首帧 watchdog 和依赖/锁文件链。
- 验证边界：本轮只做静态 diff、符号调用路径和默认值核对，未执行实机 A/B 或 native 行为复测。
- 下一动作：按用户指示先合并最新 beta，再复核 E-SP7 合并树，随后输出其他播放差异的同步意见表。

## 检查点 58：2026-09-03 beta 合并与 E-SP7 复核

- 合并基线：`dev4@ff438637b89587cf4f378843338a4122ba07e9d3` 合并 `origin/beta@bcfe7b22a05e32913448a228f9513c690bc8233f`，共同基线为 `59fd2688f79d4e6ef46da23a162c8236920629e6`；仅评估索引发生内容冲突，已组合保留 E-SP7 与 C9 两行。
- E-SP7：`a228e988d488f178890b64592f9dd89761f8e011` 的源码与测试未被 beta 覆盖；合并后定向 ExoUtilTest 通过，Mobile/Leanback Arm64 Java 编译通过。
- beta：`IntroSkipServiceTest` 22 项与 `VideoActivityLayoutTest` 153 项共 175 项通过，失败/错误/跳过均为 0；片段身份、速度键释放和移动详情页外层滚动改动未发现需阻断提交的问题。
- 结论：允许提交并推送当前合并树；C9 仍需保留真实设备播放和 OEM 行为作为后续补验，不把 Java/单测结果扩大为设备端完全验收。
- 下一动作：task guard finish 创建两父合并提交和本地 annotated recovery tag，然后推送当前 `dev4` 与该新 tag。
