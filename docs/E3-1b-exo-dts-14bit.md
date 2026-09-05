# E3-1b：Exo DTS 14-bit frame-size correction

- 任务 ID：`E3-1b`
- 所属分类：Exo
- 状态：已实施并完成验证
- 唯一任务文档：`docs/E3-1b-exo-dts-14bit.md`
- 用户授权：2026-08-26 明确要求开始下一个任务并优先快速完成
- 实施基线：`recovery/E3-1b-baseline/20260826-9c347cc688c2`
- 上游来源：`FongMi/media@d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4`

## Recovery anchor

- 目标：修正 14-bit DTS core 帧大小的整数换算，避免 DTS-CD WAV、raw DTS 和 TS 中帧边界多算 1 字节，导致后续 sync word 错位和音频损坏。
- 范围：只移植 `DtsUtil.getDtsFrameSize` 的 14-bit 公式和最小边界测试；不移植上游无关的 `findDtsCoreSync`、WAV 探测或 reader 主体。
- 接受标准：补丁按现有六个 Media3 patch 后可重放；14-bit BE/LE 与 `FSIZE+1=3585` 测试通过；`lib-extractor` 定向测试和 Java 编译通过；只更新 `media3-extractor` AAR/sources 及 lock；初始 dirty 文件不被覆盖或提交。
- 当前状态：窄补丁、DtsUtilTest、`lib-extractor` Java 编译、AAR 最小更新和 App 接线编译已完成；实现提交 `27b85eeeed5ceb55e56a67ae3b5cf8ff64b8da40`，恢复标签 `recovery/E3-1b/20260826201735-27b85eeeed5c` 已创建。
- 下一动作：完成本次修正提交后，开始 E4-1 评估。

## 实际能力

播放使用 14-bit DTS 编码的音乐 CD WAV、raw DTS 或 TS 时，播放器会正确计算每帧真实占用的物理字节数，连续帧不再因向下取整顺序错误而错位。普通 16-bit DTS 和其他音频格式行为不变。

## 证据与设计决策

- 评估索引 `docs/upstream-player-dependency-merge-assessment-2026-08-20.md` 第 9.3 节已确认当前 fork 的 WAV 探测、第二音轨和 `DtsReader` 接入已存在，缺口仅为 14-bit frame-size 整数换算。
- 上游 `d500eb27ea994ffaa8ea2b48863a0c7aaef0e4b4` 将公式从 `fsize * 16 / 14` 改为 `(fsize * 8 / 14) * 2`，先取得完整 14-bit word 数再换算容器字节；`FSIZE+1=3585` 时结果从 4097 修正为 4096。
- 当前 `DtsUtil.java` 仍使用旧公式；`DtsUtilTest` 只有 DTS:X marker 测试，没有 14-bit frame-size 覆盖。
- 采用窄适配，不移植上游 `findDtsCoreSync`，因为当前 fork 尚未需要该 API，扩大范围会增加容器和输入探测风险。

## 收益、风险与回滚

- 收益：修复 14-bit DTS 连续帧边界，避免后续音频静音、噪声或解析失败。
- 风险：错误的测试样例或公式适配可能影响 14-bit raw DTS/WAV/TS；软件/硬件 decoder 不受影响。变更只增加整数运算，性能和包体积影响可忽略。
- 兼容性：16-bit DTS、DTS-HD、DTS:X、E-AC3、TrueHD 和现有 extractor 接线不变。
- 回滚：恢复 E3-1b 实现提交和 `media3-extractor` AAR；必要时回到 `recovery/E3-1b-baseline/20260826-9c347cc688c2`。

## 实施记录

- 补丁：`third_party/patches/media3-exo-dts-14bit-frame-size.patch`，SHA-256 `328fe823b824fd36e359ea16eea4c77334ea707676e9a2803258f29b661e3d77`。
- 定向验证：独立 Temurin JDK `21.0.12.1+1`、Gradle 9.1.0、工作区 Gradle 缓存和代理；`:lib-extractor:testDebugUnitTest --tests androidx.media3.extractor.DtsUtilTest :lib-extractor:compileDebugJavaWithJavac`，`BUILD SUCCESSFUL in 7m 45s`。
- App 接线验证：独立 Temurin JDK `21.0.12.1+1`、`bash gradlew --no-daemon --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`，`BUILD SUCCESSFUL in 4m 56s`。
- AAR：仅替换既有已验证 `media3-extractor` AAR 内的 `DtsUtil.class`，保留 E-SP2/E2-1 其他 classes；SHA-256 `33109a547e7f27c1110e785ae77e8ab1e9584a24a9f904573ce770129aa4475a`。
- sources：仅替换 `DtsUtil.java`，SHA-256 `b4d65656b5d56ea8a66580d03178b98dbecac3840791a30820ae184f3d1ca416`。
- 发布限制：完整 `:lib-extractor:publishReleasePublicationToMavenRepository` 在现有 dirty Media3 checkout 被既有 `MatroskaExtractor.samplesHaveSupplementalData` 静态上下文错误阻塞；未修改受保护 checkout，采用已验证 AAR 的最小 class/source 替换并通过 ZIP、源码和校验文件验证。

## 本次直接合并记录

- 上游来源：`fish2018/fongmi-sync@27b85eeeed5ceb55e56a67ae3b5cf8ff64b8da40` 与后续记录提交 `d95bfd4df4dc8824ff8ce36e78b2355241654797`。
- 已接入上游 E3-1b 的 DTS 14-bit 公式、三条边界测试、补丁和 `media3-extractor` AAR/sources；AAR SHA-256 为 `33109a547e7f27c1110e785ae77e8ab1e9584a24a9f904573ce770129aa4475a`，sources SHA-256 为 `b4d65656b5d56ea8a66580d03178b98dbecac3840791a30820ae184f3d1ca416`。
- 已补齐 extractor Gradle module 中 AAR/sources 的大小、MD5、SHA-1、SHA-256、SHA-512 元数据，并确认每个发布文件的 sidecar 与 module entry 一致。
- `scripts/build_media_deps.sh` 保留 WebHTV 已验证的 `apply_media_patch_lf()` Windows CRLF fallback，仅为 Media3 patch 调用增加 `--unidiff-zero`，兼容上游零上下文 DTS patch；nextlib 和镜像 patch 的应用路径不变。
- `third_party/media-lock.json` 已登记 E3-1b patch、extractor 产物和已采用的上游 E3-1a exoplayer 产物 SHA-256。
- 当前 checkpoint：E3-1b source/config/artifact unit ready; next: commit this verified unit, then create the fongmi-sync two-parent merge commit。

## 本次直接合并记录

- 上游来源：`fish2018/fongmi-sync@27b85eeeed5ceb55e56a67ae3b5cf8ff64b8da40` 与后续记录提交 `d95bfd4df4dc8824ff8ce36e78b2355241654797`。
- 采用上游 E3-1b 的 `DtsUtil` 公式、三条边界测试、补丁和 extractor 产物；不引入同分支中无关的 E3-1a 文档覆盖。
- 构建脚本保留 WebHTV 已验证的 `apply_media_patch_lf()` Windows CRLF fallback，仅让 Media3 patch 调用额外使用 `--unidiff-zero`，以兼容上游零上下文 DTS patch。
- `third_party/media-lock.json` 已登记 E3-1b patch SHA-256；extractor AAR/sources 与 module 内嵌摘要将在下一单元按实际文件重新核对并更新。
- 当前 checkpoint：E3-1b source/config unit ready; next: validate and commit source/config unit, then finalize extractor publication metadata。

## Checkpoint 2026-08-28: E3-1b publication validation

- E3-1b publication metadata and artifact validation recorded：补丁与 `third_party/media-lock.json`、extractor AAR/sources、`.module` 内嵌摘要和全部 MD5/SHA-1/SHA-256/SHA-512 sidecar 已按实际文件核对一致；构建脚本语法检查通过。
- 验证记录：`media3-extractor` AAR SHA-256 为 `33109a547e7f27c1110e785ae77e8ab1e9584a24a9f904573ce770129aa4475a`，sources SHA-256 为 `b4d65656b5d56ea8a66580d03178b98dbecac3840791a30820ae184f3d1ca416`，patch SHA-256 为 `328fe823b824fd36e359ea16eea4c77334ea707676e9a2803258f29b661e3d77`。
- Workspace：`dev2@c10670e2df54553229b8303f35e01ac2b9d247a6`；范围外无预先脏路径，E3-1b 任务文件均待本次原子提交。
- Rollback anchor：`recovery/E3-1b-baseline/20260826-9c347cc688c2`；回滚时恢复 E3-1b 实现提交及 `media3-extractor` AAR/来源产物。
- Next action：commit the verified E3-1b unit。

## Checkpoint 2026-08-28: E3-1b implementation closeout

- E3-1b 原子提交：`b75ba0a5ca6e3ecf2e494f308c0296496c5a2332`；恢复标签：`recovery/fongmi-sync-e3-1b-dts/20260828010623-b75ba0a5ca6e`。
- 本地提交包含补丁、构建脚本、lock、`media3-extractor` AAR/sources、module 元数据及 sidecar；module 内嵌摘要按实际 artifact 重新生成并通过定向校验。
- `fish2018/fongmi-sync` 当前完整头为 `cafd4f69e613a5db49df5e38e762b6bf4fe58819`。三方预演的 `-X theirs` 目标树会删除本任务文档并回退已验证的 module 摘要和 Windows patch fallback，因此不采用该目标树。
- 合并边界：保留当前 WebHTV 验证树，以 `fish2018/fongmi-sync` 作为真实双亲提交的第二父；不以合并动作重新覆盖已验证产物，也不引入上游未批准的其他功能树。
- 双亲合并提交：`130eac99f735f47284106621bad4795281724786`；父提交为本地 `1cf029bc77f791f8b6b992a004704f1d93e8fa82` 与上游 `cafd4f69e613a5db49df5e38e762b6bf4fe58819`。
- 合并恢复标签：`recovery/fongmi-sync-merge-closeout/20260828013239-130eac99f735`。
- Next action：E3-1b closeout complete; proceed to E4-1 assessment。

- Merge closeout preparation：将以 `fish2018/fongmi-sync@cafd4f69e613a5db49df5e38e762b6bf4fe58819` 为第二父创建 `ours` 双亲提交；当前验证树保持不变。

## Checkpoint 2026-08-28: E3-1b merge completed

- 双亲提交 `130eac99f735f47284106621bad4795281724786` 已创建并打恢复标签；相对第一父仅增加本合并准备记录，关键功能、lock 和 artifact 树保持已验证状态。
- Next action：E3-1b closeout complete; proceed to E4-1 assessment。
