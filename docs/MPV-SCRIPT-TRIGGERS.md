# MPV scripts 按钮与执行时机联动

## 目标与边界

- 用户于 2026-09-11 明确要求实施：关闭“启用按钮”强制启动时执行、禁用点击/长按；从关闭切换为开启默认点击；开启后允许手选启动时，并可在播放中点击同一个按钮开关脚本。
- `quick-fix`，guard `MPV-SCRIPT-TRIGGERS`，基线 `aa676a941ee101cbb40c9641840e3b638b81c3d4`，分支 `feature-menu`。保护预存 `app/.cxx/` 的 35 个文件。
- 只改共享配置创建/设置对话框、脚本存储/生成、聚焦测试及本文。手机/TV 共用修复，不改播放器引擎、上游版本、JNI、原生库、脚本内容或配置格式。
- 2026-09-11 17:32 Asia/Shanghai 估计余下约 20 分钟：修改 7 分钟，验证/构建 7 分钟，真机验证/提交/tag 6 分钟，目标 17:52。

## 决策依据（2026-09-11）

1. 本地代码，基线同上（A级直接证据）：
   - `MpvConfigCreateDialog.setupScriptSettings/initEvent` 未联动开关；创建/导入回调也没有传递已选设置。
   - `MpvConfigStore.writeCustomButtonScript` 跳过 `enabled=false`；startup 只在顶层执行，无 `.short` 回调。
   - `PlayerManager.sendMpvCustomButton` 及 mobile/leanback `VideoActivity.setupCustomActionButtons` 共用 `webhtv-custom-button(id, short/long)`，无需改两端协议。
   - 脚本源保存在隐藏 `.webhtv/`，不被 mpv 自动扫描，不可依赖“关掉按钮”后自行加载。
2. mpv 官方规范/实际源码（A级，版本 `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`）：
   - [Lua 生命周期与消息回调](https://github.com/mpv-player/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/DOCS/man/lua.rst)：`mp.register_script_message` 将消息映射到函数；同一 Lua 环境的全局状态与定时器可以跨调用保留。读取本地 `build/mpv-native/mpv-android/buildscripts/deps/mpv/DOCS/man/lua.rst` 对应段落。
   - [input.rst](https://github.com/mpv-player/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/DOCS/man/input.rst)：`load-script` 创建新的脚本客户端，不能作为本需求每次按钮点击的 toggle 实现。
   - 成熟实现 [stats.lua](https://github.com/mpv-player/mpv/blob/cca559b41ceb0bb7731cf6ef2e1f33276cd30c42/player/lua/stats.lua) 的 `process_key_binding` 持有状态并主动停止定时器；与本次复用脚本开关动作一致。
3. [Material 官方 ToggleButtonGroup 文档](https://github.com/material-components/material-components-android/blob/master/docs/components/ToggleButtonGroup.md)（A级 API 说明，master 页面无固定 SHA，已通过配置代理获取正文）：单选/必选及 checked listener 是现有组件契约；仅禁用两个子按钮，保留启动按钮可用，TV 焦点随之调整。未迁移组件或样式。
4. 真机现场证据（B级，vivo V2453A / Android 15，序列号 `10CF6H1D2L0009S`）：现有“显示信息.lua”用全局 `__toggle_info` 和 `__info_timer` 开关及清理定时器。源码只读核对，不复制用户完整脚本到仓库。其 startup 与点击共享动作可实现用户所需开关。
5. 上游 PR/issues/reverts、论文/性能基准：本轮不移植上游、不改变线程/渲染/性能策略，问题由本地缺失接线直接证明；没有待由这些证据类别决定的设计问题，故不做广泛检索。现场脚本和上游成熟脚本提供相关实践证据。

## 方案与验收

- 不改：两个明确需求不成立，拒绝。
- 直接用原生 `load-script`：每次点击产生独立实例，不能复用现有脚本 toggle 状态，拒绝。
- 采用窄适配：统一归一化启用状态/执行时机；初始化保留保存的有效选择；用户开启时选点击，关闭时选启动。创建、文本保存、导入均传递选择。managed startup 脚本注册与启动共用的 short 函数，启动只调用一次；无按钮则仅启动执行。保留旧多段自定义按钮的局部变量/代码契约。
- 任意脚本的资源撤销仍由脚本自己的开关逻辑负责；不承诺把任意只执行初始化的 Lua/JS 自动变成可卸载模块，不增加通用卸载、状态劫持或依赖。
- 接受条件：关闭时 click/long disabled 且 startup checked；开启默认 click；手选 startup/long 保存重开不被覆盖；startup 一次后点击关/再开；点击/长按无额外自启动；新建/导入选项不丢失；旧多段代码可用；两端 Java 编译通过。
- 最便宜决定性验证：归一化/生成器 JUnit + 用现有 Lua 源码构建的临时宿主解释器执行生成的桥接代码（状态、次数、错误隔离、旧局部变量），随后单个 mobile debug 构建/安装和已连接手机的聚焦场景；TV 只编译共享 UI 调用链，不做 ABI 矩阵/原生库重建。
- 安全/兼容/性能：不改变数据 schema，不改用户脚本；增加常数级回调接线，无播放帧循环工作或产品依赖/ABI 变化。单元提交 + 本地 annotated recovery tag；回滚本单元提交恢复旧逻辑，用户原有脚本文件不需回滚。

## 上一单元验证与交付（MPV-SCRIPT-TRIGGERS）

- 状态：代码完成，首次合并自动化验证通过；手机安全锁屏，真机 UI/安装未执行。准备原子提交和恢复 tag。
- 文件/符号：`MpvConfigCreateDialog` 联动与创建参数，`MpvConfigDialog` 保存/导入，`MpvConfigStore` trigger 归一化及 Lua 生成，`MpvConfigStoreTest` 和 Lua fixture。
- 已完成：开关联动/TV焦点、创建/导入设置透传、存储归一化、startup/short 共用函数、disabled managed script 启动执行、旧多段作用域保留；宿主 Lua 5.2.4 已构建在 `/private/tmp/webhtv-script-trigger.PIW3G6/lua`，仅测试工具，不更新产品 native 产物。
- 验证：2026-09-11 17:49，单次 Gradle `BUILD SUCCESSFUL in 4m 1s`；`MpvConfigStoreTest` 12 tests / 0 failures / 0 errors / 0 skipped，包括实际 Lua 5.2.4 执行生成代码；mobile arm64 debug 打包、leanback arm64 Java 编译通过。测试还覆盖 hidden startup、click/long 无额外自动执行、旧多段 local 状态、运行时错误隔离及 return。Room 既有 QUERY_MISMATCH/弃用警告不在本轮范围。
- 命令：`JAVA_HOME=/Users/macbookpro/.gradle/jdks/jetbrains_s_r_o_-21-x86_64-os_x.2/jbrsdk_jcef-21.0.10-osx-x64-b1163.110/Contents/Home WEBHTV_TEST_LUA=/private/tmp/webhtv-script-trigger.PIW3G6/lua bash ./gradlew --offline --init-script /private/tmp/p9-menu-gradle-staging.gradle :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.mpv.MpvConfigStoreTest :app:assembleMobileArm64_v8aDebug :app:compileLeanbackArm64_v8aDebugJavaWithJavac --console=plain`。
- 证据：`/private/tmp/webhtv-script-trigger.PIW3G6/gradle.log`、`app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.player.mpv.MpvConfigStoreTest.xml`；APK `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`。
- 设备限制：同目录 `before.png/before.xml/device-policy.txt` 证明 `showing=true / secure=true / SCREEN_STATE_OFF`。按 android-device-tester 不绕过锁屏，已请用户解锁；只读备份 `original-scripts.tar`，没有安装、改脚本或改设置，无需恢复。自动化结果不当作真机 GUI 验收。
- 提交/tag：由 guard 原子创建，记录在提交的 Verification/Task-Guard 及 `recovery/MPV-SCRIPT-TRIGGERS/` annotated tag 中；不为回填新 SHA 另建文档提交，不推送远端。
- 唯一下一步：手机解锁后安装本次 APK，验证 scripts 开关联动与现有“显示信息”脚本 startup/点击场景。

## 后续单元：脚本独立启用状态（2026-09-11）

- 用户明确要求：仅脚本设置增加默认开启的脚本开关；scripts 列表参考站点注入区分启用/禁用，其他保持原状。guard `MPV-SCRIPT-ENABLE`，基线 `1ce8df96075f703a16cecfa077fd715c66b90279`；保护 `app/.cxx/` 35 个预存文件。不推送、不改上游/原生/依赖。
- 18:23 Asia/Shanghai 估计约20分钟，目标18:43；实现6分钟、验证/构建6分钟、手机与闭环8分钟。
- 本地成熟实现（A级，基线同上）：`CustomCspDialog.statusColor/rowBackground/titleColor/detailColor` 使用绿色启用、灰色禁用、禁用卡片仍可点击和聚焦；直接复用配色契约，不重排 scripts 列表或改变 mpv.conf/input.conf 样式。
- 方案：`CustomButton.scriptEnabled` 独立于既有 `enabled`（按钮可见），JSON 缺字段/null 按 true 兼容；设置保存、重命名/导入选项更新保留新状态；禁用时生成器不读/运行该脚本，也不生成回调；两端播放按钮隐藏。配置载入时机保持当前播放器创建时生效，不引入运行中任意脚本卸载。脚本内容、点击/长按/startup 保持上一单元逻辑。
- 备选：不改不满足需求；复用按钮 enabled 会破坏“关闭按钮仍startup”契约，拒绝；采用单独字段及现有过滤路径，最小可回滚。默认值不改变旧数据状态，新增字段随现有 metadata 文件备份。
- 规范依据沿用本文 mpv 官方 Lua 生命周期/消息契约；新增仅 App 层已有配置模式，不涉及新的上游实现或运行时API。PR/issues/论文/基准对该字段和既定站点注入样式无待决设计问题，故不扩大检索。
- 验收：旧数据/新脚本默认启用；停用后startup/click/long全不运行；重新启用不改按钮/trigger/脚本；列表准确读状态、置灰但可编辑，非 scripts 卡片不变；定向单测与实际Lua运行、两端编译、可用手机的一次开关/保存/重开验证。回滚本单元提交即可恢复旧逻辑；新字段在旧版被忽略（旧版不支持停用）。

## 后续单元：播放按钮与启动脚本状态同步（2026-09-15）

- 用户反馈：启动脚本已运行，播放页按钮仍未选中，首次点击后脚本关闭而按钮亮起。用户明确要求立即修复、不运行任何测试，由用户自行实测。
- `quick-fix`，guard `MPV-SCRIPT-BUTTON-STATE`；基线 `0667ff435ba167cfdff716236d68fea8d7fcd16f`，分支 `feature/mpv-dv7-fel`。保护 `app/.cxx/` 的 70 个预存脏文件；允许修改脚本生成器、App 内 `MpvPlayer`、mobile/leanback `VideoActivity` 和本文。08:18 Asia/Shanghai 开始修改，预计约 3 分钟，目标 08:21；不构建、不测试。
- 已定位：两端仅在消息入队后翻转 View.selected，启动调用没有回传状态；手机转屏重新创建 View 后也会丢失状态。
- 窄修复：沿用现有 Lua 回调及 MPV 属性观察接口。Lua 仅在对应动作执行成功后更新按钮状态，通过 `user-data/webhtv-custom-buttons` 发布当前激活 ID；启动和点击使用同一状态。App 缓存属性事件并通知两端 UI，按钮创建/转屏/播放器切换时读取当前播放器缓存，原生上下文销毁时清空，Activity 销毁时解除监听。
- 沿用本文件已经确定的脚本 toggle 契约；普通任意脚本的内部自发变化不在本次协议中。无 handler 的点击/长按、运行错误不翻转状态，隐藏/禁用脚本及 legacy 多段作用域保持原行为。状态回传异常不打断脚本执行；不修改 JNI、原生库、依赖、用户脚本或数据格式，不引入轮询或播放帧循环操作。
- 本地设计依据：同文件现有 `discMenuStateListeners` 主线程属性通知/监听解绑模式与 `MpvPropertySnapshot` 缓存；前述已审定 Lua 消息生命周期。此次为既定设计的状态接线修复，不扩大上游研究。
- 验收由用户实测：startup 成功后按钮激活，首次点击关闭并取消激活，再次点击恢复；手机转屏状态保持；无对应动作或脚本报错不误翻转；手机/电视行为一致。
- 交付：用户随后要求安装到手机。08:30 Asia/Shanghai 确认原 APK 早于代码修改，执行一次 `:app:assembleMobileArm64_v8aDebug`（`--offline`，沿用 `/private/tmp/webhtv-libass-stage1/isolate-cxx.gradle` 保护预存 CMake 目录）；40 秒构建成功，不运行测试。APK：`app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`；完整构建日志 `/private/tmp/webhtv-script-button-install-20260915-083003.log`。
- 安装：按 android-device-tester 安装助手覆盖安装到 vivo V2453A（`10CF6H1D2L0009S`，arm64），包名 `com.fongmi.android.tv`；助手确认设备安装包指纹已更新并启动应用，退出码 0。安装日志 `/private/tmp/webhtv-script-button-install-20260915-083003-device.log`。
- 验证状态：手机端编译/打包与安装完成；按用户要求未运行自动化或功能测试，等待用户实测。不把安装成功标为按钮行为通过。回滚仅需恢复本次五个文件，不影响用户配置。
- 10:13 用户反馈激活高亮消失：两端 `selector_control_sheet_button.xml` 的 selected 样式完好，设备上的 Lua 桥接也已更新。直接阅读当前 mpv `player/command.c::do_op_udata`、`player/client.c::getproperty_fn`、`options/m_property.c::M_PROPERTY_GET_STRING` 和 `options/m_option.c::print_node`，确认 user-data 为 NODE，STRING 观察值仍为 JSON 字符串；上一版未解码，带引号的 ID 无法匹配 View 标签。10:17 修正 `MpvPlayer.handleProperty`，先解码 JSON 字符串再更新状态；不改变样式、脚本或 JNI。继续原 guard，仅修改 `MpvPlayer` 和本文，预计 10:21 前完成一次手机端打包/覆盖安装，不运行功能测试。
- 修正版交付完成：同一 mobile arm64 assemble 命令一次通过（25 秒）；安装助手处理系统风险确认，确认安装包指纹更新并启动 `com.fongmi.android.tv`，退出码 0。日志 `/private/tmp/webhtv-script-button-highlight-20260915-101753-build.log` 和 `/private/tmp/webhtv-script-button-highlight-20260915-101753-install.log`；未运行功能测试，等待用户重新验收。

## 上一单元 Recovery anchor（脚本独立启用状态）

- 状态：字段/UI/过滤与测试实现完成，首次合并 Gradle 验证通过（1m44s）；手机安装成功，用户明确“我测试可以了，打个tag”，本单元验收通过，立即提交/tag，不追加验证。
- 允许路径：脚本存储、设置对话框/布局、列表适配器/禁用卡片资源、mobile/leanback按钮过滤、三语言string、定向Java/Lua测试及本文。
- 已完成：新增 `scriptEnabled=true` 默认和 JSON 兼容读写、设置开关/TV焦点、列表状态/灰色卡片、两端 `isButtonVisible()` 过滤、生成器跳过停用脚本；新建/导入界面不变，旧保存接口和导入时机更新保留脚本状态。手机V2453A已连接且解锁（18:22）。
- 验证执行：使用上一单元已构建 `/private/tmp/webhtv-script-trigger.PIW3G6/lua` 运行扩展脚本测试（预期15项，含真实Lua），同次构建mobile arm64 debug和编译leanback arm64 Java；CMake staging仍在build/mpv-native/app-cxx，不改预存app/.cxx/。日志 `/private/tmp/webhtv-script-enable.oVGHS9/gradle.log`。
- 设备：原脚本目录已只读备份到 `/private/tmp/webhtv-script-enable.oVGHS9/original-scripts.tar`；安装助手完成风险确认并成功安装、启动本次 mobile debug。列表启用状态与设置界面已进入，最终行为由用户实测确认通过。用户无关屏幕数据不作为任务证据引用。
- 已验证：`MpvConfigStoreTest` 15 tests / 0 skipped / 0 failures / 0 errors，包含真实Lua执行；mobile arm64 debug打包和leanback arm64 Java编译通过；XML资源通过Android构建。报告在 app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.player.mpv.MpvConfigStoreTest.xml。
- 18:47进度：实际验证启动/工具等待超过原目标，停止额外检查；仅继续手机开关/列表及提交/tag。设备仍已解锁，安装助手日志同证据目录 install.log。
- 验收：用户确认本次需求测试通过；不扩展相邻场景，不以编译替代用户实测。提交/tag 由 guard 原子生成，本轮不推送远端。
- 唯一下一步：执行 guard finish 提交本单元并创建 annotated recovery tag。

## Recovery anchor

- 当前目标：修复 MPV 启动脚本与两端播放按钮状态不一致；对应 guard `MPV-SCRIPT-BUTTON-STATE`。
- 实现状态：五个允许文件已修改；首包手机实测报告高亮丢失，已修正 user-data STRING 观察值的 JSON 解码。修正版已完成手机 arm64 编译、打包和覆盖安装；原样式保留，用户已实测确认“可以了，打个tag”。
- 保护路径、基线、验收与回滚：见上方 2026-09-15 单元；预存 `app/.cxx/` 不纳入提交。
- 验收：用户自行实测通过并明确要求打 tag；不追加构建、测试或设备检查，按闭环快路径提交本单元。
- 唯一下一步：执行原 guard finish 原子提交并创建本地恢复 tag，不推送远端。
