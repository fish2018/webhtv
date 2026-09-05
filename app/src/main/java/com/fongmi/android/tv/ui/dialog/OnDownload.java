package com.fongmi.android.tv.ui.dialog;

import com.fongmi.android.tv.bean.Episode;

import java.util.List;

/**
 * 下载剧集选择回调。定义在 main 源码集，因为下载核心（DownloadItem/DownloadManager）位于 main，
 * 而触发下载的入口（详情页、播放器页）都需实现它。
 */
public interface OnDownload {
    void onDownload(List<Episode> episodes);
}
