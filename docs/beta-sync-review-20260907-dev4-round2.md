# dev4 合并 beta 最新代码与复评（2026-09-07）

## Recovery anchor

- **目标：** 合并 `origin/beta` 最新代码，复评当前分支全部改动（含已提交未推送的 TV 壁纸设置恢复），修复并验证阻断问题后提交、推送 `dev4`，创建中文 PR 到 `beta`，再拉取远端最新代码。
- **任务守卫：** `beta-sync-review-dev4-20260907-round2`，模式 `standard`，范围 `app/**`、`docs/**`、`gradle/**`、`scripts/**`、`third_party/**`。
- **基线：** `dev4@4425e9cb63186cfe41cacb1fd3982a23249a27e6`；开始时工作树干净，无受保护脏路径。
- **beta 目标：** `origin/beta@161b190fac6c304c240bfb3b142b4dfa531fb1d5`；共同祖先为 `8163e6fbbcca2187e3c816f96f378defa7cb0405`。
- **合并：** `git merge --no-commit --no-ff origin/beta` 自动完成，零冲突；当前索引为待提交的合并结果。
- **回滚：** 提交前使用 `git merge --abort`；提交后使用本任务生成的 `recovery/beta-sync-review-dev4-20260907-round2/*` 标签。

## 评审范围与结论

### 当前分支改动

- `a6eb0e96019da096b6e50aa8944270f911a2cdfa`：恢复 TV 主设置页外观入口的壁纸设置行。
- `4425e9cb63186cfe41cacb1fd3982a23249a27e6`：恢复壁纸选择、默认、刷新、历史入口，并保留 TV 外观弹窗入口。
- 最终相对 beta 仅有 `SettingActivity.java` 和 `activity_setting.xml` 两个生产文件差异。逐项核对了 ViewBinding ID、点击/长按监听、`WallConfig` 加载回调、`ConfigEvent.WALL` 刷新和手机版对应逻辑，未发现阻断问题。

### beta 增量处理

beta 自共同祖先以来的 64 个提交已按完整 40 位 ID 建立于 Git 台账；其中播放器音频/MPV/Exo、脚本按钮、实验室 ABI、外观主题、站点主题及冲突修复均已有此前 dev1/dev2/dev3/dev4 复评记录，且本轮最终树未改写其行为，按约定沿用已有验证，不重复执行相同场景。合并后确认 beta 删除了无调用方的 `ThemeDialog.java.backup`，最终树不存在 `.backup/.orig/.rej` 残留。

代表性新增集成提交：

| 完整 commit | disposition |
| --- | --- |
| `161b190fac6c304c240bfb3b142b4dfa531fb1d5` | beta PR #224 集成承载，已合并并复评最终树 |
| `f0e9ab75bd0770b17db9f9bcd6101026d2c63466` | beta PR #225 集成承载，已有 dev3 复评 |
| `7e6fd548190834408e40332e8392d2e379536511` | PR #224 冲突修复，已有 dev2 复评 |
| `fca898f9d940816c378ec37785184f46b334cd4d` | dev3 beta 合并复评，已有标题识别与编译证据 |
| `1f37b723f9ccdc02bc4cc44b6490d1c2cc547e2c` | PR #222 集成承载，已有 dev4 复评 |
| `0b3e06e4de61e17ebbe2ce53e1ed621d988fdc20` | PR #223 集成承载，已有 dev1 复评 |

完整 64 项范围以 `git log --reverse 8163e6fbbcca2187e3c816f96f378defa7cb0405..161b190fac6c304c240bfb3b142b4dfa531fb1d5` 为权威台账；每项均为已集成、已有评审覆盖或本轮最终树继承，无未处理的 beta-only 变更。

## 验证与最终复评

- `git merge --no-commit --no-ff origin/beta`：成功，无未合并路径。
- `git diff --cached --check`：通过。
- `bash .codex/scripts/task_guard.sh check`：通过，分支、HEAD、保护路径、范围和暂存路径安全。
- `bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --console=plain`：`BUILD SUCCESSFUL`（21 秒）。构建输出仅含已有资源命名空间、字符串格式和 Gradle 弃用警告，非本次改动引入的失败。
- 编译后再次复评合并最终树：TV 壁纸行可见且四个入口接线完整；外观入口继续指向 `AppearanceDialog`；未发现剩余阻断问题。

## 下一动作

执行 `task_guard.sh finish` 原子提交合并、当前 TV 改动和本评审记录并创建恢复标签；随后推送 `dev4`、创建中文 PR 到 `beta`，最后拉取远端最新代码。
