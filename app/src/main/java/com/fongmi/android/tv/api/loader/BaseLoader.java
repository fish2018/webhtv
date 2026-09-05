package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;
    private final java.util.concurrent.ConcurrentHashMap<String, Spider> catSpiders;
    private final LoaderClearQueue clearQueue;

    private BaseLoader() {
        jarLoader = new JarLoader();
        pyLoader = new PyLoader();
        jsLoader = new JsLoader();
        catSpiders = new java.util.concurrent.ConcurrentHashMap<>();
        clearQueue = new LoaderClearQueue(Task.loaderExecutor());
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    public Future<?> clear() {
        return clear("unknown");
    }

    public Future<?> clear(String reason) {
        String source = reason == null || reason.isEmpty() ? "unknown" : reason;
        SpiderDebug.log("base-loader", "clear requested reason=%s", source);
        LoaderClearQueue.Entry entry = clearQueue.submit(source, id -> clear(id, source));
        return entry.future();
    }

    private void clear(int id, String reason) {
        SpiderDebug.log("base-loader", "clear start id=%d reason=%s", id, reason);
        clear("jar", jarLoader::clear);
        clear("py", pyLoader::clear);
        clear("js", jsLoader::clear);
        clear("cat", catSpiders::clear);
        SpiderDebug.log("base-loader", "clear done id=%d reason=%s", id, reason);
    }

    public void awaitClear() throws InterruptedException {
        if (clearQueue.isRunning()) return;
        LoaderClearQueue.Entry entry = clearQueue.latest();
        if (entry == null) return;
        Future<?> future = entry.future();
        int id = entry.id();
        String reason = entry.reason();
        boolean waiting = !future.isDone();
        long start = System.nanoTime();
        if (waiting) SpiderDebug.log("base-loader", "clear wait start id=%d reason=%s", id, reason);
        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            SpiderDebug.log("base-loader", "clear wait failed id=%d reason=%s error=%s:%s", id, reason, cause.getClass().getSimpleName(), cause.getMessage());
            SpiderDebug.log("base-loader", cause);
        } finally {
            if (waiting) SpiderDebug.log("base-loader", "clear wait done id=%d reason=%s elapsedMs=%d", id, reason, (System.nanoTime() - start) / 1_000_000L);
        }
    }

    private void clear(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("base-loader", "clear failed loader=%s error=%s:%s", name, e.getClass().getSimpleName(), e.getMessage());
            SpiderDebug.log("base-loader", e);
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        // 猫源站点 type 也是 3，但 api 指向本机 bundle 的 HTTP 路由，得走协议适配而非本地引擎
        if (CatSpider.matches(api)) return catSpiders.computeIfAbsent(key, k -> new CatSpider(api));
        if (isPy(api)) return pyLoader.getSpider(key, api, ext);
        else if (isJs(api)) return jsLoader.getSpider(key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (isJs(api)) jsLoader.setRecent(key);
        else if (isPy(api)) pyLoader.setRecent(key);
        else if (isCsp(api)) jarLoader.setRecent(Util.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (params.containsKey("siteKey")) return getSpider(params.get("siteKey")).proxy(params);
        if ("js".equals(params.get("do"))) return jsLoader.proxy(params);
        if ("py".equals(params.get("do"))) return pyLoader.proxy(params);
        return jarLoader.proxy(params);
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Util.md5(jar);
        jarLoader.parseJar(key, jar);
        if (recent) jarLoader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
