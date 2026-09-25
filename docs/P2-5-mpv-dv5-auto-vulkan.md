# P2-5: MPV non-native DV5 automatic Vulkan routing

状态：已实施并通过定向单测、Mobile arm64 Debug 构建及 vivo V2453A 真机验收。

## Recovery anchor

- 目标：MPV 顶层“自动”模式播放 Dolby Vision Profile 5 时，若设备明确不支持原生 DV5、请求硬解且 Vulkan 1.3 路径可用，自动改用 `Vulkan + gpu-next + MediaCodec`，恢复正确色彩和流畅播放。
- 验收：自动档目标样片完成一次保留位置的自动重建后使用 `actual=vulkan/gpu-next`、`hevc_mediacodec`，重建后的稳定播放阶段不再出现 `profile 5 cannot use the active GPU mapping path`；首次 OpenGL 探测阶段允许出现一次该拒绝；普通 SDR/HDR、原生 DV、电视直出和手动渲染覆盖不改变；Vulkan direct 失败继续回退 stable，仍失败则回到原 OpenGL 路径且不循环。
- lane/scope：`upstream`；仅主索引、本文档、`MpvPlayerEngine`、`PlayerManager`、新的纯自动渲染策略及单元测试。
- 分支/基线：`fongmi-sync` / `b97a3cae8a8f4c74c85c86b7285db31cddfdc2f8`；开始时工作区干净，无受保护脏文件。
- 回滚锚点：`recovery/P2-5-MPV-DV5-AUTO-VULKAN/pre-20260829023911-b97a3cae8a8f`。
- 已完成：纯策略、engine-local render override、DV5 自动切换、Vulkan 失败回退和新媒体项清理均已实现并验证。
- 验证：策略单测 6/6；Mobile arm64 Debug 构建成功；V2453A 上 DV5 自动切到 `vulkan/gpu-next` 和 `hevc_mediacodec`，普通 HDR 新媒体项恢复 `opengl/gpu` 和 `av1_mediacodec`；用户确认画面正常。
- APK：`app-mobile-arm64_v8a-debug.apk` SHA-256 `89680e76dd4b04a1e51a33949f4af8297900ba107f8631d652a8648d5d75c7a8`。
- 剩余风险：首次识别 DV5 需要一次保留位置的播放器重建；未扩大到其他机型或 ABI，失败时保留现有 stable/OpenGL 回退。
- 下一动作：无；以本任务原子提交及 task guard recovery tag 回滚。

## 1. 用户能力与复现

实际能力不是增加一个设置，而是让“自动”模式在设备不能原生显示 DV5 时，自己选择已经证明能正确播放的渲染路径。用户不再需要先看到偏色和严重卡顿，再进入设置手动切换 Vulkan。

目标设备为 vivo V2453A、Android 15、arm64；样片为本地 `P5_Dolby_Amaze.mkv`，3840x2160、60 fps、Dolby Vision Profile 5 Level 9。

### 自动 OpenGL 基线

- App：`perf_mpv_output_mode=0`，render 选项未覆盖，`actual=opengl/gpu`。
- MPV：`hwdec=mediacodec,mediacodec-copy vo=gpu gpuContext=android gpuApi=opengl`。
- native 明确拒绝：`Native Dolby Vision output is unavailable and profile 5 cannot use the active GPU mapping path; refusing MediaCodec`。
- 随后 track decoder 为软件 `hevc`；用户现场覆盖层约 8.2 fps、掉帧 582、CPU 99%、PSS 1.1 GB、Native 845 MB，且色彩错误。

### 手动 Vulkan 对照

- App：`actual=vulkan/gpu-next`。
- MPV：`hwdec=mediacodec`，track decoder 为 `hevc_mediacodec`。
- 用户现场覆盖层约 63 fps、掉帧 0、PSS 749 MB、Native 225 MB，色彩正常。
- 保存证据：`/private/tmp/webhtv-dv5-auto-20260829/app-debug-log.txt`、`current-repro-filtered.txt`、用户 02:22:30/34/40 截图。

结论：网络、缓存、系统内存和温度均正常；错误色彩与卡顿共同来自自动模式选择了不能为该设备提供 DV5 raw GPU mapping 的输出路径，导致 MediaCodec 被拒绝并退回 4K60 软件解码。

## 2. 上游与本地基线

本任务不引入新的上游源码或二进制；它让 App 正确选择当前已经锁定并验证的能力。

| 仓库/来源 | 完整 commit / revision | 关系与结论 |
| --- | --- | --- |
| FongMi/mpv | `c7fef70644b3d506340e113689a5923f324c861d` | 上游观察线的 DV5 GPU mapping 提交；只有 active `gpu-next` VO 和 raw-preserving mapper 才向 FFmpeg 声明 GPU mapping 能力。 |
| FongMi/mpv | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | WebHTV 当前锁定、与上项重落基等价的最终提交，也是当前 `libmpv.so` 源码身份。 |
| FongMi/FFmpeg | `15b73698835285d68f9615691dd4dfc04422f28e` | 目标线的 MediaCodec DV5 GPU mapping；锁定提交 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 与其 patch-id 精确等价。 |
| FongMi/libplacebo | `04b3a0918fb32b8f374193aaead8b509274aae97` | checked Dolby Vision mapping；已包含在当前锁定 `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`（API 375）中。 |
| FongMi/mpv-android | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | 当前独立 MPV native 构建框架；本任务不改构建输入。 |

当前 `MpvPlayerEngine.buildConfig()` 只把用户 render 设置为 Vulkan 时选择 `gpu-next/androidvk/vulkan`；硬解 + 自动 OpenGL 固定为 legacy `vo=gpu`。`MpvAutoOutputPolicy` 只决定 Surface Direct 与 GPU，不决定 GPU 内部的 OpenGL/Vulkan 路径。因此上游能力已存在，缺的是 App 的第二层自动路由。

## 3. 最佳实践证据

访问日期均为 2026-08-29。

| 证据 | 等级 | 支持的结论 | 设计影响 |
| --- | --- | --- | --- |
| FongMi/mpv `c7fef70644b3c506340e113689a5923f324c861d` 完整 patch 与锁定树源码 | A | DV5 MediaCodec 仅在 `gpu-next` 且 mapper 保留 raw samples 时安全；Vulkan mapper直接满足，OpenGL 还依赖 `GL_EXT_YUV_target` | 不能继续使用 legacy `vo=gpu`，也不能伪造 decoder capability。 |
| mpv 官方 `DOCS/man/vo.rst` master（2026-08-29） | A | `gpu-next` 是推荐 VO；VO/API/context 必须显式且成套选择 | 目标组合固定为 `gpu-next + androidvk + vulkan`，不混用 context/API。 |
| Android `PackageManager.FEATURE_VULKAN_HARDWARE_VERSION` 官方文档 | A | feature version 表示硬件 Vulkan API 版本；Vulkan 1.1+还保证 AHardwareBuffer external memory、SYNC_FD 与 YCbCr conversion 基础能力 | 复用当前 Vulkan 1.3 + GLES 3.1 + Android 13 门槛，不在不支持设备上尝试。 |
| mpv-android PR #596 及维护者讨论 | B | Vulkan 需要编译能力和运行时设备检查；不同驱动存在崩溃、冻结和 gpu-next/OpenGL 异常，因此不宜全局默认 | 只对已证实必须使用 GPU mapping 的 DV5 自动提升，并保留失败回退。 |
| mpvEx `4151a45f862550a91b7a8efe35a6b19841242d48` 的 `DecoderPreferencesScreen.kt` | B | 成熟 Android 下游同样使用 Android 13、Vulkan 1.3、GLES 3.1 三重门槛，并把 Vulkan标为实验选项 | 支持复用现有严格设备 gate，但其全手动设计不能满足 WebHTV 自动路由。 |
| vivo V2453A 同片源 A/B 原始日志和覆盖层 | A | OpenGL/gpu 退软解且约 8 fps；Vulkan/gpu-next 使用 MediaCodec 且约 60 fps、色彩正常 | 直接证明本项目目标设备的收益和选择条件。 |

论文类证据不适用：这里不设计新的色彩算法或调度算法，决策由 MPV/FFmpeg capability handshake、Android 图形 API 契约和目标设备复现决定。用户现场 A/B 已提供比通用博客或跨设备 benchmark 更直接的性能证据。

## 4. 方案比较与决定

### A. 不改

- 收益：零变更风险。
- 缺点：自动模式继续错误退到 4K60 软件解码，色彩和性能都不可接受。
- 决定：拒绝。

### B. 所有 MPV 自动 GPU 输出默认 Vulkan/gpu-next

- 收益：目标 DV5 无需二次 prepare。
- 缺点：改变全部 SDR/HDR 和所有设备的默认驱动、功耗和生命周期风险；与 mpv-android 的长期兼容性证据冲突。
- 决定：拒绝。

### C. 所有硬解 GPU 输出改为 OpenGL gpu-next

- 收益：部分设备可能通过 `GL_EXT_YUV_target` 映射 DV5。
- 缺点：目标设备实证路径是 Vulkan；OpenGL raw YUV 扩展并非普遍存在，且会改变普通硬解路径。
- 决定：拒绝。

### D. WebHTV 窄自动提升（采用）

- 条件同时满足：MPV 顶层 profile/渲染项保持自动、硬解请求、Profile 5、系统明确不支持原生 DV5、bundled Vulkan 和设备 Vulkan 1.3 均可用、当前项未因失败禁用。
- 动作：为当前媒体项设置 engine-local Vulkan render override，若当前在 Surface Direct 则同一次重建退出直出；保留位置、播放状态、速度、重复和字幕样式。
- 失败：现有 Vulkan direct -> stable 逻辑先执行；stable/自动 Vulkan仍失败或超时，清除自动提升、回到原 OpenGL 路径并在当前项阻止再次提升。
- 手动优先：用户显式选择 OpenGL 或 Vulkan 时不介入；新媒体项清除临时 override，重新按自己的媒体事实评估。

这是当前项目的最佳实践：能力探测、内容探测、用户意图和失败隔离同时作为 gate，避免用全局默认换取单一样片成功。

## 5. 实施步骤

1. 新增纯 Java `MpvAutoRenderPolicy`，把启用/保持原因变成可单测决定。
2. `MpvPlayerEngine` 增加 nullable、仅 engine-local 的 render override 和当前 Vulkan 状态读取；不写设置、不改 native。
3. `PlayerManager` 在已有 DV/output 评估中合并 render 决策，一次重建进入 Vulkan；新 item/settings change 清理临时状态。
4. 在现有 direct -> stable 之后增加仅自动 DV5 会话可用的 OpenGL 最终回退，防止循环。
5. 运行策略单测、Mobile arm64 Java 编译/APK，安装目标设备并用相同 DV5 样片验证。

## 6. 验收与回滚

### 必须通过

- 策略：仅 `auto + hard + P5 + native DV unsupported + Vulkan available` 启用。
- 排除：手动 render、P5 native supported/unknown、非 P5、软解、Vulkan 不可用、当前项已 fallback 均保持原路径。
- 目标设备：重置 MPV 自动设置后，日志出现自动 render 决策和 `actual=vulkan/gpu-next`；decoder 为 `hevc_mediacodec`；首次 OpenGL 探测可以拒绝一次，但自动重建后的稳定播放阶段不得继续出现 active GPU mapping refusal。
- 性能/质量：60 fps 样片无持续掉帧，色彩与此前手动 Vulkan 对照一致；无 crash/ANR。
- 邻接：普通 SDR/HDR 各一次确认仍走原自动路径；手动 OpenGL override 不被自动提升覆盖。

### 回滚

- 实施前：`recovery/P2-5-MPV-DV5-AUTO-VULKAN/pre-20260829023911-b97a3cae8a8f`。
- 完成后以任务 guard 创建单一原子提交和唯一 annotated recovery tag。
- 回滚只撤销 App 策略/测试/文档；不涉及 MPV/FFmpeg/libplacebo 二进制、lock 或构建脚本。

## 7. 实施与验证结果

- `MpvAutoRenderPolicy` 只在自动渲染、硬解、DV Profile 5、原生 DV 明确不支持且 Vulkan 能力完整时请求提升。
- `MpvPlayerEngine` 使用仅当前 engine 生效的 nullable Vulkan override，不写入用户设置。
- `PlayerManager` 保留播放位置和状态重建；Vulkan direct 失败先走 stable，仍失败或首帧超时则当前项回 OpenGL 并禁止循环。
- 新媒体项会清除临时 Vulkan override；真机普通 HDR 日志为 `actual=opengl/gpu`、`renderReason=not-dv5`、`decoder=av1_mediacodec`。
- DV5 真机日志为 `old=gpu-opengl target=gpu-vulkan reason=dv5-gpu-mapping-vulkan`，重建后 `vo=gpu-next`、`hwdec=mediacodec`、`decoder=hevc_mediacodec`；稳定播放无 rebuffer，用户确认色彩和流畅度正常。
- 验证命令：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.mpv.MpvAutoRenderPolicyTest --no-daemon`；`bash ./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon`。
