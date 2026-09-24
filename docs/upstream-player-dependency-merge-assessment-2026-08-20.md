# 播放器上游依赖任务索引（当前分支恢复副本）

## Recovery anchor

- 2026-09-18 `AV-DIAG-01` [14.18 单视频轨无效重选与重复能力日志优化](AV-DIAG-01-playback-diagnostics.md#1418-单视频轨无效重选与重复能力日志优化2026-09-18)：两项优化已实现，32项定向用例通过；日志35的18次实际查询回放将候选事件从222条减至56条。TV32快速Release在3分12秒内打包成功，APK/ZIP/签名证据及SHA-256见唯一文档；未安装、实际电视掉帧收益待在线日志对照。guard `AV-DIAG-01-EXO-RESELECT`，基线 `4818057cd64c2c62c94e7208d9121719b4d11fe0`，保护104个 `app/.cxx/` 文件；仅App Java和定向测试，native保持；本单元原子提交/tag收尾。

- 2026-09-17 `C-AVS3` [AVS3 视频解码](C-AVS3-video-decoding.md)：HPM 15.0 的0x32软件后端已提交 `edf4324034fe1681a658dd4557dd8451fcfdb792`，用户接受设备4K50软件吞吐限制；重复续播seek已修复于 `13053755eaea00aa9c6449e8ad55c2ccf1fbc68a`。获批的MPV AVS3 MediaCodec接入已完成双ARM库与Mobile64 APK；ASan/UBSan、完整补丁链、双ABI/16KB/导出、9项Java检查及手机真实JNI/NDK无硬件拒绝通过，guard `C-AVS3-mpv-mediacodec`收尾。硬解出帧/profile/性能仍需具备AVS3硬件的目标设备实测；手动解码、FEL/ASS/软件后端与其他native制品保持。

- 2026-09-17 新需求 `P10` [MPV全局智能去广](P10-mpv-smart-adblock.md)：已复用Exo识别，借鉴Kodi/mpv SponsorBlock在原时间轴跳过广告，保持原HLS清单、IV/Range及总时长。15项单测、Mobile ARM64构建/安装、37份原生库与资源身份检查、真机开关/自动跳过/手动seek/AES隐式IV/主清单/暂停/切源通过。guard `P10-mpv-smart-adblock`，基线 `54e7947c272a7b3ebad8b80bfab4c889e1ea86d5`，保护104个 `app/.cxx/` 文件；同一原子提交/tag收尾，无native或依赖变更。完整研究、边界和回滚见唯一任务文档。

- 2026-09-17 P2-4 [9.26 显式输出P8 HDR10兼容](P2-4-mpv-android-fel.md)：已修复显式GPU模式跳过现有P8→HDR10判断；48秒Mobile64构建/安装及37份原生库身份检查通过，手机已取得HDR10/MediaCodec/PQ，用户实播确认“可以了，打个tag”。guard `P2-4-p81-hdr10-compat`，回滚 `c9a1ac99d05f2d8bcac0558b725d34e196b468e2` / `v5.6.0-202609171247-fel-crash-fixed`，104个原有 `app/.cxx/` 文件保护；自动取帧/相邻场景边界见任务文档，无native或依赖改动，按用户确认立即提交/tag。

- 2026-09-17 P2-4 [9.25 FEL起播配置所有权修复](P2-4-mpv-android-fel.md)：已用mpv选项深拷贝修复9.23静态GPU配置在析构时触发的native SIGABRT；真实allocator旧错误复现/修复7路径、双ABI/ELF/补丁往返及18库不变通过，最终Mobile64已安装，原DV7样片完整FEL/Vulkan硬解出帧、seek和退出通过。guard `P2-4-fel-context-ownership`，基线 `8919cf134218a3d3bb30f91f3180e9cd83eac982`；AVS3/AV3A及视频手动切换合同保留。9.24电视性能裁决继续等待目标设备日志。

- 2026-09-17 `C-AVS3` [AVS3 视频解码](C-AVS3-video-decoding.md)：基础档次 `0x20/0x22` 双ARM制品、两套ARM64 8/10-bit逐像素/flush、Exo MP4/MKV实播及Surface修复已提交 `769e53471dbb3e9ad4f9b94842099ba97a92fdbd`，并获用户确认/tag。后续已移除Exo硬解模式的AVS3软件后备：视频仅手动切换，音频允许回退；修复APK及MPV/音频定向测试通过，用户确认并要求tag，guard `C-AVS3-video-hardware-only`。保护104个 `app/.cxx/` 文件；原始 `0x32`/4K50仍不支持；P2-4保留待电视结果状态。

- 2026-09-16 P2-4 [9.24 用户主动绑定类型对照](P2-4-mpv-android-fel.md#p2-4-fel-bind-probe)已实现获批 B 第一层：四组 fresh record、默认关闭/独占/可取消、保持源所有权，不提交诊断 shader。真实 C ASan/UBSan、21 项 Java、Web 脚本、27 文件完整补丁、双 ABI/ELF/导出及 18 库边界通过；TV64 `202609161336`（SHA256 `9192d5a6df9cb5615f7bacaed8bd55200fd7240bacfd04727dd4324eca2e876e`）MPV 身份/全部 27 个其他原生库不变、CRC/v2 签名通过。guard `P2-4-fel-bind-probe`，基线 `8de0fd70942d513034fb8118de5229d7eb719622`；保护 104 个既有 `app/.cxx/` 文件。下一步电视主动运行对照，按结果继续 B；C 和整体流畅度验收仍等待设备证据。

- 2026-09-16 P2-4 [9.23 起播一次选择 FEL](P2-4-mpv-android-fel.md#p2-4-fel-startup-selection)已实现：native 在实际选轨后建立 FEL VO/decoder，App 观察结果、取消识别后的销毁重载，并要求视频帧提交证据。guard `P2-4-fel-startup-selection` / upstream，基线 `5df95f475009ed0d04d864b60d7d22b229e87e95`，104 个既有 `app/.cxx/` 文件保护；真实 C 合同、34 项 Java 测试、双 ABI 及 18 库不变已通过。TV64 `202609161235` 内容/ZIP/v2 签名通过，SHA256 `f5c51f91c9bd1889417b813c86cd790ce5c84a861fe3a045cdea29fd65fb76ba`，暂无电视性能结果。下一步 B 有界绑定对照；C 继续等待设备证据。

- 2026-09-16 P2-4新增[9.22跨项目源码与二进制复核](P2-4-mpv-android-fel.md#p2-4-fel-cross-project-review)：已读Flutter视频纹理缓存PR/测试、Mesa PanVK/Panfrost的storage/AFBC/MTK detile实现、GL raw-YUV规范，并静态核对本机Kodi MediaCodec入口。日志33三组9/32输入槽、零淘汰，不能套扩大缓存；输出已为32位打包10bit。先修正native选轨后一次建链，再做sampler/storage及外部图像对照，C仍有条件；电视闭源驱动内部原因/实时性能未验证。guard `P2-4-fel-cross-project` / assessment，基线 `98d247ea193c58a3dbfa4033d679023282632a8e`，保护104个既有`app/.cxx/`文件；只提交方案，无新native/APK/安装。唯一下一步：进入获批的起播修正单元。

- 2026-09-16用户日志33否决P2-4整体性能验收，见[9.21复盘及下一阶段方案](P2-4-mpv-android-fel.md#921-日志33起播重复初始化重缓冲来源及a的实际结果2026-09-16)。07:21 TV64/实际libmpv身份匹配；三组map平均37.514/38.712/41.497ms，1419次命令203次descriptor写入，没有实质整体改善。起播均先直出再重建FEL，一次2563ms重缓冲来自内部重建；已记录缓存暂停均false、缓冲状态100。guard `P2-4-fel-log33-review`仅文档，保护104个`app/.cxx/`文件。下一动作：批准起播一次选择FEL路径及9.19-B有界对照阶段后实施；C继续等待证据。

- 2026-09-16 用户插入日志页需求已按[AV-DIAG-01第14.16节](AV-DIAG-01-playback-diagnostics.md#1416-无配对日志操作与固定顶部按钮2026-09-16)实现：完整取消调试配对/token，保留同源POST/限频和脱敏；下载/清空移固定顶部，删除仅手填备注的对照入口。5项JUnit、实际Java网页四视口、61秒TV64构建及10库身份/v2签名/ZIP检查通过。产物`202609160721`同时包含已提交FEL候选，SHA-256及证据见唯一文档。基线`ed3d710ef551210278920ba4cd25e8dda6e19ad6`，guard `AV-DIAG-01-WEB-ACTIONS`，104个 `app/.cxx/` 文件保护；未改native或安装设备。唯一下一步：安装TV64后用同片电视日志验收P2-4候选实际收益。

- 2026-09-16 P2-4第9.19-A获批并完成[9.20描述符内容复用候选](P2-4-mpv-android-fel.md#920-描述符内容复用候选2026-09-16)本机验证：1200帧写入1200→72，仍1200次fresh bind/record/dispatch；真实函数ASan/UBSan、静态契约、同锁双ABI/ELF/导出、18库不变及TV64包10库/签名/ZIP通过。buildTime=`202609160647`，APK SHA256=`0edb43e7a30b5dc92966dd55c6811d2fe720d24cbda7b0b28ce23f3c2e726b74`；guard `P2-4-fel-descriptor-content`原子保存本机候选，104个既有 `app/.cxx/` 文件保护，不推送。唯一下一步：含本候选libmpv的包在电视完成像素及三组性能对照；B/C未实施，起播与整体实时性能未验收。用户插入日志页需求不改变此状态。

- 2026-09-16 P2-4第9.18节无ADB诊断候选完成本机验证：新日志30的bind约32–35ms，已补每3秒本线程调度/栅栏采样及实际Vulkan能力。真实函数ASan/UBSan、源/补丁、双ABI/ELF/导出、13项Java和TV64包10库/签名/ZIP通过，18依赖不变。基线`206a57e0e337304a8b78712c487ef245d7cb0fa0`，guard `P2-4-fel-wait-diagnostics`；保护104个既有脏文件，不改重建/画质/同步。唯一下一步：TV64安装05:28候选（SHA256 `6e9bba7846e2e99620757c7ced5c357b287ce1bc64562a79f2db511e83c86a51`），从用户提供Web地址读取新采样；电视实时性能仍未验收。

- 2026-09-15追加需求完成：PR #107日志能力手工实现，QuickJS/Python归入Console；Web改为两行固定顶部及筛选/诊断/工具三页签，手机底部sheet、桌面右侧drawer。7项Python、分类JUnit、四种浏览器视口/焦点/配对检查、最终双端APK通过，未安装设备。研究、提交处置、产物哈希、验收/回滚见[AV-DIAG-01第14.15节](AV-DIAG-01-playback-diagnostics.md)。guard `AV-DIAG-01-CRAWLER-WEB`，基线 `5acbb05afff34235d66bc1a6f3d7f67427e2a239`，保护 `app/.cxx/`。

- 2026-09-15 AV-DIAG-01全文实现及产物补齐：14.11完成默认全开的分类开关、故障标记/冻结、限时探针、配对、TXT/ZIP及筛选；14.12–14.14完成Media3 owner hook、FFmpeg实际选择/操作、MPV AudioTrack、库身份、观察门控、重复“详细日志”移除及同锁产物。逐项覆盖和真实软件验证见唯一任务文档；设备/性能由用户实测，保护预存`app/.cxx/`70文件，不推送。

- 2026-09-14 插入需求：用户批准按 [AV-DIAG-01 音视频全链路诊断方案](AV-DIAG-01-playback-diagnostics.md) 从 D0 分阶段实施。基线 `2ec5afd8cc3f21bf1693b198f87018488775c660`，guard `AV-DIAG-01-D0`；原 `app/.cxx/` 70 文件保护。D0 公共日志底座代码及37项不同用例、双端 Java 编译、网页执行和三轮 exporter fixture 通过，D1–D5 未开始；设备未连接，FEL 原任务与待设备验收状态保留。唯一下一步：连接目标设备完成 D0 用户端导出及开关性能验收。此需求不合入上游提交，不改写 E/P/C 编号。

- 2026-09-14 00:37新候选：P2-4第9.17节已完成首draw独立有界初始化、能力门控push descriptors及无能力/布局失败fallback，保留逐帧录制、原同步/画质/默认行为。基线`ce10d5c15ef36fa83e27c6195182717a334a1036`，guard `P2-4-fel-warmup-push`原子收尾、不推送；原`app/.cxx/`70文件全部保护。旧VO负例、新真实函数ASan/UBSan、1200帧push、240帧交接、双ABI/ELF/导出、13项Java及两包各10库/签名/ZIP通过，18依赖不变。唯一下一步：电视安装TV32 SHA256=`4b79ec5aecdb81764cec92fa3b6d3b6f2cf0c9816027849804561ab0eab3a0b8`，同样片三轮完整播放/seek/退出，取新App日志；实际扩展能力、流畅度/回跳仍未验收。以下为历史状态。

- 2026-09-13最新候选（18:39包）：P2-4第9.16节已完成逐帧重新绑定/录制及有界帧关联/慢API日志，保留AHB缓存/同步/画质/默认行为。真实函数ASan/UBSan、源/补丁、双ABI/ELF/导出、13项Java及两APK内容/签名/ZIP通过；1200帧1200次新录制、1128次对象槽命中、不重放，18项其他库不变。guard原子收尾并归还临时隔离的Release缓存，不推送。唯一下一步：目标电视安装TV32 SHA256=`4971498a956a723cf348592dc4b228f595efdda56a77a884afb51fec05645722`，同样片三轮完整播放和seek/退出，取新增App调试日志裁决。14:52候选的实机回跳、持续掉帧及独立751ms失败仍未取得新设备验收。以下为历史状态。

- 15:01收尾说明：P2-4-fel-vk-reuse本机验证与两APK均通过，但guard finish因新出现且无法归属本轮的35个`app/.cxx/RelWithDebInfo/621cr346/`及Release tools缓存返回4，尚未提交/tag；原35个保护文件未变。下一步请求临时隔离新增缓存并原样恢复的批准，不绕过guard；详见P2-4第9.15节。

- 2026-09-13日志30续修：基线`dc1401638532840a8362b869b3220cb952ca7b35`约12.9fps/A-V最大7秒。P2-4第9.15节已实现有界AHB/命令复用及API统计，真实函数ASan/UBSan、13项Java、两ABI/ELF/导出、18库不变及两包内容/签名通过，guard `P2-4-fel-vk-reuse`收尾，不推送。唯一下一步：电视安装TV32 SHA256=`67b007a129ba488491cc666683ed8b2cc4a35d3e96c7ef6bf26b8cd56df8ad68`，同GIJoe三轮完整57秒及seek/退出，取reuse/api perf日志验证性能；整体实时播放尚未验收。以下为历史记录。

- 当前2026-09-13日志29续修：基线`1620bac1566727f4067eda631647a11652082e74`三次起播成功但约10–12fps/A-V滞后，不是性能验收。P2-4第9.14节已移除纯FEL统计的主线程/Logcat重复处理，增加无GPU等待的CPU/驱动/GPU分段计时和真实线程报告，保持像素、同步、默认行为和依赖；host、23项Java、双ABI/ELF/导出通过，18库不变。两个最终debug包各10库/签名/ZIP结构通过，guard `P2-4-fel-steady-perf`收尾，不推送。唯一下一步：目标电视安装TV32 SHA256=`871ccbea4ac975055ad54258018c3071c0e51a6624b499746c2e2e1835593f5d`，同GIJoe三轮57秒及seek/退出，取新分段日志再决定CPU并行或GPU链路优化；整体需求未验收。以下均历史状态。

- 当前续修（2026-09-13）：日志42三次首帧751ms失败已按P2-4第9.13节窄修复：实际GPU冷初始化独立有界期限、pending帧回到可取消filter/dispatch、失败仅一次EOF。旧代码冷初始化负例失败，新实际VO/wrapper/GPU host回归、两ABI/ELF/公开导出通过；只变更2份libmpv、18依赖不变。两debug包构建4m44s通过，各10库与v2签名匹配。guard仍为`P2-4-fel-vo-handoff`，HEAD=`0a82dc13e255524d7c0e4e04c2f51ec9119aec88`、恢复tag=`recovery/P2-4-fel-buffer-progress/20260913025508-0a82dc13e255`，续修未提交/tag/推送。唯一下一步：目标电视安装TV32 SHA256=`038d77eb0d694a7c96aa0c17bf3aa23eac7996805702187cdc2de835686489f0`，同GIJoe样片3次起播、完整57秒及seek/退出，取App调试日志裁决；不能以host或打包成功称电视需求完成。其余为历史状态。

- 最新状态（2026-09-13）：上轮失败候选已完整提交为`cbb02fa4c40a2d0b1d04a43d6c5be4265129f98e`，tag为`recovery/P2-4-fel-reliability/20260912214750-cbb02fa4c40a`，未推送；日志39/40否决其可靠性/性能验收。active guard `P2-4-fel-buffer-progress`已按P2-4第9.11节实现BL生产者发布前暂存/源归还及BL/EL耗时日志；保留FFmpeg/既有超时保护、EL/RPU/10bit与默认路径。定向host测试、两ABI构建/ELF/公开导出通过，仅两libmpv变化/18依赖不变，两个debug APK各10库及v2签名通过；本轮未提交/tag，未安装到目标电视。唯一下一步：安装SHA256=`26a03ee980a26f900dbd79d6fc0b6c0cc03759f2b91829e52d24df014cae9ef8`的32位候选，同一GIJoe样片重复启动3次、完整57秒和seek/退出，按新交接/耗时日志判断根因。不能称已解决卡死/掉帧。

### 此前恢复记录（历史；当前状态以上方与P2-4第9.11节为准）

- 当前分支：`feature/mpv-dv7-fel`。
- 当前实施基线：`792c1f880bc151eb1cb6675034ec144aadc14766`（2026-09-12，用户要求保存的已知问题快照）。
- 历史完整评估：已核实仓库历史提交 `9fcab83f9084446566240a8e8f5233d87d0274cc` 中的同名文件可读取；主线提交 `784b90420d646eb6c7ddcc63ad622a92c65b02b4` 删除了根目录本地任务文档，因此本分支只恢复当前实施需要的稳定索引。
- 当前任务：`P2-4`，日志38两次否决v3候选：core暂存6/6、GPU完成6/6、source-held=0，BL仍12包/6帧后停滞4003；不能再用日志37的第6帧未进入VO解释，见任务第9.10节。
- 下一步：目标电视安装第9.10节新32位APK（SHA256=`7666ca86315bd107dea68ece38bbaeaed211724142f453ddac7703f2c2fe3955`）进行同样片FEL完整播放/seek/退出，核对pure-bl及RPU来源/缺失。pure-BL候选的120包BL、360帧EL/RPU/NLQ、两ABI/ELF/公开导出及最终紧凑两APK各10库/签名已通过；跨项目研究涵盖FFmpeg/Kodi/VLC/GStreamer/Nova。active guard及HEAD保持，未提交/tag、目标电视未验收。证据和完整台账见 [P2-4-mpv-android-fel.md](P2-4-mpv-android-fel.md)。

## 稳定任务 ID 与唯一文档索引

历史任务 `P0` 至 `P8` 的完整状态仍以提交 `9fcab83f9084446566240a8e8f5233d87d0274cc` 保存的评估为准；本恢复副本不重新编号或复制其近五千行台账。

| 顺序 | 任务 ID | 类别 | 功能/能力 | 状态 | 唯一文档 |
| ---: | --- | --- | --- | --- | --- |
| 既有任务续修 | `E11` | Exo/App | 压缩音频输出、跳转释放状态、隧道一致性与起播恢复 | A 为 `123d871c027eb686702766bca995f616e8d2bd1e`，首次起播研究为 `e9fef2b90ee8aa634327c2c179eb3dc5d22cb1be`，直通开关修复为 `24fa078d2dc8a404fad23fc30972e5fc4a8a1b5b`。首次快速恢复现已实现：完整 AU 供数及启动门槛读回后，以 800 ms 窗口和原始播放头/路由复核判错，使用 Media3 内部恢复复用当前媒体与缓存；保留正常直出与关闭开关的 PCM 行为。3 项集成用例、TV32 Debug 与签名/ABI 检查通过，APK SHA256 `15c31a01404a1e1678020cecb20df5085508b67ccd880904fb9ef02072654fd5`；策略补跑未选中测试，用户要求忽略剩余测试，不宣称全量通过。guard `E11-first-playback-recovery` 原子提交/tag。按要求不联机，实际时延/性能未实测；不满足早判条件仍有 10 秒兜底，跨媒体经验扩展及音频独占重启未实现 | [E11-exo-compressed-audio-direct.md](E11-exo-compressed-audio-direct.md) |
| 插入修复 | `P11` | MPV/App | AV3A 直播使用 `.m3u8?ts=…` 媒体端点时保持分片语义，修复代理误报 HTTP 400 | 已修复；19 项定向测试、Debug/快速 Release 构建和手机同源实播通过，持续超过 6 分钟、AV3A 音频输出无写入错误；Release 已安装，视频手动解码合同保持；源内迅雷插件 Debug JNI 问题独立记录 | [P11-mpv-live-av3a.md](P11-mpv-live-av3a.md) |
| 插入需求 | `P10` | MPV/App | 全局智能去广开关接入，复用Exo识别并保持HLS时间轴/跳转 | 已实现并续修误跳正文、广告闪帧及Surface复用；原生输出边界fixture零广告帧，原链接正常跨广告；25项广告测试与4项Surface测试通过，Mobile64已安装且用户确认正常；原生库保持 | [P10-mpv-smart-adblock.md](P10-mpv-smart-adblock.md) |
| 插入需求 | `C-AVS3` | 通用，Exo → MPV | AVS3 视频解码，基准档次与 High profile 分阶段验证 | baseline/0x32软件后端及MPV MediaCodec接入已交付；手机不具备AVS3硬件，硬解实际出帧与性能待目标设备验证 | [C-AVS3-video-decoding.md](C-AVS3-video-decoding.md) |
| ASS/HDR 字幕修复已验收；双字幕同屏待实机 | `E4-LIBASS` | Exo/字幕 | ASS 特效字幕及对齐 MPV 默认行为的主／副字幕 | 2026-09-19 修复 HDR/DV/BT.2020 被排除 libass 的路径，并在独立 SDR 字幕层保留原始 RGB；7 项定向设备测试、手机 Debug/测试 APK 构建及 JNI/产物核验通过，用户确认“可以了，打tag”，按第 17.5 节归档，未追加原片配对性能验证。2026-09-18 双字幕的 13 项本机合约/真实播放器/View 检查及手机 Release/Debug/测试 APK、电视 32 位 Java 编译通过，双字幕原生同屏仍待实机。此前外挂/容器兼容桥和原字体已验收，ASS 常规构建必需；复杂字幕已接受一核 CPU ≤40% / render+upload p95 ≤16.67 ms。主 ASS、MPV 独立修复保留；精确 MKV duration/未缓存长事件 seek、libass 双 ABI 等历史边界见第 14–16 节 | [E4-LIBASS-exo-ass-rendering.md](E4-LIBASS-exo-ass-rendering.md) |
| 插入需求 | `AV-DIAG-01` | 通用/App，Exo → MPV → IJK | 无 ADB 音视频分层诊断、脱敏和可判读的日志导出 | D0–D5实现/产物补齐，覆盖与验证见14.13–14.14；设备/性能待用户实测 | [AV-DIAG-01-playback-diagnostics.md](AV-DIAG-01-playback-diagnostics.md) |
| 22 | `E9-3` | Exo/App | 普通 HEVC 硬解 + Vulkan/libplacebo 的 DV5 色彩映射默认准入 | 2026-09-12默认准入已实现，三个定向测试类及 Mobile/Leanback arm64 Java 编译通过；保留原生杜比和设备能力门控，待新版包原场景复测 | [E9-3-exo-dv5-vulkan-renderer.md](E9-3-exo-dv5-vulkan-renderer.md) |
| P2 子阶段 | `P2-4` | MPV/native/App | Android BL 硬解 + EL 软解 + GPU FEL 重建，新增手动选择项 | 日志33否决9.20的整体性能收益；9.22完成跨项目/二进制复核，推荐先修正重复建链，再做绑定类型/外部图像对照；新方案未实施，电视实时性能未验收 | [P2-4-mpv-android-fel.md](P2-4-mpv-android-fel.md) |
| 38 | `P9-MPV-BLURAY-MENU` | MPV/native/App | HDMV Blu-ray 菜单画面、按钮高亮、方向/确认/返回/Popup、菜单跳转与 still frame；BD-J 无提示回退现状 | 2026-09-11父菜单未命中修复已实现，定向验证及构建通过，用户测试确认并要求tag | [P9-MPV-BLURAY-MENU.md](P9-MPV-BLURAY-MENU.md) |

## Checkpoint 55：2026-09-06 P9 HDMV 菜单实施启动

- WebHTV MPV 基线：`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`。
- FongMi MPV 当前审阅头：`13eafa069366edb54606637b323b0d10efd05fa3`。
- 参考菜单链最终已审阅状态包含官方/FongMi 合并链提交 `c318236b8882af860f16f936225430ad053a2179`；不能整体升级到当前审阅头，因为它还携带与菜单无关的渲染、音频、格式和网络变更。
- 产品决定：仅支持 HDMV；`c625405ddcdf9d40cdda2ffe3708865c105ed965` 的 BD-J 菜单接入不实施，不启用 JVM/JAR，不新增提示。
- 实施策略：保留固定 MPV/FFmpeg/libplacebo/mpv-android 基线和 WebHTV 现有补丁，只增加独立、可撤销的 HDMV 菜单补丁及最小 App 输入接线。
- 回滚锚点：`966b3b6747ece447c2f34f7787df7c6af572baa0`。
- 下一步：源码补丁已在隔离固定基线与现有 WebHTV 补丁序列中干净应用；待 native 环境可用时执行双 ABI 构建/资产校验。

## Checkpoint 56：2026-09-07 P9 适配补丁完成

- 生产补丁 `third_party/patches/mpv-discnav.patch` 已替换为相对 `p9-local` 的最终 HDMV 适配版本；未引入 BD-J JVM/ARGB overlay。
- App 与脚本接线已完成，Java 编译、脚本语法、Task Guard check 和完整 MPV patch apply-chain 已通过。
- native 构建阻塞于当前环境缺少 `libplacebo`、Android NDK/clang；双 ABI ELF/资产和设备验收保持未执行。
- 详见 [P9-MPV-BLURAY-MENU.md](P9-MPV-BLURAY-MENU.md) 的 Checkpoint 2；唯一下一步为具备依赖后做一次 native 构建/资产校验。
