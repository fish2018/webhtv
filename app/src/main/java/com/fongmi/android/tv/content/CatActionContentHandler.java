package com.fongmi.android.tv.content;

import android.app.Activity;

import com.fongmi.android.tv.api.CatAction;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;

import java.util.List;

/**
 * 猫源「动作项」的站点级分流（对齐 {@link AudioContentHandler} / {@link ReaderContentHandler}）。
 *
 * <p>猫源配置站点把设置入口伪装成点播条目。原先的处理是先照常打开详情页，等 bundle 通过
 * {@code /msg} 请求开网页后再让详情页退场——播放页一定会被创建并闪一下，用户看到的就是
 * 「先进播放页，再跳到配置页」。
 *
 * <p>这里把判定提到点击那一刻：{@code dispatchSite} 在 {@code startActivity} 之前跑，
 * 认出动作项就直接开网页，详情页压根不创建。配置站点里的动作项都走这条路——「通用配置」、
 * 「弹幕服务」以及以后 bundle 再加的，判定不依赖动作名，见 {@link CatAction#isWebsiteAction}。
 *
 * <p>{@link CatAction} 那套「结果为空则退场」的事后判定<b>保留不动</b>——它覆盖本 handler
 * 覆盖不到的入口（历史记录、搜索结果、推送）。
 */
public class CatActionContentHandler implements ContentHandler {

    /**
     * 真正的判定还要看 {@code id} 和 {@code pic}，而这个回调只给 key 和 name。
     *
     * <p>{@code ContentDispatcher} 的契约是 {@code canHandleSite} 通过后才调
     * {@code handleSite}，由后者返回 false 表示「不归我管」——所以这里只做站点级粗筛，
     * 精确判定留给 {@link #handleSite}。粗筛能挡掉绝大多数点击：非猫源站点根本不会进来。
     */
    @Override
    public boolean canHandleSite(String key, String name) {
        return CatAction.isCatSource(key);
    }

    @Override
    public boolean canHandleUrl(String url) {
        return false;
    }

    @Override
    public boolean handleSite(Activity activity, String key, String id, String name, String pic, String mark) {
        return CatAction.openWebsite(key, id, pic);
    }

    @Override
    public boolean handleUrl(Activity activity, String url, String title) {
        return false;
    }

    @Override
    public boolean handleResult(Activity activity, String historyKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int position, Result result, long timeout) {
        return false;
    }
}
