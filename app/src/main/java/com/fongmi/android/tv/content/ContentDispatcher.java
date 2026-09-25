package com.fongmi.android.tv.content;

import android.app.Activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;

import java.util.ArrayList;
import java.util.List;

public class ContentDispatcher {

    private static final List<ContentHandler> handlers = new ArrayList<>();

    public static void registerHandler(ContentHandler handler) {
        handlers.add(handler);
    }

    public static boolean dispatchSite(Activity activity, String key, String id, String name, String pic, String mark) {
        for (ContentHandler handler : handlers) {
            if (handler.canHandleSite(key, name) && handler.handleSite(activity, key, id, name, pic, mark)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchUrl(Activity activity, String url, String title) {
        for (ContentHandler handler : handlers) {
            if (handler.canHandleUrl(url) && handler.handleUrl(activity, url, title)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchResult(Activity activity, String historyKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int position, Result result, long timeout) {
        for (ContentHandler handler : handlers) {
            if (handler.handleResult(activity, historyKey, siteKey, flag, vodName, vodPic, episodes, position, result, timeout)) {
                return true;
            }
        }
        return false;
    }
}
