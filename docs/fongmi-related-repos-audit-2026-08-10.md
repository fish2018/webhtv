# FongMi 上游增量审计（2026-08-10）

审计时间：2026-08-10（Asia/Shanghai）

本报告承接 `fongmi-related-repos-audit-2026-08-09.md`。前一轮已经逐项检查 2026-07-09 至 2026-08-09 的 394 个提交；本轮重新读取各仓库远端分支和提交图，重点处理 2026-08-10 的强推、重落基和新增提交。所有当前精确 revision 同时写入 `third_party/fongmi-repositories-lock.json`。

> 当前集成状态（2026-08-12）：下文提交映射仍是准确的历史审计记录，但 Android Vulkan AImageReader 运行实现已经在后续实测后收敛为 `v5.5.6-202608072014` 单一路径，direct/compute/fragment 后端选择不再进入最终 native assets。

## 结论

- `FongMi/TV@fongmi` 仍为 `1a19fee278fa2234da725d61a53bf59b69fe9127`，没有新的 App 提交可 cherry-pick。
- 该源码 HEAD 的 `app/build.gradle` 明确为 `versionCode 560`、`versionName 5.6.0`，WebHTV 按此开源基线同步版本。
- FFmpeg 9、Media3 release、libplacebo fongmi 和 CatVodSpider main 均未变化，不重编 Media3，也不更新 CatVod JAR 边界。
- `FongMi/mpv@fongmi` 从 `a1bb3e18...` 重落基为 `cca559b4...`。19 个 FongMi Android/播放补丁 patch-id 全部等价，只新增一个 Wayland 首帧 configure 修复；Android 编译代码没有功能差异，但 git describe 变为 `0.41.0-940-gcca559b41`，因此必须更新 lock 并重新生成两套 `libmpv.so` 资产。
- `FongMi/mpv-android@fongmi` 从 `69cd182f...` 重落基为 `99a60ad2...`。18 个 FongMi native/JNI 提交均保留；其中 JNI hardening 在新父线上调整了上下文，但旧头与新头最终 `app/src/main/jni` 树完全相同。新增的五个上游提交中，只有 JNI global reference / `GetJavaVM` 修复与 native 有关，而该行为已经包含在 WebHTV 当前拆分后的 JNI 源码中；其它四个只影响 mpv-android 示例 App UI。
- 本轮不修改 WebHTV 的 Media3 AAR、FFmpeg 9、libplacebo 或 CatVodSpider 运行时边界；更新 MPV/mpv-android 精确 revision、App 版本、native 资产和构建文档。

## 实际构建与打包验证

- 使用 NDK `29.0.14206865` / API 24，按新 lock 完整重编并安装 `arm64-v8a`、`armeabi-v7a` 两套 MPV、FFmpeg、libplacebo、curl/nghttp2、字幕、字体、光盘和归档依赖。
- 使用相同 native prefix 重编两套 `libplayer.so`；`scripts/verify_mpv_native_assets.sh --require-elf` 已通过版本字符串、能力标记、ABI、`SONAME` 与 `DT_NEEDED` 校验。
- `:app:testMobileArm64_v8aDebugUnitTest` 通过。
- `:app:assembleMobileArm64_v8aRelease :app:assembleLeanbackArmeabi_v7aRelease -PfastRelease=true` 通过；两个 APK 均为 `560 / 5.6.0`，签名和 zip alignment 校验通过，且 APK 内 MPV/JNI 资产哈希与仓库文件一致。
- 本轮完成源码同步、可复现 native 构建和 APK 静态验证；OpenGL、Vulkan、硬解/软解、线路切换和退出等真机播放回归仍需在可操作设备上单独执行。

## 当前远端头

| 仓库 | 使用分支 / commit | 默认分支 / commit | 本轮处理 |
| --- | --- | --- | --- |
| TV | `fongmi@1a19fee278fa`（App `560 / 5.6.0`） | 同左 | 无新增提交；版本严格跟随公开源码 |
| FFmpeg | `release-9.0-fongmi@04482c8d13ac` | `master@f947bc9c1532` | 使用分支不变 |
| mpv-android | `fongmi@99a60ad2141d` | `master@4c57302c655b` | 更新 lock，重编 native/JNI |
| media | `release@2bc207851df3` | 同左 | 无新增提交 |
| mpv | `fongmi@cca559b41ceb` | `master@1d15686142fd` | 更新 lock，重编 native |
| libplacebo | `fongmi@b694a21bf2dc` | `master@4d82c6898551` | 不变 |
| CatVodSpider | `main@a511a606a287` | 同左 | 不变，不内置 JAR |

## mpv 新提交

真正新增的上游提交只有一项：

| Commit | 内容 | 对 WebHTV 的影响 |
| --- | --- | --- |
| `513d3407d4e1e95ebb743c8e9c139b39d9880cc2` | Wayland 在第一次 surface configure 前不 attach buffer | Android 构建不编译 Wayland 输出，功能无直接影响；它改变祖先计数和 MPV 版本字符串 |

FongMi 的 19 个提交在新父线上全部保持 patch-id 等价：

```text
ee306d0b5ad8 -> c5082d1f8c46  stream: support Android helper schemes
0457a15d7dc3 -> 6b6d0d4a3d5e  stream: support Blu-ray and DVD ISO input
5bd3f5229d70 -> cb7c55eb9b23  stream: support rewinding linear streams
b30dba4b023b -> f7732a8e9b9e  stream_curl: retry without range on bad encoding
62af7f4269fd -> 09669e9092ce  stream_curl: synchronize worker initialization
dc9700344300 -> a582cfeaf733  player: expose attached album art data
3f2ac2a23c61 -> a82e8016de52  player: expose live media status
da9077d4fab8 -> dda48ef9ca35  demux: support MMT/TLV streams
1eee380edd94 -> 9cdc2e4933a0  sub: transform positioned TTML subtitle layout
3049cbc4e52b -> 416d4a0fae82  ao_audiotrack: preserve passthrough sample rate
7a75d6773a11 -> f783e2ba8dbc  af_scaletempo2: support 0.1x playback
ec74de4b835a -> 3f900de4e3e7  video/decode: stabilize decoder reinitialization
f2e6a68656c7 -> 13d430dcec33  video/out: negotiate Android HDR output metadata
c768ddeb5c91 -> 62648ab1789c  video/out/android: improve surface, HDR, and Dolby Vision output
3859551cec48 -> cb007d6f6b52  hwdec/aimagereader: add Android Vulkan interop
fec2225c7bec -> 3b48aae3bd06  hwdec/aimagereader: expose Vulkan backend selection
ce941cf94af2 -> 878a493dd4fe  stats.lua: preserve ASS formatting while switching VO
869c79f6433c -> f3083287e291  video/out/vulkan/android: prefer compositable swapchains
a1bb3e18efd8 -> cca559b41ceb  hwdec/aimagereader: map Dolby Vision profile 5 on the GPU
```

旧头与新头的源码树差异只有 `video/out/wayland_common.c` 和 `video/out/wayland_common.h`。WebHTV 原有 `mpv-stream-cb-disc-controls.patch` 与 `mpv-matroska-segment-end.patch` 仍需在新头上应用。

## mpv-android 新提交

新增的五个上游提交：

| Commit | 内容 | 处理 |
| --- | --- | --- |
| `9a4f45b58cc4` | `BaseMPVView` 不长期持有 Activity Context | WebHTV 不使用该 View，无 App 代码可移植 |
| `b5fd5c8d6942` | JNI 释放旧 global app context，并检查 `GetJavaVM` | WebHTV 当前 `third_party/mpv-player-jni/src/main.cpp` 已有更完整的等价检查和引用清理 |
| `cafc790ac757` | 示例 App 记忆屏幕亮度 | WebHTV 有自己的亮度/手势体系，不移植 |
| `40fbe0350690` | 配置编辑弹窗防止误丢未保存内容 | 只影响 mpv-android 示例 App，不移植 |
| `f0a3cebb6b16` | 波兰语翻译 | 示例 App 资源，不移植 |

FongMi 的 18 个提交重落基映射：

```text
762fce9af837 -> 63614b566aec  ci: configure FongMi native builds
0fff7d98e59c -> 7ba95b10ab78  buildscripts: add Android CMake dependency tooling
c05440f15ea4 -> fad982bd126a  buildscripts: build Vulkan support with NDK shaderc
b846e2cebcca -> aeef47e60602  buildscripts: enable libbluray
f65282749ea3 -> 34b2794e7d1a  buildscripts: enable iconv and uchardet
38e151e094d7 -> 7e201b18b210  buildscripts: enable libarchive
b0efeaa746f4 -> 34f917a0ef2a  buildscripts: enable dvdnav
a7175c614967 -> b043e3e722fa  buildscripts: enable rubberband
c9a1d37af673 -> 774ce02333db  buildscripts: enable libarcdav3a
57aa7a8c871d -> 5372e9f414b1  buildscripts: enable audio filters and passthrough
442da8d6fe26 -> e536f47e7606  buildscripts: enable libaribcaption
697ce2418fa1 -> a79e7af6c22f  buildscripts: avoid blocking entropy source on Android
6d2019d7caf0 -> 317c454387cc  jni: harden native lifecycle and Java interop
591228b6c26f -> 860de20006ec  jni: expose byte array properties
888919a6981a -> 08d39a01b56a  jni: expose end-file event details
ef9e01f3f306 -> 5e320439b7af  jni: serialize asynchronous commands and surface updates
641bccbb2e86 -> cb677149bcca  buildscripts: add Lua download fallback
69cd182ff0af -> 99a60ad2141d  buildscripts: update FFmpeg to 9.0
```

`317c454...` 因吸收上游 `b5fd5c8...` 而在 range-diff 中显示为调整，不是功能丢失。比较两个最终头后，`app/src/main/jni/**` 没有任何树差异；差异只在 mpv-android 示例 App 的 Kotlin、布局、字符串和偏好页面。

## 不合并项与重编边界

- TV 无新增提交，因此本轮没有主库 cherry-pick。
- Media3 upstream 头不变，继续使用 `third_party/media-lock.json` 中的本地 fork 和选择性补丁，不重新发布 AAR。
- FFmpeg 9 与 libplacebo 头不变，但它们仍是 `libmpv.so` 的同一 lock 输入；重建 MPV 时必须按两套 ABI 成套校验，不能只复制单个 `libmpv.so`。
- JNI API 和最终源码没有变化，理论上旧 `libplayer.so` ABI 兼容；为让新 lock 的发布资产完整可复现，本轮仍随两套 MPV prefix 重编并校验 `libplayer.so`。
- CatVodSpider 仍只作为外部 Spider JAR 源码审查，不把 `custom_spider.jar` 放入 App。
