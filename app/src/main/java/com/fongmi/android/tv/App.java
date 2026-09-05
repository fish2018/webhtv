package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.proxy.MultiThreadProxy;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.DanmakuSearchListFocusFixer;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PreviousProcessExitLogger;
import com.fongmi.android.tv.utils.WebViewDataDirectoryGuard;
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

    private final Runnable backgroundServicesStarter = this::startBackgroundServicesNow;

    private Activity activity;
    private Hook hook;

    private Resources resources;
    private int resourcesLanguage = Integer.MIN_VALUE;

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
        WebViewDataDirectoryGuard.clearStaleLock(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PlaybackMemoryMonitor.process().initialize(this);
        PlaybackSystemConditionMonitor.process().initialize(this);
        Setting.applyLanguage();
        AppBranding.applyLauncherIcon(this);
        DebugLogStore.restoreEnabled();
        if (DebugLogStore.isEnabled()) {
            Setting.logDebugEnvironment("restore");
            PreviousProcessExitLogger.log(this);
        }
        Notify.createChannel();
        ProxySetting.apply();
        registerActivityLifecycleCallbacks(this);
        registerContentHandlers();
        resumeBackgroundServices();
    }

    private void registerContentHandlers() {
        // 猫源动作项排最前：它的判定最便宜（只比字符串），且命中就该直接开网页，
        // 不该让音频/阅读器 handler 先按站点规则把它认走
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.CatActionContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.AudioContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.ReaderContentHandler());
        registerReaderFallback();
    }

    private void registerReaderFallback() {
        Product.registerReaderFallback();
    }

    @Override
    public void onTrimMemory(int level) {
        PlaybackMemoryMonitor.process().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        PlaybackMemoryMonitor.process().onLowMemory();
        super.onLowMemory();
    }

    private void startBackgroundServicesNow() {
        SpiderDebug.log("startup", "background services start cost=%sms", System.currentTimeMillis() - time);
        Server.get().start();
        startMultiThreadProxy();
        PlaybackRemoteSyncer.start();
        RemoteAgent.get().start();
        NsdDeviceDiscovery.register();
        com.fongmi.android.tv.lab.LabAutoStart.start(this);
        SpiderDebug.log("startup", "background services ready cost=%sms", System.currentTimeMillis() - time);
    }

    private void startMultiThreadProxy() {
        try {
            var snapshot = MultiThreadProxy.applyStored();
            SpiderDebug.log("proxy",
                    "multi-thread proxy enabled=%s ready=%s port=%s revision=%s",
                    snapshot.config().enabled(),
                    snapshot.ready(),
                    snapshot.actualPort(),
                    snapshot.configRevision());
        } catch (Exception e) {
            SpiderDebug.log("proxy", "multi-thread proxy start failed error=%s", e.getMessage());
        }
    }

    public static void resumeBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.start();
        post(get().backgroundServicesStarter, 1200);
    }

    public static void stopBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.stop();
        MultiThreadProxy.stop();
        PlaybackRemoteSyncer.stop();
        RemoteAgent.get().stop();
        NsdDeviceDiscovery.unregister();
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
    @SuppressWarnings("deprecation")
    public Resources getResources() {
        int language = Setting.getLanguage();
        if (resources == null || resourcesLanguage != language) {
            Resources resources = super.getResources();
            Configuration configuration = Setting.wrapLanguage(getBaseContext()).getResources().getConfiguration();
            // WebView adds its resource package to the framework-owned AssetManager on Android 9.
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            this.resources = resources;
            resourcesLanguage = language;
        }
        return resources;
    }

    public void invalidateResources() {
        resources = null;
        resourcesLanguage = Integer.MIN_VALUE;
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
