# dev2 合并 beta 最新代码与复评记录（2026-09-20 第二轮）

## 目标与范围

- 目标：将 `origin/beta@2c0f4234d74ec120d4d0159b6ed539337b506b95` 合入 `dev2`，复评本地已提交未推送代码与合并后的完整差异，确认通过后提交、推送、创建到 `beta` 的中文 PR，最后重新拉取远端。
- 本地上次合并基线：`035145f9fd4e31998f2008f67b25247ed0419140`（PR #328）。
- 本轮额外修复提交：`255a8b5cbc6a873c3e1a449dfe9a87975309e035`，解决 MPV 轨道列表 NODE 快照未派生到属性缓存的问题。
- 合并方式：`git merge --no-commit --no-ff origin/beta`，自动合并成功，无冲突。
- 合并结果：相对 `origin/beta` 仅保留本轮 MPV 缓存修复的两个文件，因此合并没有回退 beta 的 FOLLOW-1、C18、E-SP9、广告拦截和播放器改动。

## 复评结论

- beta 增量中最关键的播放器改动为 `8cab95c225c81798a308931e732f326bf460222d`：MPV 在 HLS 去广计划就绪时提前通知。复核后确认其只对有效 removal plan 调用通知，统计仍只记录一次，未覆盖本地 HLS 广告时间线逻辑。
- beta 的 `BaseConfig` 快照修复、配置历史当前源隐藏、Exo 软解回退与手动解码模式契约均完整进入合并树。
- 本轮 MPV 修复与 beta 无重叠文件冲突，合并后仍保持 track-list NODE 到 `track-list/count` 和 `track-list/<index>/<field>` 的属性派生能力。
- 复评未发现新的阻断问题，无需追加修复。

## 验证

- `git diff --cached --check`：通过。
- Mobile Arm64 Debug Java 编译、Leanback Arm64 Debug Java 编译、Mobile Arm64 AndroidTest Java 编译：`BUILD SUCCESSFUL`。
- Mobile Arm64 Debug 全量 JVM 单测：4752 项，失败 0、错误 0、跳过 1；`BUILD SUCCESSFUL`。
- Leanback Arm64 Debug 定向 `MpvPropertyCacheTest`：通过。
- 本轮 MPV 修复定向测试：`MpvPropertyCacheTest` 4/4 通过。

## 状态与下一步

- 合并树已通过验证，下一步为原子提交合并结果并创建 recovery tag。
- 提交后推送 `dev2`，创建 base `beta` / head `dev2` 的中文 PR，最后执行远端 beta/dev2 拉取核对。
