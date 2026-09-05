package com.fongmi.android.tv.ui.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.FileChooser;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;

/**
 * 猫源设置中心的内嵌浏览页。
 *
 * <p>猫源通过 {@code /msg} 的 {@code openInternalWebview} 请求宿主打开自己的配置站点
 * （CatPawOpen 的 {@code /website}）。原生宿主 CatVodApp 用 flutter_inappwebview 内嵌渲染，
 * 这里是等价实现——之前落到 {@code ACTION_VIEW} 跳外部浏览器，离开了 App。
 *
 * <p>与 {@link WebReaderActivity} 的关键差别：那个加载本地模板并挂 {@code AndroidReader}
 * JS 桥，因此必须阻断远程导航；这里正相反，要渲染远程页面，<b>所以绝不注册任何 JS 桥</b>——
 * addJavascriptInterface 是 WebView 级别的，一旦挂上就等于把 Java 方法交给页面脚本。
 */
public class CatWebActivity extends AppCompatActivity {

    /** 不带 TV- 前缀：SpiderDebug 自己会加，与 cat-msg / cat-source 保持一致。 */
    private static final String TAG = "cat-web";
    private static final String EXTRA_URL = "url";

    private WebView webView;
    private ProgressBar progress;
    private View loading;

    /**
     * {@code <input type="file">} 的待回调。WebView 要求每次请求必须回一次值——
     * 用户取消也得回 null，否则那个 input 永久卡住，再点没有任何反应。
     */
    private ValueCallback<Uri[]> chooser;

    public static Intent intent(Context context, String url) {
        return new Intent(context, CatWebActivity.class).putExtra(EXTRA_URL, url);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cat_web);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }

        // Android 13+ 手势返回与系统返回键都先让 WebView 回退，退到底再关页面
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) webView.goBack();
                else finish();
            }
        });

        webView = findViewById(R.id.web_view);
        progress = findViewById(R.id.progress);
        loading = findViewById(R.id.loading);
        ((TextView) findViewById(R.id.loading_text)).setText(R.string.cat_web_opening);
        ((TextView) findViewById(R.id.address)).setText(url);

        configure();
        SpiderDebug.log(TAG, "open url=%s", url);
        webView.loadUrl(url);
    }

    /**
     * 设置页是 React 应用且从 CDN 取 React/axios，所以 JS、DOM storage 与联网都必需。
     * 不开文件访问：远程页面没有任何理由读本地文件。
     */
    private void configure() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // TV 上没有触摸，靠 D-pad 移动焦点；这两项让 WebView 参与焦点链
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setBackgroundColor(0xFF101216);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                if (progress == null) return;
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                SpiderDebug.log(TAG, "console [%s] %s (%s:%d)",
                        msg.messageLevel(), msg.message(), msg.sourceId(), msg.lineNumber());
                return true;
            }

            /**
             * 设置页的「导入」是 antd Upload，底层就是 {@code <input type="file">}。
             * 不实现这个回调，WebView 对文件选择请求什么都不做——点击毫无反应。
             */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                // 上一次的请求还没回值就被新请求顶掉时，先把它收尾，避免页面那侧一直挂着
                cancelChooser();
                chooser = callback;
                SpiderDebug.log(TAG, "fileChooser accept=%s", java.util.Arrays.toString(params.getAcceptTypes()));
                try {
                    // 复用仓库既有选择器：它在 TV 或没有系统文件管理器时会退到内置 FileActivity
                    String mime = mime(params.getAcceptTypes());
                    FileChooser.from(picker).show(mime, new String[]{mime});
                    return true;
                } catch (Throwable e) {
                    SpiderDebug.log(TAG, e);
                    cancelChooser();
                    return false;
                }
            }
        });
        // 「导出」是 <a href="/website/backup"> + Content-Disposition: attachment。
        // WebView 不下载附件，没有这个监听器响应就被丢弃，同样是「点了没反应」。
        webView.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            SpiderDebug.log(TAG, "download url=%s mime=%s len=%d", url, mime, length);
            if (CatWebDownload.enqueue(this, url, userAgent, disposition, mime)) return;
            // blob:/data: 不是真实网络地址，系统下载器读不到；如实提示而不是静默失败
            SpiderDebug.log(TAG, "downloadUnsupported url=%s", url);
            com.fongmi.android.tv.utils.Notify.show(R.string.cat_web_download_failed);
        });
        webView.setWebViewClient(client());
    }

    /**
     * 把网页的 accept 收敛成一个 MIME，只认已经是 MIME 的那种。
     *
     * <p>刻意不把 {@code .json} 这类扩展名换算成 MIME：设置页给的就是 {@code .json}，
     * 而选择器列表里文件的类型由 DocumentsProvider 按扩展名反推——两边只要有一边不认
     * json，严格按 {@code application/json} 过滤就会把用户要导入的那个文件藏起来，
     * 比多列几个文件糟得多。备份文件被聊天工具转发后改名、丢后缀也很常见。
     * 仓库其它入口（{@code ConfigDialog}）同样不做类型过滤。
     */
    private String mime(String[] accepts) {
        if (accepts == null) return "*/*";
        for (String accept : accepts) {
            if (TextUtils.isEmpty(accept)) continue;
            String value = accept.trim();
            if (value.contains("/") && !value.startsWith("*")) return value;
        }
        return "*/*";
    }

    /**
     * 文件选择的结果回给页面。
     *
     * <p>取消、失败都必须回一次值（null）——漏掉的话页面那个 input 再也无法触发。
     * 内置 FileActivity 回的是 {@code file://}，系统选择器回 {@code content://}，
     * 两者 WebView 都能读，所以直接原样传回；只有取不到 URI 时才当作取消。
     */
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                ValueCallback<Uri[]> callback = chooser;
                chooser = null;
                if (callback == null) return;
                Uri uri = result.getResultCode() == Activity.RESULT_OK && result.getData() != null
                        ? result.getData().getData() : null;
                // 目录（内置 FileActivity 的「选当前目录」）不是可上传的文件，按取消处理
                if (uri != null && "file".equals(uri.getScheme()) && uri.getPath() != null
                        && new File(uri.getPath()).isDirectory()) uri = null;
                SpiderDebug.log(TAG, "fileChosen uri=%s", uri);
                callback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            });

    /** 回 null 把页面那侧的等待解开，并清掉引用避免跨页面复用。 */
    private void cancelChooser() {
        ValueCallback<Uri[]> callback = chooser;
        chooser = null;
        if (callback != null) callback.onReceiveValue(null);
    }

    private WebViewClient client() {
        return new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                SpiderDebug.log(TAG, "pageFinished url=%s", url);
                hideLoading();
                address(url);
            }

            /**
             * 页面内导航一律留在本页。设置站点会跳到自己的子路由，跳出去就等于回到「离开 App」那个毛病。
             * 非 http(s) 的 scheme（intent://、market:// 等）直接丢掉，不去唤起外部应用。
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String target = request.getUrl() == null ? "" : request.getUrl().toString();
                if (target.startsWith("http://") || target.startsWith("https://")) return false;
                SpiderDebug.log(TAG, "blockScheme url=%s", target);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                SpiderDebug.log(TAG, "resourceError url=%s code=%d desc=%s main=%b",
                        request.getUrl(), error.getErrorCode(), error.getDescription(), request.isForMainFrame());
                // 只有主文档失败才值得打扰用户：CDN 里少一个资源不该弹提示
                if (!request.isForMainFrame()) return;
                hideLoading();
                com.fongmi.android.tv.utils.Notify.show(getString(R.string.cat_web_failed, error.getDescription()));
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                // 不吞掉的话整个进程会被系统连坐杀掉
                SpiderDebug.log(TAG, "renderProcessGone crashed=%b", detail.didCrash());
                finish();
                return true;
            }
        };
    }

    private void address(String url) {
        TextView view = findViewById(R.id.address);
        if (view != null && !TextUtils.isEmpty(url)) view.setText(url);
    }

    private void hideLoading() {
        if (loading == null || loading.getVisibility() != View.VISIBLE) return;
        loading.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            loading.setVisibility(View.GONE);
            loading.setAlpha(1f);
        }).start();
    }

    /**
     * 兼容旧 API 的返回处理；新 API 走 {@code OnBackPressedDispatcher}。
     *
     * <p>不拦 {@code onKeyDown}：dispatcher 本来就会收到返回键，在 key-down 再调一次会让
     * 按住返回键的重复事件连续触发回退。参照 {@code WebReaderActivity} 的做法。
     */
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        // 选择器还挂着就销毁，WebView 那侧会一直等；先收尾再拆页面
        cancelChooser();
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                // 先摘出 view 树再 destroy，否则仍挂在父容器上销毁会告警/泄漏
                android.view.ViewParent parent = webView.getParent();
                if (parent instanceof android.view.ViewGroup) ((android.view.ViewGroup) parent).removeView(webView);
                webView.destroy();
            } catch (Throwable ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }
}
