# MPV Native 可复现构建

本文是 WebHTV 重新生成 `libmpv.so` 及其 FFmpeg 依赖的权威说明。

## 两种构建必须分开

日常 App 构建不会编译 MPV native。仓库已经提交以下目录中的 `.so`，Gradle 和 GitHub Actions 直接把它们作为 assets 打进 APK：

```text
app/src/arm64_v8a/assets/mpv-libs/arm64-v8a/
app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/
```

因此普通用户 clone 后直接执行 Gradle 即可，不需要运行本文的 native 脚本：

```bash
bash gradlew :app:assembleMobileArm64_v8aDebug
```

只有升级 MPV、FFmpeg、libplacebo、NDK，或需要重新生成原生库时，维护者才手动运行：

```bash
scripts/build_mpv_native.sh
```

该脚本没有挂接到 Gradle，也不会被 GitHub Actions 自动执行。Android Release Action 只对仓库已提交的 native assets 做完整性和 ELF 依赖校验，不会现场重新编译 MPV。

## 固定输入

所有上游仓库、commit、tar 包 SHA-256、NDK 和 Meson/Ninja 版本统一记录在：

```text
third_party/mpv-native-lock.json
```

构建包装脚本还会对当前选择的完整 lock 文件计算 SHA-256，并用它覆盖上游构建框架的 prefix cache 标识，避免升级 FFmpeg、字体栈、curl 或 nghttp2 后误复用旧缓存；使用 `--lock-file` 测试其他组合时也会生成独立缓存身份。但 lock 只锁定上游输入；最终可复现二进制还取决于 `scripts/build_mpv_native.sh`、脚本引用的 `third_party/patches/*`、`third_party/mpv-native-overrides/` 和 JNI 源码。修改补丁或 override 后应使用默认干净构建，不应依赖只由 lock hash 区分的旧 prefix。

当前锁定组合如下。截至 2026-08-17，两套 ARM ABI assets 已通过当前脚本要求的版本、能力标记、ELF `SONAME`/`DT_NEEDED` 和打包规则校验：

| 组件 | 固定版本 |
| --- | --- |
| 构建框架/JNI参考 | `FongMi/mpv-android@99a60ad2141d5ace94453590903c2c6b9a0a2443` |
| NDK | `29.0.14206865`（r29），API 24 |
| MPV | `FongMi/mpv@cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`（`0.41.0-940-gcca559b41`） |
| MediaCodec/Vulkan | FongMi 分支内建 AImageReader/AHardwareBuffer OpenGL/Vulkan 后端、sync-fd、HDR/Dolby Vision 和双 Surface OSD；不再叠加旧 `fd679c81` 或 transient patch |
| Dolby Vision双层硬解 | `third_party/patches/mpv-android-dovi-el-surface.patch`：把 GPU 杜比元数据处理与独立增强层解码拆成两项能力；Android AImageReader 只提供单路生产者 Surface，因此 `gpu-next` 保留 DV5 映射、DV 来源识别与 HDR10 基础层回退，但不启动第二个无 Surface 的 `mediacodec-copy`；非 Android 输出仍可显式声明 EL 合成能力 |
| AudioTrack音频直通 | 保留 FongMi MPV 的 passthrough carrier rate 修复：PCM 可跟随设备原生采样率，SPDIF/IEC61937 必须保留编码器声明的载波采样率；E-AC3、TrueHD、DTS-HD 使用 192 kHz，不能改写为常见的 48 kHz，否则 MPV 会判定格式不一致并回退 PCM。`mpv-audiotrack-truehd-channel-mask.patch` 保留 TrueHD 全 API 7.1 兼容规则，并在 Android 12+ 仅对实际 8-channel carrier 使用 7.1 mask；DTS-HD HRA/其它 2-channel carrier 仍使用 stereo。App 侧对 DTS-HD 同时验证 stereo/7.1，取 Media3、HDMI/ARC/eARC/USB 路由编码和 MPV IEC61937 载波探测的交集；任一所需载波不被系统接受时不启用该格式直通，由 MPV 回退 PCM 解码。 |
| MediaCodec直出 | `mpv-mediacodec-embed-optional-osd.patch` 允许关闭字幕时不创建额外 OSD Surface；`mpv-mediacodec-embed-timed-release.patch` 按播放 PTS 将缓冲帧提交给 MediaCodec，避免立即释放导致持续掉帧；`mpv-mediacodec-output-timing-diagnostics.patch` 只增加有界的提交/迟到/掉帧时序诊断。 |
| Vulkan硬解稳定性与功耗 | `auto` 通过 `mpv-android-vulkan-smart-backend.patch` 恢复优先 direct AHardwareBuffer 采样，避免 HDR 默认执行全分辨率转换；direct 不支持时回退 queue-safe stable pool，再回退通用 conversion。显式 `stable` 保留给问题驱动，App 在自动模式发生视频输出错误或已识别首帧超时时会重建为 stable，并按设备环境记忆。stable 额外尝试两种 packed RGB10 storage 格式，最后才扩大到 RGBA16F。 |
| Matroska代理Seek | `third_party/patches/mpv-matroska-segment-end.patch`，可Seek但HTTP总长度未知时使用MKV自身声明的Segment边界读取SeekHead/Cues |
| FFmpeg | `FongMi/FFmpeg@177f090e0503b7e013922ca903bde14b1c375f18`（9.0.1 fongmi） |
| 本地代理Range兼容 | `third_party/patches/ffmpeg-webhtv-proxy-range.patch`，识别App内部代理验证后的206起点标记，不把第三方未知长度伪装成完整文件长度 |
| libplacebo | `FongMi/libplacebo@b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`（7.375.0） |
| curl | 8.21.0，MbedTLS，HTTP/HTTPS、HTTP/2 |
| nghttp2 | 1.69.0 |
| 字幕/字体 | libaribcaption 1.1.1、libass、fontconfig 2.18.2、libxml2 2.15.3，全部静态链接 |
| 光盘/归档 | libbluray 1.4.1、libarchive 3.8.7、libdvdread 7.0.1、libdvdnav 7.0.0 |
| 字符集/音频 | libiconv 1.19、uchardet 0.0.8、rubberband 4.0.0、FFmpeg AV3A/libarcdav3a |
| dav1d | `54706fc6bc0cdecab7e9593974a4039cc038fca7`（1.5.4） |

其他字体、TLS、Lua 和构建工具版本也在 lock 文件中，不要只修改脚本里的单个组件。

当前 curl 使用 MbedTLS 3.6.7 和 nghttp2 1.69.0，静态链接进 `libmpv.so`，不会给 APK 增加独立 `libcurl.so` 或 `libnghttp2.so`。构建明确关闭 HTTP/3，不包含 ngtcp2、nghttp3 或 quiche。MPV 直接远程 HTTP/HTTPS 可使用 curl 后端；App 本地 HLS 代理、`stream_cb` 和 FFmpeg/lavf 输入仍保留原路径。

libass 已启用 fontconfig，fontconfig 及其 libxml2 XML 后端同样静态链接进 `libmpv.so`，不会增加独立 `.so`。App 启动 MPV 时生成内容感知的 `fonts.conf`，只登记设备上可读的 `/system`、`/product`、`/system_ext`、`/vendor` 和 `/odm` 字体目录，并把索引放在 App cache。这样可按字符回退到设备已有中文字体；APK 不携带中文字体资产，媒体或 ASS 自带的字体附件仍由 `embeddedfonts=yes` 使用。

## 主机准备

支持 macOS 和 x86_64 Linux。先安装 JDK 21、Android SDK、NDK r29，并确保根目录 `local.properties` 的 `sdk.dir` 正确。

macOS：

```bash
xcode-select --install
brew install cmake gperf pkg-config
```

Ubuntu/Debian：

```bash
sudo apt-get update
sudo apt-get install -y build-essential cmake gperf git curl file pkg-config python3 python3-venv perl
```

安装 Android NDK：

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;29.0.14206865"
```

如果 NDK 不在 `$ANDROID_HOME/ndk/29.0.14206865`，设置：

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
```

网络需要代理时，在执行脚本前设置标准代理环境变量：

```bash
export https_proxy=http://127.0.0.1:7897
export http_proxy=http://127.0.0.1:7897
export all_proxy=socks5://127.0.0.1:7897
```

## 从 clone 到重新生成 arm64 `.so`

```bash
git clone https://github.com/fish2018/webhtv.git
cd webhtv
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
scripts/build_mpv_native.sh --abi arm64-v8a --install
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

`--install` 会更新八个 MPV/FFmpeg 库和匹配 NDK 的 `libc++_shared.so`，但保留仓库已有的 `libplayer.so`。

同时生成两种 ARM ABI：

```bash
scripts/build_mpv_native.sh --abi all --install
```

不重新编译，只校验仓库已经提交的两套 native assets：

```bash
bash scripts/verify_mpv_native_assets.sh
```

发布或提交 native 更新前使用完整 ELF 校验模式：

```bash
bash scripts/verify_mpv_native_assets.sh --require-elf
```

Linux 可使用系统 `readelf`；macOS 会尝试从 `ANDROID_NDK_HOME`、`ANDROID_HOME` 或 lock 指定的 NDK 目录寻找 `llvm-readelf`。普通模式在找不到 ELF 工具时仍会检查文件集合、ABI 和嵌入版本字符串，并明确提示跳过了 `SONAME`/`DT_NEEDED` 检查。

只下载并核对源码，不编译：

```bash
scripts/build_mpv_native.sh --prepare-only
```

指定并行数或构建目录：

```bash
scripts/build_mpv_native.sh --abi arm64-v8a --jobs 8 --work-dir /tmp/webhtv-mpv-native
```

默认进行干净构建。开发脚本本身时可以使用 `--incremental` 保留 prefix，但正式生成待提交的 `.so` 时不要使用该参数。

## 脚本执行内容

`scripts/build_mpv_native.sh` 会自动完成：

1. 读取 `third_party/mpv-native-lock.json`。
2. 检查 NDK revision 和 LLVM 工具。
3. 在独立 Python venv 中安装固定版本 Meson/Ninja及 MbedTLS 生成工具依赖。
4. 下载构建框架和每个固定 commit，初始化 MbedTLS、FreeType、libplacebo 子模块，并校验所有发行 tar 包 SHA-256。
5. 对固定 FFmpeg commit 应用 `third_party/patches/ffmpeg-webhtv-proxy-range.patch`，只接受App内部代理写入的精确Range起点标记，使缺少`Content-Range`的206响应仍能按请求偏移重连；它不会制造未知的资源总长度。同时应用 `third_party/patches/ffmpeg-mediacodec-port-starvation.patch`，对 MediaCodec 输入、输出端同时不可用的状态采用短时有界等待，并返回真实解码错误让 mpv 的硬解失败计数触发下一硬解或软解回退，避免把端口永久不可用误判成普通 `EAGAIN` 后无限重试。
6. 固定 MPV 到 FongMi 完整分支；该分支已经包含 AImageReader OpenGL/Vulkan、sync-fd、HDR/Dolby Vision、双 Surface OSD、直播状态、Android helper scheme，以及直通时保留 SPDIF/IEC61937 载波采样率的 AudioTrack 修复。WebHTV 按以下顺序应用 MPV 补丁：
   - `mpv-stream-cb-disc-controls.patch`
   - `mpv-android-dovi-el-surface.patch`
   - `mpv-dovi-profile7-hdr10-base-layer.patch`
   - `mpv-audiotrack-truehd-channel-mask.patch`
   - `mpv-mediacodec-embed-timed-release.patch`
   - `mpv-mediacodec-embed-optional-osd.patch`
   - `mpv-mediacodec-output-timing-diagnostics.patch`
   - `mpv-android-vulkan-conversion-default.patch`
   - `mpv-android-vulkan-smart-backend.patch`
   - `mpv-android-vulkan-legacy-backend.patch`
   - `mpv-aimagereader-stable-flow.patch`
   - `mpv-p2-generic-uv.patch`
   - `mpv-matroska-segment-end.patch`

   Profile 7 HDR10 回退使用 FFmpeg 官方 `dovi_split` 的 `mode=bl` 在 demux 层移除 EL/RPU，GPU 与电视直出都只接收可独立解码的 HDR10 基础层。MediaCodec 直出允许无 OSD Surface，并按 PTS 调度缓冲帧释放。Vulkan `auto` 优先 direct，然后回退 stable 和通用 conversion；`legacy` 保留早期 compute 路径供兼容性验证。AImageReader 按回调序列领取图像，并在 conversion fence 完成前保持 AImage 生命周期。P2 generic compute/fragment shader 的 crop 与归一化除法由 CPU 每帧预计算，两个 SPIR-V header 必须由锁定 NDK r29 `glslc` 从补丁后的 shader source 重新生成并通过 `spirv-val`，不能手工修改数组。
7. 按依赖顺序构建字符集/压缩库、MbedTLS、dav1d、libxml2、FreeType、libaribcaption、FFmpeg、字体栈、shaderc、libplacebo、curl+nghttp2、libbluray、libarchive、DVD 库、rubberband 和 MPV。
8. 把 FFmpeg 的文件名、ELF `SONAME` 和 `DT_NEEDED` 从 `libav*`/`libsw*` 等长修改为 `libmv*`/`libmw*`。
9. 使用 NDK `llvm-strip --strip-unneeded` 处理最终库。
10. 使用 NDK `llvm-readelf` 检查每个 SONAME、MPV 的完整依赖和 Vulkan 依赖，并检查 MPV/libplacebo/curl 版本、HTTP/2、可选 OSD Surface、MediaCodec timestamped release、Vulkan AImageReader/sync-fd 与 `direct/legacy/stable` 后端、Dolby Vision 增强层 Surface 隔离、DV7 HDR10 基底层、AV3A、ARIB/TTML、MMT/TLV、代理 Range 及 Matroska Segment 标记；同时拒绝动态 fontconfig/libxml2 依赖。

`scripts/verify_mpv_native_assets.sh` 对已提交 assets 执行同类校验，Android Release Action 会在 Gradle 打包四个 APK 前以 `--require-elf` 模式调用它，防止 lock、补丁、arm64/armv7 assets 或静态能力不一致的二进制进入 Release。

未指定 `--install` 时，输出位于：

```text
build/mpv-native/output/arm64-v8a/
build/mpv-native/output/armeabi-v7a/
```

`build/` 已被 `.gitignore` 忽略，源码和中间文件不会进入提交。

## 为什么必须成套更新

`libmpv.so` 直接链接固定 FFmpeg ABI。只替换 `libmpv.so`，保留旧 FFmpeg，可能在设备上出现：

```text
dlopen failed: cannot locate symbol "av_dynamic_hdr_smpte2094_app5_alloc"
```

当前 FongMi MPV 直接依赖 FFmpeg 9 新增/调整的 HDR、Dolby Vision、MMT/TTML 与 MediaCodec API，并要求 libplacebo API 375。不能通过篡改 pkg-config 版本号让 MPV 链接旧 libplacebo，也不能从 FFmpeg 9 分支只挑单个 AV3A 或 MMT 提交；AV3A 还需要分支内携带的 `dependency/avs3a` 源码先构建 `libarcdav3a`。

最终目录必须包含：

```text
libc++_shared.so
libmpv.so
libmvcodec.so
libmvdevice.so
libmvfilter.so
libmvformat.so
libmvutil.so
libmwresample.so
libmwscale.so
libplayer.so
```

前九个由 native 脚本维护；`libplayer.so` 是 App JNI 桥接库。

## `libplayer.so` 的重建边界

原生依赖脚本仍会保留已有 `libplayer.so`，但本次 FongMi MPV/mpv-android 同步必须另行重建 JNI：

```bash
scripts/build_mpv_player_jni.sh
```

- 新 JNI 增加 command reply、byte-array property、END_FILE 细节、异步 shutdown 和引用/异常清理。
- 视频 Surface、OSD Surface 与 command 必须串行化，并支持 `android-osd-wid`。
- WebHTV 的 ISO/DVD stream callback 与 END_FILE 扩展必须在新实现上重新移植。

以后若只重编完全相同 client API/JNI 源码的 MPV/FFmpeg，才可以复用 `libplayer.so`。

## P2-2 DV7 metadata/codecpar 窄适配（2026-08-29）

P2-2 在现有 `mpv-dovi-profile7-hdr10-base-layer.patch` 内完成 Profile 7 HDR10 fallback 的 metadata/codecpar/error 完整性修复：缺少 `dv_el_present` 时仍仅对显式 HDR10 fallback 创建 `dovi_split=mode=bl`，检查 codec parameter 转换、BSF option/init 返回值，将成功过滤后的 `par_out` 原子同步回 decoder 参数并清除 EL 标记，同时在 packet 长度转换前拒绝超过 `INT_MAX` 的输入。现有三态 packet ownership、精确零拷贝、Surface Direct、单 Surface EL gate、DV7 设置和 FFmpeg `libmv*` 命名空间均保留；未升级 lock、FFmpeg、libplacebo、JNI 或启用 Android EL。

本阶段使用 NDK r29 对 `arm64-v8a` 与 `armeabi-v7a` 完整重建并安装 native assets；一次 `scripts/verify_mpv_native_assets.sh --require-elf` 通过，APK `app-mobile-arm64_v8a-debug.apk` 内十个 arm64 MPV 资产与工作区完全一致，两个 `libplayer.so` 保持字节不变。用户在 USB 连接的 vivo V2453A 上确认安装后的 DV7 及邻接播放验证通过。实现提交为 `ba47756d7e463abeb9377088b819a2520e150935`，恢复 tag 为 `recovery/P2-2-MPV-DV7-METADATA-CODECPAR/20260829065811-ba47756d7e46`。完整来源、哈希、验证和回滚记录见 [P2-2-mpv-dv7-metadata-codecpar.md](../docs/P2-2-mpv-dv7-metadata-codecpar.md)。

## 提交前验证

至少构建一个快速 Release：

```bash
bash gradlew :app:assembleMobileArm64_v8aRelease -PfastRelease=true
```

设备回归范围：

- OpenGL普通播放、硬解状态。
- OpenGL LUT 生效，预览竖线可见，拖动连续且无闪烁。
- Vulkan普通播放和 LUT。
- Vulkan 硬解时确认 `hwdec-current=mediacodec`，默认 `auto` 日志应出现 `WebHTV Vulkan auto backend prefers direct AHardwareBuffer sampling` 和 `Using Vulkan YCbCr AHardwareBuffer sampling`；仅 direct 不支持或自动恢复后才应出现 stable conversion。显式 `legacy` 应使用早期三输出 compute 路径，`stable` 应使用有界 fence pool，两者都不得无条件回退到 `mediacodec-copy`。
- Dolby Vision Profile 7 REMUX 在 HDR10 回退下确认日志包含 `stripping EL/RPU before decoder`，OpenGL/Vulkan/电视直出均只有基础层进入 MediaCodec、`hwdec-current=mediacodec` 且能持续出帧；系统日志不得出现端口饥饿、`connect: already connected` 或 MediaCodec `-22`。
- 电视直出/Dolby Vision 使用独立视频 Surface；启用字幕/OSD 时透明 OSD Surface 可见，关闭字幕时允许不创建额外 OSD Surface，退出或换集不死锁。
- MediaCodec 直出需检查 timestamped release 统计、输出迟到和掉帧时间；不得因立即释放缓冲帧持续提前显示，也不得把有界调度等待扩大为主线程长时间阻塞。
- MPV 音频直通在 HDMI 功放链路分别验证 AC3、E-AC3、DTS-HD HRA/MA 与 TrueHD/Atmos，确认功放能锁定格式并亮灯；E-AC3、DTS-HD、TrueHD 日志应保持 IEC61937 的 192 kHz 载波采样率。DTS-HD HRA 保持 stereo carrier，Android 12+ 的 8-channel DTS-HD MA 使用 7.1 carrier；能力探测失败时允许明确回退 PCM，但不得循环重建 AudioTrack。
- MMT/TLV、TTML/ARIB 字幕、AV3A、Blu-ray/DVD ISO、压缩包播放入口分别做功能回归。
- 文本字幕、图形字幕以及播放中切换。
- 使用缺少部分中文字形的 SSA/ASS 字幕确认可逐字回退，不出现 `□`；同时确认媒体内嵌字体仍生效。
- 播放成功前切换播放器内核。
- 连续起播、退出、换线路，并检查 crash buffer 中没有 destroyed-mutex。
- 大型 MKV/REMUX、硬解/软解以及前后台切换。

不要提交 `build/mpv-native/`。只提交 lock、脚本、文档和最终 assets 中发生变化的 `.so`。

## 常见错误

| 错误 | 处理 |
| --- | --- |
| `missing command: pkg-config` | macOS 安装 `brew install pkg-config`；Debian/Ubuntu 安装 `pkg-config` |
| `missing command: cmake` 或 `gperf` | 安装 CMake 与 gperf；CMake 用于 AV3A、ARIB、归档/字符集依赖 |
| `missing llvm-readelf/readelf` | Linux 安装 `binutils`；macOS 安装 NDK r29，或设置 `ANDROID_NDK_HOME`/`READELF` |
| `Android NDK ... not found` | 安装 `ndk;29.0.14206865` 或设置 `ANDROID_NDK_HOME` |
| 下载 commit/tar 包失败 | 检查代理；重新执行会复用已校验缓存 |
| tar 包 `SHA-256 mismatch` | 不要绕过检查；确认下载地址或 lock 是否经过审核 |
| `libmpv.so does not depend on libvulkan.so` | 构建参数或 libplacebo/shaderc未正确启用 Vulkan |
| `unrenamed FFmpeg dependency` | 不要手动复制中间产物，使用脚本生成的 output/assets |
| App `dlopen failed` | 检查是否只更新了部分 `.so`，并确认 ABI、SONAME 和 `DT_NEEDED` |
| macOS 构建 shaderc 时出现 `fcntl(): Bad file descriptor` | NDK make jobserver 的输出噪声；只要后续仍在编译且脚本最终显示 `MPV native build completed`，无需处理 |
