package com.fongmi.android.tv.ui.novel;

/**
 * 实验室：小说/漫画阅读器宿主。
 *
 * 由播放器 Activity（mobile VideoActivity 等）实现。阅读页切换章节时，
 * 通过 NovelRouter 调用 labPlayEpisode()，让播放器复用其完整的 playerContent
 * 解析链路（含 parse=1 二次解析 / 重试），解析出的 novel:// / pics:// 内容
 * 再经 NovelRouter.routeReaderEngine 回传给正在前台的阅读页。
 */
public interface NovelReaderHost {

    /**
     * 让播放器切换集数并执行解析（相当于点击了某集）。
     *
     * @param chapterUrl 章节 URL（episode.getUrl()）
     * @return 是否真的发出了解析请求。宿主只在「当前线路」的集数里按 URL 找章节，
     *         而阅读器的章节表是跨线路合并的，点到别条线路的章节时会找不到而静默返回；
     *         返回 false 让调用方立刻收尾，不必等在途标记靠时限过期。
     */
    boolean labPlayEpisode(String chapterUrl);
}
