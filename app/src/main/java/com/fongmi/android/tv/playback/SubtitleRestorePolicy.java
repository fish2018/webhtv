package com.fongmi.android.tv.playback;

import java.io.File;
import java.util.function.Predicate;

/**
 * 决定一条持久化的外挂字幕来源能否在本次起播时挂回去。
 *
 * <p>抽成不依赖 Android 的纯判定，是因为这里的每个分支都对应一种「挂错字幕」的
 * 用户可见故障：换集挂上一集的 srt 会让时间轴完全错位，缓存被清后挂死路径会
 * 每次起播白跑一次文件检查。判定必须可单测。
 */
public final class SubtitleRestorePolicy {

    /**
     * 判定结果：是否注入、是否该忘掉这条记录。
     *
     * <p>{@code clear} 只对「记录本身已经死了」成立，也就是文件没了。不该注入的其他
     * 情形（换集、跨源）记录仍然有效，只是这一次不适用——清掉等于替用户丢掉他下次
     * 回到那一集时想要的字幕。
     */
    public record Decision(boolean restore, boolean clear, String reason) {

        static Decision inject() {
            return new Decision(true, false, "restore");
        }

        static Decision drop(String reason) {
            return new Decision(false, true, reason);
        }

        static Decision skip(String reason) {
            return new Decision(false, false, reason);
        }
    }

    private SubtitleRestorePolicy() {
    }

    public static Decision decide(SubtitleSource source, String episodeUrl, boolean crossSource) {
        return decide(source, episodeUrl, crossSource, file -> new File(file).isFile());
    }

    /**
     * @param episodeUrl  本次要播的集地址
     * @param crossSource 跨源续播。目标源的集数编排可能不同，旧字幕不能直接用，
     *                    但原源的偏好要留着——所以不注入也不清空。
     * @param exists      文件存在性判定，单测注入替身
     */
    static Decision decide(SubtitleSource source, String episodeUrl, boolean crossSource,
                           Predicate<String> exists) {
        if (source == null || !source.isUsable()) return Decision.skip("absent");
        if (crossSource) return Decision.skip("cross-source");
        // 记录时没有集地址的旧数据永远无法判断是不是同一集，属于死数据，清掉。
        if (source.getEpisodeUrl().isEmpty()) return Decision.drop("episode-unknown");
        // 换集只是「这一次不适用」，记录本身没死。清掉的话用户切走再切回来就没字幕了，
        // 而宿主在起播前就把 episodeUrl 改成了新集（mobile VideoActivity.java:5452 等），
        // 所以每次换集都会走到这里——清空等于换一集就把偏好丢一次。
        if (!source.getEpisodeUrl().equals(episodeUrl == null ? "" : episodeUrl)) {
            return Decision.skip("episode-changed");
        }
        // 远端字幕不做预检：网络探测会拖慢起播，而字幕加载失败对播放是非致命的。
        if (source.isRemote()) return Decision.inject();
        return exists.test(source.getUrl()) ? Decision.inject() : Decision.drop("file-missing");
    }
}
