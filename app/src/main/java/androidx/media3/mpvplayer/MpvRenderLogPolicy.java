package androidx.media3.mpvplayer;

import java.util.Locale;

/**
 * 新的 AImageReader/Vulkan 出帧路径会输出若干瞬时警告和“已回退成功”提示，它们都不代表播放故障，
 * 但会命中 {@code MpvPlayer} 里几条很宽的失败关键词，把可自愈的情况报成致命错误。
 */
final class MpvRenderLogPolicy {

    private MpvRenderLogPolicy() {
    }

    /**
     * GPU/BufferQueue 侧的瞬时等待超时，native 自己会重试。{@code isNetworkFailureLog} 里裸的
     * "timed out" 会把它们当成网络故障，进而报出 ERROR_NETWORK_FAILED、抑制 HLS 格式重试，并让
     * 正常播放结束被判成网络失败。
     */
    static boolean isRenderPipelineTimeout(String line) {
        String lower = lower(line);
        return lower.contains("frame acquisition timed out") || lower.contains("fence timed out");
    }

    /**
     * 出帧后端初始化失败、但已成功回退到下一档（stable → newer → direct AHardwareBuffer）的提示。
     * 它带 "vo/" 前缀又含 "failed"，会命中 {@code isVideoOutputFailureLog} 并把 sawVideoOutputError
     * 锁到换片为止，此后任何错误都被报成 ERROR_VIDEO_OUTPUT_FAILED，反过来触发 forceMediaCodecCopy()
     * 逐帧回拷 —— 正是这批 native 修复要避开的路径。
     *
     * <p>只覆盖本次 native 升级新增的两条 conversion 提示；旧版就存在的 "acquire/release fence
     * failed" 仍按原有语义处理，改动它需要单独评估现有错误处理的依赖。
     */
    static boolean isRecoveredRenderFallback(String line) {
        return lower(line).contains("conversion initialization failed");
    }

    private static String lower(String line) {
        return line == null ? "" : line.toLowerCase(Locale.US);
    }
}
