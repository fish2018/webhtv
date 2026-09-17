# dev2 beta 合并后复评：2026-09-13

## 范围与远端证据

- 分支/HEAD：`dev2` @ `db3af69a16ba10e69b78de78ee6e5997d62bf831`。
- 远端 `origin/beta` @ `0f6912a0c49b9c3ffd91e8e30be3c606b84bbcbb`；`git merge-base HEAD origin/beta` 为同一提交，`git rev-list --left-right --count 0f6912a0c4...origin/beta` 为 `0 0`。因此本轮没有 `beta` 侧新提交需要合入，也无合并冲突。
- 已提交未推送范围：`origin/dev2...HEAD` 共 3 个提交：
  1. `154e003520a751a19187057f103e1496c5197457` — `chore(binary): sync upstream arm64/armeabi mpv and binary assets`，36 个二进制资源。
  2. `7dc58af1b0bb28818b23f43748c3ac67f76e0449` — `merge: integrate C4 upstream source round 3 (2026-09-13)`，101 个源码/补丁/测试/布局文件。
  3. `db3af69a16ba10e69b78de78ee6e5997d62bf831` — `fix(mpv): observe Blu-ray menu active property`，MPV 属性注册与任务文档。
- PR 状态：`gh pr list --head dev2 --state all` 为空，无既有开放 PR。

## 评审覆盖与结论

- 二进制同步提交：commit 元数据记录 Arm64 debug 构建通过，armeabi 构建被与二进制差异无关的 Chaquopy TLS/network 阻塞；本地 `scripts/verify_mpv_native_assets.sh --require-elf` 复核通过，双 ABI MPV/FFmpeg 资产与锁定版本、ELF、打包规则一致。图标/favicon 为资源同步，无 Java/API 行为变化。
- C4 大合并：既有 `docs/C4-main-upstream-merge.md` 记录 26 处冲突按本地行为契约与上游功能并集解决；第二轮与第三轮已执行 Mobile/Leanback Java 编译和受影响/8 项定向单测；本地 native 资产门禁复核通过。第三轮尾段“尚未完成 guard finish/tag”与 Git 实际状态不一致，但当前 HEAD 已包含合并提交 `7dc58af...` 与 recovery tag `recovery/C4-ROUND3-UPSTREAM-SRC2/20260913055718-7dc58af1b0bb`，属文档滞后而非代码缺陷。
- P9 菜单观察修复：恢复 `observe("disc-menu-active", MPV_FORMAT_FLAG)`，使既有 `dispatchProperty()` 的 `disc-menu-active` 分支可达。已有聚焦 Java 编译通过；本轮复核注册与处理分支同现于 `MpvPlayer.java`，不改状态机、输入转发或 native 行为。
- 安全审计：当前工作树干净；`git diff --check` 通过；无 unmerged path。守卫因无活动任务报告 `task is not active`，不是工作树安全问题。
- 结论：现有评审已覆盖当前全部未推送修改，未发现需要修复的代码问题；本次无代码改动。

## 验证证据

- `git fetch origin beta && git fetch origin dev2`：远端状态与未推送范围已确认。
- `scripts/verify_mpv_native_assets.sh --require-elf`：通过。
- `git diff --check`：通过。
- `git status --short --branch`：干净，当前分支 `dev2`。

## 下一步

- 推送 `dev2` 与 3 个既有 recovery tag，然后创建 base `beta` / head `dev2` 的中文 PR；PR 合并后按用户要求拉取远端最新代码。
