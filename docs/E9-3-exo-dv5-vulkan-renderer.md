# E9-3：Exo DV5 GPU 映射默认准入

## Recovery anchor

- 目标：按用户 2026-09-12 的明确要求，默认注册满足 native 能力条件的 Exo DV5 GPU 映射 renderer，不开启其他播放实验。
- 分支/回滚基线：`feature-menu` / `969261479167bca3f8f16f11551de7dcc9290112`。
- Lane/guard：`quick-fix` / `E9-3-DV5-DEFAULT`。
- 范围：`ExoUtil.java`、`ExoDv5GpuRendererFactory.java`、`ExoDv5GpuRendererTest.java`、本文及任务索引。
- 保护：任务开始前已有的 `app/.cxx/` 35 个未跟踪缓存文件，不修改或提交。
- 状态：默认注册已实现；DV5 renderer、映射策略及实验策略定向单测通过，Mobile/Leanback arm64 Java 编译通过。未重建 native，未打包或安装，当前偏色场景尚未真机复验。
- 下一动作：由当前 guard 原子提交已验证的本轮改动并创建本地注释恢复 tag，不推送。

## 历史记录与当前证据

原 E9-3 文档随主线的根目录任务文档清理被删除，本轮沿用同一任务 ID 和文件名恢复当前记录，不另建平行任务。完整历史保存在 `9fcab83f9084446566240a8e8f5233d87d0274cc:docs/E9-3-exo-dv5-vulkan-renderer.md`。

- `e6a0ee439028f5274f7dc51c7b15770b3afe2ba6` 修复 RPU pivot 差分解释及帧元数据匹配；`6a3ddd266a94a6b984099876631cc6260e77b776` 修复 DV5 停用时的 Surface 释放。该链路不是把 DV5 简单改标 HDR10，而是 HEVC MediaCodec + AImageReader/AHardwareBuffer + 独立 Vulkan/libplacebo 映射，输出 SDR。
- 本轮修改前，`ExoUtil.FfmpegRenderersFactory.buildVideoRenderers()` 把 `PlaybackExperimentSetting.isDomainEnabled(EXO)` 传给 DV5 工厂，而 `PlaybackExperimentPolicy.State.stable()` 默认总开关为 false，因此普通配置不会注册该映射 renderer。本轮移除这一实验门控，默认按 native probe 注册；实验总开关本身不变。
- 原生优先、Profile 5、非 DRM、硬件 HEVC 等判断位于 `ExoDv5GpuRenderer`；API/native 库/Vulkan/AHB 条件由 `ExoDv5Native.probe()` 保留。本轮不修改它们。
- 历史验证覆盖 V2453A 和 `P5_Dolby_Amaze.mkv`，不代表全机型或所有 RPU 状态已验证。本轮未取得当前偏色场景日志，不能断言默认关闭是当前设备偏色的唯一原因。

## 本轮设计决定与边界

沿用历史 E9-3 的 MPV/libplacebo、Android AHardwareBuffer/Vulkan 和 Media3 VideoSink 研究；本轮仅调整已建立设计的局部默认注册条件，不新增渲染算法、依赖、ABI 或原生资产，因此不重复外部综述或原生构建。

| 方案 | 决定 |
| --- | --- |
| 不改 | 默认仍无法选择已实现的 DV5 映射，不满足用户要求 |
| 打开全局/Exo 实验总开关 | 会同时影响其他实验，超出本轮行为范围 |
| DV5 工厂默认按 native probe 注册 | 采用；与其他实验开关解耦，renderer 仍只认领适用的非 DRM DV5 硬解内容 |

不改原生杜比优先级、普通视频/DV7 路径、现有失败回退或色彩算法；不声称修复所有设备/素材的偏色。新默认值会让更多满足现有条件的设备实际选择该路径，因此仍需用户用原场景复测。回滚本轮单一提交即可恢复实验门控，不涉及 native 成套回滚。

## 验证计划

- 工厂测试：默认稳定配置下完整 probe 可准入，缺失/失败 probe 不准入；实验总开关保持关闭。
- 回归现有 DV5 路由/RPU 提取/帧调度测试及 `PlaybackExperimentPolicyTest`。
- 使用 JDK 21 跑 Mobile arm64 定向单测并编译共享入口的 Leanback arm64 Java；不打完整 ABI 矩阵，不重建 native，不安装手机。
- 通过后记录结果，并由同一 guard 原子提交及创建本地注释恢复 tag，不推送。

## 实施与验证结果（2026-09-12）

- `ExoDv5GpuRendererFactory.shouldCreate()` / `create()` 删除实验开关参数，仅按非空且可用的 native probe 准入；`ExoUtil` 对应删除实验域参数，保留原注册顺序和失败处理。
- 更新工厂测试，确认稳定配置的 Exo 实验域仍关闭，但完整 probe 可准入；失败或缺失 probe 仍拒绝。
- JDK 21 下 `ExoDv5GpuRendererTest`、`ExoDv5GpuMappingPolicyTest`、`PlaybackExperimentPolicyTest` 三个定向测试类通过；Mobile arm64 与 Leanback arm64 Debug Java 编译通过。
- 同一次 Gradle 验证结果：`BUILD SUCCESSFUL in 1m 5s`，`84 actionable tasks: 8 executed, 76 up-to-date`。完整日志：`/private/tmp/webhtv-dv5-default.Td3uKE/verification.log`。
- 未执行 externalNativeBuild，未改依赖、native 资产或其他实验策略，未生成/安装新版 APK。手机现有安装包不受源码改动影响，原视频色彩表现需在新版包中复测，不能把编译/单测通过当作已解决当前设备偏色的证明。
- 提交和恢复 tag 由 `E9-3-DV5-DEFAULT` guard 的 finish 记录；回滚本轮原子提交即可恢复旧门控。
