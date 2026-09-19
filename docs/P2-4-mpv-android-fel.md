# P2-4：Android MPV DV7 FEL 双层重建

## Recovery anchor（当前：9.26，恢复显式输出模式的 P8 HDR10 兼容）

- 目标/验收：设备不支持原生 P8 时，沿用已有 HDR10 降级，原样片在用户选择的 GPU/Vulkan、视频硬解模式下实际出帧并可跳转；保留完整 FEL。用户已明确要求恢复该行为，无待批准事项。
- 工作区/回滚：`feature/mpv-dv7-fel` / `c9a1ac99d05f2d8bcac0558b725d34e196b468e2`，已创建 `v5.6.0-202609171247-fel-crash-fixed`；guard `P2-4-p81-hdr10-compat` / quick-fix，保护原有104个 `app/.cxx/` 文件。
- 范围：仅 `PlayerManager.java` 的既有兼容判断入口、本文/索引及 `build/p81-hdr10-compat/`、既有隔离C++构建输出；不改 native、依赖、解码模式或原生DV能力判断。
- 已有证据：手机原 `P8.1_4K60_GlassBlowing.mkv` 在显式GPU/Vulkan硬解下 `Video: no video`，native拒绝不支持的DV路径并正确禁止软件后备；App在非AUTO模式提前返回，已有 `updateDv8Handling` 未执行。证据 `/private/tmp/webhtv-p81-regression-20260917/`。
- 状态：入口修复、48秒Mobile64构建及安装完成，包内27份原生库及10份MPV资产与上个FEL修复包逐字节一致；手机已确认 `hdr10`、`mediacodec`、`pq` 及GPU/Vulkan硬解。用户随后明确“可以了，打个tag”，以用户实播确认闭合该场景，不追加验证。
- 验证边界：自动探针在起播541ms取到单色帧，未完成画面/seek/FEL相邻场景，不能记为自动验证通过；用户实播确认后停止可选检查。FEL实际激活后的提前返回和全部原生库保持。
- 唯一下一动作：按用户确认用当前guard原子提交并创建本地恢复tag，不推送；后台监听保持。

## 9.26 显式输出模式下恢复 P8→HDR10（2026-09-17）

这是既有兼容政策的局部回归修复，沿用本任务已完成的mpv/Android能力研究，不增加上游集成。2026-09-17本地读码证据：`PlayerManager.shouldEvaluateMpvOutput/evaluateMpvAutoOutput` 只为AUTO或FEL请求调度，且显式输出在P8兼容判断前返回；`MpvPlayerEngine.getVideoPlaybackDetails` 已可读取未选中源视频轨，`selectDv8Handling` 已规定“原生DV明确不支持且HEVC HDR10明确支持”时选 `hdr10`；native已有 `demuxer-dovi-profile8=hdr10` 保留HEVC Main10/HDR10基础层并移除DV元数据。使用这些现有入口，保留UNKNOWN三态和严格硬解合同。

方案比较：不改会继续无画面；扩展native对样片兼容ID的处理超出恢复既有HDR10功能所需范围；自动转视频软解违反用户合同。采用窄适配：硬解MPV也调度判断，显式模式仅允许源Profile 8执行已有HDR10兼容，随后立即返回，不进入AUTO输出/渲染选择；兼容变化通过 `rebuildAndRestartMpv(null, ...)` 保持用户GPU/直出及Vulkan/OpenGL设置，重用已有位置/速度/播放状态恢复。实际FEL激活仍先返回，无第二次重载。

手机样片名称虽为P8.1，容器实际声明Profile 8、compatibility ID 6；保持原始声明，不把6改成1、不放宽native拒绝条件。已知正常版本 `v5.6.0-202609061650` 中也存在同类native拒绝条件，因此不能归咎于AVS3新增该条件。

最小验证：一次Mobile ARM64离线构建，包内原生库与已安装FEL修复包逐字节相同；原样片在显式GPU/Vulkan+硬解下实际 `hdr10`/MediaCodec/PQ、真实画面与seek，另核对FEL请求关闭时兼容入口和一个FEL相邻场景。无新依赖、ABI、逐帧处理或native重建。完成后本guard原子提交/tag，不推送；回滚为上述已验证FEL提交。

交付结果：`build/p81-hdr10-compat/gradle.log` 显示Mobile64构建48秒通过；APK为 `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`，165335184字节，SHA256 `18e641a666268a80369bf426d7761f1e9d3d740cacdd63c5d341b677589befe2`。`apk-verification.json` 确认27份原生库、10份MPV资产与基线相同，CRC/v2签名通过，ZIP开销763799字节；`install.log` 确认安装成功。`device-probe.log` 已取得P8 `handling=hdr10 hwdec=mediacodec gamma=pq output=GPU/Vulkan hard=true FEL=false`；首次PixelCopy在541ms仅有一种色值，后续自动场景未执行。随后用户实际播放确认“可以了，打个tag”，接受当前修复并要求立即闭合；未将该自动取帧失败记为通过，未追加构建或相邻测试。

## 历史 Recovery anchor（9.25，FEL 起播配置所有权修复）

- 目标：修复手机开启DV7/FEL后native abort，保留完整FEL及手动视频解码合同；用户2026-09-17明确“修复”，无需再次确认。
- 工作区/回滚：`feature/mpv-dv7-fel` / `8919cf134218a3d3bb30f91f3180e9cd83eac982`，guard `P2-4-fel-context-ownership` / upstream；保护104个原有 `app/.cxx/` 文件。
- 范围：FEL patch中 `vo_gpu_next.c` 配置设置及生成源码、定向内存测试、双ARM `libmpv.so`、本文件/索引/构建记录；不改变FFmpeg、libplacebo、JNI、Exo或其他18份MPV库。
- 已有证据：手机10:24及11:53两次同栈SIGABRT；符号与包内 `.text/.rodata` 逐字节匹配，`preinit→free_option_data→free_obj_settings_list→ta_free`。9.23将静态数组/字符串写入拥有析构权的配置对象，随后释放导致断言。引入提交 `8de0fd70942d513034fb8118de5229d7eb719622`；AVS3前后和手机 `libmpv.so` SHA均 `1d55dbe26cd7192cddf4f7388becdf118c459f7bb4ce15f54e908891af91b454`。
- 状态/证据：修复与定向验证完成，最终Mobile64 APK已安装手机。真实ta回归先复现旧错误再通过7条路径；补丁往返/静态合同、双ABI增量编译、ELF/完整公开导出、18库身份及APK检查通过；手机原样片完整FEL激活、实际画面、seek和退出通过。证据 `build/fel-context-ownership/`；后台监听PID61269在 `/private/tmp/webhtv-fel-phone-restart-20260917/` 继续采集并自动重连。9.24电视性能结论仍待目标电视证据。
- 验收：真实ta和对象配置copy/free复现旧代码非法释放；修复后Vulkan、OpenGL、自动切换、双失败及普通路径均安全且无遗失原配置；双ABI/公开导出/ELF及18库身份保持；手机原样片FEL起播、seek、退出不再出现该崩溃。
- 耗时：12:10开始；原12:25目标因真实allocator回归的host适配超出，12:40目标又因增量APK空洞与OEM辅助包安装拦截超出；仅重新封装APK及复用已有仪器包，12:44完成手机场景。无新依赖、原生全栈重建或泛搜。
- 唯一下一动作：用当前guard原子提交并创建本地恢复tag，保持后台日志监听，不推送。

## 9.25 FEL 起播配置所有权修复（2026-09-17）

设计证据沿用本任务固定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 及现有补丁，不新增上游集成：`options/m_option.h::m_option_copy`、`m_option.c::copy_obj_settings_list/free_obj_settings_list/obj_setting_free` 定义对象配置的深拷贝与释放；`m_config_core.c::free_option_data` 按选项类型析构。2026-09-17已读实际源码（A级）。手机两次符号栈提供独立运行证据；这是已有所有权合同的局部回归，不引入架构或算法，不重复已完成的跨项目研究。

方案比较：保持现状会释放静态内存；取消断言或不释放整个配置会掩盖非法释放并泄漏；手工清空字段易遗失原配置和自动切换的分配。选用mpv现有对象设置copy函数，替换时先释放旧拥有的列表，再深拷贝新列表、名称及属性，由原配置析构统一释放。只在FEL创建GPU上下文时发生两组小配置分配，不进入逐帧路径。Vulkan与自动OpenGL共用同一设置函数，普通播放路径不变。

保留原FEL完整层重建、10bit、BL/EL同步、渲染选择及质量；这里的GPU后端自动切换不改变用户视频硬解/软解模式。native ABI/公开API/依赖版本、许可证与源边界不变；只重建两份libmpv并核对其他18个库，尤其保留已接入AVS3的libmvcodec。测试使用真实allocator与对象copy/free，GPU由桩提供成功/失败分支，不能将主机通过当作手机真实起播通过。

实施与构建证据（`build/fel-context-ownership/`）：

- `baseline-invalid-free-final.log` 由真实 `ta.c/ta_utils.c/ta_talloc.c` 与对象配置copy/free、生产preinit配置块复现旧实现的ASan非法释放；`fixed-memory-contract.log` 通过普通输出、显式Vulkan、显式OpenGL、auto成功、auto回退、双失败、显式Vulkan严格失败7条路径，验证原列表释放和全局名称/label/属性保留。只模拟GPU创建和外层配置容器，不模拟allocator。
- `set_android_fel_context_options` 对两个列表分别调用 `m_option_copy`；初次选择和自动OpenGL回退复用此函数。权威patch只替换 `video/out/vo_gpu_next.c` 块，其他26块原样保留；`patch-verification.json/static-contract.log` 正向/反向应用、实际源码逐字节往返及既有静态FEL合同通过。
- 两ABI只执行 `buildall.sh -n --arch arm64 mpv`、`--arch armv7l mpv`，使用同一NDK29/API24与已有静态依赖；`arm64-build.log/armv7l-build.log` 已通过。随后 `scripts/build_mpv_native.sh --abi all --stage-only` 处理依赖命名空间/strip；只复制两份libmpv到assets。第一次ARMv7复制早于stage完成，ELF检查拦截到旧 `libav*` 命名；等待stage结束重新复制后验证通过，错误中间产物未打包。
- `native-verification.json/native-assets.log`：两库的 `.text/.rodata` 地址与字节对应本次未strip构建；公开动态符号集合和 `SONAME/DT_NEEDED` 与基线相同；其余18库逐字节保持，arm64 AVS3 codec SHA256仍为 `37114bda4673f642229fb71eb3bf4f63d82d3d4939f18cc10556ab51f43359f6`。构建锁四仓及NDK/API沿用9.24，无新依赖或JNI重编。

| 产物 | 字节 | SHA256 |
| --- | ---: | --- |
| FEL权威patch | 见仓库文件 | `e96d4e45bba810f4f87c7fda16fd5207c1e054e180699881bffc1bcaf5094818` |
| arm64 libmpv | 17820224 | `9f231d28bb24c976c7ab89daf2d77ca3197eac3d182119bd4ae926674ebe3c65` |
| armv7 libmpv | 14635396 | `ec640eefa4517ca1d92536327de963bcf8b3bafd8e351437fa87d6425c425ed1` |
| Mobile64 debug APK | 165335184 | `73e6c045ca621a70b15c9764ca3ff612481b92e8c8386173555a17d830eaa782` |

最终打包与手机证据：

- Gradle使用既有 `build/avs3-native/app-build.init.gradle` 隔离C++输出，执行 `:app:assembleMobileArm64_v8aDebug --offline --max-workers=4`。首次81秒成功，但包体积门槛发现42132002字节ZIP开销；保留该中间包及日志，移走本次输出后仅重封装37秒，最终ZIP开销764153字节。`apk-verification.json` 验证CRC/v2签名、10份MPV assets与候选逐字节一致，以及27份 `lib/` 原生库与修复前APK一致。
- 最终包由OEM安装辅助脚本完成风险勾选及安装按钮确认，`final-mobile-install.log` 成功；手机实际base.apk SHA256与表中最终包一致。`device-library-sha256.txt` 确认手机实际提取的arm64 libmpv与候选一致，libmvcodec仍为原AVS3版本。
- `device-fel-probe.log` 使用已有仪器入口调用真实App方法，无UI点选播放：原 `player=2/FEL=2` 保留，vivo V2453A / Android15 / `10CF6H1D2L0009S`，PID11658，原片 `Download/影音测试库/V01_DV_Profile/P7_FEL_4K24_GIJoe.mkv`。native日志明确 `selected-track=1 profile=7 vo=gpu-next api=vulkan single-load=1`；实际 `current-vo=gpu-next`、`current-gpu-context=androidvk`、`hwdec-current=mediacodec`、gamma=`pq`，App实际FEL激活属性为true。PixelCopy起播7789种色值、seek后9101种色值，12秒跳转后播放位置12614ms，同一PID正常stop/退出；不将色值数量当作逐像素FEL质量标定。
- `device-fel-logcat.log/device-fel-web.log` 保留本轮原始记录，`device-fel-summary.log` 提取起播、硬解和FEL渲染记录；未重现 `ta.c` canary / SIGABRT。新建辅助包被OEM拒绝后改为更新原有任务仪器包并通过shell包管理器安装，未改产品权限或关闭设备安全开关。
- 这是FEL起播崩溃修复验收；未重新执行无关AVS3/ASS矩阵，未声称完成电视持续掉帧优化、ARMv7实机或完整画质标定。没有新增逐帧工作；配置分配只发生在GPU上下文初始化/失败回退。用户原有播放与FEL偏好保持，后台监听在12:43:35仍有新日志写入并继续运行。

回滚：以 `8919cf134218a3d3bb30f91f3180e9cd83eac982` 为源/补丁/两份libmpv恢复锚点，修复前两库及Mobile64包保存在证据目录；用本guard提交并创建 `recovery/P2-4-fel-context-ownership/*` annotated本地tag，不推送。最终提交/tag由task guard闭合记录关联，不为填写提交自身ID再重建或重复手机场景。

## 历史 Recovery anchor（9.24，绑定类型对照候选已完成本机验证）

- Objective / acceptance：实施 9.22-B 的第一层裁决：空控制、sampler-only、storage-only、组合布局，均 fresh record，不提交诊断 draw/dispatch。默认不运行，用户主动、独占、30 秒预算，可取消；保留真实 AImage 所有权与 fence，不保存/输出节目像素。
- Current unit：`feature/mpv-dv7-fel` / `8de0fd70942d513034fb8118de5229d7eb719622`；guard `P2-4-fel-bind-probe` / upstream；保护 104 个原有 `app/.cxx/` 文件。上个单元已提交，tag `recovery/P2-4-fel-startup-selection/20260916124938-8de0fd70942d`。
- Scope：MPV 诊断接线、Web 既有诊断抽屉、FEL patch/生成源码、定向测试、双 ARM libmpv、本文及索引；不升级依赖，不改 Exo/JNI/其余 18 库。证据 `/private/tmp/webhtv-fel-bind-probe-pa3hqm7z/`。
- Status：原生对照真实函数及 core/VO ASan/UBSan、Java 2 类 21 项测试/产品编译、实际 Java 生成的 Web 脚本语法、完整补丁 27 文件往返、双 ABI/ELF/公开导出与 18 库边界均通过。TV64 `202609161336`（SHA256 `9192d5a6df9cb5615f7bacaed8bd55200fd7240bacfd04727dd4324eca2e876e`）的 10 个 MPV 资产、ZIP CRC/v2 签名及 27 个其他原生库与基线一致均通过；104 个既有 `app/.cxx/` 文件无变化。未安装/未取得电视结果。12:55 Asia/Shanghai 起原目标 13:35，因恢复基线修正、一次 mpv 辅助函数名修正与 Gradle 缓存权限等待顺延至 13:45 收尾；不重复已通过且未变更的检查。
- Risks：首次只裁决绑定类型，普通/YCbCr/稳定/动态图像分组及真实提交依结果再进入；C 仍等待电视证据，不能把诊断包称为卡顿修复。
- Rollback：上述提交/tag 成套恢复源/补丁/App/双 ABI；保留已交付的 9.23 TV64 包。
- Exactly one next action：电视安装 13:36 候选，在 Web「诊断 → FEL 卡顿定位」主动运行一次绑定对照并导出日志，用四组结果决定 9.22-B 后续分组；C 不先行。

<a id="p2-4-fel-bind-probe"></a>

## 9.24 用户主动的绑定类型对照（2026-09-16，本机候选已验证）

用户“继续实施优化/继续”已授权沿 9.22 逐级实施。9.23 已原子保存为 `8de0fd70942d513034fb8118de5229d7eb719622`；本次只进入 B 的第一层，不越过目标电视证据直接实施 C。最佳实践证据、完整来源版本、五类研究、三方案比较和性能门槛沿用 9.19/9.22，不重复泛搜或把既有源码阅读算新发现。

具体核对了锁定 mpv 的 `playloop.c::set_pause_state/get_internal_paused/run_playloop`、`vo.c::vo_set_paused/process_fel_prepare/fel_prepare_timed_out`、stable mapper 的 `map/create_input/prepare_conversion`，以及 App `DiagnosticControls`、`MpvDiagnosticCollector` 和 Web 诊断抽屉。当前常驻计时只能观测生产链的组合 bind，未提供最小布局对照；暂停 VO 使用短互斥锁/唤醒，不同步等待 mapper，适合由 core 与 VO 做显式握手。沿用成熟 mpv 内部暂停原因与原子请求，不新建渲染线程、设备或常驻探针。

采用的窄实现：

1. 仅当前正在播放的 Vulkan FEL 实例接受请求；Web 复用同源 POST/限频和日志入口，与深度像素/PCM 统计互斥。请求不因换片/重建自动迁移到新实例。
2. 下一个合法 AImage 在 mapper 内持有时声明诊断阶段，core 暂停内部播放并确认；保留用户的 pause 意图，结束/取消按最新用户意图恢复。源 acquire fence 必须已 signal；持有期间不归还 AImage、不允许 producer 改写，不额外获取 decoder buffer。
3. 先 flush 既有 libplacebo 工作，以空队列 fence 排空本应用的 GPU 队列；有界轮询，可取消。该排空提交单独记录，不属于诊断 draw/dispatch。故障/超时未 signal 的 fence 延后回收，不能提前销毁 GPU 在用对象。
4. 独立诊断 command pool、完整写入的 descriptor set 与最小 pipeline layout；空控制/输入 sampler/输出 storage/组合逐组 fresh reset/begin/bind/end。没有 shader 执行或图像读写，记录布局中的 imageLayout 不冒充真实 GPU transition。每组最多 8 次预热 + 32 次采样 / 5 秒，总请求预算 30 秒，分别报告 bind 与 record 的墙钟/线程 CPU 和样本数，冷创建单列。
5. 一份原尺寸、原格式目标图像，按实际 VkMemoryRequirements 计入 96 MiB 上限；不降位深/分辨率补数。诊断 descriptor/command 完成销毁后才继续当前帧生产转换。失败仅终止诊断，不替换 FEL shader、配对、缓存或正常同步。
6. 真实函数 host 合同覆盖默认关闭、独占、取消/截止、暂停恢复、fresh 命令、合法布局、无 draw/dispatch/提交、资源释放和预算。随后同锁双 ARM 构建、ELF/公开导出/18 库边界及 TV64 包身份/签名。电视 B 结果到来前只交付诊断候选，物理呈现/实际流畅度仍未验收。

普通图像/YCbCr/动态源分组和真实提交按 9.22 的结果分流执行；这是逐级测量合同，不遗漏后续裁决，也不声称四组 bind 的差值就是播放可获得的收益。厂商 API 自身挂住无法由应用强制打断，保留这一实测限制，不强行销毁仍在使用的资源。

### 本机验证与实现边界

- `probe-contract-final.log`：直接编译 `hwdec_aimagereader_vk_probe.inc` 与实际 core 暂停/property 函数，ASan/UBSan 通过。四组各 40 次 fresh reset/begin/end，共 120 次真正 bind，3 次完整 descriptor 写入；唯一 2 次 queue submit 是空的排空 fence。默认不开启时无计时/分配；覆盖主动取消、用户暂停意图、原尺寸 10bit、真实 allocation 超额、分配/record 失败、未 signal fence 延后销毁、device lost、acquire 不就绪、握手失败及截止；所有诊断 GPU/descriptor 引用按顺序释放。集成编译修正为锁定 mpv 的 `m_property_strdup_ro` 后仅续跑相关合同与失败构建。
- `core-contract.log`：实际 core/VO 既有生命周期及新增诊断阶段通过；30 秒不能靠重复轮询续期，诊断结束恢复正常 750ms 交接预算。
- `gradle-focused-final.log`：仅 `FelBindProbeControlTest`（8 项）与 `MpvDiagnosticsPolicyTest`（13 项）及必要产品编译，52 秒通过。初次 Gradle 因沙箱不能访问已安装缓存锁而未执行测试，使用既有授权重试，不把权限失败算代码失败。`web-verification.log` 核对实际 Java 生成的脚本语法和新增控件唯一 ID，不宣称完成电视 UI 操作。
- `patch-roundtrip-build-final.json`、`native-static-build-final.log`：27 文件最终基线正向应用、当前树反向检查、逐字节往返及全部既有 FEL 合同通过。第一次恢复目录已带旧补丁，静态合同拦截了不完整差异；修正为先逆应用已验证旧补丁再生成完整 patch，保留未变文件的原 patch 段，没有用错误版本构建/交付。`player/playloop.c` 未在旧 FEL patch 中，基线只撤销本单元的三个明确修改，保留其他播放器补丁。
- 第一次对照是 `pipeline=none` 的合法最小 layout / record-only 测量，没有 shader 执行；它不能单凭“全部很快”就排除实际 pipeline 或渲染负载的相互作用。输出位深/原始尺寸不变；96MiB 限制是显式图像分配，并非厂商驱动内部/RSS 总量保证。普通播放 map 统计排除该次主动暂停。
- Web 状态仅读取 App 缓存；请求 epoch 阻止跨媒体投递，native serial 阻止上次结束通知覆盖新请求。已发出的取消保持互斥到 native 完成清理；原生状态区分暂停确认、执行、结束和失败，结果复用既有结构化 native 日志，未改共享事件 schema。
- `hwdec_aimagereader_vk.c::aimagereader_vk_map/buffer_removed` 共用既有 mapper mutex，源移除回调不能在对照期间销毁正在引用的 input view；core 暂停/取消只使用独立的 VO 状态锁及原子请求。`MpvPropertySnapshot.update` 接受按媒体代际校验的新增属性，状态回调没有被属性白名单丢弃。

### 构建、产物与回滚

锁定的 mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、builder `99a60ad2141d5ace94453590903c2c6b9a0a2443` 和 NDK29/API24 不变。两 ABI 增量构建通过，日志 `arm64-build.log/armv7l-build.log`；`stage.log`、`native-assets.log`、`native-boundary.log` 核对 ELF/namespace/公开 libmpv 导出和其余 18 库逐字节不变。保留既有 stable shadow 与旧 32 位 time_t 警告，未扩大修复范围。

| 产物 | 大小（字节） | SHA256 |
| --- | ---: | --- |
| FEL 权威 patch | 见仓库文件 | `2a6c2288987886bc247eab02ad40c6e516ad06f215ef0322620fa1e45a155032` |
| arm64 `libmpv.so` | 17820296 | `1d55dbe26cd7192cddf4f7388becdf118c459f7bb4ce15f54e908891af91b454` |
| armv7 `libmpv.so` | 14635188 | `7f7e39193526b81f3e90d6a30ad8bfa828d6a5c034d42499e6dc48ac7a337dc4` |
| TV64 debug 5.6.0 / `202609161336` | 164160281 | `9192d5a6df9cb5615f7bacaed8bd55200fd7240bacfd04727dd4324eca2e876e` |

固定交付副本：`/private/tmp/webhtv-fel-bind-probe-pa3hqm7z/fel-bind-probe-tv64.apk`。包由 `8de0fd70942d513034fb8118de5229d7eb719622` + 本单元未提交工作树构建，不冒称内嵌将来提交号。复用 9.23 已验证且源码未改的 App C++ 产物，打包排除该不变的 externalNativeBuild；最终 APK 的全部 27 个 `lib/` 库逐项与 9.23 包一致，MPV 的 10 个 assets 库与本次资产相符。打包 114 秒，无增量 ZIP 空洞遗留（总 ZIP 开销 801740 字节）；完整 CRC、v2 签名和 104 个保护文件通过，见 `apk-verification.log`、`artifact-summary.json`、`protected-cxx-final.json`。

本单元以 guard `P2-4-fel-bind-probe` 原子提交并生成本地 annotated recovery tag，不推送。回滚为上述 9.23 提交/tag，成套恢复 patch/App/两份 libmpv；TV64 9.23 固定副本仍保留。这是用户主动的诊断候选，不是持续卡顿/实际显示帧率的验收通过。下一步只从电视四组对照日志裁决后续 B，暂不实施 C。

## 历史 Recovery anchor（9.23，起播一次选择候选已完成本机验证）

- Objective / acceptance：实际选中 DV7 轨道后、VO/decoder 创建前一次选择完整 FEL；不再从任意可用轨道推断后销毁 App 播放器重载；非 FEL 路线、10bit、EL 配对/同步不变。视频首帧须有本轮视频输出提交证据，不能仅凭 READY/轨道尺寸。
- Current unit：`feature/mpv-dv7-fel` / `5df95f475009ed0d04d864b60d7d22b229e87e95`，guard `P2-4-fel-startup-selection` / upstream；用户多次“实施优化/继续”已授权，无待审批事项。保护原有 104 个 `app/.cxx/` 文件。
- Scope：FEL 权威补丁及生成的 mpv 树、相关 Java 播放适配/策略、定向合同验证、两份 libmpv 与 lock、本文及评估索引；不升级依赖、不修改其余 18 库/JNI/Exo。
- Plan：沿用 9.21/9.22 源码与研究结论。`android-dovi-fel` 表达请求，`vo_extra` 保存选中轨道的实际状态；各 core/VO/decoder 消费实际状态。每个 FEL VO 单独指定 gpu-next/context，普通视频配置不被改写；App 观察 native 实际属性，不再启动识别后的第二次 loadfile。
- Verification / status：实现、6 类 34 项 Java 测试及真实 C 建链/VO/decoder 的 ASan/UBSan 合同通过；源码补丁 24 文件往返一致。最终双 ABI 编译、ELF/公开导出及 18 个其他库逐字节不变通过；TV64 `202609161235` 的 10 个 MPV 库身份、完整 ZIP/v2 签名均通过，未安装/未获电视性能结果。证据 `/private/tmp/webhtv-fel-startup-uwqa5kyt/`。原 11:45/12:10 估计超出：字幕/Surface/自动 Vulkan 回退联动、一次 Java import 修正、缓存权限与产物检查，以及增量 ZIP 空洞导致的一次必要重打包；没有重跑已通过测试。
- Risks：持续 descriptor bind 等待不由本单元宣称解决；9.22-B 仍为后续独立诊断单元，C 仍需其证据。电视无法 ADB，本机验证不能替代 9.22.5 的设备门槛。
- Rollback：本单元基线及 `recovery/P2-4-fel-cross-project/20260916082750-5df95f475009`，源/补丁/Java/两份 libmpv 成套恢复。
- Exactly one next action：按 9.22-B 实施用户主动启动的有界 sampler/storage 绑定对照，用目标电视证据决定后续暂存后端；9.23 包可独立供用户验证起播变化。

<a id="p2-4-fel-startup-selection"></a>

## 9.23 起播一次选择 FEL（2026-09-16，本机候选已验证）

本单元是用户已批准 9.22 路线的第一项，研究证据和三方案比较直接沿用 9.21/9.22，不重复联网搜索。进一步本地核对锁定 mpv：`android-dovi-fel` 的 UPDATE_VO 选项是全局配置，不能在建链时直接篡改 opts/shadow；`reinit_video_chain_src(track)` 与现有 `vo_extra.prefer_hdr_output` 提供按实际 track 创建/替换 VO 的生命周期。采用同类的实例状态，decoder 通过输出链 `mp_stream_info` 继承；运行中质量保护也按实例激活，不把全局“请求”当作所有媒体的实际渲染状态。首帧证据沿用 `vo_has_frame` 的“本次 seek/reconfig 后已排入 VO”定义，明确不等同于物理屏幕呈现时间。

### 实现与边界

- native `player/video.c::reinit_video_chain_src` 在实际 track 上选择 FEL，显式输出 `null`/封面/缺失 track（包括 lavfi-complex 输出）不抢猜轨道。FEL 创建 gpu-next 后才创建 BL decoder；VO 复用比较实例 FEL/渲染请求，切轨或换片可恢复原配置。`player/loadfile.c::update_vo_chain_el_state` 使用真实 VO chain 的 track。
- `vo_extra.android_dovi_fel` 经 output-chain stream info 传播到 decoder；core lookahead、VO、AImageReader 和 stable mapper 均消费实例状态。原全局 bool 只表示请求。两个 decoder 的 fast/skip 在 native 实际激活后约束，显示允许丢迟到帧，BL/EL 不跳过必要解码；普通内容保留原选项。
- `android-dovi-fel-vulkan=no/yes/auto` 只在 FEL VO preinit 内选择 context；App 复用现有 Vulkan 能力与自动/手动偏好。auto 初始化失败在同一次 load 内试 OpenGL，手动 yes 保持严格；普通视频全局 VO/gpu-api 不改。
- App 不再从第一条可用轨道推断 FEL，不再执行 `manual-dv7-fel-output/startup` 销毁重载。FILE_LOADED 时取得实际 native 选择，避免属性观察回调晚到触发错误的二次决策；Surface、音频直出策略和字幕能力随实际输出。仅 FEL 恢复 App 原直出 `sid=no` 默认，使用 file-local option；已保存选择、用户明确 mpv.conf sid 继续优先。
- 新只读 `video-frame-submitted` 要求本轮有效 video chain/VO、READY 视频状态和 `vo_has_frame`；START_FILE 清理 App 首帧状态，seek/reconfig 的 native 状态排除旧保留画面。MPV 提交证据送入 Media3 首帧事件，日志明确 `mpv-vo-submitted`，不把尺寸、音频 restart 或物理显示混为一谈。

### 已完成验证

- `native-contract.log` 的前半及 `native-contract-remaining.log` 的后半覆盖实际生产函数：选轨/VO 复用/失败/首帧 epoch、core/VO 回压/取消、decoder 发布与EL/BL配对、drop 路径、copy fence/lease、packet/RPU、descriptor 内容缓存及诊断有界性；ASan/UBSan 通过。旧 producer fixture 缺少 9.20 的 latency window stub，补齐后只续跑尚未通过的后半，没有重跑前半。专门的缓存测试继续验证真实 latency 算法，handoff fixture 只观察采样调用。
- 未提供实样给 host EL 解码，`SKIP EL/RPU sample decode` 保持为跳过；这不等于电视完整画面或性能通过。
- `gradle-focused-final.log`：TV64 相关 6 个 Java 测试类及产品代码编译通过（59 秒）；原有废弃 API/工具链警告不扩大处理。第一次构建缺少 TextUtils import 已修正，成功结果没有重跑。
- `patch-roundtrip-final.json`：24 文件的权威补丁到实际源码逐字节一致；同锁双 ABI 构建 `arm64-build.log` / `armv7l-build.log` 已通过。`native-assets.log` / `native-boundary.log` 确认仅两份 libmpv 变化，其余 18 库（包括 JNI）及 libmpv 公开导出保持不变。依赖 lock 未改变。

### 产物、验收与回滚

- TV64 APK：`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`；固定副本 `/private/tmp/webhtv-fel-startup-uwqa5kyt/fel-startup-tv64.apk`。版本 `5.6.0` / buildTime `202609161235`，164160281 字节，SHA256 `f5c51f91c9bd1889417b813c86cd790ce5c84a861fe3a045cdea29fd65fb76ba`。包中 `GIT_REVISION` 为本单元基线 `5df95f475009ed0d04d864b60d7d22b229e87e95`+dirty，表示提交前打包，不伪称来自未来提交。
- libmpv：arm64 17810152 字节，SHA256 `ff9992cf633e4582a8051af34c2d486dd7c9186feb834f3595c4021a24d0ee67`；armv7 14622500 字节，SHA256 `939edf95ac804f22af2fbe329f2f46997b0a2eac82dd166b2fdc0bd395f3a53b`。FEL 补丁 SHA256 `ed2468126e6506c723bde0323c4fdc5677498abaa20c5e0b83f80e51fe57ad95`。最终仅按仓库原补丁格式移除空上下文行的前缀空格，reverse-apply 检查通过，编译源字节未变，无须重建。
- 首次增量 APK 的 18618205 字节 ZIP 空洞未通过原 5 MiB 预算；只移走本单元生成的 APK，再由 AGP 正常生成/签名。最终无效空间 816267 字节、包内 10 库逐一匹配、ZIP CRC/v2 签名通过；75 秒重打包不重复 Java 单测。原 104 个 `app/.cxx/` 文件内容全部保持，无需恢复/移动任何文件。
- `startup-contract-final.log` 额外验证最后的三态 Vulkan 请求完整传到 VO；`auto` 能表示同次加载内的 OpenGL 回退，未被 bool 截成手动 `yes`。只有这项受相关代码调整影响的合同重跑。
- 这是起播修正候选：完整 FEL/10bit 的处理合同保留，未宣称本电视原有 33–37ms descriptor bind 等待消失。电视验收仍需同片独立起播、无 seek 持续区间、切轨/换片/退出和完整画面；无 ADB，不伪报安装或实测通过。源、补丁、Java 与两份 libmpv 由本 guard 原子提交并生成本地恢复 tag，不推送。
- 回滚保持本节顶部基线和 tag，恢复本单元任务文件为一组；不移动已有 tag，不动 Exo、JNI、其余依赖库或用户脏文件。B/C 后续状态见顶部唯一下一动作。

## 历史恢复记录（9.22，跨项目复核后的下一阶段方案）

- Objective：继续解决日志33的起播慢和持续掉帧；本单元完成跨项目源码、规范、讨论和本机APK静态复核，给出能区分原因的下一步，保留完整FEL、10bit及非FEL行为。
- Current unit：`feature/mpv-dv7-fel` / `98d247ea193c58a3dbfa4033d679023282632a8e`；guard `P2-4-fel-cross-project` / assessment，范围仅本文与评估索引，104个既有 `app/.cxx/` 文件保护。
- Evidence：`/private/tmp/webhtv-fel-cross-project-fhko9uzb/`；`evidence-manifest.json`记录来源URL、revision、访问日、等级、结论/限制及SHA256，含失败检索，不能把32个记录称为32篇有效资料。沿用9.21已解析日志，没有重读/重解析7MB导出。
- Status：确认导入缓存没有淘汰、输出已经是32位打包10bit；开源Mali代码支持进一步区分sampler绑定、storage绑定、格式转换和队列压力，尚不能命名电视闭源驱动内部根因。起播修正、B对照和C替代均未实施；没有新APK或电视验证。
- Rollback：本单元只改变方案；播放器仍对应上述HEAD及`recovery/P2-4-fel-log33-review/20260916075556-98d247ea193c`。本文的评审tag不表示播放性能合格。
- Exactly one next action：进入获批的起播修正单元，在实际选中轨道的native建链入口一次决定FEL输出；后续按本节B的绑定类型/外部图像对照决定C，不再盲增缓存。

<a id="p2-4-fel-cross-project-review"></a>

## 9.22 跨项目源码与二进制复核：缩小等待来源（2026-09-16）

用户明确要求难点扩展到其他播放器、图形项目、论文及成熟产品实现。本轮07:58 Asia/Shanghai开始，目标08:28–08:38完成；实际生产代码、补丁、依赖、库和APK均不改。9.19的规范、ANGLE、Filament、GStreamer、FFmpeg/libplacebo及论文记录继续有效，本节只补能改变取舍的新证据。当前并无新的待合并上游提交；下列revision均为只读研究，不升级任何依赖。

### 9.22.1 已排除的方向与本地调用核对

1. **不是输入导入缓存容量不足。** 从9.21的`native.json`取各trace最大的`fresh-commands`快照：输入hit/miss分别为489/9、461/9、442/9，三组`input-slots=9`、`input-limit=32`、`input-evict=0`。`input-removed=9`是移除/清理计数，不可改称LRU淘汰。结果保存于`cache-counter-evidence.json`。Flutter的扩大缓存修复不适合直接移植。
2. **不能再把descriptor写入减少当作等待消失。** 9.20候选已减少85.7%的写入，但9.21三组bind均值仍约33–37ms；继续增加set池、更新模板或线程缺少依据。
3. **计时没有把`/proc`读取包进普通bind计时。** 已逐行核对stable mapper的`fel_wait_probe_begin/end`、`fel_api_begin/end`与`record_conversion`：先完成before采样，再开始`FEL_API_DESCRIPTORS`计时，`vkCmdBindDescriptorSets`后立即截取结束时刻，随后才做after采样。稀疏wait sample可能包含统计/慢日志的额外成本，不能与普通bind窗口混为一个口径。线程调度和时钟读取仍是测量边界的一部分；约1ms线程CPU不等于已证明33ms厂商锁等待。
4. **现有输出已经保留10bit且只占32bit/像素。** 日志明确`source format 0 / external format 0xf0 / output format 64`；64即`VK_FORMAT_A2B10G10R10_UNORM_PACK32`。`choose_output_format()`已经优先选它，`create_output_image()`用途是`SAMPLED | STORAGE`。没有“把RGBA16改10bit就减半”的剩余收益。`RGB_IDENTITY + ITU_FULL`采样和`configure_dst_params()`的`Cr/Y/Cb`映射属于raw-YUV合同，替代shader必须保持。
5. **起播确实可以在native选中轨道后决定。** 锁定mpv的`loadfile.c`先选择轨道、执行`on_loaded`，之后才正常建视频/音频链；`video.c::reinit_video_chain_src(track)`在创建VO之前就收到实际track，已有按轨道选direct/HDR输出与替换VO的本地能力。App目前清空`dv7FelOutput`→先建直出→等待轨道信息→销毁整个播放器重载，额外初始化不是mpv要求。

本地精确入口：`hwdec_aimagereader_vk_stable.c::{fel_wait_probe_begin,fel_api_end,record_conversion,choose_output_format,create_input,configure_dst_params}`；`player/loadfile.c`的轨道选择和`on_loaded`；`player/video.c::{reinit_video_chain_src,video_output_image}`；`filters/f_decoder_wrapper.c::mp_decoder_wrapper_create`；`MpvPlayerEngine::{resetDv7HandlingForNewItem,updateDv7FelOutputForCurrentItem,buildPlayer}`。文件哈希保存在manifest。编译入口仍是权威`third_party/patches/mpv-android-fel.patch`经现有构建脚本应用；生成树不能替代补丁交付。

四仓锁继续为mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、builder `99a60ad2141d5ace94453590903c2c6b9a0a2443`。本轮没有改变这些输入。

### 9.22.2 新增外部证据及决定影响

访问日均为2026-09-16。A为实际代码/规范/本机二进制事实；B为维护者或厂商解释；C为外部报告/待验证类比；D为线索。等级表示证据类型，不表示可以从别的驱动外推到本电视。

| 来源、固定身份与实际阅读 | 支持的事实 | 本项目决定及限制 |
| --- | --- | --- |
| [Flutter PR190710](https://github.com/flutter/flutter/pull/190710)，head `4a9dbbccc1e51930967faad5a821a0942a9721d9`、base `b444e7897e249e9538e2afe623b533abbabfa4fb`；已读完整PR说明、实际diff、LRU测试、[TextureView复现及反馈](https://github.com/flutter/flutter/issues/180831)、[Vulkan ProcessFrame](https://github.com/flutter/flutter/blob/4a9dbbccc1e51930967faad5a821a0942a9721d9/engine/src/flutter/shell/platform/android/image_external_texture_vk_impeller.cc)。代码A、性能报告C；访问时**open，未合并** | 真实改动是6→64项有界导入缓存，测试64个key及第65个淘汰；作者用“无纹理/暂停纹理/持续解码纹理”分离新图像成本。报告的设备分别轮转19/31个AHB，旧缓存反复miss；`ProcessFrame`命中复用，miss才建Vulkan图像及layout转换 | 采纳对照方法；本地9/32且零淘汰，拒绝照抄64。PR中约55%改善不能用作本电视预期。原报告者确认TextureView改善不等于PlatformView也修好；PR测试合并SHA不作为已合并commit |
| Mesa25.1.0 `5c142e46f3f6e752ed745fd48912ebb8fad67145`：[PanVK image_can_use_mod](https://github.com/chaotic-cx/mesa-mirror/blob/5c142e46f3f6e752ed745fd48912ebb8fad67145/src/panfrost/vulkan/panvk_image.c)，A | AFBC资格明确排除`VK_IMAGE_USAGE_STORAGE_BIT`，另受GPU/格式/tiling/mutable及调试开关约束 | C若实施应使用真正的`COLOR_ATTACHMENT | SAMPLED`输出用途，不能只是给当前STORAGE图像加COLOR_ATTACHMENT。**这不是TCL闭源驱动的AFBC开关证明**，不能保证换usage就启用压缩或获得某个百分比 |
| 同Mesa revision：[panfrost_set_shader_images](https://github.com/chaotic-cx/mesa-mirror/blob/5c142e46f3f6e752ed745fd48912ebb8fad67145/src/gallium/drivers/panfrost/pan_context.c)、[pan_resource_modifier_convert](https://github.com/chaotic-cx/mesa-mirror/blob/5c142e46f3f6e752ed745fd48912ebb8fad67145/src/gallium/drivers/panfrost/pan_resource.c)、[panfrost_mtk_detile_compute](https://github.com/chaotic-cx/mesa-mirror/blob/5c142e46f3f6e752ed745fd48912ebb8fad67145/src/gallium/drivers/panfrost/pan_cmdstream.c)，A | 绑定shader image前可能进行AFBC/AFRC转换；固定modifier的资源可用shadow图像，转换会flush writer；MediaTek分块YUV有单独的detile compute和pre-barrier。驱动资源使用路径可包含应用没显式写出的转换 | B必须增加**输入sampler与输出storage分别绑定**的控制，不先认定是AHB输入或“描述符太多”。Panfrost是Gallium/OpenGL路径，不是`vkCmdBindDescriptorSets`的同驱动实现；没有把`externalFormat=0xf0`对应到任何DRM modifier，不能照搬detile算法、拆plane或硬编码格式 |
| Khronos [EXT_YUV_target v18](https://github.com/KhronosGroup/OpenGL-Registry/blob/2e30f7894201bec9bdd3aa5218c5025f9ac5948c/extensions/EXT/EXT_YUV_target.txt)，revision `2e30f7894201bec9bdd3aa5218c5025f9ac5948c`，A；已读raw sampler定义、精度及issues 1/6/9/10/12 | `__samplerExternal2DY2YEXT`确能保留raw YUV，分量为Y/U/V；默认sampler为lowp，规范未约束低分辨率chroma的具体重建。普通YUV blit/image-store不能随意代替该能力 | GLES raw-YUV是可研究的后备路线，不等于普通`samplerExternalOES`。若需要跨API，须证明扩展、highp、10bit、chroma/crop、Cr/Y/Cb重排和fence互操作；现阶段优先同Vulkan的窄候选，避免额外GL上下文/同步边界 |
| 锁定mpv的[hook文档](https://github.com/FongMi/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/DOCS/man/input.rst)，加上述本地`loadfile.c/video.c`完整相关函数，A | `on_preloaded`还没应用默认轨道选择；`on_loaded`已有选中轨道。正常建链入口先决定VO再初始化decoder | 起播应复用native轨道/VO生命周期，不能在`on_preloaded`或App“任意可用轨道”中抢猜FEL。复杂filter的提前建链、`track=NULL`、切轨和VO复用要按实际来源处理；不添加第二次probe/loadfile或Java往返阻塞hook |

补充讨论实际读了[MDK/fvp #134](https://github.com/wang-bin/fvp/issues/134)的维护者回复，以及[ncnn #5531](https://github.com/Tencent/ncnn/issues/5531)的AHB实验。前者分别涉及渲染器、硬解选择和音频时钟，不能把“掉帧”视为同一根因；后者是Adreno/640×480相机的pipeline创建与queue wait报告，WebHTV已有一次性pipeline，不采用其数字。ncnn回复中“未编译符号导致运行时花屏”的解释也不足以成立，未作为事实采信。另读[Flutter #176695](https://github.com/flutter/flutter/issues/176695)电视纹理停顿讨论：因缺目标设备信息关闭，**不是已修复**，模拟器通过不能代替电视结果。

论文与博文类别继续沿用9.19已取得的正文；本轮另查HPCA2013《Reducing GPU offload latency via fine-grained CPU-GPU synchronization》，[Crossref元数据](https://api.crossref.org/works/10.1109/hpca.2013.6522332)和Semantic Scholar仅确认作者/DOI，未得到全文，不能称已读或引用其结论。OpenAlex新查询限流，Arm候选文章404，Collabora响应没有可读正文，均在manifest标为失败，未拿失败页面充当证据。决定所需的API合同、资源用途和具体调用已有上表原始代码；缺少的是**本电视分组测量及同版闭源驱动内部证据**，不能靠增加泛文献弥补，也不能宣布根因研究完成。

### 9.22.3 本机Kodi APK静态核对与闭源分析边界

实际分析了`/Users/macbookpro/Downloads/Kodi19DV-Final-libbluray-aarch64.apk`，没有安装它。APK为157481012字节，SHA256=`a55173d5240f9b4da418f53b721717b3ecc16e5084c707241e31672d8b934127`；其中`libkodi.so`为88818504字节，SHA256=`ccad3ca24849ca7ea95fbfea92b692ae126def098b0cca36c1143e50b57fefe1`。内嵌版本字符串`19.0.0 / 20210220-73cc2b99ff`不能确定该修改包的完整源码revision，不伪造40位来源。

用NDK29 `llvm-readelf`读取动态符号/依赖，提取字符串后，对两个实际函数按ELF地址反汇编：`CDVDVideoCodecAndroidMediaCodec::Open`在`0x16ef638`、长度4812；`ConfigureMediaCodec`在`0x16f0a6c`、长度1500。首次按符号名反汇编未命中，后改用实际地址范围，未将空输出算作成功。保存了`kodi-elf.txt`、`kodi-codec-*-disassembly.txt`和字符串地址表。

- `Open`引用真实的`video/dolby-vision`字符串，枚举codec/profile，再调用`createByCodecName`；`ConfigureMediaCodec`调用`createVideoFormat`、取得Surface/SurfaceTexture、`configure`、`start`。这是已核实的MediaCodec路线。
- `Open`的`0x16f06e4/0x16f06ec`分别记录“Dolby Vision MTK decoder, using NAL header size 4 workaround”并调用`SetDoviWorkaround`。它是码流兼容处理的实证，不是NLQ/FEL软件重建的证据；本项目此前的纯BL/EL分离也不能原样替换成它。
- 动态依赖有GLES/EGL/Media NDK，没有直接依赖Vulkan；字符串搜索未提供NLQ/完整EL软件重建证据。但缺少字符串/直接依赖不能证明所有动态路径不存在，且厂商硬件内部是否处理FEL也未验证。不能称“该Kodi丢了EL”，也不能称“它与WebHTV完成相同工作且更快”。

Kodi本身是开源产品，这次是对用户现有发行二进制的实现核对，**不是已完成Mali闭源驱动逆向**。后续若B指向厂商内部，先取得与电视实际加载库匹配的路径、Build ID/SHA256及API入口归属；再静态定位该入口与锁/等待/转换调用，能取得同设备栈或trace才将其和耗时关联。没有同版二进制时，不用别的手机、模拟器或网上任意libMali代替。成熟闭源播放器也先核实是否确实使用EL残差，可用BL与FEL有可见差异的受控素材，之后才比较实现；“杜比图标亮起”不够。参考产品和驱动二进制不加入WebHTV资产或依赖。

### 9.22.4 调整后的最短实施顺序

| 路线 | 收益与风险 | 决定 |
| --- | --- | --- |
| 不改/继续扩大descriptor或AHB缓存 | 保留基线；已验证不是主要改善方向 | 仅作对照，停止同假设调参 |
| 原样复制Flutter缓存、Panfrost detile或Kodi MediaCodec路径 | 各自在不同输入/驱动/硬件职责下成立；不满足本项目完整FEL与外部格式合同 | 不移植整体实现，分别采纳对照、用途约束和建链原则 |
| 起播窄修正 | 消除已证实的重复播放器/VO/decoder初始化；需防非DV7、切轨及生命周期回归 | **先实施，独立原子单元**；确定收益是少一次错误尝试/重载，不虚报固定秒数 |
| B：绑定类型＋外部图像的有界对照 | 区分源sampler、目标storage、生产者更新及通用队列压力；诊断本身没有流畅度承诺 | 随后实施，复用现有限时诊断入口和Web日志，不做常驻探针 |
| C：同Vulkan fragment暂存 | 可改变storage写入与tile/压缩资格，可能改善copy/渲染依赖；仍采样同一AHB，未必改善其等待 | B支持后实施；保持同位深/尺寸/raw-YUV与源归还，不默认替换 |
| GLES raw-YUV或厂商原生DV/FEL | 可能避开特定Vulkan路径；扩展、chroma、硬件FEL职责及跨API同步均未证明 | 后备路线，需单独证据；不以关EL、普通RGB转换或降分辨率取得“改善” |

**起播单元：** 将“用户请求FEL”与“当前所选轨道实际启用FEL”分开；在`reinit_video_chain_src`实际来源已知、VO/decoder尚未创建时决定当前路径，按媒体generation同步实际结果给App，避免再次destroy/create/load。不简单把全局`android-dovi-fel=yes`提前：当前`video_output_image`等core路径也消费此bool，会扩大普通视频的暂存范围。恢复用户原始输出选择、filter复杂链、切轨、纯音频、取消/换源/VO复用都是该单元的合同。首帧不能由轨道尺寸或音频`playback-restart`推定；使用可证明的视频输出事件并标清“已提交/已显示”的区别，没有物理呈现反馈就不声称测到了它。内部重新配置与真实缓存重缓冲分别归因，不把所有BUFFERING都隐藏。

**B按结果逐级执行，不铺完整测试矩阵：**

1. 仅用户主动启动的独占诊断窗口运行，记录同一设备/库、实际GPU/queue、图像格式/用途、分辨率和诊断状态；保存/恢复播放状态。先排空既有受控工作，获取并合法持有真实AHB，保留acquire/release fence。不能在节目后台偷偷抢decoder buffer、常驻加线程或输出节目像素。
2. 先加空计时控制，然后对合法且已完整写入的`sampler-only`、`storage-only`、组合descriptor布局分别做fresh bind/record，首先不提交GPU执行。它们是不同的最小布局，只用来定位绑定类型，不把差值称为相同shader的性能收益。若只有组合慢，保留布局/管线交互方向；若storage单独慢，优先检查输出用途/资源压力。
3. 若sampler方向突出，再比较普通Vulkan图像、能力允许时的普通10bit多平面YCbCr图像、稳定且不再被生产者改写的真实AHB、正常动态AHB。普通多平面组用来进一步区分YCbCr与外部导入；不支持则记未测，不能把`0xf0`强作P010或降成8bit补数。稳定AHB必须仍有有效所有权，不能归还后继续读一块正在改写的内存。
4. 对出现差异的组才增加真实提交，再分空闲GPU与既有渲染负载，分别报告bind/record线程CPU和墙钟、submit、GPU query、完成等待。只在带渲染负载时慢，应先查共同device/queue压力；普通组也慢则查通用驱动/调度/实际layer；仅动态AHB慢才重点查生产者同步。任何一步都不能把计时挪到另一个阶段当作优化。
5. 每项最多8次预热＋32次采样/5秒，全任务目标上限30秒；冷初始化单列，提前耗尽预算则报告样本不足。资源逐组复用/释放，最多一份额外source和一份目标图像，按实际allocation累计上限96MiB，超限即不执行，不能缩图制造可比结果。取消检查放在操作之间和有界等待中；厂商API自身不返回时无法保证强制中断，沿现有故障恢复，不强行销毁仍在用的资源。逐组汇总到Web日志，无逐帧刷屏。

**C的具体约束补充：** 延用9.19的GStreamer fullscreen pass与同一raw-YUV采样；输出只声明实际需要的`COLOR_ATTACHMENT | SAMPLED`，查询当前10bit格式的color-attachment能力，完整写入时load可DONT_CARE、store必须保留。fresh命令、源foreign ownership、GPU完成和独立source-release fence、输出frame lease、crop/chroma/Cr-Y-Cb映射均保持。先对受控图案验证逐帧像素及NLQ输入，再测电视。现有AHB采样如果本身就慢，fragment仍可能同样慢；不承诺AFBC、固定加速倍数或实时达标。

### 9.22.5 验收、交付与恢复

后续起播代码单元沿用9.21的真实调用顺序负例、相关Java策略和取消/切轨边界，原始DV5/DV8/SDR/HDR、默认解码、音频/字幕合同不变。B验证所有权、超时/取消、预算、失败清理和默认关闭，不把微基准当播放通过。C只有在像素合同通过后才比较性能。所有实际native变更仍同锁增量构建双ARM ABI、核对ELF/公开导出/其余18库不变，并交付内容/签名可核对的TV64包；无须为了本次文档研究重建这些产物。

电视验收保持9.19.8：同片至少三组独立起播与不seek持续区间，接近23.976fps、显示丢帧低于0.5%、A/V差不超过200ms且不持续累积；真实首开、seek/退出与完整FEL画面一起通过。起播减少一次重建不能替代这一门槛。当前观察到的约33–37ms bind和约21ms copy是不同时间域，不能相加减计算预计fps。

后续起播单元的agent时间仍以代码/定向验证20–30分钟、热缓存双ABI/TV64构建10–15分钟、记录/提交约5分钟估计；电视等待另计。B涉及独立诊断资源和取消，不借该估计承诺完成，进入该单元时按实际入口重新给总时长。C及闭源内部归因继续以B结果作为准入。

本次最小验证为`evidence-manifest.json`来源/哈希、缓存计数、关键本地调用点与索引链接的合并核对，以及checkpoint脚本；执行记录保存到`review-verification.txt`并写入guard收尾证据。仅本文和索引原子提交、本地annotated recovery tag，不推送。下一代码单元成套保存App/权威补丁/测试/两份libmpv及文档，失败回到本节基线，不移动旧tag，也不把已知卡顿基线称为性能合格版本。

## 历史恢复记录（9.21，日志33否决整体性能验收）

- Objective：核对用户07:21 TV64候选的三次复现，区分起播重建、统计的重缓冲、持续Vulkan等待；完整FEL、10bit及既有非FEL行为仍为合同。
- Current unit：`feature/mpv-dv7-fel` / `44dd3f1386ac47c5d9e2007b9d2b32dfa0f72d8e`；guard `P2-4-fel-log33-review` / assessment，仅本文和评估索引，保护104个既有 `app/.cxx/` 文件。
- Evidence：`/private/tmp/webhtv-fel-log33-0odn5fr1/`；源日志7337143字节，SHA256=`52fa2af93a6397dbbbfe185d3644a49203f0524779ad7f9ee314a513d8d83c23`。3829条唯一结构化事件、927条重复保留事件已分离；三组平均map为37.514/38.712/41.497ms，写入减少未关闭等待。一次2563ms重缓冲发生于App直出→FEL重建，全部已记录cache pause为false、buffering state为100。
- Status：A的本机实现已生效，整体FEL性能验收失败；三组均有seek，不能将跨seek均值/末128次分位冒充严格稳态A/B。新起播路径修正和9.19-B尚属下一阶段方案；当前无代码/native改动、无新APK。
- Rollback：当前代码仍为上述HEAD及`recovery/AV-DIAG-01-WEB-ACTIONS/20260916072655-44dd3f1386ac`；A可由父提交成套撤销，但不能把旧诊断基线称为性能合格版本。
- Exactly one next action：批准下一阶段后先修复按实际选中DV7轨道在创建VO/decoder之前确定FEL路径的时序，再按9.19-B执行有界外部图像对照；保留电视实际验收门槛，不直接跳到C。

## 9.21 日志33：起播重复初始化、重缓冲来源及A的实际结果（2026-09-16）

07:38 Asia/Shanghai开始只读定位；07:49确定文档范围与约5分钟收尾目标。用户本次提供的是已安装候选的失败反馈，不能继续把A称为卡顿修复。以下事实来自同一份导出和当前源码；不重复已完成的上游研究或native构建。

### 身份、去重与三次结果

设备仍为TCL Smart TV Pro / MT9655 / Android14 / arm64。App buildTime=`202609160721`；`env.native`记录实际可执行映射的libmpv SHA256=`240935cf90a8ff660bc11cf8f3d20ef2be228ee559317db952af1ee1d1a1a62d`，manifest匹配，即9.20候选。构建时源码标记为`ed3d710ef551210278920ba4cd25e8dda6e19ad6`+dirty，与提交前打包一致，不是装错库。

输入`/Users/macbookpro/Downloads/webhtv-debug-log (33).txt`，8615行、7337143字节。按`(processRunId,captureGeneration,seq)`去重得到3829条结构化事件，排除927条快照/保留段重复；传统日志另按`logSeq`去重。统计产物见证据目录`summary.json`、`events.json`、`raw-events.json`、`performance-summary.json`。文件大小本身不能证明日志导致卡顿，未采集关闭日志的同设备对照。

| 播放trace | warm map次数 / 均值 | descriptor写入 / fresh命令 | 最后128次bind p50 / p95 / max | GPU copy均值 |
| --- | --- | --- | --- | --- |
| `p-11s7zee-1` | 497 / 37.514ms | 65 / 498 | 26.945 / 72.738 / 151.135ms | 21.193ms |
| `p-11s92mr-2` | 469 / 38.712ms | 63 / 470 | 27.030 / 80.283 / 141.418ms | 21.508ms |
| `p-11sa4pb-3` | 450 / 41.497ms | 75 / 451 | 23.710 / 59.415 / 107.234ms | 21.202ms |

1419次fresh命令中实际写入203次，内容命中1216次，省去约85.7%的逐帧写入；每帧仍fresh bind/record，未启用replay。三组warm bind平均32.906/34.798/36.554ms，线程CPU每次约0.97/0.98/0.99ms。上一日志32的map平均38.880ms、bind平均34.675ms；这份日志不支持有稳定、实质的整体改善。末窗口与之前稀疏等待样本的p50不是同一统计口径，不能据27ms相对35ms就宣称达到20%门槛。

38个等待样本的runqueue长尾仍存在：三组中位4.096/1.207/3.708ms、最大20.646/70.565/50.319ms；不能排除调度争用，也不能把剩余等待直接命名为某个厂商锁。copy约21ms及渲染pass最后已知均值约24–26ms仍是独立成本；这些GPU/CPU重叠阶段不相加成帧墙钟时间。

持续掉帧确有证据：三组`frame-drop-count`采样峰值103/124/144，`decoder-frame-drop-count`已知值均为0；A/V偏差峰值4.310/6.073/5.265秒。播放包含seek、计数会重置，因此不是三组累计掉帧或严格稳态分位。`estimated-vf-fps`约23.976反映帧时间戳，不能冒充实际显示帧率。A的主要性能假设在本设备未得到支持，整体验收不通过。

### 起播和那一次“本地重缓冲”

三组均出现`actual=surface/mediacodec_embed fel=false`→识别原始DV7→`rebuild reason=manual-dv7-fel-output`→`actual=vulkan/gpu-next fel=true`。第一组07:34:01.803–02.850依次报MediaCodec启动失败、`Could not open codec`及无可用HEVC解码器；07:34:07.418重新创建后硬解成功。FEL GPU冷初始化分别306/1113/84ms，不足以单独解释全部起播延迟。

- 第一组请求07:33:59.683，App记录第一帧13.312秒；FEL的native `playback-restart`在07:34:09.932，主线程处理到07:34:12.869，相隔约2.94秒。该信号本身不证明物理呈现，不能将全部延迟算作shader编译。
- 第二组07:34:53.479在直出尝试后收到`playback-restart`，输出尺寸属性仍0，尺寸来自轨道元数据；App提前记录3.035秒“首帧”。07:34:54.028才决定启用FEL，随后重建并于07:34:58.647重启播放，相对原请求约8.14秒。
- 恰好这次重建，在07:34:56.141将重缓冲计数从0加到1，07:34:58.701完成，累计2563ms。第二组采集的`paused-for-cache`全部false、`cache-buffering-state`全部100，后续缓存时长仍有1.544–21.590秒。证据定位的是内部重启等待被通用READY→BUFFERING逻辑计为重缓冲，没有证据说明本地文件读不动或发生网络等待。
- 第三组App记录首帧8.887秒，仍先直出再重建。三组后续seek都单列，不把seek等待重复算成此2563ms事件。

源码对应：`MpvPlayerEngine.resetDv7HandlingForNewItem()`将实际FEL输出清零，`buildPlayer()`先选直出；`updateDv7FelOutputForCurrentItem()`等待track-list原始profile。`PlayerManager.evaluateMpvAutoOutput()`/`prepareMpvFelOutput()`识别后调用`rebuildAndRestartMpv()`，销毁/创建播放器并重新loadfile。`MpvPlayer.handleEvent(MPV_EVENT_PLAYBACK_RESTART)`直接报告READY，`PlayerManager.PlayerListener.onPlaybackStateChanged()`随之完成起播统计。`PlaybackAnalyticsListener`在everReady之后收到BUFFERING便记录重缓冲；这些是不同事件语义，不是掉帧字段与重缓冲字段串用。

### 下一阶段窄方案、成熟依据与验收

沿用9.19已经完成的Khronos/Arm/ANGLE/Filament/libplacebo/FFmpeg/GStreamer及论文、issue证据；本次不凭新猜测直接重写graphics暂存。对新增起播时序问题实际补读了锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`+本地补丁树的`DOCS/man/input.rst::on_preloaded/on_loaded`、`player/loadfile.c`和`player/video.c::reinit_video_chain_src`，访问2026-09-16，A类证据。[锁定源码中的mpv生命周期文档](https://github.com/FongMi/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/DOCS/man/input.rst)明确：on_preloaded已有可用轨道、尚未选择轨道/建立decoder；实际链路在选择轨道和on_loaded之后才建立VO/decoder。可沿现有媒体生命周期一次决定输出，无需先播放错误路径来探测。资料不保证把任意运行时选项移到hook均安全，下一代码单元仍须核对实际选中轨道、VO复用及取消。

| 路线 | 决定及约束 |
| --- | --- |
| 保持A并继续增加缓存/线程 | 不作为主要修复：平均等待未改善，新的线程/缓存没有证据支持 |
| 原样套mpv钩子或在App全局强制FEL/GPU | 不采用：App往返hook会增加退出/取消状态，盲目全局启用会影响普通视频；当前native部分core/VO门控只看FEL标志，不能提前无条件置true |
| 起播窄适配 | 推荐先实施：显式FEL意图与实际激活分离，在已选择真实DV7轨道、创建VO/decoder前一次配置FEL链路；保留非DV7原路线。App同步实际结果，避免再次销毁重载；通用缓存重缓冲继续使用真实缓存事件，内部重新配置单独归因 |
| 9.19-B外部图像对照 | A没有解决等待，进入已拟定的普通Vulkan图像/稳定AHB/动态MediaCodec AHB三分法，fresh record及submit分开测；仅用户主动启动、有界、可取消、结果走现有Web日志。它是定位工具，不承诺播放性能收益 |
| 9.19-C graphics暂存 | 继续等待B裁决；不将“copy+render很贵”直接当作格式、精度、队列/同步等价的证明 |

建议按两个独立恢复单元实施。起播单元预计当前agent代码/定向验证20–30分钟，同锁双ABI/TV64打包10–15分钟、文档/tag约5分钟；B涉及独立资源与取消生命周期，实施前先固化具体入口/资源预算，电视运行必须由用户主动启动。上述是后续实施估计，非本轮已执行工作或性能承诺。

起播验收：同一样片只创建所需VO/decoder并load一次，不先出现MediaCodec直出失败，不由元数据尺寸/音频restart提前判视频首帧；内部配置不增加缓存型重缓冲。DV5/DV8/普通视频/纯音频、手选轨道、seek/切片/重复播放/退出、FEL保真和非FEL准入保持。最小验证为真实路径顺序/旧代码负例及必要Java策略用例，再同锁双ABI、ELF/18库边界和TV64内容/签名；真实起播和画面由电视验证。持续性能门槛继续使用9.19.8，不随起播改善而降低。

四仓固定版本见9.19.3；无新增上游合并候选。下一代码范围需包含实际改动的App输出/统计适配、FEL patch及对应native函数、定向测试、两份libmpv与本任务/索引；保持其他18库/锁/JNI契约。回滚到本轮基线`44dd3f1386ac47c5d9e2007b9d2b32dfa0f72d8e`，保留已经验收的日志页改动。当前交付为日志验收结论和后续方案，起播新时序、B、C均未实施；按仓库新阶段批准规则进入下一代码单元。

## 历史恢复记录（9.20，描述符内容复用实施）

- 目标：实施用户2026-09-16“优化”批准的9.19-A；仅相同描述符内容省去重复写入，每帧重新绑定/录制/提交，保留完整FEL、10bit、同步与源归还。交付可核对身份的TV64候选；像素与电视性能仍须实播裁决，不将调用次数下降称为卡顿修复。
- 基线 `feature/mpv-dv7-fel` / `5f6fd1a75c210eac2571e7939a4e5451f23e84e3`，恢复tag `recovery/P2-4-fel-descriptor-review/20260916062200-5f6fd1a75c21`。guard `P2-4-fel-descriptor-content` / upstream，保护104个既有 `app/.cxx/` 文件；不推送。
- 范围：FEL patch及其生成源码stable mapper、既有Vulkan cache测试与必要校验脚本、两ABI libmpv、本文件和评估索引；四仓锁及其他18份库保持基线。研究/版本/日志32证据全部沿用9.19，不重复泛搜，不实施B/C。
- 06:33 Asia/Shanghai开始，目标07:05–07:15完成本机候选：修改及定向验证15–20分钟、同锁双ABI和TV64打包10–15分钟、文档收尾约5分钟。电视无ADB，实播时间另计。
- 已修改stable mapper及FEL patch、既有cache测试/提取脚本、静态契约和native marker校验。真实函数ASan/UBSan通过：1200帧写入1200→72，1128次内容命中，仍1200次bind/record/dispatch；同地址/句柄回收、pending、crop/尺寸/query、reset/begin/end失败、push和有界统计通过。新patch仅重生成stable段，固定pre-FEL基线正向检查及当前源反向/静态契约通过。
- 证据目录 `/private/tmp/webhtv-fel-descriptor-content.xqkvwpr9/`。真实函数/静态契约、双ABI实际编译、ELF/公开导出、18依赖字节不变、TV64内10库/签名/ZIP均通过，候选 buildTime=`202609160647`，APK SHA256=`0edb43e7a30b5dc92966dd55c6811d2fe720d24cbda7b0b28ce23f3c2e726b74`。本机候选按guard原子提交/tag，精确ID由guard记录；这不是电视像素/性能合格tag。唯一下一步：电视安装含本候选libmpv的包，按9.19.8核对画面与三组性能日志；用户插入日志页需求时保留该待验收状态。

## 9.20 描述符内容复用候选（2026-09-16）

用户已批准实施9.19-A。设计、成熟实现来源、备选、验收及回滚沿用9.19；本轮不改变shader、图像池容量、EL线程、同步或公开设置。只读参考并非依赖升级，mpv/FFmpeg/libplacebo/builder四个固定版本和既有补丁顺序不变。

对象生命期是缓存正确性的约束：输入移除/销毁必须失效关联记录，输出/immutable sampler/YCbCr/layout重建必须先销毁记录池；普通set仅在完整hit且copy已完成时跳过相同内容写入，任何miss/冷路径/失败回退继续写入。每次仍reset/begin(ONE_TIME)、绑定、当前UV、dispatch、ownership barrier/query及当前semaphore提交。复用的是资源描述，像素由生产者逐帧改写；不能由host桩证明真实AHB像素正确。

代码与产物验证通过后提供本机候选；9.19.8像素与至少三组电视性能对照仍是实际采用门槛。write下降而bind/map不改善，或出现旧帧/回跳时，否决本假设并按9.19进入B，不继续扩pool或加线程。基线TV64 SHA256=`6e9bba7846e2e99620757c7ced5c357b287ce1bc64562a79f2db511e83c86a51`保留作对照。

### 实现及定向验证

- `recording_matches`在既有input/output、crop、尺寸和query条件上增加两端精确view快照。资源代际由销毁前失效约束：`destroy_input`先清除全部关联有效记录再释放view；`destroy_conversion_resources`先完成copy/销毁recording pool，再销毁输出、immutable sampler、YCbCr和layout。没有只凭AHB地址或可复用Vulkan handle认定同一代资源，也没有扩大缓存。
- `prepare_conversion`完整命中时省略普通set更新，仍先使record无效，再fresh record，成功才重新有效并选择command。miss/cold/fallback照旧写入；push仍在每个新command内提交两个binding。失败后的下一次重新写入；不重放旧command。
- 既有`WebHTV FEL reuse:`单行增加`descriptor-content-cache=1`、`content-hit`、`descriptor-writes`、`descriptor-rebinds`和`latency-window`。继续走Java已存在的性能分类，无新增主线程处理或日志行/采样频率；最后至多128次warm bind/record/map的nearest-rank p50/p95/max复用既有计时，日志关闭不新增时钟调用，约5KiB固定元数据增量（view快照与三个窗口），无新像素池。三个阶段重叠，不能相加成整帧时间。
- `cache-test.log`编译实际函数并启用ASan/UBSan，8组结果通过；`static-contract.log`校验源/patch一致、FEL准入、失效/不可变对象寿命和既有同步。只有受影响的缓存单元与静态契约运行，未重复全量FEL/Java用例。这些结果不是电视像素或吞吐验收。

### 本机候选产物与验证边界

- `arm64-build.log`、`armv7l-build.log`均只增量编译stable mapper并重新链接libmpv；`stage.log`、`native-assets.log`通过同锁暂存与ELF/命名空间校验；`native-boundary.log`确认仅两份libmpv变化、其他18库（包括JNI）字节不变、两ABI公开mpv导出集合不变。沿用NDK r29/API24及第9.19.3四仓完整版本。既有shadow/locale警告未扩展处理。
- `apk-build.log`：JDK21、既有隔离CXX缓存、一次TV64构建26秒成功，103任务中9执行/94缓存；没有重建Exo或修改受保护 `app/.cxx/`。`apk-artifacts.log`确认10个包内MPV库逐一匹配，v2签名通过，ZIP结构/签名开销800374字节；未运行ADB/电视操作。
- 初始05:28包已保存在证据目录 `baseline-tv64.apk`；本次06:47包另存 `fel-content-tv64.apk`，避免随后日志页需求重打包覆盖对照。APK中 `GIT_REVISION=5f6fd1a75c210eac2571e7939a4e5451f23e84e3`、dirty为提交前构建信息，按下列哈希识别实际候选。

| 产物/输入 | 字节数 | SHA256 |
| --- | ---: | --- |
| arm64 `libmpv.so` | 17808120 | `240935cf90a8ff660bc11cf8f3d20ef2be228ee559317db952af1ee1d1a1a62d` |
| armv7 `libmpv.so` | 14621300 | `b0a0ed3ae3c4d6c071a31d94d7a6ccc09cd9ca8bb348ba074f1046804f172a74` |
| TV64 debug APK，buildTime `202609160647` | 164143897 | `0edb43e7a30b5dc92966dd55c6811d2fe720d24cbda7b0b28ce23f3c2e726b74` |
| FEL patch | — | `e5e094d234c6ba8ef2c99f09ac051176873c31a4fe7949e3631dc4b0844fa2e2` |
| native lock（未改） | — | `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159` |

其余输入哈希见证据目录`build-inputs.json`，20库身份见`native-artifacts.json`。普通视频、push设备与取消/源归还路径保持原设计；native像素、功耗/CPU、起播失败及实际电视掉帧尚未验收。`descriptor-writes`是转换时update调用数，非单个binding数或初始化总写入数；最近128次窗口跨seek时不能作为无seek稳态对照，须按9.19.8取持续区间。不因本机验证通过就宣称A已达到20%采用目标。

## 历史恢复记录（9.19，方案评审）

- 目标：根据新日志32完成Vulkan描述符绑定高耗时的跨项目最佳实践方案；保留完整BL硬解/EL软解/NLQ、10bit、逐帧录制、同步与退出。整体电视实时性能仍未验收，本轮只更新本任务文档和评估索引。
- 基线 `feature/mpv-dv7-fel` / `57211803d507ab1b6b6a0ee02c626a7f5909eebc`；已有诊断tag `recovery/P2-4-fel-wait-diagnostics/20260916053516-57211803d507`。本轮guard `P2-4-fel-descriptor-review` / assessment，保护104个既有 `app/.cxx/` 脏文件；不修改生产代码、二进制、依赖或用户播放设置，不推送。
- 05:28 TV64诊断候选已在电视生效。新日志32与在线快照的2429条去重事件完全一致；持续实例604次warm map均值38.880ms，bind34.675ms / 线程CPU0.980ms；16个有效等待样本runqueue中位2.144ms、acquire均none、主动切换均3次。push明确不支持；大量等待仍未归因到某个驱动锁、ioctl或GPU fence。
- 已读Vulkan规范、Khronos/Arm样例、ANGLE代码与测试、Filament、锁定libplacebo/FFmpeg、GStreamer、Mesa，以及论文、博文、论坛和PR。推荐9.19-A：相同描述符内容不重复更新，每帧仍fresh bind/record；B为失败后的最小归因，C为有条件的graphics暂存候选。A/B/C本轮均未实施。
- 原始日志、去重事件、统计、阅读源码与下载清单位于 `/private/tmp/webhtv-tv-fel-log32/`。首次播放另有BL连续8次无进展失败，不能由描述符方案自动关闭。唯一下一步：交付本节方案，下一实施单元限定为A的单变量候选及像素/电视性能裁决，不直接启动B/C架构变更。

## 9.19 新日志32：描述符绑定等待的跨项目最佳实践评审（2026-09-16）

### 9.19.1 推荐结论与边界

**先实施“相同描述符内容不重复更新，每帧仍重新绑定、重新录制”的窄候选，验证它能否消除驱动在bind阶段延迟处理的工作；不能提前承诺它解决34ms等待。若等待不降，停止沿缓存数量/线程数反复试错，进入外部图像与普通图像的最小对照。GPU暂存改为图形管线是后续候选，不与第一步捆绑。**

本轮响应用户新增的深度研究要求，覆盖论文、帖子、博文、文档、issues、跨项目代码，并检查当前调用链。它是方案评审，不是依赖升级或生产代码实施。05:55 Asia/Shanghai开始，预计20–30分钟（资料与源码15–20分钟、方案与核验5–10分钟），目标06:15–06:25。最便宜决定性核验为日志统计、源码/引用与文档一致性；本轮不构建、不安装、不运行设备测试。

### 9.19.2 日志已经证明什么

输入 `/Users/macbookpro/Downloads/webhtv-debug-log (32).txt`，5088054字节、6148行，SHA256=`a7f0f3d0257e77891ea22c6adf8e79e1501fba2455e6a83abe1fbbbd74312ae6`，与9.5的同名旧日志不同。只读在线地址 `http://192.168.1.5:9978/debug/logs` 的TXT快照SHA256=`5c47147da04c462a37a8d8137b94f28c9be364130f444df169c4aec08489606c`。两份导出并非逐字节相同，但按 `(processRunId, captureGeneration, seq)` 去重后2429条事件的集合和内容完全一致；先去重再按时间排序，不能重复统计pinned/history。

设备为TCL Smart TV Pro / MT9655 / Android14 / TV64 / 4逻辑核；buildTime=`202609160528`，实际加载libmpv SHA256=`3e3732114fdd2527bbe95cce9b635087f0a67034a50c23a59c28c6c47fc5fa05`。APK内旧基线/dirty来自提交前构建，不代表装错包。用户明确电视不能连接ADB，下一步不依赖ADB。

| 观测 | 数值与意义 |
| --- | --- |
| Vulkan能力 | API/device API均1.3.247；Mali-G57，vendor=`0x13b5`，device=`0x90910010`，driver=`0xb001000`；`push-supported=0 push-enabled=0 extension-query-result=0`。强行开push不是修复 |
| 图像格式 | `source format=0`即VK_FORMAT_UNDEFINED，`external format=0xf0`，output format=64；BL硬解、EL软解、raw YUV/NLQ已进入。源是厂商不透明外部格式，不能直接解释成P010/RGBA |
| 持续实例 | `player-20` / `p-11ock86-2`；604次warm map，均值38.880ms，其中bind34.675ms、bind线程CPU0.980ms、descriptor update0.049ms。这是跨seek的每调用累计均值，不是稳态帧率 |
| 有效等待样本 | 持续实例16个，另一起播实例1个。持续实例bind17.190–79.066ms、中位35.222ms；线程CPU中位0.993ms；runqueue中位2.144ms、最大38.477ms。调度影响部分长尾，不能一概排除CPU争用 |
| 样本边界 | 17个样本均`sched-error=0/0 rusage-error=0/0 acquire=0/0`、主动切换均3次。持续实例probe中位0.270ms、最大3.881ms。没有可观察的输入acquire fd，不等于没有隐式同步 |
| GPU/资源 | copy均值21.293ms（605个样本），最后已知render pass均值26.348ms；605次提交/完成/异步归还，最终pending=0，fence timeout/release failure均0。两项GPU统计不是同帧完整关键路径，不能直接相加声称47.6ms整帧时间 |
| 播放后果 | native显示跳过记录已到240；最后一段`frame-drop-count=98`、avsync约4.404s，期间seek，计数不得相加。`decoder-frame-drop-count`最后已知0，不能用它否定显示掉帧 |
| 独立失败 | `player-8`从约25秒恢复，BL解出6帧后连续8次无进展失败；首draw在失败附近。描述符方案不能自动关闭这个起播问题 |

`fel_api_begin/end`直接包围bind；慢调用日志阈值100ms，上述17个样本均低于阈值，不能用该分支解释它们的3次主动切换。但schedstat/rusage快照有开销与更新粒度，**wall−CPU−runqueue不是精确驱动等待；“3次主动切换”也不能直接命名为3个futex、3个plane或3次Binder事务。**当前证据支持大量非执行/非排队等待，未证明其内部原因。

### 9.19.3 本地调用链与实际缺口

权威生产输入是 `third_party/patches/mpv-android-fel.patch`。下列源码路径相对于应用补丁后的mpv，当前副本位于 `build/mpv-native/mpv-android/buildscripts/deps/mpv/`。

| 文件/符号 | 已有实现与决定 |
| --- | --- |
| `video/out/hwdec/hwdec_aimagereader_vk_stable.c::aimagereader_vk_stable_map` | poll完成任务→缓存AHB输入/独立输出→hold libplacebo输出→prepare/submit→release给libplacebo→独立sync fd归还源；不把GPU调用移到持有VO/core锁的区域 |
| `find_input/create_input/destroy_input/invalidate_input_recordings` | 已有AHB缓存，销毁/移除输入会失效关联记录，防止同地址/同槽冒充同一资源；不是缺少AHB缓存 |
| `recording_matches/select_recording/prepare_conversion` | 最多128条懒分配记录，匹配input/output、crop/尺寸/query，跳过pending；**hit后仍update两项descriptor，再fresh record**。当前复用的是对象槽，还没有复用descriptor内容 |
| `update_conversion_descriptor` | 两个紧凑binding：外部YCbCr combined sampler和storage image，无动态UBO或大批稀疏binding。update快不排除工作被延后到bind，但延后目前只是待证假设 |
| `record_conversion` | 每帧reset/begin(ONE_TIME)、当前ownership barrier、bind、push当前UV、dispatch、release barrier、end；画面回跳后已禁用replay，新方案必须保持 |
| `submit_conversion/finish_output/release_fel_source_async` | acquire/available/ready/source-release/fence职责分离；command/descriptor在copy完成后复用，output像素另外受render semaphore/frame lease保护。cache hit不能替代同步 |
| `hwdec_aimagereader_vk_stable.comp/create_output_image` | 16×8 compute采样raw YUV写独立output；UV除法已移到CPU，output usage=SAMPLED+STORAGE。可研究等价fragment暂存，但不能回到长期保留MediaCodec源 |
| `third_party/mpv-player-jni/tests/fel_vk_cache_test.c` | 1200帧fresh bind/record、pending/失效/失败等真实函数测试已有；Vulkan调用用host桩，并不验证厂商驱动像素或电视性能 |

构建图不变：mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`；FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`；libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`；mpv-android builder `99a60ad2141d5ace94453590903c2c6b9a0a2443`。均为已覆盖基线，没有新合并提交。

### 9.19.4 阅读证据：结论、适用性与限制

以下访问日期均为2026-09-16。A=规范/实际源码与测试，B=项目或厂商解释，C=跨项目实验/未独立验证报告，D=线索。代码按完整revision只读参考，不引入依赖；原始正文、源码、检索结果及SHA256清单保存在本轮证据目录。

| 来源/固定身份 | 实际阅读与支持结论 | WebHTV适配与限制 |
| --- | --- | --- |
| [Vulkan descriptorsets](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/descriptorsets.adoc)，`f84d432d5b8912362f96f581f29bbc4f3c8c7843`；A | `vkUpdateDescriptorSets/vkCmdBindDescriptorSets`：descriptor可在bind的host执行期到shader执行间被消费；相关update可使非update-after-bind的已录制命令失效；pending期间不能覆盖/释放 | bind不保证只是无成本拷贝句柄；不能将update移到bind后。相同资源描述可复用，新command仍需绑定，像素内容与descriptor内容分开管理 |
| [同版memory](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/memory.adoc)及[resources](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/resources.adoc)；A | Android外部格式、VUID02396–02398/09457；UNDEFINED源不能任意mutable重解释/添加TRANSFER用途，GL与VK采样结果未必完全相同 | 否决将0xf0强当P010、直接copy/blit或随意拆plane。GL普通samplerExternalOES不证明raw YUV精度等价 |
| [Khronos/Arm descriptor management](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/descriptor_management/README.adoc)及[descriptor_set.cpp](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/framework/core/descriptor_set.cpp)，`ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1`；A/B | 内容作key复用set，避免相同信息重复update、每帧pool reset/free；测性能排除validation干扰 | 支持A；其44→27ms来自大量draw的CPU负载，本机只有一次bind/两个binding且线程CPU约1ms，**不套用38%收益**；动态UBO建议不适用 |
| [ANGLE ProgramExecutableVk.cpp](https://github.com/google/angle/blob/314cb711e226dc1b911f3233992c1d51053cc821/src/libANGLE/renderer/vulkan/ProgramExecutableVk.cpp)、[vk_helpers.cpp](https://github.com/google/angle/blob/314cb711e226dc1b911f3233992c1d51053cc821/src/libANGLE/renderer/vulkan/vk_helpers.cpp)，`314cb711e226dc1b911f3233992c1d51053cc821`；A | `updateTexturesDescriptorSet/getOrAllocateDescriptorSet`仅miss写descriptor；image/sampler serial作key；`DynamicDescriptorPool`维护LRU、引用与延后回收 | 最直接的内容缓存参考；保留WebHTV有界槽和移除失效，不复制ANGLE的大型可增长pool/GL状态机 |
| [ANGLE VulkanPerformanceCounterTest.cpp](https://github.com/google/angle/blob/314cb711e226dc1b911f3233992c1d51053cc821/src/tests/gl_tests/VulkanPerformanceCounterTest.cpp)、[AHB实现](https://github.com/google/angle/blob/314cb711e226dc1b911f3233992c1d51053cc821/src/libANGLE/renderer/vulkan/android/HardwareBufferImageSiblingVkAndroid.cpp)，同revision；A | `TextureDescriptorsAreShared`检查跨program复用；`initializeImpl`先查普通/external格式能力 | 借鉴测试与能力分流，但普通纹理测试不等于MediaCodec改写AHB的像素测试；本机UNDEFINED源不满足普通格式优先的条件 |
| [Filament VulkanDescriptorSetCache.cpp](https://github.com/google/filament/blob/fa4e346ab2eb0db36f84e8ae2e8ca71040a94b71/filament/backend/src/vulkan/VulkanDescriptorSetCache.cpp)，`fa4e346ab2eb0db36f84e8ae2e8ca71040a94b71`；A | `commit`检查layout/上次绑定，`commands->acquire/referencedBy`保留资源；update与bind分离 | 借鉴引用/状态边界；不能跨新command照搬“set相同就不bind”。当前每帧一次bind，没有同command重复bind可删 |
| 锁定libplacebo `src/vulkan/gpu_pass.c::vk_pass_run/set_ds`、`context.c::device_init/finalize_context`；A | 普通路径每次update/bind，完成callback归还set，缺空闲set另有poll慢路径；自动启用可用push扩展 | 成熟实现并非都采用内容缓存；原样复制不会消除热点。其set池等待发生在bind外，不能解释本机单个原生bind计时 |
| 锁定FFmpeg `libavutil/vulkan.c::ff_vk_exec_start/ff_vk_exec_bind_shader/update_set_pool_write`；A | 每执行上下文有set，fence结束后回收依赖，ONE_TIME录制、显式绑定 | 支持对象复用与逐帧命令/同步并存；其无限fence等待不适合照搬到WebHTV可取消链路 |
| [GStreamer descriptor cache](https://github.com/GStreamer/gstreamer/blob/765564f3ecb3ffb066d14ac24c28e81623102788/subprojects/gst-plugins-bad/gst-libs/gst/vulkan/gstvkdescriptorcache.c)、[fullscreen quad](https://github.com/GStreamer/gstreamer/blob/765564f3ecb3ffb066d14ac24c28e81623102788/subprojects/gst-plugins-bad/gst-libs/gst/vulkan/gstvkfullscreenquad.c)，`765564f3ecb3ffb066d14ac24c28e81623102788`；A | `prepare_draw_internal/fill_command_buffer_internal/submit_final_unlocked`复用pipeline/view/framebuffer，fence延后释放，graphics处理视频图像 | C的视频实现参考。descriptor cache首先是对象池，不能误称ANGLE同类内容hash；不复制GLib运行时或颜色策略 |
| [Mesa PanVK descriptor state](https://github.com/chaotic-cx/mesa-mirror/blob/5c142e46f3f6e752ed745fd48912ebb8fad67145/src/panfrost/vulkan/panvk_vX_cmd_desc_state.c)，Mesa25.1.0 `5c142e46f3f6e752ed745fd48912ebb8fad67145`；A | `CmdBindDescriptorSets2KHR/cmd_desc_state_bind_sets`维护set/offset/dirty state，所读路径没有显式GPU fence等待 | 普通绑定可主要是CPU状态操作，值得做外部图像对照；PanVK不是电视闭源驱动，不能据此断言内部原因或直接移植 |
| [Granite作者博文](https://themaister.net/blog/2019/04/20/a-tour-of-granites-vulkan-backend-part-3/)，2019-04-20；B | `hash→VkDescriptorSet→vkCmdBindDescriptorSets`、布局专属pool、闲置回收；讨论persistent set组合数量代价 | 支持内容复用与bind分开；不复制固定“8帧后回收”的经验值，必须以实际fence/lease为准 |
| [NVIDIA Vulkan Dos and Don’ts](https://developer.nvidia.com/blog/vulkan-dos-donts/)、[Advanced API Performance: Descriptors](https://developer.nvidia.com/blog/advanced-api-performance-descriptors/)，访问日正文；B | 减少descriptor创建/复制，紧凑layout、push constants、复用command pool；并行录制需任务图 | 本地两个binding和push UV已覆盖部分建议。百万descriptor阈值、bindless和桌面多线程收益不外推到4核电视一次bind |
| [Khronos/Arm async compute](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/async_compute/README.adoc)、[layout transitions](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/layout_transitions/README.adoc)，同Samples revision；A/B | Mali上compute/vertex资源与fragment依赖可能形成空档；color attachment与正确layout有机会保留tile优化 | 支持研究C，反对盲加队列；静态场景transaction elimination、AFBC或压缩收益在本机未测，不套用sample百分比 |
| [Khronos论坛讨论](https://community.khronos.org/t/most-common-vulkan-mistakes-vs-the-world/6861)，2016-05-18，含Sascha Willems回复；C | 讨论预录制命令的适用范围，不能把静态样例建议推广到所有动态引擎 | 与现代代码共同支持保留逐帧录制；老论坛不是当前驱动性能证据 |
| [MoltenVK PR2822](https://github.com/KhronosGroup/MoltenVK/pull/2822)，head=`fd8ea9dbb1963af3642d912bad651f018434c922`，base=`4aaf714aa1b3e78e26ecfcefa9c75e9a576c500b`；C | 已读实际patch、复现代码与报告，区分resource-use cache/实际argument-buffer binding；提交者报告validation零错误仍可能像素错误 | **访问时open、未合并，非维护者已确认修复**；针对Metal/GTK，不移植到Mali。只借鉴缓存边界和像素复现方法 |
| [WebGPU Dispatch Overhead论文](https://arxiv.org/html/2604.02344v1)，arXiv2604.02344v1，§3.3/3.6/4.4/7.2/7.8；C | 区分单次同步与批量dispatch、框架/API/GPU时间及跨后端限制 | 仅借鉴测量设计，不拿24–36μs当Android bind目标；正文“0.56ms低于0.095ms”存在数值矛盾，未采信其外推结论 |
| [Mìmir CUDA/Vulkan互操作论文](https://arxiv.org/html/2504.20937v1)，arXiv2504.20937v1，§3.4/4.1/4.3；C | 共享外部内存、交替buffer、显式同步；取消同步可能使计算/渲染争用恶化 | 借鉴对象寿命/内容版本分离并保留同步；CUDA点云不是MediaCodec FEL，9×/12×收益不外推，不引入CUDA |

检索覆盖GitHub精确AHB+bind、Mali+gralloc+slow、跨项目bind stall、Stack Overflow与论文索引；未找到直接复现并修复本机 `Mali-G57 / externalFormat=0xf0 / bind阻塞` 的成熟补丁。Stack Overflow主要结果为粒子批处理，与一次视频dispatch不符；IEEE2022相关论文仅取得索引摘要，未取得全文，未作为设计依据。Google/DDG遇跳转/验证，已改读官方代码、论坛、OpenAlex/arXiv正文，失败页面不计为已读证据。剩余内部归因需要目标设备对照，继续泛搜不能代替它。

### 9.19.5 方案比较与选择

| 方案 | 能改变的工作 | 本项目限制/风险 | 决定 |
| --- | --- | --- | --- |
| 不改 | 保留可复现基线 | 继续约35ms bind和掉帧 | 留作对照，非完成方案 |
| 原样套用libplacebo/FFmpeg | 普通路径仍update/bind，push依赖能力 | 不自动改变外部图像驱动路径，可能丢本地取消/归还保护 | 不整体移植，借鉴生命周期原则 |
| A：内容缓存+fresh bind/record | 减少相同descriptor写入及其可能触发的延迟工作 | 直接可省只有0.05ms，大收益待证；须验证AHB循环改写像素 | **首选单变量候选** |
| 旧command/绑定命令跨帧重放 | 减少bind/record | 已有画面回跳，旧命令不能冒充当前内容/ownership | 不重启，也不改名为secondary缓存绕过 |
| update template、pool/reset调整 | 优化更新/管理成本 | 本地update/reset微秒级，已有有界复用 | 暂不做，不能解释34ms |
| 强开push、bindless、descriptor buffer、新bind入口 | 换绑定机制 | push明确不支持，其他feature/YCbCr能力未证实；新入口不保证不同驱动实现 | 不作此电视修复，不因Vulkan1.3假定可用 |
| 增加EL线程/录制线程/队列 | 可能重叠等待 | EL实际5线程/4逻辑核；新pool所有权、取消和CPU争用成本 | 无可重叠关键路径证据前不做 |
| B：外部图像最小对照 | 分清AHB/YCbCr路径与通用绑定/队列压力 | 需要独立有界复现，不能抢当前播放资源 | A无效后只做这一条归因 |
| C：graphics shader暂存 | 改变storage写入和compute/fragment依赖 | 仍要bind，等待可能不变；需格式、像素、queue/barrier适配 | 有条件候选，单独评审 |
| 强制P010/直接copy、普通GL RGB、CPU回读、关FEL | 绕过当前路径 | 格式/精度不成立或降低功能性能 | 不采用；GL raw-YUV路径需另外证明扩展与等价性 |

### 9.19.6 最小实施单元A

优化的是**资源引用描述的重复更新**，不缓存视频内容，不缓存逐帧acquire/release，不重放command。当前`recording_matches`比descriptor实际key更严格，第一步保留现有crop/尺寸/query条件，避免重写cache结构。

1. 仅显式FEL、普通set、有效完整cache hit且上一copy完成时，复用已写好的两个binding，仍每帧调用`record_conversion`。缓存身份包括image view的对象代际、sampler/YCbCr转换、pipeline layout、descriptor imageLayout；移除/重建必须失效，不能仅凭AHB地址/槽号。
2. miss/cold/fallback继续写当前descriptor；push路径每个新command继续push完整binding。保留pending排除、输出重建/源移除失效与失败回退；成功record后才标有效，不得命中后返回旧command。不增加像素池或cache上限。
3. 每帧继续reset/begin、bind pipeline/set、当前UV push、dispatch、barrier/query和当前semaphore提交。descriptor的copy生命周期与output的render semaphore/frame lease分开；更新前确认无pending，不能靠update-after-bind补救错误顺序。
4. 沿现有FEL日志补充有限的content-hit/write/rebind/fresh-command计数和bind/map分布，区分“少写了descriptor”与“bind真的变快”。日志关闭时不新增采样、线程、磁盘或主线程工作，不导出用户节目像素。

预期范围为FEL补丁stable mapper、相关现有host测试/校验、两ABI libmpv及本任务/产物说明；日志沿既有分类，不必新增Java通路。lock、FFmpeg/libplacebo/JNI、Exo、公共API和用户设置保持原合同；普通视频和push设备保持原路径。实际实施另按必要文件声明guard，本轮文档范围不自动授权架构变更。

**可证伪条件：**若write下降但bind/map分布未改善，则“重复update引发bind延迟处理”假设在本机不成立，不能把调用次数改善报成卡顿修复，不继续在同一假设下扩pool/加日志/调线程。若出现旧帧/回跳，即便规范允许内容复用也否决候选，恢复逐帧写入并进入B。

### 9.19.7 A无效后的分流

**B只裁决一个问题：等待是否依赖不透明外部图像。**独立有界复现比较原生Vulkan图像、稳定持有且不再被生产者改写的真实AHB、正常逐帧更新的MediaCodec AHB；三组均fresh command，另区分仅录制和真实提交。普通图像与AHB的格式/YCbCr必然不同，因此只能定位路径类别，不能直接命名内部锁。

无ADB时可由候选中的定向复现通过既有Web日志输出；必须由用户主动启动，不能自动暂停节目或抢surface buffer。统一分辨率/输出格式、warm-up/次数，分别统计录制、线程CPU、GPU和完成等待；批量微基准最终同步放在批末，组间设时限/取消出口。它不改变生产路径每帧必要的同步，不借测试取消源归还/依赖。设备不给驱动栈权限就保留未知，不能用读取失败证明无等待。

普通图像也慢则先检查实际启用的validation/layer、通用驱动锁和并发队列压力；只有动态AHB慢则检查生产者交接/外部图像实现；稳定AHB也慢则重点检查外部格式/YCbCr绑定路径。17个样本尚不能完成三分法，不应据此直接宣称GPU硬件不够。

**C优先评估等价graphics shader暂存。**借鉴GStreamer fullscreen pass，以相同raw-YUV sampler写独立10bit color attachment，复用pipeline/render pass/framebuffer；保持UV中心、crop、分量顺序、full-range、chroma filter和NLQ输入。查询目标格式COLOR_ATTACHMENT+SAMPLED能力，不满足保留compute。完整覆盖输出可用loadOp=DONT_CARE，但输出供后续渲染，storeOp必须保留内容，不能照抄丢弃输出的带宽建议。

C需适配graphics queue/stage、source foreign ownership、output layout和双向semaphore，保留独立source release及有界buffer。它可能改善storage写入/流水线空档，也可能完全不改变AHB bind等待；不预称AFBC或固定收益。先做单一等价pass，不同时改EL、分辨率、精度、同步或线程；不能回到直接长期持有MediaCodec表面，否则重新引入buffer耗尽。

### 9.19.8 验收、采用标准与回滚

- **代码门槛：**现有真实函数host用例1200帧仍有1200次fresh bind/record/dispatch；hit减少write，miss/重建必须write。覆盖同地址新资源、pending、crop/尺寸/query变化、分配失败、record失败；保留既有lease/归还/取消验证。
- **像素门槛：**用可辨认帧号/交替图案的受控素材，验证同一AHB循环写入、回收、seek/重播/暂停重绘都显示当前内容，再用原FEL片验证raw YUV/NLQ、位深和画面。host桩、PTS一致、fresh计数、validation零错误均不替代像素验证；不自动回读/导出用户节目。
- **A的性能裁决：**基线为本节05:28候选，保留候选commit/APK/libmpv哈希。同电视/素材/设置/日志类别/散热条件至少3组对照；初始化单列，持续区间不seek。看bind/record/map的中位、p95/最大值以及GPU copy、A/V和显示掉帧。只有write约0.05ms下降不算实质改善；以bind中位至少下降20%、map中位同步下降且p95无回归为A采用目标，未达标不继续把A作为主要修复路线。
- **整体FEL门槛：**A阶段改善不等于需求完成。原23.976fps片稳定段需接近片源帧率、显示丢帧比例低于0.5%、A/V差不超过200ms且不持续累积，并关闭独立起播失败。保留完整FEL、10bit、色彩、音频/字幕、可取消seek/退出，不降画质换达标。报告重复测量分布和未满足项，不能只挑最快一轮。
- **构建边界：**未来代码单元只按同锁增量构建两个受影响ARM ABI，检查ELF/公开导出、其他18库不变，打包所需TV64并核对包内库/签名；仅相关修改或不确定结果才重试。本轮没有构建或安装。
- **生命周期/成本：**A不增加像素池、EL线程、持续采样、运行时依赖；cold/seek/退出不能新增等待周期。不能把bind耗时挪到别的阶段当成变快。C另外记录framebuffer/pipeline内存、包体差额并验证新queue/layout契约。
- **回滚：**`57211803d507ab1b6b6a0ee02c626a7f5909eebc`及9.18既有tag是诊断基线，不是性能合格基线。未来A/C各成原子source/patch/两libmpv/产物说明提交和annotated tag；失败成套恢复对应父提交，保留证据，不移动旧tag、不推送。文档评审tag只代表方案快照。

本轮产出为可评审方案，A/B/C尚未实施，电视卡顿未标记为已修复。下一单元为A及像素/电视性能裁决，再依据结果进入B或验收，只有证据支持时才另行评审C。

## 历史恢复记录（9.18）

- 目标：继续定位电视 FEL 严重掉帧；本单元补齐无 ADB 的等待来源诊断，保留完整 BL 硬解/EL 软解/NLQ、10bit、逐帧录制、同步与退出。它是诊断候选，不是电视性能修复验收。
- 基线 `feature/mpv-dv7-fel` / `206a57e0e337304a8b78712c487ef245d7cb0fa0`，guard `P2-4-fel-wait-diagnostics` / upstream。启动时保护 104 个既有脏文件，均属 `app/.cxx/`；不修改或移动这些文件，不升级锁定依赖，不推送。
- 2026-09-15 新日志30与9.15同名旧日志不同：TCL MT9655、Android14、TV64、buildTime=202609152206；FEL已生效，但两轮 warm map 39.28/35.50ms、descriptor bind 34.73/31.65ms（线程CPU约1ms），第二轮360次显示跳过；seek前后计数不能相加。push扩展未启用，尚不能从日志断言物理支持情况。
- 用户确认电视不能连接ADB，只能用App调试日志；提供 `http://192.168.1.5:9978/debug/logs`，只读访问及TXT下载已成功，status当时 `playingContext=false`。沿用此前持续修复授权，采用本节窄诊断方案，无需再次请求相同授权。
- 证据目录 `/private/tmp/webhtv-fel-wait-diagnostics-20260915/`；原日志SHA256=`303afdfbd1e782b624b77f2194a15c76f5fd6eeaccc5a82b34cf5aad14fc7791`。采样/能力日志、Java分类、实际函数ASan/UBSan与源/patch一致性通过；旧静态入口断言已适配当前统一诊断链并通过。双ABI实际编译/ELF/导出通过，18依赖（含JNI）不变；13项Java及TV64包内10库/签名/ZIP通过。guard原子提交/tag收尾，精确ID由guard记录，不额外建立回填自身ID的提交。
- 候选TV64 buildTime=`202609160528`，APK SHA256=`6e9bba7846e2e99620757c7ced5c357b287ce1bc64562a79f2db511e83c86a51`。编译前基线/dirty标记保留在APK中，按本表哈希识别候选；它不证明电视卡顿已修复。唯一下一步：安装此TV64候选，先开调试日志再按原设置播放FEL约30秒，从既有Web地址读取`capability-v=1`及`WebHTV FEL wait sample`裁决等待来源。

## 9.18 新日志30：无ADB电视的Vulkan等待诊断（2026-09-15）

### 问题、研究与决定

23.976fps每帧约41.7ms；现有约32–35ms的bind墙钟时间与约1ms线程CPU时间之间有大段等待。GPU copy约21ms、最后已知渲染pass均值约21–26ms，属于异步区间，不能与CPU墙钟相加。AHB缓存已命中、无源归还失败或fence超时；不据此盲加EL线程、重启命令重放或取消同步。当前唯一待裁决问题是：这段等待主要属于runqueue调度延迟，还是非运行/非排队等待，以及输入acquire fence是否在此期间变为就绪。

固定依赖沿用9.17：mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、builder `99a60ad2141d5ace94453590903c2c6b9a0a2443`；全部为已覆盖基线，无新合并提交。访问日期均为2026-09-15。

| 来源/版本 | 等级、事实、适用性与限制 |
| --- | --- |
| 本地 stable mapper `prepare_conversion/record_conversion/fel_api_begin/fel_api_end`，VO与BL交接调用链 | A；API计时只含调用区间；BL发布前暂存、每帧重新录制与释放栅栏保护仍有效。采样只包围descriptor bind/push，不改这些行为 |
| 锁定libplacebo `src/vulkan/context.c::device_init/finalize_context`、`gpu_pass.c::vk_pass_create`及公开`vulkan.h` | A/B；创建device会自动启用可用push扩展，公开列表为实际启用。`extension-not-enabled`不能当作遗漏App配置，也不能在缺少API版本时等同物理不支持；单独记录支持/启用/版本 |
| [Linux v6.1 sched-stats](https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux/+/refs/tags/v6.1/Documentation/scheduler/sched-stats.rst)、同版`fs/proc/base.c::proc_pid_schedstat` | A；三个字段为运行纳秒、runqueue等待纳秒、调度次数；统计未启用时输出全0。读取本线程，拒绝全0、截断、溢出、倒退，不能把不可用记为零等待 |
| NDK29 `sys/resource.h`与`linux/resource.h`；[AOSP simpleperf android-14.0.0_r1 environment.cpp::GetProcessUid](https://android.googlesource.com/platform/system/extras/+/android-14.0.0_r1/simpleperf/environment.cpp) | A/B；`getrusage(RUSAGE_THREAD)`提供本线程主动/被动切换次数，simpleperf对不可读proc返回无值。次数不是时间、不保证指出某个驱动锁；缺失数据明确保留错误状态 |
| [AOSP libsync android-14.0.0_r1 sync.c::sync_wait](https://android.googlesource.com/platform/system/core/+/android-14.0.0_r1/libsync/sync.c) | A/B；poll检查sync fd的POLLIN/错误，timeout=0可只观察、不等待、不消费/关闭fd；前后状态相关不等于因果证明 |
| 上游issues/PR、Arm/Khronos技术基准 | 复用9.15–9.17已阅读的有限检索及基准；没有能证明当前等待根因的补丁。本轮只补测量，不重新扩展驱动修复搜索或套用基准收益 |
| 论文 | 不适用本诊断单元：不提出新重建、调度或性能算法；字段含义和生命周期由Linux/Android实际源码决定 |

Linux原始GitHub入口返回429，已改读同版本官方googlesource镜像并保存完整源码；联网使用127.0.0.1:7897代理。没有把失败抓取记为已读。

比较：不改则下一份日志仍无法区分等待来源；完整Perfetto/系统跟踪需设备能力，当前无ADB；强开push/修改线程或同步均缺少证据。采用窄适配：FEL且视频INFO日志启用时，每3秒最多对一次bind/push读取本线程schedstat、RUSAGE_THREAD及acquire fence前后状态；保留原API墙钟/CPU统计，另报采样自身开销。权限/能力不足只影响诊断，不重试刷屏或改变播放。首次mapper创建记录实际Vulkan版本、GPU/驱动、push广告支持与启用情况，枚举有上限，不启用新扩展。

所有样本只进入已有App/Web日志通路并有独立限流，不追加主线程处理、不保存图像/PCM、不引入线程或新的依赖/ABI/权限。只读系统调用存在有限采样开销，日志明确记录；不能从差额直接声称已测得驱动内部等待时间。

### 验收、范围与回滚

- 授权：用户要求继续通过Web日志诊断此FEL性能问题。代码范围为FEL补丁、相关原生测试/校验脚本、MPV日志分类与测试、两份libmpv及本任务/索引/构建说明；编译仅使用可再生隔离缓存，保护app/.cxx/。
- 最便宜决定性检查：真实生产函数的host ASan/UBSan验证计数解析/溢出/倒退/全0、权限失败、限频及禁用零采样、主动/被动切换、fence的pending/ready/error与零超时且不关闭；保留原有1200帧push/set录制/生命周期测试。Java检查新增记录不受普通日志洪泛影响且来源/级别严格。
- 仅同锁增量编译两ABI libmpv，检查ELF/公开导出与其他18库不变。打包本日志对应TV64，确认包内全部MPV资产、签名及ZIP；不以host/编译成功宣称电视流畅。
- 设备闭环：用户安装候选后，保持原样片/FEL设置，通过已有Web地址读取一次持续播放日志，首先裁决能力、采样有效性、runqueue/切换/fence关系，再决定性能改动。没有可用计数时仍明确未知。
- 回滚：成套恢复至基线`206a57e0e337304a8b78712c487ef245d7cb0fa0`的补丁/日志分类/两libmpv；原子提交与annotated recovery tag保留本候选，不移动已有tag、不推送。
- 时间：22:41 Asia/Shanghai声明余下25–35分钟（实现15分钟、验证/增量编译10–15分钟、打包收尾5分钟），目标23:06–23:16；电视操作与采样等待另计。

### 03:05跨日恢复与已完成验证

源码仅增加诊断：bind/push每3秒采样一次本线程schedstat/RUSAGE_THREAD与acquire fd的poll(0)，读失败/全0/倒退保持未知，权限或接口缺失不重复打开；前后采样开销单列。原来API统计、图像输入/输出、push门控、逐帧命令、barrier/semaphore/fence、NLQ和位深逻辑保留。能力枚举最多512项，仅观察，不开启扩展。新日志分类仅使测量避开旧播放状态处理，记录通过项目现有统一诊断存储。

实际生产函数的ASan/UBSan覆盖计数解析/溢出/截断/全0/倒退、拒绝访问与失效缓存、关闭时零采样、限频与时钟回退、主动/被动切换、sync fd零超时且不关闭、扩展支持/启用/入口缺失/分配失败/不完整枚举；原有1200帧cache与逐次录制、240帧交接、取消/EOF/lease/NLQ元数据等行为全部通过。EL/RPU样片解码仍因未提供素材明确SKIP。末尾旧静态入口断言已按现有代码修正，未削弱不排队主线程/不重复Logcat约束。

2026-09-16 03:05恢复时原交付目标已失效；剩余限定为双ABI增量编译、TV64 APK/Java检查和原子提交tag，估计8–12分钟，不扩研究、不重复成功测试。电视能力/性能仍待该包Web日志，不能标作已修复卡顿。

### 本机候选结果（2026-09-16 05:28构建）

- `arm64-build.log`、`armv7l-build.log`包含实际stable mapper编译及libmpv链接，未重建FFmpeg/libplacebo/JNI。`native-assets.log`、`native-boundary.log`确认两ABI ELF/命名空间/公开导出一致，另18个库逐字节不变。相对本轮HEAD基线libmpv仅增加3680/4536字节。
- 使用NDK29/API24的固定MPV构建图；Gradle用JDK21、SDK37、离线温缓存及原隔离CMake staging，未触碰受保护app/.cxx/。构建授权等待后05:28实际启动，一次有效Gradle构建1分46秒，112任务（18执行/1缓存/93未变）；未重复原生行为测试或整包构建。
- `MpvDiagnosticsPolicyTest`13项、0失败/0错误/0跳过。`apk-artifacts.json`记录TV64包10项MPV资产全部匹配、签名通过、ZIP额外开销801465字节，包含`lib/arm64-v8a/libexo_ass.so`。旧APK与该variant的精确package增量缓存已保存在证据目录，不删除用户数据。
- 原生测试的唯一未执行项为缺少实样的EL/RPU解码；此单元不改变FEL数学、解码或像素。目标电视尚未安装该候选，能力与等待分布仍未知。源码/索引检查0错误，二进制修改告警对应本guard显式拥有的两libmpv及补丁，无额外范围变更。

| 候选产物 | SHA-256 | 字节 |
| --- | --- | ---: |
| arm64-v8a libmpv | `3e3732114fdd2527bbe95cce9b635087f0a67034a50c23a59c28c6c47fc5fa05` | 17807048 |
| armeabi-v7a libmpv | `be2b7828d6806ae8cf8a390f2f48106608193ffef40d6c5e4369ddaa9dfee6c2` | 14619876 |
| TV64 debug APK，buildTime=202609160528 | `6e9bba7846e2e99620757c7ced5c357b287ce1bc64562a79f2db511e83c86a51` | 164143897 |

FEL补丁SHA256=`f2b3e011e2fac157750b20c990c447daec958f9d48304c19e4c9ff485002dfb6`；lock保持`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。设备判读先看`sched-error/rusage-error/probe-us`是否有效，再看runqueue及切换次数和acquire前后状态；不把缺失值当0、不把两个异步区间相加、不把pending→ready关联当作驱动内部因果证明。

## 历史恢复记录（9.17）

- 目标：继续修复显式FEL的首次起播失败与持续掉帧，保留BL硬解/EL软解、真实FEL重建、10bit、同步、可取消退出和其他模式。电视画面/实时性能仍未验收，不能以host或构建成功宣称完成整体需求。
- 当前分支`feature/mpv-dv7-fel`，HEAD/回滚基线`ce10d5c15ef36fa83e27c6195182717a334a1036`，tag `recovery/P2-4-fel-vk-reuse/20260913225443-ce10d5c15ef3`；guard `P2-4-fel-warmup-push` / upstream。原有`app/.cxx/`70个文件全部保护，不移动/修改；同四仓锁、无依赖升级、无推送授权。
- 新日志31（22:59–23:01）已冻结/分析：首轮首渲染725.890ms与第8帧838ms交接失败重叠；持续轮descriptor bind均值29.496–36.207ms，update仅微秒。新逐帧录制标志已生效，PTS差异计数均0不等于像素正确；用户尚未确认画面回跳消失。证据见9.17。
- 本轮代码与定向host测试已完成：VO首次实际draw独立10s界限、精确排除等待且不续期；stable仅实际启用扩展/入口/数量合规时push当前双binding，布局创建失败回原路径；保持每帧ONE_TIME录制及同步。新增renderer init/descriptors日志按严格来源独立限流写入App存储。授权为用户当前持续修复要求，未扩展FEL以外行为。
- 2026-09-14本机候选验证完成：旧VO负例已复现，新实际函数ASan/UBSan、1200帧push/普通路径、240帧交接、并发/取消/失败/默认隔离、源patch契约、双ABI/ELF/导出及两APK各10库/签名/紧凑包通过；18其他库（含JNI）逐字节不变，13项Java测试通过。EL/RPU实样因无本地样片明确SKIP，无目标电视实播。70保护文件未动，guard原子提交/tag收尾，不推送；精确提交/tag由guard记录，不另建仅为回填自身ID的提交。
- 最终APK生成于00:37，原00:15–00:35目标略超；Gradle第一次被沙箱阻止写已有wrapper缓存，获得权限后实际构建1m17s，仅一次有效构建。旧APK和两份精确debug打包缓存保存在本轮证据目录，CMake仍使用隔离温缓存。证据目录`/private/tmp/webhtv-fel-warmup-push.22C7vl/`。唯一下一步：目标电视安装TV32 SHA256=`4b79ec5aecdb81764cec92fa3b6d3b6f2cf0c9816027849804561ab0eab3a0b8`，同GIJoe设置三次起播、完整57秒及seek/退出，取新App日志裁决能力、帧顺序和实时性能。

## 9.17 日志31：首次渲染初始化与descriptor绑定热点

### 证据与可证伪问题

- 原始`/Users/macbookpro/Downloads/webhtv-debug-log (31).txt`；冻结副本`/private/tmp/webhtv-fel-log31.uK6oSM/device-log31.txt`，5113行、1384543字节，SHA256=`58bcaa55373d2287259836bbb4be3113bb7bc3996101daeb1002f7725d8745fe`。同本地GIJoe 4K23.976、Android14、Mali-G57；`command-mode=fresh-bind-record replay=0`证明9.16候选生效。
- 首轮`p-yeyf8r-1`第8帧pts39.164、staged7、elapsed838ms后4003；首个`pl_render_image_mix`耗时725.890ms wall/587.604ms thread CPU，两个新AHB分配221/252ms。前7帧acquire/map成功且AImage错误/超时/过期为0。后3轮首次渲染67/8/12ms，没有相同fatal。现有初始化阶段只覆盖stable mapper创建，不覆盖实际首draw；源码证明保护存在缺口，时间重叠支持此假设，但不是厂商驱动内部根因的完整证明。
- 持续3轮mapper均值32.849/44.494/43.037ms，其中`vkCmdBindDescriptorSets`29.496/36.165/36.207ms、线程CPU约1ms、峰173ms；descriptor update只有微秒。GPU copy约20.6–20.8ms与后续GPU渲染异步重叠，不能直接求和。无中途seek段约13fps，EL常积压8帧，不支持盲加软件解码线程。
- 所有已记录`pts-different=0`；M/R/A事件包含预取/重选，交错不等于显示回跳，亦不能证明像素正确。电视是否支持push descriptors未知，原扩展列表被旧日志限流丢弃；新路径必须明确记录能力/回退原因。

### 有界研究与当前调用链（访问2026-09-13）

固定依赖不变：mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`；均为已覆盖基线，只读参考，无新合并候选。

| 来源/固定身份 | 等级、支持结论、适用性与限制 |
| --- | --- |
| 当前mpv补丁`vo.c::vo_prepare_fel_frame/fel_prepare_timed_out/process_fel_prepare/render_frame`、`vo_gpu_next.c::draw_frame` | A；VO循环在实际draw后才处理下一暂存，首draw编译可阻塞消费者；现有staging init state只含mapper阶段。应分离一次性的渲染初始化，不能统一放宽正常帧期限 |
| 锁定libplacebo `src/vulkan/gpu_pass.c::vk_pass_create/set_ds`、`context.c`、`gpu.c`、公开`vulkan.h` | A/B；实际启用KHR扩展、数量合规时创建push layout，不建descriptor pool，逐次`CmdPushDescriptorSetKHR`；公开extensions表示实际启用，不能只看函数地址。成熟同栈方式可窄用，不升级libplacebo、不复制整个渲染器 |
| [Khronos descriptor规范](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/descriptorsets.adoc)，`f84d432d5b8912362f96f581f29bbc4f3c8c7843` | A；每次begin后push状态未定义，所有静态使用binding须逐次写入，包括immutable sampler；`dstSet`被忽略，YCbCr/数量及layout约束必须保持。只改变descriptor提供方法，不能省掉当前帧资源或同步 |
| [Khronos push sample/说明](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/extensions/push_descriptors/push_descriptors.cpp)，`ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1` | A/B；示例每帧push当前descriptor并避免普通pool/set绑定；README解释CPU管理成本。独立API用法旁证，不代表当前电视支持或一定更快 |
| 定向GitHub issues/PR检索`vkCmdBindDescriptorSets Mali`、`push descriptors Mali`；原响应在本轮证据目录 | C/D；未找到证明当前Mali问题的可直接移植修复。已读Godot PR92611实际针对Adreno5xx draw/uniform问题，不适用、不采纳；不把相似关键词当同根因 |
| 技术文章/benchmark | 沿用9.15已固定的Arm/Khronos descriptor管理基准，加上述官方sample README；本次热点是bind不是update，因此不直接套用其百分比收益 |
| 论文 | 本窄单元不适用：不改FEL/NLQ或并行算法；API合法性与一次性状态机由规范、源码和负例裁决，不能由画质/编码论文解决，也不继续泛搜 |

相关原始材料位于`/private/tmp/webhtv-fel-warmup-push.22C7vl/`，规范沿用`/private/tmp/webhtv-fel-vk-reuse.e7UiUB/`。联网使用用户127.0.0.1:7897代理。剩余不确定性需设备对照，不再扩大检索。

### 比较、决定、验收与回滚

- 不改：保留已证实首次初始化保护缺口及bind热点；不满足本轮目标。
- 原上游：libplacebo已有push实现，但不管理本地stable mapper的layout/外部YCbCr图像；原样升级/复制不能自动修复。增加CPU线程、重启跨帧命令重放或CPU回读不能对准现有证据，不采用。
- 窄适配：只在FEL+GPU_EL_SW+MediaCodec的首次实际draw进入独立10s初始化阶段，开始/结束主动维护pending期限，即使producer没有采样到阶段变化也正确；结束恢复750ms正常帧期限。初始化不可被轮询、每帧或普通seek无限延续；保持取消/代际/退出保护，不让GPU调用持有VO锁。
- 同单元对已定位的bind热点：仅设备实际启用push扩展、入口可用且YCbCr/descriptor数量合法时创建push layout，每帧push完整当前input/output，仍ONE_TIME录制、保留barrier/fence/lease与原shader。能力不足或初始化失败保留普通descriptor方式，新增有界能力、模式和API耗时日志进入现有App调试存储。没有默认模式、公开API/ABI、许可证、依赖或二进制所有权变更。
- 最便宜决定性检查：真实VO函数旧逻辑负例（首draw跨750ms但未达10s），新逻辑覆盖启动/期间新请求/两次轮询间完整warmup、10s真挂死、结束后750ms、取消与非FEL隔离。实际mapper函数host测试覆盖push完整写入/当前帧身份/能力不足fallback/资源销毁，保留已有pending/缓存/帧顺序/源归还契约。
- 构建验收：权威补丁与固定前FEL源码一致；同锁双ABI libmpv/ELF/公开导出、其他18库不变；必要诊断Java测试；TV32/Mobile64 APK逐库身份/签名/紧凑包。现有`app/.cxx/`70文件不触碰，复用隔离CMake温缓存。
- 设备验收：同电视同样片重复起播3次，完整57秒并seek/退出；记录是否push、bind/push/map耗时、FPS/掉帧/A-V、无4003/花屏/前后帧回跳。目标接近23.976fps且A-V偏差不持续增长。设备不支持时不承诺加速，不以host计数替代像素/性能。
- 用户已明确批准继续实现本FEL可靠性/性能任务；本单元先完成可恢复的本机候选，整体需求仍待电视验收。回滚至`ce10d5c15ef36fa83e27c6195182717a334a1036`对应源/补丁/诊断/两libmpv整套，不移动已有tag、不推送、不触碰保护缓存。

### 00:12实施与host验证

- 实际源码只改`vo.c`、`hwdec_aimagereader_vk_stable.c`，权威补丁由固定前FEL树和当前HEAD机械生成这两个section，其余section原样保留；正向/反向应用及静态契约通过。VO不在GPU调用期间持锁，不在普通seek/reconfig重置一次性初始化；结束只扣本次draw实际占用、保留此前已花的帧等待。
- push门控按实际启用KHR扩展、两个入口及保守的`sampler_descriptors + 1 <= maxPushDescriptors`决定；immutable YCbCr sampler不变。push模式不建pool/分配set、不对新增output调用普通update；每次新record完整push两个binding。仅push布局创建失败重试普通布局，不掩盖两条路径共同的OOM/设备故障。
- `warmup-negative.log`：旧VO在900ms首draw的producer检查稳定失败。`native-contract.log`前9组通过（含1200帧push/普通模式及实际draw接线）；随后core测试的无操作日志stub触发`phase_name`未使用编译警告，已只修测试stub使参数被消费，未改生产逻辑或弱化-Werror。
- 辅助remaining脚本首次process substitution没有实际执行测试、无输出，不算通过；改用检查抽取结果再执行的方式。最终`native-contract-remaining.log`覆盖真实VO/wrapper、120+120帧归还、计时/查询、lease/并发、bitstream与继承及最终源/patch全部通过，不重复已成功的前9组。畸形NAL的错误输出为负例预期，EL/RPU样片解码仍SKIP。host不加载目标Vulkan驱动，不验证电视像素/FPS。

### 最终本机产物（2026-09-14 00:37，待电视实测）

- `arm64-build.log`/`armv7l-build.log`实际编译VO及stable mapper并重新链接；`native-assets.log`、`native-boundary.log`确认同锁双ABI/ELF/命名空间/公开导出不变，只2份libmpv变化、其余18库（含JNI）相同。仅既有局部变量shadow/locale警告，不扩展修理。
- `apk-build-sandbox-denied.log`记录首次wrapper缓存权限限制，未执行Gradle任务；获准访问已有缓存后的`apk-build.log`一次有效构建1m17s、181任务。JDK21/SDK37/NDK29/Gradle9.5.1，离线温缓存。`MpvDiagnosticsPolicyTest`13项0失败0跳过，包含两类新日志来源/级别/限流路由。
- `apk-artifacts.log`：两包各10项MPV native assets逐字节匹配；v2签名通过；ZIP额外开销Mobile751608/TV792208字节。未安装/操作目标电视，不能用这些结果声称push在该设备启用、性能达标或画面回跳消失。

| 产物 | SHA-256 | 字节 |
| --- | --- | ---: |
| arm64-v8a libmpv | `93d948e9518207f3f14e4e7e827f7dcf48f6698e29f5d9ab5ac8d4ba7a0c933d` | 17801680 |
| armeabi-v7a libmpv | `b53a44e19af8c4337f1afe420ce2b1ce25ddb135308b208b3fbeb6a065d527a6` | 14612900 |
| Mobile64 debug APK（00:37:48） | `63521091a1a533bcd8c236c81cf89bbbc5e3886b3201cfa740a09266bf6bdc53` | 151604740 |
| TV32 debug APK（00:37:31） | `4b79ec5aecdb81764cec92fa3b6d3b6f2cf0c9816027849804561ab0eab3a0b8` | 130473287 |

最终FEL patch SHA256=`ef8e2cf166c7f35e7a02d18d4c561d2629244815d24543ab2f3bb08b72345f88`；host脚本=`8adc75545012c390eef4e20200180776f9d47061b6c3c2106f2929e38bf22fd2`；native验证脚本=`196e9003d3cb1f6354375ef86e340052bbe9ca482e12399239bc49a08347846f`；lock仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。无依赖/许可证/公开API变化，旧模式保留。整体FEL可靠性/实时性能仍是待设备验收状态。

## 历史恢复记录（9.16）

- 目标/验收：手动FEL双层重建正常顺序播放、seek/退出可靠且改善持续性能，保护画质/同步及其他模式。14:52 TV32候选在新日志29（15:46–15:48）被用户否决画面验收；3轮warm map均值15.924/12.733/18.170ms但仍掉帧/A-V滞后，另一次751ms交接失败发生于command-hit=0。尚无像素级根因证明，整体电视需求仍未验收。
- 分支`feature/mpv-dv7-fel`；原子单元`P2-4-fel-vk-reuse` / upstream；回滚基线`dc1401638532840a8362b869b3220cb952ca7b35`，tag `recovery/P2-4-fel-steady-perf/20260913130843-dc1401638532`。沿用同锁/同scope，无新上游提交、依赖升级或推送。该候选精确提交/tag由guard收尾记录，不额外创建仅为回填自身提交ID的代码提交。
- 16:22用户明确继续后已完成窄修正：保留有界AHB导入与命令/descriptor对象槽，每个新帧重新绑定、录制ONE_TIME命令，零跨帧命令重放。增加8事件帧关联环（帧编号、输入/输出槽、请求/保存PTS及跨环保留的最近差异）、6个录制API计时和限频慢调用日志，直接进入App调试存储。不改shader/NLQ/10bit、同步、全局期限、线程、默认模式或其他播放器。
- 本机结果：真实函数ASan/UBSan及最新cache/trace/240帧交接/源补丁一致性通过；1200帧1200次重新绑定/录制、1128次对象槽命中。双ABI实际编译/ELF/公开导出通过，仅2份libmpv变化，其他18库（含JNI）不变；13项Java测试通过。18:39两个新APK包内各10库、签名与紧凑ZIP全部通过，TV32 SHA256=`4971498a956a723cf348592dc4b228f595efdda56a77a884afb51fec05645722`。本机证据不代替电视像素/帧率验收。
- 文件/证据：stable mapper的可复现源码为`third_party/patches/mpv-android-fel.patch`；诊断policy、tests、定向校验脚本、两份libmpv与本任务/索引/构建文档同一单元。完整日志在`/private/tmp/webhtv-fel-frame-order.sUwUpN/`。native于16:54完成、Gradle实际耗时1m17s；会话多次暂停/恢复使原16:53–17:08及后续18:43交付目标顺延，不增加研究或重复成功构建。
- 保护/收尾：两处新增Release缓存已确认不含原保护文件后备份至`release-cxx-before.tar`并临时隔离，18:32恢复时guard检查仍通过，原35文件不变；guard提交/tag后须原样恢复`release-cxx-config`与`release-cxx-tools`到原路径。不改ignore/guard清单、不清理其他缓存。唯一下一步：完成guard原子提交/tag及缓存归还后，交付18:39 TV32包在同一电视/样片复测三次启动、完整播放及seek/退出，读取新帧关联和慢API日志裁决回跳与性能。

## 9.16 新日志29：缓存优化后的画面回跳回归

2026-09-13 15:50 Asia/Shanghai开始只读定位；15:53估计定位/测试25–35分钟、双ABI打包15–20分钟，目标16:33–16:48，设备验证另计。现有guard、分支、锁、路径和回滚基线均沿用9.15，不新增依赖或改变产品设置。范围外缓存的隔离许可是编辑前安全门，未获许可不移动文件或修改生产代码。

### 新证据与可证伪假设

- 日志副本`device-log29.txt`为4681行/1229107字节，SHA256=`dc510194dc158e39b778846730417ac6c66b31daf68d8b062d370d6021443b89`；对应14:52:31 TV32 APK，SHA256见9.15。
- 持续播放trace `p-xzid2g-1/p-xzjb48-2/p-xzkc51-4`：warm map均值15.924/12.733/18.170ms，command-hit=325/321/223，导入均为9个缓冲区且没有播放中淘汰。AImage acquired=mapped=389/386/297，timeouts/stale/newer/errors均0；unavailable outputs=0。没有日志证据支持“解码器交出了时间戳错配的AImage”，但这些统计不验证GPU读到的实际像素，也不完整证明显示PTS单调。
- `p-xzjb48-2`仍记录A/V滞后6570ms，不能用map变快称整体性能达标。其余持续轮含seek，不能混加重置前后掉帧计数。
- `p-xzk24a-3`在pts=16.225、staged=8、elapsed-ms=751时交接失败；共导入9个新buffer，command-hit/miss均0，尚未生成缓存录制。这次失败不能归因于缓存命令重放；首次输出资源已初始化不代表随后每个AHB导入都便宜。具体超时阶段仍需新日志，不能直接放宽全局超时。
- 核查`recording_matches/select_recording/prepare_conversion/submit_conversion`：键含input/output身份、crop/geometry/query，逐次提交使用当前semaphore，pending不可复用；`stable_reuse/select_reusable_output`与`hwdec_acquire`仍保留原逻辑身份/lease。尚未找到可由现有源码直接证明的错帧分支。首要假设是新增录制重放在外部动态图像路径不兼容；反假设为扩大导入缓存暴露已有外部图像可见性问题或更快流水线暴露输出生命周期问题。

### 有界增量参考、决定与验收

- 继续采用9.15已固定revision的Vulkan命令/descriptor生命周期规范；规范允许完成后的非ONE_TIME命令再次提交，因此不能声称“Vulkan禁止复用”或未经实测断定Mali驱动bug。
- Google ARCore官方`3abfeb18669c2cbb2d07057f135d117ee9d91826`的[`BackgroundRenderer::DrawBackground`](https://github.com/google-ar/arcore-android-sdk/blob/3abfeb18669c2cbb2d07057f135d117ee9d91826/samples/hello_ar_vulkan_c/app/src/main/cpp/background_renderer.cc)，2026-09-13读取，A/B：每帧导入外部图像、更新descriptor并记录绘制，可参考其动态资源处理方式；不是FEL、也不能证明本机驱动根因或整段照搬。原文件保存于证据目录。
- GitHub两次定向检索的原响应已保存；ncnn #5531是AHB导入花屏的单一用户报告，没有证明当前重放问题的修复，等级D，不据此改代码。未找到可直接移植且能解释本次回归的成熟补丁。论文不适用本单变量兼容性回退，不改FEL数学或提出新的并行算法，停止扩大检索。
- 不改：无法接受新画面回跳；直接整体撤回9.15：回到已知慢路径但无法区分两个变量；建议窄修正：先停用转换命令的跨帧重放，恢复逐帧descriptor绑定与录制，保留导入缓存/原同步，细分record内barrier/bind/push/dispatch耗时并记录帧身份/输出槽关联。若相同设备仍回跳，再以新证据单独处理导入缓存，不盲目扩池/线程/取消fence或降低画质。
- 定向验收先证明每次新帧确实重录、绑定当前input/output且不会重置pending命令，保留移除/错误/lease保护，再同锁双ABI/包验证；目标设备连续运动场景、seek/退出和重复启动检查无回跳、无错帧、无新增失败。性能仍单独记录，不能用host调用次数或编译成功替代画面验收。
- 16:22用户明确继续后已实施上述窄修正，缓存隔离与验证进度见Recovery anchor。新增`WebHTV FEL frame order:`是逻辑关联而非像素校验，PTS差异仅记录、不丢帧/失败；`WebHTV FEL api slow:`仅记录≥100ms调用且每3秒最多4条，排除重复的RECORD总计。两类均沿用独立限流、直接写App调试存储而不排主线程/重复Logcat。回滚锚点仍为`dc1401638532840a8362b869b3220cb952ca7b35`及其9.15已记录tag，源/补丁/二进制必须成套；不把14:52候选或host测试标为设备通过。

### 最新本机验证与候选产物（2026-09-13）

- `native-contract.log`行为测试已通过，其末尾源/patch一致性首次失败是因补丁尚未重生成；随后`patch-generation.log`证明固定前置基线正向应用、实际源码反向校验通过。最终输入槽/跨环PTS细化后，`native-contract-followup.log`对受影响真实函数重新执行ASan/UBSan及最终源/patch检查，全部通过；其他已经成功且未变更的契约不重复跑。EL/RPU实样解码因未提供本地样片仍明确SKIP，不计为新设备验证。
- `arm64-build.log`/`armv7l-build.log`均实际编译stable mapper并重新链接；`native-assets.log`与`native-boundary.log`证明双ABI/命名空间/ELF/公开导出/包集合一致，18个其他库字节不变。只出现既有`VkFormatProperties props`局部变量shadow警告，不扩展清理。
- `apk-build.log`一次温缓存离线Gradle，JDK21/SDK37/NDK29，1m17s成功；`MpvDiagnosticsPolicyTest`13项、0失败/0跳过。CMake staging仍隔离在之前温缓存目录；只将两份精确debug package缓存和旧APK移至证据目录保留，没有删除用户数据。
- `apk-artifacts.log`两APK各10个native assets匹配、签名通过；ZIP额外开销Mobile752946/TV793314字节。保留的14:52旧包为`reuse-tv32.apk`/`reuse-mobile64.apk`，不可与本表新候选混用。目标电视不支持ADB，本轮没有安装或实测；回跳、持续掉帧及独立751ms失败仍是待设备裁决项。

| 产物（新APK为18:39） | SHA-256 | 字节 |
| --- | --- | ---: |
| arm64-v8a libmpv | `8ca9c014569af24a2b46fc8a60ad77bba80ee71bbb50121684f84f9b64ad6f9b` | 17800368 |
| armeabi-v7a libmpv | `ca5c2b0760a03de8daa76837f408657daed40c268639beaf8ac2aefa2ac183c7` | 14611796 |
| Mobile64 debug APK | `a855f4bcefabb5933a8006e49180a9b6a979102a03cbd4af6425999998d1b83f` | 151604740 |
| TV32 debug APK | `4971498a956a723cf348592dc4b228f595efdda56a77a884afb51fec05645722` | 130473287 |

最终FEL patch SHA256=`31ebae9c560e47b8edf6850d07e1512996032b9511e2475d150905ef9b2cfc3c`；lock仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。`WebHTV FEL reuse:`中的`command-hit`现在只代表对象槽命中，不代表重放，须同时读取`command-mode=fresh-bind-record replay=0`及`fresh`计数。帧关联环的M/R/A/X分别为map/reselect/alias/miss；只有逻辑身份，不能把PTS一致当作GPU像素正确的证明。

## 9.15 日志30：FEL硬件帧导入与Vulkan命令复用

2026-09-13 13:31 Asia/Shanghai开始；13:36估计剩余60–75分钟，目标14:36–14:51（定位/研究15分钟，实现/测试30–40分钟，双ABI/打包/收尾15–20分钟）；电视无ADB，设备验证另计。仍是P2-4的独立可回滚优化单元，不是依赖升级。

### 证据与范围

锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`全部保留，不新增上游提交。允许路径以guard为准：FEL补丁、定向tests/scripts、诊断策略/测试、两个libmpv、原任务/索引/构建文档；`build/mpv-native`只是可复现编译副本。其他18库（含JNI）、Exo、音频、光盘和网络行为不变。

日志三个trace为`p-xu4mq6-3/p-xu56nw-4/p-xu6561-5`，本地GIJoe 4K23.976fps，Mali-G57、4核。warm map52.190/51.119/49.819ms，线程CPU4.515/4.633/4.572ms；import16.253/14.152/13.810ms、record31.965/34.525/33.410ms。GPU转换区间20.573/20.692/20.741ms、渲染pass约25–28ms，不与墙钟简单相加。第三轮13:17:36.861–13:17:49.019 paired53→210，约12.9fps；13:17:48.552 A/V为7004ms。seek重置计数，不能累加峰值或称三轮完整播放通过。

调用链`stage_fel_before_publish → vo_prepare_fel_frame → aimagereader_vk_stable_map → record_conversion/submit_conversion`。stable导入缓存仅8槽，命中仍每帧更新descriptor、reset/begin/录制/end command buffer。尚无API细分/cache miss证据，不能断言特定驱动bug。已有`finish_output`的copy fence/input users及与mapper共用`vk_lock`的buffer-removed回调必须保留。

### 最佳实践增量研究（2026-09-13，经127.0.0.1:7897代理）

| 来源/固定身份 | 等级、支持事实、项目适用性与限制 |
| --- | --- |
| [Vulkan command生命周期](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/cmdbuffers.adoc) | A；非ONE_TIME命令完成后可再执行，pending不可修改/无SIMULTANEOUS再次提交，引用资源销毁导致失效。仅既有fence后复用，不缓存逐帧acquire/release fence |
| 同revision [descriptor规范](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/descriptorsets.adoc) | A；更新descriptor使引用它的命令失效。每条缓存命令独占descriptor，命中不更新，失效重录；不引入update-after-bind新要求 |
| [Arm/Khronos descriptor管理与基准](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/descriptor_management/README.adoc) | A/C；移动端更新可昂贵，示例44→27ms，推荐复用且避免不必要FREE_DESCRIPTOR_SET/pool reset。采用有界缓存，但不套用多drawcall的38%收益到本单dispatch |
| 同revision [ResourceCache代码](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/framework/resource_cache.cpp) | B；以descriptor内容为key，image view改变需改变key。适配为包含input/output、crop/geometry/query的有界缓存，不照搬全局缓存 |
| [Android 14 NdkImageReader](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/media/ndk/include/media/NdkImageReader.h) | A；持有外部图形对象需buffer-removed通知；AHB与AImage/fence生命周期不同。保留callback/users/异步归还，只扩大导入元数据缓存，不扩大AImage/4K像素池 |
| 锁定libplacebo `src/vulkan/gpu_pass.c::vk_pass_run/set_ds` | B；16组descriptor由完成回调归还，不能提前更新in-flight对象。沿用完成证明，不等待所有队列空闲 |
| [Godot #112157](https://github.com/godotengine/godot/issues/112157)及两次GitHub检索，原响应在证据目录 | C/D；多款Mali（含G57）的uniform共享状态崩溃，不能在所有设备复现。无可直接移植修复，也不是当前根因证据；强化缓存失效测试，不因GPU同名推断同一bug |
| 论文/更多泛博客 | 本单元不适用：不改FEL数学、解码器或并行算法；规范、成熟源码与Arm技术基准足以决定资源复用，扩大搜索不能替代实机数据 |

### 方案比较与决定

- 不改：避免新增缓存风险，但每帧重复准备及约13fps不能满足需求。
- 原上游/原stable：8槽导入、每输出一组descriptor/一次性命令简单；没有命令内容复用。盲加CPU/GPU线程不能解决已测热点，EL已领先。
- 采用窄适配：仅显式FEL把导入上限8→32；最多128条按需创建的命令/descriptor记录，不增加4K输出槽。仅warm input/output可缓存；key含身份、crop/尺寸、query状态。命中直接再次提交，acquire/release同步仍逐帧更新；LRU不选pending命令，input移除/淘汰和重建主动失效；可选缓存分配失败走原路径。
- 新增聚合命中/miss/淘汰/失效/退回及API调用数、墙钟、线程CPU；INFO统计继续不排主线程/不重复Logcat。它能证伪“导入抖动/重复录制为主因”，不提前承诺收益。
- 不改shader/NLQ/10bit、丢EL/降分辨率、CPU回读、fence、解码线程或renderer。默认off及原模式不变，无新API/ABI/依赖/许可证；额外driver元数据有上限，真实占用/收益待设备测量。

### 验收、批准、回滚

用户本轮明确批准沿上述诊断继续实施，无需重复逐项确认。最便宜决定性验证是抽取真实函数的host ASan/UBSan：warm命中不更新/重录；cold/新input代际/crop/query变化不误命中；pending不可命中/淘汰；容量有界；可选分配失败回原路径；移除在最后GPU用户完成后失效；teardown顺序。保留现有240帧交接/query/取消/EOF/lease契约；12输入轮转验证调用次数减少，不称为电视FPS。

然后仅双ABI libmpv增量编译、ELF/公开导出/18库不变、定向Java日志策略测试、TV32/Mobile64包内容/签名。目标电视同设置三轮完整57秒并seek/退出，命中率趋稳/map成本下降，目标接近23.976fps且无递增A/V偏差、4003、花屏或退出失效；无设备证据只交付本机已验证候选，整体需求不标完成。

回滚到本节完整WebHTV基线commit/tag，FEL源码/补丁/诊断与两份libmpv成套恢复，不动原`app/.cxx/`、不移动tag、不推送。

### 实施与交付证据（2026-09-13 14:55）

- 实施文件为stable mapper（由固定前置基线更新FEL patch）、诊断policy及测试、定向native测试/验证脚本、两份libmpv。AHB 32槽、最多128条lazy command/descriptor记录；cold、移除、裁剪/尺寸/query变化、pending、分配失败均有原路径或失效保护。不增加像素缓存，不缓存逐帧semaphore，query仍在原fence完成后读取。非FEL仍8槽、一次性命令。
- `native-contract-final.log`：真实生产函数ASan/UBSan通过，覆盖冷/热layout、pending、deferred removal/同地址新对象、crop/geometry/query、LRU上限、可选分配失败（故意污染失败输出句柄）、录制失败不缓存、API计时及旧240帧交接/取消/EOF/lease。模拟1200帧/12输入/5输出的导入1200→12，descriptor更新/录制1200→72，1128次命中；仅为调用次数。未提供实样给host EL解码，该项明确SKIP，不算新设备验证。
- `arm64-build.log`、`armv7l-build.log`、`native-assets.log`、`native-boundary.log`：两ABI实际增量编译/ELF/公开导出通过，只有libmpv变化，另18库（含JNI）字节相同。无新依赖/版本/公开API。
- `apk-build.log`：JDK21、离线温缓存，一次Gradle调用1m50s通过，`MpvDiagnosticsPolicyTest`13项0失败/0跳过，同时构建TV32/Mobile64。CMake staging隔离到本证据目录；旧APK及两份精确package缓存移入同目录保留，避免增量ZIP空洞，没有删除用户数据。
- `apk-artifacts.log`：两包各10个native assets均与候选一致、签名通过；ZIP开销Mobile755801/TV798535字节。14:55验证完成，比原目标上限14:51晚4分钟，额外成本为失败路径加固/环境定位；未扩大研究或重跑已通过的ABI构建。ADB没有连接设备，未安装到目标电视，不能称已实现实时性能。

| 最终产物 | SHA-256 | 字节 |
| --- | --- | ---: |
| arm64-v8a libmpv | `72a9f828b9958f9d8f0b701dd91d84ff9a9fa05d3404c560cc594d19f6bf965a` | 17797552 |
| armeabi-v7a libmpv | `ab8c5a6c1a52763bf7bfeff0ed88e53f6c9d5cc1d4ca1f74b6d445a50de25b03` | 14606652 |
| Mobile64 debug APK | `7dca2f646d2ff8fc90d7b783c5d9afd1b88185ba61c72032c1642621f715ce50` | 151604740 |
| TV32 debug APK | `67b007a129ba488491cc666683ed8b2cc4a35d3e96c7ef6bf26b8cd56df8ad68` | 130473287 |

FEL patch SHA-256=`d94977b25d729a630504a762fd8c3491d054b14e4ada560b8745c7fd3f6e7eb6`，固定前置基线正向应用/实际源码反向验证通过；lock仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。测试脚本的新Vulkan模拟仅使用已有NDK头文件，设置`VULKAN_HEADERS_INCLUDE=<NDK>/toolchains/llvm/prebuilt/<host>/sysroot/usr/include`或`ANDROID_NDK_HOME`，不加载Android GPU驱动。

收尾阻塞（15:01）：checkpoint验证通过，但guard finish返回4，报告35个新增范围外文件，位于`app/.cxx/RelWithDebInfo/621cr346/`与`app/.cxx/tools/mobileArm64_v8aRelease/`。其CMakeCache/compile_commands创建于14:55:53，本轮保存的Gradle日志只执行两ABI的`configureCMakeDebug/buildCMakeDebug`且staging在独立临时目录，无法将这些Release文件归属本轮。原保护清单是`app/.cxx/Debug/p104q5y4/`等35文件，未报告原文件变化。没有移动/删除新缓存、修改guard状态或绕过失败门槛，等待用户确认安全隔离方式；源码/包验证无需因此重跑。

## 9.14 日志29持续掉帧：先消除诊断干扰并区分CPU/驱动/GPU

2026-09-13 11:41 Asia/Shanghai开始，目标12:40–12:50提供已本机验证的候选（定位研究15–20分钟、实现测试20–25分钟、温缓存双ABI/打包15–20分钟；设备实测另计）。此节沿用同一P2-4，不新增上游任务或第二份文档。授权为用户本轮“继续优化，解决问题，实现需求，必要时可增加详细调试日志”，不需要重复逐项确认；无推送授权。

### 当前证据及决策

基线为WebHTV `1620bac1566727f4067eda631647a11652082e74`，锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443`均保持，不引入新上游提交。

- 三轮`p-xpwush-1/p-xpxv7s-2/p-xpyne5-3`，Mali-G57，4K23.976fps的本地GIJoe样片；每轮有seek，不能用总时长/总帧数混算或把reset后的掉帧峰值相加。持续区间配对约10–12fps，最终A/V滞后数秒。映射CPU墙钟48.4/55.5/61.4ms已超过41.7ms整帧预算；其中可能含驱动阻塞、CPU被抢占和命令回收，并不是GPU shader时间。
- 当前`aimagereader_vk_stable_map`在原GPU fence后复用槽位，`pl_vulkan_hold_ex`交还输出、录制copy、队列锁/submit、独立SYNC_FD export/deleteAsync。无CPU readback、copy-wait为0，源归还与GPU资源生命周期保护有效。没有证据允许删除这些同步。
- `vd_lavc::init_avctx → mp_set_avcodec_threads`已支持自动多线程；`f_decoder_wrapper::init_group_decoder/dec_thread`已有独立BL与EL线程；不能无视8帧EL积压盲加worker。FFmpeg调用墙钟不等于所有解码工作线程CPU耗时。
- `MpvPlayer::logMessage → NativeLogWindow`所有FEL证据共用32条/5秒预算，低频GPU初始化消息仍会被其他警告挤掉；纯统计也排入主线程执行无用故障识别/字幕判断。这一重复工作可确定移除。Logcat阻塞证据支持纯性能日志只写现有App日志存储，不重复漂亮格式输出；真实warning/error仍保持旧处理。

### 增量最佳实践证据（2026-09-13访问）

| 来源/固定身份 | 等级、支持的事实 | 本项目决定与限制 |
| --- | --- | --- |
| 本次日志及上述锁定源：FFmpeg `doc/multithreading.txt`、mpv `common/av_common.c`、`vd_lavc.c` | A；frame/slice多线程不同，额外frame线程增加前视延迟；mpv自动核心数+1 | 输出实际thread_count/active_thread_type，不改用户线程设置，不以更多线程替代瓶颈验证 |
| [Vulkan queries规范](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/queries.adoc) | A；查询依赖timestampValidBits/period；不带WAIT可返回NOT_READY；旧query在reset执行前可能假就绪 | 只在既有copy fence完成后读取同槽timestamp，不等待、不新增fence；不支持或失败则统计不可用，不能使播放失败 |
| [Khronos/Arm async compute](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/async_compute/README.adoc) | A/C；Mali compute与vertex共享执行资源，fragment/compute依赖可能造成流水线气泡；多队列收益依设备/任务而异 | 当前已有compute队列，不盲加CPU/GPU队列；必须先区分阶段墙钟与GPU执行 |
| 同revision [command buffer多线程与回收基准](https://github.com/KhronosGroup/Vulkan-Samples/blob/ad5dd381b11fe2e28fdb2d14d52b0aac9b0b9ef1/samples/performance/command_buffer_usage/README.adoc) | A/C；多线程录制面向大量drawcall的CPU瓶颈；过多线程/command buffer可能反而变慢 | FEL copy只有一次dispatch；不照搬1800 drawcall/8线程的收益，不为未证实的reset成本引入复杂缓存 |
| 锁定libplacebo `src/vulkan/gpu.c::vk_timer_query/timer_begin`、`gpu_tex.c::pl_vulkan_hold_ex`及mpv `vo_gpu_next::info_callback` | A/B、成熟项目代码；异步GPU timestamp、已存在渲染pass性能缓存；hold会提交命令而非纯CPU引用计数 | 复用现有render pass统计，独立记录copy时间和CPU阶段；不把API墙钟当GPU耗时，不加同步属性查询 |
| GitHub issues检索：mpv Vulkan Android slow、跨项目Mali vkQueueSubmit performance；原响应保存本证据目录 | B/D；找到的Pi输出分辨率/Flutter/渲染后端讨论不直接证明当前MediaTek路径问题 | 未找到可直接移植且适配当前证据的修复；不采用不相关issue猜测。此前9.12/9.13已有的mpv讨论与所有权规范继续有效 |
| 学术论文/更多泛博客 | 本小单元不适用 | 不修改FEL算法或提出新并行调度；规范、实际源码和有方法的Arm基准足以决定观测方式，电视实时性仍必须实测 |

对比：不改会继续缺乏可决策耗时；无修改上游已有GPU timer但不覆盖自定义AHB copy，亦不处理App限流；盲增线程/去fence/改变shader或默认renderer风险高且无证据。采用窄适配：保留所有像素/时序与依赖，只给FEL增加聚合CPU分段、可选无等待copy timestamp、已有render pass耗时和实际解码并行信息；纯性能日志独立有界存储，不再重复送主线程/Logcat。它减少确定的诊断开销，但不预先声称主掉帧已解决。

允许路径为guard列出的FEL补丁、定向tests/校验脚本、`MpvPlayer/MpvDiagnosticsPolicy`及对应测试、两份libmpv、原任务/构建文档和索引；构建源码缓存只是可复现输入工作副本。保护原`app/.cxx/`；JNI/FFmpeg/libplacebo/Exo/音频/光盘/默认FEL关闭状态不变。新增GPU查询仅几百字节，失败不影响像素、没有新线程/公开API/依赖或许可证变更。

验收：GPU时间处理覆盖有效位回绕、未就绪/不支持/失败、fence前不可读和复用，CPU阶段冷/热分离；日志覆盖洪泛后保留低频证据、同类限流、fatal不丢及不转发非纯统计。先真实函数/host ASan+UBSan及纯Java定向测试，后双ABI ELF/公开导出和18依赖字节不变、TV32/Mobile64包内10库与签名。目标电视同设置至少三轮完整57秒（启动10秒后统计稳态）及seek/退出，目标23.976fps附近持续配对、无递增A/V偏差/4003且不改变画质；未取得此证据不得标全部需求完成。

回滚：回到上述基线提交对应的任务源、Java/测试及两份libmpv成套恢复，不动其他脏文件、不移动原tag。唯一下一步见顶部Recovery anchor。

### 实施与本机验证（2026-09-13）

- `MpvDiagnosticsPolicy::felPerformanceKind`只接受固定native来源、INFO级别和单行已知统计；11类各自最多8条/5秒，不挤占/受限于旧警告预算。`MpvPlayer::logMessage`对这些纯统计仅脱敏后写一次现有App日志存储，不排主线程、不走漂亮Logcat、不调用MPV；真实warning/error/fatal、未知日志、状态回调、字幕和DTS-HD回退保持原路径。
- `f_android_fel_perf.h`、stable mapper、`vo_gpu_next`、`vd_lavc`补充暖态map分段墙钟/线程CPU、独立冷初始化、既有copy fence后的无WAIT timestamp、已有libplacebo pass缓存、render/flush/submit/swap耗时及实际解码线程数/类型。未更改解码线程数、调度、图像/位深/NLQ、fence所有权、依赖或默认路由。诊断失败只使统计不可用，不使播放失败。
- 真实函数host ASan/UBSan、240帧CPU/异步交接、取消/EOF/lease及新增计时契约通过；`MpvDiagnosticsPolicyTest`13项、`MpvDtsHdFallbackPolicyTest`10项全部通过。证据：`native-contract.log`、`java-tests.log`。本轮未提供本地GIJoe实样给EL解码host测试，该项明确SKIP，不重复早先实样验证，也不算设备验收。
- 同锁两ABI增量编译通过，只有`libmpv.so`两份变化，其余18库（含JNI）逐字节相同；ELF/SONAME/DT_NEEDED和公开mpv导出一致。证据：`arm64-build.log`、`armv7l-build.log`、`native-assets.log`、`native-boundary.log`。仅保留已有shadow/locale/Gradle弃用警告，不处理无关问题。
- 首次APK构建1m6s，两个包内容/签名正确但增量封装有约15–18MB空洞。原包及精确的package缓存已移到证据目录保留；仅重封装一次（58s），没有重跑native或已通过的单测。最终两个包各10库逐一匹配且签名通过，ZIP开销分别745822/805437字节，低于原5MiB门槛；只交付最终包。证据：`apk-build.log`、`apk-repack.log`、`apk-final-artifacts.log`。
- 原12:50估计已超出：代码/测试续接与最终封装结构检查后需重封装。13:03本机产物校验完成，剩余仅文档/恢复提交；未扩展研究或重新全编依赖。保护`app/.cxx/`，CMake staging复用隔离临时目录。

| 最终产物 | SHA-256 | 字节 |
| --- | --- | ---: |
| arm64-v8a libmpv | `5bf20e53d76076d2a7e297384d19fbda8bd078cd54ecc9d054d6f7844333e780` | 17791200 |
| armeabi-v7a libmpv | `1a99e35a4d2e2c7265108f4cd371a673c06f16897b1e5ba2a211eb862e6c62ff` | 14599740 |
| Mobile arm64 debug APK | `11592a5d8a3eb326930cbd3f64704d58ad9a00f25492b3e4143c0e3ad4a15180` | 151588356 |
| Leanback armv7 debug APK | `871ccbea4ac975055ad54258018c3071c0e51a6624b499746c2e2e1835593f5d` | 130473287 |

最终FEL patch SHA-256=`291efca6434faacaba46e3e794beee8717c5269f480c69a423748ee8eedd4bc1`；固定前置基线正向应用、实际编译源码反向校验通过（收尾仅去掉六处补丁hunk标题尾空格，编译源码/二进制不变）。未改lock SHA-256=`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。源/测试校验值见同证据目录`source-identity.sha256`。基线仍为`1620bac1566727f4067eda631647a11652082e74`；本机已验证的诊断单元通过guard `P2-4-fel-steady-perf`原子提交并创建同ID下annotated恢复tag，不推送、不将该tag解释为电视实时性能通过。

设备门槛仍未关闭：V2453A手机曾短暂连接随后断开，目标Mali-G57电视没有ADB，本轮没有新包真机播放证据。下一轮保持相同FEL设置，重播GIJoe完整57秒至少3次并seek/退出，日志需保留`WebHTV FEL perf`/`perf stages`/`render perf`/`decoder threads`和配对/A-V进度。只用无seek稳态区间的计数差：map墙钟与线程CPU区分运算/阻塞，分段定位具体API，再结合GPU conversion与已有pass数据决定是否值得增加CPU并行；不把GPU区间或各pass均值之和当作整帧耗时。

## Recovery anchor（9.14历史）

- 2026-09-13日志29（本次1,355,757字节、11:18–11:20，不是早前同名文件）确认`1620bac1566727f4067eda631647a11652082e74`候选三轮均进入FEL播放，未见FEL fatal/交接超时，但稳定片段配对约10–12fps，23.976fps片源仍严重掉帧且A/V偏差4–5秒。该提交/tag `recovery/P2-4-fel-vo-handoff/20260913112303-1620bac15667`为本轮回滚点，未推送。
- 本轮单元 `P2-4-fel-steady-perf` / upstream，分支`feature/mpv-dv7-fel`，保护`app/.cxx/`35文件。用户明确批准继续优化、必要时补详细日志并评估CPU多线程。第9.14节的低开销分段计时和纯FEL性能日志去重已通过本机验证，按同ID guard收尾；不盲改线程数/队列、像素、同步、默认路径或依赖。
- 已有证据：排除第一次统计后，GPU map的CPU墙钟均值48.4/55.5/61.4ms，非纯GPU执行时间；AImage获取约0.10ms，无超时/错误，异步归还855/855；EL多数仍有8帧待配对。旧32条/5秒通用日志预算会吞掉低频初始化/性能消息。日志还记录一次Logcat漂亮格式同步打印卡主线程1512ms，不能单凭此认定全部持续掉帧根因。
- 本轮证据目录`/private/tmp/webhtv-fel-perf.C10X7r/`；已编辑构建缓存的stable mapper/新perf helper、`vd_lavc`实际线程报告、`vo_gpu_next`渲染/flush/submit/swap计时，以及App严格来源/级别的纯统计日志快速通道和测试。未改变像素/同步/线程数；CPU并行已存在，不能把线程增加视为已证实加速。新增query区间是GPU conversion区间（可能包含GPU依赖等待），不是纯shader执行时间；libplacebo统计是最近pass均值，不是整帧耗时。
- 定向native契约（ASan/UBSan）已通过：保留240帧交接/释放所有权用例，新增真实计时函数的冷/热区间、不支持/创建失败/NOT_READY/查询失败、有效位回绕、fence前禁止查询与无WAIT；静态opt-in及实际App快速分支检查通过。固定前置基线正向应用/构建缓存反向校验通过，生产FEL补丁已重生成；EL实样解码未重跑（本地未传样片），不冒充真机验证。
- Java23项、双ABI/ELF/18库不变及两个最终APK/签名/封装校验已通过；最终TV32 SHA256=`871ccbea4ac975055ad54258018c3071c0e51a6624b499746c2e2e1835593f5d`、Mobile64=`11592a5d8a3eb326930cbd3f64704d58ad9a00f25492b3e4143c0e3ad4a15180`。未安装到目标电视，整体需求仍待性能验收，不重跑已通过的本机门槛。
- 唯一下一步：目标电视安装上述最终TV32包，用同设置GIJoe样片三轮完整57秒并seek/退出，导出App日志，按第9.14节新增CPU/驱动/GPU证据选择下一项真实瓶颈优化；不能以本机验证声称实时播放已实现。

### 日志42续修交付记录（历史）

- 最新恢复点：2026-09-13用户要求快速保存日志41对应候选，已原子提交`0a82dc13e255524d7c0e4e04c2f51ec9119aec88`并创建`recovery/P2-4-fel-buffer-progress/20260913025508-0a82dc13e255`，含最新源码/native，未推送。用户确认起播成功率提高，但均明显卡顿，且有一次4003；不是验收通过。
- 当前分支`feature/mpv-dv7-fel`，guard `P2-4-fel-vo-handoff` / `upstream`保持active；仅FEL补丁/定向测试与校验脚本、两份libmpv、原任务/构建文档。保护原`app/.cxx/`35文件，不改FFmpeg/libplacebo/JNI/Exo/音频/光盘或默认设置。本轮未提交/tag、未推送。
- 日志42否决9.12候选：三次均首帧`staged=0`、751ms交接失败；第一轮GPU map累计CPU耗时2127383us，最终才出现`async-returns=1`。异步归还的最终计数不能证明超时前已归还。首次创建输出/compute pipeline也被算进750ms；失败后反复写EOF，首轮BL队列膨胀到28481个信号帧。没有证据将网络、BL硬解能力或EL/NLQ算法归为此次首帧失败原因。
- 第9.13节已实施：实际mapper冷初始化单独有界10秒，正常排队/交接仍750ms；wrapper用`fel_stage_frame`持有待处理帧，返回可取消的原dispatch，失败EOF仅一次。保留9.12的generation/lease确认与独立release fence、EL/NLQ/10bit、既有缓存上限及默认路径。
- 定向证据：旧VO在2.13秒冷初始化并发轮询负例失败；新实际VO/core通过冷初始化/稳态期限、单调阶段、取消/旧generation、lookahead/lease/crop测试。真实wrapper/read/process/dispatch通过pending期间不继续decode/不重复PTS修正、取消、3万次失败后请求只发一次EOF；120 CPU/120异步fence帧及C++契约仍通过。证据根目录`/private/tmp/webhtv-fel-startup.hjGizG/`。
- 本机交付：固定前置基线重新生成补丁；双ABI增量构建、ELF/公开导出通过，只变更2份libmpv、18依赖含JNI不变。TV32/Mobile64 debug一次Gradle调用4m44s成功，两包各10库与v2签名匹配。电视APK SHA256=`038d77eb0d694a7c96aa0c17bf3aa23eac7996805702187cdc2de835686489f0`，手机=`bb0e60a46c9ae913fc9df181bc8334d59fd75de9a66c05f3e3ab17ef82a521f4`。完整身份/构建更正见9.13末尾。
- 未验收风险：电视无ADB，新包未安装/实播；不能以host/构建证明真实FEL画质、重复启动、吞吐或系统GPU挂死可恢复。不能沿用日志42旧APK/旧统计判断本候选。
- 唯一下一步：目标电视安装上述TV32包，保持FEL双层重建，用同一GIJoe样片至少3次起播、完整57秒及seek/退出；取App调试日志核对`GPU init`、`handoff timeout: phase=`、`wait=async`、GPU/配对/帧率/A-V数据，裁决是否越过首帧失败及剩余掉帧。第9.12及以下旧恢复条目均为历史记录。

### 日志41之前的恢复记录（历史）

- 当前基线：用户明确指定的失败候选快照已经提交为 `cbb02fa4c40a2d0b1d04a43d6c5be4265129f98e`，恢复tag为 `recovery/P2-4-fel-reliability/20260912214750-cbb02fa4c40a`。包含第9.10节最新源码、补丁与native资产；未推送。此前 `checkpoint/dv7-fel-pure-bl-20260912` 只指向旧HEAD，不能恢复该测试候选，不移动它。
- 本轮（2026-09-12 21:52起，Asia/Shanghai）：用户授权继续分析修复；新guard为 `P2-4-fel-buffer-progress` / `upstream`，保护原 `app/.cxx/` 35个文件。只允许FEL补丁、定向测试/校验脚本、两份libmpv和原任务/构建文档；不改JNI、FFmpeg、libplacebo、Exo、音频或光盘功能。
- 新证据：日志39一轮6帧后失败，另一轮持续配对1030帧但约13fps、A/V偏差达数秒；日志40两轮分别13/6帧后4003。均pure-bl生效，已配对RPU全部来自EL且无缺失，所有已提交GPU拷贝在最终统计中完成；这否决了“纯BL输入已经解决停滞”。根因不能仅由最终统计反推第一次端口超时前的持有顺序。
- 实施状态：第9.11节候选已写入FEL补丁/构建缓存；`f_decoder_wrapper.c::stage_fel_before_publish`在独立BL worker调用现有非阻塞VO暂存，并有界等待共享租约的source-returned原子标记；`finish_output`在GPU fence完成且AImage归还后置位，缓存命中也轮询完成；`hwdec_reconfig`维持raw-YUV存储、显示时恢复实际帧表示。新增生产者/BL/EL耗时日志，旧模式不调用计时器。未改变FFmpeg/JNI/依赖锁或默认路由。
- 已验证：2026-09-12 22:41定向host测试通过，新测试编译实际生产者交接、GPU完成/缓存命中函数，在单可用输出模型下连续120帧、source-returned顺序、750ms超时和准入隔离通过；受影响core/VO/lookahead/seek/reset/lease/crop/RPU缓存测试及静态接线通过。随后双ABI增量构建、ELF/公开导出通过，仅两份libmpv变化，其余18库（含JNI）字节一致。2026-09-13两个debug包构建通过（Gradle 1m29s），各10个MPV资产逐一匹配且v2签名通过。证据`/private/tmp/webhtv-fel-handoff.hU4yvh/`；不能据此宣称电视问题修复。
- 当前候选：电视32位APK SHA256=`26a03ee980a26f900dbd79d6fc0b6c0cc03759f2b91829e52d24df014cae9ef8`，手机64位=`6ca4cec6f2c5a5c8b73bd2dc311c1be5fb6266078b49887a6b581f2a3e5dad99`。完整路径、native/补丁身份和验证见9.11节。本轮修改未提交/tag，guard仍active；此前用户明确要求的失败快照tag保持不变。电视无ADB，未安装或验证新候选。
- 唯一下一步：电视安装本节32位APK，保持「FEL双层重建」并使用同一GIJoe样片，至少重复起播3次、完整播放57秒并seek/退出；用App调试日志的`WebHTV FEL producer handoff`、`WebHTV FEL decoder cost`及GPU/配对统计确认是否仍停滞和是否达到实时。如果仍失败且源已在发布前归还，不再重复调队列/超时，须按新证据更换根因路线。

### 此前恢复记录（历史，9.10及以前；不代表当前候选）

- 目标：在 MPV「播放性能 → DV7处理」增加 **FEL 双层重建**，仅手动选择时允许 BL 硬解 + EL 软解 + libplacebo GPU 重建；默认值与现有模式不变。
- 授权：2026-09-12 用户批准新分支实施、去掉名称中的“（实验）”，要求保护既有功能和性能、后续无需逐步确认；08:56 用户进一步批准第 8 节最佳实践方案实施。未授权推送。
- 分支：`feature/mpv-dv7-fel`；基线/回滚锚点：`fc62397591701b2232ae7de4f50a032bd7742064`。
- Lane/guard：`upstream` / `P2-4-fel-reliability` 已启动，基线 `ec68966c1d72be1c27533e7e9450763a4189febe`；原 `app/.cxx/` 35 个未跟踪文件仍受保护。
- 状态（2026-09-12，日志38后pure-BL候选）：第9.10节硬解输入/RPU分离已实施；真实BSF120包、三处共360帧软件EL/RPU/NLQ、实际继承函数及前6组队列/生命周期ASan/UBSan通过。两ABI编译/ELF/公开导出、最终紧凑两APK各10库及签名通过，产物身份见第9.10节末尾。目标电视未安装/实播本候选，不能宣称已修复；同一guard、未提交/tag，HEAD仍为`ec68966c1d72be1c27533e7e9450763a4189febe`。
- 当前文件/符号：同一`mpv-android-fel.patch`内`android_fel_packet.h::mp_android_fel_packet_init/prepare`现在只给硬解传纯BL，清理私有context/packet的DV/EL配置；`f_enhancement_pair.c`复用既有EL RPU继承，增加来源/缺失统计。新增`fel_el_rpu_test.c`、`fel_rpu_inherit_test.c`，更新packet测试、host脚本及native marker。之前core异步提前暂存、图像/GPU租约、显示参数、配对、非阻塞诊断和Java/JNI可靠性修改全部保留。本轮相对日志38只有两份libmpv改变，JNI/FFmpeg/libplacebo及其余18个native资产不变。
- 已完成证据：此前Java/JNI未改的68个不同JUnit、VO丢帧有限缓冲、BSF helper与120个GIJoe包检查保留为历史证据。本轮实际core/VO/mapper函数受限输出及2/6/10帧前视、异步重试/超时/reset、hrseek至EOF、缓存租约、方向/裁剪、实际mp_image引用与并发诊断ASan/UBSan通过；最终两ABI与两APK各10库/签名通过。相对guard仍为4个目标库变化、16个依赖不变。当前产物/哈希以第9.9节末尾为准，第6/9.7/9.8节是历史记录。
- 已验证/待验边界：68个不同的定向JUnit用例通过（恢复策略9项加此前未改59项）；native host ASan/UBSan、两ABI编译/ELF/公开导出、两APK各10个native assets匹配及v2签名检查通过；只更改4个目标库，另外16个库字节不变。日志32确认FEL GPU暂存被调用，但电视连续重建实播未通过；画质、吞吐、seek/退出及独立恢复页生命周期仍待验证。
- 未解决风险：日志36已否定“只补齐VO丢帧暂存就解决电视停滞”的假设；30个配对帧含恢复点前preroll，不能据此声称持续播放改善。电视不能 ADB，必要诊断进入 `http://192.168.1.5:9978/debug/logs` / `/debug/stream?v=0`。画质/吞吐/seek/退出和独立恢复页生命周期仍待实播，不能用构建通过替代。恢复页不承诺挽救系统/GPU整体挂死。
- 唯一下一步：目标电视安装第9.10节SHA256以`7666ca86`开头的新32位APK，保持FEL双层重建，用同一GIJoe样片完整播放并seek/退出一次；核对pure-bl、RPU来源/缺失和BL/EL/GPU进度，判断是否越过日志38的固定6帧停滞。无ADB不能以host结果替代，不finish/commit/tag。

## 1. 完成范围与约束

允许改动：MPV 设置/弹窗、MPV 引擎/输出策略及必要 PlayerManager 接线、相应测试；独立 `third_party/patches/mpv-android-fel.patch`、native 构建/校验脚本与构建说明、两 ARM ABI 的受影响 MPV 资产；本文件与总索引。工作缓存仅用于可复现构建。

不改：Exo、IJK、音频策略、原盘菜单逻辑、FFmpeg/libplacebo 版本、既有 Surface/fence/MediaCodec starvation 补丁。首轮未改 JNI；第9节经批准的可靠性续修新增 NODE 事件桥接，必须成套重建两 ABI `libplayer.so`，现有 C/libmpv 与 JNI 导出符号不变。保留旧设置值 `0=P8.1`、`1=HDR10`，新增值，不迁移用户偏好、不自动启用 FEL。

当前代理沿用 `http://127.0.0.1:7897` / `socks5://127.0.0.1:7897`。无需重复已完成的上游搜索。

2026-09-12 02:40 Asia/Shanghai 的执行估计：代码和测试 40–50 分钟；温缓存双 ABI 构建/校验 15–25 分钟；打包/设备回归 20–30 分钟；收尾约 5 分钟，目标 04:25 前（设备连接等待另计）。估计不是退出或降低验收的依据。

## 2. 固定基线与上游台账

来源身份以 `third_party/mpv-native-lock.json`、已有源码缓存和前轮保存的上游 API 响应为证。旧 P2-4 分期可从 `9fcab83f9084446566240a8e8f5233d87d0274cc:docs/upstream-player-dependency-merge-assessment-2026-08-20.md` 的 42.5 节恢复。

| 仓库/组件 | 完整 revision | 本阶段处置 |
| --- | --- | --- |
| FongMi/mpv（锁定） | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | 保持，基于现有 WebHTV 补丁追加窄适配 |
| FongMi/FFmpeg | `177f090e0503b7e013922ca903bde14b1c375f18` | 已有 dovi_split / RPU；不升级、不重编无关 codec |
| FongMi/libplacebo | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` | 已有 FEL/NLQ；复用 GPU 合成 |
| FongMi/mpv-android | `99a60ad2141d5ace94453590903c2c6b9a0a2443` | 构建框架保持 |
| mpv-player/mpv PR #17932 merge | `99b4c12cccb4d8d3f72b41944cb6c640e2156650` | 已覆盖，已核实为锁定 mpv 祖先；不重复合并 |
| mpv-player/mpv PR #17932 head | `fb39a3c147bd5da26085a2bed57a5e40670d8c14` | 已被上述 merge 覆盖，仅作为配对/调度参考 |
| libplacebo MR !851 head | `05ac2cca6571c04d06369a26825d207781b73f32` | 当前源码含相关实现；本地浅克隆无该对象，不声称已证明 ancestry |
| FongMi/mpv Android hybrid | `06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042` | **部分适配**：只取 EL force_swdec 与必要接线；其余 Surface/HDR/OSD 重写不采用，保留本地后续修复 |

NDK `29.0.14206865` / r29，native API 24；`arm64-v8a`、`armeabi-v7a`；构建使用已有 `build/mpv-native/mpv-android/buildscripts/deps/` 与 ABI build/prefix 缓存。缓存含现有补丁，不 reset/覆盖不明修改。

## 3. Best-practice 决策记录

访问日期均为 2026-09-12。等级：A=实际源码/测试/官方资料；B=维护者讨论或成熟相关项目；C=性能推断，必须实测。

| 证据类别与来源 | 等级/支持的结论 | WebHTV 适用性、限制与决定影响 |
| --- | --- | --- |
| [mpv PR #17932](https://github.com/mpv-player/mpv/pull/17932)，源码 `demux/dovi_split.c`、`filters/f_enhancement_pair.c`、`vo_gpu_next.c` | A；现有拆分、PTS 配对、EL 软件帧上传可复用 | 使用原配对/seek/reset，不另写不精确的帧序号容错算法；上游桌面成功不证明 Android 成功 |
| [FongMi/mpv 06ec6e](https://github.com/FongMi/mpv/commit/06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042)，实际各文件 diff | A；EL 独立强制软解可避免两解码器争用同一 AImageReader Surface | 拒绝 hardware/hybrid decoder wrapper，不能仅关闭普通 hwaccel；只适配必要接线，不能整包 cherry-pick |
| [libplacebo MR !851](https://code.videolan.org/videolan/libplacebo/-/merge_requests/851)，锁定源码 `sh_dovi_compose_nlq()` / `sample_el()` | A；已有 BL reshape、NLQ 与采样融合 | 采用 GPU 重建，不把 4K 图像 readback 到 CPU；MR notes API 401，未获取的讨论不作已读证据 |
| [MediaCodec 官方文档](https://developer.android.com/reference/android/media/MediaCodec)、[AHardwareBuffer 官方文档](https://developer.android.com/ndk/reference/group/a-hardware-buffer) | A；Surface / GPU buffer 生命周期及格式能力是硬约束 | 原始 YUV 编码值、10-bit 精度与持有/释放规则必须保持，不能将已映射或 8-bit BL 再叠 EL |
| FelBaker `cae66302433578ec62afa8c5c5809e93d0405b2f`、DoViBaker `ffba39830b694ddca0bf5f73dcf1b462713bb7f4` 的源码/技术说明 | B；真实 FEL 残差与仅用 RPU/BL 不是同一画面 | 可作算法交叉证据和样片预期；离线 VapourSynth 工作负载不是手机实时性能证明 |
| 上述 mpv PR 维护者讨论及原始 API comments；相关项目 benchmark/field 说明 | B/C；双解码队列/残差并非所有设备都能实时承担 | 不承诺所有手机/电视无额外成本；只 opt-in，保留原模式和诊断，性能需比较冻结基线 |
| 论文类别 | 不适用作为实施规范 | 此阶段不是推导新的 DV 算法；没有论文能替代实际 RPU/NLQ 源码、Android 互操作和真机验收，故不新增泛视频论文搜索 |

原始研究响应保存在 `/private/tmp/webhtv-dv7-fel.7Daduc/`；长期事实已记入此文档，不依赖临时目录作为唯一决策记录。

### 当前代码与数据流

- `PlaybackPerformanceSetting` 已将 MPV 与 Exo 的 DV7 key 分开；MPV 默认 P8.1，旧值/旧 key 兼容必须保留。
- `PlaybackPerformanceDialog.optionAction()` 目前在两值间轮换；Mobile 与 Leanback 共用，新增第三值，不扩展 Exo。
- `MpvPlayerEngine.selectDv7Handling()` 对旧模式始终优先原生 DV7；新 FEL 显式请求必须能覆盖原生直出，其他判断顺序不变。
- `PlayerManager.evaluateMpvAutoOutput()/prepareMpvOutputForNewItem()` 负责获取源 profile 并按需重建/保留进度；必须防止自动直出覆盖 FEL、也防止普通文件继承上个 FEL 文件的输出状态。
- `mpv-android-dovi-el-surface.patch` 默认不允许 Android EL；不能简单全局删除保护或让两个 MediaCodec 共享输出 Surface。
- FFmpeg 两 ABI `CONFIG_DOVI_SPLIT_BSF=1`；libplacebo 两 ABI `PL_HAVE_DOVI=1`、API 375。现有 Vulkan AHB direct 已保留 RGB_IDENTITY/FULL 与真实 sample depth；保留全部 direct/stable/generic 流程。

### 方案比较与决定

| 方案 | 正确性/功能 | 性能/兼容/维护 | 决定 |
| --- | --- | --- | --- |
| 不改 | 只能用 BL 或保留 RPU 的单层映射，不能真正重建 FEL | 无新风险 | 不满足已批准需求 |
| 原样整包采用 06ec6e | 包含混合解码，但还重写 Surface/HDR/OSD | 会覆盖本地原盘、输出时序与 starvation 修复，回滚面过大 | 拒绝 |
| WebHTV 窄适配 | 独立 opt-in；BL 使用现有 HEVC 硬解，EL 强制软件，gpu-next/libplacebo 合成；非 FEL 路径保持原状 | 不新增大型依赖；EL/GPU 有真实开销，仅选择后承担；容易连同资产整体回滚 | **实施** |
| CPU 4K readback/自行重写合成 | 可能可出图，但易损失精度且带宽/同步开销大 | 不符合当前性能与维护目标 | 不采用 |

安全/许可：不引入 JVM、远程执行或新下载机制；沿用 mpv/FFmpeg/libplacebo 许可和源码来源。依旧视码流/RPU 为不可信输入，复用既有有界配对和解码错误流程，不新增无界缓存。JNI/API/ABI 不改变。

## 4. 验收、观测与回滚

1. **设置隔离**：旧值 0/1 与默认值不变；只有 MPV 有第三项；用户手动选择并重新起播/按现有重建流程生效；切回恢复原模式；未知/损坏值回到原默认值。
2. **路径隔离**：未选 FEL 时不创建 EL decoder，不改变原生 DV、P8.1、HDR10、普通视频的既有路由；选 FEL 后仅符合源 profile 的文件进入重建；不与 `mediacodec_embed` 混用。
3. **真正 FEL**：日志证明 BL 硬解、EL 软件 HEVC、PTS 配对与 EL/NLQ 合成；使用能分辨 BL-only 和 FEL 的样片，不能把画面正常误称 FEL 成功。MEL/无 EL 不误报重建。
4. **生命周期**：seek、暂停恢复、连续重开、切回旧模式；不得有重复重建循环、解码 Surface 争用、无限等待、崩溃或已知颜色损失。
5. **性能**：冻结基线与候选同设备/样片/设置比较；默认/旧模式额外 EL 工作为零。启动、持续丢帧、A/V 同步、CPU/热负载都要报告；FEL 模式新增成本不是默认性能收益，不能用降低精度或丢 EL 冒充优化。
6. **二进制与产品**：两 ABI mpv 增量构建和 ELF/SONAME/DT_NEEDED/导出与 native 资产检查；Mobile/Leanback 定向 Java 测试/编译，最小 APK 资产匹配；不更新无关 FFmpeg 库，JNI 仅随相关 NODE 接线成套更新。

最便宜的决定性检查是设置/输出策略单元测试与 native 源码路径断言，然后才做 native 编译；真实播放/性能不能由这些静态检查替代。

回滚：未提交时只撤销本任务自己创建的改动；提交后对本任务原子提交执行 `git revert`，同次恢复 App、补丁、构建脚本和两 ABI `libmpv.so`。不 reset 整个工作区、不改保护路径、不动已发布 tag。验证完成后由 guard 原子提交并立即创建唯一 annotated 本地恢复 tag；不推送。

## 5. 实施与验证记录

- 02:33–02:40：恢复 Git 和研究；从 `feature-menu` 新建 `feature/mpv-dv7-fel`；guard 保护 35 个初始 dirty 文件；ADB 暂无设备。
- 02:50：基线 native cache diff 与两 ABI 资产 SHA-256 已冻结到 `/private/tmp/webhtv-p24.z9ixxT/`；不执行全栈 checkout/reset。新补丁以 `VO_CAP_GPU_DOVI_EL_SW` 单独声明软件 EL 能力，Android 仍须显式 option + 源 Profile 7 才启用；默认 `VO_CAP_GPU_DOVI_EL` 保护原样保留。force_swdec 传入配对器、解码线程并拒绝硬件/hybrid wrapper；沿用原 PTS/队列/reset。在新路径内对 MEL 仅省略无用 EL 上传，不改变旧路径。设置、策略接线开始，定向测试未运行。
- 03:10：首轮两 ABI native 编译通过。补上与 WebHTV `hwdec-software-fallback=no` 的必要兼容：主动 force_swdec 的 EL 不属于 BL 硬解失败回退，不能被该设置拒绝；未 force_swdec 时判断保持不变，修正后需要增量重编。第一轮裸 ninja 的 PATH/跨平台 pkg-config 环境不完整，改用已有 buildall 的单 mpv 目标，不重建无关依赖。Java 定向验证正在运行；Gradle 使用临时 CMake staging 目录，避免改动受保护的 `app/.cxx/`。
- FEL 的必要 VO/GPU/BL 保真与 demux 选项是耦合契约，只有真实 DV7 激活 FEL 时覆盖冲突的 mpv.conf 同名配置；音频、缓存、Vulkan backend、用户 shader 等不变。显式软解 BL 仍尊重用户选择，但该新模式关闭破坏残差精度的解码跳帧/省滤波，仅允许输出丢帧；EL 始终软解。
- 06:58（会话续接后重新核对本地时间）：57 项定向 JUnit（8 类）全部通过，Mobile arm64 与 Leanback armv7 Java 编译通过（日志 `java-verification.log`，69 秒）；两 ABI 更新后的 native 编译/链接通过。原时间估计已过，剩余本机校验目标约 07:25；ADB 仍无设备。
- 07:05 源码复核：OpenGL 原始 DOVI 映射已有 `GL_EXT_YUV_target` 能力拒绝门控，不会拿 RGB8 冒充原始 YUV；Vulkan 继续复用既有原始 YUV direct/stable/generic。另补齐 `receive_frame()` 的 `force_eof` 重试终止条件，与现有 reinit 的有界失败策略一致，避免新 FEL GPU 路径遇到硬解不兼容时无限重试。此 native 修正须重编两 ABI；Java 未改，不重复已通过的 JUnit。
- 验证结果、最终资产 SHA-256、构建日志和提交/tag 由本任务后续阶段补充；此时不宣称实现完成。

## 6. 最终本机验证与待真机状态（2026-09-12）

证据根目录：`/private/tmp/webhtv-p24.z9ixxT/`。当前 HEAD 仍为基线，工作树持有本任务完整未提交改动；不自动推送。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 8 类定向 JUnit，57 项 | 通过 | `java-verification.log`；不重复运行 |
| Mobile arm64 / Leanback armv7 Java 编译 | 通过 | 同上 |
| 最终双 ABI native 编译（含 forced-EOF 修正） | 通过 | `native-bounded-fallback-build.log` |
| `verify_mpv_fel_contract.py --mpv-source build/mpv-native/mpv-android/buildscripts/deps/mpv` | 通过 | 已应用补丁反向检查、opt-in、EL 强制软解、BL fallback 隔离及有界配对 |
| `build_mpv_native.sh --abi all --stage-only --install` | 通过 | `native-final-stage.log` |
| `verify_mpv_native_assets.sh --require-elf` | 两 ABI 通过 | `native-final-assets-verification.log`；NDK r29 llvm-readelf |
| 基线/最终 native 资产 SHA-256 | 仅两份 libmpv 改变，其余 18 份一致 | `assets-before.sha256`、`assets-final.sha256` |
| 基线/最终 libmpv 动态导出符号 | 两 ABI 完全一致 | `native-symbols-verification.log`、`exports-*-before.txt`、`exports-*-final.txt` |
| 手机 64 位、电视 32 位 debug 打包 | 通过，Gradle 报告 2m 8s | `apk-build.log`；两个 assemble 目标，一次调用 |
| APK 内 MPV 资产 | 各 10 份逐一匹配最终资产 | `apk-assets-verification.log` |
| 真机、USB / 无线发现 | 无设备，待连接 | `adb devices -l` / `adb mdns services` 均为空；不以模拟器替代 ARM MediaCodec 验收 |

Gradle 使用 `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`，并带 `--init-script /private/tmp/webhtv-p24.z9ixxT/isolated-cxx.gradle` 将 CMake staging 隔离于原 `app/.cxx/`。构建目标是 `:app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug`，没有全产品矩阵或无关 native 全栈重建。

最终 SHA-256：

| 产物 | SHA-256 |
| --- | --- |
| arm64-v8a libmpv.so | `2a936ec8491c6c7826a6bf16503e6eddf8b78c740c29aa606e985f9fceaa9213` |
| armeabi-v7a libmpv.so | `6b7bc5f5bb3dfacb279f233d378cc6426f9696e8a51737da6d5abfe371a586da` |
| Mobile arm64 debug APK（180178693 bytes） | `117960c1c0ee4e23007b3465a7f4dd2bbbc1f5d4106ad616ae8215858d47d599` |
| Leanback armv7 debug APK（144857928 bytes） | `52275d4f023f7874597f362b05609a5049ff5a059953bf3abb80afea28218ae3` |
| mpv-native-lock.json（未改） | `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159` |
| mpv-android-fel.patch | `06636e67117a491e99efac4a2cde0cdf090ea73b745a1ad8679d502b66f7f611` |
| build_mpv_native.sh | `8722d86d12b4346b472f3ba9045f029894df59dec19f61f458f108c85b0178d5` |
| verify_mpv_native_assets.sh | `daccc5d2f7f7ca9e5fbf312d5ed7d656ff0375b49e6a645a2c2113f24b76e8b5` |
| verify_mpv_fel_contract.py | `ef04328bdaeb4012e0cb3cb70d70d2f14e23b214aa45fc54253cdeeb3b8b9e52` |

候选包位于：

- `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`
- `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`

基线 Mobile APK 及两份 libmpv 已冻结在证据目录的 `baseline-mobile-arm64-debug.apk`、`baseline-arm64-libmpv.so`、`baseline-arm32-libmpv.so`，供同设备对照。

样片筛选：下载并核实 `quietvoid/dovi_tool@614c816b6446dcd1dbaf433403d499a6026fbb5a` 的 `assets/hevc_tests/regular.mkv`。它实际是 256×144、Profile 8、`el_present_flag=0`；该项目的 mux 测试也只拼接测试层，不能证明真实 FEL 残差重建。保存 `dovi-regular.probe.json` 和 `dovi-hevc-demux-test.rs`，不将这些输入算作 FEL 验收。

尚须完成第 4 节的真实 BL 硬解/EL 软解/有效 NLQ、视觉差异、seek/暂停/切换恢复与默认模式对照。没有设备证据前，不允许用编译成功或 marker 存在声称画质、吞吐、Surface 生命周期或默认性能已验收；不调用 guard finish 创建“已验证”提交/tag。

## 7. 电视启动卡死：日志回调主线程饥饿（2026-09-12）

用户随后明确要求保存当前已知问题快照，故第 6 节产物及源码已于 07:41 提交/tag，**并非通过了设备验收**。本轮回滚锚点为 `792c1f880bc151eb1cb6675034ec144aadc14766`，不修改其 tag。

### 证据与最小设计

- 用户离线证据：`/Users/macbookpro/Downloads/webhtv-debug-log (30).txt`，trace `p-w2su2i-1`，本地 `P7_FEL_GIJoe_The_Rise_of_Cobra.mkv`。在线原始快照保存于 `/private/tmp/webhtv-p24-freeze.Y6FQWF/tv-live-initial.json` 和 `tv-live-raw.txt`。
- 07:43:31 原生 `video/dolby-vision` Profile 7 decoder 不存在；07:43:32 App 已正确转入手动 FEL/gpu-next。后续日志显示 `c2.mtk.hevc.decoder` 启动成功，EL 走独立软件 HEVC，不能把首次原生直出失败当作 FEL BL 硬解也失败。
- 明确卡死链：`MpvPlayer.logMessage()` 将每条日志入主线程队列，再无条件调用 `maybeRetryDtsHdAsCore()`；后者在检查是否配置 DTS-HD 或是否为 AudioTrack 初始化错误之前，就同步读取 `audio-params/format` 与 `current-tracks/audio/codec-profile`。Java 参数提前求值还使 `firstNonEmpty(cached, query())` 在缓存非空时照样查询。
- 这次实际 `audio-spdif` 为空（PCM），根本不需要 DTS-HD 回退；查询却反复花费约 1545–1550ms，主线程看门狗记录连续堵塞超过 178 秒。Vulkan 自报创建耗时只有 10.207ms，而主线程打印相关日志跨越数分钟，证明当前日志时间混入严重排队延迟，不能直接当作 native 初始化耗时。
- 最小修复：在任何属性访问前按既有 DTS-HD 配置/一次性门控/AudioTrack 初始化错误筛选；缓存只在确实缺失且需要回退判断时同步补读，保持真实 DTS-HD 回退语义。普通、FEL、非 DTS-HD、非初始化错误日志均不查询这两项 native 属性。
- 日志补齐：关键 FEL、BL/EL 解码、DV 与失败诊断在 native 回调到达时写入现有 `PlaybackTrace → SpiderDebug → DebugLogStore`，不等待主线程；主线程仍负责状态变化，禁止从回调线程操纵 MPV。补采样限频的主线程日志排队耗时，并显式保留 `WebHTV FEL pair`、`WebHTV FEL GPU input`/NLQ 标记。不开逐帧日志、不增加非调试模式的文件 I/O。
- 这是有现成策略和日志证据的局部 bug 修复，沿用 P2-4 既有设计；不扩大上游研究、不更改 BL/EL 解码或 GPU 路由、不升级依赖。

### 验收与保护

作用域：`MpvPlayer.java`、`MpvDtsHdFallbackPolicy.java`、`MpvDiagnosticsPolicy.java`、其两份策略测试、本文件和索引。通过复现条件的门控测试、既有真实 DTS-HD 回退测试、日志分类/脱敏测试及定向编译，生成 Leanback armv7 debug 包后用在线日志验证。APK 必须继续包含第 6 节的同一份 armv7 libmpv，不重建 native。代码/测试约 15 分钟，打包约 5 分钟，目标 08:20；电视安装和交互等待另计。

新增“至少可退出”问题的只读评估：Manifest 已有独立 `:error_activity` 的 `CrashActivity`；当前 MPV watchdog 仅在调试开启时运行且只记录堆栈，不会救援。主线程挂死时同进程弹窗不能保证操作；可以补独立恢复页，由用户选择结束播放并返回首页。它还需处理原进程身份、重复触发、服务重启、二进程 App 初始化与后台启动限制，不能用主线程超时或强杀线程冒充。此次未将该新进程行为混入定向日志修复，也不自动杀进程或重播。

### 本机结果与候选交付

- `MpvDtsHdFallbackPolicyTest`：10 项通过，含日志突发/PCM/重复回退前置门控及原有 DTS-HD 回退场景。
- `MpvDiagnosticsPolicyTest`：8 项通过，含 FEL/NLQ/解码关键消息即时日志分类、普通逐帧日志排除和原有脱敏策略。
- 一次 Gradle 调用完成 Mobile arm64 定向单测/编译与 `:app:assembleLeanbackArmeabi_v7aDebug`，结果 `BUILD SUCCESSFUL in 41s`。日志 `/private/tmp/webhtv-p24-freeze.Y6FQWF/java-tests-tv-apk.log`，继续使用上一轮隔离 CMake staging，保护 `app/.cxx/`。
- 电视候选：`app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`，144857928 bytes，SHA-256 `26f5b1dbfee477e948ada579323f228f1337bb8ed5eaa0688f3e9a811dcaf390`。
- APK 内 `assets/mpv-libs/armeabi-v7a/libmpv.so` 实际 SHA-256 仍为 `6b7bc5f5bb3dfacb279f233d378cc6426f9696e8a51737da6d5abfe371a586da`，与已知问题快照一致。此次不混入任何 native 变量。
- 新日志标签为 `mpv-native`（回调到达即写入 App 调试日志）和 `mpv-anr: native-log-queue delay=...`（主线程排队耗时，5 秒限频）。现有 `/debug/logs` 与 `/debug/stream` 会直接展示，无需 ADB。
- 随后的 `(29).txt` 和在线快照已证明用户使用新候选复测，结果见第 8 节：音频查询门控有效，但卡死场景未解决。不提交/tag 为已验收的完整修复。独立恢复页仍未实施，不能把本候选当作已经有强制退出兜底。

## 8. 真机二次证据与定向外部研究（2026-09-12）

### 8.1 范围、授权和研究问题

用户要求深度查阅论文、帖子、博文、官方文档、issues 和项目源码，为当前电视上的 **MediaCodec BL 硬解 + FFmpeg EL 软解 + libplacebo GPU FEL 重建** 卡死寻找参考。最新授权为调研，不进行新的播放代码修改、构建、安装、提交/tag 或推送。

仅更新本文件及 `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`，沿用当前 guard 已包含的文档路径；保护现有五份 Java/测试修改和最初的 `app/.cxx/` 35 个文件。原有源码、锁、两 ABI 二进制及其归属不变。研究从 08:25 Asia/Shanghai 开始，预计 25–35 分钟；核验采用已保存响应、源码调用链、完整提交 ID 和文档校验，不重复构建。

三个决策问题：

1. 软件 EL 较慢时，配对器是否可能占满 BL 的有限硬解输出缓冲？现有上游是否有针对性的修正？反假设是驱动/渲染 fence 或解码数据本身先失败；需要持有数、PTS、端口等待和释放计数区分。
2. 已经支持 BL 硬解的电视，应怎样保留原始 10-bit 信号并进行真正 FEL 重建？哪些项目可直接提供算法或调度参考，哪些不能证明 Android 实时可用？
3. native 变慢时，怎样避免 UI 的同步等待；若发生永久 native 死锁，怎样让用户仍有退出途径？异步化与进程隔离分别保证什么？

### 8.2 新日志已经证明什么

证据为 `/Users/macbookpro/Downloads/webhtv-debug-log (29).txt` 及在线原始快照 `/private/tmp/webhtv-p24-freeze.Y6FQWF/tv-after-log29.json` / `tv-after-log29.txt`。按时间而非文件编号排序：这是 08:12:58 开始的较新播放，trace `p-w3uti8-1`，本地 GIJoe P7 FEL MKV，从约 14.121 秒恢复。不要与 `(30).txt` 的 07:43 播放混用。

| 时刻/指标 | 直接观测 | 可以得出的结论 |
| --- | --- | --- |
| 08:13:05.261 | `MediaCodec started successfully: codec = c2.mtk.hevc.decoder` | BL 硬解启动成功，不是“电视不支持基础层硬解” |
| 08:13:05.925 / 08:13:06.900 | EL `1920x1080 yuv420p10`；BL `Using hardware decoding (mediacodec)` | 双路解码路径已经建立 |
| 08:13:07.035 | `BL=13.889000 EL=13.889000 NLQ=1 software-EL=1` | 已配对到真实 FEL 帧；恢复播放的预解码时间可早于目标时间 |
| 08:13:17.965 / 08:13:18.044 | 4K AHardwareBuffer `10-bit, raw Dolby Vision`；`matched EL uploaded with active NLQ` | 双层输入已经到达 GPU；不等于持续输出、位精度或画质验收通过 |
| 08:13:07.786–08:13:45.350 | 49 次 `Error while decoding frame (hardware decoding)` | native 错误仍存在；相邻间隔最小 0.759s、平均 0.783s、最大 0.865s |
| 08:13:41.561 | `main stalled=34661ms`，栈位于 `readTrackInfo → refreshTracks → handleEvent` | 主线程同步属性读取仍造成可见 ANR；各次调用多为约 1.55 秒 |
| 新日志标签/查询 | 已出现 `mpv-native`；未再出现那两项音频属性的 slow-native 查询 | 上轮日志查询门控确实生效，但只解决了一个入口 |

两层问题应分开：

- **已确认的 UI 缺陷**：`FILE_LOADED → refreshTracks()/syncOsdSurfaceRequirementFromMpv()/readTrackInfo() → MPVLib.getProperty*` 仍在主线程串行查询 `sub-visibility`、`sid`、`secondary-sid`、`track-list/*`。
- **有强依据但未实证的 native 假设**：16 帧 BL 配对队列与有限 MediaCodec 缓冲冲突。49 次错误的节奏吻合本地 FFmpeg 750ms 端口饥饿超时，但最新导出仍没有那条明确的 starvation 日志，也没有实际缓冲持有数量；不能写成“根因已完全证实”。
- **已确认的重试策略缺口**：`hwdec-software-fallback=no` 在 mpv 映射到 `INT_MAX`；`handle_err()` 只有累计到该阈值才设 `hwdec_failed`。已有 `force_eof` 重初始化保护不能自动终止这类运行时错误循环。禁止 BL 软回退与允许无限错误重试不是同一要求。
- **诊断盲点**：当前 `shouldLogNativeImmediately()` 按 `error/failed/invalid` 等文本筛选；FFmpeg 的关键句包含 `failing hardware`，不命中这些词。若主线程堵塞，这条错误可能一直留在旧的主线程日志路径。后续应按 native 日志级别保留 warn/error，并限频，而非只扩大关键词列表。

### 8.3 最直接的上游参考：mpv PR #18375

[PR #18375](https://github.com/mpv-player/mpv/pull/18375) 名为 `f_enhancement_pair: keep retained frames within the hw surface budget`，API 确认为已合并，合并时间 2026-08-22T19:00:12Z。读取了 PR 文件 diff、提交列表、实际合入提交及当前配对器文件历史；截至此次查询，该文件最新修改仍是以下预算修复。没有由此声称整个 mpv 最新树都已完成回归审查。

当前锁定代码只包含最初配对器 `5330cae57eba5d34719d2d6a13d98770e8841afc` 的行为及本地 FEL 适配：仍为 `QUEUE_MAX=16`，没有 `el_seen` 和 extra-frame hint 接线，以下三个改动未被本地实现覆盖。

| 实际合入的完整提交 | 实际改变 | P2-4 处置 |
| --- | --- | --- |
| [`3b4caf0f8ba3101c3aa0b2f59fbd13863e965cfc`](https://github.com/mpv-player/mpv/commit/3b4caf0f8ba3101c3aa0b2f59fbd13863e965cfc) | 首次 EL 尚未就绪时，不因为 BL 队列满就提前输出 BL-only；seek/reset 清除 `el_seen` | **建议窄适配**，同时保证 EL 调度能推进、有界失败；不能只添加一个无限等待条件 |
| [`228f3109fd1a620094758fea90dd387c64ec22c9`](https://github.com/mpv-player/mpv/commit/228f3109fd1a620094758fea90dd387c64ec22c9) | 增加 decoder wrapper 的 extra hardware frame hint，交给 `VDCTRL_SET_EXTRA_HW_FRAMES` | **有条件参考**；固定 AVHWFramesContext 帧池可用，不能当作 MediaCodec Surface 扩容开关 |
| [`b955aa28f3dc93dc6b21485a0d5b7feb8e6dc10f`](https://github.com/mpv-player/mpv/commit/b955aa28f3dc93dc6b21485a0d5b7feb8e6dc10f) | 配对队列 16→8，并为 BL/EL 申请对应额外硬解帧预算 | **建议适配其资源预算原则**，不机械照搬数字 8 或假定电视容量会随之增加 |

三者实际父子关系已由 API 验证。PR 中较早的三个修订 ID 分别是 `d3f337bf10603b5f371a0f225fd8abb8f1c9a989`、`4f4e8f6135c6c57109675a9cb48296b40332d524`、`8cadd3b93f03ccced57a2111db7b5b673616ef7b`；处置为被上述实际合入版本覆盖的参考修订，不重复作为待合并提交。

为何不能原样照搬到 Android：

- FFmpeg `libavcodec/mediacodecdec.c:mediacodec_hw_configs` 只声明 `AD_HOC | HW_DEVICE_CTX`，没有 `HW_FRAMES_CTX`。
- mpv `vd_lavc.c` 据此选择 `use_hw_device`，`init_generic_hwaccel()` 在 `!use_hw_frames` 时直接返回；`hwdec_get_extra_frames()` 增加 `initial_pool_size` 的路径不服务当前 MediaCodec Surface decoder。
- 当前 BL `AVMediaCodecBuffer` 在配对器中被引用，直到 `hwdec_aimagereader.c:mapper_map()` 才经 `av_mediacodec_release_buffer_status(..., 1)` 提交到 Surface；单纯持有 `mp_image` 并不是廉价、无限的普通内存缓存。
- 本地 `AImageReader_newWithUsage(..., maxImages=5)` 是另一层“已获取 AImage 数”限制，**不是 MediaCodec 的总输出缓冲数**。不能用 `16 > 5` 直接证明根因，也不能认为把 5 改大就解决了解码端饥饿。

### 8.4 各类资料及适用边界

访问日期均为 2026-09-12，网络使用用户指定的本机 7897 代理。等级 A=官方契约/实际代码/设备原始证据，B=维护者解释或成熟项目经验，C=未在本机复现的报告/性能结果。论文的可得范围单独标明。

| 来源与版本 | 已读证据、等级 | 对我们场景的实际价值与限制 |
| --- | --- | --- |
| [Android MediaCodec 官方文档](https://developer.android.com/reference/android/media/MediaCodec)，Buffer Processing | A；官方明确警告持有 input/output buffers 可令 codec 停滞，而且设备相关，部分 codec 要等所有 outstanding buffers 归还才继续 | 支持减少 BL 硬件帧滞留；**不**证明该电视的具体容量或当前错误唯一原因 |
| [NDK AImageReader 官方文档](https://developer.android.com/ndk/reference/group/media#aimagereader_acquirelatestimage)，API 24 起的 acquire/release 契约 | A；`maxImages` 耗尽会报 `MAX_IMAGES_ACQUIRED`，acquireLatest 丢旧帧需要至少 2 个余量 | 三层资源分别计数：codec output、AImage、GPU/fence；FEL 必须保持时间戳关联，不能靠随意丢 BL/EL 解套。当前网页涉及新 API 的格式变化不外推到这台 Android 14 电视 |
| [Kodi Android MediaCodec](https://github.com/xbmc/xbmc/blob/45a32be559f5a70a56f089353f71843b9dbbc0ec/xbmc/cores/VideoPlayer/DVDCodecs/Video/DVDVideoCodecAndroidMediaCodec.cpp)，`45a32be559f5a70a56f089353f71843b9dbbc0ec` | A/B；`GetAllowedReferences()` 返回 4；归还、丢弃、EOS 均有明确 release；正常解码短等待，丢弃预解码帧时用非阻塞 poll。该提交附有 MediaTek armv7 AVC seek 数据 | 可借鉴有限持有、及时归还、错误状态和预滚动调度；4 不是跨设备容量规范，AVC seek 数据不证明 HEVC FEL 性能，更不等于 Kodi 已提供通用 Android FEL 软件重建 |
| [libmpv client.h](https://github.com/mpv-player/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/include/mpv/client.h)，`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` | A；同步调用等待核心可用，等待时间可无界；提供 `mpv_get_property_async`、`mpv_observe_property`、async command/reply，异步调用顺序也需管理 | UI 使用缓存/订阅快照；禁止主线程等 Future、join 或重新同步补读。多个工作线程不会绕过核心串行锁，异步并不等于永久 native 死锁已隔离 |
| [media-kit 原生播放器](https://github.com/media-kit/media-kit/blob/c533e446755f51cf53c7e57aea873f2aa5355f81/media_kit/lib/src/player/native/player/real.dart)，`c533e446755f51cf53c7e57aea873f2aa5355f81` | A/B；以 `MPV_FORMAT_NODE` 观察整份 `track-list` 并更新状态；支持带 request id 的异步设置/命令 | 可借鉴事件驱动整份轨道快照。当前 WebHTV JNI `event.cpp` 只处理标量/string，尚无 NODE 分支及 GET_PROPERTY_REPLY 数据接线，不能只改 Java 调用名即视为实现；media-kit 也保留同步配置，不声称其所有路径都不会 ANR |
| [Android ANR 指南](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)、[进程/线程](https://developer.android.com/guide/components/processes-and-threads)、[后台 Activity 限制](https://developer.android.com/guide/components/activities/background-starts) | A；默认输入超时 5 秒；主线程不做阻塞工作；组件可独立进程；后台拉起有版本限制；GPU 全局挂死通常无法由 App 修复 | 区分异步化、watchdog 检测和进程恢复；不能承诺任何硬件死锁下都能退出，也不能把“线程超时”当取消 native 的保证 |
| mpv [PR #17932 讨论](https://github.com/mpv-player/mpv/pull/17932)，本文件第 2 节固定源码；FongMi [Android hybrid 改动](https://github.com/FongMi/mpv/commit/06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042) | A/B；已有双层拆分/配对；Android EL 强制软件解码避免共享同一 MediaCodec Surface；讨论记录了硬解表面不足及后续 #18375，也有另一个动态亮度元数据引发卡顿的案例 | 当前路线不是无参考自研；原有 force_swdec 是正确保留项。但不把桌面、不同 GPU、不同卡顿根因混为同一故障，不整包引入 fork 的 Surface/OSD 重写 |
| [libplacebo MR !851](https://code.videolan.org/videolan/libplacebo/-/merge_requests/851)；实际锁定代码 `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` 的 `sample_el()` / `sh_dovi_compose_nlq()` | A；已读实际 shader 及渲染接线：EL 采样、NLQ 残差叠加；MR 讨论原先 API 401，不当作已读 | 保留现有 GPU 重建核心，优先修调度/生命周期，不重写算法或把 4K 帧常态读回 CPU；原始编码值、位深和上采样仍须同帧参考验收 |
| [FelBaker](https://github.com/bbeny123/felbaker/tree/cae66302433578ec62afa8c5c5809e93d0405b2f)，`cae66302433578ec62afa8c5c5809e93d0405b2f`；[DoViBaker](https://github.com/erazortt/DoViBaker/tree/ffba39830b694ddca0bf5f73dcf1b462713bb7f4)，`ffba39830b694ddca0bf5f73dcf1b462713bb7f4` | B/C；BL+EL+RPU 烘焙至 PQ12；FelBaker 文档提供 RGB 输出 hash 校验和桌面 benchmark 方法 | 可作离线重建参考与画质验证思路，不移植 VapourSynth/AviSynth 整套运行时；M4/AVX2、best-of-10 的数据不外推到 Mali-G57/armv7。测试样片本身注明仅用于逻辑而非画质评价 |
| [Bigflake MediaCodec FAQ/样例说明](https://bigflake.com/mediacodec/) | B/C；codec 不是输入一帧必立即吐一帧；厂商 buffer 格式不同；Surface/GPU 路径和样例同步风险均有说明 | 交叉支持 backpressure、格式与 GPU 数据通路的注意事项；这是较旧 API 的技术文章，不把其 RGB/8-bit 样例直接用于 10-bit FEL |
| [mpv-android #1088](https://github.com/mpv-android/mpv-android/issues/1088) 及其维护者讨论、[FFmpeg #11676](https://trac.ffmpeg.org/ticket/11676) | B/C；Android TV 的 MediaCodec-copy 与 embed 结果不同，原生硬解能播不等于 copy/合成路径都兼容；FFmpeg ticket 仍 new、未复现/分析 | 反对用强制 `mediacodec-copy` 作为默认解决办法。报告是 Amlogic/Mali-G31，不能拿来断定这台 MediaTek/Mali-G57 的根因 |
| 论文 [Adaptive residual mapping for an efficient extension layer coding in two-layer HDR video coding](https://doi.org/10.1109/ICIP.2016.7532587)，ICIP 2016 | 本轮检索了 Crossref 与 OpenAlex 元数据/摘要；后者标为 closed，无公开全文地址，IEEE 页面未取得正文 | 摘要研究 LDR→HDR 双层编码残差映射/编码效率，不是 Android P7 FEL 解码调度实现。**未读全文、不作为算法正确性或安卓实时性能证据**；本轮不更换重建算法，因此该缺口不影响由官方契约和源码得出的调度/异步判断 |

常规 Google 结果为需脚本页面，Bing RSS 返回了明显无关内容，没有将这些内容或搜索摘要列为依据；转用精确项目 API、官方文档及正式论文索引。原始响应保存在 `/private/tmp/webhtv-p24-research.6Wp7xa/`，前期算法资料仍在 `/private/tmp/webhtv-dv7-fel.7Daduc/`；长期结论、链接、revision 和限制已记在本文件，不依赖临时文件存活。

### 8.5 适合 WebHTV 的方案比较

| 方案 | 正确性/兼容性 | 性能/维护与决定 |
| --- | --- | --- |
| 不改现状 | 保留当前代码，但已复现硬解错误和 UI ANR，不能满足需求 | **不接受为完成状态**；可作为冻结比较基线 |
| 原样合并 PR #18375 | EL 预热及固定表面预算有价值，但 MediaCodec 不走它的扩容帧池；仅改 16→8 仍可能超过设备可用输出数 | **不直接采用整包方案**；必须 Android 适配和真机验证 |
| Android FEL 窄适配 + 异步状态快照 | 仅新 FEL 软件 EL 路径限制 BL 持有/预取，优先可配对输出，保证 EL 能独立推进；严格 PTS/seek/reset；native 无进展有界退出；UI 不同步查询核心 | **推荐**。不改变未选 FEL 的默认/旧模式，不降低 EL 质量。减少重复 JNI 查询，但实际播放/功耗收益必须实测；包含 native/JNI 的部分须重新声明作用域 |
| BL 硬解后 CPU copy/readback 再上传 | 可作为格式验证后的诊断对照，但厂商布局/10-bit 读回支持不统一，不能默默转成 8-bit 或 BL 全软解 | **不作为默认修复**。仅线性 4K P010 数据约 24.9MB/帧，24fps 单方向约 597MB/s；这只是理论数据量，尚未计拷贝、上传和 stride，专有压缩布局另算 |
| 早期 GPU 复制至自有原始信号纹理 | 若确需更深配对，可能解除 codec buffer 持有；必须正确处理 AImage、fence、时间戳、10-bit 原始 YUV 信号与生命周期 | **后备研究方向**，不是现成修复。增加显存/带宽和渲染复杂度；先验证低持有方案，再决定是否值得引入 |
| 单独 watchdog / 独立进程 | watchdog 只能发现无响应；独立恢复进程或独立播放器进程才可能在故障核心无响应时继续接受操作 | 现有 `:error_activity` 可作为较小恢复页基础；完整 `:player` Service/IPC 是更大架构，**单列评估，不与 FEL 算法修复捆绑** |

推荐适配的关键约束：

1. **不是单纯缩小一个数组。** 先明确 BL/EL decoder、pair、VO/GPU 各持有多少帧；配对器有下游需求时先消费已匹配对，再决定是否继续拉 BL。软件 EL 预热与等待不得被 BL 端的同步等待饿死。队列值依实际链路验证，不能把 1、4、8 说成通用硬件容量。
2. **不把 EL 缺失伪装成成功。** 保留 RPU、PTS 精度、NLQ 和源格式；首个 EL 尚未就绪与“源确无 EL”分开。不能为了不卡而无提示长期只显示 BL，再把它计入 FEL 成功。
3. **不在主线程补读。** 订阅整份 `track-list`、相关 subtitle/selection 属性，用版本化快照更新 Java 状态；JNI 必须复制事件数据后再跨线程，处理 reply 生命周期、请求失败、过期 generation、解除订阅与 release。批量异步后也不能在 UI `Future.get()`。
4. **失败退出和软解策略分开。** 建立连续无进展/致命错误的有界终止语义，报告明确播放失败，不因用户禁用 BL 软回退而无限重试；不冒充正常 EOF，避免误触发下一集。不建议只把 750ms 改小。
5. **释放必须保持所有权。** 未经 GPU 消费/fence 确认，不能强制释放仍被引用的硬解图像；不能在线程超时后销毁仍可能被 native 使用的 handle/mutex/Surface。

### 8.6 最小分期、观测和验收门槛

这是下一轮的建议，不是本轮已实施清单。建议先解除确定的 UI 问题，同时取得 native 故障的必要计数，再独立改调度；无新增依赖、无全栈升级、无静默降画质。

1. **异步轨道快照与完整错误透传。** 复用已有 command/set-property 请求体系，补 NODE 快照与必要异步查询；保留 `source-dolby-vision-*`、HLS bitrate、音轨/字幕选择等本地字段。当前 quick-fix guard 没有 JNI 路径，不在它内直接实施。模拟核心延迟 10 秒时，主线程不得进入同步 MPV getter；遥控器移动/返回仍能被处理，输入响应目标 250ms 内。该目标是待测试门槛，非现有测量结果。
2. **FEL 资源预算与有界失败。** 在真实新增日志基础上适配 #18375 的预热/预算原则，只影响显式 FEL 路径。每 2–5 秒聚合一次 BL/EL pending/峰值、首帧/最近 PTS、配对/BL-only 数、MediaCodec 未归还 output 数及等待时间、AImage acquire/release/timeout、GPU/fence 在途数、错误及恢复次数；不逐帧刷文件。所有必要错误与汇总从独立 native 事件路径进入 App 调试日志，不能仅在 logcat。
3. **设备验收。** 同一电视、相同文件、相同设置/温度条件，分别从 0 和约 14.121 秒恢复，至少各 3 次可比较运行；记录首对/首帧耗时、连续 60 秒配对率/输出帧率/错误/晚帧、A/V 同步与 CPU/内存。可正常播放后再做 seek、暂停、退出和重复打开；FEL 缺层必须可解释，不能用长期 BL-only 换速度。因当前基线已卡死，先通过功能门槛，再与稳定旧模式比较性能。
4. **画质与原功能保护。** 用同一输入、相同 resize/色彩/DM 参数的离线 FelBaker/DoViBaker 参考和已知 FEL 差异样片确认残差生效，不以杜比标志/第一条 NLQ 日志替代画质验证。回归 DV5/P8.1、HDR10、FEL 关闭及一次蓝光菜单/退出的关键邻近场景；只覆盖改动触及的路径，不跑无关全矩阵。
5. **永久挂死退出兜底另列。** 若要进一步保证用户有操作入口，可在独立恢复进程提供“继续等待/结束故障播放并回首页”，只在用户确认后处置本 App 的目标进程，不自动重播。需要核对 PID/UID/进程代际、单实例、最小 App 初始化、服务重启及 Android 14+ 后台拉起限制。完整播放器独立进程需要 Surface/IPC/生命周期重新设计。系统或 GPU 驱动整体挂死不能承诺由 App 绝对挽救。

实现验证随实际改动选择：Java 策略单测和轨道快照/过期回包测试先行；改 JNI 则构建两个受影响 ABI 的 `libplayer.so`，改 mpv 则重建对应两 ABI `libmpv.so` 并核对 ELF/锁/资产/APK 哈希；不重建未改的 FFmpeg/libplacebo。EL 预热、有限缓冲 backpressure、seek 清队列和致命错误应有确定性回归用例；上游这三个提交未带此类测试，不能把“已合并”当作 Android 验证通过。

许可证/体积/回滚：目前只研究，包体为零变化。推荐方案不新增库；Kodi 源文件为 GPL-2.0-or-later、media-kit 为 MIT，本轮借鉴设计而非复制代码，后续若移植须保留相应许可与来源。不要引入桌面脚本运行时；二进制大小和性能变化以候选产物实测，不能预先保证数值。未来每个实施单元须将代码、补丁、锁/构建声明、产物和测试原子记录，并通过其 recovery tag 回滚；不得以恢复 `792c1f880bc151eb1cb6675034ec144aadc14766` 的方式抹掉尚未提交的日志修复。当前快照只是已知问题锚点，不是正常播放版本。

### 8.7 本轮结论与未决事实

- **路线有依据**：mpv/libplacebo 已提供核心重建，FongMi 提供 Android 软件 EL 接线，Kodi 和 media-kit 分别提供缓冲生命周期与异步状态管理的可借鉴实现。
- **发现了本地未覆盖的相关后续修复**：#18375 的真实三提交必须纳入 P2-4 适配评估，但 MediaCodec 不能照搬固定硬件帧池的扩容假设。
- **不能下“设备不支持 BL 硬解”的结论**；也不能从第一对 GPU 输入就宣称 FEL 完成。UI 同步等待已坐实，native 缓冲饥饿仍需计数验证，驱动/fence、数据及调度竞争仍是区分项。
- **未找到已证实可直接替换、并在我们这类 Android TV 路径完整验收的通用即插即用实现**。这不是“网上不存在”的断言；所查成熟部件可组合借鉴，但不是可跳过本机调试的整套成品。
- **本轮只读研究及文档更新**：生产代码、依赖锁、native 和 APK 均未更改，未复跑旧的成功测试/构建，未提交/tag/推送。下一步仅按顶部 Recovery anchor 继续，不把此调研当作实施授权或设备验收。

## 9. 最佳实践适配实施（2026-09-12 08:56 起）

用户现已明确批准第 8 节推荐方案。实施顺序：先关闭已独立验证的日志门控单元，再实施异步状态快照/完整错误透传、FEL 资源预算与有界失败、最小故障退出保护。各逻辑单元单独声明作用域和验证，不升级 FFmpeg/libplacebo，不修改 Exo、菜单或音频策略。

执行估计：当前代理代码/定向测试 60–80 分钟，温缓存双 ABI 构建与电视包 15–25 分钟，收尾约 5 分钟；目标 10:20–10:50 Asia/Shanghai，电视安装确认和真机播放另计。未通过的设备场景不能以编译结果关闭。

日志门控单元的验证依据保持第 7 节：10+8 项测试、电视 armv7 APK 构建成功；新 trace `p-w3uti8-1` 已显示即时 native 日志，未再出现 PCM 路径上的那两项音频属性 slow-native 查询。该单元可独立保存，但不代表 native 错误、其他 UI 同步调用或 FEL 画质/性能已验收。

### 9.1 可靠性实现单元

- 09:15 启动 `P2-4-fel-reliability`，保护原 35 个 dirty 文件。允许 MPV Java/JNI、专用恢复页及 App/Startup/Manifest 最小接线、FEL 补丁、定向测试、两 ABI 的 `libmpv.so`/`libplayer.so`、构建说明与本任务/索引文档；不升级锁或 FFmpeg/libplacebo，不改 Exo、原盘菜单和音频策略。
- JNI 以有界 NODE 快照传递整份 track-list 与 chapter-list；主线程轨道/章节、OSD选轨和视频元数据读取只消费订阅缓存，缺失值不回退同步查询。保留 ISO 语言回退和已选轨道行为、原有网络缓冲探针及一次性音频故障回退查询；不是全局替换所有 getProperty。按文件代际拒绝迟到事件，避免旧文件污染新状态。
- Android 软件 EL 路径的配对器采用有限 BL 持有、EL 优先推进；同时约束 decoder wrapper 预取，不把 `hwdec-extra-frames` 当成 MediaCodec 扩池。失败预算与禁止软件回退分离，聚合配对/错误诊断。
- 恢复保护独立于调试开关；新进程只做最小初始化，界面默认继续等待，只有用户明确点击退出才核验 UID/PID/启动代际并结束故障主进程。后台或系统限制拒绝拉起时记录原因，不自动杀进程。
- 最便宜验证：快照/缺失与过期状态、配对/错误预算及恢复策略定向测试；随后双 ABI native、两个产品编译/APK 与资产核对。电视不具备 ADB，本机不能替代持续播放/画质/生命周期实测。

### 9.2 当前验证证据

证据目录 `/private/tmp/webhtv-p24-reliability.ASCawt/`：

- `native-contract-final.log`：真实 JNI NODE writer 的 ASan/UBSan 编译/运行通过；覆盖 UTF-8/控制字符、int64、非有限数、坏列表、循环/深度/体积限制；FEL 预热不因 BL 满队列丢层、有限预取和连续错误预算的 host 检查通过。生产 patch 反向应用/旧模式隔离检查通过。
- `java-verification.log`：59 秒 `BUILD SUCCESSFUL`；7 类共 62 项 JUnit 全过（轨道快照9、恢复策略5、诊断10、原盘策略16、OSD9、初始选轨3、DTS-HD门控10），Mobile arm64 与 Leanback armv7 编译通过。后续为防止故障日志误判元数据、保留跨文件音频设备/缓存选项新增/调整了定向测试，仅重跑受影响两类。
- `mpv-armv7.log`：构建/链接/安装至 prefix 成功。`mpv-arm64.log`：源码编译通过，链接错误来自 `scripts/mpv.sh` 对共享 `meson.build` 写入架构绝对 iconv 路径；不能并行运行两个 mpv 目标。只改执行方式，串行重配 arm64 记录到 `mpv-arm64-serial.log`，不重建 FFmpeg/libplacebo 或篡改依赖。
- native 新模式使用 BL 待配对1/预取1、EL 待配对8/预取4，并强制两路有界解码队列各自推进；默认仍保留旧队列。连续8次没有成功解出帧的错误终止对应 FEL 解码器，BL/EL 都适用；成功输出与 seek/reset 清零。日志分别记录真实配对/decoder queue 占用，明确不等同于驱动 DPB/Surface 池计数，不能把该假设伪装成已实证根因。
- 恢复页使用独立 `:playback_recovery` 进程，App/Startup 不启动播放器、网络服务和设备发现。主进程 watchdog 不依赖调试开关，正常路径无恢复文件写入；持续8秒无主线程心跳且应用前台时尝试拉起，后台/锁屏受保护，Android 拒绝拉起不会触发自动杀进程。UID/PID/start-ticks/私有请求令牌和仍未恢复状态均需重验，用户操作在恢复页后台线程执行；请求与用户操作结果分文件、跨进程文件锁保护，页面读锁非阻塞。不能保证挽救系统/GPU/磁盘整体失效。

### 9.4 日志31续修决定（2026-09-12 10:37，Asia/Shanghai）

- 输入：`/Users/macbookpro/Downloads/webhtv-debug-log (31).txt`，trace `p-w8ciwk-1`，10:18:42启动同一GIJoe P7 FEL文件、恢复位置14.121秒。已出现本单元新增队列/有限失败标记，因此不是仅用旧包结果猜测新代码；完整APK哈希仍不能从这份日志反推。
- 直接证据：10:18:50.468 `c2.mtk.hevc.decoder`成功；10:18:51.503第一对BL/EL均13.889且NLQ=1；10:18:52.153双层输入到GPU；10:18:54.038开始明确750ms MediaCodec端口饥饿；10:18:59.452第8次连续错误停止。最终paired=12、bl-only=0、last-pair=14.348，BL pending峰值1/结束0、wrapper结束0；AImage acquired/mapped=4、timeouts/stale/newer/errors均0。用户报告花屏。
- 已通过/未通过分开：有限失败确实进入App错误处理，没有上一日志49次错误循环；本次最终有正常页面退出记录。**不代表独立恢复页已测试，也不代表花屏或持续FEL已修复。**这次证据否定“仅BL配对队列过大就是全部原因”。
- 局部源码核对（Grade A，基于锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 加本地补丁）：`hwdec_aimagereader_vk_direct.c`的`FRAME_COUNT=3`、`map_image()/finish_frame()`保留3张AImage且仅在新图像到达复用槽位时归还；`player/video.c::get_req_frames()`通常还要求2张前视帧。GPU对codec图像的持有未被本轮pair/wrapper预算消除。该循环依赖与实际端口饥饿吻合，但仍缺驱动内部DPB计数，不能宣称唯一厂商根因已实证。
- 排除一条代码假设：`vo_gpu_next.c::hwdec_reconfig()`已经逐帧更新RPU与HDR元数据，不能无证据把花屏归咎于使用首帧RPU。FFmpeg输出互斥只覆盖release/flush/close，不包住整个750ms轮询，未发现“持同一锁轮询导致不能归还”的代码证据。
- 最短适配：仅选中FEL且Vulkan后端为auto/direct时，在当前播放实例内部采用已存在的stable GPU转换池；不写回用户设置，退出FEL后仍按原后端配置。保留BL硬解、EL软解和NLQ算法，使用RGB_IDENTITY/FULL的原始分量与至少10-bit暂存，禁止该路径降为8-bit。复制完成后按真实fence归还AImage，FEL额外使用有界等待，不能依赖下一张硬解输出才回收。新日志聚合记录提交/完成、source-held、pending、位深和fence超时；仍不得提前释放GPU正在使用的图像。
- 方案比较：不改已实播失败；继续只缩小pair队列已有反证；直接减到一个导入槽会影响当前/前视/重绘的合法纹理引用，不采用；既有GPU暂存池能把codec图像所有权与后续合成分离，无CPU回读或BL软解，是本轮窄适配。既有官方MediaCodec/AImage/fence契约与成熟本地池实现已在第8节核对，不重新搜索算法论文或升级依赖。
- 成本/回归边界：仅新FEL模式可能增加GPU拷贝及显存；4个4K packed-10bit输出约132.7MB是理论纹理上限，不是实测开销。普通DV5/P8.1、HDR10、Exo、音频/菜单与非FEL后端选择均保持原逻辑。性能与花屏是否消除必须由相同电视的后续对照验证，不用“GPU完成一次”冒充成功。
- 另补正式版恢复身份检查：AOSP Android 36.1本地`android/os/Process.java::getUidForPid()`读取`/proc/PID/status`的`Uid:`；非dumpable进程的`/proc/PID`目录属主不可靠。因此将原`Os.stat`改为严格解析真实UID，保留PID、启动ticks、进程名和私有令牌核验，新增4项正常/缺失/非法/重复字段测试，尚待运行。
- 范围与授权：沿用用户“实施最佳实践、后续不需要逐步确认”的授权及同一个`P2-4-fel-reliability` guard；所有生产native变更落在现有FEL补丁、受影响mpv资产和定向测试内。JNI、FFmpeg/libplacebo不再更改。旧native/Java验证只保留未触及部分，受影响检查须重跑。回滚仍为本单元基线`ec68966c1d72be1c27533e7e9450763a4189febe`的整套源码/资产。
- 10:30局域网日志恢复可访问（HTTP200），新快照`/private/tmp/webhtv-log31-current.json`显示用户在播放其他普通视频；不打断该会话、不远程改其设置。电视无ADB，新包安装及持续FEL/视觉/性能验收仍需真实播放证据。

### 9.3 日志31之前的本机候选（已被新实播失败证据否决）

上游后续修复的最终处置：

| 完整上游 commit | 本单元处置 |
| --- | --- |
| `3b4caf0f8ba3101c3aa0b2f59fbd13863e965cfc` | 窄适配：Android软件EL预热时，即使BL队列已满也等待EL，不再以满队列冒充缺层证据 |
| `228f3109fd1a620094758fea90dd387c64ec22c9` | 不照搬 extra_hw_frames：MediaCodec AD_HOC 不靠它扩池；改为显式约束 wrapper 实际预取，保留原硬件选择 |
| `b955aa28f3dc93dc6b21485a0d5b7feb8e6dc10f` | 窄适配：新路径 BL保留1/EL保留8；不把桌面队列8当成所有Android设备安全容量，旧模式保持不变 |

最终本机结果：

- `java-final-tests.log`：最后两类受影响测试共21项通过（快照10、诊断11，16秒）；结合前一轮未改动的其余5类43项，当前64个不同用例全部通过。最末的 Android 启动/恢复身份读取收紧由最终两产品编译覆盖，不冒充设备测试。
- `node-final-sanitizers.log`：最终 C++ NODE writer 的 host ASan/UBSan 边界用例与生产FEL补丁静态契约通过；代码也包含 OOM 拒绝保护，但没有注入实际 OOM。上述检查不是硬件缓冲吞吐实测。
- `mpv-arm64-serial.log`、`mpv-armv7.log`、`jni-build-final.log`：两 ABI 实际编译/链接成功。JNI 首次因项目只有 ALOGE/ALOGV 宏、没有 ALOGW 而编译失败；改用已有 ALOGE 后两 ABI 通过，不是跳过错误。
- `native-stage.log`、`native-assets-final.log`、`binary-provenance.log`：ELF/SONAME/DT_NEEDED、锁定版本、原有 Surface/音频/菜单等标记与新FEL/NODE标记通过；C/libmpv与Java JNI公开导出未变。恰好4个目标二进制更改，其他16个逐字节相同。两 ABI native 总增量分别5,496B和5,520B；不能将它当成CPU、画质或APK压缩体积收益。
- `apk-final-build.log`：手机arm64和电视armv7 debug APK均 `BUILD SUCCESSFUL`（2分36秒）。`apk-verification.log`：每包10个MPV资产与候选源完全匹配；编译后 Manifest 的恢复页明确为 `exported=false`、独立进程/独立taskAffinity、singleTask并排除最近任务。APK包名 `com.fongmi.android.tv`、versionCode 560、versionName 5.6.0、targetSdk 28，不能只靠版本名辨别本候选，应核对哈希或新日志标记。
- ADB无设备；`http://192.168.1.5:9978/debug/stream?v=0` 默认/沙箱外两次均8秒0字节超时。没有安装候选，没有观测本候选的电视播放、退出或恢复页；保留这些验收门槛，**不能宣称已根治驱动问题、真实FEL画质已通过或性能无回归**。

| 最终产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| arm64 `libmpv.so` | 17763288 | `f3b938d62886673d5aaa2da98aa79af6621301673a4e746b1d5c875c7ddaf483` |
| arm64 `libplayer.so` | 96160 | `89cea66bf8b77b1081d602cdea3cedcec9ee9c34b4c03827d88ab000fd739689` |
| armv7 `libmpv.so` | 14569092 | `f97da0cfab135e0fc2af8794354b17df8e51b9c404e5afe9dd28df95caed8e30` |
| armv7 `libplayer.so` | 62172 | `72c3e113f9bff7e92a68793a3ea3e0982d8caa31dab40065a1ecb7480ebc3549` |
| `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk` | 144857928 | `483a967236c48fee4edcb4d69f5bfd5ed8e10ed360eeaf82717ad3ef863c6b0e` |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | 180180453 | `a50ebc5206c3433cadd99b30918e3ca72b976a1014ba60d6cdd9b3ec73f6e7de` |

电视检查顺序：原文件0秒起播 → 恢复点起播 → 两次均播至EOF（该样片总长57.140秒，不要求单次60秒），核对 `WebHTV FEL stats` 的 paired/bl-only/队列峰值、`WebHTV FEL decoder queue`、GPU NLQ 与错误记录；可播后做重复seek、暂停、退出和重开，再回归FEL关闭、DV5/P8.1、HDR10和一次原盘菜单。正常运行日志不等同于FEL残差画质验证；画质仍需第8节的参考帧/差异样片比较。若发生 native 永久等待，核对恢复页能操作、等待后回到播放器、用户确认退出只处理原目标进程且不自动重播。

回滚单位为本节对应的原子实现提交：反向恢复 Java/JNI/Manifest、FEL补丁、验证说明与4个二进制；不改变依赖锁，不回退前一独立日志门控修复，不包含原 `app/.cxx/`。候选只本地提交和恢复tag，不推送、不作为已验收版本发布。

### 9.5 日志32：GPU暂存已生效，连续播放仍失败

- 输入：`/Users/macbookpro/Downloads/webhtv-debug-log (32).txt`，trace `p-wak4ac-1`；截图`IMAGE 2026-09-12 11:22:43.jpg`显示正常色彩的一帧与“音视频解码失败”提示。设备TCL/MT9655、Android14、Mali-G57、32位，文件仍是57.140秒的GIJoe FEL样片，恢复目标16.082秒。用户观察为“短暂播放后报错，没有花屏”。
- 11:20:43.907确认`WebHTV FEL GPU staging`，43.929 BL的`c2.mtk.hevc.decoder`启动成功；45.246首对BL/EL均15.891且NLQ=1；46.056 matched EL上传。不能把初始直接输出尝试寻找原生DV7 codec失败，混同于随后成功的HEVC BL硬解。
- 53.139第8次连续错误触发FEL fatal、App错误4003；53.148配对统计paired=11、bl-only=0、stale-EL=0、last-pair=16.308、BL-held=0/peak=1、EL-held=8/peak=8。渲染最终仅4次请求/4次AImage成功导入。
- 53.212 GPU池submitted=4、completed=4、source-held=0、pending=0、output-depth=10、fence-timeouts=0；累计拷贝等待98,947µs、最大37,696µs。AImageReader timeouts/stale/newer/errors均0。**该证据反驳“GPU仍持有AImage就是本次停滞完整原因”；继续查未送达渲染端的帧/MediaCodec输出及调度，不能靠继续缩队列或延长750ms来假装修复。**
- 第9.4节代码验证保留：`/private/tmp/webhtv-p24-reliability.ASCawt/recovery-uid-tests.log`（9项）、`fel-staging-host.log`、`fel-staging-arm64.log`、`fel-staging-armv7.log`、`fel-staging-native-verification.log`、`fel-staging-apk-provenance.log`、`fel-staging-compact-apk-verification.log`。增量APK曾有ZIP空隙，移走本任务产物后仅重新package，逐库及v2签名核验通过，不重复编译未改源。
- 用户反馈对应的本机候选：电视APK SHA256 `21e774a4def6aac2443f6456f29cb57e86bab0b7f80669c2edca67cdc221e9f4`，130,440,519B；手机APK SHA256 `f0a8cf90385e1523c638d1abb300d67ab213b6a1c59409d5e8dc613530d2ae18`，151,571,972B。日志只证明新路径生效，不能单凭标记反推整包哈希。**不得再次将这些已失败包当成修复完成包交付。**
- 11:28续修估计：定位10–15分钟、受影响修正/构建15–25分钟，目标12:10前给出结果；原guard/范围不变，保护`app/.cxx/`。设备实播是剩余门槛，不能以本机编译替代。

#### 日志32的剩余循环等待：只读状态查询重新锁住解码线程

- Grade A、本地源码复核（2026-09-12）：`MpvPlayer.observeProperties()`订阅`hwdec-current`；`player/command.c::mp_property_hwdec_current()`调用`mp_decoder_wrapper_control(VDCTRL_GET_HWDEC)`；后者无条件`thread_lock()`，等到解码线程当前工作完成。该函数自己的注释明确说正常播放应避免这类等待。BL线程可能正等MediaCodec端口可用，端口又需要播放核心继续向VO送帧/释放缓冲。异步Java订阅不能切断这个native内部环路。
- Grade A/B、已读实际上游提交`f2ef360444b733f45044048bf9cb930e0ef31a81`（`f_decoder_wrapper: allow VDCTRL_GET_HWDEC to actually fail`）：为保留硬解探测阶段的`CONTROL_FALSE`，把原本缓存特判移入了同步decoder锁。**不直接回退该提交**，否则会恢复“未确定硬解方式就报告no”的旧错误。提交提及的`a3823ce0e0353fa4ae4b75b0ff2cc17e61969005`不在当前浅缓存中，未读取，不将其当已审核来源。
- 本次处置：对`f2ef360444b733f45044048bf9cb930e0ef31a81`作FEL专用窄适配，保留探测可用状态，缓存拥有自己的名称副本，getter复制到调用方线程专用存储再返回，不能跨线程借用decoder内部字符串；在FEL帧发布前及时更新缓存。只读`container_fps`为线程启动前已固定的值，同模式读取也不等待decoder。seek/reinit/fallback/销毁仍保留原同步规则。
- 比较：不改/沿用上游同步查询都保留核心等decoder的环路；仅关掉App状态订阅会损失诊断且挡不住其他调用；选取FEL限定的有界状态快照，普通模式不改变，状态未知时仍返回不可用。新增内存为两个小快照，无逐帧堆分配、不改变BL/EL/NLQ、画质或队列容量。发布序号和查询日志进入App调试日志。
- 验证/回滚：用host harness直接编译生产`update_cached_values`、`mp_decoder_wrapper_control`和fps getter，先使旧实现因“只读查询调用decoder锁”失败，再验修正路径无需该锁、保留探测unknown/software/hardware状态和字符串所有权、普通模式与修改型控制仍走同步分支；随后仅重建两ABI mpv和APK。最终仍须同一电视从0及恢复点播至EOF，不能把这一源码环路证据夸大为所有厂商问题均已排除。回滚仍以本可靠性单元整套源码/资产为单位。
- 11:48–11:50本机回归：`fel-status-before-fix.log`在旧生产函数上确定性失败`read-only FEL query waits for the decoder`；修正后`fel-status-host.log`的NODE/FEL helper和真实wrapper函数ASan/UBSan均通过，无等待、unknown/硬解/软解区分、名称副本/超长拒绝、普通路径与修改型同步控制通过。随后production patch反向检查因reset hunk遗漏既有`first_packet_pdts`上下文失败，已只修patch上下文；不把它当运行时回归，也不重跑未改且已通过的host二进制测试，单独重验补丁契约。

#### 日志32续修候选的本机验证与交付

证据仍保存在`/private/tmp/webhtv-p24-reliability.ASCawt/`：

- `fel-status-patch-contract.log`：上下文修正后，生产补丁反向应用与旧模式隔离/构建可达性检查通过。没有再次执行已通过且未改变的host测试。
- `fel-status-arm64.log`、`fel-status-armv7.log`、`fel-status-native-stage.log`：两ABI mpv串行实际编译、链接、安装成功。JNI、FFmpeg、libplacebo未重编，之前的Vulkan局部变量shadow警告未改动。
- `fel-status-native-verification.log`：两ABI ELF/SONAME/DT_NEEDED、锁定版本、原有功能标记与新snapshot标记通过；该校验本轮约6分26秒，是超过原预计12:10的主要额外耗时，不因此减少验收。
- `fel-status-apk-build.log`：电视armv7和手机arm64 Debug均构建成功（35秒）。为避免增量ZIP空隙，仅将本任务上一轮已失败APK移动保留为`log32-failed-tv.apk`和`log32-failed-mobile.apk`后打包；保护路径`app/.cxx/`继续使用原隔离CMake目录避开。
- `fel-status-provenance.log`：相对guard基线只有4个既定目标库改变，其他16个逐字节相同；本轮两JNI与此前已验证版本哈希一致；两ABI libmpv公开接口不变；每个APK的10个MPV资产均与校验源一致，包体没有异常增长。
- `fel-status-tv-signature.log`、`fel-status-mobile-signature.log`：APK签名验证通过（v2=true）。Java未再改，保留之前68项不同JUnit通过记录，不重复执行。
- `fel-status-tv-current.json`：LAN HTTP读取成功（约5.9MB日志），尚无`WebHTV FEL status snapshot`，故目前没有本候选的电视实播证据；未操作用户会话、未安装新包、不声称播放验收完成。新电视包已向用户交付本地路径。

| 最新候选产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14570788 | `bc402066dba9c413d5d53825cbfd296a0d890be2834c4e3de507efe4e5fbff63` |
| arm64 `libmpv.so` | 17765112 | `6c542aa6500a93ed521c442757da4bb49be148ea4b24690677bdf871830da997` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130440519 | `80cb16e9dd4f214efb119bc0798ba1141ba549e4f09ef8679ae477dff4b8a361` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151571972 | `c302e91d4e2accdde11b6623dee51bfc7747efc374d7c9e163d7da63f3d811bc` |

本轮输入SHA256：`mpv-android-fel.patch`=`1d21ff3fc0c10350aa6e5beab0c1a1fcc8b0aeb1ae467de9f6eac535c3ef890f`；`test_mpv_fel_contract.sh`=`553d3fc64620f491fb4112d91ab8eb358f86eb8c5673d62c63cda0fb66b6a40b`；`verify_mpv_native_assets.sh`=`a4ebb72faf4a621f04c3094ce003b1cb18067bd813dc4c1a3d0e5ed79f8abdc9`。两JNI哈希仍分别为`89cea66bf8b77b1081d602cdea3cedcec9ee9c34b4c03827d88ab000fd739689`（arm64）和`72c3e113f9bff7e92a68793a3ea3e0982d8caa31dab40065a1ecb7480ebc3549`（armv7）。

本轮没有commit/tag/push：实际电视连续FEL、画质/性能和恢复生命周期仍是未通过门槛；不能将有针对性的源码修正与本机通过记录扩张为整项需求已经完成。

### 9.6 日志33：新路径生效，但尚缺停帧位置证据

- 输入：`/Users/macbookpro/Downloads/webhtv-debug-log (33).txt`，trace `p-wd4cpb-1`，GIJoe 57.140秒样片，恢复点17.947秒。
- 12:32:27.219命中FEL GPU staging；27.265普通HEVC硬解`c2.mtk.hevc.decoder`启动；27.331/27.335两次命中`WebHTV FEL status snapshot ... (no decoder wait)`。因此不是装错旧包，也不能再将GET_HWDEC同步等待当作完整根因。
- 28.447首对BL/EL=17.893、NLQ=1；最后配对18.143，共7对，BL-only/stale-EL均0。BL pending峰值1、EL pending峰值8。
- 36.194连续8次解码错误触发有界停止/4003。结束时GPU submitted=completed=4、source-held=pending=0、10-bit、fence-timeouts=0、总copy-wait=85377us/最大22275us。AImage acquired=mapped=4、其余错误/错配/超时计数为0。不能把GPU暂存完成等同于最终渲染/显示提交完成。
- 已核查锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`及本地补丁的调用链：`player/video.c::write_video()/video_output_image()`、`filters/f_async_queue.c::process_in()/process_out()`、`filters/filter.c::filter_recursive()`、`vo_gpu_next.c::draw_frame()/flip_page()`、`vd_lavc.c::send_packet()/decode_frame()`。`VDCTRL_CHECK_FORCED_EOF`仅在EOF后调用，不能解释首次端口超时；内部filter调度不递归运行另一decoder，未发现可据此直接宣称根因的锁。现有FFmpeg输出释放锁没有包住端口等待循环，不改其保护补丁。
- 决策：不改/沿用上游日志都只能知道端口超时；再调队列、GPU池或750ms没有新的决定性证据。选取FEL实例内的有界原子进度快照，分别记录core/BL/EL/VO阶段、阶段起点和收发帧计数；在原fatal消息内附加快照，复用现有fatal限流豁免与即时App日志。无需Java主线程/native属性查询、无等待/分配/逐帧日志，无新增线程或依赖，不改变配对/解码/重建/超时行为。
- 依据：上述生产调用链与日志为Grade A；第8节Android ANR/API异步契约支持旁路非阻塞诊断。该步不改变算法或采用新上游方案，既有资料评审继续有效；不能凭本地测试把诊断包称为已修复包。
- 验收：真实诊断helper的禁用隔离、计数、时间回绕、缓冲截断与并发读写测试；两ABI编译/ELF/既有标记及新pipeline标记；APK包内资产一致。电视失败时应在同一条fatal日志读到四条lane及VO请求帧预算，用它在“核心等未来帧/decoder被阻塞/渲染或提交被阻塞”之间作出可证伪判断，之后才实施对应修正。回滚仍为本可靠性单元整套源码/资产。
- 执行目标：2026-09-12约12:41 Asia/Shanghai起，诊断/定向修改20–30分钟、双ABI构建/校验15–20分钟，目标13:30前交付可识别阻塞点的新候选；设备复现不计作已通过。
- 13:09已实现：每个VO独立持有4条lane；阶段/时间戳使用同一个64位原子值，编译期要求ARMv7/arm64均为always-lock-free；其他计数仅为各自原子的近似观察，不伪装成全管线一致事务。记录无日志I/O、无内存分配、无锁/重试；只有现有fatal发生时格式化一次，沿用不可限流的fatal通路。VO预算记录request/refs；stage覆盖取过滤帧、等VO、GPU预加载/合成/flush/submit/swap-buffers，既有行为参数不变。
- 本机`fel-trace-host.log`：真实诊断header的禁用隔离、有效/无效PTS、时间回绕、极小缓冲、4线程并发及一致stage/time测试通过ASan/UBSan；原NODE/FEL和真实decoder-control用例通过；补丁反向契约通过。同步构建缓存前修正了新增diff的空行/漏失上下文，失败时均恢复原补丁；没有丢弃其他native补丁或受保护文件。

#### 日志33定位包结果（13:19）

证据目录沿用`/private/tmp/webhtv-p24-reliability.ASCawt/`：

- `fel-trace-tsan.log`：同一个生产header测试额外通过ThreadSanitizer，并发读写无报告。
- `fel-trace-arm64.log` / `fel-trace-armv7.log` / `fel-trace-native-stage.log`：两ABI实际编译/链接和安装通过；32位同样通过always-lock-free静态断言。没有升级、重编FFmpeg/libplacebo/JNI。
- `fel-trace-native-verification.log`：两ABI ELF/SONAME/DT_NEEDED、锁定版本、旧功能标记与新pipeline标记通过（本轮约46秒）。
- `fel-trace-apk-build.log`：两Debug变体构建成功，57秒；继续使用隔离CMake目录，未用`app/.cxx/`。
- `fel-trace-provenance.log`：相对guard基线只改变4个既定库，其余16个字节一致；本轮JNI与上一候选完全相同。两libmpv公开接口一致，两APK各10个MPV资产逐项匹配。
- `fel-trace-signature-arm64-v8a.log` / `fel-trace-signature-armeabi-v7a.log`：两APK签名通过。失败的上一候选保存在`log33-failed-tv.apk` / `log33-failed-mobile.apk`，未删除。

| 当前定位包/产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14577356 | `b05166fd22273768fd2e1c4107590f6d1d9518c5a72b27c8cdd4990470b246da` |
| arm64 `libmpv.so` | 17770856 | `337bc3acc92afe62d85e99df7928d08781081fbb3d53321257613f7a65fdf540` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130440519 | `603e40c221c00537bd80c801009795ba2d1383510b1659f76bd27cc05034c6b3` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151571972 | `f307cd4fe53e009ee14ceecb99f79e9645cff7acd17f5bd7f28b4d6f511891e7` |

本轮输入SHA256：`mpv-android-fel.patch`=`17958856c0aa1506499f1439e701d8fd3bd64231de436985f51fb6890f238a75`；`test_mpv_fel_contract.sh`=`d1a3a34e1a91ae7f18a1ff9a2c74539f0d00905615bf7d8638491bb5b8bbb89c`；`verify_mpv_native_assets.sh`=`839651e8d817115285cdef4a00eac6eac465061bae110a1ac8432c5173f77966`。语义不变的patch空白格式化在打包前完成，最终安全检查另行核对可逆应用契约，不重复host执行。

状态：定位包待电视复播；真实FEL播放失败的门槛仍未关闭，未提交/tag/push。这里的`VO out`是完成显示提交调用的次数，不等同于屏幕实际呈现帧；其他lane计数也仅作诊断，不参与调度。不得将构建/并发测试通过当作电视播放已修复。

### 9.7 日志35：VO丢帧绕过GPU暂存形成的等待链

- 新证据：`/Users/macbookpro/Downloads/webhtv-debug-log (35).txt`，trace=`p-wexl39-1`。13:23:09.464命中新pipeline；09.517普通HEVC `c2.mtk.hevc.decoder`启动。19.570在8次连续错误后停止（4003）。快照为`requested=2 refs=3 core={core-wait-frame age=22ms pts=19144 in=8 out=4 held=1} BL={codec-receive age=776ms pts=19478 in=14 out=8 held=0} EL={codec-decoded age=5589ms pts=19686 in=27 out=20 held=1} VO={vo-idle age=5989ms pts=19102 in=5 out=5 held=2}`。
- 配对8帧、BL-only=0，最后配对19.186；GPU submitted/completed=4/4、source-held/pending=0、输出10bit、fence-timeouts=0；AImage acquired/mapped=4/4，无超时/错配。排除当时EL生产不足或VO正在GPU提交中阻塞。core输入含恢复点前的preroll，不能把`in-out`都解释为丢失帧。
- 源码核实（锁定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`及既有本地补丁，2026-09-12，Grade A）：`player/video.c::get_req_frames()`需要2帧；`vo_gpu_next.c::preload_deferred_hwdec_frame()`已为正常提交的未来帧完成暂存，故单纯“VO惰性上传未来帧”不是准确解释。真正缺口在`vo.c::render_frame()`：迟到帧分支跳过整个`draw_frame()`，却保留`current_frame`及其中未来帧；这也跳过GPU暂存/AImage归还。核心已送出19.144而VO最后处理19.102，与此路径一致。待用实际函数测试确认等待链，并以新日志确认电视触发频率；尚不宣称唯一厂商根因已实证。
- 依据与范围：沿用第8节Android MediaCodec缓冲所有权、mpv #18375预算、成熟GPU路径与技术资料评审，不重复广泛搜索；这里修复的是本地既有暂存接线遗漏，不替换算法、FFmpeg、Surface/fence或上游版本。论文不适用于决定这一具体控制流缺口。`DOCS/man/options.rst::video-latency-hacks`明确警告损害插帧/帧率判断，故不采用全局或隐式低延迟切换。
- 方案比较：不改/原样上游继续保留有限Surface输出；降低核心需求到1帧会影响帧时长和用户插帧；禁止所有FEL丢帧会额外合成和显示迟到画面。选择窄适配：仅显式FEL、MediaCodec、支持的gpu-next路径，在VO丢弃显示前通过内部control完成所持帧GPU暂存，归还硬解图像，仍保留原丢帧计数/显示策略/双帧时序和用户设置。正常画面不增加GPU拷贝，迟到画面只执行原本被绕过的必要暂存，不执行完整合成/显示。
- 预定保护：映射必须在VO锁外完成；临时未就绪保留原帧并请求重绘，永久错误仍走既有backend-error。不新增线程、公开API、依赖或JNI，不增大队列/超时，不牺牲BL/EL配对、10bit或NLQ。补充fatal同一物理行及丢帧暂存计数，避免pipeline第二行被限流。
- 验收/回滚：以实际`render_frame()`和新增暂存函数编译有限输出池回归（旧路径失败、新路径前进），覆盖FEL关闭、软件帧、正常显示、丢帧、暂时/永久错误及锁外执行；再做两ABI、受影响资产/APK验证。电视仍须连续播放、seek/退出及画质门槛；无设备通过证据不提交/tag。回滚锚点保持本guard基线整套源码/资产。13:33起预计分析/修正20–30分钟、构建/验证10–20分钟，执行目标14:20 Asia/Shanghai。
- 13:48本机证据：`fel-vo-drop-before.log`在旧真实scheduler上失败`codec_outputs == 0`，保留未来帧占用输出导致下一BL无法解码；`fel-vo-drop-host.log`通过实际scheduler和新暂存函数的ASan/UBSan回归，以及既有NODE/FEL、wrapper状态快照、并发进度header及可逆patch契约。测试模型只模拟DPB之外剩一个可用输出，不声称测得电视精确池容量。新实现仍计一次VO丢帧，不合成/显示，只暂存缺少的未来帧；FEL关闭/软件/其他VO/正常显示/暂停/GL均隔离，临时等待重绘与永久错误、锁外映射均通过。源码缓存已从保存的旧FEL patch可逆更新，没有替换其他native补丁。

#### 日志35修正候选：构建与最终交付

证据目录仍为`/private/tmp/webhtv-p24-reliability.ASCawt/`：

- `fel-vo-drop-arm64.log`、`fel-vo-drop-armv7.log`、`fel-vo-drop-stage.log`：两ABI真实编译/链接与stage成功；没有重编JNI/FFmpeg/libplacebo。原有stable局部变量shadow及armv7时间范围warning不属本次新增，不扩大修复范围。
- `fel-vo-drop-native-verification.log`：两ABI ELF/SONAME/DT_NEEDED、锁定版本、既有功能及新丢帧暂存标记通过。耗时约3分25秒。
- `fel-vo-drop-apk-build.log`：两Debug构建成功（39秒）。增量APK含旧libmpv占位；移至`fel-vo-drop-gap-tv.apk`/`fel-vo-drop-gap-mobile.apk`保留可恢复，再仅重新package，见`fel-vo-drop-compact-apk-build.log`（28秒）。继续隔离CMake目录，未使用受保护`app/.cxx/`。
- `fel-vo-drop-provenance.log`：两libmpv公开接口不变、两JNI与前包字节一致；相对guard仅4个目标库变化，其余16个依赖一致。
- `fel-vo-drop-compact-verification.log`：重新封装后的最终两APK各10个MPV资产逐项匹配，v2签名通过。只重验改变了封装的APK，没有重复native/Java测试。
- 16:04只读LAN取证：`/debug/logs`为HTML页面，实际JSON在`/debug/stream?v=0`。保存`fel-vo-drop-tv-stream.json`，最后记录13:39:56，FEL部分仍为日志35的`p-wexl39-1`；无新`dropped-frame staging`证据。未远程安装或操作用户播放。
- 最终作用域/检查点输出保存于`fel-vo-drop-final-safety.log`。原14:20交付目标未达到；重新打包本身已成功，执行会话随后失效，16:00恢复时收尾仍未完成。本轮停止额外研究，只核验最终产物和交付，不以耗时作为降低实播验收的理由。

| 最终产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14578788 | `8acedebbbe149f0ce3fffaf45bb7dca80126753f270d343a0e937d6452483a21` |
| arm64 `libmpv.so` | 17772120 | `9c8637ae82dfabd8779ba67c51fe2220b2408b12421caf0607b2afd6209a9986` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130440519 | `3baae67b07ba7c93f71ae4f5a70bb865dcfd53508a8798b26455632524efac12` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151571972 | `a58eb183a57d8a0daf28d82fa514d01f1b5cba3ef87f3f01c70e3cb6ce9af437` |

输入SHA256：FEL patch=`1fb5dff9741fa96621b49fd957e73f360ce69a2e5ab98d0c4ebdc3a4722f4815`；host测试脚本=`167b666d64163be77d96e2c87314f2fd5377bd1c8cda354ef73f6cd6c7e58225`；native资产校验脚本=`eee37fe9bd10f0ffaa54036edf89b9074bd319a7fbd77f3003e51bda4be3a0ed`；未改的native lock=`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。完整可靠性单元的电视播放门槛未关闭，未commit/tag/push。

### 9.8 日志36：丢帧暂存已成功，MediaCodec仍停滞

- 新证据：`/Users/macbookpro/Downloads/webhtv-debug-log (36).txt`，trace=`p-wlewnw-1`，恢复点约19.810秒、首对BL/EL为18.894秒（含preroll）。16:24:46.140在8次连续错误后4003停止，新单物理行fatal/pipeline证明修正版正在运行。
- 关键快照：`requested=2 refs=3 surface-drops=3 drop-prepared=3 drop-retries=0 core={core-wait-frame age=10ms pts=20061 in=30 out=7 held=1} BL={codec-receive age=767ms pts=20270 in=36 out=30 held=0} EL={codec-decoded age=5994ms pts=20604 in=49 out=42 held=1} VO={vo-drop-done age=5982ms pts=20061 in=6 out=6 held=2}`。
- 配对30帧、BL-only=0、stale-EL=0，最后配对20.103；BL-held=0 peak=1，EL-held=8 peak=8。GPU submitted/completed=8/8、source-held/pending=0、output-depth=10、fence-timeouts=0；copy-wait-us=163423、max-copy-wait-us=21617。AImage acquired/mapped=8/8、timeout/stale/newer/error全0，render requests=8、unavailable/pending replacements=0。
- 结论边界：已执行的GPU/AImage操作未残留资源等待，VO也非提交中阻塞；上版修的是实际控制流缺口，但不是完整电视根因。保留它的已通过定向测试，不重复未改构建，不延长750ms保护、不盲目降低核心需求或禁用插帧。不能用含preroll的配对数声称持续播放改善。
- 待证问题：`dovi_split`的`el_rpu`只生成EL副本时，硬解BL是否仍收到原始交织BL+EL？需查实际dispatcher/decoder与FFmpeg metadata处理，不以函数名或摘要替代证据；任何净化都必须保留真实EL/RPU/NLQ并隔离到显式FEL路径。
- 16:37恢复：同一guard、同一作用域与回滚锚点，既有Java/JNI及其他未变更验证保留。定位/修正约20分钟、受影响构建核验15–20分钟，目标17:15–17:20 Asia/Shanghai；真实电视验收未过前不提交/tag。
- 源码核实（2026-09-12，Grade A）：锁定mpv的`demux/dovi_split.c::mp_dovi_split_dispatch()`仅返回`el_rpu`副本；`demux_lavf.c`与`demux_mkv.c`仍将原包作为BL输出。`vd_lavc.c::send_packet()`直接送出该包；锁定FFmpeg的`mediacodec_extract_hevc_metadata(const AVPacket *)`只读取RPU，未改包。故“BL硬解仍含EL NAL”是已证实输入隔离缺口，是否独立导致该厂商停滞仍待实播。
- 决策证据：FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`的`libavcodec/bsf/dovi_split.c::nal_is_kept()`提供`bl_rpu`模式，去除type63外包EL、逐字节保留type62 RPU与BL，并支持Annex-B和1–4字节NAL长度。`bsf.h`明确send接收packet所有权；mpv `common/av_common.c::mp_set_av_packet()`却借用demux引用，必须另建ref，禁止原地交给BSF。沿用第3/8节PR、平台和成熟实现的研究；此修正不改变重建算法，论文和重复field搜索不能替代具体码流/所有权测试。
- 方案比较：不改/原样桌面路径依赖硬解忽略EL；在demux全局过滤会改变原生DV及其他decoder消费者；自行写NAL解析增加格式/畸形输入风险。选择在显式Android FEL的MediaCodec BL入口复用已有`dovi_split=bl_rpu`，不升级或重编FFmpeg、不改变共享demux数据、codec/RPU配置、EL、NLQ和GPU逻辑。普通播放、旧DV7模式、软件EL不创建BSF、无逐包扫描成本。
- 最小实施/验收：用独立owned packet承接过滤结果，EAGAIN重试不消费原包，纯EL包作为已消费空输出而不是无限重试，seek/reset清BSF，销毁归还引用；启动/首包/退出或fatal统计进入现有App调试日志。定向ASan/UBSan测试真实helper和公共BSF，覆盖BL/RPU字节相等、EL独立副本不变、时间戳/side-data/原包所有权、Annex-B/长度前缀、空/坏包与重试/复位、非FEL隔离；通过后才重建两ABI/mpv及APK。双ABI来源/资产/公开接口仍需一致；回滚继续使用本guard整套源码/资产锚点。用户持续实施授权适用，未扩至Exo/音频/菜单或新依赖。
- 实施：新helper只在`android_fel && !force_swdec && MediaCodec && source_profile==7 && HEVC`分配BSF；不将`par_out`写回共享codec/RPU配置。坏输入失败关闭，不把原始混合包送硬解；空输出消费一次；硬解EAGAIN下原始demux包仍可重试。`BL-input={isolated=... packets=... bytes=...->... empty=...}`与fatal同一物理行，避免限流丢掉关键新诊断；启动、首包、退出另有低频信息。
- 本机验证：`fel-bl-isolation-host.log`的原有NODE、decoder-control、VO-drop和并发trace测试通过；新增packet测试首轮发现Annex-B四字节起始码的首零可由FFmpeg保留为前一NAL的合法`trailing_zero_8bits`，测试错误要求长度完全相等。按`h2645_parse.c::ff_h2645_extract_rbsp()`实际行为修正测试，只允许新增尾零、仍严格检查每个有效载荷字节，不改生产数据或降低精度。
- `fel-bl-isolation-packet.log`：新增真实helper + 系统FFmpeg 9.0.1（libavcodec 63.1.101）ASan/UBSan通过，覆盖Annex-B和长度1–4、32组准入组合、H264排除、借用/无ref包、重试/复位/空/畸形输入。锁定Android FFmpeg为63.3.100，源代码已对照，host结果不冒充Android二进制实播。GIJoe本地样片前120个视频包移除296个EL NAL，41149779→37620884字节；BL/RPU每个载荷与PTS/DTS/duration/side-data保持一致。`fel-bl-isolation-patch.log`的可逆补丁/旧模式契约通过。ADB无设备；未重复未变更的Java测试，尚未构建/实播新产物。

#### 日志36续修候选：构建与交付证据

证据仍在`/private/tmp/webhtv-p24-reliability.ASCawt/`：

- `fel-bl-isolation-arm64.log`、`fel-bl-isolation-armv7.log`：两ABI真实增量编译`vd_lavc.c`/新header并链接安装成功，未重编JNI或其他依赖。`fel-bl-isolation-stage.log`与`fel-bl-isolation-native-verification.log`通过ELF/SONAME/DT_NEEDED/锁版本/旧功能及新BL隔离标记。
- `fel-bl-isolation-apk-build.log`记录首轮被沙箱拒绝用户Gradle缓存锁，属于环境权限，不是代码失败；提升权限后`fel-bl-isolation-apk-build-approved.log`成功，耗时1分10秒。继续使用既有isolated-cxx脚本，原`app/.cxx/`受保护。打包前将两旧APK移至`fel-log36-tv.apk`/`fel-log36-mobile.apk`保留可恢复，避免增量封装残留旧库占位，本轮只执行一次成功构建。
- `fel-bl-isolation-apk-verification.log`：两APK各10个MPV资产与源码assets逐项SHA256一致、v2签名通过；相对旧APK各自仅`libmpv.so`变化，JNI及其他18份库完全相同；两ABI公开导出集合一致。native增加约1.1/1.2KB，最终APK字节数因对齐未变，但APK/MPV SHA256均已变化，不能用大小判断是否更新。
- `git diff --check`首轮发现机械生成patch的空context行含空格；仅规范化patch空行后再次通过，`fel-bl-isolation-final-patch.log`的可逆源码一致性/旧模式契约也通过，无生产源码变化，不重复native构建。两个变更shell脚本的`bash -n`通过。
- 本地GIJoe样片`/Users/macbookpro/Downloads/影音测试库/V01_DV_Profile/P7_FEL_4K24_GIJoe.mkv` SHA256=`06e42fc4e06ee90c8eea0b7a31450f844ad4228ea639a5ba12d51c05d2930e63`；不能据此宣称已比较电视文件哈希或测得目标设备吞吐。

| 最终产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14579892 | `38694f30e4e397c37aa77d03d5d6462baa391cf2379748510056ecbaee5eecab` |
| arm64 `libmpv.so` | 17773320 | `6e130c534f6efaf5d6f6b75a27f10362a4f637443f8b9fa52f50365a601517fc` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130440519 | `f5dfd020371dc5e6114e2fd5eb86fde057afd2723b2a74b844dc17c65261238b` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151571972 | `1521ff7dab7d1dcf706167e9c5b497baba12c5b66e88d43828f77c41bf7242fc` |

输入SHA256：FEL patch=`3a0e06cbde365b56954d76a4ad7267c284844c8271921b1fb1957c71547dbd7f`；host脚本=`f803903a1f83e1ed9085a1daf94038b340a43df8750a1e158f1798f21eb1578d`；native验证脚本=`6467bbce2bef48e5a93238051a208975569d8629223e323829ddaac1afd8b8fc`；锁未改，仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。

17:09：本轮预计17:15–17:20的构建交付目标已达到；目标电视无法ADB，尚无新候选实播。保持同一个active guard，不finish/commit/tag/push；连续重建、画质、性能、seek/退出及恢复页门槛仍需实际证据。

### 9.9 日志37：核心前视帧未进入VO的缓冲等待链

- 证据：`/Users/macbookpro/Downloads/webhtv-debug-log (37).txt`，trace=`p-wngh5c-1`。17:21:47.614启动`bl_rpu`隔离；首包423759→313263字节，PTS20.896。17:21:55.809仍在8次连续错误后4003退出。`BL-input={isolated=1 packets=12 bytes=3250565->2855109 empty=0}`否定未安装新包/过滤未生效假设。
- fatal：`requested=2 refs=3 surface-drops=1 drop-prepared=1 drop-retries=0 core={core-wait-frame age=42ms pts=21063 in=6 out=4 held=1} BL={codec-receive age=781ms pts=21271 in=12 out=6 held=0} EL={codec-decoded age=5601ms pts=21605 in=25 out=18 held=1} VO={vo-idle age=750ms pts=21063 in=6 out=6 held=2}`。paired=6、last-pair21.104、BL-only/staleEL=0；GPU/AImage=5/5，source-held/pending/fence-timeout=0，10-bit，copy-wait123311us/max40220us。
- 本地代码（mpv锁定`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`与现有补丁，2026-09-12，Grade A）：`player/video.c::video_output_image()`将第1张未来帧放入`next_frames`，`get_req_frames()`要求2帧，`write_video()`在`VD_WAIT`时直接返回；只有攒够帧才`vo_queue_frame()`，之后才运行GPU暂存。因此至少这1张核心持有帧没有进入GPU统计，不能再将“GPU已归还5帧”解释为所有MediaCodec outputs已释放。日志37的6/5差值与此路径一致，精确厂商池容量仍未测得。
- 研究复用：第8.4节Android官方“某些codec需归还所有outstanding buffers”、mpv #18375持帧预算、Kodi及时release及Bigflake生产者背压资料仍适用；第9.7节只覆盖已进入VO后的丢帧，不覆盖本次core等待。此处不改FEL算法，新增论文/泛帖子搜索不能决定控制流问题。需要以实际core函数的有限输出池测试区分新路径与旧路径。
- 方案比较：不改/原样桌面路径保留上述等待环；强行降为1帧会改变帧时长推断与插帧；同步`vo_control()`让core等GPU会引入新的阻塞面。选择同一FEL暂存能力的异步core接线：core保留帧内容和两帧预算，在读取下一帧前请求VO线程暂存；只有有界单槽请求，完成通知core，seek/reset代数作废旧任务，锁外GPU工作，核心不等待dispatch/GPU。
- 准入/生命周期：仅显式FEL、MediaCodec帧及支持的软件EL VO；普通模式不建请求。逐帧完成标记防止两张前视帧重复轮询互相覆盖；含seek保存的帧也须在继续解码前处理。保留原PTS/RPU/EL与raw-YUV >=10bit、现有fence/750ms保护，不用CPU回读、不新增线程或公开JNI/API。检查提前暂存与后续裁剪/VO重配的缓存一致性，不能释放源后又丢掉暂存纹理。
- 验收：直接编译实际`video_output_image`/前视判断和新VO请求处理函数，旧路径在受限输出模型下不能凑够2帧，新路径可推进；覆盖前视/EOF/预滚动保存、非FEL/软件/其他VO、异步重试、超时、锁外执行、seek中途取消及释放。再重建两ABI和APK；电视仍须持续播放/seek/退出/画质与性能门槛。回滚保持同guard基线整套源码/资产。
- 17:25执行估计：定位与代码/测试30–40分钟，构建核验10–15分钟，目标18:15–18:20 Asia/Shanghai；不以估计降低验收，不复测日志37的失败包。
- 实施细化：异步请求仅占一个槽，core按20ms兜底轮询、VO完成主动唤醒，重试间隔5ms、750ms超时；不在core调用同步dispatch。`mp_image`内部的ref-counted暂存租约随图像引用复制，防止核心/插帧仍使用的纹理被环形池覆写；仅FEL池允许从原4槽按需增长，上限`2*VO_MAX_REQ_FRAMES+4`，不预分配额外4K纹理，普通模式保持4槽。FEL mapper比较仅剔除由gpu-next处理的显示裁剪/方向/比例，真实像素尺寸/格式/色彩仍严格比较；不得全局放宽参数判断。上述均在已有FEL补丁所有权内，不改锁/公开API/JNI。
- 18:06–18:20验证：`fel_core_preload_test.c`直接编译生产core前视/读取、VO请求/worker/cancel、GPU租约选择和mapper重配函数，受限输出模型下旧模式保持卡住（证明反例），候选40帧可推进；2/6/10帧请求不被缩减，软件/非FEL隔离、单槽、5ms重试、750ms超时、锁外GPU、reset先行/处理中取消、hrseek保存帧至EOF、重复租约和裁剪均通过ASan/UBSan。`fel_image_lease_test.c`直接编译生产ref/destructor/dummy/unref-data并链接真实AVBuffer，引用/EL引用/清理/租约唯一析构通过；修改后的原子进度测试通过。不是测得电视池容量或实时性能。
- 补丁溯源：在`/private/tmp/webhtv-fel-preload-patch.R5eptw/`按构建脚本的原始参数重放旧补丁链，旧FEL patch可应用，新patch与缓存可逆；前后源码只差第9.9节8个mpv文件，未带入其他模块。最初给所有旧补丁加`--recount`的生成命令在disc poll旧式patch失败，纠正为构建脚本逐条原始参数后通过；没有改原盘补丁。差异记录`/private/tmp/webhtv-p24-reliability.ASCawt/fel-core-preload-source-delta.log`。
- 首轮native日志`fel-core-preload-*-build.log`编译失败，原因是新增调用前未声明`wakeup_locked`；已补声明。外层裸`bash`没有继承buildall的shebang `-e`，不能信任其退出0；v2显式`bash -e`并检查完整日志的失败行。失败轮产物未暂存/打包。18:20预计延期10–15分钟至18:30–18:35，仅继续必要构建和产物核验。
- v2两ABI与两APK通过、各10资产/签名一致，和日志37相比仅两份`libmpv.so`变化。但19:11最终源码消费检查发现`map_frame`直接使用mapper的`par.rotate`，不能把规范化后的缓存参数当作呈现参数。v2未交付，保存为`fel-core-preload-v2-tv.apk`/`fel-core-preload-v2-mobile.apk`。新增`restore_fel_display_params`仅恢复每帧方向/翻转/比例，保留mapper真实格式/尺寸/纹理边界；实际helper纳入ASan/UBSan，验证90度旋转/翻转/非方形比例恢复且普通模式不受影响。core提前暂存失败改为统一FEL fatal日志，包含错误值、PTS及完整pipeline，满足无ADB诊断要求。此次必要边界修正导致超过前次估计；不省略重建最终产物验证。

#### 第9.9节最终候选（v3，未通过电视实播）

- 证据根目录：`/private/tmp/webhtv-p24-reliability.ASCawt/`。`fel-core-preload-test-v3.log`是最终实际函数ASan/UBSan；`fel-core-preload-*-build-v3.log`是两ABI成功编译；`fel-core-preload-native-assets-v3.log`是ELF/依赖；`fel-core-preload-apk-build-v3.log`是两APK构建（48秒、172 tasks）；`fel-core-preload-artifacts-v3.log`含公开导出、JNI身份、全部APK库字节、签名和哈希。修改后的host脚本语法检查通过。
- 两APK各10个native assets与源码资产逐库一致，签名通过；对日志37旧APK逐库检查，只有对应`libmpv.so`不同，其余18个库字节不变。相对guard仅4个既定目标库变化、16个依赖不变。两ABI公开`mpv_*`导出一致；JNI保持arm64 `89cea66bf8b77b1081d602cdea3cedcec9ee9c34b4c03827d88ab000fd739689`、armv7 `72c3e113f9bff7e92a68793a3ea3e0982d8caa31dab40065a1ecb7480ebc3549`。
- 新包包含`WebHTV FEL core preloading: async single-slot, leased GPU cache, lookahead unchanged.`；fatal的`core-preload={requests=… done=… retries=… errors=…}`能区分未进入VO、暂存重试和解码器本身停止。仅FEL额外缓存允许按需增长，实际GPU内存/耗时仍需测量，不能把host模型当作厂商输出池容量证明。

| 最终产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14584844 | `cd38c28e6a2621085ec762babf331036d839428116756a1184e55167df130f7b` |
| arm64 `libmpv.so` | 17778864 | `e076515c29fbbcd9b0e1d77e7ac9002159367d7a974807439cdc8207568ffcdc` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130456903 | `f8d0a9e860c595eba033e4ef9f3f029cc78e808ff36fdfae5e5c560893c8d333` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151588356 | `aa4ff05b66ad578c383b0ca181c7fe26dc6f3417d6ebf914c1e2bb4ef32df4d8` |

最终输入SHA256：FEL patch=`fe844402414a3e8c1c7d060632145c5725b91bcd4abd9075310b4a20bfe7838e`；host脚本=`9251e525c0ab945dbaed38467233f03d28ff109f87e6f663b17882ce5fbca49d`；native验证脚本=`6467bbce2bef48e5a93238051a208975569d8629223e323829ddaac1afd8b8fc`；锁仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。生产源码对应同一锁定图，无依赖升级。

日志37旧APK已保留为`fel-log37-tv.apk`/`fel-log37-mobile.apk`；v2中间包也保留，均非本次交付。下一步只做目标电视同源FEL连续播放/seek/退出验收及日志核对；尚未提交/tag/push，不宣称已完成真机需求。

### 9.10 日志38：6/6提前暂存完成后仍停滞

- 原始证据：`/Users/macbookpro/Downloads/webhtv-debug-log (38).txt`（2010行）。用户确认表现与之前相同；同一本地GIJoe样片两次播放，trace分别为`p-wse9rz-3`和`p-wsept7-4`。`WebHTV FEL core preloading: async single-slot, leased GPU cache, lookahead unchanged.`证明v3接线正在执行。
- 两次共同状态：`core-preload={requests=6 done=6 retries=0 errors=0}`；BL `in=12 out=6 held=0`；EL `in=25 out=18 held=1`；core `in=6 out=0 held=0`；配对6帧；AImage acquired/mapped均6，所有错误、过期和超时计数0；GPU submitted/completed均6、source-held=0、pending=0、fence-timeouts=0，输出位深10。
- 第一轮19:40:09.503 fatal：最后配对33.784秒，BL输入隔离`4129616->3881051`字节，GPU累计copy wait 138807微秒、单次最大25003微秒。第二轮19:40:30.674 fatal：最后配对3.420秒，BL输入`4069095->3582799`字节，GPU累计132977微秒、最大23696微秒。最终均为MediaCodec端口750ms不可用，累计8次错误后4003退出。
- 已排除的旧解释：不是未安装v3，不是本轮第6帧尚未送入GPU，不是GPU fence等待超时。恢复点前preroll没有正式呈现（core out=0），不能把6帧解码当作播放改善；VO held=1是worker完成前的最后阶段快照，不能用它否定GPU实际source-held=0。
- 尚未证明：厂商DPB容量、缓冲归还后的驱动行为、输入/配置兼容性。`av_mediacodec_release_buffer_status()`在实际render-release路径会先减少`hw_buffer_count`，因此仅看到源`mp_image`引用存活不足以断言硬件输出未归还。必须读完整控制流与真实返回值再决策。
- 用户新增研究要求：遇到难点扩大到非Android/非mpv的开源播放器及相关视频项目，不局限已有实现。研究问题限定为“输出已暂存归还后仍固定停止，是否存在输入调度、Surface消费者或配置契约遗漏”，保留第8节算法/生命周期研究，不重复泛搜。
- 2026-09-12 19:57 Asia/Shanghai续修估计：源码与跨项目核对10–15分钟，范围内修正/定向测试10–15分钟，双ABI和APK约10分钟，目标20:35–20:40。guard/保护文件/依赖锁不变；没有基于本日志的新生产修改，v3产物是已失败证据而非可再次交付的修复。

#### 跨项目研究与下一候选决定（访问2026-09-12）

问题：BL/RPU仍送普通HEVC硬件，而EL已由软件解码时，是否应将RPU也留在软件侧，使硬件输入成为纯BL？反假设是硬件输入无关、问题完全在Surface消费者；电视对照必须能区分两者，不能把新方案当成已证实厂商根因。

| 来源/固定修订 | 等级与实际代码证据 | 对本任务的影响与限制 |
| --- | --- | --- |
| FFmpeg锁定`177f090e0503b7e013922ca903bde14b1c375f18`，`libavcodec/hevc/hevcdec.c::decode_nal_unit/decode_nal_units`、`mediacodecdec.c::mediacodec_extract_hevc_metadata` | A；常规HEVC解析将62/63类NAL排除在slice解码之外，单独解析RPU；当前MediaCodec wrapper虽解析RPU，仍把原始NAL送硬件 | 证明普通hwaccel与整包MediaCodec wrapper契约不同。仅移除EL不等于硬件输入只含BL；不升级或修改FFmpeg |
| mpv锁定`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`及本地既有补丁，`demux/dovi_split.c`、`filters/f_enhancement_pair.c::inherit_dovi_from_el` | A；现有EL分支用`el_rpu`；按PTS配对后若BL无RPU，既有函数从EL复制DV映射与亮度字段 | 有可复用的RPU来源，不需另写解析器或取消FEL。必须实测软件EL的逐帧RPU完整性，且保留共享track/codec配置 |
| [Kodi BitstreamConverter](https://github.com/xbmc/xbmc/blob/4aab36cf9a40ce4c4889df6c1eeed3f57307bd1b/xbmc/utils/BitstreamConverter.cpp)，`4aab36cf9a40ce4c4889df6c1eeed3f57307bd1b` | A/B；`m_removeDovi`同时剔除UNSPEC62和63，转换P8.1则是另一条分支 | 参考纯HEVC输入边界，不声称Kodi此函数完成FEL重建；本轮不复制GPL实现或修改用户旧模式 |
| [Nova aos-avos PR #7](https://github.com/nova-video-player/aos-avos/pull/7)，未合并head `cbd1feb7d4ef2eb178896c7dc20606d961b27c6c`，实际`Source/codec_mediacodec_dovi.c` | A（代码）/C（作者设备报告）；BL先提取RPU，再`dovi_strip_dv_nals`，纯码流进MediaCodec，RPU按时间戳单独保留 | 是独立相关实现，支持元数据/硬解分离方向；其双硬解、CPU copy、队列和性能声明不直接移植，也不把未合并PR称成熟通用解 |
| [VLC MediaCodec](https://github.com/videolan/vlc/blob/d543b35ad1d3483cc365630ad5fc7876342f0fab/modules/codec/omxil/mediacodec.c)，`d543b35ad1d3483cc365630ad5fc7876342f0fab` | A/B；独立`OutThread`在锁外取输出、flush后区分旧输出归还，输入和输出各有进度 | 调度是可借鉴替代路线，但目前mpv已有独立VO且6帧归还完成；不足以证明必须重写MediaCodec线程，不实施此较大替代 |
| [GStreamer V4L2 bufferpool](https://github.com/GStreamer/gstreamer/blob/46db8717ba577a8f83f31ae984da5c7e689f9728/subprojects/gst-plugins-good/sys/v4l2/gstv4l2bufferpool.c)，`46db8717ba577a8f83f31ae984da5c7e689f9728` | A/B；有限硬件池低水位时深拷贝并立即归还源buffer；最新qbuf错误路径也要unref | Linux设备旁证早归还的重要性，支持保留已有GPU暂存，但不能解释日志38全部归还仍停滞；不复制CPU readback路线 |
| [Android MediaCodec官方文档](https://developer.android.com/reference/android/media/MediaCodec#DataProcessing)、[CSD约定](https://developer.android.com/reference/android/media/MediaCodec#CSD) | A；明确设备可能等所有输出/输入归还才前进；CSD与媒体数据各有提交契约 | 保留有限等待/退出保护，不增加超时或硬件队列作为试错。官方并未承诺所有普通HEVC驱动会忽略DV7扩展，厂商具体行为仍待验证 |
| [Media3 #2711](https://github.com/androidx/media/issues/2711)及维护者评论、[#3347](https://github.com/androidx/media/issues/3347)；[Nova #1650](https://github.com/nova-video-player/aos-AVP/issues/1650) | B/C/D；MTK上存在HEVC初始化成功但无输出或持续延迟的报告，部分仅DRM/不同系统版本；#3347报告VLC同设备正常，并否定简单调阈值 | 不把芯片名相同当同根因，也不据此降级为“设备不支持硬解”；只保留作为额外诊断线索 |

以上新增固定修订均为只读参考，不列为升级/cherry-pick候选。原始响应/源文件保存在`/private/tmp/webhtv-p24-reliability.ASCawt/research38-*`。论文类沿用第8节：本轮不改NLQ/色彩/编码算法，算法论文不能决定MediaCodec输入契约，未新增论文作为根因证据。技术文章沿用已读Bigflake回压说明；没有用搜索摘要替代以上实际源码。

方案比较：①不改已被日志38否决；②继续扩大池/超时无法解释6/6归还，拒绝；③整体迁移VLC线程或Nova CPU-copy会扩大架构/性能/ABI范围，暂不采用；④窄适配：仅显式FEL+Profile7+MediaCodec BL使用已有`dovi_split=bl`，剔除该私有decoder context/packet中的DV及EL配置，保留原始共享codec/demux和EL/RPU，按现有PTS匹配把软件EL的RPU交给GPU，推荐实施。用户已授权本FEL可靠性任务持续实施，无新模块/依赖/公开API/设置行为扩张。

验收：纯BL每个NAL字节/PTS不变；原始共享配置及EL/RPU不变；无FEL/软件BL/P5/P8路径不分配、不扫描、不改配置；实际软件EL解码的RPU按PTS与原包逐字节一致并含FEL NLQ；实际继承函数引用/色彩字段正确；双ABI/ELF/导出/两APK身份通过后交电视验证至少越过固定6帧、完整播放57秒并seek/退出。只有这次实播能判定输入隔离是否解决日志38。新增日志须标明`pure-bl`、`RPU-source=EL`及缺失计数，不每帧刷日志。

回滚：保留第9.9节v3补丁/产物证据；本次只改变已有FEL补丁、配套测试/校验marker/构建说明及对应两份libmpv，不重建FFmpeg/libplacebo/JNI。若元数据验证不通过，不打包此候选；电视未过不finish/commit/tag。

#### pure-BL实施及主机验证

- `video/decode/android_fel_packet.h`仅FEL/硬解MediaCodec/P7准入，改为`dovi_split=bl`；成功初始化后只移除私有AVCodecContext的DOVI及HEVC EL配置；每包用独立ref过滤并清除同类packet配置，输入借用对象、共享轨道及EL分支不变。`vd_lavc`启动/首包/销毁日志明确pure-BL；配对按既有函数从EL继承RPU，统计BL来源/EL来源/缺失，seek reset清零。
- 实际源码三处变更：上述header、`vd_lavc.c`、`f_enhancement_pair.c`；权威补丁已由固定pre-FEL基线机械再生成，反向`git apply --check --recount`通过。生成脚本/临时结果为`refresh-pure-bl-patch.sh`和`/private/tmp/webhtv-fel-pure-bl-patch.IqB8Gd/`，此前源码/前置补丁不改。
- `fel-pure-bl-host.log`前6组NODE/解码器控制/VO丢帧/core暂存/图像租约/并发快照ASan/UBSan通过；随后packet测试两处旧helper名称造成编译失败，已修正。选择脚本段的辅助命令无输出、未执行测试，不计为通过；改用显式测试脚本，没有重跑已通过的前6组。
- 最终定向结果`fel-pure-bl-metadata-tests-final.log`：Annex-B和长度1–4、准入/借用/ref/重试/复位/畸形输入通过；样片前120包移除416个DV NAL，41149779→37572364字节，所有BL载荷、PTS/DTS/duration及非DV side-data一致。新`fel_el_rpu_test.c`在0、3.212、33.575秒三个seek入口各解码120帧，360帧的原始RPU与输入按PTS逐字节一致，均为1080p10软件EL、10bit BL/EL参数、residual开启及linear-DZ NLQ。新`fel_rpu_inherit_test.c`直接编译实际继承函数，验证引用、亮度/色彩、像素range/depth保留及原有BL RPU/禁用元数据优先级；源码准入/构建接线静态检查通过。
- 主机FFmpeg为63.1.101，Android锁定为63.3.100；测试不是Android硬解/画质/性能实播替代。task guard检查通过，无ADB设备；JNI/FFmpeg/libplacebo尚未变化。为完成真实元数据验证，目标从20:35–20:40顺延至约20:50 Asia/Shanghai，仅继续必要双ABI/打包验证。

#### pure-BL最终产物（待电视实播）

- `fel-pure-bl-arm64-build.log`、`fel-pure-bl-armv7-build.log`：显式`bash -e`串行增量编译成功；`fel-pure-bl-stage.log`、`fel-pure-bl-native-assets.log`：暂存/ELF/锁版本/依赖命名空间通过。
- `fel-pure-bl-artifacts.log`：两ABI公开mpv导出与基线一致，JNI身份未变；相对guard仍仅4个目标库变化、16个依赖字节不变；相对日志38只两份libmpv不同，其余18库相同。首次APK构建45秒、172任务；但增量ZIP有旧内容空洞：电视145052016字节，实际压缩条目只129652858字节（与旧包内容仅差734字节）。未交付膨胀产物，移动保存在`fel-pure-bl-incremental-tv.apk`/`fel-pure-bl-incremental-mobile.apk`；只再次运行两个package任务生成紧凑APK，无native重编。
- `fel-pure-bl-compact-apk-build.log`：重打包46秒成功；`fel-pure-bl-compact-artifacts.log`：最终两APK各10库与源资产一致、签名通过。临时统计脚本最初取错zipinfo字段，导致overhead显示0；该辅助数字无效，已修正为第6列并单独重验实际ZIP开销，不重复已通过的签名/逐库验证。
- 21:04告知超过20:50估计，剩余路线缩为干净重打包/验签/记录，未继续研究或扩展功能。局域网日志可读，但ADB无设备，本轮没有安装/操作电视。locale/CXX32位/deprecation提示为环境或现有构建警告，没有据此改动产品。

| 最终产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| armv7 `libmpv.so` | 14585596 | `049b7872070810cbb54c639292ac7116746ecb419853e66c61a86d98b3f5dced` |
| arm64 `libmpv.so` | 17779360 | `b5cd05c4c68cddc8db4697742f4a78381c4524ea5233ed6d12e43dae2f5ed5c0` |
| 电视 `app-leanback-armeabi_v7a-debug.apk` | 130456903 | `7666ca86315bd107dea68ece38bbaeaed211724142f453ddac7703f2c2fe3955` |
| 手机 `app-mobile-arm64_v8a-debug.apk` | 151588356 | `463543f9a1e044c250d23236f49d12ae98756bb71adc13bd2b2d52846256bf06` |

最终输入：FEL patch=`563608db9d4e1c08aedab1ebe967bff0ecc5a564ddb2521e4692d53fa4dacff8`；host脚本=`776b1aad53c7e926ccc0a899f02cb4b8845d84749412d386caa4f1daa831d9f0`；native验证脚本=`a3bb7cb1f50c28633acf80694b27ed5281eb48e26bb14f37c50bbd6fd77e65e3`；native lock仍为`a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`。未提交/tag/push；只有电视新候选的持续播放、画质/吞吐/seek/退出通过，才能关闭可靠性单元。

### 9.11 日志39/40：生产者与消费者交接窗口

- 日志39：`p-wwfrl3-1`从56.139秒开始，BL输入12包/输出6帧后4003；`p-wwgd7y-2`从8.800秒开始，21:33:45.379配对55帧，到21:34:58.380配对1019帧，73.001秒推进964帧（约13.2fps，片源23.976fps），21:34:59.196 GPU完成1030/1030，累计CPU等待GPU拷贝23741517微秒，最大84306微秒。`mpv-frame-timing out`为VO丢帧计数而非已呈现帧数，末尾615；A/V偏差曾9047毫秒，不能称正常掉帧。
- 日志40：`p-wwlxdc-7`从34.576秒开始，输入19包/输出13帧、core暂存13/13，core out=0后4003；`p-wwmg05-8`从7.299秒开始，输入12包/输出6帧、暂存6/6，core out=1后4003。两轮GPU均完成且无fence timeout；解码器都是`c2.mtk.hevc.decoder`，GPU是Mali-G57。没有证据证明电视不支持基础层硬解。

#### 决策依据（2026-09-12，只读来源修订不变）

| 来源 | 等级、已读事实 | 本轮影响与限制 |
| --- | --- | --- |
| Android MediaCodec DataProcessing官方文档，见9.10节URL及保存的`research38-android-mediacodec.html`第33813–33817行 | A；设备可能在任何一个输入/输出未归还时停止产生新输出 | 要约束的是调用下一次解码时的真实所有权，不能只看销毁时统计；不证明这台MTK的唯一内部根因 |
| mpv固定`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`加当前补丁：`f_decoder_wrapper.c::read_frame/decf_process`、`player/video.c::video_output_image`、`vo.c::vo_prepare_fel_frame` | A；worker输出BL后可继续decode，而暂存发生在下游配对/core；异步队列limit=1并不等于上一帧已归还 | 确认有未覆盖的生产者/消费者时序窗口；现有host测试只覆盖core/VO侧，尚未测试这个交接契约 |
| FFmpeg固定`177f090e0503b7e013922ca903bde14b1c375f18`加既有端口保护：`mediacodec_receive_frame`、`av_mediacodec_release_buffer_status` | A；双端口不可用在一次调用内等待并750ms返回错误；render时降低hw_buffer_count，但GPU copy后的AImage归还是另一阶段 | 保留保护，不用增大超时或无限重试掩盖；本轮不修改FFmpeg |
| GStreamer `46db8717ba577a8f83f31ae984da5c7e689f9728`，`gst_v4l2_buffer_pool_process` capture分支（保存源码2035–2117行） | A/B；有限捕获池不足时，在交出下游前深拷贝并归还原buffer | 借鉴“在生产者输出边界脱离硬件池”的原则，不复制Linux/CPU-copy实现；继续复用既有GPU raw-YUV copy |
| VLC `d543b35ad1d3483cc365630ad5fc7876342f0fab`输出线程、Nova未合并PR#7 `cbd1feb7d4ef2eb178896c7dc20606d961b27c6c`输入/拷贝队列 | B/C；独立硬件输出归还与后续显示解耦 | 作为既有跨项目旁证，不照搬整体播放器/CPU readback，不把未合并项目称成熟FEL实现 |
| Media3 #3347、Android/Bigflake回压技术资料，沿用9.10与8节记录 | B/C；同设备硬解迟到可受应用调度影响；改drop阈值不等于修复吞吐 | 本轮不改drop阈值。论文不适合作为这个API所有权缺口的判定依据；不引入新的算法或泛搜论文 |

比较：不改已重复失败；原样upstream异步队列仍不能约束设备持有；整体移植VLC/Nova或重写FFmpeg超出最小修复；采用WebHTV窄适配，在FEL硬件BL的worker输出边界完成GPU暂存/源归还后才发布帧。EL继续独立软件解码，GPU仍在现有VO线程运行，配对/RPU/显示时序不变。复用原有GPU缓存，不增加第二次像素拷贝。同步仅发生在独立BL worker，不将新的同步等待放到App主线程或mpv core；最坏GPU/驱动挂死仍依赖已有独立恢复机制，不承诺消除系统级挂死。

实现要点：使用现有共享AVBuffer租约的一字节原子完成标记，初始为0，仅在GPU fence完成且AImage归还后发布1；VO缓存命中也推进未完成fence，重试有界。暂存发生在EL RPU继承前，因此mapper的FEL raw-YUV存储契约须与逐帧RPU无关，不能先做HDR10转RGB，也不能因后续继承RPU而销毁暂存像素；渲染时恢复实际帧的表示/显示参数。旧模式、EL软件路径和不支持该暂存的mapper维持原路由。

验收：生产者不会在未归还上一已输出硬件帧时进入下一decode；真实GPU像素未完成不能提前标记；共享引用/超时/取消/缓存命中/无FEL/软件EL/非MC隔离由定向测试覆盖；实际mapper函数确认RPU继承前后复用缓存且保留10bit、range、裁剪/旋转、HDR/RPU。仅新测试与受影响已有测试重跑，然后双ABI/libmpv导出/ELF及目标APK逐库验证。电视至少重复起播3次、完整播放57秒及seek/退出通过，才可宣称可靠性修复；性能先记录生产者暂存耗时，不能用host模拟宣称电视达到实时。

回滚到本轮基线`cbb02fa4c40a2d0b1d04a43d6c5be4265129f98e`对应FEL补丁和两份libmpv；不回滚此前已保存的保护功能。授权沿用用户持续实施FEL可靠性的明确要求，未增加新产品模式、依赖、JNI/API或其他播放器行为。21:52的总目标22:45–22:55；定位阶段超出原20分钟，原因是最终日志不足以证明首次超时的时序，停止可选研究，只走上述可验证的所有权交接路线。

#### 本机候选交付记录（2026-09-13，尚未电视验收）

- `f_decoder_wrapper.c::stage_fel_before_publish()`在独立BL worker的`output_frame:`发布边界调用已有非阻塞`vo_prepare_fel_frame()`，2ms重试、总界限750ms；不在App/core主线程增加同步dispatch等待，超时沿用失败结束流程。`VO_NOTIMPL`保留原路径。`fel_stage_failed`随reset清除。
- `filters/f_android_fel.h`以共享AVBuffer的一字节原子ready标记表达源归还。`finish_output()`仅在fence成功且AImage归还后置位，`stable_reuse()`缓存命中以零超时推进fence，不能提前释放硬件buffer。`hwdec_reconfig()`将FEL暂存的存储表示固定为raw YUV，显示前恢复实际表示，避免EL RPU继承前后重新建图或误做HDR10到RGB转换；不伪造RPU、不降位深、不增加第二次像素拷贝。
- 新`WebHTV FEL producer handoff:`日志记录staged/retries/平均与最大耗时/PTS和`source-returned=1 before-publish=1`；`WebHTV FEL decoder cost:`按BL/EL区分send/receive调用数、解出帧数、累计与最大耗时。首帧/每3秒采样；未选FEL不调用新增计时器。复用App现有调试日志通道，电视不依赖ADB。
- 2026-09-12 22:55已确认串行双ABI构建、stage和native ELF检查成功。恢复后的旧工具cell不可读取，但持久化校验日志完整，未重编。2026-09-13 02:17恢复时Git/guard一致；Gradle首次受缓存锁文件权限阻止，按授权访问现有缓存后构建成功，未清理缓存或改变受保护CXX目录。旧两个生成APK移至证据目录的`before-handoff-{tv,mobile}.apk`保留，避免增量ZIP空洞；没有删除用户文件。

| 最小验证 | 结果与证据 |
| --- | --- |
| 实际生产者、GPU完成及缓存命中函数host ASan/UBSan | `producer-test.log`；单可用输出120帧、fence完成→源归还→发布、750ms超时及准入隔离通过 |
| 受影响core/VO/lookahead/lease测试 | `core-test.log`；2/6/10帧前视、有限输出进展、异步retry/timeout/reset、preroll EOF、裁剪和RPU前后缓存复用通过 |
| FEL静态契约/补丁可逆应用及脚本语法 | 通过；`patch-generation.log`及本轮工具记录。Java/JNI未改，保留原JUnit/NODE证据，不重复全量测试 |
| 双ABI构建、stage、ELF | `arm64-build.log`、`armv7-build.log`、`stage.log`、`native-assets.log`通过；沿用第2节固定lock、NDK r29/API24 |
| native边界/公开接口 | `native-boundary.log`；相对`cbb02fa4c40a2d0b1d04a43d6c5be4265129f98e`仅两份libmpv变更，18个依赖及两份JNI不变，公开mpv导出集合一致 |
| Leanback armv7 + Mobile arm64 debug | `apk-build-authorized.log`；一次调用，1m29s通过。首次沙箱缓存权限失败记录在`apk-build.log`，不属于代码失败 |
| 两APK各10份MPV资产/v2签名 | `apk-artifacts.log`及`apk-*-signature.log`通过；ZIP结构/签名开销电视802065字节、手机756710字节，无显著增量空洞 |
| 目标电视重复启动、FEL画质、吞吐、seek/退出 | **未验证**。host模型不模拟真实MTK/GPU调度或像素；此候选不得称可靠性/性能已修复 |

产物与输入身份：

| 文件 | 大小（字节） | SHA256 |
| --- | ---: | --- |
| `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libmpv.so` | 17781048 | `3c32fc5e436c61f782232c77259fb7662a5f84b7edd4d5cc3a62327829798fa0` |
| `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libmpv.so` | 14587580 | `812438797193b6c596b989645d01b166410bc75c7a94412a00ea8bc210a2bb60` |
| `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk` | 130456903 | `26a03ee980a26f900dbd79d6fc0b6c0cc03759f2b91829e52d24df014cae9ef8` |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | 151588356 | `6ca4cec6f2c5a5c8b73bd2dc311c1be5fb6266078b49887a6b581f2a3e5dad99` |
| `third_party/patches/mpv-android-fel.patch` | — | `27bba3db80c58e7b9ddc221171d61a9ed76bad070203ff042aa1f9622e527dec` |
| `third_party/mpv-native-lock.json`（未改） | — | `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159` |

当前交付仅是用户已批准可靠性修复的测试候选；不finish或创建“验证通过”的commit/tag，不推送。唯一下一步与顶部恢复锚点一致：在目标电视用相同样片验证源归还与下一次decode的顺序、重复启动和持续吞吐。如发布前归还已经生效仍复现，现假设被否决，必须换路线而非继续扩大队列、错误预算或超时。

### 9.12 日志41：完成确认与异步释放栅栏

本轮基线为`0a82dc13e255524d7c0e4e04c2f51ec9119aec88`；固定四仓依赖仍见第2节，没有新上游合并。用户2026-09-13明确授权继续优化/实现并要求难点跨项目研究，不需逐步确认。当前问题是设备帧的同步与调度，不是重写FEL/NLQ算法。

#### 证据、现有实现与设计依据

访问日期2026-09-13；原始资料保存在`/private/tmp/webhtv-fel-vo-handoff.81ObkE/`。

| 来源、身份 | 等级/实际结论 | 本轮影响与限制 |
| --- | --- | --- |
| 日志41；本地`vo.c::vo_prepare_fel_frame/process_fel_prepare`与`stage_fel_before_publish` | A；mapper可以返回可用纹理而GPU尚未完成，消费者确认后重入，ready快速路径可能遗留同一请求槽；正常交接约90–103ms且GPU CPU-wait约25–26ms/帧 | 修补状态确认，不能仅抬高750ms；总交接时间不等于GPU执行时间 |
| AOSP `frameworks/av@e2f098935447ca4945946de5cb69db843fe3f003` [`NdkImage.h`](https://android.googlesource.com/platform/frameworks/av/+/e2f098935447ca4945946de5cb69db843fe3f003/media/ndk/include/media/NdkImage.h) 的`AImage_deleteAsync` | A；API26支持随image归还传递releaseFenceFd，硬件buffer由栅栏表示可复用时刻，调用后不可再用AImage指针 | 在GPU提交后异步归还，不等于允许未完成GPU时无保护复用。现有接口已动态加载，不新增Android最低版本 |
| AOSP `frameworks/native@4f463a6b1de9198963dc6aff74154a504ba3f8f6` [`GLConsumer.cpp::syncForReleaseLocked`](https://android.googlesource.com/platform/frameworks/native/+/4f463a6b1de9198963dc6aff74154a504ba3f8f6/libs/gui/GLConsumer.cpp) | A/B；系统成熟消费路径使用native fence交回BufferQueue，缺少能力才CPU等待 | 借鉴所有权规则而非复制OpenGL代码，保留fallback |
| Khronos Vulkan-Docs `f84d432d5b8912362f96f581f29bbc4f3c8c7843` [`synchronization.adoc`](https://github.com/KhronosGroup/Vulkan-Docs/blob/f84d432d5b8912362f96f581f29bbc4f3c8c7843/chapters/synchronization.adoc) 的`vkGetSemaphoreFdKHR`/copy transference | A；导出SYNC_FD将fd所有权交应用，copy-transference消耗源semaphore的payload；必须避免未消费signal再次signal、泄漏fd/使用已销毁资源 | 独立source-release semaphore，与libplacebo ready semaphore分离；导出失败停止该mapper的后续异步导出并安全等待，不复用未消费的信号 |
| Khronos/Arm Vulkan-Samples `200b3b21bd1970fc92d887dbac5abf52e0f94dde` [`wait_idle/README.adoc`](https://github.com/KhronosGroup/Vulkan-Samples/blob/200b3b21bd1970fc92d887dbac5abf52e0f94dde/samples/performance/wait_idle/README.adoc) | A/C；Mali实测说明CPU逐帧等GPU会使流水线闲置；应按资源生命周期使用异步fence | 支持减少CPU阻塞的方向，不把示例22%改善或G76结果外推到此G57电视 |
| 当前mpv补丁树`hwdec_aimagereader_vk_direct.c::finish_frame/export_release_fence` | A；既有direct路径已有SYNC_FD能力探测、导出、AImage_deleteAsync和fallback；stable目前仅导入acquire fence | 复用已存在的内部API与设计，新增行为只在显式FEL stable暂存中；不修改default/direct实现 |
| [mpv PR17303](https://github.com/mpv-player/mpv/pull/17303)，head `2eb4c8c38cb3e5213bd94e29fc50c2354f2f22db`、merge `731450f883f24bfa0e3441c205477f1c8d85f1ee`，已读描述及维护者/Android用户讨论 | B/C；GPU fence必须与实际清理消费路径配套，否则可出现泄漏；该PR属于OpenGL | 仅作为生命周期风险旁证，不移植、不将其当本Vulkan故障的修复 |
| 论文类别 | 本轮不作为规范依据 | 没有新重建或调度算法要推导；决定由Android/Vulkan所有权规范、实际状态机回归和同设备测量裁决。泛CPU/GPU吞吐论文不能证明特定厂商的fence实现，不额外扩展搜索 |

现有调用链：独立BL wrapper → 单槽`vo_prepare_fel_frame` → VO线程`preload_dropped_fel_frame` → AImageReader → stable GPU copy → libplacebo独立纹理与EL配对/呈现。`finish_output()`仍拥有提交后的VkFence/input缓存生命周期；提前交回的只能是带释放栅栏的AImage，不能销毁VkImage/VkMemory、复用未完成command buffer或覆盖仍被帧租约引用的输出。FFmpeg纯BL输入与EL/RPU继承、10bit输出、裁剪/HDR、seek/reset和错误预算保持。

#### 方案比较、决定及验证

- 不改：日志已否决稳定性/实时性，不满足目标。
- 原样使用上游/当前CPU逐帧同步：保留简单所有权，但阻断流水线；只修槽可消除一个错误来源，不能解决额外GPU等待。
- WebHTV窄适配（采用）：请求只有“安全归还源”才可确认，快速路径不遗留自己的槽；支持SYNC_FD时提交独立release semaphore并立即交还AImage+fd，GPU输入/输出及render ready semaphore继续受原队列约束；无能力或导出失败使用原有界等待。增加源已交还与GPU真正完成的区分、异步归还计数及VO分段耗时，日志不把异步交还冒充GPU完成。
- 整体更换renderer/另建GPU线程、CPU readback、降低分辨率/位深或丢EL：扩大回归面或违反需求，本轮不选。

验收：先让定向测试在旧VO状态机稳定失败，再验证延迟fence、快速命中清槽、无能力、fd导出失败、fd=-1已完成、重复使用/取消/销毁和非FEL隔离；GPU未完成时只能在有效release fence保护下交还源，不能提前复用GPU资源。保留两ABI/ELF/导出、18个未改依赖、APK逐库/签名。真实性/性能仍须同一GIJoe样片至少3次可重复起播、57秒完整播放及seek/退出；吞吐目标接近23.976fps且无持续A/V累积，默认模式无新增GPU/EL工作。不能以host模拟代替真实10bit像素、功耗或电视性能验收。

回滚：撤销本guard的FEL补丁、测试/诊断和两份libmpv，恢复上述已保存的用户候选；不改已发布tag，不推送。新增导出沿用既有Vulkan扩展，无新动态依赖、JNI/公开API、许可或包下载能力；不支持的设备继续安全fallback。

#### 实施与定向验证

- `vo_prepare_fel_frame()`先消费同一请求再判断prepared快速路径，其他帧的快速命中不消费当前请求。`process_fel_prepare()`仅在源已安全归还时确认成功，texture已映射但源仍占用则保留同槽重试；提交结果同时核对generation和lease身份。
- stable为显式FEL且EXPORTABLE时创建独立source-release semaphore，与libplacebo ready semaphore一起提交；SYNC_FD导出成功后由`AImage_deleteAsync`接管图像/fd，调用后不再使用AImage。原VkFence继续保护input/cache/command buffer生命周期，直到GPU真正完成才标记complete和重用GPU资源。导出失败会禁止该mapper后续source semaphore signal，避免重复signal未消费payload；继续原有界等待。fd=-1成功表示已signal，不误判为导出失败。
- 原子lease状态区分safe-to-publish与GPU-completed；前者只在实际GPU完成或Android已接管release fence后可见。`producer handoff`增加gpu-complete字段；GPU pool增加async-returns/release-failures；`map cost`分离MediaCodec release、AImage acquire、GPU map的累计CPU墙钟时间，均只对FEL计时。
- 新host回归先对旧代码运行，按预期在“映射成功但源未归还，应该继续等待”断言失败。修正后定向ASan/UBSan检查实际core/VO和生产者/submit/export/finish/reuse函数：120个CPU fence帧、120个async fence帧、源归还/fd转移先于发布、GPU未完成时保留input/command资源、fd=-1、分配/导出失败、失败后禁止再次signal、重复调用不二次转移fd、两信号独立与非FEL隔离全部通过。证据`targeted.log`；模拟时钟不作为电视性能改善的量化证据。

#### 双ABI与APK交付证据（2026-09-13）

证据根目录：`/private/tmp/webhtv-fel-vo-handoff.81ObkE/`。固定mpv/FFmpeg/libplacebo/mpv-android revision与NDK/API见第2节；没有JNI/依赖升级。源API还核对了同一AOSP revision的`NdkImage.cpp::AImage_deleteAsync/AImage::close`：releaseFenceFd随关闭进入reader释放路径，图像对象此后不可用；调用顺序与既有direct路径一致。

| 验证 | 结果 |
| --- | --- |
| 旧代码负例 | `core-before.log`按预期断言失败；原因是mapped但源未归还时过早确认 |
| 改后实际函数ASan/UBSan与C++策略 | `targeted.log`三组通过；含请求/延迟/复用/回退/超时/隔离，不重复无关BSF/Java/JNI测试 |
| 补丁生成/反向应用/静态接线 | `patch-generation.log`、`static-contract.log`通过；修改的两个shell脚本语法通过 |
| arm64、armv7串行增量构建/安装 | `arm64-build.log`、`armv7-build.log`、`stage.log`通过；没有重复完整依赖构建 |
| ELF/SONAME/依赖/新诊断标记 | `native-assets.log`两ABI通过；已有局部变量shadow警告不在改动处，未扩展修复 |
| baseline/candidate native边界与mpv公开接口 | `native-boundary.log`；2份libmpv变化，其他18库及公开mpv导出完全不变 |
| TV32/Mobile64 debug构建 | `apk-build.log`一次调用1m12s通过；复用隔离CXX目录，原35文件受保护 |
| APK内容/签名/结构 | `apk-artifacts.log`两包各10库逐一匹配、v2签名通过；电视ZIP开销799964字节，手机754872字节，无显著增量空洞 |
| 目标电视真实FEL画质、性能、重复启动/seek/退出 | **未执行**；ADB列表为空，不能用host模型代替 |

| 产物/输入 | 大小（字节） | SHA256 |
| --- | ---: | --- |
| `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libmpv.so` | 17782888 | `c0dbdade8e905d9ee4dadf1abe686d9ac40660471bb6d34a6c5a3104b986dc0c` |
| `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libmpv.so` | 14589660 | `941dc0af9b3cdc902964cea9c1a9fd5bb903ec444cf7c3965cbefe4dccae06bf` |
| `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk` | 130456903 | `7db281f0024e60f3ce8e451a53b84c41d99eb475fba8b778d1abd21019a11a3d` |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | 151588356 | `6854b1c300d928de2f658b020787f6f1868c0b9d4f83ebbf58d00852dbce4bbb` |
| `third_party/patches/mpv-android-fel.patch` | — | `4a5ffb6acdd9764de642c7e17a37883bf382539552a6469c39db8bda58eb0366` |
| `third_party/mpv-native-lock.json`（未改） | — | `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159` |

旧两APK保存在本证据目录的`before-async-tv.apk`与`before-async-mobile.apk`，未删除用户文件。同步/资源生命周期核对较估计久，04:29已确认APK构建成功，停止可选检查，仅完成包内身份/签名和记录。当前仅交付待电视验证候选，不将guard结束或需求标为完成；后续唯一动作是顶部所列同设备重复实播，优先看真实异步能力、交接耗时、4003和持续帧率，而非继续盲调队列/超时。

### 9.13 日志42：冷初始化误用帧期限与失败EOF空转

本节继续同一`P2-4-fel-vo-handoff`原子单元；基线、固定四仓revision及回滚点不变，无依赖/JNI/App接口升级。授权沿用用户持续修复FEL并保护默认功能/性能的要求。

#### 已确认事实与可证伪原因

日志`/Users/macbookpro/Downloads/webhtv-debug-log (42).txt`的三轮`p-xl7f32-1`、`p-xl7t3k-2`、`p-xl86d0-3`均在首帧`result=0 elapsed-ms=751 staged=0`失败，最终各GPU只提交1次、异步归还1次，未配对出首帧。第一轮`map cost`记录`gpu-map-us=2127383`（CPU墙钟，不是GPU执行时间），超过当前750ms；普通native日志经主线程转发有延迟，不能混用打印顺序反推GPU事件先后。第一轮另有主线程缓存扫描1506ms，只作为调度干扰保留，不扩展修缓存功能。

代码`stage_fel_before_publish()`在BL线程内sleep/poll，`vo_prepare_fel_frame()`也从请求入槽开始计750ms；`preload_hwdec_image → hwdec_reconfig → stable_map → create_input → create_outputs/create_pipeline`首次创建资源没有独立阶段，故正常冷初始化也会超时。原host模拟从已建好的资源开始，没有覆盖这个真实缺口。另一处确定错误：`read_frame()`在`fel_stage_failed`后每次被请求都写EOF；`f_async_queue::frame_get_samples()`对信号帧计0，因而max_samples=1挡不住重复EOF，日志队列28481并非真实硬件帧/DPB数。

#### 增量最佳实践证据（2026-09-13访问）

| 来源/固定身份 | 等级/支持的事实 | 本轮适用与限制 |
| --- | --- | --- |
| 日志42与本地上述实际函数；固定mpv `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`加当前补丁 | A；首次资源创建与逐帧交接混计、EOF信号无样本权重 | 用冷初始化/并发消费和EOF一次性回归裁决，不能再只测试已初始化mapper |
| Khronos/Arm [Pipeline Management](https://github.com/KhronosGroup/Vulkan-Samples/blob/200b3b21bd1970fc92d887dbac5abf52e0f94dde/samples/performance/pipeline_cache/README.adoc) | A/C；`vkCreateComputePipelines`会内部编译shader，首次资源准备与稳态渲染是不同工作负载；已有SPIR-V不等于免驱动编译 | 支持独立冷初始化阶段；暂不加入持久化pipeline cache，因为它不能保证第一次启动正确，且不能凭示例数字推断本电视耗时 |
| 同revision的[CPU/GPU同步样例](https://github.com/KhronosGroup/Vulkan-Samples/blob/200b3b21bd1970fc92d887dbac5abf52e0f94dde/samples/performance/wait_idle/README.adoc) | A/C；避免CPU同步等待阻断工作管线 | 保留上节独立release fence，不恢复逐帧CPU等GPU；性能仍须实测 |
| mpv [filters/filter.h](https://github.com/FongMi/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/filters/filter.h) async规则、`misc/dispatch.c::mp_dispatch_queue_process`、[`f_decoder_wrapper.c::lavc_process`](https://github.com/FongMi/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/filters/f_decoder_wrapper.c) | A；pending数据属于filter状态，等待应回到可处理reset的dispatch；上游用`eof_returned`只传播一次EOF | 复用已有BL线程/dispatch，不新增线程或让复位重入一个仍持有栈帧的process函数；只修FEL失败分支 |
| 9.12的Android/Vulkan所有权规范、AOSP成熟消费者、mpv PR17303讨论 | A/B；异步image归还不解除GPU资源生命周期保护 | 沿用，不重复搜索；generation+lease身份仍防止取消后旧任务写回新任务 |
| 论文/额外泛博客 | 本轮不适用 | 不推导新的调度或FEL重建算法；精确状态机、规范与冷初始化实测足以决定，不以泛基准支持电视实时性结论 |

原始增量证据目录`/private/tmp/webhtv-fel-startup.hjGizG/`。

#### 比较、决定、验收与回滚

- 不改：三次实播已否决。
- 只把所有帧750ms改大：掩盖稳态卡死、延长stop/reset等待，不采用。
- 无修改上游：正常async filter/EOF设计可借鉴，但上游没有本项目Android硬解源归还前发布的约束，不能删除FEL交接。
- 采用窄适配：显式标记实际GPU资源冷初始化，只有该阶段使用单独的有界初始化预算（10秒），普通排队/拷贝交接仍750ms；单调阶段标记不能被重复轮询续期。BL pending帧交给wrapper持有，每次process只检查一次，返回已有dispatch处理reset/退出；取消只撤销同一lease的VO请求，不等GPU、不释放GPU仍持有的资源。失败只发一次EOF且停止继续feed。分段输出初始化/资源/pipeline耗时，让下一次电视日志能验证具体慢点。
- 未选方案：CPU readback/降精度/丢EL/增队列、更换renderer、增加GPU线程或依赖，均不需要且违反当前范围。

最小验证：先在旧VO代码稳定重现2.13秒冷初始化被750ms误杀；改后覆盖冷初始化、10秒初始化挂起、750ms稳态停滞、阶段切换不续期、reset/取消/旧generation、pending时不继续decode、失败后大量请求只产生一次EOF和默认模式隔离。继续使用真实函数体及ASan/UBSan；再串行构建两ABI、ELF/导出/18未改库、两APK逐库与签名。原GPU fence/lease/NLQ/10bit、BL/EL配对、core lookahead契约保持。目标电视仍须至少三次成功起播、完整57秒及seek/退出；本机测试不是该验收替代。

回滚：同9.12，恢复`0a82dc13e255524d7c0e4e04c2f51ec9119aec88`对应任务源/测试/两份libmpv；已知它也有严重掉帧，不称稳定版本。当前工作未经实播验证，不自动提交/tag成通过状态、不推送。

#### 实施与host验证

- `f_android_fel.h`新增单调冷初始化阶段；`vo.c::fel_prepare_timed_out`按实际mapper阶段计时，只对冷资源阶段使用10秒，其余仍750ms。`vo_cancel_fel_frame`只取消同一lease；保留generation/身份双核对及原source/GPU就绪语义。
- `f_decoder_wrapper.c`用`fel_stage_frame`持有待交接帧，`stage_fel_before_publish`单次非阻塞检查、期限由VO统一管理；`dec_thread`回到原dispatch并仅有pending时2ms可中断等待；reset/stop先取消待处理引用，再复位/终止。失败发出一次EOF后不再feed/read，正常路径没有新增定时唤醒。
- stable mapper只在真实`resources_ready=false`时标记冷初始化，初始化日志分开输出图像资源与pipeline创建时间；不新增GPU工作、不改shader像素算法、依赖或默认策略。
- `core-before.log`对旧真实VO函数稳定失败在“初始化中并发轮询仍应等待”，不是编译失败。`core-after.log`通过旧core/lookahead/lease/裁剪及新增阶段期限/取消；`producer-after.log`通过120 CPU/120异步fence帧及fd生命周期；第一次该测试失败是fake VO仍提前把未归还源标为成功，按真实VO现有契约修正fake后通过，没有放宽生产契约。`async-after.log`编译真实read/process/dispatch/reset/handoff，覆盖2.13秒冷初始化、待处理期间无额外decode或PTS修正、取消与3万次失败后请求仅一次EOF，ASan/UBSan通过。
- ADB列表为空；目标电视仍不能自动安装/实播，后续必须通过App调试日志验证。上一APK和本轮待打包产物不能混用。未修改原`app/.cxx/`。

#### 2026-09-13本机候选产物与边界

09:12（Asia/Shanghai）原估计定位15–20分钟、修复测试20–30分钟、双ABI/APK15–20分钟，目标10:10–10:25；补齐真实filter/dispatch退出边界的回归超出估计，10:29已停止可选研究，仅完成构建核验。首次native编译发现初始化标记误插到同名`find_input`调用的buffer-removed函数，已移动至真正map路径；相关失败保存在`{arm64,armv7}-build-before-fix.log`。上游buildall在此错误下仍能以旧prefix继续stage，新标记检查拦截了旧产物；本轮临时构建编排增加编译错误/新标记检查，最终两ABI确实重新链接后才stage，不修改仓库构建框架。静态接线检查也约束begin/create_input/end必须在同一map函数按顺序出现。原host测试的函数体未受这次插入位置修正影响，不重复跑已通过用例。

最终`arm64-build.log`、`armv7-build.log`、`stage.log`、`native-assets.log`及`native-boundary.log`均通过；仅2份libmpv改变，18依赖字节不变，公开libmpv导出一致。保留原有局部变量shadow警告。`patch-generation-final.log`/`static-contract-final.log`通过，两个修改shell脚本语法通过。Java/JNI未修改，不重跑无关单测矩阵。

Gradle使用JDK21、原隔离CXX init script与授权缓存，`apk-build.log`记录一次调用4m44s成功（172项，18执行/154缓存），未重试或切换offline。期间工具授权/配置输出等待不能当作native或网络故障。`apk-artifacts.log`核验两包各10个MPV库逐一匹配，两个signature日志v2通过；电视/手机ZIP结构开销分别797680/752635字节，没有增量APK空洞。原日志42两包已移入证据目录`log42-before-tv.apk`/`log42-before-mobile.apk`保留，未删除用户数据。

| 产物/输入 | 大小（字节） | SHA256 |
| --- | ---: | --- |
| `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libmpv.so` | 17785112 | `1531f24447944768932207d1809b1cd82e8b5ab9155826e957cb2502ce270291` |
| `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libmpv.so` | 14591980 | `8edf92a75f30dea35b1218cde0005d45f8d44924b4d5e95f7dae62b80b23dd0d` |
| `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk` | 130456903 | `038d77eb0d694a7c96aa0c17bf3aa23eac7996805702187cdc2de835686489f0` |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | 151588356 | `bb0e60a46c9ae913fc9df181bc8334d59fd75de9a66c05f3e3ab17ef82a521f4` |
| `third_party/patches/mpv-android-fel.patch` | — | `20d26942ff703d308da40fd5820813ae482050a3ec3944eabb3e6529135ebc7f` |
| `third_party/mpv-native-lock.json`（未改） | — | `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159` |

诊断接线已读`MpvDiagnosticsPolicy::shouldLogNativeImmediately`及`MpvPlayer::logMessage`：新`WebHTV FEL`日志走既有App调试通道，不依赖ADB；普通信息仍可能限流/经主线程延迟，fatal维持豁免。新增`GPU init`耗时是CPU墙钟，不能冒充GPU执行时间。本候选尚未电视验收，不finish/commit/tag、不推送；唯一下一步与顶部一致。
