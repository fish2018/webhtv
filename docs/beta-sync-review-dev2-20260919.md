# dev2 合并 beta 最新代码与代码复评记录（2026-09-19）

## 目标

合并远端 `beta` 最新代码，评审 `dev2` 已提交但尚未推送的直播源故障自动切换改动及合并后的完整差异；发现问题时修复并重新验证、复评，确认通过后提交、推送、创建目标为 `beta` 的中文 PR，并在 PR 后再次拉取远端最新代码。

## 合并基线与范围

- 工作分支：`dev2`
- 合并前 HEAD：`5230fbe186611b3c9c4537925c95360c5b4f102c`
- 合并前远端 `origin/beta`：`ca62fe6f5b97a2c0d4e20390ec2b25b7456e83aa`
- 最终合并基线：`10f0b0ab2aedbbd85ef3552b15577023b305989e`
- 最新 beta 提交：`10f0b0ab2aedbbd85ef3552b15577023b305989e`，为 PR321 合并提交。
- 合并方式：`git merge --no-commit --no-ff origin/beta`
- 合并结果：自动合并成功，无冲突。
- 任务守卫：`beta-sync-review-dev2-20260919`，模式 `standard`，范围覆盖 `app/`、`catvod/`、`chaquo/`、`docs/`、`gradle/`、`quickjs/`、`scripts/`、`third_party/`。
- 合并后相对最新 `origin/beta` 仅保留本地 16 个文件、652 行新增和 20 行删除；beta 携带的播放器、TMDB、原生库、锁文件、资源和历史文档均已进入合并树。

## 本地已提交未推送改动

本地提交链均保留并纳入本次合并提交：

| 完整 commit ID | 内容 |
| --- | --- |
| `0ce10e8ebf3019e86e2a32d551dd2c1470c91077` | 增加直播源故障自动切换设置和基础 fallback 流程。 |
| `1c2948140a4a9ed219c9fde2be86362d8e4da3de` | 线路耗尽后切换到下一直播源线路。 |
| `6e1afaae899baceb0b87f204277fd5ca7f1c08df` | 抽取并测试 fallback 策略。 |
| `8a4fc98e8a85dc43e90aec372fcd8014e3919932` | 取消错误路径上的过期缓冲超时回调。 |
| `8cde0eaa62de73de87090923ac604e805ab40951` | 从运行时状态初始化直播配置。 |
| `71cdddca31f5bc4881b5d020402a273412f84a3f` | fallback 开启时绕过播放器重复重试。 |
| `96ab4c2ebbafc760be9bcaccfbc41bd53e2a92e6` | 直播源解析失败时进入 fallback。 |
| `6e29d49084addfa4ecff9ab34d05adb91b82e86f` | 保留直播源切换状态。 |
| `ce392002b2d143fb8affc8d9ac33374b3b6a67a9` | 等待直播源解析完成后再决定 fallback。 |
| `5230fbe186611b3c9c4537925c95360c5b4f102c` | 增加设备级直播源 fallback 验证。 |

## beta 新增内容评审

`ca62fe6f5b97a2c0d4e20390ec2b25b7456e83aa` 之后 beta 新增 PR321，完整提交链为：

- `0bf950d7216fcc581e02198b5639348f965c97f2`：设计无 Key 源内嵌 TMDB 详情路由。
- `19128b7d18107b5c614078ebdadc863faa2476b5`：增加运行时详情模式和源数据状态策略。
- `1c4213db0baf24e9da9495edfdbaa9dcab43f527`：允许无 Key 保存 TMDB 模式设置。
- `6a994fa1acd85cd307bf111bc077f6e5a1385da0`：增加独立详情页 source-only 路由。
- `b2343d1d1de476226b52bef11b80248e1d5033d9`：接入 Mobile/Leanback 原生增强 source-only 数据。
- `6e2cde565ae34c1a0da392c7ca93f34cb97aaa99`：收口 source-only 交互入口。
- `d52fdfc5b2027872779026bad278c1a5c8e9f5d8`：增加目标设备验证。
- `f585fe06a935211c8146b7242f528eb664159c4e`：修正无 Key 网络任务断言。
- `c2b1907c3ba7e732a6d4378ec3360502b3d115e4`：收口 C16 实施索引。
- `7f5379cd73c7ff63142c102426d3453d79fc5440`：修复无 Key 源详情保持离线。
- `10f0b0ab2aedbbd85ef3552b15577023b305989e`：合并 PR321 到 beta。

评审结论：

- `DetailRuntimeModePolicy` 将配置模式和运行时模式分离；无 Key 且源 payload 不可渲染时回退到影视原生，有可渲染源数据时使用 source-only，避免空详情页。
- `TmdbSourcePayloadParser` 对 schema、身份、季号、大小、数组、字段类型和能力声明做规范化；`TmdbSourceAvailability` 还会校验 payload 与 bundle、Vod 身份一致，避免错误源数据进入 TMDB 页面。
- `TmdbSourceAdapter` 只从已验证 payload 构建 UI bundle；`TmdbUIAdapter.loadSource()` 不写匹配缓存、不调用 TMDB 网络服务，季集、相关视频和推荐入口均有 source-only 防线。
- 有 Key 路径仍保留 source-first、能力补齐、分页和个性化推荐；新增无 Key 分支没有改变在线路径调用顺序。
- 与本地直播改动的共享文件只有字符串资源和 `LiveActivityLayoutTest`，合并后两端代码及测试契约完整保留。
- 未发现正确性、兼容性、性能、网络越权、生命周期、作用域或回滚问题。

## 第一轮与第二轮复评

### 第一轮

- 检查本地 10 个提交的直播源解析、播放器 HTTP 错误、缓冲超时、线路/源切换、状态清理及 Mobile/Leanback 对称性。
- 检查 beta PR321 的 source-only 身份校验、能力规划、TMDB 网络入口和本地 bundle 消费。
- 合并无冲突，未发现需要修改的代码问题。
- 初次设备命令误使用了不带序列号的 Gradle connected 任务，导致两台在线设备都执行测试；该命令没有发现测试失败，但不作为最终设备范围证据。

### 第二轮

- 重新拉取 beta 后发现 PR321 新增内容，放弃旧合并索引，基于 `10f0b0ab2aedbbd85ef3552b15577023b305989e` 重新无冲突合并。
- 重新核对最终相对 beta 的 16 个本地文件；未发现 beta 新代码覆盖或削弱直播 fallback。
- 重新检查 source-only 的推荐、视频、外部评分、选集、重匹配和历史刷新入口；source-only 网络防线仍完整，TMDB payload 只读本地数据。
- 复评结论：通过，无需代码修复。

## 验证

- `git diff --check origin/beta`：通过。
- `git diff --check`：通过。
- Mobile Arm64 定向 JVM 测试与 Leanback Arm64 定向 JVM 测试：`BUILD SUCCESSFUL`。
- 覆盖直播 fallback 策略、直播 Activity 源码契约、直播布局、TMDB 运行时策略、source-only 接线、payload、交互、source availability、UI adapter 及 VideoActivity 相关测试；选定测试 XML 均为 `failures="0" errors="0"`。
- Leanback：`compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。
- 设备 `192.168.50.3:5557`（型号 `SM-N9700`）仅使用以下序列号命令：
  - 构建并单独安装 Mobile Arm64 Debug APK 和 AndroidTest APK。
  - `adb -s 192.168.50.3:5557 shell am instrument -w -r -e class com.fongmi.android.tv.ui.activity.LiveSourceFallbackInstrumentedTest com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner`
  - 结果：`LiveSourceFallbackInstrumentedTest` 2/2 通过，`OK (2 tests)`。
- 设备测试执行期间没有使用未指定设备的 connected Gradle 任务；`5559` 未作为本轮最终设备验证证据。

## 上一轮状态与后续动作（历史）

- 当前状态：代码第二轮复评通过，合并结果待原子提交。
- 提交后：推送当前 `dev2` 分支，创建 base `beta` / head `dev2` 的中文 PR，PR 描述列出直播源 fallback、设置项、两端实现、测试和兼容性说明。
- PR 创建后：执行 `git fetch --prune origin beta`，核对最新 beta、当前分支、PR 状态和工作区。

## 2026-09-19 第三轮：beta `552b68bb8e` 合并与 C16 复评

### 基线与范围

- 合并前本地 HEAD：`009f4e68d495174b5d5a37b6e4664282bcd70e35`，包含 15 个相对 `origin/dev2` 尚未推送的提交。
- 本轮 `origin/beta`：`552b68bb8e781d06ca989abfd8caa96a567e494a`，相对本地上次评估基线新增长按卡片绑定剧集修复和播放会话单确认框修复。
- 使用 `git merge --no-commit --no-ff origin/beta` 合并，自动合并成功；当前待提交树包含 beta 8 个文件及本地 C16 硬化修复。

### 复评发现与修复

- 订阅配置加载改为把原始 `Config` 传入凭据接收校验，避免旧加载任务在订阅切换后用 `getConfig()` 把旧 Key 误绑定到新订阅。
- 同一订阅内替换 Key 时推进凭据 epoch，旧请求的 401/403 不再清掉刚轮换的新 Key；`TmdbService` 在请求入口拒绝已经失效的临时凭据快照。
- 畸形 JSON 的 ingress 不再原样回传含 `tmdb_api_key` 的文本，防止结构化解析失败时泄漏到日志或缓存。
- `TmdbUIAdapter.invalidateSubscription()` 和 Leanback 缓存详情入口先刷新有效配置，再捕获新订阅作用域，避免后续推荐/详情请求继续使用旧凭据。
- 重评 beta 的 `IntroSkipPlayback` 会话租约、长按卡片绑定剧集传递和原 API 兼容委托，未发现新的阻断问题。

### 第三轮验证

- `git diff --check` 与 `git diff --cached --check`：通过。
- Mobile Arm64 Debug 定向 JVM 测试：17 个测试类共 248 项，`failures=0 errors=0 skipped=0`，`BUILD SUCCESSFUL`。
- Leanback Arm64 Debug Java 编译：`BUILD SUCCESSFUL`。

### 最新状态与下一步

- 当前状态：第三轮复评和定向验证通过，合并结果与修复待由任务守卫原子提交并创建 recovery tag。
- 提交后：推送当前 `dev2` 分支，创建 base `beta` / head `dev2` 的中文 PR，最后重新拉取远端最新代码。

## 回滚

- 合并前恢复锚点：`recovery/live-source-fallback-device-verification/20260919155448-5230fbe186`。
- 本任务提交由任务守卫创建独立 recovery tag；如需回滚，可回到该 tag，或撤销本次合并提交，不影响 beta 已有提交。
