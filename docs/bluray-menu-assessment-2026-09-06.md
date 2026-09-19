# WebHTV 蓝光菜单评估（2026-09-06）

## Recovery anchor

- 目标：保存当前蓝光菜单评估结论，并继续核实开源 BD-J 实现。
- 接受标准：记录分支/远端状态、当前代码能力、HDMV 与 BD-J 边界、证据、风险、实施建议和回滚点；不修改生产代码。
- 当前分支/HEAD：`feature-menu` / `784b90420d646eb6c7ddcc63ad622a92c65b02b4`。
- 当前工作区：任务开始时干净；本任务只允许修改本文件。
- 已完成：远端一致性核对、WebHTV/mpv/libbluray 当前实现盘点、HDMV 可行性判断、BD-J 暂缓初判。
- 已完成：libbluray JVM/JAR 链路、VLC/Kodi/mpv 集成、BD-J 工具项目和 Android 构建条件核对。
- 当前未决：没有经过真实 BD-J 原盘和 Android 设备验证，不能把“理论可移植”表述成产品兼容承诺。
- 下一步：用户批准后才进入 HDMV 实施；BD-J 保持暂缓。

## 1. Git 状态

- 当前分支：`feature-menu`
- `HEAD`：`784b90420d646eb6c7ddcc63ad622a92c65b02b4`
- `origin/main`：`784b90420d646eb6c7ddcc63ad622a92c65b02b4`
- 远端 `refs/heads/main`：`784b90420d646eb6c7ddcc63ad622a92c65b02b4`
- `git rev-list --left-right --count HEAD...origin/main`：`0 0`
- 结论：当前分支与远端 `main` 完全一致。

## 2. 用户期望

用户提供的截图是完整蓝光主菜单形态：全屏背景图/视频、播放、章节、设置、特别收录等按钮，以及方向键移动、高亮和确认跳转。截图本身不能判断菜单是 HDMV 还是 BD-J；两者可以产生相同的最终画面。准确分类需要读取原盘 `BDMV/index.bdmv`、`MovieObject.bdmv`、`BDJO` 和相关 JAR/标题信息。

## 3. 当前 WebHTV 已有能力

涉及文件：

- `third_party/mpv-player-jni/src/iso_dvd.cpp`
- `third_party/patches/mpv-stream-cb-disc-controls.patch`
- `third_party/mpv-player-jni/README.md`
- `third_party/mpv-libmpv-integration-notes.md`
- `app/src/main/java/com/fongmi/android/tv/player/iso/IsoSessionManager.java`
- `app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java`
- `app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`
- `app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java`

已有基础包括：libbluray 初始化和读取、最长 playlist 自动选择、时间/章节/语言控制、远程 HTTP Range ISO、`webhtv-dvdiso://<id>/longest`、`+disc` demux 入口，以及透明 OSD Surface 生命周期管理。当前行为契约是普通 ISO 默认播放最长标题。

## 4. 当前缺口

现有 `stream_cb` 补丁只提供时间、章节、语言控制和 `+disc` 识别；没有完整 disc navigation 接口、菜单 overlay、still frame、discontinuity、菜单跳转后的 slave demuxer 重开、`disc-menu-active` 状态或 Android DPAD/ENTER/BACK/MENU 桥接。`PlaybackActivity` 没有统一的 discnav 输入分发，`PlaybackService.NavigationCallback` 也不是完整的蓝光菜单控制接口。

本地 MPV 基线：`cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`，不包含官方 PR #18080 的完整菜单链路。

## 5. HDMV 与 BD-J

### HDMV

HDMV 是蓝光原生导航模型，依靠 libbluray 的命令、事件和图形 overlay。它可以覆盖截图所示的主菜单、章节、设置、特别收录、Popup、按钮高亮、playlist 跳转和 still frame。官方 MPV 已在 PR #18080（合并提交 `c318236b8882af860f16f936225430ad053a2179`）加入完整链路；FongMi 分支也有可参考实现。

建议：实施，但必须作为 MPV/native、JNI 和 Android 输入/OSD 的完整阶段移植，不能只拣一个 HDMV commit。

### BD-J

BD-J 是蓝光内置 Java 应用程序。它可实现动态菜单、复杂动画和交互逻辑；最终画面可与 HDMV 完全相同。它需要 Java VM、`libbluray-j2se-<version>.jar`、BD-J/AWT 图形适配、字体/XML 资源、持久化存储和严格的线程/生命周期管理。

当前 Android 构建脚本 `build/mpv-native/mpv-android/buildscripts/scripts/libbluray.sh` 使用：

```text
-Dbdj_jar=disabled
-Dfontconfig=disabled
-Dfreetype=disabled
-Dlibxml2=disabled
```

因此当前 APK 没有可运行 BD-J 所需的 JAR、桌面 JVM loader 或 Android ART 适配层。建议第一阶段明确支持 HDMV，BD-J 原盘回退最长主标题并提示“BD-J 菜单暂不支持”。

## 6. 已确认的实现证据

### 官方 MPV

- PR：<https://github.com/mpv-player/mpv/pull/18080>
- 合并提交：`c318236b8882af860f16f936225430ad053a2179`
- 相关提交：
  - `12183aad6c7896c76942560f9367b50ac831c8ee`：disc-nav actions/states
  - `774016737bd6cbdbd73d17f4b6fe2de79945a3f1`：`disc-menu` option
  - `91c8c26f6ad345fd03074fb8645ea054883aaf25`：DVD menu navigation
  - `93a2983c7911ec234eb78c983ed0c55a645f17ad`：HDMV Blu-ray menu navigation
  - `2190280662e87a51c9689a87eae057b2e8075a63`：slave demuxer reopen
  - `c1df49efed4d3462d9062cd510c94504aebf7375`：synthetic Disc Menu edition
  - `cae874d76e31a2cc4bf1147c206a80f75881160e`：disc-menu OSD type
  - `350b802e85871b0f7b69692eb2ff694dd6f06e14`：`player/discnav.c`
  - `9c50acfb7d3bfd67db9c986f67036ade46195e51`：`disc-menu-active`
  - `256f348433776d4c0ddd82ce454233c6b12babf0`：discnav command/default keys
  - `8305b9d5c1a97a4b31ce74e9749e118696c066f7`：BD-J ARGB overlay
  - `c83a677facb42eedbbf5e39e1d57556078a6beec`：interactive disc read-ahead handling
  - `1dd0eb2f6d1620e089d52e9c0c4b6309bccb62a`：BD still frame

官方 MPV 的 BD-J 相关提交表示“播放器能够消费 BD-J overlay/状态”，不等于 Android 已经提供 BD-J JVM。BD-J runtime 仍由 libbluray 构建和宿主环境决定。

### libbluray

当前本地源码仓库 HEAD：`99a60ad2141d5ace94453590903c2c6b9a0a2443`。源码包含真实 BD-J 子系统，而非仅有标记：

- `src/libbluray/bdj/bdj.c`：动态发现/加载 JVM，通过 `JNI_CreateJavaVM` 创建 VM，加载 `libbluray-j2se-<version>.jar`，处理 BD-J 事件和关闭生命周期。
- `src/libbluray/bdj/native/*`：BD-J native 方法、AWT/图形和 logger 绑定。
- `src/devtools/bdj_test.c`：检查 `bdj_handled`，调用 `bd_start_bdj()`/`bd_stop_bdj()` 的测试工具。
- `bluray.h`：`bdj_detected`、`bdj_handled`、`bd_register_argb_overlay_proc()`、BD-J 输入和状态 API。
- `README.md`：明确说明安装 Java JDK 和相关包才能启用 BD-J 菜单。

这证明开源世界存在可工作的桌面 BD-J 实现基础；但它的宿主前提是桌面 JVM/J2SE，不是 Android APK 即插即用组件。

进一步核对源码可见，libbluray 并不是把 BD-J 编译成一段纯 C 逻辑：

- `src/libbluray/bdj/bdj.c` 在 J2SE 路径动态加载 `libjvm`，查找 `JNI_CreateJavaVM`，设置 `-Xbootclasspath`/Java module patch，并创建或附着 VM。
- `src/libbluray/bdj/meson.build` 需要 Ant 和 Java 编译器，生成两个独立产物：`libbluray-j2se-<version>.jar` 与 `libbluray-awt-j2se-<version>.jar`。
- 源码还保留面向 PhoneME/CDC 的 `j2me` 变体（加载 `libcvm`/`PhoneME`），但这不是 Android ART 适配；它仍要求专用 Java ME/CVM 运行时和额外 bootclasspath。
- `bd_info` 的 `bdj_detected` 与 `bdj_handled` 明确区分“盘面含 BD-J”与“宿主找到了可用 JVM/JAR 并真正处理”。

### 相关开源播放器

- mpv：已合并 HDMV/BD-J overlay 和 discnav 适配，但当前 issue 仍记录部分 BD-J 原盘因 `libbluray-j2se`、JVM 或盘面兼容性失败。
- Kodi：集成 libbluray 的 HDMV/ARGB overlay 和输入 API；其运行环境仍依赖可用的 libbluray BD-J/JVM 配置，不能直接证明 Android WebHTV 已具备 BD-J。
- VLC：构建 libbluray 时启用 libxml2、FreeType 和 `libbluray*.jar`，属于桌面/完整依赖链示例，不是可直接复制的 Android BD-J 实现。

VLC 的公开源码提供了很清楚的运行条件：`modules/access/bluray.c` 暴露 `bluray-menu`、`JAVA_HOME` 和持久化存储设置，并同时注册 HDMV 与 BD-J ARGB overlay；其构建规则依赖 libxml2、FreeType，BD-J JAR 需要 Java/Ant。VLC Flatpak 的公开 issue [#11](https://github.com/flathub/org.videolan.VLC.Plugin.bdj/issues/11) 还记录了“插件存在但系统找不到 Java，菜单回退为无菜单播放”的实际失败。

Kodi 的 `FindBluray.cmake`/`FindBlurayBDJ.cmake` 也把两个 JAR 作为单独的运行时目标：`libbluray-j2se-<version>.jar` 和 `libbluray-awt-j2se-<version>.jar`。Kodi 的 Android 依赖配置没有显示将这两个 J2SE JAR 和一个 JVM 打进 Android 包；这不是 Android BD-J runtime 的证据。

其他容易混淆的开源项目：

- `umjammer/xletview`（GPL）是 PC 上的 MHP/Xlet 模拟器，README 明确说它不是完整 MHP 实现；它不是读取 `BDMV`、运行 `BDJO` 的蓝光播放器。
- `oliverlietz/bd-j`（BSD-3-Clause）主要是 HD Cookbook 的 BD-J 制作/分析工具，并且 README 列出部分工具已损坏；它不是可嵌入播放器 runtime。
- `tourettes/libbluray`、`matt953/libbluray` 是面向 BD-J 开发/补丁的 libbluray fork，仍继承 libbluray 的 JVM/JAR 宿主模型，不提供 Android ART 适配层。

## 7. “开源实现”结论

答案是“有，但分层看”：

1. **libbluray 有真实的开源 BD-J runtime 实现**，包括 JVM 启动、JAR 加载、BD-J 事件、native/AWT/ARGB overlay 和测试工具。
2. **mpv、VLC、Kodi 有开源集成代码**，可以把 libbluray 的 BD-J 状态/overlay 接到播放器显示和输入链路；mpv 的新菜单代码还处理了 BD-J ARGB overlay。
3. **没有发现一个可以直接放进 WebHTV Android APK 的成熟、完整、免改造 BD-J 实现**。桌面项目的关键依赖是 J2SE JVM/AWT/JAR；Android ART 并不提供同等运行时契约。
4. 因此，“开源代码存在”不等于“WebHTV 可以直接开启”。可复用的是 libbluray BD-J 协议/状态/overlay 代码和播放器桥接思路；需要自行解决 Android JVM/图形/生命周期/安全/包体/许可证问题。

更精确地说：**开源实现有，开源 Android 成品没有被找到**。现有开源实现可分为“BD-J 运行时（libbluray + 外部 JVM/JAR）”“播放器集成（VLC/Kodi/mpv）”“制作/调试工具（HD Cookbook、XleTView）”三层，不能把后两层当成移动端 runtime。

## 8. 风险与产品边界

- 商业 BD-J 兼容性不确定；即使桌面 mpv 配好 JAR/JVM，也不能保证所有 4K/Java 菜单。
- 官方 mpv issue [#18423](https://github.com/mpv-player/mpv/issues/18423) 的复现显示：无 `libbluray-j2se` 时明确回退无菜单；补齐 JAR 和桌面 Java 后，部分 4K BD-J 盘仍出现空窗口/无法显示菜单。这说明“补 JAR”不是充分条件。
- 远程 HTTP Range ISO 的 BD-J 会增加随机读取、缓存和延迟压力。
- 将不受信任蓝光中的 Java 字节码放入 Android 应用会扩大攻击面，必须考虑沙箱、文件访问、网络和持久化权限。
- AACS/BD+ 解密不在菜单实现范围内；没有合法密钥时，菜单和正片都可能无法读取。
- BD-J 需要额外 JAR、字体、XML、JVM/适配层，增加 APK 体积、ABI/生命周期复杂度和维护成本。

## 9. 建议

当前建议保持：

- **HDMV Blu-ray 菜单：实施**
- **BD-J Blu-ray 菜单：暂缓**

第一阶段的产品语义应是：支持 HDMV 主菜单、方向键、确认、返回、Popup、章节/设置/特别收录跳转；检测到仅 BD-J 或 BD-J 无法启动时，回退最长主标题播放并给出明确日志/提示。

## 10. HDMV 后续最小实施边界

1. 移植官方/FongMi 完整 discnav、overlay、event、discontinuity、still frame 和 slave demuxer reload 链路。
2. 扩展 WebHTV `stream_cb` 与 `iso_dvd.cpp` 的 NAV command/state/overlay 接口，同时保留远程 Range 和最长标题回退。
3. 在 `MpvPlayer` 观察 `disc-menu-active` 并公开 discnav action；复用现有 OSD Surface。
4. 在 Android 输入层映射 DPAD、ENTER、BACK、MENU/POPUP。
5. 在 arm64-v8a、armeabi-v7a 上验证本地 ISO、远程 ISO、菜单跳转、still frame、Surface 重建和普通播放回归。

## 11. 回滚

回退到当前基线 `784b90420d646eb6c7ddcc63ad622a92c65b02b4`，并恢复同一组 MPV native lock、patch、JNI 源码和 APK 资产；BD-J 不进入该阶段。

## 12. 研究来源与访问日期

访问日期：2026-09-06；网络访问使用用户指定的 HTTP/SOCKS 代理。

- libbluray 源码：本地 `build/mpv-native/mpv-android/buildscripts/deps/libbluray`，HEAD `99a60ad2141d5ace94453590903c2c6b9a0a2443`；`README.md`、`src/libbluray/bdj/bdj.c`、`src/libbluray/bdj/meson.build`、`src/devtools/bdj_test.c`、`src/libbluray/bluray.h`。
- libbluray 官方仓库：<https://code.videolan.org/videolan/libbluray>；README 明确要求 Java JDK/相关包才能启用 BD-J 菜单。
- mpv 菜单实现：<https://github.com/mpv-player/mpv/pull/18080>，合并 commit `c318236b8882af860f16f936225430ad053a2179`。
- mpv BD-J 兼容性 issue：<https://github.com/mpv-player/mpv/issues/18423>；日志显示缺少 `libbluray-j2se` 时回退无菜单，补齐 JAR/JRE 后仍有 4K BD-J 空窗口案例。
- VLC 集成：<https://github.com/videolan/vlc/blob/master/modules/access/bluray.c>；构建规则：<https://github.com/videolan/vlc/blob/master/contrib/src/bluray/rules.mak>。
- VLC Flatpak Java 失败案例：<https://github.com/flathub/org.videolan.VLC.Plugin.bdj/issues/11>。
- Kodi 集成：<https://github.com/xbmc/xbmc/blob/master/xbmc/cores/VideoPlayer/DVDInputStreams/DVDInputStreamBluray.cpp>；BD-J 构建/运行时发现：`cmake/modules/FindBluray.cmake`、`cmake/modules/FindBlurayBDJ.cmake`。
- BD-J 工具：<https://github.com/oliverlietz/bd-j>；以制作/分析工具为主，不是播放器 runtime。
- XletView：<https://github.com/umjammer/xletview>；PC MHP/Xlet 模拟器，不是 BDMV/BDJO 播放器。
- libbluray BD-J forks：<https://github.com/tourettes/libbluray>、<https://github.com/matt953/libbluray>；仍使用 libbluray 的 JVM/JAR 宿主模型。
