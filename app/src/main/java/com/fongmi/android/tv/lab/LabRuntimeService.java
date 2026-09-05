package com.fongmi.android.tv.lab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

public class LabRuntimeService extends Service {

    private static final String CHANNEL = "lab_runtime";
    private static final String ACTION_STOP_ALL = "lab_stop_all";
    private static final int NOTIFY_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            int count = LabRunner.runningCount();
            if (count <= 0) {
                stopSelf();
                return;
            }
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFY_ID, buildNotification(count));
            handler.postDelayed(this, 5000);
        }
    };

    public static void update() {
        Context context = App.get();
        boolean enabled = LabConfig.get().getForeground();
        int count = LabRunner.runningCount();
        if (!enabled || count <= 0) {
            context.stopService(new Intent(context, LabRuntimeService.class));
            return;
        }
        Intent intent = new Intent(context, LabRuntimeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALL.equals(intent.getAction())) {
            LabRunner.stopAll();
            LabProcManager.stopAll();
            update();
            return START_NOT_STICKY;
        }
        int count = LabRunner.runningCount();
        startForeground(NOTIFY_ID, buildNotification(count));
        acquireWakeLock();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 5000);
        return START_STICKY;
    }

    private Notification buildNotification(int count) {
        Intent stopAll = new Intent(this, LabRuntimeService.class).setAction(ACTION_STOP_ALL);
        PendingIntent pending = PendingIntent.getService(this, 0, stopAll, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent showAll = new Intent(this, LabActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(LabActivity.EXTRA_SHOW_RUNNING, true);
        PendingIntent showPending = PendingIntent.getActivity(this, 1, showAll, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("实验室后台任务运行中")
                .setContentText(count + " 个后台命令正在运行")
                .setOngoing(true)
                .addAction(0, "停止全部", pending)
                .addAction(0, "显示全部", showPending)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "实验室后台任务", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebHTV:LabRuntime");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refreshRunnable);
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
