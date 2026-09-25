package com.fongmi.android.tv.player;

import android.text.TextUtils;
import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.ui.custom.CustomWebView;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Response;

public class ParseJob implements ParseCallback {

    private final AtomicBoolean done = new AtomicBoolean();
    private final List<CustomWebView> webViews;
    private ExecutorService executor;
    private ExecutorService infinite;
    private ParseCallback callback;
    private Parse parse;

    private ParseJob(ParseCallback callback) {
        this.executor = Executors.newSingleThreadExecutor();
        this.infinite = Executors.newCachedThreadPool();
        this.webViews = new ArrayList<>();
        this.callback = callback;
    }

    public static ParseJob create(ParseCallback callback) {
        return new ParseJob(callback);
    }

    public ParseJob start(Result result, boolean useParse) {
        setParse(result, useParse);
        execute(result);
        return this;
    }

    private void setParse(Result result, boolean useParse) {
        if (useParse) parse = VodConfig.get().getParse();
        if (result.getPlayUrl().startsWith("json:")) parse = Parse.get(1, result.getPlayUrl().substring(5));
        if (result.getPlayUrl().startsWith("parse:")) parse = VodConfig.get().getParse(result.getPlayUrl().substring(6));
        if (parse == null || parse.isEmpty()) parse = Parse.get(0, result.getPlayUrl());
        parse.setHeader(result.getHeader());
        parse.setClick(getClick(result));
    }

    private String getClick(Result result) {
        String click = VodConfig.get().getSite(result.getKey()).getClick();
        if (!TextUtils.isEmpty(click)) return click;
        return result.getClick();
    }

    private void execute(Result result) {
        Future<?> task = executor.submit(getTask(result));
        long timeout = parse.getType() == 4 ? Constant.TIMEOUT_PARSE_SUPER : Constant.TIMEOUT_PARSE_DEF;
        Task.schedule(() -> {
            if (task.cancel(true)) onParseError();
        }, timeout, TimeUnit.MILLISECONDS);
    }

    private Runnable getTask(Result result) {
        return () -> {
            try {
                doInBackground(result.getKey(), result.getUrl().v(), result.getFlag());
            } catch (Throwable e) {
                onParseError();
            }
        };
    }

    private void doInBackground(String key, String webUrl, String flag) throws Throwable {
        switch (parse.getType()) {
            case 0:
                startWeb(key, parse, webUrl);
                break;
            case 1:
                jsonParse(parse, webUrl, true);
                break;
            case 2:
                jsonExtend(webUrl);
                break;
            case 3:
                jsonMix(webUrl, flag);
                break;
            case 4:
                superParse(webUrl, flag);
                break;
        }
    }

    private void jsonParse(Parse item, String webUrl, boolean fatal) throws Exception {
        try (Response res = OkHttp.newCall(item.getUrl() + webUrl, item.getHeader()).execute()) {
            JsonObject object = Json.parse(res.body().string()).getAsJsonObject();
            String url = Json.safeString(object, "url");
            JsonObject data = object.getAsJsonObject("data");
            if (url.isEmpty()) url = Json.safeString(data, "url");
            checkResult(getHeader(object), url, item.getName(), fatal);
        }
    }

    private void jsonExtend(String webUrl) throws Throwable {
        LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) if (item.getType() == 1) jxs.put(item.getName(), item.extUrl());
        checkResult(Result.fromObject(BaseLoader.get().jsonExt(parse.getUrl(), jxs, webUrl)));
    }

    private void jsonMix(String webUrl, String flag) throws Throwable {
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        for (Parse item : VodConfig.get().getParses()) jxs.put(item.getName(), item.mixMap());
        checkResult(Result.fromObject(BaseLoader.get().jsonExtMix(flag, parse.getUrl(), parse.getName(), jxs, webUrl)));
    }

    private void superParse(String webUrl, String flag) throws Exception {
        List<Parse> json = VodConfig.get().getParses(1, flag);
        List<Parse> webs = VodConfig.get().getParses(0, flag);
        String targetUrl = unwrapSuperParseUrl(webUrl, webs);
        int count = json.size() + webs.size();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-super", "start json=%d web=%d flag=%s url=%s target=%s", json.size(), webs.size(), flag, webUrl, targetUrl);
        if (count == 0) {
            onParseError();
            return;
        }

        AtomicInteger pending = new AtomicInteger(count);
        for (Parse item : json) {
            infinite.execute(() -> {
                try {
                    jsonParse(item, targetUrl, false);
                } catch (Exception ignored) {
                } finally {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-super", "json attempt done name=%s pending=%d", item.getName(), pending.get() - 1);
                    finishSuperParseAttempt(pending);
                }
            });
        }

        long attemptTimeout = Math.max(20_000L, Constant.TIMEOUT_PARSE_WEB - 5_000L);
        for (Parse item : orderSuperParseWebs(webs)) {
            if (done.get()) return;
            CountDownLatch attemptDone = new CountDownLatch(1);
            AtomicBoolean attemptFinished = new AtomicBoolean();
            CompletableFuture<CustomWebView> viewFuture = startWebAsync(item, targetUrl, new ParseCallback() {
                @Override
                public void onParseSuccess(Map<String, String> headers, String url, String from) {
                    if (!attemptFinished.compareAndSet(false, true)) return;
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-super", "web success name=%s from=%s url=%s", item.getName(), from, url);
                    attemptDone.countDown();
                    ParseJob.this.onParseSuccess(headers, url, from);
                }

                @Override
                public void onParseError() {
                    if (!attemptFinished.compareAndSet(false, true)) return;
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-super", "web attempt error name=%s pending=%d", item.getName(), pending.get() - 1);
                    attemptDone.countDown();
                    finishSuperParseAttempt(pending);
                }
            });
            long deadline = System.currentTimeMillis() + attemptTimeout;
            while (!done.get() && attemptDone.getCount() > 0 && System.currentTimeMillis() < deadline) {
                attemptDone.await(Math.min(500L, Math.max(1L, deadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
            }
            if (done.get()) return;
            if (attemptDone.getCount() > 0 && attemptFinished.compareAndSet(false, true)) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-super", "web attempt timeout name=%s after=%dms", item.getName(), attemptTimeout);
                CustomWebView view = viewFuture.getNow(null);
                if (view != null) App.post(() -> view.stop(false));
                finishSuperParseAttempt(pending);
            }
        }
    }

    /**
     * 某些站点会把“智能线路”返回成另一个解析接口，例如 bd.jx.cn/?url=腾讯页。
     * 如果此时再给超级解析的所有解析器拼接前缀，就会形成解析器套解析器，
     * 页面脚本会沿错误的目标地址执行。这里先逐层解出真正的播放页。
     */
    private String unwrapSuperParseUrl(String webUrl, List<Parse> webs) {
        String current = UrlUtil.convert(webUrl);
        Set<String> parserHosts = new HashSet<>();
        for (Parse item : webs) {
            try {
                String host = Uri.parse(item.getUrl()).getHost();
                if (!TextUtils.isEmpty(host)) parserHosts.add(host.toLowerCase(java.util.Locale.ROOT));
            } catch (Throwable ignored) {
            }
        }
        for (int i = 0; i < 4; i++) {
            try {
                Uri uri = Uri.parse(current);
                String host = uri.getHost();
                String inner = uri.getQueryParameter("url");
                if (TextUtils.isEmpty(inner)) inner = uri.getQueryParameter("jx");
                if (TextUtils.isEmpty(inner) || !inner.regionMatches(true, 0, "http", 0, 4)) break;
                boolean knownParserHost = host != null && parserHosts.contains(host.toLowerCase(java.util.Locale.ROOT));
                if (!knownParserHost && !isLikelyParserUri(uri)) break;
                String unwrapped = UrlUtil.convert(inner);
                if (unwrapped.equals(current)) break;
                current = unwrapped;
            } catch (Throwable ignored) {
                break;
            }
        }
        return current;
    }

    private boolean isLikelyParserUri(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("jiexi") || path.contains("parse") || path.contains("/jx") || path.endsWith("/api");
    }

    /**
     * 老 Android WebView 对部分解析页脚本兼容性较差；先尝试已知会直接发出 HLS XHR 的接口，
     * 其余解析器保持原配置顺序作为后备。排序稳定，不改变任何解析器的 URL 或请求头。
     */
    private List<Parse> orderSuperParseWebs(List<Parse> webs) {
        List<Parse> ordered = new ArrayList<>(webs);
        ordered.sort(Comparator.comparingInt(this::superParsePriority));
        return ordered;
    }

    private int superParsePriority(Parse item) {
        String url = item == null ? "" : item.getUrl().toLowerCase(java.util.Locale.ROOT);
        // 虾米播放器是这些解析站共用的实际引擎；m3u8.tv 只是外面套一层 iframe，
        // 在 WebView 注入不到子框架时无法生效，因此放到最后再试。
        if (url.contains("xmflv.com")) return 0;
        if (url.contains("hls.one")) return 1;
        if (url.contains("m3u8.tv")) return 2;
        return 10;
    }

    private void finishSuperParseAttempt(AtomicInteger pending) {
        if (pending.decrementAndGet() == 0) onParseError();
    }

    private void checkResult(Map<String, String> headers, String url, String from, boolean fatal) {
        if (url.length() > 40) onParseSuccess(headers, url, from);
        else if (fatal) onParseError();
    }

    private void checkResult(Result result) {
        result.setHeader(parse.getHeader());
        if (result.getUrl().isEmpty()) onParseError();
        else if (result.needParse()) startWeb(result.getHeader(), UrlUtil.convert(result.getUrl().v()));
        else onParseSuccess(result.getHeader(), result.getUrl().v(), result.getJxFrom());
    }

    private void startWeb(String key, Parse item, String webUrl) {
        startWeb(key, item.getName(), item.getHeader(), item.getUrl() + webUrl, item.getClick());
    }

    private void startWeb(Parse item, String webUrl, ParseCallback callback) {
        startWeb(item.getName(), item.getName(), item.getHeader(), item.getUrl() + webUrl, item.getClick(), callback);
    }

    private void startWeb(Map<String, String> headers, String url) {
        startWeb("", "", headers, url, "");
    }

    private void startWeb(String key, String from, Map<String, String> headers, String url, String click) {
        startWeb(key, from, headers, url, click, this);
    }

    private void startWeb(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback) {
        startWebAsync(key, from, headers, url, click, callback);
    }

    private CompletableFuture<CustomWebView> startWebAsync(Parse item, String webUrl, ParseCallback callback) {
        return startWebAsync(item.getName(), item.getName(), item.getHeader(), item.getUrl() + webUrl, item.getClick(), callback);
    }

    private CompletableFuture<CustomWebView> startWebAsync(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback) {
        CompletableFuture<CustomWebView> future = new CompletableFuture<>();
        if (!WebViewUtil.support()) {
            callback.onParseError();
            future.complete(null);
        } else {
            App.post(() -> {
                try {
                    CustomWebView view = CustomWebView.create(App.get()).start(key, from, headers, url, click, callback, !url.contains("player/?url="));
                    webViews.add(view);
                    future.complete(view);
                } catch (Throwable e) {
                    callback.onParseError();
                    future.complete(null);
                }
            });
        }
        return future;
    }

    private Map<String, String> getHeader(JsonObject object) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) if (!entry.getValue().isJsonNull() && (entry.getKey().equalsIgnoreCase(HttpHeaders.USER_AGENT) || entry.getKey().equalsIgnoreCase(HttpHeaders.REFERER) || entry.getKey().equalsIgnoreCase(HttpHeaders.COOKIE) || entry.getKey().equalsIgnoreCase("ua"))) headers.put(UrlUtil.fixHeader(entry.getKey()), entry.getValue().getAsString());
        return headers.isEmpty() ? parse.getHeader() : headers;
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-job", "success from=%s url=%s headers=%s", from, url, headers == null ? "{}" : headers.keySet());
        if (!done.compareAndSet(false, true)) return;
        App.post(() -> {
            if (callback != null) callback.onParseSuccess(headers, url, from);
            stop();
        });
    }

    @Override
    public void onParseError() {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("parse-job", "error");
        if (!done.compareAndSet(false, true)) return;
        App.post(() -> {
            if (callback != null) callback.onParseError();
            stop();
        });
    }

    private void stopWeb() {
        for (CustomWebView webView : webViews) webView.stop(false);
        for (CustomWebView webView : webViews) webView.destroy();
        if (!webViews.isEmpty()) webViews.clear();
    }

    public void stop() {
        if (executor != null) executor.shutdownNow();
        if (infinite != null) infinite.shutdownNow();
        infinite = null;
        executor = null;
        callback = null;
        done.set(true);
        stopWeb();
    }
}
