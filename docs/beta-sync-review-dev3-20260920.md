# dev3 合并 beta 最新代码与两轮复评记录（2026-09-20）

## 目标与范围

- 目标：合并 `origin/beta` 最新代码，评审 `dev3` 全部已提交未推送改动（含本轮新改动），发现问题即修复并验证，复评通过后提交、推送 `dev3`、创建目标为 `beta` 的中文 PR，最后回拉远端最新代码。
- 分支与起点：`dev3@599f1c5260f115956833b6ea427e25c8eed750d3`，起始工作树干净，无受保护脏路径。
- 合并基线：`origin/beta@00dc2809f825698f5b08649c511ef03d29f32740`。
- 任务守卫：`beta-sync-review-dev3-20260920`，模式 `standard`；范围仅为本轮新提交涉及的三个文件。

## 远端 beta 合并结论

- `git fetch --all --prune --tags` 后，`origin/beta` 仍为 `00dc2809f825698f5b08649c511ef03d29f32740`。
- `git merge-base --is-ancestor origin/beta HEAD` 成立，即 `origin/beta` 已是当前 `HEAD` 的祖先，beta 最新代码已全部进入本地 `dev3` 历史，无需重复合并（重复合并只会产生空 merge commit）。
- 本地相对 `origin/beta` 仅领先一个提交 `599f1c5260f115956833b6ea427e25c8eed750d3`（TMDB 选集网格外边距对齐），即本轮唯一需要复评的未推送功能改动。

## 已提交未推送提交清单

| 完整 commit ID | 处置 | 说明 |
| --- | --- | --- |
| `599f1c5260f115956833b6ea427e25c8eed750d3` | 复评并修正 | 详情/播放页 TMDB 选集网格外边距对齐；本轮修复后重新验证。 |
| `00dc2809f825698f5b08649c511ef03d29f32740` | 已在 beta | dev2 合并 beta 与 MPV 轨道缓存修复（PR #336）。 |
| `5c79ad2841283e67c33691deca47a0eee33fe56b` | 已在 beta | dev2 第二轮 beta 合并复评记录。 |
| `255a8b5cbc6a873c3e1a449dfe9a87975309e035` | 已在 beta | MPV track-list NODE 快照派生属性缓存。 |
| `2c0f4234d74ec120d4d0159b6ed539337b506b95` | 已在 beta | dev4 修复配置历史当前源保护与删除确认（PR #335）。 |
| `6077339f3ce8e6e8c365b56c7fe26f2cc113c6ad` | 已在 beta | dev4 合并 beta 的 BaseConfig 快照修复。 |
| `cd565740ce01c92b13fa54c4283ca797645e711f` | 已在 beta | 配置历史当前源隐藏与删除二次确认。 |
| `64139076d1076743acd72aae3998b2594602bbd1` | 已在 beta | dev3 合并 beta 并修复异步加载配置引用漂移（PR #334）。 |

后七项均已位于 `origin/beta` 历史中，并由既有 dev2/dev4/C18/E-SP9 评审记录覆盖；本轮不重复执行相同模块的全量复评，仅确认其在最终树中的可达性与无冲突状态。

## 第一轮评审发现与修复

1. `TmdbEpisodeAdapter.applyCardSize()` 在绑定过程中通过 `holder.getBindingAdapterPosition()` 反查列号。RecyclerView 在 `notifyItemChanged` 等更新态下，holder 的绑定位可能与当前 `onBindViewHolder` 的 `position` 不一致，会导致首/末列外边距按错误列推导，出现左右不对称或首列带内缩。修复：把 `onBindViewHolder` 当前的 `position` 作为权威参数传入 `applyCardSize(holder, position, compact, pageHasTmdbEpisodeData)`，移除绑定期 holder 状态读取。
2. 原守卫测试只做正向文本断言，无法阻止后续重新引入 holder 状态读取。修复：新增 `!body.contains("getBindingAdapterPosition()")` 反向断言，并把新增 `position` 形参与调用点纳入断言。
3. 边缘数值复核：`gridSpanCount` 至少为 1，列号取自 `position % gridSpanCount`，因此 `gridSpacing * gridColumn / gridSpanCount` 不会除零；首列左外边距为 0、末列右外边距为 0，中间列按 `SpaceItemDecoration` 同款整数分配，卡片宽度保持等宽。

## 第二轮复评

- 逐项复核网格边距公式与 `SpaceItemDecoration(spanCount, spacing)` 的一致性：详情页 `gridSpacing` 与播放页 `12dp`/标准项 `8dp` 分支对应，首/末列贴齐内容边缘，列间间隔在整数分配下最多相差 1px，与既有播放页表现一致。
- 复核 `applyCardSize` 的其余分支未受影响：`layoutChanged` 仍只在尺寸或边距变化时调用 `setLayoutParams`，列表模式 `marginEnd` 仍是 `12dp`，底部边距与 scrim 高度逻辑未改动。
- 复核本次改动未触及协议、缓存格式、依赖、ABI、native、权限或网络路径，也未改变 beta 已合入的 FOLLOW-1、C18、E-SP9、广告拦截与播放器行为。
- 第二轮未发现新的正确性、兼容性、性能、作用域或回滚问题，复评通过。

## 验证

- `git diff --check`：通过（两轮修复后均通过）。
- `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapterTest`：`BUILD SUCCESSFUL`，15 项全部通过（首轮修复时先暴露并修正了 2 项陈旧签名断言）。
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：`BUILD SUCCESSFUL`，覆盖 Leanback 侧同源适配器编译。
- 覆盖安装实测：`bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 打包 `BUILD SUCCESSFUL`，`adb install -r` 成功（未卸载原包）。
- 覆盖安装后重新采集 UI 层级：详情页 TMDB 选集网格三行均为左外边距 `28px`、右外边距 `28px`，列间间隔均为 `21px`，卡片宽度 `450/450/450/451px`（1920px 宽，容器 `[28,91][1892,1080]`），与播放页 `SpaceItemDecoration` 的目标几何一致。
- 既有设备证据（提交 `599f1c5260` 消息记录）：同一设备上播放页 TMDB 选集网格同样为 `left=28px`、`right=28px`、间距 `21px`，详情页与播放页两侧对齐。
- 未执行：native 重建、全 ABI 矩阵、实机播放矩阵；上述证据不扩展为 ABI 或全机型验收声明。

## 回滚

- 本轮修复可单独回滚为 `599f1c5260f115956833b6ea427e25c8eed750d3`（仅 `TmdbEpisodeAdapter` 与其守卫测试），不涉及依赖、ABI 或数据迁移，回滚后即回到“零起点边距 + 单侧间隔”的上一版行为。
- 本任务提交由 `task_guard.sh finish` 生成独立 recovery tag。

## 状态与下一步

- 两轮复评与定向验证已通过，下一步为原子提交本轮修复与本文档并创建 recovery tag。
- 提交后推送 `dev3`，创建 base `beta` / head `dev3` 的中文 PR，最后执行一次远端回拉核对。
