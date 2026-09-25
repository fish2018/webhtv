package com.fongmi.android.tv.ui.web;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

/**
 * 猫源设置页的「导出」落地。
 *
 * <p>设置页的导出按钮是 {@code <a href="/website/backup" target="_blank">}，服务端回
 * {@code Content-Disposition: attachment}。WebView 自己不下载附件——没挂
 * {@code DownloadListener} 时它直接把响应丢掉，表现就是「点了没反应」。
 *
 * <p>交给系统 {@link DownloadManager} 写公共下载目录：文件落在用户找得到的地方，
 * 下载完还有系统通知可点开，导出的配置能被其它 App（网盘、聊天工具）继续使用。
 */
final class CatWebDownload {

    private static final String TAG = "cat-web";
    /** 导出的配置很小（几 KB），文件名兜底时用它，避免落一个没有后缀的文件。 */
    private static final String FALLBACK = "config.json";

    private CatWebDownload() {
    }

    /**
     * @param url         下载地址；只接受 http(s)，其余（blob:/data:）交回调用方
     * @param disposition 响应的 Content-Disposition，用来取服务端指定的文件名
     * @return true 表示已交给系统下载
     */
    static boolean enqueue(Context context, String url, String userAgent, String disposition, String mime) {
        if (!URLUtil.isNetworkUrl(url)) return false;
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) return false;
            String name = name(url, disposition, mime);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.allowScanningByMediaScanner();
            request.setTitle(name);
            if (!TextUtils.isEmpty(mime)) request.setMimeType(mime);
            if (!TextUtils.isEmpty(userAgent)) request.addRequestHeader("User-Agent", userAgent);
            // 设置站点可能带鉴权 Cookie；DownloadManager 是独立进程，不共享 WebView 的 Cookie 罐
            String cookie = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookie)) request.addRequestHeader("Cookie", cookie);
            manager.enqueue(request);
            SpiderDebug.log(TAG, "download name=%s url=%s", name, url);
            Notify.show(context.getString(R.string.cat_web_download, name));
            return true;
        } catch (Throwable e) {
            // 下载服务被裁剪、公共目录不可写等：如实告知，不要静默失败回到「点了没反应」
            SpiderDebug.log(TAG, e);
            Notify.show(R.string.cat_web_download_failed);
            return true;
        }
    }

    /**
     * 文件名优先取 Content-Disposition —— 猫源那边给的是 {@code config.<日期>.json}，
     * 比从 {@code /website/backup} 这种无后缀路径猜出来的名字有用得多。
     */
    private static String name(String url, String disposition, String mime) {
        String name = "";
        try {
            name = URLUtil.guessFileName(url, disposition, mime);
        } catch (Throwable ignored) {
        }
        // guessFileName 猜不出后缀时会拼一个 .bin，配置文件落成 .bin 用户没法直接看
        if (TextUtils.isEmpty(name) || name.endsWith(".bin")) {
            String extension = TextUtils.isEmpty(mime) ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            name = TextUtils.isEmpty(extension) ? FALLBACK : "config." + extension;
        }
        return name;
    }
}
