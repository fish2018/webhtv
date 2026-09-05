package com.fongmi.android.tv.service;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.receiver.DownloadReceiver;
import com.fongmi.android.tv.utils.DownloadManager;
import com.fongmi.android.tv.utils.Notify;

import java.util.List;

/**
 * 以前台服務維持下載任務在後台持續執行，並顯示進度通知。
 */
public class DownloadService extends Service implements DownloadManager.Listener {

    public static final String ACTION_START = "download.start";
    public static final String ACTION_STOP = "download.stop";
    private static final int NOTIFY_ID = 9528;
    private static final long INTERVAL = 1000;

    private long lastUpdate;

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_START);
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, DownloadService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notify.createChannel();
        DownloadManager.get().addListener(this);
        startForeground(NOTIFY_ID, build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            DownloadManager.get().pauseAll();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFY_ID, build());
        return START_STICKY;
    }

    @Override
    public void onDownloadUpdate(@NonNull DownloadItem item) {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < INTERVAL) return;
        lastUpdate = now;
        notifyProgress();
    }

    @Override
    public void onDownloadChange() {
        notifyProgress();
    }

    private void notifyProgress() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            NotificationManagerCompat.from(this).notify(NOTIFY_ID, build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDownloadState(boolean active) {
        if (!active) stopSelf();
    }

    private Notification build() {
        List<DownloadItem> items = DownloadManager.get().getItems();
        DownloadItem running = null;
        int active = 0;
        for (DownloadItem item : items) {
            if (!item.isActive()) continue;
            ++active;
            if (running == null && item.isRunning()) running = item;
        }
        String title = running == null ? getString(R.string.download_list) : running.getVodName() + " " + running.getEpisodeName();
        // 參考 v2：下載中展示「百分比 + 速度」，而非僅「X 個任務下載中」
        String text = running == null ? getString(R.string.download_notify_text, active)
                : running.getProgress() + "%  " + formatSpeed(running.getSpeed());
        int progress = running == null ? 0 : running.getProgress();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(active > 0)
                .setProgress(100, progress, running == null)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent())
                .addAction(0, getString(R.string.download_pause_all), pending(DownloadReceiver.ACTION_PAUSE_ALL));
        return builder.build();
    }

    private static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "0 B/s";
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSec / 1024f);
        return String.format("%.2f MB/s", bytesPerSec / (1024f * 1024f));
    }

    private PendingIntent pending(String action) {
        Intent intent = new Intent(App.get(), DownloadReceiver.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(App.get(), action.hashCode(), intent, flags);
    }

    private PendingIntent contentIntent() {
        // DownloadListActivity 仅在 mobile 源码集存在，main 不能直接引用，故用类名反射避免编译期依赖
        Intent intent = new Intent().setClassName(App.get(), "com.fongmi.android.tv.ui.activity.DownloadListActivity");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(App.get(), 0, intent, flags);
    }

    @Override
    public void onDestroy() {
        DownloadManager.get().removeListener(this);
        ServiceCompat.stopForeground(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class ServiceCompat {
        static void stopForeground(Service service) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
                else service.stopForeground(true);
            } catch (Exception ignored) {
            }
        }
    }
}
