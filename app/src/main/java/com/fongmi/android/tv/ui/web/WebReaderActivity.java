package com.fongmi.android.tv.ui.web;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.ui.novel.NovelReaderHost;
import com.fongmi.android.tv.ui.novel.NovelRouter;
import com.fongmi.android.tv.ui.novel.ReaderHistory;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验室：小说/漫画 WebView 阅读器。
 *
 * 正确姿势（非直接打开第三方网页）：
 * 加载本地 assets/reader.html 阅读器模板（自带小说翻页 / 漫画瀑布流 / 目录 / 主题 / 自动滚动 UI），
 * 把 spider 返回的 novel://{title,content} 或 pics://{图片URL列表} JSON 数据注入到模板渲染，全屏沉浸。
 */
public class WebReaderActivity extends AppCompatActivity {

    private static final String TAG = "TV-reader";

    public static final String EXTRA_KIND = "kind";           // 1=小说 2=漫画
    public static final String EXTRA_PAYLOAD = "payload";     // novel:// 或 pics:// 原始字符串
    public static final String EXTRA_SITE_KEY = "siteKey";
    public static final String EXTRA_FLAG = "flag";
    public static final String EXTRA_VOD_ID = "vodId";
    public static final String EXTRA_VOD_NAME = "vodName";
    public static final String EXTRA_VOD_PIC = "vodPic";
    public static final String EXTRA_CHAPTERS = "chapters";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_LOCAL_PATH = "localPath"; // 本地文件绝对路径（本地阅读模式）
    public static final String EXTRA_CACHE_KEY = "cacheKey";   // 大数据经静态缓存传递，避开 Binder 1MB 限制

    /**
     * 章节列表 / 正文 payload 经进程内静态缓存传递，不走 Intent。
     * 小说整本书可有数千章、单章正文可达数百 KB，直接放进 Intent 会触发
     * TransactionTooLargeException（Binder 事务上限约 1MB）。做法与 AudioActivity 一致。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, ArrayList<Episode>> CHAPTER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, String> PAYLOAD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** 缓存条目创建时间，用于清理「startActivity 没走到 onCreate」而残留的条目。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> CACHE_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    /** 把大数据存入缓存并返回 key，供 Intent 携带。 */
    public static String cacheLargeData(String payload, List<Episode> chapters) {
        evictStaleCache();
        String key = "reader_" + System.nanoTime();
        if (payload != null) PAYLOAD_CACHE.put(key, payload);
        if (chapters != null) CHAPTER_CACHE.put(key, new ArrayList<>(chapters));
        // 单调时钟：wall clock 往回跳会让 now-创建时刻 变成负数而永不过期，缓存留到进程结束
        CACHE_TIME.put(key, android.os.SystemClock.elapsedRealtime());
        return key;
    }

    /** 清理超过 TTL 的缓存：阅读器未真正启动时没人来取，否则会留到进程结束。 */
    private static void evictStaleCache() {
        long now = android.os.SystemClock.elapsedRealtime();
        for (java.util.Map.Entry<String, Long> e : CACHE_TIME.entrySet()) {
            if (now - e.getValue() <= CACHE_TTL_MS) continue;
            String k = e.getKey();
            CACHE_TIME.remove(k);
            PAYLOAD_CACHE.remove(k);
            CHAPTER_CACHE.remove(k);
        }
    }

    private static final okhttp3.OkHttpClient IMAGE_CLIENT = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /** 章节解析线程（单线程串行，避免快速连点目录时并发注入乱序）。 */
    private static final java.util.concurrent.ExecutorService RESOLVE_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** 本地文本文件单次读入上限（32MB），超过则报错而不是 OOM。 */
    private static final long MAX_LOCAL_TEXT_BYTES = 32L * 1024 * 1024;
    /** 单张图片读入上限（16MB）：漫画单页远小于此，超过视为异常响应。 */
    private static final long MAX_IMAGE_BYTES = 16L * 1024 * 1024;

    /** 读取至多 limit 字节；超出返回 null（视为异常内容，不渲染）。 */
    private static byte[] readCapped(java.io.InputStream in, long limit) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > limit) return null;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    /** 带 Referer 的图片地址表：token → {url, referer}，只在本 Activity 存活期间有效。 */
    private final java.util.concurrent.ConcurrentHashMap<String, String[]> picTokens = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong picTokenSeq = new java.util.concurrent.atomic.AtomicLong();
    private final int picNonce = new java.util.Random().nextInt();

    private WebView webView;
    private ProgressBar progress;
    private View loading;
    private volatile int kind = 1;                // volatile：RESOLVE_EXECUTOR 写、主线程与 JS 桥线程读
    private String payload = "";
    private String siteKey = "", flag = "", vodId = "", vodName = "", vodPic = "";
    private ArrayList<Episode> chapters = new ArrayList<>();
    private volatile int index = 0;               // volatile：JS 桥线程与主线程都会写读
    private String localPath = "";
    private String cacheKey = "";
    private boolean pageFinished = false;
    private String pendingJson = null;

    /** 阅读进度：JS 滚动时上报到内存，onPause / onDestroy 落库。 */
    private ReaderHistory.Record record;

    /**
     * 最新阅读位置快照。
     *
     * saveProgress 跑在 WebView 的 JavaBridge 线程，persistProgress 跑在主线程。
     * 用不可变对象整体替换，避免「章节名已换到新章、锚点还是旧章」这种撕裂写入，
     * 也保证主线程能看到最新值（volatile）。
     */
    private volatile Progress lastProgress = new Progress("", "", 0, 0);

    /** 章节内位置快照：锚点序号（小说=段落，漫画/PDF=页）与锚点总数。 */
    private static final class Progress {
        final String chapterUrl;
        final String chapterName;
        final int anchor;
        final int total;

        Progress(String chapterUrl, String chapterName, int anchor, int total) {
            this.chapterUrl = chapterUrl == null ? "" : chapterUrl;
            this.chapterName = chapterName == null ? "" : chapterName;
            this.anchor = anchor;
            this.total = total;
        }
    }
    /**
     * 本页交给宿主解析、尚未收尾的切章请求数。
     *
     * 只数不认身份：结果送达那一刻拿不到「这是哪一章的」，按身份追踪必然删错条目。
     * 抑制规则要的也只是「返回那一刻是否有请求在途」。
     */
    private final java.util.concurrent.atomic.AtomicInteger hostChapterRequests = new java.util.concurrent.atomic.AtomicInteger();

    /** 收尾一次在途请求（结果送达或判失败）；本页没有在途请求时什么都不做。 */
    private void endHostChapterRequest() {
        if (hostChapterRequests.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            NovelRouter.endChapterRequest();
        }
    }

    public boolean hasPendingHostChapterRequest() {
        return hostChapterRequests.get() > 0;
    }

    /** 待恢复的章节内锚点与总数（用完置 0）。 */
    private long restoreAnchor = 0;
    private long restoreTotal = 0;
    /** 上次读的章节与传入 payload 不同时，待重新解析的章节 URL。 */
    private String pendingRestoreUrl = null;

    /** 把内存里的最新阅读进度落库。 */
    private void persistProgress() {
        Progress p = lastProgress;
        if (record == null || p.chapterUrl.isEmpty()) return;
        ReaderHistory.save(record, p.chapterName, p.chapterUrl, p.anchor, p.total);
        SpiderDebug.log(TAG, "saveProgress index=%d anchor=%d/%d chapter=%s", index, p.anchor, p.total, p.chapterName);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_reader);
        applyImmersive();

        // 兼容 Android 13+ enableOnBackInvokedCallback：无论手势返回还是系统返回键，都直接关闭阅读页
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        Intent it = getIntent();
        kind = it.getIntExtra(EXTRA_KIND, 1);
        cacheKey = it.getStringExtra(EXTRA_CACHE_KEY);
        if (cacheKey == null) cacheKey = "";
        // 优先取静态缓存（大数据不走 Intent，避免 TransactionTooLargeException）
        payload = cacheKey.isEmpty() ? null : PAYLOAD_CACHE.get(cacheKey);
        if (payload == null) payload = it.getStringExtra(EXTRA_PAYLOAD);
        if (payload == null) payload = "";
        siteKey = it.getStringExtra(EXTRA_SITE_KEY);
        if (siteKey == null) siteKey = "";
        flag = it.getStringExtra(EXTRA_FLAG);
        if (flag == null) flag = "";
        vodId = it.getStringExtra(EXTRA_VOD_ID);
        if (vodId == null) vodId = "";
        vodName = it.getStringExtra(EXTRA_VOD_NAME);
        if (vodName == null) vodName = "";
        vodPic = it.getStringExtra(EXTRA_VOD_PIC);
        if (vodPic == null) vodPic = "";
        chapters = cacheKey.isEmpty() ? null : CHAPTER_CACHE.get(cacheKey);
        if (chapters == null) chapters = it.getParcelableArrayListExtra(EXTRA_CHAPTERS);
        if (chapters == null) chapters = new ArrayList<>();
        index = it.getIntExtra(EXTRA_INDEX, 0);
        localPath = it.getStringExtra(EXTRA_LOCAL_PATH);
        if (localPath == null) localPath = "";

        // 注册当前阅读器实例，供播放器解析完成后回传结果
        NovelRouter.currentReader = this;

        // 阅读进度：有 vodId 才能稳定标识一本书；无则只阅读不记录
        record = new ReaderHistory.Record(siteKey, vodId, flag, vodName, vodPic);
        if (record.canUse()) restoreFromHistory();

        webView = findViewById(R.id.web_view);
        progress = findViewById(R.id.progress);
        loading = findViewById(R.id.loading);
        TextView loadingText = findViewById(R.id.loading_text);
        loadingText.setText(vodName.isEmpty()
                ? getString(R.string.reader_opening_default)
                : getString(R.string.reader_opening, vodName));
        // 与 reader.html 默认主题同色，避免 WebView 首帧前出现黑屏跳变
        webView.setBackgroundColor(0xFFE8ECF1);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // 允许 file:// 页面加载 pdf.worker.min.js worker 与缓存目录里的 PDF 文件。
        // 不开 setAllowUniversalAccessFromFileURLs：正文来自第三方 spider，一旦有脚本执行，
        // 通用跨源访问就意味着「读私有文件 + 无 CORS 限制地外发」。保持关闭可切断外发这一半，
        // 与 reader.html 的 DOM 允许列表净化、shouldOverrideUrlLoading 一起构成多层防护。
        s.setAllowFileAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(this, "AndroidReader");
        SpiderDebug.log(TAG, "create kind=%d payloadLen=%d chapters=%d index=%d site=%s flag=%s local=%s",
                kind, payload.length(), chapters.size(), index, siteKey, flag, localPath);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                SpiderDebug.log(TAG, "progress=%d", newProgress);
                if (progress != null) {
                    if (newProgress >= 100) progress.setVisibility(View.GONE);
                    else { progress.setVisibility(View.VISIBLE); progress.setProgress(newProgress); }
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                // JS 报错 / console 输出：reader.html 渲染失败时的唯一线索
                SpiderDebug.log(TAG, "console [%s] %s (%s:%d)",
                        msg.messageLevel(), msg.message(), msg.sourceId(), msg.lineNumber());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                SpiderDebug.log(TAG, "pageStarted url=%s", url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                SpiderDebug.log(TAG, "pageFinished url=%s pending=%b local=%s", url, pendingJson != null, localPath);
                pageFinished = true;
                if (pendingJson != null) {
                    inject(pendingJson);
                    pendingJson = null;
                } else if (!localPath.isEmpty()) {
                    loadLocalFileAsync();
                } else {
                    buildDataJsonAsync();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // 阅读器只应停在本地模板页。正文来自第三方 spider，若被导航到远程页面，
                // AndroidReader 这个 JS 桥是 WebView 级别的，会一并暴露给远程页面。
                String u = request.getUrl() == null ? "" : request.getUrl().toString();
                if (u.startsWith("file:///android_asset/reader.html")) return false;
                SpiderDebug.log(TAG, "blockNavigation url=%s", u);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                SpiderDebug.log(TAG, "resourceError url=%s code=%d desc=%s main=%b",
                        request.getUrl(), error.getErrorCode(), error.getDescription(), request.isForMainFrame());
            }

            @Override
            public void onReceivedHttpError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceResponse response) {
                SpiderDebug.log(TAG, "httpError url=%s status=%d", request.getUrl(), response.getStatusCode());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                // 渲染进程崩溃（OOM / crash）：不吞掉，否则整个 Activity 会被系统杀掉
                SpiderDebug.log(TAG, "renderProcessGone crashed=%b", detail.didCrash());
                finish();
                return true;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith("readerpic://")) return fetchImageWithReferer(u);
                return null;
            }
        });

        webView.loadUrl("file:///android_asset/reader.html");
        // 兜底：WebView 迟迟不回调时也要放开占位层，不能让它永久遮挡页面
        loading.postDelayed(this::hideLoading, 8000);
    }

    /**
     * 首屏数据在后台线程构建后注入。
     *
     * 必须离开主线程：PDF 漫画会在这里下载整份 PDF（网络阻塞，targetSdk 28 下主线程网络
     * 直接抛 NetworkOnMainThreadException 并被 catch 吞成「当图片渲染」→ 破图）；
     * 长篇小说还要为数千章建 JSONObject。
     */
    private void buildDataJsonAsync() {
        RESOLVE_EXECUTOR.execute(() -> {
            // 执行器是进程级单线程：任务开跑前先确认自己还活着，
            // 否则一次被放弃的 PDF 下载（最长 45s 超时）会把后一本书的首屏堵在队列后面。
            if (isFinishing() || isDestroyed()) return;
            String json = buildDataJson();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                inject(json);
            });
        });
    }

    private void inject(String json) {
        if (webView == null) return;
        try {
            // 用 typeof 探测模板函数是否就绪，回执写日志：区分「注入没发生」和「注入了但渲染失败」
            json = jsSafe(json);
            String js = "(function(){try{"
                    + "if(typeof window.__injectReader!=='function') return 'no-fn';"
                    + "window.__injectReader(" + json + ");"
                    + "var r=document.getElementById('reader');"
                    + "return 'ok children='+(r?r.children.length:-1)+' theme='+document.body.className;"
                    + "}catch(e){return 'err '+e;}})()";
            SpiderDebug.log(TAG, "inject len=%d head=%s", json.length(), json.substring(0, Math.min(160, json.length())));
            webView.evaluateJavascript(js, value -> {
                SpiderDebug.log(TAG, "injectResult %s", value);
                afterInject();
            });
        } catch (Throwable e) {
            SpiderDebug.log(TAG, e);
            hideLoading();
        }
    }

    /**
     * 首屏内容注入完成后：
     * 1) 上次读的不是本章 → 直接解析目标章（占位层保持显示，避免闪现第一章）
     * 2) 已是目标章 → 恢复章节内滚动位置并放开占位层
     */
    private void afterInject() {
        if (pendingRestoreUrl != null) {
            String url = pendingRestoreUrl;
            pendingRestoreUrl = null;
            resolveChapterSelf(url);
            return;
        }
        restoreScroll();
        hideLoading();
    }

    /** 恢复章节内位置（只在本次进入时生效一次）。 */
    private void restoreScroll() {
        if (restoreAnchor <= 0 || webView == null) return;
        long a = restoreAnchor;
        long t = restoreTotal;
        restoreAnchor = 0;
        restoreTotal = 0;
        // 旧版小说记录（total==SCALE）传 0，让 HTML 走百分比兜底：a/SCALE 即原百分比
        if (t == ReaderHistory.SCALE) {
            webView.evaluateJavascript("window.__restoreScroll && window.__restoreScroll("
                    + ((double) a / ReaderHistory.SCALE) + ", 0);", null);
        } else {
            webView.evaluateJavascript("window.__restoreScroll && window.__restoreScroll(" + a + ", " + t + ");", null);
        }
    }

    /**
     * JSON 转成可安全嵌入 JS 源码的形式。
     *
     * U+2028/U+2029 在 JSON 里合法，但在旧版 WebView 的 JS 字符串字面量里是非法字符，
     * 会让整次注入变成语法错误（表现为白屏）。spider 正文里出现这两个字符并不罕见。
     */
    private static String jsSafe(String json) {
        if (json == null) return "null";
        return json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    /** 内容已渲染 → 淡出原生占位层，露出 WebView。 */
    private void hideLoading() {
        if (loading == null || loading.getVisibility() != View.VISIBLE) return;
        loading.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            loading.setVisibility(View.GONE);
            loading.setAlpha(1f);
        }).start();
    }

    /** 把 novel:// / pics:// payload 解析成阅读数据 JSON，注入模板。 */
    private String buildDataJson() {
        try {
            JSONObject data = new JSONObject();
            data.put("kind", kind);
            data.put("siteKey", siteKey);
            data.put("flag", flag);
            data.put("vodId", vodId);
            data.put("vodName", vodName);
            data.put("vodPic", vodPic);
            data.put("current", index);
            data.put("chapters", buildChaptersJson());
            // 告知页面「稍后会恢复位置」：否则页面在注入时立刻上报 anchor=0，
            // 把 restoreFromHistory 刚读出来的位置覆盖掉。
            data.put("restore", restoreAnchor > 0 || pendingRestoreUrl != null);

            if (kind == 1) {
                // 小说：novel://{title,content}
                JSONObject n = parseNovel(payload);
                data.put("title", n.optString("title", vodName));
                data.put("content", n.optString("content", ""));
            } else {
                // 漫画：pics://url1&&url2（图片）或 pics://xxx.pdf（PDF 漫画）
                String pdfFile = downloadPdfIfNeeded(payload);
                if (pdfFile != null) {
                    data.put("kind", 3); // PDF 漫画
                    data.put("pdfFile", pdfFile);
                } else {
                    data.put("images", parsePics(payload));
                }
                String t = nvl(currentChapterName(), vodName);
                data.put("title", t);
            }
            return data.toString();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "buildDataJson failed kind=%d payloadLen=%d", kind, payload.length());
            SpiderDebug.log(TAG, e);
            return "{\"kind\":" + kind + ",\"title\":\"加载失败\",\"content\":\"\",\"images\":[],\"chapters\":[],\"current\":0}";
        }
    }

    private String nvl(String a, String b) {
        return (a == null || a.isEmpty()) ? (b == null ? "" : b) : a;
    }

    private String currentChapterName() {
        if (index >= 0 && index < chapters.size()) return chapters.get(index).getName();
        return "";
    }

    private JSONArray buildChaptersJson() {
        JSONArray arr = new JSONArray();
        for (Episode e : chapters) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", e.getName() == null ? "" : e.getName());
                o.put("url", e.getUrl() == null ? "" : e.getUrl());
            } catch (Throwable ignore) {}
            arr.put(o);
        }
        return arr;
    }

    /** novel://{json} → {title, content}，容错常见字段。 */
    private JSONObject parseNovel(String raw) {
        JSONObject out = new JSONObject();
        String body = raw == null ? "" : raw.trim();
        if (body.startsWith("novel://")) body = body.substring("novel://".length()).trim();
        try {
            JSONObject o = new JSONObject(body);
            String title = o.optString("title", "");
            String content = "";
            for (String k : new String[]{"content", "text", "book", "body", "data", "txt", "chapter", "article"}) {
                Object v = o.opt(k);
                if (v instanceof String && !((String) v).isEmpty()) { content = (String) v; break; }
                else if (v instanceof JSONArray && ((JSONArray) v).length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ((JSONArray) v).length(); i++) {
                        if (i > 0) sb.append("\n");
                        sb.append(((JSONArray) v).opt(i));
                    }
                    content = sb.toString();
                    break;
                }
            }
            out.put("title", title);
            out.put("content", content);
        } catch (Throwable e) {
            // 非 JSON：把整段当正文（用已剥掉协议前缀的 body，别把 novel:// 显示成第一行）
            try { out.put("content", body); } catch (Throwable ignore) {}
        }
        return out;
    }

    /** pics://url1&&url2... → [url1,url2,...]。
     *  带 @Referer= 防盗链头的图片，转成 readerpic://img?t=token 自定义 scheme，
     *  真实地址与 Referer 只留在 Java 侧的 {@link #picTokens} 里，不进入页面 —— 页面脚本
     *  就无法把这个拦截器当成「任意 URL 取回代理」（否则可用它探测回环/内网地址）。 */
    private JSONArray parsePics(String raw) {
        JSONArray arr = new JSONArray();
        if (raw == null) return arr;
        String s = raw.trim();
        if (s.startsWith("pics://") || s.startsWith("manga://")) s = s.substring(s.indexOf("://") + 3);
        for (String u : s.split("&&")) {
            if (u == null) continue;
            u = u.trim();
            if (u.isEmpty()) continue;
            String referer = null;
            int ref = u.indexOf("@Referer=");
            if (ref > 0) {
                referer = u.substring(ref + "@Referer=".length()).trim();
                u = u.substring(0, ref);
            }
            int ua = u.indexOf("@User-Agent=");
            if (ua > 0) u = u.substring(0, ua);
            if (referer != null && !referer.isEmpty()) {
                arr.put("readerpic://img?t=" + putPicToken(u, referer));
            } else {
                arr.put(u);
            }
        }
        return arr;
    }

    /** 登记一条带 Referer 的图片地址，返回只在本次会话有效的 token。 */
    private String putPicToken(String url, String referer) {
        String token = Long.toHexString(picTokenSeq.incrementAndGet()) + "_" + Integer.toHexString(picNonce);
        picTokens.put(token, new String[]{url, referer});
        return token;
    }

    /** 检测 pics:// payload 是否为 PDF 漫画；若是则下载到缓存并返回 file:// URL，否则返回 null。 */
    private String downloadPdfIfNeeded(String raw) {
        try {
            if (raw == null) return null;
            String s = raw.trim();
            if (!s.startsWith("pics://") && !s.startsWith("manga://")) return null;
            s = s.substring(s.indexOf("://") + 3);
            String first = s.split("&&", 2)[0].trim();
            if (first.isEmpty()) return null;
            String referer = null;
            int ref = first.indexOf("@Referer=");
            if (ref > 0) {
                referer = first.substring(ref + "@Referer=".length()).trim();
                first = first.substring(0, ref).trim();
            }
            int ua = first.indexOf("@User-Agent=");
            if (ua > 0) first = first.substring(0, ua).trim();
            String path = first;
            int q = path.indexOf('?');
            if (q > 0) path = path.substring(0, q);
            if (!path.toLowerCase().endsWith(".pdf")) return null;
            return downloadPdfToCache(first, referer);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 用 OkHttp 加 Referer/UA 头下载 PDF 到缓存目录，返回 file:// 绝对路径（复用缓存）。 */
    private String downloadPdfToCache(String url, String referer) {
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "readerpdf");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, md5(url + "|" + (referer == null ? "" : referer)) + ".pdf");
            if (f.exists() && f.length() > 0) return "file://" + f.getAbsolutePath();
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
            if (referer != null && !referer.isEmpty()) rb.header("Referer", referer);
            // 流式落盘：PDF 动辄几十上百 MB，整体读进 byte[] 会 OOM
            try (okhttp3.Response resp = IMAGE_CLIENT.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                java.io.File tmp = new java.io.File(f.getAbsolutePath() + ".tmp");
                try (java.io.InputStream in = resp.body().byteStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                // 下载完再改名，避免中断留下半截文件被当成有效缓存
                if (!tmp.renameTo(f)) { tmp.delete(); return null; }
            }
            return "file://" + f.getAbsolutePath();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Throwable e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /* ---------------- 本地文件阅读 ---------------- */

    private void loadLocalFileAsync() {
        new Thread(() -> {
            String json = buildLocalDataJson(localPath);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (pageFinished) inject(json);
                else pendingJson = json;
            });
        }).start();
    }

    /** 按扩展名把本地文件分流成阅读数据 JSON。 */
    private String buildLocalDataJson(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return errorJson("文件不存在");
            String name = f.getName();
            String lower = name.toLowerCase(java.util.Locale.ROOT);

            if (lower.endsWith(".epub")) return buildEpubJson(f);
            if (lower.endsWith(".zip")) return buildZipJson(f);
            if (lower.endsWith(".pdf")) return buildLocalComicJson(f, true);
            if (isImage(lower)) return buildLocalComicJson(f, false);

            JSONObject data = new JSONObject();
            data.put("siteKey", "");
            data.put("flag", "");
            data.put("vodId", "");
            data.put("vodName", name);
            data.put("vodPic", "");
            data.put("current", 0);
            data.put("chapters", new JSONArray());

            if (lower.endsWith(".txt") || lower.endsWith(".html") || lower.endsWith(".htm")) {
                data.put("kind", 1);
                data.put("title", name);
                data.put("content", readText(f));
            } else {
                return errorJson("不支持的格式：" + name);
            }
            return data.toString();
        } catch (Throwable e) {
            return errorJson("读取失败：" + e.getMessage());
        }
    }

    /** 本地漫画（图片/PDF）：识别章节子目录，当前章独立渲染，chapters 含目录路径供本地切章。 */
    private String buildLocalComicJson(java.io.File f, boolean isPdf) {
        try {
            java.io.File dir = f.getParentFile();
            java.util.List<java.io.File> chapterDirs = detectChapterDirs(dir);

            JSONObject data = new JSONObject();
            data.put("vodName", f.getName());
            data.put("chapters", buildChaptersJson(chapterDirs));
            int cur = chapterDirs.size() > 1 ? indexOf(chapterDirs, dir) : 0;
            data.put("current", cur);
            data.put("title", chapterDirs.size() > 1 ? dir.getName() : f.getName());

            if (isPdf) {
                data.put("kind", 3);
                data.put("pdfFile", "file://" + f.getAbsolutePath());
            } else {
                data.put("kind", 2);
                data.put("images", collectImagesFromDir(dir));
            }
            return data.toString();
        } catch (Throwable e) {
            return errorJson("读取失败：" + e.getMessage());
        }
    }

    /** 检测章节结构：dir 的父目录下若有多个子目录，则这些子目录视为章节。 */
    private java.util.List<java.io.File> detectChapterDirs(java.io.File dir) {
        java.util.List<java.io.File> out = new java.util.ArrayList<>();
        if (dir == null) return out;
        java.io.File grand = dir.getParentFile();
        if (grand == null) return out;
        java.io.File[] subs = grand.listFiles(java.io.File::isDirectory);
        if (subs == null || subs.length <= 1) return out;
        for (java.io.File s : subs) out.add(s);
        java.util.Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private int indexOf(java.util.List<java.io.File> list, java.io.File dir) {
        if (dir == null) return 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).equals(dir)) return i;
        return 0;
    }

    private JSONArray buildChaptersJson(java.util.List<java.io.File> chapterDirs) {
        JSONArray arr = new JSONArray();
        for (java.io.File cd : chapterDirs) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", cd.getName());
                o.put("url", cd.getAbsolutePath());
            } catch (Throwable ignore) {}
            arr.put(o);
        }
        return arr;
    }

    private JSONArray collectImagesFromDir(java.io.File dir) {
        JSONArray arr = new JSONArray();
        if (dir == null) return arr;
        java.io.File[] files = dir.listFiles();
        if (files == null) return arr;
        java.util.List<java.io.File> imgs = new java.util.ArrayList<>();
        for (java.io.File x : files) {
            if (x.isFile() && isImage(x.getName().toLowerCase(java.util.Locale.ROOT))) imgs.add(x);
        }
        java.util.Collections.sort(imgs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (java.io.File img : imgs) arr.put("file://" + img.getAbsolutePath());
        return arr;
    }

    /** 用 JSONObject 生成，不手写转义（手写版漏了控制字符等情况）。 */
    private String errorJson(String msg) {
        try {
            JSONObject d = new JSONObject();
            d.put("kind", 1);
            d.put("title", "读取失败");
            d.put("content", msg == null ? "" : msg);
            d.put("images", new JSONArray());
            d.put("chapters", new JSONArray());
            d.put("current", 0);
            return d.toString();
        } catch (Throwable e) {
            return "{\"kind\":1,\"title\":\"读取失败\",\"content\":\"\",\"images\":[],\"chapters\":[],\"current\":0}";
        }
    }

    private boolean isImage(String lower) {
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private String readText(java.io.File f) {
        try {
            byte[] bytes = readAllBytes(f);
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return new String(bytes, 3, bytes.length - 3, "UTF-8");
            }
            try {
                java.nio.charset.CharsetDecoder dec = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                return dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (Throwable e) {
                return new String(bytes, "GBK");
            }
        } catch (Throwable e) {
            return "";
        }
    }

    private byte[] readAllBytes(java.io.File f) throws Exception {
        // 本地 txt/epub 可能很大，限制单次读入上限，避免 OOM
        long len = f.length();
        if (len > MAX_LOCAL_TEXT_BYTES) throw new java.io.IOException("文件过大：" + (len >> 20) + "MB");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) Math.max(8192, Math.min(len, MAX_LOCAL_TEXT_BYTES)));
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** ZIP 漫画：解压所有图片，按文件名排序，写缓存目录返回 file:// URL 列表。 */
    private String buildZipJson(java.io.File f) {
        // try-with-resources：异常路径也要关掉 ZipFile
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
            java.util.List<String> imgs = new java.util.ArrayList<>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
            java.io.File dir = new java.io.File(getCacheDir(), "readerzip/" + md5(f.getAbsolutePath()));
            if (!dir.exists()) dir.mkdirs();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String n = e.getName().toLowerCase(java.util.Locale.ROOT);
                if (!isImage(n)) continue;
                java.io.File out = new java.io.File(dir, Integer.toHexString(e.getName().hashCode()) + extOf(n));
                if (!out.exists() || out.length() == 0) {
                    if (!extractEntryTo(zip, e, out)) continue;
                }
                imgs.add(e.getName());
            }
            java.util.Collections.sort(imgs, String::compareToIgnoreCase);
            JSONObject data = new JSONObject();
            data.put("kind", 2);
            data.put("title", f.getName());
            data.put("vodName", f.getName());
            data.put("current", 0);
            data.put("chapters", new JSONArray());
            JSONArray arr = new JSONArray();
            for (String n : imgs) {
                arr.put("file://" + new java.io.File(dir, Integer.toHexString(n.hashCode()) + extOf(n)).getAbsolutePath());
            }
            data.put("images", arr);
            return data.toString();
        } catch (Throwable e) {
            return errorJson("ZIP 解压失败：" + e.getMessage());
        }
    }

    private String extOf(String lower) {
        int i = lower.lastIndexOf('.');
        return i >= 0 ? lower.substring(i) : ".jpg";
    }

    /** EPUB：解压 → 解析 container.xml/opf/spine → 拼接图文 HTML（图片提取到缓存转 file:// URL）。 */
    private String buildEpubJson(java.io.File f) {
        // try-with-resources：异常路径也要关掉 ZipFile
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
            String opfPath = findOpfPath(zip);
            if (opfPath == null) return errorJson("无效 EPUB：找不到 OPF");
            String opfDir = "";
            int slash = opfPath.lastIndexOf('/');
            if (slash >= 0) opfDir = opfPath.substring(0, slash + 1);

            String opf = readEntry(zip, opfPath);
            if (opf == null) return errorJson("无法读取 OPF");

            java.util.Map<String, String> manifest = new java.util.LinkedHashMap<>();
            java.util.regex.Matcher mi = java.util.regex.Pattern.compile("<item[^>]*id=\"([^\"]*)\"[^>]*href=\"([^\"]*)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(opf);
            while (mi.find()) manifest.put(mi.group(1), mi.group(2));

            java.util.List<String> spine = new java.util.ArrayList<>();
            java.util.regex.Matcher sm = java.util.regex.Pattern.compile("<itemref[^>]*idref=\"([^\"]*)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(opf);
            while (sm.find()) {
                String href = manifest.get(sm.group(1));
                if (href != null) spine.add(opfDir + href);
            }
            if (spine.isEmpty()) {
                for (String href : manifest.values()) if (href.toLowerCase().endsWith(".xhtml") || href.toLowerCase().endsWith(".html")) spine.add(opfDir + href);
            }

            java.io.File imgDir = new java.io.File(getCacheDir(), "readerepub/" + md5(f.getAbsolutePath()));
            if (!imgDir.exists()) imgDir.mkdirs();

            java.util.List<String> collectedImages = new java.util.ArrayList<>();
            StringBuilder html = new StringBuilder();
            int totalTextLen = 0;
            for (String xhtmlPath : spine) {
                String xhtml = readEntry(zip, xhtmlPath);
                if (xhtml == null) continue;
                String body = extractBody(xhtml);
                if (body == null || body.trim().isEmpty()) body = xhtml;
                totalTextLen += stripTagsLength(body);
                body = replaceImages(zip, body, xhtmlPath, imgDir, collectedImages);
                if (!body.trim().isEmpty()) html.append("<section class=\"epub-chapter\">").append(body).append("</section>\n");
            }

            JSONObject data = new JSONObject();
            data.put("title", f.getName());
            data.put("vodName", f.getName());
            data.put("current", 0);
            data.put("chapters", new JSONArray());

            // 漫画判定：有图片且文字极少（平均每张图 < 30 字符，纯图 EPUB 漫画）
            boolean isComic = !collectedImages.isEmpty() && totalTextLen < collectedImages.size() * 30;
            if (isComic) {
                data.put("kind", 2);
                JSONArray arr = new JSONArray();
                for (String u : collectedImages) arr.put(u);
                data.put("images", arr);
            } else {
                data.put("kind", 1);
                data.put("content", html.toString());
            }
            return data.toString();
        } catch (Throwable e) {
            return errorJson("EPUB 解析失败：" + e.getMessage());
        }
    }

    /** 统计去掉 HTML 标签后的纯文本长度（用于判断 EPUB 是漫画还是小说）。 */
    private int stripTagsLength(String html) {
        try {
            return html.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", "").length();
        } catch (Throwable e) {
            return 0;
        }
    }

    private String findOpfPath(java.util.zip.ZipFile zip) {
        try {
            String container = readEntry(zip, "META-INF/container.xml");
            if (container == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("full-path=\"([^\"]+)\"").matcher(container);
            return m.find() ? m.group(1) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 解压单个 zip 条目到目标文件。
     *
     * 先写 .tmp 再改名：中途失败时不会留下「非空但截断」的文件 ——
     * 缓存命中判断是 length() > 0，半截文件会被当成有效缓存永久复用。
     */
    private boolean extractEntryTo(java.util.zip.ZipFile zip, java.util.zip.ZipEntry e, java.io.File out) {
        java.io.File tmp = new java.io.File(out.getAbsolutePath() + ".tmp");
        try (java.io.InputStream in = zip.getInputStream(e);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_LOCAL_TEXT_BYTES) { tmp.delete(); return false; }
                fos.write(buf, 0, n);
            }
        } catch (Throwable ex) {
            tmp.delete();
            return false;
        }
        if (tmp.renameTo(out)) return true;
        tmp.delete();
        return false;
    }

    /** 读取 zip 内文本条目；限长 + try-with-resources（zip 条目可能是解压炸弹）。 */
    private String readEntry(java.util.zip.ZipFile zip, String path) {
        try {
            java.util.zip.ZipEntry e = zip.getEntry(path);
            if (e == null) return null;
            try (java.io.InputStream in = zip.getInputStream(e)) {
                byte[] data = readCapped(in, MAX_LOCAL_TEXT_BYTES);
                return data == null ? null : new String(data, "UTF-8");
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private String extractBody(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<body[^>]*>(.*?)</body>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /** 把 body 里 <img src> / <image xlink:href> 的图片提取到缓存并替换为 file:// URL，同时收集到 collected。 */
    private String replaceImages(java.util.zip.ZipFile zip, String body, String xhtmlPath, java.io.File imgDir, java.util.List<String> collected) {
        String xhtmlDir = "";
        int s = xhtmlPath.lastIndexOf('/');
        if (s >= 0) xhtmlDir = xhtmlPath.substring(0, s + 1);
        body = replaceImagesByPattern(zip, body, xhtmlDir, imgDir, "<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", "<img src=\"%s\">", collected);
        body = replaceImagesByPattern(zip, body, xhtmlDir, imgDir, "<image[^>]*xlink:href=[\"']([^\"']+)[\"'][^>]*>", "<img src=\"%s\">", collected);
        return body;
    }

    private String replaceImagesByPattern(java.util.zip.ZipFile zip, String body, String xhtmlDir, java.io.File imgDir, String regex, String replacement, java.util.List<String> collected) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(body);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String url = extractImage(zip, m.group(1), xhtmlDir, imgDir);
            if (collected != null) collected.add(url);
            sb.append(body, last, m.start());
            sb.append(replacement.replace("%s", url));
            last = m.end();
        }
        sb.append(body, last, body.length());
        return sb.toString();
    }

    private String extractImage(java.util.zip.ZipFile zip, String src, String xhtmlDir, java.io.File imgDir) {
        try {
            String entryPath = normalize(xhtmlDir + src);
            java.util.zip.ZipEntry e = zip.getEntry(entryPath);
            if (e == null) e = findEntryByBasename(zip, src);
            if (e == null) return src;
            java.io.File out = new java.io.File(imgDir, Integer.toHexString(e.getName().hashCode()) + extOf(e.getName().toLowerCase()));
            if (!out.exists() || out.length() == 0) {
                if (!extractEntryTo(zip, e, out)) return src;
            }
            return "file://" + out.getAbsolutePath();
        } catch (Throwable ex) {
            return src;
        }
    }

    private java.util.zip.ZipEntry findEntryByBasename(java.util.zip.ZipFile zip, String src) {
        String base = src;
        int q = base.indexOf('?'); if (q > 0) base = base.substring(0, q);
        int sl = base.lastIndexOf('/'); if (sl >= 0) base = base.substring(sl + 1);
        try { base = java.net.URLDecoder.decode(base, "UTF-8"); } catch (Throwable e) {}
        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            java.util.zip.ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String n = e.getName();
            int nsl = n.lastIndexOf('/');
            if (nsl >= 0) n = n.substring(nsl + 1);
            if (n.equals(base)) return e;
        }
        return null;
    }

    private String normalize(String p) {
        String[] parts = p.split("/");
        java.util.List<String> stack = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!stack.isEmpty()) stack.remove(stack.size() - 1); }
            else stack.add(part);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) { if (i > 0) sb.append("/"); sb.append(stack.get(i)); }
        return sb.toString();
    }

    /**
     * 拦截 readerpic:// 自定义 scheme，用 OkHttp 加 Referer/UA 头请求图片（解决防盗链图片加载失败）。
     *
     * 只接受本次会话登记过的 token，不接受页面自带的 URL —— 否则页面脚本可用它请求任意地址
     * （含 127.0.0.1 上的应用内 HTTP 服务）并伪造 Referer。
     */
    private android.webkit.WebResourceResponse fetchImageWithReferer(String proxyUrl) {
        okhttp3.Response resp = null;
        try {
            android.net.Uri u = android.net.Uri.parse(proxyUrl);
            String token = u.getQueryParameter("t");
            if (token == null || token.isEmpty()) return null;
            String[] entry = picTokens.get(token);
            if (entry == null) return null;
            String realUrl = entry[0];
            String referer = entry[1];
            if (realUrl == null || realUrl.isEmpty()) return null;
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(realUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36");
            if (referer != null && !referer.isEmpty()) rb.header("Referer", referer);
            resp = IMAGE_CLIENT.newCall(rb.build()).execute();
            if (!resp.isSuccessful() || resp.body() == null) return null;
            // 限长读取：URL 来自第三方 spider，服务端可能一直吐数据或谎报 Content-Length，
            // 无上限的 bytes() 会把 WebView 的拦截线程 OOM 掉。
            long declared = resp.body().contentLength();
            if (declared > MAX_IMAGE_BYTES) return null;
            byte[] body = readCapped(resp.body().byteStream(), MAX_IMAGE_BYTES);
            if (body == null) return null;
            // Content-Type 可能带 "; charset=..."，WebResourceResponse 只接受纯 mime；
            // encoding 参数语义是字符集，不是 Content-Encoding（OkHttp 已解压），传 null。
            String mime = resp.header("Content-Type", "image/jpeg");
            int semi = mime.indexOf(';');
            if (semi > 0) mime = mime.substring(0, semi).trim();
            return new android.webkit.WebResourceResponse(mime, null, new java.io.ByteArrayInputStream(body));
        } catch (Throwable e) {
            return null;
        } finally {
            if (resp != null) try { resp.close(); } catch (Throwable ignore) {}
        }
    }

    /* ---------------- JS bridge（HTML 里可调 AndroidReader.xxx） ---------------- */

    @JavascriptInterface
    public void back() {
        runOnUiThread(this::finish);
    }

    @JavascriptInterface
    public void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    /**
     * HTML 滚动时上报阅读进度（已在 JS 线程，直接记内存；落库由 onPause / onDestroy 触发，
     * 避免滚动过程中频繁写数据库）。
     *
     * @param anchor 章节内锚点序号（0 基）：小说=段落，漫画/PDF=页
     * @param total  锚点总数
     */
    @JavascriptInterface
    public void saveProgress(int chapterIndex, String chapterUrl, String chapterName, int anchor, int total) {
        index = chapterIndex;
        lastProgress = new Progress(chapterUrl, chapterName, anchor, total);
    }

    /**
     * 读取上次阅读记录：定位章节下标与章节内锚点。
     *
     * 若上次读的不是本次传入的章节（站点分流总是解析第一章），
     * 记下待恢复的章节 URL，等页面就绪后直接解析该章，不先渲染第一章再跳。
     */
    private void restoreFromHistory() {
        History h = ReaderHistory.find(siteKey, vodId);
        if (h == null) return;
        String url = h.getEpisodeUrl();
        if (TextUtils.isEmpty(url)) return;
        int at = indexOfChapter(url);
        if (at < 0) return;
        // 传入 payload 对应的章节（EXTRA_INDEX），必须在覆盖 index 之前取，
        // 否则「传入的是不是目标章」会拿目标章和自己比，永远相等。
        String incomingUrl = index >= 0 && index < chapters.size() ? chapters.get(index).getUrl() : null;
        index = at;
        // position 是锚点序号（读完时记为 total），换回序号交给 HTML；
        // 旧版小说记录存的是百分比×SCALE，HTML 侧按 total 是否等于 SCALE 兜底处理。
        restoreTotal = h.getDuration();
        restoreAnchor = restoreTotal == ReaderHistory.SCALE
                ? h.getPosition() : ReaderHistory.toAnchor(h.getPosition(), restoreTotal);
        String chapterName = h.getVodRemarks() == null ? "" : h.getVodRemarks();
        lastProgress = new Progress(url, chapterName, (int) restoreAnchor, (int) restoreTotal);
        // 传入 payload 已是该章内容时无需重新解析
        if (!isCurrentChapter(chapterName, incomingUrl, url)) pendingRestoreUrl = url;
        SpiderDebug.log(TAG, "restore index=%d anchor=%d/%d kind=%d chapter=%s reresolve=%b",
                index, restoreAnchor, restoreTotal, kind, chapterName, pendingRestoreUrl != null);
    }

    /**
     * 传入的 payload 是否正是待恢复的那一章。
     *
     * 漫画 payload（pics://a&&b）里没有 title，只能按章节 URL 比；
     * 小说 payload 是 novel://{title,content}，按 title 比。
     */
    private boolean isCurrentChapter(String chapterName, String incomingUrl, String targetUrl) {
        if (kind != 1) return targetUrl != null && targetUrl.equals(incomingUrl);
        return extractedTitleMatches(chapterName);
    }

    /** 小说 payload 的 title 是否等于给定章节名。 */
    private boolean extractedTitleMatches(String chapterName) {
        if (TextUtils.isEmpty(chapterName)) return false;
        try {
            JSONObject n = parseNovel(payload);
            return chapterName.equals(n.optString("title", ""));
        } catch (Throwable e) {
            return false;
        }
    }

    /** 换章：HTML 点目录时回调。本地模式读目录章节，在线模式自行解析（无播放器时也能切章）。 */
    @JavascriptInterface
    public void loadChapter(String chapterUrl) {
        // 空 URL 与任何宿主请求无关：不能让它收尾别人那一笔（会放行迟到结果、返回键失效）
        if (TextUtils.isEmpty(chapterUrl)) { chapterFailed(false); return; }
        runOnUiThread(() -> {
            // 本页已在交还前台：此刻再发宿主请求，它永远等不到下一次 markReaderClosed
            // 把自己转成待拦额度，结果回来时就会把阅读器重新拉起（返回键失效）。
            if (isFinishing() || isDestroyed() || NovelRouter.currentReader != this) {
                chapterFailed(false);
                return;
            }
            if (!localPath.isEmpty() && isLocalDir(chapterUrl)) {
                loadLocalChapter(chapterUrl);
                return;
            }
            // 站点级分流（ReaderContentHandler → NovelRouter.openSite）不经过播放器，host 为 null，
            // 此时阅读器用自己持有的 siteKey/flag 直接解析章节。
            if (!siteKey.isEmpty()) {
                resolveChapterSelf(chapterUrl);
                return;
            }
            NovelReaderHost h = NovelRouter.getHost();
            // 记下本次切章所属的关闭代号：用户点了下一章又马上返回时，爬虫几秒后才回的结果
            // 会落在 1500ms 静默期之外，靠代号比对才能认出它已过期，不该再拉起阅读器。
            if (h == null) { chapterFailedWithToast(); return; }
            NovelRouter.noteChapterRequest();
            hostChapterRequests.incrementAndGet();
            // 宿主只在当前线路里找章节，找不到会静默返回 —— 立刻收尾并报失败，
            // 否则这一笔要等 45s 才过期，期间用户主动打开别的书会被误吞。
            if (!h.labPlayEpisode(chapterUrl)) chapterFailedWithToast(true);
        });
    }

    /**
     * 通知 HTML 本次切章失败：解锁 switchingChapter 并把章节下标回退。
     * 少了这一步，HTML 会一直停在「切章中」，之后所有阅读进度都不再上报。
     */
    private void chapterFailed() {
        chapterFailed(false);
    }

    /**
     * 通知 HTML 本次切章失败，并按令牌撤销在途标记。
     *
     * @param ownHostRequest 这次失败是否对应本页发出的一笔宿主请求。空 URL、注入异常等
     *                       与任何请求无关，传 false —— 否则会替别人收尾，让那一笔的迟到
     *                       结果不再被拦，用户返回后又被重新拉起阅读器。
     */
    private void chapterFailed(boolean ownHostRequest) {
        // 收尾放在 webView 判空之前：改的是 NovelRouter 的全局计数，
        // 不该被本页的 view 引用是否还在决定（否则销毁中的页会让计数永久留着）。
        if (ownHostRequest) endHostChapterRequest();
        if (webView == null) return;
        runOnUiThread(() -> {
            if (webView == null || isFinishing() || isDestroyed()) return;
            try {
                webView.evaluateJavascript("window.__chapterFailed && window.__chapterFailed();", null);
            } catch (Throwable ignore) {}
        });
    }

    private void chapterFailedWithToast() {
        chapterFailedWithToast(false);
    }

    private void chapterFailedWithToast(boolean ownHostRequest) {
        Toast.makeText(this, R.string.reader_chapter_failed, Toast.LENGTH_SHORT).show();
        chapterFailed(ownHostRequest);
    }

    /**
     * 自行解析章节内容：复用 SiteApi.playerContent，拿到 novel:// / pics:// 后注入。
     * 返回 parse=1（要求二次解析）时交给播放器宿主处理；无宿主则提示失败。
     */
    private void resolveChapterSelf(String chapterUrl) {
        int at = indexOfChapter(chapterUrl);
        RESOLVE_EXECUTOR.execute(() -> {
            String payloadOut = null;
            int kindOut = 0;
            boolean needHost = false;
            try {
                com.fongmi.android.tv.bean.Result r = com.fongmi.android.tv.api.SiteApi.playerContent(siteKey, flag, chapterUrl);
                String u = r.getRealUrl();
                String t = u == null ? "" : u.trim();
                if (t.startsWith("novel://")) kindOut = 1;
                else if (t.startsWith("pics://") || t.startsWith("manga://")) kindOut = 2;
                else if (r.needParse()) needHost = true;
                else kindOut = kind; // 站点已判定为阅读源，内容按当前类型处理
                payloadOut = u;
                SpiderDebug.log(TAG, "resolveChapter kind=%d needHost=%b len=%d url=%s",
                        kindOut, needHost, u == null ? 0 : u.length(), chapterUrl);
            } catch (Throwable e) {
                SpiderDebug.log(TAG, "resolveChapter failed url=%s", chapterUrl);
                SpiderDebug.log(TAG, e);
            }
            final String fp = payloadOut;
            final int fk = kindOut;
            final boolean fh = needHost;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                boolean hostDispatched = false;
                if (fk != 0 && fp != null && !fp.isEmpty()) {
                    if (at >= 0) index = at;
                    // 自解析成功：没经过宿主，不能替宿主请求收尾
                    onEpisodeResolved(fk, fp, at >= 0 ? chapters.get(at).getName() : "", false);
                    return;
                }
                NovelReaderHost h = NovelRouter.getHost();
                // 这条 parse=1 兜底才是实际会走到的宿主解析路径（loadChapter 里那条在
                // siteKey 非空时不可达，而所有真实启动入口都会带 siteKey）。它要走二次解析、
                // 耗时最长，最容易掉出关闭静默期，代号标记必须打在这里。
                if (fh && h != null) {
                    hostDispatched = true;
                    NovelRouter.noteChapterRequest();
                    hostChapterRequests.incrementAndGet();
                    if (!h.labPlayEpisode(chapterUrl)) chapterFailedWithToast(true);
                    return;
                }
                if (!hostDispatched) chapterFailedWithToast();
                // 解析失败：放开占位层，并丢掉待恢复位置 —— 否则它会残留到用户下一次手动切章，
                // 把上一本/上一章的锚点套用到新章上（章短时直接跳到章末）。
                restoreAnchor = 0;
                restoreTotal = 0;
                hideLoading();
            });
        });
    }

    private int indexOfChapter(String chapterUrl) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapterUrl.equals(chapters.get(i).getUrl())) return i;
        }
        return -1;
    }

    private boolean isLocalDir(String s) {
        return s.startsWith("/storage") || s.startsWith("/sdcard") || s.startsWith("file://");
    }

    /** 本地漫画切章：读章节目录，PDF 优先否则图片，注入新章数据。 */
    private void loadLocalChapter(String dirPath) {
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(dirPath.startsWith("file://") ? dirPath.substring("file://".length()) : dirPath);
                JSONObject d = new JSONObject();
                java.io.File pdf = findFirstPdf(dir);
                if (pdf != null) {
                    d.put("kind", 3);
                    d.put("pdfFile", "file://" + pdf.getAbsolutePath());
                    d.put("images", new JSONArray());
                } else {
                    d.put("kind", 2);
                    d.put("images", collectImagesFromDir(dir));
                    d.put("pdfFile", "");
                }
                d.put("content", "");
                d.put("title", dir.getName());
                String json = d.toString();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    injectChapter(json);
                });
            } catch (Throwable ignore) {}
        }).start();
    }

    private java.io.File findFirstPdf(java.io.File dir) {
        java.io.File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return null;
        java.util.List<java.io.File> pdfs = new java.util.ArrayList<>();
        for (java.io.File x : files) {
            if (x.isFile() && x.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) pdfs.add(x);
        }
        if (pdfs.isEmpty()) return null;
        java.util.Collections.sort(pdfs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return pdfs.get(0);
    }

    /**
     * 播放器解析完成后回传结果（由 NovelRouter.routeReaderEngine 调用，已在主线程）。
     * 把 novel:// / pics:// 解析成阅读数据注入 HTML。
     */
    public void onEpisodeResolved(int newKind, String payload, String title) {
        onEpisodeResolved(newKind, payload, title, true);
    }

    /**
     * @param fromHost 结果是否来自宿主解析。自解析（resolveChapterSelf 直接拿到内容）时传 false：
     *                 那条路径没发过宿主请求，不能顺手把仍在途的宿主令牌丢掉 —— 丢了就再没有
     *                 句柄去撤销它，只能等 45s TTL，期间用户主动打开别的书会被误吞。
     */
    private void onEpisodeResolved(int newKind, String payload, String title, boolean fromHost) {
        // 宿主结果已到达，收尾一笔在途请求。只减计数、不认身份 —— 送达这一刻
        // 拿不到「这是哪一章的结果」，按身份删必然删错，反而放行别人的迟到结果。
        if (fromHost) endHostChapterRequest();
        if (webView == null) return;
        // 漫画分支要下载 PDF（网络），不能在主线程做
        RESOLVE_EXECUTOR.execute(() -> {
            if (isFinishing() || isDestroyed()) return;
            String json = buildChapterJson(newKind, payload, title);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (json != null) {
                    injectChapter(json);
                } else {
                    // 构建失败也要清掉待恢复位置，否则会被套用到用户下一次选的章上
                    restoreAnchor = 0;
                    restoreTotal = 0;
                    hideLoading();
                    chapterFailed();
                }
            });
        });
    }

    /**
     * 构建切章注入数据。
     *
     * 始终写入 kind，并把另一路数据显式置空：__updateChapter 是「字段存在才覆盖」，
     * 漏写 kind 会让漫画站里夹带的 novel:// 章节仍按漫画渲染，显示上一章的图片。
     */
    private String buildChapterJson(int newKind, String payload, String title) {
        try {
            JSONObject d = new JSONObject();
            d.put("title", title == null ? "" : title);
            if (newKind == 1) {
                JSONObject n = parseNovel(payload);
                d.put("kind", 1);
                d.put("title", n.optString("title", title == null ? "" : title));
                d.put("content", n.optString("content", ""));
                d.put("images", new JSONArray());
                d.put("pdfFile", "");
            } else {
                String pdfFile = downloadPdfIfNeeded(payload);
                d.put("content", "");
                if (pdfFile != null) {
                    d.put("kind", 3);
                    d.put("pdfFile", pdfFile);
                    d.put("images", new JSONArray());
                } else {
                    // 上一章的图片 token 不再需要，先清掉，避免长时间连续翻章无限积累
                    picTokens.clear();
                    d.put("kind", 2);
                    d.put("images", parsePics(payload));
                    d.put("pdfFile", "");
                }
            }
            kind = d.optInt("kind", newKind);
            return d.toString();
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "buildChapterJson failed kind=%d", newKind);
            SpiderDebug.log(TAG, e);
            return null;
        }
    }

    private void injectChapter(String json) {
        if (webView == null) return;
        try {
            SpiderDebug.log(TAG, "injectChapter len=%d head=%s", json.length(), json.substring(0, Math.min(160, json.length())));
            webView.evaluateJavascript("window.__updateChapter && window.__updateChapter(" + jsSafe(json) + ");",
                    value -> {
                        SpiderDebug.log(TAG, "injectChapterResult %s", value);
                        restoreScroll();
                        hideLoading();
                    });
        } catch (Throwable e) {
            SpiderDebug.log(TAG, e);
            // 注入抛异常：清掉待恢复位置并解锁切章，避免残留污染下一章
            restoreAnchor = 0;
            restoreTotal = 0;
            hideLoading();
            chapterFailed();
        }
    }

    @Override
    public void onBackPressed() {
        // 直接关闭阅读页返回播放器（兼容旧 API；新 API 走 OnBackPressedDispatcher）
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新登记：onPause 里交还前台时会把注册清掉（见 markClosed 的注释），
        // 只在 onCreate 注册的话，两个阅读器叠栈时下层那个再次回到前台就永久失去注册，
        // 之后它自己的切章会另起一个阅读器实例压在自己上面。
        NovelRouter.currentReader = this;
    }

    @Override
    protected void onPause() {
        // 切后台 / 返回都先落库，避免进程被回收后丢进度
        persistProgress();
        // 先把关闭瞬间仍在途的请求转成全局待拦额度，再清理本页计数；否则
        // markReaderClosed() 看不到这些请求，迟到结果可能在静默期后重新拉起阅读器。
        if (isFinishing()) markClosed();
        // 本页仍在途的宿主请求随本页一起作废：它们的结果不会再回到这里，
        // 不收尾的话全局在途计数永久偏高，下一次关闭会凭虚高的数字多留待拦额度，
        // 把用户之后主动打开的书误吞掉。
        int inFlight = hostChapterRequests.getAndSet(0);
        for (int i = 0; i < inFlight; i++) NovelRouter.endChapterRequest();
        // 关闭时间戳必须在这里打，不能等 onDestroy：Android 的生命周期顺序是
        // 本页 onPause -> 宿主 onResume -> 本页 onStop -> 本页 onDestroy。
        // 宿主 onResume 会因 shouldReclaim() 重新派发上一次的 playerContent 结果，
        // 那一刻若时间戳还没写，NovelRouter 的两道防线同时失效（currentReader
        // 已 isFinishing() 判不出「在前台」，时间戳又还是 0），于是立刻又拉起阅读器，
        // 表现为返回键完全无效、只能强杀 APP。
        super.onPause();
    }

    /** 交还前台：清掉阅读器注册并记下关闭时间/代号，拦截返回后残留的解析回调。 */
    private void markClosed() {
        if (NovelRouter.currentReader != this) return;
        NovelRouter.currentReader = null;
        NovelRouter.markReaderClosed();
    }

    @Override
    protected void onDestroy() {
        persistProgress();
        // 清理阅读器静态引用 + 标记关闭时间，避免残留的 playerContent 回调在返回后重新拉起阅读器。
        // 只在自己仍是「当前阅读器」时清：两个阅读器叠栈时，旧实例销毁不能把前台新实例的注册抹掉。
        // onPause 已处理返回场景，这里兜住系统回收等不经过 finish() 的销毁。
        markClosed();
        picTokens.clear();
        // 只在真正结束时清缓存：配置变更 / 系统回收导致的重建会再次用同一个 cacheKey
        // 读取正文与章节列表（Intent 里只带 key，不带数据），提前清掉会渲染成空章。
        if (isFinishing() && !cacheKey.isEmpty()) {
            CHAPTER_CACHE.remove(cacheKey);
            PAYLOAD_CACHE.remove(cacheKey);
            CACHE_TIME.remove(cacheKey);
        }
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("AndroidReader");
                webView.stopLoading();
                webView.loadUrl("about:blank");
                // 先从 view 树摘掉再 destroy，否则仍挂在父容器上销毁会告警/泄漏
                android.view.ViewParent parent = webView.getParent();
                if (parent instanceof android.view.ViewGroup) ((android.view.ViewGroup) parent).removeView(webView);
                webView.destroy();
            } catch (Throwable ignore) {}
            // 置 null：在途的 evaluateJavascript 回调不能再碰已销毁的 WebView
            webView = null;
        }
        super.onDestroy();
    }

    private void applyImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }
}
