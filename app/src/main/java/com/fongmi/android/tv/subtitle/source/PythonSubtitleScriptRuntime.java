package com.fongmi.android.tv.subtitle.source;

import com.fongmi.chaquo.Loader;
import com.fongmi.chaquo.Spider;

import java.util.concurrent.ConcurrentHashMap;

public final class PythonSubtitleScriptRuntime implements SubtitleScriptRuntime {

    private final Loader loader;
    private final ConcurrentHashMap<String, Spider> spiders;

    public PythonSubtitleScriptRuntime() {
        this(new Loader());
    }

    PythonSubtitleScriptRuntime(Loader loader) {
        this.loader = loader;
        this.spiders = new ConcurrentHashMap<>();
    }

    @Override
    public String init(String sourceKey, String scriptPath, String config) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey is empty");
        }
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new IllegalArgumentException("scriptPath is empty");
        }
        Spider previous = spiders.remove(sourceKey);
        destroy(previous);
        Spider spider = loader.spider(scriptPath);
        spiders.put(sourceKey, spider);
        try {
            return spider.subtitleInit(config);
        } catch (Throwable error) {
            spiders.remove(sourceKey, spider);
            destroy(spider);
            throw error;
        }
    }

    @Override
    public String search(String sourceKey, String request) {
        return require(sourceKey).subtitleSearch(request);
    }

    @Override
    public String resolve(String sourceKey, String request) {
        return require(sourceKey).subtitleResolve(request);
    }

    @Override
    public void destroy(String sourceKey) {
        destroy(spiders.remove(sourceKey));
    }

    public void destroyAll() {
        for (String sourceKey : spiders.keySet()) destroy(sourceKey);
    }

    private Spider require(String sourceKey) {
        Spider spider = spiders.get(sourceKey);
        if (spider == null) throw new IllegalStateException("Subtitle source is not initialized: " + sourceKey);
        return spider;
    }

    private static void destroy(Spider spider) {
        if (spider == null) return;
        try {
            spider.destroy();
        } catch (Throwable ignored) {
        }
    }
}
