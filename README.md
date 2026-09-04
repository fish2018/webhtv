# WebHomeTV

WebHomeTV 是基于 [FongMi](https://github.com/FongMi/TV) / CatVod 生态二次开发的 Android 影音应用,保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力,并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**管理页面**、**远程托管**、**WebHome 扩展**、**登录态学习/同步**、**网盘链接检测**、**站点健康排序**、**观影记录同步** 和 **Nostr/TMDB 推荐首页**。

项目的核心目标不是替换 CSP/Spider 体系,而是让 CSP 站点首页变成一个真正可开发的网页应用:开发者用 HTML/CSS/JavaScript 定制首页,再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

### 增强功能

- **网盘检测**:内置网盘分享链接有效性检测,WebHome 和本地 HTTP API 均可调用。
- **站点健康排序**:自动学习站点搜索、详情和播放成功率,搜索与换源优先使用更可用的站点;站点弹窗默认保留用户配置顺序,可在弹窗内单独开启健康排序。
- **管理页面**:在 App 内启动局域网浏览器管理页 `/m`,可管理本机或远端设备文件、登录态、同步目录、站点注入、接口、壳代理、搜索和推送,运行期间通过前台服务保活。
- **远程托管**:通过自建 Cloudflare/Deno/Vercel/Go/Rust 中转服务绑定多台 WebHTV 设备,支持设备状态、远程搜索/推送、接口配置、主页设置、一键同步和最近日志;Go/Rust 版支持 WebSocket 实时通道,不支持时自动回退 HTTP 轮询。部署说明和二进制见 [远程托管中转服务器文档及二进制](serverless)。
- **一键同步**:在同一局域网设备间同步配置、站源数据(Jar/脚本保存数据)、登录态、WebHome 数据、搜索记录、观看历史、收藏和应用设置,每项可单独勾选。
- **站点注入**:添加自定义 WebHome 或通用 CSP 站点,主列表显示核心摘要和快捷操作,新增/修改在独立表单中维护启用状态、插入位置、首页、搜索和换源行为;顶部“识别”可粘贴单个或多个松散站点 JSON 片段并自动归类追加;WebHome 站点级扩展可直接填写扩展 URL / JSON,也可选择本地 JS/CSS/JSON 自动生成配置。
- **WebHome 扩展**:给真实网页注入用户脚本,主列表显示扩展摘要和状态,新增/修改在独立表单中配置本地文件、远程链接/manifest、直接代码、表单生成或 JSON;匹配范围默认从当前点播配置的 WebHome 站点弹窗多选,也可切换到 CSP key 正则;提供调试工作台用于 Web 预览、Console/Network/Elements 和代码保存预览。
- **观影记录同步**:增强功能中提供独立总览页,包含总开关、本机 API 修改开关、远端同步源和 Webhook 上报。爬虫可通过 `/api/playback/current` 读取当前播放记录,也可在用户开启修改后调用 `/api/playback/progress`、`/api/playback/progress/batch` 或 `/api/playback/progress/delete` 写入/清理本地进度;App 也可从用户配置的远端 API 拉取批量记录合并到本地历史,并通过删除墓碑同步清理记录。仓库内置的 Cloudflare、Deno、Vercel、Go、Rust 五种服务端都可用同一 URL 同时承接 Webhook 和增量拉取，分别使用 Durable Object SQLite、Deno KV、Redis REST 或本地原子文件持久化。完整协议见 `webhome-devkit/docs/应用完整开发文档.md` 的“观影记录同步”章节。
- **登录态学习**:用户手动开启后学习 Cookie、Token、接口 Jar 网盘登录文件等登录态路径,待确认项可在管理页查看/编辑,并可参与一键同步。
- **APP 代理**:配置代理地址和域名匹配规则,可按当前站点自动建议代理域名,用于改善特定站点、接口或播放链路的网络访问。
- **调试日志**:本机和局域网日志查看入口,便于排查播放、代理、站源和 WebHome 相关问题。

以上能力集中在设置页的"增强功能"入口,手机端和电视端均为独立设置页。

## 效果演示

https://github.com/user-attachments/assets/984c274f-8a9b-4857-b641-d251e061f5cc

演示视频对应的站点配置(Nostr/TMDB 推荐首页):

```json
{
  "key": "Nostr",
  "name": "Nostr推荐",
  "type": 3,
  "api": "csp_Nostr",
  "homePage": "https://www.252035.xyz/xs/tvbox/nostr.html"
}
```

## 文档

完整开发说明见 [**应用完整开发文档.md**](webhome-devkit/docs/应用完整开发文档.md),包含:

- App 配置字段(点播、解析、直播、样式)
- Spider 开发,JS/Python Spider 运行时
- 本地 HTTP 服务端点总览
- WebHome SDK 全部方法的参数和返回值
- 透明背景、电视端遥控器 UX、性能最佳实践
- 网盘检测 API 和站点健康排序
- 观影记录同步、Webhook 上报和爬虫 HTTP API
- 管理页面和局域网 HTTP 能力
- 远程托管部署、绑定流程和能力边界
- WebHome 扩展脚本开发
- 登录态学习与同步
- PanSou 集成、Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

WebHome 主页、扩展、模板、示例和 AI skills 统一放在 [webhome-devkit/](webhome-devkit/) （附 [独立 CNB 仓库](https://cnb.cool/fish2035/ext)）：

- 扩展脚本开发指南见 [webhome-devkit/README.md](webhome-devkit/README.md)。
- 扩展示例见 [webhome-devkit/examples/extensions/](webhome-devkit/examples/extensions/)。
- 主页示例见 [webhome-devkit/examples/homepages/](webhome-devkit/examples/homepages/)。
- 模板见 [webhome-devkit/templates/](webhome-devkit/templates/)。
- AI 编程客户端如何接入和复用 Skills,见 [webhome-devkit/skills/](webhome-devkit/skills/)。

配置文件和示例 HTML 放在同一服务器目录时,`homePage` 可直接写相对路径。

社区内容:

- [网友自制分享](https://github.com/fish2018/webhtv/issues/13)

## 构建

本节按“新机器 clone 后直接复制命令打包”为目标维护。当前分支使用较新的 Android/Gradle/Media3/MPV native 组合，环境不满足时最常见的失败点是 JDK、Android SDK 37、NDK 和依赖下载网络。

### 环境要求

- 项目使用纯命令行工具链，不依赖任何 IDE。必须在 `JAVA_HOME`/`PATH` 中配置独立 JDK 21，并单独配置 Android SDK Command-line Tools。
- 需要 JDK 21；当前 `sourceCompatibility` / `targetCompatibility` 均为 Java 21。
- Python 3.10。Chaquo 运行时和构建时 Python 均固定为 3.10，仅安装 Python 3.11/3.12/3.13 会失败。
- Android SDK Platform 37 和 Build Tools 37.0.0。当前 `compileSdk=37`、`minSdk=24`、`targetSdk=28`。
- Android NDK 29.0.14206865（r29）用于重建 MPV/FFmpeg/libplacebo 和 MPV JNI；NDK 28.2.13676358（r28c）继续用于 IJK/DVD。普通 Gradle 打包直接使用仓库已提交二进制，不要求安装 NDK。`scripts/build_mpv_player_jni.sh` 只重建 JNI 桥接库 `libplayer.so`，不会重编 `libmpv.so`、FFmpeg 或 libplacebo。
- 使用仓库内置 Gradle Wrapper：Gradle 9.5.1，Android Gradle Plugin 9.2.1。
- 能访问 Maven Central、Google Maven、Gradle Plugin Portal 和 JitPack。仓库内已带定制 Media3、nextlib 和本地 AAR，但普通 Android 依赖仍需要联网下载。

macOS/Linux 可用以下命令确认版本：

```bash
java -version
bash gradlew --version
```

如果依赖下载需要代理，先在当前终端设置：

```bash
export https_proxy=http://127.0.0.1:7897
export http_proxy=http://127.0.0.1:7897
export all_proxy=socks5://127.0.0.1:7897
```

### 从零 clone 到打包

先安装或确认 Android SDK Command-line Tools，并通过 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或根目录 `local.properties` 告诉构建系统 SDK 的位置。`local.properties` 是普通文本配置，直接在仓库根目录创建即可。macOS 常见 SDK 路径示例：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Linux 常见路径是 `$HOME/Android/Sdk`；Windows 可手动创建 `local.properties`，内容类似 `sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk`。

如 SDK 未安装 API 37、Build Tools 或 Platform Tools，使用 Android SDK Command-line Tools 自带的 `sdkmanager` 安装：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;37.0.0"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

clone 仓库后直接打 debug 包：

```bash
git clone https://github.com/fish2018/webhtv.git
cd webhtv
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

如果构建指定开发分支，在 clone 后切换到对应分支再打包：

```bash
git fetch origin
git switch beta
bash gradlew clean
bash gradlew :app:assembleMobileArm64_v8aDebug
```

已 clone 仓库更新当前分支：

```bash
git fetch origin
git pull --ff-only
bash gradlew clean
```

### 常用本地打包命令

debug 包适合本地安装测试，构建速度更快：

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug
bash gradlew :app:assembleMobileArmeabi_v7aDebug
bash gradlew :app:assembleLeanbackArm64_v8aDebug
bash gradlew :app:assembleLeanbackArmeabi_v7aDebug
```

release 包适合分发测试或正式发布：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease
bash gradlew :app:assembleMobileArmeabi_v7aRelease
bash gradlew :app:assembleLeanbackArm64_v8aRelease
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease
```

临时验证“仅 Release 包可用”的接口时，可以关闭 R8/资源压缩，获得接近 debug 的构建速度：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

快速 Release 的版本标识为 `<versionName>-fast-yyyyMMddHHmm`（当前例如 `5.6.0-fast-202608101200`），时间使用上海时区；不传 `-PfastRelease=true` 时仍执行正常 Release 优化，版本标识保持 `<versionName>-yyyyMMddHHmm`。快速包只用于临时测试，不代替正式发布包。

也可以一次打常用三包：手机 64 位、电视 32 位、电视 64 位。

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug :app:assembleLeanbackArm64_v8aDebug
```

### MPV native/JNI 重建

普通打包不需要执行本节命令，Gradle 会把仓库内已提交的 MPV assets 和 `libplayer.so` 打进 APK。修改以下内容时需要重建 MPV JNI：

- `third_party/mpv-player-jni/src/**`
- `third_party/mpv-player-jni/include/mpv/client.h`
- `third_party/mpv-player-jni/include/mpv/stream_cb.h`
- 升级 MPV client API，或新的 `libmpv.so` 与现有 JNI 头文件/API 不兼容

当前 `libmpv.so` 同时启用 OpenGL、Vulkan、libcurl 和 HTTP/2；`libplayer.so` 由本仓库 `third_party/mpv-player-jni` 构建，用于保留 END_FILE reason/error 等本地桥接能力。当前 native 基线：

| ABI | MPV | FFmpeg | libplacebo | 网络后端 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `arm64-v8a` | `0.41.0-940-gcca559b41` | `177f090e0503`（9.0.1-fongmi） | `7.375.0` / `b694a21bf2dc` | curl 8.21.0 + nghttp2 1.69.0 | C0-M 双 ABI、ELF、APK 和 arm64 手机播放/生命周期验证通过 |
| `armeabi-v7a` | `0.41.0-940-gcca559b41` | `177f090e0503`（9.0.1-fongmi） | `7.375.0` / `b694a21bf2dc` | curl 8.21.0 + nghttp2 1.69.0 | C0-M 独立重建及 ELF/资产验证通过，不能跨 ABI 复制 |

替换或升级 MPV native 时必须遵守：

- `libmpv.so`、FFmpeg（codec/device/filter/format/util/swresample/swscale）、静态链接进 MPV 的 libplacebo、curl、nghttp2、MbedTLS 和 `libc++_shared.so` 必须按同一 ABI、同一 lock 成套构建，不能再混用旧 `libmpv.so` 与新依赖作为正式方案。
- 当前 native lock 使用 MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg 9.0.1-fongmi `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`（7.375.0/API 375）、mpv-android `99a60ad2141d5ace94453590903c2c6b9a0a2443` 和 NDK r29/API 24。C0-M 不启用 `dovi_rpu convert=p81`，并继续保留本地 Range、MediaCodec、DV、Vulkan、AudioTrack 和 JNI 保护。curl 使用 MbedTLS 3.6.7，只启用 HTTP/HTTPS 与 HTTP/2，不包含 HTTP/3、ngtcp2、nghttp3 或 quiche。
- 最新 FongMi MPV 分支已经内建重写后的 AImageReader/AHardwareBuffer OpenGL/Vulkan 后端、异步 fence、HDR/Dolby Vision、双 Surface OSD 和 Android helper scheme；旧的 `fd679c81` 不是新分支祖先，原 `mpv-aimagereader-transient-buffer.patch` 已删除，不能在新分支上重复叠加。
- curl 与 nghttp2 静态链接进 `libmpv.so`，APK 不新增独立网络 `.so`。它增强 MPV 直接远程 HTTP/HTTPS 输入；App 自己处理的本地 HLS 代理、`stream_cb` 和 FFmpeg/lavf 路径仍按各自实现工作，不能把启用 curl 理解为所有播放请求都强制走同一后端。
- FFmpeg 文件名、ELF `SONAME` 和所有 `DT_NEEDED` 都要从 `libav*`/`libsw*` 等长改为 `libmv*`/`libmw*`，不能只重命名文件，否则会和 `nextlib-media3ext` 内置 FFmpeg 发生 Android linker 复用冲突。
- WebHTV 当前补丁集还包含 FFmpeg MediaCodec 端口饥饿回退、DV7 HDR10 基底层、TrueHD AudioTrack channel mask、可选 OSD Surface、MediaCodec timestamped release/时序诊断、Vulkan `direct/legacy/stable` 和 AImageReader 稳定释放流程。完整顺序以 `scripts/build_mpv_native.sh` 和 `third_party/mpv-native-build.md` 为准；修改光盘控制补丁、`stream_cb.h` 或 JNI 源码后必须同时重建 `libmpv.so` 与 `libplayer.so`。
- 更新后用 NDK `llvm-readelf -d` 确认没有残留 `libav*.so`/`libsw*.so` 依赖，再分别回归 OpenGL、Vulkan、硬解/软解、LUT、字幕、线路切换、连续起播/退出和 Blu-ray ISO。Android 15 必须同时检查 crash buffer 中是否出现 destroyed mutex。

从固定源码重新生成 MPV/FFmpeg `.so`：

```bash
scripts/build_mpv_native.sh --abi arm64-v8a --install
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

需要更新仓库 assets 时，同步安装两套 ARM ABI；只有 JNI 源码、MPV client API 或 `stream_cb.h` 变化时才重建 JNI 桥：

```bash
scripts/build_mpv_native.sh --abi all --install
# 按需执行：scripts/build_mpv_player_jni.sh --abi all --install
```

脚本读取 `third_party/mpv-native-lock.json`，自动下载固定 commit、构建 FFmpeg 9/字体/字幕/光盘/归档/网络/Vulkan 依赖，按脚本声明的完整顺序应用 FFmpeg、MPV、MediaCodec、Vulkan 和 AImageReader 补丁，修改 ELF 依赖名、strip 并校验。上游版本由 lock 锁定，最终可复现输入还包括构建脚本、`third_party/patches/`、native overrides 和 JNI 源码。libass 的 fontconfig/libxml2 字体回退栈静态链接进 `libmpv.so`，不会向 APK 内置中文字体或增加独立 `.so`。普通 Gradle 和 GitHub Actions 不会现场编译 MPV；Android Release Action 只运行 `scripts/verify_mpv_native_assets.sh --require-elf`，检查文件集合、ABI、版本字符串、HTTP/2、可选 OSD Surface、MediaCodec timestamped release、AImageReader/Vulkan/HDR/Dolby Vision、光盘/Range/Matroska 标记、`SONAME` 和 `DT_NEEDED`。

只校验当前仓库已经提交的 MPV native assets：

```bash
bash scripts/verify_mpv_native_assets.sh
```

发布或 native 提交前应要求完整 ELF 校验；Linux 可使用系统 `readelf`，macOS 可使用 NDK 中的 `llvm-readelf`：

```bash
bash scripts/verify_mpv_native_assets.sh --require-elf
```

只重建 App JNI 桥接库 `libplayer.so`：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
scripts/build_mpv_player_jni.sh --abi all --install
bash gradlew :app:assembleMobileArm64_v8aDebug
```

脚本会替换：

```text
app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/libplayer.so
app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/libplayer.so
```

### IJK native/FFmpeg 重建

普通 App 构建同样不会现场编译 IJK。仓库已经提交两套 ABI 的 `libijkffmpeg.so`、`libijksdl.so` 和 `libijkplayer.so`，Gradle 与 GitHub Actions 直接复用：

```text
app/src/main/jniLibs/arm64-v8a/
app/src/main/jniLibs/armeabi-v7a/
```

`ijk-bilibili-grouped-seek-20260713-083211` 的最终稳定方案保留 TVBoxOSC FFmpeg 4.0 ABI，并在 App 的本地 DASH/HLS 代理层兼容 Bilibili。开发者修改 IJK C/C++、FFmpeg demuxer/protocol、MediaCodec 桥接或 native seek 行为时，才需要重建 `.so`。源码和工具链固定在 `third_party/ijk-native-lock.json`。

安装 native 构建依赖：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk" # Linux 通常为 $HOME/Android/Sdk
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "ndk;28.2.13676358"

# macOS
xcode-select -p >/dev/null 2>&1 || xcode-select --install
brew install openjdk@21 git python@3.10 pkg-config

# Ubuntu 24.04+
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk git python3.10 python3.10-venv build-essential perl pkg-config file
```

重建并安装 arm64 IJK，然后打快速 Release：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash scripts/build_ijk_native.sh --abi arm64-v8a --install
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

Ubuntu 下重建 32 位 IJK：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash scripts/build_ijk_native.sh --abi armeabi-v7a --install
bash gradlew :app:assembleLeanbackArmeabi_v7aRelease -PfastRelease=true
```

脚本会拉取锁定的 IJK/FFmpeg 4.0 与 OpenSSL `openssl-3.2` 源码、应用补丁、检查三项输出并按 `--install` 写入对应 ABI 目录。arm64 与 armeabi-v7a 均已在 macOS 使用 NDK 28.2.13676358 重建成功，并与 DVD 继续使用 r28c；MPV/JNI 已独立升级到 r29。32 位不再需要 NDK 21。只需打包 App 时不必运行该脚本，两套 ARM ABI 也不需要 `yasm`。完整命令和 API 21、`pkg-config` 隔离说明见 `webhome-devkit/docs/应用完整开发文档.md`。

### APK 输出路径

debug 原始输出：

```text
app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
app/build/outputs/apk/mobileArmeabi_v7a/debug/app-mobile-armeabi_v7a-debug.apk
app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk
```

release 构建完成后会自动复制到 `Release/apk/`，Gradle 原始输出仍在 `app/build/outputs/apk/<flavor>/release/`：

```text
Release/apk/mobile-arm64_v8a.apk
Release/apk/mobile-armeabi_v7a.apk
Release/apk/leanback-arm64_v8a.apk
Release/apk/leanback-armeabi_v7a.apk
```

安装到已连接设备：

```bash
adb devices
adb install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

### GitHub 手动发布

仓库内置 `.github/workflows/android-release.yml`,只支持在 GitHub Actions 页面手动触发,不会在每次 push 代码时自动打包。默认 tag 会从 `app/build.gradle` 读取当前 `versionName`:稳定版生成 `v<versionName>-yyyyMMddHHmm`;在 `fongmi-sync` 分支选择 `auto` 通道时生成测试版 `v<versionName>-beta-yyyyMMddHHmm`,APK/JSON 文件名同步追加 `-beta`。

工作流会构建 4 个 release APK,生成同名更新清单 JSON 并发布到 GitHub Release。JSON 默认使用 GitHub Release 固定版本直链，不依赖 CNB 可用性。CNB 同步默认关闭，仅在确认内容权利、平台政策和流量用途后作为可选镜像手动开启。正式发布前建议在 GitHub Secrets 配置:

```text
RELEASE_KEYSTORE_BASE64  # release keystore 的 base64 内容
RELEASE_KEY_ALIAS        # key alias
RELEASE_STORE_PASSWORD   # store password,key password 复用该值
RELEASE_KEY_PASSWORD     # 可选,key password 与 store password 不同时配置
CNB_TOKEN                # 可选，CNB 镜像令牌；仅存 GitHub Secret，需仓库读写权限
```

CNB 可选镜像默认目标为 `https://cnb.cool/fish2035/webhtv-release.git`,仓库标识为 `fish2035/webhtv-release`。`CNB Release Sync` 手动补同步时可通过输入参数临时指定其他仓库。同步脚本只会在 CNB 镜像仓库内将 JSON 改写为 `https://cnb.cool/<slug>/-/releases/download/<tag>/<apk>`；GitHub Release 中的 JSON 仍指向 GitHub。

GitHub Actions 正式发布必须配置签名 secrets,否则会直接失败,避免使用 runner 临时 debug key 生成无法覆盖安装的 APK。

如果 CNB 同步失败,不需要重新打包，GitHub Release 和应用更新清单仍可独立使用。修正 `CNB_TOKEN` 或网络问题后,在 GitHub Actions 手动运行 `CNB Release Sync`,填写已有 `release_tag` 即可补同步；`release_tag` 留空时同步最新 release。令牌不得写入仓库、日志或 workflow 参数；曾在聊天、issue 或日志中暴露的令牌必须立即吊销。

### 签名

默认不需要配置 release 签名文件。未配置签名时，release 包使用 debug signing 兜底，方便 clone 后直接打包测试。

如需正式签名，在根目录的 `local.properties` 增加以下字段；`keyPassword` 可省略，省略时复用 `storePassword`：

```properties
sdk.dir=/path/to/android/sdk
storeFile=/path/to/keystore.jks
keyAlias=your_alias
storePassword=your_password
keyPassword=your_key_password
```

### 播放层依赖

- `app/libs/*.aar`:内置 Hook、TVBus、Thunder、ForceTech、JianPian 播放能力依赖。
- `third_party/maven`:已生成的 `androidx.media3:*:1.11.0-alpha01-fongmi` 本地 Maven 产物，以及定制 `nextlib-media3ext`。
- `third_party/media-lock.json`:记录 Media3、nextlib、FFmpeg、NDK 与 CMake 的精确构建输入，配套脚本为 `scripts/build_media_deps.sh`。
- `third_party/patches/media3-*.patch`:在锁定的 FongMi Media3 源码上叠加本项目补丁；`media3-upstream-playback-fixes-2026-08.patch` 选择性移植 AV1/HEVC HDR 元数据、scrub、DASH、LL-HLS、MP4 IT.35、MediaSession 和 detached Surface 等上游修复，`media3-danmaku-live.patch` 提供 WebSocket 实时弹幕的批量接收、有界队列、TTL、每帧处理上限和聚合统计，`media3-dolby-vision-matroska.patch` 将 MKV `BlockAdditional` 中的 Dolby Vision RPU 追加到对应 HEVC sample，供 Exo 的 DV7 转换链处理。
- `third_party/patches/nextlib-*.patch`:在 `anilbeesetti/nextlib@6ff6cf9d0820382b3c233d018c52e4163b09d345` 上叠加 FFmpeg 软解负载控制和 AV3A/libarcdav3a 支持。
- `third_party/mpv-player-jni`:MPV `libplayer.so` JNI 桥接源码，修改后用 `scripts/build_mpv_player_jni.sh` 重建。
- `app/src/*/assets/mpv-libs/*`:随 APK 打包的 MPV native 库和 JNI 桥接库。
- `nextlib-media3ext`:`io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1-fongmi-softload-av3a-ffmpeg901-r1`，提供 FFmpeg renderer；内置 FongMi FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`（9.0.1）和静态链接的 `libarcdav3a`，Exo 可软解 `audio/av3a`，并在输出设备不接受源多声道 PCM 时下混到立体声。MPV C0-M 现在使用同一 FFmpeg 源 revision，但仍独立构建 `libmv*`/`libmw*`，不能共用该 AAR 内的 `.so`。
- `ExoplayerHdrUtils`:`com.suyashbelekar:exoplayerhdrutils:0.4.0`，提供基于 libdovi 的实时 HEVC RPU 转换。Exo 按“原生 DV7 硬解 → P8.1 转换硬解 → HDR10/HEVC 硬解”选择整次播放路径：原生 DV7 可用时保持原码流；原生 DV7 不可用但 P8.1 可用时使用 mode 2 转换并移除增强层；两者都不可用而设备支持 HDR10/HEVC 时，起播前直接使用 HDR10 基底层。能力查询误报或 P8.1 实际初始化/解码失败时，同一会话最多回退 HDR10 一次，不自动切软解或循环重试；用户选择 HDR10 模式时仍整次使用 HDR10 基底层。

`settings.gradle` 中的依赖顺序是仓库本地 `third_party/maven`、Maven Central、Google Maven、`app/libs` 和 JitPack。`app/build.gradle` 会强制所有 `androidx.media3` 依赖使用 `1.11.0-alpha01-fongmi`，避免传递依赖拉回官方版本。

普通 App/CI 构建直接使用仓库内已经提交完整的 Media3 AAR、POM、module 和校验文件，不会现场拉取或编译 Media3 源码。只有修改 `third_party/patches/media3-*.patch`、Media3 fork 或锁定版本后，才需要重建本地 Maven 产物：

```bash
scripts/build_media_deps.sh
bash gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.danmaku.*'
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

脚本会按 `third_party/media-lock.json` 检出锁定提交、应用全部 Media3 补丁并发布到 `third_party/maven`。上游播放器修复及其精确移植方式记录在 lock 和[相关仓库提交审计](docs/fongmi-related-repos-audit-2026-08-09.md)中；`media3-danmaku-live.patch` 还包含 Media3 渲染侧 Robolectric 压力测试。App 测试使用 MockWebServer 模拟 WebSocket，MockWebServer 仅属于 `testImplementation`，不会进入正式 APK。发布 GitHub Action 会先运行完整 WebSocket 弹幕单测，再构建四个 release APK。

只重建 nextlib/FFmpeg AV3A 扩展时使用：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
scripts/build_media_deps.sh --nextlib-only
```

该命令使用 NDK `28.2.13676358`、CMake `3.22.1` 为 `arm64-v8a` 和 `armeabi-v7a` 重编 FFmpeg/libarcdav3a，校验两端 `libavcodec.so` 的 AV3A 标记后发布到 `third_party/maven`。真实样片来源、SHA-256、实机步骤和验收结果见 [Exo AV3A 验收记录](docs/exo-av3a-acceptance-2026-08-11.md)。

### 常见构建失败

- `Unsupported class file major version`、`invalid source release: 21`：当前终端没有使用 JDK 21。
- `Chaquopy ... is not a valid Python 3.10 command`：安装主机 Python 3.10，并确保当前终端能执行 `python3.10 --version`。
- `SDK location not found`：缺少 `local.properties`，或 `sdk.dir` 指向错误。
- `failed to find target with hash string 'android-37'`：未安装 Android SDK Platform 37。
- `NDK clang++ not found`：重建 MPV/JNI 时确认已安装 `29.0.14206865`，重建 IJK/DVD 时确认已安装 `28.2.13676358`，并检查 `ANDROID_NDK_HOME` 是否与对应 lock 一致。
- `Missing MPV asset directory`：MPV assets 缺失或 ABI 目录名不匹配，确认 `app/src/arm64_v8a/assets/mpv-libs/arm64-v8a` 和 `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a` 存在。
- `missing llvm-readelf/readelf`：运行完整 MPV assets 校验时缺少 ELF 工具；Linux 安装 `binutils`，macOS 安装 NDK 29.0.14206865 或设置 `ANDROID_NDK_HOME`。
- 运行后提示 `dlopen failed`、`libplayer.so` 或 `libmpv.so` 相关错误：先确认对应 ABI 的整套 MPV/FFmpeg `.so` 已打包，并用 NDK `llvm-readelf -d` 检查 `SONAME`/`DT_NEEDED`。只有 JNI 或 client API 变化才运行 `scripts/build_mpv_player_jni.sh`；该脚本不能修复不配套的 `libmpv.so`、FFmpeg 或 libplacebo。
- `Could not resolve ...`：依赖下载失败，检查网络或设置代理后重新执行 Gradle。
- `Permission denied: ./gradlew`：本仓库文档统一使用 `bash gradlew`，不依赖可执行位。

## 目录结构

```text
app/          Android 主应用(mobile/leanback 双 flavor)
catvod/       CatVod 抽象层、Spider 接口、网络和代理工具
quickjs/      JavaScript Spider 运行时
chaquo/       Python Spider 运行时
webhome-devkit/ WebHome 开发套件(文档、主页/扩展示例、模板、AI skills)
scripts/      Media3 和 MPV JNI 本地依赖构建脚本
third_party/  Media3 本地 Maven、nextlib 源码、MPV JNI 源码和版本锁定文件
Release/      release 构建的 APK 输出
other/        Logo 图片和辅助工具
```

## 上游基线

| 仓库 | 分支 | Commit |
| --- | --- | --- |
| [TV](https://github.com/FongMi/TV) | `fongmi` | `1a19fee278fa2234da725d61a53bf59b69fe9127`（`560 / 5.6.0`） |
| [FFmpeg](https://github.com/FongMi/FFmpeg) | `release-9.0-fongmi` | `177f090e0503b7e013922ca903bde14b1c375f18` |
| [mpv-android](https://github.com/FongMi/mpv-android) | `fongmi` | `99a60ad2141d5ace94453590903c2c6b9a0a2443` |
| [media](https://github.com/FongMi/media) | `release` | `2bc207851df311340767e913931ca7b28cab1794` |
| [mpv](https://github.com/FongMi/mpv) | `fongmi` | `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42` |
| [libplacebo](https://github.com/FongMi/libplacebo) | `fongmi` | `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5` |
| [CatVodSpider](https://github.com/FongMi/CatVodSpider) | `main` | `a511a606a287089dffdd8374db75d95ec5f372b6` |

机器可读记录见 [`third_party/fongmi-repositories-lock.json`](third_party/fongmi-repositories-lock.json)。

## 免费声明与社区分享

WebHomeTV 是基于开源生态二次开发的技术学习与研究项目,软件本体完全免费,不提供任何付费服务、影视内容、直播源、接口源、资源存储或内容分发能力。

本软件仅供技术学习、研究和个人测试使用,请在下载、安装或试用后 24 小时内自行卸载。继续使用本软件所产生的一切行为及后果,由使用者自行承担。

本软件不内置、不售卖、不传播任何影视资源,不对用户自行添加的接口、站源、插件、脚本、链接、网盘资源或第三方服务内容负责。使用者应遵守所在地法律法规,尊重版权方和内容提供方的合法权益,不得将本软件用于任何侵权、盗版、传播非法内容或其他违法违规用途。

严禁任何个人或组织以本软件名义进行售卖、引流、收费维护、会员服务、广告变现、盒子预装、电视盒子捆绑销售或其他任何形式的获益行为。对于将本软件内置于电视盒子、机顶盒、付费套餐或商业服务中进行销售、推广的行为,项目方明确反对并予以谴责。

### 友情链接
[![Linux.do](https://img.shields.io/badge/-Linux.do-1c1c1e?style=flat-square&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZlcnNpb249IjEuMiIgYmFzZVByb2ZpbGU9InRpbnktcHMiIHdpZHRoPSIxMjgiIGhlaWdodD0iMTI4IiB2aWV3Qm94PSIwIDAgMTIwIDEyMCI+CiAgPGNsaXBQYXRoIGlkPSJhIj4KICAgIDxjaXJjbGUgY3g9IjYwIiBjeT0iNjAiIHI9IjQ3Ii8+CiAgPC9jbGlwUGF0aD4KICA8Y2lyY2xlIGZpbGw9IiNmMGYwZjAiIGN4PSI2MCIgY3k9IjYwIiByPSI1MCIvPgogIDxyZWN0IGZpbGw9IiMxYzFjMWUiIGNsaXAtcGF0aD0idXJsKCNhKSIgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMTAwIiBoZWlnaHQ9IjMwIi8+CiAgPHJlY3QgZmlsbD0iI2YwZjBmMCIgY2xpcC1wYXRoPSJ1cmwoI2EpIiB4PSIxMCIgeT0iNDAiIHdpZHRoPSIxMDAiIGhlaWdodD0iNDAiLz4KICA8cmVjdCBmaWxsPSIjZmZiMDAzIiBjbGlwLXBhdGg9InVybCgjYSkiIHg9IjEwIiB5PSI4MCIgd2lkdGg9IjEwMCIgaGVpZ2h0PSIzMCIvPgo8L3N2Zz4K)](https://linux.do/)
