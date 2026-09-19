package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.DanmakuSearchListFocusFixer;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PreviousProcessExitLogger;
import com.fongmi.hook.Hook;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private volatile Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        installCrashGuard();
        if (PlaybackRecoveryMonitor.isRecoveryProcess(base)) return;
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Server.get().start();
        if (PlaybackRecoveryMonitor.isRecoveryProcess(this)) return;
        PlaybackMemoryMonitor.process().initialize(this);
        PlaybackSystemConditionMonitor.process().initialize(this);
        Setting.applyLanguage();
        Config.deleteEmpty();
        DebugLogStore.restoreEnabled();
        if (DebugLogStore.isEnabled()) {
            PlaybackRecoveryMonitor.logPreviousResult(this);
            Setting.logDebugEnvironment("restore");
            PreviousProcessExitLogger.log(this);
        }
        Notify.createChannel();
        ProxySetting.apply();
        DanmakuSearchListFocusFixer.start();
        registerActivityLifecycleCallbacks(this);
        post(this::startBackgroundServices, 1200);
    }

    /**
     * 全局未捕获异常保护器。
     * 第三方 jar（如弹幕源）在任何线程中抛出的异常都不应导致主程序崩溃，
     * 只记录日志；应用自身代码的异常仍走默认处理器。
     */
    private void installCrashGuard() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String msg = throwable == null ? "null" : throwable.getClass().getSimpleName() + ":" + throwable.getMessage();
                android.util.Log.e("crash-guard", "thread=" + thread.getName() + " id=" + thread.getId() + " error=" + msg, throwable);
                String stack = throwable == null ? "null" : android.util.Log.getStackTraceString(throwable);
                SpiderDebug.log("crash-guard", "thread=%s id=%d error=%s\n%s", thread.getName(), thread.getId(), msg, stack);
            } catch (Throwable ignored) {
            }
            try {
                if (throwable != null) throwable.printStackTrace();
            } catch (Throwable ignored) {
            }
            // 非主线程异常：直接吞掉，不让进程崩溃
            if (thread.getId() != Looper.getMainLooper().getThread().getId()) {
                return;
            }
            // 主线程异常：判断是否来自第三方 jar，是则吞掉
            if (isFromThirdPartyJar(throwable)) {
                return;
            }
            // 应用自身代码的主线程异常：交给默认处理器
            if (defaultHandler != null) {
                try {
                    defaultHandler.uncaughtException(thread, throwable);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /**
     * 判断异常是否来自第三方 jar（通过堆栈跟踪中的类名前缀判断）。
     */
    private boolean isFromThirdPartyJar(Throwable throwable) {
        if (throwable == null) return false;
        StackTraceElement[] stack = throwable.getStackTrace();
        if (stack == null) return false;
        for (StackTraceElement element : stack) {
            String cls = element.getClassName();
            if (cls == null) continue;
            // 第三方 jar 中的所有 spider/parser 包类（应用自身不在这两个包下）
            // 覆盖 merge 混淆类、Init、Proxy、DexNative、Danmaku、AowuShinidie、XxxAmns 等
            if (cls.startsWith("com.github.catvod.spider.")
                    || cls.startsWith("com.github.catvod.parser.")) {
                return true;
            }
        }
        // 检查 cause
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isFromThirdPartyJar(cause);
        }
        return false;
    }

    @Override
    public void onTrimMemory(int level) {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onLowMemory();
        super.onLowMemory();
    }

    private void startBackgroundServices() {
        SpiderDebug.log("startup", "background services start cost=%sms", System.currentTimeMillis() - time);
        Server.get().start();
        PlaybackRemoteSyncer.start();
        RemoteAgent.get().start();
        NsdDeviceDiscovery.register();
        SpiderDebug.log("startup", "background services ready cost=%sms", System.currentTimeMillis() - time);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}
