package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.ui.custom.CustomWebView;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * 猫源 bundle 回调宿主的端点。
 *
 * <p>CatPawOpen 里 {@code server.messageToDart(data)} 会 POST 到
 * {@code http://127.0.0.1:<catDartServerPort()>/msg}，body 形如
 * {@code {action, opt, prefix}}。全仓库只用到两个 action：{@code sniff}（按正则嗅探
 * 播放地址）和 {@code openInternalWebview}（打开内置网页）。
 */
public class CatMessage implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/msg");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            JsonObject body = parse(files.get("postData"));
            String action = string(body, "action");
            JsonObject opt = body.has("opt") && body.get("opt").isJsonObject() ? body.getAsJsonObject("opt") : new JsonObject();
            SpiderDebug.log("cat-msg", "action=%s opt=%s", action, opt);
            if ("sniff".equals(action)) return json(sniff(opt));
            if ("openInternalWebview".equals(action)) return json(webview(opt));
            return json(new JsonObject());
        } catch (Exception e) {
            SpiderDebug.log("cat-msg", e);
            return json(new JsonObject());
        }
    }

    /** 用 WebView 加载页面，命中 rule 的请求即为播放地址，连同请求头一起回给 bundle。 */
    private JsonObject sniff(JsonObject opt) {
        JsonObject result = new JsonObject();
        String target = string(opt, "url");
        if (TextUtils.isEmpty(target)) return result;
        String rule = string(opt, "rule");
        long timeout = opt.has("timeout") ? opt.get("timeout").getAsLong() : 10000;
        Pattern pattern = compile(rule);
        AtomicReference<String> hit = new AtomicReference<>();
        AtomicReference<Map<String, String>> hitHeaders = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ParseCallback callback = new ParseCallback() {
            @Override
            public void onParseSuccess(Map<String, String> headers, String url, String from) {
                hit.set(url);
                hitHeaders.set(headers == null ? new HashMap<>() : headers);
                latch.countDown();
            }

            @Override
            public void onParseError() {
                latch.countDown();
            }
        };
        // WebView 只能在主线程创建；这里是 Nano 的工作线程，所以 post 过去再等结果
        AtomicReference<CustomWebView> holder = new AtomicReference<>();
        App.post(() -> holder.set(CustomWebView.create(App.get()).sniff(pattern).start("", "", new HashMap<>(), target, "", callback, false)));
        try {
            latch.await(Math.max(1000, timeout), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            // 命中、出错、超时都要释放：WebView 占用原生资源，且不停会在后台继续加载
            release(holder);
        }
        String found = hit.get();
        if (TextUtils.isEmpty(found)) return result;
        result.addProperty("url", found);
        Map<String, String> headers = hitHeaders.get();
        if (headers != null && !headers.isEmpty()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                out.addProperty(entry.getKey().toLowerCase(), entry.getValue());
            }
            result.add("headers", out);
        }
        return result;
    }

    /** 与创建时同线程（主线程）销毁，参照 ParseJob 的释放顺序。 */
    private void release(AtomicReference<CustomWebView> holder) {
        App.post(() -> {
            CustomWebView webView = holder.getAndSet(null);
            if (webView == null) return;
            try {
                webView.stop(false);
                webView.destroy();
            } catch (Exception ignored) {
            }
        });
    }

    private JsonObject webview(JsonObject opt) {
        String target = string(opt, "url");
        if (!TextUtils.isEmpty(target)) CatWebview.open(target);
        return new JsonObject();
    }

    private Pattern compile(String rule) {
        if (TextUtils.isEmpty(rule)) return null;
        try {
            return Pattern.compile(rule);
        } catch (Exception e) {
            SpiderDebug.log("cat-msg", "bad sniff rule: %s", rule);
            return null;
        }
    }

    private JsonObject parse(String body) {
        if (TextUtils.isEmpty(body)) return new JsonObject();
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private Response json(JsonObject object) {
        return Nano.newFixedLengthResponse(Response.Status.OK, "application/json", object.toString());
    }
}
