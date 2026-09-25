# C18：dev4 合并 beta 最新代码与合并后复评

> 状态：已完成。`origin/beta` 已合并到本地 `dev4`，唯一冲突已按本地跟随功能与 beta 配置事件监听合并；双端编译与移动端全量单测通过，复评未发现需追加修复的问题；提交、恢复标签、推送、PR #331 和远端拉取均已完成。

## Recovery anchor

- 目标：将 `origin/beta@b4501c91c748cc5b910b5dc0ab7553e513f7fa8c` 合入 `dev4`，复评全部本地未推送改动（重点为 FOLLOW-1 关注更新和 C16 订阅凭据边界），发现真实问题即最小修复并重新验证，循环复评直到通过；随后提交当前任务改动、推送 `dev4`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
- 本地基线：`dev4@ea485ee9bfff330345adf1f565ea703a508d4cc9`，相对 `origin/dev4` 领先 46 个提交；任务开始时工作树干净。
- 合并基线：共同祖先 `552b68bb8e781d06ca989abfd8caa96a567e494a`；目标 beta 头 `b4501c91c748cc5b910b5dc0ab7553e513f7fa8c`。
- 冲突：仅 `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` 的 import 区域冲突；保留本地 FOLLOW-1 全部 import 与 beta 新增 `ConfigEvent`。
- 范围：`app/**`、`docs/**`、`gradle/**`、`gradlew`、`scripts/**`、`third_party/**`；不修改依赖锁、播放器二进制或公开协议，除非复评证明合并必须处理。
- 回滚：最终合并提交以本地基线为第一父；如需回滚使用 `git revert -m 1 <merge-commit>`。本地 46 个提交及各自 recovery tag 保持不变。
- 下一动作：无；等待 PR #331 的正常评审与合并。

## beta 增量账本

共同祖先以来 beta 侧面包含 30 个非合并提交，主要行为提交及完整 ID 如下：

| 完整提交 ID | 内容 | 当前处置 |
| --- | --- | --- |
| `a50de802a1c2c3491088fa0307c0484bc95e7f1e` | TMDB 凭据限定到订阅范围 | 已合入并复评 |
| `7cc55beae44c891e01c0cbc5c4ea2ffee844cb3f` | 激活订阅源 TMDB 凭据 | 已合入；与本地 FOLLOW-1 接线冲突已解决 |
| `73abd0350b9808c5da727763deff3764e590d970` | 按能力缺口补齐源详情 | 已合入并复评 |
| `d2136e463391a6c34a4bf042d85cc001982cd001` | 凭据脱敏与验证 | 已合入并复评 |
| `6d9835298550ad5d3623d3e4fec14ddc518b7e8c` | 广告日志分列与列筛选 | 已合入；与本地 FOLLOW-1 不重叠 |
| `fd593f3d3c3972811c7572ca92342b78f16b9fb3` | 订阅凭据设备验证 | 已合入文档与测试 |
| `b51a3795e9c88d2f370271f64513e5928d6e067f` | 接受订阅配置根对象 Key | 已合入并复评 |
| `bc23329e49f050ce95142f102aa090b9520769e3` | 广告日志去重多选筛选 | 已合入；与本地 FOLLOW-1 不重叠 |
| `9cd5c6399dac79411354c1be104d28a826e37127` | 认证失败清理限定订阅 epoch | 已合入并复评 |
| `cd062e32428d4855899308503b34ceb584c9fe09` | 移动端广告筛选单击选择 | 已合入并复评 |
| `8cab95c225c81798a308931e732f326bf460222d` | MPV HLS 去广计划就绪通知 | 已合入；与本地 FOLLOW-1 不重叠 |
| `2456a3af52eb2849ca8aa9d2da98ed5f3180213d` | Leanback 背景与对话框统一主题 | 已合入并复评 |
| `27313a3af01b9b17aa48dfe4f0fda64040c6ba83` | Leanback 跟随活动主题颜色 | 已合入并复评 |
| `a1aad8745e8cf81b566016369f66a5496a6198e3` | 更新对话框焦点归一化 | 已合入并复评 |

其余文档提交、beta 同步合并和 PR 合并提交作为结构提交保留；最终树以 `git rev-list` 与回归结果核验，不以提交数量替代行为验证。

## 首轮复评

- 保留本地 FOLLOW-1 改动和 beta `ConfigEvent` 配置刷新路径；删除三处冲突标记，未改变两个功能的运行逻辑。
- `TmdbDetailActivity` 合并后同时保留 FOLLOW-1 import、beta 的 `onConfigEvent`、有效配置刷新和 `loadContent(null)` 重载。
- C16 订阅凭据边界继续按本地已 hardened 版本评估：实际加载参数绑定订阅、同订阅 Key 替换推进 epoch、畸形 JSON 不回传原文、请求前校验临时凭据仍属于当前订阅。
- 广告日志列筛选、MPV 通知、Leanback 主题和更新对话框焦点属于 beta 的独立行为，未覆盖本地 FOLLOW-1 或 C16 已提交改动。

## 验证记录

- `git diff --check` 与 `git diff --cached --check`：通过；无冲突标记和未合并路径。
- 双 flavor Java 编译：`bash ./gradlew --console=plain :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac`，`BUILD SUCCESSFUL in 51s`。
- 移动端全量 JVM 单测：`bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest`，`BUILD SUCCESSFUL in 42s`；4745 项测试、0 failure、0 error、1 skipped。

## 交付记录

- 合并提交：`9f998d6808f3e8144d2d4d68f23d92308e49b599`，第一父 `ea485ee9bfff330345adf1f565ea703a508d4cc9`，第二父 `b4501c91c748cc5b910b5dc0ab7553e513f7fa8c`。
- 恢复标签：`recovery/C18-beta-sync-review-dev4-20260920/20260920160703-9f998d6808f3`。
- 推送：`origin/dev4` 已从 `dcfd78749bf4` 更新并包含合并提交 `9f998d6808`；收口文档提交会作为分支末端同步推送。
- PR：<https://github.com/Silent1566/webhtv/pull/331>，目标分支 `beta`，当前状态 `OPEN`、非草稿、`MERGEABLE`、`mergeStateStatus=CLEAN`。
- 远端同步：推送并创建 PR 后执行 `git fetch --prune origin beta dev4` 和 `git pull --ff-only`，结果为 `Already up to date.`，当前 `dev4` 与 `origin/dev4` 一致。
