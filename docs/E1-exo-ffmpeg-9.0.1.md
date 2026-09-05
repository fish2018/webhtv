# E1：Exo 底层 FFmpeg 9.0.1

- 任务 ID：`E1`
- 类别：Exo 依赖
- 唯一文档：`docs/E1-exo-ffmpeg-9.0.1.md`
- 状态：已完成并提交。
- 下一动作：无需继续修改 E1；由用户决定是否进入 `E2-1`，`C2` 继续保持未启用。

## 当前恢复锚点

- 分支：`fongmi-sync`
- 回滚基线：`4b50754d3a2902eb4f94361669aa52079f3a2917`
- 任务：将 Exo/nextlib 内置 FFmpeg 从 `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 升级到 `177f090e0503b7e013922ca903bde14b1c375f18`。
- 新坐标：`io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1-fongmi-softload-av3a-ffmpeg901-r1`
- 实施提交：`0b09fc0944a0ef3c21f423e470ece93f3193690c`
- Recovery tag：`recovery/exo-e1-ffmpeg-9.0.1/20260822093504-0b09fc0944a0`

## 实施边界

本阶段只升级 Exo/nextlib 的 FFmpeg 安全与维护基线，保留 nextlib `6ff6cf9d0820382b3c233d018c52e4163b09d345`、NDK `28.2.13676358`、CMake `3.22.1`、FFmpeg 软解负载控制和 AV3A/libarcdav3a 支持。MPV 仍使用其独立的 FFmpeg `04482c8d13ac27b2a9fe93f5d388929eef8af5f4` 构建和 `libmv*`/`libmw*` 命名，本阶段不修改 MPV 资产。

## C2 与当前 DV7→P8.1 实现的关系

FFmpeg 提交 `177f090e0503b7e013922ca903bde14b1c375f18` 为 `dovi_rpu` bitstream filter 增加 `convert=p81`。它负责 HEVC packet 级别的 Profile 7 到 Profile 8.1 码流转换：重写 RPU/配置并删除 enhancement layer，但不负责 Android 设备能力判断、用户选择、DRM 限制、renderer 选择、会话锁定、失败策略或播放诊断。

WebHTV 当前 `DolbyVisionP81ExtractorsFactory` + ExoplayerHdrUtils/libdovi 方案已覆盖上述播放器策略，并只在原 DV7 无可用硬解、P8.1 有可用硬解且内容未加密时转换；转换失败会中止已锁定的 P8.1 会话。因此对当前 Exo 场景，现有方案整体更完整，不应由 FFmpeg C2 直接替换。

C2 有一项明确参考价值：它会让转换后的 DV 配置与 P8.1 码流保持一致。当前 WebHTV `asProfile81()` 只把 codec string 从 Profile 7 改为 Profile 8，没有同步重建 `Format.initializationData` 中可能存在的 Profile 7 DV CSD。后续 A1-2/DV 专项应借鉴 C2 的结果语义，将配置设置为 Profile 8、`rpu_present=1`、`el_present=0`、`bl_present=1`、compatibility ID `1`，或在无法可靠重建时移除旧 DV CSD。本 E1 不改 DV 播放行为，也不调用 `dovi_rpu=convert=p81`。

## 构建与验收记录

检查点：E1 构建输入已更新：nextlib 新坐标与 FFmpeg 177f090e 完成一致性校验；C2 明确不接入，仅记录 CSD 参考价值。

下一动作：清理非 Git 的旧 nextlib 缓存目录并运行双 ABI nextlib 构建。

检查点：nextlib `6ff6cf9d0820382b3c233d018c52e4163b09d345` 已重新检出，两个 nextlib 补丁均已成功应用；首次构建未进入编译，原因是当时指定的外部 JDK 路径不存在。项目构建需要 JDK 21。

下一动作：使用 JDK 21 仅重试一次同一 `--nextlib-only` 构建；若 Java 环境仍不满足项目要求，则记录环境阻塞，不继续重复尝试。

构建结果：`bash scripts/build_media_deps.sh --nextlib-only` 成功，Gradle 报告 `BUILD SUCCESSFUL in 14m 25s`。构建输入为 nextlib `6ff6cf9d0820382b3c233d018c52e4163b09d345`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、NDK `28.2.13676358`、CMake `3.22.1`；两个 ABI 的 mbedTLS、libvpx、libarcdav3a 和 FFmpeg shared libraries 均完成。

产物：`io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1-fongmi-softload-av3a-ffmpeg901-r1`。AAR SHA-256：`89ac342c534a862743dde58ffa2803e9fa1eecd2462c25d6d6b1b5f6ea048d00`。POM SHA-256：`ccb18e4ee34d12369402bb05dfa6f3e70ced9e9f9fd26f7b28c2833940eb8a68`。Gradle module SHA-256：`42875330d793f9fad5077e87bce969957ac1673ec232a8795f6698fbc020d071`。AAR 内含 `arm64-v8a`/`armeabi-v7a` 的 `libmedia3ext.so`、`libavcodec.so`、`libavutil.so`、`libswresample.so` 和 `libswscale.so`；脚本的 AV3A marker 校验通过。

构建警告：CMake 报告 SDK XML v4 与当前 CMake 3.22.1 的版本提示；编译过程另有既有第三方源码 warning，未升级为错误。它们不改变本次产物结果。

C2 实测边界：FFmpeg 配置输出的 Enabled bsfs 仅包含 `av1_frame_merge`、`evc_frame_merge`，没有 `dovi_rpu`；因此本次裁剪版 nextlib AAR 没有编译或暴露 C2 的 `convert=p81` 能力。C2 仍只作为后续 DV CSD 一致性设计参考，不属于本次升级的可用功能。

App 验证：`bash ./gradlew :app:assembleMobileArm64_v8aDebug` 成功，`BUILD SUCCESSFUL in 1m 28s`（101 actionable tasks，10 executed，91 up-to-date）。

完成结果：E1 的 lock、nextlib patch、双 ABI AAR/POM/module、README、开发文档和实施记录已同步；C2 未接入。实施提交为 `0b09fc0944a0ef3c21f423e470ece93f3193690c`，本地回滚锚点为 `recovery/exo-e1-ffmpeg-9.0.1/20260822093504-0b09fc0944a0`。
