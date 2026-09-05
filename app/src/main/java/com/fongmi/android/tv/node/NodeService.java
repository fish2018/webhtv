package com.fongmi.android.tv.node;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Process;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跑在 {@code :node} 子进程的前台 Service，承载 Node 运行时的全部生命周期。
 *
 * <p>主进程通过 {@code startForegroundService} 启动本 Service，把猫源地址放在 Intent extras 里。
 * 本进程内完成 libnode 加载、bundle 下载、Node 启动、端口就绪探测，
 * 再通过回复 Messenger（Intent extras 传入）把 READY（端口）或 ERROR 回传主进程。
 *
 * <p>换源时主进程 stopService 杀掉本进程，Node/V8 随进程销毁干净，
 * 再 startService 起一个新的 :node 进程跑新 bundle。
 * 这样绕过了 nodejs-mobile 的 node::Start 不可进程内重入的限制。
 */
public class NodeService extends Service {

    // 子进程 -> 主进程
    public static final int MSG_READY = 10;
    public static final int MSG_PROGRESS = 11;
    public static final int MSG_ERROR = 12;

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_REPLY_MESSENGER = "reply";

    private static final int NOTIFICATION_ID = 9532;

    /** node::Start 在一个进程里只能调一次，第二次会 SIGSEGV。 */
    private final AtomicBoolean nodeLaunched = new AtomicBoolean(false);

    /** 主进程调用：启动子进程 Service。 */
    public static void start(Context context, String url, Messenger reply) {
        Intent intent = new Intent(context, NodeService.class)
                .setAction(BuildConfig.APPLICATION_ID + ".node.START")
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_REPLY_MESSENGER, reply);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, NodeService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat(notification(getString(R.string.node_prepare)));
        SpiderDebug.log("node", "NodeService created in pid=%s", android.os.Process.myPid());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForegroundService 的每个请求都必须有 startForeground 响应；
        // 系统强杀/更新后重启的 intents 可能不带猫源参数，也不能只依赖 onCreate。
        startForegroundCompat(notification(getString(R.string.node_prepare)));
        if (intent != null && intent.hasExtra(EXTRA_URL) && nodeLaunched.compareAndSet(false, true)) {
            String url = intent.getStringExtra(EXTRA_URL);
            Messenger reply = intent.getParcelableExtra(EXTRA_REPLY_MESSENGER);
            new Thread(() -> startNode(url, reply), "node-runtime").start();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        SpiderDebug.log("node", "NodeService destroyed pid=%s", android.os.Process.myPid());
        // 主动杀进程，确保 node/V8 随进程彻底销毁，下次 startForegroundService 一定起新进程
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 主进程通过 startForegroundService 而非 bindService 启动
    }

    /**
     * 在子进程内启动 Node：libnode -> bundle -> boot.js -> node::Start -> 端口就绪。
     * 全程在后台线程，通过 reply Messenger 回报进度和结果。
     */
    private void startNode(String url, Messenger reply) {
        if (url == null || url.isEmpty()) {
            sendError(reply, "未填写猫源地址");
            return;
        }
        try {
            sendProgress(reply, getString(R.string.node_prepare));

            // 1) libnode
            String downloading = getString(R.string.node_downloading);
            String error = NodeLib.ensure(this, (done, total) -> {
                int percent = total > 0 ? (int) (done * 100 / total) : -1;
                String msg = percent >= 0
                        ? String.format("%s %d%%", downloading, percent)
                        : downloading + " " + size(done);
                sendProgress(reply, msg);
            });
            if (error != null) {
                sendError(reply, "Node 运行时不可用: " + error);
                return;
            }

            // 2) bundle
            sendProgress(reply, getString(R.string.node_bundle));
            error = NodeBundle.ensure(this, url);
            if (error != null) {
                sendError(reply, error);
                return;
            }

            // 3) boot script + 启动 Node
            File bundle = NodeBundle.file(this);
            File portFile = new File(NodeBundle.dir(this), "port");
            portFile.delete();
            int preferred = NodeRuntime.freePort();
            File script = NodeBoot.write(this, bundle, NodeBundle.config(this), Proxy.getPort(), preferred);

            sendProgress(reply, getString(R.string.node_starting));
            // node::Start 阻塞到事件循环结束，放独立线程
            new Thread(() -> {
                int code = com.fongmi.android.tv.nodejs.NodeBridge.start(script, "--max-old-space-size=256");
                SpiderDebug.log("node", "node exited code=%s", code);
            }, "node-main").start();

            // 4) 等端口就绪
            int port = waitReady(portFile, reply);
            if (port > 0) {
                sendReady(reply, port);
                updateNotification(getString(R.string.node_ready) + "，端口 " + port);
            } else {
                sendError(reply, "服务未在预期时间内就绪");
            }
        } catch (Throwable e) {
            SpiderDebug.log("node", e);
           sendError(reply, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
       }
    }

    /**
     * 等引导脚本把候选端口落盘，再逐个探 /config 认准猫源服务。
     * 逻辑与原 NodeRuntime.waitReady 一致。
     */
    private int waitReady(File portFile, Messenger reply) {
        boolean reported = false;
        for (int i = 0; i < 225; i++) {
            try {
                Thread.sleep(200);
                List<Integer> candidates = NodeRuntime.readPorts(portFile);
                if (candidates.isEmpty()) {
                    if (!reported) {
                        sendProgress(reply, getString(R.string.node_waiting));
                        reported = true;
                    }
                    continue;
                }
                for (int candidate : candidates) {
                    String cfg = com.github.catvod.net.OkHttp.string("http://127.0.0.1:" + candidate + "/config");
                    if (com.fongmi.android.tv.api.CatSource.isConfig(cfg)) {
                        return candidate;
                    }
                }
            } catch (InterruptedException e) {
                return 0;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private void sendReady(Messenger reply, int port) {
        if (reply == null) return;
        Message msg = Message.obtain(null, MSG_READY, port, 0);
        try {
            reply.send(msg);
        } catch (RemoteException e) {
            SpiderDebug.log("node", "reply.send ready failed: %s", e.getMessage());
        }
    }

    private void sendProgress(Messenger reply, String text) {
        SpiderDebug.log("node", "%s", text);
        if (reply == null) return;
        Bundle data = new Bundle();
        data.putString("text", text);
        Message msg = Message.obtain(null, MSG_PROGRESS);
        msg.setData(data);
        try {
            reply.send(msg);
        } catch (RemoteException ignored) {
        }
    }

    private void sendError(Messenger reply, String text) {
        SpiderDebug.log("node", "start failed: %s", text);
        if (reply == null) return;
        Bundle data = new Bundle();
        data.putString("text", text);
        Message msg = Message.obtain(null, MSG_ERROR);
        msg.setData(data);
        try {
            reply.send(msg);
            stopSelf();
        } catch (RemoteException ignored) {
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        try {
            Notification notification = new NotificationCompat.Builder(this, Notify.DEFAULT)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("猫源")
                    .setContentText(text)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
        } catch (Exception ignored) {
        }
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("猫源")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1048576) return String.format(java.util.Locale.US, "%.0fKB", bytes / 1024f);
        return String.format(java.util.Locale.US, "%.1fMB", bytes / 1048576f);
    }
}
