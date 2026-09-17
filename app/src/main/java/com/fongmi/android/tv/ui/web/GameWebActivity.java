package com.fongmi.android.tv.ui.web;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 实验室：小游戏内置 WebView 容器。
 *
 * 与 WebReaderActivity（加载本地 reader.html 模板渲染 novel:// 数据）不同，
 * 本页直接 loadUrl 远程游戏地址（H5 / 模拟器站），全屏沉浸运行。
 * 卡片 vod_id 内嵌 JSON 的 url / header（含自定义 UA）在此消费。
 */
public class GameWebActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_UA = "ua";
    public static final String EXTRA_HEADERS = "headers"; // HashMap<String,String> 序列化

    private WebView webView;
    private ProgressBar progress;
    private View loading;
    private String loadUrl = "";
    private String ua = "";
    private Map<String, String> extraHeaders = new HashMap<>();
    private boolean renderedGone;

    public static void start(android.app.Activity activity, String url, String title, String ua, Map<String, String> headers) {
        Intent it = new Intent(activity, GameWebActivity.class);
        it.putExtra(EXTRA_URL, url == null ? "" : url);
        it.putExtra(EXTRA_TITLE, title == null ? "" : title);
        it.putExtra(EXTRA_UA, ua == null ? "" : ua);
        if (headers != null) it.putExtra(EXTRA_HEADERS, new HashMap<>(headers));
        activity.startActivity(it);
    }

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_reader);
        applyImmersive();

        // 兼容 Android 13+ enableOnBackInvokedCallback：手势/系统返回直接关闭游戏页
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        Intent it = getIntent();
        loadUrl = it.getStringExtra(EXTRA_URL) == null ? "" : it.getStringExtra(EXTRA_URL);
        ua = it.getStringExtra(EXTRA_UA) == null ? "" : it.getStringExtra(EXTRA_UA);
        String title = it.getStringExtra(EXTRA_TITLE) == null ? "" : it.getStringExtra(EXTRA_TITLE);
        if (!title.isEmpty()) setTitle(title);
        //noinspection unchecked
        Map<String, String> headers = (Map<String, String>) it.getSerializableExtra(EXTRA_HEADERS);
        if (headers != null) extraHeaders.putAll(headers);
        // UA 单独传递优先；否则从 headers 里取
        if (ua.isEmpty() && extraHeaders.containsKey("User-Agent")) ua = extraHeaders.get("User-Agent");

        webView = findViewById(R.id.web_view);
        progress = findViewById(R.id.progress);
        loading = findViewById(R.id.loading);
        webView.setBackgroundColor(0xFF101010);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false); // 游戏音频自动播放
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); // 禁用混合内容
        }
        if (!ua.isEmpty()) s.setUserAgentString(ua);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progress != null) {
                    if (newProgress >= 100) progress.setVisibility(View.GONE);
                    else { progress.setVisibility(View.VISIBLE); progress.setProgress(newProgress); }
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                hideLoading();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String target = request.getUrl() == null ? "" : request.getUrl().toString();
                return !isWebUrl(target); // 游戏内网页跳转留在容器，外部 scheme 不交给 WebView
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // 游戏页渲染进程崩溃：避免整 app 闪退，直接关闭游戏页
                renderedGone = true;
                Toast.makeText(GameWebActivity.this, "游戏页面已崩溃退出", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            }
        });

        if (!isWebUrl(loadUrl)) {
            Toast.makeText(this, loadUrl.isEmpty() ? "游戏地址为空" : "网页地址无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (extraHeaders.isEmpty()) webView.loadUrl(loadUrl);
        else webView.loadUrl(loadUrl, extraHeaders);
    }

    private void hideLoading() {
        if (loading != null) loading.setVisibility(View.GONE);
    }

    private static boolean isWebUrl(String url) {
        if (url == null) return false;
        String value = url.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private void applyImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 玩游戏保持常亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ / targetSdk 35+：FLAG_FULLSCREEN 已失效，必须用 InsetsController 隐藏系统栏
            try {
                getWindow().setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } catch (Throwable ignore) {}
        } else {
            try {
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            } catch (Throwable ignore) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            } catch (Throwable ignore) {}
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // WebView 加载/游戏交互后焦点变化会把系统栏重新拉出来，必须重新隐藏
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onDestroy() {
        if (webView != null && !renderedGone) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
