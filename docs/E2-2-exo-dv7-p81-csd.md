# E2-2：Exo DV7 转 P8.1 同步重写 CSD

- 任务 ID：`E2-2`
- 类别：Exo 依赖
- 所属功能：`E2` Dolby Vision/HDR
- 历史别名：`A1-2`（只用于查找旧提交和 task guard 记录）
- 唯一文档：`docs/E2-2-exo-dv7-p81-csd.md`
- 状态：已实施；MTK 合成 CSD 首帧回归已修复，针对性单测通过，实机结论按下方检查点保留。
- 下一动作：本 CSD 单元不继续扩展；后续 parser safety 使用 `E2-1`，output/fallback policy 另按其稳定任务 ID 记录。

## 范围与来源

- 类别：Exo App 适配层，不重建 Media3/nextlib AAR，不修改 FFmpeg、MPV 或 native lock。
- 上游参考：FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` 的 `dovi_rpu convert=p81` 配置语义。
- 当前基线：`9f946cfb003e721c2c36dde1a197c4ce86422cee`；E1 已完成提交 `0b09fc0944a0ef3c21f423e470ece93f3193690c`，恢复 tag 为 `recovery/exo-e1-ffmpeg-9.0.1/20260822093504-0b09fc0944a0`。
- 历史 task guard：`exo-a1-2-dv-csd`。

## 目标

现有 DV7→P8.1 流程只改 codec string，可能产生“P8.1 codec string + P7 CSD”的不一致。本阶段在同一个 `Format` 中同步写入 Dolby Vision configuration record：

- profile = 8；
- level 保持源 codec string 的 level；
- `rpu_present = 1`、`el_present = 0`、`bl_present = 1`；
- base-layer signal compatibility ID = 1；
- metadata compression = 0。

这只是元数据一致性修正，不是启用 FFmpeg `dovi_rpu` BSF。现有硬件能力判断、加密禁用、P8.1 会话锁定、转换失败中止、HDR10 策略和诊断/fallback 均保持不变。

## 实现

- `DolbyVisionP81ExtractorsFactory.asProfile81()` 只处理 DV7，使用 Media3 四参数 `buildDolbyVisionInitializationData(8, level, 1, 0)`。
- `csd-2` 已存在且是合法 DV CSD 时原位替换为 P8.1 CSD。
- 容器没有 DV `csd-2`（当前百度网盘 MKV 的实际情况）时，保留原始 HEVC 初始化数据，不再插入 `csd-2`，也不补空 `csd-1`。这样避免把仅有 `csd-0` 的 MKV 改造成 MTK 解码器无法正常出首帧的人工 CSD 布局。
- 非 DV7 格式保持原对象语义，不修改 codec 或 CSD。

## 验证与风险

已运行：

```text
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \\
  --tests 'com.fongmi.android.tv.player.exo.DolbyVisionP81ExtractorsFactoryTest'
```

结果：`BUILD SUCCESSFUL`；`DolbyVisionP81ExtractorsFactoryTest` 通过。

已覆盖的单测场景：codec/CSD 同步、level 保留、已有 DV CSD 替换、缺失 CSD 不合成、非 DV index 2 保留、非 DV7 不修改，以及既有 HDR10 fallback 行为。

未覆盖：真实 DV7 MEL/FEL 样片、各厂商硬解实际接受的 CSD、跨 seek/segment 实机行为。该风险不改变本阶段的 App 层范围；失败时回滚本阶段提交即可，不能回滚 E1 或改变 MPV native。

## 回滚与下一步

- 预实施回滚点：`9f946cfb003e721c2c36dde1a197c4ce86422cee`。
- 原实施提交（引入回归）：`9306df6afa3d20514764fb8e3ccda08c147e8ffc`。
- 本次修复提交：`5e264c409d1d41002d707dfb5cface8f733a41ff`。
- recovery tag：`recovery/exo-dv7-p81-csd-compat/20260822170928-5e264c409d1d`。
- 它只回滚合成 CSD 配置行为，不回滚 E1、FFmpeg、MPV 或 HDR10 逻辑。
- 后续不重建 AAR/native；目标电视回归成功后，保留本修复提交作为新的 Exo 回滚边界。

## 2026-08-22 实机回归检查点

- 当时基线 HEAD：`c49c13759b05677262a8dd227f4aa059b14eeef1`；该轮修复修改本任务文档、`DolbyVisionP81ExtractorsFactory.java` 及对应单测。
- 同一百度网盘 DV7 资源的 HTTP 请求正常返回 `206 Partial Content`，输入访问单元持续到达，因此界面显示的“连接超时”不是网络根因，而是 15 秒内没有首帧后被通用启播计时器终止。
- Exo 已选择 DV7→P8.1，`c2.mtk.dvhe.st.decoder` 初始化成功；转换输出持续包含 VCL 与 RPU，转换结果为有效，但始终没有首帧，也没有可用于证明“不支持 P8.1”的 decoder 异常。
- 设备的 P8.1 与 HDR10 支持均视为既有事实。本轮禁止以设备能力不足为理由回退 HDR10，也不把 HDR10 当作修复 P8.1 的替代目标。
- 已采取的最小修复：撤销对缺失 CSD 的合成，仅在容器已有合法 DV CSD 时原位改写 profile；这保持 codec string 的 DV7→P8.1 转换和逐帧 RPU 转换不变。
- 已确认 Media3 四参数方法的语义是 `(profile, level, blSignalCompatibilityId, mdCompression)`；当前值 `(8, level, 1, 0)` 不能仅凭单测判定为厂商 codec 可接受。
- 静态差异检查和 `DolbyVisionP81ExtractorsFactoryTest`（13 项）已通过。
- 唯一剩余验证：用户在同一目标电视播放同一 DV7 资源，确认仍选择 `dvhe.08.xx` / `c2.mtk.dvhe.st.decoder`，出现首帧/READY，不再触发 `error_play_timeout`，且不进入 HDR10 fallback。设备支持 P8.1/HDR10 是既定前提，不作为本次验证变量。
