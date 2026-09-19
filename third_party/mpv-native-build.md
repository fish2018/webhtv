# MPV Native 可复现构建

本文是 WebHTV 重新生成 `libmpv.so` 及其 FFmpeg 依赖的权威说明。

2026-09-17 C-AVS3 MediaCodec：`ffmpeg-avs3-mediacodec.patch` 接在FFmpeg diagnostics补丁后，加入 `avs3_mediacodec` / `video/avs3`，将raw/av3c序列配置交给现有MediaCodec会话。显式硬件上下文与hardware-only查询均为必需，无NDK按MIME绕过筛选的兜底；设备不支持时不自动软解，软件后端保留。正式FFmpeg override显式启用并校验该decoder，日常Debug/Release直接打包已提交资产。仅更新两ARM codec，其他18个MPV库保持；依赖锁定revision、JNI/渲染/HPM与Exo制品不变。两ABI/16KB/导出、9项Java检查和手机JNI/NDK无硬件拒绝已通过；AVS3硬件实际出帧/profile兼容/性能仍需目标设备验收。精确制品、补丁哈希、原始结果与回滚见 [C-AVS3 MediaCodec](../docs/C-AVS3-video-decoding.md#mpv-avs3-mediacodec2026-09-17)。

2026-09-17 C-AVS3 高级档次：在原有 uavs3d 之外，加入固定 HPM 15.0 `0c7ac42edfac6d18b92b58a5ef43bca58526ca7a` 的 `0x32` 软件后端，由 `build_avs3_native.sh` 强制调用 `build_hpm_native.sh`，按 MPV 自有 NDK29/ABI 独立静态链接。HPM 内部符号统一隔离，ARM SIMD 使用固定 SSE2NEON；输入、分配、错误、时间戳与 flush 由适配层管理。只更新两 ABI codec，保留其他 MPV/FEL/字幕库；硬解模式不自动切软件。原 HPM 许可证限定标准开发、测试与推广，原文与 SSE2NEON MIT 许可随 nextlib AAR/APK 提供，本地构建成功不代表已取得对外商业分发授权。具体已验收范围、性能与未验收项以 [C-AVS3 当前记录](../docs/C-AVS3-video-decoding.md) 为准。

2026-09-17 P2-4第9.25节：FEL起播的GPU上下文选项改用mpv原有 `m_option_copy` 管理对象列表所有权，修复配置析构释放静态内存导致的native abort。仅修改FEL patch的 `vo_gpu_next.c` 块并增量重建两ARM ABI的libmpv，保留完整FEL、渲染回退和手动视频解码合同；其余18个MPV库（包括AVS3 codec/JNI）逐字节不变，依赖锁和公开导出不变。真实ta/option析构的ASan/UBSan先复现旧错误再通过修复；构建命令、最终产物与手机验证见[任务第9.25节](../docs/P2-4-mpv-android-fel.md)。普通Gradle构建直接包含修复后的assets。

2026-09-16 C-AVS3：固定 uavs3d `0e20d2c291853f196c68922a264bcd8471d75b68`，由共享 `build_avs3_native.sh` 按 MPV 自有 NDK/prefix 构建并静态链接到 codec；支持基准档次 0x20/0x22 的 8/10-bit，未实现 High profile 0x30/0x32。Android ARMv7 使用 softfp，pthread 来自 libc；FFmpeg 包装补丁处理初始化数据边界、错误传播及 flush。只更新两 ABI 的 `libmvcodec.so`，其余 MPV/渲染/FEL/字幕库保持基线字节；Exo 另建 `libavcodec.so`，两套命名空间不混用。来源、手机逐像素证据、限制和回滚见 [C-AVS3](../docs/C-AVS3-video-decoding.md)。日常 APK 构建直接包含提交的能力，不需要额外开关。

2026-09-16 P2-4第9.18节：为无ADB电视补充`WebHTV FEL wait sample`，仅FEL/视频INFO记录开启时每3秒采样一次descriptor bind/push的线程调度计数、CPU/墙钟及acquire fence的poll(0)状态，权限/计数缺失明确未知，采样开销独立记录。`WebHTV FEL descriptors: capability-v=1`独立记录GPU/驱动/API和push扩展支持/启用情况；不改变扩展启用、重建、位深、同步或线程。两ABI同锁libmpv、host与13项Java检查、TV64 APK验证通过，其余18库不变；来源、原始日志、哈希、判读限制及回滚见[任务第9.18节](../docs/P2-4-mpv-android-fel.md)。这是一份诊断候选，电视卡顿尚待新Web日志裁决。

2026-09-14 P2-4第9.17节续修：首次实际FEL硬件draw另设有界10s初始化，结束只排除占用时间、普通帧仍750ms，seek/轮询不续期。FEL stable按实际启用的KHR扩展/入口/YCbCr数量门控push descriptors，布局创建失败回普通set；每帧仍完整绑定当前图像并ONE_TIME录制，不重放旧命令，不改10bit/NLQ与同步。新增`WebHTV FEL renderer init`、`WebHTV FEL descriptors`（mode/reason/max-push）和`push-descriptors`耗时进入有界App日志。两ABI/产物及实际SHA-256以[任务第9.17节](../docs/P2-4-mpv-android-fel.md)为准；本单元仅重编两份libmpv、其余18库不变。host缓存/生命周期测试需给`test_mpv_fel_contract.sh`提供`ANDROID_NDK_HOME`或`VULKAN_HEADERS_INCLUDE`，只用Vulkan声明，不加载GPU驱动。电视实际扩展能力、画面正确性及实时性能仍须实测验收。

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

P9 2026-09-10 修复保留以下独立补丁（顺序由脚本锁定）：

- `mpv-discnav-poll.patch` 接在原盘输入补丁之后，在demux线程处理纯导航事件；菜单动画不再依赖暂停时的媒体包需求。
- `ffmpeg-mediacodec-output-serialization.patch` 接在硬件音频补丁之后，将每个decoder context的输出释放、serial检查、flush与stop串行化，沿用原引用计数生命周期。
- `mpv-mediacodec-embed-reset.patch` 接在输出时序诊断补丁之后，在VO reset时丢弃待提交图像/旧PTS，保留硬件直出与双Surface。

这些补丁不改变库版本或公开JNI API。重建必须覆盖两ABI的FFmpeg和MPV，再由`--stage-only --install`验证并安装整套产物；不能仅复制旧prefix声称已构建。已有同锁缓存可在完整补丁应用验证后，显式调用框架的`buildall.sh -n --arch <arm64|armv7l> ffmpeg`及`mpv`增量重编受影响组件，并保存实际编译与链接日志。JNI源/头未改时不重编`libplayer.so`。

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
| MediaCodec直出 | `mpv-mediacodec-embed-optional-osd.patch` 允许关闭字幕时不创建额外 OSD Surface；`mpv-mediacodec-embed-timed-release.patch` 按播放 PTS 将缓冲帧提交给 MediaCodec，避免立即释放导致持续掉帧；`mpv-mediacodec-output-timing-diagnostics.patch` 只增加有界的提交/迟到/掉帧时序诊断。`ffmpeg-audio-mediacodec-hardware-first.patch` 让已有 AAC/MP3/AMR 音频 wrapper 只创建真实硬件 Codec，初始化失败后由 mpv 继续普通 FFmpeg decoder。 |
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
5. 对固定 FFmpeg commit 应用 `third_party/patches/ffmpeg-webhtv-proxy-range.patch`，只接受App内部代理写入的精确Range起点标记，使缺少`Content-Range`的206响应仍能按请求偏移重连；它不会制造未知的资源总长度。同时应用 `third_party/patches/ffmpeg-mediacodec-port-starvation.patch`，对 MediaCodec 输入、输出端同时不可用的状态采用短时有界等待，并返回真实解码错误让 mpv 的硬解失败计数触发下一硬解或软解回退，避免把端口永久不可用误判成普通 `EAGAIN` 后无限重试。随后应用 `third_party/patches/ffmpeg-audio-mediacodec-hardware-first.patch`：API 29+ 要求 `isHardwareAccelerated()` 且排除 `isSoftwareOnly()`，旧系统排除已知软件 Codec 名称；没有真实硬件时让该 MediaCodec decoder 初始化失败并交回 mpv 软件后备，不在音频 buffer 热路径增加探测。
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

## P2-4 Android FEL 双层重建（2026-09-12）

独立补丁 `mpv-android-fel.patch` 在现有补丁序列末尾应用，不升级任何锁定依赖。`android-dovi-fel` 默认关闭；只有 App 手动选择「FEL 双层重建」、识别源 Profile 7 并使用 `gpu-next` 时才开启软件 EL 配对。原 `VO_CAP_GPU_DOVI_EL` Android gate 保留，新增的软件 EL 能力不能启动第二路 MediaCodec。

补丁适配 `FongMi/mpv@06ec6e1746e5cbdcd271e613fdb1f7f7ecd36042` 的 EL force_swdec 必要接线，不吸收其 Surface/HDR/OSD 重写。可靠性续修参考 mpv #18375 的预热/持帧问题，仅在新模式将 BL 待配对/预取限制为1/1、EL为8/4；两路独立有界队列，不能把 `hwdec-extra-frames` 当成 MediaCodec Surface 扩池。保持 PTS/NLQ，连续8次解码错误且无成功帧时结束失败解码，不改变禁止 BL 软回退的策略。现有 FFmpeg、libplacebo、原盘/音频补丁保持原样。

日志31进一步暴露配对之外的图像持有：FEL 的 Vulkan auto/direct 实例内部改用既有 stable 原始YUV暂存池，不写回用户后端设置，也不改变非FEL选择。暂存至少10-bit，不允许8-bit降级；GPU复制提交后以最多100ms的有界fence等待尝试及时归还AImage，超时仍保留图像所有权，不提前释放或CPU回读。`WebHTV FEL GPU staging`和`WebHTV FEL GPU pool`将实际后端、提交/完成、仍持有图像、位深与等待时间写入App调试日志；旧的direct/fence实现不改。该适配增加新FEL模式的GPU拷贝/显存需求，实际花屏、持续播放和性能仍需电视验证。

本阶段使用同锁温缓存，只重编两 ABI 的 `mpv`：**串行**执行 `buildall.sh -n --arch arm64 mpv` 与 `--arch armv7l mpv`（当前脚本会修改共享 `meson.build` 的 iconv 路径，不能并行），随后 `scripts/build_mpv_native.sh --abi all --stage-only --install`。可靠性续修还修改 canonical JNI 的 NODE 回调，必须 `scripts/build_mpv_player_jni.sh --abi all --install` 重建 `libplayer.so`，使 Java 和二进制成套交付。NODE 序列化有深度、节点及字节限制，轨道/章节通过订阅快照传递，缺值不回退逐项 JNI 查询；不改现有串行 mutation/Surface/shutdown 队列。

不得跳过实际编译而仅复制旧 prefix。`scripts/test_mpv_fel_contract.sh` 执行 NODE 与 FEL 策略的 host ASan/UBSan 检查及生产 patch 静态契约；`scripts/verify_mpv_native_assets.sh --require-elf` 额外要求队列/失败与 JNI NODE 标记。它们不代表真机重建、画质、性能或恢复页生命周期验收通过。

日志31续修未再改JNI，因此复用本单元已经编译和验证的两份`libplayer.so`，只需重编两份`libmpv.so`后重新打APK；不能为未改变的FFmpeg/libplacebo或JNI重复全链构建。

日志32续修切断FEL只读状态查询的native循环等待：`hwdec-current`通过wrapper缓存读取，包含真实探测可用状态和独立名称副本；不得调用decoder dispatch锁等待硬解端口。缓存先于async producer的帧发布更新，seek/reinit使旧状态失效；仅FEL的固定container FPS也不取decoder锁。修改型控制仍同步，普通模式不变。`WebHTV FEL status snapshot`输出查询次数、发布序号和可用状态，便于在不能ADB的电视上辨别新路径；host回归直接编译生产wrapper函数，以禁止decoder等待的stub验证读路径，并检查旧模式/重置型控制仍同步。此轮同样仅重编mpv，不改JNI、FFmpeg、libplacebo。

日志33确认上述状态快照已生效但连续播放仍失败，不能把它当作完整根因。新增`WebHTV FEL pipeline diagnostics v=1`：每个VO实例用always-lock-free原子值记录core/BL/EL/VO当前步骤和阶段起点，另外记录收发计数及VO请求帧预算；只在原fatal错误内格式化`WebHTV FEL pipeline:`，不逐帧输出、不同步查询native、不新增线程，不改变解码/渲染/超时策略。该消息复用App的fatal限流豁免，电视没有ADB也可从调试日志取证。host C测试直接编译生产header，覆盖禁用、时钟回绕、缓冲截断及并发；两ABI都必须通过lock-free编译断言和新标记校验。

日志35暴露VO丢帧接线缺口：核心持有未来帧等待第二帧，VO已空闲，但丢帧分支跳过了原本在`draw_frame`中的Surface暂存。FEL+MediaCodec+gpu-next现在通过内部`VOCTRL_PREPARE_FEL_FRAME`在丢弃显示时仍准备当前/未来帧，映射在VO锁外执行，复用既有GPU缓存，不做EL合成或呈现；暂时未就绪请求保留帧重绘，永久失败报告backend error。核心双帧时序、插帧设置及正常/非FEL路径不变。`WebHTV FEL dropped-frame staging`和fatal快照中的`surface-drops/drop-prepared/drop-retries`用于电视验证，fatal与pipeline放在同一物理行保证限流豁免。真实`render_frame()`/暂存函数的host有限缓冲测试证明旧代码停滞、新代码可归还输出；这仍不是电视持续播放验收。

日志36后曾用`dovi_split=bl_rpu`隔离EL；日志38证明全部6帧暂存归还后仍停止，不能把该版本称已修复。当前FEL候选只为Profile 7 FEL的MediaCodec BL使用已有`dovi_split=bl`，硬件输入不含RPU/EL，私有decoder context和packet移除DOVI/HEVC EL配置；共享demux/codec及独立软件EL/RPU不变。现有PTS配对函数从EL继承原始DV映射，GPU仍执行FEL重建。独立packet ref承接BSF所有权，支持EAGAIN重试/seek清理，错误失败关闭，旧模式不扫描码流；不增加公开符号、依赖或JNI变更，仅两ABI mpv增量重建。host测试涵盖真实BSF格式/所有权/输入载荷、软件EL逐帧RPU/PTS/NLQ和实际继承函数（第二参数传GIJoe Profile 7样片路径；需本机FFmpeg开发库和pkg-config）。`WebHTV FEL BL input isolation: pure-bl`、`RPU-source=EL`、配对RPU来源/缺失统计和fatal同行`BL-input={...}`供无ADB电视取证。此候选仍需电视实播，不能据host/编译通过宣称已稳定。

日志39/40否决pure-BL候选的可靠性与实时性能。生产者交接续修在独立BL worker发布硬件帧前，通过已有非阻塞VO暂存接口等待GPU完成且AImage归还；共享AVBuffer原子标记保证源归还先于发布及下一次decode，2ms轮询、750ms总界限，不新增App/core同步等待。缓存命中继续推进fence，FEL mapper保持raw-YUV存储以兼容稍后从EL继承RPU，不增加第二次像素拷贝或降位深。`WebHTV FEL producer handoff`和`WebHTV FEL decoder cost`记录交接及BL/EL耗时；非FEL不进入新增计时路径。`fel_producer_handoff_test.c`直接编译生产函数体验证有限输出进展与所有权顺序，仍不能替代电视验收。

日志41的751ms超时暴露“mapper已返回纹理但源未完成→确认后重入→ready快速路径不清请求槽”。VO先确认自己的请求，映射成功但源未归还时保留同槽重试。FEL stable若支持SYNC_FD导出，则用独立source-release semaphore随GPU copy提交，导出fd交`AImage_deleteAsync`，使CPU不必逐帧等待GPU；libplacebo的render-ready semaphore不被导出消费，原VkFence继续保护GPU input/output/command资源。原子状态区分安全交还源和GPU完成。无能力、信号量创建或fd导出失败时保留有界CPU等待；失败后禁止再次signal未消费的source semaphore。不改变默认模式、位深、EL/RPU、缓存上限和超时预算。

新`WebHTV FEL source release`、`map cost`、`async-returns`及producer的`gpu-complete`供无ADB电视取证。定向回归编译实际VO/submit/export/finish/producer函数，涵盖120帧CPU归还、120帧异步归还、延迟完成、fd=-1/失败/复用、独立两信号和准入隔离；它们不是电视画质/实时性能验收。

日志42三次首帧751ms失败否决上一候选：冷启动GPU资源/compute pipeline创建也被算进逐帧750ms。当前修正用同一共享lease的单调phase位标记真实冷初始化，仅此阶段单独有界10秒，普通排队/交接仍750ms，不由“第几帧”或轮询次数续期。BL待交接帧由wrapper持有，每次process检查一次后返回已有dispatch（2ms可中断等待），reset/stop取消自己的VO lease，generation防止旧回写。FEL失败只发送一次EOF且停止feed，避免零样本权重的信号队列空转。`WebHTV FEL GPU init`区分outputs/pipeline/总初始化耗时，`handoff timeout: phase=`与`wait=async`进入App调试日志。

日志29三次进入播放但稳态约10–12fps，不能认为此前交接修复完成实时性能需求。当前增量加入仅FEL/INFO启用的冷/热map分段墙钟、线程CPU、既有copy fence完成后的无WAIT GPU timestamp、既有libplacebo pass均值，以及render/flush/submit/swap和实际decoder线程信息；不支持/失败时只停用计时，不改变像素或生命周期。App纯性能记录按严格来源/级别识别并独立限流，只写一次现有调试日志存储，不重复排队主线程/漂亮Logcat，错误路径不变。

当前状态、实际资产 SHA-256、验证结果与回滚以 [P2-4-mpv-android-fel.md](../docs/P2-4-mpv-android-fel.md) 第9.14节为准。早先NODE可靠性单元替换过两 ABI 的 `libmpv.so` 和 `libplayer.so`；本轮以`1620bac1566727f4067eda631647a11652082e74`为基线，**仅重编/替换两份libmpv，其余18库（含JNI）逐字节不变**。host计时/所有权契约、23项Java定向测试、双ABI/ELF/导出、两个debug APK各10库/签名/封装结构通过。GPU区间可能含依赖等待，pass缓存不是整帧墙钟，线程数也不等于CPU利用率；目标电视画质、可靠性、性能与生命周期仍须新日志验证，不以构建通过标记任务完成。

## AV-DIAG-01 诊断窄补丁（2026-09-15）

本单元不改变`mpv-native-lock.json`版本及既有DV/字幕/音频/网络补丁，追加：

- `third_party/patches/ffmpeg-mediacodec-diagnostics.patch`：实际查名访问/拒绝/profile比较、查询失败stage、Java提前返回/NDK by-MIME与真实create/configure/start；SHA256 `bf3ad167b8811f661e2c47f33f6ec41e2887125727caa115f3553e814a1710e9`。
- `third_party/patches/mpv-playback-diagnostics.patch`：原AudioTrack内的配置/重建、原始write结果和单位、已有head/timestamp缓存、低频actual route；跨输出生命周期独立编号；SHA256 `0a62ef78d2cdce3a8e3d843319b364d644a70bda13291452640d86a12c2a136c`。INFO关闭时不做新增写入统计/时间采样。

当前锁定输入：framework `99a60ad2141d5ace94453590903c2c6b9a0a2443`、MPV `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`、FFmpeg `177f090e0503b7e013922ca903bde14b1c375f18`、libplacebo `b694a21bf2dc176c1e98b8a13c6421a0de5f3da5`，NDK29/API24。两ARM ABI从同一缓存补丁链增量构建，随后`bash scripts/build_mpv_native.sh --abi all --stage-only --install`；不reset已应用补丁的缓存，不重编未改变contract的`libplayer.so`。

`verify_mpv_native_assets.sh --require-elf`已通过，包含新增lookup/write marker和原命名空间/依赖门禁；arm64 libmpv SHA256 `8213150b467bc2dd9501bbd8e484ac8db04f537ebc8276133a845c18fda1b01a`，armv7 `2a0e1f749b8d57377da086a5c150db588b90cf65b656eddce36e6a3ba194709d`。完整实现、产物/验证日志、设备验收边界及原子回滚仅记录在[AV-DIAG-01](../docs/AV-DIAG-01-playback-diagnostics.md)的14.12–14.14；没有将构建通过记为设备实播通过。

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
