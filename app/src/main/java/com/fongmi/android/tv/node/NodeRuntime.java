package com.fongmi.android.tv.node;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 猫源运行时：主进程侧的协调层。
 *
 * <p>真正的 Node 启动跑在 {@link NodeService}（{@code :node} 子进程）里。
 * 这里只负责：启动子进程 Service、接收子进程通过 Messenger 回报的进度/端口/错误、
 * 换源时杀旧子进程再起新子进程。
 *
 * <p>换源流程：如果已有子进程在跑且 bundle 不同，先 {@link NodeService#stop} 杀掉它
 * （Node/V8 随进程销毁干净），再 {@link NodeService#start} 起一个新的 :node 进程
 * 跑新 bundle。这绕过了 nodejs-mobile 的 {@code node::Start} 不可进程内重入的限制。
 */
public final class NodeRuntime {

    /** 首选端口，被占用时往后找；bundle 本身不做 EADDRINUSE 重试，所以由这边探。 */
    private static final int PREFERRED_PORT = 9988;
    private static final int PORT_SCAN = 20;
    private static final long START_TIMEOUT_MS = 55_000L;

    private static volatile int port;
    private static final AtomicBoolean STARTING = new AtomicBoolean(false);
    private static volatile boolean running;
    private static volatile String servingUrl = "";
    private static volatile String servingSourceKey = "";
    /** 每轮启动的世代号，用来丢弃旧子进程的延迟回报。 */
    private static final AtomicLong START_GENERATION = new AtomicLong();

    /** 主进程侧的回复 Messenger，接收子进程回报。写在后台线程、读在主线程，必须 volatile。 */
    private static volatile Messenger replyMessenger;
    private static volatile NodeRuntime.Callback pendingCallback;

    public interface Callback {
        void onProgress(String message);

        void onReady(String baseUrl);

        void onError(String message);
    }

    private NodeRuntime() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static int port() {
        return port;
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    public static String configUrl() {
        return "http://127.0.0.1:" + port + "/config";
    }

    /**
     * 启动运行时。同一个 bundle 已在跑则直接复用；换 bundle 则杀旧子进程再起新的。
     *
     * @param url 用户填的猫源地址（{@code .../index.js.md5}）
     */
    public static synchronized void start(Context context, String url, Callback callback) {
        if (TextUtils.isEmpty(url)) {
            if (callback != null) callback.onError("未填写猫源地址");
            return;
        }
        // 复用要求「同一地址」且「来源身份仍与运行中的一致」：只比地址的话，服务端原地更新
        // bundle、或本地包被改写后都会继续跑旧 JS。本地包按内容指纹判定，内容没变就无需重启。
        if (running && same(url) && NodeBundle.servesCurrentSource(url, servingSourceKey)) {
            if (callback != null) callback.onReady(baseUrl());
            return;
        }
        if (!STARTING.compareAndSet(false, true)) {
            if (callback != null) callback.onError("正在启动中");
            return;
        }
        long generation = START_GENERATION.incrementAndGet();
        pendingCallback = callback;
        replyMessenger = new Messenger(new ReplyHandler(generation));
        // 换源：先杀旧子进程（如果有），再起新的
        if (running || !TextUtils.isEmpty(servingUrl)) {
            SpiderDebug.log("node", "switching source: stopping old node service (was=%s)", servingUrl);
            notifyProgress(context, callback, context.getString(R.string.node_stopping));
            NodeService.stop(context);
            // stopService 是异步的，onDestroy 里的 killProcess 也需要时间生效。
            // 不等的话 startForegroundService 会投递到还没死掉的旧进程，node::Start 第二次调用会 SIGSEGV。
            waitForProcessDeath(context);
            running = false;
            servingSourceKey = "";
            port = 0;
            notifyProgress(context, callback, context.getString(R.string.node_switch_restart));
        }
        servingUrl = NodeBundle.bundleUrl(url);
        servingSourceKey = "";
        try {
            NodeService.start(context, url, replyMessenger);
            App.post(() -> timeout(context, generation), START_TIMEOUT_MS);
        } catch (RuntimeException e) {
            STARTING.set(false);
            running = false;
            servingUrl = "";
            servingSourceKey = "";
            port = 0;
            pendingCallback = null;
            replyMessenger = null;
            SpiderDebug.log("node", "failed to start node service: %s", e.getMessage());
            if (callback != null) callback.onError("猫源服务启动失败: " + e.getMessage());
        }
    }

    private static synchronized void timeout(Context context, long generation) {
        if (generation != START_GENERATION.get() || !STARTING.compareAndSet(true, false)) return;
        // 先让当前 reply handler 失效：stopService 是异步的，旧 :node 仍可能在退出前发 READY。
        // 同时保留 servingUrl，让下一次 start 先等待这个旧进程真的结束，不能把重试投给旧 Service。
        START_GENERATION.incrementAndGet();
        NodeService.stop(context);
        running = false;
        servingSourceKey = "";
        port = 0;
        NodeNotify.done(context, "猫源启动失败：服务未在预期时间内就绪");
        Notify.show("猫源启动失败：服务未在预期时间内就绪");
        Callback callback = pendingCallback;
        pendingCallback = null;
        replyMessenger = null;
        if (callback != null) callback.onError("服务未在预期时间内就绪");
    }

    /** 是否就是当前这个 bundle。 */
    private static boolean same(String url) {
        String requested = NodeBundle.bundleUrl(url);
        return !TextUtils.isEmpty(requested) && requested.equals(servingUrl);
    }

    /** 换源等主进程侧阶段进度：同时推通知栏和 callback。 */
    private static void notifyProgress(Context context, Callback callback, String text) {
        SpiderDebug.log("node", "%s", text);
        NodeNotify.progress(context, text, -1);
        App.post(() -> Notify.show(text));
        if (callback != null) callback.onProgress(text);
    }

    /**
     * 等 :node 子进程真正死掉，最多等 3 秒。
     * stopService / killProcess 都是异步的，不等就 startForegroundService 会复用旧进程导致 node::Start 重入崩溃。
     */
    private static void waitForProcessDeath(Context context) {
        String procName = context.getPackageName() + ":node";
        for (int i = 0; i < 30; i++) {
            if (!isProcessRunning(context, procName)) return;
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }
        }
        SpiderDebug.log("node", "old :node process still alive after 3s, proceeding anyway");
    }

    private static boolean isProcessRunning(Context context, String processName) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (processName.equals(info.processName)) return true;
        }
        return false;
    }

    /**
     * 主进程侧处理子进程通过 Messenger 回报的消息。
     *
     * <p>每轮 start 都带自己的 generation：旧 :node 进程可能在 waitForProcessDeath 的 3 秒后
     * 还活着并继续回报，若不校验就会把旧端口写进新一轮的 port/servingSourceKey，
     * 表现为「选了 B 源却连到 A 源」，同时把新一轮的 callback 吃掉。
     */
    private static class ReplyHandler extends Handler {
        private final long generation;

        ReplyHandler(long generation) {
            super(Looper.getMainLooper());
            this.generation = generation;
        }

        @Override
        public void handleMessage(Message msg) {
            if (generation != START_GENERATION.get()) {
                SpiderDebug.log("node", "ignoring stale node reply what=%d gen=%d current=%d", msg.what, generation, START_GENERATION.get());
                return;
            }
            NodeRuntime.Callback callback = pendingCallback;
            switch (msg.what) {
                case NodeService.MSG_READY: {
                    port = msg.arg1;
                    running = true;
                    servingSourceKey = NodeBundle.installedSourceKey(App.get());
                    STARTING.set(false);
                    pendingCallback = null;
                    replyMessenger = null;
                    String ready = App.get().getString(R.string.node_ready);
                    NodeNotify.done(App.get(), ready + "，端口 " + port);
                    Notify.show(ready);
                    if (callback != null) callback.onReady(baseUrl());
                    break;
                }
                case NodeService.MSG_PROGRESS: {
                    String text = msg.getData() != null ? msg.getData().getString("text") : "";
                    NodeNotify.progress(App.get(), text, -1);
                    if (callback != null) callback.onProgress(text);
                    break;
                }
                case NodeService.MSG_ERROR: {
                    String text = msg.getData() != null ? msg.getData().getString("text") : "未知错误";
                    STARTING.set(false);
                    running = false;
                    servingSourceKey = "";
                    pendingCallback = null;
                    replyMessenger = null;
                    NodeNotify.done(App.get(), "猫源启动失败：" + text);
                    Notify.show("猫源启动失败：" + text);
                    if (callback != null) callback.onError(text);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * 从首选端口往后找一个能绑上的。探测用的 socket 立刻关闭，与 Node 真正 bind 之间
     * 存在极小的竞争窗口——所以最终仍以落盘的实际端口为准，探测只是让端口尽量可预期。
     * 全部占用时返回 0，让 bundle 自己取随机端口。
     */
    static int freePort() {
        for (int candidate = PREFERRED_PORT; candidate < PREFERRED_PORT + PORT_SCAN; candidate++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", candidate));
                return candidate;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** 引导脚本落盘的候选端口，逗号分隔，猫源那个（我们指定的）在最前。兼容只有单个端口的旧格式。 */
    static List<Integer> readPorts(File file) {
        List<Integer> ports = new ArrayList<>();
        if (!file.exists()) return ports;
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[128];
            int len = in.read(buf);
            if (len <= 0) return ports;
            for (String part : new String(buf, 0, len).trim().split(",")) {
                try {
                    int value = Integer.parseInt(part.trim());
                    if (value > 0 && !ports.contains(value)) ports.add(value);
                } catch (NumberFormatException ignored) {
                }
            }
            return ports;
        } catch (Exception ignored) {
            return ports;
        }
    }
}
