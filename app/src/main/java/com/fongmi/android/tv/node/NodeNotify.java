package com.fongmi.android.tv.node;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;

/**
 * 猫源启动的进度提示。
 *
 * <p>首次要下 Node 运行时（几十 MB）再拉 bundle、起服务，整体可能一分钟以上。
 * 这段时间发生在配置加载的后台线程里，没有 Activity 上下文可依赖，
 * 所以用通知栏承载进度，比 toast 更适合长耗时任务。
 */
final class NodeNotify {

    private static final int ID = 9531;

    private NodeNotify() {
    }

    static void progress(Context context, String text, int percent) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notify.DEFAULT)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("猫源")
                    .setContentText(text)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            if (percent >= 0) builder.setProgress(100, Math.min(100, percent), false);
            else builder.setProgress(0, 0, true);
            notify(context, builder.build());
        } catch (Exception ignored) {
        }
    }

    static void done(Context context, String text) {
        try {
            Notification notification = new NotificationCompat.Builder(context, Notify.DEFAULT)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("猫源")
                    .setContentText(text)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            notify(context, notification);
        } catch (Exception ignored) {
        }
    }

    static void cancel(Context context) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(ID);
        } catch (Exception ignored) {
        }
    }

    private static void notify(Context context, Notification notification) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(ID, notification);
    }
}
