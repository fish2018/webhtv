package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * 外挂字幕来源在 History 与播放器之间的搬运工。
 *
 * <p>三个宿主（mobile / leanback VideoActivity、TmdbDetailActivity）各自持有
 * 自己的 History，但记录和恢复的规则是同一套，放这里避免写三遍。
 */
public final class SubtitleRestoreCoordinator {

    private SubtitleRestoreCoordinator() {
    }

    /**
     * 记录用户刚选中的外挂字幕。
     *
     * @return true 表示 history 被改动，调用方需要落盘
     */
    public static boolean remember(History history, Sub sub) {
        if (history == null || Setting.isIncognito()) return false;
        SubtitleSource source = SubtitleSource.of(sub, history.getEpisodeUrl());
        if (source == null) return false;
        history.setSubtitleSourceObject(source);
        return true;
    }

    /**
     * 起播前把该恢复的字幕交给播放器。
     *
     * <p>必须在 {@code startPlayer()} 之前调用：注入点在 {@code setMediaItem} 之前，
     * 晚了就得靠 {@code setSub()} 重启一次，画面会闪。
     *
     * @param result 本次起播的解析结果，用来让自动匹配看见这条字幕
     * @return true 表示 history 里那条记录已失效并被清空，调用方需要落盘
     */
    public static boolean restore(History history, PlayerManager player, Result result) {
        if (player == null) return false;
        player.setPendingRestoreSub(null);
        if (history == null || Setting.isIncognito()) return false;
        SubtitleSource source = history.getSubtitleSourceObject();
        SubtitleRestorePolicy.Decision decision = SubtitleRestorePolicy.decide(
                source, history.getEpisodeUrl(), history.isCrossSourcePlayback());
        if (decision.restore()) {
            Sub sub = source.toSub();
            player.setPendingRestoreSub(sub);
            // 也写进 Result：SubtitlePlaybackSession 用 result.getSubs() 判断「已有字幕」
            // 来跳过自动匹配。不写的话自动匹配会照跑，下载完再 setSub() 一次，既重启
            // 一遍播放又把用户记住的选择顶掉。setSubs() 自带「已有字幕就不覆盖」语义，
            // 所以源站自带字幕时这里是空操作。
            if (result != null) {
                List<Sub> subs = new ArrayList<>();
                subs.add(sub);
                result.setSubs(subs);
            }
        }
        if (!decision.clear()) return false;
        // 失效记录必须写回，否则每次起播都白跑一次文件存在性检查。
        history.setSubtitleSource("");
        return true;
    }
}
