package com.fongmi.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.utils.DownloadManager;

/**
 * 處理下載通知欄上的操作按鈕。
 */
public class DownloadReceiver extends BroadcastReceiver {

    public static final String ACTION_PAUSE_ALL = "download.pause.all";
    public static final String ACTION_RESUME_ALL = "download.resume.all";
    public static final String ACTION_TOGGLE = "download.toggle";
    public static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || TextUtils.isEmpty(intent.getAction())) return;
        switch (intent.getAction()) {
            case ACTION_PAUSE_ALL:
                DownloadManager.get().pauseAll();
                DownloadService.stop(context);
                break;
            case ACTION_RESUME_ALL:
                DownloadManager.get().resumeAll();
                DownloadService.start(context);
                break;
            case ACTION_TOGGLE:
                DownloadItem item = DownloadManager.get().find(intent.getStringExtra(EXTRA_ID));
                if (item != null) DownloadManager.get().toggle(item);
                break;
        }
    }
}
