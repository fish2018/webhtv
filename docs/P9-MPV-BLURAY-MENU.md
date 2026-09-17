# P9 MPV HDMV Blu-ray 菜单

## Recovery anchor

- 当前修复单元（2026-09-11 15:22 Asia/Shanghai）：用户对上一轮《夜王》诊断和通用父子菜单触摸方案明确“修复”。guard `P9-MPV-PARENT-MENU-HIT`，基线 `48dfa4d67a433390e9959b934b87331a78f11436`，保护预存 `app/.cxx/` 35个文件。只改 libbluray 菜单输入补丁、routing C/脚本测试、双ABI `libmpv.so`、本文与索引；`build/mpv-native` 为派生源码，不改 FFmpeg/JNI/Java/网络/解码策略。
- 已完成证据：9月10日手机实际加载最新库；`/tmp/p9-nightking-menu-20260910.f86HLd/navigation-evidence.log` 记录设置页3的主栏触摸 `hit=0`，字幕按钮仍正常；19:22:52由按钮8向下至自动按钮20，作者指令从页3返回页1/按钮2。已有适配必须先命中当前子页按钮，遗漏无当前可选对象的主栏。
- 本轮接受标准：实际访问父页后，子页没有可命中主栏按钮也能沿可验证作者返回图关闭/切换；真实当前按钮优先，不扫描未访问父页、不执行未验证VM写寄存器/播放操作；新IG/初始化/恢复清理历史，禁用/歧义/动画/超时/新输入安全退出。先定向源码测试，再双ABI构建/ELF/包内一致性，手机《夜王》及已验收两片回归。
- 当前状态（2026-09-11用户确认）：有界已访问父菜单记录、当前页未命中路由及原入口关闭/其它入口切换已实现。用户明确“我测试可以了，打个tag”，接受其已测试场景并关闭可选验证；不把此确认扩大为全部原盘或完整电视回归已通过。源码抽取测试（ASan/UBSan、原有副本/背景菜单、8层历史边界、生命周期接线）通过，双ABI libbluray/MPV构建与ELF校验通过，其余18个原生库哈希不变；两个Debug包构建1m45s成功且包内libmpv逐字节一致。证据 `/tmp/p9-parent-menu-20260911.q9clO3/`。
- 产物：arm64 libmpv `df70eb842ff43bbe70bce56a9ec9dd05fd508511fffa8e1366ad5009135886c7`；armv7 libmpv `07cf1995f2733118b61333bc53b27d47cd352d152abe774a02fd1aa9cb6e75b5`；输入补丁 `260eac3a32d89b828d19b2aee82941b2a3282d9810773394fd9395670a55695a`。手机APK `d878e29dcd9a4914bc411c01ccbbe2da19ada9e4bab0037fa46a09ad81f4141d`；电视APK `c70c0633d48c81bc6959f7ab29b2db76b7313cf8211223c01b3712641ab5b8a2`。
- 构建偏差已处理：并行MPV构建共享meson依赖路径导致arm64首次链接错用armv7 iconv，改为串行重链arm64成功；电视Gradle新生成34个不在scope的CMake缓存，已移至证据目录 `generated-cxx-armv7` / `generated-cxx-armv7-tools`，原有35个文件未改。未升级依赖或修改构建策略源码。
- 唯一下一动作：按用户确认用当前guard原子提交并立即创建本地注释恢复tag，不push、不追加构建或设备取证。

### 2026-09-11 《夜王》父菜单未命中修复决策（已批准）

- 问题：不是新版库遗漏安装、网络等待或旧 `button_effect_running` 活性缺陷。鼠标输入已到GC，但子页只能命中实际可选对象；旧 `_mouse_move` 返回0后 MPV 正确拒绝激活，`_request_authored_return` 无法进入。已有零指令副本/合并背景适配保留。
- A级源/访问2026-09-11：固定 libbluray1.4.1（源包 SHA-256 `76b5dc40097f28dca4ebb009c98ed51321b2927453f75cc72cf74acd09b9f449`）`graphics_controller.c::_mouse_move/_user_input/_select_page/_gc_reset/gc_decode_ts/gc_run`，以及公开 `keys.h`/`bluray.h`；代码是HDMV按钮状态和VM调用的直接证据。新状态仅在同一GC互斥所有者内操作，不改变公开API/结构体ABI。
- 独立A级实验：上述手机日志和前后截图；菜单方向返回成功，不能以一次输入接受值代替切页成功。原版父页打开设置的 `SET_BUTTON_PAGE` 与子页返回页1共同证明真实父子关系。
- B级成熟项目：复核本地缓存 Kodi `DVDInputStreamBluray.cpp::UserInput/MouseMove/MouseClick/OnMenu`，revision `b2637ca499afe69f9d15c928809ffd6c42144250`，来源 `https://github.com/xbmc/xbmc/blob/b2637ca499afe69f9d15c928809ffd6c42144250/xbmc/cores/VideoPlayer/DVDInputStreams/DVDInputStreamBluray.cpp`；仍由libbluray执行导航，不能把Kodi直接调用等同本项目触摸适配已齐全。原P9 PR/官方文档/论坛研究继续适用，不新增上游提交；此轮是已有结构识别缺口，没有需要论文或新性能博客裁决的算法/性能主张，不重复网上检索。
- 方案比较：不改无法满足关闭/切换；原样Kodi鼠标API仍只命中当前页；全盘搜索同坐标或无条件ROOT会误跳/重播且丢状态。采用已批准窄适配：只记录真实用户激活引起的页切换和当时启用的父按钮，历史有界；当前页未命中时匹配最近实际父页，再沿既有可验证无副作用返回图执行作者命令，回父页后重新检查目标启用状态，原入口只关闭、其它入口再激活。
- 生命周期/风险：IG替换、菜单初始化/恢复、非关联跳转清记录；不保留已释放对象指针。方向和当前真实按钮优先；不改变BD-J静默回退、UO策略、媒体读数、菜单动画或直出。不同光盘作者脚本不能提前承诺全兼容；失败保留原有返回/鼠标结果，不伪造成功。无帧循环新增工作，只在菜单激活/换页/点击时做有界状态操作；包体只有两份MPV静态链接代码变化。
- 验证/回滚：新增无子页可选对象的父栏、同入口关闭/其它入口切换、键盘返回、实际访问证据缺失/禁用/歧义/多级/过期/新IG/动画与新输入测试，保留《豪斯医生》背景及《倩女幽魂》副本夹具。双ABI只重编libbluray/重链mpv，保留FFmpeg和JNI哈希。设备验收尚未完成不宣称修好；通过后原子提交/tag，不push。回滚本单元源码/两库至基线提交，已验收P9能力不撤回。

- 当前修复单元（2026-09-10 13:18 Asia/Shanghai）：用户在手机菜单卡住与电视黑底/入口遗漏/片头循环诊断后明确“修复”。guard `P9-MPV-MENU-LIVENESS-TV`；基线 `1ec569658157d1a9323b5c2ef00cb3468b876fca`，保护既有 `app/.cxx/` 35个文件。以下此前已验收状态是历史，不代表本次缺陷已修复。
- 本轮目标：暂停时HDMV菜单仍完成动画并可切换/关闭；TV底栏有原盘菜单入口；同次播放直出失败不循环重入；修正已捕获的MediaCodec flush/旧帧释放竞态，保留硬解、直出及既有作者菜单返回语义。仅MPV链及对应App接线，不扩展Exo/BD-J/网络缓存。
- 当前状态（2026-09-10 构建完成）：三处App/菜单缺陷及MediaCodec竞态修复代码已完成；菜单活性/原输入测试、480组并发释放/flush/close测试、19项Java输出策略测试通过。完整补丁链prepare、双ABI FFmpeg/MPV实际编译链接、ELF/资产校验通过；两个Debug包2m15s构建成功且包内三库逐项SHA一致。手机在构建期间断开，ADB设备列表持续为空，**未安装本候选、未完成真机验收，未提交/tag**。不能宣称两片实机已修好。原定时长超出后已停止研究/可选检查，当前剩余门槛是设备连接和实机。具体产物/命令/风险见下方本轮验证记录。
- 当前文件：新`mpv-discnav-poll.patch`、`mpv-mediacodec-embed-reset.patch`、`ffmpeg-mediacodec-output-serialization.patch`；TV Activity/layout、PlayerManager/MpvAutoOutputPolicy及Java测试；native构建/验证脚本、liveness/serialization源码抽取测试。临时派生源码只在`build/mpv-native`，版本锁/JNI/Exo不变。
- 唯一下一动作：手机重新连接后，用OEM安装助手安装下列Mobile arm64 Debug包，直接验证《倩女幽魂》暂停菜单切换；随后继续TV armv7两片跳转/背景回归，验收通过才guard finish提交/tag。不要重新研究或重跑已通过的构建。

- 当前优化单元（2026-09-09 23:11 Asia/Shanghai）：用户在两片日志诊断后明确“优化”，批准原始 ISO 字节层渐进读取/需求优先；guard `P9-MPV-ISO-PROGRESSIVE`，基线 `310f8feef5c0a05e5dae6c0a063453113214ea2c`，已验收菜单恢复 tag `recovery/P9-MPV-HOUSE-MENU-RETURN/20260909193413-310f8feef5c0`。下列早期状态属于历史阶段，以本文末尾“原盘渐进读取优化”记录为准。
- 本单元范围：`RemoteIsoSource.java`、`HttpRangeIsoSource.java`、`IsoPlaybackSession.java`、新增 `ProgressiveIsoPageCache.java`、对应 HTTP/渐进缓存测试及本文档；保护既有 `app/.cxx/` 35个文件。保持原生库、菜单交互、解码/渲染、BD-J静默回退和默认关闭不变。
- 当前状态（2026-09-10用户确认）：渐进缓存/HTTP续读/取消已实现，34项定向测试通过（新缓存13、原缓存13、HTTP8）；Mobile debug和Leanback Java编译通过，构建2m16s。APK `fc0b397af908989bf7e4ed6cefac6bdea491770d900af48d402e618a45543885` 已由OEM安装助手成功安装并启动。用户明确“速度好像改善了，打个tag;稍后有新需求”，按此接受当前观察场景并立即关闭可选验证；没有完成严格三轮同源A/B，不宣称达到30%或其他量化收益。原生库和已验收菜单交互未修改。
- 当前唯一下一动作：使用本guard原子提交并创建本地注释恢复tag，不push、不追加取证或构建；之后等待用户新需求。

- 目标：在 WebHTV 的 MPV ISO 播放路径实现 HDMV Blu-ray 菜单画面、按钮高亮、方向键、确认、返回、Popup、章节/设置/特别收录跳转、discontinuity 和 still frame。
- 接受标准：HDMV 菜单从远程/本地 Range ISO 入口可达并可操作；菜单跳转后音视频轨和时间线重建；普通 Blu-ray 最长标题、DVD、非 ISO、双 Surface OSD、硬解/软解和现有 Range 行为不回退。
- BD-J 产品边界：不启动 BD-J runtime，不处理 BD-J ARGB 菜单，不新增提示；遇到 BD-J 菜单时继续按现状选择最长标题播放。
- 用户决定：2026-09-06 明确“实现；遇到 BD-J 菜单时不用提示，就不处理菜单，跟现在一样即可”。
- Lane / task guard：`upstream` / `P9-MPV-HOUSE-MENU-RETURN`。
- 分支/HEAD：`feature-menu` / `4ded105fa7ea0e7aacd19a256919bd44d643ce96`；上一阶段菜单修复 `5b774430f155039d843f2e6a79aefc7df9f918cc`，恢复tag `recovery/P9-MPV-BLURAY-MENU-INPUT/20260909164438-5b774430f155`；其后仅独立移除docs忽略规则。
- 当前修复保护路径：未跟踪 `app/.cxx/`；延续现有任务源码修改，不接管其它工作。
- MPV 固定基线：`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`；构建框架 `99a60ad2141d5ace94453590903c2c6b9a0a2443`；libbluray 1.4.1 tarball SHA-256 `76b5dc40097f28dca4ebb009c98ed51321b2927453f75cc72cf74acd09b9f449`。
- 当前状态：Checkpoint 21 的历史访问记录、有界双路原始页预读与导航停顿计数已实现，37项定向Java测试、Mobile debug打包与Leanback Java编译通过；安装脚本确认包指纹变化并启动App。2026-09-08 17:54用户再次明确要求“先打个tag”，本次保存已通过上述检查的阶段性恢复点，**不等于P9全部验收通过**。新包真机开场/计数/历史效果和预览选集页退出仍待验证；这轮未改原生库，也未宣称菜单退出已修复。
- 本轮补充修复：ISO 探测返回 null 的异常、Leanback 抢先消费方向键、手机触屏/菜单控制入口、HDMV Top Menu 判别及菜单启动失败回退。设置默认关闭，DVD 不增加菜单能力。
- 回滚锚点：`831b70433e3dbdfd6f119c6036a3c8cf22d85ae4` / 本地 tag `recovery/P9-MPV-BLURAY-MENU-FIX/20260908-071735`；tag 仅包含已提交基线，不包含当前修复。回滚需成套恢复 MPV patch、JNI、App 接线和双 ABI assets。
- 本轮实证：手机已连接，trace包记录《倩女幽魂》章节页点击设置时按钮 #29 被激活但没有VM切页指令；从章节有效选择经向下，光盘的不可见自动按钮 #33 执行 `SET_BUTTON_PAGE` 返回主页面。**尚未读取按钮29的实际num_nav_cmds，不得仅据缺少执行日志断言它是装饰按钮或一定零指令**。当前候选未验收、未commit/tag，`app/.cxx/`仍保护。
- 最新用户结论（2026-09-09 10:17）：用户再次确认“几个需求全都没解决”，要求增加详细debug日志；上一轮仅《倩女幽魂》一次 `prev` 成功截图不代表功能通过。本任务仍未验收，未commit/tag。
- 已部署非trace候选：双ABI构建/资源验证/手机debug打包安装通过；arm64 libmpv SHA-256 `81b2816bb1212680a980b8b4fa4b7a877c786e63b39daa54b311ad17249ca4ea`，armv7 `2633e8decd8b8b2d7f3238fd11155a9af1911b83974af2d4afb12bfd51bad98a`，APK `dd83551c41f82a8bd8c055aa039b78879a43d7fc8396b337e74e5e9de39f7b50`。临时写文件trace已移除。设备曾断开，短片段验证未完成。
- 诊断候选已完成：新增仅debug的Java命令/耗时/1秒状态快照；MPV按线程上下文转发现有HDMV/GC日志（每API96行、每线程每秒600行上限），输入/事件/pump记录代数及状态。过滤/限额/线程隔离/关闭上下文的host测试、输入测试、still/clip边界测试均通过；双ABI构建/ELF检查、Mobile debug和Leanback Java编译通过。2026-09-09新候选APK SHA-256 `2b3d681103d9ddc65aec7e366ac2908d7f1267caa9707ce17cbcd1ace2f24ae0`。
- 设备诊断已经完成：安装成功且手机加载arm64指纹与本次构建一致。`debug-chain-setup-app.log`含完整章节成功/设置失败链路；`debug-chain-trailer-menu.log`含短片TITLE2→菜单TITLE0→光盘主动恢复第12页的指令，`debug-chain-menu-return.log`含原章节子页调用ROOT后进入第2页。底栏触发时横竖屏变化导致控件未命中，本项不算通过；菜单键结果不能代替底栏验收。
- 最新授权（2026-09-09 14:33恢复）：用户明确“继续，解决bug，直到实现我之前提的所有需求”，批准继续在libbluray内部定位和修复。当前guard精确新增 `third_party/mpv-player-jni/patches/libbluray-hdmv-input.patch`；不变更受保护的 `app/.cxx/`，不改解码/渲染/网络。14:35 ADB未发现设备，本地工作继续，真机验收待重新连接。
- 最新决定性证据：同一ISO远程Range仍有效；用固定libbluray 1.4.1在host只读取约12MiB菜单所需数据，复现章节→设置失败。`gc-boundary-host-session.log`证明第4页按钮29实际 `cmds=0, auto=0, up=down=left=right=29`，`_user_input`前后命令数均0，GC不向VM提交；主页面2章节按钮1为cmds=1，GC→VM接收返回0且正常切页。不是VM拒绝或渲染覆盖，是真正没有动作的主导航副本被触摸直接选中，使方向也困在自身。
- 已实现未真机验收（2026-09-09 16:23）：`libbluray-hdmv-input.patch`加入可达作者返回图、无动作副本不抢焦点、限时/限步的跨VM菜单切换、再次点击收起、HDMV Back及显式ROOT后沿作者路径退出恢复子页。设置页还含按PSR选择按钮的纯菜单auto节点，因此逐段执行原VM并重算路径，不直接跳过。MPV优先尝试作者Back，缓存包期间以最多约30Hz推进可见菜单动画，普通播放不新增轮询；新增键只用于HDMV，不改BD-J。
- 本地验证：`author-route-final-host.log`记录同ISO章节→设置(4→2→9→10)、设置再次点击经36→37→42的作者auto回主页面2、章节Back、特别收录预告TITLE2/PLAYLIST2无菜单播放后一次ROOT回TITLE0、作者恢复12后自动返回主页面2。`test_bluray_menu_routing.sh`通过真实源码抽取测试（副本不困焦点、切换/收起、多段VM、禁用/可见auto/未知寄存器/歧义拒绝、超时、新输入取消、动画等待）；`test_disc_navigation_input.sh`通过更新后的菜单命令/缓存30Hz节流测试。未等同于解码渲染或手机底栏验收。
- 设备/产物：16:17仍无ADB设备；手机仍是之前诊断包，新作者路径候选尚未双ABI打包安装。保护 `app/.cxx/`，现有guard不重启、不commit/tag。已从保存日志定位豪斯医生同资源，但尚未取其菜单定义。
- 阶段验收/保存授权（2026-09-09）：双ABI增量构建、ELF检查、Mobile debug APK构建通过，安装助手已在手机10CF6H1D2L0009S完成安装和启动。用户实测明确“我播放倩女幽魂是正常的”，并要求“先打个tag”；按此确认关闭该场景的可选验证，保存当前已验证的阶段性源码/补丁/双ABI产物。**用户同时确认豪斯医生菜单按钮仍不能关闭弹窗/切换，因此本恢复点不是P9全部完成，不代表豪斯医生通过。**
- 豪斯医生真机证据（17:01）：语言选择英语后主页面1，点击选集按钮2（cmds=4）进入预览页4。点击底部声音图标窗口(936,965)→盘(696,965)，实际命中一整条区域的按钮7（pos=0,792，cmds=1），唯一指令为NOP（group=0/sub=0/op=0/dst=src=0）。该按钮不是零指令自环副本，因此前阶段算法不处理；四向均引用按钮0。证据 `house-chapter-popup.png/.log`、`house-audio-switch-failed.log`。手机仍加载前阶段已tag版本。
- 17:35恢复后设备状态：ADB再次为空；豪斯医生旧远端URL的单次Range检查返回412，不能继续用过期URL反复尝试。新guard已补 `_button_has_no_action`，严格识别零命令/纯NOP（不把跳转、修改寄存器、缺失数组当无动作），并对这类大背景按钮输出当前页各按钮与有界指令摘要；现有自环副本修复保持通过，未猜测豪斯医生返回节点。真实源码抽取路由测试通过。改动在libbluray独立patch、对应测试及文档，`app/.cxx/`保护；未commit/tag。
- 18:22真机决定性证据：恢复ADB服务后连接10CF6H1D2L0009S，保存16:44豪斯医生有效输入及当前会话原盘缓存，安装NOP诊断包成功。`house-nop-audio-click.log`读取预览页4按钮6：不可见auto，四条指令为MOVE立即数到GPR、MOVE立即数到GPR、寄存器形式SET_BUTTON_PAGE、NOP，返回页1且选择按钮2；底部按钮7是覆盖五个图标的整条NOP背景，方向均指向预览按钮0。不是直接切页或逐个图标副本；旧识别条件漏掉这两层结构。
- 本轮验收与保存授权（2026-09-09）：用户真机明确确认“可以了，打个tag”，据此关闭豪斯医生本轮菜单修复的可选验证，立即原子提交并创建本地恢复tag，不push；不把此确认扩展成所有原盘/全部P9场景均通过。已完成真实源码抽取路由测试（含NOP合并背景、裁切边界、常量GPR返回、收起/切换及拒绝型案例）；同盘host证据 `house-background-final-host.log`确认选集→声音、再次声音收起、选集→字幕、Back；双ABI构建、ELF校验、Mobile debug打包、补丁反向应用检查及安装助手通过。手机10CF6H1D2L0009S已覆盖安装修复包并重新打开豪斯医生；保护`app/.cxx/`，未改解码、渲染、网络或BD-J边界。
- 最终产物SHA-256：独立libbluray补丁 `8a6087d5404b1eb4535d91d03a68739efccf8fdea83f69c33f48d935afc16076`；arm64 libmpv `eddfed3df1e1cea3b263a42778088ba3cf70f94dde6f08839357957084723948`；armv7 libmpv `371303839f5a5bd0a3724bd4fb84449d4ebae62a38cda111682c694598b86370`；已安装APK `13a7dfb6faf4567cbd95a90096c78ab8157d0e98be9903f79b8c14640cf55ce7`。证据目录 `/tmp/p9-menu-device-20260907.Vq7vyl/`，本guard以源码/测试/两ABI/本文档成套提交；回滚锚点为本guard基线 `4ded105fa7ea0e7aacd19a256919bd44d643ce96`。
- 唯一下一动作：交付本次原子提交与本地恢复tag；仅在后续新反馈下开启下一修复单元，不追加测试。

## 1. 授权、范围与排除项

这是已批准的实施阶段，不再等待设计授权。

### 2026-09-10 菜单活性与电视直出修复设计

- 用户授权：在上一轮明确说明三处缺陷、保留硬解/直出及初始flush异常验证边界后，用户明确“修复”。本轮沿用P9唯一文档；不重复此前HDMV总体调研，不引入新上游版本或合并候选。
- 现场证据：`/tmp/webhtv-tv32-disc-menu-20260910.OTLNXh/diagnosis.md`及原始日志；Mobile补充位于`/tmp/webhtv-mobile-disc-freeze-20260910.R0dn4D/`。新验证目录`/tmp/p9-menu-liveness-20260910.sMRWiR/`。日志有私人媒体URL，不进入仓库或对外摘录。
- Mobile `p-102igow-2`：OpenGL/GPU，12:48:51 noisy通知后暂停于7629ms；52.772最后一次成功切至page9，56.747起持续`button_effect_running`/`-12`，至13:05位置不变。首次49.940短暂动画拒绝早于暂停，不能倒置因果；无ANR、输入送达。TV `p-101lf66-3`/`p-101ok54-4`：MediaCodec在flush期间releaseOutputBuffer异常，回退GPU后8秒左右重新自动进入直出，再次重开ISO，repeat=false。
- 源码/提交身份（A级）：MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` + 当前P9/渲染补丁；FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18` + 本地MediaCodec补丁；mpv-android框架 `99a60ad2141d5ace94453590903c2c6b9a0a2443`；libbluray1.4.1源包SHA-256 `76b5dc40097f28dca4ebb009c98ed51321b2927453f75cc72cf74acd09b9f449`。全部保留基线，仅窄适配，不升级/新增公开JNI API。
- 确切调用链：`discnav.c::disc_nav_update`暂停时虽每50ms唤醒core，但仅在EOF/still请求一次媒体读包；`demux.c::read_packet`在导航中禁止预读，`nav_pump`只一次；`demux_disc.c::d_read_packet`才调用`STREAM_CTRL_NAV_POLL`；libbluray `_read_ext`的零字节读推进`GC_CTRL_NOP`/单调时钟动画。输入拒绝不调用demux_drive_nav，缓存状态查询不执行VM。33ms条件检查不是调度器。
- FFmpeg证据：`av_mediacodec_release_buffer_status`、`av_mediacodec_render_buffer_at_time`和`mediacodec_buffer_release`对serial检查和平台释放不在同一临界区；`mediacodec_dec_flush_codec`可在检查后并发flush；close在count先递减时也可能提前stop。引用计数已保证ctx活到最后buffer，适合把锁生命周期与ctx统一。MPV `VOCTRL_RESET`需清待提交帧和旧PTS，不能仅靠忽略错误。
- 官方契约（A级，2026-09-10经用户代理读取）：[Android 15 MediaCodec.java](https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/media/java/android/media/MediaCodec.java)，`flush()`文档明确已发出的buffer index失效、ownership归还codec，不能继续释放旧index；快照`MediaCodec.java`。libbluray固定`bluray.h`及`bluray.c::_read_ext`零字节poll为事件/VM已有契约。
- 成熟项目/维护讨论（B级）：沿用前述mpv PR #18080及已审阅菜单链，不重新抓取不变PR；复核缓存的[Kodi Bluray input](https://github.com/xbmc/xbmc/blob/b2637ca499afe69f9d15c928809ffd6c42144250/xbmc/cores/VideoPlayer/DVDInputStreams/DVDInputStreamBluray.cpp) `Read`/`BD_EVENT_IDLE`，由libbluray执行作者VM且保持导航循环。只作为分层对照，不能据此声称Kodi在所有暂停场景都有独立动画线程。此前VLC/Kodi输入及论坛类别研究仍有效。此次为已有状态机调度/互斥缺口，无新算法/性能提升主张；论文或重复博客不会决定下一动作，采用真实源码夹具与同设备复现作为独立验证。
- 方案比较：不改继续卡住/循环；原样上游或强制预读仍把动画耦合到媒体并可能提前推进盘VM；全局关闭直出牺牲原有能力且不能修Mobile菜单；窄适配采用已有core导航节拍请求**仅零字节事件处理**，由同一demux线程在媒体需求之外执行，不增加每tick媒体包。成功输入保留必要的一次读包；拒绝输入仍可请求事件泵，不伪造成功、不清作者animation guard、不取消系统暂停。TV入口复用现有共享openDiscMenu和TV焦点样式。直出失败标记只在新item/明确runtime重置时清除，自动策略不可重入失败模式。MediaCodec用每context互斥串行化serial检查/释放/flush/stop，最后引用释放时销毁，MPV reset清待交付帧。
- 兼容/性能：普通非导航文件不请求事件泵；20–30Hz仅可见HDMV菜单/既有idle场景，绝不busy-loop、主动恢复音频或打开预读。MediaCodec每帧增加短临界区，flush期间阻止旧索引调用；必须实测输出/CPU/前后台，无量化改善承诺。BD-J静默退回、开关默认关、原盘作者返回程序、双Surface和字幕/硬解选择不改变。
- 封闭路径：guard所列TV Activity/layout、PlayerManager/MpvAutoOutputPolicy及对应Java测试；`third_party/patches`、`third_party/mpv-player-jni/patches/tests`中的本次相关文件；native构建/验证脚本与构建说明；双ABI MPV assets；本文与索引。`build/mpv-native`和临时目录仅派生源码/测试证据，不提交，保护`app/.cxx/`。不改库锁版本、Exo、IJK、网络、BD-J。
- 验收：先源码抽取测试证明无媒体需求仍poll、拒绝输入可恢复、取消/非导航不poll；MediaCodec并发释放/flush/close夹具与旧serial/重复release；Java同项失败不可重入、新项恢复资格。双ABI重新编译受影响组件，完整ELF/命名空间检查，Mobile arm64与Leanback armv7 Debug。真机《倩女幽魂》《豪斯医生》菜单打开/关闭/切换、暂停切页与恢复、短片/正片返回；TV背景/底栏/遥控焦点、选正片持续播放和前后台，无自动片头循环。无候选通过记录前不宣称修复/提交完成。
- 回滚：本轮源码、补丁、构建引用、资产、App接线原子回滚至 `1ec569658157d1a9323b5c2ef00cb3468b876fca`，保留既有P9功能；只创建本地恢复tag，不push。当前风险为设备flush实现及节目切换时的独立packet overflow，若仍复现必须沿具体日志继续，不以回退循环修复替代黑底验收。

### 2026-09-10 本轮实现与验证记录（待设备验收）

- 状态：本轮源码、测试、双ABI原生库和两个Debug包已完成；2026-09-10 15:46 Asia/Shanghai恢复检查时，分支/HEAD与guard仍一致，`adb devices -l`为空。没有安装本候选，没有候选真机通过记录；guard保持active，未commit/tag。已停止额外研究和重复构建，下一步依赖手机重新连接并解锁。
- 实现：`mpv-discnav-poll.patch`在同一demux owner增加独立于媒体读包的事件处理请求，暂停/输入被动画暂拒时仍推进HDMV事件，不解除暂停、不打开预读；`ffmpeg-mediacodec-output-serialization.patch`串行化serial检查、输出释放、flush与stop；`mpv-mediacodec-embed-reset.patch`在VO reset清旧待交付帧/时序。TV补底栏入口、菜单状态监听及控制栏焦点保护；同item直出失败记忆跨rebuild保留，新item或显式设置变更才恢复资格。既有硬解、直出、BD-J静默回退和默认关闭保持。
- 测试：`test_disc_navigation_liveness.sh`与原`test_disc_navigation_input.sh`通过，覆盖暂停/队列已满而无媒体读取的事件推进、请求合并、取消/blocked、非导航/DVD、EOF/still及失败输入唤醒；`test_mediacodec_output_serialization.sh`通过480组并发释放/flush/close、timed/immediate/final、旧serial/重复释放及错误解锁，日志`mediacodec-test.log`。这些是源码抽取夹具，不替代Android平台codec/菜单实测。
- Java：`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.mpv.MpvAutoOutputPolicyTest :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac`通过，19项策略测试，`java-test-compile.log`记录`BUILD SUCCESSFUL in 3m 9s`；相关shell语法检查通过。首次Gradle缓存权限失败已按sandbox权限问题处理；未降低测试门槛。
- 原生：保留上节固定源/版本锁，NDK `29.0.14206865`、API24；完整补丁链prepare成功。首次prepare遇到旧派生`player/discnav.c`残留，已移至证据目录`candidate-discnav-before-prepare.c`保留，再执行完整prepare成功（`native-prepare-resumed.log`）。在buildscripts以`WEBHTV_ANDROID_API_LEVEL=24 WEBHTV_MPV_LIBCURL=enabled cores=8 LC_ALL=C LC_CTYPE=C`分别运行`bash buildall.sh -n --arch arm64 ffmpeg`、`arm64 mpv`、`armv7l ffmpeg`、`armv7l mpv`，四份`native-<arch>-<component>.log`均记录实际编译/链接成功。三新补丁在最终源码反向apply检查通过；JNI/API不变，`libplayer.so`未重编。
- 打包：`scripts/build_mpv_native.sh --stage-only --abi all --install`成功，`bash scripts/verify_mpv_native_assets.sh --require-elf`通过（`native-stage.log`、`native-verify.log`）；Mobile arm64与Leanback armv7 Debug同次构建成功，`apk-build.log`记录2m15s。两个APK内的`libmpv.so`、`libmvcodec.so`、`libplayer.so`分别解包SHA比对均与下表资产一致，不能把包内一致性表述成实机通过。
- 32位伴随产物：`libmvformat.so`也因本次FFmpeg链接改变，前后同为4,231,080 bytes；日志明确重编`http.o`，当前`http.c`相对上游仍只有原有代理Range offset的8行适配，本轮未修改HTTP补丁。字符串对比可见编译文件路径由`src/libavformat/http.c`变成`./src/libavformat/http.c`，伴随布局/地址变化；没有证明每个二进制差异都只是元数据，不作此宣称。保留同一锁定源码/补丁完整重编的库组，不手工换回旧库；原库`baseline-libmvformat-armv7.so`与对比`libmvformat-armv7-string-diff.txt`保存在证据目录，设备回归需覆盖实际原盘读取。
- 交接安全检查：checkpoint脚本通过（0 error；唯一warning为本任务已声明的原生资产/补丁变更）。首次guard检查发现32位APK构建新增34个未跟踪CMake文件不在源码范围内，未发现35个受保护初始文件内容漂移。核对`initial-dirty`及构建日志后，只把新增`app/.cxx/Debug/p104q5y4/armeabi-v7a`目录（含生成日志）和`tools/mobileArmeabi_v7aDebug/armeabi-v7a/compile_commands.json`移至证据目录的`cxx-generated-armv7`和`cxx-generated-armv7-compile_commands.json`，未删除数据、未扩展scope或放宽保护规则。因工作树已实际修正，接着只重试guard范围检查；不重复已通过的构建、测试或checkpoint检查。

证据根目录：`/tmp/p9-menu-liveness-20260910.sMRWiR/`。原始设备日志含私人URL，不提交；`before-install.png`因设备断开未成功生成，不能当作截图证据。

| 产物 | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` | `04935314d4497024ee0a3a25fa4f2a65e2a7869cdf1352b1649069e897e8d574` |
| `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk` | `26e9cc8996a3987baf8c722e4b1ff96dbc23412c1081f6b162f5bff669117e02` |
| arm64 `libmpv.so` | `4e27798c8846a0ed57e2e4ce67b9581511b9621cf4681d6ae6c8386ec0d0b74c` |
| arm64 `libmvcodec.so` | `405e7d5530c3e61e68f36fd1e50a01db804d4464ac826e7cad22391f793b72ec` |
| arm64 `libplayer.so`（不变） | `a63e7b34f5ccfdf00a5d8204401e57042f7f9366e4c1fd00538aafc84210c3a1` |
| armv7 `libmpv.so` | `f228e330c0eb2cb270908756a765817ba9f3ad90e1c29e209c158bdc30c2ac93` |
| armv7 `libmvcodec.so` | `42e8fd5cae02b1153390658a21fad1011552402005c6912e9567c08191ec98a2` |
| armv7 `libplayer.so`（不变） | `60f058ff16d70eec42d4bb3acf31253d7f7e507853adebf9f70ca011ce958977` |
| armv7 `libmvformat.so` | `5dc848be56424e03e87b4f968c8f3f458807c9ad76e3f5a3139741da4b3d9597` |
| armv7 `libmvformat.so`（基线） | `c08e555baa180398b84a73eb1e30307aa646893e264bb7ed7a0d418c42ccf56f` |
| `mpv-discnav-poll.patch` | `bff0e63e53d31cdf3a3995fd2540ae2c738b8baf6da76e90d7b7ee8e5a67dee8` |
| `mpv-mediacodec-embed-reset.patch` | `910153408594804031222589cc3dcb4c93e5b2bfe52d2a0034527933c494d3fa` |
| `ffmpeg-mediacodec-output-serialization.patch` | `b90680788e35741f12e261ff999eeb105f1c101b5c9655e3de78927d2b7cf3c2` |

剩余验收：设备`10CF6H1D2L0009S`（vivo V2453A/Android15 API35）接回后，用OEM安装助手覆盖安装Mobile候选，先验证《倩女幽魂》暂停菜单切页/关闭/恢复和播放往返；再覆盖TV候选验证两片背景、底栏入口/DPAD焦点、短片打断、正片持续播放与返回菜单。结合新时间/trace的`cache/webhtv-debug-log.txt`排除flush释放异常、自动回退重入、packet overflow，最后恢复手机候选。安装保留应用数据；无实机证据前不提交/tag，也不把不同VO/ABI互相替代。回滚仍为上节基线，保护`app/.cxx/`35个预存文件。

### 2026-09-09 子菜单输入适配决定

- 证据：固定libbluray源码（`graphics_controller.c::_mouse_move/_user_input`、`hdmv_vm.c::_set_button_page`，A级）及同ISO host重现（`gc-boundary-host-graph.log`，A级）；成熟Kodi输入对照沿用本文件已有源码研究，不重复网络检索。已读取光盘真实定义：章节页有不可见自动返回按钮，单条立即数 `SET_BUTTON_PAGE` 返回包含真实主导航按钮的页面；普通下一页自动按钮有可见图形，与返回节点可区分。无动作副本的位置和selected图形与主菜单真实按钮一致。
- 不改：继续选中self-loop零指令副本，三个交互要求失败；原样上游鼠标：同样直接选择副本，不能满足触屏；采用窄适配：仅对零指令、非auto、无外向方向引用的副本寻找**当前启用按钮图上可达**的不可见自动返回节点，只接受单条立即数切页命令和可验证的主导航图形对应。执行作者原有指令，不直接改页/寄存器。返回稳定页面后重新验证目标按钮；若作者已选中该按钮，则仅收起，否则激活另一个主导航按钮。
- Back复用作者返回路径，找不到可证明路径则保留原ROOT回退。显式ROOT后光盘若主动恢复子页，再沿作者返回路径退出；不重复ROOT，不影响FIRST PLAY、BD-J或全局UO策略。指针意图限时且在新输入/重置时取消，不能覆盖尚在运行的VM命令。
- 验证/回滚：host真实ISO章节→设置/收录/关闭、输入图夹具（自环、禁用、可见auto、歧义/损坏、延迟和取消）、短片ROOT、双ABI/ELF/App编译，随后真机底栏/豪斯医生验收。保持本任务原子可回滚；未通过真实交互不commit/tag。没有算法性能论文适用的新算法主张，不新增依赖/ABI命名空间或BD-J能力。

### 2026-09-09 豪斯医生合并背景/寄存器返回补充

- A级证据：手机同盘`house-nop-audio-click.log`完整按钮/指令；固定libbluray 1.4.1 `hdmv_vm.c::_read_setbuttonpage_reg/_set_button_page`确认高两位保留为标志、低12位取GPR，SET_BUTTON_PAGE在IG中终止当前指令程序。沿用前述Kodi/官方/issue调查；本次缺口由真实盘数据决定，不重复无关研究。
- 保持现状或原样上游：点击合并背景只能执行NOP，且先抢占预览按钮焦点；现有直接立即数返回识别不能证明寄存器返回目标。窄适配：仅接受最多16条、无条件/跳转的立即数MOVE准备、单个SET_BUTTON_PAGE与NOP；只解析本段程序内已确定的两个操作数，未知寄存器/其它副作用拒绝。实际执行仍由原HDMV VM完成，不自行写寄存器或切页。
- 合并背景只在作者方向图可达的隐藏返回节点给出的父页中匹配；至少两个真实可操作父页按钮的中心位于当前NOP背景内，且触点同时位于背景和唯一目标按钮的原始命中框，才适配点击。19:04真实盘边界日志证实父页按钮图像底部996–1000，而背景底部991（声音页背景还裁掉右侧边缘），因此不能要求完整图像框包含，改用中心避免图像边缘裁切导致误拒绝。保留已验证的逐图标副本匹配、方向键行为、超时/取消/动画/启用状态复核；空白区域、歧义、可见auto、未知指令不猜测。Back可使用同一已验证的父导航布局，无需鼠标位置。
- 此为用户已批准“收起/切换”同一需求的补充，不扩展到JVM/其它播放器或库升级。验收为真实选集收起、声音/字幕切换、Back及底栏菜单返回；风险是错误识别不透明菜单程序/重叠按钮，因此用拒绝型夹具、同盘host和真机验证。源码/测试/两ABI成套提交及回滚到本任务base HEAD，未验收不打完成tag。

范围：

- MPV disc navigation state/action、动态 duration/chapter/edition、菜单 OSD、slave demuxer 重开、interactive cache、still/discontinuity。
- WebHTV ISO callback 继续复用 Java Range 数据源；由 MPV/libbluray 菜单栈读取原始 ISO。
- App 仅在实际 HDMV 菜单激活时截获方向、确认、返回和菜单键。
- 两个 ARM ABI 的 coherent native/JNI 构建、ELF/资产校验和最小 App 编译。

明确排除：

- BD-J JVM、JAR、AWT/ARGB runtime、字体/XML 依赖和任何 BD-J 提示。
- AACS/BD+ 解密能力扩展。
- 整体升级 MPV、FFmpeg、libplacebo、mpv-android 或其它播放器。
- 无关渲染、音频、字幕、网络、配置和 UI 重构。

## 2. 决策问题与结论

问题：怎样在不整体升级 FongMi MPV、不中断 WebHTV Java Range ISO、且绝不启动 BD-J 的前提下，实现完整 HDMV 菜单？

假设：把上游完整 discnav 状态机移植为独立 MPV 补丁，并让 `webhtv-dvdiso` 回调提供原始可 seek ISO 字节，由 MPV 的 `stream_iso -> stream_bluray -> libbluray` 路径拥有菜单 VM，是最小完整方案。

反假设：继续让 `iso_dvd.cpp` 自己解复用最长 playlist，再只补 `bd_user_input()` 和一张菜单 bitmap 即可。该方案无法可靠处理 playlist/title hop、slave demuxer 重开、runtime track/list 更新、still、discontinuity 和播放器缓存，已被上游提交依赖关系否定。

结论：采用 WebHTV 适配方案；不整体升级，不做 Java 层自建 Blu-ray VM。

## 3. 修复前 WebHTV 调用链

- `IsoSessionManager.create()` 产生 `webhtv-dvdiso://<id>/longest`。
- `MpvPlayer` 用 `loadfile` 打开该 URL。
- `iso_dvd.cpp` 当前在 callback 内创建 libbluray、选择最长 playlist，并把已导航的 M2TS 字节交给 `+disc` demuxer。
- `mpv-stream-cb-disc-controls.patch` 只桥接 duration/current time/seek/chapter/language，没有 NAV command/state/overlay。
- `MpvPlayer` 已有透明 OSD Surface 生命周期，但没有 `disc-menu-active` 观察或 `discnav` 命令接口。
- Leanback `VideoActivity.dispatchKeyEvent()` 会在普通播放状态消费方向键，因此必须在它之前给活动菜单优先权。

## 4. 方案比较

| 方案 | 正确性 | 兼容/风险 | 决定 |
| --- | --- | --- | --- |
| 保持现状 | 普通最长标题稳定；没有菜单 | 不满足需求 | 拒绝 |
| 整体升级到 `FongMi/mpv@13eafa069366edb54606637b323b0d10efd05fa3` | 包含最终菜单链 | 同时引入 130 个分叉提交和渲染/音频/格式变化，覆盖本地补丁风险高 | 拒绝 |
| 只拣 `3a8b6995e925f2b1a6836c8e48d1fd210f4ed7ea` | 能生成部分 HDMV overlay | 缺少 demux reopen、OSD state、输入、cache、still 和后续跳转修复 | 拒绝 |
| 独立移植完整 discnav 链并适配 WebHTV raw Range callback | 上游状态机完整，保留当前二进制基线和 Range 所有权 | 补丁较大，必须做双 ABI、生命周期和真实 ISO 验证 | 采用 |

## 5. 上游提交台账

访问日期：2026-09-06；网络使用用户指定代理。仓库：`https://github.com/FongMi/mpv.git`。以下是本阶段实际判断的菜单链；DVD-only 代码只有在提供共用 discnav/still/cache 契约时才作为依赖吸收，产品验收仍以 HDMV 为准。

| 完整 commit ID | 作用 | P9 disposition |
| --- | --- | --- |
| `5db1db2852db3b43bfcc0fe1b611b4c30caa45cb` | runtime duration/chapter/edition 更新 | 实施依赖 |
| `e2b7a983fa22795b903fbcb266c275ad612c372b` | disc-nav action/state contract | 实施 |
| `fbd0e047b1c025cdfa8d517dcd98e3d7f029f33d` | `disc-menu` option | 实施，App 仅对 ISO 启用 |
| `b17feb1f7910fffb4e4dcb9dec603c30c8b7414b` | DVD menu implementation | HDMV 不直接采用；仅提取共用 lavf/disc contract（若最终补丁需要） |
| `3a8b6995e925f2b1a6836c8e48d1fd210f4ed7ea` | HDMV overlay/navigation | 实施核心 |
| `af9a11a8e5b0d06cd48a591ad22563b5ca3ed0f6` | disc hop 后重开 slave demuxer | 实施核心 |
| `e204c1cfd96835d0e24564e0d55c9b0a2206bb46` | synthetic Disc Menu edition | 实施 |
| `8d36904e053ba78c7242dae70ee11e5727250c87` | `OSDTYPE_DISC_MENU` | 实施 |
| `652ca81af9dad106d60e9cc5855cfd4ed7813c5b` | player disc-menu state/overlay | 实施 |
| `816eca1fef78077f406b5d30d12a88eb4a35b642` | runtime 新轨自动选择 | 实施依赖 |
| `5713c301603fec36352a49505e113bec1e568985` | player 响应动态 chapter/edition | 实施依赖 |
| `a5682eb2adf359b982c7b8227d34c9fe5cd28bd5` | `disc-menu-active` property | 实施 |
| `216e26c87130683724702ad4a75a6b7a48529b2d` | `discnav` command/default keys | 实施命令；App 自行映射 Android 键 |
| `4725c2e492e4fb405a32f6bec5fbcf79b2be3dd5` | partially seekable | 实施 |
| `70174945a4b7302613030d5877e572183dccdfad` | VM 与音轨/字幕选择同步 | 实施 |
| `c625405ddcdf9d40cdda2ffe3708865c105ed965` | BD-J ARGB overlay/menu | 明确排除 |
| `f0bc30aaf12c5e04e2a1f8caf19f8924b8d4d3cf` | interactive disc cache/read-ahead | 实施 |
| `5f192099531e9eaec11210d26766a434d9ded552` | Blu-ray angle 统一为 1-based | 实施兼容修正 |
| `fbcced5bf068afcb4bc0a2bf1dea80ba9eac2169` | cached disc state controls 加锁 | 实施线程安全修正 |
| `3897b3a579014b5bb293180f6e2a24fe3e6fb9a5` | player/discnav 共用 still contract | 提取共用部分 |
| `4084e7609fa06a9e40b36d689ba5cd80a6ca9c17` | Blu-ray still frame | 实施 |
| `af53a9dc4b378568f0760afdf7c5e7ec9de7ed30` | 以 `bd_read_ext()` 驱动 VM/event | 实施 |
| `dbe496e6bec0332fb169818fe0826e982f9a30e2` | drop buffers 时清 sticky AVIO EOF | 实施跳转修正 |
| `2c8d954d2111b045842110151e414edc49fecb71` | 仅有菜单支持时添加 menu edition | 实施 |
| `c7859fe5b62c35c1e9cdeda70fdec72e8c6cd1a6` | OSD image overlay 命名/所有权整理 | 采用最终语义 |
| `c9422ff88fafef2d4b7c28f6c614099ef5dbfde5` | highlight contract 整理 | 采用最终语义 |
| `bb19b0fc3696c31438adc8aba8ef1e3b6997e2fb` | 移除不可靠 popup/menu event 判定 | 实施最终语义 |
| `4a110f39cfaf062d88d84887294dbb4eca71fe28` | 每流 timeline generation | 实施跳转正确性 |
| `abbbffe0dd24f3e3221e41ebaa55bac2ef7965bb` | 保持 BD jump boundary 供 player resync | 实施 |
| `15750e12123b9cb39f44f5cb4432f4c264097deb` | still 前排空 queued events | 实施 |
| `0c87e46c83f31a8b20db0f1301134a6e28af0d3d` | buffered data 存在时延后 slave reopen | 实施 |
| `a47452aac02fca2226edf4cd306b09d4aacc58bf` | 未预告 reposition 视为 jump | 实施 |
| `6f6bedec72287d070e71a0b7d5567e2e889ea382` | BD time seek 原位 reset slave | 实施 |
| `79186bd167b295227988e14d42401dc49a024a47` | idle 时轮询 disc VM | 实施 |
| `795cd15438639e7aa1dc1b2519d5584a098f10b6` | demux 标记当前节目缺失轨 | 实施依赖 |
| `93451ef64f6267e176f5da05724282462e1ba21d` | disc program track 可达性 | 实施 |
| `c318236b8882af860f16f936225430ad053a2179` | 无 EL 时移除 enhancement pairing | 实施邻接回归修正 |

## 6. WebHTV 适配设计

1. `webhtv-dvdiso` callback 增加 raw ISO 模式，只负责 Java Range read/seek/size，不在 JNI 内抢先选择最长 playlist。
2. MPV `stream_iso` 识别该 callback scheme，并让 `stream_bluray` 明确通过 `stream_info_cb` 打开嵌套原始流，避免递归和 FFmpeg URL handler 绕行。
3. 只有当 libbluray 报告 `top_menu_supported` 且 `top_menu->bdj == 0` 时进入菜单；调用 `bd_play()` 从 FIRST PLAY 启动并让 `bd_read_ext()` 执行光盘自己的初始化，不在初始化完成前强跳 TOP MENU（Checkpoint 15 修正）。不注册 BD-J ARGB callback。BD-J top menu 直接走最长标题，不能仅凭整盘 `bdj_detected` 排除其中的 HDMV top menu。
4. 其它情况（包括 BD-J top menu、无 HDMV top menu、menu boot 失败）无提示回退 `bd_get_main_title()`/最长标题语义。
5. App 按默认关闭的 `playback_bluray_menu` 设置，在每次加载时同步 `disc-menu=yes/no` 与 ISO `/raw`/`/longest` 路由；非 ISO 探测返回 null 时保持普通播放。HDMV 菜单激活时优先转发 DPAD/ENTER/BACK/MENU；菜单曾激活的当前 ISO 正片中，MENU 仍可唤出 Popup。音量/媒体键和普通播放输入保留原行为。
6. 菜单 bitmap 继续走 MPV OSD renderer，复用现有 Android OSD Surface；不在 Java 额外复制整帧 bitmap。
7. 手机点击由 MPV `mouse` + `discnav mouse-click` 处理，坐标经原生 VO 的源/目标矩形映射；长按菜单画面打开导航面板。进入正片后可从播放控制栏的“蓝光原盘菜单”再次打开导航面板。

## 7. 证据

| 证据 | 等级 | 支持的结论 | WebHTV 影响 |
| --- | --- | --- | --- |
| FongMi MPV 上述完整 source commits 和最终 tree | A | 菜单是 stream/demux/player/OSD/input 联动链 | 不能只移植单 commit |
| libbluray 1.4.1 `bluray.h`、`overlay.h`、`keys.h` | A | HDMV 使用 RLE overlay、`bd_user_input()`、`bd_read_ext()`；BD-J 状态可区分 | 可明确禁用 BD-J 并选 HDMV top menu |
| mpv PR #18080 及合并链 | B | 上游维护者采用相同分层和 demux reopen/still/cache 设计 | 支持 adapted upstream 方案 |
| VLC/Kodi libbluray 集成源码 | B | 成熟播放器同样让 libbluray VM 拥有导航并把 overlay 接入播放器 | 反对 Java 层伪造菜单状态机 |
| 论文 | 不适用 | 本需求是既有 Blu-ray VM/API 工程集成，不含新的算法、调度或性能主张 | 不以论文替代源码/实测 |
| 博文/论坛 | C/D，仅作现场线索 | 不同盘的 HDMV/BD-J 外观无法仅凭截图判断 | 运行时必须读取 disc info |

## 8. 风险、验收与回滚

风险：

- 菜单补丁跨 stream/demux/player/OSD，编译通过不能证明真实盘跳转正确。
- 远程 ISO 菜单随机读取会放大 Range 延迟，interactive cache 必须与现有页面缓存共同验证。
- Surface direct 的菜单依赖 OSD Surface；快速 Surface 重建和字幕关闭场景必须保留菜单 OSD。
- 某些盘混合 HDMV first-play 与 BD-J top menu；P9 以 top menu 类型为准，BD-J top menu 直接回退最长标题。

最小自动门槛：

- 独立补丁按构建脚本顺序干净应用。
- MPV native 双 ABI 编译；`libplayer.so` 双 ABI 重建。
- `verify_mpv_native_assets.sh --require-elf` 通过，无 `libav*`/`libsw*` namespace 回退。
- Mobile arm64 与 Leanback arm64/armv7 Java/资源编译通过。

设备验收：

- 至少一份确认的 HDMV ISO：菜单背景/视频、按钮高亮、四方向、确认、返回、Popup、章节/设置/特别收录跳转。
- 菜单与正片间反复切换，含 still、seek、音轨/字幕选择、Surface 前后台和退出重开。
- 一份 BD-J 菜单盘：无新增提示，仍播放最长标题，不进入菜单。
- 普通 Blu-ray、DVD 和非 ISO MPV 回归。

回滚：revert P9 原子提交并恢复同一提交前的 patch、JNI、App 接线和双 ABI assets；不单独保留新 `libmpv.so` 或 `libplayer.so`。

## Checkpoint 1：2026-09-06 方案冻结

- 完成：任务 ID、用户授权、BD-J 无提示边界、基线、替代方案、提交台账、适配架构和验收矩阵已落盘。
- Source identities：WebHTV `966b3b6747ece447c2f34f7787df7c6af572baa0`；MPV baseline `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`；FongMi review head `13eafa069366edb54606637b323b0d10efd05fa3`。
- Files changed：仅本任务文档和恢复索引。
- Validation：待补丁生成后执行；当前没有把研究结论误记为构建/设备结果。
- Unresolved：尚无任务内真实 HDMV/BD-J 样片设备结果。
- Rollback anchor：`966b3b6747ece447c2f34f7787df7c6af572baa0`。
- Next action：在隔离 MPV 源码副本中生成 adapted HDMV patch，并验证它可与现有 WebHTV patch 序列共同应用。

## Checkpoint 2：2026-09-07 适配补丁与 App 接线完成

- 完成：将隔离仓库生成的 `/tmp/p9-final-source.patch`（相对 MPV `p9-local`）替换为生产 `third_party/patches/mpv-discnav.patch`；补丁覆盖 demux/cache、discnav 命令、HDMV YUV/RLE overlay、OSD、slave demux reopen、still/discontinuity、动态 chapter/edition 和 Blu-ray stream callback。
- 完成：`MpvPlayer` 设置 `disc-menu=yes`、观察 `disc-menu-active`、为 ISO 请求 OSD Surface，并暴露 `isDiscMenuActive()`/`sendDiscNav()`；`PlaybackActivity` 仅在 HDMV 菜单激活时转发 DPAD/ENTER/BACK/MENU。
- 完成：`scripts/build_mpv_native.sh` 与 `scripts/verify_mpv_native_assets.sh` 增加补丁应用和 `disc-menu-active`/`discnav` marker 校验。
- BD-J 边界：`stream_bluray.c` 根据 `BLURAY_DISC_INFO.bdj_detected` 静默回到 `BLURAY_DEFAULT_TITLE`；未注册 `bd_register_argb_overlay_proc`，未启动 JVM/JAR/BD-J runtime。PG 字幕不触发菜单激活。
- 取舍：未吸收 BD-J 提交 `c625405ddcdf9d40cdda2ffe3708865c105ed965`、DVD-Audio 代码、会删除 WebHTV 本地 DOVI/Vulkan/OSD/proxy/live/albumart 行为的整棵父树，以及 `4a110f39cfaf062d88d84887294dbb4eca71fe28`/`70174945a4b7302613030d5877e572183dccdfad` 的不兼容完整父树；仅保留其 HDMV 所需契约并作 WebHTV 适配。
- 验证：完整现有 WebHTV MPV patch chain 加 P9 补丁在固定基线应用成功；临时树 `git diff --check` 通过；`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --offline` 通过；生产脚本 `bash -n` 和 Task Guard `check` 通过。
- 未完成：MPV Meson 探针因环境缺少 `libplacebo` 失败；当前环境无可用 Android NDK/clang，因此双 ABI native、ELF/asset、`libplayer.so` 重建和真实 HDMV/BD-J 设备验收未执行，不能视为通过。
- Files changed：`third_party/patches/mpv-discnav.patch`、`app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java`、`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`、`scripts/build_mpv_native.sh`、`scripts/verify_mpv_native_assets.sh`、本任务文档。
- Rollback anchor：`966b3b6747ece447c2f34f7787df7c6af572baa0`。
- 唯一下一动作：具备依赖时执行一次双 ABI native 构建和资产校验；否则以当前未完成 native 验证的状态进入用户可见交接，不虚报设备支持。

## Checkpoint 3：2026-09-07 原始 ISO 接管修复与开关

- 完成：播放设置在 Mobile/Leanback 加入默认关闭的“蓝光原盘菜单”开关，首选项为 `playback_bluray_menu`，并纳入备份设置；仅 MPV 播放器显示。
- 完成：`MpvPlayer` 按开关传递 `disc-menu=yes/no`。开启时把 `webhtv-dvdiso://<id>/longest` 改为 `/raw`，关闭时保留 `/longest` 兼容路径。
- 完成：JNI `iso_dvd.cpp` 增加 RAW callback 模式，只提供 Java Range 原始字节，不创建 libbluray、不扫描标题、不选择 playlist；原有关闭开关的 Blu-ray/DVD 最长标题路径未改动。
- 完成：MPV disc patch 让 `stream_iso` 识别 WebHTV raw scheme，并让 Blu-ray/DVD nested stream 通过 `stream_info_cb` 打开，避免 FFmpeg URL handler 绕过 Java Range callback；固定基线上的 patch chain 已重新应用通过。
- DVD 结论：DVD-Video 确有 VMG/VTS 菜单，但当前 WebHTV 仍由 JNI 直接选择最长标题，未接入 DVD overlay/按钮/导航；本开关不宣称支持 DVD 菜单。
- 验证：`bash ./gradlew --offline :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 成功；固定 MPV 基线先应用 `mpv-stream-cb-disc-controls.patch` 再应用当前 `mpv-discnav.patch` 成功；`bash .codex/scripts/task_guard.sh check` 成功。第一次 `./gradlew` 无执行权限，已按仓库规则改用 `bash ./gradlew`，不属于代码失败。
- 未完成：当前环境仍无 Android NDK/libplacebo/native 构建依赖，未重建 `libplayer.so`/`libmpv.so`，也未安装新 APK 做真实 HDMV/BD-J 设备验证；不能把源码接线等同于实机菜单已验收。
- 当前文件：App 设置/资源、`MpvPlayer.java`、`iso_dvd.cpp`、`mpv-discnav.patch`、本任务文档。
- 回滚锚点：`831b70433e3dbdfd6f119c6036a3c8cf22d85ae4`（本修复前）；保护未跟踪 `app/.cxx/`。
- 唯一下一动作：在具备 Android NDK、libplacebo 和确认的 HDMV/BD-J 样片时，执行一次双 ABI native 构建并安装到设备，验证开关开/关两条日志与菜单画面；若仍缺依赖，保留“源码完成、native/实机未验证”状态。

## Checkpoint 4：2026-09-07 双 ABI、自动验收与 debug 包完成

- 环境恢复：NDK `29.0.14206865`、API 24、本工作区 `build/mpv-native` 缓存可用；Checkpoint 2/3 的“缺 NDK/native 依赖”已不再成立。`feature-menu` HEAD 仍为 `831b70433e3dbdfd6f119c6036a3c8cf22d85ae4`，本轮尚未提交。
- 补齐修复：`MpvDiscMenuPolicy` 使 ISO 路由对 null 安全、仅改写本协议末尾 `/longest`；`MpvPlayer` 每次加载同步开关；Leanback 在普通播放键处理之前路由菜单；只消费实际菜单按键，不吞音量/媒体键的抬起事件。
- 手机入口：新增 `DiscMenuDialog` 导航面板和控制栏入口；菜单画面支持触屏定位/点击，长按打开方向、确认、主菜单、Popup、菜单返回面板。布局/字符串同时经过 Mobile/Leanback 编译。
- 原生修正：检查实际 top menu 类型，菜单启动失败静默退回正片；RAW callback 在镜像 EOF 返回 0 并约束最后一段读取长度；删除会把通用 `stream_cb` 限制为仅一个 scheme 的 protocol marker，保留其它 callback 的原有协议能力。
- 构建：armeabi-v7a 全依赖首次构建通过；最后使用相同补丁源码分别增量重编两个 ABI 的 MPV，并 `--stage-only --install` 安装产物；`build_mpv_player_jni.sh --abi all --install` 在 RAW EOF 修复后重编两个 ABI 的 JNI。没有更新上游锁定版本。
- 自动验收：`MpvDiscMenuPolicyTest` **6 tests / 0 failures / 0 errors**；Mobile arm64、Leanback arm64 与 armeabi-v7a Java/资源编译通过；`verify_mpv_native_assets.sh --require-elf` 双 ABI 通过；固定 MPV 基线加 callback-controls/discnav 补丁干净应用，生成的 `stream_bluray.c` / `stream_cb.c` 与实际编译源码一致。
- 打包：`bash ./gradlew --offline --init-script /tmp/p9-menu-gradle-staging.gradle :app:assembleMobileArm64_v8aDebug` 成功（1m33s）。临时 init script 仅将 App CMake 中间文件重定位到 `build/mpv-native/app-cxx`，不改生产 Gradle 文件，也不接管原有 `app/.cxx/`。
- APK：`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`；包名 `com.fongmi.android.tv`；versionCode 560 / versionName 5.6.0；大小 168019744 bytes；SHA-256 `0625bac238d0a9bd806330b64ef9a48d267aecec3f4a8e04b8e0afe8eaa74d1e`。已解包逐字节比较全部 arm64 MPV 库，确认装入的是本次候选资产。
- 输入哈希：lock `a009a6dd9066eacd8547383f93cc7dce2956fff7d6d338bd886be75b9e4ae159`；discnav patch `17efb68ea38a08746d462a146fdc824b1df33a0407d8af4d4cbd2dd52dd4e567`；JNI ISO source `7335df9abf1cf0988f48415440ed8afd3d8b53b4aaa7051f45a83aa45734a17d`。收尾检查发现补丁中 5 行空白 context 的空格，已规范化为空行；只调整 patch 文本形式，源码一致性用重新应用后比较确认，不重复 native/APK 构建。

| 资产 | SHA-256 |
| --- | --- |
| arm64 `libmpv.so` | `497cb7e9549323f9e9bb8278b277edebb09651a6258f32b6a6a58b66815286a1` |
| arm64 `libplayer.so` | `a63e7b34f5ccfdf00a5d8204401e57042f7f9366e4c1fd00538aafc84210c3a1` |
| armv7 `libmpv.so` | `7acfbb728c3530fc5555ab5f51a760032d762edc66548ec4f6bee418c542afec` |
| armv7 `libplayer.so` | `60f058ff16d70eec42d4bb3acf31253d7f7e507853adebf9f70ca011ce958977` |

- 完整临时证据：`/tmp/p9-menu-native-armv7.log`、`/tmp/p9-menu-mpv-arm64-final.log`、`/tmp/p9-menu-mpv-armv7-final.log`、`/tmp/p9-menu-jni-final.log`、`/tmp/p9-menu-assets-final.log`、`/tmp/p9-menu-native-sha256.txt`、`/tmp/p9-menu-app-tests.log`、`/tmp/p9-menu-apk-build.log`。Junit XML 位于 `app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-androidx.media3.mpvplayer.MpvDiscMenuPolicyTest.xml`。
- 未完成/风险：截至 21:24，`adb devices -l` 无设备，mDNS 也未发现无线调试服务；未安装本 APK、未运行真实 HDMV/BD-J/DVD、菜单跳转和 Surface 生命周期场景。编译/资产通过不是菜单画面与操作通过，不做原子提交或 recovery tag。
- DVD 边界：DVD-Video 确有菜单，但本实现不提供 DVD 菜单；开关关闭保留 JNI 最长标题路径，开启后的 DVD 分支也选最长标题。BD-J 不提示、不启用菜单，继续普通正片播放。
- 回滚：现有 HEAD `831b70433e3dbdfd6f119c6036a3c8cf22d85ae4` 是成套源码/二进制恢复基线；不要单独混回某一个 ABI 或 `libplayer.so`。
- 唯一下一动作：手机连接后，用 Android 安装辅助脚本安装上述 APK，测试默认关闭、开启后的 HDMV 菜单操作和 BD-J 静默回退；不重新做已完成的依赖研究、构建或自动检查。

## Checkpoint 5：2026-09-07 真机发现协议注册冲突

- 设备：`10CF6H1D2L0009S` / vivo V2453A / Android 15 API 35；安装更新时间 21:39:27；设备 base.apk SHA-256 为 `0625bac238d0a9bd806330b64ef9a48d267aecec3f4a8e04b8e0afe8eaa74d1e`，与本次测试包完全相同，不是旧包混装。
- 证据：`/tmp/p9-menu-device-20260907.Vq7vyl/history.log` 保存了 21:41–21:43 的七次失败；`live.log` 继续捕获 21:51 的多次重播，均先报 `iso protocol registration failed: invalid parameter`，随后 `/raw` 报 `No protocol handler found`。未进入 libbluray 菜单判断，不是 BD-J 不支持造成的。
- 根因：`stream_iso.c::stream_info_iso.protocols` 声明 `webhtv-dvdiso`，而 JNI 又通过 `mpv_stream_cb_add_ro()` 注册同名 callback；`player/client.c` 明确用 `stream_has_proto()` 拒绝覆盖内置协议。ISO 在此只是探测器，不应抢占 WebHTV 底层 callback 的注册名。
- 最小修复：仅在 `stream_has_proto()` 检查中排除 ISO 探测器对 `webhtv-dvdiso` 的占名，不放宽 file/https 等真正内置传输协议保护、不放宽重复注册、不改其它 callback API；保留现有 raw/nested callback 路由及开关关闭的最长标题行为。
- 验证计划：新增手机可运行的 `third_party/mpv-player-jni/tests/disc_protocol_test.c`，覆盖 ISO 注册、重复注册、其它 custom callback、file/https 保留名和空 callback。先证明当前库失败，再验证候选库通过；只增量重编 MPV 两 ABI，不重建未改动的 FFmpeg/libplacebo/JNI。
- 当前边界：日志持续抓取，未清日志、未改用户 MPV 配置。`http-allow-redirect` 和 `custombuttons.json` 的配置报错不是当前 ISO 入口失败的根因，不在本修复中清理。
- 已验证：旧 arm64 库的 ISO callback 首次注册返回 `-4`，其余 5 项通过；修复后同一手机、同一测试 6 项全通过，重复注册及 file/https 内置协议覆盖仍被拒绝。日志：`protocol-before.log` / `protocol-after.log`，位于上述证据目录。手机独立测试目录 `/data/local/tmp/webhtv-p9-protocol-20260907/` 不改 App 数据或配置。
- 构建：仅增量重编 MPV arm64、armv7，未重建未改动的 FFmpeg/libplacebo/JNI；双 ABI staging 和 `verify_mpv_native_assets.sh --require-elf` 通过。固定基线重新应用 callback-controls/discnav 补丁后，`stream.c`、`stream_bluray.c`、`stream_cb.c`、`stream_dvdnav.c`、`stream_iso.c` 与实际编译源码全部一致。
- 候选 SHA-256：arm64 `libmpv.so` 为 `cda2282c89ea2b2c07c0ac7fec676a9f3d34986cfbef2d14a55ee6e197a924ed`；armv7 `libmpv.so` 为 `80786d019f6533279a84447401c29e1b553ff8709d4329f980d11224daf82aca`；discnav patch 为 `1fb616f4b60bc3a1f073f340a32ae9d4a808a637ba6cb6152780076373443856`。
- 22:30 正在运行一次增量 `:app:assembleMobileArm64_v8aDebug`，继续用 `/tmp/p9-menu-gradle-staging.gradle` 保护 `app/.cxx/`。尚未安装本轮修复，不得沿用 Checkpoint 4 的 APK 哈希作为新候选。
- 下一动作：核对新 APK 内候选库并安装，复播此前失败的原盘，确认已进入实际 ISO 读取及菜单/静默回退路径。

## Checkpoint 6：2026-09-07 修复包安装与实际原盘复播

- 新 APK：`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`；SHA-256 `686b514cb59b10d15c19318c0daab4d7e793bff0c5bb7853068e7396296588ec`。增量构建用时 1m21s；解包 `libmpv.so` 与 Checkpoint 5 arm64 候选逐字节相同。
- 安装：OEM 辅助脚本成功处理风险复选框和继续安装按钮，覆盖安装保留用户数据，随后启动。设备 base.apk 哈希与新包一致，App PID `8672`。证据 `install-protocol-fix.log`、`after-install.xml` / `.png`。
- 日志：旧采集会话在设备连接中断后结束；已保留原文件并从最后时间续采至 `live-protocol-fix.log`，未清 logcat。用户 `playback_bluray_menu=true`，未修改 MPV 配置文件。
- 复播《背水一战》：23:32:57 手机日志确认 `iso-native opened raw Blu-ray ISO callback session=1001 size=51435929600`；后续持续 Range 206，正片画面及约 188 秒进度可见。原有协议注册错误及 `/raw` 无 handler 未再出现，入口修复有效。
- 未通过项：同次播放有频繁缓冲/音频 underrun，后续出现 `mpegts: Packet corrupt`、`dca: Failed to decode block code(s)`、PTS discontinuity 和音画不同步告警。尚不能归因给片源或新实现，不能报告“播放完全正常”。App 播放性能的正常日志级别为 warn，该次日志不能证明菜单类型。
- 下一动作：同片菜单开关开/关对照并恢复原状态；继续以实际 HDMV 菜单画面和操作作为 P9 验收条件。

## Checkpoint 7：2026-09-08 首次真实 HDMV 菜单及开关对照

- 《背水一战》关菜单对照：session `1003`，`iso-native opened Blu-ray image ... playlist=1 titles=46 durationMs=6433260 chapters=17`，确认走旧 JNI 最长标题路径。00:21:07 同样出现 `dca: Residual encoded channels are present without core`；持续有缓冲，故这两项不是开启菜单后独有。开启路径后期的 Packet corrupt / PTS 跳变仍不可由此单次对照完全归因。
- 《哈利·波特3》带 BDJ 菜单修改标記的原盘：session `1002` `/raw` 打开成功且有 MediaCodec 持续运行，未复现入口错误；未将文件名当作已验证的 BD-J 类型证据。
- 《超脱》HDMV：session `1004`，原始 ISO 大小 `21159477248` bytes；00:58:01 明确 `bdnav: cfg_title=-1 hdmv_mode=1`、`HDMV entered; current title=0`、`Blu-ray successfully opened`。`detachment-menu-now.png` 显示 PLAY / SET-UP / SCENE INDEX 原盘菜单及高亮。
- 交互取证：触摸 SET-UP 打开音轨/字幕设置页（`detachment-setup-confirm.png`），点圆形 X 关闭（`detachment-audio-closed.png`）；长按画面出现方向、确认、主菜单、弹出菜单、菜单返回控制面板（`detachment-controls.png`），方向输入日志返回成功；关闭面板后触摸 SCENE INDEX，显示章节 1–4 缩略图页（`detachment-chapter-page.png`）。仅按键返回 0 不作为页面跳转成功证据，以截图为准。
- 日志续采：原采集在 01:14:21 结束，新增 `live-hdmv-test.log` 从该时间续采且只监听 App PID `8672`；不清历史日志。
- 临时状态：菜单开关已由关闭对照恢复 `true`；为了得到菜单模式证据，播放性能里的“详细日志”由正常改为详细。恢复时须设回正常并移除本次增加的 `mpv_verbose_log` override，保留用户原有 `mpv_render` override；不要重置整个播放性能或修改 `mpv.conf`。
- 唯一下一动作：确认章节 2 启动正片与 Popup 返回，随后恢复临时日志状态，按实际剩余风险决定是否达到提交验收条件。

## Checkpoint 8：2026-09-08 菜单到正片、Popup 与静默回退实测

- 章节选择：01:20:40 点击章节页第 2 个缩略图，libbluray title 切至 13、playlist 切至 0；01:20:42 `discontinuity 6->8, reopening slave` / `reopening slave demuxer`，菜单状态关闭，截图 `detachment-chapter-two.png` 已是正片。记录“章节页选项启动正片”已验证，不把启动时 chapter=0 误写成已精确验证任意章节时间点。
- Popup：01:28:25 `disc navigation action=popup result=0`、菜单 event 30=1；`detachment-popup-return.png` 显示正片上 MAIN MENU / SET-UP / SCENE INDEX 弹出菜单。该盘 Popup 会自行超时关闭；不把超时后发送的确认键当作已验证的返回主菜单动作。
- 无菜单回退：01:34:48《哈利·波特3》session `1005` 打开同一 4K ISO；01:34:50 `cfg_title=-2 hdmv_mode=0`；01:34:53 AudioTrack 7.1，01:34:58 `VO: [gpu-next] 3840x2160 mediacodec` 且首帧，未弹 BD-J 提示。`bdj-verbose-playing.png` / `live-hdmv-test.log` 为证据。该模式输出证明未启用 HDMV 菜单；实际 BD-J 类型仍以盘元数据而非文件名为准。
- 恢复用户状态：退出测试播放后备份最新 SharedPreferences，唯一文本差异是 `perf_mpv_verbose_log: true -> false` 和移除本轮新增的 `mpv_verbose_log` override；保留 `mpv_render`、`playback_bluray_menu=true` 及其余内容。首次 `adb exec-in` 临时文件传输得到零字节，校验失败后在 App 未启动的状态立即从私有备份恢复并逐字节确认；改用 adb push、复制到私有临时文件并先逐字节校验，再替换成功。重新启动后只读确认这两项恢复正确。
- 隐私/清理：未修改 `mpv.conf`、未清日志或 App 数据。仅移除本任务产生的设备公共临时传输文件；App 私有备份 `shared_prefs/com.fongmi.android.tv_preferences.xml.p9-log-backup` 和本机权限 0600 的恢复证据仍在，均不进 Git。
- 日志继续：`live-user-followup.log` 以 UID `10464` 过滤本 App，避免 App 重启后旧 PID 过滤丢失日志；回到 App 首页交还操作。完整证据目录仍为 `/tmp/p9-menu-device-20260907.Vq7vyl/`。
- 本轮结论：统一 ISO 入口致命错误已修复、已换包，真实 HDMV 核心交互链路已证实；尚未验证完整 DVD/所有 HDMV still/任意章节定位，且《背水一战》后期时间戳/损坏包需同一片段比较。保持 P9 未收尾，不以编译或一次菜单成功宣称全量无回归。
- 唯一下一动作：仅对《背水一战》的同一异常正片时间段做菜单开/关对照，区分现有音频/网络问题和新 RAW 路径风险。

## Checkpoint 9：2026-09-08 最近观看复播失败与原生崩溃修复

- 用户反例取代 Checkpoint 8 的下一动作：最近观看中的《超脱》《幽灵公主》均不正常；不再把曾显示菜单或首帧当作完成。
- 证据（同一 vivo/API35，UID 10464）：`recent-user-failures-0358.log` / `recent-user-0358.png` / `live-user-0400.log` / `recent-native-crash.log`，均在 `/tmp/p9-menu-device-20260907.Vq7vyl/`，不清设备日志，不输出分享直链/凭据。
- 03:57《超脱》session 1001，ISO 21159477248 bytes；file-loaded 后立即 seek，接着 SPS/PPS 缺失、no frame，最终连接超时。只读历史副本 position=821112、duration=794960；Mobile `onPrepare -> setPosition` 无菜单判断，`onTimeChanged/updatePlaybackHistoryPosition` 把不同 playlist 时间线写成单一电影进度。此为直接调用链证据，不据此解释全部原生故障。
- 03:55 和 04:13 SIGABRT：`FORTIFY: pthread_mutex_lock called on a destroyed mutex`。用已安装库对应的 arm64 未剥离产物 `llvm-addr2line` 解析 0x695e60 / 0x692678 / 0x692508 / 0x69e914 / 0x69dc44，依次为 `demux_flush / demux_shutdown / demux_free / reopen_slave / d_read_packet`。
- 确认根因：`reopen_slave` 释放 `p->slave` 后，遇到短暂 event-only/EOF 从 peek 分支提前返回，但未清空指针，下一次 reopen/close 重复释放。另一个初始化缺口是初次 probe 后 discontinuity 基线仍为 0，首轮读取误将初始化 playlist 事件当成跳转，丢弃首个短 clip。
- 04:14 崩溃后重新点击《幽灵公主》，这次是分享页 HTTPS（urlLen=35）直接交给 MPV，不是 ISO callback；需独立检查配置/推送解析恢复，不能把该次 unknown_format 当成 HDMV 解码证据。原先 02:08 的 raw ISO unknown_format 仍仅作旧反例。
- 局部设计沿用第 7 节 libbluray/上游导航契约，不新增 VM 或扩大 DVD/BD-J 能力：保留现状不满足复播；对所有 ISO 禁续播会破坏 BD-J/普通标题；采用原生只读 `disc-nav-active`（区别于瞬时 `disc-menu-active`），RAW ISO 延迟到 file-loaded 判别后决定是否恢复进度。实际 HDMV 会话不恢复/覆盖单一历史时间线，非导航回退保持续播。历史不删除、不迁移，菜单开关仍默认关。
- 本轮源码改动：失败立即置空 slave；初次读取冻结导航 generation；App 不给待判别 RAW ISO 嵌入 loadfile start/提前 seek；实际导航会话冻结历史时间/片尾跳转，Mobile/Leanback 一致。新单测覆盖待判别、HDMV、BD-J/无菜单回退和普通媒体；C fixture 提取真实 `reopen_slave` 函数覆盖 event-only 重试/关闭/探测失败/恢复。
- 打包完整性发现：缓存中 `player/discnav.c` 为未跟踪新文件，旧持久补丁只引用它，未收录其内容；本轮将该文件逐字纳入 `mpv-discnav.patch`，防止干净构建依赖残留缓存。
- 验证：真实 `reopen_slave` fixture 对旧 `/tmp/p9-protocol-apply.oJenrV` 在“失败后必须置空”处失败；对当前源码 event-only 三次重试、关闭、探测失败及恢复全部通过。干净基线先应用 stream-cb patch 再应用菜单 patch 成功，`discnav.c`、`demux_disc.c` 与编译源码逐字一致，目录 `/tmp/p9-resume-apply.VmeyRn`。
- Java：`:app:testMobileArm64_v8aDebugUnitTest --tests androidx.media3.mpvplayer.MpvDiscMenuPolicyTest` 10/10，通过；同次 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过（44s）。日志 `resume-app-tests.log`。native 第一次被沙箱 `sysctl ... Operation not permitted` 阻止，实际未开始编译；获准后重跑两个 ABI，仅编译 MPV，均成功，日志 `resume-mpv-arm64-built.log` / `resume-mpv-armv7-built.log`。
- 产物：双 ABI stage/安装 assets 与 ELF 校验通过，日志 `resume-native-stage.log` / `resume-native-assets.log`。patch SHA-256 `b91aee65650c4e6cb5e3671b3cad8e5e90121a379fee86a52d44e143102cc5cb`；arm64 libmpv `b15d07c4bf201fa7560289d05c2bf9fe44cff7d367ed8903668d7fec9adefda3`；armv7 libmpv `37cc016ef993800cc7903dd69a22a2c12198e303530deed549af4e8669fe67e2`。JNI 未变，不重复重编或重跑已通过的注册测试。
- 分享页错误的代码解释：进程死亡后直接恢复播放/历史 Activity，不经过 HomeActivity.initConfig；VodConfig sites 为空，SiteApi 的空 push_agent 回退将分享页作为直接媒体。原生崩溃是本轮必须修复的触发条件；全局配置初始化未改，先验证正常启动后最近观看可恢复，不能将此解释误写为已修复全局进程恢复。
- 授权/范围/回滚仍为本 P9 guard 与 HEAD 831b70433e3dbdfd6f119c6036a3c8cf22d85ae4，保护 app/.cxx/。尚未装本轮新包，不提交/tag。
- 唯一下一动作：安装正在构建的 debug 包，从正常启动的首页进入最近观看并重复播放两个条目、菜单到正片跳转，检查原生崩溃和历史写入。

## Checkpoint 10：2026-09-08 新包复播与导航缓存暂停冲突

- 新包构建 22s，通过；APK SHA-256 `1999ce4848acd53d0d4d9c281735c00773983c58f998f1ada67372d76b7641cc`。OEM 辅助安装成功，从首页正常启动；第一次播放后设备私有 libmpv SHA-256 已确认是 Checkpoint 9 的 b15d07c4... 候选（播放前磁盘保留旧库，首次使用时才重新提取，不能在提取前误判）。
- 新采集 `/tmp/p9-menu-device-20260907.Vq7vyl/resume-device.log`，UID 10464；前一采集 04:35 断开，已从该时间接续，未清日志。
- 《超脱》04:56:39、04:58:07 两次最近观看复播均进入 HDMV 菜单，日志明确忽略旧历史 821112ms，未再执行错误续播；截图 `resume-detachment-menu.png`。04:57:58 为主动 stop，不是 EOF/崩溃。04:59:14 PLAY、随后主菜单/Popup 操作有状态切换；尚不能据此宣称播放正常。
- 反例：菜单画面仍反复缓冲，一分钟内 rebuffer 计数增长到 41；菜单暂停/恢复期间网络流量和进度间歇变化。这轮未再看到旧 destroyed-mutex SIGABRT，但持续性验证未完成。
- 确认配置冲突：native `demux.c::read_packet` 在 nav_active 下禁止普通 read-ahead；App 初始化强制 cache-pause=yes，而 `playloop.c::handle_update_cache` 仍按缓存低阈值暂停。因此修复必须改变真实 cache-pause 行为，不能只隐藏 App 进度提示。
- 最窄适配：仅在 file-loaded 已确认 `disc-nav-active` 时设置 `file-local-options/cache-pause=no`，不关闭网络/ISO页面缓存，不改持久设置；BD-J/无菜单回退不适用。mpv 固定源码 `DOCS/man/input.rst` 的 file-local-options 文档、`command.c::mp_property_local_options/access_option_list` 明确保证停止当前文件时自动恢复旧值；`loadfile.c` 在通知 file-loaded 前已设置 playback_initialized，设置时机有效（A级源码/项目文档证据，2026-09-08）。
- 相比全局关闭 cache-pause，此方案不污染普通影片或复用上下文；相比再改 native cache 调度，此方案只补 App 已有导航集成的选项冲突。Native ABI/产物不变，无需重建两个 ABI或重复通过的原生门槛。
- 本轮新 Java 变更待构建实测，不提交/tag。唯一下一动作：只增量打包/安装这个 file-local 修正，验证两个最近观看条目、连续菜单/正片进度和退出重入。

## Checkpoint 11：2026-09-08 用户补充菜单触控/全屏与导航面板反例

- 用户 05:05 两张截图：内嵌菜单无法唤出全屏操作；全屏底栏的菜单按钮打开了透明、低对比、横排方向键的辅助面板。用户授权修正这些菜单 UI 问题，仍在 P9 App/资源范围内。
- 直接代码证据：Mobile 视频 FrameLayout 的 touch listener 先调用 `dispatchDiscMenuTouch`，后者对整个视频区域无条件消费事件，导致原有 `onSingleTap/showControl` 与 `onDoubleTap/enterFullscreen` 不可达；`DiscMenuDialog` 的 `transparent=true/stableOverlay=true` 清除了面板底色并清除全屏 Window flag，按钮仍受默认主题颜色影响。
- 局部方案比较：保持现状不满足显式全屏入口；把所有 tap 同时交给手势和光盘会误触光盘按钮；采用独立的小型播放控制/全屏入口，在播放器控制栏可见时优先交还普通手势。底栏“原盘菜单”直接发送 Top Menu，辅助导航改为长按入口，不冒充盘片本身菜单。
- 面板沿用项目已有底部操作层范式，限宽、清晰深色底与白字、48dp 点击目标、十字方向布局、明确标题和关闭；不调整全局主题/BaseBottomSheetDialog。打开导航面板保留当前全屏系统栏状态，执行确认/主菜单/Popup 后自动关闭面板，方向选择时保留以便观察盘片高亮。
- 这是既有 HDMV 输入接线和辅助面板的局部修正，不引入新菜单 VM/API、网络策略或架构；论文/上游提交合并不适用。验收以用户这两张截图对应的内嵌/全屏、关闭旋转全屏情况下的可点入口、真实光盘按钮仍可点击及面板清晰性为准。
- 已装缓存修正 APK SHA-256 `a48ef292a248a911be7297dcb8c092b7cdc34e79da838ced59349fc765ba99ca`；新 UID 日志 `nav-cache-device.log`：05:09:16/30/46、05:11:35 多次 HDMV 启动均 cache-pause=false；05:10 截图显示 READY/0 重缓冲。仍须分别验证两个条目的正片和章节/Popup，不据启动日志宣称完成。
- 唯一下一动作：实施这两个 UI 修正，增量构建安装后结合连续播放做同一轮真机验收。

## Checkpoint 12：2026-09-08 05:54 手机专用菜单图标与 MPEG-2 失败

- 延续同一 guard/HEAD/范围；保护 `app/.cxx/`。用户最新授权：额外全屏入口仅在实际蓝光菜单显示时出现，去掉“播放控制”，电视端不添加，图标和底栏一致且无额外背景色；继续修复《幽灵公主》。目标约 06:10 完成当前修正和真机验证，失败按实际证据继续收敛。
- 第一版 UI 包 SHA-256 `d3f7e98cd633820ef459968c357375b76b092b24404ad3baaa0cdc40f4615adc` 已构建/安装；内嵌与全屏切换图标已点击验证。`ui-navigation-card.png` 实际仍是封面/播放控制入口，不能当作导航面板通过证据。该入口在 `app/src/mobile/res/layout*/activity_video.xml`，Leanback 不包含此布局。
- UI 根因与适配：`hasDiscMenu()` 是整张盘的可用性，不能表示菜单当前可见；只换条件但不订阅事件仍会陈旧。用 MPV 主线程的 `disc-menu-active` 属性变化通知手机 Activity，注册/移除随当前 player 和 Activity 生命周期；ISO 关闭时立即通知隐藏。不增加轮询、不伪造轨道变化。图标复用 `ic_control_fullscreen`/`ic_control_fullscreen_exit` 和底栏 48dp 点击样式，去除文字与容器底色。
- 原始失败证据：`nav-cache-device.log` 05:11:35，《幽灵公主》RAW ISO session=1004/47655223296 bytes 已打开；`mpeg2_mediacodec: Unsupported or unknown profile`、`MediaCodec ... failed to start`、`Software decoding fallback is disabled`、`Decoder init failed for mpeg2video`。这是解码失败，不是未识别 ISO。旧采集已停止；不清日志，从 05:29:32.867 续采 `menu-icon-decode-device.log`。
- 直接依据（A级，2026-09-08，MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` + 已记录本地补丁）：`DOCS/man/options.rst::hwdec-software-fallback` 规定硬解失败后可回退，yes=1 并保留启动包；`video/decode/vd_lavc.c::select_and_set_hwdec` 在禁回退时强制 EOF；`player/loadfile.c::play_current_file` 的 decoder 初始化早于 FILE_LOADED；`load_per_file_options`/`M_SETOPT_BACKUP` 在退出文件时恢复选项。App `MpvPlayerEngine.hardwareDecodeSoftwareFallbackOption()` 当前固定 no。故 FILE_LOADED 时才补选项无效。
- 方案比较：不改无法显示 MPEG-2 菜单；全局解除 no 会改变普通影片既有策略；采用 `loadfile.c` 在真实 HDMV 已确认、decoder 初始化前设置带 BACKUP 的文件局部 yes，仍先尝试硬解。只对支持软件帧的 `gpu`/`gpu-next` 生效；`vo_mediacodec_embed.c::query_format` 只支持 MediaCodec 帧，不能假装它能渲染软解帧，不改其策略或全局输出选择。BD-J、无菜单、DVD、普通媒体不进入此分支。当前用户实际输出 gpu-next/androidvk 符合此门槛。
- 这是现有菜单集成遗漏的选项兼容修复，不引入新的上游候选、算法或性能优化；不重复先前已覆盖的论文/项目/讨论研究。证据由固定项目文档、原生源码和同机失败日志交叉印证。软件解码功耗/能力受设备和码流影响，不宣称所有盘或 Surface 直出已通过。
- 验证：真实 helper 提取测试覆盖 HDMV GPU、无菜单/BD-J、DVD、未知输出、硬解专用输出及 BACKUP 标记；双 ABI 仅增量 MPV + 资产检查；一次 Mobile APK/Leanback 编译；手机检查菜单图标显隐、全屏、两张盘的菜单到正片与退出重入。回滚仍成套恢复到本文件记录的 HEAD；未通过真实播放前不提交/tag。
- 唯一下一动作：按以上方案实施并完成一轮定向构建安装。

### 06:14 对“旧版可以硬解”的反证核查

- 用户指出旧版可硬解《幽灵公主》。此前从 `Unsupported or unknown profile` 推断整部影片不支持硬解是不成立的；暂停候选安装，先用原始日志和设备运行时能力交叉核查。
- 手机于 06:04:20 已覆盖安装旧版 APK，SHA-256 `2ffd0a01c39aae708b2c86c016168955f1371161cb64492dfd0a298b85ef194b`；不是本轮候选包。旧版无原盘菜单设置，普通路径 `playlist=1`、`durationMs=8002911`。续采日志 06:05/06:06/06:07 均直接打开正片；06:07:08 明确 `h264 / High / 1920x1080 / 23.976`、`decoder=h264_mediacodec`，Android 创建 `c2.qti.avc.decoder`。不再重复这个已成立的对照。
- 新菜单旧候选 05:11:35 的实际 track-list 为 `mpeg2video / Main / 1920x1080`，与正片 H.264 不同；不是缺少整个 codec/profile。FFmpeg `mediacodec_wrapper.c::ff_AMediaCodecProfile_getProfileFromAVCodecContext` 不映射 MPEG-2，因此“unknown profile”警告本身不能判定流坏了。
- 用 `/tmp/p9-menu-device-20260907.Vq7vyl/P9CodecProbe.java` 经 Android `MediaCodecList` 做只读查询，结果保存在 `device-codec-probe.log`：regular 列表无 MPEG-2；all 列表仅 `c2.vivo.mpeg2.decoder`/alias，`hardware=false software=true special=true`；H.264 的 `c2.qti.avc.decoder` 为 `hardware=true software=false`。和 XML/FFmpeg 排除 software-only decoder 的源码相符。没有改写用户 mpv.conf 或解码偏好。
- 对照结论：普通版直接进入 H.264 正片而菜单模式进入另一个 MPEG-2 片段，不能说“设备不能硬解幽灵公主”。修正应兼容这个片段且每次新视频解码器仍先尝试硬解，正片必须实际恢复为高通 H.264 硬解。是否成功到达/操作菜单、是否正常切入正片仍待真机，不能仅据能力查询宣称修好。
- 定向 helper 实测通过；干净补丁应用 `/tmp/p9-icon-decoder-apply.ohqoZ6` 与源码 helper 一致。双 ABI 仅编译 `player/loadfile.c` 成功；stage/ELF 验证通过；Mobile APK + Leanback Java 构建成功（54s）。补丁 SHA-256 `a79d2fb2357836f9b3eea91893a8768e1636e362f17bc07c7d147a0e43ff9fac`，arm64 libmpv `c3cb51cb147451041afca120891a1dcb1d8e2f93888b1cd803039c745c0b8caf`，armv7 `77eb578b39b3dced1810d51426ece34e281a1df4e74a348434fed7e0870b0c37`。
- UI XML 检查通过，无文字控件；手机复用原有白色图标/48dp点击样式，Leanback 未引用手机专用入口。尚未安装这次 UI/decoder 候选，因补做用户指出的硬解反证，06:10 目标顺延。唯一下一动作：安装已构建候选，验证 MPEG-2 片段到可操作菜单再到 H.264 硬解正片，不重跑已通过构建。

## Checkpoint 13：2026-09-08 06:31 菜单无响应和关闭菜单超时

- Checkpoint 12 候选 APK `c0b397f4dd847c01cc0e5cf18e34cff8afacf02b3f4400f132c1e0ed24fe59cc` 于 06:15:54 安装；首次播放后已核对私有 arm64 libmpv 为 `c3cb51cb147451041afca120891a1dcb1d8e2f93888b1cd803039c745c0b8caf`。用户报告开菜单按钮无响应，关菜单容易超时，验收失败；不提交/tag。
- 屏幕 `after-log-tags.png` 确认《幽灵公主》可显示绿色 HDMV 菜单和独立白色全屏退出图标，无“播放控制”和底色。但首次菜单会话在 06:16:53 达到 ENDED，后续菜单静止页点击无进展；不能把截图当作菜单功能完成。
- 系统 `log.tag=M`，即使临时打开 `mpv/TV-mpv/TV-player-engine/TV-player-error/TV-iso/MediaCodec/CCodec` 的非持久标签仍没有新 logcat。原值保存在 `candidate-log-tags-before.txt`，结束需恢复。改读 App 自己的 `cache/webhtv-debug-log.txt`，本地 `candidate-app-log.txt` 和续采 `candidate-file-live.log`；含 NUL，用 rg -a 并限制长行，不输出 URL/凭据。设备旧日志与 App 数据不清理。
- 菜单输入根因：06:26 连续 `disc: discontinuity 4->9, reopening slave`。`STREAM_CTRL_NAV_CMD` 在静止页每次 activation 都人为递增 generation，但 `bluray_stream_fill_buffer()` 先 `bd_get_event()` 后 `still_active` 立即返回；libbluray 1.4.1 `bluray.c::_run_gc` 只是把按钮字节码挂到 HDMV VM，必须 `bd_read_ext()` 才运行。故已接受的命令永远等不到执行，反复重开也无法解锁。
- 最窄修正：在静止状态判断前用 `bd_read_ext(..., len=0)` 排空事件并推进待执行 VM，保持 bounded/cancel 检查；真实 PLAYLIST/TITLE/PLAYITEM 事件退出旧 still。只对真实流跳转递增 generation，去掉“每次点击都伪造跳转”；遇真实跳转先返回边界 EOF，再让新 slave 从新 clip 开头 probe。重开后两条路径都读取最新 generation；同 codec 的新 clip 也必须接管新 extradata/参数，防止复用旧 SPS/PPS。依据为固定 libbluray 源码和保留的同机失败日志，无新依赖/ABI。
- 关闭菜单超时证据：06:17:51 和 06:19:33 普通 `/longest` 复播均设置 start=10.771/55.101，H.264 已读出，但 aid=1 被选成 `srcId=8191(0x1fff), codec=mp3, sampleRate=0`；15s 没有首帧，06:18 从头重试后能播放。0x1fff 是 TS NULL_PID，固定 FFmpeg `mpegtsenc.c::mpegts_insert_null_packet` 也用此 PID 填充。现有 `is_bd` 只认 native bd，不认 JNI callback，导致本应过滤的非音视频填充流被自动选为主音轨。先拒绝这个保留 PID，并仅在 nav_active 时给 packet 加动态 codec segment 标记，保持普通单时间线包语义；不通过放宽15s超时掩盖。此因果关系待实机复播证伪。
- 当前总耗时超出最初预计，原因是用户追加两个真实失败和旧版对照。停止额外 UI 探索；下一轮只验确定性状态/轨道规则、两 ABI 增量 MPV、一次 APK 和菜单/正片复播。预计约 15–20 分钟，不把时间目标作为降低验收的理由。
- 唯一下一动作：实现并用提取真实函数的回归检查验证静止菜单推进及 NULL_PID 排除，再构建。

## Checkpoint 14：2026-09-08 07:27 菜单片段 EOF 不应触发下一集

- 延续已授权的 `P9-MPV-BLURAY-MENU-FIX` guard、分支与范围，保护 `app/.cxx/`。用户要求先打 tag，07:17:35 已为已提交 HEAD 创建上述本地基线 tag；没有提交未验收工作或推送。07:22 用户明确继续解决。
- Checkpoint 13 已完成的验证：真实函数回归 `test_disc_navigation_progress.sh` 通过（静止页命令推进、跳转边界、无命令不跳 still、取消/IO 失败、普通路径与 NULL_PID 排除），旧候选在 pending command/still 断言失败；干净应用 `/tmp/p9-still-pid-apply.U9bsDe` 与源码一致。双 ABI MPV 增量编译、stage/ELF 资产检查、Mobile APK 与 Leanback Java 构建通过。日志均在 `/tmp/p9-menu-device-20260907.Vq7vyl/still-pid-*.log`。
- 已安装的 Checkpoint 13 APK SHA-256 `038e0087a0cee9752c68ea34932cc6411d06369f1411773ccff4e63b2c6c142d`；patch `8e6e4963dca857d826bda748e4208428ad3e6852eb6973061bad6d6e404cb428`；arm64 libmpv `128629331266c3e952fc750a40bdaf747f6d506f2bd1c45624030f250656b77a`；armv7 `f0754c06b8d7983365c53e85608ab311677d4e8cf2041c7db7088d08d9b43e71`。安装脚本曾因 USB 断连退出 1，但设备 06:54:13 安装时间、实际 base.apk 与首次播放解出的 libmpv hash 均已核对匹配，不重装旧候选。
- 最新证据 `menu-ended-recovery.log`：06:56:26.573，duration=1001 的菜单片段触发 `playback ended reason=property:eof-reached`；06:56:30.587 原生继续 `playback-restart`，后续可切换 1993ms 菜单与 8002912ms 正片，但 Java 状态仍为 ENDED。07:00:05.738 再次复现。没有相应自然 `end-file` 事件；真正的 stop 仅在退出条目时发生。
- 根因/最小修正：MPV 的 `eof-reached` 表示当前解码队列结束，包括光盘静止菜单背景，不等价于结束 libbluray 导航会话。`MpvPlayer` 仅在非导航播放时映射该属性为终态；使用已在 FILE_LOADED 确认的 `discNavigationActive`，不依赖菜单 overlay 当下是否可见。真实 end-file/error/idle 路径不改；关闭菜单、BD-J/无菜单回退、普通视频仍保留原 EOF 行为。`MpvDiscMenuPolicyTest` 覆盖菜单 EOF、跨片段 EOF 往返及普通播放 EOF。
- 这是现有已批准导航设计的局部 App 适配修复，不引入新架构/依赖/API/解码策略。依据来自固定 MPV 的 `player/command.c` / `player/playloop.c` / `player/discnav.c` 和同机日志，无需重复外网研究。回滚为同一 P9 原子单元。
- 本轮目标：北京时间 07:24 起约 30 分钟，目标 07:54；定位 5 分钟、修复构建 10 分钟、真机与记录 15 分钟。仅执行必要测试/构建；尚未通过菜单进入正片和关闭菜单历史复播，不能提交/tag。
- 单独临时 logcat 标签已经按 `candidate-log-tags-before.txt` 恢复；继续读取 App 文件日志，不清日志/数据、不改用户 mpv.conf/偏好。
- 唯一下一动作：运行 EOF 策略定向测试和 Mobile APK/Leanback 编译，然后安装并在同机验证两个失败路径。
- 07:33 EOF 策略定向测试、Mobile APK 和 Leanback Java 编译已通过（`menu-eof-apk-build.log`，29s）。安装前用户报告当前《豪斯医生 第一季》ISO 错误，先保存 `current-iso-error-0733.log/.png/.xml`，暂停安装。该盘连续 3 次在正常 HTTP 206 读取后得到 unknown_format(-17)，与菜单 EOF 不同；现有 App 日志过滤掉了部分 Blu-ray 识别/加密诊断，不能仅凭缺失日志断言 native 从未尝试 libbluray。仅补 ISO 路由选项及 Blu-ray 诊断记录，再做同盘菜单开/关对照；不改网络超时或硬解策略。

## Checkpoint 15：2026-09-08 08:12 《豪斯医生》FIRST PLAY 初始化被跳过

- Checkpoint 14 APK SHA-256 `ea5cb16eadd9626a571a3ad6e7fad1cda3dd0ad35703d0951531e00364d2eb07` 已经安装成功（`menu-eof-install.log`）。EOF 策略 JUnit 13 项全部通过；Mobile APK/Leanback Java 编译通过，新增诊断后的二次编译 22s。二次编译对应新增诊断代码，并非重复成功验证。
- `house-diagnostic-failure.log` 08:03:13–18 确认 `raw=true disc-menu=yes access-references=yes`，`ISO detected as Blu-ray`，`hdmv_mode=1`，随后初始 TITLE=65535 被 TOP MENU=0 覆盖，连续数百次 BD_EVENT_TITLE(5)=0、无 PLAYLIST(6)，最终 demux unknown_format(-17)。此前“未交到光盘入口”的初步判断已被完整日志推翻，实际是导航启动失败，不是网络或解码器失败。用户无需继续重复打开同一个候选。
- 固定 libbluray 1.4.1 `bluray.c::bd_play()` 仅排入 FIRST PLAY；VM 真正运行在 `_read_ext()`。当前 `stream_bluray.c::bluray_stream_open_internal()` 紧接 `bd_play()` 调用 `bd_play_title(TOP_MENU)` 会替换尚未执行的初始化程序。`bluray.c::_read_ext()` 自带注释明确描述：越过菜单系统初始化，可能在 root menu 中无限循环（示例 Butterfly on a Wheel）。本机 TITLE=0 循环与该机制一致。
- 最小修正只移除首次打开时强制 TOP MENU，保留 overlay 注册、bd_play 失败回退、BD-J top menu/无菜单默认最长标题。让光盘自行进行语言选择/片头/菜单初始化，用户后续 Menu/Popup 命令不变。不整体升级、不改网络、硬解或用户配置。
- 实际启动代码块提取回归 `test_disc_navigation_start.sh` 通过：FIRST PLAY 未被顶层菜单覆盖；bd_play 失败注销 overlay/销锁并回退主标题；关闭菜单/BD-J 回退不启动 VM。上个源码 `/tmp/p9-still-pid-apply.U9bsDe` 同一测试在 first_play_pending/top_menu_calls 断言失败（`old-disc-start-regression.log`），明确证伪旧行为。持久 patch 已机械同步。
- 诊断过滤收窄到 Blu-ray 识别/启动关键消息，不长期记录每个 bdnav event，避免静止页日志刷屏。两 ABI 构建日志为 `first-play-arm64-build.log` / `first-play-armv7-build.log`；JNI 无改动不重编。
- 原 07:54 目标因新增同盘故障取证及安装等待超出，已向用户说明并停止额外探索，只完成启动修复和菜单/普通续播验收。仍为同一已授权 P9 guard 与原回滚锚点，不提交不推送。
- 唯一下一动作：收取当前两 ABI 增量构建结果，stage/验证/手机打包安装，再复验《豪斯医生》和《幽灵公主》。

## Checkpoint 16：2026-09-08 10:56 《豪斯医生》菜单背景卡顿诊断（只读）

- 用户本轮要求先打 tag，然后判断菜单背景卡顿是否正常、是否网络或解码渲染问题。10:22:00 创建本地注释 tag `recovery/P9-MPV-BLURAY-MENU-FIX/20260908-102200`，指向已提交基线 `831b70433e3dbdfd6f119c6036a3c8cf22d85ae4`；已明确告知不含工作区未验收修复、不推送。没有把用户的“好像可以播放”扩大为整个 P9 验收通过。
- Checkpoint 15 产物：patch SHA-256 `fe616538bf8cdc0ed28908daad4f837c3eb452884eea034cd1d0c338d68f44ce`；arm64 libmpv `c8d0b81213be59bf887a887e6b1f1277e3e1baf4ec3b856bf0b93bef9e62643d`；armv7 `3e354633b6b6e6262c611625b3e79b7e34291a189a00eb8e1fbb0ed2b92c9e51`；手机 APK `46784d51c224dbe66e69abe76edbe1aad7345bb62b390c49b24a27c5edcbfa1b`。双 ABI 仅编译 `stream_bluray.c`，ELF/资产验证通过，Mobile APK/Leanback Java 26s 通过；当前补丁反向 apply --check 通过。`first-play-install.log` 安装成功，设备 lastUpdateTime=08:29:31。未重复构建。
- 真机恢复：ADB 曾断开，用户重新连接后仍为 vivo V2453A / Android15 / serial `10CF6H1D2L0009S`。`menu-stutter-current.png/.xml` 确认《豪斯医生》实际 HDMV 菜单与手机白色退出全屏图标。`first-play-reconnected.log` 08:33 的同盘启动已有 H.264 MediaCodec READY；当前卡顿会话 `p-x1v96g-1` 的 `menu-stutter-1022.log` 覆盖约 10:13:55–10:28:48。
- 观察：H.264 1920×1080 / 23.976fps，观察到 hwdec=mediacodec、vo=gpu-next、GPU 渲染路径非 Surface direct。10:14:00.125 `dec=0 out=813`，10:14:30.357 `dec=0 out=916`；10:28:46.749 `dec=0 out=2983`。该窗口共新增 2170 输出丢帧、decoder drop 保持 0；不能把输出丢帧直接等同于 GPU 性能不足。MediaCodec port 等待多为微秒至个位毫秒，所取日志没有 >50ms port 等待证据。温度状态 nominal，重缓冲计数为 0。
- 观察：窗口内 511 次 Range start 全为 4MiB 页面，仅 3 次来自 iso-prefetch 线程，其余来自播放读取线程；510 次完成请求中位 697ms、P90 967ms、最长 9214ms。199 个遥测样本的可播放缓冲中位 210ms、P10 为 0。10:20:42.954–10:20:52.167 一次页面请求独占约9.2秒，无需把这种停顿当成原盘正常动画。请求均正常 206 并不表示供数实时性足够，也不能据重缓冲计数0排除网络等待（导航路径 file-local cache-pause=no）。
- 本地确切路径：`demux/demux.c` 在 nav_active 时禁用媒体 read-ahead，避免提前推动光盘 VM；`IsoPageCache.readAt()` 只有消费完当前4MiB页才调用 `prefetch(next)`，通常下一次播放读取已立即需要该页。本盘画面循环时仍反复重取已读页，与8页/32MiB LRU未覆盖背景及相关文件工作集相符。日志中极少的后台预读与该代码行为一致。
- 结论分级：已证实存在真实输出丢帧与同步远程读取停顿，不属于纯静止菜单或主观低帧率；最高可信主因是原始 ISO 预读/缓存对导航按需读取适配不足，叠加来源/本地代理/网络请求延迟。不能仅从手机日志分解远端服务、代理、Wi-Fi各自贡献；也尚未做同片本地/暖缓存对照，不能声称彻底排除输出调度问题。现有证据不支持“设备不能硬解”或直接换渲染器。
- 推荐后续：先在原始字节层更早预取有限页面、保留取消/跳转及内存边界；不要直接恢复 MPV 媒体大幅 read-ahead 以免推进 VM。应对比同菜单固定窗口的 Range 等待、缓冲低水位、decoder/output drop，并保持按钮跳转正确。本轮是诊断授权，不实现该优化、不调整用户设置或超时。
- 本轮唯一编辑为该任务文档，保护 `app/.cxx/` 和所有既有 P9 改动。关闭菜单的历史复播、《幽灵公主》完整菜单→硬解正片、《超脱》以及 Surface direct 边界尚未全部验收，不能提交工作区或给未提交修复打“通过”标签。
- 唯一下一动作：交付上述诊断并等待用户是否继续优化原始 ISO 页预读的决定。

## Checkpoint 17：2026-09-08 11:16 确认菜单循环缓存淘汰，久等不会缓存完整

- 用户质疑停留菜单半小时仍卡顿，要求确认根因。本轮只读诊断，不实现优化、不修改设置；唯一仓库编辑为本任务文档，所有已有 P9 代码/产物和 `app/.cxx/` 保留。11:11 开始，预计 5–8 分钟；不重启网络研究或构建。
- ADB 当前无设备，最新采集没有成功，不将 `menu-cache-root-1111.log` 当作新证据。使用已保存的 `menu-stutter-1022.log`（同一会话 `p-x1v96g-1`，10:13:55–10:28:48）解析真实 Range 地址序列，未重跑播放。
- 决定性证据：从10:14:37.024至10:28:37.804，可分出连续16个完整循环，每个循环都是30次请求、30个不同的4MiB页，共120MiB；整个窗口也只有这同一组30个唯一页。总共511次请求，其中481次重复请求已有地址，总请求量2044MiB。这不是“菜单还没读完”或猜测工作集大小，是已有页面在每轮再次下载的直接记录（120MiB是按页触达量，包含菜单相关读取，不等同于单一视频文件的精确大小）。
- 代码对应：`IsoPlaybackSession` 始终用默认 `new IsoPageCache(new HttpRangeIsoSource(...))`；`IsoPageCache.java:15–16` 固定4MiB×8页=32MiB，`:37–40` access-order LRU在第9页插入时淘汰旧页。这个已命中的真实调用路径没有持久保存整轮页面的逻辑；日志同样长期 `pages=8`。30页循环超过8页容量，下一轮读回开头时，之前的开头页已被后面的页逐出。等待时间不会增加缓存上限，因此半小时、甚至更久都不会自然变成全缓存播放。
- 放大因素：`IsoPageCache.java:67` 当前页消费完才排队预读下一页，数据需求线程常先于预读线程发起实际请求。导航期间 MPV 媒体级read-ahead又为保护VM而禁用，故无法靠普通媒体缓存掩盖每次原始页重取的延迟。上一轮已测可播放缓冲中位210ms、小于页面请求中位697ms，与持续取数等待和输出掉帧相符。
- 根因分级更新：**确定存在的实现根因是菜单循环数据不能保留、反复淘汰重取，加上过迟的原始页预读。** 不能再将长期卡顿笼统归因于用户网速。解码器为MediaCodec，解码丢帧0、输出丢帧持续增长；尚未完成缓存修复后同场景对照，故不声称这是每一帧卡顿的唯一原因，也不据输出丢帧认定GPU算力不足。
- 最小修复方向应同时覆盖循环数据保留与提前原始页预读，而不是仅延长超时、只等待“缓存够了”、只换渲染器或盲目增大媒体read-ahead。本轮未设计/实施新磁盘或内存策略；后续必须保留低内存设备、取消、跳转、源校验及VM边界。
- 唯一下一动作：向用户说明上述已证实根因及仍待修复对照的渲染边界，等待缓存修复授权。

## Checkpoint 18：2026-09-08 原始 ISO 循环缓存修复（已授权，实施中）

- 用户明确“修复一下”，随后“继续”。沿用 `P9-MPV-BLURAY-MENU-FIX` guard、既有 P9 修改及回滚锚点；保护 `app/.cxx/`。本轮仅编辑 `app/src/main/java/com/fongmi/android/tv/player/iso/`、`third_party/mpv-player-jni/tests/` 和本文件。新 Java 回归放在既有 JNI 测试目录，通过该目录的 Gradle init script 编入定向单测，不覆盖 guard、不扩大其 scope。
- 本地开始执行约 11:49 Asia/Shanghai，目标 12:19 前完成 Java 实现、定向回归与 debug 打包；手机连接和实测等待另计。无设备时不得宣称流畅度或整项 P9 已验收。

### 决定性问题与最佳实践核对

问题：不增加 32MiB 内存上限、不提前推进光盘 VM，怎样保留已读完的一轮菜单并掩盖下一页网络延迟？

| 证据（2026-09-08 查阅） | 等级、支持结论及适用边界 |
| --- | --- |
| Checkpoint 17 原始日志 `menu-stutter-1022.log`，会话 `p-x1v96g-1` | A：30 个唯一4MiB页连续16轮重复下载；页0及连续29个视频页，120MiB工作集。不能将长期卡顿仅归因于网络速度。 |
| 本仓库 `831b70433e3dbdfd6f119c6036a3c8cf22d85ae4` 的 `IsoPageCache` / `IsoPlaybackSession` / `HttpRangeIsoSource` | A：内存LRU仅8页、页尾才预读；单个 activeCall 在收到响应头后清空，不能覆盖响应体读取与并发取消。属于 WebHTV 本地缺陷，不引入新上游 commit。 |
| 同一基线 `MpvHlsCacheCoordinator`、`DiskCacheCapacityPolicy` 与既有 coordinator 测试 | A/B：已有跨客户端容量、读租约、写预约、临时文件原子提交、LRU、低空间熔断；复用该实现比新造磁盘缓存管理器窄。保留 `max(512MiB, 总空间10%)` 空间底线。 |
| [Android app-specific cache 文档](https://developer.android.com/training/data-storage/app-specific#internal-create-cache)，正文已读取 | A：缓存必须允许被系统删除、应用负责清理、使用私有 cache 目录。磁盘失效只导致 miss/网络回退，不使 ISO 播放失败。 |
| [OkHttp 5.4.0 Call.kt](https://github.com/square/okhttp/blob/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt)，与版本目录一致，正文已读取 | A：Call 覆盖响应体的完整生命周期，cancel 针对整个请求；必须到响应体关闭才注销，并在 close 时取消全部在途 Call。 |
| PR/issues/reverts、论文/博文类别 | 本次不移植上游缓存算法或依赖版本，没有适用的上游提交/revert待决；真实故障属于本地调用与生命周期。保留既有LRU与容量策略，无新算法优越性主张，论文/泛缓存帖子不能改变已有源码、官方契约与可复现日志共同决定的窄方案，故不展开。 |

### 方案比较与实施决定

- **不改**：重复下载120MiB/轮不会随等待消失，不接受。
- **直接恢复上游 MPV 媒体 read-ahead 或单纯扩容内存**：前者会提前驱动菜单 VM，后者增加低内存设备风险，均拒绝。
- **WebHTV 窄适配（采用）**：菜单设置开启时，为 ISO 会话增加原始字节磁盘页层，复用 `mpv_hls` 目录与 `MpvHlsCacheCoordinator` 的现有共享预算，容量取当前 MPV 播放缓存设置（默认128MiB），不修改用户设置；内存仍4MiB×8。会话使用随机隔离键，不保存 URL/凭据，不跨会话复用；close 删除自身缓存，崩溃残留仍受原有全局LRU约束。关闭菜单时不启用此磁盘层，BD-J无提示回退及DVD菜单边界不变。
- 预读只在连续原始字节读取时提早请求下一页；最多一项在途预读及一项最新候选，随机跳转丢弃旧排队候选，最多容许已经在途的一页结束，不让其阻塞新的前台页。close 取消所有HTTP读取并唤醒同页等待者。预读不调用 libbluray、不改变 demux/nav_active。
- 磁盘缓存只有完整页原子提交；截断或文件消失回退网络；源长度/validator变化必须拒绝后续陈旧缓存，不混用新旧ISO。日志仅记录页数、命中/磁盘命中/网络页/预读计数，不输出URL。
- 兼容/性能/生命周期边界：无 JNI/API/ABI/原生库变更；磁盘读写引入有限I/O，容量不足保留内存及网络播放。顺序正片与元数据读取仍使用同一源校验；Exo不接入此缓存。修复后仍需实测确认是否有独立输出调度问题。

### 验收与回滚

- 最便宜决定性回归：30页循环大于8页内存，第二轮及以后所需页面不新增网络读取；内存上限不变；只读部分页时下一页已开始预取；随机跳转不执行积压预读。
- 同一组回归覆盖：共享磁盘预算/容量淘汰、失效文件/写失败回退、短读/EOF、同页并发去重、关闭与在途HTTP响应体取消、源变化拒绝缓存、会话隔离和关闭清理。
- 然后仅构建 Mobile arm64 debug 和 Leanback Java；现有两ABI native产物不变，无理由重跑成功的原生验证。
- 真机验收：同《豪斯医生》菜单，至少3个暖循环窗口对照 Range/磁盘命中及 output/decoder drop；目标暖循环不重取完整同段视频，不能以build或缓存单测替代流畅度。仍需保留菜单按钮→正片、关闭菜单复播及先前未验收边界。
- 回滚只撤销本Checkpoint新增的ISO Java改动与其接线，保留此前FIRST PLAY等修复；整项P9仍以已提交基线和既有恢复tag为成套回滚锚点。所有必要真机验收未完成前不提交/tag为通过，不push。

### Checkpoint 18 本地验证结果（13:12完成）

- 已修改 `IsoPageCache` / `IsoPlaybackSession` / `HttpRangeIsoSource`，新增 `IsoDiskPageStore`；测试及Gradle init script位于既有 `third_party/mpv-player-jni/tests/` guard范围内。未修改native源码/产物，未覆盖保护路径。
- 一次Gradle组合运行成功，耗时1m44s：11项 `IsoPageCacheTest`、3项 `HttpRangeIsoSourceTest` 全部通过；30个真实4MiB页面循环4轮，每页源读取计数为1，内存map仍8页；两并发HTTP响应体在close后及时结束。源变更拒绝缓存、磁盘丢失/截断/空间不可用回退、共享预算/会话隔离、页尾前预读、跳转去旧候选、同页去重、关闭等待者等已验证。
- 命令：`JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home LC_ALL=C bash ./gradlew --offline --init-script /tmp/p9-menu-gradle-staging.gradle --init-script third_party/mpv-player-jni/tests/iso_cache_tests.gradle :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.iso.IsoPageCacheTest --tests com.fongmi.android.tv.player.iso.HttpRangeIsoSourceTest :app:assembleMobileArm64_v8aDebug :app:compileLeanbackArm64_v8aDebugJavaWithJavac`。
- 日志 `/tmp/p9-menu-device-20260907.Vq7vyl/iso-cache-build.log`；JUnit XML在 `app/build/test-results/testMobileArm64_v8aDebugUnitTest/`。Mobile APK SHA-256 `5aeecdddb7274c29d95356d8f4e13d202627dc2d820b86e1977a7c5ebcc4cf47`，路径 `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`。
- guard `check`通过：branch/HEAD、scope、protected dirty paths和staging安全。未finish/commit/tag；真机对照未通过。12:19本地目标超出，已向用户说明共享缓存与生命周期核对耗时并停止额外研究；不重复成功构建。

## Checkpoint 19：2026-09-08 13:32–13:48 正片卡顿与CPU取证（只读诊断）

- 用户手机重连，报告“老版本”《豪斯医生》菜单及正片均持续卡顿掉帧、App CPU约100%。本轮先保留现场，不安装候选、不改mpv.conf/播放器设置、不暂停/重启当前播放、不清日志。仓库只更新本文件，所有既有代码/产物与 `app/.cxx/`保持不变。
- 真机：vivo V2453A / Android15 / `10CF6H1D2L0009S`；package `com.fongmi.android.tv` versionName5.6.0/versionCode560，`lastUpdateTime=2026-09-08 08:29:31`，pid29642。因此“老版本”是当前缓存修复前的Checkpoint15，并非已证实为菜单功能之前的更早历史版本。
- 证据目录 `/tmp/p9-menu-device-20260907.Vq7vyl/`：`feature-stutter-baseline-1332.log`、`feature-stutter-baseline-1340.log`、`feature-stutter-baseline-1332.png`、`feature-cpu-baseline-1332.txt`、`feature-cpu-baseline-1345.txt`、`feature-audioflinger-baseline.txt`、`feature-audioflinger-baseline-1345.txt`、`feature-thermal-baseline-1332.txt`、`feature-audio-sched-baseline.txt`。手机日志时钟与主机约差6分钟，所有播放时序比较在手机日志内部进行，不混用采样文件名时间。

### 已证实的观察

1. 当前正片会话 `p-x91tog-3`，H.264 1920×1080/23.976fps，实际 `h264_mediacodec` 硬解、`vo=gpu`/OpenGL（不是上午菜单会话的gpu-next）；DTS 5.1软解为PCM5.1/48kHz，`ao=audiotrack`。13:37:12输出掉帧226，13:43:55为554，decoder drop始终0。不能把输出掉帧等同于手机无法硬解或GPU能力不足。
2. 手机13:36–13:37窗口内61次完整ISO Range读取，中位1231ms、P90=1942ms、最长3517ms；同窗口13个telemetry采样的可播放缓冲中位156ms、P90=586ms，多个点为0。页地址正向递增，这次不是菜单反复循环；同步下一页读取依然会使正片供数中断。导航保护禁用媒体read-ahead时，正片也依赖及时的原始字节预读，不能以菜单循环修复概括全部正片问题。
3. CPU口径：`PlaybackPanelResourceMonitor.publishCpuLocked()`=`Process.getElapsedCpuTime()`增量/墙钟增量×100，100%代表约一个逻辑CPU的时间，并非8核整机满载。第一组top去除首个无完整间隔样本后14点，App平均77.7%，其中 `ao/audiotrack` tid20315为44.57%、主线程11.43%、MediaCodec_loop3.14%；第二组10点App88.1%、同音频线程53.20%、MediaCodec_loop2.80%。第一组整机平均608.9%idle/800%，没有整机CPU满载证据。
4. AudioFlinger pid29642/track6226是48000Hz/6声道PCM，`FrmRdy=0`，反复AT::remove/AT::add，Underruns持续累计；支持音频供数断续。该计数并非“丢了几帧视频”，不拿它代替视频掉帧指标。
5. 热状态 `MODERATE(2)`，skin约42°C，具体CPU/GPU温度项未报告节流状态；温控可能是干扰因素，不能仅此认定是卡顿根因。

### 音频零样本忙循环源码复现

- 实际native源 `build/mpv-native/mpv-android/buildscripts/deps/mpv/audio/out/ao_audiotrack.c` SHA-256 `076f3bd56f98a05154736678512cfb2340011486127ae6ee1256158db78fa86c`。`ao_thread()` PCM分支调用 `ao_read_data(..., pad_silence=false, blocking=false)`；返回0时仍执行 `AudioTrack_write(...,0)`，随后立即下一轮播放状态/时间戳查询，无条件等待。相比之下现有compressed分支无帧时会等待20ms。
- `audio/out/buffer.c::ao_read_data()` / `ao_read_data_locked()`明确允许锁暂不可得、音频断粮、EOF等情形返回0；实际PCM循环必须处理“暂时无数据”，不能假定WRITE_BLOCKING的零字节写入会形成背压。
- 只在临时目录 `/tmp/p9-audio-empty-repro.4x5JS9/` 写入host复现桩，自动提取上述真实完整 `ao_thread()`，没有编辑生产源码。`cc -std=c11 -Wall -Wextra -Werror -Wno-unused-parameter -Wno-unused-variable`编译并一次运行通过：`reads=10000 writes=10000 zeroWrites=10000 waits=0 latencyQueries=10000`，确认当前实现存在可达的无等待空转路径。
- 原生现场采样限制：`simpleperf record`因`security.perf_harden`拒绝，`debuggerd -b`要求root；不修改系统安全属性、不反复重试。故**源码忙循环是已复现缺陷，现场音频高CPU/underrun是实测；二者高度一致，但没有宣称拿到了该设备函数级CPU采样**。

### 结论、权限边界与下一步

- 可确认不是正常蓝光动画，也不是单凭“CPU100%”得出的猜测。当前证据支持两个相互放大的实现问题：原始ISO供数/预读过迟造成正片断粮；PCM AudioTrack无数据时忙循环浪费CPU。前者可导致输出停顿，后者增加CPU/发热；尚不能把全部输出掉帧唯一归因于音频空转。
- 已构建的Checkpoint18 Java包尚未安装，不包含新发现的原生PCM忙循环修复。应先明确该新原生修改范围（有限等待/唤醒、正常PCM/压缩音频不回退、退出/暂停及时、零/短写及死对象恢复），再做最小回归、两ABI相关增量与同场景对照；不能只把Java候选宣称最终修复。
- 本轮完成的是诊断。无新增生产代码/原生库修改、未安装、未commit/tag/push。预计8–12分钟的诊断用了约16分钟，原因是权限阻止函数级采样，切换到已在树上的真实源码最小复现后停止扩展。
- 本文件 `git diff --check` 通过。整仓checkpoint脚本仍报既有 `third_party/patches/mpv-discnav.patch` 的空白context行（例如593/772/788）为trailing whitespace，并提示未提交的dependency/binary路径；日志 `checkpoint19-verification.log`。本轮没有修改该补丁，不做无关清理，不把整仓检查说成通过；这不是本轮诊断或此前Java回归失败。
- 唯一下一动作：交付上述证据并确认原生音频零样本等待修复范围，然后继续候选真机对照。

## Checkpoint 20：2026-09-08 音频空读等待修复（已授权）

- 用户明确“先打个tag，然后继续修复”，后续多次“继续”。首先已成功创建本地注释tag `recovery/P9-MPV-BLURAY-MENU-FIX/pre-audio-underrun-20260908`，指向已提交基线 `831b70433e3dbdfd6f119c6036a3c8cf22d85ae4`；不含工作区未提交修复，未push，不重复创建。
- 完成目标：PCM无样本时不空转，正常输出与暂停/退出行为保持，再对照手机CPU、正片掉帧及菜单缓存。本轮路径限 `third_party/mpv-player-jni/patches/mpv-audiotrack-underrun.patch`（P9独立小补丁）、同目录tests、`scripts/build_mpv_native.sh`、`build/mpv-native`、两ABI `libmpv.so`及本文件；均在既有guard内，无需改guard元数据。保护 `app/.cxx/`及所有前序P9改动。
- 14:24 Asia/Shanghai给出代码回归8–12分钟、构建打包5–8分钟、真机10–15分钟估计，预计14:55–15:00；多次用户继续/中断后仍执行同一方案，不重新研究或创建tag。

### 最小设计与证据

- A级现场/源码证据沿用Checkpoint19：原始ISO取数中断、AudioTrack线程45–53% CPU；实际 `ao_thread()` 空读10000次无等待。无需重新取证。
- 上游对照：2026-09-08已读取 [mpv master ao_audiotrack.c](https://github.com/mpv-player/mpv/blob/master/audio/out/ao_audiotrack.c)，保存 `/tmp/p9-mpv-upstream-audiotrack.c`，同样直接把 `ao_read_data` 的0样本交给写入，因此整体更新上游不能解决该边界。固定本地mpv基线仍为 `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`，不引入上游新提交。
- A级平台证据：[Android15 AudioTrack.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/media/java/android/media/AudioTrack.java)，已读 `write(ByteBuffer,int,int)` 与WRITE_BLOCKING说明；size=0是有效请求，blocking只针对所写数据，不保证零字节请求等待。保存 `/tmp/p9-aosp15-AudioTrack.java`。
- B级成熟本地实现：现有compressed音频分支无帧时 `mp_cond_timedwait(...,20ms)`；同一 `p->wakeup` 被start/uninit唤醒，cond等待释放mutex，适用于PCM的暂时无数据。`buffer.c::ao_read_data`可能因断粮/EOF/锁竞争返回0，不采用不可中断sleep或持锁阻塞读。
- 上游issues精准搜索 `repo:mpv-player/mpv audiotrack cpu`，仅找到不适用于本边界的AAudio新后端PR12261和scaletempo默认值issue8376；不采用换后端/时间伸缩算法。论文/泛博客不适用：这是已复现空读条件遗漏而非新调度算法，不做性能理论优越性主张。
- 比较：不改=持续空转；原样上游=同缺陷；窄适配=PCM样本数<=0时等待最多20ms并继续，复用现有唤醒/退出条件。正常PCM写入、短写/死对象恢复、压缩直通、时间戳、JNI/ABI、VM/渲染完全不变。不存在音质/声道/解码降级。
- 验收：提取实际函数验证空读零次写入且每轮有等待、空读后恢复、暂停/退出、正常完整/短写及dead-object旧路径；旧源码同一门槛失败。两ABI只增量编译该C文件与链接，不重编FFmpeg/JNI；资产校验、手机打包安装；真机至少3个短窗口检查AudioTrack CPU不再45–53%空转及缓存预读/掉帧。失败则保留日志并继续窄修，不宣布全P9通过。
- 回滚：撤销本小补丁及构建接线并恢复两ABI匹配libmpv；保留之前Java缓存/FIRST PLAY修复。整体恢复锚点为上述tag。无新用户设置、安全权限、协议/依赖升级；不push。

### Checkpoint 20 验证与用户要求的恢复点（2026-09-08 15:16 Asia/Shanghai）

- 实际提取的完整 `ao_thread()` host桩回归7项通过，覆盖空读等待/恢复、暂停启动/退出、正常写/短写/dead-object及compressed空帧分支；旧函数在 `zero_writes == 0` 断言失败。这不是设备生命周期的完整验收。
- armv7增量构建成功；arm64直接ninja受pkg-config环境和残留armv7 iconv路径影响失败，改用项目 `buildall.sh -n --arch arm64 mpv` 后成功。未重编FFmpeg/JNI，功能列表与FIRST PLAY基线一致；双ABI ELF/SONAME/依赖及资产规则检查通过。
- Mobile arm64 debug构建成功，installer assist返回Success并启动App。安装日志：`/tmp/p9-menu-device-20260907.Vq7vyl/audio-underrun-install.log`。Java缓存14项回归与Leanback Java此前已通过，未重复运行。
- APK SHA-256：`21e6b884c250c53d4c2e09472810e1441de3ec550219b8da7d8e823f8c388c59`。
- arm64 libmpv SHA-256：`30c1ed7f3d516eb2e172b7f87f4e1b5ae6537dd7c759501de348adaba1ee667f`；armv7：`70852b751701decde812d8157d0bd2c34a282522b0037719dce56f34160f07e9`。
- 新audio underrun patch SHA-256：`88e6eeb356adb64641177bfd83f2f60e994787a2cba346be78b2c86f451e95bf`；discnav patch未变。此前guard及scoped源码/文档diff检查通过；整仓checkpoint脚本的既有discnav patch空白context告警不能记为通过。
- 用户先表示“视频肉眼可见的不卡顿了”，要求先tag；随后澄清“掉帧还是存在一点，不过好很多了”，CPU约60%。据此只记录显著改善，不把剩余掉帧视作正常，也不声称AudioTrack CPU已实测回归。
- 已知待确认：稳定正片/菜单的剩余掉帧、音频线程占用、最近观看缺少《豪斯医生》；此前关闭菜单历史复播、《幽灵公主》完整流程、《超脱》和Surface direct边界未全部验收。恢复点是用户明确要求的已测试快照，不是发布/全功能通过标签。保持BD-J静默回退、DVD现状、用户配置和手机数据不变，不push。

## Checkpoint 21：2026-09-08 已测试恢复点与后续反馈修复

- 用户明确要求先tag并继续解决音频/CPU、少量掉帧、开场卡顿、最近观看缺失、重缓冲始终0、预览章节页无法退出。已原子提交 `55a6365a9ff8c1a096ceb4275d50b95c1ed5f488`，本地注释tag `recovery/P9-MPV-BLURAY-MENU-FIX/20260908152234-55a6365a9ff8`，未push。保存的是用户测试后要求保留的阶段状态，不是全P9验收。guard原先被Git元数据权限和docs忽略规则阻挡；只对准确任务文档强制暂存，未修改守卫或忽略规则。
- 本轮guard `P9-MPV-BLURAY-MENU-FOLLOWUP`，保护35个既有 `app/.cxx/`文件。允许路径见guard scope：P9 Java状态/缓存、PlayerManager、PlaybackActivity、Mobile/Leanback VideoActivity、DiscMenuDialog、定向测试、P9 native补丁/构建/双ABI资产及本文件；不改其它播放器或用户配置/数据。
- 现场：`/tmp/p9-menu-device-20260907.Vq7vyl/submenu-current.log`、`submenu-audio-cpu.txt`、`audio-after-playback-1523.log`、`history-current.db`（本地只读快照）。最新菜单窗口15个1秒样本（排除首样本）：App线程合计52.07%，ao/audiotrack平均0.40%峰值1%，明显不同于原45–53%空转。该窗口不是正片完整性能验收。菜单暖播放decoder/output drop为0；开场约33Mbps、Range 4MiB约1.4秒，仍会供数不足，不能把开场掉帧称为正常。
- 历史根因：`VideoActivity.onTimeChanged/updatePlaybackHistoryPosition` 为防菜单多playlist时间线污染而跳过位置更新；`History.canSave()`仅允许position>0，首次导航观看因此从未保存访问记录。修复必须保留旧正片进度，不写入假的1毫秒、不把菜单时间当正片续播。
- 计数根因：`MpvPlayer FILE_LOADED` 在nav模式设置file-local cache-pause=no；`PlayerManager.recordBufferingState`只认STATE_BUFFERING，所以此模式的实际取数停顿未计入。不能重新启用依赖禁用read-ahead的cache-pause，也不能把每次低缓冲/掉帧/正常still计为重缓冲。
- 菜单证据：用户澄清预览项可选择播放，只有退出/其它底部按钮失效；本机方向键能移动缩略图，popup键未关闭此HDMV页面。原生NAV_CMD忽略libbluray返回值并一律STREAM_OK，Java的result=0不能证明光盘接受了输入。尚需验证实际返回/主菜单分支；不篡改光盘GPR/UO或假造鼠标成功。

### 最佳实践与最小决定（延续既有设计）

- A级来源：当前提交的Java调用链及上述真机证据；锁定libbluray1.4.1 `bluray.c::_try_menu_call/bd_user_input/bd_mouse_select`、`graphics_controller.c::_user_input/_mouse_move`及`keys.h`，证明ROOT和POPUP是不同操作、按钮受当前page约束、没有通用HDMV BACK键。MPV `playloop.c::handle_update_cache`与本地FILE_LOADED的file-local override解释漏计。
- B级交叉实现：2026-09-08经指定代理读取 [VLC bluray.c](https://github.com/videolan/vlc/blob/master/modules/access/bluray.c) `DEMUX_NAV_MENU/POPUP`，root失败才fallback popup；[Kodi DVDInputStreamBluray.cpp](https://github.com/xbmc/xbmc/blob/master/xbmc/cores/VideoPlayer/DVDInputStreams/DVDInputStreamBluray.cpp) `MouseClick/OnMenu`检查实际返回值并作popup/root fallback。下载快照位于上述证据目录；不是引入这些浮动master提交。
- 对预读，沿用Checkpoint18的原始字节缓存/取消/容量设计及测试，不改变VM媒体read-ahead。比较：不改=33Mbps开场被单请求约24Mbps限制；整体升级上游=不解决Java raw-source调度；窄适配=仅现有导航磁盘缓存路径使用最多2个预读worker、最多4页前视，仍8页内存及既有共享磁盘预算，跳转替换排队范围。实测是否改善需装机对照，不能声称突破总带宽限制。
- 对计数，采用“已开始播放、未用户暂停/seek、输出位置持续不前、无可播数据且原始ISO需求读取在等待”的联合条件，独立计数后与既有面板/telemetry统一；只观察，不改变MPV暂停或VM行为。普通媒体仍保留既有STATE_BUFFERING统计。用纯Java时间序列测试排除启动/暂停/still/仅低缓冲/短暂等待，覆盖持续停顿一次计数、恢复再停及重置。
- 官方API证据来自锁定源的公开头文件/实现；上游提交/issue沿用Checkpoint18–20对该固定基线的结论，这轮不引入上游候选。论文/泛博客不适用于页面命令/历史canSave/明确漏计条件；并行预读不主张新算法优越性，以有界并发、取消测试和同源真机性能为gate。
- 验收：定向Java测试、Mobile构建与共享调用点Leanback编译；同ISO开场→菜单→预览页退出/选择正片→最近观看；音频/掉帧至少三个短窗口。若确需native改动才运行实际函数host测试及双ABI增量构建/资产校验。当前改动未经验证前不得提交；回滚锚点为本Checkpoint的已测试tag，保留此前音频修复与FIRST PLAY接线。

### Checkpoint 21 阶段性保存（用户明确要求先tag，2026-09-08 17:54 Asia/Shanghai）

- 已实现：Mobile/Leanback允许已启动HDMV导航保存首次访问记录，仍不把菜单时间当作可续播的正片进度；导航磁盘缓存路径最多2个预读worker、4页前视，保留8页内存和跳转/关闭边界；`MpvDiscRebufferTracker`只在已开始且未暂停/seek的播放中，联合判断位置不前、可播缓冲不足、原始需求读取等待，统一接入面板与telemetry计数，不重新开启导航cache-pause。
- 验证：`MpvDiscMenuPolicyTest`14项、`MpvDiscRebufferTrackerTest`7项、`IsoPageCacheTest`13项、`HttpRangeIsoSourceTest`3项，共37项，failures/errors均为0；Leanback Java同次构建通过。日志 `followup-java-tests-jdk21.log`。首次命令因不存在的IDE内置JDK路径未启动，改用现有独立JDK21后成功，未安装新工具链。
- 手机APK构建通过，日志 `followup-java-apk.log`；SHA-256 `a40ea5f45bcd4ed9c975cd412002c6fbd70e6f85ff0b4776db98c5873fa28aa3`。`followup-java-install.log`记录包指纹已改变、终止陈旧adb安装等待并成功启动App，脚本exit=0。以上日志均位于 `/tmp/p9-menu-device-20260907.Vq7vyl/`。
- 未验证/未解决：新包开场供数、实际重缓冲计数、最近观看显示及正片连续播放；预览选集页可以移动/选择项目，但有效退出路径未确定，也未提交“菜单退出已修复”的代码。旧包菜单采样ao/audiotrack平均0.40%/峰值1%仅证明该窗口空转改善，不替代新包完整性能验收。
- 用户要求立即保存本轮状态，因此不重跑测试、不继续操作手机、不把更广的P9验收写成通过。guard只提交本轮task-owned文件，保护 `app/.cxx/`；恢复tag由本次finish创建，未push。后续以这份文档为唯一记录继续真机验证及菜单退出修复。

## Checkpoint 22：2026-09-08 Kodi对照，菜单输入与退出修复

- 前轮已提交 `f4e4f9e16fb6b99bf72f8a19a5cb184a488e0580` / 本地注释tag `recovery/P9-MPV-BLURAY-MENU-FOLLOWUP/20260908181013-f4e4f9e16fb6`，未push。用户继续授权修复，明确要求参考Kodi。当前工作区仅既有 `app/.cxx/`脏，保护35个文件；guard已重新建立为 `P9-MPV-BLURAY-MENU-INPUT`。
- 20:57 Asia/Shanghai计划：修复与回归10–15分钟、双ABI增量及APK8–12分钟、设备约10分钟，目标21:30左右。范围仅菜单输入Java接线/测试、独立native补丁及build接线/两ABI产物、本文件；不重做音频/缓存设计。

### 决定性问题与证据

- 观察：用户在《豪斯医生》《倩女幽魂》等HDMV主菜单展开章节/设置子页后，仍可点子页项目播放，但不能通过其它主菜单按钮切换或返回；短片段期间原盘主菜单有时无反应。之前日志仅有Java `disc navigation ... result=0`，此值由底层无条件STREAM_OK转换而来，不证明光盘接受操作。截图/日志沿用 `menu-exit-latest.*`、`ghost-down.png`。
- A级固定源：MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 加已提交P9补丁。`MpvPlayer.sendDiscNavPointer`调用两次命令：先`mouse x y`再`discnav mouse-click`；`player/command.c::cmd_mouse`只将位置更新入input队列，`input/input.c::mp_input_read_cmd`取出该事件后才更新`mouse_x/y`。同步client命令在另一dispatch链执行，因此第一命令返回不保证第二命令读到新坐标。可证伪假设：旧输入队列未消费时，导航命令会使用旧坐标；单次命令直接携带当前窗口坐标可消除该时序依赖。
- A级固定源：libbluray1.4.1 `bluray.c::bd_mouse_select/bd_user_input/_try_menu_call`、`graphics_controller.c::_mouse_move/_user_input/gc_run`及`keys.h`。鼠标必须命中当前页可选择按钮；root返回1成功/0失败；popup与root不同，HDMV没有通用BACK键。`BLURAY_PLAYER_SETTING_UO_RESTRICTION_DISABLED`公开注释明确可能破坏播放，因此本轮不关闭UO或修改光盘寄存器。能否中断受限短片段仍需实际命令结果判定。
- B级对照：[Kodi DVDInputStreamBluray.cpp](https://github.com/xbmc/xbmc/blob/b2637ca499afe69f9d15c928809ffd6c42144250/xbmc/cores/VideoPlayer/DVDInputStreams/DVDInputStreamBluray.cpp)，该文件最后变更commit `b2637ca499afe69f9d15c928809ffd6c42144250`（2026-09-08 API读取）。已读MouseMove/MouseClick/UserInput/OnMenu；[VideoPlayer.cpp](https://github.com/xbmc/xbmc/blob/master/xbmc/cores/VideoPlayer/VideoPlayer.cpp)将当次窗口坐标变换后直接传给输入流；头文件OnBack调用OnMenu。OnMenu先popup失败再root，MouseClick检查选择结果。下载快照 `kodi-bluray.cpp/.h`、`kodi-videoplayer-menu.cpp`及`kodi-bluray-source-revision.json`。仅行为对照，不引入Kodi源码或新依赖commit。
- B级补充：已读VLC `DEMUX_NAV_MENU/POPUP`，明确root调用失败才fallback popup；Kodi libbluray依赖patch目录只有平台构建/加载兼容补丁，不据“完美支持”假定其绕过所有光盘UO限制。
- 证据类别范围：这是已实现菜单设计内的输入时序/结果遗漏修复，官方公开API及实际源已能决定方案；不新增上游候选。此前P9记录涵盖上游实现/讨论，本次不再泛搜论文/帖子（没有新导航算法或性能理论主张）。下载均使用用户指定代理。

### 决定、验证与回滚

- 不改：两条命令间坐标时序及“成功”假象仍在。照搬Kodi：它不经libmpv输入队列，不能原样移植。窄适配：为已有discnav命令追加可选窗口坐标模式，Java只发一次携带点位的命令；保留原归一化坐标和无坐标键盘/鼠标兼容。native只对有效命中激活，记录真实结果；root失败才试可用popup，返回根据真实popup支持使用popup/root。不硬编码影片按钮/页号、不强制toggle作者未定义的菜单。
- 新native改动用独立 `third_party/mpv-player-jni/patches/mpv-discnav-input.patch`，紧随原discnav补丁，不改固定锁、不重编FFmpeg/JNI；正常播放、BD-J静默最长标题回退、DVD现状、原始页缓存及音频等待保持。
- 最小验证：实际cmd_discnav与坐标转换host测试，覆盖旧队列位置不影响直接点位、归一化旧调用、边界/黑边/缩放、无效点不激活；libbluray返回失败不伪成功、popup/root回退。Java命令形状定向测试；两ABI一致源构建及资产验证；Mobile打包安装后以主菜单→章节→设置/退出→短片段菜单的实际状态为验收。不用host测试替代真机。
- 回滚：移除独立输入补丁及构建接线，恢复前轮tag的Java命令和成套libmpv资产；保留其它已提交P9修复。下一步只做上述定向实施/验证，不追加播放器升级或广泛研究。

### 输入修复实施状态（2026-09-08 22:02 Asia/Shanghai）

- 已实现：Java单条`discnav mouse-click/mouse-move x y window`；native新增可选坐标空间，保留归一化及无坐标调用。转换反算`push_bd_overlay`实际使用的`osd_get_vo_res`矩形（带锁快照），不在core线程调用仅限VO线程的`vo_get_src_dst_rects`；负边距保留放大裁剪映射，黑边及画面外点拒绝。
- 已实现：`bd_mouse_select`必须返回1才激活；root的0失败与`bd_user_input`的0非错误分别处理，后者仍可能没有界面变化，日志明确记录hit/root/input/accepted/popup，不把0推断为已切页。Popup支持状态在overlay锁内取快照，调用libbluray前释放，避免overlay回调死锁。root失败才试可用popup；prev/popup按实际支持选择popup或root，不改UO。
- 新增独立`mpv-discnav-input.patch`，接在既有discnav补丁之后；未改锁、JNI、FFmpeg、音频和缓存。`MpvPlayer.sendDiscNavCommand`保留输入后的停顿计数抑制策略。
- 定向host测试已通过：直接提取实际坐标函数、`cmd_discnav`、Blu-ray控制分支和激活分类函数，覆盖旧input队列位置、归一化边界/兼容、NaN/画面外点、黑边/缩放/裁剪、未命中不激活、错误不触发demux drive、key返回0合法、root/popup/prev回退及非HDMV拒绝。测试脚本`third_party/mpv-player-jni/tests/test_disc_navigation_input.sh`。
- 当前进行双ABI增量及Java定向测试；原21:55目标已延后，时间消耗主要为原生转换/返回契约和回归测试实现，不再扩展研究。ADB无设备，构建通过也不代表用户反馈已真机验收。

### 候选实测失败与恢复点（2026-09-09 Asia/Shanghai）

- 构建/静态结果：两ABI增量构建成功（`input-arm64-native.log` / `input-armv7-native.log`）；stage和`--require-elf`成功（`input-stage-assets.log` / `input-verify-assets.log`）；16项`MpvDiscMenuPolicyTest`及Mobile/Leanback Java编译成功（`input-java-tests-authorized.log`）。独立patch按锁定MPV + disc-controls + discnav顺序应用校验、对真实build源码reverse校验通过。新patch空白context已规范化并重新确认可应用；未修改旧大patch。
- 产物：arm64 `libmpv.so` SHA-256 `2298a4e860e10f5af6a96d8853157a6cfdcc19b07195c0be0822af4ade07b069`；armv7 `b3377c17b1f113305e40d9d97ddaff6098aa8a77492ea7d01488724271cab0db`。仅libmpv资产变化，JNI/FFmpeg资产未变。首个候选APK SHA-256 `ec88362ed19c7c8b7508159a3a75b0219261e47b9ba0918c92302fdb3b18a458`，zip内libmpv与arm64资产hash相同。
- **用户否定验收**：2026-09-08 22:23已成功安装首个候选（`input-debug-install-reconnected.log`），用户随后明确反馈仍不能退出/切换子页，短片段中仍不能强制打开菜单。本轮坐标/结果检查修正不是用户问题的完整解决，不得标为通过，不commit/tag。
- 实际证据：`input-user-failure.log`中22:23:57–22:24:14的8次`menu`都返回`-12`，并非成功但画面没响应；章节子页大部分mouse-click返回0（libmpv命令成功），部分返回-12。代理在诊断包亲自复现《倩女幽魂》主菜单→章节页→点设置，高亮变到设置但章节页仍在（`input-ghost-chapters.png` / `input-ghost-setup-click.png`），不能解释成点位整体失效。
- 已核实设备实际加载的`app_mpv-libs/arm64-v8a/libmpv.so` SHA-256与候选相同；不是旧native资产残留。
- 诊断遗漏及修正：`MpvPlayer.shouldDebugLogMpvLine`原过滤掉新的native `discnav action=`，已补白名单并构建安装（`input-diagnostic-apk.log` / `input-diagnostic-install.log`）；随后发现`MpvPlayerEngine`默认`all=warn`还会屏蔽native INFO。在允许路径`MpvPlayer`里追加`bd=info,bdmv/bluray=info`仅放开蓝光流结果，不开启全局verbose。该最后一版已成功构建（`input-diagnostic-level-apk.log`），安装时设备断开（`input-diagnostic-level-install.log`：device not found），**尚未部署**。
- 未决原因：root请求实际失败（UO/状态/其它原因仍需区分）；子页按钮高亮成功但导航命令执行/页面转换未完成。不得直接认定UO是全部原因，不关闭UO、不硬编码影片页号、不继续重复坐标假设。`demux_drive_nav`目前仅强制读一个packet；slave自身缓存是否使VM pump延迟是待证伪问题，尚未实施新改动。
- Git/保护状态：仍在`feature-menu`、guard `P9-MPV-BLURAY-MENU-INPUT`，基线`f4e4f9e16fb6b99bf72f8a19a5cb184a488e0580`；本轮task-owned源码/patch/测试/双ABI资产/文档均未提交，既有`app/.cxx/`保持保护。所有上述日志/截图目录均为`/tmp/p9-menu-device-20260907.Vq7vyl/`。
- 唯一下一步：连接手机后安装已构建的最终诊断包，在同一章节页采集一组`mouse-click`、`prev`、`menu`的实际native结果与前后截图，以此选择下一处窄修复；不重做已完成Kodi调查或双ABI构建。

### 2026-09-09 设备输入结果与导航pump窄修复

- 已取得完整输入结果（`iso-level-chapter.log`/`iso-level-setup.log`）：当前窗口坐标命中，`hit=1 input=1 accepted=1 popup=0`，章节页→设置只有按钮高亮变化；`iso-level-popup.log`中root=0。ISO入口复用了`iso`日志前缀，Java已仅对iso/bd/bdmv开启info并保留discnav白名单。
- 当前证据决定先修导航pump缺口，不推断root失败一定由UO造成。现有`demux_drive_nav`只迫使外层读一个packet，而`d_read_packet`可以一直取slave缓存，不保证调用`bd_read_ext`；libbluray只在`bd_read_ext`执行挂起的菜单指令。旧host测试只测fill_buffer，未覆盖缓存不触达底层的条件。
- 设计比较：不改会保留输入执行依赖媒体供数；直接在core线程调用libbluray事件pump会与demux/stream的状态变更交错；窄适配新增内部`STREAM_CTRL_NAV_POLL`，输入成功仅设置锁保护pending，外层demux每次取packet前消费pending并执行既有零字节pump，之后再检查真实discontinuity并重建slave。方向/hover也可能由作者定义自动动作，因此成功输入均唤醒pump。不增加媒体预读，不改光盘寄存器/UO，不模拟菜单弹窗。
- 范围仍是已声明的独立MPV输入patch、build源码、定向测试、双ABI产物/Java日志接线及本文件，不新增上游依赖或JNI API。接受标准：缓存中已有packet时点击仍推进VM；仅页面变化不flush解码；真实播放列表跳转仍按既有代数重建。Rollback为移除独立patch/成套libmpv与对应Java参数恢复f4e4恢复点。
- 用户持续授权修复此功能；本次为已确证输入接线后的执行契约补全。必须host故障用例和同一真机章节→设置/返回结果验证，不能以编译替代。

- pump候选两ABI编译、资产校验与新增缓存packet回归通过，已安装；`pump-setup.log`明确`input=1`后`pump change=23->23`，仍未切页。因此pump缺口确实补齐但不是此盘子页问题的完整根因。`uo=3`=MENU_CALL|TITLE_SEARCH，已确认root调用被光盘限制，不再猜测坐标或漏pump。
- 当前临时诊断：仅build目录`stream_bluray.c`加无owner指针的Android日志回调并开启GC/HDMV trace；原始pump源码保存`/tmp/p9-input-native.gZWNU6/stream_bluray-pump.c`。此临时改动未进入独立patch，arm64诊断产物与armv7暂不同，**严禁commit/tag这个临时状态**；取证后必须移除临时hook并恢复双ABI一致构建。下一步仅安装trace包，读取同一按钮的实际导航bytecode/VM结果。

### 2026-09-09 VM取证与显式菜单请求的窄适配

- 设备 `10CF6H1D2L0009S` / vivo V2453A / Android 15；证据目录 `/tmp/p9-menu-device-20260907.Vq7vyl/`。`vm-setup-after.log`显示章节页4的按钮29确实激活，之后没有新的HDMV命令；`vm-panel-down.log`显示从章节预览选中项按下后自动激活不可见按钮33，执行 `SET_BUTTON_PAGE 0x80000001,0x80000002` 回到主页面2。不能再以点击成功或overlay变化作为切换通过。
- 这说明该盘章节页的底部按钮并不是主页面按钮的通用副本，物理遥控器是通过作者提供的方向图返回。Kodi同样调用 `bd_user_input`/`bd_mouse_select`，并没有通用HDMV BACK API（`keys.h`仅有ROOT/POPUP/方向/ENTER）。不采用全盘固定DOWN、不写寄存器或页号、不在无可验证动作时强制重播原点位；触摸适配需要libbluray可验证的菜单关系，另行请求新增一份依赖补丁的范围授权。
- 短片段返回菜单的已证实失败独立处理：libbluray 1.4.1 `bluray.c::_try_menu_call`在默认RELAXED级别仍拒绝 `uo_mask.menu_call`，与手机 `root=0 uo=3`一致；`bluray.h`公开 `BLURAY_PLAYER_SETTING_UO_RESTRICTION_LEVEL`，关闭限制可能绕过盘的初始化。上述锁定源码为A级证据；此前保存的Kodi `OnMenu`参考revision `b2637ca499afe69f9d15c928809ffd6c42144250`为B级证据；访问日期2026-09-09。
- 比较：不改/照搬Kodi都保留被UO拒绝的调用；全局关UO会改变所有导航且可能破坏FIRST PLAY；采用最窄适配：用户明确点击ROOT/TITLE或非Popup返回时，先正常调用，仅当失败且MENU_CALL被屏蔽、已向播放器供过媒体字节时，对这一次 `bd_menu_call`临时使用DISABLED，之后无论结果如何立即恢复库默认RELAXED。自动开盘仍只 `bd_play`，不自动强跳；无菜单盘、BD-J、未开始供数、非UO错误均不放开。
- 用户已反复明确要求短片段中“强制弹出菜单”，本次属于已批准行为；不扩大音频、缓存、解码或其它UO策略。风险：个别盘可能在片头结束后才完成其它寄存器设置，故不能宣称所有盘都可强跳；需要同盘片段和菜单/正片回跳真机检查。验收：正常菜单先走原调用，受限调用仅一次放开且恢复限制，初始化/其它错误不越过，静态host覆盖及真机真实主菜单画面。Rollback：移除此窄helper及对应测试，成套恢复原独立输入patch/双ABIlibmpv。

### 2026-09-09 10:17 用户否定候选，补充有界debug

- 最新证据 `user-failure-1018-logcat.log`：10:03:11/15 的root override返回1，但后续多次点击只有高亮，pump代数不变；其中一次主线程同步discnav达3056ms，ANR诊断记录1717ms native等待。`user-failure-1018.png`仍停留章节弹窗。不能把返回值1等同完成切页。
- 日志方案：仅debug App放开蓝光模块debug等级；MPV接入libbluray公开的 `bd_set_debug_handler` 与 `DBG_HDMV|DBG_GC|DBG_CRIT`，通过调用线程的临时上下文把指令输出送入现有MPV/App日志。只捕获菜单相关源行，限制每次/每秒数量；不写额外无限增长的手机文件，不记录媒体地址，不引用释放后的stream。普通/release配置不开启详细跟踪。
- 记录Java命令/坐标、初始化/菜单可用性/Surface状态、同步耗时与延迟状态快照；native记录输入序号、点击命中/激活/UO重试分段耗时、VM指令、菜单事件、page/按钮信息及generation。此处统计的是执行跟踪，不臆测按钮定义中的指令数量。
- 保持已有菜单语义不变；源依据为锁定libbluray 1.4.1 `util/log_control.h`/`logging.c`（公开无owner的进程级日志回调）及现有GC/HDMV日志，MPV `osdep/threads`线程局部与once机制；这是局部诊断扩充，不引入新的导航设计或依赖版本。验证重点是过滤/限额/上下文释放、两ABI可构建、日志实际可达及相同失败的完整操作链。范围沿用当前guard，新增测试仅在已允许tests目录。
- 静态验证：`test_disc_navigation_trace.sh`通过正常/禁用/过滤/每次96行/每秒600行/线程隔离/调用后无owner；`test_disc_navigation_input.sh`及`test_disc_navigation_progress.sh`通过。第一次input测试与arm64编译都暴露同一个新增计时变量 `started` 和旧bool重名，已改为 `call_started`，重跑相关测试及arm64构建成功；未重复无关已通过测试。
- 产物及构建记录：`debug-chain-arm64-fixed.log`、`debug-chain-armv7-native.log`、`debug-chain-stage.log`、`debug-chain-verify.log`均成功；`debug-chain-apk.log` Mobile debug与Leanback Java编译成功。arm64 libmpv `a8e3938d49010f1f67e293a547367712ab490b4af57fbe9b0c160ada220ec3fc`，armv7 `251e24a586a5e822ee703060a32c102acbd083f1966d6dae726df4d4b4b82288`，独立patch `867b3e726f541a08182f37277b362a2aee26c547ddda3bb43a9f6515aa19bbae`；实际build源reverse校验通过，未改libbluray源码。
- 检查点脚本 `debug-chain-checkpoint.log`：0 errors，1 warning仅提示本任务拥有的双ABI修改，已与guard scope核对；既有 `app/.cxx/`未接管。本轮源码和产物继续未提交，因为整个菜单修复单元仍有真实功能失败，不能安全声明完成并tag。

### 2026-09-09 诊断包真机结果（继续取证，非修复验收）

- `debug-chain-install.log`安装成功；手机运行中 `app_mpv-libs/arm64-v8a/libmpv.so` SHA-256为 `a8e3938d49010f1f67e293a547367712ab490b4af57fbe9b0c160ada220ec3fc`，与仓库产物一致。无需重装或重建来证明同一版本。
- 12:33:59 `request=2/seq=2`：点击章节正确激活主页面2的按钮1，随后 `SET_BUTTON_PAGE` 2→3→4，章节页实际出现。
- 12:34:04 `request=4/seq=4`：点击设置坐标由窗口 `(1350,982)` 映射到盘 `(1110,982)`，第4页按钮29激活，`user-input=1`仅3ms；pump完成0ms，change保持23，之后未执行任何切页指令；1秒后播放器仍active且章节弹窗保留。证据 `debug-chain-setup-app.log`、`debug-chain-setup-failed.png`。**“零指令按钮”仅为假设**：上游现有日志未打印 `num_nav_cmds`，还需在真实按钮/GC结果边界区分零指令、被覆盖、VM拒绝。
- 12:41:12 `request=5/seq=5`菜单键：ROOT正常调用被UO拒绝，临时重试成功，VM重进object1，PLAY_PL0后page0→1→2；图像确认该场景回到主页面。证据 `debug-chain-menu-return.log/.png`。这不证明所有返回场景或用户的其它盘通过。
- 13:26:03–06 `request=11/12`：启动预告后进入TITLE2/PLAY_PL2，当前菜单不可见、`uo=2`；约2.5秒后菜单键调用正常ROOT（无需UO重试）成功，pump114ms回TITLE0/PLAY_PL0。随后盘VM检查自己先前设置的r21=1，清为0，执行 `SET_BUTTON_PAGE` 到12，即特别收录子页。因此“返回后仍见同一弹窗”至少在这一场景是作者显式恢复路径，不是播放器没执行请求。证据 `debug-chain-trailer-menu.log` 与菜单后截图；切片前截图在黑场，不能据此确认完整预告画面，但后续 `debug-chain-trailer-visible.png`已捕获00:08/02:03实际预告画面。
- 底栏入口尚无有效复现：`debug-chain-toolbar-attempt.log`对应尝试中屏幕横竖切换，预期 `discMenu`控件不存在，脚本按未命中停止。不得把菜单键路由当作底栏成功证据；需要下次用稳定同一屏幕在片段中读取底栏控件即时操作。
- 诊断范围交付：有界详细日志已真实可达、关联到按钮/VM/事件/返回/耗时，用户反馈依然成立；源码/patch/双ABI产物和既有未验收修复处于同一guard，不能为了保存日志把整个候选提交成已验证修复。需扩展到libbluray内部只读取证/适配时，使用独立依赖patch并先取得明确范围授权。

## Checkpoint 24：2026-09-09 原盘渐进读取优化（已批准）

### 基线、问题与授权

- 用户先要求分析《豪斯医生》《倩女幽魂》片头卡顿和底栏原盘菜单延迟，随后明确“优化”。已完成诊断保存在 `/tmp/p9-opening-latency-20260909.EiUyOp/diagnosis.md`，不重做通用菜单研究。原始日志含私有地址/请求头，只公开脱敏计时。
- 基线 commit `310f8feef5c0a05e5dae6c0a063453113214ea2c`；APK SHA-256 `13a7dfb6faf4567cbd95a90096c78ab8157d0e98be9903f79b8c14640cf55ce7`。arm64/armv7 libmpv 分别为 `eddfed3df1e1cea3b263a42778088ba3cf70f94dde6f08839357957084723948` / `371303839f5a5bd0a3724bd4fb84449d4ebae62a38cda111682c694598b86370`，本单元不重建或修改它们。
- 设备10CF6H1D2L0009S（vivo V2453A，Android15）已连接。豪斯医生 `p-z7gpfl-1` 点击到menu-active/restart为6.427/6.678秒，其中前台4MiB读3630ms、导航pump3682ms；倩女幽魂 `p-z7iyho-2` 为6.104/11.177秒，前台4MiB读2525ms，重探测又读4MiB耗4982ms。Java命令分别15/4ms返回、强制返回VM请求0ms成功，不是按钮不响应。
- 豪斯医生片头约33Mb/s，出现584+1009ms供数停顿，硬解已启用、解码丢帧0；倩女幽魂补测前两段没有持续丢帧，等待集中于后续短片/菜单切换，共8289ms。不能把loopback代理Range计时当作独立线路测速，也不能保证冷远程菜单零等待。

### 最佳实践审阅与本地调用链

决策问题：不提前驱动光盘VM、不增加既有缓存预算，能否让当前所需数据早于整页完成而可用，并让跳读替换过时的预读？

| 来源与查阅日期（均2026-09-09） | 等级、支持结论、适用边界与决策影响 |
| --- | --- |
| 本仓库基线 `310f8feef5c0a05e5dae6c0a063453113214ea2c`，`IsoPlaybackSession.readAt → IsoPageCache.readAt/page/load → HttpRangeIsoSource.readAt` 及对应测试 | A：整页4MiB完成并同步写盘后才发布future；需求命中pending也等整页；跳读只撤销排队提示。现有8页LRU、共享磁盘预算、长度/validator检查和close取消是必须保留的契约。 |
| 固定MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` 加当前补丁后的 `demux.c` 导航预读限制、`stream_bluray.c::STREAM_CTRL_GET_NAV_STATE`、`demux_disc.c::reopen_slave` | A：整个导航会话都禁止普通解复用预读；菜单逻辑就绪不等于背景首帧。只优化原始字节，不移植新MPV提交或改变轨道重建。 |
| [Kodi 21.2 FileCache.cpp](https://github.com/xbmc/xbmc/blob/d1a1d48c3cb3722d39264ffdd8132f755ffecd27/xbmc/filesystem/FileCache.cpp)，`d1a1d48c3cb3722d39264ffdd8132f755ffecd27`，已读 `Process/Read/Seek` | A/B：`Read` 有数据即可返回、仅等待必要数据；后台收到seek后以最新位置重置。参考生产者/消费者分离原则，不照搬Kodi有状态顺序文件源到本项目并发随机Range。 |
| [OkHttp ResponseBody.kt](https://github.com/square/okhttp/blob/61423f472da24e0ccc42b6a2c0863fb27932fea5/okhttp/src/commonJvmAndroid/kotlin/okhttp3/ResponseBody.kt)，parent-5.4.0，commit `61423f472da24e0ccc42b6a2c0863fb27932fea5`，已读流式/关闭契约 | A：响应体是一遍消费的流，不要求整包缓冲；跨线程消费仍由消费线程关闭。使用同一Call渐进发布，取消具体请求，保留activeCalls直到body关闭。 |
| [RFC9110 §14.4、§15.3.7.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.4)，正文已读取 | A：严格限制Content-Range边界；续读从未发布字节开始，校验源长度/validator，不能重试覆盖已交付的数组区间；只完整页落盘。没有强validator时不能主张普适HTTP缓存一致性：继续现有会话内固定ISO假设，不跨会话/URL复用。 |
| [Kodi PR29133](https://github.com/xbmc/xbmc/pull/29133)、[PR29038](https://github.com/xbmc/xbmc/pull/29038)，已读完整PR说明，未移植未发布实现 | B/C：seek后陈旧恢复不能覆盖新位置，网络停顿不能伪装EOF。作为并发/取消/截断测试的反例来源，不把待审PR或其性能报告当作本候选通过。 |
| 现场报告/benchmark：前述两片日志及后续同机对照 | A/C：直接匹配本App、设备、代理和原盘；以保留原始计时和可控慢源测试验证收益，网络波动必须报告。 |
| 学术论文/通用博客 | 不适用新的算法优越性判断：本单元保留LRU、已有磁盘协调器和有界预读，只拆分数据发布/完整缓存提交时机，不引入新的预测算法或码流处理。不会以论文中的不同负载吞吐替代这里的确定性测试与手机计时。 |

研究到此收敛；OkHttp Relay旧目录两次404后已放弃，改以实际读取的Kodi生产代码验证同一设计问题；不将失败页面当作证据。本单元没有新增/合并上游commit，以上外部revision仅为参考。

### 方案、边界与选择

- 不改：仍以数秒整页读阻塞小范围菜单/索引需求，不能达到本轮目标。
- 原样套用上游：MPV普通解复用预读会提前推进VM；Kodi顺序文件缓存不能直接代替并发Range和共享预算。均不采用。
- 全局缩小页面：增加高码率媒体请求次数、影响关闭菜单路径，拒绝。
- 采用WebHTV窄适配：只有原盘菜单设置开启且成功建立会话磁盘缓存时，通过 `IsoPlaybackSession` 使用新增 `ProgressiveIsoPageCache`；否则完全保留原 `IsoPageCache`。页面仍4MiB、LRU仍8页（32MiB），在途读取最多3个，与原两路预读加前台读数量相同；前向窗口最多4页，无无限线程/队列/VM预读。
- 每页一个生产者，当前读点附近优先发Range。验证过的响应数据增量发布给等待者，不再等页尾或磁盘写入；页内已读区间有界保留，缺口续读，不覆盖已经交付的数组部分。同页去重，新需求优先于排队的预读；非连续读撤销不再需要且无等待者的任务，取消具体HTTP Call，不关闭整个ISO源。
- 完整页面才交给原 `IsoDiskPageStore` 原子提交，继续同一128MiB默认/用户配置共享预算及低空间策略；磁盘异常只回退网络。取消、短读或网络错误不能把未写字节当作零数据/EOF/完整缓存；来源变更使所有本会话缓存失效。
- 不变更JNI/API/ABI或依赖版本，不重建双ABI；不改变菜单按钮、音视频配置、BD-J回退、DVD菜单边界、播放历史与重缓冲定义。新增内部Java类只影响已启用原盘菜单的原始字节缓存接线。风险集中于并发、部分数据可见性、取消和I/O竞争，用对应测试处理。

### 验收、对照与回滚

- 最便宜决定性测试：可控慢源只交付所需前缀后阻塞剩余页，前台读取必须已返回；页尾小请求必须从附近发起，而不是等待前面数MiB。网络总请求/字节在完整顺序页面上不比整页基线增加；共享同页数据不能重复加载。
- 必测：3读线程上限、旧预读取消/新需求优先、活跃等待者不误取消、close唤醒全部等待者、截断/短读/错误恢复、HTTP重试不覆盖已发布区间、validator变更拒绝、完整才落盘、超过8页循环后磁盘命中、缓存/存储失败和边界读取；旧 `IsoPageCacheTest` 保留，验证关闭菜单路径不变。
- 构建一次定向Java测试+Mobile arm64 debug；公共Java接线同时做Leanback arm64 Java编译，不做native或全ABI矩阵。手机安装使用OEM安装助手。
- 冻结基线APK；两片分别重复同一开场/底栏操作，记录缓存冷热、点击到menu-active/restart、停顿次数/时长、硬解和丢帧。存在网络噪声时至少3次候选和可比基线，报告中位数与范围，不只选最好一次。可控测试需消除整页尾部人为阻塞；真机目标菜单等待中位数改善至少30%、片头供数停顿不劣化，无法区分波动则不能宣称达到该幅度；保持收起/跨页切换回归通过。
- 回滚：以 `310f8feef5c0a05e5dae6c0a063453113214ea2c` 及其恢复tag为锚，原子revert本轮Java/测试/文档提交即可恢复旧路径；原生资产未改，冻结旧APK可直接恢复。最终必须记录实测、原子提交并创建新的本地注释恢复tag，不push。
- 证据目录：`/tmp/p9-iso-progressive-20260909.2zBuZ5/`。目前仅有设计/研究，未宣称实现或性能通过；下一动作是实现并运行定向测试。

### 2026-09-10 用户确认与阶段收口

- 已实现范围：新增菜单会话专用 `ProgressiveIsoPageCache`；HTTP请求增量发布、具体Call取消、断线从已发布前缀后续读；关闭菜单或无磁盘缓存时仍使用原 `IsoPageCache`。完整页面才写入既有磁盘协调器，不修改原生库、解码/渲染或菜单状态机。
- 验证：`build-tests-approved.log` 记录34项定向Java测试全部通过（新缓存13、旧缓存13、HTTP8），包含慢响应提前返回、页尾需求、同页去重、旧预读取消、活跃读保护、截断/续读、源变化、完整落盘、30个4MiB页面循环及内存上限；Mobile arm64 debug打包和Leanback arm64 Java编译同次成功。先前sandbox锁文件权限失败在正确提权后解决，不属于代码失败。
- 安装：`install.log` 记录OEM风险确认与继续安装均由助手处理，`Success`并启动 `com.fongmi.android.tv`；手机10CF6H1D2L0009S，APK SHA-256 `fc0b397af908989bf7e4ed6cefac6bdea491770d900af48d402e618a45543885`。冻结旧包 `baseline.apk` SHA-256 `13a7dfb6faf4567cbd95a90096c78ab8157d0e98be9903f79b8c14640cf55ce7`。
- 用户实测反馈“速度好像改善了”，随后明确要求tag并稍后提出新需求。按明确收口规则停止额外真机/计时/构建，不把用户的主观改善扩大为所有原盘、所有网络或既定30%收益均验证通过；两片严格重复A/B尚未完成，量化幅度未定。
- 本轮只提交guard所列源码、测试与本文档；既有 `app/.cxx/` 受保护，`/tmp`诊断/即时UI工具不进入提交。回滚锚点仍为 `310f8feef5c0a05e5dae6c0a063453113214ea2c`，原生资产不变。提交和tag由紧接本记录的guard finish创建，不push。

## 2026-09-13 B 侧 C4 合并遗漏修复（已确认实现缺陷）

- Git 证据：`git diff 188553addf6620dd29ab344152599426694553a5..7dc58af1b0bb28818b23f43748c3ac67f76e0449 -- app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java` 显示 C4 squash 只新增 `case "disc-menu-active"` 处理分支，未新增 `observe("disc-menu-active", MPV_FORMAT_FLAG)`。`188553addf6620dd29ab344152599426694553a5` 相对 `2b36396c0d3ed18aa75250d3293c8f29d8b1a10d` 的差异同时移除了旧调用和处理分支，因此 C4 不能从该合并基线自动恢复；这是实现遗漏，不是有意移除。
- 影响：`dispatchProperty()` 仅由 MPV native 属性事件进入。未注册观察时该分支不可达，`discMenuActive` 无法通过属性事件置 true，Activity 的按键和触摸转发路径会持续判定菜单未激活。`disc-nav-active`/FILE_LOADED 仍可设置 `discMenuAvailable`，所以 MENU 键打开菜单的入口不受影响。
- 修复：在 `observeProperties()` 的 `sub-visibility` 观察后恢复 A 侧同位注册 `observe("disc-menu-active", MPVLib.MpvFormat.MPV_FORMAT_FLAG)`，不改变处理分支、状态机、输入转发或 native 行为。
- 验证：`git diff --check` 与 `grep -n 'observe("disc-menu-active"\|case "disc-menu-active"' MpvPlayer.java` 确认注册与分支同现；后续聚焦 Java 编译通过后完成本单元。本轮不改 CMake 缓存或原生产物。
- 编译结果（2026-09-13 Asia/Shanghai）：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac --offline` 通过，日志 `/tmp/p9-disc-menu-observe-compile.log`。编译验证只证明 Java 接线可构建；未重复真机菜单场景。
- 回滚：revert 本轮 P9 原子提交并恢复 `7dc58af1b0bb28818b23f43748c3ac67f76e0449`；native 产物不变。
- 唯一下一动作：聚焦 Java 编译通过后用当前 guard finish 提交并创建本地注释恢复 tag，不 push。
