# E9-3: Exo DV5 Vulkan renderer

状态：实施已获用户授权，先实现默认关闭的硬解 GPU 映射原型；真机验收前不替换现有生产路径。

## 1. 目标与边界

- 目标：在没有原生 Dolby Vision Profile 5 显示能力、但普通 HEVC MediaCodec 能输出可采样 10-bit `AHardwareBuffer` 的设备上，让 Exo 使用硬解并通过 Vulkan/libplacebo 恢复正确色彩。
- 保留：Exo 的解封装、网络、音频、字幕、轨道、时钟、帧释放、掉帧统计和 PlayerView 协议。
- 不做：P5->P8.1 码流伪转换、把 P5 冒充 HDR10 后 Surface 直出、复用 `libmpv.so` 私有状态、DV7 FEL 双层合成、DRM 安全视频输出。
- 首个实施单元：独立 `VideoSink`/native renderer 原型，默认关闭，仅 Profile 5、非 DRM、Vulkan 能力通过时可被显式启用。

## 2. 基线

- App：`742de1b08b01e6022517f6200a7502d1326301c9`，分支 `exo-dv5`，开始时工作树干净。
- Media3：WebHTV `e3e922d5c01bc0b564849940fe589daf37360d15`，版本 `1.11.0-alpha01-fongmi`。
- MPV 实证链：MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`。
- 设备证据：用户确认当前 WebHTV MPV 的 `MediaCodec + Vulkan + gpu-next` 在目标设备播放 DV5 时颜色正常，至少不再紫绿。
- 当前 Exo 缺口：`ExoUtil.DolbyVisionHdr10FallbackRenderer` 把 Profile 5 改报普通 HEVC/HDR10 并直接输出 Surface，没有进行 Dolby IPT/RPU 映射。

## 3. 决策证据

访问日期均为 2026-08-27。

| 证据 | 等级 | 支持的结论 | 局限/影响 |
| --- | --- | --- | --- |
| Android `MediaFormat.KEY_COLOR_TRANSFER_REQUEST` 官方 API 文档 | A | API 31+ codec 可接受 transfer/tone-map 请求；请求必须在配置后验证 | 只适用于设备已有可工作的 DV decoder，不解决普通 HEVC decoder 的 raw DV5 GPU 映射 |
| Android NDK `AImageReader` / `AHardwareBuffer` 与 Vulkan Android external-memory API | A | codec 可输出到 `AIMAGE_FORMAT_PRIVATE` reader，buffer 可用 GPU sampled usage 导入 Vulkan | 具体 external format、10-bit 精度、fence 和驱动行为依设备而异 |
| Media3 `VideoSink` / `MediaCodecVideoRenderer.Builder.setVideoSink`，WebHTV 锁定线 `e3e922d...` | A | 可保留 Media3 renderer 的 codec/时钟/掉帧逻辑，仅替换 codec 输入 Surface 和最终渲染 | `VideoSink` 是 Unstable API，升级 Media3 时必须有编译契约测试 |
| FongMi/media `0cefd3ceec27444cf8faf02486b472bab39109fe` | A | Profile 5 不能作为标准 HDR10 BL；有 DV codec 时优先请求并验证 codec tone-map | GPU fallback 必须位于 codec tone-map 之后，不能替代原生能力 |
| FongMi/FFmpeg `eb107bbafe37442065e42b4f2d410f371b758143` 与 `15b73698835285d68f9615691dd4dfc04422f28e` | A | MediaCodec 硬解链需保留逐帧 RPU/解析元数据，才能在 GPU 阶段应用 DV5 映射 | Exo 原生 MediaCodec 不会自动提供 FFmpeg `AVFrame` side data，需在输入 AU 上自行提取 |
| FongMi/mpv `c7fef70644b3d506340e113689a5923f324c861d` 及当前锁定树 | A | `gpu-next` 能对 raw MediaCodec DV5 帧应用 GPU mapping | mpv 的 VO/queue/decoder 状态机不能直接嵌进 Exo |
| 当前 `hwdec_aimagereader.c` 与 Vulkan direct/stable 实现 | A | 使用 PTS 匹配 AImage；DV5 采样必须保持 full range、RGB identity 和原始 Y/Cb/Cr 分量语义 | 需要保留 AImage 到 GPU fence 完成，不能在回调中提前释放 |
| libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | A | 支持 `PL_COLOR_SYSTEM_DOLBYVISION`、RPU matrix/reshape、tone mapping 和 Vulkan 输出 | 必须独立构建/链接，不能依赖 `libmpv.so` 内部生命周期 |
| FongMi/media `7d0d1e3c572aee885ffbbfd6d8317f1f3a581910` | B | GLES/libplacebo renderer 提供了 Media3 Surface/lifecycle 参考和 Dolby metadata 映射参考 | 该提交是 FFmpeg 软件解码，不满足本任务硬解目标，只取 renderer 生命周期设计 |
| mpv-player/mpv issue #10287 与 mpv-android issue #1081 | B | 普通 MediaCodec-copy/错误 Surface 转换会紫绿；Android 硬解 GPU DV 路径具有设备依赖 | issue 不是规范，不能替代本项目真机验收 |

论文类证据不适用：本任务的决定因素是 Android codec/BufferQueue/Vulkan ABI 和实际开源实现，不是新的色彩科学算法；Dolby 专有规范也不提供可用于 Android 公共实现的完整生产契约。

## 4. 方案比较

### A. 不改

- 优点：无新增 native 风险。
- 缺点：无 DV5 decoder 的设备继续紫绿，当前 HEVC/HDR10 伪装语义错误。
- 结论：不能满足目标。

### B. P5->P8.1/RPU-only 转换

- 优点：Extractor 层改动小。
- 缺点：只改 RPU，不能把 P5 的 Dolby IPT 基础层转换成 HDR10 基础层。
- 结论：拒绝。

### C. 原样移植 MPV 播放器或调用 `libmpv.so` 内部 libplacebo

- 优点：目标设备已有成功实证。
- 缺点：会引入第二套 demux/clock/track/lifecycle；`libmpv.so` 的 libplacebo 符号和上下文不构成稳定 Exo API；二进制与回滚耦合。
- 结论：拒绝原样集成，只移植已经验证的 AImageReader/Vulkan/DV 表示规则。

### D. WebHTV 适配的 Media3 `VideoSink` + 独立 native Vulkan/libplacebo

- 优点：保留 Exo 主体；codec 输出 Surface、帧时序和显示 Surface 有清晰所有权；可以按能力和内容窄启用；MPV/Exo 二进制独立回滚。
- 缺点：需要新的 native 资产、RPU 提取、PTS 配对、Vulkan/AImageReader 生命周期和真机矩阵。
- 结论：推荐并实施。

## 5. 推荐架构

```text
Media3 Extractor / SampleStream
        |
        | HEVC access unit + input PTS
        v
MediaCodecVideoRenderer (ordinary HEVC decoder view for P5)
        |
        | codec output Surface
        v
ExoDoviVideoSink.getInputSurface()
        |
        v
AImageReader PRIVATE + GPU_SAMPLED_IMAGE
        |
        | AImage timestamp -> input PTS metadata map
        v
AHardwareBuffer -> Vulkan external image
        |
        | RGB_IDENTITY, full range, raw Cr/Y/Cb mapping
        v
libplacebo Dolby Vision reshape + tone map
        |
        v
PlayerView output Surface
```

### 5.1 Media3 ownership

- 用 `MediaCodecVideoRenderer.Builder.setVideoSink()` 接入，不复制 `MediaCodecRenderer` 状态机。
- `VideoSink.handleInputFrame()` 保存 renderer 提供的释放 handler；到计划释放时调用 handler，将 codec buffer 送进 AImageReader Surface。
- AImage timestamp 必须与 codec buffer PTS 精确匹配；seek/flush/stream change 清空帧、RPU 和 handler 队列。
- tunneling 在该路径禁用；自定义 GPU 渲染与 tunneling 不兼容。

### 5.2 RPU 所有权

- 在 MediaCodec 输入 AU 入队前扫描 HEVC NAL type 62，保留原样 AU 给 decoder，同时把 RPU 副本按 input PTS 送入 native metadata queue。
- MP4 length-prefixed 与 Annex-B 都要支持；解析失败为单帧失败，连续失败触发整次播放受控回退。
- RPU 解析和 `pl_dovi_metadata` 生成在 native 层完成；Java 不复制 Dolby 结构体。
- 不把 ExoplayerHdrUtils 0.4.0 当作 metadata API：其公开 JNI 只提供 frame transform/profile 信息，没有输出可供 libplacebo 使用的 RPU 映射结构。

### 5.3 Native 资产

- 新库独立命名为 `libexo_dovi_renderer.so`，两 ABI 成套构建。
- 只包含本路径需要的 AImageReader、Vulkan、libplacebo、RPU 解析和同步代码；不链接 mpv demux/player/VO 状态机。
- 首选 Vulkan direct AHardwareBuffer sampling；失败可回退 stable GPU conversion；均失败则声明能力不可用并回到下一 Exo renderer/MPV。
- 禁止运行时链接 `libmpv.so` 的内部 libplacebo 状态。

### 5.4 策略顺序

1. 显示和 decoder 均支持原生 DV5：原生 DV。
2. API 31+ DV decoder 接受 `KEY_COLOR_TRANSFER_REQUEST`：codec tone-map。
3. 非 DRM、普通 HEVC decoder、Vulkan/AHB probe 通过且用户/实验允许：本 E9-3 GPU mapping。
4. 否则：MPV `gpu-next` 或服务端转码；不得再把 Profile 5 无条件冒充 HDR10。

## 6. 安全与兼容约束

- DRM：`cryptoType != NONE` 一律不声明支持；安全 decoder/secure Surface 不进入自定义 GPU。
- API：最低运行 API 26，Vulkan external-memory/AHardwareBuffer 能力逐项探测；仅检查 Vulkan 版本号不够。
- 精度：源 buffer 必须证明为可用的 10-bit/external format；不允许静默降为 8-bit 后仍标记成功。
- 生命周期：Surface replacement、后台/前台、seek、flush、换集和 release 必须停止接收回调并等待/取消有限 fence。
- 队列：输入 RPU、codec handler、AImage 三者均有上限；按 PTS 丢弃 stale 项，不允许无界积累。
- 故障：初始化失败在 codec 启动前返回不支持；运行时连续映射失败报告可分类错误，由 App 只重建一次并回退。
- 输出：SDR 屏默认输出 BT.709 SDR；HDR10 屏可后续增加 BT.2020 PQ，首个单元不同时扩展两种输出策略。

## 7. 实施阶段

### E9-3a：契约与能力门控

- 新增纯 Java 的 Profile 5/DRM/API/Vulkan 路由策略、失败原因和单测。
- 新增 `VideoSink` 壳和 native capability bridge；native 不可用时不得抢占 track。
- 将现有 Profile 5 HEVC/HDR10 fallback 收窄为最后的显式兼容选项，不在本单元直接删除。

### E9-3b：硬解 Surface 与帧同步

- 独立 native 库创建 AImageReader Surface。
- 实现 MediaCodec handler -> AImage timestamp 的有界配对、flush/release。
- 先以不做 DV mapping 的诊断图/帧计数验证 10-bit AHB 导入和稳定出帧。

### E9-3c：RPU 与 libplacebo 映射

- 输入 AU 提取 RPU并按 PTS入队。
- libplacebo raw DV representation、reshape、BT.709 SDR 输出。
- 目标 DV5 样片与 MPV 同帧截图/色彩对照。

### E9-3d：受控接线

- 默认关闭的实验开关、设备失败记忆和一次性回退。
- 通过设备矩阵后才允许自动策略选择；未通过时生产默认仍走原生 DV/codec tone-map/MPV。

## 8. 验收标准

- 自动化：Profile 5/7/8、DRM、API、Vulkan probe、renderer 优先级、flush/PTS queue 的单测。
- 编译：Media3/App Java 编译；两个 ABI native clean build；ELF `SONAME`/`DT_NEEDED`/版本标记校验。
- 设备：目标设备同一 DV5 文件确认 `MediaCodec` 硬解、Vulkan renderer、生效 RPU 数、无紫绿；与 MPV gpu-next 截图对照。
- 生命周期：起播、seek、暂停恢复、前后台、换集、Surface 重建、连续 20 次退出进入，无黑屏/死锁/use-after-free。
- 性能：4K 代表片连续 10 分钟，无持续队列增长；掉帧、GPU 帧时和功耗不显著劣于同设备 MPV 路径。
- 负向：DRM、8-bit AHB、缺少 Vulkan external format、RPU 缺失/损坏均不错误宣称成功。

## 9. 回滚

- 每个子阶段独立 commit/recovery tag。
- E9-3a 回滚只移除策略/壳，不改变 Media3 AAR 或 MPV。
- E9-3b/c 回滚删除独立 native AAR/`.so` 和 renderer 注册；现有 Exo、nextlib、MPV assets 保持原版本。
- E9-3d 运行时可通过关闭实验开关立即停止选择该路径，无需删除媒体数据库或配置。

## 10. 当前检查点

- 已完成：方案、证据、替代比较、验收和回滚边界；用户已明确要求按最佳实践继续实现。E9-3a 已新增纯 Java 路由/能力策略，覆盖原生 DV、API 31+ codec tone-map、实验性 GPU mapping、显式旧 HDR10 兼容和不支持结果；现有 P5 兼容 renderer 已接入该策略但保持原默认行为。
- 未完成风险：尚未用目标设备验证 Exo 自建 AImageReader Surface 是否得到与 MPV 相同的 10-bit external format；这是 E9-3b 的硬门槛。
- 下一动作：完成 E9-3a 的定向 JVM 验证并提交恢复点，然后为 E9-3b 声明 native/Java/build 的独立范围。

## 11. E9-3a 实施记录

- 代码：新增 `ExoDv5GpuMappingPolicy`。GPU 路由要求 Profile 5、非 DRM、API 26+、非 tunneling、普通 HEVC 硬解器、独立 renderer native 库、Vulkan/AHardwareBuffer probe 和实验开关全部通过；原生 DV 和 API 31+ 已接受的 codec tone-map 优先。DRM 即使允许旧兼容也不会进入 GPU 或旧 HDR10 fallback。
- 接线：`ExoUtil.shouldUseDolbyVisionHdr10Fallback` 的 Profile 5 分支改由策略返回值决定。E9-3a 没有注册不存在的 `VideoSink`，现有非 DRM Profile 5 默认仍进入旧兼容 renderer，等待 E9-3b/c 真机成立后再调整生产优先级。
- 测试：独立 JUnit 4.13.2 执行 `ExoDv5GpuMappingPolicyTest`，9 个用例通过；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 成功，证明 App/Media3 接线可编译。
- 已知验证阻断：`:app:testMobileArm64_v8aDebugUnitTest` 在执行测试前的 `processMobileArm64_v8aDebugResources` 失败，缺少既有 Material/Media3 style/attr/color 资源。本单元未修改资源或依赖，故没有扩大范围处理该基线问题，也不把新增 JUnit 结果表述为 Android Gradle 测试任务通过。
- 回滚：回退 E9-3a commit/tag 即恢复原 Profile 5 fallback 判断；没有 native 资产、Media3 AAR、设置或数据库变更。
- 下一动作：启动 E9-3b 独立实现单元，先完成 AImageReader/`AHardwareBuffer` capability bridge、`VideoSink` Surface 生命周期和有界 PTS 配对；DV 映射仍保持关闭。

## 12. E9-3b 实施记录

- 代码：新增独立 `libexo_dovi_renderer.so` CMake target（使用锁定 NDK r29，arm64-v8a 与 armeabi-v7a），并通过 `ExoDv5Native` 暴露能力探测、AImageReader Surface、有限 expected-frame 队列、AHardwareBuffer 使用/高位深统计和释放接口。
- Media3：新增 `ExoDv5VideoSink` 与 `ExoDv5GpuRenderer`，使用锁定 Media3 `VideoSink`/`MediaCodecVideoRenderer.Builder.setVideoSink()` 契约；视频帧 handler 的释放时间仍由 Media3 控制，AImage 时间戳按 presentation PTS（微秒转纳秒）配对，避免把渲染 deadline 错当 codec PTS。Renderer factory 仅支持显式诊断创建，当前未注册到生产 renderer 列表。
- 能力门控：native probe 要求同一 Vulkan 物理设备同时具备 Vulkan 1.1、`VK_ANDROID_external_memory_android_hardware_buffer`、`VK_EXT_queue_family_foreign`、sampler YCbCr conversion，并成功创建逻辑设备取得 AHB 导入函数；AImageReader 必须是 `AIMAGE_FORMAT_PRIVATE + AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE`。不满足时不会声明可用。
- 测试/构建：`:app:compileMobileArm64_v8aDebugJavaWithJavac` 成功；`:app:externalNativeBuildMobileArm64_v8aDebug` 与 `:app:externalNativeBuildMobileArmeabi_v7aDebug` 成功，产物分别确认 ELF 64-bit AArch64 与 ELF 32-bit ARM。新增纯逻辑测试覆盖显式 opt-in、完整 probe、队列时序和 PTS 纳秒换算；Android Gradle 单测仍受既有资源链接错误阻断。
- 当前限制：此单元只验证 codec→AImageReader/AHardwareBuffer 通路并统计 buffer，不导入 Vulkan 图像、不调用 libplacebo、不输出 PlayerView，也不提取/解析 RPU。因此不能宣称 DV5 色彩已恢复；这些属于 E9-3c/3d。
- 回滚：回退 E9-3b commit/tag 可移除 CMake/native 目标与诊断 sink，既有 Exo/MPV 二进制和默认路径不变。
- 下一动作：启动 E9-3c，接入独立 libdovi/RPU parser 与 libplacebo mapping API；先定义逐帧 metadata 所有权和 malformed-RPU 回退，再实现 Vulkan external image 到输出 Surface 的最小帧路径。

### E9-3b 检查点

- 完成：E9-3b diagnostic Media3 VideoSink and independent AImageReader/Vulkan capability bridge compile for arm64-v8a and armeabi-v7a; ELF SONAME, JNI exports and DT_NEEDED verified; production renderer remains unregistered.
- 分支/基线：`exo-dv5` / `7f665b7d858454fa19919b1e0b69b0f239ac9587`。
- 已改路径：`app/build.gradle`、`app/src/main/cpp/**`、四个 `ExoDv5*` Java 文件、新增测试和本任务文档；无起始脏文件。
- 验证：App Java、两 ABI CMake build 成功；ELF64 AArch64/ELF32 ARM、`libexo_dovi_renderer.so` SONAME、七个 JNI 导出和 `libandroid/libmediandk/libvulkan` 依赖已确认。
- 验证补充：使用 JDK 21 执行 `bash gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.exo.ExoDv5GpuRendererTest --no-daemon`；任务在既有 `processMobileArm64_v8aDebugResources` 资源链接错误处终止，未进入测试执行。该错误涉及缺失的 Material/Media3 资源，本阶段未改动资源或依赖。
- 未解决风险：目标设备尚未运行 capability probe/codec Surface 出帧；AImageReader 回调销毁只完成静态审计，尚无设备压力验证；本阶段不具备显示输出和 DV mapping。
- 回滚锚点：`7f665b7d858454fa19919b1e0b69b0f239ac9587`。
- 下一动作：Run ExoDv5GpuRendererTest with Gradle JDK 21, finish release-lifecycle review, then commit and tag E9-3b.

## 13. E9-3c 依赖本地化检查点

- 检查点摘要：E9-3c dependency provenance and missing armv7 shaderc resolved; isolated r29 build succeeded
- 下一动作：Copy the two-ABI libplacebo/shaderc closure into third_party/exo-dv5-native and link it from local CMake

## 14. E9-3c 依赖本地化实施记录

- 已完成：在当前仓库新增 `third_party/exo-dv5-native`，复制 libplacebo 7.375.0/API 375 的 arm64-v8a、armeabi-v7a 静态库，以及使用 NDK r29/API 24 独立生成的两 ABI shaderc 组合归档；构建和运行时不再读取 `main-2` 路径。
- CMake：`app/src/main/cpp/CMakeLists.txt` 通过 `${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/exo-dv5-native` 引入 ABI 对应归档，链接 `libdl`、`libm`、Android/MediaNDK/Vulkan，并启用 libplacebo 的静态 pthread 配置。
- Native 探针：`exo_dovi_renderer.cpp` 调用 `pl_version()` 并只在 API 375 库可用时上报 `CAPABILITY_LIBPLACEBO_375`；Java 能力门控已将该位列为必需能力。
- 资产哈希：见 `third_party/exo-dv5-native/MANIFEST.sha256`。来源版本为 libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`；shaderc 使用 NDK `29.0.14206865` 的 staged source，armv7 归档在独立临时输出目录生成。
- 验证：`JAVA_HOME=.../Contents/Home bash gradlew :app:externalNativeBuildMobileArm64_v8aDebug :app:externalNativeBuildMobileArmeabi_v7aDebug --no-daemon` 成功；两 ABI 均完成 CMake 链接。该验证只证明 native 资产和 JNI 探针可编译，不证明 DV5 已完成 Vulkan 图像导入、RPU 映射或色彩恢复。
- 未完成：libplacebo Vulkan 外部图像导入、PlayerView 输出和逐帧 Dolby Vision RPU 映射仍属于后续功能单元；当前 renderer 仍保持诊断/显式 opt-in，默认播放行为不变。
- 回滚：回退本单元提交即可移除本地依赖和 CMake 链接，恢复 `c94ba7c9464f4cb43df351e471e8aa2df7132178` 的诊断底座。
- 下一动作：在独立后续单元中补齐 Vulkan/AHardwareBuffer 到输出 Surface 的实际渲染，再进行目标设备 DV5 色彩验收。

## 15. E9-3c-render 检查点

- 检查点摘要：libdovi-3.3.2 builds for both Android ABIs in isolated output; current libplacebo archive lacks libdovi mapping support; no incomplete renderer was committed
- 下一动作：Start a new native asset unit to vendor libdovi C API and both ABI archives before wiring Vulkan renderer
- 已验证：`libdovi-3.3.2`（commit `4fd2b2235c9f93582dd4a00e65ee34a07800afd7`）使用 NDK `29.0.14206865`、Android API 26 linker 在独立临时目录成功生成 `aarch64-linux-android` 与 `armv7-linux-androideabi` 静态库；未写入其他工作区。
- 已验证：当前本地 libplacebo 归档是 `pl_has_dovi=1` 但 `pl_has_libdovi=0`；因此仅链接现有归档不能解析 RPU，也不能生成 `pl_dovi_metadata`。现有 ExoplayerHdrUtils JNI 只提供 profile/frame info，不能替代该接口。
- 未完成：本单元没有提交伪 Vulkan 输出或不完整 RPU 映射；当前 `VideoSink` 仍是诊断底座，生产路径和默认播放行为不变。
- 原因：要继续实现必须新增独立 libdovi C API 头/双 ABI归档，并将 `RpuDataMapping` 完整映射到 libplacebo 的 `pl_dovi_metadata`；这是新的 native 资产和 ABI 单元，不能在没有完整头文件/真机输出验证时冒充完成。
- 回滚锚点：`893a2cca7b56f953976d74d52317862bca6deb6c`。
- 下一动作：启动新的独立 native 资产单元，先把 libdovi C API（含 mapping structs）和双 ABI 归档本地化，再接入 Vulkan renderer。
- 目标：把 Exo DV5 renderer 所需的 libplacebo/shaderc 双 ABI 静态闭包复制进当前仓库，禁止运行时或构建时引用 `main-2` 绝对路径。
- 分支/基线：`exo-dv5` / `c94ba7c9464f4cb43df351e471e8aa2df7132178`；恢复时工作树无预先存在的脏文件。
- 已确认来源：libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`（7.375.0/API 375），arm64-v8a 与 armeabi-v7a 各自独立构建；shaderc 来自 Android NDK `29.0.14206865` 随附源码，组合归档包含 239 个对象。
- 已完成证据：`main-2` 缓存中存在双 ABI `libplacebo.a` 和 arm64 `libshaderc.a`；armv7 shaderc 缓存缺失，已用同一 NDK r29/API 24 在独立临时输出目录成功生成，没有写入 `main-2` 的 `obj/libs/prefix`。
- 当前限制：尚未把资产复制到 `third_party/exo-dv5-native`，尚未接入当前仓库 CMake，也尚未完成双 ABI 链接验证；不能据此声称 DV5 色彩映射已经可用。
- 回滚锚点：`c94ba7c9464f4cb43df351e471e8aa2df7132178`；删除本单元新增的本地依赖目录并回退 CMake/本文档即可。
- 下一动作：复制双 ABI libplacebo/shaderc、公共头和许可证，写入来源/哈希清单并完成当前项目双 ABI native 链接。

## 16. E9-3c-libdovi 实施记录

- 来源与构建：本地化 `libdovi` 3.3.2，源码 commit
  `4fd2b2235c9f93582dd4a00e65ee34a07800afd7`；`dolby_vision` crate 使用
  `features=capi`、`crate-type=staticlib`、NDK `29.0.14206865`、Android API
  26 分别生成 `aarch64-linux-android` 与 `armv7-linux-androideabi` 归档。
  C API 头由相同源码和 cbindgen 0.29.4 生成，归档、头和 MIT 许可证均由
  `third_party/exo-dv5-native/MANIFEST.sha256` 锁定。
- 本地闭包：CMake 仅从仓库相对路径按 `${ANDROID_ABI}` 导入
  `libdovi.a`，不读取或复用其他工作区。native capability probe 实际调用
  `dovi_parse_unspec62_nalu` 的错误输入路径并释放 opaque 对象，因此双 ABI
  链接必须解析真实 parser/free symbols，而不是只检查文件存在。
- 语义校正：libdovi 3.3.2 的 conversion mode 2 才是 P8.1 no-op mapping，
  mode 4 是保留 mapping 的 P8.1；mode 3 是静态 P8.4。E9-3 不依赖任何
  P5→P8.1 伪 base-layer 转换，而是保留原始 Profile 5 IPT base layer，供
  Vulkan/libplacebo 结合逐帧 RPU 做 reshape。
- 限制：本单元只建立可重现的 parser ABI 依赖。尚未把 RPU 从 Media3
  access unit 传入 native，也尚未创建 Vulkan swapchain/AHB external image
  和显示输出，因此不能宣称设备色彩已经恢复。
- 回滚：回退本单元提交和恢复标签即可删除 libdovi 资产、头、许可证、
  CMake 导入和 capability parser probe；上一阶段 libplacebo 诊断底座不变。
- 下一动作：覆盖 `MediaCodecRenderer.onQueueInputBuffer` 提取 NAL type 62，
  以 PTS 有界排队到 native 并完成 metadata 生命周期，然后接入 Vulkan 输出。

## 17. E9-3d-vulkan 实施检查点

- 当前基线：`exo-dv5` / `3df132a68e1aa67ce54b61cb091c41f88ceba00e`；
  当前单元只修改 native renderer 与本文档，无预先存在的用户脏文件。
- 已完成代码：创建 libplacebo Vulkan instance/device/swapchain，导入
  `AImageReader` 的 `AHardwareBuffer`，按 Profile 5 raw IPT 语义设置 full-range
  RGB-identity YCbCr sampler 与 Cr/Y/Cb 分量映射，解析 libdovi RPU 并调用
  `pl_render_image` 输出到 PlayerView Surface。
- 已验证：`externalNativeBuildMobileArm64_v8aDebug` 编译成功；该结果仅证明
  arm64 native 源码和链接闭包成立，不代表设备播放或色彩验收成功。
- 收尾风险：补齐 foreign queue ownership 的 semaphore 契约、失败路径回收、
  libplacebo log 销毁、seek 后旧 RPU 状态清理，并依据 AHB format feature
  选择采样 filter；随后执行 arm64-v8a/armeabi-v7a 双 ABI clean native build。
- 回滚锚点：`3df132a68e1aa67ce54b61cb091c41f88ceba00e`。
- 下一动作：核对 vendored libplacebo API 375 的 release/hold 同步契约并完成
  上述最小安全修复。
- 检查点摘要：arm64 native Vulkan renderer build succeeded; patched semaphore ownership, failure cleanup, log release, seek RPU reset, and AHB sampling feature gating
- 下一动作：Run dual-ABI native build, inspect result, then finish E9-3d-vulkan atomically.

### E9-3d-vulkan 完成记录

- Vulkan 输出：native renderer 现已创建 Android `VkSurfaceKHR`、libplacebo
  Vulkan device/swapchain/renderer，并把 `AImageReader` 的
  `AHardwareBuffer` 作为 external-memory `VkImage` 导入。输出 Surface 替换时
  串行销毁并重建 Vulkan 资源，release 会先停止 image listener 并等待当前
  callback 退出。
- DV5 映射：AHB sampler 强制使用 `RGB_IDENTITY + ITU_FULL`，libplacebo plane
  按 `Cr/Y/Cb` 解释 raw Profile 5 分量；输入 AU 的 NAL type 62 RPU 经 libdovi
  解析为每帧 `pl_dovi_metadata`，交给 `PL_COLOR_SYSTEM_DOLBYVISION` 的
  `pl_render_image` 完成 reshape/tone-map。
- 同步与回收：external image 在 `VK_QUEUE_FAMILY_FOREIGN_EXT` 与 libplacebo
  之间用 `pl_vulkan_release_ex`/`pl_vulkan_hold_ex` 转移 ownership；hold 使用
  libplacebo 创建的 binary semaphore，并在 `pl_gpu_finish` 后销毁 semaphore、
  wrapper、VkImage 和 imported memory。失败路径会在回收前尝试重新取得
  ownership；Vulkan/log/swapchain/renderer 均有配对释放。
- 格式门控：要求 AHB Vulkan format 支持 sampled image；YCbCr conversion
  仅在 format features 声明支持时使用 linear chroma filter，否则降为 nearest，
  并传递 separate-reconstruction-filter 能力。seek/flush 同时清空 pending RPU
  和 last-DV metadata，避免沿用上一时间线的元数据。
- 验证：使用 JDK 21、NDK `29.0.14206865` 执行
  `:app:externalNativeBuildMobileArm64_v8aDebug` 与
  `:app:externalNativeBuildMobileArmeabi_v7aDebug`，两 ABI 均 `BUILD SUCCESSFUL`。
  此结果证明 native 源码、libplacebo/libdovi/shaderc 本地闭包和双 ABI 链接成立，
  不证明目标设备已经正常出帧、颜色正确或性能达标。
- 真机门槛：renderer 虽已具备受控注册条件，但内部实验默认关闭；没有目标设备
  DV5 播放、截图、logcat、seek/lifecycle 证据前，不开启自动生产选择，也不宣称
  色彩或性能验收通过。
- E9-3d-wiring：native capability bit 已按 libplacebo 7 的最低 Vulkan
  1.2 要求校正，renderer 名称由 diagnostic 改为 experimental，统计新增
  `renderedFrames` 与 `renderFailures`。自定义 renderer 仅在现有内部实验总开关
  与 Exo 域开关同时开启、native probe 完整通过时加入列表；它位于列表最前，
  但对检测到“DV 显示能力 + 硬件 DV decoder”的设备主动返回 unsupported，保留
  原生 DV。其余 Profile 5 才走普通硬件 HEVC decoder + VideoSink/Vulkan 路径，
  并显式关闭 tunneling。sink 初始化会补传已经设置的 PlayerView Surface，避免
  Surface 先到导致 native renderer 黑屏；native 首个成功 present 会触发 Media3
  首帧通知，连续 3 次 Vulkan 渲染失败则上报 video-frame-processing error，避免
  永久静默黑屏。
- 回滚：回退本单元提交/恢复标签即可恢复到仅有 RPU 输送和本地头文件的状态；
  vendored native 依赖与现有 Exo/MPV 默认路径不受影响。

## 17. E9-3c-renderer RPU 接线记录

- Media3：`ExoDv5GpuRenderer` 覆盖 `onQueueInputBuffer`；该 hook 在锁定版
  Media3 的 `DecoderInputBuffer.flip()` 之后、`MediaCodec.queueInputBuffer()`
  之前执行。扫描器不修改原 ByteBuffer，支持 Annex-B 3/4 字节起始码和
  1/2/4 字节长度前缀，只复制 HEVC NAL type 62。
- JNI/native：每个 RPU 带原始 `buffer.timeUs` 送入 native；libdovi 必须
  成功解析 header（`rpu_type == 2`）和 mapping 才进入 16 帧有界队列。
  统计有效 RPU、malformed RPU、队列丢弃和当前 pending；flush/seek 同时
  清除 expected-frame 与 RPU 队列，避免旧 metadata 污染 seek 后帧。
- Surface：`VideoSink.setOutputSurfaceInfo/clearOutputSurfaceInfo` 已转发到 JNI，
  native 使用 `ANativeWindow_fromSurface` 获取独立引用，并在 Surface 替换、
  clear 和 renderer release 时成对释放；当前尚未用该 window 创建 swapchain。
- 验证边界：App Java 与 arm64-v8a/armeabi-v7a native 构建通过。NAL 扫描
  测试新增 Annex-B、四字节长度前缀和输入 position 不变断言；Android Gradle
  单测任务仍受既有资源链接基线错误约束。
- 当前限制：本单元完成 AU→RPU parser 与 Surface 所有权接线，但 AHB 尚未
  import 为 Vulkan external image，RPU mapping 也尚未转换成
  `pl_dovi_metadata` 并送入 `pl_render_image`，所以仍不能宣称色彩恢复。
- 下一动作：本地化完整 libplacebo API 375 公共头，然后实现 Vulkan context、
  Android swapchain、AHB external-format import、raw DV component mapping 和
  同步 `pl_render_image` 输出。

## 18. E9-3d libplacebo 公共头本地化

- 第一批公共头来自已锁定并已链接的 libplacebo 7.375.0 commit
  `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 同一源码树，包含 Vulkan、
  GPU、swapchain、renderer、colorspace、log/cache/common 和核心 shader API。
- 这些头只复制到当前仓库 `third_party/exo-dv5-native/include/libplacebo`；
  App CMake 和 native 源码不引用 `main-2`，另一工作区仅作为本地复制来源。
- 第一批是资产恢复点，不改变运行时或生产 renderer 选择；第二批将补齐
  renderer include 闭包后，由实际双 ABI 编译验证头与静态库 API 375 匹配。
- 第二批补齐 `renderer.h` 的实际传递闭包：dispatch、dither、gamut/tone
  mapping 以及 custom/ICC/LUT shader API。未复制 D3D、OpenGL、FFmpeg、
  dav1d 等本实现不使用的公共头，保持 Exo Vulkan 资产范围最小。
- 下一动作：在 native renderer 同时 include `vulkan.h` 与 `renderer.h`，以
  双 ABI 编译确认头/归档契约，再实现 context、swapchain 与 AHB import。

## 19. E9-3d-device 目标设备验证

- 设备与产物：V2453A（Android API 35，序列号 `10CF6H1D2L0009S`）安装
  `app-mobile-arm64_v8a-debug.apk`；验证基线为
  `c943d7659bf9f03d2cf3cdaa670d5b82231168ca`。
- 路由证据：开启内部播放实验与 Exo 域开关后，目标 DV5 样片实际选中
  `MediaCodecVideoRenderer-DV5-Vulkan`；native capability probe、Adreno
  Vulkan 初始化和普通 HEVC `c2.qti.hevc.decoder` 创建均成功。
- 失败分类：本次不是音频输出失败。`AudioTrack` 已完成初始化，Media3 最终
  上报的是 `ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED`，异常链为
  `MediaCodecVideoRenderer-DV5-Vulkan -> VideoSinkException ->
  DV5 Vulkan rendering failed`。应用当前把 output stage 显示成泛化的音视频
  输出提示，容易让设备侧反馈误判为音频故障。
- 真正阻断：native `renderImage()` 连续三帧返回 `false`，但当前失败出口没有
  阶段日志，尚不能区分 AHB properties、external image 创建/绑定、
  `pl_vulkan_wrap`、swapchain、`pl_render_image`、submit 或 foreign queue hold。
- 原始证据：设备完整 logcat 保存于 `/tmp/e9-dv5-full-logcat.txt`；关键错误位于
  2026-08-27 20:07:31，renderer format 为 `dvhe.05.09`、3840x2160。
- 验证结论：构建与 renderer 选择成立，但设备出帧和色彩验收失败；不得将本
  candidate 宣称为 DV5 正常播放。
- 回滚锚点：`c943d7659bf9f03d2cf3cdaa670d5b82231168ca`。
- 下一动作：为 `renderImage()` 增加有界的首个失败阶段与 AHB/Vulkan 格式日志，
  在同一设备复现后只修复首个确定失败的 native 契约。

## 20. E9-3d-render-fix 实施检查点

- 根因：目标设备返回 Android Vulkan `externalFormat` 时，旧实现仍把
  `formatProps.format` 写入 `VkImageCreateInfo.format` 和 libplacebo wrap。
  Android external-format image 必须使用 `VK_FORMAT_UNDEFINED`，实际格式由
  `VkExternalFormatANDROID.externalFormat` 描述；设备日志中的
  `Unsupported VkFormat: 1000330000...` 与此契约错误相符。
- 修复：`renderImage()` 在 external format 非零时同时使用
  `VK_FORMAT_UNDEFINED` 创建/包装 image；保留 concrete format 路径不变。
  所有失败出口增加首个阶段、`VkResult`、AHB geometry/format/usage、Vulkan
  format/externalFormat/features、PTS 和 RPU 状态日志。
- 误报修复：Media3 音频错误仍保持原 `OUTPUT` 分类；视频帧处理错误仍属于
  output stage，但 `PlayerManager` 根据精确错误码显示独立的“音频输出失败”
  或“视频输出失败”，不再把 DV5 Vulkan 错误提示成音频故障。
- 验证：JDK 21 下 `:app:externalNativeBuildMobileArm64_v8aDebug`
  `:app:externalNativeBuildMobileArmeabi_v7aDebug` 与
  `:app:compileMobileArm64_v8aDebugJavaWithJavac` 均 `BUILD SUCCESSFUL`。
- 当前状态：尚未完成修复后目标设备播放验收；下一动作是用无构建缓存的
  arm64 debug APK 安装到 `10CF6H1D2L0009S`，复现同一 DV5 样片并检查首个
  `render failed stage=` 日志、`renderedFrames` 和错误提示。

### E9-3d-render-fix 设备复测检查点

- 检查点摘要：external-format 修复已安装；普通视频回归已排除；DV5 精确复测待执行。
- 下一动作：启动应用，从当前历史列表点击 `P5_Dolby_Amaze.mkv`，读取首个
  `ExoDv5 render failed stage`。
- Next action (guard): launch app and tap P5_Dolby_Amaze.mkv from history, then inspect first ExoDv5 render failed stage
- 分支/基线：`exo-dv5` / `38a4fdb84f3c28391c43f1a98f866960981046aa`；
  当前未提交范围仍为 native external-format 修复、精确输出错误文案与本文档，
  没有新增其它播放器行为。
- 设备状态：V2453A（Android API 35，`10CF6H1D2L0009S`）在线；当前工作区
  `app-mobile-arm64_v8a-debug.apk` 已于 2026-08-27 20:46:29 重新安装。
- 已排除：用户复核后确认普通视频播放正常，不存在实验 renderer 抢占非 DV5
  轨道的回归；继续只处理 `dvhe.05.09` 样片。
- 未完成：安装后的 DV5 样片尚未完成一次有效复测，因前一次自动点击命中了
  历史列表中的其它条目，没有产生新的 `ExoDv5` 日志。
- 下一动作：重新启动应用，读取当前历史列表 UI 坐标后精确点击
  `P5_Dolby_Amaze.mkv`，保存完整 logcat 和屏幕证据，并按首个
  `render failed stage=` 决定唯一下一处 native 修复。

### E9-3d-render-fix FIFO 时间戳修复检查点

- 已确认 RPU 解析成功，但目标设备的 `AImage` timestamp 是 monotonic clock，
  与媒体 PTS 不在同一时间域；旧代码精确比较两者导致每帧都误报 `missing-rpu`。
- 已改为按 MediaCodec 输出顺序 FIFO 关联已排队的媒体 PTS，保留 AImage
  timestamp 仅用于队列为空时的诊断；arm64 debug APK 已于 22:37 构建完成。
- 工作区仍只包含既有 E9-3d native 修复、输出错误分类和本文档修改；Gradle
  生成的未跟踪 `app/.cxx` 已移至 `/tmp/e9-app-cxx-20260827-2237`，不纳入任务。
- Rollback anchor：`38a4fdb84f3c28391c43f1a98f866960981046aa`。
- Next action: install FIFO candidate and reproduce P5_Dolby_Amaze.mkv once, then fix only the first new native failure stage

### E9-3d-render-fix 色彩修复检查点

- 设备复测已确认 FIFO candidate 可以稳定出帧，但画面接近全黑。
- 根因已收敛到 RPU 状态映射：libdovi 的压缩 DM 对象不携带新的静态颜色
  矩阵，当前实现却把其中的零字段写入 `pl_dovi_metadata`；同时没有继承
  `use_prev_vdr_rpu_flag` 指向的 reshape 状态，也没有向 libplacebo 传递
  RPU 的源 PQ 亮度元数据。
- 修复边界：只调整 `exo_dovi_renderer.cpp` 的 RPU 状态继承、Profile 5
  标准静态矩阵回退和 HDR 元数据映射，不改变 Exo/MPV 路由或其它格式行为。
- Next action: implement compressed-DM inheritance and Profile 5 luminance mapping, then build and install one arm64 APK

### E9-3d-render-fix 黑屏语义复核检查点

- DV5 black-output review: libplacebo/mpv standard path applies P5 RPU reshape; hand-mapped coefficient normalization matches upstream, so disabling mapping is unsupported. Current decisive uncertainty is Android external-format sampler channel/range contract versus first-frame RPU state.
- 已核对 libplacebo 7.375.0 的 `pl_map_dovi_metadata()` 与 shader：P5 的 RPU
  mapping 会在颜色矩阵前执行；libdovi C API 的整数/小数系数组合与 FFmpeg
  signed fixed-point 语义一致。`profile5.bin` 的零 luma 曲线只是单帧 fixture，
  不能据此把所有 P5 mapping 当成错误。
- 下一动作：检查已保存的最新设备日志和 Android external-format 通道契约，
  只修复一个确定的 native 输入解释错误，然后执行 arm64 native build。
- Inspect saved latest device log and external-format channel contract, then apply one minimal native fix and arm64 build

### E9-3d-render-fix 当前候选构建检查点

- 当前 DM/RPU 色彩修复候选已通过 `:app:externalNativeBuildMobileArm64_v8aDebug` 与 `:app:assembleMobileArm64_v8aDebug`，APK SHA-256 为 `956e972b272ca219c4c48233000280af75fa5598171118fc0bb19ee970a0f5b7`。
- 目标设备 `10CF6H1D2L0009S` 当前未连接，尚不能把编译成功表述为色彩修复通过。
- 下一动作：设备上线后覆盖安装该 APK，播放 `P5_Dolby_Amaze.mkv` 并只读取 `ExoDv5`/renderer 定向日志。
- Checkpoint: arm64 DV5 DM/RPU color candidate built successfully; device offline; app/.cxx preserved under /tmp
- Next action: install candidate on 10CF6H1D2L0009S and capture focused ExoDv5 playback logs

### E9-3e HDR swapchain 日志根因与修复

- SurfaceFlinger 的目标设备现场状态确认 Exo DV5 Surface 使用
  `RGBA_1010102`，但 dataspace 仍为 `V0_SRGB`；同一设备声明支持 HDR10/PQ，
  最大亮度 800 nit。`color_bits=10` 只选择位深，不能替代 HDR 色彩空间声明。
- 根因是 Exo Vulkan 路径没有像 mpv gpu-next 一样调用
  `pl_swapchain_colorspace_hint`，libplacebo 因而按默认 sRGB 创建 swapchain。
- 最小修复是在已完成 DV reshape 的 BT.2020/PQ source 上调用 colorspace hint，
  再 resize/acquire swapchain；同时有界打印前四帧 source/target primaries、transfer、
  luminance 与输出位深，供设备日志和 SurfaceFlinger dataspace 双重确认。
- 回滚锚点：`recovery/E9-3d-render-fix/20260828004539-eb1f827bfc7b`。
- 设备验证：arm64 native 构建与 APK 打包成功，APK SHA-256 为
  `75d7cee2f3da73133ed8be65ab7ba184a4e4518ac9cecfd0ede17acab51b238b`；
  已覆盖安装到 V2453A 并精确播放 `P5_Dolby_Amaze.mkv`。
- 日志结果：Exo 仍使用 `c2.qti.hevc.decoder`，RPU 为 Profile 5 且 mapping/DM
  均有效；前四帧 source/target 均为 BT.2020/PQ、10 bit、0.005-3999.7 nit，
  无 `render failed` 或 `VideoSinkException`。
- 系统输出结果：SurfaceFlinger 将视频层标记为 `BT2020_PQ`，已从修复前的
  `V0_SRGB` 切换为 HDR 输出。日志层面的错误色彩空间根因已修复；不以截图或
  主观画面对比替代用户的最终视觉验收。

### E9-3f RPU 时间戳域修复

- 复测日志发现恢复播放时首个渲染 PTS 为 `1000000000000`，而 pending RPU
  从 `1000055767000` 开始，差值约 55.8 秒，连续三帧因此命中 `missing-rpu`。
- Media3 会通过 `VideoSink.setBufferTimestampAdjustmentUs()` 对输出帧做起始位置
  修正；旧实现只修正 AImage timestamp，RPU 和 native FIFO 的媒体 PTS 仍使用原值。
- 修复：RPU 入队、`nativeQueueFrame` 的 presentation PTS、AImage timestamp
  统一使用同一调整后的时间戳，覆盖 seek/恢复播放场景，不改变播放器路由。
- 下一动作：编译安装并从历史入口再次播放 `P5_Dolby_Amaze.mkv`，确认不再出现
  `missing-rpu`，且 Exo 仍持续出帧。

### E9-3f 当前复测检查点

- 时间戳修复候选已通过 `:app:assembleMobileArm64_v8aDebug`，APK SHA-256：
  `cd115cefb8acc2890488dc5de039d4fefd12cc2666520fb48a0c84147fde9c99`。
- 已覆盖安装并启动到目标设备 `10CF6H1D2L0009S`；尚未播放样片。
- 由于 guard 要求更换诊断路线，下一步只做一次精确历史条目播放并抓取
  `ExoDv5`/`VideoSink` 日志，判断时间戳修复是否消除 `missing-rpu`。

### E9-3f 失败复测结论

- 当前候选无效：首批 RPU 被调整为 `-432999` 至 `-299999` 微秒，自定义
  DV5 decoder 约 300 ms 后释放，随后普通 Exo renderer 才尝试连接仍由 Vulkan
  持有的 Surface 并报 `Failed to connect to surface`。没有发生 MPV 切换。
- 已否定的假设：不能对 `DecoderInputBuffer.timeUs` 和 VideoSink 输出帧 PTS
  同时直接叠加 `bufferTimestampAdjustmentUs`；Media3 的输入时间戳已经包含 stream
  offset，这种处理会制造负 RPU PTS，且未证明两个回调属于同一原始时间域。
- 当前未提交候选不得完成或打标签。下一动作是读取首次 decoder 释放前后的完整
  播放异常，确定 sink 退出原因，再用单一明确映射修正 RPU/帧配对。
- Guard checkpoint message: 当前候选无效。
- Guard next action: 读取首次 decoder 释放前后的完整播放异常，确定 sink 退出原因，再用单一明确映射修正 RPU/帧配对。
- Checkpoint: 已撤销重复叠加 `bufferTimestampAdjustmentUs` 的无效候选；当前仅增加
  renderer stream/disable、sink adjustment 和首四帧 PTS 的有界日志，以确定首帧前
  custom renderer 被释放的直接原因。
- Next action: 构建安装一次 arm64 debug APK，精确复现并读取 `ExoDv5` 定向日志。

- Compaction recovery: branch/HEAD and three task-owned dirty files match E9-3f checkpoint; generated app/.cxx is the only guard violation.
- Next action: Preserve app/.cxx outside worktree, capture one PID-bound all-buffer logcat reproduction, and fix only the first confirmed failure.

- Checkpoint: A fresh device log confirms Exo's custom renderer receives valid Profile 5
  RPU metadata and renders without failures, but the target SurfaceView is not retained by
  SurfaceFlinger as an HDR layer (`mIsHdrLayerPresent=false`). The renderer was advertising
  a 4000-nit BT.2020/PQ target, so the compositor can display PQ values as SDR, producing the
  reported near-black image. The current bounded fix changes only the swapchain hint to the
  explicit sRGB color contract; DV5 IPT/RPU mapping and Exo routing remain unchanged.
- Next action: Build the arm64 debug APK, install once, replay `P5_Dolby_Amaze.mkv`, and read
  only `ExoDv5` logs to verify the target contract is sRGB and rendering remains failure-free.

### E9-3g SDR output contract correction

- Root cause: the compatibility renderer advertised the decoded DV source itself as the
  swapchain target (`BT.2020/PQ`, 0.005-3999.7 nit). That bypassed libplacebo output tone
  mapping and depended on Android retaining and correctly mapping the custom Vulkan child
  surface as HDR. The user-visible result on V2453A was an almost-black picture.
- Fix: retain raw Profile 5 sampling and per-frame RPU reshape, but request an explicit
  sRGB/BT.709 swapchain target. libplacebo now performs the DV-to-display tone mapping before
  presentation; Exo routing, MediaCodec selection, RPU parsing and component mapping are
  unchanged.
- Build: `:app:assembleMobileArm64_v8aDebug` passed with JDK 17. APK SHA-256:
  `cf01c3fc4ebbbce8833f7d066f23ac2a175731db9ae481f7a25038583139961b`.
- Device: installed on V2453A (`10CF6H1D2L0009S`) and launched
  `P5_Dolby_Amaze.mkv`. The renderer remained Exo with `c2.qti.hevc.decoder`; RPU parsing and
  initial frame scheduling succeeded, with no MPV switch, `VideoSinkException`, native render
  failure or crash in the focused run.
- Output contract: SurfaceFlinger reports the active 3840x2160 video layer as
  `dataspace=V0_SRGB`, display `colorMode=SRGB`, and SDR white point about 400 nit. This proves
  the renderer no longer presents 4000-nit PQ as its final output and now performs GPU tone
  mapping. Per the user's instruction, no screenshot or subjective image comparison was used;
  final visual color acceptance remains user-observed.
- Rollback: revert this unit's commit/tag to restore the previous HDR10 swapchain hint without
  changing the earlier RPU queue and timestamp fixes.

- Replan: vivo shell `logcat` and application-UID `run-as logcat` both omit the App's Java/native playback logs even though the installed APK hash matches the local candidate; repeated logcat capture cannot identify the disable cause.
- Next action: replace the existing bounded `Log.i` probes with an App-private diagnostic file, reinstall once, and read that file after one P5 reproduction.

- Checkpoint: App-private diagnostics prove the custom renderer parses 26 RPU units and receives one codec frame, but is stopped before AImage acquisition. Locked Media3 `VideoSink` sources require adding `bufferTimestampAdjustmentUs` inside `handleInputFrame` and using the resulting frame PTS for scheduling and the input Surface timestamp; the raw buffer PTS remains the RPU/native match key.
- Next action: store raw and adjusted PTS in `PendingFrame`, use adjusted PTS for scheduling/AImage timestamp, rebuild, install, and replay P5 once.

- Checkpoint: a direct P5 episode click starts at zero and proves adjusted frame/position scheduling is correct. Three frames reach AImageReader and match RPU, but all native renders fail only after the candidate passed media PTS `0/17/33 ms` as `MediaCodec.releaseOutputBuffer` timestamps instead of absolute monotonic release times.
- Next action: keep adjusted frame/position scheduling and raw RPU matching, but restore monotonic `releaseTimeNs` for codec/AImage release, then rebuild and replay P5 from zero once.

- Checkpoint: monotonic codec release timestamps now let three AImages reach native matching, but all three fail at `missing-rpu`. The decisive evidence is `parsedRpu=47`, `pendingRpu=16`, `rpuQueueDrops=17`: `pendingRpus` incorrectly shares the 16-entry output-frame bound, so decoder input pre-roll discards the earliest RPU metadata before the first output frame arrives.
- Next action: separate the RPU metadata queue bound from `expectedFrames`, preserve enough decoder reorder/pre-roll metadata, then rebuild and replay P5 from zero once.

### E9-3f RPU 预滚队列修复与设备验收

- 根因：`pendingRpus` 错误复用了 `expectedFrames` 的 16 帧上限；MediaCodec
  输入预滚在首个 AImage 输出前已解析 47 至 49 个 RPU，旧策略持续丢弃队首，
  导致首帧对应的最早元数据被删除并命中 `missing-rpu`。
- 修复：为 RPU 元数据使用独立的 256 项有界队列；队列极端溢出时保留最早、
  即将被输出帧消费的元数据并丢弃最远未来项。`expectedFrames` 的 16 帧输出
  匹配边界保持不变，flush/seek 仍同时清空两类队列。
- 构建：`bash gradlew :app:assembleMobileArm64_v8aDebug` 成功；生成的未跟踪
  `app/.cxx` 已整体保留到 `/tmp/e9-cxx-20260828-2BckBs/app-cxx`。
- 真机验收：候选 APK 已覆盖安装到 V2453A（`10CF6H1D2L0009S`），从选集入口
  将 `P5_Dolby_Amaze.mkv` 从 0 秒播放约 42 秒。最终 stats 为
  `renderedFrames=2450`、`renderFailures=0`、`matchedFrames=2451`、
  `unmatchedFrames=0`、`rpuQueueDrops=0`、`malformedRpus=0`；日志无
  `missing-rpu`、`VideoSinkException` 或 `AndroidRuntime` 崩溃。
- 色彩输出日志：source/target 均为 BT.2020/PQ，0.005 至 3999.7 nit、10 bit。
  路径仍为 `MediaCodecVideoRenderer-DV5-Vulkan`，没有自动切换 MPV。
- 原始证据：`/tmp/e9-dv5-rpu-queue-final-private.log` 与
  `/tmp/e9-dv5-rpu-queue-final-logcat.log`。
- Next action: create the atomic E9-3f commit and annotated recovery tag.

### Checkpoint 2026-08-28: external research batch, mpv backends all pass

- 用户补充的设备事实：同一目标设备、同一 DV5 样片，MPV `gpu-next` 的
  `direct`、`stable`、`legacy` 三种 Vulkan AImageReader 后端均能稳定还原色彩。
- 这条事实显著降低了“Android external-format、AHardwareBuffer 通道顺序、
  YCbCr sampler/range 或单一 Vulkan 后端”的可能性；三种后端共享的上游
  `mp_image -> libplacebo` Dolby Vision 表示更值得优先对照。
- 已核对本地锁定 MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg
  `04482c8d13ac27b2a9fe93f5d388929eef8af5f4`、libplacebo
  `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 及本地 stable override；MPV
  `raw_dovi` 会保留原始 DV representation 和三分量语义，stable 另用固定
  输出池/fence 保证 AImage 生命周期。
- 外部 issue：mpv #10287 记录无 DV license 的 Snapdragon 设备在
  `mediacodec-copy` 下出现紫绿，而软件/gpu-next 映射正常；mpv #10700 记录
  P5 的错误颜色与 tone-mapping/backend 输入表示有关。这些支持“必须保留
  raw DV + RPU GPU mapping”，不支持 P5 直接改标 HDR10/P8.1。
- 本轮仍为 assessment；未修改生产代码，保护脏文件
  `app/src/main/cpp/exo_dovi_renderer.cpp`。下一动作：抓取锁定 MPV direct、
  FFmpeg DOVI 状态更新和 libplacebo frame mapping 的完整源码，逐字段对照
  Exo 当前实现，形成推荐/否决矩阵。
- External research confirms same DV5 sample is correct with MPV gpu-next direct, stable, and legacy; prioritize shared FFmpeg/libplacebo RPU representation and preserve existing dirty native file.
- Fetch locked MPV direct, FFmpeg DOVI state, and libplacebo mapping sources into /tmp and compare exact fields

### Checkpoint 2026-08-28: narrow the remaining fault domain

- Authority remains assessment-only for this batch; no production code has been changed.
- User-confirmed evidence: the same device and Profile 5 sample produce stable correct color
  with MPV gpu-next `direct`, `stable`, and `legacy`. This rules against a defect unique to
  one MPV AHardwareBuffer import backend and makes the shared FFmpeg/libplacebo Dolby Vision
  frame representation the primary reference contract.
- Current strongest hypothesis: Exo's hand-built `pl_dovi_metadata` and simplified RPU
  carry-forward/frame-selection semantics diverge from FFmpeg's stateful `DOVIContext`.
  Counter-hypothesis: Exo feeds equivalent metadata and the remaining fault is output target
  or resource lifetime. The next source comparison must distinguish these before any edit.
- Protected pre-existing dirty path remains
  `app/src/main/cpp/exo_dovi_renderer.cpp`; do not overwrite or commit it during assessment.
- Next action: compare the locked FFmpeg DOVI parse/state functions and MPV/libplacebo frame
  handoff field-by-field with Exo's current RPU parsing and render path, then record a
  recommendation and acceptance/rollback boundary.

### Checkpoint 2026-08-28: MPV 三后端证据后的有限对照

- 用户确认同一样片在 MPV gpu-next `direct`、`stable`、`legacy` 均稳定正常；因此不再把单一
  Vulkan/AHardwareBuffer 后端作为首要假设。
- 锁定源码对照显示 MPV raw DV 路径使用 `pl_map_avdovi_metadata` 的完整 FFmpeg
  `AVDOVIMetadata`，并读取 AImage dataspace；Exo 当前使用 libdovi 结果手工维护
  `pl_dovi_metadata`，且固定 RGB identity/full YCbCr 采样。这是当前最强的可证伪差异。
- 未新增生产代码；受保护脏路径仍为 `app/src/main/cpp/exo_dovi_renderer.cpp`，本文件为任务记录。
- 下一动作：完成不超过一批的外部主源码/issue核查，并在设备重新在线时仅读取当前 APK 的
  ExoDv5 私有日志，确认 target/dataspace/RPU 状态后再决定是否编辑代码。

### Checkpoint 2026-08-28: Exo 与 MPV 共享语义的最终收敛

- 用户确认同一样片在 MPV `direct`、`stable`、`legacy` 均为正常色彩，故排除某一种
  Vulkan/AHardwareBuffer 后端独有缺陷；三条路径共享的 FFmpeg Dolby Vision 状态和
  libplacebo raw-DV 表示是当前基准。
- 已确认当前样片前几帧为 Profile 5、`use_prev_vdr_rpu=0`、每帧均有 mapping 与未压缩
  DM；完整 `vdr_rpu_id`/DM 继承仍是通用缺口，但不能单独解释本样片持续异常。
- 已确认 libdovi Profile 5 默认 IPT-PQ 矩阵与 Exo 当前常量一致，不能替换为 FFmpeg 的
  非 Profile-5 默认颜色矩阵。
- 当前只剩三个有决定力的字段簇：libdovi 到 `pl_dovi_metadata` 的曲线系数映射、
  AHardwareBuffer 的实际 sample depth/crop、以及 swapchain 返回的真实 target 色彩合同。
- 保护现有 `app/src/main/cpp/exo_dovi_renderer.cpp` 未提交时间戳匹配改动；本轮不修改
  播放器路由、MPV 策略或其他模块。
- 下一动作：逐字段核对上述三个字段簇，选择一个可由源码或设备日志证实的单一根因，
  再做最小 native 修复并进行一次 arm64 构建和一次目标设备播放验证。
- Checkpoint: MPV三后端均正常，已排除单一Vulkan后端；当前仅核对曲线映射、sample depth/crop与swapchain target
- Next action: 逐字段核对三个字段簇并修复一个可证实根因

### E9-3h RPU pivot 差分解码修复

- 直接根因：Dolby Vision `pred_pivot_value` 在 RPU 中是差分编码。FFmpeg
  `dovi_rpudec.c` 读取时逐项累加后保存绝对 pivot；libdovi C API 则暴露原始编码值。
  Exo 旧实现直接归一化每个原始值，导致多 pivot 帧的分段边界乱序，libplacebo
  reshape 选中错误曲线。两 pivot 或特定曲线帧可能偶然接近正确，符合用户观察到的
  “约第 3 秒只有一瞬间色彩正常”。
- 交叉证据：libdovi 自带 Profile 8.4 曲线为
  `63,69,230,256,256,37,16,8,7`，该序列只有作为差分累加才可能形成有序 pivot；
  FFmpeg 锁定源码明确执行 `pivot += get_bits(...)`。
- 修复：`mapRpuCurve()` 在按 BL bit depth 归一化前先累加并夹取 pivot，保持系数、
  Profile 5 矩阵、时间戳匹配、swapchain 输出和播放器路由不变。
- 构建：`:app:assembleMobileArm64_v8aDebug` 成功，APK SHA-256 为
  `72fe4c5a391d4543d1473c36c954d52c26b2856d6f5dc2832835b9a88fc41893`；生成的
  `app/.cxx` 已整体保留到 `/tmp/e9-pivot-cxx.36DbvT/app-cxx`，未进入任务范围。
- 回滚锚点：`recovery/E9-3-color-input/20260828074016-eb5f7af998d3`。
- 真机：APK 已覆盖安装到 V2453A，并从本地选集播放 `P5_Dolby_Amaze.mkv`。
  路径保持 Exo `c2.qti.hevc.decoder`，未切换 MPV；一轮统计为
  `matchedFrames=2433`、`unmatchedFrames=0`、`expectedQueueDrops=0`、
  `renderFailures=0`、`malformedRpus=0`。
- 真机日志证明 RPU/帧配对和 Vulkan 渲染链稳定；最终视觉色彩仍以用户观察为准。
- 证据：`/tmp/e9-pivot-private.log`、`/tmp/e9-pivot-logcat.log`。

### E9-3 DV5 切换 DV7 的 Surface 生命周期修复

- 复现：DV5 使用自定义 Vulkan renderer 播放后直接切到 DV7；DV7 已正确选择
  HDR10 fallback，但普通 HEVC decoder 配置时报
  `nativeWindowConnect ... Invalid argument (-22)` 和
  `ERROR_CODE_DECODER_INIT_FAILED`。刷新或重进播放后恢复。
- 根因：track replacement 只调用 `ExoDv5GpuRenderer.onDisabled()` 并释放 DV5
  codec，没有调用 sink 的永久 `release()`；native Vulkan swapchain 因而继续连接
  外层输出 Surface，阻止后续普通 `MediaCodecVideoRenderer` 连接。
- 修复：codec 的 `super.onDisabled()` 完成后调用可复用的 `sink.disable()`，关闭
  native renderer、Vulkan swapchain 和 AImageReader，清空 pending frame 并重置渲染状态；
  保留 Java `outputSurface` 引用且不设置永久 `released`，使之后切回 DV5 时可重新初始化。
- 验收：arm64 构建并安装后执行 DV5 -> DV7；日志中 `sink disable` 必须先于 DV7
  decoder configure，且不得再出现 Surface `-22` 或 decoder init failure。再切回 DV5
  应重新初始化并出首帧。
- 结果：`:app:assembleMobileArm64_v8aDebug` 成功并覆盖安装到 V2453A；设备私有日志
  记录 `sink disable`，切换后的聚焦 logcat 未再出现 `nativeWindowConnect -22`、
  `Failed to connect to surface` 或 `ERROR_CODE_DECODER_INIT_FAILED`，用户确认复现流程
  已恢复正常。DV5 停用前统计为 `matchedFrames=855`、`unmatchedFrames=0`、
  `expectedQueueDrops=0`、`renderFailures=0`。构建生成的未跟踪 `app/.cxx` 已完整保留到
  `/tmp/e9-dv7-surface-cxx.FeWxKI/app-cxx`，未纳入任务范围。
- 回滚锚点：`recovery/E9-3-frame-rpu-match/20260828174850-e6a0ee439028`。
