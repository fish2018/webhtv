# dev3 合并 beta 最新代码与 C16 交付前复评（2026-09-19）

## 目标

合并远端 `beta` 最新代码，评审 `dev3` 中已提交但尚未推送的 C16 改动；发现问题后修复并验证，完成第二轮复评，再提交、推送、创建目标为 `beta` 的中文拉取请求，最后回拉远端最新代码。

## 基线与范围

- 工作分支：`dev3`
- 评审起点：`c2b1907c3ba7e732a6d4378ec3360502b3d115e4`
- 远端合并基线：`origin/beta@ca62fe6f5b97a2c0d4e20390ec2b25b7456e83aa`
- `git rev-list --left-right --count origin/beta...HEAD` 结果：`0 9`。远端 `beta` 已是当前 `HEAD` 的祖先，本轮没有新的 beta 增量、冲突或覆盖风险。
- 任务守卫：`beta-sync-review-dev3-20260919`，模式 `standard`，范围 `app/**`、`docs/**`。
- 起始工作树干净，无受保护脏路径。

## 已提交未推送提交

| 完整 commit ID | 处理结论 |
| --- | --- |
| `0bf950d7216fcc581e02198b5639348f965c97f2` | C16 无 Key 源内嵌详情设计与验收合同，保留。 |
| `19128b7d18107b5c614078ebdadc863faa2476b5` | 运行时模式与源数据可用性策略，保留。 |
| `1c4213db0baf24e9da9495edfdbaa9dcab43f527` | 设置层解除 API Key 门禁，保留。 |
| `6a994fa1acd85cd307bf111bc077f6e5a1385da0` | 独立详情页三模式路由和无源回退，保留。 |
| `b2343d1d1de476226b52bef11b80248e1d5033d9` | Mobile/Leanback 原生增强 source-only 接线，保留。 |
| `6e2cde565ae34c1a0da392c7ca93f34cb97aaa99` | source-only 交互入口和空区域收口，保留。 |
| `d52fdfc5b2027872779026bad278c1a5c8e9f5d8` | V1923A 设备验收测试，保留。 |
| `f585fe06a935211c8146b7242f528eb664159c4e` | 无 Key 网络任务断言修正，保留。 |
| `c2b1907c3ba7e732a6d4378ec3360502b3d115e4` | C16 实施索引收口，保留。 |

## 第一轮评审发现与修复

1. `TmdbUIAdapter` 的 source-only 会话只隐藏了个性化推荐入口，但播放进度产生的 `HISTORY` 事件仍可能调用 `refreshPersonalRecommendations()`，从而启动 TMDB/豆瓣推荐请求。现已在适配器的相关视频、加载更多、个性化刷新和 AI 推荐入口增加 `sourceOnly` 防线；回调返回 `false`，不创建后台网络任务。
2. 内嵌 `videos` 没有进入相关视频列表：独立详情页原本遇到 source-only 直接返回，原生增强页的适配器也只在配置就绪时发起网络聚合。现在 source-only 改由 `TmdbSourceAdapter.videos()` 从当前详情、季和集对象本地解析；无数据时保持空列表并隐藏区域。
3. `loadSource()` 原先无条件用 payload 的 `season_number` 覆盖播放 Intent 的显式季号。现仅在未显式指定季时采用 payload 季号，保持历史续播和指定集进入时的既有语义。

涉及文件：

- `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterSourceOnlyTest.java`
- `app/src/test/java/com/fongmi/android/tv/ui/activity/TmdbDetailSourcePayloadTest.java`
- `app/src/test/java/com/fongmi/android/tv/ui/activity/TmdbDetailSourceOnlyWiringTest.java`

## 验证

- `git diff --check`：通过。
- Mobile 与 Leanback Arm64 Debug 定向 JVM 测试：`TmdbUIAdapterSourceOnlyTest`、`TmdbDetailSourcePayloadTest`、`TmdbSourceOnlyInteractionTest`、`TmdbDetailSourceOnlyWiringTest`、`TmdbSourceAdapterTest`、`DetailRuntimeModePolicyTest`、`TmdbSourceAvailabilityTest`，`BUILD SUCCESSFUL`。
- V1923A、Android 9、`192.168.50.3:5559` 设备矩阵：Mobile 和 Leanback 各执行 `C16KeylessSourceDetailDeviceTest` 3 项，均为 `3/3` 通过，无 Key 提示，旧源回退正常。
- 设备验证使用 `/tmp/c16_keyless_fixture.py` 和 `adb reverse tcp:18080 tcp:18080`，结束后已停止夹具服务。

## 第二轮复评

- source-only 的播放、选集、推荐、相关视频、评分和设置入口均按 `networkAllowed` 或适配器 `sourceOnly` 收口。
- 有 Key 路径仍保留原有 source-first、缺失能力补齐、分页和个性化推荐行为；本次新增分支不会改变其调用顺序。
- 本地视频解析只读取已经通过 C16 协议校验并保存在 `TmdbBundle`/详情 JSON 中的数据，不扩大协议、依赖、ABI、缓存格式或权限范围。
- 显式季号优先规则与普通 `load()` 的既有语义一致，避免 source-only 回退映射错季。
- 未发现新的正确性、兼容性、性能、作用域或回滚问题，复评通过。

## 回滚

代码级可回滚 `TmdbUIAdapter` source-only 网络防线、本地视频分支与季号优先级修改；原 C16 阶段 A-F 提交及各自 recovery tag 保持不变。本任务提交由 `task_guard.sh finish` 生成独立 recovery tag。

## 2026-09-19 第二轮：合并 beta 2f057dc3

### 新远端增量

- 二次执行 `git fetch --all --prune` 后，`origin/beta` 已由 `ca62fe6f5b97a2c0d4e20390ec2b25b7456e83aa` 前进到 `2f057dc32e9eec9da970b8cee5942e0e1c7e65b2`。
- `origin/beta` 相对本地 `dev3` 新增 12 个提交，内容为 PR322 的直播源故障自动 fallback：线路优先切换、线路耗尽后切换下一直播源、解析失败和播放 HTTP 错误进入 fallback、缓冲超时取消、运行时配置初始化和 Mobile/Leanback 对称实现。
- 本地 `dev3` 相对 `origin/beta` 保留 1 个提交 `fa48fe5f88387133c6bf55789f7901f8ee783727`；其内容为长按选集详情优先使用卡片绑定的 `TmdbEpisode`，在无 Key、映射无效或详情 API 失败时不再错误地回退到 `Episode.getTmdbEpisode()`。此前本地 `7f5379cd73c7ff63142c102426d3453d79fc5440` 已包含在同一 `beta` 历史中。
- 使用 `git merge --no-commit --no-ff origin/beta` 合并，自动合并成功，无冲突；最终提交保留 `dev3` 与 `origin/beta` 的双父关系。

### 第二轮复评

- 直播 fallback 策略先尝试当前频道下一条线路，再在下一直播源存在时切源；没有下一直播源但仍有其他线路时继续尝试线路，二者均不可用时保持原失败状态，避免越界切换。
- `onSourceHttpError()` 仅在 fallback 开启时接管 HTTP 错误，关闭时继续沿用播放器原有重试链；`onError()` 在切换前取消旧缓冲超时，并公开清理播放器、轨道和错误会话状态。
- Mobile 与 Leanback 的 `startFlow()`、配置读取、设置开关、状态复位和生命周期清理保持对称；新增资源、布局与 `LiveSetting` 默认值不改变 TMDB source-only 路径。
- TMDB 新重载允许调用方显式传入卡片绑定的 `TmdbEpisode`，原 6 参数 API 仅委托并保持兼容；所有长按详情返回路径都传递同一绑定对象，未扩大协议、缓存、依赖、ABI 或网络权限。
- 合并后逐项检查 source-only 网络防线、显式季号优先、本地视频解析和长按卡片绑定集号；未发现新的正确性、兼容性、性能、生命周期或回滚问题。

### 第二轮验证

- `git diff --cached --check`：通过。
- Mobile Arm64 Debug 定向 JVM 测试：直播 fallback 策略 5 项、源码契约 5 项、直播布局 9 项、TMDB 详情布局 127 项、剧集详情主题 9 项、source-only 适配/载荷/交互/接线 14 项、详情运行时策略 4 项、源数据可用性 7 项，共 184 项；均为 `failures=0`、`errors=0`、`skipped=0`。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL`，覆盖 Leanback 合并后的 Java 源码与资源绑定。

## 唯一下一步

使用当前任务守卫提交合并结果并创建 recovery tag，随后推送 `dev3`、创建目标为 `beta` 的中文 PR，最后执行一次远端回拉。
