package com.fongmi.android.tv.setting;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackPerformanceCatalog {

    public static final String PROFILE = "profile";
    public static final String RENDER = "render";
    public static final String TRACK_LIMIT = "track_limit";
    public static final String ADAPTIVE_DOWNGRADE = "adaptive_downgrade";
    public static final String BANDWIDTH_METER = "bandwidth_meter";
    public static final String TUNNEL = "tunnel";
    public static final String BUFFER_TIME = "buffer_time";
    public static final String BUFFER_BYTES = "buffer_bytes";
    public static final String BACK_BUFFER = "back_buffer";
    public static final String PLAY_CACHE = "play_cache";
    public static final String LOAD_SELECTED_TRACKS = "load_selected_tracks";
    public static final String PRELOAD = "preload";
    public static final String PRELOAD_THREADS = "preload_threads";
    public static final String PRELOAD_SIZE = "preload_size";
    public static final String PRELOAD_TIME = "preload_time";
    public static final String PRELOAD_AHEAD = "preload_ahead";
    public static final String PRELOAD_PAUSE = "preload_pause";
    public static final String CODEC_ASYNC = "codec_async";
    public static final String DYNAMIC_SCHEDULING = "dynamic_scheduling";
    public static final String DURATION_PROGRESS = "duration_progress";
    public static final String LATE_DROP = "late_drop";
    public static final String SURFACE_FIXED_SIZE = "surface_fixed_size";
    public static final String DECODER_FALLBACK = "decoder_fallback";
    public static final String DV7_HDR10_FALLBACK = "dv7_hdr10_fallback";
    public static final String SOFT_VIDEO_TUNE = "soft_video_tune";
    public static final String AUDIO_PASSTHROUGH = "audio_passthrough";
    public static final String PREFER_AAC = "prefer_aac";
    public static final String AUDIO_SOFT_PREFER = "audio_soft_prefer";
    public static final String VIDEO_SOFT_PREFER = "video_soft_prefer";
    public static final String MPV_OUTPUT = "mpv_output";
    public static final String MPV_RENDER = "mpv_render";
    public static final String MPV_VULKAN_BACKEND = "mpv_vulkan_backend";
    public static final String MPV_HWDEC = "mpv_hwdec";
    public static final String MPV_SYNC = "mpv_sync";
    public static final String MPV_FRAME_DROP = "mpv_frame_drop";
    public static final String MPV_INTERPOLATION = "mpv_interpolation";
    public static final String MPV_SOFT_TUNE = "mpv_soft_tune";
    public static final String MPV_VERBOSE_LOG = "mpv_verbose_log";
    public static final String MPV_FRAME_RATE = "mpv_frame_rate";
    public static final String MPV_HLS_BITRATE = "mpv_hls_bitrate";
    public static final String MPV_REBUFFER = "mpv_rebuffer";
    public static final String MPV_OPTION_PRIORITY = "mpv_option_priority";
    public static final String IJK_SCENE = "ijk_scene";
    public static final String IJK_BUFFER = "ijk_buffer";
    public static final String IJK_PACKET_BUFFERING = "ijk_packet_buffering";
    public static final String IJK_WATER = "ijk_water";
    public static final String IJK_PICTURE_QUEUE = "ijk_picture_queue";
    public static final String IJK_FRAME_DROP = "ijk_frame_drop";
    public static final String IJK_ACCURATE_SEEK = "ijk_accurate_seek";
    public static final String IJK_PROBE = "ijk_probe";
    public static final String IJK_SOFT_TUNE = "ijk_soft_tune";
    public static final String IJK_RTSP_TRANSPORT = "ijk_rtsp_transport";
    public static final String IJK_RECONNECT = "ijk_reconnect";
    public static final String EXO_FRAME_RATE = "exo_frame_rate";
    public static final String EXO_START_BUFFER = "exo_start_buffer";
    public static final String EXO_REBUFFER = "exo_rebuffer";
    public static final String EXO_PRIORITIZE_TIME = "exo_prioritize_time";
    public static final String EXO_NETWORK_PROTECTION = "exo_network_protection";

    private static final String BASIC = "基础性能";
    private static final String BUFFER = "内存缓冲与磁盘缓存";
    private static final String PRELOAD_SECTION = "磁盘预载";
    private static final String DECODE = "解码与渲染";
    private static final String AUDIO = "音频";

    private PlaybackPerformanceCatalog() {
    }

    public static List<PlaybackPerformanceOption> forKernel(int kernel) {
        return forKernel(
                kernel,
                PlaybackPerformanceSetting.isRecommendedMerged());
    }

    static List<PlaybackPerformanceOption> forKernel(
            int kernel,
            boolean recommendedMerged) {
        List<PlaybackPerformanceOption> options = new ArrayList<>();
        options.add(option(
                PROFILE,
                BASIC,
                "性能配置",
                profileDescription(kernel, recommendedMerged)));
        if (kernel == PlayerSetting.EXO) addExo(options);
        else if (kernel == PlayerSetting.MPV) addMpv(options);
        else addIjk(options);
        return options;
    }

    private static void addExo(List<PlaybackPerformanceOption> options) {
        options.add(option(RENDER, BASIC, "渲染方式", "作用：决定视频输出控件。选择 SurfaceView（默认）通常最省 GPU、最适合电视和4K；只有需要动画、旋转或自由变换时才选 TextureView。代价：TextureView 会增加一次 GPU 合成，低性能电视更容易掉帧。"));
        options.add(option(TRACK_LIMIT, BASIC, "视频轨道限制", "作用：阻止 EXO 选择超过屏幕/硬解能力的轨道。想要“少卡顿”请选择开启（默认）；关闭只适合确认设备能稳定解码最高画质的情况。代价：开启可能主动放弃过高分辨率，换取播放成功率。"));
        options.add(option(ADAPTIVE_DOWNGRADE, BASIC, "自适应降级", "作用：重缓冲、连续掉帧或带宽不足时自动降到更容易播放的轨道。弱网、4K大文件建议开启（默认）；追求始终最高画质可关闭。代价：降级后本次播放不会自动升回，画质可能降低。"));
        options.add(option(BANDWIDTH_METER, BASIC, "带宽估算", "作用：用实际下载速度帮助 EXO 选轨和判断是否降级。网络忽快忽慢时建议开启（默认），可减少反复切换和卡顿；固定高速内网可关闭。代价：估算偏保守时可能提前选择低画质，它不会额外测速。"));
        options.add(option(TUNNEL, BASIC, "隧道模式", "作用：尝试让音视频走硬件直通，降低 CPU 并改善同步。电视硬解且只追求播放流畅时可尝试开启；出现黑屏、无声、字幕/LUT失效立即关闭。代价：依赖设备和 SurfaceView，兼容性不如普通路径。"));
        options.add(option(EXO_FRAME_RATE, BASIC, "帧率匹配", "作用：请求显示刷新率贴合视频帧率。默认“仅无缝”只接受系统无黑屏的切换；电影模式允许长片切换到精确刷新率；“分辨率+刷新率”还会匹配1080p/4K输出。遇到黑屏或刷新率异常选关闭。代价：强制模式可能黑屏1～2秒，系统也可能拒绝请求。"));
        addSharedBuffer(options, true, false);
        options.add(option(EXO_START_BUFFER, BUFFER, "起播阈值", "作用：开始播放前至少准备多少秒。自动档会按协议、分片边界、可信吞吐和缓冲趋势为每次起播锁定门槛；手动固定值可按弱网/4K用2～3秒，追求秒开用0.5～1秒。代价：阈值越高首帧越慢。"));
        options.add(option(EXO_REBUFFER, BUFFER, "重缓冲恢复", "作用：卡住后积累多少缓冲才恢复。自动档会按协议、分片边界、可信吞吐、time-to-empty和重缓冲历史为每次恢复锁定1～15秒门槛；历史只在相同网络、路径、协议和资源类型中复用，并会自动过期。轻量档固定3秒，在低内存设备上兼顾恢复稳定与容量上限。代价：数值越高越不易再次卡，但单次等待更久。"));
        options.add(option(EXO_PRIORITIZE_TIME, BUFFER, "时间优先", "作用：优先满足“缓冲秒数”，不因目标字节容量已达到就停止加载。网络波动或长视频建议开启；内存紧张设备保持关闭。代价：可能超过目标容量并暂时占用更多内存，不能突破系统可用内存。"));
        options.add(option(EXO_NETWORK_PROTECTION, BUFFER, "动态网络保护", "作用：EXO自动档默认开启，不区分单码率、多码率或资源证据是否完整。多码率HLS/DASH仍由Media3 ABR选择轨道，动态网络保护同时根据实际缓冲趋势、安全线、缓冲耗尽前剩余时间和可信吞吐兜底。优先使用0.97～1.00x轻量保护；只有持续缺口确实需要且可在缓冲耗尽前稳定时，才按计算目标进入0.85～0.97x，不会异常后直接跳到0.85x。恢复后自动回到1.00x；暂停、Seek、切资源或手动改速会立即退出。代价：介入期间片长会暂时延长，低于0.85x仍不可持续时仍可能重缓冲。"));
        options.add(option(LOAD_SELECTED_TRACKS, BUFFER, "只加载选中轨道", "作用：只请求当前音视频轨道，减少带宽和内存。网速/内存紧张建议开启；经常切换清晰度、音轨时可关闭以减少重新请求。代价：切换轨道可能需要重新缓冲。"));
        addPreload(options, false);
        options.add(option(CODEC_ASYNC, DECODE, "MediaCodec 队列", "作用：决定解码输出由异步还是同步队列驱动。保持自动（默认）通常吞吐最高；只有旧设备异步回调异常时才改同步。代价：同步可能更稳，但会增加等待和 CPU 调度压力。"));
        options.add(option(DYNAMIC_SCHEDULING, DECODE, "Media3 动态调度", "作用：按渲染器可工作时间调度播放循环。保持开启（默认）通常更省 CPU、掉帧更少；遇到特定机型时序异常再关闭。代价：关闭后可能增加无效唤醒。"));
        options.add(option(DURATION_PROGRESS, DECODE, "解码耗时推进", "作用：把异步解码耗时反馈给播放器，减少无效等待。异步队列下建议开启（默认）；同步队列不生效。代价很小，关闭只用于排查时序问题。"));
        options.add(option(LATE_DROP, DECODE, "输入丢帧阈值", "作用：输入帧明显迟到时提前丢弃，优先保证“跟上进度”。CPU不足、4K掉帧时建议开启；希望保留每一帧可关闭。代价：画面可能跳帧，但通常比持续延迟更容易接受。"));
        options.add(option(SURFACE_FIXED_SIZE, DECODE, "Surface 固定尺寸", "作用：按视频尺寸创建 Surface，减少超高分辨率合成压力。电视4K建议开启（默认）；切清晰度/旋转出现画面尺寸异常时关闭。代价：少数设备切换分辨率需要重建 Surface。"));
        options.add(option(DECODER_FALLBACK, DECODE, "解码器兜底", "作用：首选硬解初始化失败时尝试其他解码器。兼容性优先建议开启（默认）；只想快速暴露硬件问题可关闭。代价：可能多等待一次初始化，且备用解码器性能可能较低。"));
        options.add(option(DV7_HDR10_FALLBACK, DECODE, "DV7处理", "默认“升级P8.1”：设备不支持当前DV7硬解、但支持P8.1硬解时，使用libdovi mode 2实时改写RPU并丢弃增强层；原生DV7可硬解时保持原样。P8.1模式会锁定整次播放，不会自动降级HDR10；转换数据无效时会停止播放。选择“降级HDR10”会整次使用基底层，兼容性更高但失去Dolby Vision动态元数据。"));
        options.add(option(SOFT_VIDEO_TUNE, DECODE, "软解降负载", "作用：仅在 EXO 使用 FFmpeg 软解时降低滤波和解码负载。低性能设备/软解视频可开启；硬解4K基本不受影响。代价：积极降负载会牺牲细节，不能替代硬解。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "作用：把 Dolby/DTS 等压缩音频交给电视或功放解码，保留多声道。设备明确支持且要环绕声才开启；出现无声立即关闭。代价：输出链不支持时不会自动变成可播放音频。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "作用：有多条音轨时优先选兼容性更高的 AAC。电视无声、切换音轨失败时建议开启；追求原始多声道/高码率时关闭。代价：可能放弃质量更高的音轨。"));
        options.add(option(AUDIO_SOFT_PREFER, AUDIO, "音频软解优先", "作用：优先用 FFmpeg 解码冷门音频格式。硬解无声或格式不支持时开启；普通设备保持关闭。代价：增加 CPU、功耗，通常不影响视频画面流畅度。"));
        options.add(option(VIDEO_SOFT_PREFER, AUDIO, "视频软解优先", "作用：绕过异常硬件解码器，改用 FFmpeg。仅在硬解花屏/崩溃且分辨率较低时尝试；4K电视不要开启。代价：CPU、发热和掉帧风险显著增加。"));
    }

    private static void addMpv(List<PlaybackPerformanceOption> options) {
        options.add(option(MPV_OUTPUT, BASIC, "输出模式", "怎么选：保持“自动”（默认）最省心；电视播放4K且不需要MPV字幕/LUT/shader/滤镜时会在确认设备支持当前视频后自动用“电视直出”，这是当前最低GPU开销、最优先保证流畅的路径。需要MPV完整图像处理选“GPU渲染”；自动判断不正确时可手动选“电视直出”。代价：电视直出不经过OpenGL/Vulkan，MPV原生字幕和GPU滤镜不可用。"));
        options.add(option(MPV_RENDER, BASIC, "渲染后端", "怎么选：GPU渲染模式先选 OpenGL，兼容性最好；确认设备 Vulkan 驱动稳定且需要 gpu-next/libplacebo 时再选 Vulkan。电视直出时本参数不参与视频输出，切换也不会变快。代价：Vulkan可能更高效，也可能因驱动问题卡顿、黑屏并自动回退OpenGL。"));
        options.add(option(MPV_VULKAN_BACKEND, BASIC, "Vulkan 视频路径", "怎么选：仅在渲染后端为 Vulkan 且使用 MediaCodec 硬解时生效，默认 direct。direct 直接采样 AHardwareBuffer，转换步骤少；如果设备出现 GPU 负载逐渐升高、卡顿或驱动兼容问题，可改用 legacy。legacy 使用旧版三输出 compute conversion；stable 使用四输出有界 fence 池，适合 direct 不兼容且 legacy 存在同步问题时尝试。选择“播放性能优先”时本选项覆盖 mpv.conf；选择“mpv.conf优先”时，android-vulkan-aimagereader-backend 等同名配置覆盖本选项。切换后需要重新进入播放。"));
        options.add(option(MPV_HWDEC, BASIC, "硬解路径", "怎么选：保持“自动回退”（默认）；它先试 mediacodec 零拷贝，失败再试兼容复制。电视4K追求最高流畅可选“零拷贝优先”；只有零拷贝黑屏、崩溃或解码异常时选“兼容复制”。代价：兼容复制会复制每帧，4K 10bit内存带宽开销大，可能明显卡顿。"));
        options.add(option(MPV_FRAME_RATE, BASIC, "帧率匹配", "怎么选：电影、剧集在电视上保持“仅无缝”（默认），可减少24/25fps抖动；切换后黑屏、闪屏或电视刷新率异常时关闭。代价：仅无缝不会强制切换不兼容模式，旧Android自动忽略。"));
        options.add(option(MPV_OPTION_PRIORITY, BASIC, "参数优先级", "怎么选：普通用户选“播放性能优先”（默认），界面中的缓存、硬解、同步、丢帧和HLS设置才能可靠生效；只有明确维护了mpv.conf并希望同名配置覆盖界面时选“mpv.conf优先”。选错会出现“界面改了但实际被配置文件覆盖”。"));
        addSharedBuffer(options, false, true);
        options.add(option(MPV_REBUFFER, BUFFER, "重缓冲恢复", "作用：缓存耗尽后至少重新准备多少秒再继续。自动档默认2秒，轻量档使用3秒；网络反复卡顿可升到5秒，稳定高速网络可用1秒。代价：越高越不易刚恢复又卡住，但每次恢复等待越久。"));
        options.add(option(MPV_HLS_BITRATE, BUFFER, "HLS码率控制", "怎么选：自动档会用同网络、同真实路径且5分钟内的可信长期吞吐选择起播上限；没有可信历史时先限制到15Mbps。持续吞吐不足、低缓冲并伴随underrun或重缓冲时，最多逐档重载降3次，不会自动升档。手动档仍可固定最高、15Mbps、8Mbps或最低。代价：降档重载会短暂中断，VOD尽量保留位置，直播回默认live edge；清单码率标错时判断仍会失准。"));
        addPreload(options, true);
        options.add(option(MPV_SYNC, DECODE, "同步模式", "怎么选：保持“音频同步”（默认），兼容性最好。只有屏幕刷新率与视频不匹配、能感到规律性微抖且未开启音频直通时，才试“显示重采样”。代价：显示重采样会轻微调整音频速度并增加处理，直通音频不适用。"));
        options.add(option(MPV_FRAME_DROP, DECODE, "丢帧策略", "怎么选：保持“输出丢帧”（默认），跟不上时优先丢渲染帧以维持音画进度；卡顿仍严重可试“解码丢帧”；不要为追求完整画面关闭丢帧，除非设备性能充足。代价：策略越积极，跳帧越明显。"));
        options.add(option(MPV_INTERPOLATION, DECODE, "平滑运动", "怎么选：默认关闭。只有GPU余量充足、使用GPU渲染＋显示重采样且想改善低帧率运动时才开启；电视4K、HDR、LUT或已经卡顿时必须关闭。代价：会明显增加GPU负载，电视直出时不生效。"));
        options.add(option(MPV_SOFT_TUNE, DECODE, "软解降负载", "作用：仅软件解码时减少滤波和解码工作。默认“温和”；软解仍掉帧可选“积极”；硬解视频无需靠它提速。代价：模式越积极，细节和画面连续性损失越大。"));
        options.add(option(MPV_VERBOSE_LOG, DECODE, "详细日志", "怎么选：正常播放保持“正常”（默认）；只在排查崩溃、解码或缓冲问题时临时打开详细日志。代价：增加JNI、字符串处理和日志I/O，可能干扰低性能设备的流畅度。"));
        options.add(option(DV7_HDR10_FALLBACK, DECODE, "DV7处理", "作用：设备能原生播放 Dolby Vision Profile 7 时始终保留原始 DV7；否则默认尝试“升级P8.1”保留动态元数据，也可选择直接“降级HDR10”。设备不支持P8.1或转换/解码失败时会自动回退HDR10。P8.1逐帧重写RPU，会比HDR10过滤增加少量CPU处理。"));
        options.add(option(AUDIO_PASSTHROUGH, AUDIO, "音频直通", "怎么选：电视/功放明确支持Dolby、DTS且需要多声道时开启；出现无声、杂音或同步异常立即关闭。代价：压缩音频交给外部设备后，MPV无法完成所有混音和重采样处理。"));
        options.add(option(PREFER_AAC, AUDIO, "AAC 优先", "怎么选：高级音轨无声或设备兼容性差时开启；功放支持原始多声道、希望保留最佳音轨时关闭。代价：可能从Dolby/DTS切到质量或声道较低的AAC。"));
    }

    private static void addIjk(List<PlaybackPerformanceOption> options) {
        options.add(option(IJK_SCENE, BASIC, "场景模式", "怎么选：不确定就选“自动”（默认）；普通影视选“点播”；直播经常缓冲选“直播稳定”；只有网络很好且必须追求低延迟时选“直播低延迟”。代价：稳定模式延迟更高，低延迟模式更容易卡顿。"));
        options.add(option(IJK_BUFFER, BUFFER, "读包内存上限", "作用：限制IJK native前向读包队列占用的内存，不是磁盘缓存。自动档按码率、场景和内存压力使用4～15MB；自定义可选64/128/256MB，播放或暂停时都会继续读到该上限，容量越大越抗网络抖动，但会增加native内存占用。"));
        options.add(option(IJK_PACKET_BUFFERING, BUFFER, "数据包队列", "作用：决定数据不足时是否等待内存中的数据包队列恢复。点播和稳定直播保持开启；只为降低直播延迟才关闭。代价：开启会增加延迟，关闭在网络抖动时更容易卡顿或花屏。"));
        options.add(option(IJK_WATER, BUFFER, "起播与恢复水位", "作用：控制IJK内存队列达到多少数据后开始或恢复播放，不代表磁盘缓存长度。自动档会按点播、直播、低延迟和分片时长在0.1～5秒内调整；手动档网络抖动可选稳定，低延迟直播才选低。"));
        options.add(option(IJK_PICTURE_QUEUE, BUFFER, "画面队列", "自动档固定3帧，避免高分辨率盲目扩大 native/图形内存；手动档可选3/5/8帧，渲染偶发抖动可尝试5帧。代价：队列越大，内存和直播延迟越高。"));
        options.add(option(PLAY_CACHE, BUFFER, "HLS 磁盘缓存上限", "作用：限制IJK经HLS代理写入磁盘的数据量。频繁回看或拖动可增大；它与读包内存上限完全独立，不会直接扩大IJK的内存缓冲。"));
        addPreload(options, true);
        options.add(option(IJK_FRAME_DROP, DECODE, "丢帧策略", "怎么选：普通播放选“标准”（默认）；低性能设备持续落后时选“积极”；设备性能充足且必须保留每帧才关闭。代价：越积极越能追上进度，但画面跳帧越明显。"));
        options.add(option(IJK_SOFT_TUNE, DECODE, "软解降负载", "自动档仅在确认实际软解、持续FPS压力和热状态后从关闭分级到温和/积极；手动档可固定选择。代价：越积极越省CPU，但细节和连续性损失越大，参数变化需要重建。"));
        options.add(option(IJK_ACCURATE_SEEK, DECODE, "精确Seek", "怎么选：默认关闭，拖动可更快恢复；只有必须准确落在目标时间点时开启。代价：需要从关键帧继续解码，拖动等待和CPU占用都会增加，不会改善正常播放流畅度。"));
        options.add(option(IJK_PROBE, DECODE, "流探测", "怎么选：普通资源保持“系统默认”；起播太慢可试“快速”；漏音轨、格式识别失败或直播信息不全时选“完整”。代价：快速可能误判，完整会延长起播。"));
        options.add(option(IJK_RTSP_TRANSPORT, DECODE, "RTSP传输", "怎么选：优先TCP（默认），公网和Wi-Fi更稳定；局域网质量很好且必须低延迟时选UDP；不确定可选自动。代价：TCP延迟略高，UDP丢包时会花屏或卡顿。"));
        options.add(option(IJK_RECONNECT, DECODE, "断线重连", "怎么选：直播和不稳定网络保持开启（默认），短暂断线可自动恢复；需要失败立即返回时关闭。代价：无效地址或服务器故障时，开启会延长最终报错时间。"));
    }

    private static void addSharedBuffer(List<PlaybackPerformanceOption> options, boolean exo, boolean playCache) {
        options.add(option(BUFFER_TIME, BUFFER, "前向缓冲目标", exo
                ? "作用：控制EXO当前播放队列希望保留的前向时长。自动档网络资源约30～60秒，本地资源约1～15秒；手动档直接显示实际最低～最高秒数。它使用内存，不代表磁盘已经缓存到该位置。"
                : "作用：控制MPV cache-secs的实际目标时长，界面直接显示秒数，不再使用1～10档位。数值越高越抗短时网络波动，但会增加内存、预读流量和恢复等待；它不代表磁盘预载长度。"));
        options.add(option(BUFFER_BYTES, BUFFER, "内存缓冲上限", exo
                ? "作用：限制EXO播放队列可占用的内存，不是磁盘缓存。自动档按媒体需求和设备内存动态使用16～192MB；手动64/128/256MB都是上限，并不表示会立即占满。"
                : "作用：限制MPV前向demuxer缓存使用的内存，不是HLS 磁盘缓存。自动档按媒体需求和内存压力在24～192MB间调整；手动值是上限，不能提升真实网速。"));
        options.add(option(BACK_BUFFER, BUFFER, "已播放数据保留", exo
                ? "作用：在内存中保留已经播放的数据，便于短距离向后拖动。EXO按15/30/60秒设置；它不会增加前向缓冲，也不会改善网络卡顿。"
                : "作用：在MPV内存中保留已经播放的数据，便于向后拖动。MPV实际按字节控制，自动档为0～64MB，手动档显示少量/中等/与前向内存相同，不再用并不准确的秒数表示。"));
        if (playCache) options.add(option(PLAY_CACHE, BUFFER, "HLS 磁盘缓存上限", "作用：限制HLS代理在磁盘上保留的数据量，与MPV前向内存缓冲完全独立。普通播放保持128MB；频繁回看可选256～512MB；1～2GB只适合存储充足且长时间播放HLS。"));
    }

    private static void addPreload(List<PlaybackPerformanceOption> options, boolean hlsOnly) {
        String scope = hlsOnly
                ? "当前主动向前预载仅完整支持HLS点播；普通MP4/MKV直链和DASH仍由播放器内核或前台缓存处理。"
                : "适用于可缓存的HTTP/HTTPS点播；直播和不可缓存资源会跳过。";
        options.add(option(PRELOAD, PRELOAD_SECTION, "磁盘预载", "作用：在播放内存缓冲之外，提前把后续数据写入磁盘。" + scope + "计费网络、省电、过热、内存/存储压力或前台缓冲风险出现时会自动暂停。"));
        options.add(option(PRELOAD_THREADS, PRELOAD_SECTION, "预载并发", "作用：控制同时执行的后台磁盘预载任务数，不是播放器解码线程。自动档按前台缓冲、吞吐和系统状态使用0～2条；手动通常1条最稳，过多可能挤占当前播放或触发服务器限流。"));
        options.add(option(PRELOAD_SIZE, PRELOAD_SECTION, "磁盘预载配额", "作用：限制当前内核最多使用多少磁盘空间保存预载数据，范围128MB～32GB。它与内存缓冲上限完全独立；配额越大只代表允许保存更多，不代表会立即占满。"));
        options.add(option(PRELOAD_TIME, PRELOAD_SECTION, "单次预载时长", "作用：限制每个后台任务一次向前准备多少媒体时长。自动档通常10～30秒；数值越大，单次连接和写盘持续越久，但不会改变总磁盘配额或向前目标。"));
        options.add(option(PRELOAD_AHEAD, PRELOAD_SECTION, "向前预载目标", "作用：指定希望从当前播放位置向前保留多少可连续播放的数据，可选1～60分钟或整部影片。达到高水位后停止，消耗到低水位再补充，并始终受磁盘配额和系统保护限制。"));
        options.add(option(PRELOAD_PAUSE, PRELOAD_SECTION, "暂停时继续预载", "作用：决定暂停后是否继续向前准备数据。默认“始终”；担心移动流量可选“仅 WiFi”。HLS会继续填充磁盘预载，MPV普通直链会临时扩大内存预读时长；存储、内存、过热或资源压力仍会限制实际长度。"));
    }

    private static String profileDescription(
            int kernel,
            boolean recommendedMerged) {
        return switch (kernel) {
            case PlayerSetting.MPV -> "首选“自动”：电视4K硬解且不需要MPV字幕/LUT/shader/滤镜时自动使用低开销电视直出，并按可信吞吐和运行状态控制缓存、预载与HLS码率。“轻量”面向低端或问题设备，保留自动输出和硬解回退，关闭帧率切换、预载和回退缓存，限制HLS至8Mbps并使用64MB前向缓存；优先保证连续播放，最高画质和回看速度可能下降。自动档内手动修改的项目会单独固定，其他项目继续自动；重新选择“自动”可清除全部覆盖。";
            case PlayerSetting.IJK -> "首选“自动”：按协议、内存和运行反馈在4/8/15MB有限队列中有界调整。“轻量”固定8MB、稳定水位、3帧画面队列、标准丢帧和温和软解降负载，并关闭预载；它比旧4MB激进轻量档更能抵抗网络抖动，同时比旧兼容档的15MB和5帧更省内存。自动档内手动修改的项目会单独固定，其他项目继续自动；重新选择“自动”可清除全部覆盖。";
            default -> "首选“自动”（也是默认）：根据协议、分片、可信吞吐、缓冲趋势和内存状态动态控制加载、预载、起播与重缓冲门槛。“轻量”面向低端或问题设备，使用SurfaceView、64MB容量上限、15～30秒缓冲、1.5秒起播和3秒恢复，关闭预载、回退缓存及帧率切换，同时保留解码器兜底、轨道限制和带宽估算。自动档内手动修改的项目会单独固定，其他项目继续自动；重新选择“自动”可清除全部覆盖。";
        };
    }

    private static PlaybackPerformanceOption option(String id, String section, String title, String description) {
        return new PlaybackPerformanceOption(id, section, title, description);
    }
}
