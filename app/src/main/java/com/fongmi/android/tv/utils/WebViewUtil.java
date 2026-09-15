package com.fongmi.android.tv.utils;

import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Set;

public class WebViewUtil {

    private static final String SYSTEM_SETTINGS_PACKAGE = "com.android.settings";

    private static final Set<String> BROWSER_PACKAGES = Set.of(
            "com.android.chrome",
            "com.mi.globalbrowser",
            "com.huawei.browser",
            "com.heytap.browser",
            "com.vivo.browser"
    );

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static String spoof() {
        PackageManager pm = App.get().getPackageManager();
        return BROWSER_PACKAGES.stream().filter(packageName -> installed(pm, packageName)).findFirst().orElse(SYSTEM_SETTINGS_PACKAGE);
    }

    public static boolean support() {
        try {
            CookieManager.getInstance();
            return App.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
        } catch (Throwable e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static void configureBase(WebView webView, String role) {
        if (webView == null) return;
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        logSettings(webView, role);
    }

    public static void configureHome(WebView webView) {
        configureBase(webView, "webhome");
        WebSettings settings = webView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) settings.setOffscreenPreRaster(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        logSettings(webView, "webhome-final");
    }

    public static void logProvider(String role) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            SpiderDebug.log("webview", "role=%s provider unavailable sdk=%s", role, Build.VERSION.SDK_INT);
            return;
        }
        try {
            android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) {
                SpiderDebug.log("webview", "role=%s provider unavailable", role);
            } else {
                SpiderDebug.log("webview", "role=%s provider package=%s version=%s code=%s", role, info.packageName, info.versionName, Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode);
            }
        } catch (Throwable e) {
            SpiderDebug.log("webview", e);
        }
    }

    private static void logSettings(WebView webView, String role) {
        try {
            WebSettings settings = webView.getSettings();
            SpiderDebug.log("webview", "role=%s hardware=%s layer=%s js=%s dom=%s db=%s cache=%s zoom=%s builtInZoom=%s wide=%s overview=%s textZoom=%s mixed=%s offscreen=%s ua=%s",
                    role,
                    webView.isHardwareAccelerated(),
                    webView.getLayerType(),
                    settings.getJavaScriptEnabled(),
                    settings.getDomStorageEnabled(),
                    settings.getDatabaseEnabled(),
                    settings.getCacheMode(),
                    settings.supportZoom(),
                    settings.getBuiltInZoomControls(),
                    settings.getUseWideViewPort(),
                    settings.getLoadWithOverviewMode(),
                    settings.getTextZoom(),
                    settings.getMixedContentMode(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getOffscreenPreRaster(),
                    settings.getUserAgentString());
        } catch (Throwable e) {
            SpiderDebug.log("webview", e);
        }
    }
}
