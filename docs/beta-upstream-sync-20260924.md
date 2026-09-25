# BETA-UPSTREAM-SYNC-20260924

## 目标与验收

- 目标：将 `dev2` 合并上游 `https://github.com/webhtv/webhtv` 默认分支 `main` 的最新代码。
- 验收：`upstream/main` 成为合并提交第二父/祖先；本地既有测试演进保留；上游测试兼容修复进入本分支；受影响单元测试源码完成目标变体编译验证；任务文件提交并创建本地 annotated recovery tag。

## 冻结基线

- 本地分支：`dev2`
- 本地基线 HEAD：`7a8b7c896293494e11b8db476f704030aa85fe06`
- 上游目标：`https://github.com/webhtv/webhtv`
- 上游分支/HEAD：`main` / `d187f6ae8bfaf0a4720724281d0a91186c57f73d`（tag `v5.6.0-202609241253`）
- Merge base：`8e4d9333de8ea7346491e71a0b1ab6858a852298`
- 范围：`git rev-list --left-right --count dev2...upstream/main` 为本地 3826、上游 1。
- 初始脏路径：无。分支本地领先 `origin/dev2` 1 个既有提交；该既有状态不属于本任务修改。

## 上游 commit ledger

| 序号 | 完整 commit ID | 内容 | 处置 |
|---:|---|---|---|
| 1 | `d187f6ae8bfaf0a4720724281d0a91186c57f73d` | `test(exo): avoid package-private recoverable access`：为 `ExoPlaybackException.isRecoverable` 增加反射 helper，避免直接访问包私有字段。 | 纳入；保留本地新增的初始化失败通知、`@Ignore` 说明和 timestamped `PlaybackException` fixture，仅吸收反射 helper。 |

## 合并与设计决定

- 上游净变更只有测试兼容性修复，不改变生产播放行为、ABI、二进制、构建依赖或用户接口。
- 与上游无变更、直接采用上游、以及合并后保留本地测试演进的比较：
  - 不合并无法满足“合并上游最新代码”。
  - 直接取上游文件会丢弃本地初始化失败监听、`@Ignore` 及 JVM 单测时间构造修复。
  - 选择三方合并：保留本地契约并吸收上游反射访问，风险最小、可单独回滚。
- 冲突位置：`app/src/test/java/com/fongmi/android/tv/player/exo/ExoCompressedAudioDirectPolicyTest.java`。已消除冲突标记，保留上游 `assertTrue(isRecoverable(error))` 和 helper；移除合并过程中出现的一个重复 helper。上游文件中仍有一处本地新增测试对包私有字段的直接访问，与该本地测试的既有实现一致，且本次目标变体编译已通过，不扩大为额外重构。

## 验证

- `git diff --cached --check`：通过。
- `bash ./gradlew :app:compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac --no-daemon`：`BUILD SUCCESSFUL`，耗时 34 秒，79 个任务（4 executed，75 up-to-date）。
- 初始误用通用任务名 `:app:compileDebugUnitTestJavaWithJavac` 失败；已确认本仓库使用 flavor/ABI 变体任务，改用正确目标任务后通过。该失败不是代码回归。
- 未执行 APK 打包、设备安装或实机播放；本变更仅影响单元测试源码，编译已覆盖合并后的类型和语法。

## 回滚

- 任务前回滚锚点：`7a8b7c896293494e11b8db476f704030aa85fe06`。
- 若合并结果需要回滚，回退/还原本任务创建的 merge commit 即可，不影响本地既有领先提交。

## 当前状态与下一步

- 状态：验证完成，待 task guard 原子提交并创建 annotated recovery tag。
- 下一动作：执行 `task_guard.sh finish`，随后立即创建本地 annotated recovery tag；不推送。
