# v5.6.0 — 播放器核心升级与稳定性优化

比较基线：`v5.5.6-202608072014`

## 可直接用于发布

本次更新重点升级播放器底层能力和播放稳定性，整合新版 MPV、FFmpeg 9、libplacebo 与 Media3 播放修复，并完善手机端“播放参数”入口。直播、HDR/Dolby Vision、Vulkan、字幕、拖动、播放结束态以及旧设备兼容性均有针对性优化。

### 新增与增强

- 播放器原生链升级至 MPV `0.41.0-940`、FongMi FFmpeg 9 和 libplacebo `7.375.0`，同步更新 MPV JNI 通信层。
- 增强 Android MediaCodec、OpenGL 与 Vulkan 播放路径，完善 AImageReader/AHardwareBuffer、HDR 和 Dolby Vision 输出兼容。
- Android Vulkan 硬解固定为实测负载更低的单一 AHardwareBuffer GPU 转换路径，移除未发布的 direct/stable/compute/fragment 后端选择与旧配置项。
- 播放参数面板增加 MPV 渲染链 GPU 负载百分比；本地文件不再显示网络下载速度，也不再给出网速不足结论。
- 完善 Dolby Vision 直出双 Surface 支持，视频直出时可继续显示字幕、OSD 与播放控制信息。
- 增强 MMT/TLV、TTML、ARIB 字幕、Audio Vivid/AV3A、HDR10+ 元数据以及蓝光/DVD 镜像播放兼容。
- 增强 MPV 网络播放的 Range、重试、短读和 HTTP/2 处理，改善远程媒体、直播及拖动体验。
- 恢复并完善手机端“播放参数”入口，普通控制栏、全屏控制栏和播放控制面板均可快速开关参数显示。
- “播放参数”按钮与“播放按钮设置”联动；隐藏后普通和全屏控制界面不再残留对应按钮或图标。
- 直播列表支持解析 `#EXTVLCOPT:http-cookie`，提升需要 Cookie 鉴权的直播源兼容性。

### 播放优化

- MPV 命令、属性修改和视频/OSD Surface 更新改为可追踪、串行化处理，减少快速切换线路、内核、横竖屏或退出播放时的竞态。
- 优化 MPV context 创建与异步销毁流程，降低重复初始化、退出卡顿和旧 context 干扰新播放的问题。
- 完善 MPV `END_FILE` 原因与错误信息上报，让播放结束、加载失败和解码失败能被上层更准确地区分。
- Media3 选择性合入 AV1/HEVC HDR 元数据、LL-HLS partial chunk、DASH 时间线、同媒体 seek、后台 MediaSession 和厂商 Surface 兼容修复。
- 更新浏览器 User-Agent，提高部分站点、解析接口和防旧浏览器策略的兼容性。
- DNS-over-HTTPS 改为首次使用时初始化，减少启动阶段无效网络开销。

### Bug 修复

- 修复播放自然结束后播放/暂停按钮、画中画状态和音频模式状态未及时恢复的问题。
- 修复拖动到视频末尾后又被自动拉起播放，以及电视端末尾拖动提示未正确收起的问题。
- 修复 Media3 在 scrub 到结尾、空 DASH `SegmentTimeline`、LL-HLS 分片恢复及同一媒体 seek 切换中的卡死或异常。
- 修复部分设备 AV1/HEVC 容器 HDR 元数据与码流内 T.35 元数据冲突导致的色彩或解码异常。
- 修复后台停止 MediaSession 时的潜在崩溃，以及部分厂商设备 detached Surface 能力声明不准确导致的播放问题。
- 修复无效 DoH 地址可能触发初始化异常的问题；无效地址会安全回退到系统 DNS。
- 修复部分旧电视固件调用 `moveTaskToBack` 时异常，后台音频和返回桌面增加兼容回退。
- 修复电视端配置、弹幕接口和 User-Agent 编辑弹窗中的二维码服务地址错误。

## 底层与构建变更

- App 版本更新为 `versionCode 560`、`versionName 5.6.0`。
- MPV/FFmpeg/libplacebo/JNI 使用 NDK r29 重新构建；IJK/DVD 继续使用各自锁定的 NDK r28c。
- Media3 本地 AAR 已按选择性补丁重新发布，保留 WebHTV 原有定制，未直接整体替换上游分支。
- 两套 ABI 的 MPV、FFmpeg、JNI 和 `libc++_shared.so` 按同一版本锁成套更新，避免混用不同版本的 native 库。
- TV、FFmpeg、mpv-android、media、mpv、libplacebo 与 CatVodSpider 的精确 revision 记录在 `third_party/fongmi-repositories-lock.json`。
