# C20：dev1 合并远端 beta 最新代码与合并后复评

## Recovery anchor

- **目标**：将远端 `beta` 最新代码合入 `dev1`，复评相对 `origin/dev1` 的全部未推送改动（含已提交未推送的播放同步身份匹配实现）；发现问题则最小修复并验证，循环复评直到通过；随后提交、推送并创建中文 PR 到 `beta`，只创建 PR，不合并。
- **验收**：合并结果包含远端 beta 最新提交；不重新带入远端已移除/剔除的回退提交；本地未推送改动与 beta 新改动组合后编译和测试通过；测试包覆盖安装后 App 可启动且无崩溃；PR 描述用中文清楚说明改动内容与排版。
- **当前状态**：合并、代码复评、修复、验证、提交、推送和 PR 创建均已完成；PR 保持打开，不合并。
- **基线**：合并前本地 `dev1@cc85ad350c79f6db3b8f9687e6a5ba33707ca648`；远端 `beta@70592cbc8be1e6042a05de06ce14758eab0ae973`；共同祖先 `4eafca8539b405e150bbc796c4b6bd6eee5cf36a`。
- **当前关键文件**：`app/src/test/java/com/fongmi/android/tv/bean/StableInterfaceIdentityAcceptanceTest.java`（本任务修复）、远端 beta 引入的追更/播放器/解析改动，以及本地已提交的 `PlaybackIdentityResolver` 等播放同步身份实现。
- **已完成证据**：见“验证记录”。
- **未验证边界**：没有执行多设备真实远端同步矩阵；Go/Rust 工具链当前不可用，本轮未重复运行其测试；播放器自动线路回退和超级解析修复以源码评审、回归测试与启动冒烟覆盖。
- **下一动作**：无；等待 PR #365 审核结果。

## 范围与提交台账

### 本地未推送范围

- `cc85ad350c79f6db3b8f9687e6a5ba33707ca648`：播放同步稳定身份匹配实现；已通过其提交时记录的共享 fixture、多服务端测试、双 flavor 编译、定向测试和测试包覆盖安装。
- `f16d95efe9bbd8735d7dac1c5f0bdd77d1f132b7`：播放同步身份匹配设计文档。
- `origin/dev1..HEAD` 其余 16 个已提交提交为此前主题、焦点、站点切换、任务守卫和上游兼容工作，全部纳入组合复评。

### 远端 beta 提交台账

| 完整提交 | 处置 |
| --- | --- |
| `70592cbc8be1e6042a05de06ce14758eab0ae973` | 合并：PR #364，追更手动已读与 TV 焦点操作 |
| `8ed3955b6b0d6ee3407bb1d744a3c5ec0a5cefce` | 合并：从“全部已读”恢复左焦点 |
| `236b440b4e8f52bfbe442889d7bb4f6facc073ed` | 合并：TV 遥控器聚焦“全部已读” |
| `94323cd231b4dd33541f2bb2919cf7b8ce17ac8b` | 合并：恢复手动已读确认 |
| `76c4f09106e0f8bc1b1c3a9b43be8497556222fc` | 合并：PR #362 |
| `b23c409f8164c64636791bd8a52586aefd6449d8` | 合并：PR #363 |
| `4d5bb4e845d7b38c74ced93509fc8dd352772d55` | 合并：自动线路回退前强制重建播放器 |
| `d3077b1dee7db8dbf7d7789b63f74a5d9f3b0c3a` | 合并：dev2 同步 beta |
| `81cb2710247a4ded4d72626b3852d10cc65c3d01` | 合并：限制前台追更刷新节奏 |
| `8d00bbbe968c5950ee733e41eb1a529bbbae68d7` | 合并：提升前台追更刷新时效 |
| `00c6087dfed0c6f8e9ad6cb5ecea69f2a2e7af9c` | 合并：PR #361 |
| `fcc412debf8af9d0aaecd7a734b02273ff16d9c1` | 合并：移动端追更按钮均匀分布 |
| `51ecec72224faac8931a453f1356a3cd41e55a22` | 合并：避免移动端追更内容裁切 |
| `9801f2b31f4281047f6525c8006c81d299830284` | 合并：PR #360 |
| `d3e87170c3990301cd8de2157a842853e9bd6eb4` | 合并：协调 beta 焦点修正与即时详情焦点 |
| `00bc40e1f7208a9e5daf842a7ab90ac6576fafe5` | 合并：dev4 同步 beta |
| `21ebe9612b31bd9cc117a3ac5955d68b596394d0` | 合并：旧 WebView 支持超级解析 |
| `37f92430da7dd2c7ca33f82c330651e7312e072c` | 合并：自动线路回退前重建播放器 |

已核对 `HEAD..MERGE_HEAD` 与共同祖先范围：没有把 `remove-pr353-theme-changes`、历史 revert/reset 类提交或远端已剔除的主题改动作为独立改动带入；合并父提交固定为本地 `cc85ad350c79f6db3b8f9687e6a5ba33707ca648` 和远端 `70592cbc8be1e6042a05de06ce14758eab0ae973`。

## 评审记录

### 第 1 轮

- 本地播放同步身份匹配实现与 beta 新增追更/播放器/解析改动没有文件重叠；实际合并无冲突。
- 复审 `ParseJob`：超级解析按 JSON 并行、Web 串行；每个 Web 尝试有独立完成标志、超时和 WebView 停止路径，成功/错误通过 `done` CAS 防止重复回调。
- 复审 `CustomWebView`：WASM 兜底只注入指定解析站；DOM 媒体探测受尝试次数限制；停止路径清回调、停止加载并销毁由 `ParseJob` 统一管理。
- 复审自动线路回退：Mobile/Leanback 在取下一条线路前释放并重建同核心引擎，保留用户记忆核心，避免失败 Surface 状态被继承。
- 复审前台追更：`ActivityLifecycleCallbacks` 的 started/stopped 计数只影响前台判定；前台批量与间隔提升，后台周期和低频状态策略保持不变。
- 复审追更手动已读：本地乐观更新仅作用于 `hasUpdate` 条目，数据库批量写入在后台事务中执行，失败回读恢复，TV 左右焦点链完整。
- **发现问题**：`StableInterfaceIdentityAcceptanceTest` 仍断言数据库版本 47，而本地实现已升级到 48 并加入 47→48 迁移，全量测试会失败。

### 修复

- 将该测试的数据库版本断言更新为 48，并补上 `MIGRATION_47_48` 必须注册的断言。
- 修复只改测试契约，不改变运行时代码、schema 或迁移逻辑。

### 第 2 轮

- 复查修复后的差异：仅 2 行断言变化，准确覆盖 schema 48 和新增迁移注册。
- 复查合并树：追更、播放器、解析、焦点、移动布局与本地播放同步身份实现同时存在；没有生成文件、验证截图或未跟踪转储文件被暂存。
- 复查远端新提交的移动端布局：`mobile` flavor 覆盖 `activity_following.xml`，`readAll` 顺序和焦点链符合设计；`main` 布局保留给非 flavor 基线，不参与当前两个 flavor 的最终资源合并。
- 未发现新的必须修改问题；进入验证。

## 验证记录

- 通过：`git diff --check`、`git diff --cached --check`。
- 通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue --stacktrace --no-daemon`，`BUILD SUCCESSFUL in 2m 42s`。
- 测试结果 XML：
  - Mobile：716 个 suite，4915 tests，0 failures，0 errors，2 skipped。
  - Leanback：621 个 suite，4008 tests，0 failures，0 errors，2 skipped。
- 通过：`node --test serverless/playback-identity-fixtures/identity.test.js`，3/3。
- 通过：`node --test serverless/webhtv-remote-vercel/test/playback-sync.test.js`，4/4。
- 通过：`node --test serverless/webhtv-remote-deno/test/playback-sync.test.js`，4/4。
- 通过：`node --test serverless/webhtv-remote-cloudflare/test/playback-sync.test.js`，6/6。
- 通过：`bash scripts/build_arm64_debug_install.sh --flavor mobile --abi arm64-v8a --serial 192.168.50.3:5555`，Debug APK 覆盖安装成功。
- 通过：指定模拟器 `192.168.50.3:5555` 启动 `com.silent.android.webhtv` 后进程存在，焦点为 `HomeActivityCurrent`，启动窗口内无 `FATAL EXCEPTION`、fatal signal 或相关 App error。

## 回滚

- 本次合并提交可整体 revert；远端 beta 改动和本地 schema 测试修复包含在同一原子合并提交中，回滚不会留下半合并状态。
- 测试包为 Debug 覆盖安装，未卸载现有包，未触碰正式签名发布流程。

## 交付状态

- 合并提交：`4b38555402d8896b1a3c461213d0b7efbb2b471b`。
- 本地恢复标签：`recovery/C20-beta-sync-review-dev1-20260924/20260925001538-4b38555402d8`。
- 已推送：`origin/dev1` 指向合并提交。
- PR：https://github.com/Silent1566/webhtv/pull/365 （`dev1` -> `beta`，打开且可合并，仅创建，不合并）。

## 2026-09-25 追加修复与推送恢复锚点

- **目标**：推送 `dev1` 的追更站点大小写修复，并创建新的 `dev1 -> beta` 中文 PR；不合并 PR。
- **已完成**：远端 `beta` 已包含于本地 `HEAD`；`7cd8799dfe8e1d82366f3d69430c95e8cd8109c9` 已通过 `VodConfigSiteKeyTest`、`git diff --check` 与 `git diff --cached --check`；剔除提交 `5682f2b054b577b0db5c6f7c1143f2eebb51858e`、`2faa1f37757f` 均不在 `HEAD` 祖先中。
- **未完成**：`git push origin dev1` 因 GitHub TLS 握手持续失败未完成；`gh auth status` 报告 keyring token 失效，且匿名 API 已限流，无法创建 PR。
- **不安全状态**：无；tracked 工作区干净，本地已提交并打恢复标签，未跟踪文件均为既有验证截图/dump。
- **恢复动作**：GitHub 传输与认证恢复后，直接重新执行 `git push origin dev1`，然后创建 `dev1 -> beta` 的中文 PR，标题“修复追更站点大小写键匹配”，内容使用任务文档中的中文 PR 描述；不要重新评审或重跑已通过的测试。
