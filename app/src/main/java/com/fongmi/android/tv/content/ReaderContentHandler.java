package com.fongmi.android.tv.content;

import android.app.Activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.ui.novel.NovelRouter;

import java.util.List;

/**
 * 小说 / 漫画阅读内容处理器（对齐 {@link AudioContentHandler}）。
 *
 * 两条判定路径：
 * 1) 协议前缀：playerContent 返回 novel:// / pics:// / manga://，内容本身即阅读数据；
 * 2) 站点规则：站点命中小说源 / 漫画源配置（默认 [书][小说] / [画][漫画]），
 *    此时无论返回什么都按阅读内容处理，不再交给播放器。
 */
public class ReaderContentHandler implements ContentHandler {

    @Override
    public boolean canHandleSite(String key, String name) {
        return NovelRouter.isReaderSite(key);
    }

    @Override
    public boolean canHandleUrl(String url) {
        return NovelRouter.isReaderUrl(url);
    }

    @Override
    public boolean handleSite(Activity activity, String key, String id, String name, String pic, String mark) {
        return NovelRouter.openSite(activity, key, id, name, pic, mark);
    }

    @Override
    public boolean handleUrl(Activity activity, String url, String title) {
        return NovelRouter.openReaderUrl(activity, url, title);
    }

    @Override
    public boolean handleResult(Activity activity, String historyKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int position, Result result, long timeout) {
        return NovelRouter.handleResult(activity, historyKey, siteKey, flag, vodName, vodPic, episodes, position, result);
    }
}
